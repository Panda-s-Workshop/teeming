package com.panda.teeming.spawn;

import java.util.List;
import java.util.Map;

/**
 * Teeming's share of the Nether's and the Overworld's monster spawns. Vanilla's own lists stay as they are: Teeming adds about
 * 21 % of each biome's monster picks. Where whole biome-list weights can land in the share range,
 * each mob gets a tuned weight in that biome ({@link #TUNED}; its roster weight sets its part of the biome's total).
 * Where they can't (a biome whose vanilla monster weight is too small for even weight 1 per mob), the biome is
 * share-capped: the Teeming spawner places those monsters, and only while that brings the Teeming share of the
 * monsters round the player nearer the target. Plain Java with no Minecraft types, so JUnit can read it.
 */
public final class SpawnTable {
    private SpawnTable() {}

    /** The share of monster spawns Teeming aims for, and the range around it (1 in 4 to 6 natural spawns). */
    public static final double TARGET_SHARE = 0.21;
    public static final double SHARE_MIN = 0.17;
    public static final double SHARE_MAX = 0.25;

    /** Vanilla's total monster weight in each Nether biome (26.3 biome files, {@code natural_mob_spawns}). */
    public static final Map<String, Integer> VANILLA_MONSTER_WEIGHT = Map.of(
            "minecraft:nether_wastes", 168,
            "minecraft:soul_sand_valley", 71,
            "minecraft:crimson_forest", 15,
            "minecraft:warped_forest", 1,
            "minecraft:basalt_deltas", 140);

    /**
     * Vanilla's total monster weight in each Overworld biome that has a Teeming biome-list monster (26.3 biome files).
     * The Overworld is tuned as the Nether is, except that open-water mobs take their part against
     * {@link #VANILLA_OVERWORLD_WATER_WEIGHT} alone, and the rest of the biome's total goes to the other mobs.
     */
    public static final Map<String, Integer> VANILLA_OVERWORLD_MONSTER_WEIGHT = Map.ofEntries(
            Map.entry("minecraft:badlands", 515),
            Map.entry("minecraft:beach", 515),
            Map.entry("minecraft:birch_forest", 515),
            Map.entry("minecraft:cherry_grove", 515),
            Map.entry("minecraft:dappled_forest", 515),
            Map.entry("minecraft:dark_forest", 515),
            Map.entry("minecraft:eroded_badlands", 515),
            Map.entry("minecraft:flower_forest", 515),
            Map.entry("minecraft:forest", 515),
            Map.entry("minecraft:frozen_peaks", 515),
            Map.entry("minecraft:grove", 515),
            Map.entry("minecraft:jagged_peaks", 515),
            Map.entry("minecraft:lush_caves", 515),
            Map.entry("minecraft:meadow", 515),
            Map.entry("minecraft:old_growth_birch_forest", 515),
            Map.entry("minecraft:old_growth_spruce_taiga", 515),
            Map.entry("minecraft:pale_garden", 515),
            Map.entry("minecraft:snowy_beach", 515),
            Map.entry("minecraft:snowy_slopes", 515),
            Map.entry("minecraft:snowy_taiga", 515),
            Map.entry("minecraft:sparse_jungle", 515),
            Map.entry("minecraft:stony_peaks", 515),
            Map.entry("minecraft:stony_shore", 515),
            Map.entry("minecraft:taiga", 515),
            Map.entry("minecraft:windswept_forest", 515),
            Map.entry("minecraft:windswept_gravelly_hills", 515),
            Map.entry("minecraft:windswept_hills", 515),
            Map.entry("minecraft:wooded_badlands", 515),
            Map.entry("minecraft:cold_ocean", 520),
            Map.entry("minecraft:deep_cold_ocean", 520),
            Map.entry("minecraft:deep_frozen_ocean", 520),
            Map.entry("minecraft:deep_lukewarm_ocean", 520),
            Map.entry("minecraft:deep_ocean", 520),
            Map.entry("minecraft:frozen_ocean", 520),
            Map.entry("minecraft:lukewarm_ocean", 520),
            Map.entry("minecraft:ocean", 520),
            Map.entry("minecraft:warm_ocean", 520),
            Map.entry("minecraft:plains", 515),
            Map.entry("minecraft:savanna", 515),
            Map.entry("minecraft:savanna_plateau", 515),
            Map.entry("minecraft:sunflower_plains", 515),
            Map.entry("minecraft:windswept_savanna", 515),
            Map.entry("minecraft:bamboo_jungle", 517),
            Map.entry("minecraft:jungle", 517),
            Map.entry("minecraft:deep_dark", 0),
            Map.entry("minecraft:mushroom_fields", 0),
            Map.entry("minecraft:mangrove_swamp", 516),
            Map.entry("minecraft:swamp", 516),
            Map.entry("minecraft:desert", 515),
            Map.entry("minecraft:dripstone_caves", 610),
            Map.entry("minecraft:frozen_river", 516),
            Map.entry("minecraft:ice_spikes", 515),
            Map.entry("minecraft:old_growth_pine_taiga", 540),
            Map.entry("minecraft:river", 615),
            Map.entry("minecraft:snowy_plains", 515),
            Map.entry("minecraft:sulfur_caves", 311));

    /**
     * Vanilla's weight of open-water monsters (the drowned) in the same biomes. A spot in open water refuses every land
     * pick, so an open-water Teeming mob's weight is the whole number nearest the target share of this, at least 1.
     */
    public static final Map<String, Integer> VANILLA_OVERWORLD_WATER_WEIGHT = Map.ofEntries(
            Map.entry("minecraft:badlands", 0),
            Map.entry("minecraft:beach", 0),
            Map.entry("minecraft:birch_forest", 0),
            Map.entry("minecraft:cherry_grove", 0),
            Map.entry("minecraft:dappled_forest", 0),
            Map.entry("minecraft:dark_forest", 0),
            Map.entry("minecraft:eroded_badlands", 0),
            Map.entry("minecraft:flower_forest", 0),
            Map.entry("minecraft:forest", 0),
            Map.entry("minecraft:frozen_peaks", 0),
            Map.entry("minecraft:grove", 0),
            Map.entry("minecraft:jagged_peaks", 0),
            Map.entry("minecraft:lush_caves", 0),
            Map.entry("minecraft:meadow", 0),
            Map.entry("minecraft:old_growth_birch_forest", 0),
            Map.entry("minecraft:old_growth_spruce_taiga", 0),
            Map.entry("minecraft:pale_garden", 0),
            Map.entry("minecraft:snowy_beach", 0),
            Map.entry("minecraft:snowy_slopes", 0),
            Map.entry("minecraft:snowy_taiga", 0),
            Map.entry("minecraft:sparse_jungle", 0),
            Map.entry("minecraft:stony_peaks", 0),
            Map.entry("minecraft:stony_shore", 0),
            Map.entry("minecraft:taiga", 0),
            Map.entry("minecraft:windswept_forest", 0),
            Map.entry("minecraft:windswept_gravelly_hills", 0),
            Map.entry("minecraft:windswept_hills", 0),
            Map.entry("minecraft:wooded_badlands", 0),
            Map.entry("minecraft:cold_ocean", 5),
            Map.entry("minecraft:deep_cold_ocean", 5),
            Map.entry("minecraft:deep_frozen_ocean", 5),
            Map.entry("minecraft:deep_lukewarm_ocean", 5),
            Map.entry("minecraft:deep_ocean", 5),
            Map.entry("minecraft:frozen_ocean", 5),
            Map.entry("minecraft:lukewarm_ocean", 5),
            Map.entry("minecraft:ocean", 5),
            Map.entry("minecraft:warm_ocean", 5),
            Map.entry("minecraft:plains", 0),
            Map.entry("minecraft:savanna", 0),
            Map.entry("minecraft:savanna_plateau", 0),
            Map.entry("minecraft:sunflower_plains", 0),
            Map.entry("minecraft:windswept_savanna", 0),
            Map.entry("minecraft:bamboo_jungle", 0),
            Map.entry("minecraft:jungle", 0),
            Map.entry("minecraft:deep_dark", 0),
            Map.entry("minecraft:mushroom_fields", 0),
            Map.entry("minecraft:mangrove_swamp", 0),
            Map.entry("minecraft:swamp", 0),
            Map.entry("minecraft:desert", 0),
            Map.entry("minecraft:dripstone_caves", 95),
            Map.entry("minecraft:frozen_river", 1),
            Map.entry("minecraft:ice_spikes", 0),
            Map.entry("minecraft:old_growth_pine_taiga", 0),
            Map.entry("minecraft:river", 100),
            Map.entry("minecraft:snowy_plains", 0),
            Map.entry("minecraft:sulfur_caves", 0));

    /**
     * The End aims at about half of all monsters, counted as mobs (endermen come four at a time). The cap the spawner
     * keeps there counts Teeming monsters alone, against this share of the vanilla per-player cap, so the endermen that
     * fill that cap round the main island don't shut Teeming out. No End biome's lists may go past the most.
     */
    public static final double END_SHARE = 0.5;
    public static final double END_SHARE_MAX = 0.6;
    /**
     * The End's biome-list weights are set once from all 40 End rows: each outer biome's share, counted as mobs, once
     * every End mob is in (the small islands aim at a third, the others at {@link #END_SHARE}). Built mobs carry their
     * part of it in {@link #TUNED}; the live lists reach these shares when the last End mob is built.
     */
    public static final Map<String, Double> END_PLANNED_SHARE = Map.of(
            "minecraft:end_highlands", 0.4771,
            "minecraft:end_midlands", 0.5238,
            "minecraft:end_barrens", 0.5031,
            "minecraft:small_end_islands", 0.3443);
    /** How far from its target a biome's planned share may land. */
    public static final double END_PLAN_RANGE = 0.05;

    /** Vanilla's monster weight in each End biome: the enderman alone, weight 10 in groups of 4. */
    public static final Map<String, Integer> VANILLA_END_MONSTER_WEIGHT = Map.of(
            "minecraft:the_end", 10,
            "minecraft:end_highlands", 10,
            "minecraft:end_midlands", 10,
            "minecraft:end_barrens", 10,
            "minecraft:small_end_islands", 10);

    /** The default contents of {@code #teeming:share_capped}: biomes whose Teeming monsters only the spawner places. */
    public static final List<String> SHARE_CAPPED = List.of("minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:deep_dark", "minecraft:mushroom_fields");

    /** A mob's biome-list weight in one tuned biome; elsewhere in its tag it keeps its roster weight. */
    public record Row(String mob, String biome, int weight) {}

    public static final List<Row> TUNED = List.of(
            new Row("snagwood", "minecraft:birch_forest", 103),
            new Row("snagwood", "minecraft:dappled_forest", 103),
            new Row("snagwood", "minecraft:flower_forest", 103),
            new Row("snagwood", "minecraft:forest", 103),
            new Row("snagwood", "minecraft:old_growth_birch_forest", 103),
            new Row("puffcap_shepherd", "minecraft:dark_forest", 59),
            new Row("mire_gulper", "minecraft:mangrove_swamp", 103),
            new Row("mire_gulper", "minecraft:swamp", 103),
            new Row("dunefin", "minecraft:badlands", 69),
            new Row("dunefin", "minecraft:eroded_badlands", 69),
            new Row("dunefin", "minecraft:wooded_badlands", 53),
            new Row("dunefin", "minecraft:desert", 69),
            new Row("rattlecoil", "minecraft:badlands", 51),
            new Row("rattlecoil", "minecraft:eroded_badlands", 51),
            new Row("rattlecoil", "minecraft:wooded_badlands", 39),
            new Row("rattlecoil", "minecraft:desert", 51),
            new Row("snowroller", "minecraft:grove", 103),
            new Row("snowroller", "minecraft:jagged_peaks", 68),
            new Row("snowroller", "minecraft:snowy_slopes", 103),
            new Row("shiverwraith", "minecraft:frozen_peaks", 91),
            new Row("shiverwraith", "minecraft:ice_spikes", 91),
            new Row("shiverwraith", "minecraft:snowy_plains", 91),
            new Row("gloamhound", "minecraft:old_growth_spruce_taiga", 103),
            new Row("gloamhound", "minecraft:snowy_taiga", 103),
            new Row("gloamhound", "minecraft:taiga", 103),
            new Row("gloamhound", "minecraft:old_growth_pine_taiga", 108),
            new Row("vinedrop_strangler", "minecraft:sparse_jungle", 68),
            new Row("vinedrop_strangler", "minecraft:bamboo_jungle", 68),
            new Row("vinedrop_strangler", "minecraft:jungle", 68),
            new Row("snapmaw_bloom", "minecraft:lush_caves", 46),
            new Row("snapmaw_bloom", "minecraft:sparse_jungle", 46),
            new Row("snapmaw_bloom", "minecraft:bamboo_jungle", 46),
            new Row("snapmaw_bloom", "minecraft:jungle", 46),
            new Row("stormeel", "minecraft:deep_lukewarm_ocean", 1),
            new Row("stormeel", "minecraft:lukewarm_ocean", 1),
            new Row("stormeel", "minecraft:warm_ocean", 1),
            new Row("dripfang", "minecraft:dripstone_caves", 69),
            new Row("hushwing", "minecraft:badlands", 34),
            new Row("hushwing", "minecraft:beach", 34),
            new Row("hushwing", "minecraft:birch_forest", 34),
            new Row("hushwing", "minecraft:cherry_grove", 34),
            new Row("hushwing", "minecraft:dappled_forest", 34),
            new Row("hushwing", "minecraft:dark_forest", 34),
            new Row("hushwing", "minecraft:eroded_badlands", 34),
            new Row("hushwing", "minecraft:flower_forest", 34),
            new Row("hushwing", "minecraft:forest", 34),
            new Row("hushwing", "minecraft:frozen_peaks", 34),
            new Row("hushwing", "minecraft:grove", 34),
            new Row("hushwing", "minecraft:jagged_peaks", 34),
            new Row("hushwing", "minecraft:lush_caves", 34),
            new Row("hushwing", "minecraft:meadow", 34),
            new Row("hushwing", "minecraft:old_growth_birch_forest", 34),
            new Row("hushwing", "minecraft:old_growth_spruce_taiga", 34),
            new Row("hushwing", "minecraft:pale_garden", 34),
            new Row("hushwing", "minecraft:snowy_beach", 34),
            new Row("hushwing", "minecraft:snowy_slopes", 34),
            new Row("hushwing", "minecraft:snowy_taiga", 34),
            new Row("hushwing", "minecraft:sparse_jungle", 34),
            new Row("hushwing", "minecraft:stony_peaks", 34),
            new Row("hushwing", "minecraft:stony_shore", 34),
            new Row("hushwing", "minecraft:taiga", 34),
            new Row("hushwing", "minecraft:windswept_forest", 34),
            new Row("hushwing", "minecraft:windswept_gravelly_hills", 34),
            new Row("hushwing", "minecraft:windswept_hills", 34),
            new Row("hushwing", "minecraft:wooded_badlands", 34),
            new Row("hushwing", "minecraft:cold_ocean", 34),
            new Row("hushwing", "minecraft:deep_cold_ocean", 34),
            new Row("hushwing", "minecraft:deep_frozen_ocean", 34),
            new Row("hushwing", "minecraft:deep_lukewarm_ocean", 34),
            new Row("hushwing", "minecraft:deep_ocean", 34),
            new Row("hushwing", "minecraft:frozen_ocean", 34),
            new Row("hushwing", "minecraft:lukewarm_ocean", 34),
            new Row("hushwing", "minecraft:ocean", 34),
            new Row("hushwing", "minecraft:warm_ocean", 34),
            new Row("hushwing", "minecraft:plains", 34),
            new Row("hushwing", "minecraft:savanna", 34),
            new Row("hushwing", "minecraft:savanna_plateau", 34),
            new Row("hushwing", "minecraft:sunflower_plains", 34),
            new Row("hushwing", "minecraft:windswept_savanna", 34),
            new Row("hushwing", "minecraft:bamboo_jungle", 35),
            new Row("hushwing", "minecraft:jungle", 35),
            new Row("hushwing", "minecraft:mangrove_swamp", 34),
            new Row("hushwing", "minecraft:swamp", 34),
            new Row("hushwing", "minecraft:desert", 34),
            new Row("hushwing", "minecraft:dripstone_caves", 34),
            new Row("hushwing", "minecraft:frozen_river", 34),
            new Row("hushwing", "minecraft:ice_spikes", 34),
            new Row("hushwing", "minecraft:old_growth_pine_taiga", 35),
            new Row("hushwing", "minecraft:river", 34),
            new Row("hushwing", "minecraft:snowy_plains", 34),
            new Row("hushwing", "minecraft:sulfur_caves", 20),
            new Row("mycelite_shambler", "minecraft:dark_forest", 59),
            new Row("mycelite_shambler", "minecraft:lush_caves", 68),
            new Row("mycelite_shambler", "minecraft:dripstone_caves", 51),
            new Row("hollow_mourner", "minecraft:badlands", 17),
            new Row("hollow_mourner", "minecraft:beach", 137),
            new Row("hollow_mourner", "minecraft:birch_forest", 34),
            new Row("hollow_mourner", "minecraft:cherry_grove", 137),
            new Row("hollow_mourner", "minecraft:dappled_forest", 34),
            new Row("hollow_mourner", "minecraft:dark_forest", 19),
            new Row("hollow_mourner", "minecraft:eroded_badlands", 17),
            new Row("hollow_mourner", "minecraft:flower_forest", 34),
            new Row("hollow_mourner", "minecraft:forest", 34),
            new Row("hollow_mourner", "minecraft:frozen_peaks", 46),
            new Row("hollow_mourner", "minecraft:grove", 34),
            new Row("hollow_mourner", "minecraft:jagged_peaks", 23),
            new Row("hollow_mourner", "minecraft:lush_caves", 23),
            new Row("hollow_mourner", "minecraft:meadow", 137),
            new Row("hollow_mourner", "minecraft:old_growth_birch_forest", 34),
            new Row("hollow_mourner", "minecraft:old_growth_spruce_taiga", 34),
            new Row("hollow_mourner", "minecraft:pale_garden", 137),
            new Row("hollow_mourner", "minecraft:snowy_beach", 137),
            new Row("hollow_mourner", "minecraft:snowy_slopes", 34),
            new Row("hollow_mourner", "minecraft:snowy_taiga", 34),
            new Row("hollow_mourner", "minecraft:sparse_jungle", 23),
            new Row("hollow_mourner", "minecraft:stony_peaks", 46),
            new Row("hollow_mourner", "minecraft:stony_shore", 137),
            new Row("hollow_mourner", "minecraft:taiga", 34),
            new Row("hollow_mourner", "minecraft:windswept_forest", 46),
            new Row("hollow_mourner", "minecraft:windswept_gravelly_hills", 46),
            new Row("hollow_mourner", "minecraft:windswept_hills", 46),
            new Row("hollow_mourner", "minecraft:wooded_badlands", 13),
            new Row("hollow_mourner", "minecraft:cold_ocean", 137),
            new Row("hollow_mourner", "minecraft:deep_cold_ocean", 137),
            new Row("hollow_mourner", "minecraft:deep_frozen_ocean", 137),
            new Row("hollow_mourner", "minecraft:deep_lukewarm_ocean", 137),
            new Row("hollow_mourner", "minecraft:deep_ocean", 137),
            new Row("hollow_mourner", "minecraft:frozen_ocean", 137),
            new Row("hollow_mourner", "minecraft:lukewarm_ocean", 137),
            new Row("hollow_mourner", "minecraft:ocean", 137),
            new Row("hollow_mourner", "minecraft:warm_ocean", 137),
            new Row("hollow_mourner", "minecraft:plains", 137),
            new Row("hollow_mourner", "minecraft:savanna", 40),
            new Row("hollow_mourner", "minecraft:savanna_plateau", 40),
            new Row("hollow_mourner", "minecraft:sunflower_plains", 137),
            new Row("hollow_mourner", "minecraft:windswept_savanna", 40),
            new Row("hollow_mourner", "minecraft:bamboo_jungle", 23),
            new Row("hollow_mourner", "minecraft:jungle", 23),
            new Row("hollow_mourner", "minecraft:mangrove_swamp", 34),
            new Row("hollow_mourner", "minecraft:swamp", 34),
            new Row("hollow_mourner", "minecraft:desert", 17),
            new Row("hollow_mourner", "minecraft:dripstone_caves", 17),
            new Row("hollow_mourner", "minecraft:frozen_river", 137),
            new Row("hollow_mourner", "minecraft:ice_spikes", 46),
            new Row("hollow_mourner", "minecraft:old_growth_pine_taiga", 36),
            new Row("hollow_mourner", "minecraft:river", 137),
            new Row("hollow_mourner", "minecraft:snowy_plains", 46),
            new Row("hollow_mourner", "minecraft:sulfur_caves", 83),
            new Row("dustwhirl", "minecraft:wooded_badlands", 32),
            new Row("dustwhirl", "minecraft:savanna", 97),
            new Row("dustwhirl", "minecraft:savanna_plateau", 97),
            new Row("dustwhirl", "minecraft:windswept_savanna", 97),
            new Row("skreel", "minecraft:jagged_peaks", 46),
            new Row("skreel", "minecraft:stony_peaks", 91),
            new Row("skreel", "minecraft:windswept_forest", 91),
            new Row("skreel", "minecraft:windswept_gravelly_hills", 91),
            new Row("skreel", "minecraft:windswept_hills", 91),
            new Row("prismback", "minecraft:nether_wastes", 5),
            new Row("slagspitter", "minecraft:nether_wastes", 8),
            new Row("slagspitter", "minecraft:basalt_deltas", 10),
            new Row("ash_jackal", "minecraft:nether_wastes", 8),
            new Row("ash_jackal", "minecraft:soul_sand_valley", 4),
            new Row("slagheart_golem", "minecraft:nether_wastes", 3),
            new Row("scarlet_mantis", "minecraft:nether_wastes", 6),
            new Row("ossuary_wyrm", "minecraft:soul_sand_valley", 1),
            new Row("keening_shade", "minecraft:soul_sand_valley", 3),
            new Row("soulfire_seer", "minecraft:soul_sand_valley", 2),
            new Row("cindermoth_swarm", "minecraft:nether_wastes", 7),
            new Row("cindermoth_swarm", "minecraft:soul_sand_valley", 4),
            new Row("soulward_revenant", "minecraft:soul_sand_valley", 2),
            new Row("quakehopper", "minecraft:basalt_deltas", 8),
            new Row("ashharrier", "minecraft:basalt_deltas", 6),
            new Row("lavaleaper", "minecraft:nether_wastes", 5),
            new Row("lavaleaper", "minecraft:soul_sand_valley", 3),
            new Row("lavaleaper", "minecraft:basalt_deltas", 7),
            new Row("blackstone_scuttler", "minecraft:basalt_deltas", 6),
            new Row("aurovore", "minecraft:nether_wastes", 3),
            new Row("astral_augur", "minecraft:end_highlands", 2),
            new Row("nullcantor", "minecraft:end_barrens", 3),
            new Row("levimoth", "minecraft:end_highlands", 3),
            new Row("warpseed_bloom", "minecraft:end_highlands", 3),
            new Row("glimmerless", "minecraft:end_highlands", 2),
            new Row("glimmerless", "minecraft:end_midlands", 4),
            new Row("riftjackal", "minecraft:end_highlands", 2),
            new Row("chorus_husk", "minecraft:end_highlands", 3),
            new Row("gatecaster", "minecraft:end_highlands", 2),
            new Row("hollowmind", "minecraft:end_highlands", 2),
            new Row("pearlgulp", "minecraft:end_highlands", 2),
            new Row("pearlgulp", "minecraft:small_end_islands", 7),
            new Row("brinkram", "minecraft:end_midlands", 4),
            new Row("facet_doppel", "minecraft:end_midlands", 3),
            new Row("shardling_colossus", "minecraft:end_midlands", 3),
            new Row("phasewalker", "minecraft:end_midlands", 4),
            new Row("echo_fencer", "minecraft:end_midlands", 4),
            new Row("swellshade", "minecraft:end_midlands", 4),
            new Row("chronomite", "minecraft:end_midlands", 4),
            new Row("chronomite", "minecraft:end_barrens", 3),
            new Row("vigil_owl", "minecraft:end_highlands", 2),
            new Row("vigil_owl", "minecraft:end_midlands", 4),
            new Row("anchor_golem", "minecraft:end_midlands", 4),
            new Row("pullstar", "minecraft:end_barrens", 3),
            new Row("void_bloom", "minecraft:end_barrens", 3),
            new Row("recurrent", "minecraft:end_barrens", 3),
            new Row("mnemovore", "minecraft:end_barrens", 3),
            new Row("rotfang", "minecraft:end_barrens", 4),
            new Row("gemini_watchers", "minecraft:end_barrens", 3),
            new Row("spirebloom", "minecraft:end_barrens", 3),
            new Row("riftmine_sapper", "minecraft:end_barrens", 3),
            new Row("exile_wraith", "minecraft:end_barrens", 3),
            new Row("exile_wraith", "minecraft:small_end_islands", 7),
            new Row("mote_swarm", "minecraft:end_midlands", 4),
            new Row("mote_swarm", "minecraft:small_end_islands", 7),
            new Row("resonant_triad", "minecraft:end_highlands", 2));

    /** The mob's tuned rows, empty when it has none. */
    public static List<Row> tuned(String mob) {
        return TUNED.stream().filter(r -> r.mob().equals(mob)).toList();
    }

    /**
     * A tuned biome whose vanilla monsters all carry one spawn cost (charge, energy budget): Teeming's tuned monsters
     * there carry it too, or the cost would throttle vanilla's spawns and not Teeming's, and the share of real spawns
     * would run far past the share of the weights.
     */
    public record Cost(String biome, double charge, double energyBudget) {}

    public static final List<Cost> COSTS = List.of(
            new Cost("minecraft:soul_sand_valley", 0.7, 0.15));

    /**
     * Apex mobs (on open Nether ground, and on the End's main island once the dragon is dead) stay off the biome lists:
     * each full spawner attempt wins an apex roll with this chance (one attempt per player every 200 ticks: a roll won
     * about every 13.3 minutes), and a won roll places the apex only where its whole body has room and none
     * of its kind stands within {@link #APEX_RADIUS} blocks. Apex mobs count in neither side of the share cap.
     */
    public static final double APEX_CHANCE = 0.0125;
    public static final int APEX_RADIUS = 256;
}
