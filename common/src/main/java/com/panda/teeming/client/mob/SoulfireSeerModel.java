package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.SoulfireSeer;
import com.panda.teeming.mob.nether.SoulfireSeer.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Soulfire Seer: a slow, stiff walk, the ragged robe swaying and the staff of vertebrae planted with each step; the
 * soul flame over its eye flickers. Tell: it turns its skull on the target and points with its right hand while a thin
 * soul-blue line runs from its eye to the target, flickering as it follows; once the aim locks (the last 8 ticks) the
 * line steadies and brightens, the jaw drops, it leans in and the flame flares. Fire: the beam, thick and white-hot
 * along the locked line, fading as the seer is thrown back by the recoil. Snuffed: the eye and its flame are out,
 * leaving the dark socket; head bowed, it sweeps its skull from side to side and gropes ahead with both hands.
 * The line is laid from the eye along the body's facing, from the synced length and pitch.
 */
public class SoulfireSeerModel extends TeemingModel {
    private static final float PX = 16.0F;             // pixels a block
    /** Where the line leaves the skull: the socket, just in front of the face, in the head's own pixels. */
    private static final float EYE_X = 0.0F, EYE_Y = -5.0F, EYE_Z = -4.0F;
    /**
     * The line in a pose with no aim behind it (the harness poses each phase on a still mob): its length in blocks
     * and its pitch, a touch down, so it shows in a still frame rather than end on toward the camera.
     */
    private static final float POSE_LENGTH = 3.5F, POSE_PITCH = -8.0F * DEG;
    /** Ticks the line follows before it locks. */
    private static final float TRACK = SoulfireSeer.TRACK_TICKS;
    /** Ticks the beam shows. */
    private static final float BEAM = SoulfireSeer.BEAM_TICKS;
    /** The eye sits flat in its socket: its 1-pixel box squashed to this depth. */
    private static final float EYE_DEPTH = 0.35F;

    private final ModelPart body, skirt, head, jaw, eye, flame, armRight, armLeft, handRight, staff, footRight, footLeft, line;

    public SoulfireSeerModel(ModelPart root) {
        super(root, CreatureLayers.SOULFIRE_SEER_ONLY);
        body = part("body");
        skirt = part("skirt");
        head = part("head");
        jaw = part("jaw");
        eye = part("eye");
        flame = part("flame");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        staff = part("staff");
        footRight = part("foot_right");
        footLeft = part("foot_left");
        line = part("line");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        line.visible = false;
        eye.visible = true;
        flame.visible = true;
        eye.zScale = EYE_DEPTH;

        // the walk: a stiff, gliding step under the robe, the robe swaying and the staff planted
        float w = pos * 0.6F;
        footRight.z += Mth.cos(w) * 1.6F * speed;
        footLeft.z -= Mth.cos(w) * 1.6F * speed;
        footRight.y -= Math.max(0.0F, Mth.sin(w)) * 0.8F * speed;
        footLeft.y -= Math.max(0.0F, -Mth.sin(w)) * 0.8F * speed;
        skirt.xRot += Mth.sin(w) * 0.05F * speed + Mth.sin(t * 0.07F) * 0.02F;
        skirt.zRot += Mth.cos(w) * 0.04F * speed;
        body.zRot += Mth.cos(w) * 0.03F * speed;
        body.xRot += 0.06F + 0.05F * speed;                                    // a stoop
        float sway = Mth.sin(t * 0.06F) * 0.05F;
        armRight.xRot += sway + Mth.cos(w) * 0.35F * speed;
        armLeft.xRot += -0.35F + Mth.sin(t * 0.05F) * 0.03F;                   // the staff hand held forward
        staff.xRot = -armLeft.xRot - body.xRot;                                // the staff upright whatever the arm does
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        jaw.xRot = 0.05F + Math.max(0.0F, Mth.sin(t * 0.04F)) * 0.06F;
        float flicker = 1.0F + 0.12F * Mth.sin(t * 0.9F) + 0.06F * Mth.sin(t * 2.3F);
        flame.xScale = 1.0F - 0.06F * Mth.sin(t * 1.3F);
        flame.yScale = flicker;

        boolean live = SoulfireSeer.lineLive(s.flags);
        float pitch = live ? SoulfireSeer.linePitch(s.flags) * DEG : POSE_PITCH;
        float length = live ? SoulfireSeer.lineLength(s.flags) : POSE_LENGTH;
        float tm = s.phaseTime;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(tm / 5.0F);
            boolean lockedNow = live ? SoulfireSeer.lineLocked(s.flags) : tm >= TRACK;
            float lk = lockedNow ? smooth((tm - TRACK) / 3.0F) : 0.0F;
            body.xRot += 0.12F * lk;                                            // it leans into the shot once locked
            point(k, pitch);
            head.yRot = 0.0F;
            head.xRot = -pitch - body.xRot;                                     // the skull looks along the line
            jaw.xRot = 0.05F + 0.45F * lk + Mth.sin(tm * 2.0F) * 0.03F * k;
            flame.xScale = 1.0F + 0.3F * k + 0.3F * lk;
            flame.yScale = (1.0F + 0.2F * k + 0.2F * lk) * (1.0F + 0.12F * Mth.sin(tm * (lockedNow ? 0.6F : 2.6F)));
            // tracking: thin and flickering; locked: steady and a little thicker
            float thick = lockedNow ? 0.8F : 0.5F + 0.15F * Mth.sin(tm * 3.1F);
            lay(pitch, length * PX, thick);
        } else if (s.phase == Step.FIRE.ordinal()) {
            // timed in ticks, so a pose held longer than the real beam shows its first moments, not its fade
            float u = live ? Mth.clamp(tm / BEAM, 0.0F, 1.0F) : 0.2F;
            float r = 1.0F - smooth(u * 2.0F);                                // the recoil is over in half the beam
            body.xRot -= 0.3F * r;                                              // thrown back by the recoil
            point(r, pitch);
            armRight.xRot -= 0.5F * (1.0F - r);
            head.yRot = 0.0F;
            head.xRot = -pitch - body.xRot - 0.45F * r;
            jaw.xRot = 0.6F * r + 0.05F;
            flame.xScale = 1.0F + 0.6F * r;
            flame.yScale = 1.0F + 0.5F * r;
            // full width for the first quarter, then it thins to a fading streak along the line of fire
            float fade = Mth.clamp((u - 0.25F) / 0.75F, 0.0F, 1.0F);
            float thick = 3.2F * (1.0F - fade) * (1.0F - fade) + 0.35F;
            lay(pitch, length * PX, thick);
        } else if (s.phase == Step.SNUFFED.ordinal()) {
            eye.visible = false;
            flame.visible = false;
            float k = smooth(tm / 6.0F);
            head.yRot = Mth.sin(tm * 0.16F) * 0.7F * k;                          // blind: it sweeps its skull about
            head.xRot = 0.35F * k + Mth.sin(tm * 0.11F) * 0.08F;
            jaw.xRot = 0.25F * k;
            body.xRot += 0.1F * k;
            float grope = Mth.sin(tm * 0.2F);
            armRight.xRot = Mth.lerp(k, armRight.xRot, -1.2F + grope * 0.25F);
            armRight.zRot += 0.15F * k;
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -0.9F - grope * 0.2F);
            staff.xRot = -armLeft.xRot - body.xRot - 0.25F * k;                 // the staff tips as it feels about
        }
    }

    /** Its right arm raised to point along the line, {@code k} 0..1, at {@code pitch} (up positive). */
    private void point(float k, float pitch) {
        armRight.xRot = Mth.lerp(k, armRight.xRot, -Mth.HALF_PI - pitch - body.xRot);
        armRight.zRot = Mth.lerp(k, armRight.zRot, 0.12F);
        handRight.xRot = -0.2F * k;
    }

    /** The middle of the socket, in model pixels, from the head and body as posed. */
    private Vector3f eyePoint() {
        Vector3f p = new Vector3f(EYE_X, EYE_Y, EYE_Z);
        toParent(head, p);
        toParent(body, p);
        return p;
    }

    /** Moves {@code p} from {@code part}'s own space into its parent's, as the renderer does. */
    private static void toParent(ModelPart part, Vector3f p) {
        p.mul(part.xScale, part.yScale, part.zScale);
        new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(p);
        p.add(part.x, part.y, part.z);
    }

    /**
     * Lays the line from the eye along a line pitched {@code pitch} (up positive) straight ahead, {@code len} pixels
     * long and {@code thick} pixels across: one glowing pixel box, stretched.
     */
    private void lay(float pitch, float len, float thick) {
        if (len <= 0.5F) return;
        Vector3f m = eyePoint();
        Vector3f dir = new Vector3f(0.0F, -Mth.sin(pitch), -Mth.cos(pitch));
        line.visible = true;
        line.x = m.x;
        line.y = m.y;
        line.z = m.z;
        line.xRot = (float) Math.asin(Mth.clamp(dir.y, -1.0F, 1.0F));
        line.yRot = (float) Mth.atan2(-dir.x, -dir.z);
        line.zRot = 0.0F;
        line.xScale = thick;
        line.yScale = thick;
        line.zScale = len;
    }
}
