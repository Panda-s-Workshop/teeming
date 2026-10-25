package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.MyceliteShambler;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Mycelite Shambler's numbers against its roster row, written out as plain values: HP 24 -> 12 -> 6, Atk 3, Arm 0,
 * 1.0 x 1.8 scaled; it bursts in two at scale 1.0 -> 0.7 -> 0.45; each puff gives Poison I for 40 t in radius 2; no
 * tell; dark forest and caves, weight 15, groups of 1; the Crimson Cap second look; spore sac. The GameTests check the
 * numbers are applied; this checks they are the right numbers.
 */
class MyceliteShamblerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(1.0F, MyceliteShambler.SCALE_LARGE);
        assertEquals(0.7F, MyceliteShambler.SCALE_MEDIUM);
        assertEquals(0.45F, MyceliteShambler.SCALE_SMALL);
        assertEquals(24.0F, MyceliteShambler.HEALTH_LARGE);
        assertEquals(12.0F, MyceliteShambler.HEALTH_MEDIUM);
        assertEquals(6.0F, MyceliteShambler.HEALTH_SMALL);
        assertEquals(2, MyceliteShambler.SPLIT_COUNT);
        assertEquals(2.0, MyceliteShambler.PUFF_RADIUS);
        assertEquals(40, MyceliteShambler.POISON_TICKS);
        assertEquals(0, MyceliteShambler.POISON_LEVEL);
        assertEquals(0, MyceliteShambler.LARGE);
        assertEquals(2, MyceliteShambler.SMALL);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("mycelite_shambler");
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(24.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(1.0F, row.width());
        assertEquals(1.8F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(72.0F, row.balanceHealth());                    // 24 + 2 x 12 + 4 x 6: the whole line
        assertEquals(15, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("crimson_cap", row.secondLook());
        assertEquals("spore_sac", row.material());
        assertEquals(RosterStats.Place.GROUND, row.place());
    }
}
