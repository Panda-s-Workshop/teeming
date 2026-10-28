package com.panda.teeming.mob.end;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Brinkram: a heavy End ram with curled purpur horns. Tell: for 1 s it paws the ground, snorts and lowers its head
 * while dust kicks up. Then it charges in a straight line at 0.5 b/t for up to 16 blocks. A hit does its attack (8)
 * and throws the victim 2.5 b/t out along the charge and 0.4 up, less by the victim's knockback resistance: with an
 * island's edge behind you, that is over the void. A wall stops the charge and it shakes its head for 1 s, no more.
 *
 * <p>Its weakness is the same edge. When the charge reaches ground that drops away (no solid block within 4 below,
 * or the void) it goes over 70 % of the time and falls; otherwise it skids to a stop at the brink and teeters there
 * for 2 s, its rump lit weak-point gold, and any blow tips it over. One that goes over and dies before it lands again
 * (the void) leaves its drops where it went over, a step back from the edge. Counter: keep solid ground at your back,
 * or bait it at an edge and step aside. Its ordinary blow is a head butt with a short wind-up of its own.
 */
public class Brinkram extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL, CHARGE, TEETER, OVER, BONK, BUTT, RECOVER }

    public static final int TELL_TICKS = 20;
    public static final double CHARGE_SPEED = 0.5;
    public static final int CHARGE_TICKS = 32;            // 16 blocks at 0.5 b/t
    public static final double MIN_RANGE = 4.0;
    public static final double MAX_RANGE = 16.0;
    public static final float CHARGE_HIT = 1.0F;          // x attack damage
    /** The victim's speed out along the charge, and up, in blocks a tick (before its knockback resistance). */
    public static final double KNOCKBACK_OUT = 2.5;
    public static final double KNOCKBACK_UP = 0.4;
    /** At an edge, the chance it goes over; otherwise it skids to a stop at the brink. */
    public static final float OVER_CHANCE = 0.7F;
    /** It teeters at the brink this long, open to the blow that tips it over. */
    public static final int TEETER_TICKS = 40;
    /** After a wall stops it: a head shake, nothing more. */
    public static final int BONK_TICKS = 20;
    public static final int RECOVER_TICKS = 20;
    public static final int COOLDOWN = 100;
    /** The head butt: its wind-up, the wait before the next one, and its damage (x attack damage). */
    public static final int BUTT_TICKS = 10;
    public static final int BUTT_EVERY = 20;
    public static final float BUTT_HIT = 1.0F;
    /** Once over the edge it keeps running this many ticks, so its whole body clears the lip. */
    public static final int OVER_PUSH_TICKS = 6;
    /** A fall that hasn't ended after this long is over anyway (in the void it dies well before). */
    public static final int OVER_MAX_TICKS = 400;
    /** Its drops, if it dies before landing, fall this far back from where it went over, toward where it came from. */
    public static final double LOOT_STEP_BACK = 0.75;
    /** Baited over by a player: the kill counts as theirs this long (drops that need a player kill, experience). */
    public static final int OVER_CREDIT_TICKS = 400;
    public static final double KNOCKBACK_RESISTANCE = 0.5;

    private static final Telegraph SNORT = Telegraph.particles(ParticleTypes.CLOUD, 4, 0.08, 0.6).withSpeed(0.03);

    private int cooldown;
    private int buttCooldown;
    private Vec3 chargeDir = Vec3.ZERO;
    /** Where its drops go if it dies before it lands again, or null when it is not falling from an edge. */
    private @Nullable Vec3 brink;
    private @Nullable Boolean fixedRoll;
    private int edgeRolls;
    private int overs;

    public Brinkram(EntityType<? extends Brinkram> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("brinkram", 0.25).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
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

            /** In reach it lowers its horns for a moment first: the butt lands at the end of the wind-up. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
                if (buttCooldown == 0 && core().idle() && canPerformAttack(target)) butt(target);
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.TEETER);
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (buttCooldown > 0) buttCooldown--;
        LivingEntity target = getTarget();
        if (core().in(Step.TELL)) {
            tellTick(level, target);
        } else if (core().in(Step.CHARGE)) {
            chargeTick(level);
        } else if (core().in(Step.TEETER)) {
            teeterTick(level);
        } else if (core().in(Step.OVER)) {
            overTick();
        } else if (core().in(Step.BUTT)) {
            getNavigation().stop();
            LivingEntity t = core().target();
            if (t != null && t.isAlive()) face(t.position().subtract(position()));
        } else if (core().in(Step.BONK) || core().in(Step.RECOVER)) {
            getNavigation().stop();
        } else if (canCharge(target)) {
            if (startPhase(Step.TELL, TELL_TICKS, target)) {
                playSound(ModSounds.BRINKRAM_TELL.get(), 1.2F, 1.0F);
                getNavigation().stop();
            }
        }
    }

    private boolean canCharge(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || cooldown > 0 || !onGround() || isInWater()) return false;
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        double d = distanceTo(target);
        return d >= MIN_RANGE && d <= MAX_RANGE && getSensing().hasLineOfSight(target);
    }

    /** The tell: it stands square to its target, paws (dust kicked up from the ground), and snorts twice. */
    private void tellTick(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (target != null) face(target.position().subtract(position()));
        int t = core().elapsed();
        if (t % 3 == 0) {
            Vec3 hoof = position().add(forward().scale(getBbWidth() * 0.4));
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground(level)), hoof.x, getY() + 0.1, hoof.z,
                    6, 0.25, 0.05, 0.25, 0.12);
        }
        if (t == 7 || t == 15) {
            Vec3 nose = nose();
            for (int side = -1; side <= 1; side += 2) {
                Vec3 at = nose.add(sideways().scale(0.12 * side));
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 2, 0.03, 0.02, 0.03, 0.01);
            }
            level.playSound(null, nose.x, nose.y, nose.z, ModSounds.BRINKRAM_SNORT.get(), getSoundSource(), 1.0F, 1.0F);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
            return;
        }
        if (ended == Step.TELL) {
            LivingEntity target = core().target();
            Vec3 to = target == null ? Vec3.ZERO : target.position().subtract(position()).multiply(1, 0, 1);
            if (to.lengthSqr() < 1.0E-4 || !startPhase(Step.CHARGE, CHARGE_TICKS, target)) {
                rest();
                return;
            }
            chargeDir = to.normalize();
            face(chargeDir);
            playSound(ModSounds.BRINKRAM_CHARGE.get(), 1.3F, 1.0F);
        } else if (ended == Step.CHARGE) {
            startPhase(Step.RECOVER, RECOVER_TICKS, null);
        } else if (ended == Step.TEETER) {
            // it backs off the brink, shaken, and the fight goes on
            setDeltaMovement(-chargeDir.x * 0.2, getDeltaMovement().y, -chargeDir.z * 0.2);
            startPhase(Step.RECOVER, RECOVER_TICKS, null);
        } else if (ended == Step.BUTT) {
            finishButt(level, core().target());
            core().end();
        } else {
            rest();
        }
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
    }

    // ---- the charge ------------------------------------------------------------------------------------------------

    private void chargeTick(ServerLevel level) {
        getNavigation().stop();
        face(chargeDir);
        if (horizontalCollision && core().elapsed() > 2) {
            bonk(level);
            return;
        }
        AABB reach = getBoundingBox().inflate(0.3).move(chargeDir.scale(0.5));
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, reach, this::canRam)) {
            ram(level, victim);
            startPhase(Step.RECOVER, RECOVER_TICKS, null);
            return;
        }
        if (onGround() && edgeAhead(level)) {
            if (rollOver()) goOver(level);
            else skid(level);
            return;
        }
        setDeltaMovement(chargeDir.x * CHARGE_SPEED, getDeltaMovement().y, chargeDir.z * CHARGE_SPEED);
        if (tickCount % 2 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground(level)), getX(), getY() + 0.1, getZ(), 4, 0.4, 0.05, 0.4, 0.05);
        }
    }

    private boolean canRam(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Brinkram) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** The hit: its attack, then the victim thrown out along the charge and up, less by its knockback resistance and by half with a Shellward Charm. */
    private void ram(ServerLevel level, LivingEntity victim) {
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BRINKRAM_RAM.get(), getSoundSource(), 1.2F, 1.0F);
        if (!MechanicEvent.post(this, "charge_hit", victim)) return;
        DamageSource source = damageSources().mobAttack(this);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * CHARGE_HIT;
        if (!victim.hurtServer(level, source, damage)) return;
        double keep = (1.0 - Mth.clamp(victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0)) * Charms.chargeThrow(victim);
        if (keep > 0.0 && MechanicEvent.post(this, "throw", victim)) {
            Push.set(victim, new Vec3(chargeDir.x * KNOCKBACK_OUT * keep, KNOCKBACK_UP * keep, chargeDir.z * KNOCKBACK_OUT * keep));
        }
        Vec3 at = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.05);
    }

    /** A wall stopped it: it rocks back and shakes its head, and that is all. */
    private void bonk(ServerLevel level) {
        if (!startPhase(Step.BONK, BONK_TICKS, null)) {
            startPhase(Step.RECOVER, RECOVER_TICKS, null);
            return;
        }
        setDeltaMovement(-chargeDir.x * 0.2, 0.15, -chargeDir.z * 0.2);
        Vec3 front = position().add(chargeDir.scale(getBbWidth() * 0.5 + 0.3));
        BlockState wall = level.getBlockState(BlockPos.containing(front.x, getY() + 0.6, front.z));
        if (!wall.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wall), front.x, getY() + 0.8, front.z, 16, 0.25, 0.3, 0.25, 0.1);
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BRINKRAM_BONK.get(), getSoundSource(), 1.2F, 1.0F);
    }

    // ---- the edge --------------------------------------------------------------------------------------------------

    /**
     * True when the ground drops away right in front of it: every column under its front edge (both corners and the
     * middle), where it will be next tick, has no solid block within {@link Brink#MAX_DROP} below its feet' height.
     * Public as a test hook.
     */
    public boolean edgeAhead(ServerLevel level) {
        if (chargeDir.lengthSqr() < 1.0E-6) return false;
        double half = getBbWidth() / 2.0;
        double ahead = half + CHARGE_SPEED;
        Vec3 side = new Vec3(-chargeDir.z, 0.0, chargeDir.x);
        int feet = Mth.floor(getY() + 0.01);
        for (double s : new double[] {-(half - 0.1), 0.0, half - 0.1}) {
            BlockPos column = BlockPos.containing(getX() + chargeDir.x * ahead + side.x * s, feet, getZ() + chargeDir.z * ahead + side.z * s);
            if (!level.hasChunkAt(column)) return false;
            if (!Brink.edge(dy -> solid(level, column.above(dy)))) return false;
        }
        return true;
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY() || level.isOutsideBuildHeight(pos)) return false;
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** The roll at an edge: true (70 %) when it goes over. Counted, and fixable, for tests. Public as a test hook. */
    public boolean rollOver() {
        edgeRolls++;
        if (fixedRoll != null) return fixedRoll;
        return random.nextFloat() < OVER_CHANCE;
    }

    /** It runs on over the edge and falls; should it die before it lands, its drops stay at the brink. */
    private void goOver(ServerLevel level) {
        LivingEntity target = core().target();
        if (!startPhase(Step.OVER, 0, target)) {
            skid(level);
            return;
        }
        overs++;
        Vec3 back = position().subtract(chargeDir.scale(LOOT_STEP_BACK));
        brink = solid(level, BlockPos.containing(back.x, getY() - 0.5, back.z)) ? back : position();
        if (target instanceof Player p && !p.isCreative() && !p.isSpectator()) setLastHurtByPlayer(p, OVER_CREDIT_TICKS);
        setDeltaMovement(chargeDir.x * CHARGE_SPEED, getDeltaMovement().y, chargeDir.z * CHARGE_SPEED);
        Vec3 front = position().add(chargeDir.scale(getBbWidth() * 0.5));
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, ground(level)), front.x, getY(), front.z, 14, 0.4, 0.1, 0.4, 0.0);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BRINKRAM_OVER.get(), getSoundSource(), 1.4F, 1.0F);
    }

    /** Over the edge: it runs on a few ticks until its body clears the lip, then falls; landing ends it. */
    private void overTick() {
        getNavigation().stop();
        face(chargeDir);
        int t = core().elapsed();
        if (t < OVER_PUSH_TICKS) {
            setDeltaMovement(chargeDir.x * CHARGE_SPEED, getDeltaMovement().y, chargeDir.z * CHARGE_SPEED);
        } else if (onGround() || t > OVER_MAX_TICKS) {
            brink = null;
            startPhase(Step.RECOVER, RECOVER_TICKS, null);
        }
    }

    /** It skids to a stop at the brink and teeters there, open to a blow. */
    private void skid(ServerLevel level) {
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (!startPhase(Step.TEETER, TEETER_TICKS, core().target())) {
            startPhase(Step.RECOVER, RECOVER_TICKS, null);
            return;
        }
        Vec3 front = position().add(chargeDir.scale(getBbWidth() * 0.5));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground(level)), front.x, getY() + 0.1, front.z,
                14, 0.35, 0.05, 0.35, 0.15);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BRINKRAM_SKID.get(), getSoundSource(), 1.2F, 1.0F);
    }

    /** Teetering: it holds still, braced, while pebbles crumble from the brink under its front hooves. */
    private void teeterTick(ServerLevel level) {
        getNavigation().stop();
        face(chargeDir);
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (core().elapsed() % 3 == 0) {
            Vec3 front = position().add(chargeDir.scale(getBbWidth() * 0.5 + 0.2));
            level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, ground(level)), front.x, getY() - 0.1, front.z,
                    3, 0.4, 0.05, 0.4, 0.0);
        }
    }

    /** A blow while it teeters tips it over the edge. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean teetering = core().in(Step.TEETER);
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && teetering && isAlive() && source.getEntity() != null && core().in(Step.TEETER)
                && MechanicEvent.post(this, "tip", source.getEntity() instanceof LivingEntity l ? l : null)) {
            goOver(level);
        }
        return hurt;
    }

    /** Dying before it lands from an edge (the void), it leaves its drops at the brink, where they can be picked up. */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        Vec3 at = brink;
        if (at == null) {
            super.dropAllDeathLoot(level, source);
            return;
        }
        Vec3 was = position();
        setPos(at.x, at.y, at.z);
        try {
            super.dropAllDeathLoot(level, source);
        } finally {
            setPos(was.x, was.y, was.z);
        }
    }

    /**
     * The edge rule, with no game types so it can be checked on its own. A column ahead is read as cells by their
     * height from the feet' cell (0, then -1 the block it would stand on, and so on down): the ground falls away there
     * when the feet' cell is open and nothing solid lies in the {@code MAX_DROP + 1} cells below it, so a step down of
     * 4 is still ground and a drop of 5, or the void, is an edge. A solid feet' cell is a wall, never an edge.
     */
    public static final class Brink {
        /** The deepest step down that still counts as ground ahead. */
        public static final int MAX_DROP = 4;

        private Brink() {}

        public static boolean edge(IntPredicate solid) {
            if (solid.test(0)) return false;
            for (int d = 1; d <= MAX_DROP + 1; d++) if (solid.test(-d)) return false;
            return true;
        }
    }

    // ---- the head butt ---------------------------------------------------------------------------------------------

    private void butt(LivingEntity target) {
        if (!startPhase(Step.BUTT, BUTT_TICKS, target)) return;
        buttCooldown = BUTT_EVERY;
        getNavigation().stop();
        face(target.position().subtract(position()));
        playSound(ModSounds.BRINKRAM_SNORT.get(), 1.0F, 1.1F);
        SNORT.play(this);
    }

    /** The end of the wind-up: the butt lands if the target is still in reach. */
    private void finishButt(ServerLevel level, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || !isWithinMeleeAttackRange(target) || !getSensing().hasLineOfSight(target)) return;
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BRINKRAM_BUTT.get(), getSoundSource(), 1.0F, 1.0F);
        AstralAugur.GroundMarks.inPlace(target, () -> doHurtTarget(level, target));   // only the edge charge throws
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private BlockState ground(ServerLevel level) {
        BlockState under = level.getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ()));
        return under.isAir() ? level.getBlockState(BlockPos.containing(getX(), getY() - 1.2, getZ())) : under;
    }

    private Vec3 forward() {
        return Vec3.directionFromRotation(0.0F, yBodyRot);
    }

    private Vec3 sideways() {
        return Vec3.directionFromRotation(0.0F, yBodyRot + 90.0F);
    }

    /** Roughly where its nostrils are: low in front of its lowered head. */
    private Vec3 nose() {
        return position().add(forward().scale(getBbWidth() * 0.5 + 0.55)).add(0.0, getBbHeight() * 0.45, 0.0);
    }

    private void face(Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---- save ------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("ChargeCooldown", cooldown);
    }

    /** The wait to the next charge survives a reload; a charge, teeter or fall in progress ends. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cooldown = Math.max(0, input.getIntOr("ChargeCooldown", 0));
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Ticks until it may charge again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it charge again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
        buttCooldown = 0;
    }

    /** The way the current (or last) charge runs. Public as a test hook. */
    public Vec3 chargeDir() {
        return chargeDir;
    }

    /** Fixes the edge roll (true: over, false: skid; null: the 70 % roll). Public as a test hook. */
    public void fixEdgeRoll(@Nullable Boolean over) {
        fixedRoll = over;
    }

    /** How many edge rolls it has made, and how many times it went over. Public as test hooks. */
    public int edgeRolls() {
        return edgeRolls;
    }

    public int overs() {
        return overs;
    }

    /** Where its drops go if it dies before it lands, or null when it is not falling from an edge. Public as a test hook. */
    public @Nullable Vec3 brink() {
        return brink;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BRINKRAM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BRINKRAM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BRINKRAM_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.1F;
    }
}
