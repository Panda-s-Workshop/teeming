package com.panda.teeming.mob.nether;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Quakehopper: a squat, heavy hopper of cracked basalt with springy hind legs. With a target 3 to 16 blocks away in
 * sight it crouches low for 14 ticks (tell: its legs glow, they creak with the strain, smoke rises off them), then
 * hops high towards the target and comes down 3 blocks short of where it stood. Where it lands a shockwave ring grows
 * along the ground at 0.4 blocks a tick, out to a radius of 7. The ring's front strikes once: everything standing on
 * the ground as the front passes under it takes 6 and is knocked away from the landing spot; anyone in the air at that
 * moment is untouched. Up close it kicks for its attack damage.
 *
 * <p>Counter: jump the ring as its front reaches you. If its target clears the front in the air, the hopper's leg
 * springs give out: for 100 ticks it can't hop at all, its leg glow goes dark and it lumbers at half speed, so it has
 * to walk up and kick. It never places or breaks blocks.
 *
 * <p>Two parts are kept self-contained here: the hop (a scripted ballistic flight, as the Lavaleaper's leap) and the
 * {@link Wave} (a ring whose radius grows each tick, struck once per thing it passes).
 */
public class Quakehopper extends TeemingMonster {
    /** At rest or walking, the crouch, in the air, landed while the ring runs, its springs given out. */
    public enum Step implements Phase { IDLE, TELL, HOP, QUAKE, SPENT }

    /** The crouch before a hop: 14 ticks, legs glowing. */
    public static final int TELL_TICKS = 14;
    /** The ring's front moves out this far each tick, in blocks. */
    public static final double RING_SPEED = 0.4;
    /** The ring stops growing at this radius, in blocks. */
    public static final double RING_RADIUS = 7.0;
    /** The front strikes everything on the ground it passes for this much. */
    public static final float RING_DAMAGE = 6.0F;
    /** Ticks the ring takes to reach its full radius (7 / 0.4, rounded up): how long it stays landed. */
    public static final int RING_TICKS = 18;
    /** The front throws those it strikes away from the landing spot at this speed (blocks a tick), and up at this. */
    public static final double RING_KNOCKBACK = 0.5;
    public static final double RING_LIFT = 0.3;
    /** The front runs along the ground it landed on: feet this far above or below it are out of its reach. */
    public static final double RING_REACH_Y = 1.5;
    /** It hops at targets this far away across the ground; nearer ones it walks up to and kicks. */
    public static final double HOP_MIN = 3.0;
    public static final double HOP_RANGE = 16.0;
    /** A hop comes down this far short of where its target stood, and carries it at most this far. */
    public static final double HOP_SHORT = 3.0;
    public static final double HOP_MAX = 10.0;
    /** Upward speed as it leaves the ground (blocks a tick): the arc peaks 4.4 blocks up. */
    public static final double HOP_UP = 0.8;
    /** Pull on the hop's arc, in blocks a tick per tick. */
    public static final double GRAVITY = 0.08;
    /** A hop ends after this many ticks at most, wherever it is. */
    public static final int FLIGHT_TICKS = 40;
    /** Ticks after a ring (or its springs coming back) before the next crouch can start. */
    public static final int COOLDOWN = 60;
    /** Its target jumped the ring: no hops for 100 ticks, at half speed. */
    public static final int SPENT_TICKS = 100;
    public static final double SPENT_SPEED = 0.5;

    private static final int GLOW_REST = 0, GLOW_HOT = 1, GLOW_DARK = 2;
    private static final Identifier SPENT_SLOW = TeemingMod.id("quakehopper_spent");
    private static final Telegraph CROUCH = Telegraph.particles(ParticleTypes.SMOKE, 10, 0.45, 0.3).withSpeed(0.02)
            .withSound(ModSounds.QUAKEHOPPER_TELL.get(), 1.0F, 1.0F);

    private int cooldown;
    // the hop
    private boolean airborne;
    private Vec3 flight = Vec3.ZERO;
    private Vec3 hopFrom = Vec3.ZERO;
    private Vec3 hopTo = Vec3.ZERO;
    // the ring of the last landing, while it runs
    private @Nullable Wave wave;
    /** The phase the glow stage was last set for. */
    private int stagedPhase = -1;

    public Quakehopper(EntityType<? extends Quakehopper> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("quakehopper", 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return canKick() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return canKick() && super.canContinueToUse();
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

    /** It walks up and kicks when it isn't crouching, hopping or landed; with its springs gone, it still kicks. */
    private boolean canKick() {
        return (core().idle() || core().in(Step.SPENT)) && !airborne;
    }

    @Override
    public int visualStages() {
        return 3;
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (airborne) {
            flightTick(level);
        } else if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().in(Step.QUAKE)) {
            getNavigation().stop();
        } else if (core().in(Step.SPENT)) {
            spentTick(level);
        } else if (core().idle()) {
            LivingEntity target = getTarget();
            if (canHop(target)) tell(target);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (wave != null) wave.tick(level, this::canShake, e -> strike(level, e));   // kept once done: the landing reads it
        if (phase().ordinal() != stagedPhase) {                // the glow and the pace follow each change of phase
            stagedPhase = phase().ordinal();
            core().setStage(glowStage());
            slow(core().in(Step.SPENT));
        }
    }

    /** Embers at rest; the legs hot through the crouch, the hop and the landing; dark while its springs are spent. */
    private int glowStage() {
        if (core().in(Step.TELL) || core().in(Step.HOP) || core().in(Step.QUAKE)) return GLOW_HOT;
        if (core().in(Step.SPENT)) return GLOW_DARK;
        return GLOW_REST;
    }

    /** True when it may crouch to hop at {@code target} now: ready, on the ground, 3 to 16 blocks across, in sight. */
    public boolean canHop(@Nullable LivingEntity target) {
        if (!valid(target) || cooldown > 0 || !onGround() || isInWater() || isInLava()) return false;
        double d = position().subtract(target.position()).horizontalDistance();
        return d >= HOP_MIN && d <= HOP_RANGE && getSensing().hasLineOfSight(target);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (isNoAi() || !(level() instanceof ServerLevel level)) {
            airborne = false;
            core().end();
        } else if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (valid(target) && onGround()) hop(level, target);
            else rest();
        } else if (ended == Step.HOP) {
            airborne = false;                                // the flight timed out: a ring only if it is down
            if (onGround() && !isInLava() && !isInWater()) quake(level, core().target());
            else rest();
        } else if (ended == Step.QUAKE) {
            LivingEntity target = core().target();
            if (wave != null && target != null && wave.dodged(target)) spend(level);
            else rest();
        } else {
            rest();
        }
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
    }

    // ---- the tell ----------------------------------------------------------------------------------------------------

    /**
     * Starts the crouch at {@code target}: 14 ticks, then the hop. True if it started. Ignores the cooldown and the
     * range, but it must stand on the ground. Public as a test hook.
     */
    public boolean tell(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle() || airborne || !onGround()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        getNavigation().stop();
        if (target != null) face(target.position().subtract(position()));
        CROUCH.play(this);
        return true;
    }

    /** Crouched, facing its target: smoke rises off both hind legs and they creak again half way through. */
    private void tellTick(ServerLevel level) {
        getNavigation().stop();
        LivingEntity target = core().target();
        if (target != null) face(target.position().subtract(position()));
        int t = core().elapsed();
        if (t % 2 == 0) {
            for (Vec3 knee : knees()) level.sendParticles(ParticleTypes.SMOKE, knee.x, knee.y, knee.z, 1, 0.08, 0.05, 0.08, 0.01);
        }
        if (t % 3 == 1) {
            for (Vec3 knee : knees()) level.sendParticles(ParticleTypes.SMALL_FLAME, knee.x, knee.y, knee.z, 1, 0.06, 0.04, 0.06, 0.005);
        }
        if (t == TELL_TICKS / 2) playSound(ModSounds.QUAKEHOPPER_TELL.get(), 0.8F, 1.25F);
    }

    /** Where its two knees are in the world (the hind legs' tops, at its flanks). */
    private Vec3[] knees() {
        float r = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(Mth.cos(r), 0.0, Mth.sin(r)).scale(getBbWidth() * 0.55);
        Vec3 back = new Vec3(Mth.sin(r), 0.0, -Mth.cos(r)).scale(getBbWidth() * 0.15);
        Vec3 at = position().add(back).add(0.0, getBbHeight() * 0.45, 0.0);
        return new Vec3[] {at.add(side), at.subtract(side)};
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Crouched and landed it is planted: only falling moves it. */
    @Override
    public Pin pin() {
        return core().in(Step.TELL) || core().in(Step.QUAKE) ? Pin.ROOTED : Pin.FREE;
    }

    // ---- the hop -----------------------------------------------------------------------------------------------------
    // Self-contained: a scripted ballistic flight from where it stands to a spot, starting upward at HOP_UP and pulled
    // down by GRAVITY each tick, with its collisions on (a wall stops it across; it falls from there). It ends the tick
    // it comes down on the ground, in lava or in water, or after FLIGHT_TICKS.

    /**
     * The start velocity that carries it from {@code from} to {@code to} in an arc that leaves upward at {@code up}.
     * The flight moves a whole step each tick (n ticks rise n x up - GRAVITY x n(n-1)/2), so the tick count is solved
     * for those steps: on the flat, 21 ticks at 0.8 up.
     */
    public static Vec3 arc(Vec3 from, Vec3 to, double up) {
        double dy = to.y - from.y;
        double climb = Math.max(up, Math.sqrt(2.0 * GRAVITY * Math.max(0.0, dy + 0.3)));   // always clears the spot
        double b = climb + GRAVITY * 0.5;
        double ticks = Math.max(1.0, (b + Math.sqrt(Math.max(0.0, b * b - 2.0 * GRAVITY * dy))) / GRAVITY);
        Vec3 across = to.subtract(from).multiply(1, 0, 1);
        return new Vec3(across.x / ticks, climb, across.z / ticks);
    }

    /** Where a hop at {@code target} from here comes down: 3 blocks short of it, at most 10 blocks off, at its height. */
    public Vec3 landingFor(LivingEntity target) {
        Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
        double d = to.length();
        double carry = d < 1.0E-4 ? 0.0 : Math.min(HOP_MAX, Math.max(0.0, d - HOP_SHORT)) / d;
        return new Vec3(getX() + to.x * carry, target.getY(), getZ() + to.z * carry);
    }

    /** In flight it follows its arc alone (no drag); otherwise it moves as usual. */
    @Override
    public void travel(Vec3 input) {
        if (airborne) {
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        super.travel(input);
    }

    /** Up in an arc towards {@code target}; the arc's first step is taken on the next AI step. */
    private void hop(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.HOP, FLIGHT_TICKS, target)) {
            rest();                                          // vetoed: it rises from the crouch and waits
            return;
        }
        hopFrom = position();
        hopTo = landingFor(target);
        flight = arc(hopFrom, hopTo, HOP_UP);
        airborne = true;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        face(target.position().subtract(position()));
        level.playSound(null, getX(), getY(), getZ(), ModSounds.QUAKEHOPPER_HOP.get(), getSoundSource(), 1.2F, 1.0F);
        BlockState under = getBlockStateOn();
        if (!under.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), getX(), getY() + 0.05, getZ(), 16, 0.4, 0.05, 0.4, 0.15);
        }
        level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.1, getZ(), 6, 0.35, 0.05, 0.35, 0.02);
    }

    /** Each tick in the air: the landing, else the next step of the arc. */
    private void flightTick(ServerLevel level) {
        resetFallDistance();
        if (flight.y < 0.0 && (onGround() || isInLava() || isInWater())) {
            land(level);
            return;
        }
        double vx = horizontalCollision ? 0.0 : flight.x, vz = horizontalCollision ? 0.0 : flight.z;
        Vec3 left = hopTo.subtract(position()).multiply(1, 0, 1);       // never past the spot it aimed for
        double step = Math.sqrt(vx * vx + vz * vz);
        if (step > 1.0E-6 && step > left.length()) {
            vx *= left.length() / step;
            vz *= left.length() / step;
        }
        double vy = verticalCollision && !verticalCollisionBelow ? Math.min(0.0, flight.y) : flight.y;   // a roof
        setDeltaMovement(vx, vy, vz);
        flight = new Vec3(vx, vy - GRAVITY, vz);
        if (core().elapsed() % 3 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 1, 0.2, 0.1, 0.2, 0.0);
    }

    /** Down: on the ground the ring goes out; in lava or water it only splashes. */
    private void land(ServerLevel level) {
        airborne = false;
        setDeltaMovement(0.0, Math.min(0.0, getDeltaMovement().y), 0.0);
        if (isInLava() || isInWater()) {
            rest();
            return;
        }
        quake(level, core().target());
    }

    /** True from a hop until it comes down (server side). */
    public boolean airborne() {
        return airborne;
    }

    /** Where its last hop started (server side). */
    public Vec3 hopFrom() {
        return hopFrom;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return !core().in(Step.HOP) && !core().in(Step.QUAKE) && super.causeFallDamage(fallDistance, damageMultiplier, source);
    }

    // ---- the landing: the ring -----------------------------------------------------------------------------------

    /**
     * Lands right where it stands: a thud, and the ring goes out from here for 18 ticks, aimed at {@code target}
     * (whose jump over the front spends its springs). True if it did. Public as a test hook.
     */
    public boolean quake(ServerLevel level, @Nullable LivingEntity target) {
        airborne = false;
        if (!startPhase(Step.QUAKE, RING_TICKS, target)) {
            rest();
            return false;
        }
        getNavigation().stop();
        wave = new Wave(position(), level.getGameTime(), RING_SPEED, RING_RADIUS, RING_REACH_Y);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.QUAKEHOPPER_LAND.get(), getSoundSource(), 1.5F, 1.0F);
        BlockState under = getBlockStateOn();
        if (!under.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), true, true, getX(), getY() + 0.1, getZ(), 30, 0.5, 0.1, 0.5, 0.2);
        }
        level.sendParticles(ParticleTypes.DUST_PLUME, true, true, getX(), getY() + 0.1, getZ(), 12, 0.5, 0.05, 0.5, 0.03);
        return true;
    }

    /** Who the front can strike: anything alive but itself, its own kind and players in creative or spectator. */
    private boolean canShake(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Quakehopper) && !e.isSpectator()
                && !(e instanceof Player p && p.isCreative());
    }

    /**
     * The front strikes {@code e} on the ground: 6, then it is thrown straight away from the landing spot at 0.5 blocks
     * a tick and up at 0.3, in place of the hit's own knockback; knockback resistance takes its share off both.
     */
    private void strike(ServerLevel level, LivingEntity e) {
        if (!MechanicEvent.post(this, "ring_hit", e) || wave == null) return;
        Vec3 from = wave.centre();
        e.hurtServer(level, damageSources().mobAttack(this), RING_DAMAGE);
        double give = 1.0 - Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        if (give > 0.0) {
            Vec3 out = e.position().subtract(from).multiply(1, 0, 1);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : out.normalize();
            Push.set(e, new Vec3(out.x * RING_KNOCKBACK * give, RING_LIFT * give, out.z * RING_KNOCKBACK * give));
        }
        level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 0.2, e.getZ(), 6, 0.3, 0.1, 0.3, 0.2);
    }

    /** The ring of the last landing while it still grows (server side), or null. */
    public @Nullable Wave wave() {
        return wave;
    }

    /**
     * A ring on the ground that grows from a centre at a fixed speed up to a radius, kept server-side and never as
     * blocks. Each tick its front is the band between last tick's radius and this tick's; whatever stands in the band
     * (by its hitbox, across the ground, with its feet within reach of the centre's height) is checked: on the ground,
     * it is struck, once per ring; in the air through every tick the front lies under it, it dodged. Drawn as a thin
     * line of dust and ground chips at the front only, so its speed can be read.
     */
    public static final class Wave {
        private final Vec3 centre;
        private final long start;
        private final double speed, radius, reachY;
        private final Set<UUID> struck = new HashSet<>(), aloft = new HashSet<>(), dodged = new HashSet<>();

        public Wave(Vec3 centre, long start, double speed, double radius, double reachY) {
            this.centre = centre;
            this.start = start;
            this.speed = speed;
            this.radius = radius;
            this.reachY = reachY;
        }

        public Vec3 centre() {
            return centre;
        }

        /** The front's radius at game time {@code now}: one step out on the tick it starts, then one more a tick. */
        public double radius(long now) {
            return Math.min(radius, speed * (now - start + 1));
        }

        /** True once {@code e} was struck by this ring. */
        public boolean struck(Entity e) {
            return struck.contains(e.getUUID());
        }

        /** True once the front passed {@code e} while it was in the air. */
        public boolean dodged(Entity e) {
            return dodged.contains(e.getUUID());
        }

        /** One tick of the ring: draws its front and checks what it passes. False once it has stopped growing. */
        public boolean tick(ServerLevel level, Predicate<LivingEntity> who, java.util.function.Consumer<LivingEntity> hit) {
            long now = level.getGameTime();
            double inner = Math.min(radius, speed * (now - start));
            if (inner >= radius) return false;
            double outer = radius(now);
            boolean last = outer >= radius;
            draw(level, outer);
            AABB box = new AABB(centre, centre).inflate(outer + 0.5, 0.0, outer + 0.5).expandTowards(0.0, reachY + 2.0, 0.0)
                    .expandTowards(0.0, -reachY - 0.5, 0.0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, who)) {
                UUID id = e.getUUID();
                if (struck.contains(id) || dodged.contains(id)) continue;
                if (Math.abs(e.getY() - centre.y) > reachY) continue;
                AABB b = e.getBoundingBox();
                double near = near(b), far = far(b);
                if (near > outer || far < inner) continue;   // the front isn't under it this tick
                if (grounded(e)) {
                    struck.add(id);
                    aloft.remove(id);
                    hit.accept(e);
                } else {
                    aloft.add(id);
                    if (far <= outer || last) dodged.add(id);  // the front went on past it, or stopped under it
                }
            }
            return !last;
        }

        /** Horizontal distance from the centre to the nearest point of a hitbox. */
        private double near(AABB b) {
            double dx = Math.max(Math.max(b.minX - centre.x, centre.x - b.maxX), 0.0);
            double dz = Math.max(Math.max(b.minZ - centre.z, centre.z - b.maxZ), 0.0);
            return Math.sqrt(dx * dx + dz * dz);
        }

        /** Horizontal distance from the centre to the farthest point of a hitbox. */
        private double far(AABB b) {
            double dx = Math.max(Math.abs(b.minX - centre.x), Math.abs(b.maxX - centre.x));
            double dz = Math.max(Math.abs(b.minZ - centre.z), Math.abs(b.maxZ - centre.z));
            return Math.sqrt(dx * dx + dz * dz);
        }

        /**
         * Standing on something: the entity's own ground flag, or a block (or a solid entity) just under its feet.
         * Players' flags come from their clients, so the second check keeps the rule honest for players without one.
         */
        public static boolean grounded(LivingEntity e) {
            if (e.onGround()) return true;
            AABB b = e.getBoundingBox();
            AABB under = new AABB(b.minX + 0.01, b.minY - 0.08, b.minZ + 0.01, b.maxX - 0.01, b.minY, b.maxZ - 0.01);
            return !e.level().noCollision(e, under);
        }

        /**
         * The front: a puff of dust every 0.75 blocks round the circle, each sent outward at the ring's own speed, so the
         * puffs ride the front instead of trailing behind it and the ring reads as a thin moving line; a chip of the
         * ground kicked up at every fourth.
         */
        private void draw(ServerLevel level, double r) {
            int n = Mth.clamp(Mth.ceil(Math.PI * 2.0 * r / 0.75), 6, 60);
            double turn = level.getRandom().nextDouble() * Math.PI * 2.0 / n;
            for (int i = 0; i < n; i++) {
                double a = turn + i * Math.PI * 2.0 / n;
                double cos = Math.cos(a), sin = Math.sin(a);
                double x = centre.x + cos * r, z = centre.z + sin * r;
                BlockPos ground = groundAt(level, x, z);
                if (ground == null) continue;
                BlockState state = level.getBlockState(ground);
                double y = ground.getY() + state.getCollisionShape(level, ground).max(Direction.Axis.Y);
                // a count of 0 sends the offsets as the particle's velocity, times the speed
                level.sendParticles(ParticleTypes.DUST_PLUME, true, false, x, y + 0.05, z, 0, cos, -0.3, sin, speed);
                if (i % 4 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), true, false, x, y + 0.05, z, 1, 0.05, 0.0, 0.05, 0.1);
            }
        }

        /** The block whose top is the ground in the column at (x, z) within reach of the centre's height, or null. */
        private @Nullable BlockPos groundAt(ServerLevel level, double x, double z) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(centre.y + reachY), Mth.floor(z));
            for (int i = 0; i < 5; i++) {
                VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                if (!shape.isEmpty()) {
                    double top = pos.getY() + shape.max(Direction.Axis.Y);
                    return top <= centre.y + reachY + 1.0E-3 && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                            ? pos.immutable() : null;
                }
                pos.move(Direction.DOWN);
            }
            return null;
        }
    }

    // ---- the counter: its springs spent ------------------------------------------------------------------------------

    /** Its target jumped the ring: no hops for 100 ticks, half speed, legs dark. True if it started. Public as a test hook. */
    public boolean spend(ServerLevel level) {
        if (!startPhase(Step.SPENT, SPENT_TICKS, getTarget())) {
            rest();
            return false;
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.QUAKEHOPPER_SPENT.get(), getSoundSource(), 1.2F, 1.0F);
        for (Vec3 knee : knees()) level.sendParticles(ParticleTypes.CLOUD, knee.x, knee.y, knee.z, 6, 0.12, 0.1, 0.12, 0.03);
        return true;
    }

    /** Steam hissing off its cooled knees now and then. */
    private void spentTick(ServerLevel level) {
        if (core().elapsed() % 8 == 0) {
            for (Vec3 knee : knees()) level.sendParticles(ParticleTypes.WHITE_SMOKE, knee.x, knee.y, knee.z, 1, 0.06, 0.05, 0.06, 0.01);
        }
    }

    /** Half speed while its springs are spent (a transient modifier, so a reload restores it). */
    private void slow(boolean on) {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (on && speed.getModifier(SPENT_SLOW) == null) {
            speed.addTransientModifier(new AttributeModifier(SPENT_SLOW, SPENT_SPEED - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!on && speed.getModifier(SPENT_SLOW) != null) {
            speed.removeModifier(SPENT_SLOW);
        }
    }

    // ---- the kick ----------------------------------------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) level.playSound(null, getX(), getY(), getZ(), ModSounds.QUAKEHOPPER_KICK.get(), getSoundSource(), 1.0F, 1.0F);
        return hit;
    }

    // ---- small rules and test hooks ----------------------------------------------------------------------------------

    private static boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Quakehopper) && super.canAttack(target);
    }

    /** Ticks until it may crouch again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it crouch again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.QUAKEHOPPER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.QUAKEHOPPER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.QUAKEHOPPER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.2F;
    }
}
