package com.panda.teeming.gametest.mob;

import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.FortressJailer;
import com.panda.teeming.mob.nether.FortressJailer.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fortress Jailer: a 16-tick wind that does nothing yet; a chain thrown at targets 3 to 12 blocks away that flies
 * straight, reaches 12 blocks and stops at the first block or hitbox; a hooked target dragged at 0.8 blocks a tick and
 * struck for 6 once it stands next to it; a raised shield or a wall stops it; one throw every 120 ticks. Every number
 * is checked against a plain value.
 *
 * <p>Every jailer here has its goals cleared and a follow range of 1, so it never notices a survival player in a
 * neighbouring arena; its target is given to it directly, and its chain cycle still runs. Pigs have their goals cleared
 * too, so they stand still but can still be dragged. Long throws run along the arena's diagonal.
 */
public final class FortressJailerTests {
    private FortressJailerTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("fortress_jailer_tell", FortressJailerTests::tell);
        out.accept("fortress_jailer_counter", FortressJailerTests::shieldBlocks);
        out.accept("fortress_jailer_failure", FortressJailerTests::draggedAndStruck);
        out.accept("fortress_jailer_sight_counter", FortressJailerTests::wallStopsChain);
        out.accept("fortress_jailer_pull_speed", FortressJailerTests::pullSpeed);
        out.accept("fortress_jailer_range_in", FortressJailerTests::rangeIn);
        out.accept("fortress_jailer_range_out", FortressJailerTests::rangeOut);
        out.accept("fortress_jailer_min_range", FortressJailerTests::minRange);
        out.accept("fortress_jailer_chain_reach", FortressJailerTests::chainReach);
        out.accept("fortress_jailer_flies_straight", FortressJailerTests::fliesStraight);
        out.accept("fortress_jailer_interval", FortressJailerTests::interval);
        TeemingGameTests.timeLimit("fortress_jailer_interval", 200);
        out.accept("fortress_jailer_passes_jailers", FortressJailerTests::passesJailers);
        out.accept("fortress_jailer_frees_on_death", FortressJailerTests::freesOnDeath);
        out.accept("fortress_jailer_stats", FortressJailerTests::stats);
        out.accept("fortress_jailer_home", FortressJailerTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A pig that stands still (no goals) but still moves when dragged. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.removeFreeWill();
        return pig;
    }

    /** A jailer with no goals and a follow range of 1, hunting {@code target} (with none it only throws when told to). */
    private static FortressJailer jailer(GameTestHelper helper, double x, double z, net.minecraft.world.entity.@Nullable LivingEntity target) {
        FortressJailer j = helper.spawn(ModMobs.FORTRESS_JAILER.get(), new Vec3(x, 1, z));
        j.removeFreeWill();
        j.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        j.setTarget(target);
        return j;
    }

    /** Records the test tick of each mechanic step of one jailer; call {@link #stop} when done. */
    private static final class Log implements Consumer<MechanicEvent> {
        final FortressJailer jailer;
        final List<Integer> tells = new ArrayList<>(), throwsAt = new ArrayList<>(), pulls = new ArrayList<>(),
                strikes = new ArrayList<>(), reels = new ArrayList<>();
        boolean cancelPull;
        int tick;

        Log(GameTestHelper helper, FortressJailer jailer) {
            this.jailer = jailer;
            MechanicEvent.listen(this);
            TestPlayers.whenDone(helper, this::stop);
            helper.onEachTick(() -> tick++);
        }

        @Override
        public void accept(MechanicEvent e) {
            if (e.mob() != jailer) return;
            switch (e.step()) {
                case "tell" -> tells.add(tick);
                case "throw" -> throwsAt.add(tick);
                case "pull" -> {
                    pulls.add(tick);
                    if (cancelPull) e.cancel();
                }
                case "strike" -> strikes.add(tick);
                case "reel" -> reels.add(tick);
                default -> { }
            }
        }

        void stop() {
            MechanicEvent.unlisten(this);
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /** The wind lasts exactly 16 ticks: it stands still, nothing is thrown, hooked or hurt, and the throw follows. */
    private static void tell(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 5.5);
        FortressJailer j = jailer(helper, 2.5, 5.5, pig);
        Log log = new Log(helper, j);
        int[] ticks = {0};
        boolean[] quiet = {true}, ended = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (j.core().in(Step.TELL)) {
                if (start[0] == null) start[0] = j.position();
                ticks[0]++;
                moved[0] = Math.max(moved[0], j.position().distanceTo(start[0]));
                if (!log.throwsAt.isEmpty() || j.hooked() != null || pig.getHealth() < pig.getMaxHealth()) quiet[0] = false;
            } else if (ticks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the wind runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(log.tells.size() == 1 && log.tells.getFirst() <= 3, "with its target 7 blocks off it winds at once: " + log.tells);
            helper.assertTrue(ticks[0] == 16, "the wind lasts 16 ticks, lasted " + ticks[0]);
            helper.assertTrue(quiet[0], "nothing is thrown, hooked or hurt during the wind");
            helper.assertTrue(moved[0] < 0.05, "it stands still while it winds, moved " + moved[0]);
            helper.assertTrue(log.throwsAt.size() == 1, "the throw follows the wind, saw " + log.throwsAt.size());
        });
    }

    /**
     * The counter: a shield raised toward it takes the chain. The chain reaches the player and drops; nothing is
     * hooked or pulled, the player is unhurt and stays put, and the jailer reels its chain back in.
     */
    private static void shieldBlocks(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(9.5, 1, 5.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            p.setYRot(90.0F);            // facing -x, toward the jailer
            p.setYHeadRot(90.0F);
            p.yHeadRotO = 90.0F;
        });
        FortressJailer j = jailer(helper, 2.5, 5.5, p);
        Log log = new Log(helper, j);
        Vec3 at = p.position();
        float before = p.getHealth();
        boolean[] hooked = {false};
        helper.onEachTick(() -> hooked[0] |= j.hooked() != null);
        helper.succeedWhen(() -> {
            helper.assertTrue(p.isBlocking(), "the player holds the shield up");
            helper.assertTrue(log.throwsAt.size() == 1 && log.reels.size() == 1, "the chain was thrown and is reeled back: throws "
                    + log.throwsAt + ", reels " + log.reels);
            helper.assertTrue(j.chainTravelled() > 5.0 && j.chainTravelled() < 6.6, "the chain reached the player 7 blocks off, travelled " + j.chainTravelled());
            helper.assertTrue(!hooked[0] && log.pulls.isEmpty(), "nothing was hooked");
            helper.assertTrue(p.position().distanceTo(at) < 0.01 && p.getDeltaMovement().horizontalDistance() < 0.01, "the player was not pulled");
            helper.assertTrue(p.getHealth() >= before, "the player is unhurt, health " + p.getHealth());
        });
    }

    /** Failing the counter: the pig is hooked, dragged to its side and struck for 6. */
    private static void draggedAndStruck(GameTestHelper helper) {
        Pig pig = pig(helper, 10.5, 5.5);
        FortressJailer j = jailer(helper, 2.5, 5.5, pig);
        Log log = new Log(helper, j);
        float before = pig.getHealth();
        float[] lost = {-1.0F};
        double[] across = {-1.0};
        boolean[] striking = {false};
        helper.onEachTick(() -> {
            if (j.core().in(Step.STRIKE)) {
                if (!striking[0]) across[0] = Math.hypot(pig.getX() - j.getX(), pig.getZ() - j.getZ());
                striking[0] = true;
            } else if (striking[0] && lost[0] < 0) {
                lost[0] = before - pig.getHealth();     // the whole strike, measured once it is over
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.pulls.size() == 1, "the pig was hooked once, " + log.pulls);
            helper.assertTrue(lost[0] >= 0, "it struck and the strike ended, phase " + j.phase());
            helper.assertTrue(Math.abs(lost[0] - 6.0F) < 0.01F, "the strike does 6, the pig lost " + lost[0]);
            helper.assertTrue(across[0] <= 1.6 && across[0] > 0.6, "the pig was dragged to its side, " + across[0] + " blocks across");
        });
    }

    /** The other counter: a wall put up during the wind stops the chain at the wall; nothing is hooked or hurt. */
    private static void wallStopsChain(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 5.5);
        FortressJailer j = jailer(helper, 2.5, 5.5, pig);
        Log log = new Log(helper, j);
        boolean[] walled = {false}, hooked = {false};
        helper.onEachTick(() -> {
            if (!walled[0] && j.core().in(Step.TELL) && j.core().elapsed() >= 4) {
                walled[0] = true;
                for (int y = 1; y <= 4; y++) {
                    for (int z = 3; z <= 8; z++) helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
                }
            }
            hooked[0] |= j.hooked() != null;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(walled[0] && log.throwsAt.size() == 1 && log.reels.size() == 1, "thrown into the wall and reeled back: throws "
                    + log.throwsAt + ", reels " + log.reels);
            helper.assertTrue(j.chainTravelled() > 2.0 && j.chainTravelled() < 3.3, "the chain stopped at the wall 3.5 blocks off, travelled " + j.chainTravelled());
            helper.assertTrue(!hooked[0] && log.pulls.isEmpty(), "nothing was hooked");
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "the pig is unhurt");
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** A hooked pig is dragged 0.8 blocks every tick until it is next to the jailer. */
    private static void pullSpeed(GameTestHelper helper) {
        Pig pig = pig(helper, 10.5, 5.5);
        FortressJailer j = jailer(helper, 1.5, 5.5, pig);
        List<Double> steps = new ArrayList<>();
        Vec3[] last = {null};
        helper.onEachTick(() -> {
            if (!j.core().in(Step.PULL)) return;
            if (last[0] != null) steps.add(Math.hypot(pig.getX() - last[0].x, pig.getZ() - last[0].z));
            last[0] = pig.position();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(j.core().in(Step.STRIKE), "the drag ended in a strike, phase " + j.phase());
            helper.assertTrue(steps.size() >= 6, "dragged for several ticks: " + steps);
            // the first step may come a tick late and the last one stops at its side: every step between is 0.8
            for (int i = 1; i < steps.size() - 1; i++) {
                helper.assertTrue(Math.abs(steps.get(i) - 0.8) < 0.02, "dragged 0.8 a tick, the steps were " + steps);
            }
        });
    }

    /** A target 11.8 blocks away is wound up for, thrown at and hooked. */
    private static void rangeIn(GameTestHelper helper) {
        double d = 11.8 / Math.sqrt(2);
        Pig pig = pig(helper, 0.5 + d, 0.5 + d);
        FortressJailer j = jailer(helper, 0.5, 0.5, pig);
        Log log = new Log(helper, j);
        helper.succeedWhen(() -> {
            helper.assertTrue(log.tells.size() == 1 && log.throwsAt.size() == 1, "11.8 blocks is in range: tells " + log.tells + ", throws " + log.throwsAt);
            helper.assertTrue(log.pulls.size() == 1, "and the chain hooks it, " + log.pulls);
        });
    }

    /** A target 12.4 blocks away, in plain sight, is never thrown at. */
    private static void rangeOut(GameTestHelper helper) {
        double d = 12.4 / Math.sqrt(2);
        Pig pig = pig(helper, 0.5 + d, 0.5 + d);
        FortressJailer j = jailer(helper, 0.5, 0.5, pig);
        Log log = new Log(helper, j);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(log.tells.isEmpty() && log.throwsAt.isEmpty(), "12.4 blocks is out of range: tells " + log.tells + ", throws " + log.throwsAt);
            helper.assertTrue(j.core().idle(), "it stays idle, phase " + j.phase());
            helper.succeed();
        });
    }

    /** A target 2.5 blocks away is too close for the chain: no wind, no throw. */
    private static void minRange(GameTestHelper helper) {
        Pig pig = pig(helper, 5.0, 5.5);
        FortressJailer j = jailer(helper, 2.5, 5.5, pig);
        Log log = new Log(helper, j);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(log.tells.isEmpty() && log.throwsAt.isEmpty(), "2.5 blocks is too close for the chain: tells " + log.tells);
            helper.succeed();
        });
    }

    /** Thrown at a pig 15 blocks off along the diagonal, the chain goes 12 blocks and no further, and hooks nothing. */
    private static void chainReach(GameTestHelper helper) {
        Pig pig = pig(helper, 11.1, 11.1);
        FortressJailer j = jailer(helper, 0.5, 0.5, null);
        Log log = new Log(helper, j);
        helper.runAtTickTime(1, () -> helper.assertTrue(j.throwAt(pig), "the chain is thrown"));
        helper.succeedWhen(() -> {
            helper.assertTrue(log.reels.size() == 1, "the chain came to its end and is reeled in, phase " + j.phase());
            helper.assertTrue(Math.abs(j.chainTravelled() - 12.0) < 1.0E-4, "the chain goes exactly 12 blocks, went " + j.chainTravelled());
            helper.assertTrue(log.pulls.isEmpty() && pig.getHealth() >= pig.getMaxHealth(), "it fell short of the pig");
        });
    }

    /** The chain flies straight: a pig that steps aside as it is thrown is missed, and the chain carries on past. */
    private static void fliesStraight(GameTestHelper helper) {
        Pig pig = pig(helper, 7.5, 7.5);
        FortressJailer j = jailer(helper, 1.5, 1.5, null);
        Log log = new Log(helper, j);
        Vec3 aside = helper.absoluteVec(new Vec3(9.5, 1, 5.0));
        helper.runAtTickTime(1, () -> helper.assertTrue(j.throwAt(pig), "the chain is thrown"));
        helper.runAtTickTime(2, () -> pig.teleportTo(aside.x, aside.y, aside.z));
        helper.succeedWhen(() -> {
            helper.assertTrue(log.reels.size() == 1, "the chain ran out and is reeled in, phase " + j.phase());
            helper.assertTrue(log.pulls.isEmpty() && j.hooked() == null, "the pig that stepped aside was not hooked");
            helper.assertTrue(j.chainTravelled() > 9.0, "the chain flew on past where the pig had stood, travelled " + j.chainTravelled());
        });
    }

    /** With its target always in reach it throws once every 120 ticks (the hook is vetoed so nothing is dragged). */
    private static void interval(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 5.5);
        FortressJailer j = jailer(helper, 2.5, 5.5, pig);
        Log log = new Log(helper, j);
        log.cancelPull = true;
        helper.succeedWhen(() -> {
            helper.assertTrue(log.throwsAt.size() >= 2, "two throws so far: " + log.throwsAt);
            int gap = log.throwsAt.get(1) - log.throwsAt.get(0);
            helper.assertTrue(gap == 120, "one throw every 120 ticks, the gap was " + gap);
            helper.assertTrue(log.tells.size() >= 2 && log.tells.get(1) - log.tells.get(0) == 120, "and one wind every 120 ticks: " + log.tells);
        });
    }

    /** Its chain passes through another jailer standing in its line and hooks the pig behind it; the jailer is untouched. */
    private static void passesJailers(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 5.5);
        FortressJailer j = jailer(helper, 2.5, 5.5, pig);
        FortressJailer between = jailer(helper, 5.5, 5.5, null);
        between.setNoAi(true);
        Log log = new Log(helper, j);
        boolean[] hookedJailer = {false};
        helper.onEachTick(() -> hookedJailer[0] |= j.hooked() == between);
        helper.succeedWhen(() -> {
            helper.assertTrue(log.pulls.size() == 1 && log.strikes.size() == 1, "the pig behind was hooked and struck: pulls " + log.pulls
                    + ", strikes " + log.strikes);
            helper.assertTrue(!hookedJailer[0] && between.getHealth() >= between.getMaxHealth(), "the jailer in between was neither hooked nor hurt");
        });
    }

    /** A jailer killed while it drags a pig lets go: the pig coasts to a stop instead of being pulled on. */
    private static void freesOnDeath(GameTestHelper helper) {
        Pig pig = pig(helper, 10.5, 5.5);
        FortressJailer j = jailer(helper, 1.5, 5.5, pig);
        int[] killedAt = {-1};
        int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (killedAt[0] < 0 && j.core().in(Step.PULL) && j.core().elapsed() >= 2) {
                killedAt[0] = tick[0];
                j.kill(helper.getLevel());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killedAt[0] > 0 && tick[0] >= killedAt[0] + 8, "killed mid-drag, waiting for the pig to stop");
            helper.assertTrue(j.hooked() == null, "the dead jailer holds nothing");
            helper.assertTrue(pig.getDeltaMovement().horizontalDistance() < 0.05, "the pig is no longer dragged, speed " + pig.getDeltaMovement().horizontalDistance());
            helper.assertTrue(Math.hypot(pig.getX() - j.getX(), pig.getZ() - j.getZ()) > 2.0, "it stopped short of the jailer");
        });
    }

    /** Health 36, attack 6, armour 6, a 0.8 x 2.2 hitbox. */
    private static void stats(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 9.5);
        FortressJailer j = jailer(helper, 5.5, 5.5, pig);
        j.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(j.getMaxHealth() - 36.0F) < 0.01F && Math.abs(j.getHealth() - 36.0F) < 0.01F,
                    "health 36, got " + j.getHealth() + "/" + j.getMaxHealth());
            helper.assertTrue(Math.abs(j.getAttributeValue(Attributes.ATTACK_DAMAGE) - 6.0) < 0.01, "attack 6");
            helper.assertTrue(Math.abs(j.getAttributeValue(Attributes.ARMOR) - 6.0) < 0.01, "armour 6");
            helper.assertTrue(Math.abs(j.getBbWidth() - 0.8F) < 0.02F && Math.abs(j.getBbHeight() - 2.2F) < 0.02F,
                    "hitbox 0.8 x 2.2, got " + j.getBbWidth() + " x " + j.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: placed by the Teeming spawner's fortress rule only (no biome spawns), alone. */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("fortress_jailer");
        helper.assertTrue(row.spawner().equals("fortress") && TeemingSpawner.Rule.of(row.spawner()) == TeemingSpawner.Rule.FORTRESS,
                "the fortress rule places it, got '" + row.spawner() + "'");
        helper.assertTrue(row.weight() == 0 && !row.biomeSpawns(), "no biome spawns, weight " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "alone, groups of " + row.groupMin() + "-" + row.groupMax());
        helper.assertTrue(row.dim() == RosterStats.Dim.NETHER, "a Nether mob");
        helper.succeed();
    }
}
