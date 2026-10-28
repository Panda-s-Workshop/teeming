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

    /**
     * Hearth Charm: the share of a Teeming mob's freeze build-up its holder takes. It covers freezing a mob adds on
     * purpose (the Shiverwraith's frost tether); vanilla powder snow and the thaw are left as they are.
     */
    public static final double HEARTH_FREEZE = 0.5;

    /** Hearth Charm: the share of the health a frost tether on its holder heals the tether's owner (the Shiverwraith). */
    public static final double HEARTH_TETHER_HEAL = 0.5;

    /**
     * Hush Charm: the share of a sound hunter's mining range at which its holder's mining is heard. Walking and
     * sneaking already make no noise a sound hunter hears, so with the charm only sprinting and fighting are heard:
     * breaking blocks becomes as quiet as walking. A projectile its holder throws still lands as a decoy.
     */
    public static final double HUSH_MINING = 0.0;

    /** Antivenom Charm: the share of its length that Poison from a Teeming mob lasts on its holder. */
    public static final double ANTIVENOM_POISON = 0.5;

    /** True when {@code holder} has {@code charm} in the off-hand. */
    public static boolean holds(LivingEntity holder, DeferredItem<CharmItem> charm) {
        return holder.getOffhandItem().is(charm.get());
    }

    /** The share of a charge's or ram's throw that reaches {@code victim}: halved by the Shellward Charm. */
    public static double chargeThrow(LivingEntity victim) {
        return holds(victim, ModCharms.SHELLWARD_CHARM) ? SHELLWARD_THROW : 1.0;
    }

    /** The share of a Teeming mob's freeze build-up that reaches {@code target}: halved by the Hearth Charm. */
    public static double freezeBuildUp(LivingEntity target) {
        return holds(target, ModCharms.HEARTH_CHARM) ? HEARTH_FREEZE : 1.0;
    }

    /** The share of its healing a frost tether on {@code target} gives its owner: halved by the Hearth Charm. */
    public static double frostTetherHeal(LivingEntity target) {
        return holds(target, ModCharms.HEARTH_CHARM) ? HEARTH_TETHER_HEAL : 1.0;
    }

    /** The share of a sound hunter's mining range at which {@code miner} is heard: none with the Hush Charm. */
    public static double miningHeard(LivingEntity miner) {
        return holds(miner, ModCharms.HUSH_CHARM) ? HUSH_MINING : 1.0;
    }

    /** The share of its length that Poison from a Teeming mob lasts on {@code target}: halved by the Antivenom Charm. */
    public static double poisonLength(LivingEntity target) {
        return holds(target, ModCharms.ANTIVENOM_CHARM) ? ANTIVENOM_POISON : 1.0;
    }

    /** Ticks a Teeming mob's Poison of {@code ticks} lasts on {@code target}, after the Antivenom Charm. */
    public static int poisonTicks(LivingEntity target, int ticks) {
        return (int) Math.round(ticks * poisonLength(target));
    }
}
