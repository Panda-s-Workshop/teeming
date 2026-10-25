package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.overworld.Snagwood;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * Snagwood's numbers against its design, written out as plain values: tell 12 t (the creak); root sweep radius 3 in
 * a 120-degree arc in front for 1.2 x Atk; roots back after 160 t without a target; axes deal x2. The GameTests check
 * the numbers are applied; this checks they are the right numbers.
 */
class SnagwoodNumbersTest {
    @Test
    void mechanic() {
        assertEquals(12, Snagwood.TELL_TICKS);
        assertEquals(3.0, Snagwood.SWEEP_RADIUS);
        assertEquals(120.0F, Snagwood.SWEEP_ARC);
        assertEquals(1.2F, Snagwood.SWEEP_HIT);
        assertEquals(160, Snagwood.ROOT_BACK_TICKS);
        assertEquals(2.0F, Snagwood.AXE_DAMAGE);
    }

    @Test
    void stats() {
        // HP 30, Atk 5, Arm 6, 0.9 x 2.6, tell 12; strongest blow 1.2 x 5 = 6; forests, weight 15, alone
        RosterStats.Entry row = RosterStats.get("snagwood");
        assertEquals(30.0F, row.health());
        assertEquals(5.0F, row.attack());
        assertEquals(6.0F, row.armor());
        assertEquals(0.9F, row.width());
        assertEquals(2.6F, row.height());
        assertEquals(12, row.tellTicks());
        assertEquals(6.0F, row.blow());
        assertEquals(15, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals(row.tellTicks(), Snagwood.TELL_TICKS);
        assertEquals(row.blow(), row.attack() * Snagwood.SWEEP_HIT, 1.0E-5F);
    }

    @Test
    void houseRules() {
        // a tell of at least 8 ticks (the sweep is under 1.5 x Atk); the creak's trigger lies inside the sweep's reach,
        // so a target that stands still is caught; a disguise wakes before a target walks into the sweep unseen
        assertTrue(Snagwood.TELL_TICKS >= 8);
        assertTrue(Snagwood.CREAK_RANGE < Snagwood.SWEEP_RADIUS);
        assertTrue(Snagwood.CREAK_CONE < Snagwood.SWEEP_ARC / 2.0F);
        assertTrue(Snagwood.WAKE_RANGE > Snagwood.SWEEP_RADIUS);
    }
}
