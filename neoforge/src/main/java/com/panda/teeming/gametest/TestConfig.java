package com.panda.teeming.gametest;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.neoforge.NeoForgeConfig;
import java.util.function.Consumer;

/** The loader's settings with some changed, for one test body; the loader's own come back before it returns. */
final class TestConfig implements TeemingConfig.Source {
    private final NeoForgeConfig loader = new NeoForgeConfig();
    int localCap = loader.localCap();
    double trophyChance = loader.trophyChance();

    private TestConfig() {}

    /** Runs {@code body} with the settings {@code change} makes, within the same tick, so no other test sees them. */
    static void run(Consumer<TestConfig> change, Runnable body) {
        TestConfig config = new TestConfig();
        change.accept(config);
        TeemingConfig.use(config);
        try {
            body.run();
        } finally {
            TeemingConfig.use(config.loader);
        }
    }

    @Override public double spawningDensity() { return loader.spawningDensity(); }
    @Override public boolean mobEnabled(String id) { return loader.mobEnabled(id); }
    @Override public double spawnChance(String id) { return loader.spawnChance(id); }
    @Override public boolean endMainIslandAfterDragon() { return loader.endMainIslandAfterDragon(); }
    @Override public int killsToReveal() { return loader.killsToReveal(); }
    @Override public boolean temporaryBlocks() { return loader.temporaryBlocks(); }
    @Override public int localCap() { return localCap; }
    @Override public double trophyChance() { return trophyChance; }
}
