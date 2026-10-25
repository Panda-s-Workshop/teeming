package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rattlecoil: a badlands rattlesnake that wants to be left alone. When a player comes within 6 blocks (or anything
 * hurts it) it coils, lifts its head and shakes its rattle for 1.5 s. Then it holds its coil, watching: come within 4
 * blocks and it strikes, a fast lunge whose bite does its attack damage and Poison II for 5 s. Counter: back off; once
 * whoever it warned is more than 8 blocks away it settles down again. It never hunts.
 */
public class Rattlecoil extends TeemingMonster {
    /** IDLE, the rattle (tell), holding the coil while it watches, the lunge, pulling back into the coil, settling down. */
    public enum Step implements Phase { IDLE, RATTLE, WARY, STRIKE, RECOVER, CALM }

    public static final int TELL_TICKS = 30;              // 1.5 s of rattling before it may strike
    public static final double WARN_RANGE = 6.0;          // a player this close sets off the rattle
    public static final double STRIKE_RANGE = 4.0;        // after the rattle, coming this close draws the strike
    public static final double CALM_RANGE = 8.0;          // backing off past this settles it
    public static final int POISON_TICKS = 100;
    public static final int POISON_LEVEL = 1;             // amplifier 1 = Poison II
    public static final float STRIKE_HIT = 1.0F;          // × attack damage
    public static final double STRIKE_SPEED = 0.6;        // blocks a tick: a 4-block lunge lands in about 4 ticks
    public static final int STRIKE_TICKS = 8;
    public static final int RECOVER_TICKS = 20;
    public static final int CALM_TICKS = 20;
    public static final int COOLDOWN = 40;                // ticks from one strike to the next

    private static final DustParticleOptions VENOM = new DustParticleOptions(0x7FB238, 1.0F);
    private static final int WARY_RATTLE_EVERY = 10;

    private int cooldown;
    private boolean bitten;
    private Vec3 strikeDir = Vec3.ZERO;

    public Rattlecoil(EntityType<? extends Rattlecoil> type, Level level) {
        super(type, level, Step.values());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("rattlecoil", 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
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
    }

    /** It basks in the open in any light, so the light level doesn't steer where it goes or where it may spawn. */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    // ---- the warning -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        if (target != null && !valid(target)) {
            setTarget(null);
            target = null;
        }
        if (core().in(Step.RATTLE)) {
            hold(target);
            if (core().elapsed() % 4 == 0) rattle(level, 1.0F);
        } else if (core().in(Step.WARY)) {
            hold(target);
            double d = target == null ? Double.MAX_VALUE : distanceTo(target);
            if (d > CALM_RANGE) {
                calm(level);
            } else if (cooldown == 0 && d <= STRIKE_RANGE && getSensing().hasLineOfSight(target)) {
                strike(target);
            } else if (core().elapsed() % WARY_RATTLE_EVERY == 0) {
                rattle(level, 0.6F);
            }
        } else if (core().in(Step.STRIKE)) {
            strikeTick(level);
        } else if (core().in(Step.RECOVER) || core().in(Step.CALM)) {
            getNavigation().stop();
        } else if (core().idle()) {
            Player near = level.getNearestPlayer(getX(), getY(), getZ(), WARN_RANGE,
                    e -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e) && getSensing().hasLineOfSight(e));
            if (near != null) warn(near);
        }
    }

    /** Starts the rattle aimed at {@code at}, if it is idle or settling; true if it did. Public as a test hook. */
    public boolean warn(LivingEntity at) {
        if (level().isClientSide() || !(core().idle() || core().in(Step.CALM)) || !valid(at)) return false;
        if (!startPhase(Step.RATTLE, TELL_TICKS, at)) return false;
        setTarget(at);
        face(at.position().subtract(position()));
        return true;
    }

    /** Stays put, facing whoever it warned. */
    private void hold(@Nullable LivingEntity target) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        if (target != null) face(target.position().subtract(position()));
    }

    /** The dry rattle and a puff of dust where the raised tail shakes (curled up on its left, just behind the middle). */
    private void rattle(ServerLevel level, float volume) {
        playSound(ModSounds.RATTLECOIL_RATTLE.get(), volume, 0.95F + random.nextFloat() * 0.1F);
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double lx = Mth.cos(yaw), lz = Mth.sin(yaw);                 // the mob's left
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);                // its front
        double scale = getScale();
        double x = getX() + (lx * 0.35 - fx * 0.1) * scale, z = getZ() + (lz * 0.35 - fz * 0.1) * scale;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState()), x, getY() + 0.6 * scale, z, 3, 0.08, 0.06, 0.08, 0.05);
    }

    private BlockState groundState() {
        return level().getBlockState(blockPosition().below());
    }

    /** The counter landed: whoever it warned backed off. It uncoils and forgets them. */
    private void calm(ServerLevel level) {
        setTarget(null);
        if (!startPhase(Step.CALM, CALM_TICKS, null)) {
            core().end();
            return;
        }
        playSound(ModSounds.RATTLECOIL_CALM.get(), 1.0F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.1, getZ(), 10, 0.4, 0.05, 0.4, 0.02);
    }

    // ---- the strike --------------------------------------------------------------------------------------------------

    /** Lunges at {@code target}; true if the strike started. Public as a test hook. */
    public boolean strike(LivingEntity target) {
        Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0E-4) to = getLookAngle().multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0E-4 || !startPhase(Step.STRIKE, STRIKE_TICKS, target)) return false;
        strikeDir = to.normalize();
        bitten = false;
        cooldown = COOLDOWN;
        face(strikeDir);
        playSound(ModSounds.RATTLECOIL_STRIKE.get(), 1.0F, 1.0F);
        return true;
    }

    private void strikeTick(ServerLevel level) {
        getNavigation().stop();
        if (bitten) return;
        face(strikeDir);
        setDeltaMovement(strikeDir.x * STRIKE_SPEED, getDeltaMovement().y, strikeDir.z * STRIKE_SPEED);
        LivingEntity target = core().target();
        AABB reach = getBoundingBox().inflate(0.3, 0.2, 0.3).move(strikeDir.scale(0.6 * getScale()));
        if (target != null && target.isAlive() && reach.intersects(target.getBoundingBox())) bite(level, target);
    }

    /** The bite at the end of the lunge: its attack damage, then Poison II. */
    private void bite(ServerLevel level, LivingEntity target) {
        bitten = true;
        setDeltaMovement(getDeltaMovement().multiply(0.2, 1.0, 0.2));
        if (MechanicEvent.post(this, "bite", target)) {
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE_HIT;
            boolean hurt = target.hurtServer(level, damageSources().mobAttack(this), damage);
            if (hurt && MechanicEvent.post(this, "venom", target)) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_LEVEL), this);
                level.sendParticles(VENOM, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.25, 0.25, 0.25, 0.0);
            }
        }
        if (!startPhase(Step.RECOVER, RECOVER_TICKS, target)) core().end();
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.RATTLE || ended == Step.RECOVER) {
            if (getTarget() == null || !startPhase(Step.WARY, 0, getTarget())) core().end();   // WARY calms it at once if they're gone
        } else if (ended == Step.STRIKE) {
            if (!startPhase(Step.RECOVER, RECOVER_TICKS, getTarget())) core().end();
        } else {
            core().end();
        }
    }

    /** Hurt by anything living, it rattles at the attacker (and strikes if they stay close); being hurt never skips the rattle. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && source.getEntity() instanceof LivingEntity attacker && attacker != this && valid(attacker)) {
            if (!warn(attacker) && (core().in(Step.WARY) || core().in(Step.RATTLE) || core().in(Step.RECOVER))) setTarget(attacker);
        }
        return hurt;
    }

    private boolean valid(LivingEntity e) {
        return e.isAlive() && !e.isRemoved() && e.level() == level() && e != this
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Ticks until it may strike again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Test hook: lets it strike again at once. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.RATTLECOIL_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.RATTLECOIL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.RATTLECOIL_DEATH.get();
    }
}
