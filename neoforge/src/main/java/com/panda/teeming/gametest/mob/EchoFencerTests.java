package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.EchoFencer;
import com.panda.teeming.mob.end.EchoFencer.Afterimage;
import com.panda.teeming.mob.end.EchoFencer.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Echo Fencer: a 10-tick tell, then a lunge for 8.5 at everything on a line 3 blocks straight ahead; its afterimage stays
 * where it struck, draws back for the last 10 ticks and repeats the thrust 20 ticks after it, from the same spot the same
 * way, for 0.8 x 8.5 = 6.8. The fencer steps a block aside out of the line (never toward a drop); its own echo runs it
 * through when it stands in the line away from that spot, for 6.8 through its armour, and the first time spills a time
 * sand. Every number is checked against a plain value.
 *
 * <p>Most fencers here have no AI (they keep the place and facing they are given; their steps still run on time) or no
 * goals and a follow range of 1, and every one faces south (+Z), so its line runs along the arena. They leave with the
 * test. Pigs stand still as targets.
 */
public final class EchoFencerTests {
    private EchoFencerTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("echo_fencer_tell", EchoFencerTests::tell);
        out.accept("echo_fencer_counter", EchoFencerTests::stepOut);
        out.accept("echo_fencer_failure", EchoFencerTests::standInLine);
        out.accept("echo_fencer_thrust", EchoFencerTests::thrust);
        out.accept("echo_fencer_echo_delay", EchoFencerTests::echoDelay);
        out.accept("echo_fencer_echo_tell", EchoFencerTests::echoTell);
        out.accept("echo_fencer_reach", EchoFencerTests::reach);
        out.accept("echo_fencer_line_width", EchoFencerTests::lineWidth);
        out.accept("echo_fencer_wall", EchoFencerTests::wall);
        out.accept("echo_fencer_turned", EchoFencerTests::turned);
        out.accept("echo_fencer_own_spot", EchoFencerTests::ownSpot);
        out.accept("echo_fencer_sidestep", EchoFencerTests::sidestep);
        out.accept("echo_fencer_sidestep_drop", EchoFencerTests::sidestepDrop);
        out.accept("echo_fencer_sidestep_none", EchoFencerTests::sidestepNone);
        out.accept("echo_fencer_flags", EchoFencerTests::flags);
        out.accept("echo_fencer_echo_cancel", EchoFencerTests::echoCancel);
        out.accept("echo_fencer_cycle", EchoFencerTests::cycle);
        TeemingGameTests.timeLimit("echo_fencer_cycle", 160);
        out.accept("echo_fencer_stats", EchoFencerTests::stats);
        out.accept("echo_fencer_home", EchoFencerTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A fencer facing south that acts only on what the test tells it: no goals, a follow range of 1. */
    private static EchoFencer fencer(GameTestHelper helper, Vec3 at) {
        EchoFencer f = helper.spawn(ModMobs.ECHO_FENCER.get(), at);
        f.core().setLook("base", true);
        f.removeFreeWill();
        f.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        face(f, 0.0F);
        TestPlayers.whenDone(helper, f::discard);
        return f;
    }

    /** A fencer with no AI: it keeps its place and facing, and its steps still run on time. */
    private static EchoFencer still(GameTestHelper helper, Vec3 at) {
        EchoFencer f = fencer(helper, at);
        f.setNoAi(true);
        return f;
    }

    private static void face(LivingEntity e, float yaw) {
        e.setYRot(yaw);
        e.setYBodyRot(yaw);
        e.setYHeadRot(yaw);
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A still pig that lasts many blows: 100 health. */
    private static Pig sturdyPig(GameTestHelper helper, Vec3 at) {
        Pig pig = pig(helper, at);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        return pig;
    }

    private static float lost(LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.01F;
    }

    private static void move(GameTestHelper helper, LivingEntity e, Vec3 to) {
        Vec3 at = helper.absoluteVec(to);
        e.snapTo(at.x, at.y, at.z, e.getYRot(), e.getXRot());
    }

    private static int timeSand(GameTestHelper helper) {
        int n = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0),
                i -> i.isAlive() && i.getItem().is(ModMaterials.TIME_SAND.get()))) n += item.getItem().getCount();
        return n;
    }

    // ---- the tell and the thrust -----------------------------------------------------------------------------------

    /**
     * The tell: with a pig 2.5 blocks ahead in reach it draws back by itself for exactly 10 ticks, hurting nothing; the
     * lunge follows and takes 8.5 from the pig on its first tick.
     */
    private static void tell(GameTestHelper helper) {
        EchoFencer f = fencer(helper, new Vec3(5.5, 1, 3.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 6.0));
        int[] tell = {0};
        boolean[] quiet = {true}, thrustNext = {false};
        float[] atThrust = {-1.0F};
        Step[] last = {Step.IDLE};
        helper.onEachTick(() -> {
            if (f.getTarget() != pig) f.setTarget(pig);
            Step now = (Step) f.phase();
            if (now == Step.TELL) {
                tell[0]++;
                if (lost(pig) > 0.001F) quiet[0] = false;
            } else if (last[0] == Step.TELL) {
                thrustNext[0] = now == Step.THRUST;
                atThrust[0] = lost(pig);
            }
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tell[0] > 0 && atThrust[0] >= 0.0F, "the tell ends, " + tell[0] + " ticks so far");
            helper.assertTrue(tell[0] == 10, "the tell holds 10 ticks, saw " + tell[0]);
            helper.assertTrue(quiet[0], "in the tell it hurts nothing");
            helper.assertTrue(thrustNext[0], "the lunge follows");
            helper.assertTrue(near(atThrust[0], 8.5F), "the lunge takes 8.5 on its first tick, the pig lost " + atThrust[0]);
        });
    }

    /** The lunge takes 8.5 from a pig 2.5 blocks ahead; a pig 2.5 behind and one 2 to the side are untouched. */
    private static void thrust(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 5.5));
        Pig ahead = pig(helper, new Vec3(5.5, 1, 8.0));
        Pig behind = pig(helper, new Vec3(5.5, 1, 3.0));
        Pig aside = pig(helper, new Vec3(7.5, 1, 7.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(f.lunge(null), "the lunge starts");
            helper.assertTrue(near(lost(ahead), 8.5F), "the pig ahead lost 8.5, lost " + lost(ahead));
            helper.assertTrue(lost(behind) < 0.001F && lost(aside) < 0.001F, "behind and aside lost nothing: " + lost(behind) + ", " + lost(aside));
            helper.assertTrue(f.thrusts() == 1, "one thrust");
            helper.succeed();
        });
    }

    // ---- the echo ----------------------------------------------------------------------------------------------------

    /** The afterimage repeats the thrust exactly 20 ticks after it, for 0.8 x 8.5 = 6.8. */
    private static void echoDelay(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 3.5));
        Pig pig = sturdyPig(helper, new Vec3(5.5, 1, 6.0));
        long[] thrustAt = {-1}, echoAt = {-1};
        float[] first = {-1.0F};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(f.lunge(null), "the lunge starts");
            thrustAt[0] = helper.getLevel().getGameTime();
            first[0] = lost(pig);
        });
        helper.onEachTick(() -> {
            if (echoAt[0] < 0 && lost(pig) > 8.501F) echoAt[0] = helper.getLevel().getGameTime();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(echoAt[0] >= 0, "the echo lands");
            helper.assertTrue(near(first[0], 8.5F), "the thrust takes 8.5, took " + first[0]);
            helper.assertTrue(near(lost(pig) - first[0], 6.8F), "the echo takes 6.8, took " + (lost(pig) - first[0]));
            helper.assertTrue(echoAt[0] - thrustAt[0] == 20, "the echo comes 20 ticks after the thrust, came " + (echoAt[0] - thrustAt[0]));
            helper.assertTrue(f.echoes() == 1, "one echo, saw " + f.echoes());
        });
    }

    /** The afterimage's own tell: it draws back for exactly 10 ticks, hurting nothing, then the echo lands. */
    private static void echoTell(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 3.5));
        Pig pig = sturdyPig(helper, new Vec3(5.5, 1, 6.0));
        helper.runAtTickTime(2, () -> helper.assertTrue(f.lunge(null), "the lunge starts"));
        int[] telling = {0};
        boolean[] quiet = {true}, landed = {false};
        boolean[] was = {false};
        helper.onEachTick(() -> {
            boolean now = f.echoTelling();
            if (now) {
                telling[0]++;
                if (lost(pig) > 8.501F) quiet[0] = false;
            } else if (was[0]) {
                landed[0] = f.core().in(Step.ECHO) && near(lost(pig), 15.3F);
            }
            was[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(telling[0] > 0 && !was[0], "the afterimage's tell ends, " + telling[0] + " ticks so far");
            helper.assertTrue(telling[0] == 10, "it draws back for 10 ticks, saw " + telling[0]);
            helper.assertTrue(quiet[0], "its tell hurts nothing");
            helper.assertTrue(landed[0], "the echo lands as it ends, the pig lost " + lost(pig));
        });
    }

    /** The counter: a pig that steps 2 blocks aside after the thrust loses only the thrust's 8.5. */
    private static void stepOut(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 3.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 6.0));
        helper.runAtTickTime(2, () -> helper.assertTrue(f.lunge(null), "the lunge starts"));
        helper.runAtTickTime(8, () -> move(helper, pig, new Vec3(7.5, 1, 6.0)));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(f.echoes() == 1, "the echo came");
            helper.assertTrue(near(lost(pig), 8.5F), "out of the line it lost only the 8.5, lost " + lost(pig));
            helper.succeed();
        });
    }

    /** The failure: a pig that stays where it was struck loses 8.5 and then the echo's 6.8. */
    private static void standInLine(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 3.5));
        Pig pig = sturdyPig(helper, new Vec3(5.5, 1, 6.0));
        helper.runAtTickTime(2, () -> helper.assertTrue(f.lunge(null), "the lunge starts"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(near(lost(pig), 15.3F), "standing in the line costs 8.5 + 6.8 = 15.3, lost " + lost(pig));
            helper.succeed();
        });
    }

    /** The cancelled echo: with the "echo" step vetoed, the afterimage never strikes and is gone. */
    private static void echoCancel(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 3.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 6.0));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == f && e.step().equals("echo")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> helper.assertTrue(f.lunge(null), "the lunge starts"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(near(lost(pig), 8.5F), "only the thrust landed, lost " + lost(pig));
            helper.assertTrue(f.anchor() == null && f.core().idle() && !Afterimage.live(f.core().flags()), "no afterimage stands");
            helper.succeed();
        });
    }

    // ---- the line ----------------------------------------------------------------------------------------------------

    /**
     * The line reaches 3 blocks ahead (a hitbox within 0.4 of it is caught): a pig whose near edge is 3.3 ahead is hit,
     * one at 3.5 is not. Two fencers side by side, 4 blocks apart.
     */
    private static void reach(GameTestHelper helper) {
        EchoFencer a = still(helper, new Vec3(3.5, 1, 2.5));
        EchoFencer b = still(helper, new Vec3(7.5, 1, 2.5));
        Pig near = pig(helper, new Vec3(3.5, 1, 2.5 + 3.75));      // its edge 3.3 ahead: 3.0 + 0.4 reaches it
        Pig far = pig(helper, new Vec3(7.5, 1, 2.5 + 3.95));       // its edge 3.5 ahead: out of reach
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(a.lunge(null) && b.lunge(null), "the lunges start");
            helper.assertTrue(near(lost(near), 8.5F), "the pig 3.3 ahead was hit, lost " + lost(near));
            helper.assertTrue(lost(far) < 0.001F, "the pig 3.5 ahead was not, lost " + lost(far));
            helper.succeed();
        });
    }

    /** The line is 0.4 wide either side: a pig whose edge is 0.35 off it is hit, one 0.45 off is not. */
    private static void lineWidth(GameTestHelper helper) {
        EchoFencer a = still(helper, new Vec3(3.5, 1, 3.5));
        EchoFencer b = still(helper, new Vec3(8.5, 1, 3.5));
        Pig in = pig(helper, new Vec3(3.5 + 0.8, 1, 5.5));          // half width 0.45: its edge 0.35 off the line
        Pig out = pig(helper, new Vec3(8.5 + 0.9, 1, 5.5));         // its edge 0.45 off
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(a.lunge(null) && b.lunge(null), "the lunges start");
            helper.assertTrue(near(lost(in), 8.5F), "0.35 off, hit: lost " + lost(in));
            helper.assertTrue(lost(out) < 0.001F, "0.45 off, missed: lost " + lost(out));
            helper.succeed();
        });
    }

    /** The blade stops at the first block: a stone at blade height 1.5 ahead shields a pig 2.5 ahead from thrust and echo. */
    private static void wall(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 3.5));
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.STONE);           // 1.5 ahead (z 4..5), 1 above the feet
        Pig pig = pig(helper, new Vec3(5.5, 1, 6.0));
        helper.runAtTickTime(2, () -> helper.assertTrue(f.lunge(null), "the lunge starts"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(f.echoes() == 1, "the echo came");
            helper.assertTrue(lost(pig) < 0.001F, "behind the stone the pig lost nothing, lost " + lost(pig));
            helper.succeed();
        });
    }

    // ---- the afterimage turned on the fencer -------------------------------------------------------------------------

    /**
     * The payoff: a fencer standing in its own afterimage's line, 1.5 blocks from where it struck, is run through by
     * the echo for 6.8 (through its armour 2), and its hourglass cracks and spills one time sand. A second time it loses
     * 6.8 again and spills nothing.
     */
    private static void turned(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 2.5));
        int[] sandBefore = {0};
        helper.runAtTickTime(2, () -> {
            sandBefore[0] = timeSand(helper);
            helper.assertTrue(f.lunge(null), "the lunge starts");
        });
        helper.runAtTickTime(12, () -> move(helper, f, new Vec3(5.5, 1, 4.0)));
        helper.runAtTickTime(23, () -> {
            helper.assertTrue(f.turned() == 1, "its echo ran it through, " + f.turned());
            helper.assertTrue(near(lost(f), 6.8F), "for 6.8 through its armour, lost " + lost(f));
            helper.assertTrue(f.cracked(), "its hourglass cracked");
            helper.assertTrue(timeSand(helper) - sandBefore[0] == 1, "and spilled one time sand, " + (timeSand(helper) - sandBefore[0]));
        });
        helper.runAtTickTime(40, () -> helper.assertTrue(f.lunge(null), "the second lunge starts"));
        helper.runAtTickTime(50, () -> move(helper, f, new Vec3(5.5, 1, 5.5)));
        helper.runAtTickTime(61, () -> {
            helper.assertTrue(f.turned() == 2, "run through again, " + f.turned());
            helper.assertTrue(near(lost(f), 13.6F), "6.8 more, lost " + lost(f));
            helper.assertTrue(timeSand(helper) - sandBefore[0] == 1, "and nothing more spilled, " + (timeSand(helper) - sandBefore[0]));
            helper.succeed();
        });
    }

    /** A fencer still on the spot it struck from is not cut by its own echo; the pig ahead still is. */
    private static void ownSpot(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 3.5));
        Pig pig = sturdyPig(helper, new Vec3(5.5, 1, 6.0));
        helper.runAtTickTime(2, () -> helper.assertTrue(f.lunge(null), "the lunge starts"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(f.turned() == 0 && lost(f) < 0.001F, "on its own spot it lost nothing, lost " + lost(f));
            helper.assertTrue(near(lost(pig), 15.3F), "the pig lost 15.3, lost " + lost(pig));
            helper.succeed();
        });
    }

    // ---- the step aside ----------------------------------------------------------------------------------------------

    /** Where {@code f} stands across the line through {@code anchor} facing south: + is east, its left. */
    private static double across(EchoFencer f, Vec3 anchor) {
        return f.getX() - anchor.x;
    }

    /**
     * After the lunge it steps aside out of its line: by the echo it stands 0.8 to 2 blocks off the line, no farther
     * along it, on the floor, and its echo doesn't touch it.
     */
    private static void sidestep(GameTestHelper helper) {
        EchoFencer f = fencer(helper, new Vec3(5.5, 1, 4.5));
        Vec3[] anchor = {null};
        double[] y = {0};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(f.lunge(null), "the lunge starts");
            anchor[0] = f.anchor();
            y[0] = f.getY();
        });
        helper.runAtTickTime(21, () -> {
            helper.assertTrue(f.side() != 0, "it picked a side");
            double off = Math.abs(across(f, anchor[0]));
            helper.assertTrue(off >= 0.8 && off <= 2.0, "it stands 0.8 to 2 blocks off its line, " + off);
            helper.assertTrue(Math.signum(across(f, anchor[0])) == f.side(), "on the side it picked");
            helper.assertTrue(Math.abs(f.getZ() - anchor[0].z) < 0.3, "no farther along it, " + (f.getZ() - anchor[0].z));
            helper.assertTrue(Math.abs(f.getY() - y[0]) < 0.01, "on the floor");
        });
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(f.echoes() == 1 && f.turned() == 0 && lost(f) < 0.001F, "its echo came and missed it");
            helper.succeed();
        });
    }

    /** A pit across one side (3 deep) is never stepped toward: with its left over a pit it steps right. */
    private static void sidestepDrop(GameTestHelper helper) {
        pit(helper, 6, 7);                                              // its left: east of it
        EchoFencer f = fencer(helper, new Vec3(5.5, 1, 4.5));
        double[] x = {0};
        helper.runAtTickTime(2, () -> {
            x[0] = f.getX();
            helper.assertTrue(f.lunge(null), "the lunge starts");
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(f.side() == -1, "it steps right, away from the pit, side " + f.side());
            helper.assertTrue(f.getX() < x[0] - 0.8, "it moved right, " + (f.getX() - x[0]));
            helper.succeed();
        });
    }

    /** With a pit either side it stays where it struck. */
    private static void sidestepNone(GameTestHelper helper) {
        pit(helper, 6, 7);
        pit(helper, 3, 4);
        EchoFencer f = fencer(helper, new Vec3(5.5, 1, 4.5));
        Vec3[] at = {null};
        helper.runAtTickTime(2, () -> {
            at[0] = f.position();
            helper.assertTrue(f.lunge(null), "the lunge starts");
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(f.side() == 0, "no side is safe, side " + f.side());
            helper.assertTrue(f.position().distanceTo(at[0]) < 0.2, "it stays put, moved " + f.position().distanceTo(at[0]));
            helper.succeed();
        });
    }

    /** A pit 3 deep with a stone floor, x {@code from}..{@code to}, z 3 to 6. */
    private static void pit(GameTestHelper helper, int from, int to) {
        for (int x = from; x <= to; x++) {
            for (int z = 3; z <= 6; z++) {
                for (int y = 0; y > -3; y--) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, -3, z), Blocks.STONE);
            }
        }
    }

    // ---- the flags, the cycle, the row -------------------------------------------------------------------------------

    /** The synced flags put the afterimage exactly where it struck, facing the way it struck; they clear after the echo. */
    private static void flags(GameTestHelper helper) {
        EchoFencer f = still(helper, new Vec3(5.5, 1, 3.5));
        face(f, 90.0F);                                                 // facing west
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(f.lunge(null), "the lunge starts");
            int flags = f.core().flags();
            Vec3 at = helper.absoluteVec(new Vec3(5.5, 1, 3.5));
            helper.assertTrue(Afterimage.live(flags), "an afterimage stands");
            helper.assertTrue(Math.abs(Afterimage.x(flags, f.getX()) - at.x) < 1.0E-6 && Math.abs(Afterimage.z(flags, f.getZ()) - at.z) < 1.0E-6
                    && Math.abs(Afterimage.y(flags, f.getY()) - at.y) < 1.0E-6, "where it struck");
            helper.assertTrue(Math.abs(Afterimage.yaw(flags) - 90.0) < 1.0E-6, "facing west, " + Afterimage.yaw(flags));
            helper.assertTrue(Math.abs(Afterimage.x(flags, f.getX() + 7.0) - at.x) < 1.0E-6, "found again from 7 blocks off");
        });
        helper.runAtTickTime(2 + 30 + 1, () -> {
            helper.assertTrue(f.core().idle() && !Afterimage.live(f.core().flags()) && f.anchor() == null, "gone after the echo");
            helper.succeed();
        });
    }

    /**
     * In a fight it lunges every 50 ticks at a pig in reach: a 10-tick tell, the 4-tick lunge, 16 while the afterimage
     * waits, the 10-tick echo, then 10 ticks' rest before the next tell; each lunge has its echo, one afterimage at a time.
     */
    private static void cycle(GameTestHelper helper) {
        EchoFencer f = fencer(helper, new Vec3(5.5, 1, 4.5));
        Pig pig = sturdyPig(helper, new Vec3(5.5, 1, 5.7));
        List<Integer> thrusts = new ArrayList<>(), tells = new ArrayList<>(), rests = new ArrayList<>();
        Step[] last = {Step.IDLE};
        int[] run = {0};
        helper.onEachTick(() -> {
            if (f.getTarget() != pig) f.setTarget(pig);
            Step now = (Step) f.phase();
            if (now != last[0]) {
                if (now == Step.THRUST) thrusts.add((int) helper.getTick());
                if (last[0] == Step.TELL) tells.add(run[0]);
                if (last[0] == Step.IDLE && now == Step.TELL && !thrusts.isEmpty()) rests.add(run[0]);
                run[0] = 0;
            }
            run[0]++;
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(thrusts.size() >= 3, "three lunges so far: " + thrusts);
            helper.assertTrue(thrusts.get(1) - thrusts.get(0) == 50 && thrusts.get(2) - thrusts.get(1) == 50, "one every 50 ticks: " + thrusts);
            helper.assertTrue(tells.stream().allMatch(t -> t == 10), "each tell 10 ticks: " + tells);
            helper.assertTrue(rests.stream().allMatch(t -> t == 10), "10 ticks' rest after each echo: " + rests);
            helper.assertTrue(f.echoes() >= 2, "each lunge has its echo, " + f.echoes());
        });
    }

    /** Health 32, attack 8.5, armour 2, a 0.7 x 2.0 hitbox; a monster; no glow stages; two looks; nothing on it is gold. */
    private static void stats(GameTestHelper helper) {
        EchoFencer f = fencer(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(near(f.getMaxHealth(), 32.0F) && near(f.getHealth(), 32.0F), "health 32, got " + f.getHealth() + "/" + f.getMaxHealth());
            helper.assertTrue(Math.abs(f.getAttributeValue(Attributes.ATTACK_DAMAGE) - 8.5) < 0.01, "attack 8.5");
            helper.assertTrue(Math.abs(f.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(f.getBbWidth() - 0.7F) < 0.02F && Math.abs(f.getBbHeight() - 2.0F) < 0.02F,
                    "hitbox 0.7 x 2.0, got " + f.getBbWidth() + " x " + f.getBbHeight());
            helper.assertTrue(f.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(f.visualStages() == 1 && !f.vulnerable(), "no glow stages and no weak point");
            List<String> looks = Looks.of("echo_fencer").stream().map(LookDef::id).sorted().toList();
            helper.assertTrue(looks.equals(List.of("base", "gilded_fencer")), "looks base and gilded_fencer, got " + looks);
            helper.succeed();
        });
    }

    /** Home: the End midlands only; spawn weight 4, alone (groups of 1). */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("echo_fencer"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "echo_fencer").stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "its home holds the End midlands");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "echo_fencer").stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.THE_END)), "never the highlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/echo_fencer")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.ECHO_FENCER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.ECHO_FENCER.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
