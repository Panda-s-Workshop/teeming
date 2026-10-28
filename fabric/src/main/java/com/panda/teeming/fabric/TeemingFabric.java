package com.panda.teeming.fabric;

import com.panda.teeming.DevExit;
import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.command.TeemingCommand;
import com.panda.teeming.effect.NulledEffect;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.SpawnTable;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.biome.Biome;
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
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            NulledEffect.reset();
            DevExit.onServerStopped(server.isDedicatedServer());
        });
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Looks.ID, new Looks());
        ModMobs.attributes(FabricDefaultAttributeRegistry::register);
        // the eggs also go in vanilla's Spawn Eggs tab, where players and pack makers look for them
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> ModMobs.EGGS.forEach(egg -> output.accept(egg.get())));
        ServerTickEvents.END_LEVEL_TICK.register(TeemingMod::onLevelTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> TeemingCommand.register(dispatcher));
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
            if (mob.biomeSpawns()) TeemingSpawns.type(mob).ifPresent(type -> addSpawns(mob, type));
        }
        for (SpawnTable.Cost cost : SpawnTable.COSTS) {
            ResourceKey<Biome> biome = ResourceKey.create(Registries.BIOME, Identifier.parse(cost.biome()));
            BiomeModifications.create(TeemingMod.id("spawn_costs/" + biome.identifier().getPath())).add(ModificationPhase.ADDITIONS,
                    BiomeSelectors.includeByKey(biome), context -> {
                        for (SpawnTable.Row row : SpawnTable.TUNED) {
                            if (!row.biome().equals(cost.biome())) continue;
                            TeemingSpawns.type(RosterStats.get(row.mob())).ifPresent(type ->
                                    context.getMobSpawnSettings().addMobCharge(type, cost.charge(), cost.energyBudget()));
                        }
                    });
        }
    }

    /**
     * The same spawns as the generated NeoForge biome modifiers, from the same table: the mob's tag gates every one; a
     * tuned biome gets its tuned weight; the rest of the tag gets the roster weight, without the tuned biomes and, for
     * monsters, without the share-capped biomes (the Teeming spawner places monsters there).
     */
    private static void addSpawns(RosterStats.Entry mob, EntityType<? extends Mob> type) {
        Predicate<BiomeSelectionContext> home = BiomeSelectors.tag(TeemingSpawns.tag(mob.id()));
        List<ResourceKey<Biome>> tuned = new ArrayList<>();
        for (SpawnTable.Row row : SpawnTable.tuned(mob.id())) {
            ResourceKey<Biome> biome = ResourceKey.create(Registries.BIOME, Identifier.parse(row.biome()));
            tuned.add(biome);
            BiomeModifications.addSpawn(home.and(BiomeSelectors.includeByKey(biome)), TeemingSpawns.category(mob), type, row.weight(),
                    mob.groupMin(), mob.groupMax());
        }
        Predicate<BiomeSelectionContext> rest = home.and(BiomeSelectors.excludeByKey(tuned));
        if (!mob.passive()) rest = rest.and(context -> !context.hasTag(TeemingSpawner.SHARE_CAPPED));
        BiomeModifications.addSpawn(rest, TeemingSpawns.category(mob), type, mob.weight(), mob.groupMin(), mob.groupMax());
    }

    @SuppressWarnings("unchecked")
    private static <R> void registerAll(DeferredRegister<R> register) {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getValue(register.getRegistryKey().identifier());
        if (registry == null) throw new IllegalStateException("No registry " + register.getRegistryKey().identifier());
        register.registerAll((key, value) -> Registry.register(registry, key, value));
    }
}
