package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Spore Cannon: a squat fungus turret of the warped forest, rooted where it grew, with a swollen spore bulb on top. It
 * never walks and nothing pushes it off its spot; it only turns to face its target.
 *
 * <p>Tell: for 20 ticks the bulb swells and glows weak-point gold, hissing louder and louder. Then it fires a volley of
 * 3 spores, one every 4 ticks, up out of the vent in a fan; each climbs for a moment, then homes in on its target at
 * 0.3 blocks a tick, turning at most 10 degrees a tick, and bursts on the first thing it touches for 4. A spore has 1
 * health: a swing that passes through it or an arrow that crosses it pops it. One volley every 80 ticks.
 *
 * <p>Counter: hit the bulb while it glows, before and during the volley. The bulb is the top of its body, so the blow
 * must land up there: a swing aimed at it, or an arrow into it. That hit lands double, the spores it has in the air
 * fall apart, the rest of the volley is lost, and it is stunned for 80 ticks, taking double from every blow. Swat the
 * spores that come at you.
 */
public class SporeCannon extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL, FIRE, STUNNED }

    /** 1 s of swelling and hissing with the bulb lit. */
    public static final int TELL_TICKS = 20;
    /** The volley: 3 spores, one every 4 ticks, the bulb lit until the last is out. */
    public static final int SPORES = 3;
    public static final int SPORE_GAP = 4;
    public static final int FIRE_TICKS = 12;
    /** Ticks from one volley to the next, tell included. */
    public static final int VOLLEY_EVERY = 80;
    /** It starts a volley at a target this close that it can see. */
    public static final double FIRE_RANGE = 16.0;

    /** A spore: 0.3 blocks a tick, 4 on a hit, 1 health. */
    public static final double SPORE_SPEED = 0.3;
    public static final float SPORE_DAMAGE = 4.0F;
    public static final float SPORE_HEALTH = 1.0F;
    /** It climbs out of the vent for 6 ticks, then turns toward its quarry by at most 10 degrees a tick. */
    public static final int SPORE_RISE = 6;
    public static final double SPORE_TURN = 10.0;
    /** It bursts on its own after 100 ticks (30 blocks) in the air. */
    public static final int SPORE_LIFE = 100;
    /** How far from a hitbox a spore still strikes. */
    public static final double SPORE_RADIUS = 0.25;
    /** The launch: tipped this far off straight up, the side spores this far either side of the aim. */
    public static final double LAUNCH_TILT = 40.0;
    public static final double LAUNCH_FAN = 50.0;
    /** A swing pops a spore this close to the swing's line; an arrow one this close to its flight. */
    public static final double SWAT_RADIUS = 0.6;
    public static final double DART_RADIUS = 0.5;
    /** What a projectile through a spore does to it (an arrow's base damage): always enough to pop it. */
    public static final float DART_DAMAGE = 2.0F;

    /** The bulb is everything above this height (blocks from its feet, at scale 1); a hit there while it glows stuns. */
    public static final double BULB_BASE = 0.9;
    /** Stunned for 4 s, taking double from every blow; the hit on the bulb itself lands double too. */
    public static final int STUN_TICKS = 80;
    public static final float STUN_DAMAGE = 2.0F;
    /** The heaviest turn it makes in a tick to face its target, in degrees. */
    public static final float TURN_PER_TICK = 6.0F;
    /** The vent spores leave from, from its feet. */
    public static final double VENT_HEIGHT = 1.62;

    private static final int WARPED_SPORE_COLOR = 0x3EE6C4;
    private static final int CRIMSON_SPORE_COLOR = 0xF0483C;
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD36B, 1.1F);
    private static final DustParticleOptions WARPED_DUST = new DustParticleOptions(WARPED_SPORE_COLOR, 1.7F);
    private static final DustParticleOptions CRIMSON_DUST = new DustParticleOptions(CRIMSON_SPORE_COLOR, 1.7F);
    private static final DustParticleOptions WARPED_HEAD = new DustParticleOptions(WARPED_SPORE_COLOR, 1.4F);
    private static final DustParticleOptions CRIMSON_HEAD = new DustParticleOptions(CRIMSON_SPORE_COLOR, 1.4F);
    private static final DustParticleOptions WARPED_TRAIL = new DustParticleOptions(0x2FA896, 0.6F);
    private static final DustParticleOptions CRIMSON_TRAIL = new DustParticleOptions(0xA8302A, 0.6F);
    private static final int STARS = 4;
    /** Players within this many blocks of a spore are watched for swings. */
    private static final double SWAT_SEARCH = 6.0;

    /** The spores in flight. Not saved: a reload drops the ones in the air. */
    private final List<Spore> spores = new ArrayList<>();
    /** Each nearby player's swing as last seen: a new swing is a new object. */
    private final Map<UUID, LivingEntity.SwingDescription> swings = new HashMap<>();
    private final Shot.Rules rules = new Shot.Rules(this::sporeCanHit, this::trail,
            (level, shot, victim, at) -> sporeHit(level, victim, at), (level, shot, at, blocked) -> fizzle(level, at, blocked));
    private int cooldown;
    private int volleyShots;
    private int launched;
    private float aimYaw;
    private @Nullable LivingEntity pendingTell;

    public SporeCannon(EntityType<? extends SporeCannon> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("spore_cannon", 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** The bulb glows weak-point gold while it is lit (the tell and the volley) and while it is stunned (double damage). */
    @Override
    public boolean vulnerable() {
        return bulbLit() || core().in(Step.STUNNED);
    }

    /** The bulb is lit, and a hit on it stuns: from the first tick of the tell to the end of the volley. */
    public boolean bulbLit() {
        return core().in(Step.TELL) || core().in(Step.FIRE);
    }

    /** Every phase that starts a volley starts it from its first spore. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        if (phase == Step.FIRE) volleyShots = 0;
        return true;
    }

    // ---- rooted: it turns, nothing more ----------------------------------------------------------------------------

    /** Only falling moves it: walking, knockback, pistons and currents all lose their sideways part. */
    @Override
    public Pin pin() {
        return Pin.ROOTED;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Body and head are one piece. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        this.yBodyRot = this.yHeadRot;
    }

    private void turnToward(Vec3 point) {
        float yaw = Mth.approachDegrees(getYRot(), yawTo(point), TURN_PER_TICK);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private float yawTo(Vec3 point) {
        return (float) (Mth.atan2(point.z - getZ(), point.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    // ---- the volley cycle ------------------------------------------------------------------------------------------

    private static boolean canAim(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        boolean valid = canAim(target);
        if (valid && !core().in(Step.STUNNED)) turnToward(target.position());
        if (!valid || !core().idle() || cooldown > 0) return;
        if (distanceTo(target) <= FIRE_RANGE && getSensing().hasLineOfSight(target)) pendingTell = target;
    }

    /** Starts the tell at {@code target} if it is idle; ignores range and cooldown. Public as a test hook. */
    public boolean tell(@Nullable LivingEntity target) {
        if (!core().idle() || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        aimYaw = target != null ? yawTo(target.position()) : getYRot();
        Telegraph.particles(GOLD, 10, 0.35, 0.8).withSound(ModSounds.SPORE_CANNON_TELL.get(), 1.2F, 1.0F).play(this);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        // a tell starts after this tick's phase clock has run, so it lasts exactly TELL_TICKS
        if (pendingTell != null) {
            LivingEntity target = pendingTell;
            pendingTell = null;
            tell(target);
        }
        if (core().in(Step.FIRE)) {
            while (volleyShots < SPORES && core().elapsed() >= volleyShots * SPORE_GAP) {
                launch(level, volleyShots);
                volleyShots++;
            }
        }
        phaseParticles(level);
        tickSpores(level);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL) {
            cooldown = VOLLEY_EVERY - TELL_TICKS;
            if (!startPhase(Step.FIRE, FIRE_TICKS, core().target())) core().end();
        } else {
            core().end();
        }
    }

    /** The tell's gold motes and climbing spores, the volley's puffs, the stun's circling stars. */
    private void phaseParticles(ServerLevel level) {
        Vec3 bulb = bulbCentre();
        if (core().in(Step.TELL)) {
            int t = core().elapsed();
            if (t % 2 == 0) level.sendParticles(GOLD, true, true, bulb.x, bulb.y, bulb.z, 2, 0.3, 0.25, 0.3, 0.0);
            // the hiss builds: more spore dust seeps from the vent as the tell runs on
            int seep = 1 + t / 7;
            Vec3 vent = vent();
            level.sendParticles(trailDust(), true, true, vent.x, vent.y + 0.1, vent.z, seep, 0.1, 0.06, 0.1, 0.0);
        } else if (core().in(Step.STUNNED) && tickCount % 4 == 0) {
            // circling the crown of the bulb; the dust drifts up from there
            double turn = core().elapsed() * (Math.PI * 2 / 20);
            double y = getY() + getBbHeight() - 0.2 * getScale();
            for (int i = 0; i < STARS; i++) {
                double a = turn + i * (Math.PI * 2 / STARS);
                level.sendParticles(GOLD, getX() + Math.cos(a) * 0.55, y, getZ() + Math.sin(a) * 0.55, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Where spores leave: the vent on top of the bulb. */
    public Vec3 vent() {
        return position().add(0.0, VENT_HEIGHT * getScale(), 0.0);
    }

    /** The middle of the bulb. */
    public Vec3 bulbCentre() {
        return position().add(0.0, (BULB_BASE + VENT_HEIGHT) / 2.0 * getScale(), 0.0);
    }

    /** The lowest point of the bulb, in world height. */
    public double bulbBase() {
        return getY() + BULB_BASE * getScale();
    }

    /** Fires spore {@code i} of the volley: up out of the vent, tipped toward the aim and fanned to one side. */
    private void launch(ServerLevel level, int i) {
        LivingEntity quarry = canAim(getTarget()) ? getTarget() : core().target();
        if (!MechanicEvent.post(this, "spore", quarry)) return;
        if (quarry != null) aimYaw = yawTo(quarry.position());
        double fan = i == 0 ? 0.0 : i == 1 ? LAUNCH_FAN : -LAUNCH_FAN;
        double yaw = Math.toRadians(aimYaw + fan);
        double tilt = Math.toRadians(LAUNCH_TILT);
        Vec3 dir = new Vec3(-Math.sin(yaw) * Math.sin(tilt), Math.cos(tilt), Math.cos(yaw) * Math.sin(tilt));
        Vec3 from = vent();
        Homing path = new Homing(from, dir, SPORE_SPEED, SPORE_RISE, Math.toRadians(SPORE_TURN), SPORE_LIFE, quarry);
        spores.add(new Spore(new Shot(path), path));
        launched++;
        level.sendParticles(sporeDust(), true, true, from.x, from.y, from.z, 4, 0.12, 0.08, 0.12, 0.0);
        level.playSound(null, from.x, from.y, from.z, ModSounds.SPORE_CANNON_SPORE.get(), getSoundSource(), 1.0F, 0.9F + 0.1F * i);
    }

    // ---- spores ----------------------------------------------------------------------------------------------------

    /** One spore: its shot, its homing path, and what health it has left. */
    private static final class Spore {
        final Shot shot;
        final Homing path;
        float health = SPORE_HEALTH;
        boolean popped;
        /** Where it was one and two steps ago: its last two steps are the stretch a projectile can cross. */
        Vec3 back1;
        Vec3 back2;

        Spore(Shot shot, Homing path) {
            this.shot = shot;
            this.path = path;
            this.back1 = shot.pos();
            this.back2 = shot.pos();
        }
    }

    /**
     * A spore's way: straight along its launch line for its first ticks, then on at the same speed, turning toward the
     * middle of its quarry by at most a set angle a tick. Without a quarry (or once it is gone) it flies straight. It
     * is worked out a tick at a time as the shot asks for it, so it follows where the quarry is now.
     */
    static final class Homing implements Shot.Path {
        private final int rise;
        private final double maxTurn;
        private final double life;
        private final @Nullable LivingEntity quarry;
        private Vec3 pos;
        private Vec3 vel;
        private double t;

        Homing(Vec3 from, Vec3 dir, double speed, int rise, double maxTurnRadians, double life, @Nullable LivingEntity quarry) {
            this.pos = from;
            this.vel = dir.normalize().scale(speed);
            this.rise = rise;
            this.maxTurn = maxTurnRadians;
            this.life = life;
            this.quarry = quarry;
        }

        @Override
        public Vec3 at(double ticks) {
            while (t < ticks - 1.0E-9) {
                double dt = Math.min(1.0, ticks - t);
                if (t >= rise && quarry != null && quarry.isAlive() && !quarry.isRemoved()) {
                    vel = turn(vel, quarry.getBoundingBox().getCenter().subtract(pos), maxTurn * dt);
                }
                pos = pos.add(vel.scale(dt));
                t += dt;
            }
            return pos;
        }

        @Override
        public double ticks() {
            return life;
        }

        Vec3 velocity() {
            return vel;
        }
    }

    /** {@code v} turned toward {@code want} by at most {@code max} radians, its length kept. */
    static Vec3 turn(Vec3 v, Vec3 want, double max) {
        double speed = v.length();
        if (want.lengthSqr() < 1.0E-12 || speed < 1.0E-12) return v;
        Vec3 a = v.scale(1.0 / speed);
        Vec3 b = want.normalize();
        double cos = Mth.clamp(a.dot(b), -1.0, 1.0);
        if (Math.acos(cos) <= max) return b.scale(speed);
        Vec3 side = b.subtract(a.scale(cos));
        if (side.lengthSqr() < 1.0E-12) side = Math.abs(a.y) < 0.9 ? a.cross(new Vec3(0, 1, 0)) : a.cross(new Vec3(1, 0, 0));
        side = side.normalize();
        return a.scale(Math.cos(max)).add(side.scale(Math.sin(max))).scale(speed);
    }

    /** Swings and arrows first, then every spore moves a tick. */
    private void tickSpores(ServerLevel level) {
        if (spores.isEmpty() && !bulbLit()) {
            swings.clear();
            return;
        }
        watchSwings(level);
        if (spores.isEmpty()) return;
        Iterator<Spore> it = spores.iterator();
        while (it.hasNext()) {
            Spore s = it.next();
            if (s.popped) {
                it.remove();
                continue;
            }
            s.back2 = s.back1;
            s.back1 = s.shot.pos();
            if (s.shot.step(level, this, SPORE_RADIUS, rules)) it.remove();
        }
        watchDarts(level);
        spores.removeIf(s -> s.popped);
    }

    private boolean sporeCanHit(LivingEntity e) {
        return e != this && !(e instanceof SporeCannon) && e.canBeHitByProjectile() && !e.isSpectator();
    }

    private AABB sporeArea() {
        AABB all = new AABB(position(), position()).inflate(1.0);
        for (Spore s : spores) all = all.minmax(new AABB(s.shot.pos(), s.shot.pos()));
        return all;
    }

    /**
     * A player's swing pops the first spore on its line, out to the player's reach and short of any block. Swings are
     * watched while the bulb is lit and while spores fly; a swing is new when the player's swing is a new one.
     */
    private void watchSwings(ServerLevel level) {
        AABB area = sporeArea().inflate(SWAT_SEARCH);
        for (Player p : level.getEntitiesOfClass(Player.class, area, e -> e.isAlive() && !e.isSpectator())) {
            LivingEntity.SwingDescription swing = p.getCurrentSwing();
            boolean seen = swings.containsKey(p.getUUID());
            LivingEntity.SwingDescription last = swings.put(p.getUUID(), swing);
            if (!seen || swing == null || swing == last || spores.isEmpty()) continue;
            Vec3 eye = p.getEyePosition();
            Vec3 end = eye.add(p.getViewVector(1.0F).scale(p.entityInteractionRange()));
            BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
            Spore best = nearestOn(eye, end, SWAT_RADIUS);
            if (best != null) hurtSpore(level, best, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), p);
        }
    }

    /**
     * A projectile whose last move came within reach of a spore's last two steps pops it: the first such spore along
     * its flight. Both are swept, not taken where they stand, because a projectile's last move is this tick's or the
     * last tick's depending on whether it ticks before or after the cannon; two steps of the spore's path cover either.
     */
    private void watchDarts(ServerLevel level) {
        AABB area = sporeArea().inflate(5.0);
        for (Projectile dart : level.getEntitiesOfClass(Projectile.class, area, Entity::isAlive)) {
            Vec3 now = dart.position();
            Vec3 before = new Vec3(dart.xo, dart.yo, dart.zo);
            double moved = now.distanceToSqr(before);
            if (moved < 1.0E-4 || moved > 25.0) continue;        // lying still, or not yet moved since it was placed
            Spore hit = null;
            double first = Double.MAX_VALUE;
            for (Spore s : spores) {
                if (s.popped) continue;
                for (double[] near : new double[][] {closest(before, now, s.back2, s.back1), closest(before, now, s.back1, s.shot.pos())}) {
                    if (near[0] <= DART_RADIUS * DART_RADIUS && near[1] < first) {
                        first = near[1];
                        hit = s;
                    }
                }
            }
            if (hit != null) hurtSpore(level, hit, DART_DAMAGE, dart.getOwner() instanceof LivingEntity l ? l : null);
        }
    }

    /**
     * The closest approach of segments {@code p1}-{@code q1} and {@code p2}-{@code q2}: the squared distance, and how
     * far along the first (0..1) its closest point lies.
     */
    static double[] closest(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
        Vec3 d1 = q1.subtract(p1);
        Vec3 d2 = q2.subtract(p2);
        Vec3 r = p1.subtract(p2);
        double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
        double s, t;
        if (a < 1.0E-12 && e < 1.0E-12) {
            s = 0.0;
            t = 0.0;
        } else if (a < 1.0E-12) {
            s = 0.0;
            t = Mth.clamp(f / e, 0.0, 1.0);
        } else {
            double c = d1.dot(r);
            if (e < 1.0E-12) {
                t = 0.0;
                s = Mth.clamp(-c / a, 0.0, 1.0);
            } else {
                double b = d1.dot(d2);
                double denom = a * e - b * b;
                s = denom > 1.0E-12 ? Mth.clamp((b * f - c * e) / denom, 0.0, 1.0) : 0.0;
                t = (b * s + f) / e;
                if (t < 0.0) {
                    t = 0.0;
                    s = Mth.clamp(-c / a, 0.0, 1.0);
                } else if (t > 1.0) {
                    t = 1.0;
                    s = Mth.clamp((b - c) / a, 0.0, 1.0);
                }
            }
        }
        return new double[] {p1.add(d1.scale(s)).distanceToSqr(p2.add(d2.scale(t))), s};
    }

    /** The first live spore along {@code from}-{@code to} within {@code radius} of it, or null. */
    private @Nullable Spore nearestOn(Vec3 from, Vec3 to, double radius) {
        Spore best = null;
        double bestT = Double.MAX_VALUE;
        Vec3 d = to.subtract(from);
        double len2 = d.lengthSqr();
        for (Spore s : spores) {
            if (s.popped) continue;
            Vec3 p = s.shot.pos();
            double t = len2 < 1.0E-12 ? 0.0 : Mth.clamp(p.subtract(from).dot(d) / len2, 0.0, 1.0);
            if (from.add(d.scale(t)).distanceToSqr(p) <= radius * radius && t < bestT) {
                bestT = t;
                best = s;
            }
        }
        return best;
    }

    /** Takes {@code amount} off a spore's health; at none left it pops. True if it popped. */
    private boolean hurtSpore(ServerLevel level, Spore s, float amount, @Nullable LivingEntity by) {
        if (s.popped || amount <= 0.0F) return false;
        float left = s.health - amount;
        Vec3 at = s.shot.pos();
        if (left > 0.0F) {
            s.health = left;
            level.sendParticles(sporeDust(), true, true, at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.0);
            return false;
        }
        if (!MechanicEvent.post(this, "spore_pop", by)) return false;
        s.health = 0.0F;
        s.popped = true;
        level.sendParticles(sporeDust(), true, true, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(sporeMote(), true, true, at.x, at.y, at.z, 4, 0.25, 0.25, 0.25, 0.05);
        level.sendParticles(ParticleTypes.POOF, true, true, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.02);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SPORE_CANNON_SPORE_POP.get(), getSoundSource(), 1.0F, 1.0F);
        return true;
    }

    /** The spore and its wake: a bright ball at its head, a thin trail behind that hangs a moment, so the turn shows. */
    private void trail(ServerLevel level, Vec3 from, Vec3 to) {
        DustParticleOptions wake = trailDust();
        level.sendParticles(wake, true, true, (from.x + to.x) / 2, (from.y + to.y) / 2, (from.z + to.z) / 2, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(look().equals("crimson_cannon") ? CRIMSON_HEAD : WARPED_HEAD, true, true, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private DustParticleOptions trailDust() {
        return look().equals("crimson_cannon") ? CRIMSON_TRAIL : WARPED_TRAIL;
    }

    private void sporeHit(ServerLevel level, LivingEntity victim, Vec3 at) {
        if (MechanicEvent.post(this, "spore_hit", victim)) {
            victim.damageCooldownTime = 0;                        // each spore is its own blow: the last one's cooldown must not eat it
            victim.hurtServer(level, Shot.damage(this), SPORE_DAMAGE);
        }
        level.sendParticles(sporeDust(), true, true, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(sporeMote(), true, true, at.x, at.y, at.z, 3, 0.25, 0.25, 0.25, 0.05);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SPORE_CANNON_SPORE_HIT.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** A spore that hit nothing (a wall, or the end of its flight) breaks where it stopped. */
    private void fizzle(ServerLevel level, Vec3 at, boolean blocked) {
        level.sendParticles(sporeDust(), true, true, at.x, at.y, at.z, 6, 0.12, 0.12, 0.12, 0.0);
        level.sendParticles(sporeMote(), true, true, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.03);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SPORE_CANNON_SPORE_POP.get(), getSoundSource(), 0.6F, 0.8F);
    }

    /** Every spore in the air falls apart at once. */
    private void dropSpores(ServerLevel level) {
        for (Spore s : spores) {
            Vec3 at = s.shot.pos();
            level.sendParticles(sporeDust(), true, true, at.x, at.y, at.z, 6, 0.12, 0.12, 0.12, 0.0);
            level.sendParticles(ParticleTypes.SMOKE, true, true, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
        spores.clear();
    }

    private DustParticleOptions sporeDust() {
        return look().equals("crimson_cannon") ? CRIMSON_DUST : WARPED_DUST;
    }

    private SimpleParticleType sporeMote() {
        return look().equals("crimson_cannon") ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.WARPED_SPORE;
    }

    // ---- the bulb --------------------------------------------------------------------------------------------------

    /**
     * True if the blow landed on the bulb: the top of its body. A blow from something that looks (a player's swing, a
     * mob's) is traced along its look, a projectile along its flight, and the point where that line enters its body
     * decides; damage with no such line (fire, a fall, magic from nowhere) never lands on the bulb.
     */
    public boolean hitsBulb(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Vec3 from;
        Vec3 dir;
        if (direct instanceof LivingEntity living) {
            from = living.getEyePosition();
            dir = living.getViewVector(1.0F);
        } else if (direct != null && direct.getDeltaMovement().lengthSqr() > 1.0E-6) {
            dir = direct.getDeltaMovement().normalize();
            from = direct.position().subtract(dir.scale(2.0));
        } else {
            return false;
        }
        AABB box = getBoundingBox().inflate(0.05);
        Optional<Vec3> entry = box.contains(from) ? Optional.of(from) : box.clip(from, from.add(dir.scale(16.0)));
        Vec3 at;
        if (entry.isPresent()) {
            at = entry.get();
        } else {                                                  // the line misses: the point of the box nearest it
            Vec3 c = box.getCenter();
            Vec3 near = from.add(dir.scale(Math.max(0.0, c.subtract(from).dot(dir))));
            at = new Vec3(Mth.clamp(near.x, box.minX, box.maxX), Mth.clamp(near.y, box.minY, box.maxY), Mth.clamp(near.z, box.minZ, box.maxZ));
        }
        return at.y >= bulbBase();
    }

    /** A hit on the lit bulb lands double and stuns it; stunned, every hit lands double. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        LivingEntity by = source.getEntity() instanceof LivingEntity l ? l : null;
        boolean onBulb = bulbLit() && hitsBulb(source) && MechanicEvent.post(this, "bulb_hit", by);
        boolean doubled = onBulb || core().in(Step.STUNNED);
        boolean hurt = super.hurtServer(level, source, doubled ? amount * STUN_DAMAGE : amount);
        if (hurt && onBulb) stun(level, by);
        return hurt;
    }

    /**
     * Stunned for 80 ticks: the volley is lost and every spore in the air falls apart. Returns false if an event
     * cancelled it. Public as a test hook.
     */
    public boolean stun(ServerLevel level, @Nullable LivingEntity by) {
        if (!startPhase(Step.STUNNED, STUN_TICKS, by)) return false;
        dropSpores(level);
        Vec3 b = bulbCentre();
        level.sendParticles(GOLD, true, true, b.x, b.y, b.z, 14, 0.3, 0.25, 0.3, 0.0);
        level.sendParticles(sporeMote(), true, true, b.x, b.y, b.z, 16, 0.35, 0.3, 0.35, 0.06);
        level.sendParticles(ParticleTypes.POOF, true, true, b.x, b.y, b.z, 6, 0.25, 0.2, 0.25, 0.03);
        level.playSound(null, b.x, b.y, b.z, ModSounds.SPORE_CANNON_BULB_HIT.get(), getSoundSource(), 1.3F, 1.0F);
        return true;
    }

    // ---- state and test hooks --------------------------------------------------------------------------------------

    /** Ticks until it may start its next tell. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it start a tell at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    /** How many spores are in the air. */
    public int sporesInFlight() {
        return spores.size();
    }

    /** How many spores it has launched since it was spawned. */
    public int launched() {
        return launched;
    }

    /** Where each spore in the air is, oldest first. */
    public List<Vec3> sporePositions() {
        List<Vec3> out = new ArrayList<>(spores.size());
        for (Spore s : spores) out.add(s.shot.pos());
        return out;
    }

    /** Which way each spore in the air is heading, in blocks a tick, oldest first. */
    public List<Vec3> sporeVelocities() {
        List<Vec3> out = new ArrayList<>(spores.size());
        for (Spore s : spores) out.add(s.path.velocity());
        return out;
    }

    /** The health left on spore {@code i} (oldest first). */
    public float sporeHealth(int i) {
        return spores.get(i).health;
    }

    /** Deals {@code amount} to spore {@code i} (oldest first); true if it popped. Public as a test hook. */
    public boolean hurtSpore(int i, float amount) {
        return level() instanceof ServerLevel level && hurtSpore(level, spores.get(i), amount, null);
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SPORE_CANNON_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SPORE_CANNON_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SPORE_CANNON_DEATH.get();
    }
}
