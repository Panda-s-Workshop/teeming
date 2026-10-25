package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Basalt Behemoth: the Nether's first apex, a hulking mound of basalt columns that walks on its knuckles towards its
 * target, slowly and turning slowly. A strong field mob: no boss bar and no arena, only one in a wide stretch of the
 * basalt deltas.
 *
 * <p>Tell: once its target is within 3.5 blocks it rears up on its hind legs for 1 s, roaring, embers and dust pouring
 * from its joints and a ring of dust on the ground at its reach. Then it slams both forelimbs down: everything on the
 * ground within 4 blocks takes 14 and is thrown back. Anything in the air (a jump timed to the slam) is spared.
 *
 * <p>After every slam its chest plates grind apart and the molten core shows for 3 s: no armour, and every hit lands
 * double. It is no stun: it keeps walking and swatting while the core is open, then the plates seal again and it waits
 * 3 s before it may rear again.
 */
public class BasaltBehemoth extends TeemingMonster {
    /** IDLE, the rear (tell), the open chest (the punish window after each slam). */
    public enum Step implements Phase { IDLE, TELL, EXPOSED }

    /** 1 s on its hind legs before the slam. */
    public static final int TELL_TICKS = 20;
    /** It rears when its target is this close (horizontally), well inside the slam's reach. */
    public static final double REAR_RANGE = 3.5;
    /** The slam reaches 4 blocks from its middle, along the ground. */
    public static final double SLAM_RADIUS = 4.0;
    /** And reaches only those within this height of its feet (a step up or down, not a ledge above). */
    public static final double SLAM_HEIGHT = 1.5;
    public static final float SLAM_DAMAGE = 14.0F;
    /** A body this close above solid ground counts as on the ground. */
    public static final double GROUND_GAP = 0.1;
    /** The throw: blocks a tick away from it, and the lift. */
    public static final double SLAM_PUSH = 0.7;
    public static final double SLAM_LIFT = 0.35;
    /** 3 s with the core open after each slam. */
    public static final int EXPOSED_TICKS = 60;
    /** While open: no armour, and every hit lands double. */
    public static final int EXPOSED_ARMOR = 0;
    public static final float EXPOSED_DAMAGE = 2.0F;
    /** Once sealed it waits 3 s before it may rear again. */
    public static final int REAR_COOLDOWN = 60;
    public static final int XP = 100;
    /** The heaviest turn it can make in a tick, in degrees. */
    public static final float MAX_TURN = 6.0F;

    /** Glow stages: everything dark while the core is open (only the gold shows), smouldering joints, flaring joints. */
    public static final int STAGE_OPEN = 0, STAGE_SMOULDER = 1, STAGE_FLARE = 2;

    /** The core in the chest, from its feet: up and forward, in blocks. */
    private static final double CORE_UP = 1.95, CORE_FORWARD = 1.05;
    private static final int RING_POINTS = 36;
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD675, 1.2F);
    private static final BlockParticleOption BASALT_DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BASALT.defaultBlockState());

    private int untilRear;

    public BasaltBehemoth(EntityType<? extends BasaltBehemoth> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
        moveControl = new HeavyControl(this);
        core().setStage(STAGE_SMOULDER);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("basalt_behemoth", 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return walks() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return walks() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return walks() && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** It walks and swats when idle and while its core is open; it stands only to rear. */
    private boolean walks() {
        return core().idle() || core().in(Step.EXPOSED);
    }

    @Override
    public int visualStages() {
        return 3;
    }

    /** Its weak point, the core, glows weak-point gold while the chest is open. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.EXPOSED);
    }

    /** Every phase sets its glow stage as it starts: the open chest goes dark but for the core, the rear flares. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.EXPOSED ? STAGE_OPEN : phase == Step.TELL ? STAGE_FLARE : STAGE_SMOULDER);
        return true;
    }

    // ---- the rear and the slam -------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().in(Step.TELL)) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));     // reared, it stands its ground
            return;
        }
        if (!core().idle()) return;
        if (untilRear > 0) untilRear--;
        LivingEntity target = getTarget();
        if (canRear(target)) rear(target);
    }

    private boolean canRear(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || untilRear > 0 || !onGround() || isInLiquid()) return false;
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        return dx * dx + dz * dz <= REAR_RANGE * REAR_RANGE && Math.abs(target.getY() - getY()) <= SLAM_HEIGHT;
    }

    /** Rears up for the slam, if it is free to; true if it did. Ignores range and cooldown. Public as a test hook. */
    public boolean rear(@Nullable LivingEntity target) {
        if (!core().idle() || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        playSound(ModSounds.BASALT_BEHEMOTH_TELL.get(), 2.0F, 1.0F);
        return true;
    }

    /** Runs on both the AI's and a frozen statue's ticks, so the tell and the open core always show. */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (core().in(Step.TELL)) {
            int t = core().elapsed();
            if (t % 2 == 0) {                                                    // embers and dust pour from its joints
                double y = getY() + getBbHeight() * 0.7;
                level.sendParticles(ParticleTypes.LAVA, true, true, getX(), y, getZ(), 1, 0.9, 0.4, 0.9, 0.0);
                level.sendParticles(BASALT_DUST, true, true, getX(), y, getZ(), 4, 1.0, 0.5, 1.0, 0.0);
                level.sendParticles(ParticleTypes.SMOKE, true, true, getX(), y, getZ(), 2, 0.9, 0.4, 0.9, 0.01);
            }
            if (t % 4 == 0) ring(level, crumbs(level), 0.05);                    // how far to run
        } else if (core().in(Step.EXPOSED) && core().elapsed() % 3 == 0) {
            Vec3 c = corePos();                                                  // gold motes drift from the open core
            level.sendParticles(GOLD, true, true, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) return;
        if (ended == Step.TELL) {
            if (!MechanicEvent.post(this, "slam", core().target())) {           // a vetoed slam: it settles back down
                seal(level, false);
                return;
            }
            slam(level);
            if (startPhase(Step.EXPOSED, EXPOSED_TICKS, core().target())) {
                playSound(ModSounds.BASALT_BEHEMOTH_OPEN.get(), 1.5F, 1.0F);
            } else {
                seal(level, false);
            }
        } else if (ended == Step.EXPOSED) {
            seal(level, true);
        } else {
            seal(level, false);
        }
    }

    /** Back to idle with the chest shut, and the wait before it may rear again. */
    private void seal(ServerLevel level, boolean wasOpen) {
        core().end();
        core().setStage(STAGE_SMOULDER);
        untilRear = REAR_COOLDOWN;
        if (wasOpen) {
            Vec3 c = corePos();
            level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.01);
            playSound(ModSounds.BASALT_BEHEMOTH_SEAL.get(), 1.2F, 1.0F);
        }
    }

    /**
     * Both forelimbs come down: everything on the ground within 4 blocks of its middle (and within 1.5 of its height)
     * takes 14 and is thrown back; a shield stops the damage, not the throw. Returns how many it hurt. Public as a
     * test hook.
     */
    public int slam(ServerLevel level) {
        int hurt = 0;
        AABB reach = new AABB(getX() - SLAM_RADIUS, getY() - SLAM_HEIGHT, getZ() - SLAM_RADIUS,
                getX() + SLAM_RADIUS, getY() + SLAM_HEIGHT + 0.01, getZ() + SLAM_RADIUS);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, reach, this::canSlam)) {
            if (!inReach(victim.position()) || !grounded(victim) || !MechanicEvent.post(this, "slam_hit", victim)) continue;
            if (victim.hurtServer(level, damageSources().mobAttack(this), SLAM_DAMAGE)) hurt++;
            throwBack(victim);
        }
        shockwave(level);
        playSound(ModSounds.BASALT_BEHEMOTH_SLAM.get(), 2.5F, 1.0F);
        return hurt;
    }

    /** Sets the victim flying straight away from its middle at 0.7 a tick, 0.35 up, whatever the blow's own knockback did. */
    private void throwBack(LivingEntity victim) {
        Vec3 away = victim.position().subtract(position()).multiply(1.0, 0.0, 1.0);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, yBodyRot) : away.normalize();
        Push.set(victim, new Vec3(away.x * SLAM_PUSH, SLAM_LIFT, away.z * SLAM_PUSH));
    }

    private boolean canSlam(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof BasaltBehemoth) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** True if feet at {@code feet} are inside the slam: within 4 blocks of its middle, near its height. */
    public boolean inReach(Vec3 feet) {
        double dx = feet.x - getX(), dz = feet.z - getZ();
        return dx * dx + dz * dz <= SLAM_RADIUS * SLAM_RADIUS && Math.abs(feet.y - getY()) <= SLAM_HEIGHT;
    }

    /**
     * On the ground: the game says so, or there is something solid within a hair under its feet. Players report their
     * own footing; the second test covers bodies whose footing the server never hears about. A jump's first tick lifts
     * 0.42, well clear of the hair.
     */
    public static boolean grounded(LivingEntity e) {
        if (e.onGround()) return true;
        AABB box = e.getBoundingBox();
        return !e.level().noCollision(e, new AABB(box.minX, box.minY - GROUND_GAP, box.minZ, box.maxX, box.minY, box.maxZ));
    }

    /** The slam lands: a ring of air rushing out along the ground, cracked ground across the reach, sparks at the fists. */
    private void shockwave(ServerLevel level) {
        ParticleOptions crumbs = crumbs(level);
        for (double r = 1.5; r <= SLAM_RADIUS + 0.01; r += 1.25) {
            for (int i = 0; i < RING_POINTS; i++) {
                double a = i * (Math.PI * 2 / RING_POINTS);
                double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
                level.sendParticles(ParticleTypes.POOF, true, true, x, getY() + 0.15, z, 0, Math.cos(a), 0.05, Math.sin(a), 0.25);
            }
        }
        for (int i = 0; i < 70; i++) {                                           // cracked ground, never outside the reach
            double a = getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(getRandom().nextDouble()) * (SLAM_RADIUS - 0.3);
            level.sendParticles(crumbs, true, true, getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 1, 0.0, 0.15, 0.0, 0.15);
        }
        Vec3 fists = position().add(Vec3.directionFromRotation(0.0F, yBodyRot).scale(1.6));
        level.sendParticles(ParticleTypes.EXPLOSION, true, true, fists.x, fists.y + 0.3, fists.z, 2, 0.6, 0.1, 0.6, 0.0);
        level.sendParticles(ParticleTypes.LAVA, true, true, getX(), getY() + 1.6, getZ(), 12, 0.8, 0.5, 0.8, 0.0);
        level.sendParticles(BASALT_DUST, true, true, getX(), getY() + 1.8, getZ(), 30, 1.2, 0.8, 1.2, 0.0);
    }

    /** Crumbs of whatever it stands on. */
    private ParticleOptions crumbs(ServerLevel level) {
        BlockState under = level.getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ()));
        return new BlockParticleOption(ParticleTypes.BLOCK, under.isAir() ? Blocks.BASALT.defaultBlockState() : under);
    }

    /** Particles round the ground at the slam's reach, rising at {@code lift}. */
    private void ring(ServerLevel level, ParticleOptions particle, double lift) {
        for (int i = 0; i < RING_POINTS; i++) {
            double a = i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(particle, true, true, getX() + Math.cos(a) * SLAM_RADIUS, getY() + 0.15, getZ() + Math.sin(a) * SLAM_RADIUS,
                    1, 0.0, 0.05, 0.0, lift);
        }
    }

    /** Where the core sits, in the middle of its chest. */
    public Vec3 corePos() {
        return position().add(Vec3.directionFromRotation(0.0F, yBodyRot).scale(CORE_FORWARD)).add(0.0, CORE_UP, 0.0);
    }

    // ---- the open core -----------------------------------------------------------------------------------------------

    /** Open, it has no armour. */
    @Override
    public int getArmorValue() {
        return core().in(Step.EXPOSED) ? EXPOSED_ARMOR : super.getArmorValue();
    }

    /** Open, every hit lands double, and a hit that lands bursts from the core with a hiss. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean open = core().in(Step.EXPOSED);
        boolean hurt = super.hurtServer(level, source, open ? amount * EXPOSED_DAMAGE : amount);
        if (hurt && open) {
            Vec3 c = corePos();
            level.sendParticles(ParticleTypes.LAVA, true, true, c.x, c.y, c.z, 6, 0.25, 0.25, 0.25, 0.0);
            level.sendParticles(GOLD, true, true, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, c.x, c.y, c.z, ModSounds.BASALT_BEHEMOTH_CORE_HIT.get(), getSoundSource(), 1.2F, 1.0F);
        }
        return hurt;
    }

    // ---- state and test hooks ----------------------------------------------------------------------------------------

    /** Ticks until it may rear again. */
    public int untilRear() {
        return untilRear;
    }

    /** Lets it rear again at once. Public as a test hook. */
    public void resetCooldown() {
        untilRear = 0;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("UntilRear", untilRear);
    }

    /** A reload ends a rear or an open chest: it comes back sealed and smouldering, its wait kept. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        untilRear = Math.max(0, input.getIntOr("UntilRear", 0));
        core().setStage(STAGE_SMOULDER);
    }

    /** It turns like the mountain it is: at most 6 degrees a tick, so its path curves wide. */
    private static final class HeavyControl extends MoveControl<BasaltBehemoth> {
        HeavyControl(BasaltBehemoth mob) {
            super(mob);
        }

        @Override
        public void tick() {
            float before = mob.getYRot();
            super.tick();
            mob.setYRot(Mth.approachDegrees(before, mob.getYRot(), MAX_TURN));
        }
    }

    /** Its head turns slowly and not far from its body. */
    @Override
    public int getHeadRotSpeed() {
        return 4;
    }

    @Override
    public int getMaxHeadYRot() {
        return 35;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.BASALT_BEHEMOTH_STEP.get(), 0.9F, 0.9F + random.nextFloat() * 0.1F);
        if (level() instanceof ServerLevel level) {                              // the ground shakes under each step
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), getX(), getY() + 0.1, getZ(), 6, 1.0, 0.05, 1.0, 0.1);
        }
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.BASALT_BEHEMOTH_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BASALT_BEHEMOTH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BASALT_BEHEMOTH_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + random.nextFloat() * 0.1F;
    }
}
