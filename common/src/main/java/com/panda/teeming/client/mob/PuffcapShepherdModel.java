package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.PuffcapShepherd.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Puffcap Shepherd: a waddle on stubby feet with its cap bobbing. The tell swells the cap up and out (it throbs as it
 * grows), the body squats and the crook arm swings wide so the swollen brim clears it; the puff snaps the cap back
 * small with a shake; the burst leaves the cap squashed and tipped over while it sways, dazed.
 */
public class PuffcapShepherdModel extends TeemingModel {
    private final ModelPart body, cap, legRight, legLeft, armRight, armLeft, crook, sac;

    public PuffcapShepherdModel(ModelPart root) {
        super(root, CreatureLayers.PUFFCAP_SHEPHERD_ONLY);
        body = part("body");
        cap = part("cap");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        crook = part("crook");
        sac = part("sac");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float stride = Mth.cos(pos * 0.9F) * 1.1F * speed;
        legRight.xRot += stride;
        legLeft.xRot -= stride;
        armLeft.xRot -= stride * 0.6F;
        armRight.xRot += stride * 0.3F;
        body.zRot += Mth.sin(pos * 0.45F) * 0.05F * speed;                  // a side-to-side waddle
        body.y += Math.abs(Mth.sin(pos * 0.9F)) * -0.6F * speed;
        cap.zRot += Mth.sin(t * 0.08F) * 0.03F;
        cap.xRot += Mth.sin(t * 0.06F + 1.0F) * 0.02F + 0.06F * speed;       // the cap lags behind as it walks
        sac.xRot += Mth.sin(pos * 0.9F) * 0.15F * speed;

        if (s.phase == Step.SWELL.ordinal()) {
            float k = smooth(s.progress * 1.25F);
            float throb = 0.05F * Mth.sin(s.phaseTime * 1.6F) * k;
            float grow = 1.0F + 0.4F * k + throb;
            cap.xScale = grow;
            cap.zScale = grow;
            cap.yScale = 1.0F + 0.6F * k + throb;
            cap.y -= 0.8F * k;                                              // lifts off the stem as it fills
            body.y += 1.2F * k;                                             // a squat, bracing for the puff
            armRight.zRot += 0.7F * k;                                      // crook held out, clear of the brim
            armLeft.zRot -= 0.8F * k;
            armLeft.xRot -= 0.4F * k;
            legRight.zRot += 0.15F * k;
            legLeft.zRot -= 0.15F * k;
        } else if (s.phase == Step.PUFF.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            float shrink = 1.0F - 0.22F * k;
            cap.xScale = shrink;
            cap.zScale = shrink;
            cap.yScale = 1.0F - 0.3F * k;
            cap.zRot += Mth.sin(s.phaseTime * 3.0F) * 0.18F * k;            // shakes the last spores out
            cap.xRot += Mth.cos(s.phaseTime * 3.0F) * 0.1F * k;
            armRight.zRot += 0.4F * k;
            armLeft.zRot -= 0.5F * k;
        } else if (s.phase == Step.BURST.ordinal()) {
            float k = envelope(s.progress, 0.08F, 0.25F);
            float sway = Mth.sin(s.phaseTime * 0.35F);
            cap.yScale = 1.0F - 0.35F * k;                                  // squashed flat
            cap.xScale = 1.0F + 0.12F * k;
            cap.zScale = 1.0F + 0.12F * k;
            cap.zRot += 0.4F * k + sway * 0.1F * k;                         // and tipped over to one side
            cap.xRot += 0.12F * k;
            body.zRot += sway * 0.14F * k;                                  // reeling
            body.xRot += 0.15F * k;
            armRight.zRot -= 0.25F * k;                                     // the crook sags against it
            crook.zRot += 0.2F * k;
            armLeft.zRot += 0.25F * k + sway * 0.2F * k;
        }
        if (s.attack > 0.0F) {                                              // a swat with the crook
            float a = Mth.sin(s.attack * Mth.PI);
            armRight.xRot -= a * 1.4F;
            armRight.zRot -= a * 0.4F;
        }
    }
}
