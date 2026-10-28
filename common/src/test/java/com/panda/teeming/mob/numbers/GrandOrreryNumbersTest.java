package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.end.GrandOrrery;
import com.panda.teeming.mob.end.GrandOrrery.Lanes;
import com.panda.teeming.mob.end.GrandOrrery.Plan;
import com.panda.teeming.mob.end.GrandOrrery.Step;
import com.panda.teeming.mob.nether.Flamewright.Patterns;
import com.panda.teeming.mob.nether.Flamewright.Patterns.Launch;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Grand Orrery's mechanic numbers against the design, written out as plain values: a 20-tick spin-up; a spiral of 24
 * motes over 2 s, a double ring of two 12s, an aimed burst of three volleys of three; the core open 60 ticks with
 * armour 0 and damage x2; one in 128 blocks; an apex's 100 experience. Then the pattern shapes and their gaps, which
 * need no world, and the kill time against the End's reference player. The GameTests check the numbers are applied;
 * this checks they are the right numbers.
 */
class GrandOrreryNumbersTest {
    /** A player's hitbox is 0.6 wide. */
    private static final double PLAYER_HALF = 0.3;

    @Test
    void designNumbers() {
        assertEquals(20, GrandOrrery.TELL_TICKS);
        assertEquals(24, GrandOrrery.SPIRAL_COUNT);
        assertEquals(40, GrandOrrery.SPIRAL_TICKS);
        assertEquals(360.0, GrandOrrery.SPIRAL_TURN);
        assertEquals(12, GrandOrrery.RING_COUNT);
        assertEquals(15.0, GrandOrrery.RING_OFFSET);
        assertEquals(3, GrandOrrery.BURST_VOLLEYS);
        assertEquals(3, GrandOrrery.BURST_LANES);
        assertEquals(20.0, GrandOrrery.BURST_SPREAD);
        assertEquals(60, GrandOrrery.OPEN_TICKS);
        assertEquals(0, GrandOrrery.OPEN_ARMOR);
        assertEquals(2.0F, GrandOrrery.OPEN_DAMAGE);
        assertEquals(0.25, GrandOrrery.MOTE_SPEED);
        assertEquals(0.15, GrandOrrery.SLOW_RING_SPEED);
        assertEquals(12.0, GrandOrrery.MOTE_RANGE);
        assertEquals(12.0, GrandOrrery.CAST_RANGE);
        assertEquals(10.0F, GrandOrrery.MOTE_PIERCE);
        assertEquals(128, GrandOrrery.CAP_RADIUS);
        assertEquals(100, GrandOrrery.XP);
    }

    @Test
    void houseRules() {
        // a tell of at least 10 ticks before a blow of 1.5x its attack or more (a mote is 6 + 10 through armour)
        assertTrue(GrandOrrery.TELL_TICKS >= 10);
        // a punish window is 40-100 ticks, and a punish multiplier x1.5-x2
        assertTrue(GrandOrrery.OPEN_TICKS >= 40 && GrandOrrery.OPEN_TICKS <= 100);
        assertTrue(GrandOrrery.OPEN_DAMAGE >= 1.5F && GrandOrrery.OPEN_DAMAGE <= 2.0F);
        // every launch leaves inside its pattern
        for (Step step : new Step[] {Step.SPIRAL, Step.DOUBLE_RING, Step.BURST}) {
            for (Launch l : Plan.launches(step)) assertTrue(l.tick() < Plan.length(step), step + " launch at " + l.tick());
        }
        // motes reach past where it keeps station, and it keeps station well inside its casting range
        assertTrue(GrandOrrery.MOTE_RANGE > Math.hypot(GrandOrrery.KEEP_DISTANCE, GrandOrrery.HOVER_UP));
        assertTrue(GrandOrrery.KEEP_DISTANCE < GrandOrrery.CAST_RANGE);
        // it casts only where its motes reach: a player out past a mote's flight is never cast at
        assertTrue(GrandOrrery.CAST_RANGE <= GrandOrrery.MOTE_RANGE);
        // open, it comes down from its station to its target's height within the sinking time
        assertTrue(GrandOrrery.SINK_SPEED * GrandOrrery.SINK_TICKS >= GrandOrrery.HOVER_UP - GrandOrrery.SINK_UP);
        assertTrue(GrandOrrery.SINK_TICKS < GrandOrrery.OPEN_TICKS);
        // the glow stages: dark first (only the gold shows), rest, then lit
        assertEquals(0, GrandOrrery.STAGE_DARK);
        assertTrue(GrandOrrery.STAGE_REST == 1 && GrandOrrery.STAGE_LIT == 2);
    }

    @Test
    void spiralShape() {
        List<Launch> s = Plan.launches(Step.SPIRAL);
        assertEquals(24, s.size());
        assertEquals(0, s.getFirst().tick());
        assertEquals(0.0, s.getFirst().yaw(), 1.0E-9, "the first mote heads straight at the target");
        assertEquals(38, s.getLast().tick(), "the last leaves inside the 2 s");
        for (int i = 1; i < 24; i++) {
            int gap = s.get(i).tick() - s.get(i - 1).tick();
            assertTrue(gap == 1 || gap == 2, "one every 1.67 ticks");
            assertEquals(15.0, s.get(i).yaw() - s.get(i - 1).yaw(), 1.0E-9, "each 15 degrees on: one turn in all");
        }
    }

    @Test
    void doubleRingShape() {
        List<Launch> r = Plan.launches(Step.DOUBLE_RING);
        assertEquals(24, r.size());
        for (int i = 0; i < 12; i++) {
            assertEquals(0, r.get(i).tick());
            assertEquals(0, r.get(12 + i).tick());
            assertEquals(30.0 * i, r.get(i).yaw(), 1.0E-9, "the first ring, 30 degrees apart from the aim");
            assertEquals(15.0 + 30.0 * i, r.get(12 + i).yaw(), 1.0E-9, "the second, turned half a step");
            assertEquals(0.25, Plan.speed(Step.DOUBLE_RING, i));
            assertEquals(0.15, Plan.speed(Step.DOUBLE_RING, 12 + i));
        }
        assertEquals(0.25, Plan.speed(Step.SPIRAL, 23));
        assertEquals(0.25, Plan.speed(Step.BURST, 8));
    }

    @Test
    void burstShape() {
        List<Launch> b = Plan.launches(Step.BURST);
        assertEquals(9, b.size());
        for (int v = 0; v < 3; v++) {
            for (int i = 0; i < 3; i++) {
                Launch l = b.get(v * 3 + i);
                assertEquals(6 * v, l.tick(), "volleys 6 ticks apart");
                assertEquals(-20.0 + 20.0 * i, l.yaw(), 1.0E-9, "down the same three lanes, the middle one at the aim");
            }
        }
    }

    /** Every gap of a pattern, as the yaw midway between two neighbouring shots. */
    private static double[] gaps(List<Launch> plan) {
        double[] yaws = plan.stream().mapToDouble(l -> ((l.yaw() % 360.0) + 360.0) % 360.0).distinct().sorted().toArray();
        double[] mids = new double[yaws.length];
        for (int i = 0; i < yaws.length; i++) {
            double a = yaws[i], b = i + 1 < yaws.length ? yaws[i + 1] : yaws[0] + 360.0;
            mids[i] = (a + b) / 2.0;
        }
        return mids;
    }

    private static boolean clearsAll(List<Launch> plan, double aim, double gapYaw, double distance) {
        double dx = -Math.sin(Math.toRadians(gapYaw)) * distance, dz = Math.cos(Math.toRadians(gapYaw)) * distance;
        for (Launch l : plan) {
            if (!Lanes.clears(aim + l.yaw(), dx, dz, PLAYER_HALF, GrandOrrery.MOTE_RADIUS)) return false;
        }
        return true;
    }

    @Test
    void everyPatternLeavesAGapAPlayerFits() {
        // neighbouring shots of the spiral and the double ring are 15 degrees apart, the burst's lanes 20: from 4 blocks
        // out the open width between two of them, less a mote's catch radius on each side, is wider than a player
        assertTrue(Patterns.ringGap(24, 4.0, GrandOrrery.MOTE_RADIUS) > 0.6, "spiral and double ring: " + Patterns.ringGap(24, 4.0, GrandOrrery.MOTE_RADIUS));
        assertEquals(2 * Math.PI * 4.0 / 24 - 0.2, Patterns.ringGap(24, 4.0, 0.1), 1.0E-9);
        assertTrue(Patterns.ringGap(18, 4.0, GrandOrrery.MOTE_RADIUS) > 0.6, "the burst's lanes, 20 degrees apart");
        // and the box itself: a player standing still in the middle of any gap 5 blocks out clears every shot of the
        // pattern, whichever way the pattern is aimed (the box is square to the world, so a shot can cross it corner to corner)
        for (Step step : new Step[] {Step.SPIRAL, Step.DOUBLE_RING, Step.BURST}) {
            List<Launch> plan = Plan.launches(step);
            for (int aim = 0; aim < 360; aim += 3) {
                for (double gap : gaps(plan)) {
                    if (step == Step.BURST && Math.abs(gap - 180.0) < 90.0) continue;      // the burst's open half is all gap
                    assertTrue(clearsAll(plan, aim, aim + gap, 5.0), step + " aimed at " + aim + ": a player in the gap at " + gap + " is hit");
                }
            }
        }
        // the check would catch a hit: a player standing on a shot's line is struck, and close in a spiral's gap shuts
        assertFalse(clearsAll(Plan.launches(Step.SPIRAL), 0.0, 15.0, 5.0), "on the second mote's line");
        assertFalse(clearsAll(Plan.launches(Step.SPIRAL), 0.0, 7.5, 2.0), "2 blocks out the spiral's gaps are shut");
        assertFalse(clearsAll(Plan.launches(Step.DOUBLE_RING), 0.0, 15.0, 5.0), "the slow ring fills the fast ring's gaps");
    }

    @Test
    void lanesMath() {
        // a shot level along +X (yaw -90) misses a box beside its line and hits one on it
        assertTrue(Lanes.clears(-90.0, 5.0, 1.0, 0.3, 0.1));
        assertFalse(Lanes.clears(-90.0, 5.0, 0.35, 0.3, 0.1));
        assertTrue(Lanes.clears(-90.0, -5.0, 0.0, 0.3, 0.1), "nothing behind the shot is touched");
        // a pattern dips to pass through a point below: 3 down over 4 across is 36.87 degrees
        assertEquals(36.87, Lanes.pitch(3.0, 4.0, 45.0), 0.01);
        assertEquals(45.0, Lanes.pitch(9.0, 1.0, 45.0), 1.0E-9, "never steeper than 45");
        assertEquals(-45.0, Lanes.pitch(-9.0, 1.0, 45.0), 1.0E-9, "nor climbing steeper");
    }

    /** Vanilla's armour formula: armour - damage / (2 + toughness / 4), clamped to [armour / 5, 20], each point -4 %. */
    private static float afterArmor(float damage, float armor, float toughness) {
        float reduction = Math.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
        return damage * (1.0F - reduction / 25.0F);
    }

    @Test
    void killTime() {
        // the End's reference player: a netherite sword with Sharpness V, 11 a hit, a full swing every 12.5 ticks (1.6 a
        // second). It keeps its distance and fills the air while it casts, so the player lands blows only while the core
        // is open: the first 20 ticks it sinks and the player closes in, then 40 ticks within reach
        float hit = 11.0F;
        double swingTicks = 20.0 / 1.6;
        int inReach = 60 - 20;
        int swings = (int) Math.floor(inReach / swingTicks);
        assertEquals(3, swings);
        float perSwing = afterArmor(hit, 0, 0) * 2.0F;
        assertEquals(22.0F, perSwing, 1.0E-4F);
        double openings = 300.0 / (swings * perSwing);
        assertTrue(openings >= 4.0 && openings <= 5.0, "about four or five openings, took " + openings);
        assertEquals(4.55, openings, 0.01);
        // and the same with its own constants, so a changed number fails here too
        int ownSwings = (int) Math.floor((GrandOrrery.OPEN_TICKS - GrandOrrery.SINK_TICKS) / swingTicks);
        double ownOpenings = 300.0 / (ownSwings * afterArmor(hit, GrandOrrery.OPEN_ARMOR, 0) * GrandOrrery.OPEN_DAMAGE);
        assertEquals(openings, ownOpenings, 1.0E-9);
        // a cycle: the rest, the spin-up, a pattern (on average over the turn) and the open core; five of them is well
        // inside an apex's 60 s
        double pattern = (GrandOrrery.SPIRAL_TICKS + GrandOrrery.RING_TICKS + GrandOrrery.BURST_TICKS) / 3.0;
        double cycle = GrandOrrery.REST_TICKS + GrandOrrery.TELL_TICKS + pattern + GrandOrrery.OPEN_TICKS;
        double seconds = Math.ceil(openings) * cycle / 20.0;
        assertTrue(seconds <= 60.0, "dead in 60 s or less, took " + seconds);
        assertEquals(29.2, seconds, 0.1);
        // outside the openings its armour of 10 takes a hit of 11 down to 9.02
        assertEquals(9.02F, afterArmor(hit, 10, 0), 0.01F);
    }

    @Test
    void motesAgainstTheReferencePlayer() {
        // one mote, 6 through armour and 10 past it, on the End's reference player (netherite: armour 20, toughness 12;
        // Protection IV, -64 %): (0.54 + 3.6) = 4.14, so 4.8 motes kill: between the apex's 3 and 5
        float protection = 1.0F - 16.0F / 25.0F;
        float mote = (afterArmor(6.0F, 20, 12) + GrandOrrery.MOTE_PIERCE) * protection;
        assertEquals(4.136F, mote, 0.01F);
        assertTrue(20.0F / mote >= 3.0F && 20.0F / mote <= 5.0F, "three to five motes kill, " + 20.0F / mote);
        // without the piercing share it would take 37 motes, and a pattern stood in would cost nothing
        assertTrue(20.0F / (afterArmor(6.0F, 20, 12) * protection) > 30.0F);
        // on Hard (mob blows x1.5 to players) no mote is nearly a kill: 3.2 motes
        assertTrue(20.0F / ((afterArmor(9.0F, 20, 12) + GrandOrrery.MOTE_PIERCE * 1.5F) * protection) > 1.5F);
    }
}
