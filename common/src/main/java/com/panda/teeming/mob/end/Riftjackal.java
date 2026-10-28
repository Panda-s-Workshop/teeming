package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Zone;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Relative;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Riftjackal: a lean void jackal of the End highlands that hunts in packs of two or three, one of them the alpha (40
 * health, a little bigger, a second pair of rift sheets along its back). It never simply runs in to bite: the pack
 * circles its target and the jackals blink in one at a time.
 *
 * <p>Tell (8 ticks): it crouches low, its spine light flares, it hums, and a violet shimmer gathers on the ground at
 * the spot it picked: up to 4 blocks from its target, at the target's side or back as the target faces when the
 * crouch starts. Then it blinks there (a puff of portal light where it was and where it lands), facing the target, and
 * bites for its attack damage if the target is still within reach. Where it vanished it leaves a rift for 60 ticks: a
 * flat, flickering violet tear on the ground, an area with no blocks. Anything but a Riftjackal that steps into a rift
 * is thrown 8 blocks in a random direction onto safe ground ({@link SafeTeleport}); with no safe ground at that range
 * nothing happens. Each jackal blinks at most once every 60 ticks; in a pack a crouch starts at most every 20 ticks
 * (a third of 60), so a pack of three keeps each jackal to its rhythm and never blinks two at once.
 *
 * <p>Counter: watch the crouch and turn to face the shimmer. A jackal that lands in its target's sight (within 60
 * degrees of where the target looks) is thrown back through its own rift: it lands, the tear snaps shut, and it is
 * flung 8 blocks away from its target onto safe ground, so it bites nothing and leaves no rift. Failing it: a bite
 * from your blind side and one more rift on the ground.
 *
 * <p>Pack membership (the pack id and who leads it) and the blink cooldown are saved; rifts are not, so a reload or
 * the jackal's death or removal closes them.
 */
public class Riftjackal extends TeemingMonster {
    /** IDLE, the crouch (tell), the landing and its bite, thrown back through its own rift (the counter). */
    public enum Step implements Phase { IDLE, TELL, LAND, FLUNG }

    public static final int TELL_TICKS = 8;               // 0.4 s crouched before the blink
    /** Each jackal blinks at most once in this many ticks, counted from crouch to crouch. */
    public static final int BLINK_EVERY = 60;
    /** In a pack a crouch starts at most once in this many ticks: a third of {@link #BLINK_EVERY}, for packs of three. */
    public static final int TURN_GAP = 20;
    /** It starts its crouch with its target this close and in sight. */
    public static final double BLINK_REACH = 16.0;
    /** The landing is at most this far from the target (and at least {@link #LAND_NEAR}). */
    public static final double LAND_FAR = 4.0;
    public static final double LAND_NEAR = 2.0;
    /** It bites as it lands when its target is this close. */
    public static final double BITE_REACH = 2.5;
    public static final float BITE = 1.0F;                // x attack damage
    public static final int LAND_TICKS = 8;
    /** A rift stays open this long. */
    public static final int RIFT_TICKS = 60;
    /** A rift throws whatever steps in this far; the counter throws the jackal itself as far. */
    public static final double RIFT_THROW = 8.0;
    /** The rift tear: half its length and half its width, lying flat; something whose middle is inside steps in. */
    public static final double RIFT_HALF_LENGTH = 0.9;
    public static final double RIFT_HALF_WIDTH = 0.5;
    public static final double RIFT_HEIGHT = 0.5;
    /** A landing within this many degrees of where its target looks is seen coming: the counter. */
    public static final double SEEN_ANGLE = 60.0;
    public static final int FLUNG_TICKS = 10;
    /** How far out the waiting jackals circle their target. */
    public static final double CIRCLE_RADIUS = 5.0;

    public static final float ALPHA_HEALTH = 40.0F;
    public static final double ALPHA_SCALE = 1.15;
    public static final int PACK_MAX = 3;
    /** Jackals of one pack this close count as one pack. */
    public static final double PACK_RANGE = 24.0;
    /** A jackal spawned alone (egg, command) joins a pack with room this close, or founds one as its alpha. */
    public static final double JOIN_RANGE = 8.0;

    /** {@code MobCore} flag bit that marks the alpha, so clients show its second pair of rift sheets. */
    public static final int ALPHA_FLAG = 0;
    /** Glow stages: the rift at rest, and flared through the crouch. */
    public static final int GLOW_REST = 0;
    public static final int GLOW_FLARE = 1;

    private static final long NEVER = Long.MIN_VALUE;
    private static final Identifier ALPHA_MODIFIER = TeemingMod.id("riftjackal_alpha");
    private static final DustParticleOptions RIFT_EDGE = new DustParticleOptions(0x9B3DFF, 0.9F);
    private static final DustParticleOptions RIFT_CORE = new DustParticleOptions(0xE6C4FF, 0.7F);
    private static final DustParticleOptions STAR = new DustParticleOptions(0xFFF6FF, 0.5F);
    private static final DustParticleOptions FLARE = new DustParticleOptions(0xB65CFF, 1.1F);

    // ---- the pack (a linked group, as the Gloamhound's: an id saved on every member, one alpha, one turn at a time) --

    private @Nullable UUID packId;
    private boolean alpha;
    /** Game time its last crouch started; {@link #NEVER} until its first. */
    private long crouchAt = NEVER;
    /** Where the blink it is crouching for will land, picked as the crouch starts. */
    private @Nullable Vec3 landing;
    private int blinks;
    private int bites;
    private int snaps;
    private int thrown;
    /** Its rifts: kept only in memory, ticked from {@code tick()} so they keep working whatever its AI does. */
    private final Zone rifts = new Zone(new RiftShape(), 2)
            .affects(this::riftTakes, this::riftThrow)
            .drawn(this::drawRift);

    public Riftjackal(EntityType<? extends Riftjackal> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("riftjackal", 0.3);
    }

    @Override
    public int visualStages() {
        return 2;
    }

    /** What a natural spawn group shares: the pack its first jackal founded, and that jackal's look. */
    public record PackData(UUID id, String look) implements SpawnGroupData {}

    /** Its pack's id; until it has settled into a pack, its own id (a pack of one). */
    public UUID packId() {
        return packId != null ? packId : getUUID();
    }

    public boolean isAlpha() {
        return alpha;
    }

    /** Every living jackal of this pack within {@link #PACK_RANGE}, itself included. Server only. */
    public List<Riftjackal> pack() {
        UUID id = packId();
        return level().getEntitiesOfClass(Riftjackal.class, getBoundingBox().inflate(PACK_RANGE), j -> j.isAlive() && id.equals(j.packId()));
    }

    /** Joins pack {@code id}, as its alpha or not. Public as a test hook. */
    public void joinPack(UUID id, boolean asAlpha) {
        packId = id;
        setAlpha(asAlpha);
    }

    /** Makes {@code alpha} lead a new pack with {@code members}. Public as a test hook. */
    public static UUID formPack(Riftjackal alpha, Riftjackal... members) {
        UUID id = UUID.randomUUID();
        alpha.joinPack(id, true);
        for (Riftjackal member : members) member.joinPack(id, false);
        return id;
    }

    /** The alpha is the same jackal at 40 health and 1.15x, with a second pair of rift sheets. Fills its health. */
    private void setAlpha(boolean on) {
        alpha = on;
        core().setFlag(ALPHA_FLAG, on);
        xpReward = on ? 20 : 12;
        if (level().isClientSide()) return;
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        for (AttributeInstance a : new AttributeInstance[] {health, scale}) if (a != null) a.removeModifier(ALPHA_MODIFIER);
        if (on) {
            if (health != null) health.addOrReplacePermanentModifier(new AttributeModifier(ALPHA_MODIFIER,
                    ALPHA_HEALTH - health.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
            if (scale != null) scale.addOrReplacePermanentModifier(new AttributeModifier(ALPHA_MODIFIER,
                    ALPHA_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        refreshDimensions();
        setHealth(on ? getMaxHealth() : Math.min(getHealth(), getMaxHealth()));
    }

    /**
     * A natural spawn group is one pack: its first jackal founds it as the alpha and the rest join, wearing the alpha's
     * look. A jackal spawned on its own joins a pack with room within {@link #JOIN_RANGE}, else it founds one.
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

    private void settle(EntityGetter level) {
        Riftjackal host = packWithRoom(level);
        if (host != null) joinPack(host.packId(), false);
        else joinPack(UUID.randomUUID(), true);
    }

    private @Nullable Riftjackal packWithRoom(EntityGetter level) {
        Riftjackal best = null;
        for (Riftjackal j : level.getEntitiesOfClass(Riftjackal.class, getBoundingBox().inflate(JOIN_RANGE),
                j -> j != this && j.isAlive() && j.distanceTo(this) <= JOIN_RANGE)) {
            List<Riftjackal> pack = j.pack();
            if (pack.size() >= PACK_MAX || pack.stream().noneMatch(Riftjackal::isAlpha)) continue;
            if (best == null || j.distanceTo(this) < best.distanceTo(this)) best = j;
        }
        return best;
    }

    // ---- goals ---------------------------------------------------------------------------------------------------------

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
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Between blinks it circles its target {@link #CIRCLE_RADIUS} out; it never runs in to bite. */
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
            double a = Math.atan2(from.z, from.x) + 0.5;
            getNavigation().moveTo(target.getX() + Math.cos(a) * CIRCLE_RADIUS, target.getY(), target.getZ() + Math.sin(a) * CIRCLE_RADIUS, 1.0);
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    // ---- the hunt: turns, the crouch, the blink and the bite -----------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            if (packId == null && isAlive()) settle(level);
            rifts.tick(level);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if ((target == null || !target.isAlive()) && tickCount % 10 == 0) target = adoptPackTarget();
        if (core().in(Step.TELL)) {
            holdStill();
            if (target != null) face(target.position().subtract(position()));
            tellTick(level);
        } else if (core().in(Step.LAND)) {
            holdStill();
            if (target != null) face(target.position().subtract(position()));
        } else if (core().in(Step.FLUNG)) {
            holdStill();
        } else if (core().idle() && target != null && target.isAlive() && myTurn()) {
            startTurn(level, target);
        }
    }

    /** No target of its own: take the one a packmate is after. */
    private @Nullable LivingEntity adoptPackTarget() {
        for (Riftjackal j : pack()) {
            LivingEntity t = j.getTarget();
            if (j != this && t != null && t.isAlive() && canBite(t)) {
                setTarget(t);
                return t;
            }
        }
        return null;
    }

    private boolean canBite(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Riftjackal) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    /** Its own blink is due: idle, on its feet, its target alive, in reach and in sight, 60 ticks since its last crouch. */
    private boolean ready(long now) {
        LivingEntity t = getTarget();
        return core().idle() && t != null && canBite(t) && onGround() && !isInWater() && distanceTo(t) <= BLINK_REACH
                && (crouchAt == NEVER || now - crouchAt >= BLINK_EVERY) && getSensing().hasLineOfSight(t);
    }

    /** True while it holds its pack's turn: through the crouch. */
    public boolean holdsTurn() {
        return core().in(Step.TELL);
    }

    /**
     * Its turn: its own blink is due, no packmate is crouching, no crouch in the pack started in the last
     * {@link #TURN_GAP} ticks, and of the packmates ready to go it waited longest (ties go to the alpha, then to the
     * lowest entity id).
     */
    private boolean myTurn() {
        long now = level().getGameTime();
        if (!ready(now)) return false;
        Riftjackal pick = null;
        for (Riftjackal j : pack()) {
            if (j.holdsTurn() || (j.crouchAt != NEVER && now - j.crouchAt < TURN_GAP)) return false;
            if (j != this && !j.ready(now)) continue;
            if (pick == null || before(j, pick)) pick = j;
        }
        return pick == this;
    }

    private static boolean before(Riftjackal a, Riftjackal b) {
        if (a.crouchAt != b.crouchAt) return a.crouchAt < b.crouchAt;
        if (a.alpha != b.alpha) return a.alpha;
        return a.getId() < b.getId();
    }

    /** Picks where it will land, then crouches; nothing happens while no landing is safe. */
    private void startTurn(ServerLevel level, LivingEntity target) {
        Vec3 spot = findLanding(level, target);
        if (spot == null || !startPhase(Step.TELL, TELL_TICKS, target)) return;
        crouchAt = level.getGameTime();
        landing = spot;
        holdStill();
        level.playSound(null, getX(), getY(), getZ(), ModSounds.RIFTJACKAL_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        level.playSound(null, spot.x, spot.y, spot.z, ModSounds.RIFTJACKAL_TELL.get(), getSoundSource(), 0.5F, 1.3F);
    }

    /** The crouch: its spine light flares, and a shimmer gathers on the ground where it will land. */
    private void tellTick(ServerLevel level) {
        if (core().elapsed() % 2 != 0) return;
        level.sendParticles(FLARE, true, true, getX(), getY() + getBbHeight() * 0.95, getZ(), 4, 0.12, 0.15, 0.35, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, getX(), getY() + getBbHeight(), getZ(), 3, 0.1, 0.1, 0.3, 0.02);
        if (landing != null) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, landing.x, landing.y + 0.2, landing.z, 6, 0.3, 0.05, 0.3, 0.03);
            for (int i = 0; i < 8; i++) {
                double a = i * (Math.PI / 4) + core().elapsed() * 0.4;
                level.sendParticles(FLARE, true, true, landing.x + Math.cos(a) * 0.5, landing.y + 0.1, landing.z + Math.sin(a) * 0.5, 1, 0.0, 0.02, 0.0, 0.0);
            }
        }
    }

    /**
     * Where a blink at {@code target} lands: the first safe spot at its side or back as it faces now, nearest first
     * ({@link Blink#OFFSETS}, {@link Blink#DISTANCES}), with a clear line to the target. Null when nothing is safe.
     * Public as a test hook.
     */
    public @Nullable Vec3 findLanding(ServerLevel level, LivingEntity target) {
        double facing = Blink.facingAngle(target.getYHeadRot());
        int[] offsets = Blink.OFFSETS.clone();
        if (random.nextBoolean()) { int t = offsets[0]; offsets[0] = offsets[1]; offsets[1] = t; }   // which side first
        int swap = random.nextInt(3);                                                              // and where the back falls among them
        int t2 = offsets[2]; offsets[2] = offsets[swap]; offsets[swap] = t2;
        for (double distance : Blink.DISTANCES) {
            for (int offset : offsets) {
                double a = facing + Math.toRadians(offset);
                Vec3 spot = SafeTeleport.InLevel.spot(level, this, target.getX() + Math.cos(a) * distance, target.getY(), target.getZ() + Math.sin(a) * distance);
                if (spot != null && clearLine(level, spot, target)) return spot;
            }
        }
        return null;
    }

    private boolean clearLine(ServerLevel level, Vec3 spot, LivingEntity target) {
        Vec3 eye = spot.add(0, getEyeHeight(), 0);
        return level.clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    /** The spot its current crouch will land on, or null. Public as a test hook. */
    public @Nullable Vec3 landing() {
        return landing;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) blink(level);
        else rest();
    }

    /** Every phase it enters sets its glow: flared through the crouch, at rest otherwise. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.TELL ? GLOW_FLARE : GLOW_REST);
        return true;
    }

    private void rest() {
        core().end();
        core().setStage(GLOW_REST);
    }

    /**
     * The blink, as the crouch ends: to the landing it picked if that is still safe. Seen coming, the counter throws it
     * on; otherwise it leaves a rift where it vanished and bites if its target is in reach. Public as a test hook.
     */
    public void blink(ServerLevel level) {
        LivingEntity target = getTarget();
        Vec3 to = landing;
        landing = null;
        if (to != null) to = SafeTeleport.InLevel.spot(level, this, to.x, to.y, to.z);
        if (to == null || !MechanicEvent.post(this, "blink", target)) {
            rest();
            return;
        }
        Vec3 from = position();
        boolean seen = target != null && target.isAlive() && Blink.sees(target.getYHeadRot(), to.x - target.getX(), to.z - target.getZ());
        puff(level, from);
        level.playSound(null, from.x, from.y, from.z, ModSounds.RIFTJACKAL_BLINK.get(), getSoundSource(), 1.0F, 1.0F);
        SafeTeleport.InLevel.move(level, this, to);
        if (target != null) face(target.position().subtract(to));
        puff(level, to);
        level.playSound(null, to.x, to.y, to.z, ModSounds.RIFTJACKAL_BLINK.get(), getSoundSource(), 1.0F, 1.15F);
        blinks++;
        if (seen && MechanicEvent.post(this, "snap", target)) {
            snap(level, target, from);
            return;
        }
        if (MechanicEvent.post(this, "rift", target)) {
            rifts.open(level, from, RIFT_TICKS);
        }
        if (target != null && canBite(target) && distanceTo(target) <= BITE_REACH) bite(level, target);
        if (!startPhase(Step.LAND, LAND_TICKS, target)) rest();
    }

    /** The bite as it lands: its attack damage. */
    private void bite(ServerLevel level, LivingEntity victim) {
        if (!MechanicEvent.post(this, "bite", victim)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE;
        if (AstralAugur.GroundMarks.hurtInPlace(level, victim, damageSources().mobAttack(this), damage)) {
            bites++;
            setLastHurtMob(victim);
        }
        level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.RIFTJACKAL_BITE.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /**
     * The counter landed: seen as it arrives, it is thrown back through its own rift. The tear it came through snaps
     * shut (no rift is left), and it is flung {@link #RIFT_THROW} blocks away from its target onto safe ground, the
     * first safe direction turning out from straight away. It bites nothing.
     */
    private void snap(ServerLevel level, LivingEntity target, Vec3 from) {
        snaps++;
        level.sendParticles(RIFT_CORE, true, true, from.x, from.y + 0.1, from.z, 24, 0.5, 0.05, 0.5, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 30, 0.3, 0.3, 0.3, 0.15);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.RIFTJACKAL_SNAP.get(), getSoundSource(), 1.2F, 1.0F);
        double away = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
        Vec3 to = SafeTeleport.InLevel.find(level, this, position(), RIFT_THROW, away, p -> clearOfRifts(level, p));
        if (to != null) {
            puff(level, position());
            SafeTeleport.InLevel.move(level, this, to);
            face(target.position().subtract(to));
            puff(level, to);
            level.playSound(null, to.x, to.y, to.z, ModSounds.RIFTJACKAL_SNAP.get(), getSoundSource(), 0.8F, 0.8F);
        }
        if (!startPhase(Step.FLUNG, FLUNG_TICKS, target)) rest();
    }

    private static void puff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, true, true, at.x, at.y + 0.45, at.z, 28, 0.3, 0.4, 0.3, 0.4);
    }

    // ---- rifts (a Zone of flat tears, each thrown open where a blink vanished) -----------------------------------------

    /** Who a rift takes: players and mobs, never a Riftjackal, a passenger, a spectator or a creative player. */
    private boolean riftTakes(LivingEntity e) {
        return e.isAlive() && (e instanceof Mob || e instanceof Player) && !(e instanceof Riftjackal) && e.canUsePortal(false)
                && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    /** A rift throws {@code victim} {@link #RIFT_THROW} blocks in a random direction onto safe ground, or does nothing. */
    private void riftThrow(LivingEntity victim) {
        if (!(level() instanceof ServerLevel level) || !MechanicEvent.post(this, "rift_throw", victim)) return;
        Vec3 from = victim.position();
        Vec3 to = SafeTeleport.InLevel.find(level, victim, from, RIFT_THROW, random.nextDouble() * Math.PI * 2, p -> clearOfRifts(level, p));
        if (to == null) return;
        puff(level, from);
        SafeTeleport.InLevel.move(level, victim, to);
        puff(level, to);
        level.playSound(null, from.x, from.y, from.z, ModSounds.RIFTJACKAL_RIFT.get(), getSoundSource(), 1.0F, 1.0F);
        level.playSound(null, to.x, to.y, to.z, ModSounds.RIFTJACKAL_RIFT.get(), getSoundSource(), 0.8F, 1.2F);
        thrown++;
    }

    /** True when {@code at} lies inside no open rift of any Riftjackal near by, so a throw never lands in another. */
    private static boolean clearOfRifts(ServerLevel level, Vec3 at) {
        AABB box = new AABB(at, at).inflate(32.0);
        for (Riftjackal j : level.getEntitiesOfClass(Riftjackal.class, box, j -> true)) {
            for (Vec3 c : j.rifts.centres()) {
                if (c.distanceToSqr(at) < (RIFT_HALF_LENGTH + 0.6) * (RIFT_HALF_LENGTH + 0.6)) return false;
            }
        }
        return true;
    }

    /** The tear's long axis, steady for a rift where it lies (drawing and stepping in agree on it). */
    private static double riftAngle(Vec3 c) {
        double v = Math.sin(c.x * 12.9898 + c.z * 78.233) * 43758.5453;
        return (v - Math.floor(v)) * Math.PI;
    }

    /** A flat tear: an ellipse {@link #RIFT_HALF_LENGTH} by {@link #RIFT_HALF_WIDTH}, {@link #RIFT_HEIGHT} thick. */
    private static final class RiftShape implements Zone.Shape {
        @Override
        public AABB bounds(Vec3 c) {
            return new AABB(c.x - RIFT_HALF_LENGTH, c.y, c.z - RIFT_HALF_LENGTH, c.x + RIFT_HALF_LENGTH, c.y + RIFT_HEIGHT, c.z + RIFT_HALF_LENGTH);
        }

        @Override
        public boolean holds(Vec3 c, AABB box) {
            if (!box.intersects(bounds(c))) return false;
            double dx = (box.minX + box.maxX) * 0.5 - c.x, dz = (box.minZ + box.maxZ) * 0.5 - c.z;
            double a = riftAngle(c);
            double u = dx * Math.cos(a) + dz * Math.sin(a), v = -dx * Math.sin(a) + dz * Math.cos(a);
            return (u * u) / (RIFT_HALF_LENGTH * RIFT_HALF_LENGTH) + (v * v) / (RIFT_HALF_WIDTH * RIFT_HALF_WIDTH) <= 1.0;
        }
    }

    /** Draws a rift every other tick: a flickering violet lens on the ground, a lilac core line and a few star specks. */
    private void drawRift(ServerLevel level, Vec3 c) {
        if ((tickCount & 1) != 0) return;
        double a = riftAngle(c), ux = Math.cos(a), uz = Math.sin(a);
        double y = c.y + 0.06;
        for (int i = 0; i < 9; i++) {
            double s = i / 4.0 - 1.0;                                   // -1 .. 1 along the tear
            double along = s * RIFT_HALF_LENGTH, half = RIFT_HALF_WIDTH * Math.sqrt(1.0 - s * s);
            for (int side = -1; side <= 1; side += 2) {
                if (random.nextFloat() < 0.25F) continue;                // the edge flickers
                double across = side * half;
                level.sendParticles(RIFT_EDGE, true, true, c.x + ux * along - uz * across, y, c.z + uz * along + ux * across, 1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(RIFT_CORE, true, true, c.x + ux * along * 0.85, y, c.z + uz * along * 0.85, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(3) == 0) {
            double s = random.nextDouble() * 1.6 - 0.8;
            level.sendParticles(STAR, true, true, c.x + ux * s, y + 0.05, c.z + uz * s, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(4) == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, c.x, y, c.z, 1, 0.3, 0.0, 0.3, 0.01);
    }

    /** Opens a rift at {@code at} for {@link #RIFT_TICKS} ticks, as a blink does. Public as a test hook. */
    public void openRift(Vec3 at) {
        if (level() instanceof ServerLevel level) rifts.open(level, at, RIFT_TICKS);
    }

    /** The centres of its open rifts. Public as a test hook. */
    public List<Vec3> rifts() {
        return new ArrayList<>(rifts.centres());
    }

    /** Its death closes its rifts. */
    @Override
    public void die(DamageSource source) {
        rifts.clear();
        landing = null;
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        rifts.clear();
        super.onRemoval(reason);
    }

    // ---- small helpers and test counters -------------------------------------------------------------------------------

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Blinks so far. Public as a test hook. */
    public int blinks() {
        return blinks;
    }

    /** Bites that landed so far. Public as a test hook. */
    public int bites() {
        return bites;
    }

    /** Times the counter threw it back so far. Public as a test hook. */
    public int snaps() {
        return snaps;
    }

    /** Things its rifts threw so far. Public as a test hook. */
    public int throwsSoFar() {
        return thrown;
    }

    /** Ticks until its own next blink may start (0: due). Public as a test hook. */
    public int blinkCooldown() {
        if (crouchAt == NEVER) return 0;
        return (int) Math.max(0L, crouchAt + BLINK_EVERY - level().getGameTime());
    }

    // ---- save and load -----------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Pack", packId().toString());
        output.putBoolean("Alpha", alpha);
        output.putInt("BlinkCooldown", blinkCooldown());
    }

    /** The pack, who leads it and the blink cooldown survive a reload; its rifts and a crouch under way do not. */
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
        xpReward = alpha ? 20 : 12;
        int cooldown = Math.clamp(input.getIntOr("BlinkCooldown", 0), 0, BLINK_EVERY);
        crouchAt = cooldown > 0 ? level().getGameTime() - BLINK_EVERY + cooldown : NEVER;
        refreshDimensions();
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.RIFTJACKAL_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.RIFTJACKAL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.RIFTJACKAL_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return (alpha ? 0.85F : 1.0F) + random.nextFloat() * 0.1F;
    }

    // ---- the blink's aim (pure maths, no game types) ---------------------------------------------------------------------

    /** Where a blink lands round its target and when the target sees it coming, with no game types. */
    public static final class Blink {
        /** Directions it tries, in degrees round from where the target faces: either side, behind, then the back corners. */
        public static final int[] OFFSETS = {90, -90, 180, 135, -135};
        /** Distances from the target it tries, nearest first, up to {@link Riftjackal#LAND_FAR}. */
        public static final double[] DISTANCES = {2.0, 3.0, 4.0};

        private Blink() {}

        /** The angle (atan2 of z over x, radians) of the way a body with head yaw {@code yawDeg} faces. */
        public static double facingAngle(float yawDeg) {
            double yaw = Math.toRadians(yawDeg);
            return Math.atan2(Math.cos(yaw), -Math.sin(yaw));
        }

        /** True when a spot {@code (dx, dz)} from a body with head yaw {@code yawDeg} is within {@link Riftjackal#SEEN_ANGLE}. */
        public static boolean sees(float yawDeg, double dx, double dz) {
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-6) return true;
            double yaw = Math.toRadians(yawDeg);
            double cos = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / len;
            return cos >= Math.cos(Math.toRadians(SEEN_ANGLE)) - 1.0E-9;
        }
    }

    // ---- safe teleport: the End's shared rule for forced teleports ---------------------------------------------------

    /**
     * Safe ground for a forced teleport (a rift, a blink, a warp), written to be shared: a spot counts when the cell
     * under the feet is solid ground, the body has two cells of open air (more for a taller body) with no liquid, fire
     * or other hazard in or under it, and the ground is at most {@link #MAX_RISE} blocks above or {@link #MAX_DROP}
     * below where the search starts. So a teleport never puts anything over the void, into the void below the islands,
     * into lava or fire, or into a wall; when no spot is safe it does nothing. This class holds the column maths with
     * no game types, so it is checked on mock columns; {@link InLevel} reads the cells from a level and moves the body.
     */
    public static final class SafeTeleport {
        public static final int AIR = 0;
        public static final int SOLID = 1;
        /** Water and other fluids: neither ground nor room to stand. */
        public static final int LIQUID = 2;
        public static final int LAVA = 3;
        /** Fire, magma, cactus, berry bushes, powder snow, dripstone, wither roses: never stood in or on. */
        public static final int HAZARD = 4;
        /** Below the bottom of the world. */
        public static final int VOID = 5;
        /** {@link #landing} found nowhere to stand. */
        public static final int NONE = Integer.MIN_VALUE;
        /** The ground may be at most this many blocks above the start, and at most this many below. */
        public static final int MAX_RISE = 2;
        public static final int MAX_DROP = 4;
        /** Open cells a body needs above the ground, at the least. */
        public static final int HEADROOM = 2;
        /** Directions tried round the circle of a fixed-distance teleport. */
        public static final int TRIES = 16;

        private SafeTeleport() {}

        /** The heights tried in a column, nearest the start first, down before up: 0, -1, 1, -2, 2, -3, -4. */
        public static int[] heights() {
            int[] out = new int[MAX_RISE + MAX_DROP + 1];
            int n = 0;
            out[n++] = 0;
            for (int d = 1; d <= Math.max(MAX_RISE, MAX_DROP); d++) {
                if (d <= MAX_DROP) out[n++] = -d;
                if (d <= MAX_RISE) out[n++] = d;
            }
            return out;
        }

        /** Open cells a body {@code height} blocks tall needs: at least {@link #HEADROOM}. */
        public static int headroom(double height) {
            return Math.max(HEADROOM, (int) Math.ceil(height - 1.0E-6));
        }

        /** True when a body can stand with its feet in cell {@code dy}: solid ground under it and air for its headroom. */
        public static boolean standable(IntUnaryOperator cell, int dy, int headroom) {
            if (cell.applyAsInt(dy - 1) != SOLID) return false;
            for (int h = 0; h < headroom; h++) if (cell.applyAsInt(dy + h) != AIR) return false;
            return true;
        }

        /** The height of the feet cell to land in, nearest the start first, or {@link #NONE}. */
        public static int landing(IntUnaryOperator cell, int headroom) {
            for (int dy : heights()) if (standable(cell, dy, headroom)) return dy;
            return NONE;
        }

        /**
         * The directions (radians) a fixed-distance teleport tries, {@code tries} of them evenly round the circle:
         * {@code first}, then a step either side of it in turn, the opposite way last.
         */
        public static double[] angles(double first, int tries) {
            double step = Math.PI * 2 / tries;
            double[] out = new double[tries];
            out[0] = first;
            int n = 1;
            for (int k = 1; n < tries; k++) {
                out[n++] = first + k * step;
                if (n < tries) out[n++] = first - k * step;
            }
            return out;
        }

        /** The world side: cells read from a level, a spot checked against the body's hitbox, and the move itself. */
        public static final class InLevel {
            private InLevel() {}

            /** What one cell of a column is. */
            public static int cell(Level level, BlockPos pos) {
                if (pos.getY() < level.getMinY()) return VOID;
                if (level.isOutsideBuildHeight(pos)) return AIR;
                FluidState fluid = level.getFluidState(pos);
                if (fluid.is(FluidTags.LAVA)) return LAVA;
                if (!fluid.isEmpty()) return LIQUID;
                BlockState state = level.getBlockState(pos);
                if (hazard(state)) return HAZARD;
                return state.getCollisionShape(level, pos).isEmpty() ? AIR : SOLID;
            }

            /** Blocks that hurt whoever stands in or on them. */
            public static boolean hazard(BlockState state) {
                return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                        || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)
                        || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.LAVA);
            }

            /**
             * The spot {@code who} would stand on in the column at {@code (x, z)}, near height {@code y}, or null when
             * that column is unsafe: unloaded, past the world border, no ground in reach, too little room, or a hazard
             * in or under the hitbox (which can straddle columns).
             */
            public static @Nullable Vec3 spot(Level level, Entity who, double x, double y, double z) {
                BlockPos base = BlockPos.containing(x, y + 0.01, z);
                if (!level.hasChunkAt(base) || !level.getWorldBorder().isWithinBounds(base)) return null;
                int dy = landing(d -> cell(level, base.above(d)), headroom(who.getBbHeight()));
                if (dy == NONE) return null;
                BlockPos floor = base.above(dy - 1);
                double top = floor.getY() + level.getBlockState(floor).getCollisionShape(level, floor).max(Direction.Axis.Y);
                Vec3 at = new Vec3(x, top, z);
                AABB box = who.getDimensions(Pose.STANDING).makeBoundingBox(at);
                if (!level.noCollision(who, box) || level.containsAnyLiquid(box)) return null;
                for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.5, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                    if (hazard(level.getBlockState(p)) || level.getFluidState(p).is(FluidTags.LAVA)) return null;
                }
                return at;
            }

            /**
             * The first safe spot exactly {@code distance} blocks across from {@code from}, trying {@link #TRIES}
             * directions from {@code first} round ({@link #angles}), that {@code allowed} accepts; null when none is.
             */
            public static @Nullable Vec3 find(Level level, Entity who, Vec3 from, double distance, double first, Predicate<Vec3> allowed) {
                for (double a : angles(first, TRIES)) {
                    Vec3 at = spot(level, who, from.x + Math.cos(a) * distance, from.y, from.z + Math.sin(a) * distance);
                    if (at != null && allowed.test(at)) return at;
                }
                return null;
            }

            /** Moves {@code who} to {@code to}, keeping where it looks, with no motion and no fall built up. */
            public static void move(ServerLevel level, Entity who, Vec3 to) {
                if (who instanceof ServerPlayer player) {
                    player.teleportTo(level, to.x, to.y, to.z, Relative.ROTATION, 0.0F, 0.0F, false);
                } else {
                    who.teleportTo(to.x, to.y, to.z);
                }
                who.setDeltaMovement(Vec3.ZERO);
                who.resetFallDistance();
                Push.sync(who);
                if (who instanceof Mob mob) mob.getNavigation().stop();
            }
        }
    }
}
