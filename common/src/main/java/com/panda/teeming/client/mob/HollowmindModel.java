package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Hollowmind;
import com.panda.teeming.mob.end.Hollowmind.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Hollowmind: the dome breathes and its core pulses; creeping, it squashes and stretches along the ground and its six
 * tendrils writhe behind it. Latch tell: it crouches flat, the lobes part, all six tendrils reach straight up and the
 * core swells in wet beats. Riding: it settles onto the host's head with its tendrils hanging down the sides of the
 * head, and while its dome glows gold its lilac lights (the core and the tendril buds) go dark. Seeking a new host: it
 * rocks and twitches, tendrils whipping. Nip: the front rears and the maw gapes; bite: it lunges and snaps shut.
 */
public class HollowmindModel extends TeemingModel {
    private static final int TENDRILS = 6;
    /**
     * Each tendril's heading straight out from the middle of the dome, from where it is rooted under the rim: in the
     * latch tell they fan out round it like a crown instead of standing hidden against the parted lobes.
     */
    private static final float[] OUTWARD = {
            (float) Math.atan2(-4.5, -1.0), (float) Math.atan2(-4.5, 2.5), (float) Math.atan2(4.5, -1.0),
            (float) Math.atan2(4.5, 2.5), (float) Math.atan2(-2.0, 4.5), (float) Math.atan2(2.0, 4.5)};
    private final ModelPart body, lobeLeft, lobeRight, core, maw;
    private final ModelPart[] tendril = new ModelPart[TENDRILS], tip = new ModelPart[TENDRILS], bud = new ModelPart[TENDRILS];

    public HollowmindModel(ModelPart root) {
        super(root, CreatureLayers.HOLLOWMIND_ONLY);
        body = part("body");
        lobeLeft = part("lobe_left");
        lobeRight = part("lobe_right");
        core = part("core");
        maw = part("maw");
        for (int i = 0; i < TENDRILS; i++) {
            tendril[i] = part("tendril_" + i);
            tip[i] = part("tip_" + i);
            bud[i] = part("bud_" + i);
        }
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // the weak point lit: every other glow goes dark
        core.visible = !s.vulnerable;
        for (ModelPart b : bud) b.visible = !s.vulnerable;

        // breathing and creeping: a slow pulse, a squash and stretch along the ground, tendrils writhing behind
        float breathe = Mth.sin(t * 0.15F);
        float crawl = Mth.sin(pos * 1.2F) * speed;
        body.yScale *= 1.0F + 0.04F * breathe - 0.06F * crawl;
        body.zScale *= 1.0F + 0.08F * crawl;
        float pulse = 1.0F + 0.06F * Mth.sin(t * 0.3F);
        core.xScale *= pulse;
        core.yScale *= pulse;
        for (int i = 0; i < TENDRILS; i++) {
            float ph = i * 1.1F;
            tendril[i].yRot += Mth.sin(t * 0.1F + ph) * 0.12F + Mth.sin(pos * 1.2F + ph) * 0.25F * speed;
            tip[i].yRot += Mth.sin(t * 0.13F + ph + 1.0F) * 0.2F + Mth.sin(pos * 1.2F + ph - 0.8F) * 0.35F * speed;
            tip[i].xRot += Mth.sin(t * 0.09F + ph) * 0.06F;
        }
        float tm = s.phaseTime;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(tm / 6.0F);                                            // down and reaching within 6 ticks
            float beat = Math.max(0.0F, Mth.sin(tm * 0.9F));                        // wet beats as the core swells
            body.y += 1.5F * k;                                                      // crouched flat
            body.yScale *= 1.0F - 0.2F * k;
            body.xScale *= 1.0F + 0.12F * k;
            body.zScale *= 1.0F + 0.08F * k;
            lobeRight.zRot -= 0.3F * k;                                              // the lobes part
            lobeLeft.zRot += 0.3F * k;
            float swell = 1.0F + (0.2F + 0.25F * beat) * k;
            core.xScale *= swell;
            core.yScale *= swell;
            core.zScale *= 1.0F + 0.1F * beat * k;
            for (int i = 0; i < TENDRILS; i++) {
                // reaching up and out, a crown of six round the crouched dome
                tendril[i].xRot = Mth.lerp(k, tendril[i].xRot, 1.05F + 0.08F * Mth.sin(tm * 0.7F + i));
                tendril[i].yRot = Mth.lerp(k, tendril[i].yRot, OUTWARD[i]);
                tip[i].xRot = Mth.lerp(k, tip[i].xRot, 0.35F + 0.15F * beat);
                tip[i].yRot *= 1.0F - 0.7F * k;
            }
            maw.yScale *= 1.0F - 0.3F * k;
        } else if (s.phase == Step.RIDE.ordinal()) {
            boolean onHead = s.flag(Hollowmind.FLAG_ON_HOST);
            body.yScale *= 0.92F + 0.03F * breathe;
            body.xScale *= 1.04F;
            body.zScale *= 1.04F;
            if (onHead) body.y += 1.0F;                                              // settled on the head
            for (int i = 0; i < TENDRILS; i++) {
                if (onHead) {
                    tendril[i].xRot = -1.5F + 0.05F * Mth.sin(t * 0.12F + i);      // hanging down the sides of the head
                    tendril[i].yRot *= 0.3F;
                    tip[i].xRot = -0.1F;
                    tip[i].yRot *= 0.3F;
                } else {
                    tendril[i].yRot *= 0.4F;                                        // with no head under it, held in tight
                    tip[i].yRot = -tendril[i].yRot;
                }
            }
            maw.yScale *= 0.7F;
        } else if (s.phase == Step.SEEK.ordinal()) {
            float k = smooth(tm / 3.0F);
            body.zRot += Mth.sin(tm * 1.3F) * 0.18F * k;                             // rocking, frantic
            body.xRot += Mth.sin(tm * 0.9F + 1.0F) * 0.1F * k;
            body.y -= 0.6F * k * Math.abs(Mth.sin(tm * 0.8F));
            lobeRight.zRot -= 0.1F * k * Math.abs(Mth.sin(tm * 1.7F));
            lobeLeft.zRot += 0.1F * k * Math.abs(Mth.sin(tm * 1.7F + 0.5F));
            float flicker = 0.85F + 0.25F * Math.abs(Mth.sin(tm * 2.1F));
            core.xScale *= flicker;
            for (int i = 0; i < TENDRILS; i++) {
                tendril[i].xRot += (0.35F + 0.3F * Mth.sin(tm * 1.1F + i * 1.7F)) * k;   // whipping up off the ground
                tendril[i].yRot += Mth.sin(tm * 0.9F + i) * 0.4F * k;
                tip[i].xRot += Mth.sin(tm * 1.4F + i) * 0.5F * k;
            }
        } else if (s.phase == Step.NIP.ordinal()) {
            float k = smooth(tm / 4.0F);
            body.xRot -= 0.45F * k;                                                  // the front rears
            body.y -= 0.5F * k;
            maw.yScale *= 1.0F + 0.7F * k;                                           // the maw gapes
            maw.xScale *= 1.0F + 0.25F * k;
            maw.z -= 0.6F * k;
            lobeRight.zRot -= 0.08F * k;
            lobeLeft.zRot += 0.08F * k;
            for (int i = 0; i < TENDRILS; i++) tendril[i].xRot -= 0.1F * k;         // braced on the ground
        } else if (s.phase == Step.BITE.ordinal()) {
            float u = smooth(tm / 2.0F);                                             // in fast
            float ease = 1.0F - smooth((s.progress - 0.5F) / 0.5F);                 // back over its last half
            body.xRot += Mth.lerp(u, -0.45F, 0.15F) * ease;
            body.z -= 2.0F * u * ease;                                                // the lunge
            maw.yScale *= Mth.lerp(u, 1.7F, 0.6F);                                   // snapped shut
            maw.z -= 0.6F * (1.0F - u);
        }
    }
}
