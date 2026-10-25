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
    public static final DeferredItem<MaterialItem> VENOM_FANG = ITEMS.registerItem("venom_fang", MaterialItem::new);
    public static final DeferredItem<MaterialItem> HEARTWOOD = ITEMS.registerItem("heartwood", MaterialItem::new);
    public static final DeferredItem<MaterialItem> STORM_COIL = ITEMS.registerItem("storm_coil", MaterialItem::new);
    public static final DeferredItem<MaterialItem> SAND_SCALE = ITEMS.registerItem("sand_scale", MaterialItem::new);
    public static final DeferredItem<MaterialItem> SLAG_GLAND = ITEMS.registerItem("slag_gland", MaterialItem::new);
    public static final DeferredItem<MaterialItem> CHORUS_SILK = ITEMS.registerItem("chorus_silk", MaterialItem::new);

    public static final List<DeferredItem<MaterialItem>> ALL = List.of(TOUGH_HIDE, VENOM_FANG, HEARTWOOD, STORM_COIL, SAND_SCALE, SLAG_GLAND, CHORUS_SILK);

    /** Each material's uses, for the GameTests: "craft <in> <result> <out> [item*count ...]", "brew <potion>" or "tipped <potion>". */
    public static final Map<String, List<String>> USES = Map.of(
            "tough_hide", List.of("craft 1 minecraft:leather 2"),
            "venom_fang", List.of("brew minecraft:poison", "tipped minecraft:poison"),
            "heartwood", List.of("craft 1 minecraft:stick 4", "craft 2 minecraft:bone_meal 3"),
            "storm_coil", List.of("brew minecraft:water_breathing"),
            "sand_scale", List.of("craft 4 minecraft:turtle_scute 1"),
            "slag_gland", List.of("craft 1 minecraft:magma_cream 2"),
            "chorus_silk", List.of("brew minecraft:slow_falling"));

    private ModMaterials() {}
}
