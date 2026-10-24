package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Levimoth.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Levimoth: a constant flutter and bob, wings flared wide and the dust sacs swelling in the tell, a hard shake on release. */
public class LevimothModel extends TeemingModel {
    /**
     * The wings' roll at rest: a V. The rig also tips their trailing edges down 28 degrees, so their faces turn toward a
     * player in front of and below the moth (where players stand), not just their edges.
     */
    private static final float WING_REST = 28.0F * DEG;
    /** The tell: wings raised into a steep V and tipped toward the threat, eyespots facing it. */
    private static final float WING_FLARE = 55.0F * DEG, WING_FLARE_PITCH = -40.0F * DEG;

    private final ModelPart body, head, abdomen, sacs, wingRight, wingLeft;

    public LevimothModel(ModelPart root) {
        super(root, CreatureLayers.LEVIMOTH_ONLY);
        body = part("body");
        head = part("head");
        abdomen = part("abdomen");
        sacs = part("dust_sacs");
        wingRight = part("wing_right");
        wingLeft = part("wing_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float flap = Mth.sin(t * 1.3F) * 0.55F;
        wingRight.zRot = WING_REST + flap;
        wingLeft.zRot = -WING_REST - flap;
        body.y += Mth.sin(t * 0.15F) * 0.8F;
        body.xRot = 0.15F * speed;
        abdomen.xRot += Mth.sin(t * 0.15F + 1.0F) * 0.06F;
        head.xRot = s.xRot * DEG * 0.5F;
        head.yRot = s.yRot * DEG * 0.5F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);
            float pulse = 1.0F + (0.25F + 0.15F * Mth.sin(s.phaseTime * 1.6F)) * k;
            wingRight.zRot = Mth.lerp(k, wingRight.zRot, WING_FLARE) + Mth.sin(s.phaseTime * 3.0F) * 0.05F;   // raised and shown
            wingLeft.zRot = Mth.lerp(k, wingLeft.zRot, -WING_FLARE) - Mth.sin(s.phaseTime * 3.0F) * 0.05F;
            wingRight.xRot += WING_FLARE_PITCH * k;
            wingLeft.xRot += WING_FLARE_PITCH * k;
            wingRight.yRot = 0.3F * k;
            wingLeft.yRot = -0.3F * k;
            abdomen.xRot += 0.45F * k;                                   // tail curls down to show the sacs
            sacs.xScale = pulse;
            sacs.yScale = pulse;
            sacs.zScale = pulse;
        } else if (s.phase == Step.RELEASE.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            float shake = Mth.sin(s.phaseTime * 4.0F) * 0.6F * k;
            wingRight.zRot = WING_REST + shake;
            wingLeft.zRot = -WING_REST - shake;
            abdomen.xRot += 0.45F * k + Mth.sin(s.phaseTime * 3.0F) * 0.15F * k;
            float shrink = 1.0F - 0.3F * k;
            sacs.xScale = shrink;
            sacs.yScale = shrink;
            sacs.zScale = shrink;
        }
        if (s.attack > 0.0F) head.xRot += Mth.sin(s.attack * Mth.PI) * 0.6F;    // the bite
    }
}
