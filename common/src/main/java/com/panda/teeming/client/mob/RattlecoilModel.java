package com.panda.teeming.client.mob;

import com.panda.teeming.mob.overworld.Rattlecoil.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Rattlecoil, a serpent: a chain of segments from a mid-body root, {@code head <- neck1 <- neck2 <- body -> tail1 ->
 * tail2 -> tail3 -> rattle}. Every spine angle is set outright each frame (the rig's rest S only serves still images),
 * as a blend of three poses:
 * <ul>
 * <li><b>slither</b>: a sine wave of heading along the body that travels tailward as it moves, so the body throws an S;
 * each part takes the change of heading from its parent;</li>
 * <li><b>coil</b>: the warning stance: front lifted in an S with the head level, tail curled to its left and the rattle
 * raised and shaking;</li>
 * <li><b>strike</b>: neck thrown forward and low, jaw open, fangs swung down.</li>
 * </ul>
 * The head always cancels the heading the chain gives it, so it looks where the mob looks.
 */
public class RattlecoilModel extends TeemingModel {
    /** Spine order from the head (0) to the rattle (7); the body is index 3. */
    private static final int HEAD = 0, NECK1 = 1, NECK2 = 2, BODY = 3, TAIL1 = 4, TAIL2 = 5, TAIL3 = 6, RATTLE = 7;
    private static final float WAVELENGTH = 7.0F;                    // segments per wave
    private static final float IDLE_SWING = 9.0F, WALK_SWING = 30.0F;  // degrees of heading either way

    // the coil, per spine index: yaw and pitch in degrees relative to the parent
    private static final float[] COIL_YAW = {0, 10, 15, -15, 40, 45, 0, 0};
    private static final float[] COIL_PITCH = {35, 35, -70, 0, 0, 20, 65, 10};
    // the strike: pitch only, everything else straight
    private static final float[] STRIKE_PITCH = {12, 15, -55, 0, 0, 0, 0, 0};
    private static final float JAW_OPEN = 32.0F;

    private final ModelPart[] spine = new ModelPart[8];
    private final ModelPart jaw, fangs, tongue;

    public RattlecoilModel(ModelPart root) {
        super(root, CreatureLayers.RATTLECOIL_ONLY);
        String[] names = {"head", "neck1", "neck2", "body", "tail1", "tail2", "tail3", "rattle"};
        for (int i = 0; i < names.length; i++) spine[i] = part(names[i]);
        jaw = part("jaw");
        fangs = part("fangs");
        tongue = part("tongue");
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        // pose weights: slither, coil, strike (they sum to 1), and how hard the rattle shakes
        float slither = 1.0F, coil = 0.0F, strike = 0.0F, shake = 0.0F, jawOpen = 0.0F;
        boolean flicking = (t % 60.0F) < 6.0F;
        if (s.phase == Step.RATTLE.ordinal()) {
            coil = smooth(s.progress * 4.0F);                         // coiled within the first quarter
            slither = 1.0F - coil;
            shake = coil;
            flicking = (t % 16.0F) < 5.0F;
        } else if (s.phase == Step.WARY.ordinal()) {
            coil = 1.0F;
            slither = 0.0F;
            shake = 0.55F;
            flicking = (t % 16.0F) < 5.0F;
        } else if (s.phase == Step.STRIKE.ordinal()) {
            strike = smooth(s.phaseTime / 2.0F);                      // thrown out within 2 ticks
            coil = 1.0F - strike;
            slither = 0.0F;
            jawOpen = s.progress < 0.65F ? strike : 1.0F - smooth((s.progress - 0.65F) / 0.3F);   // the bite snaps shut
            flicking = false;
        } else if (s.phase == Step.RECOVER.ordinal()) {
            coil = smooth(s.progress);
            strike = 1.0F - coil;
            slither = 0.0F;
            shake = coil * 0.55F;
            flicking = false;
        } else if (s.phase == Step.CALM.ordinal()) {
            slither = smooth(s.progress);
            coil = 1.0F - slither;
            shake = coil * 0.3F;
        }

        // slither: heading along the body, travelling tailward with distance moved, drifting slowly at rest
        float swing = (IDLE_SWING + (WALK_SWING - IDLE_SWING) * speed) * slither;
        float wave = pos * 0.9F + t * 0.06F;
        float[] heading = new float[8];
        for (int i = 0; i < 8; i++) heading[i] = swing * Mth.sin(wave - Mth.TWO_PI * i / WAVELENGTH);

        for (int i = 0; i < 8; i++) {
            if (i == HEAD) continue;
            float slitherYaw = i == BODY ? heading[BODY] : heading[i] - heading[i < BODY ? i + 1 : i - 1];
            spine[i].yRot = (slitherYaw + COIL_YAW[i] * coil) * DEG;
            spine[i].xRot = (COIL_PITCH[i] * coil + STRIKE_PITCH[i] * strike) * DEG;
            spine[i].zRot = 0.0F;
        }
        // the head cancels what the chain turned it by, then looks where the mob looks
        ModelPart head = spine[HEAD];
        head.yRot = -(spine[NECK1].yRot + spine[NECK2].yRot + spine[BODY].yRot) + s.yRot * DEG * slither;
        head.xRot = (COIL_PITCH[HEAD] * coil + STRIKE_PITCH[HEAD] * strike) * DEG + s.xRot * DEG * 0.5F * slither;

        // a slow sway of the lifted neck while it watches
        spine[NECK2].yRot += Mth.sin(s.phaseTime * 0.25F) * 4.0F * DEG * coil;
        // the strike throws the neck forward out of the body
        spine[NECK2].z -= 1.5F * strike;
        spine[NECK1].z -= 1.0F * strike;

        // the raised tail and rattle shake side to side, a blur at 20 ticks a second
        float buzz = s.phaseTime * 2.4F;
        spine[TAIL3].zRot = Mth.sin(buzz) * 0.22F * shake;
        spine[RATTLE].zRot = Mth.sin(buzz + 0.7F) * 0.35F * shake;

        jaw.xRot = JAW_OPEN * jawOpen * DEG;
        fangs.xRot = 90.0F * (1.0F - jawOpen) * DEG;                  // fangs swing down from the roof of the mouth
        tongue.visible = flicking && jawOpen < 0.05F;
        tongue.xRot = -0.3F + Mth.sin(t * 2.2F) * 0.2F;                // tilted up off the ground, flickering
    }
}
