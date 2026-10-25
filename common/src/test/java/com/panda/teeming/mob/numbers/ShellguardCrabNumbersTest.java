package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.ShellguardCrab;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Shellguard Crab's numbers against its roster row, written out as plain values. The GameTests check that the
 * numbers are applied; this checks they are the right numbers.
 */
class ShellguardCrabNumbersTest {
    @Test
    void mechanic() {
        // tell 8 t, claws up; guard: no damage from the front 120 degrees; a shovel hit or knockback >= 0.5 flips it:
        // 60 t, takes x2
        assertEquals(8, ShellguardCrab.TELL_TICKS);
        assertEquals(120.0F, ShellguardCrab.GUARD_ARC);
        assertEquals(0.5, ShellguardCrab.FLIP_KNOCKBACK);
        assertEquals(60, ShellguardCrab.FLIP_TICKS);
        assertEquals(2.0F, ShellguardCrab.FLIPPED_DAMAGE);
    }

    @Test
    void stats() {
        // HP 20, Atk 4, Arm 4, 0.9 x 0.6, tell 8
        RosterStats.Entry row = RosterStats.get("shellguard_crab");
        assertEquals(20.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(4.0F, row.armor());
        assertEquals(0.9F, row.width());
        assertEquals(0.6F, row.height());
        assertEquals(8, row.tellTicks());
        assertEquals(row.tellTicks(), ShellguardCrab.TELL_TICKS);
    }
}
