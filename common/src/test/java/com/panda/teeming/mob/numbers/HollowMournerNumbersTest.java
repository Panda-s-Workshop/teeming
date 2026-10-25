package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.HollowMourner;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Hollow Mourner's numbers against its roster row, written out as plain values: HP 26, Atk 3, Arm 2, 0.7 x 2.0;
 * a 60 t chant that raises one undead that died within 8 blocks in the last 30 s, every 200 t; a hit staggers it
 * for 40 t, then for 40 t only a hit on a chant staggers it; Overworld land at night, weight 5, groups of 1; the Bog
 * Mourner second look; grave ash. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class HollowMournerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(60, HollowMourner.TELL_TICKS);
        assertEquals(200, HollowMourner.CHANT_EVERY);
        assertEquals(8.0, HollowMourner.GRAVE_RANGE);
        assertEquals(600, HollowMourner.GRAVE_TICKS);
        assertEquals(40, HollowMourner.STAGGER_TICKS);
        assertEquals(40, HollowMourner.STAGGER_GRACE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("hollow_mourner");
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(26.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(2.0F, row.height());
        assertEquals(60, row.tellTicks());
        assertEquals(5, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("bog_mourner", row.secondLook());
        assertEquals("grave_ash", row.material());
        assertEquals(RosterStats.Place.GROUND, row.place());
    }
}
