package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Pullstar;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Pullstar's numbers against its roster row, written out as plain values: HP 30, Atk 8.5 (touch), Arm 4, 1.0 x 1.0;
 * a 20-tick tell before the dim; pulls everything within 8 (sneaking x0.25); dims 40 ticks in every 100: no pull, and it
 * takes x2; the End barrens at weight 4, alone; second look Red Pullstar; drops gravity stone. Its own numbers (a drift
 * of 0.09 blocks a tick, an 8-tick flare before the touch, a touch every 28 ticks at most, hovering a block up) are its
 * design.
 */
class PullstarNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, Pullstar.TELL_TICKS);
        assertEquals(100, Pullstar.CYCLE_TICKS);
        assertEquals(40, Pullstar.DIM_TICKS);
        assertEquals(40, Pullstar.BRIGHT_TICKS);
        assertEquals(2.0F, Pullstar.DIM_DAMAGE);
        assertEquals(8.0, Pullstar.PULL_RADIUS);
        assertEquals(0.25, Pullstar.SNEAK_SHARE);
        assertEquals(1.0F, Pullstar.TOUCH_HIT);
    }

    @Test
    void design() {
        assertEquals(0.09, Pullstar.PULL_DRIFT);
        assertEquals(8, Pullstar.FLARE_TICKS);
        assertEquals(28, Pullstar.TOUCH_EVERY);
        assertEquals(0.25, Pullstar.TOUCH_REACH);
        assertEquals(1.0, Pullstar.HOVER);
        assertEquals(6.0, Pullstar.KEEP);
        assertEquals(10, Pullstar.XP);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("pullstar");
        assertEquals(30.0F, row.health());
        assertEquals(8.5F, row.attack());
        assertEquals(4.0F, row.armor());
        assertEquals(1.0F, row.width());
        assertEquals(1.0F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(4, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals(RosterStats.Place.GROUND, row.place());
        assertEquals("red_pullstar", row.secondLook());
        assertEquals("gravity_stone", row.material());
    }

    @Test
    void houseRules() {
        assertTrue(Pullstar.FLARE_TICKS >= 8, "the touch has a tell of at least 8 ticks");
        assertTrue(Pullstar.TOUCH_HIT < 1.5F, "the touch is an ordinary blow, under 1.5x its attack");
        assertTrue(Pullstar.DIM_TICKS >= 40 && Pullstar.DIM_TICKS <= 100, "a punish window of 40-100 ticks");
        assertTrue(Pullstar.DIM_DAMAGE >= 1.5F && Pullstar.DIM_DAMAGE <= 2.0F, "a punish multiplier of x1.5-x2");
        assertTrue(Pullstar.TOUCH_EVERY > Pullstar.FLARE_TICKS + 10, "the hurt cooldown has run out before the next touch lands");
        // a walking player (4.317 m/s, 0.216 blocks a tick) outpaces the pull; a sneaking one (0.065) outpaces it too
        assertTrue(Pullstar.PULL_DRIFT < 0.216 / 2, "a walk is more than twice the pull");
        assertTrue(Pullstar.PULL_DRIFT * Pullstar.SNEAK_SHARE < 0.065 / 2, "a sneak is more than twice the sneaking pull");
    }
}
