package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Skreel.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Skreel: flapping flight, a banked glide with bursts of wingbeats while it circles, the screech (nose-down, wings
 * half-folded and raised, beak wide), a tucked stoop for the dive, hard wingbeats on the climb back, and a sprawl on
 * the ground with its head lolling while it is stunned. The wings are flat cards, so from below (where it is seen
 * while it circles) their whole undersides show.
 */
public class SkreelModel extends TeemingModel {
    /** The wings' roll at rest: a shallow V. */
    private static final float WING_REST = 6.0F * DEG;

    private final ModelPart body, head, jaw, tail, wingRight, wingLeft, tipRight, tipLeft, legRight, legLeft;

    public SkreelModel(ModelPart root) {
        super(root, CreatureLayers.SKREEL_ONLY);
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

    /** Both wings at once: roll (up is positive), sweep back, and the hands' own roll and sweep. */
    private void wings(float roll, float sweep, float tipRoll, float tipSweep) {
        wingRight.zRot = WING_REST + roll;
        wingLeft.zRot = -WING_REST - roll;
        wingRight.yRot = sweep;
        wingLeft.yRot = -sweep;
        tipRight.zRot = tipRoll;
        tipLeft.zRot = -tipRoll;
        tipRight.yRot = tipSweep;
        tipLeft.yRot = -tipSweep;
    }

    private void legs(float pitch) {
        legRight.xRot = pitch;
        legLeft.xRot = pitch;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float beat = Mth.sin(t * 0.9F);
        wings(beat * 0.55F, 0.0F, Mth.sin(t * 0.9F - 0.7F) * 0.3F, 0.0F);
        body.y += Mth.sin(t * 0.45F) * 0.6F;
        legs(1.15F);                                                       // tucked back in flight
        head.xRot = s.xRot * DEG * 0.3F;
        head.yRot = s.yRot * DEG * 0.5F;

        if (s.phase == Step.CIRCLE.ordinal()) {
            float burst = Math.max(0.0F, Mth.sin(t * 0.11F));             // glides, with a few wingbeats now and then
            float flap = Mth.sin(t * 1.1F) * 0.5F * burst;
            wings(0.05F + flap, 0.0F, -0.05F + Mth.sin(t * 1.1F - 0.7F) * 0.25F * burst, 0.0F);
            body.zRot = -0.22F + Mth.sin(t * 0.07F) * 0.05F;              // banked into its turn
            tail.xScale = 1.25F;                                           // the tail spread to steer
            tail.zRot = 0.12F;
        } else if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 3.0F);
            body.xRot = 35.0F * DEG * k;                                   // tips nose-down at the target
            wings(0.85F * k, 0.45F * k, -0.7F * k, 0.25F * k);              // half-folded and raised
            head.xRot = -25.0F * DEG * k;                                  // head up and forward, screaming
            head.yRot = 0.0F;
            jaw.xRot = (0.75F + Mth.sin(s.phaseTime * 2.2F) * 0.1F) * k;   // beak wide
            tail.xRot += 0.35F * k;
            tail.xScale = 1.0F + 0.3F * k;
            legs(1.15F - 0.6F * k);
        } else if (s.phase == Step.DIVE.ordinal()) {
            float pitch = Mth.clamp(s.xRot, 40.0F, 90.0F) * DEG;
            body.xRot = pitch;                                             // along the dive line
            wings(0.05F, 1.05F, -0.15F, 0.45F);                            // swept back along its sides into a stoop
            head.xRot = -pitch * 0.25F;
            head.yRot = 0.0F;
            jaw.xRot = 0.15F;
            tail.xScale = 0.6F;
            legs(-0.9F);                                                   // talons thrown forward
        } else if (s.phase == Step.CLIMB.ordinal()) {
            float hard = Mth.sin(t * 1.6F);
            wings(0.15F + hard * 0.8F, 0.0F, Mth.sin(t * 1.6F - 0.8F) * 0.45F, 0.0F);
            body.xRot = -15.0F * DEG;
            legs(0.6F);
        } else if (s.phase == Step.STUNNED.ordinal()) {
            body.y += 3.0F;                                                // sprawled on the ground
            body.xRot = 10.0F * DEG;
            body.zRot = 0.18F;
            wings(-0.4F, 0.2F, -0.35F, 0.1F);                              // wings drooped open
            head.xRot = 20.0F * DEG;
            head.yRot = Mth.sin(s.phaseTime * 0.45F) * 0.5F;               // dazed
            head.zRot = Mth.sin(s.phaseTime * 0.3F) * 0.35F;
            jaw.xRot = 0.3F;
            legs(1.4F);
            legRight.zRot = 0.4F;
            legLeft.zRot = -0.4F;
        }
        if (s.attack > 0.0F) {                                             // a talon rake on a melee hit
            float a = Mth.sin(s.attack * Mth.PI);
            legs(Mth.lerp(a, legRight.xRot, -1.1F));
            head.xRot += a * 0.4F;
        }
    }
}
