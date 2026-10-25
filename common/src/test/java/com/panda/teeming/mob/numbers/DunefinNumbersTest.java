package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.Dunefin;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Dunefin's numbers against its roster row, written out as plain values: HP 22, Atk 5, Arm 2, 1.2 x 0.8; the fin
 * rises for 15 t; it swims through sand at 0.32 b/t; the breach bites for 1.4x Atk; it hunts players standing on sand
 * within 16 blocks; coming down on anything else beaches it, stunned 60 t; it spawns on sand. The GameTests check the
 * numbers are applied; this checks they are the right numbers.
 */
class DunefinNumbersTest {
    @Test
    void mechanic() {
        assertEquals(15, Dunefin.TELL_TICKS);
        assertEquals(0.32, Dunefin.SWIM_SPEED);
        assertEquals(1.4F, Dunefin.BITE_HIT);
        assertEquals(16.0, Dunefin.HUNT_RANGE);
        assertEquals(60, Dunefin.BEACHED_TICKS);
        assertEquals(1.5F, Dunefin.BEACHED_DAMAGE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("dunefin");
        assertEquals(22.0F, row.health());
        assertEquals(5.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(1.2F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(15, row.tellTicks());
        assertEquals(20, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertEquals("red_dunefin", row.secondLook());
        assertEquals(RosterStats.Place.SAND, row.place());
        assertEquals(true, row.anyLight());
    }
}
