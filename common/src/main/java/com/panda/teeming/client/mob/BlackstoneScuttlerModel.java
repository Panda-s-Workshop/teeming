package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.BlackstoneScuttler.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Blackstone Scuttler: a heavy, low walk on six jointed legs; for the tell it rears up on its back legs, the front of
 * the shell lifted high and both club claws raised and open, grinding (a tremble); the lunge drops the shell forward
 * and snaps the claws shut ahead of it; it settles back down in the recovery.
 *
 * <p>Each leg is hip (Y: swings fore and aft) → thigh (Z: lifts the knee) → shin (Z: bends the foot down). The walk is
 * an alternating tripod: left 1 and 3 with right 2, then right 1 and 3 with left 2, half a stride apart.
 */
public class BlackstoneScuttlerModel extends TeemingModel {
    private static final float STRIDE = 1.0F;       // leg cycles per unit of walk distance: slow, heavy steps
    private static final float LIFT = 0.5F;         // thigh lift while a leg swings
    private static final float SWING = 0.35F;       // fore-and-aft swing at the hip
    private static final float REAR = 0.5F;         // how far the shell pitches up in the rear, radians
    private static final float LIFT_UP = 2.3F;      // how far the shell lifts in the rear, px: its back stays on its back legs

    private final ModelPart body, eyes;
    private final ModelPart clawLeft, clawRight, handLeft, handRight, fingerLeft, fingerRight;
    /** Legs 0..2 are the left side front to rear, 3..5 the right side. */
    private final ModelPart[] hips = new ModelPart[6], thighs = new ModelPart[6], shins = new ModelPart[6];

    public BlackstoneScuttlerModel(ModelPart root) {
        super(root, CreatureLayers.BLACKSTONE_SCUTTLER_ONLY);
        body = part("body");
        eyes = part("eyes");
        clawLeft = part("claw_left");
        clawRight = part("claw_right");
        handLeft = part("claw_left_hand");
        handRight = part("claw_right_hand");
        fingerLeft = part("claw_left_finger");
        fingerRight = part("claw_right_finger");
        for (int i = 0; i < 6; i++) {
            String name = "leg_" + (i < 3 ? "left" : "right") + "_" + (i % 3 + 1);
            hips[i] = part(name);
            thighs[i] = part(name + "_thigh");
            shins[i] = part(name + "_shin");
        }
    }

    /** +1 for the left side (+X), -1 for the right. */
    private static float side(int leg) {
        return leg < 3 ? 1.0F : -1.0F;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        walk(pos, speed);
        idle(s, t);
        if (s.phase == Step.REAR.ordinal()) {
            rear(smooth(s.progress * 2.5F), s.phaseTime);
        } else if (s.phase == Step.LUNGE.ordinal()) {
            lunge(s.progress);
        } else if (s.phase == Step.RECOVER.ordinal()) {
            settle(s.progress);
        }
    }

    private void walk(float pos, float speed) {
        if (speed < 0.01F) return;
        float stride = pos * STRIDE;
        for (int i = 0; i < 6; i++) {
            float side = side(i);
            boolean second = ((i % 3) + (i < 3 ? 0 : 1)) % 2 == 1;
            float phase = stride + (second ? Mth.PI : 0.0F);
            thighs[i].zRot -= side * Math.max(0.0F, Mth.cos(phase)) * LIFT * speed;    // lift while it swings forward
            hips[i].yRot += side * Mth.sin(phase) * SWING * speed;
        }
        body.y += Mth.sin(stride * 2.0F) * 0.25F * speed;
        body.zRot += Mth.cos(stride) * 0.035F * speed;                                  // a heavy roll from side to side
    }

    private void idle(TeemingRenderState s, float t) {
        eyes.yRot = Mth.clamp(s.yRot, -40.0F, 40.0F) * DEG * 0.6F;
        eyes.y += Mth.sin(t * 0.08F) * 0.15F;
        float snip = (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.06F)), 6.0);        // now and then a claw opens and shuts
        fingerLeft.xRot -= snip * 0.4F;
        fingerRight.xRot -= (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.06F + 2.3F)), 6.0) * 0.4F;
        body.y += Mth.sin(t * 0.07F) * 0.1F;
    }

    /**
     * The tell: the shell pitches up and lifts on its back legs, the front legs reach up off the ground, both claws go
     * high and wide with the pincers open, and the whole front grinds (a fast tremble).
     */
    private void rear(float k, float time) {
        float grind = Mth.sin(time * 3.3F) * 0.05F * k;
        body.xRot -= REAR * k;
        body.y -= LIFT_UP * k;
        body.z += 1.0F * k;
        body.zRot += grind * 0.5F;
        clawLeft.xRot -= 0.9F * k + grind;
        clawRight.xRot -= 0.9F * k - grind;
        clawLeft.yRot -= 0.55F * k;
        clawRight.yRot += 0.55F * k;
        handLeft.xRot -= 0.35F * k;
        handRight.xRot -= 0.35F * k;
        fingerLeft.xRot -= 0.85F * k;
        fingerRight.xRot -= 0.85F * k;
        eyes.y -= 0.8F * k;
        for (int i = 0; i < 6; i++) {
            float side = side(i);
            int n = i % 3;
            if (n == 0) {                    // front legs lifted and reaching forward
                thighs[i].zRot -= side * 0.55F * k;
                hips[i].yRot += side * 0.35F * k;
            } else if (n == 1) {             // middle legs follow the shell up, feet still down
                thighs[i].zRot -= side * 0.1F * k;
                shins[i].zRot += side * 0.3F * k;
            } else {                         // back legs braced straighter, carrying the shell
                thighs[i].zRot -= side * 0.1F * k;
                shins[i].zRot -= side * 0.1F * k;
            }
        }
    }

    /**
     * The lunge: from up and open, the shell drops forward and shoves ahead, and the claws snap forward and shut on the
     * first few ticks; they stay clamped as it slides.
     */
    private void lunge(float progress) {
        float drop = smooth(progress * 3.0F);
        float up = 1.0F - drop;
        float hold = envelope(progress, 0.2F, 0.3F);
        body.xRot += -REAR * up + 0.12F * hold;
        body.y += -LIFT_UP * up + 0.4F * hold;
        body.z -= 2.5F * hold;
        clawLeft.xRot += -0.9F * up + 0.25F * hold;
        clawRight.xRot += -0.9F * up + 0.25F * hold;
        clawLeft.yRot += -0.55F * up + 0.25F * hold;
        clawRight.yRot += 0.55F * up - 0.25F * hold;
        fingerLeft.xRot -= 0.85F * up;
        fingerRight.xRot -= 0.85F * up;
        for (int i = 0; i < 6; i++) {
            if (i % 3 == 2) thighs[i].zRot -= side(i) * 0.3F * hold;     // back legs push off
        }
    }

    /** Settling back: the shell sinks low, the claws drop to the ground and slowly come back up. */
    private void settle(float progress) {
        float k = 1.0F - smooth(progress);
        body.y += 0.8F * k;
        body.xRot += 0.08F * k;
        clawLeft.xRot += 0.2F * k;
        clawRight.xRot += 0.2F * k;
        clawLeft.yRot += 0.15F * k;
        clawRight.yRot -= 0.15F * k;
    }
}
