package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.effect.EffectNumbers;
import com.panda.teeming.mob.end.Rotfang;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Rotfang's numbers against its row, written out as plain values: HP 26, Atk 8.5, Arm 2, 0.8 x 0.8, a 10-tick tell
 * (the snarl); its bite gives Void Rot for 160 ticks (no natural regeneration, healing x0.5); End barrens, weight 5,
 * groups of 1-2; second look Blight Fang; drops void ichor. The GameTests check the numbers are applied in the world;
 * this checks they are the right numbers.
 */
class RotfangNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10, Rotfang.SNARL_TICKS);
        assertEquals(160, Rotfang.ROT_TICKS);
        assertEquals(1.0F, Rotfang.BITE);
        // Void Rot itself: 160 ticks long wherever it is given, healing at half
        assertEquals(160, EffectNumbers.VOID_ROT_TICKS);
        assertEquals(0.5F, EffectNumbers.VOID_ROT_HEALING);
        // the calls made here: a snarl every 1.5 s at most, started within 0.8 blocks of open space and landing within
        // 1.3; struck in the snarl it bites itself for its own attack and gnaws for 1.5 s; the bite pose; a pack shares
        // turns and targets within 12 blocks, handing a target on once a second
        assertEquals(30, Rotfang.BITE_EVERY);
        assertEquals(0.8, Rotfang.BITE_START);
        assertEquals(1.3, Rotfang.BITE_HOLD);
        assertEquals(1.0F, Rotfang.SELF_BITE);
        assertEquals(30, Rotfang.GNAW_TICKS);
        assertEquals(6, Rotfang.BITE_TICKS);
        assertEquals(12.0, Rotfang.PACK_RANGE);
        assertEquals(20, Rotfang.RALLY_EVERY);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("rotfang");
        assertEquals(26.0F, row.health());
        assertEquals(8.5F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(5, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertEquals("blight_fang", row.secondLook());
        assertEquals("void_ichor", row.material());
    }

    @Test
    void houseRules() {
        // the bite is 1 x Atk (under the 1.5 x that asks for 10 ticks) and still gets the row's 10-tick tell; it starts a
        // snarl only where the bite would land; the snarl and the bite fit inside one cooldown; the rot stays under the
        // 200-tick cap on effects; the gnaw is a flinch (under the 40 ticks a punish window starts at), and the bite it
        // gives itself is never more than the one it would have given
        assertTrue(Rotfang.SNARL_TICKS >= 10);
        assertTrue(Rotfang.BITE < 1.5F);
        assertTrue(Rotfang.BITE_START <= Rotfang.BITE_HOLD);
        assertTrue(Rotfang.BITE_EVERY >= Rotfang.SNARL_TICKS + Rotfang.BITE_TICKS);
        assertTrue(Rotfang.ROT_TICKS <= 200);
        assertTrue(Rotfang.GNAW_TICKS < 40, "the gnaw is a flinch, not a punish window");
        assertTrue(Rotfang.SELF_BITE <= Rotfang.BITE);
    }
}
