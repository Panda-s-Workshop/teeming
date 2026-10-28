package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Driftsong.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Driftsong: a slow beat of the fin-wings (the tips lagging behind), a gentle bob and a tail that sways side to side.
 * The swell lifts the wings and the head; the song holds them raised with the jaw open; the dive pitches the nose down
 * with the wings swept back.
 */
public class DriftsongModel extends TeemingModel {
    /** The wings' roll at rest: tips a little raised, so their faces show from above and from the side. */
    private static final float WING_REST = 14.0F * DEG;
    /** The swell and the song: the wings lifted high and spread. */
    private static final float WING_LIFT = 32.0F * DEG;
    /** The dive: the nose pitched down and the wings swept back. */
    private static final float DIVE_PITCH = 30.0F * DEG, DIVE_SWEEP = 40.0F * DEG;
    /** The beat: slow, about one stroke every four and a half seconds. */
    private static final float BEAT = 0.07F;

    private final ModelPart body, head, jaw, tail, tailTip, tailFin, wingRight, wingLeft, tipRight, tipLeft, finRight, finLeft;

    public DriftsongModel(ModelPart root) {
        super(root, CreatureLayers.DRIFTSONG_ONLY);
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        tail = part("tail");
        tailTip = part("tail_tip");
        tailFin = part("tail_fin");
        wingRight = part("wing_right");
        wingLeft = part("wing_left");
        tipRight = part("wing_right_tip");
        tipLeft = part("wing_left_tip");
        finRight = part("fin_right");
        finLeft = part("fin_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float beat = Mth.sin(t * BEAT);
        float lag = Mth.sin(t * BEAT - 0.9F);
        float wing = WING_REST + beat * 0.2F;
        float tip = lag * 0.16F;
        float sway = 1.0F;
        body.y += Mth.sin(t * BEAT + 0.6F) * 0.6F;
        body.xRot += Mth.sin(t * BEAT * 0.5F) * 0.03F;
        float pitchHead = 0.0F, jawOpen = 0.0F, sweep = 0.0F;

        if (s.phase == Step.SWELL.ordinal()) {
            float k = smooth(s.progress * 1.5F);
            wing = Mth.lerp(k, wing, WING_LIFT + beat * 0.05F);
            tip = Mth.lerp(k, tip, 0.15F);
            pitchHead = -0.18F * k;
            body.xRot -= 0.05F * k;
        } else if (s.phase == Step.SING.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.25F);
            float pulse = Mth.sin(s.phaseTime * 0.2F);
            wing = Mth.lerp(k, wing, WING_LIFT + pulse * 0.08F);
            tip = Mth.lerp(k, tip, 0.2F + pulse * 0.06F);
            pitchHead = -0.3F * k;
            jawOpen = (0.35F + 0.08F * pulse) * k;
            body.xRot -= 0.08F * k;
            sway = 0.4F;
        } else if (s.phase == Step.DIVE.ordinal()) {
            float k = envelope(s.progress, 0.15F, 0.35F);
            body.xRot += DIVE_PITCH * k;
            wing = Mth.lerp(k, wing, 2.0F * DEG);
            tip = Mth.lerp(k, tip, -0.1F);
            sweep = DIVE_SWEEP * k;
            pitchHead = 0.1F * k;
            sway = 1.6F;
        }

        wingRight.zRot = wing;
        wingLeft.zRot = -wing;
        tipRight.zRot = tip;
        tipLeft.zRot = -tip;
        wingRight.yRot = sweep;                              // tips swept back (+Z)
        wingLeft.yRot = -sweep;
        finRight.zRot += lag * 0.12F;
        finLeft.zRot -= lag * 0.12F;
        head.xRot = pitchHead;
        jaw.xRot = jawOpen;
        float tailT = t * 0.05F;
        tail.yRot = Mth.sin(tailT) * 0.1F * sway;
        tailTip.yRot = Mth.sin(tailT - 0.7F) * 0.16F * sway;
        tailFin.yRot = Mth.sin(tailT - 1.4F) * 0.22F * sway;
    }
}
