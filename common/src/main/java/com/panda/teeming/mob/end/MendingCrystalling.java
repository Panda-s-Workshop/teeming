package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Mending Crystalling: a small floating crystal of the End's main island, violet facets round a star-white core, that
 * hovers behind the fight and mends it. It never attacks.
 *
 * <p>It tethers a beam (a line of light flowing from its core, a soft chime) to the most hurt End mob within 12 blocks
 * in its sight: endermen, shulkers, endermites and every Teeming mob of the End, as data packs can change it, but never
 * another crystalling. While the beam holds the mob glows and heals 2 health a second. It looks again every half
 * second and switches when another mob is clearly worse off (a tenth of its health or more); the beam drops when its
 * mob is whole, dies, leaves its reach or its sight. It has no tell: the beam locks on the moment it finds a hurt mob,
 * and the first heal lands a second later.
 *
 * <p>It keeps away from players: with a mob on its beam it drifts round to keep that mob between itself and the player
 * it watches, 4 blocks behind it; with none it backs away from a player within 8 blocks.
 *
 * <p>Counter: find it by its beam and kill it first. It has 4 health and no armour, and any hit at all breaks it, even a
 * snowball or a 1-damage arrow. It shatters into facets, its mob loses its glow and every heal stops, and a void crystal
 * falls to the floor.
 */
public class MendingCrystalling extends TeemingMonster {
    /** IDLE, and the beam holding on a mob. The beam is open-ended: it lasts while its mob is hurt, near and in sight. */
    public enum Step implements Phase { IDLE, MEND }

    /** Its beam reaches mobs this far, feet to feet. */
    public static final double BEAM_RANGE = 12.0;
    /** Health the mob on its beam gets each time, and how often: 2 a second. */
    public static final float HEAL = 2.0F;
    public static final int HEAL_EVERY = 20;
    /** It looks for the most hurt mob this often, in ticks. */
    public static final int SCAN_EVERY = 10;
    /** It switches only to a mob worse off by at least this share of its health than the one on its beam. */
    public static final float SWITCH_MARGIN = 0.1F;
    /** The mob on its beam glows; the glow is renewed every half second and fades this long after the beam drops. */
    public static final int GLOW_TICKS = 30;
    public static final int GLOW_RENEW = 10;
    /** With a mob on its beam it stays this far behind it, on the far side from the player it watches. */
    public static final double BEHIND = 4.0;
    /** Within this of its spot it has arrived and hangs still. */
    public static final double ARRIVED = 0.5;
    /** With nothing on its beam it backs away from a player this close (across the ground). */
    public static final double SHY_RANGE = 8.0;
    /** With a mob on its beam but no player near, it closes to this distance from it when it is farther. */
    public static final double STAY_NEAR = 6.0;
    /** It hovers this high over the ground: the height of a sword's swing. */
    public static final double HOVER = 1.2;
    /** Blocks a tick: drifting round to its spot or backing off (a sprinting player is faster), wandering. */
    public static final double KEEP_SPEED = 0.15;
    public static final double DRIFT_SPEED = 0.04;
    private static final int DRIFT_SPREAD = 4;
    private static final int GROUND_SEARCH = 10;
    /** Its core, where the beam leaves it: this high over its feet, in blocks at scale 1. */
    private static final double CORE_UP = 0.5;

    /** Violet light and star-white motes; the Rose Crystalling's are rose and pearl. None of them is weak-point gold. */
    public static final int BEAM_VIOLET = 0xB58CFF;
    public static final int STAR_WHITE = 0xEEF0FF;
    public static final int BEAM_ROSE = 0xFF9CC8;
    public static final int PEARL = 0xFFF0F6;
    private static final DustParticleOptions VIOLET_GLINT = new DustParticleOptions(BEAM_VIOLET, 0.6F);
    private static final DustParticleOptions ROSE_GLINT = new DustParticleOptions(BEAM_ROSE, 0.6F);
    private static final BlockParticleOption VIOLET_SHARDS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STAINED_GLASS.purple().defaultBlockState());
    private static final BlockParticleOption ROSE_SHARDS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STAINED_GLASS.pink().defaultBlockState());
    /** The beam is drawn as dashes this far apart, flowing from its core to its mob. */
    private static final double BEAM_STEP = 0.4;

    /** Who its beam mends, as data packs can change it: endermen, shulkers, endermites and the End's Teeming mobs. */
    public static final TagKey<EntityType<?>> PATIENTS = TagKey.create(Registries.ENTITY_TYPE, TeemingMod.id("mending_crystalling_patients"));

    /** The mob on its beam, or null. */
    private @Nullable LivingEntity patient;
    private int scan;
    private int heals;
    private float healed;
    private boolean shattered;
    /** Test hook: it hangs where it is; its beam still works. */
    private boolean anchored;
    private @Nullable Vec3 driftSpot;
    private int driftRepath;

    public MendingCrystalling(EntityType<? extends MendingCrystalling> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("mending_crystalling", 0.25).add(Attributes.FLYING_SPEED, 0.4);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    /** No attack goal at all: it only watches the nearest survival player in its sight, to keep away from them. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    // ---- who it mends ------------------------------------------------------------------------------------------------

    /** Whether its beam may mend {@code e}: anything in {@link #PATIENTS}, never a player or another crystalling. */
    public static boolean mends(Entity e) {
        if (e instanceof MendingCrystalling || e instanceof Player || !(e instanceof LivingEntity)) return false;
        return e.is(PATIENTS);
    }

    /** How hurt a mob is: its health as a share of its most, 0 (dying) to 1 (whole). */
    public static float share(LivingEntity e) {
        return e.getMaxHealth() <= 0.0F ? 1.0F : e.getHealth() / e.getMaxHealth();
    }

    /** Whether its beam can hold on {@code e} now: one it mends, alive, hurt, within 12 blocks and in its sight. */
    public boolean canMend(@Nullable LivingEntity e) {
        return e != null && e != this && e.isAlive() && !e.isRemoved() && e.level() == level() && mends(e)
                && e.getHealth() < e.getMaxHealth() && distanceTo(e) <= BEAM_RANGE && hasLineOfSight(e);
    }

    /** Every mob its beam could hold on now, most hurt first (nearer first when equally hurt). */
    public List<LivingEntity> candidates(ServerLevel level) {
        List<LivingEntity> out = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BEAM_RANGE), this::canMend));
        out.sort((a, b) -> {
            int c = Float.compare(share(a), share(b));
            return c != 0 ? c : Double.compare(a.distanceToSqr(this), b.distanceToSqr(this));
        });
        return out;
    }

    // ---- the beam ----------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        getNavigation().stop();
        mendTick(level);
        if (anchored) setDeltaMovement(Vec3.ZERO);
        else steerTick(level);
    }

    /** Drops a beam that can't hold, looks for the most hurt mob every half second, and holds the beam on its mob. */
    private void mendTick(ServerLevel level) {
        if (patient != null && !canMend(patient)) drop(false);
        if (--scan <= 0) {
            scan = SCAN_EVERY;
            choose(level);
        }
        if (patient != null && core().in(Step.MEND)) beamTick(level, patient);
        else if (core().in(Step.MEND)) core().end();
    }

    /**
     * Picks the mob to mend: the most hurt one, if nothing is on its beam yet or it is worse off than the one on it by
     * {@link #SWITCH_MARGIN} or more. A mob a pack vetoes is passed over for the next most hurt.
     */
    private void choose(ServerLevel level) {
        for (LivingEntity c : candidates(level)) {
            if (c == patient) return;                                             // nobody is clearly worse off
            if (patient != null && share(c) > share(patient) - SWITCH_MARGIN) return;
            if (lock(level, c)) return;
        }
    }

    /** Locks the beam on {@code e}: the old mob loses its glow, the new one starts glowing, a chime. True if it locked. */
    private boolean lock(ServerLevel level, LivingEntity e) {
        LivingEntity old = patient;
        if (!startPhase(Step.MEND, 0, e)) return false;
        if (old != null) unglow(old);
        patient = e;
        glow(e);
        playSound(ModSounds.MENDING_CRYSTALLING_TETHER.get(), 0.9F, 1.0F);
        Vec3 at = mendPoint(e);
        level.sendParticles(glint(), true, true, at.x, at.y, at.z, 8, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.0);
        return true;
    }

    /** The beam lets go: the mob loses its glow, and it goes idle. {@code snapped}: it broke, with a fizzle at the mob. */
    private void drop(boolean snapped) {
        LivingEntity old = patient;
        patient = null;
        scan = 0;                                                                 // look for the next one at once
        if (old != null) {
            unglow(old);
            if (snapped && level() instanceof ServerLevel level) {
                Vec3 at = mendPoint(old);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, at.x, at.y, at.z, 16, old.getBbWidth() * 0.4, old.getBbHeight() * 0.3, old.getBbWidth() * 0.4, 0.02);
                level.playSound(null, at.x, at.y, at.z, ModSounds.MENDING_CRYSTALLING_SNAP.get(), getSoundSource(), 1.0F, 1.0F);
            }
        }
        if (core().in(Step.MEND)) core().end();
    }

    /** One tick of the beam: it faces its mob, heals it 2 every second, keeps it glowing and draws the beam. */
    private void beamTick(ServerLevel level, LivingEntity e) {
        face(e.position().subtract(position()));
        int t = core().elapsed();
        if (t > 0 && t % HEAL_EVERY == 0) {
            if (MechanicEvent.post(this, "heal", e)) {
                float before = e.getHealth();
                e.heal(HEAL);
                heals++;
                healed += e.getHealth() - before;
                pulse(level, e);
            }
            level.playSound(null, getX(), getY() + CORE_UP, getZ(), ModSounds.MENDING_CRYSTALLING_MEND.get(), getSoundSource(), 0.6F, 1.0F);
        }
        if (t % GLOW_RENEW == 0) glow(e);
        drawBeam(level, e);
    }

    private void glow(LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false), this);
    }

    /** Takes away the glow its beam gave (a longer glow from something else, a spectral arrow, stays). */
    private static void unglow(LivingEntity e) {
        MobEffectInstance glow = e.getEffect(MobEffects.GLOWING);
        if (glow != null && glow.getDuration() <= GLOW_TICKS) e.removeEffect(MobEffects.GLOWING);
    }

    /**
     * The beam: violet dashes every other tick that flow from its core into the mob, and every few ticks a star-white
     * mote that runs the whole beam. Full-bright trail particles, so the line shows in the End's dim light.
     */
    private void drawBeam(ServerLevel level, LivingEntity e) {
        Vec3 from = corePoint();
        Vec3 to = mendPoint(e);
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 0.3) return;
        Vec3 dir = d.scale(1.0 / length);
        int beam = 0xFF000000 | beamColor();
        if (tickCount % 2 == 0) {
            double creep = (tickCount / 2 % 2) * (BEAM_STEP / 2);                // the dashes creep toward the mob
            for (double run = creep; run < length - 0.1; run += BEAM_STEP) {
                Vec3 p = from.add(dir.scale(run));
                Vec3 q = from.add(dir.scale(Math.min(length, run + BEAM_STEP)));
                level.sendParticles(new TrailParticleOption(q, beam, 4), true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (tickCount % 5 == 0) {
            int ticks = Mth.clamp((int) Math.round(length * 1.5), 4, 20);
            level.sendParticles(new TrailParticleOption(to, 0xFF000000 | starColor(), ticks), true, true, from.x, from.y, from.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (tickCount % 8 == 0) {                                                // motes rising round the mob it mends
            double turn = tickCount * 0.3;
            double r = e.getBbWidth() * 0.5 + 0.2;
            for (int i = 0; i < 3; i++) {
                double a = turn + i * (Math.PI * 2 / 3);
                double x = e.getX() + Math.cos(a) * r, z = e.getZ() + Math.sin(a) * r, y = e.getY() + e.getBbHeight() * 0.2;
                level.sendParticles(new TrailParticleOption(new Vec3(x, y + 0.7, z), beam, 10), true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** A heal: a ring of star-white light closes into the mob. */
    private void pulse(ServerLevel level, LivingEntity e) {
        Vec3 at = mendPoint(e);
        double r = e.getBbWidth() * 0.5 + 0.6;
        int star = 0xFF000000 | starColor();
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI * 2 / 8);
            level.sendParticles(new TrailParticleOption(at, star, 6), true, true, at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(glint(), true, true, at.x, at.y, at.z, 4, e.getBbWidth() * 0.3, e.getBbHeight() * 0.25, e.getBbWidth() * 0.3, 0.0);
    }

    /** Where the beam leaves it: its core. */
    public Vec3 corePoint() {
        return position().add(0.0, CORE_UP * getScale(), 0.0);
    }

    /** Where the beam meets a mob: a little above the middle of its body. */
    private static Vec3 mendPoint(LivingEntity e) {
        return e.position().add(0.0, e.getBbHeight() * 0.55, 0.0);
    }

    private boolean rose() {
        return "rose_crystalling".equals(look());
    }

    private int beamColor() {
        return rose() ? BEAM_ROSE : BEAM_VIOLET;
    }

    private int starColor() {
        return rose() ? PEARL : STAR_WHITE;
    }

    private DustParticleOptions glint() {
        return rose() ? ROSE_GLINT : VIOLET_GLINT;
    }

    // ---- keeping away -----------------------------------------------------------------------------------------------

    /** The player (or whatever else it is told to watch) it keeps away from, or null. */
    private @Nullable LivingEntity watched() {
        LivingEntity t = getTarget();
        if (t == null || t == this || !t.isAlive() || t.isRemoved() || t.level() != level()) return null;
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) return null;
        return t;
    }

    /**
     * Where it goes: behind the mob on its beam, away from the player it watches; near the mob when nobody is watched;
     * away from a player within 8 blocks with nothing on its beam; otherwise it drifts round where it is.
     */
    private void steerTick(ServerLevel level) {
        LivingEntity from = watched();
        if (patient != null && from != null) {
            hideBehind(level, patient, from);
        } else if (patient != null) {
            if (distanceTo(patient) > STAY_NEAR) {
                Vec3 side = position().subtract(patient.position()).horizontal();
                if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
                Vec3 at = patient.position().add(side.normalize().scale(STAY_NEAR - 2.0));
                steer(new Vec3(at.x, surfaceBelow(level, at.x, Math.max(getY(), patient.getY()) + 2.0, at.z) + HOVER, at.z), KEEP_SPEED, 0.12);
            } else {
                hold(level);
            }
        } else if (from != null && position().subtract(from.position()).horizontalDistance() < SHY_RANGE) {
            backOff(level, from);
        } else {
            driftTick(level);
        }
    }

    /**
     * Its spot behind the mob on its beam: {@link #BEHIND} blocks past the mob on the line from {@code from} through it
     * (or up to 60 degrees round either side when that spot is shut), at its hover height. Null when every one is shut.
     * Public as a test hook.
     */
    public @Nullable Vec3 hidingSpot(LivingEntity mob, LivingEntity from) {
        if (!(level() instanceof ServerLevel level)) return null;
        Vec3 away = mob.position().subtract(from.position()).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = position().subtract(from.position()).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
        away = away.normalize();
        for (float turn : new float[] {0, 30, -30, 60, -60}) {
            Vec3 flat = mob.position().add(away.yRot(turn * Mth.DEG_TO_RAD).scale(BEHIND));
            Vec3 spot = new Vec3(flat.x, surfaceBelow(level, flat.x, Math.max(getY(), mob.getY()) + 2.0, flat.z) + HOVER, flat.z);
            if (spot.distanceToSqr(position()) <= ARRIVED * ARRIVED || open(spot)) return spot;
        }
        return null;
    }

    private void hideBehind(ServerLevel level, LivingEntity mob, LivingEntity from) {
        Vec3 spot = hidingSpot(mob, from);
        if (spot == null || spot.distanceToSqr(position()) <= ARRIVED * ARRIVED) hold(level);
        else steer(spot, KEEP_SPEED, 0.15);
        face(mob.position().subtract(position()));
    }

    /**
     * Backs off from {@code from} to a spot {@link #SHY_RANGE} away (straight away, or up to 105 degrees off), else a
     * short way off, open air with a clear line to it; cornered with every spot shut, it slips straight away, rising
     * where a wall stops it.
     */
    private void backOff(ServerLevel level, LivingEntity from) {
        Vec3 away = position().subtract(from.position()).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0.0F, getYRot());
        away = away.normalize();
        double run = SHY_RANGE - position().subtract(from.position()).horizontalDistance() + 1.0;
        for (double distance : new double[] {Math.max(2.0, run), 2.0}) {
            for (float turn : new float[] {0, 35, -35, 70, -70, 105, -105}) {
                Vec3 flat = position().add(away.yRot(turn * Mth.DEG_TO_RAD).scale(distance));
                Vec3 spot = new Vec3(flat.x, surfaceBelow(level, flat.x, getY() + 2.0, flat.z) + HOVER, flat.z);
                if (open(spot)) {
                    steer(spot, KEEP_SPEED, 0.3);
                    return;
                }
            }
        }
        Vec3 v = away.scale(KEEP_SPEED).add(0, horizontalCollision ? KEEP_SPEED : 0.0, 0);
        setDeltaMovement(getDeltaMovement().lerp(v, 0.3));
        face(v);
    }

    /** Holds where it is, at its hover height. */
    private void hold(ServerLevel level) {
        double y = surfaceBelow(level, getX(), getY() + 1.0, getZ()) + HOVER;
        setDeltaMovement(getDeltaMovement().multiply(0.6, 0.6, 0.6).add(0, Mth.clamp((y - getY()) * 0.08, -0.04, 0.04), 0));
    }

    /** With nothing to do it drifts from spot to spot round where it is, at its hover height. */
    private void driftTick(ServerLevel level) {
        if (--driftRepath <= 0 || driftSpot == null || driftSpot.distanceToSqr(position()) < 1.0) {
            double x = getX() + random.nextInt(DRIFT_SPREAD * 2 + 1) - DRIFT_SPREAD;
            double z = getZ() + random.nextInt(DRIFT_SPREAD * 2 + 1) - DRIFT_SPREAD;
            Vec3 spot = new Vec3(x, surfaceBelow(level, x, getY() + 2.0, z) + HOVER, z);
            driftSpot = open(spot) ? spot : null;
            driftRepath = 80;
        }
        if (driftSpot != null) steer(driftSpot, DRIFT_SPEED, 0.08);
        else hold(level);
    }

    /** The top of the first solid block or fluid below {@code y}, within 10 blocks; its own height less its hover if none. */
    private double surfaceBelow(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < GROUND_SEARCH; i++, pos.move(0, -1, 0)) {
            if (!level.getFluidState(pos).isEmpty()) return pos.getY() + level.getFluidState(pos).getHeight(level, pos);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return pos.getY() + shape.max(Direction.Axis.Y);
        }
        return getY() - HOVER;
    }

    /** Whether it fits at {@code spot} (its feet there) and has a clear line to it. */
    private boolean open(Vec3 spot) {
        AABB there = getBoundingBox().move(spot.subtract(position()));
        if (!level().noCollision(this, there)) return false;
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = spot.add(0, getBbHeight() * 0.5, 0);
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this)).getType() == HitResult.Type.MISS;
    }

    /** Eases its flight toward {@code want} (its feet there) at up to {@code speed} blocks a tick. */
    private void steer(Vec3 want, double speed, double ease) {
        Vec3 to = want.subtract(position());
        if (to.length() > speed) to = to.normalize().scale(speed);
        Vec3 v = getDeltaMovement().lerp(to, ease);
        setDeltaMovement(v);
        if (patient == null && v.horizontalDistanceSqr() > 4.0E-4) face(v);
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---- the counter: any hit breaks it ------------------------------------------------------------------------------

    /**
     * Any hit at all breaks it: a blow, an arrow, a snowball that does no damage, a potion, a fall of rock. The hit is
     * made at least ten times its health, never swallowed by the cooldown after an earlier hit, so it always dies of it.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isAlive() && !isInvulnerableTo(level, source)
                && MechanicEvent.post(this, "shatter", source.getEntity() instanceof LivingEntity by ? by : null)) {
            damageCooldownTime = 0;
            amount = Math.max(amount, (getMaxHealth() + getAbsorptionAmount()) * 10.0F);
        }
        return super.hurtServer(level, source, amount);
    }

    /** Its death is the payoff: a shatter of facets and light, the beam snaps and its mob loses its glow. */
    @Override
    public void die(DamageSource source) {
        if (!shattered && !isRemoved() && level() instanceof ServerLevel level) {
            shattered = true;
            Vec3 c = corePoint();
            level.sendParticles(rose() ? ROSE_SHARDS : VIOLET_SHARDS, true, true, c.x, c.y, c.z, 30, 0.25, 0.3, 0.25, 0.15);
            int star = 0xFF000000 | starColor(), beam = 0xFF000000 | beamColor();
            for (int i = 0; i < 12; i++) {                                       // facets flying out from the core
                double a = i * (Math.PI * 2 / 12), up = (i % 3 - 1) * 0.5;
                Vec3 out = c.add(Math.cos(a) * 1.4, up, Math.sin(a) * 1.4);
                level.sendParticles(new TrailParticleOption(out, i % 2 == 0 ? star : beam, 6), true, true, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(ParticleTypes.END_ROD, true, true, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.06);
            drop(true);
        }
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        if (patient != null) unglow(patient);
        patient = null;
        super.onRemoval(reason);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- test hooks --------------------------------------------------------------------------------------------------

    /** The mob on its beam now, or null. Public as a test hook. */
    public @Nullable LivingEntity patient() {
        return patient;
    }

    /** Heals its beam has given, and the health they restored. Public as test hooks. */
    public int heals() {
        return heals;
    }

    public float healed() {
        return healed;
    }

    /** Holds it where it is (true) or lets it fly again; its beam works either way. Public as a test hook. */
    public void anchor(boolean on) {
        anchored = on;
        if (on) setDeltaMovement(Vec3.ZERO);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.MENDING_CRYSTALLING_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MENDING_CRYSTALLING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MENDING_CRYSTALLING_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return (rose() ? 1.15F : 1.0F) + random.nextFloat() * 0.1F;
    }
}
