package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.VoidBloom;
import org.junit.jupiter.api.Test;

/**
 * Void Bloom's mechanic numbers against the design, written out as plain values: the stain grows 0.5 every 40 ticks to
 * radius 8; inside, 1 damage a second and Weakness; it recedes on death. The GameTests check the numbers are applied;
 * this checks they are the right numbers.
 */
class VoidBloomNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(0.5, VoidBloom.GROW_STEP);
        assertEquals(40, VoidBloom.GROW_EVERY);
        assertEquals(8.0, VoidBloom.MAX_RADIUS);
        assertEquals(1.0F, VoidBloom.STAIN_DAMAGE);
        assertEquals(20, VoidBloom.DAMAGE_EVERY);
        assertEquals(0, VoidBloom.WEAKNESS_LEVEL);
    }

    @Test
    void chosenNumbers() {
        // not in the design row: where the stain starts, the tell, how long Weakness lingers, the recede and the stain's
        // reach up and down are this mob's own calls
        assertEquals(0.5, VoidBloom.START_RADIUS);
        assertEquals(8, VoidBloom.TELL_TICKS);
        assertEquals(20, VoidBloom.WEAKNESS_TICKS);
        assertEquals(10, VoidBloom.WEAKNESS_TOP_UP);
        assertEquals(40, VoidBloom.RECEDE_TICKS);
        assertEquals(1.0, VoidBloom.STAIN_BELOW);
        assertEquals(1.5, VoidBloom.STAIN_ABOVE);
    }

    @Test
    void houseRules() {
        // every mechanic that hurts a player has a tell of at least 8 ticks, and it fits inside a growth step
        assertTrue(VoidBloom.TELL_TICKS >= 8);
        assertTrue(VoidBloom.TELL_TICKS < VoidBloom.GROW_EVERY);
        // the widest stain is a whole number of steps from the first
        double steps = (VoidBloom.MAX_RADIUS - VoidBloom.START_RADIUS) / VoidBloom.GROW_STEP;
        assertEquals(15.0, steps);
        // no effect over 200 ticks; Weakness is topped up before it runs out
        assertTrue(VoidBloom.WEAKNESS_TICKS <= 200 && VoidBloom.WEAKNESS_TOP_UP < VoidBloom.WEAKNESS_TICKS);
        // a jump (1.25 blocks) doesn't clear the stain
        assertTrue(VoidBloom.STAIN_ABOVE > 1.25);
    }
}
