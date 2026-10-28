package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.GrandOrrery.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Grand Orrery: three tilted rings turn round the star core at their own speeds, each carrying its planets, while the
 * whole clockwork bobs where it hangs. The spin-up winds the rings faster and faster and the cage hugs the core; the
 * spiral turns the whole orrery once round; the double ring snaps the two outer rings level, one turned against the
 * other; the burst stands all three rings upright and nested toward the target, like a sight; open, the rings slow
 * almost to a stop and drift apart, the four ribs of the cage bloom outward, the caps lift off and the core swells.
 */
public class GrandOrreryModel extends TeemingModel {
    /** Each ring's own spin at rest, in radians a tick: outer, middle, inner. */
    private static final float[] SPIN = {0.03F, -0.045F, 0.065F};
    /** How far each ring drifts from the core while it is open, in model pixels, and which way (x, y, z). */
    private static final float[][] PART = {{0.0F, -6.0F, 0.0F}, {-5.0F, 3.0F, 2.0F}, {5.0F, 3.0F, -2.0F}};

    private final ModelPart body, core, coreStar, capTop, capBottom;
    private final ModelPart[] rings = new ModelPart[3], spins = new ModelPart[3], ribs = new ModelPart[4];
    private final float[][] tilt = new float[3][3];

    public GrandOrreryModel(ModelPart root) {
        super(root, CreatureLayers.GRAND_ORRERY_ONLY);
        body = part("body");
        core = part("core");
        coreStar = part("core_star");
        capTop = part("cap_top");
        capBottom = part("cap_bottom");
        String[] names = {"ring_outer", "ring_mid", "ring_inner"};
        for (int i = 0; i < 3; i++) {
            rings[i] = part(names[i]);
            spins[i] = part(names[i] + "_spin");
            tilt[i] = new float[] {rings[i].xRot, rings[i].yRot, rings[i].zRot};   // the rest tilts, from the rig
        }
        for (int i = 0; i < ribs.length; i++) ribs[i] = part("rib_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        body.y += Mth.sin(t * 0.08F) * 0.8F;                                         // it hangs on nothing, bobbing
        float wind = 1.0F;                                                            // how fast the rings turn, times their rest spin
        float bloom = 0.0F, swell = 1.0F;
        core.yRot += t * 0.02F;
        coreStar.yRot += -t * 0.03F;

        if (s.phase == Step.SPIN.ordinal()) {
            float k = smooth(s.progress);
            wind = 1.0F + 9.0F * k * k;                                              // winding up, faster and faster
            bloom = -0.12F * k;                                                       // the cage hugs the core
            swell = 1.0F + 0.08F * Mth.sin(s.phaseTime * 1.3F) * k;                   // the core throbs as motes gather
            for (int i = 0; i < 3; i++) shrink(rings[i], 1.0F - 0.08F * k);
        } else if (s.phase == Step.SPIRAL.ordinal()) {
            wind = 10.0F;
            body.yRot += Mth.TWO_PI * s.progress;                                     // the whole clockwork turns once with the spiral
        } else if (s.phase == Step.DOUBLE_RING.ordinal()) {
            float k = smooth(Math.min(1.0F, s.progress * 4.0F));                      // snapped level in the first few ticks
            wind = 6.0F;
            for (int i = 0; i < 2; i++) {
                rings[i].xRot = Mth.lerp(k, tilt[i][0], 0.0F);
                rings[i].zRot = Mth.lerp(k, tilt[i][2], 0.0F);
                shrink(rings[i], 1.0F + 0.15F * k * (i == 0 ? 1.0F : 0.6F));         // flung wide, the outer one widest
            }
            rings[1].y += 2.0F * k;                                                   // the two rings a little apart
            rings[0].y -= 2.0F * k;
        } else if (s.phase == Step.BURST.ordinal()) {
            float k = smooth(Math.min(1.0F, s.progress * 4.0F));
            wind = 4.0F;
            for (int i = 0; i < 3; i++) {                                             // upright and facing the aim: rings nested like a sight
                rings[i].xRot = Mth.lerp(k, tilt[i][0], Mth.HALF_PI);
                rings[i].yRot = Mth.lerp(k, tilt[i][1], 0.0F);
                rings[i].zRot = Mth.lerp(k, tilt[i][2], 0.0F);
                rings[i].z -= 2.5F * i * k;                                           // stepped forward, the smallest in front
            }
            float kick = 1.0F - Math.min(1.0F, (s.phaseTime % 6) / 3.0F);           // a jolt at each volley
            body.z += 0.8F * kick;
        } else if (s.phase == Step.OPEN.ordinal()) {
            float k = envelope(s.progress, 0.08F, 0.1F);
            wind = Mth.lerp(k, 1.0F, 0.1F);                                          // the rings all but stop
            bloom = 0.9F * k;                                                         // the cage opens like petals
            swell = 1.0F + 0.25F * k + 0.05F * Mth.sin(t * 0.3F) * k;                // the core swells and pulses
            for (int i = 0; i < 3; i++) {
                rings[i].x += PART[i][0] * k;
                rings[i].y += PART[i][1] * k;
                rings[i].z += PART[i][2] * k;
            }
            capTop.y -= 3.0F * k;
            capBottom.y += 3.0F * k;
        }

        // each ring's angle: its rest spin, plus what the phase has wound on (or let off) since it began, so the rings
        // never jump; the spin-up's wind climbs from 1, so it has wound on about half its top rate so far
        float extra = (wind - 1.0F) * s.phaseTime * (s.phase == Step.SPIN.ordinal() ? 0.5F : 1.0F);
        for (int i = 0; i < 3; i++) spins[i].yRot = (t + extra) * SPIN[i];
        for (ModelPart rib : ribs) rib.xRot = bloom;                                 // each rib tips out on its own hinge
        for (ModelPart c : new ModelPart[] {core, coreStar}) c.xScale = c.yScale = c.zScale = swell;
    }

    private static void shrink(ModelPart p, float scale) {
        p.xScale = p.yScale = p.zScale = scale;
    }
}
