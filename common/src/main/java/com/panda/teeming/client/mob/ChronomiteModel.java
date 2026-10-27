package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Chronomite;
import com.panda.teeming.mob.end.Chronomite.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Chronomite: a heavy plod on four stubby legs, diagonal pairs together, the shell rocking a little with each step and
 * the small head peering about under its rim. Its dial's hands turn (the long one jumps once a second, with the tick)
 * and the wind-up key on its back turns slowly. The tell rears its front up, the head craned forward and the mandibles
 * spread wide, the key spinning; the bite snaps the front down and the head out, mandibles shut. The shatter shudders
 * the whole body with the head pulled in. From the broken stage on the top of the dome, the dial and the key are gone
 * and the jagged bowl with its heap of sand shows instead.
 */
public class ChronomiteModel extends TeemingModel {
    private final ModelPart body, shell, head, mandibleLeft, mandibleRight, legFrontLeft, legFrontRight, legBackLeft, legBackRight;
    private final ModelPart domeTop, dial, hourHand, minuteHand, key, broken;

    public ChronomiteModel(ModelPart root) {
        super(root, CreatureLayers.CHRONOMITE_ONLY);
        body = part("body");
        shell = part("shell");
        head = part("head");
        mandibleLeft = part("mandible_left");
        mandibleRight = part("mandible_right");
        legFrontLeft = part("leg_front_left");
        legFrontRight = part("leg_front_right");
        legBackLeft = part("leg_back_left");
        legBackRight = part("leg_back_right");
        domeTop = part("dome_top");
        dial = part("dial");
        hourHand = part("hand_hour");
        minuteHand = part("hand_minute");
        key = part("key");
        broken = part("broken");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        boolean shattered = s.stage >= Chronomite.STAGE_BROKEN;
        domeTop.visible = !shattered;
        dial.visible = !shattered;
        key.visible = !shattered;
        broken.visible = shattered;

        // ---- the plod: diagonal pairs together, the body rocking over them
        float step = pos * 0.9F;
        float stride = Mth.cos(step) * 0.8F * speed;
        legFrontLeft.xRot += stride;
        legBackRight.xRot += stride;
        legFrontRight.xRot -= stride;
        legBackLeft.xRot -= stride;
        body.y -= Math.abs(Mth.sin(step)) * 0.5F * speed;
        body.zRot += Mth.sin(step) * 0.05F * speed;
        shell.zRot += Mth.sin(step - 0.6F) * 0.03F * speed;              // the heavy shell lags a little behind
        head.yRot += s.yRot * DEG * 0.5F;
        head.xRot += s.xRot * DEG * 0.4F;
        head.z += Mth.sin(t * 0.07F) * 0.25F;                            // peering out from under the rim

        // ---- the clockwork: the hour hand creeps, the minute hand jumps with each tick, the key turns
        float keyTurn = t * 0.05F;
        float spin = 0.0F;
        mandibleLeft.yRot += Mth.sin(t * 0.13F) * 0.06F;
        mandibleRight.yRot -= Mth.sin(t * 0.13F) * 0.06F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = envelope(s.progress, 0.5F, 0.0F);                   // up fast, held to the bite
            body.xRot -= 0.38F * k;                                       // the front reared up
            head.xRot -= 0.3F * k;
            head.z -= 1.2F * k;                                           // craned out
            mandibleLeft.yRot -= 0.7F * k;                                // spread wide
            mandibleRight.yRot += 0.7F * k;
            legFrontLeft.xRot -= 0.35F * k;                               // front feet off the ground, braced back
            legFrontRight.xRot -= 0.35F * k;
            legBackLeft.xRot += 0.2F * k;
            legBackRight.xRot += 0.2F * k;
            float shiver = Mth.sin(t * 2.4F) * 0.02F * k;
            body.zRot += shiver;
            spin = 12.0F * k;
        } else if (s.phase == Step.BITE.ordinal()) {
            float k = 1.0F - smooth(s.progress);                          // the snap lands at once, then eases back
            body.xRot += 0.22F * k;                                       // front snapped down
            head.z -= 2.2F * k;                                           // the head out
            head.xRot += 0.15F * k;
            mandibleLeft.yRot += 0.25F * k;                               // shut
            mandibleRight.yRot -= 0.25F * k;
        } else if (s.phase == Step.SHATTER.ordinal()) {
            float k = 1.0F - s.progress;
            float shudder = Mth.sin(s.phaseTime * 3.1F) * 0.09F * k;
            body.zRot += shudder;
            body.xRot += Mth.sin(s.phaseTime * 2.3F + 1.0F) * 0.05F * k;
            body.y += 0.6F * k;                                           // sagging under the burst
            head.z += 1.5F * k;                                           // the head pulled in
            mandibleLeft.yRot -= 0.4F * k;
            mandibleRight.yRot += 0.4F * k;
            legFrontLeft.zRot -= 0.15F * k;                               // splayed
            legFrontRight.zRot += 0.15F * k;
            legBackLeft.zRot -= 0.15F * k;
            legBackRight.zRot += 0.15F * k;
        }

        keyTurn += spin * 0.05F * s.phaseTime;
        key.yRot += keyTurn;
        hourHand.zRot -= t * 0.01F + spin * 0.05F * s.phaseTime;
        minuteHand.zRot -= (float) Math.floor(t / 20.0F) * ((float) Math.PI / 6.0F) + spin * 0.2F * s.phaseTime;
    }
}
