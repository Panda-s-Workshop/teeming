package com.panda.teeming.gametest;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.neoforge.NeoForgeConfig;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Place;
import com.panda.teeming.spawn.SpawnTable;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawner.Candidate;
import com.panda.teeming.spawn.TeemingSpawner.Rule;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The special placements (sand, lava, ceiling, leaves, void air, underside) accept their spot and refuse a
 * plain floor, and main-island spawns stay shut while the dragon lives. Spots are built in the arena (its floor is y 0).
 */
final class SpawnTests {
    private SpawnTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("placement_sand", h -> place(h, "dunefin", Place.SAND, Blocks.SAND));
        out.accept("placement_leaves", h -> place(h, "vinedrop_strangler", Place.LEAVES, Blocks.JUNGLE_LEAVES));
        out.accept("placement_lava", SpawnTests::lava);
        out.accept("placement_shallow", SpawnTests::shallow);
        out.accept("placement_ceiling", SpawnTests::ceiling);
        out.accept("placement_void_air", SpawnTests::voidAir);
        out.accept("placement_underside", SpawnTests::underside);
        out.accept("spawner_main_island_gate", SpawnTests::mainIslandGate);
        out.accept("spawner_spawns", SpawnTests::spawnerSpawns);
        out.accept("spawn_any_light", SpawnTests::anyLight);
        out.accept("spawn_share", SpawnTests::spawnShare);
        out.accept("spawn_share_counts", SpawnTests::shareCounts);
        out.accept("spawner_picks_by_spot", SpawnTests::picksBySpot);
        out.accept("place_looks", SpawnTests::placeLooks);
        out.accept("spawner_apex", SpawnTests::apex);
        out.accept("spawner_apex_island", SpawnTests::apexIsland);
    }

    private static boolean ok(GameTestHelper helper, String id, BlockPos rel) {
        RosterStats.Entry mob = RosterStats.get(id);
        return TeemingSpawns.placeOk(mob, ModMobs.THORNHORN.get(), helper.getLevel(), helper.absolutePos(rel));
    }

    /** A ground-kind placement: accepts a spot on its block, refuses one on the arena floor. */
    private static void place(GameTestHelper helper, String id, Place kind, net.minecraft.world.level.block.Block block) {
        helper.assertTrue(RosterStats.get(id).place() == kind, id + " should be a " + kind + " mob");
        helper.setBlock(new BlockPos(3, 1, 3), block);
        helper.assertTrue(ok(helper, id, new BlockPos(3, 2, 3)), id + " accepts a spot on " + block);
        helper.assertTrue(!ok(helper, id, new BlockPos(8, 1, 8)), id + " refuses the plain floor");
        helper.succeed();
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
        helper.succeed();
    }

    private static void lava(GameTestHelper helper) {
        helper.assertTrue(RosterStats.get("magma_tortoise").place() == Place.LAVA, "magma_tortoise should be a lava mob");
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.LAVA);
        helper.assertTrue(ok(helper, "magma_tortoise", new BlockPos(3, 1, 3)), "accepts lava with air above");
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        helper.assertTrue(!ok(helper, "magma_tortoise", new BlockPos(3, 1, 3)), "refuses lava under a block");
        helper.assertTrue(!ok(helper, "magma_tortoise", new BlockPos(8, 1, 8)), "refuses dry air");
        helper.succeed();
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
        helper.succeed();
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

    // ---- spawn share ----------------------------------------------------------------------------------------------

    private static boolean teeming(EntityType<?> type) {
        return type.builtInRegistryHolder().key().identifier().getNamespace().equals(TeemingMod.MODID);
    }

    private static double share(int teeming, int others) {
        return teeming + others == 0 ? 0.0 : teeming / (double) (teeming + others);
    }

    /**
     * In each Nether biome Teeming makes about 1 in 4 to 6 of the monster picks (17-25 %). Read from the live
     * spawn lists, after the biome modifiers ran. Vanilla's own monster weights are unchanged. A biome with tuned
     * weights lands in the range. A share-capped biome can't: its vanilla monster weight is too small for even weight 1
     * per Teeming mob (the warped forest has only the enderman at weight 1, so its six Teeming monsters would be 6 of 7
     * picks; the crimson forest's 15 against nine mobs would be 38 %). Its list holds no Teeming monster, and for any
     * number of other monsters round the player the spawner's cap settles in the range, or, where whole mobs can't, on
     * the whole number nearest the target.
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
            for (Weighted<MobSpawnSettings.SpawnerData> w : settings.getMobsInCategory(MobCategory.MONSTER).unwrap()) {
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
                double got = share(ours, vanilla);
                if (got < SpawnTable.SHARE_MIN || got > SpawnTable.SHARE_MAX)
                    wrong.add(String.format(Locale.ROOT, "%s: Teeming weight %d of %d, %.1f %%", name, ours, ours + vanilla, got * 100));
                seen.add(String.format(Locale.ROOT, "%s %d/%d = %.1f %%", name, ours, ours + vanilla, got * 100));
            }
        }
        if (TeemingSpawner.shareAllows(0, 0)) wrong.add("the cap adds a Teeming monster where no other monster is");
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        TeemingMod.LOGGER.info("spawn_share: {}", String.join(", ", seen));
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
