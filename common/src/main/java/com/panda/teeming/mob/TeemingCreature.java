package com.panda.teeming.mob;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * What every Teeming mob is, whichever vanilla class it extends. The renderers and the shared GameTests read it, and
 * its defaults give each mob the phase helpers.
 */
public interface TeemingCreature {
    MobCore core();

    /** Server: a timed phase ran out. The mob picks what comes next (usually another phase, or {@code core().end()}). */
    void onPhaseEnd(Phase ended);

    default Phase phase() {
        return core().phase();
    }

    /** Server: see {@link MobCore#start}. Public so GameTests can start a step directly. */
    default boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        return core().start(phase, length, target);
    }

    default float phaseProgress(float partialTick) {
        return core().progress(partialTick);
    }

    default String look() {
        return core().look();
    }

    /** The roster id, such as {@code thornhorn}. */
    default String mobId() {
        return core().mobId();
    }

    /** How many visual stages ({@link MobCore#stage}) the mob shows, such as heat glow; 1 for none. The harness poses each. */
    default int visualStages() {
        return 1;
    }
}
