package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.PearlshellSnail;
import com.panda.teeming.mob.end.PearlshellSnail.Growth;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Pearlshell Snail's numbers against its row in RosterStats, written out as plain values: a creature, HP 16, Atk 0,
 * Arm 6, 0.6 x 0.5, no tell; fed chorus fruit, it grows a pearl over 6000 t; shears harvest 1 ender pearl; hurt, it
 * hides in its shell for 60 t at x0.2; small End islands, weight 5, groups of 1-3; Purpur Snail; no material of its own
 * (ender pearls). The GameTests check the numbers are applied; this checks they are the right numbers, and that the
 * pearl's stages split the growing time in thirds.
 */
class PearlshellSnailNumbersTest {
    @Test
    void mechanic() {
        assertEquals(6000, PearlshellSnail.GROW_TICKS);
        assertEquals(1, PearlshellSnail.PEARLS_PER_HARVEST);
        assertEquals(60, PearlshellSnail.HIDE_TICKS);
        assertEquals(0.2F, PearlshellSnail.HIDDEN_DAMAGE);
    }

    /** Its own numbers, not in the row. */
    @Test
    void own() {
        assertEquals(20, PearlshellSnail.FEED_TICKS);
        assertEquals(20, PearlshellSnail.HARVEST_TICKS);
        assertEquals(5, PearlshellSnail.STAGES);
        assertEquals(0, PearlshellSnail.NO_PEARL);
        assertEquals(4, PearlshellSnail.GROWN_STAGE);
        assertEquals(-1, PearlshellSnail.NONE);
        assertEquals(40, PearlshellSnail.GLINT_EVERY);
    }

    @Test
    void stages() {
        assertEquals(0, Growth.stage(-1), "no pearl");
        assertEquals(1, Growth.stage(0), "just fed");
        assertEquals(1, Growth.stage(1999));
        assertEquals(2, Growth.stage(2000), "a third of the way");
        assertEquals(2, Growth.stage(3999));
        assertEquals(3, Growth.stage(4000), "two thirds");
        assertEquals(3, Growth.stage(5999), "a tick short of grown");
        assertEquals(4, Growth.stage(6000), "grown");
        for (int g = 0; g < 6000; g += 7) assertTrue(Growth.stage(g) >= 1 && Growth.stage(g) <= 3, "growing is stage 1 to 3, " + g);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("pearlshell_snail");
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Cat.CREATURE, row.cat());
        assertEquals(16.0F, row.health());
        assertEquals(0.0F, row.attack());
        assertEquals(6.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(0.5F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(5, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(3, row.groupMax());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertTrue(row.anyLight());
        assertEquals("purpur_snail", row.secondLook());
        assertEquals("", row.material(), "no material of its own: its harvest is ender pearls");
    }
}
