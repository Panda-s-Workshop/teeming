package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.ScarletMantis;
import org.junit.jupiter.api.Test;

/**
 * Scarlet Mantis's mechanic numbers against the design, written out as plain values: a 12 t tell before a parry stance
 * that comes about every 60 t; a blow caught in it staggers the attacker with Slowness III and no attacks for 30 t.
 * Its own choices: a 24 t stance, a beat of 60 give or take 8, a first tell 20 to 40 t into a fight, Weakness V as the
 * "no attacks", a 6 t blade snap, the glints' red. The GameTests check the numbers are applied; this checks they are the
 * right numbers.
 */
class ScarletMantisNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(12, ScarletMantis.TELL_TICKS);
        assertEquals(60, ScarletMantis.STANCE_EVERY);
        assertEquals(30, ScarletMantis.STAGGER_TICKS);
        assertEquals(2, ScarletMantis.STAGGER_SLOWNESS);          // Slowness III
    }

    @Test
    void ownNumbers() {
        assertEquals(24, ScarletMantis.STANCE_TICKS);
        assertEquals(8, ScarletMantis.STANCE_SPREAD);
        assertEquals(20, ScarletMantis.FIRST_TELL);
        assertEquals(20, ScarletMantis.FIRST_SPREAD);
        assertEquals(4, ScarletMantis.STAGGER_WEAKNESS);          // Weakness V: -20 attack damage
        assertEquals(6, ScarletMantis.PARRY_SNAP_TICKS);
        assertEquals(0xFF3048, ScarletMantis.GLINT_COLOR);
    }

    @Test
    void houseRules() {
        // a tell lasts at least 8 ticks, and the stance is 20 to 30 ticks long
        assertTrue(ScarletMantis.TELL_TICKS >= 8);
        assertTrue(ScarletMantis.STANCE_TICKS >= 20 && ScarletMantis.STANCE_TICKS <= 30);
        // even the shortest beat leaves an open window after each stance before the next tell
        assertTrue(ScarletMantis.TELL_TICKS + ScarletMantis.STANCE_TICKS < ScarletMantis.STANCE_EVERY - ScarletMantis.STANCE_SPREAD);
        // Weakness V takes 20 off the attack, more than any unenchanted weapon deals (a netherite sword's 8 with Strength II's 6)
        assertTrue(4 * (ScarletMantis.STAGGER_WEAKNESS + 1) > 8 + 6);
        // the glints are no weak-point gold (#ffd36b): a hot red, its green channel low and blue above it
        int g = (ScarletMantis.GLINT_COLOR >> 8) & 0xFF, b = ScarletMantis.GLINT_COLOR & 0xFF;
        assertTrue(g < 0x60 && b > g);
    }
}
