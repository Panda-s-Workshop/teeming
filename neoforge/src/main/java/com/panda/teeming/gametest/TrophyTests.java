package com.panda.teeming.gametest;

import com.panda.teeming.mob.overworld.Thornhorn;
import com.panda.teeming.registry.ModBlocks;
import com.panda.teeming.registry.ModComponents;
import com.panda.teeming.registry.ModItems;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.trophy.TrophyBlockEntity;
import com.panda.teeming.trophy.TrophyMob;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The trophy roll in a mob's loot table, the look it carries, and a placed trophy keeping its mob when broken. */
final class TrophyTests {
    private static final int ROLLS = 2000;

    private TrophyTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("trophy_drop_rate", TrophyTests::dropRate);
        out.accept("trophy_roundtrip", TrophyTests::roundtrip);
    }

    private static LootParams params(GameTestHelper helper, Thornhorn mob, ServerPlayer player, boolean byPlayer) {
        LootParams.Builder b = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, byPlayer ? mob.damageSources().playerAttack(player) : mob.damageSources().magic());
        if (byPlayer) b.withParameter(LootContextParams.ATTACKING_ENTITY, player).withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        return b.create(LootContextParamSets.ENTITY);
    }

    /** 2,000 player kills drop about 2.5 % trophies (within 3 sigma), each in the dead mob's look; other deaths none. */
    private static void dropRate(GameTestHelper helper) {
        Thornhorn mob = helper.spawn(ModMobs.THORNHORN.get(), new Vec3(5.5, 1, 5.5));
        mob.setNoAi(true);
        mob.core().setLook("sunflower", false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(mob.getType().getDefaultLootTable().orElseThrow());
        RandomSource random = RandomSource.create(20261024L);
        int trophies = 0;
        for (int i = 0; i < ROLLS; i++) {
            for (ItemStack stack : table.getRandomItems(params(helper, mob, player, true), random)) {
                if (!stack.is(ModItems.TROPHY.get())) continue;
                trophies++;
                TrophyMob shown = stack.get(ModComponents.TROPHY_MOB.get());
                helper.assertTrue(shown != null && shown.mob().equals("thornhorn") && shown.look().equals("sunflower"),
                        "the trophy shows the Thornhorn in its sunflower look, got " + shown);
            }
        }
        double expected = ROLLS * 0.025, sigma = Math.sqrt(ROLLS * 0.025 * 0.975);
        helper.assertTrue(Math.abs(trophies - expected) <= 3 * sigma, trophies + " trophies in " + ROLLS + " player kills, expected " + expected + " +- " + (int) (3 * sigma));
        int stray = 0;
        for (int i = 0; i < 400; i++) {
            for (ItemStack stack : table.getRandomItems(params(helper, mob, player, false), random)) if (stack.is(ModItems.TROPHY.get())) stray++;
        }
        helper.assertTrue(stray == 0, "deaths without a player drop no trophies, got " + stray);
        helper.succeed();
    }

    /** A trophy placed from an item keeps its mob and look, and breaking it drops the same item back. */
    private static void roundtrip(GameTestHelper helper) {
        BlockPos rel = new BlockPos(5, 1, 5);
        helper.setBlock(rel, ModBlocks.TROPHY.get());
        TrophyBlockEntity be = (TrophyBlockEntity) helper.getBlockEntity(rel, TrophyBlockEntity.class);
        ItemStack placed = new ItemStack(ModItems.TROPHY.get());
        placed.set(ModComponents.TROPHY_MOB.get(), new TrophyMob("levimoth", "pale_levimoth"));
        be.applyComponentsFromItemStack(placed);
        helper.assertTrue(be.mob().equals(new TrophyMob("levimoth", "pale_levimoth")), "placing copies the mob and look, got " + be.mob());
        BlockPos abs = helper.absolutePos(rel);
        helper.getLevel().destroyBlock(abs, true);
        helper.runAtTickTime(2, () -> {
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(ModItems.TROPHY.get()), "breaking drops one trophy, got " + drops.size());
            TrophyMob kept = drops.getFirst().getItem().get(ModComponents.TROPHY_MOB.get());
            helper.assertTrue(new TrophyMob("levimoth", "pale_levimoth").equals(kept), "the drop keeps the mob and look, got " + kept);
            helper.assertTrue(drops.getFirst().getItem().getHoverName().getString().equals("Levimoth Trophy"),
                    "it is named after its mob, got " + drops.getFirst().getItem().getHoverName().getString());
            helper.succeed();
        });
    }
}
