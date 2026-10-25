package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Prismback.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Prismback: a sprawling lizard's walk (diagonal legs together, the body and tail swaying side to side), a lunge and a
 * gaping jaw for the bite. In the glimmer it lifts its head and its prisms begin to rise; in the flash it braces low
 * on splayed legs with its head tucked, its prisms bristling upright and fanned out, trembling (the light comes from
 * its glow stage); spent, it lies flat with its head on the ground, panting, its prisms laid back along its spine.
 *
 * <p>Everything hangs under {@code body}. The legs are its children, so where the body sinks the hips are lifted back
 * by the same amount and the feet stay on the ground.
 */
public class PrismbackModel extends TeemingModel {
    /** How far the body sinks bracing in the flash and lying spent, in model pixels. */
    private static final float BRACE_DROP = 1.0F;
    private static final float SPENT_DROP = 1.6F;
    /** The ridge: three spine prisms, then three on its right and three on its left. */
    private static final int SPINE = 3, PER_SIDE = 3;

    private final ModelPart body, head, jaw, tail, tail2, tail3;
    private final ModelPart[] prisms = new ModelPart[SPINE + 2 * PER_SIDE];
    private final ModelPart[] crest = new ModelPart[2];
    private final ModelPart tailPrism;
    /** Legs in the order front right, front left, back right, back left. */
    private final ModelPart[] legs = new ModelPart[4];

    public PrismbackModel(ModelPart root) {
        super(root, CreatureLayers.PRISMBACK_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        tail = part("tail");
        tail2 = part("tail2");
        tail3 = part("tail3");
        for (int i = 0; i < prisms.length; i++) prisms[i] = part("prism_" + i);
        crest[0] = part("crest_0");
        crest[1] = part("crest_1");
        tailPrism = part("prism_tail");
        int i = 0;
        for (String end : new String[] {"front", "back"}) {
            for (String side : new String[] {"right", "left"}) {
                legs[i] = part("leg_" + end + "_" + side);
                i++;
            }
        }
    }

    /** -1 for a prism or leg on its right, +1 on its left, 0 on the spine. */
    private static float sideOf(int prism) {
        if (prism < SPINE) return 0.0F;
        return prism < SPINE + PER_SIDE ? -1.0F : 1.0F;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        if (s.phase == Step.GLIMMER.ordinal()) {
            walk(s, t, pos, speed);
            glimmer(s, t);
        } else if (s.phase == Step.FLASH.ordinal()) {
            brace(s, t);
        } else if (s.phase == Step.SPENT.ordinal()) {
            walk(s, t, pos, speed * 0.8F);
            spent(s, t);
        } else {
            walk(s, t, pos, speed);
        }
    }

    /** Sinks the body by {@code drop} pixels, lifting the hips back so the feet stay planted. */
    private void sink(float drop) {
        body.y += drop;
        for (ModelPart leg : legs) leg.y -= drop;
    }

    /** Swings the front feet forward and the back feet back by {@code amount} radians: a wide, planted stance. */
    private void splay(float amount) {
        for (int i = 0; i < 4; i++) {
            float sx = i % 2 == 0 ? -1.0F : 1.0F;
            float end = i < 2 ? 1.0F : -1.0F;
            legs[i].yRot += sx * end * amount;
        }
    }

    /** The warning: head up, the prisms rising a little and starting to shiver. */
    private void glimmer(TeemingRenderState s, float t) {
        float k = smooth(s.phaseTime / 4.0F);
        head.xRot -= 0.22F * k;
        for (int i = 0; i < prisms.length; i++) {
            prisms[i].y -= 0.5F * k;
            prisms[i].zRot += sideOf(i) * 0.08F * k + Mth.sin(t * 2.2F + i) * 0.03F * k;
        }
    }

    /** The flash: braced low on splayed legs, head tucked, jaw shut, tail raised; the prisms bristle up and fan out. */
    private void brace(TeemingRenderState s, float t) {
        float k = smooth(s.phaseTime / 3.0F);
        sink(BRACE_DROP * k);
        head.xRot += 0.18F * k;
        tail.xRot += 0.18F * k;
        tail2.xRot += 0.1F * k;
        splay(0.3F * k);                                               // feet planted wide, front and back
        for (int i = 0; i < prisms.length; i++) {
            float side = sideOf(i);
            prisms[i].y -= 0.8F * k;
            prisms[i].xRot += 0.1F * k;                                // stood upright against their rest lean
            prisms[i].zRot += side * 0.22F * k + Mth.sin(t * 3.1F + i * 1.7F) * 0.025F * k;
        }
        for (int i = 0; i < 2; i++) crest[i].zRot += (i == 0 ? -0.25F : 0.25F) * k;
        tailPrism.xRot += 0.15F * k;
    }

    /** Spent: lying flat with its head on the ground, panting; the prisms laid back along its spine. */
    private void spent(TeemingRenderState s, float t) {
        float k = smooth(s.phaseTime / 5.0F);
        sink(SPENT_DROP * k);
        head.xRot += 0.2F * k;
        jaw.xRot += (0.18F + Mth.sin(t * 0.5F) * 0.1F) * k;
        body.y += Mth.sin(t * 0.5F) * 0.2F * k;
        splay(0.4F * k);
        for (int i = 0; i < prisms.length; i++) {
            prisms[i].xRot -= 0.45F * k;
            prisms[i].zRot += sideOf(i) * 0.2F * k;
        }
        for (ModelPart c : crest) c.xRot -= 0.3F * k;
        tailPrism.xRot -= 0.3F * k;
    }

    /** The sprawling walk; the head turns to what it watches; breathing at rest; the bite. */
    private void walk(TeemingRenderState s, float t, float pos, float speed) {
        float a = pos * 0.9F;
        float swing = Mth.sin(a) * 0.55F * speed;
        float lift = Math.max(0.0F, Mth.cos(a)) * 0.35F * speed;
        float liftOther = Math.max(0.0F, -Mth.cos(a)) * 0.35F * speed;
        // diagonal pairs: front right with back left, front left with back right
        legs[0].yRot += swing;
        legs[3].yRot += swing;
        legs[1].yRot -= swing;
        legs[2].yRot -= swing;
        legs[0].zRot += lift;                                         // a right knee lifts with +z, a left one with -z
        legs[3].zRot -= lift;
        legs[1].zRot -= liftOther;
        legs[2].zRot += liftOther;
        body.yRot += Mth.sin(a) * 0.1F * speed;
        tail.yRot -= Mth.sin(a) * 0.25F * speed + Mth.sin(t * 0.07F) * 0.05F;
        tail2.yRot -= Mth.sin(a - 0.7F) * 0.3F * speed + Mth.sin(t * 0.07F - 0.6F) * 0.06F;
        tail3.yRot -= Mth.sin(a - 1.4F) * 0.35F * speed + Mth.sin(t * 0.07F - 1.2F) * 0.08F;
        head.yRot += Mth.clamp(s.yRot, -40.0F, 40.0F) * DEG * 0.7F - Mth.sin(a) * 0.08F * speed;
        head.xRot += Mth.clamp(s.xRot, -25.0F, 25.0F) * DEG * 0.5F;
        body.y += Mth.sin(t * 0.09F) * 0.12F;                         // breathing
        if (s.attack > 0.0F) {                                        // the bite: a lunge and a gaping jaw
            float b = Mth.sin(s.attack * Mth.PI);
            head.z -= 1.5F * b;
            head.xRot -= 0.15F * b;
            jaw.xRot += 0.7F * b;
        }
    }
}
