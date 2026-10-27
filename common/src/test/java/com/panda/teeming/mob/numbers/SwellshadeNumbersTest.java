package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Swellshade;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Swellshade's numbers against its row, written out as plain values: HP 24 growing to 48, Atk 4 growing to 8, Arm
 * 0, 0.8 x 1.6 growing to twice that, no tell on the row; every 200 ticks +0.25 scale and +25 % health and damage, up to
 * x2; End midlands, weight 4, alone; second look Gloom Swell; drops void ichor. Then the calls made here: the 16-tick
 * breath and 10-tick pulse round a swell, the blow's 8-tick wind-up and its reach, the experience and the extra ichor
 * by size. The GameTests check the numbers are applied in the world; this checks they are the right numbers.
 */
class SwellshadeNumbersTest {
    @Test
    void mechanic() {
        assertEquals(200, Swellshade.GROW_EVERY);
        assertEquals(0.25, Swellshade.GROW_STEP);
        assertEquals(4, Swellshade.MAX_STEPS);
        // the calls made here: a breath and a pulse round each swell so it reads, an 8-tick wind-up before a 1 x Atk
        // blow whose reach grows with it, 10 to 20 experience and up to 2 more void ichor by size
        assertEquals(16, Swellshade.INHALE_TICKS);
        assertEquals(10, Swellshade.PULSE_TICKS);
        assertEquals(8, Swellshade.WINDUP_TICKS);
        assertEquals(6, Swellshade.STRIKE_TICKS);
        assertEquals(20, Swellshade.COOLDOWN);
        assertEquals(1.0F, Swellshade.STRIKE);
        assertEquals(0.6, Swellshade.START_REACH);
        assertEquals(1.2, Swellshade.STRIKE_REACH);
        assertEquals(120.0F, Swellshade.STRIKE_ARC);
        assertEquals(15.0F, Swellshade.TURN);
        assertEquals(10, Swellshade.XP_SMALL);
        assertEquals(20, Swellshade.XP_FULL);
        assertEquals(2, Swellshade.FULL_EXTRA_ICHOR);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("swellshade");
        assertEquals(24.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(1.6F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("gloom_swell", row.secondLook());
        assertEquals("void_ichor", row.material());
        // full size is x2: 24 -> 48 health, 4 -> 8 attack
        assertEquals(48.0, row.health() * (1.0 + Swellshade.GROW_STEP * Swellshade.MAX_STEPS), 1.0E-9);
        assertEquals(8.0, row.attack() * (1.0 + Swellshade.GROW_STEP * Swellshade.MAX_STEPS), 1.0E-9);
    }

    @Test
    void houseRules() {
        // every tell at least 8 ticks (the blow is 1 x Atk, under the 1.5 x that asks for 10); the breath ends on the swell,
        // inside the wait; the blow reaches farther than the wind-up starts
        assertTrue(Swellshade.WINDUP_TICKS >= 8);
        assertTrue(Swellshade.STRIKE < 1.5F);
        assertTrue(Swellshade.INHALE_TICKS < Swellshade.GROW_EVERY);
        assertTrue(Swellshade.START_REACH < Swellshade.STRIKE_REACH);
        assertTrue(Swellshade.XP_SMALL >= 10 && Swellshade.XP_FULL <= 20, "End experience stays in 10..20");
    }
}
