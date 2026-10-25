package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.PhantasmLantern;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Phantasm Lantern's numbers against its roster row, written out as plain values: HP 18, Atk 0, Arm 0, 0.6 x 0.9;
 * a 20 t conjuring, then every 200 t up to 3 illusions of the mobs within 16 blocks, each with 1 health and no damage;
 * the warped forest, weight 10, groups of 1; the Crimson Lantern second look; siphon gel. The GameTests check the
 * numbers are applied; this checks they are the right numbers.
 */
class PhantasmLanternNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, PhantasmLantern.TELL_TICKS);
        assertEquals(200, PhantasmLantern.CONJURE_EVERY);
        assertEquals(3, PhantasmLantern.MAX_ILLUSIONS);
        assertEquals(16.0, PhantasmLantern.COPY_RANGE);
        assertEquals(1.0F, PhantasmLantern.ILLUSION_HEALTH);
        assertEquals(0.0, PhantasmLantern.ILLUSION_DAMAGE);
    }

    @Test
    void houseRules() {
        // a tell lasts at least 8 ticks; the conjuring and the release fit in one cycle, and a fresh lantern waits at
        // least its conjuring before the first
        assertTrue(PhantasmLantern.TELL_TICKS >= 8);
        assertTrue(PhantasmLantern.TELL_TICKS + PhantasmLantern.RELEASE_TICKS < PhantasmLantern.CONJURE_EVERY);
        assertTrue(PhantasmLantern.FIRST_CONJURE >= PhantasmLantern.TELL_TICKS);
        // it conjures only with its target inside the reach it copies from
        assertTrue(PhantasmLantern.CONJURE_RANGE <= PhantasmLantern.COPY_RANGE);
        // illusions step out close by and stray well past the copy reach before they pop
        assertTrue(PhantasmLantern.SPREAD_MIN < PhantasmLantern.SPREAD_MAX);
        assertTrue(PhantasmLantern.LEASH > PhantasmLantern.COPY_RANGE);
        // it keeps away inside the reach it conjures from, so keeping away never stops it conjuring
        assertTrue(PhantasmLantern.KEEP_AWAY < PhantasmLantern.CONJURE_RANGE);
        // a blow to an illusion is soaked by its ward, never by its 1 health: it pops instead of dying
        assertTrue(PhantasmLantern.Illusions.WARD > 1000.0F);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("phantasm_lantern");
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(18.0F, row.health());
        assertEquals(0.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.6F, row.width());
        assertEquals(0.9F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(10, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("crimson_lantern", row.secondLook());
        assertEquals("siphon_gel", row.material());
    }
}
