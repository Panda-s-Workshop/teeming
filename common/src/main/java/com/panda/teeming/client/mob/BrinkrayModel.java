package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Brinkray.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Brinkray: a slow glide in which a wave runs out along each broad wing (root, then tip), the body bobbing and pitching
 * with its flight and the ribbons of light streaming from the wingtips; quicker beats as it glides out to bank; the bank
 * (rolled hard onto its side, wings flared up and curled at the tips, tail raised, a shiver through it); the ram (wings
 * swept back and pressed down, nose low, tail straight out); hard upbeats as it pulls away; and stunned (tipped on its
 * side against the rock, wings crumpled, tail limp, its ribbons of light gone out).
 */
public class BrinkrayModel extends TeemingModel {
    private final ModelPart body, tail, wingRight, wingLeft, tipRight, tipLeft, trailRight, trailLeft, streamerRight, streamerLeft;

    public BrinkrayModel(ModelPart root) {
        super(root, CreatureLayers.BRINKRAY_ONLY);
        body = part("body");
        tail = part("tail");
        wingRight = part("wing_right");
        wingLeft = part("wing_left");
        tipRight = part("wing_right_tip");
        tipLeft = part("wing_left_tip");
        trailRight = part("trail_right");
        trailLeft = part("trail_left");
        streamerRight = part("streamer_right");
        streamerLeft = part("streamer_left");
    }

    /** Both wings: the roll of the root and the tip (up is positive), on top of the rest pose. */
    private void wings(float root, float tip) {
        wingRight.zRot += root;
        wingLeft.zRot -= root;
        tipRight.zRot += tip;
        tipLeft.zRot -= tip;
    }

    /** Both wings swept back (positive) or forward. */
    private void sweep(float a) {
        wingRight.yRot += a;
        wingLeft.yRot -= a;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean stalk = s.phase == Step.STALK.ordinal();
        float beat = stalk ? t * 0.34F : t * 0.17F;
        float amp = stalk ? 1.4F : 1.0F;
        wings(Mth.sin(beat) * 0.13F * amp, Mth.sin(beat - 0.9F) * 0.2F * amp);
        body.y += Mth.sin(beat + Mth.HALF_PI) * 0.7F;
        body.xRot = Mth.clamp(s.xRot, -30.0F, 30.0F) * DEG * 0.6F;          // nose down as it sinks, up as it climbs
        tail.yRot = Mth.sin(t * 0.12F) * 0.18F;
        tail.xRot += Mth.sin(t * 0.17F - 1.5F) * 0.06F;
        trailRight.yRot += Mth.sin(t * 0.21F) * 0.12F;
        trailLeft.yRot -= Mth.sin(t * 0.21F + 0.7F) * 0.12F;
        trailRight.visible = true;
        trailLeft.visible = true;
        if (stalk) sweep(0.12F);

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 4.0F);
            float shiver = Mth.sin(s.phaseTime * 2.6F) * 0.05F * k;
            body.zRot = 0.9F * k + shiver;                                    // rolled hard onto its side
            body.xRot += 0.12F * k;
            wings(0.38F * k, 0.32F * k + shiver);                             // flared up, the tips curled
            sweep(-0.15F * k);                                                // reaching forward
            tail.xRot -= 0.35F * k;                                           // tail raised
        } else if (s.phase == Step.RAM.ordinal()) {
            body.xRot += 0.1F;                                                // nose low, skimming
            wings(-0.12F, -0.18F);                                            // pressed down
            sweep(0.35F);                                                     // swept back
            tail.yRot = 0.0F;
            trailRight.yRot = 0.0F;
            trailLeft.yRot = 0.0F;
        } else if (s.phase == Step.RECOVER.ordinal()) {
            float hard = Mth.sin(t * 0.6F);
            wings(0.15F + hard * 0.45F, Mth.sin(t * 0.6F - 0.8F) * 0.35F);
            body.xRot -= 15.0F * DEG;
        } else if (s.phase == Step.STUNNED.ordinal()) {
            float shake = Mth.sin(s.phaseTime * 1.9F) * 0.03F;
            body.zRot = 0.4F + shake;                                         // tipped on its side against the rock
            body.xRot += 0.15F;
            wingRight.zRot -= 0.55F;                                          // crumpled, one wing more than the other
            wingLeft.zRot += 0.25F;
            tipRight.zRot -= 0.6F;
            tipLeft.zRot += 0.45F;
            tail.xRot += 0.35F;                                               // limp
            tail.yRot = 0.3F;
            trailRight.visible = false;                                       // its lights out
            trailLeft.visible = false;
            streamerRight.visible = false;
            streamerLeft.visible = false;
        }
    }
}
