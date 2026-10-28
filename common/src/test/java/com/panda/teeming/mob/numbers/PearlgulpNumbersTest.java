package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Pearlgulp;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Pearlgulp's numbers against its row, written out as plain values: HP 22, Atk 8.5, Arm 2, 0.9 x 0.8, no tell in the
 * row (its bite and its blink toward you still get 8 ticks each); it eats ender pearls in flight within 8 blocks, with
 * no teleport, and each one gives it one blink of 8 blocks; End highlands and small islands, weight 4, alone; second
 * look Smoky Gulp; drops rift essence. The GameTests check the numbers are applied in the world; this checks they are
 * the right numbers.
 */
class PearlgulpNumbersTest {
    @Test
    void mechanic() {
        assertEquals(8.0, Pearlgulp.EAT_RANGE);
        assertEquals(8.0, Pearlgulp.BLINK_DISTANCE);
        assertEquals(8, Pearlgulp.TELL_TICKS);
        assertEquals(8, Pearlgulp.GAPE_TICKS);
        assertEquals(1.0F, Pearlgulp.BITE);
        // the calls made here: a pouch of one stack, a blink every 3 s at most, only at a target beyond 6 and within 16,
        // landing at least 1.25 from it; an escape under half health that lands 3 or more from any player; a gape every
        // second at most, started at 2 and landing within 2.5
        assertEquals(16, Pearlgulp.MAX_HELD);
        assertEquals(60, Pearlgulp.BLINK_EVERY);
        assertEquals(6.0, Pearlgulp.CLOSE_FROM);
        assertEquals(16.0, Pearlgulp.BLINK_REACH);
        assertEquals(1.25, Pearlgulp.LAND_GAP);
        assertEquals(3.0, Pearlgulp.NEAR_PLAYER);
        assertEquals(0.5F, Pearlgulp.ESCAPE_BELOW);
        assertEquals(20, Pearlgulp.BITE_EVERY);
        assertEquals(2.0, Pearlgulp.BITE_START);
        assertEquals(2.5, Pearlgulp.BITE_HOLD);
        assertEquals(6, Pearlgulp.BLINK_TICKS);
        assertEquals(6, Pearlgulp.GULP_TICKS);
        assertEquals(6, Pearlgulp.BITE_TICKS);
        assertEquals(0, Pearlgulp.GLOW_EMPTY);
        assertEquals(4, Pearlgulp.GLOW_FLARE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("pearlgulp");
        assertEquals(22.0F, row.health());
        assertEquals(8.5F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.9F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("smoky_gulp", row.secondLook());
        assertEquals("rift_essence", row.material());
    }

    @Test
    void houseRules() {
        // every move that hurts or moves a player has a tell of at least 8 ticks (the bite is 1 x Atk, under the 1.5 x
        // that asks for 10); a closing blink always lands nearer its target and never on it; it starts a gape only
        // where the bite would land; an escape lands next to no one; a blink can't outrun the reach it starts from
        assertTrue(Pearlgulp.TELL_TICKS >= 8);
        assertTrue(Pearlgulp.GAPE_TICKS >= 8);
        assertTrue(Pearlgulp.BITE < 1.5F);
        assertTrue(Pearlgulp.BITE_START <= Pearlgulp.BITE_HOLD);
        assertTrue(Pearlgulp.LAND_GAP > 0.9, "two 0.9-wide bodies never overlap");
        assertTrue(Pearlgulp.CLOSE_FROM < Pearlgulp.BLINK_DISTANCE, "a target just past its closing range is in one blink's reach");
        assertTrue(Pearlgulp.NEAR_PLAYER > Pearlgulp.BITE_HOLD, "an escape lands out of its own bite reach of anyone");
        assertTrue(Pearlgulp.BLINK_EVERY > Pearlgulp.TELL_TICKS + Pearlgulp.BLINK_TICKS);
        assertTrue(Pearlgulp.MAX_HELD <= 16, "never more than a stack: all of it comes back on death");
    }
}
