package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.SoulwardRevenant;
import org.junit.jupiter.api.Test;

/**
 * Soulward Revenant's mechanic numbers against the design, written out as plain values: the gauge fills to 25 with
 * damage taken, twice as fast from 80 %; the kneel lasts 20 t; the nova reaches 5 blocks, does 8 and knocks back 1.2;
 * a wasted nova cracks the breastplate for 60 t with armour 0. The GameTests check the numbers are applied; this checks
 * they are the right numbers.
 */
class SoulwardRevenantNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(25.0F, SoulwardRevenant.GAUGE_MAX);
        assertEquals(0.8F, SoulwardRevenant.BRIGHT_SHARE);
        assertEquals(2.0F, SoulwardRevenant.BRIGHT_FILL);
        assertEquals(20, SoulwardRevenant.TELL_TICKS);
        assertEquals(5.0, SoulwardRevenant.NOVA_RADIUS);
        assertEquals(8.0F, SoulwardRevenant.NOVA_DAMAGE);
        assertEquals(1.2, SoulwardRevenant.KNOCKBACK);
        assertEquals(60, SoulwardRevenant.SPENT_TICKS);
        assertEquals(0, SoulwardRevenant.SPENT_ARMOR);
    }

    @Test
    void houseRules() {
        // a tell lasts at least 8 ticks, and at least 10 before a blow of 1.5x attack or more (the nova is 8 against 6)
        assertTrue(SoulwardRevenant.TELL_TICKS >= 10);
        // knockback 1.2 on the attack-knockback scale is a throw of 0.6 a tick, lifted 0.4 like vanilla's knockback
        assertEquals(0.6, SoulwardRevenant.PUSH_SPEED, 1.0E-9);
        assertEquals(0.4, SoulwardRevenant.PUSH_LIFT, 1.0E-9);
        // a punish window is 40-100 ticks
        assertTrue(SoulwardRevenant.SPENT_TICKS >= 40 && SoulwardRevenant.SPENT_TICKS <= 100);
        // the glow stages: dark when empty, soul-blue, cyan from 10, white from 20 (80 % of 25)
        assertEquals(10.0F, SoulwardRevenant.MID_AT);
        assertTrue(SoulwardRevenant.MID_AT < SoulwardRevenant.GAUGE_MAX * SoulwardRevenant.BRIGHT_SHARE);
    }

    /** Vanilla's armour formula: armour - damage / (2 + toughness / 4), clamped to [armour / 5, 20], each point -4 %. */
    private static float afterArmor(float damage, float armor, float toughness) {
        float reduction = Math.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
        return damage * (1.0F - reduction / 25.0F);
    }

    @Test
    void novaAgainstTheReferencePlayer() {
        // the Nether's reference player: a diamond sword with Sharpness II (8.5 a hit), a diamond set (armour 20,
        // toughness 8), 20 health. Through its armour 6 a hit lands 7.9: three hits bring it to bright and the
        // fourth, doubled, fills it, so a player who never stops hitting meets a nova about every four blows.
        float hit = afterArmor(8.5F, 6, 0);
        assertEquals(7.905F, hit, 0.01F);
        float gauge = 0;
        int hits = 0;
        while (gauge < SoulwardRevenant.GAUGE_MAX) {
            gauge += hit * (gauge >= SoulwardRevenant.GAUGE_MAX * SoulwardRevenant.BRIGHT_SHARE ? SoulwardRevenant.BRIGHT_FILL : 1.0F);
            hits++;
        }
        assertEquals(4, hits);
        // the nova costs the reference player 2.24 of 20 on Normal: a lesson, not a kill
        float nova = afterArmor(SoulwardRevenant.NOVA_DAMAGE, 20, 8);
        assertEquals(2.24F, nova, 0.01F);
        assertTrue(20.0F / nova >= 5.0F);
    }
}
