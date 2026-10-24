package com.panda.teeming.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.end.Levimoth;
import com.panda.teeming.mob.nether.SlagheartGolem;
import com.panda.teeming.mob.overworld.Thornhorn;
import org.junit.jupiter.api.Test;

/**
 * Each mob's mechanic numbers against the roster sheet, written out as plain values. The GameTests check that the
 * numbers are applied; this checks they are the right numbers, so a changed constant fails here until the sheet and
 * this test change with it.
 */
class MechanicNumbersTest {
    @Test
    void thornhorn() {
        // charge 0.45 b/t, <= 14 blocks, hit 1.5 x Atk, knockback 1.6; wall -> stunned 60 t, takes x1.5; cooldown 100 t; tell 20
        assertEquals(20, Thornhorn.TELL_TICKS);
        assertEquals(0.45, Thornhorn.CHARGE_SPEED);
        assertEquals(14.0, Thornhorn.MAX_RANGE);
        assertEquals(1.5F, Thornhorn.CHARGE_HIT);
        assertEquals(1.6, Thornhorn.KNOCKBACK);
        assertEquals(60, Thornhorn.STUN_TICKS);
        assertEquals(1.5F, Thornhorn.STUN_DAMAGE);
        assertEquals(100, Thornhorn.COOLDOWN);
    }

    @Test
    void slagheartGolem() {
        // heat 0-12, +1 every 60 t; erupts at 12 (radius 5, 10 dmg) -> heat 6; snowball -2, water bottle -4,
        // powder snow -12; heat 0 -> takes x1.5 for 60 t; tell 20
        assertEquals(12, SlagheartGolem.MAX_HEAT);
        assertEquals(60, SlagheartGolem.HEAT_EVERY);
        assertEquals(6, SlagheartGolem.AFTER_ERUPTION);
        assertEquals(20, SlagheartGolem.TELL_TICKS);
        assertEquals(5.0, SlagheartGolem.ERUPT_RADIUS);
        assertEquals(10.0F, SlagheartGolem.ERUPT_DAMAGE);
        assertEquals(2, SlagheartGolem.SNOWBALL);
        assertEquals(4, SlagheartGolem.SPLASH);
        assertEquals(12, SlagheartGolem.POWDER_SNOW);
        assertEquals(60, SlagheartGolem.QUENCH_TICKS);
        assertEquals(1.5F, SlagheartGolem.QUENCHED_DAMAGE);
    }

    @Test
    void levimoth() {
        // dust cloud radius 3 for 100 t: Levitation II 60 t to everything inside; tell 16
        assertEquals(16, Levimoth.TELL_TICKS);
        assertEquals(3.0F, Levimoth.CLOUD_RADIUS);
        assertEquals(100, Levimoth.CLOUD_TICKS);
        assertEquals(60, Levimoth.LEVITATION_TICKS);
        assertEquals(1, Levimoth.LEVITATION_LEVEL);
    }
}
