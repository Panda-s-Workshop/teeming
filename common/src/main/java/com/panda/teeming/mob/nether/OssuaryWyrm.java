package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Ossuary Wyrm: a long serpent of fused vertebrae that swims through soul sand and soul soil as if they were water
 * (no block ever changes: it is an entity passing through them). Submerged it travels at 0.3 blocks a tick, and all
 * that shows is a trail of soul sparks on the surface. It hunts survival players within 16 blocks: it swims to a spot
 * 6.4 blocks from its prey, and there the ground rumbles for a second while soul flames rise where it will break out,
 * the tips of its horns breaking the surface (the tell). Then it leaps out in half a circle aimed where its prey stood
 * when the rumble began: the skull leads, comes down on its prey at the end of the arc and bites (its attack damage),
 * and the body follows the skull through the same arc and back under.
 *
 * <p>It is one mob with nine hitboxes: the skull and eight vertebrae, every blow going to the one health pool. A blow
 * on the skull counts double; a blow on a vertebra counts half. Counter: step out of its line while the flames rise
 * and the skull bites the sand; hit the skull while it is out (it glows gold then). It can swim through nothing else,
 * so it never follows anyone off the soul sand; stranded on anything else it drags itself back to the nearest.
 */
public class OssuaryWyrm extends TeemingMonster {
    /** IDLE (swimming, or stranded), the rumble (tell), the arc out of the sand, the body following the skull back under. */
    public enum Step implements Phase { IDLE, TELL, ARC, FOLLOW }

    public static final int TELL_TICKS = 20;              // the ground rumbles where it will break out for 1 s
    public static final double SWIM_SPEED = 0.3;          // blocks a tick through soul sand and soul soil
    public static final float SKULL_DAMAGE = 2.0F;        // a blow on the skull counts double
    public static final float SEGMENT_DAMAGE = 0.5F;      // a blow on a vertebra counts half
    public static final int SEGMENTS = 8;                 // vertebrae behind the skull
    public static final double SEGMENT_SIZE = 0.8;        // each part's hitbox across (skull and vertebrae), and the spacing of their centres
    public static final double HUNT_RANGE = 16.0;         // hunts survival players this close
    public static final double ARC_RADIUS = 3.5;          // the leap is half a circle: up 2.5 blocks, back under 7 blocks on
    public static final double DEPTH = 1.0;               // its body's centre line swims this far under the surface
    public static final double BITE_REACH = 6.37;         // the skull comes down at chest height this far from where it broke out
    public static final int ARC_TICKS = 22;               // from breaking out until the skull is back under, about 0.44 b/t
    public static final int FOLLOW_TICKS = 15;            // the body following the skull back under
    public static final int COOLDOWN = 60;                // ticks from coming down to the next rumble, at the soonest
    public static final double WANDER_SPEED = 0.1;        // cruising with nothing to hunt
    public static final double CRAWL_SPEED = 0.08;        // stranded on anything else, it drags itself back to soul sand
    /** {@code core().flag} bits: under the surface; stranded on something it can't swim through. */
    public static final int SUBMERGED = 0, STRANDED = 1;

    private static final double STRIKE_SLACK = 0.35;      // close enough to its strike spot to start the rumble
    private static final double NOSE = 0.5;               // the column this far ahead must be soul sand too
    private static final double FALL = 0.4;               // blocks a tick it drops when nothing holds it up
    private static final int FIND_MEDIUM = 8;             // stranded, it looks for soul sand this far round
    private static final double[] TURNS = {0.0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2};
    private static final int[] AROUND = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6};   // strike spots tried round its prey, 30 degrees apart

    private boolean submerged;
    private double top = Double.NaN;                      // world height of the surface it swims under
    private int cooldown;
    private boolean bitten, snapped;
    private @Nullable Vec3 cruise;
    private @Nullable BlockPos crawlTo;

    public OssuaryWyrm(EntityType<? extends OssuaryWyrm> type, Level level) {
        super(type, level, Step.values());
        xpReward = 10;
        noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("ossuary_wyrm", 0.3);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new Cruise());
    }

    // ---- segment chain: one mob, nine hitboxes ---------------------------------------------------------------------
    // Self-contained. The body is the skull and SEGMENTS vertebrae, each an axis-aligned box SEGMENT_SIZE across round
    // a Joint on the chain. Where the joints lie follows only from synced state (the phase, its progress, the flags,
    // the facing and the scale), so the server's hitboxes and the client's drawn parts are the same joints without a
    // packet of their own. In the leap every part runs along one path (path(s)) a fixed spacing behind the skull, so
    // the body traces the skull's path exactly. The mob's own bounding box is the union of the boxes of the parts that
    // reach above the surface, so a blow on any part in the open reaches the mob (and a client can pick it); partHit
    // names the box a blow landed on. Parts under the surface are left out of it: they lie in solid soul sand where no
    // blow can reach them, and counting them would make the box reach 7 blocks through the ground, where blasts, area
    // effects and anything that clears entities from a space would find a mob nobody can see. No part is ever an
    // entity of its own: nothing can kill, save or despawn one apart from the mob.

    /** A part's centre in the chain's frame, in blocks: forward along the facing, to the mob's left, and up from its feet. */
    public record Joint(double forward, double left, double up) {}

    /** How far along the leap's path the skull is where the rumble begins (its horns just break the surface). */
    public static final double S_TELL = ARC_RADIUS * Math.asin((DEPTH - 0.05) / ARC_RADIUS);
    /** ... where the skull is back under the surface. */
    public static final double S_OUT = ARC_RADIUS * (Math.PI - Math.asin((DEPTH - 0.45) / ARC_RADIUS));
    /** ... where the last vertebra is back under. */
    public static final double S_END = S_OUT + SEGMENTS * SEGMENT_SIZE;
    /** ... where the skull comes down on its prey. */
    public static final double S_BITE = ARC_RADIUS * Math.acos((ARC_RADIUS - BITE_REACH) / ARC_RADIUS);

    /**
     * The path of the leap, {@code s} blocks along it from where it broke out: behind that (s below 0) the line it swam
     * in on under the surface; then half a circle up out of the sand and down 2 radii on, which carries on round under
     * the surface (it is never seen there).
     */
    public static Joint path(double s) {
        if (s <= 0.0) return new Joint(s, 0.0, -DEPTH);
        double a = s / ARC_RADIUS;
        return new Joint(ARC_RADIUS - ARC_RADIUS * Math.cos(a), 0.0, -DEPTH + ARC_RADIUS * Math.sin(a));
    }

    /** How far along the path the skull is, {@code progress} (0..1) through a step of the leap. */
    public static double headS(Phase step, float progress) {
        float p = Mth.clamp(progress, 0.0F, 1.0F);
        if (step == Step.TELL) return S_TELL * p * p * (3.0 - 2.0 * p);
        if (step == Step.ARC) return S_TELL + p * (S_OUT - S_TELL);
        if (step == Step.FOLLOW) return S_OUT + p * (S_END - S_OUT);
        return 0.0;
    }

    /**
     * The joints of the skull (index 0) and the vertebrae (1..SEGMENTS), unscaled: along the path in the leap; in a
     * straight line under the surface while it swims; in a line on the ground, the skull raised, while stranded; and
     * otherwise (a still mob, a trophy, a Field Guide portrait) coiled, the skull reared up and looking ahead.
     */
    public static Joint[] joints(Phase step, float progress, int flags) {
        Joint[] out = new Joint[SEGMENTS + 1];
        if (step == Step.TELL || step == Step.ARC || step == Step.FOLLOW) {
            double head = headS(step, progress);
            for (int i = 0; i <= SEGMENTS; i++) out[i] = path(head - i * SEGMENT_SIZE);
        } else if ((flags & 1 << SUBMERGED) != 0) {
            for (int i = 0; i <= SEGMENTS; i++) out[i] = new Joint(-i * SEGMENT_SIZE, 0.0, -DEPTH);
        } else if ((flags & 1 << STRANDED) != 0) {
            out[0] = new Joint(0.0, 0.0, 0.55);
            for (int i = 1; i <= SEGMENTS; i++) out[i] = new Joint(-i * SEGMENT_SIZE, 0.0, 0.4);
        } else {                                              // the coil: the same formula the rest pose is built from
            out[0] = new Joint(0.46, 0.0, 0.96);
            out[1] = new Joint(0.2, 0.0, 0.64);
            for (int k = 0; k < SEGMENTS - 1; k++) {
                double a = k * 0.84;
                out[k + 2] = new Joint(-0.95 + 0.95 * Math.cos(a), 0.95 * Math.sin(a), 0.4);
            }
        }
        return out;
    }

    /** This mob's joints now (server: the current tick; client: the last synced state). */
    public Joint[] joints() {
        return joints(core().phase(), core().progress(0.0F), core().flags());
    }

    /** The world hitboxes of the skull (index 0) and the vertebrae (1..SEGMENTS), for a mob standing at {@code at}. */
    private AABB[] boxes(Vec3 at) {
        Joint[] joints = joints();
        double k = getScale(), size = SEGMENT_SIZE * k;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);         // forward
        double lx = Mth.cos(yaw), lz = Mth.sin(yaw);          // the mob's left
        AABB[] out = new AABB[joints.length];
        for (int i = 0; i < joints.length; i++) {
            Joint j = joints[i];
            Vec3 c = new Vec3(at.x + (fx * j.forward() + lx * j.left()) * k, at.y + j.up() * k, at.z + (fz * j.forward() + lz * j.left()) * k);
            out[i] = AABB.ofSize(c, size, size, size);
        }
        return out;
    }

    /** The world hitboxes of the skull (index 0) and the vertebrae (1..SEGMENTS). Public as a test hook. */
    public AABB[] partBoxes() {
        return boxes(position());
    }

    /**
     * The union of the boxes of the parts that reach above the surface (its feet are at the surface in every pose): a
     * blow on any of them reaches the mob, and the renderer is culled by them. With all of it under, the skull's box.
     */
    @Override
    protected AABB makeBoundingBox(Vec3 at) {
        if (core() == null) return super.makeBoundingBox(at);   // the Entity constructor places it before the core exists
        AABB[] boxes = boxes(at);
        AABB all = null;
        for (AABB box : boxes) if (box.maxY > at.y) all = all == null ? box : all.minmax(box);
        return all != null ? all : boxes[0];
    }

    /** True while the skull is out of the sand: its centre above the surface it swims under. */
    public boolean skullOut() {
        return joints()[0].up() > 0.0;
    }

    /**
     * Which part a blow landed on: 0 the skull, 1..SEGMENTS a vertebra, or -1 for damage with no place (magic, fire, a
     * fall). A blow from something that looks (a player's swing, a mob's) is traced along its look, a projectile along
     * its flight, anything else (a blast) from where it came; the first box on the line, else the one nearest it.
     */
    public int partHit(DamageSource source) {
        AABB[] boxes = partBoxes();
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
            Vec3 at = source.getSourcePosition();
            if (at == null) return -1;
            int best = 0;
            for (int i = 1; i < boxes.length; i++) if (boxes[i].distanceToSqr(at) < boxes[best].distanceToSqr(at)) best = i;
            return best;
        }
        Vec3 to = from.add(dir.scale(16.0));
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < boxes.length; i++) {
            Optional<Vec3> hit = boxes[i].inflate(0.05).clip(from, to);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < bestD) {
                bestD = hit.get().distanceToSqr(from);
                best = i;
            }
        }
        if (best >= 0) return best;
        for (int i = 0; i < boxes.length; i++) {             // nothing on the line: the part nearest it
            Vec3 c = boxes[i].getCenter();
            double t = Math.max(0.0, c.subtract(from).dot(dir));
            double d = from.add(dir.scale(t)).distanceToSqr(c);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // ---- moving through soul sand ----------------------------------------------------------------------------------
    // Self-contained, like the Dunefin's sand: it moves only into columns of MEDIUM with nothing solid on top, at most a
    // block up or down; its feet stay at the surface and its body DEPTH under it, its collisions off.

    /** What it swims through: soul sand and soul soil. */
    public static final TagKey<Block> MEDIUM = BlockTags.SOUL_SPEED_BLOCKS;

    /** The top of a column of MEDIUM at (x, z) it can swim under, a block higher or lower than {@code from} at most. NaN if none. */
    private double topAt(double x, double z, double from) {
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(from) - 1;
        for (int dy : new int[] {0, 1, -1}) {
            BlockPos pos = new BlockPos(bx, by + dy, bz);
            if (isTop(pos)) return pos.getY() + 1.0;
        }
        return Double.NaN;
    }

    private boolean isTop(BlockPos pos) {
        Level level = level();
        if (!level.getBlockState(pos).is(MEDIUM)) return false;
        BlockPos above = pos.above();
        BlockState up = level.getBlockState(above);
        return !up.is(MEDIUM) && up.getCollisionShape(level, above).isEmpty() && up.getFluidState().isEmpty();
    }

    /** Standing on (or swimming under) soul sand or soul soil: the block under the feet. */
    public static boolean onMedium(Entity e) {
        return e.level().getBlockState(BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ())).is(MEDIUM);
    }

    private void enterMedium(double surface) {
        submerged = true;
        top = surface;
        crawlTo = null;
        core().setFlag(SUBMERGED, true);
        core().setFlag(STRANDED, false);
    }

    private void leaveMedium() {
        submerged = false;
        core().setFlag(SUBMERGED, false);
    }

    /** True while it swims under the surface (both sides). */
    public boolean submerged() {
        return core().flag(SUBMERGED);
    }

    /** Under the soul sand it is out of sight. */
    @Override
    public boolean hiddenFromSight() {
        return submerged();
    }

    /** Sinks it into the soul sand it is on at once; true if it did. Public as a test hook. */
    public boolean submerge() {
        double t = topAt(getX(), getZ(), getY() + 0.5);
        if (level().isClientSide() || Double.isNaN(t)) return false;
        enterMedium(t);
        setDeltaMovement(Vec3.ZERO);
        setPos(getX(), t, getZ());
        return true;
    }

    /** It moves itself, collisions off, by its own motion: no gravity, no friction, no pushing. */
    @Override
    public void travel(Vec3 input) {
        move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    public boolean isInWall() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    private Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---- prey ------------------------------------------------------------------------------------------------------

    /**
     * Prey it hunts: alive, within 16 blocks (or its follow range, if shorter), not another wyrm, not a player in
     * creative or spectator. It picks its own prey among survival players; a target given by a command follows the same rule.
     */
    public boolean huntable(LivingEntity e) {
        if (!e.isAlive() || e.isRemoved() || e.level() != level() || e == this || e instanceof OssuaryWyrm) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return distanceTo(e) <= huntRange();
    }

    private double huntRange() {
        return Math.min(HUNT_RANGE, getAttributeValue(Attributes.FOLLOW_RANGE));
    }

    private @Nullable LivingEntity hunt(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target != null && !huntable(target)) {
            setTarget(null);
            target = null;
        }
        if (target == null && submerged) {
            Player p = level.getNearestPlayer(getX(), getY(), getZ(), huntRange(), e -> e instanceof Player q && huntable(q));
            if (p != null) {
                setTarget(p);
                target = p;
            }
        }
        return target;
    }

    /**
     * Where it can rumble to strike {@code target}: a spot BITE_REACH from it, on soul sand, with soul sand where the arc
     * goes back under (just beyond the prey) and nothing solid in the arc's way. Tried round the prey from the side it
     * comes from; null if there is none (its prey stands off the soul sand). Public as a test hook.
     */
    public @Nullable Vec3 strikeSpot(LivingEntity target) {
        Vec3 t = target.position();
        double k = getScale();
        double base = Math.atan2(getZ() - t.z, getX() - t.x);
        for (int step : AROUND) {
            double a = base + step * (Math.PI / 6.0);
            double ux = Math.cos(a), uz = Math.sin(a);
            double ax = t.x + ux * BITE_REACH * k, az = t.z + uz * BITE_REACH * k;
            double topA = topAt(ax, az, t.y);
            if (Double.isNaN(topA)) continue;
            double back = 2.0 * ARC_RADIUS * k;
            if (Double.isNaN(topAt(ax - ux * back, az - uz * back, topA))) continue;
            Vec3 spot = new Vec3(ax, topA, az);
            if (clearArc(spot, new Vec3(-ux, 0.0, -uz))) return spot;
        }
        return null;
    }

    /** Nothing solid where the skull passes above the surface, for an arc from {@code from} along {@code dir}. */
    private boolean clearArc(Vec3 from, Vec3 dir) {
        double k = getScale();
        for (double s = S_TELL; s <= S_OUT; s += 1.0) {
            Joint j = path(s);
            if (j.up() < 0.1) continue;
            BlockPos pos = BlockPos.containing(from.x + dir.x * j.forward() * k, from.y + j.up() * k, from.z + dir.z * j.forward() * k);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) return false;
        }
        return true;
    }

    // ---- the AI step -----------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (!core().idle()) {
            if (core().in(Step.ARC)) arcTick(level);
            setDeltaMovement(Vec3.ZERO);                      // it holds its spot through the rumble and the leap
            return;
        }
        if (submerged) swimTick(level, hunt(level));
        else strandedTick(level);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        if (!level().isClientSide()) {
            if (submerged && Double.isNaN(top)) {             // loaded from a save: find the surface it swims under again
                double t = topAt(getX(), getZ(), getY() + 0.5);
                if (Double.isNaN(t)) leaveMedium();
                else enterMedium(t);
            }
            core().setFlag(SUBMERGED, submerged);
            core().setFlag(STRANDED, !submerged && !isNoAi() && core().idle());
            resetFallDistance();
            if (level() instanceof ServerLevel level) cues(level);
        }
        yBodyRot = getYRot();                                 // the chain lies along its facing, on both sides
        yHeadRot = getYRot();
        setBoundingBox(makeBoundingBox());                    // the joints move every tick even where it stands still
    }

    private void swimTick(ServerLevel level, @Nullable LivingEntity target) {
        double here = topAt(getX(), getZ(), top);
        if (Double.isNaN(here)) {
            if (!level.getBlockState(BlockPos.containing(getX(), top - 0.5, getZ())).is(MEDIUM)) {
                leaveMedium();                                // the soul sand round it is gone: it is stranded
                return;
            }
            here = top;                                       // covered over: it holds there until it swims out
        }
        top = here;
        Vec3 goal = null;
        Vec3 dir = Vec3.ZERO;
        double step = 0.0;
        boolean steer = false;
        if (target != null) {
            Vec3 spot = strikeSpot(target);
            if (spot != null && spot.subtract(position()).horizontalDistance() <= STRIKE_SLACK && cooldown == 0 && rumble(target)) return;
            goal = spot != null ? spot : target.position();   // with no spot it closes in as far as its soul sand goes
            steer = spot != null;
        } else if (cruise != null) {
            dir = cruise;
            step = WANDER_SPEED;
        }
        if (goal != null) {
            Vec3 to = goal.subtract(position());
            double d = to.horizontalDistance();
            if (d > 1.0E-3) {
                dir = new Vec3(to.x / d, 0.0, to.z / d);
                step = Math.min(SWIM_SPEED, d);
            }
        }
        Vec3 went = step > 0.0 ? swimStep(dir, step, steer) : Vec3.ZERO;
        if (went == Vec3.ZERO && target == null && cruise != null) cruise = cruise.yRot(Mth.PI * (0.5F + random.nextFloat()));
        if (went == Vec3.ZERO) step = 0.0;
        setDeltaMovement(went.x * step, Mth.clamp(top - getY(), -0.5, 0.5), went.z * step);
        if (target != null && step == 0.0) face(target.position().subtract(position()));
        else face(went);
        if (step > 0.0 && tickCount % 3 == 0) trail(level);
    }

    /** One step through soul sand toward {@code dir} (sliding round where it ends if {@code steer}); the heading taken, or zero. */
    private Vec3 swimStep(Vec3 dir, double step, boolean steer) {
        for (double turn : TURNS) {
            if (turn != 0.0 && !steer) break;
            Vec3 d = dir.yRot((float) turn);
            double nx = getX() + d.x * step, nz = getZ() + d.z * step;
            double next = topAt(nx, nz, top);
            if (Double.isNaN(next) || Double.isNaN(topAt(nx + d.x * NOSE, nz + d.z * NOSE, next))) continue;
            top = next;
            return d;
        }
        return Vec3.ZERO;
    }

    /** Soul sparks and a puff of soul sand where it passes under the surface. */
    private void trail(ServerLevel level) {
        level.sendParticles(ParticleTypes.SOUL, getX(), top + 0.05, getZ(), 1, 0.25, 0.0, 0.25, 0.01);
        BlockState sand = level.getBlockState(BlockPos.containing(getX(), top - 0.5, getZ()));
        if (sand.is(MEDIUM)) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand), getX(), top + 0.05, getZ(), 2, 0.3, 0.02, 0.3, 0.02);
    }

    // ---- the rumble, the arc and the bite --------------------------------------------------------------------------

    /**
     * Starts the tell aimed at {@code target}: 20 ticks of rumbling under the surface where it will break out, then the
     * arc, aimed where the target stands now. True if it started. Public as a test hook.
     */
    public boolean rumble(@Nullable LivingEntity target) {
        if (level().isClientSide() || !submerged || !core().idle()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        setDeltaMovement(Vec3.ZERO);
        if (target != null) face(target.position().subtract(position()));
        bitten = false;
        snapped = false;
        Telegraph.particles(ParticleTypes.SOUL_FIRE_FLAME, 16, 0.35, 0.0).withSpeed(0.03)
                .withSound(ModSounds.OSSUARY_WYRM_TELL.get(), 1.4F, 1.0F).play(this);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (isNoAi() || !(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            if (!startPhase(Step.ARC, ARC_TICKS, core().target())) {
                core().end();                                 // vetoed: it stays under
                return;
            }
            level.playSound(null, getX(), getY(), getZ(), ModSounds.OSSUARY_WYRM_BREACH.get(), getSoundSource(), 1.5F, 1.0F);
            burst(level, getX(), getZ(), 24, 0.3);
        } else if (ended == Step.ARC) {
            if (!startPhase(Step.FOLLOW, FOLLOW_TICKS, core().target())) land(level);
        } else if (ended == Step.FOLLOW) {
            land(level);
        } else {
            core().end();
        }
    }

    /** The skull's way through the arc: the bite when it meets its prey, a snap at nothing once it has passed it. */
    private void arcTick(ServerLevel level) {
        LivingEntity target = core().target();
        if (bitten || target == null) return;
        AABB skull = partBoxes()[0];
        if (target.isAlive() && skullOut() && skull.inflate(0.3).intersects(target.getBoundingBox())) {
            bite(level, target);
        } else if (!snapped && headS(Step.ARC, core().progress(0.0F)) > S_BITE + SEGMENT_SIZE) {
            snapped = true;                                   // stepped aside: the jaws close on nothing
            Vec3 c = skull.getCenter();
            level.playSound(null, c.x, c.y, c.z, ModSounds.OSSUARY_WYRM_SNAP.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /** The bite at the end of the arc: its attack damage. */
    private void bite(ServerLevel level, LivingEntity target) {
        bitten = true;
        if (!MechanicEvent.post(this, "bite", target)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        target.hurtServer(level, damageSources().mobAttack(this), damage);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.OSSUARY_WYRM_BITE.get(), getSoundSource(), 1.2F, 1.0F);
    }

    /** Back under: the skull went in 2 radii on, and it swims on from there; on anything but soul sand it is stranded there. */
    private void land(ServerLevel level) {
        Vec3 at = position().add(forward().scale(2.0 * ARC_RADIUS * getScale()));
        double t = topAt(at.x, at.z, getY());
        core().end();
        cooldown = COOLDOWN;
        setDeltaMovement(Vec3.ZERO);
        if (Double.isNaN(t)) {
            leaveMedium();
            setPos(at.x, getY(), at.z);
            return;
        }
        enterMedium(t);
        setPos(at.x, t, at.z);
        level.playSound(null, at.x, t, at.z, ModSounds.OSSUARY_WYRM_DIVE.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** The rumble's flames, and soul sand thrown up where the body breaks the surface in the leap (posed or live). */
    private void cues(ServerLevel level) {
        int t = core().elapsed();
        if (core().in(Step.TELL)) {
            if (t % 2 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.05, getZ(), 3, 0.3, 0.02, 0.3, 0.02);
                burst(level, getX(), getZ(), 2, 0.06);
            }
            if (t % 5 == 0) level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.1, getZ(), 2, 0.3, 0.05, 0.3, 0.03);
        } else if ((core().in(Step.ARC) || core().in(Step.FOLLOW)) && t % 2 == 0) {
            double k = getScale();
            double out = ARC_RADIUS - ARC_RADIUS * Math.cos(Math.asin(DEPTH / ARC_RADIUS));   // where the path crosses the surface
            Joint[] joints = joints();
            Vec3 f = forward();
            for (double u : new double[] {out, 2.0 * ARC_RADIUS - out}) {
                boolean crossing = false;
                for (Joint j : joints) crossing |= Math.abs(j.forward() - u) < 0.6 && Math.abs(j.up()) < 0.6;
                if (crossing) burst(level, getX() + f.x * u * k, getZ() + f.z * u * k, 4, 0.12);
            }
        }
    }

    /** Soul sand thrown up at (x, z) on the surface it swims under. */
    private void burst(ServerLevel level, double x, double z, int count, double speed) {
        BlockState sand = level.getBlockState(BlockPos.containing(x, getY() - 0.5, z));
        if (!sand.is(MEDIUM)) sand = Blocks.SOUL_SAND.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand), x, getY() + 0.1, z, count, 0.3, 0.05, 0.3, speed);
    }

    // ---- stranded --------------------------------------------------------------------------------------------------

    /** On anything it can't swim through: back under at once over soul sand, else it drags itself toward the nearest. */
    private void strandedTick(ServerLevel level) {
        double here = topAt(getX(), getZ(), getY() + 0.5);
        if (!Double.isNaN(here)) {
            enterMedium(here);
            setDeltaMovement(Vec3.ZERO);
            setPos(getX(), here, getZ());
            level.playSound(null, getX(), here, getZ(), ModSounds.OSSUARY_WYRM_DIVE.get(), getSoundSource(), 1.0F, 1.0F);
            burst(level, getX(), getZ(), 12, 0.1);
            return;
        }
        double ground = groundAt(getX(), getZ(), getY());
        double dy = Double.isNaN(ground) ? -FALL : Mth.clamp(ground - getY(), -FALL, 0.5);
        if (crawlTo == null || tickCount % 20 == 0) crawlTo = nearestMedium();
        Vec3 dir = Vec3.ZERO;
        double step = 0.0;
        if (crawlTo != null && !Double.isNaN(ground) && Math.abs(ground - getY()) < 0.05) {
            Vec3 to = Vec3.atBottomCenterOf(crawlTo).subtract(position()).multiply(1.0, 0.0, 1.0);
            double d = to.length();
            if (d > 1.0E-3) {
                dir = to.scale(1.0 / d);
                step = Math.min(CRAWL_SPEED, d);
                double next = groundAt(getX() + dir.x * step, getZ() + dir.z * step, getY());
                if (Double.isNaN(next) || Math.abs(next - getY()) > 1.0) step = 0.0;
            }
        }
        setDeltaMovement(dir.x * step, dy, dir.z * step);
        face(dir);
    }

    /** The top of the ground it can lie on at (x, z), at most a block over or 4 under {@code y}; NaN if none. */
    private double groundAt(double x, double z, double y) {
        Level level = level();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(y + 0.5); by >= Mth.floor(y) - 4; by--) {
            BlockPos pos = new BlockPos(bx, by, bz);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            BlockPos above = pos.above();
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) continue;
            return by + shape.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    private @Nullable BlockPos nearestMedium() {
        BlockPos at = blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FIND_MEDIUM, -2, -FIND_MEDIUM), at.offset(FIND_MEDIUM, 1, FIND_MEDIUM))) {
            if (!isTop(pos)) continue;
            double d = pos.distToCenterSqr(position());
            if (d < bestD) {
                bestD = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    // ---- hits ------------------------------------------------------------------------------------------------------

    /** A blow on the skull counts double, on a vertebra half; damage with no place counts as it is. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        int part = partHit(source);
        boolean skull = part == 0 && MechanicEvent.post(this, "skull_hit", source.getEntity() instanceof LivingEntity l ? l : null);
        float dealt = part > 0 ? amount * SEGMENT_DAMAGE : skull ? amount * SKULL_DAMAGE : amount;
        boolean hurt = super.hurtServer(level, source, dealt);
        if (hurt && skull) {
            Vec3 c = partBoxes()[0].getCenter();
            level.playSound(null, c.x, c.y, c.z, ModSounds.OSSUARY_WYRM_SKULL_HIT.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), c.x, c.y, c.z, 14, 0.25, 0.25, 0.25, 0.15);
        }
        return hurt;
    }

    // ---- state -----------------------------------------------------------------------------------------------------

    /** The skull is open to a double blow while it is out of the sand in the arc, or stranded on hard ground: it glows gold. */
    @Override
    public boolean vulnerable() {
        if (core().in(Step.ARC)) return skullOut();
        return core().idle() && core().flag(STRANDED);
    }

    /** Ticks until it may rumble again. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it rumble again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof OssuaryWyrm) && super.canAttack(target);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Submerged", submerged);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        submerged = input.getBooleanOr("Submerged", false);
        top = Double.NaN;                                     // found again on the first tick
    }

    /** With nothing to hunt it cruises under the soul sand: now and then a new heading, for two to four seconds. */
    private final class Cruise extends Goal {
        private int left;

        Cruise() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return submerged && core().idle() && getTarget() == null && random.nextInt(40) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && submerged && core().idle() && getTarget() == null;
        }

        @Override
        public void start() {
            left = 40 + random.nextInt(40);
            float a = random.nextFloat() * Mth.TWO_PI;
            cruise = new Vec3(Mth.cos(a), 0.0, Mth.sin(a));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            left--;
        }

        @Override
        public void stop() {
            cruise = null;
        }
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.OSSUARY_WYRM_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.OSSUARY_WYRM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.OSSUARY_WYRM_DEATH.get();
    }
}
