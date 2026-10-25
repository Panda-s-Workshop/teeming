package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.CindermothSwarm.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Cindermoth Swarm: fifteen moths drawn as one mob, each flying its own small orbit round its place in the cloud with
 * quick wingbeats. Drifting, the cloud is a loose ball, the moths circling lazily. Seeking a light, it draws in and
 * strings out forward, every moth turned to the light and beating harder. Circling a light on the ground, the moths
 * spread into a low ring round it. Engulfing someone, they swirl round them in a wide, frantic shell that shows outside the victim's body. Scattered by a blow, they
 * burst outward tumbling, then drift back together over the two seconds.
 */
public class CindermothSwarmModel extends TeemingModel {
    /** How many moths the cloud has; the rig names them moth_0 .. moth_14. */
    public static final int MOTHS = 15;

    private final Flock flock;

    public CindermothSwarmModel(ModelPart root) {
        super(root, CreatureLayers.CINDERMOTH_SWARM_ONLY);
        ModelPart[] moths = new ModelPart[MOTHS], left = new ModelPart[MOTHS], right = new ModelPart[MOTHS];
        for (int i = 0; i < MOTHS; i++) {
            moths[i] = part("moth_" + i);
            left[i] = part("moth_" + i + "_wing_left");
            right[i] = part("moth_" + i + "_wing_right");
        }
        flock = new Flock(moths, left, right, 0.0F, 16.0F, 0.0F);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        Flock.Shape shape = new Flock.Shape();
        shape.bob = Mth.sin(t * 0.15F) * 0.8F;
        if (s.phase == Step.SEEK.ordinal()) {
            shape.spread = 0.8F;
            shape.stream = 0.7F;                                           // strung out toward the light
            shape.facing = 0.85F;                                          // every moth turned to it
            shape.jitter = 1.0F;
            shape.swirl = 0.09F;
            shape.beat = 2.4F;
            shape.lift = 0.7F;
        } else if (s.phase == Step.HOVER.ordinal()) {
            shape.ring = envelope(s.progress, 0.06F, 0.08F);              // a low ring round the light below
            shape.ringRadius = 6.5F;
            shape.ringDrop = 5.0F;
            shape.jitter = 0.8F;
            shape.swirl = 0.07F;
            shape.beat = 1.7F;
            shape.lift = 0.75F;
        } else if (s.phase == Step.ENGULF.ordinal()) {
            shape.spread = 1.5F;                                           // a frantic shell swirling round the victim,
            shape.jitter = 2.0F;                                           // wide enough to show outside its body
            shape.swirl = 0.3F;
            shape.beat = 3.0F;
            shape.lift = 0.95F;
        } else if (s.phase == Step.SCATTER.ordinal()) {
            float p = Mth.clamp(s.progress, 0.0F, 1.0F);
            float burst = p < 0.15F ? smooth(p / 0.15F) : smooth((1.0F - p) / 0.85F);
            shape.spread = 1.0F + 1.8F * burst;                            // flung outward, then drifting back
            shape.jitter = 1.5F + 2.0F * burst;
            shape.tumble = burst;
            shape.swirl = 0.12F;
            shape.beat = 3.2F;
            shape.lift = 1.0F;
        }
        flock.pose(t, shape);
    }

    // ---- the swarm: many small fliers drawn as one mob ---------------------------------------------------------------

    /**
     * A cloud of small fliers drawn as one mob, self-contained so any swarm can use it. Each flier is a part with two
     * wing parts; it keeps the place the rig gave it (its rest pose) relative to the cloud's middle, and every frame
     * flies a small orbit of its own round that place, beating its wings. A {@link Shape} says how the whole cloud
     * sits this frame: spread out or drawn in, strung out forward, gathered into a ring, tumbling.
     */
    static final class Flock {
        /** How the cloud sits this frame. The defaults are a loose ball drifting about. */
        static final class Shape {
            /** Scale of every flier's distance from the middle (1 = where the rig put it). */
            float spread = 1.0F;
            /** 0..1: the cloud strings out along its heading, the fliers in front pulled ahead. */
            float stream = 0.0F;
            /** 0..1: how far each flier turns from its orbit's heading to the cloud's own heading. */
            float facing = 0.0F;
            /** 0..1: blend toward a flat ring of {@code ringRadius}, {@code ringDrop} pixels below the middle. */
            float ring = 0.0F;
            float ringRadius = 6.0F;
            float ringDrop = 0.0F;
            /** Radius of each flier's own orbit, in pixels, and how fast they fly it. */
            float jitter = 1.5F;
            float swirl = 0.06F;
            /** 0..1: fliers roll and pitch wildly. */
            float tumble = 0.0F;
            /** Wingbeat rate (radians a tick) and depth (radians). */
            float beat = 1.9F;
            float lift = 0.55F;
            /** The whole cloud's bob, in pixels. */
            float bob = 0.0F;
        }

        private final ModelPart[] fliers, left, right;
        private final float[] restX, restY, restZ, restYaw, restRoll, phase, pace;
        private final float cx, cy, cz;

        /** The fliers and their wings, in the rig's rest pose, round a middle at (cx, cy, cz) in model pixels. */
        Flock(ModelPart[] fliers, ModelPart[] left, ModelPart[] right, float cx, float cy, float cz) {
            this.fliers = fliers;
            this.left = left;
            this.right = right;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            int n = fliers.length;
            restX = new float[n];
            restY = new float[n];
            restZ = new float[n];
            restYaw = new float[n];
            restRoll = new float[n];
            phase = new float[n];
            pace = new float[n];
            for (int i = 0; i < n; i++) {
                restX[i] = fliers[i].x;
                restY[i] = fliers[i].y;
                restZ[i] = fliers[i].z;
                restYaw[i] = fliers[i].yRot;
                restRoll[i] = fliers[i].zRot;
                phase[i] = i * 2.39996F;                                   // golden angle: no two in step
                pace[i] = 0.8F + 0.4F * ((i * 7) % 5) / 4.0F;              // each its own speed
            }
        }

        /** Poses every flier for this frame (the base model has already reset them to rest). */
        void pose(float t, Shape s) {
            int n = fliers.length;
            for (int i = 0; i < n; i++) {
                ModelPart f = fliers[i];
                float ox = restX[i] - cx, oy = restY[i] - cy, oz = restZ[i] - cz;
                // its own orbit round its place in the cloud
                float a = phase[i] + t * s.swirl * pace[i];
                float jx = Mth.cos(a) * s.jitter, jz = Mth.sin(a) * s.jitter;
                float jy = Mth.sin(a * 1.3F + phase[i]) * s.jitter * 0.6F;
                // the cloud: spread, strung out along -Z (its heading), fliers in front pulled farther ahead
                float x = ox * s.spread * (1.0F - 0.35F * s.stream);
                float y = oy * s.spread * (1.0F - 0.35F * s.stream);
                float z = oz * s.spread * (1.0F + 0.8F * s.stream) - 3.0F * s.stream;
                // the ring: an even circle round the middle, turning slowly
                float ra = Mth.TWO_PI * i / n + t * 0.05F;
                float rx = Mth.cos(ra) * s.ringRadius, rz = Mth.sin(ra) * s.ringRadius, ry = s.ringDrop + Mth.sin(t * 0.2F + i) * 0.6F;
                f.x = cx + Mth.lerp(s.ring, x, rx) + jx;
                f.y = cy + Mth.lerp(s.ring, y, ry) + jy + s.bob;
                f.z = cz + Mth.lerp(s.ring, z, rz) + jz;
                // heading: along its orbit, or along the ring, or turned to the cloud's heading
                float orbitYaw = -a - Mth.HALF_PI;
                float ringYaw = -ra - Mth.PI;
                float yaw = Mth.lerp(s.ring, orbitYaw, ringYaw);
                yaw = yaw + wrap(-yaw) * s.facing;
                f.yRot = yaw + restYaw[i] * (1.0F - Math.max(s.ring, s.facing)) * 0.25F;
                f.zRot = restRoll[i] + Mth.sin(a) * 0.25F + Mth.sin(t * 0.9F + phase[i]) * 1.4F * s.tumble;
                f.xRot = Mth.sin(a * 0.7F) * 0.15F + Mth.cos(t * 0.8F + phase[i]) * 1.2F * s.tumble;
                // wingbeats: up is positive roll for the right wing, negative for the left
                float flap = Mth.sin(t * s.beat * pace[i] + phase[i]) * s.lift;
                right[i].zRot += flap + 0.15F;
                left[i].zRot -= flap + 0.15F;
            }
        }

        /** The angle brought into -pi..pi. */
        private static float wrap(float a) {
            return Mth.wrapDegrees(a * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        }
    }
}
