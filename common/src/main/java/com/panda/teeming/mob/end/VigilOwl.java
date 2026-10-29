package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Vigil Owl: a small, upright watcher perched on end stone or chorus, its huge round eyes set in a flat disc face that
 * turns to follow you. It keeps its distance: close within 4 blocks and it flutters off. It stares at a player it can
 * see within 16 blocks. The stare is its tell, 40 ticks: its eyes widen and glow brighter as it fills, a hoot rises in
 * pitch, and a thin line of motes runs from its eyes to you. Breaking its line of sight (a block, a pillar, a corner)
 * resets the stare. A full stare marks you: Glowing for 200 ticks, and every Teeming End mob within 32 blocks of you
 * takes you as its target (never vanilla mobs, never the passive ones, never other players).
 *
 * <p>Counter: break its line of sight, or hit it once: it flutters off and drops the stare. Payoff: a hit from the one
 * it marked, while the mark is up, turns its call against it: the Glowing ends at once and every mob it called onto you
 * turns on the owl instead, and keeps after it for 5 seconds.
 *
 * <p>Cornered (a player within reach while it can't flutter off again yet) it pecks: an 8 tick tell, its head drawn
 * back, then its attack (armour counts), with no knockback. A reload ends a stare, a mark's payoff and the turned
 * mobs' hunt; the Glowing and the targets it gave stay as they are.
 */
public class VigilOwl extends TeemingMonster {
    /** IDLE, the stare (tell), the call that marks, fluttering off, the peck's wind-up, the peck. */
    public enum Step implements Phase { IDLE, TELL, MARK, FLEE, PECK_TELL, PECK }

    /** It sees this far, eye to eye in blocks, and only with a clear line of sight (its follow range too). */
    public static final double SIGHT_RANGE = 16.0;
    /** The stare: 2 s of unbroken sight before the mark. */
    public static final int STARE_TICKS = 40;
    /** The mark: Glowing this long. */
    public static final int GLOW_TICKS = 200;
    /** Teeming End mobs this far from the marked one (feet to feet, in blocks) take them as their target. */
    public static final double CALL_RANGE = 32.0;
    /** Closer than this (feet to feet, in blocks) and it flutters off. */
    public static final double KEEP_AWAY = 4.0;
    /** The call: its head thrown up and wings spread this long after the mark. */
    public static final int MARK_TICKS = 20;
    /** Fluttering off lasts this long. */
    public static final int FLEE_TICKS = 30;
    /** After fluttering off from someone too close, it holds its ground this long (it pecks instead). */
    public static final int FLEE_REST = 60;
    /** Its speed fluttering off, as a multiple of its walking speed. */
    public static final double FLEE_SPEED = 1.6;
    /** The flutter's hop: up, and away, in blocks a tick. */
    public static final double HOP_UP = 0.42;
    public static final double HOP_OUT = 0.3;
    /** Fluttering, it falls no faster than this, in blocks a tick. */
    public static final double FLUTTER_FALL = -0.12;
    /** The peck's wind-up: its head drawn back. */
    public static final int PECK_TELL_TICKS = 8;
    /** The peck lands on its first tick; its head stays out this long. */
    public static final int PECK_TICKS = 6;
    /** x attack damage: its peck is its attack. */
    public static final float PECK_HIT = 1.0F;
    /** Blocks between hitboxes (across or up and down): this close it winds up a peck. */
    public static final double PECK_START = 0.8;
    /** Blocks between hitboxes: a target this close when it pecks is hit. */
    public static final double PECK_REACH = 1.4;
    /** Ticks from the end of one peck to the next wind-up. */
    public static final int PECK_COOLDOWN = 30;
    /** The mobs its call turned on it keep after it this long. */
    public static final int TURN_TICKS = 100;
    /** The hoot rises a step every this many ticks of the stare. */
    public static final int HOOT_EVERY = 14;

    /** Glow stages: 0 at rest; 1 to 3 as the stare fills (3: the whole disc lit). */
    public static final int GLOW_REST = 0;
    public static final int GLOW_FULL = 3;

    /** Teeming's End mobs, and the passive ones among all Teeming mobs (a grazer has no quarrel to pick). */
    public static final TagKey<EntityType<?>> END_MOBS = TagKey.create(Registries.ENTITY_TYPE, TeemingMod.id("end"));
    public static final TagKey<EntityType<?>> PASSIVE = TagKey.create(Registries.ENTITY_TYPE, TeemingMod.id("passive"));

    private static final int STAR = 0xFFF2F0FF;
    private static final int LILAC = 0xFFC9A8FF;
    private static final int VOID = 0xFF3A2468;

    private int fleeRest;
    private int peckCooldown;
    private @Nullable LivingEntity marked;
    private long markEnd = Long.MIN_VALUE;
    private final List<Mob> called = new ArrayList<>();
    private final List<Mob> turned = new ArrayList<>();
    private @Nullable LivingEntity turnedFrom;
    private long turnedUntil = Long.MIN_VALUE;
    private @Nullable Predicate<LivingEntity> watchOnly;
    private int marks;
    private int pecks;
    private Vec3 fleeDir = Vec3.ZERO;
    private boolean hopped;

    public VigilOwl(EntityType<? extends VigilOwl> type, Level level) {
        super(type, level, Step.values());
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("vigil_owl", 0.22).add(Attributes.FOLLOW_RANGE, SIGHT_RANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // it hops about its perch only while it watches no one
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override
            public boolean canUse() {
                return core().idle() && getTarget() == null && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && getTarget() == null && super.canContinueToUse();
            }
        });
    }

    @Override
    public int visualStages() {
        return GLOW_FULL + 1;
    }

    /** Its head swivels far round on its shoulders, so the disc stays on whoever it watches. */
    @Override
    public int getMaxHeadYRot() {
        return 150;
    }

    @Override
    public int getHeadRotSpeed() {
        return 20;
    }

    // ---- who it watches ------------------------------------------------------------------------------------------

    /** How far it sees: 16 blocks, or less if its follow range is lowered. */
    private double sight() {
        return Math.min(SIGHT_RANGE, getAttributeValue(Attributes.FOLLOW_RANGE));
    }

    private boolean valid(@Nullable LivingEntity e) {
        return e != null && e != this && e.isAlive() && !e.isRemoved() && e.level() == level()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private boolean inRange(@Nullable LivingEntity e) {
        return valid(e) && distanceTo(e) <= sight() && (watchOnly == null || watchOnly.test(e));
    }

    /** Whether it sees {@code e} now: in range, eye to eye with nothing solid between. Public as a test hook. */
    public boolean sees(LivingEntity e) {
        return inRange(e) && hasLineOfSight(e);
    }

    /** Keeps its target while it is in range, else picks the nearest survival player it sees. */
    private @Nullable LivingEntity watched(ServerLevel level) {
        LivingEntity t = getTarget();
        if (t != null && !inRange(t)) {
            setTarget(null);
            t = null;
        }
        if (t != null) return t;
        Player best = null;
        for (ServerPlayer p : level.players()) {
            if (sees(p) && (best == null || distanceToSqr(p) < distanceToSqr(best))) best = p;
        }
        if (best != null) setTarget(best);
        return best;
    }

    /** True while {@code e} carries the mark it gave. */
    public boolean markedNow(@Nullable LivingEntity e) {
        return e != null && e == marked && level().getGameTime() < markEnd;
    }

    // ---- the AI step ---------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (fleeRest > 0) fleeRest--;
        if (peckCooldown > 0) peckCooldown--;
        keepTurned(level);
        LivingEntity target = watched(level);
        if (core().in(Step.FLEE)) {
            flutter(level);
            return;
        }
        if (core().in(Step.MARK) || core().in(Step.PECK_TELL) || core().in(Step.PECK)) {
            holdStill();
            LivingEntity at = core().target();
            if (at != null) face(at);
            return;
        }
        if (core().in(Step.TELL)) {
            stare(level);
            return;
        }
        if (target == null) {
            core().setStage(GLOW_REST);
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (distanceTo(target) < KEEP_AWAY) {
            if (fleeRest == 0) {
                flee(level, target);
                return;
            }
            if (peckCooldown == 0 && MobCore.gap(this, target) <= PECK_START && hasLineOfSight(target)) {
                windUp(level, target);
                return;
            }
        }
        if (!markedNow(target) && sees(target)) startStare(level, target);
    }

    // ---- the stare -----------------------------------------------------------------------------------------------

    private void startStare(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.TELL, STARE_TICKS, target)) return;
        holdStill();
        face(target);
        core().setStage(1);
        level.playSound(null, getX(), getEyeY(), getZ(), ModSounds.VIGIL_OWL_STARE.get(), getSoundSource(), 1.0F, 0.8F);
    }

    /**
     * One tick of the stare: it holds still with its face on the target, its eyes brighter as the stare fills, the hoot
     * a step higher every 14 ticks, motes running from its eyes to the target. Lost sight, it drops the stare; someone
     * closing within 4 sends it off.
     */
    private void stare(ServerLevel level) {
        LivingEntity t = core().target();
        if (t == null || t != getTarget() || !sees(t)) {
            lose(level);
            return;
        }
        if (distanceTo(t) < KEEP_AWAY && fleeRest == 0) {
            flee(level, t);
            return;
        }
        holdStill();
        face(t);
        getLookControl().setLookAt(t, 60.0F, 60.0F);
        int elapsed = core().elapsed();
        core().setStage(Math.min(GLOW_FULL, 1 + elapsed * GLOW_FULL / STARE_TICKS));
        if (elapsed > 0 && elapsed % HOOT_EVERY == 0) {
            float pitch = 0.8F + 0.2F * (elapsed / HOOT_EVERY);
            level.playSound(null, getX(), getEyeY(), getZ(), ModSounds.VIGIL_OWL_STARE.get(), getSoundSource(), 1.0F, pitch);
        }
        motes(level, t, elapsed);
    }

    /** A thin line of motes from its eyes toward the target's chest, stopping short of their own face. */
    private void motes(ServerLevel level, LivingEntity t, int elapsed) {
        Vec3 from = getEyePosition().add(Vec3.directionFromRotation(0.0F, yBodyRot).scale(0.3));
        Vec3 to = t.position().add(0.0, t.getBbHeight() * 0.55, 0.0);
        Vec3 d = to.subtract(from);
        double length = d.length() - 1.2;
        if (length < 0.4) return;
        Vec3 dir = d.normalize();
        if (elapsed % 2 == 0) {
            double creep = (elapsed / 2 % 2) * 0.35;
            for (double run = creep; run < length; run += 0.7) {
                Vec3 p = from.add(dir.scale(run));
                Vec3 q = from.add(dir.scale(Math.min(length, run + 0.35)));
                level.sendParticles(new TrailParticleOption(q, elapsed > STARE_TICKS / 2 ? STAR : LILAC, 4), true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (elapsed % 8 == 0) level.sendParticles(ParticleTypes.END_ROD, true, true, from.x, from.y, from.z, 1, 0.08, 0.05, 0.08, 0.0);
    }

    /** The stare is dropped: lost sight, a hit, a flutter. Its eyes dim and a soft fall of the hoot says so. */
    private void lose(ServerLevel level) {
        core().end();
        core().setStage(GLOW_REST);
        Vec3 eyes = getEyePosition();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, eyes.x, eyes.y, eyes.z, 6, 0.2, 0.1, 0.2, 0.02);
        level.playSound(null, eyes.x, eyes.y, eyes.z, ModSounds.VIGIL_OWL_LOST.get(), getSoundSource(), 0.9F, 1.0F);
    }

    // ---- the mark ------------------------------------------------------------------------------------------------

    @Override
    public void onPhaseEnd(Phase ended) {
        LivingEntity t = core().target();
        if (ended == Step.TELL && valid(t) && level() instanceof ServerLevel level) {
            mark(level, t);
        } else if (ended == Step.PECK_TELL && level() instanceof ServerLevel level) {
            peck(level, t);
        } else {
            if (ended == Step.FLEE) fleeRest = FLEE_REST;
            if (ended == Step.PECK) peckCooldown = PECK_COOLDOWN;
            core().end();
            core().setStage(GLOW_REST);
        }
    }

    /**
     * The full stare marks {@code t}: Glowing for 200 ticks, and every Teeming End mob within 32 blocks of them takes
     * them as its target. Each mob it calls is a step of its own, which packs can veto.
     */
    private void mark(ServerLevel level, LivingEntity t) {
        if (!startPhase(Step.MARK, MARK_TICKS, t)) {
            core().end();
            core().setStage(GLOW_REST);
            return;
        }
        core().setStage(GLOW_FULL);
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false, true), this);
        marked = t;
        markEnd = level.getGameTime() + GLOW_TICKS;
        called.clear();
        marks++;
        for (Mob m : level.getEntitiesOfClass(Mob.class, t.getBoundingBox().inflate(CALL_RANGE), m -> answers(m, t))) {
            if (!MechanicEvent.post(this, "call", m)) continue;
            m.setTarget(t);
            called.add(m);
            Vec3 at = m.position().add(0.0, m.getBbHeight() + 0.3, 0.0);
            level.sendParticles(new TrailParticleOption(at.add(0.0, 0.8, 0.0), LILAC, 10), true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        Vec3 eyes = getEyePosition();
        level.playSound(null, eyes.x, eyes.y, eyes.z, ModSounds.VIGIL_OWL_MARK.get(), getSoundSource(), 1.4F, 1.0F);
        // a ring of star-white light closes on the one it marked
        Vec3 c = t.position().add(0.0, t.getBbHeight() * 0.5, 0.0);
        double r = t.getBbWidth() * 0.5 + 0.8;
        for (int i = 0; i < 10; i++) {
            double a = i * (Math.PI * 2 / 10);
            level.sendParticles(new TrailParticleOption(c, STAR, 8), true, true, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, true, true, eyes.x, eyes.y + 0.2, eyes.z, 6, 0.2, 0.2, 0.2, 0.05);
    }

    /**
     * Whether {@code m} answers its call on {@code marked}: a living Teeming End mob, not a passive one, not the owl
     * itself, within 32 blocks of the marked one (feet to feet). Vanilla mobs and players never answer. Public as a
     * test hook.
     */
    public boolean answers(Mob m, LivingEntity marked) {
        return m != this && m != marked && m.isAlive() && !m.isRemoved()
                && m.getType().builtInRegistryHolder().is(END_MOBS) && !m.getType().builtInRegistryHolder().is(PASSIVE)
                && m.distanceTo(marked) <= CALL_RANGE;
    }

    // ---- hit: it flees, and a hit from the marked turns its call --------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (!hurt || isDeadOrDying()) return hurt;
        Entity by = source.getEntity();
        if (by instanceof LivingEntity hitter && markedNow(hitter)) turnCall(level, hitter);
        if (core().in(Step.TELL)) lose(level);
        if (!core().in(Step.FLEE)) flee(level, by != null ? by : core().target());
        return hurt;
    }

    /**
     * The payoff: hit by the one it marked while the mark is up, its call turns on it. Their Glowing (its own, 200
     * ticks or less) ends at once, and every mob it called that still hunts them turns on the owl, for 5 seconds.
     */
    private void turnCall(ServerLevel level, LivingEntity hitter) {
        if (!MechanicEvent.post(this, "turn", hitter)) return;
        MobEffectInstance glow = hitter.getEffect(MobEffects.GLOWING);
        if (glow != null && glow.getDuration() <= GLOW_TICKS) hitter.removeEffect(MobEffects.GLOWING);
        markEnd = level.getGameTime();
        turned.clear();
        for (Mob m : called) {
            if (!m.isAlive() || m.isRemoved() || m.getTarget() != hitter) continue;
            m.setTarget(this);
            turned.add(m);
            Vec3 from = m.position().add(0.0, m.getBbHeight() * 0.6, 0.0);
            level.sendParticles(new TrailParticleOption(getEyePosition(), VOID, 12), true, true, from.x, from.y, from.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 10, 0.3, 0.3, 0.3, 0.03);
        }
        called.clear();
        turnedFrom = hitter;
        turnedUntil = level.getGameTime() + TURN_TICKS;
        Vec3 eyes = getEyePosition();
        level.playSound(null, eyes.x, eyes.y, eyes.z, ModSounds.VIGIL_OWL_TURNED.get(), getSoundSource(), 1.3F, 1.0F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, hitter.getX(), hitter.getY() + hitter.getBbHeight() * 0.5, hitter.getZ(), 16, 0.4, 0.5, 0.4, 0.05);
    }

    /** For 5 seconds after its call turned, a turned mob that goes back to the one it marked is set on the owl again. */
    private void keepTurned(ServerLevel level) {
        if (turned.isEmpty()) return;
        if (level.getGameTime() >= turnedUntil) {
            turned.clear();
            turnedFrom = null;
            return;
        }
        for (Mob m : turned) {
            if (m.isAlive() && !m.isRemoved() && m.getTarget() == turnedFrom) m.setTarget(this);
        }
    }

    // ---- fluttering off ------------------------------------------------------------------------------------------

    /** Flutters off from {@code from}: a hop up and away (only where there is ground ahead), then a run to an open spot. */
    private void flee(ServerLevel level, @Nullable Entity from) {
        LivingEntity who = from instanceof LivingEntity l ? l : null;
        if (!startPhase(Step.FLEE, FLEE_TICKS, who)) return;
        core().setStage(GLOW_REST);
        Vec3 threat = from != null ? from.position() : position().add(Vec3.directionFromRotation(0.0F, getYRot()));
        Vec3 away = position().subtract(threat).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0.0F, getYRot()).scale(-1.0);
        away = away.normalize();
        Vec3 spot = DefaultRandomPos.getPosAway(this, 10, 4, threat);
        if (spot == null) spot = DefaultRandomPos.getPosAway(this, 5, 3, threat);
        if (spot != null) {
            getNavigation().moveTo(spot.x, spot.y, spot.z, FLEE_SPEED);
            Vec3 toSpot = spot.subtract(position()).horizontal();
            if (toSpot.lengthSqr() > 1.0E-4 && toSpot.normalize().dot(away) > 0.3) away = toSpot.normalize();
        }
        fleeDir = away;
        hopped = false;
        flutter(level);
        face(position().add(away));
        level.playSound(null, getX(), getY() + 0.4, getZ(), ModSounds.VIGIL_OWL_FLEE.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.4, getZ(), 8, 0.3, 0.2, 0.3, 0.04);
    }

    /**
     * One tick of fluttering off: the hop up and away the first time it stands on the ground (only with ground ahead),
     * a straight run away when no open spot was found or the run to it is done, and a slow fall while it is aloft.
     */
    private void flutter(ServerLevel level) {
        boolean safe = groundAhead(level, fleeDir);
        if (!hopped && onGround()) {
            hopped = true;
            if (safe) {
                setDeltaMovement(fleeDir.x * HOP_OUT, HOP_UP, fleeDir.z * HOP_OUT);
                needsSync = true;
            }
        }
        if (getNavigation().isDone() && safe) {
            getMoveControl().setWantedPosition(getX() + fleeDir.x * 2.0, getY(), getZ() + fleeDir.z * 2.0, FLEE_SPEED);
        }
        Vec3 v = getDeltaMovement();
        if (!onGround() && v.y < FLUTTER_FALL) setDeltaMovement(v.x, FLUTTER_FALL, v.z);
    }

    /** Ground within 4 blocks below the spot 2 blocks along {@code dir}: it never hops out over the void. */
    private boolean groundAhead(ServerLevel level, Vec3 dir) {
        double x = getX() + dir.x * 2.0, z = getZ() + dir.z * 2.0;
        for (int dy = 0; dy <= 4; dy++) {
            BlockPos p = BlockPos.containing(x, getY() - dy, z);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return true;
        }
        return false;
    }

    // ---- the peck ------------------------------------------------------------------------------------------------

    private void windUp(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.PECK_TELL, PECK_TELL_TICKS, target)) return;
        holdStill();
        face(target);
        level.playSound(null, getX(), getEyeY(), getZ(), ModSounds.VIGIL_OWL_PECK_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        Vec3 eyes = getEyePosition();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, eyes.x, eyes.y, eyes.z, 5, 0.15, 0.1, 0.15, 0.01);
    }

    /**
     * The peck: its attack (armour counts) on a target still within reach (across or up and down) and in sight, with
     * no knockback, so it never knocks anyone off an island.
     */
    private void peck(ServerLevel level, @Nullable LivingEntity t) {
        if (!startPhase(Step.PECK, PECK_TICKS, t)) {
            core().end();
            return;
        }
        if (!valid(t) || MobCore.gap(this, t) > PECK_REACH || !hasLineOfSight(t)) return;
        face(t);
        if (AstralAugur.GroundMarks.hurtInPlace(level, t, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * PECK_HIT)) {
            pecks++;
            level.playSound(null, t.getX(), t.getEyeY(), t.getZ(), ModSounds.VIGIL_OWL_PECK.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    private void face(Entity e) {
        face(e.position());
    }

    private void face(Vec3 at) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        called.clear();
        turned.clear();
        marked = null;
        turnedFrom = null;
        super.onRemoval(reason);
    }

    // ---- test hooks ----------------------------------------------------------------------------------------------

    /**
     * Public as a test hook: from now on it watches only entities that pass {@code filter}, so a test's owl can't pick
     * up a player from a neighbouring test; null watches everyone again.
     */
    public void watchOnly(@Nullable Predicate<LivingEntity> filter) {
        watchOnly = filter;
    }

    /** Sets how long it holds its ground before it may flutter off from someone close (0: at once). Public as a test hook. */
    public void holdGround(int ticks) {
        fleeRest = Math.max(0, ticks);
    }

    /** Marks it has given. Public as a test hook. */
    public int marks() {
        return marks;
    }

    /** Pecks that landed. Public as a test hook. */
    public int pecks() {
        return pecks;
    }

    /** The mobs its last call set on the marked one, while the mark is up. Public as a test hook. */
    public List<Mob> called() {
        return List.copyOf(called);
    }

    /** The mobs its call turned on it, for as long as they keep after it. Public as a test hook. */
    public List<Mob> turned() {
        return List.copyOf(turned);
    }

    // ---- sounds --------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.VIGIL_OWL_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.VIGIL_OWL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.VIGIL_OWL_DEATH.get();
    }
}
