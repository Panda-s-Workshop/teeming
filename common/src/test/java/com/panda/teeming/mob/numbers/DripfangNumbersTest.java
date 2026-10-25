package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.overworld.Dripfang;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Dripfang's numbers against its roster row, written out as plain values: HP 16, Atk 7, Arm 2, 0.6 x 1.4; drip
 * 12 t; it drops on a target within 1.5 blocks across the ground for 1.5 x Atk (10.5); a miss sticks it 60 t, taking
 * x1.5; dripstone caves, placed on ceilings, weight 20, groups of 1 to 2; second look Calcite Fang; drops venom fang.
 * The fall, the climb and the wait before the next drip are its design. The GameTests check the numbers are applied;
 * this checks they are the right numbers.
 */
class DripfangNumbersTest {
    @Test
    void mechanic() {
        assertEquals(12, Dripfang.TELL_TICKS);
        assertEquals(1.5, Dripfang.DROP_RANGE);
        assertEquals(1.5F, Dripfang.DROP_HIT);
        assertEquals(60, Dripfang.STUCK_TICKS);
        assertEquals(1.5F, Dripfang.STUCK_DAMAGE);
    }

    @Test
    void design() {
        assertTrue(Dripfang.TELL_TICKS >= 10, "a blow of 1.5 x Atk or more gets a tell of at least 10 ticks");
        double fallen = 0.0;
        for (int t = 1; t <= Dripfang.DROP_TICKS; t++) fallen += Math.min(Dripfang.DROP_MAX, Dripfang.DROP_START + Dripfang.DROP_ACCEL * t);
        assertTrue(fallen >= Dripfang.DROP_DEPTH + 1.4, "a drop at the deepest target it drips for lands before the drop runs out");
        assertTrue(Dripfang.CLIMB_SPEED * Dripfang.CLIMB_MAX_TICKS >= Dripfang.CLIMB_REACH + 1.4,
                "a climb to the highest ceiling it climbs to arrives before it gives up");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("dripfang");
        assertEquals(16.0F, row.health());
        assertEquals(7.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(1.4F, row.height());
        assertEquals(12, row.tellTicks());
        assertEquals(10.5F, row.blow());
        assertEquals(20, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertEquals(RosterStats.Place.CEILING, row.place());
        assertEquals("calcite_fang", row.secondLook());
        assertEquals("venom_fang", row.material());
    }
}
