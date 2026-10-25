package com.panda.teeming.mob.overworld;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gloamhound: a dusk wolf of the taiga that hunts in packs of three or four, one of them the alpha (bigger, 30 health,
 * 6 attack, a pale ruff round its neck). The pack circles its target 4.5 blocks out and takes turns: only one hound
 * at a time crouches (tell: 14 ticks low to the ground, a growl, dust kicked up), then darts straight in at 0.6
 * blocks a tick and bites for its attack damage, then falls back while the next one takes its turn, at least 10
 * ticks after the last dart ended. The alpha howls every 200 ticks in a fight: every pack member within 16 blocks
 * gets Speed I for 6 s.
 *
 * <p>Counter: kill the alpha. When it dies the rest of its pack flees for 200 ticks and never hears another howl.
 * Failing it: a steady stream of bites, one hound after another. Pack membership (the pack id and who leads it) is
 * saved with each hound.
 */
public class Gloamhound extends TeemingMonster {
    /** IDLE, the crouch (tell), the dart and bite, falling back, the alpha's howl, fleeing a dead alpha's killer. */
    public enum Step implements Phase { IDLE, TELL, DART, RETREAT, HOWL, FLEE }

    public static final int TELL_TICKS = 14;              // 0.7 s crouched before the dart
    /** It may start its crouch with its target this close. */
    public static final double DART_RANGE = 6.0;
    public static final double DART_SPEED = 0.6;          // blocks a tick
    public static final int DART_TICKS = 12;              // up to 7.2 blocks
    public static final float BITE = 1.0F;                // x attack damage
    public static final int RETREAT_TICKS = 20;
    /** How far out the waiting hounds circle their target. */
    public static final double CIRCLE_RADIUS = 4.5;
    /** Pack-wide ticks from the end of one dart to the next crouch, so bites never pile up. */
    public static final int TURN_GAP = 10;
    /** Hounds of one pack this close count as one pack. */
    public static final double PACK_RANGE = 24.0;
    public static final int PACK_MAX = 4;
    /** A hound spawned alone (egg, command) joins a pack with room this close, or founds one as its alpha. */
    public static final double JOIN_RANGE = 8.0;

    public static final float ALPHA_HEALTH = 30.0F;
    public static final float ALPHA_ATTACK = 6.0F;
    public static final double ALPHA_SCALE = 1.2;

    /** Ticks from one howl to the next, at the soonest; the first comes as soon as the alpha has a target. */
    public static final int HOWL_EVERY = 200;
    public static final int HOWL_TICKS = 30;
    public static final double HOWL_RADIUS = 16.0;
    public static final int HOWL_SPEED_TICKS = 120;       // 6 s of Speed for the pack
    public static final int HOWL_SPEED_LEVEL = 0;         // amplifier 0 = Speed I
    /** When the alpha dies the rest of its pack flees for 10 s. */
    public static final int FLEE_TICKS = 200;
    public static final double FLEE_SPEED = 1.4;

    /** {@code MobCore} flag bit that marks the alpha, so clients show its ruff. */
    public static final int ALPHA_FLAG = 0;

    private static final Identifier ALPHA_MODIFIER = TeemingMod.id("gloamhound_alpha");
    private static final DustParticleOptions BREATH = new DustParticleOptions(0xC8C4CC, 0.8F);
    private static final DustParticleOptions HOWL_RING = new DustParticleOptions(0xBFD3EA, 1.1F);
    private static final int RING_POINTS = 48;

    // ---- the pack (a self-contained linked group: an id saved on every member, one alpha, one turn at a time) ----

    /** The pack this hound belongs to; null until it joins or founds one (on spawn, or on its first tick). */
    private @Nullable UUID packId;
    private boolean alpha;
    /** Game time this hound last started a turn, and when its last dart ended. Not saved: a reload starts fresh. */
    private long turnAt = Long.MIN_VALUE;
    private long dartEndAt = Long.MIN_VALUE;
    private long nextHowl;
    private Vec3 dartDir = Vec3.ZERO;
    private @Nullable LivingEntity fleeFrom;
    private Vec3 fleeSpot = Vec3.ZERO;
    private int bites;
    private int howls;

    public Gloamhound(EntityType<? extends Gloamhound> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("gloamhound", 0.3);
    }

    /** What a natural spawn group shares: the pack its first hound founded, and that hound's look. */
    public record PackData(UUID id, String look) implements SpawnGroupData {}

    /** Its pack's id; until it has settled into a pack, its own id (a pack of one). */
    public UUID packId() {
        return packId != null ? packId : getUUID();
    }

    public boolean isAlpha() {
        return alpha;
    }

    /** Every living hound of this pack within {@link #PACK_RANGE}, itself included. Server only. */
    public List<Gloamhound> pack() {
        UUID id = packId();
        return level().getEntitiesOfClass(Gloamhound.class, getBoundingBox().inflate(PACK_RANGE), h -> h.isAlive() && id.equals(h.packId()));
    }

    /** Joins pack {@code id}, as its alpha or not. Public as a test hook. */
    public void joinPack(UUID id, boolean asAlpha) {
        packId = id;
        setAlpha(asAlpha);
    }

    /** Makes {@code alpha} lead a new pack with {@code members}. Public as a test hook. */
    public static UUID formPack(Gloamhound alpha, Gloamhound... members) {
        UUID id = UUID.randomUUID();
        alpha.joinPack(id, true);
        for (Gloamhound member : members) member.joinPack(id, false);
        return id;
    }

    /** The alpha is the same hound made bigger and stronger: 30 health, 6 attack, 1.2x, and a ruff. Fills its health. */
    private void setAlpha(boolean on) {
        alpha = on;
        core().setFlag(ALPHA_FLAG, on);
        xpReward = on ? 10 : 5;
        if (level().isClientSide()) return;
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        for (AttributeInstance a : new AttributeInstance[] {health, attack, scale}) if (a != null) a.removeModifier(ALPHA_MODIFIER);
        if (on) {
            if (health != null) health.addOrReplacePermanentModifier(new AttributeModifier(ALPHA_MODIFIER,
                    ALPHA_HEALTH - health.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
            if (attack != null) attack.addOrReplacePermanentModifier(new AttributeModifier(ALPHA_MODIFIER,
                    ALPHA_ATTACK - attack.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
            if (scale != null) scale.addOrReplacePermanentModifier(new AttributeModifier(ALPHA_MODIFIER,
                    ALPHA_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        refreshDimensions();
        setHealth(on ? getMaxHealth() : Math.min(getHealth(), getMaxHealth()));
    }

    /**
     * A natural spawn group is one pack: its first hound founds it as the alpha and the rest join, wearing the alpha's
     * look. A hound spawned on its own (an egg, a command) joins a pack with room within {@link #JOIN_RANGE}, else it
     * founds one.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (data instanceof PackData pack) {
            joinPack(pack.id(), false);
            core().setLook(pack.look(), true);
            return pack;
        }
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            settle(level);
            return result;
        }
        UUID id = UUID.randomUUID();
        joinPack(id, true);
        return new PackData(id, look());
    }

    /** Joins a pack with room within {@link #JOIN_RANGE}, keeping its own look, or founds one as its alpha. */
    private void settle(EntityGetter level) {
        Gloamhound host = packWithRoom(level);
        if (host != null) joinPack(host.packId(), false);
        else joinPack(UUID.randomUUID(), true);
    }

    /** A hound that arrived without a pack (summoned with data, placed by a structure) settles into one on its first tick. */
    @Override
    public void tick() {
        super.tick();
        if (packId == null && !level().isClientSide() && isAlive()) settle(level());
    }

    /** The nearest hound within {@link #JOIN_RANGE} whose pack has an alpha and fewer than {@link #PACK_MAX} hounds. */
    private @Nullable Gloamhound packWithRoom(EntityGetter level) {
        List<Gloamhound> near = level.getEntitiesOfClass(Gloamhound.class, getBoundingBox().inflate(JOIN_RANGE),
                h -> h != this && h.isAlive() && h.distanceTo(this) <= JOIN_RANGE);
        Gloamhound best = null;
        for (Gloamhound h : near) {
            List<Gloamhound> pack = h.pack();
            if (pack.size() >= PACK_MAX || pack.stream().noneMatch(Gloamhound::isAlpha)) continue;
            if (best == null || h.distanceTo(this) < best.distanceTo(this)) best = h;
        }
        return best;
    }

    // ---- goals ----------------------------------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CircleGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !core().in(Step.FLEE) && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return !core().in(Step.FLEE) && super.canUse();
            }
        });
    }

    /** While it waits its turn it circles its target {@link #CIRCLE_RADIUS} out, all the pack the same way round. */
    private final class CircleGoal extends Goal {
        private int repath;

        CircleGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return core().idle() && target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--repath > 0) return;
            repath = 8;
            Vec3 from = position().subtract(target.position());
            double a = Math.atan2(from.z, from.x) + 0.6;
            getNavigation().moveTo(target.getX() + Math.cos(a) * CIRCLE_RADIUS, target.getY(), target.getZ() + Math.sin(a) * CIRCLE_RADIUS, 1.0);
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    // ---- turns, darts, the howl and the rout ---------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().in(Step.FLEE)) {
            fleeTick();
            return;
        }
        LivingEntity target = getTarget();
        if ((target == null || !target.isAlive()) && tickCount % 10 == 0) target = adoptPackTarget();
        if (core().in(Step.TELL)) {
            holdStill();
            if (target != null) face(target.position().subtract(position()));
            if (core().elapsed() % 4 == 2) crouchDust(level).trail(this, 1);
        } else if (core().in(Step.DART)) {
            dartTick(level);
        } else if (core().in(Step.RETREAT)) {
            retreatTick(target);
        } else if (core().in(Step.HOWL)) {
            holdStill();
            if (core().elapsed() % 3 == 0) level.sendParticles(BREATH, getX(), getY() + getBbHeight() * 1.1, getZ(), 2, 0.15, 0.2, 0.15, 0.02);
        } else if (core().idle() && target != null && target.isAlive()) {
            if (alpha && level.getGameTime() >= nextHowl) howl(level, target);
            else if (myTurn()) startTurn(level, target);
        }
    }

    /** No target of its own: take the one a packmate is after. */
    private @Nullable LivingEntity adoptPackTarget() {
        for (Gloamhound h : pack()) {
            LivingEntity t = h.getTarget();
            if (h != this && t != null && t.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(t) && !(t instanceof Gloamhound)) {
                setTarget(t);
                return t;
            }
        }
        return null;
    }

    /** Ready to take a turn: idle, on its feet, its target alive, within dart range and in sight. */
    private boolean readyForTurn() {
        LivingEntity t = getTarget();
        return core().idle() && t != null && t.isAlive() && onGround() && !isInWater() && distanceTo(t) <= DART_RANGE
                && getSensing().hasLineOfSight(t);
    }

    /** The turn token is held through the crouch and the dart. */
    public boolean holdsTurn() {
        return core().in(Step.TELL) || core().in(Step.DART);
    }

    /**
     * Its turn: no packmate holds the turn, the last dart ended at least {@link #TURN_GAP} ticks ago, and of the
     * packmates ready to go it waited longest (ties go to the lowest entity id).
     */
    private boolean myTurn() {
        if (!readyForTurn()) return false;
        long now = level().getGameTime();
        Gloamhound pick = null;
        for (Gloamhound h : pack()) {
            if (h.holdsTurn() || (h.dartEndAt != Long.MIN_VALUE && now < h.dartEndAt + TURN_GAP)) return false;
            if (h != this && !h.readyForTurn()) continue;
            if (pick == null || h.turnAt < pick.turnAt || (h.turnAt == pick.turnAt && h.getId() < pick.getId())) pick = h;
        }
        return pick == this;
    }

    private void startTurn(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return;
        turnAt = level.getGameTime();
        holdStill();
        crouchDust(level).withSound(ModSounds.GLOAMHOUND_GROWL.get(), 1.0F, alpha ? 0.85F : 1.0F).play(this);
    }

    private Telegraph crouchDust(ServerLevel level) {
        return Telegraph.particles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().below())), 6, 0.35, 0.05);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL) {
            LivingEntity target = getTarget();
            Vec3 to = target == null ? Vec3.ZERO : target.position().subtract(position()).multiply(1, 0, 1);
            if (to.lengthSqr() < 1.0E-4 || !startPhase(Step.DART, DART_TICKS, target)) {
                endDart();
                return;
            }
            dartDir = to.normalize();
            face(dartDir);
        } else if (ended == Step.DART) {
            endDart();
        } else {
            core().end();
        }
    }

    private void dartTick(ServerLevel level) {
        getNavigation().stop();
        getMoveControl().setWait();
        face(dartDir);
        setDeltaMovement(dartDir.x * DART_SPEED, getDeltaMovement().y, dartDir.z * DART_SPEED);
        if (core().elapsed() % 2 == 0) crouchDust(level).play(this);
        AABB reach = getBoundingBox().inflate(0.25).move(dartDir.scale(0.4));
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, reach, this::canBite)) {
            bite(level, victim);
            return;
        }
        if (horizontalCollision && core().elapsed() > 1) endDart();
    }

    private boolean canBite(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Gloamhound) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    /** The bite: its attack damage (4, the alpha's 6). */
    private void bite(ServerLevel level, LivingEntity victim) {
        if (MechanicEvent.post(this, "bite", victim)) {
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE;
            if (victim.hurtServer(level, damageSources().mobAttack(this), damage)) {
                bites++;
                setLastHurtMob(victim);
            }
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.GLOAMHOUND_BITE.get(), getSoundSource(), 1.0F, alpha ? 0.85F : 1.0F);
        endDart();
    }

    /** The dart is over (it bit, missed or hit a wall): it falls back, and the turn passes on. */
    private void endDart() {
        dartEndAt = level().getGameTime();
        setDeltaMovement(getDeltaMovement().multiply(0.2, 1.0, 0.2));
        if (!startPhase(Step.RETREAT, RETREAT_TICKS, getTarget())) core().end();
    }

    private void retreatTick(@Nullable LivingEntity target) {
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (core().elapsed() % 5 != 0) return;
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
        Vec3 spot = target.position().add(away.normalize().scale(CIRCLE_RADIUS + 1.0));
        getNavigation().moveTo(spot.x, spot.y, spot.z, 1.2);
    }

    /**
     * The alpha's howl: Speed I for {@link #HOWL_SPEED_TICKS} to every packmate within {@link #HOWL_RADIUS}, itself
     * included. The next one is {@link #HOWL_EVERY} ticks away.
     */
    private void howl(ServerLevel level, LivingEntity target) {
        nextHowl = level.getGameTime() + HOWL_EVERY;
        if (!startPhase(Step.HOWL, HOWL_TICKS, target)) return;
        howls++;
        holdStill();
        double y = getY() + getBbHeight();
        level.playSound(null, getX(), y, getZ(), ModSounds.GLOAMHOUND_HOWL.get(), getSoundSource(), 3.0F, 1.0F);
        for (int i = 0; i < RING_POINTS; i++) {                               // how far the howl carries
            double a = i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(HOWL_RING, getX() + Math.cos(a) * HOWL_RADIUS, getY() + 0.2, getZ() + Math.sin(a) * HOWL_RADIUS, 1, 0.0, 0.05, 0.0, 0.0);
        }
        for (Gloamhound h : pack()) {
            if (h.distanceTo(this) > HOWL_RADIUS || !MechanicEvent.post(this, "howl_speed", h)) continue;
            h.addEffect(new MobEffectInstance(MobEffects.SPEED, HOWL_SPEED_TICKS, HOWL_SPEED_LEVEL), this);
            level.sendParticles(HOWL_RING, h.getX(), h.getY() + h.getBbHeight() * 0.7, h.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (alpha && level() instanceof ServerLevel level) rout(level, source);
    }

    /** The counter landed: the alpha is dead, and every packmate within range flees its killer. */
    private void rout(ServerLevel level, DamageSource source) {
        Entity killer = source.getEntity();
        Vec3 from = killer != null ? killer.position() : position();
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() * 0.6, getZ(), 16, 0.4, 0.3, 0.4, 0.03);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.GLOAMHOUND_ROUT.get(), getSoundSource(), 1.5F, 1.0F);
        for (Gloamhound h : new ArrayList<>(pack())) {
            if (h != this) h.flee(killer instanceof LivingEntity by ? by : null, from);
        }
    }

    /** Drops its target and runs from {@code from} for {@link #FLEE_TICKS}; false if vetoed. Public as a test hook. */
    public boolean flee(@Nullable LivingEntity from, Vec3 fromSpot) {
        if (!startPhase(Step.FLEE, FLEE_TICKS, from)) return false;
        fleeFrom = from;
        fleeSpot = fromSpot;
        setTarget(null);
        getNavigation().stop();
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.3, 0.2, 0.3, 0.02);
        }
        return true;
    }

    private void fleeTick() {
        setTarget(null);
        if (core().elapsed() % 10 != 0) return;
        Vec3 threat = fleeFrom != null && fleeFrom.isAlive() ? fleeFrom.position() : fleeSpot;
        Vec3 away = DefaultRandomPos.getPosAway(this, 16, 7, threat);
        if (away == null) away = DefaultRandomPos.getPosAway(this, 6, 3, threat);   // cornered: a short run beats none
        if (away != null) getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
    }

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();                     // drop a step the navigation queued earlier this tick
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Bites that landed so far. Public as a test hook. */
    public int bites() {
        return bites;
    }

    /** Howls so far. Public as a test hook. */
    public int howls() {
        return howls;
    }

    // ---- save and load ---------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Pack", packId().toString());
        output.putBoolean("Alpha", alpha);
    }

    /** The pack and who leads it survive a reload; the alpha's modifiers come back with the saved attributes. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String pack = input.getStringOr("Pack", "");
        if (!pack.isEmpty()) {
            try {
                packId = UUID.fromString(pack);
            } catch (IllegalArgumentException e) {
                packId = null;
            }
        }
        alpha = input.getBooleanOr("Alpha", false);
        core().setFlag(ALPHA_FLAG, alpha);
        xpReward = alpha ? 10 : 5;
        refreshDimensions();                            // the alpha's scale came back with its attributes
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.GLOAMHOUND_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GLOAMHOUND_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GLOAMHOUND_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return (alpha ? 0.8F : 1.0F) + random.nextFloat() * 0.1F;
    }
}
