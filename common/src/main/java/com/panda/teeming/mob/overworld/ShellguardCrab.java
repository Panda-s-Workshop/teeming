package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAnimal;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shellguard Crab: a shore crab, passive until struck. Its shell and claws turn every blow that comes from its front
 * 120 degrees, and a blocked attacker in reach is pinched back. Tell: both claws go up and open for 8 ticks, then the
 * pinch (1x Atk). Counter: a shovel hit, or any shove of knockback 0.5 or more (a sprinting hit), flips it onto its
 * back for 3 s: it can't act and takes x2.
 *
 * <p>It walks sideways: wandering, it keeps its claws to one side of its path; with a target, it turns its front to
 * the target and sidles.
 */
public class ShellguardCrab extends TeemingAnimal {
    public enum Step implements Phase { IDLE, TELL, PINCH, FLIPPED }

    public static final int TELL_TICKS = 8;
    /** The pinch lands as the claws snap shut; the pose holds this long. */
    public static final int PINCH_TICKS = 6;
    /** Ticks after a pinch before it raises its claws again. */
    public static final int PINCH_COOLDOWN = 20;
    /** The guarded arc, in degrees, centred on the way it faces. */
    public static final float GUARD_ARC = 120.0F;
    /** A shove at least this strong flips it (a sprinting hit carries 0.5; an ordinary hit 0.4). */
    public static final double FLIP_KNOCKBACK = 0.5;
    public static final int FLIP_TICKS = 60;
    /** Damage multiplier while it lies on its back. */
    public static final float FLIPPED_DAMAGE = 2.0F;
    /** How fast it turns its front toward a target, degrees a tick: a sprinting circle at close range can just beat it. */
    public static final float TURN_TO_TARGET = 10.0F;
    private static final float WANDER_TURN = 20.0F;
    /** Synced flag: the legs scuttle toward its left (set) or its right (clear). */
    public static final int FLAG_SCUTTLE_LEFT = 0;

    private int cooldown;
    /** The way it faced when it went over; it lies still that way. NaN while it stands. */
    private float flipYaw = Float.NaN;

    public ShellguardCrab(EntityType<? extends ShellguardCrab> type, Level level) {
        super(type, level, Step.values());
        this.moveControl = new SidestepControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.animal("shellguard_crab", 0.24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** In reach, it raises its claws instead of hitting at once: the pinch follows the tell. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
                if (cooldown == 0 && canPerformAttack(target) && startTell(target)) resetAttackCooldown();
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F) {
            @Override
            public boolean canUse() {
                return !flipped() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !flipped() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !flipped() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !flipped() && super.canContinueToUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ---- guard and flip --------------------------------------------------------------------------------------------

    public boolean flipped() {
        return core().in(Step.FLIPPED);
    }

    @Override
    public boolean vulnerable() {
        return flipped();
    }

    /** True when a blow from {@code from} lands inside the guarded arc in front of it. */
    public boolean inGuardArc(Vec3 from) {
        double dx = from.x - getX();
        double dz = from.z - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return false;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.degreesDifferenceAbs(toward, yBodyRot) <= GUARD_ARC / 2.0F;
    }

    /**
     * Only blows that come from somewhere (a hit, a projectile, a blast) can be turned. A shovel hit flips it from any
     * side. A guarded melee blow does no damage but still "lands", so its shove goes through ({@link #knockback}: a
     * hard one flips it); a guarded projectile glances off.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableTo(level, source) || isDeadOrDying()) return super.hurtServer(level, source, amount);
        boolean wasFlipped = flipped();
        boolean melee = source.isDirect() && source.getEntity() instanceof LivingEntity;
        ItemStack weapon = source.getWeaponItem();
        if (melee && weapon != null && weapon.is(ItemTags.SHOVELS)) flip();
        Vec3 from = source.getSourcePosition();
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        if (!wasFlipped && from != null && inGuardArc(from) && MechanicEvent.post(this, "guard", attacker)) {
            guarded(level, attacker);
            return melee;
        }
        return super.hurtServer(level, source, wasFlipped ? amount * FLIPPED_DAMAGE : amount);
    }

    /** The blow was turned: a clink, sparks off the claws, and a pinch back at an attacker in reach. */
    private void guarded(ServerLevel level, @Nullable LivingEntity attacker) {
        Vec3 front = position().add(Vec3.directionFromRotation(0.0F, yBodyRot).scale(getBbWidth() * 0.6)).add(0, getBbHeight() * 0.55, 0);
        level.sendParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 8, 0.2, 0.1, 0.2, 0.25);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.SHELLGUARD_CRAB_GUARD.get(), getSoundSource(), 1.0F, 1.0F);
        if (attacker == null || !canPinch(attacker)) return;
        setLastHurtByMob(attacker);
        setTarget(attacker);
        if (isWithinMeleeAttackRange(attacker)) startTell(attacker);
    }

    /** Every shove goes through here; one of {@link #FLIP_KNOCKBACK} or more turns it over. */
    @Override
    public void knockback(double strength, double x, double z, DamageSource source, float damage, boolean flag) {
        if (strength >= FLIP_KNOCKBACK) flip();
        super.knockback(strength, x, z, source, damage, flag);
    }

    /** The counter landed: onto its back for {@link #FLIP_TICKS}, belly up. Public as a test hook. */
    public void flip() {
        if (flipped() || !(level() instanceof ServerLevel level)) return;
        if (!startPhase(Step.FLIPPED, FLIP_TICKS, null)) return;
        flipYaw = yBodyRot;
        getNavigation().stop();
        setXxa(0.0F);
        setZza(0.0F);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.SHELLGUARD_CRAB_FLIPPED.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.1, getZ(), 16, 0.35, 0.05, 0.35, 0.1);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight(), getZ(), 10, 0.3, 0.1, 0.3, 0.3);
    }

    private BlockState groundState() {
        return level().getBlockState(blockPosition().below());
    }

    // ---- the pinch -------------------------------------------------------------------------------------------------

    private boolean canPinch(LivingEntity target) {
        return target != this && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Raises both claws at {@code target} for {@link #TELL_TICKS}; the pinch follows. Public as a test hook. */
    public boolean startTell(LivingEntity target) {
        if (!core().idle() || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        face(target, 360.0F);
        getNavigation().stop();
        Telegraph.particles(ParticleTypes.SPLASH, 10, 0.35, 0.8).withSound(ModSounds.SHELLGUARD_CRAB_TELL.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (!startPhase(Step.PINCH, PINCH_TICKS, target)) {
                rest();
                return;
            }
            if (level() instanceof ServerLevel level) {
                level.playSound(null, getX(), getY(), getZ(), ModSounds.SHELLGUARD_CRAB_PINCH.get(), getSoundSource(), 1.0F, 1.0F);
                if (target != null && canPinch(target) && isWithinMeleeAttackRange(target)) doHurtTarget(level, target);
            }
        } else {
            rest();
        }
    }

    private void rest() {
        flipYaw = Float.NaN;
        core().end();
        cooldown = PINCH_COOLDOWN;
    }

    /** Ticks until it may raise its claws again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (flipped()) {
            getNavigation().stop();
            if (Float.isNaN(flipYaw)) flipYaw = yBodyRot;
            setYRot(flipYaw);
            yBodyRot = flipYaw;
            yHeadRot = flipYaw;
            if (tickCount % 10 == 0) level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 3, 0.3, 0.05, 0.3, 0.0);
            return;
        }
        if (core().in(Step.TELL)) {
            getNavigation().stop();
            LivingEntity target = core().target();
            if (target != null) face(target, TURN_TO_TARGET);
            Telegraph.particles(ParticleTypes.SPLASH, 10, 0.35, 0.8).trail(this, 2);
        } else if (core().in(Step.PINCH)) {
            getNavigation().stop();
        } else {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive()) face(target, TURN_TO_TARGET);
        }
    }

    /** Turns its front toward {@code target}, at most {@code maxTurn} degrees. */
    private void face(Entity target, float maxTurn) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
        setYRot(yaw);
        yBodyRot = yaw;
    }

    /**
     * Walks sideways. Vanilla turns a walker to face its path; this one keeps its front square to its path while it
     * wanders (claws to one side), or wherever {@link #customServerAiStep} turned it while it has a target, and moves
     * along the path with a sideways step.
     */
    private static final class SidestepControl extends MoveControl<ShellguardCrab> {
        private float travel;

        SidestepControl(ShellguardCrab crab) {
            super(crab);
        }

        @Override
        public void tick() {
            Operation was = operation;
            float facing = mob.getYRot();
            if (!mob.core().idle()) {
                operation = Operation.WAIT;
                mob.setZza(0.0F);
                mob.setXxa(0.0F);
                return;
            }
            if (was == Operation.MOVE_TO) {
                double dx = wantedX - mob.getX();
                double dz = wantedZ - mob.getZ();
                if (dx * dx + dz * dz > 1.0E-6) travel = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            }
            super.tick();
            if (was != Operation.MOVE_TO && was != Operation.JUMPING) {
                if (was == Operation.WAIT) mob.setXxa(0.0F);
                return;
            }
            if (mob.getTarget() == null) {
                // square to the path: whichever side keeps the turn smallest
                float left = travel - 90.0F;
                float right = travel + 90.0F;
                float side = Mth.degreesDifferenceAbs(facing, left) < Mth.degreesDifferenceAbs(facing, right) ? left : right;
                facing = Mth.approachDegrees(facing, side, WANDER_TURN);
            }
            mob.setYRot(facing);
            float speed = mob.getSpeed();
            float off = (travel - facing) * Mth.DEG_TO_RAD;
            mob.setZza(speed * Mth.cos(off));
            mob.setXxa(-speed * Mth.sin(off));
            if (Math.abs(mob.xxa) > 0.01F) mob.core().setFlag(FLAG_SCUTTLE_LEFT, mob.xxa > 0.0F);
        }
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SHELLGUARD_CRAB_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SHELLGUARD_CRAB_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SHELLGUARD_CRAB_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.SHELLGUARD_CRAB_STEP.get(), 0.3F, 1.0F);
    }
}
