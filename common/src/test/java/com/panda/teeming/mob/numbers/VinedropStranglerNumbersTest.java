package com.panda.teeming.mob.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.teeming.mob.overworld.VinedropStrangler;
import com.panda.teeming.roster.RosterStats;
import org.junit.jupiter.api.Test;

/**
 * The Vinedrop Strangler's numbers against its roster row, written out as plain values: HP 22, Atk 4, Arm 0, 0.8 x
 * 1.2; a 12 t shadow; it drops from up to 12 blocks above; the wrap is Slowness IV and 1 damage a second for 80 t;
 * shears or a sweep (a sword swing at least 90 % charged, the vanilla rule) free the victim and leave it stunned 60 t
 * (x1.5 damage, as every stunned mob); jungles, placed on leaves, weight 15, alone. The GameTests check the numbers
 * are applied; this checks they are the right numbers.
 */
class VinedropStranglerNumbersTest {
    @Test
    void mechanic() {
        assertEquals(12, VinedropStrangler.TELL_TICKS);
        assertEquals(12.0, VinedropStrangler.DROP_HEIGHT);
        assertEquals(80, VinedropStrangler.WRAP_TICKS);
        assertEquals(3, VinedropStrangler.SLOWNESS_LEVEL);
        assertEquals(1.0F, VinedropStrangler.SQUEEZE_DAMAGE);
        assertEquals(20, VinedropStrangler.SQUEEZE_EVERY);
        assertEquals(4, VinedropStrangler.SQUEEZES);
        assertEquals(0.9F, VinedropStrangler.SWEEP_CHARGE);
        assertEquals(60, VinedropStrangler.STUN_TICKS);
        assertEquals(1.5F, VinedropStrangler.STUN_DAMAGE);
    }

    /** The numbers this mob adds beyond its row (the drop, the creep, the climb back). */
    @Test
    void movement() {
        assertEquals(1.0, VinedropStrangler.DROP_SPEED);
        assertEquals(1.0, VinedropStrangler.TRIGGER_RANGE);
        assertEquals(0.08, VinedropStrangler.CREEP_SPEED);
        assertEquals(8.0, VinedropStrangler.CREEP_RANGE);
        assertEquals(0.25, VinedropStrangler.CLIMB_SPEED);
        assertEquals(20, VinedropStrangler.RECOVER_TICKS);
        assertEquals(60, VinedropStrangler.COOLDOWN);
    }

    @Test
    void row() {
        RosterStats.Entry row = RosterStats.get("vinedrop_strangler");
        assertEquals(22.0F, row.health());
        assertEquals(4.0F, row.attack());
        assertEquals(0.0F, row.armor());
        assertEquals(0.8F, row.width());
        assertEquals(1.2F, row.height());
        assertEquals(12, row.tellTicks());
        assertEquals(15, row.weight());
        assertEquals(1, row.groupMin());
        assertEquals(1, row.groupMax());
        assertEquals("bamboo_strangler", row.secondLook());
        assertEquals("heartwood", row.material());
        assertEquals(RosterStats.Place.LEAVES, row.place());
        assertEquals(RosterStats.Dim.OVERWORLD, row.dim());
        assertEquals(RosterStats.Cat.MONSTER, row.cat());
    }
}
