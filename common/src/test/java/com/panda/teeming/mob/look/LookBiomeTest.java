package com.panda.teeming.mob.look;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A second look named after a place appears only there: its look file limits it to that biome or biome tag. Looks
 * named for a colour or an age appear wherever the mob spawns, so they aren't listed.
 */
class LookBiomeTest {
    private static final Path LOOKS = Path.of("src", "main", "resources", "data", "teeming", "teeming", "looks");
    private static final Pattern BIOMES = Pattern.compile("\"biomes\"\\s*:\\s*\"([^\"]*)\"");

    /** mob/look → the biome or tag it is limited to. */
    private static final Map<String, String> PLACE_LOOKS = Map.ofEntries(
            Map.entry("snagwood/birch_snag", "#c:is_birch_forest"),
            Map.entry("mire_gulper/mangrove_gulper", "minecraft:mangrove_swamp"),
            Map.entry("dunefin/red_dunefin", "#c:is_badlands"),
            Map.entry("rattlecoil/sidewinder", "#c:is_desert"),
            Map.entry("gloamhound/snowpelt", "minecraft:snowy_taiga"),
            Map.entry("vinedrop_strangler/bamboo_strangler", "minecraft:bamboo_jungle"),
            Map.entry("stormeel/abyssal_eel", "minecraft:deep_lukewarm_ocean"),
            Map.entry("shellguard_crab/stone_crab", "minecraft:stony_shore"),
            Map.entry("hollow_mourner/bog_mourner", "#c:is_swamp"),
            Map.entry("dustwhirl/red_dust", "minecraft:wooded_badlands"),
            Map.entry("skreel/snowy_skreel", "minecraft:jagged_peaks"));

    @Test
    void placeLooksAreLimitedToTheirPlace() throws IOException {
        for (Map.Entry<String, String> e : PLACE_LOOKS.entrySet()) {
            String json = Files.readString(LOOKS.resolve(e.getKey() + ".json"));
            Matcher m = BIOMES.matcher(json);
            assertEquals(e.getValue(), m.find() ? m.group(1) : "", e.getKey() + " should appear only in its place");
        }
    }
}
