package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.Dustwhirl;
import com.panda.teeming.mob.overworld.Dustwhirl.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Dustwhirl: a 20-tick spin-up that does nothing yet, then a 60-tick gust that pushes players within reach straight
 * outward at 0.6 blocks a tick (0.3 sneaking), then a 40-tick slowdown in which it takes x1.5. It always tugs dropped
 * items within 8 blocks, swallows them and spits them out when it dies.
 *
 * <p>Every whirl here has its goals cleared, a follow range of 1 and is confined to its own arena, so it never pushes
 * a survival player or tugs an item in a neighbouring arena; it is set off directly. Its phase machine, its gust and
 * its tug still run. Survival players here never move by themselves (they have no client), so the push is read as the
 * motion the whirl gives them, on the tick it gives it.
 */
public final class DustwhirlTests {
    private DustwhirlTests() {}

    private static final Vec3 AT = new Vec3(5.5, 1, 5.5);
    /** The tick each test sets the whirl off. */
    private static final int START = 2;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("dustwhirl_tell", DustwhirlTests::tell);
        out.accept("dustwhirl_counter", DustwhirlTests::sneakThenStrikeTheCore);
        TeemingGameTests.timeLimit("dustwhirl_counter", 160);
        out.accept("dustwhirl_failure", DustwhirlTests::blownOutward);
        TeemingGameTests.timeLimit("dustwhirl_failure", 160);
        out.accept("dustwhirl_phase_lengths", DustwhirlTests::phaseLengths);
        TeemingGameTests.timeLimit("dustwhirl_phase_lengths", 200);
        out.accept("dustwhirl_gust_reach", DustwhirlTests::gustReach);
        out.accept("dustwhirl_drag_range", DustwhirlTests::dragRange);
        out.accept("dustwhirl_steals_and_spits", DustwhirlTests::stealsAndSpits);
        out.accept("dustwhirl_gust_cancel", DustwhirlTests::gustCancel);
        out.accept("dustwhirl_stats", DustwhirlTests::stats);
        out.accept("dustwhirl_home", DustwhirlTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /**
     * A whirl with no goals and a 2-block sight range, kept to this arena; its phases, gust and tug still run. Clearing
     * its goals leaves its target goals, and a follow range of 0 turns off the range check on players altogether (it
     * would pick the nearest survival player anywhere it can see and spin up on its own), so the range is 1: players
     * within the 2-block floor of that check only, and every player here stands farther off.
     */
    private static Dustwhirl whirl(GameTestHelper helper, Vec3 at) {
        Dustwhirl w = helper.spawn(ModMobs.DUSTWHIRL.get(), at);
        w.removeFreeWill();
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        w.confine(helper.getBounds());
        w.resetCooldown();
        return w;
    }

    /** The game time of each gust push this whirl gives {@code who}; the listener goes when the test ends. */
    private static List<Long> pushes(GameTestHelper helper, Dustwhirl w, Entity who) {
        List<Long> at = new ArrayList<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == w && e.step().equals("gust_push") && e.target() == who) at.add(helper.getLevel().getGameTime());
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    /** "step@time" for each phase this whirl enters, for failure messages. */
    private static List<String> phases(GameTestHelper helper, Dustwhirl w) {
        List<String> at = new ArrayList<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == w && !e.step().equals("gust_push") && !e.step().equals("tug")) at.add(e.step() + "@" + helper.getLevel().getGameTime());
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    private static void sneak(ServerPlayer p) {
        p.setShiftKeyDown(true);
        p.setPose(Pose.CROUCHING);
    }

    private static double horizontal(Vec3 v) {
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    // ---- the tell, the counter, failing it -------------------------------------------------------------------------

    /**
     * The tell: the spin-up holds for 20 ticks, the whirl stands still and a survival player 3 blocks away is not
     * pushed at all meanwhile. Then the gust starts.
     */
    private static void tell(GameTestHelper helper) {
        Dustwhirl w = whirl(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, AT.add(3.0, 0, 0));
        List<Long> pushed = pushes(helper, w, p);
        int[] spin = {0};
        boolean[] gusted = {false}, early = {false}, started = {false};
        Vec3 start = w.position();
        helper.onEachTick(() -> {
            if (helper.getTick() >= START && !started[0]) {
                started[0] = true;
                helper.assertTrue(w.spinUp(null), "it spins up from idle, phase " + w.phase() + ", target " + w.getTarget());
            }
            if (w.core().in(Step.SPIN)) {
                spin[0]++;
                early[0] |= !pushed.isEmpty() || w.getTarget() != null || horizontal(p.getDeltaMovement()) > 1.0E-4 || w.position().distanceTo(start) > 0.01;
            } else if (w.core().in(Step.GUST)) {
                gusted[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gusted[0], "the spin-up ends in a gust, phase " + w.phase());
            helper.assertTrue(spin[0] == 20, "the spin-up holds for 20 ticks, held " + spin[0]);
            helper.assertTrue(!early[0], "while it spins up it stands still, pushes no one and picks no target of its own");
        });
    }

    /**
     * Failing the counter: a player standing 3 blocks east is pushed straight outward (east) at 0.6 blocks a tick on
     * every tick of the 60-tick gust, and on no tick before or after it.
     */
    private static void blownOutward(GameTestHelper helper) {
        Dustwhirl w = whirl(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, AT.add(3.0, 0, 0));
        List<Long> pushed = pushes(helper, w, p);
        List<String> phases = phases(helper, w);
        List<Vec3> motion = new ArrayList<>();
        helper.onEachTick(() -> {
            if (helper.getTick() == START) w.spinUp(null);
            long now = helper.getLevel().getGameTime();
            if (!pushed.isEmpty() && pushed.getLast() == now) {
                motion.add(p.getDeltaMovement());                     // the motion given on this tick
                p.setDeltaMovement(Vec3.ZERO);                        // so the next tick reads only its own push
            }
        });
        helper.runAtTickTime(START + 20 + 60 + 40 + 5, () -> {
            helper.assertTrue(pushed.size() == 60, "the gust pushes on each of its 60 ticks, pushed " + pushed.size()
                    + (pushed.isEmpty() ? "" : " from " + pushed.getFirst() + " to " + pushed.getLast()) + ", phases " + phases);
            helper.assertTrue(motion.size() == 60, "a push was read on each of them, read " + motion.size());
            for (Vec3 v : motion) {
                helper.assertTrue(Math.abs(horizontal(v) - 0.6) < 0.001, "pushed at 0.6 blocks a tick, got " + horizontal(v));
                helper.assertTrue(v.x > 0.0 && Math.abs(v.z) < 0.001, "pushed straight outward (east), got " + v);
            }
            helper.assertTrue(pushed.getLast() - pushed.getFirst() == 59, "the pushes come on 60 ticks in a row, from " + pushed.getFirst() + " to " + pushed.getLast());
            helper.succeed();
        });
    }

    /**
     * The counter: a sneaking player is pushed at half the gust (0.3 blocks a tick); then in the slowdown the whirl is
     * vulnerable and a hit of 4 takes 6 (x1.5), while the same hit once it is idle again takes 4.
     */
    private static void sneakThenStrikeTheCore(GameTestHelper helper) {
        Dustwhirl w = whirl(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, AT.add(0, 0, 3.0));
        sneak(p);
        List<Long> pushed = pushes(helper, w, p);
        List<Double> speeds = new ArrayList<>();
        boolean[] struck = {false};
        float[] lost = {Float.NaN, Float.NaN};
        helper.onEachTick(() -> {
            if (helper.getTick() == START) w.spinUp(null);
            long now = helper.getLevel().getGameTime();
            if (!pushed.isEmpty() && pushed.getLast() == now) {
                speeds.add(horizontal(p.getDeltaMovement()));
                p.setDeltaMovement(Vec3.ZERO);
            }
            if (w.core().in(Step.SLOW) && w.core().elapsed() == 10 && !struck[0]) {
                struck[0] = true;
                helper.assertTrue(w.vulnerable(), "slowing down, its core shows");
                float before = w.getHealth();
                w.hurtServer(helper.getLevel(), w.damageSources().magic(), 4.0F);
                lost[0] = before - w.getHealth();
            }
        });
        helper.runAtTickTime(START + 20 + 60 + 40 + 10, () -> {
            helper.assertTrue(w.core().idle() && !w.vulnerable(), "the slowdown is over, phase " + w.phase());
            float before = w.getHealth();
            w.hurtServer(helper.getLevel(), w.damageSources().magic(), 4.0F);
            lost[1] = before - w.getHealth();
            helper.assertTrue(speeds.size() == 60, "the sneaking player is still pushed on each gust tick, " + speeds.size());
            for (double s : speeds) helper.assertTrue(Math.abs(s - 0.3) < 0.001, "sneaking halves the push to 0.3, got " + s);
            helper.assertTrue(Math.abs(lost[0] - 6.0F) < 0.01F, "a hit of 4 on the shown core takes 6, took " + lost[0]);
            helper.assertTrue(Math.abs(lost[1] - 4.0F) < 0.01F, "the same hit once it is idle takes 4, took " + lost[1]);
            helper.succeed();
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /**
     * Spin-up 20 ticks, gust 60, slowdown 40 (vulnerable on each of its ticks and on no other), then a 60-tick rest
     * before it can spin up again.
     */
    private static void phaseLengths(GameTestHelper helper) {
        Dustwhirl w = whirl(helper, AT);
        int[] spin = {0}, gust = {0}, slow = {0}, wrong = {0}, rest = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (helper.getTick() == START) w.spinUp(null);
            if (w.core().in(Step.SPIN)) spin[0]++;
            else if (w.core().in(Step.GUST)) gust[0]++;
            else if (w.core().in(Step.SLOW)) slow[0]++;
            if (w.vulnerable() != w.core().in(Step.SLOW)) wrong[0]++;
            if (slow[0] > 0 && w.core().idle()) {
                rest[0] = Math.max(rest[0], w.cooldown());
                done[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "the cycle ends, phase " + w.phase());
            helper.assertTrue(spin[0] == 20, "spin-up 20 ticks, " + spin[0]);
            helper.assertTrue(gust[0] == 60, "gust 60 ticks, " + gust[0]);
            helper.assertTrue(slow[0] == 40, "slowdown 40 ticks, " + slow[0]);
            helper.assertTrue(wrong[0] == 0, "vulnerable exactly while it slows, wrong on " + wrong[0] + " ticks");
            helper.assertTrue(rest[0] == 60 || rest[0] == 59, "a 60-tick rest follows, saw " + rest[0]);
        });
    }

    /** The gust reaches a player 4.5 blocks away and not one 5.5 blocks away. */
    private static void gustReach(GameTestHelper helper) {
        Dustwhirl w = whirl(helper, AT);
        ServerPlayer near = TestPlayers.survivor(helper, AT.add(-4.5, 0, 0));
        double d = 5.5 / Math.sqrt(2.0);
        ServerPlayer far = TestPlayers.survivor(helper, AT.add(d, 0, d));
        List<Long> nearPushed = pushes(helper, w, near);
        List<Long> farPushed = pushes(helper, w, far);
        helper.runAtTickTime(START, () -> w.spinUp(null));
        helper.runAtTickTime(START + 20 + 10, () -> {
            helper.assertTrue(w.core().in(Step.GUST), "it gusts, phase " + w.phase());
            helper.assertTrue(!nearPushed.isEmpty(), "the player 4.5 blocks away is pushed");
            helper.assertTrue(farPushed.isEmpty() && horizontal(far.getDeltaMovement()) < 1.0E-4, "the player 5.5 blocks away is not");
            helper.succeed();
        });
    }

    /** An item 7.5 blocks away is tugged in (at no more than 0.15 blocks a tick); one 8.5 blocks away stays put. */
    private static void dragRange(GameTestHelper helper) {
        Vec3 at = new Vec3(1.5, 1, 1.5);
        Dustwhirl w = whirl(helper, at);
        ItemEntity in = helper.spawnItem(Items.STICK, at.add(0, 0, 7.5));
        ItemEntity out = helper.spawnItem(Items.STICK, at.add(8.5, 0, 0));
        in.setDeltaMovement(Vec3.ZERO);
        out.setDeltaMovement(Vec3.ZERO);
        Vec3 outStart = out.position();
        double[] last = {Double.NaN}, maxStep = {0.0};
        double startDist = horizontal(in.position().subtract(w.position()));
        helper.onEachTick(() -> {
            double dist = horizontal(in.position().subtract(w.position()));
            if (!Double.isNaN(last[0])) maxStep[0] = Math.max(maxStep[0], last[0] - dist);
            last[0] = dist;
        });
        helper.runAtTickTime(30, () -> {
            double moved = startDist - horizontal(in.position().subtract(w.position()));
            helper.assertTrue(moved > 1.5, "the item 7.5 blocks away is tugged in, it came " + moved);
            helper.assertTrue(maxStep[0] <= 0.15 + 0.005, "tugged at no more than 0.15 blocks a tick, saw " + maxStep[0]);
            double drift = horizontal(out.position().subtract(outStart));
            helper.assertTrue(drift < 0.01, "the item 8.5 blocks away stays put, it moved " + drift);
            helper.succeed();
        });
    }

    /**
     * Failing to guard your drops: five diamonds dropped 3 blocks away are tugged in and swallowed, and the whirl keeps
     * them (and so never despawns). When it dies it spits all five out.
     */
    private static void stealsAndSpits(GameTestHelper helper) {
        Dustwhirl w = whirl(helper, AT);
        ItemEntity drop = helper.spawnItem(Items.DIAMOND, AT.add(3.0, 0, 0));
        drop.setItem(new ItemStack(Items.DIAMOND, 5));
        drop.setDeltaMovement(Vec3.ZERO);
        boolean[] killed = {false};
        helper.succeedWhen(() -> {
            if (!killed[0]) {
                helper.assertTrue(drop.isRemoved(), "the dropped diamonds are swallowed");
                int held = w.held().stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
                helper.assertTrue(held == 5, "it holds the five diamonds, holds " + held);
                helper.assertTrue(w.requiresCustomPersistence(), "holding them, it never despawns");
                killed[0] = true;
                w.hurtServer(helper.getLevel(), w.damageSources().generic(), 1000.0F);
                helper.assertTrue(w.isDeadOrDying(), "the blow kills it");
            }
            AABB box = new AABB(helper.absoluteVec(AT), helper.absoluteVec(AT)).inflate(3.0, 2.0, 3.0);
            int spat = 0;
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
                if (item.getItem().is(Items.DIAMOND)) spat += item.getItem().getCount();
            }
            helper.assertTrue(spat == 5, "dying, it spits out all five diamonds, " + spat + " lie here");
            helper.assertTrue(w.held().isEmpty(), "and holds nothing more");
        });
    }

    /** A listener that cancels the "gust_push" step leaves a player in the gust unpushed. */
    private static void gustCancel(GameTestHelper helper) {
        Dustwhirl w = whirl(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, AT.add(3.0, 0, 0));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == w && e.step().equals("gust_push")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(START, () -> w.spinUp(null));
        helper.runAtTickTime(START + 20 + 20, () -> {
            helper.assertTrue(w.core().in(Step.GUST), "it gusts, phase " + w.phase());
            helper.assertTrue(horizontal(p.getDeltaMovement()) < 1.0E-4, "a cancelled gust pushes no one, " + p.getDeltaMovement());
            helper.succeed();
        });
    }

    /** Health 18, attack 2, armour 0, a 0.8 x 1.6 hitbox. */
    private static void stats(GameTestHelper helper) {
        Dustwhirl w = whirl(helper, AT);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(w.getMaxHealth() - 18.0F) < 0.01F && Math.abs(w.getHealth() - 18.0F) < 0.01F,
                    "health 18, got " + w.getHealth() + "/" + w.getMaxHealth());
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.ATTACK_DAMAGE) - 2.0) < 0.01, "attack 2");
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(w.getBbWidth() - 0.8F) < 0.02F && Math.abs(w.getBbHeight() - 1.6F) < 0.02F,
                    "hitbox 0.8 x 1.6, got " + w.getBbWidth() + " x " + w.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: savannas (all of them) and wooded badlands, spawn weight 12, alone. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("dustwhirl"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SAVANNA)), "its home holds the savanna");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SAVANNA_PLATEAU)), "its home holds the savanna plateau");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.WINDSWEPT_SAVANNA)), "its home holds the windswept savanna");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.WOODED_BADLANDS)), "its home holds the wooded badlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.BADLANDS)), "plain badlands are not its home");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/dustwhirl")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(x -> x.value().type() == ModMobs.DUSTWHIRL.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 12, "spawn weight 12, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got groups of " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
