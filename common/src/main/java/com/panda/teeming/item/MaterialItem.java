package com.panda.teeming.item;

import com.panda.teeming.registry.ModMaterials;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A signature material. Its tooltip names who drops it, what it is for, and how to craft the Field Guide from it,
 * from the lang keys {@code item.teeming.<id>.source} (a list too long for one line goes on over {@code .source.2} and
 * on), {@code .use.<n>} (one line per use) and {@code item.teeming.material.guide}.
 */
public class MaterialItem extends Item {
    public MaterialItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        String key = getDescriptionId();
        CharmItem.lines(lines, key + ".source", ChatFormatting.GRAY);
        int uses = ModMaterials.USES.getOrDefault(BuiltInRegistries.ITEM.getKey(this).getPath(), List.of()).size();
        for (int i = 0; i < uses; i++) lines.accept(Component.translatable(key + ".use." + i).withStyle(ChatFormatting.DARK_AQUA));
        lines.accept(Component.translatable("item.teeming.material.guide").withStyle(ChatFormatting.GOLD));
    }
}
