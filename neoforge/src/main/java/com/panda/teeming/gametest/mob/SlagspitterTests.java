package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.nether.Slagspitter;
import com.panda.teeming.mob.nether.Slagspitter.Step;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * Slagspitter: a 16-tick tell that does nothing yet; a glob spat in an arc at targets 6 to 20 blocks away that does 5
 * and leaves a burning patch of radius 1.5 for 80 ticks; a hop away from survival players within 5 blocks, which also
 * spoils a tell in progress; a nip of 3 up close; one tell every 60 ticks. Every number is checked against a plain
 * value.
 *
 * <p>Spitters that hunt here have their goals cleared (so they don't walk off to keep their range) and a follow range
 * of 1, so they never notice a survival player in a neighbouring arena; targets are handed to them, and their cycle
 * still runs. They leave with the test, wherever they hopped. Pigs have no AI: they stand where they are put.
 */
public final class SlagspitterTests {
    private SlagspitterTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("slagspitter_tell", SlagspitterTests::tell);
        out.accept("slagspitter_counter", SlagspitterTests::crowdedTellSpoils);
        out.accept("slagspitter_failure", SlagspitterTests::globHitsAndBurns);
        out.accept("slagspitter_shield", SlagspitterTests::shieldTakesGlob);
        out.accept("slagspitter_range_min_out", SlagspitterTests::rangeMinOut);
        out.accept("slagspitter_range_min_in", SlagspitterTests::rangeMinIn);
        out.accept("slagspitter_range_max", SlagspitterTests::rangeMax);
        out.accept("slagspitter_arc", SlagspitterTests::arc);
        out.accept("slagspitter_no_homing", SlagspitterTests::noHoming);
        out.accept("slagspitter_patch_radius", SlagspitterTests::patchRadius);
        out.accept("slagspitter_patch_length", SlagspitterTests::patchLength);
        TeemingGameTests.timeLimit("slagspitter_patch_length", 140);
        out.accept("slagspitter_patch_cancel", SlagspitterTests::patchCancel);
        out.accept("slagspitter_hop", SlagspitterTests::hop);
        out.accept("slagspitter_hop_range", SlagspitterTests::hopRange);
        out.accept("slagspitter_nip", SlagspitterTests::nip);
        out.accept("slagspitter_interval", SlagspitterTests::interval);
        TeemingGameTests.timeLimit("slagspitter_interval", 200);
        out.accept("slagspitter_stats", SlagspitterTests::stats);
        out.accept("slagspitter_home", SlagspitterTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A spitter with no goals and a follow range of 1, after {@code target}, removed when the test ends. */
    private static Slagspitter hunter(GameTestHelper helper, double x, double z, @Nullable LivingEntity target) {
        Slagspitter s = helper.spawn(ModMobs.SLAGSPITTER.get(), new Vec3(x, 1, z));
        s.removeFreeWill();
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        s.setTarget(target);
        TestPlayers.whenDone(helper, s::discard);
        return s;
    }

    /** A spitter with no AI: it stays put and acts only through the test hooks. */
    private static Slagspitter still(GameTestHelper helper, double x, double z) {
        Slagspitter s = helper.spawn(ModMobs.SLAGSPITTER.get(), new Vec3(x, 1, z));
        s.setNoAi(true);
        return s;
    }

    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        return pig;
    }

    /** Records the test tick of each mechanic step of one spitter; it can veto one step by name. */
    private static final class Log implements Consumer<MechanicEvent> {
        final Slagspitter spitter;
        final List<Integer> tells = new ArrayList<>(), lobs = new ArrayList<>(), hops = new ArrayList<>(), spoils = new ArrayList<>(),
                hits = new ArrayList<>(), patches = new ArrayList<>();
        @Nullable String veto;
        int tick;

        Log(GameTestHelper helper, Slagspitter spitter) {
            this.spitter = spitter;
            MechanicEvent.listen(this);
            TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(this));
            helper.onEachTick(() -> tick++);
        }

        @Override
        public void accept(MechanicEvent e) {
            if (e.mob() != spitter) return;
            switch (e.step()) {
                case "tell" -> tells.add(tick);
                case "lob" -> lobs.add(tick);
                case "hop" -> hops.add(tick);
                case "spoil" -> spoils.add(tick);
                case "glob_hit" -> hits.add(tick);
                case "patch" -> patches.add(tick);
                default -> { }
            }
            if (e.step().equals(veto)) e.cancel();
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The ring lasts exactly 16 ticks: it sits still, its ring glows (stage 1), nothing is spat and nothing is hurt;
     * the spit follows, and the ring goes dark again.
     */
    private static void tell(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 9.5);
        Slagspitter s = hunter(helper, 3.5, 3.5, pig);           // 8.5 blocks off
        Log log = new Log(helper, s);
        int[] ticks = {0}, dark = {0};
        boolean[] quiet = {true}, ended = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (s.core().in(Step.TELL)) {
                if (start[0] == null) start[0] = s.position();
                ticks[0]++;
                moved[0] = Math.max(moved[0], s.position().distanceTo(start[0]));
                if (s.core().stage() != 1) dark[0]++;
                if (!log.lobs.isEmpty() || s.globsInFlight() > 0 || pig.getHealth() < pig.getMaxHealth()) quiet[0] = false;
            } else if (ticks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the tell runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(log.tells.size() == 1 && log.tells.getFirst() <= 3, "with its target 8.5 blocks off it starts at once: " + log.tells);
            helper.assertTrue(ticks[0] == 16, "the tell lasts 16 ticks, lasted " + ticks[0]);
            helper.assertTrue(dark[0] <= 1, "the ring glows through the tell, dark for " + dark[0] + " ticks");
            helper.assertTrue(quiet[0], "nothing is spat or hurt during the tell");
            helper.assertTrue(moved[0] < 0.05, "it sits still while it tells, moved " + moved[0]);
            helper.assertTrue(log.lobs.size() == 1 && s.lobs() == 1, "the spit follows the tell, saw " + log.lobs.size());
            helper.assertTrue(s.core().stage() == 0, "the ring goes dark after the tell");
        });
    }

    /**
     * The counter: a survival player steps within 3 blocks while the ring glows. It chokes on the shot (one spoil, no
     * spit, no glob ever leaves), hops away from the player and gains at least 2 blocks; the pig it aimed at is unhurt.
     */
    private static void crowdedTellSpoils(GameTestHelper helper) {
        Pig pig = pig(helper, 10.5, 10.5);
        Slagspitter s = hunter(helper, 4.5, 5.5, pig);           // 7.8 blocks off
        Log log = new Log(helper, s);
        ServerPlayer[] p = {null};
        double[] before = {0.0};
        boolean[] hopped = {false};
        helper.onEachTick(() -> {
            if (p[0] == null && s.core().in(Step.TELL) && s.core().elapsed() >= 4) {
                p[0] = TestPlayers.survivor(helper, new Vec3(1.5, 1, 5.5));
                before[0] = s.distanceTo(p[0]);
            }
            hopped[0] |= s.core().in(Step.HOP);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(p[0] != null, "a player stepped in during the tell");
            helper.assertTrue(log.spoils.size() == 1 && s.spoils() == 1, "the tell is spoiled once, " + log.spoils);
            helper.assertTrue(hopped[0] && log.hops.size() == 1, "it hops away, hops " + log.hops);
            helper.assertTrue(s.core().idle() && s.onGround(), "and lands, phase " + s.phase());
            double gained = s.distanceTo(p[0]) - before[0];
            helper.assertTrue(gained >= 2.0, "the hop takes it at least 2 blocks further from the player, gained " + gained);
            helper.assertTrue(log.lobs.isEmpty() && s.lobs() == 0 && s.globsInFlight() == 0, "no glob was spat: lobs " + log.lobs);
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth() && !pig.isOnFire(), "the pig it aimed at is untouched");
            helper.assertTrue(s.core().stage() == 0, "the ring went dark");
        });
    }

    /**
     * Failing the counter: the pig stays put 8.5 blocks off. The glob hits it for exactly 5, a patch opens at its feet,
     * and it catches fire.
     */
    private static void globHitsAndBurns(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 9.5);
        Slagspitter s = hunter(helper, 3.5, 3.5, pig);
        Log log = new Log(helper, s);
        float full = pig.getHealth();
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (lost[0] < 0 && pig.getHealth() < full) lost[0] = full - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.hits.size() == 1 && s.globHits() == 1, "the glob hit the pig, hits " + log.hits);
            helper.assertTrue(Math.abs(lost[0] - 5.0F) < 0.01F, "the glob does 5, the pig lost " + lost[0]);
            helper.assertTrue(s.patches().size() == 1, "a patch opened, " + s.patches().size());
            Vec3 c = s.patches().getFirst();
            helper.assertTrue(Math.hypot(c.x - pig.getX(), c.z - pig.getZ()) < 0.3 && Math.abs(c.y - pig.getY()) < 0.05,
                    "the patch is at the pig's feet: " + c + " vs " + pig.position());
            helper.assertTrue(pig.isOnFire(), "the pig in the patch burns");
        });
    }

    /**
     * The other counter: a survival player 8 blocks off holding a shield up toward it. The glob reaches the player and
     * the shield takes it: no damage from the glob (its patch still lands at the player's feet).
     */
    private static void shieldTakesGlob(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(10.5, 1, 5.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            p.setYRot(90.0F);            // facing -x, toward the spitter
            p.setYHeadRot(90.0F);
            p.yHeadRotO = 90.0F;
        });
        Slagspitter s = hunter(helper, 2.5, 5.5, p);
        Log log = new Log(helper, s);
        float before = p.getHealth();
        float[] atSplash = {-1.0F};
        // the patch is asked for right after the glob's hit, before anything burns: the glob's damage alone
        Consumer<MechanicEvent> splash = e -> {
            if (e.mob() == s && e.step().equals("patch") && atSplash[0] < 0) atSplash[0] = p.getHealth();
        };
        MechanicEvent.listen(splash);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(splash));
        helper.succeedWhen(() -> {
            helper.assertTrue(p.isBlocking(), "the player holds the shield up");
            helper.assertTrue(log.hits.size() == 1, "the glob reached the player, hits " + log.hits);
            helper.assertTrue(s.globHits() == 0, "the shield took it: no glob hurt anything");
            helper.assertTrue(atSplash[0] >= 0 && atSplash[0] >= before, "the player is unhurt by the glob, health " + atSplash[0] + " of " + before);
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** A target 5.7 blocks away, in plain sight, is too close to spit at (and not a player, so no hop): no tell. */
    private static void rangeMinOut(GameTestHelper helper) {
        Pig pig = pig(helper, 9.2, 5.5);
        Slagspitter s = hunter(helper, 3.5, 5.5, pig);
        Log log = new Log(helper, s);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(log.tells.isEmpty() && s.lobs() == 0, "5.7 blocks is too close: tells " + log.tells);
            helper.succeed();
        });
    }

    /** A target 6.3 blocks away is told for at once and spat at. */
    private static void rangeMinIn(GameTestHelper helper) {
        Pig pig = pig(helper, 9.8, 5.5);
        Slagspitter s = hunter(helper, 3.5, 5.5, pig);
        Log log = new Log(helper, s);
        helper.succeedWhen(() -> {
            helper.assertTrue(log.tells.size() == 1 && log.tells.getFirst() <= 3, "6.3 blocks is in range: tells " + log.tells);
            helper.assertTrue(log.lobs.size() == 1, "and it spits, lobs " + log.lobs);
        });
    }

    /**
     * The far end of the range, 20 blocks, is longer than the arena: within one tick the pig is set 19.8 blocks away
     * (in range) and then 20.3 (out of it) and put back. Its follow range reaches past 20, so it can pick such a target.
     */
    private static void rangeMax(GameTestHelper helper) {
        Slagspitter s = still(helper, 2.5, 5.5);
        Pig pig = pig(helper, 8.5, 5.5);
        helper.runAtTickTime(2, () -> {
            Vec3 home = pig.position();
            pig.snapTo(new Vec3(s.getX() + 19.8, s.getY(), s.getZ()));
            boolean near = s.inRange(pig);
            pig.snapTo(new Vec3(s.getX() + 20.3, s.getY(), s.getZ()));
            boolean far = s.inRange(pig);
            pig.snapTo(new Vec3(s.getX() + 6.0, s.getY(), s.getZ()));
            boolean edge = s.inRange(pig);
            pig.snapTo(new Vec3(s.getX() + 5.9, s.getY(), s.getZ()));
            boolean close = s.inRange(pig);
            pig.snapTo(home);
            helper.assertTrue(near, "19.8 blocks is in range");
            helper.assertTrue(!far, "20.3 blocks is out of range");
            helper.assertTrue(edge && !close, "6.0 blocks is in range and 5.9 is not");
            helper.assertTrue(s.getAttributeValue(Attributes.FOLLOW_RANGE) >= 20.0, "it notices targets 20 blocks off, follow range "
                    + s.getAttributeValue(Attributes.FOLLOW_RANGE));
            helper.succeed();
        });
    }

    /**
     * The glob's way: from a mouth to a spot 20 blocks off it comes down on the spot after 33.3 ticks, having crossed
     * 0.6 blocks a tick and peaked about 7 blocks up; to one 6 blocks off it is down in 10 ticks.
     */
    private static void arc(GameTestHelper helper) {
        Vec3 from = new Vec3(0, 0.4, 0), to = new Vec3(20, 0, 0);
        Shot.Path far = Slagspitter.Arc.of(from, to, 0.6, 0.05, 40);
        double t = 20.0 / 0.6;
        helper.assertTrue(far.at(t).distanceTo(to) < 1.0E-6, "the 20-block lob comes down on its spot, at " + far.at(t));
        helper.assertTrue(Math.abs(far.at(1).x - 0.6) < 1.0E-9 && Math.abs(far.at(1).z) < 1.0E-9, "it crosses 0.6 blocks a tick, " + far.at(1));
        double peak = 0.0;
        for (double s = 0; s <= t; s += 0.25) peak = Math.max(peak, far.at(s).y);
        helper.assertTrue(peak > 6.5 && peak < 7.5, "it peaks about 7 blocks up, " + peak);
        helper.assertTrue(far.ticks() > t, "it keeps falling past the spot");
        Shot.Path near = Slagspitter.Arc.of(from, new Vec3(0, 0, 6), 0.6, 0.05, 40);
        helper.assertTrue(near.at(10).distanceTo(new Vec3(0, 0, 6)) < 1.0E-6, "a 6-block lob is down in 10 ticks, at " + near.at(10));
        helper.succeed();
    }

    /** The glob never homes: a pig that steps aside as it is spat is missed, and the patch opens where it stood. */
    private static void noHoming(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 9.5);
        Slagspitter s = still(helper, 3.5, 3.5);
        Log log = new Log(helper, s);
        Vec3 stood = helper.absoluteVec(new Vec3(9.5, 1, 9.5));
        Vec3 aside = helper.absoluteVec(new Vec3(9.5, 1, 5.0));
        helper.runAtTickTime(1, () -> helper.assertTrue(s.lobAt(pig), "the glob is spat"));
        helper.runAtTickTime(2, () -> pig.teleportTo(aside.x, aside.y, aside.z));
        helper.succeedWhen(() -> {
            helper.assertTrue(log.patches.size() == 1 && s.patches().size() == 1, "the glob came down, patches " + log.patches);
            helper.assertTrue(log.hits.isEmpty() && pig.getHealth() >= pig.getMaxHealth(), "the pig that stepped aside was missed");
            Vec3 c = s.patches().getFirst();
            helper.assertTrue(Math.hypot(c.x - stood.x, c.z - stood.z) < 0.6, "the patch is where the pig stood: " + c + " vs " + stood);
        });
    }

    /**
     * A patch has radius 1.5: a pig whose middle is 1.35 blocks from its centre catches fire, one 1.65 out never does,
     * and neither does a spitter standing in the middle of it.
     */
    private static void patchRadius(GameTestHelper helper) {
        Slagspitter s = still(helper, 5.5, 5.5);
        Pig in = pig(helper, 5.5 + 1.35, 5.5);
        Pig out = pig(helper, 5.5 - 1.65, 5.5);
        boolean[] reached = {false};
        helper.onEachTick(() -> reached[0] |= out.isOnFire() || s.isOnFire());
        helper.runAtTickTime(2, () -> helper.assertTrue(s.openPatch(helper.absoluteVec(new Vec3(5.5, 1, 5.5))), "the patch opens"));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(in.isOnFire(), "1.35 blocks out is inside the patch");
            helper.assertTrue(!reached[0], "1.65 blocks out is outside it, and the spitter never burns");
            helper.succeed();
        });
    }

    /** A patch burns for 80 ticks; a pig put in its middle just after it is gone is not set on fire. */
    private static void patchLength(GameTestHelper helper) {
        Slagspitter s = still(helper, 2.5, 2.5);
        int[] alive = {0};
        long[] gone = {-1};
        boolean[] started = {false};
        Pig[] late = {null};
        helper.onEachTick(() -> {
            if (!s.patches().isEmpty()) {
                started[0] = true;
                alive[0]++;
            } else if (started[0] && gone[0] < 0) {
                gone[0] = helper.getTick();
            }
            if (gone[0] >= 0 && late[0] == null && helper.getTick() >= gone[0] + 2) late[0] = pig(helper, 6.5, 6.5);
        });
        helper.runAtTickTime(2, () -> s.openPatch(helper.absoluteVec(new Vec3(6.5, 1, 6.5))));
        helper.succeedWhen(() -> {
            helper.assertTrue(gone[0] >= 0, "the patch goes out, burning " + alive[0] + " ticks so far");
            helper.assertTrue(Math.abs(alive[0] - 80) <= 1, "the patch burns 80 ticks, burned " + alive[0]);
            helper.assertTrue(late[0] != null && helper.getTick() >= gone[0] + 8, "a pig waits in the middle a few ticks");
            helper.assertTrue(!late[0].isOnFire(), "a patch that went out burns no one");
        });
    }

    /** A listener that cancels the "patch" step stops the patch: the glob still hits for 5, but nothing burns. */
    private static void patchCancel(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 9.5);
        Slagspitter s = still(helper, 3.5, 3.5);
        Log log = new Log(helper, s);
        log.veto = "patch";
        helper.runAtTickTime(1, () -> s.lobAt(pig));
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(log.hits.size() == 1 && log.patches.size() == 1, "the glob hit and asked for its patch: hits " + log.hits);
            helper.assertTrue(s.patches().isEmpty(), "a cancelled patch never opens");
            helper.assertTrue(!pig.isOnFire(), "nothing burns");
            helper.succeed();
        });
    }

    /** A survival player 4.8 blocks away: it hops away at once and gains at least 2 blocks. */
    private static void hop(GameTestHelper helper) {
        Slagspitter s = hunter(helper, 6.5, 6.0, null);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(1.7, 1, 6.0));
        Log log = new Log(helper, s);
        double before = s.distanceTo(p);
        boolean[] hopping = {false};
        helper.onEachTick(() -> hopping[0] |= s.core().in(Step.HOP));
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(before - 4.8) < 0.01, "the player starts 4.8 blocks off, " + before);
            helper.assertTrue(hopping[0] && !log.hops.isEmpty() && log.hops.getFirst() <= 3, "it hops at once: " + log.hops);
            helper.assertTrue(s.core().idle() && s.onGround(), "and lands, phase " + s.phase());
            double gained = s.distanceTo(p) - before;
            helper.assertTrue(gained >= 2.0, "the hop takes it at least 2 blocks further away, gained " + gained);
        });
    }

    /** A survival player 5.3 blocks away is not close enough: no hop. */
    private static void hopRange(GameTestHelper helper) {
        Slagspitter s = hunter(helper, 6.8, 6.0, null);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(1.5, 1, 6.0));
        Log log = new Log(helper, s);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(Math.abs(s.distanceTo(p) - 5.3) < 0.05, "the player stands 5.3 blocks off, " + s.distanceTo(p));
            helper.assertTrue(log.hops.isEmpty() && s.hops() == 0, "5.3 blocks is not within 5: hops " + log.hops);
            helper.succeed();
        });
    }

    /** At melee range it only nips: the pig beside it loses exactly 3, its attack. */
    private static void nip(GameTestHelper helper) {
        Pig pig = pig(helper, 6.6, 5.5);
        Slagspitter s = hunter(helper, 5.5, 5.5, pig);
        float full = pig.getHealth();
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (lost[0] < 0 && pig.getHealth() < full) lost[0] = full - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0, "it nipped the pig");
            helper.assertTrue(Math.abs(lost[0] - 3.0F) < 0.01F, "the nip does 3, the pig lost " + lost[0]);
            helper.assertTrue(s.nips() >= 1 && s.lobs() == 0, "a nip, never a glob, at this range");
        });
    }

    /** With its target always in range it tells once every 60 ticks, and spits after each. */
    private static void interval(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 9.5);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        Slagspitter s = hunter(helper, 3.5, 3.5, pig);
        Log log = new Log(helper, s);
        int[] most = {0};
        helper.onEachTick(() -> most[0] = Math.max(most[0], s.cooldown()));
        helper.succeedWhen(() -> {
            helper.assertTrue(log.tells.size() >= 2 && log.lobs.size() >= 2, "two tells and two spits so far: tells " + log.tells + ", lobs " + log.lobs);
            helper.assertTrue(log.tells.get(1) - log.tells.get(0) == 60, "one tell every 60 ticks: " + log.tells);
            helper.assertTrue(log.lobs.get(1) - log.lobs.get(0) == 60, "one spit every 60 ticks: " + log.lobs);
            helper.assertTrue(most[0] == 60, "the wait is 60 ticks, saw " + most[0]);
        });
    }

    /** Health 20, attack 3, armour 2, a 0.9 x 0.8 hitbox; a monster that fire doesn't hurt. */
    private static void stats(GameTestHelper helper) {
        Slagspitter s = still(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(s.getMaxHealth() - 20.0F) < 0.01F && Math.abs(s.getHealth() - 20.0F) < 0.01F,
                    "health 20, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < 0.01, "attack 3");
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(s.getBbWidth() - 0.9F) < 0.02F && Math.abs(s.getBbHeight() - 0.8F) < 0.02F,
                    "hitbox 0.9 x 0.8, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.assertTrue(s.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(s.getType().fireImmune(), "fire doesn't hurt it");
            helper.succeed();
        });
    }

    /** Home: the nether wastes and not the crimson forest; spawn weight 15, in groups of 1 to 2. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("slagspitter"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.NETHER_WASTES)), "its home holds the nether wastes");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.CRIMSON_FOREST)), "its home never holds the crimson forest");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/slagspitter")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SLAGSPITTER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 15, "spawn weight 15, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "in groups of 1 to 2, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
