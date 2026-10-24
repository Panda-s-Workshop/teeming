package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.platform.Services;
import com.panda.teeming.platform.registry.DeferredRegister;
import java.util.function.Supplier;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import com.panda.teeming.trophy.TrophyMob;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** The mod's creative tab: the Field Guide, every spawn egg in roster order, then a trophy of each mob. */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TeemingMod.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> Services.PLATFORM.tabBuilder()
            .title(Component.translatable("itemGroup.teeming"))
            .icon(() -> new ItemStack(ModItems.FIELD_GUIDE.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.FIELD_GUIDE.get());
                ModMobs.EGGS.forEach(egg -> output.accept(egg.get()));
                ModMaterials.ALL.forEach(mat -> output.accept(mat.get()));
                for (RosterStats.Entry mob : RosterStats.ALL) {
                    if (TeemingSpawns.type(mob).isEmpty()) continue;
                    ItemStack trophy = new ItemStack(ModItems.TROPHY.get());
                    trophy.set(ModComponents.TROPHY_MOB.get(), new TrophyMob(mob.id(), "base"));
                    output.accept(trophy);
                }
            })
            .build());

    private ModTabs() {}
}
