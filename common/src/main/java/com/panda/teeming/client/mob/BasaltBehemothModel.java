package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.BasaltBehemoth.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Basalt Behemoth: a slow knuckle walk with the weight rolling side to side; for the tell it rears up on its hind legs,
 * forelimbs high, jaw open in a roar, shaking; the slam drops it forward onto its fists, and its chest plates grind apart
 * to either side for the whole open window, then shut.
 */
public class BasaltBehemothModel extends TeemingModel {
    /** How far each plate slides aside, in model pixels: its own width, so the whole core shows. */
    private static final float PLATE_SLIDE = 5.0F;
    /** The slam's impact pose eases out over this many ticks of the open window. */
    private static final float IMPACT_TICKS = 8.0F;

    private final ModelPart body, head, jaw, armRight, armLeft, foreRight, foreLeft, legRight, legLeft, plateRight, plateLeft;

    public BasaltBehemothModel(ModelPart root) {
        super(root, CreatureLayers.BASALT_BEHEMOTH_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        foreRight = part("forearm_right");
        foreLeft = part("forearm_left");
        legRight = part("leg_back_right");
        legLeft = part("leg_back_left");
        plateRight = part("plate_right");
        plateLeft = part("plate_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // the knuckle walk: forelimbs and hind legs in opposite pairs, the whole mass rolling over each step
        float gait = Mth.cos(pos * 0.45F);
        armRight.xRot = gait * 0.45F * speed;
        armLeft.xRot = -gait * 0.45F * speed;
        legRight.xRot = -gait * 0.5F * speed;
        legLeft.xRot = gait * 0.5F * speed;
        body.zRot = gait * 0.05F * speed;
        body.y += Math.abs(Mth.sin(pos * 0.45F)) * 0.8F * speed + Mth.sin(t * 0.05F) * 0.25F;   // each step, and slow breathing
        head.yRot = s.yRot * DEG * 0.6F;
        head.xRot = s.xRot * DEG * 0.4F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);                                // up on its hind legs in the first 0.4 s
            float shake = Mth.sin(s.phaseTime * 2.4F) * 0.035F * k;
            body.xRot = -0.85F * k;
            body.y -= 1.5F * k;
            body.zRot = shake;
            armRight.xRot = -2.2F * k;
            armLeft.xRot = -2.2F * k;
            armRight.zRot = 0.25F * k;
            armLeft.zRot = -0.25F * k;
            foreRight.xRot = -0.55F * k;
            foreLeft.xRot = -0.55F * k;
            legRight.xRot = 0.15F * k;
            legLeft.xRot = 0.15F * k;
            head.xRot = -0.15F * k;
            head.yRot = 0.0F;
            jaw.xRot = 0.6F * k;
        } else if (s.phase == Step.EXPOSED.ordinal()) {
            float impact = 1.0F - smooth(s.phaseTime / IMPACT_TICKS);           // slammed down onto its fists, easing out
            body.xRot += 0.3F * impact;
            body.y += 2.0F * impact;
            armRight.xRot += -0.55F * impact;
            armLeft.xRot += -0.55F * impact;
            foreRight.xRot = 0.35F * impact;
            foreLeft.xRot = 0.35F * impact;
            jaw.xRot = 0.3F * impact;
            float open = envelope(s.progress, 0.06F, 0.1F);                    // the chest grinds open, and shuts at the end
            float tremble = Mth.sin(s.phaseTime * 1.7F) * 0.15F * open;
            plateRight.x -= PLATE_SLIDE * open + tremble;                      // each slides out to its own side
            plateLeft.x += PLATE_SLIDE * open + tremble;
            plateRight.z -= 0.6F * open;                                       // and a little forward, clear of the chest
            plateLeft.z -= 0.6F * open;
        }
        if (s.attack > 0.0F) {                                                  // a swat with the right forelimb
            float a = Mth.sin(s.attack * Mth.PI);
            armRight.xRot -= 1.3F * a;
            armRight.zRot += 0.3F * a;
            body.yRot += 0.15F * a;
        }
    }
}
