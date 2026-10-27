package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
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
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hollowmind: a small void parasite of the End highlands, a cleft dome with a lilac core and six trailing tendrils.
 *
 * <p>Alone it creeps toward the nearest enderman within 16 blocks. Beside one it crouches flat, reaches all six
 * tendrils straight up and its core swells with a wet pulse for 20 ticks (the latch tell), then climbs onto the
 * enderman's head and rides it. While it rides, its dome glows weak-point gold and the host is taken over: it turns on
 * the parasite's target (the nearest player it sees) as if stared at, moves 30 % faster and hits 50 % harder (attribute
 * modifiers, gone the moment it leaves), and takes no harm from water or rain. The parasite stays on the head, where it
 * can be hit. Players that come close to it on the ground get nipped: the front rears and its maw opens for 8 ticks,
 * then a bite of its attack (4), at most once a second.
 *
 * <p>Counter: hit the parasite on the host's head (it is small and has 14 health). Killed there, it lets go and the
 * freed enderman calms down and is neutral again; it drops nothing of its own. Kill the host instead and the parasite
 * drops off with 100 ticks to reach another enderman within 16 blocks and climb on; after that it is on its own and
 * never takes a host again.
 */
public class Hollowmind extends TeemingMonster {
    /** IDLE, the latch tell, riding a host, scurrying for a new host after the old one died, the nip's tell, the bite. */
    public enum Step implements Phase { IDLE, TELL, RIDE, SEEK, NIP, BITE }

    /** How far it looks for a host: the nearest enderman within 16 blocks. */
    public static final double HOST_RANGE = 16.0;
    /** The latch: a 20-tick tell started within 2 blocks of the host, which it climbs onto if it is still within 3. */
    public static final int LATCH_TICKS = 20;
    public static final double LATCH_START = 2.0;
    public static final double LATCH_REACH = 3.0;
    /** After a latch that came to nothing (the host walked off), a second before it tries again. */
    public static final int LATCH_RETRY = 20;
    /** What the host gains while ridden: +30 % movement speed and +50 % attack damage. */
    public static final double HOST_SPEED = 0.3;
    public static final double HOST_DAMAGE = 0.5;
    /** After the host dies: 100 ticks to climb onto another, scurrying at half again its creeping pace. */
    public static final int SEEK_TICKS = 100;
    public static final double SEEK_PACE = 1.5;
    /** The nip: an 8-tick tell, then a bite of its attack (4) over 6 ticks, at most once every 20 ticks. */
    public static final int NIP_TICKS = 8;
    public static final int BITE_TICKS = 6;
    public static final int NIP_EVERY = 20;
    /** Flag bit: it sits on a host's head (the model hangs its tendrils down the sides of the head). */
    public static final int FLAG_ON_HOST = 0;
    /** An angry enderman lifts its head 5 pixels; the parasite rides that much higher so it never sinks into it. */
    public static final double ANGRY_HEAD_LIFT = 5.0 / 16.0;

    private static final Identifier SPEED_BOOST = TeemingMod.id("hollowmind_host_speed");
    private static final Identifier DAMAGE_BOOST = TeemingMod.id("hollowmind_host_damage");

    /** Lilac, its core's light: well off weak-point gold. */
    public static final int CORE_LILAC = 0xC58CFF;
    private static final DustParticleOptions LILAC = new DustParticleOptions(CORE_LILAC, 1.0F);
    private static final Telegraph PULSE = Telegraph.particles(LILAC, 10, 0.35, 0.6).withSpeed(0.02);
    private static final Telegraph REACH = Telegraph.particles(ParticleTypes.REVERSE_PORTAL, 6, 0.25, 1.0).withSpeed(0.05);
    private static final Telegraph BARE = Telegraph.particles(ParticleTypes.PORTAL, 8, 0.3, 0.4).withSpeed(0.3);

    private @Nullable Enderman quarry;
    private @Nullable LivingEntity turnedOn;
    private @Nullable Enderman freed;
    private long freedAt;
    private long seekEnd;
    private boolean orphan;
    private int nipCooldown, latchCooldown;
    private int latches, bites, frees;

    public Hollowmind(EntityType<? extends Hollowmind> type, Level level) {
        super(type, level, Step.values());
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("hollowmind", 0.24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return free() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return free() && super.canContinueToUse();
            }

            /** It closes in only: every bite is the nip's tell and the bite, started from its AI step. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return free() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return free() && super.canContinueToUse();
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

    /** Idle, on the ground and with no enderman to make for: its goals may walk it. */
    private boolean free() {
        return core().idle() && quarry == null && !isPassenger();
    }

    // ---- riding ----------------------------------------------------------------------------------------------------

    /** The enderman it rides, or null. */
    public @Nullable Enderman host() {
        return getVehicle() instanceof Enderman host ? host : null;
    }

    /** True while a living Hollowmind rides {@code enderman}: it takes no harm from water or rain. */
    public static boolean keepsDry(Enderman enderman) {
        return enderman.getFirstPassenger() instanceof Hollowmind h && h.isAlive();
    }

    /** The host steers itself; the parasite only rides. */
    @Override
    public boolean canControlVehicle() {
        return false;
    }

    /** It sits on the host's head, and rides higher while the angry head is lifted. */
    @Override
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        Vec3 point = super.getVehicleAttachmentPoint(vehicle);
        return vehicle instanceof Enderman e && e.isCreepy() ? point.add(0.0, -ANGRY_HEAD_LIFT, 0.0) : point;
    }

    /** Its eye is above the host's head: a low ceiling the host walks under doesn't smother it. */
    @Override
    public boolean isInWall() {
        return !isPassenger() && super.isInWall();
    }

    /** Its dome glows weak-point gold while it rides: that is where to hit. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.RIDE);
    }

    @Override
    public void tick() {
        super.tick();
        Enderman host = host();
        if (host != null) {
            // it faces the way the host's head does, on both sides, so it sits square on the head
            setYRot(host.getYHeadRot());
            setYBodyRot(host.getYHeadRot());
            setYHeadRot(host.getYHeadRot());
        }
        if (!(level() instanceof ServerLevel level)) return;
        if (isDeadOrDying()) {
            // a blow that killed it can sweep the host too, in the same swing: the host stays calm through the next tick
            if (freed != null && level.getGameTime() - freedAt <= 1) calm(freed);
            return;
        }
        if (host != null) ride(level, host);
    }

    /** Server, every tick it rides: drop off a dying host, else keep the host's boosts and turn it on its target. */
    private void ride(ServerLevel level, Enderman host) {
        if (host.isDeadOrDying()) {
            stopRiding();
            return;
        }
        if (!core().in(Step.RIDE) && !startPhase(Step.RIDE, 0, host)) {
            stopRiding();
            return;
        }
        if (!core().flag(FLAG_ON_HOST)) core().setFlag(FLAG_ON_HOST, true);
        boost(host);
        LivingEntity t = getTarget();
        if (valid(t) && t != host) {
            if (t != turnedOn || host.getTarget() != t) {
                if (MechanicEvent.post(this, "turn", t)) turn(host, t);
            } else if (tickCount % 20 == 0) {
                host.startPersistentAngerTimer();
            }
        }
        if (tickCount % 6 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.2, getZ(), 1, 0.2, 0.1, 0.2, 0.01);
    }

    /** The host turns on {@code t} as an enderman does on a player who stares at it. */
    private void turn(Enderman host, LivingEntity t) {
        host.setBeingStaredAt();
        host.setTarget(t);
        host.setPersistentAngerTarget(EntityReference.of(t));
        host.startPersistentAngerTimer();
        turnedOn = t;
    }

    private static void boost(Enderman host) {
        AttributeInstance speed = host.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(SPEED_BOOST)) {
            speed.addTransientModifier(new AttributeModifier(SPEED_BOOST, HOST_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance damage = host.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && !damage.hasModifier(DAMAGE_BOOST)) {
            damage.addTransientModifier(new AttributeModifier(DAMAGE_BOOST, HOST_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void unboost(Enderman host) {
        AttributeInstance speed = host.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_BOOST);
        AttributeInstance damage = host.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.removeModifier(DAMAGE_BOOST);
    }

    /** The freed host forgets its anger and its target: it is a neutral enderman again. */
    private static void calm(Enderman host) {
        if (host.isAlive()) host.stopBeingAngry();
    }

    /**
     * Every way off a host comes through here: the boosts go at once. Discarded while riding, it leaves the host calm;
     * unloaded with it, nothing else changes. Off a host that died, it scurries for a new one.
     */
    @Override
    public void removeVehicle() {
        Entity old = getVehicle();
        super.removeVehicle();
        if (!(old instanceof Enderman host) || level().isClientSide()) return;
        unboost(host);
        turnedOn = null;
        core().setFlag(FLAG_ON_HOST, false);
        if (isRemoved()) {
            RemovalReason reason = getRemovalReason();
            if (reason != null && reason.shouldDestroy()) calm(host);
            return;
        }
        if (core().in(Step.RIDE)) core().end();
        if (!isDeadOrDying() && (host.isDeadOrDying() || host.getRemovalReason() == RemovalReason.KILLED)) dropOff();
    }

    /** Off a dead host: a hop, a squirt, and the 100-tick search starts. */
    private void dropOff() {
        if (!(level() instanceof ServerLevel level)) return;
        setDeltaMovement(getDeltaMovement().add(0.0, 0.25, 0.0));
        level.sendParticles(LILAC, getX(), getY() + 0.3, getZ(), 12, 0.3, 0.2, 0.3, 0.02);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.3, getZ(), 10, 0.3, 0.2, 0.3, 0.05);
        playSound(ModSounds.HOLLOWMIND_DROP.get(), 1.0F, 1.0F);
        seekEnd = level.getGameTime() + SEEK_TICKS;
        if (!startPhase(Step.SEEK, SEEK_TICKS, null)) becomeOrphan();
    }

    private void becomeOrphan() {
        orphan = true;
        seekEnd = 0;
    }

    /** Killed on a host: it lets go, and the host calms down (the counter). */
    @Override
    public void die(DamageSource source) {
        Enderman host = host();
        super.die(source);
        if (host == null || !(level() instanceof ServerLevel level)) return;
        freed = host;
        freedAt = level.getGameTime();
        frees++;
        stopRiding();
        unboost(host);
        calm(host);
        double y = host.getY() + host.getBbHeight();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, host.getX(), y, host.getZ(), 24, 0.4, 0.3, 0.4, 0.06);
        level.sendParticles(LILAC, host.getX(), y, host.getZ(), 16, 0.4, 0.2, 0.4, 0.02);
        level.playSound(null, host.getX(), y, host.getZ(), ModSounds.HOLLOWMIND_FREED.get(), getSoundSource(), 1.2F, 1.0F);
    }

    // ---- the search, the latch and the nip -------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (nipCooldown > 0) nipCooldown--;
        if (latchCooldown > 0) latchCooldown--;
        if (isPassenger()) {
            quarry = null;
            return;
        }
        long now = level.getGameTime();
        if (!orphan && seekEnd > 0 && core().idle()) {
            if (now < seekEnd) {
                if (!startPhase(Step.SEEK, (int) (seekEnd - now), null)) becomeOrphan();
            } else {
                becomeOrphan();
            }
        }
        if (core().in(Step.TELL)) {
            holdStill();
            LivingEntity host = core().target();
            if (host != null) getLookControl().setLookAt(host, 30.0F, 30.0F);
            PULSE.trail(this, 2);
            REACH.trail(this, 3);
            return;
        }
        if (core().in(Step.NIP) || core().in(Step.BITE)) {
            holdStill();
            LivingEntity t = core().target();
            if (t != null) getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (core().in(Step.NIP)) BARE.trail(this, 2);
            return;
        }
        boolean seeking = core().in(Step.SEEK);
        if (!core().idle() && !seeking) return;
        LivingEntity target = getTarget();
        if (!seeking && nipCooldown == 0 && valid(target) && isWithinMeleeAttackRange(target) && hasLineOfSight(target)) {
            nip(target);
            return;
        }
        if (orphan || latchCooldown > 0) {
            quarry = null;
            return;
        }
        if (quarry == null || tickCount % 10 == 0 || !canHost(quarry)) quarry = findHost(level);
        if (quarry == null) return;
        if (distanceTo(quarry) <= LATCH_START) {
            latch(quarry);
        } else if (tickCount % 5 == 0 || getNavigation().isDone()) {
            getNavigation().moveTo(quarry, seeking ? SEEK_PACE : 1.0);
        }
        if (seeking && tickCount % 3 == 0) level.sendParticles(LILAC, getX(), getY() + 0.2, getZ(), 1, 0.2, 0.05, 0.2, 0.0);
    }

    /**
     * The nearest enderman within 16 blocks that has no rider, or null. Each one it considers is a "host" step, so a
     * listener can rule an enderman out. Public as a test hook.
     */
    public @Nullable Enderman findHost(ServerLevel level) {
        List<Enderman> near = level.getEntitiesOfClass(Enderman.class, getBoundingBox().inflate(HOST_RANGE),
                e -> canHost(e) && MechanicEvent.post(this, "host", e));
        Enderman best = null;
        double bestSq = Double.MAX_VALUE;
        for (Enderman e : near) {
            double d = distanceToSqr(e);
            if (inHostRange(d) && d < bestSq) {
                best = e;
                bestSq = d;
            }
        }
        return best;
    }

    /** True when an enderman {@code distanceSq} (squared blocks) away is near enough to make for. */
    public static boolean inHostRange(double distanceSq) {
        return distanceSq <= HOST_RANGE * HOST_RANGE;
    }

    private boolean canHost(@Nullable Enderman e) {
        return e != null && e.isAlive() && !e.isRemoved() && !e.isVehicle() && !e.isPassenger() && e.level() == level();
    }

    /** Starts the latch tell beside {@code host}; true if it did. Public as a test hook. */
    public boolean latch(Enderman host) {
        if (level().isClientSide() || orphan || isPassenger() || !canHost(host)) return false;
        if (!(core().idle() || core().in(Step.SEEK))) return false;
        if (!startPhase(Step.TELL, LATCH_TICKS, host)) return false;
        holdStill();
        PULSE.withSound(ModSounds.HOLLOWMIND_LATCH.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    /** Starts the nip's tell at {@code target}; true if it did. Public as a test hook. */
    public boolean nip(LivingEntity target) {
        if (level().isClientSide() || isPassenger() || !core().idle() || nipCooldown > 0) return false;
        if (!startPhase(Step.NIP, NIP_TICKS, target)) return false;
        nipCooldown = NIP_EVERY;
        holdStill();
        BARE.withSound(ModSounds.HOLLOWMIND_NIP.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) return;
        if (ended == Step.TELL) {
            climb(level, core().target() instanceof Enderman host ? host : null);
        } else if (ended == Step.NIP) {
            LivingEntity t = core().target();
            if (startPhase(Step.BITE, BITE_TICKS, t)) bite(level, t);
            else core().end();
        } else if (ended == Step.SEEK) {
            core().end();
            becomeOrphan();
        } else {
            core().end();
        }
    }

    /** The end of the latch tell: onto the host's head if it is still within reach, else a second's pause. */
    private void climb(ServerLevel level, @Nullable Enderman host) {
        boolean near = host != null && canHost(host) && distanceTo(host) <= LATCH_REACH;
        if (near && startPhase(Step.RIDE, 0, host) && startRiding(host, true, true)) {
            seekEnd = 0;
            quarry = null;
            latches++;
            core().setFlag(FLAG_ON_HOST, true);
            boost(host);
            double y = host.getY() + host.getBbHeight();
            level.sendParticles(LILAC, host.getX(), y, host.getZ(), 16, 0.35, 0.2, 0.35, 0.02);
            level.playSound(null, host.getX(), y, host.getZ(), ModSounds.HOLLOWMIND_GRIP.get(), getSoundSource(), 1.0F, 1.0F);
            return;
        }
        core().end();
        latchCooldown = LATCH_RETRY;
    }

    /** The end of the nip's tell: the bite lands if the target is still within reach. */
    private void bite(ServerLevel level, @Nullable LivingEntity target) {
        playSound(ModSounds.HOLLOWMIND_BITE.get(), 1.0F, 1.0F);
        if (valid(target) && isWithinMeleeAttackRange(target) && hasLineOfSight(target)) {
            doHurtTarget(level, target);
            bites++;
        }
    }

    private boolean valid(@Nullable LivingEntity e) {
        return e != null && e.isAlive() && !(e instanceof Hollowmind) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void holdStill() {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        quarry = null;
        super.onRemoval(reason);
    }

    // ---- save data -------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Orphan", orphan);
        output.putLong("SeekEnd", seekEnd);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        orphan = input.getBooleanOr("Orphan", false);
        seekEnd = input.getLongOr("SeekEnd", 0L);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** True once its time to find a new host ran out: it never takes one again. Public as a test hook. */
    public boolean orphan() {
        return orphan;
    }

    /** Lets it nip again at once. Public as a test hook. */
    public void resetNip() {
        nipCooldown = 0;
    }

    /** Hosts climbed onto, bites landed, hosts freed by its death. Public as test hooks. */
    public int latches() {
        return latches;
    }

    public int bites() {
        return bites;
    }

    public int frees() {
        return frees;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() || core().in(Step.RIDE) ? ModSounds.HOLLOWMIND_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HOLLOWMIND_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HOLLOWMIND_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.2F;
    }
}
