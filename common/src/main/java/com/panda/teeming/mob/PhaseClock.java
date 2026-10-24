package com.panda.teeming.mob;

/**
 * The timing half of a mob's phase machine, with no Minecraft types so it can be unit-tested. A phase is an
 * ordinal (0 = idle) and a length in ticks; a length of 0 means "until something ends it".
 */
public final class PhaseClock {
    private int phase;
    private int length;
    private int elapsed;

    public int phase() {
        return phase;
    }

    public int length() {
        return length;
    }

    public int elapsed() {
        return elapsed;
    }

    public boolean idle() {
        return phase == 0;
    }

    /** Enters a phase from its first tick. */
    public void start(int phase, int length) {
        this.phase = phase;
        this.length = Math.max(0, length);
        this.elapsed = 0;
    }

    /** Back to idle. */
    public void reset() {
        start(0, 0);
    }

    /**
     * Advances one tick. Returns true on the tick a timed phase runs out (the caller then picks the next phase); an
     * open-ended or idle phase never runs out.
     */
    public boolean tick() {
        if (phase == 0) return false;
        elapsed++;
        return length > 0 && elapsed >= length;
    }

    /** 0..1 through a timed phase, smoothed by the partial tick; 0 when idle or open-ended. */
    public float progress(float partialTick) {
        if (phase == 0 || length <= 0) return 0.0F;
        return Math.min(1.0F, (elapsed + partialTick) / length);
    }
}
