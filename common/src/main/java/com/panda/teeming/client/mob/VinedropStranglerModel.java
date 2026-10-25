package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.VinedropStrangler.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Vinedrop Strangler: on the ground a knuckle-walk on its long arms and four root tendrils, lashing with an arm. Hanging,
 * the whole model rolls upside down about the middle of its hitbox (part "core"), tendrils splayed up against the
 * leaves, head and arms dangling and swaying. In the tell it shivers, spreads its arms wide reaching down and opens its
 * maw; dropping, its arms are flung wide like a net. Wrapped round a victim it rolls upright behind them, up on their
 * back, arms round their chest and tendrils coiled round their legs, squeezing once a second. Knocked loose it lies on
 * its back, limp, head lolling; after a miss it crouches and shakes itself; climbing, it goes hand over hand up a vine.
 */
public class VinedropStranglerModel extends TeemingModel {
    private final ModelPart core, body, head, jaw, armRight, armLeft, foreRight, foreLeft, rope;
    /** Front right, front left, back right, back left, and their tips. */
    private final ModelPart[] tendrils, tips;

    public VinedropStranglerModel(ModelPart root) {
        super(root, CreatureLayers.VINEDROP_STRANGLER_ONLY);
        core = part("core");
        body = part("body");
        head = part("head");
        jaw = part("jaw");
        armRight = part("arm_right");
        armLeft = part("arm_left");
        foreRight = part("forearm_right");
        foreLeft = part("forearm_left");
        rope = part("rope");
        tendrils = new ModelPart[] {part("tendril_fr"), part("tendril_fl"), part("tendril_br"), part("tendril_bl")};
        tips = new ModelPart[] {part("tendril_fr_tip"), part("tendril_fl_tip"), part("tendril_br_tip"), part("tendril_bl_tip")};
    }

    /** Adds to every tendril's splay (out from under it) and every tip's curl. */
    private void tendrils(float splay, float curl) {
        for (ModelPart p : tendrils) p.xRot += splay;
        for (ModelPart p : tips) p.xRot += curl;
    }

    /** Both arms: pitch (down is 0, up past the head is PI, more is forward), roll outward from the body, the forearms' bend. */
    private void arms(float pitch, float out, float bend) {
        armRight.xRot = pitch;
        armLeft.xRot = pitch;
        armRight.zRot = out;
        armLeft.zRot = -out;
        foreRight.xRot = bend;
        foreLeft.xRot = bend;
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        rope.visible = false;
        // the knuckle-walk: diagonal tendril pairs step together, the arms swing in turn, the body bobs
        float step = pos * 0.9F;
        tendrils[0].xRot += Mth.sin(step) * 0.45F * speed;
        tendrils[3].xRot += Mth.sin(step) * 0.45F * speed;
        tendrils[1].xRot += Mth.sin(step + Mth.PI) * 0.45F * speed;
        tendrils[2].xRot += Mth.sin(step + Mth.PI) * 0.45F * speed;
        armRight.xRot += Mth.cos(step) * 0.6F * speed;
        armLeft.xRot -= Mth.cos(step) * 0.6F * speed;
        body.y -= Math.abs(Mth.sin(step)) * 0.6F * speed;
        // breathing: the arms drift and the maw works a little
        armRight.zRot += Mth.sin(t * 0.08F) * 0.05F;
        armLeft.zRot -= Mth.sin(t * 0.08F) * 0.05F;
        head.yRot = s.yRot * DEG * 0.6F;
        head.xRot = s.xRot * DEG * 0.5F;
        jaw.xRot = 0.05F + 0.15F * (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.05F)), 8);

        if (s.phase == Step.HANG.ordinal()) {
            float k = smooth(s.phaseTime / 6.0F);
            core.zRot = Mth.PI * k;                                          // rolled upside down under the leaves
            core.xRot = Mth.sin(t * 0.06F) * 0.06F;                          // a slow pendulum sway
            float sway = Mth.sin(t * 0.07F) * 0.12F;
            armRight.xRot = Mth.lerp(k, armRight.xRot, Mth.PI * 1.06F + sway);   // dangling below its head, a little forward
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, Mth.PI * 1.06F - sway);
            armRight.zRot = Mth.lerp(k, armRight.zRot, -0.18F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, 0.18F);
            foreRight.xRot += 0.25F * k;
            foreLeft.xRot += 0.25F * k;
            tendrils(0.35F * k, -0.5F * k);                                  // splayed against the leaves, tips hooked over
            head.xRot -= 0.25F * k;                                          // watching the ground
        } else if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 4.0F);
            core.zRot = Mth.PI;
            core.xRot = Mth.sin(s.phaseTime * 2.6F) * 0.07F;               // a shiver that shakes the leaves
            arms(Mth.PI * (1.06F + 0.1F * k), -0.18F - 0.75F * k, 0.25F + 0.3F * k);    // spread wide, reaching down at its prey
            tendrils(0.35F - 0.15F * k, -0.5F - 0.3F * k);                 // gripping hard
            head.xRot = -0.55F * k;                                          // staring at where it will land
            head.yRot = 0.0F;
            jaw.xRot = 0.15F + 0.45F * k;
        } else if (s.phase == Step.DROP.ordinal()) {
            core.zRot = Mth.PI;
            arms(Mth.PI * 1.2F, -1.15F, -0.35F);                             // flung wide and forward like a net
            tendrils(-0.3F, 0.2F);                                           // trailing above it
            head.xRot = -0.6F;
            head.yRot = 0.0F;
            jaw.xRot = 0.65F;
        } else if (s.phase == Step.WRAP.ordinal()) {
            float k = smooth(s.phaseTime / 4.0F);
            core.zRot = Mth.PI * (1.0F - k);                                 // rolls upright round its victim
            float c = s.phaseTime % 20.0F;
            float squeeze = c < 5.0F ? Mth.sin(c / 5.0F * Mth.PI) : 0.0F;   // tightening once a second
            body.z += 4.5F * k;                                              // behind its victim
            body.y -= 3.0F * k;                                              // up on their back
            body.xRot = 0.3F * k;                                            // leaning over their shoulder
            armRight.xRot = Mth.lerp(k, armRight.xRot, -1.45F);              // arms forward round the chest
            armLeft.xRot = Mth.lerp(k, armLeft.xRot, -1.45F);
            armRight.zRot = Mth.lerp(k, armRight.zRot, 0.0F);
            armLeft.zRot = Mth.lerp(k, armLeft.zRot, 0.0F);
            armRight.yRot = (-0.35F - 0.1F * squeeze) * k;
            armLeft.yRot = (0.35F + 0.1F * squeeze) * k;
            foreRight.zRot = (-1.2F - 0.25F * squeeze) * k;                 // curled across the front
            foreLeft.zRot = (1.2F + 0.25F * squeeze) * k;
            tendrils(0.8F * k, (-1.2F - 0.2F * squeeze) * k);               // coiled round the legs
            head.xRot = 0.35F * k;
            head.yRot = 0.0F;
            jaw.xRot = (0.3F + 0.25F * squeeze) * k;
        } else if (s.phase == Step.STUNNED.ordinal()) {
            float k = smooth(s.phaseTime / 4.0F);
            core.xRot = -1.45F * k;                                          // on its back
            core.y += 6.5F * k;
            arms(0.3F * k, 1.3F * k, 0.3F * k);                              // limp, flung out to the sides
            armRight.zRot += 0.17F * (1.0F - k);                             // (the rest roll, while it falls back)
            armLeft.zRot -= 0.17F * (1.0F - k);
            tendrils(0.5F * k, 0.4F * k);
            head.zRot = Mth.sin(s.phaseTime * 0.3F) * 0.4F * k;              // dazed
            head.yRot = Mth.sin(s.phaseTime * 0.45F) * 0.3F * k;
            jaw.xRot = 0.45F * k;
        } else if (s.phase == Step.RECOVER.ordinal()) {
            float e = envelope(s.progress, 0.15F, 0.4F);
            body.y += 2.5F * e;                                              // crouched, bracing
            tendrils(0.4F * e, 0.2F * e);
            armRight.xRot += -0.5F * e;
            armLeft.xRot += -0.5F * e;
            core.yRot = Mth.sin(s.phaseTime * 1.3F) * 0.15F * (1.0F - s.progress);   // it shakes itself
            head.xRot += 0.3F * e;
        } else if (s.phase == Step.CLIMB.ordinal()) {
            rope.visible = true;
            rope.yScale = 40.0F;                                             // a vine 2.5 blocks up to the leaves
            float hand = Mth.sin(s.phaseTime * 0.6F);
            arms(-2.9F, 0.3F, -0.3F);                                         // up over its head on the vine
            armRight.xRot += hand * 0.25F;                                   // hand over hand
            armLeft.xRot -= hand * 0.25F;
            tendrils(-0.25F, 0.0F);
            for (int i = 0; i < tips.length; i++) tips[i].xRot += Mth.sin(t * 0.2F + i) * 0.2F;   // dangling loose
            body.xRot = -0.1F;
            head.xRot = -0.4F;                                               // looking up
            head.yRot = 0.0F;
        }
        if (s.attack > 0.0F) {                                               // a lash with its right arm
            float a = Mth.sin(s.attack * Mth.PI);
            armRight.xRot -= 2.0F * a;
            foreRight.xRot -= 0.6F * a;
            jaw.xRot += 0.3F * a;
        }
    }
}
