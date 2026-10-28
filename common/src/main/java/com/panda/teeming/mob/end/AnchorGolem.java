package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.charm.Charms;
import com.panda.teeming.effect.EffectNumbers;
import com.panda.teeming.effect.HeavyEffect;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.Zone;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Anchor Golem: a broad, hunched golem of end stone and dark iron that carries a great anchor, its chain wound round
 * its right forearm.
 *
 * <p>Its blow: with its target within 1.5 blocks (across or up and down, whichever is more) and in sight, it plants its
 * feet and heaves the anchor up over its shoulder for 14 ticks (tell), chains rattling and grit falling, while a dark
 * violet ring of radius 1.25 settles on the ground at the target's feet, fixed where the target stood as the tell began.
 * Then the anchor slams into the ring: everyone standing in it (its target, and any player) takes its attack through
 * armour, with no knockback, and is made Heavy for 120 ticks: no jumping, gravity x1.5, Levitation and Slow Falling
 * cancelled and refused. Any Slow Falling the blow cancels is not lost: the golem holds it and gives it back, with the
 * time it had left, the moment the weight lifts. Killing the golem lifts its weight off everyone at once. It swings at
 * most once every 56 ticks (the tell, the swing and a 30-tick rest).
 *
 * <p>Counter: step out of the ring before the anchor falls. An anchor that strikes nobody bites into the bare stone and
 * leaves a sink there for 160 ticks: a ring of radius 1.5 with star-white glints sinking into it. While an Anchor Golem
 * stands in a sink its anchor is held to the ground: it can't raise it, and it drags itself along at half speed. Back
 * away across the sink and it has to drag itself through it to reach you, open to your blows. The sink touches nothing
 * else: a player can stand in it freely. Terrain left behind, and its own weight turned on it.
 */
public class AnchorGolem extends TeemingMonster {
    /** IDLE, the anchor heaved up (tell), the anchor slammed down and hauled back up. */
    public enum Step implements Phase { IDLE, TELL, SWING }

    /** The tell: the anchor held up over its shoulder this long before it falls. */
    public static final int TELL_TICKS = 14;
    /** The blow lands on the swing's first tick; the anchor is hauled back up over the rest. */
    public static final int SWING_TICKS = 12;
    /** Ticks from the end of one swing to the next tell. */
    public static final int COOLDOWN = 30;
    /** Blocks between hitboxes, across or up and down: this close (and in sight) it starts the tell. */
    public static final double START_REACH = 1.5;
    /** The ring where the anchor falls: its radius across, and how far over the ground a body still counts as in it. */
    public static final double STRIKE_RADIUS = 1.25;
    public static final double STRIKE_HEIGHT = 1.5;
    /** The blow: x its attack (7), through armour. */
    public static final float STRIKE = 1.0F;
    /** How long a blow weighs its victim down: 6 s. */
    public static final int HEAVY_TICKS = EffectNumbers.HEAVY_TICKS;
    /** The sink an anchor that struck nobody leaves: its radius, its height over the ground, how long it lasts (8 s). */
    public static final double SINK_RADIUS = 1.5;
    public static final double SINK_HEIGHT = 1.0;
    public static final int SINK_TICKS = 160;
    /** An Anchor Golem in a sink moves at this share of its speed. */
    public static final double MIRED_SPEED = 0.5;
    /** Ticks a golem stays held after it leaves a sink (each tick inside holds it this long again). */
    public static final int MIRED_GRACE = 2;
    /** Degrees a tick it turns toward the ring while it heaves. */
    public static final float TURN = 15.0F;
    public static final double WALK_SPEED = 0.18;
    public static final double KNOCKBACK_RESISTANCE = 0.7;
    public static final int XP = 15;

    /** Dark violet: the rings' colour, deep enough to read on pale end stone and well off weak-point gold. */
    public static final int DARK_VIOLET = 0x4A1E86;
    /** Star-white: the glints that sink into a sink. */
    public static final int STAR_WHITE = 0xEEF0FF;
    private static final int VIOLET_ARGB = 0xFF000000 | DARK_VIOLET;
    private static final int STAR_ARGB = 0xFF000000 | STAR_WHITE;
    private static final DustParticleOptions VOID_EDGE = new DustParticleOptions(0x24163E, 0.8F);
    private static final DustParticleOptions VIOLET_DUST = new DustParticleOptions(DARK_VIOLET, 0.9F);
    private static final int RIM_POINTS = 18;

    /** Flag bit: it stands in a sink, its anchor held to the ground (the model drags it). */
    public static final int FLAG_MIRED = 0;

    private static final Identifier MIRED_ID = TeemingMod.id("anchor_golem_mired");
    /** The effects Heavy cancels; the beneficial ones a blow cancels are held and given back. */
    private static final List<Holder<MobEffect>> LIFTS = List.of(MobEffects.SLOW_FALLING, MobEffects.LEVITATION);

    private final AstralAugur.GroundMarks ring = new AstralAugur.GroundMarks(STRIKE_HEIGHT)
            .affects(this::strikable, this::strikeHit)
            .drawn(this::drawRing, this::drawStruck, this::drawFaded);
    private final Zone sinks = new Zone(Zone.Shape.disc(SINK_RADIUS, SINK_HEIGHT), 1)
            .affects(e -> e instanceof AnchorGolem g && g.isAlive(), e -> ((AnchorGolem) e).mire())
            .drawn(this::drawSink);
    /** Everyone it has weighed down, and what each one's weight holds back from them. */
    private final Map<UUID, Held> held = new LinkedHashMap<>();
    private int cooldown;
    private long miredUntil = Long.MIN_VALUE;
    private int tells, strikes, weighs, sinksLeft, mires, lifts;

    /** One victim: who it is, and the effects the weight took off them that come back when it lifts. */
    private static final class Held {
        final LivingEntity victim;
        final List<MobEffectInstance> kept = new ArrayList<>(1);

        Held(LivingEntity victim) {
            this.victim = victim;
        }
    }

    public AnchorGolem(EntityType<? extends AnchorGolem> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("anchor_golem", WALK_SPEED).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
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

            /** It closes in only: every blow is the tell and the swing, started from its AI step. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
            }
        });
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F) {
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- every tick ------------------------------------------------------------------------------------------------

    /**
     * Its sink, its hold on those it weighed down and the flag the model drags its anchor by run from {@code tick()},
     * so they keep working for a mob without AI.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        sinks.tick(level);
        tickHeld(level);
        boolean m = mired();
        if (core().flag(FLAG_MIRED) != m) core().setFlag(FLAG_MIRED, m);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        boolean m = mired();
        drag(m);
        if (core().in(Step.TELL)) {
            holdStill();
            heaveTick(level);
            return;
        }
        if (core().in(Step.SWING)) {
            holdStill();
            return;
        }
        if (m && tickCount % 4 == 0) dragMarks(level);
        LivingEntity target = getTarget();
        if (!m && cooldown == 0 && valid(target) && MobCore.gap(this, target) <= START_REACH && getSensing().hasLineOfSight(target)) windUp(target);
    }

    // ---- the blow --------------------------------------------------------------------------------------------------

    /**
     * Starts the tell at {@code target}: the ring settles at its feet, fixed there, and the anchor goes up. True if it
     * did. Public as a test hook.
     */
    public boolean windUp(LivingEntity target) {
        if (!core().idle() || cooldown > 0 || mired() || isDeadOrDying() || !(level() instanceof ServerLevel level)) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        ring.place(level, List.of(target.position()), STRIKE_RADIUS, TELL_TICKS);
        holdStill();
        face(target.position(), 360.0F);
        Telegraph.particles(grit(level), 14, 0.7, 1.0).withSound(ModSounds.ANCHOR_GOLEM_TELL.get(), 1.2F, 0.9F).play(this);
        tells++;
        return true;
    }

    /** The tell, each tick: it turns to the ring, the ring settles, grit falls off the raised anchor, chains rattle. */
    private void heaveTick(ServerLevel level) {
        List<AstralAugur.GroundMarks.Mark> marks = ring.marks();
        if (!marks.isEmpty()) face(marks.getFirst().centre(), TURN);
        ring.tick(level);
        Vec3 anchor = raisedAnchor();
        if (core().elapsed() % 2 == 0) {
            level.sendParticles(grit(level), true, true, anchor.x, anchor.y, anchor.z, 3, 0.35, 0.2, 0.35, 0.0);
        }
        if (core().elapsed() == TELL_TICKS / 2) {
            level.playSound(null, anchor.x, anchor.y, anchor.z, ModSounds.ANCHOR_GOLEM_TELL.get(), getSoundSource(), 0.7F, 1.15F);
        }
    }

    /** Where the anchor is while it is held up: over its right shoulder, above its head. */
    private Vec3 raisedAnchor() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);          // its right
        return new Vec3(getX() + rx * 0.7, getY() + getBbHeight() * 1.2, getZ() + rz * 0.7);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            swing(level);
        } else {
            core().end();
            if (ended == Step.SWING) cooldown = COOLDOWN;
        }
    }

    /**
     * The end of the tell: the anchor comes down on the ring. Everyone standing in it is struck; if no one is, the
     * anchor bites into the stone and leaves a sink there.
     */
    private void swing(ServerLevel level) {
        List<AstralAugur.GroundMarks.Mark> marks = ring.marks();
        if (isDeadOrDying() || marks.isEmpty() || !startPhase(Step.SWING, SWING_TICKS, core().target())) {
            ring.cancel(level);
            core().end();
            cooldown = COOLDOWN;
            return;
        }
        Vec3 at = marks.getFirst().centre();
        level.playSound(null, at.x, at.y, at.z, ModSounds.ANCHOR_GOLEM_SWING.get(), getSoundSource(), 1.4F, 0.9F + random.nextFloat() * 0.1F);
        List<LivingEntity> inside = ring.strike(level);
        if (inside.isEmpty()) sink(level, at);
    }

    /** Who the anchor strikes in the ring: its target, and any player (never another Anchor Golem or a bystander). */
    private boolean strikable(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof AnchorGolem) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e == core().target();
    }

    /** One body in the ring: its attack through armour, with no knockback, then the weight. */
    private void strikeHit(ServerLevel level, LivingEntity victim) {
        if (isDeadOrDying() || !MechanicEvent.post(this, "strike", victim)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE;
        if (!AstralAugur.GroundMarks.hurtInPlace(level, victim, damageSources().mobAttack(this), damage)) return;
        strikes++;
        setLastHurtMob(victim);
        weigh(level, victim);
    }

    /**
     * Makes {@code victim} Heavy for {@link #HEAVY_TICKS} (half that with a Rot Ward Charm in the off-hand). Any
     * beneficial lift it had (Slow Falling) is held, with the time it had left, and given back when the weight lifts.
     * Public as a test hook.
     */
    public boolean weigh(ServerLevel level, LivingEntity victim) {
        if (!victim.isAlive() || !MechanicEvent.post(this, "weigh", victim)) return false;
        List<MobEffectInstance> lifted = new ArrayList<>(1);
        for (Holder<MobEffect> lift : LIFTS) {
            MobEffectInstance on = victim.getEffect(lift);
            if (on != null && on.getEffect().value().isBeneficial()) lifted.add(new MobEffectInstance(on));
        }
        if (!ModEffects.give(victim, ModEffects.HEAVY, Charms.rotTicks(victim, HEAVY_TICKS), this)) return false;
        held.computeIfAbsent(victim.getUUID(), k -> new Held(victim)).kept.addAll(lifted);
        weighs++;
        double top = victim.getY() + victim.getBbHeight();
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.ANCHOR_GOLEM_WEIGHED.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(grit(level), true, true, victim.getX(), top + 0.4, victim.getZ(), 16, 0.35, 0.2, 0.35, 0.0);
        for (int i = 0; i < 8; i++) {                       // a weight pressing down on it, from above to its feet
            double a = i * (Math.PI * 2 / 8), r = victim.getBbWidth() * 0.6;
            double x = victim.getX() + Math.cos(a) * r, z = victim.getZ() + Math.sin(a) * r;
            level.sendParticles(new TrailParticleOption(new Vec3(x, victim.getY() + 0.05, z), VIOLET_ARGB, 8), true, true,
                    x, top + 0.8, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        return true;
    }

    // ---- the weight: kept up while it lasts, given back when it lifts ---------------------------------------------

    private void tickHeld(ServerLevel level) {
        if (held.isEmpty()) return;
        Iterator<Held> it = held.values().iterator();
        while (it.hasNext()) {
            Held h = it.next();
            LivingEntity v = h.victim;
            if (v.isRemoved() || !v.isAlive()) {                 // gone, or dead: nothing is left to give back
                it.remove();
            } else if (!HeavyEffect.heavy(v)) {                  // the weight has lifted on its own
                giveBack(level, h, false);
                it.remove();
            } else if ((tickCount + v.getId()) % 5 == 0) {       // grit keeps falling off a weighed-down body
                level.sendParticles(grit(level), true, true, v.getX(), v.getY() + v.getBbHeight() + 0.3, v.getZ(), 2, 0.25, 0.1, 0.25, 0.0);
            }
        }
    }

    /** Gives {@code h}'s victim back what its weight held; {@code lift} first takes the weight itself off. */
    private void giveBack(ServerLevel level, Held h, boolean lift) {
        LivingEntity v = h.victim;
        if (lift && HeavyEffect.heavy(v)) v.removeEffect(ModEffects.HEAVY.holder());
        boolean any = lift;
        for (MobEffectInstance kept : h.kept) any |= v.addEffect(new MobEffectInstance(kept), this);
        if (any) {
            lifts++;
            level.playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.ANCHOR_GOLEM_LIFT.get(), getSoundSource(), 0.8F, 1.0F);
            level.sendParticles(ParticleTypes.END_ROD, true, true, v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** Lifts its weight off everyone it holds and gives back what it took: its death, or its leaving the world. */
    private void releaseAll() {
        if (level() instanceof ServerLevel level) {
            for (Held h : held.values()) if (!h.victim.isRemoved() && h.victim.isAlive()) giveBack(level, h, true);
        }
        held.clear();
    }

    // ---- the counter: the sink -------------------------------------------------------------------------------------

    /** An anchor that struck nobody: it bites into the stone at {@code at} and leaves a sink there. */
    private void sink(ServerLevel level, Vec3 at) {
        if (!MechanicEvent.post(this, "sink", null)) return;
        sinks.open(level, at, SINK_TICKS);
        sinksLeft++;
        level.playSound(null, at.x, at.y, at.z, ModSounds.ANCHOR_GOLEM_SINK.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, at)), true, true, at.x, at.y + 0.1, at.z,
                30, SINK_RADIUS * 0.4, 0.1, SINK_RADIUS * 0.4, 0.15);
        for (int i = 0; i < RIM_POINTS; i++) {               // the ring spreads out from where it struck
            double a = i * (Math.PI * 2 / RIM_POINTS);
            double x = at.x + Math.cos(a) * SINK_RADIUS, z = at.z + Math.sin(a) * SINK_RADIUS;
            level.sendParticles(new TrailParticleOption(new Vec3(x, at.y + 0.08, z), VIOLET_ARGB, 8), true, true,
                    at.x, at.y + 0.1, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Holds this golem's anchor to the ground for this tick and the next {@link #MIRED_GRACE}: called each tick it stands
     * in a sink. Public as a test hook.
     */
    public void mire() {
        if (!(level() instanceof ServerLevel level) || isDeadOrDying()) return;
        if (!mired()) {
            if (!MechanicEvent.post(this, "mire", null)) return;
            mires++;
            Vec3 a = anchorOnGround();
            level.playSound(null, a.x, a.y, a.z, ModSounds.ANCHOR_GOLEM_MIRED.get(), getSoundSource(), 1.2F, 0.9F);
            level.sendParticles(VIOLET_DUST, true, true, a.x, a.y + 0.3, a.z, 12, 0.4, 0.2, 0.4, 0.0);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, a)), true, true, a.x, a.y + 0.1, a.z, 12, 0.4, 0.05, 0.4, 0.1);
        }
        miredUntil = level.getGameTime() + MIRED_GRACE;
    }

    /** True while its anchor is held in a sink. */
    public boolean mired() {
        return level().getGameTime() < miredUntil;
    }

    /** Half speed while held: a modifier it owns, off again the tick it is free (and on every way out). */
    private void drag(boolean on) {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (on && !speed.hasModifier(MIRED_ID)) {
            speed.addTransientModifier(new AttributeModifier(MIRED_ID, MIRED_SPEED - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!on && speed.hasModifier(MIRED_ID)) {
            speed.removeModifier(MIRED_ID);
        }
    }

    /** Where its anchor drags on the ground: at its right side. */
    private Vec3 anchorOnGround() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(getX() - Mth.cos(yaw) * 0.9, getY(), getZ() - Mth.sin(yaw) * 0.9);
    }

    /** The anchor dragging through the sink: grit and sparks off its flukes, and a violet thread down into the ground. */
    private void dragMarks(ServerLevel level) {
        Vec3 a = anchorOnGround();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, a)), true, true, a.x, a.y + 0.1, a.z, 4, 0.3, 0.05, 0.3, 0.05);
        level.sendParticles(ParticleTypes.CRIT, true, true, a.x, a.y + 0.2, a.z, 2, 0.25, 0.05, 0.25, 0.05);
        level.sendParticles(new TrailParticleOption(a, VIOLET_ARGB, 6), true, true, a.x, a.y + 0.9, a.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    // ---- drawing the rings -----------------------------------------------------------------------------------------

    /**
     * The ring where the anchor will fall, every other tick: a dark violet rim (full bright, so it shows in the End's
     * dim light) with a void-indigo edge, and the anchor's shadow closing from the rim to the middle as it comes due.
     */
    private void drawRing(ServerLevel level, AstralAugur.GroundMarks.Mark mark, float fill) {
        if (tickCount % 2 != 0) return;
        Vec3 c = mark.centre();
        double r = mark.radius(), y = c.y + 0.08;
        double turn = tickCount * 0.03;
        for (int i = 0; i < RIM_POINTS; i++) {
            double a = turn + i * (Math.PI * 2 / RIM_POINTS);
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            level.sendParticles(new TrailParticleOption(new Vec3(x, y, z), VIOLET_ARGB, 4), true, true, x, y + 0.02, z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 2 == 0) level.sendParticles(VOID_EDGE, true, true, c.x + Math.cos(a) * (r + 0.15), y, c.z + Math.sin(a) * (r + 0.15), 1, 0.0, 0.0, 0.0, 0.0);
        }
        double front = r * (1.0 - fill);
        int n = Math.max(4, (int) Math.ceil(Math.PI * 2 * Math.max(front, 0.1) / 0.4));
        for (int i = 0; i < n; i++) {
            double a = -turn + i * (Math.PI * 2 / n);
            double x = c.x + Math.cos(a) * front, z = c.z + Math.sin(a) * front;
            level.sendParticles(VIOLET_DUST, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** The anchor lands: stone thrown up across the ring. */
    private void drawStruck(ServerLevel level, AstralAugur.GroundMarks.Mark mark) {
        Vec3 c = mark.centre();
        double r = mark.radius();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, c)), true, true, c.x, c.y + 0.1, c.z, 36, r * 0.5, 0.1, r * 0.5, 0.2);
        level.sendParticles(grit(level), true, true, c.x, c.y + 0.6, c.z, 12, r * 0.4, 0.3, r * 0.4, 0.0);
    }

    /** A ring that never fell (its tell was cut short): its rim drifts up and fades. */
    private void drawFaded(ServerLevel level, AstralAugur.GroundMarks.Mark mark) {
        Vec3 c = mark.centre();
        for (int i = 0; i < 10; i++) {
            double a = i * (Math.PI * 2 / 10);
            double x = c.x + Math.cos(a) * mark.radius(), z = c.z + Math.sin(a) * mark.radius();
            level.sendParticles(new TrailParticleOption(new Vec3(x, c.y + 0.8, z), VIOLET_ARGB, 8), true, true, x, c.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * A sink, every other tick: a still dark violet rim with a void-indigo edge, and star-white glints drawn in from it
     * and down into the middle.
     */
    private void drawSink(ServerLevel level, Vec3 c) {
        if (tickCount % 2 != 0) return;
        double y = c.y + 0.08;
        for (int i = 0; i < RIM_POINTS; i++) {
            double a = i * (Math.PI * 2 / RIM_POINTS);
            double x = c.x + Math.cos(a) * SINK_RADIUS, z = c.z + Math.sin(a) * SINK_RADIUS;
            double xi = c.x + Math.cos(a) * (SINK_RADIUS - 0.3), zi = c.z + Math.sin(a) * (SINK_RADIUS - 0.3);
            level.sendParticles(new TrailParticleOption(new Vec3(xi, y, zi), VIOLET_ARGB, 4), true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 3 == 0) level.sendParticles(VOID_EDGE, true, true, c.x + Math.cos(a) * (SINK_RADIUS + 0.15), y, c.z + Math.sin(a) * (SINK_RADIUS + 0.15), 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = SINK_RADIUS * (0.5 + random.nextDouble() * 0.45);
            double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
            level.sendParticles(new TrailParticleOption(new Vec3(c.x, c.y + 0.02, c.z), STAR_ARGB, 12), true, true, x, y + 0.15, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** End-stone grit falling, or whatever it stands on. */
    private BlockParticleOption grit(ServerLevel level) {
        return new BlockParticleOption(ParticleTypes.FALLING_DUST, groundAt(level, position()));
    }

    /** The block under {@code at}, or end stone where that is air. */
    private static BlockState groundAt(ServerLevel level, Vec3 at) {
        BlockState state = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        return state.isAir() ? Blocks.END_STONE.defaultBlockState() : state;
    }

    // ---- moving and facing -----------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity e) {
        return e != null && e != this && e.isAlive() && !e.isRemoved() && e.level() == level()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void face(Vec3 at, float maxTurn) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** While it heaves and swings its body stays square to the way it faces. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    // ---- death and removal -----------------------------------------------------------------------------------------

    /** A dead golem lands nothing: its ring fades unspent and its weight lifts off everyone it holds. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!isDeadOrDying() || !(level() instanceof ServerLevel level)) return;
        ring.cancel(level);
        releaseAll();
        drag(false);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        releaseAll();
        ring.clear();
        sinks.clear();
        drag(false);
        super.onRemoval(reason);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Lets it swing again at once. Public as a test hook. */
    public void resetSwing() {
        cooldown = 0;
    }

    /** Ticks until it may start another tell. Public as a test hook. */
    public int swingCooldown() {
        return cooldown;
    }

    /** Opens a sink at {@code at}, as a swing that struck nobody would. Public as a test hook. */
    public void openSink(Vec3 at) {
        if (level() instanceof ServerLevel level) sink(level, at);
    }

    /** The centre of the ring the anchor will fall on, or null when none is down. Public as a test hook. */
    public @Nullable Vec3 ringCentre() {
        List<AstralAugur.GroundMarks.Mark> marks = ring.marks();
        return marks.isEmpty() ? null : marks.getFirst().centre();
    }

    /** The centre of its sink, or null. Public as a test hook. */
    public @Nullable Vec3 sinkCentre() {
        List<Vec3> c = sinks.centres();
        return c.isEmpty() ? null : c.getFirst();
    }

    /** True while it holds {@code e} weighed down. Public as a test hook. */
    public boolean holds(LivingEntity e) {
        return held.containsKey(e.getUUID());
    }

    /** Tells started, blows landed, victims weighed down, sinks left, times held in a sink, weights lifted. Test hooks. */
    public int tells() {
        return tells;
    }

    public int strikes() {
        return strikes;
    }

    public int weighs() {
        return weighs;
    }

    public int sinksLeft() {
        return sinksLeft;
    }

    public int mires() {
        return mires;
    }

    public int lifts() {
        return lifts;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.ANCHOR_GOLEM_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ANCHOR_GOLEM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ANCHOR_GOLEM_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.ANCHOR_GOLEM_STEP.get(), 0.8F, 0.9F + random.nextFloat() * 0.1F);
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + random.nextFloat() * 0.1F;
    }
}
