package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Veinleech;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Veinleech's numbers against its roster row, written out as plain values: HP 22, Atk 2, Arm 0, 0.6 x 0.5; latch
 * 10 t; tether range 10: 1 damage a second to the target, heals it 1 a second; line of sight lost (or the target past
 * the range) snaps it and it is stunned 40 t; crimson forest, weight 15, groups of 1 to 2; second look Pale Leech;
 * drops vein sac. The wait after a stun and the vein's shooting speed are its design. The GameTests check the numbers
 * are applied; this checks they are the right numbers.
 */
class VeinleechNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10, Veinleech.TELL_TICKS);
        assertEquals(10.0, Veinleech.RANGE);
        assertEquals(1.0F, Veinleech.DRAIN);
        assertEquals(1.0F, Veinleech.HEAL);
        assertEquals(20, Veinleech.DRAIN_INTERVAL);
        assertEquals(40, Veinleech.STUN_TICKS);
    }

    @Test
    void drainsAndHealsOneASecond() {
        float perSecond = Veinleech.DRAIN * 20.0F / Veinleech.DRAIN_INTERVAL;
        assertEquals(1.0F, perSecond);
        assertEquals(1.0F, Veinleech.HEAL * 20.0F / Veinleech.DRAIN_INTERVAL);
    }

    @Test
    void theVeinReachesItsRangeAtOnce() {
        // it shoots out to the full 10 blocks within 3 ticks: no gap a player could read as a second tell
        assertTrue(Veinleech.RANGE / Veinleech.SHOOT_SPEED <= 3.0, "10 blocks in " + Veinleech.RANGE / Veinleech.SHOOT_SPEED + " ticks");
    }

    @Test
    void waitsAfterTheStun() {
        assertTrue(Veinleech.COOLDOWN > 0, "it doesn't rear the moment it stands up");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("veinleech");
        assertEquals(22.0F, row.health());
        assertEquals(2.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(0.5F, row.height());
        assertEquals(10, row.tellTicks());
        assertEquals(15, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("pale_leech", row.secondLook());
        assertEquals("vein_sac", row.material());
    }
}
