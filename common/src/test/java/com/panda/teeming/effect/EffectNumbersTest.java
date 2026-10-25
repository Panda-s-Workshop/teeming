package com.panda.teeming.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The status effects' numbers, written out as plain values: Sunder takes 4 armour a stack, up to 3 stacks (12);
 * Silenced lasts 80 t; Void Rot lasts 160 t and lets half of any healing through; Heavy lasts 120 t, gravity x1.5 and
 * jump x0; Swallowed outlasts its holder's last claim by 20 t. The GameTests check the numbers are applied; this checks
 * they are the right numbers.
 */
class EffectNumbersTest {
    @Test
    void numbers() {
        assertEquals(4.0, EffectNumbers.SUNDER_ARMOR);
        assertEquals(3, EffectNumbers.SUNDER_MAX_STACKS);
        assertEquals(80, EffectNumbers.SILENCED_TICKS);
        assertEquals(160, EffectNumbers.VOID_ROT_TICKS);
        assertEquals(0.5F, EffectNumbers.VOID_ROT_HEALING);
        assertEquals(120, EffectNumbers.HEAVY_TICKS);
        assertEquals(1.5, EffectNumbers.HEAVY_GRAVITY);
        assertEquals(0.0, EffectNumbers.HEAVY_JUMP);
        assertEquals(20, EffectNumbers.SWALLOWED_GRACE);
    }

    @Test
    void fitTheirRules() {
        for (int ticks : new int[] {EffectNumbers.SILENCED_TICKS, EffectNumbers.VOID_ROT_TICKS, EffectNumbers.HEAVY_TICKS}) {
            assertTrue(ticks > 0 && ticks <= 200, "no effect lasts over 200 ticks: " + ticks);
        }
        assertEquals(12.0, EffectNumbers.SUNDER_ARMOR * EffectNumbers.SUNDER_MAX_STACKS, "full Sunder takes 12 armour, not all of a diamond set's 20");
        assertTrue(EffectNumbers.VOID_ROT_HEALING > 0.0F && EffectNumbers.VOID_ROT_HEALING < 1.0F, "Void Rot slows healing but never stops it");
        assertTrue(EffectNumbers.HEAVY_GRAVITY > 1.0 && EffectNumbers.HEAVY_GRAVITY * 0.08 <= 1.0, "Heavy's gravity stays inside the attribute's range");
        assertTrue(EffectNumbers.SWALLOWED_GRACE >= 2, "a holder that checks every tick never lets Swallowed flicker off");
    }
}
