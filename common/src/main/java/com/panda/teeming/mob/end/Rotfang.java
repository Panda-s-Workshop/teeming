package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rotfang: a lean, low hound-thing of void ichor from the End barrens, hunting alone or in a pair. Its lower jaw is
 * split into two halves that open apart, dripping rot.
 *
 * <p>Each bite comes after a {@link #SNARL_TICKS}-tick snarl: it crouches and holds still, the two halves of its jaw
 * split wide, it snarls wetly and dark motes and drips of ichor fall from its mouth. Then the halves snap shut on
 * whoever is still within {@link #BITE_HOLD} blocks of open space (across or up, so a ledge or a pillar is out of
 * reach). A bite that lands deals its attack (armour counts) without knocking the victim back, so it never sends anyone
 * off an island's edge, and gives Void Rot for {@link #ROT_TICKS} ticks: no natural regeneration, and every other heal
 * halved. At most one snarl every {@link #BITE_EVERY} ticks. A pair takes turns: one never snarls at a target its
 * packmate is already snarling at or biting, and a Rotfang that finds a target hands it to a packmate near by that
 * has none and can reach it.
 *
 * <p>Counter: hit it while it snarls, by any blow or shot. Its split jaw snaps shut on its own foreleg instead: it
 * takes its own bite (its attack, through its own armour, credited to whoever struck it), rots for
 * {@link #ROT_TICKS} ticks itself, and the Void Rot in whoever struck it drains back out of them. It gnaws at itself
 * for {@link #GNAW_TICKS} ticks, a flinch, not a window for extra damage. Stepping out of reach makes the jaws close on
 * nothing.
 */
public class Rotfang extends TeemingMonster {
    /** IDLE, the snarl before a bite (tell), the bite, and the gnaw at its own leg after the counter. */
    public enum Step implements Phase { IDLE, SNARL, BITE, GNAW }

    /** The snarl before a bite: half a second. */
    public static final int SNARL_TICKS = 10;
    /** The bite pose after the jaws shut. */
    public static final int BITE_TICKS = 6;
    /** It gnaws at its own foreleg this long after the counter: a flinch. */
    public static final int GNAW_TICKS = 30;
    /** The bite: this many times its attack. */
    public static final float BITE = 1.0F;
    /** The bite it gives itself when struck in the snarl: this many times its attack. */
    public static final float SELF_BITE = 1.0F;
    /** Void Rot from a bite, in ticks (8 s). */
    public static final int ROT_TICKS = 160;
    /** It starts a snarl at a target within this many blocks of open space, and the bite lands on one still within this. */
    public static final double BITE_START = 0.8;
    public static final double BITE_HOLD = 1.3;
    /** At most one snarl every this many ticks, counted from snarl to snarl. */
    public static final int BITE_EVERY = 30;
    /** How far it looks for its packmates: turns are taken, and targets handed on, within this many blocks. */
    public static final double PACK_RANGE = 12.0;
    /** How often it hands its target to packmates without one, in ticks. */
    public static final int RALLY_EVERY = 20;

    private static final long NEVER = Long.MIN_VALUE;
    /** Dark violet motes of the snarl: they read on the cream of end stone. */
    private static final DustParticleOptions MOTE = new DustParticleOptions(0x2A1846, 1.1F);
    /** Lilac ichor. */
    private static final DustParticleOptions ICHOR = new DustParticleOptions(0xB583E6, 0.9F);

    private long snarlAt = NEVER;
    private int bites;
    private int misses;
    private int rots;
    private int gnaws;
    private int cleanses;

    public Rotfang(EntityType<? extends Rotfang> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("rotfang", 0.29);
    }

    // ---- goals ---------------------------------------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal());
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Rotfang.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** With a target it lopes up to it and stops in bite reach; the bite itself is the snarl and the snap. */
    private final class ChaseGoal extends Goal {
        private int repath;

        ChaseGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return core().idle() && canBite(getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--repath > 0) return;
            repath = 10;
            if (MobCore.gap(Rotfang.this, target) > BITE_START * 0.6) getNavigation().moveTo(target, 1.0);
            else getNavigation().stop();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    private boolean canBite(@Nullable LivingEntity e) {
        return e != null && e != this && e.isAlive() && !(e instanceof Rotfang) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    // ---- the snarl and the bite ----------------------------------------------------------------------------------------

    /** Where its jaw is: just past the front of its body, low. */
    public Vec3 jaw() {
        Vec3 ahead = Vec3.directionFromRotation(0.0F, yBodyRot);
        return position().add(ahead.scale(getBbWidth() * 0.5 + 0.35)).add(0.0, getBbHeight() * 0.3, 0.0);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (core().in(Step.SNARL)) {
            holdStill();
            LivingEntity t = core().target();
            if (t != null) face(t.position().subtract(position()));
            snarlTick(level);
        } else if (core().in(Step.BITE) || core().in(Step.GNAW)) {
            holdStill();
            if (core().in(Step.GNAW) && core().elapsed() % 4 == 0) {
                Vec3 j = jaw();
                level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, true, true, j.x, j.y, j.z, 2, 0.2, 0.05, 0.2, 0.0);
            }
        } else if (core().idle() && canBite(target)) {
            long now = level.getGameTime();
            if (MobCore.gap(this, target) <= BITE_START && (snarlAt == NEVER || now - snarlAt >= BITE_EVERY) && hasLineOfSight(target)
                    && !packBusy(target)) {
                snarl(level, target);
            }
        }
        if (target != null && canBite(target) && tickCount % RALLY_EVERY == 0) rally(target);
    }

    private void snarl(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.SNARL, SNARL_TICKS, target)) return;
        snarlAt = level.getGameTime();
        holdStill();
        face(target.position().subtract(position()));
        Vec3 j = jaw();
        level.playSound(null, j.x, j.y, j.z, ModSounds.ROTFANG_SNARL.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(MOTE, true, true, j.x, j.y + 0.2, j.z, 12, 0.35, 0.25, 0.35, 0.0);
        level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, true, true, j.x, j.y, j.z, 4, 0.2, 0.05, 0.2, 0.0);
    }

    /** Through the snarl: drips of ichor fall from the split jaw, and dark motes rise round its head. */
    private void snarlTick(ServerLevel level) {
        Vec3 j = jaw();
        if (core().elapsed() % 2 == 0) {
            level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, true, true, j.x, j.y, j.z, 2, 0.2, 0.05, 0.2, 0.0);
            level.sendParticles(MOTE, true, true, j.x, j.y + 0.35, j.z, 4, 0.4, 0.25, 0.4, 0.0);
        }
        if (core().elapsed() % 3 == 1) level.sendParticles(ICHOR, true, true, j.x, j.y - 0.05, j.z, 2, 0.15, 0.05, 0.15, 0.0);
    }

    /** A packmate near by is already snarling at, or biting, the same target: wait for the next turn. */
    private boolean packBusy(LivingEntity target) {
        for (Rotfang mate : level().getEntitiesOfClass(Rotfang.class, getBoundingBox().inflate(PACK_RANGE), r -> r != this && r.isAlive())) {
            if ((mate.core().in(Step.SNARL) || mate.core().in(Step.BITE)) && mate.core().target() == target) return true;
        }
        return false;
    }

    /** Hands its target to every packmate near by that has none and could follow it that far. */
    private void rally(LivingEntity target) {
        for (Rotfang mate : level().getEntitiesOfClass(Rotfang.class, getBoundingBox().inflate(PACK_RANGE), r -> r != this && r.isAlive())) {
            if (mate.getTarget() != null || mate.distanceTo(target) > mate.getAttributeValue(Attributes.FOLLOW_RANGE)) continue;
            if (MechanicEvent.post(this, "rally", target)) mate.setTarget(target);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.SNARL && level() instanceof ServerLevel level) bite(level, core().target());
        else core().end();
    }

    /**
     * The end of the snarl: the split jaw snaps shut. On a target still within {@link #BITE_HOLD} and in sight, the bite
     * deals its attack (as a mob's blow, so armour counts, with no knockback) and gives Void Rot. On no one, the jaws
     * close on nothing.
     */
    private void bite(ServerLevel level, @Nullable LivingEntity victim) {
        if (isDeadOrDying()) {
            core().end();
            return;
        }
        if (canBite(victim) && MobCore.gap(this, victim) <= BITE_HOLD && hasLineOfSight(victim) && MechanicEvent.post(this, "bite", victim)) {
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE;
            if (AstralAugur.GroundMarks.hurtInPlace(level, victim, damageSources().mobAttack(this), damage)) {
                bites++;
                setLastHurtMob(victim);
                rot(level, victim);
            }
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ROTFANG_BITE.get(), getSoundSource(), 1.0F, 1.0F);
        } else {
            miss(level);
        }
        if (!startPhase(Step.BITE, BITE_TICKS, victim)) core().end();
    }

    /** Void Rot on the bitten: lilac spots of rot burst on it and a wet hiss. */
    private void rot(ServerLevel level, LivingEntity victim) {
        if (!victim.isAlive() || !MechanicEvent.post(this, "rot", victim)) return;
        if (!ModEffects.give(victim, ModEffects.VOID_ROT, ROT_TICKS, this)) return;
        rots++;
        double y = victim.getY() + victim.getBbHeight() * 0.6;
        level.sendParticles(ICHOR, true, true, victim.getX(), y, victim.getZ(), 14, victim.getBbWidth() * 0.4, victim.getBbHeight() * 0.3,
                victim.getBbWidth() * 0.4, 0.0);
        level.sendParticles(MOTE, true, true, victim.getX(), y, victim.getZ(), 8, victim.getBbWidth() * 0.4, victim.getBbHeight() * 0.3,
                victim.getBbWidth() * 0.4, 0.0);
        level.playSound(null, victim.getX(), y, victim.getZ(), ModSounds.ROTFANG_ROT.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** Stepped out of reach: the halves snap shut on air, spraying a little ichor. */
    private void miss(ServerLevel level) {
        misses++;
        Vec3 j = jaw();
        level.playSound(null, j.x, j.y, j.z, ModSounds.ROTFANG_MISS.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ICHOR, true, true, j.x, j.y, j.z, 10, 0.3, 0.1, 0.3, 0.0);
        level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, true, true, j.x, j.y, j.z, 4, 0.25, 0.05, 0.25, 0.0);
    }

    // ---- the counter: struck in the snarl, it bites itself ------------------------------------------------------------

    /**
     * A hit that lands while it snarls, from anyone but another Rotfang, turns its bite on itself: see {@link #gnaw}.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean snarling = isAlive() && core().in(Step.SNARL);
        LivingEntity striker = source.getEntity() instanceof LivingEntity by && by != this && !(by instanceof Rotfang) ? by : null;
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && snarling && striker != null && !isDeadOrDying() && core().in(Step.SNARL)) gnaw(level, striker);
        return hurt;
    }

    /**
     * The counter worked: the split jaw snaps shut on its own foreleg. The Void Rot in {@code striker} drains out of
     * them, back into its jaw; it takes its own bite (its attack, through its own armour, credited to the striker, never
     * swallowed by the hurt cooldown of the blow that turned it) and rots itself. The snarl's bite never comes.
     */
    private void gnaw(ServerLevel level, LivingEntity striker) {
        if (!startPhase(Step.GNAW, GNAW_TICKS, striker)) return;
        gnaws++;
        Vec3 j = jaw();
        level.playSound(null, j.x, j.y, j.z, ModSounds.ROTFANG_GNAW.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, true, true, j.x, j.y, j.z, 10, 0.25, 0.2, 0.25, 0.1);
        level.sendParticles(ICHOR, true, true, j.x, j.y, j.z, 12, 0.3, 0.15, 0.3, 0.0);
        cleanse(level, striker);
        damageCooldownTime = 0;
        DamageSource own = new DamageSource(damageSources().mobAttack(this).typeHolder(), this, striker);
        AstralAugur.GroundMarks.hurtInPlace(level, this, own, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * SELF_BITE);
        if (isAlive()) ModEffects.give(this, ModEffects.VOID_ROT, ROT_TICKS, striker);
    }

    /** Draws the Void Rot out of {@code who}: a stream of ichor from them back into its jaw. */
    private void cleanse(ServerLevel level, LivingEntity who) {
        if (!who.hasEffect(ModEffects.VOID_ROT.holder()) || !MechanicEvent.post(this, "cleanse", who)) return;
        who.removeEffect(ModEffects.VOID_ROT.holder());
        cleanses++;
        Vec3 j = jaw();
        Vec3 from = who.position().add(0.0, who.getBbHeight() * 0.5, 0.0);
        Vec3 d = j.subtract(from);
        int n = Math.max(4, (int) Math.ceil(d.length() * 3.0));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            level.sendParticles(i % 2 == 0 ? ICHOR : MOTE, true, true, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.playSound(null, from.x, from.y, from.z, ModSounds.ROTFANG_CLEANSE.get(), getSoundSource(), 1.0F, 1.0F);
    }

    // ---- small helpers and test hooks --------------------------------------------------------------------------------

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Bites that landed so far. Public as a test hook. */
    public int bites() {
        return bites;
    }

    /** Bites that closed on nothing so far. Public as a test hook. */
    public int misses() {
        return misses;
    }

    /** Void Rots given by its bites so far. Public as a test hook. */
    public int rots() {
        return rots;
    }

    /** Times it was struck in the snarl and bit itself. Public as a test hook. */
    public int gnaws() {
        return gnaws;
    }

    /** Times it drew the Void Rot out of whoever struck it. Public as a test hook. */
    public int cleanses() {
        return cleanses;
    }

    /** Ticks until its next snarl may start (0: ready). Public as a test hook. */
    public int biteCooldown() {
        if (snarlAt == NEVER) return 0;
        return (int) Math.max(0L, snarlAt + BITE_EVERY - level().getGameTime());
    }

    // ---- save and load -----------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BiteCooldown", biteCooldown());
    }

    /** The bite cooldown survives a reload; a snarl under way does not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int cooldown = Math.clamp(input.getIntOr("BiteCooldown", 0), 0, BITE_EVERY);
        snarlAt = cooldown > 0 ? level().getGameTime() - BITE_EVERY + cooldown : NEVER;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.ROTFANG_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ROTFANG_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ROTFANG_DEATH.get();
    }
}
