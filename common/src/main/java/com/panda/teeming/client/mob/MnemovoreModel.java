package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.Mnemovore.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Mnemovore: a low sprawling scuttle, the legs swinging in diagonal pairs and the body rocking between them, the lid of
 * its jaw breathing a crack open at rest. The gape before a bite (the lid swings up wide on its hinge, so the glyph-lined
 * roof and floor of the maw face the target, the body rears a little at the front, the legs brace wide and it quivers);
 * the bite (the lid slams shut as the body lunges forward and down); and the swallow after it took a level (the lid
 * clamped, the body bulging in slow gulps).
 */
public class MnemovoreModel extends TeemingModel {
    /** How far the lid swings up through the gape. */
    private static final float GAPE_OPEN = 50.0F * DEG;

    private final ModelPart body, lid, frontLeft, frontRight, backLeft, backRight;

    public MnemovoreModel(ModelPart root) {
        super(root, CreatureLayers.MNEMOVORE_ONLY);
        body = part("body");
        lid = part("lid");
        frontLeft = part("leg_front_left");
        frontRight = part("leg_front_right");
        backLeft = part("leg_back_left");
        backRight = part("leg_back_right");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        float stride = Mth.cos(pos * 0.9F) * speed;
        float lift = Math.max(0.0F, Mth.sin(pos * 0.9F)) * speed;
        float liftB = Math.max(0.0F, -Mth.sin(pos * 0.9F)) * speed;
        // diagonal pairs: front left with back right, front right with back left; legs swing about Y (forward and back)
        // and lift about Z (the foot leaves the ground), mirrored for the right side
        frontLeft.yRot = stride * 0.6F;
        backRight.yRot = stride * 0.6F;
        frontRight.yRot = stride * 0.6F;
        backLeft.yRot = stride * 0.6F;
        frontLeft.zRot = -lift * 0.5F;
        backRight.zRot = lift * 0.5F;
        frontRight.zRot = liftB * 0.5F;
        backLeft.zRot = -liftB * 0.5F;
        body.zRot = Mth.sin(pos * 0.9F) * 0.06F * speed;
        body.y -= Math.abs(Mth.sin(pos * 0.9F)) * 0.5F * speed;

        float open = (0.04F + Mth.sin(t * 0.08F) * 0.03F) * (1.0F - speed);   // breathing a crack open at rest
        float pitch = 0.0F;
        float brace = 0.0F;

        if (s.phase == Step.GAPE.ordinal()) {
            float k = smooth(s.progress * 2.5F);
            float quiver = Mth.sin(s.phaseTime * 2.6F) * 0.035F * k;
            open = GAPE_OPEN * k + quiver;
            pitch = -9.0F * DEG * k;                                         // rears at the front
            body.y -= 0.8F * k;
            brace = 0.15F * k;
            body.xScale = 1.0F + 0.04F * k;                                  // the hungry breath fills it a little
            body.zScale = 1.0F + 0.03F * k;
        } else if (s.phase == Step.BITE.ordinal()) {
            float slam = smooth(s.progress * 4.0F);                          // shut in the first quarter
            float lunge = envelope(s.progress, 0.15F, 0.5F);
            open = GAPE_OPEN * (1.0F - slam);
            pitch = 6.0F * DEG * lunge;                                      // lunges forward and down
            body.z -= 2.0F * lunge;
            brace = -0.2F * lunge;
        } else if (s.phase == Step.SWALLOW.ordinal()) {
            float k = envelope(s.progress, 0.1F, 0.3F);
            float gulp = Mth.sin(s.phaseTime * 0.9F) * 0.5F + 0.5F;
            open = 0.0F;
            body.xScale = 1.0F + (0.06F + 0.05F * gulp) * k;
            body.yScale = 1.0F + (0.05F + 0.06F * gulp) * k;
            body.zScale = 1.0F + 0.04F * k;
            pitch = -2.0F * DEG * gulp * k;
            brace = 0.1F * k;
        }

        lid.xRot -= open;
        body.xRot += pitch;
        // a positive brace plants the feet down and out under a raised body; a negative one lifts them
        frontLeft.zRot += brace;
        backLeft.zRot += brace;
        frontRight.zRot -= brace;
        backRight.zRot -= brace;
    }
}
