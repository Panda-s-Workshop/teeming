package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.PhantasmLantern.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Phantasm Lantern: it bobs and sways as it hovers, the cowl and its long tip lagging the bob, the flame flickering
 * and turning slowly in its cage, the lantern leaning a little into its drift. The conjuring tips the cowl back to bare
 * the flame, which swells and spins as the lantern rises and shivers, its fungus caps flaring out; the release bursts
 * the flame up through the bars and snaps the cowl forward, then everything settles.
 */
public class PhantasmLanternModel extends TeemingModel {
    private final ModelPart body, hood, tip, flame, finial, capLeft, capRight;

    public PhantasmLanternModel(ModelPart root) {
        super(root, CreatureLayers.PHANTASM_LANTERN_ONLY);
        body = part("body");
        hood = part("hood");
        tip = part("tip");
        flame = part("flame");
        finial = part("finial");
        capLeft = part("cap_left");
        capRight = part("cap_right");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float bob = Mth.sin(t * 0.12F);
        body.y += bob * 0.8F;                                                // it floats: a slow rise and fall
        body.zRot += Mth.sin(t * 0.07F) * 0.05F;
        body.xRot += Mth.sin(t * 0.09F + 1.0F) * 0.04F + speed * 0.2F;       // leaning into its drift
        hood.xRot += Mth.sin(t * 0.12F - 0.6F) * 0.05F;                      // the cowl lags the bob
        hood.yRot += s.yRot * DEG * 0.5F;                                    // and turns a little to look
        tip.xRot += Mth.sin(t * 0.12F - 1.2F) * 0.14F;
        finial.zRot += Mth.sin(t * 0.07F - 0.8F) * 0.12F;
        finial.xRot += Mth.sin(t * 0.09F - 0.2F) * 0.1F;
        capLeft.zRot += Mth.sin(t * 0.1F) * 0.05F;
        capRight.zRot -= Mth.sin(t * 0.1F + 0.7F) * 0.05F;
        float flicker = 1.0F + 0.07F * Mth.sin(t * 0.9F) + 0.05F * Mth.sin(t * 1.7F + 1.0F);
        flame.xScale = flicker;
        flame.zScale = flicker;
        flame.yScale = 1.0F + 0.12F * Mth.sin(t * 1.3F);
        flame.yRot += t * 0.05F;                                             // a slow turn

        if (s.phase == Step.CONJURE.ordinal()) {
            float k = envelope(s.progress, 0.3F, 0.0F);
            hood.xRot -= 0.6F * k;                                           // the cowl tips back off the flame
            tip.xRot -= 0.35F * k;
            body.y -= 1.2F * k;                                              // it rises a little
            body.zRot += Mth.sin(s.phaseTime * 2.3F) * 0.05F * k;            // and shivers
            capLeft.zRot += 0.35F * k;                                       // the fungus caps flare out
            capRight.zRot -= 0.35F * k;
            float grow = 1.0F + 0.45F * k;
            flame.xScale *= grow;
            flame.zScale *= grow;
            flame.yScale *= 1.0F + 0.6F * k;
            flame.yRot += s.phaseTime * 0.6F * k;                            // and it spins
        } else if (s.phase == Step.RELEASE.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            float burst = Mth.sin(Mth.clamp(s.progress * 2.0F, 0.0F, 1.0F) * Mth.PI);
            hood.xRot += -0.6F * k + 0.25F * burst;                          // the cowl snaps forward again
            tip.xRot -= 0.2F * k;
            body.y -= 1.2F * k - 0.6F * burst;                               // a recoil as the flame bursts
            capLeft.zRot += 0.35F * k;
            capRight.zRot -= 0.35F * k;
            float flare = 1.0F + 0.45F * k + 0.35F * burst;                  // up through the bars, then settling
            flame.xScale *= flare;
            flame.zScale *= flare;
            flame.yScale *= 1.0F + 0.6F * k + 0.5F * burst;
        }
    }
}
