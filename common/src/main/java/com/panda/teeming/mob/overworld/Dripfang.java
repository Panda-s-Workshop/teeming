package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dripfang: a living stalactite of the dripstone caves. It hangs from a ceiling, still and silent, legs and eyes
 * folded away, one more stone fang among the real ones. When a target passes beneath it, within 1.5 blocks across the
 * ground, it drips (tell): for 12 ticks water drips from its tip with a drip sound and it shivers. Then it lets go and
 * drops point-first at the spot where the target stood when the drip began, gathering speed, and impales whatever it
 * meets for 1.5x its attack.
 *
 * <p>Counter: hear or see the drip and step aside. A miss buries its point in the ground for 60 ticks: stuck, it takes
 * x1.5 and the soft socket on its crown glows. After a hit, or once it wrenches free, it hauls itself straight back up
 * a thread of mineral silk to the spot it hung from (or, if that is gone, to the ceiling above it) and hangs again; it
 * waits 40 ticks before it drips again. Any blow while it hangs shakes it into the drip at once. Left on open ground
 * with no ceiling in reach, it walks on its long legs and stabs with its point.
 */
public class Dripfang extends TeemingMonster {
    public enum Step implements Phase { IDLE, HANGING, TELL, DROP, STUCK, CLIMB }

    public static final int TELL_TICKS = 12;            // the drip
    public static final double DROP_RANGE = 1.5;        // a target this close across the ground, beneath it, sets off the drip
    public static final double DROP_DEPTH = 12.0;       // ...if its feet are no more than this far below the point
    public static final float DROP_HIT = 1.5F;          // x attack damage: 10.5 for its attack of 7
    public static final double DROP_START = 0.4;        // blocks a tick as it lets go...
    public static final double DROP_ACCEL = 0.1;        // ...gaining this much each tick...
    public static final double DROP_MAX = 1.2;          // ...up to this
    public static final int DROP_TICKS = 30;            // a fall longer than this ends it where it is
    public static final int STUCK_TICKS = 60;           // a miss buries its point this long
    public static final float STUCK_DAMAGE = 1.5F;      // x damage taken while stuck
    public static final double CLIMB_SPEED = 0.15;      // up its thread, blocks a tick
    public static final double CLIMB_DRIFT = 0.08;      // across, toward the spot it hung from, blocks a tick
    public static final double CLIMB_REACH = 12.0;      // the highest ceiling above its top it climbs to
    public static final int CLIMB_MAX_TICKS = 200;      // a climb that hasn't arrived by then gives up
    public static final int REARM_TICKS = 40;           // hanging again, it waits this long before it drips again
    public static final double SNAP_REACH = 1.0;        // a fresh spawn this close under a ceiling grips it at once
    public static final double WALK_SPEED = 0.22;
    private static final int CLIMB_CHECK = 20;          // on open ground, how often it looks up for a ceiling
    private static final double HOME_REACH = 3.0;       // the spot it hung from counts as home within this, across

    private static final DustParticleOptions SILK = new DustParticleOptions(0xE8E2D4, 0.6F);
    private static final double SILK_STEP = 0.35;

    private int rearm;
    private int climbCheck;
    private float heldYaw;
    /** Where its feet were while it last hung (its point; the crown touches the ceiling); null until it first hangs. */
    private @Nullable Vec3 hangSpot;
    private Vec3 aim = Vec3.ZERO;
    private Vec3 dropDir = new Vec3(0, -1, 0);
    private @Nullable Vec3 climbTo;
    private boolean dropVetoed;

    public Dripfang(EntityType<? extends Dripfang> type, Level level) {
        super(type, level, Step.values());
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("dripfang", WALK_SPEED);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // only on open ground: its own phases drive it everywhere else
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

    private static boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.STUCK);
    }

    // ---- the phase machine -----------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide()) setNoGravity(core().in(Step.HANGING) || core().in(Step.TELL) || core().in(Step.DROP) || core().in(Step.CLIMB));
        super.tick();
        if (level() instanceof ServerLevel level) cues(level);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (rearm > 0) rearm--;
        if (core().in(Step.HANGING)) {
            hangTick(level);
        } else if (core().in(Step.DROP)) {
            dropTick(level);
        } else if (core().in(Step.CLIMB)) {
            climbTick();
        } else if (!core().idle()) {
            getNavigation().stop();
        } else if (onGround() && --climbCheck <= 0) {
            climbCheck = CLIMB_CHECK;
            if (spotToClimb() != null) climb();
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.HANGING) {
            // a timed hang (a test or a pose) holds on while the grip does
            if (!gripHolds() || !startPhase(Step.HANGING, 0, null)) fall();
        } else if (ended == Step.TELL) {
            letGo(level);
        } else if (ended == Step.DROP) {
            if (onGround()) miss(level);
            else fall();
        } else if (ended == Step.STUCK) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.DRIPFANG_FREE.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(groundCrumbs(), getX(), getY() + 0.1, getZ(), 12, 0.25, 0.05, 0.25, 0.08);
            climb();
        } else {
            fall();
        }
    }

    /** Back on its own legs, with gravity: open ground. It keeps the spot it hung from, to climb back to later. */
    private void fall() {
        core().end();
        setNoGravity(false);
        setXRot(0.0F);
        climbCheck = CLIMB_CHECK;
    }

    // ---- hanging: the disguise -----------------------------------------------------------------------------------
    // Self-contained: the phase HANGING is the disguise. It grips the underside of a sturdy block with its crown and
    // neither moves nor turns nor is pushed; it is silent, its legs and eyes are folded away.

    /** A fresh spawn (or one loaded from a save) tries to grip a ceiling right above it on its first tick. */
    private boolean gripOnFirstTick = true;

    /** True while it hangs disguised. */
    public boolean disguised() {
        return core().in(Step.HANGING);
    }

    /** Hanging, it passes for a stalactite. */
    @Override
    public boolean hiddenFromSight() {
        return disguised();
    }

    private void hangTick(ServerLevel level) {
        getNavigation().stop();
        if (tickCount % 10 == 0 && !gripHolds()) {
            fall();             // its ceiling is gone: it drops to the ground, harmless
            return;
        }
        if (rearm > 0) return;
        LivingEntity prey = prey(level);
        if (prey != null) drip(prey);
    }

    /** Its target, or failing that the nearest survival player, if it stands in the drop zone and it can see it. */
    private @Nullable LivingEntity prey(ServerLevel level) {
        LivingEntity target = getTarget();
        if (valid(target) && inZone(target) && getSensing().hasLineOfSight(target)) return target;
        AABB zone = new AABB(getX() - DROP_RANGE, getY() - DROP_DEPTH, getZ() - DROP_RANGE, getX() + DROP_RANGE, getY() + 0.5, getZ() + DROP_RANGE);
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : level.getEntitiesOfClass(Player.class, zone, EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
            double d = across(p);
            if (d < bestD && inZone(p) && getSensing().hasLineOfSight(p)) {
                best = p;
                bestD = d;
            }
        }
        if (best != null) setTarget(best);
        return best;
    }

    private double across(LivingEntity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * True if {@code e} is in the drop zone: within {@link #DROP_RANGE} across the ground, its head below the point,
     * its feet no more than {@link #DROP_DEPTH} below it.
     */
    public boolean inZone(LivingEntity e) {
        return across(e) <= DROP_RANGE && e.getY() + e.getBbHeight() <= getY() + 0.25 && getY() - e.getY() <= DROP_DEPTH;
    }

    /** The block its crown grips is still sturdy underneath. */
    private boolean gripHolds() {
        BlockPos above = BlockPos.containing(getX(), getY() + getBbHeight() + 0.01, getZ());
        return level().getBlockState(above).isFaceSturdy(level(), above, Direction.DOWN);
    }

    /**
     * The underside of the first block above it, if that block is sturdy underneath, no more than {@code reach} above
     * its top, and it fits hanging there; NaN otherwise.
     */
    private double ceilingAbove(double reach) {
        Level level = level();
        double top = getY() + getBbHeight();
        BlockPos.MutableBlockPos pos = BlockPos.containing(getX(), getY() + 0.01, getZ()).mutable();
        int last = Mth.floor(top + reach);
        for (; pos.getY() <= last; pos.move(Direction.UP)) {
            BlockState state = level.getBlockState(pos);
            if (state.getCollisionShape(level, pos).isEmpty()) continue;
            if (pos.getY() - top > reach || !state.isFaceSturdy(level, pos, Direction.DOWN)) return Double.NaN;
            AABB hanging = getBoundingBox().move(0.0, pos.getY() - top, 0.0);
            return level.noCollision(this, hanging) ? pos.getY() : Double.NaN;
        }
        return Double.NaN;
    }

    private @Nullable Vec3 spotAbove(double reach) {
        double ceiling = ceilingAbove(reach);
        return Double.isNaN(ceiling) ? null : new Vec3(getX(), ceiling - getBbHeight(), getZ());
    }

    /** Where it would climb to: the spot it hung from if that still holds and is near, else the ceiling above it. */
    private @Nullable Vec3 spotToClimb() {
        if (hangSpot != null && Math.hypot(hangSpot.x - getX(), hangSpot.z - getZ()) <= HOME_REACH && hangSpot.y > getY() - 0.5
                && hangSpot.y - getY() <= CLIMB_REACH + 1.0) {
            BlockPos grip = BlockPos.containing(hangSpot.x, hangSpot.y + getBbHeight() + 0.01, hangSpot.z);
            AABB there = getBoundingBox().move(hangSpot.subtract(position()));
            if (level().getBlockState(grip).isFaceSturdy(level(), grip, Direction.DOWN) && level().noCollision(this, there)) return hangSpot;
        }
        return spotAbove(CLIMB_REACH);
    }

    /**
     * Grips the ceiling with its crown at {@code spot} (its feet) and goes still: its disguise. {@code quiet} skips the
     * sound (a fresh spawn). Returns false if it doesn't fit there or an event cancelled it. Public as a test hook.
     */
    public boolean hang(Vec3 spot, boolean quiet) {
        if (!(level() instanceof ServerLevel level)) return false;
        if (!level.noCollision(this, getBoundingBox().move(spot.subtract(position())))) return false;
        if (!startPhase(Step.HANGING, 0, getTarget())) return false;
        gripOnFirstTick = false;
        setPos(spot);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        setXRot(0.0F);
        hangSpot = spot;
        rearm = REARM_TICKS;
        heldYaw = getYRot();
        getNavigation().stop();
        if (!quiet) {
            level.playSound(null, getX(), getY() + getBbHeight(), getZ(), ModSounds.DRIPFANG_HANG.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DRIPSTONE_BLOCK.defaultBlockState()),
                    getX(), getY() + getBbHeight() - 0.1, getZ(), 8, 0.3, 0.05, 0.3, 0.05);
        }
        return true;
    }

    /** Grips the nearest ceiling straight above it, within {@link #CLIMB_REACH}, at once. Public as a test hook. */
    public boolean hangHere() {
        Vec3 spot = spotAbove(CLIMB_REACH);
        return spot != null && hang(spot, true);
    }

    /** The spot it last hung from (its feet), or null. Public as a test hook. */
    public @Nullable Vec3 hangSpot() {
        return hangSpot;
    }

    /** Ticks until it may drip again. Public as a test hook. */
    public int rearm() {
        return rearm;
    }

    /** Lets it drip again at once. Public as a test hook. */
    public void resetRearm() {
        rearm = 0;
    }

    // ---- the drip, the drop and the miss ----------------------------------------------------------------------------

    /**
     * Starts the drip at {@code target}: for {@link #TELL_TICKS} water drips from its point and it shivers; it aims at
     * the spot where the target stands now (straight down if the target is not in the drop zone, or null). Returns false
     * if it isn't hanging or an event cancelled it. Public as a test hook.
     */
    public boolean drip(@Nullable LivingEntity target) {
        if (!disguised() || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        aim = target != null && inZone(target) ? target.position() : new Vec3(getX(), getY() - 1.0, getZ());
        heldYaw = getYRot();
        Telegraph.particles(ParticleTypes.FALLING_DRIPSTONE_WATER, 3, 0.04, 0.0)
                .withSound(ModSounds.DRIPFANG_TELL.get(), 1.5F, 1.0F).play(this);
        return true;
    }

    /** The point its current or last drop was aimed at. Public as a test hook. */
    public Vec3 aim() {
        return aim;
    }

    /** The drip is over: it lets go and drops point-first at the spot it aimed at. */
    private void letGo(ServerLevel level) {
        if (!startPhase(Step.DROP, DROP_TICKS, core().target())) {
            if (!startPhase(Step.HANGING, 0, null)) fall();     // vetoed: it keeps its grip
            rearm = REARM_TICKS;
            return;
        }
        Vec3 d = aim.subtract(position());
        // straight down unless the aim is a spot beneath it in the drop zone (a drip it was posed into has none)
        dropDir = d.y < -1.0E-3 && d.horizontalDistance() <= DROP_RANGE + 0.01 ? d.normalize() : new Vec3(0, -1, 0);
        dropVetoed = false;
        if (dropDir.horizontalDistanceSqr() > 1.0E-4) {
            heldYaw = (float) (Mth.atan2(dropDir.z, dropDir.x) * Mth.RAD_TO_DEG) - 90.0F;
            setFacing(heldYaw);
        }
        setXRot((float) (Mth.atan2(dropDir.horizontalDistance(), -dropDir.y) * Mth.RAD_TO_DEG));
        level.playSound(null, getX(), getY(), getZ(), ModSounds.DRIPFANG_DROP.get(), getSoundSource(), 1.2F, 1.0F);
    }

    private void dropTick(ServerLevel level) {
        getNavigation().stop();
        double speed = Math.min(DROP_MAX, DROP_START + DROP_ACCEL * core().elapsed());
        Vec3 move = dropDir.scale(speed);
        setDeltaMovement(move);
        if (!dropVetoed) {
            AABB reach = getBoundingBox().inflate(0.1).expandTowards(move);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, reach, this::canStrike)) {
                if (impale(level, victim)) return;
            }
        }
        if (core().elapsed() > 0 && (onGround() || verticalCollisionBelow || horizontalCollision)) miss(level);
    }

    /** The drop meets {@code victim}: 1.5x its attack, then it hauls itself back up. False if an event vetoed the hit. */
    private boolean impale(ServerLevel level, LivingEntity victim) {
        if (!MechanicEvent.post(this, "drop_hit", victim)) {
            dropVetoed = true;      // it falls on through, harmless
            return false;
        }
        DamageSource source = damageSources().mobAttack(this);
        if (victim.hurtServer(level, source, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * DROP_HIT)) {
            level.playSound(null, victim.getX(), victim.getY(1.0), victim.getZ(), ModSounds.DRIPFANG_IMPALE.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.9), victim.getZ(), 12, 0.25, 0.2, 0.25, 0.25);
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_WATER, victim.getX(), victim.getY(1.0), victim.getZ(), 6, 0.3, 0.1, 0.3, 0.0);
        }
        setDeltaMovement(Vec3.ZERO);
        climb();
        return true;
    }

    private boolean canStrike(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Dripfang) && !e.isSpectator() && !(e instanceof Player p && p.isCreative());
    }

    /** The counter landed: the drop missed and its point is buried in the ground. */
    private void miss(ServerLevel level) {
        if (!startPhase(Step.STUCK, STUCK_TICKS, null)) {
            fall();
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(false);
        setXRot(0.0F);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.DRIPFANG_MISS.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(groundCrumbs(), getX(), getY() + 0.1, getZ(), 24, 0.35, 0.05, 0.35, 0.15);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.3, getZ(), 10, 0.3, 0.1, 0.3, 0.2);
        level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.1, getZ(), 6, 0.2, 0.0, 0.2, 0.02);
    }

    /** Buries its point where it stands, as a missed drop does. Public as a test hook. */
    public boolean stick() {
        if (!startPhase(Step.STUCK, STUCK_TICKS, null)) return false;
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(false);
        setXRot(0.0F);
        return true;
    }

    private BlockParticleOption groundCrumbs() {
        BlockState below = level().getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ()));
        return new BlockParticleOption(ParticleTypes.BLOCK, below.isAir() ? Blocks.DRIPSTONE_BLOCK.defaultBlockState() : below);
    }

    // ---- the climb back up ------------------------------------------------------------------------------------------

    /**
     * Hauls itself back up its thread: to the spot it hung from if that still holds and is near, else to the ceiling
     * straight above it. With no ceiling in reach it stays on the ground. Public as a test hook.
     */
    public boolean climb() {
        Vec3 to = spotToClimb();
        if (to == null || !startPhase(Step.CLIMB, 0, getTarget())) {
            fall();
            return false;
        }
        climbTo = to;
        setNoGravity(true);
        setXRot(0.0F);
        heldYaw = getYRot();
        getNavigation().stop();
        return true;
    }

    private void climbTick() {
        getNavigation().stop();
        if (climbTo == null) {
            fall();
            return;
        }
        Vec3 to = climbTo.subtract(position());
        if (to.y <= CLIMB_SPEED + 1.0E-4 && to.horizontalDistance() <= CLIMB_DRIFT + 1.0E-4) {
            if (!hang(climbTo, false)) fall();
            return;
        }
        Vec3 across = new Vec3(to.x, 0.0, to.z);
        if (across.length() > CLIMB_DRIFT) across = across.normalize().scale(CLIMB_DRIFT);
        setDeltaMovement(across.x, Mth.clamp(to.y, 0.0, CLIMB_SPEED), across.z);
        if (core().elapsed() > 2 && verticalCollision && !verticalCollisionBelow) {
            // something overhead stopped it short of the spot: it grips that if it can
            Vec3 here = spotAbove(0.5);
            if (here == null || !hang(here, false)) fall();
        } else if (core().elapsed() >= CLIMB_MAX_TICKS) {
            fall();
        }
    }

    // ---- what the player sees and hears every tick --------------------------------------------------------------

    /** The drip, the scrabbling of a stuck point and the thread of a climb, sent from the server each tick. */
    private void cues(ServerLevel level) {
        if (gripOnFirstTick) {
            gripOnFirstTick = false;
            if (core().idle()) {
                Vec3 spot = spotAbove(SNAP_REACH);
                if (spot != null) hang(spot, true);
            }
        }
        if (core().in(Step.TELL)) {
            int t = core().elapsed();
            if (t % 2 == 0) level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_WATER, getX(), getY() - 0.05, getZ(), 1, 0.03, 0.0, 0.03, 0.0);
            if (t % 3 == 1) level.sendParticles(ParticleTypes.DRIPPING_DRIPSTONE_WATER, getX(), getY() + 0.05, getZ(), 1, 0.02, 0.0, 0.02, 0.0);
            if (t % 4 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, look().equals("calcite_fang")
                    ? Blocks.CALCITE.defaultBlockState() : Blocks.DRIPSTONE_BLOCK.defaultBlockState()), getX(), getY(0.8), getZ(), 2, 0.25, 0.1, 0.25, 0.02);
            if (t == TELL_TICKS / 2) level.playSound(null, getX(), getY(), getZ(), ModSounds.DRIPFANG_TELL.get(), getSoundSource(), 1.5F, 1.15F);
        } else if (core().in(Step.STUCK)) {
            if (tickCount % 6 == 0) level.sendParticles(groundCrumbs(), getX(), getY() + 0.1, getZ(), 3, 0.2, 0.02, 0.2, 0.05);
        } else if (core().in(Step.CLIMB) && tickCount % 2 == 0) {
            // up to the ceiling it climbs to; a climb it was posed into shows a thread three blocks long
            double from = getY() + getBbHeight(), to = climbTo != null ? climbTo.y + getBbHeight() : from + 3.0;
            for (double y = from; y < to; y += SILK_STEP) {
                level.sendParticles(SILK, false, true, getX(), y, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    // ---- facing and being moved -------------------------------------------------------------------------------------

    private void setFacing(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
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

    /** Hanging, dripping or stuck, it is held fast: hanging nothing moves it, stuck only falling does. */
    @Override
    public Pin pin() {
        if (core().in(Step.HANGING) || core().in(Step.TELL)) return Pin.FIXED;
        return core().in(Step.STUCK) ? Pin.ROOTED : Pin.FREE;
    }

    /** It is stone that drops for a living: falls never hurt it. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- damage ------------------------------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableTo(level, source) || isDeadOrDying()) return super.hurtServer(level, source, amount);
        boolean hurt = super.hurtServer(level, source, core().in(Step.STUCK) ? amount * STUCK_DAMAGE : amount);
        if (hurt && disguised()) {
            // a blow shakes it loose: it drips at once, aimed at the attacker if the attacker stands beneath it
            LivingEntity attacker = source.getEntity() instanceof LivingEntity living && valid(living) ? living : null;
            if (attacker != null) setTarget(attacker);
            drip(attacker);
        }
        return hurt;
    }

    // ---- saving ------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (hangSpot != null) {
            output.putDouble("HangX", hangSpot.x);
            output.putDouble("HangY", hangSpot.y);
            output.putDouble("HangZ", hangSpot.z);
        }
    }

    /** A reload ends any phase; on its first tick it grips the ceiling again if it was hanging there. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        double y = input.getDoubleOr("HangY", Double.NaN);
        if (!Double.isNaN(y)) hangSpot = new Vec3(input.getDoubleOr("HangX", getX()), y, input.getDoubleOr("HangZ", getZ()));
        gripOnFirstTick = true;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return disguised() || core().in(Step.TELL) ? null : ModSounds.DRIPFANG_AMBIENT.get();   // dripstone is silent
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.DRIPFANG_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DRIPFANG_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.DRIPFANG_STEP.get(), 0.4F, 1.0F);
    }
}
