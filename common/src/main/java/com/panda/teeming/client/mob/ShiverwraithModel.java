package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Shiverwraith.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Shiverwraith: it floats a hand's width above the snow and bobs, its tatters swaying and trailing as it drifts. The
 * tell raises both arms toward the target with a tremble while the ice collar bristles and the core brightens; the
 * tether holds the arms out straight, palms up, leaning back as it pulls; a snap throws it back with its arms flung wide.
 */
public class ShiverwraithModel extends TeemingModel {
    /** How far it floats above its rest pose, in model pixels, and the bob on top of that. */
    private static final float HOVER = 2.0F, BOB = 1.0F;
    /** The arms held out at the target: straight ahead, a touch above level. */
    private static final float REACH = -100.0F * DEG, HOLD = -90.0F * DEG;

    private final ModelPart body, core, robe, tatters, head, mantle, armRight, armLeft, handRight, handLeft;

    public ShiverwraithModel(ModelPart root) {
        super(root, CreatureLayers.SHIVERWRAITH_ONLY);
        body = part("body");
        core = part("core");
        robe = part("robe");
        tatters = part("tatters");
        head = part("head");
        mantle = part("mantle");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        body.y -= HOVER + BOB * Mth.sin(t * 0.1F);
        body.xRot += 0.2F * speed;                                           // leans into its drift
        robe.xRot += 0.12F * speed + Mth.sin(t * 0.1F + 0.8F) * 0.05F;
        tatters.xRot += 0.35F * speed + Mth.sin(t * 0.12F + 1.6F) * 0.12F;   // the tail trails and sways
        tatters.zRot += Mth.sin(t * 0.07F) * 0.06F;
        float sway = Mth.sin(t * 0.08F) * 0.08F;
        armRight.xRot += sway + Mth.cos(pos * 0.6F) * 0.3F * speed;
        armLeft.xRot += -sway - Mth.cos(pos * 0.6F) * 0.3F * speed;
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        float glow = 1.0F + 0.06F * Mth.sin(t * 0.15F);
        core.xScale = core.yScale = core.zScale = glow;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);
            float tremble = Mth.sin(s.phaseTime * 2.5F) * 0.06F * k;
            reach(k, REACH + tremble, 0.22F);
            head.xRot -= 0.15F * k;
            float bristle = 1.0F + 0.15F * k;
            mantle.xScale = mantle.yScale = mantle.zScale = bristle;
            float pulse = 1.0F + 0.35F * k * (0.5F + 0.5F * Mth.sin(s.phaseTime * 2.0F));
            core.xScale = core.yScale = core.zScale = pulse;
        } else if (s.phase == Step.TETHER.ordinal()) {
            float k = smooth(s.phaseTime / 4.0F);
            reach(k, HOLD + Mth.sin(s.phaseTime * 1.7F) * 0.03F, 0.15F);
            handRight.xRot -= 0.5F * k;                                      // palms turned up at the target
            handLeft.xRot -= 0.5F * k;
            body.xRot -= 0.12F * k;                                          // leaning back as it pulls
            tatters.xRot += (0.25F + Mth.sin(s.phaseTime * 0.5F) * 0.1F) * k;
            mantle.xScale = mantle.yScale = mantle.zScale = 1.0F + 0.12F * k;
            float pulse = 1.25F + 0.15F * Mth.sin(s.phaseTime * 0.6F);
            core.xScale = core.yScale = core.zScale = Mth.lerp(k, glow, pulse);
        } else if (s.phase == Step.SNAP.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            armRight.xRot += 0.6F * k;                                       // arms flung back and wide
            armLeft.xRot += 0.6F * k;
            armRight.zRot += 0.9F * k;
            armLeft.zRot -= 0.9F * k;
            body.xRot -= 0.35F * k;                                          // thrown back by the snap
            body.zRot += Mth.sin(s.phaseTime * 3.0F) * 0.1F * k;
            head.xRot -= 0.4F * k;
            tatters.xRot -= 0.3F * k;
            float shrink = 1.0F - 0.3F * k;
            core.xScale = core.yScale = core.zScale = shrink;
        }
        if (s.attack > 0.0F) {                                               // the claw: both arms rake down
            float a = Mth.sin(s.attack * Mth.PI);
            armRight.xRot -= 1.8F * a;
            armLeft.xRot -= 1.8F * a;
            armRight.yRot -= 0.3F * a;
            armLeft.yRot += 0.3F * a;
        }
    }

    /** Both arms out at {@code pitch}, hands drawn {@code inward} toward the middle, blended in by {@code k}. */
    private void reach(float k, float pitch, float inward) {
        armRight.xRot = Mth.lerp(k, armRight.xRot, pitch);
        armLeft.xRot = Mth.lerp(k, armLeft.xRot, pitch);
        armRight.yRot = Mth.lerp(k, armRight.yRot, -inward);
        armLeft.yRot = Mth.lerp(k, armLeft.yRot, inward);
        armRight.zRot = Mth.lerp(k, armRight.zRot, 0.0F);
        armLeft.zRot = Mth.lerp(k, armLeft.zRot, 0.0F);
    }
}
