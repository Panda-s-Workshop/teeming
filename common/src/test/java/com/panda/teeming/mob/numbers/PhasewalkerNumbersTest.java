package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Phasewalker;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Phasewalker's numbers against its row, written out as plain values: HP 32, Atk 7, Arm 2, 0.7 x 2.0, tell 10 (the
 * shimmer); 60 ticks solid, 60 ticks phased; End midlands, weight 4, alone; second look Inverted Walker; drops time
 * sand. Then the calls made here: how fast it floats through blocks, how it looks for open ground, its 8-tick wind-up
 * and blow, and the crack and the leak. The GameTests check the numbers are applied in the world; this checks they are
 * the right numbers.
 */
class PhasewalkerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(60, Phasewalker.SOLID_TICKS);
        assertEquals(10, Phasewalker.SHIMMER_TICKS);
        assertEquals(60, Phasewalker.PHASED_TICKS);
        // the calls made here: a 0.15 block-a-tick float (9 blocks over a phased spell), stopping 0.4 short of its
        // target; out of a block it looks 6 blocks for open ground, every 10 ticks
        assertEquals(0.15, Phasewalker.PHASE_SPEED);
        assertEquals(0.4, Phasewalker.HOLD_GAP);
        assertEquals(6, Phasewalker.EXIT_RANGE);
        assertEquals(10, Phasewalker.EXIT_RETHINK);
        // an 8-tick wind-up and a 1 x Atk blow with its reach, arc, turn and cooldown
        assertEquals(8, Phasewalker.WINDUP_TICKS);
        assertEquals(6, Phasewalker.STRIKE_TICKS);
        assertEquals(20, Phasewalker.COOLDOWN);
        assertEquals(1.0F, Phasewalker.STRIKE);
        assertEquals(0.8, Phasewalker.START_REACH);
        assertEquals(1.4, Phasewalker.STRIKE_REACH);
        assertEquals(120.0F, Phasewalker.STRIKE_ARC);
        assertEquals(15.0F, Phasewalker.TURN);
        // the counter's payoff: 10 health in one solid spell cracks it; a cracked walker leaks 1 every 5 ticks phased,
        // never below 1
        assertEquals(10.0F, Phasewalker.CRACK_DAMAGE);
        assertEquals(1.0F, Phasewalker.BLEED);
        assertEquals(5, Phasewalker.BLEED_INTERVAL);
        assertEquals(1.0F, Phasewalker.BLEED_FLOOR);
        assertEquals(0, Phasewalker.FLAG_CRACKED);
        assertEquals(15, Phasewalker.SAND_STEPS);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("phasewalker");
        assertEquals(32.0F, row.health());
        assertEquals(7.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(2.0F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("inverted_walker", row.secondLook());
        assertEquals("time_sand", row.material());
        assertEquals(row.tellTicks(), Phasewalker.SHIMMER_TICKS);
    }

    @Test
    void houseRules() {
        // every tell at least 8 ticks (the blow is 1 x Atk, under the 1.5 x that asks for 10)
        assertTrue(Phasewalker.SHIMMER_TICKS >= 8);
        assertTrue(Phasewalker.WINDUP_TICKS >= 8);
        assertTrue(Phasewalker.STRIKE < 1.5F);
        assertTrue(Phasewalker.START_REACH < Phasewalker.STRIKE_REACH);
        // a wind-up started early enough always lands inside the solid spell
        assertTrue(Phasewalker.WINDUP_TICKS < Phasewalker.SOLID_TICKS);
        // the leak over one phased spell (12) is a real cost, but under half its health
        float leak = (Phasewalker.PHASED_TICKS / Phasewalker.BLEED_INTERVAL) * Phasewalker.BLEED;
        assertEquals(12.0F, leak);
        assertTrue(leak < 32.0F / 2.0F);
        // the crack needs more than a weak blow but no more than one full End blow (11 through armour 2 is 10.824)
        assertTrue(Phasewalker.CRACK_DAMAGE <= 10.824F);
        assertTrue(Phasewalker.CRACK_DAMAGE > 6.0F);
    }
}
