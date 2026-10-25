package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.BasaltBehemoth;
import org.junit.jupiter.api.Test;

/**
 * Basalt Behemoth's mechanic numbers against the design, written out as plain values: the rear lasts 20 t; the slam
 * reaches 4 blocks and does 14; the core stays open 60 t with armour 0 and every hit double; an apex's 100 experience.
 * The GameTests check the numbers are applied; this checks they are the right numbers, and that they hold the apex
 * balance against the Nether's reference player.
 */
class BasaltBehemothNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(20, BasaltBehemoth.TELL_TICKS);
        assertEquals(4.0, BasaltBehemoth.SLAM_RADIUS);
        assertEquals(14.0F, BasaltBehemoth.SLAM_DAMAGE);
        assertEquals(60, BasaltBehemoth.EXPOSED_TICKS);
        assertEquals(0, BasaltBehemoth.EXPOSED_ARMOR);
        assertEquals(2.0F, BasaltBehemoth.EXPOSED_DAMAGE);
        assertEquals(100, BasaltBehemoth.XP);
    }

    @Test
    void houseRules() {
        // a tell before a blow of 1.5x attack or more lasts at least 10 ticks (the slam is 1x, the rule holds anyway)
        assertTrue(BasaltBehemoth.TELL_TICKS >= 10);
        // a punish window is 40-100 ticks and multiplies by 1.5-2
        assertTrue(BasaltBehemoth.EXPOSED_TICKS >= 40 && BasaltBehemoth.EXPOSED_TICKS <= 100);
        assertTrue(BasaltBehemoth.EXPOSED_DAMAGE >= 1.5F && BasaltBehemoth.EXPOSED_DAMAGE <= 2.0F);
        // it rears only at a target well inside the reach, so a target that stands still is hit
        assertTrue(BasaltBehemoth.REAR_RANGE < BasaltBehemoth.SLAM_RADIUS);
        // a jump clears the ground check: a jump's first tick lifts 0.42, far over the gap that counts as standing
        assertTrue(BasaltBehemoth.GROUND_GAP < 0.42);
        // the throw carries a body out past the reach: about 4 blocks through the air from where it stood
        assertTrue(BasaltBehemoth.SLAM_PUSH > 0.5 && BasaltBehemoth.SLAM_LIFT > 0.2);
        // the three glow stages are distinct and the open one is the first
        assertEquals(0, BasaltBehemoth.STAGE_OPEN);
        assertTrue(BasaltBehemoth.STAGE_SMOULDER > BasaltBehemoth.STAGE_OPEN && BasaltBehemoth.STAGE_FLARE > BasaltBehemoth.STAGE_SMOULDER);
    }

    /** Vanilla's armour formula: armour - damage / (2 + toughness / 4), clamped to [armour / 5, 20], each point -4 %. */
    private static float afterArmor(float damage, float armor, float toughness) {
        float reduction = Math.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
        return damage * (1.0F - reduction / 25.0F);
    }

    @Test
    void apexBalance() {
        // the Nether's reference player: a diamond sword with Sharpness II (8.5 a hit, 1.6 swings a second), a diamond
        // set (armour 20, toughness 8), 20 health
        float hit = 8.5F, swings = 1.6F;
        // a cycle: the rear, the open core, the wait; the player hits the core while it is open and the plates otherwise
        float cycle = (BasaltBehemoth.TELL_TICKS + BasaltBehemoth.EXPOSED_TICKS + BasaltBehemoth.REAR_COOLDOWN) / 20.0F;
        float open = BasaltBehemoth.EXPOSED_TICKS / 20.0F;
        float perCycle = swings * (open * afterArmor(hit * BasaltBehemoth.EXPOSED_DAMAGE, BasaltBehemoth.EXPOSED_ARMOR, 0)
                + (cycle - open) * afterArmor(hit, 16, 0));
        float seconds = 200.0F / perCycle * cycle;
        assertTrue(seconds <= 60.0F, "using the counter it dies in 60 s or less, took " + seconds);
        assertEquals(12.7F, seconds, 0.1F);
        // the reference player survives at least three slams (4.76 each on Normal: 4.2 slams)
        float slam = afterArmor(BasaltBehemoth.SLAM_DAMAGE, 20, 8);
        assertEquals(4.76F, slam, 0.01F);
        assertTrue(20.0F / slam >= 3.0F, "survives three slams, " + 20.0F / slam);
    }
}
