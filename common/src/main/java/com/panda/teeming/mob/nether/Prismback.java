package com.panda.teeming.mob.nether;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Prismback: a low, armoured lizard of the Nether wastes whose back is a ridge of quartz prisms. Any projectile in
 * {@code #teeming:reflectable} that strikes it is turned round and sent straight back the way it came at 0.8 of its
 * speed, owned by the Prismback, so it can hit whoever shot it (a trident keeps its thrower, so Loyalty still brings
 * it home). Its belly is bare: a shot rising into it from below its belly line is not turned.
 *
 * <p>Every 120 ticks its prisms flash for 20 ticks. The tell: for the 10 ticks before, they glimmer, with a crystalline
 * chime and a shower of sparks; then they blaze white-hot and it braces, standing still and biting nothing. A melee
 * blow during the flash is halved, and the other half goes back to the attacker as thorns damage.
 *
 * <p>Counter: hold your blows while it shines. A flash that turns nothing (no blow, no shot) burns its light out: for
 * 40 ticks its prisms are dark and it turns no projectiles at all, so shots strike home. Fight it up close between
 * flashes; ranged attacks are otherwise wasted.
 */
public class Prismback extends TeemingMonster {
    /** At rest, the warning glimmer, the flash (its tell, the "stop hitting" window), its light burnt out. */
    public enum Step implements Phase { IDLE, GLIMMER, FLASH, SPENT }

    /** The flash: 20 ticks of blazing prisms, when blows are turned. */
    public static final int FLASH_TICKS = 20;
    /** One flash starts every 120 ticks. */
    public static final int FLASH_EVERY = 120;
    /** The glimmer before each flash: half a second of warning. */
    public static final int GLIMMER_TICKS = 10;
    /** A turned projectile flies back at this share of the speed it came in at. */
    public static final double REFLECT_SPEED = 0.8;
    /** During a flash this share of a melee blow goes back to the attacker; the rest reaches the Prismback. */
    public static final float MELEE_REFLECT = 0.5F;
    /** A flash that turned nothing leaves it spent for 2 s: dark prisms, no projectiles turned. */
    public static final int SPENT_TICKS = 40;
    /** Its bare belly: a shot rising into it at or below this height over its feet (in blocks) gets through. */
    public static final double BELLY = 0.2;
    /** A fresh Prismback's first glimmer comes this many ticks after it wakes, plus up to {@link #FIRST_SPREAD} more. */
    public static final int FIRST_GLIMMER = 20;
    public static final int FIRST_SPREAD = 40;

    /** What its prisms turn; packs extend the tag. */
    public static final TagKey<EntityType<?>> REFLECTABLE = TagKey.create(Registries.ENTITY_TYPE, TeemingMod.id("reflectable"));

    /** The flash's warm white-hot: kept well off the weak-point gold, since a flash means "stop hitting". */
    public static final int FLASH_COLOR = 0xFFF3E9;
    private static final DustParticleOptions FLASH_DUST = new DustParticleOptions(FLASH_COLOR, 0.8F);
    private static final DustParticleOptions SPARK_DUST = new DustParticleOptions(0xFFE6D2, 0.7F);

    private static final int GLOW_REST = 0, GLOW_GLIMMER = 1, GLOW_FLASH = 2, GLOW_SPENT = 3;

    private static final Telegraph GLIMMER = Telegraph.particles(ParticleTypes.ELECTRIC_SPARK, 6, 0.3, 0.85).withSpeed(0.05)
            .withSound(ModSounds.PRISMBACK_GLIMMER.get(), 1.0F, 1.0F);
    private static final Telegraph FLASH = Telegraph.particles(FLASH_DUST, 9, 0.3, 0.9).withSpeed(0.0)
            .withSound(ModSounds.PRISMBACK_FLASH.get(), 1.0F, 1.0F);
    private static final Telegraph SPENT = Telegraph.particles(ParticleTypes.SMOKE, 8, 0.3, 0.85).withSpeed(0.02)
            .withSound(ModSounds.PRISMBACK_SPENT.get(), 1.0F, 1.0F);

    /** Game time the current cycle's flash started (or is reckoned from); {@link Long#MIN_VALUE} until it first wakes. */
    private long flashAt = Long.MIN_VALUE;
    /** Whether the flash under way has turned anything yet. */
    private boolean fed;
    /** The phase the glow stage was last set for. */
    private int stagedPhase = -1;
    /** The last turned projectile's motion as it came in and as it left (test hooks read them). */
    private Vec3 lastIn = Vec3.ZERO;
    private Vec3 lastOut = Vec3.ZERO;

    public Prismback(EntityType<? extends Prismback> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("prismback", 0.22).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return canBite() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return canBite() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** It bites at rest, through the glimmer and while spent; braced in the flash, it bites nothing. */
    private boolean canBite() {
        return !core().in(Step.FLASH);
    }

    @Override
    public int visualStages() {
        return 4;
    }

    /** Braced in the flash: it only falls, and nothing pushes or knocks it aside. */
    @Override
    public Pin pin() {
        return core() != null && core().in(Step.FLASH) ? Pin.ROOTED : Pin.FREE;   // null while the Entity constructor runs
    }

    // ---- the flash cycle -------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!isNoAi() && isAlive()) cycle(level);
        phaseParticles(level);
        if (phase().ordinal() != stagedPhase) {                 // the glow follows each change of phase
            stagedPhase = phase().ordinal();
            core().setStage(glowStage());
        }
    }

    /** Glints at rest; brighter in the glimmer; blazing in the flash; dark when spent. */
    private int glowStage() {
        if (core().in(Step.GLIMMER)) return GLOW_GLIMMER;
        if (core().in(Step.FLASH)) return GLOW_FLASH;
        if (core().in(Step.SPENT)) return GLOW_SPENT;
        return GLOW_REST;
    }

    /**
     * Starts each glimmer so that its flash begins exactly {@link #FLASH_EVERY} ticks after the last one. A glimmer that
     * is refused (an event, a phase in the way) skips that flash; the next one keeps the beat.
     */
    private void cycle(ServerLevel level) {
        long now = level.getGameTime();
        if (flashAt == Long.MIN_VALUE) {
            flashAt = now - (FLASH_EVERY - GLIMMER_TICKS) + FIRST_GLIMMER + random.nextInt(FIRST_SPREAD + 1);
        }
        if (now - flashAt < FLASH_EVERY - GLIMMER_TICKS) return;
        if (!core().idle() || !glimmer()) flashAt += FLASH_EVERY;
    }

    /** Starts the glimmer now: the chime and the sparks. Returns false if it was refused. Public as a test hook. */
    public boolean glimmer() {
        if (!(level() instanceof ServerLevel)) return false;
        flashAt = level().getGameTime() + GLIMMER_TICKS;          // the flash this glimmer leads to
        if (!startPhase(Step.GLIMMER, GLIMMER_TICKS, getTarget())) return false;
        GLIMMER.play(this);
        return true;
    }

    /** Starts a flash now, unfed. Returns false if it was refused. Public as a test hook. */
    public boolean flash() {
        if (!(level() instanceof ServerLevel)) return false;
        flashAt = level().getGameTime();
        fed = false;
        if (!startPhase(Step.FLASH, FLASH_TICKS, getTarget())) return false;
        getNavigation().stop();
        FLASH.play(this);
        return true;
    }

    /** Sets the cycle so its next glimmer starts {@code ticks} from now. Public as a test hook. */
    public void glimmerIn(int ticks) {
        flashAt = level().getGameTime() - (FLASH_EVERY - GLIMMER_TICKS) + ticks;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.GLIMMER) {
            if (!flash()) core().end();
        } else if (ended == Step.FLASH) {
            boolean burntOut = !fed;
            fed = false;
            if (burntOut && startPhase(Step.SPENT, SPENT_TICKS, core().target())) SPENT.play(this);
            else core().end();
        } else {
            core().end();
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().in(Step.FLASH)) {                               // braced and shining: it stands still
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
    }

    /** The glimmer's sparks, the flash's light and the smoke of a spent ridge, from the phase alone. */
    private void phaseParticles(ServerLevel level) {
        double y = getY() + getBbHeight() * 0.9;
        if (core().in(Step.GLIMMER) && tickCount % 2 == 0) {
            level.sendParticles(SPARK_DUST, getX(), y, getZ(), 1, 0.3, 0.1, 0.3, 0.0);
        } else if (core().in(Step.FLASH)) {
            FLASH.trail(this, 4);
            if (tickCount % 4 == 2) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), y + 0.1, getZ(), 1, 0.3, 0.1, 0.3, 0.03);
        } else if (core().in(Step.SPENT) && tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), y, getZ(), 1, 0.25, 0.05, 0.25, 0.005);
        }
    }

    // ---- turning projectiles ---------------------------------------------------------------------------------------

    /**
     * True if its prisms turn {@code projectile} now: it is in the tag, the Prismback is alive and not spent, and the
     * shot is not rising into its bare belly.
     */
    public boolean turns(Projectile projectile) {
        return isAlive() && projectile.is(REFLECTABLE) && !core().in(Step.SPENT) && !intoBelly(projectile);
    }

    /** A shot rising into it at or below its belly line. */
    public boolean intoBelly(Projectile projectile) {
        return projectile.getDeltaMovement().y > 0.0 && projectile.getY() <= getY() + BELLY * getScale();
    }

    /**
     * Vanilla asks this before a projectile hits, so a turned shot never lands, pierces or plays its hit. The new owner
     * is set here because vanilla reads the owner back right after this returns and hands it to the deflection.
     */
    @Override
    public ProjectileDeflection deflection(Projectile projectile) {
        if (!turns(projectile)) return super.deflection(projectile);
        if (level().isClientSide()) return REFLECT;
        if (projectile.getOwner() == this) return REFLECT;          // already turned by it: it passes on
        LivingEntity shooter = projectile.getOwner() instanceof LivingEntity l ? l : null;
        if (!MechanicEvent.post(this, "reflect", shooter)) return super.deflection(projectile);
        if (!(projectile instanceof ThrownTrident)) projectile.setOwner(this);
        if (core().in(Step.FLASH)) fed = true;
        return REFLECT;
    }

    /** Straight back the way it came at 0.8 of its speed, facing its new heading. Runs on the server only. */
    private static final ProjectileDeflection REFLECT = (projectile, by, random, scale) -> {
        Vec3 in = projectile.getDeltaMovement();
        Vec3 out = in.scale(-REFLECT_SPEED);
        projectile.setDeltaMovement(out);
        projectile.setYRot(projectile.getYRot() + 180.0F);
        projectile.yRotO += 180.0F;
        projectile.setXRot(-projectile.getXRot());
        projectile.xRotO = -projectile.xRotO;
        Push.sync(projectile);
        if (by instanceof Prismback prismback) prismback.turned(projectile, in, out);
    };

    private void turned(Projectile projectile, Vec3 in, Vec3 out) {
        lastIn = in;
        lastOut = out;
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = projectile.position();
        level.sendParticles(FLASH_DUST, at.x, at.y, at.z, 8, 0.12, 0.12, 0.12, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.15);
        level.playSound(null, at.x, at.y, at.z, ModSounds.PRISMBACK_REFLECT.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** The last turned projectile's motion as it came in. Public as a test hook. */
    public Vec3 lastReflectIn() {
        return lastIn;
    }

    /** The last turned projectile's motion as it left. Public as a test hook. */
    public Vec3 lastReflectOut() {
        return lastOut;
    }

    // ---- turning blows ---------------------------------------------------------------------------------------------

    /** A melee blow: a strike by a living attacker's own hand, claw, sting or weapon. */
    public static boolean melee(DamageSource source) {
        return source.isDirect() && source.getEntity() instanceof LivingEntity
                && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                    || source.is(DamageTypes.SPEAR) || source.is(DamageTypes.MACE_SMASH) || source.is(DamageTypes.STING));
    }

    /**
     * During a flash a melee blow is halved and the other half goes back to the attacker (thorns damage, from the
     * Prismback). Its own turned projectiles never hurt it.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() == this) return false;
        if (core().in(Step.FLASH) && amount > 0.0F && melee(source) && !isDeadOrDying() && !isInvulnerableTo(level, source)) {
            LivingEntity attacker = (LivingEntity) source.getEntity();
            if (MechanicEvent.post(this, "reflect_blow", attacker)) {
                float back = amount * MELEE_REFLECT;
                boolean hurt = super.hurtServer(level, source, amount - back);
                if (hurt) {
                    fed = true;
                    attacker.damageCooldownTime = 0;                   // its own last hit must not swallow the half it sent
                    attacker.hurtServer(level, damageSources().thorns(this), back);
                    double y = getY() + getBbHeight() * 0.9;
                    level.sendParticles(FLASH_DUST, getX(), y, getZ(), 12, 0.35, 0.15, 0.35, 0.0);
                    level.sendParticles(FLASH_DUST, attacker.getX(), attacker.getY(0.6), attacker.getZ(), 10, 0.25, 0.3, 0.25, 0.0);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, attacker.getX(), attacker.getY(0.6), attacker.getZ(), 6, 0.2, 0.3, 0.2, 0.1);
                    level.playSound(null, getX(), y, getZ(), ModSounds.PRISMBACK_REFLECT_BLOW.get(), getSoundSource(), 1.0F, 1.0F);
                }
                return hurt;
            }
        }
        return super.hurtServer(level, source, amount);
    }

    /** Whether the flash under way has turned anything yet. */
    public boolean fed() {
        return fed;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.PRISMBACK_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PRISMBACK_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PRISMBACK_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.15F;
    }
}
