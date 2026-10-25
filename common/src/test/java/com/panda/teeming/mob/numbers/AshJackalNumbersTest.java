package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.AshJackal;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * Ash Jackal's numbers against its design, written out as plain values: no tell (a plain bite); it takes dropped
 * items within 10 blocks, one stack each, and runs off for 200 t; it picks the lowest health fraction within 16. The
 * GameTests check the numbers are applied; this checks they are the right numbers.
 */
class AshJackalNumbersTest {
    @Test
    void mechanic() {
        assertEquals(10.0, AshJackal.ITEM_RANGE);
        assertEquals(200, AshJackal.FLEE_TICKS);
        assertEquals(16.0, AshJackal.TARGET_RANGE);
        assertEquals(1.0F, AshJackal.BITE);
        // the calls made here: a 6-tick dip to take an item, a new look for a weaker target every 10 t, a dropped
        // stack left alone by the pack for 200 t
        assertEquals(6, AshJackal.SNATCH_TICKS);
        assertEquals(10, AshJackal.RETARGET_TICKS);
        assertEquals(200, AshJackal.RECLAIM_TICKS);
        assertEquals(1.3, AshJackal.SNATCH_REACH);
    }

    @Test
    void stats() {
        // HP 16, Atk 4, Arm 1, 0.7 x 0.8, no tell; no blow above its bite; nether wastes and soul sand valley, weight 15, 2-3
        RosterStats.Entry row = RosterStats.get("ash_jackal");
        assertEquals(16.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(1.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(15, row.weight());
        assertEquals(2, row.groupMin());
        assertEquals(3, row.groupMax());
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals("soul_jackal", row.secondLook());
        assertEquals("ash_pelt", row.material());
    }

    @Test
    void houseRules() {
        // no tell, so no blow above 1 x Atk; no effect over 200 t; it can reach an item it stands over; it hunts farther
        // than it scavenges; the pack leaves a dropped stack alone at least as long as a run lasts
        assertTrue(AshJackal.BITE < 1.5F);
        assertTrue(AshJackal.FLEE_TICKS <= 200);
        assertTrue(AshJackal.SNATCH_REACH >= 0.7);
        assertTrue(AshJackal.SNATCH_TICKS >= 1 && AshJackal.SNATCH_TICKS < AshJackal.GIVE_UP_TICKS);
        assertTrue(AshJackal.TARGET_RANGE > AshJackal.ITEM_RANGE);
        assertTrue(AshJackal.RECLAIM_TICKS >= AshJackal.FLEE_TICKS);
    }
}
