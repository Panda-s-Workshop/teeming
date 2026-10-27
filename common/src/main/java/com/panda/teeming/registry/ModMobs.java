package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.platform.registry.DeferredRegister;
import com.panda.teeming.mob.end.AstralAugur;
import com.panda.teeming.mob.end.Brinkram;
import com.panda.teeming.mob.end.ChorusGrazer;
import com.panda.teeming.mob.end.ChorusHusk;
import com.panda.teeming.mob.end.Chronomite;
import com.panda.teeming.mob.end.EchoFencer;
import com.panda.teeming.mob.end.FacetDoppel;
import com.panda.teeming.mob.end.Gatecaster;
import com.panda.teeming.mob.end.Glimmerless;
import com.panda.teeming.mob.end.Hollowmind;
import com.panda.teeming.mob.end.Levimoth;
import com.panda.teeming.mob.end.LiftstoneMonolith;
import com.panda.teeming.mob.end.MendingCrystalling;
import com.panda.teeming.mob.end.Nullcantor;
import com.panda.teeming.mob.end.Pearlgulp;
import com.panda.teeming.mob.end.Phasewalker;
import com.panda.teeming.mob.end.Riftjackal;
import com.panda.teeming.mob.end.ShardlingColossus;
import com.panda.teeming.mob.end.Swellshade;
import com.panda.teeming.mob.end.WarpseedBloom;
import com.panda.teeming.mob.nether.AshJackal;
import com.panda.teeming.mob.nether.Ashharrier;
import com.panda.teeming.mob.nether.Aurovore;
import com.panda.teeming.mob.nether.BasaltBehemoth;
import com.panda.teeming.mob.nether.BlackstoneScuttler;
import com.panda.teeming.mob.nether.CindermothSwarm;
import com.panda.teeming.mob.nether.Flamewright;
import com.panda.teeming.mob.nether.FortressJailer;
import com.panda.teeming.mob.nether.Gleamtrap;
import com.panda.teeming.mob.nether.Gloomweaver;
import com.panda.teeming.mob.nether.KeeningShade;
import com.panda.teeming.mob.nether.Lavaleaper;
import com.panda.teeming.mob.nether.MagmaTortoise;
import com.panda.teeming.mob.nether.OssuaryWyrm;
import com.panda.teeming.mob.nether.PhantasmLantern;
import com.panda.teeming.mob.nether.Prismback;
import com.panda.teeming.mob.nether.PyreHerald;
import com.panda.teeming.mob.nether.Quakehopper;
import com.panda.teeming.mob.nether.ScarletMantis;
import com.panda.teeming.mob.nether.ShredjawBeetle;
import com.panda.teeming.mob.nether.Siphonjelly;
import com.panda.teeming.mob.nether.SlagheartGolem;
import com.panda.teeming.mob.nether.Slagspitter;
import com.panda.teeming.mob.nether.SootManta;
import com.panda.teeming.mob.nether.SoulfireSeer;
import com.panda.teeming.mob.nether.SoulwardRevenant;
import com.panda.teeming.mob.nether.SporeCannon;
import com.panda.teeming.mob.nether.Veinleech;
import com.panda.teeming.mob.nether.WeeplingBroodmother;
import com.panda.teeming.mob.nether.WoolcapGrazer;
import com.panda.teeming.mob.overworld.Dripfang;
import com.panda.teeming.mob.overworld.Dunefin;
import com.panda.teeming.mob.overworld.Dustwhirl;
import com.panda.teeming.mob.overworld.Gloamhound;
import com.panda.teeming.mob.overworld.HollowMourner;
import com.panda.teeming.mob.overworld.Hushwing;
import com.panda.teeming.mob.overworld.MireGulper;
import com.panda.teeming.mob.overworld.MyceliteShambler;
import com.panda.teeming.mob.overworld.Petalkin;
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
import com.panda.teeming.mob.overworld.VinedropStrangler;
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
    public static final Supplier<EntityType<VinedropStrangler>> VINEDROP_STRANGLER = ENTITIES.registerEntityType("vinedrop_strangler", VinedropStrangler::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 1.2F).eyeHeight(0.95F).clientTrackingRange(10));
    public static final Supplier<EntityType<SnapmawBloom>> SNAPMAW_BLOOM = ENTITIES.registerEntityType("snapmaw_bloom", SnapmawBloom::new, MobCategory.MONSTER,
            b -> b.sized(0.9F, 1.3F).eyeHeight(1.0F).clientTrackingRange(10));
    public static final Supplier<EntityType<Stormeel>> STORMEEL = ENTITIES.registerEntityType("stormeel", Stormeel::new, MobCategory.MONSTER,
            b -> b.sized(0.6F, 0.5F).eyeHeight(0.3F).clientTrackingRange(10));
    public static final Supplier<EntityType<ShellguardCrab>> SHELLGUARD_CRAB = ENTITIES.registerEntityType("shellguard_crab", ShellguardCrab::new, MobCategory.CREATURE,
            b -> b.sized(0.9F, 0.6F).eyeHeight(0.45F).clientTrackingRange(10));
    public static final Supplier<EntityType<Dripfang>> DRIPFANG = ENTITIES.registerEntityType("dripfang", Dripfang::new, MobCategory.MONSTER,
            b -> b.sized(0.6F, 1.4F).eyeHeight(1.25F).clientTrackingRange(10));
    public static final Supplier<EntityType<Hushwing>> HUSHWING = ENTITIES.registerEntityType("hushwing", Hushwing::new, MobCategory.MONSTER,
            b -> b.sized(0.5F, 0.5F).eyeHeight(0.3F).clientTrackingRange(10));
    public static final Supplier<EntityType<MyceliteShambler>> MYCELITE_SHAMBLER = ENTITIES.registerEntityType("mycelite_shambler", MyceliteShambler::new, MobCategory.MONSTER,
            b -> b.sized(1.0F, 1.8F).eyeHeight(1.45F).clientTrackingRange(10));
    public static final Supplier<EntityType<Petalkin>> PETALKIN = ENTITIES.registerEntityType("petalkin", Petalkin::new, MobCategory.CREATURE,
            b -> b.sized(0.5F, 0.9F).eyeHeight(0.62F).clientTrackingRange(10));
    public static final Supplier<EntityType<HollowMourner>> HOLLOW_MOURNER = ENTITIES.registerEntityType("hollow_mourner", HollowMourner::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 2.0F).eyeHeight(1.7F).clientTrackingRange(10));
    public static final Supplier<EntityType<Dustwhirl>> DUSTWHIRL = ENTITIES.registerEntityType("dustwhirl", Dustwhirl::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 1.6F).eyeHeight(1.0F).clientTrackingRange(10));
    public static final Supplier<EntityType<Skreel>> SKREEL = ENTITIES.registerEntityType("skreel", Skreel::new, MobCategory.MONSTER,
            b -> b.sized(0.9F, 0.7F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final Supplier<EntityType<Prismback>> PRISMBACK = ENTITIES.registerEntityType("prismback", Prismback::new, MobCategory.MONSTER,
            b -> b.sized(1.0F, 0.7F).eyeHeight(0.45F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<Slagspitter>> SLAGSPITTER = ENTITIES.registerEntityType("slagspitter", Slagspitter::new, MobCategory.MONSTER,
            b -> b.sized(0.9F, 0.8F).eyeHeight(0.6F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<AshJackal>> ASH_JACKAL = ENTITIES.registerEntityType("ash_jackal", AshJackal::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 0.8F).eyeHeight(0.62F).clientTrackingRange(10));
    public static final Supplier<EntityType<SlagheartGolem>> SLAGHEART_GOLEM = ENTITIES.registerEntityType("slagheart_golem", SlagheartGolem::new, MobCategory.MONSTER,
            b -> b.sized(1.2F, 2.4F).eyeHeight(1.5F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<SootManta>> SOOT_MANTA = ENTITIES.registerEntityType("soot_manta", SootManta::new, MobCategory.MONSTER,
            b -> b.sized(2.0F, 0.6F).eyeHeight(0.3F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<MagmaTortoise>> MAGMA_TORTOISE = ENTITIES.registerEntityType("magma_tortoise", MagmaTortoise::new, MobCategory.CREATURE,
            b -> b.sized(2.2F, 1.0F).eyeHeight(0.8F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<Veinleech>> VEINLEECH = ENTITIES.registerEntityType("veinleech", Veinleech::new, MobCategory.MONSTER,
            b -> b.sized(0.6F, 0.5F).eyeHeight(0.35F).clientTrackingRange(10));
    public static final Supplier<EntityType<ShredjawBeetle>> SHREDJAW_BEETLE = ENTITIES.registerEntityType("shredjaw_beetle", ShredjawBeetle::new, MobCategory.MONSTER,
            b -> b.sized(1.1F, 0.8F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final Supplier<EntityType<WeeplingBroodmother>> WEEPLING_BROODMOTHER = ENTITIES.registerEntityType("weepling_broodmother", WeeplingBroodmother::new, MobCategory.MONSTER,
            b -> b.sized(1.4F, 1.2F).eyeHeight(0.7F).clientTrackingRange(10));
    public static final Supplier<EntityType<Gleamtrap>> GLEAMTRAP = ENTITIES.registerEntityType("gleamtrap", Gleamtrap::new, MobCategory.MONSTER,
            b -> b.sized(1.0F, 1.0F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final Supplier<EntityType<ScarletMantis>> SCARLET_MANTIS = ENTITIES.registerEntityType("scarlet_mantis", ScarletMantis::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 1.9F).eyeHeight(1.7F).clientTrackingRange(10));
    public static final Supplier<EntityType<PhantasmLantern>> PHANTASM_LANTERN = ENTITIES.registerEntityType("phantasm_lantern", PhantasmLantern::new, MobCategory.MONSTER,
            b -> b.sized(0.6F, 0.9F).eyeHeight(0.75F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<WoolcapGrazer>> WOOLCAP_GRAZER = ENTITIES.registerEntityType("woolcap_grazer", WoolcapGrazer::new, MobCategory.CREATURE,
            b -> b.sized(1.0F, 1.2F).eyeHeight(0.95F).clientTrackingRange(10));
    public static final Supplier<EntityType<Siphonjelly>> SIPHONJELLY = ENTITIES.registerEntityType("siphonjelly", Siphonjelly::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 1.0F).eyeHeight(0.6F).clientTrackingRange(10));
    public static final Supplier<EntityType<SporeCannon>> SPORE_CANNON = ENTITIES.registerEntityType("spore_cannon", SporeCannon::new, MobCategory.MONSTER,
            b -> b.sized(1.0F, 1.6F).eyeHeight(1.25F).clientTrackingRange(10));
    public static final Supplier<EntityType<Gloomweaver>> GLOOMWEAVER = ENTITIES.registerEntityType("gloomweaver", Gloomweaver::new, MobCategory.MONSTER,
            b -> b.sized(1.3F, 0.9F).eyeHeight(0.55F).clientTrackingRange(10));
    public static final Supplier<EntityType<OssuaryWyrm>> OSSUARY_WYRM = ENTITIES.registerEntityType("ossuary_wyrm", OssuaryWyrm::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 0.8F).eyeHeight(0.4F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<KeeningShade>> KEENING_SHADE = ENTITIES.registerEntityType("keening_shade", KeeningShade::new, MobCategory.MONSTER,
            b -> b.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<SoulfireSeer>> SOULFIRE_SEER = ENTITIES.registerEntityType("soulfire_seer", SoulfireSeer::new, MobCategory.MONSTER,
            b -> b.sized(0.6F, 2.0F).eyeHeight(1.72F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<CindermothSwarm>> CINDERMOTH_SWARM = ENTITIES.registerEntityType("cindermoth_swarm", CindermothSwarm::new, MobCategory.MONSTER,
            b -> b.sized(1.0F, 1.0F).eyeHeight(0.5F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<SoulwardRevenant>> SOULWARD_REVENANT = ENTITIES.registerEntityType("soulward_revenant", SoulwardRevenant::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 2.2F).eyeHeight(1.85F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<BasaltBehemoth>> BASALT_BEHEMOTH = ENTITIES.registerEntityType("basalt_behemoth", BasaltBehemoth::new, MobCategory.MONSTER,
            b -> b.sized(2.4F, 3.2F).eyeHeight(2.6F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<Quakehopper>> QUAKEHOPPER = ENTITIES.registerEntityType("quakehopper", Quakehopper::new, MobCategory.MONSTER,
            b -> b.sized(1.0F, 1.0F).eyeHeight(0.7F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<Ashharrier>> ASHHARRIER = ENTITIES.registerEntityType("ashharrier", Ashharrier::new, MobCategory.MONSTER,
            b -> b.sized(1.4F, 0.8F).eyeHeight(0.55F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<Lavaleaper>> LAVALEAPER = ENTITIES.registerEntityType("lavaleaper", Lavaleaper::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 0.6F).eyeHeight(0.35F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<BlackstoneScuttler>> BLACKSTONE_SCUTTLER = ENTITIES.registerEntityType("blackstone_scuttler", BlackstoneScuttler::new, MobCategory.MONSTER,
            b -> b.sized(1.1F, 0.7F).eyeHeight(0.5F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<PyreHerald>> PYRE_HERALD = ENTITIES.registerEntityType("pyre_herald", PyreHerald::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 1.2F).eyeHeight(0.9F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<FortressJailer>> FORTRESS_JAILER = ENTITIES.registerEntityType("fortress_jailer", FortressJailer::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 2.2F).eyeHeight(1.95F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<Flamewright>> FLAMEWRIGHT = ENTITIES.registerEntityType("flamewright", Flamewright::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 2.0F).eyeHeight(1.7F).clientTrackingRange(10).fireImmune());
    public static final Supplier<EntityType<Aurovore>> AUROVORE = ENTITIES.registerEntityType("aurovore", Aurovore::new, MobCategory.MONSTER,
            b -> b.sized(0.9F, 0.7F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final Supplier<EntityType<MendingCrystalling>> MENDING_CRYSTALLING = ENTITIES.registerEntityType("mending_crystalling", MendingCrystalling::new, MobCategory.MONSTER,
            b -> b.sized(0.5F, 0.8F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final Supplier<EntityType<LiftstoneMonolith>> LIFTSTONE_MONOLITH = ENTITIES.registerEntityType("liftstone_monolith", LiftstoneMonolith::new, MobCategory.MONSTER,
            b -> b.sized(1.6F, 4.0F).eyeHeight(3.3F).clientTrackingRange(10));
    public static final Supplier<EntityType<AstralAugur>> ASTRAL_AUGUR = ENTITIES.registerEntityType("astral_augur", AstralAugur::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 2.1F).eyeHeight(1.85F).clientTrackingRange(10));
    public static final Supplier<EntityType<Nullcantor>> NULLCANTOR = ENTITIES.registerEntityType("nullcantor", Nullcantor::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 2.0F).eyeHeight(1.7F).clientTrackingRange(10));
    public static final Supplier<EntityType<ChorusGrazer>> CHORUS_GRAZER = ENTITIES.registerEntityType("chorus_grazer", ChorusGrazer::new, MobCategory.CREATURE,
            b -> b.sized(1.1F, 1.3F).eyeHeight(1.0F).clientTrackingRange(10));
    public static final Supplier<EntityType<Levimoth>> LEVIMOTH = ENTITIES.registerEntityType("levimoth", Levimoth::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 0.6F).eyeHeight(0.35F).clientTrackingRange(10));
    public static final Supplier<EntityType<WarpseedBloom>> WARPSEED_BLOOM = ENTITIES.registerEntityType("warpseed_bloom", WarpseedBloom::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 1.6F).eyeHeight(1.15F).clientTrackingRange(10));
    public static final Supplier<EntityType<Glimmerless>> GLIMMERLESS = ENTITIES.registerEntityType("glimmerless", Glimmerless::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 1.9F).eyeHeight(1.55F).clientTrackingRange(10));
    public static final Supplier<EntityType<Riftjackal>> RIFTJACKAL = ENTITIES.registerEntityType("riftjackal", Riftjackal::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 0.9F).eyeHeight(0.7F).clientTrackingRange(10));
    public static final Supplier<EntityType<ChorusHusk>> CHORUS_HUSK = ENTITIES.registerEntityType("chorus_husk", ChorusHusk::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 2.0F).eyeHeight(1.55F).clientTrackingRange(10));
    public static final Supplier<EntityType<Gatecaster>> GATECASTER = ENTITIES.registerEntityType("gatecaster", Gatecaster::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 2.0F).eyeHeight(1.7F).clientTrackingRange(10));
    public static final Supplier<EntityType<Hollowmind>> HOLLOWMIND = ENTITIES.registerEntityType("hollowmind", Hollowmind::new, MobCategory.MONSTER,
            b -> b.sized(0.5F, 0.5F).eyeHeight(0.35F).clientTrackingRange(10));
    public static final Supplier<EntityType<Pearlgulp>> PEARLGULP = ENTITIES.registerEntityType("pearlgulp", Pearlgulp::new, MobCategory.MONSTER,
            b -> b.sized(0.9F, 0.8F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final Supplier<EntityType<Brinkram>> BRINKRAM = ENTITIES.registerEntityType("brinkram", Brinkram::new, MobCategory.MONSTER,
            b -> b.sized(1.2F, 1.4F).eyeHeight(1.15F).clientTrackingRange(10));
    public static final Supplier<EntityType<FacetDoppel>> FACET_DOPPEL = ENTITIES.registerEntityType("facet_doppel", FacetDoppel::new, MobCategory.MONSTER,
            b -> b.sized(0.6F, 1.95F).eyeHeight(1.7F).clientTrackingRange(10));
    public static final Supplier<EntityType<ShardlingColossus>> SHARDLING_COLOSSUS = ENTITIES.registerEntityType("shardling_colossus", ShardlingColossus::new, MobCategory.MONSTER,
            b -> b.sized(1.4F, 2.6F).eyeHeight(2.1F).clientTrackingRange(10));
    public static final Supplier<EntityType<Phasewalker>> PHASEWALKER = ENTITIES.registerEntityType("phasewalker", Phasewalker::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 2.0F).eyeHeight(1.75F).clientTrackingRange(10));
    public static final Supplier<EntityType<EchoFencer>> ECHO_FENCER = ENTITIES.registerEntityType("echo_fencer", EchoFencer::new, MobCategory.MONSTER,
            b -> b.sized(0.7F, 2.0F).eyeHeight(1.75F).clientTrackingRange(10));
    public static final Supplier<EntityType<Swellshade>> SWELLSHADE = ENTITIES.registerEntityType("swellshade", Swellshade::new, MobCategory.MONSTER,
            b -> b.sized(0.8F, 1.6F).eyeHeight(1.4F).clientTrackingRange(10));
    public static final Supplier<EntityType<Chronomite>> CHRONOMITE = ENTITIES.registerEntityType("chronomite", Chronomite::new, MobCategory.MONSTER,
            b -> b.sized(0.9F, 0.7F).eyeHeight(0.45F).clientTrackingRange(10));

    public static final DeferredItem<Item> THORNHORN_SPAWN_EGG = ITEMS.registerItem("thornhorn_spawn_egg", Item::new, p -> p.spawnEgg(THORNHORN.get()));
    public static final DeferredItem<Item> SNAGWOOD_SPAWN_EGG = ITEMS.registerItem("snagwood_spawn_egg", Item::new, p -> p.spawnEgg(SNAGWOOD.get()));
    public static final DeferredItem<Item> PUFFCAP_SHEPHERD_SPAWN_EGG = ITEMS.registerItem("puffcap_shepherd_spawn_egg", Item::new, p -> p.spawnEgg(PUFFCAP_SHEPHERD.get()));
    public static final DeferredItem<Item> MIRE_GULPER_SPAWN_EGG = ITEMS.registerItem("mire_gulper_spawn_egg", Item::new, p -> p.spawnEgg(MIRE_GULPER.get()));
    public static final DeferredItem<Item> DUNEFIN_SPAWN_EGG = ITEMS.registerItem("dunefin_spawn_egg", Item::new, p -> p.spawnEgg(DUNEFIN.get()));
    public static final DeferredItem<Item> RATTLECOIL_SPAWN_EGG = ITEMS.registerItem("rattlecoil_spawn_egg", Item::new, p -> p.spawnEgg(RATTLECOIL.get()));
    public static final DeferredItem<Item> SNOWROLLER_SPAWN_EGG = ITEMS.registerItem("snowroller_spawn_egg", Item::new, p -> p.spawnEgg(SNOWROLLER.get()));
    public static final DeferredItem<Item> SHIVERWRAITH_SPAWN_EGG = ITEMS.registerItem("shiverwraith_spawn_egg", Item::new, p -> p.spawnEgg(SHIVERWRAITH.get()));
    public static final DeferredItem<Item> GLOAMHOUND_SPAWN_EGG = ITEMS.registerItem("gloamhound_spawn_egg", Item::new, p -> p.spawnEgg(GLOAMHOUND.get()));
    public static final DeferredItem<Item> VINEDROP_STRANGLER_SPAWN_EGG = ITEMS.registerItem("vinedrop_strangler_spawn_egg", Item::new, p -> p.spawnEgg(VINEDROP_STRANGLER.get()));
    public static final DeferredItem<Item> SNAPMAW_BLOOM_SPAWN_EGG = ITEMS.registerItem("snapmaw_bloom_spawn_egg", Item::new, p -> p.spawnEgg(SNAPMAW_BLOOM.get()));
    public static final DeferredItem<Item> STORMEEL_SPAWN_EGG = ITEMS.registerItem("stormeel_spawn_egg", Item::new, p -> p.spawnEgg(STORMEEL.get()));
    public static final DeferredItem<Item> SHELLGUARD_CRAB_SPAWN_EGG = ITEMS.registerItem("shellguard_crab_spawn_egg", Item::new, p -> p.spawnEgg(SHELLGUARD_CRAB.get()));
    public static final DeferredItem<Item> DRIPFANG_SPAWN_EGG = ITEMS.registerItem("dripfang_spawn_egg", Item::new, p -> p.spawnEgg(DRIPFANG.get()));
    public static final DeferredItem<Item> HUSHWING_SPAWN_EGG = ITEMS.registerItem("hushwing_spawn_egg", Item::new, p -> p.spawnEgg(HUSHWING.get()));
    public static final DeferredItem<Item> MYCELITE_SHAMBLER_SPAWN_EGG = ITEMS.registerItem("mycelite_shambler_spawn_egg", Item::new, p -> p.spawnEgg(MYCELITE_SHAMBLER.get()));
    public static final DeferredItem<Item> PETALKIN_SPAWN_EGG = ITEMS.registerItem("petalkin_spawn_egg", Item::new, p -> p.spawnEgg(PETALKIN.get()));
    public static final DeferredItem<Item> HOLLOW_MOURNER_SPAWN_EGG = ITEMS.registerItem("hollow_mourner_spawn_egg", Item::new, p -> p.spawnEgg(HOLLOW_MOURNER.get()));
    public static final DeferredItem<Item> DUSTWHIRL_SPAWN_EGG = ITEMS.registerItem("dustwhirl_spawn_egg", Item::new, p -> p.spawnEgg(DUSTWHIRL.get()));
    public static final DeferredItem<Item> SKREEL_SPAWN_EGG = ITEMS.registerItem("skreel_spawn_egg", Item::new, p -> p.spawnEgg(SKREEL.get()));
    public static final DeferredItem<Item> PRISMBACK_SPAWN_EGG = ITEMS.registerItem("prismback_spawn_egg", Item::new, p -> p.spawnEgg(PRISMBACK.get()));
    public static final DeferredItem<Item> SLAGSPITTER_SPAWN_EGG = ITEMS.registerItem("slagspitter_spawn_egg", Item::new, p -> p.spawnEgg(SLAGSPITTER.get()));
    public static final DeferredItem<Item> ASH_JACKAL_SPAWN_EGG = ITEMS.registerItem("ash_jackal_spawn_egg", Item::new, p -> p.spawnEgg(ASH_JACKAL.get()));
    public static final DeferredItem<Item> SLAGHEART_GOLEM_SPAWN_EGG = ITEMS.registerItem("slagheart_golem_spawn_egg", Item::new, p -> p.spawnEgg(SLAGHEART_GOLEM.get()));
    public static final DeferredItem<Item> SOOT_MANTA_SPAWN_EGG = ITEMS.registerItem("soot_manta_spawn_egg", Item::new, p -> p.spawnEgg(SOOT_MANTA.get()));
    public static final DeferredItem<Item> MAGMA_TORTOISE_SPAWN_EGG = ITEMS.registerItem("magma_tortoise_spawn_egg", Item::new, p -> p.spawnEgg(MAGMA_TORTOISE.get()));
    public static final DeferredItem<Item> VEINLEECH_SPAWN_EGG = ITEMS.registerItem("veinleech_spawn_egg", Item::new, p -> p.spawnEgg(VEINLEECH.get()));
    public static final DeferredItem<Item> SHREDJAW_BEETLE_SPAWN_EGG = ITEMS.registerItem("shredjaw_beetle_spawn_egg", Item::new, p -> p.spawnEgg(SHREDJAW_BEETLE.get()));
    public static final DeferredItem<Item> WEEPLING_BROODMOTHER_SPAWN_EGG = ITEMS.registerItem("weepling_broodmother_spawn_egg", Item::new, p -> p.spawnEgg(WEEPLING_BROODMOTHER.get()));
    public static final DeferredItem<Item> GLEAMTRAP_SPAWN_EGG = ITEMS.registerItem("gleamtrap_spawn_egg", Item::new, p -> p.spawnEgg(GLEAMTRAP.get()));
    public static final DeferredItem<Item> SCARLET_MANTIS_SPAWN_EGG = ITEMS.registerItem("scarlet_mantis_spawn_egg", Item::new, p -> p.spawnEgg(SCARLET_MANTIS.get()));
    public static final DeferredItem<Item> PHANTASM_LANTERN_SPAWN_EGG = ITEMS.registerItem("phantasm_lantern_spawn_egg", Item::new, p -> p.spawnEgg(PHANTASM_LANTERN.get()));
    public static final DeferredItem<Item> WOOLCAP_GRAZER_SPAWN_EGG = ITEMS.registerItem("woolcap_grazer_spawn_egg", Item::new, p -> p.spawnEgg(WOOLCAP_GRAZER.get()));
    public static final DeferredItem<Item> SIPHONJELLY_SPAWN_EGG = ITEMS.registerItem("siphonjelly_spawn_egg", Item::new, p -> p.spawnEgg(SIPHONJELLY.get()));
    public static final DeferredItem<Item> SPORE_CANNON_SPAWN_EGG = ITEMS.registerItem("spore_cannon_spawn_egg", Item::new, p -> p.spawnEgg(SPORE_CANNON.get()));
    public static final DeferredItem<Item> GLOOMWEAVER_SPAWN_EGG = ITEMS.registerItem("gloomweaver_spawn_egg", Item::new, p -> p.spawnEgg(GLOOMWEAVER.get()));
    public static final DeferredItem<Item> OSSUARY_WYRM_SPAWN_EGG = ITEMS.registerItem("ossuary_wyrm_spawn_egg", Item::new, p -> p.spawnEgg(OSSUARY_WYRM.get()));
    public static final DeferredItem<Item> KEENING_SHADE_SPAWN_EGG = ITEMS.registerItem("keening_shade_spawn_egg", Item::new, p -> p.spawnEgg(KEENING_SHADE.get()));
    public static final DeferredItem<Item> SOULFIRE_SEER_SPAWN_EGG = ITEMS.registerItem("soulfire_seer_spawn_egg", Item::new, p -> p.spawnEgg(SOULFIRE_SEER.get()));
    public static final DeferredItem<Item> CINDERMOTH_SWARM_SPAWN_EGG = ITEMS.registerItem("cindermoth_swarm_spawn_egg", Item::new, p -> p.spawnEgg(CINDERMOTH_SWARM.get()));
    public static final DeferredItem<Item> SOULWARD_REVENANT_SPAWN_EGG = ITEMS.registerItem("soulward_revenant_spawn_egg", Item::new, p -> p.spawnEgg(SOULWARD_REVENANT.get()));
    public static final DeferredItem<Item> BASALT_BEHEMOTH_SPAWN_EGG = ITEMS.registerItem("basalt_behemoth_spawn_egg", Item::new, p -> p.spawnEgg(BASALT_BEHEMOTH.get()));
    public static final DeferredItem<Item> QUAKEHOPPER_SPAWN_EGG = ITEMS.registerItem("quakehopper_spawn_egg", Item::new, p -> p.spawnEgg(QUAKEHOPPER.get()));
    public static final DeferredItem<Item> ASHHARRIER_SPAWN_EGG = ITEMS.registerItem("ashharrier_spawn_egg", Item::new, p -> p.spawnEgg(ASHHARRIER.get()));
    public static final DeferredItem<Item> LAVALEAPER_SPAWN_EGG = ITEMS.registerItem("lavaleaper_spawn_egg", Item::new, p -> p.spawnEgg(LAVALEAPER.get()));
    public static final DeferredItem<Item> BLACKSTONE_SCUTTLER_SPAWN_EGG = ITEMS.registerItem("blackstone_scuttler_spawn_egg", Item::new, p -> p.spawnEgg(BLACKSTONE_SCUTTLER.get()));
    public static final DeferredItem<Item> PYRE_HERALD_SPAWN_EGG = ITEMS.registerItem("pyre_herald_spawn_egg", Item::new, p -> p.spawnEgg(PYRE_HERALD.get()));
    public static final DeferredItem<Item> FORTRESS_JAILER_SPAWN_EGG = ITEMS.registerItem("fortress_jailer_spawn_egg", Item::new, p -> p.spawnEgg(FORTRESS_JAILER.get()));
    public static final DeferredItem<Item> FLAMEWRIGHT_SPAWN_EGG = ITEMS.registerItem("flamewright_spawn_egg", Item::new, p -> p.spawnEgg(FLAMEWRIGHT.get()));
    public static final DeferredItem<Item> AUROVORE_SPAWN_EGG = ITEMS.registerItem("aurovore_spawn_egg", Item::new, p -> p.spawnEgg(AUROVORE.get()));
    public static final DeferredItem<Item> MENDING_CRYSTALLING_SPAWN_EGG = ITEMS.registerItem("mending_crystalling_spawn_egg", Item::new, p -> p.spawnEgg(MENDING_CRYSTALLING.get()));
    public static final DeferredItem<Item> LIFTSTONE_MONOLITH_SPAWN_EGG = ITEMS.registerItem("liftstone_monolith_spawn_egg", Item::new, p -> p.spawnEgg(LIFTSTONE_MONOLITH.get()));
    public static final DeferredItem<Item> ASTRAL_AUGUR_SPAWN_EGG = ITEMS.registerItem("astral_augur_spawn_egg", Item::new, p -> p.spawnEgg(ASTRAL_AUGUR.get()));
    public static final DeferredItem<Item> NULLCANTOR_SPAWN_EGG = ITEMS.registerItem("nullcantor_spawn_egg", Item::new, p -> p.spawnEgg(NULLCANTOR.get()));
    public static final DeferredItem<Item> CHORUS_GRAZER_SPAWN_EGG = ITEMS.registerItem("chorus_grazer_spawn_egg", Item::new, p -> p.spawnEgg(CHORUS_GRAZER.get()));
    public static final DeferredItem<Item> LEVIMOTH_SPAWN_EGG = ITEMS.registerItem("levimoth_spawn_egg", Item::new, p -> p.spawnEgg(LEVIMOTH.get()));
    public static final DeferredItem<Item> WARPSEED_BLOOM_SPAWN_EGG = ITEMS.registerItem("warpseed_bloom_spawn_egg", Item::new, p -> p.spawnEgg(WARPSEED_BLOOM.get()));
    public static final DeferredItem<Item> GLIMMERLESS_SPAWN_EGG = ITEMS.registerItem("glimmerless_spawn_egg", Item::new, p -> p.spawnEgg(GLIMMERLESS.get()));
    public static final DeferredItem<Item> RIFTJACKAL_SPAWN_EGG = ITEMS.registerItem("riftjackal_spawn_egg", Item::new, p -> p.spawnEgg(RIFTJACKAL.get()));
    public static final DeferredItem<Item> CHORUS_HUSK_SPAWN_EGG = ITEMS.registerItem("chorus_husk_spawn_egg", Item::new, p -> p.spawnEgg(CHORUS_HUSK.get()));
    public static final DeferredItem<Item> GATECASTER_SPAWN_EGG = ITEMS.registerItem("gatecaster_spawn_egg", Item::new, p -> p.spawnEgg(GATECASTER.get()));
    public static final DeferredItem<Item> HOLLOWMIND_SPAWN_EGG = ITEMS.registerItem("hollowmind_spawn_egg", Item::new, p -> p.spawnEgg(HOLLOWMIND.get()));
    public static final DeferredItem<Item> PEARLGULP_SPAWN_EGG = ITEMS.registerItem("pearlgulp_spawn_egg", Item::new, p -> p.spawnEgg(PEARLGULP.get()));
    public static final DeferredItem<Item> BRINKRAM_SPAWN_EGG = ITEMS.registerItem("brinkram_spawn_egg", Item::new, p -> p.spawnEgg(BRINKRAM.get()));
    public static final DeferredItem<Item> FACET_DOPPEL_SPAWN_EGG = ITEMS.registerItem("facet_doppel_spawn_egg", Item::new, p -> p.spawnEgg(FACET_DOPPEL.get()));
    public static final DeferredItem<Item> SHARDLING_COLOSSUS_SPAWN_EGG = ITEMS.registerItem("shardling_colossus_spawn_egg", Item::new, p -> p.spawnEgg(SHARDLING_COLOSSUS.get()));
    public static final DeferredItem<Item> PHASEWALKER_SPAWN_EGG = ITEMS.registerItem("phasewalker_spawn_egg", Item::new, p -> p.spawnEgg(PHASEWALKER.get()));
    public static final DeferredItem<Item> ECHO_FENCER_SPAWN_EGG = ITEMS.registerItem("echo_fencer_spawn_egg", Item::new, p -> p.spawnEgg(ECHO_FENCER.get()));
    public static final DeferredItem<Item> SWELLSHADE_SPAWN_EGG = ITEMS.registerItem("swellshade_spawn_egg", Item::new, p -> p.spawnEgg(SWELLSHADE.get()));
    public static final DeferredItem<Item> CHRONOMITE_SPAWN_EGG = ITEMS.registerItem("chronomite_spawn_egg", Item::new, p -> p.spawnEgg(CHRONOMITE.get()));

    /** Every spawn egg, in roster order (the creative tab lists them). */
    public static final List<DeferredItem<Item>> EGGS = List.of(THORNHORN_SPAWN_EGG, SNAGWOOD_SPAWN_EGG, PUFFCAP_SHEPHERD_SPAWN_EGG, MIRE_GULPER_SPAWN_EGG, DUNEFIN_SPAWN_EGG, RATTLECOIL_SPAWN_EGG, SNOWROLLER_SPAWN_EGG, SHIVERWRAITH_SPAWN_EGG, GLOAMHOUND_SPAWN_EGG, VINEDROP_STRANGLER_SPAWN_EGG, SNAPMAW_BLOOM_SPAWN_EGG, STORMEEL_SPAWN_EGG, SHELLGUARD_CRAB_SPAWN_EGG, DRIPFANG_SPAWN_EGG, HUSHWING_SPAWN_EGG, MYCELITE_SHAMBLER_SPAWN_EGG, PETALKIN_SPAWN_EGG, HOLLOW_MOURNER_SPAWN_EGG, DUSTWHIRL_SPAWN_EGG, SKREEL_SPAWN_EGG, PRISMBACK_SPAWN_EGG, SLAGSPITTER_SPAWN_EGG, ASH_JACKAL_SPAWN_EGG, SLAGHEART_GOLEM_SPAWN_EGG, SOOT_MANTA_SPAWN_EGG, MAGMA_TORTOISE_SPAWN_EGG, VEINLEECH_SPAWN_EGG, SHREDJAW_BEETLE_SPAWN_EGG, WEEPLING_BROODMOTHER_SPAWN_EGG, GLEAMTRAP_SPAWN_EGG, SCARLET_MANTIS_SPAWN_EGG, PHANTASM_LANTERN_SPAWN_EGG, WOOLCAP_GRAZER_SPAWN_EGG, SIPHONJELLY_SPAWN_EGG, SPORE_CANNON_SPAWN_EGG, GLOOMWEAVER_SPAWN_EGG, OSSUARY_WYRM_SPAWN_EGG, KEENING_SHADE_SPAWN_EGG, SOULFIRE_SEER_SPAWN_EGG, CINDERMOTH_SWARM_SPAWN_EGG, SOULWARD_REVENANT_SPAWN_EGG, BASALT_BEHEMOTH_SPAWN_EGG, QUAKEHOPPER_SPAWN_EGG, ASHHARRIER_SPAWN_EGG, LAVALEAPER_SPAWN_EGG, BLACKSTONE_SCUTTLER_SPAWN_EGG, PYRE_HERALD_SPAWN_EGG, FORTRESS_JAILER_SPAWN_EGG, FLAMEWRIGHT_SPAWN_EGG, AUROVORE_SPAWN_EGG, MENDING_CRYSTALLING_SPAWN_EGG, LIFTSTONE_MONOLITH_SPAWN_EGG, ASTRAL_AUGUR_SPAWN_EGG, NULLCANTOR_SPAWN_EGG, CHORUS_GRAZER_SPAWN_EGG, LEVIMOTH_SPAWN_EGG, WARPSEED_BLOOM_SPAWN_EGG, GLIMMERLESS_SPAWN_EGG, RIFTJACKAL_SPAWN_EGG, CHORUS_HUSK_SPAWN_EGG, GATECASTER_SPAWN_EGG, HOLLOWMIND_SPAWN_EGG, PEARLGULP_SPAWN_EGG, BRINKRAM_SPAWN_EGG, FACET_DOPPEL_SPAWN_EGG, SHARDLING_COLOSSUS_SPAWN_EGG, PHASEWALKER_SPAWN_EGG, ECHO_FENCER_SPAWN_EGG, SWELLSHADE_SPAWN_EGG, CHRONOMITE_SPAWN_EGG);

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
        sink.accept(VINEDROP_STRANGLER.get(), VinedropStrangler.createAttributes().build());
        sink.accept(SNAPMAW_BLOOM.get(), SnapmawBloom.createAttributes().build());
        sink.accept(STORMEEL.get(), Stormeel.createAttributes().build());
        sink.accept(SHELLGUARD_CRAB.get(), ShellguardCrab.createAttributes().build());
        sink.accept(DRIPFANG.get(), Dripfang.createAttributes().build());
        sink.accept(HUSHWING.get(), Hushwing.createAttributes().build());
        sink.accept(MYCELITE_SHAMBLER.get(), MyceliteShambler.createAttributes().build());
        sink.accept(PETALKIN.get(), Petalkin.createAttributes().build());
        sink.accept(HOLLOW_MOURNER.get(), HollowMourner.createAttributes().build());
        sink.accept(DUSTWHIRL.get(), Dustwhirl.createAttributes().build());
        sink.accept(SKREEL.get(), Skreel.createAttributes().build());
        sink.accept(PRISMBACK.get(), Prismback.createAttributes().build());
        sink.accept(SLAGSPITTER.get(), Slagspitter.createAttributes().build());
        sink.accept(ASH_JACKAL.get(), AshJackal.createAttributes().build());
        sink.accept(SLAGHEART_GOLEM.get(), SlagheartGolem.createAttributes().build());
        sink.accept(SOOT_MANTA.get(), SootManta.createAttributes().build());
        sink.accept(MAGMA_TORTOISE.get(), MagmaTortoise.createAttributes().build());
        sink.accept(VEINLEECH.get(), Veinleech.createAttributes().build());
        sink.accept(SHREDJAW_BEETLE.get(), ShredjawBeetle.createAttributes().build());
        sink.accept(WEEPLING_BROODMOTHER.get(), WeeplingBroodmother.createAttributes().build());
        sink.accept(GLEAMTRAP.get(), Gleamtrap.createAttributes().build());
        sink.accept(SCARLET_MANTIS.get(), ScarletMantis.createAttributes().build());
        sink.accept(PHANTASM_LANTERN.get(), PhantasmLantern.createAttributes().build());
        sink.accept(WOOLCAP_GRAZER.get(), WoolcapGrazer.createAttributes().build());
        sink.accept(SIPHONJELLY.get(), Siphonjelly.createAttributes().build());
        sink.accept(SPORE_CANNON.get(), SporeCannon.createAttributes().build());
        sink.accept(GLOOMWEAVER.get(), Gloomweaver.createAttributes().build());
        sink.accept(OSSUARY_WYRM.get(), OssuaryWyrm.createAttributes().build());
        sink.accept(KEENING_SHADE.get(), KeeningShade.createAttributes().build());
        sink.accept(SOULFIRE_SEER.get(), SoulfireSeer.createAttributes().build());
        sink.accept(CINDERMOTH_SWARM.get(), CindermothSwarm.createAttributes().build());
        sink.accept(SOULWARD_REVENANT.get(), SoulwardRevenant.createAttributes().build());
        sink.accept(BASALT_BEHEMOTH.get(), BasaltBehemoth.createAttributes().build());
        sink.accept(QUAKEHOPPER.get(), Quakehopper.createAttributes().build());
        sink.accept(ASHHARRIER.get(), Ashharrier.createAttributes().build());
        sink.accept(LAVALEAPER.get(), Lavaleaper.createAttributes().build());
        sink.accept(BLACKSTONE_SCUTTLER.get(), BlackstoneScuttler.createAttributes().build());
        sink.accept(PYRE_HERALD.get(), PyreHerald.createAttributes().build());
        sink.accept(FORTRESS_JAILER.get(), FortressJailer.createAttributes().build());
        sink.accept(FLAMEWRIGHT.get(), Flamewright.createAttributes().build());
        sink.accept(AUROVORE.get(), Aurovore.createAttributes().build());
        sink.accept(MENDING_CRYSTALLING.get(), MendingCrystalling.createAttributes().build());
        sink.accept(LIFTSTONE_MONOLITH.get(), LiftstoneMonolith.createAttributes().build());
        sink.accept(ASTRAL_AUGUR.get(), AstralAugur.createAttributes().build());
        sink.accept(NULLCANTOR.get(), Nullcantor.createAttributes().build());
        sink.accept(CHORUS_GRAZER.get(), ChorusGrazer.createAttributes().build());
        sink.accept(LEVIMOTH.get(), Levimoth.createAttributes().build());
        sink.accept(WARPSEED_BLOOM.get(), WarpseedBloom.createAttributes().build());
        sink.accept(GLIMMERLESS.get(), Glimmerless.createAttributes().build());
        sink.accept(RIFTJACKAL.get(), Riftjackal.createAttributes().build());
        sink.accept(CHORUS_HUSK.get(), ChorusHusk.createAttributes().build());
        sink.accept(GATECASTER.get(), Gatecaster.createAttributes().build());
        sink.accept(HOLLOWMIND.get(), Hollowmind.createAttributes().build());
        sink.accept(PEARLGULP.get(), Pearlgulp.createAttributes().build());
        sink.accept(BRINKRAM.get(), Brinkram.createAttributes().build());
        sink.accept(FACET_DOPPEL.get(), FacetDoppel.createAttributes().build());
        sink.accept(SHARDLING_COLOSSUS.get(), ShardlingColossus.createAttributes().build());
        sink.accept(PHASEWALKER.get(), Phasewalker.createAttributes().build());
        sink.accept(ECHO_FENCER.get(), EchoFencer.createAttributes().build());
        sink.accept(SWELLSHADE.get(), Swellshade.createAttributes().build());
        sink.accept(CHRONOMITE.get(), Chronomite.createAttributes().build());
    }
}
