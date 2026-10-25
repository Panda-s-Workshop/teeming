package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Hushwing.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Hushwing: quick fluttering wingbeats and a bob, with the ears telling what it is doing. At rest they hang relaxed and
 * splayed. When it hears something it holds still on raised wings while they snap upright and flick back and forth
 * for the half second of the cue, head raised. While it chases a noise they are pricked forward and it flies
 * nose-down with hard beats; a bite throws its head forward, flattens its ears and pulls its wings up; drawn to a decoy
 * it hovers, head turning, each ear swivelling on its own to find the sound. The rig tips the wings' trailing edges down so their membrane faces a player below.
 */
public class HushwingModel extends TeemingModel {
    private final ModelPart body, head, earRight, earLeft, tail, legRight, legLeft, wingRight, wingLeft, tipRight, tipLeft;

    public HushwingModel(ModelPart root) {
        super(root, CreatureLayers.HUSHWING_ONLY);
        body = part("body");
        head = part("head");
        earRight = part("ear_right");
        earLeft = part("ear_left");
        tail = part("tail");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        wingRight = part("wing_right");
        wingLeft = part("wing_left");
        tipRight = part("wing_right_tip");
        tipLeft = part("wing_left_tip");
    }

    /** Both wings from their rest pose: roll (up is positive), sweep back, and the hands' roll. */
    private void wings(float roll, float sweep, float tipRoll) {
        wingRight.zRot += roll;
        wingLeft.zRot -= roll;
        wingRight.yRot += sweep;
        wingLeft.yRot -= sweep;
        tipRight.zRot += tipRoll;
        tipLeft.zRot -= tipRoll;
    }

    /** Both ears from their rest pose: tipped forward, splayed out, swivelled (each its own amount). */
    private void ears(float forward, float splay, float swivelRight, float swivelLeft) {
        earRight.xRot += forward;
        earLeft.xRot += forward;
        earRight.zRot -= splay;
        earLeft.zRot += splay;
        earRight.yRot += swivelRight;
        earLeft.yRot -= swivelLeft;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float rate = 1.5F, lift = 0.85F;
        body.y += Mth.sin(t * 0.25F) * 0.5F;
        head.xRot = s.xRot * DEG * 0.4F;
        head.yRot = s.yRot * DEG * 0.4F;
        tail.xRot += Mth.sin(t * 0.25F + 1.0F) * 0.1F;
        legRight.xRot += 0.6F;                                             // tucked back in flight
        legLeft.xRot += 0.6F;

        if (s.phase == Step.HEAR.ordinal()) {
            float k = envelope(s.progress, 0.15F, 0.25F);
            float flick = Mth.sin(s.phaseTime * 2.6F) * 0.45F * k;
            ears(-0.25F * k, -0.12F * k, flick, -flick);                   // snapped upright, flicking
            earRight.zRot += flick * 0.4F;
            earLeft.zRot += flick * 0.4F;
            head.xRot -= 0.35F * k;                                        // head up, listening
            head.zRot = Mth.sin(s.phaseTime * 1.3F) * 0.15F * k;
            wings(0.35F * k + Mth.sin(t * 2.4F) * 0.08F, 0.0F, -0.2F * k);   // it freezes, wings held up in a V, to listen
            rate = 1.5F * (1.0F - k);
        } else if (s.phase == Step.HUNT.ordinal()) {
            ears(0.55F, -0.15F, 0.0F, 0.0F);                               // pricked forward at the noise
            body.xRot = 18.0F * DEG;                                       // nose-down into the chase
            head.xRot -= 0.25F;
            rate = 2.0F;
            lift = 1.0F;
            legRight.xRot += 0.4F;
            legLeft.xRot += 0.4F;
        } else if (s.phase == Step.BITE.ordinal()) {
            float k = Mth.sin(Mth.clamp(s.progress, 0.0F, 1.0F) * Mth.PI);
            ears(-0.6F * k, 0.35F * k, 0.0F, 0.0F);                        // flattened back
            body.xRot = 30.0F * DEG * k;
            head.xRot += 0.7F * k;                                         // the head thrown into the bite
            wings(0.9F * k, 0.0F, 0.4F * k);                               // wings pulled up high
            legRight.xRot -= 1.4F * k;                                     // feet thrown forward
            legLeft.xRot -= 1.4F * k;
            rate = 0.0F;
        } else if (s.phase == Step.DECOY.ordinal()) {
            float a = s.phaseTime * 0.35F;
            ears(0.2F, -0.1F, Mth.sin(a) * 0.7F, Mth.sin(a + 1.7F) * 0.7F);   // each ear hunting the sound on its own
            head.yRot = Mth.sin(s.phaseTime * 0.2F) * 0.7F;                // head turning, searching
            head.zRot = Mth.sin(s.phaseTime * 0.27F) * 0.2F;
            rate = 1.1F;
            lift = 0.7F;
        } else {
            float twitch = Mth.sin(t * 0.09F) > 0.97F ? Mth.sin(t * 2.0F) * 0.2F : 0.0F;
            ears(0.2F, 0.12F + Mth.sin(t * 0.1F) * 0.04F, twitch, 0.0F);   // relaxed, an idle twitch now and then
        }
        if (rate > 0.0F) {
            float beat = Mth.sin(t * rate);
            wings(beat * lift, 0.0F, Mth.sin(t * rate - 0.9F) * 0.5F * lift);
        }
        if (s.attack > 0.0F) head.xRot += Mth.sin(s.attack * Mth.PI) * 0.5F;
    }
}
