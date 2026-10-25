package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Aurovore;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * Aurovore's numbers against its design, written out as plain values: a 10 t sniff, then a bite for 1 x Atk that
 * steals one item and sends it running for 200 t. The GameTests check the numbers are applied; this checks they are the
 * right numbers.
 */
class AurovoreNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10, Aurovore.TELL_TICKS);
        assertEquals(1, Aurovore.STEAL_COUNT);
        assertEquals(200, Aurovore.FLEE_TICKS);
        assertEquals(1.0F, Aurovore.BITE);
        // the calls made here: the jaws stay shut 6 t after a bite that took nothing, 20 t between bites, it starts
        // sniffing within 0.8 blocks and bites within 1.2 across a 120 degree arc, three pieces of its hoard on show
        assertEquals(6, Aurovore.BITE_TICKS);
        assertEquals(20, Aurovore.COOLDOWN);
        assertEquals(0.8, Aurovore.START_REACH);
        assertEquals(1.2, Aurovore.BITE_REACH);
        assertEquals(120.0F, Aurovore.BITE_ARC);
        assertEquals(1.5, Aurovore.FLEE_SPEED);
        assertEquals(3, Aurovore.SHOWN);
    }

    @Test
    void stats() {
        // HP 24, Atk 4, Arm 4, 0.9 x 0.7, tell 10; no blow above its bite; nether wastes (and the bastion rule), weight 6, alone
        RosterStats.Entry row = RosterStats.get("aurovore");
        assertEquals(24.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(4.0F, row.armor());
        assertEquals(0.9F, row.width());
        assertEquals(0.7F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(6, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("bastion", row.spawner());
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals("gilded_aurovore", row.secondLook());
        assertEquals("gilded_gizzard", row.material());
    }

    @Test
    void houseRules() {
        // a tell of at least 8 t; a blow under 1.5 x Atk (so a 10 t tell is enough); no effect over 200 t; it bites as far
        // as it starts sniffing and farther; the jaws reopen before the next sniff
        assertTrue(Aurovore.TELL_TICKS >= 8);
        assertTrue(Aurovore.BITE < 1.5F);
        assertTrue(Aurovore.FLEE_TICKS <= 200);
        assertTrue(Aurovore.BITE_REACH >= Aurovore.START_REACH);
        assertTrue(Aurovore.BITE_TICKS < Aurovore.COOLDOWN);
        assertTrue(Aurovore.STEAL_COUNT >= 1);
    }
}
