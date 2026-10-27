package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Phasewalker;
import com.panda.teeming.mob.end.Phasewalker.Step;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Phasewalker, drawn with the translucent entity render type. Every part carries two skins: a solid one (painted fully
 * opaque or fully clear: the glass is only its corner lines, so the sand inside shows) and a phased one (see-through
 * chorus lilac with star-white glints). Solid it shows the first, phased the second, and through the shimmer it
 * flickers between them, more and more often phased toward the end.
 *
 * <p>The hourglass is its clock. Solid, the top bulb's sand (synced as a stage) runs down a thin stream into the bottom
 * bulb, and when the top is empty the shimmer starts; through the shimmer and the phased spell the sand runs back up;
 * lingering in a block, the top bulb is full and the stream stops. The Inverted Walker's sand runs the other way.
 *
 * <p>Solid it walks with a stately stride, the hourglass rocking a little on its hips, the arms swinging. The wind-up
 * raises the right arm high over its head with the body twisting back; the blow brings it down across. The shimmer
 * spreads its arms and tips its head back, shivering. Phased it glides: legs trailing together, body leaning into the
 * float, arms swept back, a slow hover. Lingering, it reaches forward, feeling for open space. A cracked walker shows
 * star-white fracture lines on its bottom bulb.
 */
public class PhasewalkerModel extends TeemingModel {
    private static final String[] PARTS = {"leg_right", "leg_left", "shin_right", "shin_left", "body", "glass", "crack", "sand_top",
            "sand_low", "stream", "head", "arm_right", "arm_left", "fore_right", "fore_left"};

    private final ModelPart legRight, legLeft, shinRight, shinLeft, body, head, armRight, armLeft, foreRight, foreLeft;
    private final ModelPart crack, sandTop, sandLow, stream;
    private final List<ModelPart> solid = new ArrayList<>();
    private final List<ModelPart> ghost = new ArrayList<>();

    public PhasewalkerModel(ModelPart root) {
        super(root, CreatureLayers.PHASEWALKER_ONLY, RenderTypes::entityTranslucent);
        legRight = part("leg_right");
        legLeft = part("leg_left");
        shinRight = part("shin_right");
        shinLeft = part("shin_left");
        body = part("body");
        head = part("head");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        foreRight = part("fore_right");
        foreLeft = part("fore_left");
        crack = part("crack");
        sandTop = part("sand_top");
        sandLow = part("sand_low");
        stream = part("stream");
        for (String name : PARTS) {
            solid.add(part(name + "_s"));
            ghost.add(part(name + "_g"));
        }
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean solidStep = s.phase == Step.IDLE.ordinal() || s.phase == Step.WINDUP.ordinal() || s.phase == Step.STRIKE.ordinal();
        boolean shimmer = s.phase == Step.SHIMMER.ordinal();
        boolean phased = s.phase == Step.PHASED.ordinal();
        boolean linger = s.phase == Step.LINGER.ordinal();

        // ---- which skin: solid, phased, or flickering between them through the shimmer
        boolean showGhost = phased || linger;
        if (shimmer) showGhost = Mth.sin(s.phaseTime * 2.3F) > 1.0F - 2.0F * s.progress;
        for (ModelPart p : solid) p.visible = !showGhost;
        for (ModelPart p : ghost) p.visible = showGhost;
        crack.visible = s.flag(Phasewalker.FLAG_CRACKED);

        // ---- the sand: the clock
        float top;
        boolean flowing;
        if (solidStep) {
            top = ((s.flags >>> 24) & 0xF) / (float) Phasewalker.SAND_STEPS;
            flowing = top > 0.02F && top < 0.98F;
        } else if (shimmer) {
            top = 0.2F * s.progress;
            flowing = true;
        } else if (phased) {
            top = 0.2F + 0.8F * s.progress;
            flowing = true;
        } else {
            top = 1.0F;
            flowing = false;
        }
        float bottom = 1.0F - top;
        if ("inverted_walker".equals(s.look)) {
            float swap = top;
            top = bottom;
            bottom = swap;
        }
        fill(sandTop, top);
        fill(sandLow, bottom);
        stream.visible = flowing;
        stream.xScale = 1.0F + 0.25F * Mth.sin(t * 1.7F);
        stream.zScale = 1.0F + 0.25F * Mth.cos(t * 1.3F);

        // ---- the stride (solid) or the glide (phased)
        head.yRot += s.yRot * DEG * 0.7F;
        head.xRot += s.xRot * DEG * 0.5F;
        armRight.zRot += 0.08F;
        armLeft.zRot -= 0.08F;
        if (showGhost && !shimmer) {
            float hover = Mth.sin(t * 0.12F);
            body.y += hover * 0.6F;
            legRight.y += hover * 0.6F;
            legLeft.y += hover * 0.6F;
            legRight.xRot += 0.3F + hover * 0.04F;                            // trailing together
            legLeft.xRot += 0.35F - hover * 0.04F;
            shinRight.xRot += 0.25F;
            shinLeft.xRot += 0.3F;
            body.xRot += 0.14F;                                               // leaning into the float
            armRight.xRot += 0.55F + hover * 0.05F;                           // swept back
            armLeft.xRot += 0.55F - hover * 0.05F;
            armRight.zRot += 0.12F;
            armLeft.zRot -= 0.12F;
            head.xRot -= 0.1F;
        } else {
            float step = pos * 0.55F;
            float stride = Mth.cos(step) * 0.6F * speed;
            legRight.xRot += stride;
            legLeft.xRot -= stride;
            shinRight.xRot += Math.max(0.0F, -Mth.sin(step)) * 0.5F * speed;  // the knee bends as the leg swings forward
            shinLeft.xRot += Math.max(0.0F, Mth.sin(step)) * 0.5F * speed;
            body.y += Math.abs(Mth.sin(step)) * -0.6F * speed;
            body.zRot += Mth.cos(step) * 0.05F * speed;                       // the hourglass rocks on its hips
            armRight.xRot -= stride * 0.8F;
            armLeft.xRot += stride * 0.8F;
            body.xRot += Mth.sin(t * 0.07F) * 0.015F;                         // breathing
        }

        // ---- the steps
        if (s.phase == Step.WINDUP.ordinal()) {
            float k = envelope(s.progress, 0.45F, 0.0F);                      // up fast, held to the blow
            armRight.xRot += -2.75F * k;                                      // the right arm raised high overhead
            armRight.zRot -= 0.25F * k;
            foreRight.xRot -= 0.55F * k;                                      // claws cocked back
            body.yRot += 0.3F * k;                                            // the body twisting back into it
            body.xRot -= 0.1F * k;
            armLeft.xRot -= 0.5F * k;                                         // the other hand out, bracing
            armLeft.zRot -= 0.3F * k;
            head.xRot -= 0.15F * k;
            armRight.zRot += Mth.sin(t * 2.4F) * 0.03F * k;
        } else if (s.phase == Step.STRIKE.ordinal()) {
            float swing = smooth(Math.min(1.0F, s.progress * 3.0F));
            float k = 1.0F - smooth(s.progress);
            armRight.xRot += -2.75F + 3.0F * swing;                           // brought down across
            armRight.zRot += 0.45F * swing * k;
            foreRight.xRot -= 0.3F * k;
            body.yRot += 0.3F - 0.65F * swing * k;                            // the twist unwinds through the blow
            body.xRot += 0.2F * k;
        } else if (shimmer) {
            float k = envelope(s.progress, 0.3F, 0.0F);
            float shiver = Mth.sin(t * 3.1F) * 0.05F * k;
            armRight.zRot += 0.5F * k + shiver;                               // arms spread, palms out
            armLeft.zRot -= 0.5F * k + shiver;
            armRight.xRot -= 0.3F * k;
            armLeft.xRot -= 0.3F * k;
            head.xRot -= 0.35F * k;                                           // head tipped back
            body.zRot += shiver * 0.6F;
            body.y -= 0.4F * k;
        } else if (linger) {
            armRight.xRot -= 1.45F;                                           // reaching forward, feeling for open space
            armLeft.xRot -= 1.3F;
            armRight.zRot -= 0.15F;
            armLeft.zRot += 0.15F;
            foreRight.xRot -= 0.2F + Mth.sin(t * 0.3F) * 0.08F;
            foreLeft.xRot -= 0.2F + Mth.cos(t * 0.3F) * 0.08F;
            head.xRot += 0.25F;
        }
    }

    /** Shows a pile of sand {@code amount} full: it scales up from the bulb's floor, its pivot. */
    private static void fill(ModelPart pile, float amount) {
        pile.visible = amount > 0.03F;
        pile.yScale = Math.max(0.001F, Math.min(1.0F, amount));
    }
}
