package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.VigilOwl;
import com.panda.teeming.mob.end.VigilOwl.Step;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Vigil Owl: it stares at a survival player it sees within 16 blocks (line of sight needed). The stare lasts exactly 40
 * ticks and does nothing until it is full; breaking the line of sight drops it, and the next stare starts from nothing.
 * A full stare marks: Glowing for exactly 200 ticks, and every Teeming End mob (not a passive one) within 32 blocks of
 * the marked one takes them as its target; vanilla mobs, Teeming mobs of other dimensions, one 33 blocks off and other
 * players don't. A hit drops the stare and sends it fluttering off; so does closing within 4 blocks. A hit from the one
 * it marked ends the mark at once and turns the mobs it called on the owl for 100 ticks. Cornered, it pecks after an 8
 * tick wind-up for its attack of 4 (armour counts), and never at a target 2 blocks above it. Killed in the stare, it
 * marks no one.
 *
 * <p>Every owl here watches only its own test's quarry ({@link VigilOwl#watchOnly}), and every step it takes that pairs
 * one of this test's entities with a stranger (a call to another test's mob, most of all) is vetoed. Stand-ins that
 * answer the call are Glimmerless with their AI off, so nothing but the owl's call ever sets their target.
 */
public final class VigilOwlTests {
    private VigilOwlTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("vigil_owl_tell", VigilOwlTests::tell);
        out.accept("vigil_owl_sight_break", VigilOwlTests::sightBreak);
        TeemingGameTests.timeLimit("vigil_owl_sight_break", 160);
        out.accept("vigil_owl_failure", VigilOwlTests::failure);
        out.accept("vigil_owl_call", VigilOwlTests::call);
        out.accept("vigil_owl_counter", VigilOwlTests::counter);
        TeemingGameTests.timeLimit("vigil_owl_counter", 160);
        out.accept("vigil_owl_turned", VigilOwlTests::turned);
        TeemingGameTests.timeLimit("vigil_owl_turned", 220);
        out.accept("vigil_owl_keep_away", VigilOwlTests::keepAway);
        out.accept("vigil_owl_too_close", VigilOwlTests::tooClose);
        out.accept("vigil_owl_sight_range", VigilOwlTests::sightRange);
        out.accept("vigil_owl_peck", VigilOwlTests::peck);
        out.accept("vigil_owl_peck_armour", VigilOwlTests::peckArmour);
        out.accept("vigil_owl_pillar", VigilOwlTests::pillar);
        out.accept("vigil_owl_dies_mid_tell", VigilOwlTests::diesMidTell);
        out.accept("vigil_owl_spawn_rule", VigilOwlTests::spawnRule);
        out.accept("vigil_owl_stats", VigilOwlTests::stats);
        out.accept("vigil_owl_home", VigilOwlTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** An owl at {@code at} (relative) with its strolls off, removed when the test ends wherever it fluttered. */
    private static VigilOwl owl(GameTestHelper helper, Vec3 at) {
        VigilOwl o = helper.spawn(ModMobs.VIGIL_OWL.get(), at);
        o.core().setLook(LookDef.BASE, true);
        o.removeFreeWill();
        TestPlayers.whenDone(helper, o::discard);
        return o;
    }

    /** A survival player at {@code at} (relative) whom only {@code owl} watches. */
    private static ServerPlayer quarry(GameTestHelper helper, VigilOwl owl, Vec3 at) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        owl.watchOnly(e -> e == p);
        return p;
    }

    /** A Glimmerless with its AI off at {@code at} (relative): a Teeming End mob that only the owl's call can set on anyone. */
    private static Mob answerer(GameTestHelper helper, Vec3 at) {
        return still(helper, ModMobs.GLIMMERLESS.get(), at);
    }

    /** A mob of {@code type} with its AI off, held in the air where it is put, removed when the test ends. */
    private static <T extends Mob> T still(GameTestHelper helper, EntityType<T> type, Vec3 at) {
        T m = helper.spawn(type, at);
        m.setNoAi(true);
        m.setNoGravity(true);
        TestPlayers.whenDone(helper, m::discard);
        return m;
    }

    /** A still pig with 100 health at {@code at} (relative). */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = still(helper, EntityTypes.PIG, at);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        return pig;
    }

    /**
     * Keeps this test's owls and the entities they deal with to each other: any owl step (a stare, a mark, a call, a
     * turn) that pairs one of {@code mine} with an entity that isn't, or a stranger with one of {@code mine}, is vetoed.
     */
    private static void contain(GameTestHelper helper, Entity... mine) {
        Set<Entity> set = new HashSet<>(List.of(mine));
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() instanceof VigilOwl && e.target() != null && set.contains(e.mob()) != set.contains(e.target())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
    }

    private static boolean glowing(Entity e) {
        return e instanceof net.minecraft.world.entity.LivingEntity l && l.hasEffect(MobEffects.GLOWING);
    }

    private static final Vec3 OWL_AT = new Vec3(2.5, 1, 5.5);
    private static final Vec3 QUARRY_AT = new Vec3(8.5, 1, 5.5);

    // ---- the tell ------------------------------------------------------------------------------------------------

    /**
     * The stare holds for exactly 40 ticks with its quarry in sight, its eyes lit from stage 1 and at stage 3 by the
     * end, and in that time nothing happens: no Glowing, and the Glimmerless nearby has no target. Then it marks.
     */
    private static void tell(GameTestHelper helper) {
        VigilOwl o = owl(helper, OWL_AT);
        ServerPlayer p = quarry(helper, o, QUARRY_AT);
        Mob g = answerer(helper, new Vec3(8.5, 1, 2.5));
        contain(helper, o, p, g);
        long[] from = {-1}, to = {-1};
        boolean[] quiet = {true}, lit = {true};
        int[] lastStage = {0};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (from[0] < 0 && o.core().in(Step.TELL)) from[0] = now;
            if (from[0] < 0 || to[0] >= 0) return;
            if (o.marks() > 0) {
                to[0] = now;
                return;
            }
            if (!o.core().in(Step.TELL)) quiet[0] = false;
            if (glowing(p) || g.getTarget() != null) quiet[0] = false;
            if (o.core().stage() < 1) lit[0] = false;
            lastStage[0] = o.core().stage();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(to[0] >= 0, "the stare marked its quarry in the end");
            helper.assertTrue(to[0] - from[0] == 40, "the stare lasted 40 ticks, took " + (to[0] - from[0]));
            helper.assertTrue(quiet[0], "nothing happened during the stare: no Glowing, no target given");
            helper.assertTrue(lit[0] && lastStage[0] == 3, "its eyes were lit through the stare, stage 3 at the end, last " + lastStage[0]);
            helper.assertTrue(o.core().in(Step.MARK), "then it calls, has " + o.core().phase().step());
        });
    }

    /**
     * Breaking the line of sight resets the stare: a stone column put between them 20 ticks in ends it on the next tick
     * with no mark; taken away 10 ticks later, a new stare starts and still takes the full 40 ticks.
     */
    private static void sightBreak(GameTestHelper helper) {
        VigilOwl o = owl(helper, OWL_AT);
        ServerPlayer p = quarry(helper, o, QUARRY_AT);
        contain(helper, o, p);
        BlockPos[] wall = {new BlockPos(5, 1, 5), new BlockPos(5, 2, 5), new BlockPos(5, 3, 5)};
        long[] first = {-1}, walled = {-1}, lost = {-1}, cleared = {-1}, again = {-1}, marked = {-1};
        boolean[] markedEarly = {false};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (first[0] < 0) {
                if (o.core().in(Step.TELL)) first[0] = now;
                return;
            }
            if (walled[0] < 0) {
                if (now == first[0] + 20) {
                    for (BlockPos b : wall) helper.setBlock(b, Blocks.STONE);
                    walled[0] = now;
                }
                if (o.marks() > 0) markedEarly[0] = true;
                return;
            }
            if (lost[0] < 0) {
                if (!o.core().in(Step.TELL)) lost[0] = now;
                return;
            }
            if (cleared[0] < 0) {
                if (o.marks() > 0 || o.core().in(Step.TELL)) markedEarly[0] = true;
                if (now == lost[0] + 10) {
                    for (BlockPos b : wall) helper.setBlock(b, Blocks.AIR);
                    cleared[0] = now;
                }
                return;
            }
            if (again[0] < 0) {
                if (o.core().in(Step.TELL)) again[0] = now;
                return;
            }
            if (marked[0] < 0 && o.marks() > 0) marked[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(marked[0] >= 0, "it marked in the end");
            helper.assertTrue(!markedEarly[0], "no mark, and no stare, while the wall stood");
            helper.assertTrue(lost[0] - walled[0] == 1, "the stare broke the tick after the wall went up, took " + (lost[0] - walled[0]));
            helper.assertTrue(again[0] - cleared[0] <= 1, "a new stare started once it saw again, took " + (again[0] - cleared[0]));
            helper.assertTrue(marked[0] - again[0] == 40, "the new stare took the full 40 ticks, took " + (marked[0] - again[0]));
        });
    }

    // ---- the failure ---------------------------------------------------------------------------------------------

    /**
     * Failing the counter: the full stare marks its quarry with Glowing for exactly 200 ticks (no amplifier), and the
     * Teeming End mob near by takes them as its target. A second player in plain sight is not marked.
     */
    private static void failure(GameTestHelper helper) {
        VigilOwl o = owl(helper, OWL_AT);
        ServerPlayer p = quarry(helper, o, QUARRY_AT);
        ServerPlayer other = TestPlayers.survivor(helper, new Vec3(8.5, 1, 8.5));
        Mob g = answerer(helper, new Vec3(8.5, 1, 2.5));
        contain(helper, o, p, other, g);
        int[] left = {-1}, amp = {-1};
        boolean[] targeted = {false}, otherClean = {true};
        long[] at = {-1};
        helper.onEachTick(() -> {
            if (at[0] >= 0) return;
            if (o.marks() > 0) {
                at[0] = helper.getLevel().getGameTime();
                MobEffectInstance glow = p.getEffect(MobEffects.GLOWING);
                left[0] = glow == null ? -1 : glow.getDuration();
                amp[0] = glow == null ? -1 : glow.getAmplifier();
                targeted[0] = g.getTarget() == p;
                otherClean[0] = !glowing(other);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at[0] >= 0, "it marked its quarry");
            helper.assertTrue(left[0] == 200, "Glowing for 200 ticks, got " + left[0]);
            helper.assertTrue(amp[0] == 0, "plain Glowing, amplifier 0, got " + amp[0]);
            helper.assertTrue(targeted[0], "the Teeming End mob near by took the marked player as its target");
            helper.assertTrue(otherClean[0], "the other player is not marked");
            helper.assertTrue(o.called().equals(List.of(g)), "it called that one mob, called " + o.called());
        });
    }

    /**
     * Who answers the call: a Teeming End mob 3 blocks off and one 31.5 blocks straight up do; a zombie, a Teeming
     * Overworld monster (a Gloamhound), a passive Teeming End mob (a Chorus Grazer) and a Teeming End mob 33 blocks up
     * don't.
     */
    private static void call(GameTestHelper helper) {
        VigilOwl o = owl(helper, OWL_AT);
        ServerPlayer p = quarry(helper, o, QUARRY_AT);
        Mob near = answerer(helper, new Vec3(8.5, 1, 2.5));
        Mob high = answerer(helper, new Vec3(8.5, 1 + 31.5, 5.5));
        Mob far = answerer(helper, new Vec3(8.5, 1 + 33.0, 5.5));
        Mob zombie = still(helper, EntityTypes.ZOMBIE, new Vec3(8.5, 1, 8.5));
        Mob hound = still(helper, ModMobs.GLOAMHOUND.get(), new Vec3(5.5, 1, 8.5));
        Mob grazer = still(helper, ModMobs.CHORUS_GRAZER.get(), new Vec3(5.5, 1, 2.5));
        contain(helper, o, p, near, high, far, zombie, hound, grazer);
        double[] dHigh = {-1}, dFar = {-1};
        boolean[] seen = {false};
        Entity[] targets = new Entity[6];
        helper.onEachTick(() -> {
            if (seen[0] || o.marks() == 0) return;
            seen[0] = true;
            dHigh[0] = high.distanceTo(p);
            dFar[0] = far.distanceTo(p);
            Mob[] all = {near, high, far, zombie, hound, grazer};
            for (int i = 0; i < all.length; i++) targets[i] = all[i].getTarget();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen[0], "it marked its quarry");
            helper.assertTrue(Math.abs(dHigh[0] - 31.5) < 0.01 && Math.abs(dFar[0] - 33.0) < 0.01, "31.5 and 33 blocks off, got " + dHigh[0] + ", " + dFar[0]);
            helper.assertTrue(targets[0] == p, "the Teeming End mob 3 blocks off answered");
            helper.assertTrue(targets[1] == p, "the one 31.5 blocks off answered");
            helper.assertTrue(targets[2] == null, "the one 33 blocks off did not, has " + targets[2]);
            helper.assertTrue(targets[3] == null, "the zombie did not, has " + targets[3]);
            helper.assertTrue(targets[4] == null, "the Teeming Overworld monster did not, has " + targets[4]);
            helper.assertTrue(targets[5] == null, "the passive Chorus Grazer did not, has " + targets[5]);
            helper.assertTrue(o.called().size() == 2, "it called two, called " + o.called().size());
        });
    }

    // ---- the counter ---------------------------------------------------------------------------------------------

    /**
     * The counter: one hit 20 ticks into the stare drops it at once (eyes back to rest) and sends it fluttering off; 60
     * ticks later no one is marked, and the owl stands farther from its quarry than when it was hit.
     */
    private static void counter(GameTestHelper helper) {
        VigilOwl o = owl(helper, new Vec3(4.5, 1, 4.5));
        ServerPlayer p = quarry(helper, o, new Vec3(8.5, 1, 9.5));
        contain(helper, o, p);
        long[] first = {-1}, hit = {-1};
        boolean[] fled = {false}, dark = {false};
        double[] gapAtHit = {-1};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (first[0] < 0 && o.core().in(Step.TELL)) first[0] = now;
            if (first[0] >= 0 && hit[0] < 0 && now == first[0] + 20) {
                gapAtHit[0] = o.distanceTo(p);
                o.hurtServer(helper.getLevel(), o.damageSources().playerAttack(p), 1.0F);
                hit[0] = now;
                fled[0] = o.core().in(Step.FLEE);
                dark[0] = o.core().stage() == 0;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hit[0] >= 0 && helper.getLevel().getGameTime() >= hit[0] + 60, "hit mid-stare, then 60 ticks");
            helper.assertTrue(fled[0], "the hit sent it fluttering off at once");
            helper.assertTrue(dark[0], "its eyes went back to rest");
            helper.assertTrue(o.marks() == 0 && !glowing(p), "no mark after the hit");
            helper.assertTrue(o.distanceTo(p) > gapAtHit[0], "it stands farther off than when it was hit: " + o.distanceTo(p) + " > " + gapAtHit[0]);
        });
    }

    /**
     * The payoff: hit by the player it marked while the mark is up, the Glowing ends at once and the mob it called turns
     * on the owl. A turned mob set back on the player is set on the owl again for 100 ticks; after that it is let be.
     */
    private static void turned(GameTestHelper helper) {
        VigilOwl o = owl(helper, OWL_AT);
        ServerPlayer p = quarry(helper, o, QUARRY_AT);
        Mob g = answerer(helper, new Vec3(8.5, 1, 2.5));
        contain(helper, o, p, g);
        long[] marked = {-1}, hit = {-1};
        boolean[] onPlayer = {false}, glowGone = {false}, onOwl = {false}, back = {false}, letBe = {false};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (marked[0] < 0) {
                if (o.marks() > 0) {
                    marked[0] = now;
                    onPlayer[0] = g.getTarget() == p && glowing(p);
                }
                return;
            }
            if (now == marked[0] + 5) {
                o.hurtServer(helper.getLevel(), o.damageSources().playerAttack(p), 1.0F);
                hit[0] = now;
                glowGone[0] = !glowing(p);
                onOwl[0] = g.getTarget() == o && o.turned().contains(g);
            }
            if (hit[0] < 0) return;
            if (now == hit[0] + 5) g.setTarget(p);
            if (now == hit[0] + 7) back[0] = g.getTarget() == o;
            if (now == hit[0] + 102) g.setTarget(p);
            if (now == hit[0] + 105) letBe[0] = g.getTarget() == p && o.turned().isEmpty();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hit[0] >= 0 && helper.getLevel().getGameTime() >= hit[0] + 106, "hit while marked, then 106 ticks");
            helper.assertTrue(onPlayer[0], "marked: glowing, and the Glimmerless on the player");
            helper.assertTrue(glowGone[0], "the hit ended the Glowing at once");
            helper.assertTrue(onOwl[0], "the hit turned the Glimmerless on the owl");
            helper.assertTrue(back[0], "set back on the player, it was turned on the owl again");
            helper.assertTrue(letBe[0], "after 100 ticks it is let be");
        });
    }

    /**
     * It keeps its distance: 4.3 blocks off it stares and stays put; moved to 3.7 blocks, it flutters off on the next
     * tick and drops the stare.
     */
    private static void keepAway(GameTestHelper helper) {
        VigilOwl o = owl(helper, new Vec3(4.5, 1, 5.5));
        ServerPlayer p = quarry(helper, o, new Vec3(8.8, 1, 5.5));
        contain(helper, o, p);
        long[] first = {-1}, moved = {-1}, fled = {-1};
        boolean[] stayed = {true};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (first[0] < 0) {
                if (o.core().in(Step.TELL)) first[0] = now;
                return;
            }
            if (moved[0] < 0) {
                if (!o.core().in(Step.TELL)) stayed[0] = false;
                if (now == first[0] + 5) {
                    p.snapTo(helper.absoluteVec(new Vec3(8.2, 1, 5.5)));
                    moved[0] = now;
                }
                return;
            }
            if (fled[0] < 0 && o.core().in(Step.FLEE)) fled[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stayed[0], "4.3 blocks off it kept staring");
            helper.assertTrue(fled[0] >= 0 && fled[0] - moved[0] == 1, "3.7 blocks off it fluttered off the next tick, took " + (fled[0] - moved[0]));
            helper.assertTrue(o.marks() == 0, "no mark");
        });
    }

    /**
     * A player it first sees 3 blocks off is never stared at: it flutters off at once, and the flutter carries it away
     * from them (farther off when the flutter ends).
     */
    private static void tooClose(GameTestHelper helper) {
        VigilOwl o = owl(helper, new Vec3(5.5, 1, 5.5));
        ServerPlayer p = quarry(helper, o, new Vec3(8.5, 1, 5.5));
        contain(helper, o, p);
        String[] first = {null};
        long[] fled = {-1};
        double[] gap = {-1};
        helper.onEachTick(() -> {
            if (first[0] == null && !o.core().idle()) {
                first[0] = o.core().phase().step();
                fled[0] = helper.getLevel().getGameTime();
            }
            if (fled[0] >= 0 && gap[0] < 0 && helper.getLevel().getGameTime() == fled[0] + 30) gap[0] = o.distanceTo(p);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gap[0] >= 0, "30 ticks after it moved");
            helper.assertTrue("flee".equals(first[0]), "its first move was to flutter off, was " + first[0]);
            helper.assertTrue(gap[0] > 3.5, "it ended farther off than the 3 it started at, " + gap[0]);
            helper.assertTrue(o.marks() == 0, "no mark");
        });
    }

    /**
     * Its sight: a player 15.95 blocks off with a clear line is stared at; one 16.06 blocks off is not (in 40 ticks).
     */
    private static void sightRange(GameTestHelper helper) {
        VigilOwl near = owl(helper, new Vec3(0.5, 1, 0.5));
        ServerPlayer inSight = quarry(helper, near, new Vec3(11.5, 1 + 3.5, 11.5));
        VigilOwl far = owl(helper, new Vec3(11.5, 1, 0.5));
        ServerPlayer outOfSight = quarry(helper, far, new Vec3(0.5, 1 + 4.0, 11.5));
        contain(helper, near, inSight, far, outOfSight);
        boolean[] stared = {false}, farStared = {false};
        double[] d = {near.distanceTo(inSight), far.distanceTo(outOfSight)};
        helper.onEachTick(() -> {
            if (near.core().in(Step.TELL)) stared[0] = true;
            if (!far.core().idle() || far.getTarget() != null) farStared[0] = true;
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(Math.abs(d[0] - 15.95) < 0.01 && Math.abs(d[1] - 16.06) < 0.01, "15.95 and 16.06 blocks, got " + d[0] + ", " + d[1]);
            helper.assertTrue(stared[0], "the player 15.95 blocks off was stared at");
            helper.assertTrue(!farStared[0], "the player 16.06 blocks off was not seen");
            helper.succeed();
        });
    }

    // ---- the peck ------------------------------------------------------------------------------------------------

    /**
     * Cornered (holding its ground, a pig beside it), it winds up for exactly 8 ticks, hurting nothing, then pecks for
     * its attack of 4 (a pig with no armour loses exactly 4).
     */
    private static void peck(GameTestHelper helper) {
        VigilOwl o = owl(helper, new Vec3(5.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(6.5, 1, 5.5));
        contain(helper, o, pig);
        o.watchOnly(e -> e == pig);
        o.holdGround(200);
        o.setTarget(pig);
        long[] tell = {-1}, hit = {-1};
        float[] lost = {0};
        boolean[] quiet = {true};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (tell[0] < 0 && o.core().in(Step.PECK_TELL)) tell[0] = now;
            if (tell[0] < 0 || hit[0] >= 0) return;
            if (pig.getHealth() < 100.0F) {
                hit[0] = now;
                lost[0] = 100.0F - pig.getHealth();
            } else if (!o.core().in(Step.PECK_TELL) && now > tell[0]) {
                quiet[0] = false;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hit[0] >= 0, "it pecked");
            helper.assertTrue(quiet[0], "it held the wind-up, hurting nothing");
            helper.assertTrue(hit[0] - tell[0] == 8, "the wind-up lasted 8 ticks, took " + (hit[0] - tell[0]));
            helper.assertTrue(Math.abs(lost[0] - 4.0F) < 0.01F, "the peck dealt 4, got " + lost[0]);
            helper.assertTrue(o.pecks() == 1, "one peck, saw " + o.pecks());
        });
    }

    /** Armour counts: a pig with 10 armour loses 4 x (1 - 8/25) = 2.72 to a peck. */
    private static void peckArmour(GameTestHelper helper) {
        VigilOwl o = owl(helper, new Vec3(5.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(6.5, 1, 5.5));
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        contain(helper, o, pig);
        o.watchOnly(e -> e == pig);
        o.holdGround(200);
        o.setTarget(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(o.pecks() == 1, "it pecked");
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 2.72F) < 0.01F, "armour 10 takes the peck to 2.72, got " + lost);
        });
    }

    /** Reach counts height: a pig hanging 2.2 blocks above the top of its head, right over it, is never pecked. */
    private static void pillar(GameTestHelper helper) {
        VigilOwl o = owl(helper, new Vec3(5.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.5, 4.0, 6.0));
        contain(helper, o, pig);
        o.watchOnly(e -> e == pig);
        o.holdGround(200);
        o.setTarget(pig);
        boolean[] wound = {false};
        helper.onEachTick(() -> {
            if (o.core().in(Step.PECK_TELL) || o.core().in(Step.PECK)) wound[0] = true;
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!wound[0] && o.pecks() == 0, "it never wound up a peck");
            helper.assertTrue(pig.getHealth() >= 100.0F, "the pig overhead is unhurt, has " + pig.getHealth());
            helper.succeed();
        });
    }

    /** Killed one tick before the stare is full, it marks no one: no Glowing, no target given. */
    private static void diesMidTell(GameTestHelper helper) {
        VigilOwl o = owl(helper, OWL_AT);
        ServerPlayer p = quarry(helper, o, QUARRY_AT);
        Mob g = answerer(helper, new Vec3(8.5, 1, 2.5));
        contain(helper, o, p, g);
        long[] first = {-1}, killed = {-1};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (first[0] < 0 && o.core().in(Step.TELL)) first[0] = now;
            if (first[0] >= 0 && killed[0] < 0 && now == first[0] + 39) {
                o.kill(helper.getLevel());
                killed[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getLevel().getGameTime() >= killed[0] + 20, "killed mid-stare, then 20 ticks");
            helper.assertTrue(o.marks() == 0, "no mark from a dead owl");
            helper.assertTrue(!glowing(p), "the player isn't glowing");
            helper.assertTrue(g.getTarget() == null, "no mob was called");
        });
    }

    // ---- the row -------------------------------------------------------------------------------------------------

    /** Its spawn rule (on the ground): end stone under it is a spot; air under it is not. */
    private static void spawnRule(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("vigil_owl");
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "it is placed on the ground, got " + row.place());
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.END_STONE);
        helper.succeedWhen(() -> {
            helper.assertTrue(TeemingSpawns.placeOk(row, ModMobs.VIGIL_OWL.get(), helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3))),
                    "a spot on end stone is accepted");
            helper.assertTrue(!TeemingSpawns.placeOk(row, ModMobs.VIGIL_OWL.get(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))),
                    "a spot over open air is refused");
        });
    }

    /** Health 18, attack 4, armour 0, a 0.6 x 0.8 hitbox; it sees 16 blocks (its follow range). */
    private static void stats(GameTestHelper helper) {
        VigilOwl o = owl(helper, new Vec3(5.5, 1, 5.5));
        o.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(o.getMaxHealth() - 18.0F) < 0.01F && Math.abs(o.getHealth() - 18.0F) < 0.01F,
                    "health 18, got " + o.getHealth() + "/" + o.getMaxHealth());
            helper.assertTrue(Math.abs(o.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4");
            helper.assertTrue(Math.abs(o.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(o.getBbWidth() - 0.6F) < 0.02F && Math.abs(o.getBbHeight() - 0.8F) < 0.02F,
                    "hitbox 0.6 x 0.8, got " + o.getBbWidth() + " x " + o.getBbHeight());
            helper.assertTrue(Math.abs(o.getAttributeValue(Attributes.FOLLOW_RANGE) - 16.0) < 0.01, "follow range 16");
            helper.succeed();
        });
    }

    /**
     * Home: the End midlands and highlands (not the barrens, the small islands or the main island), weight 4, alone; a
     * monster of the End. The Pale Vigil is its second look; it drops void ichor.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("vigil_owl");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.VIGIL_OWL.get().getCategory() == MobCategory.MONSTER, "a monster");
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("vigil_owl"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "vigil_owl").stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)) && TeemingGameTests.spawnBiomes(helper, "vigil_owl").stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)),
                "its home holds the midlands and the highlands");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "vigil_owl").stream().noneMatch(b -> b.is(Biomes.END_BARRENS) || b.is(Biomes.SMALL_END_ISLANDS) || b.is(Biomes.THE_END)),
                "never the barrens, the small islands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/vigil_owl")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.VIGIL_OWL.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("vigil_owl");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("pale_vigil")), "the Pale Vigil is a look: " + looks);
        helper.assertTrue(row.material().equals("void_ichor") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_ichor")),
                "it drops void ichor, a registered item");
        helper.succeed();
    }
}
