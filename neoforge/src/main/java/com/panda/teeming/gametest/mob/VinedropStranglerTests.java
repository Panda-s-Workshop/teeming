package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.VinedropStrangler;
import com.panda.teeming.mob.overworld.VinedropStrangler.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.SpawnTable;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Vinedrop Strangler: hanging under leaves with prey right below it (up to 12 blocks down) it shows its shadow for 12
 * ticks and does nothing else, then drops at 1 block a tick and wraps its prey: Slowness IV and 1 damage every 20 ticks
 * for 80 ticks. Shears, or a fully charged sword swing made standing on the ground, cut it free at once: it is thrown
 * off and stunned for 60 ticks, taking x1.5. Afterwards it climbs back up to the leaves and waits 60 ticks before the
 * next drop.
 *
 * <p>Every strangler here has its goals cleared and a follow range of 0, so it never notices a survival player in a
 * neighbouring arena; targets are given to it directly. Its phase machine still runs. Leaves are placed persistent, so
 * they never decay mid-test. Pigs are tough (1000 health) and stand in where numbers must be exact (no difficulty
 * scaling); a real survival player stands in for the counters, which only players can use.
 */
public final class VinedropStranglerTests {
    private VinedropStranglerTests() {}

    private static final BlockState LEAVES = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    /** The arena floor's middle, where prey stands. */
    private static final Vec3 GROUND = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("vinedrop_strangler_tell", VinedropStranglerTests::tell);
        out.accept("vinedrop_strangler_counter", VinedropStranglerTests::shearsCut);
        out.accept("vinedrop_strangler_failure", VinedropStranglerTests::squeezedForTheWholeWrap);
        TeemingGameTests.timeLimit("vinedrop_strangler_failure", 200);
        out.accept("vinedrop_strangler_sweep", VinedropStranglerTests::sweepCuts);
        out.accept("vinedrop_strangler_shears_hit", VinedropStranglerTests::shearsHit);
        out.accept("vinedrop_strangler_squeeze_numbers", VinedropStranglerTests::squeezeNumbers);
        TeemingGameTests.timeLimit("vinedrop_strangler_squeeze_numbers", 140);
        out.accept("vinedrop_strangler_stun_length", VinedropStranglerTests::stunLength);
        TeemingGameTests.timeLimit("vinedrop_strangler_stun_length", 140);
        out.accept("vinedrop_strangler_drop_height", VinedropStranglerTests::dropHeight);
        out.accept("vinedrop_strangler_sidestep", VinedropStranglerTests::sidestep);
        TeemingGameTests.timeLimit("vinedrop_strangler_sidestep", 160);
        out.accept("vinedrop_strangler_cooldown", VinedropStranglerTests::cooldown);
        TeemingGameTests.timeLimit("vinedrop_strangler_cooldown", 240);
        out.accept("vinedrop_strangler_creep", VinedropStranglerTests::creep);
        TeemingGameTests.timeLimit("vinedrop_strangler_creep", 140);
        out.accept("vinedrop_strangler_climbs_up", VinedropStranglerTests::climbsUp);
        out.accept("vinedrop_strangler_threads_down", VinedropStranglerTests::threadsDown);
        out.accept("vinedrop_strangler_lose_grip", VinedropStranglerTests::loseGrip);
        out.accept("vinedrop_strangler_frees_on_death", VinedropStranglerTests::freesOnDeath);
        out.accept("vinedrop_strangler_frees_on_removal", VinedropStranglerTests::freesOnRemoval);
        out.accept("vinedrop_strangler_escape", VinedropStranglerTests::escape);
        out.accept("vinedrop_strangler_squeeze_cancel", VinedropStranglerTests::squeezeCancel);
        out.accept("vinedrop_strangler_stats", VinedropStranglerTests::stats);
        out.accept("vinedrop_strangler_home", VinedropStranglerTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Persistent jungle leaves over x0..x1, z0..z1 at height y (relative). */
    private static void leaves(GameTestHelper helper, int x0, int x1, int y, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) helper.setBlock(new BlockPos(x, y, z), LEAVES);
        }
    }

    /** A strangler in its base look (1.2 tall) with no goals and no sight range of its own; removed when the test ends. */
    private static VinedropStrangler strangler(GameTestHelper helper, Vec3 at) {
        VinedropStrangler s = helper.spawn(ModMobs.VINEDROP_STRANGLER.get(), at);
        s.core().setLook("base", true);
        s.removeFreeWill();
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        TestPlayers.whenDone(helper, s::discard);
        return s;
    }

    /** A strangler hanging under the leaf block at {@code leaf} (relative), which must already be there. */
    private static VinedropStrangler hung(GameTestHelper helper, BlockPos leaf) {
        VinedropStrangler s = strangler(helper, new Vec3(leaf.getX() + 0.5, 1, leaf.getZ() + 0.5));
        helper.assertTrue(s.hangUnder(helper.absolutePos(leaf)), "it hangs under the leaves at " + leaf);
        return s;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        return pig;
    }

    /** Clears the barrier roof over the arena and checks the sky over the given columns is open up to y 17. */
    private static void openRoof(GameTestHelper helper, int... columns) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        for (int x : columns) {
            for (int y = 2; y <= 17; y++) {
                BlockPos pos = new BlockPos(x, y, 5);
                if (!helper.getBlockState(pos).isAir()) helper.fail("the sky over the arena is shut at x " + x + ", y " + y + " by " + helper.getBlockState(pos));
            }
        }
    }

    /** Records the strangler's own tick count at each mechanic step it posts. */
    private static void steps(GameTestHelper helper, VinedropStrangler s, Map<String, List<Integer>> at) {
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == s) at.computeIfAbsent(e.step(), k -> new ArrayList<>()).add(s.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
    }

    /** Records where the strangler is each time it starts to hang. */
    private static List<Vec3> hangSpots(GameTestHelper helper, VinedropStrangler s) {
        List<Vec3> spots = new ArrayList<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == s && e.step().equals("hang")) spots.add(s.position());
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return spots;
    }

    private static int first(Map<String, List<Integer>> at, String step) {
        return at.getOrDefault(step, List.of(-1)).getFirst();
    }

    private static boolean slowed(LivingEntity e) {
        MobEffectInstance slow = e.getEffect(MobEffects.SLOWNESS);
        return slow != null && slow.getAmplifier() == 3;
    }

    private static void moveTo(GameTestHelper helper, Entity e, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        if (e instanceof ServerPlayer p) p.snapTo(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        else e.teleportTo(at.x, at.y, at.z);
        e.setDeltaMovement(Vec3.ZERO);
    }

    /** A squeeze on a player scales with difficulty: 1 is 1 on Easy and Normal, 1.5 on Hard. */
    private static float squeezeOnPlayer(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case HARD -> 1.5F;
            default -> 1.0F;
        };
    }

    // ---- the tell, the counter, failing it -------------------------------------------------------------------------

    /**
     * The tell: hanging 3.8 blocks over a pig, it starts its shadow at once. The shadow lasts 12 ticks from its start to
     * the drop, it hangs still meanwhile, and the pig is neither wrapped, slowed nor hurt. Then it drops.
     */
    private static void tell(GameTestHelper helper) {
        leaves(helper, 5, 5, 6, 5, 5);
        VinedropStrangler s = hung(helper, new BlockPos(5, 6, 5));
        Pig pig = pig(helper, GROUND);
        Map<String, List<Integer>> at = new HashMap<>();
        steps(helper, s, at);
        s.setTarget(pig);
        long[] began = {-1};
        boolean[] early = {false};
        double[] moved = {0.0};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            if (!s.core().in(Step.TELL)) return;
            if (began[0] < 0) {
                began[0] = helper.getTick();
                start[0] = s.position();
            }
            early[0] |= s.victim() != null || slowed(pig) || pig.getHealth() < pig.getMaxHealth();
            moved[0] = Math.max(moved[0], s.position().distanceTo(start[0]));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.containsKey("tell") && at.containsKey("drop"), "it showed its shadow and dropped: " + at.keySet());
            int shadow = first(at, "drop") - first(at, "tell");
            helper.assertTrue(shadow == 12, "the shadow lasts 12 ticks, measured " + shadow);
            helper.assertTrue(began[0] <= 3, "prey right below sets it off at once, it began at tick " + began[0]);
            helper.assertTrue(!early[0], "nothing happens to the pig while the shadow shows");
            helper.assertTrue(moved[0] < 1.0E-3, "it hangs still through the tell, moved " + moved[0]);
        });
    }

    /**
     * The counter: a survival player wrapped up (slowed) uses shears on it. The player is freed at once (no longer
     * slowed), the shears lose 1 durability, and the strangler is thrown off and stunned, its weak point showing, taking
     * x1.5 from a blow.
     */
    private static void shearsCut(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, GROUND);
        VinedropStrangler s = strangler(helper, GROUND);
        ItemStack shears = new ItemStack(Items.SHEARS);
        helper.runAtTickTime(2, () -> helper.assertTrue(s.wrap(p), "it wraps the player"));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(s.victim() == p && s.core().in(Step.WRAP), "still wrapped round the player, phase " + s.phase());
            helper.assertTrue(slowed(p), "the player is slowed (Slowness IV)");
            p.setItemInHand(InteractionHand.MAIN_HAND, shears);
            InteractionResult result = s.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(result.consumesAction(), "the shears take, got " + result);
            helper.assertTrue(s.victim() == null && !slowed(p), "the player is freed at once and no longer slowed");
            helper.assertTrue(s.core().in(Step.STUNNED) && s.vulnerable(), "it is stunned, weak point showing, phase " + s.phase());
            helper.assertTrue(shears.getDamageValue() == 1, "the shears lose 1 durability, " + shears.getDamageValue());
        });
        helper.runAtTickTime(18, () -> {
            double off = s.position().subtract(p.position()).horizontalDistance();
            helper.assertTrue(off > 0.5, "it was thrown off the player, " + off + " away");
            float had = s.getHealth();
            s.hurtServer(helper.getLevel(), s.damageSources().magic(), 4.0F);
            float lost = had - s.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "stunned, it takes x1.5: 4 became " + lost);
            helper.succeed();
        });
    }

    /**
     * Failing the counter: hanging 3.8 blocks over a survival player it drops and wraps them. They stay slowed for the
     * 80 ticks of the wrap and are squeezed at 20, 40, 60 and 80 ticks (1 each on Easy and Normal, 1.5 on Hard); then it
     * lets go (no longer slowed) and climbs back up to the spot under the leaves it dropped from.
     */
    private static void squeezedForTheWholeWrap(GameTestHelper helper) {
        leaves(helper, 4, 6, 6, 4, 6);
        VinedropStrangler s = hung(helper, new BlockPos(5, 6, 5));
        Vec3 spot = s.position();
        ServerPlayer p = TestPlayers.survivor(helper, GROUND);
        Map<String, List<Integer>> at = new HashMap<>();
        steps(helper, s, at);
        List<Vec3> spots = hangSpots(helper, s);
        s.setTarget(p);
        float before = p.getHealth();
        float each = squeezeOnPlayer(helper);
        boolean[] unslowed = {false}, strayed = {false}, released = {false};
        helper.onEachTick(() -> {
            if (s.core().in(Step.WRAP)) {
                unslowed[0] |= !slowed(p);
                strayed[0] |= s.position().distanceTo(p.position()) > 1.0E-3;
            } else if (at.containsKey("wrap") && at.containsKey("climb") && !released[0]) {
                released[0] = true;
                helper.assertTrue(!slowed(p) && s.victim() == null, "once it lets go the player is no longer slowed");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.containsKey("wrap") && at.containsKey("hang") && s.core().in(Step.HANG) && released[0],
                    "it wrapped, let go and climbed back, phase " + s.phase() + ", steps " + at.keySet());
            int wrapped = first(at, "wrap");
            List<Integer> squeezes = at.getOrDefault("squeeze", List.of()).stream().map(t -> t - wrapped).toList();
            helper.assertTrue(squeezes.equals(List.of(20, 40, 60, 80)), "squeezed at 20, 40, 60 and 80 ticks, saw " + squeezes);
            int held = first(at, "climb") - wrapped;
            helper.assertTrue(held == 80, "it holds on for 80 ticks, held " + held);
            helper.assertTrue(!unslowed[0], "the player stays slowed through the whole wrap");
            helper.assertTrue(!strayed[0], "it stays coiled round the player the whole time");
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F * each) < 0.01F, "4 squeezes of " + each + " here: lost " + lost);
            helper.assertTrue(!spots.isEmpty() && spots.getLast().distanceTo(spot) < 0.01, "it is back where it hung, " + spots + " vs " + spot);
        });
    }

    // ---- the counters in detail --------------------------------------------------------------------------------------

    /**
     * A sword swing that sweeps cuts it free: the wrapped player hits it with a diamond sword. Swung at once after
     * drawing the sword (barely charged) it stays on; fully charged, standing on the floor, it is cut free and stunned.
     */
    private static void sweepCuts(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, GROUND);
        VinedropStrangler s = strangler(helper, GROUND);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.wrap(p), "it wraps the player");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            p.doTick();                // the sword counts from the player's next tick, and drawing it resets the swing
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(p.getAttackStrengthScale(0.5F) < 0.5F, "the swing is barely charged, " + p.getAttackStrengthScale(0.5F));
            p.attack(s);
            helper.assertTrue(s.victim() == p && s.core().in(Step.WRAP), "a weak swing doesn't cut it free, phase " + s.phase());
            helper.assertTrue(slowed(p), "the player is still slowed");
            for (int i = 0; i < 20; i++) p.doTick();
            helper.assertTrue(p.getAttackStrengthScale(0.5F) >= 0.9F, "now the swing is charged, " + p.getAttackStrengthScale(0.5F));
            p.attack(s);
            helper.assertTrue(s.victim() == null && !slowed(p), "a sweeping swing frees the player at once");
            helper.assertTrue(s.core().in(Step.STUNNED) && s.vulnerable(), "and it is stunned, phase " + s.phase());
            helper.succeed();
        });
    }

    /**
     * Shears also cut it free when the player hits it with them. Nothing else does: a fully charged punch and a blow from
     * another mob leave it wrapped round its victim.
     */
    private static void shearsHit(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, GROUND);
        VinedropStrangler s = strangler(helper, GROUND);
        Pig other = pig(helper, GROUND.add(2.0, 0, 0));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.wrap(p), "it wraps the player");
            for (int i = 0; i < 20; i++) p.doTick();
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(p.getAttackStrengthScale(0.5F) >= 0.9F, "the punch is charged");
            p.attack(s);
            helper.assertTrue(s.victim() == p && s.core().in(Step.WRAP), "a punch doesn't cut it free, phase " + s.phase());
            s.damageCooldownTime = 0;
            s.hurtServer(helper.getLevel(), s.damageSources().mobAttack(other), 1.0F);
            helper.assertTrue(s.victim() == p && s.core().in(Step.WRAP), "nor does another mob's blow, phase " + s.phase());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            s.damageCooldownTime = 0;
            p.attack(s);
            helper.assertTrue(s.victim() == null && !slowed(p), "a hit with shears frees the player at once");
            helper.assertTrue(s.core().in(Step.STUNNED), "and it is stunned, phase " + s.phase());
            helper.succeed();
        });
    }

    // ---- the numbers -------------------------------------------------------------------------------------------------

    /**
     * On a pig (no difficulty scaling, no armour): each squeeze is exactly 1, at 20, 40, 60 and 80 ticks into the wrap;
     * the pig carries Slowness IV for the 80 ticks of the wrap and not after.
     */
    private static void squeezeNumbers(GameTestHelper helper) {
        leaves(helper, 3, 8, 6, 3, 8);
        VinedropStrangler s = strangler(helper, GROUND);
        Pig pig = pig(helper, GROUND);
        Map<String, List<Integer>> at = new HashMap<>();
        steps(helper, s, at);
        List<Float> squeezes = new ArrayList<>();
        float[] last = {pig.getHealth()};
        int[] slowTicks = {0};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.wrap(pig), "it wraps the pig");
            MobEffectInstance slow = pig.getEffect(MobEffects.SLOWNESS);
            helper.assertTrue(slow != null && slow.getAmplifier() == 3 && slow.getDuration() == 80,
                    "Slowness IV for 80 ticks, got " + slow);
        });
        helper.onEachTick(() -> {
            float now = pig.getHealth();
            if (now < last[0]) squeezes.add(last[0] - now);
            last[0] = now;
            if (slowed(pig)) slowTicks[0]++;
        });
        helper.runAtTickTime(2 + 80 + 4, () -> {
            int wrapped = first(at, "wrap");
            List<Integer> times = at.getOrDefault("squeeze", List.of()).stream().map(t -> t - wrapped).toList();
            helper.assertTrue(times.equals(List.of(20, 40, 60, 80)), "squeezes 20 ticks apart, the first 20 in, saw " + times);
            helper.assertTrue(squeezes.size() == 4, "4 squeezes hurt the pig, saw " + squeezes);
            for (float q : squeezes) helper.assertTrue(Math.abs(q - 1.0F) < 0.01F, "each squeeze does 1: " + squeezes);
            helper.assertTrue(Math.abs(slowTicks[0] - 80) <= 1, "slowed for 80 ticks, " + slowTicks[0]);
            helper.assertTrue(!slowed(pig) && s.victim() == null, "and free afterwards");
            helper.succeed();
        });
    }

    /** Cut free, it lies stunned for exactly 60 ticks; then it climbs back up to the leaves overhead. */
    private static void stunLength(GameTestHelper helper) {
        leaves(helper, 2, 9, 6, 2, 9);
        VinedropStrangler s = strangler(helper, GROUND);
        Pig pig = pig(helper, GROUND);
        Map<String, List<Integer>> at = new HashMap<>();
        steps(helper, s, at);
        boolean[] grounded = {false};
        helper.runAtTickTime(2, () -> helper.assertTrue(s.wrap(pig), "it wraps the pig"));
        helper.runAtTickTime(4, () -> helper.assertTrue(s.cutFree(null), "it is cut free"));
        helper.onEachTick(() -> grounded[0] |= s.core().in(Step.STUNNED) && s.onGround());
        helper.succeedWhen(() -> {
            helper.assertTrue(at.containsKey("stunned") && at.containsKey("climb"), "stunned, then climbing: " + at.keySet());
            int stunned = first(at, "climb") - first(at, "stunned");
            helper.assertTrue(stunned == 60, "the stun lasts 60 ticks, lasted " + stunned);
            helper.assertTrue(grounded[0], "it lies on the ground while stunned");
            helper.assertTrue(!s.vulnerable(), "climbing, it no longer shows its weak point");
        });
    }

    /**
     * It drops from up to 12 blocks: under the open sky, one strangler hangs 11.8 blocks over its pig and starts its
     * shadow at once; another hangs 12.8 blocks over its own pig and never does.
     */
    private static void dropHeight(GameTestHelper helper) {
        openRoof(helper, 3, 8);
        helper.setBlock(new BlockPos(3, 14, 5), LEAVES);
        helper.setBlock(new BlockPos(8, 15, 5), LEAVES);
        VinedropStrangler near = hung(helper, new BlockPos(3, 14, 5));
        VinedropStrangler far = hung(helper, new BlockPos(8, 15, 5));
        Pig a = pig(helper, new Vec3(3.5, 1, 5.5));
        Pig b = pig(helper, new Vec3(8.5, 1, 5.5));
        near.setTarget(a);
        far.setTarget(b);
        boolean[] nearTold = {false}, farTold = {false};
        helper.onEachTick(() -> {
            nearTold[0] |= near.core().in(Step.TELL);
            farTold[0] |= far.core().in(Step.TELL);
        });
        helper.runAtTickTime(30, () -> {
            double nearUp = near.hangPos().y - a.getY(), farUp = far.hangPos().y - b.getY();
            helper.assertTrue(Math.abs(nearUp - 11.8) < 0.01 && Math.abs(farUp - 12.8) < 0.01, "hung 11.8 and 12.8 up, got " + nearUp + ", " + farUp);
            helper.assertTrue(nearTold[0], "11.8 blocks over its prey it shows its shadow, phase " + near.phase());
            helper.assertTrue(!farTold[0] && far.core().in(Step.HANG), "12.8 blocks over its prey it waits, phase " + far.phase());
            helper.succeed();
        });
    }

    /**
     * Stepping out of the shadow: the pig is moved 4 blocks aside as the shadow starts. The strangler drops straight down
     * at 1 block a tick all the same, lands on nothing (the pig unhurt and unslowed), gets up for 20 ticks and climbs back
     * up to where it hung.
     */
    private static void sidestep(GameTestHelper helper) {
        openRoof(helper, 5);
        helper.setBlock(new BlockPos(5, 14, 5), LEAVES);
        VinedropStrangler s = hung(helper, new BlockPos(5, 14, 5));
        Vec3 spot = s.position();
        Pig pig = pig(helper, GROUND);
        Map<String, List<Integer>> at = new HashMap<>();
        steps(helper, s, at);
        List<Vec3> spots = hangSpots(helper, s);
        s.setTarget(pig);
        List<Double> fall = new ArrayList<>();
        Vec3[] last = {null};
        boolean[] stepped = {false}, wrapped = {false};
        helper.onEachTick(() -> {
            if (s.core().in(Step.TELL) && !stepped[0]) {
                moveTo(helper, pig, GROUND.add(4.0, 0, 0));
                stepped[0] = true;
            }
            wrapped[0] |= s.core().in(Step.WRAP);
            if (s.core().in(Step.DROP)) {
                if (last[0] != null) fall.add(last[0].y - s.getY());
                last[0] = s.position();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepped[0] && at.containsKey("recover") && at.containsKey("hang") && s.core().in(Step.HANG),
                    "it dropped, missed, got up and climbed back, phase " + s.phase() + ", steps " + at.keySet());
            helper.assertTrue(fall.size() >= 8, "it fell for at least 8 ticks, " + fall.size());
            double mean = fall.subList(0, fall.size() - 1).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - 1.0) < 0.01, "it drops 1 block a tick, mean " + mean + " over " + fall);
            int down = at.get("climb").getFirst() - first(at, "recover");
            helper.assertTrue(down == 20, "it gets up for 20 ticks, " + down);
            helper.assertTrue(!wrapped[0] && !slowed(pig) && pig.getHealth() >= pig.getMaxHealth(), "the pig that stepped aside is untouched");
            helper.assertTrue(!spots.isEmpty() && spots.getLast().distanceTo(spot) < 0.01, "it is back where it hung, " + spots + " vs " + spot);
        });
    }

    /** Back under the leaves after a wrap, it waits 60 ticks before its next shadow over the same prey. */
    private static void cooldown(GameTestHelper helper) {
        leaves(helper, 4, 6, 6, 4, 6);
        VinedropStrangler s = strangler(helper, GROUND);
        Pig pig = pig(helper, GROUND);
        Map<String, List<Integer>> at = new HashMap<>();
        steps(helper, s, at);
        helper.runAtTickTime(2, () -> {
            s.setTarget(pig);
            helper.assertTrue(s.wrap(pig), "it wraps the pig");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.containsKey("hang") && at.containsKey("tell"), "it climbed back and dropped again: " + at.keySet());
            int wait = first(at, "tell") - first(at, "hang");
            helper.assertTrue(wait == 60, "60 ticks from reaching the leaves to the next shadow, " + wait);
        });
    }

    /**
     * It creeps along the underside of the leaves at 0.08 blocks a tick toward prey up to 8 blocks across, and drops on
     * it once it is within a block.
     */
    private static void creep(GameTestHelper helper) {
        leaves(helper, 2, 9, 6, 5, 5);
        VinedropStrangler s = hung(helper, new BlockPos(3, 6, 5));
        Pig pig = pig(helper, new Vec3(7.5, 1, 5.5));
        s.setTarget(pig);
        List<Double> steps = new ArrayList<>();
        double[] last = {s.getX()};
        double[] toldAt = {Double.NaN};
        helper.onEachTick(() -> {
            if (s.core().in(Step.HANG)) {
                double moved = s.getX() - last[0];
                if (moved > 1.0E-4) steps.add(moved);
            }
            if (Double.isNaN(toldAt[0]) && s.core().in(Step.TELL)) toldAt[0] = Math.abs(pig.getX() - s.getX());
            last[0] = s.getX();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(s.victim() == pig, "it crept over the pig and wrapped it, phase " + s.phase());
            helper.assertTrue(steps.size() >= 20, "it crept for at least 20 ticks, " + steps.size());
            double mean = steps.subList(0, steps.size() - 1).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - 0.08) < 0.005, "it creeps 0.08 blocks a tick, mean " + mean);
            helper.assertTrue(toldAt[0] <= 1.0 + 1.0E-6 && toldAt[0] > 0.9, "its shadow starts once the pig is within a block across, at " + toldAt[0]);
        });
    }

    /** On the floor under leaves with nothing to hunt, it climbs up and hangs under them (1.2 below the leaf block). */
    private static void climbsUp(GameTestHelper helper) {
        leaves(helper, 4, 6, 6, 4, 6);
        VinedropStrangler s = strangler(helper, GROUND);
        helper.succeedWhen(() -> {
            helper.assertTrue(s.core().in(Step.HANG), "it climbed up to hang, phase " + s.phase());
            double y = s.getY() - helper.absoluteVec(GROUND).y;
            helper.assertTrue(Math.abs(y - 3.8) < 0.01, "it hangs with its top against the leaves, " + y + " up");
        });
    }

    /** Standing on top of a leaf platform with nothing to hunt, it threads itself down through it to hang underneath. */
    private static void threadsDown(GameTestHelper helper) {
        leaves(helper, 4, 6, 3, 4, 6);
        VinedropStrangler s = strangler(helper, new Vec3(5.5, 4, 5.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(s.core().in(Step.HANG), "it threaded down to hang, phase " + s.phase());
            double y = s.getY() - helper.absoluteVec(GROUND).y;
            helper.assertTrue(Math.abs(y - 0.8) < 0.01, "it hangs under the leaves it stood on, " + y + " up");
        });
    }

    /** Hurt while it hangs, it lets go and falls to the floor (from where it may later climb back). */
    private static void loseGrip(GameTestHelper helper) {
        leaves(helper, 5, 5, 6, 5, 5);
        VinedropStrangler s = hung(helper, new BlockPos(5, 6, 5));
        Pig other = pig(helper, GROUND.add(3.0, 0, 0));
        boolean[] hurt = {false}, landed = {false};
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(s.core().in(Step.HANG), "it hangs, phase " + s.phase());
            s.hurtServer(helper.getLevel(), s.damageSources().mobAttack(other), 1.0F);
            helper.assertTrue(s.core().idle(), "hurt, it lets go, phase " + s.phase());
            hurt[0] = true;
        });
        helper.onEachTick(() -> {
            double y = s.getY() - helper.absoluteVec(GROUND).y;
            landed[0] |= hurt[0] && s.core().idle() && s.onGround() && Math.abs(y) < 0.01;
        });
        helper.succeedWhen(() -> helper.assertTrue(landed[0], "it fell to the floor, " + (s.getY() - helper.absoluteVec(GROUND).y) + " up, phase " + s.phase()));
    }

    // ---- never leaving anyone stuck ------------------------------------------------------------------------------------

    /** Killed while it wraps a pig, it lets go: the pig is no longer slowed. */
    private static void freesOnDeath(GameTestHelper helper) {
        VinedropStrangler s = strangler(helper, GROUND);
        Pig pig = pig(helper, GROUND);
        helper.runAtTickTime(2, () -> helper.assertTrue(s.wrap(pig), "it wraps the pig"));
        helper.runAtTickTime(10, () -> {
            s.kill(helper.getLevel());
            helper.assertTrue(!s.isAlive() && s.victim() == null, "dead, it holds no one");
            helper.assertTrue(!slowed(pig), "the pig is no longer slowed");
            helper.succeed();
        });
    }

    /** Removed (unloaded or discarded) while it wraps a player, the player is no longer slowed or followed. */
    private static void freesOnRemoval(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, GROUND);
        VinedropStrangler s = strangler(helper, GROUND);
        helper.runAtTickTime(2, () -> helper.assertTrue(s.wrap(p), "it wraps the player"));
        helper.runAtTickTime(10, () -> {
            s.discard();
            helper.assertTrue(s.victim() == null, "removed, it holds no one");
            helper.assertTrue(!slowed(p), "the player is no longer slowed");
            helper.succeed();
        });
    }

    /** A victim found more than 4 blocks away (a teleport) has got away: it lets go and stops following. */
    private static void escape(GameTestHelper helper) {
        VinedropStrangler s = strangler(helper, new Vec3(3.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(3.5, 1, 5.5));
        helper.runAtTickTime(2, () -> helper.assertTrue(s.wrap(pig), "it wraps the pig"));
        helper.runAtTickTime(10, () -> moveTo(helper, pig, new Vec3(9.0, 1, 5.5)));
        helper.runAtTickTime(13, () -> {
            helper.assertTrue(s.victim() == null && !s.core().in(Step.WRAP), "it let go, phase " + s.phase());
            helper.assertTrue(!slowed(pig), "the pig is no longer slowed");
            helper.assertTrue(s.position().distanceTo(pig.position()) > 4.0, "it doesn't follow the pig");
            helper.succeed();
        });
    }

    /** A listener that cancels the "squeeze" step leaves the wrapped pig slowed but unhurt. */
    private static void squeezeCancel(GameTestHelper helper) {
        VinedropStrangler s = strangler(helper, GROUND);
        Pig pig = pig(helper, GROUND);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == s && e.step().equals("squeeze")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> helper.assertTrue(s.wrap(pig), "it wraps the pig"));
        helper.runAtTickTime(2 + 45, () -> {
            helper.assertTrue(s.victim() == pig && slowed(pig), "the pig is still wrapped and slowed");
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "cancelled squeezes do nothing, health " + pig.getHealth());
            helper.succeed();
        });
    }

    /** Health 22, attack 4, armour 0, a 0.8 x 1.2 hitbox; outside a stun a hit counts at its face value. */
    private static void stats(GameTestHelper helper) {
        VinedropStrangler s = strangler(helper, GROUND);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(s.getMaxHealth() - 22.0F) < 0.01F && Math.abs(s.getHealth() - 22.0F) < 0.01F,
                    "health 22, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4");
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(s.getBbWidth() - 0.8F) < 0.02F && Math.abs(s.getBbHeight() - 1.2F) < 0.02F,
                    "hitbox 0.8 x 1.2, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.assertTrue(!s.vulnerable(), "not stunned, no weak point");
            float had = s.getHealth();
            s.hurtServer(helper.getLevel(), s.damageSources().magic(), 4.0F);
            float lost = had - s.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F) < 0.01F, "outside a stun 4 stays 4, lost " + lost);
            helper.succeed();
        });
    }

    /** Home: the jungles (jungle, sparse and bamboo jungle), placed on leaves, roster weight 15, alone. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("vinedrop_strangler"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        for (var biome : List.of(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE)) {
            helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(biome)), "its home holds " + biome.identifier());
        }
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.PLAINS)), "not the plains");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/vinedrop_strangler")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.VINEDROP_STRANGLER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        // the rest of its tag (a biome mod's biomes; every vanilla biome of its home is tuned) has its rest weight from the
        // spawn table, the share in mobs beside the mobs that live there too, not its roster weight 15
        int rest = SpawnTable.rest("vinedrop_strangler", 15).weight();
        helper.assertTrue(entry.weight() == rest, "rest weight " + rest + ", got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got groups of " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(RosterStats.get("vinedrop_strangler").place() == RosterStats.Place.LEAVES, "it is placed on leaves");
        helper.succeed();
    }
}
