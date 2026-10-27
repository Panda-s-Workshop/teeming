package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.FacetDoppel;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Facet Doppel's numbers against its row, written out as plain values: HP 36, Atk and Arm copied (0 of its own),
 * 0.6 x 1.95, tell 20 (the flicker on first sight); End midlands, weight 3, groups of 1; second look Obsidian Doppel;
 * drops void ichor. Then the calls made here: a bare hand copies as a fist's 1, a flicker that copied nothing waits 40
 * ticks, and its blows have an 8-tick wind-up. The GameTests check the copy is made in the world from real gear; this
 * checks they are the right numbers.
 */
class FacetDoppelNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, FacetDoppel.TELL_TICKS);
        // the calls made here: a fist is 1, the retry after a flicker that copied nothing, the blow's wind-up, reach,
        // arc and cooldown, the glow stages and the flag bits the model reads
        assertEquals(1.0, FacetDoppel.FIST_DAMAGE);
        assertEquals(40, FacetDoppel.RETRY_TICKS);
        assertEquals(8, FacetDoppel.WINDUP_TICKS);
        assertEquals(6, FacetDoppel.STRIKE_TICKS);
        assertEquals(20, FacetDoppel.COOLDOWN);
        assertEquals(1.0F, FacetDoppel.STRIKE);
        assertEquals(0.8, FacetDoppel.START_REACH);
        assertEquals(1.4, FacetDoppel.STRIKE_REACH);
        assertEquals(120.0F, FacetDoppel.STRIKE_ARC);
        assertEquals(15.0F, FacetDoppel.TURN);
        assertEquals(0, FacetDoppel.GLOW_REST);
        assertEquals(1, FacetDoppel.GLOW_FLICKER);
        assertEquals(0xF, FacetDoppel.KIND_MASK);
        assertEquals(4, FacetDoppel.FLAG_COPIED);
        assertEquals(5, FacetDoppel.FLAG_SHATTERED);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("facet_doppel");
        assertEquals(36.0F, row.health());
        assertEquals(0.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(1.95F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(3, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals("obsidian_doppel", row.secondLook());
        assertEquals("void_ichor", row.material());
        assertEquals(row.tellTicks(), FacetDoppel.TELL_TICKS);
    }

    @Test
    void houseRules() {
        // every tell at least 8 ticks (the blow is 1 x Atk, under the 1.5 x that asks for 10); the flicker is long enough
        // to switch to a weaker item (a hotbar key is one tick); the flag bits don't reach the stage bits (24 and up)
        assertTrue(FacetDoppel.WINDUP_TICKS >= 8);
        assertTrue(FacetDoppel.TELL_TICKS >= 8);
        assertTrue(FacetDoppel.STRIKE < 1.5F);
        assertTrue(FacetDoppel.START_REACH < FacetDoppel.STRIKE_REACH);
        assertTrue(FacetDoppel.FLAG_SHATTERED < 24 && FacetDoppel.FLAG_COPIED > 3);
        assertTrue(FacetDoppel.RETRY_TICKS > FacetDoppel.TELL_TICKS, "a refused flicker doesn't strobe");
    }
}
