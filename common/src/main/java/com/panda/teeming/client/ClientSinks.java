package com.panda.teeming.client;

import java.util.function.Supplier;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** What client registration hands its content to. Each loader's client entry adapts these to its own events. */
public final class ClientSinks {
    private ClientSinks() {}

    public interface Renderers {
        <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider);
    }

    public interface Layers {
        void register(ModelLayerLocation layer, Supplier<LayerDefinition> definition);
    }
}
