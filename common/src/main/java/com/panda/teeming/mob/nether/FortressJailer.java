package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.Optional;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fortress Jailer: a tall, armoured warden of the fortress halls. Every 120 ticks, with a target 3 to 12 blocks away in
 * its sight, it plants its feet and whirls its chain over its head for 16 ticks (tell), turning to follow its target.
 * Then it throws the chain head straight at where its target stands: the head flies 1 block a tick along a fixed line
 * (it never homes) and stops at the first block or hitbox, out to 12 blocks. A target it hooks is dragged to it at
 * 0.8 blocks a tick until it stands next to it, then struck for its attack damage as the chained fist comes down
 * (6 ticks later). Its chain passes through other jailers. Counter: break its line of sight
 * during the wind (the chain stops at the wall) or raise a shield toward it: the chain clinks off and drops, and
 * nothing is pulled. Either way, and after a miss, the chain is reeled back in.
 *
 * <p>The chain is not an entity: the jailer moves its head each tick and tells clients how long the chain is and how
 * steeply it runs through its synced flags, so the model can lay real links from its fist to the head. Nothing is kept
 * on a hooked victim; the pull is the jailer setting its victim's motion each tick, so every way it ends (the jailer
 * dying, unloading or losing its AI, the victim dying, going creative or getting away) simply stops it.
 */
public class FortressJailer extends TeemingMonster {
    /** IDLE, whirling the chain (tell), the chain in flight, dragging a hooked victim, reeling a loose chain in, striking. */
    public enum Step implements Phase { IDLE, TELL, THROW, PULL, REEL, STRIKE }

    public static final int TELL_TICKS = 16;              // the chain whirls over its head for 0.8 s
    public static final double RANGE = 12.0;              // it throws at targets this close, and the chain reaches this far
    public static final double MIN_RANGE = 3.0;           // closer than this it fights with its fists instead
    public static final double CHAIN_SPEED = 1.0;         // blocks a tick: 12 blocks in 12 ticks, a step aside dodges it
    public static final double PULL_SPEED = 0.8;          // blocks a tick a hooked victim is dragged
    public static final int THROW_EVERY = 120;            // ticks from one tell to the next
    public static final int THROW_TICKS = 14;             // the longest a throw can take (12 blocks at 1 a tick, and a spare)
    public static final int PULL_TICKS = 20;              // the longest a drag can take (12 blocks at 0.8 a tick is 15)
    public static final double STRIKE_REACH = 1.6;        // centre to centre, across: the victim stands next to it
    public static final int STRIKE_TICKS = 8;
    public static final int STRIKE_HIT_TICK = 6;          // the chained fist goes up over its head, then lands on tick 6
    public static final double STRIKE_HIT_REACH = 2.6;    // centre to centre, across: a victim that stepped this far is missed
    public static final double REEL_SPEED = 1.5;          // blocks a tick a loose chain comes back
    public static final int MIN_REEL_TICKS = 4;
    public static final double CHAIN_RADIUS = 0.15;       // how far from a hitbox the chain head still catches it
    public static final float SHIELD_WEIGHT = 1.0F;       // what the chain head weighs on a raised shield
    /** Bigger than this the chain catches but can't drag it: the chain drops. */
    public static final float MAX_PREY_WIDTH = 1.5F, MAX_PREY_HEIGHT = 2.5F;
    /** A hooked victim found this far away (an ender pearl, a teleport) has got away. */
    public static final double ESCAPE_RANGE = RANGE + 2.0;
    public static final float MAX_PITCH = 60.0F;          // degrees up or down it can throw

    /** Where the chain leaves: its right shoulder, and the length of the arm to the fist, in blocks at scale 1. */
    public static final double SHOULDER_SIDE = 6.5 / 16.0, SHOULDER_HEIGHT = 25.5 / 16.0, ARM_LENGTH = 12.0 / 16.0;

    // flag bits: the chain's length in eighths of a block (7 bits), its pitch in degrees + 64 (7 bits), and whether
    // the two are live (unset in a pose with no throw behind it)
    private static final int LENGTH_BIT = 0, PITCH_BIT = 7, LIVE_BIT = 14;
    private static final int FIELD_MASK = 0x7F;

    private static final DustParticleOptions IRON = new DustParticleOptions(0x8A8D9C, 1.0F);
    private static final DustParticleOptions SPARK = new DustParticleOptions(0xFF8A2A, 0.8F);

    private final Grab grab = new Grab(MAX_PREY_WIDTH, MAX_PREY_HEIGHT, ESCAPE_RANGE);
    private int cooldown;
    // the chain head in flight: a fixed line from the fist, and how far along it the head is
    private boolean flying;
    private Vec3 chainFrom = Vec3.ZERO;
    private Vec3 chainDir = new Vec3(0, 0, 1);
    private double travelled;
    private float pitch;
    private boolean struck;

    public FortressJailer(EntityType<? extends FortressJailer> type, Level level) {
        super(type, level, Step.values());
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("fortress_jailer", 0.25).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
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
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
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

    // ---- facing and the chain's line ------------------------------------------------------------------------------

    /** While the chain is out its body faces exactly along the throw, so the links drawn from its fist lie on the line. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    private void setFacing(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private static float yawFromTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
    }

    /** Its right shoulder, where the chain arm turns, with its body facing {@code yaw}. */
    public Vec3 shoulder(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        double s = getScale();
        return position().add(-Mth.cos(r) * SHOULDER_SIDE * s, SHOULDER_HEIGHT * s, -Mth.sin(r) * SHOULDER_SIDE * s);
    }

    /**
     * Turns so that {@code point} lies straight ahead of its right shoulder (the chain arm is off to one side, so this
     * is not quite facing it from its middle), and returns the pitch from that shoulder to it in degrees, up positive.
     */
    private float aimAt(Vec3 point) {
        float yaw = yawFromTo(position(), point);
        for (int i = 0; i < 4; i++) yaw = yawFromTo(shoulder(yaw), point);
        setFacing(yaw);
        Vec3 s = shoulder(yaw);
        double across = Math.max(0.3, Math.hypot(point.x - s.x, point.z - s.z));
        return Mth.clamp((float) (Mth.atan2(point.y - s.y, across) * Mth.RAD_TO_DEG), -MAX_PITCH, MAX_PITCH);
    }

    private static Vec3 direction(float yaw, float pitchDeg) {
        Vec3 f = forward(yaw);
        double p = pitchDeg * Mth.DEG_TO_RAD;
        return new Vec3(f.x * Math.cos(p), Math.sin(p), f.z * Math.cos(p));
    }

    /** Its fist with the chain arm held out along {@code dir}: where the chain leaves. */
    private Vec3 fist(Vec3 dir) {
        return shoulder(getYRot()).add(dir.scale(ARM_LENGTH * getScale()));
    }

    // ---- the chain as clients see it -------------------------------------------------------------------------------

    /** Tells clients the chain is {@code length} blocks from fist to head and runs at {@code pitchDeg}. */
    private void showChain(double length, float pitchDeg) {
        int len = Mth.clamp((int) Math.round(length * 8.0 / getScale()), 0, FIELD_MASK);
        int p = Mth.clamp(Math.round(pitchDeg) + 64, 0, FIELD_MASK);
        for (int i = 0; i < 7; i++) {
            setBit(LENGTH_BIT + i, (len >> i & 1) != 0);
            setBit(PITCH_BIT + i, (p >> i & 1) != 0);
        }
        setBit(LIVE_BIT, true);
    }

    private void hideChain() {
        setBit(LIVE_BIT, false);
    }

    private void setBit(int bit, boolean on) {
        if (core().flag(bit) != on) core().setFlag(bit, on);
    }

    /** From synced flags: the chain's length in blocks at scale 1 (fist to head). */
    public static float chainLength(int flags) {
        return (flags >> LENGTH_BIT & FIELD_MASK) / 8.0F;
    }

    /** From synced flags: the chain's pitch in degrees, up positive. */
    public static float chainPitch(int flags) {
        return (flags >> PITCH_BIT & FIELD_MASK) - 64;
    }

    /** From synced flags: whether a real throw set the length and pitch (a posed phase has none). */
    public static boolean chainLive(int flags) {
        return (flags >> LIVE_BIT & 1) != 0;
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (!core().idle()) {
            holdStill();
            if (core().in(Step.TELL)) windTick(level);
            return;
        }
        LivingEntity target = getTarget();
        if (cooldown == 0 && valid(target)) {
            double d = distanceTo(target);
            if (d >= MIN_RANGE && d <= RANGE && getSensing().hasLineOfSight(target)) wind(level, target);
        }
    }

    /** Starts the tell at {@code target}: 16 ticks of whirling. True if it started. */
    private boolean wind(ServerLevel level, LivingEntity target) {
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        cooldown = THROW_EVERY;
        hideChain();
        holdStill();
        aimAt(target.getBoundingBox().getCenter());
        Vec3 o = overhead();
        level.playSound(null, o.x, o.y, o.z, ModSounds.FORTRESS_JAILER_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, o.x, o.y, o.z, 8, 0.4, 0.1, 0.4, 0.1);
        return true;
    }

    /** Where the whirling chain is: over its raised right fist. */
    private Vec3 overhead() {
        return shoulder(getYRot()).add(0.0, (ARM_LENGTH + 0.3) * getScale(), 0.0);
    }

    /** The wind: it keeps turning to its target, iron sparks fly off the whirling chain, and it rattles again halfway. */
    private void windTick(ServerLevel level) {
        LivingEntity target = core().target();
        if (valid(target)) aimAt(target.getBoundingBox().getCenter());
        int t = core().elapsed();
        Vec3 o = overhead();
        if (t % 2 == 0) {
            double a = t * 0.9;
            double r = 0.6 * getScale();
            level.sendParticles(IRON, o.x + Math.cos(a) * r, o.y, o.z + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0.0);
            level.sendParticles(ParticleTypes.CRIT, o.x - Math.cos(a) * r, o.y, o.z - Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.05);
        }
        if (t == TELL_TICKS / 2) level.playSound(null, o.x, o.y, o.z, ModSounds.FORTRESS_JAILER_TELL.get(), getSoundSource(), 0.8F, 1.15F);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (!throwChain(level, valid(target) ? target.getBoundingBox().getCenter() : null)) finish();
        } else if (ended == Step.THROW) {
            flying = false;
            reel(level, travelled, false);
        } else if (ended == Step.PULL) {
            letGo();
            reel(level, chainToVictimOr(travelled), false);
        } else {
            finish();
        }
    }

    private void finish() {
        flying = false;
        letGo();
        hideChain();
        core().end();
    }

    /**
     * Throws the chain head from its fist straight at {@code point} (straight ahead, level, with none). The line is
     * fixed now: the head never turns after a target that moves. Returns false if an event cancelled the throw.
     */
    private boolean throwChain(ServerLevel level, @Nullable Vec3 point) {
        float p = point != null ? aimAt(point) : 0.0F;
        Vec3 dir = direction(getYRot(), p);
        Vec3 from = fist(dir);
        if (!startPhase(Step.THROW, THROW_TICKS, core().target())) return false;
        pitch = p;
        chainFrom = from;
        chainDir = dir;
        travelled = 0.0;
        flying = true;
        // clients draw the chain growing at CHAIN_SPEED up to the first wall on its line
        BlockHitResult wall = level.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        showChain(wall.getType() == HitResult.Type.MISS ? RANGE : from.distanceTo(wall.getLocation()), p);
        level.playSound(null, from.x, from.y, from.z, ModSounds.FORTRESS_JAILER_THROW.get(), getSoundSource(), 1.0F, 1.0F);
        return true;
    }

    /** Throws the chain at {@code target} at once, without the tell or the range check. Public as a test hook. */
    public boolean throwAt(LivingEntity target) {
        if (!(level() instanceof ServerLevel level) || !core().idle()) return false;
        return throwChain(level, target.getBoundingBox().getCenter());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isNoAi()) return;
        // after its own move this tick, like any projectile
        if (core().in(Step.THROW) && flying) fly(level);
        else if (core().in(Step.PULL)) pullTick(level);
        else if (core().in(Step.STRIKE) && !struck && core().elapsed() >= STRIKE_HIT_TICK) land(level);
    }

    /** Moves the chain head one tick along its line; stops it at the first block or hitbox, or at the end of its reach. */
    private void fly(ServerLevel level) {
        double step = Math.min(CHAIN_SPEED, RANGE - travelled);
        Vec3 from = chainFrom.add(chainDir.scale(travelled));
        Vec3 to = from.add(chainDir.scale(step));
        BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = wall.getType() != HitResult.Type.MISS;
        if (blocked) to = wall.getLocation();
        LivingEntity victim = null;
        Vec3 at = to;
        double best = Double.MAX_VALUE;
        AABB sweep = new AABB(from, to).inflate(CHAIN_RADIUS + 0.1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, this::chainCanHit)) {
            AABB box = e.getBoundingBox().inflate(CHAIN_RADIUS);
            Optional<Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < best) {
                best = from.distanceToSqr(hit.get());
                victim = e;
                at = hit.get();
            }
        }
        level.sendParticles(IRON, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        if (victim != null) {
            flying = false;
            travelled = chainFrom.distanceTo(at);
            catchOn(level, victim, at);
            return;
        }
        travelled += from.distanceTo(to);
        if (blocked || travelled >= RANGE - 1.0E-6) {
            flying = false;
            level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 6, 0.1, 0.1, 0.1, 0.2);
            if (blocked) level.sendParticles(SPARK, to.x, to.y, to.z, 4, 0.1, 0.1, 0.1, 0.0);
            reel(level, travelled, true);
        }
    }

    private boolean chainCanHit(LivingEntity e) {
        return e != this && !(e instanceof FortressJailer) && e.isAlive() && e.canBeHitByProjectile() && !e.isSpectator();
    }

    /**
     * The chain head reaches a hitbox. A shield raised toward it takes it and the chain drops; prey it can drag is
     * hooked; anything else (too big, in creative) only stops it.
     */
    private void catchOn(ServerLevel level, LivingEntity victim, Vec3 at) {
        if (shieldBlocks(level, victim)) {
            level.playSound(null, at.x, at.y, at.z, ModSounds.FORTRESS_JAILER_CLINK.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.3);
            level.sendParticles(SPARK, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.0);
            reel(level, travelled, true);
            return;
        }
        if (grab.fits(this, victim) && startPhase(Step.PULL, PULL_TICKS, victim)) {
            grab.take(victim);
            showChain(travelled, pitch);
            level.playSound(null, at.x, at.y, at.z, ModSounds.FORTRESS_JAILER_HOOK.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(IRON, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.0);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
            return;
        }
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
        reel(level, travelled, true);
    }

    /**
     * True if {@code victim} holds up a shield that takes the chain head: vanilla's own blocking rule, with the head
     * coming from the jailer like any mob's projectile (so the shield has to face it).
     */
    private boolean shieldBlocks(ServerLevel level, LivingEntity victim) {
        return victim.applyItemBlocking(level, damageSources().mobProjectile(this, this), SHIELD_WEIGHT) > 0.0F;
    }

    /** Drags the hooked victim one tick closer; once it stands next to the jailer, it is struck. */
    private void pullTick(ServerLevel level) {
        LivingEntity v = grab.victim();
        if (v == null || !grab.holding(this, position())) {
            letGo();
            reel(level, travelled, true);
            return;
        }
        float p = aimAt(v.getBoundingBox().getCenter());
        double across = Math.hypot(getX() - v.getX(), getZ() - v.getZ());
        if (across <= STRIKE_REACH) {
            strike(level, v);
            return;
        }
        Vec3 toward = new Vec3(getX() - v.getX(), 0.0, getZ() - v.getZ()).normalize();
        double speed = Math.min(PULL_SPEED, across - 1.0);     // the last stretch only up to its side, never into it
        Push.set(v, new Vec3(toward.x * speed, v.getDeltaMovement().y, toward.z * speed));
        v.resetFallDistance();
        if (v instanceof Mob m) m.getNavigation().stop();
        // clients draw the chain as long as it was when it hooked, less PULL_SPEED a tick: only a new slope is sent
        if (Math.abs(p - pitch) >= 2.0F) {
            pitch = p;
            showChain(travelled, p);
        }
    }

    private double chainToVictimOr(double fallback) {
        LivingEntity v = grab.victim();
        return v == null ? fallback : Math.min(RANGE, fist(chainDir).distanceTo(v.getBoundingBox().getCenter()));
    }

    /** The victim stands next to it: it lets go of the chain, stops the drag and raises its chained fist. */
    private void strike(ServerLevel level, LivingEntity v) {
        letGo();
        Push.set(v, new Vec3(0.0, v.getDeltaMovement().y, 0.0));
        if (!startPhase(Step.STRIKE, STRIKE_TICKS, v)) {
            finish();
            return;
        }
        struck = false;
        hideChain();
    }

    /** The fist lands: its attack damage to the victim, if it is still next to it. */
    private void land(ServerLevel level) {
        struck = true;
        LivingEntity v = core().target();
        if (!valid(v) || Math.hypot(getX() - v.getX(), getZ() - v.getZ()) > STRIKE_HIT_REACH || Math.abs(getY() - v.getY()) > 2.0) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        v.hurtServer(level, damageSources().mobAttack(this), damage);
        level.playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.FORTRESS_JAILER_STRIKE.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, v.getX(), v.getY(0.6), v.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
    }

    /** A loose chain, missed, blocked or let go: it comes back in at REEL_SPEED, then the jailer is free again. */
    private void reel(ServerLevel level, double length, boolean dropped) {
        flying = false;
        length = Mth.clamp(length, 0.0, RANGE);
        int ticks = Math.max(MIN_REEL_TICKS, Mth.ceil(length / REEL_SPEED));
        if (!startPhase(Step.REEL, ticks, null)) {
            finish();
            return;
        }
        showChain(length, pitch);
        if (dropped) {
            Vec3 head = chainFrom.add(chainDir.scale(length));
            level.playSound(null, head.x, head.y, head.z, ModSounds.FORTRESS_JAILER_RATTLE.get(), getSoundSource(), 0.8F, 1.0F);
        }
    }

    private void letGo() {
        grab.release();
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    // its own victim neither pushes it nor is pushed by it
    @Override
    public void push(Entity other) {
        if (!grab.holds(other)) super.push(other);
    }

    @Override
    protected void doPush(Entity other) {
        if (!grab.holds(other)) super.doPush(other);
    }

    @Override
    public void die(DamageSource source) {
        letGo();
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        letGo();
        super.onRemoval(reason);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Who its chain holds, or null. */
    public @Nullable LivingEntity hooked() {
        return grab.victim();
    }

    /** True while the chain head is in flight. */
    public boolean chainFlying() {
        return flying;
    }

    /** How far the chain head has gone from its fist on this throw, in blocks. */
    public double chainTravelled() {
        return travelled;
    }

    /** Ticks until it may start its next tell. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it throw again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.FORTRESS_JAILER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.FORTRESS_JAILER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FORTRESS_JAILER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + random.nextFloat() * 0.1F;
    }
}
