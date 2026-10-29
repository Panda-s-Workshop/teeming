package com.panda.teeming.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.command.TeemingCommand;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.neoforge.NeoForgeConfig;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Place;
import com.panda.teeming.spawn.ListShare;
import com.panda.teeming.spawn.SpawnTable;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawner.Candidate;
import com.panda.teeming.spawn.TeemingSpawner.Rule;
import com.panda.teeming.spawn.TeemingSpawns;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.Difficulty;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * The special placements (sand, lava, ceiling, leaves, void air, underside) accept their spot and refuse a
 * plain floor, and main-island spawns stay shut while the dragon lives. Spots are built in the arena (its floor is y 0).
 * Each biome-list placement kind is also asked the whole question natural spawning asks, in a sealed dark room: the
 * type's vanilla placement, its spawn predicate, room for its spawn box, and the mob's own spawn rules and obstruction.
 */
final class SpawnTests {
    private SpawnTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("placement_ground", SpawnTests::ground);
        out.accept("placement_sand", h -> place(h, "dunefin", Place.SAND, Blocks.SAND));
        out.accept("placement_leaves", h -> place(h, "vinedrop_strangler", Place.LEAVES, Blocks.JUNGLE_LEAVES));
        out.accept("placement_water", SpawnTests::water);
        out.accept("placement_lava", SpawnTests::lava);
        out.accept("placement_shallow", SpawnTests::shallow);
        out.accept("placement_ceiling", SpawnTests::ceiling);
        out.accept("placement_void_air", SpawnTests::voidAir);
        out.accept("placement_underside", SpawnTests::underside);
        out.accept("spawner_main_island_gate", SpawnTests::mainIslandGate);
        out.accept("spawn_main_island_lists", SpawnTests::mainIslandLists);
        out.accept("spawner_spawns", SpawnTests::spawnerSpawns);
        out.accept("spawn_any_light", SpawnTests::anyLight);
        out.accept("spawn_share", SpawnTests::spawnShare);
        out.accept("spawn_share_counts", SpawnTests::shareCounts);
        out.accept("spawn_share_end", SpawnTests::spawnShareEnd);
        out.accept("spawn_share_overworld", SpawnTests::spawnShareOverworld);
        out.accept("spawn_rest_weights", SpawnTests::restWeights);
        out.accept("spawn_share_capped_lists", SpawnTests::shareCappedLists);
        out.accept("spawner_emptied_tag", SpawnTests::emptiedTag);
        out.accept("spawn_surface_only", SpawnTests::surfaceOnly);
        out.accept("spawner_picks_by_spot", SpawnTests::picksBySpot);
        out.accept("place_looks", SpawnTests::placeLooks);
        out.accept("spawner_apex", SpawnTests::apex);
        out.accept("spawner_apex_island", SpawnTests::apexIsland);
        out.accept("spawn_end_open_sky", SpawnTests::endOpenSky);
        out.accept("spawner_open_air_flier", SpawnTests::openAirFlier);
        out.accept("spawner_end_turns", SpawnTests::endTurns);
    }

    private static boolean ok(GameTestHelper helper, String id, BlockPos rel) {
        RosterStats.Entry mob = RosterStats.get(id);
        return TeemingSpawns.placeOk(mob, ModMobs.THORNHORN.get(), helper.getLevel(), helper.absolutePos(rel));
    }

    /** A ground-kind placement: accepts a spot on its block, refuses one on the arena floor, and spawns there naturally. */
    private static void place(GameTestHelper helper, String id, Place kind, net.minecraft.world.level.block.Block block) {
        helper.assertTrue(RosterStats.get(id).place() == kind, id + " should be a " + kind + " mob");
        helper.assertTrue(!ok(helper, id, new BlockPos(8, 1, 8)), id + " refuses the plain floor");
        sealed(helper, 3);
        helper.setBlock(new BlockPos(3, 1, 3), block);
        helper.assertTrue(ok(helper, id, new BlockPos(3, 2, 3)), id + " accepts a spot on " + block);
        helper.succeedWhen(() -> helper.assertTrue(natural(helper, id, new BlockPos(3, 2, 3)),
                id + ": natural spawning refuses its dark spot on " + block + " (" + why(helper, id, new BlockPos(3, 2, 3)) + ")"));
    }

    /** A plain ground monster (Hollow Mourner) spawns naturally on a dark stone floor. */
    private static void ground(GameTestHelper helper) {
        helper.assertTrue(RosterStats.get("hollow_mourner").place() == Place.GROUND, "hollow_mourner should be a ground mob");
        sealed(helper, 3);
        helper.succeedWhen(() -> helper.assertTrue(natural(helper, "hollow_mourner", new BlockPos(3, 1, 3)),
                "hollow_mourner: natural spawning refuses a dark stone floor (" + why(helper, "hollow_mourner", new BlockPos(3, 1, 3)) + ")"));
    }

    /** An open-water mob (Stormeel) spawns naturally in water two deep, and natural spawning refuses it on dry floor. */
    private static void water(GameTestHelper helper) {
        helper.assertTrue(RosterStats.get("stormeel").place() == Place.WATER, "stormeel should be an open-water mob");
        sealed(helper, 3);
        for (int x = 2; x <= 4; x++)
            for (int z = 2; z <= 4; z++)
                for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        helper.succeedWhen(() -> {
            helper.assertTrue(natural(helper, "stormeel", new BlockPos(3, 1, 3)),
                    "stormeel: natural spawning refuses water two deep (" + why(helper, "stormeel", new BlockPos(3, 1, 3)) + ")");
            helper.assertTrue(!natural(helper, "stormeel", new BlockPos(8, 1, 8)), "stormeel: natural spawning takes the dry floor");
            // it wins open water by default, so its natural spawns go ahead only part of the time
            RosterStats.Entry row = RosterStats.get("stormeel");
            var type = TeemingSpawns.type(row).orElseThrow();
            RandomSource random = RandomSource.create(17);
            int passed = 0;
            for (int i = 0; i < 600; i++)
                if (TeemingSpawns.check(row, type, helper.getLevel(), EntitySpawnReason.NATURAL, helper.absolutePos(new BlockPos(3, 1, 3)), random)) passed++;
            double rate = passed / 600.0;
            helper.assertTrue(Math.abs(rate - SpawnTable.OPEN_WATER_CHANCE) < 0.07,
                    "stormeel: natural spawns go ahead " + rate + " of the time, the open-water chance is " + SpawnTable.OPEN_WATER_CHANCE);
        });
    }

    /**
     * A sealed room: stone walls, floor and roof round the 3 by 3 room from (2, 1, 2) to (4, height, 4), so neither sky
     * nor block light reaches it once the light engine has caught up. The test lays the spot's own blocks inside.
     */
    private static void sealed(GameTestHelper helper, int height) {
        for (int x = 1; x <= 5; x++)
            for (int z = 1; z <= 5; z++)
                for (int y = 0; y <= height + 1; y++) {
                    boolean inside = x >= 2 && x <= 4 && z >= 2 && z <= 4 && y >= 1 && y <= height;
                    helper.setBlock(new BlockPos(x, y, z), inside ? Blocks.AIR : Blocks.STONE);
                }
    }

    /**
     * Natural spawning's whole question at the spot, in its own order: the type's vanilla placement
     * ({@code SpawnPlacements.isSpawnPositionOk}), its spawn predicate for a natural spawn, a spawn box that collides with
     * no block, then the mob itself: its spawn rules and its obstruction check.
     */
    private static boolean natural(GameTestHelper helper, String id, BlockPos rel) {
        return why(helper, id, rel).equals("taken");
    }

    private static String placementName(net.minecraft.world.entity.SpawnPlacementType type) {
        if (type == net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND) return "ON_GROUND";
        if (type == net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) return "IN_WATER";
        if (type == net.minecraft.world.entity.SpawnPlacementTypes.IN_LAVA) return "IN_LAVA";
        return type == net.minecraft.world.entity.SpawnPlacementTypes.NO_RESTRICTIONS ? "NO_RESTRICTIONS" : String.valueOf(type);
    }

    /** The first step of natural spawning that refuses the mob at the spot, or "taken". */
    private static String why(GameTestHelper helper, String id, BlockPos rel) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(rel);
        EntityType<? extends Mob> type = TeemingSpawns.type(RosterStats.get(id)).orElseThrow();
        if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) return "the vanilla placement " + placementName(SpawnPlacements.getPlacementType(type)) + " refuses it";
        // its predicate rolls (a spawn chance, the darkness roll): any of a dozen rolls of one seeded source passing counts
        boolean rolled = false;
        RandomSource random = RandomSource.create(1);
        for (int i = 0; i < 12 && !rolled; i++) rolled = SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random);
        if (!rolled)
            return "its spawn predicate refuses it (block light " + level.getBrightness(LightLayer.BLOCK, pos) + ", sky light "
                    + level.getBrightness(LightLayer.SKY, pos) + ")";
        if (!level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) return "its spawn box hits a block";
        if (!takes(level, type, pos)) return "the mob's own spawn rules or obstruction check refuse it";
        return "taken";
    }

    /**
     * Next to a glowstone (block light 15), an any-light monster (Rattlecoil) still rates the spot 0, so natural
     * spawning accepts it there; a monster without the flag (Slagheart Golem) rates it below 0, as vanilla's do.
     */
    private static void anyLight(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at.east(), Blocks.GLOWSTONE);
        BlockPos abs = helper.absolutePos(at);
        PathfinderMob snake = helper.spawnWithNoFreeWill(ModMobs.RATTLECOIL.get(), at);
        PathfinderMob golem = helper.spawnWithNoFreeWill(ModMobs.SLAGHEART_GOLEM.get(), at.north(2));
        helper.runAtTickTime(2, () -> {
            float snakeValue = snake.getWalkTargetValue(abs, helper.getLevel());
            float golemValue = golem.getWalkTargetValue(abs, helper.getLevel());
            helper.assertTrue(snakeValue == 0.0F, "an any-light monster rates a lit spot 0, got " + snakeValue);
            helper.assertTrue(golemValue < 0.0F, "a light-bound monster rates a lit spot below 0, got " + golemValue);
            helper.succeed();
        });
    }

    /** Shallow water: one or two deep over a floor and open above is accepted; dry floor and water three deep are not. */
    private static void shallow(GameTestHelper helper) {
        helper.assertTrue(RosterStats.get("mire_gulper").place() == Place.SHALLOW, "mire_gulper should be a shallow-water mob");
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.WATER);
        helper.assertTrue(ok(helper, "mire_gulper", new BlockPos(2, 1, 2)), "accepts water one deep");
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(5, 2, 2), Blocks.WATER);
        helper.assertTrue(ok(helper, "mire_gulper", new BlockPos(5, 1, 2)), "accepts water two deep at the bottom");
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(8, y, 2), Blocks.WATER);
        helper.assertTrue(!ok(helper, "mire_gulper", new BlockPos(8, 1, 2)), "refuses water three deep");
        helper.assertTrue(!ok(helper, "mire_gulper", new BlockPos(2, 1, 6)), "refuses the dry floor");
        // then natural spawning's whole question in a sealed pool one deep, three wide (the Gulper is 1.4 wide)
        sealed(helper, 3);
        for (int x = 2; x <= 4; x++)
            for (int z = 2; z <= 4; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
        helper.succeedWhen(() -> helper.assertTrue(natural(helper, "mire_gulper", new BlockPos(3, 1, 3)),
                "mire_gulper: natural spawning refuses its dark pool (" + why(helper, "mire_gulper", new BlockPos(3, 1, 3)) + ")"));
    }

    private static void lava(GameTestHelper helper) {
        helper.assertTrue(RosterStats.get("magma_tortoise").place() == Place.LAVA, "magma_tortoise should be a lava mob");
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.LAVA);
        helper.assertTrue(ok(helper, "magma_tortoise", new BlockPos(3, 1, 3)), "accepts lava with air above");
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        helper.assertTrue(!ok(helper, "magma_tortoise", new BlockPos(3, 1, 3)), "refuses lava under a block");
        helper.assertTrue(!ok(helper, "magma_tortoise", new BlockPos(8, 1, 8)), "refuses dry air");
        // natural spawning's whole question in a sealed lava pool one deep, three wide (the tortoise is 2.2 wide)
        sealed(helper, 3);
        for (int x = 2; x <= 4; x++)
            for (int z = 2; z <= 4; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.LAVA);
        helper.succeedWhen(() -> helper.assertTrue(natural(helper, "magma_tortoise", new BlockPos(3, 1, 3)),
                "magma_tortoise: natural spawning refuses its lava pool (" + why(helper, "magma_tortoise", new BlockPos(3, 1, 3)) + ")"));
    }

    private static void ceiling(GameTestHelper helper) {
        helper.assertTrue(RosterStats.get("dripfang").place() == Place.CEILING, "dripfang should be a ceiling mob");
        // a 1.4-tall mob needs two air blocks under the ceiling, or the spawner's box check refuses it
        helper.setBlock(new BlockPos(3, 5, 3), Blocks.STONE);
        helper.assertTrue(ok(helper, "dripfang", new BlockPos(3, 3, 3)), "accepts two air blocks under stone");
        helper.assertTrue(!ok(helper, "dripfang", new BlockPos(3, 4, 3)), "refuses the one block right under it, too short");
        helper.assertTrue(!ok(helper, "dripfang", new BlockPos(8, 3, 8)), "refuses open air");
        helper.setBlock(new BlockPos(5, 5, 5), Blocks.OAK_FENCE);
        helper.assertTrue(!ok(helper, "dripfang", new BlockPos(5, 3, 5)), "refuses a fence, whose underside isn't sturdy");
        // natural spawning's whole question under the roof of a sealed room two high
        helper.setBlock(new BlockPos(3, 5, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(5, 5, 5), Blocks.AIR);
        sealed(helper, 2);
        helper.succeedWhen(() -> helper.assertTrue(natural(helper, "dripfang", new BlockPos(3, 1, 3)),
                "dripfang: natural spawning refuses the dark spot under a roof (" + why(helper, "dripfang", new BlockPos(3, 1, 3)) + ")"));
    }

    private static boolean fits(GameTestHelper helper, Rule rule, BlockPos rel) {
        return rule.fits(helper.getLevel(), helper.absolutePos(rel));
    }

    private static void voidAir(GameTestHelper helper) {
        helper.setBlock(new BlockPos(7, 4, 5), Blocks.END_STONE);                      // an island edge beside the spot
        helper.assertTrue(fits(helper, Rule.VOID_AIR, new BlockPos(5, 5, 5)), "accepts open air beside an edge with a drop below");
        helper.assertTrue(!fits(helper, Rule.VOID_AIR, new BlockPos(5, 1, 5)), "refuses a spot on the floor");
        helper.assertTrue(!fits(helper, Rule.VOID_AIR, new BlockPos(4, 5, 4)), "refuses open air with no edge near");
        helper.succeed();
    }

    private static void underside(GameTestHelper helper) {
        helper.setBlock(new BlockPos(5, 6, 5), Blocks.END_STONE);
        helper.assertTrue(fits(helper, Rule.UNDERSIDE, new BlockPos(5, 5, 5)), "accepts the air under an island block with a drop below");
        helper.assertTrue(!fits(helper, Rule.UNDERSIDE, new BlockPos(8, 5, 8)), "refuses open air");
        helper.setBlock(new BlockPos(5, 3, 5), Blocks.END_STONE);
        helper.assertTrue(!fits(helper, Rule.UNDERSIDE, new BlockPos(5, 5, 5)), "refuses a spot with ground close below");
        helper.succeed();
    }

    /** The spawner's last step: a found spot passes the natural-spawn predicate, and the mob arrives with a look. */
    private static void spawnerSpawns(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("thornhorn");
        Mob mob = TeemingSpawner.spawn(helper.getLevel(), row, ModMobs.THORNHORN.get(), helper.absolutePos(new BlockPos(5, 1, 5)), helper.getLevel().getRandom());
        helper.assertTrue(mob != null && mob.isAlive(), "the spawner placed a Thornhorn");
        helper.assertTrue(!((TeemingCreature) mob).look().isEmpty(), "it arrived with a look");
        helper.assertTrue(TeemingSpawner.spawn(helper.getLevel(), row, ModMobs.THORNHORN.get(), helper.absolutePos(new BlockPos(5, 3, 5)),
                helper.getLevel().getRandom()) == null, "a spot in mid-air is refused");
        mob.discard();
        helper.succeed();
    }

    /** Before the dragon dies (here: no dragon fight at all) main-island spots are refused. */
    private static void mainIslandGate(GameTestHelper helper) {
        helper.assertTrue(!TeemingSpawner.mainIslandOpen(helper.getLevel()), "the main island is shut without a killed dragon");
        helper.assertTrue(TeemingSpawner.onMainIsland(BlockPos.ZERO) && TeemingSpawner.onMainIsland(new BlockPos(700, 60, -700)),
                "the origin and (700, -700) are main island");
        helper.assertTrue(!TeemingSpawner.onMainIsland(new BlockPos(1100, 60, 0)), "1100 blocks out is the outer islands");
        helper.assertTrue(!fits(helper, Rule.MAIN_ISLAND, new BlockPos(5, 1, 5)), "a floor spot outside the End is no main-island spot");
        helper.succeed();
    }

    /**
     * A biome list can't bring an outer End mob onto the main island: the spawn predicate refuses an End list mob inside
     * the main island's area by position, whatever the biome, as the spawner's outer rules do, and lets a main-island
     * mob's list spawn there only once the dragon is dead. A biome mod's End biome can lie there (one ring of debris
     * islands sits 150 to 450 blocks out); here the spot's biome is vanilla's main island, which is in
     * {@code #teeming:modded_end} too. In the GameTest server's End, on end stone floors at (300, 120, 0), inside the
     * area, and at (1300, 120, 0), past it: the Chorus Grazer (a creature in any light, so only the place and the
     * position decide) is refused at 300 and taken at 1300; the Astral Augur (a main-island mob with a biome list) is
     * refused at 300 while the dragon gate is shut, and with it open is taken there within 40 seeded tries (each passes
     * the End's darkness roll about half the time), as at 1300; an egg still places the grazer at 300.
     */
    private static void mainIslandLists(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            helper.fail("the GameTest server has no End");
            return;
        }
        int floor = 120;
        BlockPos inside = new BlockPos(300, floor + 1, 0);
        BlockPos outside = new BlockPos(1300, floor + 1, 0);
        for (BlockPos at : List.of(inside, outside)) {
            end.getChunk(at.getX() >> 4, at.getZ() >> 4);
            end.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
            islandFloor(end, at.getX(), floor, at.getZ());
        }
        RosterStats.Entry grazer = RosterStats.get("chorus_grazer");
        RosterStats.Entry augur = RosterStats.get("astral_augur");
        var grazerType = TeemingSpawns.type(grazer).orElseThrow();
        var augurType = TeemingSpawns.type(augur).orElseThrow();
        helper.succeedWhen(() -> {
            helper.assertTrue(end.getBiome(inside).is(TeemingSpawns.MODDED_END), "the inside spot's biome is in #teeming:modded_end");
            helper.assertTrue(TeemingSpawner.onMainIsland(inside) && !TeemingSpawner.onMainIsland(outside), "300 out is the main island's area, 1300 isn't");
            helper.assertTrue(!TeemingSpawner.mainIslandOpen(end), "the dragon gate is shut");
            RandomSource random = RandomSource.create(11);
            helper.assertTrue(TeemingSpawns.placeOk(grazer, grazerType, end, inside), "end stone at 300 is a grazer spot");
            helper.assertTrue(!TeemingSpawns.check(grazer, grazerType, end, EntitySpawnReason.NATURAL, inside, random),
                    "a natural grazer spawn inside the main island's area is refused");
            helper.assertTrue(!TeemingSpawns.check(grazer, grazerType, end, EntitySpawnReason.CHUNK_GENERATION, inside, random),
                    "and a chunk-generation one");
            helper.assertTrue(TeemingSpawns.check(grazer, grazerType, end, EntitySpawnReason.NATURAL, outside, random), "a grazer at 1300 is taken");
            helper.assertTrue(TeemingSpawns.check(grazer, grazerType, end, EntitySpawnReason.SPAWN_ITEM_USE, inside, random), "an egg still places it at 300");
            boolean shut = false;
            for (int i = 0; i < 40 && !shut; i++) shut = TeemingSpawns.check(augur, augurType, end, EntitySpawnReason.NATURAL, inside, random);
            helper.assertTrue(!shut, "the Augur's list spawn on the main island is refused while the dragon lives");
            boolean far = false;
            for (int i = 0; i < 40 && !far; i++) far = TeemingSpawns.check(augur, augurType, end, EntitySpawnReason.NATURAL, outside, random);
            helper.assertTrue(far, "the Augur is taken at 1300");
            boolean[] open = {false};
            islandOpen(() -> {
                for (int i = 0; i < 40 && !open[0]; i++) open[0] = TeemingSpawns.check(augur, augurType, end, EntitySpawnReason.NATURAL, inside, random);
            });
            helper.assertTrue(open[0], "with the dragon gate open the Augur is taken on the main island");
            for (BlockPos at : List.of(inside, outside)) end.setChunkForced(at.getX() >> 4, at.getZ() >> 4, false);
        });
    }

    // ---- spawn share ----------------------------------------------------------------------------------------------

    private static boolean teeming(EntityType<?> type) {
        return type.builtInRegistryHolder().key().identifier().getNamespace().equals(TeemingMod.MODID);
    }

    private static double share(int teeming, int others) {
        return teeming + others == 0 ? 0.0 : teeming / (double) (teeming + others);
    }

    /**
     * In each Nether biome Teeming makes about 1 in 4 to 6 of the monsters (17-25 %), counted in mobs ({@link ListShare}:
     * each entry's weight times its mean group, a vanilla one's up to its cluster size and times the share of its picks
     * its own rule lets through, so the ghast's 1-in-20 rule and cluster of 1 count), read from the live spawn lists
     * after the biome modifiers ran. Vanilla's own monster weights are unchanged and its mobs match the table. A biome
     * with tuned weights lands in the range with its carrying mobs alone and with the lava mobs on top. In a share-capped
     * biome the split by roster weight doesn't land there (the warped forest has only the enderman at weight 1, 4 mobs,
     * so its Teeming monsters at weight 1 each would be 60 %; the crimson forest's nine kinds all round to weight 1, 15 %
     * carrying, under the band). Its list holds no Teeming monster, and for any number of other
     * monsters round the player the spawner's cap settles in the range, or, where whole mobs can't, on the whole number
     * nearest the target.
     */
    private static void spawnShare(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        List<String> wrong = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Map.Entry<String, Integer> e : SpawnTable.VANILLA_MONSTER_WEIGHT.entrySet()) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(e.getKey()));
            Holder.Reference<Biome> biome = biomes.getOrThrow(key);
            MobSpawnSettings settings = biome.value().getModifiedEnvironmentAttributes()
                    .applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
            int vanilla = 0;
            int ours = 0;
            String name = key.identifier().getPath();
            List<ListShare.Entry> entries = new ArrayList<>();
            for (Weighted<MobSpawnSettings.SpawnerData> w : settings.getMobsInCategory(MobCategory.MONSTER).unwrap()) {
                entries.add(new ListShare.Entry(w.value().type().builtInRegistryHolder().key().identifier().toString(), w.weight(),
                        w.value().count().minInclusive(), w.value().count().maxInclusive()));
                if (teeming(w.value().type())) {
                    ours += w.weight();
                    RosterStats.Entry row = RosterStats.BY_ID.get(w.value().type().builtInRegistryHolder().key().identifier().getPath());
                    if (row != null && row.apex()) wrong.add(name + ": the apex " + row.id() + " is on its list; only the spawner's rare apex rule places it");
                } else {
                    vanilla += w.weight();
                }
            }
            if (vanilla != e.getValue()) wrong.add(name + ": vanilla monster weight " + vanilla + ", the biome file has " + e.getValue());
            boolean capped = biome.is(TeemingSpawner.SHARE_CAPPED);
            if (capped != SpawnTable.SHARE_CAPPED.contains(e.getKey())) wrong.add(name + ": share-capped " + capped + " against the table");
            if (capped) {
                if (ours != 0) wrong.add(name + ": share-capped, yet its list holds Teeming monster weight " + ours);
                for (int others = 1; others <= 70; others++) {
                    int t = 0;
                    while (TeemingSpawner.shareAllows(t, others)) t++;
                    double got = share(t, others);
                    double off = Math.abs(got - SpawnTable.TARGET_SHARE);
                    boolean nearest = off <= Math.abs(share(t + 1, others) - SpawnTable.TARGET_SHARE)
                            && (t == 0 || off <= Math.abs(share(t - 1, others) - SpawnTable.TARGET_SHARE));
                    if (!(got >= SpawnTable.SHARE_MIN && got <= SpawnTable.SHARE_MAX) && !nearest)
                        wrong.add(name + ": with " + others + " other monsters the cap stops at " + t + " Teeming (" + got + ")");
                }
                seen.add(name + " share-capped (vanilla " + vanilla + ")");
            } else {
                String costs = costsMatch(settings);
                if (costs != null) wrong.add(name + ": " + costs);
                ListShare.Shares got = ListShare.of(entries, e.getKey());
                Double table = SpawnTable.VANILLA_NETHER_MOBS.get(e.getKey());
                if (table == null || Math.abs(table - got.vanillaLand()) > 0.01)
                    wrong.add(name + ": vanilla mobs " + got.vanillaLand() + " on the live list, the table has " + table);
                for (var check : List.of(Map.entry("carrying", got.carrying()), Map.entry("with the lava mobs", got.surface()))) {
                    if (check.getValue() < SpawnTable.SHARE_MIN || check.getValue() > SpawnTable.SHARE_MAX)
                        wrong.add(String.format(Locale.ROOT, "%s: Teeming %s share %.1f %% of the mobs", name, check.getKey(), check.getValue() * 100));
                }
                seen.add(String.format(Locale.ROOT, "%s in mobs: carrying %.1f %%, with the lava mobs %.1f %% (picks %d/%d)", name,
                        got.carrying() * 100, got.surface() * 100, ours, ours + vanilla));
            }
        }
        if (TeemingSpawner.shareAllows(0, 0)) wrong.add("the cap adds a Teeming monster where no other monster is");
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        TeemingMod.LOGGER.info("spawn_share: {}", String.join(", ", seen));
        helper.succeed();
    }

    /**
     * In each Overworld biome with a Teeming biome-list monster Teeming makes about 1 in 4 to 6 of the monsters
     * (17-25 %), counted in mobs (each entry's weight times its mean group, since a pick places its whole group and
     * vanilla's land monsters come four at a time), read from the live spawn lists after the biome modifiers ran, with
     * vanilla's own monster weights unchanged. Each kind is held against the vanilla mobs that can stand where it does
     * ({@link ListShare}): the carrying land mobs alone, then with the narrow-place mobs (the land above y 0) and with
     * the below-y-0 mobs (the land below y 0), each against vanilla's land mobs (the slime and the ocelot uncounted), and
     * the open-water mobs against the drowned, at the whole weight nearest the target, at least 1. A biome with no
     * vanilla monsters is share-capped and its list holds no Teeming monster.
     */
    private static void spawnShareOverworld(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        List<String> wrong = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Map.Entry<String, Integer> e : SpawnTable.VANILLA_OVERWORLD_MONSTER_WEIGHT.entrySet()) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(e.getKey()));
            Holder.Reference<Biome> biome = biomes.getOrThrow(key);
            MobSpawnSettings settings = biome.value().getModifiedEnvironmentAttributes()
                    .applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
            String name = key.identifier().getPath();
            int vanilla = 0, drowned = 0, ours = 0, wetWeight = 0, wetRoster = 0;
            List<ListShare.Entry> entries = new ArrayList<>();
            List<RosterStats.Entry> wetRows = new ArrayList<>();
            var monsters = settings.getMobsInCategory(MobCategory.MONSTER); // null where the biome lists no monster at all
            for (Weighted<MobSpawnSettings.SpawnerData> w : monsters == null ? List.<Weighted<MobSpawnSettings.SpawnerData>>of() : monsters.unwrap()) {
                EntityType<?> type = w.value().type();
                entries.add(new ListShare.Entry(type.builtInRegistryHolder().key().identifier().toString(), w.weight(),
                        w.value().count().minInclusive(), w.value().count().maxInclusive()));
                if (!teeming(type)) {
                    vanilla += w.weight();
                    if (type == EntityTypes.DROWNED) drowned += w.weight();
                    continue;
                }
                ours += w.weight();
                RosterStats.Entry row = RosterStats.BY_ID.get(type.builtInRegistryHolder().key().identifier().getPath());
                if (row == null) continue;
                if (row.apex()) wrong.add(name + ": the apex " + row.id() + " is on its list");
                if (row.dim() != RosterStats.Dim.OVERWORLD) wrong.add(name + ": " + row.id() + ", a " + row.dim() + " mob, is on its list");
                if (ListShare.kind(row) == ListShare.Kind.WET) {
                    wetWeight += w.weight();
                    wetRoster += row.weight();
                    wetRows.add(row);
                }
            }
            if (vanilla != e.getValue()) wrong.add(name + ": vanilla monster weight " + vanilla + ", the biome file has " + e.getValue());
            int water = SpawnTable.VANILLA_OVERWORLD_WATER_WEIGHT.get(e.getKey());
            if (drowned != water) wrong.add(name + ": vanilla open-water weight " + drowned + ", the table has " + water);
            boolean capped = biome.is(TeemingSpawner.SHARE_CAPPED);
            if (capped != SpawnTable.SHARE_CAPPED.contains(e.getKey())) wrong.add(name + ": share-capped " + capped + " against the table");
            if (capped) {
                if (ours != 0) wrong.add(name + ": share-capped, yet its list holds Teeming monster weight " + ours);
                if (vanilla != 0) wrong.add(name + ": share-capped with vanilla monster weight " + vanilla + " to share with");
                seen.add(name + " share-capped (vanilla " + vanilla + ")");
                continue;
            }
            ListShare.Shares got = ListShare.of(entries, e.getKey());
            Double table = SpawnTable.VANILLA_OVERWORLD_LAND_MOBS.get(e.getKey());
            if (table == null || Math.abs(table - got.vanillaLand()) > 0.01)
                wrong.add(name + ": vanilla land mobs " + got.vanillaLand() + " on the live list, the table has " + table);
            for (var check : List.of(Map.entry("carrying", got.carrying()), Map.entry("above y 0", got.surface()), Map.entry("below y 0", got.below()))) {
                if (check.getValue() < SpawnTable.SHARE_MIN || check.getValue() > SpawnTable.SHARE_MAX)
                    wrong.add(String.format(Locale.ROOT, "%s: Teeming %s share %.1f %% of the land mobs", name, check.getKey(), check.getValue() * 100));
            }
            for (RosterStats.Entry row : wetRows) {
                // the open-water rule: the mobs nearest the target share against the drowned, its roster weight's part, at least 1
                double want = SpawnTable.TARGET_SHARE / (1 - SpawnTable.TARGET_SHARE) * got.vanillaWater() * row.weight() / wetRoster
                        / ((row.groupMin() + row.groupMax()) / 2.0);
                int rule = Math.max(1, (int) Math.round(want));
                int live = entries.stream().filter(x -> x.type().equals(TeemingMod.MODID + ":" + row.id())).mapToInt(ListShare.Entry::weight).sum();
                if (live != rule) wrong.add(name + ": open-water weight of " + row.id() + " " + live + " against the drowned's " + drowned + ", the rule gives " + rule);
            }
            seen.add(String.format(Locale.ROOT, "%s in mobs: carrying %.1f %%, above y 0 %.1f %%, below %.1f %%%s", name, got.carrying() * 100,
                    got.surface() * 100, got.below() * 100, wetWeight > 0 ? " (open water " + wetWeight + " against drowned " + drowned + ")" : ""));
        }
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        TeemingMod.LOGGER.info("spawn_share_overworld: {}", String.join(", ", seen));
        helper.succeed();
    }

    /**
     * A biome mod's biomes get the spawn table's weights on NeoForge as on Fabric: each Overworld monster's rest
     * modifier ({@code spawns/<mob>}) carries {@link SpawnTable#REST}'s weight, and a split rest's beside part
     * ({@code spawns/<mob>/beside}) its beside weight; each outer-island End mob's {@code spawns/<mob>/modded_end}
     * carries {@link SpawnTable#MODDED_END}'s weight on a biome set that holds none of vanilla's five End biomes.
     */
    private static void restWeights(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var modifiers = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        var biomes = access.lookupOrThrow(Registries.BIOME);
        List<String> wrong = new ArrayList<>();
        BiConsumer<String, Integer> weighs = (name, want) -> {
            var modifier = modifiers.get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id(name))).map(Holder::value).orElse(null);
            if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) {
                wrong.add(name + ": no spawn modifier");
                return;
            }
            int got = add.spawners().unwrap().getFirst().weight();
            if (got != want) wrong.add(name + ": weight " + got + ", the table has " + want);
            if (name.endsWith("/modded_end"))
                for (String id : SpawnTable.VANILLA_END_BIOMES)
                    if (add.biomes().contains(biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse(id)))))
                        wrong.add(name + " reaches the vanilla biome " + id);
        };
        for (SpawnTable.Rest rest : SpawnTable.REST) {
            weighs.accept("spawns/" + rest.mob(), rest.weight());
            if (rest.beside() >= 0) weighs.accept("spawns/" + rest.mob() + "/beside", rest.beside());
        }
        for (Map.Entry<String, Integer> e : SpawnTable.MODDED_END.entrySet()) weighs.accept("spawns/" + e.getKey() + "/modded_end", e.getValue());
        if (biomes.get(TeemingSpawns.MODDED_END).isEmpty()) wrong.add("no #teeming:modded_end tag");
        helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        helper.succeed();
    }

    /**
     * A biome set as a biome modifier's JSON writes it (a biome, a tag, a list, or NeoForge's {@code and}, {@code or},
     * {@code not} and {@code any}), asked whether it holds a biome, with {@code #teeming:share_capped} read as also
     * holding one more biome: a pack that adds that biome to the tag, without changing the live tags other tests read.
     * Teeming's own tags are read from their JSON, so a tag that includes {@code #teeming:share_capped} sees the added
     * biome; every other tag is the live one.
     */
    private static final class BiomeSetJson {
        private final ResourceManager resources;
        private final Map<Identifier, JsonArray> tags = new HashMap<>();

        BiomeSetJson(ResourceManager resources) {
            this.resources = resources;
        }

        JsonElement modifier(Identifier id) {
            return read(Identifier.fromNamespaceAndPath(id.getNamespace(), "neoforge/biome_modifier/" + id.getPath() + ".json")).getAsJsonObject().get("biomes");
        }

        private JsonElement read(Identifier file) {
            try (Reader in = resources.openAsReader(file)) {
                return JsonParser.parseReader(in);
            } catch (IOException e) {
                throw new IllegalStateException("can't read " + file, e);
            }
        }

        boolean holds(JsonElement set, Holder.Reference<Biome> biome, @Nullable ResourceKey<Biome> capped) {
            if (set.isJsonPrimitive()) return entry(set.getAsString(), biome, capped);
            if (set.isJsonArray()) {
                for (JsonElement e : set.getAsJsonArray()) if (holds(e, biome, capped)) return true;
                return false;
            }
            JsonObject o = set.getAsJsonObject();
            String type = o.get("type").getAsString();
            switch (type) {
                case "neoforge:and" -> {
                    for (JsonElement e : o.getAsJsonArray("values")) if (!holds(e, biome, capped)) return false;
                    return true;
                }
                case "neoforge:or" -> {
                    for (JsonElement e : o.getAsJsonArray("values")) if (holds(e, biome, capped)) return true;
                    return false;
                }
                case "neoforge:not" -> {
                    return !holds(o.get("value"), biome, capped);
                }
                case "neoforge:any" -> {
                    return true;
                }
                default -> throw new IllegalStateException("a biome set of type " + type);
            }
        }

        private boolean entry(String ref, Holder.Reference<Biome> biome, @Nullable ResourceKey<Biome> capped) {
            if (!ref.startsWith("#")) return biome.key().identifier().equals(Identifier.parse(ref));
            Identifier tag = Identifier.parse(ref.substring(1));
            if (tag.equals(TeemingMod.id("share_capped")) && biome.key().equals(capped)) return true;
            if (!tag.getNamespace().equals(TeemingMod.MODID)) return biome.is(TagKey.create(Registries.BIOME, tag));
            JsonArray values = tags.computeIfAbsent(tag, t -> read(Identifier.fromNamespaceAndPath(t.getNamespace(),
                    "tags/worldgen/biome/" + t.getPath() + ".json")).getAsJsonObject().getAsJsonArray("values"));
            for (JsonElement v : values) {
                String inner = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                if (entry(inner, biome, capped)) return true;
            }
            return false;
        }
    }

    /**
     * {@code #teeming:share_capped} takes a biome off every Teeming monster's list, tuned biomes included, and leaves the
     * creatures' lists alone. Every Teeming spawn modifier's JSON, read as a biome set, holds exactly the biomes NeoForge
     * bound it to, so the reading is the loader's. Then each biome whose live list holds a Teeming monster, none of them
     * share-capped, is read as if a pack had added it to the tag: no monster modifier may hold it any more (an Overworld
     * biome so gets no Teeming monster, and a Nether one is left to the spawner's share-capped placements), and every
     * creature modifier that held it still does.
     */
    private static void shareCappedLists(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var access = helper.getLevel().registryAccess();
        var modifiers = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        List<Holder.Reference<Biome>> biomes = access.lookupOrThrow(Registries.BIOME).listElements().toList();
        BiomeSetJson json = new BiomeSetJson(server.getResourceManager());
        List<String> wrong = new ArrayList<>();
        Map<Identifier, JsonElement> monsters = new HashMap<>(), creatures = new HashMap<>();
        Map<Identifier, BiomeModifiers.AddSpawnsBiomeModifier> live = new HashMap<>();
        modifiers.listElements().forEach(ref -> {
            Identifier id = ref.key().identifier();
            if (!id.getNamespace().equals(TeemingMod.MODID) || !(ref.value() instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) return;
            live.put(id, add);
            MobCategory category = add.spawners().unwrap().getFirst().value().type().getCategory();
            (category == MobCategory.MONSTER ? monsters : creatures).put(id, json.modifier(id));
        });
        if (monsters.isEmpty() || creatures.isEmpty()) wrong.add("found " + monsters.size() + " monster and " + creatures.size() + " creature spawn modifiers");
        List<Holder.Reference<Biome>> listed = new ArrayList<>();
        for (Holder.Reference<Biome> biome : biomes) {
            boolean monster = false;
            for (var e : live.entrySet()) {
                JsonElement set = monsters.containsKey(e.getKey()) ? monsters.get(e.getKey()) : creatures.get(e.getKey());
                boolean bound = e.getValue().biomes().contains(biome);
                if (json.holds(set, biome, null) != bound)
                    wrong.add(e.getKey().getPath() + " read from its JSON " + (bound ? "misses " : "holds ") + biome.key().identifier().getPath());
                if (bound && monsters.containsKey(e.getKey())) monster = true;
            }
            if (monster && biome.is(TeemingSpawner.SHARE_CAPPED)) wrong.add(biome.key().identifier().getPath() + " is share-capped, yet a Teeming monster's list holds it");
            if (monster) listed.add(biome);
        }
        if (listed.size() < 20) wrong.add("only " + listed.size() + " biomes list a Teeming monster");
        int tuned = 0;
        for (Holder.Reference<Biome> biome : listed) {
            String name = biome.key().identifier().getPath();
            for (var e : monsters.entrySet()) {
                if (json.holds(e.getValue(), biome, biome.key())) wrong.add(name + " added to #teeming:share_capped stays in " + e.getKey().getPath());
                if (e.getKey().getPath().endsWith("/" + name)) tuned++;
            }
            for (var e : creatures.entrySet())
                if (live.get(e.getKey()).biomes().contains(biome) && !json.holds(e.getValue(), biome, biome.key()))
                    wrong.add(name + " added to #teeming:share_capped leaves the creature list " + e.getKey().getPath());
        }
        if (tuned == 0) wrong.add("no tuned monster modifier was read");
        String shown = String.join("; ", wrong.subList(0, Math.min(20, wrong.size())));
        helper.assertTrue(wrong.isEmpty(), shown + (wrong.size() > 20 ? " (" + wrong.size() + " in all)" : ""));
        TeemingMod.LOGGER.info("spawn_share_capped_lists: {} monster and {} creature modifiers read; {} biomes list Teeming monsters "
                + "({} tuned modifiers), and each one added to the tag leaves every monster list", monsters.size(), creatures.size(), listed.size(), tuned);
        helper.succeed();
    }

    /**
     * A surface mob (the Shellguard Crab, the Thornhorn) spawns naturally only where the spot sees the sky, so the
     * caves under its biomes stay free of it: open sand takes it, the same sand under a roof refuses it, and an egg
     * still places it there. A passive ground mob without the rule (the Petalkin) takes both. Built high over far
     * ground, where nothing else stands.
     */
    private static void surfaceOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RosterStats.Entry crab = RosterStats.get("shellguard_crab");
        RosterStats.Entry thornhorn = RosterStats.get("thornhorn");
        RosterStats.Entry petalkin = RosterStats.get("petalkin");
        helper.assertTrue(crab.surface() && thornhorn.surface(), "the Shellguard Crab's and the Thornhorn's rows hold the sky rule");
        helper.assertTrue(!petalkin.surface(), "the Petalkin's row doesn't hold the sky rule");
        int x = 1800;
        int y = 280;
        int z = -1000;
        level.getChunk(x >> 4, z >> 4);
        level.setChunkForced(x >> 4, z >> 4, true);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -2; dz <= 2; dz++) level.setBlockAndUpdate(new BlockPos(x + dx, y, z + dz), Blocks.SAND.defaultBlockState());
        BlockPos open = new BlockPos(x - 2, y + 1, z);
        BlockPos roofed = new BlockPos(x + 2, y + 1, z);
        level.setBlockAndUpdate(roofed.above(2), Blocks.STONE.defaultBlockState());
        RandomSource random = RandomSource.create(7);
        helper.succeedWhen(() -> {
            helper.assertTrue(level.canSeeSky(open), "the open spot sees the sky");
            helper.assertTrue(!level.canSeeSky(roofed), "the roofed spot doesn't see the sky");
            var crabType = TeemingSpawns.type(crab).orElseThrow();
            var hornType = TeemingSpawns.type(thornhorn).orElseThrow();
            var petalType = TeemingSpawns.type(petalkin).orElseThrow();
            helper.assertTrue(TeemingSpawns.check(crab, crabType, level, EntitySpawnReason.NATURAL, open, random), "the crab is taken on open sand");
            helper.assertTrue(!TeemingSpawns.check(crab, crabType, level, EntitySpawnReason.NATURAL, roofed, random), "the crab is refused on roofed sand");
            helper.assertTrue(TeemingSpawns.check(crab, crabType, level, EntitySpawnReason.SPAWN_ITEM_USE, roofed, random), "an egg still places it under a roof");
            helper.assertTrue(TeemingSpawns.check(thornhorn, hornType, level, EntitySpawnReason.NATURAL, open, random), "the Thornhorn is taken on open ground");
            helper.assertTrue(!TeemingSpawns.check(thornhorn, hornType, level, EntitySpawnReason.NATURAL, roofed, random), "the Thornhorn is refused under a roof");
            helper.assertTrue(TeemingSpawns.check(petalkin, petalType, level, EntitySpawnReason.NATURAL, roofed, random), "the Petalkin is taken on roofed sand");
            level.setChunkForced(x >> 4, z >> 4, false);
        });
    }

    /**
     * In each End biome, read from the live spawn lists: vanilla's monsters are unchanged, and Teeming's share of the
     * monsters, counted as mobs (each entry's weight times its mean group, so the endermen's groups of 4 count), stays
     * at or under {@link SpawnTable#END_SHARE_MAX}; with all 40 End rows in, each outer biome's live share is its planned
     * share and lands near its target (half, a third on the small islands), and the main island's list holds none of
     * them. The spawner's own End cap counts Teeming monsters alone: it has room
     * with any number of endermen about, and stops at half the vanilla per-player cap.
     */
    private static void spawnShareEnd(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        List<String> wrong = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Map.Entry<String, Integer> e : SpawnTable.VANILLA_END_MONSTER_WEIGHT.entrySet()) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(e.getKey()));
            MobSpawnSettings settings = biomes.getOrThrow(key).value().getModifiedEnvironmentAttributes()
                    .applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
            int vanilla = 0;
            double oursMobs = 0.0, vanillaMobs = 0.0;
            for (Weighted<MobSpawnSettings.SpawnerData> w : settings.getMobsInCategory(MobCategory.MONSTER).unwrap()) {
                var count = w.value().count();
                double mobs = w.weight() * (count.minInclusive() + count.maxInclusive()) / 2.0;
                if (teeming(w.value().type())) {
                    oursMobs += mobs;
                } else {
                    vanilla += w.weight();
                    vanillaMobs += mobs;
                }
            }
            String name = key.identifier().getPath();
            if (vanilla != e.getValue()) wrong.add(name + ": vanilla monster weight " + vanilla + ", the biome file has " + e.getValue());
            double got = oursMobs + vanillaMobs > 0 ? oursMobs / (oursMobs + vanillaMobs) : 0.0;
            if (got > SpawnTable.END_SHARE_MAX) wrong.add(String.format(Locale.ROOT, "%s: Teeming %.0f of %.0f mobs, %.1f %%", name, oursMobs, oursMobs + vanillaMobs, got * 100));
            // every End mob is in, so the live lists must give each outer biome its planned share (about half, a third on
            // the small islands) and the main island none from its list; deleting a biome modifier fails this
            Double plan = SpawnTable.END_PLANNED_SHARE.get(e.getKey());
            if (plan == null) {
                if (oursMobs > 0) wrong.add(String.format(Locale.ROOT, "%s: no biome-list Teeming monster expected, %.0f mobs", name, oursMobs));
            } else {
                double target = e.getKey().equals("minecraft:small_end_islands") ? 1.0 / 3.0 : SpawnTable.END_SHARE;
                if (Math.abs(got - target) > SpawnTable.END_PLAN_RANGE || Math.abs(got - plan) > 0.001)
                    wrong.add(String.format(Locale.ROOT, "%s: live share %.1f %%, planned %.1f %%, target %.1f %%", name, got * 100, plan * 100, target * 100));
            }
            seen.add(String.format(Locale.ROOT, "%s %.1f %%", name, got * 100));
        }
        // the plan: the weights are set from all 40 End rows, so once every End mob is in, the three larger outer biomes
        // reach about half and the small islands about a third, never past the most
        for (Map.Entry<String, Double> e : SpawnTable.END_PLANNED_SHARE.entrySet()) {
            double target = e.getKey().equals("minecraft:small_end_islands") ? 1.0 / 3.0 : SpawnTable.END_SHARE;
            double plan = e.getValue();
            if (Math.abs(plan - target) > SpawnTable.END_PLAN_RANGE || plan > SpawnTable.END_SHARE_MAX)
                wrong.add(String.format(Locale.ROOT, "%s: planned share %.1f %%, target %.1f %%", e.getKey(), plan * 100, target * 100));
        }
        if (SpawnTable.END_PLANNED_SHARE.size() != 4) wrong.add("a plan for the four outer End biomes, got " + SpawnTable.END_PLANNED_SHARE.keySet());
        if (!TeemingSpawner.endAllows(0)) wrong.add("the End cap refuses with no Teeming monster about");
        // a biome mod's End biome: one more Teeming monster only while they don't outnumber the others round the spot
        if (!TeemingSpawner.outnumberAllows(0, 0)) wrong.add("a biome mod's End biome refuses the first Teeming monster");
        if (TeemingSpawner.outnumberAllows(1, 0)) wrong.add("a biome mod's End biome takes a second Teeming monster with no other about");
        if (!TeemingSpawner.outnumberAllows(5, 5) || TeemingSpawner.outnumberAllows(6, 5))
            wrong.add("a biome mod's End biome stops at as many Teeming monsters as others, plus one");
        if (!TeemingSpawner.endAllows(34)) wrong.add("the End cap refuses at 34 Teeming monsters");
        if (TeemingSpawner.endAllows(35)) wrong.add("the End cap allows a 36th Teeming monster, past half of 70");
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        TeemingMod.LOGGER.info("spawn_share_end: {}", String.join(", ", seen));
        helper.succeed();
    }

    /**
     * Where every vanilla monster of a list carries one spawn cost, every Teeming monster must carry the same one: the
     * cost throttles each spawn, so without it Teeming's share of real spawns would run far past its share of the
     * weights (the soul sand valley's 0.7 / 0.15). Null when they match or vanilla's monsters have no common cost.
     */
    static String costsMatch(MobSpawnSettings settings) {
        MobSpawnSettings.MobSpawnCost common = null;
        boolean first = true;
        List<EntityType<?>> ours = new ArrayList<>();
        for (Weighted<MobSpawnSettings.SpawnerData> w : settings.getMobsInCategory(MobCategory.MONSTER).unwrap()) {
            EntityType<?> type = w.value().type();
            if (teeming(type)) {
                ours.add(type);
                continue;
            }
            MobSpawnSettings.MobSpawnCost cost = settings.getMobSpawnCost(type);
            if (first) common = cost;
            else if (cost == null || !cost.equals(common)) common = null;
            first = false;
        }
        if (common == null) return null;
        for (EntityType<?> type : ours) {
            if (!common.equals(settings.getMobSpawnCost(type)))
                return type.builtInRegistryHolder().key().identifier().getPath() + " has spawn cost " + settings.getMobSpawnCost(type)
                        + ", vanilla's monsters there " + common;
        }
        return null;
    }

    /** The share cap's count: monsters only (a pig and a Woolcap Grazer are creatures), Teeming ones apart from the rest. */
    private static void shareCounts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
        int[] before = TeemingSpawner.monstersAround(level, center);
        List<Mob> placed = new ArrayList<>();
        for (int i = 0; i < 3; i++) placed.add(helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2 + i * 2, 1, 2)));
        placed.add(helper.spawnWithNoFreeWill(ModMobs.VEINLEECH.get(), new BlockPos(2, 1, 6)));
        placed.add(helper.spawnWithNoFreeWill(ModMobs.WOOLCAP_GRAZER.get(), new BlockPos(5, 1, 8)));
        placed.add(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 8)));
        int[] after = TeemingSpawner.monstersAround(level, center);
        placed.forEach(Mob::discard);
        helper.assertTrue(after[0] - before[0] == 1, "one more Teeming monster counted, got " + (after[0] - before[0]));
        helper.assertTrue(after[1] - before[1] == 3, "three more other monsters counted, got " + (after[1] - before[1]));
        helper.succeed();
    }

    /**
     * The spawner chooses the spot first and picks only among the mobs whose rule accepts it. Built in the GameTest
     * server's flat Nether (all basalt deltas), far from the arenas, with part of it made a crimson forest:
     * a wart block in the crimson forest goes to the Gleamtrap (fungus-cap rule, its tag holds the crimson forest) and
     * to the crimson forest's biome-list monsters on top of it (the forest is share-capped, so the spawner places them),
     * never to a fortress or bastion mob, as no structure is there; the plain crimson floor goes to the biome-list
     * monsters and not the Gleamtrap; a wart block in the basalt deltas goes to nobody (the Gleamtrap's tag leaves the
     * deltas out, and the deltas' Teeming monsters come from its own spawn list). A structure's spot goes to that
     * structure's mobs: a fortress candidate beside a biome one always wins, and two fortress mobs (weight 0, read as 1)
     * share the rolls.
     */
    private static void picksBySpot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        int x0 = 4096;
        int z0 = 4096;
        for (int cx = x0 >> 4; cx <= (x0 + 63) >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= (z0 + 15) >> 4; cz++) nether.getChunk(cx, cz);
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), String.format(Locale.ROOT,
                "execute in minecraft:the_nether run fillbiome %d 0 %d %d 60 %d minecraft:crimson_forest", x0, z0, x0 + 31, z0 + 15));
        int y = nether.getHeight(Heightmap.Types.MOTION_BLOCKING, x0 + 4, z0 + 4);
        BlockPos cap = new BlockPos(x0 + 4, y, z0 + 4);
        BlockPos deltasCap = new BlockPos(x0 + 48, y, z0 + 8);
        nether.setBlockAndUpdate(cap, Blocks.NETHER_WART_BLOCK.defaultBlockState());
        nether.setBlockAndUpdate(deltasCap, Blocks.NETHER_WART_BLOCK.defaultBlockState());
        RandomSource random = RandomSource.create(7);
        Holder<Biome> crimson = nether.getBiome(cap.above());
        helper.assertTrue(crimson.is(ResourceKey.create(Registries.BIOME, Identifier.parse(CRIMSON))),
                "the filled area is a crimson forest, got " + crimson.getRegisteredName());
        List<String> wrong = new ArrayList<>();

        List<Candidate> onCap = TeemingSpawner.candidatesAt(nether, cap.getX(), y, cap.getZ(), random, false);
        checkAccepted(nether, onCap, wrong);
        if (onCap.stream().noneMatch(c -> c.mob().id().equals("gleamtrap") && c.rule() == Rule.FUNGUS_CAP && c.pos().equals(cap.above())))
            wrong.add("the wart block's top should go to the Gleamtrap: " + ids(onCap));
        if (onCap.stream().noneMatch(c -> c.mob().id().equals("veinleech") && c.rule() == null))
            wrong.add("the crimson forest's Veinleech should stand on the cap too: " + ids(onCap));
        if (onCap.stream().anyMatch(Candidate::structure)) wrong.add("no structure here, yet " + ids(onCap));

        List<Candidate> plain = TeemingSpawner.candidatesAt(nether, x0 + 20, y, z0 + 8, random, false);
        checkAccepted(nether, plain, wrong);
        if (plain.stream().anyMatch(c -> c.mob().id().equals("gleamtrap"))) wrong.add("no wart block, yet the Gleamtrap: " + ids(plain));
        if (plain.stream().noneMatch(c -> c.mob().id().equals("veinleech"))) wrong.add("the plain crimson floor should take the Veinleech: " + ids(plain));
        if (plain.stream().anyMatch(c -> c.mob().id().equals("prismback"))) wrong.add("the Prismback lives in the wastes: " + ids(plain));

        List<Candidate> deltas = TeemingSpawner.candidatesAt(nether, deltasCap.getX(), y, deltasCap.getZ(), random, false);
        if (!deltas.isEmpty()) wrong.add("a wart block in the basalt deltas should take nothing: " + ids(deltas));

        BlockPos at = cap.above();
        Candidate jailer = new Candidate(RosterStats.get("fortress_jailer"), ModMobs.FORTRESS_JAILER.get(), at, Rule.FORTRESS);
        Candidate herald = new Candidate(RosterStats.get("pyre_herald"), ModMobs.PYRE_HERALD.get(), at, Rule.FORTRESS);
        Candidate leech = new Candidate(RosterStats.get("veinleech"), ModMobs.VEINLEECH.get(), at, null);
        int jailers = 0;
        for (int i = 0; i < 400; i++) {
            Candidate got = TeemingSpawner.pick(List.of(leech, jailer, herald), random);
            if (got == leech) {
                wrong.add("a biome mob took a fortress spot");
                break;
            }
            if (got == jailer) jailers++;
        }
        if (jailers < 140 || jailers > 260) wrong.add("two fortress mobs should share the rolls, the Jailer took " + jailers + " of 400");
        nether.setBlockAndUpdate(cap, Blocks.BASALT.defaultBlockState());
        nether.setBlockAndUpdate(deltasCap, Blocks.BASALT.defaultBlockState());
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }

    /**
     * The apex rule, for the Basalt Behemoth (its absence from every biome list is in spawn_share). Built in the GameTest
     * server's flat Nether (all basalt deltas), apart from the other spawn tests:
     * without a won apex roll the open floor offers no Behemoth; with one, the Behemoth takes the floor (its rule, its
     * placement, room for its body and its tag all accept the spot) and wins the pick over the other candidates there;
     * a Behemoth 200 blocks off keeps a second away, one 262 blocks off doesn't (one per 256 blocks); a won roll in a
     * patch made crimson forest, outside its tag, offers none. The rarity: 16000 seeded rolls win 1 in 80 within three
     * standard deviations (158 to 242), and at one full attempt per player every 200 ticks that is one won roll every
     * 10 to 20 minutes, even with a quarter of won rolls lost to a column without room, light or a cap.
     */
    private static void apex(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        RosterStats.Entry row = RosterStats.get("basalt_behemoth");
        var type = ModMobs.BASALT_BEHEMOTH.get();
        int x0 = 12288;
        int z0 = 4096;
        for (int dx : new int[] {0, 200, 262, 400}) nether.getChunk((x0 + dx) >> 4, z0 >> 4);
        nether.setChunkForced(x0 >> 4, z0 >> 4, true);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), String.format(Locale.ROOT,
                "execute in minecraft:the_nether run fillbiome %d 0 %d %d 60 %d minecraft:crimson_forest", x0 + 400, z0, x0 + 415, z0 + 15));
        int y = nether.getHeight(Heightmap.Types.MOTION_BLOCKING, x0 + 8, z0 + 8);
        List<String> wrong = new ArrayList<>();
        RandomSource random = RandomSource.create(11);

        List<Candidate> noRoll = TeemingSpawner.candidatesAt(nether, x0 + 8, y, z0 + 8, random, false, false);
        if (noRoll.stream().anyMatch(c -> c.mob() == row)) wrong.add("without a won roll the floor offered the Behemoth: " + ids(noRoll));
        List<Candidate> rolled = TeemingSpawner.candidatesAt(nether, x0 + 8, y, z0 + 8, random, false, true);
        Candidate behemoth = rolled.stream().filter(c -> c.mob() == row).findFirst().orElse(null);
        if (behemoth == null) {
            wrong.add("a won roll on open basalt-delta floor should offer the Behemoth: " + ids(rolled));
        } else {
            if (behemoth.rule() != Rule.APEX_GROUND || !behemoth.apex()) wrong.add("the Behemoth came by rule " + behemoth.rule());
            checkAccepted(nether, rolled, wrong);
            if (!TeemingSpawns.placeOk(row, type, nether, behemoth.pos())) wrong.add("its placement refuses " + behemoth.pos().toShortString());
            if (!nether.noCollision(type.getSpawnAABB(behemoth.pos().getX() + 0.5, behemoth.pos().getY(), behemoth.pos().getZ() + 0.5)))
                wrong.add("no room for its body at " + behemoth.pos().toShortString());
            if (!nether.getBiome(behemoth.pos()).is(TeemingSpawns.tag(row.id()))) wrong.add("outside its tag at " + behemoth.pos().toShortString());
            Candidate leech = new Candidate(RosterStats.get("veinleech"), ModMobs.VEINLEECH.get(), behemoth.pos(), null);
            for (int i = 0; i < 200; i++) {
                if (TeemingSpawner.pick(List.of(leech, behemoth), random) != behemoth) {
                    wrong.add("another candidate took the spot of a won apex roll");
                    break;
                }
            }
        }
        List<Candidate> crimson = TeemingSpawner.candidatesAt(nether, x0 + 408, y, z0 + 8, random, false, true);
        if (crimson.stream().anyMatch(c -> c.mob() == row)) wrong.add("a crimson forest, outside its tag, offered the Behemoth");

        int wins = 0;
        RandomSource rolls = RandomSource.create(5);
        for (int i = 0; i < 16000; i++) if (TeemingSpawner.rollsApex(rolls)) wins++;
        if (wins < 158 || wins > 242) wrong.add("16000 rolls won " + wins + " times, want 1 in 80 (158 to 242)");
        double minutes = (1.0 / SpawnTable.APEX_CHANCE) * TeemingSpawner.EVERY / 20.0 / 60.0;
        if (minutes < 10.0 || minutes / 0.75 > 20.0) wrong.add(String.format(Locale.ROOT, "a won roll every %.1f minutes, %.1f with a quarter lost", minutes, minutes / 0.75));

        // one per 256 blocks: a Behemoth at the forced chunk keeps a second from 200 blocks off, not from 262
        BlockPos first = new BlockPos(x0 + 8, y, z0 + 8);
        Mob standing = type.create(nether, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        standing.snapTo(first.getX() + 0.5, first.getY(), first.getZ() + 0.5);
        standing.setNoAi(true);
        standing.setPersistenceRequired();
        nether.addFreshEntity(standing);
        helper.runAtTickTime(5, () -> {
            if (TeemingSpawner.apexClear(nether, type, first.east(200))) wrong.add("a Behemoth 200 blocks off should keep the spot clear of a second");
            if (!TeemingSpawner.apexClear(nether, type, first.east(262))) wrong.add("a Behemoth 262 blocks off should leave the spot free");
            List<Candidate> near = TeemingSpawner.candidatesAt(nether, first.getX() + 200, y, first.getZ(), random, false, true);
            if (near.stream().anyMatch(c -> c.mob() == row)) wrong.add("a won roll 200 blocks from a Behemoth offered a second");
            List<Candidate> far = TeemingSpawner.candidatesAt(nether, first.getX() + 262, y, first.getZ(), random, false, true);
            if (far.stream().noneMatch(c -> c.mob() == row)) wrong.add("a won roll 262 blocks from a Behemoth should offer one: " + ids(far));
            standing.discard();
            nether.setChunkForced(x0 >> 4, z0 >> 4, false);
            if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
            helper.succeed();
        });
    }

    /**
     * Runs {@code body} with the main island open (the dragon gate's setting off), and puts the loader's own setting back
     * before returning, within the same tick, so no other test sees it.
     */
    private static void islandOpen(Runnable body) {
        NeoForgeConfig loader = new NeoForgeConfig();
        TeemingConfig.use(new TeemingConfig.Source() {
            @Override public double spawningDensity() { return loader.spawningDensity(); }
            @Override public boolean mobEnabled(String id) { return loader.mobEnabled(id); }
            @Override public double spawnChance(String id) { return loader.spawnChance(id); }
            @Override public boolean endMainIslandAfterDragon() { return false; }
            @Override public int killsToReveal() { return loader.killsToReveal(); }
            @Override public boolean temporaryBlocks() { return loader.temporaryBlocks(); }
            @Override public int localCap() { return loader.localCap(); }
            @Override public double trophyChance() { return loader.trophyChance(); }
        });
        try {
            body.run();
        } finally {
            TeemingConfig.use(loader);
        }
    }

    /** An end stone floor 9 by 9 round the column at y {@code floor}, with 10 empty blocks over it. */
    private static void islandFloor(ServerLevel end, int x, int floor, int z) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                end.setBlockAndUpdate(new BlockPos(x + dx, floor, z + dz), Blocks.END_STONE.defaultBlockState());
                for (int dy = 1; dy <= 10; dy++) end.setBlockAndUpdate(new BlockPos(x + dx, floor + dy, z + dz), Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** The Monolith's candidate among those found, or null. */
    private static @Nullable Candidate monolith(List<Candidate> found) {
        return found.stream().filter(c -> c.mob().id().equals("liftstone_monolith")).findFirst().orElse(null);
    }

    /**
     * The main-island apex rule, for the Liftstone Monolith. Built in the GameTest server's End on an end stone floor at
     * (600, 120, 600), inside the main island's region and over open void, and on one at (1300, 120, 600), past it. The
     * main island is opened by a setting without the dragon gate, swapped in and back within each tick.
     *
     * <p>The gate: with the dragon gate on and no dragon killed, a won roll offers no Monolith; past the main island
     * (1300 out) none either. The roll: a column without a won roll offers it nowhere; 4800 seeded attempts as the
     * spawner makes them (roll, candidates, pick) give the Monolith exactly on the won rolls, 1 in 80 within three
     * standard deviations (37 to 83; its old weight-0-as-1 pick among the main-island mobs made it about 1 in 9). The
     * other main-island mobs are unaffected: the Astral Augur takes the same main-island spot with or without the roll,
     * and on the attempts without one it wins its own weight's part of the picks (within four standard deviations).
     * The whole box: 3 blocks of headroom under a slab refuse the Monolith's 4.0-block body and 4 accept it; a block
     * beside its feet, inside its 1.6-wide box, refuses it while the Augur still takes the spot, and a block 2 off
     * doesn't. The distance: a Monolith 200 blocks off keeps a second away, one 262 blocks off doesn't (none within 256,
     * twice its 128-block local cap, as for the Basalt Behemoth).
     */
    private static void apexIsland(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) {
            helper.fail("the GameTest server has no End");
            return;
        }
        RosterStats.Entry row = RosterStats.get("liftstone_monolith");
        var type = ModMobs.LIFTSTONE_MONOLITH.get();
        int x = 600;
        int z = 600;
        int floor = 120;
        int y = floor + 1;
        BlockPos spot = new BlockPos(x, y, z);
        int[] standingX = {x - 200, x - 262};
        for (int sx : standingX) {
            end.getChunk(sx >> 4, z >> 4);
            end.setChunkForced(sx >> 4, z >> 4, true);
        }
        end.setChunkForced(x >> 4, z >> 4, true);
        end.getChunk(1300 >> 4, z >> 4);
        islandFloor(end, x, floor, z);
        islandFloor(end, 1300, floor, z);
        List<String> wrong = new ArrayList<>();
        Rule rule = Rule.of(row.spawner());
        if (rule != Rule.APEX_ISLAND || !rule.apex() || rule.dimension != Level.END) wrong.add("the Monolith's rule is '" + row.spawner() + "'");

        if (TeemingConfig.endMainIslandAfterDragon() && !TeemingSpawner.mainIslandOpen(end)) {
            List<Candidate> shut = TeemingSpawner.candidatesAt(end, x, y, z, RandomSource.create(3), false, true);
            if (monolith(shut) != null) wrong.add("with no dragon killed a won roll offered the Monolith");
        }
        islandOpen(() -> {
            RandomSource random = RandomSource.create(17);
            List<Candidate> noRoll = TeemingSpawner.candidatesAt(end, x, y, z, random, false, false);
            List<Candidate> rolled = TeemingSpawner.candidatesAt(end, x, y, z, random, false, true);
            if (monolith(noRoll) != null) wrong.add("without a won roll the main island offered the Monolith: " + ids(noRoll));
            Candidate m = monolith(rolled);
            if (m == null) {
                wrong.add("a won roll on open main-island ground should offer the Monolith: " + ids(rolled));
            } else {
                if (m.rule() != Rule.APEX_ISLAND || !m.apex() || !m.pos().equals(spot)) wrong.add("the Monolith came by " + m.rule() + " at " + m.pos().toShortString());
                if (!end.noCollision(type.getSpawnAABB(m.pos().getX() + 0.5, m.pos().getY(), m.pos().getZ() + 0.5))) wrong.add("no room for its body at its spot");
            }
            checkAccepted(end, rolled, wrong);
            Candidate augur = noRoll.stream().filter(c -> c.mob().id().equals("astral_augur")).findFirst().orElse(null);
            Candidate augurRolled = rolled.stream().filter(c -> c.mob().id().equals("astral_augur")).findFirst().orElse(null);
            if (augur == null || augur.rule() != Rule.MAIN_ISLAND || !augur.pos().equals(spot)) wrong.add("the Augur should take the main-island spot: " + ids(noRoll));
            if (augurRolled == null || !augurRolled.pos().equals(spot)) wrong.add("the Augur should take it on a won roll too: " + ids(rolled));
            if (TeemingSpawner.candidatesAt(end, 1300, y, z, random, false, true).stream().anyMatch(c -> c.mob() == row))
                wrong.add("1300 blocks out, past the main island, offered the Monolith");

            // the roll, as the spawner makes it: one roll per attempt, then the candidates, then the pick
            int total = 0;
            for (Candidate c : noRoll) total += Math.max(1, c.mob().weight());
            double augurPart = augur == null || total == 0 ? 0.0 : Math.max(1, augur.mob().weight()) / (double) total;
            RandomSource attempts = RandomSource.create(5);
            int won = 0, monoliths = 0, plain = 0, augurs = 0;
            for (int i = 0; i < 4800; i++) {
                boolean apex = TeemingSpawner.rollsApex(attempts);
                List<Candidate> found = TeemingSpawner.candidatesAt(end, x, y, z, attempts, false, apex);
                if (found.isEmpty()) {
                    wrong.add("an attempt found no candidate on open main-island ground");
                    break;
                }
                Candidate pick = TeemingSpawner.pick(found, attempts);
                if (apex) won++;
                else plain++;
                if (pick.mob() == row) {
                    monoliths++;
                    if (!apex) wrong.add("an attempt without a won roll picked the Monolith");
                } else if (apex) {
                    wrong.add("a won roll on open ground went to " + pick.mob().id());
                }
                if (!apex && pick.mob().id().equals("astral_augur")) augurs++;
            }
            if (monoliths < 37 || monoliths > 83) wrong.add("4800 attempts gave " + monoliths + " Monoliths, want 1 in 80 (37 to 83)");
            if (monoliths != won) wrong.add(monoliths + " Monoliths from " + won + " won rolls");
            double mean = plain * augurPart;
            double sd = Math.sqrt(plain * augurPart * (1.0 - augurPart));
            if (Math.abs(augurs - mean) > 4.0 * sd) wrong.add(String.format(Locale.ROOT, "the Augur won %d of %d plain picks, want %.0f (its part %.3f)", augurs, plain, mean, augurPart));

            // the whole box: 4.0 tall, 1.6 wide
            BlockPos head = spot.above(3);
            end.setBlockAndUpdate(head, Blocks.END_STONE_BRICK_SLAB.defaultBlockState());
            if (monolith(TeemingSpawner.candidatesAt(end, x, y, z, random, false, true)) != null) wrong.add("3 blocks of headroom took its 4-block body");
            end.setBlockAndUpdate(head, Blocks.AIR.defaultBlockState());
            end.setBlockAndUpdate(spot.above(4), Blocks.END_STONE_BRICK_SLAB.defaultBlockState());
            Candidate fourHigh = monolith(TeemingSpawner.candidatesAt(end, x, y, z, random, false, true));
            if (fourHigh == null || !fourHigh.pos().equals(spot)) wrong.add("4 blocks of headroom should take its body");
            end.setBlockAndUpdate(spot.above(4), Blocks.AIR.defaultBlockState());
            end.setBlockAndUpdate(spot.east().above(), Blocks.END_STONE.defaultBlockState());
            List<Candidate> beside = TeemingSpawner.candidatesAt(end, x, y, z, random, false, true);
            if (monolith(beside) != null) wrong.add("a block beside its feet, inside its 1.6-wide box, took it: " + ids(beside));
            if (beside.stream().noneMatch(c -> c.mob().id().equals("astral_augur") && c.pos().equals(spot)))
                wrong.add("the Augur's narrower body should still take the spot: " + ids(beside));
            end.setBlockAndUpdate(spot.east().above(), Blocks.AIR.defaultBlockState());
            end.setBlockAndUpdate(spot.east(2).above(), Blocks.END_STONE.defaultBlockState());
            if (monolith(TeemingSpawner.candidatesAt(end, x, y, z, random, false, true)) == null) wrong.add("a block 2 off is outside its box");
            end.setBlockAndUpdate(spot.east(2).above(), Blocks.AIR.defaultBlockState());
        });

        // the distance: a Monolith 200 blocks off keeps the spot clear of a second, one 262 blocks off doesn't
        Mob[] standing = {stand(end, type, standingX[0], y, z)};
        helper.runAtTickTime(5, () -> {
            if (TeemingSpawner.apexClear(end, type, spot)) wrong.add("a Monolith 200 blocks off should keep the spot clear of a second");
            islandOpen(() -> {
                if (monolith(TeemingSpawner.candidatesAt(end, x, y, z, RandomSource.create(9), false, true)) != null)
                    wrong.add("a won roll 200 blocks from a Monolith offered a second");
            });
            standing[0].discard();
            standing[0] = stand(end, type, standingX[1], y, z);
        });
        helper.runAtTickTime(10, () -> {
            if (!TeemingSpawner.apexClear(end, type, spot)) wrong.add("a Monolith 262 blocks off should leave the spot free");
            islandOpen(() -> {
                if (monolith(TeemingSpawner.candidatesAt(end, x, y, z, RandomSource.create(9), false, true)) == null)
                    wrong.add("a won roll 262 blocks from a Monolith should offer one");
            });
            standing[0].discard();
            for (int sx : standingX) end.setChunkForced(sx >> 4, z >> 4, false);
            end.setChunkForced(x >> 4, z >> 4, false);
            if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
            helper.succeed();
        });
    }

    /**
     * End mobs spawn from their lists on open island ground, as the enderman does. Natural spawning asks the mob itself
     * whether it would stand at the spot and refuses a negative walk-target value, which the monster default gives
     * anywhere lit, and the End's islands lie under full sky light. On an end stone floor at (1400, 150, -600) in the
     * GameTest server's End, once the sky light there is full: a zombie, with the monster default, is refused (so the
     * spot is lit), the enderman is taken, and so is every End monster that spawns from a biome list.
     */
    private static void endOpenSky(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            helper.fail("the GameTest server has no End");
            return;
        }
        int x = 1400;
        int floor = 150;
        int z = -600;
        end.getChunk(x >> 4, z >> 4);
        end.setChunkForced(x >> 4, z >> 4, true);
        islandFloor(end, x, floor, z);
        BlockPos spot = new BlockPos(x, floor + 1, z);
        helper.succeedWhen(() -> {
            int sky = end.getBrightness(LightLayer.SKY, spot);
            helper.assertTrue(sky == 15, "the spot is under full sky light, got " + sky);
            helper.assertTrue(!takes(end, EntityTypes.ZOMBIE, spot), "a zombie, with the monster default, is refused on sky-lit ground");
            helper.assertTrue(takes(end, EntityTypes.ENDERMAN, spot), "the enderman is taken on sky-lit End ground");
            List<String> refused = new ArrayList<>();
            int checked = 0;
            for (RosterStats.Entry row : RosterStats.ALL) {
                if (row.dim() != RosterStats.Dim.END || !row.biomeSpawns() || row.passive()) continue;
                var type = TeemingSpawns.type(row);
                if (type.isEmpty()) continue;
                checked++;
                if (!takes(end, type.get(), spot)) refused.add(row.id());
            }
            helper.assertTrue(checked > 0, "no End list monster is registered");
            helper.assertTrue(refused.isEmpty(), "End list monsters refused on open sky-lit ground: " + refused);
            end.setChunkForced(x >> 4, z >> 4, false);
        });
    }

    /** The ids of the candidates, as a set. */
    private static Set<String> idSet(List<Candidate> found) {
        Set<String> out = new HashSet<>();
        for (Candidate c : found) out.add(c.mob().id());
        return out;
    }

    /**
     * An emptied spawn tag stops a mob that is on the biome lists and also placed by a spawner rule, both ways, while a
     * spawner-only mob with an empty tag goes wherever its rule fits. In the GameTest server's End (all {@code the_end},
     * outside these mobs' own vanilla homes): on an end stone floor at (-600, 120, 300), on the main island, with the
     * main island open, the Astral Augur, the Nullcantor and the spawner-only Mending Crystalling take the main-island
     * spot; with all three tags read as emptied the Augur and the Cantor don't, and the Crystalling still does. Over open
     * void at (-1400, y, 700), past the main island, the Resonant Triad takes a sky spot, and with its tag emptied
     * doesn't in 20 seeded tries. The spawn command's answer at each goes from the spawner's yes to no. Tags read as
     * emptied through the spawn tag hook, put back within the tick.
     */
    private static void emptiedTag(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            helper.fail("the GameTest server has no End");
            return;
        }
        int x = -600, floor = 120, z = 300, skyX = -1400, skyZ = 700;
        for (int[] c : new int[][] {{x, z}, {skyX, skyZ}}) {
            end.getChunk(c[0] >> 4, c[1] >> 4);
            end.setChunkForced(c[0] >> 4, c[1] >> 4, true);
        }
        islandFloor(end, x, floor, z);
        BlockPos spot = new BlockPos(x, floor + 1, z);
        BlockPos air = new BlockPos(skyX, 100, skyZ);
        RosterStats.Entry augur = RosterStats.get("astral_augur"), cantor = RosterStats.get("nullcantor"), triad = RosterStats.get("resonant_triad");
        RosterStats.Entry crystalling = RosterStats.get("mending_crystalling");
        List<String> wrong = new ArrayList<>();
        for (RosterStats.Entry row : List.of(augur, cantor, triad)) {
            if (!row.biomeSpawns() || Rule.of(row.spawner()) == null) wrong.add(row.id() + " is not both on the lists and placed by a rule");
            if (TeemingSpawns.tagSize(end, row.id()) == 0) wrong.add(row.id() + "'s tag is empty by default");
        }
        if (crystalling.biomeSpawns() || Rule.of(crystalling.spawner()) != Rule.MAIN_ISLAND) wrong.add("the Mending Crystalling is not a main-island spawner-only mob");
        if (!Rule.SKY.fits(end, air)) wrong.add("no sky spot at " + air.toShortString());
        islandOpen(() -> {
            Set<String> main = idSet(TeemingSpawner.candidatesAt(end, x, floor + 1, z, RandomSource.create(5), false, false, Rule.MAIN_ISLAND));
            for (String id : List.of("astral_augur", "nullcantor", "mending_crystalling"))
                if (!main.contains(id)) wrong.add(id + " doesn't take the main-island spot with its tag: " + main);
            Set<String> sky = new HashSet<>();
            for (int i = 0; i < 20 && !sky.contains("resonant_triad"); i++)
                sky.addAll(idSet(TeemingSpawner.candidatesAt(end, skyX, 100, skyZ, RandomSource.create(i), false, false, Rule.SKY)));
            if (!sky.contains("resonant_triad")) wrong.add("the Triad takes no sky spot with its tag: " + sky);
            for (var c : List.of(Map.entry(augur, spot), Map.entry(cantor, spot), Map.entry(triad, air))) {
                String said = TeemingCommand.whereYouStand(end, c.getValue(), c.getKey());
                if (!said.equals("here_spawner")) wrong.add("the command says " + said + " for " + c.getKey().id() + " with its tag");
            }
            TeemingSpawns.withEmptiedTags(Set.of("astral_augur", "nullcantor", "resonant_triad", "mending_crystalling"), () -> {
                for (RosterStats.Entry row : List.of(augur, cantor, triad, crystalling))
                    if (TeemingSpawns.tagSize(end, row.id()) != 0) wrong.add(row.id() + "'s tag doesn't read as emptied");
                Set<String> emptied = idSet(TeemingSpawner.candidatesAt(end, x, floor + 1, z, RandomSource.create(5), false, false, Rule.MAIN_ISLAND));
                if (emptied.contains("astral_augur") || emptied.contains("nullcantor")) wrong.add("with their tags emptied the main island still offers " + emptied);
                if (!emptied.contains("mending_crystalling")) wrong.add("an emptied tag stops the spawner-only Crystalling: " + emptied);
                for (int i = 0; i < 20; i++) {
                    if (idSet(TeemingSpawner.candidatesAt(end, skyX, 100, skyZ, RandomSource.create(i), false, false, Rule.SKY)).contains("resonant_triad")) {
                        wrong.add("with its tag emptied the Triad still takes a sky spot");
                        break;
                    }
                }
                for (var c : List.of(Map.entry(augur, spot), Map.entry(cantor, spot), Map.entry(triad, air))) {
                    String said = TeemingCommand.whereYouStand(end, c.getValue(), c.getKey());
                    if (!said.equals("here_no")) wrong.add("with its tag emptied the command says " + said + " for " + c.getKey().id());
                }
            });
        });
        for (int[] c : new int[][] {{x, z}, {skyX, skyZ}}) end.setChunkForced(c[0] >> 4, c[1] >> 4, false);
        helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        helper.succeed();
    }

    /** Natural spawning's question to the mob itself at the spot: its own spawn rules (the walk-target value) and room. */
    private static boolean takes(ServerLevel level, EntityType<?> type, BlockPos pos) {
        if (!(type.create(level, EntitySpawnReason.NATURAL) instanceof Mob mob)) return false;
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        boolean ok = mob.checkSpawnRules(level, EntitySpawnReason.NATURAL) && mob.checkSpawnObstruction(level);
        mob.discard();
        return ok;
    }

    /**
     * An open-air rule's spot goes to a flier whose biome list puts it on the ground: the Resonant Triad's sky rule places
     * it, though its ground placement refuses every spot with air under it. In the GameTest server's End at
     * (1400, 200, -700), out past the main island under the open sky: the spot fits the sky rule and not the ground
     * placement, and the spawner places a triad there within 20 seeded tries (each passes the End's darkness roll about
     * half the time).
     */
    private static void openAirFlier(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            helper.fail("the GameTest server has no End");
            return;
        }
        BlockPos spot = new BlockPos(1400, 200, -700);
        end.getChunk(spot.getX() >> 4, spot.getZ() >> 4);
        end.setChunkForced(spot.getX() >> 4, spot.getZ() >> 4, true);   // so the placed mobs are found and removed
        RosterStats.Entry row = RosterStats.get("resonant_triad");
        var type = ModMobs.RESONANT_TRIAD.get();
        helper.assertTrue(Rule.of(row.spawner()) == Rule.SKY && Rule.SKY.openAir(), "the Triad's rule is the open-air sky, got " + row.spawner());
        helper.assertTrue(row.place() == Place.GROUND, "its biome list puts it on the ground, got " + row.place());
        helper.assertTrue(Rule.SKY.fits(end, spot), "open sky at " + spot.toShortString());
        helper.assertTrue(!TeemingSpawns.placeOk(row, type, end, spot), "its ground placement refuses the open-air spot");
        helper.assertTrue(end.getDifficulty() != Difficulty.PEACEFUL, "monsters spawn at this difficulty");
        helper.runAtTickTime(5, () -> {
            RandomSource random = RandomSource.create(89);
            Mob first = null;
            for (int i = 0; i < 20 && first == null; i++) first = TeemingSpawner.spawnGroup(end, new Candidate(row, type, spot, Rule.SKY), random);
            List<Mob> placed = end.getEntitiesOfClass(Mob.class, new AABB(spot).inflate(8), e -> e.getType() == type);
            placed.forEach(Mob::discard);
            end.setChunkForced(spot.getX() >> 4, spot.getZ() >> 4, false);
            helper.assertTrue(first != null, "20 tries placed no Triad at an open-sky spot");
            helper.assertTrue(first.getType() == type && placed.contains(first), "the first of the group is a Triad at the spot, found " + placed.size());
            helper.succeed();
        });
    }

    /**
     * Each regular End rule takes its own turn: every one a row uses is a turn, and on the underside rule's turn only its
     * mobs are offered. Under a 9 x 9 island at (1400, 150, -900) in the GameTest server's End the column holds an
     * underside spot and, over the island, open sky: picked among all rules, the sky's fliers compete for it; on the
     * underside turn the Underclinger and the Abyss Angler alone take the spot under the island. The underside is found
     * however far under the player's height it lies, and from void 5 blocks off the island the void-air rule walks to
     * its edge and stands 3 blocks out, where a Brinkray's 3-wide body clears the rock. The GameTest server's End is
     * {@code the_end} everywhere, so the test paints end highlands round the island and puts the biome back.
     */
    private static void endTurns(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            helper.fail("the GameTest server has no End");
            return;
        }
        int x = 1400;
        int floor = 150;
        int z = -900;
        end.getChunk(x >> 4, z >> 4);
        end.setChunkForced(x >> 4, z >> 4, true);
        islandFloor(end, x, floor, z);
        BlockPos from = new BlockPos(x - 8, floor - 24, z - 8), to = new BlockPos(x + 8, floor + 60, z + 8);
        Holder<Biome> was = end.getBiome(new BlockPos(x, floor - 1, z));
        FillBiomeCommand.fill(end, from, to, end.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.END_HIGHLANDS));
        helper.assertTrue(TeemingSpawner.END_TURNS.equals(List.of(Rule.MAIN_ISLAND, Rule.UNDERSIDE, Rule.VOID_AIR, Rule.VOID_BELOW, Rule.SKY)),
                "the End's turns are its five regular rules, got " + TeemingSpawner.END_TURNS);
        helper.assertTrue(Rule.UNDERSIDE.edge() && Rule.VOID_AIR.edge() && !Rule.SKY.edge(), "the underside and void-air rules need edges");
        RandomSource random = RandomSource.create(81);
        List<Candidate> all = TeemingSpawner.candidatesAt(end, x, floor - 1, z, random, false, false);
        List<Candidate> turn = TeemingSpawner.candidatesAt(end, x, floor - 1, z, random, false, false, Rule.UNDERSIDE);
        BlockPos deep = Rule.UNDERSIDE.find(end, x, floor + 50, z, random);
        BlockPos beside = new BlockPos(x + 4 + TeemingSpawner.EDGE_OFF, floor + 1, z);
        List<BlockPos> walked = new ArrayList<>();
        RandomSource ways = RandomSource.create(83);
        for (int i = 0; i < 24; i++) {
            BlockPos at = Rule.VOID_AIR.find(end, x + 9, floor + 1, z, ways);
            if (at != null) walked.add(at);
        }
        boolean roomy = end.noCollision(ModMobs.BRINKRAY.get().getSpawnAABB(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5));
        FillBiomeCommand.fill(end, from, to, was);
        end.setChunkForced(x >> 4, z >> 4, false);
        helper.assertTrue(all.stream().anyMatch(c -> c.rule() == Rule.SKY), "among all rules the sky's fliers compete: " + ids(all));
        helper.assertTrue(turn.stream().allMatch(c -> c.rule() == Rule.UNDERSIDE), "the underside turn offers only underside mobs: " + ids(turn));
        Set<String> got = new HashSet<>();
        for (Candidate c : turn) got.add(c.mob().id());
        helper.assertTrue(got.equals(Set.of("underclinger", "abyss_angler")), "the Underclinger and the Abyss Angler, got " + got);
        helper.assertTrue(turn.stream().allMatch(c -> c.pos().equals(new BlockPos(x, floor - 1, z))), "at the spot under the island: " + ids(turn));
        helper.assertTrue(new BlockPos(x, floor - 1, z).equals(deep), "the underside is found 51 blocks under the player's height, got " + deep);
        helper.assertTrue(walked.contains(beside), "from void 5 blocks out a void-air spot is found 3 blocks off the island's edge, level "
                + "with its ground, " + beside.toShortString() + "; found " + walked);
        helper.assertTrue(walked.stream().allMatch(p -> p.equals(beside)), "and nowhere else round the lone island: " + walked);
        helper.assertTrue(roomy, "a Brinkray's body fits at " + beside.toShortString());
        helper.succeed();
    }

    /** A mob standing still at x, y, z, kept from despawning. */
    private static Mob stand(ServerLevel level, EntityType<? extends Mob> type, int x, int y, int z) {
        Mob mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(x + 0.5, y, z + 0.5);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }

    /** Every candidate's own rule (or, for a biome-list mob, its placement) accepts its spot, inside its biome tag. */
    private static void checkAccepted(ServerLevel level, List<Candidate> found, List<String> wrong) {
        for (Candidate c : found) {
            boolean ok = c.rule() != null ? c.rule().fits(level, c.pos()) : TeemingSpawns.placeOk(c.mob(), c.type(), level, c.pos());
            if (!ok) wrong.add(c.mob().id() + " at " + c.pos().toShortString() + ", which its rule refuses");
            if (c.rule() == null && !level.getBiome(c.pos()).is(TeemingSpawns.tag(c.mob().id())))
                wrong.add(c.mob().id() + " outside its tag at " + c.pos().toShortString());
        }
    }

    private static String ids(List<Candidate> found) {
        return found.stream().map(c -> c.mob().id() + (c.rule() == null ? "" : "/" + c.rule())).toList().toString();
    }

    // ---- looks named after a place -------------------------------------------------------------------------------

    /** A second look named after a place, the biome it belongs to, and another Nether home of the mob. */
    private record PlaceLook(String mob, String look, String place, String elsewhere) {}

    private static final String CRIMSON = "minecraft:crimson_forest";
    private static final String WARPED = "minecraft:warped_forest";
    private static final String VALLEY = "minecraft:soul_sand_valley";
    private static final String WASTES = "minecraft:nether_wastes";
    private static final String DELTAS = "minecraft:basalt_deltas";

    private static final List<PlaceLook> PLACE_LOOKS = List.of(
            new PlaceLook("phantasm_lantern", "crimson_lantern", CRIMSON, WARPED),
            new PlaceLook("woolcap_grazer", "crimson_woolcap", CRIMSON, WARPED),
            new PlaceLook("siphonjelly", "crimson_jelly", CRIMSON, WARPED),
            new PlaceLook("spore_cannon", "crimson_cannon", CRIMSON, WARPED),
            new PlaceLook("gloomweaver", "crimson_weaver", CRIMSON, WARPED),
            new PlaceLook("shredjaw_beetle", "warped_shredjaw", WARPED, CRIMSON),
            new PlaceLook("gleamtrap", "warped_gleamtrap", WARPED, CRIMSON),
            new PlaceLook("scarlet_mantis", "ashen_mantis", WASTES, CRIMSON),
            new PlaceLook("ash_jackal", "soul_jackal", VALLEY, WASTES),
            new PlaceLook("cindermoth_swarm", "soul_moths", VALLEY, WASTES),
            new PlaceLook("pyre_herald", "soul_herald", VALLEY, WASTES),
            new PlaceLook("flamewright", "soul_wright", VALLEY, WASTES),
            new PlaceLook("slagspitter", "basalt_spitter", DELTAS, WASTES));

    /** Mobs whose base look belongs to their other home, so in the place only the place look appears. */
    private static final Map<String, String> PLACE_BASES = Map.of(
            "phantasm_lantern", WARPED, "woolcap_grazer", WARPED, "siphonjelly", WARPED, "spore_cannon", WARPED,
            "gloomweaver", WARPED, "shredjaw_beetle", CRIMSON);

    private static Predicate<String> in(Holder<Biome> biome) {
        return spec -> spec.startsWith("#")
                ? biome.is(TagKey.create(Registries.BIOME, Identifier.parse(spec.substring(1))))
                : biome.is(ResourceKey.create(Registries.BIOME, Identifier.parse(spec)));
    }

    /** How many of 200 evenly spread picks in the biome give the look. */
    private static int picks(List<LookDef> looks, Holder<Biome> biome, String look) {
        int n = 0;
        for (int roll = 0; roll < 200; roll++) {
            int r = roll;
            if (LookDef.pick(looks, in(biome), bound -> r % bound).id().equals(look)) n++;
        }
        return n;
    }

    /**
     * Each second look named after a place appears only there, and the mob lives there: the look is limited to that
     * biome, the mob's live biome tag holds it, picks in that biome give the look, and picks in another of the mob's
     * Nether homes never do. Where the base look belongs to the other home, every pick in the place is the place look
     * and every pick in the other home is the base.
     */
    private static void placeLooks(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        List<String> wrong = new ArrayList<>();
        for (PlaceLook p : PLACE_LOOKS) {
            String where = p.mob() + "/" + p.look();
            LookDef look = Looks.get(p.mob(), p.look()).orElse(null);
            if (look == null) {
                wrong.add(where + " not loaded");
                continue;
            }
            if (!look.biomes().equals(p.place())) wrong.add(where + " is limited to '" + look.biomes() + "', not " + p.place());
            Holder<Biome> place = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse(p.place())));
            Holder<Biome> elsewhere = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse(p.elsewhere())));
            var tag = biomes.get(TeemingSpawns.tag(p.mob()));
            if (tag.isEmpty() || !tag.get().contains(place)) wrong.add(where + ": the mob doesn't live in " + p.place());
            if (tag.isEmpty() || !tag.get().contains(elsewhere)) wrong.add(where + ": the mob doesn't live in " + p.elsewhere());
            List<LookDef> looks = Looks.of(p.mob());
            int there = picks(looks, place, p.look());
            int away = picks(looks, elsewhere, p.look());
            if (there == 0) wrong.add(where + " never picked in " + p.place());
            if (away != 0) wrong.add(where + " picked " + away + " times in " + p.elsewhere());
            String baseHome = PLACE_BASES.get(p.mob());
            LookDef base = Looks.get(p.mob(), LookDef.BASE).orElseThrow();
            if (baseHome != null) {
                if (!base.biomes().equals(baseHome)) wrong.add(p.mob() + "/base is limited to '" + base.biomes() + "', not " + baseHome);
                if (there != 200) wrong.add(where + " is " + there + " of 200 picks in " + p.place() + ", want all");
                if (picks(looks, elsewhere, LookDef.BASE) != 200) wrong.add(p.mob() + "/base should be every pick in " + p.elsewhere());
            } else if (!base.biomes().isEmpty()) {
                wrong.add(p.mob() + "/base is limited to " + base.biomes() + ", though the look of its place shares it");
            }
        }
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }
}
