package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Flamewright: a robed pyromancer of the fortress halls that fills the hall with fireballs. Every 100 ticks, with a
 * target within 14 blocks in its sight, it stops and draws a burning sigil between its raised hands for 20 ticks (the
 * wind-up: a rising roar, flames circling it, the sigil glowing gold). Then it casts one of three patterns, in turn: a
 * spiral of 12 fireballs over 30 ticks, a ring of 16 at once, or a cross of 4 arms of 3. Each pattern is aimed so one
 * fireball heads straight at its target. A fireball flies straight and level at 0.25 blocks a tick for up to 16 blocks,
 * stops at the first block or hitbox, burns whoever it reaches for the Flamewright's attack damage (a mob's projectile,
 * so a raised shield takes it) and sets nothing alight. The patterns leave gaps to walk through: a ring's fireballs are
 * more than a player's width apart from 4 blocks out, a cross has whole quarters empty, and a spiral turns one way.
 *
 * <p>Counter: hit it during the wind-up. It staggers for 40 ticks and the pattern it was drawing is lost: the next cast
 * is the pattern after it. Only a hit in the wind-up staggers it, and wind-ups are 100 ticks apart, so blows can't keep
 * it down; outside the wind-up hits simply hurt.
 *
 * <p>Fireballs are not entities: the Flamewright moves them each tick as one {@link Shot.Volley} and draws them with
 * particles. They are not saved, and they vanish with it.
 */
public class Flamewright extends TeemingMonster {
    /** IDLE, the wind-up (tell), the three patterns, the stagger after a hit in the wind-up (counter landed). */
    public enum Step implements Phase { IDLE, WINDUP, SPIRAL, RING, CROSS, STAGGER }

    /** The order patterns are cast in; each wind-up takes the next. */
    private static final Step[] TURN = {Step.SPIRAL, Step.RING, Step.CROSS};

    public static final int TELL_TICKS = 20;             // 1 s drawing the sigil: time to close in and land a blow
    public static final int STAGGER_TICKS = 40;          // 2 s reeling after a hit in the wind-up
    public static final int CAST_EVERY = 100;            // ticks from one wind-up to the next
    public static final double CAST_RANGE = 14.0;        // it winds up at targets this close, in its sight
    public static final double KEEP_RANGE = 10.0;        // it walks toward a target until this close and in sight

    public static final double FIREBALL_SPEED = 0.25;    // blocks a tick: slow enough to read and step aside
    public static final double FIREBALL_RANGE = 16.0;    // blocks a fireball flies before it burns out (64 ticks)
    public static final double FIREBALL_RADIUS = 0.25;   // how far from a hitbox a fireball still burns it
    public static final double RELEASE_HEIGHT = 1.1;     // fireballs fly level at chest height, from its feet
    public static final double RELEASE_OUT = 0.6;        // and leave this far out from its middle

    public static final int SPIRAL_COUNT = 12;
    public static final int SPIRAL_TICKS = 30;           // the spiral's 12 fireballs leave over 1.5 s, one turn round
    public static final int RING_COUNT = 16;             // all at once, 22.5 degrees apart
    public static final int RING_TICKS = 10;             // the cast pose held after a ring
    public static final int CROSS_ARMS = 4;
    public static final int CROSS_PER_ARM = 3;
    public static final int CROSS_GAP = 4;               // ticks between the fireballs of one arm: one block apart
    public static final int CROSS_TICKS = 12;

    private static final DustParticleOptions SIGIL_GOLD = new DustParticleOptions(0xFFD36B, 1.0F);

    // ---- patterns ----------------------------------------------------------------------------------------------------

    /**
     * Bullet patterns as plain numbers: when each shot leaves (ticks after the pattern starts) and which way it goes
     * (degrees round from the aim, the same sense as a mob's yaw). Nothing here touches the world, so the shapes can be
     * checked alone and reused by any mob that casts patterns.
     */
    public static final class Patterns {
        private Patterns() {}

        /** One shot of a pattern: it leaves {@code tick} ticks in, {@code yaw} degrees round from the aim. */
        public record Launch(int tick, double yaw) {}

        /** {@code count} shots one after another over {@code ticks}, each {@code turn / count} degrees on from the last. */
        public static List<Launch> spiral(int count, int ticks, double turn) {
            List<Launch> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) out.add(new Launch(i * ticks / count, turn * i / count));
            return out;
        }

        /** {@code count} shots at once, evenly round. */
        public static List<Launch> ring(int count) {
            List<Launch> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) out.add(new Launch(0, 360.0 * i / count));
            return out;
        }

        /** {@code arms} arms evenly round, each a line of {@code perArm} shots leaving {@code gap} ticks apart. */
        public static List<Launch> cross(int arms, int perArm, int gap) {
            List<Launch> out = new ArrayList<>(arms * perArm);
            for (int k = 0; k < perArm; k++) {
                for (int a = 0; a < arms; a++) out.add(new Launch(k * gap, 360.0 * a / arms));
            }
            return out;
        }

        /**
         * The open width between two neighbouring shots of an even ring of {@code count}, {@code distance} blocks out,
         * once each shot's catch {@code radius} is taken off both sides.
         */
        public static double ringGap(int count, double distance, double radius) {
            return 2.0 * Math.PI * distance / count - 2.0 * radius;
        }

        /** The direction of a level shot at {@code yaw} degrees (a mob's yaw: 0 is +Z, 90 is -X). */
        public static Vec3 direction(double yaw) {
            double r = Math.toRadians(yaw);
            return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
        }
    }

    private static final List<Patterns.Launch> SPIRAL = Patterns.spiral(SPIRAL_COUNT, SPIRAL_TICKS, 360.0);
    private static final List<Patterns.Launch> RING = Patterns.ring(RING_COUNT);
    private static final List<Patterns.Launch> CROSS = Patterns.cross(CROSS_ARMS, CROSS_PER_ARM, CROSS_GAP);

    /** The launches of a pattern step, in the order they leave. */
    public static List<Patterns.Launch> launches(Step step) {
        return switch (step) {
            case SPIRAL -> SPIRAL;
            case RING -> RING;
            case CROSS -> CROSS;
            default -> List.of();
        };
    }

    /** How long a pattern step lasts, in ticks. */
    public static int length(Step step) {
        return switch (step) {
            case SPIRAL -> SPIRAL_TICKS;
            case RING -> RING_TICKS;
            case CROSS -> CROSS_TICKS;
            default -> 0;
        };
    }

    // ---- state -------------------------------------------------------------------------------------------------------

    private final Shot.Volley fireballs = new Shot.Volley(this, FIREBALL_RADIUS, new Shot.Rules(this::canBurn, this::drawFireball,
            this::burn, this::fizzle));
    /** The fireballs in the air, for the test hooks; the volley moves them. */
    private final List<Shot> flying = new ArrayList<>();
    private int cooldown;
    /** Which pattern the next wind-up draws, as an index into the turn; a fresh Flamewright starts anywhere in it. */
    private int turn;
    /** The pattern the current wind-up is drawing (null outside one, or once lost). */
    private @Nullable Step drawing;
    // the pattern being cast: which step, how many of its launches have left, the last elapsed tick seen, its aim
    private @Nullable Step casting;
    private int launched;
    private int lastElapsed;
    private float aim;
    private int fired;

    public Flamewright(EntityType<? extends Flamewright> type, Level level) {
        super(type, level, Step.values());
        xpReward = 10;
        turn = random.nextInt(TURN.length);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("flamewright", 0.23);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && getTarget() == null && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && getTarget() == null && super.canContinueToUse();
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

    /** The wind-up is the window to hit it: the sigil glows weak-point gold. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.WINDUP);
    }

    private boolean soul() {
        return "soul_wright".equals(look());
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---- facing --------------------------------------------------------------------------------------------------------

    private float yawTo(Vec3 point) {
        return (float) (Mth.atan2(point.z - getZ(), point.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private void setFacing(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** While it winds up or casts, its body faces exactly the way it aims. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    // ---- the cycle -----------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (!core().idle()) {
            getNavigation().stop();                                          // it stands to draw, cast or reel
            if (!core().in(Step.STAGGER)) setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
            LivingEntity t = core().target();
            if (core().in(Step.WINDUP) && valid(t)) setFacing(yawTo(t.position()));
            return;
        }
        LivingEntity target = getTarget();
        if (!valid(target)) return;
        double d = distanceTo(target);
        boolean sees = getSensing().hasLineOfSight(target);
        if (cooldown == 0 && d <= CAST_RANGE && sees) {
            windUp(target);
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (d > KEEP_RANGE || !sees) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    /**
     * Starts the wind-up at {@code target} (null: the way it faces), drawing the next pattern in turn. Ignores the
     * cooldown and range. True if it started. Public as a test hook.
     */
    public boolean windUp(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel) || !core().idle()) return false;
        if (!startPhase(Step.WINDUP, TELL_TICKS, target)) return false;
        cooldown = CAST_EVERY;
        drawing = TURN[turn];
        turn = (turn + 1) % TURN.length;
        getNavigation().stop();
        if (valid(target)) setFacing(yawTo(target.position()));
        playSound(ModSounds.FLAMEWRIGHT_WINDUP.get(), 1.0F, 0.8F);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.WINDUP && level() instanceof ServerLevel) {
            Step pattern = drawing;
            drawing = null;
            LivingEntity target = core().target();
            if (pattern != null && startPhase(pattern, length(pattern), valid(target) ? target : null)) {
                playSound(ModSounds.FLAMEWRIGHT_CAST.get(), 1.0F, 1.0F);
            } else {
                core().end();
            }
        } else {
            drawing = null;
            core().end();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        int stage = core().in(Step.WINDUP) ? 1 : 0;                          // embers go dark while the sigil shows gold
        if (core().stage() != stage) core().setStage(stage);
        if (core().in(Step.WINDUP)) windUpTick(level);
        else if (core().in(Step.STAGGER) && tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.85, getZ(), 2, 0.25, 0.15, 0.25, 0.01);
        }
        castTick(level);
        fireballs.tick(level);
    }

    /** The wind-up: flames circle it and climb, sparks fly off the sigil, and the roar rises. */
    private void windUpTick(ServerLevel level) {
        int t = core().elapsed();
        double s = getScale();
        ParticleOptions flame = soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        for (int k = 0; k < 2; k++) {
            double a = t * 0.55 + k * Math.PI;
            double r = 0.95 * s;
            double y = getY() + (0.3 + 1.4 * ((t + k * 10) % TELL_TICKS) / (double) TELL_TICKS) * s;
            level.sendParticles(flame, getX() + Math.cos(a) * r, y, getZ() + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0.0);
        }
        if (t % 2 == 0) {
            Vec3 c = sigilCentre();
            level.sendParticles(SIGIL_GOLD, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.0);
        }
        if (t == TELL_TICKS / 2) playSound(ModSounds.FLAMEWRIGHT_WINDUP.get(), 1.0F, 1.05F);
    }

    /** Where the sigil hangs: in front of its chest. */
    private Vec3 sigilCentre() {
        double s = getScale();
        Vec3 f = Patterns.direction(getYRot());
        return position().add(f.scale(0.55 * s)).add(0.0, 1.3 * s, 0.0);
    }

    /**
     * Casts the current pattern: every launch whose tick has come leaves now. A pattern started outside its own tick
     * (an event, a command) still lets its first launches go on its next tick.
     */
    private void castTick(ServerLevel level) {
        Step now = core().phase() instanceof Step s ? s : Step.IDLE;
        List<Patterns.Launch> plan = launches(now);
        if (plan.isEmpty()) {
            casting = null;
            return;
        }
        int t = core().elapsed();
        if (casting != now || t < lastElapsed) {
            casting = now;
            launched = 0;
            LivingEntity target = core().target();
            aim = valid(target) ? yawTo(target.position()) : getYRot();
            setFacing(aim);
        }
        lastElapsed = t;
        while (launched < plan.size() && plan.get(launched).tick() <= t) {
            Patterns.Launch l = plan.get(launched++);
            if (MechanicEvent.post(this, "fireball", core().target())) launch(level, aim + l.yaw());
        }
    }

    /** One fireball, level, from chest height just out from its middle, at {@code yaw}. */
    private void launch(ServerLevel level, double yaw) {
        double s = getScale();
        Vec3 dir = Patterns.direction(yaw);
        Vec3 from = position().add(dir.scale(RELEASE_OUT * s)).add(0.0, RELEASE_HEIGHT * s, 0.0);
        flying.add(fireballs.fire(Shot.Path.line(from, dir, FIREBALL_SPEED, FIREBALL_RANGE)));
        fired++;
        level.sendParticles(soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, from.x, from.y, from.z, 3, 0.08, 0.08, 0.08, 0.02);
    }

    // ---- fireballs -----------------------------------------------------------------------------------------------------

    private boolean canBurn(LivingEntity e) {
        return e != this && !(e instanceof Flamewright) && e.isAlive() && e.canBeHitByProjectile() && !e.isSpectator();
    }

    /**
     * A fireball: each tick a flame at its head that flies on with it at its own speed and slows as flames do, so the
     * head stays bright and the slower flames make a short tail. Flames left standing would line its whole path and
     * hide where the fireballs are now.
     */
    private void drawFireball(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        ParticleOptions flame = soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        // a count of 0 sends one particle with (dx, dy, dz) x speed as its motion
        level.sendParticles(flame, to.x, to.y, to.z, 0, d.x, d.y, d.z, 1.0);
        level.sendParticles(flame, to.x, to.y + 0.06, to.z, 0, d.x * 0.85, d.y * 0.85, d.z * 0.85, 1.0);
    }

    /** A fireball reached a hitbox: it burns for the attack damage (a shield raised toward the Flamewright takes it). */
    private void burn(ServerLevel level, Shot shot, LivingEntity victim, Vec3 at) {
        flying.remove(shot);
        if (MechanicEvent.post(this, "fireball_hit", victim)) {
            victim.hurtServer(level, Shot.damage(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        level.sendParticles(soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.04);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, at.x, at.y, at.z, ModSounds.FLAMEWRIGHT_BURN.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** A fireball hit a wall or burned out: it gutters in a puff of smoke where it stopped. Nothing catches fire. */
    private void fizzle(ServerLevel level, Shot shot, Vec3 at, boolean blocked) {
        flying.remove(shot);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.08, 0.08, 0.08, 0.01);
        if (blocked && random.nextInt(3) == 0) {
            level.playSound(null, at.x, at.y, at.z, ModSounds.FLAMEWRIGHT_FIZZLE.get(), getSoundSource(), 0.5F, 1.0F);
        }
    }

    // ---- the counter: a hit in the wind-up -------------------------------------------------------------------------------

    /**
     * A hit from something (a blow, an arrow) during the wind-up staggers it for 40 ticks and the pattern is lost.
     * Falls, fire and the like are not hits; hits at any other time only hurt.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean inWindUp = core().in(Step.WINDUP);
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && inWindUp && isAlive() && core().in(Step.WINDUP) && (source.getEntity() != null || source.getDirectEntity() != null)) {
            stagger(level, source.getEntity() instanceof LivingEntity by ? by : null);
        }
        return hurt;
    }

    private void stagger(ServerLevel level, @Nullable LivingEntity by) {
        if (!startPhase(Step.STAGGER, STAGGER_TICKS, by)) return;           // a vetoed stagger leaves the wind-up running
        drawing = null;
        Vec3 c = sigilCentre();
        level.sendParticles(SIGIL_GOLD, c.x, c.y, c.z, 16, 0.35, 0.35, 0.35, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.06);
        level.playSound(null, c.x, c.y, c.z, ModSounds.FLAMEWRIGHT_STAGGER.get(), getSoundSource(), 1.0F, 1.0F);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        fireballs.clear();
        flying.clear();
        super.onRemoval(reason);
    }

    // ---- test hooks ----------------------------------------------------------------------------------------------------

    /** The fireballs in the air now. */
    public List<Shot> fireballs() {
        return List.copyOf(flying);
    }

    /** How many fireballs it has cast so far. */
    public int fired() {
        return fired;
    }

    /** The pattern its next wind-up will draw. */
    public Step nextPattern() {
        return TURN[turn];
    }

    /** Sets the pattern its next wind-up will draw (SPIRAL, RING or CROSS). Public as a test hook. */
    public void setNextPattern(Step pattern) {
        for (int i = 0; i < TURN.length; i++) if (TURN[i] == pattern) turn = i;
    }

    /** The pattern the current wind-up is drawing, or null. */
    public @Nullable Step drawing() {
        return drawing;
    }

    /** The way the current pattern is aimed, as a yaw. */
    public float aim() {
        return aim;
    }

    /** Ticks until it may wind up again. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it wind up again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.FLAMEWRIGHT_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.FLAMEWRIGHT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FLAMEWRIGHT_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.1F;
    }
}
