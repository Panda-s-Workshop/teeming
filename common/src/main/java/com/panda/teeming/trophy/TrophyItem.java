package com.panda.teeming.trophy;

import com.panda.teeming.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** A trophy item, named after its mob ("Thornhorn Trophy"). */
public class TrophyItem extends BlockItem {
    public TrophyItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        TrophyMob mob = stack.getOrDefault(ModComponents.TROPHY_MOB.get(), TrophyMob.NONE);
        if (mob.isEmpty()) return super.getName(stack);
        return Component.translatable("item.teeming.trophy.named", Component.translatable("entity.teeming." + mob.mob()));
    }
}
