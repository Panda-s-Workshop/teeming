package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.ExileWraith.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Exile Wraith: it drifts, bobbing a little over the ground, its tail of rags trailing and the strips swaying, the ring
 * in its hood turning slowly. Mark (the tell): it rears up, arms spread low and wide with the claws turned down, the
 * hood tipped toward the circle, and the ring swells and spins faster as the circle fills. Exile: arms flung out and up,
 * the ring flared. Snagged: bowed and hunched, arms hanging, the ring shrunk and flickering. Claw: one arm reared high
 * and back for the wind-up, then raked down across.
 */
public class ExileWraithModel extends TeemingModel {
    /** How high it drifts over its rest pose and how far it bobs, in pixels. */
    private static final float HOVER = 0.5F;
    private static final float BOB = 0.8F;
    /** The ring turns this fast at rest, in radians a tick. */
    private static final float SPIN = 0.03F;

    private final ModelPart body, skirt, tail, tailTip, hood, ring, armRight, armLeft, handRight, handLeft, capeRight, capeLeft;
    private final ModelPart[] strips = new ModelPart[6];

    public ExileWraithModel(ModelPart root) {
        super(root, CreatureLayers.EXILE_WRAITH_ONLY);
        body = part("body");
        skirt = part("skirt");
        tail = part("tail");
        tailTip = part("tail_tip");
        hood = part("hood");
        ring = part("ring");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
        capeRight = part("cape_right");
        capeLeft = part("cape_left");
        for (int i = 0; i < strips.length; i++) strips[i] = part("strip_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // the drift: a slow bob, the rags trailing more as it moves, each strip swaying on its own beat
        body.y -= HOVER + BOB * Mth.sin(t * 0.08F);
        body.xRot += 0.12F * speed;                                            // leans into its drift
        skirt.xRot += 0.25F * speed + Mth.sin(t * 0.07F) * 0.05F;
        tail.xRot += 0.2F * speed + Mth.sin(t * 0.07F - 0.6F) * 0.08F;
        tailTip.xRot += 0.2F * speed + Mth.sin(t * 0.07F - 1.2F) * 0.12F;
        for (int i = 0; i < strips.length; i++) {
            strips[i].xRot += 0.3F * speed + Mth.sin(t * 0.09F + i * 1.3F) * 0.12F;
            strips[i].zRot += Mth.cos(t * 0.07F + i * 0.9F) * 0.08F;
        }
        capeRight.xRot += 0.35F * speed + Mth.sin(t * 0.08F) * 0.06F;
        capeLeft.xRot += 0.35F * speed + Mth.sin(t * 0.08F + 1.0F) * 0.06F;
        hood.yRot += s.yRot * DEG * 0.7F;
        hood.xRot += s.xRot * DEG * 0.5F;
        float w = pos * 0.5F;
        armRight.xRot += Mth.cos(w) * 0.2F * speed + Mth.sin(t * 0.06F) * 0.04F;
        armLeft.xRot += -Mth.cos(w) * 0.2F * speed - Mth.sin(t * 0.06F) * 0.04F;
        armRight.zRot += Mth.sin(t * 0.05F) * 0.03F;
        armLeft.zRot -= Mth.sin(t * 0.05F) * 0.03F;
        float spin = t * SPIN;
        float ringScale = 1.0F;
        float tm = s.phaseTime;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(tm / 5.0F);
            float fill = Mth.clamp(s.progress, 0.0F, 1.0F);
            body.y -= 1.5F * k;                                                  // it rears up
            body.xRot -= 0.1F * k;
            hood.xRot = Mth.lerp(k, hood.xRot, 0.3F);                            // the hood tips toward the circle
            hood.yRot = Mth.lerp(k, hood.yRot, 0.0F);
            armRight.xRot = Mth.lerp(k, armRight.xRot, -0.9F);                   // arms reaching forward, low and wide
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.6F);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -0.9F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -0.6F);
            handRight.xRot = 0.9F * k;                                           // claws turned down at the ground
            handLeft.xRot = 0.9F * k;
            ringScale = 1.0F + 0.35F * k * fill + 0.06F * Mth.sin(tm * 1.4F);
            spin += 0.012F * tm * tm;                                           // and spins faster as it fills
            for (ModelPart strip : strips) strip.xRot -= 0.25F * k;              // the rags lift off the ground
        } else if (s.phase == Step.EXILE.ordinal()) {
            float k = envelope(s.progress, 0.15F, 0.5F);
            armRight.zRot += 1.6F * k;                                           // arms flung out and up
            armLeft.zRot -= 1.6F * k;
            armRight.xRot -= 0.5F * k;
            armLeft.xRot -= 0.5F * k;
            hood.xRot -= 0.35F * k;
            body.xRot -= 0.12F * k;
            ringScale = 1.0F + 0.5F * k;
            spin += tm * 0.3F;
        } else if (s.phase == Step.SNAGGED.ordinal()) {
            float k = envelope(s.progress, 0.08F, 0.2F);
            body.y += 1.0F * k;                                                  // dragged down and held
            body.xRot += 0.35F * k;                                              // bowed, hunched over
            hood.xRot += 0.35F * k;
            armRight.xRot = Mth.lerp(k, armRight.xRot, 0.25F);                   // arms hanging slack
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.15F);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, 0.25F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -0.15F);
            for (ModelPart strip : strips) strip.xRot -= 0.3F * k;               // the rags pulled down into the circle
            tail.xRot -= 0.2F * k;
            boolean flicker = ((int) (tm * 0.7F) % 3) == 0;
            ringScale = (0.6F + (flicker ? 0.25F : 0.0F)) * k + (1.0F - k);     // the ring shrunk and flickering
        } else if (s.phase == Step.WINDUP.ordinal()) {
            float k = smooth(tm / 4.0F);
            armRight.xRot = Mth.lerp(k, armRight.xRot, -2.6F);                   // the right arm reared high and back
            armRight.zRot = Mth.lerp(k, armRight.zRot, -0.3F);
            handRight.xRot = -0.5F * k;
            body.yRot -= 0.3F * k;                                               // the body twists away to load it
            hood.yRot += 0.25F * k;
            body.xRot -= 0.08F * k;
        } else if (s.phase == Step.CLAW.ordinal()) {
            float u = smooth(tm / 2.0F);
            float ease = 1.0F - smooth((tm - 3.0F) / 3.0F);
            armRight.xRot = Mth.lerp(ease, armRight.xRot, Mth.lerp(u, -2.6F, -0.2F));   // raked down across
            armRight.zRot = Mth.lerp(ease, armRight.zRot, Mth.lerp(u, -0.3F, 0.5F));
            handRight.xRot = Mth.lerp(ease, 0.0F, 0.6F * u);
            body.yRot += Mth.lerp(ease, 0.0F, 0.35F * u);
            body.xRot += 0.15F * u * ease;
        }
        ring.zRot = spin;
        ring.xScale = ring.yScale = ringScale;
    }
}
