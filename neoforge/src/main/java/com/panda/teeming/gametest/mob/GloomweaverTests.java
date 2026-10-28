package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.Gloomweaver;
import com.panda.teeming.mob.nether.Gloomweaver.Step;
import com.panda.teeming.neoforge.NeoForgeConfig;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Gloomweaver: the 20-tick spin holds it still and places nothing, then a wall of warped wart goes up 5 wide and 3 tall
 * across the path to its target, 1.5 blocks short of it; a wall stands 200 ticks and it keeps at most 2. An axe swing
 * or fire on any part brings the whole wall down at once and snaps its threads (100 ticks without spinning); any other
 * blow only scratches it. Blocks go into air only, with the mob griefing rule, and every one is taken down again: at the
 * wall's end, on the weaver's death or removal, after a piston moves them, and after a reload. Up close it bites for 5.
 * Every number is checked against a plain value.
 *
 * <p>The standard set-up: the weaver at x 2.5, a still pig (no AI, 100 health) at x 8.5 on the same row, 6 blocks off.
 * Its wall then runs along z at x 7 (the point 1.5 short of the pig), from z 3 to 7 and y 1 to 3, in the arena's
 * coordinates. Every weaver here has its goals cleared and a follow range of 1, so it acts only on what the test gives
 * it, and leaves with the test (taking its walls with it).
 */
public final class GloomweaverTests {
    private GloomweaverTests() {}

    /** The standard wall's spots, in arena coordinates: x 7, z 3..7, y 1..3. */
    private static final int WALL_X = 7, WALL_Z0 = 3, WALL_Z1 = 7, WALL_Y0 = 1, WALL_Y1 = 3;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("gloomweaver_tell", GloomweaverTests::tell);
        out.accept("gloomweaver_counter", GloomweaverTests::axeBringsItDown);
        out.accept("gloomweaver_failure", GloomweaverTests::otherBlowsScratch);
        out.accept("gloomweaver_wall_size", GloomweaverTests::wallSize);
        out.accept("gloomweaver_wall_length", GloomweaverTests::wallLength);
        TeemingGameTests.timeLimit("gloomweaver_wall_length", 260);
        out.accept("gloomweaver_max_walls", GloomweaverTests::maxWalls);
        out.accept("gloomweaver_spin_range", GloomweaverTests::spinRange);
        out.accept("gloomweaver_fire", GloomweaverTests::fireBringsItDown);
        out.accept("gloomweaver_lava", GloomweaverTests::lavaBringsItDown);
        out.accept("gloomweaver_flint", GloomweaverTests::flintBringsItDown);
        out.accept("gloomweaver_burning_attacker", GloomweaverTests::burningSwingBringsItDown);
        out.accept("gloomweaver_burning_touch", GloomweaverTests::burningTouchBringsItDown);
        out.accept("gloomweaver_snapped_length", GloomweaverTests::snappedLength);
        TeemingGameTests.timeLimit("gloomweaver_snapped_length", 140);
        out.accept("gloomweaver_air_only", GloomweaverTests::airOnly);
        out.accept("no_grief_when_gamerule_off", GloomweaverTests::noGrief);
        out.accept("gloomweaver_config_off", GloomweaverTests::configOff);
        out.accept("temp_blocks_cleanup", GloomweaverTests::chunkUnloadCleanup);
        TeemingGameTests.timeLimit("temp_blocks_cleanup", CLEANUP_TICKS);
        out.accept("gloomweaver_wall_cancel", GloomweaverTests::wallCancel);
        out.accept("gloomweaver_death_cleanup", GloomweaverTests::deathCleanup);
        out.accept("gloomweaver_discard_cleanup", GloomweaverTests::discardCleanup);
        out.accept("gloomweaver_reload_cleanup", GloomweaverTests::reloadCleanup);
        out.accept("gloomweaver_piston", GloomweaverTests::piston);
        out.accept("gloomweaver_bite", GloomweaverTests::bite);
        out.accept("gloomweaver_stats", GloomweaverTests::stats);
        out.accept("gloomweaver_home", GloomweaverTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A weaver at {@code at} that acts only on what the test tells it: no goals, no follow range, the base look. */
    private static Gloomweaver weaver(GameTestHelper helper, Vec3 at) {
        Gloomweaver g = helper.spawn(ModMobs.GLOOMWEAVER.get(), at);
        g.core().setLook("base", true);
        g.removeFreeWill();
        g.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        g.resetCooldown();
        TestPlayers.whenDone(helper, g::discard);
        return g;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static Gloomweaver standardWeaver(GameTestHelper helper) {
        return weaver(helper, new Vec3(2.5, 1, 5.5));
    }

    private static Pig standardPig(GameTestHelper helper) {
        return pig(helper, new Vec3(8.5, 1, 5.5));
    }

    /** Warped or nether wart blocks anywhere in the arena (x and z 0..11, y 1..7), in arena coordinates. */
    private static List<BlockPos> wart(GameTestHelper helper) {
        List<BlockPos> found = new ArrayList<>();
        for (int x = 0; x < 12; x++) {
            for (int y = 1; y < 8; y++) {
                for (int z = 0; z < 12; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    var state = helper.getBlockState(p);
                    if (state.is(Blocks.WARPED_WART_BLOCK) || state.is(Blocks.NETHER_WART_BLOCK)) found.add(p);
                }
            }
        }
        return found;
    }

    /** True if every spot of the standard wall holds warped wart. */
    private static boolean standardWallStands(GameTestHelper helper) {
        for (int z = WALL_Z0; z <= WALL_Z1; z++) {
            for (int y = WALL_Y0; y <= WALL_Y1; y++) {
                if (!helper.getBlockState(new BlockPos(WALL_X, y, z)).is(Blocks.WARPED_WART_BLOCK)) return false;
            }
        }
        return true;
    }

    /** Item entities lying anywhere in the arena. */
    private static List<ItemEntity> items(GameTestHelper helper) {
        AABB arena = new AABB(helper.absoluteVec(new Vec3(0, 0, 0)), helper.absoluteVec(new Vec3(12, 8, 12)));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, arena);
    }

    /**
     * A survival player at x 5.5 facing the standard wall (east, level), holding {@code item}: its eye ray meets the
     * wall's middle row 1.5 blocks ahead.
     */
    private static ServerPlayer facingWall(GameTestHelper helper, Item item) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 5.5));
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
        p.snapTo(at.x, at.y, at.z, -90.0F, 0.0F);
        p.setYHeadRot(-90.0F);
        p.setItemInHand(InteractionHand.MAIN_HAND, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
        return p;
    }

    /** The player swings its main hand, as a left click does. */
    private static void swing(ServerPlayer p) {
        p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
    }

    /** The standard weaver raises the standard wall at tick 3; the rest of the test runs from {@code then} at tick 5. */
    private static void withWall(GameTestHelper helper, Gloomweaver g, Pig pig, Runnable then) {
        helper.runAtTickTime(3, () -> helper.assertTrue(g.raiseWall(helper.getLevel(), pig) != null, "the wall goes up"));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(standardWallStands(helper), "the wall stands at x 7, z 3..7, y 1..3");
            then.run();
        });
    }

    /** It snapped: the wall's spots are all empty, nothing dropped, its threads snapped and spinning is off. */
    private static void assertBroughtDown(GameTestHelper helper, Gloomweaver g, Pig pig, String how) {
        helper.assertTrue(wart(helper).isEmpty(), how + " brings the whole wall down at once, left " + wart(helper));
        helper.assertTrue(g.walls().size() == 0, "it has no wall left, " + g.walls().size());
        helper.assertTrue(items(helper).isEmpty(), "the wall drops nothing, " + items(helper).size() + " items");
        helper.assertTrue(g.core().in(Step.SNAPPED), "its threads snap, phase " + g.phase());
        g.resetCooldown();
        g.setTarget(pig);
        helper.assertTrue(!g.canSpin(pig), "snapped, it can't spin");
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * With a pig 6 blocks off it starts spinning at once, for 20 ticks, standing still, hurting nothing and placing no
     * block; the wall goes up as the spin ends.
     */
    private static void tell(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        g.setTarget(pig);
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] placed = {false}, walled = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (!g.core().in(Step.TELL)) {
                walled[0] |= ticks[0] > 0 && g.walls().size() == 1;
                return;
            }
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = g.position();
            }
            ticks[0]++;
            placed[0] |= !wart(helper).isEmpty();
            moved[0] = Math.max(moved[0], g.position().subtract(start[0]).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && !g.core().in(Step.TELL), "the spin runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(first[0] <= 3, "with its target 6 blocks off it spins at once, started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 20) <= 1, "the spin lasts 20 ticks, lasted " + ticks[0]);
            helper.assertTrue(!placed[0], "the spin places no block");
            helper.assertTrue(moved[0] < 0.1, "it stays put while it spins, moved " + moved[0]);
            helper.assertTrue(pig.getHealth() == 100.0F, "the spin hurts nothing");
            helper.assertTrue(walled[0] && standardWallStands(helper), "the wall goes up as the spin ends");
        });
    }

    /**
     * The counter: a survival player swings an iron axe at the wall's middle. The whole wall (15 blocks) is gone at
     * once, nothing drops, and the weaver's threads snap.
     */
    private static void axeBringsItDown(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        ServerPlayer p = facingWall(helper, Items.IRON_AXE);
        withWall(helper, g, pig, () -> swing(p));
        helper.runAtTickTime(7, () -> {
            assertBroughtDown(helper, g, pig, "an axe swing");
            helper.succeed();
        });
    }

    /**
     * Failing the counter: a sword swing and a bare-handed swing do nothing; a block mined out of the wall grows back at
     * once and its drop is taken away; the wall still stands 10 ticks later, the weaver isn't snapped, and the pig
     * behind the wall can't see the weaver.
     */
    private static void otherBlowsScratch(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        ServerPlayer p = facingWall(helper, Items.IRON_SWORD);
        withWall(helper, g, pig, () -> {
            helper.assertTrue(!pig.hasLineOfSight(g), "the pig can't see the weaver through the wall");
            swing(p);
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(standardWallStands(helper), "a sword swing only scratches it");
            ServerPlayer bare = facingWall(helper, Items.AIR);
            swing(bare);
        });
        helper.runAtTickTime(11, () -> {
            helper.assertTrue(standardWallStands(helper), "a bare-handed swing only scratches it");
            BlockPos mined = helper.absolutePos(new BlockPos(WALL_X, 2, 5));
            helper.getLevel().destroyBlock(mined, true, p);              // mined out, dropping the block
        });
        helper.runAtTickTime(13, () -> {
            helper.assertTrue(standardWallStands(helper), "the mined block grew back");
            helper.assertTrue(items(helper).isEmpty(), "and its drop is gone, " + items(helper).size() + " items");
        });
        helper.runAtTickTime(23, () -> {
            helper.assertTrue(standardWallStands(helper) && wart(helper).size() == 15, "the wall still stands, " + wart(helper).size() + " blocks");
            helper.assertTrue(!g.core().in(Step.SNAPPED), "its threads are whole");
            helper.assertTrue(!pig.hasLineOfSight(g), "and it still blocks sight");
            helper.succeed();
        });
    }

    // ---- the wall's numbers ------------------------------------------------------------------------------------------

    /**
     * Size: 15 blocks of warped wart in one straight row 5 wide (z 3 to 7) across the path, 3 tall (y 1 to 3), at x 7:
     * 1.5 blocks short of the pig, between it and the weaver.
     */
    private static void wallSize(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        helper.runAtTickTime(3, () -> helper.assertTrue(g.raiseWall(helper.getLevel(), pig) != null, "the wall goes up"));
        helper.runAtTickTime(5, () -> {
            List<BlockPos> w = wart(helper);
            helper.assertTrue(w.size() == 15, "15 blocks, got " + w.size());
            helper.assertTrue(w.stream().allMatch(b -> b.getX() == 7), "all at x 7, " + w);
            int minZ = w.stream().mapToInt(BlockPos::getZ).min().orElse(-1), maxZ = w.stream().mapToInt(BlockPos::getZ).max().orElse(-1);
            int minY = w.stream().mapToInt(BlockPos::getY).min().orElse(-1), maxY = w.stream().mapToInt(BlockPos::getY).max().orElse(-1);
            helper.assertTrue(minZ == 3 && maxZ == 7, "5 wide, z 3 to 7, got " + minZ + ".." + maxZ);
            helper.assertTrue(minY == 1 && maxY == 3, "3 tall, y 1 to 3, got " + minY + ".." + maxY);
            helper.assertTrue(standardWallStands(helper), "every spot of the 5 x 3 is filled");
            helper.assertTrue(g.walls().size() == 1 && g.walls().live().getFirst().cells().size() == 15, "and it records all 15");
            helper.succeed();
        });
    }

    /**
     * Length: the wall stands for 200 ticks and then every block of it is gone at once; it withers (cracks) through
     * the last 20, never before.
     */
    private static void wallLength(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        long[] born = {-1}, gone = {-1}, withering = {-1};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(g.raiseWall(helper.getLevel(), pig) != null, "the wall goes up");
            born[0] = helper.getLevel().getGameTime();
        });
        helper.onEachTick(() -> {
            if (born[0] < 0 || gone[0] >= 0) return;
            long now = helper.getLevel().getGameTime();
            if (withering[0] < 0 && g.walls().size() == 1 && g.walls().withering(g.walls().live().getFirst(), now)) withering[0] = now - born[0];
            if (wart(helper).isEmpty()) gone[0] = now - born[0];
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gone[0] >= 0, "the wall comes down by itself");
            helper.assertTrue(Math.abs(gone[0] - 200) <= 1, "it stands 200 ticks, stood " + gone[0]);
            helper.assertTrue(Math.abs(withering[0] - 180) <= 1, "it withers through its last 20, from " + withering[0]);
            helper.assertTrue(g.walls().size() == 0, "and is out of the record");
            helper.assertTrue(!g.core().in(Step.SNAPPED), "a wall that runs out snaps nothing");
        });
    }

    /** At most 2: two walls go up (to pigs east and south), a third is refused, and so is a spin, while both stand. */
    private static void maxWalls(GameTestHelper helper) {
        Gloomweaver g = weaver(helper, new Vec3(2.5, 1, 2.5));
        Pig east = pig(helper, new Vec3(9.5, 1, 2.5));
        Pig south = pig(helper, new Vec3(2.5, 1, 9.5));
        Pig diagonal = pig(helper, new Vec3(8.5, 1, 8.5));
        helper.runAtTickTime(3, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(g.raiseWall(level, east) != null, "the first wall goes up");
            helper.assertTrue(g.raiseWall(level, south) != null, "the second wall goes up");
            helper.assertTrue(g.walls().size() == 2, "2 walls stand, " + g.walls().size());
            helper.assertTrue(g.raiseWall(level, diagonal) == null, "a third is refused");
            helper.assertTrue(!g.spin(diagonal), "a spin is refused with 2 up");
            g.setTarget(diagonal);
            helper.assertTrue(!g.canSpin(diagonal), "and it won't start one");
            helper.assertTrue(wart(helper).size() == 30, "30 blocks in all, " + wart(helper).size());
            helper.succeed();
        });
    }

    /** Range: it spins at targets 5 and 11 blocks off, not at 3 (it bites) nor 12.7 (beyond 12). */
    private static void spinRange(GameTestHelper helper) {
        Gloomweaver g = weaver(helper, new Vec3(1.5, 1, 1.5));
        Pig near = pig(helper, new Vec3(4.5, 1, 1.5));
        Pig five = pig(helper, new Vec3(1.5, 1, 6.5));
        Pig eleven = pig(helper, new Vec3(1.5 + 7.78, 1, 1.5 + 7.78));
        Pig far = pig(helper, new Vec3(10.5, 1, 10.5));
        helper.runAtTickTime(5, () -> {
            g.resetCooldown();
            helper.assertTrue(!g.canSpin(near), "3 blocks off it doesn't spin");
            helper.assertTrue(g.canSpin(five), "5 blocks off it spins");
            helper.assertTrue(g.canSpin(eleven), "11 blocks off it spins, " + g.distanceTo(eleven));
            helper.assertTrue(!g.canSpin(far), "12.7 blocks off it doesn't, " + g.distanceTo(far));
            helper.succeed();
        });
    }

    // ---- fire on any part ----------------------------------------------------------------------------------------

    /** Fire lit on the ground at the wall's foot brings it down. */
    private static void fireBringsItDown(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        withWall(helper, g, pig, () -> helper.setBlock(new BlockPos(WALL_X - 1, 1, 6), Blocks.FIRE));
        helper.runAtTickTime(7, () -> {
            helper.setBlock(new BlockPos(WALL_X - 1, 1, 6), Blocks.AIR);
            assertBroughtDown(helper, g, pig, "fire at its foot");
            helper.succeed();
        });
    }

    /** Lava flowing up to its side brings it down. */
    private static void lavaBringsItDown(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        withWall(helper, g, pig, () -> helper.setBlock(new BlockPos(WALL_X - 1, 1, 4), Blocks.LAVA));
        helper.runAtTickTime(7, () -> {
            helper.setBlock(new BlockPos(WALL_X - 1, 1, 4), Blocks.AIR);
            assertBroughtDown(helper, g, pig, "lava");
            helper.succeed();
        });
    }

    /** A survival player striking the wall's middle row with a flint and steel brings it down. */
    private static void flintBringsItDown(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        ServerPlayer p = facingWall(helper, Items.FLINT_AND_STEEL);
        withWall(helper, g, pig, () -> swing(p));
        helper.runAtTickTime(7, () -> {
            assertBroughtDown(helper, g, pig, "a flint and steel");
            helper.succeed();
        });
    }

    /** A burning survival player swinging at it bare-handed brings it down. */
    private static void burningSwingBringsItDown(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        ServerPlayer p = facingWall(helper, Items.AIR);
        withWall(helper, g, pig, () -> {
            p.igniteForSeconds(5.0F);
            swing(p);
        });
        helper.runAtTickTime(7, () -> {
            p.clearFire();
            assertBroughtDown(helper, g, pig, "a burning attacker's swing");
            helper.succeed();
        });
    }

    /** A burning pig standing against the wall brings it down. */
    private static void burningTouchBringsItDown(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        Pig torch = pig(helper, new Vec3(6.4, 1, 3.5));              // its side 0.15 from the wall's face
        withWall(helper, g, pig, () -> torch.igniteForSeconds(5.0F));
        helper.runAtTickTime(7, () -> {
            torch.clearFire();
            assertBroughtDown(helper, g, pig, "a burning pig against it");
            helper.succeed();
        });
    }

    /**
     * Snapped threads last 100 ticks: through them it won't spin (ready, its target 6 blocks off and in sight); after
     * them it will.
     */
    private static void snappedLength(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        helper.runAtTickTime(5, () -> helper.assertTrue(g.snap(helper.getLevel()), "its threads snap"));
        int[] snapped = {0};
        boolean[] over = {false}, spun = {false}, ready = {false};
        helper.onEachTick(() -> {
            if (g.core().in(Step.SNAPPED)) {
                snapped[0]++;
                g.resetCooldown();
                spun[0] |= g.canSpin(pig);
            } else if (snapped[0] > 0 && !over[0]) {
                over[0] = true;
                g.resetCooldown();
                ready[0] = g.canSpin(pig);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the threads mend, " + snapped[0] + " ticks so far");
            helper.assertTrue(Math.abs(snapped[0] - 100) <= 1, "snapped for 100 ticks, " + snapped[0]);
            helper.assertTrue(!spun[0], "snapped it never may spin");
            helper.assertTrue(ready[0], "mended it may spin again");
        });
    }

    // ---- no griefing: into air only, the rule, the veto, and every block taken down ------------------------------------

    /**
     * Into air only: a stone block in one spot and a pig standing in another are left as they are (the stone stays
     * stone, no block goes into the pig); the other 13 spots are filled.
     */
    private static void airOnly(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        helper.setBlock(new BlockPos(WALL_X, 2, 5), Blocks.STONE);
        Pig inTheWay = pig(helper, new Vec3(7.5, 1, 7.5));
        helper.runAtTickTime(3, () -> helper.assertTrue(g.raiseWall(helper.getLevel(), pig) != null, "the wall goes up"));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(WALL_X, 2, 5)).is(Blocks.STONE), "the stone stays stone");
            helper.assertTrue(helper.getBlockState(new BlockPos(WALL_X, 1, 7)).isAir(), "no block goes into the pig");
            helper.assertTrue(wart(helper).size() == 13, "the other 13 spots are filled, " + wart(helper).size());
            helper.assertTrue(inTheWay.isAlive() && !inTheWay.isInWall(), "the pig isn't stuck in a block");
            g.discard();
            helper.assertTrue(helper.getBlockState(new BlockPos(WALL_X, 2, 5)).is(Blocks.STONE), "taking the wall down leaves the stone");
            helper.assertTrue(wart(helper).isEmpty(), "and removes the rest");
            helper.succeed();
        });
    }

    /**
     * With the mob griefing rule off it neither spins nor raises a wall; the rule is turned off and back on within one
     * tick, so no other test sees it.
     */
    private static void noGrief(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        helper.runAtTickTime(3, () -> {
            ServerLevel level = helper.getLevel();
            GameRules rules = level.getGameRules();
            boolean was = rules.get(GameRules.MOB_GRIEFING);
            boolean spun, can, walled;
            try {
                rules.set(GameRules.MOB_GRIEFING, false, level.getServer());
                can = g.canSpin(pig);
                spun = g.spin(pig);
                walled = g.raiseWall(level, pig) != null;
            } finally {
                rules.set(GameRules.MOB_GRIEFING, was, level.getServer());
            }
            helper.assertTrue(!can && !spun, "with mob griefing off it won't spin");
            helper.assertTrue(!walled && wart(helper).isEmpty(), "and raises no wall");
            helper.assertTrue(g.canSpin(pig), "with it on it may");
            helper.succeed();
        });
    }

    /**
     * The server's {@code griefing.temporary_blocks} setting turned off: it neither spins nor raises a wall, but still
     * bites for 5. The setting is swapped for one that says off and back to the loader's own within one tick, so no
     * other test sees it.
     */
    private static void configOff(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        helper.runAtTickTime(3, () -> {
            ServerLevel level = helper.getLevel();
            NeoForgeConfig loader = new NeoForgeConfig();
            TeemingConfig.Source off = new TeemingConfig.Source() {
                @Override public double spawningDensity() { return loader.spawningDensity(); }
                @Override public boolean mobEnabled(String id) { return loader.mobEnabled(id); }
                @Override public double spawnChance(String id) { return loader.spawnChance(id); }
                @Override public boolean endMainIslandAfterDragon() { return loader.endMainIslandAfterDragon(); }
                @Override public int killsToReveal() { return loader.killsToReveal(); }
                @Override public boolean temporaryBlocks() { return false; }
                @Override public int localCap() { return loader.localCap(); }
                @Override public double trophyChance() { return loader.trophyChance(); }
            };
            boolean can, spun, walled;
            TeemingConfig.use(off);
            try {
                can = g.canSpin(pig);
                spun = g.spin(pig);
                walled = g.raiseWall(level, pig) != null;
            } finally {
                TeemingConfig.use(loader);
            }
            helper.assertTrue(TeemingConfig.temporaryBlocks(), "the setting is on by default");
            helper.assertTrue(!can && !spun, "with temporary blocks off it won't spin");
            helper.assertTrue(!walled && wart(helper).isEmpty() && g.walls().size() == 0, "and raises no wall");
            helper.assertTrue(g.doHurtTarget(level, pig) && Math.abs(100.0F - pig.getHealth() - 5.0F) < 0.01F,
                    "it still bites for 5, the pig lost " + (100.0F - pig.getHealth()));
            helper.assertTrue(g.canSpin(pig), "with it on again it may spin");
            helper.succeed();
        });
    }

    /**
     * The cleanup test's time limit. A far chunk forced fresh ticks entities only once its neighbours have generated off
     * the main thread, which beside the full suite on CI can take hundreds of ticks; the test waits for that before it
     * builds, and again after the reload.
     */
    private static final int CLEANUP_TICKS = 1200;

    /**
     * A real chunk unload and reload: far from every arena (10,000 blocks east), a chunk is force-loaded and, once it
     * ticks entities, floored, and a weaver (no AI, kept from despawning) raises its wall there at a still pig. The chunk
     * is let go; once the weaver has been unloaded with it, the chunk is force-loaded again. When the weaver is back and
     * has ticked, no block of its wall is left anywhere in the chunk, and it holds no walls and no waiting spots. Out of
     * time, it says how far it got.
     */
    private static void chunkUnloadCleanup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int cx = (origin.getX() >> 4) + 625, cz = origin.getZ() >> 4;
        BlockPos base = new BlockPos(cx << 4, origin.getY(), cz << 4);      // the floor's corner; mobs stand a block up
        UUID[] ids = {null, null};
        int[] stage = {0};
        Gloomweaver[] first = {null};
        TestPlayers.whenDone(helper, () -> {
            for (UUID id : ids) {
                Entity e = id == null ? null : level.getEntity(id);
                if (e != null) e.discard();
            }
            if (level.hasChunk(cx, cz)) {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) level.setBlockAndUpdate(base.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                }
            }
            level.setChunkForced(cx, cz, false);
        });
        BlockPos middle = base.offset(8, 1, 8);
        helper.runAtTickTime(2, () -> {
            level.setChunkForced(cx, cz, true);
            level.getChunk(cx, cz);
        });
        helper.onEachTick(() -> {
            if (stage[0] != 0 || helper.getTick() <= 2 || !level.isPositionEntityTicking(middle)) return;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    level.setBlockAndUpdate(base.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
                    for (int dy = 1; dy <= 5; dy++) level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
            Gloomweaver g = ModMobs.GLOOMWEAVER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
            g.snapTo(base.getX() + 2.5, base.getY() + 1, base.getZ() + 8.5, 0.0F, 0.0F);
            pig.snapTo(base.getX() + 8.5, base.getY() + 1, base.getZ() + 8.5, 0.0F, 0.0F);
            for (var m : List.of(g, pig)) {
                m.setNoAi(true);
                m.setPersistenceRequired();
                level.addFreshEntity(m);
            }
            g.core().setLook("base", true);
            ids[0] = g.getUUID();
            ids[1] = pig.getUUID();
            helper.assertTrue(g.raiseWall(level, pig) != null, "the wall goes up in the far chunk");
            helper.assertTrue(wartIn(level, base) == 15, "15 blocks, " + wartIn(level, base));
            first[0] = g;
            level.setChunkForced(cx, cz, false);                             // let the chunk go
            stage[0] = 1;
        });
        helper.runAtTickTime(CLEANUP_TICKS - 1, () -> {
            if (stage[0] >= 3) return;
            Entity back = ids[0] == null ? null : level.getEntity(ids[0]);
            helper.fail(String.format("stopped at stage %d (0 wait for the chunk to tick, 1 wait for the unload, 2 wait for the "
                    + "reloaded weaver to tick): first removed %s, back %s ticked %d, chunk loaded %b, entities tick there %b",
                    stage[0], first[0] == null ? "-" : first[0].getRemovalReason(), back == null ? "no" : back == first[0] ? "same" : "yes",
                    back == null ? -1 : back.tickCount, level.hasChunk(cx, cz), level.isPositionEntityTicking(middle)));
        });
        helper.onEachTick(() -> {
            if (stage[0] == 1 && first[0].isRemoved()) {
                helper.assertTrue(first[0].getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                        "the weaver left with its chunk, " + first[0].getRemovalReason());
                stage[0] = 2;
                level.setChunkForced(cx, cz, true);                          // and load it again
            } else if (stage[0] == 2 && level.getEntity(ids[0]) instanceof Gloomweaver back && back != first[0] && back.tickCount > 2) {
                helper.assertTrue(wartIn(level, base) == 0, "no block of the wall is left after the reload, " + wartIn(level, base));
                helper.assertTrue(back.walls().size() == 0 && back.walls().waiting() == 0, "and the weaver holds none");
                stage[0] = 3;
                helper.succeed();
            }
        });
    }

    /** Warped wart blocks in the 16 x 16 chunk floored at {@code base}, from its floor up 6 blocks. */
    private static int wartIn(ServerLevel level, BlockPos base) {
        int n = 0;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dy = 0; dy <= 6; dy++) if (level.getBlockState(base.offset(dx, dy, dz)).is(Blocks.WARPED_WART_BLOCK)) n++;
            }
        }
        return n;
    }

    /** A listener that cancels the "wall" step stops the wall: the spin runs out, nothing is placed. */
    private static void wallCancel(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == g && e.step().equals("wall")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(3, () -> helper.assertTrue(g.spin(pig), "it spins"));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(g.core().idle(), "the spin runs out, phase " + g.phase());
            helper.assertTrue(wart(helper).isEmpty() && g.walls().size() == 0, "a cancelled wall places nothing");
            helper.succeed();
        });
    }

    /** Its death takes down both its walls at once: no block of them is left. */
    private static void deathCleanup(GameTestHelper helper) {
        Gloomweaver g = weaver(helper, new Vec3(2.5, 1, 2.5));
        Pig east = pig(helper, new Vec3(9.5, 1, 2.5));
        Pig south = pig(helper, new Vec3(2.5, 1, 9.5));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(g.raiseWall(helper.getLevel(), east) != null && g.raiseWall(helper.getLevel(), south) != null, "two walls go up");
            helper.assertTrue(wart(helper).size() == 30, "30 blocks, " + wart(helper).size());
            g.kill(helper.getLevel());
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(g.isDeadOrDying(), "it died");
            helper.assertTrue(wart(helper).isEmpty(), "every block went with it, left " + wart(helper));
            helper.assertTrue(items(helper).stream().noneMatch(i -> i.getItem().is(Items.WARPED_WART_BLOCK)), "and none dropped");
            helper.succeed();
        });
    }

    /** Taken out of the level any other way (discarded), its wall goes with it. */
    private static void discardCleanup(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        withWall(helper, g, pig, g::discard);
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(wart(helper).isEmpty(), "every block went with it, left " + wart(helper));
            helper.succeed();
        });
    }

    /**
     * After a reload: a weaver saved with its wall up, loaded again while the blocks still stand, takes them down on
     * its first tick. Only its recorded spots that still hold its block are touched: a spot holding stone since stays
     * stone, and a wart block it never placed stays.
     */
    private static void reloadCleanup(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        Gloomweaver[] copy = {null};
        withWall(helper, g, pig, () -> {
            ServerLevel level = helper.getLevel();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            g.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            g.discard();                                                 // its blocks go with it...
            for (int z = WALL_Z0; z <= WALL_Z1; z++) {                    // ...so put them back, as a save left them
                for (int y = WALL_Y0; y <= WALL_Y1; y++) helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.WARPED_WART_BLOCK);
            }
            helper.setBlock(new BlockPos(WALL_X, 3, 3), Blocks.STONE);     // a recorded spot something else holds now
            helper.setBlock(new BlockPos(1, 1, 10), Blocks.WARPED_WART_BLOCK);   // wart it never placed
            Gloomweaver c = ModMobs.GLOOMWEAVER.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            helper.assertTrue(c.walls().waiting() == 15, "the copy holds the 15 recorded spots, " + c.walls().waiting());
            c.setNoAi(true);
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, c::discard);
            copy[0] = c;
        });
        helper.runAtTickTime(8, () -> {
            List<BlockPos> left = wart(helper);
            helper.assertTrue(left.size() == 1 && left.getFirst().equals(new BlockPos(1, 1, 10)), "only the wart it never placed is left, " + left);
            helper.assertTrue(helper.getBlockState(new BlockPos(WALL_X, 3, 3)).is(Blocks.STONE), "the stone stays");
            helper.assertTrue(copy[0].walls().waiting() == 0 && copy[0].walls().size() == 0, "nothing waits any more");
            helper.setBlock(new BlockPos(1, 1, 10), Blocks.AIR);
            helper.succeed();
        });
    }

    /**
     * A piston pushes the wall's bottom row one block along it (south): the block pushed out past the wall's end stays
     * part of it, and the weaver's death takes every block down, wherever it went.
     */
    private static void piston(GameTestHelper helper) {
        Gloomweaver g = standardWeaver(helper);
        Pig pig = standardPig(helper);
        withWall(helper, g, pig, () -> {
            helper.setBlock(new BlockPos(WALL_X, 1, WALL_Z0 - 1), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.SOUTH));
            helper.setBlock(new BlockPos(WALL_X, 1, WALL_Z0 - 2), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(WALL_X, 1, WALL_Z1 + 1)).is(Blocks.WARPED_WART_BLOCK),
                    "the piston pushed the bottom row one block south");
            g.kill(helper.getLevel());
        });
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(wart(helper).isEmpty(), "every block went, pushed or not, left " + wart(helper));
            helper.setBlock(new BlockPos(WALL_X, 1, WALL_Z0 - 2), Blocks.AIR);
            helper.setBlock(new BlockPos(WALL_X, 1, WALL_Z0 - 1), Blocks.AIR);
            helper.succeed();
        });
    }

    // ---- the bite and the row ----------------------------------------------------------------------------------------

    /** Up close it bites for its attack: a still pig loses 5. */
    private static void bite(GameTestHelper helper) {
        Gloomweaver g = weaver(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.9, 1, 5.5));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(g.doHurtTarget(helper.getLevel(), pig), "the bite lands");
            helper.assertTrue(Math.abs(100.0F - pig.getHealth() - 5.0F) < 0.01F, "a bite does 5, lost " + (100.0F - pig.getHealth()));
            helper.succeed();
        });
    }

    /** Health 26, attack 5, armour 2, a 1.3 x 0.9 hitbox; a monster; looks base and Crimson Weaver, walls of their wart. */
    private static void stats(GameTestHelper helper) {
        Gloomweaver g = weaver(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(g.getMaxHealth() - 26.0F) < 0.01F && Math.abs(g.getHealth() - 26.0F) < 0.01F,
                    "health 26, got " + g.getHealth() + "/" + g.getMaxHealth());
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ATTACK_DAMAGE) - 5.0) < 0.01, "attack 5");
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(g.getBbWidth() - 1.3F) < 0.02F && Math.abs(g.getBbHeight() - 0.9F) < 0.02F,
                    "hitbox 1.3 x 0.9, got " + g.getBbWidth() + " x " + g.getBbHeight());
            helper.assertTrue(g.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(!g.vulnerable(), "it has no weak point");
            helper.assertTrue(g.wallState().is(Blocks.WARPED_WART_BLOCK), "its walls are warped wart");
            g.core().setLook("crimson_weaver", false);
            helper.assertTrue(g.wallState().is(Blocks.NETHER_WART_BLOCK), "the Crimson Weaver's are nether wart");
            List<String> looks = Looks.of("gloomweaver").stream().map(LookDef::id).sorted().toList();
            helper.assertTrue(looks.equals(List.of("base", "crimson_weaver")), "looks base and crimson_weaver, got " + looks);
            helper.succeed();
        });
    }

    /** Home: the warped forest and not the crimson forest; spawn weight 8, alone. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("gloomweaver"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.WARPED_FOREST)), "its home holds the warped forest");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.CRIMSON_FOREST)), "its home holds the crimson forest");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.NETHER_WASTES)), "its home never holds the nether wastes");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/gloomweaver")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.GLOOMWEAVER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 8, "spawn weight 8, got " + entry.weight());
        var forests = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).biomes();
        var biomeLookup = access.lookupOrThrow(Registries.BIOME);
        helper.assertTrue(!forests.contains(biomeLookup.getOrThrow(Biomes.WARPED_FOREST)) && !forests.contains(biomeLookup.getOrThrow(Biomes.CRIMSON_FOREST)),
                "its biome-list spawns leave out the share-capped forests, where the Teeming spawner places it");
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
