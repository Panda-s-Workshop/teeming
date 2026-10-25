package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Snagwood.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Snagwood: a slow, swaying stride with its limbs dragging. Rooted, it holds still as a snag: the trunk leans a
 * little, one limb is raised like a branch and the other juts out crooked, and the eyes are gone. Waking, it shakes
 * and wrenches its roots free while its limbs drop. The creak leans it back with both limbs raised high; the sweep
 * twists the trunk across its front with the limbs low; then it hangs hunched while it recovers.
 */
public class SnagwoodModel extends TeemingModel {
    private final ModelPart body, crown, eyes, legRight, legLeft, armRight, armLeft, foreRight, foreLeft;
    private final ModelPart[] twigs;

    public SnagwoodModel(ModelPart root) {
        super(root, CreatureLayers.SNAGWOOD_ONLY);
        body = part("body");
        crown = part("crown");
        eyes = part("eyes");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        foreRight = part("forearm_right");
        foreLeft = part("forearm_left");
        twigs = new ModelPart[] {part("twig_right_0"), part("twig_right_1"), part("twig_right_2"), part("twig_left_0"), part("twig_left_1")};
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        eyes.visible = true;
        if (s.phase == Step.ROOTED.ordinal()) {
            snag(1.0F);
            eyes.visible = false;
            return;
        }

        // awake: a slow, heavy stride, the trunk rocking over each root
        float stride = Mth.cos(pos * 0.45F) * 0.65F * speed;
        legRight.xRot = stride;
        legLeft.xRot = -stride;
        body.zRot = Mth.cos(pos * 0.45F) * 0.07F * speed;
        body.xRot = 0.05F * speed;                                       // leans into the walk
        armRight.xRot += -stride * 0.6F;                                  // limbs swing, dragging
        armLeft.xRot += stride * 0.6F;
        crown.xRot = Mth.sin(t * 0.045F) * 0.02F;                         // a slow creaking sway at rest
        crown.zRot = Mth.sin(t * 0.03F) * 0.015F;

        if (s.phase == Step.UPROOT.ordinal()) {
            float left = 1.0F - smooth(s.progress);
            snag(left);
            float wrench = Mth.sin(s.phaseTime * 2.2F) * left;
            legRight.xRot += 0.35F * wrench;                              // roots wrenched out of the ground
            legLeft.xRot -= 0.35F * wrench;
            body.zRot += Mth.sin(s.phaseTime * 3.1F) * 0.06F * left;
            eyes.visible = s.progress > 0.25F;
        } else if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.0F);                          // wound up by half way, then held
            float tremble = Mth.sin(s.phaseTime * 2.4F) * 0.035F * k;
            body.xRot -= 16.0F * DEG * k;                                 // leans back
            crown.xRot -= 10.0F * DEG * k;
            crown.zRot = tremble;
            armRight.xRot -= 125.0F * DEG * k;                            // both limbs raised high and wide
            armLeft.xRot -= 125.0F * DEG * k;
            armRight.zRot -= 45.0F * DEG * k;                             // overhead, z turns the other way: this spreads them
            armLeft.zRot += 45.0F * DEG * k;
            foreRight.xRot -= 25.0F * DEG * k;                            // and bent back, ready to swing
            foreLeft.xRot -= 25.0F * DEG * k;
            for (ModelPart twig : twigs) twig.zRot *= 1.0F + 0.6F * k;   // twigs splay
            legRight.xRot = -6.0F * DEG * k;                              // braced
            legLeft.xRot = 8.0F * DEG * k;
        } else if (s.phase == Step.SWEEP.ordinal()) {
            float e = envelope(s.progress, 0.05F, 0.35F);
            body.yRot = (60.0F - 120.0F * smooth(s.progress * 1.3F)) * DEG; // the trunk twists across its front
            body.xRot += 24.0F * DEG * e;                                 // leaning into it
            crown.xRot += 6.0F * DEG * e;
            armRight.xRot -= 75.0F * DEG * e;                             // limbs low and forward, raking the ground
            armLeft.xRot -= 75.0F * DEG * e;
            armRight.zRot += 38.0F * DEG * e;
            armLeft.zRot -= 38.0F * DEG * e;
            foreRight.xRot += 18.0F * DEG * e;
            foreLeft.xRot += 18.0F * DEG * e;
            legRight.xRot = -16.0F * DEG * e;
            legLeft.xRot = 12.0F * DEG * e;
        } else if (s.phase == Step.RECOVER.ordinal()) {
            float e = envelope(s.progress, 0.1F, 0.5F);
            body.xRot += 14.0F * DEG * e;                                 // hunched, limbs hanging low
            crown.xRot += 12.0F * DEG * e;
            body.y += 1.0F * e;
            armRight.xRot -= 18.0F * DEG * e;
            armLeft.xRot -= 18.0F * DEG * e;
            armRight.zRot -= 6.0F * DEG * e;
            armLeft.zRot += 6.0F * DEG * e;
        }
    }

    /** Blends toward the snag pose by {@code k} (1 = the full disguise): a leaning trunk with limbs set as branches. */
    private void snag(float k) {
        body.zRot += 4.0F * DEG * k;
        crown.zRot += -6.0F * DEG * k;
        crown.xRot += 3.0F * DEG * k;
        armRight.zRot += 105.0F * DEG * k;                                // raised up and out like a branch
        armRight.xRot += 18.0F * DEG * k;
        foreRight.zRot += 28.0F * DEG * k;                                // curving on upward
        foreRight.xRot += 10.0F * DEG * k;
        armLeft.zRot -= 62.0F * DEG * k;                                  // the broken limb juts out crooked
        armLeft.xRot += 2.0F * DEG * k;
        foreLeft.zRot -= 34.0F * DEG * k;
        legRight.xRot *= 1.0F - k;
        legLeft.xRot *= 1.0F - k;
    }
}
