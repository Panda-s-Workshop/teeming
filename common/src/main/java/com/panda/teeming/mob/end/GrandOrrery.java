package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.advancement.TeemingAdvancements;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.nether.Flamewright.Patterns;
import com.panda.teeming.mob.nether.Flamewright.Patterns.Launch;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Grand Orrery: the apex of the sky round the End's outer islands, a floating clockwork of three tilted purpur rings
 * turning round a star core. A strong field mob: no boss bar and no arena, and only one in a wide stretch of sky.
 *
 * <p>It keeps its distance: 7 blocks out from its target and 3 blocks over its feet. With a target within 12 blocks of
 * its core (as far as a mote flies) in its sight it casts a pattern of star motes, each pattern after a 20-tick spin-up (the tell: its rings spin up and
 * their studs light white, a chime climbs and motes gather into the core). The patterns come in turn: a spiral of 24
 * motes over 2 s, one turn round; a double ring, two rings of 12 at once, the second slower and turned half a step;
 * and an aimed burst, three volleys of three down the same three lanes, 20 degrees apart. Each pattern is aimed so one
 * mote heads straight at its target, and its motes dip to pass through the target's chest. A mote flies straight at
 * 0.25 blocks a tick (the slow ring's at 0.15) for up to 12 blocks, stops at the first block or hitbox, and hits for
 * the orrery's attack damage as a mob's projectile (a raised shield takes it), and a mote that lands also deals
 * {@link #MOTE_PIERCE} through armour ({@code teeming:orrery_mote}, Protection still counts), all without knocking
 * anyone back, so no mote throws a player off an island.
 *
 * <p>Counter: read the lanes. Every pattern leaves lanes wider than a player 4 blocks out, wider the farther out, and a
 * player standing still in one is never touched. Payoff: after each pattern the rings part, the cage round the core
 * blooms open and the core glows weak-point gold for 3 s: armour 0, every hit doubled. The orrery sinks to its
 * target's height for it (a shifted fight: the thing that kept its distance comes down within reach), then climbs back
 * and spins up for the next pattern half a second after it closes.
 *
 * <p>Motes are not entities: the orrery moves them each tick as one {@link Shot.Volley} and draws them with
 * particles. They are not saved, and they vanish with it.
 */
public class GrandOrrery extends TeemingMonster {
    /** IDLE, the spin-up (tell), the three patterns, and the open core (the payoff). */
    public enum Step implements Phase { IDLE, SPIN, SPIRAL, DOUBLE_RING, BURST, OPEN }

    /** The order patterns are cast in; each spin-up casts the next. */
    private static final Step[] TURN = {Step.SPIRAL, Step.DOUBLE_RING, Step.BURST};

    /** The spin-up before every pattern: 1 s to read which way to step. */
    public static final int TELL_TICKS = 20;
    /** The open core: 3 s with no armour, every hit doubled. */
    public static final int OPEN_TICKS = 60;
    public static final int OPEN_ARMOR = 0;
    public static final float OPEN_DAMAGE = 2.0F;
    /** From the core closing to the next spin-up: half a second to climb back and turn to its target. */
    public static final int REST_TICKS = 10;
    /** It casts at targets this close to its core, in its sight: no farther than its motes fly. */
    public static final double CAST_RANGE = 12.0;
    /** Where it keeps station: this far out from its target across the ground, its feet this far over the target's. */
    public static final double KEEP_DISTANCE = 7.0;
    public static final double HOVER_UP = 3.0;
    public static final double KEEP_SPEED = 0.2;
    /** Open, it sinks until its feet are this far over its target's, at most this fast: down from its station in 20 t. */
    public static final double SINK_UP = 0.2;
    public static final double SINK_SPEED = 0.15;
    public static final int SINK_TICKS = 20;

    public static final int SPIRAL_COUNT = 24;
    public static final int SPIRAL_TICKS = 40;             // 24 motes over 2 s, one turn round: one 15 degrees on from the last
    public static final double SPIRAL_TURN = 360.0;
    public static final int RING_COUNT = 12;               // each of the two rings
    public static final double RING_OFFSET = 15.0;         // the second ring turned half a step: its motes in the first one's gaps
    public static final int RING_TICKS = 20;               // the release pose held after both rings
    public static final int BURST_VOLLEYS = 3;
    public static final int BURST_LANES = 3;
    public static final double BURST_SPREAD = 20.0;        // degrees between lanes
    public static final int BURST_GAP = 6;                 // ticks between volleys
    public static final int BURST_TICKS = 20;

    public static final double MOTE_SPEED = 0.25;          // blocks a tick: slow enough to read and step aside
    public static final double SLOW_RING_SPEED = 0.15;     // the second ring trails the first
    public static final double MOTE_RANGE = 12.0;          // blocks a mote flies before it fades
    public static final double MOTE_RADIUS = 0.1;          // how far from a hitbox a mote still hits it
    /**
     * What a mote that lands deals on top of its attack, through armour: with it the End reference player (netherite,
     * Protection IV) survives about 5 motes, not 37, so a pattern stood in costs something.
     */
    public static final float MOTE_PIERCE = 10.0F;
    /** The share of a mote that goes through armour. */
    public static final ResourceKey<DamageType> PIERCE = ResourceKey.create(Registries.DAMAGE_TYPE, TeemingMod.id("orrery_mote"));
    /** Motes leave the core, this high over its feet, this far out from its middle. */
    public static final double CORE_UP = 1.0;
    public static final double RELEASE_OUT = 0.6;
    /** The steepest a pattern dips (or climbs) to pass through its target. */
    public static final double MAX_PITCH = 45.0;

    /** Local cap: one within 128 blocks. */
    public static final int CAP_RADIUS = 128;
    public static final int XP = 100;
    /** Glow stages: every glow dark (the core is open: only the gold shows), at rest, and lit for the spin-up and patterns. */
    public static final int STAGE_DARK = 0, STAGE_REST = 1, STAGE_LIT = 2;

    /** The weak-point gold of the open core; star white and violet for everything else. */
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD36B, 1.2F);
    private static final DustParticleOptions STAR = new DustParticleOptions(0xF6F2FF, 1.1F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(0xB58CFF, 0.8F);

    // ---- lanes ---------------------------------------------------------------------------------------------------------

    /**
     * Flat bullet patterns aimed through a target, as plain numbers, on top of {@link Patterns}: the shapes this mob
     * adds (a ring turned part of a step, a fan of lanes fired in volleys), the dip that carries a flat pattern down
     * through a target below, and whether a player standing in a gap clears every shot. Nothing here touches the world.
     */
    public static final class Lanes {
        private Lanes() {}

        /** {@code count} shots at once, evenly round, all turned {@code offset} degrees on. */
        public static List<Launch> offsetRing(int count, double offset) {
            List<Launch> out = new ArrayList<>(count);
            for (Launch l : Patterns.ring(count)) out.add(new Launch(0, l.yaw() + offset));
            return out;
        }

        /**
         * {@code volleys} volleys {@code gap} ticks apart, each {@code lanes} shots {@code spread} degrees apart, centred on
         * the aim; every volley goes down the same lanes.
         */
        public static List<Launch> fan(int volleys, int lanes, double spread, int gap) {
            List<Launch> out = new ArrayList<>(volleys * lanes);
            for (int v = 0; v < volleys; v++) {
                for (int i = 0; i < lanes; i++) out.add(new Launch(v * gap, (i - (lanes - 1) / 2.0) * spread));
            }
            return out;
        }

        /** The direction of a shot at {@code yaw} (a mob's yaw: 0 is +Z, 90 is -X), dipped {@code pitch} degrees down. */
        public static Vec3 direction(double yaw, double pitch) {
            Vec3 flat = Patterns.direction(yaw);
            double p = Math.toRadians(pitch);
            return new Vec3(flat.x * Math.cos(p), -Math.sin(p), flat.z * Math.cos(p));
        }

        /**
         * How many degrees a shot dips to pass through a point {@code drop} blocks below where it leaves and {@code across}
         * blocks away over the ground (a negative drop: it climbs), held within {@code max} either way.
         */
        public static double pitch(double drop, double across, double max) {
            return Math.clamp(Math.toDegrees(Math.atan2(drop, Math.max(1.0E-4, across))), -max, max);
        }

        /**
         * Whether a level shot from the origin along {@code yaw} misses a square box of half-width {@code half} centred
         * {@code dx}, {@code dz} from it, with the shot's catch {@code radius} added to every side (a square for its
         * round reach, so a miss here is a miss in the world whichever way the box is turned to the shot).
         */
        public static boolean clears(double yaw, double dx, double dz, double half, double radius) {
            double r = Math.toRadians(yaw);
            double h = half + radius;
            double near = 0.0, far = Double.MAX_VALUE;
            double[] origin = {0.0, 0.0}, dir = {-Math.sin(r), Math.cos(r)}, lo = {dx - h, dz - h}, hi = {dx + h, dz + h};
            for (int axis = 0; axis < 2; axis++) {
                if (Math.abs(dir[axis]) < 1.0E-12) {
                    if (origin[axis] < lo[axis] || origin[axis] > hi[axis]) return true;
                    continue;
                }
                double a = (lo[axis] - origin[axis]) / dir[axis], b = (hi[axis] - origin[axis]) / dir[axis];
                near = Math.max(near, Math.min(a, b));
                far = Math.min(far, Math.max(a, b));
                if (near > far) return true;
            }
            return false;
        }
    }

    /**
     * The three patterns as plain numbers: when each mote leaves, which way and how fast, and how long each pattern
     * lasts. Kept apart from the mob so the shapes load and can be checked without a world.
     */
    public static final class Plan {
        private Plan() {}

        private static final List<Launch> SPIRAL = Patterns.spiral(SPIRAL_COUNT, SPIRAL_TICKS, SPIRAL_TURN);
        private static final List<Launch> DOUBLE_RING;
        private static final List<Launch> BURST = Lanes.fan(BURST_VOLLEYS, BURST_LANES, BURST_SPREAD, BURST_GAP);

        static {
            List<Launch> rings = new ArrayList<>(Patterns.ring(RING_COUNT));
            rings.addAll(Lanes.offsetRing(RING_COUNT, RING_OFFSET));
            DOUBLE_RING = List.copyOf(rings);
        }

        /** The launches of a pattern step, in the order they leave (the double ring: the fast ring, then the slow one). */
        public static List<Launch> launches(Step step) {
            return switch (step) {
                case SPIRAL -> SPIRAL;
                case DOUBLE_RING -> DOUBLE_RING;
                case BURST -> BURST;
                default -> List.of();
            };
        }

        /** How long a pattern step lasts, in ticks. */
        public static int length(Step step) {
            return switch (step) {
                case SPIRAL -> SPIRAL_TICKS;
                case DOUBLE_RING -> RING_TICKS;
                case BURST -> BURST_TICKS;
                default -> 0;
            };
        }

        /** The speed of launch {@code index} of a pattern step: the double ring's second ring is the slow one. */
        public static double speed(Step step, int index) {
            return step == Step.DOUBLE_RING && index >= RING_COUNT ? SLOW_RING_SPEED : MOTE_SPEED;
        }
    }

    // ---- state -------------------------------------------------------------------------------------------------------

    private final Shot.Volley motes = new Shot.Volley(this, MOTE_RADIUS, new Shot.Rules(this::canStrike, this::drawMote,
            this::strike, this::fade));
    /** The motes in the air, for the test hooks; the volley moves them. */
    private final List<Shot> flying = new ArrayList<>();
    private int cooldown;
    /** Which pattern the next spin-up casts, as an index into the turn; a fresh orrery starts anywhere in it. */
    private int turn;
    /** The pattern the current spin-up leads to (null outside one). */
    private @Nullable Step next;
    // the pattern being cast: which step, how many of its launches have left, the last elapsed tick seen, its aim
    private @Nullable Step casting;
    private int launched;
    private int lastElapsed;
    private float aim;
    private double pitch;
    private int fired;
    private int strikes;
    /** The pattern being watched for Between the Stars (null when none is); see {@link Watch}. */
    private @Nullable Watch watch;
    /** Test hooks: it hangs where it is (it still casts); it never starts a spin-up of its own. */
    private boolean anchored;
    private boolean holdFire;

    public GrandOrrery(EntityType<? extends GrandOrrery> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = XP;
        turn = random.nextInt(TURN.length);
        core().setStage(STAGE_REST);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("grand_orrery", 0.25)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F) {
            @Override
            public boolean canUse() {
                return core().idle() && getTarget() == null && super.canUse();
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
        return 3;
    }

    /** Its weak point, the star core, glows weak-point gold while it is open. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.OPEN);
    }

    /** Every phase sets its glow stage as it starts: dark while open (only the gold shows), lit while it casts. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.OPEN ? STAGE_DARK : phase == Step.IDLE ? STAGE_REST : STAGE_LIT);
        return true;
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private boolean casting() {
        return core().in(Step.SPIRAL) || core().in(Step.DOUBLE_RING) || core().in(Step.BURST);
    }

    // ---- facing --------------------------------------------------------------------------------------------------------

    private float yawTo(Vec3 point) {
        return (float) (Mth.atan2(point.z - getZ(), point.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private void setFacing(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** While it spins up or casts, its body faces exactly the way it aims. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    /** The middle of the star core, where motes leave from. */
    public Vec3 corePos() {
        return position().add(0.0, CORE_UP * getScale(), 0.0);
    }

    // ---- flight and the cycle ----------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        getNavigation().stop();
        if (cooldown > 0) cooldown--;
        LivingEntity target = !core().idle() && valid(core().target()) ? core().target() : getTarget();
        if (!valid(target)) target = null;
        if (anchored) {
            setDeltaMovement(Vec3.ZERO);
        } else if (core().in(Step.OPEN)) {
            sink(target);
        } else if (casting() || target == null) {
            hold();
        } else {
            keepStation(target);
        }
        if (target != null && (core().idle() || core().in(Step.SPIN))) setFacing(yawTo(target.position()));
        LivingEntity hunted = getTarget();
        if (core().idle() && cooldown == 0 && !holdFire && valid(hunted) && corePos().distanceTo(hunted.position()) <= CAST_RANGE
                && getSensing().hasLineOfSight(hunted)) {
            spinUp(hunted);
        }
    }

    /**
     * Starts the spin-up at {@code target} (null: the way it faces), leading to the next pattern in turn. Ignores the
     * wait and the range. True if it started. Public as a test hook.
     */
    public boolean spinUp(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel) || !core().idle() || isDeadOrDying()) return false;
        if (!startPhase(Step.SPIN, TELL_TICKS, target)) return false;
        next = TURN[turn];
        turn = (turn + 1) % TURN.length;
        if (valid(target)) setFacing(yawTo(target.position()));
        playSound(ModSounds.GRAND_ORRERY_SPIN.get(), 1.6F, 1.0F);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) return;
        LivingEntity target = valid(core().target()) ? core().target() : null;
        if (ended == Step.SPIN) {
            Step pattern = next;
            next = null;
            if (pattern == null || !startPhase(pattern, Plan.length(pattern), target)) settle();
        } else if (ended == Step.SPIRAL || ended == Step.DOUBLE_RING || ended == Step.BURST) {
            if (startPhase(Step.OPEN, OPEN_TICKS, target)) opened(level);
            else settle();
        } else if (ended == Step.OPEN) {
            closed(level);
        } else {
            settle();
        }
    }

    /** Back to rest: idle, the core lit star-white, the next spin-up half a second off. */
    private void settle() {
        next = null;
        core().end();
        core().setStage(STAGE_REST);
        cooldown = REST_TICKS;
    }

    /** Holds where it is, easing to a stop. */
    private void hold() {
        setDeltaMovement(getDeltaMovement().scale(0.5));
    }

    /**
     * Its station round {@code target}: {@link #KEEP_DISTANCE} out across the ground on the side it is on (or up to 90
     * degrees round either way when that spot is shut), its feet {@link #HOVER_UP} over the target's. Null when every
     * one is shut. Public as a test hook.
     */
    public @Nullable Vec3 station(LivingEntity target) {
        Vec3 away = position().subtract(target.position()).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = Patterns.direction(getYRot()).scale(-1.0);
        away = away.normalize();
        for (float turnBy : new float[] {0, 30, -30, 60, -60, 90, -90}) {
            Vec3 flat = target.position().add(away.yRot(turnBy * Mth.DEG_TO_RAD).scale(KEEP_DISTANCE));
            Vec3 spot = new Vec3(flat.x, target.getY() + HOVER_UP, flat.z);
            if (open(spot)) return spot;
        }
        return null;
    }

    private void keepStation(LivingEntity target) {
        Vec3 spot = station(target);
        if (spot == null || spot.distanceToSqr(position()) < 0.04) hold();
        else steer(spot, KEEP_SPEED, 0.15);
    }

    /** Open, it sinks straight down until its feet are {@link #SINK_UP} over its target's; with no target it holds. */
    private void sink(@Nullable LivingEntity target) {
        if (target == null) {
            hold();
            return;
        }
        double dy = Mth.clamp(target.getY() + SINK_UP - getY(), -SINK_SPEED, SINK_SPEED);
        setDeltaMovement(0.0, dy, 0.0);
    }

    /** Whether it fits at {@code spot} (its feet there) and has a clear line to it. */
    private boolean open(Vec3 spot) {
        AABB there = getBoundingBox().move(spot.subtract(position()));
        if (!level().noCollision(this, there)) return false;
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = spot.add(0, getBbHeight() * 0.5, 0);
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this)).getType() == HitResult.Type.MISS;
    }

    /** Eases its flight toward {@code want} (its feet there) at up to {@code speed} blocks a tick. */
    private void steer(Vec3 want, double speed, double ease) {
        Vec3 to = want.subtract(position());
        if (to.length() > speed) to = to.normalize().scale(speed);
        setDeltaMovement(getDeltaMovement().lerp(to, ease));
    }

    // ---- the spin-up, the patterns, the open core --------------------------------------------------------------------

    /** Runs on the AI's and a frozen statue's ticks alike, so the spin-up, the motes and the open core always show. */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (core().in(Step.SPIN)) spinTick(level);
        else if (core().in(Step.OPEN) && core().elapsed() % 3 == 0) {
            Vec3 c = corePos();                                                   // gold motes drift off the open core
            level.sendParticles(GOLD, true, true, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.0);
        }
        castTick(level);
        motes.tick(level);
        watchTick();
    }

    /** The spin-up: motes stream in to the core from all round, and the chime climbs. */
    private void spinTick(ServerLevel level) {
        int t = core().elapsed();
        Vec3 c = corePos();
        double s = getScale();
        for (int k = 0; k < 2; k++) {
            double a = random.nextDouble() * Math.PI * 2.0, up = random.nextDouble() * 2.0 - 1.0;
            double flat = Math.sqrt(1.0 - up * up);
            Vec3 dir = new Vec3(Math.cos(a) * flat, up, Math.sin(a) * flat);
            Vec3 from = c.add(dir.scale(2.2 * s));
            // a count of 0 sends one particle with (dx, dy, dz) x speed as its motion: inward, to the core
            level.sendParticles(ParticleTypes.END_ROD, true, true, from.x, from.y, from.z, 0, -dir.x, -dir.y, -dir.z, 0.19 * s);
        }
        if (t % 2 == 0) level.sendParticles(STAR, c.x, c.y, c.z, 2, 0.4 * s, 0.4 * s, 0.4 * s, 0.0);
        if (t % 4 == 0) playSound(ModSounds.GRAND_ORRERY_CHIME.get(), 1.4F, 0.6F + 0.9F * t / TELL_TICKS);
    }

    /**
     * Casts the current pattern: every launch whose tick has come leaves now, at the aim and dip taken as the pattern
     * began. A pattern started outside its own tick (an event, a command) still lets its first launches go on its next
     * tick. A dying orrery looses nothing more; motes already in the air fly on.
     */
    private void castTick(ServerLevel level) {
        Step now = core().phase() instanceof Step s ? s : Step.IDLE;
        List<Launch> plan = Plan.launches(now);
        if (plan.isEmpty() || isDeadOrDying()) {
            casting = null;
            return;
        }
        int t = core().elapsed();
        if (casting != now || t < lastElapsed) {
            casting = now;
            launched = 0;
            LivingEntity target = core().target();
            aim = valid(target) ? yawTo(target.position()) : getYRot();
            pitch = valid(target) ? pitchTo(target) : 0.0;
            setFacing(aim);
            playSound(ModSounds.GRAND_ORRERY_RELEASE.get(), 1.4F, 1.0F);
            watch = target instanceof ServerPlayer player && inReach(player) ? new Watch(player) : null;
        }
        lastElapsed = t;
        while (launched < plan.size() && plan.get(launched).tick() <= t) {
            int index = launched++;
            if (MechanicEvent.post(this, "mote", core().target())) {
                Shot mote = launch(level, aim + plan.get(index).yaw(), Plan.speed(now, index));
                if (watch != null) {
                    watch.left.add(mote);
                    watch.fired++;
                }
            }
        }
        if (watch != null && launched >= plan.size()) watch.allOut = true;
    }

    /**
     * Between the Stars: the player a pattern is aimed at earns the advancement once every mote of that pattern has
     * left and faded without one touching them (a mote a shield takes still touched them), if they stayed in its reach
     * from the moment it began until its last mote was spent: within a mote's range of the core and in the orrery's
     * sight every tick, so stepping out of range or behind cover, even for a moment, earns nothing. At least one mote
     * must actually have flown: a pattern whose every mote a listener cancelled earns nothing. A pattern cut short (the
     * orrery dies before its last mote leaves, or is removed before its motes are spent) earns nothing.
     */
    private static final class Watch {
        final ServerPlayer player;
        final Set<Shot> left = Collections.newSetFromMap(new IdentityHashMap<>());
        int fired;
        boolean allOut;
        boolean touched;
        boolean strayed;

        Watch(ServerPlayer player) {
            this.player = player;
        }
    }

    /** Resolves the watched pattern once its last mote is gone. */
    private void watchTick() {
        if (watch == null) return;
        if (!inReach(watch.player)) watch.strayed = true;
        if (!watch.allOut || !watch.left.isEmpty()) return;
        Watch done = watch;
        watch = null;
        if (!done.touched && !done.strayed && done.fired > 0) {
            TeemingAdvancements.award(done.player, TeemingAdvancements.GRAND_ORRERY_PATTERN);
        }
    }

    /** In a pattern's reach: a valid target within a mote's range of the core, with nothing blocking the orrery's sight of it. */
    private boolean inReach(ServerPlayer player) {
        return valid(player) && corePos().distanceTo(player.position()) <= MOTE_RANGE && hasLineOfSight(player);
    }

    /** How far a pattern dips to pass from its core through the middle of {@code target}. */
    private double pitchTo(LivingEntity target) {
        Vec3 c = corePos();
        double across = Math.hypot(target.getX() - c.x, target.getZ() - c.z);
        return Lanes.pitch(c.y - (target.getY() + target.getBbHeight() * 0.5), across, MAX_PITCH);
    }

    /** One mote from the core, at {@code yaw}, dipped by the pattern's pitch. */
    private Shot launch(ServerLevel level, double yaw, double speed) {
        Vec3 dir = Lanes.direction(yaw, pitch);
        Vec3 from = corePos().add(Patterns.direction(yaw).scale(RELEASE_OUT * getScale()));
        Shot mote = motes.fire(Shot.Path.line(from, dir, speed, MOTE_RANGE));
        flying.add(mote);
        fired++;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, from.x, from.y, from.z, 2, 0.05, 0.05, 0.05, 0.0);
        return mote;
    }

    /** The core opens: its cage blooms, a deep bell, and a burst of gold from the core. */
    private void opened(ServerLevel level) {
        Vec3 c = corePos();
        level.sendParticles(GOLD, true, true, c.x, c.y, c.z, 16, 0.4, 0.4, 0.4, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, true, true, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.04);
        playSound(ModSounds.GRAND_ORRERY_OPEN.get(), 1.8F, 1.0F);
    }

    /** The core closes again: the cage folds shut over it with a chain's clink, and it settles to rest. */
    private void closed(ServerLevel level) {
        settle();
        Vec3 c = corePos();
        level.sendParticles(ParticleTypes.END_ROD, true, true, c.x, c.y, c.z, 8, 0.4, 0.4, 0.4, 0.02);
        playSound(ModSounds.GRAND_ORRERY_CLOSE.get(), 1.4F, 1.0F);
    }

    // ---- motes -------------------------------------------------------------------------------------------------------

    private boolean canStrike(LivingEntity e) {
        return e != this && !(e instanceof GrandOrrery) && e.isAlive() && e.canBeHitByProjectile() && !e.isSpectator();
    }

    /**
     * A mote: a star-white spark at its head with a white glint, and a violet trail where it was half a tick ago. The
     * sparks glow by themselves and last two or three ticks, so a mote shows in the End's dark and leaves no line
     * along its path.
     */
    private void drawMote(ServerLevel level, Vec3 from, Vec3 to) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, to.x, to.y, to.z, 2, 0.03, 0.03, 0.03, 0.0);
        level.sendParticles(STAR, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
        Vec3 mid = from.add(to).scale(0.5);
        level.sendParticles(VIOLET, mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** The piercing share's source: the orrery's own damage type, caused by the orrery. */
    private DamageSource pierce(ServerLevel level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PIERCE), this);
    }

    /**
     * A mote reaches a hitbox: the orrery's attack damage as a mob's projectile (a shield raised toward the orrery takes
     * it), then, if that landed, {@link #MOTE_PIERCE} through armour; both with no knockback, so a mote never throws
     * anyone off an island's edge. A mote the shield took, or the last hit's cooldown swallowed, pierces nothing.
     */
    private void strike(ServerLevel level, Shot shot, LivingEntity victim, Vec3 at) {
        flying.remove(shot);
        if (watch != null && watch.left.remove(shot) && victim == watch.player) watch.touched = true;
        if (MechanicEvent.post(this, "mote_hit", victim)) {
            float before = victim.getHealth() + victim.getAbsorptionAmount();
            AstralAugur.GroundMarks.hurtInPlace(level, victim, Shot.damage(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
            if (victim.isAlive() && victim.getHealth() + victim.getAbsorptionAmount() < before) {
                victim.damageCooldownTime = 0;                  // the piercing share is the same mote: the hit just dealt must not eat it
                AstralAugur.GroundMarks.hurtInPlace(level, victim, pierce(level), MOTE_PIERCE);
            }
            strikes++;
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.0);
        level.sendParticles(VIOLET, at.x, at.y, at.z, 4, 0.12, 0.12, 0.12, 0.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.GRAND_ORRERY_STRIKE.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** A mote meets a wall or flies its length: it fades in a few sparks where it stopped. */
    private void fade(ServerLevel level, Shot shot, Vec3 at, boolean blocked) {
        flying.remove(shot);
        if (watch != null) watch.left.remove(shot);
        level.sendParticles(VIOLET, at.x, at.y, at.z, 3, 0.08, 0.08, 0.08, 0.0);
        if (blocked && random.nextInt(4) == 0) {
            level.playSound(null, at.x, at.y, at.z, ModSounds.GRAND_ORRERY_FIZZLE.get(), getSoundSource(), 0.5F, 1.0F);
        }
    }

    // ---- the open core -----------------------------------------------------------------------------------------------

    /** Open, it has no armour. */
    @Override
    public int getArmorValue() {
        return core().in(Step.OPEN) ? OPEN_ARMOR : super.getArmorValue();
    }

    /** Open, every hit is doubled, and one that lands rings off the core in a burst of gold. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean open = core().in(Step.OPEN);
        boolean hurt = super.hurtServer(level, source, open ? amount * OPEN_DAMAGE : amount);
        if (hurt && open) {
            Vec3 c = corePos();
            level.sendParticles(GOLD, true, true, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, c.x, c.y, c.z, ModSounds.GRAND_ORRERY_CORE_HIT.get(), getSoundSource(), 1.2F, 1.0F);
        }
        return hurt;
    }

    /** A dead orrery finishes nothing: the pattern it was casting stops, though motes in the air fly on. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) {
            next = null;
            core().end();
        }
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        motes.clear();
        flying.clear();
        watch = null;
        super.onRemoval(reason);
    }

    /** A floating clockwork never takes fall damage. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- state and test hooks ----------------------------------------------------------------------------------------

    /** The motes in the air now. */
    public List<Shot> motes() {
        return List.copyOf(flying);
    }

    /** How many motes it has loosed so far. */
    public int fired() {
        return fired;
    }

    /** How many of its motes have struck something. */
    public int strikes() {
        return strikes;
    }

    /** The pattern its next spin-up will cast. */
    public Step nextPattern() {
        return TURN[turn];
    }

    /** Sets the pattern its next spin-up will cast (SPIRAL, DOUBLE_RING or BURST). Public as a test hook. */
    public void setNextPattern(Step pattern) {
        for (int i = 0; i < TURN.length; i++) if (TURN[i] == pattern) turn = i;
    }

    /** The way the current pattern is aimed, as a yaw, and how far it dips, in degrees. */
    public float aim() {
        return aim;
    }

    public double pitch() {
        return pitch;
    }

    /** Ticks until it may spin up again. */
    public int cooldown() {
        return cooldown;
    }

    /** Holds it where it is (it still casts, and aims): a test hook. */
    public void anchor(boolean anchored) {
        this.anchored = anchored;
    }

    /** Keeps it from starting a spin-up of its own (a hook can still start one): a test hook. */
    public void holdFire(boolean hold) {
        this.holdFire = hold;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Turn", turn);
        output.putInt("Cooldown", cooldown);
    }

    /** A reload ends a spin-up, a pattern or an open core: it comes back at rest, its place in the turn kept. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        turn = Math.floorMod(input.getIntOr("Turn", turn), TURN.length);
        cooldown = Math.max(0, input.getIntOr("Cooldown", 0));
        core().setStage(STAGE_REST);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.GRAND_ORRERY_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GRAND_ORRERY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GRAND_ORRERY_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.4F;
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + random.nextFloat() * 0.1F;
    }
}
