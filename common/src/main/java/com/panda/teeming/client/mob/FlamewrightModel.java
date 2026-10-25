package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.Flamewright.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Flamewright: a slow, gliding walk under its robe, the ember crown flickering. The wind-up raises both hands in front
 * of its chest, palms out, tracing the sigil between them (the sigil only shows then) as the crown flares up; the
 * spiral sweeps its right hand round as it turns; the ring flings both arms wide; the cross thrusts its arms forward
 * and out; a stagger throws it back with its hood knocked aside and its arms flung out.
 */
public class FlamewrightModel extends TeemingModel {
    /** Hands raised before the chest for the sigil: forward and up, turned in toward each other. */
    private static final float RAISE = -95.0F * DEG, TURN_IN = 18.0F * DEG;

    private final ModelPart body, skirt, head, crown, sigil, armRight, armLeft, handRight, handLeft, footRight, footLeft,
            tailRight, tailLeft;

    public FlamewrightModel(ModelPart root) {
        super(root, CreatureLayers.FLAMEWRIGHT_ONLY);
        body = part("body");
        skirt = part("skirt");
        head = part("head");
        crown = part("crown");
        sigil = part("sigil");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        handRight = part("hand_right");
        handLeft = part("hand_left");
        footRight = part("foot_right");
        footLeft = part("foot_left");
        tailRight = part("tail_right");
        tailLeft = part("tail_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float step = Mth.cos(pos * 0.6662F);
        footRight.xRot += step * 0.9F * speed;
        footLeft.xRot -= step * 0.9F * speed;
        footRight.z -= step * 1.2F * speed;
        footLeft.z += step * 1.2F * speed;
        skirt.xRot += Mth.sin(pos * 0.6662F) * 0.05F * speed + Mth.sin(t * 0.06F) * 0.02F;
        body.zRot += step * 0.03F * speed;
        float sway = Mth.sin(t * 0.05F) * 0.05F;
        armRight.xRot += sway + step * 0.3F * speed;
        armLeft.xRot += -sway - step * 0.3F * speed;
        head.xRot = s.xRot * DEG;
        head.yRot = s.yRot * DEG;
        tailRight.xRot += 0.15F * speed + Mth.sin(t * 0.08F) * 0.04F;
        tailLeft.xRot += 0.15F * speed + Mth.sin(t * 0.08F + 1.3F) * 0.04F;
        crown.zRot += Mth.sin(t * 0.11F) * 0.03F;                              // the flames lick from side to side
        sigil.visible = s.phase == Step.WINDUP.ordinal();

        if (s.phase == Step.WINDUP.ordinal()) {
            float k = envelope(s.progress, 0.25F, 0.0F);
            float trace = Mth.sin(s.phaseTime * 0.6F) * 0.12F * k;              // the hands trace the sigil's ring
            armRight.xRot = Mth.lerp(k, armRight.xRot, RAISE + trace);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, RAISE - trace);
            armRight.yRot = Mth.lerp(k, armRight.yRot, -TURN_IN);
            armLeft.yRot = Mth.lerp(k, armLeft.yRot, TURN_IN);
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.15F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -0.15F);
            handRight.xRot -= 0.5F * k;                                         // palms turned out to the sigil
            handLeft.xRot -= 0.5F * k;
            body.xRot -= 0.08F * k;                                             // drawn up tall
            head.xRot = Mth.lerp(k, head.xRot, -0.1F);
            float grow = 0.35F + 0.65F * smooth(s.progress * 1.6F);           // the sigil burns into being
            sigil.xScale = grow;
            sigil.yScale = grow;
            sigil.zRot = s.phaseTime * 0.08F;                                   // and turns slowly as it is drawn
            crown.xScale = 1.0F + 0.12F * k;                                    // the crown flares
            crown.yScale = 1.0F + 0.12F * k;
        } else if (s.phase == Step.SPIRAL.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.15F);
            float sweep = Mth.sin(s.progress * Mth.TWO_PI) * 0.9F;               // the right hand sweeps round
            armRight.xRot = Mth.lerp(k, armRight.xRot, -1.45F);
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.6F + sweep * 0.5F);
            armRight.yRot = Mth.lerp(k, armRight.yRot, sweep);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -0.5F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -0.5F);
            body.yRot += sweep * 0.25F * k;
            skirt.yRot -= sweep * 0.12F * k;
        } else if (s.phase == Step.RING.ordinal()) {
            float k = envelope(s.progress, 0.12F, 0.35F);
            armRight.xRot = Mth.lerp(k, armRight.xRot, -0.4F);                  // both arms flung wide
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -0.4F);
            armRight.zRot = Mth.lerp(k, armRight.zRot, 1.35F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -1.35F);
            body.xRot -= 0.15F * k;
            head.xRot = Mth.lerp(k, head.xRot, -0.3F);
            skirt.xRot += 0.05F * k;
        } else if (s.phase == Step.CROSS.ordinal()) {
            float k = envelope(s.progress, 0.12F, 0.3F);
            float beat = Mth.abs(Mth.sin(s.phaseTime * Mth.PI / 4.0F));          // a thrust for each fireball of an arm
            armRight.xRot = Mth.lerp(k, armRight.xRot, -1.5F - 0.15F * beat);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -0.2F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -1.45F);                   // one arm forward, one to the side
            body.xRot += 0.1F * beat * k;
        } else if (s.phase == Step.STAGGER.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            float reel = Mth.sin(s.phaseTime * 0.9F) * k;
            body.xRot -= 0.3F * k;                                              // thrown back
            body.zRot += 0.12F * reel;
            head.zRot += 0.35F * k;                                             // hood knocked aside
            head.xRot -= 0.25F * k;
            armRight.xRot += 0.5F * k;                                          // arms flung back and wide
            armLeft.xRot += 0.4F * k;
            armRight.zRot += 0.8F * k;
            armLeft.zRot -= 0.8F * k;
            crown.xScale = 1.0F - 0.25F * k;                                    // the crown gutters
            crown.yScale = 1.0F - 0.25F * k;
            skirt.xRot += 0.15F * k;
        }
    }
}
