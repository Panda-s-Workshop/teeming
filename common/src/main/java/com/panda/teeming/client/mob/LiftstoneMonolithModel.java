package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.LiftstoneMonolith.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Liftstone Monolith: the slab stands still and only sways a hair as it drifts; the capstone bobs and turns slowly over
 * it and three chips of stone circle its shoulder. For the tell the capstone rises and spins up and the chips climb;
 * through the lift and the hold it hangs high and trembles; the slam drives it down onto the shoulder, square again;
 * split, its two halves slide apart and tip outward, showing the heart between them, then close at the end.
 */
public class LiftstoneMonolithModel extends TeemingModel {
    /** How high the capstone rises for the pull, in model pixels. */
    private static final float RISE = 10.0F;
    /** How far each half slides aside when split, in model pixels: enough to show the whole heart between them. */
    private static final float SPLIT = 8.0F;
    /** The chips' circle round the shoulder, in model pixels. */
    private static final float ORBIT = 14.0F;

    private final ModelPart body, capstone, capRight, capLeft, heart;
    private final ModelPart[] shards = new ModelPart[3];

    public LiftstoneMonolithModel(ModelPart root) {
        super(root, CreatureLayers.LIFTSTONE_MONOLITH_ONLY);
        body = part("body");
        capstone = part("capstone");
        capRight = part("cap_right");
        capLeft = part("cap_left");
        heart = part("heart");
        for (int i = 0; i < shards.length; i++) shards[i] = part("shard_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        body.zRot = Mth.sin(pos * 0.3F) * 0.015F * speed;                        // a heavy stone, barely rocking as it slides
        capstone.y += Mth.sin(t * 0.08F) * 0.8F;                                 // the capstone hangs on nothing, bobbing
        capstone.yRot = t * 0.02F;
        float orbit = t * 0.03F, radius = ORBIT, lift = 0.0F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress);
            capstone.y -= RISE * k;
            capstone.yRot = t * 0.02F + s.phaseTime * 0.12F * k;                 // spinning up as it climbs
            orbit += s.phaseTime * 0.06F * k;
            lift = 6.0F * k;
        } else if (s.phase == Step.LIFT.ordinal() || s.phase == Step.HOLD.ordinal()) {
            capstone.y -= RISE;
            capstone.yRot = t * 0.02F + s.phaseTime * 0.12F;
            capstone.x += Mth.sin(s.phaseTime * 2.1F) * 0.35F;                   // straining: a fine tremble
            capstone.z += Mth.cos(s.phaseTime * 1.7F) * 0.35F;
            orbit += t * 0.06F;
            radius = ORBIT + 3.0F;
            lift = 8.0F;
        } else if (s.phase == Step.SLAM.ordinal()) {
            float k = smooth(s.progress * 2.0F);                                 // down in the first half of the slam
            capstone.y += Mth.lerp(k, -RISE, 4.0F) - Mth.sin(t * 0.08F) * 0.8F;
            capstone.yRot = 0.0F;                                                // and square to the slab as it lands
            body.y += 1.0F * k;
            lift = Mth.lerp(k, 8.0F, -2.0F);
        } else if (s.phase == Step.CRACKED.ordinal()) {
            float open = envelope(s.progress, 0.05F, 0.08F);
            capstone.yRot = 0.0F;
            capstone.y += 3.0F * open - Mth.sin(t * 0.08F) * 0.8F;              // it sags, split, no longer bobbing
            capRight.x -= SPLIT * open;
            capLeft.x += SPLIT * open;
            capRight.zRot = 0.3F * open;                                         // each half tips outward
            capLeft.zRot = -0.3F * open;
            heart.y += Mth.sin(t * 0.25F) * 0.6F * open;                         // the heart pulses where it hangs
            heart.yRot = t * 0.1F;
        }

        for (int i = 0; i < shards.length; i++) {
            float a = orbit + i * (Mth.TWO_PI / shards.length);
            shards[i].x = Mth.cos(a) * radius;
            shards[i].z = Mth.sin(a) * radius;
            shards[i].y -= lift + Mth.sin(t * 0.1F + i * 2.0F) * 0.7F;
            shards[i].xRot = t * 0.05F + i;
            shards[i].yRot = t * 0.04F + i;
        }
    }
}
