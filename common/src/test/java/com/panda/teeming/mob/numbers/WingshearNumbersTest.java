package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Wingshear;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Wingshear's numbers against its roster row, written out as plain values: HP 30, Atk 6, Arm 2, 1.4 x 0.6; screech
 * 16 t; hunts gliders within 48; a hit ends the glide and no glide again for 40 t; a firework's speed outruns it; the
 * open sky over the End by the spawner's sky rule, weight 0, groups of 1 to 2; second look Dusk Shear; drops void
 * membrane. Its own numbers (chase 1.25 b/t, dive 1.5 b/t, lost after 8 blocks, circles off 100 t) are its design.
 *
 * <p>"Faster than a glide, slower than a rocket" is checked against vanilla's own elytra flight, run here as plain
 * arithmetic: the glide step of {@code LivingEntity.updateFallFlyingMovement} (gravity 0.08, lift by the look's pitch,
 * drag 0.99 across and 0.98 up and down) and a rocket's push from {@code FireworkRocketEntity.tick} (each tick the
 * motion moves half way to 1.5 along the look, plus 0.1).
 */
class WingshearNumbersTest {
    @Test
    void mechanic() {
        assertEquals(16, Wingshear.TELL_TICKS);
        assertEquals(48.0, Wingshear.HUNT_RANGE);
        assertEquals(40, Wingshear.NO_GLIDE_TICKS);
        assertEquals(1.0F, Wingshear.DIVE_HIT);
        assertEquals(1.25, Wingshear.CHASE_SPEED);
        assertEquals(1.5, Wingshear.DIVE_SPEED);
        assertEquals(5.0, Wingshear.SCREECH_REACH);
        assertEquals(12, Wingshear.DIVE_TICKS);
        assertEquals(8.0, Wingshear.LOSE_GAP);
        assertEquals(100, Wingshear.LOST_TICKS);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("wingshear");
        assertEquals(30.0F, row.health());
        assertEquals(6.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(1.4F, row.width());
        assertEquals(0.6F, row.height());
        assertEquals(16, row.tellTicks());
        assertEquals(0, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertEquals("sky", row.spawner());
        assertEquals("dusk_shear", row.secondLook());
        assertEquals("void_membrane", row.material());
    }

    @Test
    void houseRules() {
        assertTrue(Wingshear.TELL_TICKS >= 8, "a tell of at least 8 ticks");
        assertTrue(Wingshear.DIVE_HIT < 1.5F, "the dive is an ordinary blow, under 1.5x its attack");
        assertTrue(Wingshear.DIVE_TICKS * Wingshear.DIVE_SPEED >= 3 * Wingshear.SCREECH_REACH,
                "a dive reaches three times its screech reach, so a glider can't drift out of it by gliding");
    }

    // ---- vanilla's elytra flight, along one line ------------------------------------------------------------------

    /** Speed across and up/down after one tick of gliding at {@code pitchDeg} (down positive), with a rocket or not. */
    private static double[] glide(double across, double up, double pitchDeg, boolean rocket) {
        double p = Math.toRadians(pitchDeg);
        double lookX = Math.cos(p), lookY = -Math.sin(p);
        if (rocket) {
            across += lookX * 0.1 + (lookX * 1.5 - across) * 0.5;
            up += lookY * 0.1 + (lookY * 1.5 - up) * 0.5;
        }
        double speedAcross = Math.abs(across);
        double cos2 = Math.cos(p) * Math.cos(p);
        up += 0.08 * (-1.0 + cos2 * 0.75);
        if (up < 0) {
            double lift = up * -0.1 * cos2;
            across += lift;
            up += lift;
        }
        if (p < 0) {
            double climb = speedAcross * -Math.sin(p) * 0.04;
            across -= climb;
            up += climb * 3.2;
        }
        across += (speedAcross - across) * 0.1;
        return new double[]{across * 0.99, up * 0.98};
    }

    @Test
    void aRocketsBoostIsFasterThanItsChaseAndDive() {
        double[] v = {0.0, 0.0};
        for (int t = 0; t < 400; t++) v = glide(v[0], v[1], 0.0, true);
        double rocket = Math.hypot(v[0], v[1]);
        assertEquals(1.67, rocket, 0.01, "a rocket's boost settles at 1.67 b/t");
        assertTrue(Wingshear.DIVE_SPEED < rocket && Wingshear.CHASE_SPEED < Wingshear.DIVE_SPEED,
                "chase 1.25 < dive 1.5 < a rocket's 1.67");
    }

    @Test
    void itsChaseIsFasterThanAPlainGlide() {
        // a glide from an island's edge, looking level: still slower than the chase 8 seconds in
        double[] v = {0.0, 0.0};
        for (int t = 0; t < 160; t++) v = glide(v[0], v[1], 0.0, false);
        assertTrue(v[0] < 1.25, "a fresh level glide runs " + v[0] + " after 8 s");
        // a glide that holds its height (nose up while it is low, down while it is high): far slower once it settles
        double[] h = {1.5, 0.0};
        double y = 0;
        for (int t = 0; t < 200; t++) {
            h = glide(h[0], h[1], y < 0 ? -12.0 : 8.0, false);
            y += h[1];
        }
        assertTrue(h[0] < 0.8 && y > -10, "a height-holding glide settles under 0.8 b/t, runs " + h[0] + ", sank " + -y);
    }

    /** The gap between a glider 5 blocks ahead at a plain 1 b/t and the Wingshear behind, after {@code ticks}. */
    private static double gapAfter(int ticks, int rocketTicks) {
        double[] v = {1.0, 0.0};
        double glider = 0.0, shear = -5.0;
        for (int t = 0; t < ticks; t++) {
            v = glide(v[0], v[1], 0.0, t < rocketTicks);
            glider += v[0];
            shear += Wingshear.CHASE_SPEED;
        }
        return glider - shear;
    }

    @Test
    void oneRocketOutrunsIt() {
        // the shortest flight-1 rocket pushes for 20 ticks: 40 ticks after firing, the gap has opened by more than the 8
        // it takes to lose the glider; with no rocket, the same glider is caught
        double opened = gapAfter(40, 20) - 5.0;
        assertTrue(opened >= 8.0, "one rocket opens the gap by " + opened);
        assertEquals(14.8, opened, 0.2);
        assertFalse(gapAfter(40, 0) > 0, "a plain glide is caught inside 40 ticks, gap " + gapAfter(40, 0));
    }
}
