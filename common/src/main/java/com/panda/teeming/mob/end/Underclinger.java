package com.panda.teeming.mob.end;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Underclinger: a flat, pale creature that clings upside down to the underside of the outer End islands, its back
 * pressed to the stone, two long hooked forelimbs folded along its flanks. It crawls along the underside, only ever from
 * one gripped cell to the next, so it never hangs in open air.
 *
 * <p>When its target stands within 1 block of an island's edge, or on a block with open air under it (a bridge), it
 * crawls under that rim. Once there, with the rim no more than 2 blocks thick (its reach, measured as the gap up from its
 * back to the target's feet), it reaches (tell, 10 ticks): it slides half out past the rim and both arms rise up the
 * island's side and hook over the edge beside the target, with a scraping hiss and crumbs of stone falling from the rim.
 * A target that stepped back out of reach meanwhile is left alone. Otherwise the hooks close (its attack, through
 * armour) and it holds the target where it stands for 20 ticks, then drags it exactly 2 blocks straight out from the
 * island over 4 ticks, past the rim and over the void, and lets go. That drag is the one forced move over the void it
 * makes; anything solid in the way stops it short, never longer.
 *
 * <p>Counter: step back from the edge when the arms come over, or hit it. Any blow it takes while it reaches, holds or
 * drags makes it let go at once with a screech, and it retreats along the underside, away from the rim, for 40 ticks
 * before it can reach again. Fallen to the ground with no ceiling over it, it is harmless: it wanders until it finds an
 * underside straight above it and climbs back up.
 */
public class Underclinger extends TeemingMonster {
    /**
     * On its own legs (fallen), clinging under a ceiling, the reach (tell), holding its prey at the rim, the drag, the
     * retreat after a blow, climbing back up to a ceiling.
     */
    public enum Step implements Phase { IDLE, CLING, TELL, HOLD, PULL, RETREAT, CLIMB }

    public static final int TELL_TICKS = 10;              // the reach: arms over the rim before the hooks close
    public static final int HOLD_TICKS = 20;              // it holds its prey at the rim this long before the drag
    public static final int PULL_TICKS = 4;               // the drag itself
    public static final double PULL_DISTANCE = 2.0;       // exactly this far out from the island, never more
    public static final float GRAB_HIT = 1.0F;            // x attack damage as the hooks close: 8.5 for its attack of 8.5
    public static final int RETREAT_TICKS = 40;           // after a blow makes it let go, before it can reach again
    public static final int RECOIL_TICKS = 8;             // the start of the retreat: it flinches back before it crawls
    public static final int COOLDOWN = 40;                // after a drag, before it reaches again
    public static final double EDGE_RANGE = 1.0;          // prey this close to an edge, across, can be reached
    public static final double GRAB_REACH = 2.0;          // the gap up from its back to the prey's feet: a rim 2 thick at most
    public static final double CRAWL_SPEED = 0.1;         // along the underside, blocks a tick
    public static final int CRAWL_RANGE = 12;             // it crawls to a rim at most this many blocks across
    public static final double LEAN_OUT = 0.5;            // reaching, its middle slides this far out from its cell: onto the rim's face
    public static final double SLIDE_SPEED = 0.125;       // how fast it slides out and back, blocks a tick
    public static final int RETREAT_CELLS = 4;            // how far back under the island it retreats, in cells
    public static final int CLIMB_REACH = 6;              // fallen, it climbs to an underside at most this far above its back
    public static final double CLIMB_SPEED = 0.2;
    public static final int CLIMB_MAX_TICKS = 60;
    /** Prey wider or taller than this is too big to hook. */
    public static final float MAX_PREY_WIDTH = 1.0F, MAX_PREY_HEIGHT = 2.0F;
    /** Prey found this far from where it is held (an ender pearl, a teleport) has got away. */
    public static final double ESCAPE_RANGE = 3.0;
    public static final double WALK_SPEED = 0.2;
    private static final int SEEK_EVERY = 20;             // on the ground, how often it looks up for an underside
    private static final int PLAN_EVERY = 10;             // how often it plans its crawl again
    private static final int GRIP_EVERY = 5;              // how often it checks the stone it grips is still there
    /** The rim's thickness (1..3) in the flags, so the arms stretch up the island's side to its edge. */
    private static final int THICK_BIT0 = 0, THICK_BIT1 = 1;

    private final Grab grab = new Grab(MAX_PREY_WIDTH, MAX_PREY_HEIGHT, ESCAPE_RANGE);
    /** The cell it grips (its back to the block above), the cell it is crawling into, and how far it has got. */
    private @Nullable BlockPos cell;
    private @Nullable BlockPos next;
    private double stepDone;
    private final ArrayDeque<BlockPos> path = new ArrayDeque<>();
    private @Nullable BlockPos goal;
    private int planTimer;
    /** The way out over the rim it reaches, and the rim it reaches over. */
    private Direction out = Direction.NORTH;
    private @Nullable Rim rim;
    /** Where its prey is held, and how far it has been dragged so far. */
    private Vec3 anchor = Vec3.ZERO;
    private double pulled;
    private int cooldown;
    private int seekTimer = SEEK_EVERY;
    private @Nullable BlockPos climbTo;
    private boolean gripOnFirstTick = true;
    private float heldYaw;

    public Underclinger(EntityType<? extends Underclinger> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("underclinger", WALK_SPEED);
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
        // it senses who walks on the island above it, through the stone: no line of sight, and a tall search box
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

    /** Reaching over the rim: the reach, the hold and the drag. A blow now makes it let go. */
    public boolean reaching() {
        return core().in(Step.TELL) || core().in(Step.HOLD) || core().in(Step.PULL);
    }

    // ---- the tick ----------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide() && !core().idle()) {
            setNoGravity(true);       // it holds itself up and moves itself
            setDeltaMovement(Vec3.ZERO);
        }
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (gripOnFirstTick) {
            gripOnFirstTick = false;
            if (core().idle()) gripAbove(1);
        }
        if (clinging() && cell != null) place();
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
        if (core().in(Step.CLING)) {
            clingTick(level);
        } else if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().in(Step.HOLD)) {
            holdTick(level);
        } else if (core().in(Step.PULL)) {
            pullTick(level);
        } else if (core().in(Step.RETREAT)) {
            if (core().elapsed() >= RECOIL_TICKS) crawlStep();
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            grabOrMiss(level);
        } else if (ended == Step.HOLD) {
            drag(level);
        } else if (ended == Step.PULL) {
            finishDrag(level);
        } else if (ended == Step.CLIMB) {
            if (climbTo == null || !Underside.cell(level, climbTo) || !clingAt(climbTo)) fall();
        } else if (cell != null && ended != Step.IDLE) {
            cling();                 // the retreat ran out (or a posed phase): back to clinging, ready to reach
        } else {
            core().end();
        }
    }

    // ---- clinging and crawling -----------------------------------------------------------------------------------

    /** Back to clinging where it is; one that may not cling (an event cancelled it) falls. */
    private void cling() {
        if (!startPhase(Step.CLING, 0, getTarget())) fall();
    }

    /** Lets go of the ceiling (and of its prey) and drops onto its own legs. */
    private void fall() {
        letGo();
        core().end();
        cell = null;
        next = null;
        path.clear();
        goal = null;
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

    /** Where it should be now: between the cell it grips and the next, or slid out onto the rim's face while it reaches. */
    private Vec3 spot() {
        Vec3 here = centre(cell);
        if (next != null) here = here.lerp(centre(next), stepDone);
        if (reaching()) here = here.add(out.getStepX() * LEAN_OUT, 0.0, out.getStepZ() * LEAN_OUT);
        return here;
    }

    /** Its feet when it grips cell {@code c}: its back flat against the block above. */
    private Vec3 centre(BlockPos c) {
        return new Vec3(c.getX() + 0.5, c.getY() + 1.0 - getBbHeight(), c.getZ() + 0.5);
    }

    /** Eases toward its spot (it crawls slower than it slides, so a crawl is followed exactly). */
    private void place() {
        Vec3 want = spot();
        Vec3 d = want.subtract(position());
        double len = d.length();
        if (len < 1.0E-6) return;
        setPos(len <= SLIDE_SPEED + 0.05 ? want : position().add(d.scale(SLIDE_SPEED / len)));
    }

    private void clingTick(ServerLevel level) {
        LivingEntity t = getTarget();
        Rim r = prey(t) ? Rim.find(level, t) : null;
        if (r == null) {
            path.clear();            // nothing at an edge to reach for: it finishes the step it is on and waits
            goal = null;
            crawlStep();
            return;
        }
        if (next == null && r.cell().equals(cell)) {
            path.clear();
            face(r.out());
            if (cooldown == 0 && inReach(t)) reach(level, t, r);
            return;
        }
        if (!r.cell().equals(goal) || --planTimer <= 0) {
            planTimer = PLAN_EVERY;
            goal = r.cell();
            path.clear();
            BlockPos from = next != null ? next : cell;
            path.addAll(Underside.path(level, from, goal, CRAWL_RANGE));
        }
        crawlStep();
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
            if (!reaching()) face(Direction.getApproximateNearest(n.getX() - cell.getX(), 0, n.getZ() - cell.getZ()));
        }
        stepDone = Math.min(1.0, stepDone + CRAWL_SPEED);
        if (stepDone >= 1.0 - 1.0E-9) {
            cell = next;
            next = null;
            stepDone = 0.0;
        }
    }

    /** Prey it may reach for: its target, alive, small enough, not in creative or spectator, and near. */
    private boolean prey(@Nullable LivingEntity t) {
        return t != null && !(t instanceof Underclinger) && grab.fits(this, t)
                && Math.abs(t.getX() - getX()) <= CRAWL_RANGE + 1 && Math.abs(t.getZ() - getZ()) <= CRAWL_RANGE + 1
                && Math.abs(t.getY() - getY()) <= 8.0;
    }

    /** The rim is within its reach: the gap up from its back to {@code t}'s feet, or across, is at most 2, and {@code t} stands above it. */
    public boolean inReach(LivingEntity t) {
        return MobCore.gap(this, t) <= GRAB_REACH + 1.0E-6 && t.getY() >= getY() + getBbHeight() - 0.01;
    }

    // ---- the reach, the hold and the drag ------------------------------------------------------------------------

    /** The tell: it slides out onto the rim's face and its arms rise over the edge beside {@code t}. */
    private boolean reach(ServerLevel level, LivingEntity t, Rim r) {
        if (!startPhase(Step.TELL, TELL_TICKS, t)) return false;
        rim = r;
        out = r.out();
        face(out);
        setThickness(r.thickness());
        Vec3 at = r.lip(t);
        level.playSound(null, at.x, at.y, at.z, ModSounds.UNDERCLINGER_TELL.get(), getSoundSource(), 1.3F, 1.0F);
        crumbs(level, at, 10);
        return true;
    }

    private void tellTick(ServerLevel level) {
        LivingEntity t = core().target();
        if (rim == null) return;
        Vec3 at = t != null ? rim.lip(t) : position().add(0.0, 1.0, 0.0);
        int e = core().elapsed();
        if (e % 2 == 0) crumbs(level, at, 3);
        if (e == TELL_TICKS / 2) level.playSound(null, at.x, at.y, at.z, ModSounds.UNDERCLINGER_TELL.get(), getSoundSource(), 1.0F, 1.2F);
    }

    /** The reach is over: the hooks close on prey still at the rim and in reach, or find nothing and it settles back. */
    private void grabOrMiss(ServerLevel level) {
        LivingEntity t = core().target();
        Rim r = prey(t) ? Rim.find(level, t) : null;
        if (r == null || rim == null || !r.cell().equals(rim.cell()) || r.out() != rim.out() || !inReach(t)
                || !startPhase(Step.HOLD, HOLD_TICKS, t)) {
            rim = null;
            cling();
            return;
        }
        anchor = t.position();
        pulled = 0.0;
        grab.take(t);
        if (MechanicEvent.post(this, "grab_hit", t)) {
            Grab.hurt(level, t, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * GRAB_HIT);
        }
        if (t.isAlive()) holdAt(level, t, anchor);    // the blow's knockback is undone: it is held where it stood
        level.playSound(null, t.getX(), t.getY(), t.getZ(), ModSounds.UNDERCLINGER_GRAB.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 0.2, t.getZ(), 8, 0.3, 0.1, 0.3, 0.2);
        crumbs(level, rim.lip(t), 8);
    }

    private void holdTick(ServerLevel level) {
        LivingEntity v = grab.victim();
        if (v == null || !grab.holding(this, anchor)) {
            letGo();                 // it got away, died, or went creative
            cooldown = COOLDOWN;
            cling();
            return;
        }
        holdAt(level, v, anchor);
        if (core().elapsed() % 4 == 0 && rim != null) crumbs(level, rim.lip(v), 2);
    }

    /** The hold is over: the drag begins. */
    private void drag(ServerLevel level) {
        LivingEntity v = grab.victim();
        if (v == null || !grab.holding(this, anchor) || !startPhase(Step.PULL, PULL_TICKS, v)) {
            letGo();
            cooldown = COOLDOWN;
            cling();
            return;
        }
        pulled = 0.0;
        level.playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.UNDERCLINGER_PULL.get(), getSoundSource(), 1.3F, 1.0F);
    }

    private void pullTick(ServerLevel level) {
        LivingEntity v = grab.victim();
        if (v == null || !grab.holding(this, anchor)) {
            letGo();
            cooldown = COOLDOWN;
            cling();
            return;
        }
        dragBy(level, v, dragLength(v) / PULL_TICKS);
        if (rim != null) crumbs(level, rim.lip(v), 2);
    }

    /** The drag ran its length: whatever is left of the 2 blocks (1 with a Grounding Charm), then it lets go over the void. */
    private void finishDrag(ServerLevel level) {
        LivingEntity v = grab.victim();
        if (v != null && grab.holding(this, anchor)) dragBy(level, v, dragLength(v));
        letGo();
        rim = null;
        cooldown = COOLDOWN;
        cling();
    }

    /** How far {@code v} is dragged out in all: {@link #PULL_DISTANCE}, half that with a Grounding Charm in the off-hand. */
    private static double dragLength(LivingEntity v) {
        return PULL_DISTANCE * Charms.grounded(v);
    }

    /**
     * Drags {@code v} up to {@code step} further straight out, never past {@link #dragLength} in all; anything solid
     * in the way ends the drag there.
     */
    private void dragBy(ServerLevel level, LivingEntity v, double step) {
        double d = Math.min(step, dragLength(v) - pulled);
        if (d > 1.0E-9) {
            Vec3 move = new Vec3(out.getStepX() * d, 0.0, out.getStepZ() * d);
            AABB there = v.getBoundingBox().move(anchor.subtract(v.position())).move(move);
            if (level.noCollision(v, there)) {
                anchor = anchor.add(move);
                pulled += d;
            } else {
                pulled = PULL_DISTANCE;
            }
        }
        holdAt(level, v, anchor);
    }

    /** Lets go of its prey where it is, with no motion of its own: over the void, it falls. */
    private void letGo() {
        LivingEntity v = grab.release();
        if (v != null && v.isAlive()) Push.set(v, new Vec3(0.0, Math.min(0.0, v.getDeltaMovement().y), 0.0));
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

    // ---- the counter: a blow makes it let go -------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean wasReaching = reaching();
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && wasReaching && reaching() && isAlive() && source.getEntity() != null
                && MechanicEvent.post(this, "let_go", source.getEntity() instanceof LivingEntity l ? l : null)) {
            recoil(level);
        }
        return hurt;
    }

    /** It lets go at once, screeches, and retreats under the island, away from the rim, for {@link #RETREAT_TICKS}. */
    private void recoil(ServerLevel level) {
        LivingEntity v = grab.victim();
        Vec3 at = rim != null && v != null ? rim.lip(v) : rim != null && core().target() != null ? rim.lip(core().target()) : position();
        letGo();
        rim = null;
        if (!startPhase(Step.RETREAT, RETREAT_TICKS, null)) {
            cling();
            return;
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.UNDERCLINGER_SCREECH.get(), getSoundSource(), 1.4F, 1.0F);
        level.sendParticles(ParticleTypes.POOF, true, true, at.x, at.y, at.z, 8, 0.3, 0.1, 0.3, 0.03);
        crumbs(level, at, 14);
        path.clear();
        goal = null;
        if (cell != null) path.addAll(Underside.away(level, next != null ? next : cell, out, RETREAT_CELLS));
    }

    // ---- fallen: climbing back up ----------------------------------------------------------------------------------

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

    /** Grips cell {@code c} at once, facing an open side if it has one. True if it clings there. */
    private boolean clingAt(BlockPos c) {
        if (!Underside.cell(level(), c)) return false;
        Direction side = Underside.openSide(level(), c);
        cell = c;
        next = null;
        stepDone = 0.0;
        path.clear();
        goal = null;
        if (!startPhase(Step.CLING, 0, getTarget())) {
            cell = null;
            return false;
        }
        if (side != null) out = side;
        face(out);
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        setPos(centre(c));
        return true;
    }

    // ---- what the player sees ----------------------------------------------------------------------------------

    /** Crumbs of the rim's stone, falling from its lip. */
    private void crumbs(ServerLevel level, Vec3 at, int count) {
        BlockState stone = rim != null ? level.getBlockState(rim.ground()) : Blocks.END_STONE.defaultBlockState();
        if (stone.isAir()) stone = Blocks.END_STONE.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, stone), true, true, at.x, at.y, at.z, count, 0.25, 0.05, 0.25, 0.05);
    }

    private void setThickness(int thick) {
        int t = Mth.clamp(thick, 1, 3);
        core().setFlag(THICK_BIT0, (t & 1) != 0);
        core().setFlag(THICK_BIT1, (t & 2) != 0);
    }

    /** How thick the rim it reaches over is, 1..3 (from the synced flags; the model stretches its arms to the edge). */
    public static int thickness(int flags) {
        return Math.max(1, flags & 3);
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

    /** Clinging or climbing, it is held fast: only its own crawl moves it, and no blow knocks it off. */
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
        letGo();                     // a dead clinger drags no one: its prey stays where it was held
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        letGo();
        super.onRemoval(reason);
    }

    // ---- test hooks --------------------------------------------------------------------------------------------

    /**
     * Grips the underside of {@code block} at once (the cell under it), facing the open side of the ceiling if there is
     * one. False if there is no underside to grip there. Public as a test hook.
     */
    public boolean hangUnder(BlockPos block) {
        return !level().isClientSide() && clingAt(block.below());
    }

    /** The cell it grips, or null on the ground. Public as a test hook. */
    public @Nullable BlockPos cell() {
        return cell;
    }

    /** Ticks until it may reach again after a drag. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it reach again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    /** The way out over the rim it faces. Public as a test hook. */
    public Direction out() {
        return out;
    }

    /** Who it holds, or null. Public as a test hook. */
    public @Nullable LivingEntity victim() {
        return grab.victim();
    }

    // ---- saving --------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("ReachCooldown", cooldown);
    }

    /** A reload ends any phase and its hold; on its first tick it grips the underside right above it again. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cooldown = Math.max(0, input.getIntOr("ReachCooldown", 0));
        gripOnFirstTick = true;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return reaching() ? null : ModSounds.UNDERCLINGER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.UNDERCLINGER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.UNDERCLINGER_DEATH.get();
    }

    // ---- the rim: where someone stands at an edge ------------------------------------------------------------------

    /**
     * A rim it can reach over: the block {@code ground} its prey stands on, the way {@code out} over the edge, the cell
     * under the rim's column it grips to reach ({@code cell}), and how many blocks thick the rim is above that cell.
     */
    public record Rim(BlockPos ground, Direction out, BlockPos cell, int thickness) {
        /** A block with at least this many open cells under it is a bridge (or an overhang): open air below. */
        public static final int BRIDGE_DROP = 4;
        /** How far out from the block under its feet it looks for the edge: that block and the next. */
        public static final int SEARCH = 2;

        /**
         * The rim {@code t} stands at, or null. It stands at one when the block under its feet is solid and, along one
         * of the four sides, the ground ends within 2 blocks in a drop the Brinkram calls an edge ({@link Brinkram.Brink}:
         * more than 4 down, or the void), and either that edge is within {@link #EDGE_RANGE} of it across, or the block
         * under it has open air under it (a bridge). The nearest edge wins. The rim's column must have an underside to
         * grip within 3 blocks of its top.
         */
        public static @Nullable Rim find(Level level, LivingEntity t) {
            BlockPos ground = BlockPos.containing(t.getX(), t.getY() - 0.2, t.getZ());   // read the block: a survivor never gets onGround
            if (!solid(level, ground)) return null;
            boolean bridge = true;
            for (int i = 1; i <= BRIDGE_DROP; i++) bridge &= !solid(level, ground.below(i));
            Rim best = null;
            double bestDist = Double.MAX_VALUE;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int k = 1; k <= SEARCH; k++) {
                    BlockPos column = ground.relative(d, k);
                    BlockPos feet = column.above();
                    if (solid(level, feet)) break;                                  // a wall that way, not an edge
                    if (!Brinkram.Brink.edge(dy -> solid(level, feet.above(dy)))) {
                        if (solid(level, column)) continue;                         // level ground: look one further
                        break;                                                      // a step down: still ground
                    }
                    double off = (t.getX() - (ground.getX() + 0.5)) * d.getStepX() + (t.getZ() - (ground.getZ() + 0.5)) * d.getStepZ();
                    double dist = (k - 0.5) - off;                                  // across, from its middle to the edge
                    if ((dist <= EDGE_RANGE || bridge) && dist < bestDist) {
                        BlockPos top = ground.relative(d, k - 1);
                        BlockPos under = Underside.under(level, top, (int) Math.ceil(GRAB_REACH) + 1);
                        if (under != null) {
                            best = new Rim(ground, d, under, top.getY() - under.getY());
                            bestDist = dist;
                        }
                    }
                    break;
                }
            }
            return best;
        }

        /** The point on the rim's top edge beside {@code t}, where the arms come over and the crumbs fall. */
        public Vec3 lip(LivingEntity t) {
            BlockPos top = cell.above(thickness);
            double x = out.getStepX() != 0 ? top.getX() + 0.5 + out.getStepX() * 0.5 : t.getX();
            double z = out.getStepZ() != 0 ? top.getZ() + 0.5 + out.getStepZ() * 0.5 : t.getZ();
            return new Vec3(x, top.getY() + 1.0, z);
        }

        private static boolean solid(BlockGetter level, BlockPos pos) {
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }
    }

    // ---- the underside: crawling on ceilings -----------------------------------------------------------------------

    /**
     * Crawling on the underside of blocks, for any mob that clings to a ceiling. A cling cell is an open cell (no
     * collision, no fluid) whose block above is sturdy underneath: the mob's back is pressed to that block. The mob
     * moves only between cling cells next to each other along one of the four sides, level or a step up or down where
     * the ceiling steps, so wherever it is, it grips something: it never hangs in open air.
     */
    public static final class Underside {
        /** Cells a path search visits at most. */
        public static final int MAX_NODES = 800;

        private Underside() {}

        /** True if {@code c} is a cling cell. */
        public static boolean cell(BlockGetter level, BlockPos c) {
            BlockState here = level.getBlockState(c);
            if (!here.getCollisionShape(level, c).isEmpty() || !here.getFluidState().isEmpty()) return false;
            BlockPos above = c.above();
            return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
        }

        /** The cling cells a mob in {@code c} can crawl to next: along each side, level, a step up or a step down. */
        public static List<BlockPos> neighbours(BlockGetter level, BlockPos c) {
            List<BlockPos> out = new ArrayList<>(4);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos side = c.relative(d);
                if (cell(level, side)) {
                    out.add(side);
                } else if (open(level, side) && cell(level, side.above())) {
                    out.add(side.above());               // the ceiling steps up: round the inside corner
                } else if (!open(level, side) && cell(level, side.below())) {
                    out.add(side.below());               // the ceiling steps down: round the outside corner
                }
            }
            return out;
        }

        /** True if {@code b} is one crawl step from {@code a}. */
        public static boolean adjacent(BlockPos a, BlockPos b) {
            int dx = Math.abs(a.getX() - b.getX()), dy = Math.abs(a.getY() - b.getY()), dz = Math.abs(a.getZ() - b.getZ());
            return dx + dz == 1 && dy <= 1;
        }

        private static boolean open(BlockGetter level, BlockPos p) {
            return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
        }

        /**
         * The cells to crawl through from {@code from} to {@code to}, {@code from} left out; empty if there is no way
         * along the underside within {@code range} blocks across of {@code from}.
         */
        public static List<BlockPos> path(BlockGetter level, BlockPos from, BlockPos to, int range) {
            if (from.equals(to)) return List.of();
            Map<BlockPos, BlockPos> came = new HashMap<>();
            ArrayDeque<BlockPos> open = new ArrayDeque<>();
            came.put(from, from);
            open.add(from);
            while (!open.isEmpty() && came.size() < MAX_NODES) {
                BlockPos c = open.poll();
                for (BlockPos n : neighbours(level, c)) {
                    if (came.containsKey(n) || Math.abs(n.getX() - from.getX()) > range || Math.abs(n.getZ() - from.getZ()) > range) continue;
                    came.put(n, c);
                    if (n.equals(to)) return walkBack(came, from, to);
                    open.add(n);
                }
            }
            return List.of();
        }

        /**
         * The way back under the island from {@code from}, away from an edge that lies {@code out} of it: to the cell
         * within {@code steps} crawl steps that lies farthest the other way (nearest first among equals).
         */
        public static List<BlockPos> away(BlockGetter level, BlockPos from, Direction out, int steps) {
            Map<BlockPos, BlockPos> came = new HashMap<>();
            Map<BlockPos, Integer> depth = new HashMap<>();
            ArrayDeque<BlockPos> open = new ArrayDeque<>();
            came.put(from, from);
            depth.put(from, 0);
            open.add(from);
            BlockPos best = from;
            double bestScore = 0.0;
            while (!open.isEmpty()) {
                BlockPos c = open.poll();
                int dc = depth.get(c);
                double score = -((c.getX() - from.getX()) * out.getStepX() + (c.getZ() - from.getZ()) * out.getStepZ());
                if (score > bestScore + 1.0E-9) {
                    best = c;
                    bestScore = score;
                }
                if (dc >= steps) continue;
                for (BlockPos n : neighbours(level, c)) {
                    if (came.containsKey(n)) continue;
                    came.put(n, c);
                    depth.put(n, dc + 1);
                    open.add(n);
                }
            }
            return best.equals(from) ? List.of() : walkBack(came, from, best);
        }

        private static List<BlockPos> walkBack(Map<BlockPos, BlockPos> came, BlockPos from, BlockPos to) {
            ArrayDeque<BlockPos> out = new ArrayDeque<>();
            for (BlockPos c = to; !c.equals(from); c = came.get(c)) out.addFirst(c);
            return new ArrayList<>(out);
        }

        /**
         * The cling cell under the bottom of the solid column whose top is {@code top}: down through at most
         * {@code depth} solid blocks to the first open cell, if that cell is a cling cell; null otherwise.
         */
        public static @Nullable BlockPos under(BlockGetter level, BlockPos top, int depth) {
            BlockPos.MutableBlockPos p = top.mutable();
            for (int i = 0; i < depth; i++) {
                if (open(level, p)) return i > 0 && cell(level, p) ? p.immutable() : null;
                p.move(Direction.DOWN);
            }
            return open(level, p) && cell(level, p) ? p.immutable() : null;
        }

        /**
         * The cling cell under the first block straight above {@code from} (the cell holding a mob's back), through open
         * cells only, at most {@code reach} cells up; null if there is none or the first block isn't sturdy underneath.
         */
        public static @Nullable BlockPos above(BlockGetter level, BlockPos from, int reach) {
            BlockPos.MutableBlockPos p = from.mutable();
            for (int i = 0; i <= reach; i++) {
                if (!open(level, p)) return null;
                if (cell(level, p)) return p.immutable();
                p.move(Direction.UP);
            }
            return null;
        }

        /** A side of {@code c} where the ceiling ends (the cell beside it is open with open air over it), or null. */
        public static @Nullable Direction openSide(BlockGetter level, BlockPos c) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos side = c.relative(d);
                if (open(level, side) && open(level, side.above())) return d;
            }
            return null;
        }
    }
}
