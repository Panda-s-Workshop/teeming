package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Recurrent;
import com.panda.teeming.mob.end.Recurrent.Step;
import com.panda.teeming.mob.end.Recurrent.Trail;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Recurrent, drawn see-through where its afterimages are: a tall robed figure that hovers a pixel off the ground, its
 * robe swaying, its short arms swinging a little as it glides. Its clock hands tick round while its face glows and stand
 * still while it is dark. Tell: the right arm goes up high while the hands whirl forward. Strike: the arm comes down hard
 * as it leans in. Rewound: it shudders and the hands whirl backward.
 *
 * <p>Two afterimages follow it ({@code ghost1}, {@code ghost2}): copies of the figure a beat behind in its own motion,
 * trailing behind it, farther as it moves faster. While it stands dazed after a rewind they lie along the way back to
 * where it was killed (from the synced flags, turned from the world into model space as the renderer turns the model
 * into the world); a daze with nothing in the flags (the harness poses each phase on a still mob) shows them behind
 * it to its right.
 *
 * <p>On an entity flagged as its anchor only the hourglass cage shows, bobbing, its glass turning slowly.
 */
public class RecurrentModel extends TeemingModel {
    private static final float PX = 16.0F;
    /** The afterimages in a daze with no trail: each this many blocks farther to the right, and back. */
    private static final float POSE_RIGHT = 0.5F;
    private static final float POSE_BACK = 1.0F;
    /** Behind it at rest, and how much farther at full walking speed, in pixels, for the first afterimage (the second twice). */
    private static final float TRAIL_REST = 3.0F;
    private static final float TRAIL_WALK = 5.0F;

    /** One figure's parts: the Recurrent's, or an afterimage's ({@code ghostN_} names). */
    private final class Figure {
        final ModelPart body, hem, head, armLeft, armRight;

        Figure(String p) {
            body = part(p + "body");
            hem = part(p + "hem");
            head = part(p + "head");
            armLeft = part(p + "arm_left");
            armRight = part(p + "arm_right");
        }
    }

    private final Figure me, first, second;
    private final ModelPart ghost1, ghost2, hour, minute, crack, anchor, anchorGlass, anchorStream;

    public RecurrentModel(ModelPart root) {
        super(root, CreatureLayers.RECURRENT_ONLY, RenderTypes::entityTranslucent);
        me = new Figure("");
        first = new Figure("ghost1_");
        second = new Figure("ghost2_");
        ghost1 = part("ghost1");
        ghost2 = part("ghost2");
        hour = part("hour");
        minute = part("minute");
        crack = part("crack");
        anchor = part("anchor");
        anchorGlass = part("anchor_glass");
        anchorStream = part("anchor_stream");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean isAnchor = Recurrent.anchorIn(s.flags);
        anchor.visible = isAnchor;
        me.body.visible = !isAnchor;
        ghost1.visible = !isAnchor && s.deathTime <= 0.0F;
        ghost2.visible = ghost1.visible;
        if (isAnchor) {
            anchor.y += Mth.sin(t * 0.1F) * 0.8F;
            anchorGlass.yRot = t * 0.05F;
            anchorStream.yRot = anchorGlass.yRot;
            return;
        }
        crack.visible = s.flag(Recurrent.FLAG_BROKEN);
        pose(me, s, t, pos, speed);
        pose(first, s, t - 3.0F, pos - 1.2F * speed, speed);
        pose(second, s, t - 6.0F, pos - 2.4F * speed, speed);
        me.head.yRot += s.yRot * DEG * 0.7F;
        me.head.xRot += s.xRot * DEG * 0.5F;
        hands(s, t);
        if (s.phase == Step.REWOUND.ordinal()) {
            trail(s);
        } else {
            behind(ghost1, t, speed, 1);
            behind(ghost2, t, speed, 2);
        }
    }

    /** One figure's pose at time {@code t}: a hover and a sway, the glide's swing, then the step's own pose. */
    private static void pose(Figure f, TeemingRenderState s, float t, float pos, float speed) {
        float swing = Mth.cos(pos * 0.6F);
        f.body.y += Mth.sin(t * 0.08F) * 0.4F;                                   // it hovers, never quite still
        f.body.zRot += Mth.sin(t * 0.05F) * 0.025F;
        f.body.xRot += 0.08F * speed;                                            // leaning into its glide
        f.hem.xRot += -0.18F * speed + swing * 0.1F * speed + Mth.sin(t * 0.07F) * 0.03F;   // the hem trails and stirs
        f.armRight.xRot += swing * 0.35F * speed + Mth.sin(t * 0.06F) * 0.04F;
        f.armLeft.xRot -= swing * 0.35F * speed - Mth.sin(t * 0.06F + 1.0F) * 0.04F;
        f.armRight.zRot += 0.06F;
        f.armLeft.zRot -= 0.06F;
        float tm = s.phaseTime;
        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.0F);                                 // up in the first half, held
            f.armRight.xRot -= 2.7F * k;
            f.armRight.zRot -= 0.25F * k;
            f.body.xRot -= 0.12F * k;
            f.head.xRot -= 0.15F * k;
        } else if (s.phase == Step.STRIKE.ordinal()) {
            float down = smooth(tm / 2.0F);                                      // down in two ticks
            float back = smooth((s.progress - 0.5F) * 2.0F);                     // then back over the second half
            float k = down * (1.0F - back);
            f.armRight.xRot -= 2.7F * (1.0F - down) + 0.5F * k;
            f.body.xRot += 0.3F * k;
            f.head.xRot += 0.2F * k;
        } else if (s.phase == Step.REWOUND.ordinal()) {
            float k = 1.0F - s.progress;
            f.body.x += Mth.sin(tm * 3.1F) * 0.5F * k;                           // a shudder, dying away
            f.body.zRot += Mth.sin(tm * 2.3F) * 0.06F * k;
            f.armLeft.xRot -= 0.5F * k;
            f.armRight.xRot -= 0.5F * k;
            f.armLeft.zRot -= 0.4F * k;
            f.armRight.zRot += 0.4F * k;
        }
    }

    /** The clock hands: ticking round while the face glows, whirling forward in the wind-up and back in a daze. */
    private void hands(TeemingRenderState s, float t) {
        boolean lit = s.stage == Recurrent.STAGE_LIT;
        float tm = s.phaseTime;
        float h, m;
        if (s.phase == Step.TELL.ordinal()) {
            h = -0.3F * tm;
            m = -1.1F * tm;
        } else if (s.phase == Step.REWOUND.ordinal()) {
            h = 0.5F * tm;
            m = 1.6F * tm;
        } else if (lit) {
            h = -0.01F * t;
            m = -0.12F * t;
        } else {
            h = -1.2F;                                                           // stopped
            m = 2.6F;
        }
        hour.zRot = h;
        minute.zRot = m;
    }

    /** An afterimage at rest or gliding: behind it ({@code n} beats back), drifting a little from side to side. */
    private static void behind(ModelPart ghost, float t, float speed, int n) {
        ghost.z += n * (TRAIL_REST + TRAIL_WALK * speed);
        ghost.x += Mth.sin(t * 0.06F + n * 1.7F) * (0.8F + 0.6F * n);
        ghost.y += Mth.sin(t * 0.09F + n) * 0.3F;
    }

    /**
     * The afterimages along the way back to where it was killed, a third and two thirds of the way. The world offset is
     * turned into model space the way the renderer turns the model into the world (a turn of 180 less the body's yaw,
     * then X and Y flipped), and divided by its scale.
     */
    private void trail(TeemingRenderState s) {
        float scale = s.scale > 0.0F ? s.scale : 1.0F;
        if (!s.flag(Recurrent.FLAG_TRAIL)) {
            ghost1.x -= POSE_RIGHT * PX;
            ghost1.z += POSE_BACK * PX;
            ghost2.x -= 2.0F * POSE_RIGHT * PX;
            ghost2.z += 2.0F * POSE_BACK * PX;
            return;
        }
        double dx = Trail.x(s.flags), dy = Trail.y(s.flags), dz = Trail.z(s.flags);
        float theta = (180.0F - s.bodyRot) * DEG;
        double c = Mth.cos(theta), sn = Mth.sin(theta);
        double xr = c * dx - sn * dz;
        double zr = sn * dx + c * dz;
        float fade = 1.0F - 0.3F * s.progress;                            // drawing in toward it as the daze ends
        place(ghost1, xr, dy, zr, scale, fade / 3.0F);
        place(ghost2, xr, dy, zr, scale, fade * 2.0F / 3.0F);
    }

    private static void place(ModelPart ghost, double xr, double dy, double zr, float scale, float along) {
        ghost.x = (float) (-PX * xr * along / scale);
        ghost.y = (float) (-PX * dy * along / scale);
        ghost.z = (float) (PX * zr * along / scale);
    }
}
