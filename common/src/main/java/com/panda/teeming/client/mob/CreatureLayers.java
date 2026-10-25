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
    public static final ModelLayerLocation DUNEFIN = new ModelLayerLocation(TeemingMod.id("dunefin"), "main");
    public static final ModelLayerLocation RATTLECOIL = new ModelLayerLocation(TeemingMod.id("rattlecoil"), "main");
    public static final ModelLayerLocation SNAPMAW_BLOOM = new ModelLayerLocation(TeemingMod.id("snapmaw_bloom"), "main");
    public static final ModelLayerLocation STORMEEL = new ModelLayerLocation(TeemingMod.id("stormeel"), "main");
    public static final ModelLayerLocation SHELLGUARD_CRAB = new ModelLayerLocation(TeemingMod.id("shellguard_crab"), "main");
    public static final ModelLayerLocation SKREEL = new ModelLayerLocation(TeemingMod.id("skreel"), "main");
    public static final ModelLayerLocation SLAGHEART_GOLEM = new ModelLayerLocation(TeemingMod.id("slagheart_golem"), "main");
    public static final ModelLayerLocation LEVIMOTH = new ModelLayerLocation(TeemingMod.id("levimoth"), "main");

    private CreatureLayers() {}

    public static void register(ClientSinks.Layers out) {
        out.register(THORNHORN, CreatureLayers::thornhorn);
        out.register(SNAGWOOD, CreatureLayers::snagwood);
        out.register(PUFFCAP_SHEPHERD, CreatureLayers::puffcapShepherd);
        out.register(DUNEFIN, CreatureLayers::dunefin);
        out.register(RATTLECOIL, CreatureLayers::rattlecoil);
        out.register(SNAPMAW_BLOOM, CreatureLayers::snapmawBloom);
        out.register(STORMEEL, CreatureLayers::stormeel);
        out.register(SHELLGUARD_CRAB, CreatureLayers::shellguard_crab);
        out.register(SKREEL, CreatureLayers::skreel);
        out.register(SLAGHEART_GOLEM, CreatureLayers::slagheartGolem);
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
