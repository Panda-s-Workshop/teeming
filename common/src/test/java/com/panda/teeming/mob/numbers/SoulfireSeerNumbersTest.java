package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.SoulfireSeer;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Soulfire Seer's numbers against its roster row, written out as plain values: HP 22, Atk 9 (the beam), Arm 0,
 * 0.6 x 2.0; aim line 24 t; beam range 32 with line of sight, every 100 t, soul fire 3 s; soul sand valley, weight 8,
 * groups of 1; second look Bone Seer; drops soul vertebra. The split of the tell (16 following, 8 locked), the 4 s of
 * blindness after a dodged beam and the beam's catch radius are its design. The GameTests check the numbers are
 * applied; this checks they are the right numbers.
 */
class SoulfireSeerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(24, SoulfireSeer.TELL_TICKS);
        assertEquals(32.0, SoulfireSeer.RANGE);
        assertEquals(100, SoulfireSeer.AIM_EVERY);
        assertEquals(9.0F, SoulfireSeer.BEAM_DAMAGE);
        assertEquals(60, SoulfireSeer.SOUL_FIRE_TICKS);
    }

    @Test
    void locksForTheLastEightTicks() {
        // a sidestep needs a moment after the line stops following: the last 8 ticks of the tell
        assertEquals(16, SoulfireSeer.TRACK_TICKS);
        assertEquals(8, SoulfireSeer.TELL_TICKS - SoulfireSeer.TRACK_TICKS);
    }

    @Test
    void tellIsLongEnoughForAHeavyHit() {
        // 9 is the full attack: anything dealing 1.5x attack or more needs at least 10 ticks of tell, quick tells 8
        assertTrue(SoulfireSeer.TELL_TICKS >= 10, "tell " + SoulfireSeer.TELL_TICKS);
    }

    @Test
    void theCycleFitsInsideItsInterval() {
        // aim and beam are over well before the next tell may start; a dodge's blindness runs past it
        assertTrue(SoulfireSeer.TELL_TICKS + SoulfireSeer.BEAM_TICKS < SoulfireSeer.AIM_EVERY);
        assertEquals(80, SoulfireSeer.SNUFF_TICKS);
        assertTrue(SoulfireSeer.KEEP_AWAY < SoulfireSeer.CLOSE_IN && SoulfireSeer.CLOSE_IN < SoulfireSeer.RANGE,
                "it stands between " + SoulfireSeer.KEEP_AWAY + " and " + SoulfireSeer.CLOSE_IN + " blocks, inside its range");
    }

    @Test
    void lineLengthFitsItsFlags() {
        // the line's length is synced in quarter blocks in 8 bits: 63.75 blocks, room for the 32-block beam at any scale
        assertTrue(SoulfireSeer.RANGE * 4.0 / 0.8 <= 255.0, "32 blocks at scale 0.8 is " + SoulfireSeer.RANGE * 4.0 / 0.8 + " quarters");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("soulfire_seer");
        assertEquals(22.0F, row.health());
        assertEquals(9.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(2.0F, row.height());
        assertEquals(24, row.tellTicks());
        assertEquals(8, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("bone_seer", row.secondLook());
        assertEquals("soul_vertebra", row.material());
    }
}
