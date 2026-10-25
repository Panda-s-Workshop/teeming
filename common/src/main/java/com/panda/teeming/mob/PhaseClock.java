package com.panda.teeming.mob;

/**
 * The timing half of a mob's phase machine, with no Minecraft types so it can be unit-tested. A phase is an
 * ordinal (0 = idle) and a length in ticks; a length of 0 means "until something ends it".
 *
 * <p>A phase is timed from the game time it started, not by counting ticks, so it lasts exactly its length wherever
 * in the tick it was started: from the mob's own AI step, from a hit dealt during another entity's tick, or from an
 * event. Started at game time {@code t}, it runs out at {@code t + length}.
 */
public final class PhaseClock {
    private int phase;
    private int length;
    private long startedAt;

    public int phase() {
        return phase;
    }

    public int length() {
        return length;
    }

    /** Whole ticks since the phase started; 0 when idle. */
    public int elapsed(long now) {
        if (phase == 0) return 0;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, now - startedAt));
    }

    public boolean idle() {
        return phase == 0;
    }

    /** Enters a phase at game time {@code now}. */
    public void start(int phase, int length, long now) {
        this.phase = phase;
        this.length = Math.max(0, length);
        this.startedAt = now;
    }

    /** Back to idle. */
    public void reset(long now) {
        start(0, 0, now);
    }

    /**
     * Called once a tick. Returns true once a timed phase has run its length (the caller then picks the next phase);
     * an open-ended or idle phase never runs out.
     */
    public boolean ranOut(long now) {
        return phase != 0 && length > 0 && elapsed(now) >= length;
    }

    /** 0..1 through a timed phase, smoothed by the partial tick; 0 when idle or open-ended. */
    public float progress(long now, float partialTick) {
        if (phase == 0 || length <= 0) return 0.0F;
        return Math.min(1.0F, (elapsed(now) + partialTick) / length);
    }
}
