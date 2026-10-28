package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.RiftmineSapper;
import com.panda.teeming.mob.end.RiftmineSapper.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Riftmine Sapper: a quick, hunched scuttle, short legs pattering, the arms swinging low and the satchel jolting on its
 * back. Each mine it has out leaves that stone's socket on the satchel dark (a synced count). Plant: it reaches back over
 * its shoulder into the satchel and flicks a stone out ahead of it underarm. Tell: it rears back and raises its pick high
 * over its head, the pick shaking as the tell runs out. Jab: it drives the pick down and forward, bent right over. Thrown
 * up by a mine (a synced flag) it flails, arms and legs splayed, the satchel flung up behind it.
 */
public class RiftmineSapperModel extends TeemingModel {
    private final ModelPart body, head, armRight, armLeft, legRight, legLeft, pick, satchel, crate;
    private final ModelPart[] stones = new ModelPart[4], crateStones = new ModelPart[4];

    public RiftmineSapperModel(ModelPart root) {
        super(root, CreatureLayers.RIFTMINE_SAPPER_ONLY);
        body = part("body");
        head = part("head");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        pick = part("pick");
        satchel = part("satchel");
        crate = part("crate");
        for (int i = 0; i < 4; i++) {
            stones[i] = part("stone_" + i);
            crateStones[i] = part("crate_stone_" + i);
        }
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // each mine out darkens one socket, the last stone first
        int out = s.flags >> RiftmineSapper.FLAG_MINES_SHIFT & 7;
        for (int i = 0; i < 4; i++) {
            boolean show = i < 4 - out;
            stones[i].visible = show;
            crateStones[i].visible = show;
        }
        // the scuttle: quick short steps, the arms swinging low, the satchel jolting
        float w = pos * 0.9F;
        float stride = Mth.cos(w) * 0.9F * speed;
        legRight.xRot += stride;
        legLeft.xRot -= stride;
        armRight.xRot -= stride * 0.6F;
        armLeft.xRot += stride * 0.8F;
        float jolt = Mth.abs(Mth.sin(w)) * speed;
        body.y += jolt * 0.8F + Mth.sin(t * 0.08F) * 0.2F;
        satchel.xRot += jolt * 0.12F + Mth.sin(t * 0.08F + 1.0F) * 0.02F;
        crate.xRot += jolt * 0.1F;
        head.yRot += s.yRot * DEG * 0.7F;
        head.xRot += s.xRot * DEG * 0.5F;
        float tm = s.phaseTime;

        if (s.flag(RiftmineSapper.FLAG_FLUNG)) {
            float flail = Mth.sin(t * 1.3F);
            armRight.xRot += -2.4F + flail * 0.4F;                       // arms flung up over its head
            armRight.zRot += 0.5F;
            armLeft.xRot += -2.4F - flail * 0.4F;
            armLeft.zRot += -0.5F;
            legRight.xRot += 0.5F + flail * 0.5F;                        // legs kicking
            legLeft.xRot += -0.4F - flail * 0.5F;
            legRight.zRot += 0.25F;
            legLeft.zRot += -0.25F;
            body.xRot += -0.45F;                                         // straightened out by the throw
            head.xRot += 0.3F;
            satchel.xRot += -0.35F;
            crate.xRot += -0.3F;
        } else if (s.phase == Step.PLANT.ordinal()) {
            // reach back into the satchel (first 40 %), then the underarm flick out in front
            float back = smooth(s.progress / 0.4F);
            float flick = smooth((s.progress - 0.55F) / 0.3F);
            float settle = 1.0F - smooth((s.progress - 0.8F) / 0.2F);
            armLeft.xRot += Mth.lerp(flick, -2.8F * back, -1.2F) * settle;   // over the shoulder, then out in front
            armLeft.zRot += -0.35F * back * (1.0F - flick);
            body.zRot += 0.12F * back * (1.0F - flick);
            body.xRot += -0.1F * flick;
            head.xRot += 0.2F * flick;
        } else if (s.phase == Step.TELL.ordinal()) {
            float up = smooth(tm / 4.0F);                               // up in its first 4 ticks, then held
            float late = smooth((s.progress - 0.5F) / 0.5F);
            float shake = Mth.sin(tm * 3.1F) * 0.05F * (0.3F + late);
            armRight.xRot += -2.7F * up + shake;                        // the pick high over its head
            armRight.zRot += 0.15F * up;
            pick.xRot += 0.9F * up + shake;
            body.xRot += -0.3F * up;                                     // reared back
            head.xRot += -0.15F * up;
            armLeft.xRot += -0.5F * up;
            legLeft.xRot += -0.3F * up;
            legRight.xRot += 0.2F * up;
        } else if (s.phase == Step.JAB.ordinal()) {
            float down = smooth(tm / 2.0F);
            float back = smooth((s.progress - 0.5F) / 0.5F);
            float k = down * (1.0F - back);
            armRight.xRot += -0.9F * k;                                  // driven down and forward
            pick.xRot += -0.6F * k;
            body.xRot += 0.3F * k;
            head.xRot += 0.15F * k;
            armLeft.xRot += 0.4F * k;
            legRight.xRot += -0.3F * k;
            legLeft.xRot += 0.25F * k;
        }
    }
}
