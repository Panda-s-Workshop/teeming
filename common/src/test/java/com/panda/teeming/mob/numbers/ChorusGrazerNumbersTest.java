package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.ChorusGrazer;
import com.panda.teeming.mob.end.ChorusGrazer.Blink;
import com.panda.teeming.roster.RosterStats;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The Chorus Grazer's numbers against its row in RosterStats, written out as plain values: a creature, HP 24, Atk 0,
 * Arm 0, 1.1 x 1.3, no tell; startled, the herd blinks 4-8 blocks; shears take 1-2 purpur fleece; it eats chorus fruit
 * lying on the ground; end highlands, weight 6, groups of 2-4; Pale Grazer; purpur fleece. The GameTests check the
 * numbers are applied; this checks they are the right numbers, and that the blink's safe-ground maths never lands over
 * a gap, the void, a fluid or a hazard.
 */
class ChorusGrazerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(1, ChorusGrazer.FLEECE_MIN);
        assertEquals(2, ChorusGrazer.FLEECE_MAX);
        assertEquals(4.0, ChorusGrazer.BLINK_MIN);
        assertEquals(8.0, ChorusGrazer.BLINK_MAX);
        assertEquals(16.0, ChorusGrazer.HERD_RANGE);
        assertEquals(100, ChorusGrazer.SKITTISH_TICKS);
        assertEquals(3.0, ChorusGrazer.STARTLE_RANGE);
        assertEquals(8.0, ChorusGrazer.FRUIT_RANGE);
    }

    /** Its own numbers, not in the row: the sheep's where it has one (love, breeding, calves). */
    @Test
    void sheepLike() {
        assertEquals(20, ChorusGrazer.SHEAR_TICKS);
        assertEquals(30, ChorusGrazer.EAT_TICKS);
        assertEquals(1.5, ChorusGrazer.EAT_REACH);
        assertEquals(6.0, ChorusGrazer.SHY_RANGE);
        assertEquals(1.3, ChorusGrazer.SHY_SPEED);
        assertEquals(600, ChorusGrazer.LOVE_TICKS);
        assertEquals(8.0, ChorusGrazer.MATE_RANGE);
        assertEquals(3.0, ChorusGrazer.MATE_TOUCH);
        assertEquals(60, ChorusGrazer.MATE_TICKS);
        assertEquals(6000, ChorusGrazer.BREED_COOLDOWN);
        assertEquals(24000, ChorusGrazer.GROW_TICKS);
        assertEquals(0.1, ChorusGrazer.CALF_FEED_SHARE);
        assertEquals(1200, ChorusGrazer.CALF_EAT_GROWTH);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("chorus_grazer");
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Cat.CREATURE, row.cat());
        assertEquals(24.0F, row.health());
        assertEquals(0.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(1.1F, row.width());
        assertEquals(1.3F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(6, row.weight());
        assertEquals(2, row.groupMin());
        assertEquals(4, row.groupMax());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertTrue(row.anyLight());
        assertEquals("pale_grazer", row.secondLook());
        assertEquals("purpur_fleece", row.material());
    }

    // ---- the blink's safe ground, on mock columns (cells by height above the feet's starting cell) -----------------

    /** Flat ground: solid below the feet, air above. */
    private static final IntUnaryOperator FLAT = d -> d < 0 ? Blink.SOLID : Blink.AIR;

    /** Ground {@code below} blocks under the starting floor. */
    private static IntUnaryOperator lowered(int below) {
        return d -> d < -below ? Blink.SOLID : Blink.AIR;
    }

    /** Open air down to the bottom of the world: the void under an island's edge. */
    private static final IntUnaryOperator VOID = d -> d < -10 ? Blink.VOID : Blink.AIR;

    @Test
    void heights() {
        assertArrayEquals(new int[] {0, -1, 1, -2, 2, -3, -4}, Blink.heights());
        assertEquals(2, Blink.MAX_RISE);
        assertEquals(4, Blink.MAX_DROP);
    }

    @Test
    void landsOnGround() {
        assertEquals(0, Blink.landing(new IntUnaryOperator[] {FLAT}, 2));
        assertEquals(-3, Blink.landing(new IntUnaryOperator[] {lowered(3)}, 2), "a 3-block step down is taken");
        assertEquals(-4, Blink.landing(new IntUnaryOperator[] {lowered(4)}, 2), "4 down is the furthest it reaches");
        assertEquals(Blink.NONE, Blink.landing(new IntUnaryOperator[] {lowered(5)}, 2), "5 down is out of reach, as the void is");
        IntUnaryOperator ledge = d -> d < 2 ? Blink.SOLID : Blink.AIR;
        assertEquals(2, Blink.landing(new IntUnaryOperator[] {ledge}, 2), "it lands on top of a 2-block rise");
    }

    @Test
    void neverOverTheVoid() {
        assertEquals(Blink.NONE, Blink.landing(new IntUnaryOperator[] {VOID}, 2), "no ground: no landing");
        // the island's edge: one column of the footprint has ground, the next is over the void
        assertEquals(Blink.NONE, Blink.landing(new IntUnaryOperator[] {FLAT, VOID}, 2), "never half over the edge");
        assertEquals(Blink.NONE, Blink.landing(new IntUnaryOperator[] {FLAT, FLAT, FLAT, VOID}, 2));
        // a step in the footprint: one column a block lower, so no height has ground under every column
        assertEquals(Blink.NONE, Blink.landing(new IntUnaryOperator[] {FLAT, lowered(1)}, 2));
        assertEquals(Blink.NONE, Blink.landing(new IntUnaryOperator[0], 2), "a footprint of no columns stands nowhere");
    }

    @Test
    void neverInFluidOrHazard() {
        IntUnaryOperator lavaFloor = d -> d < -1 ? Blink.SOLID : d == -1 ? Blink.LAVA : Blink.AIR;
        IntUnaryOperator water = d -> d < 0 ? Blink.SOLID : d == 0 ? Blink.LIQUID : Blink.AIR;
        IntUnaryOperator magma = d -> d < -1 ? Blink.SOLID : d == -1 ? Blink.HAZARD : Blink.AIR;
        IntUnaryOperator low = d -> d < 0 ? Blink.SOLID : d == 1 ? Blink.SOLID : Blink.AIR;
        assertEquals(Blink.NONE, Blink.landing(new IntUnaryOperator[] {lavaFloor}, 2), "never into lava");
        assertEquals(Blink.NONE, Blink.landing(new IntUnaryOperator[] {water}, 2), "never with its feet in water");
        assertEquals(Blink.NONE, Blink.landing(new IntUnaryOperator[] {magma}, 2), "never on a hazard");
        assertEquals(2, Blink.landing(new IntUnaryOperator[] {low}, 2), "never under a ceiling too low for it: on top of it instead");
        assertEquals(0, Blink.landing(new IntUnaryOperator[] {low}, 1), "a calf's single block of room is enough");
    }

    @Test
    void lengths() {
        assertArrayEquals(new double[] {6.5, 5.5, 4.5, 7.5}, Blink.distances(6.5, 4.0, 8.0), 1.0E-9);
        assertArrayEquals(new double[] {4.0, 5.0, 6.0, 7.0, 8.0}, Blink.distances(4.0, 4.0, 8.0), 1.0E-9);
        assertArrayEquals(new double[] {8.0, 7.0, 6.0, 5.0, 4.0}, Blink.distances(8.0, 4.0, 8.0), 1.0E-9);
        assertArrayEquals(new double[] {8.0, 7.0, 6.0, 5.0, 4.0}, Blink.distances(11.0, 4.0, 8.0), 1.0E-9, "never past 8");
        for (double start = 4.0; start <= 8.0; start += 0.25) {
            for (double d : Blink.distances(start, 4.0, 8.0)) assertTrue(d >= 4.0 && d <= 8.0, "every length within 4 to 8, got " + d);
        }
        assertEquals(0, Blink.OFFSETS[0], "straight away first");
        assertEquals(12, Blink.OFFSETS.length);
        assertEquals(90, Blink.AHEAD, "the half facing away is tried first");
    }
}
