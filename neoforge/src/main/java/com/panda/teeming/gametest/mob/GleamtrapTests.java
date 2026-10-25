package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.nether.Gleamtrap;
import com.panda.teeming.mob.nether.Gleamtrap.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner.Rule;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Gleamtrap: disguised as a shroomlight it squares itself to the block grid and never moves; a pulse every 80 ticks;
 * touching it or hitting it springs it at once, standing within 1 block of it for 40 ticks in a row springs it too;
 * a 10-tick tell, then a bite of 10.5 (1.5 x 7) on whoever sprang it if they are still within 1 block. Every number is
 * checked against a plain value. Pigs get 40 health, as a bite of 10.5 would kill a plain one.
 */
public final class GleamtrapTests {
    private GleamtrapTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("gleamtrap_tell", GleamtrapTests::tell);
        out.accept("gleamtrap_counter", GleamtrapTests::counter);
        out.accept("gleamtrap_failure", GleamtrapTests::failure);
        out.accept("gleamtrap_linger", GleamtrapTests::linger);
        out.accept("gleamtrap_linger_in_a_row", GleamtrapTests::lingerInARow);
        out.accept("gleamtrap_hit_springs", GleamtrapTests::hitSprings);
        out.accept("gleamtrap_bite_reach", GleamtrapTests::biteReach);
        out.accept("gleamtrap_pulse", GleamtrapTests::pulse);
        TeemingGameTests.timeLimit("gleamtrap_pulse", 220);
        out.accept("gleamtrap_solid", GleamtrapTests::solid);
        out.accept("gleamtrap_players", GleamtrapTests::players);
        out.accept("gleamtrap_grid", GleamtrapTests::grid);
        out.accept("gleamtrap_stats", GleamtrapTests::stats);
        out.accept("gleamtrap_home", GleamtrapTests::home);
    }

    /** A Gleamtrap with the base look at ({@code x}, 1, {@code z}), its AI on: it only acts on what comes within a block. */
    private static Gleamtrap trap(GameTestHelper helper, double x, double z) {
        Gleamtrap trap = helper.spawn(ModMobs.GLEAMTRAP.get(), new Vec3(x, 1, z));
        trap.core().setLook("base", true);
        return trap;
    }

    /** A still pig with 40 health at ({@code x}, 1, {@code z}). */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        return pig;
    }

    /** Where a pig's middle stands {@code gap} blocks off a trap's east side (half widths 0.5 and 0.45), relative x. */
    private static double east(double trapX, double gap) {
        return trapX + 0.95 + gap;
    }

    /** Moves {@code pig} to stand {@code gap} blocks off {@code trap}'s east side. */
    private static void beside(Pig pig, Gleamtrap trap, double gap) {
        pig.snapTo(trap.getX() + 0.95 + gap, trap.getY(), trap.getZ());
    }

    private static float lost(Pig pig) {
        return 40.0F - pig.getHealth();
    }

    /**
     * A pig that touches it springs it on the next tick; the tell lasts exactly 10 ticks, hurts nothing and moves it
     * nowhere; then the bite takes 10.5.
     */
    private static void tell(GameTestHelper helper) {
        Gleamtrap trap = trap(helper, 3.5, 5.5);
        Pig pig = pig(helper, 9.5, 5.5);
        Vec3[] start = {null};
        int[] tick = {0};
        int[] touched = {-1};
        int[] sprang = {-1};
        int[] tell = {0};
        boolean[] bit = {false};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 3) {
                start[0] = trap.position();
                beside(pig, trap, 0.05);
                touched[0] = tick[0];
            }
            if (trap.core().in(Step.TELL)) {
                if (sprang[0] < 0) sprang[0] = tick[0];
                tell[0]++;
                helper.assertTrue(lost(pig) == 0.0F, "the tell hurts nothing yet, the pig lost " + lost(pig));
                helper.assertTrue(trap.position().distanceTo(start[0]) < 1.0E-6, "it holds still while it tells");
            } else if (tell[0] > 0 && trap.core().in(Step.BITE)) {
                bit[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bit[0], "the tell ends in the bite");
            helper.assertTrue(sprang[0] - touched[0] == 1, "a touch springs it on the next tick, took " + (sprang[0] - touched[0]));
            helper.assertTrue(tell[0] == 10, "the tell lasts 10 ticks, saw " + tell[0]);
            helper.assertTrue(Math.abs(lost(pig) - 10.5F) < 0.01F, "the bite does 1.5 x 7 = 10.5, the pig lost " + lost(pig));
        });
    }

    /**
     * The counter, both ways: a pig that touched it and steps 2 blocks away during the tell is untouched; a pig that
     * strikes it from 2.5 blocks springs it and is untouched too. A pig that stays beside it takes 10.5.
     */
    private static void counter(GameTestHelper helper) {
        Gleamtrap left = trap(helper, 2.5, 2.5);
        Gleamtrap struck = trap(helper, 8.5, 8.5);
        Gleamtrap stayed = trap(helper, 8.5, 2.5);
        Pig dodger = pig(helper, east(2.5, 0.05), 2.5);
        Pig archer = pig(helper, 8.5 - 0.95 - 2.5, 8.5);
        Pig stayer = pig(helper, east(8.5, 0.05), 2.5);
        boolean[] struckTold = {false};
        boolean[] struckBit = {false};
        helper.onEachTick(() -> {
            struckTold[0] |= struck.core().in(Step.TELL);
            struckBit[0] |= struck.core().in(Step.BITE);
        });
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(struck.hurtServer(helper.getLevel(), struck.damageSources().mobAttack(archer), 1.0F), "the blow lands");
            helper.assertTrue(struck.core().in(Step.TELL), "a blow from 2.5 blocks springs it, phase " + struck.phase());
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(left.core().in(Step.TELL), "the touch sprang it, phase " + left.phase());
            beside(dodger, left, 2.0);
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(struckTold[0] && struckBit[0], "the struck one told and bit");
            helper.assertTrue(lost(dodger) == 0.0F, "the pig that stepped away is untouched, lost " + lost(dodger));
            helper.assertTrue(lost(archer) == 0.0F, "the pig that struck from range is untouched, lost " + lost(archer));
            helper.assertTrue(Math.abs(lost(stayer) - 10.5F) < 0.01F, "the pig that stayed takes 10.5, lost " + lost(stayer));
            helper.succeed();
        });
    }

    /**
     * Failing the counter, all on its own: a pig left standing next to it (half a block off, not touching) is bitten
     * for 10.5 once it has stood there 40 ticks and the tell has run; then it settles back into its disguise, square
     * to the grid.
     */
    private static void failure(GameTestHelper helper) {
        Gleamtrap trap = trap(helper, 4.5, 5.5);
        Pig pig = pig(helper, east(4.5, 0.5), 5.5);
        boolean[] told = {false};
        boolean[] bit = {false};
        helper.onEachTick(() -> {
            told[0] |= trap.core().in(Step.TELL);
            bit[0] |= trap.core().in(Step.BITE);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(told[0] && bit[0], "it told, then bit");
            helper.assertTrue(Math.abs(lost(pig) - 10.5F) < 0.01F, "caught: 10.5, the pig lost " + lost(pig));
            helper.assertTrue(trap.disguised(), "it settles back into its disguise, phase " + trap.phase());
            helper.assertTrue(trap.getYRot() % 90.0F == 0.0F, "square to the grid again, yaw " + trap.getYRot());
        });
    }

    /**
     * Standing next to it springs it after exactly 40 ticks: half a block off and 0.9 blocks off both do; 1.1 blocks
     * off never does.
     */
    private static void linger(GameTestHelper helper) {
        double[] gaps = {0.5, 0.9, 1.1};
        Gleamtrap[] traps = {trap(helper, 2.5, 2.5), trap(helper, 2.5, 8.5), trap(helper, 7.5, 5.5)};
        Pig[] pigs = {pig(helper, 9.5, 1.5), pig(helper, 9.5, 9.5), pig(helper, 5.5, 9.5)};
        int[] tick = {0};
        int[] sprang = {-1, -1, -1};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 3) for (int i = 0; i < 3; i++) beside(pigs[i], traps[i], gaps[i]);
            for (int i = 0; i < 3; i++) if (sprang[i] < 0 && traps[i].core().in(Step.TELL)) sprang[i] = tick[0];
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(sprang[0] - 3 == 40, "half a block off for 40 ticks springs it, took " + (sprang[0] - 3));
            helper.assertTrue(sprang[1] - 3 == 40, "0.9 blocks off is still next to it: 40 ticks, took " + (sprang[1] - 3));
            helper.assertTrue(sprang[2] < 0, "1.1 blocks off is not next to it, yet it sprang at tick " + sprang[2]);
            helper.succeed();
        });
    }

    /** The 40 ticks must be in a row: a pig that steps away at 27 ticks and comes back starts again from nothing. */
    private static void lingerInARow(GameTestHelper helper) {
        Gleamtrap trap = trap(helper, 4.5, 5.5);
        Pig pig = pig(helper, 9.5, 2.5);
        int[] tick = {0};
        int[] sprang = {-1};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 3) beside(pig, trap, 0.5);
            if (tick[0] == 30) beside(pig, trap, 3.0);
            if (tick[0] == 35) beside(pig, trap, 0.5);
            if (sprang[0] < 0 && trap.core().in(Step.TELL)) sprang[0] = tick[0];
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sprang[0] > 0, "it springs in the end, tick " + tick[0]);
            helper.assertTrue(sprang[0] - 35 == 40, "40 ticks after the pig came back, took " + (sprang[0] - 35));
        });
    }

    /** A blow springs it at once at the one that struck, long before standing near would; a pig beside it is bitten. */
    private static void hitSprings(GameTestHelper helper) {
        Gleamtrap trap = trap(helper, 4.5, 5.5);
        Pig pig = pig(helper, east(4.5, 0.5), 5.5);
        helper.runAtTickTime(3, () -> {
            trap.hurtServer(helper.getLevel(), trap.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(trap.core().in(Step.TELL), "the blow springs it at once, phase " + trap.phase());
            helper.assertTrue(trap.core().target() == pig, "at the one that struck it");
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(Math.abs(lost(pig) - 10.5F) < 0.01F, "bitten for 10.5 by tick 20, lost " + lost(pig));
            helper.succeed();
        });
    }

    /** The bite reaches 1 block: a pig 0.9 blocks off its side when the lid slams is bitten, one 1.1 blocks off is not. */
    private static void biteReach(GameTestHelper helper) {
        Gleamtrap near = trap(helper, 2.5, 4.5);
        Gleamtrap far = trap(helper, 7.5, 4.5);
        Pig in = pig(helper, east(2.5, 0.9), 4.5);
        Pig out = pig(helper, east(7.5, 1.1), 4.5);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(near.spring(in) && far.spring(out), "both spring");
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(Math.abs(lost(in) - 10.5F) < 0.01F, "0.9 blocks off is in reach: lost " + lost(in) + ", want 10.5");
            helper.assertTrue(lost(out) == 0.0F, "1.1 blocks off is out of reach: lost " + lost(out) + ", want 0");
            helper.succeed();
        });
    }

    /**
     * Left alone it pulses every 80 ticks, start to start: its glow runs up to the brightest stage and back to rest,
     * and it never leaves its spot.
     */
    private static void pulse(GameTestHelper helper) {
        Gleamtrap trap = trap(helper, 5.5, 5.5);
        List<Integer> starts = new ArrayList<>();
        int[] tick = {0};
        int[] peak = {0};
        boolean[] was = {false};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 3) start[0] = trap.position();
            boolean in = trap.core().in(Step.PULSE);
            if (in && !was[0]) starts.add(tick[0]);
            if (in) peak[0] = Math.max(peak[0], trap.core().stage());
            else if (tick[0] > 3) helper.assertTrue(trap.core().stage() == 0, "between pulses it glows at rest, stage " + trap.core().stage());
            was[0] = in;
            if (start[0] != null) helper.assertTrue(trap.position().distanceTo(start[0]) < 1.0E-6, "it never moves");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(starts.size() >= 2, "two pulses, saw " + starts);
            helper.assertTrue(starts.get(1) - starts.get(0) == 80, "pulses 80 ticks apart, saw " + starts);
            helper.assertTrue(peak[0] == 2, "a pulse glows up to the brightest stage, reached " + peak[0]);
        });
    }

    /**
     * As solid as a block: a pig dropped on it comes to rest standing on its top, which counts as touching, so it is
     * bitten.
     */
    private static void solid(GameTestHelper helper) {
        Gleamtrap trap = trap(helper, 5.5, 5.5);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(5.5, 3.0, 5.5));
        pig.removeFreeWill();                                            // it falls, but never walks off
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        double[] restY = {Double.NaN};
        helper.onEachTick(() -> {
            if (Double.isNaN(restY[0]) && trap.core().in(Step.TELL)) restY[0] = pig.getY() - trap.getY();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!Double.isNaN(restY[0]), "landing on it springs it");
            helper.assertTrue(Math.abs(restY[0] - 1.0) < 0.05, "the pig stood on its top, 1 block up, at " + restY[0]);
            helper.assertTrue(Math.abs(lost(pig) - 10.5F) < 0.01F, "and was bitten for 10.5, lost " + lost(pig));
        });
    }

    /** A survival player touching it springs it; a creative player touching it for 60 ticks never does. */
    private static void players(GameTestHelper helper) {
        Gleamtrap forSurvivor = trap(helper, 3.5, 5.5);
        Gleamtrap forCreative = trap(helper, 8.5, 5.5);
        ServerPlayer[] survivor = {null};
        helper.runAtTickTime(3, () -> {
            survivor[0] = TestPlayers.survivor(helper, new Vec3(east(3.5, 0.05) - 0.15, 1, 5.5));   // a player is 0.6 wide
            ServerPlayer creative = helper.makeMockServerPlayerInLevel();
            creative.snapTo(forCreative.getX() + 0.85, forCreative.getY(), forCreative.getZ());
        });
        helper.runAtTickTime(6, () -> helper.assertTrue(forSurvivor.core().in(Step.TELL), "the survivor's touch sprang it, phase " + forSurvivor.phase()));
        helper.runAtTickTime(65, () -> {
            helper.assertTrue(forCreative.disguised(), "a creative player never springs it, phase " + forCreative.phase());
            helper.succeed();
        });
    }

    /**
     * It squares itself to the block grid on its first tick: the middle of its block and a multiple of 90 degrees.
     * After that shoves and knockback leave it exactly where it is.
     */
    private static void grid(GameTestHelper helper) {
        Gleamtrap trap = helper.spawn(ModMobs.GLEAMTRAP.get(), new Vec3(5.2, 1, 5.9));
        trap.core().setLook("base", true);
        trap.setYRot(37.0F);
        Vec3[] start = {null};
        helper.runAtTickTime(3, () -> {
            Vec3 rel = helper.relativeVec(trap.position());
            helper.assertTrue(Math.abs(rel.x - 5.5) < 1.0E-6 && Math.abs(rel.z - 5.5) < 1.0E-6, "squared to the middle of its block, at " + rel);
            helper.assertTrue(trap.getYRot() == 0.0F, "facing the grid direction nearest 37 degrees, yaw " + trap.getYRot());
            start[0] = trap.position();
            trap.knockback(2.0, -1.0, 0.0, trap.damageSources().generic(), 0.0F);
            trap.push(1.0, 0.3, 1.0);
            trap.setDeltaMovement(0.8, 0.0, -0.8);
        });
        helper.runAtTickTime(25, () -> {
            double moved = Math.hypot(trap.getX() - start[0].x, trap.getZ() - start[0].z);
            helper.assertTrue(moved < 1.0E-6, "it never left its spot, moved " + moved);
            helper.assertTrue(trap.disguised(), "and is still disguised, phase " + trap.phase());
            helper.succeed();
        });
    }

    /** Health 24, attack 7, armour 4 (a blow of 4 costs 3.68), a 1.0 x 1.0 hitbox: one block. */
    private static void stats(GameTestHelper helper) {
        Gleamtrap trap = trap(helper, 5.5, 5.5);
        Pig striker = pig(helper, 9.5, 9.5);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(trap.getMaxHealth() == 24.0F && trap.getHealth() == 24.0F, "health 24, got " + trap.getHealth() + "/" + trap.getMaxHealth());
            helper.assertTrue(trap.getAttributeValue(Attributes.ATTACK_DAMAGE) == 7.0, "attack 7, got " + trap.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(trap.getArmorValue() == 4, "armour 4, got " + trap.getArmorValue());
            helper.assertTrue(trap.getBbWidth() == 1.0F && trap.getBbHeight() == 1.0F, "a 1.0 x 1.0 hitbox, got " + trap.getBbWidth() + " x " + trap.getBbHeight());
            trap.hurtServer(helper.getLevel(), trap.damageSources().mobAttack(striker), 4.0F);
            float cost = 24.0F - trap.getHealth();
            helper.assertTrue(Math.abs(cost - 3.68F) < 0.01F, "armour 4 turns a blow of 4 into 3.68, cost " + cost);
            helper.succeed();
        });
    }

    /**
     * Home: no biome list; the Teeming spawner's fungus-cap rule places it, in the Nether, on a nether or warped wart
     * block with air above, never on a plain floor or under a block. Weight 8, alone.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("gleamtrap");
        helper.assertTrue(row.spawner().equals("fungus_cap") && Rule.of(row.spawner()) == Rule.FUNGUS_CAP, "the fungus-cap rule, got " + row.spawner());
        helper.assertTrue(Rule.FUNGUS_CAP.dimension.equals(net.minecraft.world.level.Level.NETHER), "a Nether rule");
        helper.assertTrue(!row.biomeSpawns(), "no biome spawns");
        helper.assertTrue(row.weight() == 8, "spawn weight 8, got " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "alone, got groups of " + row.groupMin() + ".." + row.groupMax());
        var access = helper.getLevel().registryAccess();
        int biomes = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("gleamtrap")).map(set -> set.size()).orElse(0);
        helper.assertTrue(biomes == 0, "its biome tag is empty, holds " + biomes);
        helper.assertTrue(access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/gleamtrap"))).isEmpty(), "no biome modifier");
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.NETHER_WART_BLOCK);
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.WARPED_WART_BLOCK);
        helper.setBlock(new BlockPos(8, 1, 2), Blocks.NETHER_WART_BLOCK);
        helper.setBlock(new BlockPos(8, 3, 2), Blocks.STONE);
        helper.assertTrue(fits(helper, new BlockPos(2, 2, 2)), "accepts the top of a nether wart block");
        helper.assertTrue(fits(helper, new BlockPos(5, 2, 2)), "accepts the top of a warped wart block");
        helper.assertTrue(!fits(helper, new BlockPos(8, 2, 2)), "refuses a wart block with no room above");
        helper.assertTrue(!fits(helper, new BlockPos(5, 1, 7)), "refuses the plain floor");
        helper.succeed();
    }

    private static boolean fits(GameTestHelper helper, BlockPos rel) {
        return Rule.FUNGUS_CAP.fits(helper.getLevel(), helper.absolutePos(rel));
    }
}
