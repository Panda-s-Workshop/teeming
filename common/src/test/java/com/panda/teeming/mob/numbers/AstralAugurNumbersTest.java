package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.AstralAugur;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Astral Augur's numbers against its roster row, written out as plain values: HP 34, Atk 4, Arm 2, 0.7 x 2.1;
 * channel 24 t; 3 circles of radius 1.5 on and near the target, 9 damage each; a hit breaks the channel; main island
 * (the spawner's rule) and End highlands, weight 4, groups of 1; second look Dusk Augur; drops star mote. A channel
 * every 100 t, the 16-block reach, the flank layout (2.6 blocks out, 50 degrees either side of straight away), the
 * 20-tick flinch, the halo of three motes with one never lost and the 10-tick backhand are its design. The GameTests
 * check the numbers are applied; this checks they are the right numbers, and the circles' layout maths.
 */
class AstralAugurNumbersTest {
    @Test
    void mechanic() {
        assertEquals(24, AstralAugur.TELL_TICKS);
        assertEquals(3, AstralAugur.CIRCLES);
        assertEquals(1.5, AstralAugur.CIRCLE_RADIUS);
        assertEquals(9.0F, AstralAugur.STRIKE_DAMAGE);
        assertEquals(100, AstralAugur.CAST_EVERY);
        assertEquals(16.0, AstralAugur.CAST_RANGE);
    }

    @Test
    void tellsAreLongEnough() {
        // 9 is more than 1.5x its attack of 4: the channel needs at least 10 ticks; the backhand (its attack) at least 8
        assertTrue(AstralAugur.TELL_TICKS >= 10, "channel " + AstralAugur.TELL_TICKS);
        assertTrue(AstralAugur.SWIPE_TICKS >= 8, "backhand wind-up " + AstralAugur.SWIPE_TICKS);
        assertEquals(10, AstralAugur.SWIPE_TICKS);
    }

    @Test
    void theHaloIsTheCircles() {
        // one circle per mote; a broken channel costs one, and the last is never lost
        assertEquals(AstralAugur.CIRCLES, AstralAugur.MOTES);
        assertEquals(1, AstralAugur.MIN_MOTES);
        assertEquals(20, AstralAugur.FLINCH_TICKS);
    }

    @Test
    void theCycleFitsInsideItsInterval() {
        // channel and strike are over well before the next channel may start
        assertTrue(AstralAugur.TELL_TICKS + AstralAugur.STRIKE_TICKS < AstralAugur.CAST_EVERY);
        assertTrue(AstralAugur.KEEP_AWAY < AstralAugur.CLOSE_IN && AstralAugur.CLOSE_IN < AstralAugur.CAST_RANGE,
                "it stands between " + AstralAugur.KEEP_AWAY + " and " + AstralAugur.CLOSE_IN + " blocks, inside its reach");
    }

    @Test
    void theFlanksLieBehindAndOverlapTheMiddle() {
        // three circles from a caster due west of the target: the first on the target, the flanks 2.6 blocks out at
        // 50 degrees either side of due east (straight away): (1.671, -1.992) and (1.671, 1.992)
        double[] xz = AstralAugur.GroundMarks.offsets(5.0, 0.0, 3, 2.6, 50.0);
        assertArrayEquals(new double[] {0.0, 0.0, 1.671, -1.992, 1.671, 1.992}, xz, 0.001);
        // two circles: the second straight behind; one: only the first
        assertArrayEquals(new double[] {0.0, 0.0, 2.6, 0.0}, AstralAugur.GroundMarks.offsets(5.0, 0.0, 2, 2.6, 50.0), 0.001);
        assertArrayEquals(new double[] {0.0, 0.0}, AstralAugur.GroundMarks.offsets(5.0, 0.0, 1, 2.6, 50.0), 0.001);
        // every flank's centre lies within two radii of the first, so the three leave no gap to stand in behind the target
        assertTrue(AstralAugur.FLANK_SPACING < 2 * AstralAugur.CIRCLE_RADIUS, "flanks overlap the middle circle");
        // stepping toward the augur by a radius always clears every circle: the flanks lie behind
        assertTrue(xz[2] > 0 && xz[4] > 0, "both flanks lie on the far side from the caster");
        assertEquals(50.0, AstralAugur.FLANK_SPREAD);
        assertEquals(2.6, AstralAugur.FLANK_SPACING);
    }

    @Test
    void theCirclesAreStarWhiteNotGold() {
        // weak-point gold is #ffd36b; the circles' light is a cool white with more blue than red
        int c = AstralAugur.STAR_WHITE;
        assertTrue((c & 0xFF) >= (c >> 16 & 0xFF), String.format("star white %06x has blue at least red", c));
        assertFalse(c == 0xFFD36B);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("astral_augur");
        assertEquals(34.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(2.1F, row.height());
        assertEquals(24, row.tellTicks());
        assertEquals(9.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("main_island", row.spawner());
        assertEquals("dusk_augur", row.secondLook());
        assertEquals("star_mote", row.material());
    }
}
