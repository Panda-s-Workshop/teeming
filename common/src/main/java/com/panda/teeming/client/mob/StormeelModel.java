package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Stormeel;
import com.panda.teeming.mob.overworld.Stormeel.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Stormeel: a travelling wave down the body that quickens as it swims, the body pitched with its heading; in the tell
 * it curls into an S and quivers with its fins bristling and jaw open, in the discharge it snaps rigid and trembles.
 * Out of water it lies on its side and slaps its tail.
 */
public class StormeelModel extends TeemingModel {
    private final ModelPart body, head, jaw, finRight, finLeft, tailFin;
    /** The body chain from the neck to the tail; each one bends on top of the one before it. */
    private final ModelPart[] chain;
    private final ModelPart[] fins;

    public StormeelModel(ModelPart root) {
        super(root, CreatureLayers.STORMEEL_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        finRight = part("fin_right");
        finLeft = part("fin_left");
        tailFin = part("tail_fin");
        chain = new ModelPart[] {part("seg1"), part("seg2"), part("seg3"), part("tail")};
        fins = new ModelPart[] {part("dorsal_0"), part("dorsal_1"), part("dorsal_2"), part("dorsal_3"), part("ventral_2"), part("ventral_3")};
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean beached = s.flag(Stormeel.BEACHED);
        float swim = beached ? 0.0F : speed;
        float w = t * 0.22F + pos * 0.9F;                                  // the wave runs faster the further it swims
        float amp = 0.12F + 0.16F * swim;
        float[] wave = new float[chain.length];
        for (int i = 0; i < chain.length; i++) wave[i] = amp * (1.0F + 0.15F * i) * Mth.sin(w - 1.0F * (i + 1));

        if (beached && s.phase == Step.IDLE.ordinal()) {
            body.zRot = 1.35F;                                             // lying on its side
            for (int i = 0; i < chain.length; i++) chain[i].yRot += 0.45F * Mth.sin(t * 0.9F - i * 0.7F);   // on its side, a bend is a slap
            head.yRot += 0.25F * Mth.sin(t * 0.9F + 0.7F);
            jaw.xRot = 0.3F + 0.2F * Mth.sin(t * 0.5F);
            tailFin.yRot += 0.3F * Mth.sin(t * 0.9F - 3.2F);
            return;
        }

        body.yRot += amp * 0.4F * Mth.sin(w);
        head.yRot += -amp * 0.5F * Mth.sin(w) + s.yRot * DEG * 0.3F;
        body.xRot = s.xRot * DEG;                                          // nose up or down with its heading
        for (int i = 0; i < chain.length; i++) chain[i].yRot += wave[i];
        tailFin.yRot += amp * 0.8F * Mth.sin(w - 4.8F);
        finRight.zRot += Mth.sin(t * 0.5F) * 0.25F;
        finLeft.zRot -= Mth.sin(t * 0.5F) * 0.25F;
        jaw.xRot = 0.03F + 0.03F * Mth.sin(t * 0.1F);

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 8.0F);
            float q = 0.05F * (0.4F + s.progress);
            float[] coil = {0.55F, -0.8F, 0.75F, -0.65F};                  // an S, ready to let go
            for (int i = 0; i < chain.length; i++)
                chain[i].yRot += (coil[i] - wave[i]) * k + Mth.sin(s.phaseTime * 3.3F + i * 1.7F) * q * k;
            body.yRot += -0.3F * k;
            head.yRot += 0.25F * k;
            head.xRot = -0.15F * k;
            jaw.xRot = 0.35F * k + Mth.sin(s.phaseTime * 2.1F) * 0.05F * k;
            for (ModelPart f : fins) f.yScale = 1.0F + 0.25F * k;           // fins bristle
            finRight.zRot -= 0.35F * k;
            finLeft.zRot += 0.35F * k;
        } else if (s.phase == Step.PULSE.ordinal()) {
            float k = 1.0F - smooth((s.progress - 0.5F) * 2.0F);              // holds rigid, then relaxes
            for (int i = 0; i < chain.length; i++)
                chain[i].yRot -= (chain[i].yRot - Mth.sin(s.phaseTime * 6.0F + i) * 0.08F) * k;   // straight as a rod
            body.yRot *= 1.0F - k;
            head.yRot *= 1.0F - k;
            head.xRot = -0.25F * k;
            jaw.xRot = 0.5F * k;
            for (ModelPart f : fins) f.yScale = 1.0F + 0.4F * k;
            finRight.zRot -= 0.5F * k;
            finLeft.zRot += 0.5F * k;
            tailFin.yRot *= 1.0F - k;
        }
        if (s.attack > 0.0F) {
            float a = Mth.sin(s.attack * Mth.PI);
            jaw.xRot += 0.7F * a;
            head.xRot += 0.2F * a;
        }
    }
}
