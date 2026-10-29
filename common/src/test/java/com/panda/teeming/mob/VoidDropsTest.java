package com.panda.teeming.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The void rule on mock columns: a column with nothing solid down to the world's floor is the void, and one solid
 * block anywhere in it, the floor's own height included, is ground the drops land on.
 */
class VoidDropsTest {
    @Test
    void anEmptyColumnIsTheVoid() {
        assertTrue(VoidDrops.overVoid(31, 0, y -> false));
    }

    @Test
    void oneSolidBlockAnywhereBelowIsGround() {
        assertFalse(VoidDrops.overVoid(31, 0, y -> y == 30));
        assertFalse(VoidDrops.overVoid(31, 0, y -> y == 0));
        assertTrue(VoidDrops.overVoid(31, 0, y -> y == 32 || y == -1));
    }
}
