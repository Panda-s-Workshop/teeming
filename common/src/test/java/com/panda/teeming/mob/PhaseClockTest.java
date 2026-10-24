package com.panda.teeming.mob;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The phase machine's timing: what the server's mechanics and the clients' animations both run on. */
class PhaseClockTest {
    @Test
    void aTimedPhaseRunsOutOnItsLastTick() {
        PhaseClock clock = new PhaseClock();
        clock.start(1, 20);
        for (int i = 1; i < 20; i++) assertFalse(clock.tick(), "still running at tick " + i);
        assertTrue(clock.tick(), "runs out on tick 20");
        assertEquals(20, clock.elapsed());
    }

    @Test
    void idleAndOpenEndedPhasesNeverRunOut() {
        PhaseClock clock = new PhaseClock();
        for (int i = 0; i < 100; i++) assertFalse(clock.tick());
        assertTrue(clock.idle());
        clock.start(2, 0);
        for (int i = 0; i < 100; i++) assertFalse(clock.tick());
        assertEquals(0.0F, clock.progress(0.5F), "an open-ended phase has no progress");
    }

    @Test
    void progressIsSmoothAndClamped() {
        PhaseClock clock = new PhaseClock();
        clock.start(1, 10);
        assertEquals(0.05F, clock.progress(0.5F), 1e-6);
        for (int i = 0; i < 5; i++) clock.tick();
        assertEquals(0.5F, clock.progress(0.0F), 1e-6);
        for (int i = 0; i < 20; i++) clock.tick();
        assertEquals(1.0F, clock.progress(0.9F), 1e-6);
    }

    @Test
    void restartingResetsTheClock() {
        PhaseClock clock = new PhaseClock();
        clock.start(1, 10);
        for (int i = 0; i < 7; i++) clock.tick();
        clock.start(1, 10);
        assertEquals(0, clock.elapsed());
        clock.reset();
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
