package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.ChorusHusk.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Chorus Husk: a stiff, dragging shamble on thin legs, the hunched body rolling with each step, the long arms hanging
 * loose and swinging late, the head lolling and the slack jaw working; the chorus on its back sways and its pods
 * breathe. The wind-up rears both arms back over its head with the body leaning back and the jaw dropped; the blow
 * brings both down in front of it. The swell flings the arms out wide and throws the head back, the body straightening
 * as the pods bulge to half again their size, throbbing faster and faster, and the stalks splay and tremble.
 */
public class ChorusHuskModel extends TeemingModel {
    private final ModelPart body, head, jaw, legRight, legLeft, armRight, armLeft, handRight, handLeft;
    private final ModelPart stalkLeft, stalkRight, sprig;
    private final ModelPart[] pods;
    private final ModelPart[] tips;

    public ChorusHuskModel(ModelPart root) {
        super(root, CreatureLayers.CHORUS_HUSK_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
        stalkLeft = part("stalk_left");
        stalkRight = part("stalk_right");
        sprig = part("sprig");
        pods = new ModelPart[] {part("pod_back"), part("pod_top"), part("pod_low"), part("pod_shoulder_right"), part("pod_shoulder_left")};
        tips = new ModelPart[] {part("tip_left"), part("tip_right"), part("bloom_left"), part("bloom_right"), part("bloom_shoulder")};
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // ---- the shamble
        float step = pos * 0.6F;
        float stride = Mth.cos(step) * 0.65F * speed;
        legRight.xRot += stride;
        legLeft.xRot -= stride;
        body.zRot += Mth.cos(step) * 0.08F * speed;                           // the hunched body rolls with each step
        body.y += Math.abs(Mth.sin(step)) * -0.7F * speed;
        armRight.xRot -= Mth.cos(step - 0.9F) * 0.45F * speed;                // arms swing late and loose
        armLeft.xRot += Mth.cos(step - 0.9F) * 0.45F * speed;
        armRight.zRot += Mth.sin(t * 0.06F) * 0.04F;
        armLeft.zRot -= Mth.sin(t * 0.06F + 1.1F) * 0.04F;
        head.yRot += s.yRot * DEG * 0.6F;
        head.xRot += s.xRot * DEG * 0.4F;
        head.zRot += Mth.sin(t * 0.05F) * 0.06F + Mth.cos(step) * 0.06F * speed;   // the head lolls
        jaw.xRot += 0.12F + Mth.sin(t * 0.11F) * 0.08F;                      // the slack jaw works
        stalkLeft.zRot += Mth.sin(t * 0.07F) * 0.05F + Mth.cos(step) * 0.05F * speed;
        stalkRight.zRot += Mth.sin(t * 0.07F + 1.7F) * 0.05F - Mth.cos(step) * 0.05F * speed;
        sprig.zRot += Mth.sin(t * 0.09F + 0.5F) * 0.08F;
        float breathe = 1.0F + Mth.sin(t * 0.08F) * 0.04F;                   // the pods breathe
        scale(pods, breathe);

        if (s.phase == Step.WINDUP.ordinal()) {
            float k = envelope(s.progress, 0.45F, 0.0F);                      // up fast, held to the blow
            armRight.xRot += -2.7F * k;                                       // both arms reared back over the head
            armLeft.xRot += -2.7F * k;
            armRight.zRot += 0.25F * k;
            armLeft.zRot -= 0.25F * k;
            handRight.xRot -= 0.5F * k;                                       // claws cocked back
            handLeft.xRot -= 0.5F * k;
            body.xRot -= 0.3F * k;                                            // leaning back out of the hunch
            head.xRot -= 0.35F * k;
            jaw.xRot += 0.45F * k;
            float shiver = Mth.sin(t * 2.2F) * 0.03F * k;
            armRight.zRot += shiver;
            armLeft.zRot -= shiver;
        } else if (s.phase == Step.STRIKE.ordinal()) {
            float k = 1.0F - smooth(s.progress);                              // the blow lands at once, then eases off
            armRight.xRot += -2.7F + 2.2F * smooth(Math.min(1.0F, s.progress * 3.0F));
            armLeft.xRot += -2.7F + 2.2F * smooth(Math.min(1.0F, s.progress * 3.0F));
            armRight.xRot -= 0.6F * k;                                        // swung through in front
            armLeft.xRot -= 0.6F * k;
            body.xRot += 0.35F * k;                                           // lunging into it
            head.xRot += 0.2F * k;
        } else if (s.phase == Step.SWELL.ordinal()) {
            float k = smooth(Math.min(1.0F, s.progress * 1.4F));
            float rate = 0.5F + 2.0F * s.progress;                            // the throb quickens toward the burst
            float throb = Mth.sin(s.phaseTime * rate) * 0.1F * (0.4F + s.progress);
            scale(pods, 1.0F + 0.55F * k + throb);
            for (ModelPart tip : tips) {
                float g = 1.0F + 0.35F * k + throb;
                tip.xScale = g;
                tip.yScale = g;
                tip.zScale = g;
            }
            armRight.zRot += 1.15F * k;                                       // arms flung out wide
            armLeft.zRot -= 1.15F * k;
            armRight.xRot -= 0.5F * k;
            armLeft.xRot -= 0.5F * k;
            handRight.xRot -= 0.4F * k;
            handLeft.xRot -= 0.4F * k;
            body.xRot -= 0.32F * k;                                           // straightening out of the hunch
            head.xRot -= 0.7F * k;                                            // head thrown back
            jaw.xRot += 0.55F * k;
            float tremble = Mth.sin(t * 2.6F) * 0.05F * k;
            stalkLeft.zRot += 0.35F * k + tremble;                            // the stalks splay and tremble
            stalkRight.zRot -= 0.35F * k + tremble;
            body.zRot += tremble * 0.6F;
            legRight.zRot += 0.12F * k;                                       // braced wide
            legLeft.zRot -= 0.12F * k;
        }
    }

    private static void scale(ModelPart[] parts, float by) {
        for (ModelPart p : parts) {
            p.xScale = by;
            p.yScale = by;
            p.zScale = by;
        }
    }
}
