package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Pullstar;
import com.panda.teeming.mob.end.Pullstar.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pullstar: two loops of loose stones turn slowly round the heart while the whole knot bobs where it hangs. Bright, the
 * stones draw in tight and the heart throbs, swelling as it pulls; in a flare the stones clench and the heart swells
 * hard. In the tell the stones loosen and wobble outward and the heart shrinks, flickering; dim, the stones hang wide
 * and sag round a smaller guttered heart.
 */
public class PullstarModel extends TeemingModel {
    private static final float SPIN_A = 0.045F, SPIN_B = -0.06F;

    private final ModelPart body, heart, loopA, loopB, spinA, spinB;
    private final ModelPart[] stones;

    public PullstarModel(ModelPart root) {
        super(root, CreatureLayers.PULLSTAR_ONLY);
        body = part("body");
        heart = part("heart");
        loopA = part("loop_a");
        loopB = part("loop_b");
        spinA = part("loop_a_spin");
        spinB = part("loop_b_spin");
        stones = new ModelPart[8];
        for (int i = 0; i < 5; i++) stones[i] = part("loop_a_stone_" + i);
        for (int i = 0; i < 3; i++) stones[5 + i] = part("loop_b_stone_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        body.y += Mth.sin(t * 0.1F) * 0.6F;                                          // it hangs on nothing, bobbing
        float spread = 0.85F + 0.04F * Mth.sin(t * 0.2F);                             // drawn in tight while it pulls
        float swell = 1.0F + 0.12F * Mth.sin(t * 0.25F);                              // the heart throbs as it pulls
        float wobble = 0.0F, sag = 0.0F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress);
            spread = Mth.lerp(k, 0.85F, 1.25F);                                       // the stones loosen
            wobble = 0.12F * k;
            swell = Mth.lerp(k, 1.0F, 0.8F) * (s.stage == Pullstar.STAGE_DARK ? 0.85F : 1.0F);   // shrinking, flickering
        } else if (s.phase == Step.DIM.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.12F);
            spread = Mth.lerp(k, 1.25F, 1.35F);
            sag = 2.5F * k;                                                           // the stones hang low
            swell = Mth.lerp(k, 0.8F, 0.78F);                                        // a smaller guttered ember, gold
        } else if (s.flag(Pullstar.FLAG_FLARE)) {
            spread = 0.68F;                                                           // the stones clench
            swell = 1.45F + 0.08F * Mth.sin(t * 1.6F);                                // the heart blazes
            wobble = 0.04F;
        }

        spinA.yRot = t * SPIN_A;                                                     // a steady turn: a rate that changed would jump
        spinB.yRot = t * SPIN_B;
        loopA.y += sag;
        loopB.y += sag * 0.8F;
        for (int i = 0; i < stones.length; i++) {
            ModelPart st = stones[i];
            float jitter = 1.0F + wobble * Mth.sin(t * 1.7F + i * 2.1F);
            st.x *= spread * jitter;
            st.z *= spread * jitter;
            st.y += wobble * 8.0F * Mth.sin(t * 1.3F + i);
            st.xRot += t * 0.02F * (i % 2 == 0 ? 1 : -1);                    // each stone tumbles on its own
        }
        heart.yRot = t * 0.03F;
        heart.xScale = heart.yScale = heart.zScale = swell;
    }
}
