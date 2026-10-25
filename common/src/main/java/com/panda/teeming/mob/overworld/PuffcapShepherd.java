package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Puffcap Shepherd: a little mushroom-folk of the dark forest that tends the monsters round it and keeps away from
 * you. Whenever it or a monster near it is in a fight, it swells its cap for 0.8 s (tell: the cap puffs up, sheds
 * spores, a soft swelling sound, a ring of spores marks its 6-block reach), then puffs: every hostile mob within 6
 * blocks gets Regeneration I and Speed I for 5 s (undead mobs ignore Regeneration, as they always do, and only get
 * faster). It never buffs players, passive mobs or itself. It puffs at most once every 8 s.
 *
 * <p>It runs from survival players within 6 blocks and otherwise trails its target from about 8 blocks back; only
 * when cornered (its target within 2 blocks) does it swat with its crook. Counter: reach it and kill it first, or hit
 * it while its cap swells: any hit that hurts it then bursts the cap early, with no buff for anyone, and it reels on
 * the spot for 2 s; its next puff is a full 8 s away.
 */
public class PuffcapShepherd extends TeemingMonster {
    /** IDLE, the cap swelling (tell), the puff, the burst cap (counter landed: it reels). */
    public enum Step implements Phase { IDLE, SWELL, PUFF, BURST }

    public static final int TELL_TICKS = 16;              // 0.8 s of swelling before the puff
    public static final int PUFF_TICKS = 10;
    /** Ticks from one puff to the next, at the soonest. */
    public static final int PUFF_EVERY = 160;
    public static final double PUFF_RADIUS = 6.0;
    public static final int BUFF_TICKS = 100;             // 5 s of Regeneration and Speed for the pack
    public static final int REGENERATION_LEVEL = 0;       // amplifier 0 = Regeneration I
    public static final int SPEED_LEVEL = 0;              // amplifier 0 = Speed I
    /** It runs from a survival player this close. */
    public static final double FLEE_RANGE = 6.0;
    /** It follows its target until it is this close, so it stays just outside its flee range. */
    public static final double FOLLOW_RANGE = 8.0;
    /** A target this close has cornered it: it swats with its crook. */
    public static final double CORNERED_RANGE = 2.0;
    /** A hit during the swell bursts the cap: it reels on the spot for 2 s. */
    public static final int BURST_TICKS = 40;
    /** A fresh shepherd's first puff can come this soon. */
    public static final int FIRST_PUFF = 40;

    public static final int SPORE_COLOR = 0xB06A8A;
    public static final int GLOW_SPORE_COLOR = 0x8EE8D0;
    private static final DustParticleOptions SPORES = new DustParticleOptions(SPORE_COLOR, 1.2F);
    private static final DustParticleOptions GLOW_SPORES = new DustParticleOptions(GLOW_SPORE_COLOR, 1.2F);
    private static final Telegraph SHED = Telegraph.particles(SPORES, 6, 0.45, 0.85).withSpeed(0.01);
    private static final Telegraph SHED_GLOW = Telegraph.particles(GLOW_SPORES, 6, 0.45, 0.85).withSpeed(0.01);
    private static final DustParticleOptions CLOUD = new DustParticleOptions(SPORE_COLOR, 0.9F);
    private static final DustParticleOptions GLOW_CLOUD = new DustParticleOptions(GLOW_SPORE_COLOR, 0.9F);
    private static final int RING_POINTS = 40;

    /** Ticks until it may puff again. */
    private int untilPuff = FIRST_PUFF;
    /** Set in registerGoals, which runs inside the Mob constructor, so it has no initializer. */
    private @Nullable WaterAvoidingRandomStrollGoal stroll;

    public PuffcapShepherd(EntityType<? extends PuffcapShepherd> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("puffcap_shepherd", 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, (float) FLEE_RANGE, 1.0, 1.3,
                e -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e)) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
            @Override
            public boolean canUse() {
                return core().idle() && cornered(CORNERED_RANGE) && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && cornered(CORNERED_RANGE + 0.5) && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(3, new FollowFightGoal());
        stroll = new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        };
        goalSelector.addGoal(5, stroll);
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private boolean cornered(double range) {
        LivingEntity target = getTarget();
        return target != null && target.isAlive() && distanceTo(target) <= range;
    }

    /** Trails its target until it is {@link #FOLLOW_RANGE} away: close enough to keep the fight inside its puff. */
    private final class FollowFightGoal extends Goal {
        private int repath;

        FollowFightGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return core().idle() && target != null && target.isAlive() && distanceTo(target) > FOLLOW_RANGE + 2.0;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = getTarget();
            return core().idle() && target != null && target.isAlive() && distanceTo(target) > FOLLOW_RANGE;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target != null && --repath <= 0) {
                repath = 10;
                getNavigation().moveTo(target, 1.0);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    // ---- the swell and the puff ---------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide() && untilPuff > 0) untilPuff--;
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (core().in(Step.SWELL)) {
            int t = core().elapsed();
            (glowcap() ? SHED_GLOW : SHED).trail(this, 2);
            if (t % 4 == 0) ring(level, 0.0);                                  // how far the puff reaches
            if (t > 0 && t % 5 == 0) playSound(ModSounds.PUFFCAP_SHEPHERD_SWELL.get(), 0.6F, 0.9F + 0.08F * (t / 5));
        } else if (core().idle() && !isNoAi() && untilPuff <= TELL_TICKS && alerted(level)) {
            swell(getTarget());
        }
    }

    /** In a fight: it has a target, or a monster within its reach has one. Without a monster to tend it never puffs. */
    private boolean alerted(ServerLevel level) {
        List<Mob> flock = flock(level);
        if (flock.isEmpty()) return false;
        if (getTarget() != null && getTarget().isAlive()) return true;
        for (Mob mob : flock) if (mob.getTarget() != null && mob.getTarget().isAlive()) return true;
        return false;
    }

    /** Every hostile mob within the puff's reach, but itself. Players and passive mobs are never part of it. */
    public List<Mob> flock(ServerLevel level) {
        return level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(PUFF_RADIUS),
                e -> e != this && e.isAlive() && e instanceof Enemy && e.distanceTo(this) <= PUFF_RADIUS);
    }

    /** Starts the swell if it is free to; true if it did. Ignores the time since the last puff. Public as a test hook. */
    public boolean swell(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle()) return false;
        if (!startPhase(Step.SWELL, TELL_TICKS, target)) return false;
        playSound(ModSounds.PUFFCAP_SHEPHERD_SWELL.get(), 1.0F, 0.8F);
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!core().idle()) {                                                  // it stands still to swell, puff or reel
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.SWELL && level() instanceof ServerLevel level) {
            untilPuff = PUFF_EVERY;
            if (startPhase(Step.PUFF, PUFF_TICKS, core().target())) puff(level);
            else core().end();
        } else {
            core().end();
        }
    }

    /**
     * The puff: Regeneration I and Speed I for 100 ticks to every hostile mob within 6 blocks. Returns how many it
     * buffed. Public as a test hook.
     */
    public int puff(ServerLevel level) {
        DustParticleOptions spores = glowcap() ? GLOW_SPORES : SPORES;
        int buffed = 0;
        for (Mob mob : flock(level)) {
            if (!MechanicEvent.post(this, "spores", mob)) continue;
            mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BUFF_TICKS, REGENERATION_LEVEL), this);
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, BUFF_TICKS, SPEED_LEVEL), this);
            level.sendParticles(spores, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 12, 0.3, 0.4, 0.3, 0.0);
            buffed++;
        }
        double y = getY() + getBbHeight() * 0.8;
        DustParticleOptions cloud = glowcap() ? GLOW_CLOUD : CLOUD;
        for (int i = 0; i < 40; i++) {                                         // a thin cloud that fills the reach, never past it
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * (PUFF_RADIUS - 0.3);
            level.sendParticles(cloud, getX() + Math.cos(a) * r, getY() + 0.2 + random.nextDouble() * 1.6,
                    getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(spores, getX(), y, getZ(), 16, 0.6, 0.3, 0.6, 0.01);
        ring(level, 0.02);
        level.playSound(null, getX(), y, getZ(), ModSounds.PUFFCAP_SHEPHERD_PUFF.get(), getSoundSource(), 1.2F, 1.0F);
        return buffed;
    }

    /** Spores round it at the puff's reach, just above the ground. */
    private void ring(ServerLevel level, double speed) {
        DustParticleOptions spores = glowcap() ? GLOW_SPORES : SPORES;
        double turn = tickCount * 0.03;
        for (int i = 0; i < RING_POINTS; i++) {
            double a = turn + i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(spores, getX() + Math.cos(a) * PUFF_RADIUS, getY() + 0.15, getZ() + Math.sin(a) * PUFF_RADIUS,
                    1, 0.0, 0.05, 0.0, speed);
        }
    }

    // ---- the counter: a hit bursts the swelling cap -------------------------------------------------------------------

    /** Any hit that hurts it while its cap swells bursts the cap: no puff, it reels for 40 ticks, and the wait restarts. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && core().in(Step.SWELL)) burst(level, source.getEntity() instanceof LivingEntity by ? by : null);
        return hurt;
    }

    private void burst(ServerLevel level, @Nullable LivingEntity by) {
        if (!startPhase(Step.BURST, BURST_TICKS, by)) return;              // a vetoed burst leaves the swell running
        untilPuff = PUFF_EVERY;
        double y = getY() + getBbHeight() * 0.8;
        level.sendParticles(ParticleTypes.POOF, getX(), y, getZ(), 12, 0.35, 0.2, 0.35, 0.03);
        level.sendParticles(glowcap() ? GLOW_SPORES : SPORES, getX(), y, getZ(), 16, 0.5, 0.3, 0.5, 0.0);
        level.playSound(null, getX(), y, getZ(), ModSounds.PUFFCAP_SHEPHERD_BURST.get(), getSoundSource(), 1.0F, 1.0F);
    }

    private boolean glowcap() {
        return "glowcap".equals(look());
    }

    /** Ticks until it may puff again. */
    public int untilPuff() {
        return untilPuff;
    }

    /** Sets the ticks until it may puff again. Public as a test hook. */
    public void setUntilPuff(int ticks) {
        untilPuff = Math.max(0, ticks);
    }

    /** Test hook: stops it wandering, so only its fleeing, following and swatting move it. */
    public void stayPut() {
        if (stroll != null) goalSelector.removeGoal(stroll);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.PUFFCAP_SHEPHERD_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PUFFCAP_SHEPHERD_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PUFFCAP_SHEPHERD_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.1F + random.nextFloat() * 0.2F;
    }
}
