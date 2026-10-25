package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Aurovore;
import com.panda.teeming.mob.nether.Aurovore.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Aurovore: a quick, sprawling lizard's walk (diagonal legs together, the body and tail swaying side to side, the head
 * bobbing up on its neck). The sniff: it crouches with its neck stretched out and up at its target, nose pad twitching
 * fast, crest flared up and fanned wide, back spikes raised and the tail tip flicking. The bite: a lunge with the jaw
 * snapping shut. The getaway: a low, fast scurry with the head stretched forward and level (so the piece in its jaws
 * stays in view), the crest and spikes laid flat and the tail held up off the ground. While it holds anything its jaw
 * stays a little open on the newest piece.
 *
 * <p>Everything hangs under {@code body}. The legs are its children, so where the body sinks the hips are lifted back
 * by the same amount and the feet stay on the ground. The neck, head, crest and tail have rest angles in the layer, so
 * every pose here adds to them.
 */
public class AurovoreModel extends TeemingModel {
    /** How far the body sinks crouching to sniff and scurrying off, in model pixels. */
    private static final float SNIFF_DROP = 1.0F;
    private static final float FLEE_DROP = 1.2F;

    private final ModelPart body, neck, head, nose, jaw, pouch, crest, tail, tail2, tail3;
    private final ModelPart[] fan = new ModelPart[5];
    private final ModelPart[] spines = new ModelPart[4];
    private final ModelPart tailSpine;
    /** Legs in the order front right, front left, back right, back left. */
    private final ModelPart[] legs = new ModelPart[4];

    public AurovoreModel(ModelPart root) {
        super(root, CreatureLayers.AUROVORE_ONLY);
        body = part("body");
        neck = part("neck");
        head = part("head");
        nose = part("nose");
        jaw = part("jaw");
        pouch = part("pouch");
        crest = part("crest");
        tail = part("tail");
        tail2 = part("tail2");
        tail3 = part("tail3");
        for (int i = 0; i < fan.length; i++) fan[i] = part("crest_" + i);
        for (int i = 0; i < spines.length; i++) spines[i] = part("spine_" + i);
        tailSpine = part("spine_tail");
        int i = 0;
        for (String end : new String[] {"front", "back"}) {
            for (String side : new String[] {"right", "left"}) {
                legs[i] = part("leg_" + end + "_" + side);
                i++;
            }
        }
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        if (s.phase == Step.FLEE.ordinal()) {
            scurry(s, t);
        } else {
            walk(s, t, pos, speed);
            if (s.phase == Step.SNIFF.ordinal()) sniff(s, t);
            else if (s.phase == Step.BITE.ordinal()) snap(s);
        }
        if (s.flag(Aurovore.CARRY_FLAG)) jaw.xRot += 0.28F;                 // the jaw held a little open on its newest piece
    }

    /** Sinks the body by {@code drop} pixels, lifting the hips back so the feet stay planted. */
    private void sink(float drop) {
        body.y += drop;
        for (ModelPart leg : legs) leg.y -= drop;
    }

    /** The legs' stride: diagonal pairs swing together, each knee lifting as its foot comes forward. */
    private void stride(float a, float amount) {
        float swing = Mth.sin(a) * 0.55F * amount;
        float lift = Math.max(0.0F, Mth.cos(a)) * 0.4F * amount;
        float liftOther = Math.max(0.0F, -Mth.cos(a)) * 0.4F * amount;
        legs[0].yRot += swing;                                             // front right with back left
        legs[3].yRot += swing;
        legs[1].yRot -= swing;                                             // front left with back right
        legs[2].yRot -= swing;
        legs[0].zRot += lift;                                              // a right knee lifts with +z, a left one with -z
        legs[3].zRot -= lift;
        legs[1].zRot -= liftOther;
        legs[2].zRot += liftOther;
    }

    /** The walk; the head turns to what it watches; the throat pouch breathes; the tail tip curls and uncurls. */
    private void walk(TeemingRenderState s, float t, float pos, float speed) {
        float a = pos * 1.1F;
        stride(a, speed);
        body.yRot += Mth.sin(a) * 0.1F * speed;
        tail.yRot -= Mth.sin(a) * 0.25F * speed + Mth.sin(t * 0.08F) * 0.06F;
        tail2.yRot -= Mth.sin(a - 0.7F) * 0.3F * speed + Mth.sin(t * 0.08F - 0.6F) * 0.08F;
        tail3.xRot += Mth.sin(t * 0.06F) * 0.1F;
        neck.yRot += Mth.clamp(s.yRot, -45.0F, 45.0F) * DEG * 0.6F;
        head.yRot += Mth.clamp(s.yRot, -45.0F, 45.0F) * DEG * 0.4F;
        head.xRot += Mth.clamp(s.xRot, -30.0F, 30.0F) * DEG * 0.6F;
        neck.xRot += Mth.cos(a * 2.0F) * 0.05F * speed;                  // the head bobs as it trots
        pouch.y += Mth.sin(t * 0.12F) * 0.25F;                           // the pouch swells and sags with each breath
        nose.xRot += Mth.sin(t * 0.21F) * 0.04F;
        if (s.attack > 0.0F) {                                           // a plain attack animation: a lunge and a gape
            float b = Mth.sin(s.attack * Mth.PI);
            head.z -= 1.2F * b;
            jaw.xRot += 0.6F * b;
        }
    }

    /** The sniff: crouched, neck out and up at the target, nose twitching, crest flared and fanned, spikes raised. */
    private void sniff(TeemingRenderState s, float t) {
        float k = smooth(s.phaseTime / 3.0F);
        sink(SNIFF_DROP * k);
        neck.xRot -= 0.5F * k;                                           // the neck stretches up and out
        head.xRot += 0.3F * k;                                           // the nose tipped a little up, testing the air
        head.z -= 1.5F * k;
        head.y -= 0.8F * k;
        nose.xRot += (0.15F + Mth.sin(t * 2.6F) * 0.22F) * k;            // the nose pad twitches, fast
        nose.y += Mth.sin(t * 3.3F + 1.0F) * 0.3F * k;
        pouch.y += 0.4F * k;
        jaw.xRot += 0.08F * k;
        crest.xRot += 0.4F * k;                                          // the crest stood up off its backward lean
        for (int i = 0; i < fan.length; i++) fan[i].zRot += (i - 2) * 0.12F * k;   // and fanned wide
        for (ModelPart spike : spines) spike.xRot += 0.3F * k;
        tailSpine.xRot += 0.3F * k;
        tail.xRot += 0.08F * k;
        tail3.yRot += Mth.sin(t * 1.4F) * 0.35F * k;                     // the tail tip flicks
    }

    /** The bite that took nothing: the head lunged in, the jaw snapped shut after a gape. */
    private void snap(TeemingRenderState s) {
        float k = envelope(s.progress, 0.15F, 0.4F);
        head.z -= 1.6F * k;
        neck.xRot += 0.15F * k;
        jaw.xRot += 0.7F * k * (1.0F - smooth(s.phaseTime / 2.0F));      // open as it strikes, shut by the second tick
        crest.xRot += 0.2F * k;
    }

    /** The getaway: a low, fast scurry, head stretched forward and level, crest and spikes flat, tail held up. */
    private void scurry(TeemingRenderState s, float t) {
        float a = s.phaseTime * 1.6F;
        stride(a, 1.4F);
        float k = smooth(s.phaseTime / 3.0F);
        sink(FLEE_DROP * k);
        body.yRot += Mth.sin(a) * 0.16F;                                 // a wide, wriggling sway
        tail.yRot -= Mth.sin(a) * 0.35F;
        tail2.yRot -= Mth.sin(a - 0.7F) * 0.4F;
        tail.xRot += 0.18F * k;                                          // the tail held up off the ground
        tail3.yRot -= Mth.sin(a - 1.4F) * 0.4F;
        neck.xRot += 0.35F * k;                                          // the neck stretched forward, low
        head.xRot -= 0.3F * k;                                           // the head level, its loot on show
        head.yRot = 0.0F;
        neck.yRot = -Mth.sin(a) * 0.12F;                                 // the head held steady against the sway
        crest.xRot -= 0.5F * k;                                          // the crest laid back flat
        for (int i = 0; i < fan.length; i++) fan[i].zRot -= (i - 2) * 0.08F * k;
        for (ModelPart spike : spines) spike.xRot -= 0.4F * k;
        tailSpine.xRot -= 0.3F * k;
        pouch.y += Mth.sin(t * 0.6F) * 0.3F;                             // panting
    }
}
