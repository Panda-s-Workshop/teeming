package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Gleamtrap.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Gleamtrap: one block, split round its middle into a base and a lid hinged at the back. Disguised it holds perfectly
 * still, a shroomlight on the block grid: nothing breathes or sways. A pulse swells the block a touch and lifts the
 * lid a crack, so the glowing throat shows along the seam. The tell splits it wide open, teeth showing, trembling and
 * hunched; the bite slams the lid shut in a short lunge and hop at its prey; then it settles, the lid easing closed.
 */
public class GleamtrapModel extends TeemingModel {
    private static final float OPEN = 58.0F * DEG;
    private final ModelPart body, lid;

    public GleamtrapModel(ModelPart root) {
        super(root, CreatureLayers.GLEAMTRAP_ONLY);
        body = part("body");
        lid = part("lid");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        scale(1.0F, 1.0F);
        if (s.phase == Step.PULSE.ordinal()) {
            float k = Mth.sin(s.progress * Mth.PI);                       // up and back down over the pulse
            lid.xRot = -4.0F * DEG * k;                                    // a crack along the seam, the throat glowing through
            scale(1.0F + 0.025F * k, 1.0F + 0.025F * k);
        } else if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);                          // wide open by 40 % of the tell, then held
            float tremble = Mth.sin(s.phaseTime * 2.6F) * 0.035F * k;
            lid.xRot = -OPEN * k + tremble;
            body.zRot = tremble * 0.5F;
            scale(1.0F + 0.04F * k, 1.0F - 0.06F * k);                    // hunched low and wide, ready to spring
        } else if (s.phase == Step.BITE.ordinal()) {
            float shut = smooth(s.progress * 4.0F);                       // slammed shut in the first quarter
            float e = envelope(s.progress, 0.08F, 0.6F);
            lid.xRot = -OPEN * (1.0F - shut);
            body.z -= 3.0F * e;                                           // lunges at its prey
            body.y -= 1.5F * e;                                           // and hops with the snap
            body.xRot = 10.0F * DEG * e;
        } else if (s.phase == Step.RECOVER.ordinal()) {
            float left = 1.0F - smooth(s.progress);
            lid.xRot = -10.0F * DEG * left;                               // the lid eases closed
            scale(1.0F, 1.0F - 0.04F * left);
        }
    }

    /** Scales the whole block about its foot: {@code across} on both level axes, {@code up} upright. */
    private void scale(float across, float up) {
        body.xScale = across;
        body.zScale = across;
        body.yScale = up;
    }
}
