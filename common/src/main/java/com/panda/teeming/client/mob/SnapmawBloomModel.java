package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.SnapmawBloom.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Snapmaw Bloom: a stalk that sways and jaws that breathe; for the tell the head rears back, the jaws gape and the
 * glowing seed swells in the mouth; the spit thrusts the head forward with the jaws pursed; the snap lunges and clamps.
 * It never walks, so nothing here reads walk speed.
 */
public class SnapmawBloomModel extends TeemingModel {
    private static final int PETALS = 5;
    private final ModelPart stem, neck, head, upper, lower, seed;
    private final ModelPart[] petals = new ModelPart[PETALS];

    public SnapmawBloomModel(ModelPart root) {
        super(root, CreatureLayers.SNAPMAW_BLOOM_ONLY);
        stem = part("stem");
        neck = part("neck");
        head = part("head");
        upper = part("jaw_upper");
        lower = part("jaw_lower");
        seed = part("seed");
        for (int i = 0; i < PETALS; i++) petals[i] = part("petal_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float sway = Mth.sin(t * 0.07F);
        stem.zRot = sway * 0.025F;
        neck.zRot = sway * 0.045F;
        neck.xRot += Mth.sin(t * 0.05F) * 0.035F;
        float breathe = 0.5F + 0.5F * Mth.sin(t * 0.11F);
        upper.xRot = -(2.0F + 3.0F * breathe) * DEG;
        lower.xRot = (1.0F + 2.0F * breathe) * DEG;
        for (int i = 0; i < PETALS; i++) petals[i].xRot += Mth.sin(t * 0.09F + i * 1.3F) * 0.03F;
        seed.visible = false;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);                          // wide open by 40 % of the tell, then held
            float tremble = Mth.sin(s.phaseTime * 2.4F) * 0.025F * k;
            neck.xRot -= 14.0F * DEG * k;                                 // rears back
            head.xRot += 4.0F * DEG * k;
            head.zRot = tremble;
            upper.xRot = -44.0F * DEG * k;
            lower.xRot = 32.0F * DEG * k;
            seed.visible = true;
            float grow = 0.35F + 0.65F * k + 0.06F * Mth.sin(s.phaseTime * 1.8F) * k;
            seed.xScale = grow;
            seed.yScale = grow;
            seed.zScale = grow;
            for (ModelPart p : petals) p.xRot -= 16.0F * DEG * k;        // the petals flare
        } else if (s.phase == Step.SPIT.ordinal()) {
            float e = envelope(s.progress, 0.1F, 0.6F);
            neck.xRot += 22.0F * DEG * e;                                 // thrusts forward
            stem.xRot += 5.0F * DEG * e;
            head.xRot -= 6.0F * DEG * e;
            upper.xRot = -12.0F * DEG;                                    // jaws pursed round the spat seed
            lower.xRot = 9.0F * DEG;
            for (ModelPart p : petals) p.xRot += 10.0F * DEG * e;
        } else if (s.phase == Step.SNAP.ordinal()) {
            float e = envelope(s.progress, 0.05F, 0.6F);
            neck.xRot += 30.0F * DEG * e;                                 // lunges
            stem.xRot += 8.0F * DEG * e;
            head.xRot += 10.0F * DEG * e;
            upper.xRot = 2.0F * DEG;                                      // clamped shut
            lower.xRot = -1.0F * DEG;
            head.zRot = Mth.sin(s.phaseTime * 3.0F) * 0.04F * e;          // worries the bite
        }
    }
}
