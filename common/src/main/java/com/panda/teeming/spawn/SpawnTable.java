package com.panda.teeming.spawn;

import java.util.List;
import java.util.Map;

/**
 * Teeming's share of the Nether's monster spawns. Vanilla's own lists stay as they are: Teeming adds about
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
     * The End aims at about half of all monsters, counted as mobs (endermen come four at a time). The cap the spawner
     * keeps there counts Teeming monsters alone, against this share of the vanilla per-player cap, so the endermen that
     * fill that cap round the main island don't shut Teeming out. No End biome's lists may go past the most.
     */
    public static final double END_SHARE = 0.5;
    public static final double END_SHARE_MAX = 0.6;

    /** Vanilla's monster weight in each End biome: the enderman alone, weight 10 in groups of 4. */
    public static final Map<String, Integer> VANILLA_END_MONSTER_WEIGHT = Map.of(
            "minecraft:the_end", 10,
            "minecraft:end_highlands", 10,
            "minecraft:end_midlands", 10,
            "minecraft:end_barrens", 10,
            "minecraft:small_end_islands", 10);

    /** The default contents of {@code #teeming:share_capped}: biomes whose Teeming monsters only the spawner places. */
    public static final List<String> SHARE_CAPPED = List.of("minecraft:crimson_forest", "minecraft:warped_forest");

    /** A mob's biome-list weight in one tuned biome; elsewhere in its tag it keeps its roster weight. */
    public record Row(String mob, String biome, int weight) {}

    public static final List<Row> TUNED = List.of(
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
            new Row("aurovore", "minecraft:nether_wastes", 3));

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
