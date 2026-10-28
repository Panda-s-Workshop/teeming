package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Slagspitter;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Slagspitter's numbers, written out as plain values: HP 20, Atk 3 (glob 6), Arm 2, 0.9 x 0.8; a 16-tick ring;
 * globs at targets 6 to 20 blocks away; a burning patch of radius 1.5 for 80 t; it hops away from players within 5;
 * nether wastes, weight 15, groups of 1 to 2; second look Basalt Spitter; drops slag gland. The glob's flight, the hop's
 * leap, the 60-tick wait between tells and the 3 s of fire are its design. The GameTests check the numbers are
 * applied; this checks they are the right numbers.
 */
class SlagspitterNumbersTest {
    @Test
    void mechanic() {
        assertEquals(16, Slagspitter.TELL_TICKS);
        assertEquals(6.0, Slagspitter.MIN_RANGE);
        assertEquals(20.0, Slagspitter.MAX_RANGE);
        assertEquals(6.0F, Slagspitter.GLOB_DAMAGE);
        assertEquals(1.5, Slagspitter.PATCH_RADIUS);
        assertEquals(80, Slagspitter.PATCH_TICKS);
        assertEquals(5.0, Slagspitter.HOP_RANGE);
        // the calls made here: a spit every 60 t, the glob's 0.6 b/t across and 0.05 gravity, 3 s of fire in a patch,
        // a hop of 0.5 b/t out and 0.45 up with 10 t on the ground between, a nip every 20 t
        assertEquals(60, Slagspitter.LOB_EVERY);
        assertEquals(8, Slagspitter.LOB_TICKS);
        assertEquals(0.6, Slagspitter.GLOB_SPEED);
        assertEquals(0.05, Slagspitter.GLOB_GRAVITY);
        assertEquals(0.3, Slagspitter.GLOB_RADIUS);
        assertEquals(60, Slagspitter.PATCH_FIRE_TICKS);
        assertEquals(0.5, Slagspitter.HOP_SPEED);
        assertEquals(0.45, Slagspitter.HOP_LIFT);
        assertEquals(10, Slagspitter.HOP_COOLDOWN);
        assertEquals(20, Slagspitter.NIP_COOLDOWN);
        assertEquals(14.0, Slagspitter.KEEP_RANGE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("slagspitter");
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(20.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.9F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(16, row.tellTicks());
        assertEquals(6.0F, row.blow());
        assertEquals(15, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertEquals("basalt_spitter", row.secondLook());
        assertEquals("slag_gland", row.material());
        assertEquals(row.tellTicks(), Slagspitter.TELL_TICKS);
        assertEquals(row.blow(), Slagspitter.GLOB_DAMAGE);
    }

    @Test
    void houseRules() {
        // the glob deals 5 / 3 = 1.67 x Atk, so its tell must be at least 10 ticks
        assertTrue(Slagspitter.GLOB_DAMAGE >= 1.5F * 3.0F && Slagspitter.TELL_TICKS >= 10);
        // the hop starts inside the spitting range's floor, so a crowding player is never spat at point blank
        assertTrue(Slagspitter.HOP_RANGE < Slagspitter.MIN_RANGE);
        // the last patch still burns when the next glob lands, but never two before it: at most two at once
        assertTrue(Slagspitter.LOB_EVERY < Slagspitter.PATCH_TICKS && 2 * Slagspitter.LOB_EVERY > Slagspitter.PATCH_TICKS);
        // the longest lob (20 blocks) is in the air for under two seconds
        double longest = 20.0 / Slagspitter.GLOB_SPEED;   // constants only: the class itself never loads here
        assertTrue(longest < 40.0, "a 20-block lob flies " + longest + " ticks");
        // a 20-block lob from level ground peaks under 8 blocks up
        double peak = Slagspitter.GLOB_GRAVITY * longest * longest / 8.0;
        assertTrue(peak < 8.0, "a 20-block lob peaks " + peak + " blocks up");
        // it keeps its range inside its spitting band; nips stay further apart than the 10-tick hurt cooldown
        assertTrue(Slagspitter.KEEP_RANGE > Slagspitter.MIN_RANGE && Slagspitter.KEEP_RANGE < Slagspitter.MAX_RANGE);
        assertTrue(Slagspitter.NIP_COOLDOWN > 10);
        // the tell is spent before the next can start
        assertTrue(Slagspitter.LOB_EVERY > Slagspitter.TELL_TICKS + Slagspitter.LOB_TICKS);
    }
}
