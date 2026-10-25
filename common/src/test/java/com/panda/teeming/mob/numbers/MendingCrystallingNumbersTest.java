package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.MendingCrystalling;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Mending Crystalling's numbers against its roster row, written out as plain values: HP 4, Atk 0, Arm 0, 0.5 x
 * 0.8, no tell; its beam heals End mobs within 12 by 2 a second; any hit breaks it; main island (the spawner's rule),
 * weight 0, groups of 1-2; second look Rose Crystalling; drops void crystal. The half-second look for a worse-off mob,
 * the tenth-of-health switch margin, the glow, the 4-block hiding spot and the 8-block shyness are its design. The
 * GameTests check the numbers are applied; this checks they are the right numbers.
 */
class MendingCrystallingNumbersTest {
    @Test
    void beam() {
        assertEquals(12.0, MendingCrystalling.BEAM_RANGE);
        assertEquals(2.0F, MendingCrystalling.HEAL);
        assertEquals(20, MendingCrystalling.HEAL_EVERY);
        // 2 every 20 ticks is 2 a second
        assertEquals(2.0F, MendingCrystalling.HEAL * 20 / MendingCrystalling.HEAL_EVERY);
    }

    @Test
    void choosing() {
        assertEquals(10, MendingCrystalling.SCAN_EVERY);
        assertEquals(0.1F, MendingCrystalling.SWITCH_MARGIN);
        // it looks again more often than it heals, so a worse-off mob never waits a whole heal
        assertTrue(MendingCrystalling.SCAN_EVERY < MendingCrystalling.HEAL_EVERY);
    }

    @Test
    void glow() {
        assertEquals(30, MendingCrystalling.GLOW_TICKS);
        assertEquals(10, MendingCrystalling.GLOW_RENEW);
        // renewed well before it runs out, so the glow never flickers while the beam holds
        assertTrue(MendingCrystalling.GLOW_RENEW < MendingCrystalling.GLOW_TICKS);
    }

    @Test
    void keepingAway() {
        assertEquals(4.0, MendingCrystalling.BEHIND);
        assertEquals(8.0, MendingCrystalling.SHY_RANGE);
        assertEquals(1.2, MendingCrystalling.HOVER);
        // its hiding spot is well inside its beam's reach, and a sprinting player (0.28 a tick) is faster than it
        assertTrue(MendingCrystalling.BEHIND < MendingCrystalling.BEAM_RANGE);
        assertTrue(MendingCrystalling.STAY_NEAR < MendingCrystalling.BEAM_RANGE);
        assertTrue(MendingCrystalling.KEEP_SPEED < 0.28);
    }

    @Test
    void theBeamIsNotGold() {
        // weak-point gold is #ffd36b; none of its lights is it, and the violet and star-white lean blue
        for (int c : new int[] {MendingCrystalling.BEAM_VIOLET, MendingCrystalling.STAR_WHITE, MendingCrystalling.BEAM_ROSE, MendingCrystalling.PEARL}) {
            assertNotEquals(0xFFD36B, c);
            assertTrue((c & 0xFF) >= 0x9C, String.format("%06x keeps plenty of blue, far from gold", c));
        }
        assertTrue((MendingCrystalling.BEAM_VIOLET & 0xFF) > (MendingCrystalling.BEAM_VIOLET >> 16 & 0xFF));
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("mending_crystalling");
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(4.0F, row.health());
        assertEquals(0.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.5F, row.width());
        assertEquals(0.8F, row.height());
        assertEquals(0, row.tellTicks());
        assertEquals(0.0F, row.blow());
        assertEquals(0, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(2, row.groupMax());
        assertFalse(row.apex());
        assertEquals("main_island", row.spawner());
        assertEquals("rose_crystalling", row.secondLook());
        assertEquals("void_crystal", row.material());
    }
}
