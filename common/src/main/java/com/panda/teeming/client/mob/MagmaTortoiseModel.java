package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.MagmaTortoise.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Magma Tortoise: a slow breaststroke with the big front flippers (swept back low, brought forward lifted, so their
 * tips break the lava surface), small kicks of the back flippers, the head held up out of the lava and turning to
 * look. Following, it stretches its neck forward with its head raised and strokes harder; for the first moments of a
 * follow it munches the magma cream.
 *
 * <p>Only the parts over model y 16 show above the lava in play (it floats half sunk), so every cue lives in the head,
 * the neck and the front flippers.
 */
public class MagmaTortoiseModel extends TeemingModel {
    private static final float STROKE = 0.7F;       // flipper cycles per unit of walk distance
    private static final float SWEEP = 0.55F;       // fore-and-aft sweep of a front flipper at full speed
    private static final float LIFT = 0.45F;        // how far a front flipper lifts on its way forward
    private static final int MUNCH_TICKS = 16;

    private final ModelPart shell, neck, head, jaw, tail;
    private final ModelPart frontLeft, frontRight, paddleLeft, paddleRight, backLeft, backRight;

    public MagmaTortoiseModel(ModelPart root) {
        super(root, CreatureLayers.MAGMA_TORTOISE_ONLY);
        shell = part("shell");
        neck = part("neck");
        head = part("head");
        jaw = part("jaw");
        tail = part("tail");
        frontLeft = part("flipper_front_left");
        frontRight = part("flipper_front_right");
        paddleLeft = part("flipper_front_left_paddle");
        paddleRight = part("flipper_front_right_paddle");
        backLeft = part("flipper_back_left");
        backRight = part("flipper_back_right");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean following = s.phase == Step.FOLLOW.ordinal();
        // a tortoise swims at a crawl: its walk speed reads small, so scale it up to a full stroke at its cruising speed
        float swim = Math.min(1.0F, speed * 3.0F);
        float effort = following ? Math.max(swim, 0.6F) : Math.max(swim, 0.15F);   // it treads the lava even at rest
        float phase = following && swim < 0.1F ? s.phaseTime * 0.22F : pos * STROKE + t * 0.04F;
        stroke(phase, effort);

        float back = Mth.sin(phase + Mth.PI * 0.5F) * 0.3F * effort;
        backLeft.yRot += back;
        backRight.yRot -= back;
        tail.yRot = Mth.sin(t * 0.07F) * 0.2F;

        head.yRot = Mth.clamp(s.yRot, -50.0F, 50.0F) * DEG * 0.8F;
        head.xRot = Mth.clamp(s.xRot, -30.0F, 8.0F) * DEG;           // it barely looks down: its face stays out of the lava
        neck.y += Mth.sin(t * 0.06F) * 0.3F;                         // breathing
        neck.xRot += Mth.sin(phase) * 0.04F * effort;                 // the head nods with the stroke
        shell.zRot = Mth.sin(phase) * 0.012F * effort;                // and the shell rocks a hair
        jaw.xRot = 0.02F + 0.02F * Mth.sin(t * 0.05F);

        if (following) eager(s);
    }

    /**
     * One breaststroke: on the power half each front flipper sweeps back low (yRot), on the return half it comes
     * forward lifted (zRot), twisting its paddle edge-on. Left is +X: a negative zRot lifts it, mirrored on the right.
     */
    private void stroke(float phase, float effort) {
        float sweep = Mth.sin(phase) * SWEEP * effort;
        float lift = Math.max(0.0F, Mth.cos(phase)) * LIFT * effort;
        frontLeft.yRot += sweep;
        frontRight.yRot -= sweep;
        frontLeft.zRot -= lift;
        frontRight.zRot += lift;
        float twist = Mth.cos(phase) * 0.35F * effort;
        paddleLeft.xRot += twist;
        paddleRight.xRot += twist;
    }

    /** Following: neck out and head up towards its leader; the first moments it munches the cream it was fed. */
    private void eager(TeemingRenderState s) {
        float k = smooth(s.phaseTime / 8.0F);
        neck.z -= 1.8F * k;
        neck.xRot -= 0.22F * k;
        head.xRot -= 0.18F * k;
        if (s.phaseTime < MUNCH_TICKS) jaw.xRot = 0.4F * Math.abs(Mth.sin(s.phaseTime * 0.8F));
    }
}
