package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Gloamhound;
import com.panda.teeming.mob.overworld.Gloamhound.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Gloamhound: a hunched, long-legged lope with the jaw hanging loose; the low crouch of the tell (chest to the ground,
 * hind legs bunched, ears flat, spines raised along the back, jaw wide); a stretched, levelled lunge for the dart; a
 * head-low backing off; the alpha's sit-back howl with its snout to the sky; and a tail-tucked flight. The ruff, the
 * collar of spikes, the glowing flank marks and the horns show on the alpha only.
 *
 * <p>The body, ears, jaw, spines and tail have a rest angle in the layer, so every pose here adds to it.
 */
public class GloamhoundModel extends TeemingModel {
    private static final int SPINES = 6;
    private final ModelPart body, mane, crown, tail, head, jaw, earRight, earLeft, frontRight, frontLeft, backRight, backLeft;
    private final ModelPart[] spines = new ModelPart[SPINES];

    public GloamhoundModel(ModelPart root) {
        super(root, CreatureLayers.GLOAMHOUND_ONLY);
        body = part("body");
        mane = part("mane");
        crown = part("crown");
        tail = part("tail");
        head = part("head");
        jaw = part("jaw");
        earRight = part("ear_right");
        earLeft = part("ear_left");
        frontRight = part("leg_front_right");
        frontLeft = part("leg_front_left");
        backRight = part("leg_back_right");
        backLeft = part("leg_back_left");
        for (int i = 0; i < SPINES; i++) spines[i] = part("spine_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean alpha = s.flag(Gloamhound.ALPHA_FLAG);
        mane.visible = alpha;
        crown.visible = alpha;

        float swing = Mth.cos(pos * 0.6662F) * 1.3F * speed;
        frontRight.xRot = swing;
        backLeft.xRot = swing;
        frontLeft.xRot = -swing;
        backRight.xRot = -swing;
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        body.y += Mth.cos(pos * 1.3324F) * 0.4F * speed;                // the hunched lope bobs at the shoulders
        jaw.xRot += Mth.sin(t * 0.11F) * 0.05F;                           // the loose jaw works as it pants
        tail.xRot += speed * 0.35F;
        tail.zRot = Mth.sin(t * 0.09F) * 0.15F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 3.0F);                       // down within the first third, then held
            float quiver = Mth.sin(s.phaseTime * 2.2F) * 0.02F * k;    // the bunched hind legs tremble
            body.y += 3.5F * k;
            body.xRot += 10.0F * DEG * k;                              // levels out: the high shoulders drop
            head.y += 4.0F * k;
            head.xRot = 6.0F * DEG * k;
            head.yRot *= 1.0F - k;
            jaw.xRot += 0.3F * k;                                      // lips back, fangs bared
            earRight.xRot -= 0.6F * k;                                 // ears flat to the skull
            earLeft.xRot -= 0.6F * k;
            for (ModelPart spine : spines) spine.xRot += (0.55F + quiver * 4.0F) * k;   // the spines bristle upright
            frontRight.xRot = -0.8F * k;
            frontLeft.xRot = -0.7F * k;
            frontRight.y += 3.0F * k;
            frontLeft.y += 2.8F * k;
            backRight.xRot = -0.75F * k + quiver;
            backLeft.xRot = -0.75F * k - quiver;
            backRight.y += 2.3F * k;
            backLeft.y += 2.3F * k;
            tail.xRot += 0.5F * k;
            tail.zRot = 0.0F;
        } else if (s.phase == Step.DART.ordinal()) {
            float reach = Mth.sin(Math.min(1.0F, s.phaseTime / 4.0F) * Mth.HALF_PI);
            body.xRot += 8.0F * DEG * reach;                           // stretched out level
            head.xRot = -6.0F * DEG * reach;
            head.yRot = 0.0F;
            head.z -= 1.5F * reach;
            jaw.xRot += 0.6F * reach;
            earRight.xRot -= 0.7F;
            earLeft.xRot -= 0.7F;
            for (ModelPart spine : spines) spine.xRot += 0.35F;
            frontRight.xRot = -1.1F * reach;
            frontLeft.xRot = -0.95F * reach;
            backRight.xRot = 0.95F * reach;
            backLeft.xRot = 0.8F * reach;
            tail.xRot += 0.8F * reach;
            tail.zRot = 0.0F;
        } else if (s.phase == Step.RETREAT.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            head.xRot = 14.0F * DEG + s.xRot * DEG * 0.3F;
            head.y += 1.0F;
            jaw.xRot += 0.2F * k;
            earRight.xRot -= 0.4F;
            earLeft.xRot -= 0.4F;
            tail.xRot -= 0.6F;
        } else if (s.phase == Step.HOWL.ordinal()) {
            float k = envelope(s.progress, 0.2F, 0.15F);
            body.y += 2.5F * k;
            body.xRot -= 15.0F * DEG * k;                              // sits back on its short hind legs
            head.xRot = -58.0F * DEG * k;
            head.yRot = 0.0F;
            head.y -= 0.5F * k;
            jaw.xRot += (0.35F + Mth.sin(s.phaseTime * 0.5F) * 0.08F) * k;
            earRight.xRot += 0.2F * k;
            earLeft.xRot += 0.2F * k;
            for (ModelPart spine : spines) spine.xRot += 0.4F * k;
            frontRight.xRot = 0.0F;
            frontLeft.xRot = 0.0F;
            frontRight.y += 0.5F * k;
            frontLeft.y += 0.5F * k;
            backRight.xRot = -1.1F * k;
            backLeft.xRot = -1.1F * k;
            backRight.y += 3.0F * k;
            backLeft.y += 3.0F * k;
            tail.xRot += 0.6F * k;
            tail.zRot = 0.0F;
        } else if (s.phase == Step.FLEE.ordinal()) {
            // a bolting gallop: front and hind pairs a quarter-stride apart, so some legs are always spread
            float a = s.phaseTime * 1.3F;
            float front = Mth.cos(a) * 0.95F, hind = Mth.sin(a) * 0.95F;
            frontRight.xRot = front;
            frontLeft.xRot = front * 0.8F;
            backRight.xRot = hind;
            backLeft.xRot = hind * 0.8F;
            body.y += 1.0F;
            body.xRot += 4.0F * DEG;
            head.y += 2.0F;
            head.xRot = 22.0F * DEG;                                   // head low, running from you
            head.yRot = 0.0F;
            earRight.xRot -= 0.8F;
            earLeft.xRot -= 0.8F;
            for (ModelPart spine : spines) spine.xRot -= 0.25F;        // spines laid flat
            tail.xRot -= 1.1F;                                         // tucked under
            tail.zRot = 0.0F;
        }
    }
}
