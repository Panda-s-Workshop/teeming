package com.panda.teeming.client.mob;

import com.panda.teeming.mob.end.AbyssAngler;
import com.panda.teeming.mob.end.AbyssAngler.Step;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Abyss Angler: a flat disc of membrane with a fin skirt, a short tail, a toothed jaw and gill flaps, and one long stalk
 * that ends in its lantern. It is built hanging: its back up against the stone. The stalk, the lantern, the line and its
 * hook are placed every frame from where the mob says they are (the rim's thickness and the line's far end, synced in
 * its flags), so the lantern the player sees sits exactly where the server casts from.
 *
 * <p>Clinging, the lantern sways half a block under the island's edge, just past the rim's face. The lure (tell): the
 * stalk swings it up over the edge, it grows and throws out four-pointed rays, the jaw opens. The reel and the hold: the
 * lantern stays over the edge, bright, with the line from it to the catch and the hook at the catch's side; the disc
 * strains and the jaw gapes. Stunned after a cut line: it hangs on the rim's face by its jaw (the whole model turned a
 * quarter up, head over the edge), the stalk limp and the lantern dark, the tail flopping. On the ground it lies belly up
 * (rolled half a turn); climbing back to a ceiling, it rolls over as it rises.
 */
public class AbyssAnglerModel extends TeemingModel {
    /** The stalk's root on the head, core-local model pixels. */
    private static final float ROOT_Y = -5.0F, ROOT_Z = -10.0F;
    private final ModelPart core, body, jaw, tail, stalk, lantern, bulb, bulbSpent, cageLit, cageSpent, rays, line, hook;

    public AbyssAnglerModel(ModelPart root) {
        super(root, CreatureLayers.ABYSS_ANGLER_ONLY);
        core = part("core");
        body = part("body");
        jaw = part("jaw");
        tail = part("tail");
        stalk = part("stalk");
        lantern = part("lantern");
        bulb = part("bulb");
        bulbSpent = part("bulb_spent");
        cageLit = part("cage_lit");
        cageSpent = part("cage_spent");
        rays = part("rays");
        line = part("line");
        hook = part("hook");
    }

    // a point as the mob faces (blocks forward, left and up from its feet) in core-local model pixels: the core's
    // pivot is the middle of the 1.0-block hitbox (model y 16), Y runs down, the mob faces -Z and +X is its left
    private static float px(float left) {
        return 16.0F * left;
    }

    private static float py(float up) {
        return 8.0F - 16.0F * up;
    }

    private static float pz(float forward) {
        return -16.0F * forward;
    }

    /** Points a part whose box runs along -Z from its pivot at core-local (x, y, z), stretched over {@code unit} pixels a pixel. */
    private static void aim(ModelPart p, float fromX, float fromY, float fromZ, float x, float y, float z, float unit) {
        float dx = x - fromX, dy = y - fromY, dz = z - fromZ;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        p.x = fromX;
        p.y = fromY;
        p.z = fromZ;
        if (len < 1.0E-4F) {
            p.zScale = 0.02F;
            return;
        }
        p.xRot = (float) Math.asin(Mth.clamp(dy / len, -1.0F, 1.0F));
        p.yRot = (float) Mth.atan2(-dx, -dz);
        p.zRot = 0.0F;
        p.zScale = Math.max(0.02F, len / unit);
    }

    @Override
    protected void animate(TeemingRenderState s, float t, float pos, float speed) {
        int phase = s.phase;
        boolean onGround = phase == Step.IDLE.ordinal() || phase == Step.CLIMB.ordinal();
        core.zRot = onGround ? Mth.PI : 0.0F;
        // the disc breathes in a slow ripple, faster as it crawls; the tail fans
        float wave = t * 0.1F + pos * 1.4F;
        body.xRot = Mth.sin(wave) * (0.02F + 0.06F * speed);
        tail.yRot = Mth.sin(wave * 0.8F) * 0.12F;
        tail.xRot = Mth.sin(wave - 1.0F) * 0.06F;

        float top = 1.0F + AbyssAngler.thickness(s.flags);   // the rim's top, in blocks above its feet
        float castF = (float) AbyssAngler.LURE_FORWARD, castU = top + (float) AbyssAngler.LURE_UP;
        // at rest the lantern sways half a block under the edge, just out past the rim's face
        float restF = 0.97F + Mth.sin(t * 0.08F) * 0.05F, restL = Mth.cos(t * 0.06F) * 0.05F, restU = top - 0.5F + Mth.sin(t * 0.11F) * 0.04F;
        float lf = restF, ll = restL, lu = restU, tipF = 0.95F, tipU = top - 0.15F, glowScale = 1.0F;
        boolean bright = false, lit = true, fishing = false;
        float jawOpen = 0.08F + Mth.sin(t * 0.05F) * 0.05F;

        if (phase == Step.TELL.ordinal()) {
            float k = smooth(s.phaseTime / 10.0F);
            lf = Mth.lerp(k, restF, castF);
            lu = Mth.lerp(k, restU, castU) + Mth.sin(s.phaseTime * 0.8F) * 0.06F * k;
            ll = restL + Mth.sin(s.phaseTime * 0.55F) * 0.18F * (1.0F - 0.6F * k);   // it swings up over the edge
            tipF = Mth.lerp(k, 0.95F, castF);
            tipU = Mth.lerp(k, top - 0.15F, castU + 0.06F);
            bright = s.phaseTime >= 4.0F;
            glowScale = 1.0F + 0.3F * k;
            jawOpen = 0.1F + 0.35F * k;
        } else if (phase == Step.REEL.ordinal() || phase == Step.HOLD.ordinal()) {
            lf = castF;
            ll = 0.0F;
            lu = castU;
            tipF = castF;
            tipU = castU + 0.06F;
            bright = true;
            fishing = s.flag(AbyssAngler.FLAG_LINE);
            glowScale = 1.25F + Mth.sin(t * 0.6F) * 0.05F;
            jawOpen = 0.6F + Mth.sin(t * 0.9F) * 0.08F;                                  // gaping as it hauls
            body.xRot = Mth.sin(t * 0.7F) * 0.05F;                                       // straining
            tail.yRot = Mth.sin(t * 0.7F) * 0.25F;
        }

        if (phase == Step.STUN.ordinal()) {
            // hung on the rim's face by its jaw: turned a quarter up, head over the edge, back to the stone
            core.xRot = -Mth.HALF_PI;
            lit = false;
            jawOpen = 0.9F;                                                              // the jaw hooked over the lip
            tail.yRot = Mth.sin(t * 0.9F) * 0.45F;                                       // flopping
            body.yRot = Mth.sin(t * 0.45F) * 0.05F;
            // the stalk hangs limp back along its belly, the dark lantern at its end
            float hx = 0.0F, hy = ROOT_Y + 3.2F, hz = ROOT_Z + 15.6F;
            aim(stalk, 0.0F, ROOT_Y, ROOT_Z, hx, hy, hz, 16.0F);
            lantern.x = hx;
            lantern.y = hy + 1.5F;
            lantern.z = hz;
        } else if (onGround) {
            // lying belly up (rolled): the stalk arches forward over its head, the lantern dim beside its face
            float hx = 0.0F, hy = 6.0F, hz = -16.0F;
            aim(stalk, 0.0F, ROOT_Y, ROOT_Z, hx, hy - 2.0F, hz, 16.0F);
            lantern.x = hx;
            lantern.y = hy;
            lantern.z = hz;
            if (phase == Step.CLIMB.ordinal()) core.zRot = Mth.PI * (1.0F - smooth(s.phaseTime / 10.0F));
        } else {
            aim(stalk, 0.0F, ROOT_Y, ROOT_Z, px(0.0F), py(tipU), pz(tipF), 16.0F);
            lantern.x = px(ll);
            lantern.y = py(lu);
            lantern.z = pz(lf);
        }
        jaw.xRot = jawOpen;
        lantern.xScale = lantern.yScale = lantern.zScale = glowScale;
        lantern.xRot = Mth.sin(t * 0.13F) * 0.08F;
        // lit or spent lantern (each look shows only its own lantern's parts)
        bulb.visible &= lit;
        cageLit.visible &= lit;
        bulbSpent.visible &= !lit;
        cageSpent.visible &= !lit;
        rays.visible = bright;
        if (bright) {
            float pulse = 0.85F + 0.2F * Mth.sin(t * 0.5F);
            rays.xScale = rays.yScale = rays.zScale = pulse / glowScale;
            rays.yRot = t * 0.05F;
        }

        // the line from the lantern to its catch's middle, and the hook just short of it
        line.visible = fishing;
        hook.visible = fishing;
        if (fishing) {
            float[] e = AbyssAngler.lineEnd(s.flags);
            float fx = lantern.x, fy = lantern.y, fz = lantern.z;
            float ex = fx + px(e[1]), ey = fy - 16.0F * e[2], ez = fz + pz(e[0]);
            aim(line, fx, fy, fz, ex, ey, ez, 1.0F);
            float len = Mth.sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2]);
            float k = len > 0.6F ? (len - 0.3F) / len : 0.5F;                            // at the catch's side, not in its middle
            hook.x = Mth.lerp(k, fx, ex);
            hook.y = Mth.lerp(k, fy, ey);
            hook.z = Mth.lerp(k, fz, ez);
            hook.xRot = line.xRot;
            hook.yRot = line.yRot;
        }
    }
}
