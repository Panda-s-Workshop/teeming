package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.ScarletMantis;
import com.panda.teeming.mob.nether.ScarletMantis.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Scarlet Mantis: an upright mantis on four long legs, its scythe arms folded before its chest. It walks with the legs
 * in diagonal pairs and its abdomen swaying; its head turns to what it watches and its scythes sway a little at rest.
 * The slash raises both scythes overhead and brings them down and forward, the blades flicking open.
 *
 * <p>The tell raises the scythes crossed in an X before its face, whetting the blades against each other as they rise.
 * The stance locks that X still, wings flared up off its back and abdomen curled, the whole body sunk a pixel; a caught
 * blow snaps the blades tight with a jerk of the head (the light on the edges comes from its glow stage).
 *
 * <p>Each arm is coxa ({@code arm_*}, X: lifts it forward and up) → femur (X: tilts it up; Y: leans it in across the
 * face once the arm is raised) → tibia, the blade (X: opens it off the femur). Each leg is hip (Y: swings fore and
 * aft) → thigh (Z: lifts the knee) → shin.
 */
public class ScarletMantisModel extends TeemingModel {
    private static final float STRIDE = 0.9F;       // leg cycles per unit of walk distance
    private static final float LIFT = 0.45F;        // thigh lift while a leg swings
    private static final float SWING = 0.35F;       // fore-and-aft swing at the hip

    /** The crossed guard, as rotations added to the rest pose, in degrees. */
    private static final float GUARD_ARM = -62.0F, GUARD_FEMUR = 36.0F, GUARD_CROSS = 32.0F, GUARD_TIBIA = 8.0F;

    private final ModelPart body, abdomen, wings, neck, head, antennaLeft, antennaRight;
    /** Index 0 is the left arm (+X), 1 the right. */
    private final ModelPart[] arms = new ModelPart[2], femurs = new ModelPart[2], tibias = new ModelPart[2];
    /** Legs 0, 1 are the left middle and hind, 2, 3 the right middle and hind. */
    private final ModelPart[] hips = new ModelPart[4], thighs = new ModelPart[4];

    public ScarletMantisModel(ModelPart root) {
        super(root, CreatureLayers.SCARLET_MANTIS_ONLY);
        body = part("body");
        abdomen = part("abdomen");
        wings = part("wings");
        neck = part("neck");
        head = part("head");
        antennaLeft = part("antenna_left");
        antennaRight = part("antenna_right");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            arms[i] = part("arm_" + sides[i]);
            femurs[i] = part("femur_" + sides[i]);
            tibias[i] = part("tibia_" + sides[i]);
        }
        for (int i = 0; i < 4; i++) {
            String name = "leg_" + sides[i / 2] + "_" + (i % 2 + 1);
            hips[i] = part(name);
            thighs[i] = part(name + "_thigh");
        }
    }

    /** +1 for the left side (+X), -1 for the right. */
    private static float side(int index) {
        return index == 0 ? 1.0F : -1.0F;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        walk(pos, speed);
        look(s, t);
        if (s.phase == Step.TELL.ordinal()) {
            tell(s, t);
        } else if (s.phase == Step.STANCE.ordinal()) {
            stance(s, t);
        } else {
            rest(s, t);
        }
    }

    /** Diagonal pairs: left middle with right hind, then right middle with left hind; the abdomen sways. */
    private void walk(float pos, float speed) {
        if (speed < 0.01F) return;
        float stride = pos * STRIDE;
        for (int i = 0; i < 4; i++) {
            float side = i < 2 ? 1.0F : -1.0F;
            boolean second = (i == 1 || i == 2);
            float phase = stride + (second ? Mth.PI : 0.0F);
            thighs[i].zRot -= side * Math.max(0.0F, Mth.cos(phase)) * LIFT * speed;
            hips[i].yRot += side * Mth.sin(phase) * SWING * speed;
        }
        body.y += Mth.sin(stride * 2.0F) * 0.25F * speed;
        abdomen.yRot += Mth.sin(stride) * 0.08F * speed;
        neck.zRot += Mth.cos(stride) * 0.03F * speed;
    }

    /** The head turns to what it watches; the antennae twitch; the abdomen breathes. */
    private void look(TeemingRenderState s, float t) {
        head.yRot += Mth.clamp(s.yRot, -50.0F, 50.0F) * DEG * 0.8F;
        head.xRot += Mth.clamp(s.xRot, -30.0F, 30.0F) * DEG * 0.6F;
        antennaLeft.xRot += Mth.sin(t * 0.13F) * 0.08F;
        antennaRight.xRot += Mth.sin(t * 0.11F + 1.3F) * 0.08F;
        abdomen.xRot += Mth.sin(t * 0.07F) * 0.03F;
    }

    /** At rest the folded scythes sway a little; the slash raises them overhead and brings them down, blades open. */
    private void rest(TeemingRenderState s, float t) {
        for (int i = 0; i < 2; i++) {
            arms[i].xRot += Mth.sin(t * 0.08F + i * 0.6F) * 0.04F;
        }
        if (s.attack > 0.0F) {
            float a = s.attack;
            float up = a < 0.4F ? smooth(a / 0.4F) : 1.0F - smooth((a - 0.4F) / 0.6F);   // overhead, then down
            float down = a < 0.4F ? 0.0F : Mth.sin((a - 0.4F) / 0.6F * Mth.PI);          // and forward through the strike
            for (int i = 0; i < 2; i++) {
                arms[i].xRot += (-95.0F * up - 40.0F * down) * DEG;
                femurs[i].xRot += (-20.0F * up - 50.0F * down) * DEG;
                tibias[i].xRot += (-70.0F * up - 40.0F * down) * DEG;
            }
            neck.xRot += 0.12F * down;
        }
    }

    /** Sinks the body by {@code drop} pixels, lifting the hips back so the feet stay planted. */
    private void sink(float drop) {
        body.y += drop;
        for (ModelPart hip : hips) hip.y -= drop;
    }

    /** Sets the arms toward the crossed guard by {@code k} (0 rest .. 1 crossed before the face). */
    private void guard(float k) {
        for (int i = 0; i < 2; i++) {
            arms[i].xRot += GUARD_ARM * DEG * k;
            femurs[i].xRot += GUARD_FEMUR * DEG * k;
            femurs[i].yRot += side(i) * GUARD_CROSS * DEG * k;
            tibias[i].xRot += GUARD_TIBIA * DEG * k;
        }
        head.xRot += 8.0F * DEG * k;
    }

    /** The tell: the scythes rise crossed before its face, the blades whetting against each other as they go. */
    private void tell(TeemingRenderState s, float t) {
        float k = smooth(s.phaseTime / 8.0F);
        guard(k);
        float whet = Mth.sin(s.phaseTime * 2.4F) * 0.12F * k;   // the blades rub up and down past each other
        tibias[0].xRot += whet;
        tibias[1].xRot -= whet;
        femurs[0].yRot += whet * 0.4F;
        femurs[1].yRot += whet * 0.4F;
        sink(-0.3F * k);
    }

    /** The stance: the X locked still, wings flared, abdomen curled up, sunk a pixel; a caught blow snaps the blades. */
    private void stance(TeemingRenderState s, float t) {
        float k = smooth(s.phaseTime / 3.0F);
        guard(1.0F);
        wings.xRot += 22.0F * DEG * k;
        abdomen.xRot += 12.0F * DEG * k;
        sink(1.0F * k);
        for (int i = 0; i < 4; i++) thighs[i].zRot += (i < 2 ? 1.0F : -1.0F) * 0.06F * k;   // knees spread under the weight
        float tremble = Mth.sin(t * 2.9F) * 0.015F;
        femurs[0].yRot += tremble;
        femurs[1].yRot -= tremble;
        if (s.flag(ScarletMantis.FLAG_PARRIED)) {
            for (int i = 0; i < 2; i++) {
                arms[i].xRot -= 8.0F * DEG;
                femurs[i].xRot += 4.0F * DEG;
                femurs[i].yRot += side(i) * 4.0F * DEG;
                tibias[i].xRot += 6.0F * DEG;
            }
            head.xRot -= 16.0F * DEG;
            neck.xRot -= 0.06F;
        }
    }
}
