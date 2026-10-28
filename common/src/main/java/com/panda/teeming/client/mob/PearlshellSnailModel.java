package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.PearlshellSnail.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pearlshell Snail: a slow glide (the sole ripples, the shell rocks a little, the eye stalks sway), the pearl in the
 * shell's mouth swelling with the visual stage, a nibble with the head down for the feed, a recoil and a rocking shell
 * for the shears, and the hide: the body draws in under the shell, the stalks and head vanish, the shell drops onto the
 * ground with a jolt and the dark lid shuts its mouth, then the body slides out again over the last few ticks.
 *
 * <p>The body is one part with the head and stalks under it, so drawing it in is a squash along its length toward the
 * middle; the sole stays on the ground because nothing scales it up or down.
 */
public class PearlshellSnailModel extends TeemingModel {
    /** The pearl's size at each visual stage: none, three growing, grown. */
    private static final float[] PEARL_SIZE = {0.0F, 0.4F, 0.65F, 0.85F, 1.0F};

    private final ModelPart body, head, stalkLeft, stalkRight, feelerLeft, feelerRight, shell, spire, pearl, lid;

    public PearlshellSnailModel(ModelPart root) {
        super(root, CreatureLayers.PEARLSHELL_SNAIL_ONLY);
        body = part("body");
        head = part("head");
        stalkLeft = part("stalk_left");
        stalkRight = part("stalk_right");
        feelerLeft = part("feeler_left");
        feelerRight = part("feeler_right");
        shell = part("shell");
        spire = part("spire");
        pearl = part("pearl");
        lid = part("lid");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // the glide: the sole ripples along its length, the shell rocks, the head and stalks follow the look
        float ripple = Mth.sin(pos * 1.4F) * speed;
        body.zScale = 1.0F + 0.06F * ripple;
        float rock = Mth.sin(pos * 0.7F) * 0.05F * speed + Mth.sin(t * 0.05F) * 0.01F;
        shell.zRot += rock;
        spire.zRot += rock;
        head.yRot = Mth.clamp(s.yRot, -40.0F, 40.0F) * DEG * 0.6F;
        head.xRot = s.xRot * DEG * 0.4F;
        float sway = Mth.sin(t * 0.08F) * 0.09F;
        stalkLeft.zRot += sway;
        stalkRight.zRot -= Mth.sin(t * 0.08F + 1.3F) * 0.09F;
        stalkLeft.xRot += Mth.sin(t * 0.06F) * 0.06F;
        stalkRight.xRot += Mth.sin(t * 0.06F + 2.0F) * 0.06F;
        feelerLeft.xRot += Mth.sin(t * 0.11F) * 0.1F;
        feelerRight.xRot += Mth.sin(t * 0.11F + 1.7F) * 0.1F;

        float size = PEARL_SIZE[Mth.clamp(s.stage, 0, PEARL_SIZE.length - 1)];
        float shut = 0.0F;

        if (s.phase == Step.FEED.ordinal()) {
            float k = envelope(s.progress, 0.15F, 0.25F);
            head.xRot += 0.45F * k + Mth.sin(s.phaseTime * 1.3F) * 0.1F * k;   // head down, nibbling
            stalkLeft.xRot -= 0.3F * k;
            stalkRight.xRot -= 0.3F * k;
            feelerLeft.xRot += 0.4F * k;
            feelerRight.xRot += 0.4F * k;
        } else if (s.phase == Step.HARVEST.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.4F);
            head.xRot -= 0.35F * k;                                             // a recoil from the shears
            float wobble = Mth.sin(s.phaseTime * 1.6F) * 0.12F * k;
            shell.zRot += wobble;
            spire.zRot += wobble;
            stalkLeft.zRot += 0.35F * k;
            stalkRight.zRot -= 0.35F * k;
        } else if (s.phase == Step.HIDE.ordinal()) {
            shut = envelope(s.progress, 0.06F, 0.12F);                         // snaps shut, slides out at the very end
            float jolt = Mth.sin(s.phaseTime * 2.2F) * 0.1F * Math.max(0.0F, 1.0F - s.phaseTime / 10.0F);
            shell.zRot += jolt;
            spire.zRot += jolt;
        }

        if (shut > 0.0F) {
            body.zScale *= 1.0F - 0.55F * shut;                                // drawn in under the shell
            body.xScale = 1.0F - 0.15F * shut;
            stalkLeft.yScale = 1.0F - shut;
            stalkRight.yScale = 1.0F - shut;
            float drop = 1.0F * shut;                                           // the shell sits down onto the sole
            shell.y += drop;
            spire.y += drop;
            shell.xRot += 0.12F * shut;
            spire.xRot += 0.08F * shut;
        }
        boolean closed = shut > 0.4F;
        head.visible = !closed;
        lid.visible = closed;
        lid.y += 1.0F * shut;
        pearl.visible = size > 0.0F && !closed;
        pearl.xScale = size;
        pearl.yScale = size;
        pearl.zScale = size;
        pearl.y += 2.0F * (1.0F - size);                                        // a small pearl rests on the sole
    }
}
