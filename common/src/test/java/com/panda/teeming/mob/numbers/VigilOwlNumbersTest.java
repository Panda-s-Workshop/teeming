package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.VigilOwl;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Vigil Owl's numbers against its roster row, written out as plain values: HP 18, Atk 4, Arm 0, 0.6 x 0.8; the
 * stare 40 t; sight 16 with line of sight; the mark is Glowing 200 t and Teeming End mobs within 32 take you as target;
 * the End midlands and highlands, weight 4, alone; second look Pale Vigil; drops void ichor. Its own numbers (flutters
 * off within 4, an 8 t peck wind-up, the turned mobs keep after it 100 t) are its design.
 */
class VigilOwlNumbersTest {
    @Test
    void mechanic() {
        assertEquals(40, VigilOwl.STARE_TICKS);
        assertEquals(16.0, VigilOwl.SIGHT_RANGE);
        assertEquals(200, VigilOwl.GLOW_TICKS);
        assertEquals(32.0, VigilOwl.CALL_RANGE);
        assertEquals(4.0, VigilOwl.KEEP_AWAY);
        assertEquals(8, VigilOwl.PECK_TELL_TICKS);
        assertEquals(1.0F, VigilOwl.PECK_HIT);
        assertEquals(100, VigilOwl.TURN_TICKS);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("vigil_owl");
        assertEquals(18.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(40, row.tellTicks());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("pale_vigil", row.secondLook());
        assertEquals("void_ichor", row.material());
    }

    @Test
    void houseRules() {
        assertTrue(VigilOwl.STARE_TICKS >= 8 && VigilOwl.PECK_TELL_TICKS >= 8, "every tell at least 8 ticks");
        assertTrue(VigilOwl.PECK_HIT < 1.5F, "the peck is an ordinary blow, under 1.5x its attack");
        assertTrue(VigilOwl.GLOW_TICKS <= 200, "Glowing, the one effect allowed to 200 ticks, no longer");
        assertTrue(VigilOwl.KEEP_AWAY < VigilOwl.SIGHT_RANGE, "it flutters off well inside its sight, so it stares from a distance");
        assertTrue(VigilOwl.PECK_REACH > VigilOwl.PECK_START, "a wind-up started in reach still lands on a target that holds still");
    }
}
