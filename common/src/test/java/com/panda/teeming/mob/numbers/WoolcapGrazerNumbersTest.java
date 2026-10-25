package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.WoolcapGrazer;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Woolcap Grazer's numbers against its row in RosterStats, written out as plain values: a creature, HP 18, Atk 0, Arm 0,
 * 1.0 x 1.2, no tell; shears take 1-2 fungal fleece, which grows back on nylium; a hurt one sets the herd within 16
 * stampeding for 100 t, and their shove is knockback 0.6; warped forest, weight 8, groups of 2-4; Crimson Woolcap;
 * fungal fleece. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class WoolcapGrazerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(1, WoolcapGrazer.FLEECE_MIN);
        assertEquals(2, WoolcapGrazer.FLEECE_MAX);
        assertEquals(100, WoolcapGrazer.STAMPEDE_TICKS);
        assertEquals(16.0, WoolcapGrazer.HERD_RANGE);
        assertEquals(0.6, WoolcapGrazer.KNOCKBACK);
        assertEquals(0.4, WoolcapGrazer.KNOCKBACK_LIFT);
    }

    /** Its own numbers, not in the row: the sheep's where it has one (graze, love, breeding, calves). */
    @Test
    void sheepLike() {
        assertEquals(40, WoolcapGrazer.GRAZE_TICKS);
        assertEquals(1000, WoolcapGrazer.GRAZE_CHANCE_FULL);
        assertEquals(50, WoolcapGrazer.GRAZE_CHANCE_CALF);
        assertEquals(200, WoolcapGrazer.GRAZE_CHANCE_BARE);
        assertEquals(20, WoolcapGrazer.SHEAR_TICKS);
        assertEquals(600, WoolcapGrazer.LOVE_TICKS);
        assertEquals(8.0, WoolcapGrazer.MATE_RANGE);
        assertEquals(3.0, WoolcapGrazer.MATE_TOUCH);
        assertEquals(60, WoolcapGrazer.MATE_TICKS);
        assertEquals(6000, WoolcapGrazer.BREED_COOLDOWN);
        assertEquals(24000, WoolcapGrazer.GROW_TICKS);
        assertEquals(0.1, WoolcapGrazer.CALF_FEED_SHARE);
        assertEquals(1200, WoolcapGrazer.CALF_GRAZE_GROWTH);
        assertEquals(1.6, WoolcapGrazer.STAMPEDE_SPEED);
        assertEquals(8.0, WoolcapGrazer.STAMPEDE_REACH);
        assertEquals(25, WoolcapGrazer.STAMPEDE_TURN);
        assertEquals(10, WoolcapGrazer.BUMP_COOLDOWN);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("woolcap_grazer");
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(RosterStats.Cat.CREATURE, row.cat());
        assertEquals(18.0F, row.health());
        assertEquals(0.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(1.0F, row.width());
        assertEquals(1.2F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(8, row.weight());
        assertEquals(2, row.groupMin());
        assertEquals(4, row.groupMax());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertTrue(row.anyLight());
        assertEquals("crimson_woolcap", row.secondLook());
        assertEquals("fungal_fleece", row.material());
    }
}
