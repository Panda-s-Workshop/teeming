package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.AstralAugur;
import com.panda.teeming.mob.end.AstralAugur.GroundMarks;
import com.panda.teeming.mob.end.AstralAugur.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Astral Augur: every 100 ticks, with a target within 16 blocks in its sight, it channels for 24 ticks (the tell, which
 * hurts nothing) while three circles of radius 1.5 fill in on the ground, one under the target and two behind it. Then
 * each circle strikes: 20 to everything inside, once however many circles it stands in, with no knockback. A hit during
 * the channel breaks it: the circles fade, nothing strikes, it flinches for 20 ticks and a halo mote cracks off and drops
 * as a star mote; it casts one circle fewer for each mote lost, down to its last.
 *
 * <p>The augur stands at one side of the arena with its goals cleared (it reads its target straight from the test, and a
 * follow range of 1 keeps it from picking up survivors in other arenas), and a still pig stands in as the target 5
 * blocks off, so all three circles lie inside the arena. The 16-block range is measured straight up, through the opened
 * roof. The ground-mark tests at the end drive the circles' piece on its own, without an augur.
 */
public final class AstralAugurTests {
    private AstralAugurTests() {}

    /** The augur at one side, the target 5 blocks from it across the middle. */
    private static final Vec3 AT = new Vec3(3.5, 1, 6.5);
    private static final Vec3 MARK = new Vec3(8.5, 1, 6.5);
    /** The augur for the range test, in the middle of the arena under the opened roof. */
    private static final Vec3 MIDDLE = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("astral_augur_tell", AstralAugurTests::tell);
        out.accept("astral_augur_dies_mid_tell", AstralAugurTests::diesMidTell);
        out.accept("astral_augur_counter", AstralAugurTests::counter);
        out.accept("astral_augur_failure", AstralAugurTests::failure);
        out.accept("astral_augur_circles", AstralAugurTests::circleRadius);
        out.accept("astral_augur_damage", AstralAugurTests::damage);
        out.accept("astral_augur_layout", AstralAugurTests::layout);
        out.accept("astral_augur_no_knockback", AstralAugurTests::noKnockback);
        out.accept("astral_augur_every", AstralAugurTests::every);
        TeemingGameTests.timeLimit("astral_augur_every", 180);
        out.accept("astral_augur_range", AstralAugurTests::range);
        out.accept("astral_augur_sight", AstralAugurTests::sight);
        out.accept("astral_augur_flinch", AstralAugurTests::flinch);
        out.accept("astral_augur_last_mote", AstralAugurTests::lastMote);
        TeemingGameTests.timeLimit("astral_augur_last_mote", 200);
        out.accept("astral_augur_swipe", AstralAugurTests::swipe);
        out.accept("astral_augur_strike_cancel", AstralAugurTests::strikeCancel);
        out.accept("astral_augur_stats", AstralAugurTests::stats);
        out.accept("astral_augur_home", AstralAugurTests::home);
        out.accept("astral_augur_marks_fill", AstralAugurTests::marksFill);
        out.accept("astral_augur_marks_ground", AstralAugurTests::marksGround);
        out.accept("astral_augur_marks_strike", AstralAugurTests::marksStrike);
        out.accept("astral_augur_marks_cancel", AstralAugurTests::marksCancel);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    private static AstralAugur augur(GameTestHelper helper, Vec3 at) {
        AstralAugur a = helper.spawn(ModMobs.ASTRAL_AUGUR.get(), at);
        a.removeFreeWill();      // no walking or backing off; its own phase machine still runs
        a.setCooldown(0);
        a.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        // the base look now, so its first tick doesn't pick one and refill its health over a test's setHealth
        a.core().setLook(LookDef.BASE, true);
        return a;
    }

    /** A still pig with 40 health. */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        return pig;
    }

    /** The augur at its side and the pig 5 blocks off as its target. */
    private static AstralAugur onPig(GameTestHelper helper, Pig[] out) {
        AstralAugur a = augur(helper, AT);
        Pig pig = pig(helper, MARK);
        a.setTarget(pig);
        out[0] = pig;
        return a;
    }

    private static boolean near(float value, float expected) {
        return Math.abs(value - expected) < 0.01F;
    }

    /** Star motes lying in the arena. */
    private static int starMotes(GameTestHelper helper) {
        var item = BuiltInRegistries.ITEM.getValue(TeemingMod.id("star_mote"));
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds())) {
            if (e.getItem().is(item)) n += e.getItem().getCount();
        }
        return n;
    }

    /** Clears the barrier roof over the arena and checks the sky above its middle is open well past 16 blocks. */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        for (int y = 2; y <= 24; y++) {
            BlockPos pos = new BlockPos(5, y, 5);
            if (!helper.getBlockState(pos).isAir()) helper.fail("the sky over the arena is shut at y " + y + " by " + helper.getBlockState(pos));
        }
    }

    /** A stone wall across the arena between the augur and the target, four high. */
    private static void wall(GameTestHelper helper, boolean up) {
        for (int z = 0; z < 12; z++) {
            for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(6, y, z), up ? Blocks.STONE : Blocks.AIR);
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A pig 5 blocks away in sight sets off the channel at once. It lasts 24 ticks with three circles down, the augur
     * holds still, and nothing is hurt yet; then the circles strike.
     */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        Vec3 start = a.position();
        int[] ticks = {0}, circles = {0};
        long[] first = {-1};
        boolean[] hurt = {false};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (!a.core().in(Step.TELL)) return;
            if (first[0] < 0) first[0] = helper.getTick();
            ticks[0]++;
            circles[0] = Math.max(circles[0], a.marks().size());
            hurt[0] |= pig[0].getHealth() < 40.0F;
            moved[0] = Math.max(moved[0], a.position().subtract(start).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && a.strikes() == 1, "the channel runs and the strike follows, " + ticks[0] + " tell ticks, phase " + a.phase());
            helper.assertTrue(first[0] <= 3, "a target in range and sight sets it off at once, it started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 24) <= 1, "the channel lasts 24 ticks, lasted " + ticks[0]);
            helper.assertTrue(circles[0] == 3, "three circles are down during the channel, saw " + circles[0]);
            helper.assertTrue(!hurt[0], "the pig loses nothing during the channel");
            helper.assertTrue(moved[0] < 0.1, "it holds still while it channels, moved " + moved[0]);
        });
    }

    /**
     * Killed 8 ticks into its channel, it strikes nothing: the pig in the middle circle is unhurt well past the moment
     * the strike would have come, through the whole death animation.
     */
    private static void diesMidTell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        long[] first = {-1}, killed = {-1};
        helper.onEachTick(() -> {
            if (first[0] < 0 && a.core().in(Step.TELL)) first[0] = helper.getTick();
            if (first[0] >= 0 && killed[0] < 0 && helper.getTick() == first[0] + 8) {
                a.kill(helper.getLevel());
                killed[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getTick() >= killed[0] + 40, "killed mid-channel, waiting out the strike");
            helper.assertTrue(a.strikes() == 0, "no strike from a dead augur, saw " + a.strikes());
            helper.assertTrue(pig[0].getHealth() >= 40.0F, "the pig loses nothing, has " + pig[0].getHealth());
        });
    }

    /**
     * The counter: a blow from another mob 8 ticks into the channel breaks it. The circles are gone at once, nothing
     * strikes (the pig, standing in the middle circle, is unhurt well past the moment of the strike), one halo mote
     * cracks off (3 circles become 2) and a star mote drops in the arena.
     */
    private static void counter(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        Pig hitter = pig(helper, new Vec3(2.5, 1, 4.5));
        int motesBefore = starMotes(helper);
        long[] start = {-1}, broke = {-1};
        boolean[] cleared = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (start[0] < 0 && a.core().in(Step.TELL)) start[0] = now;
            if (start[0] >= 0 && broke[0] < 0 && now == start[0] + 8) {
                a.hurtServer(helper.getLevel(), a.damageSources().mobAttack(hitter), 1.0F);
                broke[0] = now;
                cleared[0] = a.core().in(Step.BROKEN) && a.marks().isEmpty();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(broke[0] >= 0 && helper.getTick() >= start[0] + 34, "the channel was hit and the strike's moment passed, phase " + a.phase());
            helper.assertTrue(cleared[0], "the hit broke the channel and the circles went at once");
            helper.assertTrue(a.strikes() == 0 && a.breaks() == 1, "nothing struck, strikes " + a.strikes() + ", breaks " + a.breaks());
            helper.assertTrue(near(pig[0].getHealth(), 40.0F), "the pig in the middle circle is unhurt, health " + pig[0].getHealth());
            helper.assertTrue(a.motesLost() == 1 && a.circles() == 2, "a mote cracked off: 3 circles become 2, now " + a.circles());
            helper.assertTrue(starMotes(helper) - motesBefore == 1, "one star mote dropped, found " + (starMotes(helper) - motesBefore));
        });
    }

    /**
     * Failing the counter: a survival player stays in the circle under them. The strike hits them for 20 (mob damage to
     * players scales with difficulty: 11 on Easy, 30 on Hard; the player has 40 health) as the augur's own attack, so armour counts, and
     * doesn't move them.
     */
    private static void failure(GameTestHelper helper) {
        AstralAugur a = augur(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, MARK);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);         // the blow leaves it standing on any difficulty
        p.setHealth(40.0F);
        a.setTarget(p);
        float before = p.getHealth();
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 11.0F;
            case HARD -> 30.0F;
            default -> 20.0F;
        };
        Vec3 stood = p.position();
        long[] struckAt = {-1};
        double[] shove = {-1.0};
        DamageSource[] last = {null};
        helper.onEachTick(() -> {
            if (struckAt[0] < 0 && a.strikes() == 1) {
                struckAt[0] = helper.getTick();
                shove[0] = p.getDeltaMovement().horizontalDistance();
                last[0] = p.getLastDamageSource();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struckAt[0] >= 0, "the circles struck, phase " + a.phase());
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the strike does " + expected + " here, the player lost " + lost);
            helper.assertTrue(last[0] != null && last[0].is(DamageTypes.MOB_ATTACK) && last[0].getEntity() == a,
                    "the augur's attack, got " + last[0]);
            helper.assertTrue(!last[0].is(DamageTypeTags.BYPASSES_ARMOR), "which armour reduces");
            helper.assertTrue(shove[0] >= 0.0 && shove[0] < 0.01, "no knockback, horizontal motion " + shove[0]);
            helper.assertTrue(p.position().distanceTo(stood) < 0.01, "the player stands where they stood");
            helper.assertTrue(a.lastStruck().contains(p), "the strike hit the player");
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /**
     * Radius 1.5: of two pigs placed by the middle circle as it opens, toward the augur (where no other circle lies),
     * the one 1.4 blocks from its centre is struck for 20 and the one 1.6 blocks out is untouched.
     */
    private static void circleRadius(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        Pig[] in = {null}, out = {null};
        double[] radii = {0.0, 0.0};
        helper.onEachTick(() -> {
            if (in[0] != null || !a.core().in(Step.TELL) || a.marks().isEmpty()) return;
            GroundMarks.Mark middle = a.marks().getFirst();
            radii[0] = a.marks().stream().mapToDouble(GroundMarks.Mark::radius).min().orElse(0);
            radii[1] = a.marks().stream().mapToDouble(GroundMarks.Mark::radius).max().orElse(0);
            Vec3 toward = a.position().subtract(middle.centre()).horizontal().normalize();
            in[0] = helper.spawn(EntityTypes.PIG, helper.relativeVec(middle.centre().add(toward.scale(1.4))));
            out[0] = helper.spawn(EntityTypes.PIG, helper.relativeVec(middle.centre().add(toward.scale(1.6))));
            for (Pig p : List.of(in[0], out[0])) {
                p.setNoAi(true);
                p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
                p.setHealth(40.0F);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(in[0] != null && a.strikes() == 1, "the circles struck, phase " + a.phase());
            helper.assertTrue(Math.abs(radii[0] - 1.5) < 1.0E-6 && Math.abs(radii[1] - 1.5) < 1.0E-6, "every circle has radius 1.5, "
                    + radii[0] + ".." + radii[1]);
            helper.assertTrue(near(in[0].getHealth(), 20.0F), "1.4 blocks from the centre is inside: 40 -> 20, got " + in[0].getHealth());
            helper.assertTrue(near(out[0].getHealth(), 40.0F), "1.6 blocks out is outside: unhurt, got " + out[0].getHealth());
        });
    }

    /**
     * 20 damage each: a pig at the middle of each of the three circles loses exactly 20. A fourth pig where the middle
     * circle and a flank overlap is in two circles and still loses 20, once.
     */
    private static void damage(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        List<Pig> flanks = new ArrayList<>();
        Pig[] both = {null};
        helper.onEachTick(() -> {
            if (both[0] != null || !a.core().in(Step.TELL) || a.marks().size() < 3) return;
            List<GroundMarks.Mark> marks = a.marks();
            for (int i = 1; i < 3; i++) flanks.add(pig(helper, helper.relativeVec(marks.get(i).centre())));
            Vec3 between = marks.get(0).centre().add(marks.get(1).centre()).scale(0.5);
            both[0] = pig(helper, helper.relativeVec(between));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(both[0] != null && a.strikes() == 1, "the circles struck, phase " + a.phase());
            helper.assertTrue(near(pig[0].getHealth(), 20.0F), "the pig in the middle circle: 40 -> 20, got " + pig[0].getHealth());
            for (Pig f : flanks) helper.assertTrue(near(f.getHealth(), 20.0F), "a pig in a flank circle: 40 -> 20, got " + f.getHealth());
            helper.assertTrue(near(both[0].getHealth(), 20.0F), "in two circles at once it still loses 20, got " + both[0].getHealth());
            long times = a.lastStruck().stream().filter(e -> e == both[0]).count();
            helper.assertTrue(times == 1, "and it was struck once, " + times + " times");
            helper.assertTrue(a.lastStruck().size() == 4, "four pigs struck, " + a.lastStruck().size());
        });
    }

    /**
     * On and near the target: the first circle is centred under it, on the ground; the other two lie 2.6 blocks from it,
     * 50 degrees either side of straight away from the augur.
     */
    private static void layout(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        double floor = helper.absoluteVec(new Vec3(0, 1, 0)).y;
        helper.succeedWhen(() -> {
            helper.assertTrue(a.core().in(Step.TELL) && a.marks().size() == 3, "three circles are down, phase " + a.phase());
            List<GroundMarks.Mark> marks = a.marks();
            Vec3 c = marks.getFirst().centre();
            Vec3 on = pig[0].position();
            helper.assertTrue(Math.abs(c.x - on.x) < 0.01 && Math.abs(c.z - on.z) < 0.01, "the first lies under the pig: " + c + " vs " + on);
            Vec3 away = on.subtract(a.position()).horizontal().normalize();
            for (int i = 1; i < 3; i++) {
                Vec3 f = marks.get(i).centre();
                Vec3 d = f.subtract(c).horizontal();
                helper.assertTrue(Math.abs(d.length() - 2.6) < 0.01, "a flank lies 2.6 blocks from the first, " + d.length());
                double cos = d.normalize().dot(away);
                helper.assertTrue(Math.abs(cos - Math.cos(Math.toRadians(50.0))) < 0.01, "50 degrees off straight away, cos " + cos);
                helper.assertTrue(Math.abs(f.y - floor) < 0.01, "on the ground, y " + f.y + " vs floor " + floor);
            }
            helper.assertTrue(Math.abs(c.y - floor) < 0.01, "the first on the ground too, y " + c.y);
            double side = marks.get(1).centre().subtract(c).cross(marks.get(2).centre().subtract(c)).y;
            helper.assertTrue(Math.abs(side) > 1.0, "the flanks lie on either side, not on top of each other");
        });
    }

    /**
     * No knockback: a pig struck by the circles is left with no motion, while a pig hurt by the same attack
     * straight (the control) is knocked back. A still mob keeps the motion it is given, so it can be read after the hit.
     */
    private static void noKnockback(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        Pig control = pig(helper, new Vec3(8.5, 1, 1.5));
        double[] struck = {-1.0}, knocked = {-1.0};
        helper.onEachTick(() -> {
            if (struck[0] < 0 && a.strikes() == 1) {
                struck[0] = pig[0].getDeltaMovement().horizontalDistance();
                control.hurtServer(helper.getLevel(), a.damageSources().mobAttack(a), AstralAugur.STRIKE_DAMAGE);
                knocked[0] = control.getDeltaMovement().horizontalDistance();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0] >= 0.0, "the circles struck, phase " + a.phase());
            helper.assertTrue(near(pig[0].getHealth(), 20.0F) && near(control.getHealth(), 20.0F), "both lost 20");
            helper.assertTrue(struck[0] < 0.01, "the struck pig was not moved, motion " + struck[0]);
            helper.assertTrue(knocked[0] > 0.1, "the control was knocked back, motion " + knocked[0]);
        });
    }

    /** Every 100 ticks: with the pig standing in sight, the second channel starts 100 ticks after the first. */
    private static void every(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        long[] first = {-1}, second = {-1};
        boolean[] wasIn = {false};
        helper.onEachTick(() -> {
            boolean in = a.core().in(Step.TELL);
            if (in && !wasIn[0]) {
                if (first[0] < 0) first[0] = helper.getTick();
                else if (second[0] < 0) second[0] = helper.getTick();
            }
            wasIn[0] = in;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0] >= 0, "it channelled twice, first at " + first[0] + ", cooldown " + a.cooldown());
            long gap = second[0] - first[0];
            helper.assertTrue(Math.abs(gap - 100) <= 1, "100 ticks from one channel to the next, took " + gap);
            helper.assertTrue(a.strikes() == 1 && near(pig[0].getHealth(), 20.0F), "the first strike landed, health " + pig[0].getHealth());
        });
    }

    /** Range, measured straight up: a pig 16.5 blocks above is out of range, no channel for 20 ticks; at 15.5 it channels within 3. */
    private static void range(GameTestHelper helper) {
        openRoof(helper);
        AstralAugur a = augur(helper, MIDDLE);
        Pig pig = pig(helper, MIDDLE.add(0, 16.5, 0));
        pig.setNoGravity(true);
        TestPlayers.whenDone(helper, pig::discard);
        a.setTarget(pig);
        long[] told = {-1};
        double[] far = {0.0}, close = {0.0};
        helper.onEachTick(() -> {
            if (told[0] < 0 && a.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.runAtTickTime(20, () -> {
            far[0] = a.distanceTo(pig);
            helper.assertTrue(told[0] < 0 && a.core().idle(), "16.5 blocks away is out of range, channelled at " + told[0] + " from " + far[0]);
            pig.snapTo(helper.absoluteVec(MIDDLE.add(0, 15.5, 0)));
            close[0] = a.distanceTo(pig);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(far[0] - 16.5) < 0.05 && Math.abs(close[0] - 15.5) < 0.05,
                    "the pig stood 16.5 then 15.5 blocks away, measured " + far[0] + " and " + close[0]);
            helper.assertTrue(told[0] >= 20 && told[0] <= 23, "15.5 blocks away it channels within 3 ticks, at " + told[0]);
        });
    }

    /** Line of sight: with a wall between them it never channels (30 ticks); the wall gone, it channels within 3 ticks. */
    private static void sight(GameTestHelper helper) {
        wall(helper, true);
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        long[] told = {-1};
        helper.onEachTick(() -> {
            if (told[0] < 0 && a.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(told[0] < 0 && !a.hasLineOfSight(pig[0]), "behind the wall it never channels, at " + told[0]);
            wall(helper, false);
        });
        helper.succeedWhen(() -> helper.assertTrue(told[0] >= 30 && told[0] <= 33, "in sight again it channels within 3 ticks, at " + told[0]));
    }

    /** A broken channel: it flinches for 20 ticks, counted tick by tick, then stands up idle. */
    private static void flinch(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        Pig hitter = pig(helper, new Vec3(2.5, 1, 4.5));
        int[] flinching = {0};
        long[] ended = {-1};
        boolean[] hit = {false};
        helper.onEachTick(() -> {
            if (!hit[0] && a.core().in(Step.TELL) && a.core().elapsed() >= 4) {
                a.hurtServer(helper.getLevel(), a.damageSources().mobAttack(hitter), 1.0F);
                hit[0] = true;
            }
            if (a.core().in(Step.BROKEN)) flinching[0]++;
            else if (flinching[0] > 0 && ended[0] < 0) ended[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0] >= 0, "it flinched and recovered, " + flinching[0] + " ticks, phase " + a.phase());
            helper.assertTrue(Math.abs(flinching[0] - 20) <= 1, "the flinch lasts 20 ticks, lasted " + flinching[0]);
            helper.assertTrue(near(pig[0].getHealth(), 40.0F), "and nothing struck, pig " + pig[0].getHealth());
        });
    }

    /**
     * Its last mote: three broken channels in a row crack off two motes (two star motes drop), never the third. The
     * channel after that casts one circle.
     */
    private static void lastMote(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        Pig hitter = pig(helper, new Vec3(2.5, 1, 4.5));
        int motesBefore = starMotes(helper);
        int[] lastSize = {-1};
        helper.onEachTick(() -> {
            if (a.core().in(Step.TELL)) {
                if (a.breaks() < 3 && a.core().elapsed() >= 2) {
                    a.hurtServer(helper.getLevel(), a.damageSources().mobAttack(hitter), 1.0F);
                    a.setHealth(a.getMaxHealth());
                } else if (a.breaks() == 3) {
                    lastSize[0] = a.marks().size();
                }
            } else if (a.core().idle()) {
                // a blow makes it turn on the hitter, which a follow range of 1 then drops: point it back at the pig
                a.setTarget(pig[0]);
                if (a.cooldown() > 0) a.setCooldown(0);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(a.breaks() == 3 && lastSize[0] >= 0, "three broken channels and a fourth, breaks " + a.breaks());
            helper.assertTrue(a.motesLost() == 2, "two motes lost and the last kept, lost " + a.motesLost());
            helper.assertTrue(starMotes(helper) - motesBefore == 2, "two star motes dropped, found " + (starMotes(helper) - motesBefore));
            helper.assertTrue(lastSize[0] == 1, "the next channel casts one circle, cast " + lastSize[0]);
        });
    }

    /**
     * The backhand up close: with a pig in reach and its channel not ready, it winds up for 10 ticks (the pig unhurt the
     * while), then strikes for its attack, 8.5.
     */
    private static void swipe(GameTestHelper helper) {
        AstralAugur a = augur(helper, AT);
        a.setCooldown(10_000);
        Pig pig = pig(helper, AT.add(1.2, 0, 0));
        a.setTarget(pig);
        int[] winding = {0};
        long[] first = {-1};
        boolean[] early = {false}, done = {false};
        helper.onEachTick(() -> {
            if (a.core().in(Step.SWIPE)) {
                if (first[0] < 0) first[0] = helper.getTick();
                winding[0]++;
                early[0] |= pig.getHealth() < 40.0F;
            } else if (winding[0] > 0) {
                done[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "it wound up and swung, phase " + a.phase());
            helper.assertTrue(first[0] <= 3, "a target in reach starts the wind-up at once, at " + first[0]);
            helper.assertTrue(Math.abs(winding[0] - 10) <= 1, "the wind-up lasts 10 ticks, lasted " + winding[0]);
            helper.assertTrue(!early[0], "nothing lands during the wind-up");
            helper.assertTrue(near(pig.getHealth(), 31.5F), "the blow does its attack, 8.5: 40 -> 31.5, got " + pig.getHealth());
        });
    }

    /** A listener that cancels the "strike" step: the circles fade at the end of the channel and nothing is hurt. */
    private static void strikeCancel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        AstralAugur a = onPig(helper, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == a && e.step().equals("strike")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        long[] told = {-1};
        helper.onEachTick(() -> {
            if (told[0] < 0 && a.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(told[0] >= 0 && helper.getTick() >= told[0] + 30, "the channel ran out, phase " + a.phase());
            helper.assertTrue(a.strikes() == 0 && a.marks().isEmpty(), "no strike and the circles are gone");
            helper.assertTrue(near(pig[0].getHealth(), 40.0F), "the pig is unhurt, health " + pig[0].getHealth());
            helper.assertTrue(a.motesLost() == 0, "a vetoed strike costs it no mote");
        });
    }

    /** Health 34, attack 8.5, armour 2, a 0.7 x 2.1 hitbox. */
    private static void stats(GameTestHelper helper) {
        AstralAugur a = helper.spawn(ModMobs.ASTRAL_AUGUR.get(), AT);
        a.removeFreeWill();
        a.core().setLook(LookDef.BASE, true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(near(a.getMaxHealth(), 34.0F) && near(a.getHealth(), 34.0F), "health 34, got " + a.getHealth() + "/" + a.getMaxHealth());
            helper.assertTrue(Math.abs(a.getAttributeValue(Attributes.ATTACK_DAMAGE) - 8.5) < 0.01, "attack 8.5");
            helper.assertTrue(Math.abs(a.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(a.getBbWidth() - 0.7F) < 0.02F && Math.abs(a.getBbHeight() - 2.1F) < 0.02F,
                    "hitbox 0.7 x 2.1, got " + a.getBbWidth() + " x " + a.getBbHeight());
            helper.assertTrue(a.circles() == 3 && a.motesLost() == 0, "a whole halo: three circles");
            helper.succeed();
        });
    }

    /**
     * Home: the End highlands by biome spawn, weight 4 in groups of 1, and the main island through the Teeming spawner's
     * main-island rule (a monster of the End). The Dusk Augur is its second look; it drops star motes.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(TeemingSpawns.tag("astral_augur"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "astral_augur").stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)), "its home holds the End highlands");
        for (var other : List.of(Biomes.END_MIDLANDS, Biomes.END_BARRENS, Biomes.SMALL_END_ISLANDS, Biomes.PLAINS)) {
            helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "astral_augur").stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("astral_augur");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.ASTRAL_AUGUR.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.assertTrue(row.spawner().equals("main_island") && TeemingSpawner.Rule.of(row.spawner()) == TeemingSpawner.Rule.MAIN_ISLAND,
                "the spawner places it on the main island, rule " + row.spawner());
        helper.assertTrue(TeemingSpawner.Rule.MAIN_ISLAND.dimension.equals(Level.END), "an End rule");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/astral_augur")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.ASTRAL_AUGUR.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("astral_augur");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("dusk_augur")), "the Dusk Augur is a look: " + looks);
        helper.assertTrue(row.material().equals("star_mote") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("star_mote")),
                "it drops star motes, a registered item");
        helper.succeed();
    }

    // ---- the ground marks on their own -----------------------------------------------------------------------------

    /** Filling: a mark due in 20 ticks reads 0 full as it opens, half full at 10 ticks, and is due (full) at 20, not before. */
    private static void marksFill(GameTestHelper helper) {
        GroundMarks marks = new GroundMarks(4.0);
        long[] opened = {-1}, due = {-1};
        float[] atOpen = {-1.0F}, atHalf = {-1.0F};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (opened[0] < 0) {
                marks.place(helper.getLevel(), List.of(helper.absoluteVec(new Vec3(5.5, 1, 5.5))), 1.5, 20);
                opened[0] = now;
                atOpen[0] = marks.fill(now);
            }
            if (now == opened[0] + 10) atHalf[0] = marks.fill(now);
            if (due[0] < 0 && marks.tick(helper.getLevel())) due[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(due[0] >= 0, "the mark came due");
            helper.assertTrue(Math.abs(atOpen[0]) < 1.0E-6, "empty as it opens, " + atOpen[0]);
            helper.assertTrue(Math.abs(atHalf[0] - 0.5F) < 1.0E-6, "half full at 10 of 20 ticks, " + atHalf[0]);
            helper.assertTrue(due[0] - opened[0] == 20, "due 20 ticks after it opened, took " + (due[0] - opened[0]));
            helper.assertTrue(marks.placed(), "due is not spent: it waits for the strike");
        });
    }

    /**
     * Settling on the ground: a spot 2.5 blocks over the floor settles on it; one over a slab settles on the slab's top;
     * one inside a stone block climbs out onto it; one 6 blocks over the floor (out of reach, as over the void) stays.
     */
    private static void marksGround(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE_SLAB);
        helper.setBlock(new BlockPos(8, 1, 8), Blocks.STONE);
        helper.runAtTickTime(1, () -> {
            var level = helper.getLevel();
            double floor = helper.absoluteVec(new Vec3(0, 1, 0)).y;
            Vec3 high = GroundMarks.ground(level, helper.absoluteVec(new Vec3(5.5, 3.5, 5.5)));
            Vec3 slab = GroundMarks.ground(level, helper.absoluteVec(new Vec3(3.5, 3.0, 3.5)));
            Vec3 buried = GroundMarks.ground(level, helper.absoluteVec(new Vec3(8.5, 1.2, 8.5)));
            Vec3 air = helper.absoluteVec(new Vec3(5.5, 7.0, 2.5));
            Vec3 stays = GroundMarks.ground(level, air);
            helper.assertTrue(Math.abs(high.y - floor) < 1.0E-6, "2.5 over the floor settles on it: " + high.y + " vs " + floor);
            helper.assertTrue(Math.abs(slab.y - (floor + 0.5)) < 1.0E-6, "a slab's top: " + slab.y + " vs " + (floor + 0.5));
            helper.assertTrue(Math.abs(buried.y - (floor + 1.0)) < 1.0E-6, "out of the stone onto it: " + buried.y + " vs " + (floor + 1.0));
            helper.assertTrue(stays.equals(air), "6 over the floor is out of reach and stays: " + stays);
            helper.succeed();
        });
    }

    /**
     * Striking: three marks laid over one another round a pig hit it once; a pig outside all of them is not hit, and a
     * pig standing 5 blocks over a mark (higher than the strike's 4) is not hit either. The marks are spent.
     */
    private static void marksStrike(GameTestHelper helper) {
        Pig in = pig(helper, new Vec3(5.5, 1, 5.5));
        Pig outside = pig(helper, new Vec3(9.5, 1, 5.5));
        Pig above = pig(helper, new Vec3(2.5, 6, 2.5));
        above.setNoGravity(true);
        List<LivingEntity> hit = new ArrayList<>();
        int[] drawn = {0};
        GroundMarks marks = new GroundMarks(4.0).affects(e -> e instanceof Pig, (level, e) -> hit.add(e))
                .drawn((level, m, f) -> {}, (level, m) -> drawn[0]++, (level, m) -> {});
        helper.runAtTickTime(2, () -> {
            var level = helper.getLevel();
            Vec3 c = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
            marks.place(level, List.of(c, c.add(0.5, 0, 0), c.add(0, 0, 0.5), helper.absoluteVec(new Vec3(2.5, 1, 2.5))), 1.5, 10);
            List<LivingEntity> struck = marks.strike(level);
            helper.assertTrue(struck.equals(hit), "strike returns who it hit");
            helper.assertTrue(hit.stream().filter(e -> e == in).count() == 1, "the pig under three marks is hit once: " + hit);
            helper.assertTrue(!hit.contains(outside), "a pig outside every mark is not hit");
            helper.assertTrue(!hit.contains(above), "a pig 5 blocks over a mark is out of the strike's 4");
            helper.assertTrue(drawn[0] == 4 && !marks.placed(), "each mark showed its strike and all are spent");
            helper.succeed();
        });
    }

    /** Cancelling: the marks fade (each shows its fade) and nothing inside is hit, now or when they would have come due. */
    private static void marksCancel(GameTestHelper helper) {
        Pig in = pig(helper, new Vec3(5.5, 1, 5.5));
        List<LivingEntity> hit = new ArrayList<>();
        int[] faded = {0};
        GroundMarks marks = new GroundMarks(4.0).affects(e -> true, (level, e) -> hit.add(e))
                .drawn((level, m, f) -> {}, (level, m) -> {}, (level, m) -> faded[0]++);
        boolean[] due = {false};
        helper.runAtTickTime(2, () -> {
            marks.place(helper.getLevel(), List.of(helper.absoluteVec(new Vec3(5.5, 1, 5.5)), helper.absoluteVec(new Vec3(6.5, 1, 5.5))), 1.5, 5);
            marks.cancel(helper.getLevel());
        });
        helper.onEachTick(() -> due[0] |= marks.tick(helper.getLevel()));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(faded[0] == 2, "each mark faded, " + faded[0]);
            helper.assertTrue(!marks.placed() && !due[0], "none is left to come due");
            helper.assertTrue(hit.isEmpty() && near(in.getHealth(), 40.0F), "nothing was hit");
            helper.succeed();
        });
    }
}
