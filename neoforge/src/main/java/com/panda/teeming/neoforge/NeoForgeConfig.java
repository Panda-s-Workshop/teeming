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
            .comment("0 to 4. Below 1 it thins natural spawns: each one that passed every other rule goes ahead with this chance (0 turns them off).",
                    "Above 1 it only raises the local caps (spawning.local_cap times it, apex mobs and giants included);",
                    "how often a Teeming mob is picked, and its share of each biome's spawns, stay the same.")
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

    private static final ModConfigSpec.IntValue LOCAL_CAP = B
            .comment("At most this many of one Teeming mob type within 48 blocks; apex mobs and giants 1 in 128. Both times spawningDensity above 1.")
            .defineInRange("spawning.local_cap", TeemingConfig.DEFAULT_LOCAL_CAP, 1, 16);

    private static final ModConfigSpec.DoubleValue TROPHY_CHANCE = B
            .comment("The chance a player kill of an ordinary Teeming mob drops its trophy (apex mobs: four times it).",
                    "Looting raises it in proportion.")
            .defineInRange("trophies.chance", TeemingConfig.DEFAULT_TROPHY_CHANCE, 0.0, 1.0);

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
    public int localCap() {
        return SPEC.isLoaded() ? LOCAL_CAP.get() : TeemingConfig.DEFAULTS.localCap();
    }

    @Override
    public double trophyChance() {
        return SPEC.isLoaded() ? TROPHY_CHANCE.get() : TeemingConfig.DEFAULTS.trophyChance();
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
