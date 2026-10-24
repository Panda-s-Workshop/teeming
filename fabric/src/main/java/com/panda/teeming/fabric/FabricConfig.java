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
    private final Map<String, Boolean> enabled = new HashMap<>();
    private final Map<String, Double> chance = new HashMap<>();

    private FabricConfig(Properties p) {
        TeemingConfig.Source d = TeemingConfig.DEFAULTS;
        spawningDensity = clamp(p.getProperty("spawningDensity"), d.spawningDensity(), 0.0, 4.0);
        String gate = p.getProperty("endMainIslandAfterDragon");
        endMainIslandAfterDragon = gate == null ? d.endMainIslandAfterDragon() : !gate.trim().equalsIgnoreCase("false");
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
    public boolean mobEnabled(String id) {
        return enabled.getOrDefault(id, true);
    }

    @Override
    public double spawnChance(String id) {
        return chance.getOrDefault(id, 1.0);
    }
}
