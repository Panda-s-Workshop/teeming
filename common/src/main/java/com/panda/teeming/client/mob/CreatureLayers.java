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
    public static final ModelLayerLocation SLAGHEART_GOLEM = new ModelLayerLocation(TeemingMod.id("slagheart_golem"), "main");
    public static final ModelLayerLocation LEVIMOTH = new ModelLayerLocation(TeemingMod.id("levimoth"), "main");

    private CreatureLayers() {}

    public static void register(ClientSinks.Layers out) {
        out.register(THORNHORN, CreatureLayers::thornhorn);
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
        p_body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, 0.0F, -3.0F, 12.0F, 0.0F, 10.0F).texOffs(88, 0).addBox(-9.0F, 0.25F, 3.0F, 9.0F, 0.0F, 8.0F), PartPose.offsetAndRotation(-2.3F, -2.2F, -0.5F, 0.1745F, 0.0F, 0.3491F));
        p_body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(44, 0).addBox(0.0F, 0.0F, -3.0F, 12.0F, 0.0F, 10.0F).texOffs(88, 8).addBox(0.0F, 0.25F, 3.0F, 9.0F, 0.0F, 8.0F), PartPose.offsetAndRotation(2.3F, -2.2F, -0.5F, 0.1745F, 0.0F, -0.3491F));
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
