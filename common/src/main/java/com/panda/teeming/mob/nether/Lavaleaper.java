package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lavaleaper: a lean, eel-finned fish of the lava seas. It swims fully under the lava surface at 0.2 blocks a tick and
 * ambushes the shore: once its target is within 4 blocks (across) of it in its lava, it rises to the surface for 10
 * ticks, bubbles and a glow rising where it is (tell), then leaps out in an arc at the target, bites it for its attack
 * damage and sets it on fire for 3 seconds, and flops back into the lava. It leaps again 40 ticks after it is back in.
 *
 * <p>Counter: hook it with a fishing rod's bobber and reel in. The pull yanks it out in an arc onto land in front of
 * the angler, where it lies helpless for 100 ticks: it flops on the spot, can't move or bite, and takes 1.5 times the
 * damage from every blow (its throat gland glows weak-point gold). Out of the lava for any reason it wriggles back to
 * the nearest lava in hops. Nothing burns it.
 *
 * <p>The lava parts are kept in their own sections, as the Magma Tortoise's are: the float and the swim (its own
 * travel in lava, steered towards {@link #wanted()}), the arc flights (leap, return, yank: a scripted ballistic
 * flight), and the reel-in (a bobber hooked on it, then reeled in by an angler still holding the rod).
 */
public class Lavaleaper extends TeemingMonster {
    /** Lurking (or stranded, wriggling), the tell, the leap, the flop back, yanked out by a rod, lying helpless. */
    public enum Step implements Phase { IDLE, TELL, LEAP, RETURN, YANKED, HELPLESS }

    /** Bubbles and a glow at the surface for half a second before the leap. */
    public static final int TELL_TICKS = 10;
    /** It leaps at a target this close (across) to it, from the lava it swims in. */
    public static final double LEAP_RANGE = 4.0;
    /** The bite sets its target on fire for 3 seconds. */
    public static final float FIRE_SECONDS = 3.0F;
    /** Yanked onto land it lies helpless for 5 seconds. */
    public static final int HELPLESS_TICKS = 100;
    /** Helpless, it takes x1.5 from every blow. */
    public static final float HELPLESS_DAMAGE = 1.5F;
    /** Its swimming speed under the lava, in blocks a tick. */
    public static final double SWIM_SPEED = 0.2;
    /** Ticks back in the lava after a leap before the next tell can start. */
    public static final int LEAP_COOLDOWN = 40;
    /** Lava over its feet while it lurks: its 0.6-high back stays 0.15 under the surface. */
    public static final double SINK_LURK = 0.75;
    /** Lava over its feet in the tell: it rises half sunk, its back and spines out of the lava. */
    public static final double SINK_TELL = 0.3;
    /** It keeps a target this close; further away it lets it go. */
    public static final double HUNT_RANGE = 16.0;
    /** It leaps only at targets whose feet are at most this high over the lava surface. */
    public static final double LEAP_MAX_RISE = 1.5;
    /** Upward speed as it leaves the lava (blocks a tick); the arc peaks about 2.2 blocks up. */
    public static final double LEAP_UP = 0.6;
    /** Upward speed of the flop back into the lava, and of the yank onto land. */
    public static final double RETURN_UP = 0.4, YANK_UP = 0.55;
    /** Pull of every arc flight, in blocks a tick per tick. */
    public static final double GRAVITY = 0.08;
    /** Any flight ends after this many ticks at most, wherever it is. */
    public static final int FLIGHT_TICKS = 40;
    /** The yank lands it this far short of the angler, and carries it at most this far. */
    public static final double YANK_SHORT = 1.5, YANK_MAX = 10.0;
    /** Reeling in counts only within a fishing line's reach of the angler. */
    public static final double LINE_REACH = 32.0;
    /** Stranded: a hop towards lava this often, towards lava found this close. */
    public static final int WRIGGLE_EVERY = 12;
    public static final int FIND_LAVA = 8;
    /** {@code core().flag} bit: out of the lava and free (clients lay it on its side and let it wriggle). */
    public static final int STRANDED = 0;

    private static final double EASE = 0.4;            // horizontal speed eased towards the wanted one, per tick
    private static final double FLOAT_PULL = 0.35;     // the float closes this share of its depth error a tick
    private static final double MAX_RISE = 0.15;
    private static final float TURN = 18.0F;           // degrees a tick: a quick, supple swimmer
    private static final double ARRIVE = 0.3;
    private static final double LOOK_AHEAD = 0.3;      // lava must go on this far past its snout
    private static final double HOP_SIDE = 0.25, HOP_UP = 0.32;
    private static final int GLOW_REST = 0, GLOW_HOT = 1, GLOW_DARK = 2;

    /** Where it is swimming to, server side; null when it holds still. */
    private @Nullable Vec3 wanted;
    private int cooldown;
    // the arc flight
    private boolean airborne, bitten;
    private Vec3 flight = Vec3.ZERO;
    private Vec3 leapFrom = Vec3.ZERO;
    // a bobber hooked on it and whose line it is
    private @Nullable FishingHook hook;
    private @Nullable Player angler;
    /** The phase the glow stage was last set for. */
    private int stagedPhase = -1;

    public Lavaleaper(EntityType<? extends Lavaleaper> type, Level level) {
        super(type, level, Step.values());
        setPathfindingMalus(PathType.LAVA, 0.0F);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
        setPathfindingMalus(PathType.FIRE, 0.0F);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("lavaleaper", 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new LavaWanderGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Lavaleaper.class));
        // it senses the shore through the lava: no sight needed to pick a target, only to leap at it
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public int visualStages() {
        return 3;
    }

    // ---- floating and swimming under the lava ----------------------------------------------------------------------

    /** In lava it holds its depth and swims on its own; anywhere else it moves as vanilla mobs do. */
    @Override
    protected void travelInFluid(Vec3 input) {
        if (!isInLava()) {
            super.travelInFluid(input);
            return;
        }
        double depth = getFluidHeight(FluidTags.LAVA);
        double sink = core().in(Step.TELL) ? SINK_TELL : SINK_LURK;
        double vy = Mth.clamp((depth - sink) * FLOAT_PULL, -MAX_RISE, MAX_RISE);
        Vec3 v = getDeltaMovement();
        if (!core().idle()) {
            setDeltaMovement(0.0, vy, 0.0);                     // in the tell (or posed) it holds its spot
        } else {
            Vec3 swim = swim();
            setDeltaMovement(v.x + (swim.x - v.x) * EASE, vy, v.z + (swim.z - v.z) * EASE);
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    /** The horizontal velocity it wants this tick: turning towards {@link #wanted}, at full speed once it faces it. */
    private Vec3 swim() {
        if (wanted == null) return Vec3.ZERO;
        Vec3 to = wanted.subtract(position()).multiply(1, 0, 1);
        double dist = to.length();
        if (dist < ARRIVE) return Vec3.ZERO;
        float goal = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(getYRot(), goal, TURN);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        Vec3 heading = heading(yaw);
        if (!lavaAhead(heading)) return Vec3.ZERO;
        double align = Math.max(0.0, Math.cos(Mth.degreesDifference(yaw, goal) * Mth.DEG_TO_RAD));
        return heading.scale(SWIM_SPEED * align * Math.min(1.0, dist / 0.6));
    }

    private static Vec3 heading(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
    }

    /** True when the lava goes on past its snout across its width: it never swims out of the lava on its own. */
    public boolean lavaAhead(Vec3 heading) {
        double half = getBbWidth() / 2.0;
        Vec3 front = position().add(heading.scale(half + LOOK_AHEAD));
        Vec3 side = new Vec3(-heading.z, 0.0, heading.x).scale(half * 0.8);
        return lavaAt(front) && lavaAt(front.add(side)) && lavaAt(front.subtract(side));
    }

    private boolean lavaAt(Vec3 spot) {
        return level().getFluidState(BlockPos.containing(spot.x, getY() + 0.1, spot.z)).is(FluidTags.LAVA);
    }

    private boolean footprintInLava(Vec3 spot) {
        double half = getBbWidth() / 2.0;
        return lavaAt(spot) && lavaAt(spot.add(half, 0, 0)) && lavaAt(spot.add(-half, 0, 0))
                && lavaAt(spot.add(0, 0, half)) && lavaAt(spot.add(0, 0, -half));
    }

    /** Lurking: in the lava with its back under the surface, out of sight. Its tell brings its back out. */
    @Override
    public boolean hiddenFromSight() {
        double surface = lavaSurface();
        return !Double.isNaN(surface) && getBoundingBox().maxY < surface;
    }

    /** The height of the lava surface over its feet, or NaN out of the lava. */
    public double lavaSurface() {
        return isInLava() ? getY() + getFluidHeight(FluidTags.LAVA) : Double.NaN;
    }

    /** Where it is swimming to, or null (server side). */
    public @Nullable Vec3 wanted() {
        return wanted;
    }

    /** Sends it swimming to a spot (null: hold still). It still stops where the lava ends. Public as a test hook. */
    public void swimTo(@Nullable Vec3 spot) {
        wanted = spot;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Lava is where it wants to be, in any light (the monster default refuses a spot as bright as lava). */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getFluidState(pos).is(FluidTags.LAVA) ? 10.0F : super.getWalkTargetValue(pos, level);
    }

    /** Lava spawns: only blocks count as obstruction (the default refuses any liquid in the hitbox). */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** With nothing to hunt it cruises: now and then a spot a few blocks off it can reach without leaving the lava. */
    private class LavaWanderGoal extends Goal {
        private int wait = 20;
        private int time;

        LavaWanderGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!core().idle() || !isInLava() || airborne || getTarget() != null) return false;
            if (--wait > 0) return false;
            wait = 30 + random.nextInt(60);
            Vec3 spot = pick();
            if (spot == null) return false;
            wanted = spot;
            return true;
        }

        @Override
        public void start() {
            time = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return wanted != null && core().idle() && isInLava() && !airborne && getTarget() == null && ++time < 100
                    && wanted.subtract(position()).horizontalDistance() > ARRIVE;
        }

        @Override
        public void stop() {
            if (getTarget() == null) wanted = null;
        }

        private @Nullable Vec3 pick() {
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = 2.0 + random.nextDouble() * 5.0;
                Vec3 spot = position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
                if (clearRun(spot)) return spot;
            }
            return null;
        }

        private boolean clearRun(Vec3 spot) {
            Vec3 step = spot.subtract(position());
            int n = (int) Math.ceil(step.length() / 0.5);
            for (int i = 1; i <= n; i++) if (!footprintInLava(position().add(step.scale(i / (double) n)))) return false;
            return true;
        }
    }

    // ---- hunting -----------------------------------------------------------------------------------------------------

    /** A target it keeps: alive, here, within 16 blocks, and not a player in creative or spectator. */
    public boolean huntable(@Nullable LivingEntity e) {
        if (e == null || !e.isAlive() || e.isRemoved() || e.level() != level() || e == this || e instanceof Lavaleaper) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return distanceTo(e) <= HUNT_RANGE;
    }

    /**
     * True when it can leap at {@code target} now: it is in the lava, the target is within 4 blocks across, its feet at
     * most 1.5 over the lava surface (and not far below it), and in sight from just over the surface, where the leap
     * starts (from its eyes under the lava the shore's lip would hide anyone standing back from the edge).
     */
    public boolean inLeapReach(LivingEntity target) {
        if (!isInLava()) return false;
        double across = target.position().subtract(position()).horizontalDistance();
        double rise = target.getY() - lavaSurface();
        if (across > LEAP_RANGE || rise > LEAP_MAX_RISE || rise < -2.0) return false;
        Vec3 from = new Vec3(getX(), lavaSurface() + 0.3, getZ());
        return level().clip(new ClipContext(from, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        if (target != null && !huntable(target)) {
            setTarget(null);
            target = null;
        }
        if (airborne) {
            flightTick(level);
        } else if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().in(Step.HELPLESS)) {
            helplessTick(level);
        } else if (core().idle()) {
            if (!isInLava()) {
                wanted = null;
                strandedTick(level);
            } else if (target != null) {
                if (cooldown == 0 && inLeapReach(target)) tell(target);
                else wanted = target.position();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!isNoAi()) watchHook(level);
        if (phase().ordinal() != stagedPhase) {                // the glow follows each change of phase
            stagedPhase = phase().ordinal();
            core().setStage(glowStage());
        }
        core().setFlag(STRANDED, core().idle() && !airborne && !isInLava() && !isNoAi());
    }

    /** Embers at rest; hot through the tell, the leap and the flop back; dark while yanked out and helpless. */
    private int glowStage() {
        if (core().in(Step.TELL) || core().in(Step.LEAP) || core().in(Step.RETURN)) return GLOW_HOT;
        if (core().in(Step.YANKED) || core().in(Step.HELPLESS)) return GLOW_DARK;
        return GLOW_REST;
    }

    // ---- the tell ------------------------------------------------------------------------------------------------------

    /**
     * Starts the tell at {@code target}: it rises to the surface for 10 ticks, then leaps. True if it started. Ignores the
     * cooldown and the reach. Public as a test hook.
     */
    public boolean tell(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle() || airborne || !isInLava()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        wanted = null;
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (target != null) face(target.position().subtract(position()));
        if (level() instanceof ServerLevel level) {
            double y = lavaSurface();
            level.playSound(null, getX(), y, getZ(), ModSounds.LAVALEAPER_TELL.get(), getSoundSource(), 1.2F, 1.0F);
            level.sendParticles(ParticleTypes.LAVA, getX(), y, getZ(), 3, 0.35, 0.0, 0.35, 0.0);
        }
        return true;
    }

    /** Holding still under the surface, rising, facing its target: bubbles burst and smoke and flame rise over it. */
    private void tellTick(ServerLevel level) {
        LivingEntity target = core().target();
        if (target != null) face(target.position().subtract(position()));
        double y = isInLava() ? lavaSurface() : getY() + getBbHeight();
        int t = core().elapsed();
        if (t % 2 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), y + 0.05, getZ(), 2, 0.35, 0.02, 0.35, 0.01);
        if (t % 3 == 0) level.sendParticles(ParticleTypes.LAVA, getX(), y, getZ(), 1, 0.3, 0.0, 0.3, 0.0);
        level.sendParticles(ParticleTypes.FLAME, getX(), y + 0.1, getZ(), 2, 0.25, 0.05, 0.25, 0.01 + 0.004 * t);
        if (t == TELL_TICKS / 2) level.playSound(null, getX(), y, getZ(), ModSounds.LAVALEAPER_TELL.get(), getSoundSource(), 0.8F, 1.3F);
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (isNoAi() || !(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (target != null && target.isAlive() && isInLava()) leap(level, target);
            else core().end();
        } else {
            airborne = false;                                // a flight timed out, or the helpless spell ran out
            setXRot(0.0F);
            core().end();
        }
    }

    // ---- arc flights: the leap, the flop back, the yank --------------------------------------------------------------
    // Self-contained: a scripted ballistic flight from where it is to a spot, starting upward at a given speed and
    // pulled down by GRAVITY each tick, with its collisions on (a wall stops it across; it falls from there). It ends
    // the tick it comes down on the ground or back into the lava, or after FLIGHT_TICKS.

    /** The start velocity that carries it from {@code from} to {@code to} in an arc that leaves upward at {@code up}. */
    public static Vec3 arc(Vec3 from, Vec3 to, double up) {
        double dy = to.y - from.y;
        double climb = Math.max(up, Math.sqrt(2.0 * GRAVITY * Math.max(0.0, dy + 0.3)));   // always clears the spot
        double ticks = (climb + Math.sqrt(Math.max(0.0, climb * climb - 2.0 * GRAVITY * dy))) / GRAVITY;
        Vec3 across = to.subtract(from).multiply(1, 0, 1);
        return new Vec3(across.x / ticks, climb, across.z / ticks);
    }

    /** In flight it follows its arc alone (no lava float, no air drag); otherwise it moves as usual. */
    @Override
    public void travel(Vec3 input) {
        if (airborne) {
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        super.travel(input);
    }

    /** Starts a flight; the arc's first step is taken on the next AI step. */
    private void fly(Vec3 start) {
        airborne = true;
        wanted = null;
        flight = start;
        setDeltaMovement(Vec3.ZERO);
        face(start);
    }

    /** Out of the lava in an arc at {@code target}, aimed where it stands now (at most 4 blocks across). */
    private void leap(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.LEAP, FLIGHT_TICKS, target)) {
            core().end();                                    // vetoed: it sinks back and lurks
            return;
        }
        leapFrom = position();
        bitten = false;
        Vec3 to = target.position().subtract(leapFrom).multiply(1, 0, 1);
        if (to.length() > LEAP_RANGE) to = to.normalize().scale(LEAP_RANGE);
        fly(arc(leapFrom, leapFrom.add(to.x, target.getY() - leapFrom.y, to.z), LEAP_UP));
        level.playSound(null, getX(), getY(), getZ(), ModSounds.LAVALEAPER_LEAP.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.5, getZ(), 5, 0.3, 0.1, 0.3, 0.0);
    }

    /** Each tick in the air: the bite on contact, the landing, else the next step of the arc. */
    private void flightTick(ServerLevel level) {
        resetFallDistance();
        LivingEntity target = core().target();
        if (core().in(Step.LEAP) && !bitten && target != null && target.isAlive()
                && getBoundingBox().inflate(0.2).intersects(target.getBoundingBox())) {
            bite(level, target);
            return;
        }
        boolean falling = flight.y < 0.0;
        if (falling && (onGround() || isInLava())) {
            land(level);
            return;
        }
        double vx = horizontalCollision ? 0.0 : flight.x, vz = horizontalCollision ? 0.0 : flight.z;
        setDeltaMovement(vx, flight.y, vz);
        float pitch = (float) (-Mth.atan2(flight.y, Math.max(0.05, Math.sqrt(vx * vx + vz * vz))) * Mth.RAD_TO_DEG);
        setXRot(Mth.clamp(pitch, -60.0F, 60.0F));
        flight = new Vec3(vx, flight.y - GRAVITY, vz);
    }

    /** The bite: its attack damage, and its target set on fire for 3 seconds; then it flops back. */
    private void bite(ServerLevel level, LivingEntity target) {
        bitten = true;
        if (MechanicEvent.post(this, "bite", target)) {
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (target.hurtServer(level, damageSources().mobAttack(this), damage)) target.igniteForSeconds(FIRE_SECONDS);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.LAVALEAPER_BITE.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.25, 0.3, 0.25, 0.02);
        }
        flopBack(level);
    }

    /** Back towards the lava it leapt from, in a short arc. */
    private void flopBack(ServerLevel level) {
        if (!startPhase(Step.RETURN, FLIGHT_TICKS, null)) core().end();   // vetoed: it just falls where it is
        fly(arc(position(), leapFrom, RETURN_UP));
    }

    /** Down: back in the lava (it lurks again), on land (stranded), or, yanked, on land and helpless. */
    private void land(ServerLevel level) {
        airborne = false;
        setXRot(0.0F);
        setDeltaMovement(getDeltaMovement().multiply(0.2, 0.0, 0.2));
        boolean yanked = core().in(Step.YANKED);
        if (isInLava()) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.LAVALEAPER_SPLASH.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.4, getZ(), 4, 0.3, 0.05, 0.3, 0.0);
            if (!yanked) cooldown = LEAP_COOLDOWN;
            core().end();
        } else if (yanked) {
            if (!helpless(level)) core().end();
        } else {
            core().end();                                    // stranded: it wriggles back from here
        }
    }

    // ---- the counter: hooked and reeled in -----------------------------------------------------------------------------
    // Self-contained: each tick it notes a fishing bobber hooked on it and the bobber's angler. When that bobber is gone,
    // discarded (as reeling in does) while its angler is alive, within a line's reach and still holding a rod, the
    // angler reeled it in. A line that breaks any other way (the angler switched items, walked off, died) yanks nothing.

    private void watchHook(ServerLevel level) {
        if (hook != null) {
            if (hook.isRemoved()) {
                Player who = angler;
                boolean reeled = hook.getRemovalReason() == Entity.RemovalReason.DISCARDED && who != null && who.isAlive()
                        && !who.isSpectator() && who.level() == level && holdsRod(who) && who.distanceTo(this) <= LINE_REACH;
                hook = null;
                angler = null;
                if (reeled) yank(level, who);
            } else if (hook.getHookedIn() != this) {
                hook = null;
                angler = null;
            }
        }
        if (hook == null) {
            for (FishingHook h : level.getEntitiesOfClass(FishingHook.class, getBoundingBox().inflate(2.0), h -> h.getHookedIn() == this)) {
                hook = h;
                angler = h.getPlayerOwner();
                break;
            }
        }
    }

    /** Fishing rods from any mod: the common rod tag both loaders share, or anything built on the vanilla item. */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> RODS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "tools/fishing_rod"));

    private static boolean rod(net.minecraft.world.item.ItemStack stack) {
        return stack.is(RODS) || stack.getItem() instanceof FishingRodItem;
    }

    private static boolean holdsRod(Player p) {
        return rod(p.getMainHandItem()) || rod(p.getOffhandItem());
    }

    /** The bobber hooked on it now, or null (server side). */
    public @Nullable FishingHook hook() {
        return hook;
    }

    /**
     * Reeled in by {@code angler}: yanked out in an arc onto the ground just in front of them, where it lands helpless.
     * True if it was. Public as a test hook.
     */
    public boolean yank(ServerLevel level, Player angler) {
        if (core().in(Step.YANKED) || !isAlive()) return false;
        if (!startPhase(Step.YANKED, FLIGHT_TICKS, angler)) return false;
        Vec3 from = position();
        Vec3 spot = landing(angler);
        Vec3 to = spot.subtract(from).multiply(1, 0, 1);
        if (to.length() > YANK_MAX) to = to.normalize().scale(YANK_MAX);
        fly(arc(from, from.add(to.x, spot.y - from.y, to.z), YANK_UP));
        level.playSound(null, getX(), getY(), getZ(), ModSounds.LAVALEAPER_HOOKED.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.5, getZ(), 6, 0.3, 0.1, 0.3, 0.0);
        return true;
    }

    /**
     * Where the yank lands it: on the line from the angler back towards it, as close to 1.5 blocks short of the angler as
     * keeps lava from under its whole body; at the angler's feet when nowhere short of them is dry.
     */
    private Vec3 landing(Player angler) {
        Vec3 back = position().subtract(angler.position()).multiply(1, 0, 1);
        if (back.lengthSqr() < 1.0E-4) return angler.position();
        Vec3 dir = back.normalize();
        for (double d = Math.min(YANK_SHORT, back.length()); d > 0.0; d -= 0.25) {
            Vec3 spot = angler.position().add(dir.scale(d));
            if (dryUnder(spot)) return spot;
        }
        return angler.position();
    }

    /** No lava under its body (its centre and four edge points) if it lay at {@code spot}. */
    private boolean dryUnder(Vec3 spot) {
        double half = getBbWidth() / 2.0;
        for (Vec3 p : new Vec3[] {spot, spot.add(half, 0, 0), spot.add(-half, 0, 0), spot.add(0, 0, half), spot.add(0, 0, -half)}) {
            for (double dy : new double[] {0.1, -0.5}) {
                if (level().getFluidState(BlockPos.containing(p.x, p.y + dy, p.z)).is(FluidTags.LAVA)) return false;
            }
        }
        return true;
    }

    /** On land after the yank: helpless for 100 ticks. True if it was. Public as a test hook. */
    public boolean helpless(ServerLevel level) {
        airborne = false;
        if (!startPhase(Step.HELPLESS, HELPLESS_TICKS, null)) return false;
        setDeltaMovement(0.0, Math.min(0.0, getDeltaMovement().y), 0.0);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.LAVALEAPER_FLOP.get(), getSoundSource(), 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 12, 0.4, 0.1, 0.4, 0.02);
        return true;
    }

    /** Flopping on the spot: a gasp of smoke and a slap now and then, never a move. */
    private void helplessTick(ServerLevel level) {
        int t = core().elapsed();
        if (t % 10 == 5) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 3, 0.25, 0.05, 0.25, 0.01);
            playSound(ModSounds.LAVALEAPER_FLOP.get(), 0.7F, getVoicePitch());
        }
    }

    /** Helpless it can't move: only falling moves it. */
    @Override
    public Pin pin() {
        return core().in(Step.HELPLESS) ? Pin.ROOTED : Pin.FREE;
    }

    /** Open to punishment while it lies helpless: its throat gland glows weak-point gold. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.HELPLESS);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return super.hurtServer(level, source, core().in(Step.HELPLESS) ? amount * HELPLESS_DAMAGE : amount);
    }

    // ---- stranded: wriggling back to the lava --------------------------------------------------------------------------

    /** On land and free: a hop towards the nearest lava every 12 ticks (in a random direction when it finds none). */
    private void strandedTick(ServerLevel level) {
        if (!onGround() || tickCount % WRIGGLE_EVERY != 0) return;
        BlockPos lava = nearestLava();
        Vec3 dir = lava == null ? new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5)
                : Vec3.atBottomCenterOf(lava).subtract(position()).multiply(1, 0, 1);
        if (dir.lengthSqr() > 1.0E-4) dir = dir.normalize();
        setDeltaMovement(dir.x * HOP_SIDE, HOP_UP, dir.z * HOP_SIDE);
        face(dir);
        playSound(ModSounds.LAVALEAPER_FLOP.get(), 0.8F, getVoicePitch());
    }

    /** The nearest lava surface within 8 blocks (lava with no solid block on it), or null. */
    public @Nullable BlockPos nearestLava() {
        BlockPos at = blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FIND_LAVA, -2, -FIND_LAVA), at.offset(FIND_LAVA, 1, FIND_LAVA))) {
            if (!level().getFluidState(pos).is(FluidTags.LAVA)) continue;
            BlockPos above = pos.above();
            if (!level().getBlockState(above).getCollisionShape(level(), above).isEmpty()) continue;
            double d = pos.distToCenterSqr(position());
            if (d < bestD) {
                bestD = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    // ---- state ---------------------------------------------------------------------------------------------------------

    /** True from a leap, a flop back or a yank until it comes down (server side). */
    public boolean airborne() {
        return airborne;
    }

    /** Ticks until it may start a tell again. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it start a tell again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Lavaleaper) && super.canAttack(target);
    }

    // ---- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() && isInLava() ? ModSounds.LAVALEAPER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.LAVALEAPER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.LAVALEAPER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + random.nextFloat() * 0.2F;
    }
}
