package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Gleamtrap;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * Gleamtrap's numbers against its design, written out as plain values: a pulse every 80 t; tell 10 t (the bite); it
 * bites what touches it, hits it, or stands within 1 block of it for 40 t, for 1.5 x Atk, reaching 1 block. The
 * GameTests check the numbers are applied; this checks they are the right numbers.
 */
class GleamtrapNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10, Gleamtrap.TELL_TICKS);
        assertEquals(80, Gleamtrap.PULSE_EVERY);
        assertEquals(40, Gleamtrap.LINGER_TICKS);
        assertEquals(1.0, Gleamtrap.REACH);
        assertEquals(1.5F, Gleamtrap.BITE_HIT);
    }

    @Test
    void stats() {
        // HP 24, Atk 7, Arm 4, 1.0 x 1.0, tell 10; strongest blow 1.5 x 7 = 10.5; fungus caps (no biome list), weight 8,
        // alone; second look Warped Gleamtrap; material siphon gel
        RosterStats.Entry row = RosterStats.get("gleamtrap");
        assertEquals(24.0F, row.health());
        assertEquals(7.0F, row.attack());
        assertEquals(4.0F, row.armor());
        assertEquals(1.0F, row.width());
        assertEquals(1.0F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(10.5F, row.blow());
        assertEquals(8, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("fungus_cap", row.spawner());
        assertEquals(RosterStats.Place.NONE, row.place());
        assertEquals("warped_gleamtrap", row.secondLook());
        assertEquals("siphon_gel", row.material());
        assertEquals(row.tellTicks(), Gleamtrap.TELL_TICKS);
        assertEquals(row.blow(), row.attack() * Gleamtrap.BITE_HIT, 1.0E-5F);
    }

    @Test
    void houseRules() {
        // a bite of 1.5 x Atk needs a tell of at least 10 ticks; the touch that springs it lies inside the bite's reach,
        // so a body that stays put is caught; a pulse ends long before the next one is due
        assertTrue(Gleamtrap.TELL_TICKS >= 10);
        assertTrue(Gleamtrap.TOUCH < Gleamtrap.REACH);
        assertTrue(Gleamtrap.PULSE_TICKS < Gleamtrap.PULSE_EVERY);
    }
}
