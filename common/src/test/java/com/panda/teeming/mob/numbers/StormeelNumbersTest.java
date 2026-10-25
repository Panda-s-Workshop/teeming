package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.Stormeel;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Stormeel's numbers against its roster row, written out as plain values: HP 20, Atk 3, Arm 0, 0.6 x 0.5; glow
 * 30 t, then a pulse of radius 5 and 6 damage, only to things in water; every 120 t; in water. The GameTests check the
 * numbers are applied; this checks they are the right numbers.
 */
class StormeelNumbersTest {
    @Test
    void mechanic() {
        assertEquals(30, Stormeel.TELL_TICKS);
        assertEquals(5.0, Stormeel.PULSE_RADIUS);
        assertEquals(6.0F, Stormeel.PULSE_DAMAGE);
        assertEquals(120, Stormeel.PULSE_EVERY);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("stormeel");
        assertEquals(20.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(0.5F, row.height());
        assertEquals(30, row.tellTicks());
        assertEquals(RosterStats.Place.WATER, row.place());
    }
}
