package com.panda.teeming.client.fieldguide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** "New Field Guide entry: Dunefin" and "Field Guide: revealed", on vanilla's advancement toast frame. */
public class FieldGuideToast implements Toast {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_MS = 5000L;
    private static final int TITLE = 0xFFE8D8A8;

    private final Component title;
    private final Component line;
    private final ItemStack icon;
    private final Object token;
    private Toast.Visibility visibility = Toast.Visibility.SHOW;

    public FieldGuideToast(Component title, Component line, ItemStack icon, Object token) {
        this.title = title;
        this.line = line;
        this.icon = icon;
        this.token = token;
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        visibility = fullyVisibleForMs >= DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier() ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long fullyVisibleForMs) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
        g.text(font, title, 30, 7, TITLE, false);
        g.text(font, line, 30, 18, 0xFFFFFFFF, false);
        g.fakeItem(icon, 8, 8);
    }

    @Override
    public Object getToken() {
        return token;
    }
}
