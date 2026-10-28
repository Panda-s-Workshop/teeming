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
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's settings on Fabric: config/teeming.properties, written with the defaults on first start and read once at
 * startup. Only the server acts on them.
 */
final class FabricConfig implements TeemingConfig.Source {
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
        spawningDensity = clamp(p.getProperty("spawningDensity"), d.spawningDensity(), 0.0, 4.0);
        String gate = p.getProperty("endMainIslandAfterDragon");
        endMainIslandAfterDragon = gate == null ? d.endMainIslandAfterDragon() : !gate.trim().equalsIgnoreCase("false");
        killsToReveal = (int) clamp(p.getProperty("fieldguide.killsToReveal"), d.killsToReveal(), 1, 100);
        String temporary = p.getProperty("griefing.temporary_blocks");
        temporaryBlocks = temporary == null ? d.temporaryBlocks() : !temporary.trim().equalsIgnoreCase("false");
        localCap = (int) clamp(p.getProperty("spawning.local_cap"), d.localCap(), 1, 16);
        trophyChance = clamp(p.getProperty("trophies.chance"), d.trophyChance(), 0.0, 1.0);
        for (RosterStats.Entry mob : RosterStats.ALL) {
            String on = p.getProperty("mob." + mob.id() + ".enabled");
            enabled.put(mob.id(), on == null ? d.mobEnabled(mob.id()) : !on.trim().equalsIgnoreCase("false"));
            chance.put(mob.id(), clamp(p.getProperty("mob." + mob.id() + ".spawnChance"), d.spawnChance(mob.id()), 0.0, 1.0));
        }
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
        } else {
            TeemingConfig.Source d = TeemingConfig.DEFAULTS;
            p.setProperty("spawningDensity", String.valueOf(d.spawningDensity()));
            p.setProperty("endMainIslandAfterDragon", String.valueOf(d.endMainIslandAfterDragon()));
            p.setProperty("fieldguide.killsToReveal", String.valueOf(d.killsToReveal()));
            p.setProperty("griefing.temporary_blocks", String.valueOf(d.temporaryBlocks()));
            p.setProperty("spawning.local_cap", String.valueOf(d.localCap()));
            p.setProperty("trophies.chance", String.valueOf(d.trophyChance()));
            for (RosterStats.Entry mob : RosterStats.ALL) {
                p.setProperty("mob." + mob.id() + ".enabled", String.valueOf(d.mobEnabled(mob.id())));
                p.setProperty("mob." + mob.id() + ".spawnChance", String.valueOf(d.spawnChance(mob.id())));
            }
            try (Writer out = Files.newBufferedWriter(file)) {
                p.store(out, """
                         Teeming
                         spawningDensity: multiplies how often every Teeming mob spawns (0 turns natural spawning off, 4 is the most;
                           below 1 it thins natural spawns, above 1 it raises the local caps)
                         endMainIslandAfterDragon: true keeps the main island's End mobs away until the dragon has been killed
                         fieldguide.killsToReveal: 1..100, kills of a mob that reveal its tell, counter, drops and looks in the Field Guide
                         griefing.temporary_blocks: false stops every mob placing even temporary blocks (the Gloomweaver raises no walls)
                         spawning.local_cap: 1..16, at most this many of one Teeming mob type within 48 blocks (apex mobs and giants: 1 in 128)
                         trophies.chance: 0..1, the chance a player kill of an ordinary Teeming mob drops its trophy (apex mobs four times it)
                         mob.<id>.enabled: false stops that mob's natural spawns (eggs and commands still work)
                         mob.<id>.spawnChance: 0..1, the chance a natural spawn that passed every other rule goes ahead""");
            } catch (IOException e) {
                TeemingMod.LOGGER.warn("Couldn't write {}", file, e);
            }
        }
        return new FabricConfig(p);
    }

    private static double clamp(String value, double fallback, double min, double max) {
        if (value == null) return fallback;
        try {
            return Math.clamp(Double.parseDouble(value.trim()), min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
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
