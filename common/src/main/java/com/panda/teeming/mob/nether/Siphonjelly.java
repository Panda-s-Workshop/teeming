package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Siphonjelly: a translucent, bell-shaped jelly that drifts through the warped air trailing long tendrils. It drifts
 * up to its target, and with it close its bell flares open and its tendrils light for 10 ticks (tell, with a wet
 * chime); then it stings (its attack damage, 2). A sting that lands takes the target's longest-lasting beneficial
 * effect, at most 600 ticks of it (the target keeps whatever was left above 600, and keeps an endless effect), and
 * puts it on the jelly: it glows in that effect's colour and has its use while it holds it (Speed makes it drift
 * faster, Regeneration heals it, Resistance hardens it, as on anyone). It holds one effect at a time: while it holds
 * one, its stings only hurt, and it can take another once the first runs out.
 *
 * <p>Counter: kill it while it holds your effect and you get it back with the time it had left, added to whatever of
 * that effect you still have, as long as you are alive and within 32 blocks; anyone else, or farther, and the effect
 * is lost with it. Or fight it without buffs: a sting that finds nothing to take starves it: for 100 ticks it sinks
 * low, dim and limp, and can't sting at all.
 *
 * <p>Its glow is the stolen effect's own colour, picked from twelve hues by the nearest; effects whose colour lies
 * in the band of weak-point gold are shown orange or lime instead, so it never glows gold. The sting places and
 * breaks nothing; the effect moves from one living thing to another.
 */
public class Siphonjelly extends TeemingMonster {
    /** IDLE (drifting, hunting), the flare (tell), the sting, starved after a sting that found nothing. */
    public enum Step implements Phase { IDLE, TELL, STING, STARVED }

    /** 10 ticks of flare before the sting. */
    public static final int TELL_TICKS = 10;
    /** The sting itself: the bell snaps shut and it bobs back. */
    public static final int STING_TICKS = 8;
    /** It flares when its body comes this close to its target's, in blocks. */
    public static final double FLARE_REACH = 0.5;
    /** At the end of the flare the sting lands on a target whose body is still this close; one that stepped back is missed. */
    public static final double STING_REACH = 1.0;
    /** Ticks from one sting to the next flare at the soonest. */
    public static final int STING_COOLDOWN = 30;
    /** A fresh jelly gathers itself this long before its first flare. */
    public static final int FIRST_STING = 10;
    /** A sting takes at most 600 ticks (30 s) of the effect. */
    public static final int STEAL_CAP = 600;
    /** On its death the effect goes back to the one it was taken from, if alive and this close. */
    public static final double RETURN_RANGE = 32.0;
    /** A sting that finds nothing to take leaves it starved this long: no flare and no sting. */
    public static final int STARVED_TICKS = 100;

    /** Its drift: blocks a tick hunting, wandering, and starved; Speed and Slowness scale all three. */
    public static final double HUNT_SPEED = 0.12;
    public static final double WANDER_SPEED = 0.05;
    public static final double STARVED_SPEED = 0.03;
    /** It wanders this high over whatever is below; starved, it sinks to this. */
    public static final double HOVER_HEIGHT = 2.0;
    public static final double STARVED_HEIGHT = 0.3;
    /** After a sting it bobs back to a spot this far out from its target and this far above its eyes. */
    public static final double BACK_OFF = 2.0;
    public static final double BACK_UP = 0.6;
    private static final int WANDER_SPREAD = 6;
    private static final int GROUND_SEARCH = 16;

    /**
     * The colours it glows in while it holds an effect, one per glow stage from 1 (stage 0: it holds nothing). None
     * lies in {@link #GOLD_FROM}..{@link #GOLD_TO} degrees of hue: that band is weak-point gold's.
     */
    public static final int[] HELD_COLORS = {0xFF5A4E, 0xFF8F3A, 0xD6FF4A, 0x5CFF6A, 0x4AFFC0, 0x46ECFF,
            0x4AA6FF, 0x6670FF, 0xA45CFF, 0xEC5CFF, 0xFF5CB4, 0xEEF6FF};
    /** The band of hue (degrees) that reads as weak-point gold; effect colours inside it are moved out of it. */
    public static final float GOLD_FROM = 30.0F;
    public static final float GOLD_TO = 58.0F;
    /** Effect colours below this saturation glow white (Invisibility, Slow Falling). */
    public static final float WHITE_BELOW = 0.25F;
    /** Where a gold-band colour goes: orange below the middle of the band, lime above it. */
    private static final float GOLD_MIDDLE = 42.0F;
    private static final float GOLD_TO_ORANGE = 26.0F;
    private static final float GOLD_TO_LIME = 74.0F;
    private static final int STAGE_ORANGE = 2;
    private static final int STAGE_LIME = 3;
    private static final int STAGE_WHITE = 12;

    /** Warped-teal flare light on its tendrils; the sting's spray; the smoke of a lost effect. */
    private static final DustParticleOptions FLARE_MOTE = new DustParticleOptions(0xC8FFF2, 0.7F);
    private static final Telegraph FLARE = Telegraph.particles(FLARE_MOTE, 10, 0.45, 0.2).withSpeed(0.01);
    private static final Telegraph STARVE = Telegraph.particles(ParticleTypes.SMOKE, 12, 0.35, 0.4).withSpeed(0.01);

    private int cooldown = FIRST_STING;
    private boolean starveNext;
    private int stings;
    /** The effect it holds as it was put on it, who it came from, and the ticks of it left to give back. */
    private @Nullable MobEffectInstance held;
    private @Nullable UUID heldFrom;
    private int heldLeft;
    /** What its death did with the effect: the ticks given back (0 for none), and to whom. */
    private int lastReturned = -1;
    private @Nullable UUID lastReturnedTo;
    private @Nullable Vec3 wanderSpot;
    private int wanderRepath;

    public Siphonjelly(EntityType<? extends Siphonjelly> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 10, true);
        setNoGravity(true);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("siphonjelly", 0.25).add(Attributes.FLYING_SPEED, 0.3);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    /** It steers itself (see {@link #customServerAiStep}); the goals only look about and pick targets. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    /** One glow stage per held colour, and stage 0 for holding nothing. */
    @Override
    public int visualStages() {
        return 1 + HELD_COLORS.length;
    }

    // ---- the hunt --------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        getNavigation().stop();
        // its glow shows what it holds: holding nothing, its own (a still pose may show any stage; a live one never lies)
        if (held == null && core().stage() != 0) core().setStage(0);
        if (cooldown > 0) cooldown--;
        if (core().in(Step.TELL) || core().in(Step.STING)) {
            setDeltaMovement(getDeltaMovement().scale(0.5));
            LivingEntity target = core().target();
            if (target != null && target.isAlive()) face(target.position().subtract(position()));
            return;
        }
        if (core().in(Step.STARVED)) {
            sink(level);
            return;
        }
        LivingEntity target = getTarget();
        if (!valid(target)) {
            wander(level);
            return;
        }
        wanderSpot = null;
        if (canFlare(target)) {
            flare(target);
            return;
        }
        hunt(target);
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
    }

    /** It may flare: no sting for {@link #STING_COOLDOWN} ticks, its target within {@link #FLARE_REACH} and in sight. */
    private boolean canFlare(LivingEntity target) {
        return cooldown == 0 && core().idle() && gap(target) <= FLARE_REACH && getSensing().hasLineOfSight(target);
    }

    /** How far apart its body and the target's are, in blocks (0 when they touch). */
    public double gap(LivingEntity target) {
        AABB a = getBoundingBox(), b = target.getBoundingBox();
        double dx = Math.max(0.0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
        double dy = Math.max(0.0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
        double dz = Math.max(0.0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Starts the flare (the tell) at {@code target}; true if it started. Public as a test hook. */
    public boolean flare(LivingEntity target) {
        if (level().isClientSide() || !core().idle()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        cooldown = STING_COOLDOWN + TELL_TICKS;
        FLARE.withSound(ModSounds.SIPHONJELLY_FLARE.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    /** The drift after its target: its feet a little under the target's eyes, just beside it; after a sting, back and up. */
    private void hunt(LivingEntity target) {
        Vec3 side = position().subtract(target.position()).horizontal();
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        double apart = target.getBbWidth() * 0.5 + getBbWidth() * 0.5;
        Vec3 want;
        if (cooldown > TELL_TICKS) {
            want = target.position().add(side.scale(apart + BACK_OFF)).add(0, target.getEyeHeight() + BACK_UP, 0);
        } else {
            want = target.position().add(side.scale(apart + 0.1)).add(0, Math.max(0.2, target.getEyeHeight() - 0.4), 0);
        }
        steer(want, HUNT_SPEED * speedFactor(), 0.2);
        face(target.position().subtract(position()));
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
            return;
        }
        if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (startPhase(Step.STING, STING_TICKS, target)) sting(level, target);
            else core().end();
        } else if (ended == Step.STING) {
            boolean starve = starveNext;
            starveNext = false;
            if (!starve || !starve(level)) core().end();
        } else {
            core().end();
        }
    }

    /**
     * The sting at {@code target}: if its body is within {@link #STING_REACH}, its attack damage, and if that lands,
     * the siphon. A sting that lands on a target with nothing to take, while it holds nothing, starves it once the
     * sting ends. Returns true if the sting landed. Public as a test hook.
     */
    public boolean sting(ServerLevel level, @Nullable LivingEntity target) {
        cooldown = Math.max(cooldown, STING_COOLDOWN);
        setDeltaMovement(getDeltaMovement().add(0, 0.08, 0));
        // entering the sting phase already posted the "sting" step; "siphon" and "give_back" are posted on their own
        if (target == null || !target.isAlive() || gap(target) > STING_REACH) return false;
        swingForAttack(InteractionHand.MAIN_HAND);
        playSound(ModSounds.SIPHONJELLY_STING.get(), 1.0F, 0.95F + random.nextFloat() * 0.1F);
        if (!doHurtTarget(level, target)) return false;
        stings++;
        if (held == null) {
            if (longestBeneficial(target) == null) starveNext = true;
            else siphon(level, target);
        }
        return true;
    }

    // ---- the siphon: one effect at a time ---------------------------------------------------------------------------

    /** The target's beneficial effect with the most time left (endless counts as the most), or null if it has none. */
    public static @Nullable MobEffectInstance longestBeneficial(LivingEntity target) {
        MobEffectInstance best = null;
        for (MobEffectInstance e : target.getActiveEffects()) {
            if (!e.getEffect().value().isBeneficial()) continue;
            if (best == null || longer(e, best)) best = e;
        }
        return best;
    }

    private static boolean longer(MobEffectInstance a, MobEffectInstance b) {
        if (a.isInfiniteDuration() != b.isInfiniteDuration()) return a.isInfiniteDuration();
        if (a.getDuration() != b.getDuration()) return a.getDuration() > b.getDuration();
        return a.getAmplifier() > b.getAmplifier();
    }

    /** The same effect, level and display as {@code e}, lasting {@code ticks}. */
    private static MobEffectInstance copy(MobEffectInstance e, int ticks) {
        return new MobEffectInstance(e.getEffect(), ticks, e.getAmplifier(), e.isAmbient(), e.isVisible(), e.showIcon());
    }

    /**
     * Takes up to {@link #STEAL_CAP} ticks of the target's longest beneficial effect and puts them on itself; the target
     * keeps the rest. Returns what it took, or null if it holds one already, there was nothing, or a listener stopped
     * it. Public as a test hook.
     */
    public @Nullable MobEffectInstance siphon(ServerLevel level, LivingEntity target) {
        if (held != null) return null;
        MobEffectInstance best = longestBeneficial(target);
        if (best == null) return null;
        int take = best.isInfiniteDuration() ? STEAL_CAP : Math.min(STEAL_CAP, best.getDuration());
        MobEffectInstance mine = copy(best, take);
        if (take <= 0 || !canBeAffected(mine)) return null;
        if (!MechanicEvent.post(this, "siphon", target)) return null;
        if (!best.isInfiniteDuration()) {
            int keep = best.getDuration() - take;
            target.removeEffect(best.getEffect());
            if (keep > 0) target.addEffect(copy(best, keep), this);
        }
        addEffect(copy(mine, take), target);
        held = mine;
        heldFrom = target.getUUID();
        heldLeft = take;
        core().setStage(stageFor(mine.getEffect().value().getColor()));
        stream(level, target.position().add(0, target.getBbHeight() * 0.6, 0), centre(), shownColor(mine.getEffect().value().getColor()));
        playSound(ModSounds.SIPHONJELLY_SIPHON.get(), 1.0F, 1.0F);
        return copy(mine, take);
    }

    /** The held effect has run out (or was taken off it): it holds nothing and its glow goes back to its own. */
    private void lapse(ServerLevel level) {
        if (held != null) {
            Vec3 c = centre();
            level.sendParticles(new DustParticleOptions(shownColor(held.getEffect().value().getColor()), 0.6F), c.x, c.y, c.z, 6, 0.3, 0.25, 0.3, 0.0);
        }
        held = null;
        heldFrom = null;
        heldLeft = 0;
        core().setStage(0);
    }

    /** Ticks of the held effect it would give back now: its countdown, or less if its own copy has less left. */
    public int heldLeft() {
        if (held == null) return 0;
        MobEffectInstance mine = getEffect(held.getEffect());
        if (mine == null) return 0;
        return mine.isInfiniteDuration() ? heldLeft : Math.min(heldLeft, mine.getDuration());
    }

    /**
     * On its death the effect goes back to the one it was taken from, if that one is alive, in this world and within
     * {@link #RETURN_RANGE}: the ticks left are added to whatever of the same effect and level they still have.
     * Otherwise it is lost with the jelly.
     */
    private void giveBack(ServerLevel level) {
        lastReturned = 0;
        lastReturnedTo = null;
        if (held == null || heldFrom == null) return;
        int left = heldLeft();
        Entity from = level.getEntity(heldFrom);
        Vec3 c = centre();
        if (left > 0 && from instanceof LivingEntity victim && victim.isAlive() && victim.level() == level
                && distanceTo(victim) <= RETURN_RANGE && MechanicEvent.post(this, "give_back", victim)) {
            MobEffectInstance has = victim.getEffect(held.getEffect());
            int ticks = left;
            if (has != null && !has.isInfiniteDuration() && has.getAmplifier() == held.getAmplifier()) ticks = has.getDuration() + left;
            victim.addEffect(copy(held, ticks), this);
            lastReturned = left;
            lastReturnedTo = victim.getUUID();
            int color = shownColor(held.getEffect().value().getColor());
            stream(level, c, victim.position().add(0, victim.getBbHeight() * 0.6, 0), color);
            level.sendParticles(new DustParticleOptions(color, 1.0F), true, true, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6,
                    victim.getZ(), 14, 0.35, 0.45, 0.35, 0.0);
            level.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), ModSounds.SIPHONJELLY_RETURN.get(), getSoundSource(), 1.0F, 1.0F);
        } else {
            level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.02);
        }
        held = null;
        heldFrom = null;
        heldLeft = 0;
    }

    @Override
    public void die(DamageSource source) {
        if (!isRemoved() && !dead && level() instanceof ServerLevel level) giveBack(level);
        super.die(source);
    }

    /** A line of motes in the effect's colour from {@code a} to {@code b}: the effect moving. */
    private void stream(ServerLevel level, Vec3 a, Vec3 b, int color) {
        DustParticleOptions mote = new DustParticleOptions(color, 0.9F);
        int n = Math.max(4, (int) Math.ceil(a.distanceTo(b) * 4));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n);
            level.sendParticles(mote, true, true, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // ---- starved -----------------------------------------------------------------------------------------------------

    /** A sting that found nothing (the counter): smoke, a fizzle, and {@link #STARVED_TICKS} with no flare. */
    private boolean starve(ServerLevel level) {
        if (!startPhase(Step.STARVED, STARVED_TICKS, null)) return false;
        STARVE.withSound(ModSounds.SIPHONJELLY_STARVE.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    /** Starved, it sinks to just over the ground and drifts there. */
    private void sink(ServerLevel level) {
        Vec3 want = new Vec3(getX(), surfaceBelow(level, getX(), getY() + 0.5, getZ()) + STARVED_HEIGHT, getZ());
        steer(want, STARVED_SPEED * speedFactor(), 0.1);
    }

    /** True while it is starved: it can't flare or sting. */
    public boolean starved() {
        return core() != null && core().in(Step.STARVED);
    }

    // ---- wandering and steering ---------------------------------------------------------------------------------------

    /** With nothing to hunt it drifts from spot to spot, {@link #HOVER_HEIGHT} over whatever lies below. */
    private void wander(ServerLevel level) {
        if (--wanderRepath <= 0 || wanderSpot == null || wanderSpot.distanceToSqr(position()) < 0.5) {
            wanderSpot = wanderSpot(level);
            wanderRepath = 100;
        }
        if (wanderSpot != null) steer(wanderSpot, WANDER_SPEED * speedFactor(), 0.08);
        else setDeltaMovement(getDeltaMovement().scale(0.8));
    }

    private @Nullable Vec3 wanderSpot(ServerLevel level) {
        double x = getX() + random.nextInt(WANDER_SPREAD * 2 + 1) - WANDER_SPREAD;
        double z = getZ() + random.nextInt(WANDER_SPREAD * 2 + 1) - WANDER_SPREAD;
        double y = surfaceBelow(level, x, getY() + 2.0, z) + HOVER_HEIGHT;
        Vec3 spot = new Vec3(x, y, z);
        return open(spot) ? spot : null;
    }

    /** Whether it fits with its feet at {@code spot} and has a clear line to it. */
    private boolean open(Vec3 spot) {
        AABB there = getBoundingBox().move(spot.subtract(position()));
        if (!level().noCollision(this, there)) return false;
        Vec3 mid = spot.add(0, getBbHeight() * 0.5, 0);
        return level().clip(new ClipContext(centre(), mid, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this)).getType() == HitResult.Type.MISS;
    }

    /** The top of the first solid block or fluid below {@code y}, within 16 blocks; its own height less the hover if none. */
    private double surfaceBelow(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < GROUND_SEARCH; i++, pos.move(0, -1, 0)) {
            if (!level.getFluidState(pos).isEmpty()) return pos.getY() + level.getFluidState(pos).getHeight(level, pos);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return pos.getY() + 1.0;
        }
        return getY() - HOVER_HEIGHT;
    }

    /**
     * How much faster than its own drift it moves now: its movement speed over its base, so Speed (+20 % a level)
     * and Slowness change it as they change anyone's. Public as a test hook.
     */
    public double speedFactor() {
        double base = getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        return base > 0 ? getAttributeValue(Attributes.MOVEMENT_SPEED) / base : 1.0;
    }

    /** Eases its drift toward {@code want} (its feet there) at up to {@code speed} blocks a tick. */
    private void steer(Vec3 want, double speed, double ease) {
        Vec3 to = want.subtract(position());
        if (to.length() > speed) to = to.normalize().scale(speed);
        setDeltaMovement(getDeltaMovement().lerp(to, ease));
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private Vec3 centre() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- what players see ------------------------------------------------------------------------------------------

    /** Runs whether or not its AI does, so a held pose shows the same as the real thing; also counts the held effect down. */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (held != null) {
            heldLeft--;
            if (heldLeft <= 0 || !hasEffect(held.getEffect())) lapse(level);
            else if (tickCount % 5 == 0) {
                // motes of the effect's colour drift off its bell while it holds it
                Vec3 c = centre();
                level.sendParticles(new DustParticleOptions(shownColor(held.getEffect().value().getColor()), 0.7F),
                        c.x, c.y + 0.15, c.z, 1, 0.3, 0.15, 0.3, 0.0);
            }
        }
        if (core().in(Step.TELL)) {
            FLARE.trail(this, 2);
            if (core().elapsed() == TELL_TICKS / 2) playSound(ModSounds.SIPHONJELLY_FLARE.get(), 0.7F, 1.25F);
        } else if (core().in(Step.STARVED) && core().elapsed() % 10 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0.2, 0.1, 0.2, 0.005);
        }
    }

    // ---- the glow colour ---------------------------------------------------------------------------------------------

    /** Hue (degrees 0..360), saturation and value (0..1) of an RGB colour. */
    private static float[] hsv(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255.0F, g = (rgb >> 8 & 0xFF) / 255.0F, b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d <= 0.0F) h = 0.0F;
        else if (max == r) h = 60.0F * (((g - b) / d) % 6.0F);
        else if (max == g) h = 60.0F * ((b - r) / d + 2.0F);
        else h = 60.0F * ((r - g) / d + 4.0F);
        if (h < 0.0F) h += 360.0F;
        return new float[] {h, max <= 0.0F ? 0.0F : d / max, max};
    }

    private static int rgb(float h, float s, float v) {
        float c = v * s, x = c * (1.0F - Math.abs((h / 60.0F) % 2.0F - 1.0F)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    /** True when a hue lies in the band of weak-point gold. */
    public static boolean goldHue(float hue) {
        return hue >= GOLD_FROM && hue < GOLD_TO;
    }

    /** The hue of an RGB colour in degrees. Public for tests. */
    public static float hue(int rgb) {
        return hsv(rgb)[0];
    }

    /**
     * The glow stage (1..12) for an effect colour: white for a pale colour, orange or lime for one in the gold band,
     * else the held colour of the nearest hue.
     */
    public static int stageFor(int color) {
        float[] c = hsv(color);
        if (c[1] < WHITE_BELOW) return STAGE_WHITE;
        if (goldHue(c[0])) return c[0] < GOLD_MIDDLE ? STAGE_ORANGE : STAGE_LIME;
        int best = 1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < HELD_COLORS.length; i++) {
            if (i + 1 == STAGE_WHITE) continue;
            float d = Math.abs(hue(HELD_COLORS[i]) - c[0]);
            d = Math.min(d, 360.0F - d);
            if (d < bestD) {
                bestD = d;
                best = i + 1;
            }
        }
        return best;
    }

    /** The colour its motes show for an effect colour: the colour itself, turned out of the gold band if it lies in it. */
    public static int shownColor(int color) {
        float[] c = hsv(color);
        if (!goldHue(c[0]) || c[1] < WHITE_BELOW) return color & 0xFFFFFF;
        return rgb(c[0] < GOLD_MIDDLE ? GOLD_TO_ORANGE : GOLD_TO_LIME, c[1], c[2]);
    }

    // ---- saving ------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (held != null) {
            output.store("Siphoned", MobEffectInstance.CODEC, held);
            output.putInt("SiphonedLeft", heldLeft);
            if (heldFrom != null) output.putString("SiphonedFrom", heldFrom.toString());
        }
    }

    /** The held effect, its countdown and who it came from survive a reload, and so does its glow. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        held = input.read("Siphoned", MobEffectInstance.CODEC).orElse(null);
        heldLeft = input.getIntOr("SiphonedLeft", 0);
        try {
            String id = input.getStringOr("SiphonedFrom", "");
            heldFrom = id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException badId) {
            heldFrom = null;
        }
        core().setStage(held == null ? 0 : stageFor(held.getEffect().value().getColor()));
    }

    // ---- test hooks --------------------------------------------------------------------------------------------------

    /** The effect it holds, as it took it (its first length), or null. Public as a test hook. */
    public @Nullable MobEffectInstance held() {
        return held;
    }

    /** The effect it holds, or null. Public as a test hook. */
    public @Nullable Holder<MobEffect> heldEffect() {
        return held == null ? null : held.getEffect();
    }

    /** Who the held effect came from. Public as a test hook. */
    public @Nullable UUID heldFrom() {
        return heldFrom;
    }

    /** Ticks its death gave back (0: none, -1: it hasn't died), and to whom. Public as test hooks. */
    public int lastReturned() {
        return lastReturned;
    }

    public @Nullable UUID lastReturnedTo() {
        return lastReturnedTo;
    }

    /** Ticks until it may flare again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Sets the ticks until it may flare again. Public as a test hook. */
    public void setCooldown(int ticks) {
        cooldown = ticks;
    }

    /** Stings that landed so far. Public as a test hook. */
    public int stings() {
        return stings;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SIPHONJELLY_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SIPHONJELLY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SIPHONJELLY_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + random.nextFloat() * 0.15F;
    }
}
