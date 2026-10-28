package com.panda.teeming.charm;

import com.panda.teeming.item.CharmItem;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.registry.ModCharms;
import net.minecraft.world.entity.LivingEntity;

/**
 * What each counter charm changes, and the one check every mechanic makes: a charm counts only in its holder's
 * off-hand, so wearing one trades away the shield, totem or torch. A mechanic asks here with its target and scales
 * its own number; the charm item carries no behaviour of its own.
 */
public final class Charms {
    private Charms() {}

    /** Shellward Charm: a charge or ram throws its holder this much as far (and as high). */
    public static final double SHELLWARD_THROW = 0.5;

    /** True when {@code holder} has {@code charm} in the off-hand. */
    public static boolean holds(LivingEntity holder, DeferredItem<CharmItem> charm) {
        return holder.getOffhandItem().is(charm.get());
    }

    /** The share of a charge's or ram's throw that reaches {@code victim}: halved by the Shellward Charm. */
    public static double chargeThrow(LivingEntity victim) {
        return holds(victim, ModCharms.SHELLWARD_CHARM) ? SHELLWARD_THROW : 1.0;
    }
}
