package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindLiquidGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stormeel: a slim eel of the warm and lukewarm oceans that lives and swims in water. Whenever a target comes within
 * 8 blocks it charges: for 1.5 s its organ spots glow brighter, sparks crackle round it and a ring of sparks marks
 * its 5-block reach (tell). Then it discharges: 6 damage to every living thing standing or swimming in water within
 * 5 blocks; things out of the water take nothing. It discharges at most once every 6 s. Counter: get out of the water
 * (onto land, onto a block above the surface, into a boat) or more than 5 blocks away while it glows.
 *
 * <p>Out of water it can't discharge: it flops about, crawls back towards water and dries out (after 15 s it takes
 * 2 damage a second, as fish do). A Stormeel with no AI (a display piece) neither flops nor dries out.
 */
public class Stormeel extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL, PULSE }

    public static final int TELL_TICKS = 30;
    public static final int PULSE_TICKS = 10;
    public static final double PULSE_RADIUS = 5.0;
    public static final float PULSE_DAMAGE = 6.0F;
    /** Ticks from one discharge to the next, at the soonest. */
    public static final int PULSE_EVERY = 120;
    /** It starts charging when its target is this close. */
    public static final double TRIGGER_RANGE = 8.0;
    /** The tell's second half glows at the brightest stage. */
    public static final int BRIGHT_AFTER = 15;
    /** Out of water: 2 damage each time its air runs out (every second, once 300 ticks of air are gone). */
    public static final float DRY_DAMAGE = 2.0F;
    /** {@code core().flag} bit: out of water (clients lay it on its side and let it flop). */
    public static final int BEACHED = 0;
    private static final double FLOP_UP = 0.4, FLOP_SIDE = 0.1;

    private static final int SPARK_COLOR = 0x9FF4FF;
    private static final int RING_POINTS = 36;
    private static final Telegraph CRACKLE = Telegraph.particles(ParticleTypes.ELECTRIC_SPARK, 12, 0.5, 0.5)
            .withSpeed(0.05).withSound(ModSounds.STORMEEL_TELL.get(), 1.0F, 1.0F);
    private static final Telegraph SPARKS = Telegraph.particles(ParticleTypes.ELECTRIC_SPARK, 6, 0.6, 0.5).withSpeed(0.05);
    private static final Telegraph BUBBLES = Telegraph.particles(ParticleTypes.BUBBLE, 6, 0.5, 0.5).withSpeed(0.02);
    private static final Telegraph FIZZLE = Telegraph.particles(ParticleTypes.SMOKE, 6, 0.3, 0.1).withSpeed(0.02);

    /** Ticks until it may discharge again. */
    private int untilPulse;

    public Stormeel(EntityType<? extends Stormeel> type, Level level) {
        super(type, level, Step.values());
        setPathfindingMalus(PathType.WATER, 0.0F);
        moveControl = new SmoothSwimmingMoveControl<>(this, 85, 10, 0.02F, 0.1F, false);
        lookControl = new SmoothSwimmingLookControl(this, 10);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("stormeel", 0.9);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new TryFindLiquidGoal(this, FluidTags.WATER));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 40));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Stormeel.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- living in water -------------------------------------------------------------------------------------------

    /** Swims like a dolphin: thrust along its heading, water drag, a slow sink while it has nothing to chase. */
    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        moveRelative(getSpeed(), input);
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.9));
        if (getTarget() == null) setDeltaMovement(getDeltaMovement().add(0.0, -0.005, 0.0));
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Water spawns: only blocks count as obstruction (the default refuses any liquid in the hitbox). */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** Water is where it wants to be, in any light (the monster default prefers dark land and refuses sunlit water). */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getFluidState(pos).is(FluidTags.WATER) ? 10.0F + level.getPathfindingCostFromLightLevels(pos) : super.getWalkTargetValue(pos, level);
    }

    /** Out of water it uses up its air, then dries out; in water its air is full. */
    @Override
    public void baseTick() {
        int air = getAirSupply();
        super.baseTick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        if (isInWater() || isNoAi()) {
            setAirSupply(getMaxAirSupply());
            return;
        }
        setAirSupply(air - 1);
        if (shouldTakeDrowningDamage()) {
            setAirSupply(0);
            hurtServer(level, damageSources().dryOut(), DRY_DAMAGE);
        }
    }

    /** On land it flops: a hop in a random direction whenever it lands. */
    @Override
    public void aiStep() {
        if (!level().isClientSide() && isAlive() && !isNoAi() && !isInWater() && onGround() && verticalCollision) {
            setDeltaMovement(getDeltaMovement().add((random.nextFloat() * 2 - 1) * FLOP_SIDE, FLOP_UP, (random.nextFloat() * 2 - 1) * FLOP_SIDE));
            setYRot(random.nextFloat() * 360.0F);
            setOnGround(false);
            needsSync = true;
            playSound(ModSounds.STORMEEL_FLOP.get(), 1.0F, getVoicePitch());
        }
        super.aiStep();
    }

    // ---- the charge ------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide() && untilPulse > 0) untilPulse--;
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        core().setFlag(BEACHED, !isInWater() && !isNoAi());
        core().setStage(glowStage());
        if (core().in(Step.TELL)) {
            SPARKS.trail(this, 2);
            BUBBLES.trail(this, 3);
            int t = core().elapsed();
            if (t % 5 == 0) ring(level, 0.0);                                  // how far to get away
            if (t > 0 && t % 10 == 0) playSound(ModSounds.STORMEEL_TELL.get(), 1.0F, 1.0F + 0.1F * (t / 10));
        } else if (core().in(Step.PULSE)) {
            SPARKS.trail(this, 2);
        } else if (!isNoAi() && untilPulse <= TELL_TICKS) {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive() && distanceTo(target) <= TRIGGER_RANGE && getSensing().hasLineOfSight(target)) charge(target);
        }
    }

    /** 0 at rest, 1 in the tell's first half, 2 in its second half and the discharge. */
    private int glowStage() {
        if (core().in(Step.TELL)) return core().elapsed() < BRIGHT_AFTER ? 1 : 2;
        return core().in(Step.PULSE) ? 2 : 0;
    }

    /** Starts the tell if it is free to and in water; true if it did. Ignores the time since the last discharge. Public as a test hook. */
    public boolean charge(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle() || !isInWater()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        CRACKLE.play(this);
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!core().idle()) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().scale(0.6));
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            untilPulse = PULSE_EVERY;
            if (startPhase(Step.PULSE, PULSE_TICKS, core().target())) pulse(level);
            else core().end();
        } else {
            core().end();
        }
    }

    /**
     * The discharge: 6 damage to every living thing in water within 5 blocks but its own kind. Things out of the water
     * are spared (sparks fizzle at their feet); out of water itself, the charge fizzles. Returns how many it hurt.
     * Public as a test hook.
     */
    public int pulse(ServerLevel level) {
        if (!isInWater()) {
            FIZZLE.play(this);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.STORMEEL_FIZZLE.get(), getSoundSource(), 1.0F, 1.0F);
            return 0;
        }
        DamageSource shock = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.LIGHTNING_BOLT), this);
        int hit = 0;
        int spared = 0;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(PULSE_RADIUS))) {
            if (victim == this || victim instanceof Stormeel || victim.distanceTo(this) > PULSE_RADIUS) continue;   // exactly the ring the tell draws
            if (victim instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (!victim.isInWater()) {
                spared++;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY() + 0.1, victim.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
                continue;
            }
            if (!MechanicEvent.post(this, "pulse_hit", victim)) continue;
            if (victim.hurtServer(level, shock, PULSE_DAMAGE)) hit++;
        }
        double y = getY() + getBbHeight() * 0.5;
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, SPARK_COLOR), getX(), y, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 70; i++) {                                         // sparks fill the reach and never land outside it
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * (PULSE_RADIUS - 0.4);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(a) * r, y + (random.nextDouble() - 0.5) * 2.0,
                    getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.05);
        }
        level.sendParticles(ParticleTypes.BUBBLE, getX(), y, getZ(), 30, 1.2, 0.6, 1.2, 0.1);
        ring(level, 0.05);
        level.playSound(null, getX(), y, getZ(), ModSounds.STORMEEL_PULSE.get(), getSoundSource(), 1.5F, 1.0F);
        if (spared > 0) level.playSound(null, getX(), y, getZ(), ModSounds.STORMEEL_FIZZLE.get(), getSoundSource(), 1.0F, 1.0F);
        return hit;
    }

    /** Sparks round it at the discharge's reach, at its own height, flying at {@code speed}. */
    private void ring(ServerLevel level, double speed) {
        double y = getY() + getBbHeight() * 0.5;
        for (int i = 0; i < RING_POINTS; i++) {
            double a = i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(a) * PULSE_RADIUS, y, getZ() + Math.sin(a) * PULSE_RADIUS,
                    1, 0.0, 0.05, 0.0, speed);
        }
    }

    /** Ticks until it may discharge again. */
    public int untilPulse() {
        return untilPulse;
    }

    /** Sets the ticks until it may discharge again. Public as a test hook. */
    public void setUntilPulse(int ticks) {
        untilPulse = Math.max(0, ticks);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Stormeel) && super.canAttack(target);
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() && isInWater() ? ModSounds.STORMEEL_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.STORMEEL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.STORMEEL_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.2F;
    }
}
