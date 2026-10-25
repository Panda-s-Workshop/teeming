package com.panda.teeming.mob.overworld;

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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Skreel: a mountain raptor. With prey in sight and open sky above it, it climbs and circles 6 blocks out, 12 to 18
 * blocks above its target. Once it is 10 to 20 blocks above it, it stops in the air and screeches for 1 s (tell): it
 * tips nose-down, its wings half fold, and a dotted line of wind points from it to the target. Then it dives in a
 * straight line at 1 block a tick at the spot the target stood when the screech ended. A dive that lands hits for 1.5x
 * its attack and it climbs back up; a dive into the ground also pulls up. Counter: block the dive with a raised shield.
 * It crashes, lies stunned on the ground for 3 s and takes x1.5 there. After any dive it circles at least 4 s before
 * it screeches again. Under a roof, with no room to dive, it fights with its talons.
 */
public class Skreel extends TeemingMonster {
    public enum Step implements Phase { IDLE, CIRCLE, TELL, DIVE, CLIMB, STUNNED }

    public static final int TELL_TICKS = 20;
    /** It screeches only from this band of heights above its target, in blocks. */
    public static final double MIN_HEIGHT = 10.0;
    public static final double MAX_HEIGHT = 20.0;
    /** Where it circles: a ring this far out, at a height picked from this band each time it starts circling. */
    public static final double CIRCLE_RADIUS = 6.0;
    public static final double CRUISE_MIN = 12.0;
    public static final double CRUISE_MAX = 18.0;
    /** Its fastest speeds while circling, across and up or down, in blocks a tick. */
    public static final double CIRCLE_SPEED = 0.4;
    public static final double CLIMB_SPEED = 0.3;
    /** It dives only at a target at most this far away across the ground. */
    public static final double DIVE_REACH = 12.0;
    public static final double DIVE_SPEED = 1.0;
    public static final int DIVE_TICKS = 40;             // 40 blocks at 1 b/t, more than any dive from 20 up needs
    public static final float DIVE_HIT = 1.5F;           // x attack damage: 7.5 for its attack of 5
    public static final int CLIMB_TICKS = 30;            // climbing back after a dive: 9 blocks at 0.3 b/t
    public static final int STUN_TICKS = 60;
    public static final float STUN_DAMAGE = 1.5F;
    /** Ticks from the end of one dive until it may screech again. */
    public static final int COOLDOWN = 80;

    private static final int GALE_COLOR = 0xE6F2F6;
    private static final DustParticleOptions GALE = new DustParticleOptions(GALE_COLOR, 1.5F);
    private static final DustParticleOptions STAR = new DustParticleOptions(0xFFD36B, 1.1F);
    private static final int STARS = 4;
    private static final double LINE_STEP = 0.75;
    private static final Telegraph SCREECH = Telegraph.particles(ParticleTypes.SMALL_GUST, 3, 0.5, 0.6)
            .withSound(ModSounds.SKREEL_SCREECH.get(), 2.0F, 1.0F);
    private static final Telegraph CRASH = Telegraph.particles(ParticleTypes.CRIT, 18, 0.5, 0.5).withSpeed(0.25)
            .withSound(ModSounds.SKREEL_STUNNED.get(), 1.0F, 1.0F);

    private int cooldown;
    private double cruise = CRUISE_MIN;
    private double orbit;
    private Vec3 diveDir = new Vec3(0, -1, 0);
    private Vec3 aim = Vec3.ZERO;
    private boolean blockedDive;

    public Skreel(EntityType<? extends Skreel> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("skreel", 0.3).add(Attributes.FLYING_SPEED, 0.6);
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
        // talons, only when it has no room to dive (its own phases drive it otherwise)
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
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** It flies, except while it lies stunned, when it falls like anything else. */
    @Override
    public void travel(Vec3 input) {
        if (core().in(Step.STUNNED)) super.travel(input);
        else travelFlying(input, getSpeed());
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.STUNNED);
    }

    // ---- the hunt ----------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(!core().in(Step.STUNNED));
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        if (core().in(Step.STUNNED)) {
            getNavigation().stop();
            circleStars(level);
        } else if (core().in(Step.DIVE)) {
            diveTick(level);
        } else if (core().in(Step.CLIMB)) {
            getNavigation().stop();
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * 0.9, CLIMB_SPEED, v.z * 0.9);
            face(new Vec3(v.x, 0, v.z), -15.0F);
        } else if (core().in(Step.TELL)) {
            tellTick(level, core().target());
        } else if (core().in(Step.CIRCLE)) {
            if (!valid(target) || !roomAbove(level, target)) {
                core().end();
                return;
            }
            circleTick(target);
            if (canScreech(target) && startPhase(Step.TELL, TELL_TICKS, target)) SCREECH.play(this);
        } else if (valid(target) && roomAbove(level, target)) {
            circle(target);
        }
    }

    /** Starts circling over {@code target}: a height picked from 12 to 18 above it, and its place on the ring. */
    private void circle(LivingEntity target) {
        if (!startPhase(Step.CIRCLE, 0, target)) return;
        getNavigation().stop();
        cruise = CRUISE_MIN + random.nextDouble() * (CRUISE_MAX - CRUISE_MIN);
        orbit = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
    }

    /** Steers toward a point moving round the ring over the target, climbing or sinking to its cruise height. */
    private void circleTick(LivingEntity target) {
        getNavigation().stop();
        orbit += CIRCLE_SPEED / CIRCLE_RADIUS;
        Vec3 want = target.position().add(Math.cos(orbit) * CIRCLE_RADIUS, cruise, Math.sin(orbit) * CIRCLE_RADIUS);
        Vec3 to = want.subtract(position());
        Vec3 across = new Vec3(to.x, 0, to.z);
        if (across.length() > CIRCLE_SPEED) across = across.normalize().scale(CIRCLE_SPEED);
        double up = Mth.clamp(to.y * 0.25, -CLIMB_SPEED, CLIMB_SPEED);
        Vec3 v = getDeltaMovement();
        setDeltaMovement(Mth.lerp(0.35, v.x, across.x), up, Mth.lerp(0.35, v.z, across.z));
        face(getDeltaMovement().multiply(1, 0, 1), 0.0F);
    }

    private boolean canScreech(LivingEntity target) {
        if (cooldown > 0) return false;
        double height = getY() - target.getY();
        double across = position().subtract(target.position()).horizontalDistance();
        return height >= MIN_HEIGHT && height <= MAX_HEIGHT && across <= DIVE_REACH && getSensing().hasLineOfSight(target);
    }

    /** The screech: it hangs in the air, nose toward the target, and draws its dive line at it every other tick. */
    private void tellTick(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().scale(0.5));
        if (target == null) return;
        Vec3 from = centre();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 d = to.subtract(from);
        face(d, (float) pitchOf(d));
        if (core().elapsed() == TELL_TICKS / 2) SCREECH.play(this);
        if (tickCount % 2 == 0) {
            double length = d.length();
            Vec3 step = d.normalize().scale(LINE_STEP);
            Vec3 p = from;
            for (double run = 0; run < length; run += LINE_STEP, p = p.add(step)) {
                level.sendParticles(GALE, false, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (!valid(target) || !(level() instanceof ServerLevel)) {
                core().end();
                return;
            }
            aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 d = aim.subtract(centre());
            if (d.lengthSqr() < 1.0E-4 || !startPhase(Step.DIVE, DIVE_TICKS, target)) {
                climb();
                return;
            }
            diveDir = d.normalize();
            blockedDive = false;
            face(diveDir, (float) pitchOf(diveDir));
            playSound(ModSounds.SKREEL_DIVE.get(), 2.0F, 1.0F);
        } else if (ended == Step.DIVE) {
            climb();
        } else if (ended == Step.STUNNED || ended == Step.CLIMB) {
            if (ended == Step.STUNNED) climb();
            else if (valid(getTarget()) && level() instanceof ServerLevel level && roomAbove(level, getTarget())) circle(getTarget());
            else core().end();
        } else {
            core().end();
        }
    }

    /** Climbs back up after a dive (landed, dodged or blocked); the wait for the next screech starts here. */
    private void climb() {
        cooldown = COOLDOWN;
        if (!startPhase(Step.CLIMB, CLIMB_TICKS, getTarget())) core().end();
    }

    private void diveTick(ServerLevel level) {
        getNavigation().stop();
        face(diveDir, (float) pitchOf(diveDir));
        Vec3 move = diveDir.scale(DIVE_SPEED);
        setDeltaMovement(move);
        level.sendParticles(ParticleTypes.CLOUD, false, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        level.sendParticles(GALE, false, true, getX() - move.x * 0.5, getY() + getBbHeight() * 0.5 - move.y * 0.5, getZ() - move.z * 0.5,
                2, 0.05, 0.05, 0.05, 0.0);
        AABB reach = getBoundingBox().inflate(0.3).expandTowards(move);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, reach, this::canStrike)) {
            strike(level, victim);
            return;
        }
        if (core().elapsed() > 1 && (horizontalCollision || verticalCollision || onGround())) climb();   // dodged: it pulls up
    }

    /** The dive reaches someone: a shield stuns it, anything else takes 1.5x its attack. */
    private void strike(ServerLevel level, LivingEntity victim) {
        if (!MechanicEvent.post(this, "dive_hit", victim)) {
            climb();
            return;
        }
        blockedDive = false;
        DamageSource source = damageSources().mobAttack(this);
        victim.hurtServer(level, source, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * DIVE_HIT);
        if (blockedDive) {
            stun(level);
            return;
        }
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.SKREEL_STRIKE.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        setDeltaMovement(diveDir.x * 0.6, CLIMB_SPEED, diveDir.z * 0.6);
        climb();
    }

    /** A shield took the dive: the hit is set aside here and it crashes instead of being knocked back. */
    @Override
    protected void blockedByItem(LivingEntity defender, DamageSource source, float blocked, boolean fullBlock) {
        if (core().in(Step.DIVE)) blockedDive = true;
        else super.blockedByItem(defender, source, blocked, fullBlock);
    }

    /** The counter landed: it crashes into the shield and drops to the ground stunned. Public as a test hook. */
    public void stun(ServerLevel level) {
        if (!startPhase(Step.STUNNED, STUN_TICKS, null)) {
            climb();
            return;
        }
        setNoGravity(false);
        setDeltaMovement(-diveDir.x * 0.3, 0.1, -diveDir.z * 0.3);
        setXRot(0.0F);
        CRASH.play(this);
        level.sendParticles(ParticleTypes.SMALL_GUST, getX(), getY() + 0.4, getZ(), 2, 0.3, 0.2, 0.3, 0.0);
    }

    /** Gold stars circling over its head while it is stunned, one lap a second. */
    private void circleStars(ServerLevel level) {
        if (tickCount % 2 != 0) return;
        Vec3 head = position().add(getLookAngle().multiply(1, 0, 1).scale(getBbWidth() * 0.45)).add(0, getBbHeight() + 0.25, 0);
        double turn = core().elapsed() * (Math.PI * 2 / 20);
        for (int i = 0; i < STARS; i++) {
            double a = turn + i * (Math.PI * 2 / STARS);
            level.sendParticles(STAR, head.x + Math.cos(a) * 0.55, head.y, head.z + Math.sin(a) * 0.55, 1, 0, 0, 0, 0);
        }
    }

    private boolean canStrike(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Skreel) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private static boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Open sky over the target: no block in the 12 above it, so a dive from 10 up has room. */
    private static boolean roomAbove(ServerLevel level, LivingEntity target) {
        BlockPos.MutableBlockPos pos = target.blockPosition().mutable();
        for (int i = 1; i <= MIN_HEIGHT + 2; i++) {
            pos.move(0, 1, 0);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        }
        return true;
    }

    private Vec3 centre() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }

    /** Degrees below level of a direction, the way entity pitch counts (down is positive). */
    private static double pitchOf(Vec3 d) {
        return -Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG;
    }

    private void face(Vec3 dir, float pitch) {
        setXRot(pitch);
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

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- test hooks ---------------------------------------------------------------------------------------------------

    /** Ticks until it may screech again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it screech again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    /** The point its current or last dive was aimed at. Public as a test hook. */
    public Vec3 aim() {
        return aim;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SKREEL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SKREEL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SKREEL_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + random.nextFloat() * 0.1F;
    }
}
