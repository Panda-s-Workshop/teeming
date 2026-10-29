package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.MireGulper;
import com.panda.teeming.mob.overworld.MireGulper.Step;
import com.panda.teeming.registry.ModMobs;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Mire Gulper: prey within 1.5 blocks sets off 20 ticks of bubbles that do nothing yet; prey still within 2.5 blocks
 * then is swallowed: held in its mouth (rooted, carried with it) and chewed for 0.35 of its attack (1.4) every 10
 * ticks, for at most 100 ticks. Three hits on it while it holds someone (from inside or outside) make it spit the victim out and
 * leave it stunned for 40 ticks, taking x1.5. Under water it is invisible, untargetable and arrows pass through it.
 *
 * <p>Every gulper here has its goals cleared and a follow range of 0, so it never notices a survival player in a
 * neighbouring arena; targets are given to it directly. Its phase machine and its hold still run. Pigs are tough (1000
 * health) wherever a hold could kill one.
 */
public final class MireGulperTests {
    private MireGulperTests() {}

    private static final Vec3 AT = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("mire_gulper_tell", MireGulperTests::tell);
        out.accept("mire_gulper_counter", MireGulperTests::threeHitsSpit);
        out.accept("mire_gulper_failure", MireGulperTests::chewedForTheWholeHold);
        TeemingGameTests.timeLimit("mire_gulper_failure", 160);
        out.accept("mire_gulper_chew_numbers", MireGulperTests::chewNumbers);
        TeemingGameTests.timeLimit("mire_gulper_chew_numbers", 160);
        out.accept("mire_gulper_hit_from_inside", MireGulperTests::hitFromInside);
        out.accept("mire_gulper_stun_length", MireGulperTests::stunLength);
        out.accept("mire_gulper_rooted", MireGulperTests::rootedAndCarried);
        out.accept("mire_gulper_tell_range", MireGulperTests::tellRange);
        out.accept("mire_gulper_lunge_reach", MireGulperTests::lungeReach);
        out.accept("mire_gulper_untargetable", MireGulperTests::untargetable);
        out.accept("mire_gulper_arrows_pass", MireGulperTests::arrowsPass);
        out.accept("mire_gulper_frees_on_death", MireGulperTests::freesOnDeath);
        out.accept("mire_gulper_frees_on_removal", MireGulperTests::freesOnRemoval);
        out.accept("mire_gulper_escape", MireGulperTests::escape);
        out.accept("mire_gulper_stats", MireGulperTests::stats);
        out.accept("mire_gulper_home", MireGulperTests::home);
        out.accept("mire_gulper_chew_cancel", MireGulperTests::chewCancel);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Water two deep from x 1 to 10 and z 1 to 10, stone walls round it. */
    private static void pool(GameTestHelper helper) {
        for (int x = 0; x <= 11; x++) {
            for (int z = 0; z <= 11; z++) {
                for (int y = 1; y <= 2; y++) {
                    boolean wall = x == 0 || z == 0 || x == 11 || z == 11;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.WATER);
                }
            }
        }
    }

    /** A gulper facing +X with no goals and no sight range of its own; its phase machine and hold still run. */
    private static MireGulper gulper(GameTestHelper helper, Vec3 at) {
        MireGulper g = helper.spawn(ModMobs.MIRE_GULPER.get(), at);
        g.removeFreeWill();
        g.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        g.setYRot(-90.0F);
        g.yBodyRot = -90.0F;
        g.yHeadRot = -90.0F;
        return g;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        return pig;
    }

    private static void moveTo(GameTestHelper helper, net.minecraft.world.entity.Entity e, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        if (e instanceof ServerPlayer p) p.snapTo(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        else e.teleportTo(at.x, at.y, at.z);
        e.setDeltaMovement(Vec3.ZERO);
    }

    private static boolean inMouth(MireGulper g, net.minecraft.world.entity.Entity e) {
        return e.position().distanceTo(g.mouth()) < 0.15;
    }

    /** Records the gulper's own tick count at each mechanic step it posts; remove the listener when done. */
    private static Consumer<MechanicEvent> steps(GameTestHelper helper, MireGulper g, Map<String, List<Integer>> at) {
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == g) at.computeIfAbsent(e.step(), k -> new ArrayList<>()).add(g.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return listener;
    }

    /** Chew damage to a player scales with difficulty: 1.4 is 1.4 on Easy and Normal, 2.1 on Hard. */
    private static float chewOnPlayer(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case HARD -> 2.1F;
            default -> 1.4F;
        };
    }

    // ---- the tell, the counter, failing it -------------------------------------------------------------------------

    /**
     * The tell: a pig 1 block away in the pool sets it off on its own. 20 ticks pass from the tell to the swallow, the
     * gulper stays put, hidden under the water, and the pig is neither held nor hurt meanwhile. Then it is swallowed.
     */
    private static void tell(GameTestHelper helper) {
        pool(helper);
        MireGulper g = gulper(helper, AT);
        Pig pig = pig(helper, AT.add(1.0, 0, 0));
        Map<String, List<Integer>> at = new HashMap<>();
        steps(helper, g, at);
        boolean[] early = {false}, shown = {false};
        long[] began = {-1};
        double[] moved = {0.0};
        Vec3[] start = {null};
        helper.runAtTickTime(1, () -> {
            g.setTarget(pig);
            start[0] = g.position();
        });
        helper.onEachTick(() -> {
            if (!g.core().in(Step.TELL)) return;
            if (began[0] < 0) began[0] = helper.getTick();
            early[0] |= g.victim() != null || pig.getHealth() < pig.getMaxHealth();
            shown[0] |= !g.submerged() || !g.isInvisible();
            if (start[0] != null) moved[0] = Math.max(moved[0], g.position().subtract(start[0]).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.containsKey("tell") && at.containsKey("swallow"), "it bubbled and swallowed on its own: " + at.keySet());
            int bubbles = at.get("swallow").getFirst() - at.get("tell").getFirst();
            helper.assertTrue(bubbles == 20, "the bubbles last 20 ticks, measured " + bubbles);
            helper.assertTrue(began[0] <= 4, "a pig 1 block away sets it off at once, it began at tick " + began[0]);
            helper.assertTrue(!early[0], "nothing happens to the pig while the bubbles rise");
            helper.assertTrue(!shown[0], "under the water it stays hidden through the tell");
            helper.assertTrue(moved[0] < 0.1, "it holds still while the bubbles rise, moved " + moved[0]);
            helper.assertTrue(g.victim() == pig && g.core().in(Step.SWALLOW), "then the pig is swallowed, phase " + g.phase());
        });
    }

    /**
     * The counter: a pig is swallowed and a second pig hits the gulper. Two hits and it still holds on; the third and it
     * spits the pig out (it is free to leave) and is stunned, its weak point showing, taking x1.5 from a blow.
     */
    private static void threeHitsSpit(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        Pig pig = pig(helper, AT.add(1.0, 0, 0));
        Pig friend = pig(helper, AT.add(-2.0, 0, 2.0));
        float[] health = {0.0F};
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(pig), "it swallows the pig"));
        helper.runAtTickTime(6, () -> g.hurtServer(helper.getLevel(), g.damageSources().mobAttack(friend), 1.0F));
        helper.runAtTickTime(18, () -> g.hurtServer(helper.getLevel(), g.damageSources().mobAttack(friend), 1.0F));
        helper.runAtTickTime(19, () -> {
            helper.assertTrue(g.hits() == 2, "both hits counted, " + g.hits());
            helper.assertTrue(g.core().in(Step.SWALLOW) && g.victim() == pig && inMouth(g, pig), "after two hits it still holds the pig");
        });
        helper.runAtTickTime(30, () -> g.hurtServer(helper.getLevel(), g.damageSources().mobAttack(friend), 1.0F));
        helper.runAtTickTime(31, () -> {
            helper.assertTrue(g.victim() == null, "the third hit makes it spit the pig out");
            helper.assertTrue(g.core().in(Step.STUNNED) && g.vulnerable(), "and it is stunned, weak point showing, phase " + g.phase());
            moveTo(helper, pig, AT.add(3.5, 0, 0));
        });
        helper.runAtTickTime(34, () -> helper.assertTrue(pig.position().distanceTo(helper.absoluteVec(AT.add(3.5, 0, 0))) < 0.3,
                "the pig is free: it is no longer pulled back"));
        helper.runAtTickTime(42, () -> {                         // past the hurt cooldown of the third hit
            helper.assertTrue(g.core().in(Step.STUNNED), "still stunned, phase " + g.phase());
            health[0] = g.getHealth();
            g.hurtServer(helper.getLevel(), g.damageSources().magic(), 4.0F);
            float lost = health[0] - g.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "stunned, it takes x1.5: 4 became " + lost);
            helper.succeed();
        });
    }

    /**
     * Failing the counter: a survival player is swallowed and nobody hits the gulper. They are held for the whole 100
     * ticks, chewed 9 times (1.4 each on Easy and Normal, 2.1 on Hard), then let go, free to walk away. The player has
     * no armour and vanilla's 20 health, and lives through the whole hold on any difficulty (18.9 lost on Hard).
     */
    private static void chewedForTheWholeHold(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, AT.add(1.0, 0, 0));
        helper.assertTrue(p.getMaxHealth() == 20.0F && p.getHealth() == 20.0F && p.getArmorValue() == 0, "a fresh unarmoured player");
        Map<String, List<Integer>> at = new HashMap<>();
        steps(helper, g, at);
        float before = p.getHealth();
        float each = chewOnPlayer(helper);
        boolean[] slipped = {false};
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(p), "it swallows the player"));
        helper.onEachTick(() -> {
            if (g.core().in(Step.SWALLOW) && helper.getTick() > 3) slipped[0] |= !inMouth(g, p);
        });
        helper.runAtTickTime(2 + 100 + 3, () -> {
            helper.assertTrue(at.containsKey("swallow") && at.containsKey("recover"), "it held on and then let go: " + at.keySet());
            int held = at.get("recover").getFirst() - at.get("swallow").getFirst();
            helper.assertTrue(held == 100, "it holds on for 100 ticks, held " + held);
            helper.assertTrue(!slipped[0], "the player stayed in its mouth the whole time");
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - 9.0F * each) < 0.01F, "9 chews of " + each + " here: lost " + lost);
            helper.assertTrue(p.isAlive(), "an unarmoured player lives through a full hold, " + p.getHealth() + " left");
            helper.assertTrue(g.victim() == null && g.core().in(Step.RECOVER), "it let go, phase " + g.phase());
            moveTo(helper, p, AT.add(3.5, 0, 0));
        });
        helper.runAtTickTime(2 + 100 + 6, () -> {
            helper.assertTrue(p.position().distanceTo(helper.absoluteVec(AT.add(3.5, 0, 0))) < 0.3, "once let go the player walks away freely");
            helper.succeed();
        });
    }

    /** On a pig (no difficulty scaling, no armour): each chew is exactly 1.4 (0.35 of Atk 4), 10 ticks apart, the first 10 ticks in. */
    private static void chewNumbers(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        Pig pig = pig(helper, AT.add(1.0, 0, 0));
        Map<String, List<Integer>> at = new HashMap<>();
        steps(helper, g, at);
        List<Float> chews = new ArrayList<>();
        float[] last = {pig.getHealth()};
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(pig), "it swallows the pig"));
        helper.onEachTick(() -> {
            float now = pig.getHealth();
            if (now < last[0]) chews.add(last[0] - now);
            last[0] = now;
        });
        helper.runAtTickTime(2 + 100 + 3, () -> {
            List<Integer> times = at.getOrDefault("chew", List.of());
            helper.assertTrue(times.size() == 9 && chews.size() == 9, "9 chews in the hold, saw " + times.size() + " steps and " + chews.size() + " hurts");
            int start = at.get("swallow").getFirst();
            helper.assertTrue(times.getFirst() - start == 10, "the first chew comes 10 ticks in, " + (times.getFirst() - start));
            for (int i = 1; i < times.size(); i++) {
                helper.assertTrue(times.get(i) - times.get(i - 1) == 10, "chews are 10 ticks apart: " + times);
            }
            for (float c : chews) helper.assertTrue(Math.abs(c - 1.4F) < 0.01F, "each chew does 1.4 (0.35 of Atk 4): " + chews);
            helper.succeed();
        });
    }

    /** The counter from inside: the swallowed player hits the gulper three times themselves and is spat out. */
    private static void hitFromInside(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, AT.add(1.0, 0, 0));
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(p), "it swallows the player"));
        helper.runAtTickTime(6, () -> p.attack(g));
        helper.runAtTickTime(18, () -> p.attack(g));
        helper.runAtTickTime(19, () -> helper.assertTrue(g.hits() == 2 && g.victim() == p, "two hits from inside counted, still held, hits " + g.hits()));
        helper.runAtTickTime(30, () -> p.attack(g));
        helper.runAtTickTime(31, () -> {
            helper.assertTrue(g.victim() == null && g.core().in(Step.STUNNED), "the third hit from inside frees the player, phase " + g.phase());
            helper.succeed();
        });
    }

    /** The stun after spitting lasts 40 ticks, then it is back to idle and no longer takes x1.5. */
    private static void stunLength(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        Pig pig = pig(helper, AT.add(1.0, 0, 0));
        int[] stunned = {0};
        boolean[] over = {false};
        int[] length = {0};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(g.swallow(pig), "it swallows the pig");
            g.spit(helper.getLevel());
        });
        helper.onEachTick(() -> {
            if (g.core().in(Step.STUNNED)) {
                stunned[0]++;
                length[0] = g.core().length();
            } else if (stunned[0] > 0) {
                over[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the stun ends, " + stunned[0] + " ticks so far");
            helper.assertTrue(length[0] == 40, "the stun is 40 ticks long, " + length[0]);
            helper.assertTrue(Math.abs(stunned[0] - 40) <= 1, "and it lasted 40 ticks, " + stunned[0]);
            helper.assertTrue(g.core().idle() && !g.vulnerable(), "then it is back to idle, phase " + g.phase());
        });
    }

    /**
     * Rooted and carried: a swallowed player pulled 2 blocks away is back in its mouth within 2 ticks, with no motion
     * of their own; when the gulper is moved 2 blocks, the player comes with it.
     */
    private static void rootedAndCarried(GameTestHelper helper) {
        MireGulper g = gulper(helper, new Vec3(3.5, 1, 5.5));
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(4.5, 1, 5.5));
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(p), "it swallows the player"));
        helper.runAtTickTime(10, () -> moveTo(helper, p, new Vec3(4.5, 1, 7.5)));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(inMouth(g, p), "the player is held: back in its mouth, " + p.position().distanceTo(g.mouth()) + " away");
            helper.assertTrue(p.getDeltaMovement().length() < 1.0E-3, "with no motion of their own, " + p.getDeltaMovement());
            moveTo(helper, g, new Vec3(6.5, 1, 5.5));
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(inMouth(g, p), "carried along with it, " + p.position().distanceTo(g.mouth()) + " from its mouth");
            helper.assertTrue(p.position().distanceTo(helper.absoluteVec(new Vec3(4.5, 1, 5.5))) > 1.5, "the player moved with the gulper");
            helper.succeed();
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** A pig 1.8 blocks away across doesn't set it off for 20 ticks; moved to 1.3 blocks it starts within 2 ticks. */
    private static void tellRange(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        Pig pig = pig(helper, AT.add(1.8, 0, 0));
        long[] began = {-1};
        helper.runAtTickTime(1, () -> g.setTarget(pig));
        helper.onEachTick(() -> {
            if (began[0] < 0 && g.core().in(Step.TELL)) began[0] = helper.getTick();
        });
        helper.runAtTickTime(21, () -> {
            helper.assertTrue(began[0] < 0 && g.core().idle(), "1.8 blocks away is too far, began at " + began[0]);
            moveTo(helper, pig, AT.add(1.3, 0, 0));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(began[0] >= 21, "1.3 blocks away sets it off, began at " + began[0]);
            helper.assertTrue(began[0] <= 23, "within 2 ticks, began at " + began[0]);
        });
    }

    /**
     * The lunge reaches 2.5 blocks: of two pigs that move away while the bubbles rise, the one 2.3 blocks off when they
     * stop is swallowed, the one 2.7 blocks off is missed (unhurt, and it settles down).
     */
    private static void lungeReach(GameTestHelper helper) {
        MireGulper near = gulper(helper, new Vec3(3.5, 1, 2.5));
        MireGulper far = gulper(helper, new Vec3(3.5, 1, 8.5));
        Pig caught = pig(helper, new Vec3(4.5, 1, 2.5));
        Pig missed = pig(helper, new Vec3(4.5, 1, 8.5));
        helper.runAtTickTime(2, () -> helper.assertTrue(near.ambush(caught) && far.ambush(missed), "both start the tell"));
        helper.runAtTickTime(10, () -> {
            moveTo(helper, caught, new Vec3(5.8, 1, 2.5));
            moveTo(helper, missed, new Vec3(6.2, 1, 8.5));
        });
        helper.runAtTickTime(2 + 20 + 2, () -> {
            helper.assertTrue(near.victim() == caught && near.core().in(Step.SWALLOW), "2.3 blocks away is swallowed, phase " + near.phase());
            helper.assertTrue(far.victim() == null && far.core().in(Step.RECOVER), "2.7 blocks away is missed, phase " + far.phase());
            helper.assertTrue(missed.getHealth() >= missed.getMaxHealth(), "the missed pig is unhurt");
            helper.succeed();
        });
    }

    /**
     * Untargetable under water: lying in the pool it is invisible, no mob may target it and projectiles can't hit it.
     * Holding a pig it has surfaced: visible, a target, hittable.
     */
    private static void untargetable(GameTestHelper helper) {
        pool(helper);
        MireGulper g = gulper(helper, AT);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT.add(3.0, 0, 3.0));
        zombie.setNoAi(true);
        Pig pig = pig(helper, AT.add(1.0, 0, 0));
        TargetingConditions combat = TargetingConditions.forCombat().range(16.0).ignoreLineOfSight();
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(g.isUnderWater() && g.submerged(), "in the pool it lies under the water");
            helper.assertTrue(g.isInvisible(), "under water it is invisible");
            helper.assertTrue(!combat.test(helper.getLevel(), zombie, g), "under water no mob may target it");
            helper.assertTrue(!g.canBeHitByProjectile(), "under water projectiles pass through it");
            helper.assertTrue(g.swallow(pig), "it swallows the pig");
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(!g.submerged() && !g.isInvisible(), "holding the pig it has surfaced and shows");
            helper.assertTrue(combat.test(helper.getLevel(), zombie, g), "surfaced, it can be targeted");
            helper.assertTrue(g.canBeHitByProjectile(), "surfaced, projectiles hit it");
            helper.succeed();
        });
    }

    /** A real arrow shot down at a gulper lying under the water passes through it; one at a stunned (surfaced) gulper hits. */
    private static void arrowsPass(GameTestHelper helper) {
        pool(helper);
        MireGulper under = gulper(helper, new Vec3(3.5, 1, 5.5));
        MireGulper up = gulper(helper, new Vec3(8.5, 1, 5.5));
        helper.runAtTickTime(2, () -> up.startPhase(Step.STUNNED, 60, null));
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(under.submerged() && !up.submerged(), "one lies under, the stunned one has surfaced");
            for (MireGulper g : List.of(under, up)) {
                Vec3 from = g.position().add(0, 1.6, 0);
                Arrow arrow = new Arrow(helper.getLevel(), from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
                arrow.shoot(0.0, -1.0, 0.0, 1.5F, 0.0F);
                helper.getLevel().addFreshEntity(arrow);
            }
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(under.getHealth() >= under.getMaxHealth(), "the arrow passed through the hidden gulper, health " + under.getHealth());
            helper.assertTrue(up.getHealth() < up.getMaxHealth(), "the arrow hit the surfaced one, health " + up.getHealth());
            helper.succeed();
        });
    }

    /** Killed while it holds a player, it lets go at once: the player is not pulled back and keeps no effects. */
    private static void freesOnDeath(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, AT.add(1.0, 0, 0));
        double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(p), "it swallows the player"));
        helper.runAtTickTime(10, () -> {
            g.hurtServer(helper.getLevel(), g.damageSources().magic(), 1000.0F);
            helper.assertTrue(!g.isAlive() && g.victim() == null, "dead, it holds no one");
        });
        helper.runAtTickTime(12, () -> moveTo(helper, p, AT.add(3.5, 0, 2.0)));
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(p.position().distanceTo(helper.absoluteVec(AT.add(3.5, 0, 2.0))) < 0.3, "the player is free to move");
            helper.assertTrue(p.getActiveEffects().isEmpty(), "and has no effects left on them");
            helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1.0E-6, "and walks at their own speed");
            helper.succeed();
        });
    }

    /** Removed (unloaded or discarded) while it holds a player, it holds no one and the player is free. */
    private static void freesOnRemoval(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, AT.add(1.0, 0, 0));
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(p), "it swallows the player"));
        helper.runAtTickTime(10, () -> {
            g.discard();
            helper.assertTrue(g.victim() == null, "removed, it holds no one");
        });
        helper.runAtTickTime(12, () -> moveTo(helper, p, AT.add(3.5, 0, 2.0)));
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(p.position().distanceTo(helper.absoluteVec(AT.add(3.5, 0, 2.0))) < 0.3, "the player is free to move");
            helper.succeed();
        });
    }

    /** A victim that gets more than 4 blocks from its mouth (an ender pearl, a teleport) has got away: it lets go. */
    private static void escape(GameTestHelper helper) {
        MireGulper g = gulper(helper, new Vec3(2.5, 1, 5.5));
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(3.5, 1, 5.5));
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(p), "it swallows the player"));
        helper.runAtTickTime(10, () -> moveTo(helper, p, new Vec3(9.0, 1, 5.5)));
        helper.runAtTickTime(13, () -> {
            helper.assertTrue(g.victim() == null && g.core().in(Step.RECOVER), "it let go of the player, phase " + g.phase());
            helper.assertTrue(p.position().distanceTo(helper.absoluteVec(new Vec3(9.0, 1, 5.5))) < 0.3, "the player stays where they went");
            helper.succeed();
        });
    }

    /** Health 28, attack 4, armour 2, a 1.4 x 0.9 hitbox. */
    private static void stats(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(g.getMaxHealth() - 28.0F) < 0.01F && Math.abs(g.getHealth() - 28.0F) < 0.01F,
                    "health 28, got " + g.getHealth() + "/" + g.getMaxHealth());
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4");
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(g.getBbWidth() - 1.4F) < 0.02F && Math.abs(g.getBbHeight() - 0.9F) < 0.02F,
                    "hitbox 1.4 x 0.9, got " + g.getBbWidth() + " x " + g.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: swamps and mangrove swamps, roster weight 15, alone. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("mire_gulper"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SWAMP)), "its home holds the swamp");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.MANGROVE_SWAMP)), "its home holds the mangrove swamp");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/mire_gulper")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.MIRE_GULPER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        // the rest of its tag (a biome mod's biomes; every vanilla biome of its home is tuned) has its rest weight from the
        // spawn table, the share in mobs beside the mobs that live there too, not its roster weight 15
        int rest = SpawnTable.rest("mire_gulper", 15).weight();
        helper.assertTrue(entry.weight() == rest, "rest weight " + rest + ", got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got groups of " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }

    /** A listener that cancels the "chew" step leaves the swallowed pig held but unhurt. */
    private static void chewCancel(GameTestHelper helper) {
        MireGulper g = gulper(helper, AT);
        Pig pig = pig(helper, AT.add(1.0, 0, 0));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == g && e.step().equals("chew")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(pig), "it swallows the pig"));
        helper.runAtTickTime(2 + 35, () -> {
            helper.assertTrue(g.victim() == pig && inMouth(g, pig), "the pig is still held");
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "cancelled chews do nothing, health " + pig.getHealth());
            helper.succeed();
        });
    }
}
