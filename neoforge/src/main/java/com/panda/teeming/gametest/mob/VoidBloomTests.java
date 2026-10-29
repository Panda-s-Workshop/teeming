package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.end.VoidBloom;
import com.panda.teeming.mob.end.VoidBloom.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
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
 * Void Bloom: its stain starts at radius 0.5 and grows exactly 0.5 every 40 ticks while it has a target, to exactly 8
 * and no further, and not at all asleep; an 8-tick tell (the glow lit, the radius unchanged) before every step; inside,
 * 1 damage a second (through armour) and Weakness I, which fades soon after leaving; nothing outside, nothing 2 blocks
 * up, nothing to itself or another bloom; mobs are hurt too. Killed, the stain recedes over 40 ticks, harmless, the body
 * goes with it and nothing grows; a reload keeps the radius. Every number is checked against a plain value. The blooms
 * are NoAI (their stain runs from their tick), and nothing the stain reaches outside this arena is touched.
 */
public final class VoidBloomTests {
    private VoidBloomTests() {}

    private static final Vec3 AT = new Vec3(6.5, 1, 6.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("void_bloom_tell", VoidBloomTests::tell);
        out.accept("void_bloom_counter", VoidBloomTests::counter);
        out.accept("void_bloom_failure", VoidBloomTests::failure);
        TeemingGameTests.timeLimit("void_bloom_failure", 160);
        out.accept("void_bloom_growth_start", VoidBloomTests::growthStart);
        TeemingGameTests.timeLimit("void_bloom_growth_start", 140);
        out.accept("void_bloom_growth_max", VoidBloomTests::growthMax);
        TeemingGameTests.timeLimit("void_bloom_growth_max", 220);
        out.accept("void_bloom_asleep", VoidBloomTests::asleep);
        out.accept("void_bloom_damage", VoidBloomTests::damage);
        out.accept("void_bloom_height", VoidBloomTests::height);
        out.accept("void_bloom_weakness", VoidBloomTests::weakness);
        out.accept("void_bloom_spares_blooms", VoidBloomTests::sparesBlooms);
        out.accept("void_bloom_recede", VoidBloomTests::recede);
        out.accept("void_bloom_dead_no_growth", VoidBloomTests::deadNoGrowth);
        out.accept("void_bloom_save_load", VoidBloomTests::saveLoad);
        out.accept("void_bloom_stats", VoidBloomTests::stats);
        out.accept("void_bloom_home", VoidBloomTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A still pig with 100 health at (x, 1, z), removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /**
     * A NoAI bloom with the base look (its target goals never run, so it keeps whatever target the test hands it; the
     * stain runs from its tick all the same). Its stain touches nothing outside this arena.
     */
    private static VoidBloom bloom(GameTestHelper helper, Vec3 at) {
        VoidBloom bloom = helper.spawn(ModMobs.VOID_BLOOM.get(), at);
        bloom.core().setLook("base", true);
        bloom.setNoAi(true);
        bloom.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        contain(helper, bloom);
        TestPlayers.whenDone(helper, bloom::discard);
        return bloom;
    }

    /** Vetoes the stain's touch on anything outside this arena. */
    private static void contain(GameTestHelper helper, VoidBloom bloom) {
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == bloom && e.step().equals("stain") && e.target() != null && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
    }

    /** Records the test tick of each growth step, tell and recede of {@code bloom}. */
    private static final class Log implements Consumer<MechanicEvent> {
        final GameTestHelper helper;
        final VoidBloom bloom;
        final List<Integer> grows = new ArrayList<>();
        final List<Integer> tells = new ArrayList<>();
        int recedes;

        Log(GameTestHelper helper, VoidBloom bloom) {
            this.helper = helper;
            this.bloom = bloom;
            MechanicEvent.listen(this);
            TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(this));
        }

        @Override
        public void accept(MechanicEvent e) {
            if (e.mob() != bloom || e.cancelled()) return;
            switch (e.step()) {
                case "grow" -> grows.add((int) helper.getTick());
                case "tell" -> tells.add((int) helper.getTick());
                case "recede" -> recedes++;
                default -> {}
            }
        }
    }

    /** One stain pulse on a survival player, by difficulty: mob damage to players scales (1 on Easy and Normal, 1.5 on Hard). */
    private static float pulse(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case HARD -> 1.5F;
            default -> 1.0F;
        };
    }

    private static boolean weak(LivingEntity e) {
        MobEffectInstance w = e.getEffect(MobEffects.WEAKNESS);
        return w != null && w.getAmplifier() == 0;
    }

    // ---- the tell, the counter, the failure ------------------------------------------------------------------------

    /**
     * The tell lasts exactly 8 ticks right before the step: the mouth and drips lit (glow stage 1), the radius still
     * 0.5 and nobody hurt; the step that follows is exactly 0.5.
     */
    private static void tell(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        Pig pig = pig(helper, 6.5, 10.5);
        bloom.setTarget(pig);
        int[] tellTicks = {0}, tellBeforeGrowth = {-1};
        boolean[] quiet = {true}, lit = {true};
        double[] grewBy = {0.0};
        helper.onEachTick(() -> {
            if (tellBeforeGrowth[0] >= 0) return;
            double r = bloom.stainRadius();
            if (r != 0.5) {
                tellBeforeGrowth[0] = tellTicks[0];
                grewBy[0] = r - 0.5;
                return;
            }
            if (bloom.core().in(Step.TELL)) {
                tellTicks[0]++;
                if (bloom.core().stage() != 1) lit[0] = false;
                if (pig.getHealth() < 100.0F) quiet[0] = false;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tellBeforeGrowth[0] >= 0, "the stain grew, radius " + bloom.stainRadius() + ", phase " + bloom.phase());
            helper.assertTrue(tellBeforeGrowth[0] == 8, "the tell held 8 ticks before the step, got " + tellBeforeGrowth[0]);
            helper.assertTrue(Math.abs(grewBy[0] - 0.5) < 1.0E-9, "the step is 0.5, got " + grewBy[0]);
            helper.assertTrue(lit[0], "lit through the tell");
            helper.assertTrue(quiet[0], "nothing hurt during the tell");
            helper.assertTrue(bloom.core().idle() && bloom.core().stage() == 0, "dark and idle after the step");
        });
    }

    /**
     * The counter: a survival player inside a radius-6 stain kills the bloom. The stain recedes (a recede event), is
     * harmless from the death on, and the body is gone 40 ticks after.
     */
    private static void counter(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        bloom.setStainRadius(6.0);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 1, 9.5));
        Log log = new Log(helper, bloom);
        float[] atDeath = {-1.0F};
        int[] diedAt = {-1}, goneAt = {-1};
        helper.runAtTickTime(25, () -> {
            bloom.hurtServer(helper.getLevel(), bloom.damageSources().playerAttack(p), 100.0F);
            diedAt[0] = (int) helper.getTick();
            atDeath[0] = p.getHealth();
        });
        helper.onEachTick(() -> {
            if (diedAt[0] >= 0 && goneAt[0] < 0 && bloom.isRemoved()) goneAt[0] = (int) helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(goneAt[0] >= 0, "the body goes once the stain is gone");
            helper.assertTrue(log.recedes == 1, "one recede, got " + log.recedes);
            helper.assertTrue(p.getHealth() < 20.0F, "the player was hurt standing in it before, health " + p.getHealth());
            helper.assertTrue(p.getHealth() == atDeath[0], "and never after the death: " + atDeath[0] + " then " + p.getHealth());
            int took = goneAt[0] - diedAt[0];
            helper.assertTrue(took >= 39 && took <= 41, "gone 40 ticks after the death, took " + took);
        });
    }

    /**
     * Failing the counter: a survival player waits 2.3 blocks from a bloom whose stain is 1.5 and that has noticed
     * them. Unhurt while the stain is narrower; once the second step (to 2.5) reaches them, they take damage.
     */
    private static void failure(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        bloom.setStainRadius(1.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 1, 8.8));
        bloom.setTarget(p);
        Log log = new Log(helper, bloom);
        boolean[] hurtEarly = {false};
        int[] reached = {-1};
        helper.onEachTick(() -> {
            if (bloom.stainRadius() < 2.3 && p.getHealth() < 20.0F) hurtEarly[0] = true;
            if (reached[0] < 0 && bloom.stainRadius() >= 2.5) reached[0] = (int) helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!hurtEarly[0], "unhurt while the stain was narrower than 2.3");
            helper.assertTrue(log.grows.size() >= 2 && reached[0] >= 0, "two steps, to 2.5, grows " + log.grows);
            helper.assertTrue(helper.getTick() >= reached[0] + 21, "a second of waiting in it");
            helper.assertTrue(p.getHealth() <= 20.0F - pulse(helper) + 1.0E-4F, "the stain reached them: health " + p.getHealth());
            helper.assertTrue(weak(p), "and weakened them");
        });
    }

    // ---- growth ----------------------------------------------------------------------------------------------------

    /** A fresh bloom's stain is exactly 0.5; once it has a target it grows 0.5 a step, the steps exactly 40 ticks apart. */
    private static void growthStart(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        helper.assertTrue(bloom.stainRadius() == 0.5, "it starts at 0.5, got " + bloom.stainRadius());
        Pig pig = pig(helper, 6.5, 10.5);
        bloom.setTarget(pig);
        List<Integer> stepAt = new ArrayList<>();
        List<Double> radii = new ArrayList<>();
        double[] last = {0.5};
        helper.onEachTick(() -> {
            double r = bloom.stainRadius();
            if (r != last[0]) {
                stepAt.add((int) helper.getTick());
                radii.add(r);
                last[0] = r;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepAt.size() >= 2, "two steps, got " + radii);
            helper.assertTrue(radii.get(0) == 1.0 && radii.get(1) == 1.5, "0.5 to 1.0 to 1.5, got " + radii);
            helper.assertTrue(stepAt.get(1) - stepAt.get(0) == 40, "40 ticks apart, got " + stepAt);
        });
    }

    /** From 7, the stain grows to 7.5 and to exactly 8, then holds 8 for 80 ticks with no further tell or step. */
    private static void growthMax(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        bloom.setStainRadius(7.0);
        Pig pig = pig(helper, 6.5, 9.5);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        bloom.setTarget(pig);
        Log log = new Log(helper, bloom);
        double[] max = {7.0};
        int[] full = {-1};
        helper.onEachTick(() -> {
            max[0] = Math.max(max[0], bloom.stainRadius());
            if (full[0] < 0 && bloom.stainRadius() >= 8.0) full[0] = (int) helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(full[0] >= 0, "it reached 8, radius " + bloom.stainRadius());
            helper.assertTrue(helper.getTick() >= full[0] + 80, "held for 80 ticks");
            helper.assertTrue(bloom.stainRadius() == 8.0 && max[0] == 8.0, "exactly 8 and never more, now " + bloom.stainRadius() + ", most " + max[0]);
            helper.assertTrue(log.grows.size() == 2, "two steps (7.5, 8), got " + log.grows);
            helper.assertTrue(log.tells.size() == 2, "a tell for each step and none after, got " + log.tells);
            helper.assertTrue(log.grows.get(1) - log.grows.get(0) == 40, "40 ticks apart, got " + log.grows);
        });
    }

    /** With no target it is asleep: the stain holds at 0.5 for 100 ticks, with no tell. */
    private static void asleep(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        Log log = new Log(helper, bloom);
        helper.runAtTickTime(95, () -> {
            helper.assertTrue(bloom.stainRadius() == 0.5, "still 0.5, got " + bloom.stainRadius());
            helper.assertTrue(log.grows.isEmpty() && log.tells.isEmpty(), "no step and no tell: " + log.grows + " " + log.tells);
            helper.succeed();
        });
    }

    // ---- the stain's touch -----------------------------------------------------------------------------------------

    /**
     * A radius-3 stain (asleep, so it holds): over 60 ticks a bare survival player 2 blocks out loses exactly 3 (1 a
     * second), one with a diamond set's armour 2 blocks out loses 3 x 0.21 (armour counts), one 4.5 blocks out loses nothing.
     * Nobody is moved.
     */
    private static void damage(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        bloom.setStainRadius(3.0);
        ServerPlayer in = TestPlayers.survivor(helper, new Vec3(6.5, 1, 8.5));
        ServerPlayer armoured = TestPlayers.survivor(helper, new Vec3(4.5, 1, 6.5));
        // a full diamond set's armour and toughness (a test survivor has no client, so worn gear is never counted)
        armoured.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
        armoured.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0);
        ServerPlayer out = TestPlayers.survivor(helper, new Vec3(6.5, 1, 11.0));
        float[] start = new float[3];
        Vec3[] stood = new Vec3[2];
        helper.runAtTickTime(10, () -> {
            start[0] = in.getHealth();
            start[1] = armoured.getHealth();
            start[2] = out.getHealth();
            stood[0] = in.position();
            stood[1] = armoured.position();
        });
        helper.runAtTickTime(70, () -> {
            float p = pulse(helper);
            float bare = start[0] - in.getHealth(), clad = start[1] - armoured.getHealth(), none = start[2] - out.getHealth();
            helper.assertTrue(Math.abs(bare - 3.0F * p) < 0.01F, "the bare player lost " + 3.0F * p + " in 60 ticks, got " + bare);
            // diamond: 20 armour, 8 toughness against a hit of 1 leaves 1 x (1 - 19.75 / 25) = 0.21 a pulse
            float each = p * (1.0F - Math.min(20.0F, 20.0F - 4.0F * p / 16.0F) / 25.0F);
            helper.assertTrue(Math.abs(clad - 3.0F * each) < 0.02F, "the armoured player lost " + 3.0F * each + ", got " + clad);
            helper.assertTrue(none == 0.0F, "the player outside lost nothing, got " + none);
            helper.assertTrue(in.position().distanceTo(stood[0]) < 0.01 && armoured.position().distanceTo(stood[1]) < 0.01, "nobody was moved");
            helper.succeed();
        });
    }

    /** A survival player 2 blocks up on a pillar, inside the stain's radius, is untouched: it lies on the ground. */
    private static void height(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        bloom.setStainRadius(4.0);
        helper.setBlock(new BlockPos(6, 1, 9), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 2, 9), Blocks.STONE);
        ServerPlayer up = TestPlayers.survivor(helper, new Vec3(6.5, 3, 9.5));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(up.getHealth() == 20.0F, "the player on the pillar is unhurt, health " + up.getHealth());
            helper.assertTrue(!weak(up), "and not weakened");
            helper.succeed();
        });
    }

    /**
     * Weakness I while inside, topped up to at most 20 ticks; 25 ticks after stepping out it is gone. (A pig: a test
     * survivor has no client, so its effects never count down.)
     */
    private static void weakness(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        bloom.setStainRadius(3.0);
        Pig pig = pig(helper, 6.5, 8.5);
        int[] longest = {0};
        helper.onEachTick(() -> {
            MobEffectInstance w = pig.getEffect(MobEffects.WEAKNESS);
            if (w != null && helper.getTick() < 40) longest[0] = Math.max(longest[0], w.getDuration());
        });
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(weak(pig), "Weakness I inside, got " + pig.getEffect(MobEffects.WEAKNESS));
            helper.assertTrue(longest[0] > 0 && longest[0] <= 20, "topped up to at most 20 ticks, saw " + longest[0]);
            Vec3 away = helper.absoluteVec(new Vec3(6.5, 1, 11.5));
            pig.teleportTo(away.x, away.y, away.z);
        });
        helper.runAtTickTime(42, () -> helper.assertTrue(pig.position().distanceTo(helper.absoluteVec(AT)) > 4.5, "stepped out"));
        helper.runAtTickTime(65, () -> {
            helper.assertTrue(pig.getEffect(MobEffects.WEAKNESS) == null, "Weakness gone after stepping out, got " + pig.getEffect(MobEffects.WEAKNESS));
            helper.succeed();
        });
    }

    /**
     * Two blooms 2 blocks apart, each inside the other's radius-4 stain, and a pig between them: over 60 ticks the pig
     * (a mob) loses exactly 3 and is weakened, both blooms keep all 30 health.
     */
    private static void sparesBlooms(GameTestHelper helper) {
        VoidBloom a = bloom(helper, new Vec3(5.5, 1, 6.5));
        VoidBloom b = bloom(helper, new Vec3(7.5, 1, 6.5));
        a.setStainRadius(4.0);
        b.setStainRadius(4.0);
        Pig pig = pig(helper, 6.5, 8.0);
        float[] start = {0.0F};
        helper.runAtTickTime(10, () -> start[0] = pig.getHealth());
        helper.runAtTickTime(70, () -> {
            float lost = start[0] - pig.getHealth();
            // two stains, one pulse each a second, but a hit within half a second of the last is turned away
            helper.assertTrue(Math.abs(lost - 3.0F) < 0.01F, "the pig lost 3 in 60 ticks, got " + lost);
            helper.assertTrue(weak(pig), "and is weakened");
            helper.assertTrue(a.getHealth() == 30.0F && b.getHealth() == 30.0F, "both blooms unhurt: " + a.getHealth() + ", " + b.getHealth());
            helper.assertTrue(!weak(a) && !weak(b), "and not weakened");
            helper.succeed();
        });
    }

    // ---- death -----------------------------------------------------------------------------------------------------

    /**
     * Killed with a radius-6 stain, it recedes over 40 ticks: never wider tick by tick, about half (3) 20 ticks in,
     * and the body is removed at 40. A pig standing in it is not hurt from the death on.
     */
    private static void recede(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        bloom.setStainRadius(6.0);
        Pig pig = pig(helper, 6.5, 9.5);
        int[] diedAt = {-1}, goneAt = {-1};
        float[] pigAtDeath = {0.0F};
        double[] prev = {6.0}, atHalf = {-1.0};
        boolean[] widened = {false};
        helper.runAtTickTime(10, () -> {
            bloom.kill(helper.getLevel());
            diedAt[0] = (int) helper.getTick();
            pigAtDeath[0] = pig.getHealth();
        });
        helper.onEachTick(() -> {
            if (diedAt[0] < 0 || goneAt[0] >= 0) return;
            if (bloom.isRemoved()) {
                goneAt[0] = (int) helper.getTick();
                return;
            }
            double r = bloom.stainRadius();
            if (r > prev[0] + 1.0E-9) widened[0] = true;
            prev[0] = r;
            if (helper.getTick() == diedAt[0] + 20) atHalf[0] = r;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(goneAt[0] >= 0, "the body goes, radius " + bloom.stainRadius());
            int took = goneAt[0] - diedAt[0];
            helper.assertTrue(took >= 39 && took <= 41, "the recede took 40 ticks, got " + took);
            helper.assertTrue(!widened[0], "it never widened while receding");
            helper.assertTrue(Math.abs(atHalf[0] - 3.0) <= 0.35, "about 3 halfway, got " + atHalf[0]);
            helper.assertTrue(prev[0] <= 0.35, "almost nothing just before the end, got " + prev[0]);
            helper.assertTrue(pig.getHealth() == pigAtDeath[0], "the pig unhurt from the death on: " + pigAtDeath[0] + " then " + pig.getHealth());
        });
    }

    /** Killed during a tell, nothing grows: no step, and the stain never wider than at the death. */
    private static void deadNoGrowth(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        bloom.setStainRadius(2.0);
        Pig pig = pig(helper, 6.5, 10.5);
        bloom.setTarget(pig);
        Log log = new Log(helper, bloom);
        double[] atDeath = {-1.0}, widest = {0.0};
        helper.onEachTick(() -> {
            if (atDeath[0] < 0 && bloom.core().in(Step.TELL) && bloom.core().elapsed() == 4) {
                bloom.kill(helper.getLevel());
                atDeath[0] = bloom.stainRadius();
            } else if (atDeath[0] >= 0 && !bloom.isRemoved()) {
                widest[0] = Math.max(widest[0], bloom.stainRadius());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(atDeath[0] == 2.0, "killed mid-tell at 2.0, got " + atDeath[0]);
            helper.assertTrue(bloom.isRemoved(), "the recede ran out");
            helper.assertTrue(log.grows.isEmpty(), "no step after the death, got " + log.grows);
            helper.assertTrue(widest[0] <= 2.0, "never wider than at the death, got " + widest[0]);
        });
    }

    // ---- state -----------------------------------------------------------------------------------------------------

    /** The stain's radius and the count to its next step survive a save and reload. */
    private static void saveLoad(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        Pig pig = pig(helper, 6.5, 11.0);
        bloom.setStainRadius(4.5);
        bloom.setTarget(pig);
        VoidBloom[] copy = {null};
        int[] growIn = {0};
        helper.runAtTickTime(10, () -> {
            ServerLevel level = helper.getLevel();
            growIn[0] = bloom.growIn();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            bloom.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            VoidBloom c = ModMobs.VOID_BLOOM.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            bloom.discard();
            c.setNoAi(true);
            level.addFreshEntity(c);
            contain(helper, c);
            TestPlayers.whenDone(helper, c::discard);
            copy[0] = c;
        });
        helper.runAtTickTime(12, () -> {
            VoidBloom c = copy[0];
            helper.assertTrue(c.stainRadius() == 4.5, "the radius carries over: 4.5, got " + c.stainRadius());
            helper.assertTrue(growIn[0] < 40 && c.growIn() == growIn[0], "and the count to the next step: " + growIn[0] + " then " + c.growIn());
            helper.succeed();
        });
    }

    /** Health 30, armour 2, no attack, a 0.9 x 1.2 hitbox, rooted. */
    private static void stats(GameTestHelper helper) {
        VoidBloom bloom = bloom(helper, AT);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(bloom.getMaxHealth() == 30.0F && bloom.getHealth() == 30.0F, "health 30, got " + bloom.getHealth() + "/" + bloom.getMaxHealth());
            helper.assertTrue(bloom.getArmorValue() == 2, "armour 2, got " + bloom.getArmorValue());
            helper.assertTrue(bloom.getAttributeValue(Attributes.ATTACK_DAMAGE) == 0.0, "no attack, got " + bloom.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(Math.abs(bloom.getBbWidth() - 0.9F) < 0.01F && Math.abs(bloom.getBbHeight() - 1.2F) < 0.01F,
                    "a 0.9 x 1.2 hitbox, got " + bloom.getBbWidth() + " x " + bloom.getBbHeight());
            helper.assertTrue(bloom.pin() == Pin.ROOTED, "rooted, got " + bloom.pin());
            helper.succeed();
        });
    }

    /**
     * Home: the end barrens, never the highlands or the main island; spawn weight 3, alone; a monster. Its ground spawn
     * rule takes end stone and refuses a spot with nothing under it.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("void_bloom"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "void_bloom").stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "its home holds the end barrens");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "void_bloom").stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.THE_END)), "never the highlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/void_bloom")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.VOID_BLOOM.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 3, "spawn weight 3, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, groups of " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.VOID_BLOOM.get().getCategory() == MobCategory.MONSTER, "a monster");

        RosterStats.Entry row = RosterStats.get("void_bloom");
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground mob, place " + row.place());
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.END_STONE);
        helper.setBlock(new BlockPos(9, 0, 9), Blocks.AIR);
        helper.assertTrue(TeemingSpawns.placeOk(row, ModMobs.VOID_BLOOM.get(), helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))),
                "end stone is a spawn spot");
        helper.assertTrue(!TeemingSpawns.placeOk(row, ModMobs.VOID_BLOOM.get(), helper.getLevel(), helper.absolutePos(new BlockPos(9, 1, 9))),
                "a spot with nothing under it is not");
        helper.succeed();
    }
}
