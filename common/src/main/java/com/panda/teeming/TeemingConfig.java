package com.panda.teeming;

/** The mod's settings. Each loader keeps them in its own config system and plugs it in with {@link #use}. */
public final class TeemingConfig {
    /** Where the values come from. The defaults apply until the loader's config is loaded. */
    public interface Source {
        /** Multiplies how often every Teeming mob spawns (0 turns natural spawning off, 4 is the most). */
        double spawningDensity();

        /** Per mob: false stops its natural spawns (spawn eggs and commands still work). */
        boolean mobEnabled(String id);

        /** Per mob: 0..1, the chance a natural spawn attempt that passed every other rule goes ahead. */
        double spawnChance(String id);

        /** True: End mobs of the main island appear only after the dragon has been killed. */
        boolean endMainIslandAfterDragon();

        /** Kills of a mob that reveal its tell, counter, drops and looks in the Field Guide (1..100). */
        int killsToReveal();

        /** False: no mob places even temporary blocks (the Gloomweaver raises no walls and only bites). */
        boolean temporaryBlocks();
    }

    public static final Source DEFAULTS = new Source() {
        @Override public double spawningDensity() { return 1.0; }
        @Override public boolean mobEnabled(String id) { return true; }
        @Override public double spawnChance(String id) { return 1.0; }
        @Override public boolean endMainIslandAfterDragon() { return true; }
        @Override public int killsToReveal() { return 3; }
        @Override public boolean temporaryBlocks() { return true; }
    };

    private static Source source = DEFAULTS;

    private TeemingConfig() {}

    public static void use(Source loaderConfig) {
        source = loaderConfig;
    }

    public static double spawningDensity() {
        return source.spawningDensity();
    }

    public static boolean mobEnabled(String id) {
        return source.mobEnabled(id);
    }

    public static double spawnChance(String id) {
        return source.spawnChance(id);
    }

    public static boolean endMainIslandAfterDragon() {
        return source.endMainIslandAfterDragon();
    }

    public static int killsToReveal() {
        return Math.clamp(source.killsToReveal(), 1, 100);
    }

    public static boolean temporaryBlocks() {
        return source.temporaryBlocks();
    }
}
