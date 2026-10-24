package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.platform.registry.DeferredHolder;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.trophy.TrophyBlockEntity;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The mod's block entity types. */
public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TeemingMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrophyBlockEntity>> TROPHY = TYPES.register("trophy",
            () -> new BlockEntityType<>(TrophyBlockEntity::new, Set.of(ModBlocks.TROPHY.get())));

    private ModBlockEntities() {}
}
