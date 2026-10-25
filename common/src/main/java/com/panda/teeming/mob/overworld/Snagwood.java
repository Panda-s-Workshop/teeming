package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Snagwood: a dead tree of the forests that walks. Rooted, it is disguised: it stands stock still with its limbs set
 * like branches and its eyes shut, a bare broken snag among the living trees. A target within 5 blocks, or any blow,
 * wakes it: it tears its roots free (10 ticks) and lumbers after its target, slowly.
 *
 * <p>Tell: it creaks for 12 ticks, leaning back with its limbs raised and bark dust falling, facing where it faced
 * when the creak began. Then it sweeps its roots across its front: everything within 3 blocks and inside the 120
 * degrees in front of it takes 1.2x Atk. Counter: step behind it or out of the arc during the creak (anything outside
 * the arc is untouched), and use an axe, which deals x2. After 160 ticks without a target it roots back into its
 * disguise where it stands.
 */
public class Snagwood extends TeemingMonster {
    public enum Step implements Phase { IDLE, ROOTED, UPROOT, TELL, SWEEP, RECOVER }

    public static final int TELL_TICKS = 12;            // the creak
    public static final int SWEEP_TICKS = 8;            // the roots lash across its front; the blow lands as it starts
    public static final int RECOVER_TICKS = 16;         // hunched after the sweep, limbs low
    public static final int SWEEP_COOLDOWN = 30;        // ticks after recovering before it may creak again
    public static final double SWEEP_RADIUS = 3.0;      // centre to centre, along the ground
    public static final float SWEEP_ARC = 120.0F;       // degrees, centred on the way it faces
    public static final double SWEEP_HEIGHT = 1.5;      // how far above or below its feet a target's feet may be
    public static final float SWEEP_HIT = 1.2F;         // x attack damage
    public static final double SWEEP_KNOCKBACK = 0.5;
    public static final double CREAK_RANGE = 2.5;       // it starts the creak with its target this close...
    public static final float CREAK_CONE = 30.0F;       // ...and no more than this many degrees off its line
    public static final float AXE_DAMAGE = 2.0F;        // x damage from an axe blow
    public static final int ROOT_BACK_TICKS = 160;      // without a target, then it roots back
    public static final double WAKE_RANGE = 5.0;        // a target this close wakes it
    public static final int UPROOT_TICKS = 10;          // tearing its roots free
    public static final double WALK_SPEED = 0.17;       // slow: a zombie walks at 0.23
    public static final float TURN_PER_TICK = 8.0F;     // degrees a tick it turns toward its target while awake

    private int cooldown;
    /** The way it faces while a phase holds it still (the creak, the sweep, its disguise). */
    private float heldYaw;

    public Snagwood(EntityType<? extends Snagwood> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("snagwood", WALK_SPEED);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** The sweep is its only attack; the AI step starts the creak. This goal only walks it in. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private static boolean canTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---- the phase machine -----------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) disguiseTick(level);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        boolean valid = canTarget(target);
        if (disguised()) {
            getNavigation().stop();
            LivingEntity near = valid && distanceTo(target) <= WAKE_RANGE ? target : nearestPlayer(level);
            if (near != null) wake(near);
            return;
        }
        if (!core().idle()) {
            getNavigation().stop();
            if (core().in(Step.TELL)) creakTick(level);
            return;
        }
        if (lastTarget < 0) lastTarget = level.getGameTime();           // awake without ever having been rooted
        if (valid) {
            seenTarget(level);
            turnToward(target.position());
            if (cooldown == 0 && canCreakAt(target)) creak(target);
        } else if (level.getGameTime() - lastTarget >= ROOT_BACK_TICKS && onGround() && !isInWater()) {
            if (!root(false)) lastTarget = level.getGameTime();       // vetoed: try again after another wait
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.ROOTED) {
            if (!startPhase(Step.ROOTED, 0, null)) awake(level);    // a timed disguise (a test or a pose) holds on
        } else if (ended == Step.UPROOT) {
            awake(level);
        } else if (ended == Step.TELL) {
            if (startPhase(Step.SWEEP, SWEEP_TICKS, core().target())) sweep(level);
            else rest();
        } else if (ended == Step.SWEEP) {
            if (!startPhase(Step.RECOVER, RECOVER_TICKS, null)) rest();
        } else {
            rest();
        }
    }

    private void awake(ServerLevel level) {
        core().end();
        lastTarget = level.getGameTime();
    }

    private void rest() {
        core().end();
        cooldown = SWEEP_COOLDOWN;
    }

    /** Ticks until it may creak again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    // ---- the creak and the sweep -----------------------------------------------------------------------------------

    private boolean canCreakAt(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        return dx * dx + dz * dz <= CREAK_RANGE * CREAK_RANGE && Math.abs(target.getY() - getY()) <= SWEEP_HEIGHT
                && Math.abs(angleTo(target.position())) <= CREAK_CONE && getSensing().hasLineOfSight(target);
    }

    /**
     * Starts the creak at {@code target}: for {@link #TELL_TICKS} it leans back, limbs raised, and holds the way it
     * faces now; the sweep follows. Returns false if it is busy or an event cancelled it. Public as a test hook.
     */
    public boolean creak(@Nullable LivingEntity target) {
        if (!core().idle() || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        heldYaw = getYRot();
        getNavigation().stop();
        Telegraph.particles(barkDust(), 12, 0.45, 0.8).withSpeed(0.05).withSound(ModSounds.SNAGWOOD_TELL.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    private void creakTick(ServerLevel level) {
        Telegraph.particles(barkDust(), 9, 0.45, 0.8).withSpeed(0.05).trail(this, 3);
        if (core().elapsed() % 4 == 2) arcMarks(level, 1);       // the reach of the coming sweep, scratched into the ground
    }

    /**
     * The roots lash across its front: everything within {@link #SWEEP_RADIUS} and inside {@link #SWEEP_ARC} takes
     * 1.2x Atk and is shoved away. Returns how many it hurt. Public as a test hook.
     */
    public int sweep(ServerLevel level) {
        level.playSound(null, getX(), getY(), getZ(), ModSounds.SNAGWOOD_SWEEP.get(), getSoundSource(), 1.0F, 1.0F);
        arcMarks(level, 3);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * SWEEP_HIT;
        int hurt = 0;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(SWEEP_RADIUS, SWEEP_HEIGHT, SWEEP_RADIUS), this::canSweep)) {
            if (!inSweep(victim.position()) || !MechanicEvent.post(this, "sweep_hit", victim)) continue;
            DamageSource source = damageSources().mobAttack(this);
            if (victim.hurtServer(level, source, damage)) {
                hurt++;
                victim.knockback(SWEEP_KNOCKBACK, getX() - victim.getX(), getZ() - victim.getZ(), source, damage);
                Push.sync(victim);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.4), victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (hurt == 0) {
            // the counter landed: the roots tear through empty ground
            level.playSound(null, getX(), getY(), getZ(), ModSounds.SNAGWOOD_MISS.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 6, 0.6, 0.05, 0.6, 0.02);
        }
        return hurt;
    }

    private boolean canSweep(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Snagwood) && !e.isSpectator()
                && !(e instanceof Player p && p.isCreative());
    }

    /** True if a body standing at {@code feet} is inside the sweep: within reach, near its height, inside the front arc. */
    public boolean inSweep(Vec3 feet) {
        double dx = feet.x - getX(), dz = feet.z - getZ();
        if (dx * dx + dz * dz > SWEEP_RADIUS * SWEEP_RADIUS || Math.abs(feet.y - getY()) > SWEEP_HEIGHT) return false;
        return dx * dx + dz * dz < 1.0E-6 || Math.abs(angleTo(feet)) <= SWEEP_ARC / 2.0F;
    }

    /** Crumbs of the ground along the arc's edge (and, at the sweep, across it), so the reach shows. */
    private void arcMarks(ServerLevel level, int rings) {
        BlockParticleOption crumbs = new BlockParticleOption(ParticleTypes.BLOCK, groundState());
        for (int ring = 0; ring < rings; ring++) {
            double r = SWEEP_RADIUS * (rings == 1 ? 1.0 : (ring + 1.0) / rings);
            for (float a = -SWEEP_ARC / 2.0F; a <= SWEEP_ARC / 2.0F + 0.01F; a += 15.0F) {
                float yaw = (getYRot() + a) * Mth.DEG_TO_RAD;
                level.sendParticles(crumbs, getX() - Mth.sin(yaw) * r, getY() + 0.1, getZ() + Mth.cos(yaw) * r, rings == 1 ? 1 : 2, 0.08, 0.02, 0.08, 0.05);
            }
        }
    }

    private BlockState groundState() {
        BlockState below = level().getBlockState(blockPosition().below());
        return below.isAir() ? Blocks.DIRT.defaultBlockState() : below;
    }

    private BlockParticleOption barkDust() {
        return new BlockParticleOption(ParticleTypes.BLOCK, ("birch_snag".equals(look()) ? Blocks.BIRCH_LOG : Blocks.OAK_LOG).defaultBlockState());
    }

    // ---- facing ----------------------------------------------------------------------------------------------------

    private float yawTo(Vec3 point) {
        return (float) (Mth.atan2(point.z - getZ(), point.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** Degrees from the way it faces to {@code point}, -180..180. */
    public float angleTo(Vec3 point) {
        return Mth.degreesDifference(getYRot(), yawTo(point));
    }

    private void turnToward(Vec3 point) {
        setFacing(Mth.approachDegrees(getYRot(), yawTo(point), TURN_PER_TICK));
    }

    private void setFacing(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Turns to face {@code point} at once. Public as a test hook. */
    public void face(Vec3 point) {
        setFacing(yawTo(point));
        yRotO = getYRot();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
        heldYaw = getYRot();
    }

    /**
     * While a phase holds it, body and head stay the way it faced when the phase began. The server owns that facing;
     * a client lines body and head up with the synced yaw.
     */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core().idle()) {
            super.tickHeadTurn(yBodyRot);
        } else if (level().isClientSide()) {
            this.yBodyRot = getYRot();
            this.yHeadRot = getYRot();
        } else {
            setFacing(heldYaw);
        }
    }

    // ---- disguise: a dead snag until something comes near ------------------------------------------------------------
    // Self-contained: the phase ROOTED is the disguise. It starts rooted, wakes for a target within WAKE_RANGE or any
    // blow, and roots back after ROOT_BACK_TICKS without a target. Rooted, it neither moves nor turns nor is pushed.

    /** A fresh spawn, or one saved while rooted, takes root on its first tick. */
    private boolean rootOnFirstTick = true;
    /** Game time when it last had a target (or woke); -1 until it first stands awake. */
    private long lastTarget = -1;

    /** True while it stands rooted and disguised. */
    public boolean disguised() {
        return core().in(Step.ROOTED);
    }

    private void disguiseTick(ServerLevel level) {
        if (rootOnFirstTick) {
            rootOnFirstTick = false;
            if (core().idle()) root(true);
        }
        if (disguised()) setFacing(heldYaw);
    }

    /**
     * Takes root where it stands and goes still: its disguise. {@code quiet} skips the sound (a fresh spawn). Returns
     * false if an event cancelled it. Public as a test hook.
     */
    public boolean root(boolean quiet) {
        if (!(level() instanceof ServerLevel level) || !startPhase(Step.ROOTED, 0, null)) return false;
        rootOnFirstTick = false;
        heldYaw = getYRot();
        getNavigation().stop();
        setTarget(null);
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (!quiet) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.SNAGWOOD_ROOT.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.1, getZ(), 14, 0.4, 0.05, 0.4, 0.05);
        }
        return true;
    }

    /**
     * Drops the disguise: it tears its roots free for {@link #UPROOT_TICKS}, then hunts {@code target} (if any).
     * Returns false if it wasn't rooted or an event cancelled it. Public as a test hook.
     */
    public boolean wake(@Nullable LivingEntity target) {
        rootOnFirstTick = false;
        if (!disguised() || !startPhase(Step.UPROOT, UPROOT_TICKS, target)) return false;
        if (target != null) setTarget(target);
        if (level() instanceof ServerLevel level) {
            lastTarget = level.getGameTime();
            level.playSound(null, getX(), getY(), getZ(), ModSounds.SNAGWOOD_WAKE.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.1, getZ(), 20, 0.45, 0.05, 0.45, 0.1);
            Telegraph.particles(barkDust(), 16, 0.4, 0.6).withSpeed(0.08).play(this);
        }
        return true;
    }

    /** Wakes it at once, with no uproot, as if it had never been rooted. Public as a test hook. */
    public void standAwake() {
        rootOnFirstTick = false;
        if (level() instanceof ServerLevel level) awake(level);
    }

    private void seenTarget(ServerLevel level) {
        lastTarget = level.getGameTime();
    }

    /** The nearest player in survival or adventure within {@link #WAKE_RANGE} that it can see, or null. */
    private @Nullable Player nearestPlayer(ServerLevel level) {
        Player p = level.getNearestPlayer(getX(), getY(), getZ(), WAKE_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        return p != null && distanceTo(p) <= WAKE_RANGE && getSensing().hasLineOfSight(p) ? p : null;
    }

    /** Rooted, only falling moves it. */
    @Override
    public Pin pin() {
        return disguised() ? Pin.ROOTED : Pin.FREE;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Rooted", disguised() || rootOnFirstTick);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        rootOnFirstTick = input.getBooleanOr("Rooted", true);
    }

    // ---- axes ------------------------------------------------------------------------------------------------------

    /** True for a melee blow struck with an axe. */
    public static boolean axeBlow(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        return source.isDirect() && source.getEntity() instanceof LivingEntity && weapon != null && weapon.is(ItemTags.AXES);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableTo(level, source) || isDeadOrDying()) return super.hurtServer(level, source, amount);
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        boolean axe = axeBlow(source) && MechanicEvent.post(this, "axe_hit", attacker);
        boolean hurt = super.hurtServer(level, source, axe ? amount * AXE_DAMAGE : amount);
        if (hurt && axe) {
            level.playSound(null, getX(), getY(1.0), getZ(), ModSounds.SNAGWOOD_AXE_HIT.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(barkDust(), getX(), getY(0.55), getZ(), 18, 0.3, 0.4, 0.3, 0.15);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY(0.55), getZ(), 8, 0.3, 0.3, 0.3, 0.2);
        }
        if (hurt && disguised()) wake(attacker);
        return hurt;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SNAGWOOD_AMBIENT.get() : null;    // a snag is silent
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SNAGWOOD_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SNAGWOOD_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.SNAGWOOD_STEP.get(), 0.5F, 1.0F);
    }
}
