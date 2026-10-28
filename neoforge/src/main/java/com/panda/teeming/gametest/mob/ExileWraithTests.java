package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.end.ExileWraith;
import com.panda.teeming.mob.end.ExileWraith.Roll;
import com.panda.teeming.mob.end.ExileWraith.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Exile Wraith. A mark lasts exactly 20 ticks, its circle of radius 1 under the target's feet (0.9 from the middle is on
 * it, 1.1 is off), and nothing moves the target before it fills. A survival player still on it when it fills lands 24 to
 * 48 blocks away on solid ground under open sky, unhurt; with no land in the rolled direction the search finds land
 * elsewhere round the circle and lower down; with land only nearer than 24, farther than 48 or under a roof, nothing
 * happens. Stepping out leaves the target where it stepped, and the wraith is dragged into its own circle and held there
 * exactly 40 ticks, within a sword's reach. Nobody is sent away twice within 200 ticks. The claw winds up 8 ticks, then
 * takes the attack of 4 (armour counts: 2.72 through 10 armour), with no knockback and never at a body on a pillar. Killed
 * mid-mark or mid-wind-up it finishes nothing. Stats, home and spawn rule against plain values.
 *
 * <p>An exile reaches far past a 12-block arena, so the tests that let one happen run on a patch of sky far from every
 * arena ({@link Site}), where the test builds every block of land within reach itself. A patch forced fresh can take
 * many ticks before its mobs tick (past a hundred on a slow machine), so those tests count every step from the tick
 * their wraith first ticks, and have room for that wait in their time limits.
 */
public final class ExileWraithTests {
    private ExileWraithTests() {}

    /** The time limit of a test on a patch of sky: its own steps and up to 300 ticks of waiting for the patch to tick. */
    private static final int SITE_TICKS = 400;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("exile_wraith_tell", ExileWraithTests::tell);
        TeemingGameTests.timeLimit("exile_wraith_tell", SITE_TICKS);
        out.accept("exile_wraith_counter", ExileWraithTests::counter);
        out.accept("exile_wraith_failure", ExileWraithTests::failure);
        TeemingGameTests.timeLimit("exile_wraith_failure", SITE_TICKS);
        out.accept("exile_wraith_radius", ExileWraithTests::radius);
        out.accept("exile_wraith_range", ExileWraithTests::range);
        out.accept("exile_wraith_far_land", ExileWraithTests::farLand);
        TeemingGameTests.timeLimit("exile_wraith_far_land", SITE_TICKS);
        out.accept("exile_wraith_no_land", ExileWraithTests::noLand);
        TeemingGameTests.timeLimit("exile_wraith_no_land", SITE_TICKS);
        out.accept("exile_wraith_once_per_200", ExileWraithTests::oncePer200);
        TeemingGameTests.timeLimit("exile_wraith_once_per_200", SITE_TICKS + 200);
        out.accept("exile_wraith_claw", ExileWraithTests::claw);
        out.accept("exile_wraith_armour", ExileWraithTests::armour);
        out.accept("exile_wraith_pillar", ExileWraithTests::pillar);
        out.accept("exile_wraith_dies_mid_mark", ExileWraithTests::diesMidMark);
        out.accept("exile_wraith_dies_mid_claw", ExileWraithTests::diesMidClaw);
        out.accept("exile_wraith_spawn_rule", ExileWraithTests::spawnRule);
        out.accept("exile_wraith_stats", ExileWraithTests::stats);
        out.accept("exile_wraith_home", ExileWraithTests::home);
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------------

    /**
     * A wraith with the base look at {@code (x, 1, z)} in the arena, facing +X, no goals, a follow range of 1 (it notices
     * no survivor in another arena) and its mark held back, removed when the test ends.
     */
    private static ExileWraith wraith(GameTestHelper helper, double x, double z) {
        ExileWraith w = helper.spawn(ModMobs.EXILE_WRAITH.get(), new Vec3(x, 1, z));
        calm(w);
        TestPlayers.whenDone(helper, w::discard);
        return w;
    }

    private static void calm(ExileWraith w) {
        w.core().setLook("base", true);
        w.removeFreeWill();
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        w.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        w.setMarkCooldown(100000);
        turn(w, -90.0F);
    }

    /** A still pig with 100 health in the arena, removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static void turn(LivingEntity e, float yaw) {
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.yHeadRotO = yaw;
        e.yBodyRot = yaw;
        e.yBodyRotO = yaw;
    }

    private static double across(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** True when {@code e} stands on solid ground with open sky over it. */
    private static boolean onOpenGround(ServerLevel level, Entity e) {
        BlockPos under = BlockPos.containing(e.getX(), e.getY() - 0.01, e.getZ());
        return !level.getBlockState(under).getCollisionShape(level, under).isEmpty()
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, under.getX(), under.getZ()) <= under.getY() + 1;
    }

    /**
     * A patch of open sky far from every arena: {@code k} times 30,000 blocks east of the arena and 100 up, each test with
     * its own {@code k}, so no two tests' patches come near each other however the arenas are laid out, and no block laid
     * here hangs over another test's arena (an open-sky check under it would fail). Every block the test lays is recorded
     * and taken away when it ends, with the chunks it forced and the mobs it made there.
     */
    private static final class Site {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos centre;
        final List<BlockPos> laid = new ArrayList<>();
        final List<long[]> forced = new ArrayList<>();
        final List<Entity> made = new ArrayList<>();

        Site(GameTestHelper helper, int k) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.centre = helper.absolutePos(new BlockPos(6, 100, 6)).offset(30000 * k, 0, 0);
            TestPlayers.whenDone(helper, this::clear);
            for (int dy = -20; dy <= 12; dy++) {
                BlockPos p = centre.above(dy);
                level.getChunk(p);
                if (!level.getBlockState(p).isAir()) helper.fail("the patch of sky isn't open: " + level.getBlockState(p) + " at " + p);
            }
        }

        /** Runs {@code start} once, on the first tick {@code mob} has ticked twice: the test's steps count from there. */
        void whenTicking(Entity mob, Runnable start) {
            boolean[] done = {false};
            helper.onEachTick(() -> {
                if (!done[0] && mob.tickCount >= 2) {
                    done[0] = true;
                    start.run();
                }
            });
        }

        /** A square of end stone {@code 2 * half + 1} across at {@code (dx, dy, dz)} from the centre. */
        Site floor(int dx, int dy, int dz, int half) {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) lay(centre.offset(dx + x, dy, dz + z), Blocks.END_STONE.defaultBlockState());
            }
            return this;
        }

        void lay(BlockPos pos, BlockState state) {
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            if (forced.stream().noneMatch(c -> c[0] == cx && c[1] == cz)) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
                forced.add(new long[] {cx, cz});
            }
            level.setBlockAndUpdate(pos, state);
            laid.add(pos.immutable());
        }

        /** A point on the centre's floor level, offset in blocks (y 1 stands on the floor). */
        Vec3 at(double dx, double dy, double dz) {
            return new Vec3(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
        }

        ExileWraith wraith(double dx, double dz) {
            ExileWraith w = ModMobs.EXILE_WRAITH.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            Vec3 p = at(dx, 1, dz);
            w.snapTo(p.x, p.y, p.z, -90.0F, 0.0F);
            w.setPersistenceRequired();
            level.addFreshEntity(w);
            calm(w);
            made.add(w);
            return w;
        }

        Pig pig(double dx, double dy, double dz) {
            Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
            Vec3 p = at(dx, dy, dz);
            pig.snapTo(p.x, p.y, p.z, 0.0F, 0.0F);
            pig.setNoAi(true);
            pig.setPersistenceRequired();
            level.addFreshEntity(pig);
            pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
            pig.setHealth(100.0F);
            made.add(pig);
            return pig;
        }

        void clear() {
            for (Entity e : made) e.discard();
            for (BlockPos p : laid) {
                if (level.hasChunkAt(p)) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
            for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], false);
        }
    }

    // ---- the mark ------------------------------------------------------------------------------------------------------

    /**
     * The tell: on a patch of sky, a mark on a still pig lasts exactly 20 ticks; its circle lies under the pig's feet with
     * radius 1; through it the pig stays where it stood and is unhurt. The tick the mark ends, the pig is gone from there,
     * sent to the only land in reach, a pad 30 blocks east (the roll fixed at 30 blocks, due east).
     */
    private static void tell(GameTestHelper helper) {
        Site site = new Site(helper, 1).floor(0, 0, 0, 5).floor(30, 0, 0, 2);
        ExileWraith w = site.wraith(-4.5, 0.5);
        Pig pig = site.pig(0.5, 1, 0.5);
        Vec3 start = pig.position();
        w.fixRoll(new Roll(30.0, 0.0));
        int[] held = {0};
        boolean[] ended = {false};
        boolean[] opened = {false};
        helper.onEachTick(() -> {
            // opened here rather than in a separate callback, so it is counted from the tick it opens whatever order the
            // test's callbacks run in, once the wraith ticks
            if (!opened[0] && w.tickCount >= 2) {
                opened[0] = true;
                helper.assertTrue(w.mark(pig), "the mark opens");
            }
            if (w.core().in(Step.TELL)) {
                held[0]++;
                var mark = w.mark();
                helper.assertTrue(mark != null && Math.abs(mark.radius() - 1.0) < 1.0E-6, "a circle of radius 1 is down");
                helper.assertTrue(across(mark.centre(), start) < 1.0E-6 && Math.abs(mark.centre().y - start.y) < 1.0E-6, "under the pig's feet");
                helper.assertTrue(pig.position().distanceTo(start) < 1.0E-6, "the pig stays where it stood while it fills");
                helper.assertTrue(pig.getHealth() >= 100.0F, "and is unhurt");
            } else if (held[0] > 0 && !ended[0]) {
                ended[0] = true;
                helper.assertTrue(across(pig.position(), start) > 20.0, "the tick the mark ends the pig is gone, at " + pig.position());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the mark ran out");
            helper.assertTrue(held[0] == 20, "the mark lasts 20 ticks, saw " + held[0]);
            helper.assertTrue(w.exiles() == 1 && w.mark() == null, "one sent away and the circle gone");
        });
    }

    /**
     * The counter: in the arena, a pig steps 2.1 blocks out of the circle halfway through the mark. Nothing happens to it:
     * it keeps its spot and its health. The wraith is dragged into its own circle (its middle within 0.3 of the circle's)
     * and held there exactly 40 ticks without moving, 1.3 blocks of open space from the pig: in reach of a sword (3),
     * out of reach of its own claw's wind-up (0.8).
     */
    private static void counter(GameTestHelper helper) {
        ExileWraith w = wraith(helper, 2.5, 6.5);
        Pig pig = pig(helper, 6.5, 1, 6.5);
        Vec3 circle = pig.position();
        Vec3 stepped = helper.absoluteVec(new Vec3(6.5, 1, 8.6));
        int[] snag = {0};
        Vec3[] held = {null};
        boolean[] steppedOut = {false};
        helper.runAtTickTime(2, () -> helper.assertTrue(w.mark(pig), "the mark opens"));
        helper.onEachTick(() -> {
            if (!steppedOut[0] && w.core().in(Step.TELL) && w.core().elapsed() == 10) {
                steppedOut[0] = true;
                pig.snapTo(stepped.x, stepped.y, stepped.z);
            }
            if (w.snagged()) {
                if (held[0] == null) held[0] = w.position();
                snag[0]++;
                helper.assertTrue(w.position().distanceTo(held[0]) < 0.01, "it is held where it was dragged");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(steppedOut[0] && held[0] != null && !w.snagged(), "the pig stepped out, the wraith was snagged and let go");
            helper.assertTrue(snag[0] == 40, "the snag lasts 40 ticks, saw " + snag[0]);
            helper.assertTrue(w.exiles() == 0 && w.snags() == 1 && w.fizzles() == 0, "nobody sent away, one snag");
            helper.assertTrue(pig.position().distanceTo(stepped) < 0.01 && pig.getHealth() >= 100.0F, "the pig keeps its spot and health");
            helper.assertTrue(across(held[0], circle) < 0.3, "the wraith was dragged into its circle, " + across(held[0], circle) + " off");
            double gap = MobCore.gap(w, pig);
            helper.assertTrue(gap < 3.0 && gap > 0.8, "within a sword's reach of the pig, out of its claw's: " + gap);
        });
    }

    /**
     * Failing the counter: a survival player still on the circle when it fills, on an island of its own, is sent to the
     * only other land in reach, a pad 30 blocks east and level with it: 24 to 48 blocks across, standing on end stone with
     * open sky over it, and unhurt (all 20 health).
     */
    private static void failure(GameTestHelper helper) {
        Site site = new Site(helper, 2).floor(0, 0, 0, 5).floor(30, 0, 0, 2);
        ExileWraith w = site.wraith(-4.5, 0.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 1, 6.5));
        Vec3 start = site.at(0.5, 1, 0.5);
        p.snapTo(start.x, start.y, start.z);
        w.fixRoll(new Roll(30.0, 0.0));
        site.whenTicking(w, () -> helper.assertTrue(w.mark(p), "the mark opens under the player"));
        helper.succeedWhen(() -> {
            helper.assertTrue(w.exiles() == 1, "the player was sent away");
            double d = across(p.position(), start);
            helper.assertTrue(d >= 24.0 && d <= 48.0, "24 to 48 blocks away, " + d);
            helper.assertTrue(Math.abs(p.getY() - start.y) < 0.01 && Math.abs(p.getX() - (start.x + 30.0)) < 1.0, "on the pad, at " + p.position());
            helper.assertTrue(onOpenGround(site.level, p), "on solid ground under open sky");
            helper.assertTrue(p.getHealth() >= 20.0F, "unhurt, health " + p.getHealth());
        });
    }

    /** Radius 1: with a mark down under it, a pig moved 0.9 from the circle's middle is on it, and 1.1 out is off it. */
    private static void radius(GameTestHelper helper) {
        ExileWraith w = wraith(helper, 2.5, 6.5);
        Pig pig = pig(helper, 6.5, 1, 6.5);
        Vec3 c = pig.position();
        helper.runAtTickTime(2, () -> helper.assertTrue(w.mark(pig), "the mark opens"));
        helper.runAtTickTime(4, () -> pig.snapTo(c.x + 0.9, c.y, c.z));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(w.caught().contains(pig), "0.9 from the middle is on the circle");
            pig.snapTo(c.x, c.y, c.z + 1.1);
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(!w.caught().contains(pig), "1.1 from the middle is off it");
            w.discard();                                                     // the mark goes with it, unspent
            helper.succeed();
        });
    }

    /**
     * The roll and the search distances: a roll of 0 is 24 blocks, 1 is 48, a half 36; from 24 the search tries 24, 30,
     * 36, 42, 48, and from 36 it tries 36, 30, 42, 24, 48: never nearer than 24 or farther than 48.
     */
    private static void range(GameTestHelper helper) {
        helper.assertTrue(ExileWraith.exileDistance(0.0) == 24.0, "a roll of 0 is 24 blocks, " + ExileWraith.exileDistance(0.0));
        helper.assertTrue(ExileWraith.exileDistance(1.0) == 48.0, "a roll of 1 is 48 blocks, " + ExileWraith.exileDistance(1.0));
        helper.assertTrue(ExileWraith.exileDistance(0.5) == 36.0, "a roll of a half is 36, " + ExileWraith.exileDistance(0.5));
        helper.assertTrue(Arrays.equals(ExileWraith.distances(24.0), new double[] {24, 30, 36, 42, 48}),
                "from 24: " + Arrays.toString(ExileWraith.distances(24.0)));
        helper.assertTrue(Arrays.equals(ExileWraith.distances(36.0), new double[] {36, 30, 42, 24, 48}),
                "from 36: " + Arrays.toString(ExileWraith.distances(36.0)));
        helper.succeed();
    }

    /**
     * The search: rolled 24 blocks due east, where there is nothing, the pig still lands on the one pad in reach, 42
     * blocks off to the north-west of the roll's direction (135 degrees round) and 9 blocks lower.
     */
    private static void farLand(GameTestHelper helper) {
        double a = Math.toRadians(135.0);
        int px = (int) Math.floor(Math.cos(a) * 42.0 + 0.5), pz = (int) Math.floor(Math.sin(a) * 42.0 + 0.5);
        Site site = new Site(helper, 3).floor(0, 0, 0, 2).floor(-4, 0, 0, 1).floor(px, -9, pz, 2);
        ExileWraith w = site.wraith(-3.5, 0.5);
        Pig pig = site.pig(0.5, 1, 0.5);
        Vec3 start = pig.position();
        w.fixRoll(new Roll(24.0, 0.0));
        site.whenTicking(w, () -> helper.assertTrue(w.mark(pig), "the mark opens"));
        helper.succeedWhen(() -> {
            helper.assertTrue(w.exiles() == 1, "the pig was sent away");
            double d = across(pig.position(), start);
            helper.assertTrue(d >= 24.0 && d <= 48.0 && Math.abs(d - 42.0) < 1.5, "42 blocks away (24 to 48), " + d);
            helper.assertTrue(Math.abs(pig.getY() - (start.y - 9.0)) < 0.01, "9 blocks lower, at " + pig.position());
            helper.assertTrue(onOpenGround(site.level, pig), "on solid ground under open sky");
        });
    }

    /**
     * Never the void, never too near or far, never sealed in: a pig on a 3-block island whose only other land is a pad 15
     * blocks east (nearer than 24), a pad 60 blocks west (farther than 48) and a pad 30 blocks south under a roof (room
     * to stand, no sky; the roof is 8 thick, so its top lies higher than any search reaches) stays where it is when the
     * mark fills, unhurt; the mark fizzles and the wraith isn't snagged.
     */
    private static void noLand(GameTestHelper helper) {
        Site site = new Site(helper, 4).floor(0, 0, 0, 1).floor(15, 0, 0, 2).floor(-60, 0, 0, 2).floor(0, 0, 30, 2);
        for (int dy = 3; dy <= 10; dy++) site.floor(0, dy, 30, 2);
        ExileWraith w = site.wraith(-0.5, -0.5);
        Pig pig = site.pig(0.5, 1, 0.5);
        Vec3 start = pig.position();
        site.whenTicking(w, () -> helper.assertTrue(w.mark(pig), "the mark opens"));
        helper.succeedWhen(() -> {
            helper.assertTrue(w.marksCast() == 1 && !w.core().in(Step.TELL) && w.mark() == null, "the mark ran out");
            helper.assertTrue(w.exiles() == 0 && w.fizzles() == 1 && w.snags() == 0, "nothing sent away; it fizzled, no snag");
            helper.assertTrue(pig.position().distanceTo(start) < 0.01 && pig.getHealth() >= 100.0F, "the pig stays, unhurt, at " + pig.position());
        });
    }

    /**
     * Not twice within 200 ticks: a pig is sent to a pad, put back beside the wraith and set as its target, its own wait
     * cleared. For the next 199 ticks it never marks the pig, nor will a second wraith; at 200 ticks it marks it again by
     * itself.
     */
    private static void oncePer200(GameTestHelper helper) {
        Site site = new Site(helper, 5).floor(0, 0, 0, 5).floor(30, 0, 0, 2);
        ExileWraith w = site.wraith(-4.5, 0.5);
        ExileWraith other = site.wraith(-4.5, 3.5);
        Pig pig = site.pig(0.5, 1, 0.5);
        Vec3 start = pig.position();
        w.fixRoll(new Roll(30.0, 0.0));
        long[] sent = {-1};
        long[] again = {-1};
        long[] sentAt = {-1};
        site.whenTicking(w, () -> helper.assertTrue(w.mark(pig), "the first mark opens"));
        helper.onEachTick(() -> {
            long now = site.level.getGameTime();
            if (sent[0] < 0 && w.exiles() == 1) {
                sent[0] = now;
                sentAt[0] = helper.getTick();
                pig.snapTo(start.x, start.y, start.z);                       // back on the island, in reach and sight
                w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(16.0);
                w.setTarget(pig);
                w.setMarkCooldown(0);
            } else if (sent[0] >= 0 && again[0] < 0) {
                if (w.core().in(Step.TELL)) {
                    again[0] = now;
                } else if (now - sent[0] == 100) {
                    helper.assertTrue(!other.mark(pig), "no other wraith may mark it either, 100 ticks on");
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(again[0] >= 0, "it marked the pig again (sent on test tick " + sentAt[0] + ", " + (sent[0] < 0 ? "-" : site.level.getGameTime() - sent[0]) + " ticks ago; target " + (w.getTarget() == pig) + ", wait " + w.markCooldown() + ", idle " + w.core().idle() + ", snagged " + w.snagged() + ", " + w.distanceTo(pig) + " off, sight " + w.hasLineOfSight(pig) + ", lately sent " + ExileWraith.recentlyExiled(pig, site.level.getGameTime()) + ", ticked " + w.tickCount + ")");
            long wait = again[0] - sent[0];
            helper.assertTrue(wait >= 200 && wait <= 202, "not before 200 ticks had passed, marked again after " + wait);
            helper.assertTrue(w.marksCast() == 2 && other.marksCast() == 0, "two marks in all");
        });
    }

    // ---- the claw ------------------------------------------------------------------------------------------------------

    /**
     * The claw: a pig in reach is wound up at for exactly 8 ticks (it holds still and the pig is unhurt), then raked for
     * the attack of 4. The pig keeps its physics, and the rake leaves it where it stood: no knockback.
     */
    private static void claw(GameTestHelper helper) {
        ExileWraith w = wraith(helper, 5.5, 6.5);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(6.9, 1, 6.5));
        pig.removeFreeWill();
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        w.setTarget(pig);
        int[] windup = {0};
        Vec3[] before = {null};
        float[] after = {-1};
        helper.onEachTick(() -> {
            if (w.core().in(Step.WINDUP)) {
                windup[0]++;
                before[0] = pig.position();
                helper.assertTrue(pig.getHealth() >= 100.0F, "the wind-up hurts nothing yet");
            } else if (windup[0] > 0 && after[0] < 0) {
                after[0] = pig.getHealth();
                helper.assertTrue(w.core().in(Step.CLAW), "the wind-up ends in the rake, phase " + w.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(after[0] >= 0 && helper.getTick() > 0, "it wound up and raked");
            helper.assertTrue(windup[0] == 8, "the wind-up lasts 8 ticks, saw " + windup[0]);
            float lost = 100.0F - after[0];
            helper.assertTrue(Math.abs(lost - 4.0F) < 0.01F, "the rake takes 4, the pig lost " + lost);
            helper.assertTrue(w.claws() == 1 && !w.core().in(Step.CLAW), "one rake landed");
            helper.assertTrue(across(pig.position(), before[0]) < 0.05, "and threw the pig nowhere, moved " + across(pig.position(), before[0]));
        });
    }

    /** Armour counts: a pig in 10 armour loses 2.72 to the rake of 4 (4 x (1 - 8 / 25)). */
    private static void armour(GameTestHelper helper) {
        ExileWraith w = wraith(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 1, 6.5);
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        w.setTarget(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(w.claws() == 1, "it raked once");
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 2.72F) < 0.01F, "armour took its share, the pig lost " + lost);
        });
    }

    /**
     * Reach counts height: a pig on a pillar three blocks high right beside it (0.6 across, its feet 1.1 over the wraith's
     * head) is never wound up at in 60 ticks.
     */
    private static void pillar(GameTestHelper helper) {
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(6, y, 6), Blocks.STONE);
        ExileWraith w = wraith(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 4, 6.5);
        w.setTarget(pig);
        boolean[] wound = {false};
        helper.onEachTick(() -> wound[0] |= w.core().in(Step.WINDUP) || w.core().in(Step.CLAW));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!wound[0] && w.claws() == 0, "it never wound up at the pig on the pillar");
            helper.assertTrue(pig.getHealth() >= 100.0F, "which is unhurt");
            helper.succeed();
        });
    }

    // ---- death ---------------------------------------------------------------------------------------------------------

    /** A dead wraith sends no one away: killed 10 ticks into its mark, the pig on the circle stays put; no snag either. */
    private static void diesMidMark(GameTestHelper helper) {
        ExileWraith w = wraith(helper, 2.5, 6.5);
        Pig pig = pig(helper, 6.5, 1, 6.5);
        Vec3 start = pig.position();
        long[] died = {-1};
        helper.runAtTickTime(2, () -> helper.assertTrue(w.mark(pig), "the mark opens"));
        helper.onEachTick(() -> {
            if (died[0] < 0 && w.core().in(Step.TELL) && w.core().elapsed() == 10) {
                w.hurtServer(helper.getLevel(), w.damageSources().fellOutOfWorld(), 1000.0F);
                died[0] = helper.getTick();
            }
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(died[0] >= 0 && (w.isDeadOrDying() || w.isRemoved()), "it died in the mark");
            helper.assertTrue(w.mark() == null && w.exiles() == 0 && w.snags() == 0, "its circle went out with it: nobody sent, no snag");
            helper.assertTrue(pig.position().distanceTo(start) < 0.01, "the pig stays where it stood");
            helper.succeed();
        });
    }

    /** A dead wraith rakes no one: killed 4 ticks into its wind-up at a pig in reach, the pig is unhurt. */
    private static void diesMidClaw(GameTestHelper helper) {
        ExileWraith w = wraith(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 1, 6.5);
        w.setTarget(pig);
        long[] died = {-1};
        helper.onEachTick(() -> {
            if (died[0] < 0 && w.core().in(Step.WINDUP) && w.core().elapsed() == 4) {
                w.hurtServer(helper.getLevel(), w.damageSources().fellOutOfWorld(), 1000.0F);
                died[0] = helper.getTick();
            }
        });
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(died[0] >= 0 && (w.isDeadOrDying() || w.isRemoved()), "it died in the wind-up");
            helper.assertTrue(w.claws() == 0 && pig.getHealth() >= 100.0F, "no rake came after");
            helper.succeed();
        });
    }

    // ---- spawning, stats, home -----------------------------------------------------------------------------------------

    /** Its ground rule takes a spot on end stone and refuses one over thin air. */
    private static void spawnRule(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("exile_wraith");
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground spawn, got " + row.place());
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.END_STONE);
        helper.assertTrue(TeemingSpawns.placeOk(row, ModMobs.EXILE_WRAITH.get(), helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3))),
                "on end stone it may spawn");
        helper.assertTrue(!TeemingSpawns.placeOk(row, ModMobs.EXILE_WRAITH.get(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))),
                "over thin air it may not");
        helper.succeed();
    }

    /** Health 26, attack 4, armour 0, a 0.7 x 1.9 hitbox. */
    private static void stats(GameTestHelper helper) {
        ExileWraith w = wraith(helper, 5.5, 5.5);
        w.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(w.getMaxHealth() == 26.0F && w.getHealth() == 26.0F, "health 26, got " + w.getHealth() + "/" + w.getMaxHealth());
            helper.assertTrue(w.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4.0, "attack 4, got " + w.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(w.getArmorValue() == 0, "armour 0, got " + w.getArmorValue());
            helper.assertTrue(Math.abs(w.getBbWidth() - 0.7F) < 0.01F && Math.abs(w.getBbHeight() - 1.9F) < 0.01F,
                    "a 0.7 x 1.9 hitbox, got " + w.getBbWidth() + " x " + w.getBbHeight());
            helper.succeed();
        });
    }

    /**
     * Home: the small end islands and the End barrens only, spawn weight 4, alone; a monster; a 20-tick tell in its row;
     * the Dusk Wraith is a look; it drops rift essence.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("exile_wraith"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SMALL_END_ISLANDS)), "its home holds the small end islands");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "and the End barrens");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.END_MIDLANDS) || b.is(Biomes.THE_END)),
                "and nowhere else in the End");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/exile_wraith")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(x -> x.value().type() == ModMobs.EXILE_WRAITH.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.EXILE_WRAITH.get().getCategory() == MobCategory.MONSTER, "a monster");
        RosterStats.Entry row = RosterStats.get("exile_wraith");
        helper.assertTrue(row.tellTicks() == 20, "a 20-tick tell in its row");
        List<LookDef> looks = Looks.of("exile_wraith");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("dusk_wraith")), "the Dusk Wraith is a look: " + looks);
        helper.assertTrue(row.material().equals("rift_essence") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("rift_essence")),
                "it drops rift essence, a registered item");
        helper.succeed();
    }
}
