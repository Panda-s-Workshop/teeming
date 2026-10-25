package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Glimmerless.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Glimmerless: a stooped, creeping walk, the long arms swinging low with the claws trailing near the ground and the
 * head sliding forward and back. The tell rears it up with both arms drawn high and back, claws spread, its jaw open
 * in a hiss; the rake whips both arms down and forward through the space in front of it as it lunges; the flinch
 * throws it back with both long arms crossed up over its face, shaking.
 */
public class GlimmerlessModel extends TeemingModel {
    /** Arms drawn up and back for the tell. */
    private static final float DRAWN = -150.0F * DEG, SPREAD = 28.0F * DEG;
    /** Arms whipped down and forward in the rake. */
    private static final float RAKED = -95.0F * DEG;

    private final ModelPart body, neck, head, jaw, armRight, armLeft, foreRight, foreLeft, handRight, handLeft;
    private final ModelPart legRight, legLeft, shinRight, shinLeft;

    public GlimmerlessModel(ModelPart root) {
        super(root, CreatureLayers.GLIMMERLESS_ONLY);
        body = part("body");
        neck = part("neck");
        head = part("head");
        jaw = part("jaw");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        foreRight = part("forearm_right");
        foreLeft = part("forearm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        shinRight = part("shin_right");
        shinLeft = part("shin_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float step = Mth.cos(pos * 0.6662F);
        float lift = Mth.sin(pos * 0.6662F);
        legRight.xRot += step * 0.7F * speed;
        legLeft.xRot -= step * 0.7F * speed;
        shinRight.xRot += Math.max(0.0F, lift) * 0.5F * speed;                  // the knee folds as the foot comes up
        shinLeft.xRot += Math.max(0.0F, -lift) * 0.5F * speed;
        body.xRot += Mth.sin(pos * 1.3324F) * 0.04F * speed + Mth.sin(t * 0.05F) * 0.015F;
        body.zRot += step * 0.05F * speed;
        armRight.xRot -= step * 0.45F * speed;                                   // long arms swing low, opposite the legs
        armLeft.xRot += step * 0.45F * speed;
        foreRight.xRot -= Math.max(0.0F, step) * 0.3F * speed;
        foreLeft.xRot -= Math.max(0.0F, -step) * 0.3F * speed;
        float sway = Mth.sin(t * 0.045F) * 0.04F;
        armRight.zRot += sway;
        armLeft.zRot += sway;
        neck.xRot += Mth.sin(pos * 0.6662F * 2.0F) * 0.06F * speed;              // the head slides forward and back
        head.yRot += s.yRot * DEG * 0.6F;
        head.xRot += s.xRot * DEG * 0.5F;
        jaw.xRot += Mth.sin(t * 0.07F) * 0.04F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = envelope(s.progress, 0.35F, 0.0F);
            float shake = Mth.sin(s.phaseTime * 2.6F) * 0.05F * k;
            body.xRot -= 0.35F * k;                                              // rears up
            armRight.xRot = Mth.lerp(k, armRight.xRot, DRAWN + shake);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, DRAWN - shake);
            armRight.zRot = Mth.lerp(k, armRight.zRot, -SPREAD);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, SPREAD);
            foreRight.xRot -= 0.5F * k;                                          // claws hooked forward over its head
            foreLeft.xRot -= 0.5F * k;
            handRight.xRot -= 0.4F * k;
            handLeft.xRot -= 0.4F * k;
            neck.xRot -= 0.25F * k;
            head.xRot -= 0.2F * k;
            jaw.xRot += 0.7F * k;                                                // the hiss
        } else if (s.phase == Step.STRIKE.ordinal()) {
            float k = envelope(s.progress, 0.0F, 0.6F);
            body.xRot += 0.3F * k;                                               // the lunge
            armRight.xRot = Mth.lerp(k, armRight.xRot, RAKED);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, RAKED);
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.25F);                   // the claws cross in front
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -0.25F);
            foreRight.xRot += 0.2F * k;
            foreLeft.xRot += 0.2F * k;
            neck.xRot += 0.15F * k;
            jaw.xRot += 0.4F * k;
        } else if (s.phase == Step.RECOIL.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.35F);
            float shake = Mth.sin(s.phaseTime * 1.9F) * 0.06F * k;
            body.xRot -= 0.5F * k;                                               // thrown back upright
            body.zRot += shake;
            armRight.xRot = Mth.lerp(k, armRight.xRot, -120.0F * DEG);           // both arms up across its face
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -120.0F * DEG);
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.55F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -0.55F);
            foreRight.xRot -= 1.1F * k;
            foreLeft.xRot -= 1.1F * k;
            neck.xRot += 0.35F * k;                                              // the head ducks behind them
            head.xRot += 0.3F * k + shake;
            jaw.xRot += 0.5F * k;
        }
    }
}
