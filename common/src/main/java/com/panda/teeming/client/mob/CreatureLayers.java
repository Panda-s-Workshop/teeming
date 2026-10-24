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
        root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(72, 42).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F).texOffs(0, 51).addBox(-3.5F, 8.0F, -4.0F, 7.0F, 3.0F, 7.0F), PartPose.offsetAndRotation(-4.0F, 13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(96, 42).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F).texOffs(28, 51).addBox(-3.5F, 8.0F, -4.0F, 7.0F, 3.0F, 7.0F), PartPose.offsetAndRotation(4.0F, 13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -17.0F, -5.0F, 16.0F, 14.0F, 10.0F).texOffs(52, 12).addBox(-6.0F, -4.0F, -4.0F, 12.0F, 5.0F, 8.0F).texOffs(52, 0).addBox(-9.0F, -19.0F, -4.0F, 18.0F, 4.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("heart", CubeListBuilder.create().texOffs(82, 57).addBox(-3.0F, -3.0F, -1.5F, 6.0F, 6.0F, 2.0F), PartPose.offsetAndRotation(0.0F, -11.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(64, 25).addBox(-3.5F, -6.0F, -4.0F, 7.0F, 6.0F, 7.0F).texOffs(56, 57).addBox(-4.0F, -7.0F, -2.0F, 8.0F, 2.0F, 5.0F), PartPose.offsetAndRotation(0.0F, -17.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_right = p_body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(104, 0).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 15.0F, 6.0F), PartPose.offsetAndRotation(-10.5F, -15.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_right.addOrReplaceChild("fist_right", CubeListBuilder.create().texOffs(0, 24).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 7.0F, 8.0F).texOffs(0, 40).addBox(-4.5F, 4.0F, -4.5F, 9.0F, 2.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_arm_left = p_body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(92, 21).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 15.0F, 6.0F), PartPose.offsetAndRotation(10.5F, -15.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p_arm_left.addOrReplaceChild("fist_left", CubeListBuilder.create().texOffs(32, 25).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 7.0F, 8.0F).texOffs(36, 40).addBox(-4.5F, 4.0F, -4.5F, 9.0F, 2.0F, 9.0F), PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static final Map<String, String[]> SLAGHEART_GOLEM_ONLY = Map.of();

    public static LayerDefinition levimoth() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(44, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 5.0F).texOffs(44, 9).addBox(-2.5F, -2.5F, -3.5F, 5.0F, 5.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 17.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(44, 22).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 3.0F, 2.0F).texOffs(44, 27).addBox(-2.0F, -1.0F, -1.6F, 4.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -0.5F, -3.5F, 0.0F, 0.0F, 0.0F));
        p_head.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(58, 9).addBox(-1.5F, -4.0F, 0.0F, 3.0F, 4.0F, 0.0F), PartPose.offsetAndRotation(-0.6F, -1.4F, -1.2F, -0.6109F, 0.0F, -0.4887F));
        p_head.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(58, 13).addBox(-1.5F, -4.0F, 0.0F, 3.0F, 4.0F, 0.0F), PartPose.offsetAndRotation(0.6F, -1.4F, -1.2F, -0.6109F, 0.0F, 0.4887F));
        PartDefinition p_abdomen = p_body.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(44, 16).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 3.0F).texOffs(54, 22).addBox(-1.0F, -1.0F, 3.0F, 2.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.2F, 2.8F, -0.2618F, 0.0F, 0.0F));
        p_abdomen.addOrReplaceChild("dust_sacs", CubeListBuilder.create().texOffs(56, 17).addBox(-2.4F, -1.0F, -1.0F, 1.0F, 2.0F, 2.0F).texOffs(54, 27).addBox(1.4F, -1.0F, -1.0F, 1.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 1.5F, 0.0F, 0.0F, 0.0F));
        p_body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 28).addBox(-10.0F, 0.0F, -3.0F, 10.0F, 0.0F, 9.0F).texOffs(0, 0).addBox(-8.0F, 0.25F, 0.0F, 8.0F, 0.0F, 14.0F), PartPose.offsetAndRotation(-1.8F, -1.6F, -0.5F, 0.0F, 0.0F, 0.3491F));
        p_body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(0, 37).addBox(0.0F, 0.0F, -3.0F, 10.0F, 0.0F, 9.0F).texOffs(0, 14).addBox(0.0F, 0.25F, 0.0F, 8.0F, 0.0F, 14.0F), PartPose.offsetAndRotation(1.8F, -1.6F, -0.5F, 0.0F, 0.0F, -0.3491F));
        p_body.addOrReplaceChild("leg_right_0", CubeListBuilder.create().texOffs(62, 0).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(-1.5F, 1.8F, -1.5F, 0.0F, 0.0F, 0.8727F));
        p_body.addOrReplaceChild("leg_left_0", CubeListBuilder.create().texOffs(62, 2).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(1.5F, 1.8F, -1.5F, 0.0F, 0.0F, -0.8727F));
        p_body.addOrReplaceChild("leg_right_1", CubeListBuilder.create().texOffs(62, 4).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(-1.5F, 1.8F, 0.5F, 0.0F, 0.0F, 0.8727F));
        p_body.addOrReplaceChild("leg_left_1", CubeListBuilder.create().texOffs(62, 6).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(1.5F, 1.8F, 0.5F, 0.0F, 0.0F, -0.8727F));
        p_body.addOrReplaceChild("leg_right_2", CubeListBuilder.create().texOffs(62, 17).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(-1.5F, 1.8F, 2.5F, 0.0F, 0.0F, 0.8727F));
        p_body.addOrReplaceChild("leg_left_2", CubeListBuilder.create().texOffs(62, 19).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(1.5F, 1.8F, 2.5F, 0.0F, 0.0F, -0.8727F));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static final Map<String, String[]> LEVIMOTH_ONLY = Map.of();
}
