package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.FacetDoppel;
import com.panda.teeming.mob.end.FacetDoppel.Held;
import com.panda.teeming.mob.end.FacetDoppel.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Facet Doppel: a stiff, even walk like a player's, the arms swinging opposite the legs; with a copy in hand the right
 * arm is held a little forward, on guard, the copy swung out of the fist pointing forward and up. At rest the head turns
 * after what it watches and the facets tilt a hair, catching the light.
 *
 * <p>The flicker: both arms rise to shoulder height, palms flat to the front as if pressed to a pane of glass, the head
 * cocked; the outer facets blink out and back three times a second and the whole figure jitters sideways. The wind-up
 * raises the copy high over the right shoulder with the body leaning back; the blow brings it down in front. Shattered,
 * the chest diamond and the shoulder facets are gone, leaving dark scars.
 */
public class FacetDoppelModel extends TeemingModel {
    /** The copies are built pointing up the forearm from the fist; held, they swing out this far, forward and up. */
    private static final float HOLD = 0.95F;
    /** ...and lean out to its right, so a copy held up in front reads clear of the chest. */
    private static final float LEAN = 0.44F;

    private final ModelPart body, head, legRight, legLeft, armRight, armLeft, handRight, handLeft;
    private final ModelPart[] facets;
    private final ModelPart[] scars;
    private final ModelPart[] copies;

    public FacetDoppelModel(ModelPart root) {
        super(root, CreatureLayers.FACET_DOPPEL_ONLY);
        body = part("body");
        head = part("head");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
        facets = new ModelPart[] {part("facet_chest"), part("facet_shoulder_right"), part("facet_shoulder_left")};
        scars = new ModelPart[] {part("scar_chest"), part("scar_shoulder_right"), part("scar_shoulder_left")};
        // in the order of FacetDoppel.Held, from FIST on (NONE shows nothing)
        copies = new ModelPart[] {part("copy_fist"), part("copy_sword"), part("copy_axe"), part("copy_pickaxe"), part("copy_shovel"),
                part("copy_hoe"), part("copy_trident"), part("copy_mace"), part("copy_spear"), part("copy_rod"), part("copy_chunk")};
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        Held held = Held.from(s.flags);
        boolean armed = held != Held.NONE && held != Held.FIST;
        boolean broken = s.flag(FacetDoppel.FLAG_SHATTERED);
        for (int i = 0; i < copies.length; i++) {
            copies[i].visible = held.ordinal() == i + 1;
            if (i > 0) {
                copies[i].xRot += HOLD;
                copies[i].zRot -= LEAN;
            }
        }
        for (ModelPart f : facets) f.visible = !broken;
        for (ModelPart sc : scars) sc.visible = broken;

        // ---- the walk: even and stiff, like the player it copies
        float step = pos * 0.66F;
        float stride = Mth.cos(step) * 0.8F * speed;
        legRight.xRot += stride;
        legLeft.xRot -= stride;
        armRight.xRot -= stride * (armed ? 0.45F : 0.8F);
        armLeft.xRot += stride * 0.8F;
        if (armed) armRight.xRot -= 0.35F;                                    // on guard, the copy forward
        body.yRot += Mth.cos(step) * 0.05F * speed;
        head.yRot += s.yRot * DEG;
        head.xRot += s.xRot * DEG;
        float glint = Mth.sin(t * 0.05F) * 0.03F;                             // the facets tilt a hair, catching the light
        facets[0].zRot += glint;
        facets[1].zRot += glint * 0.6F;
        facets[2].zRot -= glint * 0.6F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = envelope(s.progress, 0.2F, 0.1F);
            armRight.xRot += -1.45F * k;                                      // both palms flat to the front, at the shoulders
            armLeft.xRot += -1.45F * k;
            armRight.zRot -= 0.12F * k;
            armLeft.zRot += 0.12F * k;
            handRight.xRot -= 0.35F * k;
            handLeft.xRot -= 0.35F * k;
            head.zRot += 0.22F * k;                                           // cocked, studying what it sees
            head.xRot += 0.1F * k;
            // the flicker: outer facets blink out and back three times a second, and the figure jitters sideways
            boolean out = (int) s.phaseTime % 7 < 2;
            if (out) for (ModelPart f : facets) f.visible = false;
            body.x += Mth.sin(s.phaseTime * 4.1F) * 0.35F * k;
            float pulse = 1.0F + 0.1F * k * Mth.sin(s.phaseTime * 1.3F);
            for (ModelPart f : facets) {
                f.xScale *= pulse;
                f.yScale *= pulse;
                f.zScale *= pulse;
            }
        } else if (s.phase == Step.WINDUP.ordinal()) {
            float k = envelope(s.progress, 0.45F, 0.0F);                      // up fast, held to the blow
            armRight.xRot += -2.75F * k;                                      // the copy raised high over the right shoulder
            armRight.zRot += 0.35F * k;
            handRight.xRot -= 0.3F * k;
            armLeft.xRot += -0.6F * k;                                        // the left arm out front for balance
            armLeft.zRot += 0.2F * k;
            body.xRot -= 0.18F * k;                                           // leaning back into it
            body.yRot -= 0.25F * k;
            head.xRot -= 0.1F * k;
            float shiver = Mth.sin(t * 2.2F) * 0.03F * k;
            armRight.zRot += shiver;
        } else if (s.phase == Step.STRIKE.ordinal()) {
            float swing = smooth(Math.min(1.0F, s.progress * 3.0F));          // the blow lands at once, then eases off
            float k = 1.0F - smooth(s.progress);
            armRight.xRot += -2.75F + 1.6F * swing - 0.4F * k;                // brought down to point at the foe, held there
            armRight.zRot += 0.35F * (1.0F - swing);
            handRight.xRot += 0.3F * k;
            armLeft.xRot += 0.5F * k;                                         // the left arm thrown back
            body.xRot += 0.3F * k;                                            // lunging into it
            body.yRot += 0.35F * k;
            head.xRot += 0.15F * k;
            legRight.xRot -= 0.35F * k;                                       // a step into the blow
            legLeft.xRot += 0.25F * k;
        }
    }
}
