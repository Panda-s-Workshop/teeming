package com.panda.teeming.fabric;

import com.panda.teeming.SettingsFile;
import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.roster.RosterStats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's settings on Fabric: config/teeming.properties, read once at startup (a change needs a restart). Keys the
 * file lacks, a new version's or all of them on first start, are added with their defaults, and the header is the
 * current one; the file keeps its values, the comments above its keys and keys it doesn't know ({@link SettingsFile}).
 * A value that can't be read is logged and the default used; one out of range is logged and brought into it. Only the
 * server acts on them.
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
        Optional<String> read = SettingsFile.readFile(file);
        // a file that can't be read is left as it is: the defaults run this session, and nothing is written over it
        if (read.isEmpty()) TeemingMod.LOGGER.warn("Couldn't read {}, using the defaults and leaving the file as it is", file);
        String old = read.orElse("");
        Map<String, String> renamed = new LinkedHashMap<>();
        for (RosterStats.Entry mob : RosterStats.ALL) {                    // an old file's per-mob keys
            for (String name : new String[] {"enabled", "spawnChance"}) renamed.put("mob." + mob.id() + "." + name, mobKey(mob, name));
        }
        // rewritten whenever the text changes (a new key, a renamed one, a new header), keeping values, comments and unknown keys
        String text = SettingsFile.write(old, HEADER, defaults(), renamed);
        if (read.isPresent() && !text.equals(old)) {
            try {
                Files.writeString(file, text);
            } catch (IOException e) {
                TeemingMod.LOGGER.warn("Couldn't write {}", file, e);
            }
        }
        return new FabricConfig(SettingsFile.read(text));
    }

    private static double number(Properties p, String key, double fallback, double min, double max) {
        String value = p.getProperty(key);
        if (value == null) return fallback;
        double read;
        try {
            read = Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            read = Double.NaN;
        }
        if (!Double.isFinite(read)) {                                        // NaN and the infinities clamp to nothing useful
            TeemingMod.LOGGER.warn("teeming.properties: {} = \"{}\" is not a number; using {}", key, value, fallback);
            return fallback;
        }
        double kept = Math.clamp(read, min, max);
        if (kept != read) TeemingMod.LOGGER.warn("teeming.properties: {} = {} is outside {} to {}; using {}", key, value.trim(), num(min), num(max), num(kept));
        return kept;
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
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
