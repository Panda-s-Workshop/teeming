package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Prismback;
import org.junit.jupiter.api.Test;

/**
 * Prismback's mechanic numbers against the design, written out as plain values: the flash (its tell) lasts 20 t and
 * starts every 120 t; turned projectiles fly back at 0.8 of their speed; a melee blow during the flash sends half back.
 * Its own choices: a 10 t glimmer before each flash, 40 t spent after a flash that turned nothing, a 0.2-block belly
 * line. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class PrismbackNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(20, Prismback.FLASH_TICKS);
        assertEquals(120, Prismback.FLASH_EVERY);
        assertEquals(0.8, Prismback.REFLECT_SPEED);
        assertEquals(0.5F, Prismback.MELEE_REFLECT);
    }

    @Test
    void ownNumbers() {
        assertEquals(10, Prismback.GLIMMER_TICKS);
        assertEquals(40, Prismback.SPENT_TICKS);
        assertEquals(0.2, Prismback.BELLY);
        assertEquals(0xEDE4FF, Prismback.FLASH_COLOR);
    }

    @Test
    void houseRules() {
        // a tell lasts at least 8 ticks; the glimmer, the flash and a spent spell all fit inside one cycle
        assertTrue(Prismback.FLASH_TICKS >= 8 && Prismback.GLIMMER_TICKS >= 8);
        assertTrue(Prismback.GLIMMER_TICKS + Prismback.FLASH_TICKS + Prismback.SPENT_TICKS < Prismback.FLASH_EVERY);
        // the flash's white-violet is no weak-point gold (#ffd36b): its blue channel is high, gold's is low
        assertTrue((Prismback.FLASH_COLOR & 0xFF) > 0xC0);
    }
}
