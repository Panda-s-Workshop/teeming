package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Recurrent;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Recurrent's numbers against its stat row, written out as plain values: HP 30, Atk 6, Arm 2, 0.7 x 1.9; no tell in
 * the row (its blow still gets 8 t); a killing blow winds it back to its place and health of 60 t ago, once per 60 t,
 * unless its anchor (4 HP, within 6 blocks) is broken; the End barrens, weight 3, alone; the Gilded Recurrent; time
 * sand. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class RecurrentNumbersTest {
    @Test
    void mechanic() {
        assertEquals(60, Recurrent.REWIND_BACK);
        assertEquals(60, Recurrent.REWIND_EVERY);
        assertEquals(4.0F, Recurrent.ANCHOR_HEALTH);
        assertEquals(6.0, Recurrent.LEASH);
    }

    @Test
    void ownCalls() {
        // the numbers its stat row leaves open: the blow's shape and pace, the daze, the anchor's size, pace and cycle
        assertEquals(8, Recurrent.TELL_TICKS);
        assertEquals(4, Recurrent.AIM_TICKS);
        assertEquals(8, Recurrent.STRIKE_TICKS);
        assertEquals(30, Recurrent.SWING_EVERY);
        assertEquals(1.0F, Recurrent.STRIKE);
        assertEquals(0.6, Recurrent.START_REACH);
        assertEquals(1.2, Recurrent.STRIKE_REACH);
        assertEquals(120.0F, Recurrent.STRIKE_ARC);
        assertEquals(20.0F, Recurrent.TURN);
        assertEquals(61, Recurrent.HISTORY);
        assertEquals(20, Recurrent.REWOUND_TICKS);
        assertEquals(0.45F, Recurrent.ANCHOR_WIDTH);
        assertEquals(0.7F, Recurrent.ANCHOR_HEIGHT);
        assertEquals(0.25, Recurrent.ANCHOR_SPEED);
        assertEquals(80, Recurrent.GUARD_TICKS);
        assertEquals(40, Recurrent.OUT_TICKS);
        assertEquals(120, Recurrent.CYCLE);
        assertEquals(1.4, Recurrent.GUARD_BACK);
        assertEquals(1.4, Recurrent.GUARD_UP);
        assertEquals(3.0, Recurrent.OUT_SIDE);
        assertEquals(0.8, Recurrent.OUT_UP);
        assertEquals(1.6, Recurrent.IDLE_RADIUS);
        assertEquals(1.5, Recurrent.IDLE_UP);
        assertEquals(100, Recurrent.PARTNER_LOST);
        assertEquals(0.25, Recurrent.SPEED);
        assertEquals(12, Recurrent.XP);
    }

    @Test
    void houseRules() {
        // every blow that hurts a player gets a tell of at least 8 ticks, and the aim locks with time left to step aside
        assertTrue(Recurrent.TELL_TICKS >= 8);
        assertTrue(Recurrent.AIM_TICKS < Recurrent.TELL_TICKS);
        // its blow is its plain attack, under 1.5x, so 8 ticks of tell is enough
        assertTrue(Recurrent.STRIKE < 1.5F);
        // a blow runs its course before the next wind-up may start
        assertTrue(Recurrent.TELL_TICKS + Recurrent.STRIKE_TICKS < Recurrent.SWING_EVERY);
        // it remembers one tick more than it winds back, so the place of exactly 60 ticks ago is kept
        assertEquals(Recurrent.REWIND_BACK + 1, Recurrent.HISTORY);
        // the daze is over well inside the 60 ticks a second kill is final
        assertTrue(Recurrent.REWOUND_TICKS < Recurrent.REWIND_EVERY);
        // the anchor's spots lie inside its leash, and out to the side it is low enough for a sword
        assertTrue(Math.hypot(Recurrent.GUARD_BACK, Recurrent.GUARD_UP) < Recurrent.LEASH);
        assertTrue(Math.hypot(Recurrent.OUT_SIDE, Recurrent.OUT_UP) < Recurrent.LEASH);
        assertTrue(Recurrent.OUT_UP + Recurrent.ANCHOR_HEIGHT < 1.8);
        // it is out of the guard a third of each cycle
        assertEquals(Recurrent.CYCLE, Recurrent.GUARD_TICKS + Recurrent.OUT_TICKS);
        // the flags it uses stay clear of the visual stage, which uses the top bits
        assertTrue(Recurrent.FLAG_ANCHOR < 24 && Recurrent.FLAG_BROKEN < 24 && Recurrent.FLAG_TRAIL < 24);
        assertTrue(Recurrent.Trail.END_BIT <= 24 && Recurrent.Trail.X_BIT > Recurrent.FLAG_TRAIL);
        // End experience: 10 to 20
        assertTrue(Recurrent.XP >= 10 && Recurrent.XP <= 20);
    }

    @Test
    void trail() {
        // the way back packs into the flags in half blocks, signed, and comes back out the same
        int bits = Recurrent.Trail.encode(-12.5, 3.0, 7.5);
        assertEquals(-12.5, Recurrent.Trail.x(bits));
        assertEquals(3.0, Recurrent.Trail.y(bits));
        assertEquals(7.5, Recurrent.Trail.z(bits));
        // too far is held at the edge, not wrapped round
        assertEquals(31.5, Recurrent.Trail.x(Recurrent.Trail.encode(80.0, 0.0, 0.0)));
        assertEquals(-7.5, Recurrent.Trail.y(Recurrent.Trail.encode(0.0, -20.0, 0.0)));
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("recurrent");
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(30.0F, row.health());
        assertEquals(6.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(1.9F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(3, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("gilded_recurrent", row.secondLook());
        assertEquals("time_sand", row.material());
    }
}
