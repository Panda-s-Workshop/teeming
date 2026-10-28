package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.advancement.TeemingAdvancements;
import com.panda.teeming.gametest.AdvancementTests;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.end.GrandOrrery;
import com.panda.teeming.mob.end.GrandOrrery.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawner.Rule;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Grand Orrery: a 20-tick spin-up that looses nothing yet; then a spiral of 24 motes over 40 ticks, a double ring of
 * two 12s (the second slower and turned half a step) or an aimed burst of three volleys of three, in turn; motes at
 * 0.25 blocks a tick that hit for 6 without knockback, stop at blocks and are taken by a shield; every pattern leaves
 * gaps a player standing still in is never hit by; then the core opens for 60 ticks with armour 0 and every hit doubled,
 * and the orrery sinks to its target's height. Health 300, armour 10, a 2.0 x 2.0 hitbox, 100 experience, one in 128
 * blocks, placed in the open sky off the main island. Every number is checked against a plain value.
 *
 * <p>Every arena that sees motes is walled at its edge, five blocks high, so no mote leaves it, and every orrery is
 * discarded when its test ends, which drops its motes. Orreries have their goals cleared, a follow range of 1 and their
 * own spin-ups held, so they never notice a survival player in a neighbouring arena; most hang where they are put, and
 * are told to spin up directly. Pigs have their goals cleared, so they stand still.
 */
public final class GrandOrreryTests {
    private GrandOrreryTests() {}

    /** Where most orreries hang: its feet a block over the floor, 3 blocks in from the arena's west wall, its core 3 up. */
    private static final Vec3 HANG = new Vec3(3.0, 2, 5.5);
    /** And the pig they aim at, 6.5 blocks east along the arena: the aim is a yaw of -90. */
    private static final Vec3 TARGET = new Vec3(9.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("grand_orrery_tell", GrandOrreryTests::tell);
        out.accept("grand_orrery_counter", GrandOrreryTests::counter);
        TeemingGameTests.timeLimit("grand_orrery_counter", 160);
        out.accept("grand_orrery_failure", GrandOrreryTests::failure);
        TeemingGameTests.timeLimit("grand_orrery_failure", 160);
        out.accept("grand_orrery_spiral", GrandOrreryTests::spiral);
        out.accept("grand_orrery_double_ring", GrandOrreryTests::doubleRing);
        out.accept("grand_orrery_burst", GrandOrreryTests::burst);
        out.accept("grand_orrery_gap_spiral", h -> gap(h, Step.SPIRAL, 7.5, -15.0));
        TeemingGameTests.timeLimit("grand_orrery_gap_spiral", 160);
        out.accept("grand_orrery_gap_double_ring", h -> gap(h, Step.DOUBLE_RING, 7.5, -15.0));
        TeemingGameTests.timeLimit("grand_orrery_gap_double_ring", 160);
        out.accept("grand_orrery_gap_burst", h -> gap(h, Step.BURST, 10.0, -20.0));
        TeemingGameTests.timeLimit("grand_orrery_gap_burst", 160);
        out.accept("grand_orrery_mote", GrandOrreryTests::mote);
        out.accept("grand_orrery_shield", GrandOrreryTests::shield);
        TeemingGameTests.timeLimit("grand_orrery_shield", 160);
        out.accept("grand_orrery_armor", GrandOrreryTests::armor);
        out.accept("grand_orrery_cycle", GrandOrreryTests::cycle);
        TeemingGameTests.timeLimit("grand_orrery_cycle", 340);
        out.accept("grand_orrery_sink", GrandOrreryTests::sink);
        TeemingGameTests.timeLimit("grand_orrery_sink", 140);
        out.accept("grand_orrery_keeps_distance", GrandOrreryTests::keepsDistance);
        out.accept("grand_orrery_dies_mid_pattern", GrandOrreryTests::diesMidPattern);
        out.accept("grand_orrery_stats", GrandOrreryTests::stats);
        out.accept("grand_orrery_home", GrandOrreryTests::home);
        out.accept("grand_orrery_spawn_rule", GrandOrreryTests::spawnRule);
        out.accept("grand_orrery_cap", GrandOrreryTests::cap);
        out.accept("grand_orrery_feat", h -> feat(h, true));
        TeemingGameTests.timeLimit("grand_orrery_feat", 160);
        out.accept("grand_orrery_feat_struck", h -> feat(h, false));
        TeemingGameTests.timeLimit("grand_orrery_feat_struck", 160);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Stone round the arena's edge cells, five high: every mote stops inside the arena. */
    private static void walls(GameTestHelper helper) {
        for (int y = 1; y <= 5; y++) {
            for (int i = 0; i < 12; i++) {
                helper.setBlock(new BlockPos(i, y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(i, y, 11), Blocks.STONE);
                helper.setBlock(new BlockPos(0, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(11, y, i), Blocks.STONE);
            }
        }
    }

    /** An orrery at {@code at} with no goals, a follow range of 1 and its own spin-ups held; {@code hang}: it stays put. */
    private static GrandOrrery orrery(GameTestHelper helper, Vec3 at, boolean hang) {
        GrandOrrery o = helper.spawn(ModMobs.GRAND_ORRERY.get(), at);
        o.removeFreeWill();
        o.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        o.holdFire(true);
        o.anchor(hang);
        TestPlayers.whenDone(helper, o::discard);                     // its motes go with it
        return o;
    }

    /** A walled arena and an orrery hanging at {@link #HANG}. */
    private static GrandOrrery hanging(GameTestHelper helper) {
        walls(helper);
        return orrery(helper, HANG, true);
    }

    /** A still pig with {@code health} at {@code at}. */
    private static Pig pig(GameTestHelper helper, Vec3 at, float health) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.removeFreeWill();
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        pig.setHealth(health);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static float lost(LivingEntity e, float from) {
        return from - e.getHealth();
    }

    /** {@code at} plus {@code distance} blocks along {@code yaw} (a mob's yaw: -90 is +X), on the floor. */
    private static Vec3 out(Vec3 at, double yaw, double distance) {
        double r = Math.toRadians(yaw);
        return new Vec3(at.x - Math.sin(r) * distance, 1, at.z + Math.cos(r) * distance);
    }

    /** The yaw a step from {@code a} to {@code b} goes along, as a mob's yaw. */
    private static double yawOf(Vec3 a, Vec3 b) {
        return Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG - 90.0;
    }

    /** One mote's cost to a player here: mob projectiles to players scale with difficulty. */
    private static float moteHere(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case EASY -> 4.0F;          // min(6 / 2 + 1, 6)
            case HARD -> 9.0F;          // 6 x 1.5
            default -> 6.0F;
        };
    }

    /**
     * Follows one orrery's motes: for each, the phase and phase tick it was first seen in, where it was then and a tick
     * later (its heading and speed), and where it was last seen.
     */
    private static final class Log {
        static final class Mote {
            Step step;
            int tick;
            Vec3 first, second, last;

            double yaw() {
                return yawOf(first, second);
            }

            double speed() {
                return second.distanceTo(first);
            }
        }

        final Map<Shot, Mote> seen = new IdentityHashMap<>();
        final List<Mote> motes = new ArrayList<>();

        Log(GameTestHelper helper, GrandOrrery o) {
            helper.onEachTick(() -> {
                for (Shot s : o.motes()) {
                    Mote m = seen.get(s);
                    if (m == null) {
                        m = new Mote();
                        m.step = (Step) o.phase();
                        m.tick = o.core().elapsed();
                        m.first = helper.relativeVec(s.pos());
                        seen.put(s, m);
                        motes.add(m);
                    } else if (m.second == null) {
                        m.second = helper.relativeVec(s.pos());
                    }
                    m.last = helper.relativeVec(s.pos());
                }
            });
        }

        List<Mote> of(Step step) {
            return motes.stream().filter(m -> m.step == step && m.second != null).toList();
        }
    }

    /** Counts the ticks an orrery spends in each phase, and the order it went through them. */
    private static final class Phases {
        final List<Step> order = new ArrayList<>();
        final List<Integer> lengths = new ArrayList<>();

        Phases(GameTestHelper helper, GrandOrrery o) {
            helper.onEachTick(() -> {
                Step now = (Step) o.phase();
                if (order.isEmpty() || order.getLast() != now) {
                    order.add(now);
                    lengths.add(0);
                }
                lengths.set(lengths.size() - 1, lengths.getLast() + 1);
            });
        }

        /** How long the n-th run of {@code step} lasted, once it is over; -1 if it hasn't run and ended. */
        int length(Step step, int n) {
            for (int i = 0, k = 0; i < order.size() - 1; i++) {
                if (order.get(i) == step && k++ == n) return lengths.get(i);
            }
            return -1;
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The spin-up lasts exactly 20 ticks: it looses nothing and hurts no one, its rings are lit (glow stage 2) and its
     * core is not open; then the spiral follows and motes fly.
     */
    private static void tell(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        Pig pig = pig(helper, TARGET, 100.0F);
        int[] ticks = {0};
        boolean[] started = {false}, quiet = {true}, lit = {true}, ended = {false};
        helper.onEachTick(() -> {
            if (!started[0]) {                                                   // started here, so its first tick counts too
                started[0] = true;
                o.setNextPattern(Step.SPIRAL);
                helper.assertTrue(o.spinUp(pig), "it spins up");
            }
            if (o.core().in(Step.SPIN)) {
                ticks[0]++;
                if (o.fired() > 0 || pig.getHealth() < pig.getMaxHealth()) quiet[0] = false;
                lit[0] &= o.core().stage() == GrandOrrery.STAGE_LIT && !o.vulnerable();
            } else if (ticks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the spin-up runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(ticks[0] == 20, "the spin-up lasts 20 ticks, lasted " + ticks[0]);
            helper.assertTrue(quiet[0], "nothing is loosed and nothing hurt during the spin-up");
            helper.assertTrue(lit[0], "its rings are lit and its core shut all through the spin-up");
            helper.assertTrue(o.core().in(Step.SPIRAL) && o.fired() > 0, "the spiral follows, phase " + o.phase() + ", fired " + o.fired());
        });
    }

    /**
     * The payoff: after a pattern the core opens for exactly 60 ticks, gold (vulnerable) with every other glow dark and
     * armour 0 the whole time, and a blow of 10 takes 20. Then it shuts: idle, faintly lit, armour 10, and the same blow
     * takes 8.
     */
    private static void counter(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        Pig pig = pig(helper, TARGET, 1000.0F);
        Phases phases = new Phases(helper, o);
        int[] open = {0};
        boolean[] gold = {true}, struck = {false}, shut = {false};
        helper.runAtTickTime(1, () -> {
            o.setNextPattern(Step.DOUBLE_RING);
            helper.assertTrue(o.spinUp(pig), "it spins up");
        });
        helper.onEachTick(() -> {
            if (o.core().in(Step.OPEN)) {
                open[0]++;
                gold[0] &= o.vulnerable() && o.core().stage() == GrandOrrery.STAGE_DARK && o.getArmorValue() == 0;
                if (o.core().elapsed() == 10) {
                    float before = o.getHealth();
                    o.damageCooldownTime = 0;
                    helper.assertTrue(o.hurtServer(helper.getLevel(), o.damageSources().mobAttack(pig), 10.0F), "the blow lands");
                    float taken = before - o.getHealth();
                    helper.assertTrue(Math.abs(taken - 20.0F) < 0.01F, "open, a blow of 10 takes 20, took " + taken);
                    struck[0] = true;
                }
            } else if (open[0] > 0 && !shut[0]) {
                shut[0] = true;
                helper.assertTrue(o.core().idle() && !o.vulnerable(), "shut, it is idle and shows no gold, phase " + o.phase());
                helper.assertTrue(o.core().stage() == GrandOrrery.STAGE_REST, "its core glows star-white again, stage " + o.core().stage());
                helper.assertTrue(o.getArmorValue() == 10, "shut, armour 10, got " + o.getArmorValue());
                float before = o.getHealth();
                o.damageCooldownTime = 0;
                helper.assertTrue(o.hurtServer(helper.getLevel(), o.damageSources().mobAttack(pig), 10.0F), "the blow lands");
                float taken = before - o.getHealth();
                helper.assertTrue(Math.abs(taken - 8.0F) < 0.01F, "shut, a blow of 10 takes 8, took " + taken);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shut[0], "the core opened and shut, " + open[0] + " ticks open so far");
            helper.assertTrue(phases.order.subList(0, 4).equals(List.of(Step.IDLE, Step.SPIN, Step.DOUBLE_RING, Step.OPEN)),
                    "it opens straight after the pattern: " + phases.order);
            helper.assertTrue(open[0] == 60, "the core stays open 60 ticks, stayed " + open[0]);
            helper.assertTrue(gold[0], "gold, every other glow dark and armour 0 the whole time");
            helper.assertTrue(struck[0], "the blow was struck while it was open");
        });
    }

    /**
     * Failing it: a survival player standing on the line of the spiral's first mote, 5 blocks out, is struck once for
     * exactly one mote (6 on Normal, as the game scales mob projectiles) and not moved at all, and no other mote of
     * the turn touches them.
     */
    private static void failure(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        ServerPlayer p = TestPlayers.survivor(helper, out(HANG, -90.0, 5.0));
        float each = moteHere(helper);
        float[] health = {20.0F};
        Vec3[] start = {null};
        double[] drift = {0.0};
        helper.runAtTickTime(1, () -> {
            health[0] = p.getHealth();
            start[0] = p.position();
            o.setNextPattern(Step.SPIRAL);
            helper.assertTrue(o.spinUp(p), "it spins up");
        });
        helper.onEachTick(() -> {
            if (start[0] != null) drift[0] = Math.max(drift[0], p.position().subtract(start[0]).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(o.core().in(Step.OPEN) && o.motes().isEmpty(), "the spiral is over and every mote spent, phase " + o.phase());
            helper.assertTrue(o.fired() == 24, "the whole spiral flew, " + o.fired());
            helper.assertTrue(o.strikes() == 1, "one mote struck the player, " + o.strikes());
            helper.assertTrue(Math.abs(lost(p, health[0]) - each) < 0.01F, "the mote costs " + each + " here, cost " + lost(p, health[0]));
            helper.assertTrue(drift[0] < 0.01, "and doesn't move the player, moved " + drift[0]);
        });
    }

    // ---- the patterns ----------------------------------------------------------------------------------------------

    /**
     * The spiral: 24 motes over exactly 40 ticks, the first on the spiral's first tick and straight at the target, the
     * last on tick 38, each 15 degrees on from the one before (one turn round) and flying 0.25 blocks a tick.
     */
    private static void spiral(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        Pig pig = pig(helper, TARGET, 1000.0F);
        Log log = new Log(helper, o);
        Phases phases = new Phases(helper, o);
        helper.runAtTickTime(1, () -> {
            o.setNextPattern(Step.SPIRAL);
            helper.assertTrue(o.spinUp(pig), "it spins up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(phases.length(Step.SPIRAL, 0) >= 0, "the spiral ran and ended, phase " + o.phase());
            helper.assertTrue(phases.length(Step.SPIRAL, 0) == 40, "the spiral lasts 40 ticks, lasted " + phases.length(Step.SPIRAL, 0));
            List<Log.Mote> m = log.of(Step.SPIRAL);
            helper.assertTrue(m.size() == 24, "24 motes, saw " + m.size());
            helper.assertTrue(m.getFirst().tick == 0 && m.getLast().tick == 38, "from tick 0 to tick 38, " + m.getFirst().tick + " to " + m.getLast().tick);
            double toPig = yawOf(m.getFirst().first, TARGET);
            helper.assertTrue(Math.abs(Mth.wrapDegrees(m.getFirst().yaw() - toPig)) < 1.0, "the first straight at the pig, off by " + Mth.wrapDegrees(m.getFirst().yaw() - toPig));
            for (int i = 1; i < m.size(); i++) {
                double step = Mth.wrapDegrees(m.get(i).yaw() - m.get(i - 1).yaw());
                helper.assertTrue(Math.abs(step - 15.0) < 0.5, "each 15 degrees on, mote " + i + " is " + step);
                helper.assertTrue(m.get(i).tick - m.get(i - 1).tick <= 2, "one every 1 or 2 ticks");
            }
            for (Log.Mote mote : m) helper.assertTrue(Math.abs(mote.speed() - 0.25) < 0.005, "0.25 blocks a tick, " + mote.speed());
        });
    }

    /**
     * The double ring: 24 motes on the pattern's first tick, 12 at 0.25 blocks a tick heading 0, 30, 60... degrees
     * from the aim (one straight at the target), and 12 at 0.15 heading 15, 45, 75...: the second ring in the first
     * one's gaps. The pattern lasts 20 ticks.
     */
    private static void doubleRing(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        Pig pig = pig(helper, TARGET, 1000.0F);
        Log log = new Log(helper, o);
        Phases phases = new Phases(helper, o);
        helper.runAtTickTime(1, () -> {
            o.setNextPattern(Step.DOUBLE_RING);
            helper.assertTrue(o.spinUp(pig), "it spins up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(phases.length(Step.DOUBLE_RING, 0) == 20, "the double ring lasts 20 ticks, lasted " + phases.length(Step.DOUBLE_RING, 0));
            List<Log.Mote> m = log.of(Step.DOUBLE_RING);
            helper.assertTrue(m.size() == 24 && m.stream().allMatch(x -> x.tick == 0), "24 motes, all on its first tick: " + m.size());
            double aim = yawOf(m.getFirst().first, TARGET);
            int fast = 0, slow = 0;
            for (Log.Mote x : m) {
                double off = ((x.yaw() - aim) % 360.0 + 360.0) % 360.0;
                if (Math.abs(x.speed() - 0.25) < 0.005) {
                    fast++;
                    double r = off % 30.0;
                    helper.assertTrue(Math.min(r, 30.0 - r) < 0.5, "a fast mote on the first ring's 30-degree steps, at " + off);
                } else {
                    helper.assertTrue(Math.abs(x.speed() - 0.15) < 0.005, "the others at 0.15, " + x.speed());
                    slow++;
                    helper.assertTrue(Math.abs(off % 30.0 - 15.0) < 0.5, "a slow mote half a step round, at " + off);
                }
            }
            helper.assertTrue(fast == 12 && slow == 12, "12 of each, " + fast + " fast and " + slow + " slow");
        });
    }

    /**
     * The aimed burst: three volleys on ticks 0, 6 and 12, each three motes heading 20 degrees left of the aim, at it,
     * and 20 right; the pig is moved after the first volley and the later volleys still go down the same lanes. The
     * pattern lasts 20 ticks.
     */
    private static void burst(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        Pig pig = pig(helper, TARGET, 1000.0F);
        Log log = new Log(helper, o);
        Phases phases = new Phases(helper, o);
        boolean[] moved = {false};
        helper.runAtTickTime(1, () -> {
            o.setNextPattern(Step.BURST);
            helper.assertTrue(o.spinUp(pig), "it spins up");
        });
        helper.onEachTick(() -> {
            if (!moved[0] && o.core().in(Step.BURST) && o.fired() >= 3) {
                moved[0] = true;
                Vec3 to = helper.absoluteVec(new Vec3(9.5, 1, 8.5));
                pig.teleportTo(to.x, to.y, to.z);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(phases.length(Step.BURST, 0) == 20, "the burst lasts 20 ticks, lasted " + phases.length(Step.BURST, 0));
            List<Log.Mote> m = log.of(Step.BURST);
            helper.assertTrue(m.size() == 9, "nine motes, saw " + m.size());
            double aim = yawOf(m.get(1).first, TARGET);
            for (int v = 0; v < 3; v++) {
                for (int i = 0; i < 3; i++) {
                    Log.Mote x = m.get(v * 3 + i);
                    helper.assertTrue(x.tick == 6 * v, "volley " + v + " on tick " + 6 * v + ", saw " + x.tick);
                    double off = Mth.wrapDegrees(x.yaw() - aim);
                    helper.assertTrue(Math.abs(off - (-20.0 + 20.0 * i)) < 0.5, "volley " + v + " lane " + i + " at " + off);
                }
            }
        });
    }

    /**
     * A player standing still in a gap is never hit: with the orrery aiming at a pig 6.5 blocks off, a survival player 5
     * blocks out in the gap {@code gapYaw} degrees round from the aim loses nothing through the whole pattern, while one
     * as far out on a mote's line {@code rayYaw} degrees round is struck, so the motes do reach that far, at that height.
     */
    private static void gap(GameTestHelper helper, Step pattern, double gapYaw, double rayYaw) {
        GrandOrrery o = hanging(helper);
        Pig pig = pig(helper, TARGET, 1000.0F);
        ServerPlayer inGap = TestPlayers.survivor(helper, out(HANG, -90.0 + gapYaw, 5.0));
        ServerPlayer onLine = TestPlayers.survivor(helper, out(HANG, -90.0 + rayYaw, 5.0));
        float each = moteHere(helper);
        float[] health = {20.0F, 20.0F};
        boolean[] cast = {false};
        helper.runAtTickTime(1, () -> {
            health[0] = inGap.getHealth();
            health[1] = onLine.getHealth();
            o.setNextPattern(pattern);
            helper.assertTrue(o.spinUp(pig), "it spins up");
        });
        helper.onEachTick(() -> cast[0] |= o.core().in(pattern));
        helper.succeedWhen(() -> {
            helper.assertTrue(cast[0] && o.core().in(Step.OPEN) && o.motes().isEmpty(), "the pattern is over and every mote spent, phase " + o.phase());
            helper.assertTrue(lost(inGap, health[0]) == 0.0F, "standing in the gap, the player loses nothing, lost " + lost(inGap, health[0]));
            helper.assertTrue(lost(onLine, health[1]) >= each - 0.01F, "on a mote's line, the player is struck, lost " + lost(onLine, health[1]));
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "and the pig it aimed at is struck");
        });
    }

    /**
     * Between the Stars: a double ring aimed at a survival player 5 blocks out. {@code step}: once the motes have left,
     * the player steps 7.5 degrees round into the gap between two lanes and stands there; untouched, they earn the
     * advancement once the last mote is spent, not before. Otherwise they stand where it aimed, the aimed mote strikes
     * them, and they earn nothing.
     */
    private static void feat(GameTestHelper helper, boolean step) {
        GrandOrrery o = hanging(helper);
        ServerPlayer p = TestPlayers.survivor(helper, out(HANG, -90.0, 5.0));
        float[] health = {20.0F};
        boolean[] stepped = {false}, early = {false};
        helper.runAtTickTime(1, () -> {
            health[0] = p.getHealth();
            o.setNextPattern(Step.DOUBLE_RING);
            helper.assertTrue(o.spinUp(p), "it spins up at the player");
        });
        helper.onEachTick(() -> {
            if (step && !stepped[0] && o.core().in(Step.DOUBLE_RING) && o.fired() > 0) {
                stepped[0] = true;
                p.snapTo(helper.absoluteVec(out(HANG, -90.0 + 7.5, 5.0)));
            }
            if (!o.motes().isEmpty() && AdvancementTests.done(helper, p, TeemingAdvancements.GRAND_ORRERY_PATTERN)) early[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(o.core().in(Step.OPEN) && o.motes().isEmpty(), "the pattern is over and every mote spent, phase " + o.phase());
            helper.assertTrue(o.fired() == 24, "the whole double ring flew, " + o.fired());
            if (step) {
                helper.assertTrue(stepped[0], "the player stepped aside");
                helper.assertTrue(lost(p, health[0]) == 0.0F, "in the gap nothing touches them, lost " + lost(p, health[0]));
                helper.assertTrue(AdvancementTests.done(helper, p, TeemingAdvancements.GRAND_ORRERY_PATTERN), "untouched, they earn Between the Stars");
                helper.assertTrue(!early[0], "not while motes were still flying");
            } else {
                helper.assertTrue(o.strikes() >= 1 && lost(p, health[0]) > 0.0F, "standing on the aim, they are struck, lost " + lost(p, health[0]));
                helper.assertTrue(!AdvancementTests.done(helper, p, TeemingAdvancements.GRAND_ORRERY_PATTERN), "struck, they earn nothing");
            }
        });
    }

    // ---- motes -----------------------------------------------------------------------------------------------------

    /**
     * A mote: flies 0.25 blocks a tick, strikes a pig for exactly 6 and doesn't move it (the pig keeps its physics), and
     * stops at a block: every mote of the burst's lane 20 degrees round from the aim ends at a stone pillar standing in
     * it a block and a half out, at the pillar's face and never past it.
     */
    private static void mote(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(4, y, 6), Blocks.STONE);   // in the lane 20 degrees round
        Pig pig = pig(helper, new Vec3(8.0, 1, 5.5), 100.0F);
        Log log = new Log(helper, o);
        Vec3 spot = pig.position();
        float[] first = {-1.0F};
        double[] drift = {0.0};
        helper.runAtTickTime(1, () -> {
            o.setNextPattern(Step.BURST);
            helper.assertTrue(o.spinUp(pig), "it spins up");
        });
        helper.onEachTick(() -> {
            drift[0] = Math.max(drift[0], pig.position().subtract(spot).horizontalDistance());
            if (first[0] < 0 && o.strikes() > 0) first[0] = 100.0F - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(o.core().in(Step.OPEN) && o.motes().isEmpty(), "the burst is over and every mote spent, phase " + o.phase());
            helper.assertTrue(Math.abs(first[0] - 6.0F) < 0.01F, "the first mote to land takes 6, took " + first[0]);
            helper.assertTrue(drift[0] < 0.01, "and doesn't knock the pig back, moved " + drift[0]);
            List<Log.Mote> m = log.of(Step.BURST);
            helper.assertTrue(m.size() == 9, "nine motes, saw " + m.size());
            double aim = yawOf(m.get(1).first, pig.position().subtract(helper.absoluteVec(Vec3.ZERO)));
            for (Log.Mote x : m) {
                helper.assertTrue(Math.abs(x.speed() - 0.25) < 0.005, "0.25 blocks a tick, " + x.speed());
                if (Mth.wrapDegrees(x.yaw() - aim) > 10.0) {
                    helper.assertTrue(x.last.z <= 6.01, "that lane's mote stops at the pillar's face, last seen at " + x.last);
                }
            }
        });
    }

    /** A survival player holding up a shield toward the orrery takes nothing from the spiral mote that reaches them. */
    private static void shield(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        ServerPlayer p = TestPlayers.survivor(helper, out(HANG, -90.0, 5.0));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            p.setYRot(90.0F);            // facing -x, toward the orrery
            p.setYHeadRot(90.0F);
            p.yHeadRotO = 90.0F;
        });
        float before = p.getHealth();
        helper.runAtTickTime(1, () -> {
            o.setNextPattern(Step.SPIRAL);
            helper.assertTrue(o.spinUp(p), "it spins up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(o.core().in(Step.OPEN) && o.motes().isEmpty(), "the spiral is over, phase " + o.phase());
            helper.assertTrue(p.isBlocking(), "the player holds the shield up");
            helper.assertTrue(o.strikes() == 1, "a mote reached the player, " + o.strikes());
            helper.assertTrue(p.getHealth() >= before, "the shield took it, health " + p.getHealth());
        });
    }

    // ---- the open core, the cycle, flight --------------------------------------------------------------------------

    /** Armour 0 and damage doubled only while open: a blow of 10 takes 8 at rest and in the spin-up, 20 open. */
    private static void armor(GameTestHelper helper) {
        GrandOrrery rest = orrery(helper, new Vec3(3.0, 1, 3.0), true);
        GrandOrrery spinning = orrery(helper, new Vec3(9.0, 1, 3.0), true);
        GrandOrrery open = orrery(helper, new Vec3(6.0, 1, 9.0), true);
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5), 100.0F);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(spinning.spinUp(null), "the second spins up");
            helper.assertTrue(open.startPhase(Step.OPEN, 60, null), "the third opens");
            int[] want = {10, 10, 0};
            float[] takes = {8.0F, 8.0F, 20.0F};
            GrandOrrery[] all = {rest, spinning, open};
            for (int i = 0; i < 3; i++) {
                GrandOrrery o = all[i];
                helper.assertTrue(o.getArmorValue() == want[i], o.phase() + ": armour " + want[i] + ", got " + o.getArmorValue());
                float before = o.getHealth();
                o.hurtServer(helper.getLevel(), o.damageSources().mobAttack(pig), 10.0F);
                float took = before - o.getHealth();
                helper.assertTrue(Math.abs(took - takes[i]) < 0.01F, o.phase() + ": a blow of 10 takes " + takes[i] + ", took " + took);
            }
            helper.succeed();
        });
    }

    /**
     * The cycle with its own wits: with a target in sight it spins up at once, casts the spiral, opens for 60, rests 10
     * ticks, then spins up for the double ring, opens, rests, and spins up for the burst: 20, 40, 60, 10, 20, 20, 60, 10,
     * 20 ticks. The pig it aims at is too sturdy to die.
     */
    private static void cycle(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        o.setNextPattern(Step.SPIRAL);
        Pig pig = pig(helper, TARGET, 5000.0F);
        Phases phases = new Phases(helper, o);
        helper.runAtTickTime(1, () -> {
            o.setTarget(pig);
            o.holdFire(false);
        });
        helper.succeedWhen(() -> {
            List<Step> want = List.of(Step.IDLE, Step.SPIN, Step.SPIRAL, Step.OPEN, Step.IDLE, Step.SPIN, Step.DOUBLE_RING, Step.OPEN,
                    Step.IDLE, Step.SPIN, Step.BURST, Step.OPEN);
            helper.assertTrue(phases.order.size() >= want.size(), "the cycle so far: " + phases.order);
            helper.assertTrue(phases.order.subList(0, want.size()).equals(want), "in turn: " + phases.order);
            helper.assertTrue(phases.lengths.getFirst() <= 3, "with its target in sight it spins up at once, after " + phases.lengths.getFirst());
            List<Integer> lengths = phases.lengths.subList(1, want.size() - 1);
            helper.assertTrue(lengths.equals(List.of(20, 40, 60, 10, 20, 20, 60, 10, 20, 20)), "phase lengths " + lengths);
        });
    }

    /**
     * Open, it sinks: hanging with its feet 3 over its target's, it comes straight down to 0.2 over them within 20
     * ticks of opening and stays there, never sliding sideways; once shut it climbs back toward its station.
     */
    private static void sink(GameTestHelper helper) {
        GrandOrrery o = orrery(helper, new Vec3(8.5, 4, 6.5), false);
        Pig pig = pig(helper, new Vec3(3.5, 1, 6.5), 100.0F);
        int[] down = {-1};
        double[] drift = {0.0}, low = {9.0};
        boolean[] held = {true}, shut = {false};
        Vec3[] start = {null};
        double[] climbed = {0.0};
        helper.runAtTickTime(1, () -> {
            start[0] = o.position();
            o.setTarget(pig);
            helper.assertTrue(o.startPhase(Step.OPEN, 60, pig), "it opens");
        });
        helper.onEachTick(() -> {
            if (start[0] == null) return;
            double feet = o.getY() - pig.getY();
            if (o.core().in(Step.OPEN)) {
                drift[0] = Math.max(drift[0], o.position().subtract(start[0]).horizontalDistance());
                low[0] = Math.min(low[0], feet);
                if (down[0] < 0 && Math.abs(feet - 0.2) < 0.01) down[0] = o.core().elapsed();
                if (down[0] >= 0 && Math.abs(feet - 0.2) > 0.01) held[0] = false;
            } else if (o.core().idle()) {
                shut[0] = true;
                climbed[0] = Math.max(climbed[0], feet);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(down[0] >= 0 && down[0] <= 20, "down to 0.2 over the pig within 20 ticks, took " + down[0]);
            helper.assertTrue(low[0] > 0.19, "and no lower, lowest " + low[0]);
            helper.assertTrue(held[0], "it stays down while open");
            helper.assertTrue(drift[0] < 0.05, "straight down, slid " + drift[0]);
            helper.assertTrue(shut[0] && climbed[0] > 1.5, "shut, it climbs back toward its station, up " + climbed[0]);
        });
    }

    /**
     * It keeps its distance: from 3 blocks off its target it backs away to its station, 7 blocks out across the ground
     * with its feet 3 over the pig's.
     */
    private static void keepsDistance(GameTestHelper helper) {
        GrandOrrery o = orrery(helper, new Vec3(6.5, 2, 6.5), false);
        Pig pig = pig(helper, new Vec3(3.5, 1, 6.5), 100.0F);
        helper.runAtTickTime(1, () -> o.setTarget(pig));
        helper.succeedWhen(() -> {
            double across = o.position().subtract(pig.position()).horizontalDistance();
            double up = o.getY() - pig.getY();
            helper.assertTrue(Math.abs(across - 7.0) < 0.3, "7 blocks out across the ground, " + across);
            helper.assertTrue(Math.abs(up - 3.0) < 0.3, "its feet 3 over the pig's, " + up);
        });
    }

    /** Killed 10 ticks into the spiral, it looses no more motes and never opens. */
    private static void diesMidPattern(GameTestHelper helper) {
        GrandOrrery o = hanging(helper);
        Pig pig = pig(helper, TARGET, 1000.0F);
        Phases phases = new Phases(helper, o);
        long[] killed = {-1};
        int[] firedThen = {-1};
        helper.runAtTickTime(1, () -> {
            o.setNextPattern(Step.SPIRAL);
            helper.assertTrue(o.spinUp(pig), "it spins up");
        });
        helper.onEachTick(() -> {
            if (killed[0] < 0 && o.core().in(Step.SPIRAL) && o.core().elapsed() == 10) {
                o.kill(helper.getLevel());
                killed[0] = helper.getTick();
                firedThen[0] = o.fired();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getTick() >= killed[0] + 40, "killed mid-spiral, then 40 ticks");
            helper.assertTrue(firedThen[0] > 0 && firedThen[0] < 24, "it had loosed part of the spiral, " + firedThen[0]);
            helper.assertTrue(o.fired() == firedThen[0], "and loosed nothing after it died: " + firedThen[0] + " then, " + o.fired() + " now");
            helper.assertTrue(!phases.order.contains(Step.OPEN), "it never opened: " + phases.order);
        });
    }

    // ---- stats, home, cap ------------------------------------------------------------------------------------------

    /** Health 300, attack 6, armour 10, a 2.0 x 2.0 hitbox, 100 experience; blows don't move it; it floats; faintly lit at rest. */
    private static void stats(GameTestHelper helper) {
        GrandOrrery o = orrery(helper, new Vec3(6.5, 2, 6.5), true);
        Pig pig = pig(helper, new Vec3(2.5, 1, 2.5), 10.0F);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(o.getMaxHealth() - 300.0F) < 0.01F && Math.abs(o.getHealth() - 300.0F) < 0.01F,
                    "health 300, got " + o.getHealth() + "/" + o.getMaxHealth());
            helper.assertTrue(Math.abs(o.getAttributeValue(Attributes.ATTACK_DAMAGE) - 6.0) < 0.01, "attack 6 a mote, got " + o.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(Math.abs(o.getAttributeValue(Attributes.ARMOR) - 10.0) < 0.01 && o.getArmorValue() == 10, "armour 10, got " + o.getArmorValue());
            helper.assertTrue(Math.abs(o.getBbWidth() - 2.0F) < 0.02F && Math.abs(o.getBbHeight() - 2.0F) < 0.02F,
                    "hitbox 2.0 x 2.0, got " + o.getBbWidth() + " x " + o.getBbHeight());
            int xp = o.getExperienceReward(helper.getLevel(), pig);
            helper.assertTrue(xp == 100, "100 experience, got " + xp);
            helper.assertTrue(o.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "blows never move it");
            helper.assertTrue(o.isNoGravity(), "it floats");
            helper.assertTrue(!o.vulnerable() && o.core().stage() == GrandOrrery.STAGE_REST, "shut and faintly lit at rest, stage " + o.core().stage());
            helper.succeed();
        });
    }

    /**
     * Home: the open sky off the End's main island, placed only by the Teeming spawner's apex sky rule (after a won apex
     * roll, none of its kind near); on no biome list, so no biome modifier, and its biome tag holds the outer End
     * biomes; alone (groups of 1), weight 0; an apex, so its local cap is one in 128 blocks.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("grand_orrery");
        helper.assertTrue(row.apex() && row.dim() == RosterStats.Dim.END, "an End apex");
        Rule rule = Rule.of(row.spawner());
        helper.assertTrue(rule == Rule.APEX_SKY && rule.apex() && rule.dimension == Level.END, "the apex sky rule places it, got '" + row.spawner() + "'");
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("grand_orrery"));
        helper.assertTrue(tag.isPresent() && tag.get().size() == 4, "its biome tag holds the four outer End biomes");
        helper.assertTrue(!row.biomeSpawns(), "on no biome list");
        helper.assertTrue(access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/grand_orrery"))).isEmpty(), "no biome modifier");
        helper.assertTrue(row.weight() == 0, "weight 0, got " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "alone, got " + row.groupMin() + ".." + row.groupMax());
        helper.assertTrue(TeemingSpawns.RARE_CAP_RADIUS == 128, "an apex's cap reaches 128 blocks, got " + TeemingSpawns.RARE_CAP_RADIUS);
        helper.succeed();
    }

    private static boolean fits(GameTestHelper helper, BlockPos rel) {
        return Rule.APEX_SKY.fits(helper.getLevel(), helper.absolutePos(rel));
    }

    /**
     * Its spawn rule takes open air under the sky with air above and below, and refuses a spot on the floor, a spot
     * right under a block, and a spot whose sky a roof higher up shuts out (checked once the sky light has caught up
     * with the roof).
     */
    private static void spawnRule(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 5, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(9, 7, 9), Blocks.STONE);
        helper.assertTrue(!fits(helper, new BlockPos(6, 1, 6)), "refuses a spot on the floor");
        helper.assertTrue(!fits(helper, new BlockPos(3, 4, 3)), "refuses a spot right under a block");
        helper.succeedWhen(() -> {
            helper.assertTrue(fits(helper, new BlockPos(6, 4, 6)), "takes open air under the sky");
            helper.assertTrue(!fits(helper, new BlockPos(9, 4, 9)), "refuses a spot under a roof three blocks up");
        });
    }

    /**
     * Local cap: one in 128 blocks. With one here the natural-spawn predicate refuses a second 127 blocks above it and
     * allows one 140 above (its 2-block body, and every arena's roof, then clear the 128-block box). Straight up, so no
     * other arena's mobs are in either box, however high they hover under their roofs.
     */
    private static void cap(GameTestHelper helper) {
        GrandOrrery o = orrery(helper, new Vec3(6.5, 1, 6.5), true);
        o.setNoAi(true);
        RosterStats.Entry row = RosterStats.get("grand_orrery");
        BlockPos at = helper.absolutePos(new BlockPos(6, 1, 6));
        helper.runAtTickTime(2, () -> {
            var type = ModMobs.GRAND_ORRERY.get();
            helper.assertTrue(GrandOrrery.CAP_RADIUS == TeemingSpawns.RARE_CAP_RADIUS, "its cap is the apex cap");
            helper.assertTrue(!TeemingSpawns.underCap(row, type, helper.getLevel(), at), "a second beside it is refused");
            helper.assertTrue(!TeemingSpawns.underCap(row, type, helper.getLevel(), at.above(127)), "a second 127 blocks off is refused");
            helper.assertTrue(TeemingSpawns.underCap(row, type, helper.getLevel(), at.above(140)), "a second 140 blocks off may spawn");
            helper.succeed();
        });
    }
}
