package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.ShardlingColossus;
import com.panda.teeming.mob.end.ShardlingColossus.Form;
import com.panda.teeming.mob.end.ShardlingColossus.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Shardling Colossus, one model for the colossus and its halves (the synced flags say which to show). The colossus
 * walks with a heavy stride, arms swinging low. Tell: the halves part a hair at the seam, which cracks and glows, and
 * both arms go up over its head, shaking harder as it runs. Slam: the arms come down in front, the body bowed over
 * them. A half stands alone on its one leg, hopping and rocking as it goes, its raw split face glowing; it staggers
 * outward out of the split, and leans toward its twin, seam blinking, as they walk together. Rejoined, the halves are
 * drawn shut and the arms spread; a fused colossus's seam is sealed dark.
 */
public class ShardlingColossusModel extends TeemingModel {
    /** How far a half alone is moved across, so it stands over the middle of its hitbox. */
    private static final float HALF_SHIFT = 5.0F;

    private final ModelPart halfLeft, halfRight, torsoLeft, torsoRight, legLeft, legRight, armLeft, armRight;
    private final ModelPart headLeft, headRight, glowLeft, glowRight, headGlowLeft, headGlowRight;
    private final ModelPart scarLeft, scarRight, headScarLeft, headScarRight;

    public ShardlingColossusModel(ModelPart root) {
        super(root, CreatureLayers.SHARDLING_COLOSSUS_ONLY);
        halfLeft = part("half_left");
        halfRight = part("half_right");
        torsoLeft = part("torso_left");
        torsoRight = part("torso_right");
        legLeft = part("leg_left");
        legRight = part("leg_right");
        armLeft = part("arm_left");
        armRight = part("arm_right");
        headLeft = part("head_left");
        headRight = part("head_right");
        glowLeft = part("glow_left");
        glowRight = part("glow_right");
        headGlowLeft = part("head_glow_left");
        headGlowRight = part("head_glow_right");
        scarLeft = part("scar_left");
        scarRight = part("scar_right");
        headScarLeft = part("head_scar_left");
        headScarRight = part("head_scar_right");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        Form form = ShardlingColossus.formOf(s.flags);
        boolean whole = form == Form.WHOLE;
        halfLeft.visible = form != Form.RIGHT;
        halfRight.visible = form != Form.LEFT;
        if (!whole) {
            halfLeft.x -= HALF_SHIFT;                                           // a half alone stands over its middle
            halfRight.x += HALF_SHIFT;
        }

        boolean lit = s.phase == Step.TELL.ordinal() || s.phase == Step.SPLIT.ordinal() || s.phase == Step.FUSE.ordinal()
                || (s.phase == Step.SLAM.ordinal() && s.progress < 0.5F)
                || (s.phase == Step.REGROUP.ordinal() && ((int) (s.phaseTime / 5.0F)) % 2 == 0);
        boolean sealed = whole && ShardlingColossus.fusedIn(s.flags) && !lit;
        glowLeft.visible = glowRight.visible = headGlowLeft.visible = headGlowRight.visible = lit;
        scarLeft.visible = scarRight.visible = headScarLeft.visible = headScarRight.visible = sealed;

        float look = s.yRot * DEG * 0.6F, pitch = s.xRot * DEG * 0.5F;
        headLeft.yRot = headRight.yRot = look;
        headLeft.xRot = headRight.xRot = pitch;
        float breathe = Mth.sin(t * 0.05F) * 0.25F;
        torsoLeft.y += breathe;
        torsoRight.y += breathe;

        if (whole) walk(pos, speed);
        else hop(form == Form.LEFT ? 1.0F : -1.0F, pos, speed);

        if (s.phase == Step.TELL.ordinal()) {
            float k = smooth(s.progress * 2.5F);
            float shake = Mth.sin(s.phaseTime * 2.4F) * 0.035F * (0.3F + s.progress);
            arms(-2.9F * k, 0.25F * k);
            torsos(-0.15F * k, shake);
            if (whole) open(0.5F * k);                                           // the seam cracks open a hair
            heads(-0.25F * k);
        } else if (s.phase == Step.SLAM.ordinal()) {
            float down = smooth(s.progress * 5.0F);
            float back = smooth((s.progress - 0.5F) * 2.0F);                     // the arms come back up after
            arms(Mth.lerp(down, -2.9F, -0.75F) * (1.0F - back), 0.1F * (1.0F - back));
            torsos(0.4F * down * (1.0F - back), 0.0F);
            heads(0.2F * down * (1.0F - back));
        } else if (s.phase == Step.SPLIT.ordinal()) {
            float k = envelope(s.progress, 0.12F, 0.5F);
            float side = form == Form.RIGHT ? -1.0F : 1.0F;
            ModelPart half = form == Form.RIGHT ? halfRight : halfLeft;
            ModelPart arm = form == Form.RIGHT ? armRight : armLeft;
            half.zRot = side * 0.18F * k;                                        // reeling outward from the split
            half.x += side * 1.0F * k;
            arm.zRot = -side * 0.55F * k;                                         // its arm flung out
            arm.xRot = -0.4F * k;
        } else if (s.phase == Step.REGROUP.ordinal() && !whole) {
            float side = form == Form.RIGHT ? -1.0F : 1.0F;
            ModelPart half = form == Form.RIGHT ? halfRight : halfLeft;
            half.zRot += -side * 0.14F;                                          // leaning in toward its twin
        } else if (s.phase == Step.FUSE.ordinal()) {
            float open = 1.0F - smooth(s.progress * 1.6F);                       // drawn shut
            open(2.0F * open);
            armLeft.zRot = -0.7F * open - 0.05F;
            armRight.zRot = 0.7F * open + 0.05F;
            heads(-0.3F * open);
        }
    }

    /** The colossus's stride: legs swinging, arms swinging the other way and low, a slow sway. */
    private void walk(float pos, float speed) {
        float stride = Mth.cos(pos * 0.45F) * 0.8F * speed;
        legLeft.xRot = stride;
        legRight.xRot = -stride;
        armLeft.xRot = -stride * 0.7F;
        armRight.xRot = stride * 0.7F;
        armLeft.zRot = -0.06F;
        armRight.zRot = 0.06F;
        float sway = Mth.cos(pos * 0.45F) * 0.05F * speed;
        torsoLeft.zRot = sway;
        torsoRight.zRot = sway;
    }

    /** A half's hop on its one leg: the leg swings, the half bobs and rocks, a little lean toward its open side. */
    private void hop(float side, float pos, float speed) {
        ModelPart half = side > 0 ? halfLeft : halfRight;
        ModelPart leg = side > 0 ? legLeft : legRight;
        ModelPart arm = side > 0 ? armLeft : armRight;
        float step = Mth.cos(pos * 0.6F);
        leg.xRot = step * 0.5F * speed;
        arm.xRot = -step * 0.6F * speed;
        arm.zRot = -side * 0.12F;
        half.y -= Math.abs(Mth.sin(pos * 0.6F)) * 1.6F * speed;
        half.zRot = -side * 0.05F + Mth.sin(pos * 0.6F) * 0.09F * speed;
    }

    private void arms(float x, float out) {
        armLeft.xRot = x;
        armRight.xRot = x;
        armLeft.zRot = -out;
        armRight.zRot = out;
    }

    private void torsos(float x, float z) {
        torsoLeft.xRot = x;
        torsoRight.xRot = x;
        torsoLeft.zRot += z;
        torsoRight.zRot += z;
    }

    private void heads(float x) {
        headLeft.xRot += x;
        headRight.xRot += x;
    }

    /** Opens the seam: each half moved {@code gap} pixels out to its own side. */
    private void open(float gap) {
        halfLeft.x += gap;
        halfRight.x -= gap;
    }
}
