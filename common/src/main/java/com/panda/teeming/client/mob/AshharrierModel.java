package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Ashharrier.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Ashharrier: slow, deep wingbeats; a soaring glide on flat broad wings with upturned fingers while it circles; the
 * cry (it stops, beak wide, head thrown forward, its wings folding back along its sides as the cry runs on); the
 * stoop (wings tucked tight, body along the line of the fall, talons swung forward at the end); the lift (hard, fast
 * beats on spread wings, body upright, legs straight down gripping); a sprawl on the ground with its head lolling while
 * it is stunned; and hard beats on the climb away. The wings are flat cards with their trailing edges tipped down, so
 * from below, where it is met, their whole undersides and ember rims show.
 */
public class AshharrierModel extends TeemingModel {
    private final ModelPart body, head, jaw, tail, wingRight, wingLeft, tipRight, tipLeft, legRight, legLeft;

    public AshharrierModel(ModelPart root) {
        super(root, CreatureLayers.ASHHARRIER_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        tail = part("tail");
        wingRight = part("wing_right");
        wingLeft = part("wing_left");
        tipRight = part("wing_right_tip");
        tipLeft = part("wing_left_tip");
        legRight = part("leg_right");
        legLeft = part("leg_left");
    }

    /** Both wings from their rest pose: roll (up is positive), sweep back, and the hands' own roll and sweep. */
    private void wings(float roll, float sweep, float tipRoll, float tipSweep) {
        wingRight.zRot += roll;
        wingLeft.zRot -= roll;
        wingRight.yRot += sweep;
        wingLeft.yRot -= sweep;
        tipRight.zRot += tipRoll;
        tipLeft.zRot -= tipRoll;
        tipRight.yRot += tipSweep;
        tipLeft.yRot -= tipSweep;
    }

    private void legs(float pitch) {
        legRight.xRot = pitch;
        legLeft.xRot = pitch;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        head.xRot = s.xRot * DEG * 0.3F;
        head.yRot = s.yRot * DEG * 0.5F;

        if (s.phase == Step.CIRCLE.ordinal()) {
            float burst = Math.max(0.0F, Mth.sin(t * 0.09F));             // soars, with a few deep beats now and then
            float flap = Mth.sin(t * 0.8F) * 0.45F * burst;
            wings(0.04F + flap, 0.0F, 0.12F + Mth.sin(t * 0.8F - 0.7F) * 0.2F * burst, 0.0F);   // fingers upturned
            body.zRot = -0.2F + Mth.sin(t * 0.06F) * 0.05F;               // banked into its turn
            body.y += Mth.sin(t * 0.3F) * 0.4F;
            tail.xScale = 1.25F;                                           // the tail spread to steer
            tail.zRot = 0.1F;
            legs(1.2F);
        } else if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);                          // the pose comes in over the first 0.3 s
            float fold = smooth(s.progress);                               // the wings fold back as the cry runs on
            body.xRot = 25.0F * DEG * k;                                   // tips nose-down at the target
            wings(0.55F * k - 0.25F * fold, 0.35F * k + 0.75F * fold, -0.5F * k - 0.3F * fold, 0.6F * fold);
            head.xRot = -30.0F * DEG * k;                                  // head up and forward, crying out
            head.yRot = 0.0F;
            head.zRot = Mth.sin(s.phaseTime * 1.9F) * 0.06F * k;           // the cry shakes it
            jaw.xRot = (0.8F + Mth.sin(s.phaseTime * 2.4F) * 0.12F) * k;  // beak wide
            tail.xRot += 0.3F * k;
            tail.xScale = 1.0F + 0.25F * k;
            legs(1.2F - 0.5F * k);
        } else if (s.phase == Step.DIVE.ordinal()) {
            float pitch = Mth.clamp(s.xRot, 30.0F, 85.0F) * DEG;
            float reach = smooth((s.progress - 0.15F) * 4.0F);             // the talons swing forward for the grab
            body.xRot = pitch;                                             // along the line of the fall
            wings(0.08F, 1.15F, -0.2F, 0.55F);                             // tucked tight along its sides
            head.xRot = -pitch * 0.3F;
            head.yRot = 0.0F;
            jaw.xRot = 0.2F;
            tail.xScale = 0.6F;
            legs(Mth.lerp(reach, 1.2F, -1.0F));
            legRight.zRot = 0.15F * reach;
            legLeft.zRot = -0.15F * reach;
        } else if (s.phase == Step.LIFT.ordinal()) {
            float beat = Mth.sin(t * 1.25F);
            wings(0.1F + beat * 0.85F, -0.1F, Mth.sin(t * 1.25F - 0.8F) * 0.5F, 0.0F);   // hard, fast beats
            body.xRot = -22.0F * DEG;                                      // upright, labouring with the weight
            body.y += beat * 0.6F;
            head.xRot = 20.0F * DEG;                                       // looking down at its catch
            head.yRot = 0.0F;
            jaw.xRot = 0.15F + Math.max(0.0F, beat) * 0.15F;
            tail.xRot += 0.5F;                                             // fanned down as a brake
            tail.xScale = 1.3F;
            legs(22.0F * DEG);                                             // straight down under it, gripping
            legRight.zRot = -0.12F;
            legLeft.zRot = 0.12F;
        } else if (s.phase == Step.CLIMB.ordinal()) {
            float hard = Mth.sin(t * 1.3F);
            wings(0.15F + hard * 0.75F, 0.0F, Mth.sin(t * 1.3F - 0.8F) * 0.45F, 0.0F);
            body.xRot = -15.0F * DEG;
            body.y += hard * 0.4F;
            legs(0.7F);
        } else if (s.phase == Step.STUNNED.ordinal()) {
            body.y += 2.5F;                                                // sprawled on the ground
            body.xRot = 8.0F * DEG;
            body.zRot = 0.2F;
            wings(-0.35F, 0.15F, -0.3F, 0.1F);                             // wings drooped open on the ground
            head.xRot = 25.0F * DEG;
            head.yRot = Mth.sin(s.phaseTime * 0.45F) * 0.5F;               // dazed
            head.zRot = Mth.sin(s.phaseTime * 0.3F) * 0.35F;
            jaw.xRot = 0.35F;
            legs(1.4F);
            legRight.zRot = 0.45F;
            legLeft.zRot = -0.45F;
        } else {
            float beat = Mth.sin(t * 0.7F);                                // slow, deep beats
            wings(beat * 0.6F, 0.0F, Mth.sin(t * 0.7F - 0.7F) * 0.35F, 0.0F);
            body.y += Mth.sin(t * 0.35F) * 0.5F;
            legs(1.2F);                                                    // tucked back in flight
        }
        if (s.attack > 0.0F) {                                             // a talon rake on a melee hit
            float a = Mth.sin(s.attack * Mth.PI);
            legs(Mth.lerp(a, legRight.xRot, -1.1F));
            head.xRot += a * 0.4F;
        }
    }
}
