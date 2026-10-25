package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Dunefin;
import com.panda.teeming.mob.overworld.Dunefin.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Dunefin: a shark's side-to-side tail beat that quickens as it swims, the head countering it. In the tell the dorsal
 * fin grows taller and quivers and the back lifts toward the surface; in the breach the jaw gapes, the pectorals spread
 * and the body pitches from nose-up to nose-down over the leap; it dives nose first; beached it lies on its side,
 * gasping and slapping its tail. Stranded on hard ground between flops it lies on its side too.
 */
public class DunefinModel extends TeemingModel {
    private final ModelPart body, head, jaw, fin, finRight, finLeft, tail1, tail2, tailFin;

    public DunefinModel(ModelPart root) {
        super(root, CreatureLayers.DUNEFIN_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        fin = part("fin");
        finRight = part("fin_right");
        finLeft = part("fin_left");
        tail1 = part("tail1");
        tail2 = part("tail2");
        tailFin = part("tail_fin");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean under = s.flag(Dunefin.SUBMERGED);
        float swim = under ? speed : 0.0F;
        float w = t * 0.2F + pos * 0.9F;
        float amp = 0.12F + 0.22F * swim;
        body.yRot += amp * 0.25F * Mth.sin(w);
        head.yRot -= amp * 0.35F * Mth.sin(w);
        tail1.yRot += amp * Mth.sin(w - 1.0F);
        tail2.yRot += amp * 1.3F * Mth.sin(w - 2.0F);
        tailFin.yRot += amp * 1.5F * Mth.sin(w - 3.0F);
        finRight.zRot += Mth.sin(t * 0.15F) * 0.08F;
        finLeft.zRot -= Mth.sin(t * 0.15F) * 0.08F;
        jaw.xRot = 0.04F + 0.03F * Mth.sin(t * 0.08F);

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 6.0F);
            fin.yScale = 1.0F + 0.35F * k;                       // the fin grows out of the sand
            fin.zRot = Mth.sin(s.phaseTime * 2.4F) * 0.07F * k;  // and quivers
            body.y -= 2.0F * k;                                  // the back lifts toward the surface
            head.xRot = -0.12F * k;
            jaw.xRot = 0.15F * k;
            tail1.yRot += Mth.sin(s.phaseTime * 1.7F) * 0.3F * k;   // the tail beats hard, ready to go
            tail2.yRot += Mth.sin(s.phaseTime * 1.7F - 1.0F) * 0.35F * k;
        } else if (s.phase == Step.BREACH.ordinal()) {
            float p = s.progress;
            body.xRot = -0.8F + 1.5F * smooth(p);                // nose up as it leaves the sand, nose down as it falls
            body.y -= 3.0F;                                      // lifted clear of the ground line, so the open jaw shows
            jaw.xRot = 0.9F;                                     // jaws wide
            head.xRot = -0.5F;                                   // snout thrown up: the gape faces forward
            finRight.zRot -= 0.55F;                              // pectorals spread
            finLeft.zRot += 0.55F;
            tail1.xRot = -0.25F;                                 // the body bows, tail low
            tail2.xRot = -0.2F;
            tail1.yRot += Mth.sin(s.phaseTime * 1.4F) * 0.25F;
        } else if (s.phase == Step.DIVE.ordinal()) {
            float k = envelope(s.progress, 0.2F, 0.35F);
            body.xRot = 0.6F * k;                                // nose first into the sand, tail up
            tail1.xRot = 0.2F * k;
            tail2.xRot = 0.2F * k;
            tail1.yRot += Mth.sin(s.phaseTime * 1.6F) * 0.3F;
        } else if (s.phase == Step.BEACHED.ordinal()) {
            onSide(s.phaseTime, 0.5F);
        } else if (s.flag(Dunefin.STRANDED)) {
            onSide(t, 1.0F);
        }
    }

    /** Lying on its side, gasping and slapping its tail ({@code vigour} 0..1). */
    private void onSide(float time, float vigour) {
        body.zRot = 1.35F;
        body.y -= 0.5F;                                          // rolled over, its flank is a touch wider than its height
        jaw.xRot = 0.3F + 0.25F * Mth.sin(time * 0.3F);
        head.yRot = 0.12F * Mth.sin(time * 0.4F);
        tail1.yRot = 0.35F * vigour * Mth.sin(time * 0.8F);
        tail2.yRot = 0.45F * vigour * Mth.sin(time * 0.8F - 0.8F);
        tailFin.yRot = 0.3F * vigour * Mth.sin(time * 0.8F - 1.6F);
        finRight.zRot -= 0.3F;
        finLeft.zRot += 0.3F;
    }
}
