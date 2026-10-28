package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Rotfang.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Rotfang: a low, quick lope on bent, splayed legs, the body slung between them, the two halves of its jaw hanging a
 * crack apart and swaying. The snarl (it crouches lower, the head drops and thrusts forward, both jaw halves swing
 * down and out on their hinges, wide apart, and quiver); the bite (the halves snap shut as the head and body lunge
 * forward); and the gnaw after the counter (it twists round, head turned back and down to its own raised foreleg, the
 * jaw halves clamped on it, shuddering).
 *
 * <p>The legs have a splayed rest angle in the layer, so every pose here adds to it.
 */
public class RotfangModel extends TeemingModel {
    /** How far each jaw half drops, and swings out, in the snarl. */
    private static final float JAW_DROP = 26.0F * DEG;
    private static final float JAW_SPREAD = 42.0F * DEG;

    private final ModelPart body, head, jawLeft, jawRight, frontLeft, frontRight, backLeft, backRight;

    public RotfangModel(ModelPart root) {
        super(root, CreatureLayers.ROTFANG_ONLY);
        body = part("body");
        head = part("head");
        jawLeft = part("jaw_left");
        jawRight = part("jaw_right");
        frontLeft = part("leg_front_left");
        frontRight = part("leg_front_right");
        backLeft = part("leg_back_left");
        backRight = part("leg_back_right");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float swing = Mth.cos(pos * 0.75F) * 1.0F * speed;
        frontLeft.xRot = swing;
        backRight.xRot = swing;
        frontRight.xRot = -swing;
        backLeft.xRot = -swing;
        body.y += Math.abs(Mth.sin(pos * 0.75F)) * 0.4F * speed;
        head.y += Math.abs(Mth.sin(pos * 0.75F)) * 0.4F * speed;
        head.xRot = s.xRot * DEG * 0.5F;
        head.yRot = s.yRot * DEG;

        float drop = (0.06F + Mth.sin(t * 0.11F) * 0.03F) * (1.0F - speed * 0.5F);   // the halves hang a crack apart
        float spread = 0.03F + Mth.sin(t * 0.07F) * 0.02F;

        if (s.phase == Step.SNARL.ordinal()) {
            float k = smooth(s.progress * 2.5F);                          // down and open within the first ticks, then held
            float quiver = Mth.sin(s.phaseTime * 2.8F) * 0.05F * k;
            body.y += 1.6F * k;
            head.y += 0.9F * k;
            head.z -= 1.2F * k;
            head.xRot = -12.0F * DEG * k;                                 // chin up, the open mouth facing the target
            head.yRot *= 1.0F - k;
            drop = JAW_DROP * k + quiver;
            spread = JAW_SPREAD * k + quiver;
            frontLeft.xRot = -0.35F * k;
            frontRight.xRot = -0.35F * k;
            backLeft.xRot = 0.4F * k + quiver;
            backRight.xRot = 0.4F * k - quiver;
            frontLeft.zRot -= 0.15F * k;                                  // elbows further out as it sinks
            frontRight.zRot += 0.15F * k;
        } else if (s.phase == Step.BITE.ordinal()) {
            float snap = smooth(s.progress * 4.0F);                       // shut in the first quarter
            float lunge = envelope(s.progress, 0.15F, 0.5F);
            drop = JAW_DROP * (1.0F - snap);
            spread = JAW_SPREAD * (1.0F - snap);
            body.z -= 1.5F * lunge;
            head.z -= 2.5F * lunge;
            head.xRot = 8.0F * DEG * lunge;
            head.yRot = 0.0F;
            frontLeft.xRot = -0.7F * lunge;
            frontRight.xRot = -0.6F * lunge;
            backLeft.xRot = 0.6F * lunge;
            backRight.xRot = 0.5F * lunge;
        } else if (s.phase == Step.GNAW.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.25F);
            float shudder = Mth.sin(s.phaseTime * 3.1F) * 0.06F * k;
            body.yRot = -0.35F * k;                                       // twisted round toward its left foreleg
            body.zRot = -0.12F * k;
            head.yRot = -1.05F * k + shudder;                             // head turned back and down onto the leg
            head.xRot = 0.55F * k;
            head.zRot = -0.3F * k;
            head.x += 1.5F * k;
            head.z += 2.0F * k;
            drop = 0.05F;
            spread = 0.0F;
            frontLeft.xRot = -0.9F * k;                                   // the bitten foreleg raised into the jaw
            frontLeft.zRot -= 0.3F * k;
            frontRight.xRot = 0.15F * k;
            backLeft.xRot = 0.2F * k;
            backRight.xRot = -0.2F * k + shudder;
        }

        jawLeft.xRot += drop;
        jawRight.xRot += drop;
        jawLeft.yRot -= spread;                                           // each half swings out to its own side
        jawRight.yRot += spread;
        jawLeft.zRot += spread * 0.6F;                                    // and rolls its outer edge down, the wet inside
        jawRight.zRot -= spread * 0.6F;                                   // of the mouth turned up and out
    }
}
