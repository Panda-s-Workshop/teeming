package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.end.Riftjackal.SafeTeleport;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Glimmerless: an invisible stalker of the End highlands and midlands. Unrevealed it is drawn not at all: no body, no
 * shadow, no glow and no potion swirl (it is hidden through the entity's own invisible state, never the Invisibility
 * effect, so nothing shows its effects either). All it leaves are footprints: a pale violet print where each foot comes
 * down, every {@link #STRIDE} blocks it walks, fading over {@link #PRINT_TICKS} ticks, and a soft crunch with every one.
 *
 * <p>Its strike: with its target next to it, it holds still for 8 ticks (tell: a shimmer rises where it stands, its body
 * is drawn faintly for the moment, and it hisses), then rakes both claws through the space in front of it for its
 * attack damage, 7, if the target is still in reach.
 *
 * <p>Counter: any projectile hit (an arrow, a trident, a snowball, an egg, a wind charge: anything whose damage is a
 * projectile's) reveals it for {@link #REVEAL_TICKS} ticks. It is drawn in full, its eyes and seams glow faintly, and a
 * chime of breaking crystal tells the shooter it worked. Being seen is its loss, and a fresh reveal also drives it off:
 * it flinches back for {@link #RECOIL_TICKS} ticks, backing {@link #RECOIL_DISTANCE} blocks away from the shot onto
 * safe ground, which breaks off a strike it was winding up. A hit while it is already seen keeps it seen for the full
 * length again, but it flinches only once per reveal. A melee hit never reveals it. Fire still shows where it burns.
 *
 * <p>With its AI off (a display, a statue) it stands in plain sight. The Shimmering look is drawn faintly even unseen.
 * The reveal and the strike cooldown are saved; footprints are not.
 */
public class Glimmerless extends TeemingMonster {
    /** IDLE, the shimmer (tell), the rake, flinching back from a reveal. */
    public enum Step implements Phase { IDLE, TELL, STRIKE, RECOIL }

    public static final int TELL_TICKS = 8;               // 0.4 s of shimmer before the rake
    public static final int STRIKE_TICKS = 8;             // the rake lands on its first tick; the arms stay out this long
    public static final float STRIKE = 1.0F;              // x attack damage: 7
    public static final int COOLDOWN = 30;                // ticks from the end of one rake to the next tell
    public static final double START_REACH = 0.9;         // blocks between hitboxes: this close it starts its tell
    public static final double STRIKE_REACH = 1.6;        // blocks between hitboxes: a target this close when it rakes is hit
    public static final float STRIKE_ARC = 120.0F;        // degrees in front of it the rake covers
    public static final float TURN = 15.0F;               // degrees a tick it turns after its target during the tell

    public static final int REVEAL_TICKS = 200;           // a projectile hit shows it for 10 s
    public static final int RECOIL_TICKS = 30;            // a fresh reveal makes it flinch back for 1.5 s
    public static final double RECOIL_DISTANCE = 3.0;     // and back off this far from the shot
    public static final double RECOIL_SPEED = 0.15;        // blocks a tick it backs off at

    public static final double STRIDE = 0.9;              // blocks walked between two footprints
    public static final int PRINT_TICKS = 60;             // a footprint fades out over 3 s
    public static final int PRINTS_MAX = 12;
    public static final double PRINT_SIDE = 0.18;         // a foot's print lies this far off the line it walks
    public static final int DRAW_EVERY = 2;               // footprints are redrawn every other tick

    /** {@code MobCore} flag bit: set while it is hidden, so clients draw nothing. */
    public static final int HIDDEN_FLAG = 0;

    /** A fresh print: pale violet, full bright so it shows in the End's dim light. */
    public static final int PRINT_FRESH = 0xFFDCC4FF;
    /** A print about to go: deep void indigo. */
    public static final int PRINT_FADED = 0xFF3A2468;
    private static final int STAR = 0xFFF4F0FF;
    private static final int SEAM = 0xFFD2A8FF;

    private static final long NEVER = Long.MIN_VALUE;

    private long revealedUntil = NEVER;
    private int cooldown;
    private int strikes;
    private int reveals;
    private final Footprints prints = new Footprints();
    private @Nullable Vec3 recoilFrom, recoilStart, recoilDir;

    public Glimmerless(EntityType<? extends Glimmerless> type, Level level) {
        super(type, level, Step.values());
        xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("glimmerless", 0.26);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** It closes in only: every attack is the shimmer and the rake, started from its AI step. */
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
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

    // ---- unseen --------------------------------------------------------------------------------------------------

    /** True while it is hidden: alive, its AI on, and not revealed (both sides, from the synced flag). */
    public boolean hidden() {
        MobCore core = core();
        return core != null && core.flag(HIDDEN_FLAG);   // null while the Entity constructor runs
    }

    /** Hidden, it is invisible: the renderer then draws no body, no glow layer and no shadow. */
    @Override
    public boolean isInvisible() {
        return super.isInvisible() || hidden();
    }

    /**
     * Who still sees a faint outline of it while it is hidden: everyone, for the Shimmering look and during its tell
     * (the renderer then draws the body faint and see-through instead of not at all).
     */
    @Override
    public boolean isInvisibleTo(Player player) {
        if (hidden() && drawnFaint()) return false;
        return super.isInvisibleTo(player);
    }

    /** True when its body shows faintly even while hidden: the Shimmering look, or its tell. */
    public boolean drawnFaint() {
        MobCore core = core();
        return core != null && ("shimmering".equals(core.look()) || core.in(Step.TELL));
    }

    /** No potion swirl gives it away: while hidden its effects send no particles. */
    @Override
    protected void updateInvisibilityStatus() {
        super.updateInvisibilityStatus();
        if (hidden()) removeEffectParticles();
    }

    /** True while a projectile hit shows it. */
    public boolean revealed() {
        return revealedUntil != NEVER && level().getGameTime() < revealedUntil;
    }

    /** Ticks it stays revealed (0: hidden, or about to hide). Public as a test hook. */
    public int revealLeft() {
        return revealedUntil == NEVER ? 0 : (int) Math.max(0L, revealedUntil - level().getGameTime());
    }

    /** True for a hit that reveals it: projectile damage (the vanilla tag, which packs extend) or one a projectile dealt. */
    public static boolean revealsIt(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // before the hit itself: a snowball or an egg deals nothing, and a hit inside the hurt cooldown is refused, but
        // either still shows it
        if (isAlive() && revealsIt(source) && !isInvulnerableTo(level, source)) reveal(level, source);
        return super.hurtServer(level, source, amount);
    }

    /**
     * Reveals it for {@link #REVEAL_TICKS} ticks, as a projectile hit does; a fresh reveal also makes it flinch back
     * from the shot. Public as a test hook.
     */
    public void reveal(ServerLevel level, DamageSource source) {
        Entity owner = source.getEntity();
        Entity direct = source.getDirectEntity();
        LivingEntity who = owner instanceof LivingEntity l ? l : null;
        if (!MechanicEvent.post(this, "reveal", who)) return;
        boolean fresh = !revealed();
        revealedUntil = level.getGameTime() + REVEAL_TICKS;
        reveals++;
        setHidden(false);
        Vec3 c = position().add(0.0, getBbHeight() * 0.55, 0.0);
        for (int i = 0; i < 18; i++) {
            Vec3 out = new Vec3(random.nextDouble() - 0.5, random.nextDouble() * 0.6 - 0.2, random.nextDouble() - 0.5).normalize();
            Vec3 at = c.add(out.x * 0.3, out.y * 0.6, out.z * 0.3);
            level.sendParticles(new TrailParticleOption(at.add(out.scale(0.9)), i % 3 == 0 ? SEAM : STAR, 8), true, true,
                    at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, c.x, c.y, c.z, ModSounds.GLIMMERLESS_REVEAL.get(), getSoundSource(), 1.2F, 1.0F);
        // a shooter out of earshot still hears that it worked
        if (owner instanceof ServerPlayer player && player.distanceTo(this) > 16.0F) {
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.GLIMMERLESS_REVEAL.get()),
                    getSoundSource(), player.getX(), player.getEyeY(), player.getZ(), 0.6F, 1.0F, random.nextLong()));
        }
        if (fresh && !core().in(Step.RECOIL) && !isDeadOrDying()) {
            Vec3 from = owner != null ? owner.position() : direct != null ? direct.position().subtract(direct.getDeltaMovement().scale(3.0)) : null;
            recoil(level, from);
        }
    }

    private void setHidden(boolean hide) {
        if (hide == core().flag(HIDDEN_FLAG)) return;
        core().setFlag(HIDDEN_FLAG, hide);
        updateInvisibilityStatus();
    }

    // ---- the flinch: a forced retreat ------------------------------------------------------------------------------

    /**
     * Flinches back for {@link #RECOIL_TICKS} ticks, backing {@link #RECOIL_DISTANCE} blocks straight away from
     * {@code threat} while it faces it, over safe ground only; with no safe way back it reels where it stands. Either
     * way a tell under way is broken off.
     */
    private void recoil(ServerLevel level, @Nullable Vec3 threat) {
        LivingEntity target = getTarget();
        if (!startPhase(Step.RECOIL, RECOIL_TICKS, target)) return;
        getNavigation().stop();
        recoilFrom = threat;
        recoilStart = position();
        recoilDir = threat == null ? null : retreatDirection(level, threat);
        if (threat != null) face(threat, 360.0F);
    }

    /**
     * Which way it backs off from {@code threat}: straight away, else turning out a little at a time, the first way
     * whose ground is safe a block, two and {@link #RECOIL_DISTANCE} out (never over a drop, the void, lava or a wall,
     * and at most a block up or down). Null when there is none. Public as a test hook.
     */
    public @Nullable Vec3 retreatDirection(ServerLevel level, Vec3 threat) {
        double away = Math.atan2(getZ() - threat.z, getX() - threat.x);
        if (Math.abs(getX() - threat.x) + Math.abs(getZ() - threat.z) < 1.0E-4) away = random.nextDouble() * Math.PI * 2;
        for (double turn : new double[] {0.0, 0.5, -0.5, 1.0, -1.0}) {
            double a = away + turn;
            boolean safe = true;
            for (double d = 1.0; d <= RECOIL_DISTANCE + 1.0E-6 && safe; d += 1.0) {
                Vec3 at = SafeTeleport.InLevel.spot(level, this, getX() + Math.cos(a) * d, getY(), getZ() + Math.sin(a) * d);
                safe = at != null && Math.abs(at.y - getY()) <= 1.01;
            }
            if (safe) return new Vec3(Math.cos(a), 0.0, Math.sin(a));
        }
        return null;
    }

    /** Backing off: {@link #RECOIL_SPEED} blocks a tick until it is {@link #RECOIL_DISTANCE} from where it flinched. */
    private void backOff() {
        getNavigation().stop();
        if (recoilFrom != null) face(recoilFrom, 30.0F);
        Vec3 v = getDeltaMovement();
        boolean going = recoilDir != null && recoilStart != null && onGround()
                && Math.hypot(getX() - recoilStart.x, getZ() - recoilStart.z) < RECOIL_DISTANCE - RECOIL_SPEED * 0.5;
        setDeltaMovement(going ? recoilDir.x * RECOIL_SPEED : 0.0, v.y, going ? recoilDir.z * RECOIL_SPEED : 0.0);
    }

    // ---- the hunt: the shimmer and the rake --------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        boolean wasRevealed = revealedUntil != NEVER;
        if (wasRevealed && !revealed()) {
            revealedUntil = NEVER;
            if (isAlive() && !isNoAi()) fade(level);
        }
        setHidden(isAlive() && !isNoAi() && !revealed());
        long now = level.getGameTime();
        if (isAlive()) {
            Footprints.Print print = prints.track(getX(), getY(), getZ(), onGround() && !isInWater() && !isPassenger(), getYRot(), now);
            if (print != null) stepped(level, print);
        }
        prints.expire(now);
        if (tickCount % DRAW_EVERY == 0) drawPrints(level, now);
        if (core().in(Step.TELL)) shimmer(level);
        if (revealed() && isAlive() && tickCount % 4 == 0) glint(level);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (core().in(Step.RECOIL)) {
            backOff();
            return;
        }
        if (!core().idle()) {
            holdStill();
            LivingEntity t = core().target();
            if (core().in(Step.TELL) && valid(t)) face(t.position(), TURN);
            return;
        }
        LivingEntity target = getTarget();
        if (cooldown == 0 && valid(target) && gapTo(target) <= START_REACH && getSensing().hasLineOfSight(target)) startTell(target);
    }

    /** Starts its shimmer at {@code target}; the rake follows in {@link #TELL_TICKS}. Public as a test hook. */
    public boolean startTell(LivingEntity target) {
        if (!core().idle() || !(level() instanceof ServerLevel level) || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        holdStill();
        face(target.position(), 360.0F);
        level.playSound(null, getX(), getY() + getBbHeight() * 0.7, getZ(), ModSounds.GLIMMERLESS_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            rake(level, core().target());
        } else if (ended == Step.STRIKE) {
            rest();
        } else {
            core().end();
        }
    }

    /** The rake: both claws through the space in front of it; a target in reach and in front takes its attack damage. */
    private void rake(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.STRIKE, STRIKE_TICKS, target)) {
            rest();
            return;
        }
        playSound(ModSounds.GLIMMERLESS_STRIKE.get(), 1.0F, 1.0F);
        Vec3 f = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 claws = position().add(f.x * 1.0, getBbHeight() * 0.45, f.z * 1.0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, claws.x, claws.y, claws.z, 1, 0.0, 0.0, 0.0, 0.0);
        if (valid(target) && gapTo(target) <= STRIKE_REACH && inFront(target) && MechanicEvent.post(this, "rake", target)) {
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE;
            if (target.hurtServer(level, damageSources().mobAttack(this), damage)) {
                strikes++;
                setLastHurtMob(target);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
            }
        }
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
    }

    /** The tell: a shimmer of star-white and lilac light rising through where its body stands, and a ring at its feet. */
    private void shimmer(ServerLevel level) {
        double w = getBbWidth() * 0.5, h = getBbHeight();
        for (int i = 0; i < 4; i++) {
            double x = getX() + (random.nextDouble() * 2 - 1) * w, y = getY() + random.nextDouble() * h, z = getZ() + (random.nextDouble() * 2 - 1) * w;
            level.sendParticles(new TrailParticleOption(new Vec3(x, y + 0.45, z), (i & 1) == 0 ? STAR : SEAM, 6), true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (core().elapsed() % 2 == 0) {
            double turn = core().elapsed() * 0.5;
            for (int i = 0; i < 6; i++) {
                double a = turn + i * (Math.PI / 3);
                double x = getX() + Math.cos(a) * 0.55, z = getZ() + Math.sin(a) * 0.55;
                level.sendParticles(new TrailParticleOption(new Vec3(x, getY() + 0.3, z), SEAM, 4), true, true, x, getY() + 0.05, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** While it is seen it sheds a faint glint now and then. */
    private void glint(ServerLevel level) {
        double w = getBbWidth() * 0.5;
        double x = getX() + (random.nextDouble() * 2 - 1) * w, y = getY() + (0.3 + random.nextDouble() * 0.7) * getBbHeight();
        double z = getZ() + (random.nextDouble() * 2 - 1) * w;
        level.sendParticles(new TrailParticleOption(new Vec3(x, y + 0.5, z), SEAM, 10), false, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** The reveal runs out: its outline shivers and it is gone. */
    private void fade(ServerLevel level) {
        playSound(ModSounds.GLIMMERLESS_FADE.get(), 0.8F, 1.0F);
        double w = getBbWidth() * 0.5;
        for (int i = 0; i < 8; i++) {
            double x = getX() + (random.nextDouble() * 2 - 1) * w, y = getY() + random.nextDouble() * getBbHeight();
            double z = getZ() + (random.nextDouble() * 2 - 1) * w;
            level.sendParticles(new TrailParticleOption(new Vec3(x, y + 0.6, z), SEAM, 8), true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ---- footprints ----------------------------------------------------------------------------------------------------

    /** A foot came down: the crunch, at the print. */
    private void stepped(ServerLevel level, Footprints.Print print) {
        if (!MechanicEvent.post(this, "footprint", getTarget())) {
            prints.forget(print);
            return;
        }
        if (!isSilent()) level.playSound(null, print.x(), print.y(), print.z(), ModSounds.GLIMMERLESS_STEP.get(), getSoundSource(),
                0.45F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** Every print, as a blob of full-bright light on the ground that dims from pale violet to void indigo, then flickers out. */
    private void drawPrints(ServerLevel level, long now) {
        for (Footprints.Print p : prints.prints()) {
            float fade = Footprints.fade(now - p.born());
            if (fade <= 0.0F || (fade < 0.25F && random.nextFloat() > fade * 4.0F)) continue;
            int colour = lerpColour(PRINT_FADED, PRINT_FRESH, fade);
            double y = p.y() + 0.04;
            level.sendParticles(new TrailParticleOption(new Vec3(p.x(), y - 0.02, p.z()), colour, DRAW_EVERY + 1), false, true,
                    p.x(), y, p.z(), 1, 0.0, 0.0, 0.0, 0.0);
            if (fade > 0.6F) {                                     // a fresh print shows its toes too
                double fx = -Mth.sin(p.yaw() * Mth.DEG_TO_RAD) * 0.22, fz = Mth.cos(p.yaw() * Mth.DEG_TO_RAD) * 0.22;
                level.sendParticles(new TrailParticleOption(new Vec3(p.x() + fx, y - 0.02, p.z() + fz), colour, DRAW_EVERY + 1), false, true,
                        p.x() + fx, y, p.z() + fz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    static int lerpColour(int from, int to, float t) {
        int r = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Its prints still on the ground, oldest first. Public as a test hook. */
    public List<Footprints.Print> footprints() {
        return prints.prints();
    }

    /** Its own steps make the crunch, with each print; the block's step sound would give two. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    /**
     * Footprints, written to be shared: a print every {@link #STRIDE} blocks walked on the ground, left and right feet
     * in turn {@link #PRINT_SIDE} off the line walked, each kept {@link #PRINT_TICKS} ticks, at most
     * {@link #PRINTS_MAX}. Plain maths with no game types, so it is checked without a world.
     */
    public static final class Footprints {
        /** One print: where it lies (on the ground under the foot), the way the foot pointed, and when it was left. */
        public record Print(double x, double y, double z, float yaw, int side, long born) {}

        private final List<Print> prints = new ArrayList<>();
        private double walked;
        private double lastX = Double.NaN, lastZ;
        private int side = 1;

        /**
         * The body is at {@code (x, y, z)} facing {@code yawDeg} at time {@code now}; {@code grounded} when its feet are on
         * the ground. Returns the print it left, or null.
         */
        public @Nullable Print track(double x, double y, double z, boolean grounded, float yawDeg, long now) {
            if (!grounded || Double.isNaN(lastX)) {
                lastX = x;
                lastZ = z;
                if (!grounded) walked = 0.0;
                return null;
            }
            double d = Math.hypot(x - lastX, z - lastZ);
            lastX = x;
            lastZ = z;
            if (d > 2.0) {                       // a teleport, not a step
                walked = 0.0;
                return null;
            }
            walked += d;
            if (walked < STRIDE) return null;
            walked -= STRIDE;
            side = -side;
            double yaw = Math.toRadians(yawDeg);
            // the side vector of a body facing yaw (forward is (-sin, cos))
            double sx = Math.cos(yaw), sz = Math.sin(yaw);
            Print p = new Print(x + sx * PRINT_SIDE * side, y, z + sz * PRINT_SIDE * side, yawDeg, side, now);
            prints.add(p);
            while (prints.size() > PRINTS_MAX) prints.removeFirst();
            return p;
        }

        /** Drops the prints that have faded out by {@code now}. */
        public void expire(long now) {
            prints.removeIf(p -> now - p.born() >= PRINT_TICKS);
        }

        void forget(Print p) {
            prints.remove(p);
        }

        public List<Print> prints() {
            return List.copyOf(prints);
        }

        /** How strongly a print {@code age} ticks old still shows: 1 when fresh, falling to 0 as it fades out. */
        public static float fade(long age) {
            if (age < 0 || age >= PRINT_TICKS) return age < 0 ? 1.0F : 0.0F;
            return 1.0F - (float) age / PRINT_TICKS;
        }
    }

    // ---- reach and facing ------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Blocks between its hitbox and {@code target}'s, across or up and down, whichever is more. */
    public double gapTo(Entity target) {
        return MobCore.gap(this, target);
    }

    /** True when {@code target} stands inside the arc its claws rake. */
    public boolean inFront(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return true;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.degreesDifferenceAbs(toward, getYRot()) <= STRIKE_ARC / 2.0F;
    }

    private void face(Vec3 at, float maxTurn) {
        double dx = at.x - getX();
        double dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** While it shimmers or rakes its body stays square to the way it faces. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && (core().in(Step.TELL) || core().in(Step.STRIKE))) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    // ---- death, save and load ----------------------------------------------------------------------------------------

    /** It dies in plain sight. */
    @Override
    public void die(DamageSource source) {
        setHidden(false);
        super.die(source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("RevealLeft", revealLeft());
        output.putInt("StrikeCooldown", cooldown);
    }

    /** The reveal and the strike cooldown survive a reload; a tell or a flinch under way and its footprints do not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int left = Math.clamp(input.getIntOr("RevealLeft", 0), 0, REVEAL_TICKS);
        revealedUntil = left > 0 ? level().getGameTime() + left : NEVER;
        cooldown = Math.clamp(input.getIntOr("StrikeCooldown", 0), 0, COOLDOWN);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Rakes that landed so far. Public as a test hook. */
    public int strikes() {
        return strikes;
    }

    /** Projectile hits that revealed it so far. Public as a test hook. */
    public int reveals() {
        return reveals;
    }

    /** Ticks until it may start a tell again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.GLIMMERLESS_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GLIMMERLESS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GLIMMERLESS_DEATH.get();
    }

    /** A stalker's breath is quiet. */
    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }
}
