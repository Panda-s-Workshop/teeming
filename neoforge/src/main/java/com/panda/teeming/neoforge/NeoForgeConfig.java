package com.panda.teeming.neoforge;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.roster.RosterStats;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The mod's settings as a NeoForge synced config (the server acts on them; clients get a copy). */
public final class NeoForgeConfig implements TeemingConfig.Source {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue SPAWNING_DENSITY = B
            .comment("Multiplies how often every Teeming mob spawns (0 turns natural spawning off, 4 is the most).",
                    "Below 1 it thins natural spawns; above 1 it raises the local caps.")
            .defineInRange("spawningDensity", 1.0, 0.0, 4.0);

    private static final ModConfigSpec.BooleanValue END_MAIN_ISLAND_AFTER_DRAGON = B
            .comment("True: End mobs of the main island appear only after the dragon has been killed.")
            .define("endMainIslandAfterDragon", true);

    private static final ModConfigSpec.IntValue KILLS_TO_REVEAL = B
            .comment("Kills of a mob that reveal its tell, counter, drops and looks in the Field Guide.")
            .defineInRange("fieldguide.killsToReveal", 3, 1, 100);

    private static final ModConfigSpec.BooleanValue TEMPORARY_BLOCKS = B
            .comment("False: no mob places even temporary blocks (the Gloomweaver raises no walls and only bites).")
            .define("griefing.temporary_blocks", true);

    private static final Map<String, ModConfigSpec.BooleanValue> ENABLED = new HashMap<>();
    private static final Map<String, ModConfigSpec.DoubleValue> CHANCE = new HashMap<>();

    static {
        B.comment("Per mob: enabled (false stops natural spawns; eggs and commands still work) and spawnChance (0..1).").push("mobs");
        for (RosterStats.Entry mob : RosterStats.ALL) {
            B.push(mob.id());
            ENABLED.put(mob.id(), B.define("enabled", true));
            CHANCE.put(mob.id(), B.defineInRange("spawnChance", 1.0, 0.0, 1.0));
            B.pop();
        }
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    @Override
    public double spawningDensity() {
        return SPEC.isLoaded() ? SPAWNING_DENSITY.get() : TeemingConfig.DEFAULTS.spawningDensity();
    }

    @Override
    public boolean endMainIslandAfterDragon() {
        return SPEC.isLoaded() ? END_MAIN_ISLAND_AFTER_DRAGON.get() : TeemingConfig.DEFAULTS.endMainIslandAfterDragon();
    }

    @Override
    public int killsToReveal() {
        return SPEC.isLoaded() ? KILLS_TO_REVEAL.get() : TeemingConfig.DEFAULTS.killsToReveal();
    }

    @Override
    public boolean temporaryBlocks() {
        return SPEC.isLoaded() ? TEMPORARY_BLOCKS.get() : TeemingConfig.DEFAULTS.temporaryBlocks();
    }

    @Override
    public boolean mobEnabled(String id) {
        var value = ENABLED.get(id);
        return value == null || !SPEC.isLoaded() ? TeemingConfig.DEFAULTS.mobEnabled(id) : value.get();
    }

    @Override
    public double spawnChance(String id) {
        var value = CHANCE.get(id);
        return value == null || !SPEC.isLoaded() ? TeemingConfig.DEFAULTS.spawnChance(id) : value.get();
    }
}
