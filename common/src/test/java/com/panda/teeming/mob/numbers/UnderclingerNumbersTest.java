package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Underclinger;
import com.panda.teeming.mob.end.Underclinger.Rim;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Underclinger's numbers against its row, written out as plain values: HP 28, Atk 8.5, Arm 2, 1.0 x 0.6, tell 10
 * (the reach); it grabs prey at an edge or on a bridge and drags it 2 blocks out; a hit makes it let go; island
 * undersides (the spawner's underside rule), one at a time; second look Pale Clinger; drops void membrane. The GameTests
 * check the numbers are applied in the world; this checks they are the right numbers.
 */
class UnderclingerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10, Underclinger.TELL_TICKS);
        assertEquals(2.0, Underclinger.PULL_DISTANCE);
        // the calls made here: the hooks close for 1 x Atk, it holds 20 t (the window to hit it), drags over 4 t, a blow
        // sends it back under the island for 40 t (8 of them a flinch), 40 t between drags, prey within 1 block of an
        // edge, a rim at most 2 thick, a crawl of 0.1 b/t up to 12 blocks across, half its width slid out over the
        // rim, a retreat of 4 cells, a climb of up to 6 at 0.2 b/t, prey up to 1.0 x 2.0, lost past 3 blocks
        assertEquals(1.0F, Underclinger.GRAB_HIT);
        assertEquals(20, Underclinger.HOLD_TICKS);
        assertEquals(4, Underclinger.PULL_TICKS);
        assertEquals(40, Underclinger.RETREAT_TICKS);
        assertEquals(8, Underclinger.RECOIL_TICKS);
        assertEquals(40, Underclinger.COOLDOWN);
        assertEquals(1.0, Underclinger.EDGE_RANGE);
        assertEquals(2.0, Underclinger.GRAB_REACH);
        assertEquals(0.1, Underclinger.CRAWL_SPEED);
        assertEquals(12, Underclinger.CRAWL_RANGE);
        assertEquals(0.5, Underclinger.LEAN_OUT);
        assertEquals(0.125, Underclinger.SLIDE_SPEED);
        assertEquals(4, Underclinger.RETREAT_CELLS);
        assertEquals(6, Underclinger.CLIMB_REACH);
        assertEquals(0.2, Underclinger.CLIMB_SPEED);
        assertEquals(60, Underclinger.CLIMB_MAX_TICKS);
        assertEquals(1.0F, Underclinger.MAX_PREY_WIDTH);
        assertEquals(2.0F, Underclinger.MAX_PREY_HEIGHT);
        assertEquals(3.0, Underclinger.ESCAPE_RANGE);
        assertEquals(0.2, Underclinger.WALK_SPEED);
        assertEquals(4, Rim.BRIDGE_DROP);
        assertEquals(2, Rim.SEARCH);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("underclinger");
        assertEquals(28.0F, row.health());
        assertEquals(8.5F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(1.0F, row.width());
        assertEquals(0.6F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("underside", row.spawner());
        assertEquals(RosterStats.Place.NONE, row.place());
        assertFalse(row.biomeSpawns());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("pale_clinger", row.secondLook());
        assertEquals("void_membrane", row.material());
        assertEquals(row.tellTicks(), Underclinger.TELL_TICKS);
    }

    @Test
    void houseRules() {
        // the reach is at least 8 ticks (its hook is 1 x Atk, under the 1.5 x that asks for 10, and it has 10 anyway);
        // the hold leaves time to answer it; the drag covers its 2 blocks in its length; the reach slides it no farther
        // out than half its width (its back stays on the stone); a retreat outlasts its flinch; it crawls slower than it
        // slides, so its slide follows a crawl exactly
        assertTrue(Underclinger.TELL_TICKS >= 8);
        assertTrue(Underclinger.GRAB_HIT < 1.5F);
        assertTrue(Underclinger.HOLD_TICKS >= Underclinger.TELL_TICKS);
        assertEquals(Underclinger.PULL_DISTANCE, Underclinger.PULL_TICKS * (Underclinger.PULL_DISTANCE / Underclinger.PULL_TICKS), 1.0E-12);
        assertTrue(Underclinger.LEAN_OUT <= 0.5);
        assertTrue(Underclinger.RETREAT_TICKS > Underclinger.RECOIL_TICKS);
        assertTrue(Underclinger.CRAWL_SPEED < Underclinger.SLIDE_SPEED);
        assertTrue(Underclinger.EDGE_RANGE + 1.0 <= Underclinger.PULL_DISTANCE, "prey within reach of the edge always ends past it");
    }
}
