package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.SootManta;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Soot Manta's numbers against its roster row, written out as plain values: HP 26, Atk 4, Arm 0, 2.0 x 0.6; a
 * 20-tick ripple; soot clouds of radius 3 for 100 t, Blindness and Darkness 60 t to anyone inside; flees below 50 %;
 * the air over lava seas (the spawner's lava-sea-air rule, no biome list), weight 6, one at a time; second look Ember
 * Manta; drops soot plume. The cloud's 140-tick wait, the 2.5-block reach that starts a ripple and the flight numbers
 * are its design. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class SootMantaNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, SootManta.TELL_TICKS);
        assertEquals(3.0, SootManta.CLOUD_RADIUS);
        assertEquals(100, SootManta.CLOUD_TICKS);
        assertEquals(60, SootManta.BLIND_TICKS);
        assertEquals(0.5F, SootManta.FLEE_BELOW);
        // the calls made here: a ripple from 2.5 blocks, 140 t between clouds, 5 s runs while the threat is within 16
        assertEquals(2.5, SootManta.SHED_RANGE);
        assertEquals(140, SootManta.COOLDOWN);
        assertEquals(50, SootManta.REFRESH_BELOW);
        assertEquals(100, SootManta.FLEE_TICKS);
        assertEquals(16.0, SootManta.FLEE_RANGE);
        assertEquals(30, SootManta.BITE_COOLDOWN);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("soot_manta");
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(26.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(2.0F, row.width());
        assertEquals(0.6F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(6, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("lava_sea_air", row.spawner());
        assertEquals(RosterStats.Place.NONE, row.place());
        assertFalse(row.anyLight());
        assertEquals("ember_manta", row.secondLook());
        assertEquals("soot_plume", row.material());
        assertEquals(row.tellTicks(), SootManta.TELL_TICKS);
    }

    @Test
    void houseRules() {
        // a tell of at least 8 ticks (the bite is 1 x Atk); the cloud starts with the target well inside it; it clears
        // before the next one, and a top-up comes before the effects could lapse; no effect over 200 t
        assertTrue(SootManta.TELL_TICKS >= 8);
        assertTrue(SootManta.SHED_RANGE < SootManta.CLOUD_RADIUS);
        assertTrue(SootManta.COOLDOWN > SootManta.CLOUD_TICKS);
        assertTrue(SootManta.REFRESH_BELOW > 0 && SootManta.REFRESH_BELOW < SootManta.BLIND_TICKS);
        assertTrue(SootManta.BLIND_TICKS <= 200 && SootManta.CLOUD_TICKS <= 200);
        // it can outrun a sprinting player (0.28 b/t), so chasing it off works; bites stay further apart than the
        // 10-tick hurt cooldown
        assertTrue(SootManta.FLEE_SPEED > 0.28);
        assertTrue(SootManta.FLEE_SHORT < SootManta.FLEE_DISTANCE);
        assertTrue(SootManta.BITE_COOLDOWN > 10);
    }
}
