package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.AstralAugur;
import com.panda.teeming.mob.end.AstralAugur.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Astral Augur: a slow glide under the bell robe, the long neck swaying, and over its head the orbit ring with its star
 * motes turning slowly. Tell (the channel): both hands go up in a high V with a star spark in each palm, the head tips
 * back to the sky, the ring lifts and the motes spin faster and faster. Strike: the hands sweep down toward the
 * circles and the head bows. Broken: thrown back, head flung aside, arms wide, the ring wobbling, one mote fewer.
 * Backhand: the right arm winds up high and sweeps across. Lost motes (synced flags) are hidden, the last first.
 */
public class AstralAugurModel extends TeemingModel {
    /** The ring turns this fast at rest, in radians a tick; in the channel it speeds up to about 0.5. */
    private static final float SPIN = 0.04F;
    private static final float SPIN_UP = 0.01F;
    /** The motes' orbit radius, in pixels. */
    private static final float ORBIT = 6.0F;

    private final ModelPart body, skirt, neck, head, halo, armRight, armLeft, handRight, handLeft, sparkRight, sparkLeft;
    private final ModelPart[] motes = new ModelPart[AstralAugur.MOTES];

    public AstralAugurModel(ModelPart root) {
        super(root, CreatureLayers.ASTRAL_AUGUR_ONLY);
        body = part("body");
        skirt = part("skirt");
        neck = part("neck");
        head = part("head");
        halo = part("halo");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
        sparkRight = part("spark_right");
        sparkLeft = part("spark_left");
        for (int i = 0; i < motes.length; i++) motes[i] = part("mote_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        int lost = AstralAugur.motesLost(s.flags);
        for (int i = 0; i < motes.length; i++) {
            motes[i].visible = i < motes.length - lost;
            motes[i].y += Mth.sin(t * 0.1F + i * 2.1F) * 0.4F;                // each mote bobs on its own beat
        }
        sparkRight.visible = false;
        sparkLeft.visible = false;

        // the glide: the bell robe sways, the neck sways, the sleeves swing a little
        float w = pos * 0.6F;
        skirt.xRot += Mth.sin(w) * 0.05F * speed + Mth.sin(t * 0.06F) * 0.02F;
        skirt.zRot += Mth.cos(w) * 0.04F * speed;
        body.zRot += Mth.cos(w) * 0.02F * speed;
        neck.xRot += -0.05F + Mth.sin(t * 0.05F) * 0.04F;
        neck.yRot += s.yRot * DEG * 0.5F;
        head.yRot += s.yRot * DEG * 0.5F;
        head.xRot += s.xRot * DEG;
        armRight.xRot += -0.15F + Mth.cos(w) * 0.25F * speed + Mth.sin(t * 0.05F) * 0.03F;
        armLeft.xRot += -0.15F - Mth.cos(w) * 0.25F * speed - Mth.sin(t * 0.05F) * 0.03F;
        float spin = t * SPIN;
        float tm = s.phaseTime;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(tm / 5.0F);
            armRight.xRot = Mth.lerp(k, armRight.xRot, -2.75F);                 // both hands high in a V
            armRight.zRot = Mth.lerp(k, armRight.zRot, -0.45F);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -2.75F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, 0.45F);
            handRight.xRot = -0.3F * k;
            handLeft.xRot = -0.3F * k;
            sparkRight.visible = true;
            sparkLeft.visible = true;
            float pulse = 0.8F + 0.4F * k + 0.15F * Mth.sin(tm * 1.5F);
            sparkRight.xScale = sparkRight.yScale = sparkRight.zScale = pulse;
            sparkLeft.xScale = sparkLeft.yScale = sparkLeft.zScale = pulse;
            body.xRot -= 0.08F * k;                                              // it leans back
            neck.xRot -= 0.15F * k;
            head.yRot = 0.0F;
            neck.yRot = 0.0F;
            head.xRot = Mth.lerp(k, head.xRot, -0.45F);                           // its face to the sky
            halo.y -= 1.2F * k;                                                  // the ring lifts
            spin += SPIN_UP * tm * tm;                                           // and spins up
        } else if (s.phase == Step.STRIKE.ordinal()) {
            float u = smooth(tm / 4.0F);
            float ease = 1.0F - smooth((tm - 6.0F) / 6.0F);                       // the pose eases off after the blow
            armRight.xRot = Mth.lerp(ease, armRight.xRot, Mth.lerp(u, -2.75F, -1.1F));   // hands sweep down at the circles
            armRight.zRot = Mth.lerp(ease, armRight.zRot, Mth.lerp(u, -0.45F, -0.15F));
            armLeft.xRot = Mth.lerp(ease, armLeft.xRot, Mth.lerp(u, -2.75F, -1.1F));
            armLeft.zRot = Mth.lerp(ease, armLeft.zRot, Mth.lerp(u, 0.45F, 0.15F));
            head.xRot = Mth.lerp(ease, head.xRot, 0.35F * u);                     // the head bows
            neck.xRot += 0.1F * u * ease;
            body.xRot += 0.1F * u * ease;
        } else if (s.phase == Step.BROKEN.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.45F);
            body.xRot -= 0.25F * k;                                              // thrown back
            neck.xRot -= 0.3F * k;
            head.xRot = Mth.lerp(k, head.xRot, -0.6F);
            head.zRot += 0.35F * k;                                              // the head flung aside
            armRight.zRot += 0.9F * k;                                           // arms flung wide
            armLeft.zRot -= 0.9F * k;
            armRight.xRot -= 0.4F * k;
            armLeft.xRot -= 0.4F * k;
            halo.zRot += 0.25F * Mth.sin(tm * 1.2F) * k;                          // the ring wobbles
            halo.xRot += 0.2F * Mth.cos(tm * 1.0F) * k;
        } else if (s.phase == Step.SWIPE.ordinal()) {
            float u = s.progress;
            float wind = smooth(u / 0.7F);
            float hit = smooth((u - 0.7F) / 0.3F);
            armRight.xRot = Mth.lerp(hit, Mth.lerp(wind, armRight.xRot, -2.1F), -0.5F);  // up high, then down across
            armRight.zRot = Mth.lerp(hit, Mth.lerp(wind, armRight.zRot, -0.5F), 0.4F);
            body.yRot += 0.35F * wind - 0.6F * hit;                              // the body twists into it
            head.yRot -= 0.2F * wind - 0.3F * hit;
        }
        // the ring floats level whatever the head does (it only wobbles when the channel breaks)
        halo.xRot -= body.xRot + neck.xRot + head.xRot;
        halo.zRot -= body.zRot + head.zRot;
        // the motes ride round the ring (moved one by one, so the ring's levelling tilt stays put)
        for (int i = 0; i < motes.length; i++) {
            float a = (90.0F + i * 120.0F) * DEG + spin;
            motes[i].x = ORBIT * Mth.cos(a);
            motes[i].z = ORBIT * Mth.sin(a);
        }
    }
}
