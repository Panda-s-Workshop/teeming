package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.HollowMourner.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Hollow Mourner: a slow, swaying walk, its robe swinging over wrapped feet. The chant raises both arms high and wide
 * with the head thrown back, hands trembling; the raising sweeps both arms forward and down at the grave as it leans
 * in; a stagger throws it back with its arms flung out and its hood knocked aside.
 */
public class HollowMournerModel extends TeemingModel {
    /** Arms raised for the chant: up past the head, and spread outward. */
    private static final float RAISED = -152.0F * DEG, SPREAD = 22.0F * DEG;
    /** Arms thrust at the grave as the dead rise: forward and a little down. */
    private static final float THRUST = -70.0F * DEG;

    private final ModelPart body, skirt, head, peak, veil, armRight, armLeft, handRight, handLeft, footRight, footLeft;

    public HollowMournerModel(ModelPart root) {
        super(root, CreatureLayers.HOLLOW_MOURNER_ONLY);
        body = part("body");
        skirt = part("skirt");
        head = part("head");
        peak = part("peak");
        veil = part("veil");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
        footRight = part("foot_right");
        footLeft = part("foot_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float step = Mth.cos(pos * 0.6662F);
        footRight.xRot += step * 0.9F * speed;
        footLeft.xRot -= step * 0.9F * speed;
        footRight.z -= step * 1.2F * speed;
        footLeft.z += step * 1.2F * speed;
        skirt.xRot += Mth.sin(pos * 0.6662F) * 0.06F * speed + Mth.sin(t * 0.06F) * 0.02F;
        skirt.zRot += step * 0.05F * speed;
        body.zRot += step * 0.04F * speed;                                      // a heavy, swaying gait
        body.xRot += 0.08F * speed;
        float sway = Mth.sin(t * 0.05F) * 0.05F;
        armRight.xRot += sway + step * 0.35F * speed;
        armLeft.xRot += -sway - step * 0.35F * speed;
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        veil.xRot += 0.1F * speed + Mth.sin(t * 0.07F + 0.5F) * 0.04F;
        peak.xRot += Mth.sin(t * 0.05F + 1.0F) * 0.03F;

        if (s.phase == Step.CHANT.ordinal()) {
            float k = envelope(s.progress, 0.2F, 0.05F);
            float wave = Mth.sin(s.phaseTime * 0.25F) * 0.08F * k;              // the arms rock with the chant
            float tremble = Mth.sin(s.phaseTime * 2.3F) * 0.04F * k;
            armRight.xRot = Mth.lerp(k, armRight.xRot, RAISED + wave);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, RAISED - wave);
            armRight.zRot = Mth.lerp(k, armRight.zRot, -SPREAD);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, SPREAD);
            handRight.xRot += tremble - 0.4F * k;                               // palms turned up to the sky
            handLeft.xRot -= tremble + 0.4F * k;
            head.xRot = Mth.lerp(k, head.xRot, -0.22F);                         // face lifted
            body.xRot -= 0.1F * k;
            peak.xRot += 0.3F * k;                                              // the cowl stays upright, tall above it
            veil.xRot += 0.12F * k + Mth.sin(s.phaseTime * 0.3F) * 0.05F * k;
            skirt.xRot += Mth.sin(s.phaseTime * 0.2F) * 0.03F * k;
        } else if (s.phase == Step.RAISE.ordinal()) {
            float k = envelope(s.progress, 0.25F, 0.3F);
            armRight.xRot = Mth.lerp(k, armRight.xRot, THRUST);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, THRUST);
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.12F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -0.12F);
            handRight.xRot += 0.6F * k;                                         // palms down over the grave
            handLeft.xRot += 0.6F * k;
            body.xRot += 0.28F * k;                                             // leaning in
            head.xRot = Mth.lerp(k, head.xRot, 0.35F);
            veil.xRot -= 0.2F * k;
        } else if (s.phase == Step.STAGGER.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            float reel = Mth.sin(s.phaseTime * 0.9F) * k;
            body.xRot -= 0.3F * k;                                              // thrown back
            body.zRot += 0.12F * reel;
            head.zRot += 0.35F * k;                                             // hood knocked aside
            head.xRot -= 0.25F * k;
            armRight.xRot += 0.5F * k;                                          // arms flung back and wide
            armLeft.xRot += 0.4F * k;
            armRight.zRot += 0.8F * k;
            armLeft.zRot -= 0.8F * k;
            skirt.xRot += 0.15F * k;
            veil.xRot += 0.3F * k;
        }
        if (s.attack > 0.0F) {                                                  // a clawing swipe, right hand first
            float a = Mth.sin(s.attack * Mth.PI);
            armRight.xRot -= 1.9F * a;
            armRight.yRot += 0.4F * a;
            armLeft.xRot -= 1.0F * a;
            body.yRot -= 0.2F * a;
        }
    }
}
