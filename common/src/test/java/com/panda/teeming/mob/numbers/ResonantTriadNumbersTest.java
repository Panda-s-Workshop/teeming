package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.ResonantTriad;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Resonant Triad's numbers, written out as plain values: HP 16 each, Atk 4, Arm 0, 0.6 x 0.9; the singer rotates every
 * 60 ticks; while all three live only the singer takes damage; the sky and the end highlands, weight 3, groups of 3; second
 * look Rose Triad; drops void crystal. The rest (the 10 tick tell before the song passes, the 8 tick crackle, the zap of 1 x
 * its attack within 5 blocks every 40 ticks) is its design. The GameTests check the numbers are applied; this checks they
 * are the right numbers.
 */
class ResonantTriadNumbersTest {
    @Test
    void song() {
        assertEquals(3, ResonantTriad.SEATS);
        assertEquals(60, ResonantTriad.SING_TICKS);
        assertEquals(10, ResonantTriad.RISE_TICKS);
        assertEquals(20, ResonantTriad.HUM_EVERY);
        assertEquals(100, ResonantTriad.PARTNER_LOST);
        assertEquals(20, ResonantTriad.COMPLETE_AFTER);
        assertEquals(16.0, ResonantTriad.LINK_RANGE);
        assertEquals(8.0, ResonantTriad.JOIN_RANGE);
    }

    @Test
    void zap() {
        assertEquals(8, ResonantTriad.TELL_TICKS);
        assertEquals(6, ResonantTriad.ZAP_TICKS);
        assertEquals(40, ResonantTriad.ZAP_EVERY);
        assertEquals(5.0, ResonantTriad.ZAP_RANGE);
        assertEquals(1.0F, ResonantTriad.ZAP);
    }

    @Test
    void design() {
        assertEquals(1.4, ResonantTriad.RADIUS);
        assertEquals(3.0, ResonantTriad.HOLD);
        assertEquals(1.5, ResonantTriad.HOVER);
        assertEquals(0.15, ResonantTriad.SPEED);
        assertEquals(3.0, ResonantTriad.WANDER);
        assertEquals(10, ResonantTriad.XP);
        assertEquals(0, ResonantTriad.STAGE_DIM);
        assertEquals(1, ResonantTriad.STAGE_RISE);
        assertEquals(2, ResonantTriad.STAGE_SING);
        assertEquals(3, ResonantTriad.STAGE_BROKEN);
        assertEquals(0, ResonantTriad.FLAG_SHIELDED);
        assertEquals(0xFFD36B, ResonantTriad.GOLD);
    }

    @Test
    void houseRules() {
        // the zap hurts a player, so it has a tell of at least 8 ticks; it is 1 x its attack, under 1.5 x, so 8 is enough
        assertTrue(ResonantTriad.TELL_TICKS >= 8 && ResonantTriad.ZAP < 1.5F);
        // the song's tell fits inside a singer's turn, and is at least 8 ticks
        assertTrue(ResonantTriad.RISE_TICKS >= 8 && ResonantTriad.RISE_TICKS < ResonantTriad.SING_TICKS);
        // stepping out of the crackle is possible: from the edge of reach a walk of 8 ticks (1.7 blocks) clears it
        assertTrue(0.216 * ResonantTriad.TELL_TICKS > 1.0);
        // its flag sits below the stage bits (24..27), which the stage setter wipes from 24 up
        assertTrue(ResonantTriad.FLAG_SHIELDED < 24);
        // the triangle holds within a sword's reach of its foe at times: its nearest corner comes within 3 blocks
        assertTrue(ResonantTriad.HOLD - ResonantTriad.RADIUS < 3.0);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("resonant_triad");
        assertEquals(16.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(0.9F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(3, row.weight());
        assertEquals(3, row.groupMin());
        assertEquals(3, row.groupMax());
        assertEquals("sky", row.spawner());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals("rose_triad", row.secondLook());
        assertEquals("void_crystal", row.material());
    }
}
