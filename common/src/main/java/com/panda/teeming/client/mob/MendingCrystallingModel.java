package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.MendingCrystalling.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Mending Crystalling: it bobs and turns slowly in the air, its eight facets leaning in to a point above and below the
 * core, breathing a little, and three small shards circling it. While its beam holds, the upper facets swing out
 * like opening petals round the bright core, which swells and pulses, and the crystal and its shards spin faster.
 */
public class MendingCrystallingModel extends TeemingModel {
    /**
     * The facets' lean at rest (in, to a point) and fully open while it mends: the upper ones bloom out like petals, the
     * lower ones only part a little, so the open crystal reads as a flower over its keel, not a figure with limbs.
     */
    private static final float LEAN_REST = -8.0F * DEG, LEAN_OPEN = 28.0F * DEG, LOWER_OPEN = 4.0F * DEG;

    private final ModelPart body, core, crown, keel, halo;
    private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4];

    public MendingCrystallingModel(ModelPart root) {
        super(root, CreatureLayers.MENDING_CRYSTALLING_ONLY);
        body = part("body");
        core = part("core");
        crown = part("crown");
        keel = part("keel");
        halo = part("halo");
        for (int i = 0; i < 4; i++) {
            upper[i] = part("upper_" + i);
            lower[i] = part("lower_" + i);
        }
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean mending = s.phase == Step.MEND.ordinal();
        // the beam is open-ended: the facets open over its first 8 ticks and stay open
        float k = mending ? smooth(s.phaseTime / 8.0F) : 0.0F;

        float bob = Mth.sin(t * 0.1F) * 0.8F;
        body.y += bob;
        body.yRot += t * (0.03F + 0.05F * k);
        halo.y += bob * 0.6F + Mth.sin(t * 0.13F + 1.0F) * 0.4F;
        halo.yRot = -t * (0.06F + 0.1F * k);
        float ring = 1.0F + 0.3F * k;
        halo.xScale = ring;
        halo.zScale = ring;

        for (int i = 0; i < 4; i++) {
            float breathe = Mth.sin(t * 0.12F + i * 1.6F) * 2.5F * DEG;
            // on a plate at +x, a positive z turn tips an upper facet's top outward and a lower facet's foot inward
            upper[i].zRot = Mth.lerp(k, LEAN_REST, LEAN_OPEN) + breathe;
            lower[i].zRot = -(Mth.lerp(k, LEAN_REST, LOWER_OPEN) + breathe);
        }
        crown.y -= 1.5F * k;                                                     // the points draw apart as it opens
        keel.y += 1.5F * k;

        float beat = 0.5F + 0.5F * Mth.sin(mending ? s.phaseTime * 0.6F : t * 0.08F);
        float size = 1.0F + (mending ? 0.25F + 0.2F * beat : 0.08F * beat);
        core.xScale = size;
        core.yScale = size;
        core.zScale = size;
    }
}
