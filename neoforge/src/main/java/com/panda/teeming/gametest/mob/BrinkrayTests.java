package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.end.Brinkray;
import com.panda.teeming.mob.end.Brinkray.Brink;
import com.panda.teeming.mob.end.Brinkray.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Brinkray. The barrier roof is 8 up, so no real drop of 20 fits in an arena: each ray here counts every cell at or
 * below y 2 inside its own arena as open air ({@link Brinkray#fakeVoid}), standing in for the void under an island, and
 * the 19-against-20 line is checked on the rule's column check against stub columns. Two sets: a bridge of end stone one
 * block wide (x 1..10, z 9, y 3, so its deck is at y 4) with nothing under it but that void, and an island (x 1..10,
 * z 9..11, y 3) whose north rim (z 9) is a brink and whose inland blocks (z 10.5) are solid ground. The ray waits 7
 * blocks north of its quarry, over the drop and level with its feet (its banking spot), and hovers there at rest,
 * so it stays in the arena.
 *
 * <p>A quarry at the brink is banked at for exactly 20 ticks and unhurt meanwhile, then rammed at 1 block a tick: its
 * attack (9, armour counts) and a throw of 1.8 on along the ram and 0.3 up, less by knockback resistance. A quarry that
 * steps off the streak during the bank (a block inland, or three along the bridge) is missed, and the ray rams the rock
 * and hangs there, held fast and within reach, for exactly 60 ticks. It ignores survival players on solid ground and
 * loses interest in one who gets back to it; hit, it hunts the one who hit it for exactly 200 ticks anywhere, and away
 * from a brink that ram throws no one. Killed in its bank, it rams nothing. Every number is checked against a plain
 * value; pigs with 100 health stand in where a number must be exact.
 */
public final class BrinkrayTests {
    private BrinkrayTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("brinkray_tell", BrinkrayTests::tell);
        out.accept("brinkray_counter", BrinkrayTests::counter);
        TeemingGameTests.timeLimit("brinkray_counter", 200);
        out.accept("brinkray_counter_bridge", BrinkrayTests::counterBridge);
        out.accept("brinkray_failure", BrinkrayTests::failure);
        out.accept("brinkray_knockback", BrinkrayTests::knockback);
        out.accept("brinkray_knockback_resisted", BrinkrayTests::knockbackResisted);
        out.accept("brinkray_armour", BrinkrayTests::armour);
        out.accept("brinkray_ram_speed", BrinkrayTests::ramSpeed);
        out.accept("brinkray_drop_20", BrinkrayTests::dropTwenty);
        out.accept("brinkray_within_1", BrinkrayTests::withinOne);
        out.accept("brinkray_hunts_brink", BrinkrayTests::huntsBrink);
        out.accept("brinkray_ignores_ground", BrinkrayTests::ignoresGround);
        out.accept("brinkray_loses_interest", BrinkrayTests::losesInterest);
        out.accept("brinkray_grudge_200", BrinkrayTests::grudge);
        TeemingGameTests.timeLimit("brinkray_grudge_200", 260);
        out.accept("brinkray_grudge_no_throw", BrinkrayTests::grudgeNoThrow);
        out.accept("brinkray_dies_mid_tell", BrinkrayTests::diesMidTell);
        out.accept("brinkray_tell_veto", BrinkrayTests::tellVeto);
        out.accept("brinkray_spawn_rule", BrinkrayTests::spawnRule);
        out.accept("brinkray_stats", BrinkrayTests::stats);
        out.accept("brinkray_home", BrinkrayTests::home);
    }

    // ---- fixtures --------------------------------------------------------------------------------------------------

    /** The bridge: end stone one block wide, x 1..10 at z 9, y 3 (its deck at y 4). */
    private static void bridge(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) helper.setBlock(new BlockPos(x, 3, 9), Blocks.END_STONE);
    }

    /** The island: end stone x 1..10, z 9..11, y 3; its north rim (z 9) faces the drop. */
    private static void island(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) for (int z = 9; z <= 11; z++) helper.setBlock(new BlockPos(x, 3, z), Blocks.END_STONE);
    }

    /** At the brink: the middle of the bridge, or the island's rim. */
    private static final Vec3 BRINK = new Vec3(5.5, 4, 9.5);
    /** Solid ground: a block in from the island's rim, with island all round within a block. */
    private static final Vec3 INLAND = new Vec3(5.5, 4, 10.5);
    /** The banking spot for a quarry at {@link #BRINK}: 7 north of it, over the drop, its underside 0.2 under the deck. */
    private static final Vec3 SPOT = new Vec3(5.5, 3.8, 2.5);

    /**
     * A Brinkray with the base look at {@code at}, the arena's void below y 2 counted as open air, a follow range of 10
     * (its own arena, never past the next one), hovering where it is at rest, removed when the test ends.
     */
    private static Brinkray ray(GameTestHelper helper, Vec3 at) {
        Brinkray r = helper.spawn(ModMobs.BRINKRAY.get(), at);
        r.core().setLook(LookDef.BASE, true);
        r.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(10.0);
        r.fakeVoid(helper.getBounds(), helper.absolutePos(new BlockPos(0, 2, 0)).getY());
        r.glideAround(r.position(), 0.0);
        TestPlayers.whenDone(helper, r::discard);
        return r;
    }

    /** A still pig with 100 health at {@code at}: it keeps any motion it is given and never falls. */
    private static Pig stillPig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A pig with 100 health and no will of its own: it keeps its physics, so a throw carries it and it falls. */
    private static Pig loosePig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.removeFreeWill();
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A survival player with 200 health at {@code at}, so a few rams can't kill it. */
    private static ServerPlayer survivor(GameTestHelper helper, Vec3 at) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
        p.setHealth(200.0F);
        return p;
    }

    private static Vec3 rel(GameTestHelper helper, LivingEntity e) {
        return helper.relativeVec(e.position());
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    /** The bridge, a ray at its banking spot and a still pig on the bridge, handed to it. */
    private static Brinkray onBridge(GameTestHelper helper, Pig[] pig) {
        bridge(helper);
        Brinkray r = ray(helper, SPOT);
        pig[0] = stillPig(helper, BRINK);
        r.setTarget(pig[0]);
        return r;
    }

    // ---- the bank (tell) -------------------------------------------------------------------------------------------

    /**
     * The bank holds for exactly 20 ticks with its leading edges lit (glow stage 1), and in that time the pig on the
     * bridge is neither hurt nor moved and the ray hangs where it banked; then it rams.
     */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkray r = onBridge(helper, pig);
        long[] from = {-1}, to = {-1};
        boolean[] lit = {true}, quiet = {true};
        double[] drift = {0};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (from[0] < 0 && r.core().in(Step.TELL)) {
                from[0] = now;
                start[0] = r.position();
            }
            if (from[0] >= 0 && to[0] < 0) {
                if (r.core().in(Step.TELL)) {
                    if (now > from[0] && r.core().stage() != Brinkray.GLOW_BANK) lit[0] = false;
                    if (pig[0].getHealth() < 100.0F || !near(rel(helper, pig[0]).z, 9.5) || r.rams() > 0) quiet[0] = false;
                    drift[0] = Math.max(drift[0], r.position().distanceTo(start[0]));
                } else {
                    to[0] = now;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(to[0] >= 0, "the bank ended");
            helper.assertTrue(to[0] - from[0] == 20, "the bank lasted 20 ticks, took " + (to[0] - from[0]));
            helper.assertTrue(lit[0], "its leading edges were lit through the bank");
            helper.assertTrue(quiet[0], "nothing happened to the pig during the bank");
            helper.assertTrue(drift[0] < 0.5, "it hung where it banked, drifted " + drift[0]);
            helper.assertTrue(r.rams() == 1, "then it rammed, rams " + r.rams());
        });
    }

    // ---- the counter -----------------------------------------------------------------------------------------------

    /**
     * The pig at the island's rim steps a block inland as the bank starts: the ram, committed to the line it banked
     * toward, misses it and meets the rim. The ray is stunned exactly 60 ticks with its lights out, held fast where it
     * struck (it moves not at all), within reach of the pig (a gap under 3); the pig is never touched. Then it pulls
     * away and, its quarry on solid ground, glides again.
     */
    private static void counter(GameTestHelper helper) {
        island(helper);
        Brinkray r = ray(helper, SPOT);
        Pig pig = stillPig(helper, BRINK);
        r.setTarget(pig);
        long[] stun = {-1}, woke = {-1};
        double[] moved = {0}, gap = {99};
        boolean[] dark = {true}, fixed = {true};
        Vec3[] at = {null};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (r.core().in(Step.TELL) && near(rel(helper, pig).z, 9.5)) pig.snapTo(helper.absoluteVec(INLAND));
            if (stun[0] < 0 && r.core().in(Step.STUNNED)) {
                stun[0] = now;
                at[0] = r.position();
                gap[0] = MobCore.gap(pig, r);
            }
            if (stun[0] >= 0 && woke[0] < 0) {
                if (r.core().in(Step.STUNNED)) {
                    moved[0] = Math.max(moved[0], r.position().distanceTo(at[0]));
                    if (now > stun[0] && r.core().stage() != Brinkray.GLOW_OUT) dark[0] = false;
                    if (r.pin() != Pin.FIXED) fixed[0] = false;
                } else {
                    woke[0] = now;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(woke[0] >= 0, "the stun wore off");
            helper.assertTrue(woke[0] - stun[0] == 60, "stunned exactly 60 ticks, took " + (woke[0] - stun[0]));
            helper.assertTrue(r.crashes() == 1 && r.hits() == 0, "one ram into the rock, no hit: crashes " + r.crashes() + ", hits " + r.hits());
            helper.assertTrue(moved[0] < 0.01 && fixed[0], "held fast where it struck, moved " + moved[0]);
            helper.assertTrue(dark[0], "its lights out while stunned");
            helper.assertTrue(gap[0] < 3.0, "within the pig's reach, a gap of " + gap[0]);
            double front = helper.relativeVec(at[0]).z + 1.5;
            helper.assertTrue(Math.abs(front - 9.0) < 0.05, "stunned against the rim, its front at z " + front);
            helper.assertTrue(pig.getHealth() >= 100.0F, "the pig was never touched");
            helper.assertTrue(r.core().idle(), "then it pulls away and, its quarry on solid ground, glides again, has " + r.phase().step());
        });
    }

    /**
     * On the bridge, the pig steps 3 blocks along it as the bank starts: the ram misses it and meets the bridge's side,
     * and the stunned ray hangs there within the pig's reach.
     */
    private static void counterBridge(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkray r = onBridge(helper, pig);
        double[] gap = {99};
        helper.onEachTick(() -> {
            if (r.core().in(Step.TELL) && near(rel(helper, pig[0]).x, 5.5)) pig[0].snapTo(helper.absoluteVec(new Vec3(8.5, 4, 9.5)));
            if (gap[0] > 98 && r.core().in(Step.STUNNED)) gap[0] = MobCore.gap(pig[0], r);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(r.core().in(Step.STUNNED), "stunned against the bridge, has " + r.phase().step());
            helper.assertTrue(r.crashes() == 1 && r.hits() == 0 && pig[0].getHealth() >= 100.0F, "the ram missed the pig and met the rock");
            helper.assertTrue(gap[0] < 3.0, "within the pig's reach along the bridge, a gap of " + gap[0]);
        });
    }

    // ---- the failure -----------------------------------------------------------------------------------------------

    /**
     * Failing the counter: the pig stays on the bridge. It loses exactly the ray's attack of 9, is thrown on along the
     * ram (south) across the bridge, and falls off its far side, out over the void.
     */
    private static void failure(GameTestHelper helper) {
        bridge(helper);
        Brinkray r = ray(helper, SPOT);
        Pig pig = loosePig(helper, BRINK);
        r.setTarget(pig);
        float[] lost = {-1};
        helper.onEachTick(() -> {
            if (lost[0] < 0 && pig.getHealth() < 100.0F) lost[0] = 100.0F - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(r.hits() == 1, "one ram hit, saw " + r.hits());
            helper.assertTrue(Math.abs(lost[0] - 9.0F) < 0.01F, "the pig lost the ray's attack of 9, lost " + lost[0]);
            Vec3 at = rel(helper, pig);
            helper.assertTrue(at.z > 10.0 && at.y < 3.5, "thrown off the bridge's far side and falling, at " + at);
        });
    }

    /** The throw on the tick of the hit: 1.8 a tick on along the ram (south) and 0.3 up, read off a still pig. */
    private static void knockback(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkray r = onBridge(helper, pig);
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && pig[0].getHealth() < 100.0F) thrown[0] = pig[0].getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown[0] != null, "the ram hit");
            Vec3 v = thrown[0];
            helper.assertTrue(near(Math.hypot(v.x, v.z), 1.8) && v.z > 1.79, "thrown 1.8 on along the ram (south), motion " + v);
            helper.assertTrue(near(v.y, 0.3), "and 0.3 up, motion " + v);
            helper.assertTrue(r.ramDir().z > 0.999, "the ram ran south, along " + r.ramDir());
        });
    }

    /** Knockback resistance 0.5 keeps half the throw: 0.9 on along the ram and 0.15 up. */
    private static void knockbackResisted(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        onBridge(helper, pig);
        pig[0].getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.5);
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && pig[0].getHealth() < 100.0F) thrown[0] = pig[0].getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown[0] != null, "the ram hit");
            Vec3 v = thrown[0];
            helper.assertTrue(near(Math.hypot(v.x, v.z), 0.9) && near(v.y, 0.15) && v.z > 0.89, "half the throw is left: 0.9 on, 0.15 up, motion " + v);
        });
    }

    /** Armour counts: armour 10 turns the ram's 9 into 7.02. */
    private static void armour(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkray r = onBridge(helper, pig);
        pig[0].getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        helper.succeedWhen(() -> {
            helper.assertTrue(r.hits() == 1, "one hit, saw " + r.hits());
            float lost = 100.0F - pig[0].getHealth();
            helper.assertTrue(Math.abs(lost - 7.02F) < 0.01F, "armour 10 turns the 9 into 7.02, the pig lost " + lost);
        });
    }

    /** The ram covers 1 block a tick, straight along its line. */
    private static void ramSpeed(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkray r = onBridge(helper, pig);
        List<Double> steps = new ArrayList<>();
        Vec3[] last = {null};
        helper.onEachTick(() -> {
            if (r.core().in(Step.RAM)) {
                Vec3 now = r.position();
                if (last[0] != null) steps.add(now.distanceTo(last[0]));
                last[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(r.hits() == 1, "the ram reached the pig");
            helper.assertTrue(steps.size() >= 4, "it rammed for at least 4 ticks, saw " + steps);
            double mean = steps.subList(0, steps.size() - 1).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - 1.0) < 0.02, "the ram covers 1 block a tick, mean " + mean + " of " + steps);
        });
    }

    // ---- who it rams -----------------------------------------------------------------------------------------------

    /** A column of stub cells, by depth under the feet' cell (1 = the block stood on): solid where listed. */
    private static IntPredicate column(Integer... solid) {
        Set<Integer> at = Set.of(solid);
        return at::contains;
    }

    /**
     * The brink's depth, on the rule's own column check against stub columns: rock 20 down leaves 19 blocks of air (no
     * brink), rock 21 down leaves 20 (a brink); the block stood on, or ground 10 down, is no drop; the void is one.
     */
    private static void dropTwenty(GameTestHelper helper) {
        helper.assertTrue(!Brink.drop(column(20)), "19 blocks of air below: no brink");
        helper.assertTrue(Brink.drop(column(21)), "20 blocks of air below: a brink");
        helper.assertTrue(!Brink.drop(column(1)) && !Brink.drop(column(10)), "the block stood on, or ground 10 down, is no drop");
        helper.assertTrue(Brink.drop(d -> false), "the void is a drop");
        helper.succeed();
    }

    /**
     * Within 1 block, in the world: on a bridge 3 wide (z 8..10) the middle (1.5 from either edge) is no brink; its edge
     * block is; a quarry 1 block in from the island's rim is on solid ground; a survival player in the air (not standing)
     * is never at a brink.
     */
    private static void withinOne(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) for (int z = 8; z <= 10; z++) helper.setBlock(new BlockPos(x, 3, z), Blocks.END_STONE);
        Brinkray r = ray(helper, SPOT);
        r.setNoAi(true);
        Pig middle = stillPig(helper, new Vec3(5.5, 4, 9.5));
        Pig edge = stillPig(helper, new Vec3(3.5, 4, 8.5));
        Pig nearEdge = stillPig(helper, new Vec3(7.5, 4, 8.9));
        ServerPlayer flying = TestPlayers.survivor(helper, new Vec3(8.5, 6.5, 9.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!r.atBrink(middle), "the middle of a bridge 3 wide is 1.5 from a drop: no brink");
            helper.assertTrue(r.atBrink(edge), "its edge block is a brink");
            helper.assertTrue(r.atBrink(nearEdge), "0.9 from the drop is a brink");
            helper.assertTrue(!r.atBrink(flying), "a player 2.5 up in the air is not standing at a brink");
            helper.succeed();
        });
    }

    /** A survival player on the bridge is hunted: the ray banks, rams and throws them 1.8 on along the ram, 0.3 up. */
    private static void huntsBrink(GameTestHelper helper) {
        bridge(helper);
        Brinkray r = ray(helper, SPOT);
        ServerPlayer p = survivor(helper, BRINK);
        Vec3[] thrown = {null};
        boolean[] banked = {false};
        helper.onEachTick(() -> {
            if (r.core().in(Step.TELL)) banked[0] = true;
            if (thrown[0] == null && p.getHealth() < 200.0F) thrown[0] = p.getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(banked[0], "it banked at the player on the bridge");
            helper.assertTrue(thrown[0] != null, "and rammed them");
            Vec3 v = thrown[0];
            helper.assertTrue(near(Math.hypot(v.x, v.z), 1.8) && near(v.y, 0.3) && v.z > 1.79, "the player is thrown 1.8 on and 0.3 up, motion " + v);
        });
    }

    /** A survival player on solid ground in plain sight is left alone: no hunt, no bank, no harm, for 60 ticks. */
    private static void ignoresGround(GameTestHelper helper) {
        island(helper);
        Brinkray r = ray(helper, SPOT);
        ServerPlayer p = survivor(helper, INLAND);
        helper.onEachTick(() -> helper.assertTrue(r.core().idle(), "it leaves a player on solid ground alone, has " + r.phase().step()));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!r.atBrink(p) && !r.hostileTo(p), "solid ground: not hostile");
            helper.assertTrue(p.getHealth() >= 200.0F, "the player is unhurt");
            helper.succeed();
        });
    }

    /**
     * A survival player at the island's rim is hunted; once the ray is on its way out to bank they step a block inland,
     * and it loses interest at once: back to gliding, no bank, for the rest of the test.
     */
    private static void losesInterest(GameTestHelper helper) {
        island(helper);
        Brinkray r = ray(helper, new Vec3(2.5, 6, 3.5));
        ServerPlayer p = survivor(helper, BRINK);
        long[] stalk = {-1}, settled = {-1};
        boolean[] banked = {false};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (stalk[0] < 0 && r.core().in(Step.STALK)) {
                stalk[0] = now;
                p.snapTo(helper.absoluteVec(INLAND));
            }
            if (stalk[0] >= 0 && settled[0] < 0 && r.core().idle()) settled[0] = now;
            if (r.core().in(Step.TELL)) banked[0] = true;
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(stalk[0] >= 0, "it set out for the player at the rim");
            helper.assertTrue(settled[0] >= 0 && settled[0] - stalk[0] <= 2, "back on solid ground, it lost interest at once, after "
                    + (settled[0] - stalk[0]) + " ticks");
            helper.assertTrue(!banked[0] && r.core().idle(), "and never banked, has " + r.phase().step());
            helper.assertTrue(p.getHealth() >= 200.0F, "the player is unhurt");
            helper.succeed();
        });
    }

    /**
     * Neutral: a survival player on solid ground hits it and is hunted for exactly 200 ticks, anywhere: the grudge reads
     * 200 at the blow, 1 a tick before its end and 0 at 200; it goes after them on solid ground in that time, and then
     * leaves them be.
     */
    private static void grudge(GameTestHelper helper) {
        island(helper);
        Brinkray r = ray(helper, SPOT.add(0, 0.8, 1.0));
        ServerPlayer p = survivor(helper, INLAND);
        long[] blow = {-1};
        int[] atBlow = {-1}, before = {-1}, after = {-1};
        boolean[] hostileBefore = {true}, hunted = {false}, hostileAfter = {true};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (blow[0] < 0 && helper.getTick() == 5) {
                hostileBefore[0] = r.hostileTo(p);
                r.hurtServer(helper.getLevel(), r.damageSources().playerAttack(p), 1.0F);
                blow[0] = now;
                atBlow[0] = r.grudgeLeft(p);
            }
            if (blow[0] < 0) return;
            if (!r.core().idle() && r.core().target() == p) hunted[0] = true;
            if (now == blow[0] + 199) before[0] = r.grudgeLeft(p);
            if (now == blow[0] + 200) {
                after[0] = r.grudgeLeft(p);
                hostileAfter[0] = r.hostileTo(p);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(after[0] >= 0, "waiting out the grudge");
            helper.assertTrue(!hostileBefore[0], "on solid ground it left the player alone before the blow");
            helper.assertTrue(atBlow[0] == 200, "the blow starts a grudge of 200 ticks, got " + atBlow[0]);
            helper.assertTrue(before[0] == 1 && after[0] == 0, "1 tick left at 199, none at 200, got " + before[0] + " and " + after[0]);
            helper.assertTrue(hunted[0], "it went after the player on solid ground");
            helper.assertTrue(!hostileAfter[0], "after 200 ticks it leaves them be");
        });
    }

    /**
     * Away from a brink its ram hurts but throws no one: a pig inland that hit it is rammed for 9 with no motion across
     * the ground at all (not even vanilla's knockback).
     */
    private static void grudgeNoThrow(GameTestHelper helper) {
        island(helper);
        Brinkray r = ray(helper, SPOT.add(0, 0.8, 1.0));
        Pig pig = stillPig(helper, INLAND);
        helper.runAtTickTime(2, () -> r.hurtServer(helper.getLevel(), r.damageSources().mobAttack(pig), 1.0F));
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && pig.getHealth() < 100.0F) thrown[0] = pig.getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown[0] != null && r.hits() == 1, "the ram hit the pig that hit it");
            helper.assertTrue(Math.abs(100.0F - pig.getHealth() - 9.0F) < 0.01F, "for its attack of 9");
            helper.assertTrue(thrown[0].horizontalDistance() < 1.0E-6, "with no motion across the ground, motion " + thrown[0]);
        });
    }

    // ---- death, the event, spawning, the row -----------------------------------------------------------------------

    /** Killed 8 ticks into its bank, it rams nothing: the pig keeps its health through the death and after. */
    private static void diesMidTell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkray r = onBridge(helper, pig);
        long[] first = {-1}, killed = {-1};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (first[0] < 0 && r.core().in(Step.TELL)) first[0] = now;
            if (first[0] >= 0 && killed[0] < 0 && now == first[0] + 8) {
                r.kill(helper.getLevel());
                killed[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getLevel().getGameTime() >= killed[0] + 30, "killed mid-bank, waiting out the ram");
            helper.assertTrue(r.rams() == 0 && r.hits() == 0, "no ram from a dead Brinkray, rams " + r.rams());
            helper.assertTrue(pig[0].getHealth() >= 100.0F && near(rel(helper, pig[0]).z, 9.5), "the pig kept its health and its place");
        });
    }

    /** A listener that cancels the "tell" step stops the bank, and so the ram: the pig is never touched. */
    private static void tellVeto(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkray r = onBridge(helper, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == r && e.step().equals("tell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.onEachTick(() -> helper.assertTrue(!r.core().in(Step.TELL), "a vetoed bank never starts"));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(r.rams() == 0 && pig[0].getHealth() >= 100.0F, "no ram, the pig untouched");
            helper.succeed();
        });
    }

    /**
     * Its spawner rule (void air): open air beside an island's edge with open air under it is a spot; a spot on the floor
     * is not, nor is open air with no island near.
     */
    private static void spawnRule(GameTestHelper helper) {
        TeemingSpawner.Rule rule = TeemingSpawner.Rule.of(RosterStats.get("brinkray").spawner());
        helper.assertTrue(rule == TeemingSpawner.Rule.VOID_AIR && rule.dimension.equals(Level.END), "the End's void-air rule places it, got " + rule);
        helper.setBlock(new BlockPos(7, 4, 5), Blocks.END_STONE);                       // an island's edge beside the spot
        helper.assertTrue(rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(5, 5, 5))), "open air beside an edge, a drop below, is a spot");
        helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))), "a spot on the floor is not");
        helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(6, 5, 6))), "open air with no island near is not");
        helper.succeed();
    }

    /** Health 40, attack 9, armour 2, a 3.0 x 0.6 hitbox; it flies (no gravity) and looks 32 out for a quarry. */
    private static void stats(GameTestHelper helper) {
        Brinkray r = helper.spawn(ModMobs.BRINKRAY.get(), new Vec3(5.5, 3, 5.5));
        r.core().setLook(LookDef.BASE, true);
        r.setNoAi(true);
        TestPlayers.whenDone(helper, r::discard);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(near(r.getMaxHealth(), 40.0) && near(r.getHealth(), 40.0), "health 40, got " + r.getHealth() + "/" + r.getMaxHealth());
            helper.assertTrue(near(r.getAttributeValue(Attributes.ATTACK_DAMAGE), 9.0), "attack 9");
            helper.assertTrue(r.getArmorValue() == 2, "armour 2, got " + r.getArmorValue());
            helper.assertTrue(Math.abs(r.getBbWidth() - 3.0F) < 0.02F && Math.abs(r.getBbHeight() - 0.6F) < 0.02F,
                    "hitbox 3.0 x 0.6, got " + r.getBbWidth() + " x " + r.getBbHeight());
            helper.assertTrue(near(r.getAttributeValue(Attributes.FOLLOW_RANGE), 32.0), "follow range 32");
            helper.assertTrue(r.isNoGravity(), "it flies");
            helper.succeed();
        });
    }

    /**
     * Home: the void air beside small islands and the outer End, only through the Teeming spawner's void-air rule (which
     * refuses the main island), on no biome list, its biome tag the four outer End biomes; neutral, of the monster
     * category; weight 0, one at a time. The Starry Ray is its second look; it drops void membrane.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("brinkray");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(row.cat() == RosterStats.Cat.NEUTRAL, "neutral");
        helper.assertTrue(ModMobs.BRINKRAY.get().getCategory() == MobCategory.MONSTER, "of the monster category");
        helper.assertTrue(row.spawner().equals("void_air"), "the spawner places it in void air, rule " + row.spawner());
        helper.assertTrue(!row.biomeSpawns(), "no biome list: only the spawner places it");
        var tag = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("brinkray"));
        helper.assertTrue(tag.isPresent() && tag.get().size() == 4, "its biome tag holds the four outer End biomes");
        helper.assertTrue(row.weight() == 0, "weight 0, got " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "one at a time, got " + row.groupMin() + ".." + row.groupMax());
        helper.assertTrue(row.tellTicks() == 20, "a 20-tick tell in its row");
        List<LookDef> looks = Looks.of("brinkray");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("starry_ray")), "the Starry Ray is a look: " + looks);
        helper.assertTrue(row.material().equals("void_membrane") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_membrane")),
                "it drops void membrane, a registered item");
        helper.succeed();
    }
}
