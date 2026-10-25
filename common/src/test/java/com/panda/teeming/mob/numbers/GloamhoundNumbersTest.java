package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.overworld.Gloamhound;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * Gloamhound's numbers against its design, written out as plain values: tell 14 t (the crouch); packs of 3 to 4 with
 * one alpha (health 30, attack 6); one hound darts at a time and bites for its attack; the alpha howls every 200 t for
 * Speed I; when it dies the rest flee for 200 t. The GameTests check the numbers are applied; this checks they are the
 * right numbers.
 */
class GloamhoundNumbersTest {
    @Test
    void mechanic() {
        assertEquals(14, Gloamhound.TELL_TICKS);
        assertEquals(30.0F, Gloamhound.ALPHA_HEALTH);
        assertEquals(6.0F, Gloamhound.ALPHA_ATTACK);
        assertEquals(1.0F, Gloamhound.BITE);
        assertEquals(4, Gloamhound.PACK_MAX);
        assertEquals(200, Gloamhound.HOWL_EVERY);
        assertEquals(0, Gloamhound.HOWL_SPEED_LEVEL);
        assertEquals(200, Gloamhound.FLEE_TICKS);
        // the calls made here: Speed for 6 s within 16 blocks, darts at 0.6 b/t for up to 12 t, 10 t between turns
        assertEquals(120, Gloamhound.HOWL_SPEED_TICKS);
        assertEquals(16.0, Gloamhound.HOWL_RADIUS);
        assertEquals(0.6, Gloamhound.DART_SPEED);
        assertEquals(12, Gloamhound.DART_TICKS);
        assertEquals(10, Gloamhound.TURN_GAP);
        assertEquals(1.2, Gloamhound.ALPHA_SCALE);
    }

    @Test
    void stats() {
        // HP 18 (alpha 30), Atk 4 (alpha 6), Arm 1, 0.8 x 0.85, tell 14; no blow above its bite; taiga, weight 15, 3-4
        RosterStats.Entry row = RosterStats.get("gloamhound");
        assertEquals(18.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(1.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(0.85F, row.height());
        assertEquals(14, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(15, row.weight());
        assertEquals(3, row.groupMin());
        assertEquals(4, row.groupMax());
        assertEquals(row.tellTicks(), Gloamhound.TELL_TICKS);
        assertEquals(row.groupMax(), Gloamhound.PACK_MAX);
    }

    @Test
    void houseRules() {
        // a tell of at least 8 ticks, and of 10 for a blow of 1.5 x Atk or more (the bite is 1 x); no effect over 200 t;
        // a hound circling its target can reach it with a dart; bites stay further apart than the 10-tick hurt cooldown;
        // the howl reaches only hounds the pack counts
        assertTrue(Gloamhound.TELL_TICKS >= 8);
        assertTrue(Gloamhound.BITE < 1.5F || Gloamhound.TELL_TICKS >= 10);
        assertTrue(Gloamhound.HOWL_SPEED_TICKS <= 200);
        assertTrue(Gloamhound.HOWL_SPEED_TICKS < Gloamhound.HOWL_EVERY);
        assertTrue(Gloamhound.CIRCLE_RADIUS < Gloamhound.DART_RANGE);
        assertTrue(Gloamhound.DART_SPEED * Gloamhound.DART_TICKS > Gloamhound.DART_RANGE);
        assertTrue(Gloamhound.TURN_GAP + Gloamhound.TELL_TICKS > 10);
        assertTrue(Gloamhound.HOWL_RADIUS <= Gloamhound.PACK_RANGE);
        assertTrue(Gloamhound.ALPHA_SCALE >= 0.8 && Gloamhound.ALPHA_SCALE <= 1.3);
    }
}
