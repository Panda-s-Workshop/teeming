package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.item.MaterialItem;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.platform.registry.DeferredRegister;
import java.util.List;
import java.util.Map;

/** The signature materials mobs drop, each with at least one use. */
public final class ModMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeemingMod.MODID);

    public static final DeferredItem<MaterialItem> TOUGH_HIDE = ITEMS.registerItem("tough_hide", MaterialItem::new);
    public static final DeferredItem<MaterialItem> HEARTWOOD = ITEMS.registerItem("heartwood", MaterialItem::new);
    public static final DeferredItem<MaterialItem> SPORE_SAC = ITEMS.registerItem("spore_sac", MaterialItem::new);
    public static final DeferredItem<MaterialItem> BOG_GLAND = ITEMS.registerItem("bog_gland", MaterialItem::new);
    public static final DeferredItem<MaterialItem> SAND_SCALE = ITEMS.registerItem("sand_scale", MaterialItem::new);
    public static final DeferredItem<MaterialItem> VENOM_FANG = ITEMS.registerItem("venom_fang", MaterialItem::new);
    public static final DeferredItem<MaterialItem> FROST_CORE = ITEMS.registerItem("frost_core", MaterialItem::new);
    public static final DeferredItem<MaterialItem> STORM_COIL = ITEMS.registerItem("storm_coil", MaterialItem::new);
    public static final DeferredItem<MaterialItem> ECHO_MEMBRANE = ITEMS.registerItem("echo_membrane", MaterialItem::new);
    public static final DeferredItem<MaterialItem> PETAL_DUST = ITEMS.registerItem("petal_dust", MaterialItem::new);
    public static final DeferredItem<MaterialItem> GRAVE_ASH = ITEMS.registerItem("grave_ash", MaterialItem::new);
    public static final DeferredItem<MaterialItem> GALE_ESSENCE = ITEMS.registerItem("gale_essence", MaterialItem::new);
    public static final DeferredItem<MaterialItem> PRISM_SHARD = ITEMS.registerItem("prism_shard", MaterialItem::new);
    public static final DeferredItem<MaterialItem> SLAG_GLAND = ITEMS.registerItem("slag_gland", MaterialItem::new);
    public static final DeferredItem<MaterialItem> ASH_PELT = ITEMS.registerItem("ash_pelt", MaterialItem::new);
    public static final DeferredItem<MaterialItem> SOOT_PLUME = ITEMS.registerItem("soot_plume", MaterialItem::new);
    public static final DeferredItem<MaterialItem> MAGMA_SCUTE = ITEMS.registerItem("magma_scute", MaterialItem::new);
    public static final DeferredItem<MaterialItem> VEIN_SAC = ITEMS.registerItem("vein_sac", MaterialItem::new);
    public static final DeferredItem<MaterialItem> CHITIN_PLATE = ITEMS.registerItem("chitin_plate", MaterialItem::new);
    public static final DeferredItem<MaterialItem> SIPHON_GEL = ITEMS.registerItem("siphon_gel", MaterialItem::new);
    public static final DeferredItem<MaterialItem> FUNGAL_FLEECE = ITEMS.registerItem("fungal_fleece", MaterialItem::new);
    public static final DeferredItem<MaterialItem> SOUL_VERTEBRA = ITEMS.registerItem("soul_vertebra", MaterialItem::new);
    public static final DeferredItem<MaterialItem> EMBER_WING = ITEMS.registerItem("ember_wing", MaterialItem::new);
    public static final DeferredItem<MaterialItem> MOLTEN_HEART = ITEMS.registerItem("molten_heart", MaterialItem::new);
    public static final DeferredItem<MaterialItem> CHAIN_LINK = ITEMS.registerItem("chain_link", MaterialItem::new);
    public static final DeferredItem<MaterialItem> GILDED_GIZZARD = ITEMS.registerItem("gilded_gizzard", MaterialItem::new);
    public static final DeferredItem<MaterialItem> VOID_CRYSTAL = ITEMS.registerItem("void_crystal", MaterialItem::new);
    public static final DeferredItem<MaterialItem> GRAVITY_STONE = ITEMS.registerItem("gravity_stone", MaterialItem::new);
    public static final DeferredItem<MaterialItem> STAR_MOTE = ITEMS.registerItem("star_mote", MaterialItem::new);
    public static final DeferredItem<MaterialItem> VOID_ICHOR = ITEMS.registerItem("void_ichor", MaterialItem::new);
    public static final DeferredItem<MaterialItem> PURPUR_FLEECE = ITEMS.registerItem("purpur_fleece", MaterialItem::new);
    public static final DeferredItem<MaterialItem> CHORUS_SILK = ITEMS.registerItem("chorus_silk", MaterialItem::new);
    public static final DeferredItem<MaterialItem> RIFT_ESSENCE = ITEMS.registerItem("rift_essence", MaterialItem::new);
    public static final DeferredItem<MaterialItem> TIME_SAND = ITEMS.registerItem("time_sand", MaterialItem::new);

    public static final List<DeferredItem<MaterialItem>> ALL = List.of(TOUGH_HIDE, HEARTWOOD, SPORE_SAC, BOG_GLAND, SAND_SCALE, VENOM_FANG, FROST_CORE, STORM_COIL, ECHO_MEMBRANE, PETAL_DUST, GRAVE_ASH, GALE_ESSENCE, PRISM_SHARD, SLAG_GLAND, ASH_PELT, SOOT_PLUME, MAGMA_SCUTE, VEIN_SAC, CHITIN_PLATE, SIPHON_GEL, FUNGAL_FLEECE, SOUL_VERTEBRA, EMBER_WING, MOLTEN_HEART, CHAIN_LINK, GILDED_GIZZARD, VOID_CRYSTAL, GRAVITY_STONE, STAR_MOTE, VOID_ICHOR, PURPUR_FLEECE, CHORUS_SILK, RIFT_ESSENCE, TIME_SAND);

    /** Each material's uses, for the GameTests: "craft <in> <result> <out> [item*count ...]", "brew <potion>" or "tipped <potion>". */
    public static final Map<String, List<String>> USES = Map.ofEntries(
            Map.entry("tough_hide", List.of("craft 1 minecraft:leather 2")),
            Map.entry("heartwood", List.of("craft 1 minecraft:stick 4", "craft 2 minecraft:bone_meal 3")),
            Map.entry("spore_sac", List.of("brew minecraft:regeneration")),
            Map.entry("bog_gland", List.of("craft 1 minecraft:slime_ball 2")),
            Map.entry("sand_scale", List.of("craft 4 minecraft:turtle_scute 1")),
            Map.entry("venom_fang", List.of("brew minecraft:poison", "tipped minecraft:poison")),
            Map.entry("frost_core", List.of("craft 1 minecraft:powder_snow_bucket 1 minecraft:bucket*1")),
            Map.entry("storm_coil", List.of("brew minecraft:water_breathing")),
            Map.entry("echo_membrane", List.of("craft 1 minecraft:phantom_membrane 1")),
            Map.entry("petal_dust", List.of("craft 1 minecraft:pink_dye 4")),
            Map.entry("grave_ash", List.of("craft 1 minecraft:bone_meal 3")),
            Map.entry("gale_essence", List.of("craft 1 minecraft:wind_charge 2")),
            Map.entry("prism_shard", List.of("craft 1 minecraft:quartz 4")),
            Map.entry("slag_gland", List.of("craft 1 minecraft:magma_cream 2")),
            Map.entry("ash_pelt", List.of("craft 1 minecraft:leather 2")),
            Map.entry("soot_plume", List.of("craft 1 minecraft:ink_sac 2")),
            Map.entry("magma_scute", List.of("brew minecraft:long_fire_resistance")),
            Map.entry("vein_sac", List.of("brew minecraft:healing")),
            Map.entry("chitin_plate", List.of("craft 4 minecraft:armadillo_scute 1")),
            Map.entry("siphon_gel", List.of("craft 1 minecraft:glowstone_dust 2")),
            Map.entry("fungal_fleece", List.of("craft 1 minecraft:cyan_wool 1", "craft 1 minecraft:red_wool 1 minecraft:red_dye*1")),
            Map.entry("soul_vertebra", List.of("craft 1 minecraft:bone_meal 3")),
            Map.entry("ember_wing", List.of("craft 1 minecraft:blaze_powder 1")),
            Map.entry("molten_heart", List.of("craft 1 minecraft:magma_block 8")),
            Map.entry("chain_link", List.of("craft 1 minecraft:iron_chain 1")),
            Map.entry("gilded_gizzard", List.of("craft 1 minecraft:gold_nugget 6")),
            Map.entry("void_crystal", List.of("craft 1 minecraft:end_crystal 1 minecraft:glass*7 minecraft:ender_eye*1")),
            Map.entry("gravity_stone", List.of("craft 4 minecraft:lodestone 1 minecraft:chiseled_stone_bricks*4 minecraft:iron_ingot*1")),
            Map.entry("star_mote", List.of("craft 1 minecraft:glowstone_dust 2")),
            Map.entry("void_ichor", List.of("brew minecraft:invisibility")),
            Map.entry("purpur_fleece", List.of("craft 1 minecraft:purple_wool 1")),
            Map.entry("chorus_silk", List.of("brew minecraft:slow_falling")),
            Map.entry("rift_essence", List.of("craft 4 minecraft:ender_pearl 1")),
            Map.entry("time_sand", List.of("brew minecraft:swiftness")));

    private ModMaterials() {}
}
