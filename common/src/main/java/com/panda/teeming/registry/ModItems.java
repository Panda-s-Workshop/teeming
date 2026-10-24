package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.item.FieldGuideItem;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.trophy.TrophyItem;
import net.minecraft.world.item.Item;

/** The mod's hand-written items (spawn eggs are generated in {@link ModMobs}). */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeemingMod.MODID);

    public static final DeferredItem<Item> FIELD_GUIDE = ITEMS.registerItem("field_guide", FieldGuideItem::new, p -> p.stacksTo(1));
    /** One item for every trophy; the {@code teeming:trophy_mob} component says which mob. */
    public static final DeferredItem<TrophyItem> TROPHY = ITEMS.registerItem("trophy", p -> new TrophyItem(ModBlocks.TROPHY.get(), p),
            p -> p.useBlockDescriptionPrefix().stacksTo(16));

    private ModItems() {}
}
