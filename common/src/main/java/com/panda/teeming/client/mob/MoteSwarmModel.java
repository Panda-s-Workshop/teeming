package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.MoteSwarm;
import com.panda.teeming.mob.end.MoteSwarm.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Mote Swarm: sixty tiny points round a faint core, two for each of its thirty motes, each swirling on an orbit of its
 * own round the middle (its own pace, its own height wobbling), so the cloud reads as one turning sphere of points.
 * Only two points are drawn for each mote the swarm has left (the synced count), so the sphere thins as it weakens.
 *
 * <p>Drifting, the motes swirl lazily. Gathering (the tell), the cloud pulls in tight round the core in 4 ticks and
 * spins up, every mote swelling (its brighter glow is the gather's own glowmask). Surging, the motes stream in and out
 * through the middle, each on its own beat, fast. Loosening after, the cloud spreads wide and settles back. Dying, the
 * motes fly apart and wink out. The Ember Motes' upper motes rise off the top in a plume and fade as they climb, so its
 * outline is a flame-shaped teardrop where the base look is a ball.
 */
public class MoteSwarmModel extends TeemingModel {
    /** How many points the rig has, two a mote: mote_0 .. mote_59. */
    public static final int POINTS = 2 * MoteSwarm.MOTES;

    private final ModelPart cloud;
    private final ModelPart core;
    private final ModelPart[] motes = new ModelPart[POINTS];
    private final float[] radius = new float[POINTS], polar = new float[POINTS], azimuth = new float[POINTS];
    private final float[] pace = new float[POINTS], beat = new float[POINTS];

    public MoteSwarmModel(ModelPart root) {
        super(root, CreatureLayers.MOTE_SWARM_ONLY);
        cloud = part("cloud");
        core = part("core");
        for (int i = 0; i < POINTS; i++) {
            ModelPart m = part("mote_" + i);
            motes[i] = m;
            // the rig's spot, round the cloud's middle (model y runs down: up is -y)
            float x = m.x, up = -m.y, z = m.z;
            radius[i] = Mth.sqrt(x * x + up * up + z * z);
            polar[i] = radius[i] < 1.0E-3F ? 0.0F : (float) Math.acos(Mth.clamp(up / radius[i], -1.0F, 1.0F));
            azimuth[i] = (float) Mth.atan2(z, x);
            pace[i] = 0.75F + 0.5F * ((i * 7) % 11) / 10.0F;              // each its own speed
            beat[i] = i * 2.39996F;                                        // golden angle: no two in step
        }
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float spread = 1.0F, swirl = 0.05F, size = 1.0F, churn = 0.0F, wobble = 0.25F;
        float coreSize = 1.0F;
        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 4.0F);                          // drawn in over 4 ticks, then held tight
            spread = 1.0F - 0.5F * k;
            swirl = 0.05F + 0.3F * k;
            size = 1.0F + 0.5F * k;
            wobble = 0.25F - 0.15F * k;
            coreSize = 1.0F + 0.35F * k;
        } else if (s.phase == Step.SURGE.ordinal()) {
            churn = 1.0F;                                                  // streaming in and out through the middle
            swirl = 0.4F;
            size = 1.3F;
            coreSize = 1.25F + 0.2F * Mth.sin(t * 1.3F);
        } else if (s.phase == Step.LOOSEN.ordinal()) {
            float p = Mth.clamp(s.progress, 0.0F, 1.0F);
            spread = 1.3F - 0.3F * smooth(p);                              // flung wide, then settling back
            swirl = 0.12F - 0.07F * smooth(p);
        }
        float dying = Mth.clamp(s.deathTime / 14.0F, 0.0F, 1.0F);
        spread *= 1.0F + 1.8F * dying;                                     // dying, the motes fly apart and wink out
        size *= 1.0F - dying;
        boolean ember = "ember_motes".equals(s.look);

        cloud.y += Mth.sin(t * 0.1F) * 0.6F;
        core.yRot = t * 0.03F;
        core.xRot = Mth.sin(t * 0.021F) * 0.35F;
        core.xScale = core.yScale = core.zScale = coreSize * (1.0F - dying);

        int shown = 2 * MoteSwarm.shown(s.flags);
        if (shown <= 0) shown = POINTS;                                    // not synced yet (a preview): the full cloud
        for (int i = 0; i < POINTS; i++) {
            ModelPart m = motes[i];
            m.visible = i < shown;
            if (!m.visible) continue;
            float a = azimuth[i] + t * swirl * pace[i];
            float p = polar[i] + Mth.sin(t * 0.05F * pace[i] + beat[i]) * wobble;
            float r = radius[i] * spread;
            if (churn > 0.0F) r *= 0.2F + 1.0F * Mth.abs(Mth.sin(t * 0.7F * pace[i] + beat[i]));
            float x = r * Mth.sin(p) * Mth.cos(a), up = r * Mth.cos(p), z = r * Mth.sin(p) * Mth.sin(a);
            float scale = size;
            if (ember && polar[i] < Mth.HALF_PI && dying <= 0.0F) {
                // the plume: an upper mote climbs off the top, drawing in and fading, then kindles again in its place
                float u = frac(t * 0.025F * pace[i] + beat[i] / Mth.TWO_PI);
                up += u * 10.0F;
                x *= 1.0F - 0.6F * u;
                z *= 1.0F - 0.6F * u;
                scale *= 1.0F - u;
            }
            m.x = x;
            m.y = -up;
            m.z = z;
            m.xScale = m.yScale = m.zScale = scale;
        }
    }

    private static float frac(float v) {
        return v - Mth.floor(v);
    }
}
