package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.Dunefin;
import com.panda.teeming.mob.overworld.Dunefin.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Dunefin: it swims through sand at 0.32 blocks a tick and hunts survival players standing on sand within 16 blocks.
 * Under its prey the fin rises for 15 ticks (nothing happens yet), then it breaches: 1.4x Atk to whatever is over it.
 * Standing on anything but sand is safe; luring it so it comes down on hard ground beaches it, stunned for 60 ticks.
 *
 * <p>Every test lays one block of sand on the arena's stone floor (y 1, so its top is y 2). Hunting reacts to survival
 * players only, and the GameTest mock player always counts as creative, so the player tests place a real survival
 * player that leaves when the test ends. Expected values are written out as plain numbers.
 */
public final class DunefinTests {
    private DunefinTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("dunefin_tell", DunefinTests::tell);
        out.accept("dunefin_counter", DunefinTests::lureOntoStone);
        TeemingGameTests.timeLimit("dunefin_counter", 160);
        out.accept("dunefin_failure", DunefinTests::stayOnSand);
        out.accept("dunefin_stone_is_safe", DunefinTests::stoneIsSafe);
        out.accept("dunefin_dodge_on_sand", DunefinTests::dodgeOnSand);
        out.accept("dunefin_bite_numbers", DunefinTests::biteNumbers);
        out.accept("dunefin_swim_speed", DunefinTests::swimSpeed);
        out.accept("dunefin_hunt_range", DunefinTests::huntRange);
        out.accept("dunefin_keeps_prey_in_range", DunefinTests::keepsPreyInRange);
        out.accept("dunefin_ignores_others", DunefinTests::ignoresOthers);
        out.accept("dunefin_stays_in_sand", DunefinTests::staysInSand);
        out.accept("dunefin_dives_in", DunefinTests::divesIn);
        out.accept("dunefin_flops_back", DunefinTests::flopsBack);
        TeemingGameTests.timeLimit("dunefin_flops_back", 200);
        out.accept("dunefin_stats", DunefinTests::stats);
        out.accept("dunefin_beached_damage", DunefinTests::beachedDamage);
        out.accept("dunefin_spawns", DunefinTests::spawns);
        out.accept("dunefin_bite_cancel", DunefinTests::biteCancel);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Fills x0..x1, z0..z1 at y 1 (on the stone floor) with {@code block}. */
    private static void floor(GameTestHelper helper, Block block, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) helper.setBlock(new BlockPos(x, 1, z), block);
    }

    /** A Dunefin already under the sand at (x, z), its cruising goal removed (its hunt still runs). */
    private static Dunefin fin(GameTestHelper helper, double x, double z) {
        Dunefin fin = helper.spawn(ModMobs.DUNEFIN.get(), new Vec3(x, 2, z));
        fin.removeFreeWill();
        helper.assertTrue(fin.submerge(), "it sinks into the sand it was placed on");
        return fin;
    }

    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        return pig;
    }

    /** Records the Dunefin's own tick count at the first time it posts each mechanic step; removed when the test ends. */
    private static Map<String, Integer> steps(GameTestHelper helper, Dunefin fin) {
        Map<String, Integer> at = new HashMap<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == fin) at.putIfAbsent(e.step(), fin.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    private static double sandTop(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(0, 2, 0)).y;
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A survival player standing over it sets off the tell at once. The fin rises for exactly 15 ticks from the tell
     * to the breach; meanwhile it stays under the sand, holds still and nothing happens to the player.
     */
    private static void tell(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 10, 1, 10);
        Dunefin fin = fin(helper, 5.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.8, 2, 5.5));
        float hp = p.getHealth();
        Map<String, Integer> at = steps(helper, fin);
        long[] first = {-1};
        boolean[] harmed = {false}, surfaced = {false};
        double[] moved = {0.0};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            if (!fin.core().in(Step.TELL)) return;
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = fin.position();
            }
            harmed[0] |= p.getHealth() < hp;
            surfaced[0] |= !fin.submerged();
            moved[0] = Math.max(moved[0], fin.position().subtract(start[0]).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.containsKey("tell") && at.containsKey("breach"), "the tell runs into the breach: " + at);
            helper.assertTrue(first[0] <= 3, "a player right over it sets it off at once, it started at tick " + first[0]);
            int tell = at.get("breach") - at.get("tell");
            helper.assertTrue(tell == 15, "the fin rises for 15 ticks, measured " + tell);
            helper.assertTrue(!harmed[0], "nothing happens to the player while the fin rises");
            helper.assertTrue(!surfaced[0], "it stays under the sand through the tell");
            helper.assertTrue(moved[0] < 0.05, "it holds still under its prey, moved " + moved[0]);
        });
    }

    /**
     * The counter: as the fin rises under the player (on sand next to stone), they step off onto the stone 3 blocks
     * away. The breach misses, it comes down on the stone and lies beached for exactly 60 ticks, open to attack, and
     * the player takes nothing.
     */
    private static void lureOntoStone(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 5, 1, 10);
        floor(helper, Blocks.STONE, 6, 10, 1, 10);
        Dunefin fin = fin(helper, 3.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(3.8, 2, 5.5));
        float hp = p.getHealth();
        boolean[] stepped = {false}, harmed = {false}, weak = {false}, open = {true};
        int[] stunned = {0};
        double[] landedX = {Double.NaN};
        double stoneX = helper.absoluteVec(new Vec3(6, 0, 0)).x;
        helper.onEachTick(() -> {
            if (!stepped[0] && fin.core().in(Step.TELL)) {
                p.snapTo(helper.absoluteVec(new Vec3(6.5, 2, 5.5)));   // off the sand, onto the stone
                stepped[0] = true;
            }
            harmed[0] |= p.getHealth() < hp;
            if (fin.core().in(Step.BEACHED)) {
                if (stunned[0] == 0) landedX[0] = fin.getX();
                stunned[0]++;
                weak[0] = fin.vulnerable();
                open[0] &= fin.vulnerable();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepped[0], "the fin rose under the player");
            helper.assertTrue(stunned[0] > 0 && !fin.core().in(Step.BEACHED), "it beached and the stun ran out, " + stunned[0] + " ticks so far");
            helper.assertTrue(landedX[0] > stoneX, "it came down on the stone, x " + landedX[0] + " (stone from " + stoneX + ")");
            helper.assertTrue(stunned[0] == 60, "beached for 60 ticks, lasted " + stunned[0]);
            helper.assertTrue(weak[0] && open[0], "its weak point shows the whole time it lies beached");
            helper.assertTrue(!harmed[0], "the player on the stone took nothing");
        });
    }

    /** Failing the counter: staying on the sand over it costs the breach's bite, 1.4x Atk 5 = 7, scaled for difficulty. */
    private static void stayOnSand(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 10, 1, 10);
        Dunefin fin = fin(helper, 5.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.8, 2, 5.5));
        float before = p.getHealth();
        // mob damage to players scales with difficulty: 7 is 4.5 on Easy, 7 on Normal, 10.5 on Hard
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 4.5F;
            case HARD -> 10.5F;
            default -> 7.0F;
        };
        boolean[] rose = {false};
        helper.onEachTick(() -> rose[0] |= fin.core().in(Step.TELL));
        helper.succeedWhen(() -> {
            float lost = before - p.getHealth();
            helper.assertTrue(lost > 0.0F, "the breach bites, phase " + fin.phase());
            helper.assertTrue(rose[0], "the fin rose first");
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the bite does " + expected + " here, the player lost " + lost);
        });
    }

    /** Standing on stone right beside the sand, 3 blocks from it: it never hunts that player and never rises. */
    private static void stoneIsSafe(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 5, 1, 10);
        floor(helper, Blocks.STONE, 6, 10, 1, 10);
        Dunefin fin = fin(helper, 3.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 2, 5.5));
        float hp = p.getHealth();
        boolean[] rose = {false}, hunted = {false}, left = {false};
        double edge = helper.absoluteVec(new Vec3(6, 0, 0)).x;
        helper.onEachTick(() -> {
            rose[0] |= !fin.core().idle();
            hunted[0] |= fin.getTarget() == p;
            left[0] |= fin.getX() >= edge || !fin.submerged();
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!hunted[0], "a player on stone is never its prey");
            helper.assertTrue(!rose[0], "it never rose, phase " + fin.phase());
            helper.assertTrue(!left[0], "it never left the sand");
            helper.assertTrue(p.getHealth() >= hp, "the player took nothing");
            helper.succeed();
        });
    }

    /**
     * Dodging on the sand: the player steps 2.5 blocks away as the fin rises. The bite (on the way up) misses, it comes
     * down on sand where the player stands and dives straight back in: never beached.
     */
    private static void dodgeOnSand(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 10, 1, 10);
        Dunefin fin = fin(helper, 3.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(3.8, 2, 5.5));
        float hp = p.getHealth();
        boolean[] stepped = {false}, harmed = {false}, beached = {false}, dove = {false}, leapt = {false};
        helper.onEachTick(() -> {
            if (!stepped[0] && fin.core().in(Step.TELL)) {
                p.snapTo(helper.absoluteVec(new Vec3(6.0, 2, 5.5)));
                stepped[0] = true;
            }
            harmed[0] |= p.getHealth() < hp;
            leapt[0] |= fin.leaping();
            beached[0] |= fin.core().in(Step.BEACHED);
            dove[0] |= leapt[0] && fin.core().in(Step.DIVE);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepped[0] && leapt[0], "it rose and breached");
            helper.assertTrue(dove[0] && fin.core().idle() && fin.submerged(), "it came down on sand and dived back in, phase " + fin.phase());
            helper.assertTrue(!beached[0], "coming down on sand never beaches it");
            helper.assertTrue(!harmed[0], "2.5 blocks away the bite missed");
            helper.assertTrue(Math.abs(fin.getX() - helper.absoluteVec(new Vec3(6.0, 0, 0)).x) < 0.3,
                    "it came down where the player stood, x " + fin.getX());
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** Given a pig standing over it (no difficulty scaling), the breach's bite does exactly 7: 1.4x Atk 5. */
    private static void biteNumbers(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 10, 1, 10);
        Dunefin fin = fin(helper, 5.5, 5.5);
        Pig pig = pig(helper, 5.8, 2, 5.5);
        float before = pig.getHealth();
        float[] lost = {-1.0F};
        boolean[] climbing = {false};
        helper.runAtTickTime(1, () -> fin.setTarget(pig));
        helper.onEachTick(() -> {
            if (lost[0] < 0 && pig.getHealth() < before) {
                lost[0] = before - pig.getHealth();
                climbing[0] = fin.leaping() && fin.getY() < sandTop(helper) + 1.5;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0.0F, "the breach bites the pig, phase " + fin.phase());
            helper.assertTrue(Math.abs(lost[0] - 7.0F) < 0.01F, "the bite does 7 (1.4x Atk 5), the pig lost " + lost[0]);
            helper.assertTrue(climbing[0], "it bit on the way up out of the sand");
        });
    }

    /** Hunting through sand it covers exactly 0.32 blocks a tick, its feet 0.55 under the sand's top all the way. */
    private static void swimSpeed(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 0, 11, 4, 6);
        Dunefin fin = fin(helper, 1.5, 5.5);
        Pig pig = pig(helper, 10.5, 2, 5.5);
        double top = sandTop(helper);
        List<Double> stepsX = new ArrayList<>();
        double[] last = {Double.NaN}, depth = {0.0};
        helper.runAtTickTime(1, () -> fin.setTarget(pig));
        helper.onEachTick(() -> {
            boolean hunting = fin.core().idle() && fin.submerged() && fin.getTarget() == pig && pig.getX() - fin.getX() > 1.5;
            if (hunting && !Double.isNaN(last[0])) {
                stepsX.add(fin.getX() - last[0]);
                depth[0] = Math.max(depth[0], Math.abs(top - fin.getY() - 0.55));
            }
            last[0] = hunting ? fin.getX() : Double.NaN;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepsX.size() >= 15, "it swam toward the pig for 15 ticks or more, saw " + stepsX.size());
            double mean = stepsX.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double spread = stepsX.stream().mapToDouble(s -> Math.abs(s - 0.32)).max().orElse(1);
            helper.assertTrue(Math.abs(mean - 0.32) < 0.005, "it swims 0.32 blocks a tick, mean " + mean);
            helper.assertTrue(spread < 0.01, "at a steady pace, worst step off by " + spread);
            helper.assertTrue(depth[0] < 0.01, "its feet stay 0.55 under the sand's top, off by up to " + depth[0]);
        });
    }

    /**
     * The hunt reaches 16 blocks. Its sand is one block in a corner (so it can't move); the player stands on a sand
     * block on a pillar in the far corner: 16.5 blocks away it is ignored for 20 ticks; lowered to 15.96 blocks it is
     * hunted within 2 ticks (unless a nearer player on sand from another test is, which the rule also allows).
     */
    private static void huntRange(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.SAND);
        for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(11, y, 11), Blocks.STONE);
        helper.setBlock(new BlockPos(11, 6, 11), Blocks.SAND);
        Dunefin fin = fin(helper, 0.5, 0.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(11.5, 7, 11.5));
        long[] lowered = {-1}, hunted = {-1};
        boolean[] early = {false};
        double[] far = {0}, near = {0};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (lowered[0] < 0) early[0] |= fin.getTarget() == p;
            if (lowered[0] < 0 && now >= 20) {
                far[0] = fin.distanceTo(p);
                helper.setBlock(new BlockPos(11, 6, 11), Blocks.AIR);
                helper.setBlock(new BlockPos(11, 5, 11), Blocks.AIR);
                helper.setBlock(new BlockPos(11, 4, 11), Blocks.SAND);
                p.snapTo(helper.absoluteVec(new Vec3(11.5, 5, 11.5)));
                near[0] = fin.distanceTo(p);
                lowered[0] = now;
            }
            LivingEntity t = fin.getTarget();
            if (lowered[0] >= 0 && hunted[0] < 0 && t != null && (t == p || fin.distanceTo(t) < fin.distanceTo(p))) hunted[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lowered[0] >= 0, "the player was lowered");
            helper.assertTrue(far[0] > 16.4 && far[0] < 16.6 && near[0] > 15.9 && near[0] < 16.0, "set-up: " + far[0] + " then " + near[0] + " blocks");
            helper.assertTrue(!early[0], "16.5 blocks away the player was never hunted");
            helper.assertTrue(hunted[0] >= 0, "15.96 blocks away it hunts, target " + fin.getTarget());
            helper.assertTrue(hunted[0] - lowered[0] <= 2, "within 2 ticks, " + (hunted[0] - lowered[0]));
        });
    }

    /** A pig given to it as prey 15.96 blocks away is kept for 10 ticks; raised to 16.5 blocks it is dropped within 2. */
    private static void keepsPreyInRange(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.SAND);
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(11, y, 11), Blocks.STONE);
        helper.setBlock(new BlockPos(11, 4, 11), Blocks.SAND);
        Dunefin fin = fin(helper, 0.5, 0.5);
        Pig pig = pig(helper, 11.5, 5, 11.5);
        long[] raised = {-1}, dropped = {-1};
        helper.runAtTickTime(1, () -> fin.setTarget(pig));
        helper.runAtTickTime(11, () -> {
            helper.assertTrue(fin.getTarget() == pig, "15.96 blocks away the pig stays its prey, " + fin.distanceTo(pig));
            helper.setBlock(new BlockPos(11, 4, 11), Blocks.STONE);
            helper.setBlock(new BlockPos(11, 5, 11), Blocks.STONE);
            helper.setBlock(new BlockPos(11, 6, 11), Blocks.SAND);
            Vec3 up = helper.absoluteVec(new Vec3(11.5, 7, 11.5));
            pig.teleportTo(up.x, up.y, up.z);
            raised[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (raised[0] >= 0 && dropped[0] < 0 && fin.getTarget() != pig) dropped[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dropped[0] >= 0, "16.5 blocks away it lets the pig go, " + fin.distanceTo(pig));
            helper.assertTrue(dropped[0] - raised[0] <= 2, "within 2 ticks, " + (dropped[0] - raised[0]));
        });
    }

    /** A creative player and a pig standing on the sand right over it are not prey it picks: no hunt, no rise in 60 ticks. */
    private static void ignoresOthers(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 10, 1, 10);
        Dunefin fin = fin(helper, 5.5, 5.5);
        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        creative.snapTo(helper.absoluteVec(new Vec3(6.0, 2, 5.5)));
        Pig pig = pig(helper, 5.5, 2, 6.0);
        float hp = creative.getHealth();
        boolean[] rose = {false}, picked = {false};
        helper.onEachTick(() -> {
            rose[0] |= !fin.core().idle();
            picked[0] |= fin.getTarget() == creative || fin.getTarget() == pig;
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(creative.isCreative(), "the mock player counts as creative");
            helper.assertTrue(!picked[0], "neither the creative player nor the pig became its prey");
            helper.assertTrue(!rose[0], "it never rose, phase " + fin.phase());
            helper.assertTrue(creative.getHealth() >= hp && pig.getHealth() >= pig.getMaxHealth(), "nobody was bitten");
            helper.succeed();
        });
    }

    /** Prey on sand beyond a strip of stone: it swims up to the edge of its sand and stops there, never crossing. */
    private static void staysInSand(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 4, 1, 10);
        floor(helper, Blocks.STONE, 5, 7, 1, 10);
        floor(helper, Blocks.SAND, 8, 10, 1, 10);
        Dunefin fin = fin(helper, 2.5, 5.5);
        Pig pig = pig(helper, 9.5, 2, 5.5);
        double edge = helper.absoluteVec(new Vec3(5, 0, 0)).x;
        double[] furthest = {Double.NEGATIVE_INFINITY};
        boolean[] out = {false}, rose = {false};
        helper.runAtTickTime(1, () -> fin.setTarget(pig));
        helper.onEachTick(() -> {
            furthest[0] = Math.max(furthest[0], fin.getX());
            out[0] |= !fin.submerged();
            rose[0] |= !fin.core().idle();
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(fin.getTarget() == pig, "the pig on sand stays its prey");
            helper.assertTrue(furthest[0] > edge - 1.0, "it swam up to the edge of its sand, x " + furthest[0] + " (stone from " + edge + ")");
            helper.assertTrue(furthest[0] < edge - 0.5, "its snout never crossed onto the stone, x " + furthest[0]);
            helper.assertTrue(!out[0] && !rose[0], "it stayed under the sand and never rose");
            helper.succeed();
        });
    }

    /** Placed on sand (as a natural spawn is), it dives in: feet 0.55 under the sand's top, and no harm from the sand round it. */
    private static void divesIn(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 10, 1, 10);
        Dunefin fin = helper.spawn(ModMobs.DUNEFIN.get(), new Vec3(5.5, 2, 5.5));
        fin.removeFreeWill();
        double top = sandTop(helper);
        boolean[] dove = {false};
        helper.onEachTick(() -> dove[0] |= fin.core().in(Step.DIVE));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(dove[0], "it dived into the sand");
            helper.assertTrue(fin.submerged() && fin.core().idle(), "it swims under the sand, phase " + fin.phase());
            helper.assertTrue(Math.abs(top - fin.getY() - 0.55) < 0.01, "feet 0.55 under the top, " + (top - fin.getY()));
            helper.assertTrue(fin.getBoundingBox().maxY > top, "the top of it (the fin) stays above the sand, where it can be hit");
            helper.assertTrue(fin.getHealth() >= fin.getMaxHealth(), "the sand round it does it no harm, health " + fin.getHealth());
            helper.succeed();
        });
    }

    /** Stranded on stone 3 blocks from sand, it flops back to the sand and dives in. */
    private static void flopsBack(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 3, 1, 10);
        floor(helper, Blocks.STONE, 4, 10, 1, 10);
        Dunefin fin = helper.spawn(ModMobs.DUNEFIN.get(), new Vec3(6.5, 2, 5.5));
        fin.removeFreeWill();
        boolean[] stranded = {false};
        helper.onEachTick(() -> stranded[0] |= fin.core().flag(Dunefin.STRANDED));
        helper.succeedWhen(() -> {
            helper.assertTrue(stranded[0], "on the stone it was stranded");
            helper.assertTrue(fin.submerged(), "it got back into the sand, x " + fin.getX());
        });
    }

    /** Beached, it takes x1.5: a blow of 4 costs 6. Out of the sand but not beached, the same blow costs 4. */
    private static void beachedDamage(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 1, 10, 1, 10);
        Dunefin beached = helper.spawn(ModMobs.DUNEFIN.get(), new Vec3(3.5, 2, 5.5));
        Dunefin stranded = helper.spawn(ModMobs.DUNEFIN.get(), new Vec3(7.5, 2, 5.5));
        beached.removeFreeWill();
        stranded.removeFreeWill();
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(beached.beach(helper.getLevel()), "it can be beached");
            float before = beached.getHealth();
            beached.hurtServer(helper.getLevel(), beached.damageSources().magic(), 4.0F);
            float lost = before - beached.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "beached it takes x1.5: 4 becomes 6, lost " + lost);
            float before2 = stranded.getHealth();
            stranded.hurtServer(helper.getLevel(), stranded.damageSources().magic(), 4.0F);
            float lost2 = before2 - stranded.getHealth();
            helper.assertTrue(Math.abs(lost2 - 4.0F) < 0.01F, "not beached it takes 4, lost " + lost2);
            helper.succeed();
        });
    }

    /** Health 22, attack 5, armour 2, a 1.2 x 0.8 hitbox. */
    private static void stats(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 10, 1, 10);
        Dunefin fin = fin(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(fin.getMaxHealth() - 22.0F) < 0.01F && Math.abs(fin.getHealth() - 22.0F) < 0.01F,
                    "health 22, got " + fin.getHealth() + "/" + fin.getMaxHealth());
            helper.assertTrue(Math.abs(fin.getAttributeValue(Attributes.ATTACK_DAMAGE) - 5.0) < 0.01, "attack 5");
            helper.assertTrue(Math.abs(fin.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(fin.getBbWidth() - 1.2F) < 0.02F && Math.abs(fin.getBbHeight() - 0.8F) < 0.02F,
                    "hitbox 1.2 x 0.8, got " + fin.getBbWidth() + " x " + fin.getBbHeight());
            helper.succeed();
        });
    }

    /**
     * Its home: deserts and badlands (where its red look lives), each at its tuned weight, roster weight 20 elsewhere
     * in its tag, in groups of 1 to 2; the sand placement takes a
     * spot on sand and refuses one on stone; and its spawn rules accept a spot beside a glowstone (any light).
     */
    private static void spawns(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/dunefin"));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key).map(Holder::value).orElse(null);
        if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) {
            helper.fail("no spawn biome modifier for the Dunefin: " + modifier);
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> spawners = add.spawners().unwrap();
        helper.assertTrue(spawners.size() == 1 && spawners.getFirst().value().type() == ModMobs.DUNEFIN.get(), "one Dunefin spawner, " + spawners);
        Weighted<MobSpawnSettings.SpawnerData> spawner = spawners.getFirst();
        helper.assertTrue(spawner.weight() == 20, "spawn weight 20, got " + spawner.weight());
        helper.assertTrue(spawner.value().count().minInclusive() == 1 && spawner.value().count().maxInclusive() == 2,
                "groups of 1 to 2, got " + spawner.value().count().minInclusive() + " to " + spawner.value().count().maxInclusive());
        // each home biome has a tuned weight (the Overworld share): its own modifier at that weight, and the roster
        // weight (for biome mods' biomes) leaves it out
        for (var biome : List.of(Biomes.DESERT, Biomes.BADLANDS)) {
            String path = biome.identifier().getPath();
            int want = com.panda.teeming.spawn.SpawnTable.tuned("dunefin").stream().filter(r -> r.biome().equals(biome.identifier().toString()))
                    .mapToInt(com.panda.teeming.spawn.SpawnTable.Row::weight).findFirst().orElse(0);
            var tuned = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                    .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/dunefin/" + path))).map(h -> h.value()).orElse(null);
            helper.assertTrue(!add.biomes().contains(access.lookupOrThrow(Registries.BIOME).getOrThrow(biome)), path + " has a tuned weight, so the roster weight leaves it out");
            helper.assertTrue(tuned instanceof BiomeModifiers.AddSpawnsBiomeModifier t && want > 0
                    && t.biomes().contains(access.lookupOrThrow(Registries.BIOME).getOrThrow(biome)) && t.spawners().unwrap().getFirst().weight() == want,
                    "it spawns in " + path + " at its tuned weight " + want + ", got " + tuned);
        }

        floor(helper, Blocks.SAND, 1, 4, 1, 4);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.GLOWSTONE);
        RosterStats.Entry row = RosterStats.get("dunefin");
        Dunefin fin = helper.spawn(ModMobs.DUNEFIN.get(), new Vec3(2.5, 2, 2.5));
        fin.setNoAi(true);
        helper.assertTrue(TeemingSpawns.placeOk(row, fin.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2))), "a spot on sand takes a spawn");
        helper.assertTrue(!TeemingSpawns.placeOk(row, fin.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 1, 8))), "a spot on stone does not");
        helper.succeedWhen(() -> {                            // the light spreads a tick or two after the glowstone goes in
            helper.assertTrue(helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(new BlockPos(2, 2, 2))) >= 13, "the spot is lit by the glowstone");
            helper.assertTrue(fin.checkSpawnRules(helper.getLevel(), EntitySpawnReason.NATURAL), "its spawn rules accept the lit spot (any light)");
        });
    }

    /** A listener that cancels the "bite" step lets the breach happen but leaves the pig over it unhurt. */
    private static void biteCancel(GameTestHelper helper) {
        floor(helper, Blocks.SAND, 1, 10, 1, 10);
        Dunefin fin = fin(helper, 5.5, 5.5);
        Pig pig = pig(helper, 5.8, 2, 5.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == fin && e.step().equals("bite")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] leapt = {false};
        helper.runAtTickTime(1, () -> fin.setTarget(pig));
        helper.onEachTick(() -> leapt[0] |= fin.leaping());
        helper.succeedWhen(() -> {
            helper.assertTrue(leapt[0] && !fin.leaping(), "it breached and came down, phase " + fin.phase());
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "a cancelled bite hurts nothing");
        });
    }
}
