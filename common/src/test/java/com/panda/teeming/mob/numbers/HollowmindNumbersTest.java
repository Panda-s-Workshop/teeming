package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Hollowmind;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Hollowmind's numbers against its roster row, written out as plain values: HP 14, Atk 4, Arm 0, 0.5 x 0.5; a
 * 20-tick latch tell; a ridden enderman gets +30 % speed and +50 % damage; after its host dies it has 100 ticks to
 * reach another enderman within 16 blocks; End highlands, weight 4, alone; second look Pale Mind; drops rift essence.
 * The 8-tick nip tell, the 6-tick bite, the once-a-second nip, the 2- and 3-block latch reach and the faster scurry are
 * its design. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class HollowmindNumbersTest {
    @Test
    void latch() {
        assertEquals(20, Hollowmind.LATCH_TICKS);
        assertEquals(16.0, Hollowmind.HOST_RANGE);
        assertEquals(2.0, Hollowmind.LATCH_START);
        assertEquals(3.0, Hollowmind.LATCH_REACH);
        assertTrue(Hollowmind.LATCH_START < Hollowmind.LATCH_REACH, "a host that steps back a little is still caught");
        assertEquals(20, Hollowmind.LATCH_RETRY);
    }

    @Test
    void host() {
        assertEquals(0.3, Hollowmind.HOST_SPEED);
        assertEquals(0.5, Hollowmind.HOST_DAMAGE);
        // an angry enderman's head rises 5 pixels
        assertEquals(0.3125, Hollowmind.ANGRY_HEAD_LIFT);
    }

    @Test
    void search() {
        assertEquals(100, Hollowmind.SEEK_TICKS);
        assertEquals(1.5, Hollowmind.SEEK_PACE);
        assertTrue(Hollowmind.SEEK_TICKS <= 200, "nothing it does lasts over 200 ticks");
    }

    @Test
    void nip() {
        // its bite is its attack (4), not 1.5x it: an 8-tick tell is the floor
        assertEquals(8, Hollowmind.NIP_TICKS);
        assertEquals(6, Hollowmind.BITE_TICKS);
        assertEquals(20, Hollowmind.NIP_EVERY);
        assertTrue(Hollowmind.NIP_TICKS + Hollowmind.BITE_TICKS < Hollowmind.NIP_EVERY, "a nip is over before the next may start");
    }

    @Test
    void theCoreIsLilacNotGold() {
        // weak-point gold is #ffd36b and is kept for the dome while it rides; the core is lilac: blue over red over green
        int c = Hollowmind.CORE_LILAC;
        int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
        assertTrue(b > r && r > g, String.format("lilac %06x", c));
        assertFalse(c == 0xFFD36B);
        assertEquals(0xC58CFF, c);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("hollowmind");
        assertEquals(14.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.5F, row.width());
        assertEquals(0.5F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("", row.spawner());
        assertEquals("pale_mind", row.secondLook());
        assertEquals("rift_essence", row.material());
    }
}
