package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Brinkray;
import com.panda.teeming.mob.end.Brinkray.Brink;
import com.panda.teeming.roster.RosterStats;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/**
 * The Brinkray's numbers against its row, written out as plain values: neutral, HP 40, Atk 8, Arm 2, 3.0 x 0.6; a 20 t
 * bank; hostile only to players standing where 20 or more blocks of air lie below within 1 block; the ram throws 1.8;
 * small islands and the outer End by the spawner's void-air rule, weight 0, one at a time; second look Starry Ray; drops
 * void membrane. Then the brink rule on mock columns (19 blocks of air is no brink, 20 is) and mock ground round the
 * feet (a drop 1 block off counts, one 2 blocks off doesn't). The GameTests check the numbers are applied in the world;
 * this checks they are the right numbers.
 */
class BrinkrayNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, Brinkray.TELL_TICKS);
        assertEquals(20, Brinkray.DROP);
        assertEquals(1.0, Brinkray.BRINK_REACH);
        assertEquals(1.8, Brinkray.KNOCKBACK);
        // the calls made here: provoked, it hunts its attacker for 200 t (10 s, like a vanilla neutral mob); a ram that
        // meets rock stuns it 60 t (a punish window in the 40-100 t band, no damage bonus); the ram is 1 x Atk at 1 block a
        // tick for at most 16, its leading edge reaching 0.8 ahead, launched from 7 out, at -0.2 under the feet at a brink
        // (it skims the deck) and 0.6 up elsewhere; a throw of 0.3 up; 40 t pulling away, peeled 0.9 rad aside; it looks 32
        // out every 5 t
        assertEquals(200, Brinkray.GRUDGE_TICKS);
        assertEquals(60, Brinkray.STUN_TICKS);
        assertEquals(1.0F, Brinkray.RAM_HIT);
        assertEquals(1.0, Brinkray.RAM_SPEED);
        assertEquals(16, Brinkray.RAM_TICKS);
        assertEquals(0.8, Brinkray.RAM_REACH);
        assertEquals(7.0, Brinkray.APPROACH);
        assertEquals(-0.2, Brinkray.LEG_LEVEL);
        assertEquals(0.6, Brinkray.BODY_LEVEL);
        assertEquals(0.3, Brinkray.KNOCKBACK_UP);
        assertEquals(40, Brinkray.RECOVER_TICKS);
        assertEquals(0.9F, Brinkray.PEEL);
        assertEquals(32.0, Brinkray.HUNT_RANGE);
        assertEquals(5, Brinkray.SCAN_EVERY);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("brinkray");
        assertEquals(RosterStats.Cat.NEUTRAL, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(40.0F, row.health());
        assertEquals(8.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(3.0F, row.width());
        assertEquals(0.6F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(0, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("void_air", row.spawner());
        assertEquals("starry_ray", row.secondLook());
        assertEquals("void_membrane", row.material());
        assertEquals(row.tellTicks(), Brinkray.TELL_TICKS);
    }

    @Test
    void houseRules() {
        // the bank is a tell of at least 10 ticks before a blow that can throw a player off a bridge; the stun is a punish
        // window of 40-100 ticks; nothing it gives lasts over 200 ticks; the ram is under 1.5 x Atk; it runs from its
        // banking spot to its quarry and past within one ram; the throw lifts less than it carries
        assertTrue(Brinkray.TELL_TICKS >= 10);
        assertTrue(Brinkray.STUN_TICKS >= 40 && Brinkray.STUN_TICKS <= 100);
        assertTrue(Brinkray.GRUDGE_TICKS <= 200);
        assertTrue(Brinkray.RAM_HIT < 1.5F);
        assertTrue(Brinkray.RAM_SPEED * Brinkray.RAM_TICKS > Brinkray.APPROACH + 3.0);
        assertTrue(Brinkray.KNOCKBACK_UP < Brinkray.KNOCKBACK);
        assertTrue(Brinkray.LEG_LEVEL < 0.0 && Brinkray.LEG_LEVEL + 0.6 > 0.0, "at a brink its body spans the deck's top: it hits the legs or the rock");
    }

    // ---- the brink rule --------------------------------------------------------------------------------------------

    /** A column whose solid cells (by depth under the feet' cell, 1 = the block stood on) are {@code solid}. */
    private static IntPredicate column(Integer... solid) {
        Set<Integer> at = Set.of(solid);
        return at::contains;
    }

    @Test
    void nineteenBlocksOfAirIsNoBrink() {
        assertFalse(Brink.drop(column(20)), "rock 20 down leaves 19 of air");
        assertFalse(Brink.drop(column(1)), "the block stood on");
        assertFalse(Brink.drop(column(10)), "ground 10 down");
    }

    @Test
    void twentyBlocksOfAirIsABrink() {
        assertTrue(Brink.drop(column(21)), "rock 21 down leaves 20 of air");
        assertTrue(Brink.drop(column(40)), "a deep drop");
        assertTrue(Brink.drop(d -> false), "the void");
        assertTrue(Brink.drop(column(0, -1)), "what stands beside the feet doesn't count, only what lies below");
    }

    @Test
    void aDropOneBlockOffCountsTwoDoNot() {
        // feet in the middle of block (5, 5); the drop is the column of block x 6 (1 block east), then x 7 (2 east)
        assertEquals(1, Brink.open(5.5, 5.5, (x, z) -> x == 6 && z == 5).size(), "a drop beside the feet's block, due east");
        assertTrue(Brink.open(5.5, 5.5, (x, z) -> x == 7).isEmpty(), "a drop two blocks east is out of reach");
        assertTrue(Brink.open(5.5, 5.5, (x, z) -> false).isEmpty(), "solid ground all round");
        double[] way = Brink.open(5.5, 5.5, (x, z) -> x == 6 && z == 5).getFirst();
        assertEquals(1.0, way[0], 1.0E-9, "the way to it is east");
        assertEquals(0.0, way[1], 1.0E-9);
        // feet near the east side of their block: the next block east is 0.2 away, inside the reach, and so is its corner
        assertFalse(Brink.open(5.8, 5.5, (x, z) -> x >= 6).isEmpty(), "0.2 from a drop");
        // feet near the west side: the next block east is 0.9 away, still inside the reach; the one after is 1.9 away
        assertFalse(Brink.open(5.1, 5.5, (x, z) -> x >= 6).isEmpty(), "0.9 from a drop");
        assertTrue(Brink.open(5.1, 5.5, (x, z) -> x >= 7).isEmpty(), "1.9 from a drop is out of reach");
    }
}
