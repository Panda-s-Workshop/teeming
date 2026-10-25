package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panda.teeming.mob.nether.Flamewright;
import com.panda.teeming.mob.nether.Flamewright.Patterns;
import com.panda.teeming.mob.nether.Flamewright.Patterns.Launch;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Flamewright's mechanic numbers against the design, written out as plain values: a 20-tick wind-up; a spiral of 12
 * fireballs over 1.5 s, a ring of 16, a cross of 4 arms of 3; fireballs at 0.25 blocks a tick; a hit in the wind-up
 * staggers it for 40 ticks. Then the pattern shapes themselves, which need no world. The GameTests check the numbers
 * are applied; this checks they are the right numbers.
 */
class FlamewrightNumbersTest {
    @Test
    void designNumbers() {
        assertEquals(20, Flamewright.TELL_TICKS);
        assertEquals(12, Flamewright.SPIRAL_COUNT);
        assertEquals(30, Flamewright.SPIRAL_TICKS);
        assertEquals(16, Flamewright.RING_COUNT);
        assertEquals(4, Flamewright.CROSS_ARMS);
        assertEquals(3, Flamewright.CROSS_PER_ARM);
        assertEquals(0.25, Flamewright.FIREBALL_SPEED);
        assertEquals(40, Flamewright.STAGGER_TICKS);
    }

    @Test
    void houseRules() {
        // a tell lasts at least 8 ticks, and the wind-up, the longest pattern and a stagger all fit in one cycle
        assertTrue(Flamewright.TELL_TICKS >= 8);
        assertTrue(Flamewright.TELL_TICKS + Math.max(Flamewright.SPIRAL_TICKS, Flamewright.STAGGER_TICKS) < Flamewright.CAST_EVERY);
        // every cross arm's last fireball leaves inside the cast
        assertTrue((Flamewright.CROSS_PER_ARM - 1) * Flamewright.CROSS_GAP < Flamewright.CROSS_TICKS);
        // a fireball reaches past the range it is cast at
        assertTrue(Flamewright.FIREBALL_RANGE > Flamewright.CAST_RANGE);
        assertTrue(Flamewright.KEEP_RANGE < Flamewright.CAST_RANGE);
        // a ring's fireballs leave a gap wider than a player (0.6) from 4 blocks out
        assertTrue(Patterns.ringGap(Flamewright.RING_COUNT, 4.0, Flamewright.FIREBALL_RADIUS) > 0.6);
    }

    @Test
    void spiralShape() {
        List<Launch> s = Patterns.spiral(12, 30, 360.0);
        assertEquals(12, s.size());
        assertEquals(0, s.getFirst().tick());
        assertTrue(s.getLast().tick() < 30, "every fireball leaves inside the 30 ticks");
        assertEquals(27, s.getLast().tick());
        for (int i = 1; i < 12; i++) {
            int gap = s.get(i).tick() - s.get(i - 1).tick();
            assertTrue(gap == 2 || gap == 3, "one every 2.5 ticks");
            assertEquals(30.0, s.get(i).yaw() - s.get(i - 1).yaw(), 1.0E-9);
        }
    }

    @Test
    void ringShape() {
        List<Launch> r = Patterns.ring(16);
        assertEquals(16, r.size());
        for (int i = 0; i < 16; i++) {
            assertEquals(0, r.get(i).tick());
            assertEquals(22.5 * i, r.get(i).yaw(), 1.0E-9);
        }
    }

    @Test
    void crossShape() {
        List<Launch> c = Patterns.cross(4, 3, 4);
        assertEquals(12, c.size());
        for (int k = 0; k < 3; k++) {
            for (int a = 0; a < 4; a++) {
                Launch l = c.get(k * 4 + a);
                assertEquals(4 * k, l.tick());
                assertEquals(90.0 * a, l.yaw(), 1.0E-9);
            }
        }
    }

    @Test
    void ringGap() {
        // 16 round a 4-block circle: 25.1 / 16 = 1.571 between centres, less a 0.25 catch radius on each side
        assertEquals(2 * Math.PI * 4.0 / 16 - 0.5, Patterns.ringGap(16, 4.0, 0.25), 1.0E-9);
        assertTrue(Patterns.ringGap(16, 3.0, 0.25) < 0.7, "close in, the ring is all but shut");
    }
}
