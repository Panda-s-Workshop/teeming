package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Mnemovore;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Mnemovore's numbers against its row, written out as plain values: HP 28, Atk 5, Arm 2, 1.0 x 0.6, a 10-tick tell
 * (the maw); each bite steals 1 experience level, and on death it drops all it stole as orbs, plus 10 %; End barrens,
 * weight 4, groups of 1-2; second look Starved Mnemovore; drops void ichor. The GameTests check the numbers are applied
 * in the world; this checks they are the right numbers.
 */
class MnemovoreNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10, Mnemovore.GAPE_TICKS);
        assertEquals(1, Mnemovore.LEVELS_PER_BITE);
        assertEquals(1.1, Mnemovore.REPAY);
        assertEquals(1.0F, Mnemovore.BITE);
        // the calls made here: a gape every 1.5 s at most, started within 0.75 blocks of open space and landing within
        // 1.25; the bite and swallow poses; the glyphs full at four levels eaten
        assertEquals(30, Mnemovore.BITE_EVERY);
        assertEquals(0.75, Mnemovore.BITE_START);
        assertEquals(1.25, Mnemovore.BITE_HOLD);
        assertEquals(6, Mnemovore.BITE_TICKS);
        assertEquals(10, Mnemovore.SWALLOW_TICKS);
        assertEquals(4, Mnemovore.GLOW_FULL);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("mnemovore");
        assertEquals(28.0F, row.health());
        assertEquals(5.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(1.0F, row.width());
        assertEquals(0.6F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertEquals("starved_mnemovore", row.secondLook());
        assertEquals("void_ichor", row.material());
    }

    @Test
    void houseRules() {
        // the bite is 1 x Atk (under the 1.5 x that asks for 10 ticks) and still gets the row's 10-tick tell; it starts a
        // gape only where the bite would land; the gape and the bite fit inside one cooldown; it always gives back more
        // than it took
        assertTrue(Mnemovore.GAPE_TICKS >= 10);
        assertTrue(Mnemovore.BITE < 1.5F);
        assertTrue(Mnemovore.BITE_START <= Mnemovore.BITE_HOLD);
        assertTrue(Mnemovore.BITE_EVERY >= Mnemovore.GAPE_TICKS + Mnemovore.SWALLOW_TICKS);
        assertTrue(Mnemovore.REPAY > 1.0);
        assertTrue(Mnemovore.SWALLOW_TICKS < 40, "the swallow is a pose, not a punish window");
    }
}
