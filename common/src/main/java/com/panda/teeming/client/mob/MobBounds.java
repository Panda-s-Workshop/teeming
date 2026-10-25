package com.panda.teeming.client.mob;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Each model's real size in its rest pose, in blocks: width, height, depth. Hitboxes miss wings, horns and tails. */
public final class MobBounds {
    private MobBounds() {}

    private static final Map<String, float[]> BOUNDS = Map.ofEntries(
            Map.entry("thornhorn", new float[] {1.314F, 1.528F, 2.321F}),
            Map.entry("rattlecoil", new float[] {0.545F, 0.312F, 2.413F}),
            Map.entry("snapmaw_bloom", new float[] {1.256F, 1.585F, 1.195F}),
            Map.entry("stormeel", new float[] {0.463F, 0.438F, 1.492F}),
            Map.entry("shellguard_crab", new float[] {1.735F, 0.835F, 1.555F}),
            Map.entry("slagheart_golem", new float[] {2.300F, 2.300F, 1.545F}),
            Map.entry("levimoth", new float[] {1.805F, 0.815F, 1.001F})
    );

    public static float @Nullable [] of(String mob) {
        return BOUNDS.get(mob);
    }
}
