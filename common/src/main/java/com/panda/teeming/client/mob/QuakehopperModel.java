package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Quakehopper.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Quakehopper: a heavy waddle with a bob; for the tell it sinks between its haunches, knees up and trembling (the leg
 * glow comes from its glow stage); in the air its hind legs trail straight out behind it and the forelegs reach
 * ahead; landed it squashes flat with its legs splayed while the ring runs; spent, it slumps with its head hung and its
 * legs dragging. A kick throws both hind feet forward as it rocks back.
 *
 * <p>Everything hangs under {@code body}. The legs are its children, so where the body sinks the hips are lifted back
 * by the same amount and the feet stay on the ground.
 */
public class QuakehopperModel extends TeemingModel {
    /** How far the body sinks in the crouch, flat on landing, and slumped when spent, in model pixels. */
    private static final float CROUCH_DROP = 2.5F;
    private static final float SQUASH_DROP = 2.2F;
    private static final float SLUMP_DROP = 1.2F;

    private final ModelPart body, head, columns, gland;
    private final ModelPart[] arms = new ModelPart[2];
    private final ModelPart[] thighs = new ModelPart[2];
    private final ModelPart[] shins = new ModelPart[2];
    private final ModelPart[] feet = new ModelPart[2];

    public QuakehopperModel(ModelPart root) {
        super(root, CreatureLayers.QUAKEHOPPER_ONLY);
        body = part("body");
        head = part("head");
        columns = part("columns");
        gland = part("gland");
        String[] sides = {"right", "left"};
        for (int i = 0; i < 2; i++) {
            arms[i] = part("arm_" + sides[i]);
            thighs[i] = part("thigh_" + sides[i]);
            shins[i] = part("shin_" + sides[i]);
            feet[i] = part("foot_" + sides[i]);
        }
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        gland.y += Mth.sin(t * 0.1F) * 0.15F;                         // the slag gland swells and settles
        if (s.phase == Step.TELL.ordinal()) {
            crouch(s);
        } else if (s.phase == Step.HOP.ordinal()) {
            leap(s);
        } else if (s.phase == Step.QUAKE.ordinal()) {
            squash(s);
        } else if (s.phase == Step.SPENT.ordinal()) {
            slump(s, t);
            walk(s, t, pos, speed * 0.6F);
        } else {
            walk(s, t, pos, speed);
        }
    }

    /** Sinks the body by {@code drop} pixels, lifting the hips and forelegs back so the feet stay planted. */
    private void sink(float drop) {
        body.y += drop;
        for (int i = 0; i < 2; i++) {
            thighs[i].y -= drop;
            arms[i].y -= drop;
        }
    }

    /** The tell: down between its haunches, nose up, knees raised and trembling with the strain. */
    private void crouch(TeemingRenderState s) {
        float k = smooth(s.phaseTime / 5.0F);                          // down within a quarter second, then held
        sink(CROUCH_DROP * k);
        body.xRot -= 0.12F * k;
        body.zRot += Mth.sin(s.phaseTime * 2.6F) * 0.035F * k;        // shaking with the load
        head.xRot -= 0.15F * k;
        columns.y += 0.4F * k;
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? -1.0F : 1.0F;
            thighs[i].xRot -= 0.18F * k;                                 // knees forward and up
            thighs[i].zRot += sx * 0.12F * k;                           // haunches flaring out
            shins[i].xRot += 0.18F * k;
            arms[i].zRot += sx * 0.25F * k;                             // forelegs braced wide
        }
    }

    /** In the air: hind legs trailing out behind, toes pointing back; forelegs reaching forward; nose up. */
    private void leap(TeemingRenderState s) {
        float k = smooth(s.phaseTime / 3.0F);                          // the legs snap straight at take-off
        body.xRot -= 0.3F * k;
        head.xRot += 0.15F * k;
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? -1.0F : 1.0F;
            thighs[i].xRot += 0.75F * k;
            thighs[i].zRot += sx * 0.3F * k;                           // and a little apart, so they show from ahead
            shins[i].xRot -= 0.85F * k;
            feet[i].xRot += 2.4F * k;
            arms[i].xRot -= 1.1F * k;
        }
    }

    /** Landed: squashed flat, legs splayed out to the sides, a hard jolt in the first ticks, then still while the ring runs. */
    private void squash(TeemingRenderState s) {
        float jolt = 1.0F - smooth(s.phaseTime / 5.0F);
        sink(SQUASH_DROP + 0.6F * jolt);                           // the belly just meets the ground
        body.zRot += Mth.sin(s.phaseTime * 3.1F) * 0.05F * jolt;
        head.xRot += 0.1F;
        columns.y -= 0.6F * jolt;                                       // the columns rattle on its back
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? -1.0F : 1.0F;
            thighs[i].zRot += sx * 0.35F;
            feet[i].zRot -= sx * 0.25F;
            arms[i].zRot += sx * 0.55F;
            arms[i].xRot -= 0.25F;
        }
    }

    /** Springs spent: slumped low, head hung, the haunches sagging out, breathing heavily. */
    private void slump(TeemingRenderState s, float t) {
        sink(SLUMP_DROP + Mth.sin(t * 0.18F) * 0.3F);
        head.xRot += 0.3F;
        body.xRot += 0.08F;
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? -1.0F : 1.0F;
            thighs[i].zRot += sx * 0.22F;
            feet[i].zRot -= sx * 0.15F;
        }
    }

    /** The walk: a heavy waddle that bobs with each push of the hind legs; the head turns to what it watches; the kick. */
    private void walk(TeemingRenderState s, float t, float pos, float speed) {
        float push = Math.max(0.0F, Mth.sin(pos * 0.6F));
        body.y -= push * 1.2F * speed;
        body.zRot += Mth.cos(pos * 0.6F) * 0.05F * speed;
        arms[0].xRot += Mth.cos(pos * 1.2F) * 0.7F * speed;
        arms[1].xRot -= Mth.cos(pos * 1.2F) * 0.7F * speed;
        for (int i = 0; i < 2; i++) {
            thighs[i].xRot += push * 0.35F * speed;
            feet[i].xRot -= push * 0.2F * speed;
        }
        head.yRot += Mth.clamp(s.yRot, -40.0F, 40.0F) * DEG * 0.6F;
        head.xRot += Mth.clamp(s.xRot, -30.0F, 30.0F) * DEG * 0.4F;
        body.y += Mth.sin(t * 0.08F) * 0.15F;                          // breathing at rest
        if (s.attack > 0.0F) {                                        // both hind feet thrown forward, rocking back
            float a = Mth.sin(s.attack * Mth.PI);
            body.xRot -= 0.35F * a;
            for (int i = 0; i < 2; i++) {
                thighs[i].xRot -= 0.8F * a;
                shins[i].xRot -= 0.3F * a;
                feet[i].xRot -= 0.4F * a;
            }
        }
    }
}
