package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.SootManta.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Soot Manta: a slow glide in which each wingbeat runs out along the wing (root, middle, tip), the body bobbing against
 * it and pitching with its flight; the ripple (tell: wings raised, a fast wave running out along them, the cephalic
 * fins flared and the tail and its soot plume raised); one hard downbeat that sheds the cloud; quick, swept-back beats
 * while it flees; and the mouth opening on a bite.
 */
public class SootMantaModel extends TeemingModel {
    /**
     * The wings' trailing edges tipped down while it flies, so their faces turn toward a player in front of and below
     * it (where players stand), not only their edges.
     */
    private static final float WING_TILT = -12.0F * DEG;

    private final ModelPart body, jaw, finRight, finLeft, tail, plume;
    private final ModelPart wingRight, midRight, tipRight, wingLeft, midLeft, tipLeft;

    public SootMantaModel(ModelPart root) {
        super(root, CreatureLayers.SOOT_MANTA_ONLY);
        body = part("body");
        jaw = part("jaw");
        finRight = part("fin_right");
        finLeft = part("fin_left");
        tail = part("tail");
        plume = part("plume");
        wingRight = part("wing_right");
        midRight = part("wing_right_mid");
        tipRight = part("wing_right_tip");
        wingLeft = part("wing_left");
        midLeft = part("wing_left_mid");
        tipLeft = part("wing_left_tip");
    }

    /** Both wings: the roll of the root, middle and tip (up is positive), each on top of the rest pose. */
    private void wings(float inner, float mid, float tip) {
        wingRight.zRot += inner;
        wingLeft.zRot -= inner;
        midRight.zRot += mid;
        midLeft.zRot -= mid;
        tipRight.zRot += tip;
        tipLeft.zRot -= tip;
    }

    /** Both wings swept back (positive) or forward. */
    private void sweep(float a) {
        wingRight.yRot += a;
        wingLeft.yRot -= a;
    }

    /** Both cephalic fins turned out (positive) or curled in. */
    private void fins(float a) {
        finRight.yRot += a;
        finLeft.yRot -= a;
    }

    private void plumeScale(float k) {
        plume.xScale = k;
        plume.yScale = k;
        plume.zScale = k;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean ripple = s.phase == Step.TELL.ordinal();
        boolean shed = s.phase == Step.SHED.ordinal();
        boolean flee = s.phase == Step.FLEE.ordinal();
        float k = ripple ? envelope(s.progress, 0.15F, 0.1F) : 0.0F;
        float glide = 1.0F - k;

        float beat = flee ? t * 0.7F : t * 0.22F;
        float amp = flee ? 1.8F : 1.0F;
        wings(Mth.sin(beat) * 0.14F * amp * glide, Mth.sin(beat - 0.7F) * 0.09F * amp * glide, Mth.sin(beat - 1.4F) * 0.12F * amp * glide);
        wingRight.xRot += WING_TILT;
        wingLeft.xRot += WING_TILT;
        body.y += Mth.sin(beat + Mth.HALF_PI) * 0.6F;
        body.xRot = Mth.clamp(s.xRot, -30.0F, 30.0F) * DEG * 0.6F;      // nose down in a dive, up in a climb
        tail.yRot = Mth.sin(t * 0.15F) * 0.2F;
        tail.xRot += Mth.sin(t * 0.22F - 1.5F) * 0.08F;
        plume.yRot = Mth.sin(t * 0.3F) * 0.3F;
        fins(Mth.sin(t * 0.3F) * 0.05F);

        if (ripple) {
            float w = s.phaseTime * 1.3F;
            wings(0.15F * k + Mth.sin(w) * 0.1F * k, Mth.sin(w - 1.1F) * 0.22F * k, Mth.sin(w - 2.2F) * 0.32F * k);   // a wave runs out
            fins(0.5F * k);                                                     // fins flared
            tail.xRot += 0.45F * k;                                             // tail and plume raised
            plumeScale(1.0F + (0.35F + 0.15F * Mth.sin(s.phaseTime * 1.7F)) * k);
            body.zRot = Mth.sin(s.phaseTime * 2.5F) * 0.05F * k;               // a shiver through the body
        } else if (shed) {
            float d = 1.0F - smooth(s.progress);
            wings(-0.55F * d, -0.25F * d, -0.2F * d);                           // one hard downbeat
            sweep(-0.15F * d);                                                  // cupped forward round the soot
            body.y -= 1.5F * d;                                                 // lifted by it
            fins(-0.3F * d);
            plumeScale(1.0F - 0.4F * d);
        } else if (flee) {
            sweep(0.3F);                                                        // wings swept back, beating hard
            body.xRot += 0.15F;                                                 // head down, bolting
            fins(-0.3F);
            tail.xRot -= 0.1F;
        }
        if (s.attack > 0.0F) {                                                 // the bite
            float a = Mth.sin(s.attack * Mth.PI);
            jaw.xRot += 0.7F * a;
            body.xRot += 0.2F * a;
            fins(0.25F * a);
        }
    }
}
