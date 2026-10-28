package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.overworld.Snowroller;
import com.panda.teeming.mob.overworld.Snowroller.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Snowroller: the 16-tick tuck holds it still and hurts nothing, then it rolls in a straight line. A roll starts at
 * 0.25 blocks a tick and keeps that on the flat, gains 0.02 a tick downhill up to 0.6, loses 0.02 a tick uphill and
 * uncurls under 0.1. A hit does its attack (4) x (1 + blocks rolled / 8), at most x2.5 (10), and its scale grows from
 * 1 to 1.5 with it. A roll into a wall stuns it 60 ticks, taking x1.5.
 *
 * <p>Pigs stand in as targets (no difficulty scaling), with 100 health so a capped hit can be read in full. Every
 * Snowroller here has its goals cleared and no follow range, so it acts only on the target the test gives it.
 */
public final class SnowrollerTests {
    private SnowrollerTests() {}

    private static final Vec3 EAST = new Vec3(1, 0, 0);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("snowroller_tell", SnowrollerTests::tell);
        out.accept("snowroller_counter", SnowrollerTests::wallStuns);
        out.accept("snowroller_failure", SnowrollerTests::rollHits);
        out.accept("snowroller_stun_length", SnowrollerTests::stunLength);
        out.accept("snowroller_roll_start", SnowrollerTests::rollStart);
        out.accept("snowroller_downhill", SnowrollerTests::downhill);
        out.accept("snowroller_speed_cap", SnowrollerTests::speedCap);
        out.accept("snowroller_uphill", SnowrollerTests::uphill);
        out.accept("snowroller_growth", SnowrollerTests::growth);
        out.accept("snowroller_hit_cap", SnowrollerTests::hitCap);
        out.accept("snowroller_hit_cancel", SnowrollerTests::hitCancel);
        out.accept("snowroller_stats", SnowrollerTests::stats);
        out.accept("snowroller_home", SnowrollerTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A Snowroller at {@code at} that acts only on what the test tells it: no goals, no follow range, the base look. */
    private static Snowroller roller(GameTestHelper helper, Vec3 at) {
        Snowroller roller = helper.spawn(ModMobs.SNOWROLLER.get(), at);
        roller.core().setLook("base", true);
        roller.removeFreeWill();
        roller.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        roller.resetCooldown();
        return roller;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        return pig;
    }

    /** A Snowroller at x 1.5 hunting a pig 9 blocks east on the flat floor. */
    private static Snowroller huntingPig(GameTestHelper helper, Pig[] out) {
        Snowroller roller = roller(helper, new Vec3(1.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(10.5, 1, 5.5));
        roller.setTarget(pig);
        out[0] = pig;
        return roller;
    }

    /** A stone wall across the arena at {@code x}, 3 high. */
    private static void wall(GameTestHelper helper, int x) {
        for (int z = 1; z <= 10; z++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }

    /**
     * Stone stairs down toward +x, 1 block down for every block across: a platform 4 high at x 0 and 1, then 3, 2 and
     * 1 high at x 2 to 4, the floor from x 5. A ball rolling east off the platform goes downhill to the floor.
     */
    private static void stairsDown(GameTestHelper helper) {
        int[] heights = {4, 4, 3, 2, 1};
        for (int x = 0; x < heights.length; x++) {
            for (int z = 2; z <= 9; z++) for (int y = 1; y <= heights[x]; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
    }

    /** Speeds and per-tick moves of the roll in progress, recorded every tick while it rolls. */
    private record Trace(List<Double> speeds, List<Double> steps, List<Double> xs, List<Double> ys) {
        static Trace of(GameTestHelper helper, Snowroller roller) {
            Trace trace = new Trace(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            Vec3[] last = {null};
            helper.onEachTick(() -> {
                if (!roller.core().in(Step.ROLL)) {
                    last[0] = null;
                    return;
                }
                Vec3 now = roller.position();
                trace.speeds.add(roller.rollSpeed());
                trace.steps.add(last[0] == null ? Double.NaN : now.subtract(last[0]).horizontalDistance());
                trace.xs.add(helper.relativeVec(now).x);
                trace.ys.add(helper.relativeVec(now).y);
                last[0] = now;
            });
            return trace;
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /** With a pig 9 blocks off it tucks at once, for 16 ticks, standing still and hurting nothing; the roll follows. */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Snowroller roller = huntingPig(helper, pig);
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] harmed = {false}, rolled = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (roller.core().in(Step.ROLL)) rolled[0] = true;
            if (!roller.core().in(Step.TELL)) return;
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = roller.position();
            }
            ticks[0]++;
            harmed[0] |= pig[0].getHealth() < pig[0].getMaxHealth();
            moved[0] = Math.max(moved[0], roller.position().subtract(start[0]).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && !roller.core().in(Step.TELL), "the tuck runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(first[0] <= 3, "with its target 9 blocks off it tucks at once, started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 16) <= 1, "the tuck lasts 16 ticks, lasted " + ticks[0]);
            helper.assertTrue(!harmed[0], "the tuck hurts nothing");
            helper.assertTrue(moved[0] < 0.2, "it stays put while it tucks, moved " + moved[0]);
            helper.assertTrue(rolled[0], "the roll follows the tuck, phase " + roller.phase());
        });
    }

    /**
     * The counter: a wall goes up between them during the tuck. The roll ends in the wall: stunned, its weak point
     * showing, the snow knocked off (scale 1 again), taking x1.5 (4 becomes 6); the pig is unhurt.
     */
    private static void wallStuns(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Snowroller roller = huntingPig(helper, pig);
        helper.runAtTickTime(6, () -> wall(helper, 6));
        boolean[] rolled = {false};
        helper.onEachTick(() -> rolled[0] |= roller.core().in(Step.ROLL));
        helper.succeedWhen(() -> {
            helper.assertTrue(roller.core().in(Step.STUNNED), "a roll into a wall stuns it, phase " + roller.phase());
            helper.assertTrue(rolled[0], "it got there by rolling");
            helper.assertTrue(roller.vulnerable(), "stunned, its weak point shows");
            helper.assertTrue(Math.abs(roller.getScale() - 1.0F) < 0.01F, "the wall knocks the snow off, scale " + roller.getScale());
            helper.assertTrue(pig[0].getHealth() >= pig[0].getMaxHealth(), "the wall saved the pig");
            float had = roller.getHealth();
            roller.hurtServer(helper.getLevel(), roller.damageSources().magic(), 4.0F);
            float lost = had - roller.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "a stunned Snowroller takes x1.5: 4 becomes 6, lost " + lost);
        });
    }

    /**
     * Failing the counter: the pig stays in line and takes the roll, 4 x (1 + blocks rolled / 8), with the blocks
     * measured here from where the roll started to where it hit. It rolled most of the 9 blocks, so the hit is close
     * to double its attack, and it uncurls rather than being stunned.
     */
    private static void rollHits(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Snowroller roller = huntingPig(helper, pig);
        float before = pig[0].getHealth();
        double[] rolled = {0.0};
        Vec3[] last = {null};
        float[] lost = {-1.0F};
        double[] atHit = {-1.0};
        boolean[] stunned = {false};
        helper.onEachTick(() -> {
            stunned[0] |= roller.core().in(Step.STUNNED);
            if (roller.core().in(Step.ROLL)) {
                Vec3 now = roller.position();
                if (last[0] != null) rolled[0] += now.subtract(last[0]).horizontalDistance();
                last[0] = now;
            }
            if (lost[0] < 0 && pig[0].getHealth() < before) {
                lost[0] = before - pig[0].getHealth();
                atHit[0] = rolled[0];
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0, "the roll hits the pig, phase " + roller.phase());
            double want = 4.0 * (1.0 + atHit[0] / 8.0);
            helper.assertTrue(Math.abs(lost[0] - want) < 0.15, "the hit is 4 x (1 + " + atHit[0] + " / 8) = " + want + ", the pig lost " + lost[0]);
            helper.assertTrue(atHit[0] > 6.0 && lost[0] > 7.0F, "it rolled most of the way (" + atHit[0] + " blocks), so it hit hard, " + lost[0]);
            helper.assertTrue(roller.core().in(Step.RECOVER) || roller.core().idle(), "after a hit it uncurls, phase " + roller.phase());
            helper.assertTrue(!stunned[0], "a hit doesn't stun it");
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The stun from a wall lasts 60 ticks. */
    private static void stunLength(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Snowroller roller = huntingPig(helper, pig);
        helper.runAtTickTime(6, () -> wall(helper, 6));
        int[] stunned = {0};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (roller.core().in(Step.STUNNED)) stunned[0]++;
            else if (stunned[0] > 0) over[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the stun ends, " + stunned[0] + " ticks so far");
            helper.assertTrue(Math.abs(stunned[0] - 60) <= 1, "the stun lasts 60 ticks, lasted " + stunned[0]);
        });
    }

    /** A roll on the flat starts at 0.25 blocks a tick and keeps exactly that until it hits. */
    private static void rollStart(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Snowroller roller = huntingPig(helper, pig);
        Trace trace = Trace.of(helper, roller);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig[0].getHealth() < pig[0].getMaxHealth() && trace.speeds().size() >= 10,
                    "it rolled at least 10 ticks and hit, saw " + trace.speeds().size());
            helper.assertTrue(Math.abs(trace.speeds().getFirst() - 0.25) < 1.0E-6, "the roll starts at 0.25, was " + trace.speeds().getFirst());
            for (double v : trace.speeds()) helper.assertTrue(Math.abs(v - 0.25) < 1.0E-6, "on the flat it keeps 0.25, saw " + v);
            List<Double> steps = trace.steps().subList(2, trace.steps().size());
            double mean = steps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - 0.25) < 0.02, "it covers 0.25 blocks a tick, mean " + mean);
        });
    }

    /**
     * Downhill: rolled east off a platform down 1:1 stairs, it gains exactly 0.02 a tick while it goes down (never
     * more in one tick), then on the floor below it keeps the speed it gathered, and moves at that speed.
     */
    private static void downhill(GameTestHelper helper) {
        stairsDown(helper);
        Snowroller roller = roller(helper, new Vec3(1.0, 5, 5.5));
        wall(helper, 11);
        helper.runAtTickTime(5, () -> helper.assertTrue(roller.roll(EAST, 0.25), "the roll starts"));
        Trace trace = Trace.of(helper, roller);
        boolean[] stunned = {false};
        helper.onEachTick(() -> stunned[0] |= roller.core().in(Step.STUNNED));
        helper.succeedWhen(() -> {
            helper.assertTrue(stunned[0], "it rolled down and on into the far wall, phase " + roller.phase());
            List<Double> v = trace.speeds();
            int gains = 0;
            for (int i = 1; i < v.size(); i++) {
                double d = v.get(i) - v.get(i - 1);
                helper.assertTrue(Math.abs(d) < 1.0E-6 || Math.abs(d - 0.02) < 1.0E-6, "each tick adds 0 or 0.02, tick " + i + " added " + d);
                if (d > 0.01) gains++;
            }
            helper.assertTrue(gains >= 5, "it gained speed on at least 5 ticks down the stairs, " + gains + " from " + v);
            double top = v.getLast();
            helper.assertTrue(Math.abs(top - (0.25 + 0.02 * gains)) < 1.0E-6, "0.25 plus 0.02 for each tick downhill, ended at " + top);
            // down on the floor, short of the far wall, the speed holds, and it moves at it
            int flat = 0;
            for (int i = 1; i < v.size(); i++) {
                boolean floor = Math.abs(trace.ys().get(i - 1) - 1.0) < 1.0E-3 && Math.abs(trace.ys().get(i) - 1.0) < 1.0E-3;
                if (!floor || trace.xs().get(i) > 9.6) continue;
                flat++;
                helper.assertTrue(Math.abs(v.get(i) - v.get(i - 1)) < 1.0E-6, "on the flat it keeps its speed, " + v.get(i - 1) + " to " + v.get(i));
                helper.assertTrue(Math.abs(trace.steps().get(i) - v.get(i - 1)) < 0.03,
                        "it moves at its speed, " + trace.steps().get(i) + " at " + v.get(i - 1));
            }
            helper.assertTrue(flat >= 3, "it rolled at least 3 ticks on the flat below, " + flat);
        });
    }

    /** The cap: started at 0.5 at the top of the stairs, it reaches 0.6 a tick going down and never more. */
    private static void speedCap(GameTestHelper helper) {
        stairsDown(helper);
        Snowroller roller = roller(helper, new Vec3(1.0, 5, 5.5));
        wall(helper, 11);
        helper.runAtTickTime(5, () -> helper.assertTrue(roller.roll(EAST, 0.5), "the roll starts"));
        Trace trace = Trace.of(helper, roller);
        boolean[] stunned = {false};
        helper.onEachTick(() -> stunned[0] |= roller.core().in(Step.STUNNED));
        helper.succeedWhen(() -> {
            helper.assertTrue(stunned[0], "it rolled down and on into the far wall, phase " + roller.phase());
            double top = trace.speeds().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            helper.assertTrue(Math.abs(top - 0.6) < 1.0E-6, "its speed tops out at exactly 0.6, max " + top);
            double fastest = trace.steps().stream().filter(d -> !d.isNaN()).mapToDouble(Double::doubleValue).max().orElse(0);
            helper.assertTrue(fastest > 0.57 && fastest < 0.63, "its fastest tick covers 0.6 blocks, " + fastest);
        });
    }

    /**
     * Uphill: rolled east at 0.25 into a slope of half-block steps, it loses exactly 0.02 a tick, never gains, and
     * uncurls once it is slower than 0.1, without being stunned.
     */
    private static void uphill(GameTestHelper helper) {
        // half-block steps up from x 4: 0.5, 1, 1.5 ... high
        for (int x = 4; x <= 11; x++) {
            int halves = x - 3;
            for (int z = 2; z <= 9; z++) {
                for (int y = 1; y <= halves / 2; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                if (halves % 2 == 1) helper.setBlock(new BlockPos(x, halves / 2 + 1, z), Blocks.STONE_SLAB.defaultBlockState());
            }
        }
        Snowroller roller = roller(helper, new Vec3(1.5, 1, 5.5));
        helper.runAtTickTime(5, () -> helper.assertTrue(roller.roll(EAST, 0.25), "the roll starts"));
        Trace trace = Trace.of(helper, roller);
        boolean[] stunned = {false}, uncurled = {false};
        helper.onEachTick(() -> {
            stunned[0] |= roller.core().in(Step.STUNNED);
            uncurled[0] |= !trace.speeds().isEmpty() && roller.core().in(Step.RECOVER);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(uncurled[0], "it slows to a stop on the slope and uncurls, phase " + roller.phase());
            helper.assertTrue(!stunned[0], "the slope doesn't stun it");
            List<Double> v = trace.speeds();
            int losses = 0;
            for (int i = 1; i < v.size(); i++) {
                double d = v.get(i) - v.get(i - 1);
                helper.assertTrue(Math.abs(d) < 1.0E-6 || Math.abs(d + 0.02) < 1.0E-6, "each tick takes 0 or 0.02, tick " + i + " changed " + d);
                if (d < -0.01) losses++;
            }
            helper.assertTrue(losses >= 3, "it lost speed on at least 3 ticks uphill, " + v);
            double last = roller.rollSpeed();
            helper.assertTrue(last < 0.1 && last > 0.07, "it uncurls once slower than 0.1, at " + last);
            helper.assertTrue(roller.getY() > helper.absoluteVec(new Vec3(0, 1.4, 0)).y, "it climbed the steps it could, y " + roller.getY());
        });
    }

    /**
     * Growth: rolling on the flat its scale is 1 + 0.5 x (blocks rolled / 12), hitbox and all; it starts at 1 and is
     * back to 1 once the roll ends.
     */
    private static void growth(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Snowroller roller = huntingPig(helper, pig);
        double[] rolled = {0.0}, worst = {0.0}, biggest = {1.0}, first = {-1.0};
        Vec3[] last = {null};
        boolean[] after = {false};
        helper.onEachTick(() -> {
            if (roller.core().in(Step.ROLL)) {
                Vec3 now = roller.position();
                // its scale this tick comes from the blocks it had rolled when the tick began
                double want = 1.0 + 0.5 * Math.min(1.0, rolled[0] / 12.0);
                if (first[0] < 0) first[0] = roller.getScale();
                worst[0] = Math.max(worst[0], Math.abs(roller.getScale() - want));
                worst[0] = Math.max(worst[0], Math.abs(roller.getBbWidth() - roller.getScale()));
                biggest[0] = Math.max(biggest[0], roller.getScale());
                if (last[0] != null) rolled[0] += now.subtract(last[0]).horizontalDistance();
                last[0] = now;
            } else if (last[0] != null) {
                after[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(after[0], "the roll ran and ended, phase " + roller.phase());
            helper.assertTrue(Math.abs(first[0] - 1.0) < 0.02, "it starts the roll at scale 1, " + first[0]);
            helper.assertTrue(worst[0] < 0.04, "scale 1 + 0.5 x blocks / 12 every tick, hitbox too, off by up to " + worst[0]);
            helper.assertTrue(biggest[0] > 1.25, "after " + rolled[0] + " blocks it had grown to " + biggest[0]);
            helper.assertTrue(Math.abs(roller.getScale() - 1.0F) < 0.01F && Math.abs(roller.getBbWidth() - 1.0F) < 0.01F,
                    "the roll over, it is back to scale 1, " + roller.getScale());
        });
    }

    /**
     * The caps: rolled 20 blocks (as if from far up a slope) it is at its full scale of 1.5, hitbox 1.5 across, and
     * its hit is 2.5 x its attack: 10, not 4 x (1 + 20 / 8) = 14.
     */
    private static void hitCap(GameTestHelper helper) {
        Snowroller roller = roller(helper, new Vec3(1.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(6.5, 1, 5.5));
        float[] scale = {0.0F}, width = {0.0F};
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(roller.roll(EAST, 0.25), "the roll starts");
            roller.setRolled(20.0);
        });
        helper.onEachTick(() -> {
            if (roller.core().in(Step.ROLL)) {
                scale[0] = Math.max(scale[0], roller.getScale());
                width[0] = Math.max(width[0], roller.getBbWidth());
            }
        });
        helper.succeedWhen(() -> {
            float lost = pig.getMaxHealth() - pig.getHealth();
            helper.assertTrue(lost > 0, "the roll hits the pig");
            helper.assertTrue(Math.abs(lost - 10.0F) < 0.01F, "the hit is capped at 2.5 x 4 = 10, the pig lost " + lost);
            helper.assertTrue(Math.abs(scale[0] - 1.5F) < 0.02F, "fully grown it is scale 1.5, was " + scale[0]);
            helper.assertTrue(Math.abs(width[0] - 1.5F) < 0.02F, "and 1.5 across, was " + width[0]);
        });
    }

    /** A listener that cancels the "roll_hit" step spares the target; the roll still ends and it uncurls. */
    private static void hitCancel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Snowroller roller = huntingPig(helper, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == roller && e.step().equals("roll_hit")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] rolled = {false}, uncurled = {false};
        helper.onEachTick(() -> {
            rolled[0] |= roller.core().in(Step.ROLL);
            uncurled[0] |= rolled[0] && roller.core().in(Step.RECOVER);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(uncurled[0], "it rolled up to the pig and uncurled, phase " + roller.phase());
            helper.assertTrue(pig[0].getHealth() >= pig[0].getMaxHealth(), "a cancelled hit does no damage");
        });
    }

    /**
     * Health 26, attack 4, armour 4, a 1.0 x 1.0 hitbox at scale 1; outside a stun a hit counts at its face value;
     * its looks are the base and Frostbitten.
     */
    private static void stats(GameTestHelper helper) {
        Snowroller roller = roller(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(roller.getMaxHealth() - 26.0F) < 0.01F && Math.abs(roller.getHealth() - 26.0F) < 0.01F,
                    "health 26, got " + roller.getHealth() + "/" + roller.getMaxHealth());
            helper.assertTrue(Math.abs(roller.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4");
            helper.assertTrue(Math.abs(roller.getAttributeValue(Attributes.ARMOR) - 4.0) < 0.01, "armour 4");
            helper.assertTrue(Math.abs(roller.getScale() - 1.0F) < 0.01F, "scale 1 when it isn't rolling, " + roller.getScale());
            helper.assertTrue(Math.abs(roller.getBbWidth() - 1.0F) < 0.02F && Math.abs(roller.getBbHeight() - 1.0F) < 0.02F,
                    "hitbox 1.0 x 1.0, got " + roller.getBbWidth() + " x " + roller.getBbHeight());
            helper.assertTrue(!roller.vulnerable(), "not stunned, no weak point");
            List<String> looks = Looks.of("snowroller").stream().map(LookDef::id).sorted().toList();
            helper.assertTrue(looks.equals(List.of("base", "frostbitten")), "looks base and frostbitten, got " + looks);
            float had = roller.getHealth();
            roller.hurtServer(helper.getLevel(), roller.damageSources().magic(), 4.0F);
            float lost = had - roller.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F) < 0.01F, "outside a stun 4 stays 4, lost " + lost);
            helper.succeed();
        });
    }

    /**
     * Its home: snowy slopes, groves and jagged peaks, each at its tuned weight; roster weight 15 elsewhere in its tag, in
     * groups of 1 to 2, in the dark like any monster.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/snowroller"));
        BiomeModifier modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key)
                .map(h -> h.value()).orElse(null);
        helper.assertTrue(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier, "a spawn modifier for the Snowroller, got " + modifier);
        var spawns = (BiomeModifiers.AddSpawnsBiomeModifier) modifier;
        // each home biome has a tuned weight (the Overworld share): its own modifier at that weight, and the roster
        // weight (for biome mods' biomes) leaves it out
        for (var biome : List.of(Biomes.SNOWY_SLOPES, Biomes.GROVE, Biomes.JAGGED_PEAKS)) {
            String path = biome.identifier().getPath();
            int want = com.panda.teeming.spawn.SpawnTable.tuned("snowroller").stream().filter(r -> r.biome().equals(biome.identifier().toString()))
                    .mapToInt(com.panda.teeming.spawn.SpawnTable.Row::weight).findFirst().orElse(0);
            var tuned = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                    .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/snowroller/" + path))).map(h -> h.value()).orElse(null);
            helper.assertTrue(!spawns.biomes().contains(access.lookupOrThrow(Registries.BIOME).getOrThrow(biome)), path + " has a tuned weight, so the roster weight leaves it out");
            helper.assertTrue(tuned instanceof BiomeModifiers.AddSpawnsBiomeModifier t && want > 0
                    && t.biomes().contains(access.lookupOrThrow(Registries.BIOME).getOrThrow(biome)) && t.spawners().unwrap().getFirst().weight() == want,
                    "it spawns in " + path + " at its tuned weight " + want + ", got " + tuned);
        }
        helper.assertTrue(!spawns.biomes().contains(biomes.getOrThrow(Biomes.PLAINS)), "not on the plains");
        var entries = spawns.spawners().unwrap();
        helper.assertTrue(entries.size() == 1, "one spawner entry, got " + entries.size());
        var entry = entries.getFirst();
        helper.assertTrue(entry.value().type() == ModMobs.SNOWROLLER.get(), "the entry spawns Snowrollers");
        helper.assertTrue(entry.weight() == 15, "weight 15, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "groups of 1 to 2, got " + entry.value().count().minInclusive() + " to " + entry.value().count().maxInclusive());
        helper.assertTrue(!RosterStats.get("snowroller").anyLight(), "it spawns in the dark, like other monsters");
        helper.succeed();
    }
}
