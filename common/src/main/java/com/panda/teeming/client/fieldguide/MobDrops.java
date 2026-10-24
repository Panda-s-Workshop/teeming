package com.panda.teeming.client.fieldguide;

import java.util.List;
import java.util.Map;

/** What each mob drops, as item ids: its signature material, its leftovers, then its trophy. */
public final class MobDrops {
    private MobDrops() {}

    private static final Map<String, List<String>> DROPS = Map.ofEntries(
            Map.entry("thornhorn", List.of("teeming:tough_hide", "minecraft:leather", "minecraft:beef", "teeming:trophy")),
            Map.entry("slagheart_golem", List.of("teeming:slag_gland", "minecraft:basalt", "minecraft:coal", "teeming:trophy")),
            Map.entry("levimoth", List.of("teeming:chorus_silk", "minecraft:string", "teeming:trophy"))
    );

    public static List<String> of(String mob) {
        return DROPS.getOrDefault(mob, List.of());
    }
}
