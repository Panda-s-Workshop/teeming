package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAnimal;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Driftsong: a vast, gentle leviathan of the void below the outer End islands. It drifts at 0.05 blocks a tick,
 * turning gently, keeping under the islands and a few blocks clear of their undersides. Every 400 ticks its back lights
 * for 20 ticks (the swell) and then it sings: every player within 24 blocks of it is given Slow Falling for 200 ticks,
 * a gift that saves anyone falling off an island nearby. It never attacks. Hurt, it dives 10 blocks and drifts on, its
 * course turned away from whoever hurt it, so its song moves out of their reach. Rare: one within 128 blocks.
 *
 * <p>It is too big to path or to shove: it has no goals and no navigation, steers itself (its course, a probe for
 * cover ahead every half second), never pushes anything and is never pushed or knocked back.
 */
public class Driftsong extends TeemingAnimal {
    public enum Step implements Phase { IDLE, SWELL, SING, DIVE }

    /** Its drift, in blocks a tick. */
    public static final double DRIFT_SPEED = 0.05;
    /** A song every this many ticks, counted from the start of one swell to the start of the next. */
    public static final int SONG_EVERY = 400;
    /** Its back lights this long before the song lands. */
    public static final int SWELL_TICKS = 20;
    /** The song itself: its back stays lit and its jaw open this long after the gift. */
    public static final int SING_TICKS = 60;
    /** Players within this many blocks of the middle of its body hear the song. */
    public static final double SONG_RANGE = 24.0;
    /** The gift: Slow Falling for this long. */
    public static final int SLOW_FALLING_TICKS = 200;
    /** Hurt, it dives this many blocks, at {@link #DIVE_SPEED}. */
    public static final double DIVE_DEPTH = 10.0;
    public static final double DIVE_SPEED = 0.25;
    /** The dive's pose lasts this long: the 10 blocks at 0.25 a tick. */
    public static final int DIVE_TICKS = 40;
    /** It never dives closer than this to the bottom of the world (the void below). */
    public static final double VOID_FLOOR = 4.0;
    /** One within this many blocks: the local cap of the rare giants. */
    public static final int CAP_RADIUS = 128;
    /** Turning, in degrees a tick: a 180-degree turn takes 18 seconds. */
    public static final float TURN = 0.5F;
    /** It keeps its back at least this many blocks under an island's underside, sinking when it is closer. */
    public static final double CLEARANCE = 4.0;
    public static final double SINK_SPEED = 0.02;
    /** How often it probes for cover, how far past its nose, and how far it looks for cover when it has none. */
    public static final int PROBE_EVERY = 10;
    public static final double LOOK_AHEAD = 10.0;
    public static final double COVER_SEARCH = 16.0;
    /** Now and then a new course up to 60 degrees off the old one. */
    public static final int WANDER_MIN = 200, WANDER_MAX = 400;
    public static final float WANDER_TURN = 60.0F;
    /** The song is played this loud: heard 64 blocks away. */
    public static final float SONG_VOLUME = 4.0F;

    public static final int STAR_COLOR = 0xEEE8FF;
    public static final int DEEP_COLOR = 0x4A3A8A;
    private static final DustParticleOptions STAR = new DustParticleOptions(STAR_COLOR, 2.0F);
    private static final DustParticleOptions STAR_SMALL = new DustParticleOptions(STAR_COLOR, 1.2F);
    private static final DustParticleOptions DEEP = new DustParticleOptions(DEEP_COLOR, 2.0F);

    private int songIn = SONG_EVERY;
    private float course = Float.NaN;
    private int wanderIn;
    private int holdFor;
    private int mooredFor;
    private boolean crowded;
    private boolean lit;
    private double diveTo = Double.NaN;
    private Vec3 motion = Vec3.ZERO;

    public Driftsong(EntityType<? extends Driftsong> type, Level level) {
        super(type, level, Step.values());
        setNoGravity(true);
        xpReward = 10;
        wanderIn = WANDER_MIN + random.nextInt(WANDER_MAX - WANDER_MIN + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.animal("driftsong", DRIFT_SPEED);
    }

    /** No goals: it steers itself and never fights. */
    @Override
    protected void registerGoals() {}

    @Override
    public int visualStages() {
        return 2;
    }

    // ---- the drift ---------------------------------------------------------------------------------------------------

    /** It moves only as it steers itself: no gravity, no friction, nothing else adds to it. */
    @Override
    public void travel(Vec3 input) {
        if (level().isClientSide()) return;
        if (isNoAi()) motion = Vec3.ZERO;
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
    }

    private static Vec3 heading(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
    }

    /** This tick's steering: its course (with the turn limit), the drift along it, and any dive or sink. */
    private void steer(ServerLevel level) {
        if (Float.isNaN(course)) course = getYRot();
        if (tickCount % PROBE_EVERY == 0) probe(level, holdFor <= 0);
        if (holdFor > 0) {
            holdFor--;
        } else if (--wanderIn <= 0) {
            course += (random.nextFloat() * 2.0F - 1.0F) * WANDER_TURN;
            wanderIn = WANDER_MIN + random.nextInt(WANDER_MAX - WANDER_MIN + 1);
        }
        float yaw = Mth.approachDegrees(getYRot(), course, TURN);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        Vec3 drift = mooredFor > 0 ? Vec3.ZERO : heading(yaw).scale(DRIFT_SPEED);
        if (mooredFor > 0) mooredFor--;
        double vy = 0.0;
        if (!Double.isNaN(diveTo)) {
            // a dive ends where it was headed, or on whatever stops it on the way down
            if (getY() - diveTo < 1.0E-4 || verticalCollisionBelow) diveTo = Double.NaN;
            else vy = Math.max(-DIVE_SPEED, diveTo - getY());
        } else if (crowded) {
            vy = -SINK_SPEED;
        }
        motion = new Vec3(drift.x, vy, drift.z);
    }

    /**
     * Every half second: is an underside too close above (it sinks), will its nose leave the islands' cover (it turns
     * back), or has it no cover at all (it turns toward the nearest it finds)?
     */
    private void probe(ServerLevel level, boolean mayTurn) {
        double top = getBoundingBox().maxY;
        crowded = gapAbove(level, top) < CLEARANCE;
        if (!mayTurn) return;
        boolean here = covered(level, getX(), getZ(), top);
        Vec3 nose = position().add(heading(getYRot()).scale(getBbWidth() / 2.0 + LOOK_AHEAD));
        // once it is turning back it holds that turn (a new pick each probe could swing it the other way)
        boolean turning = Math.abs(Mth.degreesDifference(getYRot(), course)) > 30.0F;
        if (horizontalCollision || (here && !covered(level, nose.x, nose.z, top))) {
            if (!turning) course = getYRot() + 180.0F + (random.nextFloat() * 2.0F - 1.0F) * 40.0F;
            wanderIn = WANDER_MIN;
        } else if (!here && !turning) {
            for (int i = 0; i < 8; i++) {
                float yaw = getYRot() + i * 45.0F;
                Vec3 at = position().add(heading(yaw).scale(COVER_SEARCH));
                if (covered(level, at.x, at.z, top)) {
                    course = yaw;
                    break;
                }
            }
        }
    }

    /** An island somewhere above height {@code top} in the column at x, z (an unloaded column counts as none). */
    public static boolean covered(ServerLevel level, double x, double z, double top) {
        BlockPos at = BlockPos.containing(x, top, z);
        if (!level.hasChunkAt(at)) return false;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) > top;
    }

    /** Blocks of open air above its back, up to a little past {@link #CLEARANCE}. */
    private double gapAbove(ServerLevel level, double top) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(getX(), top, getZ()).mutable();
        for (int i = 0; i <= (int) CLEARANCE + 1; i++, pos.move(0, 1, 0)) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return pos.getY() - top;
        }
        return CLEARANCE + 1.0;
    }

    /** Its course, in degrees (server side). */
    public float course() {
        return course;
    }

    /**
     * Sets its course (it turns to it at its own pace) and holds it for {@code ticks}: no turning back from an edge of
     * cover, no wandering. Public as a test hook.
     */
    public void setCourse(float yaw, int ticks) {
        course = yaw;
        holdFor = ticks;
    }

    /** Holds it still (no drift) for {@code ticks} while everything else runs: its song, its dive. Public as a test hook. */
    public void moor(int ticks) {
        mooredFor = ticks;
    }

    // ---- the song ----------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (songIn > 0) songIn--;
        if (songIn <= 0 && core().idle()) swell();
        if (core().in(Step.SWELL)) backMotes(level, 2);
        else if (core().in(Step.SING) && core().elapsed() % 6 == 0) backMotes(level, 1);
        else if (core().in(Step.DIVE) && tickCount % 2 == 0) {
            level.sendParticles(DEEP, true, true, getX(), getY() + getBbHeight(), getZ(), 4, 1.5, 0.3, 1.5, 0.0);
        }
        steer(level);
    }

    /** Its back lights: the swell before the song. The clock starts over even if a listener vetoes it. */
    private void swell() {
        songIn = SONG_EVERY;
        if (startPhase(Step.SWELL, SWELL_TICKS, null)) playSound(ModSounds.DRIFTSONG_SWELL.get(), SONG_VOLUME * 0.5F, 1.0F);
    }

    /** Star-white motes rising off its back while it is lit, sent far so players high above see them. */
    private void backMotes(ServerLevel level, int count) {
        double top = getBoundingBox().maxY;
        for (int i = 0; i < count; i++) {
            double along = (random.nextDouble() - 0.5) * 4.0;
            Vec3 h = heading(getYRot());
            level.sendParticles(STAR, true, true, getX() + h.x * along, top + 0.2, getZ() + h.z * along, 1, 0.6, 0.1, 0.6, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.SWELL && level() instanceof ServerLevel level && startPhase(Step.SING, SING_TICKS, null)) {
            sing(level);
            return;
        }
        core().end();
    }

    /** Server: the song itself. Every player who hears it gets the gift; returns how many did. Public as a test hook. */
    public int sing(ServerLevel level) {
        if (isDeadOrDying()) return 0;
        playSound(ModSounds.DRIFTSONG_SONG.get(), SONG_VOLUME, 1.0F);
        playSound(ModSounds.DRIFTSONG_SONG_ECHO.get(), SONG_VOLUME, 1.0F);
        Vec3 centre = songCentre();
        for (int ring = 1; ring <= 3; ring++) {
            double r = ring * 3.0;
            int points = 12 * ring;
            for (int i = 0; i < points; i++) {
                double a = i * Math.PI * 2.0 / points;
                level.sendParticles(STAR_SMALL, true, true, centre.x + Math.cos(a) * r, centre.y, centre.z + Math.sin(a) * r, 1, 0.0, 0.1, 0.0, 0.0);
            }
        }
        int given = 0;
        for (Player player : hearers(level)) {
            if (!MechanicEvent.post(this, "gift", player)) continue;
            if (player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS), this)) given++;
            // a small ring of motes at their feet: low, so it never fills the view of the player who got it
            level.sendParticles(STAR_SMALL, true, true, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.6, 0.05, 0.6, 0.0);
        }
        return given;
    }

    /** The middle of its body, where the song's reach is measured from. */
    public Vec3 songCentre() {
        return getBoundingBox().getCenter();
    }

    /** Whether a player standing at {@code at} hears a song sung from {@code centre}: 24 blocks or less. */
    public static boolean hears(Vec3 centre, Vec3 at) {
        return centre.distanceToSqr(at) <= SONG_RANGE * SONG_RANGE;
    }

    /** The players who would hear it sing now (not spectators). */
    public List<Player> hearers(ServerLevel level) {
        Vec3 centre = songCentre();
        return level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(SONG_RANGE),
                p -> p.isAlive() && !p.isSpectator() && hears(centre, p.position()));
    }

    /** Ticks until its next swell (server side). */
    public int songIn() {
        return songIn;
    }

    /** Sets the ticks until its next swell. Public as a test hook. */
    public void setSongIn(int ticks) {
        songIn = ticks;
    }

    // ---- hurt: the dive ------------------------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) dive(source.getEntity());
        return hurt;
    }

    /**
     * Server: dives 10 blocks (never closer than 4 to the bottom of the world) and turns its course away from
     * {@code from}. A dive already under way runs on. True if it started one. Public as a test hook.
     */
    public boolean dive(@Nullable Entity from) {
        if (level().isClientSide() || !Double.isNaN(diveTo) || core().in(Step.DIVE)) return false;
        if (!startPhase(Step.DIVE, DIVE_TICKS, from instanceof LivingEntity living ? living : null)) return false;
        diveTo = Math.max(getY() - DIVE_DEPTH, level().getMinY() + VOID_FLOOR);
        if (from != null) {
            Vec3 away = position().subtract(from.position());
            if (away.horizontalDistanceSqr() > 1.0E-6) course = (float) (Mth.atan2(away.z, away.x) * Mth.RAD_TO_DEG) - 90.0F;
            holdFor = 0;
        }
        playSound(ModSounds.DRIFTSONG_DIVE.get(), 2.0F, 1.0F);
        return true;
    }

    /** Where its dive ends, or NaN when it isn't diving (server side). */
    public double diveTo() {
        return diveTo;
    }

    // ---- too big to shove ----------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        boolean glowing = core().in(Step.SWELL) || core().in(Step.SING);
        if (glowing != lit) {
            lit = glowing;
            core().setStage(glowing ? 1 : 0);
        }
    }

    /** It never shoves anything: a drift into a player can never push them off an edge. */
    @Override
    protected void pushEntities() {}

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Too vast to knock back. */
    @Override
    public void knockback(double strength, double x, double z, DamageSource source, float damage, boolean flag) {}

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SongIn", songIn);
        if (!Float.isNaN(course)) output.putFloat("Course", course);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        songIn = Mth.clamp(input.getIntOr("SongIn", SONG_EVERY), 0, SONG_EVERY);
        float saved = input.getFloatOr("Course", Float.NaN);
        if (!Float.isNaN(saved)) course = saved;
    }

    // ---- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.DRIFTSONG_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.DRIFTSONG_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DRIFTSONG_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.1F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }
}
