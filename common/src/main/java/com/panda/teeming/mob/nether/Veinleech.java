package com.panda.teeming.mob.nether;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Veinleech: a fat crimson leech of the crimson forests with a ring of hooked teeth round its sucker. With a target
 * within 10 blocks in its sight it rears for 10 ticks (tell): its front half lifts, the sucker flares and the teeth
 * splay open, with a wet suck and crimson spores. Then it shoots a vein from its mouth into the target and holds on:
 * every second the target loses 1 health and the leech heals 1, and a swelling runs down the vein from the victim to
 * the leech each time it drinks. Counter: put a block, a tree or a corner between you, or get past 10 blocks: the vein
 * snaps (it also snaps if the target is already out of sight or reach when the tell ends) and the leech lies stunned
 * for 40 ticks, unable to move, bite or latch. It waits 2 s after the stun before it rears again; meanwhile it bites.
 * On a Tether Charm holder the vein snaps by itself once it has held for 40 ticks, as if the counter had broken it.
 *
 * <p>The vein is not an entity: the leech faces its victim while it holds, and tells clients how long the vein is and
 * how steeply it runs through its synced flags, so the model can lay the vein from its mouth to the victim. Nothing is
 * kept on the victim, so every way the hold ends (the leech dying or unloading, the victim dying or going creative)
 * simply stops it.
 */
public class Veinleech extends TeemingMonster {
    /** IDLE, rearing (tell), the vein latched on and drinking, lying stunned after the vein snapped. */
    public enum Step implements Phase { IDLE, TELL, LATCH, STUNNED }

    public static final int TELL_TICKS = 10;
    /** It rears at targets this close, in blocks from it to the target; while latched, past this the vein snaps. */
    public static final double RANGE = 10.0;
    /** Health the target loses, and the leech gains, each time it drinks. */
    public static final float DRAIN = 1.0F;
    public static final float HEAL = 1.0F;
    /** Ticks between drinks: once a second, the first a second after the vein latches. */
    public static final int DRAIN_INTERVAL = 20;
    /** How long it lies stunned after the vein snaps. */
    public static final int STUN_TICKS = 40;
    /** Ticks after a stun (or a hold that ended some other way) before it may rear again. */
    public static final int COOLDOWN = 40;
    /** Blocks a tick the vein grows as it shoots out of the mouth: 10 blocks in 3 ticks. */
    public static final float SHOOT_SPEED = 3.5F;
    /** Where the vein leaves its mouth, reared: this far ahead of its middle and this high, in blocks at scale 1. */
    public static final double MOUTH_FORWARD = 0.6, MOUTH_HEIGHT = 0.38;

    // flag bits: the vein's length in eighths of a block at scale 1 (7 bits), its pitch in degrees + 128 (8 bits), and
    // whether the two are live (unset in a pose with no hold behind it)
    private static final int LENGTH_BIT = 0, PITCH_BIT = 7, LIVE_BIT = 15;
    private static final int LENGTH_MASK = 0x7F, PITCH_MASK = 0xFF;

    private static final DustParticleOptions BLOOD = new DustParticleOptions(0x9A1022, 0.9F);
    private static final DustParticleOptions CLOT = new DustParticleOptions(0x5A0814, 1.2F);
    /** Gold stars circling over it while it lies stunned: the moment to hit it. */
    private static final DustParticleOptions STAR = new DustParticleOptions(0xFFD36B, 1.0F);
    private static final int STARS = 3;
    private static final Telegraph REAR = Telegraph.particles(ParticleTypes.CRIMSON_SPORE, 14, 0.35, 0.9).withSpeed(0.02)
            .withSound(ModSounds.VEINLEECH_TELL.get(), 1.0F, 1.0F);

    private int cooldown;

    public Veinleech(EntityType<? extends Veinleech> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("veinleech", 0.22);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // its bite, and the crawl into range and sight
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
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

    // ---- the cycle -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (core().in(Step.TELL)) {
            rearTick(level, core().target());
        } else if (core().in(Step.LATCH)) {
            latchTick(level, core().target());
        } else if (core().in(Step.STUNNED)) {
            holdStill();
            circleStars(level);
        } else if (cooldown == 0 && inReach(getTarget())) {
            rear(getTarget());
        }
    }

    /** Starts the tell at {@code target}: it rears and gapes. True if it started. Public as a test hook. */
    public boolean rear(@Nullable LivingEntity target) {
        if (target == null || !core().idle() || cooldown > 0 || level().isClientSide()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        hideVein();
        holdStill();
        face(target);
        REAR.play(this);
        return true;
    }

    /** The tell: it holds still, faces the target and drips from its open mouth. Nothing happens to the target yet. */
    private void rearTick(ServerLevel level, @Nullable LivingEntity target) {
        holdStill();
        if (valid(target)) face(target);
        if (tickCount % 3 == 0) {
            Vec3 m = mouth();
            level.sendParticles(BLOOD, m.x, m.y, m.z, 2, 0.08, 0.05, 0.08, 0.0);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, m.x, m.y + 0.1, m.z, 2, 0.15, 0.1, 0.15, 0.01);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
            return;
        }
        if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (!valid(target)) rest();
            else if (!inReach(target)) snap(level, target);                     // it got behind cover or away in time
            else if (startPhase(Step.LATCH, 0, target)) latch(level, target);
            else rest();
        } else {
            rest();
        }
    }

    // ---- Tether: a vein from this mob to a target that holds while range and sight allow ---------------------------

    /** True when a target is alive, within 10 blocks and in its sight: it can latch on, and a latched vein holds. */
    public boolean inReach(@Nullable LivingEntity target) {
        return valid(target) && distanceTo(target) <= RANGE && hasLineOfSight(target);
    }

    /** The vein shoots out and latches on: a wet slap at the target. */
    private void latch(ServerLevel level, LivingEntity target) {
        face(target);
        showVein(target);
        Vec3 at = aimPoint(target);
        level.playSound(null, at.x, at.y, at.z, ModSounds.VEINLEECH_LATCH.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(BLOOD, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
    }

    /**
     * One tick of the hold: it checks range and sight, drinks once a second, and keeps the vein on its victim. A hold
     * on a Tether Charm holder snaps at {@link Charms#TETHER_SNAP_TICKS}, before that tick's drink.
     */
    private void latchTick(ServerLevel level, @Nullable LivingEntity target) {
        holdStill();
        if (!valid(target)) {
            rest();                                                              // the victim died or went creative
            return;
        }
        int t = core().elapsed();
        if (!inReach(target) || Charms.tetherSnaps(target, t)) {
            snap(level, target);
            return;
        }
        face(target);
        showVein(target);
        if (t > 0 && t % DRAIN_INTERVAL == 0) drink(level, target);
        if (tickCount % 2 == 0) {
            // blood drifts along the vein where the swelling is, from the victim toward the leech
            Vec3 from = mouth(), to = aimPoint(target);
            double u = 1.0 - (t % DRAIN_INTERVAL) / (double) DRAIN_INTERVAL;
            Vec3 p = from.add(to.subtract(from).scale(u));
            level.sendParticles(BLOOD, false, true, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.0);
        }
    }

    /** It drinks: 1 health from the target (magic: armour points and difficulty don't change it) and 1 to itself. */
    private void drink(ServerLevel level, LivingEntity target) {
        boolean drank = false;
        if (MechanicEvent.post(this, "drain", target)) drank = target.hurtServer(level, damageSources().magic(), DRAIN);
        if (drank && MechanicEvent.post(this, "heal", target)) heal(HEAL);
        Vec3 at = aimPoint(target);
        level.sendParticles(BLOOD, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.0);
        level.sendParticles(BLOOD, getX(), getY() + getBbHeight(), getZ(), 4, 0.2, 0.1, 0.2, 0.0);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.VEINLEECH_DRAIN.get(), getSoundSource(), 0.8F, 1.0F);
    }

    /**
     * The vein snaps (the counter): a spray of blood and clots where it broke (at the wall in the way, or at the
     * target out of reach), a tearing sound, and the leech lies stunned.
     */
    private void snap(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.STUNNED, STUN_TICKS, target)) {
            rest();
            return;
        }
        Vec3 from = mouth();
        Vec3 at = from.add(Vec3.directionFromRotation(0.0F, getYRot()).scale(1.5));
        if (target != null) {
            Vec3 to = aimPoint(target);
            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            at = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
        }
        level.sendParticles(BLOOD, at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.0);
        level.sendParticles(CLOT, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.0);
        level.sendParticles(BLOOD, from.x, from.y, from.z, 8, 0.15, 0.1, 0.15, 0.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.VEINLEECH_SNAP.get(), getSoundSource(), 1.0F, 1.0F);
        playSound(ModSounds.VEINLEECH_SNAP.get(), 0.8F, 0.7F);
    }

    /** Back to idle, the vein gone, and a wait before it may rear again. */
    private void rest() {
        cooldown = COOLDOWN;
        hideVein();
        core().end();
    }

    /** Gold stars circling over it while it is stunned, one lap a second. */
    private void circleStars(ServerLevel level) {
        if (tickCount % 2 != 0) return;
        Vec3 top = position().add(0, getBbHeight() + 0.3, 0);
        double turn = core().elapsed() * (Math.PI * 2 / 20);
        for (int i = 0; i < STARS; i++) {
            double a = turn + i * (Math.PI * 2 / STARS);
            level.sendParticles(STAR, top.x + Math.cos(a) * 0.4, top.y, top.z + Math.sin(a) * 0.4, 1, 0, 0, 0, 0);
        }
    }

    // ---- the vein as clients see it --------------------------------------------------------------------------------

    /** Where the vein leaves it: its mouth, reared, ahead of its middle along the way it faces. */
    public Vec3 mouth() {
        double s = getScale();
        return position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(MOUTH_FORWARD * s)).add(0, MOUTH_HEIGHT * s, 0);
    }

    /** Where the vein ends on a target: the middle of its hitbox. */
    private static Vec3 aimPoint(LivingEntity target) {
        return target.getBoundingBox().getCenter();
    }

    /** Tells clients the vein runs from its mouth to {@code target}: how long and how steeply. */
    private void showVein(LivingEntity target) {
        Vec3 from = mouth(), to = aimPoint(target);
        Vec3 d = to.subtract(from);
        double across = Math.max(1.0E-3, d.horizontalDistance());
        int len = Mth.clamp((int) Math.round(d.length() * 8.0 / getScale()), 0, LENGTH_MASK);
        int pitch = Mth.clamp((int) Math.round(Mth.atan2(d.y, across) * Mth.RAD_TO_DEG) + 128, 0, PITCH_MASK);
        for (int i = 0; i < 8; i++) {
            if (i < 7) setBit(LENGTH_BIT + i, (len >> i & 1) != 0);
            setBit(PITCH_BIT + i, (pitch >> i & 1) != 0);
        }
        setBit(LIVE_BIT, true);
    }

    private void hideVein() {
        setBit(LIVE_BIT, false);
    }

    private void setBit(int bit, boolean on) {
        if (core().flag(bit) != on) core().setFlag(bit, on);
    }

    /** From synced flags: the vein's length in blocks at scale 1, mouth to victim. */
    public static float veinLength(int flags) {
        return (flags >> LENGTH_BIT & LENGTH_MASK) / 8.0F;
    }

    /** From synced flags: the vein's pitch in degrees, up positive. */
    public static float veinPitch(int flags) {
        return (flags >> PITCH_BIT & PITCH_MASK) - 128;
    }

    /** From synced flags: whether a real hold set the length and pitch (a posed phase has none). */
    public static boolean veinLive(int flags) {
        return (flags >> LIVE_BIT & 1) != 0;
    }

    /**
     * The box the renderer culls it by: while the vein is out it reaches the victim, so the leech (and with it the
     * vein) stays drawn while only the victim's end is on screen.
     */
    @Override
    public AABB getInterpolatedBoundingBox(float partialTick) {
        AABB box = super.getInterpolatedBoundingBox(partialTick);
        if (core() == null || core().idle() || !veinLive(core().flags())) return box;
        int flags = core().flags();
        double s = getScale();
        double pitch = veinPitch(flags) * Mth.DEG_TO_RAD;
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
        Vec3 dir = new Vec3(forward.x * Math.cos(pitch), Math.sin(pitch), forward.z * Math.cos(pitch));
        Vec3 from = box.getBottomCenter().add(forward.scale(MOUTH_FORWARD * s)).add(0, MOUTH_HEIGHT * s, 0);
        Vec3 end = from.add(dir.scale(veinLength(flags) * s));
        return box.minmax(new AABB(end, end).inflate(0.5));
    }

    // ---- facing ----------------------------------------------------------------------------------------------------

    /** While it rears, holds or lies stunned its body faces exactly the way it looks, so the vein lies on the line. */
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
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Ticks until it may rear again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it rear again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.VEINLEECH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.VEINLEECH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.VEINLEECH_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.2F;
    }
}
