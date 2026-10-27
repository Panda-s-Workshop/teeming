package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Astral Augur: a tall, robed star-reader of the End with a long neck and a halo of three star motes orbiting over its
 * head. It keeps a middle distance from its prey and reads the stars over it.
 *
 * <p>Every 100 ticks, with a target within 16 blocks in its sight, it channels for 24 ticks (tell): it raises its
 * hands, its motes spin faster and a chime climbs, and three circles of radius 1.5 open on the ground: one under the
 * target and two behind it, on the side away from the augur, each a ring of star-white light that fills in from its
 * rim to its middle as the channel runs. Then every circle strikes at once: a falling column of starlight, 9 damage
 * (the augur's attack, through armour) to everything standing inside, once per strike however many circles it
 * stands in, and no knockback, so a strike never throws anyone off an island.
 *
 * <p>Counter: step out of the circles (toward the augur always gets clear: the two flanks lie behind the target), or
 * hit the augur while it channels. A hit breaks the channel: the circles fade and nothing strikes, and one of its
 * halo motes cracks off and falls to the ground as a star mote. Each mote it loses is one circle fewer in every later
 * channel, down to its last mote, which it never loses: a broken part, paid for in loot and a weaker caster.
 *
 * <p>Up close it backhands with a long arm, after a 10-tick wind-up.
 *
 * <p>The circles are a {@link GroundMarks}: the self-contained ground-marking piece at the end of this class.
 */
public class AstralAugur extends TeemingMonster {
    /** IDLE, the channel (tell), the strike, the flinch after a broken channel (counter landed), the backhand. */
    public enum Step implements Phase { IDLE, TELL, STRIKE, BROKEN, SWIPE }

    /** The channel: 24 ticks from the circles opening to their strike. */
    public static final int TELL_TICKS = 24;
    /** Circles a whole halo casts: one per mote. */
    public static final int CIRCLES = 3;
    public static final double CIRCLE_RADIUS = 1.5;
    /** Each circle's strike on everything inside. */
    public static final float STRIKE_DAMAGE = 9.0F;
    /** Ticks from one channel to the next, at the soonest. */
    public static final int CAST_EVERY = 100;
    /** It channels at targets this close, and in its sight. */
    public static final double CAST_RANGE = 16.0;
    /** The two flank circles: this far from the one under the target, 50 degrees either side of straight away from it. */
    public static final double FLANK_SPACING = 2.6;
    public static final double FLANK_SPREAD = 50.0;
    /** A strike reaches whoever's feet are up to 4 blocks over the circle (a jump never clears it). */
    public static final double COLUMN_HEIGHT = 4.0;
    /** After the strike it lowers its arms for this long. */
    public static final int STRIKE_TICKS = 12;
    /** A broken channel: it flinches this long, its halo one mote lighter. */
    public static final int FLINCH_TICKS = 20;
    /** Motes in a whole halo, and the last one it never loses. */
    public static final int MOTES = 3;
    public static final int MIN_MOTES = 1;
    /** The backhand: a 10-tick wind-up, then a blow of its attack (4), at most every 30 ticks. */
    public static final int SWIPE_TICKS = 10;
    public static final int SWIPE_EVERY = 30;
    /** It backs off from a target closer than this, and walks toward one farther than this or out of sight. */
    public static final double KEEP_AWAY = 4.0;
    public static final double CLOSE_IN = 12.0;
    /** A spot to back off toward: this far out and up or down; the short search when no far spot is open. */
    public static final int BACK_SEARCH = 8, BACK_SEARCH_Y = 3, BACK_SHORT = 4, BACK_SHORT_Y = 2;

    /** Star-white, the circles' light and the motes: cool and pale, far from weak-point gold. */
    public static final int STAR_WHITE = 0xEEF0FF;
    /** Void indigo, the dark edge round each circle that keeps it readable on pale end stone. */
    public static final int VOID_INDIGO = 0x2A1C4A;
    private static final int STAR_ARGB = 0xFF000000 | STAR_WHITE;
    private static final DustParticleOptions EDGE = new DustParticleOptions(VOID_INDIGO, 0.5F);
    private static final DustParticleOptions GLINT = new DustParticleOptions(STAR_WHITE, 0.7F);
    private static final int RIM_POINTS = 20;

    /** Flag bits 0-1: how many motes its halo has lost, for the model. */
    private static final int MOTES_LOST_MASK = 0b11;

    private int castCooldown;
    private int swipeCooldown;
    private int motesLost;
    private int channels, strikes, breaks;
    private final List<LivingEntity> lastStruck = new ArrayList<>();
    /** The circles that just struck, and the game time the starlight stops raining into them. */
    private final List<GroundMarks.Mark> raining = new ArrayList<>(3);
    private long rainUntil;
    private static final int RAIN_TICKS = 10;

    private final GroundMarks marks = new GroundMarks(COLUMN_HEIGHT)
            .affects(this::strikable, this::strikeHit)
            .drawn(this::drawFilling, this::drawStrike, this::drawFade);

    public AstralAugur(EntityType<? extends AstralAugur> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("astral_augur", 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new KeepRangeGoal());
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F) {
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

    /**
     * With a target it keeps a middle distance: it backs off from one closer than {@link #KEEP_AWAY}, walks toward one
     * beyond {@link #CLOSE_IN} or out of its sight, and otherwise stands and watches.
     */
    private final class KeepRangeGoal extends Goal {
        private int repath;

        KeepRangeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return core().idle() && valid(getTarget());
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
            double d = distanceTo(target);
            if (d < KEEP_AWAY) {
                Vec3 spot = position().add(awayFrom(target.position()).scale(KEEP_AWAY + 1.0 - d));
                getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
            } else if (d > CLOSE_IN || !hasLineOfSight(target)) {
                getNavigation().moveTo(target, 1.0);
            } else {
                getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (castCooldown > 0) castCooldown--;
        if (swipeCooldown > 0) swipeCooldown--;
        marks.tick(level);
        rainTick(level);
        if (core().in(Step.TELL)) {
            channelTick(level, core().target());
            return;
        }
        if (core().in(Step.STRIKE) || core().in(Step.BROKEN)) {
            holdStill();
            return;
        }
        if (core().in(Step.SWIPE)) {
            holdStill();
            LivingEntity t = core().target();
            if (valid(t)) face(t);
            return;
        }
        LivingEntity target = getTarget();
        if (castCooldown == 0 && canCast(target)) {
            channel(target);
        } else if (swipeCooldown == 0 && valid(target) && isWithinMeleeAttackRange(target) && hasLineOfSight(target)) {
            swipe(target);
        }
    }

    /** True when {@code target} is a fair mark: alive, within 16 blocks and in its sight. */
    public boolean canCast(@Nullable LivingEntity target) {
        return valid(target) && distanceTo(target) <= CAST_RANGE && hasLineOfSight(target);
    }

    /**
     * Starts the channel at {@code target}: the circles open under and behind it and begin to fill. True if it started.
     * Public as a test hook.
     */
    public boolean channel(@Nullable LivingEntity target) {
        if (target == null || !core().idle() || castCooldown > 0 || !(level() instanceof ServerLevel level)) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        castCooldown = CAST_EVERY;
        channels++;
        holdStill();
        face(target);
        marks.place(level, GroundMarks.around(target.position(), position(), circles(), FLANK_SPACING, FLANK_SPREAD),
                CIRCLE_RADIUS, TELL_TICKS);
        level.playSound(null, getX(), getEyeY(), getZ(), ModSounds.ASTRAL_AUGUR_CHANNEL.get(), getSoundSource(), 1.2F, 1.0F);
        return true;
    }

    /** The channel: it stands, faces its target, star motes rise from its raised hands and the chime climbs. */
    private void channelTick(ServerLevel level, @Nullable LivingEntity target) {
        holdStill();
        if (valid(target)) face(target);
        int t = core().elapsed();
        if (t % 4 == 0 && t > 0) {
            level.playSound(null, getX(), getEyeY(), getZ(), ModSounds.ASTRAL_AUGUR_CHIME.get(), getSoundSource(),
                    0.9F, 0.8F + 0.05F * t);                                        // a chime that climbs
        }
        if (t % 2 == 0) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 hand = handPoint(side);
                level.sendParticles(new TrailParticleOption(hand.add(0.0, 0.7, 0.0), STAR_ARGB, 8), true, true,
                        hand.x, hand.y, hand.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    /** Where a raised hand is, roughly: above and to one side of its head, turned with its body. */
    private Vec3 handPoint(int side) {
        Vec3 right = Vec3.directionFromRotation(0.0F, yBodyRot + 90.0F);
        double s = getScale();
        return position().add(0.0, 2.35 * s, 0.0).add(right.scale(0.55 * side * s));
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
            return;
        }
        if (ended == Step.TELL) {
            if (startPhase(Step.STRIKE, STRIKE_TICKS, core().target())) {
                strike(level);
            } else {
                marks.cancel(level);                                                  // a vetoed strike: the circles fade
                core().end();
            }
        } else if (ended == Step.SWIPE) {
            finishSwipe(level, core().target());
            core().end();
        } else {
            core().end();
        }
    }

    /** Every circle strikes at once; starlight keeps raining into each for a moment after. */
    private void strike(ServerLevel level) {
        raining.clear();
        for (GroundMarks.Mark m : marks.marks()) raining.add(m);
        rainUntil = level.getGameTime() + RAIN_TICKS;
        lastStruck.clear();
        lastStruck.addAll(marks.strike(level));
        strikes++;
    }

    /** The column after the blow: a few more falling streaks into each struck circle, and slow sparks rising from it. */
    private void rainTick(ServerLevel level) {
        if (raining.isEmpty()) return;
        if (level.getGameTime() >= rainUntil) {
            raining.clear();
            return;
        }
        for (GroundMarks.Mark m : raining) {
            Vec3 c = m.centre();
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * m.radius() * 0.4;
                double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
                level.sendParticles(new TrailParticleOption(new Vec3(x, c.y + 0.05, z), STAR_ARGB, 4), true, true,
                        x, c.y + 3.0 + random.nextDouble() * 3.0, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(GLINT, true, true, c.x, c.y + 1.5, c.z, 3, m.radius() * 0.3, 1.2, m.radius() * 0.3, 0.0);
            if (tickCount % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, true, true, c.x, c.y + 0.3, c.z, 1, m.radius() * 0.4, 0.1, m.radius() * 0.4, 0.02);
        }
    }

    private boolean strikable(LivingEntity e) {
        return e != this && !(e instanceof AstralAugur) && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative());
    }

    /**
     * One victim of the strike: 9 as the augur's own attack, with no knockback. It goes through armour like any blow:
     * magic that skipped armour would leave only Protection, and a Nullcantor's ring turns that off.
     */
    private void strikeHit(ServerLevel level, LivingEntity victim) {
        GroundMarks.hurtInPlace(level, victim, damageSources().mobAttack(this), STRIKE_DAMAGE);
    }

    // ---- the broken channel: the counter's reward ------------------------------------------------------------------

    /**
     * A hit from something (a blow, an arrow) while it channels breaks the channel: the circles fade, nothing strikes,
     * it flinches and a halo mote cracks off.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean channelling = core().in(Step.TELL);
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && channelling && isAlive() && core().in(Step.TELL) && (source.getEntity() != null || source.getDirectEntity() != null)) {
            breakChannel(level, source.getEntity() instanceof LivingEntity by ? by : null);
        }
        return hurt;
    }

    private void breakChannel(ServerLevel level, @Nullable LivingEntity by) {
        if (!startPhase(Step.BROKEN, FLINCH_TICKS, by)) return;              // a vetoed break leaves the channel running
        marks.cancel(level);
        breaks++;
        Vec3 halo = position().add(0.0, getBbHeight() * 1.08, 0.0);
        level.sendParticles(ParticleTypes.FIREWORK, true, true, halo.x, halo.y, halo.z, 16, 0.3, 0.2, 0.3, 0.12);
        level.playSound(null, halo.x, halo.y, halo.z, ModSounds.ASTRAL_AUGUR_SHATTER.get(), getSoundSource(), 1.2F, 1.0F);
        if (motesLost < MOTES - MIN_MOTES) {
            setMotesLost(motesLost + 1);
            BuiltInRegistries.ITEM.getOptional(TeemingMod.id("star_mote"))
                    .ifPresent(item -> spawnAtLocation(level, new ItemStack(item), (float) (getBbHeight() * 0.9)));
            // the mote falls: a streak from the halo to the ground
            level.sendParticles(new TrailParticleOption(position().add(0.0, 0.1, 0.0), STAR_ARGB, 10), true, true,
                    halo.x, halo.y, halo.z, 6, 0.15, 0.1, 0.15, 0.0);
        }
    }

    /** Circles it casts now: one per mote left in its halo. */
    public int circles() {
        return MOTES - motesLost;
    }

    private void setMotesLost(int lost) {
        motesLost = Mth.clamp(lost, 0, MOTES - MIN_MOTES);
        int flags = core().flags();
        for (int bit = 0; bit < 2; bit++) {
            boolean on = (motesLost >> bit & 1) != 0;
            if (((flags >> bit) & 1) != (on ? 1 : 0)) core().setFlag(bit, on);
        }
    }

    /** From synced flags: how many motes its halo has lost. */
    public static int motesLost(int flags) {
        return flags & MOTES_LOST_MASK;
    }

    // ---- the backhand ----------------------------------------------------------------------------------------------

    private void swipe(LivingEntity target) {
        if (!startPhase(Step.SWIPE, SWIPE_TICKS, target)) return;
        swipeCooldown = SWIPE_EVERY;
        face(target);
        playSound(ModSounds.ASTRAL_AUGUR_SWIPE.get(), 1.0F, 1.0F);
        if (level() instanceof ServerLevel level) {
            Vec3 hand = handPoint(-1).add(0.0, -0.9 * getScale(), 0.0);
            level.sendParticles(new TrailParticleOption(hand.add(0.0, 0.4, 0.0), STAR_ARGB, 6), true, true,
                    hand.x, hand.y, hand.z, 4, 0.1, 0.1, 0.1, 0.0);
        }
    }

    /** The end of the wind-up: the blow lands if the target is still within reach. */
    private void finishSwipe(ServerLevel level, @Nullable LivingEntity target) {
        if (valid(target) && isWithinMeleeAttackRange(target) && hasLineOfSight(target)) doHurtTarget(level, target);
    }

    // ---- how the circles look --------------------------------------------------------------------------------------

    /**
     * A filling circle, every other tick: a star-white rim with a thin dark edge just outside it, and a front of light
     * that closes from the rim to the middle as the channel runs, with sparks in the part already filled. Each point of
     * light is drawn twice: a full-bright trail particle, which shows in the End's dim light, and a white dust mote,
     * which shows in daylight; the dark edge keeps the ring apart from pale end stone.
     */
    private void drawFilling(ServerLevel level, GroundMarks.Mark mark, float fill) {
        if (tickCount % 2 != 0) return;
        Vec3 c = mark.centre();
        double r = mark.radius();
        double y = c.y + 0.15;
        double turn = tickCount * 0.05;
        for (int i = 0; i < RIM_POINTS; i++) {
            double a = turn + i * (Math.PI * 2 / RIM_POINTS);
            light(level, c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r, 3);
        }
        if (tickCount % 6 == 0) {
            for (int i = 0; i < 12; i++) {
                double a = -turn + (i + 0.5) * (Math.PI * 2 / 12);
                level.sendParticles(EDGE, true, true, c.x + Math.cos(a) * (r + 0.2), y, c.z + Math.sin(a) * (r + 0.2), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        double front = r * (1.0 - fill);
        if (front > 0.12) {
            int n = Math.max(6, (int) Math.ceil(Math.PI * 2 * front / 0.45));
            for (int i = 0; i < n; i++) {
                double a = turn * 2 + i * (Math.PI * 2 / n);
                light(level, c.x + Math.cos(a) * front, y, c.z + Math.sin(a) * front, 3);
            }
        } else {
            light(level, c.x, y, c.z, 3);
        }
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = front + random.nextDouble() * (r - front);
            light(level, c.x + Math.cos(a) * d, y, c.z + Math.sin(a) * d, 5);
        }
    }

    /** One point of the circles' light: full bright for the End, white dust for daylight. */
    private static void light(ServerLevel level, double x, double y, double z, int ticks) {
        star(level, x, y, z, 0.0, ticks);
        level.sendParticles(GLINT, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** The strike: a column of starlight falling into the circle, a burst of sparks and a flash spreading off its rim. */
    private void drawStrike(ServerLevel level, GroundMarks.Mark mark) {
        Vec3 c = mark.centre();
        double r = mark.radius();
        for (int i = 0; i < 18; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * r * 0.45;
            double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
            double top = c.y + 2.5 + random.nextDouble() * 4.0;
            level.sendParticles(new TrailParticleOption(new Vec3(x, c.y + 0.05, z), STAR_ARGB, 3 + random.nextInt(3)), true, true,
                    x, top, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (int i = 0; i < 20; i++) {
            double a = i * (Math.PI * 2 / 20);
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            level.sendParticles(new TrailParticleOption(new Vec3(c.x + Math.cos(a) * (r + 0.5), c.y + 0.1, c.z + Math.sin(a) * (r + 0.5)),
                    STAR_ARGB, 6), true, true, x, c.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.FIREWORK, true, true, c.x, c.y + 0.2, c.z, 10, r * 0.4, 0.1, r * 0.4, 0.06);
        level.sendParticles(GLINT, true, true, c.x, c.y + 1.6, c.z, 24, r * 0.3, 1.4, r * 0.3, 0.0);   // the column, by daylight
        level.playSound(null, c.x, c.y, c.z, ModSounds.ASTRAL_AUGUR_STRIKE.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** A circle fading out unspent: its rim drifts up and goes dark. */
    private void drawFade(ServerLevel level, GroundMarks.Mark mark) {
        Vec3 c = mark.centre();
        double r = mark.radius();
        for (int i = 0; i < 12; i++) {
            double a = i * (Math.PI * 2 / 12);
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            level.sendParticles(new TrailParticleOption(new Vec3(x, c.y + 0.9, z), STAR_ARGB, 8), true, true, x, c.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(EDGE, true, true, c.x, c.y + 0.2, c.z, 6, r * 0.5, 0.1, r * 0.5, 0.0);
    }

    private static void star(ServerLevel level, double x, double y, double z, double rise, int ticks) {
        level.sendParticles(new TrailParticleOption(new Vec3(x, y + rise, z), STAR_ARGB, ticks), true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    // ---- moving and facing -----------------------------------------------------------------------------------------

    /** While it channels, strikes or swings its body faces the way it looks. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    private void face(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    /**
     * Which way to back off from {@code threat}: toward an open spot away from it, or straight away when none is open
     * (by a wall) or the spot found lies off to the side.
     */
    private Vec3 awayFrom(Vec3 threat) {
        Vec3 away = position().subtract(threat).horizontal();
        if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0.0F, getYRot()).scale(-1.0);
        away = away.normalize();
        Vec3 spot = DefaultRandomPos.getPosAway(this, BACK_SEARCH, BACK_SEARCH_Y, threat);
        if (spot == null) spot = DefaultRandomPos.getPosAway(this, BACK_SHORT, BACK_SHORT_Y, threat);
        Vec3 dir = spot == null ? away : spot.subtract(position()).horizontal();
        return dir.lengthSqr() < 1.0E-4 || dir.normalize().dot(away) < 0.7 ? away : dir.normalize();
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        marks.clear();
        lastStruck.clear();
        raining.clear();
        super.onRemoval(reason);
    }

    // ---- saved state -----------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("MotesLost", motesLost);
        output.putInt("CastCooldown", castCooldown);
    }

    /** The halo's lost motes and the wait to the next channel survive a reload; a channel in progress ends. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setMotesLost(input.getIntOr("MotesLost", 0));
        castCooldown = Math.max(0, input.getIntOr("CastCooldown", 0));
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Ticks until it may channel again. Public as a test hook. */
    public int cooldown() {
        return castCooldown;
    }

    /** Sets the wait until it may channel again (0: at once). Public as a test hook. */
    public void setCooldown(int ticks) {
        castCooldown = Math.max(0, ticks);
    }

    /** Lets it swing again at once. Public as a test hook. */
    public void resetSwipe() {
        swipeCooldown = 0;
    }

    /** Motes its halo has lost. Public as a test hook. */
    public int motesLost() {
        return motesLost;
    }

    /** The circles on the ground now. Public as a test hook. */
    public List<GroundMarks.Mark> marks() {
        return marks.marks();
    }

    /** What the last strike hit. Public as a test hook. */
    public List<LivingEntity> lastStruck() {
        return List.copyOf(lastStruck);
    }

    /** Channels started, strikes made, channels broken. Public as test hooks. */
    public int channels() {
        return channels;
    }

    public int strikes() {
        return strikes;
    }

    public int breaks() {
        return breaks;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.ASTRAL_AUGUR_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ASTRAL_AUGUR_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ASTRAL_AUGUR_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.15F;
    }

    // ================================================================================================================
    // Ground marks: circles on the ground that fill in over a set time, then strike everything standing inside, unless
    // they are cancelled first. Self-contained: it knows nothing of the augur but the callbacks it is given.
    // ================================================================================================================

    /**
     * Circles on the ground that a caster opens, lets fill over a set time and then strikes, or cancels unspent. They
     * live only on the server, in their owner, never as blocks or saved data: a reload or the owner's removal simply
     * ends them. At most one set is down at a time; placing a new set replaces the old one.
     *
     * <p>Use: {@code new GroundMarks(height).affects(who, hit).drawn(filling, struck, faded)} as a field;
     * {@link #place} to open a set (each spot settles on the ground below it); {@link #tick} every tick to draw them
     * filling (it returns true once they are full); {@link #strike} to strike them (each living thing inside is hit
     * once, however many marks it stands in); {@link #cancel} to fade them unspent; {@link #clear} on removal. Where the
     * marks go is the owner's choice; {@link #around} lays out the common one (one on the target, the rest behind it).
     * {@link #hurtInPlace} hurts without knockback, so a strike never throws anyone off an edge.
     */
    public static final class GroundMarks {
        /** One mark: its centre, on the ground, and its radius across. */
        public record Mark(Vec3 centre, double radius) {
            /**
             * Whether a hitbox counts as inside: the middle of its feet within the radius across, its feet no more than a
             * block below the mark and at most {@code height} above it.
             */
            public boolean holds(AABB box, double height) {
                double dx = (box.minX + box.maxX) * 0.5 - centre.x, dz = (box.minZ + box.maxZ) * 0.5 - centre.z;
                return dx * dx + dz * dz <= radius * radius && box.minY >= centre.y - 1.0 && box.minY <= centre.y + height;
            }
        }

        /** Draws one mark as it fills; {@code fill} runs from 0 as it opens to 1 when it is due. */
        @FunctionalInterface
        public interface Filling {
            void draw(ServerLevel level, Mark mark, float fill);
        }

        /** What a strike does to one living thing inside. */
        @FunctionalInterface
        public interface Hit {
            void hit(ServerLevel level, LivingEntity victim);
        }

        /** How far a spot settles: it looks for ground from 2 blocks over it to 4 blocks under it. */
        public static final int GROUND_UP = 2, GROUND_DOWN = 4;

        private final double height;
        private final List<Mark> marks = new ArrayList<>(3);
        private long start, end;
        private Predicate<LivingEntity> who = e -> true;
        private @Nullable Hit hit;
        private @Nullable Filling filling;
        private @Nullable BiConsumer<ServerLevel, Mark> struck, faded;

        /** Marks whose strike reaches up to {@code height} blocks over them. */
        public GroundMarks(double height) {
            this.height = height;
        }

        /** The strike hits each living thing inside that {@code who} accepts, with {@code hit}. */
        public GroundMarks affects(Predicate<LivingEntity> who, Hit hit) {
            this.who = who;
            this.hit = hit;
            return this;
        }

        /** How the marks look: each tick while they fill, once when they strike, once when they fade unspent. */
        public GroundMarks drawn(Filling filling, BiConsumer<ServerLevel, Mark> struck, BiConsumer<ServerLevel, Mark> faded) {
            this.filling = filling;
            this.struck = struck;
            this.faded = faded;
            return this;
        }

        /**
         * Opens a mark of {@code radius} at each spot, settled on the ground below it, due {@code ticks} ticks from now.
         * Replaces any marks already down. Returns the marks.
         */
        public List<Mark> place(ServerLevel level, List<Vec3> spots, double radius, int ticks) {
            marks.clear();
            start = level.getGameTime();
            end = start + Math.max(1, ticks);
            for (Vec3 spot : spots) marks.add(new Mark(ground(level, spot), radius));
            return List.copyOf(marks);
        }

        /** Whether marks are down. */
        public boolean placed() {
            return !marks.isEmpty();
        }

        public List<Mark> marks() {
            return List.copyOf(marks);
        }

        /** How full the marks are at game time {@code now}: 0 as they open, 1 once they are due. */
        public float fill(long now) {
            if (marks.isEmpty()) return 0.0F;
            return (float) Mth.clamp((double) (now - start) / (double) (end - start), 0.0, 1.0);
        }

        /** Draws the marks filling; true once they are due (full and still down). Call every tick. */
        public boolean tick(ServerLevel level) {
            if (marks.isEmpty()) return false;
            long now = level.getGameTime();
            float f = fill(now);
            if (filling != null) for (Mark m : marks) filling.draw(level, m, f);
            return now >= end;
        }

        /** Every living thing the strike would hit now: inside any mark and accepted, each once. */
        public List<LivingEntity> inside(ServerLevel level) {
            if (marks.isEmpty()) return List.of();
            AABB area = null;
            for (Mark m : marks) {
                AABB b = new AABB(m.centre().x - m.radius(), m.centre().y - 1.0, m.centre().z - m.radius(),
                        m.centre().x + m.radius(), m.centre().y + height + 2.0, m.centre().z + m.radius());
                area = area == null ? b : area.minmax(b);
            }
            return level.getEntitiesOfClass(LivingEntity.class, area, e -> who.test(e) && holdsAny(e.getBoundingBox()));
        }

        private boolean holdsAny(AABB box) {
            for (Mark m : marks) if (m.holds(box, height)) return true;
            return false;
        }

        /** Strikes every mark now: each living thing inside is hit once. The marks are spent. Returns who was hit. */
        public List<LivingEntity> strike(ServerLevel level) {
            List<LivingEntity> victims = inside(level);
            if (struck != null) for (Mark m : marks) struck.accept(level, m);
            marks.clear();
            if (hit != null) for (LivingEntity e : victims) hit.hit(level, e);
            return victims;
        }

        /** Fades every mark unspent: nothing is hit. */
        public void cancel(ServerLevel level) {
            if (faded != null) for (Mark m : marks) faded.accept(level, m);
            marks.clear();
        }

        /** Ends every mark at once, unseen (the owner is gone). */
        public void clear() {
            marks.clear();
        }

        /**
         * Where a mark at {@code at} lies: on the ground below it, at most {@link #GROUND_DOWN} blocks down; a spot inside
         * a block first climbs out of it, at most {@link #GROUND_UP} blocks. With no ground in reach (over the void), or
         * buried deeper, it stays where it is.
         */
        public static Vec3 ground(ServerLevel level, Vec3 at) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int x = Mth.floor(at.x), y = Mth.floor(at.y), z = Mth.floor(at.z);
            int from = y;
            while (from - y < GROUND_UP && solid(level, pos.set(x, from, z))) from++;
            if (solid(level, pos.set(x, from, z))) return at;
            for (int air = from; air >= y - GROUND_DOWN; air--) {
                pos.set(x, air - 1, z);
                if (solid(level, pos)) {
                    double top = pos.getY() + level.getBlockState(pos).getCollisionShape(level, pos).max(Direction.Axis.Y);
                    return new Vec3(at.x, top, at.z);
                }
            }
            return at;
        }

        private static boolean solid(ServerLevel level, BlockPos pos) {
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }

        /**
         * The usual layout: the first mark on the target, the rest behind it, away from the caster: one straight behind
         * for two marks, two at {@code spread} degrees either side of straight behind for three or more (the rest of a
         * larger count fans out between them). Every mark after the first lies {@code spacing} blocks from the first.
         */
        public static List<Vec3> around(Vec3 target, Vec3 caster, int count, double spacing, double spread) {
            double[] xz = offsets(target.x - caster.x, target.z - caster.z, count, spacing, spread);
            List<Vec3> out = new ArrayList<>(count);
            for (int i = 0; i < xz.length; i += 2) out.add(new Vec3(target.x + xz[i], target.y, target.z + xz[i + 1]));
            return out;
        }

        /**
         * The layout as plain numbers: x, z offsets from the target for each of {@code count} marks, the first (0, 0).
         * {@code awayX, awayZ} point from the caster to the target; with no length, behind means +z.
         */
        public static double[] offsets(double awayX, double awayZ, int count, double spacing, double spread) {
            int n = Math.max(0, count);
            double[] out = new double[n * 2];
            if (n <= 1) return out;
            double len = Math.sqrt(awayX * awayX + awayZ * awayZ);
            double ax = len < 1.0E-6 ? 0.0 : awayX / len, az = len < 1.0E-6 ? 1.0 : awayZ / len;
            for (int i = 1; i < n; i++) {
                double deg = n == 2 ? 0.0 : -spread + 2.0 * spread * (i - 1) / (n - 2);
                double a = Math.toRadians(deg), cos = Math.cos(a), sin = Math.sin(a);
                out[i * 2] = (ax * cos - az * sin) * spacing;
                out[i * 2 + 1] = (ax * sin + az * cos) * spacing;
            }
            return out;
        }

        /**
         * Hurts {@code victim} without knocking it back (its knockback resistance is full for the one hit), so a mark's
         * strike never throws anyone off an island's edge. True if the hit landed.
         */
        public static boolean hurtInPlace(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
            AttributeInstance resistance = victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            Identifier id = Steady.ID;
            boolean steadied = resistance != null && !resistance.hasModifier(id);
            if (steadied) resistance.addTransientModifier(new AttributeModifier(id, 1.0, AttributeModifier.Operation.ADD_VALUE));
            try {
                return victim.hurtServer(level, source, amount);
            } finally {
                if (steadied) resistance.removeModifier(id);
            }
        }

        /** The modifier that holds a victim steady for one hit (kept apart so the marks' maths loads without it). */
        private static final class Steady {
            static final Identifier ID = TeemingMod.id("ground_mark_steady");
        }
    }
}
