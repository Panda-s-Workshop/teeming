package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.Zone;
import com.panda.teeming.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Soot Manta: a wide, flat ray that glides a few blocks over the Nether's lava seas. It swoops in to bite (its attack
 * damage, 4) and climbs away between bites. With its target close (within 2.5 blocks of its middle) its wings ripple
 * for 1 s (tell): it hangs still, soot streams off its wing edges and it gives a rustling hiss. Then it sheds a soot
 * cloud where it hangs: a ball of radius 3 that lasts 5 s and stays where it was shed. Anyone inside gets Blindness and
 * Darkness for 3 s, topped up for as long as they stay in. Its next cloud comes 7 s after the last at the soonest.
 *
 * <p>Counter: fight it from outside the cloud (it leaves the cloud between bites), or chase it off: below half its
 * health it stops biting and shedding and flees from whoever threatens it, for 5 s at a time, for as long as they are
 * within 16 blocks. The bite is weak; the cloud is the threat. The cloud is an area effect it owns, never blocks.
 */
public class SootManta extends TeemingMonster {
    /** IDLE (gliding, swooping, biting), the wing ripple (tell), shedding the cloud, fleeing. */
    public enum Step implements Phase { IDLE, TELL, SHED, FLEE }

    public static final int TELL_TICKS = 20;
    /** The shed itself: wings beat down once and the soot bursts out. */
    public static final int SHED_TICKS = 10;
    /** It ripples when its target comes this close to its middle, in blocks: well inside the cloud to come. */
    public static final double SHED_RANGE = 2.5;
    public static final double CLOUD_RADIUS = 3.0;
    public static final int CLOUD_TICKS = 100;
    /** Blindness and Darkness, each this long, for anyone inside a cloud. */
    public static final int BLIND_TICKS = 60;
    /** Inside a cloud the effects are topped up to full whenever less than this is left, so they never run out there. */
    public static final int REFRESH_BELOW = 50;
    /** Ticks from one shed to the next ripple at the soonest: the cloud's 5 s and 2 s of clear air. */
    public static final int COOLDOWN = 140;

    /** Below this share of its health it stops fighting and flees. */
    public static final float FLEE_BELOW = 0.5F;
    /** One run lasts 5 s; it runs again while the threat is still this close. */
    public static final int FLEE_TICKS = 100;
    public static final double FLEE_RANGE = 16.0;
    /** How far it tries to run in one go, and the short run it falls back on when no far spot is open. */
    public static final double FLEE_DISTANCE = 12.0;
    public static final double FLEE_SHORT = 4.0;
    public static final double FLEE_SPEED = 0.45;          // blocks a tick: faster than a sprinting player

    /** The bite: its attack damage, when its body comes this close to the target's. */
    public static final double BITE_REACH = 0.4;
    /** Ticks from one bite to the next; it climbs away in between. */
    public static final int BITE_COOLDOWN = 30;
    public static final double SWOOP_SPEED = 0.35;
    /** After a bite it climbs to a spot this far out from its target and this far above. */
    public static final double RISE_OUT = 3.0;
    public static final double RISE_UP = 2.0;

    /** With nothing to hunt it glides this high over whatever is below (lava, ground) at this speed. */
    public static final double GLIDE_HEIGHT = 3.0;
    public static final double GLIDE_SPEED = 0.15;
    private static final int GLIDE_SPREAD = 8;
    private static final int GROUND_SEARCH = 16;

    public static final int SOOT_COLOR = 0x2E2926;
    /** The fleck of soot that marks a cloud's edge; the body of the cloud is puffs of ink-black smoke. */
    private static final DustParticleOptions SOOT = new DustParticleOptions(SOOT_COLOR, 0.7F);
    private static final DustParticleOptions FINE_SOOT = new DustParticleOptions(0x4A4440, 0.8F);
    private static final Telegraph RIPPLE = Telegraph.particles(FINE_SOOT, 9, 1.2, 0.3).withSpeed(0.01);
    private static final Telegraph BURST = Telegraph.particles(ParticleTypes.SQUID_INK, 16, 0.6, 0.5).withSpeed(0.1);
    private static final Telegraph BOLT = Telegraph.particles(ParticleTypes.LARGE_SMOKE, 10, 0.6, 0.5).withSpeed(0.04);
    private static final int SHELL_POINTS = 8;
    /** The cooldown outlasts a cloud, so there is one at a time; this caps the clouds a test hook can stack. */
    private static final int MAX_CLOUDS = 4;

    private int cooldown;
    private int biteCooldown;
    private @Nullable LivingEntity fleeFrom;
    private @Nullable Vec3 fleeSpot;
    private int fleeRepath;
    private @Nullable Vec3 glideSpot;
    private int glideRepath;
    private int bites;

    public SootManta(EntityType<? extends SootManta> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("soot_manta", 0.3).add(Attributes.FLYING_SPEED, 0.5);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    /** It steers itself (see {@link #customServerAiStep}); the goals only look about and pick targets. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    /** True below half its health: it no longer bites or sheds, and flees whoever threatens it. */
    public boolean timid() {
        return getHealth() < getMaxHealth() * FLEE_BELOW;
    }

    // ---- the hunt ----------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        if (cooldown > 0) cooldown--;
        if (biteCooldown > 0) biteCooldown--;
        getNavigation().stop();
        if (core().in(Step.FLEE)) {
            fleeTick(level);
            return;
        }
        if (core().in(Step.TELL)) {
            rippleTick(core().target());
            return;
        }
        if (core().in(Step.SHED)) {
            setDeltaMovement(getDeltaMovement().scale(0.5));
            return;
        }
        LivingEntity target = getTarget();
        if (timid()) {
            LivingEntity threat = threat(null);
            if (threat != null && distanceTo(threat) <= FLEE_RANGE && flee(threat)) return;
            glideTick(level);
            return;
        }
        if (!valid(target)) {
            glideTick(level);
            return;
        }
        glideSpot = null;
        if (canRipple(target) && startPhase(Step.TELL, TELL_TICKS, target)) {
            RIPPLE.withSound(ModSounds.SOOT_MANTA_RIPPLE.get(), 1.0F, 1.0F).play(this);
            return;
        }
        swoopTick(level, target);
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
    }

    /** It may ripple: no cloud for {@link #COOLDOWN} ticks, its target within {@link #SHED_RANGE} and in sight. */
    private boolean canRipple(LivingEntity target) {
        return cooldown == 0 && core().idle() && reach(target) <= SHED_RANGE && getSensing().hasLineOfSight(target);
    }

    /** How far the target's body is from its middle, in blocks (0 when it overlaps it). */
    private double reach(LivingEntity target) {
        return Math.sqrt(target.getBoundingBox().distanceToSqr(centre()));
    }

    /** The swoop: straight at the target's eyes, a bite on contact, then a climb out and up until the next bite. */
    private void swoopTick(ServerLevel level, LivingEntity target) {
        Vec3 eyes = target.getEyePosition();
        if (biteCooldown > 0) {
            Vec3 away = position().subtract(target.position()).horizontal();
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
            Vec3 rise = target.position().add(away.normalize().scale(RISE_OUT)).add(0, RISE_UP + target.getBbHeight() * 0.5, 0);
            steer(rise, SWOOP_SPEED, 0.25);
            return;
        }
        steer(eyes.subtract(0, getBbHeight() * 0.5, 0), SWOOP_SPEED, 0.3);
        face(eyes.subtract(centre()));
        if (getBoundingBox().inflate(BITE_REACH).intersects(target.getBoundingBox())) bite(level, target);
    }

    private void bite(ServerLevel level, LivingEntity target) {
        biteCooldown = BITE_COOLDOWN;
        if (!MechanicEvent.post(this, "bite", target)) return;
        swingForAttack(InteractionHand.MAIN_HAND);
        if (doHurtTarget(level, target)) bites++;
        playSound(ModSounds.SOOT_MANTA_BITE.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** The ripple: it hangs where it is, facing its target, and soot streams off its wing edges. */
    private void rippleTick(@Nullable LivingEntity target) {
        setDeltaMovement(getDeltaMovement().scale(0.5));
        if (target != null) face(target.position().subtract(position()));
        RIPPLE.trail(this, 2);
        if (core().elapsed() == TELL_TICKS / 2) playSound(ModSounds.SOOT_MANTA_RIPPLE.get(), 0.8F, 1.15F);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            cooldown = COOLDOWN;
            if (startPhase(Step.SHED, SHED_TICKS, core().target())) shed(level);
            else core().end();
        } else if (ended == Step.FLEE) {
            LivingEntity threat = threat(fleeFrom);
            core().end();
            if (timid() && threat != null && distanceTo(threat) <= FLEE_RANGE) flee(threat);
        } else {
            core().end();
        }
    }

    /**
     * Sheds a soot cloud where it hangs; returns its middle, or null if an event cancelled it. Starts the
     * {@link #COOLDOWN}. Public as a test hook.
     */
    public @Nullable Vec3 shed(ServerLevel level) {
        cooldown = COOLDOWN;
        if (!MechanicEvent.post(this, "soot_cloud", core().target())) return null;
        Vec3 at = centre();
        clouds.open(level, at, CLOUD_TICKS);
        BURST.withSound(ModSounds.SOOT_MANTA_SHED.get(), 1.2F, 1.0F).play(this);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 24, 1.0, 0.8, 1.0, 0.02);
        return at;
    }

    // ---- fleeing -----------------------------------------------------------------------------------------------------

    /** Who it runs from: {@code preferred}, else whoever last hurt it, else its target; null if none is about. */
    private @Nullable LivingEntity threat(@Nullable LivingEntity preferred) {
        for (LivingEntity e : new LivingEntity[] {preferred, getLastHurtByMob(), getTarget()}) {
            if (e != null && e.isAlive() && e.level() == level() && e != this) return e;
        }
        return null;
    }

    /** Runs from {@code from} for {@link #FLEE_TICKS}, dropping its target; false if vetoed. Public as a test hook. */
    public boolean flee(LivingEntity from) {
        if (!startPhase(Step.FLEE, FLEE_TICKS, from)) return false;
        fleeFrom = from;
        fleeSpot = null;
        fleeRepath = 0;
        setTarget(null);
        BOLT.withSound(ModSounds.SOOT_MANTA_FLEE.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    /**
     * A spot {@link #FLEE_DISTANCE} away from the threat (straight away, or up to 105 degrees off), else a short run of
     * {@link #FLEE_SHORT}; only spots in open air with a clear line to them. Null when every one is shut.
     */
    private @Nullable Vec3 fleeSpot(Vec3 threat) {
        Vec3 away = centre().subtract(threat).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0.0F, getYRot());
        away = away.normalize();
        for (double distance : new double[] {FLEE_DISTANCE, FLEE_SHORT}) {
            for (float turn : new float[] {0, 35, -35, 70, -70, 105, -105}) {
                Vec3 spot = centre().add(away.yRot(turn * Mth.DEG_TO_RAD).scale(distance)).add(0, 1.0, 0);
                if (open(spot)) return spot;
            }
        }
        return null;
    }

    /** Whether it fits at {@code spot} (its middle there) and has a clear line to it. */
    private boolean open(Vec3 spot) {
        AABB there = getBoundingBox().move(spot.subtract(centre()));
        if (!level().noCollision(this, there)) return false;
        return level().clip(new ClipContext(centre(), spot, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this)).getType() == HitResult.Type.MISS;
    }

    private void fleeTick(ServerLevel level) {
        setTarget(null);
        LivingEntity threat = threat(fleeFrom);
        Vec3 from = threat != null ? threat.position() : position();
        if (--fleeRepath <= 0 || fleeSpot == null || fleeSpot.distanceToSqr(centre()) < 1.5 * 1.5) {
            fleeSpot = fleeSpot(from);
            fleeRepath = 20;
        }
        if (fleeSpot != null) {
            steer(fleeSpot.subtract(0, getBbHeight() * 0.5, 0), FLEE_SPEED, 0.4);
        } else {
            // cornered, every spot shut: bolt straight away from the threat, climbing where a wall stops it
            Vec3 away = position().subtract(from).horizontal();
            if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0.0F, getYRot());
            Vec3 v = away.normalize().scale(FLEE_SPEED).add(0, horizontalCollision ? FLEE_SPEED : 0.0, 0);
            setDeltaMovement(getDeltaMovement().lerp(v, 0.4));
            face(v);
        }
        if (tickCount % 3 == 0) level.sendParticles(FINE_SOOT, getX(), getY() + getBbHeight() * 0.5, getZ(), 2, 0.5, 0.1, 0.5, 0.0);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && timid() && !core().in(Step.FLEE)) {
            LivingEntity threat = threat(source.getEntity() instanceof LivingEntity attacker ? attacker : null);
            if (threat != null) flee(threat);
        }
        return hurt;
    }

    // ---- gliding -----------------------------------------------------------------------------------------------------

    /** With nothing to hunt it glides from spot to spot, {@link #GLIDE_HEIGHT} over whatever lies below. */
    private void glideTick(ServerLevel level) {
        if (--glideRepath <= 0 || glideSpot == null || glideSpot.distanceToSqr(position()) < 1.0) {
            glideSpot = glideSpot(level);
            glideRepath = 80;
        }
        if (glideSpot != null) steer(glideSpot, GLIDE_SPEED, 0.1);
        else setDeltaMovement(getDeltaMovement().scale(0.8));
    }

    private @Nullable Vec3 glideSpot(ServerLevel level) {
        double x = getX() + random.nextInt(GLIDE_SPREAD * 2 + 1) - GLIDE_SPREAD;
        double z = getZ() + random.nextInt(GLIDE_SPREAD * 2 + 1) - GLIDE_SPREAD;
        double y = surfaceBelow(level, x, getY() + 2.0, z) + GLIDE_HEIGHT;
        Vec3 spot = new Vec3(x, y, z);
        return open(spot.add(0, getBbHeight() * 0.5, 0)) ? spot : null;
    }

    /** The top of the first solid block or fluid below {@code y}, within 16 blocks; its own height minus the glide height if none. */
    private double surfaceBelow(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < GROUND_SEARCH; i++, pos.move(0, -1, 0)) {
            if (!level.getFluidState(pos).isEmpty()) return pos.getY() + level.getFluidState(pos).getHeight(level, pos);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return pos.getY() + 1.0;
        }
        return getY() - GLIDE_HEIGHT;
    }

    // ---- steering ----------------------------------------------------------------------------------------------------

    /** Eases its flight toward {@code want} (its feet there) at up to {@code speed} blocks a tick, facing where it goes. */
    private void steer(Vec3 want, double speed, double ease) {
        Vec3 to = want.subtract(position());
        if (to.length() > speed) to = to.normalize().scale(speed);
        Vec3 v = getDeltaMovement().lerp(to, ease);
        setDeltaMovement(v);
        if (v.horizontalDistanceSqr() > 4.0E-4) face(v);
        setXRot((float) Mth.clamp(-Math.toDegrees(Math.atan2(v.y, Math.max(0.05, v.horizontalDistance()))), -30.0, 30.0));
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private Vec3 centre() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }

    // ---- the soot clouds: balls of air with an effect inside, never blocks ----------------------------------------

    private final Zone clouds = new Zone(Zone.Shape.ball(CLOUD_RADIUS), MAX_CLOUDS)
            .affects(e -> !(e instanceof SootManta) && e.isAlive() && EntitySelector.NO_SPECTATORS.test(e), this::soot)
            .drawn(this::drawCloud);

    /** The clouds keep working while it dies, and go with it. */
    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) clouds.tick(level);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        clouds.clear();
        super.onRemoval(reason);
    }

    /** Ink-black puffs through the ball, flecks of soot on its edge so its size shows, and a slow smoke. */
    private void drawCloud(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 2, 1.0, 0.8, 1.0, 0.005);
        if (tickCount % 3 == 0) {
            for (int i = 0; i < SHELL_POINTS; i++) {
                Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.8, random.nextGaussian());
                if (d.lengthSqr() < 1.0E-4) continue;
                Vec3 p = at.add(d.normalize().scale(CLOUD_RADIUS));
                level.sendParticles(SOOT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (tickCount % 4 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 1, 0.9, 0.7, 0.9, 0.005);
    }

    /** Blindness and Darkness for {@link #BLIND_TICKS}, topped up whenever less than {@link #REFRESH_BELOW} is left. */
    private void soot(LivingEntity e) {
        for (var effect : List.of(MobEffects.BLINDNESS, MobEffects.DARKNESS)) {
            MobEffectInstance has = e.getEffect(effect);
            if (has == null || has.getDuration() < REFRESH_BELOW) e.addEffect(new MobEffectInstance(effect, BLIND_TICKS, 0), this);
        }
    }

    /** The middles of its clouds still in the air. Public as a test hook. */
    public List<Vec3> clouds() {
        return clouds.centres();
    }

    // ---- test hooks --------------------------------------------------------------------------------------------------

    /** Ticks until it may ripple again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Sets the ticks until it may ripple again. Public as a test hook. */
    public void setCooldown(int ticks) {
        cooldown = ticks;
    }

    /** Bites that landed so far. Public as a test hook. */
    public int bites() {
        return bites;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SOOT_MANTA_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SOOT_MANTA_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SOOT_MANTA_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.15F;
    }
}
