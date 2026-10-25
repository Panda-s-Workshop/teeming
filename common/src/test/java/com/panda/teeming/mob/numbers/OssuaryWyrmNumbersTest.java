package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.nether.OssuaryWyrm;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Ossuary Wyrm's numbers against its roster row, written out as plain values: HP 50, Atk 8, Arm 4; a body of 8
 * segments of 0.8 each behind the skull; the ground rumbles for 20 t; it swims through soul sand and soul soil at
 * 0.3 b/t; a blow on its skull counts x2, on a vertebra x0.5; it spawns in soul sand valleys at weight 4, alone. The
 * GameTests check the numbers are applied; this checks they are the right numbers.
 */
class OssuaryWyrmNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, OssuaryWyrm.TELL_TICKS);
        assertEquals(0.3, OssuaryWyrm.SWIM_SPEED);
        assertEquals(2.0F, OssuaryWyrm.SKULL_DAMAGE);
        assertEquals(0.5F, OssuaryWyrm.SEGMENT_DAMAGE);
        assertEquals(8, OssuaryWyrm.SEGMENTS);
        assertEquals(0.8, OssuaryWyrm.SEGMENT_SIZE);
        assertEquals(16.0, OssuaryWyrm.HUNT_RANGE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("ossuary_wyrm");
        assertEquals(50.0F, row.health());
        assertEquals(8.0F, row.attack());
        assertEquals(4.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("bleached_wyrm", row.secondLook());
        assertEquals(RosterStats.Place.GROUND, row.place());
    }
}
