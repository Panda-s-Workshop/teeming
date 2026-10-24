package com.panda.teeming.gametest;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.item.MaterialItem;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.registry.ModItems;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Every signature material has a working use, the Field Guide is crafted from one, and built mobs drop theirs. */
final class MaterialTests {
    private static final int ROLLS = 200;

    private MaterialTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("materials_have_uses", MaterialTests::haveUses);
        out.accept("materials_field_guide_recipe", MaterialTests::fieldGuideRecipe);
        out.accept("materials_drop", MaterialTests::drop);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalStateException("no item " + id));
    }

    private static Holder<Potion> potion(String id) {
        return BuiltInRegistries.POTION.get(Identifier.parse(id)).orElseThrow(() -> new IllegalStateException("no potion " + id));
    }

    /** What crafting {@code stacks} in a 3-wide grid makes, or empty. */
    private static ItemStack craft(GameTestHelper helper, List<ItemStack> stacks) {
        List<ItemStack> grid = new ArrayList<>(stacks);
        int width = Math.min(3, grid.size()), height = (grid.size() + width - 1) / width;
        while (grid.size() < width * height) grid.add(ItemStack.EMPTY);
        CraftingInput input = CraftingInput.of(width, height, grid);
        return helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    /** The recipe-book unlock advancement exists for a crafting recipe (vanilla gives every one an unlock). */
    private static void unlock(MinecraftServer server, String recipe, List<String> wrong) {
        Identifier id = TeemingMod.id("recipes/misc/" + recipe);
        if (server.getAdvancements().get(id) == null) wrong.add("no unlock advancement " + id);
    }

    /** Each use in {@link ModMaterials#USES} works through the server's recipe manager and gives what it says. */
    private static void haveUses(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<String> wrong = new ArrayList<>();
        for (DeferredItem<MaterialItem> mat : ModMaterials.ALL) {
            String id = mat.getId().getPath();
            List<String> uses = ModMaterials.USES.getOrDefault(id, List.of());
            if (uses.isEmpty()) wrong.add(id + " has no use");
            for (String use : uses) {
                String[] w = use.split(" ");
                if (w[0].equals("craft")) {
                    int in = Integer.parseInt(w[1]), want = Integer.parseInt(w[3]);
                    List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(in, new ItemStack(mat.get())));
                    for (int i = 4; i < w.length; i++) {                          // extra ingredients, item*count
                        String[] extra = w[i].split("\\*");
                        for (int n = 0; n < Integer.parseInt(extra[1]); n++) stacks.add(new ItemStack(item(extra[0])));
                    }
                    ItemStack out = craft(helper, stacks);
                    if (!out.is(item(w[2])) || out.getCount() != want) wrong.add(id + ": " + use + " gives " + out + ", want " + want + " " + w[2]);
                    if (w.length > 4 && !craft(helper, Collections.nCopies(in, new ItemStack(mat.get()))).isEmpty() && in > 1) {
                        wrong.add(id + ": " + use + " also crafts without its extra ingredients");
                    }
                    unlock(server, id + "_to_" + Identifier.parse(w[2]).getPath(), wrong);
                } else if (w[0].equals("tipped")) {
                    List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(8, new ItemStack(Items.ARROW)));
                    stacks.add(new ItemStack(mat.get()));
                    ItemStack out = craft(helper, stacks);
                    PotionContents contents = out.get(DataComponents.POTION_CONTENTS);
                    if (!out.is(Items.TIPPED_ARROW) || out.getCount() != 8 || contents == null || !contents.is(potion(w[1]))) {
                        wrong.add(id + ": 8 arrows and one give " + out + ", want 8 tipped arrows of " + w[1]);
                    }
                    unlock(server, id + "_to_tipped_arrow", wrong);
                } else if (w[0].equals("brew")) {
                    BrewingInput input = new BrewingInput(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD), new ItemStack(mat.get()));
                    ItemStack out = server.getRecipeManager().getRecipeFor(RecipeType.BREWING, input, helper.getLevel())
                            .map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
                    PotionContents contents = out.get(DataComponents.POTION_CONTENTS);
                    if (!out.is(Items.POTION) || contents == null || !contents.is(potion(w[1]))) wrong.add(id + ": brewing an Awkward Potion gives " + out + ", want " + w[1]);
                    BrewingInput water = new BrewingInput(PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(mat.get()));
                    if (server.getRecipeManager().getRecipeFor(RecipeType.BREWING, water, helper.getLevel()).isPresent()) wrong.add(id + " also brews a Water Bottle");
                } else {
                    wrong.add(id + ": unknown use " + use);
                }
            }
        }
        if (ModMaterials.ALL.isEmpty()) wrong.add("no materials registered");
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }

    /** A book and any signature material craft a Field Guide; a book alone does not. */
    private static void fieldGuideRecipe(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        for (DeferredItem<MaterialItem> mat : ModMaterials.ALL) {
            ItemStack out = craft(helper, List.of(new ItemStack(Items.BOOK), new ItemStack(mat.get())));
            if (!out.is(ModItems.FIELD_GUIDE.get())) wrong.add("book + " + mat.getId().getPath() + " gives " + out);
        }
        if (craft(helper, List.of(new ItemStack(Items.BOOK))).is(ModItems.FIELD_GUIDE.get())) wrong.add("a book alone makes a Field Guide");
        unlock(helper.getLevel().getServer(), "field_guide", wrong);
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }

    /** Every built mob drops its roster material on each of 200 deaths, 1 without Looting. */
    private static void drop(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RandomSource random = RandomSource.create(20261024L);
        List<String> wrong = new ArrayList<>();
        for (RosterStats.Entry row : RosterStats.ALL) {
            var type = TeemingSpawns.type(row).orElse(null);
            if (type == null || row.material().isEmpty()) continue;
            Item want = item(TeemingMod.MODID + ":" + row.material().replace(' ', '_'));
            Mob mob = (Mob) helper.spawn(type, new Vec3(5.5, 1, 5.5));
            mob.setNoAi(true);
            LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable().orElseThrow());
            LootParams params = new LootParams.Builder(helper.getLevel())
                    .withParameter(LootContextParams.THIS_ENTITY, mob)
                    .withParameter(LootContextParams.ORIGIN, mob.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, mob.damageSources().playerAttack(player))
                    .withParameter(LootContextParams.ATTACKING_ENTITY, player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .create(LootContextParamSets.ENTITY);
            int missing = 0, other = 0;
            for (int i = 0; i < ROLLS; i++) {
                int count = 0;
                for (ItemStack stack : table.getRandomItems(params, random)) if (stack.is(want)) count += stack.getCount();
                if (count == 0) missing++;
                else if (count != 1) other++;
            }
            if (missing > 0 || other > 0) wrong.add(row.id() + ": " + missing + " of " + ROLLS + " deaths without " + want + ", " + other + " with more than 1");
            mob.discard();
        }
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }
}
