package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.ChorusGrazer;
import com.panda.teeming.mob.end.ChorusGrazer.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Chorus Grazer: a sheep's unhurried walk, the chorus growths swaying on its back; head down to the ground and chewing
 * while it eats a chorus fruit; a shake of the bare back after the shears; and, skittish after a blink, head up, ears
 * pinned back, tail raised and a nervous tremble through the body, with a quick, high-stepping trot when it moves. The
 * fleece and its tuft hide while it is shorn (and through the shake); the growths stay. A calf is drawn at half size
 * with a bigger head.
 */
public class ChorusGrazerModel extends TeemingModel {
    private final ModelPart body, fleece, growths, head, headFleece, tail, earRight, earLeft, frontRight, frontLeft, backRight, backLeft;

    public ChorusGrazerModel(ModelPart root) {
        super(root, CreatureLayers.CHORUS_GRAZER_ONLY);
        body = part("body");
        fleece = part("fleece");
        growths = part("growths");
        head = part("head");
        headFleece = part("head_fleece");
        tail = part("tail");
        earRight = part("ear_right");
        earLeft = part("ear_left");
        frontRight = part("leg_front_right");
        frontLeft = part("leg_front_left");
        backRight = part("leg_back_right");
        backLeft = part("leg_back_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean bare = s.flag(ChorusGrazer.SHORN_FLAG) || s.phase == Step.SHEAR.ordinal();
        fleece.visible = !bare;
        headFleece.visible = !bare;

        float swing = Mth.cos(pos * 0.6662F) * 1.3F * speed;
        frontRight.xRot = swing;
        backLeft.xRot = swing;
        frontLeft.xRot = -swing;
        backRight.xRot = -swing;
        head.xRot = s.xRot * DEG + 6.0F * DEG;                         // it carries its head a little low
        head.yRot = s.yRot * DEG;
        tail.zRot = Mth.sin(t * 0.09F) * 0.12F;
        float flick = Mth.sin(t * 0.13F) * 0.08F;
        earRight.zRot -= flick;
        earLeft.zRot += flick;
        // the growths sway a little as it walks, and drift in the still air when it stands
        growths.zRot = Mth.sin(t * 0.05F) * 0.03F + Mth.cos(pos * 0.6662F) * 0.06F * speed;
        growths.xRot = Mth.sin(t * 0.037F + 1.0F) * 0.025F;

        if (s.phase == Step.EAT.ordinal()) {
            float k = envelope(s.progress, 0.15F, 0.12F);
            float chew = Mth.sin(s.phaseTime * 1.9F);
            head.xRot = (55.0F * DEG + chew * 5.0F * DEG) * k + head.xRot * (1.0F - k);
            head.yRot *= 1.0F - k;
            head.y += 3.5F * k;                                            // muzzle to the fruit on the ground
            head.z += 1.0F * k;
            body.xRot = 3.0F * DEG * k;
            earRight.zRot += 0.15F * k;
            earLeft.zRot -= 0.15F * k;
            tail.zRot = Mth.sin(s.phaseTime * 0.6F) * 0.25F * k;            // a content swish
        } else if (s.phase == Step.SHEAR.ordinal()) {
            float fade = 1.0F - smooth(s.progress);
            float shake = Mth.sin(s.phaseTime * 2.4F) * fade;
            body.zRot = shake * 0.16F;
            head.zRot = shake * 0.3F;
            head.yRot = Mth.sin(s.phaseTime * 2.4F + 1.0F) * 0.35F * fade;
            earRight.zRot += shake * 0.4F;
            earLeft.zRot += shake * 0.4F;
            tail.zRot = shake * 0.5F;
        } else if (s.phase == Step.SKITTISH.ordinal()) {
            // wary, not fierce: head up and turned to look back, ears pinned, tail raised, a tremble through the body
            float k = 1.0F - smooth(Math.max(0.0F, (s.progress - 0.85F) / 0.15F));   // eases back to calm at the end
            float tremble = Mth.sin(t * 2.7F) * 0.02F * k;
            body.zRot = tremble;
            body.y -= 0.6F * k;                                            // up on its toes
            head.xRot = head.xRot * (1.0F - k) - 32.0F * DEG * k;
            head.yRot = head.yRot * (1.0F - k) + 28.0F * DEG * k + tremble * 3.0F;
            head.y -= 2.0F * k;                                            // neck stretched up, looking back
            earRight.zRot -= 0.7F * k;                                     // swept flat back along the head
            earLeft.zRot += 0.7F * k;
            earRight.xRot = 0.5F * k;
            earLeft.xRot = 0.5F * k;
            tail.xRot = 1.2F * k;
            growths.zRot += tremble * 2.0F;
            if (speed > 0.05F) {
                // a quick, high-stepping trot when it shies
                float trot = Mth.cos(pos * 0.9F) * 1.1F * Math.min(1.0F, speed * 1.5F);
                frontRight.xRot = trot;
                backLeft.xRot = trot;
                frontLeft.xRot = -trot;
                backRight.xRot = -trot;
                body.y -= Math.abs(Mth.sin(pos * 0.9F)) * 0.8F;
            } else {
                // standing: weight back on the hind legs, front feet set, ready to go
                frontRight.xRot = -8.0F * DEG * k;
                frontLeft.xRot = -8.0F * DEG * k;
                backRight.xRot = 10.0F * DEG * k;
                backLeft.xRot = 10.0F * DEG * k;
            }
        }

        if (s.isBaby) {
            // half size, the feet still on the ground (the root pivot is 24 px above them), a big calf's head
            ModelPart whole = root();
            whole.xScale = whole.yScale = whole.zScale = 0.5F;
            whole.y += 12.0F;
            head.xScale = head.yScale = head.zScale = 1.45F;
        }
    }
}
