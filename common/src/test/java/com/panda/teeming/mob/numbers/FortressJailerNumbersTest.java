package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.FortressJailer;
import org.junit.jupiter.api.Test;

/**
 * Fortress Jailer's mechanic numbers against the design, written out as plain values: tell 16 t (it winds its chain);
 * the chain reaches 12 blocks and flies straight; a hooked target is dragged 0.8 blocks a tick; one throw every 120 t.
 * The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class FortressJailerNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(16, FortressJailer.TELL_TICKS);
        assertEquals(12.0, FortressJailer.RANGE);
        assertEquals(0.8, FortressJailer.PULL_SPEED);
        assertEquals(120, FortressJailer.THROW_EVERY);
    }

    @Test
    void houseRules() {
        // a tell lasts at least 8 ticks; the whole cycle (wind, the longest throw, the longest drag, the strike or the
        // longest reel) fits inside one 120-tick interval
        assertTrue(FortressJailer.TELL_TICKS >= 8);
        int reel = (int) Math.ceil(FortressJailer.RANGE / FortressJailer.REEL_SPEED);
        assertTrue(FortressJailer.TELL_TICKS + FortressJailer.THROW_TICKS + FortressJailer.PULL_TICKS
                + Math.max(FortressJailer.STRIKE_TICKS, reel) < FortressJailer.THROW_EVERY);
        // the throw has time to fly its whole reach, and the drag time to cover it
        assertTrue(FortressJailer.THROW_TICKS * FortressJailer.CHAIN_SPEED >= FortressJailer.RANGE);
        assertTrue(FortressJailer.PULL_TICKS * FortressJailer.PULL_SPEED >= FortressJailer.RANGE);
        assertTrue(FortressJailer.MIN_RANGE < FortressJailer.RANGE);
        // the fist lands inside the strike, and reaches at least as far as the drag delivers the victim
        assertTrue(FortressJailer.STRIKE_HIT_TICK < FortressJailer.STRIKE_TICKS);
        assertTrue(FortressJailer.STRIKE_HIT_REACH > FortressJailer.STRIKE_REACH);
    }
}
