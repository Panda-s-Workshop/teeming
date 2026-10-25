package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * Snapmaw Bloom: a flytrap flower of lush caves and jungle floors, rooted where it grew. It never walks and nothing
 * pushes it off its spot; it only turns, at most 45 degrees a second. Tell: its jaws open for 10 ticks with a glowing
 * seed inside. Then, at a target in the next block, it snaps the jaws shut for 1.5x Atk; otherwise it spits the seed
 * straight along the way it faces (not where the target went): a glowing streak that flies 14 blocks at 0.8 blocks a
 * tick and hits the first thing in its path for 3. One attack every 40 ticks, and only at a target in front of it.
 * Counter: step aside when the jaws open, and circle it; hits from its rear 120 degrees deal x2, and its seed sac
 * glows gold while its target stands there.
 */
public class SnapmawBloom extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL, SPIT, SNAP }

    public static final int TELL_TICKS = 10;
    public static final int SPIT_TICKS = 6;             // the head's recoil after a spit
    public static final int SNAP_TICKS = 6;             // the jaws held shut after a snap
    public static final int SPIT_EVERY = 40;            // ticks from one attack to the next, tell included
    public static final double RANGE = 14.0;            // it spits at targets this close, and a seed flies this far
    public static final float SEED_DAMAGE = 3.0F;
    public static final double SEED_SPEED = 0.8;        // blocks a tick: 14 blocks in 18 ticks, after a 10-tick tell
    public static final double SEED_RADIUS = 0.15;      // how far from a hitbox a seed still strikes
    public static final float SNAP_HIT = 1.5F;          // x attack damage
    public static final double SNAP_REACH = 1.6;        // centre to centre: a target in the next block
    public static final float TURN_RATE = 45.0F;        // degrees a second
    public static final float TURN_PER_TICK = TURN_RATE / 20.0F;
    public static final float REAR_ARC = 120.0F;        // degrees, centred on its back
    public static final float REAR_DAMAGE = 2.0F;
    public static final float AIM_CONE = 15.0F;         // a spit's tell starts only with the target this close to its line
    public static final float BITE_ARC = 120.0F;        // degrees, centred on its front
    public static final double MOUTH_FORWARD = 0.55;    // where seeds leave, from its feet, in blocks
    public static final double MOUTH_HEIGHT = 1.0;
    private static final double MAX_PITCH = Math.toRadians(60.0);
    private static final int FLANKED = 0;               // flag bit: its target stands in its rear arc

    /** The seed's colour, the glow-berry orange of the seed in its mouth. */
    public static final int SEED_COLOR = 0xF0A030;
    private static final DustParticleOptions SEED_DUST = new DustParticleOptions(SEED_COLOR, 1.6F);
    private static final DustParticleOptions SAC_GOLD = new DustParticleOptions(0xFFD36B, 1.2F);

    private final List<Seed> seeds = new ArrayList<>();
    private int cooldown;
    private @Nullable LivingEntity pendingTell;

    public SnapmawBloom(EntityType<? extends SnapmawBloom> type, Level level) {
        super(type, level, Step.values());
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("snapmaw_bloom", 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean vulnerable() {
        return core().flag(FLANKED);
    }

    // ---- rooted: it turns, nothing more ----------------------------------------------------------------------------

    /** Only falling moves it: walking, knockback, pistons and currents all lose their sideways part. */
    @Override
    public void move(MoverType type, Vec3 delta) {
        super.move(type, new Vec3(0.0, delta.y, 0.0));
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Body and head are one piece: the body always faces where the head does. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        this.yBodyRot = this.yHeadRot;
    }

    /** Turns toward {@code point} by at most {@link #TURN_PER_TICK}. */
    private void turnToward(Vec3 point) {
        setFacing(Mth.approachDegrees(getYRot(), yawTo(point), TURN_PER_TICK));
    }

    private void setFacing(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Turns to face {@code point} at once. Public as a test hook. */
    public void face(Vec3 point) {
        setFacing(yawTo(point));
        yRotO = getYRot();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
    }

    private float yawTo(Vec3 point) {
        return (float) (Mth.atan2(point.z - getZ(), point.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** Degrees from the way it faces to {@code point}, -180..180. */
    public float angleTo(Vec3 point) {
        return Mth.degreesDifference(getYRot(), yawTo(point));
    }

    /** True if {@code point} lies in its rear 120 degrees. */
    public boolean inRearArc(Vec3 point) {
        if (point.distanceToSqr(getX(), point.y, getZ()) < 1.0E-6) return false;
        return Math.abs(angleTo(point)) >= 180.0F - REAR_ARC / 2.0F;
    }

    private Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    /** Where seeds leave its mouth. */
    public Vec3 mouth() {
        return position().add(forward().scale(MOUTH_FORWARD * getScale())).add(0.0, MOUTH_HEIGHT * getScale(), 0.0);
    }

    private boolean canBite(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        return dx * dx + dz * dz <= SNAP_REACH * SNAP_REACH && Math.abs(target.getY() - getY()) <= 1.0
                && Math.abs(angleTo(target.position())) <= BITE_ARC / 2.0F;
    }

    // ---- the attack cycle ------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        boolean valid = target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
        if (valid) turnToward(target.position());
        core().setFlag(FLANKED, valid && inRearArc(target.position()));
        if (!valid || !core().idle() || cooldown > 0) return;
        boolean lined = Math.abs(angleTo(target.position())) <= AIM_CONE && distanceTo(target) <= RANGE && getSensing().hasLineOfSight(target);
        if (canBite(target) || lined) pendingTell = target;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        // a tell starts after this tick's phase clock has run, so it lasts exactly TELL_TICKS
        if (pendingTell != null) {
            LivingEntity target = pendingTell;
            pendingTell = null;
            if (core().idle() && startPhase(Step.TELL, TELL_TICKS, target)) {
                playSound(ModSounds.SNAPMAW_BLOOM_TELL.get(), 1.0F, 1.0F);
                Vec3 m = mouth();
                level.sendParticles(ParticleTypes.WAX_ON, m.x, m.y + 0.35 * getScale(), m.z, 3, 0.25, 0.1, 0.25, 0.2);
            }
        }
        phaseParticles(level);
        flySeeds(level);
    }

    /** The tell's glow at the mouth and the spit's spray: from the phase alone, so they show however the phase began. */
    private void phaseParticles(ServerLevel level) {
        Vec3 m = mouth();
        // sparse, so the open maw and the seed inside stay readable
        if (core().in(Step.TELL) && tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.WAX_ON, m.x, m.y + 0.35 * getScale(), m.z, 1, 0.15, 0.05, 0.15, 0.1);
        } else if (core().in(Step.SPIT) && core().elapsed() == 1) {
            Vec3 f = forward();
            for (int i = 1; i <= 3; i++) {
                level.sendParticles(SEED_DUST, m.x + f.x * 0.25 * i, m.y, m.z + f.z * 0.25 * i, 1, 0.04 * i, 0.04 * i, 0.04 * i, 0.0);
            }
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            cooldown = SPIT_EVERY - TELL_TICKS;
            LivingEntity target = getTarget();
            if (target != null && target.isAlive() && canBite(target)) {
                if (startPhase(Step.SNAP, SNAP_TICKS, target)) snap(level, target);
                else core().end();
            } else if (startPhase(Step.SPIT, SPIT_TICKS, target)) {
                spit(level);
            } else {
                core().end();
            }
        } else {
            core().end();
        }
    }

    /** The jaws slam shut on a target in the next block: 1.5x Atk. Returns whether it hurt. Public as a test hook. */
    public boolean snap(ServerLevel level, LivingEntity target) {
        Vec3 m = mouth();
        level.playSound(null, m.x, m.y, m.z, ModSounds.SNAPMAW_BLOOM_SNAP.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.WAX_ON, m.x, m.y, m.z, 4, 0.2, 0.1, 0.2, 0.3);
        if (!canBite(target) || !MechanicEvent.post(this, "snap_hit", target)) return false;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * SNAP_HIT;
        boolean hurt = target.hurtServer(level, damageSources().mobAttack(this), damage);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        return hurt;
    }

    /**
     * Spits one seed from the mouth straight along the way it faces, pitched at its target's middle (level without
     * one). Returns false if an event cancelled it. Public as a test hook.
     */
    public boolean spit(ServerLevel level) {
        LivingEntity target = getTarget();
        if (!MechanicEvent.post(this, "seed", target)) return false;
        Vec3 m = mouth();
        double pitch = 0.0;
        if (target != null) {
            double flat = Math.max(0.5, Math.hypot(target.getX() - m.x, target.getZ() - m.z));
            pitch = Mth.clamp(Math.atan2(target.getY(0.5) - m.y, flat), -MAX_PITCH, MAX_PITCH);
        }
        Vec3 f = forward();
        seeds.add(new Seed(m, new Vec3(f.x * Math.cos(pitch), Math.sin(pitch), f.z * Math.cos(pitch))));
        level.playSound(null, m.x, m.y, m.z, ModSounds.SNAPMAW_BLOOM_SPIT.get(), getSoundSource(), 1.0F, 1.0F);
        return true;
    }

    /** How many of its seeds are in the air. */
    public int seedsInFlight() {
        return seeds.size();
    }

    // ---- seeds -----------------------------------------------------------------------------------------------------

    /**
     * A seed in flight. It is not an entity: the plant moves it {@link #SEED_SPEED} along a straight line each tick,
     * stops it at the first block or hitbox on that stretch, and draws it with particles. So it can be dodged, it never
     * homes, and it ends at {@link #RANGE}. Seeds are not saved; a reload drops the ones in the air.
     */
    private static final class Seed {
        private Vec3 pos;
        private final Vec3 dir;
        private double travelled;

        private Seed(Vec3 pos, Vec3 dir) {
            this.pos = pos;
            this.dir = dir.normalize();
        }
    }

    private void flySeeds(ServerLevel level) {
        for (Iterator<Seed> it = seeds.iterator(); it.hasNext(); ) {
            if (fly(level, it.next())) it.remove();
        }
    }

    /** Moves one seed one tick; true when it is spent. */
    private boolean fly(ServerLevel level, Seed seed) {
        double step = Math.min(SEED_SPEED, RANGE - seed.travelled);
        Vec3 from = seed.pos;
        Vec3 to = from.add(seed.dir.scale(step));
        BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = wall.getType() != HitResult.Type.MISS;
        if (blocked) to = wall.getLocation();
        LivingEntity victim = null;
        Vec3 at = to;
        double best = Double.MAX_VALUE;
        AABB sweep = new AABB(from, to).inflate(SEED_RADIUS + 0.1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, this::seedCanHit)) {
            AABB box = e.getBoundingBox().inflate(SEED_RADIUS);
            Optional<Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < best) {
                best = from.distanceToSqr(hit.get());
                victim = e;
                at = hit.get();
            }
        }
        trail(level, from, at);
        if (victim != null) {
            strike(level, victim, at);
            return true;
        }
        seed.pos = to;
        seed.travelled += step;
        if (blocked || seed.travelled >= RANGE - 1.0E-6) {
            burst(level, to);
            return true;
        }
        return false;
    }

    private boolean seedCanHit(LivingEntity e) {
        return e != this && !(e instanceof SnapmawBloom) && e.canBeHitByProjectile() && !e.isSpectator();
    }

    /** The streak: dense seed-coloured dust along this tick's stretch, and glowing sparks that linger behind it. */
    private void trail(ServerLevel level, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int points = Math.max(1, (int) Math.ceil(length / 0.2));
        for (int i = 1; i <= points; i++) {
            Vec3 p = from.lerp(to, (double) i / points);
            level.sendParticles(SEED_DUST, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 2 == 0) level.sendParticles(ParticleTypes.WAX_ON, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void strike(ServerLevel level, LivingEntity victim, Vec3 at) {
        if (MechanicEvent.post(this, "seed_hit", victim)) {
            victim.hurtServer(level, damageSources().mobProjectile(this, this), SEED_DAMAGE);
        }
        level.sendParticles(SEED_DUST, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SNAPMAW_BLOOM_SEED_HIT.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** A seed that hit nothing breaks where it stopped, so a dodge shows where the shot went. */
    private void burst(ServerLevel level, Vec3 at) {
        level.sendParticles(SEED_DUST, at.x, at.y, at.z, 6, 0.12, 0.12, 0.12, 0.0);
        level.sendParticles(ParticleTypes.WAX_ON, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.3);
        level.playSound(null, at.x, at.y, at.z, ModSounds.SNAPMAW_BLOOM_SEED_BURST.get(), getSoundSource(), 0.6F, 1.0F);
    }

    // ---- hits from behind ------------------------------------------------------------------------------------------

    /** True if the blow came from its rear 120 degrees (by where it came from; damage with no source position never is). */
    public boolean fromRear(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        return from != null && inRearArc(from);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean rear = fromRear(source) && MechanicEvent.post(this, "rear_hit", source.getEntity() instanceof LivingEntity l ? l : null);
        boolean hurt = super.hurtServer(level, source, rear ? amount * REAR_DAMAGE : amount);
        if (hurt && rear) {
            Vec3 back = position().add(forward().scale(-0.25 * getScale())).add(0.0, getBbHeight() * 0.8, 0.0);
            level.sendParticles(SAC_GOLD, back.x, back.y, back.z, 12, 0.2, 0.2, 0.2, 0.0);
            level.playSound(null, back.x, back.y, back.z, ModSounds.SNAPMAW_BLOOM_WEAK_HIT.get(), getSoundSource(), 1.0F, 1.0F);
        }
        return hurt;
    }

    /** Ticks until it may start its next tell. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SNAPMAW_BLOOM_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SNAPMAW_BLOOM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SNAPMAW_BLOOM_DEATH.get();
    }
}
