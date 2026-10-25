package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Scarlet Mantis: a tall crimson mantis of the crimson forests with two great scythe arms. Up close it slashes.
 *
 * <p>In a fight, about every 60 ticks (between 52 and 68, so the beat can't be counted) it takes a parry stance. The
 * tell: for 12 ticks it stops and raises its scythes crossed before its face, whetting them with a rasp, red sparks
 * flying off the edges. Then it holds the stance for 24 ticks, rooted, blades glowing red, slashing nothing. A melee
 * blow in the stance does nothing at all: the scythes catch it with a clink and the attacker staggers, with Slowness
 * III and no attacks for 30 ticks. Shots are not caught: arrows, tridents and anything thrown land as usual.
 *
 * <p>The stagger's "no attacks": the mantis refuses every blow from an attacker it staggered until the stagger ends
 * (it tracks them itself), and the attacker gets Weakness V for the same 30 ticks, which floors any attack's base
 * damage at 0, so an unenchanted swing at anything else lands nothing (a player's swing that would deal 0 is not
 * an attack at all; a mob's deals 0).
 *
 * <p>Counter: hold your blows while its blades glow and strike when it lowers them, or switch to a bow, since the
 * stance stops nothing that flies. A shot that lands in the stance pierces its guard with a crack of chitin.
 */
public class ScarletMantis extends TeemingMonster {
    /** At rest (slashing up close), the tell (scythes rising crossed), the parry stance. */
    public enum Step implements Phase { IDLE, TELL, STANCE }

    /** The tell: 12 ticks raising the scythes crossed before its face. */
    public static final int TELL_TICKS = 12;
    /** The stance: 24 ticks with the scythes held crossed, when every melee blow is caught. */
    public static final int STANCE_TICKS = 24;
    /** One stance starts about every 60 ticks in a fight, counted from the start of one stance to the next... */
    public static final int STANCE_EVERY = 60;
    /** ...give or take up to 8 ticks, picked afresh each time, so the beat can't be counted. */
    public static final int STANCE_SPREAD = 8;
    /** A fresh fight's first tell starts this many ticks after it picks a foe, plus up to {@link #FIRST_SPREAD} more. */
    public static final int FIRST_TELL = 20;
    public static final int FIRST_SPREAD = 20;

    /** A caught blow staggers the attacker for 30 ticks: slowed, and no attacks. */
    public static final int STAGGER_TICKS = 30;
    /** Slowness III (amplifier 2): -45 % speed. */
    public static final int STAGGER_SLOWNESS = 2;
    /** Weakness V (amplifier 4): -20 attack damage, which floors the base damage of any attack at 0. */
    public static final int STAGGER_WEAKNESS = 4;
    /** The blades snap shut on a caught blow for this many ticks (the model's clink). */
    public static final int PARRY_SNAP_TICKS = 6;

    /** The glints' hot red: kept far from the weak-point gold, since glowing blades mean "stop hitting". */
    public static final int GLINT_COLOR = 0xFF3048;
    private static final DustParticleOptions GLINT_DUST = new DustParticleOptions(GLINT_COLOR, 0.8F);
    private static final DustParticleOptions CLINK_DUST = new DustParticleOptions(0xFFE6EA, 0.6F);
    private static final DustParticleOptions DAZE_DUST = new DustParticleOptions(0xC02034, 0.6F);
    private static final DustParticleOptions CHITIN_DUST = new DustParticleOptions(0x7A1820, 1.0F);

    private static final int GLOW_REST = 0, GLOW_TELL = 1, GLOW_STANCE = 2;
    /** Synced flag bit: the blades just caught a blow. */
    public static final int FLAG_PARRIED = 0;

    private static final Telegraph TELL = Telegraph.particles(GLINT_DUST, 8, 0.35, 0.8).withSpeed(0.0)
            .withSound(ModSounds.SCARLET_MANTIS_TELL.get(), 1.0F, 1.0F);
    private static final Telegraph STANCE = Telegraph.particles(GLINT_DUST, 10, 0.3, 0.85).withSpeed(0.0)
            .withSound(ModSounds.SCARLET_MANTIS_STANCE.get(), 1.0F, 1.0F);

    /** One staggered attacker and the game time its stagger ends. */
    private record Stagger(LivingEntity who, long until) {}

    /** Game time the next stance is due to start; {@link Long#MIN_VALUE} while it is out of a fight. */
    private long stanceAt = Long.MIN_VALUE;
    /** Everyone it staggered whose stagger has not ended yet. */
    private final List<Stagger> staggered = new ArrayList<>();
    /** Game time the parry snap ends. */
    private long snapUntil = Long.MIN_VALUE;
    /** The phase the glow stage was last set for. */
    private int stagedPhase = -1;
    /** Blows caught so far (a test hook reads it). */
    private int parries;

    public ScarletMantis(EntityType<? extends ScarletMantis> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("scarlet_mantis", 0.26);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true) {
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
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public int visualStages() {
        return 3;
    }

    /** Raising its guard and holding it, it only falls: nothing pushes or knocks it aside. */
    @Override
    public Pin pin() {
        return core() != null && !core().idle() ? Pin.ROOTED : Pin.FREE;   // null while the Entity constructor runs
    }

    // ---- the stance cycle ------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!isNoAi() && isAlive()) cycle(level);
        phaseParticles(level);
        dazed(level);
        int want = core().in(Step.STANCE) ? GLOW_STANCE : core().in(Step.TELL) ? GLOW_TELL : GLOW_REST;
        // the glow follows each change of phase, and a mob with its wits about it never keeps a stale one (a mob
        // without AI keeps whatever stage it was set to, so it can be posed)
        if (phase().ordinal() != stagedPhase || (!isNoAi() && core().stage() != want)) {
            stagedPhase = phase().ordinal();
            core().setStage(want);
        }
        if (core().flag(FLAG_PARRIED) && level.getGameTime() >= snapUntil) core().setFlag(FLAG_PARRIED, false);
    }

    /** True while it has a living foe: it only takes the stance in a fight. */
    private boolean inFight() {
        LivingEntity foe = getTarget();
        return foe != null && foe.isAlive();
    }

    /**
     * Starts each tell so that its stance begins when due. Out of a fight the beat is forgotten; a fresh fight's first
     * tell comes 20 to 40 ticks in. A tell that is refused (an event, a phase in the way) skips that stance; the next
     * one is due a fresh interval later.
     */
    private void cycle(ServerLevel level) {
        long now = level.getGameTime();
        if (!inFight()) {
            stanceAt = Long.MIN_VALUE;
            return;
        }
        if (stanceAt == Long.MIN_VALUE) stanceAt = now + TELL_TICKS + FIRST_TELL + random.nextInt(FIRST_SPREAD + 1);
        if (now < stanceAt - TELL_TICKS) return;
        if (!core().idle() || !tell()) stanceAt += interval();
    }

    /** Ticks from one stance's start to the next: 60, give or take up to 8. */
    private int interval() {
        return STANCE_EVERY - STANCE_SPREAD + random.nextInt(2 * STANCE_SPREAD + 1);
    }

    /** Starts the tell now: the scythes rise, the rasp, red sparks. Returns false if it was refused. Public as a test hook. */
    public boolean tell() {
        if (!(level() instanceof ServerLevel)) return false;
        stanceAt = level().getGameTime() + TELL_TICKS;           // the stance this tell leads to
        if (!startPhase(Step.TELL, TELL_TICKS, getTarget())) return false;
        getNavigation().stop();
        TELL.play(this);
        return true;
    }

    /** Takes the stance now and sets when the next one is due. Returns false if it was refused. Public as a test hook. */
    public boolean stance() {
        if (!(level() instanceof ServerLevel)) return false;
        stanceAt = level().getGameTime() + interval();
        if (!startPhase(Step.STANCE, STANCE_TICKS, getTarget())) return false;
        getNavigation().stop();
        STANCE.play(this);
        return true;
    }

    /** Sets the cycle so its next stance starts {@code ticks} from now (its tell 12 ticks before). Public as a test hook. */
    public void stanceIn(int ticks) {
        stanceAt = level().getGameTime() + ticks;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL) {
            if (!stance()) core().end();
        } else {
            core().end();
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!core().idle()) {                                   // guarding: it stands still and faces its foe
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
            LivingEntity foe = getTarget();
            if (foe != null) getLookControl().setLookAt(foe, 30.0F, 30.0F);
        }
    }

    /** Where its crossed blades are: in front of its face. */
    private Vec3 blades() {
        return position().add(Vec3.directionFromRotation(0.0F, yBodyRot).scale(0.45)).add(0.0, getBbHeight() * 0.8, 0.0);
    }

    /** Sparks off the blades while they rise and while they are held, from the phase alone. */
    private void phaseParticles(ServerLevel level) {
        Vec3 at = blades();
        if (core().in(Step.TELL) && tickCount % 2 == 0) {
            level.sendParticles(GLINT_DUST, at.x, at.y, at.z, 2, 0.2, 0.25, 0.2, 0.0);
        } else if (core().in(Step.STANCE) && tickCount % 4 == 0) {
            level.sendParticles(GLINT_DUST, at.x, at.y, at.z, 1, 0.15, 0.2, 0.15, 0.0);
        }
    }

    // ---- catching blows ----------------------------------------------------------------------------------------------

    /** A melee blow: a strike by a living attacker's own hand, claw, sting or weapon. */
    public static boolean melee(DamageSource source) {
        return source.isDirect() && source.getEntity() instanceof LivingEntity
                && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                    || source.is(DamageTypes.SPEAR) || source.is(DamageTypes.MACE_SMASH) || source.is(DamageTypes.STING));
    }

    /**
     * In the stance a melee blow does nothing and staggers its attacker. Outside it, a blow from someone it staggered
     * does nothing until their stagger ends. Shots always land; one landing in the stance pierces its guard.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (melee(source) && !isDeadOrDying() && !isInvulnerableTo(level, source)) {
            LivingEntity attacker = (LivingEntity) source.getEntity();
            if (core().in(Step.STANCE)) {
                if (MechanicEvent.post(this, "parry", attacker)) {
                    parry(level, attacker);
                    return false;
                }
            } else if (staggered(attacker)) {
                return false;
            }
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && core().in(Step.STANCE) && source.getDirectEntity() instanceof Projectile shot) pierced(level, shot);
        return hurt;
    }

    /** The blades catch a blow: the attacker staggers, slowed and unable to strike, for 30 ticks. */
    private void parry(ServerLevel level, LivingEntity attacker) {
        long until = level.getGameTime() + STAGGER_TICKS;
        staggered.removeIf(s -> s.who() == attacker);
        staggered.add(new Stagger(attacker, until));
        attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, STAGGER_SLOWNESS), this);
        attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STAGGER_TICKS, STAGGER_WEAKNESS), this);
        parries++;
        snapUntil = level.getGameTime() + PARRY_SNAP_TICKS;
        core().setFlag(FLAG_PARRIED, true);
        Vec3 at = blades();
        Vec3 clash = at.add(attacker.getEyePosition().subtract(at).scale(0.3));
        level.sendParticles(CLINK_DUST, clash.x, clash.y, clash.z, 10, 0.12, 0.12, 0.12, 0.0);
        level.sendParticles(GLINT_DUST, clash.x, clash.y, clash.z, 8, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(DAZE_DUST, attacker.getX(), attacker.getY(1.05), attacker.getZ(), 8, 0.3, 0.05, 0.3, 0.0);
        level.playSound(null, clash.x, clash.y, clash.z, ModSounds.SCARLET_MANTIS_PARRY.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** A shot landed through its guard: a crack of chitin and a few red flakes where it went in. */
    private void pierced(ServerLevel level, Projectile shot) {
        Vec3 at = shot.position();
        level.sendParticles(CHITIN_DUST, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SCARLET_MANTIS_PIERCE.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** True while {@code who} is staggered by this mantis: its blows on it do nothing. Public as a test hook. */
    public boolean staggered(Entity who) {
        long now = level().getGameTime();
        for (Stagger s : staggered) {
            if (s.who() == who && now < s.until()) return true;
        }
        return false;
    }

    /** Drops ended staggers; a dazed ring of red flecks circles each staggered attacker's head. */
    private void dazed(ServerLevel level) {
        long now = level.getGameTime();
        staggered.removeIf(s -> now >= s.until() || s.who().isRemoved() || !s.who().isAlive());
        if (tickCount % 3 != 0) return;
        for (Stagger s : staggered) {
            LivingEntity who = s.who();
            double a = tickCount * 0.6;
            double r = Math.max(0.3, who.getBbWidth() * 0.6);
            level.sendParticles(DAZE_DUST, who.getX() + Math.cos(a) * r, who.getY() + who.getBbHeight() + 0.25, who.getZ() + Math.sin(a) * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Blows caught so far. Public as a test hook. */
    public int parries() {
        return parries;
    }

    // ---- the slash ---------------------------------------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) playSound(ModSounds.SCARLET_MANTIS_SLASH.get(), 1.0F, getVoicePitch());
        return hit;
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        staggered.clear();
        super.onRemoval(reason);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SCARLET_MANTIS_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SCARLET_MANTIS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SCARLET_MANTIS_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + random.nextFloat() * 0.15F;
    }
}
