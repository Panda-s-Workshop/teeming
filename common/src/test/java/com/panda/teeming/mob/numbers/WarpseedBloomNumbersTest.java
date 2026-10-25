package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.WarpseedBloom;
import org.junit.jupiter.api.Test;

/**
 * Warpseed Bloom's mechanic numbers against the design, written out as plain values: tell 12 t (the bulb swells);
 * seeds at range 16 for 3 damage, the hit thrown 3 to 6 blocks onto safe ground; a shield blocks. The GameTests check
 * the numbers are applied; this checks they are the right numbers.
 */
class WarpseedBloomNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(12, WarpseedBloom.TELL_TICKS);
        assertEquals(16.0, WarpseedBloom.RANGE);
        assertEquals(3.0F, WarpseedBloom.SEED_DAMAGE);
        assertEquals(3.0, WarpseedBloom.WARP_NEAR);
        assertEquals(6.0, WarpseedBloom.WARP_FAR);
    }

    @Test
    void chosenNumbers() {
        // not in the design row: the rhythm, the seed's speed and the torn bulb are this mob's own calls
        assertEquals(60, WarpseedBloom.SPIT_EVERY);
        assertEquals(0.5, WarpseedBloom.SEED_SPEED);
        assertEquals(100, WarpseedBloom.WILT_TICKS);
        assertEquals(3.0F, WarpseedBloom.REBOUND_DAMAGE);
    }

    @Test
    void houseRules() {
        // every attack that hurts or moves a player has a tell of at least 8 ticks; a punish window is 40 to 100 ticks
        assertTrue(WarpseedBloom.TELL_TICKS >= 8);
        assertTrue(WarpseedBloom.WILT_TICKS >= 40 && WarpseedBloom.WILT_TICKS <= 100);
        // the cycle has room for its tell and recoil, and a seed is spent before the next tell can start
        assertTrue(WarpseedBloom.SPIT_EVERY > WarpseedBloom.TELL_TICKS + WarpseedBloom.SPIT_TICKS);
        assertTrue(WarpseedBloom.RANGE / WarpseedBloom.SEED_SPEED < WarpseedBloom.SPIT_EVERY - WarpseedBloom.TELL_TICKS);
        assertTrue(WarpseedBloom.WARP_NEAR < WarpseedBloom.WARP_FAR);
    }
}
