package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.AshJackal;
import com.panda.teeming.mob.nether.AshJackal.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Ash Jackal: a light, springy trot with the big ears pricked and the brush low; the sniff before every snatch and
 * bite (standing square, chest up, nose lifted and twitching, ears pricked straight up); a quick bob of the head as it
 * bites; the dip of the snatch (forelegs braced, nose to the ground, jaw open); and the getaway, a stretched-out gallop with
 * the head held high and level (so the item in its jaws stays in view), ears flat back and the brush streaming behind.
 * While it carries something its jaw stays a little open on it.
 *
 * <p>The body, jaw, ears and tail have a rest angle in the layer, so every pose here adds to it.
 */
public class AshJackalModel extends TeemingModel {
    private final ModelPart body, tail, head, jaw, earRight, earLeft, frontRight, frontLeft, backRight, backLeft;

    public AshJackalModel(ModelPart root) {
        super(root, CreatureLayers.ASH_JACKAL_ONLY);
        body = part("body");
        tail = part("tail");
        head = part("head");
        jaw = part("jaw");
        earRight = part("ear_right");
        earLeft = part("ear_left");
        frontRight = part("leg_front_right");
        frontLeft = part("leg_front_left");
        backRight = part("leg_back_right");
        backLeft = part("leg_back_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean carrying = s.flag(AshJackal.CARRY_FLAG);

        float swing = Mth.cos(pos * 0.6662F) * 1.2F * speed;
        frontRight.xRot = swing;
        backLeft.xRot = swing;
        frontLeft.xRot = -swing;
        backRight.xRot = -swing;
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        body.y += Mth.cos(pos * 1.3324F) * 0.3F * speed;                 // a springy trot
        head.y += Mth.cos(pos * 1.3324F) * 0.3F * speed;
        earRight.zRot += Mth.sin(t * 0.07F) * 0.04F;                       // the big ears twitch
        earLeft.zRot -= Mth.sin(t * 0.07F + 1.3F) * 0.04F;
        tail.xRot += speed * 0.3F;
        tail.zRot = Mth.sin(t * 0.08F) * 0.12F;

        if (s.attack > 0.0F) {                                             // the bite: a quick lunge of the head
            float bite = Mth.sin(s.attack * Mth.PI);
            head.xRot += 0.35F * bite;
            head.z -= 1.2F * bite;
            jaw.xRot += 0.7F * bite;
        }
        if (carrying) {
            jaw.xRot += 0.3F;                                              // the jaw held a little open on what it carries
            head.xRot = Math.min(head.xRot, 0.1F);                         // and the head kept up
        }

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 3.0F);                          // up within 3 ticks, held to the end
            frontRight.xRot = -0.08F * k;                                  // standing square and still, forelegs planted
            frontLeft.xRot = -0.08F * k;
            backRight.xRot = 0.05F * k;
            backLeft.xRot = 0.05F * k;
            body.xRot -= 5.0F * DEG * k;                                   // chest up
            head.xRot = Math.min(head.xRot, 0.35F) - 24.0F * DEG * k       // nose lifted toward what it smells
                    + Mth.sin(t * 2.6F) * 0.05F * k;                       // and twitching as it sniffs
            head.y -= 0.8F * k;
            head.z -= 0.6F * k;
            jaw.xRot -= 0.1F * k;                                          // mouth shut
            earRight.xRot += 0.3F * k;                                     // ears pricked straight up
            earLeft.xRot += 0.3F * k;
            earRight.zRot += 11.0F * DEG * k;
            earLeft.zRot -= 11.0F * DEG * k;
            tail.xRot -= 0.15F * k;                                        // the brush held still and low
            tail.zRot *= 1.0F - k;
        } else if (s.phase == Step.SNATCH.ordinal()) {
            float k = envelope(s.progress, 0.45F, 0.25F);                  // down, grab, and back up
            head.xRot = 38.0F * DEG * k;                                   // nose to the ground
            head.y += 4.0F * k;
            head.z -= 1.0F * k;
            jaw.xRot += 0.6F * k * (1.0F - smooth((s.progress - 0.5F) * 4.0F));   // snaps shut at the bottom
            body.xRot += 10.0F * DEG * k;
            body.y += 1.0F * k;
            frontRight.xRot = -0.35F * k;                                  // forelegs braced forward
            frontLeft.xRot = -0.35F * k;
            backRight.xRot = 0.2F * k;
            backLeft.xRot = 0.2F * k;
            earRight.xRot -= 0.25F * k;                                    // ears kept up as the head dips
            earLeft.xRot -= 0.25F * k;
            tail.xRot += 0.4F * k;                                         // the brush lifts
        } else if (s.phase == Step.FLEE.ordinal()) {
            // a stretched gallop: front and hind pairs a quarter-stride apart, so some legs are always spread
            float a = s.phaseTime * 1.4F;
            float front = Mth.cos(a) * 1.0F, hind = Mth.sin(a) * 1.0F;
            frontRight.xRot = front;
            frontLeft.xRot = front * 0.85F;
            backRight.xRot = hind;
            backLeft.xRot = hind * 0.85F;
            body.y += 0.5F + Mth.sin(a * 2.0F) * 0.4F;
            body.xRot += 3.0F * DEG;
            head.y += 0.5F;
            head.xRot = -6.0F * DEG;                                       // head up and level: it shows off its loot
            head.yRot = 0.0F;
            earRight.xRot -= 0.9F;                                         // ears laid flat back
            earLeft.xRot -= 0.9F;
            tail.xRot += 0.8F;                                             // the brush streams out behind
            tail.zRot = 0.0F;
        }
    }
}
