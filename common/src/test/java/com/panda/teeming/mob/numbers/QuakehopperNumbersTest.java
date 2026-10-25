package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Quakehopper;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Quakehopper's numbers, written out as plain values: HP 26, Atk 5, Arm 2, 1.0 x 1.0; crouch 14 t; a landing ring
 * that grows 0.4 b/t to radius 7, striking those on the ground for 6 with knockback; basalt deltas, weight 12, groups
 * of 1 to 2; second look Magma Hopper; drops slag gland. The rest is its design: the knockback (0.5 out, 0.3 up), the
 * hop (at targets 3 to 16 blocks off, landing 3 short, at most 10 across, 0.8 up against 0.08), the 60-tick rest and
 * the spent springs (100 t at half speed). The GameTests check the numbers are applied; this checks they are the right
 * numbers.
 */
class QuakehopperNumbersTest {
    @Test
    void mechanic() {
        assertEquals(14, Quakehopper.TELL_TICKS);
        assertEquals(0.4, Quakehopper.RING_SPEED);
        assertEquals(7.0, Quakehopper.RING_RADIUS);
        assertEquals(6.0F, Quakehopper.RING_DAMAGE);
        assertEquals(18, Quakehopper.RING_TICKS);
        assertEquals(0.5, Quakehopper.RING_KNOCKBACK);
        assertEquals(0.3, Quakehopper.RING_LIFT);
        assertEquals(1.5, Quakehopper.RING_REACH_Y);
        assertEquals(3.0, Quakehopper.HOP_MIN);
        assertEquals(16.0, Quakehopper.HOP_RANGE);
        assertEquals(3.0, Quakehopper.HOP_SHORT);
        assertEquals(10.0, Quakehopper.HOP_MAX);
        assertEquals(0.8, Quakehopper.HOP_UP);
        assertEquals(0.08, Quakehopper.GRAVITY);
        assertEquals(60, Quakehopper.COOLDOWN);
        assertEquals(100, Quakehopper.SPENT_TICKS);
        assertEquals(0.5, Quakehopper.SPENT_SPEED);
    }

    @Test
    void ringFitsItsRules() {
        assertEquals(Quakehopper.RING_TICKS, (int) Math.ceil(Quakehopper.RING_RADIUS / Quakehopper.RING_SPEED),
                "it stays landed exactly as long as the ring takes to reach its radius");
        assertTrue(Quakehopper.RING_DAMAGE < 1.5F * 5.0F || Quakehopper.TELL_TICKS >= 10,
                "a blow of 1.5x attack or more needs a tell of at least 10 ticks");
        assertTrue(Quakehopper.TELL_TICKS >= 8, "every tell lasts at least 8 ticks");
        assertTrue(Quakehopper.HOP_SHORT / Quakehopper.RING_SPEED >= 7.0,
                "landing 3 short gives its target at least 7 ticks to read the ring before the front arrives");
        double peak = Quakehopper.HOP_UP * Quakehopper.HOP_UP / (2.0 * Quakehopper.GRAVITY) + Quakehopper.HOP_UP / 2.0;
        assertTrue(peak > 4.0 && peak < 5.0, "a high hop: about 4.4 blocks up in whole-tick steps, " + peak);
        assertTrue(Quakehopper.FLIGHT_TICKS > 2.0 * Quakehopper.HOP_UP / Quakehopper.GRAVITY, "a hop on the flat ends before its time runs out");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("quakehopper");
        assertEquals(26.0F, row.health());
        assertEquals(5.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(1.0F, row.width());
        assertEquals(1.0F, row.height());
        assertEquals(14, row.tellTicks());
        assertEquals(6.0F, row.blow());
        assertEquals(12, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("magma_hopper", row.secondLook());
        assertEquals("slag_gland", row.material());
    }
}
