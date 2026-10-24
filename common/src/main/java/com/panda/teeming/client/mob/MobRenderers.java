package com.panda.teeming.client.mob;

import com.panda.teeming.client.ClientSinks;
import com.panda.teeming.registry.ModMobs;
import java.util.Set;

/** One renderer per mob. */
public final class MobRenderers {
    private MobRenderers() {}

    public static void register(ClientSinks.Renderers out) {
        out.register(ModMobs.THORNHORN.get(), ctx -> new TeemingRenderer<>(ctx, new ThornhornModel(ctx.bakeLayer(CreatureLayers.THORNHORN)), "thornhorn",
                Set.of(), 0.8F, 1.0F, 1, true));
        out.register(ModMobs.SLAGHEART_GOLEM.get(), ctx -> new TeemingRenderer<>(ctx, new SlagheartGolemModel(ctx.bakeLayer(CreatureLayers.SLAGHEART_GOLEM)), "slagheart_golem",
                Set.of("base", "cooled_golem"), 0.9F, 1.15F, 4, true));
        out.register(ModMobs.LEVIMOTH.get(), ctx -> new TeemingRenderer<>(ctx, new LevimothModel(ctx.bakeLayer(CreatureLayers.LEVIMOTH)), "levimoth",
                Set.of("base", "pale_levimoth"), 0.35F, 1.0F, 1, false));
    }
}
