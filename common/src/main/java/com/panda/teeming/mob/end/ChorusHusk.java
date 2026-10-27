package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.end.Riftjackal.SafeTeleport;
import com.panda.teeming.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Chorus Husk: a shambling husk of the End highlands, overgrown with chorus, fat violet pods bursting from its back and
 * shoulders. It fights in melee: an 8-tick wind-up (both arms rear back overhead, a creak, lilac motes), then a blow
 * for its attack damage at whatever is still in reach in front of it.
 *
 * <p>Its death is the mechanic. When it dies it doesn't fall: for 20 ticks it swells (the tell), rooted and untouchable,
 * its pods bulging and glowing lilac, a crackle rising, a faint lilac ring on the ground marking the reach. Then the pods
 * burst: everything within 4 blocks (players and mobs; never a creative or spectating player) is teleported as by
 * chorus fruit, a random 3 to 8 blocks away, onto safe ground ({@link SafeTeleport}: never over the void, into lava or
 * into a wall); one with no safe spot at that distance stays where it is. Then the husk is gone.
 *
 * <p>Counter: back out of the 4 blocks while it swells. A burst that catches no player leaves its ripest pods whole:
 * two chorus fruit drop where it stood, a fruit the player can use. Among other mobs the burst scatters them: kill it in
 * a crowd and the crowd is pulled apart. Failing it: thrown to a random spot nearby, maybe among other mobs.
 *
 * <p>A husk that falls out of the world dies at once, with no swell. The swell survives a reload (the husk comes back
 * swelling and bursts on time); the wind-up does not. Its strike cooldown is saved.
 */
public class ChorusHusk extends TeemingMonster {
    /** IDLE, the swell as it dies (the tell), the wind-up before a blow, the blow. */
    public enum Step implements Phase { IDLE, SWELL, WINDUP, STRIKE }

    /** 1 s of swelling between the killing blow and the burst. */
    public static final int SWELL_TICKS = 20;
    /** Everything this close to it when it bursts is teleported. */
    public static final double BURST_RADIUS = 4.0;
    /** A burst teleport lands this far from where its victim stood: 3 to 8 blocks, as chorus fruit carries you. */
    public static final double TELEPORT_NEAR = 3.0;
    public static final double TELEPORT_FAR = 8.0;
    /** Chorus fruit a burst that caught no player leaves behind. */
    public static final int SPARED_FRUIT = 2;

    public static final int WINDUP_TICKS = 8;             // 0.4 s with both arms reared back before the blow
    public static final int STRIKE_TICKS = 6;             // the blow lands on its first tick; the arms come down this long
    public static final int COOLDOWN = 20;                // ticks from the end of one blow to the next wind-up
    public static final float STRIKE = 1.0F;              // x attack damage
    public static final double START_REACH = 0.8;         // blocks between hitboxes: this close it winds up
    public static final double STRIKE_REACH = 1.4;        // blocks between hitboxes: a target this close when the blow lands is hit
    public static final float STRIKE_ARC = 120.0F;        // degrees in front of it the blow covers
    public static final float TURN = 15.0F;               // degrees a tick it turns after its target while winding up

    /** Glow stages: nothing lit, and the pods lit through the swell. */
    public static final int GLOW_REST = 0;
    public static final int GLOW_SWELL = 1;

    private static final DustParticleOptions LILAC = new DustParticleOptions(0xD8A4FF, 1.1F);
    private static final DustParticleOptions RING = new DustParticleOptions(0xC08AF0, 0.8F);
    private static final DustParticleOptions POD = new DustParticleOptions(0x9A5CB4, 1.3F);
    private static final DustParticleOptions STAR = new DustParticleOptions(0xF6F2FF, 0.6F);

    /** The blow that started the swell, kept for the real death that follows the burst. */
    private @Nullable DamageSource swellCause;
    /** True from a killing blow until the burst: a swell posed by a command or a test without a death bursts nothing. */
    private boolean dying;
    /** Set while the swell's own death runs, so that death goes through. */
    private boolean finishing;
    /** Ticks of swell left to resume after a reload (0: none). */
    private int resumeSwell;
    private int cooldown;
    private @Nullable Teleport fixedRoll;
    private int bursts;
    private int teleported;
    private int spared;
    private int strikes;

    public ChorusHusk(EntityType<? extends ChorusHusk> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("chorus_husk", 0.23);
    }

    @Override
    public int visualStages() {
        return 2;
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

            /** It closes in only: every blow is the wind-up and strike, started from its AI step. */
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

    /** Every phase it enters sets its glow: the pods lit through the swell, unlit otherwise. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.SWELL ? GLOW_SWELL : GLOW_REST);
        return true;
    }

    private void rest() {
        core().end();
        core().setStage(GLOW_REST);
    }

    // ---- the swell and the burst -------------------------------------------------------------------------------------

    /** True while it swells (from the killing blow until the burst). Both sides. */
    public boolean swelling() {
        return core().in(Step.SWELL);
    }

    /** True from a killing blow until the burst. Server only. Public as a test hook. */
    public boolean dying() {
        return dying;
    }

    /**
     * The killing blow starts the swell instead of a death: it keeps 1 health, rooted and untouchable, for
     * {@link #SWELL_TICKS}. The death comes after the burst. A fall out of the world, a death while it already swells,
     * or a vetoed "swell" is an ordinary death with no burst.
     */
    @Override
    public void die(DamageSource source) {
        if (!finishing && !isRemoved() && !dead && !swelling() && level() instanceof ServerLevel level
                && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && startSwell(level, source, SWELL_TICKS)) {
            return;
        }
        if (swelling()) rest();
        swellCause = null;
        dying = false;
        super.die(source);
    }

    private boolean startSwell(ServerLevel level, @Nullable DamageSource cause, int ticks) {
        if (!startPhase(Step.SWELL, ticks, null)) return false;
        swellCause = cause;
        dying = true;
        setHealth(1.0F);
        setTarget(null);
        holdStill();
        level.playSound(null, getX(), getY(1.0), getZ(), ModSounds.CHORUS_HUSK_SWELL.get(), getSoundSource(), 1.2F, 1.0F);
        Telegraph.particles(LILAC, 12, 0.5, 0.7).play(this);
        return true;
    }

    /** While it swells nothing hurts it but what reaches past invulnerability (a fall out of the world, a kill command). */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (swelling() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        return super.isInvulnerableTo(level, source);
    }

    /** Rooted through the swell: it only falls. */
    @Override
    public Pin pin() {
        return swelling() ? Pin.ROOTED : Pin.FREE;
    }

    /** The swell: lilac light pulsing out of the pods, a crackle that rises, and the burst's reach ringed on the ground. */
    private void swellTick(ServerLevel level) {
        int t = core().elapsed();
        double k = Math.min(1.0, t / (double) SWELL_TICKS);
        double y = getY() + getBbHeight() * 0.65;
        level.sendParticles(LILAC, true, true, getX(), y, getZ(), 2 + (int) (k * 4), 0.35, 0.35, 0.35, 0.0);
        if (t % 3 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, getX(), y, getZ(), 4, 0.3, 0.4, 0.3, 0.05);
        if (t % 4 == 0) {
            level.playSound(null, getX(), y, getZ(), ModSounds.CHORUS_HUSK_CRACKLE.get(), getSoundSource(), 0.8F + 0.4F * (float) k, 0.8F + 0.8F * (float) k);
        }
        if (t % 2 == 0) {
            for (int i = 0; i < 24; i++) {
                double a = i * (Math.PI * 2 / 24) + t * 0.05;
                level.sendParticles(RING, true, true, getX() + Math.cos(a) * BURST_RADIUS, getY() + 0.1, getZ() + Math.sin(a) * BURST_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * The burst: everything within {@link #BURST_RADIUS} is teleported ({@link #teleport}); if no player was inside, two
     * chorus fruit drop. Returns how many it moved. Public as a test hook (it doesn't need the husk to be dying).
     */
    public int burst(ServerLevel level) {
        double y = getY() + getBbHeight() * 0.6;
        if (!MechanicEvent.post(this, "burst", null)) {
            level.sendParticles(ParticleTypes.SMOKE, true, true, getX(), y, getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            return 0;
        }
        bursts++;
        level.sendParticles(POD, true, true, getX(), y, getZ(), 40, 0.6, 0.5, 0.6, 0.0);
        level.sendParticles(LILAC, true, true, getX(), y, getZ(), 30, 1.2, 0.6, 1.2, 0.0);
        level.sendParticles(ParticleTypes.PORTAL, true, true, getX(), y, getZ(), 60, 1.5, 0.6, 1.5, 0.6);
        level.sendParticles(STAR, true, true, getX(), y, getZ(), 12, 1.0, 0.6, 1.0, 0.0);
        level.playSound(null, getX(), y, getZ(), ModSounds.CHORUS_HUSK_BURST.get(), getSoundSource(), 1.4F, 1.0F);
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BURST_RADIUS),
                e -> e != this && takes(e) && e.distanceTo(this) <= BURST_RADIUS);
        boolean player = false;
        int moved = 0;
        for (LivingEntity e : caught) {
            if (e instanceof Player) player = true;
            if (teleport(level, e) != null) moved++;
        }
        if (!player && MechanicEvent.post(this, "spared", null)) spare(level);
        return moved;
    }

    /** The counter landed: no player was caught, so its ripest pods fall whole as chorus fruit. */
    private void spare(ServerLevel level) {
        spared++;
        spawnAtLocation(level, new ItemStack(Items.CHORUS_FRUIT, SPARED_FRUIT), (float) (getBbHeight() * 0.6));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, true, getX(), getY() + getBbHeight() * 0.6, getZ(), 8, 0.3, 0.3, 0.3, 0.0);
        level.sendParticles(STAR, true, true, getX(), getY() + getBbHeight() * 0.6, getZ(), 10, 0.3, 0.3, 0.3, 0.0);
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.CHORUS_HUSK_SPARED.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** Who a burst takes: players and mobs that portals take, never a creative or spectating player. */
    private boolean takes(LivingEntity e) {
        return e.isAlive() && (e instanceof Mob || e instanceof Player) && e.canUsePortal(true) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    /** One burst teleport: how far and which way first. */
    public record Teleport(double distance, double angle) {}

    /** A teleport's distance for a roll {@code u} in 0..1: 3 blocks at 0, 8 at 1, evenly between. */
    public static double teleportDistance(double u) {
        return TELEPORT_NEAR + (TELEPORT_FAR - TELEPORT_NEAR) * Mth.clamp(u, 0.0, 1.0);
    }

    /**
     * Teleports {@code victim} as chorus fruit would: {@link #teleportDistance} blocks in a random direction onto safe
     * ground, off any mount and shedding its riders; when that direction is unsafe the next ones round the circle are
     * tried at the same distance, and with none safe it stays. Never onto a block that chorus fruit refuses
     * ({@code #consumable_does_not_teleport_to}). Returns where it landed, or null.
     */
    private @Nullable Vec3 teleport(ServerLevel level, LivingEntity victim) {
        if (!MechanicEvent.post(this, "teleport", victim)) return null;
        Teleport roll = fixedRoll != null ? fixedRoll : new Teleport(teleportDistance(random.nextDouble()), random.nextDouble() * Math.PI * 2);
        Vec3 from = victim.position();
        Vec3 to = SafeTeleport.InLevel.find(level, victim, from, roll.distance(), roll.angle(), p -> landable(level, p));
        if (to == null) {
            level.sendParticles(RING, true, true, from.x, from.y + victim.getBbHeight() * 0.5, from.z, 6, 0.25, 0.3, 0.25, 0.0);
            return null;
        }
        victim.stopRiding();
        victim.ejectPassengers();
        puff(level, from, victim);
        SafeTeleport.InLevel.move(level, victim, to);
        level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(victim));
        puff(level, to, victim);
        level.playSound(null, from.x, from.y, from.z, ModSounds.CHORUS_HUSK_TELEPORT.get(), getSoundSource(), 1.0F, 1.0F);
        level.playSound(null, to.x, to.y, to.z, ModSounds.CHORUS_HUSK_TELEPORT.get(), getSoundSource(), 0.8F, 1.15F);
        teleported++;
        return to;
    }

    private static boolean landable(ServerLevel level, Vec3 at) {
        return !level.getBlockState(BlockPos.containing(at.x, at.y - 0.01, at.z)).is(BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO);
    }

    private static void puff(ServerLevel level, Vec3 at, Entity who) {
        double y = at.y + who.getBbHeight() * 0.5;
        level.sendParticles(ParticleTypes.PORTAL, true, true, at.x, y, at.z, 24, 0.3, 0.4, 0.3, 0.4);
        level.sendParticles(LILAC, true, true, at.x, y, at.z, 6, 0.3, 0.4, 0.3, 0.0);
    }

    /**
     * After the burst the husk's real death: the blow that started the swell gets the kill (loot, experience, the Field
     * Guide's count), and the body, burst apart, is gone on the next tick.
     */
    private void finishDeath() {
        DamageSource cause = swellCause != null ? swellCause : damageSources().generic();
        swellCause = null;
        dying = false;
        finishing = true;
        rest();
        setHealth(0.0F);
        super.die(cause);
        deathTime = 19;
    }

    // ---- the blow ------------------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Blocks between its hitbox and {@code target}'s, across or up and down, whichever is more. */
    public double gapTo(Entity target) {
        return MobCore.gap(this, target);
    }

    /** True when {@code target} stands inside the arc its blow covers. */
    public boolean inFront(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return true;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.degreesDifferenceAbs(toward, getYRot()) <= STRIKE_ARC / 2.0F;
    }

    private void face(Entity target, float maxTurn) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** While a step runs its body stays square to the way it faces. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    @Override
    public void tick() {
        super.tick();
        if (resumeSwell > 0 && level() instanceof ServerLevel level && isAlive()) {
            int left = resumeSwell;
            resumeSwell = 0;
            if (!startSwell(level, null, left)) kill(level);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (swelling()) {
            holdStill();
            swellTick(level);
            return;
        }
        if (!core().idle()) {
            holdStill();
            if (core().in(Step.WINDUP)) windupTick(level);
            return;
        }
        LivingEntity target = getTarget();
        if (cooldown == 0 && valid(target) && gapTo(target) <= START_REACH && getSensing().hasLineOfSight(target)) startWindup(target);
    }

    /** Rears back at {@code target} for {@link #WINDUP_TICKS}; the blow follows. Public as a test hook. */
    public boolean startWindup(LivingEntity target) {
        if (!core().idle() || !(level() instanceof ServerLevel level) || !startPhase(Step.WINDUP, WINDUP_TICKS, target)) return false;
        holdStill();
        face(target, 360.0F);
        level.playSound(null, getX(), getY(0.8), getZ(), ModSounds.CHORUS_HUSK_WINDUP.get(), getSoundSource(), 1.0F, 1.0F);
        Telegraph.particles(LILAC, 8, 0.4, 0.95).play(this);
        return true;
    }

    /** The wind-up: it turns after its target, motes rising off its reared-back claws. */
    private void windupTick(ServerLevel level) {
        LivingEntity target = core().target();
        if (valid(target)) face(target, TURN);
        if (core().elapsed() % 2 == 0) {
            level.sendParticles(LILAC, true, true, getX(), getY() + getBbHeight() * 1.05, getZ(), 2, 0.35, 0.1, 0.35, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.SWELL) {
            if (!dying || isDeadOrDying() || dead) {
                rest();
                return;
            }
            burst(level);
            finishDeath();
        } else if (ended == Step.WINDUP) {
            strike(level, core().target());
        } else {
            rest();
            cooldown = COOLDOWN;
        }
    }

    /** The blow: its attack damage to a target still in reach and in front; anything else is a swing at the air. */
    private void strike(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.STRIKE, STRIKE_TICKS, target)) {
            rest();
            cooldown = COOLDOWN;
            return;
        }
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.CHORUS_HUSK_STRIKE.get(), getSoundSource(), 1.0F, 1.0F);
        if (!valid(target) || gapTo(target) > STRIKE_REACH || !inFront(target) || !MechanicEvent.post(this, "strike", target)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE;
        if (target.hurtServer(level, damageSources().mobAttack(this), damage)) {
            strikes++;
            setLastHurtMob(target);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
        }
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    // ---- test hooks ----------------------------------------------------------------------------------------------------

    /** Makes every later burst teleport use {@code roll} instead of a random one (null rolls again). Public as a test hook. */
    public void fixRoll(@Nullable Teleport roll) {
        fixedRoll = roll;
    }

    /** Bursts so far. Public as a test hook. */
    public int bursts() {
        return bursts;
    }

    /** Things its bursts teleported so far. Public as a test hook. */
    public int teleported() {
        return teleported;
    }

    /** Bursts that caught no player and left fruit so far. Public as a test hook. */
    public int spared() {
        return spared;
    }

    /** Blows that landed so far. Public as a test hook. */
    public int strikes() {
        return strikes;
    }

    /** Ticks until it may wind up again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Cooldown", cooldown);
        output.putInt("SwellLeft", swelling() && dying ? Math.max(1, SWELL_TICKS - core().elapsed()) : resumeSwell);
    }

    /** The strike cooldown and a swell under way survive a reload; a wind-up does not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cooldown = Math.clamp(input.getIntOr("Cooldown", 0), 0, COOLDOWN);
        resumeSwell = Math.clamp(input.getIntOr("SwellLeft", 0), 0, SWELL_TICKS);
    }

    // ---- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.CHORUS_HUSK_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.CHORUS_HUSK_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.CHORUS_HUSK_DEATH.get();
    }
}
