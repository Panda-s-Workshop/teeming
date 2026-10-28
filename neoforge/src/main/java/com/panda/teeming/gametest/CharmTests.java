package com.panda.teeming.gametest;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.charm.Charms;
import com.panda.teeming.item.CharmItem;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.nether.WoolcapGrazer;
import com.panda.teeming.mob.overworld.Snowroller;
import com.panda.teeming.mob.overworld.Thornhorn;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.registry.ModCharms;
import com.panda.teeming.registry.ModMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

/**
 * Counter charms: each is crafted from its recipe, and each changes its mechanic's number only from the off-hand.
 * A charm test runs the real mechanic twice, the charm in the off-hand and the same charm in the main hand.
 */
final class CharmTests {
    private CharmTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("charms_craftable", CharmTests::craftable);
        out.accept("charm_shellward", CharmTests::shellward);
        out.accept("charm_shellward_snowroller", CharmTests::shellwardSnowroller);
        out.accept("charm_shellward_grazer", CharmTests::shellwardGrazer);
        out.accept("charm_felt", CharmTests::felt);
        OverworldCharmTests.register(out);
        NetherCharmTests.register(out);
        EndCharmTests.register(out);
    }

    private static ItemStack stack(String part) {
        String[] w = part.split("\\*");
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(w[0])), Integer.parseInt(w[1]));
    }

    /** What crafting {@code stacks} (one item a slot) in a 3-wide grid makes, or empty. */
    private static ItemStack craft(GameTestHelper helper, List<ItemStack> stacks) {
        List<ItemStack> grid = new ArrayList<>(stacks);
        while (grid.size() % 3 != 0) grid.add(ItemStack.EMPTY);
        CraftingInput input = CraftingInput.of(3, grid.size() / 3, grid);
        return helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    /** One slot per item of the recipe's "item*count" parts, leaving out the part at {@code skip} (-1 for none). */
    private static List<ItemStack> slots(List<String> recipe, int skip) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < recipe.size(); i++) {
            if (i == skip) continue;
            ItemStack s = stack(recipe.get(i));
            for (int n = 0; n < s.getCount(); n++) out.add(s.copyWithCount(1));
        }
        return out;
    }

    /** Every charm's recipe makes one charm through the recipe manager, none makes it with a part missing, and each has its unlock. */
    private static void craftable(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        if (ModCharms.ALL.isEmpty()) wrong.add("no charms registered");
        for (DeferredItem<CharmItem> charm : ModCharms.ALL) {
            String id = charm.getId().getPath();
            List<String> recipe = ModCharms.RECIPES.getOrDefault(id, List.of());
            ItemStack out = craft(helper, slots(recipe, -1));
            if (!out.is(charm.get()) || out.getCount() != 1) wrong.add(id + ": its recipe gives " + out);
            for (int skip = 0; skip < recipe.size(); skip++) {
                if (craft(helper, slots(recipe, skip)).is(charm.get())) wrong.add(id + ": crafts without " + recipe.get(skip));
            }
            if (charm.get().getDefaultMaxStackSize() != 1) wrong.add(id + " stacks");
            if (helper.getLevel().getServer().getAdvancements().get(TeemingMod.id("recipes/equipment/" + id)) == null) wrong.add(id + " has no unlock advancement");
        }
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }

    /**
     * Two Thornhorns charge two survival players side by side, one with a Shellward Charm in the off-hand and one with
     * it in the main hand. The hit's damage gives vanilla's own 0.4 shove first, which the charge's knockback then
     * halves, so a throw is 0.2 plus the charge's share: the main hand keeps all of {@link Thornhorn#KNOCKBACK}, the
     * off-hand {@link Charms#SHELLWARD_THROW} of it, and the two throws differ by exactly the share the charm took.
     */
    private static void shellward(GameTestHelper helper) {
        Vec3[] off = charge(helper, 2.5, InteractionHand.OFF_HAND);
        Vec3[] main = charge(helper, 8.5, InteractionHand.MAIN_HAND);
        double vanilla = 0.4 / 2;
        helper.succeedWhen(() -> {
            helper.assertTrue(off[0] != null && main[0] != null, "both charges hit");
            double kept = off[0].horizontalDistance(), full = main[0].horizontalDistance();
            // a standing player's own motion adds a few hundredths before the knockbacks halve it
            helper.assertTrue(Math.abs(full - (vanilla + Thornhorn.KNOCKBACK)) < 0.08, "the charm in the main hand leaves the full throw "
                    + (vanilla + Thornhorn.KNOCKBACK) + ", got " + full + " (motion " + main[0] + ")");
            double taken = Thornhorn.KNOCKBACK * (1.0 - Charms.SHELLWARD_THROW);
            helper.assertTrue(Math.abs(full - kept - taken) < 0.05, "the charm in the off-hand takes " + taken + " off the throw, took "
                    + (full - kept) + " (off-hand motion " + off[0] + ", main hand " + main[0] + ")");
        });
    }

    /** A Thornhorn charging along z = {@code z} at a survivor holding a Shellward Charm in {@code hand}; the survivor's motion as the hit lands. */
    private static Vec3[] charge(GameTestHelper helper, double z, InteractionHand hand) {
        Thornhorn horn = helper.spawn(ModMobs.THORNHORN.get(), new Vec3(1.5, 1, z));
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(10.5, 1, z));
        player.setItemInHand(hand, new ItemStack(ModCharms.SHELLWARD_CHARM.get()));
        horn.setTarget(player);
        horn.resetCooldown();
        float full = player.getHealth();
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && player.getHealth() < full) thrown[0] = player.getDeltaMovement();
        });
        return thrown;
    }

    /**
     * Two Snowrollers roll at two still pigs side by side, one with a Shellward Charm in the off-hand and one with it in
     * the main hand. As with a charge, the roll's damage gives vanilla's own 0.4 shove first and the roll's knockback
     * then halves it, so a throw is 0.2 plus the roll's share: the main hand keeps all of {@link Snowroller#KNOCKBACK},
     * the off-hand {@link Charms#SHELLWARD_THROW} of it. Read the tick each hit lands, the two throws differ by exactly
     * the share the charm took.
     */
    private static void shellwardSnowroller(GameTestHelper helper) {
        Vec3[] off = rolledInto(helper, 2.5, InteractionHand.OFF_HAND);
        Vec3[] main = rolledInto(helper, 9.5, InteractionHand.MAIN_HAND);
        double vanilla = 0.4 / 2;
        helper.succeedWhen(() -> {
            helper.assertTrue(off[0] != null && main[0] != null, "both rolls hit");
            double kept = off[0].horizontalDistance(), full = main[0].horizontalDistance();
            helper.assertTrue(Math.abs(full - (vanilla + Snowroller.KNOCKBACK)) < 0.08, "the charm in the main hand leaves the full throw "
                    + (vanilla + Snowroller.KNOCKBACK) + ", got " + full + " (motion " + main[0] + ")");
            double taken = Snowroller.KNOCKBACK * (1.0 - Charms.SHELLWARD_THROW);
            helper.assertTrue(Math.abs(full - kept - taken) < 0.05, "the charm in the off-hand takes " + taken + " off the throw, took "
                    + (full - kept) + " (off-hand motion " + off[0] + ", main hand " + main[0] + ")");
            TeemingMod.LOGGER.info("charm_shellward_snowroller: throw off-hand {} main hand {}", kept, full);
        });
    }

    /**
     * A Snowroller at x 1.5 hunting, along z = {@code z}, a still pig 9 blocks east that holds a Shellward Charm in
     * {@code hand}; the pig's motion the tick the roll hits it. A still pig keeps whatever motion it is given.
     */
    private static Vec3[] rolledInto(GameTestHelper helper, double z, InteractionHand hand) {
        Snowroller roller = helper.spawn(ModMobs.SNOWROLLER.get(), new Vec3(1.5, 1, z));
        roller.core().setLook(LookDef.BASE, true);
        roller.removeFreeWill();
        roller.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        roller.resetCooldown();
        TestPlayers.whenDone(helper, roller::discard);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(10.5, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        pig.setItemInHand(hand, new ItemStack(ModCharms.SHELLWARD_CHARM.get()));
        TestPlayers.whenDone(helper, pig::discard);
        roller.setTarget(pig);
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && pig.getHealth() < 100.0F) thrown[0] = pig.getDeltaMovement();
        });
        return thrown;
    }

    /**
     * Two stampeding Woolcap Grazers run into two survival players side by side, one with a Shellward Charm in the
     * off-hand and one with it in the main hand. Each grazer is hurt by a still pig west of it, so it runs east into its
     * player, and each threat sets off only its own lane's grazer. A shove does no damage, so no vanilla shove mixes in.
     * Read the tick it lands, the main-hand player is thrown the full {@link WoolcapGrazer#KNOCKBACK} across and
     * {@link WoolcapGrazer#KNOCKBACK_LIFT} up, and the off-hand player's throw is short of it by the share the charm takes
     * ({@code 1 - }{@link Charms#SHELLWARD_THROW}) of each.
     */
    private static void shellwardGrazer(GameTestHelper helper) {
        WoolcapGrazer offGrazer = grazer(helper, 2.5), mainGrazer = grazer(helper, 9.5);
        Pig offThreat = threat(helper, 2.5), mainThreat = threat(helper, 9.5);
        ServerPlayer off = TestPlayers.survivor(helper, new Vec3(6.5, 1, 2.5));
        ServerPlayer main = TestPlayers.survivor(helper, new Vec3(6.5, 1, 9.5));
        off.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModCharms.SHELLWARD_CHARM.get()));
        main.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModCharms.SHELLWARD_CHARM.get()));
        // a hurt sets off every grazer within 16 blocks, so the two lanes would share one run; and other arenas' grazers stay put
        Consumer<MechanicEvent> lanes = e -> {
            if (!e.step().equals("stampede") || e.target() != offThreat && e.target() != mainThreat) return;
            boolean own = e.mob() == offGrazer && e.target() == offThreat || e.mob() == mainGrazer && e.target() == mainThreat;
            if (!own) e.cancel();
        };
        MechanicEvent.listen(lanes);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(lanes));
        Vec3[] offShove = shoved(helper, off), mainShove = shoved(helper, main);
        helper.runAtTickTime(3, () -> {
            offGrazer.hurtServer(helper.getLevel(), offGrazer.damageSources().mobAttack(offThreat), 1.0F);
            mainGrazer.hurtServer(helper.getLevel(), mainGrazer.damageSources().mobAttack(mainThreat), 1.0F);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(offShove[0] != null && mainShove[0] != null, "both grazers ran into their players");
            Vec3 o = offShove[0], m = mainShove[0];
            double across = WoolcapGrazer.KNOCKBACK, up = WoolcapGrazer.KNOCKBACK_LIFT, taken = 1.0 - Charms.SHELLWARD_THROW;
            helper.assertTrue(Math.abs(m.horizontalDistance() - across) < 0.001 && Math.abs(m.y - up) < 0.001,
                    "the charm in the main hand leaves the full shove, " + across + " across and " + up + " up, motion " + m);
            helper.assertTrue(Math.abs(m.horizontalDistance() - o.horizontalDistance() - across * taken) < 0.001 && Math.abs(m.y - o.y - up * taken) < 0.001,
                    "the charm in the off-hand takes " + across * taken + " across and " + up * taken + " up off the shove, off-hand motion "
                            + o + ", main hand " + m);
            TeemingMod.LOGGER.info("charm_shellward_grazer: shove off-hand {} main hand {}", o, m);
        });
    }

    /** A grazer at (2.5, z) with no will of its own: only a hurt sets it running. */
    private static WoolcapGrazer grazer(GameTestHelper helper, double z) {
        WoolcapGrazer g = helper.spawn(ModMobs.WOOLCAP_GRAZER.get(), new Vec3(2.5, 1, z));
        g.core().setLook(LookDef.BASE, true);
        g.removeFreeWill();
        TestPlayers.whenDone(helper, g::discard);
        return g;
    }

    /** A still pig at (0.5, z), west of its lane's grazer: the threat the grazer runs from. */
    private static Pig threat(GameTestHelper helper, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(0.5, 1, z));
        pig.setNoAi(true);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** {@code who}'s motion on the tick a grazer first shoves it (the shove lands in the grazer's own tick), or null until then. */
    private static Vec3[] shoved(GameTestHelper helper, ServerPlayer who) {
        long[] at = {-1};
        Consumer<MechanicEvent> bump = e -> {
            if (at[0] < 0 && e.step().equals("bump") && e.target() == who && !e.cancelled()) at[0] = helper.getLevel().getGameTime();
        };
        MechanicEvent.listen(bump);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(bump));
        Vec3[] motion = {null};
        helper.onEachTick(() -> {
            if (motion[0] == null && at[0] == helper.getLevel().getGameTime()) motion[0] = who.getDeltaMovement();
        });
        return motion;
    }

    /** A chance charm and its roll, which {@link Charms#felt} wraps. */
    private record Chance(String name, DeferredItem<CharmItem> charm, BiPredicate<LivingEntity, RandomSource> saves) {}

    /**
     * A chance charm that saves its holder says so, for each of the three (Gilded Ward, Rift and Memory): the first save
     * from the off-hand chimes and is recorded on its tick, a second save within {@link Charms#FELT_COOLDOWN} stays
     * quiet, and the same charm in the main hand never saves.
     */
    private static void felt(GameTestHelper helper) {
        List<Chance> chances = List.of(
                new Chance("Gilded Ward", ModCharms.GILDED_WARD_CHARM, Charms::theftFails),
                new Chance("Rift", ModCharms.RIFT_CHARM, Charms::teleportFails),
                new Chance("Memory", ModCharms.MEMORY_CHARM, Charms::xpTheftFails));
        List<Pig> offs = new ArrayList<>(), mains = new ArrayList<>();
        for (int i = 0; i < chances.size(); i++) {
            Pig off = helper.spawn(EntityTypes.PIG, new Vec3(2.5, 1, 2.5 + 3.5 * i));
            Pig main = helper.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 2.5 + 3.5 * i));
            off.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(chances.get(i).charm().get()));
            main.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(chances.get(i).charm().get()));
            offs.add(off);
            mains.add(main);
        }
        helper.runAtTickTime(2, () -> {
            long now = helper.getLevel().getGameTime();
            for (int i = 0; i < chances.size(); i++) {
                Chance c = chances.get(i);
                Pig off = offs.get(i), main = mains.get(i);
                RandomSource random = RandomSource.create(7);
                int tries = 0;
                while (!c.saves().test(off, random)) helper.assertTrue(++tries < 64, c.name() + ": the off-hand charm saved once in 64 rolls");
                helper.assertTrue(Charms.lastFelt(off) == now, c.name() + ": the save chimed on its tick, recorded " + Charms.lastFelt(off) + ", now " + now);
                for (int n = 0; n < 200; n++) c.saves().test(main, random);
                helper.assertTrue(Charms.lastFelt(main) == -1, c.name() + ": the main-hand charm never saves, so never chimes");
            }
        });
        helper.runAtTickTime(10, () -> {
            for (int i = 0; i < chances.size(); i++) {
                Chance c = chances.get(i);
                Pig off = offs.get(i);
                long first = Charms.lastFelt(off);
                RandomSource random = RandomSource.create(11);
                int tries = 0;
                while (!c.saves().test(off, random)) helper.assertTrue(++tries < 64, c.name() + ": a second save");
                helper.assertTrue(Charms.lastFelt(off) == first, c.name() + ": a save 8 ticks later stays quiet, recorded "
                        + Charms.lastFelt(off) + ", first " + first);
            }
            helper.succeed();
        });
    }
}
