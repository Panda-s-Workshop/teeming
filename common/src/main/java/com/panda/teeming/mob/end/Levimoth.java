package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Levimoth: a pale moth of the End highlands with a weak bite. Threatened (hurt, or a target within 4
 * blocks) it flares its wings, sheds lilac dust and chimes rising for 0.8 s (tell), then it shakes out a lilac dust
 * cloud on the ground below it, its edge traced by a ring: radius 3 for 5 s, Levitation II for 3 s to everything inside
 * but itself. Counter: keep out of the cloud,
 * above all near an edge; Slow Falling makes the drop afterwards harmless.
 */
public class Levimoth extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL, RELEASE }

    public static final int TELL_TICKS = 16;
    public static final int RELEASE_TICKS = 12;
    public static final float CLOUD_RADIUS = 3.0F;
    public static final int CLOUD_TICKS = 100;
    public static final int LEVITATION_TICKS = 60;         // 3 s: a 5-block lift, so the landing hurts even on flat ground
    public static final int LEVITATION_LEVEL = 1;          // amplifier 1 = Levitation II
    public static final double THREAT_RANGE = 4.0;
    public static final int COOLDOWN = 120;
    public static final int GROUND_SEARCH = 6;

    public static final int DUST_COLOR = 0xD9A8FF;
    private static final DustParticleOptions DUST = new DustParticleOptions(DUST_COLOR, 1.4F);
    private static final Telegraph FLARE = Telegraph.particles(DUST, 6, 0.7, 0.4).withSpeed(0.02);
    private static final Telegraph SPARK = Telegraph.particles(ParticleTypes.END_ROD, 2, 0.4, 0.3).withSpeed(0.03);
    private static final Telegraph PUFF = Telegraph.particles(DUST, 40, 1.2, 0.3)
            .withSound(ModSounds.LEVIMOTH_RELEASE.get(), 1.0F, 1.0F);
    private static final int RING_POINTS = 28;

    private int cooldown;
    private @Nullable AreaEffectCloud cloud;

    public Levimoth(EntityType<? extends Levimoth> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("levimoth", 0.3).add(Attributes.FLYING_SPEED, 0.5);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
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
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    // ---- the dust ----------------------------------------------------------------------------------------------------

    /** Starts the flare if it is free to; true if it did. Public as a test hook. */
    public boolean threaten(LivingEntity by) {
        if (cooldown > 0 || !core().idle() || level().isClientSide()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, by)) return false;
        playSound(ModSounds.LEVIMOTH_FLARE.get(), 1.0F, 1.0F);
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        traceRing(level);
        if (core().in(Step.TELL)) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().scale(0.6));
            FLARE.trail(this, 2);
            SPARK.trail(this, 4);
            int t = core().elapsed();
            if (t % 4 == 0) playSound(ModSounds.LEVIMOTH_TELL.get(), 1.0F, 0.8F + 0.05F * t);   // a chime that climbs
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && distanceTo(target) <= THREAT_RANGE && getSensing().hasLineOfSight(target)) threaten(target);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            if (startPhase(Step.RELEASE, RELEASE_TICKS, getTarget())) release(level);
            else rest();
        } else {
            rest();
        }
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
    }

    /** Shakes out the dust cloud on the ground below; returns it, or null if an event cancelled it. Public as a test hook. */
    public AreaEffectCloud release(ServerLevel level) {
        if (!MechanicEvent.post(this, "dust", getTarget())) return null;
        AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), groundY(level), getZ());
        cloud.setRadius(CLOUD_RADIUS);
        cloud.setDuration(CLOUD_TICKS);
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(0.0F);
        cloud.setCustomParticle(DUST);
        cloud.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_TICKS, LEVITATION_LEVEL));
        cloud.setOwner(this);
        level.addFreshEntity(cloud);
        PUFF.play(this);
        this.cloud = cloud;
        return cloud;
    }

    /** Traces the edge of its cloud with a ring of dust every few ticks, so the border shows on any ground. */
    private void traceRing(ServerLevel level) {
        if (cloud == null) return;
        if (!cloud.isAlive()) {
            cloud = null;
            return;
        }
        if (tickCount % 4 != 0) return;
        double turn = tickCount * 0.02;
        for (int i = 0; i < RING_POINTS; i++) {
            double a = turn + i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(DUST, cloud.getX() + Math.cos(a) * CLOUD_RADIUS, cloud.getY() + 0.15,
                    cloud.getZ() + Math.sin(a) * CLOUD_RADIUS, 1, 0.0, 0.05, 0.0, 0.0);
        }
    }

    /** The top of the first solid block within 6 below it, or its own height over the void. */
    private double groundY(ServerLevel level) {
        BlockPos.MutableBlockPos pos = blockPosition().mutable();
        for (int i = 0; i <= GROUND_SEARCH; i++, pos.move(0, -1, 0)) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return pos.getY() + 1.0;
        }
        return getY();
    }

    /** Its own dust doesn't lift it. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.LEVITATION) && super.canBeAffected(effect);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) threaten(source.getEntity() instanceof LivingEntity attacker ? attacker : getTarget());
        return hurt;
    }

    public int cooldown() {
        return cooldown;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return random.nextInt(3) == 0 ? ModSounds.LEVIMOTH_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.LEVIMOTH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.LEVIMOTH_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + random.nextFloat() * 0.1F;
    }
}
