package com.panda.teeming.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A counter charm. It does nothing itself: the mobs' mechanics ask {@link com.panda.teeming.charm.Charms} whether their
 * target holds it in the off-hand. Its tooltip says what it answers, from {@code item.teeming.<id>.effect} and
 * {@code .dimension}, and where it works, from {@code item.teeming.charm.offhand}.
 */
public class CharmItem extends Item {
    public CharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        String key = getDescriptionId();
        lines.accept(Component.translatable(key + ".effect").withStyle(ChatFormatting.DARK_AQUA));
        lines.accept(Component.translatable(key + ".dimension").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("item.teeming.charm.offhand").withStyle(ChatFormatting.GOLD));
    }
}
