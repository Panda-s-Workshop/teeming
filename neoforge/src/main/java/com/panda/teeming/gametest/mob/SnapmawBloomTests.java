package com.panda.teeming.gametest.mob;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.SnapmawBloom;
import com.panda.teeming.mob.overworld.SnapmawBloom.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Snapmaw Bloom: a 10-tick tell, seeds of 3 that fly 14 blocks straight along its facing every 40 ticks, a 7.5 snap at
 * the next block, a 45-degree-a-second turn, x2 from its rear 120 degrees, and nothing ever moves it. Every number is
 * checked against a plain value. The arena is 12 x 12, so long shots run along its diagonal from a corner.
 */
public final class SnapmawBloomTests {
    private SnapmawBloomTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("snapmaw_bloom_tell", SnapmawBloomTests::tell);
        out.accept("snapmaw_bloom_counter", SnapmawBloomTests::rearHitsDouble);
        out.accept("snapmaw_bloom_failure", SnapmawBloomTests::seedHits);
        out.accept("snapmaw_bloom_rear_arc", SnapmawBloomTests::rearArcEdges);
        out.accept("snapmaw_bloom_flanked_glow", SnapmawBloomTests::flankedGlow);
        out.accept("snapmaw_bloom_range_in", SnapmawBloomTests::rangeIn);
        out.accept("snapmaw_bloom_range_out", SnapmawBloomTests::rangeOut);
        out.accept("snapmaw_bloom_seed_reach", SnapmawBloomTests::seedReach);
        out.accept("snapmaw_bloom_spit_interval", SnapmawBloomTests::spitInterval);
        out.accept("snapmaw_bloom_snap", SnapmawBloomTests::snap);
        out.accept("snapmaw_bloom_turn_rate", SnapmawBloomTests::turnRate);
        out.accept("snapmaw_bloom_rooted", SnapmawBloomTests::rooted);
        out.accept("snapmaw_bloom_dodge", SnapmawBloomTests::dodge);
        out.accept("snapmaw_bloom_cover", SnapmawBloomTests::cover);
    }

    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        return pig;
    }

    /** A bloom facing {@code target} with it as its target. */
    private static SnapmawBloom bloom(GameTestHelper helper, double x, double z, Pig target) {
        SnapmawBloom bloom = helper.spawn(ModMobs.SNAPMAW_BLOOM.get(), new Vec3(x, 1, z));
        bloom.face(target.position());
        bloom.setTarget(target);
        return bloom;
    }

    /** A still bloom (no AI, so it neither turns nor attacks) facing +Z. */
    private static SnapmawBloom stillBloom(GameTestHelper helper, double x, double z) {
        SnapmawBloom bloom = helper.spawn(ModMobs.SNAPMAW_BLOOM.get(), new Vec3(x, 1, z));
        bloom.setNoAi(true);
        bloom.face(bloom.position().add(0, 0, 5));
        return bloom;
    }

    /** Records the test tick of every seed {@code bloom} launches; call {@link #stop} when done. */
    private static final class SeedLog implements Consumer<MechanicEvent> {
        final SnapmawBloom bloom;
        final List<Integer> launches = new ArrayList<>();
        int hits;
        int tick;

        SeedLog(GameTestHelper helper, SnapmawBloom bloom) {
            this.bloom = bloom;
            MechanicEvent.listen(this);
            helper.onEachTick(() -> tick++);
        }

        @Override
        public void accept(MechanicEvent e) {
            if (e.mob() != bloom) return;
            if (e.step().equals("seed")) launches.add(tick);
            if (e.step().equals("seed_hit")) hits++;
        }

        void stop() {
            MechanicEvent.unlisten(this);
        }
    }

    /** The jaws stay open for exactly 10 ticks, hurting nothing and launching nothing; the seed comes after. */
    private static void tell(GameTestHelper helper) {
        Pig pig = pig(helper, 2.5, 8.5);
        SnapmawBloom bloom = bloom(helper, 2.5, 2.5, pig);
        SeedLog log = new SeedLog(helper, bloom);
        int[] tellTicks = {0};
        boolean[] ended = {false};
        boolean[] quietTell = {true};
        helper.onEachTick(() -> {
            if (bloom.core().in(Step.TELL)) {
                tellTicks[0]++;
                if (!log.launches.isEmpty() || pig.getHealth() < pig.getMaxHealth()) quietTell[0] = false;
            } else if (tellTicks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the tell ends, " + tellTicks[0] + " ticks so far");
            helper.assertTrue(tellTicks[0] == 10, "the jaws stay open 10 ticks, saw " + tellTicks[0]);
            helper.assertTrue(quietTell[0], "nothing is launched or hurt during the tell");
            helper.assertTrue(log.launches.size() == 1, "the tell ends in one seed, saw " + log.launches.size());
            log.stop();
        });
    }

    /** Failing the counter: a target that stands in front of it takes the seed, exactly 3. */
    private static void seedHits(GameTestHelper helper) {
        Pig pig = pig(helper, 2.5, 8.5);
        SnapmawBloom bloom = bloom(helper, 2.5, 2.5, pig);
        SeedLog log = new SeedLog(helper, bloom);
        float before = pig.getHealth();
        helper.succeedWhen(() -> {
            float lost = before - pig.getHealth();
            helper.assertTrue(log.hits == 1, "one seed struck, saw " + log.hits);
            helper.assertTrue(Math.abs(lost - 3.0F) < 0.01F, "a seed does 3, the pig lost " + lost);
            log.stop();
        });
    }

    /** The counter: the same blow from straight behind does x2, from straight ahead x1. */
    private static void rearHitsDouble(GameTestHelper helper) {
        SnapmawBloom rear = stillBloom(helper, 2.5, 5.5);
        SnapmawBloom front = stillBloom(helper, 8.5, 5.5);
        Pig behind = pig(helper, 2.5, 3.5);
        Pig ahead = pig(helper, 8.5, 7.5);
        helper.runAtTickTime(2, () -> {
            float r0 = rear.getHealth(), f0 = front.getHealth();
            rear.hurtServer(helper.getLevel(), rear.damageSources().indirectMagic(behind, behind), 2.0F);
            front.hurtServer(helper.getLevel(), front.damageSources().indirectMagic(ahead, ahead), 2.0F);
            float r = r0 - rear.getHealth(), f = f0 - front.getHealth();
            helper.assertTrue(Math.abs(r - 4.0F) < 0.01F, "a 2-damage blow from behind takes 4, took " + r);
            helper.assertTrue(Math.abs(f - 2.0F) < 0.01F, "the same blow from in front takes 2, took " + f);
            helper.succeed();
        });
    }

    /** Its rear arc is 120 degrees: a blow from 125 degrees off its facing doubles, one from 115 degrees doesn't. */
    private static void rearArcEdges(GameTestHelper helper) {
        SnapmawBloom inside = stillBloom(helper, 3.5, 4.5);
        SnapmawBloom outside = stillBloom(helper, 8.5, 4.5);
        double a = Math.toRadians(125), b = Math.toRadians(115);
        Pig at125 = pig(helper, 3.5 - 2.5 * Math.sin(a), 4.5 + 2.5 * Math.cos(a));
        Pig at115 = pig(helper, 8.5 + 2.5 * Math.sin(b), 4.5 + 2.5 * Math.cos(b));
        helper.runAtTickTime(2, () -> {
            float i0 = inside.getHealth(), o0 = outside.getHealth();
            inside.hurtServer(helper.getLevel(), inside.damageSources().indirectMagic(at125, at125), 2.0F);
            outside.hurtServer(helper.getLevel(), outside.damageSources().indirectMagic(at115, at115), 2.0F);
            float i = i0 - inside.getHealth(), o = o0 - outside.getHealth();
            helper.assertTrue(Math.abs(i - 4.0F) < 0.01F, "125 degrees off its facing is its rear: took " + i + ", want 4");
            helper.assertTrue(Math.abs(o - 2.0F) < 0.01F, "115 degrees off its facing is not: took " + o + ", want 2");
            helper.succeed();
        });
    }

    /** With its target behind it the weak point shows, and it does not spit while it is still turning round. */
    private static void flankedGlow(GameTestHelper helper) {
        Pig pig = pig(helper, 5.5, 1.5);
        SnapmawBloom bloom = helper.spawn(ModMobs.SNAPMAW_BLOOM.get(), new Vec3(5.5, 1, 7.5));
        bloom.face(bloom.position().add(0, 0, 5));
        bloom.setTarget(pig);
        SeedLog log = new SeedLog(helper, bloom);
        helper.runAtTickTime(3, () -> helper.assertTrue(bloom.vulnerable(), "its target is straight behind it: the seed sac glows"));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(log.launches.isEmpty(), "no seed while the target is out of its line, saw " + log.launches.size());
            helper.assertTrue(!bloom.vulnerable(), "turned 135 degrees, its target is no longer behind it");
            log.stop();
            helper.succeed();
        });
    }

    /** A target 13.9 blocks away is spat at and hit. */
    private static void rangeIn(GameTestHelper helper) {
        double d = 13.9 / Math.sqrt(2);
        Pig pig = pig(helper, 0.5 + d, 0.5 + d);
        SnapmawBloom bloom = bloom(helper, 0.5, 0.5, pig);
        SeedLog log = new SeedLog(helper, bloom);
        float before = pig.getHealth();
        helper.succeedWhen(() -> {
            float lost = before - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 3.0F) < 0.01F, "13.9 blocks is in range: the pig lost " + lost + ", want 3");
            log.stop();
        });
    }

    /** A target 14.5 blocks away, square in its line, is never spat at. */
    private static void rangeOut(GameTestHelper helper) {
        double d = 14.5 / Math.sqrt(2);
        Pig pig = pig(helper, 0.5 + d, 0.5 + d);
        SnapmawBloom bloom = bloom(helper, 0.5, 0.5, pig);
        SeedLog log = new SeedLog(helper, bloom);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(log.launches.isEmpty(), "14.5 blocks is out of range: no seed, saw " + log.launches.size());
            helper.assertTrue(bloom.core().idle(), "no tell either, phase " + bloom.phase());
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "the pig is unhurt");
            log.stop();
            helper.succeed();
        });
    }

    /** A seed flies 14 blocks and no further: spat at a pig whose near corner is 14.9 blocks off, it falls short. */
    private static void seedReach(GameTestHelper helper) {
        Pig pig = pig(helper, 11.5, 11.5);
        SnapmawBloom bloom = bloom(helper, 0.5, 0.5, pig);
        bloom.setNoAi(true);
        SeedLog log = new SeedLog(helper, bloom);
        helper.runAtTickTime(2, () -> helper.assertTrue(bloom.spit(helper.getLevel()), "the seed is launched"));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(log.launches.size() == 1 && bloom.seedsInFlight() == 0, "the one seed is spent, " + bloom.seedsInFlight() + " in the air");
            helper.assertTrue(log.hits == 0 && pig.getHealth() >= pig.getMaxHealth(), "it fell short of the pig");
            log.stop();
            helper.succeed();
        });
    }

    /** With its target held in front it spits once every 40 ticks. */
    private static void spitInterval(GameTestHelper helper) {
        Pig pig = pig(helper, 2.5, 8.5);
        SnapmawBloom bloom = bloom(helper, 2.5, 2.5, pig);
        SeedLog log = new SeedLog(helper, bloom);
        helper.succeedWhen(() -> {
            helper.assertTrue(log.launches.size() >= 2, "two seeds so far: " + log.launches);
            int gap = log.launches.get(1) - log.launches.get(0);
            helper.assertTrue(gap == 40, "one seed every 40 ticks, the gap was " + gap);
            log.stop();
        });
    }

    /** A target in the next block, in front, is bitten instead of spat at: 1.5 x 5 = 7.5. */
    private static void snap(GameTestHelper helper) {
        Pig pig = pig(helper, 2.5, 3.7);
        SnapmawBloom bloom = bloom(helper, 2.5, 2.5, pig);
        SeedLog log = new SeedLog(helper, bloom);
        float before = pig.getHealth();
        helper.succeedWhen(() -> {
            float lost = before - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 7.5F) < 0.01F, "the snap does 7.5, the pig lost " + lost);
            helper.assertTrue(bloom.core().in(Step.SNAP), "it snapped, phase " + bloom.phase());
            helper.assertTrue(log.launches.isEmpty(), "no seed at a target in the next block");
            log.stop();
        });
    }

    /** It turns toward a target 90 degrees off at 2.25 degrees a tick, never faster: 45 degrees a second. */
    private static void turnRate(GameTestHelper helper) {
        Pig pig = pig(helper, 5.5, 8.5);
        SnapmawBloom bloom = helper.spawn(ModMobs.SNAPMAW_BLOOM.get(), new Vec3(5.5, 1, 2.5));
        bloom.face(bloom.position().add(5, 0, 0));
        bloom.setTarget(pig);
        List<Float> yaws = new ArrayList<>();
        helper.onEachTick(() -> yaws.add(bloom.getYRot()));
        helper.runAtTickTime(30, () -> {
            List<Float> steps = new ArrayList<>();
            for (int i = 1; i < yaws.size(); i++) steps.add(Math.abs(Mth.degreesDifference(yaws.get(i - 1), yaws.get(i))));
            float max = 0.0F, first20 = 0.0F;
            int moving = 0;
            for (float s : steps) {
                max = Math.max(max, s);
                if (s > 0.001F && moving < 20) {
                    first20 += s;
                    moving++;
                }
            }
            helper.assertTrue(moving == 20, "it turned for 20 ticks, saw " + moving);
            helper.assertTrue(max <= 2.25F + 0.01F, "never more than 2.25 degrees a tick, saw " + max);
            helper.assertTrue(Math.abs(first20 - 45.0F) < 0.5F, "45 degrees in 20 ticks, turned " + first20);
            helper.succeed();
        });
    }

    /** Knockback, a shove and a blast next to it leave it exactly where it grew. */
    private static void rooted(GameTestHelper helper) {
        Pig pig = pig(helper, 11.5, 11.5);
        SnapmawBloom bloom = bloom(helper, 5.5, 5.5, pig);
        Pig attacker = pig(helper, 4.0, 5.5);
        Vec3 start = bloom.position();
        float[] health = {0.0F};
        helper.runAtTickTime(2, () -> {
            health[0] = bloom.getHealth();
            bloom.hurtServer(helper.getLevel(), bloom.damageSources().mobAttack(attacker), 1.0F);
            bloom.knockback(3.0, -1.0, 0.0, bloom.damageSources().mobAttack(attacker), 1.0F);
            bloom.push(1.0, 0.3, 1.0);
            bloom.setDeltaMovement(0.8, 0.0, -0.8);
            helper.getLevel().explode(null, bloom.getX() + 1.5, bloom.getY() + 0.5, bloom.getZ(), 2.0F, Level.ExplosionInteraction.NONE);
        });
        helper.runAtTickTime(25, () -> {
            double moved = Math.hypot(bloom.getX() - start.x, bloom.getZ() - start.z);
            helper.assertTrue(bloom.getHealth() < health[0], "the blows landed, health " + bloom.getHealth());
            helper.assertTrue(moved < 0.01, "it never left its spot, moved " + moved);
            helper.succeed();
        });
    }

    /** The counter in motion: a target that steps aside as the seed leaves is not hit; seeds fly straight. */
    private static void dodge(GameTestHelper helper) {
        Pig pig = pig(helper, 6.5, 6.5);
        SnapmawBloom bloom = bloom(helper, 0.5, 0.5, pig);
        bloom.setNoAi(true);
        SeedLog log = new SeedLog(helper, bloom);
        helper.runAtTickTime(2, () -> bloom.spit(helper.getLevel()));
        helper.runAtTickTime(3, () -> pig.snapTo(pig.getX() + 1.5, pig.getY(), pig.getZ() - 1.5));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(log.launches.size() == 1 && log.hits == 0, "the seed missed, hits " + log.hits);
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "the pig that stepped aside is unhurt");
            log.stop();
            helper.succeed();
        });
    }

    /** A block between them stops the seed. */
    private static void cover(GameTestHelper helper) {
        Pig pig = pig(helper, 2.5, 9.5);
        SnapmawBloom bloom = bloom(helper, 2.5, 2.5, pig);
        bloom.setNoAi(true);
        for (int x = 1; x <= 4; x++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
        SeedLog log = new SeedLog(helper, bloom);
        helper.runAtTickTime(2, () -> bloom.spit(helper.getLevel()));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(log.launches.size() == 1 && bloom.seedsInFlight() == 0, "the seed is spent");
            helper.assertTrue(log.hits == 0 && pig.getHealth() >= pig.getMaxHealth(), "the wall took the seed");
            log.stop();
            helper.succeed();
        });
    }
}
