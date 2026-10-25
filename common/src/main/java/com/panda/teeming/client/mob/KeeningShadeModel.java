package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.KeeningShade.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Keening Shade: it drifts clear of the ground, bobbing slowly, hunched with its head hung forward on a leaning neck,
 * its trailing shroud and the veil down its back swaying a beat behind; moving, it leans in and the tatters stream out
 * behind. Tell: it draws breath: its chest caves in, its shoulders hunch and its arms draw back, its head tips back and
 * the jaw parts on the soul glow in its throat, and it rises a little as the breath fills it. Wail: the jaw drops wide
 * and long, its head comes up to face ahead and shakes, its arms fling out with their torn drapes hanging beneath like
 * ragged sails, and the shroud and veil are blown back. Voiceless: it sinks until its tatters drag on the ground, head
 * bowed and turned aside, the jaw hanging slack and crooked, arms limp.
 */
public class KeeningShadeModel extends TeemingModel {
    /** How far it hangs and bobs above its rest height, in pixels. */
    private static final float BOB = 0.8F;
    /** How far it sinks while voiceless, in pixels: the tatters just touch the ground. */
    private static final float SINK = 2.0F;
    /** The open mouth lies flat over the face: its 1-pixel box squashed to this depth. */
    private static final float MAW_DEPTH = 0.15F;

    private final ModelPart body, chest, hollow, skirt, tail, wisp, neck, head, jaw, maw, veil, lockRight, lockLeft;
    private final ModelPart armRight, armLeft, drapeRight, drapeLeft, handRight, handLeft;

    public KeeningShadeModel(ModelPart root) {
        super(root, CreatureLayers.KEENING_SHADE_ONLY);
        body = part("body");
        chest = part("chest");
        hollow = part("hollow");
        skirt = part("skirt");
        tail = part("tail");
        wisp = part("wisp");
        neck = part("neck");
        head = part("head");
        jaw = part("jaw");
        maw = part("maw");
        veil = part("veil");
        lockRight = part("lock_right");
        lockLeft = part("lock_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        drapeRight = part("drape_right");
        drapeLeft = part("drape_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // the drift: a slow bob, the shroud swaying a beat behind it, the tatters trailing when it moves
        float bob = Mth.sin(t * 0.08F);
        body.y += bob * BOB - 0.3F;
        body.xRot += 0.14F * speed;                                             // it leans into its drift
        float sway = Mth.sin(t * 0.08F - 0.9F);
        skirt.xRot += 0.06F * sway + 0.22F * speed;
        tail.xRot += 0.09F * Mth.sin(t * 0.08F - 1.6F) + 0.18F * speed;
        wisp.xRot += 0.12F * Mth.sin(t * 0.08F - 2.3F) + 0.15F * speed;
        skirt.zRot += 0.04F * Mth.sin(t * 0.05F);
        tail.zRot += 0.06F * Mth.sin(t * 0.05F - 0.8F);
        veil.xRot += 0.05F + 0.04F * sway + 0.2F * speed;
        lockRight.zRot += 0.03F * Mth.sin(t * 0.07F);
        lockLeft.zRot -= 0.03F * Mth.sin(t * 0.07F + 0.5F);
        float arms = Mth.sin(t * 0.06F) * 0.05F;
        armRight.xRot += arms + 0.25F * speed;                                  // limp arms trailing as it goes
        armLeft.xRot -= arms - 0.25F * speed;
        drapeRight.xRot += 0.05F * sway + 0.2F * speed;
        drapeLeft.xRot += 0.05F * sway + 0.2F * speed;
        handRight.xRot += 0.1F;
        handLeft.xRot += 0.1F;
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        head.zRot = 0.05F * Mth.sin(t * 0.04F);                                 // a slow, mournful tilt
        jaw.xRot = 0.04F + 0.03F * Mth.sin(t * 0.05F);
        maw.visible = false;                                                    // the mouth is shut
        maw.zScale = MAW_DEPTH;

        if (s.attack > 0.0F) {                                                  // a weak poke with the right hand
            float a = Mth.sin(s.attack * Mth.PI);
            armRight.xRot -= 1.3F * a;
            drapeRight.zRot -= 0.3F * a;
            body.yRot -= 0.15F * a;
        }

        float tm = s.phaseTime;
        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(tm / 5.0F);                                        // into the pose in a quarter second
            float fill = smooth(s.progress);                                    // the breath filling it, all the tell long
            float e = envelope(s.progress, 0.0F, 0.08F);
            chest.zScale = 1.0F - 0.35F * k * e;                                // the chest caves in
            chest.xScale = 1.0F - 0.1F * k * e;
            hollow.zScale = chest.zScale;
            hollow.xScale = chest.xScale;
            body.y -= 1.0F * fill * e;                                          // and it rises a little
            body.xRot -= 0.1F * k * e;
            neck.xRot -= 0.15F * k * e;
            head.xRot = Mth.lerp(k * e, head.xRot, -0.85F - 0.1F * fill);       // head tipped back past the neck's lean
            head.yRot *= 1.0F - k;
            head.zRot = 0.0F;
            jaw.xRot = 0.04F + (0.3F + 0.15F * fill) * k * e;                   // the jaw parts on the glow in its throat
            mouth(0.6F, (0.35F + 0.25F * fill) * k * e);                        // a small round mouth, sucking in
            armRight.xRot += 0.55F * k * e;                                     // shoulders hunched, arms drawn back
            armLeft.xRot += 0.55F * k * e;
            armRight.zRot -= 0.12F * k * e;
            armLeft.zRot += 0.12F * k * e;
            drapeRight.xRot += 0.2F * k * e;
            drapeLeft.xRot += 0.2F * k * e;
            skirt.xRot -= 0.1F * k * e;                                         // the shroud drawn in and up
            tail.xRot -= 0.12F * k * e;
            veil.xRot -= 0.1F * k * e;
        } else if (s.phase == Step.WAIL.ordinal()) {
            float w = smooth(tm / 3.0F);                                        // the wail bursts out
            float e = envelope(s.progress, 0.0F, 0.2F);
            float a = w * e;
            float shake = Mth.sin(tm * 3.1F) * 0.05F * a;
            jaw.xRot = 0.04F + 1.05F * a;                                       // the jaw drops wide...
            jaw.yScale = 1.0F + 0.45F * a;                                      // ...and long
            jaw.zScale = 1.0F + 0.1F * a;
            mouth(1.0F, 1.35F * a);                                             // the long, gaping mouth of the keen
            head.xRot = Mth.lerp(a, head.xRot, -0.3F) + shake;                  // up to face ahead, shaking
            head.yRot *= 1.0F - a;
            head.zRot = shake;
            chest.zScale = 1.0F + 0.15F * a;
            hollow.zScale = chest.zScale;
            body.xRot -= 0.12F * a;
            armRight.zRot += 1.25F * a;                                         // arms flung out...
            armLeft.zRot -= 1.25F * a;
            armRight.xRot -= 0.35F * a;
            armLeft.xRot -= 0.35F * a;
            drapeRight.zRot -= 0.75F * a;                                       // ...their drapes hanging beneath
            drapeLeft.zRot += 0.75F * a;
            handRight.zRot -= 0.3F * a;
            handLeft.zRot += 0.3F * a;
            skirt.xRot += 0.3F * a;                                             // the shroud and the veil blown back
            tail.xRot += 0.25F * a;
            wisp.xRot += 0.3F * a;
            veil.xRot += 0.7F * a;
            lockRight.zRot += 0.35F * a;
            lockLeft.zRot -= 0.35F * a;
        } else if (s.phase == Step.VOICELESS.ordinal()) {
            float k = smooth(tm / 8.0F) * envelope(s.progress, 0.0F, 0.05F);
            body.y += (SINK - bob * BOB * 0.8F) * k;                            // sunk to the ground, hardly bobbing
            body.xRot += 0.15F * k;
            head.xRot = Mth.lerp(k, head.xRot, 0.35F);                          // head bowed and turned aside
            head.yRot = Mth.lerp(k, head.yRot, 0.25F);
            head.zRot = 0.18F * k;
            jaw.xRot = 0.04F + 0.45F * k;                                       // the jaw hanging slack, crooked
            jaw.zRot = 0.14F * k;
            chest.zScale = 1.0F - 0.15F * k;
            hollow.zScale = chest.zScale;
            armRight.xRot = Mth.lerp(k, armRight.xRot, 0.12F);                  // limp
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, 0.12F);
            armRight.zRot -= 0.06F * k;
            armLeft.zRot += 0.06F * k;
            tail.xRot += 0.35F * k;                                             // the tatters crumpled on the ground
            wisp.xRot += 0.5F * k;
            veil.xRot -= 0.04F * k;
        }
    }

    /** Shows the open mouth {@code wide} across (0..1) and {@code open} of its full length down the face. */
    private void mouth(float wide, float open) {
        if (open <= 0.05F) return;
        maw.visible = true;
        maw.xScale = wide;
        maw.yScale = open;
    }
}
