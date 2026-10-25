package com.panda.teeming.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.LiftstoneMonolith;
import com.panda.teeming.roster.RosterStats;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The apex spawn rules as numbers: each apex is placed only by its apex rule (the Basalt Behemoth on open Nether ground,
 * the Liftstone Monolith on the End's main island), on no biome list; a won apex roll comes 1 attempt in 80; and none
 * spawns within 256 blocks of another, twice the 128-block local cap. The GameTests check the rules at work in the
 * world (spawner_apex, spawner_apex_island).
 */
class ApexSpawnTest {
    @Test
    void eachApexHasItsRule() {
        RosterStats.Entry monolith = RosterStats.get("liftstone_monolith");
        assertEquals("apex_island", monolith.spawner());
        assertTrue(monolith.apex());
        assertEquals(RosterStats.Dim.END, monolith.dim());
        assertFalse(monolith.biomeSpawns());
        assertEquals(1, monolith.groupMin());
        assertEquals(1, monolith.groupMax());
        RosterStats.Entry behemoth = RosterStats.get("basalt_behemoth");
        assertEquals("apex_ground", behemoth.spawner());
        assertFalse(behemoth.biomeSpawns());
    }

    @Test
    void mainIslandPicksLeaveTheMonolithOut() {
        // the weighted main-island picks (weight 0 counts as 1) no longer hold the Monolith, which took 1 in 9 of them;
        // the Astral Augur keeps its weight 4 there
        List<RosterStats.Entry> island = RosterStats.ALL.stream().filter(e -> e.spawner().equals("main_island")).toList();
        assertTrue(island.stream().noneMatch(e -> e.id().equals("liftstone_monolith")));
        RosterStats.Entry augur = RosterStats.get("astral_augur");
        assertTrue(island.contains(augur));
        assertEquals(4, augur.weight());
        assertTrue(island.stream().noneMatch(RosterStats.Entry::apex), "no apex among the weighted main-island picks");
    }

    @Test
    void rollAndRadius() {
        assertEquals(1.0 / 80.0, SpawnTable.APEX_CHANCE, 1.0E-12);
        // one full attempt per player every 200 ticks: a won roll every 13.3 minutes, under 20 even with a quarter of
        // won rolls lost to a column without room, light or a cap
        double minutes = (1.0 / SpawnTable.APEX_CHANCE) * 200 / 20.0 / 60.0;
        assertTrue(minutes >= 10.0 && minutes / 0.75 <= 20.0, "a won roll every " + minutes + " minutes");
        assertEquals(256, SpawnTable.APEX_RADIUS);
        assertEquals(2 * LiftstoneMonolith.CAP_RADIUS, SpawnTable.APEX_RADIUS);
    }
}
