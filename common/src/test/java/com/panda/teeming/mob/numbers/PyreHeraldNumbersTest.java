package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.PyreHerald;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Pyre Herald's numbers against its roster row, written out as plain values: HP 22, Atk 3, Arm 0, 0.7 x 1.2; a
 * 20 t raise, then every 200 t 8 absorption to blazes, wither skeletons and Teeming Nether mobs within 10 blocks; a
 * hit in the raise drops the brazier for 60 t; Nether fortresses (the spawner's fortress rule), groups of 1; the Soul
 * Herald second look; ember wing. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class PyreHeraldNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, PyreHerald.TELL_TICKS);
        assertEquals(200, PyreHerald.RALLY_EVERY);
        assertEquals(8.0F, PyreHerald.SHELL);
        assertEquals(10.0, PyreHerald.RALLY_RADIUS);
        assertEquals(60, PyreHerald.DROP_TICKS);
    }

    @Test
    void houseRules() {
        // Absorption gives 4 per level: the amplifier must give exactly the shell
        assertEquals(PyreHerald.SHELL, 4.0F * (PyreHerald.SHELL_LEVEL + 1));
        // a tell lasts at least 8 ticks; the raise and the flare fit in one cycle, and a shell outlasts the wait to the
        // next rally, which renews it
        assertTrue(PyreHerald.TELL_TICKS >= 8);
        assertTrue(PyreHerald.TELL_TICKS + PyreHerald.RALLY_TICKS < PyreHerald.RALLY_EVERY);
        assertTrue(PyreHerald.SHELL_TICKS > PyreHerald.RALLY_EVERY);
        // the dropped brazier is back before the next rally is due
        assertTrue(PyreHerald.DROP_TICKS < PyreHerald.RALLY_EVERY);
        // it hides inside its own reach, and backs off before it is cornered
        assertTrue(PyreHerald.BEHIND < PyreHerald.RALLY_RADIUS);
        assertTrue(PyreHerald.CORNERED_RANGE < PyreHerald.FLEE_RANGE);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("pyre_herald");
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(22.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.7F, row.width());
        assertEquals(1.2F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(0, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("fortress", row.spawner());
        assertEquals("soul_herald", row.secondLook());
        assertEquals("ember_wing", row.material());
    }
}
