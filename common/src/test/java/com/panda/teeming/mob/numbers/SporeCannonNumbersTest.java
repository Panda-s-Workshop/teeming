package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.SporeCannon;
import org.junit.jupiter.api.Test;

/**
 * Spore Cannon's mechanic numbers against the design, written out as plain values: tell 20 t (the bulb); 3 homing
 * spores at 0.3 blocks a tick, 4 damage, 1 health each, a volley every 80 t; a hit on the lit bulb stuns it 80 t, taking
 * x2. The GameTests check the numbers are applied; this checks they are the right numbers.
 */
class SporeCannonNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(20, SporeCannon.TELL_TICKS);
        assertEquals(3, SporeCannon.SPORES);
        assertEquals(0.3, SporeCannon.SPORE_SPEED);
        assertEquals(4.0F, SporeCannon.SPORE_DAMAGE);
        assertEquals(1.0F, SporeCannon.SPORE_HEALTH);
        assertEquals(80, SporeCannon.VOLLEY_EVERY);
        assertEquals(80, SporeCannon.STUN_TICKS);
        assertEquals(2.0F, SporeCannon.STUN_DAMAGE);
    }

    @Test
    void houseRules() {
        // a tell of at least 8 ticks (a spore is 1 x Atk); the volley's cycle has room for its tell and every spore
        assertTrue(SporeCannon.TELL_TICKS >= 8);
        assertTrue(SporeCannon.FIRE_TICKS >= (SporeCannon.SPORES - 1) * SporeCannon.SPORE_GAP + 1);
        assertTrue(SporeCannon.VOLLEY_EVERY > SporeCannon.TELL_TICKS + SporeCannon.FIRE_TICKS);
        // the bulb is the top of its 1.6-block body, and spores leave from its top
        assertTrue(SporeCannon.BULB_BASE > 0.8 && SporeCannon.BULB_BASE < 1.6);
        assertTrue(SporeCannon.VENT_HEIGHT >= 1.6);
        // any swing or arrow pops a spore: a bare fist does 1, a projectile through one does more
        assertTrue(SporeCannon.DART_DAMAGE >= SporeCannon.SPORE_HEALTH);
    }
}
