package com.panda.teeming.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A counter charm. It does nothing itself: the mobs' mechanics ask {@link com.panda.teeming.charm.Charms} whether their
 * target holds it in the off-hand. Its tooltip says what it does, from {@code item.teeming.<id>.effect}, the mobs it
 * answers, from {@code .dimension} and, for a list too long for one line, {@code .dimension.2}, {@code .dimension.3}
 * and on, and where it works, from {@code item.teeming.charm.offhand}.
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
        for (int i = 2; Language.getInstance().has(key + ".dimension." + i); i++) {
            lines.accept(Component.translatable(key + ".dimension." + i).withStyle(ChatFormatting.GRAY));
        }
        lines.accept(Component.translatable("item.teeming.charm.offhand").withStyle(ChatFormatting.GOLD));
    }
}
