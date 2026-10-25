package com.panda.teeming.mob;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The phase machine's timing: what the server's mechanics and the clients' animations both run on. */
class PhaseClockTest {
    @Test
    void aTimedPhaseRunsOutAfterItsLength() {
        PhaseClock clock = new PhaseClock();
        clock.start(1, 20, 1000);
        for (long t = 1000; t < 1020; t++) assertFalse(clock.ranOut(t), "still running at game time " + t);
        assertTrue(clock.ranOut(1020), "runs out 20 ticks after it started");
        assertEquals(20, clock.elapsed(1020));
    }

    @Test
    void itLastsItsLengthWhereverInTheTickItStarted() {
        // started at game time 50, whether by the mob's own AI step or by a hit during another entity's tick:
        // the end-of-tick check sees it on ticks 50..59 and not on 60
        PhaseClock clock = new PhaseClock();
        clock.start(1, 10, 50);
        int shown = 0;
        for (long t = 50; t < 80; t++) {
            if (clock.ranOut(t)) clock.reset(t);
            if (!clock.idle()) shown++;
        }
        assertEquals(10, shown);
    }

    @Test
    void idleAndOpenEndedPhasesNeverRunOut() {
        PhaseClock clock = new PhaseClock();
        for (long t = 0; t < 100; t++) assertFalse(clock.ranOut(t));
        assertTrue(clock.idle());
        assertEquals(0, clock.elapsed(500), "idle has no elapsed time");
        clock.start(2, 0, 100);
        for (long t = 100; t < 200; t++) assertFalse(clock.ranOut(t));
        assertEquals(0.0F, clock.progress(150, 0.5F), "an open-ended phase has no progress");
    }

    @Test
    void progressIsSmoothAndClamped() {
        PhaseClock clock = new PhaseClock();
        clock.start(1, 10, 0);
        assertEquals(0.05F, clock.progress(0, 0.5F), 1e-6);
        assertEquals(0.5F, clock.progress(5, 0.0F), 1e-6);
        assertEquals(1.0F, clock.progress(25, 0.9F), 1e-6);
    }

    @Test
    void restartingResetsTheClock() {
        PhaseClock clock = new PhaseClock();
        clock.start(1, 10, 0);
        assertEquals(7, clock.elapsed(7));
        clock.start(1, 10, 7);
        assertEquals(0, clock.elapsed(7));
        clock.reset(8);
        assertTrue(clock.idle());
    }

    @Test
    void packedPhaseKeepsOrdinalAndLength() {
        int packed = MobCore.pack(5, 3, 1234);
        assertEquals(5, packed & 0xFF);
        assertEquals(3, packed >>> 8 & 0xF);
        assertEquals(1234, packed >>> 12);
        assertNotEquals(MobCore.pack(5, 3, 20), MobCore.pack(5, 4, 20), "a restart of the same phase changes the synced value");
    }
}
