package com.panda.teeming.spawn;

import java.util.List;
import java.util.Map;

/**
 * Teeming's share of the Nether's, the Overworld's and the End's monster spawns. Vanilla's own lists stay as they are.
 * Shares are counted in mobs: each entry's weight times its mean group (a vanilla entry's up to its cluster size, times
 * the share of its picks its own spawn rule lets through), since natural spawning places a pick's whole group. Where
 * whole biome-list weights can land in the share range, each mob gets a tuned weight in that biome ({@link #TUNED};
 * its roster weight sets its part of the biome's total). Where they can't, the biome is share-capped: the Teeming
 * spawner places those monsters, and only while that brings the Teeming share of the monsters round the player nearer
 * the target. Plain Java with no Minecraft types, so JUnit can read it.
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
     * Vanilla's monsters in each Nether biome, in mobs: weight times the mean count up to the cluster size, times the
     * share of picks the mob's own spawn rule lets through. The Nether's carrying mobs (all but the lava mobs, whose
     * picks land only on a lava sea's surface) aim at {@link #NETHER_CARRY} of these; the lava mobs go on top up to
     * the target.
     */
    public static final Map<String, Double> VANILLA_NETHER_MOBS = Map.of(
            "minecraft:nether_wastes", 474.50,
            "minecraft:soul_sand_valley", 86.50,
            "minecraft:crimson_forest", 52.00,
            "minecraft:warped_forest", 4.00,
            "minecraft:basalt_deltas", 327.00);
    public static final double NETHER_CARRY = 0.18;

    /** Natural spawning stops a spot at the mob's cluster size: {@link #CLUSTER} for every monster but these. */
    public static final int CLUSTER = 4;
    public static final Map<String, Integer> VANILLA_CLUSTER = Map.of("minecraft:ghast", 1);
    /** The share of a vanilla monster's picks its own spawn rule lets through, where it isn't all (the ghast's 1 in 20). */
    public static final Map<String, Double> VANILLA_PASS = Map.of("minecraft:ghast", 0.05);

    /**
     * Biomes whose ordinary ground is a narrow-place mob's place (the desert's sand for the Dunefin): there the mob
     * carries the share, and counts once, as its picks in the caves below fail while it skips the darkness roll above.
     */
    public static final Map<String, List<String>> CARRIES = Map.of("dunefin", List.of("minecraft:desert"));

    /** The mob carries the share in this biome though its place is a narrow one ({@link #CARRIES}). */
    public static boolean carries(String mob, String biome) {
        return CARRIES.getOrDefault(mob, List.of()).contains(biome);
    }

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

    /**
     * The Overworld is counted in mobs: a pick's weight times its mean group, since natural spawning places a pick's whole
     * group and vanilla's land monsters come four to a pick. These vanilla monsters count as none: the slime spawns only in
     * slime chunks below y 40 and on swamp ground in some moon phases, so nearly every pick of it fails, and the ocelot
     * on the jungles' monster list is a creature. {@link #WATER_MONSTERS} stand only in open water.
     */
    public static final List<String> UNCOUNTED_VANILLA = List.of("minecraft:slime", "minecraft:ocelot");
    public static final List<String> WATER_MONSTERS = List.of("minecraft:drowned");

    /**
     * The Overworld's carrying land mobs aim at this share in mobs, under the band's middle: natural spawning tries three
     * packs at a spot and stops at four mobs, so single picks out-land vanilla's packs of four, and the narrow-place,
     * below-y-0 and open-water mobs win spots no vanilla monster takes. With those on top up to {@link #TARGET_SHARE},
     * the night's arrivals land near the target.
     */
    public static final double OVERWORLD_CARRY = 0.18;
    /**
     * A monster that spawns in any light skips the darkness roll that refuses a dark monster about three times in four
     * on open ground under the night sky and never in an unlit cave: its mobs count this many times.
     */
    public static final int ANY_LIGHT_FACTOR = 2;
    /**
     * An open-water mob wins open water by default even at weight 1 (the drowned takes 1 of 40 of its picks in an ocean,
     * by its own spawn rule), so its natural spawns go ahead this often.
     */
    public static final double OPEN_WATER_CHANCE = 0.3333333333333333;

    /** Vanilla's land monsters in each tuned Overworld biome, in mobs (weight times mean group, the uncounted ones left out). */
    public static final Map<String, Double> VANILLA_OVERWORLD_LAND_MOBS = Map.ofEntries(
            Map.entry("minecraft:badlands", 1615.0),
            Map.entry("minecraft:beach", 1615.0),
            Map.entry("minecraft:birch_forest", 1615.0),
            Map.entry("minecraft:cherry_grove", 1615.0),
            Map.entry("minecraft:dappled_forest", 1615.0),
            Map.entry("minecraft:dark_forest", 1615.0),
            Map.entry("minecraft:eroded_badlands", 1615.0),
            Map.entry("minecraft:flower_forest", 1615.0),
            Map.entry("minecraft:forest", 1615.0),
            Map.entry("minecraft:frozen_peaks", 1615.0),
            Map.entry("minecraft:grove", 1615.0),
            Map.entry("minecraft:jagged_peaks", 1615.0),
            Map.entry("minecraft:lush_caves", 1615.0),
            Map.entry("minecraft:meadow", 1615.0),
            Map.entry("minecraft:old_growth_birch_forest", 1615.0),
            Map.entry("minecraft:old_growth_spruce_taiga", 1615.0),
            Map.entry("minecraft:pale_garden", 1615.0),
            Map.entry("minecraft:snowy_beach", 1615.0),
            Map.entry("minecraft:snowy_slopes", 1615.0),
            Map.entry("minecraft:snowy_taiga", 1615.0),
            Map.entry("minecraft:sparse_jungle", 1615.0),
            Map.entry("minecraft:stony_peaks", 1615.0),
            Map.entry("minecraft:stony_shore", 1615.0),
            Map.entry("minecraft:taiga", 1615.0),
            Map.entry("minecraft:windswept_forest", 1615.0),
            Map.entry("minecraft:windswept_gravelly_hills", 1615.0),
            Map.entry("minecraft:windswept_hills", 1615.0),
            Map.entry("minecraft:wooded_badlands", 1615.0),
            Map.entry("minecraft:cold_ocean", 1615.0),
            Map.entry("minecraft:deep_cold_ocean", 1615.0),
            Map.entry("minecraft:deep_frozen_ocean", 1615.0),
            Map.entry("minecraft:deep_lukewarm_ocean", 1615.0),
            Map.entry("minecraft:deep_ocean", 1615.0),
            Map.entry("minecraft:frozen_ocean", 1615.0),
            Map.entry("minecraft:lukewarm_ocean", 1615.0),
            Map.entry("minecraft:ocean", 1615.0),
            Map.entry("minecraft:warm_ocean", 1615.0),
            Map.entry("minecraft:plains", 1600.0),
            Map.entry("minecraft:savanna", 1600.0),
            Map.entry("minecraft:savanna_plateau", 1600.0),
            Map.entry("minecraft:sunflower_plains", 1600.0),
            Map.entry("minecraft:windswept_savanna", 1600.0),
            Map.entry("minecraft:bamboo_jungle", 1615.0),
            Map.entry("minecraft:jungle", 1615.0),
            Map.entry("minecraft:deep_dark", 0.0),
            Map.entry("minecraft:mushroom_fields", 0.0),
            Map.entry("minecraft:mangrove_swamp", 1615.0),
            Map.entry("minecraft:swamp", 1615.0),
            Map.entry("minecraft:desert", 1627.0),
            Map.entry("minecraft:dripstone_caves", 1615.0),
            Map.entry("minecraft:frozen_river", 1615.0),
            Map.entry("minecraft:ice_spikes", 1615.0),
            Map.entry("minecraft:old_growth_pine_taiga", 1655.0),
            Map.entry("minecraft:river", 1615.0),
            Map.entry("minecraft:snowy_plains", 1600.0),
            Map.entry("minecraft:sulfur_caves", 636.0));

    /**
     * An Overworld monster's weight in the rest of its tag (a biome mod's biomes; every vanilla Overworld biome it lives
     * in is tuned). The list is taken to be vanilla's common one, and the weight is the one that brings the share there,
     * in mobs, to the target beside the every-biome mobs and the mobs that share a convention tag with it. A
     * {@code beside} weight of -1: one weight for the whole rest. Otherwise the rest that the spawn tag of one of
     * {@code besideMobs} also holds gets {@code beside}: the Hollow Mourner carries the share alone where no other
     * carrying mob lives and beside one where one does.
     */
    public record Rest(String mob, int weight, int beside, List<String> besideMobs) {}

    public static final List<Rest> REST = List.of(
            new Rest("hushwing", 10, -1, List.of()),
            new Rest("hollow_mourner", 355, 71, List.of("shiverwraith", "snapmaw_bloom", "skreel", "dustwhirl", "snagwood", "puffcap_shepherd", "rattlecoil", "snowroller", "gloamhound", "mycelite_shambler", "dripfang")),
            new Rest("snagwood", 266, -1, List.of()),
            new Rest("puffcap_shepherd", 218, -1, List.of()),
            new Rest("mire_gulper", 74, -1, List.of()),
            new Rest("dunefin", 24, -1, List.of()),
            new Rest("rattlecoil", 89, -1, List.of()),
            new Rest("snowroller", 177, -1, List.of()),
            new Rest("shiverwraith", 236, -1, List.of()),
            new Rest("gloamhound", 76, -1, List.of()),
            new Rest("vinedrop_strangler", 74, -1, List.of()),
            new Rest("snapmaw_bloom", 226, -1, List.of()),
            new Rest("stormeel", 1, -1, List.of()),
            new Rest("dripfang", 189, -1, List.of()),
            new Rest("mycelite_shambler", 118, -1, List.of()),
            new Rest("dustwhirl", 250, -1, List.of()),
            new Rest("skreel", 79, -1, List.of()));

    /** The rest weight of a mob: its row in {@link #REST}, else its roster weight. */
    public static Rest rest(String mob, int rosterWeight) {
        return REST.stream().filter(r -> r.mob().equals(mob)).findFirst().orElse(new Rest(mob, rosterWeight, -1, List.of()));
    }

    /**
     * A biome mod's End biomes ({@code #teeming:modded_end}, which every one of these mobs' spawn tags holds: by default
     * {@code #c:is_end} and {@code #minecraft:is_end}, so it holds vanilla's five End biomes as well, and every modifier
     * that reads it leaves them out) get every outer-island biome-list End mob at this weight, inside the mob's own tag.
     * Their terrain class is unknown, so the monsters are scaled together until their share, counted as mobs against
     * the enderman, is about {@link #MODDED_END_PLANNED_SHARE}; the creatures keep their roster weight.
     */
    public static final Map<String, Integer> MODDED_END = Map.ofEntries(
            Map.entry("astral_augur", 1),
            Map.entry("nullcantor", 1),
            Map.entry("levimoth", 1),
            Map.entry("warpseed_bloom", 1),
            Map.entry("glimmerless", 1),
            Map.entry("riftjackal", 1),
            Map.entry("chorus_husk", 1),
            Map.entry("gatecaster", 1),
            Map.entry("hollowmind", 1),
            Map.entry("pearlgulp", 1),
            Map.entry("brinkram", 1),
            Map.entry("facet_doppel", 1),
            Map.entry("shardling_colossus", 1),
            Map.entry("phasewalker", 1),
            Map.entry("echo_fencer", 1),
            Map.entry("swellshade", 1),
            Map.entry("chronomite", 1),
            Map.entry("vigil_owl", 1),
            Map.entry("anchor_golem", 1),
            Map.entry("pullstar", 1),
            Map.entry("void_bloom", 1),
            Map.entry("recurrent", 1),
            Map.entry("mnemovore", 1),
            Map.entry("rotfang", 1),
            Map.entry("gemini_watchers", 1),
            Map.entry("spirebloom", 1),
            Map.entry("riftmine_sapper", 1),
            Map.entry("exile_wraith", 1),
            Map.entry("mote_swarm", 1),
            Map.entry("resonant_triad", 1),
            Map.entry("chorus_grazer", 6),
            Map.entry("pearlshell_snail", 5));
    public static final double MODDED_END_PLANNED_SHARE = 0.4839;
    public static final List<String> VANILLA_END_BIOMES = List.of("minecraft:the_end", "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens", "minecraft:small_end_islands");

    /**
     * The vanilla biomes of {@code #teeming:share_capped}: biomes whose Teeming monsters only the spawner places. The tag
     * also holds biome mods' Nether forests, through {@code #c:is_nether_forest}, {@code #c:primary_wood_type/crimson}, {@code #c:primary_wood_type/warped}.
     */
    public static final List<String> SHARE_CAPPED = List.of("minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:deep_dark", "minecraft:mushroom_fields");

    /**
     * A mob's biome-list weight in one biome with a modifier of its own: a tuned biome, or a vanilla End biome of an outer
     * End creature's home (at its roster weight: its rest leaves {@code #teeming:modded_end} out, which holds vanilla's
     * End biomes). Elsewhere in its tag it has its rest weight ({@link #rest}).
     */
    public record Row(String mob, String biome, int weight) {}

    public static final List<Row> TUNED = List.of(
            new Row("snagwood", "minecraft:birch_forest", 266),
            new Row("snagwood", "minecraft:dappled_forest", 266),
            new Row("snagwood", "minecraft:flower_forest", 266),
            new Row("snagwood", "minecraft:forest", 266),
            new Row("snagwood", "minecraft:old_growth_birch_forest", 266),
            new Row("puffcap_shepherd", "minecraft:dark_forest", 152),
            new Row("mire_gulper", "minecraft:mangrove_swamp", 74),
            new Row("mire_gulper", "minecraft:swamp", 74),
            new Row("dunefin", "minecraft:badlands", 24),
            new Row("dunefin", "minecraft:eroded_badlands", 24),
            new Row("dunefin", "minecraft:wooded_badlands", 25),
            new Row("dunefin", "minecraft:desert", 180),
            new Row("rattlecoil", "minecraft:badlands", 89),
            new Row("rattlecoil", "minecraft:eroded_badlands", 89),
            new Row("rattlecoil", "minecraft:wooded_badlands", 55),
            new Row("rattlecoil", "minecraft:desert", 7),
            new Row("snowroller", "minecraft:grove", 177),
            new Row("snowroller", "minecraft:jagged_peaks", 118),
            new Row("snowroller", "minecraft:snowy_slopes", 177),
            new Row("shiverwraith", "minecraft:frozen_peaks", 236),
            new Row("shiverwraith", "minecraft:ice_spikes", 236),
            new Row("shiverwraith", "minecraft:snowy_plains", 234),
            new Row("gloamhound", "minecraft:old_growth_spruce_taiga", 76),
            new Row("gloamhound", "minecraft:snowy_taiga", 76),
            new Row("gloamhound", "minecraft:taiga", 76),
            new Row("gloamhound", "minecraft:old_growth_pine_taiga", 78),
            new Row("vinedrop_strangler", "minecraft:sparse_jungle", 75),
            new Row("vinedrop_strangler", "minecraft:bamboo_jungle", 75),
            new Row("vinedrop_strangler", "minecraft:jungle", 75),
            new Row("snapmaw_bloom", "minecraft:lush_caves", 118),
            new Row("snapmaw_bloom", "minecraft:sparse_jungle", 236),
            new Row("snapmaw_bloom", "minecraft:bamboo_jungle", 236),
            new Row("snapmaw_bloom", "minecraft:jungle", 236),
            new Row("stormeel", "minecraft:deep_lukewarm_ocean", 1),
            new Row("stormeel", "minecraft:lukewarm_ocean", 1),
            new Row("stormeel", "minecraft:warm_ocean", 1),
            new Row("dripfang", "minecraft:dripstone_caves", 118),
            new Row("hushwing", "minecraft:badlands", 20),
            new Row("hushwing", "minecraft:beach", 21),
            new Row("hushwing", "minecraft:birch_forest", 21),
            new Row("hushwing", "minecraft:cherry_grove", 21),
            new Row("hushwing", "minecraft:dappled_forest", 21),
            new Row("hushwing", "minecraft:dark_forest", 21),
            new Row("hushwing", "minecraft:eroded_badlands", 20),
            new Row("hushwing", "minecraft:flower_forest", 21),
            new Row("hushwing", "minecraft:forest", 21),
            new Row("hushwing", "minecraft:frozen_peaks", 21),
            new Row("hushwing", "minecraft:grove", 21),
            new Row("hushwing", "minecraft:jagged_peaks", 21),
            new Row("hushwing", "minecraft:lush_caves", 21),
            new Row("hushwing", "minecraft:meadow", 21),
            new Row("hushwing", "minecraft:old_growth_birch_forest", 21),
            new Row("hushwing", "minecraft:old_growth_spruce_taiga", 21),
            new Row("hushwing", "minecraft:pale_garden", 21),
            new Row("hushwing", "minecraft:snowy_beach", 21),
            new Row("hushwing", "minecraft:snowy_slopes", 21),
            new Row("hushwing", "minecraft:snowy_taiga", 21),
            new Row("hushwing", "minecraft:sparse_jungle", 21),
            new Row("hushwing", "minecraft:stony_peaks", 21),
            new Row("hushwing", "minecraft:stony_shore", 21),
            new Row("hushwing", "minecraft:taiga", 21),
            new Row("hushwing", "minecraft:windswept_forest", 21),
            new Row("hushwing", "minecraft:windswept_gravelly_hills", 21),
            new Row("hushwing", "minecraft:windswept_hills", 21),
            new Row("hushwing", "minecraft:wooded_badlands", 21),
            new Row("hushwing", "minecraft:cold_ocean", 21),
            new Row("hushwing", "minecraft:deep_cold_ocean", 21),
            new Row("hushwing", "minecraft:deep_frozen_ocean", 21),
            new Row("hushwing", "minecraft:deep_lukewarm_ocean", 21),
            new Row("hushwing", "minecraft:deep_ocean", 21),
            new Row("hushwing", "minecraft:frozen_ocean", 21),
            new Row("hushwing", "minecraft:lukewarm_ocean", 21),
            new Row("hushwing", "minecraft:ocean", 21),
            new Row("hushwing", "minecraft:warm_ocean", 21),
            new Row("hushwing", "minecraft:plains", 21),
            new Row("hushwing", "minecraft:savanna", 21),
            new Row("hushwing", "minecraft:savanna_plateau", 21),
            new Row("hushwing", "minecraft:sunflower_plains", 21),
            new Row("hushwing", "minecraft:windswept_savanna", 21),
            new Row("hushwing", "minecraft:bamboo_jungle", 21),
            new Row("hushwing", "minecraft:jungle", 21),
            new Row("hushwing", "minecraft:mangrove_swamp", 21),
            new Row("hushwing", "minecraft:swamp", 21),
            new Row("hushwing", "minecraft:desert", 21),
            new Row("hushwing", "minecraft:dripstone_caves", 21),
            new Row("hushwing", "minecraft:frozen_river", 21),
            new Row("hushwing", "minecraft:ice_spikes", 21),
            new Row("hushwing", "minecraft:old_growth_pine_taiga", 21),
            new Row("hushwing", "minecraft:river", 21),
            new Row("hushwing", "minecraft:snowy_plains", 21),
            new Row("hushwing", "minecraft:sulfur_caves", 8),
            new Row("mycelite_shambler", "minecraft:dark_forest", 152),
            new Row("mycelite_shambler", "minecraft:lush_caves", 177),
            new Row("mycelite_shambler", "minecraft:dripstone_caves", 133),
            new Row("hollow_mourner", "minecraft:badlands", 89),
            new Row("hollow_mourner", "minecraft:beach", 355),
            new Row("hollow_mourner", "minecraft:birch_forest", 89),
            new Row("hollow_mourner", "minecraft:cherry_grove", 355),
            new Row("hollow_mourner", "minecraft:dappled_forest", 89),
            new Row("hollow_mourner", "minecraft:dark_forest", 51),
            new Row("hollow_mourner", "minecraft:eroded_badlands", 89),
            new Row("hollow_mourner", "minecraft:flower_forest", 89),
            new Row("hollow_mourner", "minecraft:forest", 89),
            new Row("hollow_mourner", "minecraft:frozen_peaks", 118),
            new Row("hollow_mourner", "minecraft:grove", 89),
            new Row("hollow_mourner", "minecraft:jagged_peaks", 59),
            new Row("hollow_mourner", "minecraft:lush_caves", 59),
            new Row("hollow_mourner", "minecraft:meadow", 355),
            new Row("hollow_mourner", "minecraft:old_growth_birch_forest", 89),
            new Row("hollow_mourner", "minecraft:old_growth_spruce_taiga", 89),
            new Row("hollow_mourner", "minecraft:pale_garden", 355),
            new Row("hollow_mourner", "minecraft:snowy_beach", 355),
            new Row("hollow_mourner", "minecraft:snowy_slopes", 89),
            new Row("hollow_mourner", "minecraft:snowy_taiga", 89),
            new Row("hollow_mourner", "minecraft:sparse_jungle", 118),
            new Row("hollow_mourner", "minecraft:stony_peaks", 118),
            new Row("hollow_mourner", "minecraft:stony_shore", 355),
            new Row("hollow_mourner", "minecraft:taiga", 89),
            new Row("hollow_mourner", "minecraft:windswept_forest", 118),
            new Row("hollow_mourner", "minecraft:windswept_gravelly_hills", 118),
            new Row("hollow_mourner", "minecraft:windswept_hills", 118),
            new Row("hollow_mourner", "minecraft:wooded_badlands", 55),
            new Row("hollow_mourner", "minecraft:cold_ocean", 355),
            new Row("hollow_mourner", "minecraft:deep_cold_ocean", 355),
            new Row("hollow_mourner", "minecraft:deep_frozen_ocean", 355),
            new Row("hollow_mourner", "minecraft:deep_lukewarm_ocean", 355),
            new Row("hollow_mourner", "minecraft:deep_ocean", 355),
            new Row("hollow_mourner", "minecraft:frozen_ocean", 355),
            new Row("hollow_mourner", "minecraft:lukewarm_ocean", 355),
            new Row("hollow_mourner", "minecraft:ocean", 355),
            new Row("hollow_mourner", "minecraft:warm_ocean", 355),
            new Row("hollow_mourner", "minecraft:plains", 351),
            new Row("hollow_mourner", "minecraft:savanna", 103),
            new Row("hollow_mourner", "minecraft:savanna_plateau", 103),
            new Row("hollow_mourner", "minecraft:sunflower_plains", 351),
            new Row("hollow_mourner", "minecraft:windswept_savanna", 103),
            new Row("hollow_mourner", "minecraft:bamboo_jungle", 118),
            new Row("hollow_mourner", "minecraft:jungle", 118),
            new Row("hollow_mourner", "minecraft:mangrove_swamp", 355),
            new Row("hollow_mourner", "minecraft:swamp", 355),
            new Row("hollow_mourner", "minecraft:desert", 67),
            new Row("hollow_mourner", "minecraft:dripstone_caves", 44),
            new Row("hollow_mourner", "minecraft:frozen_river", 355),
            new Row("hollow_mourner", "minecraft:ice_spikes", 118),
            new Row("hollow_mourner", "minecraft:old_growth_pine_taiga", 91),
            new Row("hollow_mourner", "minecraft:river", 355),
            new Row("hollow_mourner", "minecraft:snowy_plains", 117),
            new Row("hollow_mourner", "minecraft:sulfur_caves", 140),
            new Row("dustwhirl", "minecraft:wooded_badlands", 133),
            new Row("dustwhirl", "minecraft:savanna", 248),
            new Row("dustwhirl", "minecraft:savanna_plateau", 248),
            new Row("dustwhirl", "minecraft:windswept_savanna", 248),
            new Row("skreel", "minecraft:jagged_peaks", 39),
            new Row("skreel", "minecraft:stony_peaks", 79),
            new Row("skreel", "minecraft:windswept_forest", 79),
            new Row("skreel", "minecraft:windswept_gravelly_hills", 79),
            new Row("skreel", "minecraft:windswept_hills", 79),
            new Row("prismback", "minecraft:nether_wastes", 9),
            new Row("slagspitter", "minecraft:nether_wastes", 14),
            new Row("slagspitter", "minecraft:basalt_deltas", 15),
            new Row("ash_jackal", "minecraft:nether_wastes", 8),
            new Row("ash_jackal", "minecraft:soul_sand_valley", 2),
            new Row("slagheart_golem", "minecraft:nether_wastes", 8),
            new Row("scarlet_mantis", "minecraft:nether_wastes", 14),
            new Row("ossuary_wyrm", "minecraft:soul_sand_valley", 1),
            new Row("keening_shade", "minecraft:soul_sand_valley", 3),
            new Row("soulfire_seer", "minecraft:soul_sand_valley", 3),
            new Row("cindermoth_swarm", "minecraft:nether_wastes", 17),
            new Row("cindermoth_swarm", "minecraft:soul_sand_valley", 4),
            new Row("soulward_revenant", "minecraft:soul_sand_valley", 2),
            new Row("quakehopper", "minecraft:basalt_deltas", 12),
            new Row("ashharrier", "minecraft:basalt_deltas", 15),
            new Row("lavaleaper", "minecraft:nether_wastes", 9),
            new Row("lavaleaper", "minecraft:soul_sand_valley", 2),
            new Row("lavaleaper", "minecraft:basalt_deltas", 10),
            new Row("blackstone_scuttler", "minecraft:basalt_deltas", 10),
            new Row("aurovore", "minecraft:nether_wastes", 8),
            new Row("astral_augur", "minecraft:end_highlands", 2),
            new Row("nullcantor", "minecraft:end_barrens", 3),
            new Row("chorus_grazer", "minecraft:end_highlands", 6),
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
            new Row("pearlshell_snail", "minecraft:small_end_islands", 5),
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
