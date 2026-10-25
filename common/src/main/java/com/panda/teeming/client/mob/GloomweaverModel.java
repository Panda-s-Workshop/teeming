package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Gloomweaver;
import com.panda.teeming.mob.nether.Gloomweaver.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Gloomweaver: a spider's walk on eight long legs, the abdomen breathing and its vine strands swaying behind. Tell (the
 * spin): it rears, front up on its hind legs, the front pair raised, the abdomen curled up and the spinnerets whirling
 * with the strands whipping round them. Weave: the abdomen arches high over its back, spinnerets aimed ahead, the
 * strands streaming forward, legs braced wide. Snapped: the abdomen sags, the spinnerets droop and the strands hang
 * limp; its legs twitch. The bite: fangs flick forward and in with a short lunge.
 *
 * <p>Each leg is hip (Y: swings fore and aft) → thigh (Z: lifts the knee) → shin (Z: reaches the foot down), as the
 * Shellguard Crab's. The walk is an alternating tetrapod: left 1 and 3 with right 2 and 4, then the other four, half a
 * stride apart; a leg lifts its knee while it swings forward.
 */
public class GloomweaverModel extends TeemingModel {
    private static final float STRIDE = 1.1F;       // leg cycles per unit of walk distance
    private static final float SWING = 0.32F;       // fore-and-aft swing at the hip
    private static final float LIFT = 0.4F;         // knee lift while a leg swings forward

    private final ModelPart body, head, fangLeft, fangRight, abdomen, spinnerets, vines;
    private final ModelPart vineMid, vineLeft, vineRight;
    /** Legs 0..3 are the left side front to rear, 4..7 the right side. */
    private final ModelPart[] hips = new ModelPart[8], thighs = new ModelPart[8], shins = new ModelPart[8];

    public GloomweaverModel(ModelPart root) {
        super(root, CreatureLayers.GLOOMWEAVER_ONLY);
        body = part("body");
        head = part("head");
        fangLeft = part("fang_left");
        fangRight = part("fang_right");
        abdomen = part("abdomen");
        spinnerets = part("spinnerets");
        vines = part("vines");
        vineMid = part("vine_mid");
        vineLeft = part("vine_left");
        vineRight = part("vine_right");
        for (int i = 0; i < 8; i++) {
            String name = "leg_" + (i < 4 ? "left" : "right") + "_" + (i % 4 + 1);
            hips[i] = part(name);
            thighs[i] = part(name + "_thigh");
            shins[i] = part(name + "_shin");
        }
    }

    /** +1 for the left side (+X), -1 for the right. */
    private static float side(int leg) {
        return leg < 4 ? 1.0F : -1.0F;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        walk(pos, speed);
        idle(t);
        if (s.phase == Step.TELL.ordinal()) {
            spin(s, t);
        } else if (s.phase == Step.WEAVE.ordinal()) {
            weave(s, t);
        } else if (s.phase == Step.SNAPPED.ordinal()) {
            snapped(s, t);
        }
        bite(s.attack);
    }

    private void walk(float pos, float speed) {
        if (speed < 0.01F) return;
        float stride = pos * STRIDE;
        for (int i = 0; i < 8; i++) {
            float side = side(i);
            boolean second = ((i % 4) + (i < 4 ? 0 : 1)) % 2 == 1;
            float phase = stride + (second ? Mth.PI : 0.0F);
            hips[i].yRot += side * Mth.sin(phase) * SWING * speed;
            thighs[i].zRot -= side * Math.max(0.0F, Mth.cos(phase)) * LIFT * speed;
        }
        body.y += Mth.sin(stride * 2.0F) * 0.3F * speed;
        abdomen.yRot += Mth.sin(stride) * 0.06F * speed;
    }

    private void idle(float t) {
        abdomen.xRot += Mth.sin(t * 0.08F) * 0.025F;                      // breathing
        vines.yRot += Mth.sin(t * 0.06F) * 0.12F;
        vineMid.xRot += Mth.sin(t * 0.07F) * 0.06F;
        vineLeft.zRot += Mth.sin(t * 0.09F + 1.0F) * 0.08F;
        vineRight.zRot += Mth.sin(t * 0.08F + 2.3F) * 0.08F;
        float flick = (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.05F)), 8.0);   // now and then the fangs twitch
        fangLeft.xRot -= flick * 0.3F;
        fangRight.xRot -= flick * 0.3F;
    }

    /** The spin: it rears, front pair high, abdomen curled up, spinnerets whirling and the strands whipping round. */
    private void spin(TeemingRenderState s, float t) {
        float k = smooth(s.phaseTime / 5.0F);
        body.xRot -= 0.38F * k;                                          // front up
        body.y -= 2.0F * k;                                              // up on its legs
        abdomen.xRot += 0.95F * k;                                       // tail curled up, spinnerets showing over its back
        for (int i = 0; i < 8; i++) {
            float side = side(i);
            int n = i % 4;
            if (n == 0) {                                                // the front pair raised and reaching
                thighs[i].zRot -= side * 0.75F * k;
                hips[i].yRot += side * 0.25F * k;
                shins[i].zRot -= side * 0.35F * k;
            } else if (n == 3) {                                         // the hind pair braced under it
                shins[i].zRot += side * 0.2F * k;
            }
        }
        spinnerets.zRot += s.phaseTime * 0.9F * k;                       // whirling
        vines.yRot += Mth.sin(s.phaseTime * 0.9F) * 0.7F * k;           // the strands whipped round with them
        vineMid.xRot -= 0.5F * k;
        vineLeft.xRot -= 0.4F * k;
        vineRight.xRot -= 0.4F * k;
        fangLeft.xRot -= 0.35F * k;
        fangRight.xRot -= 0.35F * k;
    }

    /** The weave: abdomen arched high over its back, spinnerets aimed ahead, strands streaming forward, legs braced. */
    private void weave(TeemingRenderState s, float t) {
        float k = envelope(s.progress, 0.25F, 0.35F);
        body.y += 0.8F * k;                                              // low and braced
        abdomen.xRot += 0.85F * k;
        spinnerets.xRot += 0.5F * k;
        vineMid.xRot += 1.6F * k;                                        // from hanging behind to streaming over its back
        vineLeft.xRot += 1.5F * k;
        vineRight.xRot += 1.5F * k;
        vines.yRot += Mth.sin(t * 1.4F) * 0.15F * k;
        for (int i = 0; i < 8; i++) {
            float side = side(i);
            hips[i].yRot += side * (i % 4 < 2 ? 0.18F : -0.18F) * k;      // fanned wider
            shins[i].zRot += side * 0.15F * k;
        }
    }

    /** Snapped: the abdomen sags to the ground, spinnerets drooping, strands limp; the legs twitch. */
    private void snapped(TeemingRenderState s, float t) {
        float in = s.phaseTime / 6.0F;
        float out = (1.0F - s.progress) * Gloomweaver.SNAPPED_TICKS / 10.0F;
        float k = smooth(Math.min(in, out));
        abdomen.xRot -= 0.2F * k;
        body.y += 0.6F * k;
        spinnerets.xRot -= 0.7F * k;
        vineMid.xRot -= 0.45F * k;
        vineLeft.xRot -= 0.35F * k;
        vineRight.xRot -= 0.35F * k;
        vines.yRot *= 1.0F - k;
        for (int i = 0; i < 8; i++) {
            shins[i].zRot += side(i) * 0.06F * Mth.sin(t * 1.3F + i * 1.9F) * k;
        }
        head.xRot += 0.12F * k;
    }

    /** The bite: fangs flick forward and in, the front lunges a little. */
    private void bite(float attack) {
        if (attack <= 0.0F) return;
        float a = Mth.sin(attack * Mth.PI);
        fangLeft.xRot -= 0.9F * a;
        fangRight.xRot -= 0.9F * a;
        fangLeft.zRot -= 0.25F * a;
        fangRight.zRot += 0.25F * a;
        body.z -= 1.2F * a;
        head.xRot += 0.15F * a;
    }
}
