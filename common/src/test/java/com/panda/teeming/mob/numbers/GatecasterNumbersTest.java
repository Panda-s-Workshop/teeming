package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Gatecaster;
import com.panda.teeming.mob.end.Gatecaster.Gate;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Gatecaster's numbers against its roster row, written out as plain values: HP 30, Atk 6, Arm 2, 0.7 x 2.0; tell
 * 20 t (the exit rift); its bolt comes out of a gate 3-5 blocks behind its target, every 80 t; End highlands, weight
 * 4, groups of 1; second look Mirrored Weaver; drops rift essence. The 16-block reach in sight, the 6 of the bolt (its
 * attack), the 12-tick follow before the 8-tick lock, the bolt's speed and length, and the turned bolt's burst are its
 * design. Then the gate's layout and its packing into the synced flags. The GameTests check the numbers are applied in
 * the world; this checks they are the right numbers.
 */
class GatecasterNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, Gatecaster.TELL_TICKS);
        assertEquals(80, Gatecaster.CAST_EVERY);
        assertEquals(3.0, Gatecaster.GATE_NEAR);
        assertEquals(5.0, Gatecaster.GATE_FAR);
        assertEquals(16.0, Gatecaster.CAST_RANGE);
        assertEquals(6.0F, Gatecaster.BOLT_DAMAGE);
        // the calls made here: 12 ticks following then 8 locked, a bolt at 0.6 a tick out to 12 blocks (20 thrown
        // straight), caught within 0.3 of a hitbox, a ring 0.7 across at 0.65 of the target's height
        assertEquals(12, Gatecaster.TRACK_TICKS);
        assertEquals(0.6, Gatecaster.BOLT_SPEED);
        assertEquals(12.0, Gatecaster.BOLT_RANGE);
        assertEquals(20.0, Gatecaster.STRAIGHT_RANGE);
        assertEquals(0.3, Gatecaster.BOLT_RADIUS);
        assertEquals(0.7, Gatecaster.RING_RADIUS);
        assertEquals(0.65, Gatecaster.CHEST);
        assertEquals(10, Gatecaster.BOLT_TICKS);
        assertEquals(20, Gatecaster.TURNED_TICKS);
    }

    @Test
    void tellsAreLongEnough() {
        // the bolt deals its attack, 1x: the tell needs at least 8 ticks; the lock leaves at least 8 to step aside
        assertTrue(Gatecaster.TELL_TICKS >= 8, "tell " + Gatecaster.TELL_TICKS);
        assertTrue(Gatecaster.TELL_TICKS - Gatecaster.TRACK_TICKS >= 8, "locked for " + (Gatecaster.TELL_TICKS - Gatecaster.TRACK_TICKS));
    }

    @Test
    void theCycleFitsInsideItsInterval() {
        // the tell, the release and the slowest bolt (the full 20 blocks thrown straight) are over before the next tell
        double flight = Gatecaster.STRAIGHT_RANGE / Gatecaster.BOLT_SPEED;
        assertTrue(Gatecaster.TELL_TICKS + flight < Gatecaster.CAST_EVERY, "tell and flight " + (Gatecaster.TELL_TICKS + flight));
        assertTrue(Gatecaster.KEEP_AWAY < Gatecaster.CLOSE_IN && Gatecaster.CLOSE_IN < Gatecaster.CAST_RANGE,
                "it stands between " + Gatecaster.KEEP_AWAY + " and " + Gatecaster.CLOSE_IN + " blocks, inside its reach");
        // a gate at its farthest still sends the bolt past a target standing 5 blocks off
        assertTrue(Gatecaster.BOLT_RANGE > Gatecaster.GATE_FAR + 1.0);
    }

    @Test
    void theGateOpensBehindThenAtTheSides() {
        // a target facing south (yaw 0: it looks toward +z) with the caster due north of it (toward the target is +z):
        // behind is -z at 4, 5 then 3; the sides tie (both square to the caster), so its left (+x) comes first
        double[] xz = Gate.offsets(0.0F, 0.0, 6.0);
        assertArrayEquals(new double[] {0, -4, 0, -5, 0, -3, 4, 0, 5, 0, 3, 0, -4, 0, -5, 0, -3, 0}, xz, 1.0E-9);
        // every distance lies in 3..5
        assertArrayEquals(new double[] {4.0, 5.0, 3.0}, Gate.DISTANCES);
        for (double d : Gate.DISTANCES) assertTrue(d >= Gatecaster.GATE_NEAR && d <= Gatecaster.GATE_FAR, "distance " + d);
        // a target facing east (yaw -90: toward +x), the caster to its north-west: behind is -x; its left is north
        // (-z), and the caster lies north of it, so the right (south, +z, farther from the caster) comes first
        double[] east = Gate.offsets(-90.0F, 4.0, 3.0);
        assertArrayEquals(new double[] {-4, 0}, new double[] {east[0], east[1]}, 1.0E-9);
        assertArrayEquals(new double[] {0, 4}, new double[] {east[6], east[7]}, 1.0E-9);
        assertArrayEquals(new double[] {0, -4}, new double[] {east[12], east[13]}, 1.0E-9);
    }

    @Test
    void theFlagsCarryTheGate() {
        int f = Gate.encode(9.3, -4.1, 1.17, 33.0);
        assertTrue(f != Gate.NONE && Gate.live(f));
        assertEquals(9.25, Gate.forward(f));
        assertEquals(-4.0, Gate.left(f));
        assertEquals(1.0, Gate.up(f));
        assertEquals(22.5, Gate.yaw(f));
        int back = Gate.encode(-12.0, 7.75, -7.5, -90.0);
        assertEquals(-12.0, Gate.forward(back));
        assertEquals(7.75, Gate.left(back));
        assertEquals(-7.5, Gate.up(back));
        assertEquals(270.0, Gate.yaw(back));
        // the farthest gate (16 blocks to the target and 5 past it) fits; 8 blocks to the side or up does not
        assertTrue(Gate.encode(Gatecaster.CAST_RANGE + Gatecaster.GATE_FAR, 0.0, 1.0, 0.0) != Gate.NONE);
        assertEquals(Gate.NONE, Gate.encode(4.0, 8.0, 1.0, 0.0));
        assertEquals(Gate.NONE, Gate.encode(4.0, 0.0, 8.0, 0.0));
        assertFalse(Gate.live(0));
        assertTrue(Gate.BITS <= 24, "the gate leaves the visual stage bits alone");
    }

    @Test
    void theLightIsVioletNotGold() {
        // weak-point gold is #ffd36b; the rift light has more blue than red
        int c = Gatecaster.RIFT_COLOR;
        assertTrue((c & 0xFF) > (c >> 16 & 0xFF) - 0x10 && (c & 0xFF) > (c >> 8 & 0xFF), String.format("rift %06x is violet", c));
        assertFalse(c == 0xFFD36B);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("gatecaster");
        assertEquals(30.0F, row.health());
        assertEquals(6.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(2.0F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("mirrored_weaver", row.secondLook());
        assertEquals("rift_essence", row.material());
        // the bolt is its attack
        assertEquals(row.attack(), Gatecaster.BOLT_DAMAGE);
    }
}
