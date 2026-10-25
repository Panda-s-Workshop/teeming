package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.ChorusHusk;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Chorus Husk's numbers against its row, written out as plain values: HP 30, Atk 6, Arm 2, 0.7 x 2.0, tell 20
 * (the swell as it dies); the burst teleports everything within 4 blocks as chorus fruit does, up to 8 blocks away; End
 * highlands, weight 5, groups of 1 to 2; second look Blooming Husk; drops chorus silk. Then the calls made here: the
 * blow's 8-tick wind-up, the shortest teleport, the fruit a spared burst leaves. The GameTests check the numbers are
 * applied in the world (the roll from 3 to 8 blocks too: calling a method would load the entity class, which needs the
 * game); this checks they are the right numbers.
 */
class ChorusHuskNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, ChorusHusk.SWELL_TICKS);
        assertEquals(4.0, ChorusHusk.BURST_RADIUS);
        assertEquals(8.0, ChorusHusk.TELEPORT_FAR);
        // the calls made here: a teleport of at least 3 blocks (one that barely moves you doesn't read), 2 fruit from a
        // burst that caught no player, an 8-tick wind-up and a 1 x Atk blow with its reach, arc and cooldown
        assertEquals(3.0, ChorusHusk.TELEPORT_NEAR);
        assertEquals(2, ChorusHusk.SPARED_FRUIT);
        assertEquals(8, ChorusHusk.WINDUP_TICKS);
        assertEquals(6, ChorusHusk.STRIKE_TICKS);
        assertEquals(20, ChorusHusk.COOLDOWN);
        assertEquals(1.0F, ChorusHusk.STRIKE);
        assertEquals(0.8, ChorusHusk.START_REACH);
        assertEquals(1.4, ChorusHusk.STRIKE_REACH);
        assertEquals(120.0F, ChorusHusk.STRIKE_ARC);
        assertEquals(15.0F, ChorusHusk.TURN);
        assertEquals(0, ChorusHusk.GLOW_REST);
        assertEquals(1, ChorusHusk.GLOW_SWELL);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("chorus_husk");
        assertEquals(30.0F, row.health());
        assertEquals(6.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(2.0F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(5, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("blooming_husk", row.secondLook());
        assertEquals("chorus_silk", row.material());
        assertEquals(row.tellTicks(), ChorusHusk.SWELL_TICKS);
    }

    @Test
    void houseRules() {
        // every tell at least 8 ticks (the blow is 1 x Atk, under the 1.5 x that asks for 10); the swell is long enough
        // to walk out of the burst (4 blocks at a walk of 4.3 blocks a second takes under the swell's second)
        assertTrue(ChorusHusk.WINDUP_TICKS >= 8);
        assertTrue(ChorusHusk.SWELL_TICKS >= 8);
        assertTrue(ChorusHusk.STRIKE < 1.5F);
        assertTrue(ChorusHusk.BURST_RADIUS / 4.317 * 20.0 <= ChorusHusk.SWELL_TICKS, "a walk gets you out in time");
        assertTrue(ChorusHusk.TELEPORT_NEAR < ChorusHusk.TELEPORT_FAR);
        assertTrue(ChorusHusk.START_REACH < ChorusHusk.STRIKE_REACH);
    }
}
