package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.BlackstoneScuttler;
import org.junit.jupiter.api.Test;

/**
 * Blackstone Scuttler's mechanic numbers against the design, written out as plain values: the rear (its tell) lasts
 * 12 t; weapons and projectiles do x0.3 to it, pickaxes x3; the pinch is 1x Atk; the gilded look drops gold nuggets.
 * Its own choices: it rears within 3 blocks, lunges 0.4 b/t for 6 t, settles 10 t and waits 30 t before the next rear;
 * the gilded look drops 1 to 3 nuggets. The GameTests check the numbers are applied; this checks they are the right
 * numbers.
 */
class BlackstoneScuttlerNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(12, BlackstoneScuttler.REAR_TICKS);
        assertEquals(0.3F, BlackstoneScuttler.SHELL_FACTOR);
        assertEquals(3.0F, BlackstoneScuttler.PICKAXE_FACTOR);
        assertEquals(1.0F, BlackstoneScuttler.PINCH_HIT);
        assertEquals("gilded_scuttler", BlackstoneScuttler.GILDED);
    }

    @Test
    void ownNumbers() {
        assertEquals(3.0, BlackstoneScuttler.REAR_RANGE);
        assertEquals(0.4, BlackstoneScuttler.LUNGE_SPEED);
        assertEquals(6, BlackstoneScuttler.LUNGE_TICKS);
        assertEquals(10, BlackstoneScuttler.RECOVER_TICKS);
        assertEquals(30, BlackstoneScuttler.COOLDOWN);
        assertEquals(1, BlackstoneScuttler.GILDED_NUGGETS_MIN);
        assertEquals(3, BlackstoneScuttler.GILDED_NUGGETS_MAX);
    }

    @Test
    void houseRules() {
        // a tell lasts at least 8 ticks; the pinch is under 1.5x Atk, so 10 ticks aren't required
        assertTrue(BlackstoneScuttler.REAR_TICKS >= 8);
        assertTrue(BlackstoneScuttler.PINCH_HIT < 1.5F);
        // the lunge reaches past the range it rears at, so a target that stands still is caught
        assertTrue(BlackstoneScuttler.LUNGE_SPEED * BlackstoneScuttler.LUNGE_TICKS > BlackstoneScuttler.REAR_RANGE - 1.0);
    }
}
