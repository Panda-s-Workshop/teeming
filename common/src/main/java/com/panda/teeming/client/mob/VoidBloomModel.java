package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.VoidBloom.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Void Bloom: a crook that sways a little, a cup that hangs from it like a heavy bell, breathing, and drips that
 * stretch and draw back. For the tell the cup bows lower and throbs, swelling with each beat, and the drips run long.
 * Dying, the crook folds over and the cup shrinks and sags while the stain recedes. It never walks, so nothing here
 * reads walk speed.
 */
public class VoidBloomModel extends TeemingModel {
    private static final int DRIPS = 3;
    private final ModelPart stem, upper, neck, bend, cup;
    private final ModelPart[] drips = new ModelPart[DRIPS];

    public VoidBloomModel(ModelPart root) {
        super(root, CreatureLayers.VOID_BLOOM_ONLY);
        stem = part("stem");
        upper = part("stem_upper");
        neck = part("neck");
        bend = part("bend");
        cup = part("cup");
        for (int i = 0; i < DRIPS; i++) drips[i] = part("drip_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float sway = Mth.sin(t * 0.05F);
        stem.zRot = sway * 0.02F;
        upper.zRot = sway * 0.03F;
        neck.xRot += Mth.sin(t * 0.04F) * 0.03F;
        // the cup hangs like a bell: a slow swing across, a slower one fore and aft
        cup.zRot = Mth.sin(t * 0.07F + 0.6F) * 0.06F;
        cup.xRot += Mth.sin(t * 0.045F) * 0.04F;
        float swell = 1.0F + 0.03F * (0.5F + 0.5F * Mth.sin(t * 0.09F));
        float[] drip = new float[DRIPS];
        for (int i = 0; i < DRIPS; i++) drip[i] = 0.8F + 0.25F * (0.5F + 0.5F * Mth.sin(t * 0.11F + i * 2.1F));

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(Math.min(1.0F, s.progress * 3.0F));               // bowed by a third of the tell, then held
            float beat = Math.abs(Mth.sin(s.phaseTime * 0.8F));                // throbs, about twice through the tell
            neck.xRot += 10.0F * DEG * k;                                      // the crook bows lower
            bend.xRot += 8.0F * DEG * k;
            cup.xRot -= 18.0F * DEG * k;                                       // and the cup keeps hanging straight
            cup.zRot *= 1.0F - k;
            swell = 1.0F + (0.12F + 0.14F * beat) * k;
            for (int i = 0; i < DRIPS; i++) drip[i] = 1.0F + 0.8F * k + 0.15F * beat;
        }
        if (s.deathTime > 0.0F) {
            float d = Mth.clamp(s.deathTime / 40.0F, 0.0F, 1.0F);
            neck.xRot += 25.0F * DEG * d;                                      // the crook folds over
            bend.xRot += 20.0F * DEG * d;
            upper.xRot += 10.0F * DEG * d;
            swell = 1.0F - 0.35F * d;                                          // the cup shrinks as the stain goes
            for (int i = 0; i < DRIPS; i++) drip[i] *= 1.0F - 0.7F * d;
        }
        cup.xScale = swell;
        cup.zScale = swell;
        cup.yScale = 1.0F + (swell - 1.0F) * 0.6F;
        for (int i = 0; i < DRIPS; i++) drips[i].yScale = drip[i];
    }
}
