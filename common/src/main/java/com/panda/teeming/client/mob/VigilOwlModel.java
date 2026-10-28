package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.VigilOwl.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Vigil Owl: a squat perched owl whose disc face swivels far round on its shoulders to stay on whoever it watches. It
 * waddles with a side-to-side roll and hops. The stare leans it forward a touch, hunched, its face fixed on its quarry,
 * its huge eyes widening as the stare fills; the call throws its head up and back with both wings flung wide and the
 * tail fanned; fluttering off it beats its wings fast, leaning into the run; before a peck it draws its head back and
 * down with its eyes narrowed, then thrusts it forward.
 */
public class VigilOwlModel extends TeemingModel {
    private final ModelPart body, head, eyeRight, eyeLeft, wingRight, wingLeft, tail;

    public VigilOwlModel(ModelPart root) {
        super(root, CreatureLayers.VIGIL_OWL_ONLY);
        body = part("body");
        head = part("head");
        eyeRight = part("eye_right");
        eyeLeft = part("eye_left");
        wingRight = part("wing_right");
        wingLeft = part("wing_left");
        tail = part("tail");
    }

    /** Both wings from their rest pose: flung out (up is positive) and swung forward. */
    private void wings(float out, float forward) {
        wingRight.zRot += out;
        wingLeft.zRot -= out;
        wingRight.xRot -= forward;
        wingLeft.xRot -= forward;
    }

    /** Both eyes scaled from their rest size: across and up. */
    private void eyes(float across, float up) {
        eyeRight.xScale = across;
        eyeLeft.xScale = across;
        eyeRight.yScale = up;
        eyeLeft.yScale = up;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // the head follows the look far round; the body waddles and bobs as it hops about
        head.yRot = s.yRot * DEG;
        head.xRot = s.xRot * DEG * 0.6F;
        body.zRot = Mth.sin(pos * 0.9F) * 0.12F * speed;
        body.y -= Math.abs(Mth.sin(pos * 0.9F)) * 1.2F * speed;
        body.xRot = Mth.sin(t * 0.08F) * 0.02F;                            // a slow breath
        eyes(1.0F, 1.0F);

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 3.0F);                           // settles into the stare in its first third
            float wide = 1.0F + 0.35F * smooth(s.progress);                // the eyes widen as the stare fills
            body.xRot += 0.12F * k;                                        // leaning in, hunched
            head.xRot -= 0.12F * k;
            head.zRot = Mth.sin(s.phaseTime * 0.6F) * 0.04F * (1.0F - s.progress);   // a slight tilt that stills
            wings(0.12F * k, 0.0F);                                        // shoulders lifted off the body
            tail.xRot += 0.2F * k;
            eyes(wide, wide);
        } else if (s.phase == Step.MARK.ordinal()) {
            float k = envelope(s.progress, 0.2F, 0.35F);
            head.xRot -= 0.55F * k;                                        // head thrown up: the call
            body.xRot -= 0.15F * k;
            wings(1.25F * k, 0.25F * k);                                   // wings flung wide
            tail.xRot -= 0.5F * k;                                         // the tail fanned up
            float wide = 1.35F + 0.1F * k;
            eyes(wide, wide);
        } else if (s.phase == Step.FLEE.ordinal()) {
            float beat = Mth.sin(t * 2.2F);
            wings(1.0F + beat * 0.7F, 0.2F);                               // fast beats
            body.xRot += 0.35F;                                            // leaning into the run
            head.xRot -= 0.25F;
            tail.xRot -= 0.3F;
        } else if (s.phase == Step.PECK_TELL.ordinal()) {
            float k = smooth(s.progress * 1.5F);
            head.xRot += 0.25F * k;                                        // head drawn down and back
            head.z += 1.2F * k;
            body.xRot -= 0.18F * k;                                        // rocked back on its talons
            wings(0.25F * k, 0.0F);
            eyes(1.0F, 1.0F - 0.4F * k);                                   // eyes narrowed to a glare
        } else if (s.phase == Step.PECK.ordinal()) {
            float k = Mth.sin(Mth.clamp(s.progress, 0.0F, 1.0F) * Mth.PI);
            head.xRot += 0.25F * k;                                        // the head thrust forward and down
            head.z -= 1.2F * k;
            body.xRot += 0.15F * k;
            wings(0.3F * k, 0.0F);
            eyes(1.0F, 0.7F);
        } else {
            float ruffle = Mth.sin(t * 0.05F) > 0.96F ? Mth.sin(t * 1.6F) * 0.15F : 0.0F;
            wings(ruffle, 0.0F);                                           // a ruffle of the wings now and then
            if (speed > 0.1F) wings(0.15F * speed, 0.0F);                  // wings a little out to balance the hops
        }
    }
}
