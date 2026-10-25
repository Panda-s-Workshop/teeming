package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.WeeplingBroodmother;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Weepling Broodmother's numbers against her stat row, written out as plain values: HP 36, Atk 3, Arm 2,
 * 1.4 x 1.2; a 20 t lay tell, then every 200 t 2 sacs of 1 health that hatch at 60 t into weeplings with 6 health and 2
 * attack, at most 6 alive; the crimson forest, weight 6, groups of 1; the Bloated Brood second look; vein sac. The
 * GameTests check the numbers are applied; this checks they are the right numbers.
 */
class WeeplingBroodmotherNumbersTest {
    @Test
    void mechanic() {
        assertEquals(20, WeeplingBroodmother.TELL_TICKS);
        assertEquals(200, WeeplingBroodmother.LAY_EVERY);
        assertEquals(2, WeeplingBroodmother.CLUTCH);
        assertEquals(1.0F, WeeplingBroodmother.SAC_HEALTH);
        assertEquals(60, WeeplingBroodmother.HATCH_TICKS);
        assertEquals(6.0F, WeeplingBroodmother.WEEPLING_HEALTH);
        assertEquals(2.0, WeeplingBroodmother.WEEPLING_ATTACK);
        assertEquals(6, WeeplingBroodmother.MAX_BROOD);
    }

    @Test
    void ownCalls() {
        // the numbers her stat row leaves open: the payoff for a popped clutch, the orphans' clock, range and sizes
        assertEquals(200, WeeplingBroodmother.GRIEF_TICKS);
        assertEquals(100, WeeplingBroodmother.ORPHAN_TICKS);
        assertEquals(20, WeeplingBroodmother.ORPHAN_GRACE);
        assertEquals(16.0, WeeplingBroodmother.LAY_RANGE);
        assertEquals(0.4F, WeeplingBroodmother.BROOD_SCALE);
        assertEquals(1, WeeplingBroodmother.WEEPLING_XP);
        assertEquals(10, WeeplingBroodmother.MOTHER_XP);
        assertEquals(0.33, WeeplingBroodmother.WEEPLING_SPEED);
    }

    @Test
    void houseRules() {
        // a tell lasts at least 8 ticks; the squat, the lay and the hatching all fit in one cycle, so a clutch hatches
        // before the next is laid; a fresh mother waits at least her squat before the first lay
        assertTrue(WeeplingBroodmother.TELL_TICKS >= 8);
        assertTrue(WeeplingBroodmother.TELL_TICKS + WeeplingBroodmother.LAY_TICKS < WeeplingBroodmother.LAY_EVERY);
        assertTrue(WeeplingBroodmother.HATCH_TICKS < WeeplingBroodmother.LAY_EVERY);
        assertTrue(WeeplingBroodmother.FIRST_LAY >= WeeplingBroodmother.TELL_TICKS);
        // a full brood is whole clutches
        assertEquals(0, WeeplingBroodmother.MAX_BROOD % WeeplingBroodmother.CLUTCH);
        // sacs drop clear of her own hitbox (half of 1.4 plus half of a sac's 0.56)
        assertTrue(WeeplingBroodmother.SPREAD_MIN >= 0.7 + 0.28);
        assertTrue(WeeplingBroodmother.SPREAD_MIN < WeeplingBroodmother.SPREAD_MAX);
        // a lost sac bursts before it could hatch; a weepling outlives her a while, never long
        assertTrue(WeeplingBroodmother.ORPHAN_GRACE < WeeplingBroodmother.HATCH_TICKS);
        assertTrue(WeeplingBroodmother.ORPHAN_TICKS <= WeeplingBroodmother.LAY_EVERY);
        // the brood's form bits stay clear of the visual stage, which uses the top bits of the flags
        assertTrue(WeeplingBroodmother.FLAG_SAC < 24 && WeeplingBroodmother.FLAG_WEEPLING < 24);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("weepling_broodmother");
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.NETHER, row.dim());
        assertEquals(36.0F, row.health());
        assertEquals(3.0F, row.attack());
        assertEquals(2.0F, row.armor());
        assertEquals(1.4F, row.width());
        assertEquals(1.2F, row.height());
        assertEquals(20, row.tellTicks());
        assertEquals(6, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("bloated_brood", row.secondLook());
        assertEquals("vein_sac", row.material());
    }
}
