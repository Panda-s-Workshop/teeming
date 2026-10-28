package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Riftjackal;
import com.panda.teeming.mob.end.Riftjackal.Blink;
import com.panda.teeming.mob.end.Riftjackal.SafeTeleport;
import com.panda.teeming.roster.RosterStats;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The Riftjackal's numbers against its row, written out as plain values: HP 26 (alpha 40), Atk 8.5, Arm 2, 0.8 x 0.9,
 * tell 8 (the crouch); it blinks to your side or back every 60 t, up to 4 blocks from you, and bites for its attack;
 * each rift lasts 60 t and throws whoever steps in 8 blocks away onto safe ground; End highlands, weight 4, packs of 2
 * to 3; second look Starbitten; drops rift essence. Then the safe-teleport rule on mock columns (never the void, a drop
 * past reach, lava, water, a hazard, a wall or under one block of headroom) and the blink's aim. The GameTests check
 * the numbers are applied in the world; this checks they are the right numbers.
 */
class RiftjackalNumbersTest {
    private static final int A = SafeTeleport.AIR;
    private static final int S = SafeTeleport.SOLID;

    @Test
    void mechanic() {
        assertEquals(8, Riftjackal.TELL_TICKS);
        assertEquals(60, Riftjackal.BLINK_EVERY);
        assertEquals(60, Riftjackal.RIFT_TICKS);
        assertEquals(8.0, Riftjackal.RIFT_THROW);
        assertEquals(4.0, Riftjackal.LAND_FAR);
        assertEquals(1.0F, Riftjackal.BITE);
        assertEquals(40.0F, Riftjackal.ALPHA_HEALTH);
        assertEquals(3, Riftjackal.PACK_MAX);
        // the calls made here: a third of 60 between crouches in a pack, landing 2 to 4 out, a bite reach of 2.5, seen
        // within 60 degrees, a 16-block reach to start a blink, the alpha 1.15x, a tear 1.8 x 1.0
        assertEquals(20, Riftjackal.TURN_GAP);
        assertEquals(2.0, Riftjackal.LAND_NEAR);
        assertEquals(2.5, Riftjackal.BITE_REACH);
        assertEquals(60.0, Riftjackal.SEEN_ANGLE);
        assertEquals(16.0, Riftjackal.BLINK_REACH);
        assertEquals(1.15, Riftjackal.ALPHA_SCALE);
        assertEquals(0.9, Riftjackal.RIFT_HALF_LENGTH);
        assertEquals(0.5, Riftjackal.RIFT_HALF_WIDTH);
        assertEquals(8, Riftjackal.LAND_TICKS);
        assertEquals(10, Riftjackal.FLUNG_TICKS);
        assertEquals(2, SafeTeleport.MAX_RISE);
        assertEquals(4, SafeTeleport.MAX_DROP);
        assertEquals(2, SafeTeleport.HEADROOM);
        assertEquals(16, SafeTeleport.TRIES);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("riftjackal");
        assertEquals(26.0F, row.health());
        assertEquals(8.5F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(0.9F, row.height());
        assertEquals(8, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(2, row.groupMin());
        assertEquals(3, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("starbitten", row.secondLook());
        assertEquals("rift_essence", row.material());
        assertEquals(row.tellTicks(), Riftjackal.TELL_TICKS);
        assertEquals(row.groupMax(), Riftjackal.PACK_MAX);
    }

    @Test
    void houseRules() {
        // a tell of at least 8 ticks (its bite is 1 x Atk, under the 1.5 x that asks for 10); a pack of three blinking
        // in turn keeps each jackal to its 60 ticks; the landing is always in bite reach at its nearest and at most 4
        // out; a rift outlasts no blink of its own jackal by more than a tell; a side landing is outside the seen cone
        assertTrue(Riftjackal.TELL_TICKS >= 8);
        assertTrue(Riftjackal.BITE < 1.5F);
        assertEquals(Riftjackal.BLINK_EVERY, Riftjackal.TURN_GAP * Riftjackal.PACK_MAX);
        assertTrue(Riftjackal.TURN_GAP > Riftjackal.TELL_TICKS, "one crouch is over before the next can start");
        assertTrue(Riftjackal.LAND_NEAR <= Riftjackal.BITE_REACH);
        assertEquals(Riftjackal.LAND_FAR, Blink.DISTANCES[Blink.DISTANCES.length - 1]);
        assertTrue(Riftjackal.RIFT_TICKS <= Riftjackal.BLINK_EVERY);
        for (int offset : Blink.OFFSETS) assertTrue(Math.abs(offset) >= 90, "only the side or back, got " + offset);
        assertTrue(Riftjackal.SEEN_ANGLE < 90.0, "a jackal at your side is out of sight until you turn");
        assertTrue(Riftjackal.ALPHA_HEALTH <= 60.0F, "inside the End's health band");
    }

    // ---- the blink's aim ---------------------------------------------------------------------------------------------

    @Test
    void facingFollowsMinecraftYaw() {
        // yaw 0 faces +Z, yaw 90 faces -X, yaw 180 faces -Z, yaw -90 faces +X
        assertEquals(Math.PI / 2, Blink.facingAngle(0.0F), 1.0E-9);
        assertEquals(Math.PI, Math.abs(Blink.facingAngle(90.0F)), 1.0E-9);
        assertEquals(-Math.PI / 2, Blink.facingAngle(180.0F), 1.0E-9);
        assertEquals(0.0, Blink.facingAngle(-90.0F), 1.0E-9);
    }

    @Test
    void seenOnlyWithinSixtyDegrees() {
        assertTrue(Blink.sees(0.0F, 0.0, 3.0), "straight ahead");
        assertTrue(Blink.sees(0.0F, Math.sin(Math.toRadians(59)) * 3, Math.cos(Math.toRadians(59)) * 3), "59 degrees off");
        assertFalse(Blink.sees(0.0F, Math.sin(Math.toRadians(61)) * 3, Math.cos(Math.toRadians(61)) * 3), "61 degrees off");
        assertFalse(Blink.sees(0.0F, 3.0, 0.0), "at its side");
        assertFalse(Blink.sees(0.0F, 0.0, -3.0), "behind it");
        assertTrue(Blink.sees(90.0F, -3.0, 0.0), "turned to face -X, it sees -X");
    }

    // ---- the safe-teleport rule ----------------------------------------------------------------------------------------

    /** A column read from the bottom: {@code cells[i]} is the cell at height {@code from + i}; above is air, below {@code below}. */
    private static IntUnaryOperator column(int from, int below, int... cells) {
        Map<Integer, Integer> at = new HashMap<>();
        for (int i = 0; i < cells.length; i++) at.put(from + i, cells[i]);
        return dy -> at.getOrDefault(dy, dy < from ? below : A);
    }

    @Test
    void flatGroundLandsLevel() {
        assertEquals(0, SafeTeleport.landing(column(-1, S, S, A, A), 2));
    }

    @Test
    void stepsDownAndUpWithinReach() {
        assertEquals(-1, SafeTeleport.landing(column(-2, S, S, A, A, A), 2), "a step down");
        assertEquals(1, SafeTeleport.landing(column(-1, S, S, S, A, A), 2), "a step up onto a block");
        assertEquals(-4, SafeTeleport.landing(column(-5, S, S, A, A, A, A, A), 2), "a drop of 4 is within reach");
        assertEquals(2, SafeTeleport.landing(column(-1, S, S, S, S, A, A), 2), "a ledge 2 up is within reach");
    }

    @Test
    void neverPastItsReach() {
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(column(-6, S, S, A, A, A, A, A, A, A), 2), "a drop of 5 is a cliff");
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(column(-1, S, S, S, S, S, A, A), 2), "a wall 3 high");
    }

    @Test
    void neverOverOrIntoTheVoid() {
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(dy -> dy < -3 ? SafeTeleport.VOID : A, 2), "open air down to the bottom of the world");
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(column(-8, SafeTeleport.VOID, SafeTeleport.VOID), 2), "no ground at all");
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(dy -> SafeTeleport.VOID, 2), "the void itself is no ground");
    }

    @Test
    void neverIntoLavaFireOrWater() {
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(column(-2, S, S, SafeTeleport.LAVA, A, A, A), 2), "a lava floor");
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(column(-2, S, S, SafeTeleport.LIQUID, A, A, A), 2), "water: no ground");
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(column(-1, S, SafeTeleport.HAZARD, A, A), 2), "a burning floor");
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(column(-1, S, S, SafeTeleport.HAZARD, A, A), 2), "fire where the feet go");
    }

    @Test
    void twoCellsOfAir() {
        assertFalse(SafeTeleport.standable(column(-1, S, S, A, S), 0, 2), "one cell of room is not enough");
        assertTrue(SafeTeleport.standable(column(-1, S, S, A, A, S), 0, 2), "two cells are");
        assertEquals(2, SafeTeleport.headroom(0.9), "even a small body asks for two");
        assertEquals(2, SafeTeleport.headroom(1.8), "a player's two");
        assertEquals(3, SafeTeleport.headroom(2.9), "a taller body asks for more");
        assertEquals(SafeTeleport.NONE, SafeTeleport.landing(dy -> S, 2), "solid all the way up");
    }

    @Test
    void prefersTheStartHeight() {
        assertEquals(-1, SafeTeleport.landing(column(-2, S, S, A, A, S, A, A), 2), "down before up at equal distance");
        assertArrayEquals(new int[] {0, -1, 1, -2, 2, -3, -4}, SafeTeleport.heights());
    }

    @Test
    void anglesCoverTheCircleFromTheFirst() {
        double[] a = SafeTeleport.angles(0.3, 16);
        assertEquals(16, a.length);
        assertEquals(0.3, a[0], 1.0E-12, "the first direction first");
        assertEquals(0.3 + Math.PI * 2 / 16, a[1], 1.0E-12);
        assertEquals(0.3 - Math.PI * 2 / 16, a[2], 1.0E-12);
        assertEquals(Math.PI, Math.abs(a[15] - 0.3), 1.0E-9, "the opposite way last");
        Set<Long> seen = new HashSet<>();
        for (double v : a) seen.add(Math.round(((v % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI) * 1000));
        assertEquals(16, seen.size(), "16 different directions");
    }
}
