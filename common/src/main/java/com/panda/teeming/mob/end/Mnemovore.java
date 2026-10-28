package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
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
import net.minecraft.world.entity.ExperienceOrb;
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
 * Mnemovore: a low, wide crawler of the End barrens that is nearly all mouth, a broad lidded maw on four short legs,
 * lined with glyphs. It eats experience.
 *
 * <p>Its bite comes after a {@link #GAPE_TICKS}-tick gape: the lid of its upper jaw swings wide, it holds still, draws
 * a hungry breath, and glyph motes drift from its target into its mouth. Then the jaw slams shut on whoever is still
 * within {@link #BITE_HOLD} blocks of open space (across or up, so a ledge or a pillar is out of reach). A bite that
 * lands deals its attack (armour counts) and takes one experience level from a player who has one: the player keeps
 * their progress through the bar, as an enchanting table leaves it, and the Mnemovore keeps exactly the points that
 * level held for them. The glyphs in its maw glow a step brighter for each level it has eaten. At most one gape every
 * {@link #BITE_EVERY} ticks.
 *
 * <p>Counter: step back when the maw gapes, or raise a shield; a bite that doesn't land takes nothing, and its jaws
 * snap shut on air. Payoff: killed, it spills every point it ate as experience orbs, plus a tenth (rounded up), so the
 * player gets back more than they lost. A Mnemovore holding stolen experience never despawns, and a removal that
 * destroys it (Peaceful, a command) spills the points too: nothing it took is ever lost.
 */
public class Mnemovore extends TeemingMonster {
    /** IDLE, the gape before a bite (tell), the bite, the swallow after a level was eaten. */
    public enum Step implements Phase { IDLE, GAPE, BITE, SWALLOW }

    /** The gape before a bite: half a second. */
    public static final int GAPE_TICKS = 10;
    public static final int BITE_TICKS = 6;
    /** It sits still this long after a bite that took a level, its glyphs flaring. */
    public static final int SWALLOW_TICKS = 10;
    /** The bite: this many times its attack. */
    public static final float BITE = 1.0F;
    /** Each bite that lands on a player takes this many experience levels. */
    public static final int LEVELS_PER_BITE = 1;
    /** On death it gives back what it ate times this, rounded up. */
    public static final double REPAY = 1.1;
    /** It starts a gape at a target within this many blocks of open space, and the bite lands on one still within this. */
    public static final double BITE_START = 0.75;
    public static final double BITE_HOLD = 1.25;
    /** At most one gape every this many ticks, counted from gape to gape. */
    public static final int BITE_EVERY = 30;
    /** Glow stages: none eaten, then one per level eaten, full at this many. */
    public static final int GLOW_FULL = 4;

    private static final long NEVER = Long.MIN_VALUE;
    private static final DustParticleOptions GLYPH = new DustParticleOptions(0xC9A2F0, 0.9F);
    private static final DustParticleOptions PALE = new DustParticleOptions(0xF2EEFF, 0.7F);

    /** Experience points it has eaten and owes back. */
    private int memories;
    private final Footing footing = new Footing();
    /** Levels it has eaten (its glyphs show them). */
    private int levels;
    private long gapeAt = NEVER;
    private int bites;
    private int misses;
    private int spills;

    public Mnemovore(EntityType<? extends Mnemovore> type, Level level) {
        super(type, level, Step.values());
        xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("mnemovore", 0.23);
    }

    @Override
    public int visualStages() {
        return GLOW_FULL + 1;
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** With a target it scuttles up to it and stops in bite reach; the bite itself is the gape and the snap. */
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
            if (MobCore.gap(Mnemovore.this, target) > BITE_START * 0.6) getNavigation().moveTo(target, 1.0);
            else getNavigation().stop();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    private boolean canBite(@Nullable LivingEntity e) {
        return e != null && e != this && e.isAlive() && !(e instanceof Mnemovore) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    // ---- the gape and the bite ---------------------------------------------------------------------------------------

    /** Where its mouth is: just inside the front of the maw, at the lip line. */
    public Vec3 mouth() {
        Vec3 ahead = Vec3.directionFromRotation(0.0F, yBodyRot);
        return position().add(ahead.scale(getBbWidth() * 0.45)).add(0.0, getBbHeight() * 0.6, 0.0);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().stage() != stage()) showGlyphs();                       // the glyphs always show what it holds
        LivingEntity target = getTarget();
        if (core().in(Step.GAPE)) {
            holdStill();
            LivingEntity t = core().target();
            if (t != null) face(t.position().subtract(position()));
            inhale(level, t);
        } else if (core().in(Step.BITE) || core().in(Step.SWALLOW)) {
            holdStill();
            if (core().in(Step.SWALLOW) && core().elapsed() % 3 == 0) glow(level, 3);
        } else if (core().idle() && canBite(target)) {
            long now = level.getGameTime();
            if (MobCore.gap(this, target) <= BITE_START && (gapeAt == NEVER || now - gapeAt >= BITE_EVERY) && hasLineOfSight(target)) {
                gape(level, target);
            }
        }
    }

    private void gape(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.GAPE, GAPE_TICKS, target)) return;
        gapeAt = level.getGameTime();
        holdStill();
        face(target.position().subtract(position()));
        Vec3 m = mouth();
        level.playSound(null, m.x, m.y, m.z, ModSounds.MNEMOVORE_GAPE.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(GLYPH, true, true, m.x, m.y, m.z, 8, 0.35, 0.15, 0.35, 0.0);
    }

    /**
     * The hungry breath through the gape: enchanting glyphs fly from round the target into its mouth (a particle sent
     * with count 0 flies from the offset given to the point given), and lilac motes swirl at the lips.
     */
    private void inhale(ServerLevel level, @Nullable LivingEntity from) {
        if (core().elapsed() % 2 != 0) return;
        Vec3 m = mouth();
        Vec3 src = from != null ? from.position().add(0.0, from.getBbHeight() * 0.6, 0.0) : m.add(Vec3.directionFromRotation(0.0F, yBodyRot).scale(1.5));
        for (int i = 0; i < 4; i++) {
            Vec3 off = src.subtract(m).add((random.nextDouble() - 0.5) * 0.9, (random.nextDouble() - 0.5) * 0.7, (random.nextDouble() - 0.5) * 0.9);
            level.sendParticles(ParticleTypes.ENCHANT, true, true, m.x, m.y, m.z, 0, off.x, off.y, off.z, 1.0);
        }
        level.sendParticles(GLYPH, true, true, m.x, m.y + 0.1, m.z, 3, 0.4, 0.12, 0.4, 0.0);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.GAPE && level() instanceof ServerLevel level) bite(level, core().target());
        else rest();
    }

    /** Every phase it enters keeps its glyphs showing what it has eaten. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        showGlyphs();
        return true;
    }

    private void rest() {
        core().end();
        showGlyphs();
    }

    /** The glyph glow: one stage per level eaten, full at {@link #GLOW_FULL}. */
    private void showGlyphs() {
        core().setStage(stage());
    }

    /**
     * The end of the gape: the jaws slam shut. On a target still within {@link #BITE_HOLD} and in sight, the bite deals
     * its attack (as a mob's blow, so armour counts); if it landed on a living player with a level, it takes one. On no
     * one, the jaws snap on air.
     */
    private void bite(ServerLevel level, @Nullable LivingEntity victim) {
        if (isDeadOrDying()) {
            rest();
            return;
        }
        boolean took = false;
        if (canBite(victim) && MobCore.gap(this, victim) <= BITE_HOLD && hasLineOfSight(victim) && MechanicEvent.post(this, "bite", victim)) {
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE;
            if (AstralAugur.GroundMarks.hurtInPlace(level, victim, damageSources().mobAttack(this), damage)) {
                bites++;
                setLastHurtMob(victim);
                took = victim instanceof Player player && steal(level, player);
            }
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.MNEMOVORE_BITE.get(), getSoundSource(), 1.0F, 1.0F);
        } else {
            miss(level);
        }
        if (!startPhase(took ? Step.SWALLOW : Step.BITE, took ? SWALLOW_TICKS : BITE_TICKS, victim)) rest();
    }

    /** The counter worked: its jaws snap shut on nothing, a puff of air and the glyph motes it drew in scattering. */
    private void miss(ServerLevel level) {
        misses++;
        Vec3 m = mouth();
        level.playSound(null, m.x, m.y, m.z, ModSounds.MNEMOVORE_MISS.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(GLYPH, true, true, m.x, m.y, m.z, 12, 0.5, 0.2, 0.5, 0.0);
        level.sendParticles(ParticleTypes.POOF, true, true, m.x, m.y, m.z, 4, 0.15, 0.05, 0.15, 0.01);
    }

    /**
     * Takes one experience level from {@code player}, if they are alive, in survival and have one. They keep their
     * progress through the bar (as a fraction, the way vanilla takes levels for enchanting); the points they lose are
     * kept as memories, exactly. Returns whether it took one.
     */
    private boolean steal(ServerLevel level, Player player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.experienceLevel < LEVELS_PER_BITE) return false;
        if (!MechanicEvent.post(this, "steal", player)) return false;
        int lvl = player.experienceLevel;
        double progress = Mth.clamp(player.experienceProgress, 0.0F, 1.0F);
        double before = pointsAt(lvl, progress);
        player.giveExperienceLevels(-LEVELS_PER_BITE);
        double after = pointsAt(player.experienceLevel, Mth.clamp(player.experienceProgress, 0.0F, 1.0F));
        int taken = (int) Math.round(before - after);
        memories = saturated(memories, taken);
        levels = saturated(levels, LEVELS_PER_BITE);
        showGlyphs();
        Vec3 m = mouth();
        Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        Vec3 d = m.subtract(from);
        int n = Math.max(4, (int) Math.ceil(d.length() * 3.0));
        for (int i = 0; i <= n; i++) {                                     // a stream of lilac light from the victim into its maw
            Vec3 p = from.add(d.scale(i / (double) n));
            level.sendParticles(i % 2 == 0 ? GLYPH : PALE, true, true, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        for (int i = 0; i < 8; i++) {
            Vec3 off = from.subtract(m).add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.6);
            level.sendParticles(ParticleTypes.ENCHANT, true, true, m.x, m.y, m.z, 0, off.x, off.y, off.z, 1.0);
        }
        level.playSound(null, m.x, m.y, m.z, ModSounds.MNEMOVORE_DEVOUR.get(), getSoundSource(), 1.0F, 1.0F);
        return true;
    }

    /** Lilac and star-white motes rising from its lips, more the fuller it is. */
    private void glow(ServerLevel level, int count) {
        Vec3 m = mouth();
        level.sendParticles(stage() >= GLOW_FULL ? PALE : GLYPH, true, true, m.x, m.y + 0.1, m.z, count + stage(), 0.4, 0.1, 0.4, 0.0);
    }

    /**
     * A player's experience as points, at {@code level} levels and {@code progress} of the way through the next: the
     * points of every whole level below plus that share of the next one's, by vanilla's level costs.
     */
    public static double pointsAt(int level, double progress) {
        double total = 0.0;
        for (int l = 0; l < level; l++) total += pointsFor(l);
        return total + progress * pointsFor(level);
    }

    /** The points from level {@code level} to the next, as vanilla counts them. */
    public static int pointsFor(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** What it gives back for {@code points} eaten: a tenth more, rounded up. */
    public static int repay(int points) {
        if (points <= 0) return 0;
        long tenths = (long) points * Math.round(REPAY * 10.0);
        return (int) Math.min(Integer.MAX_VALUE, (tenths + 9) / 10);
    }

    private static int saturated(int a, int b) {
        long sum = (long) a + b;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, sum));
    }

    // ---- the payoff: everything back, and more ------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) spill(level);
    }

    /** Removed for good some other way (Peaceful, a command): it still never destroys the experience it took. */
    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level && reason.shouldDestroy() && memories > 0) spill(level);
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        footing.track(this);
    }

    /**
     * Spills everything it ate, plus a tenth, as experience orbs from its mouth (on the last ground it stood on, if it
     * died in the void below the world); its glyphs go dark.
     */
    private void spill(ServerLevel level) {
        if (memories <= 0) return;
        int points = repay(memories);
        spills++;
        memories = 0;
        levels = 0;
        showGlyphs();
        Vec3 m = footing.spill(this, mouth());
        ExperienceOrb.award(level, m, points);
        level.sendParticles(PALE, true, true, m.x, m.y, m.z, 14, 0.35, 0.25, 0.35, 0.0);
        level.sendParticles(GLYPH, true, true, m.x, m.y, m.z, 14, 0.35, 0.25, 0.35, 0.0);
        level.playSound(null, m.x, m.y, m.z, ModSounds.MNEMOVORE_SPILL.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** It never despawns while it holds experience it took. */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || memories > 0;
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

    /** Its glyph stage: one per level eaten, up to {@link #GLOW_FULL}. */
    public int stage() {
        return Math.min(levels, GLOW_FULL);
    }

    /** Experience points it holds and owes back. */
    public int memories() {
        return memories;
    }

    /** Levels it has eaten. */
    public int levelsEaten() {
        return levels;
    }

    /** Sets what it holds: {@code points} of experience from {@code levelCount} levels. Public as a test hook. */
    public void setMemories(int points, int levelCount) {
        memories = Math.max(0, points);
        levels = Math.max(0, levelCount);
        showGlyphs();
    }

    /** Counts one more level of {@code points} as eaten, as a bite on a player with one does. Public as a test hook. */
    public void feed(int points) {
        memories = saturated(memories, Math.max(0, points));
        levels = saturated(levels, LEVELS_PER_BITE);
        showGlyphs();
    }

    /** Bites that landed so far. Public as a test hook. */
    public int bites() {
        return bites;
    }

    /** Bites that snapped on nothing so far. Public as a test hook. */
    public int misses() {
        return misses;
    }

    /** Times it has spilled what it ate (it only ever has once, at its end). Public as a test hook. */
    public int spills() {
        return spills;
    }

    /** Ticks until its next gape may start (0: ready). Public as a test hook. */
    public int biteCooldown() {
        if (gapeAt == NEVER) return 0;
        return (int) Math.max(0L, gapeAt + BITE_EVERY - level().getGameTime());
    }

    // ---- save and load -----------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Memories", memories);
        output.putInt("LevelsEaten", levels);
        output.putInt("BiteCooldown", biteCooldown());
    }

    /** What it ate and the bite cooldown survive a reload; a gape under way does not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        memories = Math.max(0, input.getIntOr("Memories", 0));
        levels = Math.max(0, input.getIntOr("LevelsEaten", 0));
        int cooldown = Math.clamp(input.getIntOr("BiteCooldown", 0), 0, BITE_EVERY);
        gapeAt = cooldown > 0 ? level().getGameTime() - BITE_EVERY + cooldown : NEVER;
        showGlyphs();
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.MNEMOVORE_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MNEMOVORE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MNEMOVORE_DEATH.get();
    }
}
