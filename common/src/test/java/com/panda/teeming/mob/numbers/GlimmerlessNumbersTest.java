package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Glimmerless;
import com.panda.teeming.mob.end.Glimmerless.Footprints;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Glimmerless's numbers against its row, written out as plain values: HP 30, Atk 7, Arm 2, 0.7 x 1.9, its tell
 * the footprints; invisible but for them, and any projectile hit reveals it for 200 t; End highlands and midlands,
 * weight 4, alone; second look Shimmering; drops void ichor. Then its own calls (an 8-tick shimmer before a rake of
 * 1 x Atk, a 30-tick cooldown, a 30-tick flinch 3 blocks back) and the footprint rule on plain positions. The GameTests
 * check the numbers are applied in the world; this checks they are the right numbers.
 */
class GlimmerlessNumbersTest {
    @Test
    void mechanic() {
        assertEquals(200, Glimmerless.REVEAL_TICKS);
        assertEquals(8, Glimmerless.TELL_TICKS);
        assertEquals(1.0F, Glimmerless.STRIKE);
        // the calls made here: the rake holds 8 ticks, 30 between rakes, a flinch of 30 ticks and 3 blocks, a print every
        // 0.9 blocks that fades over 60 ticks, 12 at most, 0.18 off the line walked
        assertEquals(8, Glimmerless.STRIKE_TICKS);
        assertEquals(30, Glimmerless.COOLDOWN);
        assertEquals(0.9, Glimmerless.START_REACH);
        assertEquals(1.6, Glimmerless.STRIKE_REACH);
        assertEquals(120.0F, Glimmerless.STRIKE_ARC);
        assertEquals(30, Glimmerless.RECOIL_TICKS);
        assertEquals(3.0, Glimmerless.RECOIL_DISTANCE);
        assertEquals(0.15, Glimmerless.RECOIL_SPEED);
        assertEquals(0.9, Glimmerless.STRIDE);
        assertEquals(60, Glimmerless.PRINT_TICKS);
        assertEquals(12, Glimmerless.PRINTS_MAX);
        assertEquals(0.18, Glimmerless.PRINT_SIDE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("glimmerless");
        assertEquals(30.0F, row.health());
        assertEquals(7.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(1.9F, row.height());
        assertEquals(0, row.tellTicks(), "its row's tell is the footprints, not a timed pose");
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertEquals("shimmering", row.secondLook());
        assertEquals("void_ichor", row.material());
    }

    @Test
    void houseRules() {
        // every blow gets a tell of at least 8 ticks; the rake is 1 x Atk, under the 1.5 x that asks for 10; a reveal is
        // no longer than the 200-tick cap on effects; the reveal far outlasts the flinch, so most of it is a fight in
        // the open; a print lasts long enough to read a few strides of trail at a walk
        assertTrue(Glimmerless.TELL_TICKS >= 8);
        assertTrue(Glimmerless.STRIKE < 1.5F);
        assertTrue(Glimmerless.REVEAL_TICKS <= 200);
        assertTrue(Glimmerless.RECOIL_TICKS * 4 <= Glimmerless.REVEAL_TICKS);
        assertTrue(Glimmerless.START_REACH < Glimmerless.STRIKE_REACH, "a target that starts the tell is in reach of the rake");
        assertTrue(Glimmerless.PRINT_TICKS >= 40, "at least two seconds of trail");
    }

    // ---- footprints ----------------------------------------------------------------------------------------------------

    @Test
    void aPrintEveryStride() {
        Footprints f = new Footprints();
        int left = 0;
        // walking straight along +Z (yaw 0) a tenth of a block a tick for 4.7 blocks: one print each 0.9
        for (int t = 0; t <= 47; t++) if (f.track(0.0, 64.0, t * 0.1, true, 0.0F, t) != null) left++;
        assertEquals(5, left, "4.7 blocks at 0.9 a stride");
        assertEquals(5, f.prints().size());
        Footprints.Print first = f.prints().getFirst();
        assertEquals(64.0, first.y(), 1.0E-9, "on the ground under the feet");
        assertEquals(0.18, Math.abs(first.x()), 1.0E-9, "a foot off the line walked");
        assertEquals(-f.prints().get(1).side(), first.side(), "left and right in turn");
        assertEquals(-first.x(), f.prints().get(1).x(), 1.0E-9, "the other foot on the other side");
    }

    @Test
    void noPrintsOffTheGround() {
        Footprints f = new Footprints();
        for (int t = 0; t <= 40; t++) assertNull(f.track(0.0, 70.0, t * 0.2, false, 0.0F, t), "in the air at tick " + t);
        assertTrue(f.prints().isEmpty());
    }

    @Test
    void aTeleportLeavesNoPrint() {
        Footprints f = new Footprints();
        f.track(0.0, 64.0, 0.0, true, 0.0F, 0);
        assertNull(f.track(0.0, 64.0, 8.0, true, 0.0F, 1), "8 blocks in a tick is no step");
        assertNotNull(f.track(0.0, 64.0, 8.95, true, 0.0F, 2), "and walking on from there leaves prints again");
    }

    @Test
    void printsFadeAndGo() {
        assertEquals(1.0F, Footprints.fade(0), 1.0E-6);
        assertEquals(0.5F, Footprints.fade(30), 1.0E-6);
        assertEquals(0.0F, Footprints.fade(60), 1.0E-6);
        Footprints f = new Footprints();
        f.track(0.0, 64.0, 0.0, true, 0.0F, 0);
        assertNotNull(f.track(0.0, 64.0, 0.9, true, 0.0F, 10));
        f.expire(69);
        assertEquals(1, f.prints().size(), "still there 59 ticks on");
        f.expire(70);
        assertTrue(f.prints().isEmpty(), "gone 60 ticks on");
    }

    @Test
    void atMostTwelve() {
        Footprints f = new Footprints();
        for (int t = 0; t <= 200; t++) f.track(0.0, 64.0, t * 0.9, true, 0.0F, t);
        assertEquals(12, f.prints().size());
    }
}
