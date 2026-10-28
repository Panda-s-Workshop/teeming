package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.AbyssAngler;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Abyss Angler's numbers against its row, written out as plain values: HP 30, Atk 4, Arm 2, 1.2 x 1.0, tell 16 (the
 * lure); from below an edge it casts a line up to 16 long and reels you toward the edge at 0.5 blocks a tick; a hit on
 * the line cuts it; island undersides (the spawner's underside rule), one at a time; second look Lantern Angler; drops
 * void membrane. The GameTests check the numbers are applied in the world; this checks they are the right numbers.
 */
class AbyssAnglerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(16, AbyssAngler.TELL_TICKS);
        assertEquals(16.0, AbyssAngler.CAST_RANGE);
        assertEquals(0.5, AbyssAngler.PULL_SPEED);
        // the calls made here: the hook bites for 1 x Atk, the catch is held 40 t at the brink, a cut line stuns it 40 t,
        // 60 t between one cast's end and the next lure, a swing or flight within 0.3 of the line cuts it, the casting
        // spot half a block past the rim's face and over its top, rims up to 3 thick, stunned it hangs 1.1 out (against
        // the rim's face), the reel climbs a step of 0.6 and goes down one of 1, a crawl of 0.1 b/t up to 12 across, a
        // slide of 0.3 b/t, a climb of up to 6 at 0.2 b/t, prey up to 1.5 x 2.5, lost past 3 blocks
        assertEquals(1.0F, AbyssAngler.HOOK_HIT);
        assertEquals(40, AbyssAngler.HOLD_TICKS);
        assertEquals(40, AbyssAngler.STUN_TICKS);
        assertEquals(60, AbyssAngler.COOLDOWN);
        assertEquals(0.3, AbyssAngler.LINE_RADIUS);
        assertEquals(1.0, AbyssAngler.LURE_FORWARD);
        assertEquals(0.5, AbyssAngler.LURE_UP);
        assertEquals(3, AbyssAngler.MAX_RIM);
        assertEquals(1.1, AbyssAngler.STUN_OUT);
        assertEquals(0.6, AbyssAngler.STEP_UP);
        assertEquals(1.0, AbyssAngler.STEP_DOWN);
        assertEquals(0.1, AbyssAngler.CRAWL_SPEED);
        assertEquals(12, AbyssAngler.CRAWL_RANGE);
        assertEquals(0.3, AbyssAngler.SLIDE_SPEED);
        assertEquals(6, AbyssAngler.CLIMB_REACH);
        assertEquals(0.2, AbyssAngler.CLIMB_SPEED);
        assertEquals(60, AbyssAngler.CLIMB_MAX_TICKS);
        assertEquals(1.5F, AbyssAngler.MAX_PREY_WIDTH);
        assertEquals(2.5F, AbyssAngler.MAX_PREY_HEIGHT);
        assertEquals(3.0, AbyssAngler.ESCAPE_RANGE);
        assertEquals(0.15, AbyssAngler.WALK_SPEED);
        assertEquals(12, AbyssAngler.XP);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("abyss_angler");
        assertEquals(30.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(1.2F, row.width());
        assertEquals(1.0F, row.height());
        assertEquals(16, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("underside", row.spawner());
        assertEquals(RosterStats.Place.NONE, row.place());
        assertFalse(row.biomeSpawns());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("lantern_angler", row.secondLook());
        assertEquals("void_membrane", row.material());
        assertEquals(row.tellTicks(), AbyssAngler.TELL_TICKS);
    }

    @Test
    void houseRules() {
        // the lure is at least 8 ticks (its hook is 1 x Atk, under the 1.5 x that asks for 10, and it has 16 anyway); the
        // stun is a punish window of 40..100 t; the cooldown outlasts the stun, so a stunned angler lures again only
        // after it is back under its rim; the line can't be cut from farther than a body's width; a stunned angler hangs
        // wholly out past the rim's face (its middle 0.6 past it, half its 1.2 width) and the casting spot is out past
        // the face too; it crawls slower than it slides, so its slide follows a crawl exactly; the reel can climb a stair
        // (0.5) and a slab's step but not a full block
        assertTrue(AbyssAngler.TELL_TICKS >= 8);
        assertTrue(AbyssAngler.HOOK_HIT < 1.5F);
        assertTrue(AbyssAngler.STUN_TICKS >= 40 && AbyssAngler.STUN_TICKS <= 100);
        assertTrue(AbyssAngler.COOLDOWN > AbyssAngler.STUN_TICKS);
        assertTrue(AbyssAngler.LINE_RADIUS <= 0.5);
        assertEquals(0.5 + 1.2 / 2.0, AbyssAngler.STUN_OUT, 1.0E-9);
        assertTrue(AbyssAngler.LURE_FORWARD > 0.5);
        assertTrue(AbyssAngler.CRAWL_SPEED < AbyssAngler.SLIDE_SPEED);
        assertTrue(AbyssAngler.STEP_UP >= 0.5 && AbyssAngler.STEP_UP < 1.0);
        assertTrue(AbyssAngler.PULL_SPEED * 32 == AbyssAngler.CAST_RANGE, "a line at its longest is reeled in in 32 ticks");
    }
}
