package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Snowroller.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Snowroller: a stubby walk; for the tell it draws head, legs and tail into the shell and settles onto the ground as a
 * ball, shivering; rolling, the whole ball spins by the ground it covers and gathers snow clumps one by one; it uncurls
 * after a roll, and lies on its back with its legs waving while it is stunned.
 *
 * <p>Everything hangs under {@code body}, whose pivot is the ball's centre, so turning {@code body} spins the ball.
 * While it rolls the walk position counts the blocks rolled since the roll started (the entity sees to that).
 */
public class SnowrollerModel extends TeemingModel {
    /** The ball's radius in blocks at scale 1: 7 px. */
    private static final float RADIUS = 7.0F / 16.0F;
    /** How far the body sinks to rest on the ground as a ball, and on its back. */
    private static final float BALL_DROP = 2.0F;
    private static final float BACK_DROP = 1.0F;
    /** Blocks rolled per snow clump: all seven by the time it is fully grown (12 blocks). */
    private static final float BLOCKS_PER_CLUMP = 12.0F / 7.0F;

    private final ModelPart body, head, crest, tail;
    private final ModelPart[] legs = new ModelPart[4];
    private final ModelPart[] snow = new ModelPart[7];

    public SnowrollerModel(ModelPart root) {
        super(root, CreatureLayers.SNOWROLLER_ONLY);
        body = part("body");
        head = part("head");
        crest = part("crest");
        tail = part("tail");
        legs[0] = part("leg_front_right");
        legs[1] = part("leg_front_left");
        legs[2] = part("leg_back_right");
        legs[3] = part("leg_back_left");
        for (int i = 0; i < snow.length; i++) snow[i] = part("snow_" + (i + 1));
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean rolling = s.phase == Step.ROLL.ordinal();
        for (int i = 0; i < snow.length; i++) snow[i].visible = rolling && pos >= (i + 0.5F) * BLOCKS_PER_CLUMP;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 1.6F);                    // fully curled a little past half way
            tuck(k);
            body.zRot += Mth.sin(s.phaseTime * 2.3F) * 0.06F * k;  // shivering, puffing snow
            body.xRot += 0.15F * k;                                 // nose down into the curl
        } else if (rolling) {
            tuck(1.0F);
            float scale = Math.max(0.5F, s.scale);
            body.xRot += pos / (RADIUS * scale);                     // the top moves forward: it spins by the ground it covers
        } else if (s.phase == Step.RECOVER.ordinal()) {
            tuck(1.0F - smooth(s.progress * 1.4F));
            walk(s, t, pos, speed);
        } else if (s.phase == Step.STUNNED.ordinal()) {
            onBack(s);
        } else {
            walk(s, t, pos, speed);
        }
    }

    /** The walk: short legs, a rocking body, the head turned toward what it watches. */
    private void walk(TeemingRenderState s, float t, float pos, float speed) {
        float swing = Mth.cos(pos * 0.9F) * 1.0F * speed;
        legs[0].xRot += swing;
        legs[3].xRot += swing;
        legs[1].xRot -= swing;
        legs[2].xRot -= swing;
        body.zRot += Mth.cos(pos * 0.9F) * 0.06F * speed;
        head.xRot += s.xRot * DEG * 0.6F;
        head.yRot += Mth.clamp(s.yRot, -50.0F, 50.0F) * DEG * 0.7F;
        tail.yRot += Mth.sin(t * 0.12F) * 0.2F;
        crest.y += Mth.sin(t * 0.07F) * 0.1F;
        if (s.attack > 0.0F) {                                        // a head-butt on a melee hit
            float a = Mth.sin(s.attack * Mth.PI);
            head.xRot += a * 0.6F;
            head.z -= a * 1.5F;
        }
    }

    /** 0 = standing, 1 = a ball: head, legs and tail drawn in, crests sunk, the body down on the ground. */
    private void tuck(float k) {
        body.y += BALL_DROP * k;
        head.z += 5.0F * k;                                          // all the way in: only the horns show at the sides
        head.y -= 1.0F * k;
        head.xRot += 0.9F * k;
        crest.y += 2.5F * k;
        tail.z -= 2.0F * k;
        for (int i = 0; i < legs.length; i++) {
            legs[i].y -= 3.5F * k;
            legs[i].xRot += (i < 2 ? 0.6F : -0.6F) * k;
        }
    }

    /** Stunned: rolled onto its back, the gold belly up, legs waving, head lolling, crests pressed into the snow. */
    private void onBack(TeemingRenderState s) {
        float k = smooth(s.phaseTime / 4.0F);                         // it flops over in the first few ticks
        body.zRot += Mth.PI * k;
        body.y += BACK_DROP * k;
        crest.y += 3.0F * k;
        body.xRot += Mth.sin(s.phaseTime * 0.3F) * 0.06F * k;        // rocking a little on its shell
        for (int i = 0; i < legs.length; i++) legs[i].xRot += Mth.sin(s.phaseTime * 0.8F + i * 1.7F) * 0.5F * k;
        head.yRot += Mth.sin(s.phaseTime * 0.4F) * 0.4F * k;
        head.xRot -= 0.3F * k;
        tail.yRot += Mth.sin(s.phaseTime * 0.9F) * 0.3F * k;
    }
}
