package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Soulfire Seer: a tall, gaunt skeletal prophet of the soul sand valleys with a soul flame burning in its one eye
 * socket. It keeps its distance and snipes. Every 100 ticks, with a target within 32 blocks in its sight, it takes aim
 * for 24 ticks (tell): a thin soul-blue line runs from its eye to the target, with a rising whine and soul motes at the
 * eye. For the first 16 ticks the line follows the target; for the last 8 it is locked where it last pointed. Then the
 * beam fires along the locked line, at once, out to 32 blocks: the first thing it meets takes 9 (a mob's projectile, so
 * a raised shield takes it) and burns with soul fire for 3 s. Blocks stop it.
 *
 * <p>Counter: break its line of sight while the line is drawn (the line locks where it last saw you and the beam goes
 * into the wall), or step aside once the line stops following you. A beam that hits nothing burns out its eye: for 80
 * ticks it is blind. It loses its target, can't take aim and can't keep its distance; it gropes about slowly instead,
 * and only picks a target again once the eye rekindles. It takes no extra damage meanwhile: the reward is the time to
 * close in on a mob that otherwise backs away.
 *
 * <p>The line is not an entity: the seer faces its target while it aims, and tells clients how long the line is and
 * how steeply it runs through its synced flags, so the model lays the line from its eye. Nothing is kept on the target.
 */
public class SoulfireSeer extends TeemingMonster {
    /** IDLE, taking aim (tell), the beam, blind with its eye snuffed out (counter landed). */
    public enum Step implements Phase { IDLE, TELL, FIRE, SNUFFED }

    /** 24 ticks of the aim line before the beam: 16 following the target, then 8 locked. */
    public static final int TELL_TICKS = 24;
    public static final int TRACK_TICKS = 16;
    /** It takes aim at targets this close, and the beam reaches this far from its eye. */
    public static final double RANGE = 32.0;
    /** Ticks from one tell to the next, at the soonest. */
    public static final int AIM_EVERY = 100;
    /** The beam's hit on the first thing it meets. */
    public static final float BEAM_DAMAGE = 9.0F;
    /** What the beam hits burns for 3 s. */
    public static final int SOUL_FIRE_TICKS = 60;
    /** A beam that hits nothing leaves it blind this long. */
    public static final int SNUFF_TICKS = 80;
    /** The beam shows this long: a flash, then a fading streak along the line of fire. */
    public static final int BEAM_TICKS = 20;
    /** How far from a hitbox the beam still catches it. */
    public static final double BEAM_RADIUS = 0.15;
    /** It backs away from a target closer than this... */
    public static final double KEEP_AWAY = 6.0;
    /** ...and walks toward one farther than this, or out of its sight. */
    public static final double CLOSE_IN = 24.0;
    /** The socket sits this far ahead of its eye line, in blocks at scale 1. */
    public static final double EYE_FORWARD = 0.22;
    /** A spot to back off toward: this far out and up or down; the short search when no far spot is open. */
    public static final int BACK_SEARCH = 8, BACK_SEARCH_Y = 3, BACK_SHORT = 4, BACK_SHORT_Y = 2;
    /** Blind, it shuffles a step or two this often, slowly. */
    public static final int GROPE_EVERY = 20;
    public static final double GROPE_SPEED = 0.5;

    // flag bits: the line's length in quarters of a block at scale 1 (8 bits), its pitch in degrees + 128 (8 bits),
    // whether the two are live (unset in a pose with no aim behind it), whether the aim is locked, and whether the
    // beam struck something
    private static final int LENGTH_BIT = 0, PITCH_BIT = 8, LIVE_BIT = 16, LOCKED_BIT = 17, HIT_BIT = 18;
    private static final int FIELD_MASK = 0xFF;

    /** Soul blue, the colour of the line and the locked lane: well off weak-point gold. */
    public static final int SOUL_COLOR = 0x57CDE0;
    private static final DustParticleOptions LANE = new DustParticleOptions(SOUL_COLOR, 0.6F);

    private int cooldown;
    private boolean locked;
    private boolean missed;
    private @Nullable LivingEntity struck;
    private Vec3 aimAt = Vec3.ZERO;
    private Vec3 lockFrom = Vec3.ZERO;
    private Vec3 lockDir = new Vec3(0, 0, 1);
    private Vec3 beamEnd = Vec3.ZERO;
    private int tells, hits, misses;
    /** What its beam set alight, and the game time the soul fire it lit runs out: drawn with soul flames till then. */
    private final List<Burn> burning = new ArrayList<>();

    private record Burn(LivingEntity who, long until) {}

    private final Shot.Rules beamRules = new Shot.Rules(this::beamCanHit, this::drawBeam, this::beamHit, this::beamStop);

    public SoulfireSeer(EntityType<? extends SoulfireSeer> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // it must notice targets as far off as its beam reaches
        return TeemingAttributes.monster("soulfire_seer", 0.24).add(Attributes.FOLLOW_RANGE, RANGE);
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
        // blind, it picks no target: not even whoever hits it
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !blind() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !blind() && super.canContinueToUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return !blind() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !blind() && super.canContinueToUse();
            }
        });
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
        if (cooldown > 0) cooldown--;
        if (core().in(Step.TELL)) {
            aimTick(level, core().target());
        } else if (core().in(Step.FIRE)) {
            holdStill();
        } else if (core().in(Step.SNUFFED)) {
            blindTick(level);
        } else if (cooldown == 0 && canAim(getTarget())) {
            aim(getTarget());
        }
    }

    /** True when {@code target} is a fair mark: alive, within 32 blocks and in its sight. */
    public boolean canAim(@Nullable LivingEntity target) {
        return valid(target) && distanceTo(target) <= RANGE && hasLineOfSight(target);
    }

    /** Starts the tell at {@code target}: the aim line, tracking. True if it started. Public as a test hook. */
    public boolean aim(@Nullable LivingEntity target) {
        if (target == null || !core().idle() || cooldown > 0 || level().isClientSide()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        cooldown = AIM_EVERY;
        tells++;
        locked = false;
        missed = false;
        struck = null;
        holdStill();
        face(target);
        aimAt = aimPoint(target);
        showLine(eye(), aimAt, false, false);
        Vec3 e = eye();
        level().playSound(null, e.x, e.y, e.z, ModSounds.SOULFIRE_SEER_TELL.get(), getSoundSource(), 1.0F, 0.85F);
        return true;
    }

    /**
     * The tell: it holds still and its line follows the target for 16 ticks, then locks where it last pointed. It locks
     * early on the spot it last saw the target when the target goes out of sight, out of range or away. Nothing
     * happens to the target yet.
     */
    private void aimTick(ServerLevel level, @Nullable LivingEntity target) {
        holdStill();
        int t = core().elapsed();
        Vec3 e = eye();
        if (!locked) {
            if (t >= TRACK_TICKS || !canAim(target)) {
                lock(level);
            } else {
                face(target);
                aimAt = aimPoint(target);
                showLine(eye(), aimAt, false, false);
                if (t == TELL_TICKS / 3) {
                    level.playSound(null, e.x, e.y, e.z, ModSounds.SOULFIRE_SEER_TELL.get(), getSoundSource(), 1.0F, 1.1F);
                }
            }
        }
        if (t % 2 == 0) level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.x, e.y + 0.1, e.z, 1, 0.06, 0.04, 0.06, 0.005);
        if (t % 4 == 0) level.sendParticles(ParticleTypes.SOUL, e.x, e.y, e.z, 1, 0.12, 0.08, 0.12, 0.01);
        if (locked && t % 2 == 0) drawLane(level);
    }

    /** The aim locks: the line holds still where it points, out to the first block or 32 blocks, with a click. */
    private void lock(ServerLevel level) {
        locked = true;
        lockFrom = eye();
        Vec3 d = aimAt.subtract(lockFrom);
        lockDir = d.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(getXRot(), getYRot()) : d.normalize();
        Vec3 reach = lockFrom.add(lockDir.scale(RANGE));
        BlockHitResult wall = level.clip(new ClipContext(lockFrom, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? reach : wall.getLocation();
        showLine(lockFrom, end, true, false);
        level.playSound(null, lockFrom.x, lockFrom.y, lockFrom.z, ModSounds.SOULFIRE_SEER_LOCK.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** The locked lane: soul-blue motes along it every block, where the beam will go. */
    private void drawLane(ServerLevel level) {
        Vec3 end = lineEnd();
        double length = end.distanceTo(lockFrom);
        for (double run = 1.0; run < length; run += 1.0) {
            Vec3 p = lockFrom.add(lockDir.scale(run));
            level.sendParticles(LANE, false, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
            return;
        }
        if (ended == Step.TELL) {
            if (!locked) lock(level);
            if (startPhase(Step.FIRE, BEAM_TICKS, core().target())) fire(level);
            else rest();
        } else if (ended == Step.FIRE) {
            if (missed) snuff(level);
            else rest();
        } else if (ended == Step.SNUFFED) {
            relight(level);
        } else {
            core().end();
        }
    }

    // ---- the beam: a shot that flies its whole 32 blocks in one step, stopped by the first block or hitbox --------

    /** The beam fires along the locked line, at once. */
    private void fire(ServerLevel level) {
        missed = false;
        struck = null;
        beamEnd = lockFrom;
        new Shot(Shot.Path.line(lockFrom, lockDir, RANGE, RANGE)).step(level, this, BEAM_RADIUS, beamRules);
        showLine(lockFrom, beamEnd, true, struck != null);
        level.playSound(null, lockFrom.x, lockFrom.y, lockFrom.z, ModSounds.SOULFIRE_SEER_BEAM.get(), getSoundSource(), 1.2F, 1.0F);
    }

    private boolean beamCanHit(LivingEntity e) {
        return e != this && !(e instanceof SoulfireSeer) && e.isAlive() && e.canBeHitByProjectile() && !e.isSpectator()
                && !(e instanceof Player p && p.isCreative());
    }

    /** The beam as players see it besides the model's: soul flames along its length and a flash where it ends. */
    private void drawBeam(ServerLevel level, Vec3 from, Vec3 to) {
        beamEnd = to;
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length > 1.0E-3) {
            Vec3 dir = d.scale(1.0 / length);
            for (double run = 0.6; run < length; run += 0.7) {
                Vec3 p = from.add(dir.scale(run));
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, false, true, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.01);
            }
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, to.x, to.y, to.z, 10, 0.15, 0.15, 0.15, 0.05);
    }

    /** The beam meets a hitbox: 9 as a mob's projectile, and soul fire for 3 s if it landed (a shield takes both). */
    private void beamHit(ServerLevel level, Shot shot, LivingEntity victim, Vec3 at) {
        struck = victim;
        if (!MechanicEvent.post(this, "beam_hit", victim)) return;
        float before = victim.getHealth() + victim.getAbsorptionAmount();
        victim.hurtServer(level, Shot.damage(this), BEAM_DAMAGE);
        if (victim.getHealth() + victim.getAbsorptionAmount() < before) {
            hits++;
            victim.igniteForTicks(SOUL_FIRE_TICKS);
            burning.removeIf(b -> b.who() == victim);
            burning.add(new Burn(victim, level.getGameTime() + SOUL_FIRE_TICKS));
        }
        level.sendParticles(ParticleTypes.SOUL, true, true, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.03);
    }

    /** The beam hits nothing: a block stopped it or it ran out its 32 blocks. Its eye will gutter out. */
    private void beamStop(ServerLevel level, Shot shot, Vec3 at, boolean blocked) {
        missed = true;
        misses++;
        if (blocked) level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.01);
    }

    // ---- snuffed: the counter's reward ----------------------------------------------------------------------------

    /** Its eye gutters out (the counter): smoke from the socket, a fizzle, and it is blind for 80 ticks. */
    private void snuff(ServerLevel level) {
        if (!startPhase(Step.SNUFFED, SNUFF_TICKS, null)) {
            rest();
            return;
        }
        hideLine();
        setTarget(null);
        getNavigation().stop();
        Vec3 e = eye();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, e.x, e.y, e.z, 8, 0.12, 0.1, 0.12, 0.02);
        level.sendParticles(ParticleTypes.SOUL, e.x, e.y + 0.2, e.z, 4, 0.15, 0.15, 0.15, 0.02);
        level.playSound(null, e.x, e.y, e.z, ModSounds.SOULFIRE_SEER_SNUFF.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** Blind: no target, smoke curling from the empty socket, and now and then a slow, groping step or two. */
    private void blindTick(ServerLevel level) {
        if (getTarget() != null) setTarget(null);
        int t = core().elapsed();
        Vec3 e = eye();
        if (t % 3 == 0) level.sendParticles(ParticleTypes.SMOKE, e.x, e.y + 0.05, e.z, 1, 0.04, 0.02, 0.04, 0.01);
        if (t % GROPE_EVERY == GROPE_EVERY / 2) {
            double a = random.nextDouble() * Math.PI * 2.0, r = 1.0 + random.nextDouble();
            getNavigation().moveTo(getX() + Math.cos(a) * r, getY(), getZ() + Math.sin(a) * r, GROPE_SPEED);
        }
    }

    /** The eye rekindles: soul flames at the socket, and it may pick a target again. */
    private void relight(ServerLevel level) {
        Vec3 e = eye();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.x, e.y, e.z, 8, 0.1, 0.1, 0.1, 0.03);
        level.playSound(null, e.x, e.y, e.z, ModSounds.SOULFIRE_SEER_RELIGHT.get(), getSoundSource(), 1.0F, 1.0F);
        core().end();
    }

    /** True while its eye is snuffed out: it sees nothing. */
    public boolean blind() {
        return core() != null && core().in(Step.SNUFFED);
    }

    private void rest() {
        hideLine();
        core().end();
    }

    // ---- soul fire on what it struck ------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || burning.isEmpty()) return;
        long now = level.getGameTime();
        burning.removeIf(b -> now >= b.until() || !b.who().isAlive() || !b.who().isOnFire() || b.who().level() != level);
        for (Burn b : burning) {
            if (tickCount % 2 != 0) continue;
            LivingEntity w = b.who();
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, w.getX(), w.getY() + w.getBbHeight() * 0.5, w.getZ(), 2,
                    w.getBbWidth() * 0.4, w.getBbHeight() * 0.35, w.getBbWidth() * 0.4, 0.01);
        }
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        burning.clear();
        super.onRemoval(reason);
    }

    // ---- the line as clients see it --------------------------------------------------------------------------------

    /** Where the line leaves it: its burning socket, a little ahead of its eye line along the way it faces. */
    public Vec3 eye() {
        return getEyePosition().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(EYE_FORWARD * getScale()));
    }

    /** Where it aims on a target: the middle of its hitbox. */
    private static Vec3 aimPoint(LivingEntity target) {
        return target.getBoundingBox().getCenter();
    }

    /** Where the drawn line ends now, from the synced length and pitch along its facing. */
    private Vec3 lineEnd() {
        int flags = core().flags();
        double pitch = linePitch(flags) * Mth.DEG_TO_RAD;
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 dir = new Vec3(forward.x * Math.cos(pitch), Math.sin(pitch), forward.z * Math.cos(pitch));
        return lockFrom.add(dir.scale(lineLength(flags) * getScale()));
    }

    /** Tells clients the line runs from {@code from} to {@code to}: how long and how steeply, locked or not, struck or not. */
    private void showLine(Vec3 from, Vec3 to, boolean isLocked, boolean hit) {
        Vec3 d = to.subtract(from);
        double across = Math.max(1.0E-3, d.horizontalDistance());
        int len = Mth.clamp((int) Math.round(d.length() * 4.0 / getScale()), 0, FIELD_MASK);
        int pitch = Mth.clamp((int) Math.round(Mth.atan2(d.y, across) * Mth.RAD_TO_DEG) + 128, 0, FIELD_MASK);
        for (int i = 0; i < 8; i++) {
            setBit(LENGTH_BIT + i, (len >> i & 1) != 0);
            setBit(PITCH_BIT + i, (pitch >> i & 1) != 0);
        }
        setBit(LIVE_BIT, true);
        setBit(LOCKED_BIT, isLocked);
        setBit(HIT_BIT, hit);
    }

    private void hideLine() {
        setBit(LIVE_BIT, false);
        setBit(LOCKED_BIT, false);
        setBit(HIT_BIT, false);
    }

    private void setBit(int bit, boolean on) {
        if (core().flag(bit) != on) core().setFlag(bit, on);
    }

    /** From synced flags: the line's length in blocks at scale 1, eye to end. */
    public static float lineLength(int flags) {
        return (flags >> LENGTH_BIT & FIELD_MASK) / 4.0F;
    }

    /** From synced flags: the line's pitch in degrees, up positive. */
    public static float linePitch(int flags) {
        return (flags >> PITCH_BIT & FIELD_MASK) - 128;
    }

    /** From synced flags: whether a real aim set the length and pitch (a posed phase has none). */
    public static boolean lineLive(int flags) {
        return (flags >> LIVE_BIT & 1) != 0;
    }

    /** From synced flags: whether the aim is locked (it no longer follows the target). */
    public static boolean lineLocked(int flags) {
        return (flags >> LOCKED_BIT & 1) != 0;
    }

    /** From synced flags: whether the beam struck something. */
    public static boolean lineHit(int flags) {
        return (flags >> HIT_BIT & 1) != 0;
    }

    /**
     * The box the renderer culls it by: while the line is drawn it reaches the far end, so the seer (and with it the
     * line) stays drawn while only the target's end is on screen.
     */
    @Override
    public AABB getInterpolatedBoundingBox(float partialTick) {
        AABB box = super.getInterpolatedBoundingBox(partialTick);
        if (core() == null || core().idle() || !lineLive(core().flags())) return box;
        int flags = core().flags();
        double s = getScale();
        double pitch = linePitch(flags) * Mth.DEG_TO_RAD;
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
        Vec3 dir = new Vec3(forward.x * Math.cos(pitch), Math.sin(pitch), forward.z * Math.cos(pitch));
        Vec3 from = box.getBottomCenter().add(0, getEyeHeight(), 0).add(forward.scale(EYE_FORWARD * s));
        Vec3 end = from.add(dir.scale(lineLength(flags) * s));
        return box.minmax(new AABB(end, end).inflate(0.5));
    }

    // ---- moving and facing -----------------------------------------------------------------------------------------

    /** While it aims, fires or gropes its body faces exactly the way it looks, so the line lies on the line of fire. */
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

    /** Ticks until it may take aim again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it take aim again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    /** Whether the current aim is locked. Public as a test hook. */
    public boolean locked() {
        return locked;
    }

    /** Tells started, beams that hurt what they struck, beams that hit nothing. Public as test hooks. */
    public int tells() {
        return tells;
    }

    public int hits() {
        return hits;
    }

    public int misses() {
        return misses;
    }

    /** What the last beam met, or null. Public as a test hook. */
    public @Nullable LivingEntity struck() {
        return struck;
    }

    /** Where the last beam ended: its hit point, the block that stopped it, or its full reach. Public as a test hook. */
    public Vec3 beamEnd() {
        return beamEnd;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SOULFIRE_SEER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SOULFIRE_SEER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SOULFIRE_SEER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.15F;
    }
}
