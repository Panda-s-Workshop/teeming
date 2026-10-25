package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Gloomweaver;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Gloomweaver's numbers, written out as plain values: HP 26, Atk 5, Arm 2, 1.3 x 0.9; spin 20 t; vine walls 5 wide
 * x 3 tall standing 200 t, at most 2 at once, an axe or fire bringing one down at once; warped forest, weight 8, alone;
 * second look Crimson Weaver; drops chitin plate. The rest is its design: the wall's last 20 t withering, spinning at
 * targets 4 to 12 blocks off with the wall 1.5 short of the target (and at least 2 from the weaver), the 10-tick weave,
 * 100 t between walls, and the counter's payoff (its threads snapped: 100 t without spinning). The GameTests check the
 * numbers are applied; this checks they are the right numbers.
 */
class GloomweaverNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, Gloomweaver.TELL_TICKS);
        assertEquals(5, Gloomweaver.WALL_WIDTH);
        assertEquals(3, Gloomweaver.WALL_HEIGHT);
        assertEquals(200, Gloomweaver.WALL_TICKS);
        assertEquals(2, Gloomweaver.MAX_WALLS);
        assertEquals(20, Gloomweaver.WITHER_TICKS);
        assertEquals(4.0, Gloomweaver.SPIN_MIN);
        assertEquals(12.0, Gloomweaver.SPIN_RANGE);
        assertEquals(1.5, Gloomweaver.WALL_SHORT);
        assertEquals(2.0, Gloomweaver.WALL_NEAR);
        assertEquals(10, Gloomweaver.WEAVE_TICKS);
        assertEquals(100, Gloomweaver.SPIN_COOLDOWN);
        assertEquals(20, Gloomweaver.FIRST_SPIN);
        assertEquals(100, Gloomweaver.SNAPPED_TICKS);
        assertEquals(0.3, Gloomweaver.FIRE_TOUCH);
    }

    @Test
    void wallFitsItsRules() {
        assertTrue(Gloomweaver.TELL_TICKS >= 8, "every tell lasts at least 8 ticks");
        assertEquals(1, Gloomweaver.WALL_WIDTH % 2, "an odd width, so the wall centres on the path");
        assertTrue(Gloomweaver.WITHER_TICKS < Gloomweaver.WALL_TICKS, "it withers only at the end of its time");
        assertTrue(Gloomweaver.SPIN_MIN - Gloomweaver.WALL_SHORT >= Gloomweaver.WALL_NEAR,
                "at the nearest target it spins at, the wall still goes up 1.5 short of it");
        assertTrue(Gloomweaver.WALL_NEAR - 1.0 > 1.3 / 2.0,
                "the block holding the wall's nearest point never reaches back into the weaver's own hitbox");
        assertTrue(Gloomweaver.SPIN_COOLDOWN * Gloomweaver.MAX_WALLS >= Gloomweaver.WALL_TICKS,
                "two walls at the soonest, then a third only once the first has run out");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("gloomweaver");
        assertEquals(26.0F, row.health());
        assertEquals(5.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(1.3F, row.width());
        assertEquals(0.9F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(8, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("crimson_weaver", row.secondLook());
        assertEquals("chitin_plate", row.material());
    }
}
