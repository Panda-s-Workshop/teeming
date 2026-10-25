package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Weepling Broodmother: a bloated crimson creature of the crimson forests, dripping red ichor and dragging a swollen
 * abdomen. Every 200 ticks, with a target within 16 blocks, she squats for 1 s (tell: the abdomen swells and pulses, a
 * wet groan, ichor drips) and lays 2 sacs on the ground beside her. A sac has 1 health and pulses; any hit pops it.
 * After 60 ticks each unpopped sac hatches a weepling: a small crying creature with 6 health and 2 attack that rushes
 * her target. She keeps at most 6 sacs and weeplings at once and lays only what fits.
 *
 * <p>Counter: pop the sacs before they hatch. A clutch popped whole, before any of it hatched, costs her the next lay:
 * she grieves and her wait to the next lay grows by 200 ticks. Or kill her first: her unhatched sacs burst the moment
 * she dies, and the weeplings she leaves behind wither 100 ticks later. The brood never outlives her for long: a sac
 * whose mother is gone bursts within 1 s, a weepling withers within 5 s. Weeplings and sacs drop nothing; a weepling
 * gives 1 experience, a sac none.
 *
 * <p>The brood is this same entity type in another form, kept in the synced flags: {@link Form#SAC} or
 * {@link Form#WEEPLING}, with its own hitbox (0.4 of hers), health, attack and speed, and its mother's id. The
 * section "the brood" below holds all of it, so it can become an entity type of its own without touching the rest.
 */
public class WeeplingBroodmother extends TeemingMonster {
    /** IDLE, the squat before laying (tell), the laying itself. */
    public enum Step implements Phase { IDLE, TELL, LAY }

    /** What this one is: the mother, an unhatched sac, or a hatched weepling. */
    public enum Form { MOTHER, SAC, WEEPLING }

    /** 1 s squatting, abdomen pulsing, before the sacs drop. */
    public static final int TELL_TICKS = 20;
    /** The laying itself: the abdomen squeezes and the sacs drop beside her. */
    public static final int LAY_TICKS = 10;
    /** Ticks from one lay to the next, at the soonest. */
    public static final int LAY_EVERY = 200;
    /** A fresh mother may lay this soon. */
    public static final int FIRST_LAY = 40;
    /** Sacs in one clutch. */
    public static final int CLUTCH = 2;
    /** Sacs and weeplings alive at once, per mother. */
    public static final int MAX_BROOD = 6;
    /** She lays only with her target this close. */
    public static final double LAY_RANGE = 16.0;
    /** A clutch popped whole adds this much to her wait for the next lay. */
    public static final int GRIEF_TICKS = 200;
    /** Sacs drop this far from her middle, on the ground beside her abdomen. */
    public static final double SPREAD_MIN = 1.2;
    public static final double SPREAD_MAX = 1.8;
    /** Experience she gives. */
    public static final int MOTHER_XP = 10;

    /** A sac: 1 health, and any hit pops it. */
    public static final float SAC_HEALTH = 1.0F;
    /** Ticks from laying to hatching. */
    public static final int HATCH_TICKS = 60;
    /** A weepling: 6 health, 2 attack, quick on its feet, and it rushes. */
    public static final float WEEPLING_HEALTH = 6.0F;
    public static final double WEEPLING_ATTACK = 2.0;
    public static final double WEEPLING_SPEED = 0.33;
    public static final double WEEPLING_RUSH = 1.2;
    /** Experience a weepling gives; a sac gives none. */
    public static final int WEEPLING_XP = 1;
    /** A weepling whose mother is gone withers this many ticks later (5 s). */
    public static final int ORPHAN_TICKS = 100;
    /** A sac whose mother is gone (and not just loading a moment later) bursts this many ticks later. */
    public static final int ORPHAN_GRACE = 20;
    /** The brood's hitbox and shadow, as a share of hers. */
    public static final float BROOD_SCALE = 0.4F;

    /** Synced flag bits the model reads for the form. */
    public static final int FLAG_SAC = 0;
    public static final int FLAG_WEEPLING = 1;

    public static final int ICHOR_COLOR = 0xB01E2A;
    public static final int SAC_COLOR = 0xECC8C0;
    public static final int SAC_GLOW_COLOR = 0xFF6A8C;
    private static final DustParticleOptions ICHOR = new DustParticleOptions(ICHOR_COLOR, 1.0F);
    private static final DustParticleOptions SAC_DUST = new DustParticleOptions(SAC_COLOR, 1.0F);
    private static final DustParticleOptions SAC_GLOW = new DustParticleOptions(SAC_GLOW_COLOR, 0.8F);
    private static final int GROUND_SEARCH = 4;

    // ---- the mother's state
    private final List<WeeplingBroodmother> brood = new ArrayList<>();
    /** Ticks until she may lay again. */
    private int untilLay = FIRST_LAY;
    /** The latest clutch: its number, how many sacs it had, how many popped and hatched. */
    private int clutch;
    private int clutchLaid;
    private int clutchPopped;
    private int clutchHatched;
    /** Test hook: she holds still, so only her phase machine runs. */
    private boolean anchored;
    /** Set in registerGoals, which runs inside the Mob constructor, so it has no initializer. */
    private @Nullable WaterAvoidingRandomStrollGoal stroll;

    // ---- a brood member's state
    private @Nullable UUID motherId;
    private @Nullable WeeplingBroodmother mother;
    /** Ticks since it was laid. */
    private int age;
    /** Ticks it has gone without a living mother. */
    private int orphaned;
    /** The clutch it was laid in. */
    private int clutchOf;
    /** The form clients last sized it for; null until the first synced update. */
    private @Nullable Form sizedFor;

    public WeeplingBroodmother(EntityType<? extends WeeplingBroodmother> type, Level level) {
        super(type, level, Step.values());
        xpReward = MOTHER_XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("weepling_broodmother", 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {           // her own blow, between lays
            @Override
            public boolean canUse() {
                return form() == Form.MOTHER && core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return form() == Form.MOTHER && core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new MeleeAttackGoal(this, WEEPLING_RUSH, true) {  // a weepling's rush
            @Override
            public boolean canUse() {
                return form() == Form.WEEPLING && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return form() == Form.WEEPLING && super.canContinueToUse();
            }
        });
        stroll = new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return form() != Form.SAC && core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return form() != Form.SAC && core().idle() && super.canContinueToUse();
            }
        };
        goalSelector.addGoal(5, stroll);
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return form() != Form.SAC && core().idle() && super.canUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return form() != Form.SAC && core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return form() != Form.SAC && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return form() != Form.SAC && super.canUse();
            }
        });
    }

    // ---- what it is --------------------------------------------------------------------------------------------------

    /** Its form, read from the synced flags on either side. */
    public Form form() {
        return core() == null ? Form.MOTHER : formOf(core().flags());     // null while the Entity constructor runs
    }

    /** The form held in a set of synced flags: for the model, which sees only the flags. */
    public static Form formOf(int flags) {
        if ((flags & 1 << FLAG_WEEPLING) != 0) return Form.WEEPLING;
        if ((flags & 1 << FLAG_SAC) != 0) return Form.SAC;
        return Form.MOTHER;
    }

    /** How ripe a sac is, 0 when laid to 1 at hatching, from the synced flags: for the model's pulse. */
    public static float ripeness(int flags) {
        return (flags >>> 24 & 0xF) / 15.0F;
    }

    /** The brood is smaller: hitbox and shadow both follow this. */
    @Override
    public float getAgeScale() {
        return form() == Form.MOTHER ? 1.0F : BROOD_SCALE;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (core() == null) return;
        Form f = form();
        if (f != sizedFor) {
            sizedFor = f;
            refreshDimensions();
        }
    }

    /** A sac stays where it was laid: it only falls. (So does a mother told to stay put, as a test hook.) */
    @Override
    public Pin pin() {
        return form() == Form.SAC || anchored ? Pin.ROOTED : Pin.FREE;
    }

    /** A mother, her brood and her brood's siblings never turn on one another. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof WeeplingBroodmother other && family(other)) && super.canAttack(target);
    }

    private boolean family(WeeplingBroodmother other) {
        UUID mine = form() == Form.MOTHER ? getUUID() : motherId;
        UUID theirs = other.form() == Form.MOTHER ? other.getUUID() : other.motherId;
        return mine != null && mine.equals(theirs);
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && !(target instanceof WeeplingBroodmother w && family(w))
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---- the mother: laying ------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide() && untilLay > 0 && form() == Form.MOTHER) untilLay--;
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        switch (form()) {
            case MOTHER -> motherTick(level);
            case SAC -> sacTick(level);
            case WEEPLING -> weeplingTick(level);
        }
    }

    private void motherTick(ServerLevel level) {
        brood.removeIf(b -> b.isRemoved() || !b.isAlive() || b.mother != this);
        if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().idle() && !isNoAi() && untilLay <= TELL_TICKS && brood.size() < MAX_BROOD) {
            LivingEntity target = getTarget();
            if (valid(target) && distanceTo(target) <= LAY_RANGE) tell(target);
        }
        if (tickCount % 12 == 0) {                                               // ichor dripping from her belly
            Vec3 tip = tip();
            level.sendParticles(ICHOR, tip.x, getY() + 0.15, tip.z, 1, 0.3, 0.05, 0.3, 0.0);
        }
    }

    /**
     * Starts the squat before laying if she is free to; true if she did. Ignores the time since the last lay and how
     * many she has. Public as a test hook.
     */
    public boolean tell(@Nullable LivingEntity target) {
        if (level().isClientSide() || form() != Form.MOTHER || !core().idle()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        Telegraph.particles(ICHOR, 10, 0.5, 0.45).withSound(ModSounds.WEEPLING_BROODMOTHER_TELL.get(), 1.0F, 0.9F).play(this);
        return true;
    }

    /** Ichor drips and crimson spores rise round her pulsing abdomen; a second groan half way. */
    private void tellTick(ServerLevel level) {
        int t = core().elapsed();
        LivingEntity target = core().target();
        if (target != null) face(target.position());
        Vec3 tip = tip();
        if (t % 2 == 0) level.sendParticles(ICHOR, tip.x, getY() + 0.5, tip.z, 2, 0.45, 0.3, 0.45, 0.0);
        if (t % 4 == 0) level.sendParticles(ParticleTypes.CRIMSON_SPORE, tip.x, getY() + 0.8, tip.z, 3, 0.5, 0.3, 0.5, 0.01);
        if (t == TELL_TICKS / 2) playSound(ModSounds.WEEPLING_BROODMOTHER_TELL.get(), 0.7F, 1.05F);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            untilLay = LAY_EVERY;
            if (startPhase(Step.LAY, LAY_TICKS, core().target())) layClutch(level);
            else core().end();
        } else {
            core().end();
        }
    }

    /**
     * The laying: up to {@link #CLUTCH} sacs drop on the ground beside her abdomen, as many as fit under
     * {@link #MAX_BROOD}. Each sac posts its own "sac" step. Returns the new sacs. Public as a test hook.
     */
    public List<WeeplingBroodmother> layClutch(ServerLevel level) {
        List<WeeplingBroodmother> made = new ArrayList<>();
        brood.removeIf(b -> b.isRemoved() || !b.isAlive());
        int want = Math.min(CLUTCH, MAX_BROOD - brood.size());
        clutch++;
        clutchLaid = 0;
        clutchPopped = 0;
        clutchHatched = 0;
        LivingEntity target = core().target();
        for (int i = 0; i < want; i++) {
            if (!MechanicEvent.post(this, "sac", target)) continue;
            Vec3 at = spot(level, i);
            if (at == null) continue;
            WeeplingBroodmother sac = makeSac(level, at);
            if (sac == null) continue;
            made.add(sac);
            clutchLaid++;
            level.sendParticles(ICHOR, at.x, at.y + 0.2, at.z, 10, 0.25, 0.1, 0.25, 0.02);
            level.sendParticles(SAC_DUST, at.x, at.y + 0.3, at.z, 6, 0.2, 0.15, 0.2, 0.0);
        }
        Vec3 tip = tip();
        level.sendParticles(ICHOR, tip.x, getY() + 0.4, tip.z, 14, 0.4, 0.25, 0.4, 0.03);
        level.playSound(null, tip.x, getY() + 0.4, tip.z, ModSounds.WEEPLING_BROODMOTHER_LAY.get(), getSoundSource(), 1.0F, 1.0F);
        return made;
    }

    /** The back end of her abdomen, on the ground. */
    private Vec3 tip() {
        Vec3 back = Vec3.directionFromRotation(0.0F, yBodyRot).scale(-0.7);
        return position().add(back.x, 0.0, back.z);
    }

    /**
     * A spot on the ground beside her abdomen for sac {@code index}: left for the first, right for the second, a
     * little behind her middle; room for a sac, solid footing, no fluid, a live chunk.
     */
    private @Nullable Vec3 spot(ServerLevel level, int index) {
        Vec3 ahead = Vec3.directionFromRotation(0.0F, yBodyRot);
        EntityType<?> type = getType();
        for (int attempt = 0; attempt < 10; attempt++) {
            double side = index % 2 == 0 ? 1.0 : -1.0;
            double turn = side * (100.0 + attempt * 25.0) + (index >= 2 ? side * 40.0 : 0.0);
            double r = SPREAD_MIN + random.nextDouble() * (SPREAD_MAX - SPREAD_MIN);
            Vec3 flat = ahead.yRot((float) (turn * Mth.DEG_TO_RAD)).scale(r);
            double x = getX() + flat.x, z = getZ() + flat.z;
            BlockPos.MutableBlockPos p = BlockPos.containing(x, getY() + 1.0, z).mutable();
            for (int k = 0; k < GROUND_SEARCH; k++, p.move(Direction.DOWN)) {
                BlockPos below = p.below();
                VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
                if (floor.isEmpty()) continue;
                double y = below.getY() + floor.max(Direction.Axis.Y);
                if (!level.getFluidState(p).isEmpty() || !level.isPositionEntityTicking(p)) break;
                AABB box = type.getDimensions().scale(BROOD_SCALE).makeBoundingBox(x, y, z);
                if (level.noCollision(box) && !box.intersects(getBoundingBox())
                        && level.getEntitiesOfClass(WeeplingBroodmother.class, box).isEmpty()) return new Vec3(x, y, z);
                break;
            }
        }
        return null;
    }

    private @Nullable WeeplingBroodmother makeSac(ServerLevel level, Vec3 at) {
        if (!(getType().create(level, EntitySpawnReason.MOB_SUMMONED) instanceof WeeplingBroodmother sac)) return null;
        sac.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
        sac.becomeSac(this);
        if (!level.addFreshEntity(sac)) return null;
        brood.add(sac);
        return sac;
    }

    /** Counts a popped sac of hers; a clutch popped whole, none of it hatched, makes her grieve. */
    private void onSacPopped(WeeplingBroodmother sac, @Nullable LivingEntity by) {
        brood.remove(sac);
        if (!isAlive() || sac.clutchOf != clutch || clutchLaid == 0) return;
        clutchPopped++;
        if (clutchPopped < clutchLaid || clutchHatched > 0) return;
        clutchLaid = 0;                                                        // grieved once per clutch
        if (!(level() instanceof ServerLevel level) || !MechanicEvent.post(this, "grieve", by)) return;
        untilLay += GRIEF_TICKS;
        double y = getY() + getBbHeight() * 0.7;
        level.sendParticles(ICHOR, getX(), y, getZ(), 18, 0.5, 0.3, 0.5, 0.04);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), y, getZ(), 12, 0.6, 0.3, 0.6, 0.02);
        level.playSound(null, getX(), y, getZ(), ModSounds.WEEPLING_BROODMOTHER_GRIEVE.get(), getSoundSource(), 1.2F, 0.9F);
    }

    private void adopt(WeeplingBroodmother b) {
        if (!brood.contains(b)) brood.add(b);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        switch (form()) {
            case MOTHER -> {
                if (anchored || !core().idle()) {                              // she stands to squat and lay
                    getNavigation().stop();
                    setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
                }
            }
            case SAC -> {
                getNavigation().stop();
                setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
            }
            case WEEPLING -> {
                // it goes for its mother's target when it has none of its own
                if (!valid(getTarget()) && mother != null && valid(mother.getTarget())) setTarget(mother.getTarget());
            }
        }
    }

    /** Her unhatched sacs burst the moment she dies; the weeplings she leaves behind start to wither. */
    @Override
    public void die(DamageSource source) {
        if (form() != Form.MOTHER) {
            // the brood's death is no kill of a broodmother: a source with no killer keeps it out of the Field Guide
            super.die(damageSources().generic());
            return;
        }
        super.die(source);
        if (level() instanceof ServerLevel level) {
            for (WeeplingBroodmother b : List.copyOf(brood)) {
                if (b.form() == Form.SAC && !b.isRemoved()) b.pop(level, null);
            }
            brood.clear();
        }
    }

    // ---- the brood ---------------------------------------------------------------------------------------------------

    /** Makes this fresh entity one of {@code mom}'s sacs: her look, 1 health, nothing to hit with, rooted. */
    private void becomeSac(WeeplingBroodmother mom) {
        core().setLook(mom.look().isEmpty() ? "base" : mom.look(), false);
        core().setFlag(FLAG_SAC, true);
        core().setFlag(FLAG_WEEPLING, false);
        setBase(Attributes.MAX_HEALTH, SAC_HEALTH);
        setBase(Attributes.ATTACK_DAMAGE, 0.0);
        setBase(Attributes.ARMOR, 0.0);
        setBase(Attributes.MOVEMENT_SPEED, 0.0);
        setHealth(SAC_HEALTH);
        motherId = mom.getUUID();
        mother = mom;
        clutchOf = mom.clutch;
        age = 0;
        refreshDimensions();
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private void sacTick(ServerLevel level) {
        age++;
        core().setStage(Math.min(15, age * 16 / HATCH_TICKS));
        if (motherAlive(level)) {
            orphaned = 0;
        } else if (++orphaned >= ORPHAN_GRACE) {
            pop(level, null);
            return;
        }
        if (tickCount % 10 == 0) level.sendParticles(SAC_GLOW, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0.12, 0.1, 0.12, 0.0);
        if (age >= HATCH_TICKS) hatch(level);
    }

    /**
     * The sac hatches: a weepling with 6 health, 2 attack and its quick legs, set on its mother's target. A vetoed
     * "hatch" leaves a shrivelled sac that is gone. Public as a test hook.
     */
    public void hatch(ServerLevel level) {
        if (form() != Form.SAC) return;
        LivingEntity target = mother != null && valid(mother.getTarget()) ? mother.getTarget() : null;
        if (!MechanicEvent.post(this, "hatch", target)) {
            level.sendParticles(SAC_DUST, getX(), getY() + 0.2, getZ(), 6, 0.2, 0.1, 0.2, 0.0);
            discard();
            return;
        }
        core().setFlag(FLAG_SAC, false);
        core().setFlag(FLAG_WEEPLING, true);
        core().setStage(0);
        setBase(Attributes.MAX_HEALTH, WEEPLING_HEALTH);
        setBase(Attributes.ATTACK_DAMAGE, WEEPLING_ATTACK);
        setBase(Attributes.MOVEMENT_SPEED, WEEPLING_SPEED);
        setHealth(WEEPLING_HEALTH);
        refreshDimensions();
        if (mother != null) mother.clutchHatched += mother.clutch == clutchOf ? 1 : 0;
        if (target != null) setTarget(target);
        double y = getY() + 0.3;
        level.sendParticles(ICHOR, getX(), y, getZ(), 16, 0.25, 0.2, 0.25, 0.05);
        level.sendParticles(SAC_DUST, getX(), y, getZ(), 12, 0.25, 0.2, 0.25, 0.03);
        level.playSound(null, getX(), y, getZ(), ModSounds.WEEPLING_BROODMOTHER_HATCH.get(), getSoundSource(), 1.0F, 1.0F);
    }

    private void weeplingTick(ServerLevel level) {
        if (motherAlive(level)) {
            orphaned = 0;
        } else if (++orphaned >= ORPHAN_TICKS) {
            wither(level);
            return;
        }
        if (tickCount % 16 == 0) {                                               // red tears
            level.sendParticles(ICHOR, getX(), getY() + getBbHeight() * 0.7, getZ(), 1, 0.15, 0.05, 0.15, 0.0);
        }
    }

    /** Whether its mother lives; finds her again by id after a load, and she takes it back into her count. */
    private boolean motherAlive(ServerLevel level) {
        if (mother != null && mother.isAlive() && !mother.isRemoved()) return true;
        mother = null;
        if (motherId != null && level.getEntity(motherId) instanceof WeeplingBroodmother m && m.isAlive() && m.form() == Form.MOTHER) {
            mother = m;
            m.adopt(this);
            return true;
        }
        return false;
    }

    /** An orphaned weepling shrivels into ichor and is gone: no loot, no experience. A vetoed "wither" waits again. */
    private void wither(ServerLevel level) {
        orphaned = 0;
        if (!MechanicEvent.post(this, "wither", null)) return;
        double y = getY() + getBbHeight() * 0.5;
        level.sendParticles(ICHOR, getX(), y, getZ(), 14, 0.2, 0.15, 0.2, 0.02);
        level.sendParticles(ParticleTypes.SMOKE, getX(), y, getZ(), 6, 0.15, 0.1, 0.15, 0.01);
        level.playSound(null, getX(), y, getZ(), ModSounds.WEEPLING_BROODMOTHER_WITHER.get(), getSoundSource(), 0.9F, 1.0F);
        discard();
    }

    /** A sac pops: pale membrane and ichor burst, a wet pop, and it is gone without a death. */
    private void pop(ServerLevel level, @Nullable LivingEntity by) {
        double y = getY() + getBbHeight() * 0.5;
        level.sendParticles(SAC_DUST, getX(), y, getZ(), 12, 0.2, 0.15, 0.2, 0.03);
        level.sendParticles(ICHOR, getX(), y, getZ(), 12, 0.2, 0.15, 0.2, 0.04);
        level.sendParticles(SAC_GLOW, getX(), y, getZ(), 6, 0.15, 0.15, 0.15, 0.02);
        level.playSound(null, getX(), y, getZ(), ModSounds.WEEPLING_BROODMOTHER_POP.get(), getSoundSource(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        discard();
        if (mother != null) mother.onSacPopped(this, by);
    }

    /**
     * The counter: any hit that would hurt a sac pops it (its 1 health). It pops rather than dies, so nothing drops and
     * no death is counted. A vetoed "pop" leaves the sac whole.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (form() != Form.SAC) return super.hurtServer(level, source, amount);
        if (isRemoved() || amount <= 0.0F || isInvulnerableTo(level, source)) return false;
        LivingEntity by = source.getEntity() instanceof LivingEntity l ? l : null;
        if (!MechanicEvent.post(this, "pop", by)) return false;
        pop(level, by);
        return true;
    }

    /**
     * The brood stays as long as its mother does: while she is kept from despawning (named, or kept by whatever placed
     * her), so are her sacs and weeplings. Otherwise they despawn like any monster, and with her gone they burst or
     * wither on their own.
     */
    @Override
    public boolean requiresCustomPersistence() {
        WeeplingBroodmother mom = mother;
        if (form() == Form.MOTHER || mom == null || !mom.isAlive()) return super.requiresCustomPersistence();
        return mom.isPersistenceRequired() || mom.requiresCustomPersistence();
    }

    /** The brood carries nothing worth taking. */
    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return form() == Form.MOTHER && super.shouldDropLoot(level);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return switch (form()) {
            case MOTHER -> super.getBaseExperienceReward(level);
            case SAC -> 0;
            case WEEPLING -> WEEPLING_XP;
        };
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Brood", form().ordinal());
        if (motherId != null) output.putString("Mother", motherId.toString());
        output.putInt("BroodAge", age);
        output.putInt("Clutch", clutchOf);
    }

    /** The brood keeps its form, its age and its mother's id; it finds her again once both are loaded. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int f = input.getIntOr("Brood", 0);
        core().setFlag(FLAG_SAC, f == Form.SAC.ordinal());
        core().setFlag(FLAG_WEEPLING, f == Form.WEEPLING.ordinal());
        try {
            String id = input.getStringOr("Mother", "");
            motherId = id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException badId) {
            motherId = null;
        }
        age = Math.max(0, input.getIntOr("BroodAge", 0));
        clutchOf = input.getIntOr("Clutch", 0);
        refreshDimensions();
    }

    // ---- helpers and test hooks --------------------------------------------------------------------------------------

    private void face(Vec3 at) {
        Vec3 d = at.subtract(position());
        if (d.horizontalDistanceSqr() < 1.0E-4) return;
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Her live sacs and weeplings. */
    public List<WeeplingBroodmother> brood() {
        brood.removeIf(b -> b.isRemoved() || !b.isAlive());
        return List.copyOf(brood);
    }

    /** A brood member's mother, while she lives (server only). */
    public @Nullable WeeplingBroodmother mother() {
        return mother;
    }

    /** Ticks since a sac or weepling was laid. */
    public int age() {
        return age;
    }

    /** Ticks a weepling or sac has gone without a living mother. */
    public int orphaned() {
        return orphaned;
    }

    /** Ticks until she may lay again. */
    public int untilLay() {
        return untilLay;
    }

    /** Sets the ticks until she may lay again. Public as a test hook. */
    public void setUntilLay(int ticks) {
        untilLay = Math.max(0, ticks);
    }

    /** Test hook: she holds still where she is; her phase machine and her laying still run. */
    public void stayPut() {
        anchored = true;
        if (stroll != null) goalSelector.removeGoal(stroll);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return switch (form()) {
            case MOTHER -> core().idle() ? ModSounds.WEEPLING_BROODMOTHER_AMBIENT.get() : null;
            case SAC -> null;
            case WEEPLING -> ModSounds.WEEPLING_BROODMOTHER_WEEPLING_AMBIENT.get();
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return form() == Form.MOTHER ? ModSounds.WEEPLING_BROODMOTHER_HURT.get() : ModSounds.WEEPLING_BROODMOTHER_WEEPLING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return form() == Form.MOTHER ? ModSounds.WEEPLING_BROODMOTHER_DEATH.get() : ModSounds.WEEPLING_BROODMOTHER_WEEPLING_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return form() == Form.MOTHER ? 0.9F + random.nextFloat() * 0.1F : 1.05F + random.nextFloat() * 0.2F;
    }
}
