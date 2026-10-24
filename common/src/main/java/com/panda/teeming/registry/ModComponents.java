package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.trophy.TrophyMob;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

/** The mod's item data components. */
public final class ModComponents {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TeemingMod.MODID);

    /** Which mob, in which look, a trophy shows. */
    public static final Supplier<DataComponentType<TrophyMob>> TROPHY_MOB = COMPONENTS.registerComponentType("trophy_mob",
            b -> b.persistent(TrophyMob.CODEC).networkSynchronized(TrophyMob.STREAM_CODEC));

    private ModComponents() {}
}
