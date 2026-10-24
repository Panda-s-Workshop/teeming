package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.item.MaterialItem;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.platform.registry.DeferredRegister;
import java.util.List;
import java.util.Map;

/** The signature materials mobs drop, each with at least one use. */
public final class ModMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeemingMod.MODID);

    public static final DeferredItem<MaterialItem> TOUGH_HIDE = ITEMS.registerItem("tough_hide", MaterialItem::new);
    public static final DeferredItem<MaterialItem> SLAG_GLAND = ITEMS.registerItem("slag_gland", MaterialItem::new);
    public static final DeferredItem<MaterialItem> CHORUS_SILK = ITEMS.registerItem("chorus_silk", MaterialItem::new);

    public static final List<DeferredItem<MaterialItem>> ALL = List.of(TOUGH_HIDE, SLAG_GLAND, CHORUS_SILK);

    /** Each material's uses, for the GameTests: "craft <count in> <result> <count out>" or "brew <potion>". */
    public static final Map<String, List<String>> USES = Map.of(
            "tough_hide", List.of("craft 1 minecraft:leather 2"),
            "slag_gland", List.of("craft 1 minecraft:magma_cream 2"),
            "chorus_silk", List.of("brew minecraft:slow_falling"));

    private ModMaterials() {}
}
