package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.CindermothSwarm;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Cindermoth Swarm's numbers, written out as plain values: HP 10, a burn of 1 a second (attack 1), Arm 0, 1.0 x 1.0,
 * no tell; it seeks the brightest light within 12 blocks, and a light on the ground holds it 200 ticks; soul sand valley
 * and nether wastes, weight 12, alone; second look Soul Moths; drops ember wing. The rest (40 ticks scattered, a sweep
 * taking double, 0.12 blocks a tick, flames of 19 ticks) is its design. The GameTests check the numbers are applied;
 * this checks they are the right numbers.
 */
class CindermothSwarmNumbersTest {
    @Test
    void light() {
        assertEquals(12.0, CindermothSwarm.SEEK_RANGE);
        assertEquals(200, CindermothSwarm.HOVER_TICKS);
        assertEquals(20, CindermothSwarm.BURN_INTERVAL);
    }

    @Test
    void design() {
        assertEquals(19, CindermothSwarm.FLAME_TICKS);
        assertEquals(40, CindermothSwarm.SCATTER_TICKS);
        assertEquals(0.5, CindermothSwarm.SCATTER_PUSH);
        assertEquals(2.0F, CindermothSwarm.SWEEP_DAMAGE);
        assertEquals(0.9F, CindermothSwarm.SWEEP_CHARGE);
        assertEquals(0.12, CindermothSwarm.SEEK_SPEED);
        assertEquals(1.0, CindermothSwarm.HOVER_REACH);
        assertEquals(0.8, CindermothSwarm.HOVER_HEIGHT);
        assertEquals(10, CindermothSwarm.LOOK_TICKS);
        assertEquals(300, CindermothSwarm.GIVE_UP_TICKS);
        assertEquals(0.1, CindermothSwarm.TOUCH_REACH);
        assertTrue(CindermothSwarm.SEEK_SPEED < 0.216, "it drifts slower than a walk, so walking away escapes it");
        assertTrue(CindermothSwarm.FLAME_TICKS < CindermothSwarm.BURN_INTERVAL, "its flames never last long enough to burn on their own");
        assertTrue(CindermothSwarm.GIVE_UP_TICKS > CindermothSwarm.SEEK_RANGE / CindermothSwarm.SEEK_SPEED,
                "it never gives up a light it could still reach from the edge of its range");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("cindermoth_swarm");
        assertEquals(10.0F, row.health());
        assertEquals(1.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(1.0F, row.width());
        assertEquals(1.0F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(12, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertTrue(!row.anyLight());
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals("soul_moths", row.secondLook());
        assertEquals("ember_wing", row.material());
    }
}
