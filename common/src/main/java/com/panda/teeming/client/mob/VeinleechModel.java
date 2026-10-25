package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Veinleech;
import com.panda.teeming.mob.nether.Veinleech.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Veinleech: a crawl that ripples down the ringed body, the sac breathing. Tell: the front half rears up off the
 * ground, the sucker flares and the ring of teeth splays open, quivering. Latch: still reared, the sucker turned to
 * the victim and the vein laid from its mouth to them: it shoots out in a few ticks, sags a little, writhes, and a
 * swelling runs down it from the victim to the leech once a second, when the sac and sucker gulp. Stunned: it slumps
 * flat, the teeth limp, the head swaying dazed and the tail twitching; the snapped vein whips back into its mouth and
 * hangs from it as a limp stub. The vein is laid out from the synced length and pitch, along the body's facing, which
 * the leech keeps on its victim while it holds.
 */
public class VeinleechModel extends TeemingModel {
    private static final int VEINS = 44;
    private static final float VEIN_PITCH = 4.0F;      // pixels between segment centres, as in the rig
    private static final float VEIN_HALF = 2.5F;       // half a segment's length
    private static final float PX = 16.0F;             // pixels a block
    private static final int TEETH = 6;
    private static final float TOOTH_REST = -25.0F * DEG, TOOTH_OPEN = 70.0F * DEG, TOOTH_GRIP = 40.0F * DEG;
    /**
     * The vein in a pose with no hold behind it (the harness poses each phase on a still mob): its length in blocks,
     * raised at this pitch like a rearing snake, so it shows in a still frame rather than end on toward the camera.
     */
    private static final float POSE_LENGTH = 1.25F, POSE_PITCH = 55.0F * DEG;
    /** Ticks between drinks: a swelling runs the vein's length in this time. */
    private static final float DRINK = Veinleech.DRAIN_INTERVAL;

    private final ModelPart body, sac, neck, head, rear, tail;
    private final ModelPart[] teeth = new ModelPart[TEETH];
    private final float[] toothRoll = new float[TEETH];
    private final ModelPart[] veins = new ModelPart[VEINS];

    public VeinleechModel(ModelPart root) {
        super(root, CreatureLayers.VEINLEECH_ONLY);
        body = part("body");
        sac = part("sac");
        neck = part("neck");
        head = part("head");
        rear = part("rear");
        tail = part("tail");
        for (int i = 0; i < TEETH; i++) {
            teeth[i] = part("tooth_" + i);
            toothRoll[i] = teeth[i].zRot;
        }
        for (int i = 0; i < VEINS; i++) veins[i] = part("vein_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        for (ModelPart v : veins) v.visible = false;
        // the crawl: a ripple runs from the head to the tail, each segment lifting and stretching in turn
        float w = pos * 0.8F;
        neck.xRot += Mth.sin(w) * 0.12F * speed;
        rear.xRot += Mth.sin(w + 2.0F) * 0.1F * speed;
        tail.xRot += Mth.sin(w + 3.0F) * 0.14F * speed;
        neck.zScale = 1.0F + Mth.sin(w) * 0.1F * speed;
        rear.zScale = 1.0F + Mth.sin(w + 2.0F) * 0.1F * speed;
        float breath = 1.0F + Mth.sin(t * 0.12F) * 0.04F;
        scale(sac, breath);
        neck.yRot = Mth.clamp(s.yRot * DEG * 0.5F, -0.5F, 0.5F);
        head.xRot = Mth.clamp(s.xRot * DEG * 0.5F, -0.4F, 0.4F);
        float teethAngle = TOOTH_REST + Mth.sin(t * 0.2F) * 0.05F;
        if (s.attack > 0.0F) {                                           // the bite: a lunge with the teeth flung open
            float a = Mth.sin(s.attack * Mth.PI);
            neck.xRot -= 0.3F * a;
            head.z -= 1.5F * a;
            teethAngle += 1.2F * a;
        }

        boolean live = Veinleech.veinLive(s.flags);
        float pitch = live ? Veinleech.veinPitch(s.flags) * DEG : POSE_PITCH;
        float length = live ? Veinleech.veinLength(s.flags) : POSE_LENGTH;
        float tm = s.phaseTime;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);                          // reared within the first 40%, then held
            rearUp(k);
            float quiver = Mth.sin(tm * 2.2F) * 0.04F * k;
            neck.yRot = quiver;
            float flare = 1.0F + 0.3F * k;
            head.xScale = flare;
            head.yScale = flare;
            teethAngle = Mth.lerp(k, TOOTH_REST, TOOTH_OPEN) + quiver * 3.0F;
            scale(sac, breath + 0.12F * k + Mth.sin(tm * 1.5F) * 0.05F * k);
        } else if (s.phase == Step.LATCH.ordinal()) {
            rearUp(0.75F);
            neck.yRot = 0.0F;
            // the sucker turned square to the victim along the vein
            head.xRot = Mth.clamp(-pitch - body.xRot - neck.xRot, -1.3F, 1.0F);
            float since = tm % DRINK;                                    // ticks since the last gulp
            float gulp = (float) Math.exp(-since / 3.0F);
            float flare = 1.2F + 0.15F * gulp;
            head.xScale = flare;
            head.yScale = flare;
            head.zScale = 1.0F - 0.15F * gulp;
            body.xScale = 1.0F + 0.06F * gulp;
            scale(sac, breath + 0.1F + 0.2F * gulp);
            teethAngle = TOOTH_GRIP + gulp * 0.2F;
            float shown = Math.min(length, Veinleech.SHOOT_SPEED * tm);
            lay(pitch, length * PX, shown * PX, length * PX * 0.03F, tm, true);
        } else if (s.phase == Step.STUNNED.ordinal()) {
            body.yScale = 0.85F;
            body.xScale = 1.08F;
            body.y += 0.6F;
            neck.xRot = 0.18F;
            head.xRot = 0.3F;
            rear.xRot = -0.05F;
            neck.yRot = Mth.sin(tm * 0.25F) * 0.3F;                       // dazed
            float twitch = Mth.sin(tm * 0.9F);
            tail.xRot = 0.2F * twitch * twitch * twitch * twitch;
            teethAngle = -10.0F * DEG;
            scale(sac, 0.92F);
            float whip = smooth(tm / 5.0F);
            if (live && whip < 1.0F) {
                // the snapped vein whips back into the mouth, falling as it goes
                float len = length * PX;
                lay(pitch * (1.0F - whip), len, len * (1.0F - whip), len * (0.05F + 0.25F * whip), tm, false);
            } else {
                lay(-15.0F * DEG, 16.0F, 16.0F, 2.0F, tm, false);   // a limp stub lying from the mouth
            }
        }
        for (int i = 0; i < TEETH; i++) {
            teeth[i].xRot = teethAngle;
            teeth[i].zRot = toothRoll[i];
        }
    }

    /** The front half lifted off the ground, {@code k} 0..1; the rear stays down. */
    private void rearUp(float k) {
        body.xRot = -0.18F * k;
        neck.xRot = -0.55F * k;
        head.xRot = -0.25F * k;
        rear.xRot = 0.18F * k;
        tail.xRot = 0.05F * k;
    }

    private static void scale(ModelPart p, float k) {
        p.xScale = k;
        p.yScale = k;
        p.zScale = k;
    }

    /** The middle of the sucker's face, in model pixels, from the body, neck and head as posed. */
    private Vector3f mouth() {
        Vector3f p = new Vector3f(0.0F, 0.0F, -3.0F);
        toParent(head, p);
        toParent(neck, p);
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
     * Lays the vein from the mouth along a line pitched {@code pitch} (up positive) straight ahead: {@code len} pixels
     * to its end, of which {@code shown} are out; {@code sag} pixels of droop at its middle. A held vein writhes a little
     * from side to side and swells where the drink runs down it (from the victim's end to the mouth once a second).
     */
    private void lay(float pitch, float len, float shown, float sag, float time, boolean held) {
        Vector3f m = mouth();
        Vector3f d = new Vector3f(0.0F, -Mth.sin(pitch), -Mth.cos(pitch));
        Vector3f side = new Vector3f(1.0F, 0.0F, 0.0F);
        float drink = 1.0F - (time % DRINK) / DRINK;                     // where the swelling is, 1 at the victim
        for (int i = 0; i < VEINS; i++) {
            ModelPart p = veins[i];
            float along = i * VEIN_PITCH + VEIN_HALF;
            if (along - VEIN_HALF >= shown || len <= 0.0F) continue;
            along = Math.min(along, Math.max(VEIN_HALF, shown - VEIN_HALF));
            float u = along / len;
            float arch = 4.0F * u * (1.0F - u);
            float drop = sag * arch;
            float dDrop = sag * 4.0F * (1.0F - 2.0F * u) / len;
            float wobble = 0.0F, dWobble = 0.0F;
            if (held) {
                float phase = along * 0.25F + time * 0.6F;
                float env = Mth.sin(Mth.PI * u);
                wobble = Mth.sin(phase) * 0.7F * env;
                dWobble = Mth.cos(phase) * 0.25F * 0.7F * env;
            }
            Vector3f c = new Vector3f(d).mul(along).add(m).add(0.0F, drop, 0.0F).add(new Vector3f(side).mul(wobble));
            Vector3f dir = new Vector3f(d).add(0.0F, dDrop, 0.0F).add(new Vector3f(side).mul(dWobble)).normalize();
            orient(p, c, dir);
            float swell = 1.0F;
            if (held) {
                float gap = (u - drink) * len / 6.0F;
                swell += 0.9F * (float) Math.exp(-gap * gap) + Mth.sin(time * 0.8F + along * 0.1F) * 0.1F;
            }
            p.xScale = swell;
            p.yScale = swell;
            p.zScale = 1.0F;
        }
    }

    /** Centres {@code p} on {@code c}, its long axis (-Z in the rig) along the unit vector {@code dir}. */
    private static void orient(ModelPart p, Vector3f c, Vector3f dir) {
        p.visible = true;
        p.x = c.x;
        p.y = c.y;
        p.z = c.z;
        p.xRot = (float) Math.asin(Mth.clamp(dir.y, -1.0F, 1.0F));
        p.yRot = (float) Mth.atan2(-dir.x, -dir.z);
        p.zRot = 0.0F;
    }
}
