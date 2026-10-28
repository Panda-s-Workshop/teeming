package com.panda.teeming.gametest;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.charm.Charms;
import com.panda.teeming.item.CharmItem;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.overworld.Hushwing;
import com.panda.teeming.mob.overworld.MyceliteShambler;
import com.panda.teeming.mob.overworld.Rattlecoil;
import com.panda.teeming.mob.overworld.Shiverwraith;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.registry.ModCharms;
import com.panda.teeming.registry.ModMobs;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Overworld charms after the Shellward Charm: Hearth, Hush and Antivenom. Each test runs the real mechanic twice
 * side by side, on a target holding the charm in the off-hand and on one holding the same charm in the main hand, and
 * asserts the off-hand changes the mechanic's number by the share in {@link Charms} while the main hand changes nothing.
 * Still pigs stand in where a mechanic takes any living target (a charm counts on any holder), and real survival
 * players where the mob reacts to players only.
 */
final class OverworldCharmTests {
    private OverworldCharmTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("charm_hearth", OverworldCharmTests::hearth);
        TeemingGameTests.timeLimit("charm_hearth", 140);
        out.accept("charm_tether_frost", OverworldCharmTests::tetherFrost);
        out.accept("charm_hush", OverworldCharmTests::hush);
        out.accept("charm_antivenom", OverworldCharmTests::antivenom);
    }

    private static Pig pig(GameTestHelper helper, Vec3 at, DeferredItem<CharmItem> charm, InteractionHand hand) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.setItemInHand(hand, charm.get().getDefaultInstance());
        return pig;
    }

    private static ServerPlayer survivor(GameTestHelper helper, Vec3 at, DeferredItem<CharmItem> charm, InteractionHand hand) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemInHand(hand, charm.get().getDefaultInstance());
        return p;
    }

    // ---- Hearth Charm ----------------------------------------------------------------------------------------------

    /** One Shiverwraith tethered to one pig: the pig's freeze ticks 5 and 25 ticks into the tether, the wraith's health at 65. */
    private static final class Tether {
        final Shiverwraith wraith;
        final Pig pig;
        long locked = -1;
        /** Ticks the tether held before it let go, or -1 while it holds. */
        long held = -1;
        int at5 = -1, at25 = -1;
        float at65 = -1.0F;

        Tether(GameTestHelper helper, double z, DeferredItem<CharmItem> charm, InteractionHand hand) {
            wraith = helper.spawn(ModMobs.SHIVERWRAITH.get(), new Vec3(1.5, 1, z));
            wraith.removeFreeWill();        // no wandering or clawing; its own phase machine still runs
            wraith.resetCooldown();
            // the base look now, so its first tick doesn't pick one and refill its health over the setHealth below
            wraith.core().setLook(LookDef.BASE, true);
            wraith.setHealth(10.0F);
            pig = pig(helper, new Vec3(8.5, 1, z), charm, hand);
            wraith.setTarget(pig);
        }

        void tick(long now) {
            if (locked < 0 && wraith.core().in(Shiverwraith.Step.TETHER)) locked = now;
            if (locked < 0) return;
            if (held < 0 && !wraith.core().in(Shiverwraith.Step.TETHER)) held = now - locked;
            if (now == locked + 5) at5 = pig.getTicksFrozen();
            if (now == locked + 25) at25 = pig.getTicksFrozen();
            if (at65 < 0 && wraith.core().in(Shiverwraith.Step.TETHER) && wraith.core().elapsed() >= 65) at65 = wraith.getHealth();
        }

        int gain() {
            return at25 - at5;
        }
    }

    /**
     * Two Shiverwraiths, each 7 blocks from a still pig, one pig with a Hearth Charm in the off-hand and one with it in
     * the main hand. Over 20 ticks of the tether the main-hand pig gains the full 3 freeze ticks a tick (60) and the
     * off-hand pig {@link Charms#HEARTH_FREEZE} of it (30, paid alternately 1 and 2 a tick); a tick either way for when
     * a reading lands is 3 freeze ticks, so the tolerance is 3. Hurt to 10 of 20, the main-hand pig's wraith has healed
     * the full 1 a second by the tether's 65th tick (13) and the off-hand pig's {@link Charms#HEARTH_TETHER_HEAL} of it
     * (11.5); healing comes in whole seconds, so those are exact.
     */
    private static void hearth(GameTestHelper helper) {
        Tether off = new Tether(helper, 2.5, ModCharms.HEARTH_CHARM, InteractionHand.OFF_HAND);
        Tether main = new Tether(helper, 9.5, ModCharms.HEARTH_CHARM, InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            off.tick(helper.getTick());
            main.tick(helper.getTick());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(off.at65 >= 0 && main.at65 >= 0, "both tethers held 65 ticks, phases " + off.wraith.phase() + " and " + main.wraith.phase());
            int full = Shiverwraith.FREEZE_PER_TICK * 20;
            helper.assertTrue(Math.abs(main.gain() - full) <= 3, "the charm in the main hand leaves the full freeze, " + full
                    + " over 20 ticks, got " + main.gain() + " (" + main.at5 + " -> " + main.at25 + ")");
            double kept = full * Charms.HEARTH_FREEZE;
            helper.assertTrue(Math.abs(off.gain() - kept) <= 3, "the charm in the off-hand keeps " + kept + " of the freeze over 20 ticks, got "
                    + off.gain() + " (" + off.at5 + " -> " + off.at25 + ")");
            float healed = 3 * Shiverwraith.HEAL;
            helper.assertTrue(Math.abs(main.at65 - 10.0F - healed) < 0.01F, "the charm in the main hand leaves the full healing, "
                    + healed + " in 65 ticks, got " + (main.at65 - 10.0F));
            double kept65 = healed * Charms.HEARTH_TETHER_HEAL;
            helper.assertTrue(Math.abs(off.at65 - 10.0F - kept65) < 0.01F, "the charm in the off-hand keeps " + kept65
                    + " of the healing in 65 ticks, got " + (off.at65 - 10.0F));
            TeemingMod.LOGGER.info("charm_hearth: freeze over 20 ticks off-hand {} main hand {}; healed in 65 ticks off-hand {} main hand {}",
                    off.gain(), main.gain(), off.at65 - 10.0F, main.at65 - 10.0F);
        });
    }

    /**
     * The Tether Charm on a Shiverwraith's frost tether: the off-hand pig's tether snaps after
     * {@link Charms#TETHER_SNAP_TICKS}, as a lost hold would; the main-hand pig's still holds 20 ticks later. The snap is
     * checked on the tick the tether reaches the limit, and the reading lands within a tick of it.
     */
    private static void tetherFrost(GameTestHelper helper) {
        Tether off = new Tether(helper, 2.5, ModCharms.TETHER_CHARM, InteractionHand.OFF_HAND);
        Tether main = new Tether(helper, 9.5, ModCharms.TETHER_CHARM, InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            off.tick(helper.getTick());
            main.tick(helper.getTick());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(off.held >= 0, "the off-hand tether let go, phase " + off.wraith.phase());
            helper.assertTrue(Math.abs(off.held - Charms.TETHER_SNAP_TICKS) <= 1, "the off-hand tether snaps at " + Charms.TETHER_SNAP_TICKS + " ticks, held " + off.held);
            helper.assertTrue(main.locked >= 0 && helper.getTick() >= main.locked + Charms.TETHER_SNAP_TICKS + 20 && main.held < 0,
                    "the main-hand tether still holds " + (Charms.TETHER_SNAP_TICKS + 20) + " ticks in, held " + main.held);
        });
    }

    // ---- Hush Charm ------------------------------------------------------------------------------------------------

    /** A Hushwing hovering at {@code at} that stays put until it hears something, and hears only {@code allowed}. */
    private static Hushwing bat(GameTestHelper helper, Vec3 at, Set<Entity> allowed) {
        Hushwing bat = helper.spawn(ModMobs.HUSHWING.get(), at);
        bat.removeFreeWill();         // no flitting about; its hearing and chase still run
        bat.hearOnly(allowed::contains);
        TestPlayers.whenDone(helper, bat::discard);
        return bat;
    }

    private static boolean heard(Hushwing bat) {
        return bat.noiseAt() != null && !bat.core().idle();
    }

    /** Clears the barrier roof over the arena (the two layers above its 8 rows), so Hushwings can hover past 10 blocks up. */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
    }

    /**
     * Two survival players each break a stone at their feet, one with a Hush Charm in the off-hand and one with it in
     * the main hand, each heard only by the Hushwings over their own head. The main-hand miner keeps the full mining
     * range: heard 9.5 blocks up and not 10.5. The off-hand miner's range is {@link Charms#HUSH_MINING} of it, none:
     * not heard 1.5 or 9.5 blocks up. Then the off-hand player sprints, and both of its Hushwings hear that at once:
     * the charm leaves sprinting to be heard.
     */
    private static void hush(GameTestHelper helper) {
        openRoof(helper);
        Vec3 offAt = new Vec3(2.5, 1, 5.5), mainAt = new Vec3(9.5, 1, 5.5);
        Set<Entity> offEars = new HashSet<>(), mainEars = new HashSet<>();
        ServerPlayer off = survivor(helper, offAt, ModCharms.HUSH_CHARM, InteractionHand.OFF_HAND);
        ServerPlayer main = survivor(helper, mainAt, ModCharms.HUSH_CHARM, InteractionHand.MAIN_HAND);
        offEars.add(off);
        mainEars.add(main);
        double reach = Hushwing.MINING_RANGE;
        Hushwing offNear = bat(helper, offAt.add(0, 1.5, 0), offEars);
        Hushwing offFar = bat(helper, offAt.add(0, reach - 0.5, 0), offEars);
        Hushwing mainNear = bat(helper, mainAt.add(0, reach - 0.5, 0), mainEars);
        Hushwing mainFar = bat(helper, mainAt.add(0, reach + 0.5, 0), mainEars);
        BlockPos offStone = new BlockPos(2, 1, 6), mainStone = new BlockPos(9, 1, 6);
        helper.setBlock(offStone, Blocks.STONE);
        helper.setBlock(mainStone, Blocks.STONE);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(mainNear.distanceTo(main) - (reach - 0.5)) < 0.05 && Math.abs(mainFar.distanceTo(main) - (reach + 0.5)) < 0.05
                    && Math.abs(offNear.distanceTo(off) - 1.5) < 0.05 && Math.abs(offFar.distanceTo(off) - (reach - 0.5)) < 0.05,
                    "the Hushwings hover where they were put, " + mainNear.distanceTo(main) + ", " + mainFar.distanceTo(main) + ", "
                            + offNear.distanceTo(off) + ", " + offFar.distanceTo(off));
            helper.assertTrue(helper.getLevel().destroyBlock(helper.absolutePos(offStone), false, off), "the off-hand player broke its stone");
            helper.assertTrue(helper.getLevel().destroyBlock(helper.absolutePos(mainStone), false, main), "the main-hand player broke its stone");
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(heard(mainNear), "the charm in the main hand: mining is heard " + (reach - 0.5) + " blocks away, phase " + mainNear.phase());
            helper.assertTrue(!heard(mainFar), "the charm in the main hand: mining is not heard " + (reach + 0.5) + " blocks away, phase " + mainFar.phase());
            double range = reach * Charms.HUSH_MINING;
            helper.assertTrue(!heard(offNear) && !heard(offFar), "the charm in the off-hand: mining is heard " + range
                    + " blocks away, so not 1.5 or " + (reach - 0.5) + " away, phases " + offNear.phase() + " and " + offFar.phase());
            off.setSprinting(true);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(heard(offNear) && heard(offFar), "the off-hand player sprinting is still heard, phases "
                    + offNear.phase() + " and " + offFar.phase());
            helper.succeed();
        });
    }

    // ---- Antivenom Charm -------------------------------------------------------------------------------------------

    /** The Poison on {@code target} the first tick it has any, as {duration, amplifier}; {-1, -1} until then. */
    private static int[] firstPoison(GameTestHelper helper, LivingEntity target) {
        int[] seen = {-1, -1};
        helper.onEachTick(() -> {
            MobEffectInstance poison = target.getEffect(MobEffects.POISON);
            if (seen[0] < 0 && poison != null) {
                seen[0] = poison.getDuration();
                seen[1] = poison.getAmplifier();
            }
        });
        return seen;
    }

    /** A Rattlecoil 3 blocks from a survival player holding an Antivenom Charm in {@code hand}: close enough to draw the strike. */
    private static int[] bitten(GameTestHelper helper, double z, InteractionHand hand) {
        Rattlecoil snake = helper.spawn(ModMobs.RATTLECOIL.get(), new Vec3(1.5, 1, z));
        snake.removeFreeWill();
        ServerPlayer p = survivor(helper, new Vec3(4.5, 1, z), ModCharms.ANTIVENOM_CHARM, hand);
        return firstPoison(helper, p);
    }

    /**
     * Poison from both Teeming mobs that give it. Two Rattlecoils each strike a survival player (their warning reacts
     * to players only), one player with an Antivenom Charm in the off-hand and one with it in the main hand; a Mycelite
     * Shambler's spore puff reaches two still pigs held the same two ways. Each Poison is read the tick it lands: the
     * main hand keeps the full length (Rattlecoil 100 ticks, puff 40) and the off-hand {@link Charms#ANTIVENOM_POISON} of
     * it (50, 20), at the same strength. A player's effects may tick once between the bite and the reading, so the
     * bites allow 2 ticks; the puff is read straight after it, so its lengths are exact.
     */
    private static void antivenom(GameTestHelper helper) {
        // the snakes stand 7.6 blocks from each other's player, past the 6-block warning
        int[] offBite = bitten(helper, 2.5, InteractionHand.OFF_HAND);
        int[] mainBite = bitten(helper, 9.5, InteractionHand.MAIN_HAND);
        MyceliteShambler shambler = helper.spawn(ModMobs.MYCELITE_SHAMBLER.get(), new Vec3(9.5, 1, 5.5));
        shambler.removeFreeWill();
        Pig offPig = pig(helper, new Vec3(8.5, 1, 5.5), ModCharms.ANTIVENOM_CHARM, InteractionHand.OFF_HAND);
        Pig mainPig = pig(helper, new Vec3(10.5, 1, 5.5), ModCharms.ANTIVENOM_CHARM, InteractionHand.MAIN_HAND);
        int puffKept = (int) Math.round(MyceliteShambler.POISON_TICKS * Charms.ANTIVENOM_POISON);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(shambler.puff(helper.getLevel()) == 2, "the puff poisons both pigs");
            MobEffectInstance offPoison = offPig.getEffect(MobEffects.POISON), mainPoison = mainPig.getEffect(MobEffects.POISON);
            helper.assertTrue(mainPoison != null && mainPoison.getDuration() == MyceliteShambler.POISON_TICKS,
                    "the charm in the main hand leaves the puff's full " + MyceliteShambler.POISON_TICKS + " ticks, got " + mainPoison);
            helper.assertTrue(offPoison != null && offPoison.getDuration() == puffKept,
                    "the charm in the off-hand keeps " + puffKept + " ticks of the puff's Poison, got " + offPoison);
            helper.assertTrue(offPoison.getAmplifier() == MyceliteShambler.POISON_LEVEL, "at the same strength, amplifier " + offPoison.getAmplifier());
            TeemingMod.LOGGER.info("charm_antivenom: puff Poison off-hand {} main hand {} ticks", offPoison.getDuration(), mainPoison.getDuration());
        });
        int biteKept = (int) Math.round(Rattlecoil.POISON_TICKS * Charms.ANTIVENOM_POISON);
        helper.succeedWhen(() -> {
            helper.assertTrue(offBite[0] >= 0 && mainBite[0] >= 0, "both snakes struck and poisoned, read " + offBite[0] + " and " + mainBite[0]);
            helper.assertTrue(mainBite[0] <= Rattlecoil.POISON_TICKS && mainBite[0] >= Rattlecoil.POISON_TICKS - 2,
                    "the charm in the main hand leaves the bite's full " + Rattlecoil.POISON_TICKS + " ticks, got " + mainBite[0]);
            helper.assertTrue(offBite[0] <= biteKept && offBite[0] >= biteKept - 2,
                    "the charm in the off-hand keeps " + biteKept + " ticks of the bite's Poison, got " + offBite[0]);
            helper.assertTrue(offBite[1] == Rattlecoil.POISON_LEVEL && mainBite[1] == Rattlecoil.POISON_LEVEL,
                    "both at the same strength, amplifiers " + offBite[1] + " and " + mainBite[1]);
            TeemingMod.LOGGER.info("charm_antivenom: bite Poison off-hand {} main hand {} ticks", offBite[0], mainBite[0]);
        });
    }
}
