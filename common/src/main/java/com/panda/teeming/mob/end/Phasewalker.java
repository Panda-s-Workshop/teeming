package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.end.Riftjackal.SafeTeleport;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Phasewalker: a tall walker of the End midlands whose body is an hourglass, and whose hourglass is its clock. It
 * keeps a cycle whether or not it hunts: 60 ticks solid, a 10-tick shimmer, 60 ticks phased, solid again.
 *
 * <p>Solid, it is an ordinary fighter: it can be hurt, it walks to its target and strikes (an 8-tick wind-up with the
 * right arm raised, then its attack damage to a target still in reach in front of it). The top bulb of its hourglass
 * runs empty over the solid spell, so the sand shows how long is left. When it is empty comes the shimmer (the tell):
 * it stands and flickers, the sand runs back up, a chime rings, star motes rise. It is still solid and can still be
 * hurt. Then it is phased for 60 ticks: translucent, immune to every damage but what reaches past invulnerability (a
 * fall out of the world, a kill command), never hit by a blow or a projectile (it can't be picked), nothing pushes it
 * and it pushes nothing, and it floats straight through blocks toward its target at 0.15 blocks a tick (no collision,
 * no gravity), stopping beside it. It deals no damage while phased.
 *
 * <p>It never turns solid inside a block. When its phased spell runs out (or it is found solid inside a block, after a
 * reload or a shove), it checks its hitbox against block collisions; if any block overlaps it, it stays phased (the
 * linger step, open-ended) and floats toward the nearest spot it could stand on ({@link SafeTeleport} finds it: its
 * own column first, then rings 1 to 6 blocks out), or straight up when there is none that near, and it turns solid on
 * the first tick its hitbox is clear.
 *
 * <p>Counter: strike in the solid window, and don't rely on walls. Payoff, a broken part: 10 health lost inside one
 * solid spell (from turning solid to fading out) cracks the bottom bulb, for good. A cracked walker leaks sand, and
 * while it is phased the leak costs it 1 health every 5 ticks (12 over a phased spell), down to 1, never to death: it
 * must turn solid to be killed. Failing it: it walks through the wall you hid behind and strikes.
 */
public class Phasewalker extends TeemingMonster {
    /** IDLE (solid), the shimmer (the tell), phased, lingering phased inside a block, the wind-up and the blow. */
    public enum Step implements Phase { IDLE, SHIMMER, PHASED, LINGER, WINDUP, STRIKE }

    /** 3 s solid: it can be hurt and it strikes. */
    public static final int SOLID_TICKS = 60;
    /** 0.5 s of shimmer before it fades out. */
    public static final int SHIMMER_TICKS = 10;
    /** 3 s phased: immune, translucent, through blocks. */
    public static final int PHASED_TICKS = 60;
    /** Blocks a tick it floats while phased: 9 blocks over a phased spell. */
    public static final double PHASE_SPEED = 0.15;
    /** Blocks of open ground between its hitbox and its target's at which a phased walker stops closing in. */
    public static final double HOLD_GAP = 0.4;
    /** Lingering inside a block, it looks this far (blocks across) for a spot to stand on. */
    public static final int EXIT_RANGE = 6;
    /** Ticks between looks for that spot. */
    public static final int EXIT_RETHINK = 10;

    public static final int WINDUP_TICKS = 8;             // 0.4 s with the right arm raised high before the blow
    public static final int STRIKE_TICKS = 6;             // the blow lands on its first tick; the arm comes down this long
    public static final int COOLDOWN = 20;                // ticks from the end of one blow to the next wind-up
    public static final float STRIKE = 1.0F;              // x attack damage
    public static final double START_REACH = 0.8;         // blocks between hitboxes: this close it winds up
    public static final double STRIKE_REACH = 1.4;        // blocks between hitboxes: a target this close when the blow lands is hit
    public static final float STRIKE_ARC = 120.0F;        // degrees in front of it the blow covers
    public static final float TURN = 15.0F;               // degrees a tick it turns after its target while winding up

    /** Health it must lose inside one solid spell for its glass to crack. */
    public static final float CRACK_DAMAGE = 10.0F;
    /** Health a cracked walker leaks while phased, every {@link #BLEED_INTERVAL} ticks, never below {@link #BLEED_FLOOR}. */
    public static final float BLEED = 1.0F;
    public static final int BLEED_INTERVAL = 5;
    public static final float BLEED_FLOOR = 1.0F;

    /** Flag bit: the bottom bulb is cracked. */
    public static final int FLAG_CRACKED = 0;
    /** The top bulb's sand is synced as a stage, 15 full to 0 empty, through the solid spell. */
    public static final int SAND_STEPS = 15;

    private static final DustParticleOptions LILAC = new DustParticleOptions(0xC79EE8, 1.0F);
    private static final DustParticleOptions STAR = new DustParticleOptions(0xF6F2FF, 0.7F);
    private static final DustParticleOptions SAND = new DustParticleOptions(0xE8D088, 0.8F);
    private static final ParticleOptions SAND_FALL = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
    private static final ParticleOptions SHARDS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
    private static final Telegraph SHIMMER = Telegraph.particles(ParticleTypes.END_ROD, 10, 0.4, 0.6).withSpeed(0.03)
            .withSound(ModSounds.PHASEWALKER_SHIMMER.get(), 1.2F, 1.0F);
    private static final Telegraph WINDUP = Telegraph.particles(LILAC, 8, 0.35, 0.95)
            .withSound(ModSounds.PHASEWALKER_WINDUP.get(), 1.0F, 1.0F);

    /** Ticks of the current solid spell so far. */
    private int solidTicks;
    /** Health lost in the current solid spell. */
    private float windowDamage;
    private int cooldown;
    /** Saved while phased: it comes back lingering, never solid inside a wall. */
    private boolean resumePhased;
    private @Nullable Vec3 exit;
    private int exitAt;
    private int strikes;
    private int bleeds;

    public Phasewalker(EntityType<? extends Phasewalker> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("phasewalker", 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** It closes in only: every blow is the wind-up and strike, started from its AI step. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
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
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setUnseenMemoryTicks(200));
        // it keeps a target it can't see for 10 s: long enough to walk through the wall that hides it
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true).setUnseenMemoryTicks(200));
    }

    // ---- the cycle ---------------------------------------------------------------------------------------------------

    /** True while it is phased (and lingering): immune, translucent, through blocks. Both sides. */
    public boolean phased() {
        return core().in(Step.PHASED) || core().in(Step.LINGER);
    }

    /** True once its bottom bulb has cracked. Both sides. */
    public boolean cracked() {
        return core().flag(FLAG_CRACKED);
    }

    @Override
    public void tick() {
        if (resumePhased && !level().isClientSide()) {
            resumePhased = false;
            startPhase(Step.LINGER, 0, null);
        }
        noPhysics = phased();
        super.tick();
        noPhysics = phased();
        if (cracked() && level() instanceof ServerLevel level && tickCount % 4 == 0) {
            level.sendParticles(SAND_FALL, getX(), getY() + getBbHeight() * 0.42, getZ(), 1, 0.15, 0.05, 0.15, 0.0);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (phased()) {
            phasedTick(level);
            return;
        }
        if (core().in(Step.SHIMMER)) {
            shimmerTick(level);
            return;
        }
        // solid: never inside a block
        if (!clear(level)) {
            linger();
            return;
        }
        solidTicks++;
        showSand();
        if (solidTicks >= SOLID_TICKS) {
            shimmer();
            return;
        }
        if (core().in(Step.WINDUP)) {
            holdStill();
            windupTick(level);
            return;
        }
        if (core().in(Step.STRIKE)) {
            holdStill();
            return;
        }
        LivingEntity target = getTarget();
        if (cooldown == 0 && valid(target) && gapTo(target) <= START_REACH && solidTicks + WINDUP_TICKS < SOLID_TICKS
                && getSensing().hasLineOfSight(target)) {
            startWindup(target);
        }
    }

    /** The top bulb's sand for the ticks of the solid spell left, synced for the model. */
    private void showSand() {
        int fill = Math.clamp(Math.round((SOLID_TICKS - solidTicks) * (float) SAND_STEPS / SOLID_TICKS), 0, SAND_STEPS);
        if (core().stage() != fill) core().setStage(fill);
    }

    /**
     * The tell: it stops and shimmers for {@link #SHIMMER_TICKS}, still solid. A vetoed shimmer gives it another solid
     * spell. Cuts short the recovery from a blow. Public as a test hook (it may be called at any point of the spell).
     */
    public boolean shimmer() {
        if (!(level() instanceof ServerLevel) || phased()) return false;
        if (!startPhase(Step.SHIMMER, SHIMMER_TICKS, getTarget())) {
            solidTicks = 0;
            return false;
        }
        holdStill();
        SHIMMER.play(this);
        return true;
    }

    /** The shimmer: it stands still and flickers, the sand runs back up, star motes rise. */
    private void shimmerTick(ServerLevel level) {
        holdStill();
        SHIMMER.trail(this, 3);
        double y = getY() + getBbHeight() * 0.45;
        // count 0: the offset is the direction, the speed its pace; the sand rises through the waist
        level.sendParticles(SAND, true, true, getX(), y, getZ(), 0, 0.0, 1.0, 0.0, 0.06);
        if (core().elapsed() % 2 == 0) {
            level.sendParticles(LILAC, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 3, 0.3, 0.5, 0.3, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.SHIMMER) {
            phaseOut(level);
        } else if (ended == Step.PHASED || ended == Step.LINGER) {
            if (clear(level)) solidify(level);
            else linger();
        } else if (ended == Step.WINDUP) {
            strike(level, core().target());
        } else {
            core().end();
            cooldown = COOLDOWN;
        }
    }

    /** It fades out for {@link #PHASED_TICKS}. A vetoed fade leaves it solid for another spell. */
    private void phaseOut(ServerLevel level) {
        if (!startPhase(Step.PHASED, PHASED_TICKS, getTarget())) {
            core().end();
            solidTicks = 0;
            return;
        }
        exit = null;
        double y = getY() + getBbHeight() * 0.5;
        level.sendParticles(LILAC, true, true, getX(), y, getZ(), 20, 0.35, 0.6, 0.35, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), y, getZ(), 8, 0.3, 0.6, 0.3, 0.04);
        level.playSound(null, getX(), y, getZ(), ModSounds.PHASEWALKER_PHASE.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /**
     * Stays phased, open-ended, because a block overlaps its hitbox: it floats out to open space and turns solid there.
     * A pack that vetoes the linger takes the consequence: it turns solid where it is.
     */
    private void linger() {
        exit = null;
        if (core().in(Step.LINGER)) return;
        if (!startPhase(Step.LINGER, 0, getTarget()) && phased() && level() instanceof ServerLevel level) solidify(level);
    }

    /** It turns solid: a new solid spell starts, its top bulb full. */
    private void solidify(ServerLevel level) {
        core().end();
        solidTicks = 0;
        windowDamage = 0.0F;
        exit = null;
        resetFallDistance();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
        showSand();
        double y = getY() + getBbHeight() * 0.5;
        level.sendParticles(SAND, true, true, getX(), y, getZ(), 12, 0.3, 0.5, 0.3, 0.0);
        level.sendParticles(STAR, true, true, getX(), y, getZ(), 6, 0.3, 0.5, 0.3, 0.0);
        level.playSound(null, getX(), y, getZ(), ModSounds.PHASEWALKER_SOLID.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** True when no block's collision shape overlaps its hitbox: it may be solid here. */
    public boolean clear(ServerLevel level) {
        return level.noBlockCollision(this, getBoundingBox().deflate(1.0E-4));
    }

    // ---- phased: through blocks toward the target ---------------------------------------------------------------------

    private void phasedTick(ServerLevel level) {
        getNavigation().stop();
        if (core().in(Step.LINGER) && clear(level)) {
            solidify(level);
            return;
        }
        drift(level);
        bleed(level);
        if (tickCount % 2 == 0) {
            level.sendParticles(LILAC, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0.25, 0.6, 0.25, 0.0);
        }
        if (tickCount % 5 == 0) {
            level.sendParticles(STAR, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0.3, 0.6, 0.3, 0.0);
        }
    }

    /**
     * One tick of floating, no collision and no gravity: toward the target, stopping across once the gap between them is
     * {@link #HOLD_GAP} but still matching its height; lingering, toward the exit spot (or straight up without one).
     */
    private void drift(ServerLevel level) {
        Vec3 goal = null;
        LivingEntity target = core().in(Step.PHASED) ? getTarget() : null;
        if (core().in(Step.LINGER)) {
            if (exit == null || tickCount - exitAt >= EXIT_RETHINK) {
                exit = findExit(level);
                exitAt = tickCount;
            }
            goal = exit != null ? exit : position().add(0.0, 1.0, 0.0);
        } else if (valid(target)) {
            goal = target.position();
        }
        if (goal == null) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        double dx = goal.x - getX(), dy = goal.y - getY(), dz = goal.z - getZ();
        if (target != null && gapTo(target) <= HOLD_GAP) {
            dx = 0.0;
            dz = 0.0;
        }
        Vec3 d = new Vec3(dx, dy, dz);
        double length = d.length();
        Vec3 v = length < 1.0E-4 ? Vec3.ZERO : d.scale(Math.min(PHASE_SPEED, length) / length);
        setDeltaMovement(v);
        if (target != null) face(target, 30.0F);
        else if (v.horizontalDistanceSqr() > 1.0E-6) faceAlong(v);
    }

    /**
     * The nearest spot it could stand on, from {@link SafeTeleport}: its own column first, then rings 1 to
     * {@link #EXIT_RANGE} blocks out, each tried round the circle from the way to its target (or the way it faces).
     */
    private @Nullable Vec3 findExit(ServerLevel level) {
        Vec3 from = position();
        Vec3 here = SafeTeleport.InLevel.spot(level, this, from.x, from.y, from.z);
        if (here != null) return here;
        LivingEntity target = getTarget();
        double first = target != null ? Math.atan2(target.getZ() - getZ(), target.getX() - getX())
                : Math.atan2(Mth.cos(getYRot() * Mth.DEG_TO_RAD), -Mth.sin(getYRot() * Mth.DEG_TO_RAD));
        for (int d = 1; d <= EXIT_RANGE; d++) {
            Vec3 at = SafeTeleport.InLevel.find(level, this, from, d, first, p -> true);
            if (at != null) return at;
        }
        return null;
    }

    /** A cracked walker leaks: 1 health every {@link #BLEED_INTERVAL} ticks phased, from its first, down to 1. */
    private void bleed(ServerLevel level) {
        int t = core().elapsed();
        if (!cracked() || t < 1 || (t - 1) % BLEED_INTERVAL != 0) return;
        level.sendParticles(SAND_FALL, true, true, getX(), getY() + getBbHeight() * 0.42, getZ(), 4, 0.2, 0.05, 0.2, 0.0);
        if (getHealth() <= BLEED_FLOOR || !MechanicEvent.post(this, "bleed", null)) return;
        setHealth(Math.max(BLEED_FLOOR, getHealth() - BLEED));
        bleeds++;
        if (t % 20 == 1) level.playSound(null, getX(), getY(0.45), getZ(), ModSounds.PHASEWALKER_LEAK.get(), getSoundSource(), 0.8F, 1.0F);
    }

    /** Phased, it takes no damage but what reaches past invulnerability (a fall out of the world, a kill command). */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (phased() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        return super.isInvulnerableTo(level, source);
    }

    /** Phased, blows and projectiles pass through it: it can't be picked. */
    @Override
    public boolean isPickable() {
        return !phased() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !phased() && super.isPushable();
    }

    @Override
    protected void doPush(Entity entity) {
        if (!phased()) super.doPush(entity);
    }

    @Override
    public boolean isNoGravity() {
        return phased() || super.isNoGravity();
    }

    // ---- the crack ---------------------------------------------------------------------------------------------------

    /** Health lost while solid counts toward the crack; enough in one solid spell cracks the bottom bulb. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float before = getHealth();
        boolean hurt = super.hurtServer(level, source, amount);
        float lost = before - getHealth();
        if (hurt && lost > 0.0F && !phased() && isAlive()) {
            windowDamage += lost;
            if (!cracked() && windowDamage >= CRACK_DAMAGE) crack(level);
        }
        return hurt;
    }

    /** The counter landed: the bottom bulb cracks for good, with a shatter and spilled sand. */
    private void crack(ServerLevel level) {
        if (!MechanicEvent.post(this, "crack", getTarget())) return;
        core().setFlag(FLAG_CRACKED, true);
        double y = getY() + getBbHeight() * 0.42;
        level.sendParticles(SHARDS, true, true, getX(), y, getZ(), 18, 0.25, 0.15, 0.25, 0.1);
        level.sendParticles(SAND_FALL, true, true, getX(), y, getZ(), 14, 0.3, 0.1, 0.3, 0.0);
        level.sendParticles(STAR, true, true, getX(), y, getZ(), 8, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, getX(), y, getZ(), ModSounds.PHASEWALKER_CRACK.get(), getSoundSource(), 1.2F, 1.0F);
    }

    // ---- the blow ------------------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Blocks between its hitbox and {@code target}'s, across or up and down, whichever is more. */
    public double gapTo(Entity target) {
        return MobCore.gap(this, target);
    }

    /** True when {@code target} stands inside the arc its blow covers. */
    public boolean inFront(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return true;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.degreesDifferenceAbs(toward, getYRot()) <= STRIKE_ARC / 2.0F;
    }

    private void face(Entity target, float maxTurn) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void faceAlong(Vec3 v) {
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F, 30.0F);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** While a step runs its body stays square to the way it faces. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    /** Raises its arm at {@code target} for {@link #WINDUP_TICKS}; the blow follows. Public as a test hook. */
    public boolean startWindup(LivingEntity target) {
        if (!core().idle() || !(level() instanceof ServerLevel) || !startPhase(Step.WINDUP, WINDUP_TICKS, target)) return false;
        holdStill();
        face(target, 360.0F);
        WINDUP.play(this);
        return true;
    }

    /** The wind-up: it turns after its target, motes gathering on the raised hand. */
    private void windupTick(ServerLevel level) {
        LivingEntity target = core().target();
        if (valid(target)) face(target, TURN);
        if (core().elapsed() % 2 == 0) {
            level.sendParticles(LILAC, true, true, getX(), getY() + getBbHeight() * 1.05, getZ(), 2, 0.3, 0.1, 0.3, 0.0);
        }
    }

    /** The blow: its attack damage to a target still in reach and in front; anything else is a swing at the air. */
    private void strike(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.STRIKE, STRIKE_TICKS, target)) {
            core().end();
            cooldown = COOLDOWN;
            return;
        }
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.PHASEWALKER_STRIKE.get(), getSoundSource(), 1.0F, 1.0F);
        if (!valid(target) || gapTo(target) > STRIKE_REACH || !inFront(target) || !MechanicEvent.post(this, "strike", target)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE;
        if (target.hurtServer(level, damageSources().mobAttack(this), damage)) {
            strikes++;
            setLastHurtMob(target);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
        }
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    // ---- test hooks ----------------------------------------------------------------------------------------------------

    /** Ticks of the current solid spell so far. Public as a test hook. */
    public int solidTicks() {
        return solidTicks;
    }

    /** Health lost in the current solid spell. Public as a test hook. */
    public float windowDamage() {
        return windowDamage;
    }

    /** Blows that landed so far. Public as a test hook. */
    public int strikes() {
        return strikes;
    }

    /** Health leaks so far. Public as a test hook. */
    public int bleeds() {
        return bleeds;
    }

    /** Cracks (or mends) its bottom bulb at once. Public as a test hook. */
    public void setCracked(boolean on) {
        core().setFlag(FLAG_CRACKED, on);
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SolidTicks", solidTicks);
        output.putInt("Cooldown", cooldown);
        output.putFloat("WindowDamage", windowDamage);
        output.putBoolean("Cracked", cracked());
        output.putBoolean("Phased", phased() || resumePhased);
    }

    /**
     * The crack, the solid spell so far, the damage toward the crack and the strike cooldown survive a reload. A walker
     * saved phased comes back lingering (phased until it stands in open space), never solid inside a block; the shimmer
     * and a wind-up do not survive.
     */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        solidTicks = Math.clamp(input.getIntOr("SolidTicks", 0), 0, SOLID_TICKS - 1);
        cooldown = Math.clamp(input.getIntOr("Cooldown", 0), 0, COOLDOWN);
        windowDamage = Math.max(0.0F, input.getFloatOr("WindowDamage", 0.0F));
        core().setFlag(FLAG_CRACKED, input.getBooleanOr("Cracked", false));
        resumePhased = input.getBooleanOr("Phased", false);
    }

    // ---- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.PHASEWALKER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PHASEWALKER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PHASEWALKER_DEATH.get();
    }
}
