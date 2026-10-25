package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import com.panda.teeming.roster.RosterStats;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Pyre Herald: a small, hunched brazier-bearer of the Nether fortresses that rallies the monsters round it. Every 200
 * ticks, while it or one of its allies is in a fight, it raises its brazier high over its head for 20 ticks (tell:
 * wings spread, a horn-like call, embers rising, a ring of embers marks its 10-block reach), then the brazier flares:
 * every blaze, wither skeleton and Nether-born Teeming mob within 10 blocks gains 8 absorption (Absorption II, a
 * shell of embers that lasts 12 s or until it is broken). It never shells itself, another herald, or anything else.
 *
 * <p>It never wants to be in front. With an ally near, it keeps that ally between itself and its target, standing 3
 * blocks behind it; with none (or no way to get behind one) it backs away from survival players within 6 blocks, and
 * it swats with its brazier pole only when its target is within 2 blocks. Counter: reach it and kill it first, or hit
 * it while the brazier is raised: the rally breaks, nobody gets a shell, and the brazier falls from its hands. It
 * stands groping for it for 60 ticks and can't rally until it has picked it up; the next rally is a full 200 ticks
 * away. Hits while it gropes don't make the fumble longer.
 */
public class PyreHerald extends TeemingMonster {
    /** IDLE, the brazier raised (tell), the brazier flaring as the shells go up, the brazier dropped (counter landed). */
    public enum Step implements Phase { IDLE, RAISE, RALLY, DROPPED }

    /** 1 s with the brazier raised before the rally: long enough to close in and land a hit. */
    public static final int TELL_TICKS = 20;
    /** The flare after the raise, while it lowers the brazier again. */
    public static final int RALLY_TICKS = 10;
    /** Ticks from one rally to the next, at the soonest. */
    public static final int RALLY_EVERY = 200;
    /** How far its rally reaches, centre to feet. */
    public static final double RALLY_RADIUS = 10.0;
    /** Absorption each ally gets: four hearts. */
    public static final float SHELL = 8.0F;
    /** The Absorption amplifier that gives exactly {@link #SHELL}: 4 per level, so amplifier 1 is Absorption II. */
    public static final int SHELL_LEVEL = 1;
    /** A shell lasts 12 s (past the next rally, which renews it) unless it is broken first. */
    public static final int SHELL_TICKS = 240;
    /** A hit during the raise knocks the brazier out of its hands for 3 s. */
    public static final int DROP_TICKS = 60;
    /** A fresh herald's first rally can come this soon. */
    public static final int FIRST_RALLY = 40;
    /** It stands this far behind the ally it hides behind, on the far side from its target. */
    public static final double BEHIND = 3.0;
    /** Within this of its hiding spot it has arrived and stops. */
    public static final double ARRIVED = 1.0;
    /** With nobody to hide behind, it backs away from survival players this close. */
    public static final double FLEE_RANGE = 6.0;
    /** Each step it backs off takes it at least this much further from the player, in blocks; a smaller one isn't taken. */
    public static final double BACK_OFF_GAIN = 1.0;
    /** How many spots it weighs each time it looks for a way to back off. */
    private static final int BACK_OFF_TRIES = 3;
    /** A target this close has cornered it: it swats with the brazier pole. */
    public static final double CORNERED_RANGE = 2.0;
    /** How often it looks for a new hiding spot, in ticks. */
    public static final int SPOT_EVERY = 10;

    public static final int EMBER_COLOR = 0xFF7A1A;
    public static final int SOUL_COLOR = 0x57CDE0;
    private static final DustParticleOptions EMBER_DUST = new DustParticleOptions(EMBER_COLOR, 0.9F);
    private static final DustParticleOptions SOUL_DUST = new DustParticleOptions(SOUL_COLOR, 0.9F);
    private static final int RING_POINTS = 56;
    /** Where its brazier burns, in blocks at scale 1: to its right, up, and forward; held at rest, raised in the tell. */
    private static final double BRAZIER_SIDE = 0.3, REST_UP = 1.4, REST_FORWARD = 0.45, RAISED_UP = 2.1, RAISED_FORWARD = 0.1;
    /** Where the dropped brazier lies: in front of its feet. */
    private static final double DROPPED_FORWARD = 1.3, DROPPED_UP = 0.15;

    /** Ticks until it may rally again. */
    private int untilRally = FIRST_RALLY;
    /** Who its shells are on, to draw the embers round them while the shells hold. */
    private final Set<UUID> shelled = new LinkedHashSet<>();
    /** Where it means to stand, behind an ally; null with no ally to hide behind or no way there. */
    private @Nullable Vec3 spot;
    private int spotCheck;
    /** Set in registerGoals, which runs inside the Mob constructor, so it has no initializer. */
    private @Nullable WaterAvoidingRandomStrollGoal stroll;

    public PyreHerald(EntityType<? extends PyreHerald> type, Level level) {
        super(type, level, Step.values());
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("pyre_herald", 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false) {
            @Override
            public boolean canUse() {
                return core().idle() && cornered(CORNERED_RANGE) && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && cornered(CORNERED_RANGE + 0.5) && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new KeepBehindGoal());
        goalSelector.addGoal(3, new BackOffGoal());
        stroll = new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        };
        goalSelector.addGoal(5, stroll);
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private static boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private boolean cornered(double range) {
        LivingEntity target = getTarget();
        return valid(target) && distanceTo(target) <= range;
    }

    // ---- who it rallies -----------------------------------------------------------------------------------------------

    /**
     * Whether its rally shells {@code e}: a blaze, a wither skeleton, or a Teeming mob of the Nether, but never another
     * herald (heralds don't shield each other). Players, other mobs and Overworld or End Teeming mobs never count.
     */
    public static boolean rallies(Entity e) {
        if (e instanceof PyreHerald) return false;
        EntityType<?> type = e.getType();
        if (type == EntityTypes.BLAZE || type == EntityTypes.WITHER_SKELETON) return true;
        if (!(e instanceof TeemingCreature creature)) return false;
        RosterStats.Entry row = RosterStats.BY_ID.get(creature.mobId());
        return row != null && row.dim() == RosterStats.Dim.NETHER;
    }

    /** Every ally within its rally's reach, nearest first. */
    public List<LivingEntity> allies(ServerLevel level) {
        List<LivingEntity> out = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RALLY_RADIUS),
                e -> e != this && e.isAlive() && rallies(e) && e.distanceTo(this) <= RALLY_RADIUS));
        out.sort((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this)));
        return out;
    }

    /** In a fight: it has a target, or one of its allies has. Without an ally near it never rallies. */
    private boolean alerted(ServerLevel level) {
        List<LivingEntity> allies = allies(level);
        if (allies.isEmpty()) return false;
        if (valid(getTarget())) return true;
        for (LivingEntity ally : allies) if (ally instanceof Mob mob && valid(mob.getTarget())) return true;
        return false;
    }

    // ---- keeping behind its allies --------------------------------------------------------------------------------------

    /**
     * Its hiding spot: {@link #BEHIND} blocks past its nearest ally on the line from its target through that ally (or a
     * little round to either side, or a block closer, when that spot can't be walked to). Null with no target, no ally
     * or no reachable spot. Public as a test hook.
     */
    public @Nullable Vec3 findSpot() {
        if (!(level() instanceof ServerLevel level)) return null;
        LivingEntity target = getTarget();
        if (!valid(target)) return null;
        for (LivingEntity shield : allies(level)) {
            if (shield == target) continue;
            Vec3 away = shield.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0E-4) continue;
            Vec3 dir = away.normalize();
            for (double back : new double[] {BEHIND, BEHIND - 1.0}) {
                for (double turn : new double[] {0.0, 30.0, -30.0, 60.0, -60.0}) {
                    double a = Math.toRadians(turn);
                    Vec3 d = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0.0, dir.x * Math.sin(a) + dir.z * Math.cos(a));
                    Vec3 at = shield.position().add(d.scale(back));
                    // already there, if it is a place it can stand: a spot inside a wall it stands beside is no spot
                    if (at.distanceToSqr(position()) <= ARRIVED * ARRIVED && level.noCollision(this, getBoundingBox().move(at.subtract(position())))) return at;
                    Path path = getNavigation().createPath(BlockPos.containing(at), 0);
                    if (path != null && path.canReach()) return at;
                }
            }
            return null;                                                       // only its nearest ally is its shield
        }
        return null;
    }

    /** With an ally near and a target: gets behind the ally and stays there, facing the fight. */
    private final class KeepBehindGoal extends Goal {
        private int repath;

        KeepBehindGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!core().idle() || !valid(getTarget())) {
                spot = null;
                return false;
            }
            if (--spotCheck <= 0) {
                spotCheck = SPOT_EVERY;
                spot = findSpot();
            }
            return spot != null;
        }

        @Override
        public boolean canContinueToUse() {
            return core().idle() && valid(getTarget()) && spot != null;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (--repath <= 0) {
                repath = SPOT_EVERY;
                spot = findSpot();
                if (spot == null) return;
                if (spot.distanceToSqr(position()) > ARRIVED * ARRIVED) getNavigation().moveTo(spot.x, spot.y, spot.z, 0, 1.15);   // to the spot itself, not just near it
                else getNavigation().stop();
            }
            LivingEntity target = getTarget();
            if (target != null) getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** The fallback, with no ally to hide behind: backs away from the nearest survival player within its flee range. */
    private final class BackOffGoal extends Goal {
        private @Nullable Player from;

        BackOffGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!core().idle() || spot != null) return false;
            from = nearestThreat();
            if (from == null) return false;
            for (int i = 0; i < BACK_OFF_TRIES; i++) {
                Path path = retreat(from);
                if (path != null) return getNavigation().moveTo(path, 1.25);
            }
            return false;
        }

        /**
         * A path away from {@code from} that ends at least {@link #BACK_OFF_GAIN} further from it and never passes
         * closer to it than it starts, or null.
         */
        private @Nullable Path retreat(Player from) {
            Vec3 away = DefaultRandomPos.getPosAway(PyreHerald.this, 16, 7, from.position());
            if (away == null) away = DefaultRandomPos.getPosAway(PyreHerald.this, 6, 3, from.position());   // by a wall: a short step beats none
            if (away == null || away.distanceToSqr(from.position()) <= distanceToSqr(from)) return null;
            // a spot it can't stand on (on top of a wall, past a fence) gives a path that stops short of it, and that
            // can end no further from the player than it is now: what counts is where the path really ends
            Path path = getNavigation().createPath(away.x, away.y, away.z, 1);
            Node end = path == null ? null : path.getEndNode();
            if (end == null) return null;
            double gain = Vec3.atBottomCenterOf(end.asBlockPos()).distanceTo(from.position()) - distanceTo(from);
            if (gain < BACK_OFF_GAIN) return null;
            // backing off never walks it past the player: a way round a wall that crosses in front of them is no retreat
            double start = Vec3.atBottomCenterOf(path.getNodePos(0)).distanceTo(from.position());
            for (int i = 1; i < path.getNodeCount(); i++) {
                if (Vec3.atBottomCenterOf(path.getNodePos(i)).distanceTo(from.position()) < start) return null;
            }
            return path;
        }

        @Override
        public boolean canContinueToUse() {
            return core().idle() && spot == null && !getNavigation().isDone();
        }

        @Override
        public void stop() {
            from = null;
        }
    }

    private @Nullable Player nearestThreat() {
        Player best = null;
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(FLEE_RANGE),
                p -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) && p.isAlive() && distanceTo(p) <= FLEE_RANGE)) {
            if (best == null || distanceToSqr(p) < distanceToSqr(best)) best = p;
        }
        return best;
    }

    // ---- the raise and the rally ------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide() && untilRally > 0) untilRally--;
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (core().in(Step.RAISE)) {
            raiseTick(level);
        } else if (core().in(Step.DROPPED)) {
            if (tickCount % 3 == 0) {
                Vec3 b = brazier();
                level.sendParticles(ParticleTypes.SMOKE, b.x, b.y + 0.1, b.z, 1, 0.12, 0.05, 0.12, 0.01);
                level.sendParticles(flame(), b.x, b.y + 0.05, b.z, 1, 0.15, 0.02, 0.15, 0.0);
            }
        } else if (core().idle() && !isNoAi() && untilRally <= TELL_TICKS && alerted(level)) {
            raise(getTarget());
        }
        if (tickCount % 8 == 0) showShells(level);
    }

    /** Embers stream up from the raised brazier, a ring of embers marks how far the rally reaches, and the call sounds again. */
    private void raiseTick(ServerLevel level) {
        int t = core().elapsed();
        Vec3 b = brazier();
        for (int i = 0; i < 2; i++) {
            double x = b.x + (random.nextDouble() - 0.5) * 0.35, z = b.z + (random.nextDouble() - 0.5) * 0.35;
            // rising slowly: flame particles keep their speed, so a dust-like speed would throw them blocks high
            level.sendParticles(flame(), x, b.y, z, 0, 0.0, 1.0, 0.0, 0.015 + random.nextDouble() * 0.015);
        }
        if (t % 5 == 0) ring(level);
        if (t == TELL_TICKS / 2) playSound(ModSounds.PYRE_HERALD_RAISE.get(), 0.9F, 1.15F);
    }

    /** Embers round it at the rally's reach, just above the ground. */
    private void ring(ServerLevel level) {
        DustParticleOptions dust = soul() ? SOUL_DUST : EMBER_DUST;
        double turn = tickCount * 0.02;
        for (int i = 0; i < RING_POINTS; i++) {
            double a = turn + i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(dust, getX() + Math.cos(a) * RALLY_RADIUS, getY() + 0.15, getZ() + Math.sin(a) * RALLY_RADIUS,
                    1, 0.0, 0.05, 0.0, 0.0);
        }
    }

    /**
     * Raises the brazier (the tell) if it is free to and holds its brazier; true if it did. Ignores the time since the
     * last rally. Public as a test hook.
     */
    public boolean raise(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle()) return false;
        if (!startPhase(Step.RAISE, TELL_TICKS, target)) return false;
        getNavigation().stop();
        playSound(ModSounds.PYRE_HERALD_RAISE.get(), 1.1F, 0.85F);
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!core().idle()) {                                                  // it stands still to raise, flare or grope
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.RAISE && level() instanceof ServerLevel level) {
            untilRally = RALLY_EVERY;
            if (startPhase(Step.RALLY, RALLY_TICKS, core().target())) rally(level);
            else core().end();
        } else if (ended == Step.DROPPED && level() instanceof ServerLevel level) {
            Vec3 b = brazier();
            level.playSound(null, b.x, b.y, b.z, ModSounds.PYRE_HERALD_PICKUP.get(), getSoundSource(), 0.9F, 1.0F);
            core().end();
        } else {
            core().end();
        }
    }

    /**
     * The rally: Absorption II (8 absorption) for 240 ticks to every ally within 10 blocks, topped back up to 8 on an
     * ally whose last shell is still partly there. Returns how many it shelled. Public as a test hook.
     */
    public int rally(ServerLevel level) {
        int count = 0;
        for (LivingEntity ally : allies(level)) {
            if (!MechanicEvent.post(this, "ember", ally)) continue;
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, SHELL_TICKS, SHELL_LEVEL, false, false), this);
            // a renewed effect doesn't refill the absorption by itself; a fresh one already starts at 8
            if (ally.getAbsorptionAmount() < SHELL) ally.setAbsorptionAmount(SHELL);
            shelled.add(ally.getUUID());
            shell(level, ally, 14);
            count++;
        }
        Vec3 b = brazier();
        level.sendParticles(flame(), b.x, b.y, b.z, 24, 0.25, 0.3, 0.25, 0.02);
        level.sendParticles(ParticleTypes.LAVA, b.x, b.y, b.z, 2, 0.2, 0.1, 0.2, 0.0);
        level.playSound(null, b.x, b.y, b.z, ModSounds.PYRE_HERALD_RALLY.get(), getSoundSource(), 1.2F, 1.0F);
        return count;
    }

    /** Embers in a loose shell round one ally's body. */
    private void shell(ServerLevel level, LivingEntity e, int points) {
        SimpleParticleType f = flame();
        double r = e.getBbWidth() * 0.5 + 0.25;
        double turn = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < points; i++) {
            double a = turn + i * (Math.PI * 2 / points);
            double y = e.getY() + e.getBbHeight() * (0.15 + 0.7 * ((i * 7) % points) / (double) points);
            level.sendParticles(f, e.getX() + Math.cos(a) * r, y, e.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Draws the ember shells on everyone whose shell from this herald still holds, and forgets the rest. */
    private void showShells(ServerLevel level) {
        shelled.removeIf(id -> {
            Entity e = level.getEntity(id);
            if (!(e instanceof LivingEntity living) || !living.isAlive() || living.getAbsorptionAmount() <= 0.0F
                    || !living.hasEffect(MobEffects.ABSORPTION)) return true;
            shell(level, living, 5);
            return false;
        });
    }

    // ---- the counter: a hit during the raise drops the brazier --------------------------------------------------------

    /**
     * A hit from something (a blow, an arrow, a thrown potion) while the brazier is raised breaks the rally: nobody gets
     * a shell, the brazier drops for 60 ticks and the rally waits a full 200 ticks again. Hits at other times, and
     * hits while it gropes for the brazier, change nothing.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && core().in(Step.RAISE) && (source.getEntity() != null || source.getDirectEntity() != null)) {
            drop(level, source.getEntity() instanceof LivingEntity by ? by : null);
        }
        return hurt;
    }

    private void drop(ServerLevel level, @Nullable LivingEntity by) {
        if (!startPhase(Step.DROPPED, DROP_TICKS, by)) return;              // a vetoed drop leaves the raise running
        untilRally = RALLY_EVERY;
        Vec3 b = brazier();
        level.sendParticles(flame(), b.x, b.y + 0.2, b.z, 16, 0.35, 0.2, 0.35, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, b.x, b.y + 0.2, b.z, 6, 0.25, 0.15, 0.25, 0.02);
        level.sendParticles(ParticleTypes.LAVA, b.x, b.y + 0.2, b.z, 2, 0.2, 0.1, 0.2, 0.0);
        level.playSound(null, b.x, b.y, b.z, ModSounds.PYRE_HERALD_DROP.get(), getSoundSource(), 1.1F, 1.0F);
    }

    // ---- helpers and test hooks ---------------------------------------------------------------------------------------

    /** Where its brazier burns now: held at its side, raised over its head, or dropped at its feet. */
    public Vec3 brazier() {
        float r = yBodyRot * Mth.DEG_TO_RAD;
        double s = getScale();
        Vec3 forward = new Vec3(-Mth.sin(r), 0.0, Mth.cos(r)), right = new Vec3(-Mth.cos(r), 0.0, -Mth.sin(r));
        double up, ahead;
        if (core().in(Step.RAISE) || core().in(Step.RALLY)) {
            up = RAISED_UP;
            ahead = RAISED_FORWARD;
        } else if (core().in(Step.DROPPED)) {
            up = DROPPED_UP;
            ahead = DROPPED_FORWARD;
        } else {
            up = REST_UP;
            ahead = REST_FORWARD;
        }
        return position().add(right.scale(BRAZIER_SIDE * s)).add(forward.scale(ahead * s)).add(0.0, up * s, 0.0);
    }

    private boolean soul() {
        return "soul_herald".equals(look());
    }

    private SimpleParticleType flame() {
        return soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
    }

    /** True while it holds its brazier: false while the brazier lies dropped. */
    public boolean holdsBrazier() {
        return !core().in(Step.DROPPED);
    }

    /** Where it means to stand now, behind an ally, or null. */
    public @Nullable Vec3 spot() {
        return spot;
    }

    /** Ticks until it may rally again. */
    public int untilRally() {
        return untilRally;
    }

    /** Sets the ticks until it may rally again. Public as a test hook. */
    public void setUntilRally(int ticks) {
        untilRally = Math.max(0, ticks);
    }

    /** Test hook: stops it wandering, so only its hiding, backing off and swatting move it. */
    public void stayPut() {
        if (stroll != null) goalSelector.removeGoal(stroll);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.PYRE_HERALD_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PYRE_HERALD_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PYRE_HERALD_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.05F + random.nextFloat() * 0.15F;
    }
}
