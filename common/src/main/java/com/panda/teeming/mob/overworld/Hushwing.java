package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hushwing: a blind cave bat that hunts by sound, in flocks of 3 to 4. It cannot see; it notices only noise, by kind
 * and range: a player sprinting within 16 blocks, fighting (hurting something or being hurt) within 12, or mining
 * (breaking a block) within 10. Walking and sneaking make no noise it hears. It has no warning before a bite; what
 * shows is its hearing: when it hears something its ears snap up and flick for half a second, a ring of sound rises
 * over it and a vibration runs from the noise to it. Then it flies to where it heard the noise and bites whoever it
 * finds there. It chases the last noise for 3 s; someone who goes quiet in that time is lost.
 *
 * <p>Counter: move quietly, and throw something. A projectile landing within 16 blocks (an arrow, a snowball, an egg)
 * is a decoy: it flies to the impact and searches there for 5 s, deaf to every other noise unless it is hit itself.
 * Failure: the flock finds you, and each bite is a fight that every Hushwing within 12 blocks hears.
 *
 * <p>Hearing uses vanilla's game events (the ones the Warden and sculk sensors listen to) for impacts, broken blocks
 * and damage, and a scan of nearby players for sprinting.
 */
public class Hushwing extends TeemingMonster {
    /** IDLE, the ear flick when it hears something (no wait: it is already flying), the chase, a bite, the decoy search. */
    public enum Step implements Phase { IDLE, HEAR, HUNT, BITE, DECOY }

    /** How far it hears each noise, in blocks, measured to whoever made it. */
    public static final double SPRINT_RANGE = 16.0;
    public static final double COMBAT_RANGE = 12.0;
    public static final double MINING_RANGE = 10.0;
    /** Walking and sneaking: no noise at all. */
    public static final double WALK_RANGE = 0.0;
    /** A projectile's impact is heard as far as a sprint, and holds it for 5 s. */
    public static final double DECOY_RANGE = 16.0;
    public static final int DECOY_TICKS = 100;
    /** The ear flick: half a second. */
    public static final int HEAR_TICKS = 10;
    /** It chases the last noise this long after hearing it, then gives up. */
    public static final int MEMORY_TICKS = 60;
    public static final float BITE_HIT = 1.0F;             // x attack damage
    public static final int BITE_TICKS = 6;
    public static final int BITE_COOLDOWN = 20;            // ticks from one bite to the next, per Hushwing
    /** Its top speed while it chases a noise, in blocks a tick: faster than a sprint (0.28), so only quiet escapes. */
    public static final double CHASE_SPEED = 0.35;
    /** A bite reaches this far past its hitbox, in blocks. */
    public static final double BITE_REACH = 0.4;

    /** Each flock member closes in from its own side: this far off the noise, in blocks. */
    private static final double SPREAD = 0.6;
    /**
     * For the last ticks of its bite cooldown it stops circling and dives back at the spot, so a victim still standing
     * there is in reach the tick the cooldown ends: its searching loop runs at the edge of its reach.
     */
    private static final int DIVE_TICKS = 5;
    private static final ShriekParticleOption RING = new ShriekParticleOption(0);

    private enum Kind { SPRINT, COMBAT, MINING, TOLD, DECOY }

    /** One noise heard this tick: where to fly, who made it (none for an impact), and whether it was a hit on itself. */
    private record Noise(Vec3 at, @Nullable LivingEntity maker, Kind kind, boolean struck) {}

    private final DynamicGameEventListener<Ears> ears;
    private final List<Noise> heard = new ArrayList<>();
    private final Set<LivingEntity> hurtNearby = new LinkedHashSet<>();
    private @Nullable Vec3 noiseAt;
    private @Nullable LivingEntity maker;
    private long heardTime;
    private long decoyEnd;
    private int biteCooldown;
    private @Nullable Predicate<Entity> hearOnly;

    public Hushwing(EntityType<? extends Hushwing> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 3;
        ears = new DynamicGameEventListener<>(new Ears(new EntityPositionSource(this, 0.3F)));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("hushwing", 0.3).add(Attributes.FLYING_SPEED, 0.25);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // flits about while it hears nothing; its own phases fly it otherwise
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    // ---- hearing -----------------------------------------------------------------------------------------------------

    /** What it listens to through vanilla's game events: impacts, broken blocks and damage. */
    private final class Ears implements GameEventListener {
        private final PositionSource source;

        Ears(PositionSource source) {
            this.source = source;
        }

        @Override
        public PositionSource getListenerSource() {
            return source;
        }

        @Override
        public int getListenerRadius() {
            return (int) Math.ceil(DECOY_RANGE);
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 pos) {
            if (!isAlive() || isNoAi()) return false;
            Entity from = context.sourceEntity();
            if (event.is(GameEvent.PROJECTILE_LAND)) {
                // an impact on itself is a hit, not a decoy
                if (from == null || !hears(from) || pos.distanceTo(position()) > DECOY_RANGE || getBoundingBox().inflate(1.0).contains(pos)) return false;
                heard.add(new Noise(above(level, pos), null, Kind.DECOY, false));
                return true;
            }
            if (event.is(GameEvent.BLOCK_DESTROY)) {
                if (!(from instanceof Player miner) || !prey(miner) || !hears(miner) || distanceTo(miner) > MINING_RANGE) return false;
                heard.add(new Noise(chest(miner), miner, Kind.MINING, false));
                return true;
            }
            if (event.is(GameEvent.ENTITY_DAMAGE) && from instanceof LivingEntity victim) {
                // who hit it is only known once the hurt has finished: it is read on the next AI step
                hurtNearby.add(victim);
                return true;
            }
            return false;
        }
    }

    /**
     * A block hit is heard at the middle of the block it struck: it searches just over that block instead, the open
     * side a thrown snowball came from.
     */
    private static Vec3 above(ServerLevel level, Vec3 pos) {
        BlockPos at = BlockPos.containing(pos);
        if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()) return pos;
        return new Vec3(pos.x, at.getY() + 1.3, pos.z);
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> action) {
        if (ears != null && level() instanceof ServerLevel level) action.accept(ears, level);   // null while the Entity constructor runs
    }

    /** The fights heard since the last step: a victim being hurt, and whoever hurt it. */
    private void hearFights() {
        for (LivingEntity victim : hurtNearby) {
            if (victim.isRemoved() || victim.level() != level()) continue;
            boolean struck = victim == this;
            // being hurt is a noise: a player, or the creature it is already chasing
            if (!struck && (prey(victim) || victim == maker && valid(victim)) && hears(victim) && distanceTo(victim) <= COMBAT_RANGE) {
                heard.add(new Noise(chest(victim), victim, Kind.COMBAT, false));
            }
            DamageSource source = victim.getLastDamageSource();
            if (source != null && source.getEntity() instanceof LivingEntity attacker && attacker != victim
                    && (prey(attacker) || struck && valid(attacker)) && hears(attacker) && distanceTo(attacker) <= COMBAT_RANGE) {
                heard.add(new Noise(chest(attacker), attacker, Kind.COMBAT, struck));
            }
        }
        hurtNearby.clear();
    }

    /** Sprinting players nearby; walking and sneaking ones make no noise. */
    private void hearSteps(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (!prey(p) || !hears(p)) continue;
            double range = p.isSprinting() ? SPRINT_RANGE : WALK_RANGE;
            if (range > 0 && distanceTo(p) <= range) heard.add(new Noise(chest(p), p, Kind.SPRINT, false));
        }
    }

    /** Picks what it acts on: the newest impact, else (unless an impact holds it) the noise of whoever it chases, else the nearest. */
    private void listen(ServerLevel level) {
        hearFights();
        hearSteps(level);
        if (heard.isEmpty()) return;
        long now = level.getGameTime();
        Noise decoy = null, hit = null, same = null, nearest = null;
        for (Noise n : heard) {
            if (n.kind() == Kind.DECOY) {
                decoy = n;
                continue;
            }
            if (n.struck()) hit = n;
            if (n.maker() == maker) same = n;
            if (nearest == null || n.at().distanceToSqr(position()) < nearest.at().distanceToSqr(position())) nearest = n;
        }
        heard.clear();
        if (decoy != null && hit == null) {
            decoy(level, decoy);
            return;
        }
        if (decoyEnd > now && hit == null) return;        // drawn to an impact: deaf to everything else
        Noise pick = hit != null ? hit : same != null ? same : nearest;
        if (pick != null) hear(level, pick);
    }

    /** Hears a noise: the chase now aims at it. Coming from quiet, the ears flick and the sound ring shows. */
    private void hear(ServerLevel level, Noise noise) {
        decoyEnd = 0;
        noiseAt = noise.at();
        maker = noise.maker();
        heardTime = level.getGameTime();
        super.setTarget(maker);
        if ((core().idle() || core().in(Step.DECOY)) && startPhase(Step.HEAR, HEAR_TICKS, maker)) cue(level, noise.at(), ModSounds.HUSHWING_HEAR.get());
    }

    /** The counter landed: an impact draws it to search there for 5 s, deaf to anything else. */
    private void decoy(ServerLevel level, Noise noise) {
        if (!startPhase(Step.DECOY, DECOY_TICKS, null)) return;
        noiseAt = noise.at();
        maker = null;
        super.setTarget(null);
        decoyEnd = level.getGameTime() + DECOY_TICKS;
        cue(level, noise.at(), ModSounds.HUSHWING_DECOY.get());
    }

    /** What a player sees and hears when it hears something: its call, a sound ring over it and a vibration from the noise. */
    private void cue(ServerLevel level, Vec3 from, SoundEvent sound) {
        playSound(sound, 1.0F, 0.95F + random.nextFloat() * 0.1F);
        level.sendParticles(RING, false, true, getX(), getY() + getBbHeight() + 0.15, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        int travel = Mth.clamp((int) Math.round(from.distanceTo(position()) * 1.5), 4, 24);
        level.sendParticles(new VibrationParticleOption(new EntityPositionSource(this, getBbHeight() * 0.5F), travel), false, true,
                from.x, from.y, from.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Forgets the noise and goes back to flitting about. */
    private void lose() {
        noiseAt = null;
        maker = null;
        decoyEnd = 0;
        super.setTarget(null);
        core().end();
    }

    /** A target handed to it (by a command, a pack, another mob) counts as a noise heard from it now. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target != null && target != maker && !level().isClientSide() && valid(target) && hears(target)) {
            heard.add(new Noise(chest(target), target, Kind.TOLD, false));
        }
    }

    // ---- the chase ---------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        if (biteCooldown > 0) biteCooldown--;
        listen(level);
        long now = level.getGameTime();
        if (core().idle()) return;
        getNavigation().stop();
        if (noiseAt == null) {
            if (!core().in(Step.BITE)) lose();
            return;
        }
        if (core().in(Step.BITE)) {
            setDeltaMovement(getDeltaMovement().scale(0.7));       // pulls back off the bite
            return;
        }
        if (!core().in(Step.DECOY) && now - heardTime > MEMORY_TICKS) {
            lose();                                                // the noise went cold: whoever made it went quiet
            return;
        }
        LivingEntity victim = reachable();
        if (victim != null && biteCooldown == 0) {
            bite(level, victim);
            return;
        }
        fly(goal(now));
    }

    /**
     * Where it steers: the noise, from its own side of it; once there, small searching loops round the spot, except
     * that it dives back at the spot as its bite cooldown runs out.
     */
    private Vec3 goal(long now) {
        double side = (getId() * 2.399) % (Math.PI * 2);           // each flock member its own angle
        Vec3 at = noiseAt;
        boolean diving = biteCooldown > 0 && biteCooldown <= DIVE_TICKS;
        if (diving || at.distanceTo(centre()) > 1.5) return at.add(Math.cos(side) * SPREAD, 0.0, Math.sin(side) * SPREAD);
        double a = side + now * 0.25;
        return at.add(Math.cos(a) * 1.0, 0.3 + Math.sin(now * 0.31 + side) * 0.3, Math.sin(a) * 1.0);
    }

    private void fly(Vec3 to) {
        Vec3 d = to.subtract(centre());
        double length = d.length();
        Vec3 want = length < 1.0E-3 ? Vec3.ZERO : d.scale(Math.min(CHASE_SPEED, length * 0.5) / length);
        setDeltaMovement(getDeltaMovement().lerp(want, 0.4));
        face(getDeltaMovement());
    }

    /** Who a bite would reach now: whoever it chases, else any player it bumps into. */
    private @Nullable LivingEntity reachable() {
        var reach = getBoundingBox().inflate(BITE_REACH);
        if (maker != null && valid(maker) && reach.intersects(maker.getBoundingBox())) return maker;
        LivingEntity best = null;
        for (Player p : level().getEntitiesOfClass(Player.class, reach, this::prey)) {
            if (best == null || distanceToSqr(p) < distanceToSqr(best)) best = p;
        }
        return best;
    }

    /** A bite: its attack damage (no warning first). Being bitten is a fight, which every Hushwing near enough hears. */
    private void bite(ServerLevel level, LivingEntity victim) {
        if (!startPhase(Step.BITE, BITE_TICKS, victim)) return;
        biteCooldown = BITE_COOLDOWN;
        face(victim.position().subtract(position()));
        victim.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE_HIT);
        playSound(ModSounds.HUSHWING_BITE.get(), 1.0F, 0.95F + random.nextFloat() * 0.1F);
        level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
        Vec3 back = position().subtract(victim.position()).multiply(1, 0, 1);
        if (back.lengthSqr() > 1.0E-4) setDeltaMovement(back.normalize().scale(0.25).add(0, 0.1, 0));
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        long now = level().getGameTime();
        if (ended == Step.DECOY) {
            lose();                                                // the impact went quiet: it starts listening afresh
        } else if (noiseAt == null) {
            lose();
        } else if (decoyEnd > now) {
            if (!startPhase(Step.DECOY, (int) (decoyEnd - now), null)) lose();
        } else if (!startPhase(Step.HUNT, 0, maker)) {
            lose();
        }
    }

    /** Hit by anything living near enough, it hears its attacker, even through a decoy. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) hurtNearby.add(this);
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (isNoAi()) {                                            // nothing reads them while its AI is off
            heard.clear();
            hurtNearby.clear();
        }
    }

    /** Players it can hunt: alive, in survival or adventure. */
    private boolean prey(LivingEntity e) {
        return e instanceof Player p && valid(p);
    }

    private boolean valid(LivingEntity e) {
        return e.isAlive() && !e.isRemoved() && e.level() == level() && e != this && !(e instanceof Hushwing)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private boolean hears(Entity maker) {
        return hearOnly == null || hearOnly.test(maker);
    }

    private static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.6, 0);
    }

    private Vec3 centre() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- test hooks ---------------------------------------------------------------------------------------------------

    /**
     * Public as a test hook: from now on it hears only noises made by entities that pass {@code filter} (a projectile
     * for an impact), so a test can't pick up a noise from a neighbouring test; null hears everything again.
     */
    public void hearOnly(@Nullable Predicate<Entity> filter) {
        hearOnly = filter;
    }

    /** Where it is flying to, or null when it heard nothing. Public as a test hook. */
    public @Nullable Vec3 noiseAt() {
        return noiseAt;
    }

    /** True while an impact holds it. Public as a test hook. */
    public boolean decoyed() {
        return decoyEnd > level().getGameTime();
    }

    /** Ticks until it may bite again. Public as a test hook. */
    public int biteCooldown() {
        return biteCooldown;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.HUSHWING_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HUSHWING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HUSHWING_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + random.nextFloat() * 0.15F;
    }
}
