package com.panda.teeming.mob.end;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.end.Underclinger.Underside;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Abyss Angler: a flat, pale lurker pressed under the rim of an outer End island, its back to the stone, with one long
 * stalk from its head that ends in a glowing lantern. At rest the lantern dangles half a block under the island's edge,
 * just out past the rim's face: from the island above, it is all of the angler anyone sees.
 *
 * <p>When its target stands within 16 blocks of the lantern's casting spot (half a block over the edge and half a block
 * out from the rim's face) with nothing solid between them, it lures (its tell, 16 ticks): the stalk swings the lantern
 * up over the edge, it brightens and throws out rays of light, and a low cranking sound plays. Then it casts: a line
 * from the lantern to its target's body and a hook, which bites for its attack (armour counts). Hooked, the target is
 * reeled straight toward the lantern at 0.5 blocks a tick, across the ground (down a step of a block at most, up one of
 * 0.6), and stops at the brink: standing on the rim's last block, its toes at the edge. A bigger drop or a wall ahead
 * stops the reel where it is, so the reel never takes anyone over an edge: the fall is the victim's own step. At the
 * brink it holds its catch for 40 ticks, then lets go and waits 60 ticks before it lures again.
 *
 * <p>Counter: break its sight of the lantern before the cast, or cut the line. The line can be hit: a swing whose reach
 * passes within 0.3 blocks of it (by anyone), or a projectile whose flight crosses within 0.3 blocks of it, cuts it. A
 * cut line snaps back and yanks the angler out from under the rim: for 40 ticks it hangs stunned on the rim's face by
 * its jaw, its body against the stone just under the edge, in reach of anyone standing there, its gill flaps glowing
 * weak-point gold. Then it crawls back under. A rim more than 3 blocks thick is too thick to fish over; an angler under
 * the middle of an island crawls along the underside to the nearest rim it can fish over. Fallen to the ground it is
 * harmless and climbs back to an underside straight above it.
 */
public class AbyssAngler extends TeemingMonster {
    /**
     * On the ground (fallen), clinging under its rim, the lure (tell), reeling its catch in, holding it at the brink,
     * stunned on the rim's face after a cut line, climbing back up to a ceiling.
     */
    public enum Step implements Phase { IDLE, CLING, TELL, REEL, HOLD, STUN, CLIMB }

    public static final int TELL_TICKS = 16;              // the lure: the lantern over the edge before the cast
    public static final double CAST_RANGE = 16.0;         // the longest line, from the lantern's casting spot to the target's middle
    public static final double PULL_SPEED = 0.5;          // the reel, blocks a tick
    public static final float HOOK_HIT = 1.0F;            // x attack damage as the hook sets: 4 for its attack of 4
    public static final int HOLD_TICKS = 40;              // its catch held at the brink
    public static final int STUN_TICKS = 40;              // hanging stunned on the rim after a cut line
    public static final int COOLDOWN = 60;                // from a cut or a let-go to the next lure
    public static final double LINE_RADIUS = 0.3;         // a swing or a flight this close to the line cuts it
    public static final double LURE_FORWARD = 1.0;        // the casting spot, out from its middle: half a block past the rim's face
    public static final double LURE_UP = 0.5;             // and over the rim's top
    public static final int MAX_RIM = 3;                  // a rim thicker than this is too thick to fish over
    public static final double STUN_OUT = 1.1;            // stunned, its middle hangs this far out from its cell's: against the rim's face
    public static final double STEP_UP = 0.6;             // the reel takes its catch up a step this high at most
    public static final double STEP_DOWN = 1.0;           // and down one this deep at most; a bigger drop is an edge
    public static final double CRAWL_SPEED = 0.1;         // along the underside, blocks a tick
    public static final int CRAWL_RANGE = 12;             // it crawls to a rim at most this many blocks across
    public static final double SLIDE_SPEED = 0.3;         // how fast it moves out onto the rim's face and back under
    public static final int CLIMB_REACH = 6;              // fallen, it climbs to an underside at most this far above its back
    public static final double CLIMB_SPEED = 0.2;
    public static final int CLIMB_MAX_TICKS = 60;
    /** Prey wider or taller than this is too big to reel in. */
    public static final float MAX_PREY_WIDTH = 1.5F, MAX_PREY_HEIGHT = 2.5F;
    /** A catch found this far from where the reel left it (an ender pearl, a teleport) has got away. */
    public static final double ESCAPE_RANGE = 3.0;
    public static final double WALK_SPEED = 0.15;
    public static final int XP = 12;

    /** Synced flags: the line is out; the rim's thickness (1..3); the line's far end from the casting spot, in quarter blocks. */
    public static final int FLAG_LINE = 0;
    private static final int THICK_SHIFT = 1, FORWARD_SHIFT = 3, LEFT_SHIFT = 10, UP_SHIFT = 17, COMPONENT_BITS = 7;
    private static final double LINE_UNIT = 0.25;

    private static final int SEEK_EVERY = 20;             // on the ground, how often it looks up for an underside
    private static final int PLAN_EVERY = 10;             // how often it looks for a rim when it has none
    private static final int GRIP_EVERY = 5;              // how often it checks the stone it grips is still there
    private static final int SEARCH_NODES = 400;          // cells a search for a rim visits at most
    private static final DustParticleOptions WHITE = new DustParticleOptions(0xF6F2FF, 1.0F);
    private static final DustParticleOptions LILAC = new DustParticleOptions(0xC9B8FF, 0.8F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(0x3A1A6E, 1.2F);

    private final Grab grab = new Grab(MAX_PREY_WIDTH, MAX_PREY_HEIGHT, ESCAPE_RANGE);
    /** The cell it grips (its back to the block above), the cell it is crawling into, and how far it has got. */
    private @Nullable BlockPos cell;
    private @Nullable BlockPos next;
    private double stepDone;
    private final ArrayDeque<BlockPos> path = new ArrayDeque<>();
    private int planTimer;
    /** The way out over its rim, and the rim's thickness over its cell (0: not at a rim it can fish over). */
    private Direction out = Direction.NORTH;
    private int thick;
    /** Where its catch is held now, and how far the reel has brought it. */
    private Vec3 anchor = Vec3.ZERO;
    private double reeled;
    private @Nullable Vec3 stunAt;
    private int cooldown;
    private int seekTimer = SEEK_EVERY;
    private @Nullable BlockPos climbTo;
    private boolean gripOnFirstTick = true;
    private float heldYaw;
    /** The swing each player near the line was last seen in: only a new swing can cut it. */
    private final Map<Player, Object> swings = new WeakHashMap<>();
    private int casts, cuts;

    public AbyssAngler(EntityType<? extends AbyssAngler> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("abyss_angler", WALK_SPEED);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // only on the ground: under an island its own phases move it
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
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // it feels who walks on the island above it, through the stone: no line of sight, and a tall search box (the
        // lure itself needs a clear line from the lantern)
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false) {
            @Override
            protected AABB getTargetSearchArea(double range) {
                return mob.getBoundingBox().inflate(range, 8.0, range);
            }
        });
    }

    /** Clinging under a ceiling: everything but standing on the ground and climbing up to one. */
    private boolean clinging() {
        return !core().idle() && !core().in(Step.CLIMB);
    }

    /** The line is out: the reel and the hold. */
    public boolean fishing() {
        return core().in(Step.REEL) || core().in(Step.HOLD);
    }

    // ---- the tick ----------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide() && !core().idle()) {
            setNoGravity(true);       // it holds itself up and moves itself
            setDeltaMovement(Vec3.ZERO);
        }
        super.tick();
        if (!(level() instanceof ServerLevel)) return;
        if (gripOnFirstTick) {
            gripOnFirstTick = false;
            if (core().idle()) gripAbove(1);
        }
        if (clinging() && cell != null) place();
        if (!fishing() && core().flag(FLAG_LINE)) core().setFlag(FLAG_LINE, false);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (core().idle()) {
            groundTick();
            return;
        }
        getNavigation().stop();
        if (core().in(Step.CLIMB)) {
            climbTick();
            return;
        }
        if (cell == null || (tickCount % GRIP_EVERY == 0 && !gripHolds())) {
            fall();                  // the stone it gripped is gone
            return;
        }
        if (core().in(Step.CLING)) clingTick(level);
        else if (core().in(Step.TELL)) tellTick(level);
        else if (core().in(Step.REEL)) reelTick(level);
        else if (core().in(Step.HOLD)) holdTick(level);
        else if (core().in(Step.STUN)) stunTick(level);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            cast(level);
        } else if (ended == Step.HOLD) {
            slack();                 // it lets its catch go at the brink
        } else if (ended == Step.CLIMB) {
            if (climbTo == null || !Underside.cell(level, climbTo) || !clingAt(climbTo)) fall();
        } else if (cell != null && ended != Step.IDLE) {
            cling();                 // the stun ran out (or a posed phase): back under the rim
        } else {
            core().end();
        }
    }

    // ---- clinging, and crawling to a rim ----------------------------------------------------------------------------

    /** Back to clinging under its cell; one that may not cling (an event cancelled it) falls. */
    private void cling() {
        stunAt = null;
        if (!startPhase(Step.CLING, 0, getTarget())) fall();
    }

    /** Lets go of the ceiling (and of its catch) and drops onto its own belly. */
    private void fall() {
        letGo();
        core().end();
        cell = null;
        next = null;
        path.clear();
        stunAt = null;
        setNoGravity(false);
        seekTimer = SEEK_EVERY;
    }

    /** True while the stone over the cell it grips (and the one it is crawling into) is still there. */
    private boolean gripHolds() {
        if (cell == null || !Underside.cell(level(), cell)) return false;
        if (next != null && !Underside.cell(level(), next)) {
            next = null;             // the way ahead crumbled: it stays where it grips
            stepDone = 0.0;
            path.clear();
        }
        return true;
    }

    /** Its feet when it grips cell {@code c}: its back flat against the block above. */
    private Vec3 centre(BlockPos c) {
        return new Vec3(c.getX() + 0.5, c.getY() + 1.0 - getBbHeight(), c.getZ() + 0.5);
    }

    /** Where it should be now: in (or between) its cells, or hanging on the rim's face while stunned. */
    private Vec3 spot() {
        if (cell == null) return position();
        if (core().in(Step.STUN)) {
            if (stunAt == null) stunAt = hangSpot();
            return stunAt;
        }
        Vec3 here = centre(cell);
        return next != null ? here.lerp(centre(next), stepDone) : here;
    }

    /**
     * Stunned: out past the rim's face, its top level with the rim's top, so it hangs against the stone under the
     * edge; if something solid is there, leaning half out of its cell instead.
     */
    private Vec3 hangSpot() {
        Vec3 c = centre(cell);
        Vec3 hang = new Vec3(c.x + out.getStepX() * STUN_OUT, c.y + Math.max(1, thick), c.z + out.getStepZ() * STUN_OUT);
        if (level().noCollision(this, getBoundingBox().move(hang.subtract(position())).deflate(1.0E-3))) return hang;
        return new Vec3(c.x + out.getStepX() * 0.6, c.y, c.z + out.getStepZ() * 0.6);
    }

    /**
     * Eases toward its spot. Going up it moves across first, then up; going down, down first: so it goes out from under
     * the rim and then up its face, and back the same way, never through the stone.
     */
    private void place() {
        Vec3 want = spot();
        Vec3 d = want.subtract(position());
        double len = d.length();
        if (len < 1.0E-6) return;
        if (len <= SLIDE_SPEED + 0.05) {
            setPos(want);
            return;
        }
        double across = Math.hypot(d.x, d.z);
        Vec3 move;
        if (Math.abs(d.y) > 1.0E-6 && across > 1.0E-6) {
            move = d.y > 0 ? new Vec3(d.x, 0.0, d.z) : new Vec3(0.0, d.y, 0.0);
        } else {
            move = d;
        }
        double m = move.length();
        setPos(position().add(move.scale(Math.min(SLIDE_SPEED, m) / m)));
    }

    private void clingTick(ServerLevel level) {
        if (thick == 0 || next != null || !path.isEmpty()) {
            if (next == null && path.isEmpty() && --planTimer <= 0) {
                planTimer = PLAN_EVERY;
                path.addAll(pathToRim(level, cell));
            }
            crawlStep();
            return;
        }
        LivingEntity t = getTarget();
        if (cooldown == 0 && canLure(t)) lure(level, t);
    }

    /** One tick of crawling: along its path, a cell at a time, {@link #CRAWL_SPEED} blocks a tick. */
    private void crawlStep() {
        if (next == null) {
            BlockPos n = path.pollFirst();
            if (n == null || cell == null) return;
            if (!Underside.cell(level(), n) || !Underside.adjacent(cell, n)) {
                path.clear();
                return;
            }
            next = n;
            stepDone = 0.0;
            face(Direction.getApproximateNearest(n.getX() - cell.getX(), 0, n.getZ() - cell.getZ()));
        }
        stepDone = Math.min(1.0, stepDone + CRAWL_SPEED);
        if (stepDone >= 1.0 - 1.0E-9) {
            cell = next;
            next = null;
            stepDone = 0.0;
            settle();
        }
    }

    /** Its cell's rim: the way out it fishes over and how thick the rim is; none (0) if it can't fish from here. */
    private void settle() {
        if (cell == null) return;
        thick = 0;
        Direction first = Underside.openSide(level(), cell);
        if (first != null && rimThickness(level(), cell, first) > 0) {
            out = first;
            thick = rimThickness(level(), cell, first);
        } else {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int t = rimThickness(level(), cell, d);
                if (t > 0) {
                    out = d;
                    thick = t;
                    break;
                }
            }
            if (thick == 0 && first != null) out = first;
        }
        setThickness(Math.max(1, thick));
        face(out);
    }

    /**
     * How thick the rim over cling cell {@code c} is when it fishes out of its side {@code d}: the solid blocks straight
     * over {@code c} (at most {@link #MAX_RIM}), with room to stand on top, and the column beside it on side {@code d}
     * open from {@code c}'s height to the lantern's casting spot over the rim. 0 when it can't fish that way.
     */
    public static int rimThickness(BlockGetter level, BlockPos c, Direction d) {
        int t = 0;
        while (t <= MAX_RIM && solid(level, c.above(t + 1))) t++;
        if (t == 0 || t > MAX_RIM || solid(level, c.above(t + 1))) return 0;
        BlockPos side = c.relative(d);
        for (int k = 0; k <= t + 1; k++) if (solid(level, side.above(k))) return 0;
        return t;
    }

    private static boolean solid(BlockGetter level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** The crawl from {@code from} to the nearest cell along the underside it can fish from, {@code from} left out. */
    private List<BlockPos> pathToRim(Level level, @Nullable BlockPos from) {
        if (from == null) return List.of();
        Map<BlockPos, BlockPos> came = new HashMap<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        came.put(from, from);
        open.add(from);
        while (!open.isEmpty() && came.size() < SEARCH_NODES) {
            BlockPos c = open.poll();
            for (BlockPos n : Underside.neighbours(level, c)) {
                if (came.containsKey(n) || Math.abs(n.getX() - from.getX()) > CRAWL_RANGE || Math.abs(n.getZ() - from.getZ()) > CRAWL_RANGE) continue;
                came.put(n, c);
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (rimThickness(level, n, d) > 0) {
                        ArrayDeque<BlockPos> way = new ArrayDeque<>();
                        for (BlockPos p = n; !p.equals(from); p = came.get(p)) way.addFirst(p);
                        return new ArrayList<>(way);
                    }
                }
                open.add(n);
            }
        }
        return List.of();
    }

    // ---- the lure ----------------------------------------------------------------------------------------------------

    /** Where the lantern hangs to cast: half a block past the rim's face, half a block over its top. */
    public Vec3 lurePoint() {
        double up = getBbHeight() + Math.max(1, thick) + LURE_UP;
        return new Vec3(getX() + out.getStepX() * LURE_FORWARD, getY() + up, getZ() + out.getStepZ() * LURE_FORWARD);
    }

    private static Vec3 body(Entity e) {
        return e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
    }

    /**
     * True when it would lure {@code t}: at a rim it can fish over, off its cooldown, and {@code t} a catch it can reel
     * (not in creative or spectator, not too big, not already held) on the ground (or a block over it at most), its
     * middle within 16 blocks of the casting spot with nothing solid between.
     */
    public boolean canLure(@Nullable LivingEntity t) {
        if (t == null || cell == null || thick == 0 || t instanceof AbyssAngler || !grab.fits(this, t) || Grab.isHeld(t)) return false;
        if (Double.isNaN(standY(level(), t.getX(), t.getY(), t.getZ()))) return false;
        Vec3 from = lurePoint(), to = body(t);
        if (from.distanceToSqr(to) > CAST_RANGE * CAST_RANGE) return false;
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** The tell: the lantern swings up over the edge and brightens, with a low cranking. */
    private void lure(ServerLevel level, LivingEntity t) {
        if (!startPhase(Step.TELL, TELL_TICKS, t)) return;
        face(out);
        Vec3 at = lurePoint();
        level.playSound(null, at.x, at.y, at.z, ModSounds.ABYSS_ANGLER_TELL.get(), getSoundSource(), 1.3F, 1.0F);
        level.sendParticles(WHITE, true, true, at.x, at.y - 0.6, at.z, 6, 0.15, 0.3, 0.15, 0.0);
    }

    private void tellTick(ServerLevel level) {
        int e = core().elapsed();
        Vec3 at = lurePoint();
        double rise = Math.min(1.0, e / 10.0);
        double y = at.y - 0.9 * (1.0 - rise);
        if (e % 2 == 0) {
            level.sendParticles(e < 8 ? LILAC : WHITE, true, true, at.x, y, at.z, 3 + e / 4, 0.2, 0.2, 0.2, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, y, at.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
        if (e == TELL_TICKS / 2) level.playSound(null, at.x, at.y, at.z, ModSounds.ABYSS_ANGLER_TELL.get(), getSoundSource(), 1.0F, 0.8F);
    }

    // ---- the cast, the reel and the hold -----------------------------------------------------------------------------

    /** The lure is over: the line flies to a target still in reach and in sight, and the hook sets; or it settles back. */
    private void cast(ServerLevel level) {
        LivingEntity t = core().target();
        if (!canLure(t) || !MechanicEvent.post(this, "cast", t) || !startPhase(Step.REEL, 0, t)) {
            cling();
            return;
        }
        casts++;
        grab.take(t);
        anchor = new Vec3(t.getX(), standY(level, t.getX(), t.getY(), t.getZ()), t.getZ());   // on its feet: a jump is cut short
        reeled = 0.0;
        swings.clear();
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(lurePoint(), body(t)).inflate(8.0))) swings.put(p, p.getCurrentSwing());
        Vec3 from = lurePoint(), to = body(t);
        level.playSound(null, from.x, from.y, from.z, ModSounds.ABYSS_ANGLER_CAST.get(), getSoundSource(), 1.3F, 1.0F);
        if (MechanicEvent.post(this, "hook", t)) {
            Grab.hurt(level, t, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * HOOK_HIT);
        }
        level.playSound(null, to.x, to.y, to.z, ModSounds.ABYSS_ANGLER_HOOK.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, true, true, to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0.2);
        alongLine(level, WHITE, from, to, 16);
        if (!t.isAlive()) {
            slack();
            return;
        }
        holdAt(level, t, anchor);    // the hook's knockback is undone: the reel moves it, nothing else
        setLine(t);
    }

    private void reelTick(ServerLevel level) {
        LivingEntity v = grab.victim();
        if (v == null || !grab.holding(this, anchor)) {
            slack();                 // it got away, died, or went creative
            return;
        }
        if (lineCut(level, v)) return;
        if (!pullStep(level, v)) {
            // at the brink (or stopped by a wall or a drop): it holds its catch there
            if (!startPhase(Step.HOLD, HOLD_TICKS, v)) {
                slack();
                return;
            }
            holdAt(level, v, anchor);
        }
        setLine(v);
        if (core().elapsed() % 3 == 0) alongLine(level, LILAC, lurePoint(), body(v), 4);
        if (core().elapsed() % 8 == 0) level.playSound(null, getX(), getY(), getZ(), ModSounds.ABYSS_ANGLER_REEL.get(), getSoundSource(), 1.1F, 1.0F);
    }

    private void holdTick(ServerLevel level) {
        LivingEntity v = grab.victim();
        if (v == null || !grab.holding(this, anchor)) {
            slack();
            return;
        }
        if (lineCut(level, v)) return;
        holdAt(level, v, anchor);
        setLine(v);
        if (core().elapsed() % 5 == 0) alongLine(level, LILAC, lurePoint(), body(v), 2);
    }

    /**
     * The brink {@code v} is reeled to: on top of the rim's last block (the top of the column over its cell), as far
     * out as it can stand with its whole body over the block, its toes at the edge.
     */
    public Vec3 brink(LivingEntity v) {
        BlockPos top = cell == null ? blockPosition() : cell.above(Math.max(1, thick));
        VoxelShape shape = level().getBlockState(top).getCollisionShape(level(), top);
        double y = top.getY() + (shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y));
        double in = Math.max(0.0, 0.5 - v.getBbWidth() * 0.5);
        return new Vec3(top.getX() + 0.5 + out.getStepX() * in, y, top.getZ() + 0.5 + out.getStepZ() * in);
    }

    /**
     * One tick of the reel: {@code v} moves up to 0.5 blocks straight toward its brink across the ground (half that with
     * a Grounding Charm in the off-hand). False, and nothing moves, once it is there, or when the next step would leave
     * the ground (a drop deeper than a block), climb more than a step, or run into something solid.
     */
    private boolean pullStep(ServerLevel level, LivingEntity v) {
        Vec3 to = brink(v);
        double dx = to.x - anchor.x, dz = to.z - anchor.z, d = Math.hypot(dx, dz);
        if (d < 1.0E-6) return false;
        double step = Math.min(PULL_SPEED * Charms.grounded(v), d);
        double nx = anchor.x + dx / d * step, nz = anchor.z + dz / d * step;
        double ny = standY(level, nx, anchor.y, nz);
        if (Double.isNaN(ny)) return false;
        Vec3 next = new Vec3(nx, ny, nz);
        if (!level.noCollision(v, v.getBoundingBox().move(next.subtract(v.position())).deflate(1.0E-4))) return false;
        anchor = next;
        reeled += step;
        holdAt(level, v, anchor);
        return true;
    }

    /**
     * The height to stand at over (x, z) for feet at {@code y}: the top of the ground there, if it is no more than a step
     * up or a block down; NaN over a bigger drop, or where a wall rises.
     */
    private static double standY(Level level, double x, double y, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(y + STEP_UP); by >= Mth.floor(y - STEP_DOWN - 0.01); by--) {
            BlockPos p = new BlockPos(bx, by, bz);
            VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
            if (s.isEmpty()) continue;
            double top = by + s.max(Direction.Axis.Y);
            if (top > y + STEP_UP + 1.0E-6) return Double.NaN;
            if (top >= y - STEP_DOWN - 1.0E-6) return top;
            return Double.NaN;
        }
        return Double.NaN;
    }

    /** It lets its catch go (it falls only if it stands on nothing), and waits before it lures again. */
    private void slack() {
        letGo();
        cooldown = COOLDOWN;
        cling();
    }

    /** Lets go of its catch where it is, with no motion of its own. */
    private void letGo() {
        LivingEntity v = grab.release();
        if (v != null && v.isAlive()) Push.set(v, new Vec3(0.0, Math.min(0.0, v.getDeltaMovement().y), 0.0));
        if (core().flag(FLAG_LINE)) core().setFlag(FLAG_LINE, false);
    }

    /** Holds {@code v} at {@code at}: no motion, no fall counted, nothing it rides. */
    private static void holdAt(ServerLevel level, LivingEntity v, Vec3 at) {
        v.resetFallDistance();
        if (v.isPassenger()) v.stopRiding();
        if (v.position().distanceToSqr(at) > 1.0E-8) {
            if (v instanceof ServerPlayer p) p.teleportTo(level, at.x, at.y, at.z, Relative.ROTATION, 0.0F, 0.0F, false);
            else v.teleportTo(at.x, at.y, at.z);
        }
        Push.set(v, Vec3.ZERO);
        if (v instanceof Mob m) m.getNavigation().stop();
    }

    // ---- the counter: cutting the line ---------------------------------------------------------------------------------

    /**
     * True (and the line snaps) when something cut the line from the casting spot to {@code v}'s middle this tick: a new
     * swing by a player whose reach passes within {@link #LINE_RADIUS} of it with nothing solid in the way, or a
     * projectile whose last move passed that close.
     */
    private boolean lineCut(ServerLevel level, LivingEntity v) {
        Vec3 a = lurePoint(), b = body(v);
        AABB near = new AABB(a, b).inflate(6.0);
        Entity by = null;
        boolean found = false;
        for (Player p : level.getEntitiesOfClass(Player.class, near, p -> p.isAlive() && !p.isSpectator())) {
            Object swing = p.getCurrentSwing();
            Object seen = swings.put(p, swing);
            if (swing == null || swing == seen) continue;
            Vec3 eye = p.getEyePosition();
            Vec3 end = eye.add(p.getViewVector(1.0F).scale(p.entityInteractionRange()));
            double[] hit = closest(eye, end, a, b);
            if (hit[0] > LINE_RADIUS) continue;
            Vec3 at = eye.lerp(end, hit[1]);
            if (level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            by = p;
            found = true;
            break;
        }
        if (!found) {
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class, near, Entity::isAlive)) {
                Vec3 from = new Vec3(shot.xo, shot.yo, shot.zo), to = shot.position();
                if (from.distanceToSqr(to) < 1.0E-6) continue;
                if (closest(from, to, a, b)[0] > LINE_RADIUS + shot.getBbWidth() * 0.5) continue;
                by = shot.getOwner() != null ? shot.getOwner() : shot;
                found = true;
                break;
            }
        }
        if (!found || !MechanicEvent.post(this, "cut", by instanceof LivingEntity l ? l : null)) return false;
        snap(level, a, b);
        return true;
    }

    /**
     * The gap between segments p1-q1 and p2-q2, and how far along the first (0..1) the nearest point lies.
     */
    static double[] closest(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
        Vec3 d1 = q1.subtract(p1), d2 = q2.subtract(p2), r = p1.subtract(p2);
        double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
        double s, t;
        if (a <= 1.0E-12 && e <= 1.0E-12) {
            s = 0.0;
            t = 0.0;
        } else if (a <= 1.0E-12) {
            s = 0.0;
            t = Mth.clamp(f / e, 0.0, 1.0);
        } else {
            double c = d1.dot(r);
            if (e <= 1.0E-12) {
                t = 0.0;
                s = Mth.clamp(-c / a, 0.0, 1.0);
            } else {
                double b = d1.dot(d2), denom = a * e - b * b;
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
        Vec3 c1 = p1.add(d1.scale(s)), c2 = p2.add(d2.scale(t));
        return new double[] {c1.distanceTo(c2), s};
    }

    /** The line snaps back: its catch is let go where it stands, and the recoil yanks it out onto the rim's face, stunned. */
    private void snap(ServerLevel level, Vec3 a, Vec3 b) {
        letGo();
        cuts++;
        cooldown = COOLDOWN;
        level.playSound(null, a.x, a.y, a.z, ModSounds.ABYSS_ANGLER_SNAP.get(), getSoundSource(), 1.4F, 1.0F);
        level.playSound(null, b.x, b.y, b.z, ModSounds.ABYSS_ANGLER_SNAP.get(), getSoundSource(), 1.0F, 1.2F);
        alongLine(level, WHITE, a, b, 20);
        level.sendParticles(ParticleTypes.CRIT, true, true, b.x, b.y, b.z, 10, 0.2, 0.2, 0.2, 0.3);
        stunAt = null;
        if (!startPhase(Step.STUN, STUN_TICKS, null)) {
            cling();
            return;
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.ABYSS_ANGLER_STUN.get(), getSoundSource(), 1.3F, 1.0F);
        level.sendParticles(ParticleTypes.POOF, true, true, a.x, a.y - 0.5, a.z, 8, 0.2, 0.3, 0.2, 0.03);
    }

    /** Stunned on the rim: dazed sparks circle over its head, and its gills glow. */
    private void stunTick(ServerLevel level) {
        int e = core().elapsed();
        if (e % 4 == 0) {
            double ang = e * 0.6, top = getY() + getBbHeight() + 0.25;
            for (int i = 0; i < 3; i++) {
                double a = ang + i * Mth.TWO_PI / 3.0;
                level.sendParticles(WHITE, true, true, getX() + Math.cos(a) * 0.5, top, getZ() + Math.sin(a) * 0.5, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (e % 10 == 0) level.sendParticles(VIOLET, true, true, getX(), getY() + 0.5, getZ(), 3, 0.4, 0.3, 0.4, 0.0);
    }

    /** Stunned on its rim after a cut line, its gill flaps glow weak-point gold: hit it now. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.STUN);
    }

    private static void alongLine(ServerLevel level, DustParticleOptions dust, Vec3 a, Vec3 b, int points) {
        for (int i = 0; i <= points; i++) {
            Vec3 p = a.lerp(b, i / (double) points);
            level.sendParticles(dust, true, true, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    // ---- the synced line -------------------------------------------------------------------------------------------

    private void setThickness(int t) {
        setBits(THICK_SHIFT, 2, Mth.clamp(t, 1, 3));
    }

    /** The rim's thickness, 1..3, from the synced flags (the model reaches the lantern over it). */
    public static int thickness(int flags) {
        return Math.max(1, flags >>> THICK_SHIFT & 3);
    }

    /** The line out to {@code v}'s middle, synced as its far end from the casting spot: forward, left and up, in quarter blocks. */
    private void setLine(LivingEntity v) {
        Vec3 d = body(v).subtract(lurePoint());
        double fx = out.getStepX(), fz = out.getStepZ();
        setBits(FORWARD_SHIFT, COMPONENT_BITS, quarter(d.x * fx + d.z * fz));
        setBits(LEFT_SHIFT, COMPONENT_BITS, quarter(d.x * fz - d.z * fx));
        setBits(UP_SHIFT, COMPONENT_BITS, quarter(d.y));
        if (!core().flag(FLAG_LINE)) core().setFlag(FLAG_LINE, true);
    }

    private static int quarter(double v) {
        int max = (1 << COMPONENT_BITS - 1) - 1;
        return Mth.clamp((int) Math.round(v / LINE_UNIT), -max - 1, max) & ((1 << COMPONENT_BITS) - 1);
    }

    private void setBits(int shift, int count, int value) {
        for (int i = 0; i < count; i++) {
            boolean on = (value >>> i & 1) != 0;
            if (core().flag(shift + i) != on) core().setFlag(shift + i, on);
        }
    }

    /**
     * The line's far end from the casting spot, from the synced flags: blocks forward (out over the rim), left and up,
     * as the mob faces. Both sides read it: the model draws the line, the client widens the box it is drawn in.
     */
    public static float[] lineEnd(int flags) {
        return new float[] {component(flags, FORWARD_SHIFT), component(flags, LEFT_SHIFT), component(flags, UP_SHIFT)};
    }

    private static float component(int flags, int shift) {
        int raw = flags >>> shift & ((1 << COMPONENT_BITS) - 1);
        if (raw >= 1 << COMPONENT_BITS - 1) raw -= 1 << COMPONENT_BITS;
        return (float) (raw * LINE_UNIT);
    }

    /** A client draws the line out of its own box: the box it is culled by reaches the lantern and the line's end. */
    @Override
    public AABB getInterpolatedBoundingBox(float partialTick) {
        AABB box = super.getInterpolatedBoundingBox(partialTick);
        if (!level().isClientSide() || !core().flag(FLAG_LINE)) return box;
        int flags = core().flags();
        double yaw = Math.toRadians(getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double bx = (box.minX + box.maxX) * 0.5, bz = (box.minZ + box.maxZ) * 0.5;
        Vec3 lure = new Vec3(bx + fx * LURE_FORWARD, box.minY + getBbHeight() + thickness(flags) + LURE_UP, bz + fz * LURE_FORWARD);
        float[] e = lineEnd(flags);
        Vec3 end = lure.add(fx * e[0] + fz * e[1], e[2], fz * e[0] - fx * e[1]);
        return box.minmax(new AABB(lure, end).inflate(0.5));
    }

    // ---- fallen: climbing back up ------------------------------------------------------------------------------------

    private void groundTick() {
        if (--seekTimer > 0) return;
        seekTimer = SEEK_EVERY;
        BlockPos up = Underside.above(level(), BlockPos.containing(getX(), getY() + getBbHeight() - 0.01, getZ()), CLIMB_REACH);
        if (up == null || !startPhase(Step.CLIMB, 0, null)) return;
        climbTo = up;
        setNoGravity(true);
        getNavigation().stop();
    }

    private void climbTick() {
        if (climbTo == null || !Underside.cell(level(), climbTo) || core().elapsed() > CLIMB_MAX_TICKS) {
            fall();
            return;
        }
        Vec3 to = centre(climbTo).subtract(position());
        if (to.length() <= CLIMB_SPEED) {
            if (!clingAt(climbTo)) fall();
            return;
        }
        setPos(position().add(to.normalize().scale(CLIMB_SPEED)));
    }

    /** A fresh spawn (or one just loaded) grips the underside right above it, if there is one. */
    private void gripAbove(int reach) {
        BlockPos up = Underside.above(level(), BlockPos.containing(getX(), getY() + getBbHeight() - 0.01, getZ()), reach);
        if (up != null) clingAt(up);
    }

    /** Grips cell {@code c} at once and takes up its rim, if it has one. True if it clings there. */
    private boolean clingAt(BlockPos c) {
        if (!Underside.cell(level(), c)) return false;
        cell = c;
        next = null;
        stepDone = 0.0;
        path.clear();
        stunAt = null;
        if (!startPhase(Step.CLING, 0, getTarget())) {
            cell = null;
            return false;
        }
        settle();
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        setPos(centre(c));
        return true;
    }

    private void face(Direction d) {
        float yaw = d.toYRot();
        heldYaw = yaw;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Off the ground, body and head keep the facing it took; a client lines both up with the synced yaw. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core().idle()) {
            super.tickHeadTurn(yBodyRot);
        } else if (level().isClientSide()) {
            this.yBodyRot = getYRot();
            this.yHeadRot = getYRot();
        } else {
            setYRot(heldYaw);
            this.yBodyRot = heldYaw;
            this.yHeadRot = heldYaw;
        }
    }

    /** Clinging, stunned or climbing, it is held fast: only its own moves move it, and no blow knocks it off. */
    @Override
    public Pin pin() {
        return core().idle() ? Pin.FREE : Pin.FIXED;
    }

    @Override
    protected void doPush(Entity other) {
        if (!grab.holds(other)) super.doPush(other);
    }

    @Override
    public void die(DamageSource source) {
        letGo();                     // a dead angler reels no one: its catch stays where it was
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        letGo();
        super.onRemoval(reason);
    }

    // ---- test hooks --------------------------------------------------------------------------------------------------

    /**
     * Grips the underside of {@code block} at once (the cell under it) and takes up the rim there, if it has one. False
     * if there is no underside to grip there. Public as a test hook.
     */
    public boolean hangUnder(BlockPos block) {
        return !level().isClientSide() && clingAt(block.below());
    }

    /** The cell it grips, or null on the ground. Public as a test hook. */
    public @Nullable BlockPos cell() {
        return cell;
    }

    /** The way out over its rim, and the rim's thickness (0: none it can fish over). Public as test hooks. */
    public Direction out() {
        return out;
    }

    public int rim() {
        return thick;
    }

    /** Who its line holds, or null. Public as a test hook. */
    public @Nullable LivingEntity victim() {
        return grab.victim();
    }

    /** Ticks until it may lure again, and a reset to 0. Public as test hooks. */
    public int cooldown() {
        return cooldown;
    }

    public void resetCooldown() {
        cooldown = 0;
    }

    /** Casts made and lines cut since it spawned, and how far the current reel has brought its catch. Public as test hooks. */
    public int casts() {
        return casts;
    }

    public int cuts() {
        return cuts;
    }

    public double reeled() {
        return reeled;
    }

    // ---- saving --------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("LureCooldown", cooldown);
    }

    /** A reload ends any phase and lets its catch go; on its first tick it grips the underside right above it again. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cooldown = Math.max(0, input.getIntOr("LureCooldown", 0));
        gripOnFirstTick = true;
    }

    // ---- sounds --------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return fishing() ? null : ModSounds.ABYSS_ANGLER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ABYSS_ANGLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ABYSS_ANGLER_DEATH.get();
    }
}
