package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Lavaleaper;
import com.panda.teeming.mob.nether.Lavaleaper.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Lavaleaper: an eel's travelling wave down the body and tail that quickens as it swims, the body pitched along its
 * flight. In the tell it coils like a spring under the surface: nose up, tail curled under, jaw opening, spines
 * bristling and the tail quivering. In the leap it stretches out, jaws wide and fins swept back; flopping back it
 * thrashes its tail. Yanked out on a line it flails; helpless on land it lies on its side gasping and slapping its
 * tail, and stranded between hops it wriggles on its side too.
 */
public class LavaleaperModel extends TeemingModel {
    private final ModelPart body, head, jaw, gland, finRight, finLeft, tail1, tail2, tailFin;
    private final ModelPart[] spines;

    public LavaleaperModel(ModelPart root) {
        super(root, CreatureLayers.LAVALEAPER_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        gland = part("gland");
        finRight = part("fin_right");
        finLeft = part("fin_left");
        tail1 = part("tail1");
        tail2 = part("tail2");
        tailFin = part("tail_fin");
        spines = new ModelPart[] {part("dorsal_0"), part("dorsal_1"), part("dorsal_2")};
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean stranded = s.flag(Lavaleaper.STRANDED);
        float swim = stranded ? 0.0F : speed;
        float w = t * 0.3F + pos * 1.2F;
        float amp = 0.15F + 0.3F * swim;
        body.yRot += amp * 0.25F * Mth.sin(w);
        head.yRot -= amp * 0.3F * Mth.sin(w);
        tail1.yRot += amp * Mth.sin(w - 1.0F);
        tail2.yRot += amp * 1.3F * Mth.sin(w - 2.0F);
        tailFin.yRot += amp * 1.4F * Mth.sin(w - 3.0F);
        body.xRot = s.xRot * DEG;                                      // nose along its flight
        finRight.zRot += Mth.sin(t * 0.25F) * 0.15F;
        finLeft.zRot -= Mth.sin(t * 0.25F) * 0.15F;
        jaw.xRot = 0.04F + 0.04F * Mth.sin(t * 0.09F);
        gland.yScale = 1.0F + 0.05F * Mth.sin(t * 0.12F);              // the gland swells with each breath

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 4.0F);
            float quiver = Mth.sin(s.phaseTime * 2.6F);
            body.xRot -= 0.3F * k;                                     // nose up towards the surface
            head.xRot = -0.2F * k;
            tail1.xRot = 0.4F * k;                                     // tail curled under: a spring, ready to go
            tail2.xRot = 0.45F * k;
            tailFin.xRot = 0.3F * k;
            tail1.yRot += quiver * 0.12F * k;
            tail2.yRot += quiver * 0.18F * k;
            jaw.xRot = 0.15F + 0.35F * k + 0.05F * quiver * k;         // the jaw opens
            gland.yScale = 1.0F + 0.25F * k;                           // and the gland swells
            for (ModelPart sp : spines) sp.yScale = 1.0F + 0.3F * k;   // spines bristle
            finRight.zRot -= 0.5F * k;
            finLeft.zRot += 0.5F * k;
        } else if (s.phase == Step.LEAP.ordinal()) {
            jaw.xRot = 0.9F;                                           // jaws wide
            head.xRot = -0.3F;                                         // snout thrown up: the gape faces forward
            tail1.xRot = -0.18F;                                       // the body bows, tail high
            tail2.xRot = -0.22F;
            tail1.yRot += Mth.sin(s.phaseTime * 1.8F) * 0.25F;
            tail2.yRot += Mth.sin(s.phaseTime * 1.8F - 1.0F) * 0.35F;
            finRight.yRot += 0.6F;                                     // pectorals swept back
            finLeft.yRot -= 0.6F;
            for (ModelPart sp : spines) sp.yScale = 1.25F;
        } else if (s.phase == Step.RETURN.ordinal()) {
            float thrash = Mth.sin(s.phaseTime * 1.6F);
            body.zRot = 0.35F * thrash;                                // twisting back over
            tail1.yRot += 0.55F * thrash;
            tail2.yRot += 0.7F * Mth.sin(s.phaseTime * 1.6F - 0.9F);
            tailFin.yRot += 0.5F * Mth.sin(s.phaseTime * 1.6F - 1.8F);
            tail1.xRot = 0.2F;
            jaw.xRot = 0.35F;
        } else if (s.phase == Step.YANKED.ordinal()) {
            float flail = Mth.sin(s.phaseTime * 2.2F);
            body.zRot = 0.7F * flail;                                  // flailing on the line
            head.xRot = -0.4F;                                         // dragged by the jaw
            jaw.xRot = 0.6F;
            tail1.yRot += 0.6F * flail;
            tail2.yRot += 0.8F * Mth.sin(s.phaseTime * 2.2F - 1.0F);
            finRight.zRot -= 0.6F;
            finLeft.zRot += 0.6F;
        } else if (s.phase == Step.HELPLESS.ordinal()) {
            onSide(s.phaseTime, 1.0F);
        } else if (stranded) {
            onSide(t, 0.7F);
        }
    }

    /** Lying on its side, gasping, slapping its tail and arching in a flop ({@code vigour} 0..1). */
    private void onSide(float time, float vigour) {
        body.zRot = 1.35F;
        body.y -= 0.5F;
        float slap = Mth.sin(time * 0.9F);
        float arch = Math.max(0.0F, Mth.sin(time * 0.63F));             // now and then it arches both ends up
        body.yRot = 0.25F * arch * vigour;
        head.yRot = -0.3F * arch * vigour + 0.1F * slap;
        tail1.yRot = (0.35F * slap - 0.35F * arch) * vigour;
        tail2.yRot = (0.45F * Mth.sin(time * 0.9F - 0.8F) - 0.3F * arch) * vigour;
        tailFin.yRot = 0.35F * Mth.sin(time * 0.9F - 1.6F) * vigour;
        jaw.xRot = 0.3F + 0.3F * Mth.sin(time * 0.35F);
        gland.yScale = 1.0F + 0.15F * Mth.sin(time * 0.35F);
        finRight.zRot -= 0.4F;
        finLeft.zRot += 0.4F;
    }
}
