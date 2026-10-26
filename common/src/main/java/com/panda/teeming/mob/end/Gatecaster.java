package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gatecaster: a hunched, robed rift-weaver of the End highlands, holding a ring of violet light upright between its
 * hands. It keeps its distance and never strikes from the front: its bolts come out of a gate it opens behind you.
 *
 * <p>Every 80 ticks, with a target within 16 blocks in its sight, it weaves for 20 ticks (tell): its hands glow, its
 * ring spins up, and an exit rift opens 3-5 blocks behind the target, behind as the target faces when the tell starts:
 * an upright violet ring at chest height, crackling, with a rising whine. The gate needs open air: a ring-sized space
 * with no block or liquid in it and a clear line to the target. With no such spot behind, it tries the target's sides
 * (the side farther from the caster first); with none there either it opens no gate and throws its bolt straight from
 * its own ring. For the first 12 ticks the gate turns to follow the target; for the last 8 it is locked on where the
 * target last stood, with a click and a faint lane of light. Then a bolt (a shot, not an entity: a violet streak)
 * flies out of the gate along the locked line at 0.6 blocks a tick, out to 12 blocks. The first thing it meets takes
 * 6 as a mob's projectile from the gate's side, with no knockback, so a hit never throws anyone off an island.
 *
 * <p>Counter: hear the whine and turn round. Step aside once the gate stops turning and the bolt flies past, or raise
 * a shield toward the gate. A shield raised toward the gate takes the bolt (one raised toward the caster does not: the
 * bolt comes from behind), and the bolt goes back through the gate: the gate collapses and drags the caster through
 * after it. It tumbles out where the gate stood, onto safe ground under it, right in front of the shield (with no safe
 * ground there it stays where it was), and its own bolt bursts at its feet for 6. The ranged caster ends up close,
 * hurt and getting up: the fight shifts to the player's reach.
 *
 * <p>The gate is not an entity: the caster holds its facing through the tell and tells clients where the gate stands
 * (its offset in the caster's own frame) and which way it faces through its synced flags, and the model draws it there.
 * Its cast cooldown is saved; a gate or bolt under way is not.
 */
public class Gatecaster extends TeemingMonster {
    /** IDLE, the weave (tell, the gate open), the bolt's release, dragged through its own gate (counter landed). */
    public enum Step implements Phase { IDLE, TELL, BOLT, TURNED }

    /** 20 ticks of the open gate before the bolt: 12 following the target, then 8 locked. */
    public static final int TELL_TICKS = 20;
    public static final int TRACK_TICKS = 12;
    /** Ticks from one tell to the next, at the soonest. */
    public static final int CAST_EVERY = 80;
    /** It weaves at targets this close, and in its sight. */
    public static final double CAST_RANGE = 16.0;
    /** The bolt's hit on the first thing it meets, and the burst at its feet when a shield turns it back. */
    public static final float BOLT_DAMAGE = 6.0F;
    /** The gate opens this far behind (or beside) the target, across the ground. */
    public static final double GATE_NEAR = 3.0;
    public static final double GATE_FAR = 5.0;
    /** The gate's ring: its radius in blocks, upright. */
    public static final double RING_RADIUS = 0.7;
    /** The gate's middle stands at this share of the target's height, but never lower than its radius and a bit. */
    public static final double CHEST = 0.65;
    public static final double MIN_LIFT = RING_RADIUS + 0.2;
    /** The bolt: blocks a tick, how far it flies from the gate (or from its hands with no gate), how close it catches. */
    public static final double BOLT_SPEED = 0.6;
    public static final double BOLT_RANGE = 12.0;
    public static final double STRAIGHT_RANGE = 20.0;
    public static final double BOLT_RADIUS = 0.3;
    /** The release: it throws its ring forward and the gate closes over this many ticks. */
    public static final int BOLT_TICKS = 10;
    /** Dragged through its gate: it tumbles out and gets up over this many ticks. */
    public static final int TURNED_TICKS = 20;
    /** It backs off from a target closer than this, and walks toward one farther than this or out of sight. */
    public static final double KEEP_AWAY = 5.0;
    public static final double CLOSE_IN = 12.0;
    /** A spot to back off toward: this far out and up or down; the short search when no far spot is open. */
    public static final int BACK_SEARCH = 8, BACK_SEARCH_Y = 3, BACK_SHORT = 4, BACK_SHORT_Y = 2;

    /** Rift violet and its lilac-white core: the gate, the bolt and the held ring. Well off weak-point gold. */
    public static final int RIFT_COLOR = 0xA64DFF;
    public static final int CORE_COLOR = 0xECD4FF;
    private static final DustParticleOptions RIM = new DustParticleOptions(RIFT_COLOR, 1.0F);
    private static final DustParticleOptions CORE = new DustParticleOptions(CORE_COLOR, 0.7F);
    private static final DustParticleOptions LANE = new DustParticleOptions(0xC79EE8, 0.5F);
    private static final DustParticleOptions STREAK = new DustParticleOptions(RIFT_COLOR, 1.3F);
    private static final int RIM_POINTS = 18;

    private int castCooldown;
    private int tells, bolts, hits, turns, straight;
    /** The open gate's middle, or null with no gate (a straight bolt, or no tell under way). */
    private @Nullable Vec3 gate;
    /** Where the gate (or its hands, with no gate) aims: the target's middle, followed until the lock, then held. */
    private @Nullable Vec3 aimAt;
    private boolean locked;
    /** The bolt in flight, and where it set out from. */
    private @Nullable Shot bolt;
    private Vec3 boltFrom = Vec3.ZERO;
    private @Nullable LivingEntity lastStruck;
    private @Nullable DamageSource lastSource;

    private final Shot.Rules boltRules = new Shot.Rules(this::boltCanHit, this::drawBolt, this::boltHit, this::boltStop);

    public Gatecaster(EntityType<? extends Gatecaster> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // it notices targets as far off as it weaves
        return TeemingAttributes.monster("gatecaster", 0.25).add(Attributes.FOLLOW_RANGE, CAST_RANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new KeepRangeGoal());
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * With a target it keeps its distance: it backs off from one closer than {@link #KEEP_AWAY}, walks toward one
     * beyond {@link #CLOSE_IN} or out of its sight, and otherwise stands and watches.
     */
    private final class KeepRangeGoal extends Goal {
        private int repath;

        KeepRangeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return core().idle() && valid(getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--repath > 0) return;
            repath = 10;
            double d = distanceTo(target);
            if (d < KEEP_AWAY) {
                Vec3 spot = position().add(awayFrom(target.position()).scale(KEEP_AWAY + 1.0 - d));
                getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
            } else if (d > CLOSE_IN || !hasLineOfSight(target)) {
                getNavigation().moveTo(target, 1.0);
            } else {
                getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (castCooldown > 0) castCooldown--;
        if (core().in(Step.TELL)) {
            weaveTick(level, core().target());
        } else if (core().in(Step.BOLT)) {
            holdStill();
            if (gate != null) drawGate(level, core().elapsed(), true);
        } else if (core().in(Step.TURNED)) {
            holdStill();
        } else if (castCooldown == 0 && canCast(getTarget())) {
            weave(getTarget());
        }
    }

    /** The bolt flies on whatever its caster does, until it hits something or stops. */
    @Override
    public void tick() {
        super.tick();
        if (bolt != null && level() instanceof ServerLevel level && bolt.step(level, this, BOLT_RADIUS, boltRules)) bolt = null;
    }

    /** True when {@code target} is a fair mark: alive, within 16 blocks and in its sight. */
    public boolean canCast(@Nullable LivingEntity target) {
        return valid(target) && distanceTo(target) <= CAST_RANGE && hasLineOfSight(target);
    }

    /**
     * Starts the tell at {@code target}: it faces the target and holds that facing, its hands glow, and the gate opens
     * behind the target (or beside it, or nowhere). True if it started. Public as a test hook.
     */
    public boolean weave(@Nullable LivingEntity target) {
        if (target == null || !core().idle() || castCooldown > 0 || !(level() instanceof ServerLevel level)) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        castCooldown = CAST_EVERY;
        tells++;
        holdStill();
        face(target);
        locked = false;
        aimAt = target.getBoundingBox().getCenter();
        gate = findGate(level, target);
        showGate();
        Vec3 e = position().add(0.0, getBbHeight() * 0.55, 0.0);
        level.playSound(null, e.x, e.y, e.z, ModSounds.GATECASTER_TELL.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, e.x, e.y, e.z, 16, 0.4, 0.3, 0.4, 0.05);
        if (gate != null) {
            level.playSound(null, gate.x, gate.y, gate.z, ModSounds.GATECASTER_WHINE.get(), getSoundSource(), 1.0F, 0.7F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, gate.x, gate.y, gate.z, 20, 0.3, 0.3, 0.3, 0.1);
        }
        return true;
    }

    /**
     * The tell: it holds still, its hands glow, the gate crackles and whines higher and higher. For the first 12 ticks
     * its aim follows the target's middle; then it locks there with a click, and a faint lane shows the line of fire.
     * Nothing happens to the target yet.
     */
    private void weaveTick(ServerLevel level, @Nullable LivingEntity target) {
        holdStill();
        int t = core().elapsed();
        if (valid(target)) getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (!locked && aimAt != null) {
            if (t >= TRACK_TICKS) {
                lock(level);
            } else if (valid(target)) {
                aimAt = target.getBoundingBox().getCenter();
                showGate();
            }
        }
        if (t % 2 == 0) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 hand = handPoint(side);
                level.sendParticles(CORE, true, true, hand.x, hand.y, hand.z, 1, 0.05, 0.05, 0.05, 0.0);
                if (t % 4 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, hand.x, hand.y, hand.z, 1, 0.05, 0.05, 0.05, 0.02);
            }
        }
        if (gate == null) return;
        drawGate(level, t, false);
        if (t % 4 == 0 && t > 0) {
            level.playSound(null, gate.x, gate.y, gate.z, ModSounds.GATECASTER_WHINE.get(), getSoundSource(),
                    1.0F, 0.7F + 0.06F * t);                                         // a whine that climbs
        }
        if (locked && t % 2 == 0 && aimAt != null) drawLane(level);
    }

    /** The aim locks where the target's middle last was: a click at the gate, and the lane of fire. */
    private void lock(ServerLevel level) {
        locked = true;
        Vec3 at = gate != null ? gate : ringPoint();
        level.playSound(null, at.x, at.y, at.z, ModSounds.GATECASTER_LOCK.get(), getSoundSource(), 1.0F, 1.0F);
        showGate();
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
            return;
        }
        if (ended == Step.TELL) {
            if (aimAt == null) {
                rest();
                return;
            }
            if (!locked) lock(level);
            if (startPhase(Step.BOLT, BOLT_TICKS, core().target())) fire(level);
            else rest();
        } else {
            rest();
        }
    }

    // ---- the gate: where it opens, how it looks ----------------------------------------------------------------------

    /**
     * Where the gate opens for {@code target}: the first open spot 3-5 blocks behind it as it faces now, then at its
     * sides ({@link Gate#offsets}), at its chest height. A spot counts when a ring-sized box there holds no block or
     * liquid, it has a clear line to the target's middle, and clients can be told where it is. Null when none counts.
     * Public as a test hook.
     */
    public @Nullable Vec3 findGate(ServerLevel level, LivingEntity target) {
        Vec3 centre = target.getBoundingBox().getCenter();
        double y = target.getY() + Math.max(target.getBbHeight() * CHEST, MIN_LIFT);
        double[] xz = Gate.offsets(target.getYHeadRot(), target.getX() - getX(), target.getZ() - getZ());
        for (int i = 0; i < xz.length; i += 2) {
            Vec3 at = new Vec3(target.getX() + xz[i], y, target.getZ() + xz[i + 1]);
            if (openAir(level, at) && clearLine(level, at, centre) && encode(at, centre) != Gate.NONE) return at;
        }
        return null;
    }

    /** True when an upright ring of {@link #RING_RADIUS} fits at {@code at}: a box that size holds no block or liquid. */
    private boolean openAir(ServerLevel level, Vec3 at) {
        AABB box = new AABB(at, at).inflate(RING_RADIUS + 0.1);
        return level.noCollision(box) && !level.containsAnyLiquid(box);
    }

    private boolean clearLine(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this)).getType() == HitResult.Type.MISS;
    }

    /** The gate's flags for clients: its offset from the caster in the caster's frame and the way it faces. */
    private int encode(Vec3 at, Vec3 aim) {
        double s = getScale();
        Vec3 o = at.subtract(position());
        float body = getYRot();
        double fx = -Mth.sin(body * Mth.DEG_TO_RAD), fz = Mth.cos(body * Mth.DEG_TO_RAD);
        double forward = (o.x * fx + o.z * fz) / s;
        double left = (o.x * fz - o.z * fx) / s;
        Vec3 n = aim.subtract(at);
        double yaw = Math.toDegrees(Math.atan2(-n.x, n.z));
        return Gate.encode(forward, left, o.y / s, yaw - body);
    }

    /** Tells clients where the gate is and which way it faces (or that there is none). */
    private void showGate() {
        int flags = gate == null || aimAt == null ? 0 : encode(gate, aimAt);
        if (flags == Gate.NONE) flags = 0;
        int old = core().flags();
        for (int bit = 0; bit < Gate.BITS; bit++) {
            boolean on = (flags >> bit & 1) != 0;
            if ((old >> bit & 1) != (on ? 1 : 0)) core().setFlag(bit, on);
        }
    }

    /**
     * The gate as particles, every other tick: a turning rim of violet dust round the ring, a lilac core ring once it
     * is locked, portal light drawn into its middle, and star-white crackles at its edge. {@code closing}: the bolt is
     * out and the ring shrinks shut.
     */
    private void drawGate(ServerLevel level, int t, boolean closing) {
        if (gate == null || aimAt == null || t % 2 != 0) return;
        Vec3 n = aimAt.subtract(gate).multiply(1, 0, 1);
        if (n.lengthSqr() < 1.0E-6) n = new Vec3(0, 0, 1);
        n = n.normalize();
        Vec3 right = new Vec3(n.z, 0.0, -n.x);
        double r = RING_RADIUS * (closing ? Math.max(0.0, 1.0 - t / (double) BOLT_TICKS) : Math.min(1.0, 0.3 + t / 5.0));
        double turn = tickCount * 0.12;
        for (int i = 0; i < RIM_POINTS; i++) {
            double a = turn + i * (Math.PI * 2 / RIM_POINTS);
            Vec3 p = gate.add(right.scale(Math.cos(a) * r)).add(0.0, Math.sin(a) * r, 0.0);
            level.sendParticles(RIM, true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (locked || closing) {
            for (int i = 0; i < 10; i++) {
                double a = -turn + i * (Math.PI * 2 / 10);
                Vec3 p = gate.add(right.scale(Math.cos(a) * r * 0.65)).add(0.0, Math.sin(a) * r * 0.65, 0.0);
                level.sendParticles(CORE, true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, gate.x, gate.y, gate.z, 3, r * 0.4, r * 0.4, r * 0.4, 0.02);
        if (random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 p = gate.add(right.scale(Math.cos(a) * r)).add(0.0, Math.sin(a) * r, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);   // a crackle
        }
    }

    /** The locked lane: faint lilac motes from the gate to where it aims, and a little past. */
    private void drawLane(ServerLevel level) {
        if (gate == null || aimAt == null) return;
        Vec3 d = aimAt.subtract(gate);
        double length = d.length();
        if (length < 1.0E-3) return;
        Vec3 dir = d.scale(1.0 / length);
        for (double run = 0.8; run < length + 1.0; run += 0.7) {
            Vec3 p = gate.add(dir.scale(run));
            level.sendParticles(LANE, true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ---- the bolt --------------------------------------------------------------------------------------------------

    /** The bolt flies out of the gate along the locked line, or straight from its ring with no gate. */
    private void fire(ServerLevel level) {
        if (aimAt == null) return;
        Vec3 from = gate != null ? gate : ringPoint();
        Vec3 dir = aimAt.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) dir = Vec3.directionFromRotation(0.0F, getYRot());
        if (!MechanicEvent.post(this, "bolt", core().target())) return;
        boltFrom = from;
        lastStruck = null;
        lastSource = null;
        bolt = new Shot(Shot.Path.line(from, dir, BOLT_SPEED, gate != null ? BOLT_RANGE : STRAIGHT_RANGE));
        bolts++;
        if (gate == null) straight++;
        level.playSound(null, from.x, from.y, from.z, ModSounds.GATECASTER_BOLT.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, from.x, from.y, from.z, 14, 0.15, 0.15, 0.15, 0.25);
        Vec3 ring = ringPoint();
        level.sendParticles(CORE, true, true, ring.x, ring.y, ring.z, 8, 0.2, 0.2, 0.2, 0.0);   // the throw into its own ring
    }

    private boolean boltCanHit(LivingEntity e) {
        return e != this && !(e instanceof Gatecaster) && e.isAlive() && e.canBeHitByProjectile() && !e.isSpectator()
                && !(e instanceof Player p && p.isCreative());
    }

    /** The bolt as players see it: a thick violet streak along the stretch it flew this tick, a white-hot head. */
    private void drawBolt(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length > 1.0E-3) {
            Vec3 dir = d.scale(1.0 / length);
            for (double run = 0.0; run < length; run += 0.2) {
                Vec3 p = from.add(dir.scale(run));
                level.sendParticles(STREAK, true, true, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
        level.sendParticles(CORE, true, true, to.x, to.y, to.z, 3, 0.05, 0.05, 0.05, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, true, true, to.x, to.y, to.z, 1, 0.02, 0.02, 0.02, 0.01);
    }

    /**
     * The bolt meets a hitbox: 6 as a mob's projectile from where it set out, with no knockback. A shield raised toward
     * the gate takes it, and the bolt is turned back through the gate.
     */
    private void boltHit(ServerLevel level, Shot shot, LivingEntity victim, Vec3 at) {
        lastStruck = victim;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.15);
        level.playSound(null, at.x, at.y, at.z, ModSounds.GATECASTER_HIT.get(), getSoundSource(), 1.0F, 1.0F);
        if (!MechanicEvent.post(this, "bolt_hit", victim)) return;
        DamageSource source = boltSource(boltFrom);
        lastSource = source;
        boolean shielded = shieldTakes(victim, source, BOLT_DAMAGE);
        float before = victim.getHealth() + victim.getAbsorptionAmount();
        AstralAugur.GroundMarks.hurtInPlace(level, victim, source, BOLT_DAMAGE);
        if (victim.getHealth() + victim.getAbsorptionAmount() < before) hits++;
        if (shielded) turnBack(level, victim);
    }

    /** The bolt hits nothing: a block stopped it or it flew its full length. It fizzles. */
    private void boltStop(ServerLevel level, Shot shot, Vec3 at, boolean blocked) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.05);
    }

    /** A hit from the bolt: a mob's projectile with the caster as its cause, coming from {@code from} (the gate). */
    public DamageSource boltSource(Vec3 from) {
        return new FromGate(damageSources().mobProjectile(this, this).typeHolder(), this, from);
    }

    /**
     * A damage source that comes from a point other than its cause: the gate. A shield blocks by where a hit comes
     * from, so only one raised toward the gate takes the bolt.
     */
    private static final class FromGate extends DamageSource {
        private final Vec3 from;

        FromGate(Holder<DamageType> type, Entity caster, Vec3 from) {
            super(type, caster, caster);
            this.from = from;
        }

        @Override
        public Vec3 getSourcePosition() {
            return from;
        }

        @Override
        public Vec3 sourcePositionRaw() {
            return from;
        }
    }

    /**
     * Whether {@code victim}'s raised shield takes all of {@code amount} from {@code source}: held up, not bypassed,
     * and turned toward where the hit comes from (the shield's own rule, read without using it).
     */
    public static boolean shieldTakes(LivingEntity victim, DamageSource source, float amount) {
        ItemStack stack = victim.getItemBlockingWith();
        if (stack == null) return false;
        BlocksAttacks blocks = stack.get(DataComponents.BLOCKS_ATTACKS);
        if (blocks == null || blocks.bypassedBy().map(set -> set.contains(source.typeHolder())).orElse(false)) return false;
        Vec3 at = source.getSourcePosition();
        double angle = Math.PI;
        if (at != null) {
            Vec3 view = Vec3.directionFromRotation(0.0F, victim.getYHeadRot());
            Vec3 to = at.subtract(victim.position());
            to = new Vec3(to.x, 0.0, to.z).normalize();
            angle = Math.acos(Mth.clamp(to.dot(view), -1.0, 1.0));
        }
        return blocks.resolveBlockedDamage(source, amount, angle) >= amount - 1.0E-4F;
    }

    // ---- turned back: the counter's reward ---------------------------------------------------------------------------

    /**
     * The counter landed: the bolt goes back through the gate, the gate collapses and drags the caster after it. It
     * lands on safe ground under where the gate stood, facing the one who blocked, and its own bolt bursts at its feet.
     * With no safe ground there (or no gate) it stays where it is and the burst finds it there.
     */
    private void turnBack(ServerLevel level, LivingEntity blocker) {
        Vec3 through = gate;
        if (!startPhase(Step.TURNED, TURNED_TICKS, blocker)) return;
        turns++;
        bolt = null;
        level.playSound(null, blocker.getX(), blocker.getEyeY(), blocker.getZ(), ModSounds.GATECASTER_TURNED.get(), getSoundSource(), 1.2F, 1.0F);
        Vec3 from = position();
        if (through != null) {
            double feet = through.y - Math.max(blocker.getBbHeight() * CHEST, MIN_LIFT);
            Vec3 to = Riftjackal.SafeTeleport.InLevel.spot(level, this, through.x, feet, through.z);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, through.x, through.y, through.z, 30, 0.3, 0.3, 0.3, 0.3);
            if (to != null) {
                level.sendParticles(ParticleTypes.PORTAL, true, true, from.x, from.y + 1.0, from.z, 30, 0.3, 0.6, 0.3, 0.4);
                Riftjackal.SafeTeleport.InLevel.move(level, this, to);
            }
        }
        gate = null;
        aimAt = null;
        showGate();
        face(blocker);
        burst(level, blocker);
    }

    /** Its own bolt bursts at its feet: 6, with the one who turned it back as the cause, past any hurt cooldown. */
    private void burst(ServerLevel level, LivingEntity blocker) {
        Vec3 at = position();
        level.sendParticles(STREAK, true, true, at.x, at.y + 0.3, at.z, 24, 0.5, 0.2, 0.5, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, at.x, at.y + 0.3, at.z, 30, 0.4, 0.2, 0.4, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y + 0.3, at.z, 10, 0.4, 0.2, 0.4, 0.08);
        level.playSound(null, at.x, at.y, at.z, ModSounds.GATECASTER_BURST.get(), getSoundSource(), 1.2F, 1.0F);
        damageCooldownTime = 0;
        hurtServer(level, damageSources().indirectMagic(this, blocker), BOLT_DAMAGE);
    }

    private void rest() {
        gate = null;
        aimAt = null;
        locked = false;
        showGate();
        core().end();
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        bolt = null;
        gate = null;
        super.onRemoval(reason);
    }

    // ---- where things are on its body ------------------------------------------------------------------------------

    /** Its held ring: before its chest, turned with its body. */
    public Vec3 ringPoint() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
        double s = getScale();
        return position().add(0.0, 0.91 * s, 0.0).add(forward.scale(0.58 * s));
    }

    /** One hand on its ring, {@code side} -1 right, 1 left. */
    private Vec3 handPoint(int side) {
        Vec3 left = Vec3.directionFromRotation(0.0F, yBodyRot - 90.0F);
        return ringPoint().add(left.scale(0.45 * side * getScale()));
    }

    /**
     * The box the renderer culls it by: while a gate is open it reaches the gate, so the gate stays drawn while the
     * caster itself is off screen.
     */
    @Override
    public AABB getInterpolatedBoundingBox(float partialTick) {
        AABB box = super.getInterpolatedBoundingBox(partialTick);
        if (core() == null || core().idle()) return box;
        int flags = core().flags();
        if (!Gate.live(flags)) return box;
        double s = getScale();
        float body = yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(body), fz = Mth.cos(body);
        double f = Gate.forward(flags) * s, l = Gate.left(flags) * s;
        Vec3 at = box.getBottomCenter().add(fx * f + fz * l, Gate.up(flags) * s, fz * f - fx * l);
        return box.minmax(new AABB(at, at).inflate(RING_RADIUS * s + 0.3));
    }

    // ---- moving and facing -----------------------------------------------------------------------------------------

    /** Through the tell and after it its body faces exactly the way it was turned, so the gate's offset holds still. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    private void face(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    /**
     * Which way to back off from {@code threat}: toward an open spot away from it, or straight away when none is open
     * (by a wall) or the spot found lies off to the side.
     */
    private Vec3 awayFrom(Vec3 threat) {
        Vec3 away = position().subtract(threat).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0.0F, getYRot()).scale(-1.0);
        away = away.normalize();
        Vec3 spot = DefaultRandomPos.getPosAway(this, BACK_SEARCH, BACK_SEARCH_Y, threat);
        if (spot == null) spot = DefaultRandomPos.getPosAway(this, BACK_SHORT, BACK_SHORT_Y, threat);
        Vec3 dir = spot == null ? away : spot.subtract(position()).horizontal();
        return dir.lengthSqr() < 1.0E-4 || dir.normalize().dot(away) < 0.7 ? away : dir.normalize();
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Ticks until it may weave again. Public as a test hook. */
    public int castCooldown() {
        return castCooldown;
    }

    /** Lets it weave again at once. Public as a test hook. */
    public void resetCooldown() {
        castCooldown = 0;
    }

    /** The open gate's middle, or null. Public as a test hook. */
    public @Nullable Vec3 gate() {
        return gate;
    }

    /** Where it aims now. Public as a test hook. */
    public @Nullable Vec3 aimAt() {
        return aimAt;
    }

    /** Whether the aim is locked. Public as a test hook. */
    public boolean locked() {
        return locked;
    }

    /** The bolt in flight, or null; and where the last bolt set out from. Public as test hooks. */
    public @Nullable Shot bolt() {
        return bolt;
    }

    public Vec3 boltFrom() {
        return boltFrom;
    }

    /** What the last bolt met, and the damage source it hit with. Public as test hooks. */
    public @Nullable LivingEntity lastStruck() {
        return lastStruck;
    }

    public @Nullable DamageSource lastSource() {
        return lastSource;
    }

    /** Tells, bolts fired, bolts that hurt, bolts turned back, bolts thrown with no gate. Public as test hooks. */
    public int tells() {
        return tells;
    }

    public int bolts() {
        return bolts;
    }

    public int hits() {
        return hits;
    }

    public int turns() {
        return turns;
    }

    public int straightBolts() {
        return straight;
    }

    // ---- save and load -----------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("CastCooldown", castCooldown);
    }

    /** The cast cooldown survives a reload; a gate or a bolt under way does not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        castCooldown = Math.clamp(input.getIntOr("CastCooldown", 0), 0, CAST_EVERY);
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.GATECASTER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GATECASTER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GATECASTER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.15F;
    }

    // ---- the gate's place (pure maths, no game types) ----------------------------------------------------------------

    /**
     * Where a gate may open round its target, and how its place is packed into the synced flags, with no game types.
     *
     * <p>Flags: bit 0 says a gate is open; bits 1-8 its distance ahead of the caster (quarter blocks, signed); bits
     * 9-14 to the caster's left (quarter blocks, signed); bits 15-19 above the caster's feet (half blocks, signed); bits
     * 20-23 which way the ring faces, in sixteenths of a turn from the caster's facing. All at scale 1.
     */
    public static final class Gate {
        /** Distances tried, in order: the middle of 3-5 blocks first, then the far end, then the near end. */
        public static final double[] DISTANCES = {4.0, 5.0, 3.0};
        /** Flag bits the gate uses, from bit 0. */
        public static final int BITS = 24;
        /** {@link #encode} could not pack the place: out of reach of the flags. */
        public static final int NONE = -1;
        public static final int LIVE_BIT = 0, FORWARD_BIT = 1, LEFT_BIT = 9, UP_BIT = 15, YAW_BIT = 20;
        private static final int FORWARD_SIZE = 8, LEFT_SIZE = 6, UP_SIZE = 5, YAW_SIZE = 4;
        public static final int YAW_STEPS = 1 << YAW_SIZE;

        private Gate() {}

        /**
         * The x, z offsets from the target to try, in order: each of {@link #DISTANCES} straight behind the way a body
         * with head yaw {@code yawDeg} faces, then each at its side farther from the caster (the caster is
         * {@code -towardX, -towardZ} from the target; on a tie, its left), then each at its other side.
         */
        public static double[] offsets(float yawDeg, double towardX, double towardZ) {
            double yaw = Math.toRadians(yawDeg);
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double lx = Math.cos(yaw), lz = Math.sin(yaw);
            double leftDot = lx * towardX + lz * towardZ;
            int first = leftDot >= -1.0E-6 ? 1 : -1;
            double[][] dirs = {{-fx, -fz}, {first * lx, first * lz}, {-first * lx, -first * lz}};
            double[] out = new double[dirs.length * DISTANCES.length * 2];
            int n = 0;
            for (double[] d : dirs) {
                for (double dist : DISTANCES) {
                    out[n++] = d[0] * dist;
                    out[n++] = d[1] * dist;
                }
            }
            return out;
        }

        /**
         * Packs a gate {@code forward} blocks ahead of the caster, {@code left} to its left and {@code up} above its feet,
         * its ring facing {@code yawDeg} degrees round from the caster's facing; {@link #NONE} when it is out of reach.
         */
        public static int encode(double forward, double left, double up, double yawDeg) {
            int f = (int) Math.round(forward * 4.0), l = (int) Math.round(left * 4.0), u = (int) Math.round(up * 2.0);
            if (!fits(f, FORWARD_SIZE) || !fits(l, LEFT_SIZE) || !fits(u, UP_SIZE)) return NONE;
            int y = Math.floorMod((int) Math.round(yawDeg / (360.0 / YAW_STEPS)), YAW_STEPS);
            return 1 << LIVE_BIT | (f & mask(FORWARD_SIZE)) << FORWARD_BIT | (l & mask(LEFT_SIZE)) << LEFT_BIT
                    | (u & mask(UP_SIZE)) << UP_BIT | y << YAW_BIT;
        }

        private static boolean fits(int v, int bits) {
            return v >= -(1 << bits - 1) && v < 1 << bits - 1;
        }

        private static int mask(int bits) {
            return (1 << bits) - 1;
        }

        private static int signed(int flags, int bit, int bits) {
            int v = flags >> bit & mask(bits);
            return v >= 1 << bits - 1 ? v - (1 << bits) : v;
        }

        /** From flags: whether a gate is open. */
        public static boolean live(int flags) {
            return (flags >> LIVE_BIT & 1) != 0;
        }

        /** From flags: blocks ahead of the caster, at scale 1. */
        public static double forward(int flags) {
            return signed(flags, FORWARD_BIT, FORWARD_SIZE) / 4.0;
        }

        /** From flags: blocks to the caster's left, at scale 1. */
        public static double left(int flags) {
            return signed(flags, LEFT_BIT, LEFT_SIZE) / 4.0;
        }

        /** From flags: blocks above the caster's feet, at scale 1. */
        public static double up(int flags) {
            return signed(flags, UP_BIT, UP_SIZE) / 2.0;
        }

        /** From flags: degrees the ring faces round from the caster's facing (clockwise seen from above, as yaw runs). */
        public static double yaw(int flags) {
            return (flags >> YAW_BIT & mask(YAW_SIZE)) * (360.0 / YAW_STEPS);
        }
    }
}
