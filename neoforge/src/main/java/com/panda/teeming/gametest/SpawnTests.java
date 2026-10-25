package com.panda.teeming.gametest;

import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Place;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawner.Rule;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.Blocks;

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
        helper.setBlock(new BlockPos(3, 5, 3), Blocks.STONE);
        helper.assertTrue(ok(helper, "dripfang", new BlockPos(3, 4, 3)), "accepts air under stone");
        helper.assertTrue(!ok(helper, "dripfang", new BlockPos(8, 4, 8)), "refuses open air");
        helper.setBlock(new BlockPos(5, 5, 5), Blocks.OAK_FENCE);
        helper.assertTrue(!ok(helper, "dripfang", new BlockPos(5, 4, 5)), "refuses a fence, whose underside isn't sturdy");
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
}
