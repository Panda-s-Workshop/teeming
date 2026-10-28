package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.item.CharmItem;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.platform.registry.DeferredRegister;
import java.util.List;
import java.util.Map;

/** The counter charms; each works only in the off-hand (its number and check are in Charms). */
public final class ModCharms {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeemingMod.MODID);

    public static final DeferredItem<CharmItem> SHELLWARD_CHARM = ITEMS.registerItem("shellward_charm", CharmItem::new, p -> p.stacksTo(1));

    public static final List<DeferredItem<CharmItem>> ALL = List.of(SHELLWARD_CHARM);

    /** Each charm's recipe for the GameTests: its ingredients as "item*count". */
    public static final Map<String, List<String>> RECIPES = Map.ofEntries(
            Map.entry("shellward_charm", List.of("teeming:tough_hide*2", "teeming:sand_scale*2", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")));

    private ModCharms() {}
}
