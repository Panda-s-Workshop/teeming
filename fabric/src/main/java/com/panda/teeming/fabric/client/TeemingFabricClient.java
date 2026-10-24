package com.panda.teeming.fabric.client;

import com.panda.teeming.DevExit;
import com.panda.teeming.client.ClientSinks;
import com.panda.teeming.client.TeemingClientInit;
import com.panda.teeming.client.mob.CreatureLayers;
import com.panda.teeming.client.mob.MobRenderers;
import com.panda.teeming.client.trophy.TrophyRenderer;
import com.panda.teeming.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** The Fabric client entry point: hands the common client registrations to Fabric API's registries and events. */
public final class TeemingFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> DevExit.onClientStopping());
        TeemingClientInit.init();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> TeemingClientInit.tick());
        MobRenderers.register(new ClientSinks.Renderers() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider) {
                EntityRendererRegistry.register(type, provider);
            }
        });
        CreatureLayers.register((layer, definition) -> ModelLayerRegistry.registerModelLayer(layer, definition::get));
        BlockEntityRenderers.register(ModBlockEntities.TROPHY.get(), TrophyRenderer::new);
    }
}
