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
    public static final ModelLayerLocation SLAGSPITTER = new ModelLayerLocation(TeemingMod.id("slagspitter"), "main");
    public static final ModelLayerLocation SLAGHEART_GOLEM = new ModelLayerLocation(TeemingMod.id("slagheart_golem"), "main");
    public static final ModelLayerLocation SOOT_MANTA = new ModelLayerLocation(TeemingMod.id("soot_manta"), "main");
    public static final ModelLayerLocation MAGMA_TORTOISE = new ModelLayerLocation(TeemingMod.id("magma_tortoise"), "main");
    public static final ModelLayerLocation GLEAMTRAP = new ModelLayerLocation(TeemingMod.id("gleamtrap"), "main");
    public static final ModelLayerLocation LAVALEAPER = new ModelLayerLocation(TeemingMod.id("lavaleaper"), "main");
    public static final ModelLayerLocation PYRE_HERALD = new ModelLayerLocation(TeemingMod.id("pyre_herald"), "main");
    public static final ModelLayerLocation FORTRESS_JAILER = new ModelLayerLocation(TeemingMod.id("fortress_jailer"), "main");
    public static final ModelLayerLocation FLAMEWRIGHT = new ModelLayerLocation(TeemingMod.id("flamewright"), "main");
    public static final ModelLayerLocation LEVIMOTH = new ModelLayerLocation(TeemingMod.id("levimoth"), "main");

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
        out.register(SLAGSPITTER, CreatureLayers::slagspitter);
        out.register(SLAGHEART_GOLEM, CreatureLayers::slagheartGolem);
        out.register(SOOT_MANTA, CreatureLayers::soot_manta);
        out.register(MAGMA_TORTOISE, CreatureLayers::magmaTortoise);
        out.register(GLEAMTRAP, CreatureLayers::gleamtrap);
        out.register(LAVALEAPER, CreatureLayers::lavaleaper);
        out.register(PYRE_HERALD, CreatureLayers::pyreHerald);
        out.register(FORTRESS_JAILER, CreatureLayers::fortressJailer);
        out.register(FLAMEWRIGHT, CreatureLayers::flamewright);
        out.register(LEVIMOTH, CreatureLayers::levimoth);
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

    public static LayerDefinition slagspitter() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -9.0F, -4.0F, 14.0F, 9.0F, 11.0F).texOffs(88, 0).addBox(-5.5F, -10.5F, -2.5F, 11.0F, 2.0F, 8.0F).texOffs(64, 20).addBox(-4.5F, -7.0F, 7.0F, 9.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(50, 0).addBox(-6.5F, -4.5F, -6.0F, 13.0F, 5.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -4.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("eye_right", CubeListBuilder.create().texOffs(116, 18).addBox(-1.5F, -2.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(-4.0F, -4.5F, -3.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("eye_left", CubeListBuilder.create().texOffs(116, 24).addBox(-1.5F, -2.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(4.0F, -4.5F, -3.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(92, 10).addBox(-6.0F, 0.1F, -5.5F, 12.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -4.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(50, 11).addBox(-7.5F, -1.5F, -3.0F, 15.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -0.3F, -6.8F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("clinker", CubeListBuilder.create().texOffs(24, 30).addBox(-4.0F, -0.8F, -1.5F, 3.0F, 1.0F, 3.0F).texOffs(12, 29).addBox(1.5F, -1.8F, 0.5F, 3.0F, 2.0F, 3.0F).texOffs(44, 30).addBox(-1.5F, -0.8F, 3.0F, 2.0F, 1.0F, 2.0F).texOffs(52, 30).addBox(3.5F, -0.8F, -2.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("columns", CubeListBuilder.create().texOffs(84, 20).addBox(-3.5F, -3.8F, -1.0F, 2.0F, 4.0F, 2.0F).texOffs(108, 27).addBox(-0.5F, -2.8F, 1.0F, 2.0F, 3.0F, 2.0F).texOffs(36, 30).addBox(2.5F, -1.8F, -0.5F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(96, 27).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(64, 27).addBox(-2.5F, 2.0F, -2.5F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-6.5F, 21.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(24, 20).addBox(-2.5F, -2.0F, -2.5F, 4.0F, 4.0F, 6.0F).texOffs(92, 18).addBox(-3.0F, 2.0F, -4.5F, 5.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(-6.0F, 20.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(0, 29).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(80, 27).addBox(-1.5F, 2.0F, -2.5F, 4.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(6.5F, 21.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(44, 20).addBox(-1.5F, -2.0F, -2.5F, 4.0F, 4.0F, 6.0F).texOffs(0, 20).addBox(-2.0F, 2.0F, -4.5F, 5.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(6.0F, 20.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    /** Parts shown only for some variants of slagspitter. */
    public static final Map<String, String[]> SLAGSPITTER_ONLY = Map.ofEntries(
            Map.entry("clinker", new String[] {"base"}),
            Map.entry("columns", new String[] {"basalt_spitter"}));

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

    public static LayerDefinition gleamtrap() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 8.0F, 16.0F).texOffs(100, 24).addBox(-7.0F, -11.0F, -7.0F, 14.0F, 3.0F, 0.0F).texOffs(52, 24).addBox(-7.0F, -11.0F, -6.5F, 0.0F, 3.0F, 12.0F).texOffs(76, 24).addBox(7.0F, -11.0F, -6.5F, 0.0F, 3.0F, 12.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(64, 0).addBox(-8.0F, -8.0F, -16.0F, 16.0F, 8.0F, 16.0F).texOffs(100, 27).addBox(-7.0F, 0.0F, -15.5F, 14.0F, 3.0F, 0.0F).texOffs(0, 24).addBox(-7.5F, 0.0F, -15.0F, 0.0F, 3.0F, 13.0F).texOffs(26, 24).addBox(7.5F, 0.0F, -15.0F, 0.0F, 3.0F, 13.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    public static final Map<String, String[]> GLEAMTRAP_ONLY = Map.of();

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
}
