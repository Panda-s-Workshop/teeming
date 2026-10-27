package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModSounds;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Swellshade: a gloomy shade of the End midlands, a dark swelling mass with a lilac core showing through the cracks in
 * its belly. Asleep it stays as it is. From the tick it first takes a foe it swells every 200 ticks: a deep breath for
 * the last 16 ticks before (it holds still, lilac motes streaming into it, the breath's sound), then a pulse and one step
 * bigger: +0.25 scale and +25 % of its base health and attack, up to four steps (x2: 0.8 x 1.6 to 1.6 x 3.2, health 24
 * to 48, attack 4 to 8). Each swell adds the health it gains to what it has left, and lights more of the cracks round
 * its core. Once awake it keeps swelling whether or not its foe stays in sight: it grows while you wait. A swell needs
 * headroom; under a ceiling too low for the bigger body it waits until it has room.
 *
 * <p>It fights in melee: an 8-tick wind-up (the mass rears back, both arms raised high, lilac motes, a rising hiss),
 * then a blow for its attack at whatever is still in reach in front of it. Its reach grows with it.
 *
 * <p>Counter: kill it early. A fresh shade is a short fight and dies snuffed out like a candle. A full-size one has
 * twice the health and hits twice as hard, but bursts when it dies and drops two more void ichor (one more from half
 * size) and twice the experience.
 *
 * <p>Its size, its clock to the next swell, whether it is awake and its strike cooldown survive a reload. The growth
 * step is its visual stage, so the cracks' glow and the size always agree on every client.
 */
public class Swellshade extends TeemingMonster {
    /** IDLE, the deep breath before a swell, the pulse as it swells, the wind-up before a blow, the blow. */
    public enum Step implements Phase { IDLE, INHALE, PULSE, WINDUP, STRIKE }

    /** Ticks between swells, counted from the tick it first takes a foe. */
    public static final int GROW_EVERY = 200;
    /** Each swell adds this much of its base scale, health and attack. */
    public static final double GROW_STEP = 0.25;
    /** Swells at most: four steps of 0.25 make it twice its size. */
    public static final int MAX_STEPS = 4;
    /** The deep breath before a swell: the last 16 ticks of the wait, held still. */
    public static final int INHALE_TICKS = 16;
    /** The pulse as it swells: the body throbs out past its new size and settles. */
    public static final int PULSE_TICKS = 10;

    public static final int WINDUP_TICKS = 8;             // 0.4 s reared back with both arms raised before the blow
    public static final int STRIKE_TICKS = 6;             // the blow lands on its first tick; the arms come down this long
    public static final int COOLDOWN = 20;                // ticks from the end of one blow to the next wind-up
    public static final float STRIKE = 1.0F;              // x attack damage (which grows with it)
    public static final double START_REACH = 0.6;         // blocks between hitboxes, times its growth: this close it winds up
    public static final double STRIKE_REACH = 1.2;        // blocks between hitboxes, times its growth: this close the blow hits
    public static final float STRIKE_ARC = 120.0F;        // degrees in front of it the blow covers
    public static final float TURN = 15.0F;               // degrees a tick it turns after its target while winding up

    /** Experience when it dies: 10 fresh, 20 at full size, evenly between. */
    public static final int XP_SMALL = 10;
    public static final int XP_FULL = 20;
    /** Void ichor a full-size one drops on top of its loot; half size drops half as many more. */
    public static final int FULL_EXTRA_ICHOR = 2;

    private static final Identifier GROWTH = TeemingMod.id("swellshade_growth");
    private static final List<Holder<Attribute>> GROWN = List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE);
    private static final DustParticleOptions LILAC = new DustParticleOptions(0xC890FF, 1.2F);
    private static final DustParticleOptions GLOOM = new DustParticleOptions(0x2E2452, 1.6F);
    private static final DustParticleOptions STAR = new DustParticleOptions(0xF4F2FF, 0.7F);

    /** Server: true from the tick it first takes a foe; its swell clock runs only then. */
    private boolean awake;
    /** Server: ticks since it woke or last swelled, up to {@link #GROW_EVERY} (held there while it lacks room). */
    private int growClock;
    private int cooldown;
    private int swells;
    private int strikes;
    private int snuffs;
    private int waits;

    public Swellshade(EntityType<? extends Swellshade> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP_SMALL;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("swellshade", 0.22);
    }

    /** Its five growth steps show as five glow stages: the cracks round its core lit wider at each. */
    @Override
    public int visualStages() {
        return MAX_STEPS + 1;
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- growth --------------------------------------------------------------------------------------------------------

    /** Its growth step, 0 (fresh) to {@link #MAX_STEPS} (full size). Both sides. */
    public int steps() {
        return Math.min(core().stage(), MAX_STEPS);
    }

    /** Its size against a fresh one: 1 + 0.25 per step, so 1 to 2. */
    public double growth() {
        return 1.0 + GROW_STEP * steps();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) applyGrowth();
    }

    /**
     * Server: sets the growth modifiers (scale, health, attack: +0.25 of the base each per step) to match its step,
     * wherever the step came from (a swell, a reload, a command posing it). Health it gains is added to what it has.
     */
    private void applyGrowth() {
        double want = GROW_STEP * steps();
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier had = health == null ? null : health.getModifier(GROWTH);
        if ((had == null ? 0.0 : had.amount()) == want) return;
        float before = getMaxHealth();
        for (Holder<Attribute> attribute : GROWN) {
            AttributeInstance instance = getAttribute(attribute);
            if (instance == null) continue;
            if (want <= 0.0) instance.removeModifier(GROWTH);
            else instance.addOrReplacePermanentModifier(new AttributeModifier(GROWTH, want, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        refreshDimensions();
        float gained = getMaxHealth() - before;
        if (gained > 0.0F && isAlive()) setHealth(getHealth() + gained);
        else if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        xpReward = XP_SMALL + (XP_FULL - XP_SMALL) * steps() / MAX_STEPS;
    }

    /** The swell clock, once awake: the deep breath 16 ticks before the mark, the swell on it. */
    private void growTick(ServerLevel level) {
        growClock = Math.min(growClock + 1, GROW_EVERY);
        if (growClock == GROW_EVERY - INHALE_TICKS && core().idle()) inhale(level);
        if (growClock >= GROW_EVERY) swell(level);
    }

    /** The deep breath before a swell: it holds still and draws in a long breath. */
    private void inhale(ServerLevel level) {
        if (!startPhase(Step.INHALE, INHALE_TICKS, null)) return;
        holdStill();
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.SWELLSHADE_INHALE.get(), getSoundSource(), 1.0F + 0.2F * steps(), 1.0F);
    }

    /** Through the breath lilac motes and wisps of murk stream in toward its core. */
    private void inhaleTick(ServerLevel level) {
        if (core().elapsed() % 2 != 0) return;
        double r = getBbWidth() * 1.6 + 0.6;
        double y = getY() + getBbHeight() * 0.45;
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double px = getX() + Math.cos(a) * r, pz = getZ() + Math.sin(a) * r, py = y + (random.nextDouble() - 0.5) * getBbHeight() * 0.6;
            // a speed-0 count-0 particle moves along its offset: each mote flies from the ring toward the core
            level.sendParticles(LILAC, true, true, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, px, py, pz, 0, getX() - px, y - py, getZ() - pz, 0.12);
        }
    }

    /**
     * The swell: one step bigger, with a pulse. Waits (clock held at the mark, tried again every tick) while the bigger
     * body would not fit under what is over it; a vetoed swell starts the wait over. Public as a test hook; true if it
     * grew.
     */
    public boolean swell(ServerLevel level) {
        if (steps() >= MAX_STEPS) return false;
        if (!roomToGrow()) {
            waits++;
            return false;
        }
        growClock = 0;
        if (!MechanicEvent.post(this, "swell", getTarget())) {
            level.sendParticles(ParticleTypes.SMOKE, true, true, getX(), getY(0.5), getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            return false;
        }
        core().setStage(steps() + 1);
        applyGrowth();
        swells++;
        boolean full = steps() >= MAX_STEPS;
        double y = getY(0.5);
        double w = getBbWidth();
        level.playSound(null, getX(), y, getZ(), full ? ModSounds.SWELLSHADE_FULL.get() : ModSounds.SWELLSHADE_SWELL.get(), getSoundSource(),
                1.2F + 0.2F * steps(), 1.0F);
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24);
            level.sendParticles(LILAC, true, true, getX() + Math.cos(a) * w * 0.9, y, getZ() + Math.sin(a) * w * 0.9, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(GLOOM, true, true, getX(), y, getZ(), 16 + 6 * steps(), w * 0.6, getBbHeight() * 0.35, w * 0.6, 0.0);
        level.sendParticles(STAR, true, true, getX(), y, getZ(), 6 + 2 * steps(), w * 0.5, getBbHeight() * 0.3, w * 0.5, 0.0);
        if (core().idle() || core().in(Step.INHALE)) {
            if (startPhase(Step.PULSE, PULSE_TICKS, null)) holdStill();
            else core().end();
        }
        return true;
    }

    /** True when the body one step bigger fits under whatever is above it here (it grows from its feet up). */
    private boolean roomToGrow() {
        AABB box = getBoundingBox();
        double height = getBbHeight() + getType().getHeight() * GROW_STEP;
        return level().noBlockCollision(this, new AABB(box.minX + 0.01, box.minY + 0.01, box.minZ + 0.01, box.maxX - 0.01, box.minY + height, box.maxZ - 0.01));
    }

    // ---- the AI step ---------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        if (!awake) {
            if (valid(target)) {
                awake = true;                                // the clock starts this tick: the first swell is 200 later
                growClock = 0;
            }
        } else if (steps() < MAX_STEPS) {
            growTick(level);
        }
        if (!core().idle()) {
            holdStill();
            if (core().in(Step.WINDUP)) windupTick(level);
            else if (core().in(Step.INHALE)) inhaleTick(level);
            return;
        }
        if (cooldown == 0 && valid(target) && gapTo(target) <= START_REACH * growth() && getSensing().hasLineOfSight(target)) startWindup(target);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.WINDUP) {
            strike(level, core().target());
        } else if (ended == Step.STRIKE) {
            core().end();
            cooldown = COOLDOWN;
        } else {
            core().end();
        }
    }

    // ---- the blow ------------------------------------------------------------------------------------------------------

    /** Rears back at {@code target} for {@link #WINDUP_TICKS}; the blow follows. Public as a test hook. */
    public boolean startWindup(LivingEntity target) {
        if (!core().idle() || !(level() instanceof ServerLevel level) || !startPhase(Step.WINDUP, WINDUP_TICKS, target)) return false;
        holdStill();
        face(target, 360.0F);
        Telegraph.particles(LILAC, 8 + 2 * steps(), 0.4 * growth(), 0.9).withSound(ModSounds.SWELLSHADE_WINDUP.get(), 1.0F, 1.1F - 0.1F * steps()).play(this);
        return true;
    }

    /** The wind-up: it turns after its target, motes rising off its raised claws. */
    private void windupTick(ServerLevel level) {
        LivingEntity target = core().target();
        if (valid(target)) face(target, TURN);
        if (core().elapsed() % 2 == 0) {
            level.sendParticles(LILAC, true, true, getX(), getY() + getBbHeight() * 1.05, getZ(), 2, 0.35 * growth(), 0.1, 0.35 * growth(), 0.0);
        }
    }

    /** The blow: its attack (which grows with it) to a target still in reach and in front; anything else is a swing at the air. */
    private void strike(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.STRIKE, STRIKE_TICKS, target)) {
            core().end();
            cooldown = COOLDOWN;
            return;
        }
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.SWELLSHADE_STRIKE.get(), getSoundSource(), 1.0F, 1.1F - 0.1F * steps());
        if (!valid(target) || gapTo(target) > STRIKE_REACH * growth() || !inFront(target) || !MechanicEvent.post(this, "strike", target)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE;
        if (target.hurtServer(level, damageSources().mobAttack(this), damage)) {
            strikes++;
            setLastHurtMob(target);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
        }
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Blocks of open ground between its hitbox and {@code target}'s, across. */
    public double gapTo(Entity target) {
        double across = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        return across - (getBbWidth() + target.getBbWidth()) / 2.0;
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

    /** While a step runs its body stays square to the way it faces. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    // ---- death and drops -----------------------------------------------------------------------------------------------

    /** A fresh shade is snuffed out like a candle (the counter landed); a swollen one bursts in murk and lilac. */
    @Override
    public void die(DamageSource source) {
        boolean was = dead;
        super.die(source);
        if (was || !dead || !(level() instanceof ServerLevel level)) return;
        double y = getY(0.5);
        if (steps() == 0) {
            snuffs++;
            level.playSound(null, getX(), y, getZ(), ModSounds.SWELLSHADE_SNUFFED.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.SMOKE, true, true, getX(), y, getZ(), 12, 0.2, 0.3, 0.2, 0.01);
            level.sendParticles(STAR, true, true, getX(), y, getZ(), 6, 0.15, 0.15, 0.15, 0.0);
        } else {
            double w = getBbWidth();
            level.sendParticles(GLOOM, true, true, getX(), y, getZ(), 20 + 12 * steps(), w * 0.7, getBbHeight() * 0.4, w * 0.7, 0.0);
            level.sendParticles(LILAC, true, true, getX(), y, getZ(), 10 + 8 * steps(), w * 0.6, getBbHeight() * 0.35, w * 0.6, 0.0);
        }
    }

    /** Void ichor on top of its loot for its size: 2 more at full size, 1 more from half size. */
    public int extraIchor() {
        return FULL_EXTRA_ICHOR * steps() / MAX_STEPS;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        int extra = extraIchor();
        if (extra > 0) spawnAtLocation(level, new ItemStack(ModMaterials.VOID_ICHOR.get(), extra), (float) (getBbHeight() * 0.5));
    }

    // ---- test hooks ----------------------------------------------------------------------------------------------------

    /** True once it has taken a foe and its swell clock runs. Public as a test hook. */
    public boolean awake() {
        return awake;
    }

    /** Ticks since it woke or last swelled. Public as a test hook. */
    public int growClock() {
        return growClock;
    }

    /** Wakes it and sets its swell clock, as if that many ticks had passed since it woke or last swelled. Public as a test hook. */
    public void setGrowClock(int ticks) {
        awake = true;
        growClock = Math.clamp(ticks, 0, GROW_EVERY);
    }

    /** Sets its growth step at once (0 to 4), with the health that comes with it. Public as a test hook. */
    public void setSteps(int steps) {
        core().setStage(Math.clamp(steps, 0, MAX_STEPS));
        applyGrowth();
    }

    /** Swells so far. Public as a test hook. */
    public int swells() {
        return swells;
    }

    /** Swells put off for want of headroom, a tick each. Public as a test hook. */
    public int waits() {
        return waits;
    }

    /** Blows that landed so far. Public as a test hook. */
    public int strikes() {
        return strikes;
    }

    /** Deaths while still fresh (the counter landed). Public as a test hook. */
    public int snuffs() {
        return snuffs;
    }

    /** Ticks until it may wind up again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** The experience it drops now. Public as a test hook. */
    public int experience() {
        return xpReward;
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Swell", steps());
        output.putInt("SwellClock", growClock);
        output.putBoolean("Awake", awake);
        output.putInt("Cooldown", cooldown);
    }

    /** Its size, its swell clock, whether it is awake and its strike cooldown survive a reload; a breath or a blow does not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        core().setStage(Math.clamp(input.getIntOr("Swell", 0), 0, MAX_STEPS));
        growClock = Math.clamp(input.getIntOr("SwellClock", 0), 0, GROW_EVERY);
        awake = input.getBooleanOr("Awake", false);
        cooldown = Math.clamp(input.getIntOr("Cooldown", 0), 0, COOLDOWN);
        applyGrowth();
    }

    // ---- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SWELLSHADE_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SWELLSHADE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SWELLSHADE_DEATH.get();
    }

    /** A bigger shade sounds deeper. */
    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * (float) (1.15 - 0.1 * steps());
    }
}
