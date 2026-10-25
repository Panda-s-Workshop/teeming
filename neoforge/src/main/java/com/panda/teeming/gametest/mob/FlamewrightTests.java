package com.panda.teeming.gametest.mob;

import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.nether.Flamewright;
import com.panda.teeming.mob.nether.Flamewright.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Flamewright: a 20-tick wind-up that casts nothing yet; then a spiral of 12 fireballs over 30 ticks, a ring of 16 at
 * once or a cross of 4 arms of 3, in turn; fireballs that fly straight at 0.25 blocks a tick, burn for 4, stop at
 * blocks, set nothing alight and are taken by a shield; a hit in the wind-up staggers it for 40 ticks and the pattern
 * is lost. Every number is checked against a plain value.
 *
 * <p>Every arena here is walled at its edge, three blocks high, so no fireball leaves it. Every Flamewright has its
 * goals cleared and a follow range of 1, so it never notices a survival player in a neighbouring arena; it is either
 * given its target or told to wind up directly. Pigs have their goals cleared, so they stand still.
 */
public final class FlamewrightTests {
    private FlamewrightTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("flamewright_tell", FlamewrightTests::tell);
        out.accept("flamewright_counter", FlamewrightTests::hitStaggers);
        TeemingGameTests.timeLimit("flamewright_counter", 120);
        out.accept("flamewright_failure", FlamewrightTests::burned);
        out.accept("flamewright_spiral", FlamewrightTests::spiral);
        out.accept("flamewright_ring", FlamewrightTests::ring);
        out.accept("flamewright_cross", FlamewrightTests::cross);
        out.accept("flamewright_in_turn", FlamewrightTests::inTurn);
        TeemingGameTests.timeLimit("flamewright_in_turn", 140);
        out.accept("flamewright_speed", FlamewrightTests::speed);
        out.accept("flamewright_ring_gap", FlamewrightTests::ringGap);
        out.accept("flamewright_stops_at_blocks", FlamewrightTests::stopsAtBlocks);
        out.accept("flamewright_shield", FlamewrightTests::shield);
        out.accept("flamewright_hit_outside_windup", FlamewrightTests::hitOutsideWindUp);
        out.accept("flamewright_stats", FlamewrightTests::stats);
        out.accept("flamewright_home", FlamewrightTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Stone round the arena's edge cells, three high: every fireball stops inside the arena. */
    private static void walls(GameTestHelper helper) {
        for (int y = 1; y <= 3; y++) {
            for (int i = 0; i < 12; i++) {
                helper.setBlock(new BlockPos(i, y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(i, y, 11), Blocks.STONE);
                helper.setBlock(new BlockPos(0, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(11, y, i), Blocks.STONE);
            }
        }
    }

    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.removeFreeWill();
        return pig;
    }

    /** A walled arena and a Flamewright with no goals and a follow range of 1, hunting {@code target} (or no one). */
    private static Flamewright wright(GameTestHelper helper, double x, double z, @Nullable LivingEntity target) {
        walls(helper);
        Flamewright w = helper.spawn(ModMobs.FLAMEWRIGHT.get(), new Vec3(x, 1, z));
        w.removeFreeWill();
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        w.setTarget(target);
        TestPlayers.whenDone(helper, w::discard);
        return w;
    }

    /** Turns it to face along {@code yaw} (a mob's yaw: -90 is +X). */
    private static void face(Flamewright w, float yaw) {
        w.setYRot(yaw);
        w.setYBodyRot(yaw);
        w.setYHeadRot(yaw);
    }

    /** The yaw from the Flamewright's middle to {@code p}, as a mob's yaw. */
    private static double yawTo(Flamewright w, Vec3 p) {
        return Mth.atan2(p.z - w.getZ(), p.x - w.getX()) * Mth.RAD_TO_DEG - 90.0;
    }

    /** {@code a - b} in degrees, folded into -180..180. */
    private static double diff(double a, double b) {
        return Mth.wrapDegrees(a - b);
    }

    /** Records each mechanic step of one Flamewright by test tick, and each new fireball with the tick it first flew. */
    private static final class Log implements Consumer<MechanicEvent> {
        final Flamewright w;
        final Map<String, List<Integer>> steps = new java.util.HashMap<>();
        final Map<Shot, Integer> seen = new IdentityHashMap<>();
        final List<Shot> shots = new ArrayList<>();
        final List<Integer> firstFlew = new ArrayList<>();
        int tick;

        Log(GameTestHelper helper, Flamewright w) {
            this.w = w;
            MechanicEvent.listen(this);
            TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(this));
            helper.onEachTick(() -> {
                tick++;
                for (Shot s : w.fireballs()) {
                    if (seen.putIfAbsent(s, tick) == null) {
                        shots.add(s);
                        firstFlew.add(tick);
                    }
                }
            });
        }

        @Override
        public void accept(MechanicEvent e) {
            if (e.mob() == w) steps.computeIfAbsent(e.step(), k -> new ArrayList<>()).add(tick);
        }

        List<Integer> of(String step) {
            return steps.getOrDefault(step, List.of());
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The wind-up lasts exactly 20 ticks: it stands still with the sigil showing gold (vulnerable), casts nothing and
     * hurts no one; then a pattern follows and fireballs fly.
     */
    private static void tell(GameTestHelper helper) {
        Pig pig = pig(helper, 8.5, 5.5);
        Flamewright w = wright(helper, 3.5, 5.5, pig);
        Log log = new Log(helper, w);
        int[] ticks = {0};
        boolean[] quiet = {true}, gold = {true}, ended = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (w.core().in(Step.WINDUP)) {
                if (start[0] == null) start[0] = w.position();
                ticks[0]++;
                moved[0] = Math.max(moved[0], w.position().subtract(start[0]).horizontalDistance());
                gold[0] &= w.vulnerable();
                if (w.fired() > 0 || pig.getHealth() < pig.getMaxHealth()) quiet[0] = false;
            } else if (ticks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the wind-up runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(log.of("windup").size() == 1 && log.of("windup").getFirst() <= 3, "with its target 5 blocks off it winds up at once: " + log.steps);
            helper.assertTrue(ticks[0] == 20, "the wind-up lasts 20 ticks, lasted " + ticks[0]);
            helper.assertTrue(quiet[0], "nothing is cast and nothing hurt during the wind-up");
            helper.assertTrue(gold[0], "the sigil shows gold all through the wind-up");
            helper.assertTrue(moved[0] < 0.05, "it stands still while it winds up, moved " + moved[0]);
            helper.assertTrue(w.fired() > 0, "a pattern follows the wind-up, fired " + w.fired());
            helper.assertTrue(!w.vulnerable(), "and the gold goes once it casts");
        });
    }

    /**
     * The counter: a hit 10 ticks into the wind-up staggers it for exactly 40 ticks and the ring it was drawing is lost:
     * no fireball flies, and the next wind-up draws the cross, the pattern after the ring.
     */
    private static void hitStaggers(GameTestHelper helper) {
        Pig pig = pig(helper, 8.5, 8.5);
        Flamewright w = wright(helper, 3.5, 5.5, null);
        Log log = new Log(helper, w);
        int[] staggered = {0}, firedAfterStagger = {-1};
        boolean[] wasStaggered = {false};
        helper.runAtTickTime(1, () -> {
            w.setNextPattern(Step.RING);
            helper.assertTrue(w.windUp(pig), "it winds up");
            helper.assertTrue(w.drawing() == Step.RING, "drawing the ring, " + w.drawing());
        });
        helper.onEachTick(() -> {
            if (w.core().in(Step.WINDUP) && w.core().elapsed() == 10 && log.of("stagger").isEmpty()) {
                helper.assertTrue(w.hurtServer(helper.getLevel(), w.damageSources().mobAttack(pig), 1.0F), "the hit lands");
                helper.assertTrue(w.core().in(Step.STAGGER), "a hit in the wind-up staggers it, phase " + w.phase());
            }
            if (w.core().in(Step.STAGGER)) {
                staggered[0]++;
                wasStaggered[0] = true;
            } else if (wasStaggered[0] && firedAfterStagger[0] < 0) {
                firedAfterStagger[0] = w.fired();
                helper.assertTrue(w.nextPattern() == Step.CROSS, "the ring is lost: the next pattern is the cross, " + w.nextPattern());
                helper.assertTrue(w.windUp(pig), "it winds up again");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(staggered[0] == 40, "the stagger lasts 40 ticks, lasted " + staggered[0]);
            helper.assertTrue(firedAfterStagger[0] == 0, "no fireball flew from the broken wind-up, fired " + firedAfterStagger[0]);
            helper.assertTrue(log.of("ring").isEmpty(), "the ring was never cast: " + log.steps);
            helper.assertTrue(log.of("cross").size() == 1 && w.fired() == 12, "the next cast is the cross, fired " + w.fired());
        });
    }

    /** Failing the counter: a ring is cast at the pig and the fireball aimed at it burns it for exactly 4, once. */
    private static void burned(GameTestHelper helper) {
        Pig pig = pig(helper, 8.5, 5.5);
        Flamewright w = wright(helper, 3.5, 5.5, null);
        Log log = new Log(helper, w);
        float before = pig.getHealth();
        helper.runAtTickTime(1, () -> {
            w.setNextPattern(Step.RING);
            helper.assertTrue(w.windUp(pig), "it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.of("fireball_hit").size() == 1, "one fireball reached the pig: " + log.steps);
            helper.assertTrue(Math.abs(before - pig.getHealth() - 4.0F) < 0.01F, "a fireball burns for 4, the pig lost " + (before - pig.getHealth()));
            helper.assertTrue(pig.getRemainingFireTicks() <= 0, "and it is not set alight");
        });
    }

    // ---- the patterns ----------------------------------------------------------------------------------------------

    /** The spiral: 12 fireballs, one after another over the 30-tick cast, each 30 degrees on from the last. */
    private static void spiral(GameTestHelper helper) {
        Flamewright w = wright(helper, 5.5, 5.5, null);
        Log log = new Log(helper, w);
        int[] casting = {0};
        helper.runAtTickTime(1, () -> {
            w.setNextPattern(Step.SPIRAL);
            helper.assertTrue(w.windUp(null), "it winds up");
        });
        helper.onEachTick(() -> {
            if (w.core().in(Step.SPIRAL)) casting[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(w.fired() == 12 && log.shots.size() == 12, "12 fireballs, fired " + w.fired() + ", seen " + log.shots.size());
            helper.assertTrue(casting[0] == 30 && w.core().idle(), "the spiral is cast over 30 ticks, " + casting[0]);
            int first = log.firstFlew.getFirst(), last = log.firstFlew.getLast();
            helper.assertTrue(last - first >= 26 && last - first < 30, "spread over the 30 ticks, first " + first + ", last " + last);
            for (int i = 1; i < 12; i++) {
                int gap = log.firstFlew.get(i) - log.firstFlew.get(i - 1);
                helper.assertTrue(gap == 2 || gap == 3, "one every 2.5 ticks: " + log.firstFlew);
                double turn = diff(yawTo(w, log.shots.get(i).pos()), yawTo(w, log.shots.get(i - 1).pos()));
                helper.assertTrue(Math.abs(turn - 30.0) < 0.5, "each 30 degrees on from the last, got " + turn);
            }
        });
    }

    /** The ring: 16 fireballs on the same tick, 22.5 degrees apart, one straight along its aim. */
    private static void ring(GameTestHelper helper) {
        Flamewright w = wright(helper, 5.5, 5.5, null);
        Log log = new Log(helper, w);
        helper.runAtTickTime(1, () -> {
            face(w, -90.0F);
            w.setNextPattern(Step.RING);
            helper.assertTrue(w.windUp(null), "it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.shots.size() == 16 && w.fired() == 16, "16 fireballs, seen " + log.shots.size());
            helper.assertTrue(log.firstFlew.stream().distinct().count() == 1, "all at once: " + log.firstFlew);
            boolean[] slot = new boolean[16];
            for (Shot s : log.shots) {
                double off = diff(yawTo(w, s.pos()), w.aim());
                int k = Math.floorMod((int) Math.round(off / 22.5), 16);
                helper.assertTrue(Math.abs(diff(off, k * 22.5)) < 0.5, "on a 22.5-degree step from the aim, off by " + off);
                helper.assertTrue(!slot[k], "one fireball to each step, two at " + k * 22.5);
                slot[k] = true;
            }
            helper.assertTrue(slot[0], "one fireball straight along its aim");
        });
    }

    /** The cross: 4 arms 90 degrees apart, each 3 fireballs leaving 4 ticks apart, 12 in all. */
    private static void cross(GameTestHelper helper) {
        Flamewright w = wright(helper, 5.5, 5.5, null);
        Log log = new Log(helper, w);
        helper.runAtTickTime(1, () -> {
            face(w, -90.0F);
            w.setNextPattern(Step.CROSS);
            helper.assertTrue(w.windUp(null), "it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.shots.size() == 12 && w.fired() == 12, "12 fireballs, seen " + log.shots.size());
            int[] perArm = new int[4];
            Map<Integer, List<Integer>> ticksByArm = new java.util.HashMap<>();
            for (int i = 0; i < 12; i++) {
                double off = Mth.positiveModulo(diff(yawTo(w, log.shots.get(i).pos()), w.aim()) + 1.0, 360.0) - 1.0;
                int arm = (int) Math.round(off / 90.0) % 4;
                helper.assertTrue(Math.abs(off - arm * 90.0) < 0.5, "on one of 4 arms 90 degrees apart, off by " + off);
                perArm[arm]++;
                ticksByArm.computeIfAbsent(arm, k -> new ArrayList<>()).add(log.firstFlew.get(i));
            }
            for (int a = 0; a < 4; a++) {
                helper.assertTrue(perArm[a] == 3, "3 to each arm: " + java.util.Arrays.toString(perArm));
                List<Integer> t = ticksByArm.get(a);
                helper.assertTrue(t.get(1) - t.get(0) == 4 && t.get(2) - t.get(1) == 4, "an arm's fireballs leave 4 ticks apart: " + t);
            }
        });
    }

    /** Patterns come in turn: three wind-ups in a row cast the spiral, then the ring, then the cross. */
    private static void inTurn(GameTestHelper helper) {
        Flamewright w = wright(helper, 5.5, 5.5, null);
        Log log = new Log(helper, w);
        List<String> cast = new ArrayList<>();
        w.setNextPattern(Step.SPIRAL);
        helper.onEachTick(() -> {
            if (w.core().idle() && cast.size() < 3) {
                helper.assertTrue(w.windUp(null), "it winds up");
                cast.add(w.drawing().step());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cast.equals(List.of("spiral", "ring", "cross")), "drawn in turn: " + cast);
            helper.assertTrue(log.of("spiral").size() == 1 && log.of("ring").size() == 1 && log.of("cross").size() == 1, "and cast: " + log.steps);
            helper.assertTrue(log.of("spiral").getFirst() < log.of("ring").getFirst() && log.of("ring").getFirst() < log.of("cross").getFirst(),
                    "spiral, then ring, then cross: " + log.steps);
            helper.assertTrue(w.fired() == 12 + 16 + 12, "12 + 16 + 12 fireballs, fired " + w.fired());
        });
    }

    // ---- fireballs -------------------------------------------------------------------------------------------------

    /** A fireball flies 0.25 blocks every tick, in a straight line. */
    private static void speed(GameTestHelper helper) {
        Flamewright w = wright(helper, 2.5, 5.5, null);
        Log log = new Log(helper, w);
        List<Vec3> path = new ArrayList<>();
        helper.runAtTickTime(1, () -> {
            face(w, -90.0F);
            w.setNextPattern(Step.RING);
            helper.assertTrue(w.windUp(null), "it winds up");
        });
        helper.onEachTick(() -> {
            if (log.shots.isEmpty()) return;
            Shot east = null;
            for (Shot s : log.shots) if (Math.abs(diff(yawTo(w, s.pos()), w.aim())) < 0.5) east = s;
            if (east != null && w.fireballs().contains(east)) path.add(east.pos());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(path.size() >= 12, "followed the fireball heading east for 12 ticks, " + path.size());
            for (int i = 1; i < path.size(); i++) {
                Vec3 d = path.get(i).subtract(path.get(i - 1));
                helper.assertTrue(Math.abs(d.length() - 0.25) < 1.0E-6, "0.25 blocks a tick, moved " + d.length());
                helper.assertTrue(Math.abs(d.y) < 1.0E-9 && Math.abs(d.z) < 1.0E-6, "straight and level along its line, " + d);
            }
        });
    }

    /**
     * The gaps: of two pigs 6 blocks off, one where the ring is aimed and one half way between two of its fireballs,
     * only the first is burned.
     */
    private static void ringGap(GameTestHelper helper) {
        Pig aimed = pig(helper, 8.5, 5.5);
        double a = Math.toRadians(11.25);
        Pig between = pig(helper, 2.5 + 6.0 * Math.cos(a), 5.5 + 6.0 * Math.sin(a));
        Flamewright w = wright(helper, 2.5, 5.5, null);
        helper.runAtTickTime(1, () -> {
            w.setNextPattern(Step.RING);
            helper.assertTrue(w.windUp(aimed), "it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(w.fired() == 16 && w.fireballs().isEmpty(), "the ring is cast and has burned out, in the air " + w.fireballs().size());
            helper.assertTrue(aimed.getHealth() < aimed.getMaxHealth(), "the pig it aimed at is burned");
            helper.assertTrue(between.getHealth() >= between.getMaxHealth(), "the pig in the gap is untouched");
        });
    }

    /** A fireball stops at the first block on its way and sets nothing alight. */
    private static void stopsAtBlocks(GameTestHelper helper) {
        Flamewright w = wright(helper, 4.5, 5.5, null);
        Log log = new Log(helper, w);
        for (int y = 1; y <= 3; y++) {
            for (int z = 3; z <= 8; z++) helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        }
        helper.runAtTickTime(1, () -> {
            face(w, -90.0F);
            w.setNextPattern(Step.RING);
            helper.assertTrue(w.windUp(null), "it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.shots.size() == 16 && w.fireballs().isEmpty(), "the ring is cast and every fireball has stopped");
            Shot east = null;
            for (Shot s : log.shots) if (Math.abs(diff(yawTo(w, s.pos()), w.aim())) < 0.5) east = s;
            helper.assertTrue(east != null, "found the fireball heading east");
            // it leaves 0.6 blocks out (x 5.1) and the wall's face is at x 8
            helper.assertTrue(Math.abs(east.travelled() - 2.9) < 0.01, "it stopped at the wall 2.9 blocks on, travelled " + east.travelled());
            for (int x = 1; x <= 10; x++) {
                for (int y = 1; y <= 4; y++) {
                    for (int z = 1; z <= 10; z++) {
                        helper.assertTrue(!(helper.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof BaseFireBlock), "nothing caught fire at " + x + " " + y + " " + z);
                    }
                }
            }
        });
    }

    /** A shield raised toward it takes a fireball: the fireball reaches the player, who is unhurt. */
    private static void shield(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(8.5, 1, 5.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            p.setYRot(90.0F);            // facing -x, toward the Flamewright
            p.setYHeadRot(90.0F);
            p.yHeadRotO = 90.0F;
        });
        Flamewright w = wright(helper, 3.5, 5.5, null);
        Log log = new Log(helper, w);
        float before = p.getHealth();
        helper.runAtTickTime(1, () -> {
            w.setNextPattern(Step.RING);
            helper.assertTrue(w.windUp(p), "it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(p.isBlocking(), "the player holds the shield up");
            helper.assertTrue(log.of("fireball_hit").size() == 1, "a fireball reached the player: " + log.steps);
            helper.assertTrue(p.getHealth() >= before, "the shield took it, health " + p.getHealth());
            helper.assertTrue(p.getRemainingFireTicks() <= 0, "and the player is not set alight");
        });
    }

    /** Only a hit in the wind-up staggers it: a hit while it stands idle, or while it casts, only hurts. */
    private static void hitOutsideWindUp(GameTestHelper helper) {
        Pig pig = pig(helper, 8.5, 8.5);
        Flamewright w = wright(helper, 3.5, 5.5, null);
        Log log = new Log(helper, w);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(w.hurtServer(helper.getLevel(), w.damageSources().mobAttack(pig), 1.0F), "the hit lands");
            helper.assertTrue(w.core().idle(), "a hit while idle doesn't stagger it, phase " + w.phase());
            w.setNextPattern(Step.SPIRAL);
            helper.assertTrue(w.windUp(null), "it winds up");
        });
        helper.onEachTick(() -> {
            if (w.core().in(Step.SPIRAL) && w.core().elapsed() == 5) {
                w.damageCooldownTime = 0;                                     // the last hit's cooldown would refuse an equal one
                helper.assertTrue(w.hurtServer(helper.getLevel(), w.damageSources().mobAttack(pig), 1.0F), "the hit lands");
                helper.assertTrue(w.core().in(Step.SPIRAL), "a hit while it casts doesn't stagger it, phase " + w.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.of("stagger").isEmpty(), "never staggered: " + log.steps);
            helper.assertTrue(w.fired() == 12 && w.core().idle(), "the spiral was cast whole, fired " + w.fired());
        });
    }

    // ---- stats and home --------------------------------------------------------------------------------------------

    /** Health 30, attack 4 (a fireball's burn), armour 2, a 0.7 x 2.0 hitbox. */
    private static void stats(GameTestHelper helper) {
        Flamewright w = wright(helper, 5.5, 5.5, null);
        w.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(w.getMaxHealth() - 30.0F) < 0.01F && Math.abs(w.getHealth() - 30.0F) < 0.01F,
                    "health 30, got " + w.getHealth() + "/" + w.getMaxHealth());
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4");
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(w.getBbWidth() - 0.7F) < 0.02F && Math.abs(w.getBbHeight() - 2.0F) < 0.02F,
                    "hitbox 0.7 x 2.0, got " + w.getBbWidth() + " x " + w.getBbHeight());
            helper.assertTrue(w.fireImmune(), "fire never hurts it");
            helper.assertTrue(!w.vulnerable(), "no gold outside the wind-up");
            helper.succeed();
        });
    }

    /** Home: placed by the Teeming spawner's fortress rule only (no biome spawns), alone. */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("flamewright");
        helper.assertTrue(row.spawner().equals("fortress") && TeemingSpawner.Rule.of(row.spawner()) == TeemingSpawner.Rule.FORTRESS,
                "the fortress rule places it, got '" + row.spawner() + "'");
        helper.assertTrue(row.weight() == 0 && !row.biomeSpawns(), "no biome spawns, weight " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "alone, groups of " + row.groupMin() + "-" + row.groupMax());
        helper.assertTrue(row.dim() == RosterStats.Dim.NETHER, "a Nether mob");
        helper.succeed();
    }
}
