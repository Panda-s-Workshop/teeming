package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.mob.end.Levimoth;
import com.panda.teeming.mob.nether.SlagheartGolem;
import com.panda.teeming.mob.overworld.Dunefin;
import com.panda.teeming.mob.overworld.Gloamhound;
import com.panda.teeming.mob.overworld.MireGulper;
import com.panda.teeming.mob.overworld.PuffcapShepherd;
import com.panda.teeming.mob.overworld.Rattlecoil;
import com.panda.teeming.mob.overworld.ShellguardCrab;
import com.panda.teeming.mob.overworld.Shiverwraith;
import com.panda.teeming.mob.overworld.Skreel;
import com.panda.teeming.mob.overworld.Snagwood;
import com.panda.teeming.mob.overworld.SnapmawBloom;
import com.panda.teeming.mob.overworld.Snowroller;
import com.panda.teeming.mob.overworld.Stormeel;
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
    public static final Supplier<EntityType<Snagwood>> SNAGWOOD = ENTITIES.registerEntityType("snagwood", Snagwood::new, MobCategory.MONSTER,
            b -> b.sized(0.9F, 2.6F).eyeHeight(2.05F).clientTrackingRange(10));
    public static final Supplier<EntityType<PuffcapShepherd>> PUFFCAP_SHEPHERD = ENTITIES.registerEntityType("puffcap_shepherd", PuffcapShepherd::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 1.1F).eyeHeight(0.6F).clientTrackingRange(10));
    public static final Supplier<EntityType<MireGulper>> MIRE_GULPER = ENTITIES.registerEntityType("mire_gulper", MireGulper::new, MobCategory.MONSTER,
            b -> b.sized(1.4F, 0.9F).eyeHeight(0.55F).clientTrackingRange(10));
    public static final Supplier<EntityType<Dunefin>> DUNEFIN = ENTITIES.registerEntityType("dunefin", Dunefin::new, MobCategory.MONSTER,
            b -> b.sized(1.2F, 0.8F).eyeHeight(0.4F).clientTrackingRange(10));
    public static final Supplier<EntityType<Rattlecoil>> RATTLECOIL = ENTITIES.registerEntityType("rattlecoil", Rattlecoil::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 0.4F).eyeHeight(0.3F).clientTrackingRange(10));
    public static final Supplier<EntityType<Snowroller>> SNOWROLLER = ENTITIES.registerEntityType("snowroller", Snowroller::new, MobCategory.MONSTER,
            b -> b.sized(1.0F, 1.0F).eyeHeight(0.6F).clientTrackingRange(10));
    public static final Supplier<EntityType<Shiverwraith>> SHIVERWRAITH = ENTITIES.registerEntityType("shiverwraith", Shiverwraith::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 1.9F).eyeHeight(1.65F).clientTrackingRange(10));
    public static final Supplier<EntityType<Gloamhound>> GLOAMHOUND = ENTITIES.registerEntityType("gloamhound", Gloamhound::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 0.85F).eyeHeight(0.7F).clientTrackingRange(10));
    public static final Supplier<EntityType<SnapmawBloom>> SNAPMAW_BLOOM = ENTITIES.registerEntityType("snapmaw_bloom", SnapmawBloom::new, MobCategory.MONSTER,
            b -> b.sized(0.9F, 1.3F).eyeHeight(1.0F).clientTrackingRange(10));
    public static final Supplier<EntityType<Stormeel>> STORMEEL = ENTITIES.registerEntityType("stormeel", Stormeel::new, MobCategory.MONSTER,
            b -> b.sized(0.6F, 0.5F).eyeHeight(0.3F).clientTrackingRange(10));
    public static final Supplier<EntityType<ShellguardCrab>> SHELLGUARD_CRAB = ENTITIES.registerEntityType("shellguard_crab", ShellguardCrab::new, MobCategory.CREATURE,
            b -> b.sized(0.9F, 0.6F).eyeHeight(0.45F).clientTrackingRange(10));
    public static final Supplier<EntityType<Skreel>> SKREEL = ENTITIES.registerEntityType("skreel", Skreel::new, MobCategory.MONSTER,
            b -> b.sized(0.9F, 0.7F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final Supplier<EntityType<SlagheartGolem>> SLAGHEART_GOLEM = ENTITIES.registerEntityType("slagheart_golem", SlagheartGolem::new, MobCategory.MONSTER,
            b -> b.sized(1.2F, 2.4F).eyeHeight(1.5F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<Levimoth>> LEVIMOTH = ENTITIES.registerEntityType("levimoth", Levimoth::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 0.6F).eyeHeight(0.35F).clientTrackingRange(10));

    public static final DeferredItem<Item> THORNHORN_SPAWN_EGG = ITEMS.registerItem("thornhorn_spawn_egg", Item::new, p -> p.spawnEgg(THORNHORN.get()));
    public static final DeferredItem<Item> SNAGWOOD_SPAWN_EGG = ITEMS.registerItem("snagwood_spawn_egg", Item::new, p -> p.spawnEgg(SNAGWOOD.get()));
    public static final DeferredItem<Item> PUFFCAP_SHEPHERD_SPAWN_EGG = ITEMS.registerItem("puffcap_shepherd_spawn_egg", Item::new, p -> p.spawnEgg(PUFFCAP_SHEPHERD.get()));
    public static final DeferredItem<Item> MIRE_GULPER_SPAWN_EGG = ITEMS.registerItem("mire_gulper_spawn_egg", Item::new, p -> p.spawnEgg(MIRE_GULPER.get()));
    public static final DeferredItem<Item> DUNEFIN_SPAWN_EGG = ITEMS.registerItem("dunefin_spawn_egg", Item::new, p -> p.spawnEgg(DUNEFIN.get()));
    public static final DeferredItem<Item> RATTLECOIL_SPAWN_EGG = ITEMS.registerItem("rattlecoil_spawn_egg", Item::new, p -> p.spawnEgg(RATTLECOIL.get()));
    public static final DeferredItem<Item> SNOWROLLER_SPAWN_EGG = ITEMS.registerItem("snowroller_spawn_egg", Item::new, p -> p.spawnEgg(SNOWROLLER.get()));
    public static final DeferredItem<Item> SHIVERWRAITH_SPAWN_EGG = ITEMS.registerItem("shiverwraith_spawn_egg", Item::new, p -> p.spawnEgg(SHIVERWRAITH.get()));
    public static final DeferredItem<Item> GLOAMHOUND_SPAWN_EGG = ITEMS.registerItem("gloamhound_spawn_egg", Item::new, p -> p.spawnEgg(GLOAMHOUND.get()));
    public static final DeferredItem<Item> SNAPMAW_BLOOM_SPAWN_EGG = ITEMS.registerItem("snapmaw_bloom_spawn_egg", Item::new, p -> p.spawnEgg(SNAPMAW_BLOOM.get()));
    public static final DeferredItem<Item> STORMEEL_SPAWN_EGG = ITEMS.registerItem("stormeel_spawn_egg", Item::new, p -> p.spawnEgg(STORMEEL.get()));
    public static final DeferredItem<Item> SHELLGUARD_CRAB_SPAWN_EGG = ITEMS.registerItem("shellguard_crab_spawn_egg", Item::new, p -> p.spawnEgg(SHELLGUARD_CRAB.get()));
    public static final DeferredItem<Item> SKREEL_SPAWN_EGG = ITEMS.registerItem("skreel_spawn_egg", Item::new, p -> p.spawnEgg(SKREEL.get()));
    public static final DeferredItem<Item> SLAGHEART_GOLEM_SPAWN_EGG = ITEMS.registerItem("slagheart_golem_spawn_egg", Item::new, p -> p.spawnEgg(SLAGHEART_GOLEM.get()));
    public static final DeferredItem<Item> LEVIMOTH_SPAWN_EGG = ITEMS.registerItem("levimoth_spawn_egg", Item::new, p -> p.spawnEgg(LEVIMOTH.get()));

    /** Every spawn egg, in roster order (the creative tab lists them). */
    public static final List<DeferredItem<Item>> EGGS = List.of(THORNHORN_SPAWN_EGG, SNAGWOOD_SPAWN_EGG, PUFFCAP_SHEPHERD_SPAWN_EGG, MIRE_GULPER_SPAWN_EGG, DUNEFIN_SPAWN_EGG, RATTLECOIL_SPAWN_EGG, SNOWROLLER_SPAWN_EGG, SHIVERWRAITH_SPAWN_EGG, GLOAMHOUND_SPAWN_EGG, SNAPMAW_BLOOM_SPAWN_EGG, STORMEEL_SPAWN_EGG, SHELLGUARD_CRAB_SPAWN_EGG, SKREEL_SPAWN_EGG, SLAGHEART_GOLEM_SPAWN_EGG, LEVIMOTH_SPAWN_EGG);

    private ModMobs() {}

    /** Every mob's attributes, for the loader's attribute registration. */
    public static void attributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> sink) {
        sink.accept(THORNHORN.get(), Thornhorn.createAttributes().build());
        sink.accept(SNAGWOOD.get(), Snagwood.createAttributes().build());
        sink.accept(PUFFCAP_SHEPHERD.get(), PuffcapShepherd.createAttributes().build());
        sink.accept(MIRE_GULPER.get(), MireGulper.createAttributes().build());
        sink.accept(DUNEFIN.get(), Dunefin.createAttributes().build());
        sink.accept(RATTLECOIL.get(), Rattlecoil.createAttributes().build());
        sink.accept(SNOWROLLER.get(), Snowroller.createAttributes().build());
        sink.accept(SHIVERWRAITH.get(), Shiverwraith.createAttributes().build());
        sink.accept(GLOAMHOUND.get(), Gloamhound.createAttributes().build());
        sink.accept(SNAPMAW_BLOOM.get(), SnapmawBloom.createAttributes().build());
        sink.accept(STORMEEL.get(), Stormeel.createAttributes().build());
        sink.accept(SHELLGUARD_CRAB.get(), ShellguardCrab.createAttributes().build());
        sink.accept(SKREEL.get(), Skreel.createAttributes().build());
        sink.accept(SLAGHEART_GOLEM.get(), SlagheartGolem.createAttributes().build());
        sink.accept(LEVIMOTH.get(), Levimoth.createAttributes().build());
    }
}
