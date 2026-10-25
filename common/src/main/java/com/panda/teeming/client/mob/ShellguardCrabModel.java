package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.ShellguardCrab;
import com.panda.teeming.mob.overworld.ShellguardCrab.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Shellguard Crab: a sideways scuttle on eight jointed legs, both claws up and open for the tell, a forward snap for
 * the pinch, and a roll onto its back with the legs waving while it is flipped.
 *
 * <p>Each leg is hip (Y: fans fore and aft) → thigh (Z: lifts the knee) → shin (Z: bends the foot down). The walk is an
 * alternating tetrapod: left 1 and 3 with right 2 and 4, then the other four, half a stride apart. A leg lifts its
 * thigh while it swings and bends its shin while its foot is down, so the feet push the shell along its side; the
 * synced scuttle flag says which way, so the push runs the right way round.
 */
public class ShellguardCrabModel extends TeemingModel {
    private static final float STRIDE = 1.3F;       // leg cycles per unit of walk distance, faster than a quadruped's
    private static final float LIFT = 0.6F;         // thigh lift while a leg swings
    private static final float REACH = 0.35F;       // shin bend while a foot pushes
    private static final float FAN = 0.12F;         // a little fore-and-aft swing at the hip

    private final ModelPart body, eyes;
    private final ModelPart clawLeft, clawRight, handLeft, handRight, fingerLeft, fingerRight;
    /** Legs 0..3 are the left side front to rear, 4..7 the right side. */
    private final ModelPart[] hips = new ModelPart[8], thighs = new ModelPart[8], shins = new ModelPart[8];

    public ShellguardCrabModel(ModelPart root) {
        super(root, CreatureLayers.SHELLGUARD_CRAB_ONLY);
        body = part("body");
        eyes = part("eyes");
        clawLeft = part("claw_left");
        clawRight = part("claw_right");
        handLeft = part("claw_left_hand");
        handRight = part("claw_right_hand");
        fingerLeft = part("claw_left_finger");
        fingerRight = part("claw_right_finger");
        for (int i = 0; i < 8; i++) {
            String name = "leg_" + (i < 4 ? "left" : "right") + "_" + (i % 4 + 1);
            hips[i] = part(name);
            thighs[i] = part(name + "_thigh");
            shins[i] = part(name + "_shin");
        }
    }

    /** +1 for the left side (+X), -1 for the right. */
    private static float side(int leg) {
        return leg < 4 ? 1.0F : -1.0F;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        walk(s, pos, speed);
        idle(s, t);
        if (s.phase == Step.TELL.ordinal()) {
            clawsUp(smooth(s.progress * 2.5F), s.phaseTime);
        } else if (s.phase == Step.PINCH.ordinal()) {
            pinch(s.progress);
        } else if (s.phase == Step.FLIPPED.ordinal()) {
            flipped(s, t);
        }
    }

    private void walk(TeemingRenderState s, float pos, float speed) {
        if (speed < 0.01F) return;
        float stride = pos * STRIDE;
        float toward = s.flag(ShellguardCrab.FLAG_SCUTTLE_LEFT) ? 1.0F : -1.0F;
        for (int i = 0; i < 8; i++) {
            float side = side(i);
            boolean second = ((i % 4) + (i < 4 ? 0 : 1)) % 2 == 1;
            float phase = stride + (second ? Mth.PI : 0.0F);
            float swing = Mth.sin(phase);
            thighs[i].zRot -= side * Math.max(0.0F, swing) * LIFT * speed;          // lift while it swings
            shins[i].zRot += toward * Mth.cos(phase) * REACH * speed;               // fold or reach while its foot is down
            hips[i].yRot += side * swing * FAN * speed;
        }
        body.y += Mth.sin(stride * 2.0F) * 0.25F * speed;
        body.zRot += Mth.cos(stride) * 0.03F * speed;
    }

    private void idle(TeemingRenderState s, float t) {
        eyes.yRot = Mth.clamp(s.yRot, -40.0F, 40.0F) * DEG * 0.6F;
        eyes.y += Mth.sin(t * 0.09F) * 0.15F;
        float snip = (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.07F)), 6.0);       // now and then a claw opens and shuts
        fingerLeft.xRot -= snip * 0.45F;
        fingerRight.xRot -= (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.07F + 2.1F)), 6.0) * 0.45F;
        clawLeft.xRot += Mth.sin(t * 0.05F) * 0.03F;
        clawRight.xRot += Mth.sin(t * 0.05F + 1.3F) * 0.03F;
    }

    /** The tell: both claws high and wide, pincers open, the front of the shell lifted. */
    private void clawsUp(float k, float time) {
        float shake = Mth.sin(time * 2.2F) * 0.04F * k;
        clawLeft.xRot += -1.05F * k;
        clawRight.xRot += -1.05F * k;
        clawLeft.yRot += -0.45F * k;
        clawRight.yRot += 0.45F * k;
        clawLeft.zRot += shake;
        clawRight.zRot -= shake;
        handLeft.xRot += 0.45F * k;
        handRight.xRot += 0.45F * k;
        fingerLeft.xRot += -0.75F * k;
        fingerRight.xRot += -0.75F * k;
        body.xRot += -0.08F * k;
        eyes.y -= 0.6F * k;
    }

    /** The pinch: from up and open, the claws snap forward and down and shut, with a small lunge. */
    private void pinch(float progress) {
        float k = smooth(progress * 2.5F);
        float up = 1.0F - k;
        clawLeft.xRot += -1.05F * up + 0.2F * k;
        clawRight.xRot += -1.05F * up + 0.2F * k;
        clawLeft.yRot += -0.45F * up + 0.2F * k;
        clawRight.yRot += 0.45F * up - 0.2F * k;
        handLeft.xRot += 0.45F * up;
        handRight.xRot += 0.45F * up;
        fingerLeft.xRot += -0.75F * up;
        fingerRight.xRot += -0.75F * up;
        float lunge = Mth.sin(progress * Mth.PI);
        body.z -= 1.2F * lunge;
        body.xRot += 0.06F * lunge;
    }

    /** On its back: rolls over in 4 ticks, waves its legs and claws, rolls back in the last 6. */
    private void flipped(TeemingRenderState s, float t) {
        float in = s.phaseTime / 4.0F;
        float out = (1.0F - s.progress) * ShellguardCrab.FLIP_TICKS / 6.0F;
        float roll = smooth(Math.min(in, out));
        body.zRot += Mth.PI * roll;
        body.y -= 0.6F * roll + 7.0F * Mth.sin(Mth.PI * roll);       // lifted mid-roll so the shell's edge stays out of the ground
        eyes.y += 3.5F * roll;                                           // eyes folded into their sockets
        for (int i = 0; i < 8; i++) {
            float side = side(i);
            thighs[i].zRot += side * (0.25F + 0.35F * Mth.sin(t * 0.9F + i * 1.7F)) * roll;   // body-down is sky-up now
            shins[i].zRot += side * 0.35F * Mth.sin(t * 1.1F + i * 0.9F) * roll;
        }
        // claws flung out to the sides, off the sand and clear of the belly, grabbing at air
        clawLeft.xRot += (0.3F + 0.25F * Mth.sin(t * 0.7F)) * roll;
        clawRight.xRot += (0.3F + 0.25F * Mth.sin(t * 0.7F + 1.6F)) * roll;
        clawLeft.yRot -= 0.95F * roll;
        clawRight.yRot += 0.95F * roll;
        fingerLeft.xRot -= (0.5F + 0.5F * Mth.sin(t * 1.3F)) * 0.5F * roll;
        fingerRight.xRot -= (0.5F + 0.5F * Mth.sin(t * 1.3F + 1.0F)) * 0.5F * roll;
    }
}
