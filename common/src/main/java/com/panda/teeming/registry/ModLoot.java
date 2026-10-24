package com.panda.teeming.registry;

import com.mojang.serialization.MapCodec;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.platform.registry.DeferredHolder;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.trophy.TrophyLookFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

/** The mod's loot functions. */
public final class ModLoot {
    private static final DeferredRegister<MapCodec<? extends LootItemFunction>> FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, TeemingMod.MODID);

    public static final DeferredHolder<MapCodec<? extends LootItemFunction>, MapCodec<TrophyLookFunction>> TROPHY_LOOK = FUNCTIONS.register("trophy_look",
            () -> TrophyLookFunction.MAP_CODEC);

    private ModLoot() {}
}
