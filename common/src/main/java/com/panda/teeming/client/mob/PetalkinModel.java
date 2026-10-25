package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Petalkin.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Petalkin: a quick toddling walk with its blossom bobbing and the petals breathing open and shut. Just after a blink
 * it re-forms: crouched small with its arms up and its blossom folded shut into a bud, which opens again as the half
 * second ends. Its peck is a sharp nod of the whole head, blossom and all.
 */
public class PetalkinModel extends TeemingModel {
    private final ModelPart body, head, crown, legRight, legLeft, armRight, armLeft;
    private final ModelPart[] blades = new ModelPart[5];

    public PetalkinModel(ModelPart root) {
        super(root, CreatureLayers.PETALKIN_ONLY);
        body = part("body");
        head = part("head");
        crown = part("crown");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        for (int i = 0; i < blades.length; i++) blades[i] = part("petal_" + i + "_blade");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float stride = Mth.cos(pos * 1.1F) * 1.2F * speed;
        legRight.xRot += stride;
        legLeft.xRot -= stride;
        armRight.xRot -= stride * 0.7F;
        armLeft.xRot += stride * 0.7F;
        float bob = Math.abs(Mth.sin(pos * 1.1F)) * -0.7F * speed;
        body.y += bob;
        head.y += bob;
        head.xRot += s.xRot * DEG;
        head.yRot += s.yRot * DEG;
        crown.zRot += Mth.sin(t * 0.07F) * 0.05F + Mth.sin(pos * 0.55F) * 0.08F * speed;
        crown.xRot += Mth.sin(t * 0.05F + 1.3F) * 0.04F;
        for (int i = 0; i < blades.length; i++) {
            blades[i].xRot += Mth.sin(t * 0.09F + i * 1.26F) * 0.06F;               // the petals breathe
        }

        if (s.phase == Step.BLINK.ordinal()) {
            // folded tight for most of the half second, opening over the last part
            float k = 1.0F - smooth((s.progress - 0.45F) / 0.55F);
            float shiver = Mth.sin(s.phaseTime * 2.2F) * 0.06F * k;
            for (ModelPart blade : blades) blade.xRot -= 1.45F * k;              // the blossom closes into a bud
            crown.yRot += s.phaseTime * 0.35F * k;                               // and turns as it gathers itself
            body.y += 1.2F * k;
            head.y += 1.2F * k;
            head.xRot -= 0.25F * k;                                              // face tipped up
            armRight.zRot += 1.4F * k + shiver;                                  // arms flung up
            armLeft.zRot -= 1.4F * k + shiver;
            legRight.xRot -= 0.35F * k;
            legLeft.xRot += 0.35F * k;
        }
        if (s.attack > 0.0F) {                                                   // the peck: a sharp nod
            float a = Mth.sin(s.attack * Mth.PI);
            head.xRot += a * 0.85F;
            head.z -= a * 1.2F;
            body.xRot += a * 0.18F;
            armRight.xRot -= a * 0.6F;
            armLeft.xRot -= a * 0.6F;
        }
    }
}
