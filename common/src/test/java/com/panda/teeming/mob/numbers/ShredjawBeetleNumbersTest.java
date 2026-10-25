package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.ShredjawBeetle;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Shredjaw Beetle's numbers, written out as plain values: HP 30, Atk 5, Arm 8, 1.1 x 0.8; jaws spread 12 t;
 * Sunder takes 4 armour per stack, at most 3 stacks, for 200 t; each bite wears every worn armour piece by 2 more;
 * crimson forest, weight 12, groups of 1 to 2; second look Warped Shredjaw; drops chitin plate. The rest is its design:
 * the bite holds 6 t, a blocked bite jars it for 30 t, 30 t between bites, it spreads its jaws with 0.8 blocks between
 * hitboxes and still bites at 1.2, across a 120-degree front, turning 12 degrees a tick in the tell. The GameTests
 * check the numbers are applied; this checks they are the right numbers.
 */
class ShredjawBeetleNumbersTest {
    @Test
    void mechanic() {
        assertEquals(12, ShredjawBeetle.TELL_TICKS);
        assertEquals(4.0, ShredjawBeetle.SUNDER_ARMOR);
        assertEquals(3, ShredjawBeetle.SUNDER_MAX);
        assertEquals(200, ShredjawBeetle.SUNDER_TICKS);
        assertEquals(2, ShredjawBeetle.WEAR);
        assertEquals(6, ShredjawBeetle.BITE_TICKS);
        assertEquals(30, ShredjawBeetle.JARRED_TICKS);
        assertEquals(30, ShredjawBeetle.COOLDOWN);
        assertEquals(0.8, ShredjawBeetle.START_REACH);
        assertEquals(1.2, ShredjawBeetle.BITE_REACH);
        assertEquals(120.0F, ShredjawBeetle.BITE_ARC);
        assertEquals(12.0F, ShredjawBeetle.TURN);
    }

    @Test
    void fitsItsRules() {
        assertTrue(ShredjawBeetle.TELL_TICKS >= 8, "every tell lasts at least 8 ticks");
        assertTrue(ShredjawBeetle.SUNDER_TICKS <= 200, "no effect lasts over 200 ticks");
        assertTrue(ShredjawBeetle.BITE_REACH > ShredjawBeetle.START_REACH, "a target that only stands still is always bitten");
        assertTrue(ShredjawBeetle.TURN * ShredjawBeetle.TELL_TICKS >= ShredjawBeetle.BITE_ARC,
                "in the tell it can turn further than its front is wide, so only a real sidestep slips out of it");
        assertTrue(ShredjawBeetle.JARRED_TICKS >= 20, "a blocked bite leaves a window of at least a second to strike back");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("shredjaw_beetle");
        assertEquals(30.0F, row.health());
        assertEquals(5.0F, row.attack());
        assertEquals(8.0F, row.armor());
        assertEquals(1.1F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(12, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(12, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("warped_shredjaw", row.secondLook());
        assertEquals("chitin_plate", row.material());
    }
}
