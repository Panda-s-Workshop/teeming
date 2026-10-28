package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Spirebloom.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Spirebloom: a crystal growth that barely stirs at rest, a faint shimmer through its crown. For the tell the crown
 * draws in round the spire and shivers, the spire rises out of the mound and trembles, and the front root (the crack the
 * line runs from) stretches out along the ground; for the burst the crown snaps open and the spire jolts up, then
 * settles. It never walks, so nothing here reads walk speed.
 */
public class SpirebloomModel extends TeemingModel {
    private static final int SPIKES = 5, PETALS = 5, BUDS = 3;
    private final ModelPart spire, spireMid, frontRoot;
    private final ModelPart[] spikes = new ModelPart[SPIKES];
    private final ModelPart[] petals = new ModelPart[PETALS];
    private final ModelPart[] buds = new ModelPart[BUDS];

    public SpirebloomModel(ModelPart root) {
        super(root, CreatureLayers.SPIREBLOOM_ONLY);
        spire = part("spire");
        spireMid = part("spire_mid");
        frontRoot = part("root_0");
        for (int i = 0; i < SPIKES; i++) spikes[i] = part("spike_" + i);
        for (int i = 0; i < PETALS; i++) petals[i] = part("petal_" + i);
        for (int i = 0; i < BUDS; i++) buds[i] = part("bud_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float spread = 0.0F;        // degrees the crown leans out (+) or draws in (-)
        float shiver = 0.0F;
        float rise = 0.0F;          // pixels the spire rises
        for (int i = 0; i < SPIKES; i++) spikes[i].xRot += Mth.sin(t * 0.05F + i * 1.7F) * 0.012F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 1.4F);                          // drawn in by two thirds of the tell, then held
            spread = -14.0F * k;
            shiver = 0.06F * k;
            rise = 2.0F * k;
            frontRoot.zScale = 1.0F + 0.8F * k;                           // the crack stretches out ahead
            frontRoot.yScale = 1.0F + 0.6F * k;
        } else if (s.phase == Step.BURST.ordinal()) {
            float e = envelope(s.progress, 0.08F, 0.7F);
            spread = 22.0F * e;
            rise = 3.0F * e;
            frontRoot.zScale = 1.0F + 0.8F * (1.0F - s.progress);
        }
        float tremble = Mth.sin(s.phaseTime * 2.7F) * shiver;
        spire.y -= rise;
        spire.zRot += tremble * 0.5F;
        spireMid.zRot -= tremble * 0.4F;
        for (int i = 0; i < SPIKES; i++) {
            spikes[i].xRot += spread * DEG + Mth.sin(s.phaseTime * 3.1F + i * 2.1F) * shiver;
        }
        for (int i = 0; i < PETALS; i++) {
            petals[i].xRot += spread * DEG + Mth.sin(s.phaseTime * 3.1F + i * 2.1F) * shiver;
        }
        for (int i = 0; i < BUDS; i++) buds[i].xRot += spread * 0.5F * DEG + Mth.sin(s.phaseTime * 2.9F + i) * shiver;
    }
}
