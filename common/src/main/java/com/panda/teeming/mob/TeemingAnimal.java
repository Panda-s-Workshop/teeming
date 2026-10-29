package com.panda.teeming.mob;

import com.panda.teeming.fieldguide.FieldGuide;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Base of the passive Teeming mobs (grazers, the Magma Tortoise, the Driftsong): a {@link PathfinderMob} with
 * the shared {@link MobCore}. Neither tameable nor breedable.
 */
public abstract class TeemingAnimal extends PathfinderMob implements TeemingCreature {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(TeemingAnimal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_LOOK = SynchedEntityData.defineId(TeemingAnimal.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(TeemingAnimal.class, EntityDataSerializers.INT);

    private final MobCore core;

    /** {@code phases} is the mob's phase enum's {@code values()}; the first constant is idle. */
    protected TeemingAnimal(EntityType<? extends TeemingAnimal> type, Level level, Phase[] phases) {
        super(type, level);
        this.core = new MobCore(this, phases, DATA_PHASE, DATA_LOOK, DATA_FLAGS);
    }

    /** Herds stay like vanilla animals do: creature-category spawns are placed once, at chunk generation. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public final MobCore core() {
        return core;
    }

    /** Default: a phase that runs out returns the mob to idle. */
    @Override
    public void onPhaseEnd(Phase ended) {
        core.end();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        MobCore.define(builder, DATA_PHASE, DATA_LOOK, DATA_FLAGS);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (core != null) core.onSynced(key); // null while the Entity constructor defines the data
    }

    /** A held mob ({@link #pin()}) is moved only as far as its hold allows and is never pushed or knocked back. */
    @Override
    public void move(MoverType type, Vec3 delta) {
        super.move(type, pin().limit(delta));
    }

    @Override
    public void push(double x, double y, double z) {
        if (!pin().held()) super.push(x, y, z);
    }

    @Override
    public boolean isPushable() {
        return !pin().held() && super.isPushable();
    }

    @Override
    public void knockback(double strength, double x, double z, DamageSource source, float damage, boolean flag) {
        if (!pin().held()) super.knockback(strength, x, z, source, damage, flag);
    }

    @Override
    public void tick() {
        super.tick();
        if (core.look().isEmpty() && !level().isClientSide()) core.pickLook(level()); // spawns that skip finalizeSpawn
        Phase ended = core.tick();
        // a mob that has died finishes nothing it started: no blow, spit or lift lands from the death animation
        if (ended != null && isDeadOrDying()) core.end();
        else if (ended != null) onPhaseEnd(ended);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        core.pickLook(level);
        return result;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        FieldGuide.onKilled(this, source);
    }

    /** Over the void, a player's kill leaves its drops at the player's feet ({@link VoidDrops}). */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        VoidDrops.drop(level, this, source, () -> super.dropAllDeathLoot(level, source));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Look", core.look());
    }

    /** A reload mid-mechanic ends the mechanic; the look survives. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String look = input.getStringOr("Look", "");
        if (!look.isEmpty()) core.setLook(look, false);
    }
}
