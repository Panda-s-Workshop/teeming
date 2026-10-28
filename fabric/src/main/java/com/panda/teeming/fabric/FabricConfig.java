package com.panda.teeming.fabric;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.roster.RosterStats;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's settings on Fabric: config/teeming.properties, read once at startup (a change needs a restart). Keys the
 * file lacks, a new version's or all of them on first start, are added with their defaults; a value that can't be read
 * is logged and the default used. Only the server acts on them.
 */
final class FabricConfig implements TeemingConfig.Source {
    private static final String HEADER = """
             Teeming (read at startup: restart after a change)
             spawningDensity: 0..4; below 1 each natural spawn that passed every other rule goes ahead with this chance (0 turns
               them off); above 1 it only raises the local caps (spawning.local_cap times it, apex mobs and giants included),
               and how often a Teeming mob is picked, and its share of each biome's spawns, stay the same
             endMainIslandAfterDragon: true keeps the main island's End mobs away until the dragon has been killed
             fieldguide.killsToReveal: 1..100, kills of a mob that reveal its tell, counter, drops and looks in the Field Guide
             griefing.temporary_blocks: false stops every mob placing even temporary blocks (the Gloomweaver raises no walls)
             spawning.local_cap: 1..16, at most this many of one Teeming mob type within 48 blocks (apex mobs and giants: 1 in 128;
               both times spawningDensity when it is above 1)
             trophies.chance: 0..1, the chance a player kill of an ordinary Teeming mob drops its trophy (apex mobs four times it)
             mobs.<id>.enabled: false stops that mob's natural spawns (eggs and commands still work)
             mobs.<id>.spawnChance: 0..1, the chance a natural spawn that passed every other rule goes ahead""";

    private final double spawningDensity;
    private final boolean endMainIslandAfterDragon;
    private final int killsToReveal;
    private final boolean temporaryBlocks;
    private final int localCap;
    private final double trophyChance;
    private final Map<String, Boolean> enabled = new HashMap<>();
    private final Map<String, Double> chance = new HashMap<>();

    private FabricConfig(Properties p) {
        TeemingConfig.Source d = TeemingConfig.DEFAULTS;
        spawningDensity = number(p, "spawningDensity", d.spawningDensity(), 0.0, 4.0);
        endMainIslandAfterDragon = bool(p, "endMainIslandAfterDragon", d.endMainIslandAfterDragon());
        killsToReveal = (int) number(p, "fieldguide.killsToReveal", d.killsToReveal(), 1, 100);
        temporaryBlocks = bool(p, "griefing.temporary_blocks", d.temporaryBlocks());
        localCap = (int) number(p, "spawning.local_cap", d.localCap(), 1, 16);
        trophyChance = number(p, "trophies.chance", d.trophyChance(), 0.0, 1.0);
        for (RosterStats.Entry mob : RosterStats.ALL) {
            enabled.put(mob.id(), bool(p, mobKey(mob, "enabled"), d.mobEnabled(mob.id())));
            chance.put(mob.id(), number(p, mobKey(mob, "spawnChance"), d.spawnChance(mob.id()), 0.0, 1.0));
        }
    }

    /** Every key with its default, in the file's order. */
    private static Map<String, String> defaults() {
        TeemingConfig.Source d = TeemingConfig.DEFAULTS;
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("spawningDensity", String.valueOf(d.spawningDensity()));
        keys.put("endMainIslandAfterDragon", String.valueOf(d.endMainIslandAfterDragon()));
        keys.put("fieldguide.killsToReveal", String.valueOf(d.killsToReveal()));
        keys.put("griefing.temporary_blocks", String.valueOf(d.temporaryBlocks()));
        keys.put("spawning.local_cap", String.valueOf(d.localCap()));
        keys.put("trophies.chance", String.valueOf(d.trophyChance()));
        for (RosterStats.Entry mob : RosterStats.ALL) {
            keys.put("mobs." + mob.id() + ".enabled", String.valueOf(d.mobEnabled(mob.id())));
            keys.put("mobs." + mob.id() + ".spawnChance", String.valueOf(d.spawnChance(mob.id())));
        }
        return keys;
    }

    /** Per-mob keys are {@code mobs.<id>.*}, as on NeoForge; a file written before that used {@code mob.<id>.*}. */
    private static String mobKey(RosterStats.Entry mob, String name) {
        return "mobs." + mob.id() + "." + name;
    }

    static FabricConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("teeming.properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file)) {
                p.load(in);
            } catch (IOException e) {
                TeemingMod.LOGGER.warn("Couldn't read {}, using the defaults", file, e);
            }
        }
        boolean added = false;
        for (RosterStats.Entry mob : RosterStats.ALL) {                    // carry an old file's per-mob values over
            for (String name : new String[] {"enabled", "spawnChance"}) {
                String old = p.getProperty("mob." + mob.id() + "." + name);
                if (old == null) continue;
                p.remove("mob." + mob.id() + "." + name);
                p.putIfAbsent(mobKey(mob, name), old);
                added = true;
            }
        }
        for (Map.Entry<String, String> key : defaults().entrySet()) {
            if (p.putIfAbsent(key.getKey(), key.getValue()) == null) added = true;
        }
        if (added) {
            try (Writer out = Files.newBufferedWriter(file)) {
                p.store(out, HEADER);
            } catch (IOException e) {
                TeemingMod.LOGGER.warn("Couldn't write {}", file, e);
            }
        }
        return new FabricConfig(p);
    }

    private static double number(Properties p, String key, double fallback, double min, double max) {
        String value = p.getProperty(key);
        if (value == null) return fallback;
        try {
            return Math.clamp(Double.parseDouble(value.trim()), min, max);
        } catch (NumberFormatException e) {
            TeemingMod.LOGGER.warn("teeming.properties: {} = \"{}\" is not a number; using {}", key, value, fallback);
            return fallback;
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        if (value == null) return fallback;
        String v = value.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        TeemingMod.LOGGER.warn("teeming.properties: {} = \"{}\" is neither true nor false; using {}", key, value, fallback);
        return fallback;
    }

    @Override
    public double spawningDensity() {
        return spawningDensity;
    }

    @Override
    public boolean endMainIslandAfterDragon() {
        return endMainIslandAfterDragon;
    }

    @Override
    public int killsToReveal() {
        return killsToReveal;
    }

    @Override
    public boolean temporaryBlocks() {
        return temporaryBlocks;
    }

    @Override
    public int localCap() {
        return localCap;
    }

    @Override
    public double trophyChance() {
        return trophyChance;
    }

    @Override
    public boolean mobEnabled(String id) {
        return enabled.getOrDefault(id, true);
    }

    @Override
    public double spawnChance(String id) {
        return chance.getOrDefault(id, 1.0);
    }
}
