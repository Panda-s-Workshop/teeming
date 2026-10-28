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
    public static final DeferredItem<CharmItem> HEARTH_CHARM = ITEMS.registerItem("hearth_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> HUSH_CHARM = ITEMS.registerItem("hush_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> ANTIVENOM_CHARM = ITEMS.registerItem("antivenom_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> QUENCH_CHARM = ITEMS.registerItem("quench_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> TETHER_CHARM = ITEMS.registerItem("tether_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> GILDED_WARD_CHARM = ITEMS.registerItem("gilded_ward_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> CLEAR_VOICE_CHARM = ITEMS.registerItem("clear_voice_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> GROUNDING_CHARM = ITEMS.registerItem("grounding_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> RIFT_CHARM = ITEMS.registerItem("rift_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> ROT_WARD_CHARM = ITEMS.registerItem("rot_ward_charm", CharmItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CharmItem> MEMORY_CHARM = ITEMS.registerItem("memory_charm", CharmItem::new, p -> p.stacksTo(1));

    public static final List<DeferredItem<CharmItem>> ALL = List.of(SHELLWARD_CHARM, HEARTH_CHARM, HUSH_CHARM, ANTIVENOM_CHARM, QUENCH_CHARM, TETHER_CHARM, GILDED_WARD_CHARM, CLEAR_VOICE_CHARM, GROUNDING_CHARM, RIFT_CHARM, ROT_WARD_CHARM, MEMORY_CHARM);

    /** Each charm's recipe for the GameTests: its ingredients as "item*count". */
    public static final Map<String, List<String>> RECIPES = Map.ofEntries(
            Map.entry("shellward_charm", List.of("teeming:tough_hide*2", "teeming:sand_scale*2", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("hearth_charm", List.of("teeming:frost_core*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("hush_charm", List.of("teeming:echo_membrane*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("antivenom_charm", List.of("teeming:venom_fang*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("quench_charm", List.of("teeming:frost_core*2", "teeming:slag_gland*2", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("tether_charm", List.of("teeming:vein_sac*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("gilded_ward_charm", List.of("teeming:gilded_gizzard*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("clear_voice_charm", List.of("teeming:soul_vertebra*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("grounding_charm", List.of("teeming:gravity_stone*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("rift_charm", List.of("teeming:rift_essence*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("rot_ward_charm", List.of("teeming:time_sand*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")),
            Map.entry("memory_charm", List.of("teeming:void_ichor*4", "minecraft:gold_ingot*1", "minecraft:amethyst_shard*1")));

    private ModCharms() {}
}
