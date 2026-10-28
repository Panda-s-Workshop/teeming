package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Wingshear.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Wingshear: a stiff-winged glide with a few quick flicks now and then while it soars, fast flickering beats in the
 * chase, the screech (blade wings thrown up and spread wide, the tips flared, the bill wide open and the forked tail
 * fanned), a tight stoop with the blades swept back along its sides for the dive, hard beats as it pulls up, and a long
 * banked glide as it circles off. The body follows its flight's pitch.
 */
public class WingshearModel extends TeemingModel {
    /** The wings' roll at rest: a shallow V (the rig sets the same). */
    private static final float WING_REST = 8.0F * DEG;

    private final ModelPart body, head, jaw, tail, wingRight, wingLeft, tipRight, tipLeft;

    public WingshearModel(ModelPart root) {
        super(root, CreatureLayers.WINGSHEAR_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        tail = part("tail");
        wingRight = part("wing_right");
        wingLeft = part("wing_left");
        tipRight = part("wing_right_tip");
        tipLeft = part("wing_left_tip");
    }

    /** Both wings at once: roll (up is positive), sweep back (positive folds them toward the tail), and the tips' own. */
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

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float pitch = Mth.clamp(s.xRot, -60.0F, 80.0F) * DEG;
        body.xRot = pitch;
        body.y += Mth.sin(t * 0.2F) * 0.4F;
        // a glide on stiff blades, with a burst of three quick flicks every few seconds
        float burst = Math.max(0.0F, Mth.sin(t * 0.09F) - 0.7F) / 0.3F;
        float flick = Mth.sin(t * 1.6F) * 0.35F * burst;
        wings(flick, 0.0F, Mth.sin(t * 1.6F - 0.6F) * 0.15F * burst, 0.0F);
        head.xRot = -pitch * 0.4F;
        tail.xRot += Mth.sin(t * 0.13F) * 0.05F;

        if (s.phase == Step.CHASE.ordinal()) {
            float beat = Mth.sin(t * 1.5F);
            wings(beat * 0.4F, 0.25F, Mth.sin(t * 1.5F - 0.6F) * 0.2F, 0.15F);   // fast flickering beats, blades half swept
            tail.xScale = 0.8F;
        } else if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 4.0F);
            float shiver = Mth.sin(s.phaseTime * 2.8F) * 0.06F * k;
            wings(0.65F * k + shiver, -0.25F * k, 0.2F * k, -0.15F * k);        // raised high and spread wide, tips flared
            body.xRot = Mth.lerp(k, pitch, -15.0F * DEG);                       // reared back, chest out
            head.xRot = -30.0F * DEG * k;                                      // head up, screaming
            jaw.xRot = (0.7F + Mth.sin(s.phaseTime * 2.4F) * 0.1F) * k;        // bill wide
            tail.xScale = 1.0F + 0.5F * k;                                     // the fork fanned
            tail.xRot -= 0.25F * k;
        } else if (s.phase == Step.DIVE.ordinal()) {
            wings(0.05F, 0.95F, -0.1F, 0.5F);                                  // blades swept back tight along its sides
            body.xRot = Mth.clamp(s.xRot, -30.0F, 85.0F) * DEG;               // along the dive line
            head.xRot = 0.0F;
            jaw.xRot = 0.1F;
            tail.xScale = 0.5F;                                                // the fork closed to a spike
        } else if (s.phase == Step.RECOVER.ordinal()) {
            float hard = Mth.sin(t * 1.8F);
            wings(0.2F + hard * 0.75F, 0.0F, Mth.sin(t * 1.8F - 0.8F) * 0.4F, 0.0F);
            body.xRot = -20.0F * DEG;
        } else if (s.phase == Step.LOST.ordinal()) {
            wings(0.05F, 0.1F, 0.0F, 0.0F);                                    // a long glide out of the fight
            body.zRot = -0.4F;                                                 // banked hard into its turn
            head.yRot = 0.5F;                                                  // looking back at the one that got away
            tail.zRot = 0.15F;
        }
    }
}
