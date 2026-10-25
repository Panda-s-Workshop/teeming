package com.panda.teeming.client.mob;

import com.panda.teeming.mob.nether.PyreHerald.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pyre Herald: a hunched, scuttling walk with the brazier swaying on its pole and the folded wings twitching. The raise
 * straightens its back and lifts the brazier high over its head, the free arm up beside it, the wings spread wide and
 * beating, the face turned up; the rally thrusts the brazier up once more as its fire flares, then lowers it. When the
 * brazier is dropped it lies on its side on the ground in front of the herald, which stoops right over it, the fire
 * guttering, and lifts it again at the end.
 */
public class PyreHeraldModel extends TeemingModel {
    /** The body's rest hunch, which the brazier pole's own tilt undoes to stand upright. */
    private static final float HUNCH = 28.0F * DEG;
    /** The brazier arm thrown straight up over the head, measured from the body. */
    private static final float RAISED = -172.0F * DEG;
    /** The arm hanging straight down toward the dropped brazier, and the extra stoop over it. */
    private static final float STOOP = 35.0F * DEG;

    private final ModelPart body, head, skirt, legRight, legLeft, armRight, armLeft, staff, fire, wingRight, wingLeft;

    public PyreHeraldModel(ModelPart root) {
        super(root, CreatureLayers.PYRE_HERALD_ONLY);
        body = part("body");
        head = part("head");
        skirt = part("skirt");
        legRight = part("leg_right");
        legLeft = part("leg_left");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        staff = part("staff");
        fire = part("fire");
        wingRight = part("wing_right");
        wingLeft = part("wing_left");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float stride = Mth.cos(pos * 1.1F) * 1.0F * speed;
        legRight.xRot += stride;
        legLeft.xRot -= stride;
        body.y += Math.abs(Mth.sin(pos * 1.1F)) * -0.5F * speed;            // a quick, bobbing scuttle
        body.zRot += Mth.sin(pos * 0.55F) * 0.05F * speed;
        skirt.xRot += Mth.sin(pos * 1.1F) * 0.08F * speed + Mth.sin(t * 0.07F) * 0.02F;
        armLeft.xRot -= stride * 0.6F;
        armRight.xRot += stride * 0.15F;
        staff.zRot += Mth.sin(pos * 0.55F) * 0.08F * speed + Mth.sin(t * 0.05F) * 0.03F;   // the brazier sways on its pole
        float twitch = Mth.sin(t * 0.09F) * 0.04F + Mth.sin(pos * 1.1F) * 0.06F * speed;
        wingRight.yRot -= twitch;
        wingLeft.yRot += twitch;
        head.yRot = s.yRot * DEG;
        head.xRot += s.xRot * DEG * 0.6F;
        float flicker = 1.0F + 0.06F * Mth.sin(t * 0.9F) + 0.04F * Mth.sin(t * 1.7F + 1.0F);
        fire.xScale = flicker;
        fire.zScale = flicker;
        fire.yScale = 1.0F + 0.1F * Mth.sin(t * 1.3F);
        float lie = 0.0F;                                                  // 1 = the pole lies flat on the ground

        if (s.phase == Step.RAISE.ordinal()) {
            float k = envelope(s.progress, 0.25F, 0.0F);
            float beat = Mth.sin(s.phaseTime * 0.9F) * 0.25F * k;
            body.xRot -= 0.38F * k;                                         // it straightens up under the brazier
            armRight.xRot = Mth.lerp(k, armRight.xRot, RAISED);
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.05F);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -2.3F);                // the free hand up beside it, calling
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -0.5F);
            head.xRot -= 0.45F * k;                                         // face turned up to the fire
            wingRight.yRot -= 0.75F * k + beat;                             // wings flung wide and beating
            wingLeft.yRot += 0.75F * k + beat;
            wingRight.zRot += 0.35F * k;
            wingLeft.zRot -= 0.35F * k;
            legRight.zRot += 0.12F * k;
            legLeft.zRot -= 0.12F * k;
            staff.zRot += Mth.sin(s.phaseTime * 2.1F) * 0.04F * k;          // the pole trembles held up high
            float grow = 1.0F + 0.25F * k;
            fire.xScale *= grow;
            fire.zScale *= grow;
            fire.yScale *= 1.0F + 0.4F * k;
        } else if (s.phase == Step.RALLY.ordinal()) {
            float k = 1.0F - smooth(s.progress);
            float thrust = Mth.sin(Mth.clamp(s.progress * 2.0F, 0.0F, 1.0F) * Mth.PI) * 0.12F;
            body.xRot -= 0.38F * k + thrust;
            armRight.xRot = Mth.lerp(k, armRight.xRot, RAISED);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -1.6F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, -0.7F);
            head.xRot -= 0.3F * k;
            wingRight.yRot -= 0.9F * k;
            wingLeft.yRot += 0.9F * k;
            float flare = 1.0F + 0.6F * k;                                   // the fire bursts up, then settles
            fire.xScale *= flare;
            fire.zScale *= flare;
            fire.yScale *= 1.0F + 0.9F * k;
        } else if (s.phase == Step.DROPPED.ordinal()) {
            float k = envelope(s.progress, 0.08F, 0.2F);
            float grope = Mth.sin(s.phaseTime * 0.5F) * 0.12F * k;
            body.xRot += STOOP * k;                                          // stooped right over it
            armRight.xRot = Mth.lerp(k, armRight.xRot, -(HUNCH + STOOP) + grope);   // hanging straight down to it
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.0F);
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -(HUNCH + STOOP) - 0.4F - grope);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, 0.25F);
            head.xRot += 0.35F * k;                                          // staring down at it
            wingRight.yRot += 0.25F * k;                                     // wings drooping shut
            wingLeft.yRot -= 0.25F * k;
            wingRight.xRot += 0.3F * k;
            wingLeft.xRot += 0.3F * k;
            lie = k;
            float gutter = 1.0F - 0.45F * k + 0.08F * Mth.sin(s.phaseTime * 1.9F) * k;
            fire.xScale *= gutter;
            fire.zScale *= gutter;
            fire.yScale *= gutter;
        }
        if (s.attack > 0.0F) {                                              // a jab with the butt of the pole
            float a = Mth.sin(s.attack * Mth.PI);
            armRight.xRot -= a * 0.9F;
            body.yRot += a * 0.25F;
        }
        // the pole stands upright whatever the body and the arm do, or lies along the ground pointing forward
        float upright = -(body.xRot + armRight.xRot);
        staff.xRot = Mth.lerp(lie, upright, upright + 90.0F * DEG);
        if (lie > 0.0F) staff.y += 0.5F * lie;                              // let it rest on the floor, not float over it
    }
}
