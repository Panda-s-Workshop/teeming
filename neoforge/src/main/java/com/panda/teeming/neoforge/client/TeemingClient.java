package com.panda.teeming.neoforge.client;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.DevExit;
import com.panda.teeming.client.ClientSinks;
import com.panda.teeming.client.TeemingClientInit;
import com.panda.teeming.client.mob.CreatureLayers;
import com.panda.teeming.client.mob.MobRenderers;
import com.panda.teeming.client.trophy.TrophyRenderer;
import com.panda.teeming.registry.ModBlockEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** The NeoForge client entry point: hands the common client registrations NeoForge's events. */
@Mod(value = TeemingMod.MODID, dist = Dist.CLIENT)
public final class TeemingClient {
    public TeemingClient(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent e) -> DevExit.onClientStopping());
        TeemingClientInit.init();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> TeemingClientInit.tick());
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> MobRenderers.register(new ClientSinks.Renderers() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider) {
                e.registerEntityRenderer(type, provider);
            }
        }));
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> CreatureLayers.register(e::registerLayerDefinition));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerBlockEntityRenderer(ModBlockEntities.TROPHY.get(), TrophyRenderer::new));
    }
}
