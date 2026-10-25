package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.OssuaryWyrm;
import com.panda.teeming.mob.nether.OssuaryWyrm.Joint;
import com.panda.teeming.mob.nether.OssuaryWyrm.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Ossuary Wyrm: every part (the skull and eight vertebrae, each a root part) is placed on the chain's joints each frame,
 * the same joints the mob's hitboxes sit on, and turned along the chain from its neighbours. Swimming or stranded a
 * sideways wave runs down the body; in the rumble only the horns and crown break the surface, the skull tilted up and
 * shuddering; in the arc the skull rises out of the sand, the jaw gapes on the way down and snaps shut where it bites,
 * and the body follows through the same arc. Still (a trophy, a portrait), it lies coiled with the skull reared.
 */
public class OssuaryWyrmModel extends TeemingModel {
    private static final float TELL_PITCH = 12.0F * DEG;     // the skull's tilt just under the surface in the rumble
    private static final float REST_PITCH = 12.0F * DEG;     // coiled, it looks ahead and a little down
    private final ModelPart skull, jaw;
    private final ModelPart[] parts = new ModelPart[OssuaryWyrm.SEGMENTS + 1];
    private final double[] f = new double[OssuaryWyrm.SEGMENTS + 1], l = new double[OssuaryWyrm.SEGMENTS + 1], u = new double[OssuaryWyrm.SEGMENTS + 1];

    public OssuaryWyrmModel(ModelPart root) {
        super(root, CreatureLayers.OSSUARY_WYRM_ONLY);
        skull = part("skull");
        jaw = part("jaw");
        parts[0] = skull;
        for (int i = 1; i <= OssuaryWyrm.SEGMENTS; i++) parts[i] = part("seg" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        Step[] steps = Step.values();
        Step step = steps[Math.min(Math.max(s.phase, 0), steps.length - 1)];
        Joint[] joints = OssuaryWyrm.joints(step, s.progress, s.flags);
        boolean submerged = s.flag(OssuaryWyrm.SUBMERGED), stranded = s.flag(OssuaryWyrm.STRANDED);
        boolean flat = step == Step.IDLE && (submerged || stranded);
        double amp = stranded ? 0.14 : 0.08;
        for (int i = 0; i < joints.length; i++) {
            f[i] = joints[i].forward();
            l[i] = joints[i].left() + (flat && i > 0 ? amp * Math.min(1.0, i / 2.0) * Math.sin(t * 0.35 - i * 0.9) : 0.0);
            u[i] = joints[i].up();
        }
        for (int i = 0; i < parts.length; i++) {
            int a = i == 0 ? 0 : i - 1, b = i == parts.length - 1 ? i : i + 1;
            double df = f[a] - f[b], dl = l[a] - l[b], du = u[a] - u[b];
            ModelPart p = parts[i];
            p.x = (float) (16.0 * l[i]);
            p.y = (float) (24.0 - 16.0 * u[i]);
            p.z = (float) (-16.0 * f[i]);
            p.xRot = (float) -Math.atan2(du, Math.sqrt(df * df + dl * dl));
            p.yRot = (float) Math.atan2(-dl, df);
            p.zRot = 0.0F;
        }
        jaw.xRot = 0.05F + 0.03F * Mth.sin(t * 0.07F);

        if (step == Step.TELL) {
            float k = smooth(s.progress * 3.0F);
            skull.xRot = -TELL_PITCH * k;                         // tilted up under the surface: the horns and crown break it
            skull.yRot = 0.0F;
            skull.zRot = Mth.sin(s.phaseTime * 2.3F) * 0.06F * k; // shuddering with the rumble
            jaw.xRot = 0.12F * k;
        } else if (step == Step.ARC) {
            float k = smooth(s.progress / 0.2F);                  // from the rumble's tilt to the arc's heading as it breaks out
            skull.xRot = Mth.lerp(k, -TELL_PITCH, skull.xRot);
            double head = OssuaryWyrm.headS(step, s.progress);
            float open = smooth((float) ((head - 4.5) / 2.5));     // the jaw gapes past the top of the arc...
            if (head > OssuaryWyrm.S_BITE) open = 0.15F;          // ...and snaps shut where it bites
            jaw.xRot = 0.05F + 0.95F * open;
        } else if (step == Step.IDLE && stranded) {
            skull.xRot = -0.2F + 0.08F * Mth.sin(t * 0.2F);       // stranded: the skull raised, snapping at the air
            jaw.xRot = 0.2F + 0.25F * Math.max(0.0F, Mth.sin(t * 0.4F));
        } else if (step == Step.IDLE && !submerged) {
            skull.xRot = REST_PITCH;                              // coiled: looking ahead, swaying a little
            skull.yRot = 0.15F * Mth.sin(t * 0.05F);
            skull.y += 0.5F * Mth.sin(t * 0.08F);
        }
    }
}
