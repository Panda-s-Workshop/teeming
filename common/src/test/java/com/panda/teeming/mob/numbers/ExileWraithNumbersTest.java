package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.ExileWraith;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Exile Wraith's numbers against its row, written out as plain values: HP 26, Atk 4, Arm 0, 0.7 x 1.9, a 20-tick
 * tell (the mark); a mark of radius 1 under you, and whoever is still on it at the end is sent to other land 24 to 48
 * blocks away; small end islands and End barrens, weight 4, alone; second look Dusk Wraith; drops rift essence. The
 * GameTests check the numbers are applied in the world; this checks they are the right numbers.
 */
class ExileWraithNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, ExileWraith.MARK_TICKS);
        assertEquals(1.0, ExileWraith.MARK_RADIUS);
        assertEquals(24.0, ExileWraith.EXILE_NEAR);
        assertEquals(48.0, ExileWraith.EXILE_FAR);
        assertEquals(200, ExileWraith.EXILE_IMMUNITY);
        assertEquals(8, ExileWraith.CLAW_TELL);
        assertEquals(1.0F, ExileWraith.CLAW);
        // the calls made here: a mark at most every 5 s within 12 blocks, kept at 6 to 10 blocks; a jump at the last
        // moment doesn't clear the circle; a mark that catches nobody holds it 2 s; the rake's reach and cadence; the
        // landing search's steps and heights
        assertEquals(100, ExileWraith.MARK_EVERY);
        assertEquals(12.0, ExileWraith.MARK_RANGE);
        assertEquals(6.0, ExileWraith.KEEP_AWAY);
        assertEquals(10.0, ExileWraith.CLOSE_IN);
        assertEquals(1.5, ExileWraith.MARK_HEIGHT);
        assertEquals(40, ExileWraith.SNAG_TICKS);
        assertEquals(10, ExileWraith.EXILE_TICKS);
        assertEquals(6, ExileWraith.CLAW_TICKS);
        assertEquals(30, ExileWraith.CLAW_EVERY);
        assertEquals(0.8, ExileWraith.CLAW_START);
        assertEquals(1.4, ExileWraith.CLAW_REACH);
        assertEquals(6.0, ExileWraith.DISTANCE_STEP);
        assertEquals(7, ExileWraith.BAND_UP);
        assertEquals(14, ExileWraith.BAND_DOWN);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("exile_wraith");
        assertEquals(26.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(1.9F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertEquals("dusk_wraith", row.secondLook());
        assertEquals("rift_essence", row.material());
    }

    @Test
    void houseRules() {
        // every tell at least 8 ticks; the rake is 1 x Atk (under the 1.5 x that asks for 10 ticks); it winds up only where
        // the rake would land; the mark and its aftermath fit inside one wait; the snag is a 40-tick window, the least a
        // punish window may be; the search's heights overlap, leaving no gap between 18 down and 9 up
        assertTrue(ExileWraith.MARK_TICKS >= 8 && ExileWraith.CLAW_TELL >= 8);
        assertTrue(ExileWraith.CLAW < 1.5F);
        assertTrue(ExileWraith.CLAW_START <= ExileWraith.CLAW_REACH);
        assertTrue(ExileWraith.MARK_EVERY >= ExileWraith.MARK_TICKS + ExileWraith.SNAG_TICKS);
        assertTrue(ExileWraith.SNAG_TICKS >= 40 && ExileWraith.SNAG_TICKS <= 100);
        assertTrue(ExileWraith.KEEP_AWAY < ExileWraith.CLOSE_IN && ExileWraith.CLOSE_IN <= ExileWraith.MARK_RANGE);
        assertTrue(ExileWraith.BAND_UP <= 7 && ExileWraith.BAND_DOWN - ExileWraith.BAND_UP <= 7, "each start covers 4 down to 2 up");
        assertTrue(ExileWraith.EXILE_IMMUNITY >= ExileWraith.MARK_EVERY, "nobody is sent away by the same wraith's next mark");
    }
}
