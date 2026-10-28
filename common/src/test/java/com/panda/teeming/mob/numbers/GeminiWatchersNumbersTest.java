package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.GeminiWatchers;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Gemini Watchers' numbers against their stat row, written out as plain values: HP 30 each, Atk 6, Arm 4, 0.7 x 2.0;
 * a 100 t revive beam: one dies, the other raises it after 100 t at 50 %, unless both are dead; the End barrens, weight
 * 3, in pairs; the Dusk Pair; void crystal. The GameTests check the numbers are applied; this checks they are the right
 * numbers.
 */
class GeminiWatchersNumbersTest {
    @Test
    void mechanic() {
        assertEquals(100, GeminiWatchers.REVIVE_TICKS);
        assertEquals(0.5F, GeminiWatchers.REVIVE_HEALTH);
    }

    @Test
    void ownCalls() {
        // the numbers its stat row leaves open: the staff blow's shape and pace, the beam's reach and hum, standing up,
        // the egg's reach, the wait for a partner after a load, the husk's size
        assertEquals(8, GeminiWatchers.TELL_TICKS);
        assertEquals(4, GeminiWatchers.AIM_TICKS);
        assertEquals(8, GeminiWatchers.STRIKE_TICKS);
        assertEquals(30, GeminiWatchers.SWING_EVERY);
        assertEquals(1.0F, GeminiWatchers.STRIKE);
        assertEquals(0.6, GeminiWatchers.START_REACH);
        assertEquals(1.2, GeminiWatchers.STRIKE_REACH);
        assertEquals(120.0F, GeminiWatchers.STRIKE_ARC);
        assertEquals(20.0F, GeminiWatchers.TURN);
        assertEquals(12.0, GeminiWatchers.CHANNEL_RANGE);
        assertEquals(20, GeminiWatchers.HUM_EVERY);
        assertEquals(20, GeminiWatchers.RISE_TICKS);
        assertEquals(8.0, GeminiWatchers.EGG_LINK);
        assertEquals(40, GeminiWatchers.PARTNER_WAIT);
        assertEquals(1.2F, GeminiWatchers.HUSK_WIDTH);
        assertEquals(0.45F, GeminiWatchers.HUSK_HEIGHT);
        assertEquals(0.25, GeminiWatchers.SPEED);
        assertEquals(12, GeminiWatchers.XP);
    }

    @Test
    void houseRules() {
        // the staff blow hurts a player, so it gets a tell of at least 8 ticks, and the aim locks with time left to step aside
        assertTrue(GeminiWatchers.TELL_TICKS >= 8);
        assertTrue(GeminiWatchers.AIM_TICKS < GeminiWatchers.TELL_TICKS);
        // its blow is its plain attack, under 1.5x, so 8 ticks of tell is enough
        assertTrue(GeminiWatchers.STRIKE < 1.5F);
        // a blow runs its course before the next wind-up may start
        assertTrue(GeminiWatchers.TELL_TICKS + GeminiWatchers.STRIKE_TICKS < GeminiWatchers.SWING_EVERY);
        // the beam hums at least four times, and standing up is over well inside another beam's length
        assertTrue(GeminiWatchers.REVIVE_TICKS / GeminiWatchers.HUM_EVERY >= 4);
        assertTrue(GeminiWatchers.RISE_TICKS < GeminiWatchers.REVIVE_TICKS);
        // a partner missing after a load is given up well before a beam could have finished
        assertTrue(GeminiWatchers.PARTNER_WAIT < GeminiWatchers.REVIVE_TICKS);
        // the husk glows dark for the first quarter of the beam and brightest for the last, one step a quarter
        assertEquals(GeminiWatchers.STAGE_DARK, GeminiWatchers.Revive.stage(0));
        assertEquals(GeminiWatchers.STAGE_DARK, GeminiWatchers.Revive.stage(24));
        assertEquals(2, GeminiWatchers.Revive.stage(25));
        assertEquals(3, GeminiWatchers.Revive.stage(50));
        assertEquals(GeminiWatchers.STAGE_BRIGHTEST, GeminiWatchers.Revive.stage(75));
        assertEquals(GeminiWatchers.STAGE_BRIGHTEST, GeminiWatchers.Revive.stage(100));
        // the flags it uses stay clear of the visual stage, which uses the top bits
        assertTrue(GeminiWatchers.FLAG_HUSK < 24 && GeminiWatchers.FLAG_MIRROR < 24 && GeminiWatchers.FLAG_BEAM < 24);
        assertTrue(GeminiWatchers.Beam.END_BIT <= 24 && GeminiWatchers.Beam.X_BIT > GeminiWatchers.FLAG_BEAM);
        // the beam's packing reaches past the farthest a twin can be and still channel
        assertTrue(15.75 > GeminiWatchers.CHANNEL_RANGE);
        // End experience: 10 to 20
        assertTrue(GeminiWatchers.XP >= 10 && GeminiWatchers.XP <= 20);
    }

    @Test
    void beam() {
        // the way to the husk packs into the flags in quarter blocks, signed, and comes back out the same
        int bits = GeminiWatchers.Beam.encode(-11.75, 2.5, 7.25);
        assertEquals(-11.75, GeminiWatchers.Beam.x(bits));
        assertEquals(2.5, GeminiWatchers.Beam.y(bits));
        assertEquals(7.25, GeminiWatchers.Beam.z(bits));
        // too far is held at the edge, not wrapped round
        assertEquals(15.75, GeminiWatchers.Beam.x(GeminiWatchers.Beam.encode(40.0, 0.0, 0.0)));
        assertEquals(-15.75, GeminiWatchers.Beam.y(GeminiWatchers.Beam.encode(0.0, -40.0, 0.0)));
        // and nothing spills below its first bit
        assertEquals(0, GeminiWatchers.Beam.encode(15.75, -15.75, 15.75) & ((1 << GeminiWatchers.Beam.X_BIT) - 1));
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("gemini_watchers");
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(30.0F, row.health());
        assertEquals(6.0F, row.attack());
        assertEquals(4.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(2.0F, row.height());
        assertEquals(100, row.tellTicks());
        assertEquals(3, row.weight());
        assertEquals(2, row.groupMin());
        assertEquals(2, row.groupMax());
        assertEquals("dusk_pair", row.secondLook());
        assertEquals("void_crystal", row.material());
    }
}
