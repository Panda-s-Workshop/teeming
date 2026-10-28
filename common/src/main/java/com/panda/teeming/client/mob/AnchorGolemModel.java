package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.AnchorGolem;
import com.panda.teeming.mob.end.AnchorGolem.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Anchor Golem: a slow, heavy stride, the boulder fist swinging and the anchor held still at its side, its crown
 * scraping the ground. Tell: it plants its feet, leans back and heaves the anchor up over its right shoulder until it
 * hangs above its head, the anchor rattling on its chain as the tell runs out. Swing: it brings the anchor down in front
 * of it in one blow, bent right over, the crown on the ground, and hauls it back up over the rest of the swing. Held in
 * a sink (a synced flag) the anchor lies flat behind it on the ground and it leans away, straining against it.
 */
public class AnchorGolemModel extends TeemingModel {
    private final ModelPart body, head, armRight, armLeft, forearmRight, anchor, legRight, legLeft;

    public AnchorGolemModel(ModelPart root) {
        super(root, CreatureLayers.ANCHOR_GOLEM_ONLY);
        body = part("body");
        head = part("head");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        forearmRight = part("forearm_right");
        anchor = part("anchor");
        legRight = part("leg_right");
        legLeft = part("leg_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean mired = s.flag(AnchorGolem.FLAG_MIRED);
        // the stride: short, heavy steps with a roll of the shoulders; the fist swings, the anchor barely does
        float w = pos * 0.45F;
        float stride = Mth.cos(w) * 0.7F * speed;
        legRight.xRot += stride;
        legLeft.xRot -= stride;
        armLeft.xRot -= stride * 0.7F;
        armRight.xRot += stride * 0.15F;
        anchor.xRot += -stride * 0.2F;
        body.zRot += Mth.cos(w) * 0.05F * speed;
        body.y += Mth.abs(Mth.sin(w)) * 0.6F * speed + Mth.sin(t * 0.05F) * 0.25F;   // each step settles; slow breaths
        head.yRot += s.yRot * DEG * 0.6F;
        head.xRot += s.xRot * DEG * 0.5F;
        float tm = s.phaseTime;

        if (s.phase == Step.TELL.ordinal()) {
            float up = smooth(tm / 6.0F);                                       // heaved up in its first 6 ticks, then held
            float late = smooth((s.progress - 0.55F) / 0.45F);
            float rattle = Mth.sin(tm * 2.7F) * 0.06F * (0.3F + late);
            armRight.xRot += -2.88F * up;                                        // straight up over its shoulder
            armRight.zRot += 0.17F * up;
            forearmRight.xRot += -0.17F * up;
            anchor.xRot += 0.6F * up + rattle;                                   // hanging back behind its head, rattling
            anchor.zRot += rattle * 0.6F;
            body.xRot += -0.14F * up;                                            // leaning back under the weight
            head.xRot += -0.2F * up;
            armLeft.xRot += -0.5F * up;                                          // the fist out for balance
            armLeft.zRot += -0.17F * up;
            legRight.xRot += 0.15F * up;                                         // its feet planted wide
            legLeft.xRot += -0.25F * up;
            body.y += 0.6F * up;
        } else if (s.phase == Step.SWING.ordinal()) {
            float down = smooth(tm / 2.0F);                                      // down in two ticks
            float back = smooth((s.progress - 0.45F) / 0.55F);                  // held, then hauled back up
            float k = down * (1.0F - back);
            armRight.xRot += -0.6F * k;
            forearmRight.xRot += -0.09F * k;
            anchor.xRot += -0.8F * k;                                            // its crown on the ground in front
            armRight.zRot += -0.07F * k;
            body.xRot += 0.49F * k;                                              // bent right over the blow
            head.xRot += 0.25F * k;
            armLeft.xRot += 0.45F * k;
            body.y += 2.5F * k;
            legRight.xRot += -0.25F * k;
            legLeft.xRot += 0.2F * k;
        } else if (mired) {
            float strain = Mth.sin(t * 0.5F) * 0.04F;
            armRight.xRot += 0.45F + strain;                                     // pulled back and down by its anchor
            armRight.zRot += 0.1F;
            anchor.xRot += 1.2F;                                                 // lying flat behind it on the ground
            body.xRot += 0.12F;
            body.zRot += -0.1F + strain;                                         // leaning away, straining against it
            head.xRot += 0.15F;
        }
    }
}
