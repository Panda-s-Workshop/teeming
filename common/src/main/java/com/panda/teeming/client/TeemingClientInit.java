package com.panda.teeming.client;

import com.panda.teeming.ClientHooks;
import com.panda.teeming.client.fieldguide.FieldGuideScreen;
import com.panda.teeming.client.fieldguide.FieldGuideWatcher;
import net.minecraft.client.Minecraft;

/** The common client side's setup and tick. Each loader's client entry calls these. */
public final class TeemingClientInit {
    private TeemingClientInit() {}

    public static void init() {
        ClientHooks.openFieldGuide = () -> Minecraft.getInstance().gui.setScreen(new FieldGuideScreen());
    }

    /** After each client tick. */
    public static void tick() {
        FieldGuideWatcher.tick(Minecraft.getInstance());
    }
}
