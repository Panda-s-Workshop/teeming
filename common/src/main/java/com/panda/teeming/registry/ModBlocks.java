package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.platform.registry.DeferredBlock;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.trophy.TrophyBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** The mod's blocks. */
public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TeemingMod.MODID);

    public static final DeferredBlock<TrophyBlock> TROPHY = BLOCKS.registerBlock("trophy", TrophyBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.0F).sound(SoundType.STONE).noOcclusion());

    private ModBlocks() {}
}
