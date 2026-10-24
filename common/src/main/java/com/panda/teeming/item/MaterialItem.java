package com.panda.teeming.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A signature material. Its tooltip names who drops it, what it is for, and how to craft the Field Guide from it,
 * from the lang keys {@code item.teeming.<id>.source}, {@code .use} and {@code item.teeming.material.guide}.
 */
public class MaterialItem extends Item {
    public MaterialItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        String key = getDescriptionId();
        lines.accept(Component.translatable(key + ".source").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable(key + ".use").withStyle(ChatFormatting.DARK_AQUA));
        lines.accept(Component.translatable("item.teeming.material.guide").withStyle(ChatFormatting.GOLD));
    }
}
