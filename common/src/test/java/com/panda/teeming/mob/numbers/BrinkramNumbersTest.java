package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.Brinkram;
import com.panda.teeming.mob.end.Brinkram.Brink;
import com.panda.teeming.roster.RosterStats;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/**
 * The Brinkram's numbers against its row, written out as plain values: HP 40, Atk 8, Arm 6, 1.2 x 1.4, tell 20 (the
 * paw); a charge at 0.5 b/t whose hit throws 2.5 out and 0.4 up; at an edge with no ground ahead it goes over 70 % of
 * the time; End midlands, weight 5, alone or in pairs; second look Purpur Ram; drops gravity stone. Then the edge rule
 * on mock columns (a step down of 4 is ground, 5 or the void is an edge, a wall is never one). The GameTests check the
 * numbers are applied in the world; this checks they are the right numbers.
 */
class BrinkramNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, Brinkram.TELL_TICKS);
        assertEquals(0.5, Brinkram.CHARGE_SPEED);
        assertEquals(2.5, Brinkram.KNOCKBACK_OUT);
        assertEquals(0.4, Brinkram.KNOCKBACK_UP);
        assertEquals(0.7F, Brinkram.OVER_CHANCE);
        assertEquals(4, Brink.MAX_DROP);
        // the calls made here: a 16-block charge (32 ticks), started 4 to 16 blocks out, a hit of 1 x Atk, a 2 s teeter
        // (inside the 40-100 t punish band), a 1 s head shake after a wall, a 1 s recovery, a 5 s rest, a 10 t butt
        // every 20 t at 1 x Atk, 6 ticks of run-on over an edge, drops 0.75 back from the brink, the kill credited to
        // the baiting player for 20 s, knockback resistance 0.5
        assertEquals(32, Brinkram.CHARGE_TICKS);
        assertEquals(4.0, Brinkram.MIN_RANGE);
        assertEquals(16.0, Brinkram.MAX_RANGE);
        assertEquals(1.0F, Brinkram.CHARGE_HIT);
        assertEquals(40, Brinkram.TEETER_TICKS);
        assertEquals(20, Brinkram.BONK_TICKS);
        assertEquals(20, Brinkram.RECOVER_TICKS);
        assertEquals(100, Brinkram.COOLDOWN);
        assertEquals(10, Brinkram.BUTT_TICKS);
        assertEquals(20, Brinkram.BUTT_EVERY);
        assertEquals(1.0F, Brinkram.BUTT_HIT);
        assertEquals(6, Brinkram.OVER_PUSH_TICKS);
        assertEquals(400, Brinkram.OVER_MAX_TICKS);
        assertEquals(0.75, Brinkram.LOOT_STEP_BACK);
        assertEquals(400, Brinkram.OVER_CREDIT_TICKS);
        assertEquals(0.5, Brinkram.KNOCKBACK_RESISTANCE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("brinkram");
        assertEquals(40.0F, row.health());
        assertEquals(8.0F, row.attack());
        assertEquals(6.0F, row.armor());
        assertEquals(1.2F, row.width());
        assertEquals(1.4F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(5, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("purpur_ram", row.secondLook());
        assertEquals("gravity_stone", row.material());
        assertEquals(row.tellTicks(), Brinkram.TELL_TICKS);
    }

    @Test
    void houseRules() {
        // the tell is at least 10 ticks (a blow of 1 x Atk asks for 8; a charge that can throw you off an island gets
        // the longer one); the butt's own wind-up is at least 8; the teeter is a punish window of 40-100 ticks; the charge runs its full
        // reach in its length; the throw is the one End blow allowed over the void, and its lift is under its reach
        assertTrue(Brinkram.TELL_TICKS >= 10);
        assertTrue(Brinkram.BUTT_TICKS >= 8);
        assertTrue(Brinkram.BUTT_EVERY > Brinkram.BUTT_TICKS);
        assertTrue(Brinkram.TEETER_TICKS >= 40 && Brinkram.TEETER_TICKS <= 100);
        assertEquals(Brinkram.MAX_RANGE, Brinkram.CHARGE_SPEED * Brinkram.CHARGE_TICKS, 1.0E-9);
        assertTrue(Brinkram.CHARGE_HIT < 1.5F && Brinkram.BUTT_HIT < 1.5F);
        assertTrue(Brinkram.OVER_PUSH_TICKS * Brinkram.CHARGE_SPEED >= 1.2 + 0.5, "its run-on carries its whole body past the lip");
        assertTrue(Brinkram.OVER_CREDIT_TICKS >= 200, "outlasts a fall from the islands into the void");
        assertTrue(Brinkram.LOOT_STEP_BACK < Brinkram.MIN_RANGE);
    }

    // ---- the edge rule ---------------------------------------------------------------------------------------------

    /** A column whose solid cells (by height from the feet' cell) are {@code solid}; everything else open. */
    private static IntPredicate column(Integer... solid) {
        Set<Integer> at = Set.of(solid);
        return at::contains;
    }

    @Test
    void levelGroundIsNoEdge() {
        assertFalse(Brink.edge(column(-1)), "ground at its own height");
    }

    @Test
    void stepsDownToFourAreGround() {
        assertFalse(Brink.edge(column(-2)), "a step down of 1");
        assertFalse(Brink.edge(column(-4)), "a drop of 3");
        assertFalse(Brink.edge(column(-5)), "a drop of 4 is still ground");
    }

    @Test
    void aDropOfFiveIsAnEdge() {
        assertTrue(Brink.edge(column(-6)), "a drop of 5");
        assertTrue(Brink.edge(column(-7, -8)), "a deep pit");
        assertTrue(Brink.edge(dy -> false), "the void: nothing at all below");
    }

    @Test
    void aWallIsNeverAnEdge() {
        assertFalse(Brink.edge(column(0)), "a block where its feet go is a wall");
        assertFalse(Brink.edge(column(0, 1)), "a tall wall");
    }

    @Test
    void anOverhangAboveDoesNotCount() {
        assertTrue(Brink.edge(column(1, 2)), "only what lies below the feet is ground");
    }
}
