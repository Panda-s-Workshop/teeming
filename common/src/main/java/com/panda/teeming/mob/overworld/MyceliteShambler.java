package com.panda.teeming.mob.overworld;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mycelite Shambler: a slow fungal shambler of the dark forest and the caves, in three sizes. A full-grown one (scale
 * 1, 24 health) that dies bursts into two of the next size (scale 0.7, 12 health), and each of those into two of the
 * smallest (scale 0.45, 6 health), which don't burst. Every burst puffs spores: Poison I for 2 s to everything within
 * 2 blocks of it except shamblers. The burst comes as the body vanishes, a second after the killing blow (as a
 * slime's does), while its cap and spore sacs swell: time to step back out of the spores. New shamblers stand up out
 * of the burst for half a second before they fight. It swings with no tell; every size hits for its attack.
 *
 * <p>Counter: fire. A shambler that is burning when it dies, or dies to fire, crumbles to ash: no burst, no spores.
 * Failing it costs a growing crowd (one becomes up to four) and the poison of each burst.
 *
 * <p>The split comes on death, not on any hit: the health of each size is then what it takes to burst it, and a
 * fast weapon can't multiply the crowd. Loot drops from each death that doesn't burst (a smallest one, or any size
 * killed by fire), so a whole line drops once per smallest shambler or once for a burned one; experience drops from
 * every death, by size. Every size is a Mycelite Shambler, so the split shamblers count against the local and
 * monster caps like any other, and they despawn like any other unless the first one was kept.
 *
 * <p>The size is kept as the visual stage ({@code MobCore.stage}: 2 full-grown, 1 middle, 0 smallest), synced to
 * clients with the phase; the server turns it into a scale modifier (hitbox and model shrink together on every
 * client) and a health modifier.
 */
public class MyceliteShambler extends TeemingMonster {
    /** IDLE, and SPROUT: a fresh shamble standing up out of a burst, not yet fighting. */
    public enum Step implements Phase { IDLE, SPROUT }

    /** Sizes, largest first; a shambler of the last size doesn't burst. */
    public static final int LARGE = 0;
    public static final int MEDIUM = 1;
    public static final int SMALL = 2;
    /** Scale and health of each size (the full-grown health is the attribute's own). */
    public static final float SCALE_LARGE = 1.0F;
    public static final float SCALE_MEDIUM = 0.7F;
    public static final float SCALE_SMALL = 0.45F;
    public static final float HEALTH_LARGE = 24.0F;
    public static final float HEALTH_MEDIUM = 12.0F;
    public static final float HEALTH_SMALL = 6.0F;
    /** How many shamblers of the next size a burst leaves. */
    public static final int SPLIT_COUNT = 2;
    /** The spore puff of a burst: Poison I for 40 ticks to everything within 2 blocks. */
    public static final double PUFF_RADIUS = 2.0;
    public static final int POISON_TICKS = 40;
    public static final int POISON_LEVEL = 0;              // amplifier 0 = Poison I
    /** Ticks a fresh shambler spends standing up out of the burst before it may fight. */
    public static final int SPROUT_TICKS = 10;
    /** Experience for a kill, by size. */
    public static final int XP_LARGE = 5;
    public static final int XP_MEDIUM = 2;
    public static final int XP_SMALL = 1;

    /** Flag bit: the dying shambler will burst (the model swells its cap and sacs). */
    public static final int FLAG_BURSTING = 0;
    private static final int GROWN = 2;                    // the visual stage of a full-grown shambler
    private static final Identifier SIZE = TeemingMod.id("mycelite_size");
    public static final int SPORE_COLOR = 0xB06A8A;
    private static final DustParticleOptions SPORES = new DustParticleOptions(SPORE_COLOR, 1.3F);
    private static final DustParticleOptions CLOUD = new DustParticleOptions(SPORE_COLOR, 0.9F);

    /** Server: the size the modifiers were last set for (-1: not yet). */
    private int appliedSize = -1;
    /** Server: set at death when this death bursts. */
    private boolean bursts;
    private @Nullable LivingEntity killer;

    public MyceliteShambler(EntityType<? extends MyceliteShambler> type, Level level) {
        super(type, level, Step.values());
        core().setStage(GROWN);
        xpReward = XP_LARGE;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("mycelite_shambler", 0.23);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- size ------------------------------------------------------------------------------------------------------

    /** Three visual stages, one per size, so each size can be posed and looked at. */
    @Override
    public int visualStages() {
        return 3;
    }

    /** {@link #LARGE}, {@link #MEDIUM} or {@link #SMALL}. Both sides. */
    public int size() {
        return Math.clamp(GROWN - core().stage(), LARGE, SMALL);
    }

    public static float scaleOf(int size) {
        return size == LARGE ? SCALE_LARGE : size == MEDIUM ? SCALE_MEDIUM : SCALE_SMALL;
    }

    public static float healthOf(int size) {
        return size == LARGE ? HEALTH_LARGE : size == MEDIUM ? HEALTH_MEDIUM : HEALTH_SMALL;
    }

    /** Server: makes it this size; {@code fresh} also fills its health. Public as a test hook. */
    public void setSize(int size, boolean fresh) {
        core().setStage(GROWN - Math.clamp(size, LARGE, SMALL));
        applySize(fresh);
    }

    /**
     * The size's scale and health as permanent modifiers (saved with the mob; they sit beside the look's own and
     * multiply with them).
     */
    private void applySize(boolean fresh) {
        if (level().isClientSide()) return;
        int size = size();
        appliedSize = size;
        set(Attributes.SCALE, scaleOf(size) / SCALE_LARGE);
        set(Attributes.MAX_HEALTH, healthOf(size) / HEALTH_LARGE);
        xpReward = size == LARGE ? XP_LARGE : size == MEDIUM ? XP_MEDIUM : XP_SMALL;
        refreshDimensions();
        if (fresh || getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }

    private void set(Holder<Attribute> attribute, double factor) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) return;
        if (Math.abs(factor - 1.0) < 1.0E-6) instance.removeModifier(SIZE);
        else instance.addOrReplacePermanentModifier(new AttributeModifier(SIZE, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @Override
    public void tick() {
        // the size follows the stage, whoever set it (a split, a reload, a command)
        if (!level().isClientSide() && appliedSize != size()) applySize(false);
        super.tick();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().in(Step.SPROUT)) {
            getNavigation().stop();
            if (tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.MYCELIUM, getX(), getY() + 0.1, getZ(), 2, getBbWidth() * 0.4, 0.05, getBbWidth() * 0.4, 0.0);
            }
        }
    }

    // ---- death, the burst and the puff ------------------------------------------------------------------------------

    /** Decided at the killing blow: it bursts unless it is the smallest, it burns or fire killed it, or a pack vetoed it. */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide() && !isRemoved() && !dead) {
            boolean fire = source.is(DamageTypeTags.IS_FIRE) || isOnFire();
            killer = source.getEntity() instanceof LivingEntity by ? by : null;
            bursts = size() < SMALL && !fire && MechanicEvent.post(this, "burst", killer);
            core().setFlag(FLAG_BURSTING, bursts);
            if (size() < SMALL && fire && level() instanceof ServerLevel level) scorch(level);
        }
        super.die(source);
    }

    /** The counter landed: fire took it before it could burst. Smoke, ash and a fizzle. */
    private void scorch(ServerLevel level) {
        double y = getY() + getBbHeight() * 0.6;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), y, getZ(), 12, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.02);
        level.sendParticles(ParticleTypes.WHITE_ASH, getX(), y, getZ(), 30, getBbWidth() * 0.6, getBbHeight() * 0.4, getBbWidth() * 0.6, 0.02);
        level.playSound(null, getX(), y, getZ(), ModSounds.MYCELITE_SHAMBLER_SCORCH.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** Loot only from a death that ends its line: one that doesn't burst. */
    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return !bursts && super.shouldDropLoot(level);
    }

    /** The body vanishes a second after death (vanilla's death time); a bursting one bursts then. */
    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED && bursts && isDeadOrDying() && level() instanceof ServerLevel level) burst(level);
        super.remove(reason);
    }

    /**
     * The burst: the spore puff, then {@link #SPLIT_COUNT} shamblers of the next size, side by side where it stood,
     * in its look, each standing up out of the burst for {@link #SPROUT_TICKS} and then after whoever killed it.
     * Returns them. Public as a test hook (it doesn't need the shambler to be dead).
     */
    public List<MyceliteShambler> burst(ServerLevel level) {
        bursts = false;
        List<MyceliteShambler> out = new ArrayList<>();
        if (size() >= SMALL) return out;
        puff(level);
        int next = size() + 1;
        String look = look();
        double yaw = Math.toRadians(getYRot());
        Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));                  // its left-right, across where it faced
        double gap = getBbWidth() * 0.3;
        @SuppressWarnings("unchecked")
        EntityType<MyceliteShambler> type = (EntityType<MyceliteShambler>) getType();
        for (int i = 0; i < SPLIT_COUNT; i++) {
            double sign = i % 2 == 0 ? -1.0 : 1.0;
            MyceliteShambler child = convertTo(type, new ConversionParams(ConversionType.SPLIT_ON_DEATH, false, false, getTeam()),
                    EntitySpawnReason.TRIGGERED, c -> {
                        c.core().setLook(look.isEmpty() ? "base" : look, true);
                        c.setSize(next, true);
                        c.snapTo(getX() + side.x * gap * sign, getY() + 0.1, getZ() + side.z * gap * sign,
                                getYRot() + (float) sign * 25.0F, 0.0F);
                        c.yBodyRot = c.getYRot();
                        c.yHeadRot = c.getYRot();
                        c.setDeltaMovement(side.x * 0.12 * sign, 0.25, side.z * 0.12 * sign);
                        c.startPhase(Step.SPROUT, SPROUT_TICKS, null);
                        if (valid(killer)) c.setTarget(killer);
                    });
            if (child != null) out.add(child);
        }
        return out;
    }

    /**
     * The spore puff: Poison I for 40 ticks (20 under an Antivenom Charm) to every living thing within 2 blocks but
     * shamblers and spectators. Returns how many it poisoned (undead shrug poison off, as always). Public as a test hook.
     */
    public int puff(ServerLevel level) {
        int poisoned = 0;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(PUFF_RADIUS),
                e -> e != this && e.isAlive() && !e.isSpectator() && !(e instanceof MyceliteShambler) && e.distanceTo(this) <= PUFF_RADIUS);
        for (LivingEntity e : near) {
            if (!MechanicEvent.post(this, "spores", e)) continue;
            if (e.addEffect(new MobEffectInstance(MobEffects.POISON, Charms.poisonTicks(e, POISON_TICKS), POISON_LEVEL), this)) poisoned++;
            level.sendParticles(SPORES, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.25, 0.3, 0.25, 0.0);
        }
        double y = getY() + getBbHeight() * 0.5;
        for (int i = 0; i < 50; i++) {                                         // a cloud that fills the reach, never past it
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * (PUFF_RADIUS - 0.2);
            level.sendParticles(CLOUD, getX() + Math.cos(a) * r, getY() + 0.1 + random.nextDouble() * 1.4,
                    getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(SPORES, getX(), y, getZ(), 24, 0.5, 0.4, 0.5, 0.02);
        level.sendParticles(ParticleTypes.POOF, getX(), y, getZ(), 10, 0.4, 0.3, 0.4, 0.03);
        level.sendParticles(ParticleTypes.MYCELIUM, getX(), getY() + 0.2, getZ(), 30, PUFF_RADIUS * 0.6, 0.2, PUFF_RADIUS * 0.6, 0.0);
        level.playSound(null, getX(), y, getZ(), ModSounds.MYCELITE_SHAMBLER_BURST.get(), getSoundSource(), 1.2F, 1.0F + 0.2F * size());
        return poisoned;
    }

    private static boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** True from the killing blow until the burst, for a death that bursts. Public as a test hook. */
    public boolean willBurst() {
        return bursts;
    }

    // ---- saving --------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Size", size());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        core().setStage(GROWN - Math.clamp(input.getIntOr("Size", LARGE), LARGE, SMALL));
        appliedSize = -1;                                                      // re-applied on the next tick
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MYCELITE_SHAMBLER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MYCELITE_SHAMBLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MYCELITE_SHAMBLER_DEATH.get();
    }

    /** Smaller shamblers sound higher. */
    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * (1.0F + 0.25F * size());
    }
}
