package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Thornhorn.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Thornhorn: a grazer's walk, the head-down paw of the tell, a gallop for the charge, a dazed wobble when stunned. */
public class ThornhornModel extends TeemingModel {
    private final ModelPart body, head, tail, frontRight, frontLeft, backRight, backLeft;

    public ThornhornModel(ModelPart root) {
        super(root, CreatureLayers.THORNHORN_ONLY);
        body = part("body");
        head = part("head");
        tail = part("tail");
        frontRight = part("leg_front_right");
        frontLeft = part("leg_front_left");
        backRight = part("leg_back_right");
        backLeft = part("leg_back_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float swing = Mth.cos(pos * 0.6662F) * 1.2F * speed;
        frontRight.xRot = swing;
        backLeft.xRot = swing;
        frontLeft.xRot = -swing;
        backRight.xRot = -swing;
        head.xRot = s.xRot * DEG + Mth.cos(pos * 0.6662F) * 0.05F * speed;
        head.yRot = s.yRot * DEG;
        tail.zRot = Mth.sin(t * 0.08F) * 0.15F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 3.0F);                       // horns down within the first third
            head.xRot = 28.0F * DEG * k;
            head.yRot = 0.0F;
            head.y += 1.5F * k;
            body.xRot = 4.0F * DEG * k;
            frontRight.xRot = -0.5F + Mth.sin(s.phaseTime * 1.1F) * 0.55F;   // pawing
            frontLeft.xRot = 0.0F;
            tail.xRot = 0.5F * k;
        } else if (s.phase == Step.CHARGE.ordinal()) {
            float gallop = Mth.cos(s.phaseTime * 1.4F) * 1.1F;
            frontRight.xRot = gallop;
            frontLeft.xRot = gallop * 0.8F;
            backRight.xRot = -gallop;
            backLeft.xRot = -gallop * 0.8F;
            head.xRot = 32.0F * DEG;
            head.yRot = 0.0F;
            head.y += 1.5F;
            body.xRot = 6.0F * DEG + Mth.sin(s.phaseTime * 1.4F) * 0.04F;
            tail.xRot = 0.9F;
        } else if (s.phase == Step.RECOVER.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            head.xRot = 28.0F * DEG * k;
            head.y += 1.5F * k;
        } else if (s.phase == Step.STUNNED.ordinal()) {
            head.xRot = 18.0F * DEG;
            head.yRot = Mth.sin(s.phaseTime * 0.45F) * 0.35F;
            head.zRot = Mth.sin(s.phaseTime * 0.3F) * 0.2F;
            body.zRot = Mth.sin(s.phaseTime * 0.3F) * 0.05F;
            frontRight.xRot = 0.15F;
            frontLeft.xRot = -0.1F;
        }
        if (s.attack > 0.0F) head.xRot -= Mth.sin(s.attack * Mth.PI) * 40.0F * DEG;   // a horn toss on a melee hit
    }
}
