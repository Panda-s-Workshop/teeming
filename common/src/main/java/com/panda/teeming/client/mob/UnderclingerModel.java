package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Underclinger;
import com.panda.teeming.mob.end.Underclinger.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Underclinger: a flat membrane in three jointed plates with two long hooked forelimbs. The whole model hangs from part
 * "core", pivoted at the middle of the hitbox, and rolls half a turn about Z to cling: back to the stone above, belly
 * to the void. Clinging, its plates ripple in a slow wave (faster as it crawls) and its arms lie folded along its flanks.
 * The reach (tell): both arms rise straight up the island's side, stretched to the rim's thickness, and their forearms
 * hook back over the edge, scraping. The hold: the hooks clench and tremble. The drag: the arms swing out from the
 * island. The retreat: it flinches, arms flung wide and hooks open, then folds them and scuttles back. On the ground
 * it lies the other way up; climbing back to a ceiling, it rolls over as it rises.
 */
public class UnderclingerModel extends TeemingModel {
    private static final float ARM_LENGTH = 18.0F;     // the upper arm in model pixels; a rim block is 16 more
    private final ModelPart core, front, mid, rear, tail;
    /** Right then left: upper arm, forearm, hook, hook tip. */
    private final ModelPart[] upper, fore, hook, tip;

    public UnderclingerModel(ModelPart root) {
        super(root, CreatureLayers.UNDERCLINGER_ONLY);
        core = part("core");
        front = part("front");
        mid = part("mid");
        rear = part("rear");
        tail = part("tail");
        upper = new ModelPart[] {part("arm_right"), part("arm_left")};
        fore = new ModelPart[] {part("forearm_right"), part("forearm_left")};
        hook = new ModelPart[] {part("hook_right"), part("hook_left")};
        tip = new ModelPart[] {part("hook_tip_right"), part("hook_tip_left")};
    }

    /** Both arms, from rest toward a pose by {@code k}: upper arm pitch and splay, forearm pitch, hook and tip curl (radians). */
    private void arms(float k, float up, float splay, float bend, float curl, float tipCurl) {
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? -1.0F : 1.0F;
            upper[i].xRot = Mth.lerp(k, upper[i].xRot, up);
            upper[i].yRot = Mth.lerp(k, upper[i].yRot, sx * splay);
            fore[i].xRot = Mth.lerp(k, fore[i].xRot, bend);
            fore[i].yRot = Mth.lerp(k, fore[i].yRot, 0.0F);
            hook[i].xRot = Mth.lerp(k, hook[i].xRot, curl);
            tip[i].xRot = Mth.lerp(k, tip[i].xRot, tipCurl);
        }
    }

    /** The upper arms stretched so the forearms clear a rim {@code thick} blocks thick. */
    private void stretch(float k, int thick) {
        float s = 1.0F + k * (thick - 1) * 16.0F / ARM_LENGTH;
        upper[0].zScale = s;
        upper[1].zScale = s;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean clinging = s.phase != Step.IDLE.ordinal() && s.phase != Step.CLIMB.ordinal();
        core.zRot = clinging ? Mth.PI : 0.0F;
        // the membrane breathes in a slow wave down its plates, a fast one while it crawls
        float wave = t * 0.12F + pos * 1.6F;
        float amp = 0.05F + 0.2F * speed;
        front.xRot = Mth.sin(wave) * amp;
        rear.xRot = Mth.sin(wave - 1.3F) * amp;
        tail.xRot = Mth.sin(wave - 2.6F) * amp * 1.4F;
        mid.yRot = Mth.sin(wave * 0.5F) * 0.03F;
        // folded arms twitch now and then
        float twitch = (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.07F)), 12) * 0.12F;
        upper[0].yRot -= twitch;
        upper[1].yRot += twitch;
        int thick = Underclinger.thickness(s.flags);

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 6.0F);
            arms(k, -Mth.HALF_PI, 5.0F * DEG, Mth.HALF_PI, Mth.HALF_PI, 40.0F * DEG);   // up the island's side, hooked over the rim
            stretch(k, thick);
            float scrape = Mth.sin(s.phaseTime * 2.4F) * 0.05F * k;                    // scraping at the edge
            upper[0].xRot += scrape;
            upper[1].xRot -= scrape;
            front.xRot = -0.12F * k;                                                     // the head plate lifts toward the rim
        } else if (s.phase == Step.HOLD.ordinal()) {
            arms(1.0F, -Mth.HALF_PI, 5.0F * DEG, Mth.HALF_PI, 105.0F * DEG, 60.0F * DEG); // hooks clenched
            stretch(1.0F, thick);
            float tremble = Mth.sin(s.phaseTime * 1.7F) * 0.05F;
            fore[0].xRot += tremble;
            fore[1].xRot -= tremble;
            front.xRot = -0.15F;
            mid.xRot = Mth.sin(s.phaseTime * 0.9F) * 0.04F;                             // straining
        } else if (s.phase == Step.PULL.ordinal()) {
            float k = smooth(s.progress);
            arms(1.0F, -Mth.HALF_PI - 60.0F * DEG * k, 5.0F * DEG, Mth.HALF_PI - 30.0F * DEG * k, 105.0F * DEG, 60.0F * DEG);  // swung out from the island
            stretch(1.0F, thick);
            front.xRot = -0.15F - 0.2F * k;
        } else if (s.phase == Step.RETREAT.ordinal()) {
            float recoil = 1.0F - smooth((s.phaseTime - Underclinger.RECOIL_TICKS * 0.6F) / (Underclinger.RECOIL_TICKS * 0.8F));
            float k = smooth(s.phaseTime / 2.0F) * recoil;
            arms(k, 15.0F * DEG, 70.0F * DEG, 25.0F * DEG, 10.0F * DEG, 10.0F * DEG);   // flung wide, hooks open
            core.xRot = -0.25F * k;                                                     // it jerks back from the rim
            front.xRot += -0.3F * k;
            tail.xRot += 0.35F * k;
            if (recoil < 1.0F) {                                                        // then scuttles, fast
                float scuttle = s.phaseTime * 0.9F;
                front.xRot += Mth.sin(scuttle) * 0.15F * (1.0F - recoil);
                rear.xRot += Mth.sin(scuttle - 1.3F) * 0.15F * (1.0F - recoil);
            }
        } else if (s.phase == Step.CLIMB.ordinal()) {
            float k = smooth(s.phaseTime / 10.0F);
            core.zRot = Mth.PI * k;                                                     // rolls over as it rises
            arms(0.6F, -Mth.HALF_PI, 20.0F * DEG, 0.6F, 0.4F, 0.4F);                   // reaching up for the ceiling
        }
    }
}
