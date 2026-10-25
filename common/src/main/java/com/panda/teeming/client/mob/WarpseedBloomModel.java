package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.WarpseedBloom.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Warpseed Bloom: a stalk that sways and a bulb that breathes. For the tell the bulb swells and trembles, the stalk
 * draws back, the sepals peel open and the seed grows in the pore; the spit thrusts the bulb forward and squeezes it;
 * torn, the stalk bows over, the bulb hangs shrunken and limp and the sepals and side buds droop. It never walks, so
 * nothing here reads walk speed.
 */
public class WarpseedBloomModel extends TeemingModel {
    private static final int SEPALS = 6;
    private final ModelPart stem, upper, bulb, seed, branchLeft, branchRight;
    private final ModelPart[] sepals = new ModelPart[SEPALS];

    public WarpseedBloomModel(ModelPart root) {
        super(root, CreatureLayers.WARPSEED_BLOOM_ONLY);
        stem = part("stem");
        upper = part("stem_upper");
        bulb = part("bulb");
        seed = part("seed");
        branchLeft = part("branch_left");
        branchRight = part("branch_right");
        for (int i = 0; i < SEPALS; i++) sepals[i] = part("sepal_" + i + "_leaf");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float sway = Mth.sin(t * 0.06F);
        stem.zRot = sway * 0.02F;
        upper.zRot = sway * 0.035F;
        upper.xRot += Mth.sin(t * 0.045F) * 0.03F;
        float breathe = 0.5F + 0.5F * Mth.sin(t * 0.1F);
        float width = 1.0F + 0.03F * breathe;
        float height = width;
        for (int i = 0; i < SEPALS; i++) sepals[i].xRot += Mth.sin(t * 0.08F + i * 1.3F) * 0.03F;
        seed.visible = false;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 1.5F);                              // swollen by two thirds of the tell, then held
            float tremble = Mth.sin(s.phaseTime * 2.5F) * 0.035F * k;
            width = 1.0F + 0.34F * k + tremble;
            height = 1.0F + 0.24F * k - tremble;
            upper.xRot -= 12.0F * DEG * k;                                    // draws back
            stem.xRot -= 3.0F * DEG * k;
            bulb.zRot = tremble * 0.6F;
            for (ModelPart p : sepals) p.xRot -= 34.0F * DEG * k;             // the sepals peel open
            seed.visible = true;
            float grow = 0.4F + 0.6F * k + 0.08F * Mth.sin(s.phaseTime * 1.9F) * k;
            seed.xScale = grow;
            seed.yScale = grow;
            seed.zScale = grow;
        } else if (s.phase == Step.SPIT.ordinal()) {
            float e = envelope(s.progress, 0.1F, 0.6F);
            upper.xRot += 16.0F * DEG * e;                                    // thrusts forward
            stem.xRot += 4.0F * DEG * e;
            width = 1.0F - 0.14F * e;                                         // squeezed empty
            height = 1.0F + 0.06F * e;
            for (ModelPart p : sepals) p.xRot += 14.0F * DEG * e;             // and the sepals snap shut round it
        } else if (s.phase == Step.WILTED.ordinal()) {
            float e = envelope(s.progress, 0.08F, 0.12F);
            float droop = Mth.sin(s.phaseTime * 0.12F) * 0.03F * e;
            stem.xRot += 10.0F * DEG * e;
            upper.xRot += 40.0F * DEG * e + droop;                            // bows over
            bulb.xRot += 26.0F * DEG * e;                                     // the bulb hangs
            bulb.zRot = 12.0F * DEG * e;                                      // split, it lolls to one side
            width = 1.0F - 0.2F * e;                                          // shrunken and limp
            height = 1.0F - 0.26F * e;
            for (ModelPart p : sepals) p.xRot -= 18.0F * DEG * e;             // the sepals close limp round it
            branchLeft.zRot += 25.0F * DEG * e;                               // the side buds droop
            branchRight.zRot -= 25.0F * DEG * e;
        }
        bulb.xScale = width;
        bulb.zScale = width;
        bulb.yScale = height;
    }
}
