package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.overworld.Shiverwraith;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Shiverwraith's numbers against its roster row, written out as plain values: HP 20, Atk 3, Arm 0, 0.7 x 1.9; beam
 * 12 t; tether range 12: +3 freeze ticks per tick (frozen in about 2.5 s), heals it 1 a second; breaks with fire, a
 * campfire or lava within 3 of the target; snowy plains, ice spikes and frozen peaks at night, weight 10, one at a time;
 * second look Hoarfrost; drops frost core. The recoil and the wait after a snap are its design. The GameTests check the
 * numbers are applied; this checks they are the right numbers.
 */
class ShiverwraithNumbersTest {
    /** Vanilla's full-freeze mark (Entity.BASE_TICKS_REQUIRED_TO_FREEZE) and its thaw outside powder snow. */
    private static final int FULLY_FROZEN = 140;
    private static final int VANILLA_THAW = 2;

    @Test
    void mechanic() {
        assertEquals(12, Shiverwraith.TELL_TICKS);
        assertEquals(12.0, Shiverwraith.TETHER_RANGE);
        assertEquals(3, Shiverwraith.FREEZE_PER_TICK);
        assertEquals(1.0F, Shiverwraith.HEAL);
        assertEquals(20, Shiverwraith.HEAL_INTERVAL);
        assertEquals(3, Shiverwraith.HEAT_RANGE);
        assertEquals(VANILLA_THAW, Shiverwraith.THAW_PER_TICK);
    }

    @Test
    void frozenInAboutTwoAndAHalfSeconds() {
        int ticks = (FULLY_FROZEN + Shiverwraith.FREEZE_PER_TICK - 1) / Shiverwraith.FREEZE_PER_TICK;
        assertEquals(47, ticks);
        assertTrue(ticks <= 50, "fully frozen within 2.5 s, " + ticks + " ticks");
    }

    @Test
    void waitsLongerThanItRecoils() {
        assertTrue(Shiverwraith.COOLDOWN > Shiverwraith.SNAP_TICKS, "the wait after a snap outlasts the recoil");
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("shiverwraith");
        assertEquals(20.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(1.9F, row.height());
        assertEquals(12, row.tellTicks());
        assertEquals(10, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertFalse(row.anyLight());
        assertEquals("hoarfrost", row.secondLook());
        assertEquals("frost_core", row.material());
    }
}
