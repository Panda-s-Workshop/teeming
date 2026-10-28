package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.GeminiWatchers;
import com.panda.teeming.mob.end.GeminiWatchers.Beam;
import com.panda.teeming.mob.end.GeminiWatchers.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Gemini Watchers: an 8-tick staff wind-up (still, harmless) before a blow of 8.5 through armour, with no knockback, that
 * doesn't reach a pig 4 blocks up. A pair links at spawn through the spawn data, and a watcher from a spawn egg links to
 * the nearest unlinked one within 8 or comes alone. One killed falls as an untouchable husk and its twin channels a beam
 * for exactly 100 ticks, standing still and fighting nothing, the husk glowing a step brighter each quarter; then the
 * fallen one stands at exactly 15 of 30. Sight doesn't matter. Killing the channeling twin inside the window, even on its
 * last tick, ends both for good, and nothing happens after; a lone one, one whose twin is out of reach, one killed by a
 * kill command and one whose husk or channel is vetoed die for good. Save and load keep the link and a revive in
 * progress. Its stats, home and spawn rule. Every number is checked against a plain value.
 */
public final class GeminiWatchersTests {
    private GeminiWatchersTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("gemini_watchers_tell", GeminiWatchersTests::tell);
        out.accept("gemini_watchers_strike", GeminiWatchersTests::strike);
        out.accept("gemini_watchers_armour", GeminiWatchersTests::armour);
        out.accept("gemini_watchers_reach_height", GeminiWatchersTests::reachHeight);
        out.accept("gemini_watchers_dies_mid_tell", GeminiWatchersTests::diesMidTell);
        out.accept("gemini_watchers_pair_spawn", GeminiWatchersTests::pairSpawn);
        out.accept("gemini_watchers_pair_egg", GeminiWatchersTests::pairEgg);
        out.accept("gemini_watchers_failure", GeminiWatchersTests::failure);
        TeemingGameTests.timeLimit("gemini_watchers_failure", 160);
        out.accept("gemini_watchers_channel", GeminiWatchersTests::channel);
        TeemingGameTests.timeLimit("gemini_watchers_channel", 160);
        out.accept("gemini_watchers_sight", GeminiWatchersTests::sight);
        TeemingGameTests.timeLimit("gemini_watchers_sight", 160);
        out.accept("gemini_watchers_counter", GeminiWatchersTests::counter);
        TeemingGameTests.timeLimit("gemini_watchers_counter", 180);
        out.accept("gemini_watchers_window_edge", GeminiWatchersTests::windowEdge);
        TeemingGameTests.timeLimit("gemini_watchers_window_edge", 160);
        out.accept("gemini_watchers_nothing_after", GeminiWatchersTests::nothingAfter);
        TeemingGameTests.timeLimit("gemini_watchers_nothing_after", 200);
        out.accept("gemini_watchers_lone", GeminiWatchersTests::lone);
        out.accept("gemini_watchers_out_of_reach", GeminiWatchersTests::outOfReach);
        out.accept("gemini_watchers_kill_command", GeminiWatchersTests::killCommand);
        out.accept("gemini_watchers_veto", GeminiWatchersTests::veto);
        out.accept("gemini_watchers_twin_aggro", GeminiWatchersTests::twinAggro);
        out.accept("gemini_watchers_save_load", GeminiWatchersTests::saveLoad);
        TeemingGameTests.timeLimit("gemini_watchers_save_load", 160);
        out.accept("gemini_watchers_stats", GeminiWatchersTests::stats);
        out.accept("gemini_watchers_home", GeminiWatchersTests::home);
        out.accept("gemini_watchers_spawn_rule", GeminiWatchersTests::spawnRule);
    }

    // ---- fixtures --------------------------------------------------------------------------------------------------

    /**
     * A watcher with the base look, a follow range of 1 (it never notices survivors in other arenas), ready to strike,
     * facing +z; removed when the test ends.
     */
    private static GeminiWatchers watcher(GameTestHelper helper, double x, double z) {
        GeminiWatchers w = helper.spawn(ModMobs.GEMINI_WATCHERS.get(), new Vec3(x, 1, z));
        w.core().setLook("base", true);
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        w.resetSwing();
        face(w, 0.0F);
        TestPlayers.whenDone(helper, w::discard);
        return w;
    }

    /** One that stays exactly where it is put: no AI (its own tick still runs the husk and the beam). */
    private static GeminiWatchers still(GameTestHelper helper, double x, double z) {
        GeminiWatchers w = watcher(helper, x, z);
        w.setNoAi(true);
        return w;
    }

    /** Two still watchers linked as a pair: the first at (ax, az), its twin at (bx, bz). */
    private static GeminiWatchers[] pair(GameTestHelper helper, double ax, double az, double bx, double bz) {
        GeminiWatchers a = still(helper, ax, az), b = still(helper, bx, bz);
        GeminiWatchers.pair(a, b);
        return new GeminiWatchers[] {a, b};
    }

    private static void face(GeminiWatchers w, float yaw) {
        w.setYRot(yaw);
        w.setYBodyRot(yaw);
        w.setYHeadRot(yaw);
    }

    /** A still pig with 100 health at (x, y, z), removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** Magic damage (armour doesn't count) with the hurt cooldown cleared first, so every hit lands in full. */
    private static boolean hit(GameTestHelper helper, GeminiWatchers w, float amount) {
        w.damageCooldownTime = 0;
        return w.hurtServer(helper.getLevel(), w.damageSources().magic(), amount);
    }

    /** A killing blow: far more than its health, as magic. */
    private static void kill(GameTestHelper helper, GeminiWatchers w) {
        hit(helper, w, 1000.0F);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    private static boolean dead(GeminiWatchers w) {
        return w.isRemoved() || w.isDeadOrDying();
    }

    // ---- the staff blow --------------------------------------------------------------------------------------------

    /** The wind-up lasts exactly 8 ticks, standing still and hurting nothing, and ends in the strike. */
    private static void tell(GameTestHelper helper) {
        GeminiWatchers w = watcher(helper, 5.5, 4.5);
        Pig pig = pig(helper, 5.5, 1, 5.6);
        w.setTarget(pig);
        long[] start = {-1}, strike = {-1};
        helper.onEachTick(() -> {
            if (w.core().in(Step.TELL)) {
                if (start[0] < 0) start[0] = helper.getTick();
                helper.assertTrue(w.getDeltaMovement().horizontalDistance() < 0.05, "it stands still while it winds up");
                helper.assertTrue(pig.getHealth() >= 100.0F, "the wind-up hurts nothing");
            } else if (start[0] >= 0 && strike[0] < 0) {
                strike[0] = helper.getTick();
                helper.assertTrue(w.core().in(Step.STRIKE), "the wind-up ends in the strike, phase " + w.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && strike[0] >= 0, "a wind-up and then a strike");
            helper.assertTrue(strike[0] - start[0] == 8, "the wind-up lasts 8 ticks, lasted " + (strike[0] - start[0]));
        });
    }

    /** The staff hits a pig 1 block in front for exactly 8.5 and doesn't move it; a pig behind is untouched. */
    private static void strike(GameTestHelper helper) {
        GeminiWatchers w = still(helper, 5.5, 4.5);
        Pig front = pig(helper, 5.5, 1, 5.5);
        Pig behind = pig(helper, 5.5, 1, 3.4);
        Vec3[] moved = {null};
        helper.runAtTickTime(2, () -> helper.assertTrue(w.tell(front), "it winds up"));
        helper.onEachTick(() -> {
            if (moved[0] == null && front.getHealth() < 100.0F) moved[0] = front.getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(moved[0] != null, "the strike landed");
            float lost = 100.0F - front.getHealth();
            helper.assertTrue(Math.abs(lost - 8.5F) < 0.01F, "the staff hits for 8.5, the pig lost " + lost);
            helper.assertTrue(moved[0].horizontalDistance() < 0.01, "and doesn't knock it back, motion " + moved[0]);
            helper.assertTrue(behind.getHealth() >= 100.0F, "nothing behind it is hit");
            helper.assertTrue(w.strikes() == 1, "one blow landed, " + w.strikes());
        });
    }

    /** Armour counts: a pig with 10 armour takes 8.5 x (1 - (10 - 8.5/2) / 25) = 6.545 from the blow, vanilla's armour sum. */
    private static void armour(GameTestHelper helper) {
        GeminiWatchers w = still(helper, 5.5, 4.5);
        Pig pig = pig(helper, 5.5, 1, 5.5);
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        helper.runAtTickTime(2, () -> helper.assertTrue(w.tell(pig), "it winds up"));
        helper.succeedWhen(() -> {
            helper.assertTrue(w.strikes() == 1, "the blow landed");
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 6.545F) < 0.01F, "armour 10 takes the 8.5 down to 6.545, lost " + lost);
        });
    }

    /** Reach counts height: beside a pig whose feet are 4 blocks up (2 over its head) the blow lands nothing; 2 up, its 8.5. */
    private static void reachHeight(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(3, y, 5), Blocks.STONE);
        for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(8, y, 5), Blocks.STONE);
        GeminiWatchers high = still(helper, 3.5, 4.5);
        GeminiWatchers low = still(helper, 8.5, 4.5);
        Pig up4 = pig(helper, 3.5, 5, 5.5);
        Pig up2 = pig(helper, 8.5, 3, 5.5);
        helper.runAtTickTime(2, () -> helper.assertTrue(high.tell(up4) && low.tell(up2), "both wind up"));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(up4.getHealth() >= 100.0F, "a pig 4 blocks up is out of reach, health " + up4.getHealth());
            float lost = 100.0F - up2.getHealth();
            helper.assertTrue(Math.abs(lost - 8.5F) < 0.01F, "a pig 2 blocks up is hit for 8.5, lost " + lost);
            helper.succeed();
        });
    }

    /** Nothing after death: a lone watcher killed 4 ticks into its wind-up stays dead and the pig in reach is never struck. */
    private static void diesMidTell(GameTestHelper helper) {
        GeminiWatchers w = still(helper, 5.5, 4.5);
        Pig pig = pig(helper, 5.5, 1, 5.5);
        helper.runAtTickTime(2, () -> helper.assertTrue(w.tell(pig), "it winds up at the pig"));
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(w.core().in(Step.TELL), "4 ticks into its wind-up");
            kill(helper, w);
            helper.assertTrue(w.isDeadOrDying() && !w.husk(), "dead, no husk");
        });
        helper.onEachTick(() -> {
            if (helper.getTick() > 6) helper.assertTrue(pig.getHealth() >= 100.0F, "a dead watcher strikes nothing");
        });
        helper.runAtTickTime(36, () -> {
            helper.assertTrue(w.strikes() == 0 && dead(w), "it stayed dead");
            helper.succeed();
        });
    }

    // ---- the pair --------------------------------------------------------------------------------------------------

    /**
     * A natural spawn group is a pair: the first hands itself on in the spawn data and the second links to it, both ways,
     * in the first's look (set to the rare Dusk Pair) whatever the second rolled; the first's eye is on its left, the second's on its right. A third
     * handed the same data finds the pair full and stays alone.
     */
    private static void pairSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GeminiWatchers[] made = new GeminiWatchers[3];
        SpawnGroupData data = null;
        for (int i = 0; i < 3; i++) {
            GeminiWatchers w = ModMobs.GEMINI_WATCHERS.get().create(level, EntitySpawnReason.NATURAL);
            Vec3 at = helper.absoluteVec(new Vec3(3.5 + i * 2.5, 1, 5.5));
            w.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            data = w.finalizeSpawn(level, level.getCurrentDifficultyAt(w.blockPosition()), EntitySpawnReason.NATURAL, data);
            if (i == 0) w.core().setLook("dusk_pair", true);       // the rare look, so a copy can't pass by chance
            w.setNoAi(true);
            level.addFreshEntity(w);
            w.setPersistenceRequired();                        // a natural spawn despawns while other tests' players are over 128 blocks off
            TestPlayers.whenDone(helper, w::discard);
            made[i] = w;
        }
        helper.runAtTickTime(2, () -> {
            GeminiWatchers first = made[0], second = made[1], third = made[2];
            helper.assertTrue(first.twinOf(level) == second && second.twinOf(level) == first, "the first two are linked both ways");
            helper.assertTrue(second.look().equals("dusk_pair"), "the second wears the first's look, dusk_pair, got " + second.look());
            helper.assertTrue(!first.mirrored() && second.mirrored(), "the first's eye on its left, the second's on its right");
            helper.assertTrue(!third.linked() && third.twinOf(level) == null, "a third finds the pair full and stays alone");
            helper.succeed();
        });
    }

    /**
     * A watcher from a spawn egg links to the nearest unlinked watcher within 8 blocks (7 away here; a linked pair nearer
     * is passed over), takes its look and the other side; one from an egg with no unlinked watcher within 8 comes alone.
     */
    private static void pairEgg(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GeminiWatchers lone = still(helper, 1.5, 5.5);
        lone.core().setLook("dusk_pair", true);
        GeminiWatchers[] linked = pair(helper, 9.5, 7.5, 10.5, 8.5);
        GeminiWatchers egg = fromEgg(helper, 8.5, 5.5);
        GeminiWatchers far = fromEgg(helper, 10.5, 1.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(egg.twinOf(level) == lone && lone.twinOf(level) == egg, "the egg's watcher links to the unlinked one 7 blocks off");
            helper.assertTrue(linked[0].twinOf(level) == linked[1] && linked[1].twinOf(level) == linked[0], "the nearer pair keeps its link");
            helper.assertTrue(egg.look().equals("dusk_pair"), "it takes its partner's look, got " + egg.look());
            helper.assertTrue(egg.mirrored() != lone.mirrored(), "and the other side");
            helper.assertTrue(lone.position().distanceTo(far.position()) > 8.0, "the last egg is over 8 blocks from the only other unlinked one");
            helper.assertTrue(!far.linked(), "with no unlinked watcher within 8 it comes alone");
            helper.succeed();
        });
    }

    /** A watcher placed as a spawn egg places it: created, set down at (x, 1, z) and finished for a spawn-item use. */
    private static GeminiWatchers fromEgg(GameTestHelper helper, double x, double z) {
        ServerLevel level = helper.getLevel();
        GeminiWatchers w = ModMobs.GEMINI_WATCHERS.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        Vec3 at = helper.absoluteVec(new Vec3(x, 1, z));
        w.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        w.finalizeSpawn(level, level.getCurrentDifficultyAt(w.blockPosition()), EntitySpawnReason.SPAWN_ITEM_USE, null);
        w.setNoAi(true);
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        level.addFreshEntity(w);
        TestPlayers.whenDone(helper, w::discard);
        return w;
    }

    // ---- the revive: failing the counter -----------------------------------------------------------------------------

    /**
     * Failing the counter: one killed falls as a husk (alive at 1 health, a 1.2 x 0.45 hitbox, nothing can hurt or target
     * it) while its twin channels; 99 ticks on it still lies there; exactly 100 ticks after the kill it stands again with
     * 15 of its 30 health, rising, and its twin is free again.
     */
    private static void failure(GameTestHelper helper) {
        GeminiWatchers[] p = pair(helper, 3.5, 5.5, 7.5, 5.5);
        GeminiWatchers a = p[0], b = p[1];
        long[] killed = {-1}, stood = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 10) {
                kill(helper, a);
                killed[0] = tick;
                helper.assertTrue(a.isAlive() && !a.isDeadOrDying() && a.husk(), "it fell as a husk, not dead");
                helper.assertTrue(near(a.getHealth(), 1.0), "a husk has 1 health, got " + a.getHealth());
                helper.assertTrue(Math.abs(a.getBbWidth() - 1.2F) < 0.01F && Math.abs(a.getBbHeight() - 0.45F) < 0.01F,
                        "a husk lies in a 1.2 x 0.45 hitbox, got " + a.getBbWidth() + " x " + a.getBbHeight());
                helper.assertTrue(!hit(helper, a, 5.0F) && near(a.getHealth(), 1.0), "a husk can't be hurt");
                helper.assertTrue(!a.isPickable() && !a.isAttackable() && !a.canBeSeenAsEnemy(), "and can't be aimed at or targeted");
                helper.assertTrue(b.core().in(Step.CHANNEL) && b.reviving(), "its twin channels, phase " + b.phase());
                return;
            }
            if (killed[0] < 0) return;
            if (tick == killed[0] + 99) helper.assertTrue(a.husk() && b.core().in(Step.CHANNEL), "99 ticks on it still lies there");
            if (stood[0] < 0 && !a.husk()) {
                stood[0] = tick;
                helper.assertTrue(tick - killed[0] == 100, "it stands again 100 ticks after the kill, took " + (tick - killed[0]));
                helper.assertTrue(a.isAlive() && near(a.getHealth(), 15.0), "with half of 30, 15 health, got " + a.getHealth());
                helper.assertTrue(near(a.getMaxHealth(), 30.0), "its most is still 30");
                helper.assertTrue(a.core().in(Step.RISE) && a.revives() == 1, "rising, phase " + a.phase());
                helper.assertTrue(Math.abs(a.getBbHeight() - 2.0F) < 0.01F, "standing 2 blocks tall again");
                helper.assertTrue(!b.reviving() && !b.core().in(Step.CHANNEL), "its twin's beam is done");
                helper.succeed();
            }
        });
    }

    /**
     * The tell: for its 100 ticks the twin stands still facing the husk, the beam drawn to it (the flags hold the way to
     * the husk), and fights nothing: a pig in reach that it is set on every tick is never struck. The husk glows dark for
     * the first quarter, then a step brighter each quarter (stages 1, 2, 3, 4).
     */
    private static void channel(GameTestHelper helper) {
        GeminiWatchers a = still(helper, 3.5, 4.5);
        GeminiWatchers b = watcher(helper, 7.5, 6.5);
        GeminiWatchers.pair(a, b);
        Pig pig = pig(helper, 7.5, 1, 7.6);
        long[] killed = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 10) {
                kill(helper, a);
                killed[0] = tick;
                return;
            }
            if (killed[0] < 0) return;
            long in = tick - killed[0];
            if (in < 100) {
                b.setTarget(pig);
                helper.assertTrue(b.core().in(Step.CHANNEL), "the beam runs, " + in + " ticks in");
                helper.assertTrue(b.getDeltaMovement().horizontalDistance() < 0.05, "the twin stands still");
                helper.assertTrue(pig.getHealth() >= 100.0F, "and fights nothing while it channels");
                helper.assertTrue(b.core().flag(GeminiWatchers.FLAG_BEAM), "the beam is drawn");
                int flags = b.core().flags();
                Vec3 to = a.position().subtract(b.position());
                helper.assertTrue(Math.abs(Beam.x(flags) - to.x) <= 0.125 && Math.abs(Beam.z(flags) - to.z) <= 0.125,
                        "the beam reaches the husk, flags " + Beam.x(flags) + ", " + Beam.z(flags) + " for " + to);
                float toward = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
                helper.assertTrue(Mth.degreesDifferenceAbs(toward, b.getYRot()) < 1.0F, "it faces the husk");
                int want = in < 25 ? 1 : in < 50 ? 2 : in < 75 ? 3 : 4;
                if (in > 0) helper.assertTrue(a.core().stage() == want, "the husk glows at stage " + want + " " + in + " ticks in, got " + a.core().stage());
            } else if (in == 100) {
                helper.assertTrue(!b.core().in(Step.CHANNEL) && !b.core().flag(GeminiWatchers.FLAG_BEAM), "the beam ends after 100 ticks");
                helper.assertTrue(!a.husk() && a.core().stage() == GeminiWatchers.STAGE_STANDING, "and the husk stands, its eye lit");
                helper.succeed();
            }
        });
    }

    /** Sight doesn't matter: a stone wall 3 high between the twin and the husk changes nothing; it stands after 100 ticks. */
    private static void sight(GameTestHelper helper) {
        for (int x = 3; x <= 8; x++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
        GeminiWatchers[] p = pair(helper, 5.5, 3.5, 5.5, 7.5);
        GeminiWatchers a = p[0], b = p[1];
        long[] killed = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 10) {
                helper.assertTrue(!b.hasLineOfSight(a), "the wall hides them from each other");
                kill(helper, a);
                killed[0] = tick;
                helper.assertTrue(a.husk() && b.core().in(Step.CHANNEL), "it fell and its twin channels through the wall");
            } else if (killed[0] >= 0 && tick == killed[0] + 100) {
                helper.assertTrue(!a.husk() && a.isAlive() && near(a.getHealth(), 15.0), "it stands again behind the wall, health " + a.getHealth());
                helper.succeed();
            }
        });
    }

    // ---- the counter -------------------------------------------------------------------------------------------------

    /**
     * The counter: the twin killed 60 ticks into its beam dies for good, the husk crumbles with it, and 120 ticks after the
     * first kill neither has stood again.
     */
    private static void counter(GameTestHelper helper) {
        GeminiWatchers[] p = pair(helper, 3.5, 5.5, 7.5, 5.5);
        GeminiWatchers a = p[0], b = p[1];
        helper.runAtTickTime(10, () -> {
            kill(helper, a);
            helper.assertTrue(a.husk() && b.core().in(Step.CHANNEL), "the first falls as a husk, its twin channels");
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(a.husk() && b.reviving(), "60 ticks into the beam");
            kill(helper, b);
            helper.assertTrue(b.isDeadOrDying() && !b.husk(), "the channeling twin dies for good");
            helper.assertTrue(a.isDeadOrDying(), "and the husk crumbles with it");
            helper.assertTrue(!b.reviving() && !b.core().flag(GeminiWatchers.FLAG_BEAM), "the beam is gone");
        });
        helper.runAtTickTime(130, () -> {
            helper.assertTrue(dead(a) && dead(b), "both stay dead");
            helper.assertTrue(a.revives() == 0, "nothing stood again");
            helper.succeed();
        });
    }

    /** Both dead in the window stay dead, to its last tick: the twin killed 99 ticks into its beam takes the husk with it. */
    private static void windowEdge(GameTestHelper helper) {
        GeminiWatchers[] p = pair(helper, 3.5, 5.5, 7.5, 5.5);
        GeminiWatchers a = p[0], b = p[1];
        long[] killed = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 10) {
                kill(helper, a);
                killed[0] = tick;
            } else if (killed[0] >= 0 && tick == killed[0] + 99) {
                helper.assertTrue(a.husk() && b.core().in(Step.CHANNEL), "99 ticks in, the beam still runs");
                kill(helper, b);
                helper.assertTrue(a.isDeadOrDying() && b.isDeadOrDying(), "a kill on the beam's last tick ends both");
            } else if (killed[0] >= 0 && tick == killed[0] + 130) {
                helper.assertTrue(dead(a) && dead(b) && a.revives() == 0, "both stay dead");
                helper.succeed();
            }
        });
    }

    /**
     * Nothing after both deaths: for 120 ticks after a pair is killed together neither stands, no beam or phase starts,
     * the pig beside them is never struck, and both are gone once their deaths play out.
     */
    private static void nothingAfter(GameTestHelper helper) {
        GeminiWatchers[] p = pair(helper, 3.5, 5.5, 7.5, 5.5);
        GeminiWatchers a = p[0], b = p[1];
        Pig pig = pig(helper, 7.5, 1, 6.6);
        long[] both = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 10) kill(helper, a);
            if (tick == 30) {
                kill(helper, b);
                both[0] = tick;
                helper.assertTrue(a.isDeadOrDying() && b.isDeadOrDying(), "the pair is killed together");
            }
            if (both[0] < 0 || tick <= both[0]) return;
            for (GeminiWatchers w : p) {
                helper.assertTrue(!w.isAlive(), "neither stands again");
                helper.assertTrue(w.isRemoved() || w.core().idle(), "no phase starts, phase " + w.phase());
                helper.assertTrue(!w.reviving() && !w.core().flag(GeminiWatchers.FLAG_BEAM), "no beam");
            }
            helper.assertTrue(pig.getHealth() >= 100.0F, "nothing is struck");
            if (tick == both[0] + 120) {
                helper.assertTrue(a.isRemoved() && b.isRemoved(), "both are gone");
                helper.succeed();
            }
        });
    }

    /** A watcher with no twin dies for good when killed. */
    private static void lone(GameTestHelper helper) {
        GeminiWatchers w = still(helper, 5.5, 5.5);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(!w.linked(), "it has no twin");
            kill(helper, w);
            helper.assertTrue(w.isDeadOrDying() && !w.husk(), "a lone watcher's death is final");
            helper.succeed();
        });
    }

    /** A twin more than 12 blocks off (15.6 here, corner to corner) can't reach: the kill is final and it doesn't channel. */
    private static void outOfReach(GameTestHelper helper) {
        GeminiWatchers[] p = pair(helper, 0.5, 0.5, 11.5, 11.5);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(p[0].distanceTo(p[1]) > 12.0, "over 12 blocks apart");
            kill(helper, p[0]);
            helper.assertTrue(p[0].isDeadOrDying() && !p[0].husk(), "the kill is final");
            helper.assertTrue(!p[1].reviving() && p[1].core().idle(), "the twin doesn't channel");
            helper.succeed();
        });
    }

    /** A kill that ignores invulnerability (a kill command) is final, with its twin standing right beside it. */
    private static void killCommand(GameTestHelper helper) {
        GeminiWatchers[] p = pair(helper, 4.5, 5.5, 6.5, 5.5);
        helper.runAtTickTime(5, () -> {
            p[0].kill(helper.getLevel());
            helper.assertTrue(p[0].isDeadOrDying() && !p[0].husk(), "a kill command is final");
            helper.assertTrue(!p[1].reviving(), "its twin doesn't channel");
            helper.succeed();
        });
    }

    /** A listener that cancels the "channel" step, or the "husk" step, makes the kill final. */
    private static void veto(GameTestHelper helper) {
        GeminiWatchers[] channel = pair(helper, 2.5, 3.5, 4.5, 3.5);
        GeminiWatchers[] husk = pair(helper, 7.5, 7.5, 9.5, 7.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == channel[1] && e.step().equals("channel")) e.cancel();
            if (e.mob() == husk[0] && e.step().equals("husk")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(5, () -> {
            kill(helper, channel[0]);
            helper.assertTrue(channel[0].isDeadOrDying() && !channel[0].husk() && !channel[1].reviving(), "a vetoed channel makes the kill final");
            kill(helper, husk[0]);
            helper.assertTrue(husk[0].isDeadOrDying() && !husk[0].husk() && !husk[1].reviving(), "a vetoed husk makes the kill final");
            helper.succeed();
        });
    }

    /** A blow to one turns its twin on the attacker too. */
    private static void twinAggro(GameTestHelper helper) {
        GeminiWatchers[] p = pair(helper, 4.5, 5.5, 6.5, 5.5);
        Pig pig = pig(helper, 5.5, 1, 8.5);
        helper.runAtTickTime(5, () -> {
            p[0].damageCooldownTime = 0;
            p[0].hurtServer(helper.getLevel(), p[0].damageSources().mobAttack(pig), 2.0F);
            helper.assertTrue(p[1].getTarget() == pig, "its twin turns on the attacker");
            helper.succeed();
        });
    }

    // ---- saving ----------------------------------------------------------------------------------------------------

    /**
     * Save and load: a pair saved 30 ticks into a revive, and loaded, find each other again by id; the husk is still a
     * husk at 1 health and lying, its twin still channels and keeps its side, and the fallen one stands exactly 100 ticks
     * after the kill, as if nothing had happened.
     */
    private static void saveLoad(GameTestHelper helper) {
        GeminiWatchers[] p = pair(helper, 3.5, 5.5, 7.5, 5.5);
        GeminiWatchers[] loaded = {null, null};
        long[] killed = {-1}, stood = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            ServerLevel level = helper.getLevel();
            if (tick == 10) {
                kill(helper, p[0]);
                killed[0] = tick;
                helper.assertTrue(p[0].husk() && p[1].reviving(), "a revive is under way");
            } else if (tick == 40) {
                CompoundTag husk = save(level, p[0]), twin = save(level, p[1]);
                p[0].discard();
                p[1].discard();
                loaded[0] = load(helper, husk);
                loaded[1] = load(helper, twin);
            } else if (tick == 41) {
                GeminiWatchers h = loaded[0], t = loaded[1];
                helper.assertTrue(h.twinOf(level) == t && t.twinOf(level) == h, "they find each other again");
                helper.assertTrue(h.husk() && near(h.getHealth(), 1.0) && Math.abs(h.getBbHeight() - 0.45F) < 0.01F, "the husk loads as a husk");
                helper.assertTrue(t.reviving() && t.core().in(Step.CHANNEL), "its twin still channels");
                helper.assertTrue(!h.mirrored() && t.mirrored(), "each keeps its side");
            } else if (tick > 41 && stood[0] < 0 && !loaded[0].husk()) {
                stood[0] = tick;
                helper.assertTrue(tick - killed[0] == 100, "it stands 100 ticks after the kill, took " + (tick - killed[0]));
                helper.assertTrue(near(loaded[0].getHealth(), 15.0), "with 15 health, got " + loaded[0].getHealth());
                helper.succeed();
            }
        });
    }

    private static CompoundTag save(ServerLevel level, GeminiWatchers w) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        w.saveWithoutId(out);
        return out.buildResult();
    }

    private static GeminiWatchers load(GameTestHelper helper, CompoundTag tag) {
        ServerLevel level = helper.getLevel();
        GeminiWatchers w = ModMobs.GEMINI_WATCHERS.get().create(level, EntitySpawnReason.LOAD);
        w.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        helper.assertTrue(level.addFreshEntity(w), "the loaded one is back in the level");
        TestPlayers.whenDone(helper, w::discard);
        return w;
    }

    // ---- the row ---------------------------------------------------------------------------------------------------

    /** Health 30, attack 8.5, armour 4, a 0.7 x 2.0 hitbox, 12 experience. */
    private static void stats(GameTestHelper helper) {
        GeminiWatchers w = still(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(w.getMaxHealth() == 30.0F && w.getHealth() == 30.0F, "health 30, got " + w.getHealth() + "/" + w.getMaxHealth());
            helper.assertTrue(w.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8.5, "attack 8.5, got " + w.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(w.getArmorValue() == 4, "armour 4, got " + w.getArmorValue());
            helper.assertTrue(Math.abs(w.getBbWidth() - 0.7F) < 0.01F && Math.abs(w.getBbHeight() - 2.0F) < 0.01F,
                    "a 0.7 x 2.0 hitbox, got " + w.getBbWidth() + " x " + w.getBbHeight());
            helper.assertTrue(w.getExperienceReward(helper.getLevel(), null) == 12, "12 experience");
            helper.succeed();
        });
    }

    /** Home: the End barrens (not the highlands, midlands or main island), weight 3, in pairs of 2, a monster; the Dusk Pair; void crystal. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("gemini_watchers"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "its home holds the End barrens");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.END_MIDLANDS) || b.is(Biomes.THE_END)),
                "never the highlands, midlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/gemini_watchers")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.GEMINI_WATCHERS.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 3, "spawn weight 3, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 2 && entry.value().count().maxInclusive() == 2,
                "a pair, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.GEMINI_WATCHERS.get().getCategory() == MobCategory.MONSTER, "a monster");
        RosterStats.Entry row = RosterStats.get("gemini_watchers");
        helper.assertTrue(row.secondLook().equals("dusk_pair"), "the Dusk Pair is its second look");
        helper.assertTrue(row.material().equals("void_crystal"), "it drops void crystal");
        helper.succeed();
    }

    /** Its spawn rule (on the ground) takes a spot on end stone and refuses one with nothing under it. */
    private static void spawnRule(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.END_STONE);
        RosterStats.Entry row = RosterStats.get("gemini_watchers");
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground mob, place " + row.place());
        helper.assertTrue(TeemingSpawns.placeOk(row, ModMobs.GEMINI_WATCHERS.get(), helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))),
                "a spot on end stone takes a spawn");
        helper.assertTrue(!TeemingSpawns.placeOk(row, ModMobs.GEMINI_WATCHERS.get(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))),
                "a spot with nothing under it does not");
        helper.succeed();
    }
}
