package com.panda.teeming.fabric;

import com.panda.teeming.DevExit;
import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import com.panda.teeming.platform.registry.DeferredRegister;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;

/** The Fabric entry point: registers the common content and wires the common hooks to Fabric API's events. */
public final class TeemingFabric implements ModInitializer {
    /**
     * Registries whose entries others are made from, registered first in this order: items take blocks, components and
     * entity types (spawn eggs). The rest follow in the order the mod created them.
     */
    private static final List<ResourceKey<?>> FIRST = List.of(Registries.BLOCK, Registries.DATA_COMPONENT_TYPE, Registries.SOUND_EVENT,
            Registries.PARTICLE_TYPE, Registries.ENTITY_TYPE, Registries.BLOCK_ENTITY_TYPE, Registries.ITEM);

    @Override
    public void onInitialize() {
        TeemingConfig.use(FabricConfig.load());
        TeemingMod.init();
        DeferredRegister.all().stream()
                .sorted(Comparator.comparingInt(r -> {
                    int i = FIRST.indexOf(r.getRegistryKey());
                    return i < 0 ? FIRST.size() : i;
                }))
                .forEach(TeemingFabric::registerAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DevExit.onServerStopped(server.isDedicatedServer()));
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Looks.ID, new Looks());
        ModMobs.attributes(FabricDefaultAttributeRegistry::register);
        ServerTickEvents.END_LEVEL_TICK.register(TeemingMod::onLevelTick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!TeemingMod.onEntityJoin(entity)) entity.discard();
        });
        TeemingSpawns.placements(new TeemingSpawns.Placements() {
            @Override
            public <T extends Mob> void set(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap,
                                            SpawnPlacements.SpawnPredicate<T> predicate) {
                SpawnPlacements.register(type, placement, heightmap, predicate);
            }
        });
        for (RosterStats.Entry mob : RosterStats.ALL) {
            if (!mob.biomeSpawns()) continue;
            TeemingSpawns.type(mob).ifPresent(type -> BiomeModifications.addSpawn(BiomeSelectors.tag(TeemingSpawns.tag(mob.id())),
                    TeemingSpawns.category(mob), type, mob.weight(), mob.groupMin(), mob.groupMax()));
        }
    }

    @SuppressWarnings("unchecked")
    private static <R> void registerAll(DeferredRegister<R> register) {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getValue(register.getRegistryKey().identifier());
        if (registry == null) throw new IllegalStateException("No registry " + register.getRegistryKey().identifier());
        register.registerAll((key, value) -> Registry.register(registry, key, value));
    }
}
