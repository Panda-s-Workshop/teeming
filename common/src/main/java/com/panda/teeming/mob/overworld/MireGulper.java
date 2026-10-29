package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TryFindLiquidGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mire Gulper: a wide-mouthed ambusher of the swamps that lies under shallow water. While it is under (idle or in its
 * tell, its eyes under the surface) it is invisible, no mob can target it and projectiles pass through it; a few
 * bubbles are the only sign. When its prey stands within 1.5 blocks of it, bubbles boil up over it for 1 s (tell); then
 * it lunges up and swallows its prey if it is still within 2.5 blocks. The victim is held in its mouth, rooted and
 * carried with it, and takes half its attack damage every 10 ticks, for at most 100 ticks. Counter: hit it 3 times
 * while it holds someone (the victim from inside or anyone from outside): it spits the victim out and is stunned for
 * 2 s, taking x1.5 damage. Out of water it can still ambush, frothing at the mouth instead.
 *
 * <p>Nothing is kept on the victim: the hold is the gulper putting its victim back in its mouth each tick, so a gulper
 * that dies, unloads, loses its AI or lets go leaves its victim free on the spot.
 */
public class MireGulper extends TeemingMonster {
    /** IDLE (lying in wait), bubbles rising (tell), holding a victim, stunned after spitting one out, settling down. */
    public enum Step implements Phase { IDLE, TELL, SWALLOW, STUNNED, RECOVER }

    public static final int TELL_TICKS = 20;              // bubbles boil up over it for 1 s
    public static final double TELL_RANGE = 1.5;          // it starts once its prey is this close across
    public static final double LUNGE_REACH = 2.5;         // prey still this close across when the bubbles stop is swallowed
    public static final int HOLD_TICKS = 100;             // it lets go after 5 s at the most
    public static final int BITE_EVERY = 10;              // a chew every half second while it holds someone
    public static final float BITE_HIT = 0.5F;            // x attack damage per chew
    public static final int HITS_TO_SPIT = 3;             // hits on it while it holds someone: it spits them out
    public static final int STUN_TICKS = 40;              // stunned for 2 s after spitting
    public static final float STUN_DAMAGE = 1.5F;         // stunned, it takes x1.5 from every blow
    public static final int RECOVER_TICKS = 20;
    public static final int COOLDOWN = 100;               // ticks from letting go (or missing) to the next ambush
    /** Prey taller or wider than this doesn't fit in its mouth; it bites instead. */
    public static final float MAX_PREY_HEIGHT = 2.0F, MAX_PREY_WIDTH = 1.0F;
    /** A victim found this far from its mouth (an ender pearl, a teleport) has got away. */
    public static final double ESCAPE_RANGE = 4.0;
    /** {@code core().flag} bit: lying under water (clients hide it). */
    public static final int SUBMERGED = 0;

    /** Where the victim's feet are held: ahead of its centre and a little up, inside the open mouth. */
    private static final double MOUTH_FORWARD = 0.45, MOUTH_UP = 0.2;
    private static final DustParticleOptions STAR = new DustParticleOptions(0xFFD36B, 1.1F);
    private static final int STARS = 4;

    private int cooldown;
    private int hits;
    private final Grab grab = new Grab(MAX_PREY_WIDTH, MAX_PREY_HEIGHT, ESCAPE_RANGE);

    public MireGulper(EntityType<? extends MireGulper> type, Level level) {
        super(type, level, Step.values());
        setPathfindingMalus(PathType.WATER, 0.0F);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("mire_gulper", 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** With an ambush ready it only closes in and lets the swallow do the work; otherwise it bites. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
                if (cooldown > 0 || !swallowable(target)) super.checkAndPerformAttack(target);
            }
        });
        goalSelector.addGoal(4, new TryFindLiquidGoal(this, FluidTags.WATER) {
            @Override
            public boolean canUse() {
                return core().idle() && getTarget() == null && super.canUse();
            }
        });
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8, 160) {
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

    // ---- lying in wait under water ---------------------------------------------------------------------------------

    /** True while it lies under water, idle or in its tell (both sides). */
    public boolean submerged() {
        MobCore core = core();
        return core != null && core.flag(SUBMERGED);   // null while the Entity constructor runs
    }

    /** Under water it is out of sight. */
    @Override
    public boolean hiddenFromSight() {
        return submerged();
    }

    /** Under water it can't be seen: the bubbles are the only sign. */
    @Override
    public boolean isInvisible() {
        return super.isInvisible() || submerged();
    }

    /** No mob can target it while it is under. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return !submerged() && super.canBeSeenAsEnemy();
    }

    /** Projectiles pass through it while it is under. */
    @Override
    public boolean canBeHitByProjectile() {
        return !submerged() && super.canBeHitByProjectile();
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** It spawns in its pool: only blocks and mobs count as obstruction (the default refuses any liquid in the hitbox). */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** It crawls along the bottom as fast as on land, sinks, and climbs out over a bank it walks into. */
    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        moveRelative(getSpeed(), input);
        move(MoverType.SELF, getDeltaMovement());
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x * 0.546, v.y * 0.8 - 0.02, v.z * 0.546);
        if (horizontalCollision && isFree(v.x, v.y + 0.6 - getY() + oldY, v.z)) setDeltaMovement(v.x, 0.3, v.z);
    }

    /** Water is where it wants to wait; on land it walks as a monster does. */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getFluidState(pos).is(FluidTags.WATER) ? 10.0F : super.getWalkTargetValue(pos, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) return;
        boolean under = isAlive() && isUnderWater() && (core().idle() || core().in(Step.TELL));
        core().setFlag(SUBMERGED, under);
        // after its own move this tick, so a victim is where its mouth ended up (knocked back, falling, walking)
        LivingEntity v = grab.victim();
        if (v != null && core().in(Step.SWALLOW) && !isNoAi() && grab.holding(this, mouth())) holdInMouth(v, false);
    }

    // ---- the ambush ------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().in(Step.SWALLOW)) {
            holdTick(level);
        } else if (core().in(Step.STUNNED)) {
            holdStill();
            circleStars(level);
        } else if (core().in(Step.RECOVER)) {
            holdStill();
        } else if (core().idle()) {
            LivingEntity target = getTarget();
            if (canAmbush(target)) ambush(target);
            else if (submerged() && random.nextInt(60) == 0) bubbles(level, 2);   // now and then a bubble gives it away
        }
    }

    /** Prey it can swallow: alive, small enough to fit in its mouth, and not a player in creative or spectator. */
    public boolean swallowable(@Nullable LivingEntity e) {
        return !(e instanceof MireGulper) && grab.fits(this, e);
    }

    private boolean canAmbush(@Nullable LivingEntity target) {
        return cooldown == 0 && swallowable(target) && across(target) <= TELL_RANGE && Math.abs(target.getY() - getY()) <= 1.5
                && getSensing().hasLineOfSight(target);
    }

    private double across(Entity e) {
        return e.position().subtract(position()).horizontalDistance();
    }

    /** Starts the tell under {@code target}: bubbles for 20 ticks, then the lunge. True if it started. Public as a test hook. */
    public boolean ambush(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        holdStill();
        if (target != null) face(target.position().subtract(position()));
        if (level() instanceof ServerLevel level) {
            bubbles(level, 10);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.MIRE_GULPER_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        }
        return true;
    }

    /** Holding still under its prey while the bubbles boil up. */
    private void tellTick(ServerLevel level) {
        holdStill();
        LivingEntity target = core().target();
        if (target != null) face(target.position().subtract(position()));
        if (core().elapsed() % 2 == 0) bubbles(level, 6);
        if (core().elapsed() == TELL_TICKS / 2) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.MIRE_GULPER_TELL.get(), getSoundSource(), 1.0F, 1.2F);
        }
    }

    /**
     * Bubbles over it: in water they rise from its back and pop on the surface; out of water it froths at the mouth.
     */
    private void bubbles(ServerLevel level, int count) {
        double surface = surfaceY(level);
        if (Double.isNaN(surface)) {
            Vec3 m = mouth();
            level.sendParticles(ParticleTypes.BUBBLE_POP, m.x, getY() + 0.5, m.z, count, 0.45, 0.15, 0.45, 0.02);
            level.sendParticles(ParticleTypes.SPLASH, m.x, getY() + 0.6, m.z, count, 0.4, 0.1, 0.4, 0.0);
            return;
        }
        double low = getY() + 0.4;
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), (low + surface) / 2.0, getZ(), count, 0.45,
                Math.max(0.05, (surface - low) / 2.0), 0.45, 0.05);
        level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), surface + 0.02, getZ(), count, 0.5, 0.0, 0.5, 0.02);
        level.sendParticles(ParticleTypes.SPLASH, getX(), surface + 0.05, getZ(), Math.max(1, count / 2), 0.5, 0.0, 0.5, 0.0);
    }

    /** The height of the water's surface over it, or NaN when it isn't in water. */
    private double surfaceY(ServerLevel level) {
        BlockPos.MutableBlockPos p = BlockPos.containing(getX(), getY() + 0.2, getZ()).mutable();
        if (!level.getFluidState(p).is(FluidTags.WATER)) return Double.NaN;
        for (int i = 0; i < 8 && level.getFluidState(p.above()).is(FluidTags.WATER); i++) p.move(0, 1, 0);
        FluidState top = level.getFluidState(p);
        return p.getY() + top.getHeight(level, p);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            lunge(level, core().target());
        } else if (ended == Step.SWALLOW) {
            letGo(level);
        } else {
            core().end();
        }
    }

    /** The bubbles stop: it lunges up and swallows its prey if it is still within reach, or snaps shut on nothing. */
    private void lunge(ServerLevel level, @Nullable LivingEntity target) {
        if (target != null && across(target) <= LUNGE_REACH && Math.abs(target.getY() - getY()) <= 2.0 && swallow(target)) return;
        cooldown = COOLDOWN;
        level.playSound(null, getX(), getY(), getZ(), ModSounds.MIRE_GULPER_MISS.get(), getSoundSource(), 1.0F, 1.0F);
        Vec3 m = mouth();
        level.sendParticles(ParticleTypes.SPLASH, m.x, getY() + 0.6, m.z, 12, 0.5, 0.2, 0.5, 0.1);
        if (!startPhase(Step.RECOVER, RECOVER_TICKS, null)) core().end();
    }

    // ---- grab and carry: holding a victim in its mouth --------------------------------------------------------------
    // The victim is put back at the mouth whenever it drifts, its motion zeroed, so it is rooted and carried wherever
    // the gulper goes; the Grab says whether it still holds them.

    /** Swallows {@code prey} at once, without the tell; true if it did. Public as a test hook. */
    public boolean swallow(@Nullable LivingEntity prey) {
        if (level().isClientSide() || !swallowable(prey) || core().in(Step.SWALLOW)) return false;
        if (!startPhase(Step.SWALLOW, HOLD_TICKS, prey)) return false;
        grab.take(prey);
        hits = 0;
        cooldown = COOLDOWN;
        holdInMouth(prey, true);
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.MIRE_GULPER_GULP.get(), getSoundSource(), 1.2F, 1.0F);
            Vec3 m = mouth();
            level.sendParticles(ParticleTypes.SPLASH, m.x, getY() + 0.8, m.z, 24, 0.6, 0.3, 0.6, 0.2);
            level.sendParticles(ParticleTypes.BUBBLE_POP, m.x, getY() + 0.8, m.z, 12, 0.5, 0.2, 0.5, 0.05);
        }
        return true;
    }

    /** Where it holds its victim's feet: in its open mouth. */
    public Vec3 mouth() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return position().add(-Mth.sin(yaw) * MOUTH_FORWARD, MOUTH_UP, Mth.cos(yaw) * MOUTH_FORWARD);
    }

    private void holdTick(ServerLevel level) {
        LivingEntity v = grab.victim();
        if (v == null || !grab.holding(this, mouth())) {
            grab.release();
            if (!startPhase(Step.RECOVER, RECOVER_TICKS, null)) core().end();
            return;
        }
        holdStill();
        holdInMouth(v, false);
        int t = core().elapsed();
        if (t > 0 && t < HOLD_TICKS && t % BITE_EVERY == 0) chew(level, v);
    }

    /** Puts the victim back in its mouth if it drifted, roots it there, and keeps a shield from being raised inside. */
    private void holdInMouth(LivingEntity v, boolean always) {
        Vec3 at = mouth();
        v.resetFallDistance();
        if (v.isPassenger()) v.stopRiding();
        if (always || v.position().distanceToSqr(at) > 0.01) {
            if (v instanceof ServerPlayer p && level() instanceof ServerLevel level) {
                p.teleportTo(level, at.x, at.y, at.z, Relative.ROTATION, 0.0F, 0.0F, false);   // they still look and swing where they like
            } else {
                v.teleportTo(at.x, at.y, at.z);
            }
        }
        v.setDeltaMovement(Vec3.ZERO);
        if (v instanceof Mob m) m.getNavigation().stop();
        if (v.isBlocking()) v.stopUsingItem();
    }

    /** One chew: half its attack damage, landing in full whatever hit the victim just before. */
    private void chew(ServerLevel level, LivingEntity v) {
        if (!MechanicEvent.post(this, "chew", v)) return;
        Grab.hurt(level, v, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE_HIT);
        holdInMouth(v, true);                                 // the chew's knockback doesn't free it
        level.playSound(null, getX(), getY(), getZ(), ModSounds.MIRE_GULPER_CHEW.get(), getSoundSource(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        Vec3 m = mouth();
        level.sendParticles(ParticleTypes.BUBBLE_POP, m.x, getY() + 0.9, m.z, 6, 0.4, 0.2, 0.4, 0.05);
    }

    /** Lets go of the victim, if any, pushing it out of its mouth at {@code speed} blocks a tick (and up a little). */
    private @Nullable LivingEntity release(double speed) {
        LivingEntity v = grab.release();
        hits = 0;
        if (v != null && v.isAlive() && !v.isRemoved()) {
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            Push.set(v, new Vec3(-Mth.sin(yaw) * speed, 0.3, Mth.cos(yaw) * speed));
        }
        return v;
    }

    /** The hold ran out (failing the counter): it lets go and settles down. */
    private void letGo(ServerLevel level) {
        LivingEntity v = release(0.15);
        cooldown = COOLDOWN;
        if (v != null) level.playSound(null, getX(), getY(), getZ(), ModSounds.MIRE_GULPER_LET_GO.get(), getSoundSource(), 1.0F, 1.0F);
        if (!startPhase(Step.RECOVER, RECOVER_TICKS, null)) core().end();
    }

    /** The counter landed: three hits while it held someone. It spits them out and is stunned. Public as a test hook. */
    public void spit(ServerLevel level) {
        if (!core().in(Step.SWALLOW)) return;
        release(0.5);
        cooldown = COOLDOWN;
        Vec3 m = mouth();
        level.playSound(null, getX(), getY(), getZ(), ModSounds.MIRE_GULPER_SPIT.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, m.x, getY() + 0.8, m.z, 30, 0.5, 0.3, 0.5, 0.3);
        level.sendParticles(ParticleTypes.BUBBLE_POP, m.x, getY() + 0.8, m.z, 12, 0.4, 0.2, 0.4, 0.1);
        if (startPhase(Step.STUNNED, STUN_TICKS, null)) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.MIRE_GULPER_STUNNED.get(), getSoundSource(), 1.0F, 1.0F);
        } else if (!startPhase(Step.RECOVER, RECOVER_TICKS, null)) {
            core().end();
        }
    }

    /** Hits while it holds someone count toward spitting them out; stunned, it takes x1.5. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, core().in(Step.STUNNED) ? amount * STUN_DAMAGE : amount);
        if (hurt && isAlive() && core().in(Step.SWALLOW) && source.getEntity() != null && ++hits >= HITS_TO_SPIT) spit(level);
        return hurt;
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.STUNNED);
    }

    /** Gold stars circling over its head while it is stunned, one lap a second. */
    private void circleStars(ServerLevel level) {
        if (tickCount % 2 != 0) return;
        Vec3 head = mouth().add(0, getBbHeight() + 0.2, 0);
        double turn = core().elapsed() * (Math.PI * 2 / 20);
        for (int i = 0; i < STARS; i++) {
            double a = turn + i * (Math.PI * 2 / STARS);
            level.sendParticles(STAR, head.x + Math.cos(a) * 0.6, head.y, head.z + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
        }
    }

    // its own victim neither pushes it nor is pushed by it
    @Override
    public void push(Entity other) {
        if (!grab.holds(other)) super.push(other);
    }

    @Override
    protected void doPush(Entity other) {
        if (!grab.holds(other)) super.doPush(other);
    }

    @Override
    public void die(DamageSource source) {
        release(0.2);
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        grab.release();
        super.onRemoval(reason);
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Who it holds in its mouth, or null. */
    public @Nullable LivingEntity victim() {
        return grab.victim();
    }

    /** Hits taken during the current hold. */
    public int hits() {
        return hits;
    }

    /** Ticks until it may ambush again. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it ambush again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    /** Silent while it lies under water. */
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return submerged() ? null : ModSounds.MIRE_GULPER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MIRE_GULPER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MIRE_GULPER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.15F;
    }
}
