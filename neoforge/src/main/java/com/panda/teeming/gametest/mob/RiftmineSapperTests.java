package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.RiftmineSapper;
import com.panda.teeming.mob.end.RiftmineSapper.Step;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Riftmine Sapper: with a target in reach it sows a mine at the target's feet, then one every 100 ticks, never more than
 * 4 at once (the next only once one is spent); a mine arms over exactly 40 ticks and is harmless until then; armed, a
 * pig or a player within 1.5 of it (not 1.6) sets it off and is launched straight up at 1.35 a tick, a rise of about 10
 * blocks (9.8 to 10.4) and a fall that costs 7; an arrow sets an armed mine off from range and knocks out one still
 * arming; its own mine never goes off under it, but shot while it stands in it throws it 10 blocks up (7 from the fall)
 * and its other mines fizzle; killed, its mines fizzle at once, set nothing off and are gone within 40 ticks; a reload
 * keeps them, their ages and their arming. Over the void it sows nothing. Its jab: a 10-tick tell, then 4 through armour
 * with no knockback, out of reach of a target on a pillar, and nothing from a sapper killed mid-tell. Every number is
 * checked against a plain value.
 *
 * <p>Each sapper has no goals and a follow range of 1 (it notices nothing past its arena); its AI step, phases and mines
 * run. It launches nothing outside its own arena.
 */
public final class RiftmineSapperTests {
    private RiftmineSapperTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("riftmine_sapper_tell", RiftmineSapperTests::tell);
        out.accept("riftmine_sapper_counter", RiftmineSapperTests::counter);
        out.accept("riftmine_sapper_failure", RiftmineSapperTests::failure);
        TeemingGameTests.timeLimit("riftmine_sapper_failure", 120);
        out.accept("riftmine_sapper_player", RiftmineSapperTests::player);
        out.accept("riftmine_sapper_every_100", RiftmineSapperTests::every);
        TeemingGameTests.timeLimit("riftmine_sapper_every_100", 160);
        out.accept("riftmine_sapper_max_4", RiftmineSapperTests::max4);
        out.accept("riftmine_sapper_radius", RiftmineSapperTests::radius);
        out.accept("riftmine_sapper_turned", RiftmineSapperTests::turned);
        TeemingGameTests.timeLimit("riftmine_sapper_turned", 120);
        out.accept("riftmine_sapper_fizzle_on_death", RiftmineSapperTests::fizzleOnDeath);
        out.accept("riftmine_sapper_save_load", RiftmineSapperTests::saveLoad);
        out.accept("riftmine_sapper_ground", RiftmineSapperTests::ground);
        out.accept("riftmine_sapper_jab_tell", RiftmineSapperTests::jabTell);
        out.accept("riftmine_sapper_jab", RiftmineSapperTests::jab);
        out.accept("riftmine_sapper_pillar", RiftmineSapperTests::pillar);
        out.accept("riftmine_sapper_dies_mid_tell", RiftmineSapperTests::diesMidTell);
        out.accept("riftmine_sapper_stats", RiftmineSapperTests::stats);
        out.accept("riftmine_sapper_home", RiftmineSapperTests::home);
        out.accept("riftmine_sapper_spawn_rule", RiftmineSapperTests::spawnRule);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /**
     * A sapper with the base look, no goals and a follow range of 1, removed when the test ends; it launches nothing
     * outside the arena.
     */
    private static RiftmineSapper sapper(GameTestHelper helper, Vec3 at) {
        RiftmineSapper s = helper.spawn(ModMobs.RIFTMINE_SAPPER.get(), at);
        s.core().setLook("base", true);
        s.removeFreeWill();
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == s && e.target() != null && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        TestPlayers.whenDone(helper, s::discard);
        return s;
    }

    /** Vetoes {@code step} for {@code s} for the rest of the test. */
    private static void veto(GameTestHelper helper, RiftmineSapper s, String step) {
        Consumer<MechanicEvent> v = e -> {
            if (e.mob() == s && e.step().equals(step)) e.cancel();
        };
        MechanicEvent.listen(v);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(v));
    }

    /** A pig with 100 health and no goals (its physics still run, so a launch moves it), removed when the test ends. */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.removeFreeWill();
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** An arrow at {@code at} with no owner, flying at {@code motion}; removed when the test ends. */
    private static Arrow arrow(GameTestHelper helper, Vec3 at, Vec3 motion, boolean gravity) {
        Vec3 abs = helper.absoluteVec(at);
        Arrow arrow = new Arrow(helper.getLevel(), abs.x, abs.y, abs.z, new ItemStack(Items.ARROW), null);
        arrow.setNoGravity(!gravity);
        arrow.setDeltaMovement(motion);
        helper.getLevel().addFreshEntity(arrow);
        TestPlayers.whenDone(helper, arrow::discard);
        return arrow;
    }

    /** Clears the barrier roof over the arena (the two layers above its 8 rows), so a launch rises its full height. */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        for (int y = 2; y <= 16; y++) {
            BlockPos pos = new BlockPos(5, y, 5);
            if (!helper.getBlockState(pos).isAir()) helper.fail("the sky over the arena is shut at y " + y + " by " + helper.getBlockState(pos));
        }
    }

    private static Vec3 abs(GameTestHelper helper, double x, double y, double z) {
        return helper.absoluteVec(new Vec3(x, y, z));
    }

    private static boolean near(double value, double expected) {
        return Math.abs(value - expected) < 0.01;
    }

    private static boolean same(Vec3 a, Vec3 b) {
        return a.distanceTo(b) < 0.001;
    }

    // ---- the mines -----------------------------------------------------------------------------------------------

    /**
     * The tell: with a pig 5 blocks off as its target, it sows a mine at the pig's feet. The mine arms over exactly 40
     * ticks, its age counting up one a tick; the pig stands on it all that time unharmed and unmoved; on the 40th tick
     * it is launched.
     */
    private static void tell(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(3.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(8.5, 1, 5.5));
        s.setPlantClock(0);
        s.setTarget(pig);
        long[] sown = {-1}, launched = {-1};
        double[] off = {-1.0};
        helper.onEachTick(() -> {
            if (sown[0] < 0 && s.plants() == 1) {
                sown[0] = helper.getTick();
                Vec3 m = s.mines().getFirst();
                off[0] = Math.hypot(m.x - pig.getX(), m.z - pig.getZ());
                helper.assertTrue(s.core().in(Step.PLANT), "it flicks the stone, phase " + s.phase());
            }
            if (sown[0] >= 0 && launched[0] < 0) {
                if (s.launches() > 0) {
                    launched[0] = helper.getTick();
                } else {
                    long age = helper.getTick() - sown[0];
                    helper.assertTrue(s.mineAges().getFirst() == age, "the mine is " + age + " ticks into arming, reads " + s.mineAges());
                    helper.assertTrue(pig.getDeltaMovement().y < 0.5 && pig.getHealth() >= 100.0F, "an arming mine is harmless");
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sown[0] >= 0 && launched[0] >= 0, "a mine sown, then the pig launched");
            helper.assertTrue(off[0] < 0.01, "the mine lies at the pig's feet, " + off[0] + " off");
            helper.assertTrue(launched[0] - sown[0] == 40, "it armed 40 ticks after it was sown, took " + (launched[0] - sown[0]));
            helper.assertTrue(s.blasts() == 1 && s.mines().isEmpty(), "the mine is spent");
        });
    }

    /**
     * The counter: an arrow flying through an armed mine 5 blocks from where it was loosed sets it off, launching no one
     * (a pig 2.3 off its centre is left alone); an arrow dropped onto a mine still arming knocks it out, and it fizzles.
     */
    private static void counter(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(2.5, 1, 2.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 6.2));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.plantArmed(abs(helper, 5.5, 1, 8.5)), "an armed mine is down");
            helper.assertTrue(s.plantAt(abs(helper, 8.5, 1, 3.5), null), "a fresh one is down");
            s.core().end();
        });
        helper.runAtTickTime(5, () -> {
            arrow(helper, new Vec3(0.5, 1.5, 8.5), new Vec3(0.8, 0.0, 0.0), false);      // straight through the armed one
            arrow(helper, new Vec3(8.5, 3.5, 3.5), new Vec3(0.0, -0.5, 0.0), true);      // dropped onto the arming one
        });
        double[] lift = {0.0};
        helper.onEachTick(() -> lift[0] = Math.max(lift[0], pig.getDeltaMovement().y));
        helper.succeedWhen(() -> {
            helper.assertTrue(s.shotOff() == 2, "both mines were shot, " + s.shotOff());
            helper.assertTrue(s.blasts() == 1, "the armed one went off, blasts " + s.blasts());
            helper.assertTrue(s.fizzles() == 1, "the arming one fizzled, fizzles " + s.fizzles());
            helper.assertTrue(s.mines().isEmpty() && s.launches() == 0, "no mine left, and no one launched");
            helper.assertTrue(lift[0] < 0.5 && pig.getHealth() >= 100.0F, "the pig beside it stays put, lift " + lift[0]);
        });
    }

    /**
     * Failing the counter: a pig steps onto an armed mine and is launched straight up: it rises 9.8 to 10.4 blocks
     * (about 10), never drifts sideways, comes down where it went up, and the fall costs it 7 (floor(10.09 - 3)).
     */
    private static void failure(GameTestHelper helper) {
        openRoof(helper);
        RiftmineSapper s = sapper(helper, new Vec3(2.5, 1, 2.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        double[] from = {0, 0, 0}, top = {0.0}, drift = {0.0};
        long[] launched = {-1}, landed = {-1};
        helper.runAtTickTime(3, () -> {
            from[0] = pig.getX();
            from[1] = pig.getY();
            from[2] = pig.getZ();
            helper.assertTrue(s.plantArmed(pig.position()), "an armed mine under the pig");
        });
        helper.onEachTick(() -> {
            if (launched[0] < 0 && s.launches() == 1) launched[0] = helper.getTick();
            if (launched[0] < 0) return;
            top[0] = Math.max(top[0], pig.getY() - from[1]);
            drift[0] = Math.max(drift[0], Math.hypot(pig.getX() - from[0], pig.getZ() - from[2]));
            if (landed[0] < 0 && helper.getTick() > launched[0] + 5 && pig.onGround()) landed[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(landed[0] >= 0 && helper.getTick() >= landed[0] + 2, "launched and landed again");
            helper.assertTrue(top[0] >= 9.8 && top[0] <= 10.4, "it rose about 10 blocks, rose " + top[0]);
            helper.assertTrue(drift[0] < 0.01, "straight up and down, drifted " + drift[0]);
            helper.assertTrue(near(pig.getHealth(), 93.0), "the fall cost it 7, has " + pig.getHealth());
        });
    }

    /**
     * A survival player: standing on a mine still arming does nothing; on an armed one they are launched with a motion
     * of exactly (0, 1.35, 0), straight up, their fall so far forgotten.
     */
    private static void player(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(2.5, 1, 2.5));
        ServerPlayer arming = TestPlayers.survivor(helper, new Vec3(8.5, 1, 3.5));
        ServerPlayer armed = TestPlayers.survivor(helper, new Vec3(8.5, 1, 8.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.plantAt(arming.position(), null), "a fresh mine under one player");
            s.core().end();
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(s.launches() == 0 && s.mines().size() == 1, "an arming mine launches no one");
            armed.fallDistance = 3.0;
            helper.assertTrue(s.plantArmed(armed.position()), "an armed mine under the other");
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(s.launches() == 1, "the player on the armed mine is launched, launches " + s.launches());
            Vec3 v = armed.getDeltaMovement();
            helper.assertTrue(near(v.x, 0.0) && near(v.z, 0.0) && near(v.y, 1.35), "straight up at 1.35, got " + v);
            helper.assertTrue(armed.fallDistance < 0.01, "their fall forgotten, " + armed.fallDistance);
            helper.assertTrue(arming.getDeltaMovement().y < 0.5, "the other player stays put");
            helper.succeed();
        });
    }

    /**
     * One mine every 100 ticks: with a pig in reach left as its target (mines going off vetoed, so they pile up at its
     * feet), the second mine comes exactly 100 ticks after the first.
     */
    private static void every(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(3.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(8.5, 1, 5.5));
        veto(helper, s, "blast");
        s.setPlantClock(0);
        s.setTarget(pig);
        long[] first = {-1}, second = {-1};
        helper.onEachTick(() -> {
            if (first[0] < 0 && s.plants() == 1) first[0] = helper.getTick();
            if (second[0] < 0 && s.plants() == 2) second[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first[0] >= 0 && second[0] >= 0, "two mines, plants " + s.plants());
            helper.assertTrue(second[0] - first[0] == 100, "100 ticks apart, got " + (second[0] - first[0]));
            helper.assertTrue(s.mines().size() == 2, "both lie there, " + s.mines().size());
        });
    }

    /**
     * Never more than 4: with 4 out a fifth is refused, and its clock running out with a pig in reach sows nothing; once
     * an arrow knocks one out it sows again, back to 4.
     */
    private static void max4(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(2.5, 1, 2.5));
        Pig pig = pig(helper, new Vec3(8.5, 1, 8.5));
        veto(helper, s, "blast");
        helper.runAtTickTime(2, () -> {
            double[][] spots = {{2.5, 8.5}, {4.5, 8.5}, {8.5, 2.5}, {8.5, 4.5}};
            for (double[] xz : spots) {
                helper.assertTrue(s.plantAt(abs(helper, xz[0], 1, xz[1]), null), "mine at " + xz[0] + ", " + xz[1]);
                s.core().end();
            }
            helper.assertTrue(!s.plantAt(abs(helper, 5.5, 1, 5.5), null), "a fifth is refused");
            s.setPlantClock(0);
            s.setTarget(pig);
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(s.mines().size() == 4 && s.plants() == 4, "still 4 with its clock run out, " + s.mines().size());
            arrow(helper, new Vec3(2.5, 3.5, 8.5), new Vec3(0.0, -0.5, 0.0), true);     // onto the first, still arming
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(s.fizzles() == 1, "one knocked out, fizzles " + s.fizzles());
            helper.assertTrue(s.plants() == 5 && s.mines().size() == 4, "and one sown in its place, plants " + s.plants() + ", out " + s.mines().size());
        });
    }

    /** The radius is 1.5: a pig 1.4 off an armed mine's centre sets it off and is launched; one 1.6 off another does not. */
    private static void radius(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(9.5, 1, 6.0));
        Pig inside = pig(helper, new Vec3(4.9, 1, 3.5));
        Pig outside = pig(helper, new Vec3(5.1, 1, 8.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.plantArmed(abs(helper, 3.5, 1, 3.5)) && s.plantArmed(abs(helper, 3.5, 1, 8.5)), "two armed mines");
        });
        double[] in = {0.0}, out = {0.0};
        helper.onEachTick(() -> {
            in[0] = Math.max(in[0], inside.getDeltaMovement().y);
            out[0] = Math.max(out[0], outside.getDeltaMovement().y);
        });
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(in[0] > 1.0, "the pig 1.4 off is launched, lift " + in[0]);
            helper.assertTrue(out[0] < 0.5, "the pig 1.6 off is not, lift " + out[0]);
            helper.assertTrue(s.blasts() == 1 && s.mines().size() == 1, "one mine went off, one is left");
            helper.succeed();
        });
    }

    /**
     * The turned weapon: its own armed mine never goes off under it; an arrow into it throws the sapper about 10 blocks
     * straight up (9.8 to 10.4), the fall costs it 7 of its 26, and its other mine fizzles at once.
     */
    private static void turned(GameTestHelper helper) {
        openRoof(helper);
        RiftmineSapper s = sapper(helper, new Vec3(5.5, 1, 5.5));
        double[] from = {0.0}, top = {0.0};
        long[] thrown = {-1}, landed = {-1};
        helper.runAtTickTime(3, () -> {
            from[0] = s.getY();
            helper.assertTrue(s.plantArmed(s.position()), "an armed mine under itself");
            helper.assertTrue(s.plantAt(abs(helper, 2.5, 1, 9.5), null), "and another");
            s.core().end();
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(s.blasts() == 0 && s.launches() == 0, "its own mine never goes off under it");
            arrow(helper, new Vec3(6.7, 4.0, 5.5), new Vec3(0.0, -0.6, 0.0), true);    // into the ring beside it
        });
        helper.onEachTick(() -> {
            if (thrown[0] < 0 && s.timesFlung() == 1) {
                thrown[0] = helper.getTick();
                helper.assertTrue(s.mines().isEmpty() && s.fizzles() == 1, "its other mine fizzles at once, fizzles " + s.fizzles());
                helper.assertTrue(s.flung(), "it shows it was thrown");
            }
            if (thrown[0] < 0) return;
            top[0] = Math.max(top[0], s.getY() - from[0]);
            if (landed[0] < 0 && helper.getTick() > thrown[0] + 5 && s.onGround()) landed[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(landed[0] >= 0 && helper.getTick() >= landed[0] + 3, "thrown and landed again");
            helper.assertTrue(top[0] >= 9.8 && top[0] <= 10.4, "it rose about 10 blocks, rose " + top[0]);
            helper.assertTrue(near(s.getHealth(), 19.0), "the fall cost it 7, has " + s.getHealth());
            helper.assertTrue(!s.flung(), "on the ground again");
        });
    }

    /**
     * When it dies its mines fizzle at once: none is live the tick it dies, a pig stepping onto where the armed one lay is
     * not launched, and every mine is gone within 40 ticks.
     */
    private static void fizzleOnDeath(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(2.5, 1, 2.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 8.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.plantArmed(abs(helper, 8.5, 1, 8.5)), "an armed mine");
            helper.assertTrue(s.plantAt(abs(helper, 8.5, 1, 3.5), null), "an arming one");
            s.core().end();
        });
        helper.runAtTickTime(6, () -> {
            s.kill(helper.getLevel());
            helper.assertTrue(s.mines().isEmpty() && s.fizzles() == 2, "both fizzle the tick it dies, fizzles " + s.fizzles());
        });
        helper.runAtTickTime(7, () -> pig.snapTo(abs(helper, 8.5, 1, 8.5)));
        double[] lift = {0.0};
        helper.onEachTick(() -> lift[0] = Math.max(lift[0], pig.getDeltaMovement().y));
        helper.runAtTickTime(46, () -> {
            helper.assertTrue(lift[0] < 0.5 && s.launches() == 0, "the pig on the dead mine stays put, lift " + lift[0]);
            helper.assertTrue(s.minesHeld() == 0, "every mine is gone 40 ticks on, held " + s.minesHeld());
            helper.succeed();
        });
    }

    /**
     * Save and load keep the mines: an armed one and one about 15 ticks into arming come back where they lay with the
     * same ages; the second goes on to arm exactly 40 ticks after it was sown, and the loaded sapper's armed mine launches
     * a pig that steps onto it.
     */
    private static void saveLoad(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(2.5, 1, 2.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 8.5));
        Vec3 a = abs(helper, 8.5, 1, 8.5), b = abs(helper, 8.5, 1, 3.5);
        RiftmineSapper[] loaded = {null};
        long[] saved = {-1}, armed = {-1};
        int[] savedAge = {-1};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(s.plantArmed(a), "an armed mine");
            helper.assertTrue(s.plantAt(b, null), "a fresh one");
            s.core().end();
        });
        helper.runAtTickTime(18, () -> {
            List<Integer> ages = s.mineAges();
            helper.assertTrue(ages.size() == 2 && ages.get(0) == 40 && ages.get(1) >= 14 && ages.get(1) <= 15, "ages before the save " + ages);
            ServerLevel level = helper.getLevel();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            s.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            s.discard();
            RiftmineSapper fresh = ModMobs.RIFTMINE_SAPPER.get().create(level, EntitySpawnReason.LOAD);
            helper.assertTrue(fresh != null, "a fresh entity");
            fresh.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            fresh.removeFreeWill();
            fresh.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
            helper.assertTrue(level.addFreshEntity(fresh), "it joins");
            TestPlayers.whenDone(helper, fresh::discard);
            List<Vec3> mines = fresh.mines();
            helper.assertTrue(mines.size() == 2 && same(mines.get(0), a) && same(mines.get(1), b), "both mines where they lay: " + mines);
            helper.assertTrue(fresh.mineAges().equals(ages), "with their ages: " + fresh.mineAges() + ", saved " + ages);
            loaded[0] = fresh;
            saved[0] = helper.getTick();
            savedAge[0] = ages.get(1);
        });
        helper.runAtTickTime(20, () -> pig.snapTo(a));
        helper.onEachTick(() -> {
            RiftmineSapper fresh = loaded[0];
            if (fresh == null || armed[0] >= 0) return;
            List<Integer> ages = fresh.mineAges();
            if (!ages.isEmpty() && ages.getLast() == 40) armed[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            RiftmineSapper fresh = loaded[0];
            helper.assertTrue(fresh != null && fresh.launches() == 1, "the loaded armed mine launched the pig");
            helper.assertTrue(armed[0] >= 0, "the other armed");
            helper.assertTrue(armed[0] - saved[0] + savedAge[0] == 40, "40 ticks after it was sown, took " + (armed[0] - saved[0] + savedAge[0]));
        });
    }

    /**
     * Mines lie on solid ground only: a spot 1.5 over the floor settles onto it; a spot 6 blocks up in open air, with no
     * ground within reach under it (as over the void), takes no mine.
     */
    private static void ground(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(2.5, 1, 2.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!s.plantAt(abs(helper, 8.5, 7, 8.5), null), "no mine in open air");
            helper.assertTrue(s.mines().isEmpty() && s.core().idle(), "nothing sown, still idle");
            helper.assertTrue(s.plantAt(abs(helper, 8.5, 2.5, 8.5), null), "a mine over the floor");
            Vec3 m = s.mines().getFirst();
            helper.assertTrue(near(m.y, helper.absoluteVec(new Vec3(0, 1, 0)).y), "settled on the floor, at " + m);
            helper.succeed();
        });
    }

    // ---- the jab ---------------------------------------------------------------------------------------------------

    /**
     * The jab's tell: a pig a hand's breadth away starts it; it lasts exactly 10 ticks, the sapper stands still and
     * nothing is hurt; then the jab lands for exactly 4 with no knockback.
     */
    private static void jabTell(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        s.setPlantClock(1000);
        s.setTarget(pig);
        long[] start = {-1}, jab = {-1};
        double[] moved = {-1.0};
        helper.onEachTick(() -> {
            if (s.core().in(Step.TELL)) {
                if (start[0] < 0) start[0] = helper.getTick();
                helper.assertTrue(s.getDeltaMovement().horizontalDistance() < 0.01, "it stands still with the pick raised");
                helper.assertTrue(pig.getHealth() >= 100.0F, "the tell hurts nothing");
            } else if (start[0] >= 0 && jab[0] < 0) {
                jab[0] = helper.getTick();
                helper.assertTrue(s.core().in(Step.JAB), "the tell ends in the jab, phase " + s.phase());
                moved[0] = pig.getDeltaMovement().horizontalDistance();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && jab[0] >= 0, "a tell and then a jab");
            helper.assertTrue(jab[0] - start[0] == 10, "the tell lasts 10 ticks, lasted " + (jab[0] - start[0]));
            helper.assertTrue(near(pig.getHealth(), 96.0), "the jab does 4, health " + pig.getHealth());
            helper.assertTrue(moved[0] < 0.01, "with no knockback, motion " + moved[0]);
        });
    }

    /**
     * The jab on a survival player: its attack (4 on Normal; mob damage to players scales with difficulty: 3 on Easy, 6
     * on Hard) as the sapper's own blow, which armour reduces.
     */
    private static void jab(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(4.5, 1, 5.5));
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.3, 1, 5.5));
        s.setPlantClock(1000);
        s.setTarget(p);
        float before = p.getHealth();
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 3.0F;
            case HARD -> 6.0F;
            default -> 4.0F;
        };
        DamageSource[] last = {null};
        helper.onEachTick(() -> {
            if (last[0] == null && s.jabs() == 1) last[0] = p.getLastDamageSource();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(s.jabs() == 1, "the jab landed, phase " + s.phase());
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the jab does " + expected + " here, the player lost " + lost);
            helper.assertTrue(last[0] != null && last[0].is(DamageTypes.MOB_ATTACK) && last[0].getEntity() == s, "the sapper's attack, got " + last[0]);
            helper.assertTrue(!last[0].is(DamageTypeTags.BYPASSES_ARMOR), "which armour reduces");
        });
    }

    /**
     * Reach counts height: a pig on a pillar 3 high right beside it (1.6 up from the top of its head) starts no jab; a
     * pig on the ground beside it does.
     */
    private static void pillar(GameTestHelper helper) {
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.STONE);
        RiftmineSapper s = sapper(helper, new Vec3(4.5, 1, 5.5));
        Pig high = pig(helper, new Vec3(5.5, 4, 5.5));
        s.setPlantClock(1000);
        s.setTarget(high);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(s.tells() == 0 && s.core().idle(), "no jab at a pig 3 blocks up, phase " + s.phase());
            Pig low = pig(helper, new Vec3(4.5, 1, 6.5));
            s.setTarget(low);
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(s.tells() == 1, "a pig on the ground beside it starts one, tells " + s.tells());
            helper.succeed();
        });
    }

    /** A dead sapper lands nothing: killed 5 ticks into the jab's tell, the pig is never hurt. */
    private static void diesMidTell(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        s.setPlantClock(1000);
        s.setTarget(pig);
        long[] first = {-1}, killed = {-1};
        helper.onEachTick(() -> {
            if (first[0] < 0 && s.core().in(Step.TELL)) first[0] = helper.getTick();
            if (first[0] >= 0 && killed[0] < 0 && helper.getTick() == first[0] + 5) {
                s.kill(helper.getLevel());
                killed[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getTick() >= killed[0] + 20, "killed mid-tell, waiting out the jab");
            helper.assertTrue(s.jabs() == 0 && pig.getHealth() >= 100.0F, "no jab from a dead sapper, pig at " + pig.getHealth());
        });
    }

    // ---- the row ---------------------------------------------------------------------------------------------------

    /** Health 26, attack 4, armour 2, a 0.7 x 1.4 hitbox, speed 0.25. */
    private static void stats(GameTestHelper helper) {
        RiftmineSapper s = sapper(helper, new Vec3(5.5, 1, 5.5));
        s.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.getMaxHealth() == 26.0F && s.getHealth() == 26.0F, "health 26, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(s.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4.0, "attack 4, got " + s.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(s.getArmorValue() == 2, "armour 2, got " + s.getArmorValue());
            helper.assertTrue(Math.abs(s.getBbWidth() - 0.7F) < 0.01F && Math.abs(s.getBbHeight() - 1.4F) < 0.01F,
                    "a 0.7 x 1.4 hitbox, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.assertTrue(near(s.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.25), "speed 0.25");
            helper.succeed();
        });
    }

    /**
     * Home: the End barrens only, spawn weight 4, alone; a monster; a 40-tick tell (the arming) in its row; the Purpur
     * Sapper is a look; it drops gravity stone.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("riftmine_sapper"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "riftmine_sapper").stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "its home holds the End barrens");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "riftmine_sapper").stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.END_MIDLANDS)
                || b.is(Biomes.SMALL_END_ISLANDS) || b.is(Biomes.THE_END)), "and nowhere else in the End");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/riftmine_sapper")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.RIFTMINE_SAPPER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.RIFTMINE_SAPPER.get().getCategory() == MobCategory.MONSTER, "a monster");
        RosterStats.Entry row = RosterStats.get("riftmine_sapper");
        helper.assertTrue(row.tellTicks() == 40, "a 40-tick tell in its row");
        helper.assertTrue(Looks.of("riftmine_sapper").stream().anyMatch(x -> x.id().equals("purpur_sapper")), "the Purpur Sapper is a look");
        helper.assertTrue(row.material().equals("gravity_stone") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("gravity_stone")),
                "it drops gravity stone, a registered item");
        helper.succeed();
    }

    /**
     * Its spawn rule takes end stone and refuses a spot with nothing under it; a natural spawn is refused in the light
     * (a glowstone block beside the spot), as it spawns only in the dark.
     */
    private static void spawnRule(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("riftmine_sapper");
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.END_STONE);
        helper.setBlock(new BlockPos(8, 0, 8), Blocks.AIR);
        helper.setBlock(new BlockPos(8, 1, 3), Blocks.END_STONE);
        helper.setBlock(new BlockPos(9, 2, 3), Blocks.GLOWSTONE);
        var type = ModMobs.RIFTMINE_SAPPER.get();
        var level = helper.getLevel();
        helper.assertTrue(row.place() == RosterStats.Place.GROUND && !row.anyLight(), "a ground mob of the dark, place " + row.place());
        helper.assertTrue(TeemingSpawns.placeOk(row, type, level, helper.absolutePos(new BlockPos(3, 1, 3))), "end stone is a spawn spot");
        helper.assertTrue(!TeemingSpawns.placeOk(row, type, level, helper.absolutePos(new BlockPos(8, 1, 8))), "a spot over air is not");
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(!TeemingSpawns.check(row, type, level, EntitySpawnReason.NATURAL, helper.absolutePos(new BlockPos(8, 2, 3)), level.getRandom()),
                    "a lit spot on end stone takes no natural spawn");
            helper.succeed();
        });
    }
}
