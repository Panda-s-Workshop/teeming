package com.panda.teeming.roster;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Every mob's numbers, names, looks and homes. Plain Java with no Minecraft types, so JUnit can read it. */
public final class RosterStats {
    private RosterStats() {}

    public enum Dim { OVERWORLD, NETHER, END }

    /** MONSTER hostile; NEUTRAL attacks when provoked or warned; CREATURE passive; CREATURE_NEUTRAL passive until hit. */
    public enum Cat { MONSTER, NEUTRAL, CREATURE, CREATURE_NEUTRAL }

    /**
     * @param blow          strongest mechanic blow, 0 = none
     * @param balanceHealth the health {@code BalanceTest} uses (grown, split or paired mobs differ from {@code health})
     * @param weight        biome spawn weight, 0 = placed by the Teeming spawner only
     * @param secondLook    id of the second look, "" for apex mobs
     * @param material      id of the signature material, "" when it has none
     * @param maxY          highest natural spawn y, {@code Integer.MAX_VALUE} = no limit
     * @param spawner       the Teeming spawner's rule for this mob, "" = none
     */
    public record Entry(int number, String id, Dim dim, Cat cat, boolean apex, float health, float attack, float armor,
                        float width, float height, int tellTicks, float blow, float balanceHealth, float balanceAttack,
                        int weight, int groupMin, int groupMax, String secondLook, String material, Place place, boolean anyLight,
                        int maxY, String spawner) {
        /** Natural spawns come from biome spawn lists (a weight and a non-empty biome tag). */
        public boolean biomeSpawns() {
            return weight > 0 && !place.equals(Place.NONE);
        }

        public boolean passive() {
            return cat == Cat.CREATURE || cat == Cat.CREATURE_NEUTRAL;
        }

        /** The look ids, base first. Known on both sides (look data itself is server data). */
        public List<String> looks() {
            return secondLook.isEmpty() ? List.of("base") : List.of("base", secondLook);
        }
    }

    /** Where a spawn position is valid; NONE = no biome spawns, only the Teeming spawner places it. */
    public enum Place { GROUND, WATER, SHALLOW, LAVA, SAND, LEAVES, CEILING, AIR, NONE }

    private static Entry e(int number, String id, Dim dim, Cat cat, boolean apex, float health, float attack, float armor,
                           float width, float height, int tellTicks, float blow, float balanceHealth, float balanceAttack,
                           int weight, int groupMin, int groupMax, String secondLook, String material, Place place, boolean anyLight,
                           int maxY, String spawner) {
        return new Entry(number, id, dim, cat, apex, health, attack, armor, width, height, tellTicks, blow, balanceHealth,
                balanceAttack, weight, groupMin, groupMax, secondLook, material, place, anyLight, maxY, spawner);
    }

    public static final List<Entry> ALL = List.of(
            e(1, "thornhorn", Dim.OVERWORLD, Cat.CREATURE_NEUTRAL, false, 24F, 5F, 2F, 1.3F, 1.4F, 20, 7.5F, 24F, 5F, 8, 2, 4, "sunflower", "tough_hide", Place.GROUND, true, Integer.MAX_VALUE, ""),
            e(2, "snagwood", Dim.OVERWORLD, Cat.MONSTER, false, 30F, 5F, 6F, 0.9F, 2.6F, 12, 6F, 30F, 5F, 15, 1, 1, "birch_snag", "heartwood", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(3, "puffcap_shepherd", Dim.OVERWORLD, Cat.MONSTER, false, 16F, 2F, 0F, 0.7F, 1.1F, 16, 0F, 16F, 2F, 15, 1, 1, "glowcap", "spore_sac", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(4, "mire_gulper", Dim.OVERWORLD, Cat.MONSTER, false, 28F, 4F, 2F, 1.4F, 0.9F, 20, 0F, 28F, 4F, 15, 1, 1, "mangrove_gulper", "bog_gland", Place.SHALLOW, false, Integer.MAX_VALUE, ""),
            e(5, "dunefin", Dim.OVERWORLD, Cat.MONSTER, false, 22F, 5F, 2F, 1.2F, 0.8F, 15, 7F, 22F, 5F, 20, 1, 2, "red_dunefin", "sand_scale", Place.SAND, true, Integer.MAX_VALUE, ""),
            e(6, "rattlecoil", Dim.OVERWORLD, Cat.NEUTRAL, false, 14F, 4F, 0F, 0.8F, 0.4F, 30, 0F, 14F, 4F, 15, 1, 2, "sidewinder", "venom_fang", Place.GROUND, true, Integer.MAX_VALUE, ""),
            e(7, "snowroller", Dim.OVERWORLD, Cat.MONSTER, false, 26F, 4F, 4F, 1F, 1F, 16, 10F, 26F, 4F, 15, 1, 2, "frostbitten", "frost_core", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(8, "shiverwraith", Dim.OVERWORLD, Cat.MONSTER, false, 20F, 3F, 0F, 0.7F, 1.9F, 12, 0F, 20F, 3F, 10, 1, 1, "hoarfrost", "frost_core", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(9, "gloamhound", Dim.OVERWORLD, Cat.MONSTER, false, 18F, 4F, 1F, 0.8F, 0.85F, 14, 0F, 18F, 4F, 15, 3, 4, "snowpelt", "tough_hide", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(10, "vinedrop_strangler", Dim.OVERWORLD, Cat.MONSTER, false, 22F, 4F, 0F, 0.8F, 1.2F, 12, 0F, 22F, 4F, 15, 1, 1, "bamboo_strangler", "heartwood", Place.LEAVES, false, Integer.MAX_VALUE, ""),
            e(11, "snapmaw_bloom", Dim.OVERWORLD, Cat.MONSTER, false, 24F, 5F, 2F, 0.9F, 1.3F, 10, 7.5F, 24F, 5F, 10, 1, 1, "azalea_bloom", "heartwood", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(12, "stormeel", Dim.OVERWORLD, Cat.MONSTER, false, 20F, 3F, 0F, 0.6F, 0.5F, 30, 6F, 20F, 3F, 10, 1, 2, "abyssal_eel", "storm_coil", Place.WATER, false, Integer.MAX_VALUE, ""),
            e(13, "shellguard_crab", Dim.OVERWORLD, Cat.CREATURE_NEUTRAL, false, 20F, 4F, 4F, 0.9F, 0.6F, 8, 0F, 20F, 4F, 10, 2, 3, "stone_crab", "sand_scale", Place.GROUND, true, Integer.MAX_VALUE, ""),
            e(14, "dripfang", Dim.OVERWORLD, Cat.MONSTER, false, 16F, 7F, 2F, 0.6F, 1.4F, 12, 10.5F, 16F, 7F, 20, 1, 2, "calcite_fang", "venom_fang", Place.CEILING, false, Integer.MAX_VALUE, ""),
            e(15, "hushwing", Dim.OVERWORLD, Cat.MONSTER, false, 8F, 3F, 0F, 0.5F, 0.5F, 0, 0F, 8F, 3F, 15, 3, 4, "echo_hushwing", "echo_membrane", Place.GROUND, false, 0, ""),
            e(16, "mycelite_shambler", Dim.OVERWORLD, Cat.MONSTER, false, 24F, 3F, 0F, 1F, 1.8F, 0, 0F, 72F, 3F, 15, 1, 1, "crimson_cap", "spore_sac", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(17, "petalkin", Dim.OVERWORLD, Cat.CREATURE_NEUTRAL, false, 12F, 3F, 0F, 0.5F, 0.9F, 0, 0F, 12F, 3F, 8, 1, 2, "snowpetal", "petal_dust", Place.GROUND, true, Integer.MAX_VALUE, ""),
            e(18, "hollow_mourner", Dim.OVERWORLD, Cat.MONSTER, false, 26F, 3F, 2F, 0.7F, 2F, 60, 0F, 26F, 3F, 5, 1, 1, "bog_mourner", "grave_ash", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(19, "dustwhirl", Dim.OVERWORLD, Cat.MONSTER, false, 18F, 2F, 0F, 0.8F, 1.6F, 20, 0F, 18F, 2F, 12, 1, 1, "red_dust", "gale_essence", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(20, "skreel", Dim.OVERWORLD, Cat.MONSTER, false, 16F, 5F, 0F, 0.9F, 0.7F, 20, 0F, 16F, 5F, 10, 1, 2, "snowy_skreel", "gale_essence", Place.GROUND, true, Integer.MAX_VALUE, ""),
            e(21, "prismback", Dim.NETHER, Cat.MONSTER, false, 30F, 5F, 6F, 1F, 0.7F, 20, 0F, 30F, 5F, 10, 1, 2, "smoky_prismback", "prism_shard", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(22, "slagspitter", Dim.NETHER, Cat.MONSTER, false, 20F, 3F, 2F, 0.9F, 0.8F, 16, 5F, 20F, 3F, 15, 1, 2, "basalt_spitter", "slag_gland", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(23, "ash_jackal", Dim.NETHER, Cat.MONSTER, false, 16F, 4F, 1F, 0.7F, 0.8F, 10, 0F, 16F, 4F, 15, 2, 3, "soul_jackal", "ash_pelt", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(24, "slagheart_golem", Dim.NETHER, Cat.MONSTER, false, 44F, 7F, 6F, 1.2F, 2.4F, 20, 10F, 44F, 7F, 6, 1, 1, "cooled_golem", "slag_gland", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(25, "soot_manta", Dim.NETHER, Cat.MONSTER, false, 26F, 4F, 0F, 2F, 0.6F, 20, 0F, 26F, 4F, 6, 1, 1, "ember_manta", "soot_plume", Place.NONE, false, Integer.MAX_VALUE, "lava_sea_air"),
            e(26, "magma_tortoise", Dim.NETHER, Cat.CREATURE, false, 60F, 0F, 10F, 2.2F, 1F, 0, 0F, 60F, 0F, 4, 1, 1, "old_tortoise", "magma_scute", Place.LAVA, true, Integer.MAX_VALUE, ""),
            e(27, "veinleech", Dim.NETHER, Cat.MONSTER, false, 22F, 2F, 0F, 0.6F, 0.5F, 10, 0F, 22F, 2F, 15, 1, 2, "pale_leech", "vein_sac", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(28, "shredjaw_beetle", Dim.NETHER, Cat.MONSTER, false, 30F, 5F, 8F, 1.1F, 0.8F, 12, 0F, 30F, 5F, 12, 1, 2, "warped_shredjaw", "chitin_plate", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(29, "weepling_broodmother", Dim.NETHER, Cat.MONSTER, false, 36F, 3F, 2F, 1.4F, 1.2F, 20, 0F, 36F, 3F, 6, 1, 1, "bloated_brood", "vein_sac", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(30, "gleamtrap", Dim.NETHER, Cat.MONSTER, false, 24F, 7F, 4F, 1F, 1F, 10, 10.5F, 24F, 7F, 8, 1, 1, "warped_gleamtrap", "siphon_gel", Place.NONE, false, Integer.MAX_VALUE, "fungus_cap"),
            e(31, "scarlet_mantis", Dim.NETHER, Cat.MONSTER, false, 28F, 6F, 2F, 0.7F, 1.9F, 12, 0F, 28F, 6F, 10, 1, 1, "ashen_mantis", "chitin_plate", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(32, "phantasm_lantern", Dim.NETHER, Cat.MONSTER, false, 18F, 0F, 0F, 0.6F, 0.9F, 20, 0F, 18F, 0F, 10, 1, 1, "crimson_lantern", "siphon_gel", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(33, "woolcap_grazer", Dim.NETHER, Cat.CREATURE, false, 18F, 0F, 0F, 1F, 1.2F, 0, 0F, 18F, 0F, 8, 2, 4, "crimson_woolcap", "fungal_fleece", Place.GROUND, true, Integer.MAX_VALUE, ""),
            e(34, "siphonjelly", Dim.NETHER, Cat.MONSTER, false, 16F, 2F, 0F, 0.8F, 1F, 10, 0F, 16F, 2F, 10, 1, 2, "crimson_jelly", "siphon_gel", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(35, "spore_cannon", Dim.NETHER, Cat.MONSTER, false, 30F, 4F, 6F, 1F, 1.6F, 20, 4F, 30F, 4F, 8, 1, 1, "crimson_cannon", "siphon_gel", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(36, "gloomweaver", Dim.NETHER, Cat.MONSTER, false, 26F, 5F, 2F, 1.3F, 0.9F, 20, 0F, 26F, 5F, 8, 1, 1, "crimson_weaver", "chitin_plate", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(37, "ossuary_wyrm", Dim.NETHER, Cat.MONSTER, false, 50F, 8F, 4F, 0.8F, 0.8F, 20, 0F, 50F, 8F, 4, 1, 1, "bleached_wyrm", "soul_vertebra", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(38, "keening_shade", Dim.NETHER, Cat.MONSTER, false, 20F, 3F, 0F, 0.6F, 1.8F, 16, 0F, 20F, 3F, 10, 1, 1, "hollow_shade", "soul_vertebra", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(39, "soulfire_seer", Dim.NETHER, Cat.MONSTER, false, 22F, 9F, 0F, 0.6F, 2F, 24, 9F, 22F, 9F, 8, 1, 1, "bone_seer", "soul_vertebra", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(40, "cindermoth_swarm", Dim.NETHER, Cat.MONSTER, false, 10F, 1F, 0F, 1F, 1F, 12, 0F, 10F, 1F, 12, 1, 1, "soul_moths", "ember_wing", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(41, "soulward_revenant", Dim.NETHER, Cat.MONSTER, false, 40F, 6F, 6F, 0.8F, 2.2F, 20, 8F, 40F, 6F, 6, 1, 1, "gilded_revenant", "soul_vertebra", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(42, "basalt_behemoth", Dim.NETHER, Cat.MONSTER, true, 200F, 14F, 16F, 2.4F, 3.2F, 20, 14F, 200F, 14F, 1, 1, 1, "", "molten_heart", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(43, "quakehopper", Dim.NETHER, Cat.MONSTER, false, 26F, 5F, 2F, 1F, 1F, 14, 6F, 26F, 5F, 12, 1, 2, "magma_hopper", "slag_gland", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(44, "ashharrier", Dim.NETHER, Cat.MONSTER, false, 24F, 4F, 0F, 1.4F, 0.8F, 16, 6F, 24F, 4F, 10, 1, 1, "cinder_harrier", "ember_wing", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(45, "lavaleaper", Dim.NETHER, Cat.MONSTER, false, 24F, 7F, 2F, 0.8F, 0.6F, 10, 0F, 24F, 7F, 10, 1, 2, "ember_leaper", "slag_gland", Place.LAVA, false, Integer.MAX_VALUE, ""),
            e(46, "blackstone_scuttler", Dim.NETHER, Cat.MONSTER, false, 30F, 5F, 10F, 1.1F, 0.7F, 12, 0F, 30F, 5F, 10, 1, 2, "gilded_scuttler", "chitin_plate", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(47, "pyre_herald", Dim.NETHER, Cat.MONSTER, false, 22F, 3F, 0F, 0.7F, 1.2F, 20, 0F, 22F, 3F, 0, 1, 1, "soul_herald", "ember_wing", Place.NONE, false, Integer.MAX_VALUE, "fortress"),
            e(48, "fortress_jailer", Dim.NETHER, Cat.MONSTER, false, 36F, 6F, 6F, 0.8F, 2.2F, 16, 0F, 36F, 6F, 0, 1, 1, "rusted_jailer", "chain_link", Place.NONE, false, Integer.MAX_VALUE, "fortress"),
            e(49, "flamewright", Dim.NETHER, Cat.MONSTER, false, 30F, 4F, 2F, 0.7F, 2F, 20, 0F, 30F, 4F, 0, 1, 1, "soul_wright", "ember_wing", Place.NONE, false, Integer.MAX_VALUE, "fortress"),
            e(50, "aurovore", Dim.NETHER, Cat.MONSTER, false, 24F, 4F, 4F, 0.9F, 0.7F, 10, 0F, 24F, 4F, 6, 1, 1, "gilded_aurovore", "gilded_gizzard", Place.GROUND, false, Integer.MAX_VALUE, "bastion"),
            e(51, "mending_crystalling", Dim.END, Cat.MONSTER, false, 4F, 0F, 0F, 0.5F, 0.8F, 0, 0F, 4F, 0F, 0, 1, 2, "rose_crystalling", "void_crystal", Place.NONE, false, Integer.MAX_VALUE, "main_island"),
            e(52, "liftstone_monolith", Dim.END, Cat.MONSTER, true, 250F, 12F, 12F, 1.6F, 4F, 20, 12F, 250F, 12F, 0, 1, 1, "", "gravity_stone", Place.NONE, false, Integer.MAX_VALUE, "main_island"),
            e(53, "astral_augur", Dim.END, Cat.MONSTER, false, 34F, 4F, 2F, 0.7F, 2.1F, 24, 9F, 34F, 4F, 4, 1, 1, "dusk_augur", "star_mote", Place.GROUND, false, Integer.MAX_VALUE, "main_island"),
            e(54, "nullcantor", Dim.END, Cat.MONSTER, false, 40F, 6F, 4F, 0.7F, 2F, 0, 0F, 40F, 6F, 3, 1, 1, "pale_cantor", "void_ichor", Place.GROUND, false, Integer.MAX_VALUE, "main_island"),
            e(55, "chorus_grazer", Dim.END, Cat.CREATURE, false, 24F, 0F, 0F, 1.1F, 1.3F, 0, 0F, 24F, 0F, 6, 2, 4, "pale_grazer", "purpur_fleece", Place.GROUND, true, Integer.MAX_VALUE, ""),
            e(56, "levimoth", Dim.END, Cat.MONSTER, false, 16F, 2F, 0F, 0.8F, 0.6F, 16, 0F, 16F, 2F, 5, 1, 3, "pale_levimoth", "chorus_silk", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(57, "warpseed_bloom", Dim.END, Cat.MONSTER, false, 26F, 3F, 4F, 0.8F, 1.6F, 12, 3F, 26F, 3F, 5, 1, 1, "violet_bloom", "chorus_silk", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(58, "glimmerless", Dim.END, Cat.MONSTER, false, 30F, 7F, 2F, 0.7F, 1.9F, 0, 0F, 30F, 7F, 4, 1, 1, "shimmering", "void_ichor", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(59, "riftjackal", Dim.END, Cat.MONSTER, false, 26F, 6F, 2F, 0.8F, 0.9F, 8, 0F, 26F, 6F, 4, 2, 3, "starbitten", "rift_essence", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(60, "chorus_husk", Dim.END, Cat.MONSTER, false, 30F, 6F, 2F, 0.7F, 2F, 20, 0F, 30F, 6F, 5, 1, 2, "blooming_husk", "chorus_silk", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(61, "gatecaster", Dim.END, Cat.MONSTER, false, 30F, 6F, 2F, 0.7F, 2F, 20, 0F, 30F, 6F, 4, 1, 1, "mirrored_weaver", "rift_essence", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(62, "hollowmind", Dim.END, Cat.MONSTER, false, 14F, 4F, 0F, 0.5F, 0.5F, 20, 0F, 14F, 4F, 4, 1, 1, "pale_mind", "rift_essence", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(63, "pearlgulp", Dim.END, Cat.MONSTER, false, 22F, 5F, 2F, 0.9F, 0.8F, 0, 0F, 22F, 5F, 4, 1, 1, "smoky_gulp", "rift_essence", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(64, "brinkram", Dim.END, Cat.MONSTER, false, 40F, 8F, 6F, 1.2F, 1.4F, 20, 0F, 40F, 8F, 5, 1, 2, "purpur_ram", "gravity_stone", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(65, "facet_doppel", Dim.END, Cat.MONSTER, false, 36F, 0F, 0F, 0.6F, 1.95F, 20, 0F, 36F, 0F, 3, 1, 1, "obsidian_doppel", "void_ichor", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(66, "shardling_colossus", Dim.END, Cat.MONSTER, false, 50F, 9F, 8F, 1.4F, 2.6F, 16, 0F, 50F, 9F, 3, 1, 1, "amethyst_colossus", "void_crystal", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(67, "phasewalker", Dim.END, Cat.MONSTER, false, 32F, 7F, 2F, 0.7F, 2F, 10, 0F, 32F, 7F, 4, 1, 1, "inverted_walker", "time_sand", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(68, "echo_fencer", Dim.END, Cat.MONSTER, false, 32F, 7F, 2F, 0.7F, 2F, 10, 0F, 32F, 7F, 4, 1, 1, "gilded_fencer", "time_sand", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(69, "swellshade", Dim.END, Cat.MONSTER, false, 24F, 4F, 0F, 0.8F, 1.6F, 0, 0F, 48F, 8F, 4, 1, 1, "gloom_swell", "void_ichor", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(70, "chronomite", Dim.END, Cat.MONSTER, false, 26F, 5F, 6F, 0.9F, 0.7F, 0, 0F, 26F, 5F, 4, 1, 1, "gilded_mite", "time_sand", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(71, "vigil_owl", Dim.END, Cat.MONSTER, false, 18F, 4F, 0F, 0.6F, 0.8F, 40, 0F, 18F, 4F, 4, 1, 1, "pale_vigil", "void_ichor", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(72, "anchor_golem", Dim.END, Cat.MONSTER, false, 44F, 7F, 8F, 1.2F, 2.2F, 14, 0F, 44F, 7F, 4, 1, 1, "rusted_anchor", "gravity_stone", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(73, "pullstar", Dim.END, Cat.MONSTER, false, 30F, 6F, 4F, 1F, 1F, 20, 0F, 30F, 6F, 4, 1, 1, "red_pullstar", "gravity_stone", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(74, "void_bloom", Dim.END, Cat.MONSTER, false, 30F, 0F, 2F, 0.9F, 1.2F, 0, 0F, 30F, 0F, 3, 1, 1, "pale_bloom", "void_ichor", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(75, "recurrent", Dim.END, Cat.MONSTER, false, 30F, 6F, 2F, 0.7F, 1.9F, 0, 0F, 30F, 6F, 3, 1, 1, "gilded_recurrent", "time_sand", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(76, "mnemovore", Dim.END, Cat.MONSTER, false, 28F, 5F, 2F, 1F, 0.6F, 10, 0F, 28F, 5F, 4, 1, 2, "starved_mnemovore", "void_ichor", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(77, "rotfang", Dim.END, Cat.MONSTER, false, 26F, 6F, 2F, 0.8F, 0.8F, 10, 0F, 26F, 6F, 5, 1, 2, "blight_fang", "void_ichor", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(78, "gemini_watchers", Dim.END, Cat.MONSTER, false, 30F, 6F, 4F, 0.7F, 2F, 100, 0F, 60F, 6F, 3, 2, 2, "dusk_pair", "void_crystal", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(79, "spirebloom", Dim.END, Cat.MONSTER, false, 28F, 8F, 6F, 0.9F, 1.5F, 16, 8F, 28F, 8F, 4, 1, 1, "rose_spire", "void_crystal", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(80, "riftmine_sapper", Dim.END, Cat.MONSTER, false, 26F, 4F, 2F, 0.7F, 1.4F, 40, 0F, 26F, 4F, 4, 1, 1, "purpur_sapper", "gravity_stone", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(81, "underclinger", Dim.END, Cat.MONSTER, false, 28F, 6F, 2F, 1F, 0.6F, 10, 0F, 28F, 6F, 0, 1, 1, "pale_clinger", "void_membrane", Place.NONE, false, Integer.MAX_VALUE, "underside"),
            e(82, "abyss_angler", Dim.END, Cat.MONSTER, false, 30F, 4F, 2F, 1.2F, 1F, 16, 0F, 30F, 4F, 0, 1, 1, "lantern_angler", "void_membrane", Place.NONE, false, Integer.MAX_VALUE, "underside"),
            e(83, "brinkray", Dim.END, Cat.NEUTRAL, false, 40F, 8F, 2F, 3F, 0.6F, 20, 0F, 40F, 8F, 0, 1, 1, "starry_ray", "void_membrane", Place.NONE, false, Integer.MAX_VALUE, "void_air"),
            e(84, "exile_wraith", Dim.END, Cat.MONSTER, false, 26F, 4F, 0F, 0.7F, 1.9F, 20, 0F, 26F, 4F, 4, 1, 1, "dusk_wraith", "rift_essence", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(85, "mote_swarm", Dim.END, Cat.MONSTER, false, 30F, 1F, 0F, 1.5F, 1.5F, 0, 0F, 30F, 1F, 4, 1, 1, "ember_motes", "star_mote", Place.GROUND, false, Integer.MAX_VALUE, ""),
            e(86, "pearlshell_snail", Dim.END, Cat.CREATURE, false, 16F, 0F, 6F, 0.6F, 0.5F, 0, 0F, 16F, 0F, 5, 1, 3, "purpur_snail", "", Place.GROUND, true, Integer.MAX_VALUE, ""),
            e(87, "wingshear", Dim.END, Cat.MONSTER, false, 30F, 6F, 2F, 1.4F, 0.6F, 16, 0F, 30F, 6F, 0, 1, 2, "dusk_shear", "void_membrane", Place.NONE, false, Integer.MAX_VALUE, "sky"),
            e(88, "grand_orrery", Dim.END, Cat.MONSTER, true, 300F, 6F, 10F, 2F, 2F, 20, 6F, 300F, 6F, 0, 1, 1, "", "star_mote", Place.NONE, false, Integer.MAX_VALUE, "sky"),
            e(89, "resonant_triad", Dim.END, Cat.MONSTER, false, 16F, 4F, 0F, 0.6F, 0.9F, 0, 0F, 16F, 4F, 3, 3, 3, "rose_triad", "void_crystal", Place.GROUND, false, Integer.MAX_VALUE, "sky"),
            e(90, "driftsong", Dim.END, Cat.CREATURE, false, 120F, 0F, 6F, 6F, 2.5F, 0, 0F, 120F, 0F, 0, 1, 1, "starlit_leviathan", "void_membrane", Place.NONE, false, Integer.MAX_VALUE, "void_below"));

    public static final Map<String, Entry> BY_ID = ALL.stream().collect(Collectors.toUnmodifiableMap(Entry::id, Function.identity()));

    public static Entry get(String id) {
        Entry entry = BY_ID.get(id);
        if (entry == null) throw new IllegalArgumentException("not in the roster: " + id);
        return entry;
    }
}
