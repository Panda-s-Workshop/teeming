package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.ShardlingColossus;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Shardling Colossus's numbers against its stat row, written out as plain values: HP 50, Atk 9, Arm 8, 1.4 x 2.6;
 * a 16 t crack before its slam; a hit of 8 or more, or dropping below 50 %, splits it into 2 halves at scale 0.7 with
 * half its health each, which rejoin after 100 t if both live; the End midlands, weight 3, alone; the Amethyst
 * Colossus second look; void crystal. The GameTests check the numbers are applied; this checks they are the right
 * numbers.
 */
class ShardlingColossusNumbersTest {
    @Test
    void mechanic() {
        assertEquals(16, ShardlingColossus.TELL_TICKS);
        assertEquals(8.0F, ShardlingColossus.SPLIT_HIT);
        assertEquals(0.5F, ShardlingColossus.SPLIT_BELOW);
        assertEquals(0.7F, ShardlingColossus.HALF_SCALE);
        assertEquals(0.5F, ShardlingColossus.HALF_SHARE);
        assertEquals(100, ShardlingColossus.REJOIN_AFTER);
    }

    @Test
    void ownCalls() {
        // the numbers its stat row leaves open: the slam's shape and pace, the halves' blow and legs, the walk to rejoin
        assertEquals(3.0, ShardlingColossus.SLAM_REACH);
        assertEquals(75.0, ShardlingColossus.SLAM_ARC);
        assertEquals(8, ShardlingColossus.AIM_TICKS);
        assertEquals(12, ShardlingColossus.SLAM_TICKS);
        assertEquals(40, ShardlingColossus.SLAM_EVERY);
        assertEquals(6.0, ShardlingColossus.HALF_ATTACK);
        assertEquals(0.26, ShardlingColossus.HALF_SPEED);
        assertEquals(0.2, ShardlingColossus.SPEED);
        assertEquals(10, ShardlingColossus.SPLIT_TICKS);
        assertEquals(0.5, ShardlingColossus.SPLIT_APART);
        assertEquals(1.2, ShardlingColossus.MEET);
        assertEquals(200, ShardlingColossus.REGROUP_MAX);
        assertEquals(20, ShardlingColossus.FUSE_TICKS);
        assertEquals(0.6, ShardlingColossus.KNOCKBACK_RESISTANCE);
        assertEquals(14, ShardlingColossus.XP);
        assertEquals(7, ShardlingColossus.HALF_XP);
    }

    @Test
    void houseRules() {
        // a tell lasts at least 8 ticks, and the aim locks with time left to step aside
        assertTrue(ShardlingColossus.TELL_TICKS >= 8);
        assertTrue(ShardlingColossus.AIM_TICKS < ShardlingColossus.TELL_TICKS);
        assertTrue(ShardlingColossus.TELL_TICKS - ShardlingColossus.AIM_TICKS >= 8);
        // a slam runs its course before the next tell may start
        assertTrue(ShardlingColossus.TELL_TICKS + ShardlingColossus.SLAM_TICKS < ShardlingColossus.SLAM_EVERY);
        // the halves stagger, then have time to fight before they set off to rejoin
        assertTrue(ShardlingColossus.SPLIT_TICKS < ShardlingColossus.REJOIN_AFTER);
        // a half hits for less than the colossus's 9, and two halves together carry its health, no more
        assertTrue(ShardlingColossus.HALF_ATTACK < 9.0);
        assertEquals(1.0F, 2 * ShardlingColossus.HALF_SHARE);
        // the halves land clear of each other (two half-widths of 1.4 x 0.7) but within reach of rejoining
        assertTrue(2 * ShardlingColossus.SPLIT_APART >= 1.4 * 0.7);
        assertTrue(2 * ShardlingColossus.SPLIT_APART < ShardlingColossus.MEET);
        // the halves give the colossus's experience between them
        assertEquals(ShardlingColossus.XP, 2 * ShardlingColossus.HALF_XP);
        // the form bits stay clear of the visual stage, which uses the top bits of the flags
        assertTrue(ShardlingColossus.FLAG_HALF < 24 && ShardlingColossus.FLAG_RIGHT < 24 && ShardlingColossus.FLAG_FUSED < 24);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("shardling_colossus");
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
        assertEquals(RosterStats.Dim.END, row.dim());
        assertEquals(50.0F, row.health());
        assertEquals(9.0F, row.attack());
        assertEquals(8.0F, row.armor());
        assertEquals(1.4F, row.width());
        assertEquals(2.6F, row.height());
        assertEquals(16, row.tellTicks());
        assertEquals(3, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("amethyst_colossus", row.secondLook());
        assertEquals("void_crystal", row.material());
    }
}
