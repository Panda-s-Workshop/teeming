package com.panda.teeming.gametest;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.charm.Charms;
import com.panda.teeming.item.CharmItem;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.AnchorGolem;
import com.panda.teeming.mob.end.Brinkram;
import com.panda.teeming.mob.end.ChorusHusk;
import com.panda.teeming.mob.end.Gatecaster;
import com.panda.teeming.mob.end.Levimoth;
import com.panda.teeming.mob.end.LiftstoneMonolith;
import com.panda.teeming.mob.end.Mnemovore;
import com.panda.teeming.mob.end.Nullcantor;
import com.panda.teeming.mob.end.Pullstar;
import com.panda.teeming.mob.end.Riftjackal;
import com.panda.teeming.mob.end.RiftmineSapper;
import com.panda.teeming.mob.end.Rotfang;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.registry.ModCharms;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
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

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("charm_grounding", EndCharmTests::grounding);
        TeemingGameTests.wide("charm_grounding");
        TeemingGameTests.timeLimit("charm_grounding", 140);
        out.accept("charm_rift", EndCharmTests::rift);
        TeemingGameTests.wide("charm_rift");
        TeemingGameTests.timeLimit("charm_rift", 120);
        out.accept("charm_rot_ward", EndCharmTests::rotWard);
        out.accept("charm_memory", EndCharmTests::memory);
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
     * main-hand pigs are thrown at once; the off-hand pigs that a rift let through on the first tick are all still
     * standing in it 25 ticks later (a roll every tick would throw nearly all of them).</li>
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
            helper.assertTrue(stayed[1] == stayed[0], "the off-hand pigs a rift let through stayed in it: " + stayed[0] + " after its first tick, "
                    + stayed[1] + " 25 ticks on");
            int offTell = tell[0], mainTell = tell[1];
            // both tells are counted by the same callback, so a tick either way for its order is the same tick on both
            helper.assertTrue(Math.abs(mainTell - Gatecaster.TELL_TICKS) <= 1, "main hand: the exit rift shows " + Gatecaster.TELL_TICKS
                    + " ticks, showed " + mainTell);
            helper.assertTrue(offTell - mainTell == Charms.RIFT_EARLY, "off-hand: the exit rift shows " + Charms.RIFT_EARLY
                    + " ticks earlier, showed " + offTell + " against " + mainTell);
            helper.assertTrue(lockedAt13[0] && lockedAt13[1], "both aims locked " + Gatecaster.TRACK_TICKS + " ticks in");
            TeemingMod.LOGGER.info("charm_rift: burst teleports landed off-hand {} / {} main hand {} / {}; rifts let {} of 8 through, {} still there 25 ticks on; exit rift {} / {} ticks",
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

    private static Holder<Enchantment> ench(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(ench(helper, key), level);
        return stack;
    }
}
