package com.panda.teeming.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The landing rule on mock columns (0 open, 1 solid, 2 lava): a drop lands when it meets a solid block before lava or
 * the world's floor; nothing down to the floor (the void) or lava first loses it.
 */
class VoidDropsTest {
    @Test
    void anEmptyColumnIsTheVoid() {
        assertFalse(VoidDrops.lands(31, 0, y -> 0));
    }

    @Test
    void oneSolidBlockAnywhereBelowIsGround() {
        assertTrue(VoidDrops.lands(31, 0, y -> y == 30 ? 1 : 0));
        assertTrue(VoidDrops.lands(31, 0, y -> y == 0 ? 1 : 0));
        assertFalse(VoidDrops.lands(31, 0, y -> y == 32 || y == -1 ? 1 : 0));
    }

    @Test
    void lavaBeforeGroundBurnsIt() {
        assertFalse(VoidDrops.lands(31, 0, y -> y == 20 ? 2 : y == 10 ? 1 : 0));
        assertFalse(VoidDrops.lands(31, 0, y -> y == 31 ? 2 : 0));
        assertTrue(VoidDrops.lands(31, 0, y -> y == 20 ? 1 : y == 10 ? 2 : 0));
    }
}
