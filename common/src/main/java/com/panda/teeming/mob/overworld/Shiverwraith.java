package com.panda.teeming.mob.overworld;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shiverwraith: a frost spirit of the snowfields at night that drains warmth through a beam. With a target within 12
 * blocks in sight it stops, raises its arms toward it and a thin frost beam flickers between them for 12 ticks (tell).
 * Then the tether locks: every tick the target gains 3 freeze ticks, the same freezing powder snow gives (fully frozen
 * in about 2.4 s, then vanilla freeze damage and the powder-snow slowdown), and the wraith heals 1 health a second
 * while it holds. Counter: heat or cover. Fire, a lit campfire or lava within 3 blocks of the target (or the target
 * burning), losing line of sight, or the target moving out past 12 blocks snaps the tether with a fizzle, and the
 * wraith recoils. It waits 3 s after a snap before it reaches out again; meanwhile it claws (its attack). A Hearth
 * Charm in the target's off-hand halves both the freeze and the healing.
 */
public class Shiverwraith extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL, TETHER, SNAP }

    public static final int TELL_TICKS = 12;
    /** The tether reaches this far, in blocks from the wraith to its target; past it the beam snaps. */
    public static final double TETHER_RANGE = 12.0;
    /**
     * Freeze ticks the target gains each tick of the tether: fully frozen (140) in 47 ticks, about 2.4 s. A Hearth
     * Charm halves it (1.5 a tick, alternately 1 and 2).
     */
    public static final int FREEZE_PER_TICK = 3;
    /** Freeze ticks vanilla takes back every tick from anything outside powder snow; the tether pays it back first. */
    public static final int THAW_PER_TICK = 2;
    /** Health the wraith gains each second the tether holds; a Hearth Charm on the target halves it. */
    public static final float HEAL = 1.0F;
    public static final int HEAL_INTERVAL = 20;
    /** Fire, a lit campfire or lava this many blocks or fewer from the target (on each axis) breaks the tether. */
    public static final int HEAT_RANGE = 3;
    /** The recoil after a snap. */
    public static final int SNAP_TICKS = 20;
    /** Ticks from a snap until it may reach out again. */
    public static final int COOLDOWN = 60;

    private static final int BEAM_COLOR = 0xBDEBFF;
    private static final DustParticleOptions BEAM = new DustParticleOptions(BEAM_COLOR, 1.0F);
    private static final DustParticleOptions THIN = new DustParticleOptions(0xE6F7FF, 0.6F);
    /** The beam's dots are this far apart, and it stops this far short of the target, out of a player's face. */
    private static final double BEAM_STEP = 0.3;
    private static final double BEAM_SHORT = 0.6;
    private static final Telegraph REACH = Telegraph.particles(ParticleTypes.SNOWFLAKE, 6, 0.45, 0.65).withSpeed(0.01)
            .withSound(ModSounds.SHIVERWRAITH_BEAM.get(), 1.0F, 1.0F);
    private static final Telegraph LOCK = Telegraph.particles(ParticleTypes.SNOWFLAKE, 14, 0.5, 0.65).withSpeed(0.03)
            .withSound(ModSounds.SHIVERWRAITH_TETHER.get(), 1.0F, 1.0F);

    private int cooldown;

    public Shiverwraith(EntityType<? extends Shiverwraith> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("shiverwraith", 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // its claws, while it can't hold a tether (cooling down, or its target stands by a fire)
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
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

    // ---- the beam ----------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (core().in(Step.TELL)) {
            reachTick(level, core().target());
        } else if (core().in(Step.TETHER)) {
            tetherTick(level, core().target());
        } else if (core().in(Step.SNAP)) {
            holdStill();
        } else if (cooldown == 0 && canHold(level, getTarget())) {
            reach(getTarget());
        }
    }

    /** Starts the tell at {@code target}: arms up, a flickering beam. True if it started. Public as a test hook. */
    public boolean reach(@Nullable LivingEntity target) {
        if (target == null || !core().idle() || cooldown > 0 || level().isClientSide()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        REACH.play(this);
        return true;
    }

    /** The tell: it stops, faces the target and a thin beam flickers at it. Nothing happens to the target yet. */
    private void reachTick(ServerLevel level, @Nullable LivingEntity target) {
        holdStill();
        REACH.trail(this, 3);
        if (target == null) return;
        face(target);
        drawBeam(level, target, false);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
            return;
        }
        if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (!canHold(level, target)) snap(level, target);                      // it moved to heat or cover in time
            else if (startPhase(Step.TETHER, 0, target)) LOCK.play(this);
            else rest();
        } else {
            core().end();
        }
    }

    // ---- Tether: a beam from this mob to a target that holds while range, sight and the rules allow ------------------

    /** True when the tether can reach {@code target} and hold: alive, in range, in sight, no heat near it. */
    public boolean canHold(ServerLevel level, @Nullable LivingEntity target) {
        return valid(target) && distanceTo(target) <= TETHER_RANGE && hasLineOfSight(target) && !warmed(level, target);
    }

    /**
     * One tick of the locked tether: it checks the hold, chills the target, heals itself once a second, draws the beam.
     * A Hearth Charm in the target's off-hand halves the chill and the healing.
     */
    private void tetherTick(ServerLevel level, @Nullable LivingEntity target) {
        holdStill();
        if (!canHold(level, target)) {
            snap(level, target);
            return;
        }
        face(target);
        int t = core().elapsed();
        if (MechanicEvent.post(this, "freeze", target)) chill(target, t);
        if (t > 0 && t % HEAL_INTERVAL == 0) {
            if (MechanicEvent.post(this, "heal", target)) {
                heal((float) (HEAL * Charms.frostTetherHeal(target)));
                level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() * 0.6, getZ(), 5, 0.3, 0.3, 0.3, 0.01);
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SHIVERWRAITH_DRAIN.get(), getSoundSource(), 0.7F, 1.0F);
        }
        drawBeam(level, target, true);
    }

    /**
     * Adds the tether's freeze ticks to the target on tick {@code t} of the tether, as powder snow would: up to the
     * vanilla full-freeze mark, plus the thaw vanilla takes off this tick, so the target stays fully frozen (and takes
     * freeze damage) while it holds. A Hearth Charm halves the build-up; a rate that isn't whole is paid out so the
     * total after t ticks is the rate times t, rounded down. Anything that can't freeze (leather armour, a stray) is
     * left alone.
     */
    private static void chill(LivingEntity target, int t) {
        if (!target.canFreeze()) return;
        double rate = FREEZE_PER_TICK * Charms.freezeBuildUp(target);
        int gain = Mth.floor((t + 1) * rate) - Mth.floor(t * rate);
        int cap = target.getTicksRequiredToFreeze() + THAW_PER_TICK;
        target.setTicksFrozen(Math.min(cap, target.getTicksFrozen() + gain + THAW_PER_TICK));
    }

    /** Heat close to the target: fire (soul fire too), a lit campfire or lava within 3 blocks on each axis, or the target burning. */
    public static boolean warmed(ServerLevel level, LivingEntity target) {
        if (target.isOnFire()) return true;
        BlockPos feet = target.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-HEAT_RANGE, -HEAT_RANGE, -HEAT_RANGE), feet.offset(HEAT_RANGE, HEAT_RANGE, HEAT_RANGE))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(state) || state.getFluidState().is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    /** The tether (or the beam of the tell) breaks: a fizzle at the target and at the wraith, and it recoils. */
    private void snap(ServerLevel level, @Nullable LivingEntity target) {
        cooldown = COOLDOWN;
        if (!startPhase(Step.SNAP, SNAP_TICKS, target)) {
            core().end();
            return;
        }
        if (target != null) {
            double y = target.getY() + target.getBbHeight() * 0.6;
            level.sendParticles(ParticleTypes.WHITE_SMOKE, target.getX(), y, target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), y, target.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
            level.playSound(null, target.getX(), y, target.getZ(), ModSounds.SHIVERWRAITH_SNAP.get(), getSoundSource(), 1.0F, 1.0F);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() * 0.65, getZ(), 12, 0.4, 0.4, 0.4, 0.06);
        level.sendParticles(ParticleTypes.WHITE_SMOKE, beamStart().x, beamStart().y, beamStart().z, 6, 0.2, 0.2, 0.2, 0.02);
        playSound(ModSounds.SHIVERWRAITH_SNAP.get(), 0.8F, 0.8F);
    }

    /**
     * Draws the beam from its hands to the target. In the tell: a thin, broken line that flickers tick to tick. Locked:
     * a solid line every other tick whose dots creep back toward the wraith, and frost motes drifting from the target
     * up the beam, the warmth it drains.
     */
    private void drawBeam(ServerLevel level, LivingEntity target, boolean locked) {
        Vec3 from = beamStart();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0);
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 0.5) return;
        Vec3 dir = d.scale(1.0 / length);
        double stop = length - BEAM_SHORT;
        if (!locked) {
            for (double run = 0.2; run < stop; run += BEAM_STEP * 1.5) {
                if (random.nextFloat() < 0.55) continue;                         // the flicker
                Vec3 p = from.add(dir.scale(run));
                level.sendParticles(THIN, false, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            return;
        }
        if (tickCount % 2 == 0) {
            double creep = BEAM_STEP - (tickCount / 2 % 3) * (BEAM_STEP / 3);      // the dots creep toward the wraith
            for (double run = creep; run < stop; run += BEAM_STEP) {
                Vec3 p = from.add(dir.scale(run));
                level.sendParticles(BEAM, false, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (tickCount % 3 == 0) {
            Vec3 p = from.add(dir.scale(stop * (0.3 + 0.7 * random.nextDouble())));
            // count 0: the offset is the particle's direction, the speed its pace
            level.sendParticles(ParticleTypes.END_ROD, false, true, p.x, p.y, p.z, 0, -dir.x, -dir.y, -dir.z, 0.08);
            level.sendParticles(ParticleTypes.SNOWFLAKE, false, true, to.x, to.y, to.z, 0, -dir.x, -dir.y, -dir.z, 0.15);
        }
    }

    /** Where the beam leaves it: between its raised hands, in front of its chest. */
    private Vec3 beamStart() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
        return position().add(forward.scale(0.75)).add(0, getBbHeight() * 0.7, 0);
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
    }

    private void face(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void rest() {
        cooldown = COOLDOWN;
        core().end();
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** It is frost itself: powder snow and its own beam never chill it. */
    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- test hooks ---------------------------------------------------------------------------------------------------

    /** Ticks until it may reach out again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it reach out again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SHIVERWRAITH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SHIVERWRAITH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SHIVERWRAITH_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.2F;
    }
}
