package com.panda.teeming.mob;

import com.panda.teeming.fieldguide.FieldGuide;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Base of the hostile and neutral Teeming mobs: a vanilla {@link Monster} (so it blocks sleeping, prefers the
 * dark, despawns in Peaceful) with the shared {@link MobCore}.
 */
public abstract class TeemingMonster extends Monster implements TeemingCreature {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(TeemingMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_LOOK = SynchedEntityData.defineId(TeemingMonster.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(TeemingMonster.class, EntityDataSerializers.INT);

    private final MobCore core;

    /** {@code phases} is the mob's phase enum's {@code values()}; the first constant is idle. */
    protected TeemingMonster(EntityType<? extends TeemingMonster> type, Level level, Phase[] phases) {
        super(type, level);
        this.core = new MobCore(this, phases, DATA_PHASE, DATA_LOOK, DATA_FLAGS);
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

    @Override
    public void tick() {
        super.tick();
        if (core.look().isEmpty() && !level().isClientSide()) core.pickLook(level()); // spawns that skip finalizeSpawn
        Phase ended = core.tick();
        if (ended != null) onPhaseEnd(ended);
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
