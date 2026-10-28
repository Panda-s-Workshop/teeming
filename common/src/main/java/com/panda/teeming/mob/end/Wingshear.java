package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wingshear: a narrow, swept-wing hunter of the open sky over the outer End. It leaves anyone on their feet alone and
 * hunts only players gliding on elytra within 48 blocks, flying 1.25 blocks a tick: faster than a glide that holds its
 * height, slower than a rocket's boost. Within 5 blocks of its glider it keeps station beside them and screeches for
 * 16 ticks (tell): its blade wings flare, their leading edges light up lilac and sparks stream off the tips. Then it
 * dives through at 1.5 blocks a tick: a hit deals its attack through armour, ends the glide and shuts the glider's
 * wings for 40 ticks. Counter: fire a rocket. A boosted glide outruns both its chase and its dive, and once the glider
 * is 8 blocks farther off than the closest it got, it loses them and circles off for 5 seconds, hunting nobody. A
 * player who lands is left alone at once.
 */
public class Wingshear extends TeemingMonster {
    public enum Step implements Phase { IDLE, CHASE, TELL, DIVE, RECOVER, LOST }

    /** It hunts gliders at most this far off, eye to eye in blocks. */
    public static final double HUNT_RANGE = 48.0;
    /** Its chase, in blocks a tick (25 m/s): a glide that holds its height runs about 0.5 to 1.1, a rocket's boost 1.67. */
    public static final double CHASE_SPEED = 1.25;
    /** The gap (hitbox to hitbox, across or up and down) at which it starts its screech. */
    public static final double SCREECH_REACH = 5.0;
    public static final int TELL_TICKS = 16;
    /** Its dive, in blocks a tick: it catches any glider short of a rocket's boost. */
    public static final double DIVE_SPEED = 1.5;
    /** The longest dive: 18 blocks at 1.5 a tick. */
    public static final int DIVE_TICKS = 12;
    /** x attack damage: the dive is its one blow, 6 for its attack of 6. */
    public static final float DIVE_HIT = 1.0F;
    /** After a hit the glider's wings stay shut this long: every try to glide again is put out. */
    public static final int NO_GLIDE_TICKS = 40;
    /** It loses a glider once the gap is this much wider than the closest it got in this hunt. */
    public static final double LOSE_GAP = 8.0;
    /** Lost, it circles off this long and hunts nobody. */
    public static final int LOST_TICKS = 100;
    /** Pulling up and away after a dive that hit. */
    public static final int RECOVER_TICKS = 20;
    /** How often it looks round for a glider while it soars. */
    public static final int SCAN_EVERY = 5;
    /** Soaring with nothing to hunt: a slow circle this wide round where it settled, at this speed. */
    public static final double SOAR_RADIUS = 8.0;
    public static final double SOAR_SPEED = 0.4;
    /** Circling off when lost, and climbing away after a hit, in blocks a tick. */
    public static final double LOST_SPEED = 0.9;
    public static final double RECOVER_SPEED = 0.7;
    /** It comes at its glider from this far above, so the dive drops onto them. */
    public static final double APPROACH_ABOVE = 2.0;

    /** Glow stages: 0 at rest, 1 the screech (the leading edges lit). */
    public static final int GLOW_REST = 0;
    public static final int GLOW_SCREECH = 1;

    private final GroundedWings grounded = new GroundedWings(NO_GLIDE_TICKS);
    private @Nullable Vec3 home;
    private double orbit;
    private double closest;
    private Vec3 station = Vec3.ZERO;
    private Vec3 heading = new Vec3(0, 0, 1);
    private @Nullable Vec3 lostFrom;
    private boolean forced;
    private @Nullable Phase shown;
    private int dives;
    private int hits;

    public Wingshear(EntityType<? extends Wingshear> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("wingshear", 0.3).add(Attributes.FLYING_SPEED, 0.6).add(Attributes.FOLLOW_RANGE, HUNT_RANGE);
    }

    /** It always flies: it never lands and never falls. */
    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- who it hunts --------------------------------------------------------------------------------------------

    /**
     * Whether it would hunt {@code player} now: a survival player gliding on elytra, within 48 blocks and in sight, not
     * yet sunk below the bottom of the world (it never drives anyone further into the void). Public as a test hook.
     */
    public boolean huntable(Player player) {
        return gliding(player) && distanceTo(player) <= HUNT_RANGE && hasLineOfSight(player);
    }

    private boolean gliding(LivingEntity e) {
        return e.isAlive() && e.isFallFlying() && e.getY() >= level().getMinY()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /**
     * Whether its hunt of {@code target} goes on: the glider still glides and is in range. A target handed to it from
     * outside ({@link #setTarget}) is hunted whether or not it glides.
     */
    private boolean stillHunted(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || distanceTo(target) > HUNT_RANGE) return false;
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return forced || gliding(target);
    }

    /**
     * A target handed to it by a command, another mod or a test: it hunts that one, gliding or not, and the rest of
     * its rules (screech, dive, losing an outrun target) hold as usual.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target != null && target != this && !level().isClientSide() && target.isAlive() && core().idle()) hunt(target, true);
    }

    /** Starts a hunt of {@code target}; false if an event vetoed it. */
    private boolean hunt(LivingEntity target, boolean handed) {
        if (!startPhase(Step.CHASE, 0, target)) return false;
        forced = handed;
        closest = MobCore.gap(this, target);
        return true;
    }

    /** Every glider it would hunt, nearest first, until one hunt isn't vetoed. */
    private void scan(ServerLevel level) {
        List<Player> found = new ArrayList<>();
        for (Player p : level.players()) {
            if (huntable(p)) found.add(p);
        }
        found.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (Player p : found) {
            if (hunt(p, false)) return;
        }
    }

    // ---- arriving in pairs ---------------------------------------------------------------------------------------

    /** The look a natural group shares: the first picks it, the others take it. */
    private record Pair(String look) implements SpawnGroupData {}

    /** A natural group (the spawner brings 1 or 2) flies in one look: the first's. */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (data instanceof Pair pair) {
            core().setLook(pair.look(), true);
            return data;
        }
        return reason == EntitySpawnReason.NATURAL ? new Pair(look()) : result;
    }

    // ---- the hunt ------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        getNavigation().stop();
        setNoGravity(true);
        LivingEntity target = core().target();
        if (core().in(Step.CHASE) || core().in(Step.TELL) || core().in(Step.DIVE)) {
            if (!stillHunted(target)) {
                settle();                                  // landed, gone or out of range: left alone
                return;
            }
            double gap = MobCore.gap(this, target);
            closest = Math.min(closest, gap);
            if (gap - closest >= LOSE_GAP) {
                lose(level, target);
                return;
            }
            if (core().in(Step.CHASE)) chaseTick(target, gap);
            else if (core().in(Step.TELL)) tellTick(level, target);
            else diveTick(level, target);
        } else if (core().in(Step.RECOVER)) {
            fly(new Vec3(heading.x * RECOVER_SPEED, 0.3, heading.z * RECOVER_SPEED), 0.25);
        } else if (core().in(Step.LOST)) {
            lostTick();
        } else {
            soar();
            if (tickCount % SCAN_EVERY == 0) scan(level);
        }
    }

    /** Closes on the glider from a little above; within reach it starts the screech. */
    private void chaseTick(LivingEntity target, double gap) {
        if (gap <= SCREECH_REACH && startPhase(Step.TELL, TELL_TICKS, target)) {
            station = position().subtract(target.position());
            setDeltaMovement(Vec3.ZERO);
            playSound(ModSounds.WINGSHEAR_SCREECH.get(), 2.5F, 1.0F);
            return;
        }
        Vec3 to = target.position().add(0, APPROACH_ABOVE, 0).subtract(position());
        fly(to.length() > CHASE_SPEED ? to.normalize().scale(CHASE_SPEED) : to, 1.0);
    }

    /** The screech: it keeps its place beside the glider, facing them, sparks streaming off both wingtips. */
    private void tellTick(ServerLevel level, LivingEntity target) {
        Vec3 to = target.position().add(station).subtract(position());
        Vec3 v = to.length() > CHASE_SPEED ? to.normalize().scale(CHASE_SPEED) : to;
        setDeltaMovement(v);
        face(target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(centre()));
        Vec3 side = new Vec3(heading.z, 0, -heading.x).scale(1.4);
        Vec3 back = heading.scale(-0.65);
        for (Vec3 tip : new Vec3[]{centre().add(side).add(back), centre().subtract(side).add(back)}) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, tip.x, tip.y, tip.z, 2, 0.05, 0.05, 0.05, 0.02);
            if (tickCount % 2 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, tip.x, tip.y, tip.z, 1, 0.02, 0.02, 0.02, 0.01);
        }
        if (core().elapsed() == TELL_TICKS / 2) playSound(ModSounds.WINGSHEAR_SCREECH.get(), 2.0F, 1.2F);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        LivingEntity target = core().target();
        if (ended == Step.TELL && stillHunted(target) && startPhase(Step.DIVE, DIVE_TICKS, target)) {
            dives++;
            playSound(ModSounds.WINGSHEAR_DIVE.get(), 2.0F, 1.0F);
        } else if (ended == Step.DIVE && stillHunted(target) && startPhase(Step.CHASE, 0, target)) {
            // missed: the hunt goes on, and the closest it got still counts toward losing them
        } else if (ended == Step.RECOVER && stillHunted(target) && hunt(target, forced)) {
            // its quarry is gliding again (or was handed to it): a fresh hunt
        } else if (ended == Step.LOST || ended == Step.RECOVER || ended == Step.TELL || ended == Step.DIVE) {
            settle();
        } else {
            core().end();
        }
    }

    /** The dive: straight at the glider's middle at 1.5 a tick; the first touch is the hit. */
    private void diveTick(ServerLevel level, LivingEntity target) {
        Vec3 d = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(centre());
        Vec3 move = d.lengthSqr() < 1.0E-6 ? heading.scale(DIVE_SPEED) : d.normalize().scale(DIVE_SPEED);
        setDeltaMovement(move);
        face(move);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        AABB reach = getBoundingBox().inflate(0.3).expandTowards(move);
        if (reach.intersects(target.getBoundingBox())) strike(level, target);
    }

    /** The dive lands: its attack through armour, then the glide ends and the wings stay shut for 40 ticks. */
    private void strike(ServerLevel level, LivingEntity victim) {
        if (MechanicEvent.post(this, "dive_hit", victim)) {
            DamageSource source = damageSources().mobAttack(this);
            boolean hurt = victim.hurtServer(level, source, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * DIVE_HIT);
            if (hurt) {
                hits++;
                grounded.shut(level, victim);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.WINGSHEAR_STRIKE.get(), getSoundSource(), 1.5F, 1.0F);
                level.sendParticles(ParticleTypes.CRIT, true, true, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        12, 0.3, 0.3, 0.3, 0.3);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        16, 0.4, 0.3, 0.4, 0.1);
            }
        }
        recover(victim);
    }

    /** Pulls up and away along its dive, still minding who it hunted. */
    private void recover(LivingEntity quarry) {
        if (!startPhase(Step.RECOVER, RECOVER_TICKS, quarry)) settle();
    }

    /**
     * The counter worked: the glider outran it. It lets them go with a falling cry and circles off for 5 seconds,
     * hunting nobody. Public as a test hook.
     */
    public void lose(ServerLevel level, LivingEntity from) {
        if (!startPhase(Step.LOST, LOST_TICKS, null)) {
            settle();
            return;
        }
        lostFrom = from.position();
        Vec3 away = position().subtract(from.position()).multiply(1, 0, 1);
        heading = away.lengthSqr() < 1.0E-6 ? heading : away.normalize();
        playSound(ModSounds.WINGSHEAR_LOST.get(), 2.0F, 1.0F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 20, 0.5, 0.3, 0.5, 0.05);
    }

    /** Circling off: away from where it lost them, turning half a circle over the 5 seconds, climbing a little. */
    private void lostTick() {
        double turn = Math.PI / LOST_TICKS;
        heading = new Vec3(heading.x * Math.cos(turn) - heading.z * Math.sin(turn), 0, heading.x * Math.sin(turn) + heading.z * Math.cos(turn));
        fly(new Vec3(heading.x * LOST_SPEED, 0.05, heading.z * LOST_SPEED), 0.3);
    }

    /** Back to soaring where it is now. */
    private void settle() {
        core().end();
        super.setTarget(null);
        forced = false;
        home = position();
        orbit = random.nextDouble() * Mth.TWO_PI;
    }

    /** Soaring with nothing to hunt: a slow, level circle round where it settled. */
    private void soar() {
        if (home == null) home = position();
        orbit += SOAR_SPEED / SOAR_RADIUS;
        Vec3 want = home.add(Math.cos(orbit) * SOAR_RADIUS, Math.sin(orbit * 0.5) * 0.5, Math.sin(orbit) * SOAR_RADIUS);
        Vec3 to = want.subtract(position());
        fly(to.length() > SOAR_SPEED ? to.normalize().scale(SOAR_SPEED) : to, 0.3);
    }

    /** Eases its motion toward {@code want} by {@code ease} (1 = at once) and faces the way it goes. */
    private void fly(Vec3 want, double ease) {
        Vec3 v = getDeltaMovement();
        Vec3 next = ease >= 1.0 ? want : new Vec3(Mth.lerp(ease, v.x, want.x), Mth.lerp(ease, v.y, want.y), Mth.lerp(ease, v.z, want.z));
        setDeltaMovement(next);
        face(next);
    }

    private Vec3 centre() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }

    private void face(Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-6) return;
        setXRot((float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG));
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        heading = dir.multiply(1, 0, 1).normalize();
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---- the shut wings, kept by the mob that shut them -----------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        grounded.tick(level);
        // the leading edges light up for the screech, whatever started it, and go dark when it ends
        Phase now = core().phase();
        if (now != shown) {
            shown = now;
            core().setStage(now == Step.TELL ? GLOW_SCREECH : GLOW_REST);
        }
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        grounded.clear();
        super.onRemoval(reason);
    }

    /**
     * Gliders whose wings a hit shut: each one's glide is put out on every tick it tries to glide again, until its time
     * runs out. Kept by entity id and game time, server side; a self-contained piece that a shared status effect could
     * replace (the glide check vanilla runs each tick is {@code LivingEntity.canGlide}).
     */
    public static final class GroundedWings {
        private final int ticks;
        private final Map<UUID, Long> until = new LinkedHashMap<>();

        public GroundedWings(int ticks) {
            this.ticks = ticks;
        }

        /** Ends the glide now and keeps it ended for the length this was made with. */
        public void shut(ServerLevel level, LivingEntity victim) {
            if (victim.isFallFlying()) victim.stopFallFlying();
            until.put(victim.getUUID(), level.getGameTime() + ticks);
        }

        /** Each tick: puts out any glide of a victim still shut, and forgets the ones whose time ran out. */
        public void tick(ServerLevel level) {
            long now = level.getGameTime();
            for (Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Long> e = it.next();
                if (now >= e.getValue()) {
                    it.remove();
                    continue;
                }
                Entity found = level.getEntity(e.getKey());
                if (found instanceof LivingEntity victim && victim.isFallFlying()) victim.stopFallFlying();
            }
        }

        /** Ticks left before {@code victim} may glide again, 0 if it may now. */
        public int left(ServerLevel level, LivingEntity victim) {
            Long end = until.get(victim.getUUID());
            return end == null ? 0 : (int) Math.max(0, end - level.getGameTime());
        }

        public void clear() {
            until.clear();
        }
    }

    // ---- test hooks ----------------------------------------------------------------------------------------------

    /** Ticks left before {@code victim} may glide again after its dive. Public as a test hook. */
    public int shutFor(LivingEntity victim) {
        return level() instanceof ServerLevel level ? grounded.left(level, victim) : 0;
    }

    /** Dives started and dives that hit, since it spawned. Public as test hooks. */
    public int dives() {
        return dives;
    }

    public int hits() {
        return hits;
    }

    /** The closest it got to its quarry in this hunt, a gap in blocks. Public as a test hook. */
    public double closest() {
        return closest;
    }

    /** Where it lost its last quarry, or null. Public as a test hook. */
    public @Nullable Vec3 lostFrom() {
        return lostFrom;
    }

    // ---- sounds --------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.WINGSHEAR_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.WINGSHEAR_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.WINGSHEAR_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.0F + random.nextFloat() * 0.1F;
    }
}
