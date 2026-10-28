package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Underclinger;
import com.panda.teeming.mob.end.Underclinger.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner.Rule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Underclinger. The arena holds an island of end stone: a back wall solid to the floor (x 1..10, z 8..9, y 1..5) and an
 * overhang one block thick in front of it (y 5, z 5..7) with open air under it down to the floor, its rim facing north
 * (z 5); a fall onto the floor stands in for the void. A pig (or a survival player) at the rim is reached for 10 ticks
 * and moves not at all meanwhile, then hooked for the clinger's attack (8.5), held 20 ticks, and dragged exactly 2.0
 * blocks north, out from the island, over 4 ticks, after which it falls to the floor. A blow while it reaches or holds
 * makes it let go at once (no drag) and retreat exactly 40 ticks, reaching for no one, before it reaches again. It stays
 * pressed to the underside the whole time, never leaves it for open air, and ignores prey away from an edge, prey 1.5
 * from an edge on solid ground and prey above a rim 3 thick (a rim 2 thick is in reach). It crawls 0.1 blocks a tick.
 * Every number is checked against a plain value.
 */
public final class UnderclingerTests {
    private UnderclingerTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("underclinger_tell", UnderclingerTests::tell);
        out.accept("underclinger_counter", UnderclingerTests::counter);
        TeemingGameTests.timeLimit("underclinger_counter", 200);
        out.accept("underclinger_failure", UnderclingerTests::failure);
        out.accept("underclinger_pull_2", UnderclingerTests::pullTwo);
        out.accept("underclinger_pulls_players", UnderclingerTests::pullsPlayers);
        out.accept("underclinger_hold", UnderclingerTests::hold);
        out.accept("underclinger_tell_hit", UnderclingerTests::tellHit);
        out.accept("underclinger_clings", UnderclingerTests::clings);
        out.accept("underclinger_ignores_inland", UnderclingerTests::ignoresInland);
        out.accept("underclinger_edge_range", UnderclingerTests::edgeRange);
        out.accept("underclinger_thick_rim", UnderclingerTests::thickRim);
        out.accept("underclinger_reach_2", UnderclingerTests::reachTwo);
        out.accept("underclinger_bridge", UnderclingerTests::bridge);
        out.accept("underclinger_crawl", UnderclingerTests::crawl);
        TeemingGameTests.timeLimit("underclinger_crawl", 160);
        out.accept("underclinger_dies_mid_hold", UnderclingerTests::diesMidHold);
        out.accept("underclinger_tell_veto", UnderclingerTests::tellVeto);
        out.accept("underclinger_save_load", UnderclingerTests::saveLoad);
        out.accept("underclinger_spawn_rule", UnderclingerTests::spawnRule);
        out.accept("underclinger_stats", UnderclingerTests::stats);
        out.accept("underclinger_home", UnderclingerTests::home);
    }

    // ---- fixtures --------------------------------------------------------------------------------------------------

    /**
     * The island: a back wall of end stone solid to the floor (x 1..10, z 8..9, y 1..5) and an overhang {@code thick}
     * blocks thick whose top is at y 5 (z {@code fromZ}..7), with open air under it to the floor; its rim faces north.
     */
    private static void island(GameTestHelper helper, int fromZ, int thick) {
        for (int x = 1; x <= 10; x++) {
            for (int y = 1; y <= 5; y++) for (int z = 8; z <= 9; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.END_STONE);
            for (int z = fromZ; z <= 7; z++) for (int y = 6 - thick; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.END_STONE);
        }
    }

    private static void island(GameTestHelper helper) {
        island(helper, 5, 1);
    }

    /**
     * An Underclinger with the base look gripping the underside of the block at {@code (x, y + 1, z)}, a follow range of
     * 1 (it never notices survivors in other arenas), ready to reach, removed when the test ends.
     */
    private static Underclinger clinger(GameTestHelper helper, int x, int y, int z) {
        Underclinger u = helper.spawn(ModMobs.UNDERCLINGER.get(), new Vec3(x + 0.5, y + 0.4, z + 0.5));
        u.core().setLook("base", true);
        u.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        helper.assertTrue(u.hangUnder(helper.absolutePos(new BlockPos(x, y + 1, z))), "it grips the underside at " + x + " " + y + " " + z);
        u.resetCooldown();
        TestPlayers.whenDone(helper, u::discard);
        return u;
    }

    /** A pig with 100 health and no will of its own (it keeps its physics, so it falls), removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.removeFreeWill();
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** The standard set-up: the island, a clinger one cell in from the rim, a pig on the rim above it, aimed at. */
    private static Underclinger atRim(GameTestHelper helper, Pig[] target) {
        island(helper);
        Underclinger u = clinger(helper, 5, 4, 6);
        Pig pig = pig(helper, 5.5, 6, 5.5);
        u.setTarget(pig);
        target[0] = pig;
        return u;
    }

    private static Vec3 rel(GameTestHelper helper, LivingEntity e) {
        return helper.relativeVec(e.position());
    }

    // ---- the reach (tell) ------------------------------------------------------------------------------------------

    /** It reaches for exactly 10 ticks; the pig is neither moved nor hurt meanwhile, and then the hooks close. */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Underclinger u = atRim(helper, pig);
        long[] start = {-1}, end = {-1};
        Vec3[] at = {null};
        helper.onEachTick(() -> {
            if (u.core().in(Step.TELL)) {
                if (start[0] < 0) {
                    start[0] = helper.getTick();
                    at[0] = pig[0].position();
                }
                helper.assertTrue(pig[0].position().distanceTo(at[0]) < 0.01, "the reach moves nothing");
                helper.assertTrue(pig[0].getHealth() >= 100.0F, "the reach hurts nothing");
                helper.assertTrue(u.victim() == null, "the reach holds nothing yet");
            } else if (start[0] >= 0 && end[0] < 0) {
                end[0] = helper.getTick();
                helper.assertTrue(u.core().in(Step.HOLD), "the reach ends in the hold, phase " + u.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && end[0] >= 0, "a reach and then a hold");
            helper.assertTrue(end[0] - start[0] == 10, "the reach lasts 10 ticks, lasted " + (end[0] - start[0]));
            helper.assertTrue(u.out() == Direction.NORTH, "it reaches out over the north rim, out " + u.out());
        });
    }

    /** A blow while it reaches, before the hooks close: it lets go and retreats, and the pig is never hooked or hurt. */
    private static void tellHit(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Underclinger u = atRim(helper, pig);
        boolean[] hit = {false};
        helper.onEachTick(() -> {
            if (!hit[0] && u.core().in(Step.TELL) && u.core().elapsed() == 5) {
                hit[0] = true;
                u.hurtServer(helper.getLevel(), u.damageSources().mobAttack(pig[0]), 1.0F);
                helper.assertTrue(u.core().in(Step.RETREAT), "a blow in the reach makes it retreat, phase " + u.phase());
            }
            if (hit[0]) helper.assertTrue(!u.core().in(Step.HOLD) && !u.core().in(Step.PULL), "it never holds after the blow");
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(hit[0], "it reached and was struck");
            helper.assertTrue(pig[0].getHealth() >= 100.0F, "the pig was never hooked, health " + pig[0].getHealth());
            helper.assertTrue(Math.abs(rel(helper, pig[0]).z - 5.5) < 0.01 && Math.abs(rel(helper, pig[0]).y - 6.0) < 0.01,
                    "the pig stands where it stood, at " + rel(helper, pig[0]));
            helper.succeed();
        });
    }

    // ---- the hold and the drag -------------------------------------------------------------------------------------

    /**
     * Failing the counter: the hooks close for 8.5 (its attack; a pig has no armour), and the pig is dragged out over the
     * edge and falls to the floor 2 blocks out from the rim block it stood on.
     */
    private static void failure(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Underclinger u = atRim(helper, pig);
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (lost[0] < 0.0F && u.core().in(Step.HOLD)) lost[0] = 100.0F - pig[0].getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] >= 0.0F, "it held the pig");
            helper.assertTrue(Math.abs(lost[0] - 8.5F) < 0.01F, "the hooks hit for 8.5, the pig lost " + lost[0]);
            Vec3 at = rel(helper, pig[0]);
            helper.assertTrue(Math.abs(at.y - 1.0) < 0.01, "the pig fell to the floor, at " + at);
            helper.assertTrue(Math.abs(at.z - 3.5) < 0.01 && Math.abs(at.x - 5.5) < 0.01, "2 blocks out from where it stood, at " + at);
            helper.assertTrue(u.victim() == null && !Grab.isHeld(pig[0]), "and it is let go");
        });
    }

    /**
     * The drag is exactly 2.0 blocks and points away from the island: from where it was held to where it is let go the
     * pig moves 2.0 north (the rim's open side) and not at all across; the drag takes 4 ticks.
     */
    private static void pullTwo(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Underclinger u = atRim(helper, pig);
        Vec3[] held = {null}, letGo = {null};
        long[] start = {-1}, end = {-1};
        helper.onEachTick(() -> {
            if (held[0] == null && u.core().in(Step.HOLD)) held[0] = rel(helper, pig[0]);
            if (u.core().in(Step.PULL) && start[0] < 0) start[0] = helper.getTick();
            if (start[0] >= 0 && end[0] < 0 && !u.core().in(Step.PULL)) {
                end[0] = helper.getTick();
                letGo[0] = rel(helper, pig[0]);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(held[0] != null && letGo[0] != null, "a hold and then a drag");
            double north = held[0].z - letGo[0].z, across = Math.abs(letGo[0].x - held[0].x);
            helper.assertTrue(Math.abs(north - 2.0) < 0.01 && across < 0.01, "dragged 2.0 north and 0 across, saw " + north + " north and " + across + " across");
            helper.assertTrue(letGo[0].z < 5.0 - 0.45, "the pig is past the rim (z 5) and over open air, at " + letGo[0]);
            helper.assertTrue(end[0] - start[0] == 4, "the drag takes 4 ticks, took " + (end[0] - start[0]));
        });
    }

    /** A survival player at the rim is dragged the same 2.0 blocks out (moved and sent its own position), and hurt. */
    private static void pullsPlayers(GameTestHelper helper) {
        island(helper);
        Underclinger u = clinger(helper, 5, 4, 6);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 6, 5.5));
        u.setTarget(player);
        float full = player.getHealth();
        Vec3[] held = {null}, letGo = {null};
        boolean[] pulling = {false};
        helper.onEachTick(() -> {
            if (held[0] == null && u.core().in(Step.HOLD)) held[0] = rel(helper, player);
            pulling[0] |= u.core().in(Step.PULL);
            if (pulling[0] && letGo[0] == null && !u.core().in(Step.PULL)) letGo[0] = rel(helper, player);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(held[0] != null && letGo[0] != null, "the player was held and dragged");
            double north = held[0].z - letGo[0].z;
            helper.assertTrue(Math.abs(north - 2.0) < 0.01 && Math.abs(letGo[0].x - held[0].x) < 0.01, "dragged 2.0 north, saw " + north);
            helper.assertTrue(player.getHealth() < full, "the hooks hurt the player");
        });
    }

    /** The hold lasts exactly 20 ticks and keeps the pig where it stood the whole time, then the drag starts. */
    private static void hold(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Underclinger u = atRim(helper, pig);
        long[] start = {-1}, end = {-1};
        Vec3[] at = {null};
        helper.onEachTick(() -> {
            if (u.core().in(Step.HOLD)) {
                if (start[0] < 0) {
                    start[0] = helper.getTick();
                    at[0] = pig[0].position();
                }
                helper.assertTrue(pig[0].position().distanceTo(at[0]) < 0.01, "held where it stood");
                helper.assertTrue(u.victim() == pig[0] && Grab.isHeld(pig[0]), "and held");
            } else if (start[0] >= 0 && end[0] < 0) {
                end[0] = helper.getTick();
                helper.assertTrue(u.core().in(Step.PULL), "the hold ends in the drag, phase " + u.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(end[0] >= 0, "a hold, then the drag");
            helper.assertTrue(end[0] - start[0] == 20, "the hold lasts 20 ticks, lasted " + (end[0] - start[0]));
        });
    }

    // ---- the counter -----------------------------------------------------------------------------------------------

    /**
     * The counter: a blow during the hold makes it let go at once (the pig stays on the rim where it was held, never
     * dragged) and it retreats exactly 40 ticks, reaching for no one though the pig still stands at the rim; then it
     * comes back and reaches again.
     */
    private static void counter(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Underclinger u = atRim(helper, pig);
        // the blow makes the pig its revenge target too, and a target goal drops a target past its follow range: 3
        // keeps the pig (about 2 away) and still reaches no other arena
        u.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(3.0);
        long[] start = {-1}, end = {-1}, again = {-1};
        Vec3[] held = {null};
        helper.onEachTick(() -> {
            if (start[0] < 0 && u.core().in(Step.HOLD) && u.core().elapsed() == 5) {
                held[0] = rel(helper, pig[0]);
                u.hurtServer(helper.getLevel(), u.damageSources().mobAttack(pig[0]), 2.0F);
                helper.assertTrue(u.core().in(Step.RETREAT), "the blow makes it retreat, phase " + u.phase());
                helper.assertTrue(u.victim() == null && !Grab.isHeld(pig[0]), "and let go at once");
                start[0] = helper.getTick();
            } else if (start[0] >= 0 && end[0] < 0) {
                if (u.core().in(Step.RETREAT)) {
                    helper.assertTrue(rel(helper, pig[0]).distanceTo(held[0]) < 0.01, "the pig stays where it was held");
                } else {
                    end[0] = helper.getTick();
                }
            } else if (end[0] >= 0 && again[0] < 0 && u.core().in(Step.TELL)) {
                again[0] = helper.getTick();
            }
            if (start[0] >= 0 && end[0] < 0) helper.assertTrue(!u.reaching(), "it reaches for no one while it retreats");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(end[0] >= 0, "the retreat ended");
            helper.assertTrue(end[0] - start[0] == 40, "it retreated 40 ticks, " + (end[0] - start[0]));
            helper.assertTrue(again[0] > end[0], "then it reached again");
            helper.assertTrue(pig[0].getHealth() >= 91.5F - 0.01F, "only the one hook landed, health " + pig[0].getHealth());
        });
    }

    /** Killed while it holds, it drags no one: the pig stays where it was held and is let go. */
    private static void diesMidHold(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Underclinger u = atRim(helper, pig);
        Vec3[] held = {null};
        long[] died = {-1};
        helper.onEachTick(() -> {
            if (died[0] < 0 && u.core().in(Step.HOLD) && u.core().elapsed() == 10) {
                held[0] = rel(helper, pig[0]);
                u.hurtServer(helper.getLevel(), u.damageSources().fellOutOfWorld(), 1000.0F);
                died[0] = helper.getTick();
            }
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(died[0] >= 0 && u.isDeadOrDying(), "it died in the hold");
            helper.assertTrue(rel(helper, pig[0]).distanceTo(held[0]) < 0.01, "the pig was never dragged, at " + rel(helper, pig[0]));
            helper.assertTrue(!Grab.isHeld(pig[0]), "and is let go");
            helper.succeed();
        });
    }

    // ---- the underside -----------------------------------------------------------------------------------------------

    /**
     * It stays on the ceiling: with its target on a lone block across open air (a rim it could reach only through
     * the air), it never falls and never leaves the underside: every tick its back is pressed to the stone (its top at
     * y 5.0) and the cell it grips has end stone over it.
     */
    private static void clings(GameTestHelper helper) {
        island(helper);
        helper.setBlock(new BlockPos(5, 5, 2), Blocks.END_STONE);         // a lone block, three blocks off the rim
        Underclinger u = clinger(helper, 5, 4, 6);
        Pig pig = pig(helper, 5.5, 6, 2.5);
        u.setTarget(pig);
        helper.onEachTick(() -> {
            if (helper.getTick() < 2) return;
            double top = rel(helper, u).y + u.getBbHeight();
            helper.assertTrue(Math.abs(top - 5.0) < 0.01, "its back stays on the underside, top at " + top);
            helper.assertTrue(u.core().in(Step.CLING), "it clings, phase " + u.phase());
            BlockPos c = u.cell();
            helper.assertTrue(c != null && helper.getLevel().getBlockState(c.above()).is(Blocks.END_STONE), "it grips stone");
            BlockPos at = new BlockPos((int) Math.floor(u.getX()), c.getY() + 1, (int) Math.floor(u.getZ()));
            helper.assertTrue(helper.getLevel().getBlockState(at).is(Blocks.END_STONE), "stone over its middle, never open air");
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(pig.getHealth() >= 100.0F, "the pig across the air was never reached");
            helper.succeed();
        });
    }

    /** A player in the middle of the island, away from any edge, is ignored: no reach, no crawl, no harm, for 80 ticks. */
    private static void ignoresInland(GameTestHelper helper) {
        island(helper);
        Underclinger u = clinger(helper, 5, 4, 7);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 6, 8.5));
        u.setTarget(player);
        float full = player.getHealth();
        BlockPos home = u.cell();
        helper.onEachTick(() -> {
            helper.assertTrue(!u.reaching(), "it never reaches for a player away from the edge, phase " + u.phase());
            helper.assertTrue(home.equals(u.cell()), "and never crawls toward one");
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(player.getHealth() >= full, "the player is unhurt");
            helper.assertTrue(Math.abs(rel(helper, player).z - 8.5) < 0.01, "and unmoved");
            helper.succeed();
        });
    }

    /**
     * Within 1 block of an edge: on solid ground 1.5 from the rim (a rim one block deep, ground under the player) the
     * player is ignored, though the clinger lies right under the rim with the player in reach; on the rim block itself
     * it reaches at once.
     */
    private static void edgeRange(GameTestHelper helper) {
        island(helper, 7, 1);
        Underclinger u = clinger(helper, 5, 4, 7);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 6, 8.5));
        u.setTarget(player);
        long[] reach = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) helper.assertTrue(!u.reaching(), "1.5 from the edge on solid ground, it is ignored, phase " + u.phase());
            if (tick == 40) player.snapTo(helper.absoluteVec(new Vec3(5.5, 6, 7.5)));
            if (tick > 40 && reach[0] < 0 && u.core().in(Step.TELL)) reach[0] = tick;
        });
        helper.succeedWhen(() -> helper.assertTrue(reach[0] >= 0 && reach[0] <= 43, "on the rim block it reaches at once, at tick " + reach[0]));
    }

    /** A rim 3 blocks thick is out of its reach: it crawls under the rim but never reaches for the pig above. */
    private static void thickRim(GameTestHelper helper) {
        island(helper, 5, 3);
        Underclinger u = clinger(helper, 5, 2, 6);
        Pig pig = pig(helper, 5.5, 6, 5.5);
        u.setTarget(pig);
        helper.onEachTick(() -> helper.assertTrue(!u.reaching(), "a rim 3 thick is out of reach, phase " + u.phase()));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(pig.getHealth() >= 100.0F, "the pig is unhurt");
            helper.assertTrue(helper.absolutePos(new BlockPos(5, 2, 5)).equals(u.cell()), "it waits right under the rim, in " + u.cell());
            helper.succeed();
        });
    }

    /** A rim 2 blocks thick is just in reach: it reaches up over it for the pig. */
    private static void reachTwo(GameTestHelper helper) {
        island(helper, 5, 2);
        Underclinger u = clinger(helper, 5, 3, 6);
        Pig pig = pig(helper, 5.5, 6, 5.5);
        u.setTarget(pig);
        helper.succeedWhen(() -> helper.assertTrue(u.core().in(Step.TELL) || u.core().in(Step.HOLD), "a rim 2 thick is in reach, phase " + u.phase()));
    }

    /**
     * On a bridge (a block with open air under it), the edge need not be within 1 block: a pig in the middle of a bridge
     * 3 wide (1.5 from either edge) is reached over the nearer-first north edge and dragged 2 blocks out, off the bridge.
     */
    private static void bridge(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) for (int z = 4; z <= 6; z++) helper.setBlock(new BlockPos(x, 5, z), Blocks.END_STONE);
        Underclinger u = clinger(helper, 5, 4, 5);
        Pig pig = pig(helper, 5.5, 6, 5.5);
        u.setTarget(pig);
        helper.succeedWhen(() -> {
            Vec3 at = rel(helper, pig);
            helper.assertTrue(Math.abs(at.y - 1.0) < 0.01 && Math.abs(at.z - 3.5) < 0.01 && Math.abs(at.x - 5.5) < 0.01,
                    "the pig was dragged 2 north off the bridge and fell, at " + at);
        });
    }

    /** It crawls along the underside at 0.1 blocks a tick, 7 cells round to the rim under its target, then reaches. */
    private static void crawl(GameTestHelper helper) {
        island(helper);
        Underclinger u = clinger(helper, 2, 4, 7);
        Pig pig = pig(helper, 7.5, 6, 5.5);
        u.setTarget(pig);
        List<Double> steps = new ArrayList<>();
        Vec3[] last = {null};
        long[] reached = {-1};
        helper.onEachTick(() -> {
            if (u.core().in(Step.CLING)) {
                Vec3 now = u.position();
                if (last[0] != null) steps.add(Math.hypot(now.x - last[0].x, now.z - last[0].z));
                last[0] = now;
            } else if (reached[0] < 0 && u.core().in(Step.TELL)) {
                reached[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(reached[0] >= 0, "it got there and reached");
            List<Double> moving = steps.stream().filter(d -> d > 1.0E-4).toList();
            helper.assertTrue(moving.size() >= 60, "it crawled at least 60 ticks, saw " + moving.size());
            double mean = moving.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - 0.1) < 0.005, "it crawls 0.1 blocks a tick, mean " + mean);
            helper.assertTrue(helper.absolutePos(new BlockPos(7, 4, 5)).equals(u.cell()), "under the rim below its target, in " + u.cell());
        });
    }

    // ---- the event, saving, spawning, the row ----------------------------------------------------------------------

    /** A listener that cancels the "tell" step stops the reach: the pig is never touched. */
    private static void tellVeto(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Underclinger u = atRim(helper, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == u && e.step().equals("tell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(u.core().in(Step.CLING), "a cancelled reach leaves it clinging, phase " + u.phase());
            helper.assertTrue(pig[0].getHealth() >= 100.0F && Math.abs(rel(helper, pig[0]).z - 5.5) < 0.01, "the pig was never touched");
            helper.succeed();
        });
    }

    /** Saved and loaded while it clings, it grips the same underside again on its first tick. */
    private static void saveLoad(GameTestHelper helper) {
        island(helper);
        Underclinger u = clinger(helper, 5, 4, 6);
        Underclinger[] loaded = {null};
        helper.runAtTickTime(3, () -> {
            ServerLevel level = helper.getLevel();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            u.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            u.discard();
            Underclinger fresh = ModMobs.UNDERCLINGER.get().create(level, EntitySpawnReason.LOAD);
            helper.assertTrue(fresh != null, "a fresh entity");
            fresh.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            helper.assertTrue(level.addFreshEntity(fresh), "it joins");
            TestPlayers.whenDone(helper, fresh::discard);
            loaded[0] = fresh;
        });
        helper.runAtTickTime(10, () -> {
            Underclinger f = loaded[0];
            helper.assertTrue(f != null && f.core().in(Step.CLING), "the loaded one clings, phase " + (f == null ? "none" : f.phase()));
            helper.assertTrue(helper.absolutePos(new BlockPos(5, 4, 6)).equals(f.cell()), "under the same block, in " + f.cell());
            helper.assertTrue(Math.abs(rel(helper, f).y + f.getBbHeight() - 5.0) < 0.01, "its back on the stone");
            helper.succeed();
        });
    }

    /**
     * The spawner's underside rule takes the air under an island block with open air below and refuses open air and a
     * spot with ground close below; one spawned at the spot it takes grips the block above on its first tick.
     */
    private static void spawnRule(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("underclinger");
        Rule rule = Rule.of(row.spawner());
        helper.assertTrue(rule == Rule.UNDERSIDE && rule.dimension == Level.END, "placed by the End's underside rule, got " + rule);
        helper.setBlock(new BlockPos(3, 6, 3), Blocks.END_STONE);
        helper.assertTrue(rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(3, 5, 3))), "the air under an island block with a drop below fits");
        helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(8, 5, 8))), "open air doesn't");
        helper.setBlock(new BlockPos(8, 6, 3), Blocks.END_STONE);
        helper.setBlock(new BlockPos(8, 3, 3), Blocks.END_STONE);
        helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(8, 5, 3))), "ground close below doesn't");
        Underclinger u = helper.spawn(ModMobs.UNDERCLINGER.get(), new Vec3(3.5, 5, 3.5));
        u.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, u::discard);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(u.core().in(Step.CLING), "spawned at the spot, it grips the block above, phase " + u.phase());
            helper.assertTrue(Math.abs(rel(helper, u).y + u.getBbHeight() - 6.0) < 0.01, "its back on the stone, top at " + (rel(helper, u).y + u.getBbHeight()));
            helper.succeed();
        });
    }

    /** Health 28, attack 8.5, armour 2, a 1.0 x 0.6 hitbox. */
    private static void stats(GameTestHelper helper) {
        Underclinger u = helper.spawn(ModMobs.UNDERCLINGER.get(), new Vec3(5.5, 1, 5.5));
        u.core().setLook("base", true);
        u.setNoAi(true);
        TestPlayers.whenDone(helper, u::discard);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(u.getMaxHealth() == 28.0F && u.getHealth() == 28.0F, "health 28, got " + u.getHealth() + "/" + u.getMaxHealth());
            helper.assertTrue(u.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8.5, "attack 8.5, got " + u.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(u.getArmorValue() == 2, "armour 2, got " + u.getArmorValue());
            helper.assertTrue(Math.abs(u.getBbWidth() - 1.0F) < 0.01F && Math.abs(u.getBbHeight() - 0.6F) < 0.01F,
                    "a 1.0 x 0.6 hitbox, got " + u.getBbWidth() + " x " + u.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: island undersides in the End, placed only by the spawner, one at a time; a monster; the Pale Clinger; void membrane. */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("underclinger");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.UNDERCLINGER.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.assertTrue(row.spawner().equals("underside"), "the spawner places it under islands, rule " + row.spawner());
        helper.assertTrue(!row.biomeSpawns(), "no biome list: only the spawner places it");
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "one at a time, got " + row.groupMin() + ".." + row.groupMax());
        helper.assertTrue(row.tellTicks() == 10, "a 10-tick tell in its row");
        List<LookDef> looks = Looks.of("underclinger");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("pale_clinger")), "the Pale Clinger is a look: " + looks);
        helper.assertTrue(row.material().equals("void_membrane") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_membrane")),
                "it drops void membrane, a registered item");
        helper.succeed();
    }
}
