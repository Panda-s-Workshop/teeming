package com.panda.teeming.client.mob;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Each model's real size in its rest pose, in blocks: width, height, depth. Hitboxes miss wings, horns and tails. */
public final class MobBounds {
    private MobBounds() {}

    private static final Map<String, float[]> BOUNDS = Map.ofEntries(
            Map.entry("thornhorn", new float[] {1.314F, 1.528F, 2.321F}),
            Map.entry("snagwood", new float[] {1.454F, 2.812F, 0.905F}),
            Map.entry("puffcap_shepherd", new float[] {0.836F, 1.094F, 0.750F}),
            Map.entry("mire_gulper", new float[] {2.062F, 1.000F, 1.637F}),
            Map.entry("dunefin", new float[] {1.002F, 1.199F, 1.938F}),
            Map.entry("rattlecoil", new float[] {0.545F, 0.312F, 2.413F}),
            Map.entry("snowroller", new float[] {0.938F, 1.250F, 1.250F}),
            Map.entry("shiverwraith", new float[] {1.341F, 2.018F, 0.863F}),
            Map.entry("gloamhound", new float[] {1.110F, 1.274F, 2.021F}),
            Map.entry("vinedrop_strangler", new float[] {0.915F, 1.568F, 0.562F}),
            Map.entry("snapmaw_bloom", new float[] {1.256F, 1.585F, 1.195F}),
            Map.entry("stormeel", new float[] {0.463F, 0.438F, 1.492F}),
            Map.entry("shellguard_crab", new float[] {1.735F, 0.835F, 1.555F}),
            Map.entry("dripfang", new float[] {1.863F, 1.620F, 1.863F}),
            Map.entry("hushwing", new float[] {2.007F, 0.883F, 0.779F}),
            Map.entry("mycelite_shambler", new float[] {1.168F, 1.893F, 1.187F}),
            Map.entry("petalkin", new float[] {0.805F, 0.990F, 0.766F}),
            Map.entry("hollow_mourner", new float[] {1.005F, 2.629F, 0.895F}),
            Map.entry("dustwhirl", new float[] {0.875F, 1.562F, 0.987F}),
            Map.entry("skreel", new float[] {2.740F, 0.834F, 1.592F}),
            Map.entry("slagspitter", new float[] {1.125F, 0.894F, 1.125F}),
            Map.entry("slagheart_golem", new float[] {2.300F, 2.300F, 1.545F}),
            Map.entry("soot_manta", new float[] {3.120F, 0.494F, 2.298F}),
            Map.entry("magma_tortoise", new float[] {3.597F, 0.719F, 3.175F}),
            Map.entry("gleamtrap", new float[] {1.000F, 1.000F, 1.000F}),
            Map.entry("lavaleaper", new float[] {0.773F, 0.656F, 1.844F}),
            Map.entry("pyre_herald", new float[] {0.866F, 1.817F, 0.903F}),
            Map.entry("fortress_jailer", new float[] {1.188F, 2.656F, 0.562F}),
            Map.entry("flamewright", new float[] {1.472F, 2.398F, 1.323F}),
            Map.entry("levimoth", new float[] {1.805F, 0.815F, 1.001F})
    );

    public static float @Nullable [] of(String mob) {
        return BOUNDS.get(mob);
    }
}
