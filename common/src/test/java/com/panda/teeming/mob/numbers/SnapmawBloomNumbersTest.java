package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.overworld.SnapmawBloom;
import org.junit.jupiter.api.Test;

/**
 * Snapmaw Bloom's mechanic numbers against the design, written out as plain values: tell 10 t (jaws open); seed
 * spit range 14, 3 damage, every 40 t; snap at the next block for 1.5 x Atk; turns 45 degrees a second; hits from its
 * rear 120 degrees x2. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class SnapmawBloomNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(10, SnapmawBloom.TELL_TICKS);
        assertEquals(14.0, SnapmawBloom.RANGE);
        assertEquals(3.0F, SnapmawBloom.SEED_DAMAGE);
        assertEquals(40, SnapmawBloom.SPIT_EVERY);
        assertEquals(1.5F, SnapmawBloom.SNAP_HIT);
        assertEquals(45.0F, SnapmawBloom.TURN_RATE);
        assertEquals(2.25F, SnapmawBloom.TURN_PER_TICK);
        assertEquals(120.0F, SnapmawBloom.REAR_ARC);
        assertEquals(2.0F, SnapmawBloom.REAR_DAMAGE);
    }

    @Test
    void houseRules() {
        // a blow of 1.5 x Atk or more needs a tell of at least 10 ticks; the spit's cycle has room for its tell
        assertTrue(SnapmawBloom.TELL_TICKS >= 10);
        assertTrue(SnapmawBloom.SPIT_EVERY > SnapmawBloom.TELL_TICKS + SnapmawBloom.SPIT_TICKS);
    }
}
