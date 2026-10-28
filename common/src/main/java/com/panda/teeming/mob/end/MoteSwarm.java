package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mote Swarm: a drifting cloud of tiny star-white motes round a faint core, one mob drawn as many points. It has one
 * mote for every point of health it has left (thirty at full), and draws only those: it thins as it weakens.
 *
 * <p>It drifts after whatever it hunts, slower than a walk, and settles round them. Once it has wrapped its target for
 * half a second it gathers (the tell, 8 ticks): it stops dead, pulls in tight round its core and brightens, with a
 * chime that rises in three steps and star sparks drawn in. Then it surges where it gathered, for 10 ticks: its motes
 * stream through everyone inside it, players and its target alike, and each mote that touches is a blow of its
 * attack (1) through armour, at most 3 on one body in a tick. A full swarm's thirty motes so pass through a body over the
 * surge for 30 before armour; a thinner swarm has fewer motes and its surge is shorter. The motes hurt in place: they
 * never knock anyone back, so a surge at an island's edge throws no one off it. Then it loosens for 2 seconds, drifting
 * after its target again, before it can gather again.
 *
 * <p>Damage taken: a plain single-target hit (a melee blow aimed at it, an arrow, a trident or any other projectile)
 * passes through the cloud and does a quarter. Anything that spreads does triple and knocks motes loose: a sweep (a
 * melee blow at something beside it that sweeps into it), explosions (fireworks included), dragon's breath, lightning,
 * and splash and lingering potions. Which damage types spread is the {@link #SCATTERS} tag; thrown potions, lingering
 * clouds and sweeps are told apart by their source. Everything else (fire, a fall, magic with no thrower) is taken as
 * it comes.
 */
public class MoteSwarm extends TeemingMonster {
    /** IDLE drifting or wrapped round its target, TELL the gather, SURGE the motes streaming through, LOOSEN after. */
    public enum Step implements Phase { IDLE, TELL, SURGE, LOOSEN }

    /** Motes at full health: one for each point of the base look's health. */
    public static final int MOTES = 30;
    /** At most this many motes land on one body in one tick. */
    public static final int MOTES_PER_TICK = 3;
    /** The gather before it surges: 0.4 s. */
    public static final int TELL_TICKS = 8;
    /** The surge: a full swarm's motes, 3 a tick, pass through a body in exactly this long. */
    public static final int SURGE_TICKS = 10;
    /** After the surge it loosens and drifts this long before it can gather again. */
    public static final int LOOSEN_TICKS = 40;
    /** It gathers once it has been wrapped round its target this many ticks in a row. */
    public static final int WRAP_TICKS = 10;
    /** A plain single-target hit does this share of its damage. */
    public static final float SINGLE_TARGET_DAMAGE = 0.25F;
    /** Damage that spreads does this many times its damage. */
    public static final float SPREAD_DAMAGE = 3.0F;
    /** Its top speed drifting after a target, in blocks a tick: slower than a walk (0.216), so walking away leaves it. */
    public static final double DRIFT_SPEED = 0.15;
    /** A sweep reaches whatever is this far across (and a quarter block up or down) from the body the blow was aimed at. */
    public static final double SWEEP_ACROSS = 1.0, SWEEP_UP = 0.25;
    public static final int XP = 10;

    /** Glow stages: its usual glow, and the gather and surge's flare. */
    public static final int STAGE_CALM = 0, STAGE_FLARE = 1;
    /** The motes it has left are synced in six flag bits from this one, for the model to draw. */
    public static final int SHOWN_BIT = 8;
    private static final int SHOWN_MASK = 0x3F;

    /** Damage types that spread, which do triple. */
    public static final TagKey<DamageType> SCATTERS = TagKey.create(Registries.DAMAGE_TYPE, TeemingMod.id("mote_swarm_scatters"));

    /** How a blow lands on the cloud. */
    public enum Blow {
        /** It spreads through the cloud: triple. */
        SPREAD,
        /** One point passing through: a quarter. */
        SINGLE,
        /** Anything else: as it comes. */
        PLAIN
    }

    private static final DustParticleOptions STAR = new DustParticleOptions(0xF4F2FF, 0.7F);
    private static final DustParticleOptions CREAM = new DustParticleOptions(0xF0E6CC, 0.7F);
    private static final DustParticleOptions ROSE = new DustParticleOptions(0xEAA2B6, 0.7F);

    /** Motes already through each body this surge, by entity id. */
    private final Map<Integer, Integer> through = new HashMap<>();
    private int wrapped;
    private int stings;
    private int surges;

    public MoteSwarm(EntityType<? extends MoteSwarm> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("mote_swarm", 0.2).add(Attributes.FLYING_SPEED, DRIFT_SPEED);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // drifts about while it hunts nothing; it flies itself after a target
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return core().idle() && getTarget() == null && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && getTarget() == null && super.canContinueToUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    @Override
    public int visualStages() {
        return 2;
    }

    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.TELL || phase == Step.SURGE ? STAGE_FLARE : STAGE_CALM);
        return true;
    }

    // ---- the motes ---------------------------------------------------------------------------------------------------

    /** The motes it has left: one for every thirtieth of its health, rounded up; none once it is dead. */
    public int motes() {
        if (isDeadOrDying() || getMaxHealth() <= 0.0F) return 0;
        return Mth.clamp(Mth.ceil(MOTES * getHealth() / getMaxHealth() - 1.0E-4F), 1, MOTES);
    }

    /** The motes the model draws, read from the synced flags (both sides). */
    public int shownMotes() {
        return shown(core().flags());
    }

    /** The mote count kept in a set of flags. */
    public static int shown(int flags) {
        return flags >>> SHOWN_BIT & SHOWN_MASK;
    }

    private void show(int motes) {
        if (shownMotes() == motes) return;
        for (int bit = 0; bit < 6; bit++) core().setFlag(SHOWN_BIT + bit, (motes >> bit & 1) != 0);
    }

    // ---- the cycle ---------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (isDeadOrDying()) return;                       // the model scatters the motes it last drew as it dies
        show(motes());
        if (!isNoAi()) core().setStage(core().in(Step.TELL) || core().in(Step.SURGE) ? STAGE_FLARE : STAGE_CALM);
        if (core().in(Step.TELL)) {
            gatherTick(level);
        } else if (core().in(Step.SURGE) && core().elapsed() > 0 && core().elapsed() < SURGE_TICKS) {
            sting(level);
        }
        if (isNoAi() || !core().idle()) return;
        LivingEntity target = getTarget();
        wrapped = hunts(target) && touches(target) ? wrapped + 1 : 0;
        if (wrapped >= WRAP_TICKS) gather(level, target);
    }

    /** The gather: it stops dead, pulls in tight and brightens, and the chime starts to rise. False if it was vetoed. */
    private boolean gather(ServerLevel level, LivingEntity target) {
        wrapped = 0;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        setDeltaMovement(Vec3.ZERO);
        playSound(ModSounds.MOTE_SWARM_GATHER.get(), 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), middleY(), getZ(), 3, 0.5, 0.4, 0.5, 0.01);
        return true;
    }

    /** The gather held: star sparks are drawn in to the core, and the chime rises a step at a third and two thirds. */
    private void gatherTick(ServerLevel level) {
        int t = core().elapsed();
        if (t % 2 == 0) {
            double a = random.nextDouble() * Mth.TWO_PI, r = 1.2 + random.nextDouble() * 0.6;
            double px = getX() + Math.cos(a) * r, pz = getZ() + Math.sin(a) * r, py = middleY() - 0.6 + random.nextDouble() * 1.2;
            // a speed-0 count-0 particle moves along its offset: each spark flies in toward the core
            level.sendParticles(ParticleTypes.END_ROD, true, true, px, py, pz, 0, getX() - px, middleY() - py, getZ() - pz, 0.12);
        }
        if (t % 2 == 1) level.sendParticles(dust(), true, true, getX(), middleY(), getZ(), 2, 0.25, 0.25, 0.25, 0.0);
        if (t == 3) playSound(ModSounds.MOTE_SWARM_GATHER.get(), 1.0F, 1.06F);
        if (t == 6) playSound(ModSounds.MOTE_SWARM_GATHER.get(), 1.0F, 1.4F);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level && startPhase(Step.SURGE, SURGE_TICKS, core().target())) {
            surges++;
            through.clear();
            playSound(ModSounds.MOTE_SWARM_SURGE.get(), 1.2F, 1.0F);
            level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), middleY(), getZ(), 4, 0.3, 0.3, 0.3, 0.1);
            sting(level);
            return;
        }
        if (ended == Step.SURGE && startPhase(Step.LOOSEN, LOOSEN_TICKS, core().target())) return;
        core().end();
    }

    /**
     * One tick of the surge: everyone inside takes the motes that reach them this tick, at most 3, and no more than the
     * swarm's motes over the whole surge. Each mote is a blow of its own: its attack, through armour, without knockback.
     */
    private void sting(ServerLevel level) {
        if (!isAlive() || isDeadOrDying()) return;
        int motes = motes();
        float each = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean landed = false;
        for (LivingEntity v : inside()) {
            int passed = through.getOrDefault(v.getId(), 0);
            int n = Math.min(MOTES_PER_TICK, motes - passed);
            if (n <= 0 || !MechanicEvent.post(this, "sting", v)) continue;
            through.put(v.getId(), passed + n);
            int hit = 0;
            for (int i = 0; i < n && v.isAlive(); i++) {
                v.damageCooldownTime = 0;                   // each mote is a blow of its own: the last one's cooldown must not eat it
                if (AstralAugur.GroundMarks.hurtInPlace(level, v, damageSources().mobAttack(this), each)) hit++;
            }
            if (hit == 0) continue;
            stings += hit;
            landed = true;
            setLastHurtMob(v);
            level.sendParticles(dust(), true, true, v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 1 + hit, 0.3, 0.4, 0.3, 0.0);
        }
        if (landed && core().elapsed() % 2 == 0) playSound(ModSounds.MOTE_SWARM_STING.get(), 0.8F, 0.95F + random.nextFloat() * 0.1F);
    }

    /** Who a surge stings: everyone whose hitbox is inside the cloud's, players not in creative or spectator and its target. */
    public List<LivingEntity> inside() {
        LivingEntity target = getTarget() != null ? getTarget() : core().target();
        return new ArrayList<>(level().getEntitiesOfClass(LivingEntity.class, getBoundingBox(), e -> stingable(e, target)));
    }

    private boolean stingable(LivingEntity e, @Nullable LivingEntity target) {
        if (e == this || !e.isAlive() || e.isSpectator() || e instanceof MoteSwarm) return false;
        if (e instanceof Player p) return !p.isCreative();
        return e == target;
    }

    /** True while its cloud wraps {@code e}: their hitboxes meet. */
    public boolean touches(LivingEntity e) {
        return getBoundingBox().intersects(e.getBoundingBox());
    }

    private boolean hunts(@Nullable LivingEntity e) {
        return e != null && e.isAlive() && !e.isRemoved() && e.level() == level() && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---- the drift ---------------------------------------------------------------------------------------------------

    /** It drifts after its target, slower than a walk; gathering and surging, it hangs still where it is. */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        if (core().in(Step.TELL) || core().in(Step.SURGE)) {
            steerSelf();
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        LivingEntity target = getTarget();
        if (!hunts(target)) return;
        steerSelf();
        fly(target.getBoundingBox().getCenter(), DRIFT_SPEED);
    }

    /** Its own steering flies it: the drifting goal's path and the move control stop, so nothing adds to its speed. */
    private void steerSelf() {
        getNavigation().stop();
        getMoveControl().setWait();
    }

    private void fly(Vec3 to, double speed) {
        Vec3 d = to.subtract(middle());
        double length = d.length();
        Vec3 want = length < 1.0E-3 ? Vec3.ZERO : d.scale(Math.min(speed, length * 0.3) / length);
        Vec3 v = getDeltaMovement().lerp(want, 0.3);
        if (v.length() > speed) v = v.normalize().scale(speed);
        setDeltaMovement(v);
        if (want.horizontalDistanceSqr() > 1.0E-6) {
            float yaw = (float) (Mth.atan2(want.z, want.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }

    private Vec3 middle() {
        return position().add(0.0, getBbHeight() * 0.5, 0.0);
    }

    private double middleY() {
        return getY() + getBbHeight() * 0.5;
    }

    // ---- damage taken ------------------------------------------------------------------------------------------------

    /**
     * How {@code source} lands on the cloud: SPREAD for damage types in {@link #SCATTERS}, splash and lingering potions
     * and sweeps; SINGLE for projectiles and melee blows aimed at it; PLAIN for the rest.
     */
    public Blow blow(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (source.is(SCATTERS) || direct instanceof AbstractThrownPotion || direct instanceof AreaEffectCloud || swept(source)) return Blow.SPREAD;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return Blow.SINGLE;
        if (direct instanceof LivingEntity && direct == source.getEntity()) return Blow.SINGLE;
        return Blow.PLAIN;
    }

    /**
     * A sweep: a melee blow from an attacker who hurt something else beside the cloud this same tick. A sweeping swing
     * lands on the body it was aimed at first, then on everything round that body, so a blow that finds another body
     * already hurt by the same attacker this tick, within the sweep's reach of the cloud, is the sweep and not the blow
     * aimed at the cloud.
     */
    private boolean swept(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || source.is(DamageTypeTags.IS_PROJECTILE)) return false;
        AABB reach = getBoundingBox().inflate(SWEEP_ACROSS, SWEEP_UP, SWEEP_ACROSS);
        return !level().getEntitiesOfClass(LivingEntity.class, reach,
                e -> e != this && e != attacker && e.getLastHurtByMob() == attacker && e.getLastHurtByMobTimestamp() == e.tickCount).isEmpty();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Blow blow = isDeadOrDying() || amount <= 0.0F ? Blow.PLAIN : blow(source);
        float scaled = switch (blow) {
            case SPREAD -> amount * SPREAD_DAMAGE;
            case SINGLE -> amount * SINGLE_TARGET_DAMAGE;
            case PLAIN -> amount;
        };
        boolean hurt = super.hurtServer(level, source, scaled);
        if (!hurt) return false;
        double y = middleY();
        if (blow == Blow.SPREAD) {
            // motes knocked loose: a burst flung out of the cloud
            level.playSound(null, getX(), y, getZ(), ModSounds.MOTE_SWARM_SCATTER.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), y, getZ(), 6, 0.4, 0.4, 0.4, 0.12);
            level.sendParticles(dust(), true, true, getX(), y, getZ(), 10, 0.7, 0.6, 0.7, 0.0);
        } else if (blow == Blow.SINGLE) {
            // the blow passes through the cloud: the motes part round it and close again
            level.playSound(null, getX(), y, getZ(), ModSounds.MOTE_SWARM_GLANCE.get(), getSoundSource(), 0.8F, 1.0F);
            level.sendParticles(dust(), true, true, getX(), y, getZ(), 3, 0.3, 0.3, 0.3, 0.0);
        }
        return true;
    }

    /** A dead swarm finishes nothing it started: no surge goes on through its death. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        through.clear();
        if (!level().isClientSide()) core().end();
    }

    // a cloud neither pushes nor is pushed, and never falls
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected void doPush(Entity other) {
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    private boolean ember() {
        return "ember_motes".equals(look());
    }

    private DustParticleOptions dust() {
        return ember() ? (random.nextBoolean() ? CREAM : ROSE) : STAR;
    }

    // ---- test hooks --------------------------------------------------------------------------------------------------

    /** Motes that have landed on anyone, and surges begun, since it spawned. Public as test hooks. */
    public int stings() {
        return stings;
    }

    public int surges() {
        return surges;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.MOTE_SWARM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MOTE_SWARM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MOTE_SWARM_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + random.nextFloat() * 0.15F;
    }
}
