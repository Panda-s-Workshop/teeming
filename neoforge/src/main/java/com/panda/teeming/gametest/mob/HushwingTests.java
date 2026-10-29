package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.Hushwing;
import com.panda.teeming.mob.overworld.Hushwing.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.SpawnTable;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Hushwing: blind, it hears a player sprinting within 16 blocks, fighting within 12 and mining within 10, and never
 * hears walking or sneaking. There is no tell: hearing shows as a 10-tick ear flick while it is already flying at the
 * noise, and a bite (its attack, 3) needs no warning. A projectile's impact within 16 blocks is a decoy that holds it
 * for 100 ticks, deaf to anything else. HP 8, Atk 3, Arm 0, 0.5 x 0.5; below y 0 in the Overworld, weight 15, flocks of
 * 3 to 4.
 *
 * <p>It hunts survival players only, and the GameTest mock player always counts as creative, so these tests use real
 * survival players ({@link TestPlayers#survivor}). Nothing ticks those players here: a sprint flag stays set until a
 * test clears it, and where a test needs more than one bite to land, it counts the player's hurt cooldown down by hand
 * as the player's own tick would. Every Hushwing is told to hear only the test's own players and projectiles, so a
 * fight or a sprint in a neighbouring arena can't reach it. The range tests stack Hushwings in the open column over
 * the arena (its barrier roof removed) so the distances can reach past 16.
 */
public final class HushwingTests {
    private HushwingTests() {}

    /** Where the player stands: the middle of the arena floor. */
    private static final Vec3 GROUND = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("hushwing_tell", HushwingTests::tell);
        out.accept("hushwing_counter", HushwingTests::counter);
        out.accept("hushwing_failure", HushwingTests::failure);
        out.accept("hushwing_no_warning", HushwingTests::noWarning);
        out.accept("hushwing_sprint_range", HushwingTests::sprintRange);
        out.accept("hushwing_combat_range", HushwingTests::combatRange);
        out.accept("hushwing_attack_range", HushwingTests::attackRange);
        out.accept("hushwing_mining_range", HushwingTests::miningRange);
        out.accept("hushwing_quiet", HushwingTests::quiet);
        out.accept("hushwing_decoy_range", HushwingTests::decoyRange);
        out.accept("hushwing_decoy_length", HushwingTests::decoyLength);
        TeemingGameTests.timeLimit("hushwing_decoy_length", 180);
        out.accept("hushwing_hit_breaks_decoy", HushwingTests::hitBreaksDecoy);
        out.accept("hushwing_lost", HushwingTests::lost);
        TeemingGameTests.timeLimit("hushwing_lost", 140);
        out.accept("hushwing_bite", HushwingTests::bite);
        out.accept("hushwing_bite_cancel", HushwingTests::biteCancel);
        out.accept("hushwing_stats", HushwingTests::stats);
        out.accept("hushwing_home", HushwingTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A Hushwing hovering at {@code at} that stays put until it hears something, and hears only {@code allowed}. */
    private static Hushwing bat(GameTestHelper helper, Vec3 at, Set<Entity> allowed) {
        Hushwing bat = helper.spawn(ModMobs.HUSHWING.get(), at);
        bat.removeFreeWill();         // no flitting about; its hearing and chase still run
        bat.hearOnly(allowed::contains);
        TestPlayers.whenDone(helper, bat::discard);
        return bat;
    }

    /** A real survival player at {@code at}; the Hushwings of the test hear it. */
    private static ServerPlayer survivor(GameTestHelper helper, Vec3 at, Set<Entity> allowed) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        allowed.add(p);
        return p;
    }

    /** Counts the player's hurt cooldown down each tick, as its own tick would, so bites can land one after another. */
    private static void coolDown(GameTestHelper helper, ServerPlayer p) {
        helper.onEachTick(() -> p.damageCooldownTime = Math.max(0, p.damageCooldownTime - 1));
    }

    /**
     * A snowball dropped straight down from half a block over the floor at ({@code x}, {@code z}), so it lands on the
     * floor inside this arena on its next tick; the test's Hushwings hear its impact. One still in the air when the test
     * ends is removed, so nothing of this test can reach a neighbouring arena.
     */
    private static Snowball drop(GameTestHelper helper, double x, double z, Set<Entity> allowed) {
        Snowball ball = helper.spawn(EntityTypes.SNOWBALL, new Vec3(x, 1.5, z));
        ball.setDeltaMovement(0, -0.6, 0);
        allowed.add(ball);
        TestPlayers.whenDone(helper, () -> {
            if (!ball.isRemoved()) ball.discard();
        });
        return ball;
    }

    /** Mob damage to players scales with difficulty: Atk 3 is 2.5 on Easy, 3 on Normal, 4.5 on Hard. */
    private static float biteOnPlayer(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case EASY -> 2.5F;
            case HARD -> 4.5F;
            default -> 3.0F;
        };
    }

    /**
     * Clears the barrier roof over the arena (the two layers above its 8 rows) so the column over it is open, and checks
     * it is open up to y 20.
     */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        for (int y = 2; y <= 20; y++) {
            BlockPos pos = new BlockPos(5, y, 5);
            if (!helper.getBlockState(pos).isAir()) helper.fail("the column over the arena is shut at y " + y + " by " + helper.getBlockState(pos));
        }
    }

    /**
     * Two Hushwings straight over the player's feet, {@code near} and {@code far} blocks up; {@code noise} runs at tick
     * 2. A few ticks later the near one has heard it (it is chasing) and the far one hasn't.
     */
    private static void range(GameTestHelper helper, double near, double far, String what, BiConsumer<ServerPlayer, Set<Entity>> noise) {
        openRoof(helper);
        Set<Entity> allowed = new HashSet<>();
        ServerPlayer p = survivor(helper, GROUND, allowed);
        Hushwing heard = bat(helper, GROUND.add(0, near, 0), allowed);
        Hushwing deaf = bat(helper, GROUND.add(0, far, 0), allowed);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(heard.distanceTo(p) - near) < 0.05 && Math.abs(deaf.distanceTo(p) - far) < 0.05,
                    "they hover " + near + " and " + far + " away, are " + heard.distanceTo(p) + " and " + deaf.distanceTo(p));
            noise.accept(p, allowed);
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(heard.noiseAt() != null && !heard.core().idle(), what + " " + near + " blocks away is heard, phase " + heard.phase());
            helper.assertTrue(deaf.noiseAt() == null && deaf.core().idle(), what + " " + far + " blocks away is not heard, phase " + deaf.phase());
            helper.succeed();
        });
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * No tell, a hearing cue instead: a player 6 blocks off starts sprinting, and within a tick or two its ears flick
     * (the 10-tick HEAR) while it is already flying at them; the chase follows, and nothing was hurt during the cue.
     */
    private static void tell(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        ServerPlayer p = survivor(helper, new Vec3(5.5, 1, 9.5), allowed);
        Hushwing bat = bat(helper, new Vec3(5.5, 1.5, 3.5), allowed);
        float before = p.getHealth();
        int[] ticks = {0};
        long[] first = {-1};
        double[] start = {-1}, end = {-1};
        boolean[] harmed = {false}, hunted = {false};
        helper.runAtTickTime(2, () -> p.setSprinting(true));
        helper.onEachTick(() -> {
            if (bat.core().in(Step.HUNT)) hunted[0] = true;
            if (!bat.core().in(Step.HEAR)) return;
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = bat.distanceTo(p);
            }
            ticks[0]++;
            end[0] = bat.distanceTo(p);
            harmed[0] |= p.getHealth() < before;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && !bat.core().in(Step.HEAR), "the ear flick runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(first[0] >= 2 && first[0] <= 4, "it hears the sprint at once, flick started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 10) <= 1, "the ear flick lasts 10 ticks, lasted " + ticks[0]);
            helper.assertTrue(start[0] - end[0] >= 1.5, "it flies at the noise during the flick (no wait), closed " + (start[0] - end[0]));
            helper.assertTrue(!harmed[0], "nothing is hurt during the flick from 6 blocks away");
            helper.assertTrue(hunted[0], "the chase follows, phase " + bat.phase());
        });
    }

    /**
     * The counter: a Hushwing chasing a sprinting player hears a snowball land 7 blocks the other way. It turns to the
     * impact and searches there, deaf to the sprint that goes on, and the player is never bitten.
     */
    private static void counter(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        ServerPlayer p = survivor(helper, new Vec3(5.5, 1, 10.5), allowed);
        p.setSprinting(true);
        Hushwing bat = bat(helper, new Vec3(5.5, 1.5, 4.5), allowed);
        float before = p.getHealth();
        Vec3 impact = helper.absoluteVec(new Vec3(5.5, 1, 1.5));
        boolean[] chased = {false}, decoyed = {false}, harmed = {false}, turned = {false};
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            harmed[0] |= p.getHealth() < before;
            if (bat.decoyed()) {
                decoyed[0] = true;
                closest[0] = Math.min(closest[0], bat.position().distanceTo(impact));
                turned[0] |= bat.noiseAt() != null && bat.noiseAt().distanceTo(impact) < 0.6;
            } else if (bat.noiseAt() != null) {
                chased[0] = true;
            }
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(chased[0], "it heard the sprint and is chasing, phase " + bat.phase());
            drop(helper, 5.5, 1.5, allowed);
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(decoyed[0] && bat.decoyed() && bat.core().in(Step.DECOY), "the impact holds it, phase " + bat.phase());
            helper.assertTrue(turned[0], "it flies to where the snowball landed, aiming at " + bat.noiseAt());
            helper.assertTrue(closest[0] < 2.0, "it reaches the impact and searches there, came within " + closest[0]);
            helper.assertTrue(p.isSprinting() && !harmed[0], "the player sprinting on the other side is never bitten");
            helper.succeed();
        });
    }

    /**
     * Failing the counter: a flock of three hears a player sprinting 6 to 7 blocks off and swarms them. Bites land one
     * after another, each its attack (difficulty-scaled), from more than one Hushwing.
     */
    private static void failure(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        ServerPlayer p = survivor(helper, new Vec3(5.5, 1, 8.5), allowed);
        p.setSprinting(true);
        coolDown(helper, p);
        List<Hushwing> flock = List.of(bat(helper, new Vec3(2.5, 1.5, 2.5), allowed), bat(helper, new Vec3(8.5, 1.5, 2.5), allowed),
                bat(helper, new Vec3(5.5, 1.5, 2.0), allowed));
        float bite = biteOnPlayer(helper);
        float[] last = {p.getHealth()};
        int[] bites = {0};
        float[] wrong = {-1};
        Set<Hushwing> biters = new HashSet<>();
        helper.onEachTick(() -> {
            float lost = last[0] - p.getHealth();
            if (lost > 0.01F) {
                bites[0]++;
                if (Math.abs(lost - bite) > 0.01F) wrong[0] = lost;
            }
            last[0] = p.getHealth();
            for (Hushwing bat : flock) if (bat.core().in(Step.BITE)) biters.add(bat);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bites[0] >= 3, "bite after bite lands, " + bites[0] + " so far");
            helper.assertTrue(wrong[0] < 0, "each bite is its attack (" + bite + " on this difficulty), one took " + wrong[0]);
            helper.assertTrue(biters.size() >= 2, "more than one of the flock bites, " + biters.size() + " so far");
        });
    }

    /** No warning before a bite: a Hushwing right beside a player bites within two ticks of hearing them sprint. */
    private static void noWarning(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        ServerPlayer p = survivor(helper, GROUND, allowed);
        Hushwing bat = bat(helper, GROUND.add(0, 0.9, 0.75), allowed);
        float before = p.getHealth();
        long[] heard = {-1}, bitten = {-1};
        helper.runAtTickTime(3, () -> p.setSprinting(true));
        helper.onEachTick(() -> {
            if (heard[0] < 0 && bat.noiseAt() != null) heard[0] = helper.getTick();
            if (bitten[0] < 0 && p.getHealth() < before) bitten[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(heard[0] >= 3, "it hears the sprint, at tick " + heard[0]);
            helper.assertTrue(bitten[0] >= 0, "it bites");
            helper.assertTrue(bitten[0] - heard[0] <= 2, "the bite comes with no warning: heard at " + heard[0] + ", bitten at " + bitten[0]);
            helper.assertTrue(Math.abs(before - p.getHealth() - biteOnPlayer(helper)) < 0.01F, "one bite is its attack, lost " + (before - p.getHealth()));
        });
    }

    // ---- hearing ranges --------------------------------------------------------------------------------------------

    /** Sprinting is heard 15.5 blocks away and not 16.5. */
    private static void sprintRange(GameTestHelper helper) {
        range(helper, 15.5, 16.5, "a sprint", (p, allowed) -> p.setSprinting(true));
    }

    /** Being hurt is a fight: heard 11.5 blocks away and not 12.5. */
    private static void combatRange(GameTestHelper helper) {
        range(helper, 11.5, 12.5, "a player being hurt", (p, allowed) -> p.hurtServer(helper.getLevel(), p.damageSources().magic(), 1.0F));
    }

    /** Hurting something is a fight too: a player hitting a pig is heard 11.5 blocks away and not 12.5. */
    private static void attackRange(GameTestHelper helper) {
        range(helper, 11.5, 12.5, "a player hitting a pig", (p, allowed) -> {
            Pig pig = helper.spawn(EntityTypes.PIG, GROUND.add(1.0, 0, 0));
            pig.setNoAi(true);
            pig.hurtServer(helper.getLevel(), pig.damageSources().playerAttack(p), 1.0F);
        });
    }

    /** Mining: a player breaking a block is heard 9.5 blocks away and not 10.5. */
    private static void miningRange(GameTestHelper helper) {
        BlockPos stone = new BlockPos(6, 1, 5);
        helper.setBlock(stone, Blocks.STONE);
        range(helper, 9.5, 10.5, "a player breaking a block", (p, allowed) -> {
            boolean broke = helper.getLevel().destroyBlock(helper.absolutePos(stone), false, p);
            helper.assertTrue(broke, "the stone was broken");
        });
    }

    /**
     * Walking and sneaking make no noise: a player walks past 2 blocks under it, then sneaks back, for 60 ticks, and
     * it never hears them. Then the same player sprints, and it hears that at once.
     */
    private static void quiet(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        ServerPlayer p = survivor(helper, new Vec3(2.5, 1, 5.5), allowed);
        Hushwing bat = bat(helper, new Vec3(5.5, 3.0, 5.5), allowed);
        float before = p.getHealth();
        boolean[] heardQuiet = {false};
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t < 2 || t > 62) return;
            boolean sneaking = t > 32;
            p.setShiftKeyDown(sneaking);
            p.setPose(sneaking ? Pose.CROUCHING : Pose.STANDING);
            double x = sneaking ? 8.5 - (t - 32) * 0.1 : 2.5 + (t - 2) * 0.2;   // walking 0.2 a tick out, sneaking 0.1 back
            p.snapTo(helper.absoluteVec(new Vec3(x, 1, 5.5)));
            heardQuiet[0] |= bat.noiseAt() != null || !bat.core().idle();
        });
        helper.runAtTickTime(64, () -> {
            helper.assertTrue(!heardQuiet[0], "walking and sneaking within 2 to 3 blocks are never heard");
            helper.assertTrue(p.getHealth() >= before, "the quiet player is never bitten");
            p.setShiftKeyDown(false);
            p.setPose(Pose.STANDING);
            p.setSprinting(true);
        });
        helper.runAtTickTime(68, () -> {
            helper.assertTrue(bat.noiseAt() != null && !bat.core().idle(), "the same player sprinting is heard at once, phase " + bat.phase());
            helper.succeed();
        });
    }

    /** A snowball landing is a decoy heard 15.5 blocks away and not 16.5. */
    private static void decoyRange(GameTestHelper helper) {
        openRoof(helper);
        Set<Entity> allowed = new HashSet<>();
        // a block hit is heard from the middle of the block struck, half a block under the floor's top
        Hushwing heard = bat(helper, GROUND.add(0, 15.0, 0), allowed);
        Hushwing deaf = bat(helper, GROUND.add(0, 16.0, 0), allowed);
        Vec3 struck = helper.absoluteVec(GROUND.add(0, -0.5, 0));
        Vec3 over = helper.absoluteVec(GROUND.add(0, 0.3, 0));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(heard.position().distanceTo(struck) - 15.5) < 0.05 && Math.abs(deaf.position().distanceTo(struck) - 16.5) < 0.05,
                    "they hover 15.5 and 16.5 from where the impact is heard, are " + heard.position().distanceTo(struck) + " and " + deaf.position().distanceTo(struck));
            drop(helper, GROUND.x, GROUND.z, allowed);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(heard.decoyed(), "an impact 15.5 blocks away draws it, phase " + heard.phase());
            helper.assertTrue(heard.noiseAt() != null && heard.noiseAt().distanceTo(over) < 0.3, "it searches just over where the snowball landed, " + heard.noiseAt());
            helper.assertTrue(!deaf.decoyed() && deaf.noiseAt() == null && deaf.core().idle(), "an impact 16.5 blocks away is not heard, phase " + deaf.phase());
        });
    }

    /**
     * A decoy holds it for 100 ticks: through them it searches the impact, deaf to a player sprinting 7 blocks away;
     * then it hears the sprint again at once.
     */
    private static void decoyLength(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        ServerPlayer p = survivor(helper, new Vec3(5.5, 1, 10.5), allowed);
        p.setSprinting(true);
        Hushwing bat = bat(helper, new Vec3(5.5, 1.5, 4.5), allowed);
        Vec3 impact = helper.absoluteVec(new Vec3(5.5, 1, 1.5));
        int[] held = {0};
        long[] over = {-1}, again = {-1};
        boolean[] strayed = {false};
        helper.runAtTickTime(5, () -> drop(helper, 5.5, 1.5, allowed));
        helper.onEachTick(() -> {
            if (bat.core().in(Step.DECOY)) {
                held[0]++;
                strayed[0] |= bat.noiseAt() == null || bat.noiseAt().distanceTo(impact) > 0.6;
            } else if (held[0] > 0 && over[0] < 0) {
                over[0] = helper.getTick();
            }
            if (over[0] >= 0 && again[0] < 0 && bat.core().in(Step.HEAR)) again[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0] >= 0, "the decoy ends, held " + held[0] + " ticks so far");
            helper.assertTrue(Math.abs(held[0] - 100) <= 1, "the decoy holds it for 100 ticks, held " + held[0]);
            helper.assertTrue(!strayed[0], "all that time it flies to the impact, deaf to the sprint");
            helper.assertTrue(again[0] >= 0 && again[0] - over[0] <= 2, "then it hears the sprint again at once, at " + again[0] + " (ended " + over[0] + ")");
        });
    }

    /** Hit by a player within 12 blocks, a Hushwing held by a decoy hears its attacker and turns on them. */
    private static void hitBreaksDecoy(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        ServerPlayer p = survivor(helper, new Vec3(5.5, 1, 9.5), allowed);
        Hushwing bat = bat(helper, new Vec3(5.5, 1.5, 4.5), allowed);
        helper.runAtTickTime(2, () -> drop(helper, 5.5, 1.5, allowed));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(bat.decoyed(), "the impact holds it, phase " + bat.phase());
            bat.hurtServer(helper.getLevel(), bat.damageSources().playerAttack(p), 1.0F);
        });
        helper.runAtTickTime(23, () -> {
            helper.assertTrue(!bat.decoyed(), "the hit breaks the decoy");
            helper.assertTrue(bat.noiseAt() != null && bat.noiseAt().distanceTo(p.position()) < 1.5, "it heard its attacker, aiming at " + bat.noiseAt());
            helper.assertTrue(bat.getTarget() == p, "and turns on them, target " + bat.getTarget());
            helper.succeed();
        });
    }

    /**
     * Going quiet loses it: a player sprints for a tick, then walks away. It flies to where it heard them, finds no one,
     * and gives up 60 ticks after the last noise. The player is never bitten.
     */
    private static void lost(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        ServerPlayer p = survivor(helper, new Vec3(5.5, 1, 9.5), allowed);
        Hushwing bat = bat(helper, new Vec3(5.5, 1.5, 2.5), allowed);
        Vec3 heardAt = helper.absoluteVec(new Vec3(5.5, 1, 9.5));
        float before = p.getHealth();
        long[] quiet = {-1}, idle = {-1};
        double[] closest = {Double.MAX_VALUE};
        helper.runAtTickTime(2, () -> p.setSprinting(true));
        helper.runAtTickTime(4, () -> {
            p.setSprinting(false);
            quiet[0] = helper.getTick();
            p.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 6.5)));   // walked off, without a sound
        });
        helper.onEachTick(() -> {
            if (quiet[0] < 0) return;
            closest[0] = Math.min(closest[0], bat.position().distanceTo(heardAt));
            if (idle[0] < 0 && bat.core().idle()) idle[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(idle[0] >= 0, "it gives up, phase " + bat.phase() + ", " + (helper.getTick() - quiet[0]) + " ticks after the last noise");
            helper.assertTrue(closest[0] < 2.0, "it flew to where it heard the player, came within " + closest[0]);
            long gap = idle[0] - quiet[0];
            helper.assertTrue(gap >= 59 && gap <= 63, "it gives up 60 ticks after the last noise, gave up after " + gap);
            helper.assertTrue(p.getHealth() >= before, "the player who went quiet is never bitten");
            helper.assertTrue(bat.noiseAt() == null && bat.getTarget() == null, "and it forgets them");
        });
    }

    // ---- the bite ----------------------------------------------------------------------------------------------------

    /**
     * A target handed to it counts as heard, and the bite numbers on a pig (no difficulty scaling): each bite is exactly
     * its attack, 3, and one Hushwing bites every 20 ticks.
     */
    private static void bite(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        Pig pig = helper.spawn(EntityTypes.PIG, GROUND);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        allowed.add(pig);
        Hushwing bat = bat(helper, GROUND.add(0, 1.0, 2.0), allowed);
        bat.setTarget(pig);
        float[] last = {100.0F};
        long[] times = {-1, -1};
        float[] wrong = {-1};
        int[] bites = {0};
        helper.onEachTick(() -> {
            float lost = last[0] - pig.getHealth();
            last[0] = pig.getHealth();
            if (lost < 0.01F) return;
            if (Math.abs(lost - 3.0F) > 0.01F) wrong[0] = lost;
            if (bites[0] < 2) times[bites[0]] = helper.getTick();
            bites[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bites[0] >= 2, "it bites the pig it was handed twice, " + bites[0] + " so far");
            helper.assertTrue(wrong[0] < 0, "each bite is its attack, 3; one took " + wrong[0]);
            long gap = times[1] - times[0];
            helper.assertTrue(gap >= 19 && gap <= 21, "20 ticks from one bite to the next, saw " + gap);
        });
    }

    /** A listener that cancels the "bite" step spares the target: no damage, and it keeps chasing. */
    private static void biteCancel(GameTestHelper helper) {
        Set<Entity> allowed = new HashSet<>();
        Pig pig = helper.spawn(EntityTypes.PIG, GROUND);
        pig.setNoAi(true);
        allowed.add(pig);
        Hushwing bat = bat(helper, GROUND.add(0, 1.0, 1.5), allowed);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == bat && e.step().equals("bite")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        bat.setTarget(pig);
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> closest[0] = Math.min(closest[0], bat.distanceTo(pig)));
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(closest[0] < 1.2, "it reached the pig, came within " + closest[0]);
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "a cancelled bite does no damage");
            helper.assertTrue(bat.core().in(Step.HUNT), "and it keeps chasing, phase " + bat.phase());
            helper.succeed();
        });
    }

    // ---- stats and home --------------------------------------------------------------------------------------------

    /** Health 8, attack 3, armour 0, a 0.5 x 0.5 hitbox; no weak point. */
    private static void stats(GameTestHelper helper) {
        Hushwing bat = helper.spawn(ModMobs.HUSHWING.get(), GROUND.add(0, 1, 0));
        bat.removeFreeWill();
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(bat.getMaxHealth() - 8.0F) < 0.01F && Math.abs(bat.getHealth() - 8.0F) < 0.01F,
                    "health 8, got " + bat.getHealth() + "/" + bat.getMaxHealth());
            helper.assertTrue(Math.abs(bat.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < 0.01, "attack 3");
            helper.assertTrue(Math.abs(bat.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(bat.getBbWidth() - 0.5F) < 0.02F && Math.abs(bat.getBbHeight() - 0.5F) < 0.02F,
                    "hitbox 0.5 x 0.5, got " + bat.getBbWidth() + " x " + bat.getBbHeight());
            helper.assertTrue(!bat.vulnerable(), "no weak point");
            helper.assertTrue(bat.isNoGravity(), "it flies");
            helper.succeed();
        });
    }

    /**
     * Its home: Overworld biomes (caves included), weight 15, in flocks of 3 to 4, a monster of the dark; natural spawns
     * only at y 0 and below.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("hushwing"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        for (var biome : List.of(Biomes.PLAINS, Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES)) {
            helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(biome)), "its home holds " + biome.identifier());
        }
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.NETHER_WASTES)), "never the Nether");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/hushwing")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.HUSHWING.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        // the rest of its tag (a biome mod's biomes; every vanilla biome of its home is tuned) has its rest weight from the
        // spawn table, the share in mobs beside the mobs that live there too, not its roster weight 15
        int rest = SpawnTable.rest("hushwing", 15).weight();
        helper.assertTrue(entry.weight() == rest, "rest weight " + rest + ", got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 3 && entry.value().count().maxInclusive() == 4,
                "in flocks of 3 to 4, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.HUSHWING.get().getCategory() == MobCategory.MONSTER, "a monster");
        RosterStats.Entry row = RosterStats.get("hushwing");
        helper.assertTrue(row.maxY() == 0, "natural spawns stop above y 0, max y " + row.maxY());
        helper.assertTrue(!row.anyLight(), "only in the dark");
        BlockPos abs = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos above = new BlockPos(abs.getX(), 1, abs.getZ());
        boolean[] taken = {false};
        TestPlayers.monstersOn(helper.getLevel(), () -> taken[0] = TeemingSpawns.check(row, ModMobs.HUSHWING.get(), helper.getLevel(),
                EntitySpawnReason.NATURAL, above, helper.getLevel().getRandom()));
        helper.assertTrue(!taken[0], "no natural spawn at y 1, with monster spawning on");
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at.east(), Blocks.GLOWSTONE);
        Hushwing bat = helper.spawn(ModMobs.HUSHWING.get(), new Vec3(2.5, 1, 2.5));
        bat.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            float value = bat.getWalkTargetValue(helper.absolutePos(at), helper.getLevel());
            helper.assertTrue(value < 0.0F, "a lit spot rates below 0, so it only spawns in the dark, got " + value);
            helper.succeed();
        });
    }
}
