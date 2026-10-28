package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.end.AstralAugur.GroundMarks;
import com.panda.teeming.mob.end.Riftjackal.SafeTeleport;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Exile Wraith: a tall, legless wraith of rift-torn cloth of the small end islands and the End barrens, drifting a little
 * over the ground, a ring of rift light in its hollow hood where a face should be. It keeps a middle distance from its
 * prey (6 to 10 blocks) and marks the ground under it.
 *
 * <p>The mark (tell, 20 ticks): with a target within 12 blocks in its sight, at most every 100 ticks, it spreads its arms
 * low and a circle of radius 1 opens on the ground under the target's feet, a dark violet stain with a star-white rim
 * that fills in from the rim while motes spiral into it and a whisper rises. The circle stays where it opened. When it
 * fills, whoever still stands in it (players and mobs; never a creative or spectating player, never another wraith) is
 * torn away to other land 24 to 48 blocks off: solid ground under the whole of its feet, room to stand and open sky over
 * it ({@link SafeTeleport} finds the spot; nothing is ever put over or below the void, or in a sealed space). When there
 * is no such land within reach, nothing happens to them. Nobody is sent away twice within 200 ticks, by any wraith.
 *
 * <p>Counter: step out of the circle before it fills. A mark that catches nobody snaps back on the wraith: it is dragged
 * into its own circle and held there, snagged, for 40 ticks (it can't move or mark, though it still claws whatever comes
 * in reach), within reach of the one it marked. The fight shifts to close quarters, where its claws are its only answer.
 *
 * <p>Up close it rakes with its claws after an 8-tick wind-up, for its attack, through armour and with no knockback, so a
 * blow never throws anyone off an island.
 *
 * <p>The circles are {@link GroundMarks}; the landing search is {@link #landing}. A reload ends a mark in progress.
 */
public class ExileWraith extends TeemingMonster {
    /** IDLE, the mark filling (tell), the pose after sending someone away, the snag, the claw's wind-up, the claw. */
    public enum Step implements Phase { IDLE, TELL, EXILE, SNAGGED, WINDUP, CLAW }

    /** 1 s from the circle opening to its filling. */
    public static final int MARK_TICKS = 20;
    /** The circle's radius: the middle of a body's feet within it counts as on it. */
    public static final double MARK_RADIUS = 1.0;
    /** Feet up to this far over the circle still count as on it, so a jump at the last moment doesn't clear it. */
    public static final double MARK_HEIGHT = 1.5;
    /** Whoever is caught lands this far across from where it stood: 24 to 48 blocks. */
    public static final double EXILE_NEAR = 24.0;
    public static final double EXILE_FAR = 48.0;
    /** Nobody is sent away again within 200 ticks of being sent away. */
    public static final int EXILE_IMMUNITY = 200;
    /** It marks targets this close, and in its sight; at most one mark every 100 ticks, start to start. */
    public static final double MARK_RANGE = 12.0;
    public static final int MARK_EVERY = 100;
    /** It backs off from a target closer than 6 blocks and drifts toward one farther than 10 or out of sight. */
    public static final double KEEP_AWAY = 6.0;
    public static final double CLOSE_IN = 10.0;
    /** The landing search: the rolled distance first, then distances 6 blocks apart either side of it, within range. */
    public static final double DISTANCE_STEP = 6.0;
    /**
     * Heights a landing search starts from, against the caught one's own: each start covers 4 blocks down to 2 up
     * ({@link SafeTeleport#MAX_DROP}, {@link SafeTeleport#MAX_RISE}), so together they cover 18 blocks down to 9 up.
     */
    public static final int BAND_UP = 7;
    public static final int BAND_DOWN = 14;
    /** The pose after sending someone away. */
    public static final int EXILE_TICKS = 10;
    /** A mark that caught nobody holds it in its circle this long. */
    public static final int SNAG_TICKS = 40;
    /** The claw: an 8-tick wind-up, then a rake of its attack, at most every 30 ticks. */
    public static final int CLAW_TELL = 8;
    public static final int CLAW_TICKS = 6;
    public static final int CLAW_EVERY = 30;
    public static final float CLAW = 1.0F;                  // x attack damage
    public static final double CLAW_START = 0.8;            // blocks between hitboxes: this close it winds up
    public static final double CLAW_REACH = 1.4;            // blocks between hitboxes: a target this close when it rakes is hit
    public static final float CLAW_ARC = 120.0F;            // degrees in front of it the rake covers
    public static final float TURN = 15.0F;                 // degrees a tick it turns after its target while winding up
    /** How long the rift light lingers where someone was sent from, where they landed and along the way between. */
    public static final int SCAR_TICKS = 30;
    /** A spot to back off toward: this far out and up or down; the short search when no far spot is open. */
    public static final int BACK_SEARCH = 8, BACK_SEARCH_Y = 3, BACK_SHORT = 4, BACK_SHORT_Y = 2;

    /** Dark violet, the stain that fills the circle: dark enough to read on pale end stone. */
    public static final int STAIN = 0x3A1460;
    /** Void indigo, the thin edge just outside the rim. */
    public static final int EDGE_COLOUR = 0x1A0A30;
    /** Star-white, the rim and the motes: cool and pale, far from weak-point gold. */
    public static final int STAR_WHITE = 0xF2F0FF;
    /** Lilac, the rift light of its ring, its tears and the streak an exile leaves. */
    public static final int RIFT = 0xB47CFF;
    private static final DustParticleOptions DARK = new DustParticleOptions(STAIN, 1.5F);
    private static final DustParticleOptions EDGE = new DustParticleOptions(EDGE_COLOUR, 1.0F);
    private static final DustParticleOptions RIM = new DustParticleOptions(STAR_WHITE, 1.0F);
    private static final DustParticleOptions MOTE = new DustParticleOptions(STAR_WHITE, 0.7F);
    private static final DustParticleOptions LILAC = new DustParticleOptions(RIFT, 1.1F);
    private static final int RIM_POINTS = 18;

    /** When each body was last sent away (game time), by any wraith; kept only as long as it matters. */
    private static final Map<UUID, Long> EXILED = new HashMap<>();

    private final GroundMarks marks = new GroundMarks(MARK_HEIGHT).affects(this::takes, (level, e) -> {}).drawn(this::drawFilling, (l, m) -> {}, (l, m) -> {});
    private final List<Scar> scars = new ArrayList<>(2);
    private int markCooldown;
    private int clawCooldown;
    private long snagUntil;
    private @Nullable Roll fixedRoll;
    private int marksCast, exiles, snags, fizzles, claws;

    public ExileWraith(EntityType<? extends ExileWraith> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("exile_wraith", 0.24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new KeepRangeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && !snagged() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && !snagged() && super.canContinueToUse();
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
     * With a target it keeps a middle distance: it backs off from one closer than {@link #KEEP_AWAY}, drifts toward one
     * beyond {@link #CLOSE_IN} or out of its sight, and otherwise hangs where it is and watches.
     */
    private final class KeepRangeGoal extends Goal {
        private int repath;

        KeepRangeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return core().idle() && !snagged() && valid(getTarget());
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
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && isAlive()) {
            if (marks.placed()) {
                marks.tick(level);
                whisper(level);
            }
            scarTick(level);
            if (snagged() && tickCount % 3 == 0) {
                // threads of the mark still hold it: a dark ring tight round its feet
                for (int i = 0; i < 8; i++) {
                    double a = i * (Math.PI / 4) + tickCount * 0.2;
                    level.sendParticles(DARK, true, true, getX() + Math.cos(a) * 0.6, getY() + 0.1, getZ() + Math.sin(a) * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (markCooldown > 0) markCooldown--;
        if (clawCooldown > 0) clawCooldown--;
        if (snagged()) holdStill();
        LivingEntity target = getTarget();
        if (core().in(Step.TELL)) {
            holdStill();
            LivingEntity marked = core().target();
            if (valid(marked)) face(marked, 360.0F);
            return;
        }
        if (core().in(Step.WINDUP)) {
            holdStill();
            LivingEntity t = core().target();
            if (valid(t)) face(t, TURN);
            return;
        }
        if (core().in(Step.CLAW) || core().in(Step.EXILE)) {
            holdStill();
            return;
        }
        boolean free = core().idle() || core().in(Step.SNAGGED);
        if (free && clawCooldown == 0 && valid(target) && MobCore.gap(this, target) <= CLAW_START && getSensing().hasLineOfSight(target)) {
            startWindup(target);
        } else if (core().idle() && canMark(target)) {
            mark(target);
        }
    }

    /** True when it would mark {@code target} now: in range and sight, not lately sent away, its own wait over, free to cast. */
    public boolean canMark(@Nullable LivingEntity target) {
        return valid(target) && markCooldown == 0 && !snagged() && distanceTo(target) <= MARK_RANGE && hasLineOfSight(target)
                && !recentlyExiled(target, level().getGameTime());
    }

    /**
     * Opens a circle under {@code target}'s feet, due in {@link #MARK_TICKS}. Refuses a target sent away within the last
     * {@link #EXILE_IMMUNITY} ticks. True if it started. Public as a test hook (it skips the range, sight and wait checks).
     */
    public boolean mark(@Nullable LivingEntity target) {
        if (target == null || !core().idle() || !(level() instanceof ServerLevel level) || recentlyExiled(target, level.getGameTime())) return false;
        if (!startPhase(Step.TELL, MARK_TICKS, target)) return false;
        markCooldown = MARK_EVERY;
        marksCast++;
        holdStill();
        face(target, 360.0F);
        marks.place(level, List.of(target.position()), MARK_RADIUS, MARK_TICKS);
        level.playSound(null, getX(), getEyeY(), getZ(), ModSounds.EXILE_WRAITH_MARK.get(), getSoundSource(), 1.2F, 1.0F);
        Telegraph.particles(LILAC, 10, 0.4, 0.85).play(this);
        return true;
    }

    /** The whisper under the circle, rising as it fills. */
    private void whisper(ServerLevel level) {
        int t = core().elapsed();
        if (!core().in(Step.TELL) || t % 4 != 0 || t == 0) return;
        Vec3 c = marks.marks().getFirst().centre();
        level.playSound(null, c.x, c.y, c.z, ModSounds.EXILE_WRAITH_WHISPER.get(), getSoundSource(), 0.8F, 0.7F + 0.05F * t);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            resolve(level);
        } else if (ended == Step.WINDUP) {
            rake(level, core().target());
        } else if (ended == Step.CLAW) {
            core().end();
            clawCooldown = CLAW_EVERY;
        } else {
            core().end();
        }
    }

    /**
     * The circle fills: everyone on it is sent away ({@link #exile}). With nobody on it the mark snaps back on the wraith
     * ({@link #snag}); with someone on it but nowhere to send them, it fizzles and nothing happens.
     */
    private void resolve(ServerLevel level) {
        if (!marks.placed()) {                                  // a tell posed or started without a circle
            core().end();
            return;
        }
        GroundMarks.Mark mark = marks.marks().getFirst();
        List<LivingEntity> caught = marks.inside(level);
        marks.clear();
        LivingEntity marked = core().target();
        if (caught.isEmpty()) {
            snag(level, mark, marked);
            return;
        }
        boolean sentMarked = false;
        int sent = 0;
        for (LivingEntity e : caught) {
            if (exile(level, e) != null) {
                sent++;
                sentMarked |= e == marked;
            }
        }
        if (sent == 0) {
            fizzle(level, mark);
            core().end();
            return;
        }
        if (sentMarked && getTarget() == marked) setTarget(null);   // its prey is gone from here
        if (!startPhase(Step.EXILE, EXILE_TICKS, marked)) core().end();
    }

    /** Who a circle takes: players and mobs that portals take, never a creative or spectating player, never a wraith. */
    private boolean takes(LivingEntity e) {
        return e != this && !(e instanceof ExileWraith) && e.isAlive() && (e instanceof Mob || e instanceof Player) && e.canUsePortal(true)
                && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e) && !recentlyExiled(e, level().getGameTime());
    }

    // ---- the exile -------------------------------------------------------------------------------------------------

    /** One exile's roll: how far, and which way first (radians). */
    public record Roll(double distance, double angle) {}

    /** An exile's distance for a roll {@code u} in 0..1: 24 blocks at 0, 48 at 1, evenly between. */
    public static double exileDistance(double u) {
        return EXILE_NEAR + (EXILE_FAR - EXILE_NEAR) * Mth.clamp(u, 0.0, 1.0);
    }

    /**
     * The distances a landing search tries, in order: {@code first}, then {@link #DISTANCE_STEP} either side of it in
     * turn, nearer first, every one within {@link #EXILE_NEAR}..{@link #EXILE_FAR}.
     */
    public static double[] distances(double first) {
        double d = Mth.clamp(first, EXILE_NEAR, EXILE_FAR);
        List<Double> out = new ArrayList<>();
        out.add(d);
        for (int k = 1; ; k++) {
            boolean below = d - k * DISTANCE_STEP >= EXILE_NEAR - 1.0E-6, above = d + k * DISTANCE_STEP <= EXILE_FAR + 1.0E-6;
            if (!below && !above) break;
            if (below) out.add(d - k * DISTANCE_STEP);
            if (above) out.add(d + k * DISTANCE_STEP);
        }
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** The heights a landing search starts from, against the caught one's own: level first, then down, up, further down. */
    public static int[] bands() {
        return new int[] {0, -BAND_UP, BAND_UP, -BAND_DOWN};
    }

    /**
     * Where {@code who}, standing at {@code from}, would land: the first safe spot over solid ground in the middle of a
     * block, 24 to 48 blocks across from {@code from}, trying {@link #distances} from {@code distance}, at each the
     * directions round the circle from {@code first} ({@link SafeTeleport#angles}), and in each column the heights of
     * {@link #bands}. A spot counts only when {@link #openGround} holds. Null when there is none: then nothing happens.
     */
    public static @Nullable Vec3 landing(ServerLevel level, Entity who, Vec3 from, double distance, double first) {
        for (double d : distances(distance)) {
            for (double a : SafeTeleport.angles(first, SafeTeleport.TRIES)) {
                double x = Mth.floor(from.x + Math.cos(a) * d) + 0.5, z = Mth.floor(from.z + Math.sin(a) * d) + 0.5;
                double across = Math.hypot(x - from.x, z - from.z);
                if (across < EXILE_NEAR || across > EXILE_FAR) continue;
                for (int band : bands()) {
                    Vec3 at = SafeTeleport.InLevel.spot(level, who, x, from.y + band, z);
                    if (at != null && openGround(level, who, at)) return at;
                }
            }
        }
        return null;
    }

    /**
     * True when {@code who} could stand at {@code at} in the open: solid ground under every corner of its feet (never half
     * over the void), open sky over every column it covers (never a cave or a sealed room), and not on a block chorus fruit
     * refuses ({@code #consumable_does_not_teleport_to}).
     */
    public static boolean openGround(ServerLevel level, Entity who, Vec3 at) {
        AABB box = who.getDimensions(Pose.STANDING).makeBoundingBox(at).deflate(0.05, 0.0, 0.05);
        double[][] corners = {{at.x, at.z}, {box.minX, box.minZ}, {box.minX, box.maxZ}, {box.maxX, box.minZ}, {box.maxX, box.maxZ}};
        for (double[] c : corners) {
            BlockPos under = BlockPos.containing(c[0], at.y - 0.01, c[1]);
            if (level.getBlockState(under).getCollisionShape(level, under).isEmpty()) return false;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, under.getX(), under.getZ()) > under.getY() + 1) return false;
        }
        return !level.getBlockState(BlockPos.containing(at.x, at.y - 0.01, at.z)).is(BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO);
    }

    /**
     * Sends {@code victim} away: {@link #exileDistance} blocks in a random direction onto other land ({@link #landing}),
     * off any mount and shedding its riders. Returns where it landed, or null when there was nowhere (it stays).
     */
    private @Nullable Vec3 exile(ServerLevel level, LivingEntity victim) {
        if (!MechanicEvent.post(this, "send", victim)) return null;
        Roll roll = fixedRoll != null ? fixedRoll : new Roll(exileDistance(random.nextDouble()), random.nextDouble() * Math.PI * 2);
        Vec3 from = victim.position();
        Vec3 to = landing(level, victim, from, roll.distance(), roll.angle());
        if (to == null) return null;
        victim.stopRiding();
        victim.ejectPassengers();
        SafeTeleport.InLevel.move(level, victim, to);
        level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(victim));
        long now = level.getGameTime();
        EXILED.values().removeIf(t -> now - t >= EXILE_IMMUNITY || now < t);
        EXILED.put(victim.getUUID(), now);
        exiles++;
        Scar scar = new Scar(from, to, victim.getBbHeight(), now + SCAR_TICKS);
        scars.add(scar);
        burst(level, from, victim.getBbHeight());
        burst(level, to, victim.getBbHeight());
        streak(level, scar);
        level.playSound(null, from.x, from.y, from.z, ModSounds.EXILE_WRAITH_EXILE.get(), getSoundSource(), 1.2F, 1.0F);
        level.playSound(null, to.x, to.y, to.z, ModSounds.EXILE_WRAITH_EXILE.get(), getSoundSource(), 1.0F, 1.2F);
        return to;
    }

    /** True when {@code e} was sent away by any wraith within the last {@link #EXILE_IMMUNITY} ticks before {@code now}. */
    public static boolean recentlyExiled(Entity e, long now) {
        Long at = EXILED.get(e.getUUID());
        return at != null && now >= at && now - at < EXILE_IMMUNITY;
    }

    /** Where someone was sent from and where they landed, lit for a moment after. */
    private record Scar(Vec3 from, Vec3 to, double height, long until) {}

    /** A column of rift light where someone left or landed. */
    private static void burst(ServerLevel level, Vec3 at, double height) {
        double h = Math.max(1.0, height);
        level.sendParticles(DARK, true, true, at.x, at.y + h * 0.5, at.z, 24, 0.35, h * 0.5, 0.35, 0.0);
        level.sendParticles(RIM, true, true, at.x, at.y + h * 0.6, at.z, 16, 0.3, h * 0.6, 0.3, 0.0);
        level.sendParticles(LILAC, true, true, at.x, at.y + 0.2, at.z, 12, 0.6, 0.1, 0.6, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y + h * 0.5, at.z, 6, 0.2, h * 0.4, 0.2, 0.03);
    }

    /** The streak an exile tears through the air, from where its victim stood to where it landed. */
    private static void streak(ServerLevel level, Scar scar) {
        Vec3 a = scar.from().add(0.0, scar.height() * 0.6, 0.0), b = scar.to().add(0.0, scar.height() * 0.6, 0.0);
        double len = a.distanceTo(b);
        int n = Math.max(2, (int) Math.ceil(len / 0.8));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n);
            level.sendParticles(i % 2 == 0 ? LILAC : RIM, true, true, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** The rift light lingering after an exile: columns at both ends, the streak drawn again now and then. */
    private void scarTick(ServerLevel level) {
        if (scars.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Scar> it = scars.iterator(); it.hasNext(); ) {
            Scar s = it.next();
            if (now >= s.until()) {
                it.remove();
                continue;
            }
            if (now % 2 == 0) {
                for (Vec3 end : List.of(s.from(), s.to())) {
                    level.sendParticles(DARK, true, true, end.x, end.y + 0.8, end.z, 3, 0.2, 0.7, 0.2, 0.0);
                    level.sendParticles(RIM, true, true, end.x, end.y + 1.2, end.z, 2, 0.15, 0.9, 0.15, 0.0);
                }
            }
            if (now % 6 == 0) streak(level, s);
        }
    }

    /** Someone was on the circle but there was nowhere to send them: it collapses with a dull puff. */
    private void fizzle(ServerLevel level, GroundMarks.Mark mark) {
        fizzles++;
        Vec3 c = mark.centre();
        level.sendParticles(EDGE, true, true, c.x, c.y + 0.2, c.z, 12, 0.5, 0.1, 0.5, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, true, true, c.x, c.y + 0.2, c.z, 8, 0.4, 0.1, 0.4, 0.01);
        level.playSound(null, c.x, c.y, c.z, ModSounds.EXILE_WRAITH_WHISPER.get(), getSoundSource(), 0.8F, 0.5F);
    }

    // ---- the snag: the counter's reward --------------------------------------------------------------------------

    /**
     * The counter landed: nobody was on the circle, so it snaps back on the wraith. It is dragged into the circle (onto
     * safe ground there; with no room to stand there, it stays where it is) and held for {@link #SNAG_TICKS}.
     */
    private void snag(ServerLevel level, GroundMarks.Mark mark, @Nullable LivingEntity marked) {
        if (!startPhase(Step.SNAGGED, SNAG_TICKS, marked)) {
            fizzle(level, mark);
            core().end();
            return;
        }
        snags++;
        snagUntil = level.getGameTime() + SNAG_TICKS;
        Vec3 c = mark.centre();
        Vec3 to = SafeTeleport.InLevel.spot(level, this, c.x, c.y, c.z);
        Vec3 from = position();
        if (to != null) {
            streak(level, new Scar(from, to, getBbHeight(), 0L));
            SafeTeleport.InLevel.move(level, this, to);
            level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(this));
        }
        if (valid(marked)) face(marked, 360.0F);
        burst(level, position(), getBbHeight());
        level.playSound(null, getX(), getY(0.5), getZ(), ModSounds.EXILE_WRAITH_SNAG.get(), getSoundSource(), 1.2F, 1.0F);
    }

    /** True while a mark that caught nobody holds it in its circle. Both sides read the phase; the server also the timer. */
    public boolean snagged() {
        return level().isClientSide() ? core().in(Step.SNAGGED) : level().getGameTime() < snagUntil;
    }

    /** Snagged, it is held where it stands: it only falls. */
    @Override
    public Pin pin() {
        return core() != null && snagged() ? Pin.ROOTED : Pin.FREE;
    }

    // ---- the claw --------------------------------------------------------------------------------------------------

    /** Rears its claws at {@code target} for {@link #CLAW_TELL}; the rake follows. Public as a test hook. */
    public boolean startWindup(LivingEntity target) {
        if (!(core().idle() || core().in(Step.SNAGGED)) || !(level() instanceof ServerLevel level) || !startPhase(Step.WINDUP, CLAW_TELL, target)) return false;
        holdStill();
        face(target, 360.0F);
        level.playSound(null, getX(), getY(0.8), getZ(), ModSounds.EXILE_WRAITH_CLAW_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        Telegraph.particles(LILAC, 8, 0.4, 0.95).play(this);
        return true;
    }

    /** The rake: its attack to a target still in reach and in front, through armour and with no knockback. */
    private void rake(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.CLAW, CLAW_TICKS, target)) {
            core().end();
            clawCooldown = CLAW_EVERY;
            return;
        }
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.EXILE_WRAITH_CLAW.get(), getSoundSource(), 1.0F, 1.0F);
        if (!valid(target) || MobCore.gap(this, target) > CLAW_REACH || !inFront(target) || !MechanicEvent.post(this, "rake", target)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * CLAW;
        if (GroundMarks.hurtInPlace(level, target, damageSources().mobAttack(this), damage)) {
            claws++;
            setLastHurtMob(target);
            level.sendParticles(LILAC, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.3, 0.25, 0.0);
        }
    }

    /** True when {@code target} stands inside the arc its rake covers. */
    public boolean inFront(Entity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return true;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.degreesDifferenceAbs(toward, getYRot()) <= CLAW_ARC / 2.0F;
    }

    // ---- how the circle looks --------------------------------------------------------------------------------------

    /**
     * A filling circle, every tick: a star-white rim with a thin void-indigo edge just outside it, a dark violet stain
     * closing in from the rim to the middle as it fills, and star-white motes spiralling in from outside the rim. All of
     * it is dust, which reads at any distance and in any light, dark on pale end stone and white on dark.
     */
    private void drawFilling(ServerLevel level, GroundMarks.Mark mark, float fill) {
        Vec3 c = mark.centre();
        double r = mark.radius();
        double y = c.y + 0.08;
        double turn = tickCount * 0.08;
        if (tickCount % 2 == 0) {
            for (int i = 0; i < RIM_POINTS; i++) {
                double a = turn + i * (Math.PI * 2 / RIM_POINTS);
                level.sendParticles(RIM, true, true, c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
        } else {
            for (int i = 0; i < RIM_POINTS; i++) {
                double a = -turn + (i + 0.5) * (Math.PI * 2 / RIM_POINTS);
                level.sendParticles(EDGE, true, true, c.x + Math.cos(a) * (r + 0.2), y, c.z + Math.sin(a) * (r + 0.2), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        double front = r * (1.0 - fill);
        int stain = 6 + (int) (10 * fill);
        for (int i = 0; i < stain; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = front + Math.sqrt(random.nextDouble()) * (r - front);
            level.sendParticles(DARK, true, true, c.x + Math.cos(a) * d, y, c.z + Math.sin(a) * d, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (front > 0.1) {
            int n = Math.max(5, (int) Math.ceil(Math.PI * 2 * front / 0.35));
            for (int i = 0; i < n; i++) {
                double a = turn * 2 + i * (Math.PI * 2 / n);
                level.sendParticles(LILAC, true, true, c.x + Math.cos(a) * front, y, c.z + Math.sin(a) * front, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        for (int k = 0; k < 3; k++) {                                      // motes spiralling in, a turn every 10 ticks
            double phase = ((tickCount + k * 3) % 10) / 10.0;
            double a = phase * Math.PI * 2 + k * (Math.PI * 2 / 3);
            double d = r + 0.9 * (1.0 - phase);
            level.sendParticles(MOTE, true, true, c.x + Math.cos(a) * d, y + 0.5 * (1.0 - phase), c.z + Math.sin(a) * d, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ---- moving and facing -----------------------------------------------------------------------------------------

    /** While it casts, rakes or is held, its body faces the way it looks. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    private void face(Entity target, float maxTurn) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
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
     * (by a wall or an edge) or the spot found lies off to the side.
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

    /** It drifts rather than walks: a fall never hurts it. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    /** A dead wraith finishes nothing: its circle goes out with it. */
    @Override
    public void die(DamageSource source) {
        marks.clear();
        snagUntil = 0L;
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        marks.clear();
        scars.clear();
        super.onRemoval(reason);
    }

    // ---- saved state -----------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("MarkCooldown", markCooldown);
        output.putInt("ClawCooldown", clawCooldown);
    }

    /** The waits to its next mark and its next rake survive a reload; a mark in progress and a snag end. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        markCooldown = Math.clamp(input.getIntOr("MarkCooldown", 0), 0, MARK_EVERY);
        clawCooldown = Math.clamp(input.getIntOr("ClawCooldown", 0), 0, CLAW_EVERY);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Makes every later exile use {@code roll} instead of a random one (null rolls again). Public as a test hook. */
    public void fixRoll(@Nullable Roll roll) {
        fixedRoll = roll;
    }

    /** Ticks until it may mark again. Public as a test hook. */
    public int markCooldown() {
        return markCooldown;
    }

    /** Sets the wait until it may mark again (0: at once). Public as a test hook. */
    public void setMarkCooldown(int ticks) {
        markCooldown = Math.max(0, ticks);
    }

    /** Lets it rake again at once. Public as a test hook. */
    public void resetClaw() {
        clawCooldown = 0;
    }

    /** The circle on the ground now, or null. Public as a test hook. */
    public GroundMarks.@Nullable Mark mark() {
        return marks.placed() ? marks.marks().getFirst() : null;
    }

    /** Who the circle would send away if it filled now. Public as a test hook. */
    public List<LivingEntity> caught() {
        return level() instanceof ServerLevel level ? marks.inside(level) : List.of();
    }

    /** Marks opened, bodies sent away, snags, fizzles and rakes that landed so far. Public as test hooks. */
    public int marksCast() {
        return marksCast;
    }

    public int exiles() {
        return exiles;
    }

    public int snags() {
        return snags;
    }

    public int fizzles() {
        return fizzles;
    }

    public int claws() {
        return claws;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.EXILE_WRAITH_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.EXILE_WRAITH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.EXILE_WRAITH_DEATH.get();
    }
}
