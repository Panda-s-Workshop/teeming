package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Spirebloom;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Spirebloom's numbers against its roster row, written out as plain values: HP 28, Atk 8, Arm 6, 0.9 x 1.5; a
 * 16-tick tell (cracks); every 80 ticks a spike line 10 long toward its target, 8 damage and a knock-up; End barrens,
 * weight 4, alone; second look Rose Spire; drops void crystal. The spike spacing and width, the throw's height and the
 * spikes left standing are its design. The GameTests check the numbers are applied; this checks they are the right
 * numbers.
 */
class SpirebloomNumbersTest {
    @Test
    void line() {
        assertEquals(16, Spirebloom.TELL_TICKS);
        assertEquals(80, Spirebloom.EVERY);
        assertEquals(10.0, Spirebloom.LINE_LENGTH);
        assertEquals(1.0, Spirebloom.LINE_START);
        assertEquals(0.5, Spirebloom.SPIKE_SPACING);
        assertEquals(0.5, Spirebloom.SPIKE_RADIUS);
        assertEquals(1.5, Spirebloom.SPIKE_HEIGHT);
        assertEquals(10.0, Spirebloom.RANGE);
        assertEquals(3.0, Spirebloom.RANGE_UP);
        assertEquals(10, Spirebloom.BURST_TICKS);
        assertTrue(Spirebloom.TELL_TICKS + Spirebloom.BURST_TICKS < Spirebloom.EVERY, "the burst ends before the next tell");
        // spikes every half block, each reaching half a block across: the line has no gaps to stand in
        assertTrue(Spirebloom.SPIKE_SPACING <= Spirebloom.SPIKE_RADIUS * 2.0);
        assertEquals(19, (int) Math.round((Spirebloom.LINE_LENGTH - Spirebloom.LINE_START) / Spirebloom.SPIKE_SPACING) + 1, "19 spikes");
    }

    @Test
    void blow() {
        // its blow is its attack (8), not 1.5x it, so an 8-tick tell is the floor; it takes 16, past the 10 for a big blow
        assertEquals(1.0F, Spirebloom.STRIKE);
        assertTrue(Spirebloom.TELL_TICKS >= 10);
        assertEquals(0.6, Spirebloom.KNOCK_UP);
        // a throw of 0.6 a tick rises about 2.2 blocks: the landing is under 3 blocks, so it costs no fall damage
        double v = 0.6, h = 0.0;
        while (v > 0) {
            h += v;
            v = (v - 0.08) * 0.98;
        }
        assertTrue(h < 3.0, "rises " + h);
    }

    @Test
    void standing() {
        assertEquals(60, Spirebloom.STAND_TICKS);
        assertTrue(Spirebloom.STAND_TICKS < Spirebloom.EVERY - Spirebloom.TELL_TICKS, "the spikes are gone before the next line bursts");
        assertEquals(0.4, Spirebloom.FEND_PUSH);
        assertEquals(12, Spirebloom.XP);
        assertTrue(Spirebloom.XP >= 10 && Spirebloom.XP <= 20, "End XP is 10 to 20");
    }

    @Test
    void theCracksAreDarkVioletNotGold() {
        int c = Spirebloom.DARK_VIOLET;
        int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
        assertTrue(b > r && r > g && r + g + b < 300, String.format("dark violet %06x", c));
        assertFalse(c == 0xFFD36B);
        assertEquals(0x4A1E86, c);
        assertEquals(0xEEF0FF, Spirebloom.STAR_WHITE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("spirebloom");
        assertEquals(79, row.number());
        assertEquals(28.0F, row.health());
        assertEquals(8.0F, row.attack());
        assertEquals(6.0F, row.armor());
        assertEquals(0.9F, row.width());
        assertEquals(1.5F, row.height());
        assertEquals(16, row.tellTicks());
        assertEquals(8.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("", row.spawner());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertEquals("rose_spire", row.secondLook());
        assertEquals("void_crystal", row.material());
        assertEquals(row.tellTicks(), Spirebloom.TELL_TICKS);
        assertEquals(row.blow(), row.attack() * Spirebloom.STRIKE);
    }
}
