package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.effect.EffectNumbers;
import com.panda.teeming.mob.end.AnchorGolem;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Anchor Golem's numbers against its roster row, written out as plain values: HP 44, Atk 7, Arm 8, 1.2 x 2.2; a
 * 14-tick wind-up; its hits make you Heavy for 120 ticks (no jumping, gravity x1.5, Levitation and Slow Falling
 * cancelled); End midlands, weight 4, alone; second look Rusted Anchor; drops gravity stone. The ring, the sink, the
 * swing's rhythm and its reach are its design. The GameTests check the numbers are applied; this checks they are the
 * right numbers.
 */
class AnchorGolemNumbersTest {
    @Test
    void blow() {
        assertEquals(14, AnchorGolem.TELL_TICKS);
        // its blow is its attack (7), not 1.5x it: an 8-tick tell is the floor; it takes 14
        assertEquals(1.0F, AnchorGolem.STRIKE);
        assertTrue(AnchorGolem.STRIKE < 1.5F && AnchorGolem.TELL_TICKS >= 8);
        assertEquals(12, AnchorGolem.SWING_TICKS);
        assertEquals(30, AnchorGolem.COOLDOWN);
        assertEquals(56, AnchorGolem.TELL_TICKS + AnchorGolem.SWING_TICKS + AnchorGolem.COOLDOWN, "a swing every 56 ticks at most");
        assertEquals(1.5, AnchorGolem.START_REACH);
        assertEquals(1.25, AnchorGolem.STRIKE_RADIUS);
        assertEquals(1.5, AnchorGolem.STRIKE_HEIGHT);
        assertEquals(15.0F, AnchorGolem.TURN);
    }

    @Test
    void heavy() {
        assertEquals(120, AnchorGolem.HEAVY_TICKS);
        assertEquals(120, EffectNumbers.HEAVY_TICKS);
        assertEquals(1.5, EffectNumbers.HEAVY_GRAVITY);
        assertEquals(0.0, EffectNumbers.HEAVY_JUMP);
        assertTrue(AnchorGolem.HEAVY_TICKS <= 200, "no effect over 200 ticks");
    }

    @Test
    void sink() {
        assertEquals(1.5, AnchorGolem.SINK_RADIUS);
        assertEquals(1.0, AnchorGolem.SINK_HEIGHT);
        assertEquals(160, AnchorGolem.SINK_TICKS);
        assertTrue(AnchorGolem.SINK_TICKS <= 200, "nothing it leaves lasts over 200 ticks");
        assertTrue(AnchorGolem.SINK_RADIUS > AnchorGolem.STRIKE_RADIUS, "the sink covers the ring it was left by");
        assertEquals(0.5, AnchorGolem.MIRED_SPEED);
        assertEquals(2, AnchorGolem.MIRED_GRACE);
    }

    @Test
    void body() {
        assertEquals(0.18, AnchorGolem.WALK_SPEED);
        assertEquals(0.7, AnchorGolem.KNOCKBACK_RESISTANCE);
        assertEquals(15, AnchorGolem.XP);
        assertTrue(AnchorGolem.XP >= 10 && AnchorGolem.XP <= 20, "End XP is 10 to 20");
    }

    @Test
    void theRingsAreDarkVioletNotGold() {
        // weak-point gold is #ffd36b; the rings are dark violet: more blue than red, more red than green, and dark
        int c = AnchorGolem.DARK_VIOLET;
        int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
        assertTrue(b > r && r > g && r + g + b < 300, String.format("dark violet %06x", c));
        assertFalse(c == 0xFFD36B);
        assertEquals(0x4A1E86, c);
        assertEquals(0xEEF0FF, AnchorGolem.STAR_WHITE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("anchor_golem");
        assertEquals(72, row.number());
        assertEquals(44.0F, row.health());
        assertEquals(7.0F, row.attack());
        assertEquals(8.0F, row.armor());
        assertEquals(1.2F, row.width());
        assertEquals(2.2F, row.height());
        assertEquals(14, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("", row.spawner());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertEquals("rusted_anchor", row.secondLook());
        assertEquals("gravity_stone", row.material());
        assertEquals(row.tellTicks(), AnchorGolem.TELL_TICKS);
    }
}
