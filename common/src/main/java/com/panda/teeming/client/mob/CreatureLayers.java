package com.panda.teeming.client.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.client.ClientSinks;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** The model layers of every mob: boxes and texture coordinates, matching each mob's texture atlas. */
@SuppressWarnings("unused")
public final class CreatureLayers {
    public static final ModelLayerLocation THORNHORN = new ModelLayerLocation(TeemingMod.id("thornhorn"), "main");
    public static final ModelLayerLocation SNAGWOOD = new ModelLayerLocation(TeemingMod.id("snagwood"), "main");
    public static final ModelLayerLocation PUFFCAP_SHEPHERD = new ModelLayerLocation(TeemingMod.id("puffcap_shepherd"), "main");
    public static final ModelLayerLocation MIRE_GULPER = new ModelLayerLocation(TeemingMod.id("mire_gulper"), "main");
    public static final ModelLayerLocation DUNEFIN = new ModelLayerLocation(TeemingMod.id("dunefin"), "main");
    public static final ModelLayerLocation RATTLECOIL = new ModelLayerLocation(TeemingMod.id("rattlecoil"), "main");
    public static final ModelLayerLocation SNOWROLLER = new ModelLayerLocation(TeemingMod.id("snowroller"), "main");
    public static final ModelLayerLocation SHIVERWRAITH = new ModelLayerLocation(TeemingMod.id("shiverwraith"), "main");
    public static final ModelLayerLocation GLOAMHOUND = new ModelLayerLocation(TeemingMod.id("gloamhound"), "main");
    public static final ModelLayerLocation VINEDROP_STRANGLER = new ModelLayerLocation(TeemingMod.id("vinedrop_strangler"), "main");
    public static final ModelLayerLocation SNAPMAW_BLOOM = new ModelLayerLocation(TeemingMod.id("snapmaw_bloom"), "main");
    public static final ModelLayerLocation STORMEEL = new ModelLayerLocation(TeemingMod.id("stormeel"), "main");
    public static final ModelLayerLocation SHELLGUARD_CRAB = new ModelLayerLocation(TeemingMod.id("shellguard_crab"), "main");
    public static final ModelLayerLocation DRIPFANG = new ModelLayerLocation(TeemingMod.id("dripfang"), "main");
    public static final ModelLayerLocation HUSHWING = new ModelLayerLocation(TeemingMod.id("hushwing"), "main");
    public static final ModelLayerLocation MYCELITE_SHAMBLER = new ModelLayerLocation(TeemingMod.id("mycelite_shambler"), "main");
    public static final ModelLayerLocation PETALKIN = new ModelLayerLocation(TeemingMod.id("petalkin"), "main");
    public static final ModelLayerLocation HOLLOW_MOURNER = new ModelLayerLocation(TeemingMod.id("hollow_mourner"), "main");
    public static final ModelLayerLocation DUSTWHIRL = new ModelLayerLocation(TeemingMod.id("dustwhirl"), "main");
    public static final ModelLayerLocation SKREEL = new ModelLayerLocation(TeemingMod.id("skreel"), "main");
    public static final ModelLayerLocation PRISMBACK = new ModelLayerLocation(TeemingMod.id("prismback"), "main");
    public static final ModelLayerLocation SLAGSPITTER = new ModelLayerLocation(TeemingMod.id("slagspitter"), "main");
    public static final ModelLayerLocation ASH_JACKAL = new ModelLayerLocation(TeemingMod.id("ash_jackal"), "main");
    public static final ModelLayerLocation SLAGHEART_GOLEM = new ModelLayerLocation(TeemingMod.id("slagheart_golem"), "main");
    public static final ModelLayerLocation SOOT_MANTA = new ModelLayerLocation(TeemingMod.id("soot_manta"), "main");
    public static final ModelLayerLocation MAGMA_TORTOISE = new ModelLayerLocation(TeemingMod.id("magma_tortoise"), "main");
    public static final ModelLayerLocation VEINLEECH = new ModelLayerLocation(TeemingMod.id("veinleech"), "main");
    public static final ModelLayerLocation SHREDJAW_BEETLE = new ModelLayerLocation(TeemingMod.id("shredjaw_beetle"), "main");
    public static final ModelLayerLocation WEEPLING_BROODMOTHER = new ModelLayerLocation(TeemingMod.id("weepling_broodmother"), "main");
    public static final ModelLayerLocation GLEAMTRAP = new ModelLayerLocation(TeemingMod.id("gleamtrap"), "main");
    public static final ModelLayerLocation SCARLET_MANTIS = new ModelLayerLocation(TeemingMod.id("scarlet_mantis"), "main");
    public static final ModelLayerLocation PHANTASM_LANTERN = new ModelLayerLocation(TeemingMod.id("phantasm_lantern"), "main");
    public static final ModelLayerLocation WOOLCAP_GRAZER = new ModelLayerLocation(TeemingMod.id("woolcap_grazer"), "main");
    public static final ModelLayerLocation SIPHONJELLY = new ModelLayerLocation(TeemingMod.id("siphonjelly"), "main");
    public static final ModelLayerLocation SPORE_CANNON = new ModelLayerLocation(TeemingMod.id("spore_cannon"), "main");
    public static final ModelLayerLocation GLOOMWEAVER = new ModelLayerLocation(TeemingMod.id("gloomweaver"), "main");
    public static final ModelLayerLocation OSSUARY_WYRM = new ModelLayerLocation(TeemingMod.id("ossuary_wyrm"), "main");
    public static final ModelLayerLocation KEENING_SHADE = new ModelLayerLocation(TeemingMod.id("keening_shade"), "main");
    public static final ModelLayerLocation SOULFIRE_SEER = new ModelLayerLocation(TeemingMod.id("soulfire_seer"), "main");
    public static final ModelLayerLocation CINDERMOTH_SWARM = new ModelLayerLocation(TeemingMod.id("cindermoth_swarm"), "main");
    public static final ModelLayerLocation SOULWARD_REVENANT = new ModelLayerLocation(TeemingMod.id("soulward_revenant"), "main");
    public static final ModelLayerLocation BASALT_BEHEMOTH = new ModelLayerLocation(TeemingMod.id("basalt_behemoth"), "main");
    public static final ModelLayerLocation QUAKEHOPPER = new ModelLayerLocation(TeemingMod.id("quakehopper"), "main");
    public static final ModelLayerLocation ASHHARRIER = new ModelLayerLocation(TeemingMod.id("ashharrier"), "main");
    public static final ModelLayerLocation LAVALEAPER = new ModelLayerLocation(TeemingMod.id("lavaleaper"), "main");
    public static final ModelLayerLocation BLACKSTONE_SCUTTLER = new ModelLayerLocation(TeemingMod.id("blackstone_scuttler"), "main");
    public static final ModelLayerLocation PYRE_HERALD = new ModelLayerLocation(TeemingMod.id("pyre_herald"), "main");
    public static final ModelLayerLocation FORTRESS_JAILER = new ModelLayerLocation(TeemingMod.id("fortress_jailer"), "main");
    public static final ModelLayerLocation FLAMEWRIGHT = new ModelLayerLocation(TeemingMod.id("flamewright"), "main");
    public static final ModelLayerLocation AUROVORE = new ModelLayerLocation(TeemingMod.id("aurovore"), "main");
    public static final ModelLayerLocation MENDING_CRYSTALLING = new ModelLayerLocation(TeemingMod.id("mending_crystalling"), "main");
    public static final ModelLayerLocation LIFTSTONE_MONOLITH = new ModelLayerLocation(TeemingMod.id("liftstone_monolith"), "main");
    public static final ModelLayerLocation ASTRAL_AUGUR = new ModelLayerLocation(TeemingMod.id("astral_augur"), "main");
    public static final ModelLayerLocation NULLCANTOR = new ModelLayerLocation(TeemingMod.id("nullcantor"), "main");
    public static final ModelLayerLocation CHORUS_GRAZER = new ModelLayerLocation(TeemingMod.id("chorus_grazer"), "main");
    public static final ModelLayerLocation LEVIMOTH = new ModelLayerLocation(TeemingMod.id("levimoth"), "main");
    public static final ModelLayerLocation WARPSEED_BLOOM = new ModelLayerLocation(TeemingMod.id("warpseed_bloom"), "main");
    public static final ModelLayerLocation GLIMMERLESS = new ModelLayerLocation(TeemingMod.id("glimmerless"), "main");
    public static final ModelLayerLocation RIFTJACKAL = new ModelLayerLocation(TeemingMod.id("riftjackal"), "main");
    public static final ModelLayerLocation CHORUS_HUSK = new ModelLayerLocation(TeemingMod.id("chorus_husk"), "main");
    public static final ModelLayerLocation GATECASTER = new ModelLayerLocation(TeemingMod.id("gatecaster"), "main");
    public static final ModelLayerLocation HOLLOWMIND = new ModelLayerLocation(TeemingMod.id("hollowmind"), "main");
    public static final ModelLayerLocation PEARLGULP = new ModelLayerLocation(TeemingMod.id("pearlgulp"), "main");
    public static final ModelLayerLocation BRINKRAM = new ModelLayerLocation(TeemingMod.id("brinkram"), "main");
    public static final ModelLayerLocation FACET_DOPPEL = new ModelLayerLocation(TeemingMod.id("facet_doppel"), "main");
    public static final ModelLayerLocation SHARDLING_COLOSSUS = new ModelLayerLocation(TeemingMod.id("shardling_colossus"), "main");
    public static final ModelLayerLocation PHASEWALKER = new ModelLayerLocation(TeemingMod.id("phasewalker"), "main");
    public static final ModelLayerLocation ECHO_FENCER = new ModelLayerLocation(TeemingMod.id("echo_fencer"), "main");
    public static final ModelLayerLocation SWELLSHADE = new ModelLayerLocation(TeemingMod.id("swellshade"), "main");
    public static final ModelLayerLocation CHRONOMITE = new ModelLayerLocation(TeemingMod.id("chronomite"), "main");
    public static final ModelLayerLocation ANCHOR_GOLEM = new ModelLayerLocation(TeemingMod.id("anchor_golem"), "main");
    public static final ModelLayerLocation RECURRENT = new ModelLayerLocation(TeemingMod.id("recurrent"), "main");
    public static final ModelLayerLocation MNEMOVORE = new ModelLayerLocation(TeemingMod.id("mnemovore"), "main");
    public static final ModelLayerLocation UNDERCLINGER = new ModelLayerLocation(TeemingMod.id("underclinger"), "main");
    public static final ModelLayerLocation WINGSHEAR = new ModelLayerLocation(TeemingMod.id("wingshear"), "main");
    public static final ModelLayerLocation GRAND_ORRERY = new ModelLayerLocation(TeemingMod.id("grand_orrery"), "main");

    private CreatureLayers() {}

    public static void register(ClientSinks.Layers out) {
        out.register(THORNHORN, CreatureLayers::thornhorn);
        out.register(SNAGWOOD, CreatureLayers::snagwood);
        out.register(PUFFCAP_SHEPHERD, CreatureLayers::puffcapShepherd);
        out.register(MIRE_GULPER, CreatureLayers::mire_gulper);
        out.register(DUNEFIN, CreatureLayers::dunefin);
        out.register(RATTLECOIL, CreatureLayers::rattlecoil);
        out.register(SNOWROLLER, CreatureLayers::snowroller);
        out.register(SHIVERWRAITH, CreatureLayers::shiverwraith);
        out.register(GLOAMHOUND, CreatureLayers::gloamhound);
        out.register(VINEDROP_STRANGLER, CreatureLayers::vinedrop_strangler);
        out.register(SNAPMAW_BLOOM, CreatureLayers::snapmawBloom);
        out.register(STORMEEL, CreatureLayers::stormeel);
        out.register(SHELLGUARD_CRAB, CreatureLayers::shellguard_crab);
        out.register(DRIPFANG, CreatureLayers::dripfang);
        out.register(HUSHWING, CreatureLayers::hushwing);
        out.register(MYCELITE_SHAMBLER, CreatureLayers::myceliteShambler);
        out.register(PETALKIN, CreatureLayers::petalkin);
        out.register(HOLLOW_MOURNER, CreatureLayers::hollowMourner);
        out.register(DUSTWHIRL, CreatureLayers::dustwhirl);
        out.register(SKREEL, CreatureLayers::skreel);
        out.register(PRISMBACK, CreatureLayers::prismback);
        out.register(SLAGSPITTER, CreatureLayers::slagspitter);
        out.register(ASH_JACKAL, CreatureLayers::ash_jackal);
        out.register(SLAGHEART_GOLEM, CreatureLayers::slagheartGolem);
        out.register(SOOT_MANTA, CreatureLayers::soot_manta);
        out.register(MAGMA_TORTOISE, CreatureLayers::magmaTortoise);
        out.register(VEINLEECH, CreatureLayers::veinleech);
        out.register(SHREDJAW_BEETLE, CreatureLayers::shredjaw_beetle);
        out.register(WEEPLING_BROODMOTHER, CreatureLayers::weeplingBroodmother);
        out.register(GLEAMTRAP, CreatureLayers::gleamtrap);
        out.register(SCARLET_MANTIS, CreatureLayers::scarlet_mantis);
        out.register(PHANTASM_LANTERN, CreatureLayers::phantasmLantern);
        out.register(WOOLCAP_GRAZER, CreatureLayers::woolcapGrazer);
        out.register(SIPHONJELLY, CreatureLayers::siphonjelly);
        out.register(SPORE_CANNON, CreatureLayers::sporeCannon);
        out.register(GLOOMWEAVER, CreatureLayers::gloomweaver);
        out.register(OSSUARY_WYRM, CreatureLayers::ossuary_wyrm);
        out.register(KEENING_SHADE, CreatureLayers::keeningShade);
        out.register(SOULFIRE_SEER, CreatureLayers::soulfireSeer);
        out.register(CINDERMOTH_SWARM, CreatureLayers::cindermothSwarm);
        out.register(SOULWARD_REVENANT, CreatureLayers::soulwardRevenant);
        out.register(BASALT_BEHEMOTH, CreatureLayers::basaltBehemoth);
        out.register(QUAKEHOPPER, CreatureLayers::quakehopper);
        out.register(ASHHARRIER, CreatureLayers::ashharrier);
        out.register(LAVALEAPER, CreatureLayers::lavaleaper);
        out.register(BLACKSTONE_SCUTTLER, CreatureLayers::blackstone_scuttler);
        out.register(PYRE_HERALD, CreatureLayers::pyreHerald);
        out.register(FORTRESS_JAILER, CreatureLayers::fortressJailer);
        out.register(FLAMEWRIGHT, CreatureLayers::flamewright);
        out.register(AUROVORE, CreatureLayers::aurovore);
        out.register(MENDING_CRYSTALLING, CreatureLayers::mendingCrystalling);
        out.register(LIFTSTONE_MONOLITH, CreatureLayers::liftstoneMonolith);
        out.register(ASTRAL_AUGUR, CreatureLayers::astralAugur);
        out.register(NULLCANTOR, CreatureLayers::nullcantor);
        out.register(CHORUS_GRAZER, CreatureLayers::chorusGrazer);
        out.register(LEVIMOTH, CreatureLayers::levimoth);
        out.register(WARPSEED_BLOOM, CreatureLayers::warpseedBloom);
        out.register(GLIMMERLESS, CreatureLayers::glimmerless);
        out.register(RIFTJACKAL, CreatureLayers::riftjackal);
        out.register(CHORUS_HUSK, CreatureLayers::chorusHusk);
        out.register(GATECASTER, CreatureLayers::gatecaster);
        out.register(HOLLOWMIND, CreatureLayers::hollowmind);
        out.register(PEARLGULP, CreatureLayers::pearlgulp);
        out.register(BRINKRAM, CreatureLayers::brinkram);
        out.register(FACET_DOPPEL, CreatureLayers::facetDoppel);
        out.register(SHARDLING_COLOSSUS, CreatureLayers::shardlingColossus);
        out.register(PHASEWALKER, CreatureLayers::phasewalker);
        out.register(ECHO_FENCER, CreatureLayers::echoFencer);
        out.register(SWELLSHADE, CreatureLayers::swellshade);
        out.register(CHRONOMITE, CreatureLayers::chronomite);
        out.register(ANCHOR_GOLEM, CreatureLayers::anchorGolem);
        out.register(RECURRENT, CreatureLayers::recurrent);
        out.register(MNEMOVORE, CreatureLayers::mnemovore);
        out.register(UNDERCLINGER, CreatureLayers::underclinger);
        out.register(WINGSHEAR, CreatureLayers::wingshear);
        out.register(GRAND_ORRERY, CreatureLayers::grandOrrery);
    }

    public static LayerDefinition thornhorn() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -5.0F, -9.0F, 14.0F, 11.0F, 18.0F).texOffs(64, 0).addBox(-8.0F, -9.0F, -10.0F, 16.0F, 9.0F, 9.0F).texOffs(64, 18).addBox(-6.0F, 5.0F, -6.0F, 12.0F, 2.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(114, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 7.0F, 2.0F).texOffs(114, 9).addBox(-1.5F, 6.0F, -0.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 9.0F, 0.4363F, 0.0F, 0.0F));
        PartDefinition p_head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 29).addBox(-4.0F, -4.0F, -7.0F, 8.0F, 8.0F, 7.0F).texOffs(110, 42).addBox(-3.0F, 0.0F, -10.0F, 6.0F, 5.0F, 3.0F).texOffs(74, 32).addBox(-4.5F, -6.0F, -6.0F, 9.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 7.0F, -10.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_horn_right = p_head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(90, 40).addBox(-6.0F, -2.0F, -2.0F, 6.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(-4.0F, -3.0F, -3.5F, 0.0F, 0.0F, 0.2094F));
        p_horn_right.addOrReplaceChild("horn_right_tip", CubeListBuilder.create().texOffs(30, 29).addBox(-1.5F, -1.5F, -8.0F, 3.0F, 3.0F, 8.0F).texOffs(122, 0).addBox(0.0F, -2.5F, -5.0F, 1.0F, 1.0F, 1.0F).texOffs(122, 2).addBox(0.0F, -2.5F, -2.0F, 1.0F, 1.0F, 1.0F).texOffs(102, 32).addBox(-0.5F, -1.0F, -11.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-5.5F, 0.0F, 0.0F, -0.3142F, -0.6981F, 0.0F));
        PartDefinition p_horn_left = p_head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(46, 43).addBox(0.0F, -2.0F, -2.0F, 6.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(4.0F, -3.0F, -3.5F, 0.0F, 0.0F, -0.2094F));
        p_horn_left.addOrReplaceChild("horn_left_tip", CubeListBuilder.create().texOffs(52, 32).addBox(-1.5F, -1.5F, -8.0F, 3.0F, 3.0F, 8.0F).texOffs(122, 4).addBox(-1.0F, -2.5F, -5.0F, 1.0F, 1.0F, 1.0F).texOffs(122, 6).addBox(-1.0F, -2.5F, -2.0F, 1.0F, 1.0F, 1.0F).texOffs(66, 43).addBox(-0.5F, -1.0F, -11.0F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(5.5F, 0.0F, 0.0F, -0.3142F, 0.6981F, 0.0F));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(112, 18).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F).texOffs(0, 44).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(-4.5F, 14.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(112, 30).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F).texOffs(90, 48).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(4.5F, 14.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(30, 40).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F).texOffs(0, 51).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(-4.5F, 14.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(74, 40).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F).texOffs(46, 51).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(4.5F, 14.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> THORNHORN_ONLY = Map.of();

    public static LayerDefinition snagwood() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(48, 30).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F).texOffs(0, 24).addBox(-3.0F, 7.0F, -3.0F, 6.0F, 3.0F, 6.0F).texOffs(0, 44).addBox(-1.0F, 8.0F, -6.0F, 2.0F, 2.0F, 4.0F).texOffs(12, 53).addBox(-4.0F, 8.0F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(52, 55).addBox(-1.0F, 9.0F, 2.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-2.5F, 14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(0, 33).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F).texOffs(24, 30).addBox(-3.0F, 7.0F, -3.0F, 6.0F, 3.0F, 6.0F).texOffs(0, 50).addBox(-1.0F, 8.0F, -6.0F, 2.0F, 2.0F, 4.0F).texOffs(0, 56).addBox(1.0F, 8.0F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(22, 56).addBox(-1.0F, 9.0F, 2.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(2.5F, 14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -17.0F, -3.5F, 8.0F, 17.0F, 7.0F).texOffs(30, 18).addBox(-4.5F, -4.0F, -4.0F, 9.0F, 4.0F, 8.0F).texOffs(52, 50).addBox(-6.0F, -8.0F, -2.0F, 2.0F, 1.0F, 4.0F).texOffs(32, 56).addBox(-6.0F, -11.0F, 0.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("stub", CubeListBuilder.create().texOffs(28, 52).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(3.5F, -12.0F, 0.0F, 0.0F, 0.0F, -0.6981F));
        PartDefinition p_crown = p_body.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(30, 0).addBox(-3.5F, -12.0F, -3.0F, 7.0F, 12.0F, 6.0F).texOffs(40, 50).addBox(-3.5F, -15.0F, -3.0F, 3.0F, 3.0F, 3.0F).texOffs(40, 41).addBox(0.5F, -18.0F, -1.0F, 3.0F, 6.0F, 3.0F).texOffs(56, 11).addBox(-2.0F, -14.0F, 1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -17.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_crown.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(42, 59).addBox(-3.5F, -1.0F, -0.15F, 7.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -9.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_crown.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(16, 39).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 11.0F, 3.0F), PartPose.offsetAndRotation(-5.0F, -9.0F, 0.0F, -0.2443F, 0.0F, 0.2793F));
        p_arm_right.addOrReplaceChild("arm_right_twig", CubeListBuilder.create().texOffs(20, 33).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-1.2F, 4.95F, 0.0F, 0.0F, 0.0F, 0.9599F));
        PartDefinition p_forearm_right = p_arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(56, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, -0.2094F, 0.0F, -0.1047F));
        p_forearm_right.addOrReplaceChild("twig_right_0", CubeListBuilder.create().texOffs(60, 41).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.5F, 0.0F, -0.007F, 0.0F, -0.5585F));
        p_forearm_right.addOrReplaceChild("twig_right_1", CubeListBuilder.create().texOffs(24, 24).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.5F, 0.0F, -0.1745F, 0.0F, 0.0F));
        p_forearm_right.addOrReplaceChild("twig_right_2", CubeListBuilder.create().texOffs(12, 44).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.5F, 0.0F, -0.007F, 0.0F, 0.5585F));
        PartDefinition p_arm_left = p_crown.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(28, 39).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 10.0F, 3.0F), PartPose.offsetAndRotation(5.0F, -9.0F, 0.0F, -0.2443F, 0.0F, -0.2793F));
        p_arm_left.addOrReplaceChild("arm_left_twig", CubeListBuilder.create().texOffs(10, 57).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(1.2F, 4.5F, 0.0F, 0.0F, 0.0F, -0.9599F));
        PartDefinition p_forearm_left = p_arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(52, 41).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, -0.2094F, 0.0F, 0.1047F));
        p_forearm_left.addOrReplaceChild("twig_left_0", CubeListBuilder.create().texOffs(14, 57).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 6.5F, 0.0F, -0.0908F, 0.0F, -0.2793F));
        p_forearm_left.addOrReplaceChild("twig_left_1", CubeListBuilder.create().texOffs(16, 33).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 6.5F, 0.0F, -0.0908F, 0.0F, 0.2793F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> SNAGWOOD_ONLY = Map.of();

    public static LayerDefinition puffcapShepherd() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(48, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F), PartPose.offsetAndRotation(-1.7F, 20.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(48, 7).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F), PartPose.offsetAndRotation(1.7F, 20.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(36, 15).addBox(-3.0F, -9.0F, -2.5F, 6.0F, 9.0F, 5.0F).texOffs(0, 26).addBox(-3.5F, -5.0F, -3.0F, 7.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("sac", CubeListBuilder.create().texOffs(46, 29).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(1.0F, -2.5F, 2.7F, 0.0F, 0.2618F, 0.0F));
        p_body.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -3.0F, -6.0F, 12.0F, 3.0F, 12.0F).texOffs(0, 15).addBox(-4.5F, -5.0F, -4.5F, 9.0F, 2.0F, 9.0F).texOffs(26, 29).addBox(-2.5F, -6.0F, -2.5F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(0, 35).addBox(-1.7F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F), PartPose.offsetAndRotation(-3.6F, -4.0F, 0.0F, 0.0F, 0.0F, 0.5236F));
        p_arm_right.addOrReplaceChild("crook", CubeListBuilder.create().texOffs(60, 0).addBox(-0.5F, -10.5F, -0.5F, 1.0F, 14.0F, 1.0F).texOffs(16, 35).addBox(-0.5F, -11.2F, -3.5F, 1.0F, 1.0F, 4.0F).texOffs(58, 15).addBox(-0.35F, -10.4F, -3.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-0.7F, 4.2F, 0.0F, 0.0F, 0.0F, -0.5236F));
        p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(8, 35).addBox(-0.3F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F), PartPose.offsetAndRotation(3.6F, -4.0F, 0.0F, 0.0F, 0.0F, -0.1745F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> PUFFCAP_SHEPHERD_ONLY = Map.of();

    public static LayerDefinition mire_gulper() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-10.0F, -8.0F, -1.0F, 20.0F, 8.0F, 13.0F).texOffs(66, 0).addBox(-8.0F, -10.0F, 1.0F, 16.0F, 2.0F, 9.0F).texOffs(110, 57).addBox(-5.0F, -11.0F, 3.0F, 4.0F, 1.0F, 3.0F).texOffs(16, 57).addBox(2.0F, -11.0F, 5.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 21).addBox(-11.0F, -4.0F, -14.0F, 22.0F, 4.0F, 14.0F).texOffs(118, 17).addBox(-8.5F, 0.0F, -14.2F, 1.0F, 2.0F, 1.0F).texOffs(122, 19).addBox(-5.5F, 0.0F, -14.2F, 1.0F, 2.0F, 1.0F).texOffs(116, 34).addBox(-2.5F, 0.0F, -14.2F, 1.0F, 2.0F, 1.0F).texOffs(120, 34).addBox(1.5F, 0.0F, -14.2F, 1.0F, 2.0F, 1.0F).texOffs(124, 34).addBox(4.5F, 0.0F, -14.2F, 1.0F, 2.0F, 1.0F).texOffs(116, 37).addBox(7.5F, 0.0F, -14.2F, 1.0F, 2.0F, 1.0F).texOffs(120, 37).addBox(-11.0F, 0.0F, -11.0F, 1.0F, 2.0F, 1.0F).texOffs(124, 37).addBox(10.0F, 0.0F, -11.0F, 1.0F, 2.0F, 1.0F).texOffs(68, 39).addBox(-11.0F, 0.0F, -7.0F, 1.0F, 2.0F, 1.0F).texOffs(116, 40).addBox(10.0F, 0.0F, -7.0F, 1.0F, 2.0F, 1.0F).texOffs(120, 40).addBox(-11.0F, 0.0F, -3.5F, 1.0F, 2.0F, 1.0F).texOffs(124, 40).addBox(10.0F, 0.0F, -3.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("eye_right", CubeListBuilder.create().texOffs(68, 44).addBox(-2.5F, -4.0F, -2.0F, 5.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(-6.5F, -4.0F, -9.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("eye_left", CubeListBuilder.create().texOffs(68, 52).addBox(-2.5F, -4.0F, -2.0F, 5.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(6.5F, -4.0F, -9.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("lily", CubeListBuilder.create().texOffs(86, 52).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 0.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -4.15F, -4.0F, 0.0F, 0.3491F, 0.0F));
        PartDefinition p_jaw = p_body.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 39).addBox(-10.5F, 0.15F, -13.0F, 21.0F, 5.0F, 13.0F).texOffs(116, 43).addBox(-7.0F, -1.7F, -12.8F, 1.0F, 2.0F, 1.0F).texOffs(120, 43).addBox(-1.5F, -1.7F, -12.8F, 1.0F, 2.0F, 1.0F).texOffs(124, 43).addBox(3.0F, -1.7F, -12.8F, 1.0F, 2.0F, 1.0F).texOffs(86, 44).addBox(6.5F, -1.7F, -12.8F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_jaw.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(72, 22).addBox(-3.5F, -0.7F, -9.0F, 7.0F, 1.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        p_jaw.addOrReplaceChild("throat", CubeListBuilder.create().texOffs(72, 11).addBox(-7.0F, 0.0F, -4.5F, 14.0F, 2.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 5.3F, -8.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("roots", CubeListBuilder.create().texOffs(122, 10).addBox(-7.0F, -3.0F, 3.0F, 1.0F, 4.0F, 1.0F).texOffs(118, 20).addBox(-6.0F, -4.0F, 3.0F, 1.0F, 1.0F, 1.0F).texOffs(94, 32).addBox(-5.0F, -5.0F, 3.0F, 2.0F, 1.0F, 1.0F).texOffs(100, 32).addBox(-3.0F, -4.0F, 3.0F, 1.0F, 1.0F, 1.0F).texOffs(66, 11).addBox(-2.0F, -3.0F, 3.0F, 1.0F, 4.0F, 1.0F).texOffs(122, 15).addBox(2.0F, -2.0F, 6.0F, 1.0F, 3.0F, 1.0F).texOffs(68, 42).addBox(3.0F, -3.0F, 6.0F, 1.0F, 1.0F, 1.0F).texOffs(116, 46).addBox(4.0F, -4.0F, 6.0F, 2.0F, 1.0F, 1.0F).texOffs(90, 44).addBox(6.0F, -3.0F, 6.0F, 1.0F, 1.0F, 1.0F).texOffs(66, 16).addBox(7.0F, -2.0F, 6.0F, 1.0F, 3.0F, 1.0F).texOffs(118, 10).addBox(-1.0F, -5.0F, 5.0F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(30, 57).addBox(-4.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F).texOffs(116, 0).addBox(-5.0F, 1.0F, -2.0F, 2.0F, 2.0F, 3.0F).texOffs(110, 52).addBox(-6.5F, 3.0F, -3.5F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-10.0F, 20.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(104, 22).addBox(-4.0F, -2.0F, -3.0F, 4.0F, 5.0F, 7.0F).texOffs(94, 34).addBox(-4.5F, 3.0F, -6.0F, 4.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(-9.0F, 19.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(44, 57).addBox(0.0F, -1.0F, -1.5F, 4.0F, 2.0F, 3.0F).texOffs(116, 5).addBox(3.0F, 1.0F, -2.0F, 2.0F, 2.0F, 3.0F).texOffs(0, 57).addBox(2.5F, 3.0F, -3.5F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(10.0F, 20.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(72, 32).addBox(0.0F, -2.0F, -3.0F, 4.0F, 5.0F, 7.0F).texOffs(94, 43).addBox(0.5F, 3.0F, -6.0F, 4.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(9.0F, 19.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of mire_gulper. */
    public static final Map<String, String[]> MIRE_GULPER_ONLY = Map.ofEntries(
            Map.entry("lily", new String[] {"base"}),
            Map.entry("roots", new String[] {"mangrove_gulper"}));

    public static LayerDefinition dunefin() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -5.0F, 8.0F, 7.0F, 10.0F).texOffs(0, 17).addBox(-3.0F, 3.0F, -5.0F, 6.0F, 1.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(36, 0).addBox(-3.5F, -3.5F, -6.0F, 7.0F, 6.0F, 6.0F, new CubeDeformation(0.15F)).texOffs(48, 32).addBox(-2.5F, -2.5F, -8.0F, 5.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(32, 24).addBox(-3.0F, 0.0F, -6.0F, 6.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.35F, -0.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(18, 32).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 2.0F, 6.0F).texOffs(34, 32).addBox(-1.0F, -4.0F, -2.0F, 2.0F, 2.0F, 5.0F).texOffs(52, 38).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 2.0F, 4.0F).texOffs(18, 40).addBox(-1.0F, -8.0F, 0.0F, 2.0F, 2.0F, 3.0F).texOffs(18, 28).addBox(-1.0F, -10.0F, 1.5F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(0, 37).addBox(-5.0F, 0.0F, 0.0F, 5.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-4.0F, 2.0F, -2.0F, 0.0F, 0.384F, -0.5236F));
        p_body.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(34, 39).addBox(0.0F, 0.0F, 0.0F, 5.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(4.0F, 2.0F, -2.0F, 0.0F, -0.384F, 0.5236F));
        PartDefinition p_tail1 = p_body.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(36, 12).addBox(-3.0F, -3.0F, -1.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, -0.5F, 5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 28).addBox(-2.0F, -2.0F, -1.0F, 4.0F, 4.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        p_tail2.addOrReplaceChild("tail_fin", CubeListBuilder.create().texOffs(56, 24).addBox(-0.5F, -4.0F, -1.0F, 1.0F, 4.0F, 3.0F).texOffs(0, 42).addBox(-0.5F, -7.0F, 1.0F, 1.0F, 3.0F, 3.0F).texOffs(8, 42).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> DUNEFIN_ONLY = Map.of();

    public static LayerDefinition rattlecoil() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 24.0F, -2.0F, 0.0F, -0.2748F, 0.0F));
        PartDefinition p_neck2 = p_body.addOrReplaceChild("neck2", CubeListBuilder.create().texOffs(24, 0).addBox(-2.5F, -4.0F, -4.0F, 5.0F, 4.0F, 6.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, -3.0F, 0.0F, 0.1421F, 0.0F));
        PartDefinition p_neck1 = p_neck2.addOrReplaceChild("neck1", CubeListBuilder.create().texOffs(46, 0).addBox(-2.0F, -3.25F, -3.0F, 4.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, 0.2421F, 0.0F));
        PartDefinition p_head = p_neck1.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 20).addBox(-3.5F, -4.0F, -4.0F, 7.0F, 3.0F, 4.0F, new CubeDeformation(0.15F)).texOffs(44, 26).addBox(-2.5F, -3.0F, -6.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, -0.1094F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(22, 20).addBox(-2.5F, 0.0F, -6.0F, 5.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("fangs", CubeListBuilder.create().texOffs(20, 11).addBox(-2.0F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(20, 14).addBox(1.0F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -5.0F, 1.5708F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(0, 27).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 0.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(20, 17).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.4F, -4.0F, -2.6F, -0.5236F, 0.0F, -0.3491F));
        p_head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(58, 19).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.4F, -4.0F, -2.6F, -0.5236F, 0.0F, 0.3491F));
        PartDefinition p_tail1 = p_body.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(24, 10).addBox(-2.5F, -4.0F, -2.0F, 5.0F, 4.0F, 6.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0648F, 0.0F));
        PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 11).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 0.223F, 0.0F));
        PartDefinition p_tail3 = p_tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(46, 8).addBox(-1.5F, -3.0F, -2.0F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 0.2132F, 0.0F));
        p_tail3.addOrReplaceChild("rattle", CubeListBuilder.create().texOffs(46, 16).addBox(-2.0F, -3.0F, -1.0F, 4.0F, 3.0F, 2.0F).texOffs(44, 21).addBox(-2.0F, -3.0F, 1.0F, 4.0F, 3.0F, 2.0F).texOffs(14, 27).addBox(-1.5F, -3.0F, 3.0F, 3.0F, 3.0F, 2.0F).texOffs(58, 16).addBox(-1.0F, -2.5F, 5.0F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0429F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    /** Parts shown only for some variants of rattlecoil. */
    public static final Map<String, String[]> RATTLECOIL_ONLY = Map.ofEntries(
            Map.entry("horn_left", new String[] {"sidewinder"}),
            Map.entry("horn_right", new String[] {"sidewinder"}));

    public static LayerDefinition snowroller() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(52, 0).addBox(-7.0F, -5.0F, -6.0F, 14.0F, 7.0F, 12.0F).texOffs(0, 0).addBox(-6.0F, -5.0F, -7.0F, 12.0F, 7.0F, 14.0F).texOffs(0, 21).addBox(-6.0F, 1.0F, -6.0F, 12.0F, 3.0F, 12.0F).texOffs(52, 19).addBox(-7.5F, 0.5F, -6.5F, 15.0F, 2.0F, 13.0F).texOffs(48, 34).addBox(-6.0F, -7.0F, -5.5F, 12.0F, 2.0F, 11.0F).texOffs(94, 34).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 1.0F, 8.0F).texOffs(0, 36).addBox(-4.5F, 4.0F, -4.5F, 9.0F, 2.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(120, 19).addBox(-1.0F, -2.0F, -4.0F, 2.0F, 2.0F, 2.0F).texOffs(120, 23).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(120, 43).addBox(-1.0F, -2.0F, 2.0F, 2.0F, 2.0F, 2.0F).texOffs(48, 30).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(104, 0).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 6.0F, 4.0F).texOffs(94, 43).addBox(-4.5F, -4.5F, -3.5F, 9.0F, 2.0F, 4.0F).texOffs(28, 49).addBox(-2.0F, 0.5F, -5.5F, 4.0F, 3.0F, 2.0F).texOffs(48, 21).addBox(-2.0F, 3.5F, -5.0F, 1.0F, 2.0F, 1.0F).texOffs(48, 24).addBox(1.0F, 3.5F, -5.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(118, 15).addBox(-3.0F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(48, 27).addBox(-3.0F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, -3.0F, -1.5F, 0.0F, 0.0F, 0.3491F));
        p_head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(96, 49).addBox(0.0F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(124, 27).addBox(2.0F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(4.0F, -3.0F, -1.5F, 0.0F, 0.0F, -0.3491F));
        p_body.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(36, 43).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(28, 47).addBox(-1.5F, 2.0F, -2.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 6.0F, -3.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(48, 47).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(84, 47).addBox(-1.5F, 2.0F, -2.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(4.0F, 6.0F, -3.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(60, 47).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(120, 47).addBox(-1.5F, 2.0F, -2.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 6.0F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(72, 47).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(40, 49).addBox(-1.5F, 2.0F, -2.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(4.0F, 6.0F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(118, 10).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 6.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("snow_1", CubeListBuilder.create().texOffs(104, 10).addBox(-7.5F, -4.0F, -3.0F, 2.0F, 4.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("snow_2", CubeListBuilder.create().texOffs(108, 19).addBox(5.5F, -3.0F, 0.5F, 2.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("snow_3", CubeListBuilder.create().texOffs(108, 27).addBox(1.5F, -8.5F, 1.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("snow_4", CubeListBuilder.create().texOffs(84, 49).addBox(-5.5F, -6.0F, -7.5F, 4.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("snow_5", CubeListBuilder.create().texOffs(0, 47).addBox(1.0F, -4.0F, 5.5F, 5.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("snow_6", CubeListBuilder.create().texOffs(36, 36).addBox(5.5F, -7.0F, -4.0F, 2.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("snow_7", CubeListBuilder.create().texOffs(14, 47).addBox(-6.5F, -7.5F, 0.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> SNOWROLLER_ONLY = Map.of();

    public static LayerDefinition shiverwraith() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 12).addBox(-4.0F, -6.0F, -2.5F, 8.0F, 8.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(54, 38).addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.0F, -2.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_robe = p_body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(32, 0).addBox(-4.5F, 0.0F, -3.0F, 9.0F, 6.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_robe.addOrReplaceChild("tatters", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, 0.0F, -2.5F, 8.0F, 6.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F).texOffs(0, 27).addBox(-4.5F, -8.5F, -4.6F, 9.0F, 2.0F, 3.0F).texOffs(26, 25).addBox(-2.5F, -9.5F, -0.5F, 5.0F, 2.0F, 5.0F).texOffs(42, 40).addBox(-1.5F, -9.0F, 4.0F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -6.0F, -0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_mantle = p_body.addOrReplaceChild("mantle", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_mantle.addOrReplaceChild("shard_0", CubeListBuilder.create().texOffs(46, 25).addBox(-1.0F, -13.0F, -1.0F, 2.0F, 13.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 2.5F, -0.4189F, 0.0F, 0.0F));
        p_mantle.addOrReplaceChild("shard_1", CubeListBuilder.create().texOffs(54, 25).addBox(-1.0F, -11.0F, -1.0F, 2.0F, 11.0F, 2.0F), PartPose.offsetAndRotation(-2.4F, 0.0F, 2.0F, -0.3142F, 0.0F, -0.3491F));
        p_mantle.addOrReplaceChild("shard_2", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -11.0F, -1.0F, 2.0F, 11.0F, 2.0F), PartPose.offsetAndRotation(2.4F, 0.0F, 2.0F, -0.3142F, 0.0F, 0.3491F));
        p_mantle.addOrReplaceChild("shard_3", CubeListBuilder.create().texOffs(58, 12).addBox(-1.0F, -9.0F, -0.5F, 2.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 0.5F, 1.0F, -0.1745F, 0.0F, -0.733F));
        p_mantle.addOrReplaceChild("shard_4", CubeListBuilder.create().texOffs(36, 32).addBox(-1.0F, -9.0F, -0.5F, 2.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(4.0F, 0.5F, 1.0F, -0.1745F, 0.0F, 0.733F));
        p_mantle.addOrReplaceChild("shard_5", CubeListBuilder.create().texOffs(26, 16).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 1.0F, -1.2F, 0.1745F, 0.0F, -1.0821F));
        p_mantle.addOrReplaceChild("shard_6", CubeListBuilder.create().texOffs(42, 32).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(4.0F, 1.0F, -1.2F, 0.1745F, 0.0F, 1.0821F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(8, 32).addBox(-1.5F, -1.0F, -1.0F, 2.0F, 9.0F, 2.0F).texOffs(24, 32).addBox(-2.0F, 6.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-4.8F, -5.0F, 0.0F, 0.0F, 0.0F, 0.1396F));
        p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(54, 42).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(36, 42).addBox(-1.0F, 2.0F, -1.0F, 1.0F, 3.0F, 1.0F).texOffs(58, 22).addBox(0.0F, 2.0F, 0.0F, 1.0F, 2.0F, 1.0F).texOffs(20, 43).addBox(-1.0F, 2.0F, 0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-0.5F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(16, 32).addBox(-0.5F, -1.0F, -1.0F, 2.0F, 9.0F, 2.0F).texOffs(24, 38).addBox(-1.0F, 6.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(4.8F, -5.0F, 0.0F, 0.0F, 0.0F, -0.1396F));
        p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(8, 43).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(16, 43).addBox(-1.0F, 2.0F, -1.0F, 1.0F, 3.0F, 1.0F).texOffs(24, 44).addBox(0.0F, 2.0F, 0.0F, 1.0F, 2.0F, 1.0F).texOffs(28, 44).addBox(-1.0F, 2.0F, 0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.5F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> SHIVERWRAITH_ONLY = Map.of();

    public static LayerDefinition gloamhound() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(30, 12).addBox(-3.5F, -4.0F, -8.0F, 7.0F, 7.0F, 5.0F).texOffs(28, 32).addBox(-3.0F, -5.0F, -7.0F, 6.0F, 1.0F, 4.0F).texOffs(0, 14).addBox(-2.5F, -3.0F, -3.0F, 5.0F, 4.0F, 6.0F).texOffs(20, 24).addBox(-2.5F, -3.0F, 3.0F, 5.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 13.5F, 0.0F, -0.2618F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_0", CubeListBuilder.create().texOffs(0, 50).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -4.0F, -6.5F, -0.6109F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_1", CubeListBuilder.create().texOffs(6, 50).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -4.0F, -4.0F, -0.6109F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_2", CubeListBuilder.create().texOffs(52, 50).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -3.0F, -1.5F, -0.6109F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_3", CubeListBuilder.create().texOffs(58, 50).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 1.0F, -0.6109F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_4", CubeListBuilder.create().texOffs(22, 21).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 3.5F, -0.6109F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_5", CubeListBuilder.create().texOffs(26, 21).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 6.0F, -0.6109F, 0.0F, 0.0F));
        PartDefinition p_mane = p_body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(30, 0).addBox(-4.5F, -5.0F, -8.5F, 9.0F, 8.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_mane.addOrReplaceChild("collar_r0", CubeListBuilder.create().texOffs(20, 50).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, -4.5F, -7.5F, -0.3491F, 0.0F, -0.6981F));
        p_mane.addOrReplaceChild("collar_r1", CubeListBuilder.create().texOffs(24, 53).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, -1.5F, -8.0F, -0.3491F, 0.0F, -1.2217F));
        p_mane.addOrReplaceChild("collar_r2", CubeListBuilder.create().texOffs(28, 53).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 1.5F, -7.5F, -0.3491F, 0.0F, -1.7453F));
        p_mane.addOrReplaceChild("collar_l0", CubeListBuilder.create().texOffs(32, 53).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(4.0F, -4.5F, -7.5F, -0.3491F, 0.0F, 0.6981F));
        p_mane.addOrReplaceChild("collar_l1", CubeListBuilder.create().texOffs(12, 54).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(4.0F, -1.5F, -8.0F, -0.3491F, 0.0F, 1.2217F));
        p_mane.addOrReplaceChild("collar_l2", CubeListBuilder.create().texOffs(16, 54).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(4.0F, 1.5F, -7.5F, -0.3491F, 0.0F, 1.7453F));
        p_mane.addOrReplaceChild("runes", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -3.0F, -3.0F, 5.0F, 4.0F, 10.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(60, 31).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 8.0F, 1.0F).texOffs(12, 50).addBox(-1.0F, 7.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 7.0F, 0.9599F, 0.0F, 0.0F));
        PartDefinition p_head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 24).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 4.0F).texOffs(26, 45).addBox(-2.0F, -3.0F, -3.0F, 4.0F, 1.0F, 3.0F).texOffs(38, 24).addBox(-1.5F, -1.0F, -10.0F, 3.0F, 2.0F, 6.0F).texOffs(40, 40).addBox(-1.0F, -1.5F, -9.0F, 2.0F, 1.0F, 5.0F).texOffs(40, 37).addBox(-1.7F, 1.0F, -9.5F, 1.0F, 2.0F, 1.0F).texOffs(44, 37).addBox(0.7F, 1.0F, -9.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 12.5F, -8.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 40).addBox(-1.5F, 0.0F, -5.0F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 1.0F, -4.0F, 0.2094F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(54, 18).addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-2.0F, -1.5F, -1.0F, 0.5236F, -0.3491F, 0.0F));
        p_head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(16, 40).addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(2.0F, -1.5F, -1.0F, 0.5236F, 0.3491F, 0.0F));
        PartDefinition p_crown = p_head.addOrReplaceChild("crown", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -2.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        p_crown.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(54, 40).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F).texOffs(36, 50).addBox(-0.5F, -0.5F, 3.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.8F, 0.0F, 0.0F, 0.6109F, -0.2618F, 0.0F));
        p_crown.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(54, 45).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F).texOffs(44, 50).addBox(-0.5F, -0.5F, 3.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.8F, 0.0F, 0.0F, 0.6109F, 0.2618F, 0.0F));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(48, 32).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 5.0F, 3.0F).texOffs(56, 0).addBox(-1.0F, 3.5F, -1.0F, 2.0F, 7.0F, 2.0F).texOffs(0, 46).addBox(-1.5F, 10.5F, -2.0F, 3.0F, 1.0F, 3.0F).texOffs(36, 54).addBox(-1.5F, 10.5F, -3.0F, 1.0F, 1.0F, 1.0F).texOffs(40, 54).addBox(0.5F, 10.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-2.2F, 12.5F, -6.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(28, 37).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 5.0F, 3.0F).texOffs(56, 9).addBox(-1.0F, 3.5F, -1.0F, 2.0F, 7.0F, 2.0F).texOffs(12, 46).addBox(-1.5F, 10.5F, -2.0F, 3.0F, 1.0F, 3.0F).texOffs(44, 54).addBox(-1.5F, 10.5F, -3.0F, 1.0F, 1.0F, 1.0F).texOffs(48, 54).addBox(0.5F, 10.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.2F, 12.5F, -6.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 4.0F, 4.0F).texOffs(22, 14).addBox(-1.0F, 2.5F, -0.5F, 2.0F, 5.0F, 2.0F).texOffs(40, 46).addBox(-1.5F, 6.5F, -2.0F, 3.0F, 1.0F, 3.0F).texOffs(52, 55).addBox(-1.5F, 6.5F, -3.0F, 1.0F, 1.0F, 1.0F).texOffs(56, 55).addBox(0.5F, 6.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-2.2F, 16.5F, 5.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(14, 32).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 4.0F, 4.0F).texOffs(56, 24).addBox(-1.0F, 2.5F, -0.5F, 2.0F, 5.0F, 2.0F).texOffs(24, 49).addBox(-1.5F, 6.5F, -2.0F, 3.0F, 1.0F, 3.0F).texOffs(60, 55).addBox(-1.5F, 6.5F, -3.0F, 1.0F, 1.0F, 1.0F).texOffs(0, 56).addBox(0.5F, 6.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.2F, 16.5F, 5.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> GLOAMHOUND_ONLY = Map.of();

    public static LayerDefinition vinedrop_strangler() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_core = root.addOrReplaceChild("core", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 14.4F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = p_core.addOrReplaceChild("body", CubeListBuilder.create().texOffs(38, 0).addBox(-3.0F, -10.0F, -2.5F, 6.0F, 10.0F, 5.0F).texOffs(0, 0).addBox(-5.0F, -10.2F, -4.5F, 10.0F, 0.0F, 9.0F).texOffs(28, 15).addBox(-4.5F, -3.0F, -4.0F, 9.0F, 0.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 3.6F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("heart", CubeListBuilder.create().texOffs(24, 31).addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -6.0F, -2.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 9).addBox(-3.5F, -5.0F, -3.5F, 7.0F, 5.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(32, 23).addBox(-3.0F, 0.0F, -6.5F, 6.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 23).addBox(-4.0F, -0.2F, -4.0F, 8.0F, 0.0F, 8.0F).texOffs(0, 37).addBox(-3.0F, -4.0F, 0.0F, 6.0F, 4.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_shoots = p_head.addOrReplaceChild("shoots", CubeListBuilder.create().texOffs(0, 21).addBox(-3.5F, -5.0F, -0.5F, 4.0F, 2.0F, 0.0F).texOffs(8, 21).addBox(0.5F, -3.5F, 0.5F, 4.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_shoots.addOrReplaceChild("shoot_a", CubeListBuilder.create().texOffs(60, 0).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(-1.5F, 0.0F, 0.5F, 0.0F, 0.0F, 0.2094F));
        p_shoots.addOrReplaceChild("shoot_b", CubeListBuilder.create().texOffs(26, 35).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(1.5F, 0.0F, -0.5F, 0.1396F, 0.0F, -0.1745F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(56, 23).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F).texOffs(62, 7).addBox(-1.2F, 0.0F, -0.5F, 0.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(-3.5F, -9.0F, 0.0F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_forearm_right = p_arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(40, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F).texOffs(60, 7).addBox(-0.1F, 1.0F, -1.5F, 0.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, -0.1396F, 0.0F, 0.0F));
        PartDefinition p_hand_right = p_forearm_right.addOrReplaceChild("hand_right", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("finger_right_0", CubeListBuilder.create().texOffs(56, 35).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-0.6F, 0.0F, -0.6F, -0.4363F, 0.0F, 0.1396F));
        p_hand_right.addOrReplaceChild("finger_right_1", CubeListBuilder.create().texOffs(60, 35).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.6F, 0.0F, -0.6F, -0.4363F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("finger_right_2", CubeListBuilder.create().texOffs(12, 37).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.6F, 0.3491F, 0.0F, -0.1396F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(32, 30).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F).texOffs(24, 35).addBox(1.2F, 0.0F, -0.5F, 0.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(3.5F, -9.0F, 0.0F, 0.0F, 0.0F, -0.1745F));
        PartDefinition p_forearm_left = p_arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(48, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F).texOffs(36, 9).addBox(0.1F, 1.0F, -1.5F, 0.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, -0.1396F, 0.0F, 0.0F));
        PartDefinition p_hand_left = p_forearm_left.addOrReplaceChild("hand_left", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_left.addOrReplaceChild("finger_left_0", CubeListBuilder.create().texOffs(16, 37).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-0.6F, 0.0F, -0.6F, -0.4363F, 0.0F, -0.1396F));
        p_hand_left.addOrReplaceChild("finger_left_1", CubeListBuilder.create().texOffs(20, 37).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.6F, 0.0F, -0.6F, -0.4363F, 0.0F, 0.0F));
        p_hand_left.addOrReplaceChild("finger_left_2", CubeListBuilder.create().texOffs(40, 38).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.6F, 0.3491F, 0.0F, 0.1396F));
        PartDefinition p_tendril_fr = p_body.addOrReplaceChild("tendril_fr", CubeListBuilder.create().texOffs(28, 9).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(-1.8F, 0.0F, -1.4F, 0.6981F, -2.3562F, 0.0F));
        p_tendril_fr.addOrReplaceChild("tendril_fr_tip", CubeListBuilder.create().texOffs(44, 38).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, -0.4363F, 0.0F, 0.0F));
        PartDefinition p_tendril_fl = p_body.addOrReplaceChild("tendril_fl", CubeListBuilder.create().texOffs(0, 31).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(1.8F, 0.0F, -1.4F, 0.6981F, 2.3562F, 0.0F));
        p_tendril_fl.addOrReplaceChild("tendril_fl_tip", CubeListBuilder.create().texOffs(48, 38).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, -0.4363F, 0.0F, 0.0F));
        PartDefinition p_tendril_br = p_body.addOrReplaceChild("tendril_br", CubeListBuilder.create().texOffs(8, 31).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(-1.8F, 0.0F, 1.4F, 0.6981F, -0.7854F, 0.0F));
        p_tendril_br.addOrReplaceChild("tendril_br_tip", CubeListBuilder.create().texOffs(52, 38).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, -0.4363F, 0.0F, 0.0F));
        PartDefinition p_tendril_bl = p_body.addOrReplaceChild("tendril_bl", CubeListBuilder.create().texOffs(16, 31).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(1.8F, 0.0F, 1.4F, 0.6981F, 0.7854F, 0.0F));
        p_tendril_bl.addOrReplaceChild("tendril_bl_tip", CubeListBuilder.create().texOffs(30, 39).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, -0.4363F, 0.0F, 0.0F));
        p_core.addOrReplaceChild("rope", CubeListBuilder.create().texOffs(56, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -9.6F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    /** Parts shown only for some variants of vinedrop_strangler. */
    public static final Map<String, String[]> VINEDROP_STRANGLER_ONLY = Map.ofEntries(
            Map.entry("crown", new String[] {"base"}),
            Map.entry("shoots", new String[] {"bamboo_strangler"}));

    public static LayerDefinition snapmawBloom() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_leaf_0 = root.addOrReplaceChild("leaf_0", CubeListBuilder.create().texOffs(76, 15).addBox(-2.5F, -0.5F, -7.0F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 23.2F, 0.0F, -0.5236F, 0.6283F, 0.0F));
        p_leaf_0.addOrReplaceChild("leaf_0_tip", CubeListBuilder.create().texOffs(96, 15).addBox(-1.5F, -0.5F, -4.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.733F, 0.0F, 0.0F));
        PartDefinition p_leaf_1 = root.addOrReplaceChild("leaf_1", CubeListBuilder.create().texOffs(28, 20).addBox(-2.5F, -0.5F, -7.0F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 23.07F, 0.0F, -0.5236F, 1.885F, 0.0F));
        p_leaf_1.addOrReplaceChild("leaf_1_tip", CubeListBuilder.create().texOffs(20, 26).addBox(-1.5F, -0.5F, -4.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.733F, 0.0F, 0.0F));
        PartDefinition p_leaf_2 = root.addOrReplaceChild("leaf_2", CubeListBuilder.create().texOffs(48, 21).addBox(-2.5F, -0.5F, -7.0F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 22.94F, 0.0F, -0.5236F, 3.1416F, 0.0F));
        p_leaf_2.addOrReplaceChild("leaf_2_tip", CubeListBuilder.create().texOffs(34, 26).addBox(-1.5F, -0.5F, -4.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.733F, 0.0F, 0.0F));
        PartDefinition p_leaf_3 = root.addOrReplaceChild("leaf_3", CubeListBuilder.create().texOffs(68, 21).addBox(-2.5F, -0.5F, -7.0F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 22.81F, 0.0F, -0.5236F, 4.3982F, 0.0F));
        p_leaf_3.addOrReplaceChild("leaf_3_tip", CubeListBuilder.create().texOffs(48, 27).addBox(-1.5F, -0.5F, -4.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.733F, 0.0F, 0.0F));
        PartDefinition p_leaf_4 = root.addOrReplaceChild("leaf_4", CubeListBuilder.create().texOffs(88, 21).addBox(-2.5F, -0.5F, -7.0F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 22.68F, 0.0F, -0.5236F, 5.6549F, 0.0F));
        p_leaf_4.addOrReplaceChild("leaf_4_tip", CubeListBuilder.create().texOffs(62, 27).addBox(-1.5F, -0.5F, -4.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.733F, 0.0F, 0.0F));
        PartDefinition p_stem = root.addOrReplaceChild("stem", CubeListBuilder.create().texOffs(110, 8).addBox(-2.0F, -9.0F, -2.0F, 4.0F, 9.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_neck = p_stem.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(16, 13).addBox(-1.5F, -6.5F, -1.5F, 3.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, -0.1745F, 0.0F, 0.0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.1745F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw_upper", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -4.0F, -9.0F, 12.0F, 4.0F, 9.0F).texOffs(80, 0).addBox(-5.0F, -5.15F, -8.0F, 10.0F, 1.0F, 7.0F).texOffs(76, 27).addBox(-5.5F, 0.0F, -9.0F, 11.0F, 2.0F, 0.0F).texOffs(60, 11).addBox(-6.0F, 0.0F, -9.0F, 0.0F, 2.0F, 8.0F).texOffs(0, 13).addBox(6.0F, 0.0F, -9.0F, 0.0F, 2.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw_lower", CubeListBuilder.create().texOffs(42, 0).addBox(-5.5F, 0.15F, -8.5F, 11.0F, 3.0F, 8.0F).texOffs(80, 8).addBox(-4.5F, 3.3F, -7.5F, 9.0F, 1.0F, 6.0F).texOffs(76, 29).addBox(-4.5F, -1.85F, -8.3F, 9.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("sac", CubeListBuilder.create().texOffs(42, 11).addBox(-2.5F, -2.5F, 0.2F, 5.0F, 5.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -2.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("petal_0", CubeListBuilder.create().texOffs(114, 0).addBox(-2.0F, -9.0F, 0.0F, 4.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.5F, 0.9F, -0.4887F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("petal_1", CubeListBuilder.create().texOffs(108, 21).addBox(-2.0F, -9.0F, 0.0F, 4.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.5F, 0.9F, -0.4887F, 0.0F, 1.2566F));
        p_head.addOrReplaceChild("petal_2", CubeListBuilder.create().texOffs(118, 21).addBox(-2.0F, -9.0F, 0.0F, 4.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.5F, 0.9F, -0.4887F, 0.0F, 2.5133F));
        p_head.addOrReplaceChild("petal_3", CubeListBuilder.create().texOffs(0, 23).addBox(-2.0F, -9.0F, 0.0F, 4.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.5F, 0.9F, -0.4887F, 0.0F, 3.7699F));
        p_head.addOrReplaceChild("petal_4", CubeListBuilder.create().texOffs(10, 23).addBox(-2.0F, -9.0F, 0.0F, 4.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.5F, 0.9F, -0.4887F, 0.0F, 5.0265F));
        p_head.addOrReplaceChild("seed", CubeListBuilder.create().texOffs(28, 13).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.6F, -4.5F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> SNAPMAW_BLOOM_ONLY = Map.of();

    public static LayerDefinition stormeel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(18, 0).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 20.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("dorsal_0", CubeListBuilder.create().texOffs(58, 7).addBox(0.0F, -2.0F, 1.0F, 0.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -4.0F, 5.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 8).addBox(-2.0F, 0.15F, -3.8F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 1.5F, -0.2F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(14, 14).addBox(-2.0F, 0.0F, 0.0F, 2.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, 1.0F, 1.0F, 0.0F, 0.4363F, -0.3491F));
        p_body.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(34, 14).addBox(0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(2.0F, 1.0F, 1.0F, 0.0F, -0.4363F, 0.3491F));
        PartDefinition p_seg1 = p_body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(34, 0).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 0.1396F, 0.0F));
        p_seg1.addOrReplaceChild("dorsal_1", CubeListBuilder.create().texOffs(38, 8).addBox(0.0F, -2.0F, 0.0F, 0.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_seg2 = p_seg1.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(50, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, -0.2443F, 0.0F));
        p_seg2.addOrReplaceChild("dorsal_2", CubeListBuilder.create().texOffs(0, 13).addBox(0.0F, -2.0F, 0.0F, 0.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_seg2.addOrReplaceChild("ventral_2", CubeListBuilder.create().texOffs(8, 13).addBox(0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_seg3 = p_seg2.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(16, 8).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, -0.1047F, 0.0F));
        p_seg3.addOrReplaceChild("dorsal_3", CubeListBuilder.create().texOffs(58, 12).addBox(0.0F, -2.0F, 0.0F, 0.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_seg3.addOrReplaceChild("ventral_3", CubeListBuilder.create().texOffs(28, 13).addBox(0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tail = p_seg3.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(28, 8).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.2094F, 0.0F));
        p_tail.addOrReplaceChild("tail_fin", CubeListBuilder.create().texOffs(50, 7).addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }
    public static final Map<String, String[]> STORMEEL_ONLY = Map.of();

    public static LayerDefinition shellguard_crab() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -4.0F, -5.0F, 12.0F, 5.0F, 10.0F).texOffs(0, 15).addBox(-5.0F, -5.5F, -4.0F, 10.0F, 2.0F, 8.0F).texOffs(0, 25).addBox(-5.0F, 0.5F, -4.0F, 10.0F, 2.0F, 8.0F).texOffs(0, 35).addBox(-7.0F, -3.5F, -3.5F, 14.0F, 3.0F, 6.0F).texOffs(58, 15).addBox(5.5F, -3.0F, -4.8F, 2.0F, 1.0F, 1.0F).texOffs(58, 17).addBox(-7.5F, -3.0F, -4.8F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 19.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(56, 8).addBox(-2.5F, -3.0F, -0.5F, 1.0F, 4.0F, 1.0F).texOffs(56, 0).addBox(-3.0F, -4.5F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(60, 8).addBox(1.5F, -3.0F, -0.5F, 1.0F, 4.0F, 1.0F).texOffs(56, 4).addBox(1.0F, -4.5F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -4.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_claw_left = p_body.addOrReplaceChild("claw_left", CubeListBuilder.create().texOffs(44, 0).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(5.2F, -1.0F, -4.5F, 0.0F, 0.5236F, 0.0F));
        PartDefinition p_claw_left_hand = p_claw_left.addOrReplaceChild("claw_left_hand", CubeListBuilder.create().texOffs(36, 15).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 6.0F).texOffs(14, 56).addBox(-1.0F, -0.2F, -8.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, -0.5236F, 0.0F));
        p_claw_left_hand.addOrReplaceChild("claw_left_finger", CubeListBuilder.create().texOffs(24, 59).addBox(-1.0F, -1.4F, -2.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_claw_right = p_body.addOrReplaceChild("claw_right", CubeListBuilder.create().texOffs(44, 6).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-5.2F, -1.0F, -4.5F, 0.0F, -0.5236F, 0.0F));
        PartDefinition p_claw_right_hand = p_claw_right.addOrReplaceChild("claw_right_hand", CubeListBuilder.create().texOffs(36, 25).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 6.0F).texOffs(34, 59).addBox(-1.0F, -0.2F, -8.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, 0.5236F, 0.0F));
        p_claw_right_hand.addOrReplaceChild("claw_right_finger", CubeListBuilder.create().texOffs(44, 59).addBox(-1.0F, -1.4F, -2.5F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_left_1 = p_body.addOrReplaceChild("leg_left_1", CubeListBuilder.create(), PartPose.offsetAndRotation(5.5F, 0.5F, -3.2F, 0.0F, 0.5934F, 0.0F));
        PartDefinition p_leg_left_1_thigh = p_leg_left_1.addOrReplaceChild("leg_left_1_thigh", CubeListBuilder.create().texOffs(16, 48).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.6109F));
        p_leg_left_1_thigh.addOrReplaceChild("leg_left_1_shin", CubeListBuilder.create().texOffs(40, 35).addBox(-0.5F, -0.85F, -0.85F, 6.0F, 2.0F, 2.0F).texOffs(44, 12).addBox(5.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6581F));
        PartDefinition p_leg_left_2 = p_body.addOrReplaceChild("leg_left_2", CubeListBuilder.create(), PartPose.offsetAndRotation(5.5F, 0.5F, -1.1F, 0.0F, 0.2094F, 0.0F));
        PartDefinition p_leg_left_2_thigh = p_leg_left_2.addOrReplaceChild("leg_left_2_thigh", CubeListBuilder.create().texOffs(30, 51).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.6109F));
        p_leg_left_2_thigh.addOrReplaceChild("leg_left_2_shin", CubeListBuilder.create().texOffs(40, 39).addBox(-0.5F, -0.85F, -0.85F, 6.0F, 2.0F, 2.0F).texOffs(52, 13).addBox(5.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6581F));
        PartDefinition p_leg_left_3 = p_body.addOrReplaceChild("leg_left_3", CubeListBuilder.create(), PartPose.offsetAndRotation(5.5F, 0.5F, 1.1F, 0.0F, -0.2094F, 0.0F));
        PartDefinition p_leg_left_3_thigh = p_leg_left_3.addOrReplaceChild("leg_left_3_thigh", CubeListBuilder.create().texOffs(44, 51).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.6109F));
        p_leg_left_3_thigh.addOrReplaceChild("leg_left_3_shin", CubeListBuilder.create().texOffs(40, 43).addBox(-0.5F, -0.85F, -0.85F, 6.0F, 2.0F, 2.0F).texOffs(56, 35).addBox(5.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6581F));
        PartDefinition p_leg_left_4 = p_body.addOrReplaceChild("leg_left_4", CubeListBuilder.create(), PartPose.offsetAndRotation(5.5F, 0.5F, 3.2F, 0.0F, -0.5934F, 0.0F));
        PartDefinition p_leg_left_4_thigh = p_leg_left_4.addOrReplaceChild("leg_left_4_thigh", CubeListBuilder.create().texOffs(0, 52).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.6109F));
        p_leg_left_4_thigh.addOrReplaceChild("leg_left_4_shin", CubeListBuilder.create().texOffs(0, 44).addBox(-0.5F, -0.85F, -0.85F, 6.0F, 2.0F, 2.0F).texOffs(56, 37).addBox(5.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6581F));
        PartDefinition p_leg_right_1 = p_body.addOrReplaceChild("leg_right_1", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.5F, 0.5F, -3.2F, 0.0F, -0.5934F, 0.0F));
        PartDefinition p_leg_right_1_thigh = p_leg_right_1.addOrReplaceChild("leg_right_1_thigh", CubeListBuilder.create().texOffs(14, 52).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6109F));
        p_leg_right_1_thigh.addOrReplaceChild("leg_right_1_shin", CubeListBuilder.create().texOffs(16, 44).addBox(-5.5F, -0.85F, -0.85F, 6.0F, 2.0F, 2.0F).texOffs(56, 39).addBox(-8.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6581F));
        PartDefinition p_leg_right_2 = p_body.addOrReplaceChild("leg_right_2", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.5F, 0.5F, -1.1F, 0.0F, -0.2094F, 0.0F));
        PartDefinition p_leg_right_2_thigh = p_leg_right_2.addOrReplaceChild("leg_right_2_thigh", CubeListBuilder.create().texOffs(28, 55).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6109F));
        p_leg_right_2_thigh.addOrReplaceChild("leg_right_2_shin", CubeListBuilder.create().texOffs(32, 47).addBox(-5.5F, -0.85F, -0.85F, 6.0F, 2.0F, 2.0F).texOffs(56, 41).addBox(-8.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6581F));
        PartDefinition p_leg_right_3 = p_body.addOrReplaceChild("leg_right_3", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.5F, 0.5F, 1.1F, 0.0F, 0.2094F, 0.0F));
        PartDefinition p_leg_right_3_thigh = p_leg_right_3.addOrReplaceChild("leg_right_3_thigh", CubeListBuilder.create().texOffs(42, 55).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6109F));
        p_leg_right_3_thigh.addOrReplaceChild("leg_right_3_shin", CubeListBuilder.create().texOffs(48, 47).addBox(-5.5F, -0.85F, -0.85F, 6.0F, 2.0F, 2.0F).texOffs(56, 43).addBox(-8.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6581F));
        PartDefinition p_leg_right_4 = p_body.addOrReplaceChild("leg_right_4", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.5F, 0.5F, 3.2F, 0.0F, 0.5934F, 0.0F));
        PartDefinition p_leg_right_4_thigh = p_leg_right_4.addOrReplaceChild("leg_right_4_thigh", CubeListBuilder.create().texOffs(0, 56).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6109F));
        p_leg_right_4_thigh.addOrReplaceChild("leg_right_4_shin", CubeListBuilder.create().texOffs(0, 48).addBox(-5.5F, -0.85F, -0.85F, 6.0F, 2.0F, 2.0F).texOffs(32, 44).addBox(-8.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6581F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> SHELLGUARD_CRAB_ONLY = Map.of();

    public static LayerDefinition dripfang() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(40, 11).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 1.0F, 6.0F).texOffs(0, 0).addBox(-5.0F, 0.35F, -5.0F, 10.0F, 3.0F, 10.0F).texOffs(0, 13).addBox(-4.0F, 2.85F, -4.0F, 8.0F, 5.0F, 8.0F).texOffs(40, 0).addBox(-3.0F, 7.35F, -3.0F, 6.0F, 5.0F, 6.0F).texOffs(32, 13).addBox(-4.6F, 4.0F, -1.0F, 1.0F, 3.0F, 2.0F).texOffs(24, 26).addBox(3.6F, 8.5F, 0.0F, 1.0F, 2.0F, 2.0F).texOffs(30, 27).addBox(-1.0F, 5.0F, 3.6F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.75F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(32, 18).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F).texOffs(0, 26).addBox(-1.0F, 4.5F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(36, 27).addBox(-0.5F, 8.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 11.85F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(24, 30).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 1.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 1.35F, -5.15F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_hip_0 = p_body.addOrReplaceChild("hip_0", CubeListBuilder.create(), PartPose.offsetAndRotation(4.2426F, 1.75F, 4.2426F, 0.0F, 0.7854F, 0.0F));
        PartDefinition p_thigh_0 = p_hip_0.addOrReplaceChild("thigh_0", CubeListBuilder.create().texOffs(8, 26).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 2.0944F, 0.0F, 0.0F));
        p_thigh_0.addOrReplaceChild("shin_0", CubeListBuilder.create().texOffs(48, 18).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 26.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.5F, 0.0F, -1.8326F, 0.0F, 0.0F));
        PartDefinition p_hip_1 = p_body.addOrReplaceChild("hip_1", CubeListBuilder.create(), PartPose.offsetAndRotation(4.2426F, 1.75F, -4.2426F, 0.0F, 2.3562F, 0.0F));
        PartDefinition p_thigh_1 = p_hip_1.addOrReplaceChild("thigh_1", CubeListBuilder.create().texOffs(12, 26).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 2.0944F, 0.0F, 0.0F));
        p_thigh_1.addOrReplaceChild("shin_1", CubeListBuilder.create().texOffs(52, 18).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 26.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.5F, 0.0F, -1.8326F, 0.0F, 0.0F));
        PartDefinition p_hip_2 = p_body.addOrReplaceChild("hip_2", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.2426F, 1.75F, -4.2426F, 0.0F, 3.927F, 0.0F));
        PartDefinition p_thigh_2 = p_hip_2.addOrReplaceChild("thigh_2", CubeListBuilder.create().texOffs(16, 26).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 2.0944F, 0.0F, 0.0F));
        p_thigh_2.addOrReplaceChild("shin_2", CubeListBuilder.create().texOffs(56, 18).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 26.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.5F, 0.0F, -1.8326F, 0.0F, 0.0F));
        PartDefinition p_hip_3 = p_body.addOrReplaceChild("hip_3", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.2426F, 1.75F, 4.2426F, 0.0F, 5.4978F, 0.0F));
        PartDefinition p_thigh_3 = p_hip_3.addOrReplaceChild("thigh_3", CubeListBuilder.create().texOffs(20, 26).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 2.0944F, 0.0F, 0.0F));
        p_thigh_3.addOrReplaceChild("shin_3", CubeListBuilder.create().texOffs(60, 18).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 26.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.5F, 0.0F, -1.8326F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> DRIPFANG_ONLY = Map.of();

    public static LayerDefinition hushwing() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 7).addBox(-2.0F, -2.0F, -2.5F, 4.0F, 4.0F, 5.0F).texOffs(16, 19).addBox(-2.5F, -2.3F, -2.8F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 19.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(42, 7).addBox(-2.0F, -2.0F, -3.0F, 4.0F, 4.0F, 3.0F).texOffs(56, 7).addBox(-1.0F, 0.0F, -3.8F, 2.0F, 1.0F, 1.0F).texOffs(56, 9).addBox(-1.0F, -1.8F, -3.9F, 2.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -0.5F, -2.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -8.0F, 0.0F, 4.0F, 8.0F, 0.0F).texOffs(8, 16).addBox(-2.0F, -8.0F, 0.25F, 4.0F, 8.0F, 0.0F), PartPose.offsetAndRotation(-1.2F, -1.8F, -1.2F, -0.1396F, 0.1745F, -0.2793F));
        p_head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(42, 18).addBox(-2.0F, -8.0F, 0.0F, 4.0F, 8.0F, 0.0F).texOffs(50, 18).addBox(-2.0F, -8.0F, 0.25F, 4.0F, 8.0F, 0.0F), PartPose.offsetAndRotation(1.2F, -1.8F, -1.2F, -0.1396F, -0.1745F, 0.2793F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(42, 14).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 0.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 1.2F, 2.3F, -0.3491F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(60, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-1.2F, 1.6F, 2.0F, 0.4363F, 0.0F, 0.0F));
        PartDefinition p_wing_right = p_body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(18, 7).addBox(-6.0F, 0.0F, -0.5F, 6.0F, 0.0F, 6.0F), PartPose.offsetAndRotation(-2.0F, -1.5F, -1.2F, -0.4189F, 0.0F, 0.3491F));
        p_wing_right.addOrReplaceChild("wing_right_tip", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, 0.0F, -0.5F, 8.0F, 0.0F, 7.0F), PartPose.offsetAndRotation(-6.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(60, 3).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(1.2F, 1.6F, 2.0F, 0.4363F, 0.0F, 0.0F));
        PartDefinition p_wing_left = p_body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(18, 13).addBox(0.0F, 0.0F, -0.5F, 6.0F, 0.0F, 6.0F), PartPose.offsetAndRotation(2.0F, -1.5F, -1.2F, -0.4189F, 0.0F, -0.3491F));
        p_wing_left.addOrReplaceChild("wing_left_tip", CubeListBuilder.create().texOffs(30, 0).addBox(0.0F, 0.0F, -0.5F, 8.0F, 0.0F, 7.0F), PartPose.offsetAndRotation(6.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> HUSHWING_ONLY = Map.of();

    public static LayerDefinition myceliteShambler() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(50, 33).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F).texOffs(82, 38).addBox(-2.5F, 5.0F, -3.0F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(-2.6F, 17.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(66, 38).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F).texOffs(0, 39).addBox(-2.5F, 5.0F, -3.0F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(2.6F, 17.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 31).addBox(-4.0F, -3.0F, -2.5F, 8.0F, 3.0F, 5.0F).texOffs(72, 16).addBox(-5.0F, -11.0F, -3.5F, 10.0F, 8.0F, 7.0F).texOffs(68, 31).addBox(-4.0F, -12.0F, -3.0F, 8.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 17.0F, 1.0F, 0.3142F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("sac_back", CubeListBuilder.create().texOffs(20, 40).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(0.5F, -7.0F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("sac_low", CubeListBuilder.create().texOffs(118, 16).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(-2.5F, -3.5F, 2.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("sac_shoulder", CubeListBuilder.create().texOffs(36, 43).addBox(-2.0F, -2.5F, -2.0F, 4.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(-4.0F, -12.0F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(40, 20).addBox(-3.5F, -6.0F, -3.5F, 7.0F, 6.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -12.0F, -1.0F, -0.2443F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 0).addBox(-9.0F, -2.0F, -9.0F, 18.0F, 2.0F, 18.0F).texOffs(72, 0).addBox(-7.0F, -4.0F, -7.0F, 14.0F, 2.0F, 14.0F).texOffs(0, 20).addBox(-5.0F, -5.0F, -5.0F, 10.0F, 1.0F, 10.0F).texOffs(26, 33).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(106, 16).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 11.0F, 3.0F), PartPose.offsetAndRotation(-6.0F, -10.0F, -0.5F, -0.3142F, 0.0F, 0.1047F));
        p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(102, 44).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 3.0F, 4.0F).texOffs(68, 20).addBox(-1.5F, 3.0F, -1.5F, 1.0F, 2.0F, 1.0F).texOffs(118, 21).addBox(0.5F, 3.0F, 0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(106, 30).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 11.0F, 3.0F), PartPose.offsetAndRotation(6.0F, -10.0F, -0.5F, -0.3142F, 0.0F, -0.1047F));
        p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(82, 45).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 3.0F, 4.0F).texOffs(122, 21).addBox(-1.5F, 3.0F, -1.5F, 1.0F, 2.0F, 1.0F).texOffs(68, 23).addBox(0.5F, 3.0F, 0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> MYCELITE_SHAMBLER_ONLY = Map.of();

    public static LayerDefinition petalkin() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(60, 9).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-1.2F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(60, 14).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(1.2F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(48, 0).addBox(-2.0F, -4.8F, -2.0F, 4.0F, 5.0F, 4.0F).texOffs(24, 0).addBox(-3.0F, -2.5F, -3.0F, 6.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(60, 19).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(54, 15).addBox(-1.0F, 2.5F, -1.0F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, -4.2F, 0.0F, 0.0F, 0.0F, 0.4363F));
        p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(40, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(54, 19).addBox(0.0F, 2.5F, -1.0F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(2.0F, -4.2F, 0.0F, 0.0F, 0.0F, -0.4363F));
        PartDefinition p_head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("tress_right", CubeListBuilder.create().texOffs(0, 17).addBox(-1.5F, -0.5F, -4.5F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-1.4F, -4.6F, 2.7F, 1.117F, 2.9671F, 0.0F));
        p_head.addOrReplaceChild("tress_left", CubeListBuilder.create().texOffs(16, 20).addBox(-1.5F, -0.5F, -4.5F, 3.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(1.4F, -4.6F, 2.7F, 1.117F, 3.3161F, 0.0F));
        PartDefinition p_crown = p_head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(32, 21).addBox(-1.0F, -0.8F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_petal_0 = p_crown.addOrReplaceChild("petal_0", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.2F, 0.0F, 0.0F, 0.6283F, 0.0F));
        p_petal_0.addOrReplaceChild("petal_0_blade", CubeListBuilder.create().texOffs(24, 8).addBox(-2.0F, -0.5F, -5.5F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -0.6F, -0.1047F, 0.0F, 0.0F));
        PartDefinition p_petal_1 = p_crown.addOrReplaceChild("petal_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.34F, 0.0F, 0.0F, 1.885F, 0.0F));
        p_petal_1.addOrReplaceChild("petal_1_blade", CubeListBuilder.create().texOffs(42, 9).addBox(-2.0F, -0.5F, -5.5F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -0.6F, -0.1047F, 0.0F, 0.0F));
        PartDefinition p_petal_2 = p_crown.addOrReplaceChild("petal_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.48F, 0.0F, 0.0F, 3.1416F, 0.0F));
        p_petal_2.addOrReplaceChild("petal_2_blade", CubeListBuilder.create().texOffs(0, 11).addBox(-2.0F, -0.5F, -5.5F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -0.6F, -0.1047F, 0.0F, 0.0F));
        PartDefinition p_petal_3 = p_crown.addOrReplaceChild("petal_3", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.62F, 0.0F, 0.0F, 4.3982F, 0.0F));
        p_petal_3.addOrReplaceChild("petal_3_blade", CubeListBuilder.create().texOffs(18, 14).addBox(-2.0F, -0.5F, -5.5F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -0.6F, -0.1047F, 0.0F, 0.0F));
        PartDefinition p_petal_4 = p_crown.addOrReplaceChild("petal_4", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.76F, 0.0F, 0.0F, 5.6549F, 0.0F));
        p_petal_4.addOrReplaceChild("petal_4_blade", CubeListBuilder.create().texOffs(36, 15).addBox(-2.0F, -0.5F, -5.5F, 4.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -0.6F, -0.1047F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> PETALKIN_ONLY = Map.of();

    public static LayerDefinition hollowMourner() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(74, 26).addBox(-1.5F, 0.0F, -3.4F, 3.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(-2.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(0, 33).addBox(-1.5F, 0.0F, -3.4F, 3.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(2.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(94, 0).addBox(-4.0F, -9.0F, -2.5F, 8.0F, 9.0F, 5.0F).texOffs(94, 14).addBox(-4.5F, -9.5F, -3.0F, 9.0F, 4.0F, 6.0F).texOffs(116, 24).addBox(-2.6F, -8.5F, -3.4F, 2.0F, 15.0F, 1.0F).texOffs(122, 24).addBox(0.6F, -8.5F, -3.4F, 2.0F, 15.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("weeds", CubeListBuilder.create().texOffs(30, 0).addBox(-5.0F, 0.0F, -3.7F, 10.0F, 9.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -0.5F, -3.0F, 9.0F, 13.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(64, 0).addBox(-4.5F, -8.5F, -2.0F, 9.0F, 9.0F, 6.0F).texOffs(26, 31).addBox(-3.5F, -6.8F, -3.2F, 7.0F, 7.0F, 1.0F).texOffs(120, 0).addBox(-4.65F, -8.0F, -4.5F, 1.0F, 8.0F, 3.0F).texOffs(14, 33).addBox(3.65F, -8.0F, -4.5F, 1.0F, 8.0F, 3.0F).texOffs(0, 28).addBox(-5.0F, -9.0F, -4.8F, 10.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("peak", CubeListBuilder.create().texOffs(64, 15).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 5.0F, 6.0F).texOffs(0, 19).addBox(-2.5F, -7.0F, -1.8F, 5.0F, 4.0F, 5.0F).texOffs(42, 31).addBox(-2.0F, -9.5F, -1.0F, 4.0F, 4.0F, 4.0F).texOffs(74, 33).addBox(-1.5F, -11.5F, 0.4F, 3.0F, 3.0F, 3.0F).texOffs(20, 19).addBox(-1.0F, -13.5F, 1.2F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 1.5F, -0.384F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("veil", CubeListBuilder.create().texOffs(30, 16).addBox(-3.5F, 0.0F, 0.0F, 7.0F, 14.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 4.15F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(46, 16).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 10.0F, 3.0F).texOffs(100, 24).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 5.0F, 4.0F), PartPose.offsetAndRotation(-4.9F, -8.0F, 0.0F, 0.0F, 0.0F, 0.1047F));
        p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(100, 33).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(120, 11).addBox(-1.0F, 3.0F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(124, 11).addBox(0.2F, 3.0F, 0.2F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(88, 24).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 10.0F, 3.0F).texOffs(58, 26).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 5.0F, 4.0F), PartPose.offsetAndRotation(4.9F, -8.0F, 0.0F, 0.0F, 0.0F, -0.1047F));
        p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(108, 33).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(124, 13).addBox(-1.0F, 3.0F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(88, 15).addBox(0.2F, 3.0F, 0.2F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of hollow_mourner. */
    public static final Map<String, String[]> HOLLOW_MOURNER_ONLY = Map.ofEntries(
            Map.entry("weeds", new String[] {"bog_mourner"}));

    public static LayerDefinition dustwhirl() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_funnel = root.addOrReplaceChild("funnel", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_funnel.addOrReplaceChild("ring_0", CubeListBuilder.create().texOffs(48, 19).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_funnel.addOrReplaceChild("ring_1", CubeListBuilder.create().texOffs(40, 35).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 4.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_funnel.addOrReplaceChild("ring_2", CubeListBuilder.create().texOffs(0, 49).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 4.0F, 8.0F), PartPose.offsetAndRotation(0.0F, -10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_funnel.addOrReplaceChild("ring_3", CubeListBuilder.create().texOffs(0, 35).addBox(-5.0F, -2.0F, -5.0F, 10.0F, 4.0F, 10.0F), PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_funnel.addOrReplaceChild("ring_4", CubeListBuilder.create().texOffs(0, 19).addBox(-6.0F, -2.0F, -6.0F, 12.0F, 4.0F, 12.0F), PartPose.offsetAndRotation(0.0F, -18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_funnel.addOrReplaceChild("ring_5", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -2.5F, -7.0F, 14.0F, 5.0F, 14.0F), PartPose.offsetAndRotation(0.0F, -22.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_funnel.addOrReplaceChild("core", CubeListBuilder.create().texOffs(48, 27).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_funnel.addOrReplaceChild("debris_0", CubeListBuilder.create().texOffs(56, 4).addBox(4.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_funnel.addOrReplaceChild("debris_1", CubeListBuilder.create().texOffs(56, 0).addBox(6.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, 2.0944F, 0.0F));
        p_funnel.addOrReplaceChild("debris_2", CubeListBuilder.create().texOffs(40, 45).addBox(7.5F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -20.0F, 0.0F, 0.0F, 4.1888F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> DUSTWHIRL_ONLY = Map.of();

    public static LayerDefinition skreel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 10.0F).texOffs(36, 26).addBox(-3.5F, -3.6F, -4.0F, 7.0F, 1.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(104, 0).addBox(-2.5F, -3.0F, -4.5F, 5.0F, 5.0F, 5.0F).texOffs(104, 10).addBox(-1.5F, -3.85F, -1.0F, 3.0F, 1.0F, 4.0F).texOffs(118, 10).addBox(-1.0F, -1.5F, -7.0F, 2.0F, 2.0F, 3.0F).texOffs(104, 15).addBox(-0.5F, 0.35F, -7.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -1.5F, -5.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(108, 23).addBox(-0.5F, 0.15F, -2.2F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.5F, -4.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(64, 26).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 0.0F, 8.0F).texOffs(92, 26).addBox(-3.0F, 0.25F, 0.0F, 6.0F, 0.0F, 8.0F), PartPose.offsetAndRotation(0.0F, -1.5F, 5.0F, -0.0873F, 0.0F, 0.0F));
        PartDefinition p_wing_right = p_body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(32, 0).addBox(-9.0F, 0.0F, -3.0F, 9.0F, 0.0F, 9.0F).texOffs(68, 0).addBox(-9.0F, 0.25F, -3.0F, 9.0F, 0.0F, 9.0F), PartPose.offsetAndRotation(-3.0F, -2.3F, -1.0F, 0.0F, 0.0F, 0.1047F));
        p_wing_right.addOrReplaceChild("wing_right_tip", CubeListBuilder.create().texOffs(0, 18).addBox(-10.0F, 0.0F, -3.0F, 10.0F, 0.0F, 8.0F).texOffs(36, 18).addBox(-10.0F, 0.25F, -3.0F, 10.0F, 0.0F, 8.0F), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(108, 19).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(124, 0).addBox(-0.5F, 0.5F, -0.5F, 1.0F, 4.0F, 1.0F).texOffs(108, 15).addBox(-1.0F, 4.0F, -1.5F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.5F, 3.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_wing_left = p_body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(32, 9).addBox(0.0F, 0.0F, -3.0F, 9.0F, 0.0F, 9.0F).texOffs(68, 9).addBox(0.0F, 0.25F, -3.0F, 9.0F, 0.0F, 9.0F), PartPose.offsetAndRotation(3.0F, -2.3F, -1.0F, 0.0F, 0.0F, -0.1047F));
        p_wing_left.addOrReplaceChild("wing_left_tip", CubeListBuilder.create().texOffs(72, 18).addBox(0.0F, 0.0F, -3.0F, 10.0F, 0.0F, 8.0F).texOffs(0, 26).addBox(0.0F, 0.25F, -3.0F, 10.0F, 0.0F, 8.0F), PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(116, 19).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(124, 5).addBox(-0.5F, 0.5F, -0.5F, 1.0F, 4.0F, 1.0F).texOffs(118, 15).addBox(-1.0F, 4.0F, -1.5F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.5F, 3.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> SKREEL_ONLY = Map.of();

    public static LayerDefinition prismback() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -2.5F, -6.0F, 10.0F, 5.0F, 12.0F).texOffs(0, 17).addBox(-4.0F, -3.5F, -5.5F, 8.0F, 1.0F, 11.0F), PartPose.offsetAndRotation(0.0F, 19.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("prism_0", CubeListBuilder.create().texOffs(22, 50).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(60, 6).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.5F, -3.5F, -0.1396F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("prism_1", CubeListBuilder.create().texOffs(22, 44).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(44, 15).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.5F, 0.0F, -0.1396F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("prism_2", CubeListBuilder.create().texOffs(30, 50).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(48, 15).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.5F, 3.5F, -0.1396F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("prism_3", CubeListBuilder.create().texOffs(8, 52).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(52, 15).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-2.8F, -3.5F, -3.0F, -0.1047F, 0.0F, -0.4887F));
        p_body.addOrReplaceChild("prism_4", CubeListBuilder.create().texOffs(38, 50).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(56, 15).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-2.8F, -3.5F, 0.5F, -0.1047F, 0.0F, -0.4887F));
        p_body.addOrReplaceChild("prism_5", CubeListBuilder.create().texOffs(46, 53).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(60, 15).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-2.8F, -3.5F, 4.0F, -0.1047F, 0.0F, -0.4887F));
        p_body.addOrReplaceChild("prism_6", CubeListBuilder.create().texOffs(54, 53).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(60, 28).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.8F, -3.5F, -3.0F, -0.1047F, 0.0F, 0.4887F));
        p_body.addOrReplaceChild("prism_7", CubeListBuilder.create().texOffs(0, 51).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(60, 30).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.8F, -3.5F, 0.5F, -0.1047F, 0.0F, 0.4887F));
        p_body.addOrReplaceChild("prism_8", CubeListBuilder.create().texOffs(16, 55).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(60, 32).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.8F, -3.5F, 4.0F, -0.1047F, 0.0F, 0.4887F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 29).addBox(-3.5F, -2.0F, -5.0F, 7.0F, 4.0F, 5.0F).texOffs(44, 8).addBox(-2.5F, -1.0F, -9.0F, 5.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 38).addBox(-2.5F, -0.5F, -4.0F, 5.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("crest_0", CubeListBuilder.create().texOffs(60, 0).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(60, 34).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-1.5F, -2.0F, -2.5F, -0.2618F, 0.0F, -0.2618F));
        p_head.addOrReplaceChild("crest_1", CubeListBuilder.create().texOffs(60, 3).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(60, 36).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(1.5F, -2.0F, -2.5F, -0.2618F, 0.0F, 0.2618F));
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(38, 17).addBox(-3.0F, -2.0F, -0.5F, 6.0F, 4.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 6.0F, -0.1047F, 0.0F, 0.0F));
        p_tail.addOrReplaceChild("prism_tail", CubeListBuilder.create().texOffs(24, 55).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(60, 38).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 3.0F, -0.2094F, 0.0F, 0.0F));
        PartDefinition p_tail2 = p_tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(38, 28).addBox(-2.0F, -1.5F, -0.5F, 4.0F, 3.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.5F, 6.0F, -0.1047F, 0.0F, 0.0F));
        p_tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(44, 0).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.3F, 6.0F, -0.1047F, 0.0F, 0.0F));
        PartDefinition p_leg_front_right = p_body.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(8, 44).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-4.5F, 0.5F, -3.5F, 0.0F, 0.0F, 0.0F));
        p_leg_front_right.addOrReplaceChild("shin_front_right", CubeListBuilder.create().texOffs(52, 38).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F).texOffs(24, 29).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-4.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_front_left = p_body.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(48, 45).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(4.5F, 0.5F, -3.5F, 0.0F, 0.0F, 0.0F));
        p_leg_front_left.addOrReplaceChild("shin_front_left", CubeListBuilder.create().texOffs(32, 43).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F).texOffs(24, 34).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(4.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_back_right = p_body.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(8, 48).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-4.5F, 0.5F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_leg_back_right.addOrReplaceChild("shin_back_right", CubeListBuilder.create().texOffs(40, 43).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F).texOffs(38, 38).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-4.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_back_left = p_body.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(48, 49).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(4.5F, 0.5F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_leg_back_left.addOrReplaceChild("shin_back_left", CubeListBuilder.create().texOffs(0, 44).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F).texOffs(18, 39).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(4.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> PRISMBACK_ONLY = Map.of();

    public static LayerDefinition slagspitter() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.5F, -3.5F, -5.0F, 11.0F, 5.0F, 10.0F).texOffs(42, 0).addBox(-4.5F, -5.5F, -3.5F, 9.0F, 2.0F, 8.0F).texOffs(104, 18).addBox(-3.0F, -6.5F, -1.5F, 6.0F, 1.0F, 5.0F).texOffs(40, 19).addBox(-3.5F, -2.5F, 5.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 17.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(76, 0).addBox(-4.5F, -2.0F, -6.0F, 9.0F, 4.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -1.5F, -5.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("eye_right", CubeListBuilder.create().texOffs(0, 24).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-4.2F, -1.0F, -2.8F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("eye_left", CubeListBuilder.create().texOffs(12, 24).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(4.2F, -1.0F, -2.8F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_spout = p_head.addOrReplaceChild("spout", CubeListBuilder.create().texOffs(84, 18).addBox(-2.5F, -2.5F, -2.5F, 5.0F, 3.0F, 5.0F).texOffs(24, 24).addBox(-1.5F, -5.5F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(110, 11).addBox(-2.0F, -7.5F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -2.0F, -3.5F, 0.4189F, 0.0F, 0.0F));
        p_spout.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(42, 10).addBox(-3.0F, -1.5F, -3.0F, 6.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -1.8F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(84, 11).addBox(-4.0F, 2.1F, -5.0F, 8.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -1.5F, -5.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("clinker", CubeListBuilder.create().texOffs(58, 28).addBox(-3.0F, -0.8F, -1.5F, 2.0F, 1.0F, 2.0F).texOffs(90, 26).addBox(0.5F, -1.8F, 0.5F, 3.0F, 2.0F, 2.0F).texOffs(36, 29).addBox(-1.5F, -0.8F, 2.5F, 2.0F, 1.0F, 2.0F).texOffs(100, 29).addBox(3.2F, 0.7F, -2.5F, 2.0F, 1.0F, 2.0F).texOffs(108, 29).addBox(-5.0F, 0.7F, 1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -6.5F, 1.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("columns", CubeListBuilder.create().texOffs(118, 24).addBox(-2.5F, -3.8F, -1.5F, 2.0F, 4.0F, 2.0F).texOffs(82, 26).addBox(0.5F, -2.8F, 0.5F, 2.0F, 3.0F, 2.0F).texOffs(74, 27).addBox(-1.0F, -1.8F, 2.5F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -6.5F, 1.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(58, 19).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F).texOffs(36, 24).addBox(-2.0F, 5.6F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-5.0F, 17.5F, -2.5F, 0.0F, 0.0F, 0.2443F));
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(106, 0).addBox(-3.0F, -2.5F, -3.0F, 3.0F, 5.0F, 6.0F).texOffs(74, 21).addBox(-2.5F, 2.5F, 1.5F, 2.0F, 4.0F, 2.0F).texOffs(0, 15).addBox(-3.0F, 6.0F, -4.0F, 3.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(-5.0F, 16.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(66, 21).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F).texOffs(104, 24).addBox(-1.0F, 5.6F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(5.0F, 17.5F, -2.5F, 0.0F, 0.0F, -0.2443F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(66, 10).addBox(0.0F, -2.5F, -3.0F, 3.0F, 5.0F, 6.0F).texOffs(50, 24).addBox(0.5F, 2.5F, 1.5F, 2.0F, 4.0F, 2.0F).texOffs(20, 15).addBox(0.0F, 6.0F, -4.0F, 3.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(5.0F, 16.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of slagspitter. */
    public static final Map<String, String[]> SLAGSPITTER_ONLY = Map.ofEntries(
            Map.entry("clinker", new String[] {"base"}),
            Map.entry("columns", new String[] {"basalt_spitter"}));

    public static LayerDefinition ash_jackal() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -3.0F, -6.0F, 5.0F, 6.0F, 5.0F).texOffs(38, 0).addBox(-2.0F, -3.0F, -1.0F, 4.0F, 4.0F, 4.0F).texOffs(20, 8).addBox(-2.5F, -3.0F, 3.0F, 5.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, -0.0698F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 11).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F).texOffs(38, 17).addBox(-1.5F, 6.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 5.5F, 0.6632F, 0.0F, 0.0F));
        PartDefinition p_head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(12, 16).addBox(-1.5F, -1.0F, -2.0F, 3.0F, 4.0F, 3.0F).texOffs(20, 0).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 4.0F, 4.0F).texOffs(24, 17).addBox(-1.5F, -1.5F, -9.0F, 3.0F, 2.0F, 4.0F).texOffs(32, 23).addBox(-1.0F, -2.0F, -8.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 11.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(50, 17).addBox(-1.0F, 0.0F, -4.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.5F, -5.0F, 0.1047F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(32, 27).addBox(-2.0F, -3.0F, -0.5F, 4.0F, 3.0F, 1.0F).texOffs(12, 11).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 2.0F, 1.0F).texOffs(58, 22).addBox(-1.0F, -7.0F, -0.5F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-1.6F, -3.0F, -3.0F, -0.1396F, 0.0F, -0.2443F));
        p_head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(42, 28).addBox(-2.0F, -3.0F, -0.5F, 4.0F, 3.0F, 1.0F).texOffs(42, 23).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 2.0F, 1.0F).texOffs(58, 25).addBox(-1.0F, -7.0F, -0.5F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(1.6F, -3.0F, -3.0F, -0.1396F, 0.0F, 0.2443F));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(50, 22).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(8, 23).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(52, 28).addBox(-1.0F, 8.0F, -2.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.5F, 15.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(16, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(24, 23).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(0, 29).addBox(-1.0F, 8.0F, -2.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.5F, 15.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(36, 8).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 5.0F, 4.0F).texOffs(54, 0).addBox(-1.0F, 3.5F, -0.5F, 2.0F, 5.0F, 2.0F).texOffs(10, 29).addBox(-1.0F, 8.5F, -1.5F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.5F, 14.5F, 4.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(50, 8).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 5.0F, 4.0F).texOffs(0, 20).addBox(-1.0F, 3.5F, -0.5F, 2.0F, 5.0F, 2.0F).texOffs(20, 29).addBox(-1.0F, 8.5F, -1.5F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.5F, 14.5F, 4.5F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> ASH_JACKAL_ONLY = Map.of();

    public static LayerDefinition slagheartGolem() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(96, 62).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 5.0F, 7.0F).texOffs(96, 51).addBox(-4.0F, 5.0F, -4.5F, 8.0F, 3.0F, 8.0F), PartPose.offsetAndRotation(-4.5F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(0, 63).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 5.0F, 7.0F).texOffs(28, 59).addBox(-4.0F, 5.0F, -4.5F, 8.0F, 3.0F, 8.0F), PartPose.offsetAndRotation(4.5F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 22).addBox(-6.0F, -4.0F, -4.0F, 12.0F, 5.0F, 8.0F).texOffs(0, 0).addBox(-8.0F, -14.0F, -6.0F, 16.0F, 11.0F, 11.0F).texOffs(54, 0).addBox(-7.0F, -21.0F, -1.0F, 14.0F, 9.0F, 10.0F).texOffs(60, 63).addBox(-5.0F, -18.0F, 8.0F, 10.0F, 7.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("vent_0", CubeListBuilder.create().texOffs(40, 22).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-3.5F, -21.0F, 2.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("vent_1", CubeListBuilder.create().texOffs(40, 28).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(3.5F, -21.0F, 2.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("vent_2", CubeListBuilder.create().texOffs(108, 43).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -21.0F, 6.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("heart", CubeListBuilder.create().texOffs(108, 35).addBox(-3.0F, -3.0F, -1.5F, 6.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -7.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(102, 0).addBox(-3.5F, -4.0F, -5.0F, 7.0F, 6.0F, 6.0F).texOffs(102, 12).addBox(-4.0F, -5.0F, -5.5F, 8.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -12.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(80, 35).addBox(-3.5F, -2.0F, -3.5F, 7.0F, 9.0F, 7.0F).texOffs(28, 47).addBox(-4.5F, -3.0F, -4.0F, 9.0F, 4.0F, 8.0F), PartPose.offsetAndRotation(-11.0F, -12.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        p_arm_right.addOrReplaceChild("fist_right", CubeListBuilder.create().texOffs(54, 19).addBox(-4.5F, 0.0F, -4.5F, 9.0F, 7.0F, 9.0F).texOffs(0, 35).addBox(-5.0F, 4.0F, -5.0F, 10.0F, 2.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(0, 47).addBox(-3.5F, -2.0F, -3.5F, 7.0F, 9.0F, 7.0F).texOffs(62, 51).addBox(-4.5F, -3.0F, -4.0F, 9.0F, 4.0F, 8.0F), PartPose.offsetAndRotation(11.0F, -12.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        p_arm_left.addOrReplaceChild("fist_left", CubeListBuilder.create().texOffs(90, 19).addBox(-4.5F, 0.0F, -4.5F, 9.0F, 7.0F, 9.0F).texOffs(40, 35).addBox(-5.0F, 4.0F, -5.0F, 10.0F, 2.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static final Map<String, String[]> SLAGHEART_GOLEM_ONLY = Map.of();

    public static LayerDefinition soot_manta() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -2.0F, -7.0F, 10.0F, 4.0F, 14.0F).texOffs(72, 35).addBox(-3.0F, -3.2F, -5.0F, 6.0F, 2.0F, 9.0F).texOffs(48, 13).addBox(-4.0F, 1.2F, -5.0F, 8.0F, 2.0F, 10.0F).texOffs(102, 35).addBox(-4.0F, -1.5F, -10.0F, 8.0F, 3.0F, 4.0F).texOffs(124, 0).addBox(-4.6F, -1.2F, -9.0F, 1.0F, 1.0F, 1.0F).texOffs(124, 2).addBox(3.6F, -1.2F, -9.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 19.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(102, 42).addBox(-3.0F, 0.0F, -4.2F, 6.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 1.5F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(116, 8).addBox(-1.0F, -0.5F, -4.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-3.5F, 0.3F, -9.5F, -0.1745F, 0.2618F, 0.4363F));
        p_body.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(36, 18).addBox(-1.0F, -0.5F, -4.0F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(3.5F, 0.3F, -9.5F, -0.1745F, -0.2618F, -0.4363F));
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 29).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 13.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 7.0F, 0.1396F, 0.0F, 0.0F));
        p_tail.addOrReplaceChild("plume", CubeListBuilder.create().texOffs(116, 0).addBox(0.0F, -2.0F, -0.5F, 0.0F, 4.0F, 4.0F).texOffs(90, 47).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 0.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 12.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_wing_right = p_body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(48, 0).addBox(-5.0F, -1.0F, -6.0F, 6.0F, 2.0F, 11.0F), PartPose.offsetAndRotation(-5.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_wing_right_mid = p_wing_right.addOrReplaceChild("wing_right_mid", CubeListBuilder.create().texOffs(84, 13).addBox(-7.0F, 0.0F, -6.0F, 7.0F, 0.0F, 11.0F).texOffs(0, 18).addBox(-7.0F, 0.25F, -6.0F, 7.0F, 0.0F, 11.0F), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_wing_right_mid.addOrReplaceChild("wing_right_tip", CubeListBuilder.create().texOffs(28, 36).addBox(-8.0F, 0.0F, -4.0F, 8.0F, 0.0F, 7.0F).texOffs(0, 43).addBox(-8.0F, 0.25F, -4.0F, 8.0F, 0.0F, 7.0F), PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1047F));
        PartDefinition p_wing_left = p_body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(82, 0).addBox(-1.0F, -1.0F, -6.0F, 6.0F, 2.0F, 11.0F), PartPose.offsetAndRotation(5.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_wing_left_mid = p_wing_left.addOrReplaceChild("wing_left_mid", CubeListBuilder.create().texOffs(84, 24).addBox(0.0F, 0.0F, -6.0F, 7.0F, 0.0F, 11.0F).texOffs(36, 25).addBox(0.0F, 0.25F, -6.0F, 7.0F, 0.0F, 11.0F), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_wing_left_mid.addOrReplaceChild("wing_left_tip", CubeListBuilder.create().texOffs(30, 43).addBox(0.0F, 0.0F, -4.0F, 8.0F, 0.0F, 7.0F).texOffs(60, 46).addBox(0.0F, 0.25F, -4.0F, 8.0F, 0.0F, 7.0F), PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1047F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> SOOT_MANTA_ONLY = Map.of();

    public static LayerDefinition magmaTortoise() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_shell = root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 33).addBox(-13.0F, -8.0F, -13.0F, 26.0F, 3.0F, 26.0F).texOffs(128, 0).addBox(-15.0F, -5.5F, -15.0F, 30.0F, 4.0F, 30.0F).texOffs(0, 0).addBox(-17.0F, -2.0F, -15.2F, 34.0F, 3.0F, 30.0F).texOffs(36, 68).addBox(-15.0F, -2.0F, -17.2F, 9.0F, 3.0F, 3.0F).texOffs(60, 68).addBox(6.0F, -2.0F, -17.2F, 9.0F, 3.0F, 3.0F).texOffs(36, 62).addBox(-12.0F, -2.0F, 14.6F, 24.0F, 3.0F, 3.0F).texOffs(104, 34).addBox(-12.0F, 0.5F, -13.0F, 24.0F, 3.0F, 26.0F), PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_shell.addOrReplaceChild("crust", CubeListBuilder.create().texOffs(240, 56).addBox(-16.5F, -5.5F, -9.0F, 3.0F, 3.0F, 5.0F).texOffs(236, 34).addBox(13.5F, -5.5F, -4.0F, 3.0F, 3.0F, 6.0F).texOffs(118, 70).addBox(-16.5F, -4.5F, 5.0F, 3.0F, 2.0F, 4.0F).texOffs(132, 70).addBox(13.6F, -4.5F, 8.0F, 3.0F, 2.0F, 4.0F).texOffs(236, 43).addBox(-6.0F, -4.5F, 13.2F, 5.0F, 2.0F, 3.0F).texOffs(146, 70).addBox(3.0F, -5.5F, 13.4F, 4.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_neck = root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(204, 48).addBox(-3.5F, -2.5F, -6.0F, 7.0F, 6.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 14.0F, -14.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(204, 34).addBox(-4.0F, -4.5F, -8.0F, 8.0F, 6.0F, 8.0F).texOffs(192, 63).addBox(-4.6F, -5.4F, -7.8F, 3.0F, 1.0F, 3.0F).texOffs(206, 70).addBox(1.6F, -5.4F, -7.8F, 3.0F, 1.0F, 3.0F).texOffs(192, 70).addBox(-2.0F, -3.6F, -10.6F, 4.0F, 3.0F, 3.0F).texOffs(248, 0).addBox(-1.0F, -0.9F, -11.3F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(90, 63).addBox(-3.0F, -1.2F, -9.4F, 6.0F, 2.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 2.5F, -1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_flipper_front_left = root.addOrReplaceChild("flipper_front_left", CubeListBuilder.create().texOffs(232, 48).addBox(0.0F, -1.5F, -2.5F, 6.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(13.0F, 15.5F, -9.0F, 0.0F, 0.4363F, -0.1396F));
        p_flipper_front_left.addOrReplaceChild("flipper_front_left_paddle", CubeListBuilder.create().texOffs(204, 61).addBox(0.0F, -1.0F, -3.5F, 11.0F, 2.0F, 7.0F).texOffs(248, 2).addBox(10.2F, -0.6F, -3.0F, 2.0F, 1.0F, 1.0F).texOffs(248, 4).addBox(10.2F, -0.6F, -0.8F, 2.0F, 1.0F, 1.0F).texOffs(248, 6).addBox(10.2F, -0.6F, 1.4F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.5F, 0.0F, 0.0F, 0.0F, 0.2618F, 0.0F));
        root.addOrReplaceChild("flipper_back_left", CubeListBuilder.create().texOffs(118, 63).addBox(0.0F, -1.0F, -2.5F, 8.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(12.0F, 17.0F, 11.0F, 0.0F, -0.6109F, 0.0F));
        PartDefinition p_flipper_front_right = root.addOrReplaceChild("flipper_front_right", CubeListBuilder.create().texOffs(170, 63).addBox(-6.0F, -1.5F, -2.5F, 6.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(-13.0F, 15.5F, -9.0F, 0.0F, -0.4363F, 0.1396F));
        p_flipper_front_right.addOrReplaceChild("flipper_front_right_paddle", CubeListBuilder.create().texOffs(0, 62).addBox(-11.0F, -1.0F, -3.5F, 11.0F, 2.0F, 7.0F).texOffs(248, 8).addBox(-12.2F, -0.6F, -3.0F, 2.0F, 1.0F, 1.0F).texOffs(248, 10).addBox(-12.2F, -0.6F, -0.8F, 2.0F, 1.0F, 1.0F).texOffs(248, 12).addBox(-12.2F, -0.6F, 1.4F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.5F, 0.0F, 0.0F, 0.0F, -0.2618F, 0.0F));
        root.addOrReplaceChild("flipper_back_right", CubeListBuilder.create().texOffs(144, 63).addBox(-8.0F, -1.0F, -2.5F, 8.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(-12.0F, 17.0F, 11.0F, 0.0F, 0.6109F, 0.0F));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(240, 64).addBox(-1.5F, -1.0F, 0.0F, 3.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 17.0F, 15.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 256, 128);
    }
    /** Parts shown only for some variants of magma_tortoise. */
    public static final Map<String, String[]> MAGMA_TORTOISE_ONLY = Map.ofEntries(
            Map.entry("crust", new String[] {"old_tortoise"}));

    public static LayerDefinition veinleech() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -4.0F, -3.0F, 9.0F, 8.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("sac", CubeListBuilder.create().texOffs(74, 0).addBox(-3.0F, -3.0F, -2.5F, 6.0F, 3.0F, 5.0F).texOffs(112, 42).addBox(-2.0F, -4.0F, -1.5F, 4.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_neck = p_body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(30, 0).addBox(-3.5F, -3.5F, -4.0F, 7.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.5F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(96, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("tooth_0", CubeListBuilder.create().texOffs(104, 9).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F).texOffs(100, 16).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.3F, -2.5F, -0.4363F, 0.0F, -3.1416F));
        p_head.addOrReplaceChild("tooth_1", CubeListBuilder.create().texOffs(118, 14).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F).texOffs(100, 18).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(1.99F, -1.15F, -2.5F, -0.4363F, 0.0F, -2.0944F));
        p_head.addOrReplaceChild("tooth_2", CubeListBuilder.create().texOffs(90, 44).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F).texOffs(118, 18).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(1.99F, 1.15F, -2.5F, -0.4363F, 0.0F, -1.0472F));
        p_head.addOrReplaceChild("tooth_3", CubeListBuilder.create().texOffs(28, 46).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F).texOffs(122, 18).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 2.3F, -2.5F, -0.4363F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("tooth_4", CubeListBuilder.create().texOffs(36, 46).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F).texOffs(52, 46).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-1.99F, 1.15F, -2.5F, -0.4363F, 0.0F, 1.0472F));
        p_head.addOrReplaceChild("tooth_5", CubeListBuilder.create().texOffs(44, 46).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F).texOffs(56, 46).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-1.99F, -1.15F, -2.5F, -0.4363F, 0.0F, 2.0944F));
        PartDefinition p_rear = p_body.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(52, 0).addBox(-3.5F, -3.5F, 0.0F, 7.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.5F, 3.0F, 0.0F, 0.0F, 0.0F));
        p_rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(74, 8).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 2.0F).texOffs(70, 44).addBox(-3.5F, 2.0F, 0.0F, 7.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.5F, 4.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_0", CubeListBuilder.create().texOffs(114, 0).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_1", CubeListBuilder.create().texOffs(114, 7).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_2", CubeListBuilder.create().texOffs(90, 9).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_3", CubeListBuilder.create().texOffs(30, 11).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_4", CubeListBuilder.create().texOffs(44, 11).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_5", CubeListBuilder.create().texOffs(58, 11).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_6", CubeListBuilder.create().texOffs(0, 14).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_7", CubeListBuilder.create().texOffs(14, 14).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_8", CubeListBuilder.create().texOffs(104, 14).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_9", CubeListBuilder.create().texOffs(72, 16).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_10", CubeListBuilder.create().texOffs(86, 16).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_11", CubeListBuilder.create().texOffs(28, 18).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_12", CubeListBuilder.create().texOffs(42, 18).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_13", CubeListBuilder.create().texOffs(56, 18).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_14", CubeListBuilder.create().texOffs(0, 21).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_15", CubeListBuilder.create().texOffs(14, 21).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_16", CubeListBuilder.create().texOffs(100, 21).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_17", CubeListBuilder.create().texOffs(114, 21).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_18", CubeListBuilder.create().texOffs(70, 23).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_19", CubeListBuilder.create().texOffs(84, 23).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_20", CubeListBuilder.create().texOffs(28, 25).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_21", CubeListBuilder.create().texOffs(42, 25).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_22", CubeListBuilder.create().texOffs(56, 25).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_23", CubeListBuilder.create().texOffs(0, 28).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_24", CubeListBuilder.create().texOffs(14, 28).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_25", CubeListBuilder.create().texOffs(98, 28).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_26", CubeListBuilder.create().texOffs(112, 28).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_27", CubeListBuilder.create().texOffs(70, 30).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_28", CubeListBuilder.create().texOffs(84, 30).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_29", CubeListBuilder.create().texOffs(28, 32).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_30", CubeListBuilder.create().texOffs(42, 32).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_31", CubeListBuilder.create().texOffs(56, 32).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_32", CubeListBuilder.create().texOffs(0, 35).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_33", CubeListBuilder.create().texOffs(14, 35).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_34", CubeListBuilder.create().texOffs(98, 35).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_35", CubeListBuilder.create().texOffs(112, 35).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_36", CubeListBuilder.create().texOffs(70, 37).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_37", CubeListBuilder.create().texOffs(84, 37).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_38", CubeListBuilder.create().texOffs(28, 39).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_39", CubeListBuilder.create().texOffs(42, 39).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_40", CubeListBuilder.create().texOffs(56, 39).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_41", CubeListBuilder.create().texOffs(0, 42).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_42", CubeListBuilder.create().texOffs(14, 42).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("vein_43", CubeListBuilder.create().texOffs(98, 42).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> VEINLEECH_ONLY = Map.of();

    public static LayerDefinition shredjaw_beetle() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -6.0F, -3.0F, 14.0F, 7.0F, 13.0F).texOffs(54, 0).addBox(-6.0F, -7.5F, -2.0F, 12.0F, 2.0F, 11.0F).texOffs(100, 9).addBox(-5.0F, -7.0F, 9.0F, 10.0F, 5.0F, 2.0F).texOffs(54, 13).addBox(-6.0F, 1.0F, -2.0F, 12.0F, 1.0F, 11.0F).texOffs(0, 20).addBox(-6.5F, -5.0F, -7.5F, 13.0F, 6.0F, 5.0F).texOffs(86, 25).addBox(-1.5F, -6.5F, -7.0F, 3.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 19.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(100, 0).addBox(-4.5F, -2.5F, -3.5F, 9.0F, 5.0F, 4.0F).texOffs(118, 16).addBox(4.0F, -2.0F, -3.0F, 1.0F, 2.0F, 2.0F).texOffs(118, 20).addBox(-5.0F, -2.0F, -3.0F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -7.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_jaw_left = p_head.addOrReplaceChild("jaw_left", CubeListBuilder.create().texOffs(100, 16).addBox(-1.5F, -1.5F, -6.0F, 3.0F, 3.0F, 6.0F).texOffs(124, 9).addBox(-2.5F, -1.0F, -1.5F, 1.0F, 2.0F, 1.0F).texOffs(124, 12).addBox(-2.5F, -1.0F, -3.5F, 1.0F, 2.0F, 1.0F).texOffs(124, 15).addBox(-2.5F, -1.0F, -5.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(3.0F, 0.5F, -3.0F, 0.0F, -0.2443F, 0.0F));
        p_jaw_left.addOrReplaceChild("jaw_left_tip", CubeListBuilder.create().texOffs(54, 25).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 2.0F, 6.0F).texOffs(82, 33).addBox(-2.0F, -0.5F, -2.0F, 1.0F, 1.0F, 1.0F).texOffs(122, 33).addBox(-2.0F, -0.5F, -4.0F, 1.0F, 1.0F, 1.0F).texOffs(8, 35).addBox(-0.5F, -0.5F, -7.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.5F, 0.0F, 0.733F, 0.0F));
        PartDefinition p_jaw_right = p_head.addOrReplaceChild("jaw_right", CubeListBuilder.create().texOffs(36, 20).addBox(-1.5F, -1.5F, -6.0F, 3.0F, 3.0F, 6.0F).texOffs(124, 18).addBox(1.5F, -1.0F, -1.5F, 1.0F, 2.0F, 1.0F).texOffs(124, 21).addBox(1.5F, -1.0F, -3.5F, 1.0F, 2.0F, 1.0F).texOffs(50, 29).addBox(1.5F, -1.0F, -5.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-3.0F, 0.5F, -3.0F, 0.0F, 0.2443F, 0.0F));
        p_jaw_right.addOrReplaceChild("jaw_right_tip", CubeListBuilder.create().texOffs(70, 25).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 2.0F, 6.0F).texOffs(12, 35).addBox(1.0F, -0.5F, -2.0F, 1.0F, 1.0F, 1.0F).texOffs(16, 35).addBox(1.0F, -0.5F, -4.0F, 1.0F, 1.0F, 1.0F).texOffs(20, 35).addBox(-0.5F, -0.5F, -7.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.5F, 0.0F, -0.733F, 0.0F));
        PartDefinition p_leg_left_1 = p_body.addOrReplaceChild("leg_left_1", CubeListBuilder.create(), PartPose.offsetAndRotation(6.0F, 0.5F, -5.5F, 0.0F, 0.6283F, 0.0F));
        PartDefinition p_leg_left_1_thigh = p_leg_left_1.addOrReplaceChild("leg_left_1_thigh", CubeListBuilder.create().texOffs(14, 31).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3491F));
        p_leg_left_1_thigh.addOrReplaceChild("leg_left_1_shin", CubeListBuilder.create().texOffs(100, 25).addBox(-0.5F, -0.85F, -0.85F, 5.0F, 2.0F, 2.0F).texOffs(26, 31).addBox(4.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(4.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.309F));
        PartDefinition p_leg_left_2 = p_body.addOrReplaceChild("leg_left_2", CubeListBuilder.create(), PartPose.offsetAndRotation(6.0F, 0.5F, 0.5F, 0.0F, 0.0698F, 0.0F));
        PartDefinition p_leg_left_2_thigh = p_leg_left_2.addOrReplaceChild("leg_left_2_thigh", CubeListBuilder.create().texOffs(86, 31).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3491F));
        p_leg_left_2_thigh.addOrReplaceChild("leg_left_2_shin", CubeListBuilder.create().texOffs(114, 25).addBox(-0.5F, -0.85F, -0.85F, 5.0F, 2.0F, 2.0F).texOffs(74, 33).addBox(4.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(4.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.309F));
        PartDefinition p_leg_left_3 = p_body.addOrReplaceChild("leg_left_3", CubeListBuilder.create(), PartPose.offsetAndRotation(6.0F, 0.5F, 6.0F, 0.0F, -0.5934F, 0.0F));
        PartDefinition p_leg_left_3_thigh = p_leg_left_3.addOrReplaceChild("leg_left_3_thigh", CubeListBuilder.create().texOffs(26, 33).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3491F));
        p_leg_left_3_thigh.addOrReplaceChild("leg_left_3_shin", CubeListBuilder.create().texOffs(36, 29).addBox(-0.5F, -0.85F, -0.85F, 5.0F, 2.0F, 2.0F).texOffs(98, 33).addBox(4.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(4.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.309F));
        PartDefinition p_leg_right_1 = p_body.addOrReplaceChild("leg_right_1", CubeListBuilder.create(), PartPose.offsetAndRotation(-6.0F, 0.5F, -5.5F, 0.0F, -0.6283F, 0.0F));
        PartDefinition p_leg_right_1_thigh = p_leg_right_1.addOrReplaceChild("leg_right_1_thigh", CubeListBuilder.create().texOffs(38, 33).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3491F));
        p_leg_right_1_thigh.addOrReplaceChild("leg_right_1_shin", CubeListBuilder.create().texOffs(100, 29).addBox(-4.5F, -0.85F, -0.85F, 5.0F, 2.0F, 2.0F).texOffs(106, 33).addBox(-7.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.309F));
        PartDefinition p_leg_right_2 = p_body.addOrReplaceChild("leg_right_2", CubeListBuilder.create(), PartPose.offsetAndRotation(-6.0F, 0.5F, 0.5F, 0.0F, -0.0698F, 0.0F));
        PartDefinition p_leg_right_2_thigh = p_leg_right_2.addOrReplaceChild("leg_right_2_thigh", CubeListBuilder.create().texOffs(50, 33).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3491F));
        p_leg_right_2_thigh.addOrReplaceChild("leg_right_2_shin", CubeListBuilder.create().texOffs(114, 29).addBox(-4.5F, -0.85F, -0.85F, 5.0F, 2.0F, 2.0F).texOffs(114, 33).addBox(-7.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.309F));
        PartDefinition p_leg_right_3 = p_body.addOrReplaceChild("leg_right_3", CubeListBuilder.create(), PartPose.offsetAndRotation(-6.0F, 0.5F, 6.0F, 0.0F, 0.5934F, 0.0F));
        PartDefinition p_leg_right_3_thigh = p_leg_right_3.addOrReplaceChild("leg_right_3_thigh", CubeListBuilder.create().texOffs(62, 33).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3491F));
        p_leg_right_3_thigh.addOrReplaceChild("leg_right_3_shin", CubeListBuilder.create().texOffs(0, 31).addBox(-4.5F, -0.85F, -0.85F, 5.0F, 2.0F, 2.0F).texOffs(0, 35).addBox(-7.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.309F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> SHREDJAW_BEETLE_ONLY = Map.of();

    public static LayerDefinition weeplingBroodmother() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(62, 0).addBox(-6.0F, -6.0F, -6.0F, 12.0F, 10.0F, 9.0F).texOffs(92, 31).addBox(-4.0F, -8.0F, -4.0F, 3.0F, 2.0F, 3.0F).texOffs(92, 45).addBox(1.0F, -8.0F, -2.0F, 4.0F, 2.0F, 4.0F).texOffs(32, 52).addBox(-2.0F, -7.0F, 0.0F, 2.0F, 1.0F, 2.0F).texOffs(40, 52).addBox(3.5F, -7.0F, -5.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 15.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 27).addBox(-4.0F, -4.0F, -5.0F, 8.0F, 7.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("mandible_right", CubeListBuilder.create().texOffs(122, 51).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F).texOffs(124, 0).addBox(0.0F, 0.0F, -2.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.5F, 2.0F, -5.0F, 0.0F, 0.2618F, 0.0F));
        p_head.addOrReplaceChild("mandible_left", CubeListBuilder.create().texOffs(56, 52).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F).texOffs(124, 3).addBox(-1.0F, 0.0F, -2.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.5F, 2.0F, -5.0F, 0.0F, -0.2618F, 0.0F));
        p_head.addOrReplaceChild("chin_drip", CubeListBuilder.create().texOffs(124, 6).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.5F, 3.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_fl", CubeListBuilder.create().texOffs(44, 43).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F).texOffs(68, 43).addBox(-1.0F, 5.0F, -2.5F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.5F, 3.0F, -4.0F, 0.0F, 0.0F, -0.3142F));
        p_body.addOrReplaceChild("leg_fr", CubeListBuilder.create().texOffs(56, 43).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F).texOffs(0, 54).addBox(-2.0F, 5.0F, -2.5F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.5F, 3.0F, -4.0F, 0.0F, 0.0F, 0.3142F));
        p_body.addOrReplaceChild("leg_ml", CubeListBuilder.create().texOffs(68, 45).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F).texOffs(6, 54).addBox(-1.0F, 5.0F, -2.5F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.5F, 3.0F, 1.0F, 0.0F, 0.0F, -0.3142F));
        p_body.addOrReplaceChild("leg_mr", CubeListBuilder.create().texOffs(80, 45).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F).texOffs(68, 54).addBox(-2.0F, 5.0F, -2.5F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.5F, 3.0F, 1.0F, 0.0F, 0.0F, 0.3142F));
        PartDefinition p_abdomen = p_body.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 0).addBox(-9.0F, -6.0F, 0.0F, 18.0F, 14.0F, 13.0F).texOffs(62, 19).addBox(-6.0F, -9.0F, 2.0F, 12.0F, 3.0F, 9.0F).texOffs(104, 0).addBox(-10.0F, -3.0F, 2.0F, 1.0F, 9.0F, 9.0F).texOffs(104, 18).addBox(9.0F, -3.0F, 2.0F, 1.0F, 9.0F, 9.0F).texOffs(92, 51).addBox(-10.5F, -2.0F, 4.0F, 1.0F, 2.0F, 2.0F).texOffs(98, 51).addBox(9.5F, -1.0F, 7.0F, 1.0F, 2.0F, 2.0F).texOffs(104, 51).addBox(-10.5F, 2.0F, 8.0F, 1.0F, 2.0F, 2.0F).texOffs(110, 51).addBox(9.5F, 3.0F, 3.0F, 1.0F, 2.0F, 2.0F).texOffs(44, 39).addBox(-3.0F, -9.5F, 6.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        p_abdomen.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(108, 36).addBox(-4.0F, -3.0F, 0.0F, 8.0F, 7.0F, 2.0F).texOffs(52, 27).addBox(-2.0F, -1.0F, 2.0F, 4.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 0.0F, 0.0F, 0.0F));
        p_abdomen.addOrReplaceChild("belly_drip", CubeListBuilder.create().texOffs(124, 9).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-2.5F, 8.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        p_abdomen.addOrReplaceChild("boils", CubeListBuilder.create().texOffs(108, 45).addBox(-10.5F, -3.0F, 2.0F, 1.0F, 3.0F, 3.0F).texOffs(116, 45).addBox(9.5F, -3.0F, 8.0F, 1.0F, 3.0F, 3.0F).texOffs(22, 47).addBox(-4.0F, -10.0F, 7.0F, 3.0F, 1.0F, 3.0F).texOffs(48, 52).addBox(3.0F, -10.0F, 3.0F, 2.0F, 1.0F, 2.0F).texOffs(34, 47).addBox(-10.5F, 4.0F, 6.0F, 1.0F, 2.0F, 3.0F).texOffs(116, 51).addBox(9.5F, 4.0F, 9.0F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_weepling = root.addOrReplaceChild("weepling", CubeListBuilder.create().texOffs(26, 27).addBox(-3.5F, -8.0F, -3.0F, 7.0F, 6.0F, 6.0F).texOffs(62, 52).addBox(-0.5F, -9.0F, -1.0F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 21.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        p_weepling.addOrReplaceChild("wleg_fl", CubeListBuilder.create().texOffs(0, 50).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(2.0F, -2.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        p_weepling.addOrReplaceChild("wleg_fr", CubeListBuilder.create().texOffs(8, 50).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        p_weepling.addOrReplaceChild("wleg_bl", CubeListBuilder.create().texOffs(16, 51).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(2.0F, -2.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        p_weepling.addOrReplaceChild("wleg_br", CubeListBuilder.create().texOffs(24, 51).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_sac = root.addOrReplaceChild("sac", CubeListBuilder.create().texOffs(76, 36).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 1.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 21.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_sac_body = p_sac.addOrReplaceChild("sac_body", CubeListBuilder.create().texOffs(52, 31).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F).texOffs(22, 39).addBox(-3.5F, -5.0F, -2.0F, 7.0F, 4.0F, 4.0F).texOffs(0, 39).addBox(-2.0F, -5.0F, -3.5F, 4.0F, 4.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_sac_body.addOrReplaceChild("sac_cap", CubeListBuilder.create().texOffs(76, 31).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of weepling_broodmother. */
    public static final Map<String, String[]> WEEPLING_BROODMOTHER_ONLY = Map.ofEntries(
            Map.entry("boils", new String[] {"bloated_brood"}));

    public static LayerDefinition gleamtrap() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 8.0F, 16.0F).texOffs(100, 24).addBox(-7.0F, -11.0F, -7.0F, 14.0F, 3.0F, 0.0F).texOffs(52, 24).addBox(-7.0F, -11.0F, -6.5F, 0.0F, 3.0F, 12.0F).texOffs(76, 24).addBox(7.0F, -11.0F, -6.5F, 0.0F, 3.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(64, 0).addBox(-8.0F, -8.0F, -16.0F, 16.0F, 8.0F, 16.0F).texOffs(100, 27).addBox(-7.0F, 0.0F, -15.5F, 14.0F, 3.0F, 0.0F).texOffs(0, 24).addBox(-7.5F, 0.0F, -15.0F, 0.0F, 3.0F, 13.0F).texOffs(26, 24).addBox(7.5F, 0.0F, -15.0F, 0.0F, 3.0F, 13.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> GLEAMTRAP_ONLY = Map.of();

    public static LayerDefinition scarlet_mantis() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(74, 0).addBox(-2.5F, -2.0F, -3.0F, 5.0F, 4.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 13.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_abdomen = p_body.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(40, 0).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 5.0F, 11.0F).texOffs(0, 14).addBox(-2.0F, -1.5F, 11.0F, 4.0F, 4.0F, 3.0F).texOffs(24, 20).addBox(-1.0F, -0.5F, 14.0F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 3.0F, -0.2443F, 0.0F, 0.0F));
        p_abdomen.addOrReplaceChild("wings", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -0.5F, -1.0F, 7.0F, 1.0F, 13.0F), PartPose.offsetAndRotation(0.0F, -2.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_neck = p_body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(96, 0).addBox(-1.5F, -12.0F, -1.5F, 3.0F, 12.0F, 3.0F).texOffs(14, 14).addBox(-2.0F, -13.0F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -2.0F, -2.0F, 0.1396F, 0.0F, 0.0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(74, 10).addBox(-3.0F, -3.0F, -2.0F, 6.0F, 3.0F, 3.0F).texOffs(92, 15).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 3.0F).texOffs(100, 20).addBox(-1.0F, 2.0F, -2.0F, 2.0F, 1.0F, 2.0F).texOffs(14, 20).addBox(-4.5F, -4.0F, -2.5F, 2.0F, 3.0F, 3.0F).texOffs(56, 20).addBox(2.5F, -4.0F, -2.5F, 2.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -13.0F, -0.5F, -0.1396F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(92, 20).addBox(-0.5F, -9.0F, -0.5F, 1.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(1.0F, -3.0F, -1.5F, -0.6109F, 0.0F, 0.3142F));
        p_head.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(96, 20).addBox(-0.5F, -9.0F, -0.5F, 1.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(-1.0F, -3.0F, -1.5F, -0.6109F, 0.0F, -0.3142F));
        PartDefinition p_arm_left = p_neck.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(30, 14).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offsetAndRotation(2.0F, -11.0F, -1.5F, -0.5236F, 0.0F, 0.1047F));
        PartDefinition p_femur_left = p_arm_left.addOrReplaceChild("femur_left", CubeListBuilder.create().texOffs(108, 0).addBox(-1.0F, -11.0F, -2.0F, 2.0F, 11.0F, 3.0F).texOffs(92, 10).addBox(-0.5F, -3.0F, -3.0F, 1.0F, 1.0F, 1.0F).texOffs(92, 12).addBox(-0.5F, -5.5F, -3.0F, 1.0F, 1.0F, 1.0F).texOffs(100, 23).addBox(-0.5F, -8.0F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.8727F, 0.0F, 0.0F));
        p_femur_left.addOrReplaceChild("tibia_left", CubeListBuilder.create().texOffs(116, 14).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 9.0F, 2.0F).texOffs(104, 23).addBox(-0.5F, 7.5F, 0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -10.5F, -2.0F, -0.2094F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_neck.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(108, 14).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offsetAndRotation(-2.0F, -11.0F, -1.5F, -0.5236F, 0.0F, -0.1047F));
        PartDefinition p_femur_right = p_arm_right.addOrReplaceChild("femur_right", CubeListBuilder.create().texOffs(118, 0).addBox(-1.0F, -11.0F, -2.0F, 2.0F, 11.0F, 3.0F).texOffs(108, 23).addBox(-0.5F, -3.0F, -3.0F, 1.0F, 1.0F, 1.0F).texOffs(112, 23).addBox(-0.5F, -5.5F, -3.0F, 1.0F, 1.0F, 1.0F).texOffs(50, 24).addBox(-0.5F, -8.0F, -3.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.8727F, 0.0F, 0.0F));
        p_femur_right.addOrReplaceChild("tibia_right", CubeListBuilder.create().texOffs(122, 14).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 9.0F, 2.0F).texOffs(0, 25).addBox(-0.5F, 7.5F, 0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -10.5F, -2.0F, -0.2094F, 0.0F, 0.0F));
        PartDefinition p_leg_left_1 = p_body.addOrReplaceChild("leg_left_1", CubeListBuilder.create(), PartPose.offsetAndRotation(2.0F, 0.5F, -1.5F, 0.0F, 0.5934F, 0.0F));
        PartDefinition p_leg_left_1_thigh = p_leg_left_1.addOrReplaceChild("leg_left_1_thigh", CubeListBuilder.create().texOffs(38, 16).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3491F));
        p_leg_left_1_thigh.addOrReplaceChild("leg_left_1_shin", CubeListBuilder.create().texOffs(66, 20).addBox(-0.5F, -0.5F, -0.5F, 12.0F, 1.0F, 1.0F).texOffs(0, 21).addBox(11.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6581F));
        PartDefinition p_leg_left_2 = p_body.addOrReplaceChild("leg_left_2", CubeListBuilder.create(), PartPose.offsetAndRotation(2.0F, 0.5F, 2.5F, 0.0F, -0.6632F, 0.0F));
        PartDefinition p_leg_left_2_thigh = p_leg_left_2.addOrReplaceChild("leg_left_2_thigh", CubeListBuilder.create().texOffs(56, 16).addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3491F));
        p_leg_left_2_thigh.addOrReplaceChild("leg_left_2_shin", CubeListBuilder.create().texOffs(66, 22).addBox(-0.5F, -0.5F, -0.5F, 12.0F, 1.0F, 1.0F).texOffs(6, 21).addBox(11.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6581F));
        PartDefinition p_leg_right_1 = p_body.addOrReplaceChild("leg_right_1", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.0F, 0.5F, -1.5F, 0.0F, -0.5934F, 0.0F));
        PartDefinition p_leg_right_1_thigh = p_leg_right_1.addOrReplaceChild("leg_right_1_thigh", CubeListBuilder.create().texOffs(74, 16).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3491F));
        p_leg_right_1_thigh.addOrReplaceChild("leg_right_1_shin", CubeListBuilder.create().texOffs(24, 24).addBox(-11.5F, -0.5F, -0.5F, 12.0F, 1.0F, 1.0F).texOffs(0, 23).addBox(-13.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6581F));
        PartDefinition p_leg_right_2 = p_body.addOrReplaceChild("leg_right_2", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.0F, 0.5F, 2.5F, 0.0F, 0.6632F, 0.0F));
        PartDefinition p_leg_right_2_thigh = p_leg_right_2.addOrReplaceChild("leg_right_2_thigh", CubeListBuilder.create().texOffs(38, 20).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3491F));
        p_leg_right_2_thigh.addOrReplaceChild("leg_right_2_shin", CubeListBuilder.create().texOffs(66, 24).addBox(-11.5F, -0.5F, -0.5F, 12.0F, 1.0F, 1.0F).texOffs(6, 23).addBox(-13.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6581F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> SCARLET_MANTIS_ONLY = Map.of();

    public static LayerDefinition phantasmLantern() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(36, 9).addBox(-3.0F, -3.5F, -3.0F, 6.0F, 1.0F, 6.0F).texOffs(0, 14).addBox(-3.0F, 3.0F, -3.0F, 6.0F, 1.0F, 6.0F).texOffs(60, 9).addBox(-3.5F, -3.8F, -3.5F, 1.0F, 8.0F, 1.0F).texOffs(56, 16).addBox(2.5F, -3.8F, -3.5F, 1.0F, 8.0F, 1.0F).texOffs(60, 18).addBox(-3.5F, -3.8F, 2.5F, 1.0F, 8.0F, 1.0F).texOffs(52, 21).addBox(2.5F, -3.8F, 2.5F, 1.0F, 8.0F, 1.0F).texOffs(22, 25).addBox(-0.5F, -2.7F, -3.5F, 1.0F, 6.0F, 1.0F).texOffs(26, 25).addBox(-0.5F, -2.7F, 2.5F, 1.0F, 6.0F, 1.0F).texOffs(30, 25).addBox(-3.5F, -2.7F, -0.5F, 1.0F, 6.0F, 1.0F).texOffs(34, 25).addBox(2.5F, -2.7F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("cap_left", CubeListBuilder.create().texOffs(46, 25).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(40, 21).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(3.6F, -2.2F, 1.8F, 0.0F, 0.0F, 0.6109F));
        p_body.addOrReplaceChild("cap_right", CubeListBuilder.create().texOffs(8, 26).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(38, 25).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-3.6F, 2.0F, -2.0F, 0.0F, 0.0F, -0.9599F));
        p_body.addOrReplaceChild("flame", CubeListBuilder.create().texOffs(24, 16).addBox(-2.0F, -5.0F, -2.0F, 4.0F, 5.0F, 4.0F).texOffs(0, 26).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 2.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_hood = p_body.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -4.5F, -7.25F, 9.0F, 5.0F, 9.0F).texOffs(36, 0).addBox(-3.5F, -6.5F, -6.25F, 7.0F, 2.0F, 7.0F).texOffs(0, 21).addBox(-1.5F, -7.25F, -3.25F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -3.5F, 2.5F, 0.0F, 0.0F, 0.0F));
        p_hood.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(14, 21).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 2.0F, 2.0F).texOffs(14, 25).addBox(-1.0F, 0.5F, 2.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -6.5F, 0.75F, 0.5236F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("finial", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 1.0F, 4.0F).texOffs(56, 27).addBox(-1.0F, 1.0F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(24, 14).addBox(-0.5F, 2.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> PHANTASM_LANTERN_ONLY = Map.of();

    public static LayerDefinition woolcapGrazer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(62, 0).addBox(-5.0F, -3.0F, -8.0F, 10.0F, 6.0F, 16.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(120, 42).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 8.0F, 0.5236F, 0.0F, 0.0F));
        PartDefinition p_fleece = p_body.addOrReplaceChild("fleece", CubeListBuilder.create().texOffs(0, 0).addBox(-6.5F, -2.0F, -9.0F, 13.0F, 6.0F, 18.0F).texOffs(0, 24).addBox(-8.5F, -3.0F, -8.5F, 17.0F, 2.0F, 17.0F).texOffs(68, 22).addBox(-6.0F, -3.5F, -6.0F, 12.0F, 1.0F, 12.0F).texOffs(28, 46).addBox(-6.5F, 4.0F, -8.0F, 1.0F, 2.0F, 1.0F).texOffs(32, 46).addBox(5.5F, 4.0F, -8.0F, 1.0F, 2.0F, 1.0F).texOffs(44, 43).addBox(-6.5F, 4.0F, -6.0F, 1.0F, 3.0F, 1.0F).texOffs(62, 22).addBox(5.5F, 4.0F, -6.0F, 1.0F, 1.0F, 1.0F).texOffs(102, 47).addBox(-6.5F, 4.0F, -4.0F, 1.0F, 1.0F, 1.0F).texOffs(44, 47).addBox(5.5F, 4.0F, -4.0F, 1.0F, 2.0F, 1.0F).texOffs(68, 48).addBox(-6.5F, 4.0F, -2.0F, 1.0F, 1.0F, 1.0F).texOffs(72, 48).addBox(5.5F, 4.0F, -2.0F, 1.0F, 1.0F, 1.0F).texOffs(76, 48).addBox(-6.5F, 4.0F, 0.0F, 1.0F, 1.0F, 1.0F).texOffs(48, 43).addBox(5.5F, 4.0F, 0.0F, 1.0F, 3.0F, 1.0F).texOffs(48, 47).addBox(-6.5F, 4.0F, 2.0F, 1.0F, 2.0F, 1.0F).texOffs(52, 43).addBox(5.5F, 4.0F, 2.0F, 1.0F, 3.0F, 1.0F).texOffs(80, 48).addBox(-6.5F, 4.0F, 4.0F, 1.0F, 1.0F, 1.0F).texOffs(84, 48).addBox(5.5F, 4.0F, 4.0F, 1.0F, 1.0F, 1.0F).texOffs(56, 43).addBox(-6.5F, 4.0F, 6.0F, 1.0F, 3.0F, 1.0F).texOffs(60, 43).addBox(5.5F, 4.0F, 6.0F, 1.0F, 3.0F, 1.0F).texOffs(52, 47).addBox(-6.5F, 4.0F, 8.0F, 1.0F, 2.0F, 1.0F).texOffs(88, 48).addBox(5.5F, 4.0F, 8.0F, 1.0F, 1.0F, 1.0F).texOffs(106, 48).addBox(-5.0F, 4.0F, 8.0F, 1.0F, 1.0F, 1.0F).texOffs(64, 43).addBox(-3.0F, 4.0F, 8.0F, 1.0F, 3.0F, 1.0F).texOffs(56, 47).addBox(-1.0F, 4.0F, 8.0F, 1.0F, 2.0F, 1.0F).texOffs(60, 47).addBox(1.0F, 4.0F, 8.0F, 1.0F, 2.0F, 1.0F).texOffs(16, 46).addBox(3.0F, 4.0F, 8.0F, 1.0F, 3.0F, 1.0F).texOffs(64, 47).addBox(5.0F, 4.0F, 8.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_fleece.addOrReplaceChild("tufts", CubeListBuilder.create().texOffs(20, 46).addBox(-3.0F, -3.0F, -3.0F, 1.0F, 3.0F, 1.0F).texOffs(94, 47).addBox(2.0F, -2.0F, 1.0F, 1.0F, 2.0F, 1.0F).texOffs(24, 46).addBox(-1.0F, -3.0F, 3.0F, 1.0F, 3.0F, 1.0F).texOffs(98, 47).addBox(3.0F, -2.0F, -4.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_fleece.addOrReplaceChild("weeping", CubeListBuilder.create().texOffs(0, 43).addBox(-8.5F, 0.0F, -8.5F, 1.0F, 8.0F, 1.0F).texOffs(8, 43).addBox(7.5F, 0.0F, -8.5F, 1.0F, 7.0F, 1.0F).texOffs(12, 43).addBox(-8.5F, 0.0F, 7.5F, 1.0F, 7.0F, 1.0F).texOffs(4, 43).addBox(7.5F, 0.0F, 7.5F, 1.0F, 8.0F, 1.0F).texOffs(36, 43).addBox(-8.5F, 0.0F, -1.0F, 1.0F, 6.0F, 1.0F).texOffs(40, 43).addBox(7.5F, 0.0F, 2.0F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(68, 35).addBox(-3.0F, -3.0F, -7.0F, 6.0F, 6.0F, 7.0F).texOffs(106, 42).addBox(-2.0F, 0.0F, -10.0F, 4.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 9.5F, -8.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("head_fleece", CubeListBuilder.create().texOffs(106, 36).addBox(-3.0F, -1.5F, -4.5F, 6.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(16, 43).addBox(-3.0F, 0.0F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-3.0F, -2.0F, -3.0F, 0.0F, 0.0F, -0.6109F));
        p_head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(26, 43).addBox(0.0F, 0.0F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(3.0F, -2.0F, -3.0F, 0.0F, 0.0F, 0.6109F));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(114, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F), PartPose.offsetAndRotation(-3.0F, 15.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(116, 12).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F), PartPose.offsetAndRotation(3.0F, 15.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(116, 24).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F), PartPose.offsetAndRotation(-3.0F, 15.0F, 5.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(94, 35).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F), PartPose.offsetAndRotation(3.0F, 15.0F, 5.5F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of woolcap_grazer. */
    public static final Map<String, String[]> WOOLCAP_GRAZER_ONLY = Map.ofEntries(
            Map.entry("tufts", new String[] {"base"}),
            Map.entry("weeping", new String[] {"crimson_woolcap"}));

    public static LayerDefinition siphonjelly() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_bell = root.addOrReplaceChild("bell", CubeListBuilder.create().texOffs(100, 7).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 3.0F, 4.0F).texOffs(100, 0).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 1.0F, 6.0F).texOffs(0, 15).addBox(-5.0F, -3.0F, -5.0F, 10.0F, 2.0F, 10.0F).texOffs(52, 0).addBox(-6.0F, -1.0F, -6.0F, 12.0F, 3.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_bell.addOrReplaceChild("rim", CubeListBuilder.create().texOffs(0, 0).addBox(-6.5F, 0.0F, -6.5F, 13.0F, 2.0F, 13.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arms = root.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arms.addOrReplaceChild("arm_0", CubeListBuilder.create().texOffs(116, 7).addBox(-1.0F, 0.0F, 0.5F, 2.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));
        p_arms.addOrReplaceChild("arm_1", CubeListBuilder.create().texOffs(122, 7).addBox(-1.0F, 0.0F, 0.5F, 2.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 2.3562F, 0.0F));
        p_arms.addOrReplaceChild("arm_2", CubeListBuilder.create().texOffs(100, 14).addBox(-1.0F, 0.0F, 0.5F, 2.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.927F, 0.0F));
        p_arms.addOrReplaceChild("arm_3", CubeListBuilder.create().texOffs(106, 14).addBox(-1.0F, 0.0F, 0.5F, 2.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 5.4978F, 0.0F));
        PartDefinition p_tendril_0 = root.addOrReplaceChild("tendril_0", CubeListBuilder.create().texOffs(124, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(2.1F, 16.0F, -5.08F, 0.0F, 0.0F, 0.0F));
        p_tendril_0.addOrReplaceChild("lit_0", CubeListBuilder.create().texOffs(112, 14).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_0_tip = p_tendril_0.addOrReplaceChild("tendril_0_tip", CubeListBuilder.create().texOffs(40, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tendril_0_tip.addOrReplaceChild("lit_0_tip", CubeListBuilder.create().texOffs(44, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_1 = root.addOrReplaceChild("tendril_1", CubeListBuilder.create().texOffs(48, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(5.08F, 16.0F, -2.1F, 0.0F, 0.0F, 0.0F));
        p_tendril_1.addOrReplaceChild("lit_1", CubeListBuilder.create().texOffs(52, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_1_tip = p_tendril_1.addOrReplaceChild("tendril_1_tip", CubeListBuilder.create().texOffs(52, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tendril_1_tip.addOrReplaceChild("lit_1_tip", CubeListBuilder.create().texOffs(56, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_2 = root.addOrReplaceChild("tendril_2", CubeListBuilder.create().texOffs(56, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(5.08F, 16.0F, 2.1F, 0.0F, 0.0F, 0.0F));
        p_tendril_2.addOrReplaceChild("lit_2", CubeListBuilder.create().texOffs(60, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_2_tip = p_tendril_2.addOrReplaceChild("tendril_2_tip", CubeListBuilder.create().texOffs(64, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tendril_2_tip.addOrReplaceChild("lit_2_tip", CubeListBuilder.create().texOffs(68, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_3 = root.addOrReplaceChild("tendril_3", CubeListBuilder.create().texOffs(60, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(2.1F, 16.0F, 5.08F, 0.0F, 0.0F, 0.0F));
        p_tendril_3.addOrReplaceChild("lit_3", CubeListBuilder.create().texOffs(64, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_3_tip = p_tendril_3.addOrReplaceChild("tendril_3_tip", CubeListBuilder.create().texOffs(72, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tendril_3_tip.addOrReplaceChild("lit_3_tip", CubeListBuilder.create().texOffs(76, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_4 = root.addOrReplaceChild("tendril_4", CubeListBuilder.create().texOffs(80, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-2.1F, 16.0F, 5.08F, 0.0F, 0.0F, 0.0F));
        p_tendril_4.addOrReplaceChild("lit_4", CubeListBuilder.create().texOffs(84, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_4_tip = p_tendril_4.addOrReplaceChild("tendril_4_tip", CubeListBuilder.create().texOffs(88, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tendril_4_tip.addOrReplaceChild("lit_4_tip", CubeListBuilder.create().texOffs(92, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_5 = root.addOrReplaceChild("tendril_5", CubeListBuilder.create().texOffs(96, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-5.08F, 16.0F, 2.1F, 0.0F, 0.0F, 0.0F));
        p_tendril_5.addOrReplaceChild("lit_5", CubeListBuilder.create().texOffs(116, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_5_tip = p_tendril_5.addOrReplaceChild("tendril_5_tip", CubeListBuilder.create().texOffs(68, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tendril_5_tip.addOrReplaceChild("lit_5_tip", CubeListBuilder.create().texOffs(72, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_6 = root.addOrReplaceChild("tendril_6", CubeListBuilder.create().texOffs(76, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-5.08F, 16.0F, -2.1F, 0.0F, 0.0F, 0.0F));
        p_tendril_6.addOrReplaceChild("lit_6", CubeListBuilder.create().texOffs(80, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_6_tip = p_tendril_6.addOrReplaceChild("tendril_6_tip", CubeListBuilder.create().texOffs(120, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tendril_6_tip.addOrReplaceChild("lit_6_tip", CubeListBuilder.create().texOffs(124, 15).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_7 = root.addOrReplaceChild("tendril_7", CubeListBuilder.create().texOffs(112, 20).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-2.1F, 16.0F, -5.08F, 0.0F, 0.0F, 0.0F));
        p_tendril_7.addOrReplaceChild("lit_7", CubeListBuilder.create().texOffs(40, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_7_tip = p_tendril_7.addOrReplaceChild("tendril_7_tip", CubeListBuilder.create().texOffs(44, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tendril_7_tip.addOrReplaceChild("lit_7_tip", CubeListBuilder.create().texOffs(48, 21).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> SIPHONJELLY_ONLY = Map.of();

    public static LayerDefinition sporeCannon() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_base = root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -3.0F, -7.0F, 14.0F, 3.0F, 14.0F).texOffs(40, 46).addBox(-5.0F, -4.0F, -5.0F, 10.0F, 1.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_base.addOrReplaceChild("twisting_0", CubeListBuilder.create().texOffs(120, 55).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(5.91F, -3.0F, 1.04F, -0.3142F, 1.7453F, 0.0F));
        p_base.addOrReplaceChild("twisting_1", CubeListBuilder.create().texOffs(24, 55).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(-3.86F, -3.0F, 4.6F, -0.3142F, 3.8397F, 0.0F));
        p_base.addOrReplaceChild("twisting_2", CubeListBuilder.create().texOffs(40, 57).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(-2.05F, -3.0F, -5.64F, -0.3142F, 5.9341F, 0.0F));
        root.addOrReplaceChild("root_0", CubeListBuilder.create().texOffs(112, 0).addBox(-1.5F, -1.5F, -11.0F, 3.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 22.5F, 0.0F, 0.1396F, 0.3491F, 0.0F));
        root.addOrReplaceChild("root_1", CubeListBuilder.create().texOffs(112, 8).addBox(-1.5F, -1.5F, -11.0F, 3.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 22.5F, 0.0F, 0.1396F, 1.6057F, 0.0F));
        root.addOrReplaceChild("root_2", CubeListBuilder.create().texOffs(112, 33).addBox(-1.5F, -1.5F, -11.0F, 3.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 22.5F, 0.0F, 0.1396F, 2.8623F, 0.0F));
        root.addOrReplaceChild("root_3", CubeListBuilder.create().texOffs(112, 41).addBox(-1.5F, -1.5F, -11.0F, 3.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 22.5F, 0.0F, 0.1396F, 4.119F, 0.0F));
        root.addOrReplaceChild("root_4", CubeListBuilder.create().texOffs(24, 47).addBox(-1.5F, -1.5F, -11.0F, 3.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 22.5F, 0.0F, 0.1396F, 5.3756F, 0.0F));
        PartDefinition p_stem = root.addOrReplaceChild("stem", CubeListBuilder.create().texOffs(80, 17).addBox(-4.5F, -7.0F, -4.5F, 9.0F, 7.0F, 9.0F).texOffs(40, 34).addBox(-5.0F, -2.0F, -5.0F, 10.0F, 2.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_stem.addOrReplaceChild("shelf_0", CubeListBuilder.create().texOffs(96, 50).addBox(-2.5F, -1.0F, -7.5F, 5.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -2.5F, 0.0F, 0.0F, 1.2217F, 0.0F));
        p_stem.addOrReplaceChild("shelf_1", CubeListBuilder.create().texOffs(0, 54).addBox(-2.5F, -1.0F, -7.5F, 5.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.0F, 3.4034F, 0.0F));
        p_stem.addOrReplaceChild("shelf_2", CubeListBuilder.create().texOffs(96, 54).addBox(-2.5F, -1.0F, -7.5F, 5.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -5.5F, 0.0F, 0.0F, 5.5851F, 0.0F));
        PartDefinition p_collar = p_stem.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(56, 0).addBox(-7.0F, -3.0F, -7.0F, 14.0F, 3.0F, 14.0F), PartPose.offsetAndRotation(0.0F, -7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("drip_0", CubeListBuilder.create().texOffs(48, 57).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(1.04F, 0.0F, -5.91F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("drip_1", CubeListBuilder.create().texOffs(32, 55).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(5.27F, 0.0F, -2.87F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("drip_2", CubeListBuilder.create().texOffs(116, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(5.53F, 0.0F, 2.33F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("drip_3", CubeListBuilder.create().texOffs(56, 57).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(1.63F, 0.0F, 5.78F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("drip_4", CubeListBuilder.create().texOffs(112, 55).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(-3.5F, 0.0F, 4.87F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("drip_5", CubeListBuilder.create().texOffs(16, 54).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(-5.99F, 0.0F, 0.3F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("drip_6", CubeListBuilder.create().texOffs(64, 57).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-3.97F, 0.0F, -4.5F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("weeping_0", CubeListBuilder.create().texOffs(80, 50).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(4.18F, 0.0F, -4.98F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("weeping_1", CubeListBuilder.create().texOffs(116, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), PartPose.offsetAndRotation(2.22F, 0.0F, 6.11F, 0.0F, 0.0F, 0.0F));
        p_collar.addOrReplaceChild("weeping_2", CubeListBuilder.create().texOffs(88, 50).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(-6.4F, 0.0F, -1.13F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_bulb = p_collar.addOrReplaceChild("bulb", CubeListBuilder.create().texOffs(80, 33).addBox(-4.0F, -9.0F, -4.0F, 8.0F, 9.0F, 8.0F).texOffs(0, 17).addBox(-5.0F, -8.0F, -5.0F, 10.0F, 7.0F, 10.0F).texOffs(0, 34).addBox(-6.0F, -7.0F, -4.0F, 12.0F, 5.0F, 8.0F).texOffs(40, 17).addBox(-4.0F, -7.0F, -6.0F, 8.0F, 5.0F, 12.0F).texOffs(0, 47).addBox(-3.0F, -10.0F, -3.0F, 6.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_bulb.addOrReplaceChild("vent", CubeListBuilder.create().texOffs(112, 49).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -10.0F, -0.5F, -0.2443F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of spore_cannon. */
    public static final Map<String, String[]> SPORE_CANNON_ONLY = Map.ofEntries(
            Map.entry("twisting_0", new String[] {"base"}),
            Map.entry("twisting_1", new String[] {"base"}),
            Map.entry("twisting_2", new String[] {"base"}),
            Map.entry("weeping_0", new String[] {"crimson_cannon"}),
            Map.entry("weeping_1", new String[] {"crimson_cannon"}),
            Map.entry("weeping_2", new String[] {"crimson_cannon"}));

    public static LayerDefinition gloomweaver() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 23).addBox(-5.0F, -3.0F, -9.0F, 10.0F, 5.0F, 9.0F).texOffs(38, 31).addBox(-4.0F, -4.2F, -8.0F, 8.0F, 2.0F, 7.0F).texOffs(68, 31).addBox(-4.0F, 1.5F, -8.0F, 8.0F, 1.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 17.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(106, 11).addBox(-3.5F, -2.5F, -3.0F, 7.0F, 4.0F, 3.0F).texOffs(120, 30).addBox(-2.5F, -2.3F, -3.4F, 2.0F, 2.0F, 1.0F).texOffs(118, 33).addBox(0.5F, -2.3F, -3.4F, 2.0F, 2.0F, 1.0F).texOffs(124, 33).addBox(-3.8F, -1.6F, -2.4F, 1.0F, 1.0F, 1.0F).texOffs(94, 39).addBox(2.8F, -1.6F, -2.4F, 1.0F, 1.0F, 1.0F).texOffs(64, 40).addBox(-2.0F, -3.0F, -2.2F, 1.0F, 1.0F, 1.0F).texOffs(74, 43).addBox(1.0F, -3.0F, -2.2F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -0.5F, -9.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("fang_left", CubeListBuilder.create().texOffs(120, 18).addBox(-1.0F, -0.5F, -1.2F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(1.5F, 1.2F, -2.6F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("fang_right", CubeListBuilder.create().texOffs(120, 24).addBox(-1.0F, -0.5F, -1.2F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(-1.5F, 1.2F, -2.6F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_abdomen = p_body.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -5.0F, 0.0F, 12.0F, 9.0F, 14.0F).texOffs(52, 0).addBox(-7.5F, -3.5F, 1.0F, 15.0F, 6.0F, 12.0F).texOffs(52, 18).addBox(-5.0F, -6.5F, 1.5F, 10.0F, 2.0F, 11.0F).texOffs(0, 37).addBox(-4.5F, -4.0F, 14.0F, 9.0F, 7.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_spinnerets = p_abdomen.addOrReplaceChild("spinnerets", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -3.0F, 15.5F, 0.0F, 0.0F, 0.0F));
        p_spinnerets.addOrReplaceChild("spinneret_mid", CubeListBuilder.create().texOffs(38, 23).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.4363F, 0.0F, 0.0F));
        p_spinnerets.addOrReplaceChild("spinneret_left", CubeListBuilder.create().texOffs(114, 49).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(2.6F, 1.2F, 0.0F, 0.2443F, 0.5236F, 0.0F));
        p_spinnerets.addOrReplaceChild("spinneret_right", CubeListBuilder.create().texOffs(80, 53).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-2.6F, 1.2F, 0.0F, 0.2443F, -0.5236F, 0.0F));
        PartDefinition p_vines = p_spinnerets.addOrReplaceChild("vines", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 2.5F, 0.0F, 0.0F, 0.0F));
        p_vines.addOrReplaceChild("vine_mid", CubeListBuilder.create().texOffs(94, 18).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 11.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, -0.8727F, 0.0F, 0.0F));
        p_vines.addOrReplaceChild("vine_left", CubeListBuilder.create().texOffs(106, 0).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 10.0F), PartPose.offsetAndRotation(2.8F, 1.2F, 0.0F, -1.0821F, 0.4538F, 0.0F));
        p_vines.addOrReplaceChild("vine_right", CubeListBuilder.create().texOffs(98, 31).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 9.0F), PartPose.offsetAndRotation(-2.8F, 1.2F, 0.0F, -1.1519F, -0.4189F, 0.0F));
        PartDefinition p_leg_left_1 = p_body.addOrReplaceChild("leg_left_1", CubeListBuilder.create(), PartPose.offsetAndRotation(4.8F, 0.5F, -7.5F, 0.0F, 0.8378F, 0.0F));
        PartDefinition p_leg_left_1_thigh = p_leg_left_1.addOrReplaceChild("leg_left_1_thigh", CubeListBuilder.create().texOffs(0, 46).addBox(0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0472F));
        p_leg_left_1_thigh.addOrReplaceChild("leg_left_1_shin", CubeListBuilder.create().texOffs(68, 39).addBox(-0.5F, -0.85F, -0.85F, 11.0F, 2.0F, 2.0F).texOffs(118, 36).addBox(10.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(8.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.1817F));
        PartDefinition p_leg_left_2 = p_body.addOrReplaceChild("leg_left_2", CubeListBuilder.create(), PartPose.offsetAndRotation(4.8F, 0.5F, -5.3F, 0.0F, 0.3142F, 0.0F));
        PartDefinition p_leg_left_2_thigh = p_leg_left_2.addOrReplaceChild("leg_left_2_thigh", CubeListBuilder.create().texOffs(20, 48).addBox(0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0472F));
        p_leg_left_2_thigh.addOrReplaceChild("leg_left_2_shin", CubeListBuilder.create().texOffs(22, 40).addBox(-0.5F, -0.85F, -0.85F, 11.0F, 2.0F, 2.0F).texOffs(22, 37).addBox(10.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(8.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.1817F));
        PartDefinition p_leg_left_3 = p_body.addOrReplaceChild("leg_left_3", CubeListBuilder.create(), PartPose.offsetAndRotation(4.8F, 0.5F, -3.1F, 0.0F, -0.2443F, 0.0F));
        PartDefinition p_leg_left_3_thigh = p_leg_left_3.addOrReplaceChild("leg_left_3_thigh", CubeListBuilder.create().texOffs(74, 49).addBox(0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0472F));
        p_leg_left_3_thigh.addOrReplaceChild("leg_left_3_shin", CubeListBuilder.create().texOffs(94, 41).addBox(-0.5F, -0.85F, -0.85F, 11.0F, 2.0F, 2.0F).texOffs(30, 37).addBox(10.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(8.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.1817F));
        PartDefinition p_leg_left_4 = p_body.addOrReplaceChild("leg_left_4", CubeListBuilder.create(), PartPose.offsetAndRotation(4.8F, 0.5F, -0.9F, 0.0F, -0.7679F, 0.0F));
        PartDefinition p_leg_left_4_thigh = p_leg_left_4.addOrReplaceChild("leg_left_4_thigh", CubeListBuilder.create().texOffs(94, 49).addBox(0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0472F));
        p_leg_left_4_thigh.addOrReplaceChild("leg_left_4_shin", CubeListBuilder.create().texOffs(48, 43).addBox(-0.5F, -0.85F, -0.85F, 11.0F, 2.0F, 2.0F).texOffs(118, 38).addBox(10.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(8.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.1817F));
        PartDefinition p_leg_right_1 = p_body.addOrReplaceChild("leg_right_1", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.8F, 0.5F, -7.5F, 0.0F, -0.8378F, 0.0F));
        PartDefinition p_leg_right_1_thigh = p_leg_right_1.addOrReplaceChild("leg_right_1_thigh", CubeListBuilder.create().texOffs(0, 50).addBox(-8.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0472F));
        p_leg_right_1_thigh.addOrReplaceChild("leg_right_1_shin", CubeListBuilder.create().texOffs(22, 44).addBox(-10.5F, -0.85F, -0.85F, 11.0F, 2.0F, 2.0F).texOffs(48, 40).addBox(-13.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-8.0F, 0.0F, 0.0F, 0.0F, 0.0F, -2.1817F));
        PartDefinition p_leg_right_2 = p_body.addOrReplaceChild("leg_right_2", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.8F, 0.5F, -5.3F, 0.0F, -0.3142F, 0.0F));
        PartDefinition p_leg_right_2_thigh = p_leg_right_2.addOrReplaceChild("leg_right_2_thigh", CubeListBuilder.create().texOffs(40, 51).addBox(-8.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0472F));
        p_leg_right_2_thigh.addOrReplaceChild("leg_right_2_shin", CubeListBuilder.create().texOffs(74, 45).addBox(-10.5F, -0.85F, -0.85F, 11.0F, 2.0F, 2.0F).texOffs(56, 40).addBox(-13.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-8.0F, 0.0F, 0.0F, 0.0F, 0.0F, -2.1817F));
        PartDefinition p_leg_right_3 = p_body.addOrReplaceChild("leg_right_3", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.8F, 0.5F, -3.1F, 0.0F, 0.2443F, 0.0F));
        PartDefinition p_leg_right_3_thigh = p_leg_right_3.addOrReplaceChild("leg_right_3_thigh", CubeListBuilder.create().texOffs(20, 52).addBox(-8.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0472F));
        p_leg_right_3_thigh.addOrReplaceChild("leg_right_3_shin", CubeListBuilder.create().texOffs(100, 45).addBox(-10.5F, -0.85F, -0.85F, 11.0F, 2.0F, 2.0F).texOffs(120, 40).addBox(-13.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-8.0F, 0.0F, 0.0F, 0.0F, 0.0F, -2.1817F));
        PartDefinition p_leg_right_4 = p_body.addOrReplaceChild("leg_right_4", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.8F, 0.5F, -0.9F, 0.0F, 0.7679F, 0.0F));
        PartDefinition p_leg_right_4_thigh = p_leg_right_4.addOrReplaceChild("leg_right_4_thigh", CubeListBuilder.create().texOffs(60, 53).addBox(-8.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0472F));
        p_leg_right_4_thigh.addOrReplaceChild("leg_right_4_shin", CubeListBuilder.create().texOffs(48, 47).addBox(-10.5F, -0.85F, -0.85F, 11.0F, 2.0F, 2.0F).texOffs(120, 42).addBox(-13.0F, -0.4F, -0.4F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-8.0F, 0.0F, 0.0F, 0.0F, 0.0F, -2.1817F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> GLOOMWEAVER_ONLY = Map.of();

    public static LayerDefinition ossuary_wyrm() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_skull = root.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -4.5F, -1.0F, 10.0F, 8.0F, 8.0F).texOffs(32, 31).addBox(-3.5F, -3.0F, -8.0F, 7.0F, 5.0F, 8.0F).texOffs(20, 55).addBox(-5.5F, -5.2F, -2.5F, 11.0F, 2.0F, 4.0F, new CubeDeformation(0.15F)).texOffs(116, 60).addBox(4.15F, -3.5F, -1.5F, 1.0F, 3.0F, 4.0F).texOffs(108, 68).addBox(4.3F, -3.0F, -1.0F, 1.0F, 2.0F, 3.0F).texOffs(86, 41).addBox(2.0F, 1.6F, -7.6F, 1.0F, 2.0F, 1.0F).texOffs(20, 61).addBox(-5.15F, -3.5F, -1.5F, 1.0F, 3.0F, 4.0F).texOffs(76, 70).addBox(-5.3F, -3.0F, -1.0F, 1.0F, 2.0F, 3.0F).texOffs(90, 41).addBox(-3.0F, 1.6F, -7.6F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.64F, -7.36F, 0.2094F, 0.0F, 0.0F));
        p_skull.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(36, 0).addBox(-3.0F, 0.0F, -11.0F, 6.0F, 3.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 2.15F, 3.0F, 0.0F, 0.0F, 0.0F));
        p_skull.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(110, 44).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F).texOffs(50, 63).addBox(-0.5F, -0.5F, 5.5F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(3.5F, -4.2F, 3.5F, 0.5585F, 0.384F, 0.0F));
        p_skull.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(110, 52).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F).texOffs(86, 63).addBox(-0.5F, -0.5F, 5.5F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-3.5F, -4.2F, 3.5F, 0.5585F, -0.384F, 0.0F));
        root.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(72, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.15F)).texOffs(0, 44).addBox(-2.0F, -1.5F, 3.0F, 4.0F, 4.0F, 7.0F).texOffs(86, 32).addBox(-1.0F, -9.0F, -1.5F, 2.0F, 6.0F, 3.0F).texOffs(24, 32).addBox(-0.5F, -11.0F, 0.0F, 1.0F, 3.0F, 3.0F).texOffs(104, 12).addBox(3.5F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(32, 16).addBox(6.4F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F).texOffs(116, 12).addBox(-7.0F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(32, 21).addBox(-7.4F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 13.76F, -3.2F, -0.8831F, -0.0F, 0.0F));
        root.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(36, 15).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F).texOffs(22, 44).addBox(-2.0F, -1.5F, 3.0F, 4.0F, 4.0F, 7.0F).texOffs(66, 55).addBox(-1.0F, -9.0F, -1.5F, 2.0F, 6.0F, 3.0F).texOffs(24, 38).addBox(-0.5F, -11.0F, 0.0F, 1.0F, 3.0F, 3.0F).texOffs(16, 68).addBox(3.5F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(32, 26).addBox(6.4F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F).texOffs(28, 68).addBox(-7.0F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(96, 32).addBox(-7.4F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 17.6F, -0.0F, -0.2675F, 0.9407F, 0.0F));
        root.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.15F)).texOffs(44, 44).addBox(-2.0F, -1.5F, 3.0F, 4.0F, 4.0F, 7.0F).texOffs(76, 55).addBox(-1.0F, -9.0F, -1.5F, 2.0F, 6.0F, 3.0F).texOffs(60, 64).addBox(-0.5F, -11.0F, 0.0F, 1.0F, 3.0F, 3.0F).texOffs(40, 68).addBox(3.5F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(96, 37).addBox(6.4F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F).texOffs(84, 68).addBox(-7.0F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(106, 55).addBox(-7.4F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(11.3186F, 17.6F, 5.0546F, -0.0F, 0.7308F, 0.0F));
        root.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(68, 16).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F).texOffs(66, 44).addBox(-2.0F, -1.5F, 3.0F, 4.0F, 4.0F, 7.0F).texOffs(86, 55).addBox(-1.0F, -8.0F, -1.5F, 2.0F, 5.0F, 3.0F).texOffs(68, 64).addBox(-0.5F, -10.0F, 0.0F, 1.0F, 3.0F, 3.0F).texOffs(96, 68).addBox(3.5F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(124, 67).addBox(6.4F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(52, 70).addBox(-7.0F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(124, 71).addBox(-7.4F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(15.1095F, 17.6F, 16.8566F, -0.0F, -0.1092F, 0.0F));
        root.addOrReplaceChild("seg5", CubeListBuilder.create().texOffs(100, 16).addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.15F)).texOffs(104, 0).addBox(-2.0F, -1.5F, 2.5F, 4.0F, 4.0F, 8.0F).texOffs(96, 55).addBox(-1.0F, -7.5F, -1.5F, 2.0F, 5.0F, 3.0F).texOffs(76, 64).addBox(-0.5F, -9.5F, 0.0F, 1.0F, 3.0F, 3.0F).texOffs(64, 70).addBox(3.0F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(104, 72).addBox(5.9F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(0, 71).addBox(-6.5F, -1.5F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(108, 73).addBox(-6.9F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(8.8514F, 17.6F, 27.5569F, -0.0F, -0.9492F, 0.0F));
        root.addOrReplaceChild("seg6", CubeListBuilder.create().texOffs(100, 30).addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F).texOffs(88, 44).addBox(-1.5F, -1.0F, 2.5F, 3.0F, 3.0F, 8.0F).texOffs(106, 60).addBox(-1.0F, -7.5F, -1.5F, 2.0F, 5.0F, 3.0F).texOffs(0, 65).addBox(-0.5F, -9.5F, 0.0F, 1.0F, 3.0F, 3.0F).texOffs(12, 72).addBox(3.0F, -1.5F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(112, 73).addBox(4.9F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(22, 72).addBox(-5.5F, -1.5F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(116, 73).addBox(-5.9F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-3.2935F, 17.6F, 30.0389F, -0.0F, -1.7892F, 0.0F));
        root.addOrReplaceChild("seg7", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.15F)).texOffs(62, 32).addBox(-1.5F, -1.0F, 2.0F, 3.0F, 3.0F, 9.0F).texOffs(30, 61).addBox(-1.0F, -6.0F, -1.5F, 2.0F, 4.0F, 3.0F).texOffs(8, 65).addBox(-0.5F, -8.0F, 0.0F, 1.0F, 3.0F, 3.0F).texOffs(32, 72).addBox(2.5F, -1.5F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(16, 65).addBox(4.4F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(42, 72).addBox(-5.0F, -1.5F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(120, 73).addBox(-5.4F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-13.248F, 17.6F, 22.652F, -0.0F, -2.6292F, 0.0F));
        root.addOrReplaceChild("seg8", CubeListBuilder.create().texOffs(0, 55).addBox(-2.5F, -2.5F, -2.5F, 5.0F, 5.0F, 5.0F).texOffs(40, 61).addBox(-1.0F, -5.5F, -1.5F, 2.0F, 4.0F, 3.0F).texOffs(116, 67).addBox(-0.5F, -7.5F, 0.0F, 1.0F, 3.0F, 3.0F).texOffs(84, 72).addBox(2.0F, -1.5F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(52, 74).addBox(3.9F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(94, 72).addBox(-4.5F, -1.5F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(56, 74).addBox(-4.9F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(50, 55).addBox(-1.0F, -1.0F, 2.0F, 2.0F, 2.0F, 6.0F).texOffs(96, 63).addBox(-0.5F, -0.5F, 7.5F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-14.3916F, 17.6F, 10.3089F, -0.0F, -3.0492F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static final Map<String, String[]> OSSUARY_WYRM_ONLY = Map.of();

    public static LayerDefinition keeningShade() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(36, 0).addBox(-5.0F, -4.5F, -3.0F, 10.0F, 5.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(112, 0).addBox(-2.5F, -4.0F, -1.5F, 5.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("hollow", CubeListBuilder.create().texOffs(22, 11).addBox(-2.5F, -4.0F, -1.5F, 5.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_skirt = p_body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(68, 0).addBox(-4.0F, -0.5F, -3.0F, 8.0F, 4.0F, 6.0F).texOffs(0, 0).addBox(-5.0F, 3.0F, -4.0F, 10.0F, 3.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tail = p_skirt.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 11).addBox(-3.5F, 0.0F, -2.5F, 7.0F, 6.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 1.5F, 0.9599F, 0.0F, 0.0F));
        p_tail.addOrReplaceChild("wisp", CubeListBuilder.create().texOffs(16, 22).addBox(-2.0F, 0.0F, -1.5F, 4.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 5.0F, -0.5F, 0.3491F, 0.0F, 0.0F));
        PartDefinition p_neck = p_body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(88, 10).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -4.5F, -1.0F, 0.384F, 0.0F, 0.0F));
        p_neck.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(108, 23).addBox(-2.4F, -0.5F, -1.8F, 1.0F, 2.0F, 1.0F).texOffs(122, 23).addBox(1.4F, -0.5F, -1.8F, 1.0F, 2.0F, 1.0F).texOffs(58, 25).addBox(-2.8F, -0.5F, 0.2F, 1.0F, 2.0F, 1.0F).texOffs(62, 25).addBox(1.8F, -0.5F, 0.2F, 1.0F, 2.0F, 1.0F).texOffs(112, 25).addBox(-0.5F, -0.5F, 1.6F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(68, 10).addBox(-2.5F, -7.0F, -3.5F, 5.0F, 7.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 0.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 21).addBox(-2.0F, 0.0F, -4.3F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.15F, 1.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("maw", CubeListBuilder.create().texOffs(76, 22).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -3.65F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("veil", CubeListBuilder.create().texOffs(88, 17).addBox(-3.5F, -0.5F, -4.5F, 7.0F, 1.0F, 5.0F).texOffs(96, 0).addBox(-3.5F, 0.0F, 0.0F, 7.0F, 16.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -7.5F, 1.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("lock_right", CubeListBuilder.create().texOffs(38, 11).addBox(-0.5F, 0.0F, -3.3F, 1.0F, 13.0F, 4.0F), PartPose.offsetAndRotation(-3.0F, -7.0F, -0.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("lock_left", CubeListBuilder.create().texOffs(48, 11).addBox(-0.5F, 0.0F, -3.3F, 1.0F, 13.0F, 4.0F), PartPose.offsetAndRotation(3.0F, -7.0F, -0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(30, 22).addBox(-1.5F, -1.0F, -1.0F, 2.0F, 11.0F, 2.0F), PartPose.offsetAndRotation(-4.5F, -3.6F, 0.0F, -0.2094F, 0.0F, 0.1047F));
        p_arm_right.addOrReplaceChild("drape_right", CubeListBuilder.create().texOffs(58, 11).addBox(-3.0F, 0.0F, -1.0F, 3.0F, 12.0F, 2.0F), PartPose.offsetAndRotation(-1.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(84, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(100, 23).addBox(-1.0F, 1.0F, -1.0F, 1.0F, 4.0F, 1.0F).texOffs(122, 11).addBox(0.2F, 1.0F, 0.2F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-0.5F, 9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(68, 22).addBox(-0.5F, -1.0F, -1.0F, 2.0F, 11.0F, 2.0F), PartPose.offsetAndRotation(4.5F, -3.6F, 0.0F, -0.2094F, 0.0F, -0.1047F));
        p_arm_left.addOrReplaceChild("drape_left", CubeListBuilder.create().texOffs(112, 11).addBox(0.0F, 0.0F, -1.0F, 3.0F, 12.0F, 2.0F), PartPose.offsetAndRotation(1.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(92, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(104, 23).addBox(-1.0F, 1.0F, -1.0F, 1.0F, 4.0F, 1.0F).texOffs(122, 17).addBox(0.2F, 1.0F, 0.2F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.5F, 9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of keening_shade. */
    public static final Map<String, String[]> KEENING_SHADE_ONLY = Map.ofEntries(
            Map.entry("chest", new String[] {"base"}),
            Map.entry("collar", new String[] {"hollow_shade"}),
            Map.entry("hollow", new String[] {"hollow_shade"}));

    public static LayerDefinition soulfireSeer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(0, 46).addBox(-1.0F, 1.0F, -4.5F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.7F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(10, 46).addBox(-1.0F, 1.0F, -4.5F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.7F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(96, 0).addBox(-3.5F, -9.5F, -2.5F, 7.0F, 10.0F, 5.0F).texOffs(68, 37).addBox(-4.0F, -1.5F, -3.0F, 8.0F, 1.0F, 6.0F).texOffs(0, 29).addBox(-5.0F, -10.5F, -3.0F, 10.0F, 2.0F, 6.0F).texOffs(0, 20).addBox(-5.0F, -8.5F, -3.0F, 10.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(68, 0).addBox(-4.0F, -0.5F, -3.0F, 8.0F, 7.0F, 6.0F).texOffs(34, 0).addBox(-5.0F, 6.5F, -3.5F, 10.0F, 7.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(110, 15).addBox(-5.4F, -2.5F, -3.5F, 1.0F, 4.0F, 7.0F).texOffs(32, 32).addBox(-3.2F, -2.5F, -3.5F, 1.0F, 4.0F, 7.0F).texOffs(96, 37).addBox(2.2F, -2.5F, -3.5F, 1.0F, 4.0F, 7.0F).texOffs(112, 37).addBox(4.4F, -2.5F, -3.5F, 1.0F, 4.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(120, 10).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(86, 15).addBox(-3.0F, -8.5F, -3.5F, 6.0F, 7.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(68, 31).addBox(-2.5F, 0.0F, -3.2F, 5.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -2.5F, -0.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("eye", CubeListBuilder.create().texOffs(26, 37).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -5.0F, -3.62F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("flame", CubeListBuilder.create().texOffs(26, 40).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F).texOffs(42, 50).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.5F, -6.0F, -4.15F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_hood = p_head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.5F, -5.5F, 8.0F, 11.0F, 9.0F).texOffs(86, 28).addBox(-3.5F, -10.4F, -5.4F, 7.0F, 1.0F, 8.0F).texOffs(68, 13).addBox(-3.9F, -10.0F, -5.4F, 1.0F, 10.0F, 8.0F).texOffs(34, 14).addBox(2.9F, -10.0F, -5.4F, 1.0F, 10.0F, 8.0F).texOffs(26, 43).addBox(-3.5F, -10.0F, 2.4F, 7.0F, 10.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hood.addOrReplaceChild("hood_peak", CubeListBuilder.create().texOffs(0, 37).addBox(-3.5F, -3.0F, -3.0F, 7.0F, 3.0F, 6.0F).texOffs(52, 44).addBox(-2.5F, -5.0F, -2.0F, 5.0F, 2.0F, 4.0F).texOffs(116, 26).addBox(-1.5F, -7.0F, -1.0F, 3.0F, 2.0F, 3.0F).texOffs(42, 43).addBox(-0.5F, -8.0F, 0.0F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -10.0F, 1.0F, -0.384F, 0.0F, 0.0F));
        PartDefinition p_crest = p_head.addOrReplaceChild("crest", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -8.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_crest.addOrReplaceChild("crest_0", CubeListBuilder.create().texOffs(70, 48).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(52, 50).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-3.0F, 0.0F, -0.5F, 0.0F, 0.0F, -0.3142F));
        p_crest.addOrReplaceChild("crest_1", CubeListBuilder.create().texOffs(78, 48).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(56, 50).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(60, 50).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-1.0F, 0.0F, -0.5F, 0.0F, 0.0F, -0.1047F));
        p_crest.addOrReplaceChild("crest_2", CubeListBuilder.create().texOffs(86, 48).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(64, 50).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(118, 51).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(1.0F, 0.0F, -0.5F, 0.0F, 0.0F, 0.1047F));
        p_crest.addOrReplaceChild("crest_3", CubeListBuilder.create().texOffs(94, 48).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(122, 51).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(3.0F, 0.0F, -0.5F, 0.0F, 0.0F, 0.3142F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(52, 14).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 11.0F, 4.0F).texOffs(120, 0).addBox(-0.5F, 3.0F, -0.5F, 1.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(-4.2F, -8.5F, 0.0F, 0.0F, 0.0F, 0.0698F));
        p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(102, 48).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(68, 52).addBox(-1.0F, 2.0F, -1.0F, 1.0F, 2.0F, 1.0F).texOffs(72, 52).addBox(0.2F, 2.0F, 0.2F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(52, 29).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 11.0F, 4.0F).texOffs(124, 0).addBox(-0.5F, 3.0F, -0.5F, 1.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(4.2F, -8.5F, 0.0F, 0.0F, 0.0F, -0.0698F));
        PartDefinition p_hand_left = p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(110, 48).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(76, 52).addBox(-1.0F, 2.0F, -1.0F, 1.0F, 2.0F, 1.0F).texOffs(80, 52).addBox(0.2F, 2.0F, 0.2F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_left.addOrReplaceChild("staff", CubeListBuilder.create().texOffs(48, 32).addBox(-0.5F, -29.5F, -0.5F, 1.0F, 39.0F, 1.0F).texOffs(118, 48).addBox(-1.0F, -22.0F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(0, 50).addBox(-1.0F, -24.0F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(8, 50).addBox(-1.0F, -26.0F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(116, 31).addBox(-0.5F, -30.3F, -3.5F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.15F)).texOffs(86, 13).addBox(-0.5F, -29.3F, -3.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.15F)).texOffs(70, 44).addBox(-1.5F, -28.5F, -4.5F, 3.0F, 1.0F, 3.0F).texOffs(20, 46).addBox(-1.5F, -27.5F, -4.5F, 1.0F, 3.0F, 1.0F).texOffs(42, 46).addBox(0.5F, -27.5F, -4.5F, 1.0F, 3.0F, 1.0F).texOffs(16, 50).addBox(-1.5F, -27.5F, -2.5F, 1.0F, 3.0F, 1.0F).texOffs(20, 50).addBox(0.5F, -27.5F, -2.5F, 1.0F, 3.0F, 1.0F).texOffs(82, 44).addBox(-1.5F, -24.5F, -4.5F, 3.0F, 1.0F, 3.0F).texOffs(84, 52).addBox(-0.5F, -27.0F, -3.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.0F, -0.3F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("line", CubeListBuilder.create().texOffs(90, 13).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    /** Parts shown only for some variants of soulfire_seer. */
    public static final Map<String, String[]> SOULFIRE_SEER_ONLY = Map.ofEntries(
            Map.entry("crest", new String[] {"bone_seer"}),
            Map.entry("hood", new String[] {"base"}),
            Map.entry("mantle", new String[] {"bone_seer"}));

    public static LayerDefinition cindermothSwarm() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_moth_0 = root.addOrReplaceChild("moth_0", CubeListBuilder.create().texOffs(60, 9).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 1.0F).texOffs(56, 0).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(2.0F, 20.0F, 0.0F, 0.0F, -1.5708F, 0.1396F));
        p_moth_0.addOrReplaceChild("moth_0_wing_left", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.5F, 0.0F, 0.0F, -0.1745F));
        p_moth_0.addOrReplaceChild("moth_0_wing_right", CubeListBuilder.create().texOffs(14, 0).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.5F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_1 = root.addOrReplaceChild("moth_1", CubeListBuilder.create().texOffs(60, 11).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(60, 13).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-2.5F, 19.5F, 2.5F, 0.0F, 0.8292F, -0.2269F));
        p_moth_1.addOrReplaceChild("moth_1_wing_left", CubeListBuilder.create().texOffs(0, 9).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.0F, 0.0F, 0.0F, -0.1745F));
        p_moth_1.addOrReplaceChild("moth_1_wing_right", CubeListBuilder.create().texOffs(10, 9).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.0F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_2 = root.addOrReplaceChild("moth_2", CubeListBuilder.create().texOffs(18, 15).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 1.0F).texOffs(56, 3).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.5F, 19.0F, -4.5F, 0.0F, -3.0541F, 0.3142F));
        p_moth_2.addOrReplaceChild("moth_2_wing_left", CubeListBuilder.create().texOffs(28, 0).addBox(0.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.5F, 0.0F, 0.0F, -0.1745F));
        p_moth_2.addOrReplaceChild("moth_2_wing_right", CubeListBuilder.create().texOffs(42, 0).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.5F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_3 = root.addOrReplaceChild("moth_3", CubeListBuilder.create().texOffs(22, 15).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(26, 15).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(3.0F, 18.5F, 4.0F, 0.0F, -0.6541F, -0.1396F));
        p_moth_3.addOrReplaceChild("moth_3_wing_left", CubeListBuilder.create().texOffs(20, 9).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.0F, 0.0F, 0.0F, -0.1745F));
        p_moth_3.addOrReplaceChild("moth_3_wing_right", CubeListBuilder.create().texOffs(30, 9).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.0F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_4 = root.addOrReplaceChild("moth_4", CubeListBuilder.create().texOffs(30, 15).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(34, 15).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.5F, 18.0F, -1.0F, 0.0F, 1.7459F, 0.2269F));
        p_moth_4.addOrReplaceChild("moth_4_wing_left", CubeListBuilder.create().texOffs(40, 9).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.0F, 0.0F, 0.0F, -0.1745F));
        p_moth_4.addOrReplaceChild("moth_4_wing_right", CubeListBuilder.create().texOffs(50, 9).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.0F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_5 = root.addOrReplaceChild("moth_5", CubeListBuilder.create().texOffs(38, 15).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 1.0F).texOffs(56, 6).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(5.0F, 17.0F, -3.0F, 0.0F, -2.1374F, -0.3142F));
        p_moth_5.addOrReplaceChild("moth_5_wing_left", CubeListBuilder.create().texOffs(0, 3).addBox(0.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.5F, 0.0F, 0.0F, -0.1745F));
        p_moth_5.addOrReplaceChild("moth_5_wing_right", CubeListBuilder.create().texOffs(14, 3).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.5F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_6 = root.addOrReplaceChild("moth_6", CubeListBuilder.create().texOffs(42, 15).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(46, 15).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-1.5F, 16.5F, 5.5F, 0.0F, 0.2626F, 0.1396F));
        p_moth_6.addOrReplaceChild("moth_6_wing_left", CubeListBuilder.create().texOffs(0, 11).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.0F, 0.0F, 0.0F, -0.1745F));
        p_moth_6.addOrReplaceChild("moth_6_wing_right", CubeListBuilder.create().texOffs(10, 11).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.0F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_7 = root.addOrReplaceChild("moth_7", CubeListBuilder.create().texOffs(50, 15).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(54, 15).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-3.0F, 16.0F, -5.5F, 0.0F, 2.6626F, -0.2269F));
        p_moth_7.addOrReplaceChild("moth_7_wing_left", CubeListBuilder.create().texOffs(20, 11).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.0F, 0.0F, 0.0F, -0.1745F));
        p_moth_7.addOrReplaceChild("moth_7_wing_right", CubeListBuilder.create().texOffs(30, 11).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.0F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_8 = root.addOrReplaceChild("moth_8", CubeListBuilder.create().texOffs(58, 15).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 1.0F).texOffs(0, 15).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(5.5F, 15.5F, 2.0F, 0.0F, -1.2207F, 0.3142F));
        p_moth_8.addOrReplaceChild("moth_8_wing_left", CubeListBuilder.create().texOffs(28, 3).addBox(0.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.5F, 0.0F, 0.0F, -0.1745F));
        p_moth_8.addOrReplaceChild("moth_8_wing_right", CubeListBuilder.create().texOffs(42, 3).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.5F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_9 = root.addOrReplaceChild("moth_9", CubeListBuilder.create().texOffs(18, 17).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(22, 17).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.5F, 15.0F, 2.0F, 0.0F, 1.1793F, -0.1396F));
        p_moth_9.addOrReplaceChild("moth_9_wing_left", CubeListBuilder.create().texOffs(40, 11).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.0F, 0.0F, 0.0F, -0.1745F));
        p_moth_9.addOrReplaceChild("moth_9_wing_right", CubeListBuilder.create().texOffs(50, 11).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.0F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_10 = root.addOrReplaceChild("moth_10", CubeListBuilder.create().texOffs(26, 17).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(30, 17).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.5F, 14.0F, -5.0F, 0.0F, -2.7039F, 0.2269F));
        p_moth_10.addOrReplaceChild("moth_10_wing_left", CubeListBuilder.create().texOffs(0, 13).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.0F, 0.0F, 0.0F, -0.1745F));
        p_moth_10.addOrReplaceChild("moth_10_wing_right", CubeListBuilder.create().texOffs(10, 13).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.0F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_11 = root.addOrReplaceChild("moth_11", CubeListBuilder.create().texOffs(34, 17).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 1.0F).texOffs(6, 15).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(1.5F, 13.5F, 5.0F, 0.0F, -0.304F, -0.3142F));
        p_moth_11.addOrReplaceChild("moth_11_wing_left", CubeListBuilder.create().texOffs(0, 6).addBox(0.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.5F, 0.0F, 0.0F, -0.1745F));
        p_moth_11.addOrReplaceChild("moth_11_wing_right", CubeListBuilder.create().texOffs(14, 6).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.5F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_12 = root.addOrReplaceChild("moth_12", CubeListBuilder.create().texOffs(38, 17).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(42, 17).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 13.0F, -2.0F, 0.0F, 2.096F, 0.1396F));
        p_moth_12.addOrReplaceChild("moth_12_wing_left", CubeListBuilder.create().texOffs(20, 13).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.0F, 0.0F, 0.0F, -0.1745F));
        p_moth_12.addOrReplaceChild("moth_12_wing_right", CubeListBuilder.create().texOffs(30, 13).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.0F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_13 = root.addOrReplaceChild("moth_13", CubeListBuilder.create().texOffs(46, 17).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 1.0F).texOffs(12, 15).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(3.5F, 12.5F, -1.0F, 0.0F, -1.7872F, -0.2269F));
        p_moth_13.addOrReplaceChild("moth_13_wing_left", CubeListBuilder.create().texOffs(28, 6).addBox(0.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.5F, 0.0F, 0.0F, -0.1745F));
        p_moth_13.addOrReplaceChild("moth_13_wing_right", CubeListBuilder.create().texOffs(42, 6).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.5F, 0.0F, 0.0F, 0.1745F));
        PartDefinition p_moth_14 = root.addOrReplaceChild("moth_14", CubeListBuilder.create().texOffs(50, 17).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(54, 17).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-1.0F, 12.0F, 2.0F, 0.0F, 0.6127F, 0.3142F));
        p_moth_14.addOrReplaceChild("moth_14_wing_left", CubeListBuilder.create().texOffs(40, 13).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(0.5F, -0.5F, -1.0F, 0.0F, 0.0F, -0.1745F));
        p_moth_14.addOrReplaceChild("moth_14_wing_right", CubeListBuilder.create().texOffs(50, 13).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -0.5F, -1.0F, 0.0F, 0.0F, 0.1745F));
        return LayerDefinition.create(mesh, 64, 32);
    }
    public static final Map<String, String[]> CINDERMOTH_SWARM_ONLY = Map.of();

    public static LayerDefinition soulwardRevenant() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(86, 9).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F).texOffs(40, 25).addBox(-2.5F, 4.0F, -2.6F, 5.0F, 2.0F, 1.0F).texOffs(0, 16).addBox(-2.5F, 11.0F, -3.5F, 5.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(-2.3F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(102, 9).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F).texOffs(12, 33).addBox(-2.5F, 4.0F, -2.6F, 5.0F, 2.0F, 1.0F).texOffs(52, 20).addBox(-2.5F, 11.0F, -3.5F, 5.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(2.3F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(56, 0).addBox(-4.5F, -2.0F, -2.8F, 9.0F, 4.0F, 6.0F).texOffs(0, 0).addBox(-4.5F, -13.0F, -2.6F, 9.0F, 11.0F, 5.0F).texOffs(40, 28).addBox(-3.0F, -14.5F, -2.2F, 6.0F, 2.0F, 4.0F).texOffs(8, 36).addBox(-1.0F, -12.5F, 2.2F, 2.0F, 1.0F, 2.0F).texOffs(16, 36).addBox(-1.0F, -10.4F, 2.2F, 2.0F, 1.0F, 2.0F).texOffs(60, 38).addBox(-1.0F, -8.3F, 2.2F, 2.0F, 1.0F, 2.0F).texOffs(68, 38).addBox(-1.0F, -6.2F, 2.2F, 2.0F, 1.0F, 2.0F).texOffs(76, 38).addBox(-1.0F, -4.1F, 2.2F, 2.0F, 1.0F, 2.0F).texOffs(84, 38).addBox(-1.0F, -2.0F, 2.2F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("breastplate", CubeListBuilder.create().texOffs(12, 24).addBox(-3.5F, -4.5F, -1.2F, 7.0F, 8.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -8.0F, -2.6F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tabard", CubeListBuilder.create().texOffs(28, 25).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, -3.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("chain_a", CubeListBuilder.create().texOffs(124, 0).addBox(-0.5F, -7.0F, -4.5F, 1.0F, 14.0F, 1.0F).texOffs(124, 15).addBox(-0.5F, -7.0F, 2.5F, 1.0F, 14.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -7.5F, 0.0F, 0.0F, 0.0F, 0.6283F));
        p_body.addOrReplaceChild("chain_b", CubeListBuilder.create().texOffs(118, 16).addBox(-0.5F, -7.0F, -4.5F, 1.0F, 14.0F, 1.0F).texOffs(122, 30).addBox(-0.5F, -7.0F, 2.5F, 1.0F, 14.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -7.5F, 0.0F, 0.0F, 0.0F, -0.6283F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(28, 0).addBox(-3.5F, -8.0F, -3.5F, 7.0F, 8.0F, 7.0F).texOffs(86, 0).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 1.0F, 8.0F), PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(24, 35).addBox(-1.0F, -2.0F, -2.0F, 2.0F, 2.0F, 2.0F).texOffs(32, 35).addBox(-1.0F, -3.5F, 0.2F, 2.0F, 2.0F, 2.0F).texOffs(0, 36).addBox(-1.0F, -4.5F, 2.4F, 2.0F, 2.0F, 2.0F).texOffs(22, 16).addBox(-0.5F, -5.0F, 4.4F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -8.0F, -1.0F, -0.4189F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(74, 20).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F).texOffs(56, 10).addBox(-3.0F, -2.5F, -3.0F, 6.0F, 4.0F, 6.0F).texOffs(60, 32).addBox(-2.5F, 1.5F, -2.5F, 5.0F, 1.0F, 5.0F).texOffs(86, 24).addBox(-2.0F, 7.0F, -2.0F, 4.0F, 4.0F, 4.0F).texOffs(80, 32).addBox(-2.5F, 6.0F, -2.5F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-6.2F, -11.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_right.addOrReplaceChild("sword", CubeListBuilder.create().texOffs(52, 15).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(92, 38).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(60, 28).addBox(-2.5F, 1.5F, -1.0F, 5.0F, 1.0F, 2.0F).texOffs(118, 0).addBox(-1.0F, 2.5F, -0.5F, 2.0F, 15.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 9.5F, -0.5F, -1.2566F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(0, 24).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F).texOffs(28, 15).addBox(-3.0F, -2.5F, -3.0F, 6.0F, 4.0F, 6.0F).texOffs(100, 32).addBox(-2.5F, 1.5F, -2.5F, 5.0F, 1.0F, 5.0F).texOffs(102, 24).addBox(-2.0F, 7.0F, -2.0F, 4.0F, 4.0F, 4.0F).texOffs(40, 34).addBox(-2.5F, 6.0F, -2.5F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(6.2F, -11.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_left.addOrReplaceChild("chain_hang", CubeListBuilder.create().texOffs(80, 10).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 8.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 7.5F, 2.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> SOULWARD_REVENANT_ONLY = Map.of();

    public static LayerDefinition basaltBehemoth() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(92, 51).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 8.0F, 6.0F).texOffs(92, 65).addBox(-3.5F, 7.0F, -4.25F, 7.0F, 3.0F, 7.0F), PartPose.offsetAndRotation(-6.0F, 14.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(0, 62).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 8.0F, 6.0F).texOffs(50, 68).addBox(-3.5F, 7.0F, -4.25F, 7.0F, 3.0F, 7.0F), PartPose.offsetAndRotation(6.0F, 14.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(64, 18).addBox(-8.0F, -6.0F, -2.0F, 16.0F, 8.0F, 8.0F).texOffs(0, 0).addBox(-10.0F, -18.0F, -14.0F, 20.0F, 16.0F, 12.0F).texOffs(64, 0).addBox(-8.0F, -21.0F, -8.0F, 16.0F, 5.0F, 13.0F), PartPose.offsetAndRotation(0.0F, 14.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("column_0", CubeListBuilder.create().texOffs(52, 78).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(-6.0F, -20.0F, -7.5F, -0.1396F, 0.0F, 0.2793F));
        p_body.addOrReplaceChild("column_1", CubeListBuilder.create().texOffs(78, 75).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 9.0F, 4.0F), PartPose.offsetAndRotation(-2.0F, -20.0F, -8.0F, -0.1745F, 0.0F, 0.0698F));
        p_body.addOrReplaceChild("column_2", CubeListBuilder.create().texOffs(112, 18).addBox(-2.0F, -9.0F, -2.0F, 4.0F, 10.0F, 4.0F), PartPose.offsetAndRotation(2.5F, -20.0F, -8.0F, -0.1745F, 0.0F, -0.0698F));
        p_body.addOrReplaceChild("column_3", CubeListBuilder.create().texOffs(0, 86).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(6.5F, -20.0F, -7.0F, -0.1396F, 0.0F, -0.2793F));
        p_body.addOrReplaceChild("column_4", CubeListBuilder.create().texOffs(24, 74).addBox(-2.0F, -9.0F, -2.0F, 4.0F, 10.0F, 4.0F), PartPose.offsetAndRotation(-4.5F, -20.0F, -2.5F, 0.0698F, 0.0F, 0.1396F));
        p_body.addOrReplaceChild("column_5", CubeListBuilder.create().texOffs(72, 51).addBox(-2.5F, -11.0F, -2.5F, 5.0F, 12.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -20.0F, -3.0F, 0.0349F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("column_6", CubeListBuilder.create().texOffs(94, 75).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 9.0F, 4.0F), PartPose.offsetAndRotation(4.5F, -20.0F, -2.5F, 0.0698F, 0.0F, -0.1396F));
        p_body.addOrReplaceChild("column_7", CubeListBuilder.create().texOffs(16, 88).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(-3.0F, -20.0F, 2.5F, 0.3142F, 0.0F, 0.1396F));
        p_body.addOrReplaceChild("column_8", CubeListBuilder.create().texOffs(52, 89).addBox(-2.0F, -5.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offsetAndRotation(2.5F, -20.0F, 2.5F, 0.3491F, 0.0F, -0.1396F));
        p_body.addOrReplaceChild("column_9", CubeListBuilder.create().texOffs(116, 51).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(-8.0F, -20.0F, -1.0F, 0.1396F, 0.0F, 0.4538F));
        p_body.addOrReplaceChild("column_10", CubeListBuilder.create().texOffs(24, 99).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(8.0F, -20.0F, -0.5F, 0.1396F, 0.0F, -0.4538F));
        p_body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 76).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -8.0F, -14.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("plate_right", CubeListBuilder.create().texOffs(68, 88).addBox(0.1F, -5.0F, -2.0F, 5.0F, 10.0F, 2.0F), PartPose.offsetAndRotation(-5.0F, -8.0F, -15.2F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("plate_left", CubeListBuilder.create().texOffs(82, 88).addBox(-5.1F, -5.0F, -2.0F, 5.0F, 10.0F, 2.0F), PartPose.offsetAndRotation(5.0F, -8.0F, -15.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(24, 63).addBox(-3.5F, -3.0F, -5.0F, 7.0F, 5.0F, 6.0F).texOffs(0, 99).addBox(-4.5F, -4.0F, -5.5F, 9.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -18.0F, -13.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(32, 93).addBox(-3.0F, 0.0F, -3.5F, 6.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -1.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(64, 34).addBox(-3.5F, -2.0F, -3.5F, 7.0F, 10.0F, 7.0F), PartPose.offsetAndRotation(-12.0F, -15.0F, -9.0F, 0.0F, 0.0F, 0.0F));
        p_arm_right.addOrReplaceChild("pauldron_right_0", CubeListBuilder.create().texOffs(96, 90).addBox(-2.0F, -5.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offsetAndRotation(-0.5F, -2.0F, 0.5F, 0.0F, 0.0F, -0.3142F));
        p_arm_right.addOrReplaceChild("pauldron_right_1", CubeListBuilder.create().texOffs(36, 99).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(-2.0F, -2.0F, -1.5F, 0.0F, 0.0F, -0.5934F));
        p_arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(0, 28).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 14.0F, 8.0F).texOffs(110, 75).addBox(-4.0F, -3.0F, -1.0F, 3.0F, 12.0F, 3.0F).texOffs(0, 50).addBox(-4.5F, 14.0F, -5.0F, 9.0F, 3.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(92, 34).addBox(-3.5F, -2.0F, -3.5F, 7.0F, 10.0F, 7.0F), PartPose.offsetAndRotation(12.0F, -15.0F, -9.0F, 0.0F, 0.0F, 0.0F));
        p_arm_left.addOrReplaceChild("pauldron_left_0", CubeListBuilder.create().texOffs(112, 90).addBox(-2.0F, -5.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offsetAndRotation(0.5F, -2.0F, 0.5F, 0.0F, 0.0F, 0.3142F));
        p_arm_left.addOrReplaceChild("pauldron_left_1", CubeListBuilder.create().texOffs(48, 99).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(2.0F, -2.0F, -1.5F, 0.0F, 0.0F, 0.5934F));
        p_arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(32, 28).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 14.0F, 8.0F).texOffs(40, 78).addBox(1.0F, -3.0F, -1.0F, 3.0F, 12.0F, 3.0F).texOffs(36, 51).addBox(-4.5F, 14.0F, -5.0F, 9.0F, 3.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static final Map<String, String[]> BASALT_BEHEMOTH_ONLY = Map.of();

    public static LayerDefinition quakehopper() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -5.0F, -5.0F, 10.0F, 8.0F, 11.0F).texOffs(0, 19).addBox(-4.0F, -6.7F, -4.0F, 8.0F, 2.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 18.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("columns", CubeListBuilder.create().texOffs(30, 30).addBox(-3.5F, -2.0F, -2.5F, 3.0F, 2.0F, 3.0F).texOffs(106, 6).addBox(0.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(42, 31).addBox(-1.5F, -1.5F, 1.5F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -6.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 30).addBox(-5.0F, -4.0F, -4.5F, 10.0F, 6.0F, 5.0F).texOffs(34, 19).addBox(-4.5F, -5.2F, -4.0F, 2.0F, 2.0F, 2.0F).texOffs(34, 23).addBox(2.5F, -5.2F, -4.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("gland", CubeListBuilder.create().texOffs(106, 0).addBox(-3.0F, -0.3F, -1.5F, 6.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -2.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(118, 6).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(106, 12).addBox(-1.5F, 2.5F, -2.0F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-3.5F, 2.5F, -3.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(118, 11).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(54, 31).addBox(-1.5F, 2.5F, -2.0F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(3.5F, 2.5F, -3.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_thigh_right = p_body.addOrReplaceChild("thigh_right", CubeListBuilder.create().texOffs(42, 16).addBox(-2.0F, -2.5F, -9.0F, 4.0F, 5.0F, 10.0F), PartPose.offsetAndRotation(-6.5F, 0.0F, 4.0F, -0.6109F, 0.0F, 0.0F));
        PartDefinition p_shin_right = p_thigh_right.addOrReplaceChild("shin_right", CubeListBuilder.create().texOffs(42, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 13.0F), PartPose.offsetAndRotation(-1.5F, 0.0F, -8.0F, -0.192F, 0.0F, 0.0F));
        p_shin_right.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(98, 16).addBox(-2.0F, -0.5F, -9.0F, 4.0F, 2.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 12.0F, 0.8029F, 0.0F, 0.0F));
        PartDefinition p_thigh_left = p_body.addOrReplaceChild("thigh_left", CubeListBuilder.create().texOffs(70, 16).addBox(-2.0F, -2.5F, -9.0F, 4.0F, 5.0F, 10.0F), PartPose.offsetAndRotation(6.5F, 0.0F, 4.0F, -0.6109F, 0.0F, 0.0F));
        PartDefinition p_shin_left = p_thigh_left.addOrReplaceChild("shin_left", CubeListBuilder.create().texOffs(74, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 13.0F), PartPose.offsetAndRotation(1.5F, 0.0F, -8.0F, -0.192F, 0.0F, 0.0F));
        p_shin_left.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(98, 28).addBox(-2.0F, -0.5F, -9.0F, 4.0F, 2.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 12.0F, 0.8029F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> QUAKEHOPPER_ONLY = Map.of();

    public static LayerDefinition ashharrier() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -3.5F, -6.0F, 8.0F, 7.0F, 12.0F).texOffs(92, 22).addBox(-4.5F, -4.1F, -4.5F, 9.0F, 1.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 42).addBox(-3.0F, -3.5F, -5.0F, 6.0F, 6.0F, 5.0F).texOffs(22, 42).addBox(-2.0F, -4.6F, -2.0F, 4.0F, 1.0F, 6.0F).texOffs(66, 42).addBox(-1.0F, -1.5F, -8.0F, 2.0F, 2.0F, 3.0F).texOffs(24, 19).addBox(-1.0F, 0.35F, -8.5F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -2.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 19).addBox(-1.0F, 0.15F, -2.6F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.5F, -5.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(92, 31).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 0.0F, 9.0F).texOffs(92, 40).addBox(-4.0F, 0.25F, 0.0F, 8.0F, 0.0F, 9.0F), PartPose.offsetAndRotation(0.0F, -2.0F, 6.0F, -0.1047F, 0.0F, 0.0F));
        PartDefinition p_wing_right = p_body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(40, 0).addBox(-11.0F, 0.0F, -3.0F, 11.0F, 0.0F, 11.0F).texOffs(84, 0).addBox(-11.0F, 0.25F, -3.0F, 11.0F, 0.0F, 11.0F), PartPose.offsetAndRotation(-4.0F, -3.0F, -2.0F, -0.2793F, 0.0F, 0.1047F));
        p_wing_right.addOrReplaceChild("wing_right_tip", CubeListBuilder.create().texOffs(0, 22).addBox(-13.0F, 0.0F, -3.0F, 13.0F, 0.0F, 10.0F).texOffs(46, 22).addBox(-13.0F, 0.25F, -3.0F, 13.0F, 0.0F, 10.0F), PartPose.offsetAndRotation(-11.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(42, 42).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(66, 47).addBox(-1.0F, 1.5F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(76, 42).addBox(-1.5F, 4.5F, -2.0F, 3.0F, 1.0F, 3.0F).texOffs(8, 19).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.0F, 3.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_wing_left = p_body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(40, 11).addBox(0.0F, 0.0F, -3.0F, 11.0F, 0.0F, 11.0F).texOffs(84, 11).addBox(0.0F, 0.25F, -3.0F, 11.0F, 0.0F, 11.0F), PartPose.offsetAndRotation(4.0F, -3.0F, -2.0F, -0.2793F, 0.0F, -0.1047F));
        p_wing_left.addOrReplaceChild("wing_left_tip", CubeListBuilder.create().texOffs(0, 32).addBox(0.0F, 0.0F, -3.0F, 13.0F, 0.0F, 10.0F).texOffs(46, 32).addBox(0.0F, 0.25F, -3.0F, 13.0F, 0.0F, 10.0F), PartPose.offsetAndRotation(11.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(54, 42).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(42, 48).addBox(-1.0F, 1.5F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(76, 46).addBox(-1.5F, 4.5F, -2.0F, 3.0F, 1.0F, 3.0F).texOffs(16, 19).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.0F, 3.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> ASHHARRIER_ONLY = Map.of();

    public static LayerDefinition lavaleaper() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -4.0F, 6.0F, 6.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 20.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("dorsal_0", CubeListBuilder.create().texOffs(20, 20).addBox(-0.5F, -3.0F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("gland", CubeListBuilder.create().texOffs(0, 21).addBox(-2.0F, -0.5F, -1.5F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 3.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(28, 0).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 5.0F, 5.0F).texOffs(46, 21).addBox(-1.5F, -2.0F, -7.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.5F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 14).addBox(-2.0F, 0.15F, -6.8F, 4.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -0.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(46, 27).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(-3.0F, 1.5F, -2.0F, 0.0F, 0.3491F, 0.5585F));
        p_body.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(18, 29).addBox(0.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F), PartPose.offsetAndRotation(3.0F, 1.5F, -2.0F, 0.0F, -0.3491F, -0.5585F));
        PartDefinition p_tail1 = p_body.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(28, 10).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        p_tail1.addOrReplaceChild("dorsal_1", CubeListBuilder.create().texOffs(34, 20).addBox(-0.5F, -3.0F, 0.0F, 1.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -2.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tail1.addOrReplaceChild("ventral_1", CubeListBuilder.create().texOffs(20, 14).addBox(0.0F, 0.0F, 1.0F, 0.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 2.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(48, 13).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        p_tail2.addOrReplaceChild("dorsal_2", CubeListBuilder.create().texOffs(0, 27).addBox(-0.5F, -2.0F, 0.0F, 1.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tail2.addOrReplaceChild("ventral_2", CubeListBuilder.create().texOffs(10, 27).addBox(0.0F, 0.0F, 0.0F, 0.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_tail2.addOrReplaceChild("tail_fin", CubeListBuilder.create().texOffs(48, 0).addBox(0.0F, -4.0F, 0.0F, 0.0F, 8.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> LAVALEAPER_ONLY = Map.of();

    public static LayerDefinition blackstone_scuttler() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -4.0F, -6.0F, 14.0F, 5.0F, 12.0F).texOffs(52, 11).addBox(-6.0F, -6.0F, -5.0F, 12.0F, 2.0F, 10.0F).texOffs(52, 0).addBox(-8.0F, -3.0F, -4.5F, 16.0F, 2.0F, 9.0F).texOffs(0, 17).addBox(-6.0F, 1.0F, -5.0F, 12.0F, 2.0F, 10.0F).texOffs(96, 22).addBox(-5.0F, -7.5F, -3.5F, 4.0F, 2.0F, 4.0F).texOffs(112, 22).addBox(1.0F, -8.0F, -1.5F, 4.0F, 2.0F, 4.0F).texOffs(0, 35).addBox(-2.5F, -7.0F, 2.0F, 3.0F, 1.0F, 3.0F).texOffs(12, 35).addBox(-2.5F, -1.0F, -6.6F, 5.0F, 2.0F, 1.0F).texOffs(24, 37).addBox(7.0F, -2.6F, -4.9F, 2.0F, 1.0F, 1.0F).texOffs(30, 37).addBox(-9.0F, -2.6F, -4.9F, 2.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 19.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(92, 23).addBox(-3.0F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(120, 11).addBox(-3.5F, -3.5F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(124, 28).addBox(2.0F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(120, 15).addBox(1.5F, -3.5F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -4.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_claw_left = p_body.addOrReplaceChild("claw_left", CubeListBuilder.create().texOffs(16, 29).addBox(-1.5F, -1.5F, -3.0F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(5.0F, -0.5F, -5.5F, 0.0F, 0.4363F, 0.0F));
        PartDefinition p_claw_left_hand = p_claw_left.addOrReplaceChild("claw_left_hand", CubeListBuilder.create().texOffs(102, 0).addBox(-3.0F, -2.5F, -6.0F, 6.0F, 5.0F, 6.0F).texOffs(40, 29).addBox(-1.5F, 0.3F, -8.5F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -3.0F, 0.0F, -0.4363F, 0.0F));
        p_claw_left_hand.addOrReplaceChild("claw_left_finger", CubeListBuilder.create().texOffs(52, 29).addBox(-1.5F, -1.6F, -2.5F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_claw_right = p_body.addOrReplaceChild("claw_right", CubeListBuilder.create().texOffs(28, 29).addBox(-1.5F, -1.5F, -3.0F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-5.0F, -0.5F, -5.5F, 0.0F, -0.4363F, 0.0F));
        PartDefinition p_claw_right_hand = p_claw_right.addOrReplaceChild("claw_right_hand", CubeListBuilder.create().texOffs(96, 11).addBox(-3.0F, -2.5F, -6.0F, 6.0F, 5.0F, 6.0F).texOffs(64, 29).addBox(-1.5F, 0.3F, -8.5F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -3.0F, 0.0F, 0.4363F, 0.0F));
        p_claw_right_hand.addOrReplaceChild("claw_right_finger", CubeListBuilder.create().texOffs(76, 29).addBox(-1.5F, -1.6F, -2.5F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_left_1 = p_body.addOrReplaceChild("leg_left_1", CubeListBuilder.create(), PartPose.offsetAndRotation(7.0F, 0.5F, -3.0F, 0.0F, 0.5236F, 0.0F));
        PartDefinition p_leg_left_1_thigh = p_leg_left_1.addOrReplaceChild("leg_left_1_thigh", CubeListBuilder.create().texOffs(44, 23).addBox(0.0F, -1.5F, -1.5F, 5.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.5236F));
        p_leg_left_1_thigh.addOrReplaceChild("leg_left_1_shin", CubeListBuilder.create().texOffs(40, 34).addBox(-0.5F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F).texOffs(44, 17).addBox(4.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.7453F));
        PartDefinition p_leg_left_2 = p_body.addOrReplaceChild("leg_left_2", CubeListBuilder.create(), PartPose.offsetAndRotation(7.0F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_left_2_thigh = p_leg_left_2.addOrReplaceChild("leg_left_2_thigh", CubeListBuilder.create().texOffs(60, 23).addBox(0.0F, -1.5F, -1.5F, 5.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.5236F));
        p_leg_left_2_thigh.addOrReplaceChild("leg_left_2_shin", CubeListBuilder.create().texOffs(54, 34).addBox(-0.5F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F).texOffs(44, 19).addBox(4.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.7453F));
        PartDefinition p_leg_left_3 = p_body.addOrReplaceChild("leg_left_3", CubeListBuilder.create(), PartPose.offsetAndRotation(7.0F, 0.5F, 4.0F, 0.0F, -0.4887F, 0.0F));
        PartDefinition p_leg_left_3_thigh = p_leg_left_3.addOrReplaceChild("leg_left_3_thigh", CubeListBuilder.create().texOffs(76, 23).addBox(0.0F, -1.5F, -1.5F, 5.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.5236F));
        p_leg_left_3_thigh.addOrReplaceChild("leg_left_3_shin", CubeListBuilder.create().texOffs(68, 34).addBox(-0.5F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F).texOffs(120, 19).addBox(4.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.7453F));
        PartDefinition p_leg_right_1 = p_body.addOrReplaceChild("leg_right_1", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.0F, 0.5F, -3.0F, 0.0F, -0.5236F, 0.0F));
        PartDefinition p_leg_right_1_thigh = p_leg_right_1.addOrReplaceChild("leg_right_1_thigh", CubeListBuilder.create().texOffs(92, 28).addBox(-5.0F, -1.5F, -1.5F, 5.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5236F));
        p_leg_right_1_thigh.addOrReplaceChild("leg_right_1_shin", CubeListBuilder.create().texOffs(82, 34).addBox(-4.5F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F).texOffs(44, 21).addBox(-7.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.7453F));
        PartDefinition p_leg_right_2 = p_body.addOrReplaceChild("leg_right_2", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.0F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_right_2_thigh = p_leg_right_2.addOrReplaceChild("leg_right_2_thigh", CubeListBuilder.create().texOffs(108, 28).addBox(-5.0F, -1.5F, -1.5F, 5.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5236F));
        p_leg_right_2_thigh.addOrReplaceChild("leg_right_2_shin", CubeListBuilder.create().texOffs(96, 34).addBox(-4.5F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F).texOffs(24, 35).addBox(-7.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.7453F));
        PartDefinition p_leg_right_3 = p_body.addOrReplaceChild("leg_right_3", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.0F, 0.5F, 4.0F, 0.0F, 0.4887F, 0.0F));
        PartDefinition p_leg_right_3_thigh = p_leg_right_3.addOrReplaceChild("leg_right_3_thigh", CubeListBuilder.create().texOffs(0, 29).addBox(-5.0F, -1.5F, -1.5F, 5.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5236F));
        p_leg_right_3_thigh.addOrReplaceChild("leg_right_3_shin", CubeListBuilder.create().texOffs(110, 34).addBox(-4.5F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F).texOffs(32, 35).addBox(-7.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.7453F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> BLACKSTONE_SCUTTLER_ONLY = Map.of();

    public static LayerDefinition pyreHerald() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(0, 37).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F).texOffs(42, 38).addBox(-1.5F, 4.0F, -3.0F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.6F, 19.0F, 0.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(8, 37).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F).texOffs(16, 40).addBox(-1.5F, 4.0F, -3.0F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.6F, 19.0F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -8.0F, -2.5F, 7.0F, 8.0F, 5.0F).texOffs(0, 23).addBox(-4.0F, -8.5F, -3.0F, 8.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 19.0F, 0.5F, 0.4887F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(24, 0).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 5.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, -0.384F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(24, 11).addBox(-3.0F, -6.0F, -3.5F, 6.0F, 6.0F, 6.0F).texOffs(24, 37).addBox(-3.5F, -6.5F, -4.5F, 7.0F, 1.0F, 2.0F).texOffs(42, 33).addBox(-1.5F, -7.5F, 0.5F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -8.0F, -1.5F, -0.4887F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(48, 11).addBox(-0.5F, -7.0F, 0.0F, 1.0F, 8.0F, 6.0F), PartPose.offsetAndRotation(-2.2F, -6.5F, 2.0F, -0.3491F, -0.6981F, 0.2094F));
        p_body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(28, 23).addBox(-0.5F, -7.0F, 0.0F, 1.0F, 8.0F, 6.0F), PartPose.offsetAndRotation(2.2F, -6.5F, 2.0F, -0.3491F, 0.6981F, -0.2094F));
        p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(52, 0).addBox(-0.5F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F).texOffs(28, 40).addBox(-0.5F, 5.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(3.8F, -7.0F, -0.5F, -0.2618F, 0.0F, -0.1396F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(16, 32).addBox(-1.5F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F).texOffs(36, 42).addBox(-1.5F, 5.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-3.8F, -7.0F, -0.5F, -1.2217F, 0.0F, 0.1396F));
        PartDefinition p_staff = p_arm_right.addOrReplaceChild("staff", CubeListBuilder.create().texOffs(58, 25).addBox(-0.5F, -13.0F, -0.5F, 1.0F, 16.0F, 1.0F).texOffs(52, 8).addBox(-1.0F, -13.5F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(0, 13).addBox(-2.5F, -17.5F, -2.5F, 5.0F, 4.0F, 5.0F).texOffs(0, 32).addBox(-2.0F, -14.5F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-0.5F, 6.0F, 0.0F, 0.733F, 0.0F, -0.1396F));
        p_staff.addOrReplaceChild("fire", CubeListBuilder.create().texOffs(42, 25).addBox(-2.0F, -5.0F, -2.0F, 4.0F, 4.0F, 4.0F).texOffs(44, 42).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(60, 0).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -14.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> PYRE_HERALD_ONLY = Map.of();

    public static LayerDefinition fortressJailer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(58, 25).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F).texOffs(62, 40).addBox(-2.5F, 2.0F, -2.5F, 5.0F, 3.0F, 5.0F).texOffs(90, 32).addBox(-2.5F, 10.0F, -3.5F, 5.0F, 4.0F, 6.0F), PartPose.offsetAndRotation(-2.6F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(74, 25).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F).texOffs(82, 42).addBox(-2.5F, 2.0F, -2.5F, 5.0F, 3.0F, 5.0F).texOffs(0, 38).addBox(-2.5F, 10.0F, -3.5F, 5.0F, 4.0F, 6.0F), PartPose.offsetAndRotation(2.6F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -13.0F, -3.0F, 10.0F, 13.0F, 6.0F).texOffs(64, 0).addBox(-5.5F, -12.5F, -3.5F, 11.0F, 7.0F, 7.0F).texOffs(0, 19).addBox(-5.5F, -3.0F, -3.5F, 11.0F, 2.0F, 7.0F).texOffs(116, 0).addBox(-3.0F, -5.0F, -3.75F, 6.0F, 13.0F, 0.0F).texOffs(116, 13).addBox(-3.0F, -5.0F, 3.75F, 6.0F, 11.0F, 0.0F).texOffs(116, 24).addBox(-1.5F, -10.0F, -4.5F, 3.0F, 3.0F, 1.0F).texOffs(32, 17).addBox(-1.0F, -11.0F, -4.0F, 2.0F, 1.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("key_ring", CubeListBuilder.create().texOffs(58, 17).addBox(0.0F, 0.0F, -1.5F, 0.0F, 3.0F, 3.0F).texOffs(124, 24).addBox(0.0F, 3.0F, -0.5F, 0.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(5.5F, -2.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -9.0F, -4.0F, 8.0F, 9.0F, 8.0F).texOffs(36, 17).addBox(-1.0F, -11.0F, -4.5F, 2.0F, 2.0F, 9.0F).texOffs(64, 14).addBox(-4.5F, -1.0F, -4.5F, 9.0F, 2.0F, 9.0F), PartPose.offsetAndRotation(0.0F, -13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(100, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F).texOffs(0, 28).addBox(-3.0F, -3.5F, -3.0F, 6.0F, 4.0F, 6.0F).texOffs(22, 38).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 5.0F, 5.0F).texOffs(72, 89).addBox(-2.5F, 2.0F, -2.5F, 5.0F, 1.0F, 5.0F).texOffs(92, 89).addBox(-2.5F, 4.0F, -2.5F, 5.0F, 1.0F, 5.0F).texOffs(72, 95).addBox(-2.5F, 6.0F, -2.5F, 5.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-6.5F, -1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(100, 16).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F).texOffs(24, 28).addBox(-3.0F, -3.5F, -3.0F, 6.0F, 4.0F, 6.0F).texOffs(42, 40).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 5.0F, 5.0F), PartPose.offsetAndRotation(6.5F, -1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("chain_0", CubeListBuilder.create().texOffs(42, 68).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(-6.5F, 13.5F, 0.0F, 1.5708F, 0.0F, 0.0F));
        root.addOrReplaceChild("chain_1", CubeListBuilder.create().texOffs(112, 32).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(-6.5F, 18.0F, 0.0F, 1.5708F, 0.0F, 0.0F));
        root.addOrReplaceChild("chain_2", CubeListBuilder.create().texOffs(60, 68).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_3", CubeListBuilder.create().texOffs(112, 41).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_4", CubeListBuilder.create().texOffs(78, 68).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_5", CubeListBuilder.create().texOffs(0, 48).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_6", CubeListBuilder.create().texOffs(96, 68).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_7", CubeListBuilder.create().texOffs(14, 48).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_8", CubeListBuilder.create().texOffs(0, 75).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_9", CubeListBuilder.create().texOffs(28, 48).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_10", CubeListBuilder.create().texOffs(18, 75).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_11", CubeListBuilder.create().texOffs(62, 48).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_12", CubeListBuilder.create().texOffs(36, 75).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_13", CubeListBuilder.create().texOffs(42, 50).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_14", CubeListBuilder.create().texOffs(54, 75).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_15", CubeListBuilder.create().texOffs(76, 50).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_16", CubeListBuilder.create().texOffs(72, 75).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_17", CubeListBuilder.create().texOffs(90, 50).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_18", CubeListBuilder.create().texOffs(90, 75).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_19", CubeListBuilder.create().texOffs(104, 50).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_20", CubeListBuilder.create().texOffs(108, 75).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_21", CubeListBuilder.create().texOffs(0, 57).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_22", CubeListBuilder.create().texOffs(0, 82).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_23", CubeListBuilder.create().texOffs(14, 57).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_24", CubeListBuilder.create().texOffs(18, 82).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_25", CubeListBuilder.create().texOffs(28, 57).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_26", CubeListBuilder.create().texOffs(36, 82).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_27", CubeListBuilder.create().texOffs(56, 57).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_28", CubeListBuilder.create().texOffs(54, 82).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_29", CubeListBuilder.create().texOffs(42, 59).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_30", CubeListBuilder.create().texOffs(72, 82).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_31", CubeListBuilder.create().texOffs(70, 59).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_32", CubeListBuilder.create().texOffs(90, 82).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_33", CubeListBuilder.create().texOffs(84, 59).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_34", CubeListBuilder.create().texOffs(108, 82).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_35", CubeListBuilder.create().texOffs(98, 59).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_36", CubeListBuilder.create().texOffs(0, 89).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_37", CubeListBuilder.create().texOffs(112, 59).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_38", CubeListBuilder.create().texOffs(18, 89).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_39", CubeListBuilder.create().texOffs(0, 66).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_40", CubeListBuilder.create().texOffs(36, 89).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_41", CubeListBuilder.create().texOffs(14, 66).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_42", CubeListBuilder.create().texOffs(54, 89).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("chain_43", CubeListBuilder.create().texOffs(28, 66).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        root.addOrReplaceChild("hook", CubeListBuilder.create().texOffs(112, 89).addBox(-1.0F, -3.0F, -3.0F, 2.0F, 1.0F, 6.0F).texOffs(92, 95).addBox(-1.0F, 2.0F, -3.0F, 2.0F, 1.0F, 6.0F).texOffs(90, 25).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 4.0F, 1.0F).texOffs(48, 28).addBox(-1.0F, -2.0F, 2.0F, 2.0F, 4.0F, 1.0F).texOffs(116, 28).addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-6.5F, 23.5F, 0.0F, 1.5708F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static final Map<String, String[]> FORTRESS_JAILER_ONLY = Map.of();

    public static LayerDefinition flamewright() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(76, 21).addBox(-1.5F, 0.0F, -3.4F, 3.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(-2.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(24, 24).addBox(-1.5F, 0.0F, -3.4F, 3.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(2.0F, 21.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(94, 0).addBox(-4.0F, -9.0F, -2.5F, 8.0F, 9.0F, 5.0F).texOffs(94, 14).addBox(-4.5F, -9.5F, -3.0F, 9.0F, 4.0F, 6.0F).texOffs(60, 12).addBox(-5.0F, -1.5F, -3.4F, 10.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -0.5F, -3.0F, 9.0F, 9.0F, 6.0F).texOffs(60, 0).addBox(-5.0F, 8.0F, -3.5F, 10.0F, 5.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail_right", CubeListBuilder.create().texOffs(120, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 13.0F, 1.0F), PartPose.offsetAndRotation(-1.5F, -6.0F, 3.2F, 0.1047F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail_left", CubeListBuilder.create().texOffs(38, 24).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 13.0F, 1.0F), PartPose.offsetAndRotation(1.5F, -6.0F, 3.2F, 0.1047F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(30, 0).addBox(-4.0F, -8.0F, -3.5F, 8.0F, 8.0F, 7.0F).texOffs(90, 24).addBox(-4.5F, -8.6F, -4.3F, 9.0F, 2.0F, 2.0F).texOffs(56, 21).addBox(-2.5F, -9.5F, -1.0F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_crown = p_head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(74, 30).addBox(-3.5F, -1.0F, 0.0F, 7.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -4.5F, 3.4F, 0.0F, 0.0F, 0.0F));
        p_crown.addOrReplaceChild("spike_0", CubeListBuilder.create().texOffs(44, 24).addBox(-1.0F, -12.0F, -0.5F, 2.0F, 8.0F, 2.0F).texOffs(124, 14).addBox(-0.5F, -14.0F, 0.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.5585F, 0.0F, -1.3963F));
        p_crown.addOrReplaceChild("spike_1", CubeListBuilder.create().texOffs(16, 28).addBox(-1.0F, -10.0F, -0.5F, 2.0F, 6.0F, 2.0F).texOffs(56, 15).addBox(-0.5F, -12.0F, 0.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.5585F, 0.0F, -1.0472F));
        p_crown.addOrReplaceChild("spike_2", CubeListBuilder.create().texOffs(112, 24).addBox(-1.0F, -12.0F, -0.5F, 2.0F, 8.0F, 2.0F).texOffs(124, 17).addBox(-0.5F, -14.0F, 0.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.5585F, 0.0F, -0.6981F));
        p_crown.addOrReplaceChild("spike_3", CubeListBuilder.create().texOffs(90, 28).addBox(-1.0F, -10.0F, -0.5F, 2.0F, 6.0F, 2.0F).texOffs(56, 18).addBox(-0.5F, -12.0F, 0.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.5585F, 0.0F, -0.3491F));
        p_crown.addOrReplaceChild("spike_4", CubeListBuilder.create().texOffs(120, 24).addBox(-1.0F, -12.0F, -0.5F, 2.0F, 8.0F, 2.0F).texOffs(124, 20).addBox(-0.5F, -14.0F, 0.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.5585F, 0.0F, 0.0F));
        p_crown.addOrReplaceChild("spike_5", CubeListBuilder.create().texOffs(98, 28).addBox(-1.0F, -10.0F, -0.5F, 2.0F, 6.0F, 2.0F).texOffs(90, 21).addBox(-0.5F, -12.0F, 0.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.5585F, 0.0F, 0.3491F));
        p_crown.addOrReplaceChild("spike_6", CubeListBuilder.create().texOffs(0, 28).addBox(-1.0F, -12.0F, -0.5F, 2.0F, 8.0F, 2.0F).texOffs(86, 33).addBox(-0.5F, -14.0F, 0.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.5585F, 0.0F, 0.6981F));
        p_crown.addOrReplaceChild("spike_7", CubeListBuilder.create().texOffs(56, 30).addBox(-1.0F, -10.0F, -0.5F, 2.0F, 6.0F, 2.0F).texOffs(110, 34).addBox(-0.5F, -12.0F, 0.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.5585F, 0.0F, 1.0472F));
        p_crown.addOrReplaceChild("spike_8", CubeListBuilder.create().texOffs(8, 28).addBox(-1.0F, -12.0F, -0.5F, 2.0F, 8.0F, 2.0F).texOffs(114, 34).addBox(-0.5F, -14.0F, 0.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.5585F, 0.0F, 1.3963F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(0, 15).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 10.0F, 3.0F).texOffs(24, 15).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 5.0F, 4.0F), PartPose.offsetAndRotation(-4.9F, -8.0F, 0.0F, 0.0F, 0.0F, 0.1047F));
        p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(28, 31).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(74, 33).addBox(-1.0F, 3.0F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(78, 33).addBox(0.2F, 3.0F, 0.2F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(12, 15).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 10.0F, 3.0F).texOffs(40, 15).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 5.0F, 4.0F), PartPose.offsetAndRotation(4.9F, -8.0F, 0.0F, 0.0F, 0.0F, -0.1047F));
        p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(44, 34).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(82, 33).addBox(-1.0F, 3.0F, -1.0F, 1.0F, 1.0F, 1.0F).texOffs(118, 34).addBox(0.2F, 3.0F, 0.2F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("sigil", CubeListBuilder.create().texOffs(64, 35).addBox(-5.0F, -6.0F, 0.0F, 10.0F, 1.0F, 1.0F).texOffs(64, 37).addBox(-5.0F, 5.0F, 0.0F, 10.0F, 1.0F, 1.0F).texOffs(106, 28).addBox(-6.0F, -5.0F, 0.0F, 1.0F, 10.0F, 1.0F).texOffs(24, 31).addBox(5.0F, -5.0F, 0.0F, 1.0F, 10.0F, 1.0F).texOffs(52, 24).addBox(-0.5F, -8.0F, -0.4F, 1.0F, 16.0F, 1.0F).texOffs(56, 28).addBox(-8.0F, -0.5F, -0.6F, 16.0F, 1.0F, 1.0F).texOffs(64, 30).addBox(-2.0F, -2.0F, -1.0F, 4.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -6.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> FLAMEWRIGHT_ONLY = Map.of();

    public static LayerDefinition aurovore() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -3.0F, -5.5F, 8.0F, 5.0F, 11.0F), PartPose.offsetAndRotation(0.0F, 19.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_0", CubeListBuilder.create().texOffs(6, 42).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F).texOffs(60, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.0F, -3.5F, -0.3142F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_1", CubeListBuilder.create().texOffs(12, 44).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F).texOffs(60, 2).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.0F, -0.5F, -0.3142F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_2", CubeListBuilder.create().texOffs(42, 33).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(60, 4).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 2.5F, -0.3142F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("spine_3", CubeListBuilder.create().texOffs(18, 44).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(60, 6).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 5.0F, -0.3142F, 0.0F, 0.0F));
        PartDefinition p_neck = p_body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(18, 16).addBox(-2.5F, -2.0F, -3.0F, 5.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -2.0F, -5.0F, -0.6632F, 0.0F, 0.0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(38, 0).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 5.0F, 5.0F).texOffs(36, 20).addBox(-2.0F, -1.5F, -9.0F, 4.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -1.5F, -2.5F, 0.6632F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 45).addBox(-1.5F, -0.5F, -1.0F, 3.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -1.5F, -9.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 25).addBox(-2.0F, -0.5F, -4.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 1.5F, -4.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("pouch", CubeListBuilder.create().texOffs(32, 27).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -2.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_crest = p_head.addOrReplaceChild("crest", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -3.0F, -2.0F, -0.1745F, 0.0F, 0.0F));
        p_crest.addOrReplaceChild("crest_0", CubeListBuilder.create().texOffs(46, 41).addBox(-0.5F, -4.0F, -1.0F, 1.0F, 4.0F, 2.0F).texOffs(60, 8).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, -0.6981F, 0.0F, -0.733F));
        p_crest.addOrReplaceChild("crest_1", CubeListBuilder.create().texOffs(54, 37).addBox(-0.5F, -5.0F, -1.0F, 1.0F, 5.0F, 2.0F).texOffs(60, 10).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.3142F, 0.0F, -0.3491F));
        p_crest.addOrReplaceChild("crest_2", CubeListBuilder.create().texOffs(28, 33).addBox(-0.5F, -6.0F, -1.0F, 1.0F, 6.0F, 2.0F).texOffs(60, 12).addBox(-0.5F, -7.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2094F, 0.0F, 0.0F));
        p_crest.addOrReplaceChild("crest_3", CubeListBuilder.create().texOffs(40, 41).addBox(-0.5F, -5.0F, -1.0F, 1.0F, 5.0F, 2.0F).texOffs(60, 14).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, -0.3142F, 0.0F, 0.3491F));
        p_crest.addOrReplaceChild("crest_4", CubeListBuilder.create().texOffs(0, 42).addBox(-0.5F, -4.0F, -1.0F, 1.0F, 4.0F, 2.0F).texOffs(60, 16).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, -0.6981F, 0.0F, 0.733F));
        PartDefinition p_tail = p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(38, 10).addBox(-2.5F, -2.0F, -0.5F, 5.0F, 4.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 5.5F, -0.0698F, 0.0F, 0.0F));
        PartDefinition p_tail2 = p_tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.5F, 5.5F, 0.1396F, 0.0F, 0.0F));
        p_tail2.addOrReplaceChild("spine_tail", CubeListBuilder.create().texOffs(52, 44).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(60, 18).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -1.5F, 2.5F, -0.3491F, 0.0F, 0.0F));
        p_tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(18, 24).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, 0.6981F, 0.0F, 0.0F));
        PartDefinition p_leg_front_right = p_body.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(16, 36).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-4.0F, 1.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        p_leg_front_right.addOrReplaceChild("shin_front_right", CubeListBuilder.create().texOffs(52, 20).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(48, 27).addBox(-1.5F, 3.0F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-3.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_front_left = p_body.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(42, 37).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(4.0F, 1.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        p_leg_front_left.addOrReplaceChild("shin_front_left", CubeListBuilder.create().texOffs(34, 33).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(0, 31).addBox(-1.5F, 3.0F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(3.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_back_right = p_body.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(16, 40).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-4.0F, 1.0F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_leg_back_right.addOrReplaceChild("shin_back_right", CubeListBuilder.create().texOffs(0, 36).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(14, 31).addBox(-1.5F, 3.0F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-3.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_back_left = p_body.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(28, 41).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(4.0F, 1.0F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_leg_back_left.addOrReplaceChild("shin_back_left", CubeListBuilder.create().texOffs(8, 36).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(48, 32).addBox(-1.5F, 3.0F, -3.0F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(3.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> AUROVORE_ONLY = Map.of();

    public static LayerDefinition mendingCrystalling() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(24, 0).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(0, 0).addBox(-1.5F, 2.0F, -1.5F, 3.0F, 4.0F, 3.0F).texOffs(12, 17).addBox(-1.0F, 6.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, 0.0F, 0.7854F, 0.0F));
        p_body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(12, 0).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_petal_0 = p_body.addOrReplaceChild("petal_0", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_petal_0.addOrReplaceChild("upper_0", CubeListBuilder.create().texOffs(36, 0).addBox(0.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F).texOffs(34, 12).addBox(0.0F, -3.0F, -1.5F, 1.0F, 1.0F, 3.0F).texOffs(58, 12).addBox(0.0F, -5.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(20, 20).addBox(0.0F, -6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.1396F));
        p_petal_0.addOrReplaceChild("lower_0", CubeListBuilder.create().texOffs(46, 0).addBox(0.0F, 0.0F, -2.0F, 1.0F, 2.0F, 4.0F).texOffs(56, 0).addBox(0.0F, 2.0F, -1.5F, 1.0F, 2.0F, 3.0F).texOffs(34, 16).addBox(0.0F, 4.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(24, 20).addBox(0.0F, 6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.1396F));
        PartDefinition p_petal_1 = p_body.addOrReplaceChild("petal_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        p_petal_1.addOrReplaceChild("upper_1", CubeListBuilder.create().texOffs(12, 6).addBox(0.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F).texOffs(42, 12).addBox(0.0F, -3.0F, -1.5F, 1.0F, 1.0F, 3.0F).texOffs(40, 16).addBox(0.0F, -5.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(28, 20).addBox(0.0F, -6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.1396F));
        p_petal_1.addOrReplaceChild("lower_1", CubeListBuilder.create().texOffs(22, 6).addBox(0.0F, 0.0F, -2.0F, 1.0F, 2.0F, 4.0F).texOffs(10, 12).addBox(0.0F, 2.0F, -1.5F, 1.0F, 2.0F, 3.0F).texOffs(46, 16).addBox(0.0F, 4.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(32, 20).addBox(0.0F, 6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.1396F));
        PartDefinition p_petal_2 = p_body.addOrReplaceChild("petal_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        p_petal_2.addOrReplaceChild("upper_2", CubeListBuilder.create().texOffs(32, 6).addBox(0.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F).texOffs(50, 12).addBox(0.0F, -3.0F, -1.5F, 1.0F, 1.0F, 3.0F).texOffs(52, 16).addBox(0.0F, -5.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(36, 20).addBox(0.0F, -6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.1396F));
        p_petal_2.addOrReplaceChild("lower_2", CubeListBuilder.create().texOffs(42, 6).addBox(0.0F, 0.0F, -2.0F, 1.0F, 2.0F, 4.0F).texOffs(18, 12).addBox(0.0F, 2.0F, -1.5F, 1.0F, 2.0F, 3.0F).texOffs(58, 16).addBox(0.0F, 4.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(40, 20).addBox(0.0F, 6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.1396F));
        PartDefinition p_petal_3 = p_body.addOrReplaceChild("petal_3", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 4.7124F, 0.0F));
        p_petal_3.addOrReplaceChild("upper_3", CubeListBuilder.create().texOffs(52, 6).addBox(0.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F).texOffs(0, 13).addBox(0.0F, -3.0F, -1.5F, 1.0F, 1.0F, 3.0F).texOffs(0, 17).addBox(0.0F, -5.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(44, 20).addBox(0.0F, -6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.1396F));
        p_petal_3.addOrReplaceChild("lower_3", CubeListBuilder.create().texOffs(0, 7).addBox(0.0F, 0.0F, -2.0F, 1.0F, 2.0F, 4.0F).texOffs(26, 12).addBox(0.0F, 2.0F, -1.5F, 1.0F, 2.0F, 3.0F).texOffs(6, 17).addBox(0.0F, 4.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(48, 20).addBox(0.0F, 6.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(2.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.1396F));
        p_body.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(20, 17).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -6.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("keel", CubeListBuilder.create().texOffs(24, 17).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_halo = root.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_halo.addOrReplaceChild("shard_0", CubeListBuilder.create().texOffs(28, 17).addBox(5.0F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.3491F, 0.0F));
        p_halo.addOrReplaceChild("shard_1", CubeListBuilder.create().texOffs(12, 20).addBox(5.0F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 2.4435F, 0.0F));
        p_halo.addOrReplaceChild("shard_2", CubeListBuilder.create().texOffs(16, 20).addBox(5.0F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 4.5379F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }
    public static final Map<String, String[]> MENDING_CRYSTALLING_ONLY = Map.of();

    public static LayerDefinition liftstoneMonolith() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 43).addBox(-13.0F, -5.0F, -10.0F, 26.0F, 5.0F, 20.0F).texOffs(0, 0).addBox(-10.0F, -36.0F, -6.0F, 20.0F, 31.0F, 12.0F).texOffs(0, 94).addBox(-11.0F, -9.0F, -7.0F, 22.0F, 3.0F, 14.0F).texOffs(0, 111).addBox(-11.0F, -35.0F, -7.0F, 22.0F, 2.0F, 14.0F).texOffs(60, 68).addBox(-8.0F, -44.0F, -5.0F, 16.0F, 8.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_capstone = p_body.addOrReplaceChild("capstone", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -52.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_capstone.addOrReplaceChild("cap_right", CubeListBuilder.create().texOffs(64, 0).addBox(-14.0F, -10.0F, -8.0F, 14.0F, 10.0F, 16.0F).texOffs(64, 26).addBox(-10.0F, -13.0F, -5.0F, 10.0F, 3.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_capstone.addOrReplaceChild("cap_left", CubeListBuilder.create().texOffs(0, 68).addBox(0.0F, -10.0F, -8.0F, 14.0F, 10.0F, 16.0F).texOffs(72, 86).addBox(0.0F, -13.0F, -5.0F, 10.0F, 3.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_capstone.addOrReplaceChild("heart", CubeListBuilder.create().texOffs(92, 39).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("shard_0", CubeListBuilder.create().texOffs(104, 26).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(14.0F, -40.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("shard_1", CubeListBuilder.create().texOffs(116, 26).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-7.0F, -43.0F, 12.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("shard_2", CubeListBuilder.create().texOffs(104, 32).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-7.0F, -46.0F, -12.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static final Map<String, String[]> LIFTSTONE_MONOLITH_ONLY = Map.of();

    public static LayerDefinition astralAugur() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(66, 0).addBox(-3.5F, -8.0F, -2.5F, 7.0F, 8.0F, 5.0F).texOffs(90, 9).addBox(-4.0F, -1.5F, -3.0F, 8.0F, 2.0F, 6.0F).texOffs(90, 0).addBox(-4.5F, -8.5F, -3.0F, 9.0F, 3.0F, 6.0F).texOffs(76, 17).addBox(-2.5F, -10.5F, -2.0F, 5.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(36, 0).addBox(-4.5F, 0.0F, -3.0F, 9.0F, 7.0F, 6.0F).texOffs(0, 0).addBox(-5.5F, 7.0F, -3.5F, 11.0F, 7.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_neck = p_body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(120, 0).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(60, 13).addBox(-2.0F, -5.0F, -2.5F, 4.0F, 5.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(118, 9).addBox(-0.5F, -4.0F, 0.0F, 1.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -4.5F, 0.5F, -0.4887F, 0.0F, 0.0F));
        PartDefinition p_halo = p_head.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -7.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_halo.addOrReplaceChild("ring_0", CubeListBuilder.create().texOffs(60, 22).addBox(-2.5F, -0.5F, -6.5F, 5.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_halo.addOrReplaceChild("ring_1", CubeListBuilder.create().texOffs(72, 23).addBox(-2.5F, -0.5F, -6.5F, 5.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));
        p_halo.addOrReplaceChild("ring_2", CubeListBuilder.create().texOffs(84, 23).addBox(-2.5F, -0.5F, -6.5F, 5.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        p_halo.addOrReplaceChild("ring_3", CubeListBuilder.create().texOffs(96, 23).addBox(-2.5F, -0.5F, -6.5F, 5.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 2.3562F, 0.0F));
        p_halo.addOrReplaceChild("ring_4", CubeListBuilder.create().texOffs(60, 24).addBox(-2.5F, -0.5F, -6.5F, 5.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        p_halo.addOrReplaceChild("ring_5", CubeListBuilder.create().texOffs(36, 25).addBox(-2.5F, -0.5F, -6.5F, 5.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.927F, 0.0F));
        p_halo.addOrReplaceChild("ring_6", CubeListBuilder.create().texOffs(48, 25).addBox(-2.5F, -0.5F, -6.5F, 5.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 4.7124F, 0.0F));
        p_halo.addOrReplaceChild("ring_7", CubeListBuilder.create().texOffs(72, 25).addBox(-2.5F, -0.5F, -6.5F, 5.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 5.4978F, 0.0F));
        p_halo.addOrReplaceChild("mote_0", CubeListBuilder.create().texOffs(94, 17).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.1F)).texOffs(32, 14).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        p_halo.addOrReplaceChild("mote_1", CubeListBuilder.create().texOffs(106, 17).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.1F)).texOffs(32, 20).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(-5.2F, 0.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        p_halo.addOrReplaceChild("mote_2", CubeListBuilder.create().texOffs(0, 22).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.1F)).texOffs(20, 22).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(5.2F, 0.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(36, 13).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F).texOffs(0, 14).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(-4.6F, -7.5F, 0.0F, 0.0F, 0.0F, 0.1047F));
        PartDefinition p_hand_right = p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(76, 13).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(84, 13).addBox(-1.0F, 2.0F, -1.0F, 1.0F, 3.0F, 1.0F).texOffs(24, 22).addBox(0.2F, 2.0F, 0.2F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("spark_right", CubeListBuilder.create().texOffs(118, 17).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(48, 13).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F).texOffs(16, 14).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(4.6F, -7.5F, 0.0F, 0.0F, 0.0F, -0.1047F));
        PartDefinition p_hand_left = p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(118, 21).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(28, 22).addBox(-1.0F, 2.0F, -1.0F, 1.0F, 3.0F, 1.0F).texOffs(108, 23).addBox(0.2F, 2.0F, 0.2F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_left.addOrReplaceChild("spark_left", CubeListBuilder.create().texOffs(12, 22).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> ASTRAL_AUGUR_ONLY = Map.of();

    public static LayerDefinition nullcantor() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(96, 29).addBox(-3.5F, -8.0F, -2.5F, 7.0F, 8.0F, 5.0F).texOffs(28, 19).addBox(-4.0F, -1.5F, -3.0F, 8.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_skirt = p_body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(96, 15).addBox(-4.5F, 0.0F, -3.5F, 9.0F, 7.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_skirt.addOrReplaceChild("hem", CubeListBuilder.create().texOffs(56, 15).addBox(-5.5F, 0.0F, -4.5F, 11.0F, 7.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(56, 0).addBox(-8.0F, -1.5F, -6.0F, 16.0F, 3.0F, 12.0F).texOffs(0, 0).addBox(-6.0F, -1.7F, -8.0F, 12.0F, 3.0F, 16.0F).texOffs(28, 31).addBox(-5.0F, 1.5F, -4.0F, 10.0F, 1.0F, 8.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.1396F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("collar_broken", CubeListBuilder.create().texOffs(64, 31).addBox(-4.5F, -0.5F, -3.5F, 9.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.1396F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 19).addBox(-3.5F, -8.0F, -3.5F, 7.0F, 8.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("peak", CubeListBuilder.create().texOffs(12, 34).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -7.5F, 1.5F, -0.5236F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(112, 0).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 8.0F, 3.0F).texOffs(28, 40).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(-4.3F, -6.5F, 0.0F, -0.8727F, -0.4887F, 0.0F));
        p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(112, 11).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(0, 34).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 8.0F, 3.0F).texOffs(44, 40).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(4.3F, -6.5F, 0.0F, -0.8727F, 0.4887F, 0.0F));
        p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(120, 11).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> NULLCANTOR_ONLY = Map.of();

    public static LayerDefinition chorusGrazer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(68, 0).addBox(-5.0F, -3.0F, -8.0F, 10.0F, 7.0F, 16.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(66, 49).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 8.0F, 0.4363F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("fleece", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -2.0F, -10.0F, 14.0F, 9.0F, 20.0F).texOffs(0, 29).addBox(-5.0F, -4.0F, -9.0F, 10.0F, 2.0F, 6.0F).texOffs(68, 23).addBox(-6.0F, -5.0F, -3.0F, 12.0F, 3.0F, 6.0F).texOffs(32, 29).addBox(-5.0F, -4.0F, 3.0F, 10.0F, 2.0F, 6.0F).texOffs(74, 39).addBox(-8.0F, 0.0F, -7.0F, 1.0F, 5.0F, 5.0F).texOffs(86, 42).addBox(-8.0F, 1.0F, 2.0F, 1.0F, 4.0F, 5.0F).texOffs(98, 42).addBox(7.0F, 1.0F, -4.0F, 1.0F, 4.0F, 5.0F).texOffs(36, 43).addBox(7.0F, 0.0F, 4.0F, 1.0F, 5.0F, 4.0F).texOffs(88, 35).addBox(-5.0F, -1.0F, 10.0F, 10.0F, 6.0F, 1.0F).texOffs(36, 37).addBox(-5.0F, 0.0F, -11.0F, 10.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("growths", CubeListBuilder.create().texOffs(120, 0).addBox(0.0F, -10.0F, 0.0F, 2.0F, 10.0F, 2.0F).texOffs(58, 39).addBox(-1.0F, -14.0F, -1.0F, 4.0F, 4.0F, 4.0F).texOffs(76, 49).addBox(2.0F, -11.0F, 0.0F, 3.0F, 2.0F, 2.0F).texOffs(36, 52).addBox(4.0F, -13.0F, 0.0F, 2.0F, 2.0F, 2.0F).texOffs(110, 46).addBox(3.5F, -16.0F, -0.5F, 3.0F, 3.0F, 3.0F).texOffs(120, 12).addBox(-1.5F, -9.0F, -6.0F, 2.0F, 9.0F, 2.0F).texOffs(54, 47).addBox(-2.0F, -12.0F, -6.5F, 3.0F, 3.0F, 3.0F).texOffs(86, 51).addBox(-4.5F, -8.0F, -6.0F, 3.0F, 2.0F, 2.0F).texOffs(108, 52).addBox(-4.5F, -10.0F, -6.0F, 2.0F, 2.0F, 2.0F).texOffs(12, 48).addBox(-5.0F, -12.0F, -6.5F, 3.0F, 2.0F, 3.0F).texOffs(46, 43).addBox(-2.0F, -8.0F, 5.0F, 2.0F, 8.0F, 2.0F).texOffs(0, 48).addBox(-2.5F, -11.0F, 4.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(104, 23).addBox(-3.0F, -3.0F, -6.0F, 6.0F, 6.0F, 6.0F).texOffs(24, 48).addBox(-2.0F, 0.0F, -8.0F, 4.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 10.0F, -10.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("head_fleece", CubeListBuilder.create().texOffs(64, 32).addBox(-3.5F, -1.5F, -4.5F, 7.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(88, 32).addBox(-4.0F, 0.0F, -1.0F, 4.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-3.0F, -2.0F, -3.0F, 0.0F, 0.0F, -0.6981F));
        p_head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(96, 51).addBox(0.0F, 0.0F, -1.0F, 4.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(3.0F, -2.0F, -3.0F, 0.0F, 0.0F, 0.6981F));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(110, 35).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(-3.0F, 16.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(0, 37).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(3.0F, 16.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(12, 37).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(-3.0F, 16.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(24, 37).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offsetAndRotation(3.0F, 16.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> CHORUS_GRAZER_ONLY = Map.of();

    public static LayerDefinition levimoth() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 10).addBox(-2.5F, -2.5F, -2.0F, 5.0F, 5.0F, 5.0F).texOffs(36, 10).addBox(-3.0F, -3.0F, -2.5F, 6.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 17.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(50, 10).addBox(-2.0F, -2.0F, -3.0F, 4.0F, 4.0F, 3.0F).texOffs(122, 0).addBox(-2.6F, -1.5F, -2.6F, 1.0F, 2.0F, 2.0F).texOffs(122, 4).addBox(1.6F, -1.5F, -2.6F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -0.3F, -2.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(76, 10).addBox(-2.5F, -6.0F, 0.0F, 5.0F, 6.0F, 0.0F), PartPose.offsetAndRotation(-1.0F, -2.0F, -2.5F, -0.6981F, 0.3491F, -0.6981F));
        p_head.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(64, 16).addBox(-2.5F, -6.0F, 0.0F, 5.0F, 6.0F, 0.0F), PartPose.offsetAndRotation(1.0F, -2.0F, -2.5F, -0.6981F, -0.3491F, 0.6981F));
        PartDefinition p_abdomen = p_body.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(20, 10).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 4.0F).texOffs(64, 10).addBox(-1.5F, -1.5F, 4.0F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.4F, 3.0F, -0.2094F, 0.0F, 0.0F));
        p_abdomen.addOrReplaceChild("dust_sacs", CubeListBuilder.create().texOffs(122, 8).addBox(-3.0F, -1.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(122, 12).addBox(2.0F, -1.0F, -1.0F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, 0.0F, -3.0F, 12.0F, 0.0F, 10.0F).texOffs(88, 0).addBox(-9.0F, 0.25F, 3.0F, 9.0F, 0.0F, 8.0F), PartPose.offsetAndRotation(-2.3F, -2.2F, -0.5F, -0.4887F, 0.0F, 0.4887F));
        p_body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(44, 0).addBox(0.0F, 0.0F, -3.0F, 12.0F, 0.0F, 10.0F).texOffs(88, 8).addBox(0.0F, 0.25F, 3.0F, 9.0F, 0.0F, 8.0F), PartPose.offsetAndRotation(2.3F, -2.2F, -0.5F, -0.4887F, 0.0F, -0.4887F));
        p_body.addOrReplaceChild("leg_right_0", CubeListBuilder.create().texOffs(86, 10).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(-2.0F, 2.3F, -1.5F, 0.0F, 0.0F, 0.8727F));
        p_body.addOrReplaceChild("leg_left_0", CubeListBuilder.create().texOffs(86, 12).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(2.0F, 2.3F, -1.5F, 0.0F, 0.0F, -0.8727F));
        p_body.addOrReplaceChild("leg_right_1", CubeListBuilder.create().texOffs(86, 14).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(-2.0F, 2.3F, 0.5F, 0.0F, 0.0F, 0.8727F));
        p_body.addOrReplaceChild("leg_left_1", CubeListBuilder.create().texOffs(74, 16).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(2.0F, 2.3F, 0.5F, 0.0F, 0.0F, -0.8727F));
        p_body.addOrReplaceChild("leg_right_2", CubeListBuilder.create().texOffs(76, 16).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(-2.0F, 2.3F, 2.5F, 0.0F, 0.0F, 0.8727F));
        p_body.addOrReplaceChild("leg_left_2", CubeListBuilder.create().texOffs(78, 16).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(2.0F, 2.3F, 2.5F, 0.0F, 0.0F, -0.8727F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> LEVIMOTH_ONLY = Map.of();

    public static LayerDefinition warpseedBloom() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("root_0", CubeListBuilder.create().texOffs(24, 18).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, -0.1396F, 0.7854F, 0.0F));
        root.addOrReplaceChild("root_1", CubeListBuilder.create().texOffs(92, 23).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, -0.1396F, 2.3562F, 0.0F));
        root.addOrReplaceChild("root_2", CubeListBuilder.create().texOffs(108, 25).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, -0.1396F, 3.927F, 0.0F));
        root.addOrReplaceChild("root_3", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, -0.1396F, 5.4978F, 0.0F));
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 18).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_stem = root.addOrReplaceChild("stem", CubeListBuilder.create().texOffs(112, 13).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 8.0F, 4.0F).texOffs(72, 23).addBox(-2.5F, -9.0F, -2.5F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_stem.addOrReplaceChild("branch_left", CubeListBuilder.create().texOffs(88, 31).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F).texOffs(100, 31).addBox(2.5F, -4.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(44, 26).addBox(2.0F, -6.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(1.5F, -2.0F, 0.5F, 0.0F, -0.9599F, -0.1745F));
        PartDefinition p_stem_upper = p_stem.addOrReplaceChild("stem_upper", CubeListBuilder.create().texOffs(16, 26).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, -0.0698F, 0.0F, 0.0F));
        p_stem_upper.addOrReplaceChild("branch_right", CubeListBuilder.create().texOffs(52, 32).addBox(-3.0F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(44, 32).addBox(-4.0F, -3.5F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(56, 26).addBox(-4.5F, -6.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-1.0F, -1.5F, 0.5F, 0.0F, 1.0472F, 0.2094F));
        PartDefinition p_bulb = p_stem_upper.addOrReplaceChild("bulb", CubeListBuilder.create().texOffs(40, 17).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 1.0F, 8.0F).texOffs(0, 0).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 8.0F, 10.0F).texOffs(80, 0).addBox(-6.0F, -7.5F, -4.0F, 12.0F, 5.0F, 8.0F).texOffs(40, 0).addBox(-4.0F, -7.5F, -6.0F, 8.0F, 5.0F, 12.0F).texOffs(80, 13).addBox(-4.0F, -11.0F, -4.0F, 8.0F, 2.0F, 8.0F).texOffs(28, 26).addBox(-2.0F, -12.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_bulb.addOrReplaceChild("pore", CubeListBuilder.create().texOffs(68, 30).addBox(-2.5F, -2.5F, -0.6F, 5.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -5.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_bulb.addOrReplaceChild("seed", CubeListBuilder.create().texOffs(108, 33).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -5.0F, -6.2F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_sepal_0 = p_bulb.addOrReplaceChild("sepal_0", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.5236F, 0.0F));
        p_sepal_0.addOrReplaceChild("sepal_0_leaf", CubeListBuilder.create().texOffs(120, 0).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.3F, 0.4887F, 0.0F, 0.0F));
        PartDefinition p_sepal_1 = p_bulb.addOrReplaceChild("sepal_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 1.5708F, 0.0F));
        p_sepal_1.addOrReplaceChild("sepal_1_leaf", CubeListBuilder.create().texOffs(120, 6).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.3F, 0.4887F, 0.0F, 0.0F));
        PartDefinition p_sepal_2 = p_bulb.addOrReplaceChild("sepal_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 2.618F, 0.0F));
        p_sepal_2.addOrReplaceChild("sepal_2_leaf", CubeListBuilder.create().texOffs(72, 17).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.3F, 0.4887F, 0.0F, 0.0F));
        PartDefinition p_sepal_3 = p_bulb.addOrReplaceChild("sepal_3", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 3.6652F, 0.0F));
        p_sepal_3.addOrReplaceChild("sepal_3_leaf", CubeListBuilder.create().texOffs(80, 30).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.3F, 0.4887F, 0.0F, 0.0F));
        PartDefinition p_sepal_4 = p_bulb.addOrReplaceChild("sepal_4", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 4.7124F, 0.0F));
        p_sepal_4.addOrReplaceChild("sepal_4_leaf", CubeListBuilder.create().texOffs(28, 31).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.3F, 0.4887F, 0.0F, 0.0F));
        PartDefinition p_sepal_5 = p_bulb.addOrReplaceChild("sepal_5", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 5.7596F, 0.0F));
        p_sepal_5.addOrReplaceChild("sepal_5_leaf", CubeListBuilder.create().texOffs(36, 31).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.3F, 0.4887F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> WARPSEED_BLOOM_ONLY = Map.of();

    public static LayerDefinition glimmerless() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_leg_right = root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(52, 27).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(-2.6F, 8.4F, 1.5F, -0.4363F, 0.0F, 0.0F));
        PartDefinition p_shin_right = p_leg_right.addOrReplaceChild("shin_right", CubeListBuilder.create().texOffs(56, 13).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.8727F, 0.0F, 0.0F));
        p_shin_right.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(12, 35).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 2.0F, 5.0F).texOffs(12, 32).addBox(-1.5F, 1.0F, -5.0F, 1.0F, 1.0F, 1.0F).texOffs(16, 32).addBox(0.5F, 1.0F, -5.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, -0.4363F, 0.0F, 0.0F));
        PartDefinition p_leg_left = root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(2.6F, 8.4F, 1.5F, -0.4363F, 0.0F, 0.0F));
        PartDefinition p_shin_left = p_leg_left.addOrReplaceChild("shin_left", CubeListBuilder.create().texOffs(56, 37).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.8727F, 0.0F, 0.0F));
        p_shin_left.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(28, 35).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 2.0F, 5.0F).texOffs(44, 35).addBox(-1.5F, 1.0F, -5.0F, 1.0F, 1.0F, 1.0F).texOffs(48, 35).addBox(0.5F, 1.0F, -5.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, -0.4363F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 27).addBox(-3.0F, -3.0F, -2.0F, 6.0F, 4.0F, 4.0F).texOffs(0, 23).addBox(-2.5F, -8.0F, -2.0F, 5.0F, 5.0F, 4.0F).texOffs(0, 0).addBox(-4.5F, -15.0F, -3.0F, 9.0F, 7.0F, 6.0F).texOffs(20, 13).addBox(-3.5F, -16.0F, -1.0F, 7.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 8.4F, 1.5F, 0.6632F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("shard_0", CubeListBuilder.create().texOffs(0, 48).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -14.5F, 3.0F, -1.7453F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("shard_1", CubeListBuilder.create().texOffs(8, 48).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -11.5F, 3.0F, -1.7453F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("shard_2", CubeListBuilder.create().texOffs(56, 23).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -8.5F, 3.0F, -1.7453F, 0.0F, 0.0F));
        PartDefinition p_neck = p_body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(44, 37).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -13.5F, -2.5F, 1.309F, 0.0F, 0.0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 13).addBox(-2.5F, -4.0F, -4.5F, 5.0F, 5.0F, 5.0F).texOffs(32, 21).addBox(-2.5F, -4.0F, -5.1F, 5.0F, 5.0F, 1.0F).texOffs(48, 47).addBox(-1.5F, -5.0F, -3.5F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, -1.6581F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(32, 45).addBox(-2.0F, 0.0F, -4.5F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 1.0F, -0.5F, 0.1745F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(44, 13).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 11.0F, 3.0F), PartPose.offsetAndRotation(-5.2F, -13.5F, -0.5F, -0.8029F, 0.0F, 0.1222F));
        PartDefinition p_forearm_right = p_arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(30, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, -0.1396F, 0.0F, 0.0F));
        PartDefinition p_hand_right = p_forearm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(0, 42).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("claw_right_0", CubeListBuilder.create().texOffs(16, 48).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(-1.1F, 2.0F, -1.2F, -0.2094F, 0.0F, -0.1047F));
        p_hand_right.addOrReplaceChild("claw_right_1", CubeListBuilder.create().texOffs(20, 48).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -1.2F, -0.2094F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("claw_right_2", CubeListBuilder.create().texOffs(24, 48).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(1.1F, 2.0F, -1.2F, -0.2094F, 0.0F, 0.1047F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(20, 21).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 11.0F, 3.0F), PartPose.offsetAndRotation(5.2F, -13.5F, -0.5F, -0.8029F, 0.0F, -0.1222F));
        PartDefinition p_forearm_left = p_arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(46, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, -0.1396F, 0.0F, 0.0F));
        PartDefinition p_hand_left = p_forearm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(16, 42).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_left.addOrReplaceChild("claw_left_0", CubeListBuilder.create().texOffs(28, 48).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(-1.1F, 2.0F, -1.2F, -0.2094F, 0.0F, -0.1047F));
        p_hand_left.addOrReplaceChild("claw_left_1", CubeListBuilder.create().texOffs(32, 50).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -1.2F, -0.2094F, 0.0F, 0.0F));
        p_hand_left.addOrReplaceChild("claw_left_2", CubeListBuilder.create().texOffs(36, 50).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(1.1F, 2.0F, -1.2F, -0.2094F, 0.0F, 0.1047F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> GLIMMERLESS_ONLY = Map.of();

    public static LayerDefinition riftjackal() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(22, 12).addBox(-2.5F, -2.5F, -6.0F, 5.0F, 5.0F, 5.0F).texOffs(16, 22).addBox(-1.5F, -1.8F, -1.0F, 3.0F, 3.0F, 4.0F).texOffs(30, 22).addBox(-2.0F, -2.5F, 3.0F, 4.0F, 4.0F, 3.0F).texOffs(22, 0).addBox(-0.5F, -3.0F, -5.5F, 1.0F, 1.0F, 11.0F), PartPose.offsetAndRotation(0.0F, 12.5F, 0.0F, -0.0524F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("flame", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, -5.0F, -5.5F, 0.0F, 5.0F, 11.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_crest = p_body.addOrReplaceChild("crest", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -2.6F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_crest.addOrReplaceChild("crest_r", CubeListBuilder.create().texOffs(46, 0).addBox(0.0F, -3.0F, -4.5F, 0.0F, 3.0F, 8.0F), PartPose.offsetAndRotation(-1.2F, 0.0F, 0.0F, 0.0F, 0.0F, -0.384F));
        p_crest.addOrReplaceChild("crest_l", CubeListBuilder.create().texOffs(46, 11).addBox(0.0F, -3.0F, -4.5F, 0.0F, 3.0F, 8.0F), PartPose.offsetAndRotation(1.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.384F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(42, 12).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.1F)).texOffs(30, 36).addBox(-1.0F, 7.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, -1.8F, 5.8F, 0.8378F, 0.0F, 0.0F));
        PartDefinition p_head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(48, 29).addBox(-1.0F, -1.5F, -2.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.25F)).texOffs(0, 16).addBox(-2.0F, -3.5F, -5.5F, 4.0F, 3.0F, 4.0F).texOffs(38, 36).addBox(-0.5F, -4.0F, -4.5F, 1.0F, 1.0F, 3.0F).texOffs(44, 22).addBox(-1.0F, -2.0F, -10.5F, 2.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 11.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(20, 29).addBox(-1.0F, 0.0F, -4.5F, 2.0F, 1.0F, 4.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, -5.5F, 0.1047F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F).texOffs(16, 20).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-1.4F, -3.5F, -2.5F, 0.4189F, 0.0F, -0.4538F));
        p_head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(10, 23).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F).texOffs(42, 20).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(1.4F, -3.5F, -2.5F, 0.4189F, 0.0F, 0.4538F));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(32, 29).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 5.0F, 2.0F).texOffs(10, 37).addBox(-0.5F, 4.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.2F)).texOffs(0, 31).addBox(-1.0F, 9.5F, -2.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.6F, 13.5F, -4.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(40, 29).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 5.0F, 2.0F).texOffs(14, 37).addBox(-0.5F, 4.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.2F)).texOffs(20, 34).addBox(-1.0F, 9.5F, -2.0F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.6F, 13.5F, -4.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(0, 23).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.25F)).texOffs(58, 22).addBox(-0.5F, 3.5F, 0.0F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.2F)).texOffs(0, 35).addBox(-1.0F, 10.0F, -1.5F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.5F, 13.0F, 4.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(10, 29).addBox(-1.0F, -1.5F, -1.5F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.25F)).texOffs(58, 30).addBox(-0.5F, 3.5F, 0.0F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.2F)).texOffs(48, 35).addBox(-1.0F, 10.0F, -1.5F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.5F, 13.0F, 4.5F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> RIFTJACKAL_ONLY = Map.of();

    public static LayerDefinition chorusHusk() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(82, 10).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F).texOffs(108, 23).addBox(-2.0F, 10.0F, -2.5F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-2.2F, 12.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(58, 12).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F).texOffs(70, 24).addBox(-2.0F, 10.0F, -2.5F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(2.2F, 12.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 15).addBox(-3.5F, -3.0F, -2.0F, 7.0F, 3.0F, 4.0F).texOffs(0, 0).addBox(-4.5F, -12.0F, -3.0F, 9.0F, 9.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 1.0F, 0.384F, 0.0F, 0.0F));
        PartDefinition p_growth = p_body.addOrReplaceChild("growth", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -8.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        p_growth.addOrReplaceChild("pod_back", CubeListBuilder.create().texOffs(58, 0).addBox(-3.5F, -3.5F, -2.5F, 7.0F, 7.0F, 5.0F), PartPose.offsetAndRotation(0.5F, 0.5F, 2.0F, 0.0F, 0.0F, 0.0F));
        p_growth.addOrReplaceChild("pod_top", CubeListBuilder.create().texOffs(30, 14).addBox(-2.5F, -2.5F, -2.0F, 5.0F, 5.0F, 4.0F), PartPose.offsetAndRotation(-1.5F, -5.0F, 1.5F, 0.0F, 0.0F, 0.0F));
        p_growth.addOrReplaceChild("pod_low", CubeListBuilder.create().texOffs(94, 23).addBox(-2.0F, -2.0F, -1.0F, 4.0F, 4.0F, 3.0F), PartPose.offsetAndRotation(-2.5F, 4.5F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_stalk_left = p_growth.addOrReplaceChild("stalk_left", CubeListBuilder.create().texOffs(70, 12).addBox(-1.0F, -9.0F, -1.0F, 2.0F, 9.0F, 2.0F).texOffs(60, 26).addBox(1.0F, -9.0F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(94, 10).addBox(2.0F, -13.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(2.5F, -3.0F, 0.5F, -0.3142F, 0.0F, 0.1745F));
        p_stalk_left.addOrReplaceChild("tip_left", CubeListBuilder.create().texOffs(116, 16).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(3.0F, -12.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_stalk_left.addOrReplaceChild("bloom_left", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(3.0F, -12.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_stalk_right = p_growth.addOrReplaceChild("stalk_right", CubeListBuilder.create().texOffs(48, 14).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F).texOffs(72, 30).addBox(-4.0F, -7.0F, -1.0F, 3.0F, 2.0F, 2.0F).texOffs(22, 15).addBox(-4.0F, -10.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(-2.5F, -2.0F, 0.5F, -0.2443F, 0.0F, -0.2094F));
        p_stalk_right.addOrReplaceChild("tip_right", CubeListBuilder.create().texOffs(48, 26).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-3.0F, -9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_stalk_right.addOrReplaceChild("bloom_right", CubeListBuilder.create().texOffs(16, 23).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(-3.0F, -9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("pod_shoulder_right", CubeListBuilder.create().texOffs(82, 0).addBox(-2.5F, -3.5F, -2.5F, 6.0F, 5.0F, 5.0F), PartPose.offsetAndRotation(-4.25F, -12.0F, 0.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("pod_shoulder_left", CubeListBuilder.create().texOffs(32, 23).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(4.5F, -11.5F, 1.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("bloom_shoulder", CubeListBuilder.create().texOffs(108, 29).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-4.5F, -15.0F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(30, 0).addBox(-3.5F, -7.0F, -4.0F, 7.0F, 7.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -12.0F, -1.0F, -0.2443F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(94, 16).addBox(-3.0F, 0.0F, -3.5F, 6.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("sprig", CubeListBuilder.create().texOffs(78, 12).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(86, 24).addBox(-1.0F, -5.5F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(1.5F, -7.0F, 0.0F, 0.0F, 0.0F, 0.2618F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(104, 0).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 13.0F, 3.0F), PartPose.offsetAndRotation(-5.5F, -11.0F, -0.5F, -0.384F, 0.0F, 0.0873F));
        p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(0, 30).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F).texOffs(78, 16).addBox(-1.5F, 2.0F, -1.5F, 1.0F, 2.0F, 1.0F).texOffs(78, 19).addBox(0.5F, 2.0F, -1.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(116, 0).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 13.0F, 3.0F), PartPose.offsetAndRotation(5.5F, -11.0F, -0.5F, -0.384F, 0.0F, -0.0873F));
        p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(60, 30).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F).texOffs(22, 20).addBox(-1.5F, 2.0F, -1.5F, 1.0F, 2.0F, 1.0F).texOffs(26, 20).addBox(0.5F, 2.0F, -1.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of chorus_husk. */
    public static final Map<String, String[]> CHORUS_HUSK_ONLY = Map.ofEntries(
            Map.entry("bloom_left", new String[] {"blooming_husk"}),
            Map.entry("bloom_right", new String[] {"blooming_husk"}),
            Map.entry("bloom_shoulder", new String[] {"blooming_husk"}),
            Map.entry("tip_left", new String[] {"base"}),
            Map.entry("tip_right", new String[] {"base"}));

    public static LayerDefinition gatecaster() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(54, 10).addBox(-4.0F, 0.0F, -2.5F, 8.0F, 6.0F, 5.0F).texOffs(0, 0).addBox(-4.5F, 6.0F, -3.0F, 9.0F, 6.0F, 6.0F).texOffs(110, 0).addBox(-2.5F, -0.5F, -3.4F, 5.0F, 11.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(30, 0).addBox(-3.5F, -9.0F, -2.5F, 7.0F, 9.0F, 5.0F).texOffs(0, 12).addBox(-4.0F, -1.5F, -3.0F, 8.0F, 2.0F, 6.0F).texOffs(54, 0).addBox(-5.0F, -10.0F, -3.2F, 10.0F, 4.0F, 6.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.2094F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("lappet_right", CubeListBuilder.create().texOffs(122, 0).addBox(-1.0F, 0.0F, -0.6F, 2.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(-2.6F, -7.0F, -3.2F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("lappet_left", CubeListBuilder.create().texOffs(122, 8).addBox(-1.0F, 0.0F, -0.6F, 2.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(2.6F, -7.0F, -3.2F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(86, 0).addBox(-3.0F, -6.5F, -3.0F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.25F)).texOffs(80, 13).addBox(-2.5F, -5.5F, -2.6F, 5.0F, 5.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -9.5F, -0.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("point_right", CubeListBuilder.create().texOffs(100, 13).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.2F)).texOffs(48, 14).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(-1.6F, -6.0F, 1.0F, -0.5236F, 0.0F, 0.4538F));
        p_head.addOrReplaceChild("point_left", CubeListBuilder.create().texOffs(40, 14).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.2F)).texOffs(122, 16).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(1.6F, -6.0F, 1.0F, -0.5236F, 0.0F, -0.4538F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(110, 12).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 5.0F, 3.0F).texOffs(0, 20).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(-4.8F, -8.5F, -0.5F, -1.0123F, 0.0F, 0.3491F));
        PartDefinition p_hand_right = p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(16, 20).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("spark_right", CubeListBuilder.create().texOffs(30, 24).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 2.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(28, 14).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 5.0F, 3.0F).texOffs(8, 20).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(4.8F, -8.5F, -0.5F, -1.0123F, 0.0F, -0.3491F));
        PartDefinition p_hand_left = p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(100, 20).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_left.addOrReplaceChild("spark_left", CubeListBuilder.create().texOffs(38, 25).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 2.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_held = p_body.addOrReplaceChild("held", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -4.5F, -8.5F, -0.2094F, 0.0F, 0.0F));
        PartDefinition p_ring = p_held.addOrReplaceChild("ring", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring.addOrReplaceChild("ring_0", CubeListBuilder.create().texOffs(68, 21).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring.addOrReplaceChild("ring_1", CubeListBuilder.create().texOffs(46, 25).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5236F));
        p_ring.addOrReplaceChild("ring_2", CubeListBuilder.create().texOffs(56, 25).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0472F));
        p_ring.addOrReplaceChild("ring_3", CubeListBuilder.create().texOffs(66, 25).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.5708F));
        p_ring.addOrReplaceChild("ring_4", CubeListBuilder.create().texOffs(76, 25).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0944F));
        p_ring.addOrReplaceChild("ring_5", CubeListBuilder.create().texOffs(0, 26).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.618F));
        p_ring.addOrReplaceChild("ring_6", CubeListBuilder.create().texOffs(10, 26).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.1416F));
        p_ring.addOrReplaceChild("ring_7", CubeListBuilder.create().texOffs(20, 26).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.6652F));
        p_ring.addOrReplaceChild("ring_8", CubeListBuilder.create().texOffs(86, 26).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.1888F));
        p_ring.addOrReplaceChild("ring_9", CubeListBuilder.create().texOffs(96, 26).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.7124F));
        p_ring.addOrReplaceChild("ring_10", CubeListBuilder.create().texOffs(106, 26).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.236F));
        p_ring.addOrReplaceChild("ring_11", CubeListBuilder.create().texOffs(116, 26).addBox(-2.0F, -7.0F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.7596F));
        PartDefinition p_ring_core = p_held.addOrReplaceChild("ring_core", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring_core.addOrReplaceChild("ring_core_0", CubeListBuilder.create().texOffs(40, 29).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring_core.addOrReplaceChild("ring_core_1", CubeListBuilder.create().texOffs(48, 29).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5236F));
        p_ring_core.addOrReplaceChild("ring_core_2", CubeListBuilder.create().texOffs(56, 29).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0472F));
        p_ring_core.addOrReplaceChild("ring_core_3", CubeListBuilder.create().texOffs(64, 29).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.5708F));
        p_ring_core.addOrReplaceChild("ring_core_4", CubeListBuilder.create().texOffs(72, 29).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0944F));
        p_ring_core.addOrReplaceChild("ring_core_5", CubeListBuilder.create().texOffs(0, 30).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.618F));
        p_ring_core.addOrReplaceChild("ring_core_6", CubeListBuilder.create().texOffs(8, 30).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.1416F));
        p_ring_core.addOrReplaceChild("ring_core_7", CubeListBuilder.create().texOffs(16, 30).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.6652F));
        p_ring_core.addOrReplaceChild("ring_core_8", CubeListBuilder.create().texOffs(24, 30).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.1888F));
        p_ring_core.addOrReplaceChild("ring_core_9", CubeListBuilder.create().texOffs(32, 30).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.7124F));
        p_ring_core.addOrReplaceChild("ring_core_10", CubeListBuilder.create().texOffs(80, 30).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.236F));
        p_ring_core.addOrReplaceChild("ring_core_11", CubeListBuilder.create().texOffs(88, 30).addBox(-1.5F, -5.0F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.7596F));
        PartDefinition p_gate = root.addOrReplaceChild("gate", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 7.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_gate_rim = p_gate.addOrReplaceChild("gate_rim", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_gate_rim.addOrReplaceChild("gate_rim_0", CubeListBuilder.create().texOffs(108, 20).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_gate_rim.addOrReplaceChild("gate_rim_1", CubeListBuilder.create().texOffs(40, 21).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5236F));
        p_gate_rim.addOrReplaceChild("gate_rim_2", CubeListBuilder.create().texOffs(54, 21).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0472F));
        p_gate_rim.addOrReplaceChild("gate_rim_3", CubeListBuilder.create().texOffs(24, 22).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.5708F));
        p_gate_rim.addOrReplaceChild("gate_rim_4", CubeListBuilder.create().texOffs(108, 22).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0944F));
        p_gate_rim.addOrReplaceChild("gate_rim_5", CubeListBuilder.create().texOffs(38, 23).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.618F));
        p_gate_rim.addOrReplaceChild("gate_rim_6", CubeListBuilder.create().texOffs(52, 23).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.1416F));
        p_gate_rim.addOrReplaceChild("gate_rim_7", CubeListBuilder.create().texOffs(66, 23).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.6652F));
        p_gate_rim.addOrReplaceChild("gate_rim_8", CubeListBuilder.create().texOffs(80, 23).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.1888F));
        p_gate_rim.addOrReplaceChild("gate_rim_9", CubeListBuilder.create().texOffs(16, 24).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.7124F));
        p_gate_rim.addOrReplaceChild("gate_rim_10", CubeListBuilder.create().texOffs(94, 24).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.236F));
        p_gate_rim.addOrReplaceChild("gate_rim_11", CubeListBuilder.create().texOffs(108, 24).addBox(-3.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.7596F));
        PartDefinition p_gate_core = p_gate.addOrReplaceChild("gate_core", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_gate_core.addOrReplaceChild("gate_core_0", CubeListBuilder.create().texOffs(46, 27).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_gate_core.addOrReplaceChild("gate_core_1", CubeListBuilder.create().texOffs(56, 27).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5236F));
        p_gate_core.addOrReplaceChild("gate_core_2", CubeListBuilder.create().texOffs(66, 27).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0472F));
        p_gate_core.addOrReplaceChild("gate_core_3", CubeListBuilder.create().texOffs(76, 27).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.5708F));
        p_gate_core.addOrReplaceChild("gate_core_4", CubeListBuilder.create().texOffs(0, 28).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0944F));
        p_gate_core.addOrReplaceChild("gate_core_5", CubeListBuilder.create().texOffs(10, 28).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.618F));
        p_gate_core.addOrReplaceChild("gate_core_6", CubeListBuilder.create().texOffs(20, 28).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.1416F));
        p_gate_core.addOrReplaceChild("gate_core_7", CubeListBuilder.create().texOffs(30, 28).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.6652F));
        p_gate_core.addOrReplaceChild("gate_core_8", CubeListBuilder.create().texOffs(86, 28).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.1888F));
        p_gate_core.addOrReplaceChild("gate_core_9", CubeListBuilder.create().texOffs(96, 28).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.7124F));
        p_gate_core.addOrReplaceChild("gate_core_10", CubeListBuilder.create().texOffs(106, 28).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.236F));
        p_gate_core.addOrReplaceChild("gate_core_11", CubeListBuilder.create().texOffs(116, 28).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.7596F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> GATECASTER_ONLY = Map.of();

    public static LayerDefinition hollowmind() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -1.0F, -4.5F, 9.0F, 2.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("lobe_right", CubeListBuilder.create().texOffs(36, 0).addBox(-4.0F, -4.0F, -4.0F, 4.0F, 4.0F, 8.0F).texOffs(44, 12).addBox(-3.0F, -6.0F, -3.0F, 3.0F, 2.0F, 7.0F).texOffs(18, 24).addBox(-2.0F, -7.0F, -1.0F, 2.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(-0.5F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("lobe_left", CubeListBuilder.create().texOffs(0, 11).addBox(0.0F, -4.0F, -4.0F, 4.0F, 4.0F, 8.0F).texOffs(44, 21).addBox(0.0F, -6.0F, -3.0F, 3.0F, 2.0F, 7.0F).texOffs(18, 30).addBox(0.0F, -7.0F, -1.0F, 2.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.5F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(24, 12).addBox(-1.0F, -4.2F, -4.2F, 2.0F, 4.0F, 8.0F).texOffs(0, 23).addBox(-1.0F, -6.2F, -3.2F, 2.0F, 2.0F, 7.0F).texOffs(32, 30).addBox(-1.0F, -7.2F, -1.2F, 2.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("maw", CubeListBuilder.create().texOffs(30, 36).addBox(-2.0F, -1.5F, -1.0F, 4.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -0.5F, -4.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_0 = p_body.addOrReplaceChild("tendril_0", CubeListBuilder.create().texOffs(32, 24).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-4.5F, 1.5F, -1.0F, 0.0873F, -0.8727F, 0.0F));
        PartDefinition p_tip_0 = p_tendril_0.addOrReplaceChild("tip_0", CubeListBuilder.create().texOffs(56, 30).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0698F, -0.2618F, 0.0F));
        p_tip_0.addOrReplaceChild("bud_0", CubeListBuilder.create().texOffs(60, 0).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_1 = p_body.addOrReplaceChild("tendril_1", CubeListBuilder.create().texOffs(46, 30).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-4.5F, 1.5F, 2.5F, 0.0873F, -0.4363F, 0.0F));
        PartDefinition p_tip_1 = p_tendril_1.addOrReplaceChild("tip_1", CubeListBuilder.create().texOffs(10, 32).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0698F, -0.1309F, 0.0F));
        p_tip_1.addOrReplaceChild("bud_1", CubeListBuilder.create().texOffs(60, 2).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_2 = p_body.addOrReplaceChild("tendril_2", CubeListBuilder.create().texOffs(0, 32).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(4.5F, 1.5F, -1.0F, 0.0873F, 0.8727F, 0.0F));
        PartDefinition p_tip_2 = p_tendril_2.addOrReplaceChild("tip_2", CubeListBuilder.create().texOffs(56, 34).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0698F, 0.2618F, 0.0F));
        p_tip_2.addOrReplaceChild("bud_2", CubeListBuilder.create().texOffs(60, 4).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_3 = p_body.addOrReplaceChild("tendril_3", CubeListBuilder.create().texOffs(46, 35).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(4.5F, 1.5F, 2.5F, 0.0873F, 0.4363F, 0.0F));
        PartDefinition p_tip_3 = p_tendril_3.addOrReplaceChild("tip_3", CubeListBuilder.create().texOffs(0, 37).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0698F, 0.1309F, 0.0F));
        p_tip_3.addOrReplaceChild("bud_3", CubeListBuilder.create().texOffs(60, 6).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_4 = p_body.addOrReplaceChild("tendril_4", CubeListBuilder.create().texOffs(10, 36).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-2.0F, 1.5F, 4.5F, 0.0873F, -0.1396F, 0.0F));
        PartDefinition p_tip_4 = p_tendril_4.addOrReplaceChild("tip_4", CubeListBuilder.create().texOffs(56, 38).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0698F, -0.0419F, 0.0F));
        p_tip_4.addOrReplaceChild("bud_4", CubeListBuilder.create().texOffs(60, 8).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_tendril_5 = p_body.addOrReplaceChild("tendril_5", CubeListBuilder.create().texOffs(20, 36).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(2.0F, 1.5F, 4.5F, 0.0873F, 0.1396F, 0.0F));
        PartDefinition p_tip_5 = p_tendril_5.addOrReplaceChild("tip_5", CubeListBuilder.create().texOffs(30, 40).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0698F, 0.0419F, 0.0F));
        p_tip_5.addOrReplaceChild("bud_5", CubeListBuilder.create().texOffs(60, 10).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> HOLLOWMIND_ONLY = Map.of();

    public static LayerDefinition pearlgulp() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -5.0F, -5.0F, 14.0F, 7.0F, 12.0F).texOffs(0, 19).addBox(-5.5F, -8.0F, -3.0F, 11.0F, 3.0F, 9.0F).texOffs(88, 26).addBox(-6.0F, 2.0F, -1.0F, 12.0F, 3.0F, 8.0F).texOffs(74, 41).addBox(-2.5F, -9.0F, -2.0F, 5.0F, 1.0F, 2.0F).texOffs(18, 41).addBox(-3.0F, -9.0F, 1.0F, 6.0F, 1.0F, 3.0F).texOffs(88, 41).addBox(-2.0F, -9.0F, 5.0F, 4.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 19.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 41).addBox(-3.0F, -1.0F, 0.0F, 6.0F, 2.0F, 3.0F).texOffs(98, 21).addBox(-5.0F, -0.5F, 3.0F, 10.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 1.0F, 7.0F, -0.1396F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(52, 0).addBox(-8.0F, -4.0F, -8.0F, 16.0F, 5.0F, 8.0F).texOffs(74, 37).addBox(-8.5F, 0.0F, -9.0F, 17.0F, 1.0F, 3.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, -3.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_jaw = p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(40, 26).addBox(-8.0F, 0.0F, -8.0F, 16.0F, 2.0F, 8.0F).texOffs(40, 36).addBox(-6.0F, -0.5F, -7.0F, 12.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 1.0F, -1.0F, 0.0698F, 0.0F, 0.0F));
        p_jaw.addOrReplaceChild("pouch", CubeListBuilder.create().texOffs(52, 13).addBox(-6.5F, 0.0F, -7.0F, 13.0F, 3.0F, 10.0F).texOffs(98, 16).addBox(-5.5F, 0.0F, -8.0F, 11.0F, 4.0F, 1.0F).texOffs(0, 31).addBox(-5.5F, 3.0F, -7.0F, 11.0F, 1.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 2.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("flipper_right", CubeListBuilder.create().texOffs(40, 19).addBox(-3.0F, -1.0F, -1.5F, 3.0F, 4.0F, 3.0F).texOffs(100, 0).addBox(-4.0F, 3.0F, -3.0F, 5.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(-7.0F, 0.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("flipper_left", CubeListBuilder.create().texOffs(114, 37).addBox(0.0F, -1.0F, -1.5F, 3.0F, 4.0F, 3.0F).texOffs(100, 8).addBox(-1.0F, 3.0F, -3.0F, 5.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(7.0F, 0.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> PEARLGULP_ONLY = Map.of();

    public static LayerDefinition brinkram() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -9.0F, -9.0F, 14.0F, 12.0F, 17.0F).texOffs(90, 26).addBox(-6.0F, -11.0F, -7.0F, 12.0F, 2.0F, 6.0F).texOffs(70, 34).addBox(-5.0F, -11.0F, 0.0F, 10.0F, 2.0F, 5.0F).texOffs(100, 34).addBox(-8.0F, -6.0F, -6.0F, 1.0F, 7.0F, 6.0F).texOffs(114, 34).addBox(7.0F, -5.0F, -1.0F, 1.0F, 6.0F, 6.0F).texOffs(90, 15).addBox(-6.0F, -7.0F, -11.0F, 12.0F, 9.0F, 2.0F).texOffs(48, 29).addBox(-4.0F, -8.0F, -14.0F, 8.0F, 7.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("rump", CubeListBuilder.create().texOffs(62, 0).addBox(-8.0F, -5.0F, -4.0F, 16.0F, 9.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(118, 14).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 11.0F, 0.5236F, 0.0F, 0.0F));
        PartDefinition p_head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(62, 15).addBox(-3.5F, -3.0F, -7.0F, 7.0F, 7.0F, 7.0F).texOffs(48, 50).addBox(-2.5F, 0.0F, -10.0F, 5.0F, 4.0F, 3.0F).texOffs(96, 47).addBox(-3.0F, -5.0F, -6.0F, 6.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 6.0F, -12.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(70, 29).addBox(-3.0F, 0.0F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-3.5F, -1.0F, -2.0F, 0.0F, 0.0F, -0.5236F));
        PartDefinition p_horn_right = p_head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(48, 39).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(-4.0F, -2.5F, -4.0F, -1.0472F, -0.4363F, -0.1745F));
        PartDefinition p_horn_right_b = p_horn_right.addOrReplaceChild("horn_right_b", CubeListBuilder.create().texOffs(64, 41).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(-1.2F, -6.5F, 0.0F, -1.309F, 0.0F, 0.0F));
        PartDefinition p_horn_right_c = p_horn_right_b.addOrReplaceChild("horn_right_c", CubeListBuilder.create().texOffs(80, 41).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(-1.2F, -6.5F, 0.0F, -1.309F, 0.0F, 0.0F));
        PartDefinition p_horn_right_d = p_horn_right_c.addOrReplaceChild("horn_right_d", CubeListBuilder.create().texOffs(64, 52).addBox(-1.5F, -6.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offsetAndRotation(-1.0F, -6.5F, 0.0F, -1.309F, 0.0F, 0.0F));
        p_horn_right_d.addOrReplaceChild("horn_right_tip", CubeListBuilder.create().texOffs(118, 20).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(-0.6F, -5.5F, 0.0F, -0.8727F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(80, 29).addBox(0.0F, 0.0F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(3.5F, -1.0F, -2.0F, 0.0F, 0.0F, 0.5236F));
        PartDefinition p_horn_left = p_head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(0, 43).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(4.0F, -2.5F, -4.0F, -1.0472F, 0.4363F, 0.1745F));
        PartDefinition p_horn_left_b = p_horn_left.addOrReplaceChild("horn_left_b", CubeListBuilder.create().texOffs(16, 43).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(1.2F, -6.5F, 0.0F, -1.309F, 0.0F, 0.0F));
        PartDefinition p_horn_left_c = p_horn_left_b.addOrReplaceChild("horn_left_c", CubeListBuilder.create().texOffs(32, 43).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 7.0F, 4.0F), PartPose.offsetAndRotation(1.2F, -6.5F, 0.0F, -1.309F, 0.0F, 0.0F));
        PartDefinition p_horn_left_d = p_horn_left_c.addOrReplaceChild("horn_left_d", CubeListBuilder.create().texOffs(76, 52).addBox(-1.5F, -6.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offsetAndRotation(1.0F, -6.5F, 0.0F, -1.309F, 0.0F, 0.0F));
        p_horn_left_d.addOrReplaceChild("horn_left_tip", CubeListBuilder.create().texOffs(118, 46).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.6F, -5.5F, 0.0F, -0.8727F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(106, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F), PartPose.offsetAndRotation(-4.5F, 14.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(0, 29).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F), PartPose.offsetAndRotation(4.5F, 14.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(16, 29).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F), PartPose.offsetAndRotation(-4.5F, 14.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(32, 29).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F), PartPose.offsetAndRotation(4.5F, 14.0F, 6.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static final Map<String, String[]> BRINKRAM_ONLY = Map.of();

    public static LayerDefinition facetDoppel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(96, 12).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F).texOffs(18, 21).addBox(-1.5F, 5.0F, -2.6F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-2.5F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(112, 12).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F).texOffs(102, 34).addBox(-1.5F, 5.0F, -2.6F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(2.5F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(74, 20).addBox(-3.5F, -3.0F, -2.0F, 7.0F, 3.0F, 4.0F).texOffs(70, 0).addBox(-5.0F, -12.0F, -3.0F, 10.0F, 4.0F, 6.0F).texOffs(0, 0).addBox(-4.5F, -8.0F, -3.0F, 9.0F, 5.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("facet_chest", CubeListBuilder.create().texOffs(36, 26).addBox(-2.5F, -2.5F, -0.6F, 5.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -7.5F, -3.0F, 0.0F, 0.0F, 0.7854F));
        p_body.addOrReplaceChild("scar_chest", CubeListBuilder.create().texOffs(16, 33).addBox(-2.0F, -2.0F, -0.3F, 4.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -7.5F, -3.0F, 0.0F, 0.0F, 0.7854F));
        p_body.addOrReplaceChild("facet_back", CubeListBuilder.create().texOffs(26, 33).addBox(-2.0F, -2.0F, -0.4F, 4.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -7.0F, 3.0F, 0.0F, 0.0F, 0.7854F));
        p_body.addOrReplaceChild("facet_shoulder_right", CubeListBuilder.create().texOffs(70, 10).addBox(-4.5F, -1.5F, -3.5F, 6.0F, 3.0F, 7.0F), PartPose.offsetAndRotation(-5.0F, -12.0F, 0.0F, 0.0F, 0.0F, -0.384F));
        p_body.addOrReplaceChild("scar_shoulder_right", CubeListBuilder.create().texOffs(0, 21).addBox(-3.5F, -1.0F, -2.5F, 4.0F, 2.0F, 5.0F).texOffs(60, 16).addBox(-2.5F, -1.2F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-5.0F, -12.0F, 0.0F, 0.0F, 0.0F, -0.384F));
        p_body.addOrReplaceChild("facet_shoulder_left", CubeListBuilder.create().texOffs(0, 11).addBox(-1.5F, -1.5F, -3.5F, 6.0F, 3.0F, 7.0F), PartPose.offsetAndRotation(5.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.384F));
        p_body.addOrReplaceChild("scar_shoulder_left", CubeListBuilder.create().texOffs(18, 26).addBox(-0.5F, -1.0F, -2.5F, 4.0F, 2.0F, 5.0F).texOffs(118, 35).addBox(0.5F, -1.2F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(5.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.384F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("head_gem", CubeListBuilder.create().texOffs(102, 0).addBox(-3.0F, -6.5F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));
        p_head.addOrReplaceChild("head_top", CubeListBuilder.create().texOffs(90, 28).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -6.5F, 0.0F, 0.0F, 0.7854F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(30, 0).addBox(-2.5F, -1.5F, -2.5F, 5.0F, 11.0F, 5.0F).texOffs(26, 16).addBox(-3.0F, 4.0F, -3.0F, 6.0F, 4.0F, 6.0F), PartPose.offsetAndRotation(-6.5F, -11.0F, 0.0F, 0.0F, 0.0F, 0.1047F));
        PartDefinition p_hand_right = p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(74, 27).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_fist", CubeListBuilder.create().texOffs(60, 35).addBox(-2.5F, -0.5F, -2.8F, 5.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_sword", CubeListBuilder.create().texOffs(96, 10).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F).texOffs(26, 11).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(48, 36).addBox(-2.0F, -4.0F, -0.5F, 4.0F, 1.0F, 1.0F).texOffs(72, 34).addBox(-1.0F, -11.0F, -0.5F, 2.0F, 7.0F, 1.0F).texOffs(48, 38).addBox(-0.5F, -12.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_axe", CubeListBuilder.create().texOffs(36, 32).addBox(-0.5F, -11.0F, -0.5F, 1.0F, 12.0F, 1.0F).texOffs(78, 34).addBox(-1.0F, -10.8F, -1.8F, 2.0F, 4.0F, 2.0F).texOffs(118, 28).addBox(-1.5F, -12.0F, -2.2F, 3.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_pickaxe", CubeListBuilder.create().texOffs(44, 32).addBox(-0.5F, -10.0F, -0.5F, 1.0F, 11.0F, 1.0F).texOffs(60, 30).addBox(-1.0F, -11.0F, -2.0F, 2.0F, 1.0F, 4.0F).texOffs(52, 38).addBox(-0.5F, -10.0F, -2.0F, 1.0F, 1.0F, 1.0F).texOffs(56, 38).addBox(-0.5F, -10.0F, 1.0F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_shovel", CubeListBuilder.create().texOffs(98, 34).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 9.0F, 1.0F).texOffs(110, 34).addBox(-1.5F, -12.0F, -0.5F, 3.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_hoe", CubeListBuilder.create().texOffs(40, 32).addBox(-0.5F, -11.0F, -0.5F, 1.0F, 12.0F, 1.0F).texOffs(50, 16).addBox(-1.0F, -12.0F, -2.0F, 2.0F, 1.0F, 3.0F).texOffs(22, 38).addBox(-1.0F, -11.0F, -2.0F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_trident", CubeListBuilder.create().texOffs(86, 34).addBox(-0.5F, -9.0F, -0.5F, 1.0F, 10.0F, 1.0F).texOffs(12, 38).addBox(-2.0F, -10.0F, -0.5F, 4.0F, 1.0F, 1.0F).texOffs(12, 35).addBox(-0.5F, -12.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(28, 38).addBox(-2.0F, -12.0F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(32, 38).addBox(1.0F, -12.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_mace", CubeListBuilder.create().texOffs(8, 35).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 7.0F, 1.0F).texOffs(106, 28).addBox(-1.5F, -9.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(60, 38).addBox(-0.5F, -10.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_spear", CubeListBuilder.create().texOffs(90, 34).addBox(-0.5F, -9.0F, -0.5F, 1.0F, 10.0F, 1.0F).texOffs(0, 35).addBox(-1.0F, -12.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_rod", CubeListBuilder.create().texOffs(94, 34).addBox(-0.5F, -9.0F, -0.5F, 1.0F, 10.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hand_right.addOrReplaceChild("copy_chunk", CubeListBuilder.create().texOffs(48, 30).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(50, 0).addBox(-2.5F, -1.5F, -2.5F, 5.0F, 11.0F, 5.0F).texOffs(50, 20).addBox(-3.0F, 4.0F, -3.0F, 6.0F, 4.0F, 6.0F), PartPose.offsetAndRotation(6.5F, -11.0F, 0.0F, 0.0F, 0.0F, -0.1047F));
        p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(0, 28).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> FACET_DOPPEL_ONLY = Map.of();

    public static LayerDefinition shardlingColossus() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_half_left = root.addOrReplaceChild("half_left", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_half_left.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(0, 60).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 5.0F, 6.0F).texOffs(48, 26).addBox(-4.0F, 5.0F, -4.5F, 8.0F, 4.0F, 9.0F), PartPose.offsetAndRotation(4.5F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_torso_left = p_half_left.addOrReplaceChild("torso_left", CubeListBuilder.create().texOffs(28, 54).addBox(0.0F, -4.0F, -4.0F, 8.0F, 4.0F, 8.0F).texOffs(0, 0).addBox(0.0F, -19.0F, -6.0F, 10.0F, 15.0F, 11.0F), PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head_left = p_torso_left.addOrReplaceChild("head_left", CubeListBuilder.create().texOffs(72, 74).addBox(0.0F, -7.0F, -5.0F, 4.0F, 7.0F, 6.0F).texOffs(28, 45).addBox(0.0F, -8.0F, -6.0F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -17.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_head_left.addOrReplaceChild("head_glow_left", CubeListBuilder.create().texOffs(82, 26).addBox(0.0F, -6.0F, -5.3F, 1.0F, 6.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head_left.addOrReplaceChild("head_scar_left", CubeListBuilder.create().texOffs(76, 39).addBox(0.0F, -6.0F, -5.15F, 1.0F, 6.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_shoulder_left = p_torso_left.addOrReplaceChild("shoulder_left", CubeListBuilder.create().texOffs(84, 0).addBox(-4.0F, -3.0F, -5.0F, 8.0F, 6.0F, 10.0F), PartPose.offsetAndRotation(9.0F, -18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_shoulder_left.addOrReplaceChild("spike_left", CubeListBuilder.create().texOffs(0, 26).addBox(-3.0F, -13.0F, -3.0F, 6.0F, 13.0F, 6.0F), PartPose.offsetAndRotation(1.0F, -3.0F, -0.5F, 0.0F, 0.0F, 0.3491F));
        p_shoulder_left.addOrReplaceChild("spike_back_left", CubeListBuilder.create().texOffs(0, 82).addBox(-2.0F, -9.0F, -2.0F, 4.0F, 9.0F, 4.0F), PartPose.offsetAndRotation(-1.5F, -3.0F, 3.0F, -0.4189F, 0.0F, 0.1745F));
        p_shoulder_left.addOrReplaceChild("spike_front_left", CubeListBuilder.create().texOffs(120, 0).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F), PartPose.offsetAndRotation(2.5F, -3.0F, -3.5F, 0.2793F, 0.0F, 0.5585F));
        PartDefinition p_arm_left = p_torso_left.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(76, 45).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 11.0F, 6.0F), PartPose.offsetAndRotation(12.5F, -15.0F, -0.5F, 0.0F, 0.0F, 0.0F));
        p_arm_left.addOrReplaceChild("fist_left", CubeListBuilder.create().texOffs(48, 39).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 8.0F, 7.0F).texOffs(116, 32).addBox(-2.5F, 2.0F, -4.5F, 5.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_torso_left.addOrReplaceChild("glow_left", CubeListBuilder.create().texOffs(120, 14).addBox(0.0F, -19.0F, -6.35F, 1.0F, 15.0F, 0.0F).texOffs(120, 29).addBox(1.0F, -15.0F, -6.35F, 3.0F, 1.0F, 0.0F).texOffs(116, 43).addBox(3.0F, -16.0F, -6.35F, 2.0F, 1.0F, 0.0F).texOffs(120, 43).addBox(1.0F, -10.0F, -6.35F, 2.0F, 1.0F, 0.0F).texOffs(120, 30).addBox(3.0F, -9.0F, -6.35F, 3.0F, 1.0F, 0.0F).texOffs(122, 14).addBox(0.0F, -19.0F, 5.35F, 1.0F, 15.0F, 0.0F).texOffs(92, 62).addBox(0.0F, -19.35F, -6.0F, 1.0F, 0.0F, 11.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_torso_left.addOrReplaceChild("scar_left", CubeListBuilder.create().texOffs(124, 14).addBox(0.0F, -19.0F, -6.18F, 1.0F, 15.0F, 0.0F).texOffs(126, 14).addBox(0.0F, -19.0F, 5.18F, 1.0F, 15.0F, 0.0F).texOffs(24, 66).addBox(0.0F, -19.18F, -6.0F, 1.0F, 0.0F, 11.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_half_right = root.addOrReplaceChild("half_right", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_half_right.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(0, 71).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 5.0F, 6.0F).texOffs(82, 32).addBox(-4.0F, 5.0F, -4.5F, 8.0F, 4.0F, 9.0F), PartPose.offsetAndRotation(-4.5F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_torso_right = p_half_right.addOrReplaceChild("torso_right", CubeListBuilder.create().texOffs(60, 62).addBox(-8.0F, -4.0F, -4.0F, 8.0F, 4.0F, 8.0F).texOffs(42, 0).addBox(-10.0F, -19.0F, -6.0F, 10.0F, 15.0F, 11.0F), PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head_right = p_torso_right.addOrReplaceChild("head_right", CubeListBuilder.create().texOffs(24, 77).addBox(-4.0F, -7.0F, -5.0F, 4.0F, 7.0F, 6.0F).texOffs(108, 84).addBox(-5.0F, -8.0F, -6.0F, 5.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -17.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_head_right.addOrReplaceChild("head_glow_right", CubeListBuilder.create().texOffs(78, 39).addBox(-1.0F, -6.0F, -5.3F, 1.0F, 6.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head_right.addOrReplaceChild("head_scar_right", CubeListBuilder.create().texOffs(80, 39).addBox(-1.0F, -6.0F, -5.15F, 1.0F, 6.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_shoulder_right = p_torso_right.addOrReplaceChild("shoulder_right", CubeListBuilder.create().texOffs(84, 16).addBox(-4.0F, -3.0F, -5.0F, 8.0F, 6.0F, 10.0F), PartPose.offsetAndRotation(-9.0F, -18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_shoulder_right.addOrReplaceChild("spike_right", CubeListBuilder.create().texOffs(24, 26).addBox(-3.0F, -13.0F, -3.0F, 6.0F, 13.0F, 6.0F), PartPose.offsetAndRotation(-1.0F, -3.0F, -0.5F, 0.0F, 0.0F, -0.3491F));
        p_shoulder_right.addOrReplaceChild("spike_back_right", CubeListBuilder.create().texOffs(92, 84).addBox(-2.0F, -9.0F, -2.0F, 4.0F, 9.0F, 4.0F), PartPose.offsetAndRotation(1.5F, -3.0F, 3.0F, -0.4189F, 0.0F, -0.1745F));
        p_shoulder_right.addOrReplaceChild("spike_front_right", CubeListBuilder.create().texOffs(120, 7).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F), PartPose.offsetAndRotation(-2.5F, -3.0F, -3.5F, 0.2793F, 0.0F, -0.5585F));
        PartDefinition p_arm_right = p_torso_right.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(100, 45).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 11.0F, 6.0F), PartPose.offsetAndRotation(-12.5F, -15.0F, -0.5F, 0.0F, 0.0F, 0.0F));
        p_arm_right.addOrReplaceChild("fist_right", CubeListBuilder.create().texOffs(0, 45).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 8.0F, 7.0F).texOffs(116, 37).addBox(-2.5F, 2.0F, -4.5F, 5.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_torso_right.addOrReplaceChild("glow_right", CubeListBuilder.create().texOffs(124, 42).addBox(-1.0F, -19.0F, -6.35F, 1.0F, 15.0F, 0.0F).texOffs(120, 31).addBox(-4.0F, -15.0F, -6.35F, 3.0F, 1.0F, 0.0F).texOffs(116, 44).addBox(-5.0F, -16.0F, -6.35F, 2.0F, 1.0F, 0.0F).texOffs(120, 44).addBox(-3.0F, -10.0F, -6.35F, 2.0F, 1.0F, 0.0F).texOffs(116, 42).addBox(-6.0F, -9.0F, -6.35F, 3.0F, 1.0F, 0.0F).texOffs(126, 42).addBox(-1.0F, -19.0F, 5.35F, 1.0F, 15.0F, 0.0F).texOffs(92, 73).addBox(-1.0F, -19.35F, -6.0F, 1.0F, 0.0F, 11.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_torso_right.addOrReplaceChild("scar_right", CubeListBuilder.create().texOffs(124, 57).addBox(-1.0F, -19.0F, -6.18F, 1.0F, 15.0F, 0.0F).texOffs(126, 57).addBox(-1.0F, -19.0F, 5.18F, 1.0F, 15.0F, 0.0F).texOffs(48, 74).addBox(-1.0F, -19.18F, -6.0F, 1.0F, 0.0F, 11.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static final Map<String, String[]> SHARDLING_COLOSSUS_ONLY = Map.of();

    public static LayerDefinition phasewalker() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_leg_right = root.addOrReplaceChild("leg_right", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_leg_right.addOrReplaceChild("leg_right_s", CubeListBuilder.create().texOffs(66, 34).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_leg_right.addOrReplaceChild("leg_right_g", CubeListBuilder.create().texOffs(78, 34).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_shin_right = p_leg_right.addOrReplaceChild("shin_right", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_shin_right.addOrReplaceChild("shin_right_s", CubeListBuilder.create().texOffs(36, 35).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(32, 51).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.1F)).texOffs(114, 41).addBox(-1.5F, 4.0F, -2.5F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_shin_right.addOrReplaceChild("shin_right_g", CubeListBuilder.create().texOffs(44, 52).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.04F)).texOffs(110, 51).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.06F)).texOffs(100, 42).addBox(-1.5F, 4.0F, -2.5F, 3.0F, 1.0F, 4.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_leg_left = root.addOrReplaceChild("leg_left", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_leg_left.addOrReplaceChild("leg_left_s", CubeListBuilder.create().texOffs(90, 34).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_leg_left.addOrReplaceChild("leg_left_g", CubeListBuilder.create().texOffs(102, 34).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_shin_left = p_leg_left.addOrReplaceChild("shin_left", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_shin_left.addOrReplaceChild("shin_left_s", CubeListBuilder.create().texOffs(52, 52).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(0, 52).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.1F)).texOffs(32, 46).addBox(-1.5F, 4.0F, -2.5F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_shin_left.addOrReplaceChild("shin_left_g", CubeListBuilder.create().texOffs(60, 52).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.04F)).texOffs(12, 52).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.06F)).texOffs(114, 46).addBox(-1.5F, 4.0F, -2.5F, 3.0F, 1.0F, 4.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("body_s", CubeListBuilder.create().texOffs(76, 0).addBox(-5.0F, -1.0F, -3.5F, 10.0F, 2.0F, 7.0F).texOffs(0, 47).addBox(-3.0F, 1.0F, -3.2F, 6.0F, 4.0F, 1.0F).texOffs(46, 47).addBox(-3.0F, 1.0F, 2.2F, 6.0F, 4.0F, 1.0F).texOffs(28, 47).addBox(-5.5F, -15.0F, -0.5F, 1.0F, 14.0F, 1.0F).texOffs(102, 47).addBox(4.5F, -15.0F, -0.5F, 1.0F, 14.0F, 1.0F).texOffs(0, 0).addBox(-6.0F, -17.0F, -3.5F, 12.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("body_g", CubeListBuilder.create().texOffs(0, 9).addBox(-5.0F, -1.0F, -3.5F, 10.0F, 2.0F, 7.0F, new CubeDeformation(-0.04F)).texOffs(14, 47).addBox(-3.0F, 1.0F, -3.2F, 6.0F, 4.0F, 1.0F, new CubeDeformation(-0.04F)).texOffs(60, 47).addBox(-3.0F, 1.0F, 2.2F, 6.0F, 4.0F, 1.0F, new CubeDeformation(-0.04F)).texOffs(98, 47).addBox(-5.5F, -15.0F, -0.5F, 1.0F, 14.0F, 1.0F, new CubeDeformation(-0.04F)).texOffs(106, 47).addBox(4.5F, -15.0F, -0.5F, 1.0F, 14.0F, 1.0F, new CubeDeformation(-0.04F)).texOffs(38, 0).addBox(-6.0F, -17.0F, -3.5F, 12.0F, 2.0F, 7.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_glass = p_body.addOrReplaceChild("glass", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_glass.addOrReplaceChild("glass_s", CubeListBuilder.create().texOffs(34, 9).addBox(-4.0F, -7.0F, -3.0F, 8.0F, 3.0F, 6.0F).texOffs(44, 27).addBox(-3.0F, -4.0F, -2.5F, 6.0F, 2.0F, 5.0F).texOffs(36, 41).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 1.0F, 4.0F).texOffs(68, 55).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(16, 42).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 1.0F, 4.0F).texOffs(88, 27).addBox(-3.0F, 2.0F, -2.5F, 6.0F, 2.0F, 5.0F).texOffs(90, 9).addBox(-4.0F, 4.0F, -3.0F, 8.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_glass.addOrReplaceChild("glass_g", CubeListBuilder.create().texOffs(62, 9).addBox(-4.0F, -7.0F, -3.0F, 8.0F, 3.0F, 6.0F, new CubeDeformation(-0.04F)).texOffs(66, 27).addBox(-3.0F, -4.0F, -2.5F, 6.0F, 2.0F, 5.0F, new CubeDeformation(-0.04F)).texOffs(0, 42).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(-0.04F)).texOffs(76, 55).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.04F)).texOffs(52, 42).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(-0.04F)).texOffs(44, 34).addBox(-3.0F, 2.0F, -2.5F, 6.0F, 2.0F, 5.0F, new CubeDeformation(-0.04F)).texOffs(0, 18).addBox(-4.0F, 4.0F, -3.0F, 8.0F, 3.0F, 6.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_crack = p_body.addOrReplaceChild("crack", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_crack.addOrReplaceChild("crack_s", CubeListBuilder.create().texOffs(28, 18).addBox(-4.0F, -1.0F, -3.0F, 8.0F, 3.0F, 6.0F, new CubeDeformation(0.12F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_crack.addOrReplaceChild("crack_g", CubeListBuilder.create().texOffs(56, 18).addBox(-4.0F, -1.0F, -3.0F, 8.0F, 3.0F, 6.0F, new CubeDeformation(0.08F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_sand_top = p_body.addOrReplaceChild("sand_top", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_sand_top.addOrReplaceChild("sand_top_s", CubeListBuilder.create().texOffs(110, 0).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_sand_top.addOrReplaceChild("sand_top_g", CubeListBuilder.create().texOffs(110, 27).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_sand_low = p_body.addOrReplaceChild("sand_low", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_sand_low.addOrReplaceChild("sand_low_s", CubeListBuilder.create().texOffs(0, 27).addBox(-3.0F, -3.0F, -2.5F, 6.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_sand_low.addOrReplaceChild("sand_low_g", CubeListBuilder.create().texOffs(22, 27).addBox(-3.0F, -3.0F, -2.5F, 6.0F, 3.0F, 5.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_stream = p_body.addOrReplaceChild("stream", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_stream.addOrReplaceChild("stream_s", CubeListBuilder.create().texOffs(122, 51).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_stream.addOrReplaceChild("stream_g", CubeListBuilder.create().texOffs(24, 52).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 9.0F, 1.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -17.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("head_s", CubeListBuilder.create().texOffs(84, 18).addBox(-3.0F, -4.0F, -2.5F, 6.0F, 4.0F, 5.0F).texOffs(84, 55).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(68, 42).addBox(-2.0F, -4.5F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("head_g", CubeListBuilder.create().texOffs(106, 18).addBox(-3.0F, -4.0F, -2.5F, 6.0F, 4.0F, 5.0F, new CubeDeformation(-0.04F)).texOffs(32, 56).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.04F)).texOffs(84, 42).addBox(-2.0F, -4.5F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.0F, -16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_right.addOrReplaceChild("arm_right_s", CubeListBuilder.create().texOffs(118, 7).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_right.addOrReplaceChild("arm_right_g", CubeListBuilder.create().texOffs(74, 47).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_fore_right = p_arm_right.addOrReplaceChild("fore_right", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_fore_right.addOrReplaceChild("fore_right_s", CubeListBuilder.create().texOffs(114, 34).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F).texOffs(110, 56).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(32, 42).addBox(-1.0F, 6.0F, -1.0F, 1.0F, 3.0F, 1.0F).texOffs(118, 15).addBox(0.0F, 6.0F, 0.0F, 1.0F, 2.0F, 1.0F).texOffs(68, 52).addBox(-1.0F, 6.0F, 0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_fore_right.addOrReplaceChild("fore_right_g", CubeListBuilder.create().texOffs(0, 35).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(-0.04F)).texOffs(0, 57).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.04F)).texOffs(110, 47).addBox(-1.0F, 6.0F, -1.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(-0.04F)).texOffs(122, 15).addBox(0.0F, 6.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.04F)).texOffs(118, 56).addBox(-1.0F, 6.0F, 0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create(), PartPose.offsetAndRotation(7.0F, -16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_left.addOrReplaceChild("arm_left_s", CubeListBuilder.create().texOffs(82, 47).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_left.addOrReplaceChild("arm_left_g", CubeListBuilder.create().texOffs(90, 47).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_fore_left = p_arm_left.addOrReplaceChild("fore_left", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_fore_left.addOrReplaceChild("fore_left_s", CubeListBuilder.create().texOffs(12, 35).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F).texOffs(8, 57).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 2.0F, 2.0F).texOffs(92, 55).addBox(-1.0F, 6.0F, -1.0F, 1.0F, 3.0F, 1.0F).texOffs(44, 58).addBox(0.0F, 6.0F, 0.0F, 1.0F, 2.0F, 1.0F).texOffs(52, 58).addBox(-1.0F, 6.0F, 0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_fore_left.addOrReplaceChild("fore_left_g", CubeListBuilder.create().texOffs(24, 35).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(-0.04F)).texOffs(16, 57).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.04F)).texOffs(40, 56).addBox(-1.0F, 6.0F, -1.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(-0.04F)).texOffs(48, 58).addBox(0.0F, 6.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.04F)).texOffs(56, 58).addBox(-1.0F, 6.0F, 0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static final Map<String, String[]> PHASEWALKER_ONLY = Map.of();

    public static LayerDefinition echoFencer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_hips = root.addOrReplaceChild("hips", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hips.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(92, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 13.0F, 3.0F).texOffs(56, 23).addBox(-1.5F, 7.0F, -1.5F, 3.0F, 6.0F, 3.0F, new CubeDeformation(0.25F)).texOffs(108, 40).addBox(-1.5F, 11.0F, -3.5F, 3.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(1.8F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_hips.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(104, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 13.0F, 3.0F).texOffs(68, 23).addBox(-1.5F, 7.0F, -1.5F, 3.0F, 6.0F, 3.0F, new CubeDeformation(0.25F)).texOffs(118, 40).addBox(-1.5F, 11.0F, -3.5F, 3.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-1.8F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = p_hips.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -12.0F, -2.0F, 7.0F, 12.0F, 4.0F).texOffs(0, 16).addBox(-3.5F, -2.0F, -2.0F, 7.0F, 2.0F, 4.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail_back", CubeListBuilder.create().texOffs(56, 13).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 9.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 1.6F, 0.2094F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail_left", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, 0.0F, -1.5F, 1.0F, 8.0F, 4.0F), PartPose.offsetAndRotation(3.7F, 0.0F, -0.4F, 0.0F, 0.0F, -0.1745F));
        p_body.addOrReplaceChild("tail_right", CubeListBuilder.create().texOffs(10, 22).addBox(-0.5F, 0.0F, -1.5F, 1.0F, 8.0F, 4.0F), PartPose.offsetAndRotation(-3.7F, 0.0F, -0.4F, 0.0F, 0.0F, 0.1745F));
        p_body.addOrReplaceChild("collar_back", CubeListBuilder.create().texOffs(92, 16).addBox(-4.0F, -6.0F, 0.0F, 8.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -12.0F, 1.4F, -0.2618F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("collar_left", CubeListBuilder.create().texOffs(108, 23).addBox(-0.5F, -5.0F, -2.0F, 1.0F, 5.0F, 4.0F), PartPose.offsetAndRotation(3.6F, -12.0F, 0.0F, 0.0F, 0.0F, 0.2618F));
        p_body.addOrReplaceChild("collar_right", CubeListBuilder.create().texOffs(118, 23).addBox(-0.5F, -5.0F, -2.0F, 1.0F, 5.0F, 4.0F), PartPose.offsetAndRotation(-3.6F, -12.0F, 0.0F, 0.0F, 0.0F, -0.2618F));
        p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(44, 0).addBox(-3.0F, -7.0F, -3.0F, 6.0F, 7.0F, 6.0F).texOffs(74, 42).addBox(-2.5F, -1.5F, -3.6F, 5.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(64, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F).texOffs(80, 32).addBox(-1.5F, 6.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(32, 39).addBox(-1.0F, 9.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-4.5F, -11.0F, 0.0F, -0.9599F, 0.1396F, 0.0F));
        PartDefinition p_rapier = p_arm_right.addOrReplaceChild("rapier", CubeListBuilder.create().texOffs(80, 38).addBox(-1.5F, 1.0F, -1.5F, 3.0F, 1.0F, 3.0F).texOffs(8, 44).addBox(-2.5F, 0.8F, -0.5F, 5.0F, 1.0F, 1.0F).texOffs(40, 22).addBox(-0.5F, 2.0F, -0.5F, 1.0F, 24.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 10.5F, 0.0F, -0.6981F, 0.0F, 0.0F));
        p_rapier.addOrReplaceChild("pommel", CubeListBuilder.create().texOffs(0, 39).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_rapier.addOrReplaceChild("glint", CubeListBuilder.create().texOffs(108, 44).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.15F)).texOffs(24, 34).addBox(-0.5F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(108, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(4.5F, -11.0F, 0.0F, 2.4435F, 0.0F, -0.6109F));
        p_arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(8, 39).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F).texOffs(0, 34).addBox(-1.5F, 2.0F, -1.5F, 3.0F, 2.0F, 3.0F).texOffs(32, 43).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, -1.2217F, 0.0F, 0.0F));
        PartDefinition p_ghost = root.addOrReplaceChild("ghost", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ghost_hips = p_ghost.addOrReplaceChild("ghost_hips", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost_hips.addOrReplaceChild("ghost_leg_left", CubeListBuilder.create().texOffs(116, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 13.0F, 3.0F, new CubeDeformation(-0.2F)).texOffs(80, 23).addBox(-1.5F, 7.0F, -1.5F, 3.0F, 6.0F, 3.0F, new CubeDeformation(-0.15F)).texOffs(54, 41).addBox(-1.5F, 11.0F, -3.5F, 3.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(1.8F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost_hips.addOrReplaceChild("ghost_leg_right", CubeListBuilder.create().texOffs(44, 13).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 13.0F, 3.0F, new CubeDeformation(-0.2F)).texOffs(92, 23).addBox(-1.5F, 7.0F, -1.5F, 3.0F, 6.0F, 3.0F, new CubeDeformation(-0.15F)).texOffs(64, 42).addBox(-1.5F, 11.0F, -3.5F, 3.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-1.8F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ghost_body = p_ghost_hips.addOrReplaceChild("ghost_body", CubeListBuilder.create().texOffs(22, 0).addBox(-3.5F, -12.0F, -2.0F, 7.0F, 12.0F, 4.0F, new CubeDeformation(-0.2F)).texOffs(22, 16).addBox(-3.5F, -2.0F, -2.0F, 7.0F, 2.0F, 4.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost_body.addOrReplaceChild("ghost_tail_back", CubeListBuilder.create().texOffs(74, 13).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 9.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.6F, 0.2094F, 0.0F, 0.0F));
        p_ghost_body.addOrReplaceChild("ghost_tail_left", CubeListBuilder.create().texOffs(20, 22).addBox(-0.5F, 0.0F, -1.5F, 1.0F, 8.0F, 4.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(3.7F, 0.0F, -0.4F, 0.0F, 0.0F, -0.1745F));
        p_ghost_body.addOrReplaceChild("ghost_tail_right", CubeListBuilder.create().texOffs(30, 22).addBox(-0.5F, 0.0F, -1.5F, 1.0F, 8.0F, 4.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-3.7F, 0.0F, -0.4F, 0.0F, 0.0F, 0.1745F));
        p_ghost_body.addOrReplaceChild("ghost_collar_back", CubeListBuilder.create().texOffs(110, 16).addBox(-4.0F, -6.0F, 0.0F, 8.0F, 6.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, -12.0F, 1.4F, -0.2618F, 0.0F, 0.0F));
        p_ghost_body.addOrReplaceChild("ghost_collar_left", CubeListBuilder.create().texOffs(44, 29).addBox(-0.5F, -5.0F, -2.0F, 1.0F, 5.0F, 4.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(3.6F, -12.0F, 0.0F, 0.0F, 0.0F, 0.2618F));
        p_ghost_body.addOrReplaceChild("ghost_collar_right", CubeListBuilder.create().texOffs(54, 32).addBox(-0.5F, -5.0F, -2.0F, 1.0F, 5.0F, 4.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-3.6F, -12.0F, 0.0F, 0.0F, 0.0F, -0.2618F));
        p_ghost_body.addOrReplaceChild("ghost_head", CubeListBuilder.create().texOffs(68, 0).addBox(-3.0F, -7.0F, -3.0F, 6.0F, 7.0F, 6.0F, new CubeDeformation(-0.2F)).texOffs(86, 42).addBox(-2.5F, -1.5F, -3.6F, 5.0F, 2.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ghost_arm_right = p_ghost_body.addOrReplaceChild("ghost_arm_right", CubeListBuilder.create().texOffs(72, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(-0.2F)).texOffs(92, 32).addBox(-1.5F, 6.0F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.2F)).texOffs(44, 43).addBox(-1.0F, 9.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.5F, -11.0F, 0.0F, -0.9599F, 0.1396F, 0.0F));
        PartDefinition p_ghost_rapier = p_ghost_arm_right.addOrReplaceChild("ghost_rapier", CubeListBuilder.create().texOffs(92, 38).addBox(-1.5F, 1.0F, -1.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(-0.2F)).texOffs(20, 44).addBox(-2.5F, 0.8F, -0.5F, 5.0F, 1.0F, 1.0F, new CubeDeformation(-0.2F)).texOffs(104, 23).addBox(-0.5F, 2.0F, -0.5F, 1.0F, 24.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 10.5F, 0.0F, -0.6981F, 0.0F, 0.0F));
        p_ghost_rapier.addOrReplaceChild("ghost_pommel", CubeListBuilder.create().texOffs(16, 39).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost_rapier.addOrReplaceChild("ghost_glint", CubeListBuilder.create().texOffs(118, 44).addBox(-2.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.15F)).texOffs(44, 38).addBox(-0.5F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ghost_arm_left = p_ghost_body.addOrReplaceChild("ghost_arm_left", CubeListBuilder.create().texOffs(116, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(4.5F, -11.0F, 0.0F, 2.4435F, 0.0F, -0.6109F));
        p_ghost_arm_left.addOrReplaceChild("ghost_forearm_left", CubeListBuilder.create().texOffs(24, 39).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(-0.2F)).texOffs(12, 34).addBox(-1.5F, 2.0F, -1.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(-0.2F)).texOffs(0, 44).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, -1.2217F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> ECHO_FENCER_ONLY = Map.of();

    public static LayerDefinition swellshade() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(100, 12).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 3.0F, 6.0F).texOffs(88, 21).addBox(-2.0F, 3.0F, -2.0F, 4.0F, 2.0F, 4.0F).texOffs(120, 27).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_mass = p_body.addOrReplaceChild("mass", CubeListBuilder.create().texOffs(0, 19).addBox(-5.0F, -4.0F, -4.5F, 10.0F, 4.0F, 9.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_belly = p_mass.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(54, 0).addBox(-6.0F, -10.0F, -5.5F, 12.0F, 10.0F, 11.0F).texOffs(0, 0).addBox(-7.0F, -8.0F, -6.5F, 14.0F, 6.0F, 13.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_hump = p_belly.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(38, 21).addBox(-4.5F, -3.0F, -4.0F, 9.0F, 3.0F, 8.0F), PartPose.offsetAndRotation(0.0F, -10.0F, 0.5F, 0.0F, 0.0F, 0.0F));
        p_hump.addOrReplaceChild("curl_right", CubeListBuilder.create().texOffs(118, 21).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(124, 16).addBox(-1.0F, -6.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-3.0F, -2.5F, 2.0F, -0.4363F, 0.0F, -0.3142F));
        p_hump.addOrReplaceChild("curl_left", CubeListBuilder.create().texOffs(112, 27).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(120, 30).addBox(0.0F, -6.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(3.0F, -2.5F, 2.0F, -0.4363F, 0.0F, 0.3142F));
        PartDefinition p_head = p_hump.addOrReplaceChild("head", CubeListBuilder.create().texOffs(100, 0).addBox(-3.0F, -5.0F, -3.5F, 6.0F, 6.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -2.0F, -3.5F, 0.2094F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(104, 21).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -4.5F, 1.5F, -0.6109F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_hump.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(72, 21).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offsetAndRotation(-8.3F, -1.5F, -1.0F, -0.1396F, 0.0F, 0.1047F));
        p_arm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(88, 27).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F).texOffs(124, 0).addBox(-1.5F, 2.0F, -1.5F, 1.0F, 3.0F, 1.0F).texOffs(124, 4).addBox(0.5F, 2.0F, -1.5F, 1.0F, 3.0F, 1.0F).texOffs(124, 30).addBox(-0.5F, 2.0F, 0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_hump.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(80, 21).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offsetAndRotation(8.3F, -1.5F, -1.0F, -0.1396F, 0.0F, -0.1047F));
        p_arm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(100, 27).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F).texOffs(124, 8).addBox(-1.5F, 2.0F, -1.5F, 1.0F, 3.0F, 1.0F).texOffs(124, 12).addBox(0.5F, 2.0F, -1.5F, 1.0F, 3.0F, 1.0F).texOffs(0, 32).addBox(-0.5F, 2.0F, 0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> SWELLSHADE_ONLY = Map.of();

    public static LayerDefinition chronomite() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(40, 16).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F).texOffs(40, 26).addBox(-1.5F, 2.0F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(5.0F, 21.0F, -4.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(40, 21).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F).texOffs(104, 28).addBox(-1.5F, 2.0F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-5.0F, 21.0F, -4.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(104, 23).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F).texOffs(116, 28).addBox(-1.5F, 2.0F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(5.0F, 21.0F, 4.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(116, 23).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 3.0F).texOffs(52, 29).addBox(-1.5F, 2.0F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-5.0F, 21.0F, 4.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(56, 0).addBox(-6.0F, -1.5F, -6.0F, 12.0F, 3.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 20.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_shell = p_body.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -2.0F, -7.0F, 14.0F, 2.0F, 14.0F).texOffs(56, 15).addBox(-6.0F, -4.0F, -6.0F, 12.0F, 2.0F, 12.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_shell.addOrReplaceChild("dome_top", CubeListBuilder.create().texOffs(0, 16).addBox(-5.0F, -6.0F, -5.0F, 10.0F, 2.0F, 10.0F).texOffs(104, 0).addBox(-3.0F, -7.0F, -3.0F, 6.0F, 1.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_dial = p_shell.addOrReplaceChild("dial", CubeListBuilder.create().texOffs(104, 15).addBox(-3.5F, -3.5F, -1.0F, 7.0F, 7.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -4.5F, -5.2F, -0.7854F, 0.0F, 0.0F));
        p_dial.addOrReplaceChild("hand_hour", CubeListBuilder.create().texOffs(52, 16).addBox(-0.5F, -2.0F, -0.55F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        p_dial.addOrReplaceChild("hand_minute", CubeListBuilder.create().texOffs(124, 7).addBox(-0.5F, -3.0F, -0.85F, 1.0F, 3.0F, 1.0F, new CubeDeformation(-0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F));
        p_shell.addOrReplaceChild("key", CubeListBuilder.create().texOffs(124, 11).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(72, 29).addBox(-3.5F, -5.0F, -0.5F, 3.0F, 3.0F, 1.0F).texOffs(80, 29).addBox(0.5F, -5.0F, -0.5F, 3.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_shell.addOrReplaceChild("broken", CubeListBuilder.create().texOffs(120, 20).addBox(-6.0F, -6.0F, -6.0F, 3.0F, 2.0F, 1.0F).texOffs(36, 30).addBox(1.0F, -7.0F, -6.0F, 2.0F, 3.0F, 1.0F).texOffs(112, 32).addBox(4.0F, -5.0F, -5.0F, 2.0F, 1.0F, 1.0F).texOffs(120, 15).addBox(5.0F, -6.0F, -2.0F, 1.0F, 2.0F, 3.0F).texOffs(88, 29).addBox(5.0F, -7.0F, 3.0F, 1.0F, 3.0F, 2.0F).texOffs(42, 30).addBox(2.0F, -6.0F, 5.0F, 3.0F, 2.0F, 1.0F).texOffs(118, 32).addBox(-3.0F, -5.0F, 5.0F, 2.0F, 1.0F, 1.0F).texOffs(64, 29).addBox(-6.0F, -6.0F, 2.0F, 1.0F, 2.0F, 3.0F).texOffs(94, 29).addBox(-6.0F, -7.0F, -3.0F, 1.0F, 3.0F, 2.0F).texOffs(0, 28).addBox(-4.5F, -5.0F, -4.5F, 9.0F, 1.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(104, 7).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -5.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("mandible_left", CubeListBuilder.create().texOffs(100, 32).addBox(-0.5F, -0.5F, -2.0F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(1.6F, 1.2F, -3.8F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("mandible_right", CubeListBuilder.create().texOffs(106, 32).addBox(-0.5F, -0.5F, -2.0F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-1.6F, 1.2F, -3.8F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> CHRONOMITE_ONLY = Map.of();

    public static LayerDefinition anchorGolem() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(0, 52).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 8.0F, 7.0F).texOffs(84, 72).addBox(-4.0F, 8.0F, -4.5F, 8.0F, 3.0F, 8.0F), PartPose.offsetAndRotation(-4.5F, 13.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(28, 61).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 8.0F, 7.0F).texOffs(0, 76).addBox(-4.0F, 8.0F, -4.5F, 8.0F, 3.0F, 8.0F), PartPose.offsetAndRotation(4.5F, 13.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(60, 16).addBox(-7.0F, -5.0F, -4.0F, 14.0F, 6.0F, 9.0F).texOffs(0, 24).addBox(-7.5F, -2.5F, -4.5F, 15.0F, 2.0F, 10.0F).texOffs(0, 0).addBox(-9.0F, -16.0F, -6.0F, 18.0F, 12.0F, 12.0F).texOffs(60, 0).addBox(-8.0F, -20.0F, -3.0F, 16.0F, 6.0F, 10.0F).texOffs(62, 48).addBox(-6.0F, -22.0F, 0.0F, 5.0F, 2.0F, 5.0F).texOffs(106, 25).addBox(1.5F, -21.5F, 1.5F, 5.0F, 2.0F, 4.0F).texOffs(28, 36).addBox(-9.5F, -15.0F, -2.0F, 1.0F, 6.0F, 6.0F).texOffs(110, 45).addBox(8.5F, -14.0F, -1.0F, 1.0F, 5.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 13.0F, 1.0F, 0.1745F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("drape", CubeListBuilder.create().texOffs(0, 93).addBox(-8.5F, -0.6F, -1.0F, 17.0F, 1.0F, 2.0F).texOffs(42, 36).addBox(-8.6F, 0.4F, -1.0F, 1.0F, 5.0F, 2.0F).texOffs(118, 31).addBox(7.6F, 0.4F, -1.0F, 1.0F, 7.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -20.0F, 6.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(96, 83).addBox(-3.5F, -3.0F, -5.0F, 7.0F, 6.0F, 6.0F).texOffs(0, 67).addBox(-4.0F, -4.2F, -5.5F, 8.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -12.0F, -6.0F, -0.1745F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(0, 36).addBox(-3.5F, -2.0F, -3.5F, 7.0F, 9.0F, 7.0F).texOffs(28, 48).addBox(-4.5F, -4.0F, -4.0F, 9.0F, 5.0F, 8.0F), PartPose.offsetAndRotation(11.0F, -13.0F, 0.0F, -0.1745F, 0.0F, -0.1047F));
        p_arm_left.addOrReplaceChild("fist_left", CubeListBuilder.create().texOffs(50, 31).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 9.0F, 8.0F).texOffs(84, 61).addBox(-4.5F, 5.0F, -4.5F, 9.0F, 2.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(82, 45).addBox(-3.5F, -2.0F, -3.5F, 7.0F, 9.0F, 7.0F).texOffs(82, 31).addBox(-5.0F, -4.5F, -4.5F, 9.0F, 5.0F, 9.0F), PartPose.offsetAndRotation(-11.0F, -13.0F, 0.0F, -0.1745F, 0.0F, 0.1396F));
        PartDefinition p_forearm_right = p_arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(56, 61).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 8.0F, 7.0F).texOffs(64, 83).addBox(-4.0F, 0.8F, -4.0F, 8.0F, 2.0F, 8.0F).texOffs(32, 76).addBox(-4.2F, 4.2F, -4.2F, 8.0F, 3.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_anchor = p_forearm_right.addOrReplaceChild("anchor", CubeListBuilder.create().texOffs(50, 24).addBox(-2.0F, -12.0F, -0.5F, 4.0F, 4.0F, 1.0F).texOffs(0, 87).addBox(-7.0F, -8.0F, -1.5F, 14.0F, 3.0F, 3.0F).texOffs(50, 87).addBox(-9.0F, -8.5F, -2.0F, 2.0F, 4.0F, 4.0F).texOffs(62, 93).addBox(7.0F, -8.5F, -2.0F, 2.0F, 4.0F, 4.0F).texOffs(112, 0).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 14.0F, 3.0F).texOffs(106, 17).addBox(-3.0F, 7.5F, -2.5F, 6.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 5.5F, -4.3F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_fluke_right = p_anchor.addOrReplaceChild("fluke_right", CubeListBuilder.create().texOffs(116, 72).addBox(-1.5F, -6.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offsetAndRotation(-2.5F, 9.0F, 0.0F, 0.0F, 0.0F, -1.0472F));
        PartDefinition p_palm_right = p_fluke_right.addOrReplaceChild("palm_right", CubeListBuilder.create().texOffs(110, 55).addBox(-2.5F, -4.0F, -1.0F, 5.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -5.5F, 0.0F, 0.0F, 0.0F, 0.6109F));
        p_palm_right.addOrReplaceChild("bill_right", CubeListBuilder.create().texOffs(118, 40).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-0.5F, -4.0F, 0.0F, 0.0F, 0.0F, 0.4363F));
        PartDefinition p_fluke_left = p_anchor.addOrReplaceChild("fluke_left", CubeListBuilder.create().texOffs(38, 87).addBox(-1.5F, -6.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offsetAndRotation(2.5F, 9.0F, 0.0F, 0.0F, 0.0F, 1.0472F));
        PartDefinition p_palm_left = p_fluke_left.addOrReplaceChild("palm_left", CubeListBuilder.create().texOffs(64, 76).addBox(-2.5F, -4.0F, -1.0F, 5.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -5.5F, 0.0F, 0.0F, 0.0F, -0.6109F));
        p_palm_left.addOrReplaceChild("bill_left", CubeListBuilder.create().texOffs(42, 43).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.5F, -4.0F, 0.0F, 0.0F, 0.0F, -0.4363F));
        p_anchor.addOrReplaceChild("fluke_right_stub", CubeListBuilder.create().texOffs(62, 55).addBox(-2.0F, -7.5F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offsetAndRotation(-2.5F, 9.0F, 0.0F, 0.0F, 0.0F, -1.0472F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    /** Parts shown only for some variants of anchor_golem. */
    public static final Map<String, String[]> ANCHOR_GOLEM_ONLY = Map.ofEntries(
            Map.entry("drape", new String[] {"rusted_anchor"}),
            Map.entry("fluke_right_stub", new String[] {"rusted_anchor"}),
            Map.entry("palm_right", new String[] {"base"}));

    public static LayerDefinition recurrent() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(30, 15).addBox(-3.5F, 0.0F, -3.0F, 7.0F, 6.0F, 6.0F).texOffs(56, 27).addBox(-4.0F, -1.0F, -3.5F, 8.0F, 2.0F, 7.0F).texOffs(30, 39).addBox(-3.0F, -8.0F, -2.0F, 6.0F, 8.0F, 4.0F).texOffs(86, 28).addBox(-4.5F, -8.0F, -3.0F, 9.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("hem", CubeListBuilder.create().texOffs(90, 0).addBox(-4.0F, 0.0F, -3.5F, 8.0F, 7.0F, 7.0F), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -9.0F, -3.0F, 9.0F, 9.0F, 6.0F).texOffs(0, 54).addBox(-3.5F, -8.0F, -3.3F, 7.0F, 7.0F, 1.0F).texOffs(78, 58).addBox(-1.5F, -11.0F, -1.0F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("hour", CubeListBuilder.create().texOffs(86, 26).addBox(-0.5F, -2.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -4.5F, -3.45F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("minute", CubeListBuilder.create().texOffs(28, 47).addBox(-0.5F, -3.0F, 0.0F, 1.0F, 3.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -4.5F, -3.62F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("crack", CubeListBuilder.create().texOffs(102, 61).addBox(-3.5F, -8.0F, -3.44F, 7.0F, 7.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("halo", CubeListBuilder.create().texOffs(40, 65).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 1.0F, 1.0F).texOffs(26, 67).addBox(-5.0F, 4.0F, 0.0F, 10.0F, 1.0F, 1.0F).texOffs(120, 0).addBox(-5.0F, -4.0F, 0.0F, 1.0F, 8.0F, 1.0F).texOffs(124, 0).addBox(4.0F, -4.0F, 0.0F, 1.0F, 8.0F, 1.0F).texOffs(50, 39).addBox(-1.0F, -7.0F, 0.0F, 2.0F, 2.0F, 1.0F).texOffs(50, 42).addBox(-1.0F, 5.0F, 0.0F, 2.0F, 2.0F, 1.0F).texOffs(22, 47).addBox(-7.0F, -1.0F, 0.0F, 2.0F, 2.0F, 1.0F).texOffs(40, 62).addBox(5.0F, -1.0F, 0.0F, 2.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -4.5F, 3.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(116, 28).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F).texOffs(120, 9).addBox(-1.0F, 8.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(4.5F, -7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(116, 39).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F).texOffs(120, 13).addBox(-1.0F, 8.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-4.5F, -7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ghost1 = root.addOrReplaceChild("ghost1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ghost1_body = p_ghost1.addOrReplaceChild("ghost1_body", CubeListBuilder.create().texOffs(56, 15).addBox(-3.5F, 0.0F, -3.0F, 7.0F, 6.0F, 6.0F, new CubeDeformation(-0.2F)).texOffs(0, 29).addBox(-4.0F, -1.0F, -3.5F, 8.0F, 2.0F, 7.0F, new CubeDeformation(-0.2F)).texOffs(50, 45).addBox(-3.0F, -8.0F, -2.0F, 6.0F, 8.0F, 4.0F, new CubeDeformation(-0.2F)).texOffs(56, 36).addBox(-4.5F, -8.0F, -3.0F, 9.0F, 3.0F, 6.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost1_body.addOrReplaceChild("ghost1_hem", CubeListBuilder.create().texOffs(90, 14).addBox(-4.0F, 0.0F, -3.5F, 8.0F, 7.0F, 7.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost1_body.addOrReplaceChild("ghost1_head", CubeListBuilder.create().texOffs(30, 0).addBox(-4.5F, -9.0F, -3.0F, 9.0F, 9.0F, 6.0F, new CubeDeformation(-0.2F)).texOffs(46, 57).addBox(-3.5F, -8.0F, -3.3F, 7.0F, 7.0F, 1.0F, new CubeDeformation(-0.2F)).texOffs(116, 61).addBox(-1.5F, -11.0F, -1.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost1_body.addOrReplaceChild("ghost1_arm_left", CubeListBuilder.create().texOffs(112, 50).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(-0.2F)).texOffs(82, 15).addBox(-1.0F, 8.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(4.5F, -7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost1_body.addOrReplaceChild("ghost1_arm_right", CubeListBuilder.create().texOffs(22, 51).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(-0.2F)).texOffs(120, 17).addBox(-1.0F, 8.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.5F, -7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ghost2 = root.addOrReplaceChild("ghost2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ghost2_body = p_ghost2.addOrReplaceChild("ghost2_body", CubeListBuilder.create().texOffs(30, 27).addBox(-3.5F, 0.0F, -3.0F, 7.0F, 6.0F, 6.0F, new CubeDeformation(-0.2F)).texOffs(86, 37).addBox(-4.0F, -1.0F, -3.5F, 8.0F, 2.0F, 7.0F, new CubeDeformation(-0.2F)).texOffs(70, 46).addBox(-3.0F, -8.0F, -2.0F, 6.0F, 8.0F, 4.0F, new CubeDeformation(-0.2F)).texOffs(0, 38).addBox(-4.5F, -8.0F, -3.0F, 9.0F, 3.0F, 6.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost2_body.addOrReplaceChild("ghost2_hem", CubeListBuilder.create().texOffs(0, 15).addBox(-4.0F, 0.0F, -3.5F, 8.0F, 7.0F, 7.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost2_body.addOrReplaceChild("ghost2_head", CubeListBuilder.create().texOffs(60, 0).addBox(-4.5F, -9.0F, -3.0F, 9.0F, 9.0F, 6.0F, new CubeDeformation(-0.2F)).texOffs(62, 58).addBox(-3.5F, -8.0F, -3.3F, 7.0F, 7.0F, 1.0F, new CubeDeformation(-0.2F)).texOffs(14, 62).addBox(-1.5F, -11.0F, -1.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost2_body.addOrReplaceChild("ghost2_arm_left", CubeListBuilder.create().texOffs(34, 51).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(-0.2F)).texOffs(82, 19).addBox(-1.0F, 8.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(4.5F, -7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ghost2_body.addOrReplaceChild("ghost2_arm_right", CubeListBuilder.create().texOffs(90, 53).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(-0.2F)).texOffs(120, 21).addBox(-1.0F, 8.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.5F, -7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_anchor = root.addOrReplaceChild("anchor", CubeListBuilder.create().texOffs(90, 46).addBox(-3.0F, -11.0F, -2.5F, 6.0F, 2.0F, 5.0F).texOffs(0, 47).addBox(-3.0F, -3.0F, -2.5F, 6.0F, 2.0F, 5.0F).texOffs(124, 50).addBox(-3.0F, -9.0F, -2.5F, 1.0F, 6.0F, 1.0F).texOffs(102, 53).addBox(-3.0F, -9.0F, 1.5F, 1.0F, 6.0F, 1.0F).texOffs(106, 53).addBox(2.0F, -9.0F, -2.5F, 1.0F, 6.0F, 1.0F).texOffs(16, 54).addBox(2.0F, -9.0F, 1.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_anchor.addOrReplaceChild("anchor_glass", CubeListBuilder.create().texOffs(26, 62).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 2.0F, 3.0F).texOffs(82, 23).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(0, 62).addBox(-2.0F, 0.0F, -1.5F, 4.0F, 3.0F, 3.0F).texOffs(120, 25).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(-0.15F)).texOffs(78, 64).addBox(-2.0F, 1.0F, -1.5F, 4.0F, 2.0F, 3.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_anchor.addOrReplaceChild("anchor_stream", CubeListBuilder.create().texOffs(112, 46).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    /** Parts shown only for some variants of recurrent. */
    public static final Map<String, String[]> RECURRENT_ONLY = Map.ofEntries(
            Map.entry("halo", new String[] {"gilded_recurrent"}));

    public static LayerDefinition mnemovore() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 11).addBox(-7.0F, -1.5F, -7.0F, 14.0F, 3.0F, 8.0F).texOffs(94, 0).addBox(-5.5F, -1.5F, 1.0F, 11.0F, 3.0F, 5.0F).texOffs(82, 16).addBox(-2.5F, -2.5F, -5.0F, 5.0F, 1.0F, 6.0F).texOffs(74, 23).addBox(-4.0F, -1.0F, 6.0F, 8.0F, 2.0F, 2.0F).texOffs(82, 14).addBox(-6.0F, -2.5F, -6.5F, 1.0F, 1.0F, 1.0F).texOffs(86, 14).addBox(-4.0F, -2.5F, -6.5F, 1.0F, 1.0F, 1.0F).texOffs(124, 25).addBox(-2.0F, -2.5F, -6.5F, 1.0F, 1.0F, 1.0F).texOffs(94, 26).addBox(1.0F, -2.5F, -6.5F, 1.0F, 1.0F, 1.0F).texOffs(98, 26).addBox(3.0F, -2.5F, -6.5F, 1.0F, 1.0F, 1.0F).texOffs(120, 26).addBox(5.0F, -2.5F, -6.5F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 20.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_lid = p_body.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -3.0F, -13.0F, 16.0F, 3.0F, 8.0F).texOffs(44, 11).addBox(-6.5F, -3.0F, -5.0F, 13.0F, 3.0F, 6.0F).texOffs(48, 0).addBox(-6.5F, -4.0F, -11.0F, 13.0F, 1.0F, 10.0F).texOffs(94, 8).addBox(-4.5F, -5.0F, -7.0F, 9.0F, 1.0F, 7.0F).texOffs(104, 16).addBox(-8.0F, 0.0F, -12.0F, 2.0F, 1.0F, 5.0F).texOffs(44, 20).addBox(6.0F, 0.0F, -12.0F, 2.0F, 1.0F, 5.0F).texOffs(90, 11).addBox(-6.0F, 0.0F, -13.0F, 1.0F, 3.0F, 1.0F).texOffs(124, 22).addBox(-4.0F, 0.0F, -13.0F, 1.0F, 2.0F, 1.0F).texOffs(12, 27).addBox(-3.0F, 0.0F, -13.0F, 1.0F, 1.0F, 1.0F).texOffs(94, 23).addBox(-1.0F, 0.0F, -13.0F, 1.0F, 2.0F, 1.0F).texOffs(98, 23).addBox(0.0F, 0.0F, -13.0F, 1.0F, 2.0F, 1.0F).texOffs(16, 27).addBox(2.0F, 0.0F, -13.0F, 1.0F, 1.0F, 1.0F).texOffs(70, 25).addBox(3.0F, 0.0F, -13.0F, 1.0F, 2.0F, 1.0F).texOffs(120, 22).addBox(5.0F, 0.0F, -13.0F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -1.5F, 5.0F, 0.0F, 0.0F, 0.0F));
        p_lid.addOrReplaceChild("ridge", CubeListBuilder.create().texOffs(82, 11).addBox(-1.0F, -5.0F, -10.0F, 2.0F, 1.0F, 2.0F).texOffs(118, 16).addBox(-1.0F, -5.0F, -7.5F, 2.0F, 1.0F, 2.0F).texOffs(118, 19).addBox(-1.0F, -6.0F, -5.0F, 2.0F, 1.0F, 2.0F).texOffs(74, 20).addBox(-1.0F, -6.0F, -2.5F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(32, 22).addBox(0.0F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(58, 20).addBox(1.0F, 1.5F, -2.5F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(6.0F, 1.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(58, 25).addBox(-3.0F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(0, 22).addBox(-5.0F, 1.5F, -2.5F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-6.0F, 1.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(44, 26).addBox(0.0F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(16, 22).addBox(1.0F, 1.5F, -2.5F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(6.0F, 1.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(0, 27).addBox(-3.0F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(104, 22).addBox(-5.0F, 1.5F, -2.5F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-6.0F, 1.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of mnemovore. */
    public static final Map<String, String[]> MNEMOVORE_ONLY = Map.ofEntries(
            Map.entry("ridge", new String[] {"starved_mnemovore"}));

    public static LayerDefinition underclinger() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_core = root.addOrReplaceChild("core", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 19.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_front = p_core.addOrReplaceChild("front", CubeListBuilder.create().texOffs(68, 38).addBox(-6.0F, -1.5F, -7.0F, 12.0F, 3.0F, 2.0F).texOffs(30, 20).addBox(-8.0F, -1.5F, -5.0F, 16.0F, 3.0F, 5.0F).texOffs(30, 31).addBox(-9.0F, 0.9F, -6.0F, 18.0F, 0.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 3.3F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_front.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 18.0F), PartPose.offsetAndRotation(-6.0F, -2.0F, -4.5F, 0.0F, -0.1396F, 0.0F));
        PartDefinition p_forearm_right = p_arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(80, 10).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 13.0F), PartPose.offsetAndRotation(1.5F, -0.6F, 17.5F, 0.0F, 3.2463F, 0.0F));
        PartDefinition p_hook_right = p_forearm_right.addOrReplaceChild("hook_right", CubeListBuilder.create().texOffs(110, 10).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 13.0F, -0.6109F, 0.0F, 0.0F));
        p_hook_right.addOrReplaceChild("hook_tip_right", CubeListBuilder.create().texOffs(72, 20).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, -1.2217F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_front.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(40, 0).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 18.0F), PartPose.offsetAndRotation(6.0F, -2.0F, -4.5F, 0.0F, 0.1396F, 0.0F));
        PartDefinition p_forearm_left = p_arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 13.0F), PartPose.offsetAndRotation(-1.5F, -0.6F, 17.5F, 0.0F, 3.0369F, 0.0F));
        PartDefinition p_hook_left = p_forearm_left.addOrReplaceChild("hook_left", CubeListBuilder.create().texOffs(110, 16).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 13.0F, -0.6109F, 0.0F, 0.0F));
        p_hook_left.addOrReplaceChild("hook_tip_left", CubeListBuilder.create().texOffs(114, 31).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, -1.2217F, 0.0F, 0.0F));
        p_core.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(80, 0).addBox(-8.0F, -1.5F, -3.0F, 16.0F, 3.0F, 7.0F).texOffs(72, 25).addBox(-10.0F, 0.9F, -2.5F, 20.0F, 0.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 3.3F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_rear = p_core.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(78, 31).addBox(-7.0F, -1.5F, 0.0F, 14.0F, 3.0F, 4.0F).texOffs(0, 41).addBox(-5.0F, -1.5F, 4.0F, 10.0F, 3.0F, 1.0F).texOffs(0, 37).addBox(-9.0F, 0.9F, 0.5F, 18.0F, 0.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 3.3F, 4.0F, 0.0F, 0.0F, 0.0F));
        p_rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(44, 37).addBox(-4.0F, -1.0F, 0.0F, 8.0F, 2.0F, 4.0F).texOffs(96, 38).addBox(-6.0F, 0.4F, 0.5F, 12.0F, 0.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.5F, 5.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> UNDERCLINGER_ONLY = Map.of();

    public static LayerDefinition wingshear() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(86, 12).addBox(-1.5F, -1.5F, -5.0F, 3.0F, 3.0F, 11.0F).texOffs(0, 42).addBox(-0.5F, -2.3F, -4.0F, 1.0F, 1.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 19.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(114, 12).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F).texOffs(0, 24).addBox(-0.5F, -0.8F, -8.0F, 1.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, -0.4F, -5.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(10, 24).addBox(-0.5F, 0.0F, -3.5F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.2F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(114, 18).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -1.5F, -0.3491F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(88, 26).addBox(-3.5F, 0.0F, 0.0F, 7.0F, 0.0F, 10.0F).texOffs(88, 36).addBox(-3.5F, 0.25F, 0.0F, 7.0F, 0.0F, 10.0F), PartPose.offsetAndRotation(0.0F, -0.5F, 6.0F, -0.1047F, 0.0F, 0.0F));
        PartDefinition p_wing_right = p_body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 0).addBox(-9.0F, 0.0F, -3.0F, 9.0F, 0.0F, 12.0F).texOffs(42, 0).addBox(-9.0F, 0.25F, -3.0F, 9.0F, 0.0F, 12.0F), PartPose.offsetAndRotation(-1.5F, -1.0F, -2.0F, 0.0F, 0.0F, 0.1396F));
        p_wing_right.addOrReplaceChild("wing_right_tip", CubeListBuilder.create().texOffs(42, 12).addBox(-12.0F, 0.0F, 2.0F, 12.0F, 0.0F, 10.0F).texOffs(42, 22).addBox(-12.0F, 0.25F, 2.0F, 12.0F, 0.0F, 10.0F), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_wing_left = p_body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(84, 0).addBox(0.0F, 0.0F, -3.0F, 9.0F, 0.0F, 12.0F).texOffs(0, 12).addBox(0.0F, 0.25F, -3.0F, 9.0F, 0.0F, 12.0F), PartPose.offsetAndRotation(1.5F, -1.0F, -2.0F, 0.0F, 0.0F, -0.1396F));
        p_wing_left.addOrReplaceChild("wing_left_tip", CubeListBuilder.create().texOffs(0, 32).addBox(0.0F, 0.0F, 2.0F, 12.0F, 0.0F, 10.0F).texOffs(44, 32).addBox(0.0F, 0.25F, 2.0F, 12.0F, 0.0F, 10.0F), PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> WINGSHEAR_ONLY = Map.of();

    public static LayerDefinition grandOrrery() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));
        p_body.addOrReplaceChild("core_star", CubeListBuilder.create().texOffs(32, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.7854F));
        p_body.addOrReplaceChild("rib_0", CubeListBuilder.create().texOffs(120, 23).addBox(-1.0F, -10.0F, -6.0F, 2.0F, 10.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.7854F, 0.0F));
        p_body.addOrReplaceChild("rib_1", CubeListBuilder.create().texOffs(114, 31).addBox(-1.0F, -10.0F, -6.0F, 2.0F, 10.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 2.3562F, 0.0F));
        p_body.addOrReplaceChild("rib_2", CubeListBuilder.create().texOffs(120, 34).addBox(-1.0F, -10.0F, -6.0F, 2.0F, 10.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 3.927F, 0.0F));
        p_body.addOrReplaceChild("rib_3", CubeListBuilder.create().texOffs(0, 36).addBox(-1.0F, -10.0F, -6.0F, 2.0F, 10.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 5.4978F, 0.0F));
        p_body.addOrReplaceChild("cap_top", CubeListBuilder.create().texOffs(56, 0).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 2.0F, 6.0F).texOffs(6, 37).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("cap_bottom", CubeListBuilder.create().texOffs(80, 0).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 2.0F, 6.0F).texOffs(120, 0).addBox(-1.0F, 2.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ring_outer = p_body.addOrReplaceChild("ring_outer", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3491F, 0.0F, 0.1396F));
        PartDefinition p_ring_outer_spin = p_ring_outer.addOrReplaceChild("ring_outer_spin", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg0", CubeListBuilder.create().texOffs(56, 8).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg1", CubeListBuilder.create().texOffs(74, 8).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.3927F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg2", CubeListBuilder.create().texOffs(92, 8).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg3", CubeListBuilder.create().texOffs(110, 8).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.1781F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg4", CubeListBuilder.create().texOffs(32, 12).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg5", CubeListBuilder.create().texOffs(50, 13).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.9635F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg6", CubeListBuilder.create().texOffs(68, 13).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 2.3562F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg7", CubeListBuilder.create().texOffs(86, 13).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 2.7489F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg8", CubeListBuilder.create().texOffs(104, 13).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg9", CubeListBuilder.create().texOffs(0, 16).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.5343F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg10", CubeListBuilder.create().texOffs(18, 17).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.927F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg11", CubeListBuilder.create().texOffs(36, 18).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 4.3197F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg12", CubeListBuilder.create().texOffs(54, 18).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 4.7124F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg13", CubeListBuilder.create().texOffs(72, 18).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 5.1051F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg14", CubeListBuilder.create().texOffs(90, 18).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 5.4978F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_seg15", CubeListBuilder.create().texOffs(108, 18).addBox(-3.5F, -1.5F, -16.0F, 7.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 5.8905F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_planet0", CubeListBuilder.create().texOffs(104, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(-15.0F, 0.0F, -0.0F, 0.0F, 0.0F, 0.0F));
        p_ring_outer_spin.addOrReplaceChild("ring_outer_planet1", CubeListBuilder.create().texOffs(0, 30).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(15.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ring_mid = p_body.addOrReplaceChild("ring_mid", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.5236F, 1.117F));
        PartDefinition p_ring_mid_spin = p_ring_mid.addOrReplaceChild("ring_mid_spin", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg0", CubeListBuilder.create().texOffs(0, 22).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg1", CubeListBuilder.create().texOffs(20, 23).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.5236F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg2", CubeListBuilder.create().texOffs(40, 23).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.0472F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg3", CubeListBuilder.create().texOffs(60, 23).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg4", CubeListBuilder.create().texOffs(80, 23).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 2.0944F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg5", CubeListBuilder.create().texOffs(100, 23).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 2.618F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg6", CubeListBuilder.create().texOffs(0, 26).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg7", CubeListBuilder.create().texOffs(20, 27).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.6652F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg8", CubeListBuilder.create().texOffs(40, 27).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 4.1888F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg9", CubeListBuilder.create().texOffs(60, 27).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 4.7124F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg10", CubeListBuilder.create().texOffs(80, 27).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 5.236F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_seg11", CubeListBuilder.create().texOffs(100, 27).addBox(-4.0F, -1.0F, -13.0F, 8.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 5.7596F, 0.0F));
        p_ring_mid_spin.addOrReplaceChild("ring_mid_planet0", CubeListBuilder.create().texOffs(12, 31).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(4.1042F, 0.0F, 11.2763F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_ring_inner = p_body.addOrReplaceChild("ring_inner", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.9076F, -0.6109F, 0.0F));
        PartDefinition p_ring_inner_spin = p_ring_inner.addOrReplaceChild("ring_inner_spin", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg0", CubeListBuilder.create().texOffs(24, 31).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg1", CubeListBuilder.create().texOffs(42, 31).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.6283F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg2", CubeListBuilder.create().texOffs(60, 31).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.2566F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg3", CubeListBuilder.create().texOffs(78, 31).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.885F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg4", CubeListBuilder.create().texOffs(96, 31).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 2.5133F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg5", CubeListBuilder.create().texOffs(24, 35).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg6", CubeListBuilder.create().texOffs(42, 35).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.7699F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg7", CubeListBuilder.create().texOffs(60, 35).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 4.3982F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg8", CubeListBuilder.create().texOffs(78, 35).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 5.0265F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_seg9", CubeListBuilder.create().texOffs(96, 35).addBox(-3.5F, -1.0F, -10.0F, 7.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 5.6549F, 0.0F));
        p_ring_inner_spin.addOrReplaceChild("ring_inner_planet0", CubeListBuilder.create().texOffs(14, 37).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-3.0782F, 0.0F, -8.4572F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> GRAND_ORRERY_ONLY = Map.of();
}
