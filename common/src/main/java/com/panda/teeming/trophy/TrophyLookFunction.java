package com.panda.teeming.trophy;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.registry.ModComponents;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Loot function {@code teeming:trophy_look}: a dropped trophy shows the look of the mob that dropped it. */
public class TrophyLookFunction extends LootItemConditionalFunction {
    public static final MapCodec<TrophyLookFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).apply(i, TrophyLookFunction::new));

    protected TrophyLookFunction(Optional<Holder<LootItemCondition>> condition) {
        super(condition);
    }

    @Override
    public MapCodec<TrophyLookFunction> codec() {
        return MAP_CODEC;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
        TrophyMob mob = stack.get(ModComponents.TROPHY_MOB.get());
        if (mob != null && entity instanceof TeemingCreature creature && !creature.look().isEmpty()) {
            stack.set(ModComponents.TROPHY_MOB.get(), mob.withLook(creature.look()));
        }
        return stack;
    }
}
