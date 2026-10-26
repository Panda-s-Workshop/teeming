package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Brinkram.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Brinkram: a heavy walk; the tell's pawing with the horns lowered; a gallop with the head down for the charge; braced
 * forelegs and a rocking body while it teeters at a brink; legs flung out as it falls over an edge; a head shake after
 * a wall; the butt's rear-back and jab.
 */
public class BrinkramModel extends TeemingModel {
    private final ModelPart body, head, tail, earRight, earLeft, frontRight, frontLeft, backRight, backLeft;

    public BrinkramModel(ModelPart root) {
        super(root, CreatureLayers.BRINKRAM_ONLY);
        body = part("body");
        head = part("head");
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
        float swing = Mth.cos(pos * 0.6F) * 1.1F * speed;
        frontRight.xRot = swing;
        backLeft.xRot = swing;
        frontLeft.xRot = -swing;
        backRight.xRot = -swing;
        head.xRot = s.xRot * DEG + Mth.cos(pos * 0.6F) * 0.04F * speed;
        head.yRot = s.yRot * DEG;
        tail.zRot = Mth.sin(t * 0.09F) * 0.12F;
        earRight.zRot += Mth.sin(t * 0.07F) * 0.08F;
        earLeft.zRot -= Mth.sin(t * 0.07F + 1.3F) * 0.08F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);                          // horns down within the first part
            head.xRot = 34.0F * DEG * k;
            head.yRot = 0.0F;
            head.y += 2.5F * k;
            head.z -= 0.5F * k;
            body.xRot = 5.0F * DEG * k;
            frontRight.xRot = -0.55F + Mth.sin(s.phaseTime * 1.2F) * 0.6F;    // pawing the ground
            frontLeft.xRot = 0.1F;
            backRight.xRot = 0.25F * k;                                  // haunches set to push off
            backLeft.xRot = 0.25F * k;
            tail.xRot = 0.6F * k;
            // a snort tosses the head a little twice
            float snort = Math.max(0.0F, Mth.sin((s.phaseTime - 6.0F) * 0.4F)) * (s.phaseTime > 6.0F && s.phaseTime < 18.0F ? 1.0F : 0.0F);
            head.xRot -= snort * 6.0F * DEG;
        } else if (s.phase == Step.CHARGE.ordinal()) {
            float gallop = Mth.cos(s.phaseTime * 1.5F) * 1.15F;
            frontRight.xRot = gallop;
            frontLeft.xRot = gallop * 0.8F;
            backRight.xRot = -gallop;
            backLeft.xRot = -gallop * 0.8F;
            head.xRot = 38.0F * DEG;
            head.yRot = 0.0F;
            head.y += 2.5F;
            head.z -= 0.5F;
            body.xRot = 6.0F * DEG + Mth.sin(s.phaseTime * 1.5F) * 0.04F;
            tail.xRot = 1.0F;
        } else if (s.phase == Step.TEETER.ordinal()) {
            float rock = Mth.sin(s.phaseTime * 0.55F);
            body.xRot = (7.0F + 5.0F * rock) * DEG;                      // rocking out over the edge and back
            head.xRot = -22.0F * DEG + rock * 6.0F * DEG;                // head flung up, looking down the drop
            head.yRot = Mth.sin(s.phaseTime * 0.3F) * 0.15F;
            head.y += 1.0F + rock;
            frontRight.xRot = -0.65F;                                    // forelegs planted forward, braced
            frontLeft.xRot = -0.55F;
            frontRight.zRot = 0.12F;
            frontLeft.zRot = -0.12F;
            backRight.xRot = 0.45F + rock * 0.1F;                        // hind legs digging back
            backLeft.xRot = 0.4F - rock * 0.1F;
            tail.xRot = 0.8F;
            tail.zRot = Mth.sin(s.phaseTime * 1.3F) * 0.4F;
        } else if (s.phase == Step.OVER.ordinal()) {
            float flail = Mth.sin(s.phaseTime * 1.6F);
            body.xRot = 30.0F * DEG;                                     // pitched nose-down over the drop
            head.xRot = -30.0F * DEG;
            head.yRot = 0.0F;
            frontRight.xRot = -0.9F + flail * 0.5F;
            frontLeft.xRot = -0.9F - flail * 0.5F;
            backRight.xRot = 0.9F - flail * 0.5F;
            backLeft.xRot = 0.9F + flail * 0.5F;
            frontRight.zRot = 0.3F;
            frontLeft.zRot = -0.3F;
            backRight.zRot = 0.25F;
            backLeft.zRot = -0.25F;
            tail.xRot = 1.2F;
        } else if (s.phase == Step.BONK.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            head.xRot = 16.0F * DEG;
            head.yRot = Mth.sin(s.phaseTime * 1.1F) * 0.45F * k;         // a hard head shake that settles
            head.zRot = Mth.sin(s.phaseTime * 1.1F + 0.6F) * 0.18F * k;
            body.zRot = Mth.sin(s.phaseTime * 0.8F) * 0.04F * k;
            frontRight.xRot = -0.2F;
            frontLeft.xRot = 0.15F;
        } else if (s.phase == Step.BUTT.ordinal()) {
            float jab = s.progress < 0.55F ? -smooth(s.progress / 0.55F) : smooth((s.progress - 0.55F) / 0.25F);
            head.xRot = (jab < 0.0F ? 22.0F * jab : 40.0F * jab) * DEG;  // rear back, then drive the horns down
            head.yRot = 0.0F;
            head.y += 1.5F * Math.max(0.0F, jab);
            head.z -= 2.0F * Math.max(0.0F, jab);
            body.xRot = 4.0F * DEG * jab;
            frontRight.xRot = -0.3F * Math.max(0.0F, -jab);
            frontLeft.xRot = -0.3F * Math.max(0.0F, -jab);
        } else if (s.phase == Step.RECOVER.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            head.xRot = 30.0F * DEG * k;
            head.y += 2.0F * k;
        }
        if (s.attack > 0.0F) head.xRot += Mth.sin(s.attack * Mth.PI) * 30.0F * DEG;
    }
}
