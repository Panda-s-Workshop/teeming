package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.SoulwardRevenant.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Soulward Revenant: a heavy, measured stride with its blade held low; for the nova it drops to one knee, plants the
 * blade and spreads its free hand to gather, trembling harder as the toll rises; it rises with both arms flung wide for
 * the release, and with its breastplate cracked it hunches over the plate, one hand clutched to it, still swinging.
 */
public class SoulwardRevenantModel extends TeemingModel {
    private final ModelPart body, head, crest, armRight, armLeft, legRight, legLeft, sword, chainHang, tabard;

    public SoulwardRevenantModel(ModelPart root) {
        super(root, CreatureLayers.SOULWARD_REVENANT_ONLY);
        body = part("body");
        head = part("head");
        crest = part("crest");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        sword = part("sword");
        chainHang = part("chain_hang");
        tabard = part("tabard");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float swing = Mth.cos(pos * 0.55F);
        float stride = swing * 0.75F * speed;
        legRight.xRot = stride;
        legLeft.xRot = -stride;
        armRight.xRot = -stride * 0.3F - 0.15F;                    // the blade arm stays low and forward
        armLeft.xRot = stride * 0.6F;
        armRight.zRot = 0.06F;
        armLeft.zRot = -0.06F;
        body.y += Mth.abs(swing) * speed * 0.6F + Mth.sin(t * 0.05F) * 0.25F;
        legRight.y += Mth.abs(swing) * speed * 0.6F;
        legLeft.y += Mth.abs(swing) * speed * 0.6F;
        head.xRot = s.xRot * DEG * 0.6F;
        head.yRot = s.yRot * DEG;
        tabard.xRot = -Math.abs(stride) * 0.6F;
        crest.xRot += Mth.sin(t * 0.05F) * 0.03F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 3.0F);                      // down on one knee in the first third
            float shake = Mth.sin(s.phaseTime * 2.9F) * 0.04F * s.progress;
            float drop = 7.0F * k;
            body.y += drop;
            legRight.y += drop;
            legLeft.y += drop;
            legRight.xRot = 1.25F * k;                                // the knee down behind
            legLeft.xRot = -1.1F * k;                                 // the other foot planted ahead
            body.xRot = 0.22F * k;
            body.zRot = shake;
            armRight.xRot = -0.9F * k;                                // the blade driven point-down in front
            armRight.zRot = 0.1F;
            sword.xRot = Mth.lerp(k, sword.xRot, 0.9F - 0.22F);         // straight down, body lean and arm taken off
            armLeft.xRot = -0.35F * k;                                // the free hand spread wide to gather
            armLeft.zRot = -1.9F * k + shake * 3.0F;
            head.xRot = Mth.lerp(k, head.xRot, 0.45F - 0.85F * s.progress);   // head bowed, lifting as the toll rises
            head.yRot *= 1.0F - k;
            tabard.xRot = -1.0F * k;
        } else if (s.phase == Step.NOVA.ordinal()) {
            float k = 1.0F - smooth((s.progress - 0.5F) * 2.0F);       // flung wide, easing back in the second half
            body.xRot = -0.22F * k;
            head.xRot = -0.5F * k;
            armRight.xRot = -0.2F * k;
            armRight.zRot = 1.5F * k;
            armLeft.xRot = -0.2F * k;
            armLeft.zRot = -1.5F * k;
            legRight.zRot = 0.18F * k;
            legLeft.zRot = -0.18F * k;
            sword.xRot = Mth.lerp(k, sword.xRot, -1.57F);            // the blade out level with the arm
        } else if (s.phase == Step.SPENT.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.2F);
            float sway = Mth.sin(s.phaseTime * 0.25F) * 0.06F * k;
            body.xRot = 0.3F * k;
            body.zRot = sway;
            head.xRot = 0.35F * k;
            armLeft.xRot = -1.1F * k;                                 // a hand clutched to the cracked plate
            armLeft.zRot = 0.55F * k;
            armRight.xRot += 0.2F * k;
            sword.xRot = Mth.lerp(k, sword.xRot, -0.5F);             // the blade's point drags low
        }
        if (s.attack > 0.0F) {
            float a = Mth.sin(s.attack * Mth.PI);
            armRight.xRot -= 1.9F * a;
            armRight.zRot -= 0.3F * a;
            body.yRot = 0.25F * a;
        }
        chainHang.xRot = Mth.sin(t * 0.08F) * 0.15F - armLeft.xRot - body.xRot; // the broken chain hangs straight from the shackle
        chainHang.zRot = -armLeft.zRot;
    }
}
