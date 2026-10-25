package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.overworld.Gloamhound;
import com.panda.teeming.mob.overworld.Gloamhound.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Gloamhound: a 14-tick crouch before each dart; a pack takes turns, one hound holding the turn at a time, bites of 4
 * (the alpha's 6) at least 10 ticks apart after each dart; the alpha (30 health, 6 attack) howls every 200 ticks for
 * Speed I (6 s, 16 blocks); when it dies the rest flee for 200 ticks. Every number is checked against a plain value.
 */
public final class GloamhoundTests {
    private GloamhoundTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("gloamhound_tell", GloamhoundTests::tell);
        out.accept("gloamhound_counter", GloamhoundTests::counter);
        out.accept("gloamhound_failure", GloamhoundTests::failure);
        TeemingGameTests.timeLimit("gloamhound_failure", 240);
        out.accept("gloamhound_bite", GloamhoundTests::bite);
        out.accept("gloamhound_dart", GloamhoundTests::dart);
        out.accept("gloamhound_stats", GloamhoundTests::stats);
        out.accept("gloamhound_alpha", GloamhoundTests::alphaStats);
        out.accept("gloamhound_howl", GloamhoundTests::howl);
        TeemingGameTests.timeLimit("gloamhound_howl", 280);
        out.accept("gloamhound_flee_length", GloamhoundTests::fleeLength);
        TeemingGameTests.timeLimit("gloamhound_flee_length", 260);
        out.accept("gloamhound_pack_spawn", GloamhoundTests::packSpawn);
        out.accept("gloamhound_settle", GloamhoundTests::settle);
        out.accept("gloamhound_save_load", GloamhoundTests::saveLoad);
        out.accept("gloamhound_home", GloamhoundTests::home);
    }

    /**
     * A plain hound with the base look, a pack of its own, that only notices what the test shows it (a short follow range keeps survivors in
     * neighbouring arenas out of reach), removed when the test ends wherever it ran to.
     */
    private static Gloamhound hound(GameTestHelper helper, double x, double z) {
        Gloamhound hound = helper.spawn(ModMobs.GLOAMHOUND.get(), new Vec3(x, 1, z));
        hound.core().setLook("base", true);
        hound.joinPack(UUID.randomUUID(), false);        // a plain hound of its own; formPack makes packs
        hound.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(10.0);
        TestPlayers.whenDone(helper, hound::discard);
        return hound;
    }

    /** A still pig with 100 health, so a run of bites never kills it. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        return pig;
    }

    /** The crouch lasts exactly 14 ticks, holds still and hurts nothing; then the dart bites for 4. */
    private static void tell(GameTestHelper helper) {
        Gloamhound hound = hound(helper, 2.5, 5.5);
        Pig pig = pig(helper, 6.5, 5.5);
        hound.setTarget(pig);
        int[] crouch = {0};
        Vec3[] start = {null};
        boolean[] darted = {false};
        helper.onEachTick(() -> {
            if (hound.core().in(Step.TELL)) {
                if (start[0] == null) start[0] = hound.position();
                crouch[0]++;
                helper.assertTrue(pig.getHealth() >= 100.0F, "the crouch hurts nothing yet");
                double moved = Math.hypot(hound.getX() - start[0].x, hound.getZ() - start[0].z);
                helper.assertTrue(moved < 0.01, "it holds still while it crouches, moved " + moved);
            } else if (crouch[0] > 0 && !darted[0]) {
                helper.assertTrue(hound.core().in(Step.DART) || hound.core().in(Step.RETREAT), "the crouch ends in the dart, phase " + hound.phase());
                darted[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(darted[0], "it crouched and darted");
            helper.assertTrue(crouch[0] == 14, "the crouch lasts 14 ticks, saw " + crouch[0]);
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F) < 0.01F, "then the bite takes 4, the pig lost " + lost);
        });
    }

    /**
     * The counter: the alpha dies and both its packmates flee at once, drop their target and run from where it fell;
     * a hound of another pack keeps hunting.
     */
    private static void counter(GameTestHelper helper) {
        // the alpha in the middle of the 12-block arena and its packmates either side, so both have room to run from
        // where it falls (one by the edge was fleeing into the wall)
        Pig pig = pig(helper, 6.5, 2.5);
        Gloamhound alpha = hound(helper, 6.5, 6.5);
        Gloamhound a = hound(helper, 4.5, 6.5);
        Gloamhound b = hound(helper, 8.5, 6.5);
        Gloamhound stranger = hound(helper, 6.5, 10.5);
        Gloamhound.formPack(alpha, a, b);
        for (Gloamhound h : List.of(alpha, a, b, stranger)) h.setTarget(pig);
        Vec3[] fell = {null};
        double[] startA = {0}, startB = {0};
        helper.runAtTickTime(5, () -> {
            fell[0] = alpha.position();
            alpha.kill(helper.getLevel());
            helper.assertTrue(!alpha.isAlive(), "the alpha is dead");
            helper.assertTrue(a.core().in(Step.FLEE) && b.core().in(Step.FLEE), "its pack flees at once, phases " + a.phase() + ", " + b.phase());
            helper.assertTrue(a.getTarget() == null && b.getTarget() == null, "and drops its target");
            helper.assertTrue(!stranger.core().in(Step.FLEE) && stranger.getTarget() == pig, "a hound of another pack keeps hunting, phase " + stranger.phase());
            startA[0] = a.position().distanceTo(fell[0]);
            startB[0] = b.position().distanceTo(fell[0]);
        });
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(a.core().in(Step.FLEE) && b.core().in(Step.FLEE), "40 ticks on they are still fleeing");
            helper.assertTrue(a.getTarget() == null && b.getTarget() == null, "with no target");
            double gainA = a.position().distanceTo(fell[0]) - startA[0];
            double gainB = b.position().distanceTo(fell[0]) - startB[0];
            helper.assertTrue(gainA > 1.0 && gainB > 1.0, "they ran from where the alpha fell, by " + gainA + " and " + gainB);
            helper.succeed();
        });
    }

    /**
     * Failing the counter: a pack of three (alpha and two) wears a still pig down with a steady stream of bites. Never
     * more than one hound holds the turn; the first three turns go to three different hounds; each bite is 4 (the
     * alpha's 6); bites land at least 24 ticks apart (the 10-tick gap after a dart plus the 14-tick crouch).
     */
    private static void failure(GameTestHelper helper) {
        Pig pig = pig(helper, 6.5, 6.5);
        Gloamhound alpha = hound(helper, 2.5, 6.5);
        Gloamhound a = hound(helper, 10.5, 6.5);
        Gloamhound b = hound(helper, 6.5, 2.5);
        Gloamhound.formPack(alpha, a, b);
        List<Gloamhound> pack = List.of(alpha, a, b);
        for (Gloamhound h : pack) h.setTarget(pig);
        List<Gloamhound> turns = new ArrayList<>();
        boolean[] crouching = new boolean[3];
        List<Integer> gaps = new ArrayList<>();
        int[] tick = {0};
        int[] lastBite = {-1};
        float[] last = {100.0F};
        helper.onEachTick(() -> {
            tick[0]++;
            int holders = 0;
            for (int i = 0; i < 3; i++) {
                Gloamhound h = pack.get(i);
                if (h.holdsTurn()) holders++;
                boolean now = h.core().in(Step.TELL);
                if (now && !crouching[i]) turns.add(h);
                crouching[i] = now;
            }
            helper.assertTrue(holders <= 1, "one hound holds the turn at a time, tick " + tick[0] + ": " + holders);
            float hp = pig.getHealth();
            if (hp < last[0]) {
                if (lastBite[0] >= 0) gaps.add(tick[0] - lastBite[0]);
                lastBite[0] = tick[0];
                last[0] = hp;
            }
        });
        helper.succeedWhen(() -> {
            int bites = alpha.bites() + a.bites() + b.bites();
            helper.assertTrue(bites >= 4, "a steady stream of bites, " + bites + " so far");
            helper.assertTrue(turns.size() >= 3 && new HashSet<>(turns.subList(0, 3)).size() == 3, "the first three turns go to three hounds, " + turns.size() + " turns");
            float lost = 100.0F - pig.getHealth();
            float want = 4.0F * (a.bites() + b.bites()) + 6.0F * alpha.bites();
            helper.assertTrue(Math.abs(lost - want) < 0.01F, "bites of 4 and the alpha's 6 make " + want + ", the pig lost " + lost);
            for (int gap : gaps) helper.assertTrue(gap >= 24, "bites land at least 24 ticks apart, saw " + gaps);
        });
    }

    /** A plain hound's bite takes 4, an alpha's 6 (the first bite each). */
    private static void bite(GameTestHelper helper) {
        Gloamhound plain = hound(helper, 2.5, 2.5);
        Gloamhound alpha = hound(helper, 2.5, 9.5);
        Gloamhound.formPack(alpha);
        Pig pigA = pig(helper, 6.5, 2.5);
        Pig pigB = pig(helper, 6.5, 9.5);
        plain.setTarget(pigA);
        alpha.setTarget(pigB);
        float[] first = {0.0F, 0.0F};
        helper.onEachTick(() -> {
            if (first[0] == 0.0F && pigA.getHealth() < 100.0F) first[0] = 100.0F - pigA.getHealth();
            if (first[1] == 0.0F && pigB.getHealth() < 100.0F) first[1] = 100.0F - pigB.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first[0] > 0.0F && first[1] > 0.0F, "both bit, " + first[0] + " and " + first[1]);
            helper.assertTrue(Math.abs(first[0] - 4.0F) < 0.01F, "a hound bites for 4, took " + first[0]);
            helper.assertTrue(Math.abs(first[1] - 6.0F) < 0.01F, "the alpha bites for 6, took " + first[1]);
        });
    }

    /** The dart covers 0.6 blocks a tick and lasts at most 12 ticks (here the pig is lifted out of reach once it starts). */
    private static void dart(GameTestHelper helper) {
        Gloamhound hound = hound(helper, 1.5, 5.5);
        Pig pig = pig(helper, 6.5, 5.5);
        hound.setTarget(pig);
        List<Double> steps = new ArrayList<>();
        double[] lastX = {Double.NaN};
        int[] darting = {0};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (hound.core().in(Step.DART)) {
                if (darting[0] == 0) pig.snapTo(pig.getX(), pig.getY() + 3.0, pig.getZ());
                darting[0]++;
                if (!Double.isNaN(lastX[0])) steps.add(hound.getX() - lastX[0]);
                lastX[0] = hound.getX();
            } else if (darting[0] > 0) {
                over[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the dart ends, " + darting[0] + " ticks so far");
            helper.assertTrue(darting[0] == 12, "an unchecked dart lasts 12 ticks, lasted " + darting[0]);
            double mean = steps.subList(1, steps.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - 0.6) < 0.02, "the dart covers 0.6 blocks a tick, mean " + mean);
            helper.assertTrue(pig.getHealth() >= 100.0F, "the lifted pig was out of reach");
        });
    }

    /** A plain hound: health 18, attack 4, armour 1, a 0.8 x 0.85 hitbox, no ruff. */
    private static void stats(GameTestHelper helper) {
        Gloamhound hound = hound(helper, 5.5, 5.5);
        hound.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!hound.isAlpha() && !hound.core().flag(Gloamhound.ALPHA_FLAG), "a lone hound is no alpha");
            helper.assertTrue(hound.getMaxHealth() == 18.0F && hound.getHealth() == 18.0F, "health 18, got " + hound.getHealth() + "/" + hound.getMaxHealth());
            helper.assertTrue(hound.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4.0, "attack 4, got " + hound.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(hound.getArmorValue() == 1, "armour 1, got " + hound.getArmorValue());
            helper.assertTrue(Math.abs(hound.getBbWidth() - 0.8F) < 0.01F && Math.abs(hound.getBbHeight() - 0.85F) < 0.01F,
                    "a 0.8 x 0.85 hitbox, got " + hound.getBbWidth() + " x " + hound.getBbHeight());
            helper.succeed();
        });
    }

    /** The alpha: health 30 (full), attack 6, armour 1, 1.2x the size (0.96 x 1.02), its ruff flag set. */
    private static void alphaStats(GameTestHelper helper) {
        Gloamhound alpha = hound(helper, 5.5, 5.5);
        alpha.setNoAi(true);
        Gloamhound.formPack(alpha);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(alpha.isAlpha() && alpha.core().flag(Gloamhound.ALPHA_FLAG), "it leads, and shows its ruff");
            helper.assertTrue(alpha.getMaxHealth() == 30.0F && alpha.getHealth() == 30.0F, "health 30, got " + alpha.getHealth() + "/" + alpha.getMaxHealth());
            helper.assertTrue(alpha.getAttributeValue(Attributes.ATTACK_DAMAGE) == 6.0, "attack 6, got " + alpha.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(alpha.getArmorValue() == 1, "armour 1, got " + alpha.getArmorValue());
            helper.assertTrue(Math.abs(alpha.getBbWidth() - 0.96F) < 0.01F && Math.abs(alpha.getBbHeight() - 1.02F) < 0.01F,
                    "a 0.96 x 1.02 hitbox, got " + alpha.getBbWidth() + " x " + alpha.getBbHeight());
            helper.succeed();
        });
    }

    /**
     * The howl: the alpha howls as soon as it has a target and again 200 ticks later; each howl gives every packmate
     * within 16 blocks (itself too) Speed I for 120 ticks; one 16.4 blocks off gets nothing. The alpha here cannot walk,
     * so it never takes a turn that could delay a howl.
     */
    private static void howl(GameTestHelper helper) {
        Gloamhound alpha = hound(helper, 0.5, 0.5);
        Gloamhound near = hound(helper, 11.5, 11.5);   // 15.56 blocks off
        Gloamhound far = hound(helper, 0.5, 16.9);     // 16.4 blocks off, out over the gap between arenas
        Gloamhound.formPack(alpha, near, far);
        alpha.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        near.setNoAi(true);
        far.setNoAi(true);
        far.setNoGravity(true);
        Pig pig = pig(helper, 0.5, 8.5);               // 8 blocks: beyond its dart range
        alpha.setTarget(pig);
        int[] tick = {0};
        int[] howls = {-1, -1};
        int[] fast = {0};
        boolean[] farFast = {false};
        helper.onEachTick(() -> {
            tick[0]++;
            if (alpha.howls() >= 1 && howls[0] < 0) {
                howls[0] = tick[0];
                helper.assertTrue(alpha.core().in(Step.HOWL), "it howls, phase " + alpha.phase());
                MobEffectInstance speed = near.getEffect(MobEffects.SPEED);
                helper.assertTrue(speed != null && speed.getAmplifier() == 0, "the howl gives a packmate 15.6 blocks off Speed I, got " + speed);
                helper.assertTrue(speed.getDuration() >= 118 && speed.getDuration() <= 120, "for 120 ticks, " + speed.getDuration() + " left");
                helper.assertTrue(alpha.hasEffect(MobEffects.SPEED), "the alpha too");
            }
            if (alpha.howls() >= 2 && howls[1] < 0) howls[1] = tick[0];
            if (howls[0] > 0 && howls[1] < 0 && near.hasEffect(MobEffects.SPEED)) fast[0]++;
            farFast[0] |= far.hasEffect(MobEffects.SPEED);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(howls[1] > 0, "it howls a second time, first at tick " + howls[0]);
            helper.assertTrue(howls[1] - howls[0] == 200, "howls come 200 ticks apart, saw " + (howls[1] - howls[0]));
            helper.assertTrue(Math.abs(fast[0] - 120) <= 1, "Speed lasts 120 ticks, lasted " + fast[0]);
            helper.assertTrue(!farFast[0], "a packmate 16.4 blocks off never hears it");
        });
    }

    /** The rout lasts 200 ticks: each packmate flees for exactly that long, then stops. */
    private static void fleeLength(GameTestHelper helper) {
        Gloamhound alpha = hound(helper, 5.5, 5.5);
        Gloamhound a = hound(helper, 3.5, 8.5);
        Gloamhound b = hound(helper, 7.5, 8.5);
        Gloamhound.formPack(alpha, a, b);
        for (Gloamhound h : List.of(a, b)) h.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);   // stay on stage
        int[] fled = {0, 0};
        boolean[] over = {false, false};
        helper.runAtTickTime(3, () -> alpha.kill(helper.getLevel()));
        helper.onEachTick(() -> {
            Gloamhound[] pair = {a, b};
            for (int i = 0; i < 2; i++) {
                if (pair[i].core().in(Step.FLEE)) fled[i]++;
                else if (fled[i] > 0) over[i] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0] && over[1], "the flight ends, " + fled[0] + " and " + fled[1] + " ticks so far");
            helper.assertTrue(Math.abs(fled[0] - 200) <= 1 && Math.abs(fled[1] - 200) <= 1, "they flee for 200 ticks, fled " + fled[0] + " and " + fled[1]);
        });
    }

    /**
     * A natural group of four is one pack: the first founds it as the alpha and the rest join, in its look. A hound
     * spawned by command next to a full pack founds its own; the next one spawned beside it joins that.
     */
    private static void packSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Gloamhound> group = new ArrayList<>();
        SpawnGroupData data = null;
        for (int i = 0; i < 4; i++) {
            Gloamhound h = ModMobs.GLOAMHOUND.get().create(level, EntitySpawnReason.NATURAL);
            Vec3 at = helper.absoluteVec(new Vec3(3.5 + i * 1.5, 1, 3.5));
            h.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            data = h.finalizeSpawn(level, level.getCurrentDifficultyAt(h.blockPosition()), EntitySpawnReason.NATURAL, data);
            h.setNoAi(true);
            level.addFreshEntity(h);
            TestPlayers.whenDone(helper, h::discard);
            group.add(h);
        }
        Gloamhound lone = helper.spawn(ModMobs.GLOAMHOUND.get(), new Vec3(6.5, 1, 9.5), EntitySpawnReason.COMMAND);
        lone.setNoAi(true);
        TestPlayers.whenDone(helper, lone::discard);
        Gloamhound joiner = helper.spawn(ModMobs.GLOAMHOUND.get(), new Vec3(8.5, 1, 9.5), EntitySpawnReason.COMMAND);
        joiner.setNoAi(true);
        TestPlayers.whenDone(helper, joiner::discard);
        helper.runAtTickTime(2, () -> {
            UUID pack = group.getFirst().packId();
            helper.assertTrue(group.getFirst().isAlpha(), "the group's first hound leads it");
            helper.assertTrue(group.stream().filter(Gloamhound::isAlpha).count() == 1, "one alpha in the group");
            helper.assertTrue(group.stream().allMatch(h -> h.packId().equals(pack)), "all four share one pack");
            helper.assertTrue(group.stream().allMatch(h -> h.look().equals(group.getFirst().look())), "all four wear the alpha's look");
            helper.assertTrue(group.getFirst().pack().size() == 4, "the pack counts four, got " + group.getFirst().pack().size());
            helper.assertTrue(lone.isAlpha() && !lone.packId().equals(pack), "next to a full pack a new hound founds its own");
            helper.assertTrue(!joiner.isAlpha() && joiner.packId().equals(lone.packId()), "the next one joins it");
            helper.succeed();
        });
    }

    /**
     * Hounds that arrive with no pack at all (summoned with data, so never finalised) settle on their first tick: one
     * founds a pack as its alpha and the other joins it, each keeping its own look.
     */
    private static void settle(GameTestHelper helper) {
        Gloamhound first = helper.spawn(ModMobs.GLOAMHOUND.get(), new Vec3(5.5, 1, 5.5));
        Gloamhound second = helper.spawn(ModMobs.GLOAMHOUND.get(), new Vec3(7.5, 1, 5.5));
        for (Gloamhound h : List.of(first, second)) {
            h.setNoAi(true);
            TestPlayers.whenDone(helper, h::discard);
        }
        first.core().setLook("base", true);
        second.core().setLook("snowpelt", true);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(first.isAlpha() != second.isAlpha(), "one of them leads, alphas " + first.isAlpha() + ", " + second.isAlpha());
            helper.assertTrue(first.packId().equals(second.packId()), "they share one pack");
            helper.assertTrue(first.look().equals("base") && second.look().equals("snowpelt"), "each keeps its own look");
            helper.succeed();
        });
    }

    /** Pack and alpha survive a save and reload: the reloaded hounds share their pack, and the alpha keeps 30/6/1.2x. */
    private static void saveLoad(GameTestHelper helper) {
        Gloamhound alpha = hound(helper, 3.5, 5.5);
        Gloamhound member = hound(helper, 8.5, 5.5);
        UUID id = Gloamhound.formPack(alpha, member);
        alpha.setNoAi(true);
        member.setNoAi(true);
        Gloamhound[] copies = new Gloamhound[2];
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            Gloamhound[] originals = {alpha, member};
            for (int i = 0; i < 2; i++) {
                TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                originals[i].saveWithoutId(out);
                CompoundTag tag = out.buildResult();
                tag.remove("UUID");
                Gloamhound copy = ModMobs.GLOAMHOUND.get().create(level, EntitySpawnReason.LOAD);
                copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                originals[i].discard();
                level.addFreshEntity(copy);
                TestPlayers.whenDone(helper, copy::discard);
                copies[i] = copy;
            }
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(copies[0].packId().equals(id) && copies[1].packId().equals(id), "both keep their pack");
            helper.assertTrue(copies[0].isAlpha() && copies[0].core().flag(Gloamhound.ALPHA_FLAG), "the alpha is still the alpha, ruff and all");
            helper.assertTrue(!copies[1].isAlpha(), "the packmate is still not");
            helper.assertTrue(copies[0].getMaxHealth() == 30.0F && copies[0].getHealth() == 30.0F, "the alpha keeps health 30, got " + copies[0].getHealth());
            helper.assertTrue(copies[0].getAttributeValue(Attributes.ATTACK_DAMAGE) == 6.0, "and attack 6");
            helper.assertTrue(Math.abs(copies[0].getBbWidth() - 0.96F) < 0.01F, "and its size, width " + copies[0].getBbWidth());
            helper.assertTrue(copies[1].getMaxHealth() == 18.0F, "the packmate keeps health 18");
            helper.assertTrue(copies[0].pack().size() == 2 && copies[0].pack().contains(copies[1]), "they find each other as one pack");
            helper.succeed();
        });
    }

    /**
     * Home: the taiga (snowy and old-growth too), never the plains; spawn weight 15, in groups of 3 to 4; a monster,
     * so it spawns at night: it rates a spot lit by glowstone below 0, as vanilla's do.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("gloamhound"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.TAIGA)), "its home holds the taiga");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SNOWY_TAIGA)), "its home holds the snowy taiga");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)), "its home holds the old-growth spruce taiga");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.PLAINS)), "its home never holds the plains");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/gloamhound")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.GLOAMHOUND.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 15, "spawn weight 15, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 3 && entry.value().count().maxInclusive() == 4,
                "in packs of 3 to 4, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.GLOAMHOUND.get().getCategory() == MobCategory.MONSTER, "a monster");
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at.east(), Blocks.GLOWSTONE);
        Gloamhound hound = hound(helper, 2.5, 2.5);
        hound.setNoAi(true);
        BlockPos abs = helper.absolutePos(at);
        helper.runAtTickTime(2, () -> {
            float value = hound.getWalkTargetValue(abs, helper.getLevel());
            helper.assertTrue(value < 0.0F, "a lit spot rates below 0, so it only spawns in the dark, got " + value);
            helper.succeed();
        });
    }
}
