package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.RiftmineSapper;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Riftmine Sapper's numbers against its roster row, written out as plain values: HP 26, Atk 4, Arm 2, 0.7 x 1.4; a
 * mine (radius 1.5) every 100 ticks, at most 4; each arms over 40 ticks; set off, it launches whoever stands in it about
 * 10 blocks up; arrows set mines off; End barrens, weight 4, alone; second look Purpur Sapper; drops gravity stone. The
 * jab, its keeping its distance and the fizzle are its design. The GameTests check the numbers are applied; this checks
 * they are the right numbers.
 */
class RiftmineSapperNumbersTest {
    @Test
    void mines() {
        assertEquals(100, RiftmineSapper.PLANT_EVERY);
        assertEquals(4, RiftmineSapper.MAX_MINES);
        assertEquals(40, RiftmineSapper.ARM_TICKS);
        assertEquals(1.5, RiftmineSapper.MINE_RADIUS);
        assertEquals(1.0, RiftmineSapper.MINE_HEIGHT);
        assertEquals(20, RiftmineSapper.FIRST_PLANT);
        assertEquals(10.0, RiftmineSapper.PLANT_RANGE);
        assertEquals(10, RiftmineSapper.LEAD_TICKS);
        assertEquals(2.0, RiftmineSapper.MAX_LEAD);
        assertEquals(24.0, RiftmineSapper.MINE_LEASH);
        assertEquals(20, RiftmineSapper.FIZZLE_TICKS);
        assertTrue(RiftmineSapper.FIZZLE_TICKS <= 40, "its mines are gone within 40 ticks of its death");
        assertTrue(RiftmineSapper.ARM_TICKS >= 8, "the arming is a tell of at least 8 ticks");
        assertTrue(RiftmineSapper.MINE_LEASH > RiftmineSapper.PLANT_RANGE + RiftmineSapper.MAX_LEAD, "a fresh mine is never past the leash");
    }

    /** The launch: 1.35 blocks a tick straight up rises 10.09 blocks under living gravity (0.08 a tick, drag 0.98). */
    @Test
    void launch() {
        assertEquals(1.35, RiftmineSapper.LAUNCH_SPEED);
        double y = 0.0, v = 1.35, top = 0.0;
        for (int t = 0; t < 40 && v > 0.0; t++) {
            y += v;
            v = (v - 0.08) * 0.98;
            top = Math.max(top, y);
        }
        assertEquals(10.09, top, 0.01, "about 10 blocks up");
        // the fall from there: floor(10.09 - 3) = 7 damage (the game floors the fall past 3 blocks), survived twice by an
        // unarmoured player at 20
        assertEquals(7, (int) Math.floor(top - 3.0));
    }

    @Test
    void jab() {
        assertEquals(10, RiftmineSapper.TELL_TICKS);
        assertEquals(6, RiftmineSapper.JAB_TICKS);
        assertEquals(20, RiftmineSapper.JAB_COOLDOWN);
        assertEquals(36, RiftmineSapper.TELL_TICKS + RiftmineSapper.JAB_TICKS + RiftmineSapper.JAB_COOLDOWN, "a jab every 36 ticks at most");
        assertEquals(1.0, RiftmineSapper.JAB_REACH);
        // its jab is its attack (4), not 1.5x it: 8 ticks is the floor; it takes 10
        assertEquals(1.0F, RiftmineSapper.STRIKE);
        assertTrue(RiftmineSapper.STRIKE < 1.5F && RiftmineSapper.TELL_TICKS >= 8);
        assertEquals(10, RiftmineSapper.PLANT_TICKS);
    }

    @Test
    void body() {
        assertEquals(0.25, RiftmineSapper.WALK_SPEED);
        assertEquals(3.0, RiftmineSapper.KEEP_AWAY);
        assertEquals(8.0, RiftmineSapper.CLOSE_IN);
        assertEquals(12, RiftmineSapper.XP);
        assertTrue(RiftmineSapper.XP >= 10 && RiftmineSapper.XP <= 20, "End XP is 10 to 20");
    }

    @Test
    void theRingIsDarkVioletNotGold() {
        // weak-point gold is #ffd36b; the ring is dark violet: more blue than red, more red than green, and dark
        int c = RiftmineSapper.DARK_VIOLET;
        int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
        assertTrue(b > r && r > g && r + g + b < 300, String.format("dark violet %06x", c));
        assertFalse(c == 0xFFD36B);
        assertEquals(0x4A1E86, c);
        assertEquals(0xEEF0FF, RiftmineSapper.STAR_WHITE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("riftmine_sapper");
        assertEquals(80, row.number());
        assertEquals(26.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(1.4F, row.height());
        assertEquals(40, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("", row.spawner());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertEquals("purpur_sapper", row.secondLook());
        assertEquals("gravity_stone", row.material());
        assertEquals(row.tellTicks(), RiftmineSapper.ARM_TICKS);
    }
}
