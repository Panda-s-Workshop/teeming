package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.Gauge;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Slagheart Golem: heat 0–12 rises by 1 for every hit it takes and every 3 s while it fights; its cracks
 * glow in four stages (heat / 3). At 12 it shakes for 1 s (tell), smoke tracing the 5-block reach on the ground, and
 * erupts: 10 damage and fire within 5 blocks, a ring of flame along that edge, heat back to 6. Counter: a snowball cools it by 2, splash water by 4, powder snow by 12; at heat 0 it is quenched for
 * 3 s and takes ×1.5.
 */
public class SlagheartGolem extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL, ERUPT, QUENCHED }

    public static final int MAX_HEAT = 12;
    public static final int HEAT_EVERY = 60;
    public static final int AFTER_ERUPTION = 6;
    public static final int TELL_TICKS = 20;
    public static final int ERUPT_TICKS = 10;
    public static final double ERUPT_RADIUS = 5.0;
    private static final int RING_POINTS = 40;
    public static final float ERUPT_DAMAGE = 10.0F;
    public static final int SNOWBALL = 2, SPLASH = 4, POWDER_SNOW = 12;
    public static final int QUENCH_TICKS = 60;
    public static final float QUENCHED_DAMAGE = 1.5F;

    private static final Telegraph STEAM = Telegraph.particles(ParticleTypes.CLOUD, 10, 0.6, 0.6).withSound(ModSounds.SLAGHEART_GOLEM_QUENCH.get(), 1.0F, 1.0F);
    private static final Telegraph RUMBLE = Telegraph.particles(ParticleTypes.LAVA, 3, 0.5, 0.6);

    /**
     * Heat: whole steps from 3, a glow stage per 3 heat, no heat from blows while quenched. At 0 it is quenched; at 12,
     * idle, the eruption starts; the eruption leaves it at 6.
     */
    private final Gauge heat = new Gauge(this, "Heat", MAX_HEAT).whole().from(3)
            .stages(v -> Math.min(3, (int) v / 3))
            .sealedWhen(() -> core().in(Step.QUENCHED))
            .every(HEAT_EVERY, 1)
            .resetTo(AFTER_ERUPTION)
            .onChange(before -> {
                if (heat() == 0 && !core().in(Step.QUENCHED) && startPhase(Step.QUENCHED, QUENCH_TICKS, null)) STEAM.play(this);
            })
            .releaseAt(MAX_HEAT, () -> {
                if (core().idle() && startPhase(Step.TELL, TELL_TICKS, getTarget())) playSound(ModSounds.SLAGHEART_GOLEM_TELL.get(), 1.0F, 1.0F);
            });
    private boolean inPowderSnow;

    public SlagheartGolem(EntityType<? extends SlagheartGolem> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
        heat.show();
    }

    @Override
    public int visualStages() {
        return 4;
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.QUENCHED);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("slagheart_golem", 0.2).add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
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
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- heat ------------------------------------------------------------------------------------------------------

    public int heat() {
        return (int) heat.value();
    }

    /** Sets the heat (0..12) and its glow stage; at 0 it is quenched, at 12 the eruption starts. Public as a test hook. */
    public void setHeat(int value) {
        heat.set(value);
    }

    /** A cooling hit: lowers the heat with a hiss. */
    public void cool(int amount) {
        if (!MechanicEvent.post(this, "cool", null)) return;
        if (!core().in(Step.QUENCHED)) STEAM.play(this);
        if (core().in(Step.TELL)) core().end();     // quenching the tell stops the eruption
        setHeat(heat() - amount);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        boolean snow = level.getBlockState(blockPosition()).is(Blocks.POWDER_SNOW) || level.getBlockState(blockPosition().above()).is(Blocks.POWDER_SNOW);
        if (snow && !inPowderSnow) cool(POWDER_SNOW);
        inPowderSnow = snow;
        if (core().in(Step.TELL) || core().in(Step.ERUPT)) {
            getNavigation().stop();
            RUMBLE.trail(this, 2);
            if (core().in(Step.TELL) && tickCount % 4 == 0) ring(level, ParticleTypes.SMOKE, 0.0);   // how far to run
            return;
        }
        if (core().in(Step.QUENCHED)) {
            getNavigation().stop();
            return;
        }
        heat.tick(getTarget() != null);                   // +1 heat every 3 s while it fights
        if (heat() >= 6 && tickCount % 10 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.8, getZ(), 2, 0.4, 0.3, 0.4, 0.01);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            if (startPhase(Step.ERUPT, ERUPT_TICKS, getTarget())) erupt(level);
            else core().end();
        } else {
            core().end();
            if (ended == Step.ERUPT) heat.reset();
        }
    }

    /** Full heat: everything within 5 blocks takes 10 and catches fire. Returns how many it hit. Public as a test hook. */
    public int erupt(ServerLevel level) {
        int hit = 0;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(ERUPT_RADIUS))) {
            if (victim == this || victim.distanceTo(this) > ERUPT_RADIUS) continue;     // exactly the ring the tell draws
            if (victim instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (!MechanicEvent.post(this, "erupt_hit", victim)) continue;
            if (victim.hurtServer(level, damageSources().onFire(), ERUPT_DAMAGE)) hit++;
            victim.igniteForSeconds(4.0F);
            Push.away(victim, position(), 0.6, 0.35);
        }
        level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1.2, getZ(), 40, 1.0, 0.8, 1.0, 0.2);
        for (int i = 0; i < 60; i++) {                                       // flames fill the reach and never land outside it
            double a = getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(getRandom().nextDouble()) * (ERUPT_RADIUS - 0.4);
            level.sendParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, 1, 0.0, 0.3, 0.0, 0.04);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0, getZ(), 3, 1.0, 0.5, 1.0, 0.0);
        ring(level, ParticleTypes.FLAME, 0.02);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.SLAGHEART_GOLEM_ERUPT.get(), getSoundSource(), 1.0F, 1.0F);
        return hit;
    }

    /** Particles along the ground at the eruption's reach, rising at {@code lift}. */
    private void ring(ServerLevel level, SimpleParticleType particle, double lift) {
        for (int i = 0; i < RING_POINTS; i++) {
            double a = i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(particle, getX() + Math.cos(a) * ERUPT_RADIUS, getY() + 0.2, getZ() + Math.sin(a) * ERUPT_RADIUS,
                    1, 0.0, 0.05, 0.0, lift);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        heat.save(output);
    }

    /** The heat survives a reload; its glow stage comes back with it (a reload mid-eruption ends the eruption). */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        heat.load(input);
    }

    // ---- hits and quenching ----------------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof Snowball) {
            cool(SNOWBALL);
            return false;
        }
        if (direct instanceof AbstractThrownPotion potion && potion.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
            cool(SPLASH);
            return false;
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            cool(1);
            return false;
        }
        boolean hurt = super.hurtServer(level, source, core().in(Step.QUENCHED) ? amount * QUENCHED_DAMAGE : amount);
        if (hurt && !source.is(DamageTypeTags.IS_FIRE)) heat.fill(1);      // sealed while quenched
        return hurt;
    }

    /** Splash water reaches it through this (vanilla hurts water-sensitive mobs with it); so do rain and water. */
    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && heat() >= 6) target.igniteForSeconds(3.0F);
        return hit;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SLAGHEART_GOLEM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SLAGHEART_GOLEM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SLAGHEART_GOLEM_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + random.nextFloat() * 0.1F;
    }
}
