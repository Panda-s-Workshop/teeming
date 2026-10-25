package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.WoolcapGrazer;
import com.panda.teeming.mob.nether.WoolcapGrazer.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Woolcap Grazer: a sheep's walk; head down munching while it grazes; a shake of the bare back after the shears; a
 * bounding gallop, head low, for the stampede. The cap and its tuft hide while it is shorn (and through the shake), and
 * a calf is drawn at half size with a bigger head.
 */
public class WoolcapGrazerModel extends TeemingModel {
    private final ModelPart body, fleece, head, headFleece, tail, earRight, earLeft, frontRight, frontLeft, backRight, backLeft;

    public WoolcapGrazerModel(ModelPart root) {
        super(root, CreatureLayers.WOOLCAP_GRAZER_ONLY);
        body = part("body");
        fleece = part("fleece");
        head = part("head");
        headFleece = part("head_fleece");
        tail = part("tail");
        earRight = part("ear_right");
        earLeft = part("ear_left");
        frontRight = part("leg_front_right");
        frontLeft = part("leg_front_left");
        backRight = part("leg_back_right");
        backLeft = part("leg_back_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean bare = s.flag(WoolcapGrazer.SHORN_FLAG) || s.phase == Step.SHEAR.ordinal();
        fleece.visible = !bare;
        headFleece.visible = !bare;

        float swing = Mth.cos(pos * 0.6662F) * 1.3F * speed;
        frontRight.xRot = swing;
        backLeft.xRot = swing;
        frontLeft.xRot = -swing;
        backRight.xRot = -swing;
        head.xRot = s.xRot * DEG + 8.0F * DEG;                         // it carries its head a little low
        head.yRot = s.yRot * DEG;
        tail.zRot = Mth.sin(t * 0.09F) * 0.12F;
        float flick = Mth.sin(t * 0.13F) * 0.08F;
        earRight.zRot -= flick;
        earLeft.zRot += flick;

        if (s.phase == Step.GRAZE.ordinal()) {
            float k = envelope(s.progress, 0.12F, 0.12F);
            head.xRot = (52.0F * DEG + Mth.sin(s.phaseTime * 1.7F) * 6.0F * DEG) * k + head.xRot * (1.0F - k);
            head.yRot *= 1.0F - k;
            head.y += 3.0F * k;                                            // muzzle down to the ground, not into it
            head.z += 1.0F * k;
            body.xRot = 3.0F * DEG * k;
        } else if (s.phase == Step.SHEAR.ordinal()) {
            float fade = 1.0F - smooth(s.progress);
            float shake = Mth.sin(s.phaseTime * 2.4F) * fade;
            body.zRot = shake * 0.16F;
            head.zRot = shake * 0.3F;
            head.yRot = Mth.sin(s.phaseTime * 2.4F + 1.0F) * 0.35F * fade;
            earRight.zRot += shake * 0.4F;
            earLeft.zRot += shake * 0.4F;
            tail.zRot = shake * 0.5F;
        } else if (s.phase == Step.STAMPEDE.ordinal()) {
            float beat = s.phaseTime * 1.5F;
            float g = Mth.cos(beat) * 1.15F;
            frontRight.xRot = g;                                           // front pair and back pair together: a bound
            frontLeft.xRot = g * 0.85F;
            backRight.xRot = -g;
            backLeft.xRot = -g * 0.85F;
            float lift = Math.abs(Mth.sin(beat));
            body.y -= lift * 1.2F;
            body.xRot = (4.0F + Mth.sin(beat) * 4.0F) * DEG;
            fleece.y -= lift * 0.6F;                                       // the cap bounces on its back
            head.xRot = 22.0F * DEG;                                       // head down, eyes rolling
            head.yRot = Mth.sin(beat * 0.5F) * 0.15F;
            head.y += 1.0F - lift * 1.2F;
            earRight.zRot -= 0.6F;
            earLeft.zRot += 0.6F;
            tail.xRot = 1.1F;
        }

        if (s.isBaby) {
            // half size, the feet still on the ground (the root pivot is 24 px above them), a big calf's head
            ModelPart whole = root();
            whole.xScale = whole.yScale = whole.zScale = 0.5F;
            whole.y += 12.0F;
            head.xScale = head.yScale = head.zScale = 1.45F;
        }
    }
}
