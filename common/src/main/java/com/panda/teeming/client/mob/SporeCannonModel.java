package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.SporeCannon;
import com.panda.teeming.mob.nether.SporeCannon.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Spore Cannon: a bulb that breathes slowly and drips that sway. For the tell the bulb swells and trembles, the collar
 * lifts and the drips shiver; firing squeezes the bulb hard once per spore and pops the vent up; stunned, the whole
 * cannon sags to one side and rocks with the bulb slumped. It never walks, so nothing here reads walk speed.
 */
public class SporeCannonModel extends TeemingModel {
    private static final int DRIPS = 7;
    private static final int WEEPING = 3;
    private final ModelPart stem, collar, bulb, vent;
    private final ModelPart[] drips = new ModelPart[DRIPS];
    private final ModelPart[] weeping = new ModelPart[WEEPING];

    public SporeCannonModel(ModelPart root) {
        super(root, CreatureLayers.SPORE_CANNON_ONLY);
        stem = part("stem");
        collar = part("collar");
        bulb = part("bulb");
        vent = part("vent");
        for (int i = 0; i < DRIPS; i++) drips[i] = part("drip_" + i);
        for (int i = 0; i < WEEPING; i++) weeping[i] = part("weeping_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float breathe = 0.5F + 0.5F * Mth.sin(t * 0.09F);
        float width = 1.0F + 0.03F * breathe;
        float height = width;
        for (int i = 0; i < DRIPS; i++) drips[i].xRot = Mth.sin(t * 0.08F + i * 1.7F) * 0.06F;
        for (int i = 0; i < WEEPING; i++) weeping[i].zRot = Mth.sin(t * 0.06F + i * 2.1F) * 0.05F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 1.6F);                              // swollen by 60 % of the tell, then held
            float tremble = Mth.sin(s.phaseTime * 2.2F) * 0.035F * k;
            width = 1.0F + 0.26F * k + tremble;
            height = 1.0F + 0.22F * k - tremble;
            collar.y -= 1.0F * k;                                             // the collar lifts with the swelling
            vent.y -= 0.6F * k;
            for (int i = 0; i < DRIPS; i++) drips[i].zRot = Mth.sin(s.phaseTime * 3.0F + i) * 0.14F * k;
        } else if (s.phase == Step.FIRE.ordinal()) {
            // one hard squeeze per spore, every SPORE_GAP ticks, as the bulb empties over the volley
            float since = (s.phaseTime % SporeCannon.SPORE_GAP) / SporeCannon.SPORE_GAP;
            float kick = (1.0F - since) * (1.0F - since);
            float full = 1.26F - 0.24F * s.progress;
            width = full - 0.16F * kick;
            height = full - 0.06F - 0.12F * kick;
            vent.y -= 1.6F * kick;
            vent.xRot -= 0.25F * kick;                                        // the vent spits forward
            collar.y -= 0.8F * (1.0F - s.progress);
            stem.xRot += 0.04F * kick;                                        // and the whole cannon rocks back
        } else if (s.phase == Step.STUNNED.ordinal()) {
            float e = envelope(s.progress, 0.08F, 0.15F);
            float rock = Mth.sin(s.phaseTime * 0.3F) * 0.05F;
            stem.zRot = (0.2F + rock) * e;                                    // sags to one side and rocks there
            stem.xRot = 0.06F * e;
            collar.zRot = 0.1F * e;
            width = 1.0F + 0.08F * e;                                         // the bulb slumps, wide and flat
            height = 1.0F - 0.2F * e;
            vent.xRot += 0.45F * e;
            for (ModelPart d : drips) d.xRot *= 1.0F - e;                     // drips hang limp
        }
        bulb.xScale = width;
        bulb.zScale = width;
        bulb.yScale = height;
    }
}
