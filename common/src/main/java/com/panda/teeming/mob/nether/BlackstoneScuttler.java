package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Blackstone Scuttler: a low, heavy crab-like scuttler of the basalt deltas whose shell is fused blackstone.
 *
 * <p>Its shell decides what a blow is worth, all the time. A pickaxe in the hand that strikes it cracks the stone:
 * x3, with a crack and a spray of blackstone chips. Every other melee blow by a living attacker (bare hands, swords,
 * axes, maces, a trident or spear used in the hand, another mob's claws or bite) and every projectile (arrows, thrown
 * tridents, fire charges) glances off: x0.3. Anything that is neither a blow nor a shot (explosions, magic, falls,
 * thorns) lands whole. Lava, fire and magma floors never hurt it.
 *
 * <p>Tell: within 3 blocks of its target it turns to face it and rears up on its back legs for 12 ticks, claws high
 * and open, with a grinding scrape and blackstone dust. Its aim is set as it rears: it lunges straight that way, 0.4
 * blocks a tick for 6 ticks, and pinches the first thing in its path for 1x Atk. Counter: bring a pickaxe; a player
 * who steps aside while it rears lets the lunge go by.
 */
public class BlackstoneScuttler extends TeemingMonster {
    public enum Step implements Phase { IDLE, REAR, LUNGE, RECOVER }

    /** The rear: claws up, grinding, for 12 ticks before the lunge. */
    public static final int REAR_TICKS = 12;
    /** It rears when its target is this close (blocks, centre to centre) and in sight. */
    public static final double REAR_RANGE = 3.0;
    /** The lunge: straight ahead at 0.4 blocks a tick for 6 ticks, about 2.4 blocks. */
    public static final double LUNGE_SPEED = 0.4;
    public static final int LUNGE_TICKS = 6;
    /** The pinch at the end of the lunge, as a multiple of its attack damage. */
    public static final float PINCH_HIT = 1.0F;
    /** After the lunge it settles back onto its legs for half a second. */
    public static final int RECOVER_TICKS = 10;
    /** Ticks after it settles before it may rear again. */
    public static final int COOLDOWN = 30;
    /** What a weapon blow or a projectile is worth against the fused shell. */
    public static final float SHELL_FACTOR = 0.3F;
    /** What a pickaxe blow is worth: it cracks the stone. */
    public static final float PICKAXE_FACTOR = 3.0F;
    /** The rare gilded look, which drops gold nuggets as well. */
    public static final String GILDED = "gilded_scuttler";
    public static final int GILDED_NUGGETS_MIN = 1;
    public static final int GILDED_NUGGETS_MAX = 3;

    private static final BlockState STONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockParticleOption DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, STONE);
    private static final BlockParticleOption CHIPS = new BlockParticleOption(ParticleTypes.BLOCK, STONE);
    private static final Telegraph REAR = Telegraph.particles(DUST, 9, 0.45, 0.6).withSpeed(0.0)
            .withSound(ModSounds.BLACKSTONE_SCUTTLER_REAR.get(), 1.0F, 1.0F);

    /** How its shell takes a blow. */
    public enum Shell {
        /** A pickaxe blow: x3. */
        CRACK,
        /** Any other melee blow, or a projectile: x0.3. */
        GLANCE,
        /** Everything else lands whole. */
        WHOLE
    }

    private int cooldown;
    private Vec3 lungeDir = Vec3.ZERO;
    /** The way it faced as it reared: the lunge goes this way. */
    private float aimYaw;
    /** Whether the lunge under way has pinched something already. */
    private boolean pinched;

    public BlackstoneScuttler(EntityType<? extends BlackstoneScuttler> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("blackstone_scuttler", 0.2).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** It closes in only; the blow is the lunge that follows the rear. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {}
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- the shell -------------------------------------------------------------------------------------------------

    /** A melee blow: a strike by a living attacker's own hand, claw, sting or held weapon. */
    public static boolean melee(DamageSource source) {
        return source.isDirect() && source.getEntity() instanceof LivingEntity
                && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                    || source.is(DamageTypes.SPEAR) || source.is(DamageTypes.MACE_SMASH) || source.is(DamageTypes.STING));
    }

    /** How its shell takes {@code source}: a pickaxe blow cracks it, other blows and every projectile glance off. */
    public static Shell shell(DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return Shell.GLANCE;
        if (!melee(source)) return Shell.WHOLE;
        ItemStack weapon = source.getWeaponItem();
        return weapon != null && weapon.is(ItemTags.PICKAXES) ? Shell.CRACK : Shell.GLANCE;
    }

    /**
     * Scales each blow by its shell rule. Packs can veto each step: a vetoed "crack" leaves the pickaxe an ordinary
     * weapon (x0.3), a vetoed "glance" lets the blow land whole.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (amount <= 0.0F || isDeadOrDying() || isInvulnerableTo(level, source)) return super.hurtServer(level, source, amount);
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        Shell rule = shell(source);
        if (rule == Shell.CRACK && !MechanicEvent.post(this, "crack", attacker)) rule = Shell.GLANCE;
        if (rule == Shell.GLANCE && !MechanicEvent.post(this, "glance", attacker)) rule = Shell.WHOLE;
        float factor = switch (rule) {
            case CRACK -> PICKAXE_FACTOR;
            case GLANCE -> SHELL_FACTOR;
            case WHOLE -> 1.0F;
        };
        boolean hurt = super.hurtServer(level, source, amount * factor);
        if (hurt && rule == Shell.CRACK) cracked(level, source);
        else if (hurt && rule == Shell.GLANCE) glanced(level, source);
        return hurt;
    }

    /** Where a blow struck the shell: the attacker's side of it, at mid height. */
    private Vec3 struck(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        Vec3 mid = position().add(0, getBbHeight() * 0.6, 0);
        if (from == null) return mid;
        Vec3 to = from.subtract(mid).multiply(1, 0, 1);
        return to.lengthSqr() < 1.0E-6 ? mid : mid.add(to.normalize().scale(getBbWidth() * 0.5));
    }

    /** The counter landed: the stone cracks, with a burst of blackstone chips and a crack. */
    private void cracked(ServerLevel level, DamageSource source) {
        Vec3 at = struck(source);
        level.sendParticles(CHIPS, at.x, at.y, at.z, 24, 0.25, 0.2, 0.25, 0.25);
        level.sendParticles(DUST, at.x, at.y + 0.1, at.z, 6, 0.3, 0.1, 0.3, 0.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BLACKSTONE_SCUTTLER_CRACK.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** The shell turned the blow: a dull clank and a puff of dust. */
    private void glanced(ServerLevel level, DamageSource source) {
        Vec3 at = struck(source);
        level.sendParticles(DUST, at.x, at.y, at.z, 3, 0.15, 0.1, 0.15, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.1, 0.05, 0.1, 0.01);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BLACKSTONE_SCUTTLER_GLANCE.get(), getSoundSource(), 0.8F, 1.0F);
    }

    // ---- the rear and the lunge ------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (core().in(Step.REAR)) {
            holdStill();
            face(aimYaw);                                         // its aim was set as it reared: it doesn't turn
            REAR.trail(this, 2);
        } else if (core().in(Step.LUNGE)) {
            lungeTick(level);
        } else if (core().in(Step.RECOVER)) {
            holdStill();
        } else {
            LivingEntity target = getTarget();
            if (canRear(target)) rear(target);
        }
    }

    private void holdStill() {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    private boolean canRear(@Nullable LivingEntity target) {
        if (target == null || !canPinch(target) || cooldown > 0 || !onGround() || isInWater()) return false;
        return distanceTo(target) <= REAR_RANGE && getSensing().hasLineOfSight(target);
    }

    /** Turns to {@code target} and rears up for {@link #REAR_TICKS}; the lunge follows that way. Public as a test hook. */
    public boolean rear(LivingEntity target) {
        if (!core().idle() || !startPhase(Step.REAR, REAR_TICKS, target)) return false;
        holdStill();
        face(target.position().subtract(position()), 360.0F);
        aimYaw = getYRot();
        REAR.play(this);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.REAR) {
            Vec3 dir = Vec3.directionFromRotation(0.0F, aimYaw);
            if (!startPhase(Step.LUNGE, LUNGE_TICKS, core().target())) {
                rest();
                return;
            }
            lungeDir = dir.multiply(1, 0, 1).normalize();
            pinched = false;
            playSound(ModSounds.BLACKSTONE_SCUTTLER_STEP.get(), 0.8F, 0.7F);
        } else if (ended == Step.LUNGE) {
            if (!startPhase(Step.RECOVER, RECOVER_TICKS, null)) rest();
        } else {
            rest();
        }
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
    }

    /** One tick of the lunge: straight ahead, and a pinch for the first thing in its path. */
    private void lungeTick(ServerLevel level) {
        getNavigation().stop();
        face(lungeDir, 360.0F);
        setDeltaMovement(lungeDir.x * LUNGE_SPEED, getDeltaMovement().y, lungeDir.z * LUNGE_SPEED);
        if (tickCount % 2 == 0) level.sendParticles(CHIPS, getX(), getY() + 0.1, getZ(), 3, 0.35, 0.05, 0.35, 0.05);
        if (pinched) return;
        AABB reach = getBoundingBox().inflate(0.3, 0.0, 0.3).move(lungeDir.scale(0.5));
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, reach, this::canPinch)) {
            pinched = true;
            level.playSound(null, getX(), getY(), getZ(), ModSounds.BLACKSTONE_SCUTTLER_PINCH.get(), getSoundSource(), 1.0F, 1.0F);
            if (MechanicEvent.post(this, "pinch", victim)) {
                victim.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * PINCH_HIT);
                setLastHurtMob(victim);
            }
            startPhase(Step.RECOVER, RECOVER_TICKS, null);
            return;
        }
    }

    private boolean canPinch(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof BlackstoneScuttler) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void face(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Turns to face along {@code dir}, at most {@code maxTurn} degrees. */
    private void face(Vec3 dir, float maxTurn) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Ticks until it may rear again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it rear again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    /** The way the lunge under way goes (zero before the first). Public as a test hook. */
    public Vec3 lungeDir() {
        return lungeDir;
    }

    // ---- loot ------------------------------------------------------------------------------------------------------

    /** The gilded look drops 1 to 3 gold nuggets on top of its loot table. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (GILDED.equals(look())) {
            int count = GILDED_NUGGETS_MIN + random.nextInt(GILDED_NUGGETS_MAX - GILDED_NUGGETS_MIN + 1);
            spawnAtLocation(level, new ItemStack(Items.GOLD_NUGGET, count), (float) (getBbHeight() * 0.5));
        }
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.BLACKSTONE_SCUTTLER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BLACKSTONE_SCUTTLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BLACKSTONE_SCUTTLER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.BLACKSTONE_SCUTTLER_STEP.get(), 0.35F, 1.0F);
    }
}
