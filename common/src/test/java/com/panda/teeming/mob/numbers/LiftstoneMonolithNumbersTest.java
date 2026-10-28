package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.LiftstoneMonolith;
import org.junit.jupiter.api.Test;

/**
 * Liftstone Monolith's mechanic numbers against the design, written out as plain values: the rise lasts 20 t; the pull
 * reaches 6 blocks and lifts 6 blocks over 20 t; the slam does 27; a block within 3 over the head stops the lift; it
 * pulls every 160 t; one in 128 blocks; an apex's 100 experience. The GameTests check the numbers are applied; this
 * checks they are the right numbers, and that they hold the apex balance against the End's reference player.
 */
class LiftstoneMonolithNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(20, LiftstoneMonolith.TELL_TICKS);
        assertEquals(6.0, LiftstoneMonolith.LIFT_RADIUS);
        assertEquals(6.0, LiftstoneMonolith.LIFT_HEIGHT);
        assertEquals(20, LiftstoneMonolith.LIFT_TICKS);
        assertEquals(27.0F, LiftstoneMonolith.SLAM_DAMAGE);
        assertEquals(3.0, LiftstoneMonolith.ROOF_REACH);
        assertEquals(160, LiftstoneMonolith.CYCLE_TICKS);
        assertEquals(128, LiftstoneMonolith.CAP_RADIUS);
        assertEquals(100, LiftstoneMonolith.XP);
    }

    @Test
    void houseRules() {
        // a tell before a blow of 1.5x attack or more lasts at least 10 ticks (the slam is 1x its attack of 12; it holds anyway)
        assertTrue(LiftstoneMonolith.TELL_TICKS >= 10);
        // a punish window is 40-100 ticks
        assertTrue(LiftstoneMonolith.CRACKED_TICKS >= 40 && LiftstoneMonolith.CRACKED_TICKS <= 100);
        assertEquals(0, LiftstoneMonolith.CRACKED_ARMOR);
        // the slam drives a victim down the whole lift within the slam: 4 ticks of 1.5
        assertEquals(LiftstoneMonolith.LIFT_HEIGHT, LiftstoneMonolith.SLAM_SPEED * LiftstoneMonolith.SLAM_TICKS, 1.0E-9);
        // the whole pull and the split capstone fit inside one cycle, so the next rise is never late
        int pull = LiftstoneMonolith.TELL_TICKS + LiftstoneMonolith.LIFT_TICKS + LiftstoneMonolith.HOLD_TICKS + LiftstoneMonolith.SLAM_TICKS;
        assertTrue(pull + LiftstoneMonolith.CRACKED_TICKS < LiftstoneMonolith.CYCLE_TICKS, "pull and split take " + (pull + LiftstoneMonolith.CRACKED_TICKS));
        // a roof that stops the lift is lower than the lift: a block met higher up stops a victim, it doesn't spare it
        assertTrue(LiftstoneMonolith.ROOF_REACH < LiftstoneMonolith.LIFT_HEIGHT);
        // it drifts to well inside its reach before it pulls
        assertTrue(LiftstoneMonolith.APPROACH < LiftstoneMonolith.LIFT_RADIUS);
        // no status over 200 ticks: the cushion when it lets go mid-air
        assertTrue(LiftstoneMonolith.CUSHION_TICKS <= 200);
        // the glow stages: dark first (only the gold shows), rest, then the tell up to the brightest
        assertEquals(0, LiftstoneMonolith.STAGE_DARK);
        assertTrue(LiftstoneMonolith.STAGE_REST == 1 && LiftstoneMonolith.STAGE_FULL == 4);
    }

    /** What Protection IV (EPF 16) leaves of a blow. */
    private static final float PROTECTION_IV = 1.0F - 16.0F / 25.0F;

    /** Vanilla's armour formula: armour - damage / (2 + toughness / 4), clamped to [armour / 5, 20], each point -4 %. */
    private static float afterArmor(float damage, float armor, float toughness) {
        float reduction = Math.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
        return damage * (1.0F - reduction / 25.0F);
    }

    @Test
    void apexBalance() {
        // the End's reference player: a netherite sword with Sharpness V (11 a hit, 1.6 swings a second), a netherite set
        // (armour 20, toughness 12) with Protection IV (-64 %), 20 health
        float hit = 11.0F, swings = 1.6F;
        // a cycle with a roof: the pull, the split capstone (armour 0), and the rest of the wait at armour 12
        float cycle = LiftstoneMonolith.CYCLE_TICKS / 20.0F;
        float split = LiftstoneMonolith.CRACKED_TICKS / 20.0F;
        float pull = (LiftstoneMonolith.TELL_TICKS + LiftstoneMonolith.LIFT_TICKS + LiftstoneMonolith.HOLD_TICKS + LiftstoneMonolith.SLAM_TICKS) / 20.0F;
        // under its roof the player can't reach it during the pull; it strikes the heart while split and the stone otherwise
        float perCycle = swings * (split * afterArmor(hit, LiftstoneMonolith.CRACKED_ARMOR, 0) + (cycle - split - pull) * afterArmor(hit, 12, 0));
        float seconds = 250.0F / perCycle * cycle;
        assertTrue(seconds <= 60.0F, "using the counter it dies in 60 s or less, took " + seconds);
        assertEquals(22.9F, seconds, 0.1F);
        // the reference player survives at least three slams and at most five (4.04 each on Normal: 4.95 slams), so a
        // lift left unbroken is a real risk and never a kill
        float slam = afterArmor(LiftstoneMonolith.SLAM_DAMAGE, 20, 12) * PROTECTION_IV;
        assertEquals(4.04F, slam, 0.01F);
        assertTrue(20.0F / slam >= 3.0F && 20.0F / slam <= 5.0F, "survives three to five slams, " + 20.0F / slam);
        // on Hard, where mob blows to players are 1.5x (7.64 each), no slam is nearly a kill: 2.6 slams
        assertTrue(20.0F / (afterArmor(LiftstoneMonolith.SLAM_DAMAGE * 1.5F, 20, 12) * PROTECTION_IV) > 1.5F);
    }
}
