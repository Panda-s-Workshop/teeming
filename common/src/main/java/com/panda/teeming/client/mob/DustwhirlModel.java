package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Dustwhirl.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Dustwhirl: six dust rings, each spinning at its own rate (faster towards the top) and swaying into a snaking column,
 * with pebbles and a twig orbiting. Spinning up, the funnel tightens and stretches while the spin climbs; gusting, it
 * spins hard and flares wide at the top; slowing down, it barely turns, the upper funnel lifts and the middle opens
 * round the core, which swells so the gold weak point shows.
 *
 * <p>The rings are siblings under {@code funnel}, so each one's scale and spin are its own.
 */
public class DustwhirlModel extends TeemingModel {
    private static final int RINGS = 6;

    private final ModelPart funnel, core;
    private final ModelPart[] rings = new ModelPart[RINGS];
    private final ModelPart[] debris = new ModelPart[3];

    public DustwhirlModel(ModelPart root) {
        super(root, CreatureLayers.DUSTWHIRL_ONLY);
        funnel = part("funnel");
        core = part("core");
        for (int i = 0; i < RINGS; i++) rings[i] = part("ring_" + i);
        for (int i = 0; i < debris.length; i++) debris[i] = part("debris_" + i);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float spinMul = 1.0F, spinExtra = 0.0F, sway = 1.0F, flare = 0.0F, tighten = 0.0F, open = 0.0F, orbit = 1.0F;
        if (s.phase == Step.SPIN.ordinal()) {
            float k = smooth(s.progress);
            spinExtra = s.phaseTime * s.phaseTime * 0.02F;                  // the spin climbs through the tell
            tighten = k;
            sway = 1.0F - 0.7F * k;
            orbit = 1.0F + 1.5F * k;
        } else if (s.phase == Step.GUST.ordinal()) {
            spinMul = 3.0F;
            flare = envelope(s.progress, 0.08F, 0.1F);
            sway = 0.5F;
            orbit = 3.0F;
        } else if (s.phase == Step.SLOW.ordinal()) {
            spinMul = 0.25F;
            open = envelope(s.progress, 0.12F, 0.15F);
            sway = 1.6F;
            orbit = 0.3F;
        }

        for (int i = 0; i < RINGS; i++) {
            ModelPart ring = rings[i];
            float rate = 0.22F + 0.05F * i;
            ring.yRot = t * rate * spinMul + spinExtra * (1.0F + 0.15F * i) + i * 0.6F;
            ring.x += Mth.sin(t * 0.12F + i * 0.7F) * i * 0.3F * sway;   // the column snakes, more towards the top
            ring.z += Mth.cos(t * 0.1F + i * 0.6F) * i * 0.22F * sway;
            float wide = 1.0F - 0.22F * tighten + flare * (0.12F + 0.07F * i);
            ring.y -= tighten * i * 0.45F;                                  // stretched up as it tightens
            if (i >= 3) ring.y -= open * (3.5F + (i - 3) * 0.8F);           // the upper funnel lifts off the core
            if (i == 2) {                                                   // the ring round the core sinks and squashes
                ring.y += open * 1.5F;
                ring.yScale = 1.0F - 0.45F * open;
            }
            if (i == 2 || i == 3) wide += 0.3F * open;
            ring.xScale = wide;
            ring.zScale = wide;
        }

        float coreScale = 1.0F + 0.35F * open + (tighten > 0.0F || flare > 0.0F ? 0.08F * Mth.sin(t * 1.3F) : 0.0F);
        core.xScale = coreScale;
        core.yScale = coreScale;
        core.zScale = coreScale;
        core.yRot = t * 0.15F;
        core.y += Mth.sin(t * 0.2F) * 0.4F - open * 1.0F;

        for (int i = 0; i < debris.length; i++) {
            ModelPart d = debris[i];
            d.yRot += -t * (0.18F + 0.06F * i) * orbit;
            d.y += Mth.sin(t * 0.25F + i * 2.0F) * 0.6F + open * (2.0F + i);   // the debris settles as it slows
            float r = 1.0F + flare * 0.5F - tighten * 0.15F;
            d.xScale = r;
            d.zScale = r;
        }

        funnel.zRot = Mth.sin(t * 0.07F) * 0.04F * sway + open * 0.1F;     // a lean as it runs down
        funnel.xRot = Mth.cos(t * 0.05F) * 0.03F * sway;
        if (s.attack > 0.0F) funnel.xRot -= Mth.sin(s.attack * Mth.PI) * 0.35F;   // it lurches into a melee hit
    }
}
