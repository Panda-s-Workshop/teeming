package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.Siphonjelly;
import com.panda.teeming.mob.nether.Siphonjelly.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Siphonjelly: with its target close it flares for 10 ticks, hurting and taking nothing, then stings for its attack
 * (2). A sting that lands takes the target's longest beneficial effect, at most 600 ticks of it (the target keeps the
 * rest, and keeps an endless one), and puts it on the jelly, which glows in that effect's colour (never in the gold
 * band) and has its use. It holds one at a time. Killed, it gives the effect back with the time it had left to the one
 * it took it from, alive and within 32 blocks; else it is lost. A sting that finds nothing starves it for 100 ticks.
 *
 * <p>Pigs stand in as victims where numbers must be exact: they keep effects as players do, and take the jelly's
 * attack unscaled by difficulty. Hunting jellies get a follow range of 1 so they never notice a survival player in a
 * neighbouring arena, and leave with the test.
 */
public final class SiphonjellyTests {
    private SiphonjellyTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("siphonjelly_tell", SiphonjellyTests::tell);
        out.accept("siphonjelly_counter", SiphonjellyTests::killGivesBack);
        out.accept("siphonjelly_failure", SiphonjellyTests::stingSiphons);
        out.accept("siphonjelly_starve", SiphonjellyTests::starve);
        TeemingGameTests.timeLimit("siphonjelly_starve", 220);
        out.accept("siphonjelly_cap", SiphonjellyTests::cap);
        out.accept("siphonjelly_one_at_a_time", SiphonjellyTests::oneAtATime);
        out.accept("siphonjelly_return_range", SiphonjellyTests::returnRange);
        out.accept("siphonjelly_benefits", SiphonjellyTests::benefits);
        out.accept("siphonjelly_glow", SiphonjellyTests::glow);
        out.accept("siphonjelly_step_back", SiphonjellyTests::stepBack);
        out.accept("siphonjelly_siphon_cancel", SiphonjellyTests::siphonCancel);
        out.accept("siphonjelly_stats", SiphonjellyTests::stats);
        out.accept("siphonjelly_home", SiphonjellyTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A jelly with no AI at {@code at}: it hangs where it is put and stings when a test tells it to. */
    private static Siphonjelly still(GameTestHelper helper, Vec3 at) {
        Siphonjelly jelly = helper.spawn(ModMobs.SIPHONJELLY.get(), at);
        jelly.setNoAi(true);
        return jelly;
    }

    /** A hunting jelly, blind to anything beyond 1 block that it isn't handed, removed when the test ends. */
    private static Siphonjelly hunter(GameTestHelper helper, Vec3 at) {
        Siphonjelly jelly = helper.spawn(ModMobs.SIPHONJELLY.get(), at);
        jelly.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, jelly::discard);
        return jelly;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    private static void give(LivingEntity e, Holder<MobEffect> effect, int ticks, int amplifier) {
        e.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    private static int left(LivingEntity e, Holder<MobEffect> effect) {
        MobEffectInstance has = e.getEffect(effect);
        return has == null ? 0 : has.getDuration();
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A buffed pig beside it: it flares at once (after its first 10 ticks), for 10 ticks, holding still; no damage and
     * no theft until the flare ends, and then the sting lands.
     */
    private static void tell(GameTestHelper helper) {
        Siphonjelly jelly = hunter(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(6.6, 1, 5.5));
        give(pig, MobEffects.SPEED, 2000, 0);
        jelly.setTarget(pig);
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] early = {false}, stung = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            stung[0] |= jelly.core().in(Step.STING);
            if (!jelly.core().in(Step.TELL)) return;
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = jelly.position();
            }
            ticks[0]++;
            early[0] |= pig.getHealth() < pig.getMaxHealth() || jelly.held() != null || left(pig, MobEffects.SPEED) < 1900;
            moved[0] = Math.max(moved[0], jelly.position().distanceTo(start[0]));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && !jelly.core().in(Step.TELL), "the flare runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(first[0] <= 14, "with its target this close it flares as soon as it may, started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 10) <= 1, "the flare lasts 10 ticks, lasted " + ticks[0]);
            helper.assertTrue(!early[0], "no damage and no theft before the flare ends");
            helper.assertTrue(moved[0] < 0.5, "it holds still while it flares, moved " + moved[0]);
            helper.assertTrue(stung[0] && Math.abs(pig.getHealth() - (pig.getMaxHealth() - 2.0F)) < 0.01F,
                    "the sting follows and does 2, the pig has " + pig.getHealth());
            helper.assertTrue(jelly.heldEffect() != null && jelly.heldEffect().is(MobEffects.SPEED), "and takes the pig's Speed");
        });
    }

    /**
     * Failing the counter: a pig with Speed 300, Regeneration 900 and Poison 2000 is stung. It loses exactly 2 health
     * and 600 ticks of its Regeneration (the longest beneficial one; Poison is longer but harmful), keeping 300; its
     * Speed and Poison are untouched. The jelly has Regeneration for 600 ticks, remembers the pig, and glows.
     */
    private static void stingSiphons(GameTestHelper helper) {
        Siphonjelly jelly = still(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(6.6, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            give(pig, MobEffects.SPEED, 300, 0);
            give(pig, MobEffects.REGENERATION, 900, 0);
            give(pig, MobEffects.POISON, 2000, 0);
            float before = pig.getHealth();
            helper.assertTrue(jelly.sting(helper.getLevel(), pig), "the sting lands on a pig beside it");
            float lost = before - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 2.0F) < 0.01F, "the sting does 2, the pig lost " + lost);
            helper.assertTrue(left(pig, MobEffects.REGENERATION) == 300, "the pig keeps 300 of its 900 Regeneration, has " + left(pig, MobEffects.REGENERATION));
            helper.assertTrue(left(pig, MobEffects.SPEED) == 300, "its Speed is untouched, " + left(pig, MobEffects.SPEED));
            helper.assertTrue(left(pig, MobEffects.POISON) == 2000, "its Poison is untouched, " + left(pig, MobEffects.POISON));
            helper.assertTrue(left(jelly, MobEffects.REGENERATION) == 600, "the jelly has Regeneration for 600, has " + left(jelly, MobEffects.REGENERATION));
            helper.assertTrue(!jelly.hasEffect(MobEffects.POISON) && !jelly.hasEffect(MobEffects.SPEED), "and nothing else");
            helper.assertTrue(pig.getUUID().equals(jelly.heldFrom()), "it remembers who it took it from");
            helper.assertTrue(jelly.core().stage() == Siphonjelly.stageFor(MobEffects.REGENERATION.value().getColor()) && jelly.core().stage() > 0,
                    "it glows in Regeneration's colour, stage " + jelly.core().stage());
            helper.succeed();
        });
    }

    /**
     * The counter: kill it and the pig gets back exactly the ticks it had left, added to what it kept. Speed 1000 on
     * the pig: 600 go, 400 stay; 20 ticks later the jelly dies and the pig's Speed is what it had plus what the jelly
     * had.
     */
    private static void killGivesBack(GameTestHelper helper) {
        Siphonjelly jelly = still(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(6.6, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            give(pig, MobEffects.SPEED, 1000, 1);
            helper.assertTrue(jelly.siphon(helper.getLevel(), pig) != null, "it takes the pig's Speed");
            helper.assertTrue(left(pig, MobEffects.SPEED) == 400 && jelly.heldLeft() == 600, "600 taken, 400 kept: " + jelly.heldLeft() + " / " + left(pig, MobEffects.SPEED));
        });
        helper.runAtTickTime(22, () -> {
            int kept = left(pig, MobEffects.SPEED), held = jelly.heldLeft();
            helper.assertTrue(Math.abs(held - 580) <= 1 && Math.abs(kept - 380) <= 1, "20 ticks on: jelly " + held + ", pig " + kept);
            jelly.kill(helper.getLevel());
            helper.assertTrue(!jelly.isAlive(), "the jelly is dead");
            helper.assertTrue(left(pig, MobEffects.SPEED) == kept + held, "the pig has its " + kept + " plus the jelly's " + held + ", got " + left(pig, MobEffects.SPEED));
            helper.assertTrue(pig.getEffect(MobEffects.SPEED).getAmplifier() == 1, "at the level it had (Speed II)");
            helper.assertTrue(jelly.lastReturned() == held && pig.getUUID().equals(jelly.lastReturnedTo()), "the jelly gave back " + jelly.lastReturned());
            helper.succeed();
        });
    }

    /**
     * The other counter, fighting it without buffs: a sting on a pig with nothing to take starves it for 100 ticks, in
     * which it neither flares nor stings though the pig stays beside it; after that it flares again.
     */
    private static void starve(GameTestHelper helper) {
        Siphonjelly jelly = hunter(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(6.6, 1, 5.5));
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        jelly.setTarget(pig);
        int[] starved = {0};
        long[] ended = {-1}, again = {-1};
        float[] atStart = {-1.0F};
        boolean[] hurtWhileStarved = {false}, flaredWhileStarved = {false};
        helper.onEachTick(() -> {
            if (jelly.core().in(Step.STARVED)) {
                if (starved[0] == 0) atStart[0] = pig.getHealth();
                starved[0]++;
                hurtWhileStarved[0] |= pig.getHealth() < atStart[0];
            } else if (starved[0] > 0 && ended[0] < 0) {
                ended[0] = helper.getTick();
            }
            if (ended[0] >= 0 && again[0] < 0 && jelly.core().in(Step.TELL)) again[0] = helper.getTick();
            if (starved[0] > 0 && ended[0] < 0 && jelly.core().in(Step.TELL)) flaredWhileStarved[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(again[0] >= 0, "it flares again once it has eaten, phase " + jelly.phase() + ", starved " + starved[0]);
            helper.assertTrue(Math.abs(atStart[0] - 98.0F) < 0.01F, "the empty sting still did 2, the pig had " + atStart[0]);
            helper.assertTrue(Math.abs(starved[0] - 100) <= 1, "starved for 100 ticks, " + starved[0]);
            helper.assertTrue(!hurtWhileStarved[0] && !flaredWhileStarved[0], "no flare and no sting while starved");
            helper.assertTrue(jelly.held() == null, "it holds nothing");
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /**
     * At most 600 ticks: from Speed 1000 it takes 600 and the pig keeps 400; from Speed 500 it takes all 500 and the
     * pig has none left; from an endless Fire Resistance it takes 600 and the pig keeps the endless one.
     */
    private static void cap(GameTestHelper helper) {
        Siphonjelly a = still(helper, new Vec3(2.5, 2, 4.5)), b = still(helper, new Vec3(5.5, 2, 4.5)), c = still(helper, new Vec3(8.5, 2, 4.5));
        Pig pa = pig(helper, new Vec3(2.5, 1, 5.6)), pb = pig(helper, new Vec3(5.5, 1, 5.6)), pc = pig(helper, new Vec3(8.5, 1, 5.6));
        helper.runAtTickTime(2, () -> {
            give(pa, MobEffects.SPEED, 1000, 0);
            give(pb, MobEffects.SPEED, 500, 0);
            give(pc, MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0);
            MobEffectInstance ta = a.siphon(helper.getLevel(), pa), tb = b.siphon(helper.getLevel(), pb), tc = c.siphon(helper.getLevel(), pc);
            helper.assertTrue(ta != null && ta.getDuration() == 600 && left(pa, MobEffects.SPEED) == 400, "1000: 600 taken, 400 kept");
            helper.assertTrue(left(a, MobEffects.SPEED) == 600, "the jelly has 600, " + left(a, MobEffects.SPEED));
            helper.assertTrue(tb != null && tb.getDuration() == 500 && !pb.hasEffect(MobEffects.SPEED), "500: all of it taken, none kept");
            helper.assertTrue(left(b, MobEffects.SPEED) == 500, "the jelly has 500, " + left(b, MobEffects.SPEED));
            helper.assertTrue(tc != null && tc.getDuration() == 600 && left(c, MobEffects.FIRE_RESISTANCE) == 600, "endless: 600 taken");
            helper.assertTrue(pc.getEffect(MobEffects.FIRE_RESISTANCE) != null && pc.getEffect(MobEffects.FIRE_RESISTANCE).isInfiniteDuration(), "and the pig keeps its endless one");
            helper.succeed();
        });
    }

    /**
     * One at a time: holding Strength (40 ticks of it), it takes nothing from a second pig's Speed; the Strength runs out
     * after 40 ticks, its glow goes back to its own, and then it takes 600 of the Speed.
     */
    private static void oneAtATime(GameTestHelper helper) {
        Siphonjelly jelly = still(helper, new Vec3(5.5, 2, 5.5));
        Pig first = pig(helper, new Vec3(6.6, 1, 5.5)), second = pig(helper, new Vec3(4.4, 1, 5.5));
        int[] heldTicks = {0};
        helper.onEachTick(() -> {
            if (jelly.held() != null && jelly.heldEffect().is(MobEffects.STRENGTH)) heldTicks[0]++;
        });
        helper.runAtTickTime(2, () -> {
            give(first, MobEffects.STRENGTH, 40, 0);
            give(second, MobEffects.SPEED, 1000, 0);
            helper.assertTrue(jelly.siphon(helper.getLevel(), first) != null, "it takes the Strength");
            helper.assertTrue(jelly.siphon(helper.getLevel(), second) == null && left(second, MobEffects.SPEED) == 1000, "holding one, it takes no other");
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(jelly.held() == null && !jelly.hasEffect(MobEffects.STRENGTH), "the Strength ran out");
            helper.assertTrue(Math.abs(heldTicks[0] - 40) <= 1, "held 40 ticks, " + heldTicks[0]);
            helper.assertTrue(jelly.core().stage() == 0, "its glow is its own again, stage " + jelly.core().stage());
            MobEffectInstance took = jelly.siphon(helper.getLevel(), second);
            helper.assertTrue(took != null && took.getDuration() == 600 && jelly.heldEffect().is(MobEffects.SPEED), "now it takes 600 of the Speed");
            helper.succeed();
        });
    }

    /**
     * Who gets it back: a pig 31.5 blocks off gets its Speed back; a pig 33 blocks off doesn't, and nor does a pig that
     * died first. (The far pigs float straight above their jellies, out of every other arena, and leave with the test.)
     */
    private static void returnRange(GameTestHelper helper) {
        Siphonjelly near = still(helper, new Vec3(2.5, 2, 4.5)), far = still(helper, new Vec3(5.5, 2, 4.5)), orphan = still(helper, new Vec3(8.5, 2, 4.5));
        Pig pn = pig(helper, new Vec3(2.5, 1, 5.6)), pf = pig(helper, new Vec3(5.5, 1, 5.6)), po = pig(helper, new Vec3(8.5, 1, 5.6));
        TestPlayers.whenDone(helper, pn::discard);
        TestPlayers.whenDone(helper, pf::discard);
        int[] kept = {0, 0};
        helper.runAtTickTime(2, () -> {
            for (Pig p : List.of(pn, pf, po)) give(p, MobEffects.SPEED, 1000, 0);
            helper.assertTrue(near.siphon(helper.getLevel(), pn) != null && far.siphon(helper.getLevel(), pf) != null
                    && orphan.siphon(helper.getLevel(), po) != null, "all three take their pig's Speed");
            pn.snapTo(near.position().add(0, 31.5, 0));
            pf.snapTo(far.position().add(0, 33.0, 0));
            po.kill(helper.getLevel());
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(Math.abs(near.distanceTo(pn) - 31.5) < 0.01 && Math.abs(far.distanceTo(pf) - 33.0) < 0.01,
                    "31.5 and 33 blocks off: " + near.distanceTo(pn) + ", " + far.distanceTo(pf));
            kept[0] = left(pn, MobEffects.SPEED);
            kept[1] = left(pf, MobEffects.SPEED);
            int back = near.heldLeft();
            for (Siphonjelly j : List.of(near, far, orphan)) j.kill(helper.getLevel());
            helper.assertTrue(left(pn, MobEffects.SPEED) == kept[0] + back && near.lastReturned() == back, "31.5 blocks off: given back " + near.lastReturned());
            helper.assertTrue(left(pf, MobEffects.SPEED) == kept[1] && far.lastReturned() == 0, "33 blocks off: lost, " + far.lastReturned());
            helper.assertTrue(orphan.lastReturned() == 0, "a dead victim gets nothing, " + orphan.lastReturned());
            helper.succeed();
        });
    }

    /**
     * It has the use of what it holds, as anyone would: Regeneration II heals it (from 6 health, at least 2 more in 60
     * ticks), Speed II makes it drift 1.4 times as fast (1.0 without), and Resistance I takes a fifth off a blow (5 → 4).
     */
    private static void benefits(GameTestHelper helper) {
        Siphonjelly healer = still(helper, new Vec3(2.5, 2, 4.5)), runner = still(helper, new Vec3(5.5, 2, 4.5)), hard = still(helper, new Vec3(8.5, 2, 4.5));
        Pig ph = pig(helper, new Vec3(2.5, 1, 5.6)), pr = pig(helper, new Vec3(5.5, 1, 5.6)), pd = pig(helper, new Vec3(8.5, 1, 5.6));
        float[] from = {0.0F};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(Math.abs(runner.speedFactor() - 1.0) < 0.001, "unbuffed it drifts at its own speed, x" + runner.speedFactor());
            give(ph, MobEffects.REGENERATION, 900, 1);
            give(pr, MobEffects.SPEED, 900, 1);
            give(pd, MobEffects.RESISTANCE, 900, 0);
            healer.setHealth(6.0F);
            from[0] = healer.getHealth();
            helper.assertTrue(healer.siphon(helper.getLevel(), ph) != null && runner.siphon(helper.getLevel(), pr) != null
                    && hard.siphon(helper.getLevel(), pd) != null, "each takes its pig's effect");
            helper.assertTrue(Math.abs(runner.speedFactor() - 1.4) < 0.001, "with Speed II it drifts 1.4 times as fast, x" + runner.speedFactor());
            float before = hard.getHealth();
            hard.hurtServer(helper.getLevel(), hard.damageSources().mobAttack(pd), 5.0F);
            helper.assertTrue(Math.abs(before - hard.getHealth() - 4.0F) < 0.01F, "Resistance I: a blow of 5 takes 4, took " + (before - hard.getHealth()));
        });
        helper.runAtTickTime(63, () -> {
            helper.assertTrue(healer.getHealth() >= from[0] + 2.0F - 0.01F, "Regeneration II heals it, " + from[0] + " -> " + healer.getHealth());
            helper.succeed();
        });
    }

    /**
     * Its glow is the effect's colour: Speed (cyan) is stage 6; every beneficial effect in the game maps to one of the
     * twelve held colours, none of which lies in the gold band (30-58 degrees of hue, weak-point gold is 42), and the
     * motes it sheds are turned out of the band too. Strength's and Haste's gold-ish colours glow orange or lime.
     */
    private static void glow(GameTestHelper helper) {
        Siphonjelly jelly = still(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(6.6, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(jelly.core().stage() == 0, "holding nothing it shows its own glow, stage " + jelly.core().stage());
            give(pig, MobEffects.SPEED, 900, 0);
            jelly.siphon(helper.getLevel(), pig);
            helper.assertTrue(jelly.core().stage() == 6, "holding Speed it glows cyan, stage 6, got " + jelly.core().stage());
            helper.assertTrue(Math.abs(Siphonjelly.hue(0xFFD36B) - 42.0F) < 1.0F, "weak-point gold sits at 42 degrees");
            for (int held : Siphonjelly.HELD_COLORS) {
                float h = Siphonjelly.hue(held);
                helper.assertTrue(h < 30.0F || h >= 58.0F, "held colour " + Integer.toHexString(held) + " is off gold, hue " + h);
            }
            BuiltInRegistries.MOB_EFFECT.listElements().filter(e -> e.value().isBeneficial()).forEach(e -> {
                int color = e.value().getColor();
                int stage = Siphonjelly.stageFor(color);
                helper.assertTrue(stage >= 1 && stage <= 12, e.getRegisteredName() + " maps to a held colour, stage " + stage);
                float shown = Siphonjelly.hue(Siphonjelly.shownColor(color));
                boolean pale = (Siphonjelly.shownColor(color) & 0xFFFFFF) == (color & 0xFFFFFF) && stage == 12;
                helper.assertTrue(pale || shown < 30.0F || shown >= 58.0F, e.getRegisteredName() + "'s motes are off gold, hue " + shown);
            });
            for (Holder<MobEffect> gold : List.of(MobEffects.STRENGTH, MobEffects.HASTE)) {
                int stage = Siphonjelly.stageFor(gold.value().getColor());
                helper.assertTrue(stage == 2 || stage == 3, gold.getRegisteredName() + " glows orange or lime, stage " + stage);
            }
            helper.succeed();
        });
    }

    /**
     * The sting's reach: a pig that steps back during the flare, to 1.85 blocks from its body, is missed (it keeps its Speed and its
     * health); one still within 1.0 of its body at the end of the flare is stung.
     */
    private static void stepBack(GameTestHelper helper) {
        Siphonjelly a = still(helper, new Vec3(3.5, 2, 4.5)), b = still(helper, new Vec3(8.5, 2, 4.5));
        Pig away = pig(helper, new Vec3(3.5, 1, 5.6)), stays = pig(helper, new Vec3(8.5, 1, 5.6));
        helper.runAtTickTime(2, () -> {
            give(away, MobEffects.SPEED, 900, 0);
            give(stays, MobEffects.SPEED, 900, 0);
            helper.assertTrue(a.flare(away) && b.flare(stays), "both flare");
        });
        helper.runAtTickTime(6, () -> {
            away.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 7.2)));     // a gap of about 1.85 between the bodies
            stays.snapTo(helper.absoluteVec(new Vec3(8.5, 1, 6.2)));    // a gap of about 0.86
        });
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(a.gap(away) > 1.0 && b.gap(stays) <= 1.0, "gaps " + a.gap(away) + " and " + b.gap(stays));
            helper.assertTrue(away.getHealth() >= away.getMaxHealth() && left(away, MobEffects.SPEED) > 850 && a.held() == null,
                    "the pig that stepped back is missed");
            helper.assertTrue(stays.getHealth() < stays.getMaxHealth() && b.held() != null, "the pig within reach is stung and robbed");
            helper.succeed();
        });
    }

    /** A listener that cancels the "siphon" step: the sting still hurts, but the pig keeps its Speed. */
    private static void siphonCancel(GameTestHelper helper) {
        Siphonjelly jelly = still(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(6.6, 1, 5.5));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == jelly && e.step().equals("siphon")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> {
            give(pig, MobEffects.SPEED, 900, 0);
            helper.assertTrue(jelly.sting(helper.getLevel(), pig), "the sting lands");
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "and hurts");
            helper.assertTrue(left(pig, MobEffects.SPEED) == 900 && jelly.held() == null, "but takes nothing");
            helper.succeed();
        });
    }

    /** Health 16, attack 2, armour 0, a 0.8 x 1.0 hitbox; a monster that flies. */
    private static void stats(GameTestHelper helper) {
        Siphonjelly jelly = still(helper, new Vec3(5.5, 2, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(jelly.getMaxHealth() - 16.0F) < 0.01F && Math.abs(jelly.getHealth() - 16.0F) < 0.01F,
                    "health 16, got " + jelly.getHealth() + "/" + jelly.getMaxHealth());
            helper.assertTrue(Math.abs(jelly.getAttributeValue(Attributes.ATTACK_DAMAGE) - 2.0) < 0.01, "attack 2");
            helper.assertTrue(Math.abs(jelly.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(jelly.getBbWidth() - 0.8F) < 0.02F && Math.abs(jelly.getBbHeight() - 1.0F) < 0.02F,
                    "hitbox 0.8 x 1.0, got " + jelly.getBbWidth() + " x " + jelly.getBbHeight());
            helper.assertTrue(jelly.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(jelly.isNoGravity(), "it flies");
            helper.succeed();
        });
    }

    /**
     * Its home: the warped and crimson forests and nowhere else, weight 10, groups of 1 to 2; both forests are
     * share-capped, so its biome-list spawns leave them out and the Teeming spawner places it there. The Crimson Jelly
     * is a look.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(TeemingSpawns.tag("siphonjelly"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.WARPED_FOREST)), "its home holds the warped forest");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.CRIMSON_FOREST)), "its home holds the crimson forest");
        for (var other : List.of(Biomes.SOUL_SAND_VALLEY, Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS, Biomes.PLAINS)) {
            helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("siphonjelly");
        helper.assertTrue(row.dim() == RosterStats.Dim.NETHER, "a Nether mob");
        helper.assertTrue(ModMobs.SIPHONJELLY.get().getCategory() == MobCategory.MONSTER, "a monster");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/siphonjelly")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SIPHONJELLY.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 10, "spawn weight 10, got " + entry.weight());
        var forests = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).biomes();
        var biomeLookup = access.lookupOrThrow(Registries.BIOME);
        helper.assertTrue(!forests.contains(biomeLookup.getOrThrow(Biomes.WARPED_FOREST)) && !forests.contains(biomeLookup.getOrThrow(Biomes.CRIMSON_FOREST)),
                "its biome-list spawns leave out the share-capped forests, where the Teeming spawner places it");
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "groups of 1 to 2, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("siphonjelly");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("crimson_jelly")), "the Crimson Jelly is a look: " + looks);
        helper.succeed();
    }
}
