package com.panda.teeming;

import com.mojang.logging.LogUtils;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.mob.nether.PhantasmLantern;
import com.panda.teeming.mob.overworld.HollowMourner;
import com.panda.teeming.registry.ModBlockEntities;
import com.panda.teeming.registry.ModBlocks;
import com.panda.teeming.registry.ModComponents;
import com.panda.teeming.registry.ModItems;
import com.panda.teeming.registry.ModLoot;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.registry.ModSounds;
import com.panda.teeming.registry.ModTabs;
import com.panda.teeming.spawn.TeemingSpawner;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/** The mod's common side. Each loader module has its own entry point, which calls {@link #init()} first. */
public final class TeemingMod {
    public static final String MODID = "teeming";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The classes whose static DeferredRegisters hold the mod's content. */
    private static final List<Class<?>> CONTENT = List.of(ModComponents.class, ModBlocks.class, ModBlockEntities.class, ModLoot.class, ModItems.class, ModMaterials.class,
            ModMobs.class, ModSounds.class, ModTabs.class);

    private TeemingMod() {}

    /** Loads the content classes so every registration is queued before the loader registers them. */
    public static void init() {
        for (Class<?> c : CONTENT) {
            try {
                Class.forName(c.getName(), true, c.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        LOGGER.info("Teeming booting on {}", com.panda.teeming.platform.Services.PLATFORM.loaderName());
    }

    /** Server, after each level tick (both loaders): the Teeming spawner and the Field Guide's sight checks. */
    public static void onLevelTick(ServerLevel level) {
        TeemingSpawner.tick(level);
        FieldGuide.tick(level);
    }

    /**
     * Server, whenever an entity joins a level, spawned or loaded from disk (both loaders). False when it must not
     * stay: a Phantasm Lantern's illusion never outlives the lantern that made it, so one loaded from disk is refused.
     */
    public static boolean onEntityJoin(Entity entity) {
        if (PhantasmLantern.Illusions.isIllusion(entity)) return false;   // marked only after its first join
        HollowMourner.onJoin(entity);
        return true;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
