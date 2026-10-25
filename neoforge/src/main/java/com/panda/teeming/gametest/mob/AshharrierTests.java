package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.Ashharrier;
import com.panda.teeming.mob.nether.Ashharrier.Step;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Ashharrier: the 16-tick cry hangs it in the air and takes no one; the stoop that follows grabs its prey and lifts it
 * 8 to 12 blocks over 40 ticks, then lets go, and the fall does the damage (vanilla's: a block of damage for every block
 * past 3). A hit on it while it carries makes it let go at once, so an early hit means a low, safe fall. A shield raised
 * into the stoop blocks the grab and stuns it on the ground for 40 ticks. It never lifts a creative player, leaves a
 * victim's Slow Falling alone, and rakes prey too big to carry for its attack damage (4).
 *
 * <p>Pigs stand in as prey where the numbers must be exact (no difficulty scaling); they keep their AI but lose their
 * goals, so they fall when let go (a NoAI mob never moves). A shield needs a real survival player who holds it up: the
 * survivor is ticked by hand until the shield is raised. The bird is made blind to other arenas (follow range 1), and the
 * barrier roof over its own arena is opened, since a lift climbs past it.
 */
public final class AshharrierTests {
    private AshharrierTests() {}

    /** Where the prey stands: the middle of the arena floor. */
    private static final Vec3 GROUND = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("ashharrier_tell", AshharrierTests::tell);
        out.accept("ashharrier_counter", AshharrierTests::shieldStuns);
        out.accept("ashharrier_failure", AshharrierTests::liftAndDrop);
        TeemingGameTests.timeLimit("ashharrier_failure", 220);
        out.accept("ashharrier_stun_length", AshharrierTests::stunLength);
        TeemingGameTests.timeLimit("ashharrier_stun_length", 160);
        out.accept("ashharrier_lift_height", AshharrierTests::liftHeight);
        TeemingGameTests.timeLimit("ashharrier_lift_height", 120);
        out.accept("ashharrier_lift_range", AshharrierTests::liftRange);
        out.accept("ashharrier_lift_ticks", AshharrierTests::liftTicks);
        TeemingGameTests.timeLimit("ashharrier_lift_ticks", 120);
        out.accept("ashharrier_hit_early", AshharrierTests::hitEarly);
        TeemingGameTests.timeLimit("ashharrier_hit_early", 120);
        out.accept("ashharrier_hit_late", AshharrierTests::hitLate);
        TeemingGameTests.timeLimit("ashharrier_hit_late", 160);
        out.accept("ashharrier_never_creative", AshharrierTests::neverCreative);
        out.accept("ashharrier_slow_falling", AshharrierTests::slowFalling);
        TeemingGameTests.timeLimit("ashharrier_slow_falling", 260);
        out.accept("ashharrier_rake", AshharrierTests::rake);
        TeemingGameTests.timeLimit("ashharrier_rake", 140);
        out.accept("ashharrier_lift_cancel", AshharrierTests::liftCancel);
        TeemingGameTests.timeLimit("ashharrier_lift_cancel", 140);
        out.accept("ashharrier_death_lets_go", AshharrierTests::deathLetsGo);
        out.accept("ashharrier_stats", AshharrierTests::stats);
        out.accept("ashharrier_home", AshharrierTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** An Ashharrier {@code up} blocks above the arena floor at {@code x}, ready to cry, blind to other arenas. */
    private static Ashharrier harrier(GameTestHelper helper, double x, double up) {
        openRoof(helper);
        Ashharrier bird = helper.spawn(ModMobs.ASHHARRIER.get(), new Vec3(x, 1 + up, 5.5));
        bird.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        bird.resetCooldown();
        TestPlayers.whenDone(helper, bird::discard);
        return bird;
    }

    /** Clears the barrier roof over the arena (the two layers above its 8 rows), so the sky above it is open. */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        for (int y = 2; y <= 26; y++) {
            BlockPos pos = new BlockPos(5, y, 5);
            if (!helper.getBlockState(pos).isAir()) helper.fail("the sky over the arena is shut at y " + y + " by " + helper.getBlockState(pos));
        }
    }

    /** A pig with 100 health that keeps its physics but has no goals: it stands still and falls when let go. */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.removeFreeWill();
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A pig on the floor and an Ashharrier 3 blocks off and {@code up} above it, hunting it. */
    private static Ashharrier overPig(GameTestHelper helper, double up, Pig[] out) {
        Pig pig = pig(helper, GROUND);
        Ashharrier bird = harrier(helper, GROUND.x + 3.0, up);
        bird.setTarget(pig);
        out[0] = pig;
        return bird;
    }

    /**
     * A survival player at x 3.5 holding a shield up toward +x, and an Ashharrier 4 blocks further along x and 8 above,
     * hunting the player. The player is ticked by hand until the shield counts as raised.
     */
    private static Ashharrier overShield(GameTestHelper helper, ServerPlayer[] out) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(3.5, 1, 5.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        Ashharrier bird = harrier(helper, 7.5, 8);
        bird.setTarget(p);
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            p.setYRot(-90.0F);         // facing +x, toward the bird
            p.setYHeadRot(-90.0F);
            p.yHeadRotO = -90.0F;
        });
        out[0] = p;
        return bird;
    }

    /** What the pig lost, from its 100. */
    private static float lost(Pig pig) {
        return 100.0F - pig.getHealth();
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /** 8 above a pig it cries at once, for 16 ticks, hanging in the air and taking no one; the stoop follows. */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Ashharrier bird = overPig(helper, 8, pig);
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] took = {false}, dived = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (bird.core().in(Step.DIVE)) dived[0] = true;
            if (!bird.core().in(Step.TELL)) return;
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = bird.position();
            }
            ticks[0]++;
            took[0] |= bird.victim() != null || Grab.isHeld(pig[0]) || lost(pig[0]) > 0.0F;
            moved[0] = Math.max(moved[0], bird.position().distanceTo(start[0]));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && !bird.core().in(Step.TELL), "the cry runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(first[0] <= 4, "8 above its prey it cries at once, started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 16) <= 1, "the cry lasts 16 ticks, lasted " + ticks[0]);
            helper.assertTrue(!took[0], "the cry takes and hurts no one");
            helper.assertTrue(moved[0] < 1.0, "it hangs in the air while it cries, moved " + moved[0]);
            helper.assertTrue(dived[0], "the stoop follows the cry, phase " + bird.phase());
        });
    }

    /**
     * The counter: a raised shield takes the stoop. The player is neither lifted nor hurt, and the Ashharrier lies
     * stunned with its weak point showing.
     */
    private static void shieldStuns(GameTestHelper helper) {
        ServerPlayer[] p = new ServerPlayer[1];
        Ashharrier bird = overShield(helper, p);
        float before = p[0].getHealth();
        boolean[] dived = {false}, held = {false};
        double[] high = {0.0};
        double startY = p[0].getY();
        helper.onEachTick(() -> {
            dived[0] |= bird.core().in(Step.DIVE);
            held[0] |= bird.victim() != null || p[0].hasEffect(ModEffects.SWALLOWED.holder());
            high[0] = Math.max(high[0], p[0].getY() - startY);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(p[0].isBlocking(), "the player holds the shield up");
            helper.assertTrue(bird.core().in(Step.STUNNED), "a blocked stoop stuns it, phase " + bird.phase());
            helper.assertTrue(dived[0], "it got there by stooping");
            helper.assertTrue(bird.vulnerable(), "stunned, its weak point shows");
            helper.assertTrue(!held[0] && high[0] < 0.5, "the shield blocks the grab: never held, rose " + high[0]);
            helper.assertTrue(p[0].getHealth() >= before, "the shield took it all, health " + p[0].getHealth() + " of " + before);
        });
    }

    /**
     * Failing the counter: the stoop grabs the pig (held, shown as Swallowed), lifts it 8 to 12 blocks and lets go; the
     * fall costs it a point of health for every block past 3, at least 5.
     */
    private static void liftAndDrop(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Ashharrier bird = overPig(helper, 8, pig);
        double[] from = {Double.NaN}, top = {-Double.MAX_VALUE};
        boolean[] cried = {false}, swallowed = {false}, dropped = {false}, landed = {false};
        helper.onEachTick(() -> {
            cried[0] |= bird.core().in(Step.TELL);
            if (bird.victim() == pig[0]) {
                if (Double.isNaN(from[0])) from[0] = pig[0].getY();
                swallowed[0] |= pig[0].hasEffect(ModEffects.SWALLOWED.holder());
            } else if (!Double.isNaN(from[0])) {
                dropped[0] = true;
            }
            if (!Double.isNaN(from[0])) top[0] = Math.max(top[0], pig[0].getY());
            landed[0] |= dropped[0] && pig[0].getY() < from[0] + 0.1;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cried[0], "it cried first");
            helper.assertTrue(!Double.isNaN(from[0]), "the stoop grabs the pig, phase " + bird.phase());
            helper.assertTrue(swallowed[0], "held, the pig shows as Swallowed");
            helper.assertTrue(dropped[0] && landed[0], "it lets go and the pig lands, pig at " + (pig[0].getY() - from[0]));
            helper.assertTrue(!pig[0].hasEffect(ModEffects.SWALLOWED.holder()), "let go, it is no longer held");
            double rise = top[0] - from[0];
            int want = Mth.floor(rise - 3.0);
            helper.assertTrue(rise >= 8.0 - 0.15 && rise <= 12.0 + 0.15, "lifted 8 to 12 blocks, rose " + rise);
            helper.assertTrue(lost(pig[0]) >= 5.0F, "the fall hurts: at least 5, lost " + lost(pig[0]));
            helper.assertTrue(Math.abs(lost(pig[0]) - want) <= 1.0F, "a fall of " + rise + " costs about " + want + ", lost " + lost(pig[0]));
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The stun from a blocked stoop lasts 40 ticks, down on the ground, open to blows at their face value; then it climbs. */
    private static void stunLength(GameTestHelper helper) {
        ServerPlayer[] p = new ServerPlayer[1];
        Ashharrier bird = overShield(helper, p);
        int[] stunned = {0};
        boolean[] over = {false}, grounded = {false}, climbed = {false};
        float[] hit = {-1.0F};
        helper.onEachTick(() -> {
            if (bird.core().in(Step.STUNNED)) {
                stunned[0]++;
                grounded[0] |= bird.onGround();
                if (stunned[0] == 20) {
                    float had = bird.getHealth();
                    bird.hurtServer(helper.getLevel(), bird.damageSources().magic(), 4.0F);
                    hit[0] = had - bird.getHealth();
                }
            } else if (stunned[0] > 0) {
                over[0] = true;
                climbed[0] |= bird.core().in(Step.CLIMB);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the stun ends, " + stunned[0] + " ticks so far, phase " + bird.phase());
            helper.assertTrue(Math.abs(stunned[0] - 40) <= 1, "the stun lasts 40 ticks, lasted " + stunned[0]);
            helper.assertTrue(grounded[0], "it lies on the ground while stunned");
            helper.assertTrue(Math.abs(hit[0] - 4.0F) < 0.01F, "stunned, a blow of 4 costs 4, cost " + hit[0]);
            helper.assertTrue(climbed[0], "it climbs away afterwards, phase " + bird.phase());
        });
    }

    /** Taken straight off the floor, the pig rises between 8 and 12 blocks before it is let go: the height picked. */
    private static void liftHeight(GameTestHelper helper) {
        Pig pig = pig(helper, GROUND);
        Ashharrier bird = harrier(helper, GROUND.x, 3);
        double from = pig.getY();
        helper.assertTrue(bird.grab(pig), "it takes the pig");
        double[] top = {from};
        boolean[] dropped = {false};
        helper.onEachTick(() -> {
            if (bird.victim() == pig) top[0] = Math.max(top[0], pig.getY());
            else dropped[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dropped[0], "it lets go, phase " + bird.phase());
            double rise = top[0] - from;
            helper.assertTrue(rise >= 8.0 - 0.15 && rise <= 12.0 + 0.15, "lifted 8 to 12 blocks, rose " + rise);
            helper.assertTrue(Math.abs(rise - bird.liftHeight()) < 0.15, "lifted the height it picked, " + bird.liftHeight() + ", rose " + rise);
        });
    }

    /** The lift's height is picked evenly from 8 to 12: never outside, and both ends reached. */
    private static void liftRange(GameTestHelper helper) {
        RandomSource random = RandomSource.create(44L);
        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE, sum = 0.0;
        int n = 4000;
        for (int i = 0; i < n; i++) {
            double h = Ashharrier.rollLift(random);
            low = Math.min(low, h);
            high = Math.max(high, h);
            sum += h;
        }
        helper.assertTrue(low >= 8.0 && high <= 12.0, "lifts stay within 8 to 12, saw " + low + " to " + high);
        helper.assertTrue(low < 8.05 && high > 11.95, "both ends of 8 to 12 are reached, saw " + low + " to " + high);
        helper.assertTrue(Math.abs(sum / n - 10.0) < 0.1, "evenly spread, mean " + sum / n);
        helper.succeed();
    }

    /** The lift runs 40 ticks from the grab to letting go, an even climb: halfway through, half the height. */
    private static void liftTicks(GameTestHelper helper) {
        Pig pig = pig(helper, GROUND);
        Ashharrier bird = harrier(helper, GROUND.x, 3);
        double from = pig.getY();
        helper.assertTrue(bird.grab(pig), "it takes the pig");
        int[] held = {0};
        double[] half = {Double.NaN};
        helper.onEachTick(() -> {
            if (bird.victim() != pig) return;
            held[0]++;
            if (held[0] == 20) half[0] = pig.getY() - from;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bird.victim() == null && held[0] > 0, "it lets go, held " + held[0] + " ticks so far");
            helper.assertTrue(Math.abs(held[0] - 40) <= 1, "the lift lasts 40 ticks, lasted " + held[0]);
            double want = bird.liftHeight() / 2.0;
            helper.assertTrue(Math.abs(half[0] - want) < 0.6, "halfway, half of " + bird.liftHeight() + " (" + want + "), rose " + half[0]);
        });
    }

    /**
     * Hitting it early: 5 ticks into the lift the pig strikes it. It lets go that same tick, from barely a block up, and
     * the pig lands unhurt.
     */
    private static void hitEarly(GameTestHelper helper) {
        Pig pig = pig(helper, GROUND);
        Ashharrier bird = harrier(helper, GROUND.x, 3);
        double from = pig.getY();
        helper.assertTrue(bird.grab(pig), "it takes the pig");
        int[] held = {0};
        boolean[] hit = {false}, freed = {false};
        double[] at = {Double.NaN};
        helper.onEachTick(() -> {
            if (bird.victim() == pig) held[0]++;
            if (!hit[0] && held[0] == 5) {
                hit[0] = bird.hurtServer(helper.getLevel(), bird.damageSources().mobAttack(pig), 1.0F);
                freed[0] = bird.victim() == null && !Grab.isHeld(pig);
                at[0] = pig.getY() - from;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hit[0], "the blow lands");
            helper.assertTrue(freed[0], "it lets go at once, the same tick");
            helper.assertTrue(at[0] < 2.0, "early means low: let go " + at[0] + " up");
            helper.assertTrue(pig.onGround() || pig.getY() < from + 0.05, "the pig is down, at " + (pig.getY() - from));
            helper.assertTrue(lost(pig) == 0.0F, "a low fall is safe, lost " + lost(pig));
            helper.assertTrue(bird.core().in(Step.CLIMB), "it climbs away, phase " + bird.phase());
        });
    }

    /**
     * Hitting it late: 30 ticks into the lift. It lets go at once, from three quarters of its height: the pig takes the
     * fall from there (a point for every block past 3), less than the whole lift would have cost.
     */
    private static void hitLate(GameTestHelper helper) {
        Pig pig = pig(helper, GROUND);
        Ashharrier bird = harrier(helper, GROUND.x, 3);
        double from = pig.getY();
        helper.assertTrue(bird.grab(pig), "it takes the pig");
        int[] held = {0};
        boolean[] hit = {false}, freed = {false};
        double[] at = {Double.NaN};
        helper.onEachTick(() -> {
            if (bird.victim() == pig) held[0]++;
            if (!hit[0] && held[0] == 30) {
                hit[0] = bird.hurtServer(helper.getLevel(), bird.damageSources().mobAttack(pig), 1.0F);
                freed[0] = bird.victim() == null;
                at[0] = pig.getY() - from;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hit[0] && freed[0], "the blow lands and it lets go at once");
            helper.assertTrue(pig.getY() < from + 0.05 && lost(pig) > 0.0F, "the pig landed hurt, at " + (pig.getY() - from) + ", lost " + lost(pig));
            double share = at[0] / bird.liftHeight();
            helper.assertTrue(Math.abs(share - 0.75) < 0.06, "let go at three quarters of the lift, " + at[0] + " of " + bird.liftHeight());
            int want = Mth.floor(at[0] - 3.0);
            helper.assertTrue(Math.abs(lost(pig) - want) <= 1.0F, "a fall of " + at[0] + " costs about " + want + ", lost " + lost(pig));
            helper.assertTrue(lost(pig) < Mth.floor(bird.liftHeight() - 3.0), "less than the whole lift would cost, lost " + lost(pig));
        });
    }

    /** A creative player is never lifted: not prey, and the grab refuses it. */
    private static void neverCreative(GameTestHelper helper) {
        Player mock = helper.makeMockServerPlayerInLevel();
        mock.snapTo(helper.absoluteVec(GROUND));
        Ashharrier bird = harrier(helper, GROUND.x, 3);
        helper.assertTrue(mock.isCreative(), "the mock player is creative");
        helper.assertTrue(!bird.canLift(mock), "a creative player is not prey");
        helper.assertTrue(!bird.grab(mock), "the grab refuses it");
        helper.assertTrue(bird.victim() == null && !mock.hasEffect(ModEffects.SWALLOWED.holder()), "nothing is held");
        helper.succeed();
    }

    /** It leaves a victim's Slow Falling alone: lifted the whole way and let go, a pig with it lands unhurt. */
    private static void slowFalling(GameTestHelper helper) {
        Pig pig = pig(helper, GROUND);
        pig.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600));
        Ashharrier bird = harrier(helper, GROUND.x, 3);
        double from = pig.getY();
        helper.assertTrue(bird.grab(pig), "it takes the pig");
        double[] top = {from};
        boolean[] dropped = {false};
        helper.onEachTick(() -> {
            if (bird.victim() == pig) top[0] = Math.max(top[0], pig.getY());
            else dropped[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dropped[0] && top[0] - from >= 8.0 - 0.15, "lifted the whole way and let go, rose " + (top[0] - from));
            helper.assertTrue(pig.hasEffect(MobEffects.SLOW_FALLING), "its Slow Falling is left on it");
            helper.assertTrue(pig.getY() < from + 0.05, "the pig is down, at " + (pig.getY() - from));
            helper.assertTrue(lost(pig) == 0.0F, "it lands unhurt, lost " + lost(pig));
        });
    }

    /** Prey too big to carry (an iron golem, 2.7 tall) is raked by the stoop for its attack damage, 4, and never lifted. */
    private static void rake(GameTestHelper helper) {
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, GROUND);
        golem.setNoAi(true);
        TestPlayers.whenDone(helper, golem::discard);
        Ashharrier bird = harrier(helper, GROUND.x + 3.0, 8);
        bird.setTarget(golem);
        float before = golem.getHealth();
        boolean[] held = {false}, dived = {false}, climbed = {false};
        helper.onEachTick(() -> {
            dived[0] |= bird.core().in(Step.DIVE);
            climbed[0] |= dived[0] && bird.core().in(Step.CLIMB);
            held[0] |= bird.victim() != null || bird.core().in(Step.LIFT);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dived[0] && climbed[0], "it stooped and climbed away, phase " + bird.phase());
            float lost = before - golem.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F) < 0.01F, "the talons rake for its attack, 4, lost " + lost);
            helper.assertTrue(!held[0], "too big to carry: never lifted");
        });
    }

    /** A listener that cancels the "lift" step spares the prey: the stoop takes no one and it climbs away. */
    private static void liftCancel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Ashharrier bird = overPig(helper, 8, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == bird && e.step().equals("lift")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] dived = {false}, climbed = {false}, held = {false};
        helper.onEachTick(() -> {
            dived[0] |= bird.core().in(Step.DIVE);
            climbed[0] |= dived[0] && bird.core().in(Step.CLIMB);
            held[0] |= bird.victim() != null || Grab.isHeld(pig[0]);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dived[0] && climbed[0], "it stooped and climbed away, phase " + bird.phase());
            helper.assertTrue(!held[0], "a cancelled lift takes no one");
            helper.assertTrue(lost(pig[0]) == 0.0F, "the pig is unhurt, lost " + lost(pig[0]));
        });
    }

    /** Killed while it carries, it lets go: the pig is free and no longer shown as held. */
    private static void deathLetsGo(GameTestHelper helper) {
        Pig pig = pig(helper, GROUND);
        Ashharrier bird = harrier(helper, GROUND.x, 3);
        helper.assertTrue(bird.grab(pig), "it takes the pig");
        helper.runAtTickTime(6, () -> bird.hurtServer(helper.getLevel(), bird.damageSources().magic(), 1000.0F));
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(!bird.isAlive(), "it is dead");
            helper.assertTrue(!Grab.isHeld(pig) && !pig.hasEffect(ModEffects.SWALLOWED.holder()), "the pig is let go");
            helper.succeed();
        });
    }

    /** Health 24, attack 4, armour 0, a 1.4 x 0.8 hitbox; outside a stun nothing shows gold. */
    private static void stats(GameTestHelper helper) {
        Ashharrier bird = helper.spawn(ModMobs.ASHHARRIER.get(), GROUND);
        bird.removeFreeWill();
        TestPlayers.whenDone(helper, bird::discard);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(bird.getMaxHealth() - 24.0F) < 0.01F && Math.abs(bird.getHealth() - 24.0F) < 0.01F,
                    "health 24, got " + bird.getHealth() + "/" + bird.getMaxHealth());
            helper.assertTrue(Math.abs(bird.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4");
            helper.assertTrue(Math.abs(bird.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(bird.getBbWidth() - 1.4F) < 0.02F && Math.abs(bird.getBbHeight() - 0.8F) < 0.02F,
                    "hitbox 1.4 x 0.8, got " + bird.getBbWidth() + " x " + bird.getBbHeight());
            helper.assertTrue(!bird.vulnerable(), "not stunned, no weak point");
            helper.assertTrue(bird.fireImmune(), "fire and lava never burn it");
            helper.succeed();
        });
    }

    /** Its home: the basalt deltas only, weight 10, alone (groups of 1). */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("ashharrier"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(bi -> bi.is(Biomes.BASALT_DELTAS)), "its home holds the basalt deltas");
        helper.assertTrue(tag.get().stream().noneMatch(bi -> bi.is(Biomes.NETHER_WASTES)), "its home never holds the nether wastes");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/ashharrier")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.ASHHARRIER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 10, "spawn weight 10, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
