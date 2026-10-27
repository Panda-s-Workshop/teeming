package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Swellshade;
import com.panda.teeming.mob.end.Swellshade.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Swellshade: a legless mass that hovers and bobs on its wisp of smoke, the belly slowly breathing, the long arms
 * dangling and swaying late behind its glide, the smoke curls on its shoulders drifting. The deep breath before a swell
 * fills the belly out and up, throws the head back and spreads the arms, the curls rising; the pulse grows the whole body
 * from its old size to its new one, past it and back, the arms flung out. The wind-up rears the mass back with both arms
 * raised high over its head and the claws cocked; the blow brings both down in front of it.
 */
public class SwellshadeModel extends TeemingModel {
    private final ModelPart body, tail, mass, belly, head, hood, armRight, armLeft, handRight, handLeft, curlRight, curlLeft;

    public SwellshadeModel(ModelPart root) {
        super(root, CreatureLayers.SWELLSHADE_ONLY);
        body = part("body");
        tail = part("tail");
        mass = part("mass");
        belly = part("belly");
        head = part("head");
        hood = part("hood");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
        curlRight = part("curl_right");
        curlLeft = part("curl_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // ---- the hover and the glide
        body.y += Mth.sin(t * 0.1F) * 0.6F;                                   // bobbing on its wisp
        body.xRot += 0.18F * speed;                                           // leaning into the glide
        tail.zRot += Mth.sin(t * 0.13F) * 0.12F;
        tail.xRot += Mth.sin(t * 0.09F + 0.7F) * 0.08F + 0.35F * speed;       // the wisp streams back as it moves
        float breathe = 1.0F + Mth.sin(t * 0.08F) * 0.035F;                  // the belly breathes
        belly.xScale = breathe;
        belly.zScale = breathe;
        belly.yScale = 1.0F + Mth.sin(t * 0.08F) * 0.02F;
        float sway = Mth.cos(pos * 0.5F) * 0.35F * speed;
        armRight.xRot += sway - 0.25F * speed + Mth.sin(t * 0.07F) * 0.05F;   // dangling, trailing the glide
        armLeft.xRot -= sway + 0.25F * speed - Mth.sin(t * 0.07F + 1.3F) * 0.05F;
        armRight.zRot += Mth.sin(t * 0.06F) * 0.05F;
        armLeft.zRot -= Mth.sin(t * 0.06F + 0.9F) * 0.05F;
        head.yRot += s.yRot * DEG * 0.5F;
        head.xRot += s.xRot * DEG * 0.4F;
        hood.xRot += Mth.sin(t * 0.05F) * 0.06F;
        curlRight.zRot += Mth.sin(t * 0.11F) * 0.12F;                         // the smoke curls drift
        curlLeft.zRot += Mth.sin(t * 0.11F + 2.0F) * 0.12F;
        curlRight.xRot += Mth.sin(t * 0.07F + 0.5F) * 0.08F;
        curlLeft.xRot += Mth.sin(t * 0.07F + 1.5F) * 0.08F;

        if (s.phase == Step.INHALE.ordinal()) {
            float k = smooth(s.progress);                                     // drawing it in, deeper and deeper
            float fill = 1.0F + 0.14F * k;
            belly.xScale *= fill;
            belly.zScale *= fill;
            belly.yScale *= 1.0F + 0.08F * k;
            mass.xRot -= 0.12F * k;                                           // swelling up and back
            head.xRot -= 0.55F * k;                                           // head thrown back
            armRight.zRot += 0.7F * k;                                        // arms spread
            armLeft.zRot -= 0.7F * k;
            armRight.xRot -= 0.25F * k;
            armLeft.xRot -= 0.25F * k;
            handRight.xRot -= 0.3F * k;
            handLeft.xRot -= 0.3F * k;
            curlRight.zRot -= 0.35F * k;                                      // the curls rise and splay
            curlLeft.zRot += 0.35F * k;
            curlRight.xRot += 0.3F * k;
            curlLeft.xRot += 0.3F * k;
            body.y -= 0.8F * k;
        } else if (s.phase == Step.PULSE.ordinal()) {
            // the pulse: from its old size (the server already grew it) through its new one, past it and back
            int step = Math.min(s.stage, Swellshade.MAX_STEPS);
            float was = step > 0 ? (float) ((1.0 + Swellshade.GROW_STEP * (step - 1)) / (1.0 + Swellshade.GROW_STEP * step)) : 1.0F;
            float p = s.progress;
            float grow = was + (1.0F - was) * smooth(Math.min(1.0F, p * 1.8F)) + 0.08F * Mth.sin(Math.min(1.0F, p) * Mth.PI);
            body.xScale = grow;
            body.yScale = grow;
            body.zScale = grow;
            float k = 1.0F - smooth(p);                                       // the breath let go: arms flung, head down
            belly.xScale *= 1.0F + 0.1F * k;
            belly.zScale *= 1.0F + 0.1F * k;
            armRight.zRot += 0.95F * k;
            armLeft.zRot -= 0.95F * k;
            head.xRot += 0.25F * k;
            curlRight.zRot -= 0.25F * k;
            curlLeft.zRot += 0.25F * k;
        } else if (s.phase == Step.WINDUP.ordinal()) {
            float k = envelope(s.progress, 0.45F, 0.0F);                      // up fast, held to the blow
            mass.xRot -= 0.3F * k;                                            // the mass rears back
            head.xRot -= 0.35F * k;
            armRight.xRot += -2.8F * k;                                       // both arms raised high over its head
            armLeft.xRot += -2.8F * k;
            armRight.zRot += 0.2F * k;
            armLeft.zRot -= 0.2F * k;
            handRight.xRot -= 0.6F * k;                                       // claws cocked back
            handLeft.xRot -= 0.6F * k;
            body.y -= 0.6F * k;
            float shiver = Mth.sin(t * 2.4F) * 0.03F * k;
            armRight.zRot += shiver;
            armLeft.zRot -= shiver;
        } else if (s.phase == Step.STRIKE.ordinal()) {
            float k = 1.0F - smooth(s.progress);                              // the blow lands at once, then eases off
            float down = smooth(Math.min(1.0F, s.progress * 3.0F));
            armRight.xRot += -2.8F + 2.2F * down - 0.6F * k;                  // swung down through in front
            armLeft.xRot += -2.8F + 2.2F * down - 0.6F * k;
            mass.xRot += 0.35F * k;                                           // lunging into it
            head.xRot += 0.2F * k;
        }
    }
}
