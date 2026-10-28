package com.panda.teeming.gametest;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.charm.Charms;
import com.panda.teeming.item.CharmItem;
import com.panda.teeming.mob.overworld.Thornhorn;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.registry.ModCharms;
import com.panda.teeming.registry.ModMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
     * A chance charm that saves its holder says so: the first save from the off-hand chimes and is recorded on its tick,
     * a second save within {@link Charms#FELT_COOLDOWN} stays quiet, and the same charm in the main hand never saves.
     */
    private static void felt(GameTestHelper helper) {
        net.minecraft.world.entity.animal.pig.Pig off = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, new Vec3(2.5, 1, 2.5));
        net.minecraft.world.entity.animal.pig.Pig main = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, new Vec3(6.5, 1, 2.5));
        off.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModCharms.GILDED_WARD_CHARM.get()));
        main.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModCharms.GILDED_WARD_CHARM.get()));
        helper.runAtTickTime(2, () -> {
            net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7);
            long now = helper.getLevel().getGameTime();
            int tries = 0;
            while (!Charms.theftFails(off, random)) helper.assertTrue(++tries < 64, "the off-hand charm saved once in 64 rolls");
            helper.assertTrue(Charms.lastFelt(off) == now, "the save chimed on its tick, recorded " + Charms.lastFelt(off) + ", now " + now);
            for (int i = 0; i < 200; i++) Charms.theftFails(main, random);
            helper.assertTrue(Charms.lastFelt(main) == -1, "the main-hand charm never saves, so never chimes");
        });
        helper.runAtTickTime(10, () -> {
            long first = Charms.lastFelt(off);
            net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(11);
            int tries = 0;
            while (!Charms.theftFails(off, random)) helper.assertTrue(++tries < 64, "a second save");
            helper.assertTrue(Charms.lastFelt(off) == first, "a save 8 ticks later stays quiet, recorded " + Charms.lastFelt(off) + ", first " + first);
            helper.succeed();
        });
    }
}
