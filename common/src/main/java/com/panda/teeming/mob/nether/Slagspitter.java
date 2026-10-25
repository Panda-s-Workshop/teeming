package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Zone;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Slagspitter: a squat, toad-like lump of cooling slag of the Nether wastes that bombards from range. With a target 6
 * to 20 blocks away in its sight it sits still for 16 ticks while a glowing ring swells round its throat (tell: the
 * pose, a gurgle, embers rising), then spits a glob of slag in a high arc at the spot its target stood on. The glob
 * flies 0.6 blocks a tick across the ground, never homes, and stops at the first block or hitbox: a hit does 5 as a
 * mob's projectile, so a shield raised toward the spitter takes it. Where the glob lands the ground burns for 80
 * ticks: anyone standing within 1.5 blocks of the spot is set on fire. The patch is an area the spitter owns, never a
 * fire block. One tell every 60 ticks.
 *
 * <p>A survival player within 5 blocks makes it hop away to get its range back: a leap toward a spot away from the
 * player, or straight away from them when no spot is found. Counter: close the distance. A player who comes within 5
 * blocks while the ring glows makes it choke on its shot (the glob is lost, it fizzles) and hop away; kept hopping it
 * never gets a shot off. Up close it only nips for its attack (3). Its own patches and fire never hurt it.
 */
public class Slagspitter extends TeemingMonster {
    /** IDLE, the swelling ring (tell), the spit, the hop away. */
    public enum Step implements Phase { IDLE, TELL, LOB, HOP }

    public static final int TELL_TICKS = 16;              // the ring swells for 0.8 s before the spit
    /** It spits at targets at least this far away... */
    public static final double MIN_RANGE = 6.0;
    /** ...and no farther than this. */
    public static final double MAX_RANGE = 20.0;
    public static final float GLOB_DAMAGE = 5.0F;
    public static final double PATCH_RADIUS = 1.5;
    public static final int PATCH_TICKS = 80;             // 4 s of burning ground
    /** A survival player this close makes it hop away, and spoils a tell in progress. */
    public static final double HOP_RANGE = 5.0;

    public static final int LOB_TICKS = 8;                // the spit: head forward, mouth wide
    public static final int LOB_EVERY = 60;               // ticks from one tell to the next
    public static final double GLOB_SPEED = 0.6;          // blocks a tick across the ground: 20 blocks in 33 ticks
    public static final double GLOB_GRAVITY = 0.05;       // blocks a tick, each tick: a 20-block lob rises about 7
    public static final double GLOB_RADIUS = 0.3;         // how far from a hitbox the glob still strikes
    /** Ticks the glob keeps falling past the spot it was aimed at (a ledge, a drop), before it is given up. */
    public static final int GLOB_OVERSHOOT = 40;
    /** Where the glob leaves its mouth: this far ahead of its feet and this high, in blocks at scale 1. */
    public static final double MOUTH_FORWARD = 0.6, MOUTH_HEIGHT = 0.4;
    public static final double PATCH_HEIGHT = 0.5;        // a hitbox reaching this low over the spot stands in it
    public static final int PATCH_FIRE_TICKS = 60;        // inside a patch: on fire for at least 3 s more
    public static final int MAX_PATCHES = 4;
    /** The hop: 0.5 blocks a tick away and 0.45 up, about 3.5 blocks in 12 ticks of air. */
    public static final double HOP_SPEED = 0.5, HOP_LIFT = 0.45;
    public static final int HOP_TICKS = 16;               // the longest a hop lasts; it ends on landing
    public static final int HOP_COOLDOWN = 10;            // ticks on the ground between hops
    /** A spot to hop toward: this far out and up or down; the short search when no far spot is open. */
    public static final int HOP_SEARCH = 10, HOP_SEARCH_Y = 4, HOP_SHORT = 4, HOP_SHORT_Y = 2;
    /** It walks back to at least {@link #MIN_RANGE} from a target this close, and closes in beyond this. */
    public static final double KEEP_RANGE = 14.0;
    public static final int NIP_COOLDOWN = 20;

    /** The ember colour of its glob and patches: red-orange, well off weak-point gold. */
    public static final int EMBER_COLOR = 0xFF5A1E;
    private static final DustParticleOptions EMBER = new DustParticleOptions(EMBER_COLOR, 1.4F);
    private static final DustParticleOptions CINDER = new DustParticleOptions(0x9A2A12, 1.0F);
    private static final int RING_POINTS = 18;

    private int cooldown;
    private int hopCooldown;
    private int nipCooldown;
    private int lobs, spoils, hops, globHits, nips;
    private Vec3 hopDir = Vec3.ZERO;

    // ---- the glob: a shot that falls along an arc (Arc below), stopped by the first block or hitbox ----------------

    private final Shot.Volley globs = new Shot.Volley(this, GLOB_RADIUS, new Shot.Rules(this::globCanHit, this::drawGlob, this::globHit, this::globStop));
    /** Where each glob in the air was spat from, so a stop against a wall can step back off it. */
    private final Map<Shot, Vec3> origins = new IdentityHashMap<>();

    // ---- the burning patches: areas it owns, never fire blocks ------------------------------------------------------

    private final Zone patches = new Zone(Zone.Shape.disc(PATCH_RADIUS, PATCH_HEIGHT), MAX_PATCHES)
            .affects(this::burns, e -> e.igniteForTicks(PATCH_FIRE_TICKS))
            .drawn(this::drawPatch);

    public Slagspitter(EntityType<? extends Slagspitter> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("slagspitter", 0.22);
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F) {
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
     * With a target it keeps its range: it backs off from one closer than {@link #MIN_RANGE} (the way it would hop),
     * closes in on one beyond {@link #KEEP_RANGE} or out of sight, and otherwise sits and watches.
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
            if (d < MIN_RANGE) {
                Vec3 dir = awayFrom(target.position());
                Vec3 spot = position().add(dir.scale(MIN_RANGE + 1.0 - d));
                getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
            } else if (d > KEEP_RANGE || !getSensing().hasLineOfSight(target)) {
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

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level()
                && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
    }

    /** True if {@code target} is within its spitting range, 6 to 20 blocks (line of sight aside). Public as a test hook. */
    public boolean inRange(LivingEntity target) {
        double d = distanceTo(target);
        return d >= MIN_RANGE && d <= MAX_RANGE;
    }

    /** The nearest survival player within {@link #HOP_RANGE}, or null. */
    private @Nullable Player crowder(ServerLevel level) {
        Player best = null;
        double bestD = HOP_RANGE;
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) continue;
            double d = distanceTo(p);
            if (d <= bestD) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (hopCooldown > 0) hopCooldown--;
        if (nipCooldown > 0) nipCooldown--;
        if (core().in(Step.TELL)) {
            tellTick(level);
            return;
        }
        if (core().in(Step.LOB)) {
            holdStill();
            return;
        }
        if (core().in(Step.HOP)) {
            hopTick();
            return;
        }
        Player crowding = crowder(level);
        if (crowding != null && hopCooldown == 0 && onGround()) {
            hop(level, crowding);
            return;
        }
        LivingEntity target = getTarget();
        if (!valid(target)) return;
        if (cooldown == 0 && inRange(target) && getSensing().hasLineOfSight(target)) {
            tell(level, target);
        } else if (nipCooldown == 0 && isWithinMeleeAttackRange(target)) {
            nip(level, target);
        }
    }

    /** Starts the tell at {@code target}: the ring swells for 16 ticks. True if it started. */
    private boolean tell(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        cooldown = LOB_EVERY;
        holdStill();
        face(target.position().subtract(position()));
        Vec3 t = throat();
        level.playSound(null, t.x, t.y, t.z, ModSounds.SLAGSPITTER_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.SMALL_FLAME, t.x, t.y, t.z, 6, 0.3, 0.1, 0.3, 0.01);
        return true;
    }

    /** Where its slag-gland ring is: round the root of the spout on its snout. */
    private Vec3 throat() {
        Vec3 f = forward(getYRot());
        double s = getScale();
        return position().add(f.x * 0.45 * s, 0.5 * s, f.z * 0.45 * s);
    }

    /**
     * The held tell: it sits still facing its target while embers rise off the swelling ring, and gurgles again
     * halfway. A survival player within {@link #HOP_RANGE} spoils it.
     */
    private void tellTick(ServerLevel level) {
        holdStill();
        LivingEntity target = core().target();
        if (valid(target)) face(target.position().subtract(position()));
        Player crowding = crowder(level);
        if (crowding != null && spoil(level, crowding)) return;
        int t = core().elapsed();
        Vec3 at = throat();
        if (t % 2 == 0) {
            double a = t * 0.8;
            double r = 0.45 * getScale();
            level.sendParticles(EMBER, at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0.0);
            level.sendParticles(ParticleTypes.SMALL_FLAME, at.x - Math.cos(a) * r, at.y + 0.1, at.z - Math.sin(a) * r, 1, 0.03, 0.03, 0.03, 0.01);
        }
        if (t == TELL_TICKS / 2) level.playSound(null, at.x, at.y, at.z, ModSounds.SLAGSPITTER_TELL.get(), getSoundSource(), 0.8F, 1.2F);
    }

    /**
     * The counter: a player crowded it while its ring glowed. It chokes on the glob (lost: no shot this round), with a
     * fizzle and a puff of smoke, and hops away from {@code by}. False if an event vetoed it.
     */
    private boolean spoil(ServerLevel level, Player by) {
        if (!MechanicEvent.post(this, "spoil", by)) return false;
        spoils++;
        Vec3 at = throat();
        level.playSound(null, at.x, at.y, at.z, ModSounds.SLAGSPITTER_FIZZLE.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 8, 0.25, 0.15, 0.25, 0.02);
        level.sendParticles(CINDER, at.x, at.y, at.z, 10, 0.3, 0.1, 0.3, 0.0);
        core().end();
        hop(level, by);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            LivingEntity target = core().target();
            if (valid(target) && startPhase(Step.LOB, LOB_TICKS, target)) spit(level, target.position());
            else core().end();
        } else if (ended == Step.HOP) {
            land();
        } else {
            core().end();
        }
    }

    /** Spits a glob from its mouth in an arc at {@code spot}; it flies on its own from the next tick. */
    private void spit(ServerLevel level, Vec3 spot) {
        face(spot.subtract(position()));
        Vec3 from = mouth();
        Shot glob = globs.fire(Arc.of(from, spot, GLOB_SPEED, GLOB_GRAVITY, GLOB_OVERSHOOT));
        origins.put(glob, from);
        lobs++;
        level.playSound(null, from.x, from.y, from.z, ModSounds.SLAGSPITTER_LOB.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.LAVA, from.x, from.y, from.z, 1, 0.1, 0.1, 0.1, 0.0);
        level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 6, 0.12, 0.08, 0.12, 0.03);
    }

    /** Spits at {@code target} at once, without the tell or the range check. Public as a test hook. */
    public boolean lobAt(LivingEntity target) {
        if (!(level() instanceof ServerLevel level) || !core().idle()) return false;
        if (!startPhase(Step.LOB, LOB_TICKS, target)) return false;
        spit(level, target.position());
        return true;
    }

    /** Where globs leave: its mouth, ahead of its feet. */
    private Vec3 mouth() {
        Vec3 f = forward(getYRot());
        double s = getScale();
        return position().add(f.x * MOUTH_FORWARD * s, MOUTH_HEIGHT * s, f.z * MOUTH_FORWARD * s);
    }

    // ---- the hop ---------------------------------------------------------------------------------------------------

    /**
     * Leaps away from {@code from}: toward a spot away from them, or straight away when none is open (or the spot found
     * lies off to the side). True if it started.
     */
    private boolean hop(ServerLevel level, Player from) {
        if (!startPhase(Step.HOP, HOP_TICKS, from)) return false;
        hopDir = awayFrom(from.position());
        getNavigation().stop();
        getMoveControl().setWait();
        face(hopDir);
        setDeltaMovement(hopDir.x * HOP_SPEED, HOP_LIFT, hopDir.z * HOP_SPEED);
        needsSync = true;
        hops++;
        level.playSound(null, getX(), getY(), getZ(), ModSounds.SLAGSPITTER_HOP.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 6, 0.3, 0.05, 0.3, 0.02);
        return true;
    }

    /**
     * Which way to get away from {@code threat}: toward an open spot away from it ({@link #HOP_SEARCH} out, or the short
     * search when none is open), or straight away when there is none or the spot found lies off to the side.
     */
    private Vec3 awayFrom(Vec3 threat) {
        Vec3 away = position().subtract(threat).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = forward(getYRot()).scale(-1.0);
        away = away.normalize();
        Vec3 spot = DefaultRandomPos.getPosAway(this, HOP_SEARCH, HOP_SEARCH_Y, threat);
        if (spot == null) spot = DefaultRandomPos.getPosAway(this, HOP_SHORT, HOP_SHORT_Y, threat);
        Vec3 dir = spot == null ? away : spot.subtract(position()).horizontal();
        return dir.lengthSqr() < 1.0E-4 || dir.normalize().dot(away) < 0.7 ? away : dir.normalize();
    }

    /** In the air it keeps facing the way it leapt; once it lands (and has been up a moment) the hop is over. */
    private void hopTick() {
        getNavigation().stop();
        getMoveControl().setWait();
        face(hopDir);
        if (core().elapsed() >= 3 && onGround()) land();
    }

    private void land() {
        hopCooldown = HOP_COOLDOWN;
        core().end();
    }

    // ---- the nip ---------------------------------------------------------------------------------------------------

    /** At melee range it only nips: its attack damage (3), no more often than every {@link #NIP_COOLDOWN} ticks. */
    private void nip(ServerLevel level, LivingEntity target) {
        nipCooldown = NIP_COOLDOWN;
        if (!MechanicEvent.post(this, "nip", target)) return;
        face(target.position().subtract(position()));
        swingForAttack(InteractionHand.MAIN_HAND);
        if (doHurtTarget(level, target)) nips++;
        playSound(ModSounds.SLAGSPITTER_NIP.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
    }

    // ---- globs and patches -----------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        // after its own move this tick, like any projectile; patches burn on through its death throes
        globs.tick(level);
        patches.tick(level);
        int stage = core().in(Step.TELL) ? 1 : 0;   // the throat ring glows through the tell
        if (core().stage() != stage) core().setStage(stage);
    }

    private boolean globCanHit(LivingEntity e) {
        return e != this && !(e instanceof Slagspitter) && e.isAlive() && e.canBeHitByProjectile() && !e.isSpectator();
    }

    /** The glob in flight: a clot of embers with flame and a thin smoke trailing it (lava flecks would smoke in plumes). */
    private void drawGlob(ServerLevel level, Vec3 from, Vec3 to) {
        for (int i = 1; i <= 3; i++) {
            Vec3 p = from.lerp(to, i / 3.0);
            level.sendParticles(EMBER, p.x, p.y, p.z, 2, 0.06, 0.06, 0.06, 0.0);
        }
        level.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 2, 0.05, 0.05, 0.05, 0.005);
        level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 1, 0.04, 0.04, 0.04, 0.0);
    }

    /** The glob reaches a hitbox: 5 as a mob's projectile (a shield toward it takes it), then it splashes underfoot. */
    private void globHit(ServerLevel level, Shot glob, LivingEntity victim, Vec3 at) {
        origins.remove(glob);
        if (MechanicEvent.post(this, "glob_hit", victim)) {
            float before = victim.getHealth() + victim.getAbsorptionAmount();
            victim.hurtServer(level, Shot.damage(this), GLOB_DAMAGE);
            if (victim.getHealth() + victim.getAbsorptionAmount() < before) globHits++;   // a shield took none of it
        }
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 1, 0.15, 0.1, 0.15, 0.0);
        splat(level, groundBelow(level, victim.position()));
    }

    /** The glob hits the ground (or a wall, and drops down it), or falls past its overshoot: it splashes where it is. */
    private void globStop(ServerLevel level, Shot glob, Vec3 at, boolean blocked) {
        Vec3 from = origins.remove(glob);
        Vec3 back = at;
        if (from != null) {
            Vec3 toward = from.subtract(at).horizontal();
            if (toward.lengthSqr() > 1.0E-6) back = at.add(toward.normalize().scale(0.05));   // off the wall's face
        }
        splat(level, groundBelow(level, back));
    }

    /** The top of the first solid block at or below {@code at}, within a few blocks; {@code at} itself over a drop. */
    private static Vec3 groundBelow(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 0.2, at.z).mutable();
        for (int i = 0; i <= 6; i++, pos.move(Direction.DOWN)) {
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) return new Vec3(at.x, pos.getY() + shape.max(Direction.Axis.Y), at.z);
        }
        return at;
    }

    /** The glob splashes: a burning patch opens round {@code spot} for 80 ticks. Returns false if an event vetoed it. */
    private boolean splat(ServerLevel level, Vec3 spot) {
        level.playSound(null, spot.x, spot.y, spot.z, ModSounds.SLAGSPITTER_SPLAT.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + 0.1, spot.z, 2, 0.3, 0.05, 0.3, 0.0);
        level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y + 0.1, spot.z, 14, 0.5, 0.05, 0.5, 0.03);
        if (!MechanicEvent.post(this, "patch", core().target())) return false;
        patches.open(level, spot, PATCH_TICKS);
        return true;
    }

    /** Opens a burning patch at {@code spot} at once, as a glob would. Public as a test hook. */
    public boolean openPatch(Vec3 spot) {
        return level() instanceof ServerLevel level && splat(level, spot);
    }

    /** Who a patch burns: anything alive standing in it but slagspitters, spectators and creative players. */
    private boolean burns(LivingEntity e) {
        return !(e instanceof Slagspitter) && e.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    /**
     * The patch as players see it: a ring of embers tracing its edge every few ticks, so the border shows on any ground,
     * and flames, smoke and a rare lava fleck over it.
     */
    private void drawPatch(ServerLevel level, Vec3 at) {
        RandomSource r = getRandom();
        if (tickCount % 4 == 0) {
            double turn = tickCount * 0.03;
            for (int i = 0; i < RING_POINTS; i++) {
                double a = turn + i * (Math.PI * 2 / RING_POINTS);
                level.sendParticles(EMBER, at.x + Math.cos(a) * PATCH_RADIUS, at.y + 0.1, at.z + Math.sin(a) * PATCH_RADIUS, 1, 0.0, 0.03, 0.0, 0.0);
            }
        }
        if (tickCount % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * PATCH_RADIUS;
                level.sendParticles(ParticleTypes.FLAME, at.x + Math.cos(a) * d, at.y + 0.05, at.z + Math.sin(a) * d, 1, 0.0, 0.02, 0.0, 0.01);
            }
        }
        if (tickCount % 3 == 0) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * PATCH_RADIUS;
            level.sendParticles(ParticleTypes.SMOKE, at.x + Math.cos(a) * d, at.y + 0.2, at.z + Math.sin(a) * d, 1, 0.0, 0.05, 0.0, 0.01);
        }
        if (tickCount % 20 == 0) level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.1, at.z, 1, 0.4, 0.0, 0.4, 0.0);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        globs.clear();
        origins.clear();
        patches.clear();
        super.onRemoval(reason);
    }

    // ---- facing ----------------------------------------------------------------------------------------------------

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
    }

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    // ---- the arc ---------------------------------------------------------------------------------------------------

    /**
     * A lobbed shot's way: from {@code from} it crosses the ground at a fixed speed and falls under a fixed gravity,
     * timed to come down on {@code to}; after that it keeps falling along the same curve for {@code overshoot} more
     * ticks, so a spot over a drop still gets its glob. A {@link Shot.Path}, the same shape as {@code Shot.Path.line}.
     */
    public static final class Arc {
        private Arc() {}

        /** Ticks of flight to come down {@code across} blocks away (at least one). */
        public static double flightTicks(double across, double speed) {
            return Math.max(1.0, across / speed);
        }

        public static Shot.Path of(Vec3 from, Vec3 to, double speed, double gravity, double overshoot) {
            double across = Math.hypot(to.x - from.x, to.z - from.z);
            double t = flightTicks(across, speed);
            double vx = (to.x - from.x) / t, vz = (to.z - from.z) / t;
            double vy = (to.y - from.y) / t + 0.5 * gravity * t;
            double ticks = t + overshoot;
            return new Shot.Path() {
                @Override
                public Vec3 at(double s) {
                    return new Vec3(from.x + vx * s, from.y + vy * s - 0.5 * gravity * s * s, from.z + vz * s);
                }

                @Override
                public double ticks() {
                    return ticks;
                }
            };
        }
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** How many globs are in the air. */
    public int globsInFlight() {
        return globs.size();
    }

    /** The centres of its burning patches. */
    public List<Vec3> patches() {
        return patches.centres();
    }

    /** Globs it has spat, tells spoiled by a crowding player, hops, globs that hurt what they hit, nips that landed. */
    public int lobs() {
        return lobs;
    }

    public int spoils() {
        return spoils;
    }

    public int hops() {
        return hops;
    }

    public int globHits() {
        return globHits;
    }

    public int nips() {
        return nips;
    }

    /** Ticks until it may start its next tell. */
    public int cooldown() {
        return cooldown;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SLAGSPITTER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SLAGSPITTER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SLAGSPITTER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.1F;
    }
}
