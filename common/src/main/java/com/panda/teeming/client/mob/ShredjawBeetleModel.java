package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.ShredjawBeetle.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Shredjaw Beetle: a heavy beetle on six jointed legs. The tell throws its saw-toothed mandibles wide open and grinds
 * them (a fast tremble), its head and the front of its shell rearing up; the bite slams them shut past each other
 * with a lunge; a blocked bite tosses its head up and back with the jaws hanging slack while the shell rocks.
 *
 * <p>Each leg is hip (Y: swings fore and aft) → thigh (Z: lifts the knee) → shin (Z: bends the foot down). The walk is
 * an alternating tripod: left 1 and 3 with right 2, then right 1 and 3 with left 2, half a stride apart.
 */
public class ShredjawBeetleModel extends TeemingModel {
    private static final float STRIDE = 1.1F;       // leg cycles per unit of walk distance
    private static final float LIFT = 0.55F;        // thigh lift while a leg swings
    private static final float SWING = 0.4F;        // fore-and-aft swing at the hip
    private static final float SPREAD = 0.85F;      // how far each mandible swings out in the tell, radians

    private final ModelPart body, head, jawLeft, jawRight, tipLeft, tipRight;
    /** Legs 0..2 are the left side front to rear, 3..5 the right side. */
    private final ModelPart[] hips = new ModelPart[6], thighs = new ModelPart[6], shins = new ModelPart[6];

    public ShredjawBeetleModel(ModelPart root) {
        super(root, CreatureLayers.SHREDJAW_BEETLE_ONLY);
        body = part("body");
        head = part("head");
        jawLeft = part("jaw_left");
        jawRight = part("jaw_right");
        tipLeft = part("jaw_left_tip");
        tipRight = part("jaw_right_tip");
        for (int i = 0; i < 6; i++) {
            String name = "leg_" + (i < 3 ? "left" : "right") + "_" + (i % 3 + 1);
            hips[i] = part(name);
            thighs[i] = part(name + "_thigh");
            shins[i] = part(name + "_shin");
        }
    }

    /** +1 for the left side (+X), -1 for the right. */
    private static float side(int leg) {
        return leg < 3 ? 1.0F : -1.0F;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        walk(pos, speed);
        idle(s, t);
        if (s.phase == Step.TELL.ordinal()) {
            gape(smooth(s.progress * 3.0F), s.phaseTime);
        } else if (s.phase == Step.BITE.ordinal()) {
            bite(s.progress);
        } else if (s.phase == Step.JARRED.ordinal()) {
            jarred(s.progress, s.phaseTime);
        }
    }

    private void walk(float pos, float speed) {
        if (speed < 0.01F) return;
        float stride = pos * STRIDE;
        for (int i = 0; i < 6; i++) {
            float side = side(i);
            boolean second = ((i % 3) + (i < 3 ? 0 : 1)) % 2 == 1;
            float phase = stride + (second ? Mth.PI : 0.0F);
            float swing = Mth.sin(phase);
            thighs[i].zRot -= side * Math.max(0.0F, Mth.cos(phase)) * LIFT * speed;    // lift while it swings forward
            hips[i].yRot += side * swing * SWING * speed;
        }
        body.y += Mth.sin(stride * 2.0F) * 0.2F * speed;
        body.zRot += Mth.cos(stride) * 0.025F * speed;
    }

    private void idle(TeemingRenderState s, float t) {
        head.yRot = Mth.clamp(s.yRot, -30.0F, 30.0F) * DEG * 0.5F;
        head.xRot = Mth.clamp(s.xRot, -20.0F, 20.0F) * DEG * 0.5F;
        float scissor = (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.06F)), 4.0) * 0.18F;   // now and then the jaws work
        jawLeft.yRot -= scissor;
        jawRight.yRot += scissor;
        body.y += Mth.sin(t * 0.08F) * 0.12F;
    }

    /** The tell: mandibles flung wide and grinding, head and shell front rearing up. */
    private void gape(float k, float time) {
        float grind = Mth.sin(time * 3.1F) * 0.06F * k;
        jawLeft.yRot -= SPREAD * k + grind;
        jawRight.yRot += SPREAD * k + grind;
        tipLeft.yRot += 0.25F * k;              // the curved tips hook further in, teeth bared
        tipRight.yRot -= 0.25F * k;
        jawLeft.xRot -= 0.2F * k;
        jawRight.xRot -= 0.2F * k;
        head.xRot -= 0.3F * k;
        head.y -= 0.6F * k;
        body.xRot -= 0.12F * k;
        body.y -= 0.5F * k;
        for (int i = 0; i < 6; i++) {
            if (i % 3 == 2) thighs[i].zRot -= side(i) * 0.15F * k;   // the rear legs brace
        }
    }

    /**
     * The bite: from wide open the jaws slam shut past each other in a few frames and stay crossed, head dipped and
     * thrust forward, the whole body lunging; it eases back over the last third.
     */
    private void bite(float progress) {
        float shut = smooth(progress * 4.0F);
        float open = 1.0F - shut;
        float hold = envelope(progress, 0.15F, 0.35F);
        jawLeft.yRot -= SPREAD * open - 0.2F * hold;           // crossed: the blades scissor past each other
        jawRight.yRot += SPREAD * open - 0.2F * hold;
        tipLeft.yRot += 0.15F * hold;
        tipRight.yRot -= 0.15F * hold;
        body.z -= 2.0F * hold;
        body.xRot += 0.1F * hold;
        body.y += 0.4F * hold;
        head.xRot += 0.3F * hold;
        head.z -= 1.0F * hold;
    }

    /**
     * A blocked bite: the jaws clamp shut and hang slack, the head is tossed up and shakes from side to side, the shell tips back and
     * rocks, settling as it recovers. Nothing is spread, so it never looks like the tell.
     */
    private void jarred(float progress, float time) {
        float k = envelope(progress, 0.08F, 0.4F);
        float fade = 1.0F - progress;
        head.xRot -= 0.5F * k;
        head.yRot += Mth.sin(time * 1.3F) * 0.35F * fade * k;
        head.z += 1.0F * k;
        jawLeft.yRot += 0.12F * k;
        jawRight.yRot -= 0.12F * k;
        jawLeft.xRot += 0.3F * k;             // slack: they droop from the tossed head
        jawRight.xRot += 0.3F * k;
        body.xRot -= 0.22F * k;
        body.z += 1.5F * k;
        body.zRot += Mth.sin(time * 0.9F) * 0.12F * fade;
        for (int i = 0; i < 6; i++) {
            if (i % 3 == 0) thighs[i].zRot -= side(i) * 0.4F * k;   // the front legs splay up off the ground
        }
    }
}
