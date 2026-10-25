package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.MyceliteShambler;
import com.panda.teeming.mob.overworld.MyceliteShambler.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Mycelite Shambler: a heavy, rolling shamble on short legs, the hunched body swaying side to side, the long arms
 * swinging late and loose and the broad cap wobbling. Its swing clubs down with both arms. A fresh shambler standing up
 * out of a burst starts crouched with its arms flung out and its cap squashed, then straightens with a bounce. A dying
 * shambler that is about to burst swells its cap and spore sacs, throbbing faster, until the burst.
 */
public class MyceliteShamblerModel extends TeemingModel {
    private final ModelPart body, head, cap, legRight, legLeft, armRight, armLeft, handRight, handLeft;
    private final ModelPart sacBack, sacLow, sacShoulder;

    public MyceliteShamblerModel(ModelPart root) {
        super(root, CreatureLayers.MYCELITE_SHAMBLER_ONLY);
        body = part("body");
        head = part("head");
        cap = part("cap");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
        sacBack = part("sac_back");
        sacLow = part("sac_low");
        sacShoulder = part("sac_shoulder");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float step = pos * 0.55F;
        float stride = Mth.cos(step) * 0.7F * speed;
        legRight.xRot += stride;
        legLeft.xRot -= stride;
        body.zRot += Mth.cos(step) * 0.09F * speed;                          // the whole hunched body rolls with each step
        body.y += Math.abs(Mth.sin(step)) * -0.8F * speed;
        armRight.xRot -= Mth.cos(step - 0.8F) * 0.55F * speed;               // arms swing late and loose
        armLeft.xRot += Mth.cos(step - 0.8F) * 0.55F * speed;
        armRight.zRot += Mth.sin(t * 0.07F) * 0.04F + 0.05F * speed;
        armLeft.zRot -= Mth.sin(t * 0.07F + 1.3F) * 0.04F + 0.05F * speed;
        handRight.xRot -= 0.15F * speed;
        handLeft.xRot -= 0.15F * speed;
        head.yRot += s.yRot * DEG * 0.6F;
        head.xRot += s.xRot * DEG * 0.4F;
        cap.zRot += Mth.sin(t * 0.06F) * 0.035F + Mth.cos(step) * 0.05F * speed;   // the cap wobbles on its stem
        cap.xRot += Mth.sin(t * 0.05F + 0.7F) * 0.025F;
        body.xRot += Mth.sin(t * 0.045F) * 0.025F;                           // slow breathing, sacs pulsing with it
        float breathe = 1.0F + Mth.sin(t * 0.09F) * 0.05F;
        sacBack.xScale = breathe;
        sacBack.yScale = breathe;
        sacShoulder.xScale = breathe;
        sacShoulder.zScale = breathe;

        if (s.phase == Step.SPROUT.ordinal()) {
            float k = 1.0F - smooth(s.progress);                              // 1 just out of the burst, 0 standing
            float bounce = Mth.sin(s.progress * Mth.PI) * 0.15F;
            body.y += 4.0F * k;                                               // crouched low
            body.xRot += 0.45F * k;
            legRight.xRot -= 0.9F * k;                                        // knees bent under it
            legLeft.xRot -= 0.9F * k;
            legRight.zRot += 0.25F * k;
            legLeft.zRot -= 0.25F * k;
            armRight.zRot += 1.1F * k;                                        // arms flung out from the burst
            armLeft.zRot -= 1.1F * k;
            armRight.xRot -= 0.6F * k;
            armLeft.xRot -= 0.6F * k;
            head.xRot -= 0.5F * k;                                            // looking up out of the cloud
            cap.yScale = 1.0F - 0.35F * k + bounce;                           // squashed, springing back
            cap.xScale = 1.0F + 0.15F * k;
            cap.zScale = 1.0F + 0.15F * k;
        }
        if (s.deathTime > 0.0F && s.flag(MyceliteShambler.FLAG_BURSTING)) {
            float k = Mth.clamp(s.deathTime / 20.0F, 0.0F, 1.0F);
            float throb = Mth.sin(s.deathTime * (0.6F + k * 1.4F)) * 0.08F * k;
            float grow = 1.0F + 0.3F * k + throb;
            cap.xScale = grow;
            cap.zScale = grow;
            cap.yScale = 1.0F + 0.45F * k + throb;
            float sac = 1.0F + 0.8F * k + throb * 2.0F;
            sacBack.xScale = sac;
            sacBack.yScale = sac;
            sacBack.zScale = sac;
            sacLow.xScale = sac;
            sacLow.yScale = sac;
            sacLow.zScale = sac;
            sacShoulder.xScale = sac;
            sacShoulder.yScale = sac;
            sacShoulder.zScale = sac;
        }
        if (s.attack > 0.0F) {                                                // both arms club down
            float a = Mth.sin(s.attack * Mth.PI);
            float lift = Mth.sin(Mth.sqrt(s.attack) * Mth.PI);
            armRight.xRot -= 2.2F * lift - 0.4F * a;
            armLeft.xRot -= 2.2F * lift - 0.4F * a;
            armRight.zRot -= 0.2F * a;
            armLeft.zRot += 0.2F * a;
            body.xRot += 0.25F * a;
            head.xRot -= 0.2F * a;
        }
    }
}
