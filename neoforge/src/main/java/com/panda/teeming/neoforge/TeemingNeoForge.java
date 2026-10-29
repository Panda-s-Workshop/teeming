package com.panda.teeming.neoforge;

import com.panda.teeming.DevExit;
import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.command.TeemingCommand;
import com.panda.teeming.effect.NulledEffect;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

/** The NeoForge entry point: registers the common content and wires the common hooks to NeoForge's events. */
@Mod(TeemingMod.MODID)
public final class TeemingNeoForge {
    public TeemingNeoForge(IEventBus modBus, ModContainer container) {
        TeemingMod.init();
        modBus.addListener((RegisterEvent e) -> DeferredRegister.all().forEach(register -> registerInto(e, register)));
        container.registerConfig(ModConfig.Type.SYNCED, NeoForgeConfig.SPEC);
        NeoForgeAttachments.register(modBus);
        TeemingConfig.use(new NeoForgeConfig());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            NulledEffect.reset();
            DevExit.onServerStopped(e.getServer().isDedicatedServer());
        });
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent e) -> e.addListener(Looks.ID, new Looks()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> TeemingCommand.register(e.getDispatcher()));
        modBus.addListener((EntityAttributeCreationEvent e) -> ModMobs.attributes(e::put));
        // the eggs also go in vanilla's Spawn Eggs tab, where players and pack makers look for them
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) ModMobs.EGGS.forEach(egg -> e.accept(egg.get()));
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) TeemingMod.onLevelTick(level);
        });
        // a refused entity's join is cancelled before it is added; only an entity let in gets the join's side effects
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel().isClientSide()) return;
            if (!TeemingMod.allowJoin(e.getEntity())) e.setCanceled(true);
            else TeemingMod.onJoined(e.getEntity());
        });
        modBus.addListener((RegisterSpawnPlacementsEvent e) -> TeemingSpawns.placements(new TeemingSpawns.Placements() {
            @Override
            public <T extends Mob> void set(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap,
                                            SpawnPlacements.SpawnPredicate<T> predicate) {
                e.register(type, placement, heightmap, predicate, RegisterSpawnPlacementsEvent.Operation.REPLACE);
            }
        }));

        // the tests register only in a GameTest run, so a player's or a pack's game carries none of them
        if (GameTestHooks.isGametestEnabled()) com.panda.teeming.gametest.TeemingGameTests.register(modBus);
    }

    private static <R> void registerInto(RegisterEvent event, DeferredRegister<R> register) {
        event.register(register.getRegistryKey(), helper -> register.registerAll(helper::register));
    }
}
