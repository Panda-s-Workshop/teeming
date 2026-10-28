package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.hoglin.HoglinBase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Phantasm Lantern: a hooded lantern-spirit of the warped forests, a pale flame in a cage of warped fungus, that drifts
 * at head height and keeps about 8 blocks from players. It never strikes anyone itself (attack 0). Every 200 ticks,
 * with a target within 16 blocks, it conjures for 1 s (tell: it stops, the cowl tips back, the flame swells and spins,
 * soul wisps swirl in round it, a whispering chime), then up to 3 illusions step out of its flame: copies of the kinds
 * of mobs within 16 blocks of it, or, with none about, of its home's hoglins, piglins, endermen and striders. Never a
 * player, a boss or another Teeming mob.
 *
 * <p>An illusion looks like the real mob and rushes the lantern's target and swings at it, but it has 1 health, deals
 * no damage, makes no sound and casts no shadow, and the first blow pops it into wisps. Illusions drop nothing, give no
 * experience and never count as kills. They all vanish the moment the lantern dies, and none outlives it.
 *
 * <p>Counter: look for the missing shadows and kill the lantern. Every blow that lands on it gutters its flame and
 * snuffs its newest illusion.
 */
public class PhantasmLantern extends TeemingMonster {
    /** IDLE (drifting, keeping away), the conjuring (tell), the release of the illusions. */
    public enum Step implements Phase { IDLE, CONJURE, RELEASE }

    /** 1 s of conjuring before the illusions step out. */
    public static final int TELL_TICKS = 20;
    /** The release itself: the flame bursts and the illusions step out of it. */
    public static final int RELEASE_TICKS = 10;
    /** Ticks from one release to the next, at the soonest. */
    public static final int CONJURE_EVERY = 200;
    /** A fresh lantern may conjure this soon. */
    public static final int FIRST_CONJURE = 40;
    /** It holds at most this many illusions at once; each release tops them up to it. */
    public static final int MAX_ILLUSIONS = 3;
    /** It copies the kinds of mobs this close to it, and conjures only with its target this close. */
    public static final double COPY_RANGE = 16.0;
    public static final double CONJURE_RANGE = 16.0;
    /** An illusion's health, and the damage it deals. */
    public static final float ILLUSION_HEALTH = 1.0F;
    public static final double ILLUSION_DAMAGE = 0.0;
    /** An illusion that strays this far from its lantern pops. */
    public static final double LEASH = 24.0;
    /** Illusions step out this far from it, on the ground. */
    public static final double SPREAD_MIN = 1.5;
    public static final double SPREAD_MAX = 3.0;

    /** It keeps this far from players; it backs off when one comes closer than a block inside it. */
    public static final double KEEP_AWAY = 8.0;
    /** It hovers this high over the ground. */
    public static final double HOVER = 1.2;
    /** Blocks a tick: backing off (a sprinting player is a little faster), closing in, drifting. */
    public static final double BACK_SPEED = 0.25;
    public static final double CLOSE_SPEED = 0.12;
    public static final double DRIFT_SPEED = 0.06;
    private static final int DRIFT_SPREAD = 6;
    private static final int GROUND_SEARCH = 10;

    public static final int WARPED_COLOR = 0x6FE6DC;
    public static final int CRIMSON_COLOR = 0xF49AB4;
    private static final DustParticleOptions WARPED_DUST = new DustParticleOptions(WARPED_COLOR, 0.9F);
    private static final DustParticleOptions CRIMSON_DUST = new DustParticleOptions(CRIMSON_COLOR, 0.9F);
    /** The kinds it copies when there are no mobs about to copy: its home's. */
    public static final List<EntityType<?>> HOME_KINDS = List.of(EntityTypes.HOGLIN, EntityTypes.PIGLIN, EntityTypes.ENDERMAN, EntityTypes.STRIDER);

    /** The entity ids of its illusions, for clients (one slot each, -1 for none): the renderers read them. */
    private static final List<EntityDataAccessor<Integer>> DATA_ILLUSIONS = List.of(
            SynchedEntityData.defineId(PhantasmLantern.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(PhantasmLantern.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(PhantasmLantern.class, EntityDataSerializers.INT));

    private final Illusions illusions = new Illusions(this, MAX_ILLUSIONS, LEASH, this::popped);
    /** Ticks until it may release again. */
    private int untilConjure = FIRST_CONJURE;
    /** Test hook: it holds still, so only its phase machine runs. */
    private boolean anchored;
    private @Nullable Vec3 driftSpot;
    private int driftRepath;

    public PhantasmLantern(EntityType<? extends PhantasmLantern> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("phantasm_lantern", 0.25).add(Attributes.FLYING_SPEED, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        for (EntityDataAccessor<Integer> slot : DATA_ILLUSIONS) builder.define(slot, -1);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    /** It steers itself (see {@link #customServerAiStep}); the goals only look about and pick targets. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- the conjuring -----------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide() && untilConjure > 0) untilConjure--;
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        illusions.tick(level, illusionTarget());
        syncIllusions();
        if (core().in(Step.CONJURE)) {
            conjureTick(level);
        } else if (core().idle() && !isNoAi() && untilConjure <= TELL_TICKS && illusions.size() < MAX_ILLUSIONS) {
            LivingEntity target = getTarget();
            if (valid(target) && distanceTo(target) <= CONJURE_RANGE) conjure(target);
        }
    }

    /** Who its illusions go for: its target while it has a live one, else no one (they drift back to it). */
    private @Nullable LivingEntity illusionTarget() {
        LivingEntity target = getTarget();
        if (!valid(target)) target = core().target();
        return valid(target) ? target : null;
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && !illusions.holds(target)
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /**
     * Starts the conjuring if it is free to; true if it did. Ignores the time since the last release. Public as a
     * test hook.
     */
    public boolean conjure(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle()) return false;
        if (!startPhase(Step.CONJURE, TELL_TICKS, target)) return false;
        playSound(ModSounds.PHANTASM_LANTERN_CONJURE.get(), 1.0F, 0.9F);
        return true;
    }

    /** Wisps spiral in round it, closing on the flame as the conjuring builds; a second chime half way. */
    private void conjureTick(ServerLevel level) {
        int t = core().elapsed();
        LivingEntity target = core().target();
        if (target != null) face(target.position().subtract(position()));
        double k = Math.min(1.0, t / (double) TELL_TICKS);
        double radius = 1.6 - 1.1 * k;
        double y = getY() + getBbHeight() * 0.45;
        for (int arm = 0; arm < 3; arm++) {
            double a = t * 0.55 + arm * (Math.PI * 2 / 3);
            double px = getX() + Math.cos(a) * radius, pz = getZ() + Math.sin(a) * radius;
            level.sendParticles(wisp(), true, true, px, y + 0.25 * Math.sin(a * 2), pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (t % 2 == 0) level.sendParticles(dust(), true, true, getX(), y, getZ(), 2, 0.35, 0.3, 0.35, 0.0);
        if (t == TELL_TICKS / 2) playSound(ModSounds.PHANTASM_LANTERN_CONJURE.get(), 0.7F, 1.2F);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.CONJURE && level() instanceof ServerLevel level) {
            untilConjure = CONJURE_EVERY;
            if (startPhase(Step.RELEASE, RELEASE_TICKS, core().target())) release(level);
            else core().end();
        } else {
            core().end();
        }
    }

    /**
     * The release: illusions step out of its flame until it holds {@link #MAX_ILLUSIONS}, each a copy of a mob within
     * {@link #COPY_RANGE} (the more of a kind about, the likelier it is copied), or of one of its {@link #HOME_KINDS}
     * when none is about. Each illusion posts its own "illusion" step. Returns the new illusions. Public as a test hook.
     */
    public List<Mob> release(ServerLevel level) {
        return release(level, sources(level, core().target()));
    }

    /** The release, copying from {@code sources} (empty: its home's kinds). Public as a test hook. */
    public List<Mob> release(ServerLevel level, List<Mob> sources) {
        LivingEntity target = core().target();
        List<Mob> made = new ArrayList<>();
        int want = MAX_ILLUSIONS - illusions.size();
        for (int i = 0; i < want; i++) {
            if (!MechanicEvent.post(this, "illusion", target)) continue;
            Mob source = sources.isEmpty() ? null : sources.get(random.nextInt(sources.size()));
            EntityType<?> type = source != null ? source.getType() : HOME_KINDS.get(random.nextInt(HOME_KINDS.size()));
            Vec3 at = spot(level, type, i, want);
            if (at == null) continue;
            Mob m = illusions.make(level, type, source, at, getYRot());
            if (m == null) continue;
            made.add(m);
            level.sendParticles(wisp(), true, true, at.x, at.y + m.getBbHeight() * 0.5, at.z, 12, m.getBbWidth() * 0.4, m.getBbHeight() * 0.35,
                    m.getBbWidth() * 0.4, 0.02);
        }
        syncIllusions();
        double y = getY() + getBbHeight() * 0.45;
        level.sendParticles(wisp(), true, true, getX(), y, getZ(), 16, 0.3, 0.3, 0.3, 0.06);
        level.sendParticles(dust(), true, true, getX(), y, getZ(), 10, 0.5, 0.4, 0.5, 0.0);
        level.playSound(null, getX(), y, getZ(), ModSounds.PHANTASM_LANTERN_RELEASE.get(), getSoundSource(), 1.1F, 1.0F);
        return made;
    }

    /**
     * The mobs it may copy: every copyable mob within {@link #COPY_RANGE} of it but its own target. Public as a test
     * hook.
     */
    public List<Mob> sources(ServerLevel level, @Nullable LivingEntity target) {
        return level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(COPY_RANGE),
                e -> e != this && e != target && e.isAlive() && distanceTo(e) <= COPY_RANGE && Illusions.copyable(e));
    }

    /** A spot on the ground round it for an illusion of {@code type}: room to stand, solid footing, a live chunk. */
    private @Nullable Vec3 spot(ServerLevel level, EntityType<?> type, int index, int of) {
        double base = random.nextDouble() * Math.PI * 2;
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = base + (index + attempt * 0.37) * (Math.PI * 2 / Math.max(1, of));
            double r = SPREAD_MIN + random.nextDouble() * (SPREAD_MAX - SPREAD_MIN);
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            BlockPos.MutableBlockPos p = BlockPos.containing(x, getY() + 1.0, z).mutable();
            for (int k = 0; k < GROUND_SEARCH; k++, p.move(Direction.DOWN)) {
                BlockPos below = p.below();
                VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
                if (floor.isEmpty()) continue;
                double y = below.getY() + floor.max(Direction.Axis.Y);
                if (!level.getFluidState(p).isEmpty() || !level.isPositionEntityTicking(p)) break;
                if (level.noCollision(type.getSpawnAABB(x, y, z))) return new Vec3(x, y, z);
                break;
            }
        }
        return null;
    }

    // ---- the counter: a blow gutters the flame ------------------------------------------------------------------

    /**
     * A blow from something (a hit, an arrow, a thrown potion) gutters its flame: its newest illusion pops. Falls, fire
     * and the like are not blows.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && (source.getEntity() != null || source.getDirectEntity() != null)) {
            gutter(level, source.getEntity() instanceof LivingEntity by ? by : null);
        }
        return hurt;
    }

    private void gutter(ServerLevel level, @Nullable LivingEntity by) {
        if (illusions.size() == 0 || !MechanicEvent.post(this, "gutter", by)) return;
        illusions.popNewest(level);
        syncIllusions();
        double y = getY() + getBbHeight() * 0.45;
        level.sendParticles(ParticleTypes.SMOKE, getX(), y, getZ(), 8, 0.2, 0.2, 0.2, 0.02);
        level.sendParticles(dust(), getX(), y, getZ(), 8, 0.3, 0.25, 0.3, 0.0);
        level.playSound(null, getX(), y, getZ(), ModSounds.PHANTASM_LANTERN_GUTTER.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** Every illusion vanishes the moment it dies. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            illusions.dispel(level);
            syncIllusions();
        }
    }

    /**
     * However it leaves the level, its illusions go with it. Unloading with its chunk, the illusions in that same chunk
     * are being saved and removed with it already; the rest are removed here.
     */
    @Override
    public void onRemoval(RemovalReason reason) {
        if (level() instanceof ServerLevel) illusions.drop(reason.shouldSave() ? blockPosition() : null);
        super.onRemoval(reason);
    }

    /** Wisps and a pop where an illusion goes. */
    private void popped(Mob m) {
        if (!(level() instanceof ServerLevel level)) return;
        double y = m.getY() + m.getBbHeight() * 0.5;
        level.sendParticles(wisp(), true, true, m.getX(), y, m.getZ(), 14, m.getBbWidth() * 0.35, m.getBbHeight() * 0.3, m.getBbWidth() * 0.35, 0.03);
        level.sendParticles(dust(), m.getX(), y, m.getZ(), 10, m.getBbWidth() * 0.4, m.getBbHeight() * 0.35, m.getBbWidth() * 0.4, 0.0);
        level.playSound(null, m.getX(), y, m.getZ(), ModSounds.PHANTASM_LANTERN_POP.get(), getSoundSource(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
    }

    // ---- drifting and keeping away -------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        getNavigation().stop();
        if (anchored) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (!core().idle()) {                                                  // it hangs still to conjure and release
            setDeltaMovement(getDeltaMovement().scale(0.5));
            return;
        }
        LivingEntity target = getTarget();
        Player near = level.getNearestPlayer(getX(), getY(), getZ(), KEEP_AWAY - 1.0, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        LivingEntity from = near != null ? near : valid(target) && target instanceof Player ? target : null;
        if (from != null) {
            double d = position().subtract(from.position()).horizontalDistance();
            if (d < KEEP_AWAY - 1.0) {
                backOff(level, from);
                return;
            }
        }
        if (valid(target) && distanceTo(target) > (target instanceof Player ? KEEP_AWAY + 3.0 : CONJURE_RANGE - 4.0)) {
            Vec3 toward = position().subtract(target.position()).horizontal();
            if (toward.lengthSqr() < 1.0E-4) toward = new Vec3(1, 0, 0);
            Vec3 at = target.position().add(toward.normalize().scale(KEEP_AWAY));
            steer(new Vec3(at.x, surfaceBelow(level, at.x, Math.max(getY(), target.getY()) + 2.0, at.z) + HOVER, at.z), CLOSE_SPEED, 0.1);
            return;
        }
        if (valid(target)) {
            hold(level);
            face(target.position().subtract(position()));
            return;
        }
        driftTick(level);
    }

    /**
     * Backs off from {@code from} to a spot {@link #KEEP_AWAY} away (straight away, or up to 105 degrees off), else a
     * short way off, open air with a clear line to it; cornered with every spot shut, it bolts straight away, rising
     * where a wall stops it.
     */
    private void backOff(ServerLevel level, LivingEntity from) {
        Vec3 away = position().subtract(from.position()).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0.0F, getYRot());
        away = away.normalize();
        double run = KEEP_AWAY - position().subtract(from.position()).horizontalDistance() + 1.0;
        for (double distance : new double[] {Math.max(2.0, run), 2.0}) {
            for (float turn : new float[] {0, 35, -35, 70, -70, 105, -105}) {
                Vec3 flat = position().add(away.yRot(turn * Mth.DEG_TO_RAD).scale(distance));
                Vec3 spot = new Vec3(flat.x, surfaceBelow(level, flat.x, getY() + 2.0, flat.z) + HOVER, flat.z);
                if (open(spot)) {
                    steer(spot, BACK_SPEED, 0.35);
                    return;
                }
            }
        }
        Vec3 v = away.scale(BACK_SPEED).add(0, horizontalCollision ? BACK_SPEED : 0.0, 0);
        setDeltaMovement(getDeltaMovement().lerp(v, 0.35));
        face(v);
    }

    /** Holds where it is, at its hover height. */
    private void hold(ServerLevel level) {
        double y = surfaceBelow(level, getX(), getY() + 1.0, getZ()) + HOVER;
        setDeltaMovement(getDeltaMovement().multiply(0.6, 0.6, 0.6).add(0, Mth.clamp((y - getY()) * 0.08, -0.04, 0.04), 0));
    }

    /** With no target it drifts from spot to spot round where it is, at its hover height. */
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
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
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
        LivingEntity target = getTarget();
        if (valid(target)) face(target.position().subtract(position()));
        else if (v.horizontalDistanceSqr() > 4.0E-4) face(v);
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---- what clients see ----------------------------------------------------------------------------------------

    /** Puts its illusions' entity ids in the synced slots. */
    private void syncIllusions() {
        List<Mob> live = illusions.live();
        for (int i = 0; i < DATA_ILLUSIONS.size(); i++) {
            int id = i < live.size() ? live.get(i).getId() : -1;
            if (getEntityData().get(DATA_ILLUSIONS.get(i)) != id) getEntityData().set(DATA_ILLUSIONS.get(i), id);
        }
    }

    /** Whether {@code e} is one of its illusions; on the server from its own list, on a client from the synced ids. */
    public boolean holds(Entity e) {
        if (!level().isClientSide()) return illusions.holds(e);
        for (EntityDataAccessor<Integer> slot : DATA_ILLUSIONS) if (getEntityData().get(slot) == e.getId()) return true;
        return false;
    }

    /** The entity ids in its synced slots, as clients see them (-1 for an empty slot). Public as a test hook. */
    public int[] syncedIllusionIds() {
        int[] ids = new int[DATA_ILLUSIONS.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = getEntityData().get(DATA_ILLUSIONS.get(i));
        return ids;
    }

    /**
     * Whether {@code e} casts a shadow: false for a lantern's illusion, true for everything else. Works on both sides
     * (clients read the lanterns' synced ids), for a renderer to skip the shadow of an illusion. Illusions are always
     * silent, so anything that isn't silent is answered without a search.
     */
    public static boolean castsShadow(Entity e) {
        if (!e.isSilent() || !(e instanceof Mob)) return true;
        for (PhantasmLantern lantern : e.level().getEntitiesOfClass(PhantasmLantern.class, e.getBoundingBox().inflate(LEASH + 1.0))) {
            if (lantern.holds(e)) return false;
        }
        return true;
    }

    private boolean crimson() {
        return "crimson_lantern".equals(look());
    }

    /** Soul-blue wisps on every look, so the tell reads the same; the look shows in its dust. */
    private ParticleOptions wisp() {
        return ParticleTypes.SOUL_FIRE_FLAME;
    }

    private DustParticleOptions dust() {
        return crimson() ? CRIMSON_DUST : WARPED_DUST;
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** The illusions it keeps. */
    public Illusions illusions() {
        return illusions;
    }

    /** Ticks until it may release again. */
    public int untilConjure() {
        return untilConjure;
    }

    /** Sets the ticks until it may release again. Public as a test hook. */
    public void setUntilConjure(int ticks) {
        untilConjure = Math.max(0, ticks);
    }

    /** Test hook: it holds still where it is; its phase machine, its conjuring and its illusions still run. */
    public void stayPut() {
        anchored = true;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.PHANTASM_LANTERN_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PHANTASM_LANTERN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PHANTASM_LANTERN_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + random.nextFloat() * 0.15F;
    }

    // ---- Illusions: copies of real mobs that look the part and can do nothing ------------------------------------

    /**
     * The illusions one mob keeps: real entities of the copied kind, made harmless and kept on a short leash. Each is a
     * fresh mob of the kind (the source's age and gear, when it copies one) with no AI of its own, so no vanilla attack,
     * griefing or conversion ever runs, steered each tick by its owner toward a target, swinging at it when close and
     * dealing nothing. It has 1 health, a ward of absorption that soaks any blow so that a blow pops it instead of
     * killing it (no death, so no kill statistics, advancements or death loot), an empty loot table, no equipment drops,
     * no experience, no loot pick-up and no sound.
     *
     * <p>None outlives its owner: they pop when the owner dies or leaves (see {@link #dispel} and {@link #drop}), when one
     * strays past its leash, and when the chunks round the owner stop ticking (the edge of the loaded world closing in,
     * before they are saved). One saved anyway (a server stopping with them about) loads as a still figure with no AI,
     * and the next owner that ticks near it removes it ({@link #sweep}), since no owner claims it after a load.
     */
    public static final class Illusions {
        /** The mark every illusion carries, and its owner's id in a second mark. */
        public static final String TAG = "teeming_illusion";
        private static final String OWNER_PREFIX = "teeming_illusion_of_";
        /** The absorption that soaks any blow, so that a blow pops an illusion rather than killing it. */
        public static final float WARD = 1024.0F;
        /** An illusion swings at its target this often when it is close. */
        public static final int SWING_EVERY = 20;
        /** How much past their two half-widths an illusion stops short of its target to swing. */
        public static final double REACH = 0.6;
        /** Radians between neighbouring illusions' flanks round their target. */
        private static final double FLANK = 1.0;
        private static final int SWEEP_EVERY = 20;
        private static final int EDGE_EVERY = 5;
        private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));
        /** Never copied: the apex mobs. */
        private static final List<EntityType<?>> APEX = List.of(EntityTypes.WITHER, EntityTypes.ENDER_DRAGON, EntityTypes.WARDEN, EntityTypes.ELDER_GUARDIAN);
        /** Never copied: mobs that hurt without their AI (a fuse a player can light, a touch, thorns, a split). */
        private static final List<EntityType<?>> HARMFUL = List.of(EntityTypes.CREEPER, EntityTypes.SLIME, EntityTypes.MAGMA_CUBE, EntityTypes.GUARDIAN,
                EntityTypes.PUFFERFISH);

        private final Mob owner;
        private final int max;
        private final double leash;
        private final Consumer<Mob> onPop;
        private final List<Mob> live = new ArrayList<>();
        private long edgeChunk = Long.MIN_VALUE;
        private int edgeTicking;

        /** {@code onPop} plays the pop (particles, sound) where an illusion goes; it is not called for a quiet removal. */
        public Illusions(Mob owner, int max, double leash, Consumer<Mob> onPop) {
            this.owner = owner;
            this.max = max;
            this.leash = leash;
            this.onPop = onPop;
        }

        /** Whether an entity is an illusion (anyone's): server side, by its mark. */
        public static boolean isIllusion(Entity e) {
            return e.entityTags().contains(TAG);
        }

        /**
         * Whether a kind of mob may be copied: a vanilla kind only (so never a Teeming mob, another lantern included,
         * nor another mod's mob, whose own code might hurt without its AI), and never a player, a boss or apex mob, a
         * mob that hurts without its AI, or anything that isn't a mob.
         */
        public static boolean copyable(EntityType<?> type) {
            if (type == EntityTypes.PLAYER || APEX.contains(type) || HARMFUL.contains(type)) return false;
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null || !id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) return false;   // vanilla kinds only
            if (type.builtInRegistryHolder().is(BOSSES)) return false;
            return type.canSerialize() && type.canSummon() && type.getCategory() != MobCategory.MISC;
        }

        /** Whether this mob may be copied: a copyable kind that walks on the ground, and not an illusion itself. */
        public static boolean copyable(Mob mob) {
            return !isIllusion(mob) && !(mob instanceof TeemingCreature) && mob instanceof PathfinderMob
                    && mob.getNavigation() instanceof GroundPathNavigation && copyable(mob.getType());
        }

        /** Its live illusions, oldest first. */
        public List<Mob> live() {
            return List.copyOf(live);
        }

        public int size() {
            return live.size();
        }

        public boolean holds(Entity e) {
            return e instanceof Mob m && live.contains(m);
        }

        /**
         * Makes an illusion of {@code type} standing at {@code at}, facing {@code yaw}: the age and the gear of
         * {@code source} when it copies one. Null when it is full or the mob can't be made.
         */
        public @Nullable Mob make(ServerLevel level, EntityType<?> type, @Nullable Mob source, Vec3 at, float yaw) {
            if (live.size() >= max) return null;
            if (!(type.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob m)) return null;
            m.snapTo(at.x, at.y, at.z, yaw, 0.0F);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
            Entity mount = m.getVehicle();                                     // no jockeys: one mob, alone
            if (mount != null) {
                m.stopRiding();
                mount.discard();
            }
            for (Entity rider : List.copyOf(m.getPassengers())) {
                rider.stopRiding();
                rider.discard();
            }
            m.setBaby(source != null && source.isBaby());
            if (source != null) {
                for (EquipmentSlot slot : EquipmentSlot.VALUES) m.setItemSlot(slot, source.getItemBySlot(slot).copy());
            }
            for (EquipmentSlot slot : EquipmentSlot.VALUES) m.setDropChance(slot, 0.0F);
            m.setCanPickUpLoot(false);
            m.setNoAi(true);
            m.setSilent(true);
            setBase(m, Attributes.MAX_HEALTH, ILLUSION_HEALTH);
            setBase(m, Attributes.ATTACK_DAMAGE, ILLUSION_DAMAGE);
            setBase(m, Attributes.MAX_ABSORPTION, WARD);
            noLoot(level, m);
            m.setHealth(ILLUSION_HEALTH);
            m.setAbsorptionAmount(WARD);
            m.skipDropExperience();
            m.setYHeadRot(yaw);
            m.setYBodyRot(yaw);
            if (!level.addFreshEntity(m)) return null;
            m.addTag(TAG);                                                     // marked once it is in: a load is the only join with a mark
            m.addTag(OWNER_PREFIX + owner.getUUID());
            live.add(m);
            return m;
        }

        private static void setBase(Mob m, Holder<Attribute> attribute, double value) {
            AttributeInstance instance = m.getAttribute(attribute);
            if (instance == null) return;
            instance.removeModifiers();
            instance.setBaseValue(value);
        }

        /** Points the illusion's death loot at the empty table, the same way {@code /data merge} would, so it saves. */
        private static void noLoot(ServerLevel level, Mob mob) {
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            mob.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.putString("DeathLootTable", "minecraft:empty");
            UUID id = mob.getUUID();
            mob.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            mob.setUUID(id);
        }

        /**
         * Each tick: forgets illusions that are gone, pops any that took a blow, strayed past the leash or stand in a
         * chunk that no longer ticks, pops them all when the ticking chunks round the owner shrink, sweeps up strays,
         * and steers the rest toward {@code target} (or back to the owner when there is none).
         */
        public void tick(ServerLevel level, @Nullable LivingEntity target) {
            if (owner.tickCount % SWEEP_EVERY == 1) sweep(level);
            if (live.isEmpty()) {
                edgeChunk = Long.MIN_VALUE;
                return;
            }
            if (owner.tickCount % EDGE_EVERY == 0 && edgeClosing(level)) {
                dispel(level);
                return;
            }
            for (Mob m : List.copyOf(live)) {
                if (m.isRemoved() || !m.isAlive() || m.level() != level) {
                    live.remove(m);
                } else if (struck(m) || m.distanceToSqr(owner) > leash * leash || !level.isPositionEntityTicking(m.blockPosition())) {
                    pop(m);
                } else {
                    steer(level, m, target);
                }
            }
        }

        /** Whether a blow landed on it: its ward is dented, it is hurt, or it flinched. */
        private static boolean struck(Mob m) {
            return m.hurtTime > 0 || m.getAbsorptionAmount() < WARD - 0.01F || m.getHealth() < ILLUSION_HEALTH - 0.01F;
        }

        /**
         * Whether fewer of the 3 x 3 chunks round the owner tick than did before, while it stayed in the same chunk:
         * the edge of the loaded world closing in. Its illusions go before their chunks are saved.
         */
        private boolean edgeClosing(ServerLevel level) {
            int cx = owner.getBlockX() >> 4, cz = owner.getBlockZ() >> 4;
            long here = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
            int ticking = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.isPositionEntityTicking(new BlockPos(((cx + dx) << 4) + 8, owner.getBlockY(), ((cz + dz) << 4) + 8))) ticking++;
                }
            }
            if (here != edgeChunk) {
                edgeChunk = here;
                edgeTicking = ticking;
                return false;
            }
            boolean closing = ticking < edgeTicking;
            edgeTicking = Math.max(edgeTicking, ticking);
            return closing;
        }

        /**
         * Walks it toward its target and swings when close, or back toward the owner when it has none. Each takes its
         * own flank of the target (spread round the side its owner is on), as a pack would, rather than queueing.
         */
        private void steer(ServerLevel level, Mob m, @Nullable LivingEntity target) {
            boolean chase = target != null && target.level() == level;
            double reach = chase ? (m.getBbWidth() + target.getBbWidth()) / 2.0 + REACH : 3.0;
            Vec3 goal = chase ? target.position() : owner.position();
            Vec3 d = goal.subtract(m.position());
            if (d.horizontalDistanceSqr() > 1.0E-6) {
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
                m.setYRot(yaw);
                m.setYHeadRot(yaw);
                m.setYBodyRot(yaw);
            }
            boolean walk = d.horizontalDistance() > reach;
            if (chase) {
                Vec3 side = owner.position().subtract(goal);
                double a = Mth.atan2(side.z, side.x) + (live.indexOf(m) - (live.size() - 1) / 2.0) * FLANK;
                Vec3 flank = goal.add(Math.cos(a) * (reach - 0.15), 0.0, Math.sin(a) * (reach - 0.15));
                Vec3 f = flank.subtract(m.position());
                // there, too close already, or walled off from its flank while in reach: it stands and swings
                walk = f.horizontalDistance() > 0.25 && d.horizontalDistance() > 0.6 * reach
                        && !(m.horizontalCollision && d.horizontalDistance() <= reach);
                if (walk) {
                    float yaw = (float) (Mth.atan2(f.z, f.x) * Mth.RAD_TO_DEG) - 90.0F;
                    m.setYRot(yaw);                                             // heading for its flank; the head still
                    m.setYBodyRot(yaw);                                         // watches the target
                }
            }
            m.setAggressive(chase);
            m.setSpeed(walk ? (float) m.getAttributeValue(Attributes.MOVEMENT_SPEED) : 0.0F);
            if (walk && m.horizontalCollision && m.onGround()) m.jumpFromGround();
            m.travel(new Vec3(m.xxa, m.yya, m.zza));
            // in reach it swings, even while it still shuffles toward its flank: the crowd round the target (the
            // other illusions, the target itself) can keep it from ever standing exactly on its flank
            if (chase && d.horizontalDistance() <= reach && (m.tickCount + m.getId()) % SWING_EVERY == 0) {
                m.swingForAttack(InteractionHand.MAIN_HAND);                    // the swing, and nothing behind it
                if (m instanceof HoglinBase) level.broadcastEntityEvent(m, (byte) 4);
            }
        }

        /** Pops its newest illusion; false if it has none. */
        public boolean popNewest(ServerLevel level) {
            if (live.isEmpty()) return false;
            pop(live.getLast());
            return true;
        }

        /** Pops one illusion: wisps, a sound, and it is gone. */
        public void pop(Mob m) {
            live.remove(m);
            onPop.accept(m);
            m.discard();
        }

        /** Pops every illusion at once; returns how many. */
        public int dispel(ServerLevel level) {
            int n = live.size();
            for (Mob m : List.copyOf(live)) pop(m);
            return n;
        }

        /**
         * Removes every illusion quietly, as the owner leaves the level; with {@code savedWith} set (it is unloading with
         * the chunk there) those in that chunk are left to go with it.
         */
        public void drop(@Nullable BlockPos savedWith) {
            for (Mob m : List.copyOf(live)) {
                boolean sameChunk = savedWith != null && m.getBlockX() >> 4 == savedWith.getX() >> 4 && m.getBlockZ() >> 4 == savedWith.getZ() >> 4;
                live.remove(m);
                if (!sameChunk && !m.isRemoved()) m.discard();
            }
        }

        /**
         * Removes illusions near the owner that no live owner claims: those loaded from a save (no owner claims them
         * after a load) and any whose owner is gone. Returns how many.
         */
        public int sweep(ServerLevel level) {
            int swept = 0;
            for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(leash + 8.0), e -> isIllusion(e) && !live.contains(e))) {
                if (claimed(level, m)) continue;
                onPop.accept(m);
                m.discard();
                swept++;
            }
            return swept;
        }

        private static boolean claimed(ServerLevel level, Mob m) {
            for (String tag : m.entityTags()) {
                if (!tag.startsWith(OWNER_PREFIX)) continue;
                try {
                    Entity owner = level.getEntity(UUID.fromString(tag.substring(OWNER_PREFIX.length())));
                    return owner instanceof PhantasmLantern o && owner.isAlive() && o.illusions().holds(m);
                } catch (IllegalArgumentException badId) {
                    return false;
                }
            }
            return false;
        }
    }
}
