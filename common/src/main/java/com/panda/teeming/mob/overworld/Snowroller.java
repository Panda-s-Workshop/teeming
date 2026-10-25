package com.panda.teeming.mob.overworld;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Snowroller: a snow beast of the high slopes. With a target 3 to 16 blocks away in sight it tucks into a ball for
 * 16 ticks (tell: head and legs drawn in, snow puffing off it), then rolls in a straight line at where the target
 * stood when the tuck ended. It starts at 0.25 blocks a tick and gains 0.02 every tick it rolls downhill, up to 0.6;
 * on the flat it keeps its speed, uphill it loses 0.02 a tick and uncurls once it is slower than 0.1. As it rolls it
 * gathers snow and grows, from scale 1 to 1.5 by the time it has rolled 12 blocks. A hit does its attack times
 * (1 + blocks rolled / 8), at most x2.5, and ends the roll. Counter: step out of its line, or get a wall between you.
 * A Snowroller that rolls into a wall lies stunned on its back for 3 s and takes x1.5. Any end of a roll knocks the
 * snow off it, back to its own size. It never places or breaks blocks.
 */
public class Snowroller extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL, ROLL, RECOVER, STUNNED }

    public static final int TELL_TICKS = 16;
    /** Speeds in blocks a tick: where a roll starts, what each tick downhill adds (and uphill takes), the cap. */
    public static final double ROLL_START_SPEED = 0.25;
    public static final double ROLL_ACCEL = 0.02;
    public static final double ROLL_MAX_SPEED = 0.6;
    /** Rolled uphill to below this speed it uncurls. */
    public static final double ROLL_MIN_SPEED = 0.1;
    /** The longest a roll lasts: 20 blocks on the flat at its starting speed. */
    public static final int ROLL_TICKS = 80;
    /** A hit does attack x (1 + blocks rolled / ROLL_BLOCKS_PER_STEP), at most x ROLL_MAX_HIT. */
    public static final double ROLL_BLOCKS_PER_STEP = 8.0;
    public static final float ROLL_MAX_HIT = 2.5F;
    /** Its scale grows with the hit: 1 at the start, this much once the hit is at its cap (12 blocks rolled). */
    public static final double ROLL_MAX_SCALE = 1.5;
    /** It rolls at targets this far away across the ground, in blocks. */
    public static final double MIN_RANGE = 3.0;
    public static final double MAX_RANGE = 16.0;
    public static final double KNOCKBACK = 1.0;
    public static final int STUN_TICKS = 60;
    public static final float STUN_DAMAGE = 1.5F;
    /** Uncurling after a roll that hit or ran out. */
    public static final int RECOVER_TICKS = 20;
    /** Ticks from the end of one roll (uncurled or stunned) until it may tuck again. */
    public static final int COOLDOWN = 80;

    /** The ground ahead counts as lower or higher once it is this far from its feet, in blocks. */
    private static final double SLOPE = 0.1;
    private static final Identifier GROWTH = TeemingMod.id("snowroller_growth");
    private static final DustParticleOptions STAR = new DustParticleOptions(0xFFD36B, 1.1F);
    private static final int STARS = 4;
    private static final BlockParticleOption SNOW = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState());
    private static final Telegraph TUCK = Telegraph.particles(SNOW, 14, 0.5, 0.4).withSpeed(0.08)
            .withSound(ModSounds.SNOWROLLER_TUCK.get(), 1.0F, 1.0F);
    private static final Telegraph PUFF = Telegraph.particles(ParticleTypes.SNOWFLAKE, 6, 0.45, 0.6).withSpeed(0.02);

    private int cooldown;
    private Vec3 rollDir = Vec3.ZERO;
    private double rollSpeed;
    private double rolled;
    private @Nullable Vec3 lastPos;
    /** Client and server: whether the walk animation is counting the roll (it restarts from 0 when a roll starts). */
    private boolean animRolling;

    public Snowroller(EntityType<? extends Snowroller> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("snowroller", 0.23).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
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
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.STUNNED);
    }

    // ---- the roll ------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        if (core().in(Step.ROLL)) {
            rollTick(level);
            return;
        }
        if (growth() > 1.0) shed(level);                  // whatever ended the roll, the snow comes off
        if (core().in(Step.TELL)) {
            getNavigation().stop();
            LivingEntity aim = core().target();
            if (aim != null) face(aim.position().subtract(position()));
            PUFF.trail(this, 3);
        } else if (core().in(Step.STUNNED)) {
            getNavigation().stop();
            circleStars(level);
        } else if (core().in(Step.RECOVER)) {
            getNavigation().stop();
        } else if (canRoll(target)) {
            if (startPhase(Step.TELL, TELL_TICKS, target)) TUCK.play(this);
        }
    }

    private boolean canRoll(@Nullable LivingEntity target) {
        if (!valid(target) || cooldown > 0 || !onGround() || isInWater()) return false;
        double d = position().subtract(target.position()).horizontalDistance();
        return d >= MIN_RANGE && d <= MAX_RANGE && getSensing().hasLineOfSight(target);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL) {
            LivingEntity target = core().target();
            Vec3 to = valid(target) ? target.position().subtract(position()).multiply(1, 0, 1) : Vec3.ZERO;
            if (to.lengthSqr() < 1.0E-4 || !roll(to, ROLL_START_SPEED)) rest();
        } else if (ended == Step.ROLL) {
            uncurl();
        } else {
            rest();
        }
    }

    /**
     * Starts a roll along {@code dir} (flattened) at {@code speed} blocks a tick, from no blocks rolled. The tuck calls
     * it with the starting speed; public as a test hook. False if the roll step was cancelled.
     */
    public boolean roll(Vec3 dir, double speed) {
        Vec3 flat = dir.multiply(1, 0, 1);
        if (flat.lengthSqr() < 1.0E-6 || !startPhase(Step.ROLL, ROLL_TICKS, core().target() != null ? core().target() : getTarget())) return false;
        rollDir = flat.normalize();
        rollSpeed = speed;
        rolled = 0.0;
        lastPos = position();
        face(rollDir);
        getNavigation().stop();
        playSound(ModSounds.SNOWROLLER_ROLL.get(), 1.0F, 1.0F);
        return true;
    }

    private void rollTick(ServerLevel level) {
        getNavigation().stop();
        face(rollDir);
        Vec3 now = position();
        if (lastPos != null) rolled += now.subtract(lastPos).horizontalDistance();
        lastPos = now;
        grow(growth(rolled));

        LivingEntity victim = firstInPath(level);
        if (victim != null) {
            slam(level, victim);
            return;
        }
        if (horizontalCollision && core().elapsed() > 1) {
            stun(level);
            return;
        }
        if (isInWater() || isInLava()) {
            uncurl();
            return;
        }
        // the slope: falling or lower ground ahead is downhill, ground ahead it can step up onto is uphill, the rest
        // (level ground, or a wall it can't climb and will hit) is flat
        double feet = getY();
        double reach = getBbWidth() * 0.5 + 0.3;
        double ahead = floorAt(level, getX() + rollDir.x * reach, getZ() + rollDir.z * reach);
        boolean falling = !onGround() && getDeltaMovement().y < -0.01;
        if (falling || ahead < feet - SLOPE) rollSpeed = Math.min(ROLL_MAX_SPEED, rollSpeed + ROLL_ACCEL);
        else if (ahead > feet + SLOPE && ahead <= feet + maxUpStep() + 1.0E-3) rollSpeed -= ROLL_ACCEL;
        if (rollSpeed < ROLL_MIN_SPEED - 1.0E-9) {
            uncurl();
            return;
        }
        setDeltaMovement(rollDir.x * rollSpeed, getDeltaMovement().y, rollDir.z * rollSpeed);
        if (tickCount % 2 == 0) {
            level.sendParticles(SNOW, getX(), getY() + 0.1, getZ(), 3, getBbWidth() * 0.3, 0.05, getBbWidth() * 0.3, 0.05);
        }
        if (core().elapsed() % 10 == 9) playSound(ModSounds.SNOWROLLER_ROLL.get(), 0.6F, 0.9F + (float) rollSpeed * 0.4F);
    }

    /** The top of the ground in the column at (x, z), searched from step height above its feet down 4 blocks. */
    private double floorAt(ServerLevel level, double x, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(getY() + 0.55), Mth.floor(z));
        for (int i = 0; i < 5; i++) {
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) return pos.getY() + shape.max(Direction.Axis.Y);
            pos.move(Direction.DOWN);
        }
        return getY() - 5.0;
    }

    private @Nullable LivingEntity firstInPath(ServerLevel level) {
        AABB reach = getBoundingBox().inflate(0.2).expandTowards(rollDir.x * rollSpeed, 0, rollDir.z * rollSpeed);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach, this::canHit)) return e;
        return null;
    }

    /** The roll reaches someone: attack x (1 + blocks rolled / 8), at most x2.5, and a shove along the roll. */
    private void slam(ServerLevel level, LivingEntity victim) {
        if (MechanicEvent.post(this, "roll_hit", victim)) {
            DamageSource source = damageSources().mobAttack(this);
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * hitMultiplier();
            victim.hurtServer(level, source, damage);
            victim.knockback(KNOCKBACK, -rollDir.x, -rollDir.z, source, damage);
            Push.sync(victim);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.SNOWROLLER_SLAM.get(), getSoundSource(), 1.0F, 1.0F);
        }
        setDeltaMovement(-rollDir.x * 0.15, 0.2, -rollDir.z * 0.15);
        uncurl();
    }

    /** The counter landed: it rolled into a wall. Public as a test hook. */
    public void stun(ServerLevel level) {
        shed(level);
        if (!startPhase(Step.STUNNED, STUN_TICKS, null)) {
            rest();
            return;
        }
        setDeltaMovement(-rollDir.x * 0.25, 0.25, -rollDir.z * 0.25);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.SNOWROLLER_STUNNED.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(SNOW, getX() + rollDir.x * 0.6, getY() + 0.5, getZ() + rollDir.z * 0.6, 30, 0.35, 0.35, 0.35, 0.15);
        level.sendParticles(ParticleTypes.CRIT, getX() + rollDir.x * 0.6, getY() + 0.6, getZ() + rollDir.z * 0.6, 10, 0.3, 0.3, 0.3, 0.2);
    }

    /** A roll that hit or ran out: it uncurls, the snow falls off. */
    private void uncurl() {
        if (level() instanceof ServerLevel level) shed(level);
        if (startPhase(Step.RECOVER, RECOVER_TICKS, null)) playSound(ModSounds.SNOWROLLER_UNROLL.get(), 1.0F, 1.0F);
        else rest();
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
    }

    /** Gold stars circling over it while it lies stunned, one lap a second. */
    private void circleStars(ServerLevel level) {
        if (tickCount % 2 != 0) return;
        Vec3 top = position().add(0, getBbHeight() + 0.25, 0);
        double turn = core().elapsed() * (Math.PI * 2 / 20);
        for (int i = 0; i < STARS; i++) {
            double a = turn + i * (Math.PI * 2 / STARS);
            level.sendParticles(STAR, top.x + Math.cos(a) * 0.55, top.y, top.z + Math.sin(a) * 0.55, 1, 0, 0, 0, 0);
        }
    }

    // ---- growing with the roll -------------------------------------------------------------------------------------

    /** The hit multiplier for the blocks rolled so far: 1 + blocks / 8, at most 2.5. */
    public float hitMultiplier() {
        return (float) Math.min(ROLL_MAX_HIT, 1.0 + rolled / ROLL_BLOCKS_PER_STEP);
    }

    /** Scale for {@code blocks} rolled: from 1 to 1.5 as the hit grows to its cap. */
    private static double growth(double blocks) {
        double hit = Math.min(ROLL_MAX_HIT, 1.0 + blocks / ROLL_BLOCKS_PER_STEP);
        return 1.0 + (ROLL_MAX_SCALE - 1.0) * (hit - 1.0) / (ROLL_MAX_HIT - 1.0);
    }

    /** Its current growth factor from the roll (1 when it carries no snow), on top of the look's own scale. */
    public double growth() {
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        AttributeModifier mod = scale == null ? null : scale.getModifier(GROWTH);
        return mod == null ? 1.0 : 1.0 + mod.amount();
    }

    /** Sets the growth factor (a transient scale modifier, so hitbox and model grow together on every client). */
    private void grow(double factor) {
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale == null) return;
        double amount = Math.round((factor - 1.0) * 64.0) / 64.0;   // 1/64 steps: a resync only when it visibly grows
        AttributeModifier mod = scale.getModifier(GROWTH);
        if (mod != null && mod.amount() == amount) return;
        if (amount <= 0.0) scale.removeModifier(GROWTH);
        else scale.addOrUpdateTransientModifier(new AttributeModifier(GROWTH, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** The snow comes off: back to its own size, in a burst of snow as big as the ball was. */
    private void shed(ServerLevel level) {
        double was = growth();
        grow(1.0);
        if (was > 1.0) {
            level.sendParticles(SNOW, getX(), getY() + getBbHeight() * 0.5, getZ(), (int) (10 + 40 * (was - 1.0)), 0.5, 0.4, 0.5, 0.12);
        }
    }

    /**
     * While it rolls the walk animation counts the distance rolled, uncapped and from 0 at the start of the roll, so
     * the model spins the ball by exactly the ground it covers (vanilla caps the walk speed for legs).
     */
    @Override
    protected void updateWalkAnimation(float distance) {
        boolean rolling = core().in(Step.ROLL);
        if (rolling != animRolling) {
            walkAnimation.stop();
            animRolling = rolling;
        }
        if (rolling) walkAnimation.update(distance, 1.0F, 1.0F);
        else super.updateWalkAnimation(distance);
    }

    // ---- small rules -------------------------------------------------------------------------------------------------

    private boolean canHit(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Snowroller) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private static boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return super.hurtServer(level, source, core().in(Step.STUNNED) ? amount * STUN_DAMAGE : amount);
    }

    /** A rolling ball tumbles down slopes unhurt. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return !core().in(Step.ROLL) && super.causeFallDamage(fallDistance, damageMultiplier, source);
    }

    // ---- test hooks ---------------------------------------------------------------------------------------------------

    /** Ticks until it may tuck again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it tuck again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    /** Its roll speed in blocks a tick (the last roll's, once it ends). Public as a test hook. */
    public double rollSpeed() {
        return rollSpeed;
    }

    /** Blocks rolled so far in this roll (or the last). Public as a test hook. */
    public double rolled() {
        return rolled;
    }

    /** Sets the blocks rolled so far, as if it had rolled them. Public as a test hook. */
    public void setRolled(double blocks) {
        rolled = blocks;
        grow(growth(rolled));
    }

    /** The direction of its current or last roll. Public as a test hook. */
    public Vec3 rollDir() {
        return rollDir;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SNOWROLLER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SNOWROLLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SNOWROLLER_DEATH.get();
    }
}
