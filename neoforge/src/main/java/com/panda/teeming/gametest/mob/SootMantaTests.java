package com.panda.teeming.gametest.mob;

import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.SootManta;
import com.panda.teeming.mob.nether.SootManta.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner.Rule;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Soot Manta: with its target close it ripples its wings for 20 ticks, hanging still and harming nothing, then sheds a
 * soot cloud where it hangs: a ball of radius 3 that lasts 100 ticks. Anyone inside gets Blindness and Darkness for 60
 * ticks, topped up while they stay in; it isn't touched by its own soot. Between clouds it bites for its attack (4),
 * and the next cloud comes no sooner than 140 ticks after the last. Below half its health (13 of 26) it flees from its
 * attacker and stops biting and shedding.
 *
 * <p>Pigs stand in as targets and victims where numbers must be exact. Hunting mantas get a follow range of 1 so they
 * never notice a survival player in a neighbouring arena, and leave with the test, wherever they flew.
 */
public final class SootMantaTests {
    private SootMantaTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("soot_manta_tell", SootMantaTests::tell);
        out.accept("soot_manta_counter", SootMantaTests::outsideTheCloud);
        out.accept("soot_manta_failure", SootMantaTests::rippleSheds);
        out.accept("soot_manta_chase_off", SootMantaTests::chaseOff);
        out.accept("soot_manta_flee_threshold", SootMantaTests::fleeThreshold);
        out.accept("soot_manta_bite", SootMantaTests::bite);
        out.accept("soot_manta_cloud_length", SootMantaTests::cloudLength);
        TeemingGameTests.timeLimit("soot_manta_cloud_length", 140);
        out.accept("soot_manta_effect_length", SootMantaTests::effectLength);
        out.accept("soot_manta_refresh", SootMantaTests::refresh);
        out.accept("soot_manta_cooldown", SootMantaTests::cooldown);
        TeemingGameTests.timeLimit("soot_manta_cooldown", 260);
        out.accept("soot_manta_cloud_cancel", SootMantaTests::cloudCancel);
        out.accept("soot_manta_stats", SootMantaTests::stats);
        out.accept("soot_manta_home", SootMantaTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A manta with no AI at {@code at}: it hangs where it is put, and sheds when a test tells it to. */
    private static SootManta still(GameTestHelper helper, Vec3 at) {
        SootManta manta = helper.spawn(ModMobs.SOOT_MANTA.get(), at);
        manta.setNoAi(true);
        return manta;
    }

    /** A hunting manta, blind to anything beyond 1 block that it isn't handed, removed when the test ends. */
    private static SootManta hunter(GameTestHelper helper, Vec3 at) {
        SootManta manta = helper.spawn(ModMobs.SOOT_MANTA.get(), at);
        manta.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, manta::discard);
        return manta;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    private static boolean sooted(LivingEntity e) {
        return e.hasEffect(MobEffects.BLINDNESS) && e.hasEffect(MobEffects.DARKNESS);
    }

    private static boolean sootedAtAll(LivingEntity e) {
        return e.hasEffect(MobEffects.BLINDNESS) || e.hasEffect(MobEffects.DARKNESS);
    }

    private static int left(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance has = e.getEffect(effect);
        return has == null ? 0 : has.getDuration();
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A pig 1.1 blocks from its middle: it ripples at once, for 20 ticks, hanging still; no cloud and no soot on the
     * pig until the ripple ends, and then the shed follows.
     */
    private static void tell(GameTestHelper helper) {
        SootManta manta = hunter(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(7.0, 1, 5.5));
        manta.setTarget(pig);
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] early = {false}, shed = {false}, hurt = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            shed[0] |= manta.core().in(Step.SHED);
            if (!manta.core().in(Step.TELL)) return;
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = manta.position();
            }
            ticks[0]++;
            early[0] |= !manta.clouds().isEmpty() || sootedAtAll(pig);
            hurt[0] |= pig.getHealth() < pig.getMaxHealth();
            moved[0] = Math.max(moved[0], manta.position().distanceTo(start[0]));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && !manta.core().in(Step.TELL), "the ripple runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(first[0] <= 3, "with its target this close it ripples at once, started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 20) <= 1, "the ripple lasts 20 ticks, lasted " + ticks[0]);
            helper.assertTrue(!early[0], "no cloud and no soot before the ripple ends");
            helper.assertTrue(!hurt[0], "the ripple hurts nothing");
            helper.assertTrue(moved[0] < 0.5, "it hangs still while it ripples, moved " + moved[0]);
            helper.assertTrue(shed[0] && manta.clouds().size() == 1, "the shed follows with one cloud, " + manta.clouds().size());
        });
    }

    /**
     * Failing the counter: the pig stays by it through the ripple and is inside the cloud: Blindness and Darkness, each
     * with at most 60 ticks left. The cloud is where the manta hung, and its own soot leaves it untouched.
     */
    private static void rippleSheds(GameTestHelper helper) {
        SootManta manta = hunter(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(7.0, 1, 5.5));
        manta.setTarget(pig);
        Vec3[] hung = {null};
        helper.onEachTick(() -> {
            if (manta.core().in(Step.TELL)) hung[0] = manta.position().add(0, manta.getBbHeight() * 0.5, 0);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(manta.clouds().size() == 1, "the ripple ends in one cloud, " + manta.clouds().size());
            helper.assertTrue(hung[0] != null && manta.clouds().getFirst().distanceTo(hung[0]) < 0.6,
                    "the cloud is where it hung, " + manta.clouds().getFirst() + " vs " + hung[0]);
            helper.assertTrue(sooted(pig), "the pig inside is blinded and darkened");
            int blind = left(pig, MobEffects.BLINDNESS), dark = left(pig, MobEffects.DARKNESS);
            helper.assertTrue(blind > 40 && blind <= 60, "Blindness for 60 ticks, " + blind + " left");
            helper.assertTrue(dark > 40 && dark <= 60, "Darkness for 60 ticks, " + dark + " left");
            helper.assertTrue(!sootedAtAll(manta), "its own soot doesn't touch it");
        });
    }

    /**
     * The counter: stay outside the cloud. A pig whose near side is 2.9 blocks from its middle is sooted, and so is a
     * survival player 1.9 blocks away; a pig 3.2 blocks out never is.
     */
    private static void outsideTheCloud(GameTestHelper helper) {
        SootManta manta = still(helper, new Vec3(5.5, 1, 5.5));         // its middle: 5.5, 1.3, 5.5
        Pig in = pig(helper, new Vec3(5.5 + 2.9 + 0.45, 1, 5.5));        // a pig is 0.9 wide
        Pig out = pig(helper, new Vec3(5.5 - 3.2 - 0.45, 1, 5.5));
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 1, 7.7));   // 0.6 wide: its side 1.9 away
        boolean[] reached = {false};
        helper.onEachTick(() -> reached[0] |= sootedAtAll(out));
        helper.runAtTickTime(2, () -> helper.assertTrue(manta.shed(helper.getLevel()) != null, "the shed makes a cloud"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(sooted(in), "2.9 blocks out is inside the cloud");
            helper.assertTrue(sooted(player), "a player 1.9 blocks out is blinded and darkened");
            helper.assertTrue(!reached[0], "3.2 blocks out is outside the cloud");
            helper.succeed();
        });
    }

    // ---- fleeing -----------------------------------------------------------------------------------------------------

    /**
     * Chasing it off: at 13 health a hit from the pig takes it below half, and it flees from the pig: at least 2.5
     * blocks further away, its target dropped, without biting or shedding. The arena is too small for a long run, so it
     * takes a short one.
     */
    private static void chaseOff(GameTestHelper helper) {
        SootManta manta = hunter(helper, new Vec3(6.5, 3, 5.5));
        Pig pig = pig(helper, new Vec3(3.5, 1, 5.5));
        double[] before = {0.0};
        boolean[] struck = {false};
        helper.runAtTickTime(3, () -> {
            manta.setHealth(13.0F);
            before[0] = manta.distanceTo(pig);
            struck[0] = manta.hurtServer(helper.getLevel(), manta.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(struck[0] && manta.core().in(Step.FLEE), "below half it flees, phase " + manta.phase() + ", health " + manta.getHealth());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0], "the hit landed");
            helper.assertTrue(manta.core().in(Step.FLEE), "still fleeing, phase " + manta.phase());
            double gained = manta.distanceTo(pig) - before[0];
            helper.assertTrue(gained >= 2.5, "it runs from the pig, " + gained + " blocks so far");
            helper.assertTrue(manta.getTarget() == null, "it dropped its target");
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth() && manta.clouds().isEmpty(), "no bites and no cloud while it flees");
        });
    }

    /** "Below half": 13 of 26 left is not below, and it keeps fighting; 12.5 is, and it flees. */
    private static void fleeThreshold(GameTestHelper helper) {
        SootManta manta = hunter(helper, new Vec3(5.5, 3, 5.5));
        Pig pig = pig(helper, new Vec3(2.5, 1, 2.5));
        manta.setCooldown(1000);
        helper.runAtTickTime(3, () -> {
            manta.setHealth(14.0F);
            manta.hurtServer(helper.getLevel(), manta.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(Math.abs(manta.getHealth() - 13.0F) < 0.01F, "at 13, got " + manta.getHealth());
            helper.assertTrue(!manta.core().in(Step.FLEE) && !manta.timid(), "13 of 26 is half, not below: no flight, phase " + manta.phase());
        });
        helper.runAtTickTime(22, () -> {
            manta.hurtServer(helper.getLevel(), manta.damageSources().mobAttack(pig), 0.5F);
            helper.assertTrue(Math.abs(manta.getHealth() - 12.5F) < 0.01F, "at 12.5, got " + manta.getHealth());
            helper.assertTrue(manta.core().in(Step.FLEE), "12.5 is below half: it flees, phase " + manta.phase());
            helper.succeed();
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** Between clouds it swoops in and bites for its attack: the pig loses exactly 4. */
    private static void bite(GameTestHelper helper) {
        SootManta manta = hunter(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(8.5, 1, 5.5));
        manta.setCooldown(1000);                                     // no cloud: only the bite
        manta.setTarget(pig);
        float before = pig.getHealth();
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (lost[0] < 0 && pig.getHealth() < before) lost[0] = before - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0, "it bit the pig");
            helper.assertTrue(Math.abs(lost[0] - 4.0F) < 0.01F, "the bite does 4, the pig lost " + lost[0]);
            helper.assertTrue(manta.bites() >= 1, "it counts its bite");
        });
    }

    /** The cloud lasts 100 ticks; a pig put in its middle just after it clears is not touched. */
    private static void cloudLength(GameTestHelper helper) {
        SootManta manta = still(helper, new Vec3(5.5, 1, 5.5));
        int[] alive = {0};
        long[] gone = {-1};
        boolean[] started = {false};
        Pig[] late = {null};
        helper.onEachTick(() -> {
            if (!manta.clouds().isEmpty()) {
                started[0] = true;
                alive[0]++;
            } else if (started[0] && gone[0] < 0) {
                gone[0] = helper.getTick();
            }
            if (gone[0] >= 0 && late[0] == null && helper.getTick() >= gone[0] + 2) late[0] = pig(helper, new Vec3(5.5, 1, 6.0));
        });
        helper.runAtTickTime(2, () -> manta.shed(helper.getLevel()));
        helper.succeedWhen(() -> {
            helper.assertTrue(gone[0] >= 0, "the cloud clears, alive " + alive[0] + " ticks so far");
            helper.assertTrue(Math.abs(alive[0] - 100) <= 1, "the cloud lasts 100 ticks, lasted " + alive[0]);
            helper.assertTrue(late[0] != null && helper.getTick() >= gone[0] + 6, "a pig waits in the middle a few ticks");
            helper.assertTrue(!sootedAtAll(late[0]), "a cleared cloud touches no one");
        });
    }

    /** Blindness and Darkness last 60 ticks: a pig taken out of the cloud the tick it is sooted keeps both 60 ticks. */
    private static void effectLength(GameTestHelper helper) {
        SootManta manta = still(helper, new Vec3(5.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(7.0, 1, 5.5));
        boolean[] out = {false};
        int[] blind = {0}, dark = {0};
        helper.onEachTick(() -> {
            if (!out[0] && sooted(pig)) {
                out[0] = true;
                pig.snapTo(helper.absoluteVec(new Vec3(10.5, 1, 5.5)));   // its near side 4.55 from the cloud's middle
            }
            if (!out[0]) return;
            if (pig.hasEffect(MobEffects.BLINDNESS)) blind[0]++;
            if (pig.hasEffect(MobEffects.DARKNESS)) dark[0]++;
        });
        helper.runAtTickTime(2, () -> manta.shed(helper.getLevel()));
        helper.succeedWhen(() -> {
            helper.assertTrue(out[0] && !sootedAtAll(pig), "sooted, then out and clear, " + blind[0] + " ticks so far");
            helper.assertTrue(Math.abs(blind[0] - 60) <= 1, "Blindness lasts 60 ticks, lasted " + blind[0]);
            helper.assertTrue(Math.abs(dark[0] - 60) <= 1, "Darkness lasts 60 ticks, lasted " + dark[0]);
        });
    }

    /** Staying in the cloud keeps them topped up: 85 ticks in, far past 60, both still have at least 45 left. */
    private static void refresh(GameTestHelper helper) {
        SootManta manta = still(helper, new Vec3(5.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(7.0, 1, 5.5));
        boolean[] lapsed = {false};
        long[] first = {-1};
        helper.onEachTick(() -> {
            if (first[0] < 0 && sooted(pig)) first[0] = helper.getTick();
            if (first[0] >= 0 && !sooted(pig)) lapsed[0] = true;
        });
        helper.runAtTickTime(2, () -> manta.shed(helper.getLevel()));
        helper.runAtTickTime(2 + 85, () -> {
            helper.assertTrue(first[0] >= 0 && !lapsed[0], "the soot never lapses while it stays in");
            helper.assertTrue(left(pig, MobEffects.BLINDNESS) >= 45 && left(pig, MobEffects.DARKNESS) >= 45,
                    "topped up: " + left(pig, MobEffects.BLINDNESS) + " and " + left(pig, MobEffects.DARKNESS) + " left");
            helper.succeed();
        });
    }

    /** The next ripple comes at the soonest 140 ticks after the last shed, though the pig stays in reach. */
    private static void cooldown(GameTestHelper helper) {
        SootManta manta = hunter(helper, new Vec3(5.5, 2, 5.5));
        Pig pig = pig(helper, new Vec3(7.0, 1, 5.5));
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        manta.setTarget(pig);
        long[] shedAt = {-1}, again = {-1};
        int[] most = {0};
        boolean[] shedOver = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (shedAt[0] < 0 && !manta.clouds().isEmpty()) shedAt[0] = now;
            if (shedAt[0] >= 0) most[0] = Math.max(most[0], manta.cooldown());
            if (shedAt[0] >= 0 && !manta.core().in(Step.SHED) && !manta.core().in(Step.TELL)) shedOver[0] = true;
            if (shedOver[0] && again[0] < 0 && manta.core().in(Step.TELL)) again[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(again[0] >= 0, "it ripples again, phase " + manta.phase() + ", shed at " + shedAt[0]);
            long gap = again[0] - shedAt[0];
            helper.assertTrue(gap >= 139, "no ripple sooner than 140 ticks after the shed, saw " + gap);
            helper.assertTrue(most[0] >= 139 && most[0] <= 140, "the wait is 140 ticks, saw " + most[0]);
        });
    }

    /** A listener that cancels the "soot_cloud" step stops the cloud: no soot on a pig beside it. */
    private static void cloudCancel(GameTestHelper helper) {
        SootManta manta = still(helper, new Vec3(5.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(7.0, 1, 5.5));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == manta && e.step().equals("soot_cloud")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> helper.assertTrue(manta.shed(helper.getLevel()) == null, "a cancelled shed makes no cloud"));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(manta.clouds().isEmpty(), "no cloud in the air");
            helper.assertTrue(!sootedAtAll(pig), "no soot on the pig");
            helper.succeed();
        });
    }

    /** Health 26, attack 4, armour 0, a 2.0 x 0.6 hitbox; a monster that flies and doesn't burn (it lives over lava). */
    private static void stats(GameTestHelper helper) {
        SootManta manta = still(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(manta.getMaxHealth() - 26.0F) < 0.01F && Math.abs(manta.getHealth() - 26.0F) < 0.01F,
                    "health 26, got " + manta.getHealth() + "/" + manta.getMaxHealth());
            helper.assertTrue(Math.abs(manta.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4");
            helper.assertTrue(Math.abs(manta.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(manta.getBbWidth() - 2.0F) < 0.02F && Math.abs(manta.getBbHeight() - 0.6F) < 0.02F,
                    "hitbox 2.0 x 0.6, got " + manta.getBbWidth() + " x " + manta.getBbHeight());
            helper.assertTrue(manta.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(manta.isNoGravity(), "it flies");
            helper.assertTrue(manta.getType().fireImmune(), "fire and lava don't hurt it");
            helper.succeed();
        });
    }

    /**
     * Its home: the Teeming spawner's rule for the air over lava seas in the Nether, weight 6, one at a time; no biome
     * list. The rule takes open air over lava and refuses open air over the floor.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("soot_manta");
        helper.assertTrue(row.spawner().equals("lava_sea_air"), "placed by the lava-sea-air rule, got " + row.spawner());
        Rule rule = Rule.of(row.spawner());
        helper.assertTrue(rule == Rule.LAVA_SEA_AIR && rule.dimension == Level.NETHER, "a Nether rule, got " + rule);
        helper.assertTrue(row.weight() == 6, "weight 6, got " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "one at a time, got " + row.groupMin() + " to " + row.groupMax());
        helper.assertTrue(!row.biomeSpawns(), "no biome list: only the spawner places it");
        helper.assertTrue(TeemingSpawns.type(row).isPresent(), "registered, so the spawner can pick it");
        BlockPos over = new BlockPos(3, 4, 3);
        int y = helper.absolutePos(over).getY();
        helper.assertTrue(y > helper.getLevel().getSeaLevel() + 1, "the arena sits above the sea level the rule asks for, y " + y);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.LAVA);
        helper.assertTrue(rule.fits(helper.getLevel(), helper.absolutePos(over)), "open air three blocks over lava fits");
        helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))), "open air over the floor doesn't");
        helper.succeed();
    }
}
