package com.panda.teeming.client.mob;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Each model's real size in its rest pose, in blocks: width, height, depth. Hitboxes miss wings, horns and tails. */
public final class MobBounds {
    private MobBounds() {}

    private static final Map<String, float[]> BOUNDS = Map.ofEntries(
            Map.entry("thornhorn", new float[] {1.314F, 1.528F, 2.321F}),
            Map.entry("slagheart_golem", new float[] {2.300F, 2.300F, 1.545F}),
            Map.entry("levimoth", new float[] {1.719F, 0.759F, 1.014F})
    );

    public static float @Nullable [] of(String mob) {
        return BOUNDS.get(mob);
    }
}
