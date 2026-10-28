package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.ResonantTriad;
import com.panda.teeming.mob.end.ResonantTriad.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Resonant Triad: a crystal tuning fork that bobs and sways in the air. The singer's tines thrum (a fast shiver, spread a
 * little); the next singer's spread and lift while it brightens; a fork whose chord broke hangs tilted with its tines
 * splayed apart and out of true. The zap's crackle leans the fork in toward its target with the tines pinched and
 * trembling, and the zap snaps it forward and lets it swing back.
 */
public class ResonantTriadModel extends TeemingModel {
    private final ModelPart body, tineL, tineR, heart;

    public ResonantTriadModel(ModelPart root) {
        super(root, CreatureLayers.RESONANT_TRIAD_ONLY);
        body = part("body");
        tineL = part("tine_l");
        tineR = part("tine_r");
        heart = part("heart");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean broken = s.stage == ResonantTriad.STAGE_BROKEN;
        float bob = Mth.sin(t * (broken ? 0.06F : 0.1F)) * (broken ? 0.5F : 0.8F);
        body.y += bob;
        body.zRot += Mth.sin(t * 0.05F) * 4.0F * DEG;

        // a positive turn about z tips the left tine (at +x) outward; the right tine mirrors it
        float spread = 0.0F;
        if (s.stage == ResonantTriad.STAGE_SING) {
            spread = 3.0F * DEG + Mth.sin(t * 2.6F) * 2.5F * DEG;          // the thrum of a struck fork
        } else if (s.stage == ResonantTriad.STAGE_RISE) {
            spread = 6.0F * DEG + Mth.sin(t * 1.4F) * 1.0F * DEG;
            body.y -= 1.0F;
        } else if (broken) {
            tineL.zRot += 15.0F * DEG;                                       // splayed and out of true
            tineR.zRot -= 8.0F * DEG;
            tineR.xRot += 6.0F * DEG;
            body.zRot += 12.0F * DEG;
        }

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.0F);
            body.xRot += 22.0F * DEG * k;                                    // leans in toward its target
            spread = -5.0F * DEG * k + Mth.sin(t * 3.1F) * 1.5F * DEG;       // tines pinched, trembling
            heart.xScale = heart.yScale = heart.zScale = 1.0F + 0.25F * k;
        } else if (s.phase == Step.ZAP.ordinal()) {
            float k = 1.0F - s.progress;
            body.xRot += 34.0F * DEG * k;                                    // snapped forward, swinging back
            spread = 8.0F * DEG * k;
        }
        tineL.zRot += spread;
        tineR.zRot -= spread;
    }
}
