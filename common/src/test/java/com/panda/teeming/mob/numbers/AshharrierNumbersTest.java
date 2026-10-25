package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Ashharrier;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Ashharrier's numbers against its roster row, written out as plain values: HP 24, Atk 4, Arm 0, 1.4 x 0.8; the cry
 * lasts 16 t; it grabs and lifts its prey 8 to 12 blocks over 40 t, then drops it; a shield raised into the stoop stuns
 * it for 40 t; it lives in the basalt deltas, weight 10, alone. The GameTests check the numbers are applied; this checks
 * they are the right numbers.
 */
class AshharrierNumbersTest {
    @Test
    void mechanic() {
        assertEquals(16, Ashharrier.TELL_TICKS);
        assertEquals(8.0, Ashharrier.LIFT_MIN);
        assertEquals(12.0, Ashharrier.LIFT_MAX);
        assertEquals(40, Ashharrier.LIFT_TICKS);
        assertEquals(40, Ashharrier.STUN_TICKS);
        assertEquals(5.0, Ashharrier.MIN_HEIGHT);
        assertEquals(14.0, Ashharrier.MAX_HEIGHT);
        assertEquals(8.0, Ashharrier.DIVE_REACH);
        assertEquals(1.0F, Ashharrier.MAX_PREY_WIDTH);
        assertEquals(2.0F, Ashharrier.MAX_PREY_HEIGHT);
        assertEquals(15, Ashharrier.ROOM);
        assertEquals(100, Ashharrier.COOLDOWN);
    }

    @Test
    void room() {
        // the open sky it asks for covers the highest lift, a player's height and its own body over them
        assertTrue(Ashharrier.ROOM >= Ashharrier.LIFT_MAX + 1.8 + 0.8);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("ashharrier");
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(24.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(1.4F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(16, row.tellTicks());
        assertEquals(10, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("cinder_harrier", row.secondLook());
        assertEquals("ember_wing", row.material());
        assertEquals(RosterStats.Place.GROUND, row.place());
    }
}
