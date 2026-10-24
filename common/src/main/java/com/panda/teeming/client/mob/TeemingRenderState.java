package com.panda.teeming.client.mob;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What every Teeming model animates from: the look and the synced phase machine. */
public class TeemingRenderState extends LivingEntityRenderState {
    public String look = "base";
    /** The phase's ordinal (0 = idle) and 0..1 progress through it (0 for open-ended phases). */
    public int phase;
    public float progress;
    /** Ticks spent in the current phase, smoothed by the partial tick (for open-ended phases). */
    public float phaseTime;
    /** The mob's visual flags ({@code MobCore.flag}). */
    public int flags;
    /** The mob's visual stage ({@code MobCore.stage}): picks a staged glowmask. */
    public int stage;
    /** 0..1 progress of the melee swing. */
    public float attack;

    public boolean flag(int bit) {
        return (flags & 1 << bit) != 0;
    }
}
