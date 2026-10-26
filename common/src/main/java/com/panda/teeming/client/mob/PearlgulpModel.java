package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Pearlgulp;
import com.panda.teeming.mob.end.Pearlgulp.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pearlgulp: a rolling waddle on its two front flippers, the hind end sliding on its belly and the fluke swaying; the
 * pouch under its jaw breathes, and sits fuller with each pearl it holds (the glow stage). The throat flare before a
 * blink (it rears, the mouth opens and the pouch swells and quivers); the squashed landing; the gulp (the head snaps up,
 * the mouth claps and the pouch bulges); the gape before a bite (jaw dropped wide, head low and forward); and the bite
 * (a lunge, the jaw snapping shut).
 *
 * <p>The jaw and the tail have a rest angle in the layer, so every pose here adds to it. The pouch hangs from the jaw
 * and is turned back against the jaw's swing, so an open mouth never drives it into the ground.
 */
public class PearlgulpModel extends TeemingModel {
    private final ModelPart body, tail, head, jaw, pouch, flipperRight, flipperLeft;

    public PearlgulpModel(ModelPart root) {
        super(root, CreatureLayers.PEARLGULP_ONLY);
        body = part("body");
        tail = part("tail");
        head = part("head");
        jaw = part("jaw");
        pouch = part("pouch");
        flipperRight = part("flipper_right");
        flipperLeft = part("flipper_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float stride = Mth.cos(pos * 0.6662F);
        flipperRight.xRot = stride * 0.9F * speed;
        flipperLeft.xRot = -stride * 0.9F * speed;
        body.zRot = Mth.sin(pos * 0.6662F) * 0.12F * speed;                 // a roll from flipper to flipper
        body.y += Math.abs(stride) * -0.6F * speed;
        tail.yRot = Mth.sin(pos * 0.6662F) * 0.3F * speed + Mth.sin(t * 0.07F) * 0.08F;
        head.yRot = s.yRot * DEG * 0.5F;
        head.xRot = s.xRot * DEG * 0.4F;

        int held = s.stage >= Pearlgulp.GLOW_FLARE ? 2 : Math.min(s.stage, 3);
        float full = 1.0F + 0.07F * held;                                   // fuller with each pearl
        float breathe = Mth.sin(t * 0.1F) * 0.025F;
        float swell = 0.0F;
        float open = Mth.sin(t * 0.05F) * 0.02F;                            // the jaw barely moves at rest
        float bodyPitch = 0.0F;

        // The reaction phases are short in play (6 ticks) but are held far longer for a still frame, so each pose rises
        // fast, holds through the middle and eases out at the end.
        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);
            float quiver = Mth.sin(s.phaseTime * 2.4F) * 0.03F * k;
            bodyPitch = -9.0F * DEG * k;                                    // it rears
            head.xRot = -14.0F * DEG * k;
            head.yRot *= 1.0F - k;
            open = 0.45F * k;
            swell = 0.15F * k + quiver;
            flipperRight.zRot = 0.35F * k;                                   // flippers planted wide
            flipperLeft.zRot = -0.35F * k;
            flipperRight.xRot = -0.3F * k;
            flipperLeft.xRot = -0.3F * k;
            tail.xRot += 0.25F * k;
        } else if (s.phase == Step.BLINK.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.4F);                     // landed flat, then it gathers itself
            body.y += 1.5F * k;
            body.xScale = 1.0F + 0.12F * k;
            body.zScale = 1.0F + 0.08F * k;
            body.yScale = 1.0F - 0.15F * k;
            flipperRight.zRot = 0.5F * k;
            flipperLeft.zRot = -0.5F * k;
            open = 0.12F * k;
        } else if (s.phase == Step.GULP.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.45F);                    // head up and mouth open, clapping shut
            head.xRot = -16.0F * DEG * k;
            head.yRot *= 1.0F - k;
            open = 0.5F * k;
            swell = 0.3F * envelope(s.progress, 0.2F, 0.3F);
        } else if (s.phase == Step.GAPE.ordinal()) {
            float k = smooth(s.progress * 2.5F);
            float quiver = Mth.sin(s.phaseTime * 2.8F) * 0.03F * k;
            bodyPitch = 3.0F * DEG * k;
            head.xRot = -12.0F * DEG * k;                                   // the upper jaw lifts as the lower drops
            head.yRot *= 1.0F - k;
            head.z -= 1.0F * k;
            open = 0.55F * k + quiver;
            flipperRight.xRot = 0.4F * k;
            flipperLeft.xRot = 0.4F * k;
            tail.xRot -= 0.2F * k;
        } else if (s.phase == Step.BITE.ordinal()) {
            float lunge = envelope(s.progress, 0.1F, 0.4F);
            bodyPitch = 6.0F * DEG * lunge;
            head.z -= 2.0F * lunge;
            body.z -= 1.0F * lunge;
            open = 0.5F * (1.0F - smooth(s.progress * 4.0F));              // open as it lunges, snapping shut
            flipperRight.xRot = -0.5F * lunge;
            flipperLeft.xRot = -0.5F * lunge;
        }

        body.xRot += bodyPitch;
        jaw.xRot += open;
        pouch.xRot = -open * 0.6F;                                          // keep the pouch hanging under the open jaw
        float p = full + breathe + swell;
        pouch.xScale = p;
        pouch.zScale = p;
        pouch.yScale = 1.0F + (p - 1.0F) * 0.5F;
    }
}
