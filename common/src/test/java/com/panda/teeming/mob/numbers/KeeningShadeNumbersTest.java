package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.KeeningShade;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Keening Shade's numbers against its row in RosterStats, written out as plain values: HP 20, Atk 3, Arm 0, 0.6 x 1.8;
 * a 16 t breath; a wail of radius 10 with line of sight that Silences for 80 t, every 200 t; soul sand valley, weight
 * 10, groups of 1; second look Hollow Shade; drops soul vertebra. The 200 t voiceless after a wail that silences no one,
 * and the wait after it, are its design. The GameTests check the numbers are applied; this checks they are the right
 * numbers.
 */
class KeeningShadeNumbersTest {
    @Test
    void mechanic() {
        assertEquals(16, KeeningShade.TELL_TICKS);
        assertEquals(10.0, KeeningShade.WAIL_RADIUS);
        assertEquals(80, KeeningShade.SILENCE_TICKS);
        assertEquals(200, KeeningShade.WAIL_EVERY);
    }

    @Test
    void tellIsLongEnough() {
        // the wail deals no damage, so a quick tell's 8 ticks is the floor; the breath is twice that
        assertTrue(KeeningShade.TELL_TICKS >= 8, "tell " + KeeningShade.TELL_TICKS);
    }

    @Test
    void theCycleFitsInsideItsInterval() {
        // breath and wail are over well before the next breath may start
        assertTrue(KeeningShade.TELL_TICKS + KeeningShade.WAIL_TICKS < KeeningShade.WAIL_EVERY);
        // the ring reaches the full radius within the wail, and rings once more
        assertEquals(2 * KeeningShade.RING_TICKS, KeeningShade.WAIL_TICKS);
        // a Silenced player is free again well before the next wail
        assertTrue(KeeningShade.SILENCE_TICKS < KeeningShade.WAIL_EVERY);
    }

    @Test
    void voicelessIsAWholeQuietCycle() {
        // a torn voice: 10 s without a wail, then the ordinary wait: twenty seconds in which no one is Silenced
        assertEquals(200, KeeningShade.VOICELESS_TICKS);
        assertEquals(200, KeeningShade.AFTER_VOICELESS);
    }

    @Test
    void aFreshShadeGathersItselfFirst() {
        // never on the tick it appears, and well inside the first ordinary wait
        assertEquals(10, KeeningShade.FIRST_WAIL);
        assertTrue(KeeningShade.FIRST_WAIL < KeeningShade.WAIL_EVERY);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("keening_shade");
        assertEquals(20.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(1.8F, row.height());
        assertEquals(16, row.tellTicks());
        assertEquals(10, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("hollow_shade", row.secondLook());
        assertEquals("soul_vertebra", row.material());
    }
}
