package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.SlagheartGolem.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Slagheart Golem: a heavy stride, arms up and shaking before the eruption, flung out as it blows, slumped when quenched. */
public class SlagheartGolemModel extends TeemingModel {
    private final ModelPart body, head, armRight, armLeft, legRight, legLeft;

    public SlagheartGolemModel(ModelPart root) {
        super(root, CreatureLayers.SLAGHEART_GOLEM_ONLY);
        body = part("body");
        head = part("head");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        legRight = part("leg_right");
        legLeft = part("leg_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float stride = Mth.cos(pos * 0.5F) * 0.9F * speed;
        legRight.xRot = stride;
        legLeft.xRot = -stride;
        armRight.xRot = -stride * 0.6F;
        armLeft.xRot = stride * 0.6F;
        armRight.zRot = 0.08F;
        armLeft.zRot = -0.08F;
        body.zRot = Mth.cos(pos * 0.5F) * 0.04F * speed;
        head.xRot = s.xRot * DEG * 0.5F;
        head.yRot = s.yRot * DEG;
        body.y += Mth.sin(t * 0.06F) * 0.3F;                       // slow breathing of a hot mass

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.0F);
            float shake = Mth.sin(s.phaseTime * 2.6F) * 0.05F * (0.5F + s.progress);
            armRight.xRot = -2.6F * k;
            armLeft.xRot = -2.6F * k;
            armRight.zRot = 0.35F * k;
            armLeft.zRot = -0.35F * k;
            body.xRot = -0.12F * k;
            body.zRot = shake;
            head.xRot = -0.3F * k;
        } else if (s.phase == Step.ERUPT.ordinal()) {
            armRight.xRot = -1.4F;
            armLeft.xRot = -1.4F;
            armRight.zRot = 1.2F;
            armLeft.zRot = -1.2F;
            body.xRot = -0.2F;
            head.xRot = -0.5F;
        } else if (s.phase == Step.QUENCHED.ordinal()) {
            float k = envelope(s.progress, 0.15F, 0.25F);
            body.xRot = 0.35F * k;
            head.xRot = 0.5F * k;
            armRight.xRot = 0.2F * k;
            armLeft.xRot = 0.2F * k;
            body.y += 1.5F * k;
        }
        if (s.attack > 0.0F) {
            float a = Mth.sin(s.attack * Mth.PI);
            armRight.xRot -= 1.8F * a;
            armLeft.xRot -= 1.8F * a;
        }
    }
}
