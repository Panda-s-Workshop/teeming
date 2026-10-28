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

        /** At most this many of one Teeming mob type within 48 blocks (1..16); apex mobs and giants stay at 1 in 128. */
        int localCap();

        /** 0..1, the chance a player kill of an ordinary Teeming mob drops its trophy; apex chances and Looting scale with it. */
        double trophyChance();
    }

    public static final int DEFAULT_LOCAL_CAP = 3;
    public static final double DEFAULT_TROPHY_CHANCE = 0.025;

    public static final Source DEFAULTS = new Source() {
        @Override public double spawningDensity() { return 1.0; }
        @Override public boolean mobEnabled(String id) { return true; }
        @Override public double spawnChance(String id) { return 1.0; }
        @Override public boolean endMainIslandAfterDragon() { return true; }
        @Override public int killsToReveal() { return 3; }
        @Override public boolean temporaryBlocks() { return true; }
        @Override public int localCap() { return DEFAULT_LOCAL_CAP; }
        @Override public double trophyChance() { return DEFAULT_TROPHY_CHANCE; }
    };

    private static Source source = DEFAULTS;

    private TeemingConfig() {}

    public static void use(Source loaderConfig) {
        source = loaderConfig;
    }

    /** The source in use, so a test can wrap it and put it back. */
    public static Source source() {
        return source;
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

    public static int localCap() {
        return Math.clamp(source.localCap(), 1, 16);
    }

    /** How many times the default trophy chance the config asks for (1 at the default). */
    public static double trophyScale() {
        return Math.clamp(source.trophyChance(), 0.0, 1.0) / DEFAULT_TROPHY_CHANCE;
    }
}
