package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Nullcantor;
import com.panda.teeming.mob.end.Nullcantor.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Nullcantor: a slow glide under the bell robe, hands clasped before it, the hood nodding to its chant and the collar
 * breathing a little with it. Tell: both arms go up and out like a conductor's at the swell, the hood tips back, the
 * collar flares and it rises onto its toes. Strike: both arms come down hard in front of it and it leans into the blow.
 * Cracked: it reels back, hands at its throat, the hood thrown aside. While its collar is off (a synced flag) the collar
 * is hidden and a ragged stub shows in its place.
 */
public class NullcantorModel extends TeemingModel {
    private final ModelPart body, skirt, hem, collar, collarBroken, head, armRight, armLeft;

    public NullcantorModel(ModelPart root) {
        super(root, CreatureLayers.NULLCANTOR_ONLY);
        body = part("body");
        skirt = part("skirt");
        hem = part("hem");
        collar = part("collar");
        collarBroken = part("collar_broken");
        head = part("head");
        armRight = part("arm_right");
        armLeft = part("arm_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean off = s.flag(Nullcantor.FLAG_COLLAR_OFF);
        collar.visible = !off;
        collarBroken.visible = off;

        // the glide: the robe sways, the hood nods to the chant, the collar breathes with it
        float w = pos * 0.6F;
        float chant = Mth.sin(t * 0.12F);
        skirt.xRot += Mth.sin(w) * 0.05F * speed + Mth.sin(t * 0.06F) * 0.02F;
        hem.xRot += Mth.sin(w - 0.6F) * 0.06F * speed;
        skirt.zRot += Mth.cos(w) * 0.03F * speed;
        body.zRot += Mth.cos(w) * 0.02F * speed;
        body.y += Mth.sin(t * 0.08F) * 0.3F;
        head.yRot += s.yRot * DEG * 0.6F;
        head.xRot += s.xRot * DEG * 0.6F + chant * 0.04F;
        float breathe = 1.0F + 0.02F * chant;
        collar.xScale *= breathe;
        collar.zScale *= breathe;
        armRight.xRot += Mth.sin(t * 0.05F) * 0.03F - 0.08F * speed;
        armLeft.xRot += Mth.sin(t * 0.05F) * 0.03F - 0.08F * speed;
        float tm = s.phaseTime;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(tm / 4.0F);
            float swell = smooth(s.progress);
            raise(armRight, -1, k);
            raise(armLeft, 1, k);
            head.xRot = Mth.lerp(k, head.xRot, -0.5F);                          // the hollow turned up to sing
            head.yRot *= 1.0F - k;
            body.xRot -= 0.1F * k;                                               // it leans back into the swell
            body.y -= 1.0F * swell;                                              // and rises
            float flare = 1.0F + 0.15F * k + 0.05F * Mth.sin(tm * 2.0F) * k;
            collar.xScale *= flare;
            collar.zScale *= flare;
        } else if (s.phase == Step.STRIKE.ordinal()) {
            float u = smooth(tm / 2.5F);                                          // down fast
            float ease = 1.0F - smooth((s.progress - 0.55F) / 0.45F);            // held, then back over its last half
            float x = Mth.lerp(u, -2.7F, -0.9F);
            armRight.xRot = Mth.lerp(ease, armRight.xRot, x);                   // both arms brought down before it
            armLeft.xRot = Mth.lerp(ease, armLeft.xRot, x);
            armRight.yRot = Mth.lerp(ease, armRight.yRot, -0.2F);
            armLeft.yRot = Mth.lerp(ease, armLeft.yRot, 0.2F);
            armRight.zRot = Mth.lerp(ease, armRight.zRot, 0.0F);
            armLeft.zRot = Mth.lerp(ease, armLeft.zRot, 0.0F);
            body.xRot += 0.3F * u * ease;                                        // leaning into the blow
            head.xRot = Mth.lerp(ease, head.xRot, 0.3F * u);
        } else if (s.phase == Step.CRACKED.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.4F);
            body.xRot -= 0.25F * k;                                              // reeling back
            head.xRot = Mth.lerp(k, head.xRot, -0.4F);
            head.zRot += 0.4F * k;                                               // the hood thrown aside
            armRight.xRot = Mth.lerp(k, armRight.xRot, -2.0F);                  // hands to its throat
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -2.0F);
            armRight.yRot = Mth.lerp(k, armRight.yRot, -0.7F);
            armLeft.yRot = Mth.lerp(k, armLeft.yRot, 0.7F);
            hem.zRot += 0.08F * Mth.sin(tm * 1.5F) * k;
        }
    }

    /** One arm up and out, like a conductor's at the swell; {@code side} -1 right, 1 left. */
    private static void raise(ModelPart arm, int side, float k) {
        arm.xRot = Mth.lerp(k, arm.xRot, -2.65F);
        arm.yRot = Mth.lerp(k, arm.yRot, 0.0F);
        arm.zRot = Mth.lerp(k, arm.zRot, side * 0.55F);
    }
}
