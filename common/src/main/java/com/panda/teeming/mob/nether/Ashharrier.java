package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Relative;
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
 * Ashharrier: a broad-winged bird of prey of the basalt deltas, ash-grey with ember-edged feathers. With prey in sight
 * and open sky over it, it climbs and circles 5 blocks out, 7 to 10 blocks above its target. Once it is 5 to 14 blocks
 * above it, it stops in the air and cries for 16 ticks (tell): beak wide, wings folding back, shedding ash and embers.
 * Then it stoops: a falling arc that steers onto the target as it drops. If the stoop connects it grabs its prey in its
 * talons (the prey shows as held) and climbs straight up with it, 8 to 12 blocks over 40 ticks, then lets go. The fall
 * is what hurts. Prey too big to carry is raked by its talons for its attack damage instead.
 *
 * <p>Counters: a hit on it while it carries someone makes it let go at once, so a victim who strikes early falls from
 * low and lands safe. A shield raised toward the stoop blocks the grab: it crashes into it and lies stunned on the
 * ground for 40 ticks (its mantle glows weak-point gold, open to blows at their face value), then climbs away. After
 * every stoop it circles at least 5 s before it cries again. Under a roof, with no room to lift, it fights with its
 * talons.
 *
 * <p>It never takes a player in creative or spectator, nor prey another holder already has. It leaves its victim's own
 * protection alone: it holds them without hurting them and lets go without pushing them down, so Slow Falling lands a
 * dropped victim safe and an elytra can be opened on the way down. Fall damage is the vanilla rule.
 *
 * <p>The stoop is an arc flight in its own section (a scripted ballistic flight from where it is to a spot, the shape
 * the Lavaleaper leaps in), here steered towards the moving target; the carry uses the kit's {@link Grab}.
 */
public class Ashharrier extends TeemingMonster {
    /** IDLE, circling over its prey, the cry (tell), the stoop, the lift with prey, stunned by a shield, climbing away. */
    public enum Step implements Phase { IDLE, CIRCLE, TELL, DIVE, LIFT, STUNNED, CLIMB }

    /** The cry: it hangs in the air for 0.8 s before it stoops. */
    public static final int TELL_TICKS = 16;
    /** It lifts its prey between 8 and 12 blocks (picked for each lift), over 40 ticks. */
    public static final double LIFT_MIN = 8.0;
    public static final double LIFT_MAX = 12.0;
    public static final int LIFT_TICKS = 40;
    /** A shield raised into the stoop stuns it on the ground for 2 s. */
    public static final int STUN_TICKS = 40;
    /** It cries only from this band of heights above its target, in blocks. */
    public static final double MIN_HEIGHT = 5.0;
    public static final double MAX_HEIGHT = 14.0;
    /** It stoops only at a target at most this far away across the ground. */
    public static final double DIVE_REACH = 8.0;
    /** Where it circles: a ring this far out, at a height picked from this band each time it starts circling. */
    public static final double CIRCLE_RADIUS = 5.0;
    public static final double CRUISE_MIN = 7.0;
    public static final double CRUISE_MAX = 10.0;
    /** Its fastest speeds while circling, across and up or down, in blocks a tick. */
    public static final double CIRCLE_SPEED = 0.35;
    public static final double CLIMB_SPEED = 0.35;
    /** The stoop's pull, in blocks a tick per tick: a drop from 8 up takes 14 ticks and ends at 1.1 b/t. */
    public static final double GRAVITY = 0.08;
    /** How much it may change its speed across, per tick of the stoop, to follow a target that moves. */
    public static final double DIVE_STEER = 0.05;
    /** A stoop ends after this many ticks at most, wherever it is. */
    public static final int DIVE_TICKS = 40;
    /** The stoop takes whoever comes within this many blocks of its hitbox. */
    public static final double GRAB_REACH = 0.3;
    /** Prey wider or taller than this is too big to carry: it is raked for the attack damage instead. */
    public static final float MAX_PREY_WIDTH = 1.0F, MAX_PREY_HEIGHT = 2.0F;
    /** A victim found this far from its talons (an ender pearl, a teleport) has got away. */
    public static final double ESCAPE_RANGE = 4.0;
    /** The victim's head is held this far up inside its talons and belly. */
    public static final double GRIP = 0.3;
    /** Open sky it needs over its target before it circles: room to lift prey 12 blocks and fly over them. */
    public static final int ROOM = 15;
    /** Climbing away after a stoop (or a stun). */
    public static final int CLIMB_TICKS = 20;
    /** Ticks from the end of one stoop until it may cry again. */
    public static final int COOLDOWN = 100;

    /** Glow stages: embers dark (stunned; the base texture's unlit edges), embers at rest, embers flaring. */
    private static final int GLOW_DARK = 0, GLOW_REST = 1, GLOW_HOT = 2;
    private static final DustParticleOptions ASH = new DustParticleOptions(0x8E8884, 1.6F);
    private static final DustParticleOptions STAR = new DustParticleOptions(0xFFD36B, 1.1F);
    private static final int STARS = 4;
    private static final Telegraph CRY = Telegraph.particles(ASH, 14, 0.7, 0.5).withSound(ModSounds.ASHHARRIER_CRY.get(), 2.0F, 1.0F);
    private static final Telegraph CRASH = Telegraph.particles(ParticleTypes.CRIT, 18, 0.6, 0.5).withSpeed(0.25)
            .withSound(ModSounds.ASHHARRIER_STUNNED.get(), 1.0F, 1.0F);

    private final Grab grab = new Grab(MAX_PREY_WIDTH, MAX_PREY_HEIGHT, ESCAPE_RANGE);
    private int cooldown;
    private double cruise = CRUISE_MIN;
    private double orbit;
    /** The stoop's velocity, server side: set at the start, steered and pulled down each tick. */
    private Vec3 flight = Vec3.ZERO;
    /** The lift: where the victim's feet started, and how high this lift takes them. */
    private double liftFrom;
    private double lift = LIFT_MIN;

    public Ashharrier(EntityType<? extends Ashharrier> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 10;
        core().setStage(GLOW_REST);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("ashharrier", 0.3).add(Attributes.FLYING_SPEED, 0.5);
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
        // talons, only when it has no room to lift (its own phases drive it otherwise)
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

    @Override
    public int visualStages() {
        return 3;
    }

    /**
     * It flies; while it stoops or lifts it follows its own scripted motion (no drag, no steering by goals); stunned,
     * it falls like anything else.
     */
    @Override
    public void travel(Vec3 input) {
        if (core().in(Step.STUNNED)) super.travel(input);
        else if (core().in(Step.DIVE) || core().in(Step.LIFT)) move(MoverType.SELF, getDeltaMovement());
        else travelFlying(input, getSpeed());
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.STUNNED);
    }

    /** Every phase it enters sets its glow: dark while stunned, flaring through the cry, the stoop and the lift. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(glowStage());
        return true;
    }

    /** Back to idle, embers at rest. */
    private void rest() {
        core().end();
        core().setStage(GLOW_REST);
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
        } else if (core().in(Step.LIFT)) {
            liftTick(level);
        } else if (core().in(Step.CLIMB)) {
            getNavigation().stop();
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * 0.9, CLIMB_SPEED, v.z * 0.9);
            face(new Vec3(v.x, 0, v.z), -15.0F);
        } else if (core().in(Step.TELL)) {
            tellTick(level, core().target());
        } else if (core().in(Step.CIRCLE)) {
            if (!valid(target) || !roomAbove(level, target)) {
                rest();
                return;
            }
            circleTick(target);
            if (canCry(target)) cry(target);
        } else if (valid(target) && roomAbove(level, target)) {
            circle(target);
        }
    }

    /** Starts circling over {@code target}: a height picked from 7 to 10 above it, and its place on the ring. */
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

    private boolean canCry(LivingEntity target) {
        if (cooldown > 0) return false;
        double height = getY() - target.getY();
        double across = position().subtract(target.position()).horizontalDistance();
        return height >= MIN_HEIGHT && height <= MAX_HEIGHT && across <= DIVE_REACH && getSensing().hasLineOfSight(target);
    }

    /**
     * Starts the cry at {@code target}: it hangs in the air for 16 ticks, then stoops. True if it started. Ignores the
     * cooldown, the height and the reach. Public as a test hook.
     */
    public boolean cry(@Nullable LivingEntity target) {
        if (level().isClientSide() || !(core().idle() || core().in(Step.CIRCLE))) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        getNavigation().stop();
        CRY.play(this);
        if (level() instanceof ServerLevel level) embers(level, 6);
        return true;
    }

    /** The cry: it hangs in the air, nose toward the target, folding its wings and shedding ash and embers. */
    private void tellTick(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().scale(0.5));
        if (target != null) {
            Vec3 d = target.position().subtract(position());
            face(d, (float) Math.min(35.0, pitchOf(d) * 0.5));
        }
        int t = core().elapsed();
        if (t == TELL_TICKS / 2) CRY.play(this);
        if (t % 2 == 0) {
            level.sendParticles(ASH, true, true, getX(), getY() + getBbHeight() * 0.6, getZ(), 3, 0.8, 0.2, 0.8, 0.0);
            level.sendParticles(ParticleTypes.WHITE_ASH, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.9, 0.3, 0.9, 0.02);
        }
        if (t % 3 == 0) embers(level, 2);
    }

    /** Sparks shed from its ember-edged wings. */
    private void embers(ServerLevel level, int count) {
        level.sendParticles(ParticleTypes.SMALL_FLAME, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), count, 0.9, 0.15, 0.9, 0.01);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (isNoAi() || !(level() instanceof ServerLevel level)) {
            rest();
        } else if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (valid(target)) stoop(level, target);
            else climb();
        } else if (ended == Step.DIVE) {
            climb();                                             // the stoop ran out in the air
        } else if (ended == Step.LIFT) {
            letGo(level, true);
        } else if (ended == Step.STUNNED) {
            climb();
        } else if (ended == Step.CLIMB) {
            LivingEntity target = getTarget();
            if (valid(target) && roomAbove(level, target)) circle(target);
            else rest();
        } else {
            rest();
        }
    }

    /** Climbs away after a stoop (landed, missed, blocked); the wait for the next cry starts here. */
    private void climb() {
        cooldown = COOLDOWN;
        if (!startPhase(Step.CLIMB, CLIMB_TICKS, getTarget())) rest();
    }

    // ---- the stoop: an arc flight ------------------------------------------------------------------------------------
    // Self-contained: a scripted ballistic flight from where it is towards a spot, pulled down by GRAVITY each tick,
    // with its collisions on. Each tick the speed across is steered (by at most DIVE_STEER) to land on where the target
    // is now, given the time the fall still takes. It ends on contact, on the ground, or after DIVE_TICKS.

    /** The start velocity that carries it from {@code from} to {@code to} in an arc that leaves upward at {@code up}. */
    public static Vec3 arc(Vec3 from, Vec3 to, double up) {
        double dy = to.y - from.y;
        double climb = Math.max(up, Math.sqrt(2.0 * GRAVITY * Math.max(0.0, dy + 0.3)));   // always clears the spot
        double ticks = (climb + Math.sqrt(Math.max(0.0, climb * climb - 2.0 * GRAVITY * dy))) / GRAVITY;
        Vec3 across = to.subtract(from).multiply(1, 0, 1);
        return new Vec3(across.x / ticks, climb, across.z / ticks);
    }

    /** Ticks a fall that is moving up at {@code vy} now takes to drop {@code dy} (negative: below), or NaN never. */
    public static double fallTicks(double vy, double dy) {
        double root = vy * vy - 2.0 * GRAVITY * dy;
        if (root < 0.0) return Double.NaN;
        double ticks = (vy + Math.sqrt(root)) / GRAVITY;
        return ticks > 0.0 ? ticks : Double.NaN;
    }

    /** Where its feet aim on {@code target}: over its shoulders, where the talons close. */
    private static Vec3 aimAt(LivingEntity target) {
        return target.position().add(0, target.getBbHeight() * 0.6, 0);
    }

    /** The stoop at {@code target}: wings folded, it drops in an arc towards where the target stands. */
    private void stoop(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.DIVE, DIVE_TICKS, target)) {
            climb();
            return;
        }
        flight = arc(position(), aimAt(target), 0.0);
        setDeltaMovement(Vec3.ZERO);
        face(flight, 60.0F);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.ASHHARRIER_DIVE.get(), getSoundSource(), 2.0F, 1.0F);
    }

    /** Each tick of the stoop: contact, the ground, or the next step of the arc, steered onto the target. */
    private void diveTick(ServerLevel level) {
        getNavigation().stop();
        resetFallDistance();
        LivingEntity target = core().target();
        Vec3 step = flight;
        AABB reach = getBoundingBox().inflate(GRAB_REACH).expandTowards(step);
        if (valid(target) && reach.intersects(target.getBoundingBox())) {
            strike(level, target);
            return;
        }
        if (core().elapsed() > 1 && step.y < 0.0 && (onGround() || verticalCollision || horizontalCollision)) {
            climb();                                             // missed: it pulls up off the ground
            return;
        }
        Vec3 across = new Vec3(step.x, 0, step.z);
        if (valid(target)) {
            Vec3 aim = aimAt(target);
            double ticks = fallTicks(step.y, aim.y - getY());
            if (!Double.isNaN(ticks)) {
                Vec3 want = aim.subtract(position()).multiply(1, 0, 1).scale(1.0 / Math.max(1.0, ticks));
                Vec3 change = want.subtract(across);
                if (change.length() > DIVE_STEER) change = change.normalize().scale(DIVE_STEER);
                across = across.add(change);
            }
        }
        setDeltaMovement(across.x, step.y, across.z);
        face(getDeltaMovement(), (float) Mth.clamp(pitchOf(getDeltaMovement()), 20.0, 85.0));
        flight = new Vec3(across.x, Math.max(-1.8, step.y - GRAVITY), across.z);
        if (core().elapsed() % 2 == 0) level.sendParticles(ASH, true, true, getX(), getY() + getBbHeight() * 0.7, getZ(), 1, 0.2, 0.2, 0.2, 0.0);
    }

    /**
     * The stoop reaches {@code victim}: a shield raised toward it stuns it; prey it can carry is taken; anything too big
     * to carry is raked by its talons for its attack damage.
     */
    private void strike(ServerLevel level, LivingEntity victim) {
        float attack = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (victim.applyItemBlocking(level, damageSources().mobAttack(this), attack) > 0.0F) {
            stun(level);
            return;
        }
        if (canLift(victim)) {
            if (!grab(victim)) climb();
            return;
        }
        if (!Grab.isHeld(victim) && MechanicEvent.post(this, "rake", victim)) {
            victim.hurtServer(level, damageSources().mobAttack(this), attack);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASHHARRIER_GRAB.get(), getSoundSource(), 1.0F, 1.3F);
        }
        climb();
    }

    /** A shield took the stoop: it crashes instead of being knocked back. */
    @Override
    protected void blockedByItem(LivingEntity defender, DamageSource source, float blocked, boolean fullBlock) {
        if (!core().in(Step.DIVE)) super.blockedByItem(defender, source, blocked, fullBlock);
    }

    /** The counter landed: it crashes into the shield and drops to the ground stunned. Public as a test hook. */
    public void stun(ServerLevel level) {
        if (!startPhase(Step.STUNNED, STUN_TICKS, null)) {
            climb();
            return;
        }
        setNoGravity(false);
        Vec3 back = flight.multiply(-0.3, 0, -0.3);
        setDeltaMovement(back.x, 0.1, back.z);
        setXRot(0.0F);
        CRASH.play(this);
        level.sendParticles(ASH, true, true, getX(), getY() + 0.4, getZ(), 16, 0.6, 0.3, 0.6, 0.0);
    }

    /** Gold stars circling over its head while it is stunned, one lap a second. */
    private void circleStars(ServerLevel level) {
        if (tickCount % 2 != 0) return;
        Vec3 head = position().add(getLookAngle().multiply(1, 0, 1).scale(getBbWidth() * 0.4)).add(0, getBbHeight() + 0.25, 0);
        double turn = core().elapsed() * (Math.PI * 2 / 20);
        for (int i = 0; i < STARS; i++) {
            double a = turn + i * (Math.PI * 2 / STARS);
            level.sendParticles(STAR, head.x + Math.cos(a) * 0.6, head.y, head.z + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
        }
    }

    // ---- the carry: lifting a victim -----------------------------------------------------------------------------------
    // The victim hangs under it, its head held up in the talons; each tick, after its own move, the victim is put back
    // there, its motion zeroed and its fall forgotten, so the fall counts only from where it lets go. The Grab says
    // whether it still holds them (alive, here, not gone creative, not pearled away).

    /** Prey it can carry: it fits in its talons, isn't a player in creative or spectator, and no one else holds it. */
    public boolean canLift(@Nullable LivingEntity e) {
        return !(e instanceof Ashharrier) && grab.fits(this, e) && !Grab.isHeld(e);
    }

    /** One lift's height: 8 to 12 blocks, evenly. */
    public static double rollLift(RandomSource random) {
        return LIFT_MIN + random.nextDouble() * (LIFT_MAX - LIFT_MIN);
    }

    /** Takes {@code prey} at once (without the cry and the stoop) and starts the lift; true if it did. Public as a test hook. */
    public boolean grab(@Nullable LivingEntity prey) {
        if (level().isClientSide() || !canLift(prey) || core().in(Step.LIFT)) return false;
        if (!startPhase(Step.LIFT, LIFT_TICKS, prey)) return false;
        grab.take(prey);
        liftFrom = prey.getY();
        lift = rollLift(random);
        flight = Vec3.ZERO;
        setDeltaMovement(Vec3.ZERO);
        setXRot(0.0F);
        setPos(prey.getX(), prey.getY() + prey.getBbHeight() - GRIP, prey.getZ());   // over its shoulders
        hold(prey, true);
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASHHARRIER_GRAB.get(), getSoundSource(), 1.2F, 1.0F);
            level.sendParticles(ASH, true, true, getX(), getY(), getZ(), 20, 0.6, 0.4, 0.6, 0.0);
            embers(level, 8);
        }
        return true;
    }

    /** Where the victim's feet hang: under it, the head held {@link #GRIP} up in its talons. */
    private Vec3 anchor(LivingEntity v) {
        return position().add(0, GRIP - v.getBbHeight(), 0);
    }

    /** Climbing with the victim: an even rise to the lift's height over the lift's length, straight up. */
    private void liftTick(ServerLevel level) {
        getNavigation().stop();
        resetFallDistance();
        LivingEntity v = grab.victim();
        if (v == null || !grab.holding(this, anchor(v))) {
            letGo(level, false);
            return;
        }
        if (core().elapsed() > 0 && verticalCollision) {         // a ceiling over it: it lets go where it is
            letGo(level, true);
            return;
        }
        double share = Math.min(1.0, (core().elapsed() + 1) / (double) LIFT_TICKS);
        double wantY = liftFrom + lift * share + v.getBbHeight() - GRIP;
        setDeltaMovement(0.0, Mth.clamp(wantY - getY(), 0.0, 1.0), 0.0);
        if (core().elapsed() % 3 == 0) level.sendParticles(ASH, true, true, getX(), getY() + 0.3, getZ(), 2, 0.6, 0.1, 0.6, 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) return;
        // after its own move this tick, so the victim is where its talons ended up
        LivingEntity v = grab.victim();
        if (v != null && core().in(Step.LIFT) && !isNoAi() && grab.holding(this, anchor(v))) hold(v, false);
    }

    /** Embers at rest; flaring through the cry, the stoop and the lift; dark while it lies stunned. */
    private int glowStage() {
        if (core().in(Step.TELL) || core().in(Step.DIVE) || core().in(Step.LIFT)) return GLOW_HOT;
        if (core().in(Step.STUNNED)) return GLOW_DARK;
        return GLOW_REST;
    }

    /** Puts the victim back under its talons if it drifted, holds it still there and forgets its fall so far. */
    private void hold(LivingEntity v, boolean always) {
        Vec3 at = anchor(v);
        v.resetFallDistance();
        if (v.isPassenger()) v.stopRiding();
        if (always || v.position().distanceToSqr(at) > 0.0025) {
            if (v instanceof ServerPlayer p && level() instanceof ServerLevel level) {
                p.teleportTo(level, at.x, at.y, at.z, Relative.ROTATION, 0.0F, 0.0F, false);   // they still look and swing where they like
            } else {
                v.teleportTo(at.x, at.y, at.z);
            }
        }
        v.setDeltaMovement(Vec3.ZERO);
        if (v instanceof Mob m) m.getNavigation().stop();
    }

    /**
     * Lets go of the victim, if any, from where it hangs now; it falls from rest, nothing pushed on it. Then it climbs
     * away. {@code place}: put the victim at its talons first (the lift's last tick, before its own tick has run).
     */
    private @Nullable LivingEntity letGo(ServerLevel level, boolean place) {
        LivingEntity v = grab.victim();
        if (v != null && place && grab.holding(this, anchor(v))) hold(v, true);
        v = grab.release();
        if (v != null && v.isAlive() && !v.isRemoved()) {
            Push.set(v, Vec3.ZERO);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASHHARRIER_DROP.get(), getSoundSource(), 1.5F, 1.0F);
            level.sendParticles(ASH, true, true, getX(), getY(), getZ(), 12, 0.5, 0.2, 0.5, 0.0);
        }
        if (core().in(Step.LIFT)) climb();
        return v;
    }

    /** A hit that lands while it carries someone makes it let go at once: the earlier, the lower the fall. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && core().in(Step.LIFT) && source.getEntity() != null && grab.victim() != null
                && MechanicEvent.post(this, "drop", grab.victim())) {
            letGo(level, false);
        }
        return hurt;
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
        LivingEntity v = grab.release();
        if (v != null && v.isAlive()) Push.set(v, Vec3.ZERO);
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        grab.release();
        super.onRemoval(reason);
    }

    // ---- shared helpers ----------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Ashharrier) && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Open sky over the target: no block in the 15 above its feet, room to lift it 12 blocks and fly over it. */
    private static boolean roomAbove(ServerLevel level, LivingEntity target) {
        BlockPos.MutableBlockPos pos = target.blockPosition().mutable();
        for (int i = 1; i <= ROOM; i++) {
            pos.move(0, 1, 0);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        }
        return true;
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
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- test hooks ---------------------------------------------------------------------------------------------------

    /** Who it carries, or null. Public as a test hook. */
    public @Nullable LivingEntity victim() {
        return grab.victim();
    }

    /** The height the current (or last) lift takes its victim, in blocks. Public as a test hook. */
    public double liftHeight() {
        return lift;
    }

    /** Ticks until it may cry again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it cry again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() || core().in(Step.CIRCLE) ? ModSounds.ASHHARRIER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ASHHARRIER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ASHHARRIER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.15F;
    }
}
