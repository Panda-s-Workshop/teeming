package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Siphonjelly;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Siphonjelly's numbers against its row in RosterStats, written out as plain values: HP 16, Atk 2, Arm 0,
 * 0.8 x 1.0; a 10 t flare; the sting takes the longest beneficial effect, at most 600 t of it, and gives the rest back
 * when it dies; warped forest, weight 10, groups of 1 to 2; second look Crimson Jelly; drops siphon gel. The 32-block
 * reach of the give-back, the 100 t starve after an empty sting, the sting's reach and its wait are its design. The
 * GameTests check the numbers are applied; this checks they are the right numbers. Only compile-time constants are
 * read, so the class (and the game) never loads.
 */
class SiphonjellyNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10, Siphonjelly.TELL_TICKS);
        assertEquals(600, Siphonjelly.STEAL_CAP);
        // the calls made here: the give-back reaches 32 blocks, an empty sting starves it 100 t, 30 t between stings,
        // the flare from 0.5 blocks and the sting landing within 1.0
        assertEquals(32.0, Siphonjelly.RETURN_RANGE);
        assertEquals(100, Siphonjelly.STARVED_TICKS);
        assertEquals(30, Siphonjelly.STING_COOLDOWN);
        assertEquals(0.5, Siphonjelly.FLARE_REACH);
        assertEquals(1.0, Siphonjelly.STING_REACH);
        assertEquals(8, Siphonjelly.STING_TICKS);
        // the gold band its glow keeps out of: weak-point gold (#ffd36b) is at 42 degrees of hue
        assertEquals(30.0F, Siphonjelly.GOLD_FROM);
        assertEquals(58.0F, Siphonjelly.GOLD_TO);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("siphonjelly");
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(16.0F, row.health());
        assertEquals(2.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(1.0F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(10, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertEquals("", row.spawner());
        assertTrue(row.biomeSpawns());
        assertFalse(row.anyLight());
        assertEquals("crimson_jelly", row.secondLook());
        assertEquals("siphon_gel", row.material());
        assertEquals(row.tellTicks(), Siphonjelly.TELL_TICKS);
    }

    @Test
    void houseRules() {
        // a tell of at least 8 ticks (the sting is 1 x Atk); the flare starts with the target inside the sting's reach,
        // so stepping back is what dodges it; stings further apart than the 10-tick hurt cooldown
        assertTrue(Siphonjelly.TELL_TICKS >= 8);
        assertTrue(Siphonjelly.FLARE_REACH < Siphonjelly.STING_REACH);
        assertTrue(Siphonjelly.STING_COOLDOWN > 10);
        // a player walking (0.216 b/t) through the flare gets out of reach: 10 t carry them 2.16 blocks
        assertTrue(0.216 * Siphonjelly.TELL_TICKS > Siphonjelly.STING_REACH - Siphonjelly.FLARE_REACH);
        // a walking player outpaces its hunting drift; starved it is slower still
        assertTrue(Siphonjelly.HUNT_SPEED < 0.216);
        assertTrue(Siphonjelly.STARVED_SPEED < Siphonjelly.HUNT_SPEED);
        // the gold band holds weak-point gold's hue
        assertTrue(Siphonjelly.GOLD_FROM < 42.0F && 42.0F < Siphonjelly.GOLD_TO);
    }
}
