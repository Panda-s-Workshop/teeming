package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.effect.NulledEffect;
import com.panda.teeming.mob.end.Nullcantor;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Nullcantor's numbers against its roster row, written out as plain values: HP 40, Atk 6, Arm 4, 0.7 x 2.0; its
 * ring always on, radius 8, violet, with a hum; main island (the spawner's rule) and End barrens, weight 3, groups of
 * 1; second look Pale Cantor; drops void ichor. The 10-tick swing tell, the 30-tick swing interval, the 160-tick shard
 * and the 2-tick grace of the status are its design. The GameTests check the numbers are applied; this checks they are
 * the right numbers.
 */
class NullcantorNumbersTest {
    @Test
    void ring() {
        assertEquals(8.0, Nullcantor.RING_RADIUS);
        assertEquals(2.0, Nullcantor.RING_BELOW);
        assertEquals(4.0, Nullcantor.RING_ABOVE);
        assertEquals(40, Nullcantor.HUM_EVERY);
        assertEquals(1.5F, Nullcantor.HUM_VOLUME);
        // heard 16 blocks x its volume away: well past the ring's edge
        assertTrue(16.0 * Nullcantor.HUM_VOLUME > Nullcantor.RING_RADIUS * 2);
        assertEquals(2, NulledEffect.GRACE);
    }

    @Test
    void swing() {
        // its blow is its attack (6), not 1.5x it: an 8-tick tell is the floor; it takes 10
        assertEquals(10, Nullcantor.TELL_TICKS);
        assertTrue(Nullcantor.TELL_TICKS >= 8);
        assertEquals(8, Nullcantor.STRIKE_TICKS);
        assertEquals(30, Nullcantor.STRIKE_EVERY);
        assertTrue(Nullcantor.TELL_TICKS + Nullcantor.STRIKE_TICKS < Nullcantor.STRIKE_EVERY, "a swing is over before the next may start");
    }

    @Test
    void collar() {
        assertEquals(12, Nullcantor.CRACK_TICKS);
        assertEquals(160, Nullcantor.SHARD_TICKS);
        assertTrue(Nullcantor.SHARD_TICKS <= 200, "nothing it does lasts over 200 ticks");
    }

    @Test
    void theRingIsVioletNotGold() {
        // weak-point gold is #ffd36b; the ring is violet: more blue than red, more red than green
        int c = Nullcantor.RING_VIOLET;
        int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
        assertTrue(b > r && r > g, String.format("violet %06x", c));
        assertFalse(c == 0xFFD36B);
        assertEquals(0x9B5CFF, c);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("nullcantor");
        assertEquals(40.0F, row.health());
        assertEquals(6.0F, row.attack());
        assertEquals(4.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(2.0F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(3, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("main_island", row.spawner());
        assertEquals("pale_cantor", row.secondLook());
        assertEquals("void_ichor", row.material());
    }
}
