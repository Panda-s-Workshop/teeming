package com.panda.teeming.client.fieldguide;

import java.util.List;
import java.util.Map;

/** What each mob drops, as item ids: its signature material, its leftovers, then its trophy. */
public final class MobDrops {
    private MobDrops() {}

    private static final Map<String, List<String>> DROPS = Map.ofEntries(
            Map.entry("thornhorn", List.of("teeming:tough_hide", "minecraft:leather", "minecraft:beef", "teeming:trophy")),
            Map.entry("snagwood", List.of("teeming:heartwood", "minecraft:stick", "minecraft:bone_meal", "teeming:trophy")),
            Map.entry("puffcap_shepherd", List.of("teeming:spore_sac", "minecraft:red_mushroom", "minecraft:stick", "teeming:trophy")),
            Map.entry("mire_gulper", List.of("teeming:bog_gland", "minecraft:slime_ball", "minecraft:lily_pad", "teeming:trophy")),
            Map.entry("dunefin", List.of("teeming:sand_scale", "minecraft:sand", "minecraft:bone", "teeming:trophy")),
            Map.entry("rattlecoil", List.of("teeming:venom_fang", "minecraft:leather", "teeming:trophy")),
            Map.entry("snowroller", List.of("teeming:frost_core", "minecraft:snowball", "teeming:trophy")),
            Map.entry("shiverwraith", List.of("teeming:frost_core", "minecraft:snowball", "teeming:trophy")),
            Map.entry("gloamhound", List.of("teeming:tough_hide", "minecraft:bone", "teeming:trophy")),
            Map.entry("vinedrop_strangler", List.of("teeming:heartwood", "minecraft:vine", "minecraft:stick", "teeming:trophy")),
            Map.entry("snapmaw_bloom", List.of("teeming:heartwood", "minecraft:wheat_seeds", "teeming:trophy")),
            Map.entry("stormeel", List.of("teeming:storm_coil", "minecraft:cod", "minecraft:prismarine_crystals", "teeming:trophy")),
            Map.entry("shellguard_crab", List.of("teeming:sand_scale", "teeming:trophy")),
            Map.entry("dripfang", List.of("teeming:venom_fang", "minecraft:pointed_dripstone", "teeming:trophy")),
            Map.entry("hushwing", List.of("teeming:echo_membrane", "teeming:trophy")),
            Map.entry("mycelite_shambler", List.of("teeming:spore_sac", "minecraft:brown_mushroom", "teeming:trophy")),
            Map.entry("petalkin", List.of("teeming:petal_dust", "minecraft:pink_petals", "teeming:trophy")),
            Map.entry("hollow_mourner", List.of("teeming:grave_ash", "minecraft:bone", "minecraft:string", "teeming:trophy")),
            Map.entry("dustwhirl", List.of("teeming:gale_essence", "minecraft:stick", "teeming:trophy")),
            Map.entry("skreel", List.of("teeming:gale_essence", "minecraft:feather", "teeming:trophy")),
            Map.entry("prismback", List.of("teeming:prism_shard", "minecraft:quartz", "teeming:trophy")),
            Map.entry("slagspitter", List.of("teeming:slag_gland", "minecraft:basalt", "minecraft:magma_cream", "teeming:trophy")),
            Map.entry("ash_jackal", List.of("teeming:ash_pelt", "minecraft:bone", "teeming:trophy")),
            Map.entry("slagheart_golem", List.of("teeming:slag_gland", "minecraft:basalt", "minecraft:coal", "teeming:trophy")),
            Map.entry("soot_manta", List.of("teeming:soot_plume", "minecraft:charcoal", "teeming:trophy")),
            Map.entry("magma_tortoise", List.of("teeming:magma_scute", "minecraft:basalt", "teeming:trophy")),
            Map.entry("veinleech", List.of("teeming:vein_sac", "minecraft:slime_ball", "teeming:trophy")),
            Map.entry("shredjaw_beetle", List.of("teeming:chitin_plate", "minecraft:crimson_fungus", "teeming:trophy")),
            Map.entry("weepling_broodmother", List.of("teeming:vein_sac", "minecraft:crimson_fungus", "teeming:trophy")),
            Map.entry("gleamtrap", List.of("teeming:siphon_gel", "minecraft:shroomlight", "teeming:trophy")),
            Map.entry("scarlet_mantis", List.of("teeming:chitin_plate", "minecraft:crimson_fungus", "teeming:trophy")),
            Map.entry("phantasm_lantern", List.of("teeming:siphon_gel", "minecraft:warped_fungus", "teeming:trophy")),
            Map.entry("woolcap_grazer", List.of("teeming:fungal_fleece", "minecraft:warped_roots", "teeming:trophy")),
            Map.entry("siphonjelly", List.of("teeming:siphon_gel", "minecraft:slime_ball", "teeming:trophy")),
            Map.entry("spore_cannon", List.of("teeming:siphon_gel", "minecraft:warped_fungus", "minecraft:warped_wart_block", "teeming:trophy")),
            Map.entry("gloomweaver", List.of("teeming:chitin_plate", "minecraft:string", "teeming:trophy")),
            Map.entry("ossuary_wyrm", List.of("teeming:soul_vertebra", "minecraft:bone", "minecraft:soul_sand", "teeming:trophy")),
            Map.entry("keening_shade", List.of("teeming:soul_vertebra", "minecraft:soul_sand", "teeming:trophy")),
            Map.entry("soulfire_seer", List.of("teeming:soul_vertebra", "minecraft:bone", "minecraft:soul_sand", "teeming:trophy")),
            Map.entry("cindermoth_swarm", List.of("teeming:ember_wing", "minecraft:glowstone_dust", "teeming:trophy")),
            Map.entry("soulward_revenant", List.of("teeming:soul_vertebra", "minecraft:bone", "minecraft:iron_nugget", "teeming:trophy")),
            Map.entry("basalt_behemoth", List.of("teeming:molten_heart", "minecraft:basalt", "minecraft:blackstone", "teeming:trophy")),
            Map.entry("quakehopper", List.of("teeming:slag_gland", "minecraft:basalt", "teeming:trophy")),
            Map.entry("ashharrier", List.of("teeming:ember_wing", "minecraft:feather", "teeming:trophy")),
            Map.entry("lavaleaper", List.of("teeming:slag_gland", "minecraft:magma_cream", "teeming:trophy")),
            Map.entry("blackstone_scuttler", List.of("teeming:chitin_plate", "minecraft:blackstone", "teeming:trophy")),
            Map.entry("pyre_herald", List.of("teeming:ember_wing", "minecraft:charcoal", "teeming:trophy")),
            Map.entry("fortress_jailer", List.of("teeming:chain_link", "minecraft:iron_nugget", "teeming:trophy")),
            Map.entry("flamewright", List.of("teeming:ember_wing", "minecraft:blaze_powder", "teeming:trophy")),
            Map.entry("aurovore", List.of("teeming:gilded_gizzard", "minecraft:gold_nugget", "teeming:trophy")),
            Map.entry("mending_crystalling", List.of("teeming:void_crystal", "minecraft:amethyst_shard", "teeming:trophy")),
            Map.entry("liftstone_monolith", List.of("teeming:gravity_stone", "minecraft:end_stone", "minecraft:obsidian", "teeming:trophy")),
            Map.entry("astral_augur", List.of("teeming:star_mote", "minecraft:chorus_fruit", "teeming:trophy")),
            Map.entry("nullcantor", List.of("teeming:void_ichor", "minecraft:chorus_fruit", "teeming:trophy")),
            Map.entry("chorus_grazer", List.of("teeming:purpur_fleece", "minecraft:chorus_fruit", "teeming:trophy")),
            Map.entry("levimoth", List.of("teeming:chorus_silk", "minecraft:string", "teeming:trophy")),
            Map.entry("warpseed_bloom", List.of("teeming:chorus_silk", "minecraft:chorus_fruit", "teeming:trophy")),
            Map.entry("glimmerless", List.of("teeming:void_ichor", "minecraft:chorus_fruit", "teeming:trophy")),
            Map.entry("riftjackal", List.of("teeming:rift_essence", "minecraft:chorus_fruit", "teeming:trophy")),
            Map.entry("chorus_husk", List.of("teeming:chorus_silk", "minecraft:bone", "teeming:trophy")),
            Map.entry("gatecaster", List.of("teeming:rift_essence", "minecraft:chorus_fruit", "teeming:trophy")),
            Map.entry("hollowmind", List.of("teeming:rift_essence", "minecraft:chorus_fruit", "teeming:trophy")),
            Map.entry("pearlgulp", List.of("teeming:rift_essence", "teeming:trophy")),
            Map.entry("brinkram", List.of("teeming:gravity_stone", "minecraft:end_stone", "teeming:trophy")),
            Map.entry("facet_doppel", List.of("teeming:void_ichor", "teeming:trophy")),
            Map.entry("shardling_colossus", List.of("teeming:void_crystal", "minecraft:end_stone", "teeming:trophy")),
            Map.entry("swellshade", List.of("teeming:void_ichor", "teeming:trophy"))
    );

    public static List<String> of(String mob) {
        return DROPS.getOrDefault(mob, List.of());
    }
}
