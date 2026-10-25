package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.MagmaTortoise;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Magma Tortoise's numbers against its roster row, written out as plain values: HP 60, Atk 0, Arm 10, 2.2 x 1.0,
 * no tell; swims on lava at 0.08 b/t; magma cream makes it follow for 600 t; lava seas, weight 4, alone; Old Tortoise;
 * magma scute. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class MagmaTortoiseNumbersTest {
    @Test
    void mechanic() {
        assertEquals(0.08, MagmaTortoise.SWIM_SPEED);
        assertEquals(600, MagmaTortoise.FOLLOW_TICKS);
        assertEquals(0.5, MagmaTortoise.SINK);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("magma_tortoise");
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(RosterStats.Cat.CREATURE, row.cat());
        assertEquals(60.0F, row.health());
        assertEquals(0.0F, row.attack());
        assertEquals(10.0F, row.armor());
        assertEquals(2.2F, row.width());
        assertEquals(1.0F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals(RosterStats.Place.LAVA, row.place());
        assertTrue(row.anyLight());
        assertEquals("old_tortoise", row.secondLook());
        assertEquals("magma_scute", row.material());
    }
}
