package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAnimal;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Petalkin: a shy cherry-blossom sprite of the cherry groves, alone or in pairs. It leaves everyone alone until it is
 * hit; then it fights back with a peck (its attack, 3, with no warning first).
 *
 * <p>Every hit from something (a blow, a projectile) makes it blink up to 6 blocks away, to a safe spot: solid ground
 * with room to stand, never the void, a drop of more than 4 blocks, lava, fire or the inside of a wall. It prefers
 * the spot straight away from whoever hit it, then spots further round, then nearer ones. Where it stood it leaves 3
 * decoys: copies in its look that walk at the attacker and peck at the air for 100 ticks, then pop into petals. A hit
 * pops a decoy at once and does nothing else. A new blink pops the copies of the last one, and they all pop when it
 * dies. After a blink it stands for half a second re-forming before it comes back to fight.
 *
 * <p>Counter: find the real one. A decoy casts no shadow and makes no sound at all: no footsteps, no chirps. Failure:
 * hits wasted on decoys while the real one pecks.
 *
 * <p>Decoys are Petalkins with the decoy flag ({@link #FLAG_DECOY}), synced to clients with the phase. The renderer
 * scales a mob's shadow by its age scale, which nothing else reads here, so a decoy's age scale of 0 is what takes its
 * shadow away; its hitbox keeps the full size. Decoys are never saved with the world.
 */
public class Petalkin extends TeemingAnimal {
    /** IDLE, and BLINK: just arrived from a blink, re-forming for half a second before it fights. */
    public enum Step implements Phase { IDLE, BLINK }

    /** How far a blink goes, in blocks: this far when the ground allows, never further. */
    public static final double BLINK_RANGE = 6.0;
    /** The nearest spot it will settle for when nothing further is safe, in blocks. */
    public static final double BLINK_MIN = 2.0;
    public static final int DECOY_COUNT = 3;
    public static final int DECOY_TICKS = 100;
    /** Half a second re-forming where it lands. */
    public static final int BLINK_TICKS = 10;
    public static final float PECK_HIT = 1.0F;             // x attack damage
    /** The decoys stand this far round the spot it blinked from, in blocks. */
    public static final double DECOY_SPREAD = 0.7;

    /** Flag bit: this one is a decoy. */
    public static final int FLAG_DECOY = 0;

    public static final int PETAL_COLOR = 0xEAA6C2;
    private static final DustParticleOptions PETAL_DUST = new DustParticleOptions(PETAL_COLOR, 1.0F);

    /** Server: the decoys of its last blink. */
    private final List<Petalkin> decoys = new ArrayList<>();
    /** Server, decoys only: the game time it pops. */
    private long popAt;
    private @Nullable Petalkin owner;

    public Petalkin(EntityType<? extends Petalkin> type, Level level) {
        super(type, level, Step.values());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.animal("petalkin", 0.28);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ---- being hit ---------------------------------------------------------------------------------------------------

    /** A decoy pops at any hit. The real one takes the hit, then blinks away if something dealt it. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isDecoy()) {
            if (isInvulnerableTo(level, source)) return false;
            pop(level);
            return true;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) {
            Entity cause = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
            if (cause != null) blink(level, cause);
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (isDecoy()) return false;                       // a copy pecks at the air
        boolean hit = super.doHurtTarget(level, target);
        if (hit) playSound(ModSounds.PETALKIN_PECK.get(), 1.0F, 0.95F + random.nextFloat() * 0.1F);
        return hit;
    }

    // ---- the blink ---------------------------------------------------------------------------------------------------

    /**
     * Blinks to the safest spot away from {@code threat} and leaves its decoys where it stood. Does nothing when no
     * spot is safe or a pack vetoed the blink. Returns where it went, or null.
     */
    private @Nullable Vec3 blink(ServerLevel level, Entity threat) {
        Vec3 to = findBlink(level, threat.position());
        LivingEntity attacker = threat instanceof LivingEntity e ? e : null;
        if (to == null || !startPhase(Step.BLINK, BLINK_TICKS, attacker)) return null;
        Vec3 from = position();
        popDecoys(level, true);
        getNavigation().stop();
        puff(level, from);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
        face(threat.position().subtract(to));
        puff(level, to);
        level.playSound(null, from.x, from.y, from.z, ModSounds.PETALKIN_BLINK.get(), getSoundSource(), 1.0F, 1.0F);
        level.playSound(null, to.x, to.y, to.z, ModSounds.PETALKIN_BLINK.get(), getSoundSource(), 0.8F, 1.2F);
        if (MechanicEvent.post(this, "decoys", attacker)) spawnDecoys(level, from, attacker);
        return to;
    }

    /**
     * Where a blink away from {@code threat} lands: the first safe spot trying the full range straight away from it,
     * then 30 degrees further round each side in turn, then each nearer distance down to {@link #BLINK_MIN}. Null when
     * nothing is safe. Public as a test hook.
     */
    public @Nullable Vec3 findBlink(ServerLevel level, Vec3 threat) {
        double ax = getX() - threat.x, az = getZ() - threat.z;
        double away = ax * ax + az * az < 1.0E-4 ? random.nextDouble() * Math.PI * 2 : Math.atan2(az, ax);
        int headroom = Math.max(1, Mth.ceil(getBbHeight()));
        for (double distance : Blink.distances(BLINK_RANGE, BLINK_MIN)) {
            for (int offset : Blink.OFFSETS) {
                double a = away + Math.toRadians(offset);
                Vec3 spot = landing(level, getX() + Math.cos(a) * distance, getZ() + Math.sin(a) * distance, headroom);
                if (spot != null) return spot;
            }
        }
        return null;
    }

    /** The spot to stand on in the column at (x, z), near its own height, or null when that column is unsafe. */
    private @Nullable Vec3 landing(ServerLevel level, double x, double z, int headroom) {
        BlockPos base = BlockPos.containing(x, getY() + 0.01, z);
        if (!level.hasChunkAt(base) || !level.getWorldBorder().isWithinBounds(base)) return null;
        int dy = Blink.landing(d -> cell(level, base.above(d)), headroom);
        if (dy == Blink.NONE) return null;
        BlockPos floor = base.above(dy - 1);
        double top = floor.getY() + level.getBlockState(floor).getCollisionShape(level, floor).max(Direction.Axis.Y);
        Vec3 at = new Vec3(x, top, z);
        AABB box = getDimensions(Pose.STANDING).makeBoundingBox(at);
        if (!level.noCollision(this, box) || level.containsAnyLiquid(box)) return null;
        // the hitbox can straddle columns: nothing in it or under it may burn or prick
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.5, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (hazard(level.getBlockState(p)) || level.getFluidState(p).is(FluidTags.LAVA)) return null;
        }
        return at;
    }

    /** What one cell of a column is, for {@link Blink#landing}. */
    private static int cell(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY()) return Blink.VOID;
        if (level.isOutsideBuildHeight(pos)) return Blink.AIR;
        FluidState fluid = level.getFluidState(pos);
        if (fluid.is(FluidTags.LAVA)) return Blink.LAVA;
        if (!fluid.isEmpty()) return Blink.LIQUID;
        BlockState state = level.getBlockState(pos);
        if (hazard(state)) return Blink.HAZARD;
        return state.getCollisionShape(level, pos).isEmpty() ? Blink.AIR : Blink.SOLID;
    }

    /** Blocks that hurt whoever stands in or on them. */
    private static boolean hazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.LAVA);
    }

    /**
     * The safe-ground maths of a blink, with no game types so it can be checked on its own: a column is read as cells
     * by their height above the spot it starts from, and a blink lands where a cell of solid ground has open air
     * above it for the whole body.
     */
    public static final class Blink {
        public static final int AIR = 0;
        public static final int SOLID = 1;
        /** Water and other fluids: neither ground nor room to stand. */
        public static final int LIQUID = 2;
        public static final int LAVA = 3;
        /** Fire, magma, cactus, berry bushes, powder snow: never stood in or on. */
        public static final int HAZARD = 4;
        /** Below the bottom of the world. */
        public static final int VOID = 5;
        /** {@link #landing} found nowhere to stand. */
        public static final int NONE = Integer.MIN_VALUE;
        /** It lands at most this many blocks above its own height, and at most this many below. */
        public static final int MAX_RISE = 2;
        public static final int MAX_DROP = 4;
        /** The directions it tries, in degrees round from straight away from the threat, in order. */
        public static final int[] OFFSETS = {0, 30, -30, 60, -60, 90, -90, 120, -120, 150, -150, 180};

        private Blink() {}

        /** The heights it tries in a column, nearest its own first: 0, -1, 1, -2, 2, -3, -4. */
        public static int[] heights() {
            int[] out = new int[MAX_RISE + MAX_DROP + 1];
            int n = 0;
            out[n++] = 0;
            for (int d = 1; d <= Math.max(MAX_RISE, MAX_DROP); d++) {
                if (d <= MAX_DROP) out[n++] = -d;
                if (d <= MAX_RISE) out[n++] = d;
            }
            return out;
        }

        /** True when it can stand with its feet in cell {@code dy}: solid ground under it and air for its whole height. */
        public static boolean standable(IntUnaryOperator cell, int dy, int headroom) {
            if (cell.applyAsInt(dy - 1) != SOLID) return false;
            for (int h = 0; h < headroom; h++) if (cell.applyAsInt(dy + h) != AIR) return false;
            return true;
        }

        /** The height of the feet cell to land in, nearest its own first, or {@link #NONE}. */
        public static int landing(IntUnaryOperator cell, int headroom) {
            for (int dy : heights()) if (standable(cell, dy, headroom)) return dy;
            return NONE;
        }

        /** The distances it tries, furthest first: {@code range}, then a block nearer each time, down to {@code min}. */
        public static double[] distances(double range, double min) {
            int n = (int) Math.floor(range - min) + 1;
            double[] out = new double[Math.max(1, n)];
            for (int i = 0; i < out.length; i++) out[i] = range - i;
            return out;
        }
    }

    // ---- decoys ------------------------------------------------------------------------------------------------------

    private void spawnDecoys(ServerLevel level, Vec3 at, @Nullable LivingEntity target) {
        @SuppressWarnings("unchecked")
        EntityType<Petalkin> type = (EntityType<Petalkin>) getType();
        double turn = random.nextDouble() * Math.PI * 2;
        String look = look().isEmpty() ? "base" : look();
        for (int i = 0; i < DECOY_COUNT; i++) {
            Petalkin decoy = type.create(level, EntitySpawnReason.TRIGGERED);
            if (decoy == null) continue;
            double a = turn + i * (Math.PI * 2 / DECOY_COUNT);
            Vec3 spot = at.add(Math.cos(a) * DECOY_SPREAD, 0.0, Math.sin(a) * DECOY_SPREAD);
            if (!level.noCollision(decoy, decoy.getDimensions(Pose.STANDING).makeBoundingBox(spot))) spot = at;
            float yaw = getYRot() + (i - 1) * 40.0F;
            decoy.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
            decoy.yBodyRot = yaw;
            decoy.yHeadRot = yaw;
            decoy.core().setLook(look, true);
            decoy.core().setFlag(FLAG_DECOY, true);
            decoy.refreshDimensions();
            decoy.owner = this;
            decoy.popAt = level.getGameTime() + DECOY_TICKS;
            if (target != null && target.isAlive()) decoy.setTarget(target);
            if (level.addFreshEntity(decoy)) decoys.add(decoy);
        }
    }

    /** A decoy goes: a burst of petals and a soft pop. */
    private void pop(ServerLevel level) {
        if (isRemoved()) return;
        double y = getY() + getBbHeight() * 0.5;
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), y, getZ(), 14, 0.3, 0.35, 0.3, 0.02);
        level.sendParticles(PETAL_DUST, getX(), y, getZ(), 10, 0.25, 0.3, 0.25, 0.0);
        level.sendParticles(ParticleTypes.POOF, getX(), y, getZ(), 4, 0.15, 0.15, 0.15, 0.01);
        level.playSound(null, getX(), y, getZ(), ModSounds.PETALKIN_POP.get(), getSoundSource(), 0.9F, 0.95F + random.nextFloat() * 0.1F);
        discard();
    }

    /** Its decoys go: popping ({@code loud}) or quietly (the chunk unloads). */
    private void popDecoys(ServerLevel level, boolean loud) {
        for (Petalkin d : decoys) {
            if (loud) d.pop(level);
            else d.discard();
        }
        decoys.clear();
    }

    /** The petals of a blink, where it leaves and where it lands. */
    private void puff(ServerLevel level, Vec3 at) {
        double y = at.y + getBbHeight() * 0.5;
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, true, true, at.x, y, at.z, 24, 0.35, 0.45, 0.35, 0.03);
        level.sendParticles(PETAL_DUST, true, true, at.x, y, at.z, 16, 0.3, 0.4, 0.3, 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isDecoy()) return;
        // a copy lasts its time, and no longer than the one it copies
        if (level.getGameTime() >= popAt || owner == null || !owner.isAlive() || owner.isRemoved()) pop(level);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().in(Step.BLINK)) {
            getNavigation().stop();
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            if (tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + getBbHeight() * 0.7, getZ(), 2, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) popDecoys(level, true);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!isDecoy() && level() instanceof ServerLevel level) popDecoys(level, reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED);
        super.remove(reason);
    }

    /** Decoys are illusions of the moment: never written to the world. */
    @Override
    public boolean shouldBeSaved() {
        return !isDecoy() && super.shouldBeSaved();
    }

    /** True for a decoy. Both sides. */
    public boolean isDecoy() {
        return core() != null && core().flag(FLAG_DECOY);       // null while the Entity constructor runs
    }

    /** The renderer scales the shadow by this and nothing else here reads it: a decoy's is 0, so it casts none. */
    @Override
    public float getAgeScale() {
        return isDecoy() ? 0.0F : 1.0F;
    }

    /** The full size for every Petalkin, decoys included (the age scale only takes the shadow away). */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return getType().getDimensions();
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---- test hooks ---------------------------------------------------------------------------------------------------

    /** The decoys of its last blink still standing. Public as a test hook. */
    public List<Petalkin> decoys() {
        decoys.removeIf(Entity::isRemoved);
        return List.copyOf(decoys);
    }

    /** Ticks until a decoy pops (0 for the real one). Public as a test hook. */
    public int decoyTicksLeft() {
        return isDecoy() ? (int) Math.max(0L, popAt - level().getGameTime()) : 0;
    }

    /** False for a decoy, which makes no sound as it walks. Public as a test hook. */
    public boolean makesFootsteps() {
        return !isDecoy();
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (makesFootsteps()) super.playStepSound(pos, state);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return isDecoy() ? null : ModSounds.PETALKIN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PETALKIN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PETALKIN_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.1F + random.nextFloat() * 0.15F;
    }
}
