package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.EchoFencer;
import com.panda.teeming.mob.end.EchoFencer.Afterimage;
import com.panda.teeming.mob.end.EchoFencer.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Echo Fencer, drawn see-through where its afterimage is: a light, upright walk in a fencer's guard, the rapier held
 * out level, the back arm raised behind its head. Tell: it draws back, leaning away with its sword arm pulled in and the
 * blade still level, a star glinting at the tip. Thrust: a long lunge, the front foot far forward, the back leg straight,
 * the arm and blade one line at chest height. Recover: it springs back into its guard as it steps aside.
 *
 * <p>The afterimage is a second copy of the figure under the root part {@code ghost}, placed each frame where the
 * fencer's synced flags put it in the world (turned from the fencer's body frame into model space, so it stays put
 * however the fencer moves or turns), facing the way it struck. Through the recovery it holds the lunge, then draws back
 * with its own glint for the last 10 ticks; in the echo it lunges, holds, and crumbles to the ground. A pose with no
 * afterimage behind it (the harness poses each phase on a still mob) shows one a block to the fencer's right.
 */
public class EchoFencerModel extends TeemingModel {
    private static final float PX = 16.0F;
    /** The afterimage in a pose with nothing behind it: this many blocks to the fencer's right, facing the same way. */
    private static final float POSE_RIGHT = 1.1F;

    /** One figure's parts: the fencer's, or its afterimage's ({@code ghost_} names). */
    private final class Figure {
        final ModelPart hips, body, legLeft, legRight, tailBack, tailLeft, tailRight, collarBack, head;
        final ModelPart armRight, rapier, glint, pommel, armLeft, forearmLeft;

        Figure(String p) {
            hips = part(p + "hips");
            body = part(p + "body");
            legLeft = part(p + "leg_left");
            legRight = part(p + "leg_right");
            tailBack = part(p + "tail_back");
            tailLeft = part(p + "tail_left");
            tailRight = part(p + "tail_right");
            collarBack = part(p + "collar_back");
            head = part(p + "head");
            armRight = part(p + "arm_right");
            rapier = part(p + "rapier");
            glint = part(p + "glint");
            pommel = part(p + "pommel");
            armLeft = part(p + "arm_left");
            forearmLeft = part(p + "forearm_left");
        }
    }

    private final Figure me, ghost;
    private final ModelPart ghostRoot;

    public EchoFencerModel(ModelPart root) {
        super(root, CreatureLayers.ECHO_FENCER_ONLY, RenderTypes::entityTranslucent);
        me = new Figure("");
        ghost = new Figure("ghost_");
        ghostRoot = part("ghost");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean whole = (s.flags >> EchoFencer.CRACKED_BIT & 1) == 0;
        me.pommel.visible = whole;
        ghost.pommel.visible = whole;
        me.glint.visible = false;
        ghost.glint.visible = false;
        ghostRoot.visible = false;

        stance(me);
        stance(ghost);
        walk(me, t, pos, speed);
        me.head.yRot += s.yRot * DEG * 0.8F;
        me.head.xRot += s.xRot * DEG * 0.6F;

        float tm = s.phaseTime;
        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 3.0F);                          // drawn back in the first third, then held
            drawBack(me, k);
            glint(me, tm);
        } else if (s.phase == Step.THRUST.ordinal()) {
            lunge(me, smooth(tm));                                        // out in one tick, held
        } else if (s.phase == Step.RECOVER.ordinal()) {
            float u = s.progress * EchoFencer.RECOVER_TICKS;
            float back = smooth(u / EchoFencer.SIDESTEP_TICKS);
            lunge(me, 1.0F - back);                                       // springing back into its guard as it steps aside
            me.body.zRot += 0.12F * Mth.sin(back * Mth.PI);
            float tell = u - (EchoFencer.RECOVER_TICKS - EchoFencer.ECHO_TELL_TICKS);
            showGhost(s, 1.0F);
            if (tell < 0.0F) {
                lunge(ghost, 1.0F);                                       // frozen where it struck
            } else {
                float k = smooth(tell / 3.0F);                            // then it draws back in turn, glinting
                lunge(ghost, 1.0F - k);
                drawBack(ghost, k);
                glint(ghost, tell);
            }
        } else if (s.phase == Step.ECHO.ordinal()) {
            float v = s.progress * EchoFencer.ECHO_TICKS;
            float crumble = smooth((v - EchoFencer.ECHO_TICKS * 0.5F) / (EchoFencer.ECHO_TICKS * 0.5F));
            showGhost(s, 1.0F - 0.95F * crumble);
            lunge(ghost, smooth(v));                                      // the echo's lunge, then it sinks into sand
        }
    }

    /** The fencer's guard, on top of the rest pose: feet apart with the sword-side foot ahead, knees a little bent. */
    private static void stance(Figure f) {
        f.hips.y += 0.5F;
        f.legRight.xRot -= 0.22F;
        f.legLeft.xRot += 0.2F;
    }

    private static void walk(Figure f, float t, float pos, float speed) {
        float swing = Mth.cos(pos * 0.6F);
        f.legRight.xRot += swing * 0.7F * speed;
        f.legLeft.xRot -= swing * 0.7F * speed;
        f.hips.y += Mth.abs(swing) * 0.5F * speed + Mth.sin(t * 0.07F) * 0.2F;
        f.tailBack.xRot += 0.25F * speed + Mth.sin(t * 0.09F) * 0.03F;
        f.tailLeft.zRot -= 0.06F * Mth.abs(swing) * speed;
        f.tailRight.zRot += 0.06F * Mth.abs(swing) * speed;
        f.armRight.xRot += Mth.sin(t * 0.07F) * 0.03F;                   // the blade's point breathes
        f.forearmLeft.xRot += Mth.sin(t * 0.05F) * 0.05F;
    }

    /** Drawn back ({@code k} 0..1): leaning away, weight on the back foot, the sword arm pulled in, the blade kept level. */
    private static void drawBack(Figure f, float k) {
        f.hips.z += 1.5F * k;
        f.body.xRot -= 0.18F * k;
        f.armRight.xRot += 0.55F * k;
        f.armRight.yRot -= 0.25F * k;
        f.rapier.xRot -= 0.37F * k;
        f.legRight.xRot -= 0.15F * k;
        f.legLeft.xRot += 0.15F * k;
        f.armLeft.xRot += 0.2F * k;
        f.tailBack.xRot += 0.15F * k;
        f.collarBack.xRot -= 0.05F * k;
    }

    /** The lunge ({@code k} 0..1): front foot far forward, back leg straight, body low and forward, arm and blade one line. */
    private static void lunge(Figure f, float k) {
        if (k <= 0.0F) return;
        f.hips.z -= 4.0F * k;
        f.hips.y += 2.5F * k;
        f.legRight.xRot -= 0.6F * k;
        f.legLeft.xRot += 0.55F * k;
        f.body.xRot += 0.22F * k;
        f.armRight.xRot -= 0.78F * k;                                     // from the guard to level, the body's lean taken off
        f.armRight.yRot -= 0.14F * k;
        f.rapier.xRot += 0.7F * k;                                        // the blade straight out of the arm
        f.armLeft.xRot -= 1.3F * k;                                       // the back arm flung down behind
        f.forearmLeft.xRot += 0.9F * k;
        f.tailBack.xRot += 0.45F * k;
        f.tailLeft.zRot -= 0.15F * k;
        f.tailRight.zRot += 0.15F * k;
        f.head.xRot -= 0.15F * k;
    }

    /** The star at the blade's tip through a tell, pulsing. */
    private static void glint(Figure f, float tm) {
        f.glint.visible = true;
        float pulse = 0.8F + 0.45F * Mth.abs(Mth.sin(tm * 0.9F));
        f.glint.xScale = f.glint.yScale = f.glint.zScale = pulse;
        f.glint.yRot += tm * 0.4F;
    }

    /**
     * Shows the afterimage where the flags put it in the world, turned the way it struck, at {@code height} of its size
     * (shrinking toward its feet as it crumbles). The world offset from the fencer is turned into model space the way
     * the renderer turns the model into the world (a turn of 180 less the body's yaw, then X and Y flipped), and divided
     * by the fencer's scale.
     */
    private void showGhost(TeemingRenderState s, float height) {
        if (s.deathTime > 0.0F || height <= 0.02F) return;
        ghostRoot.visible = true;
        int flags = s.flags;
        float scale = s.scale > 0.0F ? s.scale : 1.0F;
        if (Afterimage.live(flags)) {
            double dx = Afterimage.x(flags, s.x) - s.x;
            double dy = Afterimage.y(flags, s.y) - s.y;
            double dz = Afterimage.z(flags, s.z) - s.z;
            float theta = (180.0F - s.bodyRot) * DEG;
            double c = Mth.cos(theta), sn = Mth.sin(theta);
            double xr = c * dx - sn * dz;
            double zr = sn * dx + c * dz;
            ghostRoot.x = (float) (-PX * xr / scale);
            ghostRoot.y = (float) (-PX * dy / scale);
            ghostRoot.z = (float) (PX * zr / scale);
            ghostRoot.yRot = (float) ((Afterimage.yaw(flags) - s.bodyRot) * DEG);
        } else {
            ghostRoot.x = -POSE_RIGHT * PX;
        }
        ghostRoot.yScale = height;
        ghostRoot.xScale = ghostRoot.zScale = 1.0F + 0.25F * (1.0F - height);   // spreading a little as it slumps
        ghostRoot.y += 24.0F * (1.0F - height);                                  // feet kept on the ground
    }
}
