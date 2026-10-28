package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Driftsong;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import org.junit.jupiter.api.Test;

/**
 * The Driftsong's numbers against its roster row, written out as plain values: HP 120, Atk 0, Arm 6, 6.0 x 2.5, no
 * tell; drifts 0.05 b/t under the islands, sings every 400 t; Slow Falling 200 t to players within 24; local cap 1 in
 * 128; the void below the islands, alone; Starlit Leviathan; void membrane. The GameTests check the numbers are
 * applied; this checks they are the right numbers.
 */
class DriftsongNumbersTest {
    @Test
    void mechanic() {
        assertEquals(0.05, Driftsong.DRIFT_SPEED);
        assertEquals(400, Driftsong.SONG_EVERY);
        assertEquals(200, Driftsong.SLOW_FALLING_TICKS);
        assertEquals(24.0, Driftsong.SONG_RANGE);
        assertEquals(128, Driftsong.CAP_RADIUS);
        assertEquals(128, TeemingSpawns.RARE_CAP_RADIUS);
        assertEquals(10.0, Driftsong.DIVE_DEPTH);
        assertEquals(20, Driftsong.SWELL_TICKS);
    }

    @Test
    void houseRules() {
        assertTrue(Driftsong.SLOW_FALLING_TICKS <= 200, "no effect over 200 t");
        assertTrue(Driftsong.SWELL_TICKS >= 8, "the swell before the song reads as a cue: at least 8 t");
        assertTrue(Driftsong.SWELL_TICKS + Driftsong.SING_TICKS < Driftsong.SONG_EVERY, "the song is over before the next swell");
        assertEquals(Driftsong.DIVE_DEPTH, Driftsong.DIVE_SPEED * Driftsong.DIVE_TICKS, 1.0E-9, "the dive's pose lasts as long as the dive");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("driftsong");
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Cat.CREATURE, row.cat());
        assertEquals(120.0F, row.health());
        assertEquals(0.0F, row.attack());
        assertEquals(6.0F, row.armor());
        assertEquals(6.0F, row.width());
        assertEquals(2.5F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(0, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals(RosterStats.Place.NONE, row.place());
        assertEquals("void_below", row.spawner());
        assertEquals("starlit_leviathan", row.secondLook());
        assertEquals("void_membrane", row.material());
    }
}
