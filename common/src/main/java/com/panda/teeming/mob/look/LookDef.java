package com.panda.teeming.mob.look;

import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * One look of one mob, from {@code data/<ns>/teeming/looks/<mob>/<look>.json}. The client finds the texture
 * by name ({@code textures/entity/<mob>/<look>.png}); everything here is server data. No Minecraft types, so the
 * picking rule is unit-tested.
 *
 * @param weight     relative chance among the looks allowed where the mob spawns
 * @param scale      size multiplier (hitbox and model together, via {@code minecraft:scale})
 * @param attributes attribute id → multiplier, applied as {@code ADD_MULTIPLIED_TOTAL}
 * @param biomes     "" = anywhere; else a biome id or {@code #tag} the look is limited to
 */
public record LookDef(String mob, String id, int weight, float scale, Map<String, Double> attributes, String biomes) {
    public static final String BASE = "base";

    /**
     * Picks a look: a weighted roll among the looks allowed in the spawn biome. {@code roll} gets a bound and returns a
     * number in [0, bound). Falls back to the first look (the base) when nothing is allowed.
     */
    public static LookDef pick(List<LookDef> looks, Predicate<String> inBiome, IntUnaryOperator roll) {
        if (looks.isEmpty()) throw new IllegalArgumentException("a mob with no looks");
        int total = 0;
        for (LookDef look : looks) if (look.allowed(inBiome)) total += look.weight;
        if (total <= 0) return looks.getFirst();
        int r = roll.applyAsInt(total);
        for (LookDef look : looks) {
            if (!look.allowed(inBiome)) continue;
            r -= look.weight;
            if (r < 0) return look;
        }
        return looks.getFirst();
    }

    private boolean allowed(Predicate<String> inBiome) {
        return weight > 0 && (biomes.isEmpty() || inBiome.test(biomes));
    }
}
