package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.Dustwhirl;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Dustwhirl's numbers against its roster row, written out as plain values: HP 18, Atk 2, Arm 0, 0.8 x 1.6; a
 * 20-tick spin-up; drags items within 8 blocks; pushes players outward at 0.6 b/t for 60 ticks, sneaking halves it;
 * then a 40-tick slowdown in which it takes x1.5; savanna and wooded badlands, weight 12, alone; Red Dust; gale
 * essence. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class DustwhirlNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, Dustwhirl.TELL_TICKS);
        assertEquals(60, Dustwhirl.GUST_TICKS);
        assertEquals(40, Dustwhirl.SLOW_TICKS);
        assertEquals(0.6, Dustwhirl.GUST_SPEED);
        assertEquals(0.5, Dustwhirl.SNEAK_FACTOR);
        assertEquals(1.5F, Dustwhirl.CORE_DAMAGE);
        assertEquals(8.0, Dustwhirl.ITEM_REACH);
    }

    /** Numbers the row leaves open, chosen for play: the gust's and trigger's reach, the tug, the rest. */
    @Test
    void chosen() {
        assertEquals(5.0, Dustwhirl.GUST_RADIUS);
        assertEquals(5.0, Dustwhirl.TRIGGER_RANGE);
        assertEquals(0.15, Dustwhirl.PULL_SPEED);
        assertEquals(60, Dustwhirl.COOLDOWN);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("dustwhirl");
        assertEquals(18.0F, row.health());
        assertEquals(2.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(1.6F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(12, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("red_dust", row.secondLook());
        assertEquals("gale_essence", row.material());
        assertEquals(RosterStats.Place.GROUND, row.place());
    }
}
