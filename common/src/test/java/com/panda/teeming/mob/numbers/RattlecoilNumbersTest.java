package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.Rattlecoil;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Rattlecoil's numbers against its roster row, written out as plain values: HP 14, Atk 4, Arm 0, 0.8 x 0.4;
 * rattle 30 t within 6; strikes within 4 after the rattle: Poison II 100 t; calms beyond 8. The GameTests check the
 * numbers are applied; this checks they are the right numbers.
 */
class RattlecoilNumbersTest {
    @Test
    void mechanic() {
        assertEquals(30, Rattlecoil.TELL_TICKS);
        assertEquals(6.0, Rattlecoil.WARN_RANGE);
        assertEquals(4.0, Rattlecoil.STRIKE_RANGE);
        assertEquals(8.0, Rattlecoil.CALM_RANGE);
        assertEquals(100, Rattlecoil.POISON_TICKS);
        assertEquals(1, Rattlecoil.POISON_LEVEL);
        assertEquals(1.0F, Rattlecoil.STRIKE_HIT);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("rattlecoil");
        assertEquals(14.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(0.4F, row.height());
        assertEquals(30, row.tellTicks());
    }
}
