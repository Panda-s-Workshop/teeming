package com.panda.teeming.client.fieldguide;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.registry.ModItems;
import java.util.List;
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
    /** More changes than this in one tick show as one toast. */
    public static final int EACH_UP_TO = 3;
    private static @Nullable FieldGuideProgress last;

    private FieldGuideWatcher() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            last = null;
            return;
        }
        FieldGuideProgress now = FieldGuide.get(mc.player);
        if (last != null && now != last) {
            FieldGuideProgress before = last;
            toasts(mc, "seen", now.seen().stream().filter(mob -> !before.hasSeen(mob)).toList());
            toasts(mc, "revealed", now.kills().keySet().stream().filter(mob -> now.revealed(mob) && !before.revealed(mob)).toList());
        }
        last = now;
    }

    /** One toast per mob, or a single one naming the count when more than {@link #EACH_UP_TO} change at once (a command). */
    private static void toasts(Minecraft mc, String kind, List<String> mobs) {
        if (mobs.size() <= EACH_UP_TO) {
            mobs.forEach(mob -> toast(mc, kind, mob));
            return;
        }
        mc.gui.toastManager().addToast(new FieldGuideToast(Component.translatable("toast.teeming.field_guide." + kind),
                Component.translatable("toast.teeming.field_guide.many", mobs.size()), new ItemStack(ModItems.FIELD_GUIDE.get()), kind + ":many"));
        if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.2F);
    }

    private static void toast(Minecraft mc, String kind, String mob) {
        ItemStack icon = BuiltInRegistries.ITEM.getOptional(TeemingMod.id(mob + "_spawn_egg")).map(ItemStack::new)
                .orElseGet(() -> new ItemStack(ModItems.FIELD_GUIDE.get()));
        mc.gui.toastManager().addToast(new FieldGuideToast(Component.translatable("toast.teeming.field_guide." + kind),
                Component.translatable("entity.teeming." + mob), icon, kind + ":" + mob));
        if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.2F);
    }
}
