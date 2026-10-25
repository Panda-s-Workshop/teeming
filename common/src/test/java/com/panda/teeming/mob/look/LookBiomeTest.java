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
            Map.entry("skreel/snowy_skreel", "minecraft:jagged_peaks"),
            Map.entry("gleamtrap/warped_gleamtrap", "minecraft:warped_forest"),
            Map.entry("scarlet_mantis/ashen_mantis", "minecraft:nether_wastes"),
            Map.entry("phantasm_lantern/crimson_lantern", "minecraft:crimson_forest"),
            Map.entry("woolcap_grazer/crimson_woolcap", "minecraft:crimson_forest"),
            Map.entry("siphonjelly/crimson_jelly", "minecraft:crimson_forest"),
            Map.entry("spore_cannon/crimson_cannon", "minecraft:crimson_forest"),
            Map.entry("gloomweaver/crimson_weaver", "minecraft:crimson_forest"),
            Map.entry("shredjaw_beetle/warped_shredjaw", "minecraft:warped_forest"),
            Map.entry("ash_jackal/soul_jackal", "minecraft:soul_sand_valley"),
            Map.entry("cindermoth_swarm/soul_moths", "minecraft:soul_sand_valley"),
            Map.entry("pyre_herald/soul_herald", "minecraft:soul_sand_valley"),
            Map.entry("flamewright/soul_wright", "minecraft:soul_sand_valley"),
            Map.entry("slagspitter/basalt_spitter", "minecraft:basalt_deltas"),
            // a base look that belongs to the mob's other home, so the place look is the only one in its place
            Map.entry("phantasm_lantern/base", "minecraft:warped_forest"),
            Map.entry("woolcap_grazer/base", "minecraft:warped_forest"),
            Map.entry("siphonjelly/base", "minecraft:warped_forest"),
            Map.entry("spore_cannon/base", "minecraft:warped_forest"),
            Map.entry("gloomweaver/base", "minecraft:warped_forest"),
            Map.entry("shredjaw_beetle/base", "minecraft:crimson_forest"));

    @Test
    void placeLooksAreLimitedToTheirPlace() throws IOException {
        for (Map.Entry<String, String> e : PLACE_LOOKS.entrySet()) {
            String json = Files.readString(LOOKS.resolve(e.getKey() + ".json"));
            Matcher m = BIOMES.matcher(json);
            assertEquals(e.getValue(), m.find() ? m.group(1) : "", e.getKey() + " should appear only in its place");
        }
    }
}
