package com.panda.teeming.mob.nether;

import com.mojang.serialization.Codec;
import com.panda.teeming.TeemingConfig;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.platform.Services;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gloomweaver: a broad, low, eight-legged weaver of the warped forests whose spinnerets spill warped vines. With a
 * target 4 to 12 blocks away in sight it rears for 20 ticks (tell: its spinnerets whirl and glow, vines twist round
 * them with a rustle), then raises a wall of vines 5 wide and 3 tall across the path between them, 1.5 blocks short of
 * its target. A wall stands for 200 ticks and withers visibly through its last 20; it keeps at most 2 at once. Walls
 * block movement, sight and shots. Up close it bites for its attack damage.
 *
 * <p>Counter: an axe swing or fire on any part of a wall brings the whole wall down at once: a player swinging an axe
 * at it, striking it with a flint and steel or a fire charge, or swinging at it while burning; fire or lava next to any
 * of its blocks; anything burning (a lit arrow, a burning mob, a fire charge in flight) touching it. Every other blow
 * only scratches it: a block mined or blown out of a wall grows back at once and drops nothing. A wall brought down
 * snaps the weaver's threads back into its spinnerets: for 100 ticks it can't spin at all, its spinnerets dark.
 *
 * <p>The walls are real blocks (warped wart, or nether wart for the Crimson Weaver), so that they truly block paths,
 * sight and projectiles for every mob and player. They are kept by {@link Walls}, a self-contained section: placed into
 * air only, never into anything living, only where the server's temporary-blocks setting and the mob griefing rule
 * allow; every block recorded and removed when its wall ends, when the weaver dies or leaves the level, and, for any
 * still standing after a save, on its first tick after loading. It never leaves a block behind.
 */
public class Gloomweaver extends TeemingMonster {
    /** At rest or walking, the spin (tell), the weave as the wall goes up, its threads snapped. */
    public enum Step implements Phase { IDLE, TELL, WEAVE, SNAPPED }

    /** 1 s of spinning before the wall goes up. */
    public static final int TELL_TICKS = 20;
    /** A wall is this many blocks wide, across the path, and this many tall. */
    public static final int WALL_WIDTH = 5;
    public static final int WALL_HEIGHT = 3;
    /** A wall stands this long, and withers visibly through the last of it. */
    public static final int WALL_TICKS = 200;
    public static final int WITHER_TICKS = 20;
    /** It keeps at most this many walls standing at once. */
    public static final int MAX_WALLS = 2;
    /** It spins at targets this far away across the ground; nearer ones it walks up to and bites. */
    public static final double SPIN_MIN = 4.0;
    public static final double SPIN_RANGE = 12.0;
    /** A wall goes up this far short of its target, and never nearer the weaver than this. */
    public static final double WALL_SHORT = 1.5;
    public static final double WALL_NEAR = 2.0;
    /** The weave: it holds its pose this long while the wall goes up. */
    public static final int WEAVE_TICKS = 10;
    /** Ticks from one wall to the next spin, at the soonest; a fresh weaver may spin this soon. */
    public static final int SPIN_COOLDOWN = 100;
    public static final int FIRST_SPIN = 20;
    /** A wall brought down by an axe or fire snaps its threads: this long without spinning. */
    public static final int SNAPPED_TICKS = 100;
    /** Anything burning this close to a wall's blocks sets it alight. */
    public static final double FIRE_TOUCH = 0.3;
    /** Players this close to a wall's middle are checked for a swing at it. */
    private static final double SWING_SCAN = 8.0;

    private static final int GLOW_REST = 0, GLOW_SPIN = 1, GLOW_DARK = 2;
    private static final String CRIMSON = "crimson_weaver";

    private final Walls walls = new Walls(this, MAX_WALLS, WITHER_TICKS);
    private int cooldown = FIRST_SPIN;
    /** The phase the glow stage was last set for. */
    private int stagedPhase = -1;

    public Gloomweaver(EntityType<? extends Gloomweaver> type, Level level) {
        super(type, level, Step.values());
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("gloomweaver", 0.26);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return canBite() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return canBite() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // it remembers a target it walled off for as long as the wall stands
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true).setUnseenMemoryTicks(WALL_TICKS));
    }

    /** It walks up and bites when it isn't spinning or weaving; with its threads snapped it still bites. */
    private boolean canBite() {
        return core().idle() || core().in(Step.SNAPPED);
    }

    @Override
    public int visualStages() {
        return 3;
    }

    /** Spinning and weaving it is planted: only falling moves it. */
    @Override
    public Pin pin() {
        return core().in(Step.TELL) || core().in(Step.WEAVE) ? Pin.ROOTED : Pin.FREE;
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().in(Step.WEAVE)) {
            getNavigation().stop();
        } else if (core().in(Step.SNAPPED)) {
            snappedTick(level);
        } else if (core().idle()) {
            LivingEntity target = getTarget();
            if (canSpin(target)) spin(target);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        walls.tick(level);
        for (Walls.Wall wall : walls.live()) {
            if (brokenByCounter(level, wall)) bringDown(level, wall);
        }
        if (phase().ordinal() != stagedPhase) {               // the glow follows each change of phase
            stagedPhase = phase().ordinal();
            core().setStage(glowStage());
        }
    }

    /** Dim at rest; bright through the spin and the weave; dark while its threads are snapped. */
    private int glowStage() {
        if (core().in(Step.TELL) || core().in(Step.WEAVE)) return GLOW_SPIN;
        if (core().in(Step.SNAPPED)) return GLOW_DARK;
        return GLOW_REST;
    }

    /**
     * True when it may start spinning a wall at {@code target} now: idle (not spinning, weaving or snapped), ready, on
     * the ground, fewer than 2 walls up, temporary blocks and the mob griefing rule both allowed, and its target 4 to 12 blocks across the ground and
     * in sight.
     */
    public boolean canSpin(@Nullable LivingEntity target) {
        if (!core().idle() || !valid(target) || cooldown > 0 || !onGround() || !mayWeave()) return false;
        double d = position().subtract(target.position()).horizontalDistance();
        return d >= SPIN_MIN && d <= SPIN_RANGE && getSensing().hasLineOfSight(target);
    }

    /**
     * Whether it may raise another wall at all: fewer than 2 up, the server's temporary-blocks setting on, and the mob
     * griefing rule letting mobs change blocks. Otherwise it never spins and only bites.
     */
    private boolean mayWeave() {
        return level() instanceof ServerLevel level && walls.size() < MAX_WALLS && TeemingConfig.temporaryBlocks()
                && Services.PLATFORM.canGrief(level, this);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (isNoAi() || !(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (valid(target) && raiseWall(level, target) != null) weave(target);
            else rest();
        } else {
            rest();
        }
    }

    private void rest() {
        core().end();
        cooldown = SPIN_COOLDOWN;
    }

    // ---- the tell: the spin ----------------------------------------------------------------------------------------

    /**
     * Starts the spin at {@code target}: 20 ticks, then the wall. True if it started. Ignores the cooldown and the
     * range, but never starts with 2 walls up, with temporary blocks turned off, or with the mob griefing rule off. Public as a test hook.
     */
    public boolean spin(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle() || !mayWeave()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        getNavigation().stop();
        if (target != null) face(target.position().subtract(position()));
        Telegraph.particles(spore(), 14, 0.5, 0.9).withSpeed(0.01).withSound(ModSounds.GLOOMWEAVER_TELL.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    /** Reared, facing its target: spores and vine shreds whirl round its spinnerets, and they rustle again half way. */
    private void tellTick(ServerLevel level) {
        getNavigation().stop();
        LivingEntity target = core().target();
        if (target != null) face(target.position().subtract(position()));
        int t = core().elapsed();
        Vec3 tail = spinnerets();
        double a = t * 0.9;
        for (int arm = 0; arm < 2; arm++) {
            double r = 0.45, ang = a + arm * Math.PI;
            level.sendParticles(spore(), tail.x + Math.cos(ang) * r, tail.y + 0.15, tail.z + Math.sin(ang) * r, 1, 0.02, 0.05, 0.02, 0.0);
        }
        if (t % 3 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wallState()), tail.x, tail.y, tail.z, 2, 0.2, 0.15, 0.2, 0.05);
        if (t == TELL_TICKS / 2) playSound(ModSounds.GLOOMWEAVER_TELL.get(), 0.8F, 1.2F);
    }

    /** Where its spinnerets are in the world: high on the tail, behind its middle. */
    private Vec3 spinnerets() {
        float r = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(r), 0.0, -Mth.cos(r)).scale(getBbWidth() * 0.75);
        return position().add(back).add(0.0, getBbHeight() * 0.85, 0.0);
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private ParticleOptions spore() {
        return CRIMSON.equals(look()) ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.WARPED_SPORE;
    }

    /** What its walls are made of: warped wart, or nether wart for the Crimson Weaver. */
    public BlockState wallState() {
        return (CRIMSON.equals(look()) ? Blocks.NETHER_WART_BLOCK : Blocks.WARPED_WART_BLOCK).defaultBlockState();
    }

    // ---- the wall ------------------------------------------------------------------------------------------------

    /**
     * Raises a wall across the path to {@code target} now (see {@link #wallCells}): every block of it that can go into
     * air. Null if it may not (2 walls up, temporary blocks or the mob griefing rule off, the step cancelled) or no block could go in.
     * Public as a test hook.
     */
    public Walls.@Nullable Wall raiseWall(ServerLevel level, LivingEntity target) {
        if (!mayWeave() || !MechanicEvent.post(this, "wall", target)) return null;
        Walls.Wall wall = walls.raise(level, wallCells(level, position(), target.position()), wallState(), WALL_TICKS);
        if (wall == null) return null;
        level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.GLOOMWEAVER_WALL.get(), getSoundSource(), 1.2F, 1.0F);
        for (BlockPos pos : wall.cells()) {
            level.sendParticles(spore(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.01);
        }
        Vec3 tail = spinnerets();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wallState()), tail.x, tail.y, tail.z, 12, 0.25, 0.2, 0.25, 0.15);
        return wall;
    }

    /** The follow-through: it holds its pose while the wall goes up. */
    private void weave(LivingEntity target) {
        if (!startPhase(Step.WEAVE, WEAVE_TICKS, target)) rest();
        getNavigation().stop();
    }

    /**
     * Where a wall from a weaver at {@code from} to a target at {@code to} goes: a straight row of {@link #WALL_WIDTH}
     * columns across the path (along the ground axis that crosses it most squarely), centred on the point 1.5 blocks
     * short of the target (and at least 2 from the weaver), each column {@link #WALL_HEIGHT} tall from the ground near
     * the target's feet. Only the spots; which of them may take a block is up to {@link Walls#raise}.
     */
    public static List<BlockPos> wallCells(Level level, Vec3 from, Vec3 to) {
        List<BlockPos> cells = new ArrayList<>();
        Vec3 d = to.subtract(from).multiply(1, 0, 1);
        double dist = d.length();
        if (dist < 1.0E-3) return cells;
        Vec3 centre = from.add(d.scale(Math.max(WALL_NEAR, dist - WALL_SHORT) / dist));
        boolean alongZ = Math.abs(d.x) >= Math.abs(d.z);                 // heading along X: the wall runs along Z
        int cx = Mth.floor(centre.x), cz = Mth.floor(centre.z), feet = Mth.floor(to.y + 1.0E-3);
        for (int i = -WALL_WIDTH / 2; i <= WALL_WIDTH / 2; i++) {
            int x = alongZ ? cx : cx + i, z = alongZ ? cz + i : cz;
            Integer base = groundAt(level, x, feet, z);
            if (base == null) continue;
            for (int h = 0; h < WALL_HEIGHT; h++) cells.add(new BlockPos(x, base + h, z));
        }
        return cells;
    }

    /** The lowest open spot standing on something in the column, at the target's feet or within a step of it. */
    private static @Nullable Integer groundAt(Level level, int x, int feet, int z) {
        for (int dy : new int[] {0, -1, 1, -2}) {
            BlockPos at = new BlockPos(x, feet + dy, z);
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                    && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) return at.getY();
        }
        return null;
    }

    /** Its walls (server side). */
    public Walls walls() {
        return walls;
    }

    // ---- the counter: an axe or fire brings a wall down -------------------------------------------------------------

    /**
     * Whether an axe or fire is on this wall now: fire or lava next to any of its blocks; anything burning (or a fire
     * charge in flight) touching it; a player swinging at one of its blocks with an axe, with a flint and steel or a
     * fire charge, or while burning.
     */
    private boolean brokenByCounter(ServerLevel level, Walls.Wall wall) {
        if (wall.cells().isEmpty()) return false;
        for (BlockPos pos : wall.cells()) {
            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                BlockState state = level.getBlockState(n);
                if (state.is(BlockTags.FIRE) || level.getFluidState(n).is(FluidTags.LAVA)) return true;
            }
        }
        AABB box = wall.bounds().inflate(FIRE_TOUCH);
        if (!level.getEntities((Entity) null, box, e -> e.isAlive() && (e.isOnFire() || e instanceof Fireball) && touches(wall, e)).isEmpty()) return true;
        Vec3 middle = wall.bounds().getCenter();
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(middle, middle).inflate(SWING_SCAN), p -> !p.isSpectator() && p.isSwinging())) {
            ItemStack held = p.getItemInHand(p.getCurrentSwing() != null ? p.getCurrentSwing().hand() : net.minecraft.world.InteractionHand.MAIN_HAND);
            boolean cuts = held.is(ItemTags.AXES) || held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE) || p.isOnFire();
            if (!cuts) continue;
            HitResult hit = p.pick(p.blockInteractionRange(), 1.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK && wall.holds(((BlockHitResult) hit).getBlockPos())) return true;
        }
        return false;
    }

    /** Whether an entity's hitbox comes within reach of one of the wall's blocks. */
    private static boolean touches(Walls.Wall wall, Entity e) {
        AABB b = e.getBoundingBox().inflate(FIRE_TOUCH);
        for (BlockPos pos : wall.cells()) if (b.intersects(new AABB(pos))) return true;
        return false;
    }

    /** The whole wall comes down at once, and its threads snap back into the weaver. */
    private void bringDown(ServerLevel level, Walls.Wall wall) {
        if (!MechanicEvent.post(this, "wall_break", null)) return;
        Vec3 middle = wall.bounds().getCenter();
        walls.shatter(level, wall);
        level.playSound(null, middle.x, middle.y, middle.z, ModSounds.GLOOMWEAVER_SNAP.get(), SoundSource.HOSTILE, 1.4F, 1.0F);
        level.sendParticles(ParticleTypes.SMOKE, middle.x, middle.y, middle.z, 20, 1.0, 0.8, 1.0, 0.02);
        snap(level);
    }

    /**
     * Its threads snap: no spinning for 100 ticks, its spinnerets dark, a spin under way cut short. True if it took.
     * Public as a test hook.
     */
    public boolean snap(ServerLevel level) {
        if (!startPhase(Step.SNAPPED, SNAPPED_TICKS, getTarget())) return false;
        Vec3 tail = spinnerets();
        level.sendParticles(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 10, 0.2, 0.15, 0.2, 0.03);
        return true;
    }

    /** Smoke curls off its snapped spinnerets now and then. */
    private void snappedTick(ServerLevel level) {
        if (core().elapsed() % 8 == 0) {
            Vec3 tail = spinnerets();
            level.sendParticles(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 1, 0.08, 0.05, 0.08, 0.01);
        }
    }

    // ---- leaving the level: every wall goes with it -----------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) walls.clear(level, true);
    }

    /**
     * However it leaves the level, its walls go: dead or discarded, every block; unloading, those in loaded chunks (the
     * rest are in the save it was just written to, and go on its first tick after loading).
     */
    @Override
    public void onRemoval(RemovalReason reason) {
        if (level() instanceof ServerLevel level) walls.clear(level, reason.shouldDestroy());
        super.onRemoval(reason);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SpinCooldown", cooldown);
        walls.save(output);
    }

    /** A reload ends its walls: their blocks are taken down on its first tick. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cooldown = Math.max(0, input.getIntOr("SpinCooldown", 0));
        walls.load(input);
    }

    // ---- the bite ----------------------------------------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) level.playSound(null, getX(), getY(), getZ(), ModSounds.GLOOMWEAVER_BITE.get(), getSoundSource(), 1.0F, 1.0F);
        return hit;
    }

    // ---- small rules and test hooks ----------------------------------------------------------------------------------

    private static boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Gloomweaver) && super.canAttack(target);
    }

    /** Ticks until it may spin again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it spin again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.GLOOMWEAVER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GLOOMWEAVER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GLOOMWEAVER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.GLOOMWEAVER_STEP.get(), 0.35F, 1.0F);
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.15F;
    }

    // ---- temporary walls ---------------------------------------------------------------------------------------------

    /**
     * Temporary blocks one mob keeps: walls of real blocks that stand for a set time and are always taken down again.
     * Self-contained, so it can serve any mob that builds.
     *
     * <ul>
     *   <li>Placed into air only: a spot holding anything (a block, a plant, a fluid) or overlapping anything that
     *       blocks building (a mob, a player) is left as it is; a spot in an unloaded chunk too.</li>
     *   <li>Every block placed is recorded, with its wall. A wall ends when its time runs out (withering visibly, as
     *       spreading cracks and falling crumbs, through its last ticks), when it is shattered, or when its owner dies
     *       or leaves the level; its blocks are then removed, and only spots still holding the wall's own block are
     *       touched, so nothing that came there since is ever removed.</li>
     *   <li>A block knocked out of a standing wall (mined, blown up, popped) grows back at once once its spot is clear,
     *       and the fresh drop it left is taken away, so a wall gives nothing. A spot something else has filled since
     *       is let go.</li>
     *   <li>A block a piston moves stays part of its wall: blocks arriving next to the wall's spots in a piston's
     *       moving block are taken into the record, so they are removed with it wherever they end up.</li>
     *   <li>Saved with the owner, with the dimension they stand in. An owner loaded from a save takes down every
     *       recorded block still standing on its first tick (a reload ends its walls); spots in chunks not loaded yet
     *       wait, saved, until they are. Loaded in another dimension (through a portal), it leaves them be: they were
     *       taken down as it left.</li>
     * </ul>
     *
     * The owner asks the mob griefing rule before it raises a wall; this only places and removes.
     */
    public static final class Walls {
        /** One wall: its block, the spots it holds, when it went up and how long it stands. */
        public static final class Wall {
            private final BlockState state;
            private final List<BlockPos> cells;
            private final long born;
            private final int life;

            Wall(BlockState state, List<BlockPos> cells, long born, int life) {
                this.state = state;
                this.cells = cells;
                this.born = born;
                this.life = life;
            }

            /** The spots it holds (its standing blocks, and any knocked out and waiting to grow back). */
            public List<BlockPos> cells() {
                return List.copyOf(cells);
            }

            public BlockState state() {
                return state;
            }

            public boolean holds(BlockPos pos) {
                return cells.contains(pos);
            }

            /** Ticks since it went up, at game time {@code now}. */
            public long age(long now) {
                return now - born;
            }

            public int life() {
                return life;
            }

            /** The box round all its spots. */
            public AABB bounds() {
                AABB box = null;
                for (BlockPos pos : cells) box = box == null ? new AABB(pos) : box.minmax(new AABB(pos));
                return box == null ? new AABB(BlockPos.ZERO) : box;
            }
        }

        private static final Codec<List<Long>> CELLS = Codec.LONG.listOf();
        private static final Codec<List<String>> BLOCKS = Codec.STRING.listOf();
        /** A fresh drop this young, of the wall's block, lying in a knocked-out spot is the drop it left. */
        private static final int FRESH_DROP = 4;

        private final Mob owner;
        private final int max;
        private final int wither;
        private final List<Wall> live = new ArrayList<>();
        /** Blocks recorded in a save, with their block, taken down on the first tick after loading. */
        private final List<BlockPos> loaded = new ArrayList<>();
        private final List<Block> loadedBlocks = new ArrayList<>();
        /** The dimension those were recorded in: an owner that loads elsewhere (through a portal) leaves them be. */
        private @Nullable String loadedLevel;

        /** {@code max} walls at once; each withers visibly through its last {@code wither} ticks. */
        public Walls(Mob owner, int max, int wither) {
            this.owner = owner;
            this.max = max;
            this.wither = wither;
        }

        public List<Wall> live() {
            return List.copyOf(live);
        }

        public int size() {
            return live.size();
        }

        /** Whether {@code wall} is in its last ticks at game time {@code now}, withering. */
        public boolean withering(Wall wall, long now) {
            long age = wall.age(now);
            return age >= wall.life - wither && age < wall.life;
        }

        /**
         * Puts {@code state} into every spot of {@code cells} that holds air, in a loaded chunk, with nothing that
         * blocks building in it, and records the result as one wall standing {@code life} ticks. Null when it is full
         * or no block went in.
         */
        public @Nullable Wall raise(ServerLevel level, List<BlockPos> cells, BlockState state, int life) {
            if (live.size() >= max) return null;
            List<BlockPos> placed = new ArrayList<>();
            for (BlockPos pos : cells) {
                if (placed.contains(pos) || !open(level, pos)) continue;
                if (level.setBlock(pos, state, Block.UPDATE_ALL)) placed.add(pos.immutable());
            }
            if (placed.isEmpty()) return null;
            Wall wall = new Wall(state, placed, level.getGameTime(), life);
            live.add(wall);
            return wall;
        }

        /** Air, loaded, and nothing in the way that blocks building. */
        private static boolean open(ServerLevel level, BlockPos pos) {
            return level.isLoaded(pos) && level.getBlockState(pos).isAir() && unobstructed(level, pos);
        }

        private static boolean unobstructed(ServerLevel level, BlockPos pos) {
            return level.getEntities((Entity) null, new AABB(pos), e -> e.blocksBuilding && !e.isRemoved() && !e.isSpectator()).isEmpty();
        }

        /** Each tick: loaded blocks come down, walls past their time end, the last ticks wither, gaps grow back. */
        public void tick(ServerLevel level) {
            if (!loaded.isEmpty()) takeDownLoaded(level);
            long now = level.getGameTime();
            for (Wall wall : List.copyOf(live)) {
                long age = wall.age(now);
                if (age >= wall.life) {
                    end(level, wall);
                    continue;
                }
                mend(level, wall);
                if (age >= wall.life - wither) witherTick(level, wall, age);
            }
        }

        /** The blocks of its walls through the last ticks: cracks spread over them, crumbs fall, a dry rustle starts it. */
        private void witherTick(ServerLevel level, Wall wall, long age) {
            long into = age - (wall.life - wither);
            int crack = (int) Math.min(9, into * 10 / Math.max(1, wither));
            for (BlockPos pos : wall.cells) {
                if (!level.getBlockState(pos).is(wall.state.getBlock())) continue;
                level.destroyBlockProgress(breaker(pos), pos, crack);
                if (level.getRandom().nextInt(4) == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, wall.state), pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5,
                            1, 0.3, 0.05, 0.3, 0.0);
                }
            }
            if (into == 0) {
                Vec3 c = wall.bounds().getCenter();
                level.playSound(null, c.x, c.y, c.z, ModSounds.GLOOMWEAVER_WITHER.get(), SoundSource.HOSTILE, 0.8F, 1.0F);
            }
        }

        /** A crack overlay id per spot and owner, kept clear of entity ids (those are never negative). */
        private int breaker(BlockPos pos) {
            return (int) (pos.asLong() * 31 + owner.getId()) | Integer.MIN_VALUE;
        }

        /**
         * Checks every spot: a block still standing stays; one in a piston's move is followed; one knocked out leaves
         * its fresh drop taken away and grows back once its spot is clear; a spot something else has filled is let go.
         */
        private void mend(ServerLevel level, Wall wall) {
            Block block = wall.state.getBlock();
            for (BlockPos pos : List.copyOf(wall.cells)) {
                if (!level.isLoaded(pos)) continue;
                BlockState now = level.getBlockState(pos);
                if (now.is(block)) continue;
                if (followPiston(level, wall, pos) || now.is(Blocks.MOVING_PISTON)) continue;
                if (now.isAir()) {
                    eatDrops(level, pos, block);
                    if (unobstructed(level, pos) && level.setBlock(pos, wall.state, Block.UPDATE_ALL)) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wall.state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                4, 0.25, 0.25, 0.25, 0.02);
                    }
                } else {
                    wall.cells.remove(pos);                                     // something else is there now: never replaced
                }
            }
        }

        /**
         * A piston is moving blocks round {@code pos}: any of the wall's block arriving next to it (or in it) in a
         * piston's moving block joins the wall's record. True if a piston is at work there.
         */
        private static boolean followPiston(ServerLevel level, Wall wall, BlockPos pos) {
            boolean moving = false;
            for (int i = -1; i < Direction.values().length; i++) {
                BlockPos n = i < 0 ? pos : pos.relative(Direction.values()[i]);
                if (!level.getBlockState(n).is(Blocks.MOVING_PISTON)) continue;
                moving = true;
                if (level.getBlockEntity(n) instanceof PistonMovingBlockEntity m && m.getMovedState().is(wall.state.getBlock()) && !wall.cells.contains(n)) {
                    wall.cells.add(n.immutable());
                }
            }
            return moving;
        }

        /** Takes away the drop a knocked-out block just left in its spot. */
        private static void eatDrops(ServerLevel level, BlockPos pos, Block block) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.75),
                    e -> e.getAge() <= FRESH_DROP && e.getItem().is(block.asItem()))) {
                item.discard();
            }
        }

        /** Its time is up: the blocks crumble away. */
        private void end(ServerLevel level, Wall wall) {
            live.remove(wall);
            for (BlockPos pos : wall.cells) {
                if (takeDown(level, pos, wall.state.getBlock()) && level.getRandom().nextInt(2) == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wall.state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            3, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }

        /** The whole wall at once, broken apart: every block bursts (with its break sound), dropping nothing. */
        public void shatter(ServerLevel level, Wall wall) {
            live.remove(wall);
            Block block = wall.state.getBlock();
            for (BlockPos pos : wall.cells) {
                level.destroyBlockProgress(breaker(pos), pos, -1);
                BlockState now = level.getBlockState(pos);
                if (now.is(block)) level.destroyBlock(pos, false);
                else takeDown(level, pos, block);
            }
        }

        /**
         * Every wall comes down now, and any blocks still waiting from a save: all of them with {@code all}, else only
         * those in loaded chunks (the rest stay recorded).
         */
        public void clear(ServerLevel level, boolean all) {
            for (Wall wall : List.copyOf(live)) {
                List<BlockPos> left = new ArrayList<>();
                for (BlockPos pos : wall.cells) {
                    if (all || level.isLoaded(pos)) takeDown(level, pos, wall.state.getBlock());
                    else left.add(pos);
                }
                if (left.isEmpty()) live.remove(wall);
                else {
                    wall.cells.clear();
                    wall.cells.addAll(left);
                }
            }
            if (all) {
                for (int i = 0; i < loaded.size(); i++) takeDown(level, loaded.get(i), loadedBlocks.get(i));
                loaded.clear();
                loadedBlocks.clear();
            } else {
                takeDownLoaded(level);
            }
        }

        /** Takes down the blocks recorded in a save that stand in loaded chunks; the rest wait. */
        private void takeDownLoaded(ServerLevel level) {
            if (loadedLevel != null && !loadedLevel.equals(level.dimension().identifier().toString())) {
                loaded.clear();                                                 // recorded in another dimension, and
                loadedBlocks.clear();                                           // taken down there as it left
                return;
            }
            for (int i = loaded.size() - 1; i >= 0; i--) {
                if (!level.isLoaded(loaded.get(i))) continue;
                takeDown(level, loaded.get(i), loadedBlocks.get(i));
                loaded.remove(i);
                loadedBlocks.remove(i);
            }
        }

        /**
         * Removes the block at {@code pos} if it is still the wall's own (or that block mid-move in a piston); anything
         * else there is left alone. True if it removed one.
         */
        private boolean takeDown(ServerLevel level, BlockPos pos, Block block) {
            BlockState now = level.getBlockState(pos);
            boolean ours = now.is(block) || now.is(Blocks.MOVING_PISTON)
                    && level.getBlockEntity(pos) instanceof PistonMovingBlockEntity m && m.getMovedState().is(block);
            level.destroyBlockProgress(breaker(pos), pos, -1);
            if (!ours) return false;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }

        /** Every recorded spot, with its block, standing or waiting from an earlier load. */
        public void save(ValueOutput output) {
            List<Long> cells = new ArrayList<>();
            List<String> blocks = new ArrayList<>();
            for (Wall wall : live) {
                String id = BuiltInRegistries.BLOCK.getKey(wall.state.getBlock()).toString();
                for (BlockPos pos : wall.cells) {
                    cells.add(pos.asLong());
                    blocks.add(id);
                }
            }
            for (int i = 0; i < loaded.size(); i++) {
                cells.add(loaded.get(i).asLong());
                blocks.add(BuiltInRegistries.BLOCK.getKey(loadedBlocks.get(i)).toString());
            }
            if (cells.isEmpty()) return;
            output.putString("WallLevel", owner.level().dimension().identifier().toString());
            output.store("WallCells", CELLS, cells);
            output.store("WallBlocks", BLOCKS, blocks);
        }

        /** Recorded spots from a save: they come down on the first tick. */
        public void load(ValueInput input) {
            List<Long> cells = input.read("WallCells", CELLS).orElse(List.of());
            List<String> blocks = input.read("WallBlocks", BLOCKS).orElse(List.of());
            String where = input.getStringOr("WallLevel", "");
            loadedLevel = where.isEmpty() ? null : where;
            for (int i = 0; i < cells.size() && i < blocks.size(); i++) {
                Identifier id = Identifier.tryParse(blocks.get(i));
                Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                if (block == null || block == Blocks.AIR) continue;
                loaded.add(BlockPos.of(cells.get(i)));
                loadedBlocks.add(block);
            }
        }

        /** How many recorded spots from a save still wait to come down. */
        public int waiting() {
            return loaded.size();
        }
    }
}
