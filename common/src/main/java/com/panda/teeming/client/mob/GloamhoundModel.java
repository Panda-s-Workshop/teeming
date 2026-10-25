package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Gloamhound;
import com.panda.teeming.mob.overworld.Gloamhound.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Gloamhound: a wolf's trot, the low crouch of the tell (chest to the ground, hind legs bunched, ears flat, lips
 * back), a stretched lunge with the jaw wide for the dart, a head-low backing off, the alpha's sit-back howl with
 * its snout to the sky, and a tail-tucked flight. The ruff shows on the alpha only.
 */
public class GloamhoundModel extends TeemingModel {
    private static final float TAIL_REST = 40.0F * DEG;
    private final ModelPart body, mane, tail, head, jaw, earRight, earLeft, frontRight, frontLeft, backRight, backLeft;

    public GloamhoundModel(ModelPart root) {
        super(root, CreatureLayers.GLOAMHOUND_ONLY);
        body = part("body");
        mane = part("mane");
        tail = part("tail");
        head = part("head");
        jaw = part("jaw");
        earRight = part("ear_right");
        earLeft = part("ear_left");
        frontRight = part("leg_front_right");
        frontLeft = part("leg_front_left");
        backRight = part("leg_back_right");
        backLeft = part("leg_back_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        mane.visible = s.flag(Gloamhound.ALPHA_FLAG);

        float swing = Mth.cos(pos * 0.6662F) * 1.3F * speed;
        frontRight.xRot = swing;
        backLeft.xRot = swing;
        frontLeft.xRot = -swing;
        backRight.xRot = -swing;
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        tail.xRot = TAIL_REST + speed * 0.35F;
        tail.zRot = Mth.sin(t * 0.09F) * 0.12F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 3.0F);                       // down within the first third, then held
            float quiver = Mth.sin(s.phaseTime * 2.2F) * 0.02F * k;    // the bunched hind legs tremble
            body.y += 2.0F * k;
            body.xRot = 7.0F * DEG * k;
            head.y += 3.0F * k;
            head.xRot = 6.0F * DEG * k;
            head.yRot *= 1.0F - k;
            jaw.xRot = 0.18F * k;                                      // lips back, teeth showing
            earRight.xRot = -1.1F * k;
            earLeft.xRot = -1.1F * k;
            frontRight.xRot = -0.65F * k;
            frontLeft.xRot = -0.55F * k;
            frontRight.y += 1.6F * k;
            frontLeft.y += 1.4F * k;
            backRight.xRot = -0.75F * k + quiver;
            backLeft.xRot = -0.75F * k - quiver;
            backRight.y += 2.3F * k;
            backLeft.y += 2.3F * k;
            tail.xRot = TAIL_REST + 0.6F * k;
            tail.zRot = 0.0F;
        } else if (s.phase == Step.DART.ordinal()) {
            float reach = Mth.sin(Math.min(1.0F, s.phaseTime / 4.0F) * Mth.HALF_PI);
            body.xRot = -4.0F * DEG * reach;
            head.xRot = -6.0F * DEG * reach;
            head.yRot = 0.0F;
            head.z -= 1.5F * reach;
            jaw.xRot = 0.75F * reach;
            earRight.xRot = -1.2F;
            earLeft.xRot = -1.2F;
            frontRight.xRot = -1.1F * reach;
            frontLeft.xRot = -0.95F * reach;
            backRight.xRot = 0.95F * reach;
            backLeft.xRot = 0.8F * reach;
            tail.xRot = TAIL_REST + 0.9F * reach;
            tail.zRot = 0.0F;
        } else if (s.phase == Step.RETREAT.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            head.xRot = 14.0F * DEG + s.xRot * DEG * 0.3F;
            head.y += 1.0F;
            jaw.xRot = 0.25F * k;
            earRight.xRot = -0.7F;
            earLeft.xRot = -0.7F;
            tail.xRot = 12.0F * DEG;
        } else if (s.phase == Step.HOWL.ordinal()) {
            float k = envelope(s.progress, 0.2F, 0.15F);
            body.y += 2.5F * k;
            body.xRot = -15.0F * DEG * k;
            head.xRot = -58.0F * DEG * k;
            head.yRot = 0.0F;
            head.y -= 0.5F * k;
            jaw.xRot = (0.45F + Mth.sin(s.phaseTime * 0.5F) * 0.08F) * k;
            earRight.xRot = -0.35F * k;
            earLeft.xRot = -0.35F * k;
            frontRight.xRot = 0.0F;
            frontLeft.xRot = 0.0F;
            frontRight.y += 0.5F * k;
            frontLeft.y += 0.5F * k;
            backRight.xRot = -1.1F * k;
            backLeft.xRot = -1.1F * k;
            backRight.y += 3.8F * k;
            backLeft.y += 3.8F * k;
            tail.xRot = TAIL_REST + 0.7F * k;
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
            body.xRot = 4.0F * DEG;
            head.y += 2.0F;
            head.xRot = 22.0F * DEG;                                   // head low, running from you
            head.yRot = 0.0F;
            earRight.xRot = -1.3F;
            earLeft.xRot = -1.3F;
            tail.xRot = -8.0F * DEG;                                   // tucked under
            tail.zRot = 0.0F;
        }
    }
}
