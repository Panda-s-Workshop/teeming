package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Siphonjelly.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Siphonjelly, drawn see-through: a slow rhythm of the bell pulsing (it squeezes narrow and tall, then relaxes wide)
 * while the whole jelly bobs and its tendrils sway, each a beat behind the last and trailing as it drifts; the flare
 * (tell: the bell spread wide and flat, the margin flung open, the tendrils fanned out and lit, quivering); the sting
 * (the bell snapping shut and every tendril whipping forward); and starved (the bell slumped flat, the tendrils and
 * oral arms hanging limp and still).
 */
public class SiphonjellyModel extends TeemingModel {
    private static final int TENDRILS = 8;

    private final ModelPart bell, rim, arms;
    private final ModelPart[] arm = new ModelPart[4];
    private final ModelPart[] tendril = new ModelPart[TENDRILS], tip = new ModelPart[TENDRILS];
    private final ModelPart[] lit = new ModelPart[TENDRILS], litTip = new ModelPart[TENDRILS];
    /** Each tendril's outward direction round the bell (x, z), from where it hangs. */
    private final float[] outX = new float[TENDRILS], outZ = new float[TENDRILS];

    public SiphonjellyModel(ModelPart root) {
        super(root, CreatureLayers.SIPHONJELLY_ONLY, RenderTypes::entityTranslucent);
        bell = part("bell");
        rim = part("rim");
        arms = part("arms");
        for (int i = 0; i < arm.length; i++) arm[i] = part("arm_" + i);
        for (int i = 0; i < TENDRILS; i++) {
            tendril[i] = part("tendril_" + i);
            tip[i] = part("tendril_" + i + "_tip");
            lit[i] = part("lit_" + i);
            litTip[i] = part("lit_" + i + "_tip");
            float len = Mth.sqrt(tendril[i].x * tendril[i].x + tendril[i].z * tendril[i].z);
            outX[i] = len > 0 ? tendril[i].x / len : 0.0F;
            outZ[i] = len > 0 ? tendril[i].z / len : 0.0F;
        }
    }

    private static void scale(ModelPart p, float across, float tall) {
        p.xScale *= across;
        p.zScale *= across;
        p.yScale *= tall;
    }

    /** Tilts tendril {@code i} so its tip swings out from the bell (positive) or in under it. */
    private void spread(int i, ModelPart p, float a) {
        p.xRot += a * outZ[i];
        p.zRot -= a * outX[i];
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean flare = s.phase == Step.TELL.ordinal();
        boolean sting = s.phase == Step.STING.ordinal();
        boolean starved = s.phase == Step.STARVED.ordinal();
        float limp = starved ? envelope(s.progress, 0.08F, 0.12F) : 0.0F;
        float live = 1.0F - limp;

        // the pulse: a squeeze (narrow, tall) and a long relax; the bob rides on it
        float beat = t * 0.14F;
        float squeeze = Math.max(0.0F, Mth.sin(beat)) * live;
        float relax = Math.max(0.0F, -Mth.sin(beat)) * live;
        scale(bell, 1.0F - 0.07F * squeeze + 0.04F * relax, 1.0F + 0.06F * squeeze - 0.03F * relax);
        scale(rim, 1.0F - 0.1F * squeeze + 0.06F * relax, 1.0F);
        float bob = Mth.sin(beat - 0.9F) * 0.7F * live + 1.5F * limp;          // starved, it slumps lower
        bell.y += bob;
        arms.y += bob;
        for (ModelPart p : tendril) p.y += bob;

        // tendrils and oral arms: a slow sway, each a beat behind the last, trailing behind as it drifts
        float trail = Math.min(1.0F, speed * 2.5F) * 0.35F * live;
        for (int i = 0; i < TENDRILS; i++) {
            float w = t * 0.09F + i * 0.8F;
            float amp = 0.12F * live + 0.03F;
            tendril[i].xRot += Mth.sin(w) * amp + trail;
            tendril[i].zRot += Mth.cos(w * 0.8F) * amp * 0.7F;
            tip[i].xRot += Mth.sin(w - 0.9F) * amp * 1.4F + trail * 0.6F;
            tip[i].zRot += Mth.cos(w * 0.8F - 0.9F) * amp;
            spread(i, tendril[i], 0.06F * squeeze);
        }
        for (int i = 0; i < arm.length; i++) {
            arm[i].xRot += Mth.sin(t * 0.07F + i * 1.6F) * 0.08F * live + 0.12F * limp;
            arm[i].zRot += Mth.cos(t * 0.06F + i) * 0.05F * live;
        }

        boolean lights = flare || (sting && s.progress < 0.6F);
        for (int i = 0; i < TENDRILS; i++) {
            lit[i].visible = lights;
            litTip[i].visible = lights;
        }

        if (flare) {
            // the bell spread wide and flat, the margin flung open, the tendrils fanned out and quivering
            float k = envelope(s.progress, 0.35F, 0.0F);
            scale(bell, 1.0F + 0.12F * k, 1.0F - 0.15F * k);
            scale(rim, 1.0F + 0.1F * k, 1.0F + 0.3F * k);                  // the margin is the bell's child: it widens with it
            bell.y += 0.8F * k;
            float quiver = Mth.sin(s.phaseTime * 2.6F) * 0.05F * k;
            for (int i = 0; i < TENDRILS; i++) {
                spread(i, tendril[i], 0.55F * k + quiver);
                spread(i, tip[i], -0.25F * k);                              // the tips curl back in, a cage round you
            }
            for (ModelPart a : arm) a.xRot -= 0.2F * k;
        } else if (sting) {
            // the bell snaps shut and every tendril whips forward
            float d = 1.0F - smooth(s.progress);
            scale(bell, 1.0F - 0.2F * d, 1.0F + 0.16F * d);
            scale(rim, 1.0F - 0.25F * d, 1.0F);
            bell.y -= 1.0F * d;
            for (int i = 0; i < TENDRILS; i++) {
                tendril[i].xRot -= 0.75F * d;
                tip[i].xRot -= 0.45F * d;
                spread(i, tendril[i], -0.15F * d);
            }
            arms.xRot -= 0.4F * d;
        } else if (starved) {
            // slumped flat and limp: the tendrils hang straight, the arms droop
            scale(bell, 1.0F + 0.14F * limp, 1.0F - 0.3F * limp);
            scale(rim, 1.0F + 0.08F * limp, 1.0F - 0.3F * limp);
            bell.y += 1.2F * limp;
            for (int i = 0; i < TENDRILS; i++) spread(i, tendril[i], -0.08F * limp);
        }
    }
}
