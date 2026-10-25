package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hollow Mourner: a robed mourner with an empty mask for a face, who walks the Overworld at night and brings the dead
 * back. It remembers every undead mob that dies within 8 blocks of it (what it was and where it fell) for 30 s. Every
 * 200 ticks, while it remembers one, it chants for 3 s (tell: arms raised, souls rising from the grave, a low choir),
 * then raises it: a fresh mob of the same kind stands up where the old one fell and joins its fight. The raised dead
 * carry nothing worth taking: they drop no loot, no equipment and no experience.
 *
 * <p>Counter: any hit staggers it for 2 s, and a hit during the chant breaks it, so nothing rises; the grave is still
 * remembered, and it chants again once it recovers. The long chant is there to be interrupted. It never raises players
 * or anything that isn't undead, and never a boss.
 */
public class HollowMourner extends TeemingMonster {
    /** IDLE, the chant (tell), the raising, the stagger after a hit (counter landed). */
    public enum Step implements Phase { IDLE, CHANT, RAISE, STAGGER }

    /** 3 s of chanting before the dead rise: long enough to walk up and interrupt it. */
    public static final int TELL_TICKS = 60;
    /** Ticks from one raising to the next, at the soonest. */
    public static final int CHANT_EVERY = 200;
    /** It remembers undead that die this close to it. */
    public static final double GRAVE_RANGE = 8.0;
    /** And remembers each for 30 s. */
    public static final int GRAVE_TICKS = 600;
    /** Any hit staggers it for 2 s. */
    public static final int STAGGER_TICKS = 40;
    /** The raising itself: the grave erupts and the dead stand up. */
    public static final int RAISE_TICKS = 12;
    /** It remembers at most this many graves at once; a new one pushes out the oldest. */
    public static final int MEMORY = 8;
    /** The mark every raised mob carries. */
    public static final String RAISED_TAG = "teeming_raised";

    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));
    public static final int ASH_COLOR = 0xA8A29A;
    public static final int BOG_COLOR = 0x7E8A5A;
    private static final DustParticleOptions ASH = new DustParticleOptions(ASH_COLOR, 1.0F);
    private static final DustParticleOptions BOG = new DustParticleOptions(BOG_COLOR, 1.0F);

    /** One remembered death: who fell (by id, so a corpse is counted once), what it was, where, and when. */
    public record Grave(UUID id, EntityType<?> type, Vec3 pos, boolean baby, long diedAt) {}

    private final List<Grave> graves = new ArrayList<>();
    /** The grave the current chant is for: picked when the chant starts. */
    private @Nullable Grave chanting;
    /** Ticks until it may raise again. A fresh mourner may chant as soon as it remembers a grave. */
    private int untilChant;
    /** Set in registerGoals, which runs inside the Mob constructor, so it has no initializer. */
    private @Nullable WaterAvoidingRandomStrollGoal stroll;

    public HollowMourner(EntityType<? extends HollowMourner> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("hollow_mourner", 0.23);
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
        stroll = new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        };
        goalSelector.addGoal(5, stroll);
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- remembering the dead -----------------------------------------------------------------------------------------

    /** Whether a kind of mob can be raised: undead, and neither a player nor a boss. */
    public static boolean raisable(EntityType<?> type) {
        if (type == EntityTypes.PLAYER || type == EntityTypes.WITHER) return false;
        var holder = type.builtInRegistryHolder();
        return holder.is(EntityTypeTags.UNDEAD) && !holder.is(BOSSES) && type.canSerialize();
    }

    /** Notes every undead mob dying within reach that it hasn't noted yet, and forgets graves older than 30 s. */
    private void remember(ServerLevel level) {
        long now = level.getGameTime();
        graves.removeIf(g -> now - g.diedAt() > GRAVE_TICKS);
        for (Mob dead : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(GRAVE_RANGE),
                e -> e != this && e.isDeadOrDying() && raisable(e.getType()) && e.distanceTo(this) <= GRAVE_RANGE)) {
            UUID id = dead.getUUID();
            if (graves.stream().anyMatch(g -> g.id().equals(id))) continue;
            remember(new Grave(id, dead.getType(), dead.position(), dead.isBaby(), now));
        }
    }

    /** Adds a grave, pushing out the oldest when its memory is full. Public as a test hook. */
    public void remember(Grave grave) {
        while (graves.size() >= MEMORY) graves.remove(oldest());
        graves.add(grave);
    }

    private int oldest() {
        int at = 0;
        for (int i = 1; i < graves.size(); i++) if (graves.get(i).diedAt() < graves.get(at).diedAt()) at = i;
        return at;
    }

    /** The graves it remembers, oldest first. */
    public List<Grave> graves() {
        List<Grave> out = new ArrayList<>(graves);
        out.sort((a, b) -> Long.compare(a.diedAt(), b.diedAt()));
        return out;
    }

    /** The freshest remembered grave with room for its mob to stand, or null. */
    private @Nullable Grave pick(ServerLevel level) {
        Grave best = null;
        for (Grave g : graves) {
            if (!level.noCollision(g.type().getSpawnAABB(g.pos()))) continue;
            if (best == null || g.diedAt() > best.diedAt()) best = g;
        }
        return best;
    }

    // ---- the chant and the raising ------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide() && untilChant > 0) untilChant--;
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        remember(level);
        if (core().in(Step.CHANT)) {
            chantTick(level);
        } else if (core().in(Step.STAGGER)) {
            if (tickCount % 4 == 0) {
                level.sendParticles(dust(), getX(), getY() + getBbHeight() * 0.9, getZ(), 2, 0.25, 0.15, 0.25, 0.0);
            }
        } else if (core().idle() && !isNoAi() && untilChant <= TELL_TICKS && !graves.isEmpty()) {
            chant(getTarget());
        }
    }

    /** Souls rise from the grave it chants over, ash drifts from its raised hands, and the choir swells. */
    private void chantTick(ServerLevel level) {
        int t = core().elapsed();
        Grave g = chanting;
        if (g != null) {
            face(g.pos());
            if (t % 2 == 0) {
                double r = 0.25 + 0.35 * t / (double) TELL_TICKS;
                level.sendParticles(ParticleTypes.SOUL, g.pos().x, g.pos().y + 0.1, g.pos().z, 1, r, 0.05, r, 0.02);
            }
            if (t % 3 == 0) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, g.pos().x, g.pos().y + 0.3, g.pos().z, 1, 0.3, 0.1, 0.3, 0.01);
            }
            if (t % 6 == 0) {
                BlockPos below = BlockPos.containing(g.pos()).below();
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)),
                        g.pos().x, g.pos().y + 0.05, g.pos().z, 4, 0.3, 0.02, 0.3, 0.05);
            }
        }
        if (t % 3 == 0) {                                                       // ash rising from its raised hands
            double yaw = Math.toRadians(yBodyRot);
            for (int side = -1; side <= 1; side += 2) {
                double x = getX() + Math.cos(yaw) * 0.45 * side, z = getZ() + Math.sin(yaw) * 0.45 * side;
                level.sendParticles(dust(), x, getY() + getBbHeight() + 0.15, z, 2, 0.08, 0.08, 0.08, 0.0);
            }
        }
        if (t > 0 && t % 20 == 0) playSound(ModSounds.HOLLOW_MOURNER_CHANT.get(), 0.9F, 0.8F + 0.06F * (t / 20));
    }

    /**
     * Starts the chant over the freshest grave it remembers, if it is free to; true if it did. Ignores the time since
     * the last raising. Public as a test hook.
     */
    public boolean chant(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel level) || !core().idle()) return false;
        Grave g = pick(level);
        if (g == null) return false;
        if (!startPhase(Step.CHANT, TELL_TICKS, target)) return false;
        chanting = g;
        face(g.pos());
        playSound(ModSounds.HOLLOW_MOURNER_CHANT.get(), 1.0F, 0.75F);
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().idle()) return;
        getNavigation().stop();                                                // it stands to chant, raise or reel
        if (!core().in(Step.STAGGER)) setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.CHANT && level() instanceof ServerLevel level) {
            Grave g = chanting;
            chanting = null;
            untilChant = CHANT_EVERY;
            if (g != null && startPhase(Step.RAISE, RAISE_TICKS, core().target())) raise(level, g);
            else core().end();
        } else {
            chanting = null;
            core().end();
        }
    }

    /**
     * Raises the dead of one grave: a fresh mob of that kind (a baby if the dead one was) where it fell, marked as
     * raised, with no loot, no equipment drops and no experience, set on the mourner's target. The grave is used up.
     * Returns the mob, or null if there was no room or it couldn't be made. Public as a test hook.
     */
    public @Nullable Mob raise(ServerLevel level, Grave g) {
        graves.removeIf(x -> x.id().equals(g.id()));
        if (!level.noCollision(g.type().getSpawnAABB(g.pos()))) return null;
        if (!(g.type().create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob risen)) return null;
        risen.snapTo(g.pos().x, g.pos().y, g.pos().z, random.nextFloat() * 360.0F, 0.0F);
        risen.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(g.pos())), EntitySpawnReason.MOB_SUMMONED, null);
        Entity mount = risen.getVehicle();                                    // no jockey mounts: just the dead, as they fell
        if (mount != null) {
            risen.stopRiding();
            mount.discard();
        }
        risen.setBaby(g.baby());
        risen.addTag(RAISED_TAG);
        for (EquipmentSlot slot : EquipmentSlot.VALUES) risen.setDropChance(slot, 0.0F);
        risen.skipDropExperience();
        noLoot(level, risen);
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))) risen.setTarget(target);
        level.addFreshEntityWithPassengers(risen);
        Vec3 p = g.pos();
        level.sendParticles(ParticleTypes.SOUL, p.x, p.y + 0.6, p.z, 24, 0.35, 0.6, 0.35, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.3, p.z, 10, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(BlockPos.containing(p).below())),
                p.x, p.y + 0.1, p.z, 30, 0.4, 0.1, 0.4, 0.15);
        level.sendParticles(dust(), getX(), getY() + getBbHeight(), getZ(), 12, 0.4, 0.2, 0.4, 0.02);
        level.playSound(null, p.x, p.y, p.z, ModSounds.HOLLOW_MOURNER_RAISE.get(), getSoundSource(), 1.2F, 1.0F);
        return risen;
    }

    /** Points the raised mob's death loot at the empty table, the same way {@code /data merge} would, so it saves. */
    private static void noLoot(ServerLevel level, Mob mob) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        mob.saveWithoutId(out);
        CompoundTag tag = out.buildResult();
        tag.putString("DeathLootTable", "minecraft:empty");
        UUID id = mob.getUUID();
        mob.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        mob.setUUID(id);
    }

    // ---- the counter: any hit staggers it ---------------------------------------------------------------------------

    /**
     * Any hit from something (a blow, an arrow, a thrown potion) staggers it for 40 ticks, from that hit: a chant in
     * progress breaks and nothing rises. Falls, fire and the like don't count as hits.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && (source.getEntity() != null || source.getDirectEntity() != null)) {
            stagger(level, source.getEntity() instanceof LivingEntity by ? by : null);
        }
        return hurt;
    }

    private void stagger(ServerLevel level, @Nullable LivingEntity by) {
        boolean broke = core().in(Step.CHANT);
        if (!startPhase(Step.STAGGER, STAGGER_TICKS, by)) return;               // a vetoed stagger leaves it as it was
        chanting = null;
        double y = getY() + getBbHeight() * 0.85;
        level.sendParticles(ParticleTypes.POOF, getX(), y, getZ(), broke ? 14 : 6, 0.3, 0.25, 0.3, 0.03);
        level.sendParticles(dust(), getX(), y, getZ(), broke ? 16 : 6, 0.4, 0.3, 0.4, 0.02);
        if (broke) level.sendParticles(ParticleTypes.SOUL, getX(), y + 0.4, getZ(), 10, 0.5, 0.2, 0.5, 0.06);
        level.playSound(null, getX(), y, getZ(), ModSounds.HOLLOW_MOURNER_STAGGER.get(), getSoundSource(), broke ? 1.2F : 0.8F, broke ? 0.9F : 1.1F);
    }

    // ---- helpers and test hooks ---------------------------------------------------------------------------------------

    private void face(Vec3 at) {
        Vec3 d = at.subtract(position());
        if (d.horizontalDistanceSqr() < 1.0E-4) return;
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private DustParticleOptions dust() {
        return "bog_mourner".equals(look()) ? BOG : ASH;
    }

    /** The grave the current chant is for, or null. */
    public @Nullable Grave chanting() {
        return chanting;
    }

    /** Ticks until it may raise again. */
    public int untilChant() {
        return untilChant;
    }

    /** Sets the ticks until it may raise again. Public as a test hook. */
    public void setUntilChant(int ticks) {
        untilChant = Math.max(0, ticks);
    }

    /** Test hook: stops it wandering, so only its fighting moves it. */
    public void stayPut() {
        if (stroll != null) goalSelector.removeGoal(stroll);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.HOLLOW_MOURNER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HOLLOW_MOURNER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HOLLOW_MOURNER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + random.nextFloat() * 0.1F;
    }
}
