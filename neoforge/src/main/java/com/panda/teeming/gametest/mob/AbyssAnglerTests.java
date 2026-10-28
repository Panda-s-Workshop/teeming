package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.AbyssAngler;
import com.panda.teeming.mob.end.AbyssAngler.Step;
import com.panda.teeming.mob.end.Underclinger;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner.Rule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Abyss Angler. The arena holds an island of end stone one block thick (x 1..10, z 3..10, y 5, top at 6) with open air
 * under it down to the floor; its north edge (z 3) is the rim. The angler grips the underside of the rim block (5, 5, 3)
 * and fishes north over it: its casting spot is at (5.5, 6.5, 2.5). A pig (or a survival player) 7 blocks inland is
 * lured for exactly 16 ticks, unmoved and unhurt, then hooked for the angler's attack (4) and reeled north at 0.5 blocks
 * a tick to the brink, its toes at the rim's edge (z 3.0), where it is held 40 ticks and let go on the rim. The reel
 * stops at the near edge of a gap rather than cross it. A swing through the line, or a snowball across it, cuts it: the
 * catch stops where it is, and the angler hangs stunned on the rim's face for exactly 40 ticks, its top at the rim's
 * top, in clear reach of a player at the brink, which it isn't while it clings. The cast reaches 15.9 blocks and not
 * 16.1. It stays pressed to the underside throughout, crawls to the nearest rim at 0.1 blocks a tick, and finishes
 * nothing after it dies. Every number is checked against a plain value.
 */
public final class AbyssAnglerTests {
    private AbyssAnglerTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("abyss_angler_tell", AbyssAnglerTests::tell);
        out.accept("abyss_angler_counter", AbyssAnglerTests::counter);
        out.accept("abyss_angler_failure", AbyssAnglerTests::failure);
        out.accept("abyss_angler_pull", AbyssAnglerTests::pull);
        out.accept("abyss_angler_pulls_players", AbyssAnglerTests::pullsPlayers);
        out.accept("abyss_angler_hold", AbyssAnglerTests::hold);
        TeemingGameTests.timeLimit("abyss_angler_hold", 180);
        out.accept("abyss_angler_gap", AbyssAnglerTests::gap);
        out.accept("abyss_angler_cut_shot", AbyssAnglerTests::cutShot);
        out.accept("abyss_angler_swing_miss", AbyssAnglerTests::swingMiss);
        out.accept("abyss_angler_stun", AbyssAnglerTests::stun);
        TeemingGameTests.timeLimit("abyss_angler_stun", 140);
        out.accept("abyss_angler_range", AbyssAnglerTests::range);
        out.accept("abyss_angler_sight", AbyssAnglerTests::sight);
        out.accept("abyss_angler_clings", AbyssAnglerTests::clings);
        out.accept("abyss_angler_crawl", AbyssAnglerTests::crawl);
        TeemingGameTests.timeLimit("abyss_angler_crawl", 140);
        out.accept("abyss_angler_thick_rim", AbyssAnglerTests::thickRim);
        out.accept("abyss_angler_dies_mid_reel", AbyssAnglerTests::diesMidReel);
        out.accept("abyss_angler_dies_mid_tell", AbyssAnglerTests::diesMidTell);
        out.accept("abyss_angler_cast_veto", AbyssAnglerTests::castVeto);
        out.accept("abyss_angler_save_load", AbyssAnglerTests::saveLoad);
        out.accept("abyss_angler_spawn_rule", AbyssAnglerTests::spawnRule);
        out.accept("abyss_angler_stats", AbyssAnglerTests::stats);
        out.accept("abyss_angler_home", AbyssAnglerTests::home);
    }

    // ---- fixtures --------------------------------------------------------------------------------------------------

    /** The island: end stone one block thick at y 5 (x 1..10, z 3..10), open air under it; its rim faces north (z 3). */
    private static void island(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) for (int z = 3; z <= 10; z++) helper.setBlock(new BlockPos(x, 5, z), Blocks.END_STONE);
    }

    /**
     * An Abyss Angler with the base look gripping the underside of the block at {@code (x, y + 1, z)}, a follow range of
     * {@code follow} (1: it never notices survivors in other arenas), ready to lure, removed when the test ends.
     */
    private static AbyssAngler angler(GameTestHelper helper, int x, int y, int z, double follow) {
        AbyssAngler a = helper.spawn(ModMobs.ABYSS_ANGLER.get(), new Vec3(x + 0.5, y, z + 0.5));
        a.core().setLook("base", true);
        a.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(follow);
        helper.assertTrue(a.hangUnder(helper.absolutePos(new BlockPos(x, y + 1, z))), "it grips the underside at " + x + " " + y + " " + z);
        a.resetCooldown();
        TestPlayers.whenDone(helper, a::discard);
        return a;
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

    /** The standard set-up: the island, the angler under the rim block (5, 5, 3), a pig 7 blocks inland, its target. */
    private static AbyssAngler atRim(GameTestHelper helper, Pig[] target) {
        island(helper);
        AbyssAngler a = angler(helper, 5, 4, 3, 1.0);
        Pig pig = pig(helper, 5.5, 6, 9.5);
        a.setTarget(pig);
        target[0] = pig;
        return a;
    }

    private static Vec3 rel(GameTestHelper helper, LivingEntity e) {
        return helper.relativeVec(e.position());
    }

    // ---- the lure (tell) -------------------------------------------------------------------------------------------

    /**
     * It lures for exactly 16 ticks with its casting spot at (5.5, 6.5, 2.5), over the rim; the pig is neither moved nor
     * hurt meanwhile and nothing holds it; then the line is out and the reel begins.
     */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        long[] start = {-1}, end = {-1};
        Vec3[] at = {null};
        helper.onEachTick(() -> {
            if (a.core().in(Step.TELL)) {
                if (start[0] < 0) {
                    start[0] = helper.getTick();
                    at[0] = pig[0].position();
                }
                helper.assertTrue(pig[0].position().distanceTo(at[0]) < 0.01, "the lure moves nothing");
                helper.assertTrue(pig[0].getHealth() >= 100.0F, "the lure hurts nothing");
                helper.assertTrue(a.victim() == null && !a.core().flag(AbyssAngler.FLAG_LINE), "no line yet");
            } else if (start[0] >= 0 && end[0] < 0) {
                end[0] = helper.getTick();
                helper.assertTrue(a.core().in(Step.REEL), "the lure ends in the reel, phase " + a.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && end[0] >= 0, "a lure and then a reel");
            helper.assertTrue(end[0] - start[0] == 16, "the lure lasts 16 ticks, lasted " + (end[0] - start[0]));
            Vec3 lure = helper.relativeVec(a.lurePoint());
            helper.assertTrue(lure.distanceTo(new Vec3(5.5, 6.5, 2.5)) < 0.01, "it casts from over the rim, at " + lure);
            helper.assertTrue(a.out() == Direction.NORTH && a.rim() == 1, "out over the north rim, one block thick: " + a.out() + " " + a.rim());
        });
    }

    // ---- the hook and the reel -------------------------------------------------------------------------------------

    /**
     * Failing the counter: the hook bites for 4 (its attack; a pig has no armour), and the pig is reeled to the brink:
     * on the rim's last block (5, 5, 3), its middle at z 3.45 (its toes at the edge, z 3.0), standing where an
     * Underclinger's rim rule finds it at the rim.
     */
    private static void failure(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (lost[0] < 0.0F && a.core().in(Step.REEL)) lost[0] = 100.0F - pig[0].getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] >= 0.0F, "it hooked the pig");
            helper.assertTrue(Math.abs(lost[0] - 4.0F) < 0.01F, "the hook bites for 4, the pig lost " + lost[0]);
            helper.assertTrue(a.core().in(Step.HOLD), "it holds the pig at the brink, phase " + a.phase());
            Vec3 at = rel(helper, pig[0]);
            helper.assertTrue(Math.abs(at.z - 3.45) < 0.01 && Math.abs(at.x - 5.5) < 0.01 && Math.abs(at.y - 6.0) < 0.01, "at the brink, at " + at);
            helper.assertTrue(Math.abs(pig[0].getBoundingBox().minZ - helper.absoluteVec(new Vec3(0, 0, 3.0)).z) < 0.01, "its toes at the edge");
            helper.assertTrue(Underclinger.Rim.find(helper.getLevel(), pig[0]) != null, "the Underclinger's rim rule sees it at the rim");
        });
    }

    /**
     * The reel: every tick of it the pig moves exactly 0.5 blocks north (toward the edge) and not at all across, but for
     * the last short step onto the brink; then it stops there (z 3.45) and the hold begins.
     */
    private static void pull(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        List<Vec3> steps = new ArrayList<>();
        Vec3[] last = {null};
        long[] held = {-1};
        helper.onEachTick(() -> {
            if (a.core().in(Step.REEL) || (held[0] < 0 && a.core().in(Step.HOLD))) {
                Vec3 now = rel(helper, pig[0]);
                if (last[0] != null) steps.add(now.subtract(last[0]));
                last[0] = now;
                if (a.core().in(Step.HOLD)) held[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(held[0] >= 0, "the reel ended in the hold");
            List<Vec3> moving = steps.stream().filter(d -> d.length() > 1.0E-4).toList();
            helper.assertTrue(moving.size() == 13, "12 steps of 0.5 and one of 0.05 from z 9.5 to 3.45, saw " + moving.size() + ": " + moving);
            for (int i = 0; i < 12; i++) {
                Vec3 d = moving.get(i);
                helper.assertTrue(Math.abs(d.z + 0.5) < 0.001 && Math.abs(d.x) < 0.001 && Math.abs(d.y) < 0.001, "step " + i + " is 0.5 north, was " + d);
            }
            helper.assertTrue(Math.abs(moving.get(12).z + 0.05) < 0.001, "the last step onto the brink, " + moving.get(12));
            Vec3 at = rel(helper, pig[0]);
            helper.assertTrue(Math.abs(at.z - 3.45) < 0.01 && Math.abs(at.y - 6.0) < 0.01, "stopped at the brink, at " + at);
            helper.assertTrue(Math.abs(a.reeled() - 6.05) < 0.001, "reeled 6.05 blocks, " + a.reeled());
        });
    }

    /** A survival player is reeled the same way (moved, not just pushed): 0.5 a tick to its brink (z 3.3), and hurt. */
    private static void pullsPlayers(GameTestHelper helper) {
        island(helper);
        AbyssAngler a = angler(helper, 5, 4, 3, 12.0);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 6, 9.5));
        a.setTarget(player);
        float full = player.getHealth();
        List<Double> steps = new ArrayList<>();
        Vec3[] last = {null};
        helper.onEachTick(() -> {
            if (a.core().in(Step.REEL)) {
                Vec3 now = rel(helper, player);
                if (last[0] != null) steps.add(last[0].z - now.z);
                last[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(a.core().in(Step.HOLD), "the player is held at the brink, phase " + a.phase());
            Vec3 at = rel(helper, player);
            helper.assertTrue(Math.abs(at.z - 3.3) < 0.01 && Math.abs(at.x - 5.5) < 0.01 && Math.abs(at.y - 6.0) < 0.01, "at its brink, at " + at);
            long full05 = steps.stream().filter(d -> Math.abs(d - 0.5) < 0.001).count();
            helper.assertTrue(full05 >= 10, "reeled 0.5 a tick, steps " + steps);
            helper.assertTrue(player.getHealth() < full, "the hook hurt the player");
        });
    }

    /**
     * The hold lasts exactly 40 ticks, the pig kept at the brink; then it is let go there, on the rim (it doesn't fall),
     * and the angler doesn't lure again for 60 ticks from the let-go.
     */
    private static void hold(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        long[] start = {-1}, end = {-1}, again = {-1};
        helper.onEachTick(() -> {
            if (a.core().in(Step.HOLD)) {
                if (start[0] < 0) start[0] = helper.getTick();
                helper.assertTrue(Math.abs(rel(helper, pig[0]).z - 3.45) < 0.01, "held at the brink");
            } else if (start[0] >= 0 && end[0] < 0) {
                end[0] = helper.getTick();
                helper.assertTrue(a.victim() == null && !Grab.isHeld(pig[0]), "let go");
            } else if (end[0] >= 0 && again[0] < 0 && a.core().in(Step.TELL)) {
                again[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(end[0] >= 0 && again[0] >= 0, "a hold, a let-go and a lure again");
            helper.assertTrue(end[0] - start[0] == 40, "the hold lasts 40 ticks, " + (end[0] - start[0]));
            helper.assertTrue(again[0] - end[0] >= 60 && again[0] - end[0] <= 62, "60 ticks before the next lure, " + (again[0] - end[0]));
            Vec3 at = rel(helper, pig[0]);
            helper.assertTrue(Math.abs(at.y - 6.0) < 0.01 && Math.abs(at.z - 3.45) < 0.01, "the pig let go stays on the rim, at " + at);
        });
    }

    /**
     * Never over an edge: with a gap across the island (z 6 cut out down to the floor) between the pig and the rim, the
     * reel stops at the gap's near edge (the pig's middle at z 7.0, still over stone) and holds it there; it never falls.
     */
    private static void gap(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        for (int x = 1; x <= 10; x++) helper.setBlock(new BlockPos(x, 5, 6), Blocks.AIR);
        helper.onEachTick(() -> helper.assertTrue(rel(helper, pig[0]).y > 5.99, "the pig never drops into the gap"));
        helper.succeedWhen(() -> {
            helper.assertTrue(a.core().in(Step.HOLD), "it holds the pig at the gap, phase " + a.phase());
            Vec3 at = rel(helper, pig[0]);
            helper.assertTrue(Math.abs(at.z - 7.0) < 0.01 && Math.abs(at.y - 6.0) < 0.01, "stopped at the gap's brink, at " + at);
        });
    }

    // ---- the counter: cutting the line -----------------------------------------------------------------------------

    /**
     * The counter: the hooked player swings through the line (looking at a point on it 1.5 blocks from its middle):
     * the line is cut at once, the player is let go where it stands and reeled no further, and the angler is stunned.
     */
    private static void counter(GameTestHelper helper) {
        island(helper);
        AbyssAngler a = angler(helper, 5, 4, 3, 12.0);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 6, 9.5));
        a.setTarget(player);
        long[] swung = {-1}, cut = {-1};
        Vec3[] where = {null};
        helper.onEachTick(() -> {
            if (swung[0] < 0 && a.core().in(Step.REEL) && a.core().elapsed() == 3) {
                Vec3 body = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
                Vec3 on = body.add(a.lurePoint().subtract(body).normalize().scale(1.5));
                player.lookAt(EntityAnchorArgument.Anchor.EYES, on);
                player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
                swung[0] = helper.getTick();
            } else if (swung[0] >= 0 && cut[0] < 0 && a.core().in(Step.STUN)) {
                cut[0] = helper.getTick();
                where[0] = rel(helper, player);
            }
            if (where[0] != null) helper.assertTrue(rel(helper, player).distanceTo(where[0]) < 0.01, "reeled no further after the cut");
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(cut[0] >= 0 && cut[0] - swung[0] == 1, "the swing cut the line on the next tick, " + swung[0] + " -> " + cut[0]);
            helper.assertTrue(a.cuts() == 1 && a.victim() == null && !Grab.isHeld(player), "the line is cut and the player let go");
            helper.assertTrue(where[0].z > 7.0, "cut early in the reel, the player well inland at " + where[0]);
            helper.succeed();
        });
    }

    /** A snowball thrown across the line (2 blocks from the casting spot) cuts it too: the pig stops where it was. */
    private static void cutShot(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        boolean[] thrown = {false};
        long[] cut = {-1};
        Vec3[] where = {null};
        helper.onEachTick(() -> {
            if (!thrown[0] && a.core().in(Step.REEL) && a.core().elapsed() == 1) {
                thrown[0] = true;
                Snowball ball = helper.spawn(EntityTypes.SNOWBALL, new Vec3(8.0, 6.5, 4.5));
                ball.setNoGravity(true);
                ball.setDeltaMovement(new Vec3(-1.0, 0.0, 0.0));
                TestPlayers.whenDone(helper, () -> {
                    if (!ball.isRemoved()) ball.discard();
                });
            } else if (thrown[0] && cut[0] < 0 && a.core().in(Step.STUN)) {
                cut[0] = helper.getTick();
                where[0] = rel(helper, pig[0]);
            }
            if (where[0] != null) helper.assertTrue(Math.abs(rel(helper, pig[0]).z - where[0].z) < 0.01, "reeled no further after the cut");
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(cut[0] >= 0 && a.cuts() == 1, "the snowball cut the line");
            helper.assertTrue(where[0].z > 6.0 && a.victim() == null, "early in the reel, and let go, at " + where[0]);
            helper.succeed();
        });
    }

    /** A swing that doesn't pass the line (the player looks straight up) cuts nothing: the reel goes on to the brink. */
    private static void swingMiss(GameTestHelper helper) {
        island(helper);
        AbyssAngler a = angler(helper, 5, 4, 3, 12.0);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 6, 9.5));
        a.setTarget(player);
        boolean[] swung = {false};
        helper.onEachTick(() -> {
            if (!swung[0] && a.core().in(Step.REEL) && a.core().elapsed() == 3) {
                player.setXRot(-90.0F);
                player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
                swung[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(swung[0] && a.core().in(Step.HOLD) && a.cuts() == 0, "the swing missed, and the reel went on, phase " + a.phase());
            helper.assertTrue(Math.abs(rel(helper, player).z - 3.3) < 0.01, "to the brink, at " + rel(helper, player));
        });
    }

    /**
     * The stun: a cut line stuns it for exactly 40 ticks, luring at no one though the pig is in range; it hangs on the
     * rim's face (out past it, its top level with the rim's top at y 6), its weak point lit, and a player standing at
     * the brink has it within reach (3 blocks) with nothing in the way, which, clinging under the rim, it never is.
     * Then it is back under its rim.
     */
    private static void stun(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        Vec3 brinkEye = helper.absoluteVec(new Vec3(5.5, 6.0 + 1.62, 3.3));
        boolean[] thrown = {false}, hidden = {false}, seen = {false};
        long[] start = {-1}, end = {-1};
        Vec3[] last = {null};
        helper.onEachTick(() -> {
            // under its rim (luring before the cast; after the stun, once it has eased back from the face and settled)
            // it is never in reach from the brink
            boolean settled = last[0] != null && last[0].distanceToSqr(a.position()) < 1.0E-8;
            last[0] = a.position();
            boolean under = start[0] < 0 ? a.core().in(Step.CLING) || a.core().in(Step.TELL) : end[0] >= 0 && a.core().in(Step.CLING) && settled;
            if (under && helper.getTick() > 2) {
                helper.assertTrue(!inReach(helper, a, brinkEye), "under its rim, it is out of reach from the brink");
                hidden[0] = true;
            }
            if (!thrown[0] && a.core().in(Step.REEL) && a.core().elapsed() == 1) {
                thrown[0] = true;
                Snowball ball = helper.spawn(EntityTypes.SNOWBALL, new Vec3(8.0, 6.5, 4.5));
                ball.setNoGravity(true);
                ball.setDeltaMovement(new Vec3(-1.0, 0.0, 0.0));
                TestPlayers.whenDone(helper, () -> {
                    if (!ball.isRemoved()) ball.discard();
                });
            }
            if (a.core().in(Step.STUN)) {
                if (start[0] < 0) start[0] = helper.getTick();
                helper.assertTrue(a.vulnerable(), "its weak point is lit while stunned");
                if (a.core().elapsed() >= 12) {
                    Vec3 r = rel(helper, a);
                    helper.assertTrue(Math.abs(r.y + a.getBbHeight() - 6.0) < 0.01, "its top level with the rim's top, at " + r);
                    helper.assertTrue(r.z + a.getBbWidth() * 0.5 <= 3.0 + 0.01, "out past the rim's face, at " + r);
                    seen[0] |= inReach(helper, a, brinkEye);
                }
            } else if (start[0] >= 0 && end[0] < 0) {
                end[0] = helper.getTick();
                helper.assertTrue(a.core().in(Step.CLING), "back to clinging, phase " + a.phase());
            }
            if (start[0] >= 0 && end[0] < 0) helper.assertTrue(!a.core().in(Step.TELL), "no lure while stunned");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(end[0] >= 0, "a stun that ended");
            helper.assertTrue(end[0] - start[0] == 40, "the stun lasts 40 ticks, " + (end[0] - start[0]));
            helper.assertTrue(hidden[0], "seen under its rim, out of reach from the brink");
            helper.assertTrue(seen[0], "stunned on the rim, it is in reach from the brink");
            helper.assertTrue(helper.absolutePos(new BlockPos(5, 4, 3)).equals(a.cell()) && Math.abs(rel(helper, a).y - 4.0) < 0.31, "back under its rim");
        });
    }

    /** The middle of its body within 3 blocks of {@code eye}, with nothing solid between. */
    private static boolean inReach(GameTestHelper helper, AbyssAngler a, Vec3 eye) {
        Vec3 mid = a.getBoundingBox().getCenter();
        if (mid.distanceTo(eye) > 3.0) return false;
        return helper.getLevel().clip(new ClipContext(eye, mid, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, a)).getType() == HitResult.Type.MISS;
    }

    // ---- range and sight -------------------------------------------------------------------------------------------

    /**
     * The cast reaches 16 blocks. The angler grips under a lone rim block in a corner (1, 6, 0), fishing west, its
     * casting spot over the corner; a pig on the floor toward the far corner 16.1 blocks from the spot (middle to
     * spot) is never lured in 30 ticks; moved to 15.9 blocks, it is lured within 2 ticks.
     */
    private static void range(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 6, 0), Blocks.END_STONE);
        helper.setBlock(new BlockPos(2, 5, 0), Blocks.END_STONE);    // walls beside its cell, so only the west is open
        helper.setBlock(new BlockPos(1, 5, 1), Blocks.END_STONE);
        AbyssAngler a = angler(helper, 1, 5, 0, 1.0);
        helper.assertTrue(a.out() == Direction.WEST, "it fishes west, " + a.out());
        Vec3 lure = a.lurePoint();
        Pig pig = pig(helper, 6.0, 1, 6.0);
        double[] far = {placeAt(helper, pig, lure, 16.1)}, near = {0};
        a.setTarget(pig);
        long[] moved = {-1}, lured = {-1};
        boolean[] early = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (moved[0] < 0) early[0] |= !a.core().in(Step.CLING);
            if (moved[0] < 0 && now >= 30) {
                near[0] = placeAt(helper, pig, lure, 15.9);
                moved[0] = now;
            }
            if (moved[0] >= 0 && lured[0] < 0 && a.core().in(Step.TELL)) lured[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(far[0] - 16.1) < 0.01 && Math.abs(near[0] - 15.9) < 0.01, "set-up: " + far[0] + " then " + near[0]);
            helper.assertTrue(!early[0], "16.1 blocks off it never lured, phase " + a.phase());
            helper.assertTrue(lured[0] >= 0 && lured[0] - moved[0] <= 2, "15.9 blocks off it lures within 2 ticks, " + (lured[0] - moved[0]));
        });
    }

    /** Puts the pig on the floor toward the far corner with its middle {@code d} from {@code lure}; returns the distance. */
    private static double placeAt(GameTestHelper helper, Pig pig, Vec3 lure, double d) {
        double floor = helper.absoluteVec(new Vec3(0, 1, 0)).y;
        double dy = lure.y - (floor + pig.getBbHeight() * 0.5);
        double across = Math.sqrt(d * d - dy * dy) / Math.sqrt(2.0);
        pig.snapTo(new Vec3(lure.x + across, floor, lure.z + across));
        return lure.distanceTo(pig.position().add(0.0, pig.getBbHeight() * 0.5, 0.0));
    }

    /** Sight: with a block between the casting spot and the pig it never lures; the block taken away, it lures. */
    private static void sight(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        island(helper);
        helper.setBlock(new BlockPos(5, 6, 6), Blocks.END_STONE);
        helper.setBlock(new BlockPos(5, 7, 6), Blocks.END_STONE);
        AbyssAngler a = atRim(helper, pig);
        long[] cleared = {-1}, lured = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (cleared[0] < 0) helper.assertTrue(a.core().in(Step.CLING), "a wall in the way: no lure, phase " + a.phase());
            if (cleared[0] < 0 && now >= 30) {
                helper.setBlock(new BlockPos(5, 6, 6), Blocks.AIR);
                helper.setBlock(new BlockPos(5, 7, 6), Blocks.AIR);
                cleared[0] = now;
            }
            if (cleared[0] >= 0 && lured[0] < 0 && a.core().in(Step.TELL)) lured[0] = now;
        });
        helper.succeedWhen(() -> helper.assertTrue(lured[0] >= 0 && lured[0] - cleared[0] <= 2, "in sight it lures, " + lured[0]));
    }

    // ---- the underside ---------------------------------------------------------------------------------------------

    /**
     * It stays on its ceiling: through clinging, the lure, the reel and the hold, its back stays pressed to the
     * underside (its top at y 5.0), it never moves from under its rim block, and that block is stone.
     */
    private static void clings(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        boolean[] phases = new boolean[Step.values().length];
        helper.onEachTick(() -> {
            if (helper.getTick() < 2) return;
            phases[a.phase().ordinal()] = true;
            helper.assertTrue(!a.core().idle() && !a.core().in(Step.STUN), "it clings, phase " + a.phase());
            Vec3 r = rel(helper, a);
            helper.assertTrue(Math.abs(r.y + a.getBbHeight() - 5.0) < 0.01, "its back on the underside, top at " + (r.y + a.getBbHeight()));
            helper.assertTrue(Math.abs(r.x - 5.5) < 0.01 && Math.abs(r.z - 3.5) < 0.01, "under its rim block, at " + r);
            helper.assertTrue(helper.getLevel().getBlockState(a.cell().above()).is(Blocks.END_STONE), "it grips stone");
        });
        helper.succeedWhen(() -> helper.assertTrue(phases[Step.CLING.ordinal()] && phases[Step.TELL.ordinal()] && phases[Step.REEL.ordinal()]
                && phases[Step.HOLD.ordinal()], "clinging through the lure, the reel and the hold"));
    }

    /** Under the middle of the island it crawls along the underside at 0.1 blocks a tick to the nearest rim (3 cells north), then fishes. */
    private static void crawl(GameTestHelper helper) {
        island(helper);
        AbyssAngler a = angler(helper, 5, 4, 6, 1.0);
        helper.assertTrue(a.rim() == 0, "no rim over the middle of the island");
        Pig pig = pig(helper, 5.5, 6, 9.5);
        a.setTarget(pig);
        List<Double> steps = new ArrayList<>();
        Vec3[] last = {null};
        long[] lured = {-1};
        helper.onEachTick(() -> {
            if (a.core().in(Step.CLING)) {
                Vec3 now = a.position();
                if (last[0] != null) steps.add(now.distanceTo(last[0]));
                last[0] = now;
            } else if (lured[0] < 0 && a.core().in(Step.TELL)) {
                lured[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lured[0] >= 0, "it got there and lured");
            List<Double> moving = steps.stream().filter(d -> d > 1.0E-4).toList();
            helper.assertTrue(moving.size() == 30, "3 cells at 0.1 a tick is 30 ticks, saw " + moving.size());
            double mean = moving.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - 0.1) < 0.001, "it crawls 0.1 blocks a tick, mean " + mean);
            helper.assertTrue(helper.absolutePos(new BlockPos(5, 4, 3)).equals(a.cell()) && a.out() == Direction.NORTH, "under the north rim, in " + a.cell());
        });
    }

    /** A rim 4 blocks thick is too thick to fish over: it never lures the pig on top. */
    private static void thickRim(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) for (int z = 3; z <= 10; z++) for (int y = 2; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.END_STONE);
        AbyssAngler a = angler(helper, 5, 1, 3, 1.0);
        Pig pig = pig(helper, 5.5, 6, 6.5);
        a.setTarget(pig);
        helper.onEachTick(() -> helper.assertTrue(!a.core().in(Step.TELL) && !a.fishing(), "a rim 4 thick: no lure, phase " + a.phase()));
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(a.rim() == 0 && pig.getHealth() >= 100.0F, "no rim to fish over, the pig untouched");
            helper.succeed();
        });
    }

    // ---- death, the event, saving --------------------------------------------------------------------------------

    /** Killed mid-reel, it reels no further: the pig stays where it was and is let go. */
    private static void diesMidReel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        Vec3[] at = {null};
        helper.onEachTick(() -> {
            if (at[0] == null && a.core().in(Step.REEL) && a.core().elapsed() == 4) {
                a.hurtServer(helper.getLevel(), a.damageSources().fellOutOfWorld(), 1000.0F);
                at[0] = rel(helper, pig[0]);
            }
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(at[0] != null && a.isDeadOrDying(), "it died in the reel");
            helper.assertTrue(rel(helper, pig[0]).distanceTo(at[0]) < 0.01, "the pig was reeled no further, at " + rel(helper, pig[0]));
            helper.assertTrue(!Grab.isHeld(pig[0]), "and is let go");
            helper.succeed();
        });
    }

    /** Killed mid-lure, it casts nothing: the pig is never hooked, hurt or moved. */
    private static void diesMidTell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        boolean[] killed = {false};
        helper.onEachTick(() -> {
            if (!killed[0] && a.core().in(Step.TELL) && a.core().elapsed() == 10) {
                killed[0] = true;
                a.hurtServer(helper.getLevel(), a.damageSources().fellOutOfWorld(), 1000.0F);
            }
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(killed[0] && a.isDeadOrDying(), "it died in the lure");
            helper.assertTrue(pig[0].getHealth() >= 100.0F && Math.abs(rel(helper, pig[0]).z - 9.5) < 0.01, "the pig untouched, at " + rel(helper, pig[0]));
            helper.assertTrue(a.casts() == 0, "no cast");
            helper.succeed();
        });
    }

    /** A listener that cancels the "cast" step stops it: the lure ends with no line, the pig untouched. */
    private static void castVeto(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AbyssAngler a = atRim(helper, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == a && e.step().equals("cast")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] lured = {false};
        helper.onEachTick(() -> lured[0] |= a.core().in(Step.TELL));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(lured[0] && a.casts() == 0 && !a.fishing(), "lured but no cast, phase " + a.phase());
            helper.assertTrue(pig[0].getHealth() >= 100.0F && Math.abs(rel(helper, pig[0]).z - 9.5) < 0.01, "the pig untouched");
            helper.succeed();
        });
    }

    /** Saved and loaded while it clings, it grips the same underside again on its first tick, fishing over the same rim. */
    private static void saveLoad(GameTestHelper helper) {
        island(helper);
        AbyssAngler a = angler(helper, 5, 4, 3, 1.0);
        AbyssAngler[] loaded = {null};
        helper.runAtTickTime(3, () -> {
            ServerLevel level = helper.getLevel();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            a.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            a.discard();
            AbyssAngler fresh = ModMobs.ABYSS_ANGLER.get().create(level, EntitySpawnReason.LOAD);
            helper.assertTrue(fresh != null, "a fresh entity");
            fresh.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            helper.assertTrue(level.addFreshEntity(fresh), "it joins");
            TestPlayers.whenDone(helper, fresh::discard);
            loaded[0] = fresh;
        });
        helper.runAtTickTime(10, () -> {
            AbyssAngler f = loaded[0];
            helper.assertTrue(f != null && f.core().in(Step.CLING), "the loaded one clings, phase " + (f == null ? "none" : f.phase()));
            helper.assertTrue(helper.absolutePos(new BlockPos(5, 4, 3)).equals(f.cell()), "under the same block, in " + f.cell());
            helper.assertTrue(f.out() == Direction.NORTH && f.rim() == 1, "over the same rim");
            helper.assertTrue(Math.abs(rel(helper, f).y + f.getBbHeight() - 5.0) < 0.01, "its back on the stone");
            helper.succeed();
        });
    }

    // ---- spawning, the row -----------------------------------------------------------------------------------------

    /**
     * The spawner's underside rule takes the air under an island block with open air below and refuses open air and a
     * spot with ground close below; one spawned at the spot it takes grips the block above on its first tick.
     */
    private static void spawnRule(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("abyss_angler");
        Rule rule = Rule.of(row.spawner());
        helper.assertTrue(rule == Rule.UNDERSIDE && rule.dimension == Level.END, "placed by the End's underside rule, got " + rule);
        helper.setBlock(new BlockPos(3, 6, 3), Blocks.END_STONE);
        helper.assertTrue(rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(3, 5, 3))), "the air under an island block with a drop below fits");
        helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(8, 5, 8))), "open air doesn't");
        helper.setBlock(new BlockPos(8, 6, 3), Blocks.END_STONE);
        helper.setBlock(new BlockPos(8, 3, 3), Blocks.END_STONE);
        helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(8, 5, 3))), "ground close below doesn't");
        AbyssAngler a = helper.spawn(ModMobs.ABYSS_ANGLER.get(), new Vec3(3.5, 5, 3.5));
        a.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, a::discard);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(a.core().in(Step.CLING), "spawned at the spot, it grips the block above, phase " + a.phase());
            helper.assertTrue(Math.abs(rel(helper, a).y + a.getBbHeight() - 6.0) < 0.01, "its back on the stone, top at " + (rel(helper, a).y + a.getBbHeight()));
            helper.succeed();
        });
    }

    /** Health 30, attack 4, armour 2, a 1.2 x 1.0 hitbox. */
    private static void stats(GameTestHelper helper) {
        AbyssAngler a = helper.spawn(ModMobs.ABYSS_ANGLER.get(), new Vec3(5.5, 1, 5.5));
        a.core().setLook("base", true);
        a.setNoAi(true);
        TestPlayers.whenDone(helper, a::discard);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(a.getMaxHealth() == 30.0F && a.getHealth() == 30.0F, "health 30, got " + a.getHealth() + "/" + a.getMaxHealth());
            helper.assertTrue(a.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4.0, "attack 4, got " + a.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(a.getArmorValue() == 2, "armour 2, got " + a.getArmorValue());
            helper.assertTrue(Math.abs(a.getBbWidth() - 1.2F) < 0.01F && Math.abs(a.getBbHeight() - 1.0F) < 0.01F,
                    "a 1.2 x 1.0 hitbox, got " + a.getBbWidth() + " x " + a.getBbHeight());
            helper.succeed();
        });
    }

    /**
     * Home: island undersides in the outer End, placed only by the spawner, one at a time; a monster; a 16-tick lure in
     * its row; the Lantern Angler; void membrane.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("abyss_angler");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.ABYSS_ANGLER.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.assertTrue(row.spawner().equals("underside"), "the spawner places it under islands, rule " + row.spawner());
        helper.assertTrue(!row.biomeSpawns(), "no biome list: only the spawner places it");
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "one at a time, got " + row.groupMin() + ".." + row.groupMax());
        helper.assertTrue(row.tellTicks() == 16, "a 16-tick tell in its row");
        List<LookDef> looks = Looks.of("abyss_angler");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("lantern_angler")), "the Lantern Angler is a look: " + looks);
        helper.assertTrue(row.material().equals("void_membrane") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_membrane")),
                "it drops void membrane, a registered item");
        helper.succeed();
    }
}
