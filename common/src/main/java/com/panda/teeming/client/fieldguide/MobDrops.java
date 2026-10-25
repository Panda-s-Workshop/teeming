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
            Map.entry("dunefin", List.of("teeming:sand_scale", "minecraft:sand", "minecraft:bone", "teeming:trophy")),
            Map.entry("rattlecoil", List.of("teeming:venom_fang", "minecraft:leather", "teeming:trophy")),
            Map.entry("snapmaw_bloom", List.of("teeming:heartwood", "minecraft:wheat_seeds", "teeming:trophy")),
            Map.entry("stormeel", List.of("teeming:storm_coil", "minecraft:cod", "minecraft:prismarine_crystals", "teeming:trophy")),
            Map.entry("shellguard_crab", List.of("teeming:sand_scale", "teeming:trophy")),
            Map.entry("skreel", List.of("teeming:gale_essence", "minecraft:feather", "teeming:trophy")),
            Map.entry("slagheart_golem", List.of("teeming:slag_gland", "minecraft:basalt", "minecraft:coal", "teeming:trophy")),
            Map.entry("levimoth", List.of("teeming:chorus_silk", "minecraft:string", "teeming:trophy"))
    );

    public static List<String> of(String mob) {
        return DROPS.getOrDefault(mob, List.of());
    }
}
