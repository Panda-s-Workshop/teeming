package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.FortressJailer;
import com.panda.teeming.mob.nether.FortressJailer.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Fortress Jailer: a heavy, upright march with a short length of chain and the manacle swinging from its right fist.
 * Tell: the right fist goes up and the chain whirls in a ring over its head. Throw: the arm drives out along the throw
 * and the links pay out to the head. Pull: it leans back, both hands on the taut chain. Reel: the slack chain sags
 * and shortens. Strike: the chained fist goes up over its head and comes down. The thrown chain is laid out from the synced length and pitch
 * (extrapolated between updates at the chain's own speeds), and along the body's facing, which the jailer keeps on
 * the line while the chain is out.
 */
public class FortressJailerModel extends TeemingModel {
    private static final int LINKS = 44;
    private static final float LINK_PITCH = 4.5F;     // pixels between link centres, as in the rig
    private static final float LINK_HALF = 3.0F;      // half a link's length
    private static final float HAND = 12.0F;          // shoulder to the fist's end, in pixels
    private static final float PX = 16.0F;            // pixels a block
    private static final int WHIRL_LINKS = 9;
    private static final float WHIRL_RADIUS = 9.0F;
    // lengths shown by a pose with no throw behind it (the harness poses each phase on a still mob)
    private static final float POSE_THROW = 4.0F, POSE_PULL = 3.0F, POSE_REEL = 2.0F;

    private final ModelPart body, head, armRight, armLeft, legRight, legLeft, hook;
    private final ModelPart[] links = new ModelPart[LINKS];
    private final Vector3f hand = new Vector3f();

    public FortressJailerModel(ModelPart root) {
        super(root, CreatureLayers.FORTRESS_JAILER_ONLY);
        body = part("body");
        head = part("head");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        hook = part("hook");
        for (int i = 0; i < LINKS; i++) links[i] = part("chain_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float stride = Mth.cos(pos * 0.6F) * 0.8F * speed;
        legRight.xRot = stride;
        legLeft.xRot = -stride;
        armRight.xRot = -stride * 0.5F;
        armLeft.xRot = stride * 0.5F;
        armRight.zRot = 0.06F;
        armLeft.zRot = -0.06F;
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        body.y += Mth.sin(t * 0.05F) * 0.25F;
        if (s.attack > 0.0F) armRight.xRot -= 2.0F * Mth.sin(s.attack * Mth.PI);

        boolean live = FortressJailer.chainLive(s.flags);
        float pitch = live ? FortressJailer.chainPitch(s.flags) * DEG : 0.0F;
        float length = FortressJailer.chainLength(s.flags);
        float tm = s.phaseTime;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 4.0F);                         // up within the first quarter, then held
            armRight.xRot = -2.95F * k + armRight.xRot * (1.0F - k);
            armRight.zRot = -0.15F * k;
            armLeft.xRot = -0.5F * k;
            armLeft.zRot = -0.45F * k;
            body.xRot = -0.06F * k;
            whirl(tm * 0.9F, k);
        } else if (s.phase == Step.THROW.ordinal()) {
            float len = Math.min((float) FortressJailer.CHAIN_SPEED * tm, live ? length : POSE_THROW);
            pointArm(pitch);
            body.xRot = 0.12F;
            armLeft.xRot = 0.6F;
            line(pitch, len * PX, 0.0F);
        } else if (s.phase == Step.PULL.ordinal()) {
            float len = live ? Math.max(1.0F, length - (float) FortressJailer.PULL_SPEED * tm) : POSE_PULL;
            pointArm(pitch);
            body.xRot = -0.16F;
            armRight.z += 1.5F;                                          // drawn back as it hauls
            armLeft.xRot = -Mth.HALF_PI - pitch + 0.15F;
            armLeft.yRot = 0.45F;
            armLeft.zRot = 0.0F;
            line(pitch, len * PX, 0.0F);
        } else if (s.phase == Step.REEL.ordinal()) {
            float len = live ? Math.max(0.0F, length - (float) FortressJailer.REEL_SPEED * tm) : POSE_REEL;
            pointArm(pitch - 0.35F);
            armLeft.xRot = -0.3F;
            line(pitch - 0.35F, len * PX, len * PX * 0.18F);           // slack: it sags
        } else if (s.phase == Step.STRIKE.ordinal()) {
            float up = smooth(s.progress * 2.5F);                        // the chained fist goes up over its head
            float down = smooth((s.progress - 0.62F) / 0.12F);           // and lands on the strike's sixth tick of eight
            armRight.xRot = -2.9F * up + 2.3F * down;
            armRight.zRot = -0.25F * up * (1.0F - down);
            body.xRot = -0.08F * up + 0.3F * down;
            armLeft.xRot = 0.4F * up;
            dangle(t, 0.0F);
        } else {
            dangle(t, speed);
        }
    }

    /** The right arm held straight out along a line pitched {@code pitch} (up positive). */
    private void pointArm(float pitch) {
        armRight.xRot = -Mth.HALF_PI - pitch;
        armRight.yRot = 0.0F;
        armRight.zRot = 0.0F;
    }

    /** The end of the right fist, in model pixels, from the arm as posed. */
    private Vector3f fist() {
        hand.set(0.0F, HAND, 0.0F);
        new Quaternionf().rotationZYX(armRight.zRot, armRight.yRot, armRight.xRot).transform(hand);
        return hand.add(armRight.x, armRight.y, armRight.z);
    }

    /** Two links and the manacle hanging from the fist, swaying as it walks. */
    private void dangle(float t, float speed) {
        Vector3f h = fist();
        float sway = Mth.sin(t * 0.12F) * 0.08F + Mth.cos(t * 0.3F) * 0.25F * speed;
        Vector3f d = new Vector3f(0.0F, 1.0F, sway).normalize();
        for (int i = 0; i < LINKS; i++) {
            if (i < 2) place(links[i], h, d, i * LINK_PITCH + LINK_HALF, 0.0F, 0.0F);
            else links[i].visible = false;
        }
        place(hook, h, d, 2 * LINK_PITCH + 4.0F, 0.0F, 0.0F);
    }

    /**
     * The chain straight out ahead of the fist, pitched {@code pitch}, {@code len} pixels from fist to head;
     * {@code sag} pixels of droop at its middle.
     */
    private void line(float pitch, float len, float sag) {
        Vector3f h = fist();
        Vector3f d = new Vector3f(0.0F, -Mth.sin(pitch), -Mth.cos(pitch));
        for (int i = 0; i < LINKS; i++) {
            float along = i * LINK_PITCH + LINK_HALF;
            if (along + LINK_HALF > len) {
                links[i].visible = false;
                continue;
            }
            place(links[i], h, d, along, sagAt(along, len, sag), slope(along, len, sag));
        }
        place(hook, h, d, Math.max(LINK_HALF, len), 0.0F, 0.0F);
    }

    private static float sagAt(float along, float len, float sag) {
        float u = len > 0.0F ? along / len : 0.0F;
        return sag * 4.0F * u * (1.0F - u);
    }

    private static float slope(float along, float len, float sag) {
        if (len <= 0.0F || sag == 0.0F) return 0.0F;
        return sag * 4.0F * (1.0F - 2.0F * along / len) / len;
    }

    /** The chain whirling in a ring over the raised fist, {@code k} 0..1 how far the arm has come up. */
    private void whirl(float spin, float k) {
        Vector3f h = fist();
        float r = WHIRL_RADIUS * k;
        float cx = h.x, cy = h.y - 2.0F, cz = h.z;
        for (int i = 0; i < LINKS; i++) {
            ModelPart p = links[i];
            if (i >= WHIRL_LINKS) {
                p.visible = false;
                continue;
            }
            float a = spin + i * (Mth.TWO_PI / (WHIRL_LINKS + 1));
            Vector3f c = new Vector3f(cx + Mth.cos(a) * r, cy, cz + Mth.sin(a) * r);
            Vector3f tangent = new Vector3f(-Mth.sin(a), 0.0F, Mth.cos(a));
            orient(p, c, tangent);
        }
        float a = spin + WHIRL_LINKS * (Mth.TWO_PI / (WHIRL_LINKS + 1));
        orient(hook, new Vector3f(cx + Mth.cos(a) * r, cy, cz + Mth.sin(a) * r), new Vector3f(-Mth.sin(a), 0.0F, Mth.cos(a)));
    }

    /**
     * Puts {@code p} {@code along} pixels from {@code h} on direction {@code d}, dropped {@code drop} pixels, lying along
     * {@code d} bent down by {@code slope} (pixels down per pixel along).
     */
    private static void place(ModelPart p, Vector3f h, Vector3f d, float along, float drop, float slope) {
        Vector3f c = new Vector3f(d).mul(along).add(h).add(0.0F, drop, 0.0F);
        Vector3f dir = new Vector3f(d).add(0.0F, slope, 0.0F).normalize();
        orient(p, c, dir);
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
