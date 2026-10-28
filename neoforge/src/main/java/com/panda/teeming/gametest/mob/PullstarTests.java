package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Pullstar;
import com.panda.teeming.mob.end.Pullstar.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Pullstar: while bright it pulls every living thing whose middle is within 8 blocks of its own toward itself, at a
 * steady 0.09 blocks a tick (a sneaking player at a quarter of that), never across open void. Touching it starts an
 * 8-tick flare, then the touch deals its attack of 6 through armour. Every 100 ticks it dims for 40, after a 20-tick
 * tell: dim, it neither pulls nor burns and takes x2. Dead, it finishes nothing.
 *
 * <p>Stars with their AI on run the cycle; they get a speed of 0 so they hold still, and a follow range of 1 so they
 * never notice survivors in other arenas. Stars with no AI never dim: they stay bright, pulling and burning, which
 * suits the pull and touch tests. Pigs that must be moved keep their physics and lose their goals; pigs whose pull is
 * only read have no AI. Every test keeps its stars to its own bodies: a step that pairs one of this test's stars with
 * someone else's body, or the other way round, is vetoed, and its stars pull no items unless the test is about them.
 */
public final class PullstarTests {
    private PullstarTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("pullstar_tell", PullstarTests::tell);
        out.accept("pullstar_counter", PullstarTests::counter);
        TeemingGameTests.timeLimit("pullstar_counter", 160);
        out.accept("pullstar_failure", PullstarTests::failure);
        TeemingGameTests.timeLimit("pullstar_failure", 160);
        out.accept("pullstar_cycle", PullstarTests::cycle);
        TeemingGameTests.timeLimit("pullstar_cycle", 260);
        out.accept("pullstar_strength", PullstarTests::strength);
        out.accept("pullstar_sneak", PullstarTests::sneak);
        out.accept("pullstar_radius", PullstarTests::radius);
        out.accept("pullstar_void", PullstarTests::noPullAcrossVoid);
        out.accept("pullstar_touch", PullstarTests::touch);
        out.accept("pullstar_armour", PullstarTests::armour);
        out.accept("pullstar_dies_mid_flare", PullstarTests::diesMidFlare);
        out.accept("pullstar_items", PullstarTests::items);
        out.accept("pullstar_hover", PullstarTests::hover);
        out.accept("pullstar_pull_cancel", PullstarTests::pullCancel);
        out.accept("pullstar_spawn_rule", PullstarTests::spawnRule);
        out.accept("pullstar_stats", PullstarTests::stats);
        out.accept("pullstar_home", PullstarTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /**
     * A base-look Pullstar set on the floor at (x, z) (it rises to hover a block up on its first tick), with a follow
     * range of 1, removed when the test ends. With {@code ai} it runs its cycle and holds still (speed 0); without, it
     * stays bright.
     */
    private static Pullstar star(GameTestHelper helper, double x, double z, boolean ai) {
        Pullstar s = helper.spawn(ModMobs.PULLSTAR.get(), new Vec3(x, 1, z));
        s.core().setLook(LookDef.BASE, true);
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        if (ai) s.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        else s.setNoAi(true);
        TestPlayers.whenDone(helper, s::discard);
        return s;
    }

    /** A pig with 100 health on the floor at (x, z): {@code moves} keeps its physics (no goals), else it has no AI. */
    private static Pig pig(GameTestHelper helper, double x, double z, boolean moves) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        if (moves) pig.removeFreeWill();
        else pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /**
     * Keeps this test's stars and bodies to each other: a Pullstar step that pairs one of {@code mine} with a body that
     * isn't, or a stranger with one of {@code mine}, is vetoed, and so is every item pull by one of {@code mine}.
     */
    private static Set<Entity> contain(GameTestHelper helper, boolean items, Entity... mine) {
        Set<Entity> set = new HashSet<>(List.of(mine));
        Consumer<MechanicEvent> keep = e -> {
            if (!(e.mob() instanceof Pullstar)) return;
            if (e.step().equals("pull_item") && set.contains(e.mob()) && !items) e.cancel();
            if (e.target() != null && set.contains(e.mob()) != set.contains(e.target())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        return set;
    }

    private static Vec3 rel(GameTestHelper helper, Entity e) {
        return helper.relativeVec(e.position());
    }

    private static double across(Entity a, Entity b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    // ---- the tell and the cycle ------------------------------------------------------------------------------------

    /**
     * The tell: 40 ticks after it first burns, it loosens for exactly 20 ticks before the dim. Through it the pull still
     * holds a body in reach at 0.09, it takes plain damage (a 4 costs 4), and its heart flickers dark and bright.
     */
    private static void tell(GameTestHelper helper) {
        Pullstar s = star(helper, 3.5, 5.5, true);
        Pig pig = pig(helper, 8.5, 5.5, false);
        contain(helper, false, s, pig);
        long[] born = {-1}, from = {-1}, to = {-1};
        boolean[] quiet = {true}, dark = {false}, bright = {false};
        float[] lost = {-1};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (born[0] < 0 && s.tickCount > 0) born[0] = now;
            if (from[0] < 0 && s.core().in(Step.TELL)) from[0] = now;
            if (from[0] >= 0 && to[0] < 0) {
                if (s.core().in(Step.TELL)) {
                    if (s.vulnerable() || Math.abs(s.drift(pig) - 0.09) > 1.0E-9) quiet[0] = false;
                    if (s.core().stage() == Pullstar.STAGE_DARK) dark[0] = true;
                    if (s.core().stage() == Pullstar.STAGE_BRIGHT) bright[0] = true;
                    if (now == from[0] + 10) {
                        float before = s.getHealth();
                        s.hurtServer(helper.getLevel(), s.damageSources().magic(), 4.0F);
                        lost[0] = before - s.getHealth();
                    }
                } else {
                    to[0] = now;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(to[0] >= 0, "the tell ran out");
            helper.assertTrue(from[0] - born[0] >= 39 && from[0] - born[0] <= 41, "the tell came 40 ticks after it first burned, after " + (from[0] - born[0]));
            helper.assertTrue(to[0] - from[0] == 20, "the tell lasted 20 ticks, saw " + (to[0] - from[0]));
            helper.assertTrue(s.core().in(Step.DIM), "the dim follows the tell");
            helper.assertTrue(quiet[0], "through the tell it still pulls at 0.09 and is not open");
            helper.assertTrue(Math.abs(lost[0] - 4.0F) < 0.01F, "a 4 in the tell costs 4, cost " + lost[0]);
            helper.assertTrue(dark[0] && bright[0], "the heart flickers dark and bright in the tell");
        });
    }

    /** The cycle: tell 20, dim 40, and one tell to the next exactly 100 ticks apart, twice over. */
    private static void cycle(GameTestHelper helper) {
        Pullstar s = star(helper, 5.5, 5.5, true);
        contain(helper, false, s);
        List<Long> tells = new java.util.ArrayList<>(), dims = new java.util.ArrayList<>(), brights = new java.util.ArrayList<>();
        Step[] last = {Step.IDLE};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            Step p = (Step) s.core().phase();
            if (p != last[0]) {
                if (p == Step.TELL) tells.add(now);
                else if (p == Step.DIM) dims.add(now);
                else if (!dims.isEmpty()) brights.add(now);
                last[0] = p;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tells.size() >= 3 && dims.size() >= 2 && brights.size() >= 2, "two full cycles, saw " + tells + " " + dims + " " + brights);
            for (int i = 0; i < 2; i++) {
                helper.assertTrue(dims.get(i) - tells.get(i) == 20, "tell " + i + " lasted " + (dims.get(i) - tells.get(i)));
                helper.assertTrue(brights.get(i) - dims.get(i) == 40, "dim " + i + " lasted " + (brights.get(i) - dims.get(i)));
                helper.assertTrue(tells.get(i + 1) - tells.get(i) == 100, "cycle " + i + " lasted " + (tells.get(i + 1) - tells.get(i)));
            }
        });
    }

    /**
     * The counter: dim, it is open (its heart gold) and a 5 costs it 10; it neither pulls (a pig 4 blocks off stays put
     * through the dim) nor burns (a pig touching it is never flared).
     */
    private static void counter(GameTestHelper helper) {
        Pullstar s = star(helper, 5.5, 5.5, true);
        Set<Entity> mine = contain(helper, false, s);
        long[] dim = {-1};
        float[] lost = {-1};
        Pig[] near = {null}, against = {null};
        Vec3[] at = {null};
        double[] moved = {0};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (dim[0] < 0 && s.core().in(Step.DIM)) {
                dim[0] = now;
                near[0] = pig(helper, 5.5, 9.5, true);
                against[0] = pig(helper, 5.5, 6.45, false);   // pressed against it: a touch, if it burned
                mine.add(near[0]);
                mine.add(against[0]);
            }
            if (dim[0] < 0) return;
            if (now == dim[0] + 3) at[0] = near[0].position();
            if (now > dim[0] + 3 && now < dim[0] + 38) moved[0] = Math.max(moved[0], near[0].position().distanceTo(at[0]));
            if (now == dim[0] + 10) {
                float before = s.getHealth();
                s.hurtServer(helper.getLevel(), s.damageSources().magic(), 5.0F);
                lost[0] = before - s.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dim[0] >= 0 && helper.getLevel().getGameTime() >= dim[0] + 38, "waiting out the dim");
            helper.assertTrue(s.core().in(Step.DIM) && s.vulnerable(), "still dim, its heart open");
            helper.assertTrue(Math.abs(lost[0] - 10.0F) < 0.01F, "a 5 while dim costs 10, cost " + lost[0]);
            helper.assertTrue(moved[0] < 0.01, "no pull while dim: the pig 4 off moved " + moved[0]);
            helper.assertTrue(s.drift(near[0]) == 0.0, "nothing drifts toward a dim star");
            helper.assertTrue(s.flares() == 0 && against[0].getHealth() == 100.0F, "no flare and no burn while dim");
        });
    }

    /**
     * The failure: a body that stands in the pull is drawn in to the star, which flares and burns it: 6 (its attack),
     * and the flare came 8 ticks before the burn.
     */
    private static void failure(GameTestHelper helper) {
        Pullstar s = star(helper, 3.5, 5.5, false);
        Pig pig = pig(helper, 7.5, 5.5, true);
        contain(helper, false, s, pig);
        long[] flare = {-1}, burn = {-1};
        float[] first = {-1};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (flare[0] < 0 && s.flaring()) flare[0] = now;
            if (burn[0] < 0 && pig.getHealth() < 100.0F) {
                burn[0] = now;
                first[0] = 100.0F - pig.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(burn[0] >= 0, "the pig was drawn in and burned");
            helper.assertTrue(across(s, pig) < 1.1, "the pig ended pressed against it, " + across(s, pig) + " across");
            helper.assertTrue(Math.abs(first[0] - 6.0F) < 0.01F, "the touch deals 6, dealt " + first[0]);
            helper.assertTrue(burn[0] - flare[0] == 8, "the flare came 8 ticks before the burn, saw " + (burn[0] - flare[0]));
        });
    }

    // ---- the pull ----------------------------------------------------------------------------------------------------

    /** Strength: a pig standing still on stone 6 blocks off settles to a drift of 0.09 a tick toward the star. */
    private static void strength(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) helper.setBlock(new BlockPos(x, 0, 5), Blocks.END_STONE);
        Pullstar s = star(helper, 2.5, 5.5, false);
        Pig pig = pig(helper, 8.5, 5.5, true);
        contain(helper, false, s, pig);
        double[] x15 = {Double.NaN}, x25 = {Double.NaN};
        helper.runAtTickTime(15, () -> x15[0] = pig.getX());
        helper.runAtTickTime(25, () -> x25[0] = pig.getX());
        helper.runAtTickTime(26, () -> {
            double speed = (x15[0] - x25[0]) / 10.0;
            helper.assertTrue(Math.abs(speed - 0.09) < 0.005, "it drifts toward the star at 0.09 a tick, saw " + speed);
            helper.assertTrue(Math.abs(rel(helper, pig).z - 5.5) < 0.01, "straight toward it");
            helper.succeed();
        });
    }

    /** Sneaking: a survivor in reach is pulled at 0.09; sneaking, at 0.0225 (a quarter). */
    private static void sneak(GameTestHelper helper) {
        Pullstar s = star(helper, 3.5, 5.5, false);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(8.5, 1, 5.5));
        contain(helper, false, s, p);
        helper.runAtTickTime(3, () -> {
            double walking = s.drift(p);
            p.setShiftKeyDown(true);
            double sneaking = s.drift(p);
            helper.assertTrue(Math.abs(walking - 0.09) < 1.0E-9, "standing, it is pulled at 0.09, got " + walking);
            helper.assertTrue(Math.abs(sneaking - 0.0225) < 1.0E-9, "sneaking, at 0.0225, got " + sneaking);
            helper.succeed();
        });
    }

    /** Radius 8, middle to middle: a pig 7.9 off is drawn in, one 8.1 off stays put. */
    private static void radius(GameTestHelper helper) {
        Pullstar s = star(helper, 1.5, 2.5, false);
        Pig[] pigs = new Pig[2];
        Vec3[] from = new Vec3[2];
        helper.runAtTickTime(2, () -> {
            Vec3 m = rel(helper, s).add(0, s.getBbHeight() / 2.0, 0);   // risen: its middle 2.5 over the floor's base
            double dy = m.y - (1.0 + EntityTypes.PIG.getHeight() / 2.0);
            double in = Math.sqrt(7.9 * 7.9 - dy * dy), out = Math.sqrt(8.1 * 8.1 - dy * dy - 36.0);
            pigs[0] = pig(helper, m.x + in, m.z, true);
            pigs[1] = pig(helper, m.x + out, m.z + 6.0, true);
            contain(helper, false, s, pigs[0], pigs[1]);
            for (int i = 0; i < 2; i++) from[i] = pigs[i].position();
            double d0 = pigs[0].getBoundingBox().getCenter().distanceTo(s.getBoundingBox().getCenter());
            double d1 = pigs[1].getBoundingBox().getCenter().distanceTo(s.getBoundingBox().getCenter());
            helper.assertTrue(Math.abs(d0 - 7.9) < 0.01 && Math.abs(d1 - 8.1) < 0.01, "pigs set 7.9 and 8.1 off, at " + d0 + " and " + d1);
            helper.assertTrue(s.inReach(pigs[0]) && !s.inReach(pigs[1]), "7.9 is in reach, 8.1 is not");
        });
        helper.runAtTickTime(23, () -> {
            double near = from[0].distanceTo(pigs[0].position()), far = from[1].distanceTo(pigs[1].position());
            helper.assertTrue(near > 0.5, "the pig 7.9 off was drawn in, moved " + near);
            helper.assertTrue(far < 0.01, "the pig 8.1 off stayed put, moved " + far);
            helper.succeed();
        });
    }

    /**
     * No pull across open void: a trench 7 deep between a pig and the star leaves the pig alone; a pig on the other
     * side, with ground all the way, is drawn in.
     */
    private static void noPullAcrossVoid(GameTestHelper helper) {
        for (int z = 2; z <= 8; z++) {
            for (int y = 0; y >= -6; y--) helper.setBlock(new BlockPos(5, y, z), Blocks.AIR);
            helper.setBlock(new BlockPos(5, -7, z), Blocks.STONE);
        }
        Pullstar s = star(helper, 2.5, 5.5, false);
        Pig beyond = pig(helper, 8.5, 5.5, true);
        Pig control = pig(helper, 2.5, 9.5, true);
        contain(helper, false, s, beyond, control);
        Vec3[] from = new Vec3[2];
        helper.runAtTickTime(3, () -> {
            from[0] = beyond.position();
            from[1] = control.position();
            helper.assertTrue(s.voidBetween(beyond) && !s.voidBetween(control), "the trench is void between, the other side is ground");
            helper.assertTrue(s.drift(beyond) == 0.0 && s.drift(control) > 0.0, "the pull skips the pig past the trench");
        });
        helper.runAtTickTime(23, () -> {
            double b = from[0].distanceTo(beyond.position()), c = from[1].distanceTo(control.position());
            helper.assertTrue(b < 0.01, "the pig past the trench stayed put, moved " + b);
            helper.assertTrue(c > 0.5, "the pig on solid ground was drawn in, moved " + c);
            helper.succeed();
        });
    }

    /** A vetoed pull: an event that cancels the step "pull" as it starts burning switches the pull off. */
    private static void pullCancel(GameTestHelper helper) {
        Pullstar[] mine = {null};
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() instanceof Pullstar && e.step().equals("pull") && e.target() == null && (mine[0] == null || e.mob() == mine[0])) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        Pullstar s = star(helper, 3.5, 5.5, false);
        mine[0] = s;
        Pig pig = pig(helper, 7.5, 5.5, true);
        contain(helper, false, s, pig);
        Vec3[] from = {null};
        helper.runAtTickTime(3, () -> from[0] = pig.position());
        helper.runAtTickTime(23, () -> {
            helper.assertTrue(!s.pulls() && s.drift(pig) == 0.0, "the pull is off");
            helper.assertTrue(from[0].distanceTo(pig.position()) < 0.01, "the pig stayed put");
            helper.succeed();
        });
    }

    /** Items: a stack lying 4 blocks off slides in toward it. */
    private static void items(GameTestHelper helper) {
        Pullstar s = star(helper, 5.5, 5.5, false);
        contain(helper, true, s);
        ItemEntity item = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.END_STONE));
        Vec3 at = helper.absoluteVec(new Vec3(9.5, 1, 5.5));
        item.snapTo(at.x, at.y, at.z);
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        TestPlayers.whenDone(helper, item::discard);
        helper.runAtTickTime(25, () -> {
            double moved = at.x - item.getX();
            helper.assertTrue(moved > 1.0, "the item slid toward it, " + moved);
            helper.succeed();
        });
    }

    // ---- the touch ---------------------------------------------------------------------------------------------------

    /**
     * The touch: a pig pressed against it starts a flare at once; for the 8 ticks of the flare nothing is hurt, then the
     * pig loses 6. The next flare waits until 28 ticks after the first.
     */
    private static void touch(GameTestHelper helper) {
        Pullstar s = star(helper, 5.5, 5.5, false);
        Pig[] pig = {null};
        long[] flare = {-1}, burn = {-1}, second = {-1};
        boolean[] lit = {true};
        float[] first = {-1};
        helper.runAtTickTime(2, () -> {
            pig[0] = pig(helper, 5.5, 5.5 + 0.95, false);
            contain(helper, false, s, pig[0]);
        });
        helper.onEachTick(() -> {
            if (pig[0] == null) return;
            long now = helper.getLevel().getGameTime();
            if (flare[0] < 0 && s.flaring()) flare[0] = now;
            if (flare[0] >= 0 && burn[0] < 0) {
                if (pig[0].getHealth() < 100.0F) {
                    burn[0] = now;
                    first[0] = 100.0F - pig[0].getHealth();
                } else if (!s.core().flag(Pullstar.FLAG_FLARE) || s.core().stage() != Pullstar.STAGE_FLARE) lit[0] = false;
            }
            if (burn[0] >= 0 && second[0] < 0 && s.flaring() && now > burn[0]) second[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0] >= 0, "a second flare came");
            helper.assertTrue(burn[0] - flare[0] == 8, "nothing for the 8-tick flare, then the burn: " + (burn[0] - flare[0]));
            helper.assertTrue(lit[0], "the heart blazed (flag and glow) through the flare");
            helper.assertTrue(Math.abs(first[0] - 6.0F) < 0.01F, "the touch deals 6, dealt " + first[0]);
            helper.assertTrue(second[0] - flare[0] == 28, "the next flare 28 ticks after the first, saw " + (second[0] - flare[0]));
        });
    }

    /** Armour counts: a pig with armour 10 takes 4.32 of the touch's 6. */
    private static void armour(GameTestHelper helper) {
        Pullstar s = star(helper, 5.5, 5.5, false);
        Pig[] pig = {null};
        helper.runAtTickTime(2, () -> {
            pig[0] = pig(helper, 5.5, 5.5 + 0.95, false);
            pig[0].getAttribute(Attributes.ARMOR).setBaseValue(10.0);
            contain(helper, false, s, pig[0]);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(pig[0] != null && s.touches() == 1, "one touch, saw " + s.touches());
            float lost = 100.0F - pig[0].getHealth();
            helper.assertTrue(Math.abs(lost - 4.32F) < 0.01F, "armour 10 turns the 6 into 4.32, the pig lost " + lost);
        });
    }

    /**
     * Killed 4 ticks into a flare, it finishes nothing: the pig pressed against it is never burned, and a pig in reach
     * is pulled no more through the death.
     */
    private static void diesMidFlare(GameTestHelper helper) {
        Pullstar s = star(helper, 3.5, 5.5, false);
        Pig[] pig = {null};
        Pig drawn = pig(helper, 3.5, 9.5, true);
        long[] flare = {-1}, killed = {-1};
        Vec3[] at = {null};
        double[] moved = {0};
        helper.runAtTickTime(2, () -> {
            pig[0] = pig(helper, 3.5, 5.5 - 0.95, false);
            contain(helper, false, s, pig[0], drawn);
        });
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (flare[0] < 0 && s.flaring()) flare[0] = now;
            if (flare[0] >= 0 && killed[0] < 0 && now == flare[0] + 4) {
                s.kill(helper.getLevel());
                killed[0] = now;
            }
            if (killed[0] >= 0 && now == killed[0] + 5) at[0] = drawn.position();
            if (at[0] != null && now <= killed[0] + 18) moved[0] = Math.max(moved[0], drawn.position().distanceTo(at[0]));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getLevel().getGameTime() >= killed[0] + 18, "killed mid-flare, waiting out the burn");
            helper.assertTrue(pig[0].getHealth() == 100.0F && s.touches() == 0, "no burn from a dead star");
            helper.assertTrue(moved[0] < 0.02, "no pull through the death, the pig moved " + moved[0]);
            helper.assertTrue(s.drift(drawn) == 0.0, "a dying star pulls nothing");
        });
    }

    // ---- hovering ----------------------------------------------------------------------------------------------------

    /**
     * It hovers a block over the floor it is set on, and drifting up to its target it stops at the brink of a pit: it
     * never moves out over open void.
     */
    private static void hover(GameTestHelper helper) {
        for (int x = 4; x <= 6; x++) {
            for (int z = 0; z <= 11; z++) {
                for (int y = 0; y >= -6; y--) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, -7, z), Blocks.STONE);
            }
        }
        Pullstar s = helper.spawn(ModMobs.PULLSTAR.get(), new Vec3(1.5, 1, 5.5));
        s.core().setLook(LookDef.BASE, true);
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, s::discard);
        Pig pig = pig(helper, 9.5, 5.5, false);
        contain(helper, false, s, pig);
        double[] most = {0};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(Math.abs(rel(helper, s).y - 2.0) < 0.01, "it rose to hover a block over the floor, at " + rel(helper, s).y);
            s.setTarget(pig);
        });
        helper.onEachTick(() -> most[0] = Math.max(most[0], rel(helper, s).x + s.getBbWidth() / 2.0));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(most[0] <= 4.0, "it never reached out over the pit, its edge got to x " + most[0]);
            helper.assertTrue(most[0] > 3.0, "it did drift up to the brink, its edge got to x " + most[0]);
            helper.assertTrue(Math.abs(rel(helper, s).y - 2.0) < 0.05, "still hovering a block up, at " + rel(helper, s).y);
            helper.succeed();
        });
    }

    // ---- spawning and numbers ---------------------------------------------------------------------------------------

    /**
     * The spawn rule: in the End barrens' list, alone, at weight 3 (the barrens' list is weighted across the whole End
     * roster); its roster weight of 4 stands for any other biome its tag is moved to, and leaves the barrens out. End
     * stone under it is a spawn spot, open air is not.
     */
    private static void spawnRule(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var modifiers = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        var barrens = modifiers.get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/pullstar/end_barrens")))
                .map(Holder::value).orElse(null);
        var elsewhere = modifiers.get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/pullstar")))
                .map(Holder::value).orElse(null);
        if (!(barrens instanceof BiomeModifiers.AddSpawnsBiomeModifier add) || !(elsewhere instanceof BiomeModifiers.AddSpawnsBiomeModifier rest)) {
            helper.fail("no spawn biome modifiers for the Pullstar: " + barrens + ", " + elsewhere);
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> spawners = add.spawners().unwrap();
        helper.assertTrue(spawners.size() == 1 && spawners.getFirst().value().type() == ModMobs.PULLSTAR.get(), "one Pullstar spawner, " + spawners);
        Weighted<MobSpawnSettings.SpawnerData> spawner = spawners.getFirst();
        helper.assertTrue(spawner.weight() == 3, "weight 3 in the barrens, got " + spawner.weight());
        helper.assertTrue(spawner.value().count().minInclusive() == 1 && spawner.value().count().maxInclusive() == 1,
                "alone, got " + spawner.value().count().minInclusive() + " to " + spawner.value().count().maxInclusive());
        helper.assertTrue(add.biomes().contains(biomes.getOrThrow(Biomes.END_BARRENS)), "the End barrens are its home");
        for (var other : List.of(Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS, Biomes.THE_END, Biomes.SMALL_END_ISLANDS)) {
            helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(other)) && !rest.biomes().contains(biomes.getOrThrow(other)), "not in " + other);
        }
        Weighted<MobSpawnSettings.SpawnerData> row4 = rest.spawners().unwrap().getFirst();
        helper.assertTrue(row4.weight() == 4 && !rest.biomes().contains(biomes.getOrThrow(Biomes.END_BARRENS)),
                "weight 4 anywhere else its tag reaches, not again in the barrens");

        RosterStats.Entry row = RosterStats.get("pullstar");
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.END_STONE);
        for (int y = 0; y >= -3; y--) helper.setBlock(new BlockPos(8, y, 8), Blocks.AIR);
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground spawn, place " + row.place());
        helper.assertTrue(TeemingSpawns.placeOk(row, ModMobs.PULLSTAR.get(), helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))),
                "end stone under it is a spawn spot");
        helper.assertTrue(!TeemingSpawns.placeOk(row, ModMobs.PULLSTAR.get(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 1, 8))),
                "open air under it is not");
        helper.succeed();
    }

    /** Health 30, attack 6, armour 4, a 1.0 x 1.0 hitbox. */
    private static void stats(GameTestHelper helper) {
        Pullstar s = star(helper, 5.5, 5.5, false);
        contain(helper, false, s);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.getMaxHealth() == 30.0F && s.getHealth() == 30.0F, "health 30, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(s.getAttributeValue(Attributes.ATTACK_DAMAGE) == 6.0, "attack 6, got " + s.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(s.getArmorValue() == 4, "armour 4, got " + s.getArmorValue());
            helper.assertTrue(Math.abs(s.getBbWidth() - 1.0F) < 0.01F && Math.abs(s.getBbHeight() - 1.0F) < 0.01F,
                    "a 1.0 x 1.0 hitbox, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: the End barrens, alone, at weight 4; a monster; a 20-tick tell; the Red Pullstar; gravity stone. */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("pullstar");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.PULLSTAR.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.assertTrue(row.biomeSpawns() && row.weight() == 4, "biome spawns at weight 4");
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "alone, got " + row.groupMin() + ".." + row.groupMax());
        helper.assertTrue(row.tellTicks() == 20, "a 20-tick tell in its row");
        List<LookDef> looks = Looks.of("pullstar");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("red_pullstar")), "the Red Pullstar is a look: " + looks);
        helper.assertTrue(row.material().equals("gravity_stone") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("gravity_stone")),
                "it drops gravity stone, a registered item");
        helper.succeed();
    }
}
