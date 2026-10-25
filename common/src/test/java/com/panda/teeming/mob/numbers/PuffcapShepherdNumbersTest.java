package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.PuffcapShepherd;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Puffcap Shepherd's numbers against its roster row, written out as plain values: HP 16, Atk 2, Arm 0, 0.7 x 1.1;
 * a 16 t swell, then a puff of radius 6 every 160 t giving hostile mobs Regeneration I and Speed I for 100 t; it flees
 * players within 6; dark forest, weight 15, groups of 1; the Glowcap second look; spore sac. The GameTests check the
 * numbers are applied; this checks they are the right numbers.
 */
class PuffcapShepherdNumbersTest {
    @Test
    void mechanic() {
        assertEquals(16, PuffcapShepherd.TELL_TICKS);
        assertEquals(6.0, PuffcapShepherd.PUFF_RADIUS);
        assertEquals(160, PuffcapShepherd.PUFF_EVERY);
        assertEquals(100, PuffcapShepherd.BUFF_TICKS);
        assertEquals(0, PuffcapShepherd.REGENERATION_LEVEL);
        assertEquals(0, PuffcapShepherd.SPEED_LEVEL);
        assertEquals(6.0, PuffcapShepherd.FLEE_RANGE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("puffcap_shepherd");
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(16.0F, row.health());
        assertEquals(2.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(1.1F, row.height());
        assertEquals(16, row.tellTicks());
        assertEquals(15, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("glowcap", row.secondLook());
        assertEquals("spore_sac", row.material());
        assertEquals(RosterStats.Place.GROUND, row.place());
    }
}
