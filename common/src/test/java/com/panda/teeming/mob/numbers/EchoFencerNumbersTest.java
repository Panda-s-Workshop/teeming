package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.EchoFencer;
import com.panda.teeming.mob.end.EchoFencer.Afterimage;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Echo Fencer's numbers against its row, written out as plain values: HP 32, Atk 8.5, Arm 2, 0.7 x 2.0, tell 10 (the
 * swing); its afterimage repeats each attack 20 ticks later at 0.8x; End midlands, weight 4, alone; second look Gilded
 * Fencer; drops time sand. Then the calls made here (the lunge's line, the step aside, the afterimage's own tell, the
 * rest between lunges) and the afterimage's place in the synced flags, which is pure maths and needs no game. The
 * GameTests check the numbers are applied in the world; this checks they are the right numbers.
 */
class EchoFencerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10, EchoFencer.TELL_TICKS);
        assertEquals(20, EchoFencer.ECHO_DELAY);
        assertEquals(0.8F, EchoFencer.ECHO);
        assertEquals(1.0F, EchoFencer.THRUST);
        // the calls made here: a 4-tick lunge, the afterimage's 10-tick tell and 10-tick echo, 10 ticks' rest, a 3-block
        // line 0.4 either side from the hand on, a step aside of about a block, its own spot 0.8 clear
        assertEquals(4, EchoFencer.THRUST_TICKS);
        assertEquals(16, EchoFencer.RECOVER_TICKS);
        assertEquals(10, EchoFencer.ECHO_TELL_TICKS);
        assertEquals(10, EchoFencer.ECHO_TICKS);
        assertEquals(10, EchoFencer.COOLDOWN);
        assertEquals(3.0, EchoFencer.REACH);
        assertEquals(0.4, EchoFencer.LINE_RADIUS);
        assertEquals(0.5, EchoFencer.LINE_START);
        assertEquals(0.1, EchoFencer.LINE_STEP);
        assertEquals(0.2, EchoFencer.LINE_LOW);
        assertEquals(1.8, EchoFencer.LINE_HIGH);
        assertEquals(1.2, EchoFencer.BLADE_HEIGHT);
        assertEquals(0.8, EchoFencer.SELF_CLEAR);
        assertEquals(1.8, EchoFencer.START_GAP);
        assertEquals(3.0, EchoFencer.CHASE_GAP);
        assertEquals(4, EchoFencer.SIDESTEP_TICKS);
        assertEquals(0.25, EchoFencer.SIDESTEP_SPEED);
        assertEquals(12.0F, EchoFencer.TURN);
        assertEquals(24, EchoFencer.CRACKED_BIT);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("echo_fencer");
        assertEquals(32.0F, row.health());
        assertEquals(8.5F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(2.0F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("gilded_fencer", row.secondLook());
        assertEquals("time_sand", row.material());
        assertEquals(row.tellTicks(), EchoFencer.TELL_TICKS);
    }

    @Test
    void houseRules() {
        // every tell at least 8 ticks, and at least 10 before the thrust (its damage is under 1.5 x Atk either way); the
        // afterimage's own tell fits inside the wait before its echo; one afterimage stands at a time (the next thrust
        // comes after the echo and its crumbling are over)
        assertTrue(EchoFencer.TELL_TICKS >= 10);
        assertTrue(EchoFencer.ECHO_TELL_TICKS >= 8);
        assertTrue(EchoFencer.THRUST < 1.5F && EchoFencer.ECHO < 1.5F);
        assertTrue(EchoFencer.ECHO_TELL_TICKS <= EchoFencer.RECOVER_TICKS);
        assertEquals(EchoFencer.ECHO_DELAY, EchoFencer.THRUST_TICKS + EchoFencer.RECOVER_TICKS);
        // the step aside clears the line: about a block, more than its half width plus the line's
        assertTrue(EchoFencer.SIDESTEP_TICKS * EchoFencer.SIDESTEP_SPEED > 0.35 + EchoFencer.LINE_RADIUS);
        assertTrue(EchoFencer.SELF_CLEAR > 0.35 + EchoFencer.LINE_RADIUS - EchoFencer.LINE_START);
        assertTrue(EchoFencer.START_GAP < EchoFencer.REACH && EchoFencer.START_GAP < EchoFencer.CHASE_GAP);
        // the glints stay off the weak-point gold: blue well above green's share, as a lilac white
        int glint = EchoFencer.GLINT_COLOR;
        assertTrue((glint & 0xFF) >= (glint >> 8 & 0xFF));
    }

    @Test
    void afterimageFlags() {
        int f = Afterimage.encode(1203.5, 64.0, -88.125, 90.0);
        assertTrue(Afterimage.live(f));
        assertEquals(0, f & ~Afterimage.MASK, "it keeps to its 24 bits");
        assertEquals(1203.5, Afterimage.x(f, 1203.5), 1.0E-9);
        assertEquals(1203.5, Afterimage.x(f, 1210.0), 1.0E-9);      // found again from 6.5 blocks off
        assertEquals(1203.5, Afterimage.x(f, 1197.0), 1.0E-9);
        assertEquals(-88.125, Afterimage.z(f, -84.0), 1.0E-9);
        assertEquals(64.0, Afterimage.y(f, 65.5), 1.0E-9);
        assertEquals(64.0, Afterimage.y(f, 62.6), 1.0E-9);
        assertEquals(90.0, Afterimage.yaw(f), 1.0E-9);
        assertEquals(270.0, Afterimage.yaw(Afterimage.encode(0, 0, 0, -90.0)), 1.0E-9);
        assertFalse(Afterimage.live(0));
        // snapping: an eighth of a block across, half a block up, 64ths of a turn
        assertEquals(10.125, Afterimage.snapXZ(10.1), 1.0E-9);
        assertEquals(64.5, Afterimage.snapY(64.4), 1.0E-9);
        assertEquals(5.625F, Afterimage.snapYaw(4.0F), 1.0E-6F);
        assertEquals(16.0, Afterimage.XZ_WRAP);
        assertEquals(4.0, Afterimage.Y_WRAP);
    }

    @Test
    void line() {
        // a fencer at the origin facing south (+Z): its line runs from 0.5 to 3 blocks along z, 0.4 either side
        assertTrue(Afterimage.inLine(0, 0, 0, 0.0, 3.0, -0.3, 0.0, 2.0, 0.3, 1.8, 2.6), "a body 2 ahead");
        assertTrue(Afterimage.inLine(0, 0, 0, 0.0, 3.0, -0.3, 0.0, 3.3, 0.3, 1.8, 3.9), "an edge 3.3 ahead");
        assertFalse(Afterimage.inLine(0, 0, 0, 0.0, 3.0, -0.3, 0.0, 3.5, 0.3, 1.8, 4.1), "an edge 3.5 ahead");
        assertFalse(Afterimage.inLine(0, 0, 0, 0.0, 3.0, -0.3, 0.0, -2.6, 0.3, 1.8, -2.0), "a body behind");
        assertTrue(Afterimage.inLine(0, 0, 0, 0.0, 3.0, 0.35, 0.0, 1.0, 1.25, 0.9, 1.9), "an edge 0.35 aside");
        assertFalse(Afterimage.inLine(0, 0, 0, 0.0, 3.0, 0.45, 0.0, 1.0, 1.35, 0.9, 1.9), "an edge 0.45 aside");
        assertFalse(Afterimage.inLine(0, 0, 0, 0.0, 3.0, -0.3, 2.0, 1.0, 0.3, 3.8, 1.6), "a body above the blade's band");
        // facing east (yaw -90): the line runs along +x
        assertTrue(Afterimage.inLine(0, 0, 0, -90.0, 3.0, 2.0, 0.0, -0.3, 2.6, 1.8, 0.3), "east");
        assertFalse(Afterimage.inLine(0, 0, 0, -90.0, 3.0, -0.3, 0.0, 2.0, 0.3, 1.8, 2.6), "not south");
    }
}
