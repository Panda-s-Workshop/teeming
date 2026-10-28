package com.panda.teeming.gametest;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.item.CharmItem;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.nether.Aurovore;
import com.panda.teeming.mob.nether.KeeningShade;
import com.panda.teeming.mob.nether.Siphonjelly;
import com.panda.teeming.mob.nether.SlagheartGolem;
import com.panda.teeming.mob.nether.Slagspitter;
import com.panda.teeming.mob.nether.Veinleech;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.registry.ModCharms;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModMobs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Nether's counter charms. Each test runs the real mechanic twice side by side, the charm in the off-hand and the
 * same charm in the main hand, and asserts the number moves by what {@link Charms} says from the off-hand and not at all
 * from the main hand. Still pigs stand in as targets where the mechanic acts on any living thing; survival players
 * where it acts only on players or reads a player's inventory.
 */
final class NetherCharmTests {
    private NetherCharmTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("charm_quench", NetherCharmTests::quench);
        TeemingGameTests.timeLimit("charm_quench", 220);
        out.accept("charm_tether", NetherCharmTests::tether);
        TeemingGameTests.timeLimit("charm_tether", 180);
        out.accept("charm_gilded_ward", NetherCharmTests::gildedWard);
        out.accept("charm_clear_voice", NetherCharmTests::clearVoice);
        TeemingGameTests.timeLimit("charm_clear_voice", 160);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    private static ItemStack charm(DeferredItem<CharmItem> charm) {
        return new ItemStack(charm.get());
    }

    /** A pig that stands where it is put, with 40 health, holding {@code charm} in {@code hand}. */
    private static Pig pig(GameTestHelper helper, Vec3 at, DeferredItem<CharmItem> charm, InteractionHand hand) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        pig.setItemInHand(hand, charm(charm));
        return pig;
    }

    /** A survival player holding {@code charm} in {@code hand}. */
    private static ServerPlayer survivor(GameTestHelper helper, Vec3 at, DeferredItem<CharmItem> charm, InteractionHand hand) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemInHand(hand, charm(charm));
        return p;
    }

    // ---- Quench Charm ------------------------------------------------------------------------------------------------

    /**
     * Both halves of the Quench Charm. Heat: two still Slagheart Golems at heat 6 each take one blow from a survival
     * player; the main-hand holder's blow heats its golem by 1 as any blow does, the off-hand holder's cools it by
     * {@link Charms#QUENCH_COOL}. Burn: two still Slagspitters each open a burning patch under a still pig, one holding
     * the charm in the off-hand and one in the main hand; the pigs stand in their patches for all 80 ticks and burn on
     * for the 60 after. Both pigs burn on the same ticks (each spitter ticks just before its pig, and the hurt cooldown
     * paces both alike), so the off-hand pig loses exactly {@link Charms#QUENCH_PATCH_BURN} of what the main-hand pig
     * loses.
     */
    private static void quench(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SlagheartGolem offGolem = golem(helper, new Vec3(2.5, 1, 2.5));
        SlagheartGolem mainGolem = golem(helper, new Vec3(2.5, 1, 9.5));
        ServerPlayer offHitter = survivor(helper, new Vec3(5.5, 1, 2.5), ModCharms.QUENCH_CHARM, InteractionHand.OFF_HAND);
        ServerPlayer mainHitter = survivor(helper, new Vec3(5.5, 1, 9.5), ModCharms.QUENCH_CHARM, InteractionHand.MAIN_HAND);

        Slagspitter offSpitter = spitter(helper, new Vec3(11.5, 1, 2.5));
        Pig offPig = pig(helper, new Vec3(9.5, 1, 3.5), ModCharms.QUENCH_CHARM, InteractionHand.OFF_HAND);
        Slagspitter mainSpitter = spitter(helper, new Vec3(11.5, 1, 9.5));
        Pig mainPig = pig(helper, new Vec3(9.5, 1, 8.5), ModCharms.QUENCH_CHARM, InteractionHand.MAIN_HAND);

        int[] heat = {-1, -1};
        boolean[] opened = {false};
        int[] burnt = {0};
        helper.runAtTickTime(2, () -> {
            boolean a = offGolem.hurtServer(level, level.damageSources().playerAttack(offHitter), 1.0F);
            boolean b = mainGolem.hurtServer(level, level.damageSources().playerAttack(mainHitter), 1.0F);
            helper.assertTrue(a && b, "both blows landed");
            heat[0] = offGolem.heat();
            heat[1] = mainGolem.heat();
            opened[0] = offSpitter.openPatch(offPig.position()) && mainSpitter.openPatch(mainPig.position());
        });
        helper.onEachTick(() -> {
            if (offPig.isOnFire() || mainPig.isOnFire()) burnt[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(heat[1] == 6 + 1, "the charm in the main hand leaves the blow heating the golem, 6 -> " + heat[1]);
            helper.assertTrue(heat[0] == 6 - Charms.QUENCH_COOL, "the charm in the off-hand cools the golem by " + Charms.QUENCH_COOL
                    + ", 6 -> " + heat[0]);
            helper.assertTrue(opened[0], "both patches opened");
            helper.assertTrue(burnt[0] > Slagspitter.PATCH_TICKS && !offPig.isOnFire() && !mainPig.isOnFire()
                    && offSpitter.patches().isEmpty() && mainSpitter.patches().isEmpty(),
                    "the patches closed and the pigs burnt out, burning " + burnt[0] + " ticks so far");
            float full = 40.0F - mainPig.getHealth(), kept = 40.0F - offPig.getHealth();
            helper.assertTrue(full >= 6.0F, "the main-hand pig burns for the whole patch and its after-burn, lost " + full);
            helper.assertTrue(Math.abs(kept - full * Charms.QUENCH_PATCH_BURN) < 0.01F, "the off-hand pig loses "
                    + Charms.QUENCH_PATCH_BURN + " of the main-hand pig's " + full + ", lost " + kept);
        });
    }

    /** A still Slagheart Golem at heat 6: no AI, so nothing but the test's blow changes its heat. */
    private static SlagheartGolem golem(GameTestHelper helper, Vec3 at) {
        SlagheartGolem golem = helper.spawn(ModMobs.SLAGHEART_GOLEM.get(), at);
        golem.setNoAi(true);
        golem.setHeat(6);
        return golem;
    }

    /** A still Slagspitter: no AI, so it spits nothing; its patches still burn in its own tick. */
    private static Slagspitter spitter(GameTestHelper helper, Vec3 at) {
        Slagspitter s = helper.spawn(ModMobs.SLAGSPITTER.get(), at);
        s.setNoAi(true);
        return s;
    }

    // ---- Tether Charm ------------------------------------------------------------------------------------------------

    /**
     * Two Veinleeches latch onto two still pigs 7 blocks off, in sight and in range, one pig holding the charm in the
     * off-hand and one in the main hand. The main-hand pig's vein holds for 100 ticks and more; the off-hand pig's snaps
     * after {@link Charms#TETHER_SNAP_TICKS} as the counter would (the leech lies stunned), having drunk once, at tick 20.
     */
    private static void tether(GameTestHelper helper) {
        Veinleech offLeech = leech(helper, new Vec3(1.5, 1, 1.5));
        Pig offPig = pig(helper, new Vec3(8.5, 1, 1.5), ModCharms.TETHER_CHARM, InteractionHand.OFF_HAND);
        Veinleech mainLeech = leech(helper, new Vec3(1.5, 1, 10.5));
        Pig mainPig = pig(helper, new Vec3(8.5, 1, 10.5), ModCharms.TETHER_CHARM, InteractionHand.MAIN_HAND);
        offLeech.setTarget(offPig);
        mainLeech.setTarget(mainPig);
        int[] held = {0, 0};
        boolean[] stunned = {false, false};
        helper.onEachTick(() -> {
            if (offLeech.core().in(Veinleech.Step.LATCH)) held[0]++;
            if (mainLeech.core().in(Veinleech.Step.LATCH)) held[1]++;
            stunned[0] |= offLeech.core().in(Veinleech.Step.STUNNED);
            stunned[1] |= mainLeech.core().in(Veinleech.Step.STUNNED);
        });
        int drinks = (Charms.TETHER_SNAP_TICKS - 1) / Veinleech.DRAIN_INTERVAL;
        helper.succeedWhen(() -> {
            helper.assertTrue(held[1] >= 100, "the main-hand pig's vein holds, " + held[1] + " ticks so far");
            helper.assertTrue(!stunned[1] && mainLeech.core().in(Veinleech.Step.LATCH), "the charm in the main hand snaps nothing");
            helper.assertTrue(stunned[0], "the off-hand pig's vein snapped and its leech lay stunned, phase " + offLeech.phase());
            helper.assertTrue(Math.abs(held[0] - Charms.TETHER_SNAP_TICKS) <= 1, "it snapped after " + Charms.TETHER_SNAP_TICKS
                    + " ticks, held " + held[0]);
            helper.assertTrue(Math.abs(40.0F - offPig.getHealth() - drinks * Veinleech.DRAIN) < 0.01F, "the off-hand pig was drunk from "
                    + drinks + " time(s), lost " + (40.0F - offPig.getHealth()));
        });
    }

    /** A Veinleech with no free will, blind to other arenas; its own phase machine still runs. */
    private static Veinleech leech(GameTestHelper helper, Vec3 at) {
        Veinleech v = helper.spawn(ModMobs.VEINLEECH.get(), at);
        v.removeFreeWill();
        v.resetCooldown();
        v.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        v.core().setLook(LookDef.BASE, true);
        return v;
    }

    // ---- Gilded Ward Charm -------------------------------------------------------------------------------------------

    /** Thefts tried on each player by each thief, at {@link #ROLLS_A_TICK} a tick. */
    private static final int ROLLS = 400;
    private static final int ROLLS_A_TICK = 25;
    /** The loot's slot: not the selected one, which the Aurovore leaves alone (and where the main-hand charm sits). */
    private static final int LOOT_SLOT = 5;

    /**
     * The two thieves that steal from a player: an Aurovore's theft of one gold nugget and a Siphonjelly's siphon of an
     * endless Speed, each tried {@link #ROLLS} times on a survival player holding the charm in the off-hand and on one
     * holding it in the main hand. Each theft is a fresh thief (its own random source), spawned beside the player, run
     * through its own steal or siphon, and removed. From the main hand not one theft fails; from the off-hand the share
     * that fails is {@link Charms#GILDED_WARD_FAIL} within 0.1: over 400 rolls one standard deviation of the share is
     * 0.025, so 0.1 is four of them, and a sound charm fails this less than once in ten thousand runs, while a charm that
     * did nothing (0) or always worked (1) can't pass.
     */
    private static void gildedWard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer off = survivor(helper, new Vec3(3.5, 1, 3.5), ModCharms.GILDED_WARD_CHARM, InteractionHand.OFF_HAND);
        ServerPlayer main = survivor(helper, new Vec3(8.5, 1, 8.5), ModCharms.GILDED_WARD_CHARM, InteractionHand.MAIN_HAND);
        for (ServerPlayer p : new ServerPlayer[] {off, main}) {
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 0));
        }
        // tried and failed, per player: [aurovore tried, aurovore failed, jelly tried, jelly failed]
        int[][] count = new int[2][4];
        helper.onEachTick(() -> {
            for (int side = 0; side < 2; side++) {
                ServerPlayer p = side == 0 ? off : main;
                int[] c = count[side];
                for (int i = 0; i < ROLLS_A_TICK && c[0] < ROLLS; i++) {
                    p.getInventory().setItem(LOOT_SLOT, new ItemStack(Items.GOLD_NUGGET, 64));
                    Aurovore a = helper.spawn(ModMobs.AUROVORE.get(), helper.relativeVec(p.position()).add(1.0, 0, 0));
                    boolean took = a.steal(level, p);
                    if (took != (p.getInventory().getItem(LOOT_SLOT).getCount() == 63)) helper.fail("a theft that says " + took + " left "
                            + p.getInventory().getItem(LOOT_SLOT));
                    c[0]++;
                    if (!took) c[1]++;
                    a.takeHoard();
                    a.discard();

                    Siphonjelly j = helper.spawn(ModMobs.SIPHONJELLY.get(), helper.relativeVec(p.position()).add(-1.0, 1.0, 0));
                    c[2]++;
                    if (j.siphon(level, p) == null) c[3]++;
                    j.discard();
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(count[0][0] == ROLLS && count[1][0] == ROLLS, "every theft tried, " + count[0][0] + " and " + count[1][0]);
            helper.assertTrue(count[1][1] == 0 && count[1][3] == 0, "the charm in the main hand turns no theft away: the Aurovore failed "
                    + count[1][1] + " and the Siphonjelly " + count[1][3] + " of " + ROLLS);
            double aurovore = count[0][1] / (double) ROLLS, jelly = count[0][3] / (double) ROLLS;
            helper.assertTrue(Math.abs(aurovore - Charms.GILDED_WARD_FAIL) <= 0.1, "the Aurovore fails " + Charms.GILDED_WARD_FAIL
                    + " of its thefts from the off-hand holder, failed " + aurovore);
            helper.assertTrue(Math.abs(jelly - Charms.GILDED_WARD_FAIL) <= 0.1, "the Siphonjelly fails " + Charms.GILDED_WARD_FAIL
                    + " of its siphons from the off-hand holder, failed " + jelly);
            helper.assertTrue(off.hasEffect(MobEffects.SPEED) && main.hasEffect(MobEffects.SPEED), "an endless effect stays with its owner");
        });
    }

    // ---- Clear Voice Charm -------------------------------------------------------------------------------------------

    /**
     * A Keening Shade in the middle of the arena wails at two survival players 4.5 blocks off on either side, both in its
     * sight, one holding the charm in the off-hand and one in the main hand. The main-hand holder is Silenced for the
     * full 80 ticks, the off-hand holder for {@link Charms#CLEAR_VOICE_SILENCED_TICKS}, each counted tick by tick in the
     * player's own tick (which only the test runs here).
     */
    private static void clearVoice(GameTestHelper helper) {
        KeeningShade shade = helper.spawn(ModMobs.KEENING_SHADE.get(), new Vec3(6.0, 1, 6.0));
        shade.removeFreeWill();
        shade.resetCooldown();
        shade.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        shade.core().setLook(LookDef.BASE, true);
        ServerPlayer off = survivor(helper, new Vec3(10.5, 1, 6.0), ModCharms.CLEAR_VOICE_CHARM, InteractionHand.OFF_HAND);
        ServerPlayer main = survivor(helper, new Vec3(1.5, 1, 6.0), ModCharms.CLEAR_VOICE_CHARM, InteractionHand.MAIN_HAND);
        shade.setTarget(main);
        ServerPlayer[] players = {off, main};
        int[] given = {-1, -1}, lasted = {0, 0};
        boolean[] over = {false, false};
        helper.onEachTick(() -> {
            for (int i = 0; i < 2; i++) {
                // read before the player's own tick runs the effect down, so the tick it lands counts in full
                MobEffectInstance e = players[i].getEffect(ModEffects.SILENCED.holder());
                if (e != null) {
                    if (lasted[i] == 0) given[i] = e.getDuration();
                    lasted[i]++;
                } else if (lasted[i] > 0) {
                    over[i] = true;
                }
                players[i].doTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0] && over[1], "both were Silenced and it wore off, " + lasted[0] + " and " + lasted[1] + " ticks so far");
            helper.assertTrue(given[1] == KeeningShade.SILENCE_TICKS && Math.abs(lasted[1] - KeeningShade.SILENCE_TICKS) <= 1,
                    "the charm in the main hand leaves the full " + KeeningShade.SILENCE_TICKS + " ticks, given " + given[1] + ", lasted " + lasted[1]);
            helper.assertTrue(given[0] == Charms.CLEAR_VOICE_SILENCED_TICKS && Math.abs(lasted[0] - Charms.CLEAR_VOICE_SILENCED_TICKS) <= 1,
                    "the charm in the off-hand cuts it to " + Charms.CLEAR_VOICE_SILENCED_TICKS + " ticks, given " + given[0] + ", lasted " + lasted[0]);
        });
    }
}
