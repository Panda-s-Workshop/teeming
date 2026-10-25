package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAnimal;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TryFindLiquidGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Magma Tortoise: a huge, slow tortoise of the lava seas. It floats half sunk at the lava surface and paddles across
 * it at 0.08 blocks a tick, wandering on its own but never out of the lava on purpose. The top of its shell is solid:
 * anything can stand on it, and whatever stands on it rides along as it swims. Fed magma cream (one is used), it
 * follows that player for 600 ticks, paddling towards them; while they stand on its shell it swims the way they look,
 * so they can steer it across the lava. Nothing burns it and it never attacks.
 *
 * <p>The lava parts are kept in their own sections so other lava swimmers can copy them: the float and the swim
 * (its own travel in lava, steered towards {@link #wanted()}), and the platform (a solid top that carries its riders).
 */
public class MagmaTortoise extends TeemingAnimal {
    public enum Step implements Phase { IDLE, FOLLOW }

    /** Its swimming speed in lava, in blocks a tick. */
    public static final double SWIM_SPEED = 0.08;
    /** How long it follows whoever fed it, in ticks. */
    public static final int FOLLOW_TICKS = 600;
    /** How much of its height floats under the lava surface: half, so the shell top is half its height above it. */
    public static final double SINK = 0.5;
    /** Turning, in degrees a tick: a heavy body comes round slowly. */
    public static final float TURN = 4.0F;
    /** While following, it stops this far from its leader's side (blocks beyond the edge of its shell). */
    public static final double FOLLOW_GAP = 1.5;
    /** A leader standing on its shell steers it: it swims for a point this far ahead along their gaze. */
    public static final double STEER_AHEAD = 4.0;
    /** Wandering: a spot this many blocks away, picked again after a wait of this many ticks. */
    public static final double WANDER_MIN = 3.0, WANDER_MAX = 8.0;
    public static final int WANDER_WAIT_MIN = 40, WANDER_WAIT_MAX = 120;

    private static final double EASE = 0.3;           // horizontal speed eased towards the wanted one, per tick
    private static final double FLOAT_PULL = 0.3;     // the float closes this share of its depth error a tick
    private static final double MAX_RISE = 0.1;
    private static final double ARRIVE = 0.4;
    private static final double LOOK_AHEAD = 0.4;     // lava must go on this far past the front of its shell
    private static final double DECK_BELOW = 0.15, DECK_ABOVE = 0.1;

    private static final Telegraph FED = Telegraph.particles(ParticleTypes.FLAME, 10, 0.25, 0.7).withSpeed(0.03)
            .withSound(ModSounds.MAGMA_TORTOISE_FED.get(), 1.0F, 1.0F);
    private static final Telegraph FED_POPS = Telegraph.particles(ParticleTypes.LAVA, 6, 0.4, 0.8);
    private static final Telegraph EAGER = Telegraph.particles(ParticleTypes.SMALL_FLAME, 3, 0.15, 0.8).withSpeed(0.01);

    /** Where it is paddling to, server side; null when it rests. */
    private @Nullable Vec3 wanted;

    public MagmaTortoise(EntityType<? extends MagmaTortoise> type, Level level) {
        super(type, level, Step.values());
        setPathfindingMalus(PathType.LAVA, 0.0F);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
        setPathfindingMalus(PathType.FIRE, 0.0F);
        xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.animal("magma_tortoise", 0.12);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));                  // water only: in lava its own float wins
        goalSelector.addGoal(1, new TryFindLiquidGoal(this, FluidTags.LAVA));
        goalSelector.addGoal(5, new LavaWanderGoal());
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ---- floating and swimming in lava -----------------------------------------------------------------------------

    /** In lava it floats and paddles on its own; anywhere else it moves as vanilla mobs do. */
    @Override
    protected void travelInFluid(Vec3 input) {
        if (!isInLava()) {
            super.travelInFluid(input);
            return;
        }
        double depth = getFluidHeight(FluidTags.LAVA);
        double vy = Mth.clamp((depth - getBbHeight() * SINK) * FLOAT_PULL, -MAX_RISE, MAX_RISE);
        Vec3 v = getDeltaMovement();
        Vec3 paddle = paddle();
        setDeltaMovement(v.x + (paddle.x - v.x) * EASE, vy, v.z + (paddle.z - v.z) * EASE);
        move(MoverType.SELF, getDeltaMovement());
    }

    /** The horizontal velocity it wants this tick: turning towards {@link #wanted}, at full speed only once it faces it. */
    private Vec3 paddle() {
        if (wanted == null) return Vec3.ZERO;
        Vec3 to = wanted.subtract(position()).multiply(1, 0, 1);
        double dist = to.length();
        if (dist < ARRIVE) return Vec3.ZERO;
        float goal = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(getYRot(), goal, TURN);
        setYRot(yaw);
        yBodyRot = yaw;
        Vec3 heading = heading(yaw);
        if (!lavaAhead(heading)) return Vec3.ZERO;
        double align = Math.max(0.0, Math.cos(Mth.degreesDifference(yaw, goal) * Mth.DEG_TO_RAD));
        return heading.scale(SWIM_SPEED * align * Math.min(1.0, dist / 0.8));
    }

    private static Vec3 heading(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
    }

    /** True when the lava goes on past the front of its shell, across its whole width: it never paddles out of it. */
    public boolean lavaAhead(Vec3 heading) {
        double half = getBbWidth() / 2.0;
        Vec3 front = position().add(heading.scale(half + LOOK_AHEAD));
        Vec3 side = new Vec3(-heading.z, 0.0, heading.x).scale(half * 0.8);
        return lavaAt(front) && lavaAt(front.add(side)) && lavaAt(front.subtract(side));
    }

    /** Lava where its belly would be, at a spot. */
    private boolean lavaAt(Vec3 spot) {
        return level().getFluidState(BlockPos.containing(spot.x, getY() + 0.1, spot.z)).is(FluidTags.LAVA);
    }

    /** Lava under its whole shell if it stood at {@code spot} (its centre and four edge points). */
    private boolean footprintInLava(Vec3 spot) {
        double half = getBbWidth() / 2.0;
        return lavaAt(spot) && lavaAt(spot.add(half, 0, 0)) && lavaAt(spot.add(-half, 0, 0))
                && lavaAt(spot.add(0, 0, half)) && lavaAt(spot.add(0, 0, -half));
    }

    /** Where it is paddling to, or null when it rests (server side). */
    public @Nullable Vec3 wanted() {
        return wanted;
    }

    /** Sends it paddling to a spot (null: rest). It still stops where the lava ends. Public as a test hook. */
    public void paddleTo(@Nullable Vec3 spot) {
        wanted = spot;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Lava is where it wants to be, in any light. */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getFluidState(pos).is(FluidTags.LAVA) ? 10.0F : 0.0F;
    }

    /** Lava spawns: only blocks count as obstruction (the default refuses any liquid in the hitbox). */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** Wandering: now and then a spot a few blocks off that it can reach in a straight line without leaving the lava. */
    private class LavaWanderGoal extends Goal {
        private int wait = WANDER_WAIT_MIN;
        private int time;

        LavaWanderGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!core().idle() || !isInLava()) return false;
            if (--wait > 0) return false;
            wait = WANDER_WAIT_MIN + random.nextInt(WANDER_WAIT_MAX - WANDER_WAIT_MIN + 1);
            Vec3 spot = pick();
            if (spot == null) return false;
            wanted = spot;
            return true;
        }

        @Override
        public void start() {
            time = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return wanted != null && core().idle() && isInLava() && ++time < 200
                    && wanted.subtract(position()).horizontalDistance() > ARRIVE;
        }

        @Override
        public void stop() {
            if (core().idle()) wanted = null;
        }

        private @Nullable Vec3 pick() {
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = WANDER_MIN + random.nextDouble() * (WANDER_MAX - WANDER_MIN);
                Vec3 spot = position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
                if (clearRun(spot)) return spot;
            }
            return null;
        }

        /** Lava under the whole shell every half block along the straight line there. */
        private boolean clearRun(Vec3 spot) {
            Vec3 step = spot.subtract(position());
            int n = (int) Math.ceil(step.length() / 0.5);
            for (int i = 1; i <= n; i++) if (!footprintInLava(position().add(step.scale(i / (double) n)))) return false;
            return true;
        }
    }

    // ---- the shell as a platform -----------------------------------------------------------------------------------

    /** Its shell is solid to everything, as a boat or a shulker is: things stand on it and bump into it. */
    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return isAlive();
    }

    /** Too heavy to shove. */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** It never shoves what stands on its shell (as it rises into a rider's feet, before the rider is lifted). */
    @Override
    protected void doPush(Entity other) {
        if (other.getBoundingBox().minY < getBoundingBox().maxY - 0.3) super.doPush(other);
    }

    /** Server: whatever stood on its shell moves with it. */
    @Override
    public void travel(Vec3 input) {
        AABB before = getBoundingBox();
        Vec3 from = position();
        super.travel(input);
        if (!level().isClientSide()) carryRiders(before, position().subtract(from));
    }

    /** Client: the local player standing on its shell moves with it, as the server moves everything else. */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            Vec3 moved = new Vec3(getX() - xo, getY() - yo, getZ() - zo);
            carryRiders(getBoundingBox().move(moved.reverse()), moved);
        }
        if (!(level() instanceof ServerLevel level)) return;
        if (core().in(Step.FOLLOW)) EAGER.trail(this, 20);
        paddleCues(level);
    }

    /** Moves everything standing on {@code shell} (its box before it moved) by {@code moved}, as a shulker's lid lifts. */
    private void carryRiders(AABB shell, Vec3 moved) {
        if (moved.lengthSqr() < 1.0E-10) return;
        AABB deck = new AABB(shell.minX, shell.maxY - DECK_BELOW, shell.minZ, shell.maxX, shell.maxY + DECK_ABOVE, shell.maxZ);
        for (Entity rider : level().getEntities(this, deck, e -> onDeck(e, shell))) rider.move(MoverType.SHULKER, moved);
    }

    private boolean onDeck(Entity e, AABB shell) {
        if (e.noPhysics || e.isSpectator() || e.isPassenger() || e instanceof MagmaTortoise) return false;
        if (level().isClientSide() && !e.isLocalInstanceAuthoritative()) return false;
        double feet = e.getBoundingBox().minY;
        return feet >= shell.maxY - DECK_BELOW && feet <= shell.maxY + DECK_ABOVE;
    }

    /** True when {@code e} stands on its shell now. */
    public boolean carries(Entity e) {
        AABB shell = getBoundingBox();
        AABB b = e.getBoundingBox();
        return b.maxX > shell.minX && b.minX < shell.maxX && b.maxZ > shell.minZ && b.minZ < shell.maxZ
                && b.minY >= shell.maxY - DECK_BELOW && b.minY <= shell.maxY + DECK_ABOVE;
    }

    /** Lava splashes off its front flippers and a stroke sound now and then while it swims. */
    private void paddleCues(ServerLevel level) {
        if (!isInLava() || getDeltaMovement().horizontalDistanceSqr() < 0.03 * 0.03) return;
        if (tickCount % 6 == 0) {
            Vec3 h = heading(yBodyRot);
            double half = getBbWidth() / 2.0;
            for (int s = -1; s <= 1; s += 2) {
                Vec3 at = position().add(h.scale(half * 0.4)).add(new Vec3(-h.z, 0, h.x).scale(s * (half + 0.3)));
                level.sendParticles(ParticleTypes.LAVA, at.x, getY() + getBbHeight() * SINK, at.z, 1, 0.1, 0.0, 0.1, 0.0);
            }
            Vec3 wake = position().subtract(h.scale(half + 0.2));
            level.sendParticles(ParticleTypes.SMOKE, wake.x, getY() + getBbHeight() * SINK + 0.1, wake.z, 2, 0.4, 0.05, 0.4, 0.01);
        }
        if (tickCount % 30 == 0) playSound(ModSounds.MAGMA_TORTOISE_PADDLE.get(), 0.7F, getVoicePitch());
    }

    // ---- fed: it follows --------------------------------------------------------------------------------------------

    /** Magma cream feeds it (one is used); anything else does nothing. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.MAGMA_CREAM)) return super.mobInteract(player, hand);
        if (level() instanceof ServerLevel && !feed(player, held)) return InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    /**
     * Server: eats one of {@code food} and follows {@code player} for 600 ticks, starting over if it already follows
     * someone. Returns false (nothing used) when a listener vetoes the "follow" step. Public as a test hook.
     */
    public boolean feed(Player player, ItemStack food) {
        if (level().isClientSide() || !isAlive() || !startPhase(Step.FOLLOW, FOLLOW_TICKS, player)) return false;
        food.consume(1, player);
        wanted = null;
        FED.play(this);
        FED_POPS.play(this);
        return true;
    }

    /** Who it follows, or null (server side). */
    public @Nullable LivingEntity leader() {
        return core().in(Step.FOLLOW) ? core().target() : null;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (isInLava() && !getNavigation().isDone()) getNavigation().stop();   // in lava it steers itself
        if (!core().in(Step.FOLLOW)) return;
        LivingEntity leader = core().target();
        if (leader == null) return;                                          // a posed follow with no one to follow
        if (!leader.isAlive() || leader.isRemoved() || leader.isSpectator() || leader.level() != level()) {
            core().end();
            wanted = null;
            return;
        }
        wanted = followSpot(leader);
    }

    /**
     * Where it paddles to while following: along its leader's gaze while they stand on its shell (so they steer it),
     * else towards them until it is beside them.
     */
    private @Nullable Vec3 followSpot(LivingEntity leader) {
        if (carries(leader)) return position().add(heading(leader.getYRot()).scale(STEER_AHEAD));
        Vec3 to = leader.position().subtract(position()).multiply(1, 0, 1);
        return to.length() > getBbWidth() / 2.0 + FOLLOW_GAP ? leader.position() : null;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        core().end();
        wanted = null;
    }

    // ---- sounds -----------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MAGMA_TORTOISE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MAGMA_TORTOISE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MAGMA_TORTOISE_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + random.nextFloat() * 0.15F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }
}
