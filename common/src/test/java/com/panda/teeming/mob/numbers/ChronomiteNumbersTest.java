package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Chronomite;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Chronomite's numbers against its row, written out as plain values: HP 26, Atk 8.5, Arm 6, 0.9 x 0.7, no tell of
 * its own (its bite gets one anyway); a field of radius 6 in which movement and projectiles run at x0.4 and attack speed
 * at x0.6 (40 % slower); a 20-point shell that takes damage first and ends the field when it cracks; End midlands and
 * barrens, weight 4, alone; second look Gilded Mite; drops time sand. Then the calls made here: the bite's 8-tick tell
 * and reach, the shatter, the stages (the crack stage of a given shell is a method, checked in the GameTests). The
 * GameTests check the numbers are applied in the world; this checks they are the right numbers.
 */
class ChronomiteNumbersTest {
    @Test
    void mechanic() {
        assertEquals(6.0, Chronomite.FIELD_RADIUS);
        assertEquals(0.4, Chronomite.FIELD_MOVE);
        assertEquals(0.4, Chronomite.FIELD_SHOTS);
        assertEquals(0.6, Chronomite.FIELD_ATTACK);
        assertEquals(20.0F, Chronomite.SHELL);
        // the calls made here: an 8-tick tell before a 1 x Atk bite, its reach, arc and cooldown, the shatter's length,
        // a tick each second, the five shell stages
        assertEquals(8, Chronomite.TELL_TICKS);
        assertEquals(6, Chronomite.BITE_TICKS);
        assertEquals(12, Chronomite.SHATTER_TICKS);
        assertEquals(20, Chronomite.COOLDOWN);
        assertEquals(1.0F, Chronomite.BITE);
        assertEquals(0.6, Chronomite.START_REACH);
        assertEquals(1.2, Chronomite.BITE_REACH);
        assertEquals(120.0F, Chronomite.BITE_ARC);
        assertEquals(15.0F, Chronomite.TURN);
        assertEquals(20, Chronomite.TICK_EVERY);
        assertEquals(20, Chronomite.FIELD_RETRY);
        assertEquals(0, Chronomite.STAGE_WHOLE);
        assertEquals(4, Chronomite.STAGE_BROKEN);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("chronomite");
        assertEquals(26.0F, row.health());
        assertEquals(8.5F, row.attack());
        assertEquals(6.0F, row.armor());
        assertEquals(0.9F, row.width());
        assertEquals(0.7F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("gilded_mite", row.secondLook());
        assertEquals("time_sand", row.material());
    }

    @Test
    void houseRules() {
        // every blow gets a tell of at least 8 ticks even where the row has none (the bite is 1 x Atk, under the 1.5 x
        // that asks for 10); the field slows and never stops; the bite's reach is past where it starts
        assertTrue(Chronomite.TELL_TICKS >= 8);
        assertTrue(Chronomite.BITE < 1.5F);
        assertTrue(Chronomite.FIELD_MOVE > 0.0 && Chronomite.FIELD_MOVE < 1.0);
        assertTrue(Chronomite.FIELD_SHOTS > 0.0 && Chronomite.FIELD_SHOTS < 1.0);
        assertEquals(0.4, 1.0 - Chronomite.FIELD_ATTACK, 1.0E-9, "attacks 40 % slower");
        assertTrue(Chronomite.START_REACH < Chronomite.BITE_REACH);
    }
}
