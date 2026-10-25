package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.overworld.Snowroller;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Snowroller's numbers against its roster row, written out as plain values: HP 26, Atk 4, Arm 4, 1.0 x 1.0; tuck
 * 16 t; roll speed +0.02 b/t per tick downhill, at most 0.6; damage Atk x (1 + blocks rolled / 8), at most x2.5; scale
 * 1 to 1.5; a wall stuns it 60 t, taking x1.5; snowy slopes, groves and jagged peaks, weight 15, groups of 1 to 2;
 * second look Frostbitten; drops frost core. Its starting speed (0.25), the speed it uncurls under (0.1), the range it
 * rolls from (3 to 16) and the 80-tick rest are its design. The GameTests check the numbers are applied; this checks
 * they are the right numbers.
 */
class SnowrollerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(16, Snowroller.TELL_TICKS);
        assertEquals(0.02, Snowroller.ROLL_ACCEL);
        assertEquals(0.6, Snowroller.ROLL_MAX_SPEED);
        assertEquals(8.0, Snowroller.ROLL_BLOCKS_PER_STEP);
        assertEquals(2.5F, Snowroller.ROLL_MAX_HIT);
        assertEquals(1.5, Snowroller.ROLL_MAX_SCALE);
        assertEquals(60, Snowroller.STUN_TICKS);
        assertEquals(1.5F, Snowroller.STUN_DAMAGE);
        assertEquals(0.25, Snowroller.ROLL_START_SPEED);
        assertEquals(0.1, Snowroller.ROLL_MIN_SPEED);
        assertEquals(3.0, Snowroller.MIN_RANGE);
        assertEquals(16.0, Snowroller.MAX_RANGE);
        assertEquals(80, Snowroller.COOLDOWN);
    }

    @Test
    void rollFitsItsRules() {
        assertTrue(Snowroller.ROLL_START_SPEED > Snowroller.ROLL_MIN_SPEED && Snowroller.ROLL_START_SPEED < Snowroller.ROLL_MAX_SPEED,
                "a roll starts between the speed it uncurls under and its cap");
        assertTrue(Snowroller.TELL_TICKS >= 10, "the hit can reach 1.5x attack and more, so the tell lasts at least 10 ticks");
        assertTrue(Snowroller.ROLL_TICKS * Snowroller.ROLL_START_SPEED >= Snowroller.MAX_RANGE,
                "a roll on the flat lasts long enough to cross its whole range");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("snowroller");
        assertEquals(26.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(4.0F, row.armor());
        assertEquals(1.0F, row.width());
        assertEquals(1.0F, row.height());
        assertEquals(16, row.tellTicks());
        assertEquals(15, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("frostbitten", row.secondLook());
        assertEquals("frost_core", row.material());
    }
}
