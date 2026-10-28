package com.panda.teeming.mob.overworld;

import com.panda.teeming.advancement.TeemingAdvancements;
import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAnimal;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Thornhorn: a plains grazer, passive until hurt, then the herd turns on the attacker. Tell: it lowers its
 * horns and paws the ground for 1 s. Mechanic: a straight charge at 0.45 b/t for up to 14 blocks; a hit does 1.5×Atk
 * and knocks back 1.6. Counter: sidestep so it charges a wall, which stuns it for 3 s and makes it take ×1.5.
 */
public class Thornhorn extends TeemingAnimal {
    public enum Step implements Phase { IDLE, TELL, CHARGE, RECOVER, STUNNED }

    public static final int TELL_TICKS = 20;
    public static final double CHARGE_SPEED = 0.45;
    public static final int CHARGE_TICKS = 32;          // 14 blocks at 0.45 b/t
    public static final double MIN_RANGE = 4.0;
    public static final double MAX_RANGE = 14.0;
    public static final float CHARGE_HIT = 1.5F;        // × attack damage
    public static final double KNOCKBACK = 1.6;
    public static final int STUN_TICKS = 60;
    public static final float STUN_DAMAGE = 1.5F;
    public static final int RECOVER_TICKS = 20;
    public static final int COOLDOWN = 100;

    private static final DustParticleOptions STAR = new DustParticleOptions(0xFFD36B, 1.1F);
    private static final int STARS = 4;

    private int cooldown;
    private Vec3 chargeDir = Vec3.ZERO;

    public Thornhorn(EntityType<? extends Thornhorn> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;                                      // fightable when provoked: the Overworld band's low end
    }

    /** Gold stars circling over its head while it is stunned, one lap a second. */
    private void circleStars(ServerLevel level) {
        if (tickCount % 2 != 0) return;
        Vec3 head = position().add(getLookAngle().multiply(1, 0, 1).scale(getBbWidth() * 0.45)).add(0, getBbHeight() + 0.25, 0);
        double turn = core().elapsed() * (Math.PI * 2 / 20);
        for (int i = 0; i < STARS; i++) {
            double a = turn + i * (Math.PI * 2 / STARS);
            level.sendParticles(STAR, head.x + Math.cos(a) * 0.55, head.y, head.z + Math.sin(a) * 0.55, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.STUNNED);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.animal("thornhorn", 0.23).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    // ---- the charge ------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        if (core().in(Step.TELL)) {
            getNavigation().stop();
            if (target != null) face(target.position().subtract(position()));
            if (tickCount % 4 == 0) {
                Telegraph.particles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().below())), 6, 0.35, 0.05).play(this);
            }
        } else if (core().in(Step.CHARGE)) {
            chargeTick(level);
        } else if (core().in(Step.STUNNED)) {
            getNavigation().stop();
            circleStars(level);
        } else if (core().in(Step.RECOVER)) {
            getNavigation().stop();
        } else if (canCharge(target)) {
            if (startPhase(Step.TELL, TELL_TICKS, target)) playSound(ModSounds.THORNHORN_TELL.get(), 1.0F, 1.0F);
        }
    }

    private boolean canCharge(LivingEntity target) {
        if (target == null || !target.isAlive() || cooldown > 0 || !onGround() || isInWater()) return false;
        double d = distanceTo(target);
        return d >= MIN_RANGE && d <= MAX_RANGE && getSensing().hasLineOfSight(target);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL) {
            LivingEntity target = getTarget();
            Vec3 to = target == null ? Vec3.ZERO : target.position().subtract(position()).multiply(1, 0, 1);
            if (to.lengthSqr() < 1.0E-4 || !startPhase(Step.CHARGE, CHARGE_TICKS, target)) {
                rest();
                return;
            }
            chargeDir = to.normalize();
            face(chargeDir);
        } else if (ended == Step.CHARGE) {
            startPhase(Step.RECOVER, RECOVER_TICKS, null);
        } else {
            rest();
        }
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
    }

    private void chargeTick(ServerLevel level) {
        getNavigation().stop();
        face(chargeDir);
        setDeltaMovement(chargeDir.x * CHARGE_SPEED, getDeltaMovement().y, chargeDir.z * CHARGE_SPEED);
        if (tickCount % 3 == 0) {
            Telegraph.particles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().below())), 4, 0.4, 0.05).play(this);
        }
        AABB reach = getBoundingBox().inflate(0.3).move(chargeDir.scale(0.5));
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, reach, this::canGore)) {
            if (MechanicEvent.post(this, "charge_hit", victim)) {
                DamageSource source = damageSources().mobAttack(this);
                float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * CHARGE_HIT;
                victim.hurtServer(level, source, damage);
                victim.knockback(KNOCKBACK * Charms.chargeThrow(victim), -chargeDir.x, -chargeDir.z, source, damage);
                Push.sync(victim);
            }
            level.playSound(null, getX(), getY(), getZ(), ModSounds.THORNHORN_RAM.get(), getSoundSource(), 1.0F, 1.0F);
            startPhase(Step.RECOVER, RECOVER_TICKS, null);
            return;
        }
        if (horizontalCollision && core().elapsed() > 2) stun(level);
    }

    private boolean canGore(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Thornhorn) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /**
     * The counter landed: it ran into a wall. A charge at a player that ends this way earns that player the Sidestep
     * advancement: the Thornhorn was charging (not posed or stunned by a hook) and the one it charged is a player.
     * Public as a test hook.
     */
    public void stun(ServerLevel level) {
        LivingEntity charged = core().in(Step.CHARGE) ? core().target() : null;
        if (!startPhase(Step.STUNNED, STUN_TICKS, null)) return;
        if (charged instanceof ServerPlayer player && player.isAlive()) TeemingAdvancements.award(player, TeemingAdvancements.THORNHORN_WALL);
        setDeltaMovement(-chargeDir.x * 0.2, 0.2, -chargeDir.z * 0.2);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.THORNHORN_STUNNED.get(), getSoundSource(), 1.0F, 1.0F);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.THORNHORN_HORN_CRACK.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().relative(getDirection()))),
                getX() + chargeDir.x, getY() + 0.8, getZ() + chargeDir.z, 20, 0.3, 0.3, 0.3, 0.1);
    }

    private void face(Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return super.hurtServer(level, source, core().in(Step.STUNNED) ? amount * STUN_DAMAGE : amount);
    }

    /** Test hook: lets it charge again at once. */
    /** Ticks until it may charge again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    public void resetCooldown() {
        cooldown = 0;
    }

    public Vec3 chargeDir() {
        return chargeDir;
    }

    // ---- sounds (goat, pitched low, until the Teeming sound set lands) ---------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.THORNHORN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.THORNHORN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.THORNHORN_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + random.nextFloat() * 0.1F;
    }
}
