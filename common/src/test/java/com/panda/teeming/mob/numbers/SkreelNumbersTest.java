package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.overworld.Skreel;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Skreel's numbers against its roster row, written out as plain values: HP 16, Atk 5, Arm 0, 0.9 x 0.7; screech
 * 20 t; dives from 10 to 20 above its target; a shield block stuns it 60 t, taking x1.5; windswept hills and stony and
 * jagged peaks, weight 10, groups of 1 to 2, any light; second look Snowy Skreel; drops gale essence. The dive's own
 * numbers (1.5x Atk, 1 b/t, 80 t between dives) are its design. The GameTests check the numbers are applied; this
 * checks they are the right numbers.
 */
class SkreelNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, Skreel.TELL_TICKS);
        assertEquals(10.0, Skreel.MIN_HEIGHT);
        assertEquals(20.0, Skreel.MAX_HEIGHT);
        assertEquals(60, Skreel.STUN_TICKS);
        assertEquals(1.5F, Skreel.STUN_DAMAGE);
        assertEquals(1.5F, Skreel.DIVE_HIT);
        assertEquals(1.0, Skreel.DIVE_SPEED);
        assertEquals(80, Skreel.COOLDOWN);
    }

    @Test
    void circlesInsideTheDiveBand() {
        assertTrue(Skreel.CRUISE_MIN >= Skreel.MIN_HEIGHT && Skreel.CRUISE_MAX <= Skreel.MAX_HEIGHT,
                "it circles at heights it may dive from");
        assertTrue(Skreel.DIVE_TICKS * Skreel.DIVE_SPEED >= Math.hypot(Skreel.MAX_HEIGHT, Skreel.DIVE_REACH),
                "a dive is long enough to reach the target from the far corner of its band");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("skreel");
        assertEquals(16.0F, row.health());
        assertEquals(5.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.9F, row.width());
        assertEquals(0.7F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(10, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertTrue(row.anyLight());
        assertEquals("snowy_skreel", row.secondLook());
        assertEquals("gale_essence", row.material());
    }
}
