package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.MireGulper;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Mire Gulper's numbers against its roster row, written out as plain values: HP 28, Atk 4, Arm 2, 1.4 x 0.9;
 * bubbles for 20 t; the swallowed victim is chewed for Atk x 0.35 (1.4) every 10 t, for at most 100 t, 9 chews in all;
 * 3 hits make it spit the victim out and leave it stunned 40 t (x1.5 damage, as every stunned mob); swamps, weight 15,
 * alone. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class MireGulperNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, MireGulper.TELL_TICKS);
        assertEquals(0.35F, MireGulper.BITE_HIT);
        assertEquals(10, MireGulper.BITE_EVERY);
        assertEquals(100, MireGulper.HOLD_TICKS);
        assertEquals(9, MireGulper.CHEWS);
        assertEquals(3, MireGulper.HITS_TO_SPIT);
        assertEquals(40, MireGulper.STUN_TICKS);
        assertEquals(1.5F, MireGulper.STUN_DAMAGE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("mire_gulper");
        assertEquals(28.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(1.4F, row.width());
        assertEquals(0.9F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(15, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("mangrove_gulper", row.secondLook());
        assertEquals("bog_gland", row.material());
        assertEquals(RosterStats.Dim.OVERWORLD, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
    }
}
