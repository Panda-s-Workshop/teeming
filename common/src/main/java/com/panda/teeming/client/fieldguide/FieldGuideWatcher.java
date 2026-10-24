package com.panda.teeming.client.fieldguide;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Turns Field Guide changes into toasts on the client: it compares the synced progress with what it saw last tick, so
 * no packet is needed. The first progress after joining is taken as it is, without toasts.
 */
public final class FieldGuideWatcher {
    private static @Nullable FieldGuideProgress last;

    private FieldGuideWatcher() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            last = null;
            return;
        }
        FieldGuideProgress now = FieldGuide.get(mc.player);
        if (last != null && now != last) {
            for (String mob : now.seen()) {
                if (!last.hasSeen(mob)) toast(mc, "seen", mob);
            }
            for (String mob : now.kills().keySet()) {
                if (now.revealed(mob, FieldGuide.KILLS_TO_REVEAL) && !last.revealed(mob, FieldGuide.KILLS_TO_REVEAL)) toast(mc, "revealed", mob);
            }
        }
        last = now;
    }

    private static void toast(Minecraft mc, String kind, String mob) {
        ItemStack icon = BuiltInRegistries.ITEM.getOptional(TeemingMod.id(mob + "_spawn_egg")).map(ItemStack::new)
                .orElseGet(() -> new ItemStack(ModItems.FIELD_GUIDE.get()));
        mc.gui.toastManager().addToast(new FieldGuideToast(Component.translatable("toast.teeming.field_guide." + kind),
                Component.translatable("entity.teeming." + mob), icon, kind + ":" + mob));
        if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.2F);
    }
}
