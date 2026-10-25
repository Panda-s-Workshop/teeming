package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.overworld.Petalkin;
import com.panda.teeming.mob.overworld.Petalkin.Blink;
import com.panda.teeming.roster.RosterStats;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The Petalkin's numbers against its roster row, written out as plain values: HP 12, Atk 3, Arm 0, 0.5 x 0.9, no
 * tell; hit, it blinks 6 blocks and leaves 3 decoys for 100 t; cherry groves, weight 8, alone or in pairs, any light;
 * second look Snowpetal; drops petal dust. Then the blink's safe-ground rule on mock columns: never into the void, a
 * drop past its reach, lava, water, a hazard or a wall. The GameTests check the numbers are applied in the world; this
 * checks they are the right numbers.
 */
class PetalkinNumbersTest {
    private static final int A = Blink.AIR;
    private static final int S = Blink.SOLID;

    @Test
    void mechanic() {
        assertEquals(6.0, Petalkin.BLINK_RANGE);
        assertEquals(3, Petalkin.DECOY_COUNT);
        assertEquals(100, Petalkin.DECOY_TICKS);
        assertEquals(1.0F, Petalkin.PECK_HIT);
        assertEquals(10, Petalkin.BLINK_TICKS);
        assertEquals(2.0, Petalkin.BLINK_MIN);
        assertEquals(2, Blink.MAX_RISE);
        assertEquals(4, Blink.MAX_DROP);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("petalkin");
        assertEquals(12.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.5F, row.width());
        assertEquals(0.9F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(8, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertTrue(row.anyLight());
        assertEquals(RosterStats.Cat.CREATURE_NEUTRAL, row.cat());
        assertEquals("snowpetal", row.secondLook());
        assertEquals("petal_dust", row.material());
    }

    /** A column read from the bottom: {@code cells[i]} is the cell at height {@code from + i}; above is air, below is {@code below}. */
    private static IntUnaryOperator column(int from, int below, int... cells) {
        Map<Integer, Integer> at = new HashMap<>();
        for (int i = 0; i < cells.length; i++) at.put(from + i, cells[i]);
        return dy -> at.getOrDefault(dy, dy < from ? below : A);
    }

    @Test
    void flatGroundLandsLevel() {
        assertEquals(0, Blink.landing(column(-1, S, S, A, A), 1));
    }

    @Test
    void stepsDownAndUpWithinReach() {
        assertEquals(-1, Blink.landing(column(-2, S, S, A, A, A), 1), "a step down");
        assertEquals(1, Blink.landing(column(-1, S, S, S, A, A), 1), "a step up onto a block");
        assertEquals(-4, Blink.landing(column(-5, S, S, A, A, A, A, A), 1), "a drop of 4 is within reach");
        assertEquals(2, Blink.landing(column(-1, S, S, S, S, A, A), 1), "a ledge 2 up is within reach");
    }

    @Test
    void neverPastItsReach() {
        assertEquals(Blink.NONE, Blink.landing(column(-6, S, S, A, A, A, A, A, A, A), 1), "a drop of 5 is a cliff");
        assertEquals(Blink.NONE, Blink.landing(column(-1, S, S, S, S, S, A), 1), "a wall 3 high is too tall to land on");
    }

    @Test
    void neverIntoTheVoid() {
        assertEquals(Blink.NONE, Blink.landing(column(-8, Blink.VOID, Blink.VOID), 1), "no ground at all");
        assertEquals(Blink.NONE, Blink.landing(dy -> dy < -3 ? Blink.VOID : A, 1), "open air down to the bottom of the world");
    }

    @Test
    void neverIntoLavaOrWater() {
        assertEquals(Blink.NONE, Blink.landing(column(-2, S, S, Blink.LAVA, A, A), 1), "a lava floor");
        assertEquals(Blink.NONE, Blink.landing(column(-3, S, S, Blink.LAVA, Blink.LAVA, A, A), 1), "a lava pool two deep");
        assertEquals(Blink.NONE, Blink.landing(column(-2, S, S, Blink.LIQUID, A, A), 1), "water: no ground to stand on");
        assertEquals(Blink.NONE, Blink.landing(column(-1, S, Blink.HAZARD, A, A), 1), "a burning or pricking floor");
        assertEquals(Blink.NONE, Blink.landing(column(-1, S, S, Blink.HAZARD, A), 1), "fire where its feet would be");
    }

    @Test
    void neverIntoAWall() {
        assertEquals(Blink.NONE, Blink.landing(dy -> S, 1), "solid all the way up");
        assertFalse(Blink.standable(column(-1, S, S, S, A), 0, 1), "its feet would be in a block");
        assertFalse(Blink.standable(column(-1, S, S, A, S), 0, 2), "a body two blocks tall would be in the ceiling");
        assertTrue(Blink.standable(column(-1, S, S, A, S), 0, 1), "one block of room is enough for a small body");
    }

    @Test
    void prefersItsOwnHeight() {
        // ground both one below and one above: the nearer the start the better, and down before up
        IntUnaryOperator twoFloors = column(-2, S, S, A, S, A, A);
        assertEquals(-1, Blink.landing(twoFloors, 1));
        assertArrayEquals(new int[] {0, -1, 1, -2, 2, -3, -4}, Blink.heights());
    }

    @Test
    void triesFarFirstAndAllRound() {
        assertArrayEquals(new double[] {6.0, 5.0, 4.0, 3.0, 2.0}, Blink.distances(6.0, 2.0));
        assertEquals(12, Blink.OFFSETS.length);
        assertEquals(0, Blink.OFFSETS[0], "straight away from the threat first");
        assertEquals(180, Blink.OFFSETS[Blink.OFFSETS.length - 1], "toward it only last");
        for (int d = -150; d <= 180; d += 30) {
            int want = d;
            assertTrue(java.util.Arrays.stream(Blink.OFFSETS).anyMatch(o -> o == want), "tries " + want + " degrees");
        }
    }
}
