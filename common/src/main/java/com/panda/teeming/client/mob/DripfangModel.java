package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Dripfang.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Dripfang: hanging, it is plain dripstone, still, with its legs folded away and its eyes shut. The drip opens its
 * eyes and makes it shiver, the fang trembling on its own. Dropping, it leans along its fall. Stuck, it sits buried to
 * a quarter of its length and askew, rocking, its four long legs braced round it and heaving. Climbing, the legs reach up
 * hand over hand for the thread. On open ground it walks on the legs like a harvestman, its point carried just off
 * the ground, and stabs with it.
 */
public class DripfangModel extends TeemingModel {
    private static final int LEGS = 4;
    /** How far the point sinks into the ground while stuck, in model pixels. */
    private static final float BURIED = 6.0F;

    private final ModelPart body, tip, eyes;
    private final ModelPart[] hips = new ModelPart[LEGS], thighs = new ModelPart[LEGS], shins = new ModelPart[LEGS];

    public DripfangModel(ModelPart root) {
        super(root, CreatureLayers.DRIPFANG_ONLY);
        body = part("body");
        tip = part("tip");
        eyes = part("eyes");
        for (int i = 0; i < LEGS; i++) {
            hips[i] = part("hip_" + i);
            thighs[i] = part("thigh_" + i);
            shins[i] = part("shin_" + i);
        }
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean legs = s.phase == Step.IDLE.ordinal() || s.phase == Step.STUCK.ordinal() || s.phase == Step.CLIMB.ordinal();
        for (ModelPart hip : hips) hip.visible = legs;
        eyes.visible = s.phase != Step.HANGING.ordinal() && !(s.phase == Step.TELL.ordinal() && s.progress < 0.35F);

        if (s.phase == Step.HANGING.ordinal()) {
            return;                                                       // stone: nothing moves
        }
        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 3.0F);                          // the shiver builds fast, then holds
            float a = s.phaseTime;
            body.zRot += Mth.sin(a * 4.1F) * 2.0F * DEG * k;
            body.xRot += Mth.cos(a * 3.3F) * 1.5F * DEG * k;
            body.y -= 0.5F * k;                                           // it hunches up against the ceiling
            tip.zRot += Mth.sin(a * 5.7F) * 4.0F * DEG * k;               // the fang trembles on its own
            tip.xRot += Mth.cos(a * 4.9F) * 3.0F * DEG * k;
            tip.y += 1.0F * k;                                            // and reaches a pixel lower
            return;
        }
        if (s.phase == Step.DROP.ordinal()) {
            body.xRot = -s.xRot * DEG;                                    // the point leads along its fall
            tip.y += 1.0F;
            return;
        }
        if (s.phase == Step.STUCK.ordinal()) {
            float a = s.phaseTime;
            body.y += BURIED;
            body.zRot += (9.0F + Mth.sin(a * 0.9F) * 3.0F) * DEG;         // askew, rocking as it tries to pull free
            body.xRot += (5.0F + Mth.cos(a * 0.7F) * 2.0F) * DEG;
            for (int i = 0; i < LEGS; i++) {
                // legs splayed wider to reach the ground round the buried point, braced and heaving, one pair
                // scrabbling up off the ground while the other pushes
                float heave = Mth.sin(a * 0.8F + (i % 2) * Mth.PI);
                thighs[i].xRot += Math.max(0.0F, heave) * 18.0F * DEG;
                shins[i].xRot += (28.0F - Math.max(0.0F, heave) * 10.0F) * DEG;
                hips[i].yRot += Mth.sin(a * 0.6F + i * 2.1F) * 8.0F * DEG;
            }
            return;
        }
        if (s.phase == Step.CLIMB.ordinal()) {
            float a = s.phaseTime;
            body.zRot += Mth.sin(a * 0.6F) * 3.0F * DEG;
            for (int i = 0; i < LEGS; i++) {                              // hand over hand up the thread, pairs in turn
                float ph = a * 0.6F + (i % 2) * Mth.PI;
                thighs[i].xRot += (30.0F + Mth.sin(ph) * 12.0F) * DEG;
                shins[i].xRot += (145.0F + Mth.cos(ph) * 20.0F) * DEG;
            }
            return;
        }

        // open ground: a high-stepping harvestman walk, diagonal pairs in turn, the cone bobbing between the legs
        float walk = pos * 0.9F;
        for (int i = 0; i < LEGS; i++) {
            float ph = walk + (i % 2) * Mth.PI;
            float lift = Math.max(0.0F, Mth.sin(ph)) * speed;
            hips[i].yRot += Mth.cos(ph) * 0.35F * speed;
            thighs[i].xRot += lift * 0.35F;
            shins[i].xRot -= lift * 0.25F;
        }
        body.y -= Math.abs(Mth.sin(walk)) * 0.6F * speed;
        body.zRot += Mth.cos(walk) * 0.04F * speed + Mth.sin(t * 0.08F) * 0.02F;
        if (s.attack > 0.0F) body.xRot -= Mth.sin(s.attack * Mth.PI) * 35.0F * DEG;   // a stab: the point swings forward
    }
}
