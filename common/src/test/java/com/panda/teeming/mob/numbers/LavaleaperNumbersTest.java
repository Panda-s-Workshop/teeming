package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.nether.Lavaleaper;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Lavaleaper's numbers against its roster row, written out as plain values: HP 24, Atk 7, Arm 2, 0.8 x 0.6; the
 * bubbles at the surface last 10 t; it leaps at targets within 4 blocks of the lava it swims in and sets them on fire
 * for 3 s; reeled in on a fishing line it lies helpless for 100 t and takes x1.5; it lives in lava in the Nether, weight
 * 10, groups of 1 to 2. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class LavaleaperNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10, Lavaleaper.TELL_TICKS);
        assertEquals(4.0, Lavaleaper.LEAP_RANGE);
        assertEquals(3.0F, Lavaleaper.FIRE_SECONDS);
        assertEquals(100, Lavaleaper.HELPLESS_TICKS);
        assertEquals(1.5F, Lavaleaper.HELPLESS_DAMAGE);
        assertEquals(0.2, Lavaleaper.SWIM_SPEED);
        assertEquals(40, Lavaleaper.LEAP_COOLDOWN);
        assertEquals(0.75, Lavaleaper.SINK_LURK);
        assertEquals(0.3, Lavaleaper.SINK_TELL);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("lavaleaper");
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(24.0F, row.health());
        assertEquals(7.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(0.6F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(10, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertEquals("ember_leaper", row.secondLook());
        assertEquals("slag_gland", row.material());
        assertEquals(RosterStats.Place.LAVA, row.place());
    }
}
