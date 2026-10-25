package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.MireGulper.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Mire Gulper: a toad's crawl and a breathing throat sac at rest; in the tell it crouches with its mouth just parted
 * and its throat pumping; swallowing, it rears up with its mouth wide open round its victim and chews every 10 ticks;
 * stunned, it sits back with its jaw hanging slack, tongue lolling and the throat sac turned to the front; recovering,
 * it shakes itself and shuts its mouth.
 */
public class MireGulperModel extends TeemingModel {
    private final ModelPart body, head, jaw, tongue, throat, eyeRight, eyeLeft, armRight, armLeft, legRight, legLeft;

    public MireGulperModel(ModelPart root) {
        super(root, CreatureLayers.MIRE_GULPER_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        tongue = part("tongue");
        throat = part("throat");
        eyeRight = part("eye_right");
        eyeLeft = part("eye_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        legRight = part("leg_right");
        legLeft = part("leg_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // the crawl: front legs sweep forward and back in turn, hind legs push, the body bobs
        float step = pos * 0.8F;
        armRight.yRot = Mth.cos(step) * 0.6F * speed;
        armLeft.yRot = Mth.cos(step) * 0.6F * speed;
        armRight.zRot = Math.max(0.0F, Mth.sin(step)) * 0.4F * speed;
        armLeft.zRot = -Math.max(0.0F, -Mth.sin(step)) * 0.4F * speed;
        legRight.xRot = Mth.cos(step + Mth.PI) * 0.35F * speed;
        legLeft.xRot = Mth.cos(step) * 0.35F * speed;
        body.y -= Math.abs(Mth.sin(step)) * 0.8F * speed;
        // breathing: the throat sac swells and the eyes look about
        float breath = 0.5F + 0.5F * Mth.sin(t * 0.12F);
        throat.yScale = 1.0F + 0.15F * breath;
        throat.xScale = 1.0F + 0.05F * breath;
        eyeRight.yRot = Mth.sin(t * 0.05F) * 0.3F;
        eyeLeft.yRot = Mth.sin(t * 0.05F + 1.3F) * 0.3F;
        jaw.xRot = 0.02F + 0.04F * (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.04F)), 12);

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 5.0F);
            float pump = Math.abs(Mth.sin(s.phaseTime * 0.9F));
            body.y += 1.2F * k;                                            // crouched low, ready to spring
            head.xRot = -0.08F * k;
            jaw.xRot = (0.14F + 0.05F * Mth.sin(s.phaseTime * 1.7F)) * k;  // mouth just parted, bubbles leaking out
            throat.yScale = 1.0F + 0.45F * pump * k;
            throat.xScale = 1.0F + 0.15F * pump * k;
            throat.zScale = 1.0F + 0.15F * pump * k;
            armRight.yRot = 0.35F * k;
            armLeft.yRot = -0.35F * k;
            legRight.xRot = -0.25F * k;
            legLeft.xRot = -0.25F * k;
            eyeRight.xRot = -0.35F * k;                                    // eyes rolled up at its prey
            eyeLeft.xRot = -0.35F * k;
        } else if (s.phase == Step.SWALLOW.ordinal()) {
            float k = smooth(s.phaseTime / 4.0F);                         // the lunge: up and open in 4 ticks
            float c = s.phaseTime % 10.0F;                                 // a chew every 10 ticks
            float chew = c < 4.0F ? Mth.sin(c / 4.0F * Mth.PI) : 0.0F;
            body.xRot = -0.2F * k;
            body.y -= 1.5F * k;
            head.xRot = (-0.8F + 0.3F * chew) * k;                         // the mouth wide open round its victim
            jaw.xRot = (0.45F - 0.15F * chew) * k;
            throat.yScale = 1.0F + 0.4F * chew;
            throat.xScale = 1.0F + 0.15F * chew;
            armRight.zRot = 0.35F * k;                                     // braced
            armLeft.zRot = -0.35F * k;
            armRight.yRot = 0.3F * k;
            armLeft.yRot = -0.3F * k;
            legRight.xRot = -0.3F * k;
            legLeft.xRot = -0.3F * k;
            eyeRight.xRot = -0.2F * k;
            eyeLeft.xRot = -0.2F * k;
        } else if (s.phase == Step.STUNNED.ordinal()) {
            float k = smooth(s.phaseTime / 4.0F);
            float sway = Mth.sin(s.phaseTime * 0.3F);
            body.xRot = -0.5F * k;                                         // sat back: the throat sac turned to the front
            body.zRot = 0.08F * sway * k;
            head.xRot = -0.2F * k;
            jaw.xRot = (0.6F + 0.08F * Mth.sin(s.phaseTime * 0.5F)) * k;  // slack
            tongue.xRot = 0.35F * k;                                       // lolling out over the lip
            tongue.z -= 5.0F * k;
            throat.yScale = 1.0F + 1.0F * k;                               // blown up, bulging past the jaw
            throat.xScale = 1.0F + 0.35F * k;
            throat.zScale = 1.0F + 0.45F * k;
            eyeRight.zRot = (-0.3F + 0.15F * sway) * k;                    // eyes askew
            eyeLeft.zRot = (0.45F - 0.15F * sway) * k;
            eyeRight.xRot = 0.2F * k;
            armRight.zRot = -0.2F * k;
            armLeft.zRot = 0.2F * k;
        } else if (s.phase == Step.RECOVER.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            body.yRot = Mth.sin(s.phaseTime * 1.3F) * 0.18F * k;          // it shakes itself
            head.xRot = -0.35F * k;
            jaw.xRot = 0.3F * k;
            throat.yScale = 1.0F + 0.3F * k;
        }
        if (s.attack > 0.0F) {                                             // a snapping bite
            float a = Mth.sin(s.attack * Mth.PI);
            head.xRot -= 0.45F * a;
            jaw.xRot += 0.35F * a;
        }
    }
}
