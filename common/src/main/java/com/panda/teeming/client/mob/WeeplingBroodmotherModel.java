package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.WeeplingBroodmother;
import com.panda.teeming.mob.nether.WeeplingBroodmother.Form;
import com.panda.teeming.mob.nether.WeeplingBroodmother.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Weepling Broodmother, one model for the mother and her brood (the synced flags say which form to show). The mother
 * crawls on four short legs, her swollen abdomen dragging and swaying behind and breathing slowly. Tell: she squats,
 * legs splayed, head down and mandibles wide, while the abdomen swells and throbs. Lay: the abdomen squeezes, its tip
 * lifting as the sacs drop. A sac sits in its puddle and pulses, faster and harder as it ripens. A weepling scuttles
 * on stubby legs with a little hop, sobbing (its body heaves), and lunges forward to bite.
 */
public class WeeplingBroodmotherModel extends TeemingModel {
    private static final float GROUND = 24.0F;

    private final ModelPart body, head, mandibleRight, mandibleLeft, abdomen, tip;
    private final ModelPart[] legs = new ModelPart[4];
    private final float[] legSplay = new float[4];
    private final ModelPart weepling, sac, sacBody;
    private final ModelPart[] wlegs = new ModelPart[4];

    public WeeplingBroodmotherModel(ModelPart root) {
        super(root, CreatureLayers.WEEPLING_BROODMOTHER_ONLY);
        body = part("body");
        head = part("head");
        mandibleRight = part("mandible_right");
        mandibleLeft = part("mandible_left");
        abdomen = part("abdomen");
        tip = part("tip");
        String[] legNames = {"leg_fl", "leg_fr", "leg_ml", "leg_mr"};
        for (int i = 0; i < 4; i++) {
            legs[i] = part(legNames[i]);
            legSplay[i] = Math.signum(legs[i].zRot);
        }
        weepling = part("weepling");
        sac = part("sac");
        sacBody = part("sac_body");
        String[] wlegNames = {"wleg_fl", "wleg_fr", "wleg_bl", "wleg_br"};
        for (int i = 0; i < 4; i++) wlegs[i] = part(wlegNames[i]);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        Form form = WeeplingBroodmother.formOf(s.flags);
        body.visible = form == Form.MOTHER;
        weepling.visible = form == Form.WEEPLING;
        sac.visible = form == Form.SAC;
        switch (form) {
            case MOTHER -> mother(s, t, pos, speed);
            case SAC -> sac(s, t);
            case WEEPLING -> weepling(s, t, pos, speed);
        }
    }

    private void mother(TeemingRenderState s, float t, float pos, float speed) {
        // the crawl: diagonal pairs of legs step together, the body bobs and the abdomen drags and sways behind
        float w = pos * 0.9F;
        float step = Mth.cos(w);
        legs[0].xRot += step * 0.6F * speed;
        legs[3].xRot += step * 0.6F * speed;
        legs[1].xRot -= step * 0.6F * speed;
        legs[2].xRot -= step * 0.6F * speed;
        body.y += Math.abs(Mth.sin(w)) * 0.6F * speed;
        abdomen.yRot += Mth.sin(w * 0.5F) * 0.1F * speed;
        abdomen.zRot += Mth.sin(w * 0.5F + 1.0F) * 0.04F * speed;
        float breath = 1.0F + Mth.sin(t * 0.08F) * 0.025F;
        scale(abdomen, breath);
        head.yRot = Mth.clamp(s.yRot * DEG, -0.5F, 0.5F);
        head.xRot = Mth.clamp(s.xRot * DEG * 0.5F, -0.3F, 0.3F);
        float jaw = 0.1F + Mth.sin(t * 0.15F) * 0.05F;                    // mandibles twitching
        if (s.attack > 0.0F) {                                             // the bite: a lunge with the mandibles flung wide
            float a = Mth.sin(s.attack * Mth.PI);
            head.z -= 2.0F * a;
            head.xRot += 0.25F * a;
            jaw += 0.6F * a;
        }

        if (s.phase == Step.TELL.ordinal()) {
            float k = envelope(s.progress, 0.25F, 0.08F);
            float throb = Mth.sin(s.phaseTime * 0.9F);
            body.y += 2.5F * k;                                             // squatting low
            for (int i = 0; i < 4; i++) legs[i].zRot += legSplay[i] * 0.45F * k;
            head.xRot += 0.3F * k;
            jaw += 0.45F * k + throb * 0.08F * k;
            abdomen.xRot -= 0.06F * k;
            abdomen.xScale = breath + 0.18F * k + 0.08F * k * throb;        // swelling and throbbing
            abdomen.yScale = breath + 0.2F * k + 0.09F * k * throb;
            abdomen.zScale = breath + 0.08F * k + 0.04F * k * throb;
            tip.xRot -= 0.2F * k;
        } else if (s.phase == Step.LAY.ordinal()) {
            float k = envelope(s.progress, 0.2F, 0.4F);
            body.y += 1.5F * k;
            for (int i = 0; i < 4; i++) legs[i].zRot += legSplay[i] * 0.3F * k;
            abdomen.xScale = breath + 0.06F * k;                            // the squeeze
            abdomen.yScale = breath - 0.08F * k;
            abdomen.zScale = breath - 0.12F * k;
            tip.xRot -= 0.6F * k;                                           // the tip lifts as the sacs drop
            tip.zScale = 1.0F + 0.25F * k;
            jaw += 0.3F * k;
            head.xRot -= 0.15F * k;
        }
        mandibleRight.yRot += jaw;
        mandibleLeft.yRot -= jaw;
    }

    private void sac(TeemingRenderState s, float t) {
        sac.x = 0.0F;
        sac.y = GROUND;
        sac.z = 0.0F;
        float ripe = WeeplingBroodmother.ripeness(s.flags);
        float beat = t * (0.25F + 0.65F * ripe);
        float amp = 0.05F + 0.1F * ripe;
        sacBody.xScale = 1.0F + amp * Mth.sin(beat);
        sacBody.zScale = 1.0F + amp * Mth.sin(beat);
        sacBody.yScale = 1.0F + amp * 0.7F * Mth.sin(beat + 0.6F);
        sacBody.zRot = Mth.sin(beat * 0.5F) * 0.08F * ripe;                 // something curled inside stirs
        sacBody.xRot = Mth.cos(beat * 0.37F) * 0.06F * ripe;
    }

    private void weepling(TeemingRenderState s, float t, float pos, float speed) {
        weepling.x = 0.0F;
        weepling.y = GROUND;
        weepling.z = 0.0F;
        float w = pos * 1.6F;
        float step = Mth.cos(w);
        wlegs[0].xRot += step * 0.9F * speed;
        wlegs[3].xRot += step * 0.9F * speed;
        wlegs[1].xRot -= step * 0.9F * speed;
        wlegs[2].xRot -= step * 0.9F * speed;
        weepling.y -= Math.abs(Mth.sin(w)) * 1.2F * speed;                  // a little hop each step
        float sob = Mth.sin(t * 0.35F);
        weepling.yScale = 1.0F + 0.05F * sob;                               // sobbing: the body heaves
        weepling.xScale = 1.0F - 0.03F * sob;
        weepling.zScale = 1.0F - 0.03F * sob;
        weepling.yRot = Mth.clamp(s.yRot * DEG, -0.6F, 0.6F) * 0.5F;
        weepling.xRot = 0.08F * speed;
        if (s.attack > 0.0F) {                                             // the bite: a lunge forward and down
            float a = Mth.sin(s.attack * Mth.PI);
            weepling.z -= 2.0F * a;
            weepling.xRot += 0.35F * a;
        }
    }

    private static void scale(ModelPart p, float k) {
        p.xScale = k;
        p.yScale = k;
        p.zScale = k;
    }
}
