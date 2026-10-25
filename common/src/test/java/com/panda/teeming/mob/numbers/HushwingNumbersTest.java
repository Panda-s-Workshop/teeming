package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.overworld.Hushwing;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Hushwing's numbers against its roster row, written out as plain values: HP 8, Atk 3, Arm 0, 0.5 x 0.5, no tell;
 * it hears sprinting at 16 blocks, fighting at 12, mining at 10, walking or sneaking at 0; a projectile's impact is a
 * decoy for 100 ticks; below y 0 in the Overworld, weight 15, flocks of 3 to 4; second look Echo Hushwing; drops echo
 * membrane. The rest (the 10-tick ear flick, 3 s of memory, a bite of 1x Atk every 20 ticks, 16 blocks for an impact)
 * is its design. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class HushwingNumbersTest {
    @Test
    void hearing() {
        assertEquals(16.0, Hushwing.SPRINT_RANGE);
        assertEquals(12.0, Hushwing.COMBAT_RANGE);
        assertEquals(10.0, Hushwing.MINING_RANGE);
        assertEquals(0.0, Hushwing.WALK_RANGE);
        assertEquals(100, Hushwing.DECOY_TICKS);
    }

    @Test
    void design() {
        assertEquals(16.0, Hushwing.DECOY_RANGE);
        assertEquals(10, Hushwing.HEAR_TICKS);
        assertEquals(60, Hushwing.MEMORY_TICKS);
        assertEquals(1.0F, Hushwing.BITE_HIT);
        assertEquals(20, Hushwing.BITE_COOLDOWN);
        assertTrue(Hushwing.CHASE_SPEED > 0.28, "it chases faster than a player sprints, so only going quiet escapes it");
        assertTrue(Hushwing.HEAR_TICKS >= 8, "the hearing cue shows at least as long as the shortest tell");
        assertTrue(Hushwing.BITE_TICKS < Hushwing.BITE_COOLDOWN, "a bite ends before the next may start");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("hushwing");
        assertEquals(8.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.5F, row.width());
        assertEquals(0.5F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(15, row.weight());
        assertEquals(3, row.groupMin());
        assertEquals(4, row.groupMax());
        assertEquals(0, row.maxY());
        assertTrue(!row.anyLight());
        assertEquals(RosterStats.Dim.OVERWORLD, row.dim());
        assertEquals("echo_hushwing", row.secondLook());
        assertEquals("echo_membrane", row.material());
    }
}
