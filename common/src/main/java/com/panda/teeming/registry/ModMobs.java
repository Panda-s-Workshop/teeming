package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.mob.end.Levimoth;
import com.panda.teeming.mob.nether.SlagheartGolem;
import com.panda.teeming.mob.overworld.Thornhorn;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;

/** Entity types, spawn eggs and attributes of every mob. */
public final class ModMobs {
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(TeemingMod.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeemingMod.MODID);

    public static final Supplier<EntityType<Thornhorn>> THORNHORN = ENTITIES.registerEntityType("thornhorn", Thornhorn::new, MobCategory.CREATURE,
            b -> b.sized(1.3F, 1.4F).eyeHeight(1.1F).clientTrackingRange(10));
    public static final Supplier<EntityType<SlagheartGolem>> SLAGHEART_GOLEM = ENTITIES.registerEntityType("slagheart_golem", SlagheartGolem::new, MobCategory.MONSTER,
            b -> b.sized(1.2F, 2.4F).eyeHeight(1.5F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<Levimoth>> LEVIMOTH = ENTITIES.registerEntityType("levimoth", Levimoth::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 0.6F).eyeHeight(0.35F).clientTrackingRange(10));

    public static final DeferredItem<Item> THORNHORN_SPAWN_EGG = ITEMS.registerItem("thornhorn_spawn_egg", Item::new, p -> p.spawnEgg(THORNHORN.get()));
    public static final DeferredItem<Item> SLAGHEART_GOLEM_SPAWN_EGG = ITEMS.registerItem("slagheart_golem_spawn_egg", Item::new, p -> p.spawnEgg(SLAGHEART_GOLEM.get()));
    public static final DeferredItem<Item> LEVIMOTH_SPAWN_EGG = ITEMS.registerItem("levimoth_spawn_egg", Item::new, p -> p.spawnEgg(LEVIMOTH.get()));

    /** Every spawn egg, in roster order (the creative tab lists them). */
    public static final List<DeferredItem<Item>> EGGS = List.of(THORNHORN_SPAWN_EGG, SLAGHEART_GOLEM_SPAWN_EGG, LEVIMOTH_SPAWN_EGG);

    private ModMobs() {}

    /** Every mob's attributes, for the loader's attribute registration. */
    public static void attributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> sink) {
        sink.accept(THORNHORN.get(), Thornhorn.createAttributes().build());
        sink.accept(SLAGHEART_GOLEM.get(), SlagheartGolem.createAttributes().build());
        sink.accept(LEVIMOTH.get(), Levimoth.createAttributes().build());
    }
}
