package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.advancement.TeemingAdvancements;
import com.panda.teeming.gametest.AdvancementTests;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.MagmaTortoise;
import com.panda.teeming.mob.nether.MagmaTortoise.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Magma Tortoise: HP 60, Atk 0, armour 10, 2.2 x 1.0 (the Old Tortoise x1.3). It floats half sunk in lava and swims at
 * 0.08 blocks a tick, never leaving the lava on purpose; its shell top is solid and carries what stands on it; fed
 * magma cream (one used) it follows that player for 600 ticks, and a leader standing on its shell steers it by their
 * gaze. Nothing burns it and it never attacks: it has no tell. Lava seas in any Nether biome, weight 4, alone.
 *
 * <p>Every test lays its own lava pool, two deep (y 1 and 2, so the surface is at y 2 + 8/9), walled with stone, on
 * the arena floor. Feeding needs a player whose cream is used up, so those tests use real survival players. The
 * tortoises have their goals cleared (no wandering) except in the wander test; their own float, swim and follow still
 * run. Expected values are written out as plain numbers.
 */
public final class MagmaTortoiseTests {
    private MagmaTortoiseTests() {}

    /** The lava surface in the pools, relative to the arena: a source block's lava is 8/9 of a block deep. */
    private static final double SURFACE = 2.0 + 8.0 / 9.0;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("magma_tortoise_tell", MagmaTortoiseTests::noTellNeverAttacks);
        out.accept("magma_tortoise_counter", MagmaTortoiseTests::fedItFollows);
        out.accept("magma_tortoise_failure", MagmaTortoiseTests::wrongFood);
        out.accept("magma_tortoise_follow_length", MagmaTortoiseTests::followLength);
        TeemingGameTests.timeLimit("magma_tortoise_follow_length", 660);
        out.accept("magma_tortoise_refeed", MagmaTortoiseTests::refeed);
        out.accept("magma_tortoise_follow_cancel", MagmaTortoiseTests::followCancel);
        out.accept("magma_tortoise_swim_speed", MagmaTortoiseTests::swimSpeed);
        out.accept("magma_tortoise_floats", MagmaTortoiseTests::floats);
        out.accept("magma_tortoise_stays_in_lava", MagmaTortoiseTests::staysInLava);
        TeemingGameTests.timeLimit("magma_tortoise_stays_in_lava", 140);
        out.accept("magma_tortoise_wanders", MagmaTortoiseTests::wanders);
        TeemingGameTests.timeLimit("magma_tortoise_wanders", 240);
        out.accept("magma_tortoise_platform", MagmaTortoiseTests::platform);
        out.accept("magma_tortoise_carries", MagmaTortoiseTests::carries);
        out.accept("magma_tortoise_steer", MagmaTortoiseTests::steer);
        TeemingGameTests.timeLimit("magma_tortoise_steer", 120);
        out.accept("magma_tortoise_fire_immune", MagmaTortoiseTests::fireImmune);
        out.accept("magma_tortoise_stats", MagmaTortoiseTests::stats);
        out.accept("magma_tortoise_spawns", MagmaTortoiseTests::spawns);
        out.accept("magma_tortoise_feat", MagmaTortoiseTests::feat);
        TeemingGameTests.wide("magma_tortoise_feat");
        TeemingGameTests.timeLimit("magma_tortoise_feat", 420);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Lava two deep from x = 1 to {@code lastLava} (z 1..10), stone walls round it, dry floor beyond. */
    private static void pool(GameTestHelper helper, int lastLava) {
        for (int x = 0; x <= 11; x++) {
            for (int z = 0; z <= 11; z++) {
                for (int y = 1; y <= 2; y++) {
                    boolean wall = x == 0 || z == 0 || z == 11 || x == lastLava + 1 || x == 11;
                    if (wall) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    else if (x <= lastLava) helper.setBlock(new BlockPos(x, y, z), Blocks.LAVA);
                }
            }
        }
    }

    /** A base-look tortoise in the lava at (x, z), goals cleared, facing {@code yaw} (-90 is +X, 0 is +Z). */
    private static MagmaTortoise tortoise(GameTestHelper helper, double x, double z, float yaw) {
        MagmaTortoise t = helper.spawn(ModMobs.MAGMA_TORTOISE.get(), new Vec3(x, 2.4, z));
        t.core().setLook("base", true);
        t.removeFreeWill();
        t.setYRot(yaw);
        t.setYBodyRot(yaw);
        return t;
    }

    private static ServerPlayer withCream(GameTestHelper helper, Vec3 at, int count) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MAGMA_CREAM, count));
        return p;
    }

    private static InteractionResult feed(MagmaTortoise t, ServerPlayer p) {
        return t.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    private static double surface(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(0, SURFACE, 0)).y;
    }

    private static Vec3 flat(Entity e) {
        return e.position().multiply(1, 0, 1);
    }

    // ---- the tell, the counter, failing it -------------------------------------------------------------------------

    /**
     * No tell, because it never attacks: a survival player hits it for 4 (armour 10 leaves 2.72) and for the next 60
     * ticks it starts no phase and posts no mechanic step, picks no target, and the player loses nothing.
     */
    private static void noTellNeverAttacks(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 5.5, 5.5, 0.0F);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 3, 0.5));
        List<String> steps = new ArrayList<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == t) steps.add(e.step());
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        helper.runAtTickTime(5, () -> {
            float before = t.getHealth();
            t.hurtServer(helper.getLevel(), t.damageSources().playerAttack(p), 4.0F);
            float lost = before - t.getHealth();
            helper.assertTrue(Math.abs(lost - 2.72F) < 0.01F, "armour 10 takes a 4 hit down to 2.72, lost " + lost);
        });
        helper.onEachTick(() -> {
            if (helper.getTick() <= 5) return;
            helper.assertTrue(t.core().idle(), "it starts nothing after the hit, phase " + t.phase());
            helper.assertTrue(t.getTarget() == null, "it picks no target");
            helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "the player loses nothing, health " + p.getHealth());
        });
        helper.runAtTickTime(66, () -> {
            helper.assertTrue(steps.isEmpty(), "no mechanic step was posted: " + steps);
            helper.succeed();
        });
    }

    /**
     * The counter: magma cream from a survival player 8 blocks off is eaten (3 become 2), it follows that player, and
     * it paddles more than 3 blocks towards them across the lava.
     */
    private static void fedItFollows(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 3.5, 5.5, -90.0F);
        ServerPlayer p = withCream(helper, new Vec3(11.5, 3, 5.5), 3);
        double[] start = {0};
        helper.runAtTickTime(5, () -> {
            InteractionResult result = feed(t, p);
            helper.assertTrue(result.consumesAction(), "the cream takes, got " + result);
            helper.assertTrue(p.getMainHandItem().getCount() == 2, "one cream is used, " + p.getMainHandItem().getCount() + " left");
            helper.assertTrue(t.core().in(Step.FOLLOW) && t.leader() == p, "it follows the player who fed it, phase " + t.phase());
            start[0] = flat(t).distanceTo(flat(p));
        });
        helper.runAtTickTime(80, () -> {
            double now = flat(t).distanceTo(flat(p));
            helper.assertTrue(start[0] - now > 3.0, "it paddled towards the player: " + start[0] + " to " + now);
            helper.assertTrue(t.isInLava(), "still in the lava");
            helper.succeed();
        });
    }

    /** Failing it: a slime ball or an empty hand feeds nothing; nothing is used and it stays where it floats, idle. */
    private static void wrongFood(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 3.5, 5.5, -90.0F);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(11.5, 3, 5.5));
        Vec3[] at = {null};
        helper.runAtTickTime(20, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 3));
            helper.assertTrue(!feed(t, p).consumesAction(), "a slime ball doesn't take");
            helper.assertTrue(p.getMainHandItem().getCount() == 3, "nothing is used, " + p.getMainHandItem().getCount() + " left");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(!feed(t, p).consumesAction(), "an empty hand does nothing");
            helper.assertTrue(t.core().idle() && t.leader() == null, "it follows no one, phase " + t.phase());
            at[0] = flat(t);
        });
        helper.runAtTickTime(70, () -> {
            double moved = flat(t).distanceTo(at[0]);
            helper.assertTrue(moved < 0.05, "it stays where it floats, moved " + moved);
            helper.succeed();
        });
    }

    /** It follows for exactly 600 ticks, counted tick by tick, then rests and stops paddling towards the player. */
    private static void followLength(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 5.5, 5.5, 0.0F);
        ServerPlayer p = withCream(helper, new Vec3(5.5, 3, 11.5), 1);
        int[] following = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() == 5) helper.assertTrue(feed(t, p).consumesAction(), "fed");
            if (t.core().in(Step.FOLLOW)) following[0]++;
        });
        helper.runAtTickTime(5 + 600 + 20, () -> {
            helper.assertTrue(following[0] == 600, "it follows for 600 ticks, counted " + following[0]);
            helper.assertTrue(t.core().idle() && t.leader() == null, "then it follows no one, phase " + t.phase());
            helper.assertTrue(t.wanted() == null, "and paddles nowhere, wanted " + t.wanted());
            helper.assertTrue(p.getMainHandItem().isEmpty(), "the one cream was used");
            helper.succeed();
        });
    }

    /** Fed again it starts its 600 ticks over, with a second cream; fed by someone else it follows them instead. */
    private static void refeed(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 5.5, 5.5, 0.0F);
        ServerPlayer first = withCream(helper, new Vec3(0.5, 3, 5.5), 2);
        ServerPlayer second = withCream(helper, new Vec3(11.5, 3, 5.5), 2);
        helper.runAtTickTime(5, () -> helper.assertTrue(feed(t, first).consumesAction(), "the first feeds it"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(t.core().elapsed() >= 24, "25 ticks into following, " + t.core().elapsed());
            helper.assertTrue(feed(t, first).consumesAction() && first.getMainHandItem().getCount() == 0, "a second cream, used");
            helper.assertTrue(t.core().elapsed() == 0, "the follow starts over, " + t.core().elapsed() + " ticks in");
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(feed(t, second).consumesAction() && second.getMainHandItem().getCount() == 1, "someone else feeds it, one used");
            helper.assertTrue(t.leader() == second, "and it follows them now");
            helper.succeed();
        });
    }

    /** A listener that cancels the "follow" step keeps the cream in hand and the tortoise idle. */
    private static void followCancel(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 5.5, 5.5, 0.0F);
        ServerPlayer p = withCream(helper, new Vec3(5.5, 3, 11.5), 3);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == t && e.step().equals("follow")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(!feed(t, p).consumesAction(), "the vetoed feed doesn't take");
            helper.assertTrue(p.getMainHandItem().getCount() == 3, "no cream used, " + p.getMainHandItem().getCount());
            helper.assertTrue(t.core().idle(), "it stays idle, phase " + t.phase());
            helper.succeed();
        });
    }

    // ---- swimming and floating ---------------------------------------------------------------------------------------

    /** Cruising across the lava it swims 0.08 blocks a tick: measured over 30 ticks, and never faster on any tick. */
    private static void swimSpeed(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 3.0, 5.5, -90.0F);
        Vec3[] from = {null};
        double[] fastest = {0};
        Vec3[] last = {null};
        helper.runAtTickTime(2, () -> t.paddleTo(helper.absoluteVec(new Vec3(9.8, 2.4, 5.5))));
        helper.onEachTick(() -> {
            Vec3 now = flat(t);
            if (last[0] != null && helper.getTick() > 3) fastest[0] = Math.max(fastest[0], now.distanceTo(last[0]));
            last[0] = now;
            if (helper.getTick() == 25) from[0] = now;
        });
        helper.runAtTickTime(55, () -> {
            double speed = flat(t).distanceTo(from[0]) / 30.0;
            helper.assertTrue(Math.abs(speed - 0.08) < 0.003, "it swims 0.08 blocks a tick, measured " + speed);
            helper.assertTrue(fastest[0] < 0.0805, "never faster on any tick, fastest " + fastest[0]);
            helper.succeed();
        });
    }

    /**
     * It floats half sunk: from the bottom of the pool both looks rise and hold with half their height under the lava
     * surface, so the base look's shell top is 0.5 above it and the Old Tortoise's (1.3 tall) 0.65.
     */
    private static void floats(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise base = helper.spawn(ModMobs.MAGMA_TORTOISE.get(), new Vec3(5.5, 1.0, 3.5));
        MagmaTortoise old = helper.spawn(ModMobs.MAGMA_TORTOISE.get(), new Vec3(5.5, 1.0, 7.8));
        base.core().setLook("base", true);
        old.core().setLook("old_tortoise", true);
        base.removeFreeWill();
        old.removeFreeWill();
        helper.runAtTickTime(60, () -> {
            double top = surface(helper);
            double baseAbove = base.getBoundingBox().maxY - top;
            double oldAbove = old.getBoundingBox().maxY - top;
            helper.assertTrue(Math.abs(baseAbove - 0.5) < 0.02, "the base shell top floats 0.5 over the lava, " + baseAbove);
            helper.assertTrue(Math.abs(oldAbove - 0.65) < 0.02, "the old shell top floats 0.65 over the lava, " + oldAbove);
            helper.assertTrue(base.isInLava() && old.isInLava(), "both float in the lava");
            helper.assertTrue(Math.abs(base.getDeltaMovement().y) < 0.005, "and hold there, vertical speed " + base.getDeltaMovement().y);
            helper.succeed();
        });
    }

    /**
     * It never leaves the lava on purpose: following a player on dry ground past the shore, it paddles to the shore
     * and stops there, in the lava, every tick.
     */
    private static void staysInLava(GameTestHelper helper) {
        pool(helper, 6);
        MagmaTortoise t = tortoise(helper, 3.0, 5.5, -90.0F);
        ServerPlayer p = withCream(helper, new Vec3(9.5, 1, 5.5), 1);
        double startX = t.getX();
        helper.runAtTickTime(5, () -> helper.assertTrue(feed(t, p).consumesAction(), "fed"));
        helper.onEachTick(() -> helper.assertTrue(helper.getTick() < 3 || t.isInLava(), "it stays in the lava"));
        helper.runAtTickTime(125, () -> {
            double shore = helper.absoluteVec(new Vec3(7.0, 0, 0)).x;
            double front = t.getBoundingBox().maxX;
            helper.assertTrue(t.getX() - startX > 1.5, "it paddled towards the player, " + (t.getX() - startX));
            helper.assertTrue(front <= shore, "and stopped at the shore, its front " + (shore - front) + " short of it");
            helper.assertTrue(t.core().in(Step.FOLLOW) && t.wanted() != null, "still wanting to reach the player");
            helper.assertTrue(t.getDeltaMovement().horizontalDistance() < 0.01, "but not moving, " + t.getDeltaMovement());
            helper.succeed();
        });
    }

    /** With its goals on and no one to follow, it swims off on its own within 200 ticks, and stays in the lava. */
    private static void wanders(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = helper.spawn(ModMobs.MAGMA_TORTOISE.get(), new Vec3(6.0, 2.4, 6.0));
        t.core().setLook("base", true);
        Vec3 start = flat(t);
        double[] farthest = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() < 3) return;
            helper.assertTrue(t.isInLava(), "it stays in the lava");
            farthest[0] = Math.max(farthest[0], flat(t).distanceTo(start));
        });
        helper.runAtTickTime(210, () -> {
            helper.assertTrue(farthest[0] > 1.0, "it swam off on its own, farthest " + farthest[0]);
            helper.assertTrue(t.core().idle(), "unfed, it follows no one");
            helper.succeed();
        });
    }

    // ---- the shell as a platform -------------------------------------------------------------------------------------

    /**
     * Its shell is solid: a survival player's fall onto it stops on its top, and a pig dropped on it stands there over
     * the lava, out of it.
     */
    private static void platform(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 5.5, 5.5, 0.0F);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(5.5, 4.5, 5.5));
        pig.removeFreeWill();
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(4.8, 4, 5.0));
        helper.runAtTickTime(40, () -> {
            double top = t.getBoundingBox().maxY;
            helper.assertTrue(t.canBeCollidedWith(p), "its shell is solid to a player");
            p.snapTo(p.getX(), top + 0.5, p.getZ());
            AABB box = p.getBoundingBox();
            Vec3 fall = Entity.collideBoundingBox(p, new Vec3(0, -2, 0), box, helper.getLevel(),
                    Entity.collectAllColliders(p, helper.getLevel(), box.expandTowards(0, -2, 0)));
            helper.assertTrue(Math.abs(fall.y + 0.5) < 0.01, "a fall of 2 stops on the shell after 0.5, " + fall.y);
            helper.assertTrue(Math.abs(pig.getY() - top) < 0.02, "the pig stands on the shell top, " + (pig.getY() - top) + " off");
            helper.assertTrue(!pig.isInLava() && pig.getY() > surface(helper), "over the lava, out of it");
            helper.assertTrue(t.carries(pig), "the tortoise counts it as standing on its shell");
            helper.succeed();
        });
    }

    /** What stands on its shell rides along: a pig on it moves as far as it swims (over 2 blocks) and stays on top. */
    private static void carries(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 3.5, 5.5, -90.0F);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(3.5, 4.5, 5.5));
        pig.removeFreeWill();
        double[] start = {0, 0};
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(t.carries(pig), "the pig landed on the shell");
            start[0] = t.getX();
            start[1] = pig.getX();
            t.paddleTo(helper.absoluteVec(new Vec3(9.5, 2.4, 5.5)));
        });
        helper.runAtTickTime(85, () -> {
            double swam = t.getX() - start[0];
            double rode = pig.getX() - start[1];
            helper.assertTrue(swam > 2.0, "it swam " + swam);
            helper.assertTrue(Math.abs(rode - swam) < 0.15, "the pig rode as far, " + rode + " of " + swam);
            helper.assertTrue(t.carries(pig) && !pig.isInLava(), "still on the shell, out of the lava");
            helper.succeed();
        });
    }

    /**
     * Steering: a leader standing on its shell, looking along +Z while it faces +X, makes it turn and swim the way they
     * look, carrying them along.
     */
    private static void steer(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 5.5, 3.5, -90.0F);
        ServerPlayer p = withCream(helper, new Vec3(0.5, 3, 0.5), 1);
        double[] start = {0, 0, 0};
        helper.runAtTickTime(20, () -> {
            p.snapTo(t.getX(), t.getBoundingBox().maxY, t.getZ(), 0.0F, 0.0F);
            helper.assertTrue(t.carries(p), "the player stands on the shell");
            helper.assertTrue(feed(t, p).consumesAction(), "and feeds it");
            start[0] = t.getZ();
            start[1] = p.getZ();
            start[2] = t.getX();
        });
        helper.runAtTickTime(100, () -> {
            double went = t.getZ() - start[0];
            double rode = p.getZ() - start[1];
            helper.assertTrue(went > 1.5, "it swam the way the player looks (+Z), " + went);
            helper.assertTrue(Math.abs(t.getX() - start[2]) < 0.8, "not on along its old heading, " + (t.getX() - start[2]));
            helper.assertTrue(Math.abs(rode - went) < 0.15, "carrying the player as far, " + rode + " of " + went);
            helper.assertTrue(t.carries(p), "who is still on the shell");
            helper.succeed();
        });
    }

    /**
     * Lava Ferry: a survival player standing on its shell while it swims a long pool (in the wide arena: lava two deep
     * from x = 1 to 24, z 1..7) earns the advancement once it has carried them 16 blocks across the lava, not before.
     */
    private static void feat(GameTestHelper helper) {
        for (int x = 0; x <= 25; x++) {
            for (int z = 0; z <= 8; z++) {
                for (int y = 1; y <= 2; y++) {
                    boolean wall = x == 0 || x == 25 || z == 0 || z == 8;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.LAVA);
                }
            }
        }
        MagmaTortoise t = tortoise(helper, 3.5, 4.5, -90.0F);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(0.5, 3, 0.5));
        double[] startX = {0};
        double[] whenEarned = {-1};
        helper.runAtTickTime(20, () -> {
            p.snapTo(t.getX(), t.getBoundingBox().maxY, t.getZ(), -90.0F, 0.0F);
            helper.assertTrue(t.carries(p), "the player stands on the shell");
            startX[0] = t.getX();
            t.paddleTo(helper.absoluteVec(new Vec3(23.5, 2.4, 4.5)));
        });
        helper.onEachTick(() -> {
            if (whenEarned[0] < 0 && AdvancementTests.done(helper, p, TeemingAdvancements.MAGMA_TORTOISE_RIDE)) whenEarned[0] = t.ridden(p);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(whenEarned[0] >= 0, "not earned yet: carried " + t.ridden(p) + " of 16, swum " + (t.getX() - startX[0])
                    + ", still on the shell " + t.carries(p));
            helper.assertTrue(whenEarned[0] >= MagmaTortoise.FERRY_BLOCKS && whenEarned[0] < MagmaTortoise.FERRY_BLOCKS + 0.2,
                    "earned at 16 blocks carried, not before: " + whenEarned[0]);
            helper.assertTrue(Math.abs(MagmaTortoise.FERRY_BLOCKS - 16.0) < 1.0E-9, "a ride of 16 blocks, " + MagmaTortoise.FERRY_BLOCKS);
            helper.assertTrue(t.carries(p), "the player rode all the way on the shell");
        });
    }

    // ---- fire, stats, spawning ---------------------------------------------------------------------------------------

    /** Fire-immune: 80 ticks in lava cost nothing and never set it alight; lava and fire damage are refused outright. */
    private static void fireImmune(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 5.5, 5.5, 0.0F);
        helper.onEachTick(() -> helper.assertTrue(!t.isOnFire(), "it never burns"));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(t.isInLava(), "it was in the lava");
            helper.assertTrue(t.getHealth() >= 60.0F, "80 ticks of lava cost nothing, health " + t.getHealth());
            helper.assertTrue(t.fireImmune() && t.getType().fireImmune(), "its type is fire-immune");
            helper.assertTrue(!t.hurtServer(helper.getLevel(), t.damageSources().lava(), 4.0F), "lava damage is refused");
            helper.assertTrue(!t.hurtServer(helper.getLevel(), t.damageSources().onFire(), 4.0F), "burning is refused");
            helper.assertTrue(t.getHealth() >= 60.0F, "still at full health, " + t.getHealth());
            helper.succeed();
        });
    }

    /** Health 60, attack 0, armour 10, a 2.2 x 1.0 hitbox; the Old Tortoise is 1.3 times the size with the same health. */
    private static void stats(GameTestHelper helper) {
        pool(helper, 10);
        MagmaTortoise t = tortoise(helper, 3.5, 5.5, 0.0F);
        MagmaTortoise old = tortoise(helper, 8.0, 5.5, 0.0F);
        old.core().setLook("old_tortoise", true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(t.getMaxHealth() - 60.0F) < 0.01F && Math.abs(t.getHealth() - 60.0F) < 0.01F,
                    "health 60, got " + t.getHealth() + "/" + t.getMaxHealth());
            helper.assertTrue(Math.abs(t.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.01, "attack 0");
            helper.assertTrue(Math.abs(t.getAttributeValue(Attributes.ARMOR) - 10.0) < 0.01, "armour 10");
            helper.assertTrue(Math.abs(t.getBbWidth() - 2.2F) < 0.02F && Math.abs(t.getBbHeight() - 1.0F) < 0.02F,
                    "hitbox 2.2 x 1.0, got " + t.getBbWidth() + " x " + t.getBbHeight());
            helper.assertTrue(Math.abs(old.getBbWidth() - 2.86F) < 0.02F && Math.abs(old.getBbHeight() - 1.3F) < 0.02F,
                    "the Old Tortoise is 2.86 x 1.3, got " + old.getBbWidth() + " x " + old.getBbHeight());
            helper.assertTrue(Math.abs(old.getMaxHealth() - 60.0F) < 0.01F, "with health 60, got " + old.getMaxHealth());
            helper.assertTrue(t.getType().getCategory() == MobCategory.CREATURE, "a creature, " + t.getType().getCategory());
            helper.succeed();
        });
    }

    /**
     * Its home: the biome modifier adds it alone at weight 4 to every Nether biome; the lava placement takes lava with
     * air over it and refuses dry ground; and in lava its obstruction and spawn rules pass in any light.
     */
    private static void spawns(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/magma_tortoise"));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key).map(Holder::value).orElse(null);
        if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) {
            helper.fail("no spawn biome modifier for the Magma Tortoise: " + modifier);
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> spawners = add.spawners().unwrap();
        helper.assertTrue(spawners.size() == 1 && spawners.getFirst().value().type() == ModMobs.MAGMA_TORTOISE.get(), "one tortoise spawner, " + spawners);
        Weighted<MobSpawnSettings.SpawnerData> spawner = spawners.getFirst();
        helper.assertTrue(spawner.weight() == 4, "spawn weight 4, got " + spawner.weight());
        helper.assertTrue(spawner.value().count().minInclusive() == 1 && spawner.value().count().maxInclusive() == 1,
                "alone, got " + spawner.value().count().minInclusive() + " to " + spawner.value().count().maxInclusive());
        var biomes = access.lookupOrThrow(Registries.BIOME);
        for (var biome : List.of(Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS, Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST, Biomes.SOUL_SAND_VALLEY)) {
            helper.assertTrue(add.biomes().contains(biomes.getOrThrow(biome)), biome.identifier() + " is its home");
        }
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.PLAINS)), "the Overworld is not");

        pool(helper, 10);
        RosterStats.Entry row = RosterStats.get("magma_tortoise");
        MagmaTortoise t = helper.spawn(ModMobs.MAGMA_TORTOISE.get(), new Vec3(5.5, 2.0, 5.5));
        t.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(row.place() == RosterStats.Place.LAVA, "it is a lava mob, place " + row.place());
            helper.assertTrue(TeemingSpawns.placeOk(row, t.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5))), "lava under air is a spawn spot");
            helper.assertTrue(!TeemingSpawns.placeOk(row, t.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))), "lava under lava is not");
            helper.assertTrue(!TeemingSpawns.placeOk(row, t.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 3, 5))), "the air above it is not");
            helper.assertTrue(t.checkSpawnObstruction(helper.getLevel()), "lava doesn't count as obstruction");
            helper.assertTrue(t.checkSpawnRules(helper.getLevel(), EntitySpawnReason.NATURAL), "its spawn rules accept lava in any light");
            helper.succeed();
        });
    }
}
