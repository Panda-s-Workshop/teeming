package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Slagspitter.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Slagspitter: a toad's waddle on splayed legs, the spout on its snout swaying and the slag ring at its root slowly
 * breathing. In the tell it squats and tips its head back until the spout stands upright, its mouth parted on the
 * molten pool in its throat, while the ring swells and pulses (it glows from the glowmask while the tell runs). The
 * spit snaps the head forward, the spout thrown out and stretched with the shot and the ring squeezed small. The hop
 * flings the hind legs out behind and the front legs forward, nose up.
 */
public class SlagspitterModel extends TeemingModel {
    private final ModelPart body, head, jaw, spout, ring, eyeRight, eyeLeft, armRight, armLeft, legRight, legLeft;

    public SlagspitterModel(ModelPart root) {
        super(root, CreatureLayers.SLAGSPITTER_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        spout = part("spout");
        ring = part("ring");
        eyeRight = part("eye_right");
        eyeLeft = part("eye_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        legRight = part("leg_right");
        legLeft = part("leg_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // the waddle: front legs paddle in turn, hind legs push, the body rocks side to side
        float step = pos * 0.9F;
        armRight.xRot = Mth.cos(step) * 0.7F * speed;
        armLeft.xRot = Mth.cos(step + Mth.PI) * 0.7F * speed;
        legRight.xRot = Mth.cos(step + Mth.PI) * 0.4F * speed;
        legLeft.xRot = Mth.cos(step) * 0.4F * speed;
        body.zRot = Mth.sin(step) * 0.08F * speed;
        body.y -= Math.abs(Mth.sin(step)) * 0.6F * speed;
        // the spout sways a beat behind the body; the ring breathes; the eyes look about
        spout.xRot += Mth.sin(t * 0.07F) * 0.04F + Mth.sin(step) * 0.06F * speed;
        spout.zRot = Mth.sin(t * 0.05F) * 0.03F;
        float breath = 0.5F + 0.5F * Mth.sin(t * 0.1F);
        ring.xScale = 1.0F + 0.05F * breath;
        ring.yScale = 1.0F + 0.1F * breath;
        ring.zScale = 1.0F + 0.05F * breath;
        eyeRight.yRot = Mth.sin(t * 0.05F) * 0.25F;
        eyeLeft.yRot = Mth.sin(t * 0.05F + 1.1F) * 0.25F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 4.0F);
            float swell = smooth(s.progress);                               // the ring keeps swelling to the spit
            float pulse = Mth.sin(s.phaseTime * 1.4F);
            body.y += 0.8F * k;                                             // squatting, braced for the recoil
            body.xRot = -0.08F * k;
            head.xRot = -0.3F * k;                                          // head tipped back...
            spout.xRot -= (0.14F + 0.02F * pulse) * k;                      // ...until the spout stands upright
            spout.zRot *= 1.0F - k;
            jaw.xRot = head.xRot + (0.12F + 0.04F * pulse) * k;              // mouth parted on the molten pool
            ring.xScale = 1.0F + (0.15F + 0.25F * swell + 0.04F * pulse) * k;
            ring.yScale = 1.0F + (0.25F + 0.5F * swell + 0.06F * pulse) * k;
            ring.zScale = 1.0F + (0.15F + 0.25F * swell + 0.04F * pulse) * k;
            armRight.zRot = 0.3F * k;                                       // front legs splayed wide
            armLeft.zRot = -0.3F * k;
            legRight.xRot = -0.15F * k;
            legLeft.xRot = -0.15F * k;
            eyeRight.xRot = -0.25F * k;                                     // eyes on its target
            eyeLeft.xRot = -0.25F * k;
        } else if (s.phase == Step.LOB.ordinal()) {
            float k = envelope(s.progress, 0.15F, 0.5F);
            body.xRot = 0.12F * k;                                          // the lunge behind the spit
            body.y += 0.4F * k;
            head.xRot = 0.15F * k;                                          // head snapped forward,
            spout.xRot += 0.25F * k;                                        // the spout thrown out
            spout.yScale = 1.0F + 0.2F * k;                                 // and stretched with the shot
            jaw.xRot = head.xRot + 0.1F * k;
            ring.xScale = 1.0F - 0.1F * k;                                  // the ring squeezed empty
            ring.yScale = 1.0F - 0.2F * k;
            ring.zScale = 1.0F - 0.1F * k;
            armRight.xRot = 0.35F * k;
            armLeft.xRot = 0.35F * k;
            legRight.xRot = -0.3F * k;
            legLeft.xRot = -0.3F * k;
        } else if (s.phase == Step.HOP.ordinal()) {
            float k = smooth(s.phaseTime / 3.0F);
            body.xRot = -0.3F * k;                                          // nose up
            body.y -= 0.5F * k;
            head.xRot = -0.1F * k;
            jaw.xRot = head.xRot + 0.08F * k;
            spout.xRot += 0.2F * k;                                         // the spout held pointing ahead
            legRight.xRot = 1.0F * k;                                       // hind legs flung out behind
            legLeft.xRot = 1.0F * k;
            legRight.z += 1.5F * k;
            legLeft.z += 1.5F * k;
            armRight.xRot = -0.9F * k;                                      // front legs reaching for the landing
            armLeft.xRot = -0.9F * k;
            armRight.zRot = 0.2F * k;
            armLeft.zRot = -0.2F * k;
        }
        if (s.attack > 0.0F) {                                              // a snapping nip
            float a = Mth.sin(s.attack * Mth.PI);
            head.xRot -= 0.3F * a;
            jaw.xRot += 0.5F * a;
        }
    }
}
