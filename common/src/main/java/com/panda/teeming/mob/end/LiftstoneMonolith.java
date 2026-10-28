package com.panda.teeming.mob.end;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Liftstone Monolith: the End's apex, a towering slab of end stone and obsidian with a capstone floating over it. It
 * drifts slowly across the main island after its target. A strong field mob: no boss bar and no arena, only one in a
 * wide stretch of the island.
 *
 * <p>Tell: once its target is within 6 blocks its capstone rises for 1 s, spinning, while the four runes down its faces
 * light one after another from the bottom up, a deep hum climbs, end-rod motes rise round it and a ring of portal
 * light traces its reach on the ground. Then everything within 6 blocks (players and mobs; not players in creative or
 * spectator) is lifted straight up 6 blocks over 1 s, held there half a second, and slammed straight back down onto the
 * ground it stood on: 12 on landing. The slam is the whole blow: a victim is held in place from the lift to the landing
 * and its fall is forgotten every tick until half a second after it, so no vanilla fall damage comes on top. No one
 * is moved sideways, so the pull never puts anyone over the void. It pulls again 8 s after it last rose, while its
 * target stays in reach.
 *
 * <p>Counter: a roof. A solid block within 3 blocks over a victim's head stops that victim's lift, and they stay on the
 * ground untouched (a lifted victim whose rise meets a block higher up stops under it, and still falls). Running out
 * of the ring during the tell also works, but only a roof pays: a pull that catches on a roof jars the capstone, which
 * splits in two after the slam and shows the gravity heart inside, weak-point gold, for 4 s: its armour is gone (a broken
 * part, not a stun: it keeps drifting). Then the halves close again.
 */
public class LiftstoneMonolith extends TeemingMonster {
    /** IDLE, the rising capstone (tell), the lift, the hold, the slam, and the split capstone (the payoff). */
    public enum Step implements Phase { IDLE, TELL, LIFT, HOLD, SLAM, CRACKED }

    /** 1 s of the capstone rising before the pull. */
    public static final int TELL_TICKS = 20;
    /** The pull reaches 6 blocks from its middle, along the ground. */
    public static final double LIFT_RADIUS = 6.0;
    /** And reaches only feet within this height of its own: the ground round it, a step up or down. */
    public static final double LIFT_BAND = 3.0;
    /** Each victim rises 6 blocks, evenly, over 1 s. */
    public static final double LIFT_HEIGHT = 6.0;
    public static final int LIFT_TICKS = 20;
    /** Held at the top for half a second before the slam. */
    public static final int HOLD_TICKS = 10;
    /** The slam drives each victim straight down 1.5 blocks a tick: from 6 up, it lands on the slam's fourth tick. */
    public static final double SLAM_SPEED = 1.5;
    public static final int SLAM_TICKS = 4;
    /** What the landing does, its whole blow (the same as its attack). */
    public static final float SLAM_DAMAGE = 12.0F;
    /** A solid block within this many blocks over a victim's head stops its lift. */
    public static final double ROOF_REACH = 3.0;
    /** From one rise to the next: 8 s. */
    public static final int CYCLE_TICKS = 160;
    /** The split capstone: 4 s with the heart open and no armour. */
    public static final int CRACKED_TICKS = 80;
    public static final int CRACKED_ARMOR = 0;
    /** A victim's fall is still forgotten this long after it lands. */
    public static final int LANDING_GRACE = 10;
    /** Victims it lets go of in mid-air (it died, or was unloaded) fall under Slow Falling for this long: 3 s. */
    public static final int CUSHION_TICKS = 60;
    /** A held victim found this far from its column got away (an ender pearl, a teleport). */
    public static final double ESCAPE = 2.0;
    /** It drifts after its target until it is this close. */
    public static final double APPROACH = 3.0;
    /** Local cap: one within 128 blocks. */
    public static final int CAP_RADIUS = 128;
    public static final int XP = 100;

    /** Glow stages: every rune dark (the heart is open: only the gold shows), faint runes at rest, then the tell's three. */
    public static final int STAGE_DARK = 0, STAGE_REST = 1, STAGE_FULL = 4;

    /** The gravity heart, from its feet: inside the capstone, which sags a little as it splits. */
    private static final double HEART_UP = 3.35;
    private static final int RING_POINTS = 40;
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD675, 1.2F);
    private static final BlockParticleOption END_STONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.END_STONE.defaultBlockState());

    private final Hoist hoist = new Hoist(LANDING_GRACE);
    private int untilRise;
    /** Victims whose lift a roof stopped in the current pull. */
    private int caught;
    private boolean slamHeard;

    public LiftstoneMonolith(EntityType<? extends LiftstoneMonolith> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
        core().setStage(STAGE_REST);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("liftstone_monolith", 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new DriftGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override
            public boolean canUse() {
                return drifts() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return drifts() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** It drifts when idle and while its capstone is split; it stands still from the rise to the slam. */
    private boolean drifts() {
        return core().idle() || core().in(Step.CRACKED);
    }

    /** From the rise to the slam: it stands its ground. */
    private boolean pulling() {
        return core().in(Step.TELL) || core().in(Step.LIFT) || core().in(Step.HOLD) || core().in(Step.SLAM);
    }

    @Override
    public Pin pin() {
        return pulling() ? Pin.ROOTED : Pin.FREE;
    }

    @Override
    public int visualStages() {
        return 5;
    }

    /** Its weak point, the gravity heart, glows weak-point gold while the capstone is split. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.CRACKED);
    }

    /** Every phase sets its glow stage as it starts; the tell then climbs through its stages as it runs. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.CRACKED ? STAGE_DARK : phase == Step.IDLE ? STAGE_REST : phase == Step.TELL ? STAGE_REST + 1 : STAGE_FULL);
        return true;
    }

    // ---- the rise ----------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (untilRise > 0) untilRise--;
        if (pulling()) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
            return;
        }
        if (!core().idle() || untilRise > 0) return;
        LivingEntity target = getTarget();
        if (canRise(target)) rise(target);
    }

    private boolean canRise(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && onGround() && !isInLiquid() && inReach(target.position());
    }

    /** Starts the tell, if it is free to; true if it did. Ignores range and the wait. Public as a test hook. */
    public boolean rise(@Nullable LivingEntity target) {
        if (!core().idle() || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        untilRise = CYCLE_TICKS;
        playSound(ModSounds.LIFTSTONE_MONOLITH_TELL.get(), 2.0F, 1.0F);
        return true;
    }

    /** True if feet at {@code feet} are inside the pull: within 6 blocks of its middle, and within 3 of its height. */
    public boolean inReach(Vec3 feet) {
        double dx = feet.x - getX(), dz = feet.z - getZ();
        return dx * dx + dz * dz <= LIFT_RADIUS * LIFT_RADIUS && Math.abs(feet.y - getY()) <= LIFT_BAND;
    }

    /**
     * What the pull takes: players not in creative or spectator, and mobs; not decorations (armour stands, mannequins),
     * not bosses, not shulkers fixed to their block, not another Monolith, and not what another mob already holds.
     */
    public boolean canLift(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isRemoved() || e.isSpectator()) return false;
        if (e instanceof Player p) return !p.isCreative();
        if (!(e instanceof Mob)) return false;
        return !(e instanceof LiftstoneMonolith || e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Shulker) && !Grab.isHeld(e);
    }

    /** Runs on both the AI's and a frozen statue's ticks, so the tell, the pull and the open heart always show. */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        hoist.forgive(level.getGameTime());
        if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().in(Step.LIFT)) {
            hoist.raise(level, Math.min(1.0, (core().elapsed() + 1) / (double) LIFT_TICKS));
            if (core().elapsed() % 2 == 0) pullTrails(level);
        } else if (core().in(Step.HOLD)) {
            hoist.keep(level);
            if (core().elapsed() % 3 == 0) pullTrails(level);
        } else if (core().in(Step.SLAM)) {
            List<LivingEntity> landed = hoist.drop(level, SLAM_SPEED);
            for (LivingEntity v : landed) slamHit(level, v);
            if (!landed.isEmpty()) slamSound(level);
        } else if (core().in(Step.CRACKED) && core().elapsed() % 3 == 0) {
            Vec3 h = heartPos();                                                // gold motes drift from the open heart
            level.sendParticles(GOLD, true, true, h.x, h.y, h.z, 2, 0.25, 0.25, 0.25, 0.0);
        }
    }

    /** The tell: the runes light stage by stage, motes rise, the reach glows on the ground, and the hum climbs. */
    private void tellTick(ServerLevel level) {
        int t = core().elapsed();
        float progress = core().length() > 0 ? Math.min(1.0F, (float) t / core().length()) : 0.0F;
        core().setStage(Math.min(STAGE_FULL, STAGE_REST + 1 + (int) (progress * 3.0F)));
        if (t % 2 == 0) {
            for (int i = 0; i < 3; i++) {                                       // end-rod motes rising round the slab
                double a = getRandom().nextDouble() * Math.PI * 2, r = 1.0 + getRandom().nextDouble() * 0.6;
                level.sendParticles(ParticleTypes.END_ROD, true, true, getX() + Math.cos(a) * r, getY() + getRandom().nextDouble() * 3.0,
                        getZ() + Math.sin(a) * r, 0, 0.0, 1.0, 0.0, 0.08);
            }
        }
        if (t % 4 == 0) ring(level, ParticleTypes.REVERSE_PORTAL, 0.02);        // how far to run, or where a roof must be
        if (t % 5 == 0) playSound(ModSounds.LIFTSTONE_MONOLITH_HUM.get(), 1.6F, 0.6F + 0.6F * progress);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) return;
        if (ended == Step.TELL) {
            if (startPhase(Step.LIFT, LIFT_TICKS, core().target())) {
                takeHold(level);
            } else {
                settle();
            }
        } else if (ended == Step.LIFT) {
            if (startPhase(Step.HOLD, HOLD_TICKS, core().target())) {
                playSound(ModSounds.LIFTSTONE_MONOLITH_HOLD.get(), 1.6F, 1.0F);
            } else {
                hoist.setDown(level);                                           // a vetoed hold: set down, unhurt
                finish(level);
            }
        } else if (ended == Step.HOLD) {
            if (startPhase(Step.SLAM, SLAM_TICKS, core().target())) {
                hoist.startDrop();
                slamHeard = false;
                shockwave(level);
            } else {
                hoist.setDown(level);                                           // a vetoed slam: set down, unhurt
                finish(level);
            }
        } else if (ended == Step.SLAM) {
            List<LivingEntity> late = hoist.setDown(level);                    // anyone with farther to fall lands now
            for (LivingEntity v : late) slamHit(level, v);
            if (!slamHeard) slamSound(level);
            finish(level);
        } else if (ended == Step.CRACKED) {
            seal(level);
        } else {
            settle();
        }
    }

    /**
     * The pull takes hold: everything in reach that a roof doesn't cover is held at its feet, ready to rise (half as
     * high with a Grounding Charm in the off-hand; the slam's damage stays); each one a roof covers is left on the
     * ground, and the pull catches on that roof.
     */
    private void takeHold(ServerLevel level) {
        hoist.clear();
        caught = 0;
        AABB reach = new AABB(getX() - LIFT_RADIUS, getY() - LIFT_BAND, getZ() - LIFT_RADIUS,
                getX() + LIFT_RADIUS, getY() + LIFT_BAND + 0.01, getZ() + LIFT_RADIUS);
        for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, reach, this::canLift)) {
            if (!inReach(v.position())) continue;
            if (Hoist.roofed(v, ROOF_REACH)) {
                caught++;
                caughtOn(level, v);
                continue;
            }
            if (MechanicEvent.post(this, "lift_take", v)) hoist.take(level, v, LIFT_HEIGHT * Charms.grounded(v));
        }
        playSound(ModSounds.LIFTSTONE_MONOLITH_LIFT.get(), 2.0F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY() + 3.6, getZ(), 16, 0.6, 0.4, 0.6, 0.05);
    }

    /** The pull meets the roof over {@code v}: sparks and portal light under the roof, and a dull clunk. */
    private void caughtOn(ServerLevel level, LivingEntity v) {
        double top = v.getBoundingBox().maxY + 0.3;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, v.getX(), top, v.getZ(), 24, 0.4, 0.2, 0.4, 0.05);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, v.getX(), top, v.getZ(), 10, 0.4, 0.1, 0.4, 0.1);
        level.playSound(null, v.getX(), top, v.getZ(), ModSounds.LIFTSTONE_MONOLITH_CAUGHT.get(), getSoundSource(), 1.4F, 1.0F);
    }

    /**
     * The landing blow on {@code v}: 12, from the Monolith but from no point (so a raised shield, which only blocks
     * blows from a side, can't catch a slam into the ground); armour counts. The landing pins it: no knockback, and its
     * fall forgotten. Returns whether it was hurt. Public as a test hook.
     */
    public boolean slamHit(ServerLevel level, LivingEntity v) {
        if (!MechanicEvent.post(this, "slam_hit", v)) return false;
        v.damageCooldownTime = 0;                                               // a recent scratch doesn't eat the slam
        boolean hurt = v.hurtServer(level, slamSource(), SLAM_DAMAGE);
        Push.set(v, Vec3.ZERO);
        v.resetFallDistance();
        BlockState under = level.getBlockState(BlockPos.containing(v.getX(), v.getY() - 0.2, v.getZ()));
        ParticleOptions crumbs = new BlockParticleOption(ParticleTypes.BLOCK, under.isAir() ? Blocks.END_STONE.defaultBlockState() : under);
        level.sendParticles(crumbs, true, true, v.getX(), v.getY() + 0.1, v.getZ(), 30, 0.5, 0.1, 0.5, 0.2);
        level.sendParticles(ParticleTypes.POOF, true, true, v.getX(), v.getY() + 0.1, v.getZ(), 8, 0.4, 0.05, 0.4, 0.05);
        return hurt;
    }

    /** The slam's blow: the Monolith's own attack type, with it as the cause and no direct source or position. */
    public DamageSource slamSource() {
        return new DamageSource(damageSources().mobAttack(this).typeHolder(), null, this);
    }

    private void slamSound(ServerLevel level) {
        slamHeard = true;
        playSound(ModSounds.LIFTSTONE_MONOLITH_SLAM.get(), 2.5F, 1.0F);
    }

    /** After the slam: a pull that caught on a roof splits the capstone; otherwise it settles back to rest. */
    private void finish(ServerLevel level) {
        hoist.clear();
        if (caught > 0 && startPhase(Step.CRACKED, CRACKED_TICKS, core().target())) {
            Vec3 h = heartPos();
            level.sendParticles(END_STONE, true, true, h.x, h.y + 0.3, h.z, 40, 0.7, 0.3, 0.7, 0.15);
            level.sendParticles(GOLD, true, true, h.x, h.y, h.z, 16, 0.4, 0.3, 0.4, 0.0);
            level.playSound(null, h.x, h.y, h.z, ModSounds.LIFTSTONE_MONOLITH_CRACK.get(), getSoundSource(), 2.0F, 1.0F);
        } else {
            settle();
        }
        caught = 0;
    }

    /** Back to rest: idle, faint runes. The wait for the next rise runs from the last rise, not from here. */
    private void settle() {
        hoist.clear();
        core().end();
        core().setStage(STAGE_REST);
    }

    /** The halves of the capstone close over the heart again. */
    private void seal(ServerLevel level) {
        settle();
        Vec3 h = heartPos();
        level.sendParticles(ParticleTypes.SMOKE, true, true, h.x, h.y, h.z, 10, 0.4, 0.3, 0.4, 0.01);
        level.playSound(null, h.x, h.y, h.z, ModSounds.LIFTSTONE_MONOLITH_SEAL.get(), getSoundSource(), 1.5F, 1.0F);
    }

    /** Portal light streaming up from the ground under each held victim. */
    private void pullTrails(ServerLevel level) {
        for (LivingEntity v : hoist.victims()) {
            Vec3 floor = hoist.groundOf(v);
            if (floor == null) continue;
            double up = v.getY() - floor.y;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, floor.x, floor.y + up * 0.5, floor.z, 6, 0.3, Math.max(0.1, up * 0.3), 0.3, 0.02);
        }
        level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY() + 3.9, getZ(), 2, 0.7, 0.2, 0.7, 0.02);
    }

    /** The slam begins: a ring of air rushing out along the ground to the reach, and crumbs thrown up round it. */
    private void shockwave(ServerLevel level) {
        for (double r = 1.5; r <= LIFT_RADIUS + 0.01; r += 1.5) {
            for (int i = 0; i < RING_POINTS; i++) {
                double a = i * (Math.PI * 2 / RING_POINTS);
                level.sendParticles(ParticleTypes.POOF, true, true, getX() + Math.cos(a) * r, getY() + 0.15, getZ() + Math.sin(a) * r,
                        0, Math.cos(a), 0.05, Math.sin(a), 0.25);
            }
        }
        level.sendParticles(END_STONE, true, true, getX(), getY() + 0.2, getZ(), 40, 1.5, 0.1, 1.5, 0.2);
    }

    /** Particles round the ground at the pull's reach, rising at {@code lift}. */
    private void ring(ServerLevel level, ParticleOptions particle, double lift) {
        for (int i = 0; i < RING_POINTS; i++) {
            double a = i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(particle, true, true, getX() + Math.cos(a) * LIFT_RADIUS, getY() + 0.2, getZ() + Math.sin(a) * LIFT_RADIUS,
                    1, 0.0, 0.05, 0.0, lift);
        }
    }

    /** Where the gravity heart sits: inside the capstone, over its middle. */
    public Vec3 heartPos() {
        return position().add(0.0, HEART_UP, 0.0);
    }

    // ---- the split capstone ------------------------------------------------------------------------------------------

    /** Split, it has no armour. */
    @Override
    public int getArmorValue() {
        return core().in(Step.CRACKED) ? CRACKED_ARMOR : super.getArmorValue();
    }

    /** Split, a hit that lands rings off the heart in a burst of gold. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && core().in(Step.CRACKED)) {
            Vec3 h = heartPos();
            level.sendParticles(GOLD, true, true, h.x, h.y, h.z, 10, 0.3, 0.3, 0.3, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, h.x, h.y, h.z, 4, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, h.x, h.y, h.z, ModSounds.LIFTSTONE_MONOLITH_HEART_HIT.get(), getSoundSource(), 1.2F, 1.0F);
        }
        return hurt;
    }

    // ---- letting go --------------------------------------------------------------------------------------------------

    /** Dying mid-pull, it lets go of everyone it holds, and they drift down under Slow Falling. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        hoist.letGo(this, CUSHION_TICKS);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        if (!level().isClientSide()) hoist.letGo(this, CUSHION_TICKS);
        super.onRemoval(reason);
    }

    /** It never takes fall damage: a stone that floats its own capstone. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- the local cap -----------------------------------------------------------------------------------------------

    /**
     * Its local cap as a spawner rule: true when no other Liftstone Monolith stands within 128 blocks of {@code pos}.
     * A spawner placing one should ask this first (the shared apex cap in the natural-spawn predicate counts the same).
     */
    public static boolean roomFor(EntityGetter level, BlockPos pos) {
        return level.getEntitiesOfClass(LiftstoneMonolith.class, new AABB(pos).inflate(CAP_RADIUS)).isEmpty();
    }

    // ---- state and test hooks ----------------------------------------------------------------------------------------

    /** Ticks until it may rise again. */
    public int untilRise() {
        return untilRise;
    }

    /** Lets it rise again at once. Public as a test hook. */
    public void resetCooldown() {
        untilRise = 0;
    }

    /** Who it holds in the air now. */
    public List<LivingEntity> held() {
        return hoist.victims();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("UntilRise", untilRise);
    }

    /** A reload ends a pull or a split capstone: it comes back whole and at rest, its wait kept. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        untilRise = Math.max(0, input.getIntOr("UntilRise", 0));
        core().setStage(STAGE_REST);
    }

    /** It drifts after its target until it is within 3 blocks of it, when idle and while split. */
    private final class DriftGoal extends Goal {
        private int repath;

        DriftGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean wants() {
            LivingEntity target = getTarget();
            return drifts() && target != null && target.isAlive() && distanceToSqr(target) > APPROACH * APPROACH;
        }

        @Override
        public boolean canUse() {
            return wants();
        }

        @Override
        public boolean canContinueToUse() {
            return wants();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 10.0F, 10.0F);
            if (--repath <= 0) {
                repath = 10;
                getNavigation().moveTo(target, 1.0);
            }
        }
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.LIFTSTONE_MONOLITH_DRIFT.get(), 0.6F, 0.9F + random.nextFloat() * 0.1F);
        if (level() instanceof ServerLevel level) {                              // grit scraped from the ground it slides on
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), getX(), getY() + 0.1, getZ(), 4, 0.7, 0.05, 0.7, 0.05);
        }
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.LIFTSTONE_MONOLITH_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.LIFTSTONE_MONOLITH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.LIFTSTONE_MONOLITH_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + random.nextFloat() * 0.1F;
    }

    // ---- Hoist: lifting a crowd and slamming it down -----------------------------------------------------------------

    /**
     * Lifting several victims straight up together, holding them, and driving them back down onto the ground under
     * them, with no fall damage of the game's own on top. Self-contained: it owns its victims and the rules of a roof,
     * the headroom and the landing; the mob decides who to take, when each step runs, and what a landing does.
     *
     * <p>A victim is held in place every tick from {@link #take} to its landing: put back at its column (players by a
     * teleport that leaves their view alone), its motion zeroed and its fall forgotten. No one is ever moved sideways,
     * and none ends lower than where it was taken, so a lift can't put anyone over the void. After a landing its fall
     * stays forgotten for a grace of ticks ({@link #forgive}), for a client that is a move behind. A victim stops being
     * held when it dies, leaves the level, turns creative or spectator, or is found away from its column (an ender
     * pearl).
     */
    public static final class Hoist {
        private final List<Held> held = new ArrayList<>();
        /** Landed victims and the game time their fall stops being forgotten. */
        private final Map<LivingEntity, Long> landed = new WeakHashMap<>();
        private final int grace;
        private long now;

        /** {@code grace}: how many ticks after its landing a victim's fall is still forgotten. */
        public Hoist(int grace) {
            this.grace = grace;
        }

        private static final class Held {
            final LivingEntity victim;
            final double x, z, from, rise;
            double y;
            double floor = Double.NaN;

            Held(LivingEntity victim, double rise) {
                this.victim = victim;
                this.x = victim.getX();
                this.z = victim.getZ();
                this.from = victim.getY();
                this.rise = rise;
                this.y = from;
            }
        }

        /** True if a solid block lies within {@code reach} blocks over {@code v}'s head: a roof that stops its lift. */
        public static boolean roofed(LivingEntity v, double reach) {
            AABB box = v.getBoundingBox();
            return !v.level().noBlockCollision(v, new AABB(box.minX + 0.01, box.maxY, box.minZ + 0.01, box.maxX - 0.01, box.maxY + reach, box.maxZ - 0.01));
        }

        /** How far {@code v} can rise, up to {@code max}: to the underside of the first solid block over its head. */
        public static double headroom(LivingEntity v, double max) {
            AABB box = v.getBoundingBox();
            AABB above = new AABB(box.minX + 0.01, box.maxY, box.minZ + 0.01, box.maxX - 0.01, box.maxY + max, box.maxZ - 0.01);
            double room = max;
            for (VoxelShape shape : v.level().getBlockCollisions(v, above)) {
                for (AABB part : shape.toAabbs()) {
                    if (part.minY >= box.maxY - 1.0E-7 && part.intersects(above)) room = Math.min(room, part.minY - box.maxY);
                }
            }
            return Math.max(0.0, room);
        }

        /**
         * The top of the highest solid block under {@code v} at height {@code feet}, searched down to {@code depth}
         * below {@code from}; NaN if there is none (open air, the void).
         */
        public static double floorUnder(LivingEntity v, double feet, double from, double depth) {
            AABB box = v.getBoundingBox();
            AABB below = new AABB(box.minX + 0.01, from - depth, box.minZ + 0.01, box.maxX - 0.01, feet, box.maxZ - 0.01);
            double best = Double.NaN;
            for (VoxelShape shape : v.level().getBlockCollisions(v, below)) {
                for (AABB part : shape.toAabbs()) {
                    if (part.maxY <= feet + 1.0E-7 && part.intersects(below) && !(part.maxY <= best)) best = part.maxY;
                }
            }
            return best;
        }

        /** Takes hold of {@code v} where it stands, to rise up to {@code height} (less under a block higher up). */
        public void take(ServerLevel level, LivingEntity v, double height) {
            Held h = new Held(v, headroom(v, height));
            held.add(h);
            place(level, v, h.x, h.from, h.z);
        }

        /** Every victim at {@code share} (0..1) of its rise. */
        public void raise(ServerLevel level, double share) {
            for (Held h : still(level)) {
                h.y = h.from + h.rise * share;
                place(level, h.victim, h.x, h.y, h.z);
            }
        }

        /** Every victim held where it is. */
        public void keep(ServerLevel level) {
            for (Held h : still(level)) place(level, h.victim, h.x, h.y, h.z);
        }

        /** The drop begins: each victim's landing is the ground under where it was taken (or that spot, over the void). */
        public void startDrop() {
            for (Held h : held) h.floor = floorUnder(h.victim, h.y, h.from, LIFT_HEIGHT);
        }

        /**
         * Drives every victim down {@code speed} blocks, no lower than its landing; returns those that landed on solid
         * ground this tick (no longer held). One with no ground under it is set down where it was taken and let go.
         */
        public List<LivingEntity> drop(ServerLevel level, double speed) {
            List<LivingEntity> down = new ArrayList<>();
            List<Held> done = new ArrayList<>();
            for (Held h : still(level)) {
                double floor = Double.isNaN(h.floor) ? h.from : h.floor;
                h.y = Math.max(floor, h.y - speed);
                place(level, h.victim, h.x, h.y, h.z);
                if (h.y > floor + 1.0E-7) continue;
                land(h);
                done.add(h);
                if (!Double.isNaN(h.floor)) down.add(h.victim);
            }
            held.removeAll(done);
            return down;
        }

        /** Sets every victim still held straight down on its landing at once; returns those that met solid ground. */
        public List<LivingEntity> setDown(ServerLevel level) {
            List<LivingEntity> down = new ArrayList<>();
            for (Held h : still(level)) {
                if (Double.isNaN(h.floor)) h.floor = floorUnder(h.victim, h.y, h.from, LIFT_HEIGHT);
                h.y = Double.isNaN(h.floor) ? h.from : h.floor;
                place(level, h.victim, h.x, h.y, h.z);
                land(h);
                if (!Double.isNaN(h.floor)) down.add(h.victim);
            }
            held.clear();
            return down;
        }

        private void land(Held h) {
            Push.set(h.victim, Vec3.ZERO);
            h.victim.resetFallDistance();
            landed.put(h.victim, now + grace);
        }

        /** Lets go of everyone still held, where they are; they fall under Slow Falling for {@code cushion} ticks. */
        public void letGo(@Nullable Entity by, int cushion) {
            for (Held h : held) {
                LivingEntity v = h.victim;
                if (!v.isAlive() || v.isRemoved()) continue;
                Push.set(v, Vec3.ZERO);
                v.resetFallDistance();
                v.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, cushion, 0), by);
            }
            held.clear();
        }

        /** Forgets every victim (a new pull, or the pull is over); landed ones keep their grace. */
        public void clear() {
            held.clear();
        }

        /** Forgets the falls of victims that landed within the grace. Call it every tick, before the pull's step. */
        public void forgive(long time) {
            now = time;
            for (Iterator<Map.Entry<LivingEntity, Long>> it = landed.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<LivingEntity, Long> e = it.next();
                LivingEntity v = e.getKey();
                if (!v.isAlive() || v.isRemoved() || time > e.getValue()) {
                    it.remove();
                } else {
                    v.resetFallDistance();
                }
            }
        }

        /** Who is held now. */
        public List<LivingEntity> victims() {
            List<LivingEntity> out = new ArrayList<>();
            for (Held h : held) out.add(h.victim);
            return out;
        }

        public boolean holds(@Nullable Entity e) {
            for (Held h : held) if (h.victim == e) return true;
            return false;
        }

        /** Where {@code v} was taken from, or null if it isn't held. */
        public @Nullable Vec3 groundOf(LivingEntity v) {
            for (Held h : held) if (h.victim == v) return new Vec3(h.x, h.from, h.z);
            return null;
        }

        /** The victims still held, dropping any that got away, died, left or turned creative. */
        private List<Held> still(ServerLevel level) {
            held.removeIf(h -> {
                LivingEntity v = h.victim;
                if (!v.isAlive() || v.isRemoved() || v.level() != level) return true;
                if (v instanceof Player p && (p.isCreative() || p.isSpectator())) return true;
                double dx = v.getX() - h.x, dz = v.getZ() - h.z;
                return dx * dx + dz * dz > ESCAPE * ESCAPE;
            });
            return new ArrayList<>(held);
        }

        /** Holds {@code v} at x, y, z: still, its fall forgotten, off anything it rode. Players keep their view. */
        private static void place(ServerLevel level, LivingEntity v, double x, double y, double z) {
            v.resetFallDistance();
            if (v.isPassenger()) v.stopRiding();
            if (v.distanceToSqr(x, y, z) > 1.0E-6) {
                if (v instanceof ServerPlayer p) {
                    p.teleportTo(level, x, y, z, Relative.ROTATION, 0.0F, 0.0F, false);
                } else {
                    v.teleportTo(x, y, z);
                }
            }
            v.setDeltaMovement(Vec3.ZERO);
            if (v instanceof Mob m) m.getNavigation().stop();
        }
    }
}
