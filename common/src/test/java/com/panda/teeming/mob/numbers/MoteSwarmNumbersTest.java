package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.MoteSwarm;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Mote Swarm's numbers, written out as plain values: HP 30, 1 a mote (attack 1), Arm 0, 1.5 x 1.5; single-target
 * hits x0.25, sweeps, area damage, explosions and splash potions x3; small end islands and end midlands, weight 4,
 * alone; second look Ember Motes; drops star mote. The rest (30 motes, 3 a tick, an 8 tick gather, a 10 tick surge, 40
 * ticks loosening, 10 ticks wrapped before it gathers, 0.15 blocks a tick) is its design. The GameTests check the
 * numbers are applied; this checks they are the right numbers.
 */
class MoteSwarmNumbersTest {
    @Test
    void damageTaken() {
        assertEquals(0.25F, MoteSwarm.SINGLE_TARGET_DAMAGE);
        assertEquals(3.0F, MoteSwarm.SPREAD_DAMAGE);
        assertEquals(1.0, MoteSwarm.SWEEP_ACROSS);
        assertEquals(0.25, MoteSwarm.SWEEP_UP);
    }

    @Test
    void design() {
        assertEquals(30, MoteSwarm.MOTES);
        assertEquals(3, MoteSwarm.MOTES_PER_TICK);
        assertEquals(8, MoteSwarm.TELL_TICKS);
        assertEquals(10, MoteSwarm.SURGE_TICKS);
        assertEquals(40, MoteSwarm.LOOSEN_TICKS);
        assertEquals(10, MoteSwarm.WRAP_TICKS);
        assertEquals(0.15, MoteSwarm.DRIFT_SPEED);
        assertEquals(5, MoteSwarm.XP);
        assertEquals(8, MoteSwarm.SHOWN_BIT);
    }

    @Test
    void houseRules() {
        // the surge hurts a player, so it has a tell of at least 8 ticks: each mote is a blow of its own of 1 x its
        // attack, under 1.5 x, so 8 is enough
        assertTrue(MoteSwarm.TELL_TICKS >= 8);
        // a full swarm's motes pass through a body in exactly the surge, 3 a tick
        assertEquals(MoteSwarm.MOTES, MoteSwarm.MOTES_PER_TICK * MoteSwarm.SURGE_TICKS);
        // it drifts slower than a walk (0.216 blocks a tick), so walking away leaves it
        assertTrue(MoteSwarm.DRIFT_SPEED < 0.216);
        // stepping out while it gathers is possible: a walk in 8 ticks (1.7 blocks) clears the 1.5 block cloud from its middle
        assertTrue(0.216 * MoteSwarm.TELL_TICKS > 0.75 + 0.3);
        // the mote count fits its six synced bits, below the stage bits (24..27)
        assertTrue(MoteSwarm.MOTES < 64 && MoteSwarm.SHOWN_BIT + 6 <= 24);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("mote_swarm");
        assertEquals(30.0F, row.health());
        assertEquals(1.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(1.5F, row.width());
        assertEquals(1.5F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals("ember_motes", row.secondLook());
        assertEquals("star_mote", row.material());
    }
}
