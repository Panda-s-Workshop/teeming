package com.panda.teeming.gametest.mob;

import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.PyreHerald;
import com.panda.teeming.mob.nether.PyreHerald.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Pyre Herald: it raises its brazier for 20 ticks, then shells every blaze, wither skeleton and Nether-born Teeming
 * mob within 10 blocks with 8 absorption, at most once every 200 ticks. A hit during the raise drops the brazier for
 * 60 ticks: nobody is shelled. It keeps its nearest ally between itself and its target, backs off from survival
 * players when it has no ally to hide behind, and swats a target that corners it. Every number is checked against a
 * plain value.
 *
 * <p>Wither skeletons and blazes stand in for its allies, with no AI so they stay where they are put; a still pig is
 * its target and the hand that hits it. Heralds that must not move have their goals cleared and a follow range of 1,
 * so they never notice a survival player in a neighbouring arena; their rally cycle still runs.
 */
public final class PyreHeraldTests {
    private PyreHeraldTests() {}

    private static final Vec3 AT = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("pyre_herald_tell", PyreHeraldTests::tell);
        out.accept("pyre_herald_counter", PyreHeraldTests::hitDropsBrazier);
        out.accept("pyre_herald_failure", PyreHeraldTests::alliesShelled);
        out.accept("pyre_herald_radius", PyreHeraldTests::radius);
        out.accept("pyre_herald_allies", PyreHeraldTests::allies);
        out.accept("pyre_herald_renews", PyreHeraldTests::renews);
        out.accept("pyre_herald_every", PyreHeraldTests::every);
        TeemingGameTests.timeLimit("pyre_herald_every", 300);
        out.accept("pyre_herald_keeps_behind", PyreHeraldTests::keepsBehind);
        TeemingGameTests.timeLimit("pyre_herald_keeps_behind", 160);
        out.accept("pyre_herald_backs_off", PyreHeraldTests::backsOff);
        out.accept("pyre_herald_no_spot_backs_off", PyreHeraldTests::noSpotBacksOff);
        out.accept("pyre_herald_swat", PyreHeraldTests::swat);
        out.accept("pyre_herald_stats", PyreHeraldTests::stats);
        out.accept("pyre_herald_home", PyreHeraldTests::home);
        out.accept("pyre_herald_ember_cancel", PyreHeraldTests::emberCancel);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A herald at {@code at} with every goal cleared, a follow range of 1 and no rally of its own coming. */
    private static PyreHerald still(GameTestHelper helper, Vec3 at) {
        PyreHerald h = helper.spawn(ModMobs.PYRE_HERALD.get(), at);
        h.removeFreeWill();
        h.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        h.setUntilRally(100000);
        return h;
    }

    /** A point {@code d} blocks from {@code from} along a direction at {@code deg} degrees round it. */
    private static Vec3 around(Vec3 from, double d, double deg) {
        double a = Math.toRadians(deg);
        return from.add(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    /** A mob of {@code type} with no AI, {@code d} blocks from the middle. */
    private static Mob ally(GameTestHelper helper, net.minecraft.world.entity.EntityType<? extends Mob> type, Vec3 from, double d, double deg) {
        Mob m = helper.spawn(type, around(from, d, deg));
        m.setNoAi(true);
        return m;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    /** Records the herald's own tick count each time it posts a mechanic step; removed when the test ends. */
    private static Map<String, List<Integer>> steps(GameTestHelper helper, PyreHerald h) {
        Map<String, List<Integer>> at = new HashMap<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == h) at.computeIfAbsent(e.step(), k -> new ArrayList<>()).add(h.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /** The raise: exactly 20 ticks from its start to the rally, the herald holds still, and nobody is shelled meanwhile. */
    private static void tell(GameTestHelper helper) {
        PyreHerald h = still(helper, AT);
        Mob skeleton = ally(helper, EntityTypes.WITHER_SKELETON, AT, 3.0, 0);
        Map<String, List<Integer>> at = steps(helper, h);
        Vec3[] start = {null};
        double[] moved = {0.0};
        boolean[] early = {false};
        int[] held = {0};
        helper.runAtTickTime(2, () -> {
            start[0] = h.position();
            helper.assertTrue(h.raise(null), "it raises the brazier");
            helper.assertTrue(h.core().in(Step.RAISE), "the raise is the tell, phase " + h.phase());
        });
        helper.onEachTick(() -> {
            if (!h.core().in(Step.RAISE)) return;
            held[0]++;
            early[0] |= skeleton.getAbsorptionAmount() > 0.0F;
            moved[0] = Math.max(moved[0], h.position().subtract(start[0]).horizontalDistance());
        });
        helper.runAtTickTime(2 + 20 + 4, () -> {
            helper.assertTrue(at.containsKey("raise") && at.containsKey("rally"), "it raised, then rallied: " + at);
            int raise = at.get("rally").getFirst() - at.get("raise").getFirst();
            helper.assertTrue(raise == 20, "the raise lasts 20 ticks, measured " + raise);
            helper.assertTrue(Math.abs(held[0] - 20) <= 1, "it holds the raise phase 20 ticks, held " + held[0]);
            helper.assertTrue(!early[0], "nobody is shelled during the raise");
            helper.assertTrue(moved[0] < 0.1, "it stands still while the brazier is up, moved " + moved[0]);
            helper.assertTrue(skeleton.getAbsorptionAmount() > 0.0F, "the rally follows the raise");
            helper.succeed();
        });
    }

    /**
     * The counter: a hit while idle changes nothing, but a hit 12 ticks into the raise drops the brazier. No rally
     * follows and the skeleton beside it gets no shell; the brazier lies dropped for exactly 60 ticks, a second hit
     * meanwhile doesn't make that longer, it can't raise again until it has picked the brazier up, and its next rally
     * is a full 200 ticks away.
     */
    private static void hitDropsBrazier(GameTestHelper helper) {
        PyreHerald h = still(helper, AT);
        Mob skeleton = ally(helper, EntityTypes.WITHER_SKELETON, AT, 3.0, 0);
        Pig pig = pig(helper, around(AT, 2.0, 180));
        Map<String, List<Integer>> at = steps(helper, h);
        int[] dropped = {0};
        int[] wait = {-1};
        boolean[] refused = {false};
        helper.onEachTick(() -> {
            if (h.core().in(Step.DROPPED)) {
                dropped[0]++;
                helper.assertTrue(!h.holdsBrazier(), "the brazier is out of its hands while dropped");
            }
        });
        helper.runAtTickTime(1, () -> {
            h.hurtServer(helper.getLevel(), h.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(h.core().idle(), "a hit while idle drops nothing, phase " + h.phase());
        });
        helper.runAtTickTime(2, () -> helper.assertTrue(h.raise(null), "it raises the brazier"));
        helper.runAtTickTime(2 + 12, () -> {
            helper.assertTrue(h.core().in(Step.RAISE), "still raised, phase " + h.phase());
            boolean hurt = h.hurtServer(helper.getLevel(), h.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(hurt, "the hit lands");
            helper.assertTrue(h.core().in(Step.DROPPED), "a hit in the raise drops the brazier, phase " + h.phase());
            wait[0] = h.untilRally();
        });
        helper.runAtTickTime(2 + 12 + 30, () -> {
            // past the hurt cooldown, so this hit lands too
            boolean hurt = h.hurtServer(helper.getLevel(), h.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(hurt, "the second hit lands");
            refused[0] = !h.raise(null);
        });
        helper.runAtTickTime(2 + 12 + 60 + 4, () -> {
            helper.assertTrue(!at.containsKey("rally"), "no rally after a drop: " + at);
            helper.assertTrue(skeleton.getAbsorptionAmount() == 0.0F, "the skeleton got no shell, " + skeleton.getAbsorptionAmount());
            helper.assertTrue(wait[0] == 200, "the next rally is 200 ticks away, " + wait[0]);
            helper.assertTrue(Math.abs(dropped[0] - 60) <= 1, "the brazier lies dropped 60 ticks, " + dropped[0]);
            helper.assertTrue(refused[0], "no raise while the brazier is down");
            helper.assertTrue(h.core().idle() && h.holdsBrazier(), "then it has the brazier back, phase " + h.phase());
            helper.assertTrue(h.raise(null), "and can raise it again");
            helper.succeed();
        });
    }

    /**
     * Failing the counter: the raise runs out and the allies are shelled. A wither skeleton 3 blocks away and a blaze 5
     * blocks away each get exactly 8 absorption (Absorption II, for 240 ticks); 5 damage to the skeleton then comes
     * off its shell, not its health, and leaves 3.
     */
    private static void alliesShelled(GameTestHelper helper) {
        PyreHerald h = still(helper, AT);
        Mob skeleton = ally(helper, EntityTypes.WITHER_SKELETON, AT, 3.0, 90);
        Mob blaze = ally(helper, EntityTypes.BLAZE, AT, 5.0, 210);
        Map<String, List<Integer>> at = steps(helper, h);
        helper.runAtTickTime(1, () -> helper.assertTrue(h.raise(null), "it raises the brazier"));
        helper.runAtTickTime(1 + 20 + 2, () -> {
            helper.assertTrue(at.containsKey("rally"), "it rallied: " + at);
            for (LivingEntity e : List.<LivingEntity>of(skeleton, blaze)) {
                helper.assertTrue(Math.abs(e.getAbsorptionAmount() - 8.0F) < 0.01F, "8 absorption, got " + e.getAbsorptionAmount());
                MobEffectInstance shell = e.getEffect(MobEffects.ABSORPTION);
                helper.assertTrue(shell != null && shell.getAmplifier() == 1, "Absorption II, " + shell);
                helper.assertTrue(shell.getDuration() > 230 && shell.getDuration() <= 240, "for 240 ticks, " + shell.getDuration() + " left");
            }
            float health = skeleton.getHealth();
            skeleton.hurtServer(helper.getLevel(), skeleton.damageSources().generic(), 5.0F);
            helper.assertTrue(Math.abs(skeleton.getHealth() - health) < 0.01F, "the shell takes the blow, health " + health + " -> " + skeleton.getHealth());
            helper.assertTrue(Math.abs(skeleton.getAbsorptionAmount() - 3.0F) < 0.01F, "8 - 5 = 3 left, " + skeleton.getAbsorptionAmount());
            helper.succeed();
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The reach is 10 blocks: a skeleton 9.5 blocks away is shelled, one 10.5 blocks away is not. */
    private static void radius(GameTestHelper helper) {
        Vec3 corner = new Vec3(1.0, 1, 1.0);
        PyreHerald h = still(helper, corner);
        Mob inside = ally(helper, EntityTypes.WITHER_SKELETON, corner, 9.5, 30);
        Mob outside = ally(helper, EntityTypes.WITHER_SKELETON, corner, 10.5, 60);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(inside.distanceTo(h) - 9.5F) < 0.05F && Math.abs(outside.distanceTo(h) - 10.5F) < 0.05F,
                    "skeletons at 9.5 and 10.5, got " + inside.distanceTo(h) + " and " + outside.distanceTo(h));
            int shelled = h.rally(helper.getLevel());
            helper.assertTrue(shelled == 1, "one skeleton inside 10 blocks is shelled, shelled " + shelled);
            helper.assertTrue(inside.getAbsorptionAmount() > 0.0F, "9.5 blocks away is inside the reach");
            helper.assertTrue(outside.getAbsorptionAmount() == 0.0F, "10.5 blocks away is outside it");
            helper.succeed();
        });
    }

    /**
     * Who it rallies: a wither skeleton, a blaze and a Fortress Jailer (a Teeming mob of the Nether) are shelled; a
     * zombified piglin, a husk, a Thornhorn (a Teeming mob of the Overworld), another herald, a survival player and
     * the herald itself are not.
     */
    private static void allies(GameTestHelper helper) {
        PyreHerald h = still(helper, AT);
        List<LivingEntity> yes = List.of(
                ally(helper, EntityTypes.WITHER_SKELETON, AT, 3.0, 0),
                ally(helper, EntityTypes.BLAZE, AT, 3.0, 40),
                ally(helper, ModMobs.FORTRESS_JAILER.get(), AT, 3.5, 80));
        List<LivingEntity> no = new ArrayList<>(List.of(
                ally(helper, EntityTypes.ZOMBIFIED_PIGLIN, AT, 3.0, 120),
                ally(helper, EntityTypes.HUSK, AT, 3.0, 160),
                ally(helper, ModMobs.THORNHORN.get(), AT, 3.5, 210),
                still(helper, around(AT, 3.0, 260))));
        ServerPlayer player = TestPlayers.survivor(helper, around(AT, 3.0, 310));
        no.add(player);
        no.add(h);
        helper.runAtTickTime(2, () -> {
            int shelled = h.rally(helper.getLevel());
            helper.assertTrue(shelled == 3, "skeleton, blaze and jailer are shelled, shelled " + shelled);
            for (LivingEntity e : yes) helper.assertTrue(Math.abs(e.getAbsorptionAmount() - 8.0F) < 0.01F, e.getType() + " gets 8 absorption, " + e.getAbsorptionAmount());
            for (LivingEntity e : no) helper.assertTrue(e.getAbsorptionAmount() == 0.0F, e.getType() + " gets nothing, " + e.getAbsorptionAmount());
            helper.succeed();
        });
    }

    /**
     * The shell doesn't stack: a second rally right after the first leaves 8, not 16, and a shell worn down to 3 is
     * topped back up to 8 by the next rally.
     */
    private static void renews(GameTestHelper helper) {
        PyreHerald h = still(helper, AT);
        Mob skeleton = ally(helper, EntityTypes.WITHER_SKELETON, AT, 3.0, 0);
        helper.runAtTickTime(2, () -> {
            h.rally(helper.getLevel());
            h.rally(helper.getLevel());
            helper.assertTrue(Math.abs(skeleton.getAbsorptionAmount() - 8.0F) < 0.01F, "two rallies make 8, not 16: " + skeleton.getAbsorptionAmount());
            skeleton.hurtServer(helper.getLevel(), skeleton.damageSources().generic(), 5.0F);
            helper.assertTrue(Math.abs(skeleton.getAbsorptionAmount() - 3.0F) < 0.01F, "worn down to 3, " + skeleton.getAbsorptionAmount());
        });
        helper.runAtTickTime(6, () -> {
            h.rally(helper.getLevel());
            helper.assertTrue(Math.abs(skeleton.getAbsorptionAmount() - 8.0F) < 0.01F, "the next rally tops it back to 8, " + skeleton.getAbsorptionAmount());
            helper.succeed();
        });
    }

    /**
     * Every 200 ticks: with an ally to rally and a target, it raises the brazier when 20 ticks remain until it may rally,
     * so the rally lands exactly when the wait runs out; then it waits 200 ticks, and the second rally comes 200 ticks
     * after the first.
     */
    private static void every(GameTestHelper helper) {
        PyreHerald h = still(helper, AT);
        ally(helper, EntityTypes.WITHER_SKELETON, AT, 3.0, 0);
        Pig pig = pig(helper, around(AT, 5.0, 180));
        Map<String, List<Integer>> at = steps(helper, h);
        int[] preset = {-1};
        int[] after = {0};
        helper.runAtTickTime(1, () -> {
            h.setTarget(pig);
            h.setUntilRally(40);
            preset[0] = h.tickCount;
        });
        helper.onEachTick(() -> {
            if (at.containsKey("rally") && at.get("rally").size() == 1) after[0] = Math.max(after[0], h.untilRally());
        });
        helper.succeedWhen(() -> {
            List<Integer> rallies = at.getOrDefault("rally", List.of()), raises = at.getOrDefault("raise", List.of());
            helper.assertTrue(rallies.size() >= 2, "it rallies twice on its own, rallies at " + rallies + ", raises at " + raises);
            helper.assertTrue(raises.size() == 2, "and raises the brazier before each, raises at " + raises);
            int lead = raises.get(0) - preset[0];
            int first = rallies.get(0) - preset[0];
            int between = rallies.get(1) - rallies.get(0);
            int secondLead = raises.get(1) - rallies.get(0);
            helper.assertTrue(lead == 20, "with 40 ticks to wait it raises 20 ticks later, measured " + lead);
            helper.assertTrue(first == 40, "and rallies when the wait runs out, measured " + first);
            helper.assertTrue(after[0] == 200, "after a rally it waits 200 ticks, saw " + after[0]);
            helper.assertTrue(secondLead == 180, "it raises again 180 ticks after the rally, measured " + secondLead);
            helper.assertTrue(between == 200, "rallies come 200 ticks apart, measured " + between);
        });
    }

    /**
     * It keeps behind its ally: with a wither skeleton between the middle of the arena and its target pig, a herald
     * that starts beside the pig walks round to the far side and stands about 3 blocks behind the skeleton, on the line
     * from the pig through it, so the skeleton is between them.
     */
    private static void keepsBehind(GameTestHelper helper) {
        Vec3 skelAt = new Vec3(5.5, 1, 6.5);
        Vec3 pigAt = new Vec3(5.5, 1, 10.0);
        Vec3 want = new Vec3(5.5, 1, 3.5);                                     // 3 blocks past the skeleton, away from the pig
        Mob skeleton = ally(helper, EntityTypes.WITHER_SKELETON, skelAt, 0.0, 0);
        Pig pig = pig(helper, pigAt);
        PyreHerald h = helper.spawn(ModMobs.PYRE_HERALD.get(), new Vec3(3.5, 1, 8.5));
        h.stayPut();
        h.setUntilRally(100000);
        h.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0);             // paths reach the spot; neighbours stay unseen
        helper.onEachTick(() -> {
            if (pig.isAlive()) h.setTarget(pig);
        });
        helper.succeedWhen(() -> {
            Vec3 spot = helper.absoluteVec(want);
            double off = h.position().subtract(spot).horizontalDistance();
            helper.assertTrue(off < 1.2, "it stands about 3 blocks behind the skeleton, " + off + " from the spot, at " + helper.relativeVec(h.position()));
            double toPig = h.distanceTo(pig), skelToPig = skeleton.distanceTo(pig);
            helper.assertTrue(toPig > skelToPig + 2.0, "the skeleton is between it and the pig: " + toPig + " vs " + skelToPig);
            helper.assertTrue(h.spot() != null, "it keeps a spot behind its ally");
        });
    }

    /**
     * The fallback, with no ally near: a survival player 7 blocks away leaves it where it is for 20 ticks; stepping to
     * 5 blocks sends it backing off, at least a block further away within 40 ticks.
     */
    private static void backsOff(GameTestHelper helper) {
        PyreHerald h = helper.spawn(ModMobs.PYRE_HERALD.get(), AT);
        h.stayPut();
        h.setUntilRally(100000);
        ServerPlayer p = TestPlayers.survivor(helper, around(AT, 7.0, 45));
        Vec3[] start = {null};
        double[] moved = {0.0};
        int[] stepped = {-1};
        double[] fled = {0.0};
        helper.runAtTickTime(1, () -> start[0] = h.position());
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (start[0] == null) return;
            if (stepped[0] < 0) {
                moved[0] = Math.max(moved[0], h.position().subtract(start[0]).horizontalDistance());
                if (now >= 21) {
                    p.snapTo(helper.absoluteVec(around(AT, 5.0, 45)));
                    stepped[0] = (int) now;
                }
            } else {
                fled[0] = Math.max(fled[0], h.position().subtract(p.position()).horizontalDistance() - 5.0);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepped[0] >= 0, "the player is still 7 blocks away");
            helper.assertTrue(moved[0] < 0.3, "7 blocks away doesn't move it, it moved " + moved[0]);
            helper.assertTrue(fled[0] >= 1.0, "5 blocks away it backs off: " + fled[0] + " blocks further so far");
            helper.assertTrue(helper.getTick() - stepped[0] <= 40, "within 40 ticks of stepping in");
        });
    }

    /**
     * The fallback when there is an ally but no way behind it: a wither skeleton stands with a wall two blocks high
     * behind it, so every spot behind it is inside stone. The herald finds no spot and backs off from the survival
     * player instead, at least a block further away within 40 ticks.
     */
    private static void noSpotBacksOff(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) for (int z = 0; z <= 4; z++) for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        ally(helper, EntityTypes.WITHER_SKELETON, new Vec3(5.5, 1, 5.5), 0.0, 0);
        Vec3 playerAt = new Vec3(5.5, 1, 9.0);
        ServerPlayer p = TestPlayers.survivor(helper, playerAt);
        PyreHerald h = helper.spawn(ModMobs.PYRE_HERALD.get(), new Vec3(8.5, 1, 7.0));
        h.stayPut();
        h.setUntilRally(100000);
        double[] start = {-1.0};
        double[] fled = {0.0};
        boolean[] hid = {false};
        helper.onEachTick(() -> {
            if (p.isAlive()) h.setTarget(p);
            if (helper.getTick() == 2) start[0] = h.distanceTo(p);
            if (start[0] > 0) fled[0] = Math.max(fled[0], h.distanceTo(p) - start[0]);
            hid[0] |= h.spot() != null;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] > 0 && start[0] < 6.0, "the player starts inside 6 blocks, " + start[0]);
            helper.assertTrue(fled[0] >= 1.0, "it backs off: " + fled[0] + " blocks further so far");
            helper.assertTrue(!hid[0], "it never found a spot behind the walled-in skeleton");
            helper.assertTrue(helper.getTick() <= 42, "within 40 ticks");
        });
    }

    /** Cornered (its target a block and a half away, no ally near) it swats: exactly 3 damage (Atk) to a pig. */
    private static void swat(GameTestHelper helper) {
        PyreHerald h = helper.spawn(ModMobs.PYRE_HERALD.get(), AT);
        h.stayPut();
        h.setUntilRally(100000);
        Pig pig = pig(helper, around(AT, 1.5, 0));
        float before = pig.getHealth();
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (pig.isAlive()) h.setTarget(pig);                               // players in other arenas never pull it off
            if (lost[0] < 0 && pig.getHealth() < before) lost[0] = before - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0, "it swats the pig that cornered it");
            helper.assertTrue(Math.abs(lost[0] - 3.0F) < 0.01F, "the swat does 3 (Atk), the pig lost " + lost[0]);
        });
    }

    /** Health 22, attack 3, armour 0, a 0.7 x 1.2 hitbox. */
    private static void stats(GameTestHelper helper) {
        PyreHerald h = still(helper, AT);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(h.getMaxHealth() - 22.0F) < 0.01F && Math.abs(h.getHealth() - 22.0F) < 0.01F,
                    "health 22, got " + h.getHealth() + "/" + h.getMaxHealth());
            helper.assertTrue(Math.abs(h.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < 0.01, "attack 3");
            helper.assertTrue(Math.abs(h.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(h.getBbWidth() - 0.7F) < 0.02F && Math.abs(h.getBbHeight() - 1.2F) < 0.02F,
                    "hitbox 0.7 x 1.2, got " + h.getBbWidth() + " x " + h.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: placed by the Teeming spawner's fortress rule only (no biome spawns), alone, in the Nether. */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("pyre_herald");
        helper.assertTrue(row.spawner().equals("fortress") && TeemingSpawner.Rule.of(row.spawner()) == TeemingSpawner.Rule.FORTRESS,
                "the fortress rule places it, got '" + row.spawner() + "'");
        helper.assertTrue(row.weight() == 0 && !row.biomeSpawns(), "no biome spawns, weight " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "alone, groups of " + row.groupMin() + "-" + row.groupMax());
        helper.assertTrue(row.dim() == RosterStats.Dim.NETHER, "a Nether mob");
        helper.succeed();
    }

    /** A listener that cancels the "ember" step for one skeleton leaves that one unshelled; the other is still shelled. */
    private static void emberCancel(GameTestHelper helper) {
        PyreHerald h = still(helper, AT);
        Mob vetoed = ally(helper, EntityTypes.WITHER_SKELETON, AT, 3.0, 0);
        Mob other = ally(helper, EntityTypes.WITHER_SKELETON, AT, 3.0, 180);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == h && e.step().equals("ember") && e.target() == vetoed) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> {
            int shelled = h.rally(helper.getLevel());
            helper.assertTrue(shelled == 1, "one skeleton shelled, shelled " + shelled);
            helper.assertTrue(vetoed.getAbsorptionAmount() == 0.0F, "the vetoed skeleton gets nothing");
            helper.assertTrue(other.getAbsorptionAmount() > 0.0F, "the other is shelled");
            helper.succeed();
        });
    }
}
