package com.panda.teeming.client.mob;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.end.Gatecaster;
import com.panda.teeming.mob.end.Gatecaster.Gate;
import com.panda.teeming.mob.end.Gatecaster.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Gatecaster: a slow glide under the long robe, hunched over the ring of violet light it holds upright between its
 * hands; the ring turns slowly, its inner ring the other way. Tell: its fingertips glow, it draws itself up and lifts
 * the ring, which swells and spins faster and faster; the exit rift (a big ring of the same light) opens out where the
 * caster's synced flags put it, turned to face the target, and its inner ring lights once the aim locks. Bolt: it
 * thrusts the ring forward and the gate flares and shrinks shut. Turned: dragged through its own gate, it is thrown
 * back, arms flung wide, the ring wobbling small.
 */
public class GatecasterModel extends TeemingModel {
    private static final float PX = 16.0F;
    /** The held ring turns this fast at rest, in radians a tick; its inner ring the other way. */
    private static final float SPIN = 0.03F;
    /** The gate's rim and inner ring radii, in pixels (the rim matches the gate's radius in blocks). */
    private static final float RIM = (float) (Gatecaster.RING_RADIUS * PX), CORE = RIM * 0.72F;
    private static final int BARS = 12;
    /** The gate in a pose with no real gate behind it (the harness poses each phase on a still mob): ahead and to its left. */
    private static final float POSE_FORWARD = 1.6F, POSE_LEFT = 1.7F, POSE_UP = 1.2F;
    private static final float TRACK = Gatecaster.TRACK_TICKS;

    private final ModelPart body, skirt, head, held, ring, ringCore, armRight, armLeft, sparkRight, sparkLeft, gate, gateRim, gateCore;
    private final ModelPart[] rimBars = new ModelPart[BARS], coreBars = new ModelPart[BARS];

    public GatecasterModel(ModelPart root) {
        super(root, CreatureLayers.GATECASTER_ONLY);
        body = part("body");
        skirt = part("skirt");
        head = part("head");
        held = part("held");
        ring = part("ring");
        ringCore = part("ring_core");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        sparkRight = part("spark_right");
        sparkLeft = part("spark_left");
        gate = part("gate");
        gateRim = part("gate_rim");
        gateCore = part("gate_core");
        for (int i = 0; i < BARS; i++) {
            rimBars[i] = part("gate_rim_" + i);
            coreBars[i] = part("gate_core_" + i);
        }
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        sparkRight.visible = false;
        sparkLeft.visible = false;
        gate.visible = false;
        gateCore.visible = false;
        ringCore.visible = true;

        // the glide: the robe sways, the hood bobs, the held ring rides with the breath
        float w = pos * 0.6F;
        skirt.xRot += Mth.sin(w) * 0.06F * speed + Mth.sin(t * 0.06F) * 0.02F;
        skirt.zRot += Mth.cos(w) * 0.04F * speed;
        body.zRot += Mth.cos(w) * 0.03F * speed;
        float breath = Mth.sin(t * 0.08F);
        held.y += breath * 0.3F;
        armRight.xRot += breath * 0.02F;
        armLeft.xRot += breath * 0.02F;
        head.yRot += s.yRot * DEG * 0.7F;
        head.xRot += s.xRot * DEG * 0.7F;
        float spin = t * SPIN;
        float tm = s.phaseTime;
        int flags = s.flags;
        boolean live = Gate.live(flags);
        // a phase held longer than its real length is a pose with nothing behind it (the harness poses each phase on a
        // still mob): it shows a gate where one would be, and the bolt's first moments rather than their end (a Rift
        // Charm's longer tell is still a real one)
        float length = s.progress > 0.001F ? tm / s.progress : 0.0F;

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(tm / 4.0F);
            sparkRight.visible = true;
            sparkLeft.visible = true;
            float pulse = 0.8F + 0.5F * k + 0.2F * Mth.sin(tm * 1.7F);
            sparkRight.xScale = sparkRight.yScale = sparkRight.zScale = pulse;
            sparkLeft.xScale = sparkLeft.yScale = sparkLeft.zScale = pulse;
            body.xRot -= 0.12F * k;                                               // it draws itself up
            held.xRot += 0.12F * k;                                               // and keeps the ring upright
            held.y -= 1.5F * k;                                                   // lifting it
            armRight.xRot -= 0.22F * k;
            armLeft.xRot -= 0.22F * k;
            float swell = 1.0F + 0.25F * k + 0.04F * Mth.sin(tm * 2.5F);
            held.xScale = held.yScale = held.zScale = swell;
            spin += 0.004F * tm * tm;                                             // and it spins up
            float open = smooth(tm / 5.0F);
            if (live || length > Gatecaster.TELL_TICKS + Charms.RIFT_EARLY + 2) showGate(flags, live, 0.2F + 0.8F * open, t, tm >= TRACK);
        } else if (s.phase == Step.BOLT.ordinal()) {
            boolean posed = length > Gatecaster.BOLT_TICKS + 2;
            float u = posed ? 0.2F : Mth.clamp(tm / Gatecaster.BOLT_TICKS, 0.0F, 1.0F);
            float thrust = 1.0F - smooth((u - 0.3F) / 0.7F);
            float jab = smooth(u / 0.2F) * thrust;
            armRight.xRot -= 0.45F * jab;                                         // the ring thrust forward
            armLeft.xRot -= 0.45F * jab;
            held.z -= 3.0F * jab;
            held.y -= 1.0F * jab;
            float flash = 1.0F + 0.35F * (1.0F - smooth(u / 0.4F));
            held.xScale = held.yScale = held.zScale = flash;
            body.xRot += 0.08F * jab;
            if (live || posed) {
                float shut = 1.0F - smooth(u);
                showGate(flags, live, (1.0F + 0.2F * (1.0F - smooth(u / 0.25F))) * Math.max(0.0F, shut), t, true);
            }
        } else if (s.phase == Step.TURNED.ordinal()) {
            float k = envelope(s.progress, 0.12F, 0.45F);
            body.xRot -= 0.65F * k;                                               // thrown back
            head.xRot -= 0.45F * k;
            head.zRot += 0.25F * k;
            armRight.zRot += 1.0F * k;                                            // arms flung wide
            armLeft.zRot -= 1.0F * k;
            armRight.xRot += 0.5F * k;
            armLeft.xRot += 0.5F * k;
            skirt.xRot += 0.15F * k;
            float shrink = 1.0F - 0.4F * k;
            held.xScale = held.yScale = held.zScale = shrink;
            held.zRot += 0.4F * Mth.sin(tm * 1.3F) * k;                           // the ring wobbles
            held.xRot += 0.3F * Mth.cos(tm * 1.1F) * k;
            ringCore.visible = ((int) tm & 2) == 0 || k < 0.3F;                   // and its inner light gutters
        }
        ring.zRot += spin;
        ringCore.zRot -= spin * 1.6F;
    }

    /**
     * Shows the gate: where the flags put it (or, in a pose with no gate behind it, ahead and to the left of the caster,
     * facing ahead), opened out to {@code open} of its size, its rim turning and its inner ring lit when {@code lit}.
     */
    private void showGate(int flags, boolean live, float open, float t, boolean lit) {
        if (open <= 0.02F) return;
        float forward = live ? (float) Gate.forward(flags) : POSE_FORWARD;
        float left = live ? (float) Gate.left(flags) : POSE_LEFT;
        float up = live ? (float) Gate.up(flags) : POSE_UP;
        float yaw = live ? (float) Gate.yaw(flags) * DEG : 0.0F;
        gate.visible = true;
        gate.x = left * PX;
        gate.y = 24.0F - up * PX;
        gate.z = -forward * PX;
        gate.yRot = yaw;
        gate.xRot = 0.0F;
        gate.zRot = 0.0F;
        float flicker = 1.0F + 0.04F * Mth.sin(t * 3.1F);
        gate.xScale = gate.yScale = gate.zScale = open * flicker;
        lay(rimBars, RIM);
        gateRim.zRot = t * 0.08F;
        gateCore.visible = lit;
        if (lit) {
            lay(coreBars, CORE);
            gateCore.zRot = -t * 0.12F;
        }
    }

    /** Sets a ring's bars out round its middle, {@code radius} pixels out, each along its own tangent. */
    private static void lay(ModelPart[] bars, float radius) {
        for (int i = 0; i < bars.length; i++) {
            float a = i * (Mth.TWO_PI / bars.length);
            bars[i].x = radius * Mth.sin(a);
            bars[i].y = -radius * Mth.cos(a);
        }
    }
}
