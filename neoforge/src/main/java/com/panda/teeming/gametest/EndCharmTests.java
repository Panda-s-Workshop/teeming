package com.panda.teeming.gametest;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.charm.Charms;
import com.panda.teeming.item.CharmItem;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.AbyssAngler;
import com.panda.teeming.mob.end.AnchorGolem;
import com.panda.teeming.mob.end.Brinkram;
import com.panda.teeming.mob.end.Brinkray;
import com.panda.teeming.mob.end.ChorusHusk;
import com.panda.teeming.mob.end.ExileWraith;
import com.panda.teeming.mob.end.Gatecaster;
import com.panda.teeming.mob.end.Levimoth;
import com.panda.teeming.mob.end.LiftstoneMonolith;
import com.panda.teeming.mob.end.Mnemovore;
import com.panda.teeming.mob.end.Nullcantor;
import com.panda.teeming.mob.end.Pullstar;
import com.panda.teeming.mob.end.Riftjackal;
import com.panda.teeming.mob.end.RiftmineSapper;
import com.panda.teeming.mob.end.Rotfang;
import com.panda.teeming.mob.end.Spirebloom;
import com.panda.teeming.mob.end.Underclinger;
import com.panda.teeming.mob.end.WarpseedBloom;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.registry.ModCharms;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The End charms: Grounding, Rift, Rot Ward and Memory. Each test runs the real mechanics side by side on a target
 * holding the charm in the off-hand and one holding the same charm in the main hand, and asserts the off-hand changes
 * each number by what {@link Charms} says while the main hand changes nothing. Still pigs stand in where a mechanic
 * takes any living target (a charm counts on any holder), and real survival players where it acts on players only.
 * A "fails 50 %" charm is measured over many rolls of the mob's own random, with the tolerance its test explains.
 */
final class EndCharmTests {
    private EndCharmTests() {}

    /** The seed of the first rift jackal's rolls in `charm_rift` (each next jackal takes the next seed). */
    private static final long RIFT_SEED = 4242L;

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("charm_grounding", EndCharmTests::grounding);
        TeemingGameTests.wide("charm_grounding");
        TeemingGameTests.timeLimit("charm_grounding", 140);
        out.accept("charm_rift", EndCharmTests::rift);
        TeemingGameTests.wide("charm_rift");
        TeemingGameTests.timeLimit("charm_rift", 120);
        out.accept("charm_rot_ward", EndCharmTests::rotWard);
        out.accept("charm_memory", EndCharmTests::memory);
        out.accept("charm_grounding_underclinger", EndCharmTests::groundingUnderclinger);
        out.accept("charm_grounding_angler", EndCharmTests::groundingAngler);
        out.accept("charm_grounding_spirebloom", EndCharmTests::groundingSpirebloom);
        out.accept("charm_grounding_brinkray", EndCharmTests::groundingBrinkray);
        out.accept("charm_rift_warpseed", EndCharmTests::riftWarpseed);
        out.accept("charm_rift_exile", EndCharmTests::riftExile);
        TeemingGameTests.wide("charm_rift_exile");
    }

    /** A still pig with 100 health holding {@code charm} in {@code hand}, removed when the test ends wherever it went. */
    private static Pig pig(GameTestHelper helper, Vec3 at, DeferredItem<CharmItem> charm, InteractionHand hand) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        pig.setItemInHand(hand, charm.get().getDefaultInstance());
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** As {@link #pig}, but it keeps its physics (no goals), so a pull can move it. */
    private static Pig movingPig(GameTestHelper helper, Vec3 at, DeferredItem<CharmItem> charm, InteractionHand hand) {
        Pig pig = pig(helper, at, charm, hand);
        pig.setNoAi(false);
        pig.removeFreeWill();
        return pig;
    }

    private static ServerPlayer survivor(GameTestHelper helper, Vec3 at, DeferredItem<CharmItem> charm, InteractionHand hand) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemInHand(hand, charm.get().getDefaultInstance());
        return p;
    }

    private static boolean near(double value, double expected, double tolerance) {
        return Math.abs(value - expected) <= tolerance;
    }

    // ---- Grounding Charm -------------------------------------------------------------------------------------------

    /**
     * Five End mechanics in the wide arena (its floor's top is at y 0), each on two pigs: one holding a Grounding Charm in the off-hand, one in the
     * main hand. Every main-hand number is the mechanic's own and every off-hand number {@link Charms#GROUNDING} of it:
     * <ul>
     * <li>a Pullstar's pull (with no AI it never dims): pigs 6 blocks off each side, read over ticks 15 to 25 as the
     * strength test reads it, drift at 0.09 and 0.045 a tick (a few thousandths of friction either way);</li>
     * <li>a Levimoth's dust cloud: Levitation II for 60 ticks and for 30, read the tick it lands (it may tick twice
     * before the reading, so 2 ticks either way);</li>
     * <li>a Riftmine Sapper's launch: straight up at 1.35 and 0.675, read straight after it (exact);</li>
     * <li>a Liftstone Monolith's lift: the highest each pig rose, 6 and 3 blocks (it is set in place each tick);</li>
     * <li>a Brinkram's charge, through the Shellward call site: thrown 2.5 out and 0.4 up, and 1.25 and 0.2, read the
     * tick the hit lands (exact, as the Brinkram's own throw test reads it).</li>
     * </ul>
     */
    private static void grounding(GameTestHelper helper) {
        DeferredItem<CharmItem> charm = ModCharms.GROUNDING_CHARM;
        InteractionHand off = InteractionHand.OFF_HAND, main = InteractionHand.MAIN_HAND;

        // the pull
        Pullstar star = helper.spawn(ModMobs.PULLSTAR.get(), new Vec3(10.5, 0, 20.5));
        star.core().setLook(LookDef.BASE, true);
        star.setNoAi(true);
        TestPlayers.whenDone(helper, star::discard);
        Pig offPulled = movingPig(helper, new Vec3(4.5, 0, 20.5), charm, off);
        Pig mainPulled = movingPig(helper, new Vec3(16.5, 0, 20.5), charm, main);
        double[] pullX = new double[4];
        helper.runAtTickTime(15, () -> {
            pullX[0] = offPulled.getX();
            pullX[1] = mainPulled.getX();
        });
        helper.runAtTickTime(25, () -> {
            pullX[2] = offPulled.getX();
            pullX[3] = mainPulled.getX();
        });

        // the Levitation
        Levimoth moth = helper.spawn(ModMobs.LEVIMOTH.get(), new Vec3(40.5, 0, 20.5));
        moth.setNoAi(true);
        TestPlayers.whenDone(helper, moth::discard);
        Pig offLifted = pig(helper, new Vec3(38.9, 0, 20.5), charm, off);
        Pig mainLifted = pig(helper, new Vec3(42.1, 0, 20.5), charm, main);
        int[] offLev = firstEffect(helper, offLifted), mainLev = firstEffect(helper, mainLifted);
        helper.runAtTickTime(2, () -> helper.assertTrue(moth.release(helper.getLevel()) != null, "the Levimoth shook out its cloud"));

        // the launch
        RiftmineSapper sapper = helper.spawn(ModMobs.RIFTMINE_SAPPER.get(), new Vec3(60.5, 0, 20.5));
        sapper.removeFreeWill();
        sapper.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, sapper::discard);
        Pig offLaunched = pig(helper, new Vec3(58.5, 0, 20.5), charm, off);
        Pig mainLaunched = pig(helper, new Vec3(62.5, 0, 20.5), charm, main);
        double[] launch = {Double.NaN, Double.NaN};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(sapper.launch(helper.getLevel(), offLaunched) && sapper.launch(helper.getLevel(), mainLaunched), "both pigs launched");
            launch[0] = offLaunched.getDeltaMovement().y;
            launch[1] = mainLaunched.getDeltaMovement().y;
        });

        // the lift
        LiftstoneMonolith mono = helper.spawn(ModMobs.LIFTSTONE_MONOLITH.get(), new Vec3(80.5, 0, 20.5));
        mono.removeFreeWill();
        mono.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, mono::discard);
        Pig offRaised = pig(helper, new Vec3(77.5, 0, 20.5), charm, off);
        Pig mainRaised = pig(helper, new Vec3(83.5, 0, 20.5), charm, main);
        double offFloor = offRaised.getY(), mainFloor = mainRaised.getY();
        double[] rose = {0.0, 0.0};
        helper.runAtTickTime(1, () -> helper.assertTrue(mono.rise(null), "the Monolith rises"));
        helper.onEachTick(() -> {
            rose[0] = Math.max(rose[0], offRaised.getY() - offFloor);
            rose[1] = Math.max(rose[1], mainRaised.getY() - mainFloor);
        });

        // the charge
        Vec3[] offThrow = charge(helper, 32.5, charm, off), mainThrow = charge(helper, 40.5, charm, main);

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() >= 62, "the lift has run through its slam");
            double g = Charms.GROUNDING;
            double offDrift = (pullX[2] - pullX[0]) / 10.0, mainDrift = (pullX[1] - pullX[3]) / 10.0;
            helper.assertTrue(near(mainDrift, Pullstar.PULL_DRIFT, 0.005), "main hand: pulled at " + Pullstar.PULL_DRIFT + ", saw " + mainDrift);
            helper.assertTrue(near(offDrift, Pullstar.PULL_DRIFT * g, 0.005), "off-hand: pulled at " + Pullstar.PULL_DRIFT * g + ", saw " + offDrift);

            helper.assertTrue(offLev[0] >= 0 && mainLev[0] >= 0, "the cloud lifted both pigs, read " + offLev[0] + " and " + mainLev[0]);
            helper.assertTrue(near(mainLev[0], Levimoth.LEVITATION_TICKS, 2), "main hand: Levitation for " + Levimoth.LEVITATION_TICKS + ", saw " + mainLev[0]);
            helper.assertTrue(near(offLev[0], Levimoth.LEVITATION_TICKS * g, 2), "off-hand: Levitation for " + Levimoth.LEVITATION_TICKS * g + ", saw " + offLev[0]);
            helper.assertTrue(offLev[1] == Levimoth.LEVITATION_LEVEL && mainLev[1] == Levimoth.LEVITATION_LEVEL,
                    "both at the cloud's strength, amplifiers " + offLev[1] + " and " + mainLev[1]);

            helper.assertTrue(near(launch[1], RiftmineSapper.LAUNCH_SPEED, 1.0E-9), "main hand: launched at " + RiftmineSapper.LAUNCH_SPEED + ", saw " + launch[1]);
            helper.assertTrue(near(launch[0], RiftmineSapper.LAUNCH_SPEED * g, 1.0E-9), "off-hand: launched at " + RiftmineSapper.LAUNCH_SPEED * g + ", saw " + launch[0]);

            helper.assertTrue(near(rose[1], LiftstoneMonolith.LIFT_HEIGHT, 0.05), "main hand: lifted " + LiftstoneMonolith.LIFT_HEIGHT + ", rose " + rose[1]);
            helper.assertTrue(near(rose[0], LiftstoneMonolith.LIFT_HEIGHT * g, 0.05), "off-hand: lifted " + LiftstoneMonolith.LIFT_HEIGHT * g + ", rose " + rose[0]);

            helper.assertTrue(offThrow[0] != null && mainThrow[0] != null, "both charges hit");
            Vec3 m = mainThrow[0], o = offThrow[0];
            helper.assertTrue(near(m.x, 2.5, 0.01) && near(m.y, 0.4, 0.01), "main hand: thrown 2.5 out and 0.4 up, motion " + m);
            helper.assertTrue(near(o.x, 2.5 * g, 0.01) && near(o.y, 0.4 * g, 0.01), "off-hand: thrown " + 2.5 * g + " out and " + 0.4 * g + " up, motion " + o);
            TeemingMod.LOGGER.info("charm_grounding: drift off-hand {} main hand {}; Levitation {} / {} ticks; launch {} / {}; lift {} / {}; charge throw {} / {}",
                    offDrift, mainDrift, offLev[0], mainLev[0], launch[0], launch[1], rose[0], rose[1], o, m);
        });
    }

    /** A Levitation's {duration, amplifier} on {@code target} the first tick it has one; {-1, -1} until then. */
    private static int[] firstEffect(GameTestHelper helper, LivingEntity target) {
        int[] seen = {-1, -1};
        helper.onEachTick(() -> {
            MobEffectInstance on = target.getEffect(MobEffects.LEVITATION);
            if (seen[0] < 0 && on != null) {
                seen[0] = on.getDuration();
                seen[1] = on.getAmplifier();
            }
        });
        return seen;
    }

    /** A Brinkram at x 3 charging a still pig at x 10.5 along z = {@code z}; the pig's motion the tick the hit lands. */
    private static Vec3[] charge(GameTestHelper helper, double z, DeferredItem<CharmItem> charm, InteractionHand hand) {
        Brinkram ram = helper.spawn(ModMobs.BRINKRAM.get(), new Vec3(3.0, 0, z));
        ram.core().setLook(LookDef.BASE, true);
        ram.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        ram.resetCooldown();
        TestPlayers.whenDone(helper, ram::discard);
        Pig pig = pig(helper, new Vec3(10.5, 0, z), charm, hand);
        ram.setTarget(pig);
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && pig.getHealth() < 100.0F) thrown[0] = pig.getDeltaMovement();
        });
        return thrown;
    }

    // ---- Rift Charm ------------------------------------------------------------------------------------------------

    /**
     * Forced teleports and exit rifts, in the wide arena (its floor's top is at y 0).
     * <ul>
     * <li>The share: a Chorus Husk's burst (its own random rolls each body's charm) catches 20 pigs holding a Rift Charm
     * in the off-hand and 20 in the main hand; it bursts 10 times, the pigs set back each time: 200 rolls a side. Every
     * main-hand pig is moved every time. The off-hand pigs are moved {@link Charms#RIFT_FAIL} of the time: 100 of 200
     * expected, a spread of 7.1 (sqrt(200 x 0.25)); the test allows 4 spreads either way, 72 to 128, which a fair 50 %
     * misses about once in 16,000 runs while a charm that did nothing (200) or everything (0) never passes.</li>
     * <li>A rift rolls once per step in: 8 off-hand pigs and 2 main-hand pigs each stand in a Riftjackal's rift. Both
     * main-hand pigs are thrown at once; the off-hand pigs that a rift spared (did not throw) on the first tick are all
     * still standing in it 25 ticks later (a roll every tick would throw nearly all of them).</li>
     * <li>An exit rift opens {@link Charms#RIFT_EARLY} earlier: a Gatecaster's tell at the main-hand pig lasts 20 ticks
     * (a tick either way for when the counting callback runs), at the off-hand pig exactly 10 more, and both lock their
     * aim 12 ticks in.</li>
     * </ul>
     */
    private static void rift(GameTestHelper helper) {
        DeferredItem<CharmItem> charm = ModCharms.RIFT_CHARM;
        InteractionHand off = InteractionHand.OFF_HAND, main = InteractionHand.MAIN_HAND;

        // the share, by a Chorus Husk's burst
        ChorusHusk husk = helper.spawn(ModMobs.CHORUS_HUSK.get(), new Vec3(20.5, 0, 20.5));
        husk.core().setLook(LookDef.BASE, true);
        husk.removeFreeWill();
        husk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        husk.fixRoll(new ChorusHusk.Teleport(4.0, Math.PI / 2));          // +Z, inside the arena
        Consumer<MechanicEvent> quiet = e -> {
            if (e.mob() == husk && e.step().equals("spared")) e.cancel();   // no chorus fruit piling up between bursts
        };
        MechanicEvent.listen(quiet);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(quiet));
        TestPlayers.whenDone(helper, husk::discard);
        List<Pig> offCrowd = new ArrayList<>(), mainCrowd = new ArrayList<>();
        List<Vec3> offHome = new ArrayList<>(), mainHome = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double a = i * (Math.PI * 2 / 40), r = 1.5 + (i % 3) * 0.6;
            Vec3 at = new Vec3(20.5 + Math.cos(a) * r, 0, 20.5 + Math.sin(a) * r);
            Pig p = pig(helper, at, charm, i % 2 == 0 ? off : main);
            (i % 2 == 0 ? offCrowd : mainCrowd).add(p);
            (i % 2 == 0 ? offHome : mainHome).add(helper.absoluteVec(at));
        }
        int[] moved = {0, 0};
        int bursts = 10;
        helper.runAtTickTime(2, () -> {
            for (int b = 0; b < bursts; b++) {
                husk.burst(helper.getLevel());
                moved[0] += backHome(offCrowd, offHome);
                moved[1] += backHome(mainCrowd, mainHome);
            }
        });

        // a rift rolls once per step in
        List<Pig> offInRift = new ArrayList<>(), mainInRift = new ArrayList<>();
        List<Vec3> offRiftAt = new ArrayList<>(), mainRiftAt = new ArrayList<>();
        List<Riftjackal> jackals = new ArrayList<>();
        for (int j = 0; j < 5; j++) {
            Riftjackal jackal = helper.spawn(ModMobs.RIFTJACKAL.get(), new Vec3(66.5 + j * 6, 0, 4.5));
            jackal.setNoAi(true);
            TestPlayers.whenDone(helper, jackal::discard);
            jackals.add(jackal);
            for (int k = 0; k < 2; k++) {
                Vec3 at = new Vec3(66.5 + j * 6, 0, 16.5 + k * 12);
                boolean offHand = j < 4;
                (offHand ? offInRift : mainInRift).add(pig(helper, at, charm, offHand ? off : main));
                (offHand ? offRiftAt : mainRiftAt).add(helper.absoluteVec(at));
            }
        }
        helper.runAtTickTime(2, () -> {
            // each jackal's rolls come from a seeded source, so the eight off-hand steps split the same way every run
            // and the test can ask for some spared and some thrown without a one-in-256 miss
            for (int j = 0; j < 5; j++) jackals.get(j).getRandom().setSeed(RIFT_SEED + j);
            for (int j = 0; j < 5; j++) {
                for (int k = 0; k < 2; k++) {
                    int i = j * 2 + k;
                    jackals.get(j).openRift(j < 4 ? offRiftAt.get(i) : mainRiftAt.get(i - 8));
                }
            }
        });
        int[] stayed = {-1, -1};
        helper.runAtTickTime(6, () -> {
            stayed[0] = home(offInRift, offRiftAt);
            helper.assertTrue(home(mainInRift, mainRiftAt) == 0, "the main-hand pigs were thrown as they stood in their rifts");
        });
        helper.runAtTickTime(31, () -> stayed[1] = home(offInRift, offRiftAt));

        // exit rifts
        Gatecaster[] casters = {caster(helper, 40.5), caster(helper, 50.5)};
        Pig offMark = pig(helper, new Vec3(40.5, 0, 14.5), charm, off), mainMark = pig(helper, new Vec3(50.5, 0, 14.5), charm, main);
        long[] start = {-1};
        int[] tell = {0, 0};
        boolean[] done = {false, false}, lockedAt13 = {false, false};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(casters[0].weave(offMark) && casters[1].weave(mainMark), "both casters weave");
            start[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (start[0] < 0) return;
            for (int i = 0; i < 2; i++) {
                boolean telling = casters[i].core().in(Gatecaster.Step.TELL);
                if (helper.getTick() - start[0] == Gatecaster.TRACK_TICKS + 1) lockedAt13[i] = telling && casters[i].locked();
                if (telling && !done[i]) tell[i]++;
                else if (tell[i] > 0) done[i] = true;
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(stayed[1] >= 0 && done[0] && done[1], "the rifts and both tells ran");
            int rolls = bursts * offCrowd.size();
            helper.assertTrue(moved[1] == rolls, "main hand: every one of " + rolls + " burst teleports landed, " + moved[1] + " did");
            helper.assertTrue(moved[0] >= 72 && moved[0] <= 128, "off-hand: about " + rolls * (1.0 - Charms.RIFT_FAIL)
                    + " of " + rolls + " teleports landed (72 to 128), " + moved[0] + " did");
            helper.assertTrue(stayed[0] >= 1 && stayed[0] <= offInRift.size() - 1, "off-hand: some of the " + offInRift.size()
                    + " pigs stepping into a rift are spared and some thrown, " + stayed[0] + " spared");
            helper.assertTrue(stayed[1] == stayed[0], "the off-hand pigs a rift spared stayed in it: " + stayed[0] + " after its first tick, "
                    + stayed[1] + " 25 ticks on");
            int offTell = tell[0], mainTell = tell[1];
            // both tells are counted by the same callback, so a tick either way for its order is the same tick on both
            helper.assertTrue(Math.abs(mainTell - Gatecaster.TELL_TICKS) <= 1, "main hand: the exit rift shows " + Gatecaster.TELL_TICKS
                    + " ticks, showed " + mainTell);
            helper.assertTrue(offTell - mainTell == Charms.RIFT_EARLY, "off-hand: the exit rift shows " + Charms.RIFT_EARLY
                    + " ticks earlier, showed " + offTell + " against " + mainTell);
            helper.assertTrue(lockedAt13[0] && lockedAt13[1], "both aims locked " + Gatecaster.TRACK_TICKS + " ticks in");
            TeemingMod.LOGGER.info("charm_rift: burst teleports landed off-hand {} / {} main hand {} / {}; rifts spared {} of 8 off-hand pigs (not thrown), {} still unmoved 25 ticks on; exit rift {} / {} ticks",
                    moved[0], rolls, moved[1], rolls, stayed[0], stayed[1], offTell, mainTell);
        });
    }

    /** Sets each pig back on its home (absolute); returns how many had been moved off it. */
    private static int backHome(List<Pig> pigs, List<Vec3> homes) {
        int moved = 0;
        for (int i = 0; i < pigs.size(); i++) {
            Pig p = pigs.get(i);
            Vec3 h = homes.get(i);
            if (p.position().distanceToSqr(h) > 0.25) {
                moved++;
                p.teleportTo(h.x, h.y, h.z);
            }
        }
        return moved;
    }

    /** How many pigs still stand on their home (absolute). */
    private static int home(List<Pig> pigs, List<Vec3> homes) {
        int n = 0;
        for (int i = 0; i < pigs.size(); i++) if (pigs.get(i).position().distanceToSqr(homes.get(i)) <= 0.25) n++;
        return n;
    }

    /** A Gatecaster at (x, 0, 8.5) with no goals, blind to anything it isn't told to weave at. */
    private static Gatecaster caster(GameTestHelper helper, double x) {
        Gatecaster g = helper.spawn(ModMobs.GATECASTER.get(), new Vec3(x, 0, 8.5));
        g.core().setLook(LookDef.BASE, true);
        g.removeFreeWill();
        g.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, g::discard);
        return g;
    }

    // ---- Rot Ward Charm --------------------------------------------------------------------------------------------

    /**
     * Two Rotfangs each bite a still pig, one holding a Rot Ward Charm in the off-hand and one in the main hand, and an
     * Anchor Golem weighs down two more pigs held the same two ways. Read the tick each lands: the main hand keeps Void
     * Rot's full {@link Rotfang#ROT_TICKS} (160) and Heavy's {@link AnchorGolem#HEAVY_TICKS} (120); the off-hand
     * {@link Charms#ROT_WARD} of each (80, 60). The weight is read straight after it (exact); a bite's rot may tick once
     * before its reading, so it allows 1.
     */
    private static void rotWard(GameTestHelper helper) {
        DeferredItem<CharmItem> charm = ModCharms.ROT_WARD_CHARM;
        int[] offRot = bitten(helper, 2.5, charm, InteractionHand.OFF_HAND), mainRot = bitten(helper, 9.5, charm, InteractionHand.MAIN_HAND);
        AnchorGolem golem = helper.spawn(ModMobs.ANCHOR_GOLEM.get(), new Vec3(1.5, 1, 6.0));
        golem.removeFreeWill();
        golem.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, golem::discard);
        Pig offHeavy = pig(helper, new Vec3(3.5, 1, 4.5), charm, InteractionHand.OFF_HAND);
        Pig mainHeavy = pig(helper, new Vec3(3.5, 1, 7.5), charm, InteractionHand.MAIN_HAND);
        int[] heavy = {-1, -1};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(golem.weigh(helper.getLevel(), offHeavy) && golem.weigh(helper.getLevel(), mainHeavy), "both pigs weighed down");
            heavy[0] = offHeavy.getEffect(ModEffects.HEAVY.holder()).getDuration();
            heavy[1] = mainHeavy.getEffect(ModEffects.HEAVY.holder()).getDuration();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(offRot[0] >= 0 && mainRot[0] >= 0 && heavy[0] >= 0, "both bites rotted and both pigs were weighed down");
            int rotKept = (int) Math.round(Rotfang.ROT_TICKS * Charms.ROT_WARD), heavyKept = (int) Math.round(AnchorGolem.HEAVY_TICKS * Charms.ROT_WARD);
            helper.assertTrue(near(mainRot[0], Rotfang.ROT_TICKS, 1), "main hand: Void Rot for " + Rotfang.ROT_TICKS + ", saw " + mainRot[0]);
            helper.assertTrue(near(offRot[0], rotKept, 1), "off-hand: Void Rot for " + rotKept + ", saw " + offRot[0]);
            helper.assertTrue(heavy[1] == AnchorGolem.HEAVY_TICKS, "main hand: Heavy for " + AnchorGolem.HEAVY_TICKS + ", saw " + heavy[1]);
            helper.assertTrue(heavy[0] == heavyKept, "off-hand: Heavy for " + heavyKept + ", saw " + heavy[0]);
            TeemingMod.LOGGER.info("charm_rot_ward: Void Rot off-hand {} main hand {} ticks; Heavy off-hand {} main hand {} ticks",
                    offRot[0], mainRot[0], heavy[0], heavy[1]);
        });
    }

    /** A Rotfang at (5.5, z) facing +X bites a still pig at (6.9, z) once; the pig's Void Rot length the tick it lands. */
    private static int[] bitten(GameTestHelper helper, double z, DeferredItem<CharmItem> charm, InteractionHand hand) {
        Rotfang fang = helper.spawn(ModMobs.ROTFANG.get(), new Vec3(5.5, 1, z));
        fang.core().setLook(LookDef.BASE, true);
        fang.removeFreeWill();
        fang.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        fang.setYRot(-90.0F);
        fang.setYHeadRot(-90.0F);
        fang.yBodyRot = -90.0F;
        TestPlayers.whenDone(helper, fang::discard);
        Pig pig = pig(helper, new Vec3(6.9, 1, z), charm, hand);
        fang.setTarget(pig);
        int[] seen = {-1};
        helper.onEachTick(() -> {
            if (fang.bites() >= 1 && !fang.isRemoved()) fang.discard();                 // one bite only
            MobEffectInstance rot = pig.getEffect(ModEffects.VOID_ROT.holder());
            if (seen[0] < 0 && rot != null) seen[0] = rot.getDuration();
        });
        return seen;
    }

    // ---- Memory Charm ----------------------------------------------------------------------------------------------

    /**
     * Two survival players, one holding a Memory Charm in the off-hand and one in the main hand, each with 1000 levels.
     * <ul>
     * <li>Theft: a Mnemovore's theft (its own random rolls the charm) is tried 200 times on each. The main-hand player
     * loses a level every time; the off-hand player {@link Charms#MEMORY_FAIL} of the time, 100 expected with a spread
     * of 7.1, allowed 72 to 128 (4 spreads, as the Rift Charm's share), and loses exactly the levels taken.</li>
     * <li>Suppression: both stand in a Nullcantor's ring in four pieces of Protection IV. Outside it the armour protects
     * 16 (4 per piece); inside, the main-hand player's protects 0 and each piece reads Protection 0, the off-hand
     * player's protects 8 and each piece reads Protection II (half of IV), and the off-hand player's Sharpness V sword
     * adds 2 to a 6 (Sharpness III's 0.5 + 0.5 x 3; V would add 3).</li>
     * </ul>
     */
    private static void memory(GameTestHelper helper) {
        DeferredItem<CharmItem> charm = ModCharms.MEMORY_CHARM;
        Nullcantor cantor = helper.spawn(ModMobs.NULLCANTOR.get(), new Vec3(1.5, 1, 5.5));
        cantor.removeFreeWill();
        cantor.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        cantor.core().setLook(LookDef.BASE, true);
        // its ring reaches past the arena's edge: it nulls no one but this test's players
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == cantor && e.step().equals("null") && e.target() != null && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        TestPlayers.whenDone(helper, cantor::discard);

        Mnemovore vore = helper.spawn(ModMobs.MNEMOVORE.get(), new Vec3(10.5, 1, 10.5));
        vore.removeFreeWill();
        vore.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, () -> {
            vore.setMemories(0, 0);                                                     // test experience is not spilled
            vore.discard();
        });

        ServerPlayer off = survivor(helper, new Vec3(5.5, 1, 3.5), charm, InteractionHand.OFF_HAND);
        ServerPlayer main = survivor(helper, new Vec3(5.5, 1, 8.5), charm, InteractionHand.MAIN_HAND);
        for (ServerPlayer p : List.of(off, main)) {
            p.giveExperienceLevels(1000);
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                Item item = switch (slot) {
                    case HEAD -> Items.DIAMOND_HELMET;
                    case CHEST -> Items.DIAMOND_CHESTPLATE;
                    case LEGS -> Items.DIAMOND_LEGGINGS;
                    default -> Items.DIAMOND_BOOTS;
                };
                p.setItemSlot(slot, enchanted(helper, item, Enchantments.PROTECTION, 4));
            }
            helper.onEachTick(() -> {
                if (!p.isRemoved()) p.doTick();                                          // its status and enchantment tick
            });
        }
        off.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 5));
        Pig dummy = helper.spawn(EntityTypes.PIG, new Vec3(9.5, 1, 5.5));
        dummy.setNoAi(true);

        // before any tick, so before the ring has nulled anyone
        float[] before = {EnchantmentHelper.getDamageProtection(helper.getLevel(), off, off.damageSources().generic()),
                EnchantmentHelper.getDamageProtection(helper.getLevel(), main, main.damageSources().generic())};
        int tries = 200;
        int[] took = {0, 0}, lost = {0, 0};
        helper.runAtTickTime(1, () -> {
            int offLevels = off.experienceLevel, mainLevels = main.experienceLevel;
            for (int i = 0; i < tries; i++) {
                if (vore.steal(helper.getLevel(), off)) took[0]++;
                if (vore.steal(helper.getLevel(), main)) took[1]++;
            }
            lost[0] = offLevels - off.experienceLevel;
            lost[1] = mainLevels - main.experienceLevel;
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(cantor.ringHolds(off) && cantor.ringHolds(main) && off.hasEffect(ModEffects.NULLED.holder())
                    && main.hasEffect(ModEffects.NULLED.holder()), "both players stand Nulled in the ring");
            float offGuard = EnchantmentHelper.getDamageProtection(helper.getLevel(), off, off.damageSources().generic());
            float mainGuard = EnchantmentHelper.getDamageProtection(helper.getLevel(), main, main.damageSources().generic());
            Holder<Enchantment> protection = ench(helper, Enchantments.PROTECTION);
            int offLevel = EnchantmentHelper.getItemEnchantmentLevel(protection, off.getItemBySlot(EquipmentSlot.CHEST));
            int mainLevel = EnchantmentHelper.getItemEnchantmentLevel(protection, main.getItemBySlot(EquipmentSlot.CHEST));
            float sharp = EnchantmentHelper.modifyDamage(helper.getLevel(), off.getMainHandItem(), dummy, off.damageSources().playerAttack(off), 6.0F);

            helper.assertTrue(took[1] == tries && lost[1] == tries, "main hand: all " + tries + " thefts took a level, " + took[1] + " did, " + lost[1] + " lost");
            helper.assertTrue(took[0] >= 72 && took[0] <= 128, "off-hand: about " + tries * (1.0 - Charms.MEMORY_FAIL) + " of " + tries
                    + " thefts took a level (72 to 128), " + took[0] + " did");
            helper.assertTrue(lost[0] == took[0], "off-hand: lost exactly the levels taken, " + lost[0] + " for " + took[0]);
            helper.assertTrue(near(before[0], 16.0, 0.01) && near(before[1], 16.0, 0.01), "outside the ring both sets protect 16, " + before[0] + " and " + before[1]);
            helper.assertTrue(near(mainGuard, 0.0, 0.01) && mainLevel == 0, "main hand: nulled armour protects 0 at Protection 0, " + mainGuard + " at " + mainLevel);
            int half = Charms.nulledLevel(4);
            helper.assertTrue(near(offGuard, 4.0 * half, 0.01) && offLevel == half, "off-hand: nulled armour protects " + 4 * half + " at Protection "
                    + half + ", " + offGuard + " at " + offLevel);
            float sharpKept = 6.0F + 0.5F + 0.5F * Charms.nulledLevel(5);
            helper.assertTrue(near(sharp, sharpKept, 0.01), "off-hand: the Sharpness V sword hits " + sharpKept + " for a 6, hit " + sharp);
            TeemingMod.LOGGER.info("charm_memory: thefts off-hand {} / {} main hand {} / {}; nulled Protection off-hand {} (level {}) main hand {} (level {}); Sharpness hit {}",
                    took[0], tries, took[1], tries, offGuard, offLevel, mainGuard, mainLevel, sharp);
            helper.succeed();
        });
    }

    // ---- Grounding Charm at the remaining call sites ---------------------------------------------------------------

    private static Vec3 rel(GameTestHelper helper, LivingEntity e) {
        return helper.relativeVec(e.position());
    }

    /**
     * An Underclinger's drag. An island of end stone (a back wall x 1..10, z 8..9, y 1..5, and an overhang at y 5,
     * z 5..7, with open air under it), two Underclingers under its north rim at x 3 and x 8, and above each a pig on the
     * rim: one holding a Grounding Charm in the off-hand, one in the main hand. From where it is held to where it is let
     * go, the main-hand pig is dragged the full {@link Underclinger#PULL_DISTANCE} (2) north and the off-hand pig
     * {@link Charms#GROUNDING} of it (1), neither of them across.
     */
    private static void groundingUnderclinger(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int y = 1; y <= 5; y++) for (int z = 8; z <= 9; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.END_STONE);
            for (int z = 5; z <= 7; z++) helper.setBlock(new BlockPos(x, 5, z), Blocks.END_STONE);
        }
        double[] off = dragged(helper, 3, InteractionHand.OFF_HAND), main = dragged(helper, 8, InteractionHand.MAIN_HAND);
        helper.succeedWhen(() -> {
            helper.assertTrue(off[0] >= 0 && main[0] >= 0, "both pigs were held and dragged");
            double full = Underclinger.PULL_DISTANCE, kept = full * Charms.GROUNDING;
            helper.assertTrue(near(main[0], full, 0.01) && main[1] < 0.01, "main hand: dragged " + full + " north, saw " + main[0] + " north, " + main[1] + " across");
            helper.assertTrue(near(off[0], kept, 0.01) && off[1] < 0.01, "off-hand: dragged " + kept + " north, saw " + off[0] + " north, " + off[1] + " across");
            TeemingMod.LOGGER.info("charm_grounding_underclinger: dragged off-hand {} main hand {}", off[0], main[0]);
        });
    }

    /**
     * An Underclinger gripping the underside of the rim block at (x, 5, 6), aimed at a pig on the rim above it that
     * keeps its physics and holds a Grounding Charm in {@code hand}; {north, across}, how far the pig moved from where it
     * was held to where it was let go, or {-1, -1} until then.
     */
    private static double[] dragged(GameTestHelper helper, int x, InteractionHand hand) {
        Underclinger u = helper.spawn(ModMobs.UNDERCLINGER.get(), new Vec3(x + 0.5, 4.4, 6.5));
        u.core().setLook(LookDef.BASE, true);
        u.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        helper.assertTrue(u.hangUnder(helper.absolutePos(new BlockPos(x, 5, 6))), "the Underclinger grips the underside at x " + x);
        u.resetCooldown();
        TestPlayers.whenDone(helper, u::discard);
        Pig pig = movingPig(helper, new Vec3(x + 0.5, 6, 5.5), ModCharms.GROUNDING_CHARM, hand);
        u.setTarget(pig);
        double[] moved = {-1, -1};
        Vec3[] held = {null};
        boolean[] pulling = {false};
        helper.onEachTick(() -> {
            if (held[0] == null && u.core().in(Underclinger.Step.HOLD)) held[0] = rel(helper, pig);
            pulling[0] |= u.core().in(Underclinger.Step.PULL);
            if (pulling[0] && moved[0] < 0 && !u.core().in(Underclinger.Step.PULL)) {
                Vec3 at = rel(helper, pig);
                moved[0] = held[0].z - at.z;
                moved[1] = Math.abs(at.x - held[0].x);
            }
        });
        return moved;
    }

    /** The north-ward steps of one reel, tick by tick, until the hold at the brink begins. */
    private static final class Reel {
        final List<Double> steps = new ArrayList<>();
        boolean held;
        Vec3 last;

        List<Double> moving() {
            return steps.stream().filter(d -> Math.abs(d) > 1.0E-4).toList();
        }

        double total() {
            return steps.stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    /**
     * An Abyss Angler's reel. An island of end stone one block thick (x 1..10, z 3..10, y 5) with its rim to the north,
     * two Abyss Anglers under the rim at x 3 and x 8, and a pig 7 blocks inland from each: one holding a Grounding Charm
     * in the off-hand, one in the main hand. Every tick of the reel but the last short step onto the brink, the
     * main-hand pig moves the full {@link AbyssAngler#PULL_SPEED} (0.5) north and the off-hand pig
     * {@link Charms#GROUNDING} of it (0.25); both end at the same brink, 6.05 blocks on.
     */
    private static void groundingAngler(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) for (int z = 3; z <= 10; z++) helper.setBlock(new BlockPos(x, 5, z), Blocks.END_STONE);
        Reel off = reeled(helper, 3, InteractionHand.OFF_HAND), main = reeled(helper, 8, InteractionHand.MAIN_HAND);
        helper.succeedWhen(() -> {
            helper.assertTrue(off.held && main.held, "both reels ended in the hold at the brink");
            double full = AbyssAngler.PULL_SPEED, kept = full * Charms.GROUNDING;
            List<Double> m = main.moving(), o = off.moving();
            for (int i = 0; i < m.size() - 1; i++) helper.assertTrue(near(m.get(i), full, 0.001), "main hand: reeled " + full + " a tick, steps " + m);
            for (int i = 0; i < o.size() - 1; i++) helper.assertTrue(near(o.get(i), kept, 0.001), "off-hand: reeled " + kept + " a tick, steps " + o);
            helper.assertTrue(m.size() >= 2 && o.size() >= 2 && m.getLast() <= full + 0.001 && o.getLast() <= kept + 0.001, "the last steps are short ones onto the brink, "
                    + m + " and " + o);
            helper.assertTrue(near(main.total(), 6.05, 0.01) && near(off.total(), 6.05, 0.01), "both reeled the 6.05 blocks to the brink, "
                    + main.total() + " and " + off.total());
            TeemingMod.LOGGER.info("charm_grounding_angler: reel steps off-hand {} main hand {}", o, m);
        });
    }

    /**
     * An Abyss Angler gripping the underside of the rim block at (x, 5, 3), aimed at a pig at (x + 0.5, 6, 9.5) that
     * keeps its physics and holds a Grounding Charm in {@code hand}.
     */
    private static Reel reeled(GameTestHelper helper, int x, InteractionHand hand) {
        AbyssAngler a = helper.spawn(ModMobs.ABYSS_ANGLER.get(), new Vec3(x + 0.5, 4, 3.5));
        a.core().setLook(LookDef.BASE, true);
        a.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        helper.assertTrue(a.hangUnder(helper.absolutePos(new BlockPos(x, 5, 3))), "the Abyss Angler grips the underside at x " + x);
        a.resetCooldown();
        TestPlayers.whenDone(helper, a::discard);
        Pig pig = movingPig(helper, new Vec3(x + 0.5, 6, 9.5), ModCharms.GROUNDING_CHARM, hand);
        a.setTarget(pig);
        Reel reel = new Reel();
        helper.onEachTick(() -> {
            if (a.core().in(AbyssAngler.Step.REEL) || (!reel.held && a.core().in(AbyssAngler.Step.HOLD))) {
                Vec3 now = rel(helper, pig);
                if (reel.last != null) reel.steps.add(reel.last.z - now.z);
                reel.last = now;
                if (a.core().in(AbyssAngler.Step.HOLD)) reel.held = true;
            }
        });
        return reel;
    }

    /**
     * A Spirebloom's knock-up. Two Spireblooms each send their line along the floor at a still pig 5 blocks off, one
     * pig holding a Grounding Charm in the off-hand and one in the main hand. The spikes strike with no knockback of
     * their own, so read the tick the strike lands (a still pig keeps the motion it is given): the main-hand pig is
     * thrown straight up at the full {@link Spirebloom#KNOCK_UP} (0.6) and the off-hand pig at {@link Charms#GROUNDING}
     * of it (0.3), neither across.
     */
    private static void groundingSpirebloom(GameTestHelper helper) {
        Vec3[] off = spiked(helper, 2.5, InteractionHand.OFF_HAND), main = spiked(helper, 9.5, InteractionHand.MAIN_HAND);
        helper.succeedWhen(() -> {
            helper.assertTrue(off[0] != null && main[0] != null, "the spikes struck both pigs");
            double full = Spirebloom.KNOCK_UP, kept = full * Charms.GROUNDING;
            helper.assertTrue(near(main[0].y, full, 0.01) && main[0].horizontalDistance() < 0.01, "main hand: thrown straight up at " + full + ", motion " + main[0]);
            helper.assertTrue(near(off[0].y, kept, 0.01) && off[0].horizontalDistance() < 0.01, "off-hand: thrown straight up at " + kept + ", motion " + off[0]);
            TeemingMod.LOGGER.info("charm_grounding_spirebloom: knock-up off-hand {} main hand {}", off[0], main[0]);
        });
    }

    /** A Spirebloom at (1.5, 1, z) cracking the ground toward a still pig at (6.5, 1, z); the pig's motion the tick it is struck. */
    private static Vec3[] spiked(GameTestHelper helper, double z, InteractionHand hand) {
        Spirebloom b = helper.spawn(ModMobs.SPIREBLOOM.get(), new Vec3(1.5, 1, z));
        b.core().setLook(LookDef.BASE, true);
        b.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        TestPlayers.whenDone(helper, b::discard);
        Pig pig = pig(helper, new Vec3(6.5, 1, z), ModCharms.GROUNDING_CHARM, hand);
        helper.runAtTickTime(2, () -> helper.assertTrue(b.crack(pig), "the tell starts at the pig"));
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && pig.getHealth() < 100.0F) thrown[0] = pig.getDeltaMovement();
        });
        return thrown;
    }

    /**
     * A Brinkray's ram at the brink. A bridge of end stone one block wide (x 1..10, z 9, y 3) over the arena's floor,
     * which each ray counts as open air below y 2 (the void under an island), and two Brinkrays, each banking 7 blocks
     * north of a still pig on the bridge, one pig holding a Grounding Charm in the off-hand and one in the main hand.
     * Read the tick the ram lands: the main-hand pig is thrown the full {@link Brinkray#KNOCKBACK} (1.8) on along the
     * ram and {@link Brinkray#KNOCKBACK_UP} (0.3) up, the off-hand pig {@link Charms#GROUNDING} of each (0.9, 0.15).
     */
    private static void groundingBrinkray(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) helper.setBlock(new BlockPos(x, 3, 9), Blocks.END_STONE);
        Vec3[] off = rammed(helper, 3.5, InteractionHand.OFF_HAND), main = rammed(helper, 8.5, InteractionHand.MAIN_HAND);
        helper.succeedWhen(() -> {
            helper.assertTrue(off[0] != null && main[0] != null, "both rams hit");
            double g = Charms.GROUNDING, on = Brinkray.KNOCKBACK, up = Brinkray.KNOCKBACK_UP;
            Vec3 m = main[0], o = off[0];
            helper.assertTrue(near(m.horizontalDistance(), on, 0.01) && m.z > 0.0 && near(m.y, up, 0.01), "main hand: thrown " + on + " on and " + up + " up, motion " + m);
            helper.assertTrue(near(o.horizontalDistance(), on * g, 0.01) && o.z > 0.0 && near(o.y, up * g, 0.01), "off-hand: thrown " + on * g + " on and "
                    + up * g + " up, motion " + o);
            TeemingMod.LOGGER.info("charm_grounding_brinkray: ram throw off-hand {} main hand {}", o, m);
        });
    }

    /**
     * A Brinkray hovering at its banking spot (x, 3.8, 2.5), the arena below y 2 its void, at a still pig on the bridge
     * at (x, 4, 9.5) holding a Grounding Charm in {@code hand}; the pig's motion the tick the ram hits it.
     */
    private static Vec3[] rammed(GameTestHelper helper, double x, InteractionHand hand) {
        Brinkray r = helper.spawn(ModMobs.BRINKRAY.get(), new Vec3(x, 3.8, 2.5));
        r.core().setLook(LookDef.BASE, true);
        r.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(10.0);
        r.fakeVoid(helper.getBounds(), helper.absolutePos(new BlockPos(0, 2, 0)).getY());
        r.glideAround(r.position(), 0.0);
        TestPlayers.whenDone(helper, r::discard);
        Pig pig = pig(helper, new Vec3(x, 4, 9.5), ModCharms.GROUNDING_CHARM, hand);
        r.setTarget(pig);
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && pig.getHealth() < 100.0F) thrown[0] = pig.getDeltaMovement();
        });
        return thrown;
    }

    // ---- Rift Charm at the remaining call sites --------------------------------------------------------------------

    /**
     * The first seed from {@link #RIFT_SEED} on whose fresh random the first {@code nextDouble} rolls under
     * {@link Charms#RIFT_FAIL} ({@code saves}: the charm holds) or not (it lets the teleport through). A mob's random is
     * a plain {@link RandomSource#create()}, so the same seed set on it rolls the same.
     */
    private static long riftSeed(boolean saves) {
        for (long s = RIFT_SEED; ; s++) if ((RandomSource.create(s).nextDouble() < Charms.RIFT_FAIL) == saves) return s;
    }

    /** Seeds {@code mob}'s random with {@code seed} as it posts {@code step}, so the roll that follows is the seed's first. */
    private static void seedBefore(GameTestHelper helper, Mob mob, String step, long seed) {
        Consumer<MechanicEvent> seeding = e -> {
            if (e.mob() == mob && e.step().equals(step)) mob.getRandom().setSeed(seed);
        };
        MechanicEvent.listen(seeding);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(seeding));
    }

    /** One lane of a forced-teleport pair: the mob, its pig and where the pig stood. */
    private record Lane(Mob mob, Pig pig, Vec3 home) {
        double moved() {
            return Math.hypot(pig.getX() - home.x, pig.getZ() - home.z);
        }
    }

    /**
     * A Warpseed Bloom's seed, in three lanes, each a still bloom spitting one seed at a still pig 5 blocks east, the
     * throw fixed at 3 blocks east and the bloom's roll seeded the tick before it rolls: a pig holding a Rift Charm in
     * the off-hand with a roll the charm holds on, one holding it in the main hand with the same roll, and one holding
     * it in the off-hand with a roll it lets through. Every seed hurts its pig. The off-hand pig whose charm held stays
     * where it stood, unthrown, and its charm chimed; the main-hand pig is thrown exactly 3 blocks on the same roll, as
     * is the off-hand pig whose roll the charm lets through (the charm fails {@link Charms#RIFT_FAIL} of throws, not all).
     */
    private static void riftWarpseed(GameTestHelper helper) {
        long holds = riftSeed(true), letsThrough = riftSeed(false);
        Lane saved = seedLane(helper, 2.5, InteractionHand.OFF_HAND, holds);
        Lane main = seedLane(helper, 6.0, InteractionHand.MAIN_HAND, holds);
        Lane thrown = seedLane(helper, 9.5, InteractionHand.OFF_HAND, letsThrough);
        double throwBlocks = WarpseedBloom.WARP_NEAR;
        helper.succeedWhen(() -> {
            for (Lane l : List.of(saved, main, thrown)) helper.assertTrue(l.pig().getHealth() < 100.0F, "every seed struck its pig");
            WarpseedBloom sb = (WarpseedBloom) saved.mob(), mb = (WarpseedBloom) main.mob(), tb = (WarpseedBloom) thrown.mob();
            helper.assertTrue(sb.warps() == 0 && saved.moved() < 0.01, "off-hand, a roll the charm holds on: the pig stays where it stood, thrown "
                    + sb.warps() + ", moved " + saved.moved());
            helper.assertTrue(Charms.lastFelt(saved.pig()) >= 0, "and its charm chimed for the save");
            helper.assertTrue(mb.warps() == 1 && near(main.moved(), throwBlocks, 0.01), "main hand, the same roll: thrown " + throwBlocks
                    + " blocks, thrown " + mb.warps() + ", moved " + main.moved());
            helper.assertTrue(tb.warps() == 1 && near(thrown.moved(), throwBlocks, 0.01) && Charms.lastFelt(thrown.pig()) == -1,
                    "off-hand, a roll the charm lets through: thrown " + throwBlocks + " blocks with no chime, thrown " + tb.warps() + ", moved "
                            + thrown.moved());
            TeemingMod.LOGGER.info("charm_rift_warpseed: seeds {} (holds) and {} (lets through); moved off-hand saved {} main hand {} off-hand let through {}",
                    holds, letsThrough, saved.moved(), main.moved(), thrown.moved());
        });
    }

    /** A still Warpseed Bloom at (1.5, 1, z) that spits one seed, at tick 2, at a still pig at (6.5, 1, z) holding a Rift Charm in {@code hand}. */
    private static Lane seedLane(GameTestHelper helper, double z, InteractionHand hand, long seed) {
        Pig pig = pig(helper, new Vec3(6.5, 1, z), ModCharms.RIFT_CHARM, hand);
        WarpseedBloom bloom = helper.spawn(ModMobs.WARPSEED_BLOOM.get(), new Vec3(1.5, 1, z));
        bloom.core().setLook(LookDef.BASE, true);
        bloom.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        bloom.face(pig.position());
        bloom.setTarget(pig);
        bloom.setNoAi(true);
        bloom.fixWarp(new WarpseedBloom.Warp(WarpseedBloom.WARP_NEAR, 0.0));       // 3 blocks toward +x
        TestPlayers.whenDone(helper, bloom::discard);
        // a seed that misses hurts and throws nothing outside this arena
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == bloom && e.target() != null && (e.step().equals("seed_hit") || e.step().equals("warp"))
                    && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        seedBefore(helper, bloom, "warp", seed);
        helper.runAtTickTime(2, () -> helper.assertTrue(bloom.tell(pig), "the bloom tells at its pig"));
        return new Lane(bloom, pig, pig.position());
    }

    /**
     * An Exile Wraith's mark, in the wide arena (its floor's top is at y 0, open to the sky), in three lanes 14 blocks
     * apart, each a still wraith marking a still pig, the sending fixed at 30 blocks east (onto the arena's floor) and
     * the wraith's roll seeded as it sends: a pig holding a Rift Charm in the off-hand with a roll the charm holds on,
     * one holding it in the main hand with the same roll, and one holding it in the off-hand with a roll it lets
     * through. The off-hand pig whose charm held stays on its circle and its charm chimed; the main-hand pig is sent 30
     * blocks on the same roll, as is the off-hand pig whose roll the charm lets through.
     */
    private static void riftExile(GameTestHelper helper) {
        long holds = riftSeed(true), letsThrough = riftSeed(false);
        Lane saved = markLane(helper, 6.5, InteractionHand.OFF_HAND, holds);
        Lane main = markLane(helper, 20.5, InteractionHand.MAIN_HAND, holds);
        Lane sent = markLane(helper, 34.5, InteractionHand.OFF_HAND, letsThrough);
        helper.succeedWhen(() -> {
            for (Lane l : List.of(saved, main, sent)) {
                ExileWraith w = (ExileWraith) l.mob();
                helper.assertTrue(w.marksCast() == 1 && w.mark() == null && !w.core().in(ExileWraith.Step.TELL), "every mark was cast and filled");
            }
            ExileWraith sw = (ExileWraith) saved.mob(), mw = (ExileWraith) main.mob(), tw = (ExileWraith) sent.mob();
            helper.assertTrue(sw.exiles() == 0 && saved.moved() < 0.01, "off-hand, a roll the charm holds on: the pig stays on its circle, sent "
                    + sw.exiles() + ", moved " + saved.moved());
            helper.assertTrue(Charms.lastFelt(saved.pig()) >= 0, "and its charm chimed for the save");
            helper.assertTrue(mw.exiles() == 1 && near(main.moved(), 30.0, 0.01), "main hand, the same roll: sent 30 blocks, sent " + mw.exiles()
                    + ", moved " + main.moved());
            helper.assertTrue(tw.exiles() == 1 && near(sent.moved(), 30.0, 0.01) && Charms.lastFelt(sent.pig()) == -1,
                    "off-hand, a roll the charm lets through: sent 30 blocks with no chime, sent " + tw.exiles() + ", moved " + sent.moved());
            TeemingMod.LOGGER.info("charm_rift_exile: seeds {} (holds) and {} (lets through); moved off-hand saved {} main hand {} off-hand let through {}",
                    holds, letsThrough, saved.moved(), main.moved(), sent.moved());
        });
    }

    /** A still Exile Wraith at (6.5, 0, z) that marks, at tick 2, a still pig at (10.5, 0, z) holding a Rift Charm in {@code hand}. */
    private static Lane markLane(GameTestHelper helper, double z, InteractionHand hand, long seed) {
        ExileWraith w = helper.spawn(ModMobs.EXILE_WRAITH.get(), new Vec3(6.5, 0, z));
        w.core().setLook(LookDef.BASE, true);
        w.removeFreeWill();
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        w.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        w.setMarkCooldown(100000);                                                   // it marks only when the test says
        w.fixRoll(new ExileWraith.Roll(30.0, 0.0));                                  // 30 blocks toward +x
        TestPlayers.whenDone(helper, w::discard);
        seedBefore(helper, w, "send", seed);
        Pig pig = pig(helper, new Vec3(10.5, 0, z), ModCharms.RIFT_CHARM, hand);
        helper.runAtTickTime(2, () -> helper.assertTrue(w.mark(pig), "the wraith marks its pig"));
        return new Lane(w, pig, pig.position());
    }

    private static Holder<Enchantment> ench(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(ench(helper, key), level);
        return stack;
    }
}
