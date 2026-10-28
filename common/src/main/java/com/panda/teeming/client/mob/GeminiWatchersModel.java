package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.GeminiWatchers;
import com.panda.teeming.mob.end.GeminiWatchers.Beam;
import com.panda.teeming.mob.end.GeminiWatchers.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Gemini Watchers: a tall crystal sentinel hovering on the point of its spindle, bobbing gently, leaning into its glide,
 * its free arm swinging and its staff held upright. The twin of a pair is drawn as the mirror image: its eye, its crest
 * (or crescent) and its staff arm move across to the other side.
 *
 * <p>Tell: the staff goes up high and back, the body leans back, the eye narrows. Strike: the staff comes down hard in
 * two ticks as it leans in. Channel: both arms reach toward the husk, the eye widens, and a beam of light runs from the
 * eye to the husk (from the synced flags, turned from the world into model space as the renderer turns the model into
 * the world); a channel with nothing in the flags (the harness poses each phase on a still mob) aims its beam at the
 * ground 2.5 blocks ahead. Husk: it lies on its back, its staff fallen beside it, cracks over its face and torso (they
 * glow by stage). Rise: it swings back up from lying to standing.
 */
public class GeminiWatchersModel extends TeemingModel {
    private static final float PX = 16.0F;
    private static final float HALF_PI = Mth.HALF_PI;
    /** Where a lying husk's waist pivot sits: low enough that its back rests on the ground, shifted to centre the body. */
    private static final float LIE_Y = 21.0F;
    private static final float LIE_Z = -6.0F;
    /** The middle of the eye's front at rest, in model pixels (x for the first of a pair; its twin's is mirrored). */
    private static final float EYE_X = 1.5F, EYE_Y = -3.5F, EYE_Z = -5.0F;
    /** The beam's box: 16 px long, its two thin sides drawn this much of their 2 px. */
    private static final float BEAM_LENGTH = 16.0F, BEAM_THICK = 1.0F;

    /**
     * The staff blow's angles in radians about the shoulder's axis (a hanging arm swings forward as they go negative, an upright
     * staff leans back): the arm raised, the staff held overhead leaning back, then the arm down in front and the staff
     * chopping to point a little below ahead.
     */
    private static final float RAISE_ARM = -2.6F, RAISE_FORE = -0.4F, RAISED = -0.35F, CHOP_ARM = -1.1F, CHOP = 1.65F;

    private final ModelPart body, head, eye, crest, moon, armFree, foreFree, armStaff, foreStaff, staff, cracks, headCracks, beam;

    public GeminiWatchersModel(ModelPart root) {
        super(root, CreatureLayers.GEMINI_WATCHERS_ONLY);
        body = part("body");
        head = part("head");
        eye = part("eye");
        crest = part("crest");
        moon = part("moon");
        armFree = part("arm_free");
        foreFree = part("forearm_free");
        armStaff = part("arm_staff");
        foreStaff = part("forearm_staff");
        staff = part("staff");
        cracks = part("cracks");
        headCracks = part("head_cracks");
        beam = part("beam");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean mirror = s.flag(GeminiWatchers.FLAG_MIRROR);
        float side = mirror ? -1.0F : 1.0F;                       // +1: eye on its left, staff on its right
        if (mirror) mirror();
        int phase = s.phase;
        boolean lying = s.flag(GeminiWatchers.FLAG_HUSK) || phase == Step.HUSK.ordinal() || s.stage >= GeminiWatchers.STAGE_DARK;
        boolean rising = phase == Step.RISE.ordinal() && !lying;
        cracks.visible = lying || rising;
        headCracks.visible = cracks.visible;
        beam.visible = false;

        if (lying) {
            lie(s, t, side, 1.0F);
            return;
        }
        if (rising) {
            lie(s, t, side, 1.0F - smooth(s.progress));
            return;
        }

        float swing = Mth.cos(pos * 0.6F);
        body.y += Mth.sin(t * 0.08F) * 0.5F;                      // it hovers on its point, never still
        body.zRot += Mth.sin(t * 0.05F) * 0.02F;
        body.xRot += 0.1F * speed;                                // leaning into its glide
        armFree.xRot += swing * 0.4F * speed + Mth.sin(t * 0.06F) * 0.04F;
        armFree.zRot += side * 0.08F;
        armStaff.xRot -= swing * 0.15F * speed;                   // the staff arm keeps its staff upright
        armStaff.zRot -= side * 0.05F;
        head.yRot += s.yRot * DEG * 0.6F;
        head.xRot += s.xRot * DEG * 0.5F;

        float tm = s.phaseTime;
        if (phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.0F);                   // up in the first half, held
            armStaff.xRot += RAISE_ARM * k;                        // the arm up over its head,
            foreStaff.xRot += RAISE_FORE * k;
            staff.xRot += (RAISED - RAISE_ARM - RAISE_FORE) * k;   // the staff held head up, leaning back behind it
            armStaff.zRot += side * 0.2F * k;
            body.xRot -= 0.12F * k;
            head.xRot -= 0.1F * k;
            eye.yScale = 1.0F - 0.35F * k;                         // the eye narrows on its foe
        } else if (phase == Step.STRIKE.ordinal()) {
            float down = smooth(tm / 2.0F);                        // down in two ticks
            float back = 1.0F - smooth((s.progress - 0.5F) * 2.0F);   // back to rest over the second half
            float arm = Mth.lerp(down, RAISE_ARM, CHOP_ARM), fore = Mth.lerp(down, RAISE_FORE, 0.0F);
            armStaff.xRot += arm * back;                           // the arm swings down in front,
            foreStaff.xRot += fore * back;
            staff.xRot += (Mth.lerp(down, RAISED, CHOP) - arm - fore) * back;   // the staff chopping down to point ahead
            body.xRot += 0.3F * down * back;
            head.xRot += 0.15F * down * back;
        } else if (phase == Step.CHANNEL.ordinal()) {
            float k = smooth(Math.min(1.0F, tm / 6.0F));          // the free arm reaches out over six ticks and stays
            float pulse = Mth.sin(t * 0.6F) * 0.04F;
            armFree.xRot -= (1.4F + pulse) * k;                    // its open hand toward the husk,
            armFree.zRot -= side * 0.2F * k;
            foreFree.xRot -= 0.25F * k;
            armStaff.xRot -= 0.25F * k;                            // its staff planted upright beside it
            staff.xRot += 0.25F * k;
            eye.xScale = eye.yScale = 1.0F + (0.12F + Mth.sin(t * 0.5F) * 0.05F) * k;
            beam(s, side);
        }
    }

    /** The twin: its eye, crest, crescent and arms on the other side (their boxes are symmetric about their pivots). */
    private void mirror() {
        eye.x = -eye.x;
        crest.x = -crest.x;
        crest.zRot = -crest.zRot;
        moon.x = -moon.x;
        moon.yRot += Mth.PI;                                      // the crescent turned round, opening outward the other way
        armFree.x = -armFree.x;
        armStaff.x = -armStaff.x;
    }

    /**
     * Lying on its back by {@code k} (1 flat, 0 standing): the waist pivot swung down and back so its back rests on the
     * ground and the body is centred where it lies, the staff fallen out to its side, the arms at its sides.
     */
    private void lie(TeemingRenderState s, float t, float side, float k) {
        body.xRot = -HALF_PI * k;
        body.y = Mth.lerp(k, body.y, LIE_Y);
        body.z = Mth.lerp(k, body.z, LIE_Z);
        armStaff.zRot -= side * 0.35F * k;                         // the staff lies out beside it
        armFree.zRot += side * 0.12F * k;
        head.xRot += 0.08F * k;                                    // the head tipped back on the ground
        if (s.stage >= GeminiWatchers.STAGE_BRIGHTEST && k > 0.99F) {
            body.y -= 0.3F + Mth.sin(t * 1.3F) * 0.3F;             // as the beam ends it shivers, about to rise
        }
    }

    /**
     * The revive beam from the middle of its eye to the husk: the way to the husk in the flags turned into model space
     * (a turn of 180 less the body's yaw, X and Y flipped, divided by its scale), then the beam's box turned to point
     * along it and stretched to its length.
     */
    private void beam(TeemingRenderState s, float side) {
        float ex = EYE_X * side, ey = EYE_Y, ez = EYE_Z;
        float tx, ty, tz;
        if (s.flag(GeminiWatchers.FLAG_BEAM)) {
            float scale = s.scale > 0.0F ? s.scale : 1.0F;
            double dx = Beam.x(s.flags), dy = Beam.y(s.flags) + Beam.AIM_UP, dz = Beam.z(s.flags);
            float theta = (180.0F - s.bodyRot) * DEG;
            double c = Mth.cos(theta), sn = Mth.sin(theta);
            double xr = c * dx - sn * dz;
            double zr = sn * dx + c * dz;
            tx = (float) (-PX * xr / scale);
            ty = (float) (24.0 - PX * dy / scale);
            tz = (float) (PX * zr / scale);
        } else {
            tx = ex;                                               // posed: at the ground 2.5 blocks ahead
            ty = 20.0F;
            tz = -40.0F;
        }
        float dx = tx - ex, dy = ty - ey, dz = tz - ez;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0F) return;
        beam.visible = true;
        beam.x = ex;
        beam.y = ey;
        beam.z = ez;
        beam.xRot = (float) Math.asin(Mth.clamp(dy / length, -1.0F, 1.0F));
        beam.yRot = (float) Mth.atan2(-dx, -dz);
        beam.zRot = 0.0F;
        beam.zScale = length / BEAM_LENGTH;
        beam.xScale = BEAM_THICK;
        beam.yScale = BEAM_THICK;
    }
}
