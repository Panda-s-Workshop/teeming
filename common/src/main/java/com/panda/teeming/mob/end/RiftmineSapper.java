package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Riftmine Sapper: a small, hunched digger in purpur plating with a great satchel of glowing stones on its back.
 *
 * <p>Its mines: 20 ticks after it finds a target and every 100 ticks after, with the target within 10 blocks and in
 * sight, it flicks a stone from its satchel onto
 * the ground where its target is walking (at most 4 lie at once; it sows the next only once one is spent). A mine is not
 * a block: the sapper keeps each one as a spot on the ground and an age, saved with it, and draws it with particles. A
 * mine arms over 40 ticks (the tell): a dark violet ring of radius 1.5 lies on the ground, a violet arc sweeps round its
 * rim and a glyph pulses at its middle, ticking faster as it arms. Before then it is harmless. Armed, its glyph burns
 * star-white. Anything but a sapper that steps within 1.5 of an armed mine sets it off, and everyone standing in it is
 * launched straight up, about 10 blocks: never sideways, so no one is thrown off an island. The fall is what hurts;
 * Slow Falling or a water bucket saves you.
 *
 * <p>Counter: arrows and any other projectile set a mine off from range: an armed one goes off where it lies, launching
 * whoever stands in it; one still arming is knocked out and fizzles. Payoff, a turned weapon: a mine set off while the
 * sapper stands in it throws the sapper up too, and the fall hurts it like anyone else; thrown, it loses its grip on the
 * rest of its field and its other mines fizzle. When the sapper dies its mines fizzle out at once, harmless, and are gone
 * within 20 ticks.
 *
 * <p>It keeps its distance: it backs off from a target closer than 3 blocks and follows one farther than 8. Cornered,
 * it jabs with its pick: a 10-tick wind-up with the pick raised (tell), then its attack through armour with no
 * knockback, if its target is still within a block (across or up and down) and in sight; at most once every 36 ticks.
 */
public class RiftmineSapper extends TeemingMonster {
    /** IDLE, a stone flicked from the satchel, the pick raised (tell), the jab. */
    public enum Step implements Phase { IDLE, PLANT, TELL, JAB }

    /** One mine every 100 ticks while it has a target in reach; the first 20 ticks after it finds one. */
    public static final int PLANT_EVERY = 100;
    public static final int FIRST_PLANT = 20;
    /** At most 4 mines lie at once for each sapper. */
    public static final int MAX_MINES = 4;
    /** A mine arms over 40 ticks: harmless until then. */
    public static final int ARM_TICKS = 40;
    /** A mine's radius across: stepping within it sets an armed mine off, and everyone within it is launched. */
    public static final double MINE_RADIUS = 1.5;
    /** How far over a mine a body's feet still count as standing on it. */
    public static final double MINE_HEIGHT = 1.0;
    /** The launch: straight up at 1.35 blocks a tick, a rise of about 10 blocks under ordinary gravity. */
    public static final double LAUNCH_SPEED = 1.35;
    /** It sows only at a target within this many blocks and in sight. */
    public static final double PLANT_RANGE = 10.0;
    /** A mine lands where its target will be this many ticks on at its pace, at most this many blocks ahead. */
    public static final int LEAD_TICKS = 10;
    public static final double MAX_LEAD = 2.0;
    /** A spent mine's fizzle is drawn this long, harmless, then it is gone. */
    public static final int FIZZLE_TICKS = 20;
    /** A mine farther than this from its sapper fizzles: the sapper has left its field behind. */
    public static final double MINE_LEASH = 24.0;
    /** The flick of a stone from its satchel (the mine lands as it starts). */
    public static final int PLANT_TICKS = 10;
    /** The jab: the pick held up this long (tell), then the blow on the jab's first tick, then a rest. */
    public static final int TELL_TICKS = 10;
    public static final int JAB_TICKS = 6;
    public static final int JAB_COOLDOWN = 20;
    /** Blocks between hitboxes, across or up and down: this close (and in sight) it jabs, and the jab lands. */
    public static final double JAB_REACH = 1.0;
    /** The jab: x its attack (4), through armour. */
    public static final float STRIKE = 1.0F;
    /** It backs off from a target closer than this, and follows one farther than that. */
    public static final double KEEP_AWAY = 3.0;
    public static final double CLOSE_IN = 8.0;
    public static final double WALK_SPEED = 0.25;
    public static final int XP = 12;

    /** Dark violet: a mine's ring, deep enough to read on pale end stone and well off weak-point gold. */
    public static final int DARK_VIOLET = 0x4A1E86;
    /** Star-white: an armed mine's glyph. */
    public static final int STAR_WHITE = 0xEEF0FF;
    private static final int VIOLET_ARGB = 0xFF000000 | DARK_VIOLET;
    private static final int STAR_ARGB = 0xFF000000 | STAR_WHITE;
    private static final DustParticleOptions VOID_EDGE = new DustParticleOptions(0x24163E, 1.1F);
    private static final DustParticleOptions VIOLET_DUST = new DustParticleOptions(DARK_VIOLET, 2.0F);
    private static final DustParticleOptions STAR_DUST = new DustParticleOptions(STAR_WHITE, 2.0F);
    private static final DustParticleOptions ASH_DUST = new DustParticleOptions(0x5A5266, 0.8F);
    private static final DustParticleOptions RIM_DUST = new DustParticleOptions(DARK_VIOLET, 1.7F);
    private static final int RIM_POINTS = 28;

    /** Flag bit: thrown up by a mine, until it lands (the model flails). */
    public static final int FLAG_FLUNG = 0;
    /** Flag bits 1-3: how many of its mines lie out (the model darkens that many stones on the satchel). */
    public static final int FLAG_MINES_SHIFT = 1;

    private final Minefield field = new Minefield(MAX_MINES, ARM_TICKS, FIZZLE_TICKS);
    private int plantClock = FIRST_PLANT;
    private int jabCooldown;
    private @Nullable Vec3 targetWas;
    private Vec3 targetPace = Vec3.ZERO;
    private @Nullable Vec3 tossedTo;
    private long flungAt = Long.MIN_VALUE;
    private int plants, blasts, launches, shotOff, fizzles, flung, jabs, tells;

    public RiftmineSapper(EntityType<? extends RiftmineSapper> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("riftmine_sapper", WALK_SPEED);
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
     * With a target it keeps a middle distance: it backs off from one closer than {@link #KEEP_AWAY}, walks toward one
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
                getNavigation().moveTo(spot.x, spot.y, spot.z, 1.1);
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

    /** The way away from {@code threat}: a walkable spot away from it if there is one, else straight away. */
    private Vec3 awayFrom(Vec3 threat) {
        Vec3 away = position().subtract(threat).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0.0F, getYRot()).scale(-1.0);
        away = away.normalize();
        Vec3 spot = DefaultRandomPos.getPosAway(this, 6, 3, threat);
        Vec3 dir = spot == null ? away : spot.subtract(position()).horizontal();
        return dir.lengthSqr() < 1.0E-4 || dir.normalize().dot(away) < 0.6 ? away : dir.normalize();
    }

    // ---- every tick ------------------------------------------------------------------------------------------------

    /**
     * Its mines run from {@code tick()}, so they keep working for a sapper without AI; a dying sapper's mines fizzle at
     * once and set nothing off.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (isDeadOrDying() && field.live() > 0) fizzleAll(level);
        tickMines(level);
        if (core().flag(FLAG_FLUNG) && level.getGameTime() > flungAt + 2 && (onGround() || isInWater())) core().setFlag(FLAG_FLUNG, false);
        int shown = Math.min(7, field.live());
        int flags = core().flags();
        if ((flags >> FLAG_MINES_SHIFT & 7) != shown) {
            for (int b = 0; b < 3; b++) core().setFlag(FLAG_MINES_SHIFT + b, (shown >> b & 1) != 0);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (jabCooldown > 0) jabCooldown--;
        LivingEntity target = getTarget();
        trackPace(target);
        // its sowing clock runs only while it has a target; a fresh one waits at least the first delay
        if (!valid(target)) plantClock = Math.max(plantClock, FIRST_PLANT);
        else if (plantClock > 0) plantClock--;
        if (core().in(Step.PLANT)) {
            holdStill();
            if (tossedTo != null) face(tossedTo, 30.0F);
            return;
        }
        if (core().in(Step.TELL) || core().in(Step.JAB)) {
            holdStill();
            LivingEntity t = core().target();
            if (valid(t)) face(t.position(), 30.0F);
            return;
        }
        if (!valid(target)) return;
        boolean sees = getSensing().hasLineOfSight(target);
        if (plantClock == 0 && field.live() < MAX_MINES && distanceTo(target) <= PLANT_RANGE && sees && sow(level, target)) return;
        if (jabCooldown == 0 && MobCore.gap(this, target) <= JAB_REACH && sees) windUp(target);
    }

    /** Where its target is heading: the step it took this tick (players move on their own client, so it is measured). */
    private void trackPace(@Nullable LivingEntity target) {
        if (!valid(target)) {
            targetWas = null;
            targetPace = Vec3.ZERO;
            return;
        }
        Vec3 now = target.position();
        targetPace = targetWas == null ? Vec3.ZERO : now.subtract(targetWas).horizontal();
        targetWas = now;
    }

    // ---- sowing mines ----------------------------------------------------------------------------------------------

    /**
     * Flicks a stone from its satchel onto the ground where {@code target} is walking. True if a mine was sown: the
     * spot must be on solid ground (never over the void), and a pack may veto the plant.
     */
    private boolean sow(ServerLevel level, LivingEntity target) {
        Vec3 lead = targetPace.scale(LEAD_TICKS);
        if (lead.length() > MAX_LEAD) lead = lead.normalize().scale(MAX_LEAD);
        Vec3 spot = groundSpot(level, target.position().add(lead));
        if (spot == null) spot = groundSpot(level, target.position());
        if (spot == null) return false;
        return plantAt(spot, target);
    }

    /**
     * Sows a mine at {@code at} (settled on the ground below it) as a plant at {@code target} would. False if it is not
     * idle or alive, already has {@link #MAX_MINES} out, the spot has no ground under it, or the plant was vetoed.
     * Public as a test hook.
     */
    public boolean plantAt(Vec3 at, @Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel level) || isDeadOrDying() || !core().idle() || field.live() >= MAX_MINES) return false;
        Vec3 spot = groundSpot(level, at);
        if (spot == null || !startPhase(Step.PLANT, PLANT_TICKS, target)) return false;
        field.plant(spot, level.getGameTime());
        plantClock = PLANT_EVERY;
        plants++;
        tossedTo = spot;
        face(spot, 360.0F);
        Vec3 bag = satchel();
        level.playSound(null, bag.x, bag.y, bag.z, ModSounds.RIFTMINE_SAPPER_PLANT.get(), getSoundSource(), 0.9F, 0.95F + random.nextFloat() * 0.1F);
        level.sendParticles(new TrailParticleOption(spot.add(0, 0.1, 0), STAR_ARGB, 8), true, true, bag.x, bag.y, bag.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(new TrailParticleOption(spot.add(0, 0.1, 0), VIOLET_ARGB, 10), true, true, bag.x, bag.y + 0.2, bag.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, spot)), true, true, spot.x, spot.y + 0.1, spot.z, 10, 0.3, 0.05, 0.3, 0.1);
        return true;
    }

    /** Sows a mine at {@code at} that is armed at once (its age past arming). Public as a test hook. */
    public boolean plantArmed(Vec3 at) {
        if (!plantAt(at, null)) return false;
        field.arm(field.mines.getLast(), level().getGameTime());
        core().end();
        return true;
    }

    /** The spot on the ground at or below {@code at}, or null with no solid ground under it within reach (the void). */
    private static @Nullable Vec3 groundSpot(ServerLevel level, Vec3 at) {
        Vec3 g = AstralAugur.GroundMarks.ground(level, at);
        BlockPos under = BlockPos.containing(g.x, g.y - 0.05, g.z);
        return level.getBlockState(under).getCollisionShape(level, under).isEmpty() ? null : g;
    }

    /** Where its satchel rides: up over its back. */
    private Vec3 satchel() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(getX() + Mth.sin(yaw) * 0.35, getY() + getBbHeight() * 0.95, getZ() - Mth.cos(yaw) * 0.35);
    }

    // ---- the mines, each tick ----------------------------------------------------------------------------------------

    private void tickMines(ServerLevel level) {
        if (field.isEmpty()) return;
        boolean alive = !isDeadOrDying();
        for (Minefield.Mine m : field.tick(level.getGameTime())) {               // the mines that armed this tick
            level.playSound(null, m.at.x, m.at.y, m.at.z, ModSounds.RIFTMINE_SAPPER_ARMED.get(), getSoundSource(), 0.9F, 1.0F);
            level.sendParticles(STAR_DUST, true, true, m.at.x, m.at.y + 0.15, m.at.z, 10, 0.25, 0.05, 0.25, 0.0);
        }
        List<Minefield.Mine> spent = new ArrayList<>();
        for (Minefield.Mine m : field.mines) {
            if (m.dud) {
                drawFizzle(level, m);
                continue;
            }
            if (!alive) continue;
            if (distanceToSqr(m.at) > MINE_LEASH * MINE_LEASH) {
                spent.add(m);
                continue;
            }
            drawMine(level, m);
            Projectile p = projectileIn(level, m);
            if (p != null) {
                shot(level, m, p);
            } else if (field.armed(m) && !level.getEntitiesOfClass(LivingEntity.class, standing(m), e -> setsOff(e) && on(m, e.getBoundingBox())).isEmpty()) {
                blast(level, m);
            }
        }
        for (Minefield.Mine m : spent) fizzle(level, m);
        field.sweep();
    }

    /** Whether {@code e} sets an armed mine off by stepping on it: anything alive but a sapper, a creative or a spectator. */
    private boolean setsOff(LivingEntity e) {
        return e.isAlive() && !(e instanceof RiftmineSapper) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Whether {@code e} is launched by a mine going off: anything alive in it, sappers too, but no creative or spectator. */
    private static boolean launchable(LivingEntity e) {
        return e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** The room over a mine where a body counts as standing on it: within its radius across, its feet on the mine. */
    private static AABB standing(Minefield.Mine m) {
        return new AABB(m.at.x - MINE_RADIUS, m.at.y - 0.5, m.at.z - MINE_RADIUS, m.at.x + MINE_RADIUS, m.at.y + MINE_HEIGHT, m.at.z + MINE_RADIUS);
    }

    /** True if the middle of {@code box}'s feet is within the mine's radius and its feet are on it. */
    private static boolean on(Minefield.Mine m, AABB box) {
        double dx = (box.minX + box.maxX) * 0.5 - m.at.x, dz = (box.minZ + box.maxZ) * 0.5 - m.at.z;
        return dx * dx + dz * dz <= MINE_RADIUS * MINE_RADIUS && box.minY >= m.at.y - 0.5 && box.minY <= m.at.y + MINE_HEIGHT;
    }

    /**
     * A projectile that came into the mine since it was sown: one inside its room now, or one whose path this tick ran
     * through it. Projectiles already lying there when it was sown (a stuck arrow) are ignored.
     */
    private @Nullable Projectile projectileIn(ServerLevel level, Minefield.Mine m) {
        AABB room = new AABB(m.at.x - MINE_RADIUS, m.at.y - 0.25, m.at.z - MINE_RADIUS, m.at.x + MINE_RADIUS, m.at.y + 1.5, m.at.z + MINE_RADIUS);
        List<Projectile> near = level.getEntitiesOfClass(Projectile.class, room.inflate(4.0), Entity::isAlive);
        if (m.ignored == null) {
            m.ignored = new HashSet<>();
            for (Projectile p : near) if (inside(m, p.position())) m.ignored.add(p.getUUID());
            return null;
        }
        for (Projectile p : near) {
            if (m.ignored.contains(p.getUUID())) continue;
            Vec3 was = new Vec3(p.xo, p.yo, p.zo), now = p.position();
            if (inside(m, now) || room.clip(was, now).isPresent() && crosses(m, was, now)) return p;
        }
        return null;
    }

    private static boolean inside(Minefield.Mine m, Vec3 p) {
        double dx = p.x - m.at.x, dz = p.z - m.at.z;
        return dx * dx + dz * dz <= MINE_RADIUS * MINE_RADIUS && p.y >= m.at.y - 0.25 && p.y <= m.at.y + 1.5;
    }

    /** Whether the segment from {@code a} to {@code b} passes within the mine's radius (checked at a few points along it). */
    private static boolean crosses(Minefield.Mine m, Vec3 a, Vec3 b) {
        for (int i = 0; i <= 8; i++) if (inside(m, a.lerp(b, i / 8.0))) return true;
        return false;
    }

    /** A projectile sets the mine off: an armed one goes off where it lies; one still arming is knocked out, harmless. */
    private void shot(ServerLevel level, Minefield.Mine m, Projectile p) {
        if (!MechanicEvent.post(this, "shot", null)) {
            if (m.ignored != null) m.ignored.add(p.getUUID());
            return;
        }
        shotOff++;
        level.playSound(null, m.at.x, m.at.y, m.at.z, ModSounds.RIFTMINE_SAPPER_SHOT.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, true, true, m.at.x, m.at.y + 0.3, m.at.z, 12, 0.3, 0.2, 0.3, 0.2);
        if (field.armed(m)) blast(level, m);
        else fizzle(level, m);
    }

    /**
     * An armed mine goes off: everyone standing in it is launched straight up. The mine is spent. If the sapper was in
     * it, its other mines fizzle.
     */
    private void blast(ServerLevel level, Minefield.Mine m) {
        if (!MechanicEvent.post(this, "blast", null)) return;
        field.spend(m);
        blasts++;
        level.playSound(null, m.at.x, m.at.y, m.at.z, ModSounds.RIFTMINE_SAPPER_LAUNCH.get(), getSoundSource(), 1.3F, 0.9F + random.nextFloat() * 0.15F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, groundAt(level, m.at)), true, true, m.at.x, m.at.y + 0.1, m.at.z,
                30, MINE_RADIUS * 0.4, 0.1, MINE_RADIUS * 0.4, 0.3);
        level.sendParticles(ParticleTypes.GUST, true, true, m.at.x, m.at.y + 0.5, m.at.z, 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 10; i++) {                          // star-white streaks shooting straight up out of the ring
            double a = i * (Math.PI * 2 / 10), r = MINE_RADIUS * 0.6;
            double x = m.at.x + Math.cos(a) * r, z = m.at.z + Math.sin(a) * r;
            level.sendParticles(new TrailParticleOption(new Vec3(x, m.at.y + 6.0, z), i % 2 == 0 ? STAR_ARGB : VIOLET_ARGB, 12), true, true,
                    x, m.at.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        AABB room = standing(m).expandTowards(0, 0.5, 0);
        boolean self = false;
        for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, room, e -> launchable(e) && on(m, e.getBoundingBox()))) {
            if (launch(level, v) && v == this) self = true;
        }
        if (self) thrown(level);
    }

    /**
     * Launches {@code v} straight up at {@link #LAUNCH_SPEED}: its sideways motion is zeroed, so it comes down where it
     * went up. True if it was launched (a pack may veto each one).
     */
    public boolean launch(ServerLevel level, LivingEntity v) {
        if (!launchable(v) || !MechanicEvent.post(this, "launch", v)) return false;
        if (v.isPassenger()) v.stopRiding();
        v.resetFallDistance();
        v.setOnGround(false);
        Push.set(v, new Vec3(0.0, LAUNCH_SPEED, 0.0));
        if (v instanceof Mob mob) mob.getNavigation().stop();
        launches++;
        return true;
    }

    /** Its own mine threw it: it loses its grip on its field, and every other mine it has out fizzles. */
    private void thrown(ServerLevel level) {
        flung++;
        flungAt = level.getGameTime();
        core().setFlag(FLAG_FLUNG, true);
        plantClock = PLANT_EVERY;
        if (core().in(Step.TELL) || core().in(Step.JAB) || core().in(Step.PLANT)) core().end();
        fizzleAll(level);
    }

    /** Every mine it has out fizzles, harmless. */
    private void fizzleAll(ServerLevel level) {
        for (Minefield.Mine m : new ArrayList<>(field.mines)) if (!m.dud) fizzle(level, m);
    }

    /** One mine fizzles out: harmless from now on, drawn going dark for {@link #FIZZLE_TICKS}, then gone. */
    private void fizzle(ServerLevel level, Minefield.Mine m) {
        if (m.dud) return;
        field.fizzle(m);
        fizzles++;
        level.playSound(null, m.at.x, m.at.y, m.at.z, ModSounds.RIFTMINE_SAPPER_FIZZLE.get(), getSoundSource(), 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.SMOKE, true, true, m.at.x, m.at.y + 0.15, m.at.z, 8, 0.3, 0.05, 0.3, 0.01);
    }

    // ---- drawing the mines -------------------------------------------------------------------------------------------

    /**
     * A mine, every other tick. Arming: a dark violet arc sweeps round the rim as it arms, the rest of the rim a faint
     * void-indigo edge, and a dark violet glyph at its middle pulses with a tick, faster as it comes due. Armed: the whole
     * rim dark violet and the glyph star-white, ringed in dark violet so it reads on pale stone, with a glint rising.
     */
    private void drawMine(ServerLevel level, Minefield.Mine m) {
        int age = m.age;
        if (!field.armed(m)) {
            int period = age < ARM_TICKS / 2 ? 10 : age < ARM_TICKS - 8 ? 6 : 3;
            boolean pulse = age % period == 0;
            if (pulse) {
                level.playSound(null, m.at.x, m.at.y, m.at.z, ModSounds.RIFTMINE_SAPPER_TICK.get(), getSoundSource(), 0.6F, 0.9F + 0.4F * age / ARM_TICKS);
                glyph(level, m.at, VIOLET_DUST, 0.45);
            }
        }
        if ((tickCount + m.salt) % 2 != 0) return;
        double y = m.at.y + 0.06;
        double share = field.armed(m) ? 1.0 : (double) age / ARM_TICKS;
        int lit = (int) Math.round(RIM_POINTS * share);
        for (int i = 0; i < RIM_POINTS; i++) {
            double a = i * (Math.PI * 2 / RIM_POINTS) - Math.PI / 2;
            double x = m.at.x + Math.cos(a) * MINE_RADIUS, z = m.at.z + Math.sin(a) * MINE_RADIUS;
            if (i < lit) {
                level.sendParticles(RIM_DUST, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            } else if (i % 2 == 0) {
                level.sendParticles(VOID_EDGE, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (field.armed(m)) {
            glyph(level, m.at, VIOLET_DUST, 0.7);
            glyph(level, m.at, STAR_DUST, 0.35);
            if ((tickCount + m.salt) % 8 == 0) level.sendParticles(ParticleTypes.END_ROD, true, true, m.at.x, y + 0.1, m.at.z, 1, 0.15, 0.0, 0.15, 0.02);
        }
    }

    /** The glyph at a mine's middle: a diamond of eight points round a centre point. */
    private static void glyph(ServerLevel level, Vec3 c, DustParticleOptions dust, double r) {
        double y = c.y + 0.1;
        level.sendParticles(dust, true, true, c.x, y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI / 4), d = i % 2 == 0 ? r : r * 0.55;
            level.sendParticles(dust, true, true, c.x + Math.cos(a) * d, y, c.z + Math.sin(a) * d, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A fizzling mine: grey ash settling over where its ring lay, thinning out as it goes. */
    private void drawFizzle(ServerLevel level, Minefield.Mine m) {
        if ((tickCount + m.salt) % 3 != 0 || m.fizzle < FIZZLE_TICKS / 3) return;
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = MINE_RADIUS * random.nextDouble();
            level.sendParticles(ASH_DUST, true, true, m.at.x + Math.cos(a) * d, m.at.y + 0.12, m.at.z + Math.sin(a) * d, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.SMOKE, true, true, m.at.x, m.at.y + 0.1, m.at.z, 1, 0.2, 0.0, 0.2, 0.005);
    }

    /** The block under {@code at}, or end stone where that is air. */
    private static BlockState groundAt(ServerLevel level, Vec3 at) {
        BlockState state = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        return state.isAir() ? Blocks.END_STONE.defaultBlockState() : state;
    }

    // ---- the jab ---------------------------------------------------------------------------------------------------

    /** Raises its pick at {@code target} (the tell). True if it did. Public as a test hook. */
    public boolean windUp(LivingEntity target) {
        if (!core().idle() || jabCooldown > 0 || isDeadOrDying() || !(level() instanceof ServerLevel level)) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        holdStill();
        face(target.position(), 360.0F);
        Telegraph.particles(VIOLET_DUST, 8, 0.4, 1.2).withSound(ModSounds.RIFTMINE_SAPPER_TELL.get(), 1.0F, 1.0F).play(this);
        tells++;
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            LivingEntity t = core().target();
            if (!startPhase(Step.JAB, JAB_TICKS, t)) {
                core().end();
                jabCooldown = JAB_COOLDOWN;
                return;
            }
            jab(level, t);
        } else {
            core().end();
            if (ended == Step.JAB) jabCooldown = JAB_COOLDOWN;
        }
    }

    /** The jab lands if its target is still within reach (counting height) and in sight: its attack, no knockback. */
    private void jab(ServerLevel level, @Nullable LivingEntity t) {
        playSound(ModSounds.RIFTMINE_SAPPER_JAB.get(), 1.0F, 0.95F + random.nextFloat() * 0.1F);
        if (isDeadOrDying() || !valid(t) || MobCore.gap(this, t) > JAB_REACH || !getSensing().hasLineOfSight(t)) return;
        if (!MechanicEvent.post(this, "strike", t)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE;
        if (AstralAugur.GroundMarks.hurtInPlace(level, t, damageSources().mobAttack(this), damage)) {
            jabs++;
            setLastHurtMob(t);
            level.sendParticles(ParticleTypes.CRIT, true, true, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
        }
    }

    // ---- moving and facing -----------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity e) {
        return e != null && e != this && e.isAlive() && !e.isRemoved() && e.level() == level()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void face(Vec3 at, float maxTurn) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        if (!core().flag(FLAG_FLUNG)) setDeltaMovement(0.0, v.y, 0.0);
    }

    // ---- death, removal, saving ----------------------------------------------------------------------------------------

    /** A dead sapper sets nothing off: its mines fizzle at once and are gone with it. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (isDeadOrDying() && level() instanceof ServerLevel level) fizzleAll(level);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        field.clear();
        super.onRemoval(reason);
    }

    /** Its live mines (where each lies and how long it has been arming) and its sowing clock survive a reload. */
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        field.save(output);
        output.putInt("PlantClock", plantClock);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        field.load(input, level().getGameTime());
        plantClock = input.getIntOr("PlantClock", FIRST_PLANT);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Where each of its live mines lies, oldest first. Public as a test hook. */
    public List<Vec3> mines() {
        List<Vec3> out = new ArrayList<>();
        for (Minefield.Mine m : field.mines) if (!m.dud) out.add(m.at);
        return out;
    }

    /** Each live mine's age in ticks, oldest first (armed at {@link #ARM_TICKS}). Public as a test hook. */
    public List<Integer> mineAges() {
        List<Integer> out = new ArrayList<>();
        for (Minefield.Mine m : field.mines) if (!m.dud) out.add(m.age);
        return out;
    }

    /** Every mine it holds, live or fizzling. Public as a test hook. */
    public int minesHeld() {
        return field.mines.size();
    }

    /** Ticks until it may sow again. Public as a test hook. */
    public int plantClock() {
        return plantClock;
    }

    /** Sets the ticks until it may sow again. Public as a test hook. */
    public void setPlantClock(int ticks) {
        plantClock = Math.max(0, ticks);
    }

    /** Lets it jab again at once. Public as a test hook. */
    public void resetJab() {
        jabCooldown = 0;
    }

    /** True while it is in the air from its own mine. Public as a test hook. */
    public boolean flung() {
        return core().flag(FLAG_FLUNG);
    }

    /** Counters: mines sown, mines gone off, bodies launched, mines shot, mines fizzled, times thrown, jabs landed, tells. */
    public int plants() {
        return plants;
    }

    public int blasts() {
        return blasts;
    }

    public int launches() {
        return launches;
    }

    public int shotOff() {
        return shotOff;
    }

    public int fizzles() {
        return fizzles;
    }

    public int timesFlung() {
        return flung;
    }

    public int jabs() {
        return jabs;
    }

    public int tells() {
        return tells;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.RIFTMINE_SAPPER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.RIFTMINE_SAPPER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.RIFTMINE_SAPPER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.05F + random.nextFloat() * 0.15F;
    }

    // ---- Minefield: mines kept as data ---------------------------------------------------------------------------------

    /**
     * Mines kept as data on their owner, never blocks: each is a spot on the ground and an age, armed once it reaches its
     * arming time, saved with the owner. A spent or fizzled mine stays on as a dud for a few ticks (for its fizzle to be
     * drawn), harmless, then is swept away. Self-contained: what sets a mine off, what it does and how it looks are the
     * owner's.
     */
    static final class Minefield {
        /** One mine: where it lies, the game time it was sown, its age (ticks since, up to arming), and whether it is spent. */
        static final class Mine {
            final Vec3 at;
            final int salt;
            long born;
            int age;
            boolean announced;
            boolean dud;
            int fizzle;
            /** Projectiles already lying in it when it was first checked; never saved. */
            @Nullable Set<java.util.UUID> ignored;

            Mine(Vec3 at, long born) {
                this.at = at;
                this.born = born;
                this.salt = (int) (Math.abs(Double.doubleToLongBits(at.x * 31.0 + at.z)) % 7);
            }
        }

        private final int max, armTicks, fizzleTicks;
        final List<Mine> mines = new ArrayList<>(4);

        Minefield(int max, int armTicks, int fizzleTicks) {
            this.max = max;
            this.armTicks = armTicks;
            this.fizzleTicks = fizzleTicks;
        }

        /** Sows a mine at {@code at} at game time {@code now}. */
        Mine plant(Vec3 at, long now) {
            Mine m = new Mine(at, now);
            mines.add(m);
            return m;
        }

        /** Whether {@code m} is armed: live and as old as the arming time. */
        boolean armed(Mine m) {
            return !m.dud && m.age >= armTicks;
        }

        /** Makes {@code m} armed already, as if sown its arming time ago. */
        void arm(Mine m, long now) {
            m.born = now - armTicks;
            m.age = armTicks;
            m.announced = true;
        }

        /** Gone off: harmless, swept away next. */
        void spend(Mine m) {
            m.dud = true;
            m.fizzle = 0;
        }

        /** Fizzling: harmless, drawn going dark for the fizzle time, then swept away. */
        void fizzle(Mine m) {
            m.dud = true;
            m.fizzle = fizzleTicks;
        }

        /** Live mines (not spent or fizzling). */
        int live() {
            int n = 0;
            for (Mine m : mines) if (!m.dud) n++;
            return n;
        }

        boolean isEmpty() {
            return mines.isEmpty();
        }

        /**
         * Brings every live mine's age up to game time {@code now} and counts down every fizzle; returns the mines that
         * armed since the last call.
         */
        List<Mine> tick(long now) {
            List<Mine> armedNow = new ArrayList<>(1);
            for (Mine m : mines) {
                if (m.dud) {
                    m.fizzle--;
                    continue;
                }
                m.age = (int) Math.clamp(now - m.born, 0L, (long) armTicks);
                if (m.age >= armTicks && !m.announced) {
                    m.announced = true;
                    armedNow.add(m);
                }
            }
            return armedNow;
        }

        /** Drops spent mines and finished fizzles. */
        void sweep() {
            for (Iterator<Mine> it = mines.iterator(); it.hasNext(); ) {
                Mine m = it.next();
                if (m.dud && m.fizzle <= 0) it.remove();
            }
        }

        void clear() {
            mines.clear();
        }

        /** Saves each live mine's spot and age. */
        void save(ValueOutput output) {
            List<Vec3> at = new ArrayList<>();
            List<Integer> ages = new ArrayList<>();
            for (Mine m : mines) {
                if (m.dud) continue;
                at.add(m.at);
                ages.add(m.age);
            }
            if (at.isEmpty()) return;
            output.store("Mines", Vec3.CODEC.listOf(), at);
            output.putIntArray("MineAges", ages.stream().mapToInt(Integer::intValue).toArray());
        }

        /** Loads the saved mines, each going on from its saved age at game time {@code now}. */
        void load(ValueInput input, long now) {
            mines.clear();
            List<Vec3> at = input.read("Mines", Vec3.CODEC.listOf()).orElse(List.of());
            int[] ages = input.getIntArray("MineAges").orElse(new int[0]);
            for (int i = 0; i < at.size() && mines.size() < max; i++) {
                int age = i < ages.length ? Math.clamp(ages[i], 0, armTicks) : 0;
                Mine m = plant(at.get(i), now - age);
                m.age = age;
                m.announced = age >= armTicks;
            }
        }
    }
}
