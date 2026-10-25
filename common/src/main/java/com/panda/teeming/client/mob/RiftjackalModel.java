package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Riftjackal;
import com.panda.teeming.mob.end.Riftjackal.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Riftjackal: a high-stepping trot on long thin legs, the rift light along its spine flickering; the low crouch of the
 * tell (belly down, legs bunched, head level and thrust forward, ears flat, the rift light standing tall and
 * quivering); the landing, stretched out with the jaw snapping shut; and the tumble when the counter throws it back
 * through its own rift (twisted sideways, legs splayed, the light shrunk to a flicker). The second pair of rift sheets
 * shows on the alpha only.
 *
 * <p>The body, ears, jaw and tail have a rest angle in the layer, so every pose here adds to it.
 */
public class RiftjackalModel extends TeemingModel {
    private final ModelPart body, flame, crest, tail, head, jaw, earRight, earLeft, frontRight, frontLeft, backRight, backLeft;

    public RiftjackalModel(ModelPart root) {
        super(root, CreatureLayers.RIFTJACKAL_ONLY);
        body = part("body");
        flame = part("flame");
        crest = part("crest");
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
        crest.visible = s.flag(Riftjackal.ALPHA_FLAG);

        float swing = Mth.cos(pos * 0.6662F) * 1.2F * speed;
        frontRight.xRot = swing;
        backLeft.xRot = swing;
        frontLeft.xRot = -swing;
        backRight.xRot = -swing;
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        body.y += Mth.cos(pos * 1.3324F) * 0.3F * speed;
        tail.xRot += speed * 0.3F;
        tail.zRot = Mth.sin(t * 0.08F) * 0.12F;
        float flicker = 1.0F + Mth.sin(t * 0.9F) * 0.08F + Mth.sin(t * 2.3F) * 0.05F;   // the rift light never sits still
        flame.yScale = flicker;
        crest.yScale = flicker;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);                      // down within the first ticks, then held
            float quiver = Mth.sin(s.phaseTime * 2.6F) * 0.03F * k;
            body.y += 3.0F * k;
            body.xRot += 3.0F * DEG * k;
            head.y += 3.0F * k;
            head.z -= 1.0F * k;
            head.xRot = 4.0F * DEG * k;                               // level, eyes on the target
            head.yRot *= 1.0F - k;
            jaw.xRot += 0.15F * k;
            earRight.xRot -= 0.7F * k;                                // ears flat back
            earLeft.xRot -= 0.7F * k;
            flame.yScale = 1.0F + 0.5F * k + quiver * 4.0F;          // the rift light stands tall and quivers
            crest.yScale = 1.0F + 0.4F * k + quiver * 3.0F;
            frontRight.xRot = -0.75F * k;
            frontLeft.xRot = -0.65F * k;
            frontRight.y += 2.6F * k;
            frontLeft.y += 2.4F * k;
            backRight.xRot = -0.85F * k + quiver;
            backLeft.xRot = -0.85F * k - quiver;
            backRight.y += 2.2F * k;
            backLeft.y += 2.2F * k;
            tail.xRot += 0.6F * k;                                    // straight out behind
            tail.zRot = 0.0F;
        } else if (s.phase == Step.LAND.ordinal()) {
            float snap = 1.0F - smooth(s.progress * 2.0F);           // the bite at once, then it gathers itself
            body.xRot += 6.0F * DEG * snap;
            head.xRot = -8.0F * DEG * snap;
            head.yRot = 0.0F;
            head.z -= 1.5F * snap;
            jaw.xRot += 0.7F * (1.0F - smooth(s.progress * 4.0F));  // open as it lands, snapping shut
            earRight.xRot -= 0.5F;
            earLeft.xRot -= 0.5F;
            flame.yScale = 1.0F + 0.4F * snap;
            frontRight.xRot = -0.9F * snap;
            frontLeft.xRot = -0.7F * snap;
            backRight.xRot = 0.8F * snap;
            backLeft.xRot = 0.6F * snap;
            tail.xRot += 0.5F * snap;
            tail.zRot = 0.0F;
        } else if (s.phase == Step.FLUNG.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            body.zRot = 0.45F * k;                                    // twisted over on its side
            body.y += 1.5F * k;
            head.zRot = 0.5F * k;
            head.yRot = 0.5F * k;
            head.y += 1.5F * k;
            jaw.xRot += 0.4F * k;
            earRight.xRot -= 0.8F;
            earLeft.xRot -= 0.8F;
            flame.yScale = 0.3F + 0.7F * (1.0F - k);                  // the light shrinks to a flicker
            crest.yScale = flame.yScale;
            frontRight.xRot = -0.9F * k;
            frontLeft.xRot = 0.6F * k;
            frontRight.zRot = 0.4F * k;
            frontLeft.zRot = 0.5F * k;
            backRight.xRot = 0.9F * k;
            backLeft.xRot = -0.5F * k;
            backRight.zRot = 0.4F * k;
            backLeft.zRot = 0.5F * k;
            tail.xRot -= 0.6F * k;
        }
    }
}
