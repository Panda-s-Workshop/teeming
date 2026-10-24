package com.panda.teeming.mob.look;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** How a spawning mob picks its look. */
class LookPickTest {
    private static LookDef look(String id, int weight, String biomes) {
        return new LookDef("thornhorn", id, weight, 1.0F, Map.of(), biomes);
    }

    @Test
    void weightsSplitTheRolls() {
        List<LookDef> looks = List.of(look("base", 80, ""), look("sunflower", 20, ""));
        int second = 0;
        for (int r = 0; r < 100; r++) {
            int roll = r;
            if (LookDef.pick(looks, b -> false, bound -> roll % bound).id().equals("sunflower")) second++;
        }
        assertEquals(20, second);
    }

    @Test
    void aBiomeLookOnlyAppearsInItsBiome() {
        List<LookDef> looks = List.of(look("base", 80, ""), look("red", 240, "#c:is_badlands"));
        for (int r = 0; r < 80; r++) {
            int roll = r;
            assertEquals("base", LookDef.pick(looks, b -> false, bound -> roll % bound).id(), "outside the badlands");
        }
        assertEquals("red", LookDef.pick(looks, "#c:is_badlands"::equals, bound -> bound - 1).id());
    }

    @Test
    void noAllowedLookFallsBackToTheBase() {
        List<LookDef> looks = List.of(look("base", 0, ""), look("pale", 10, "minecraft:snowy_taiga"));
        assertEquals("base", LookDef.pick(looks, b -> false, bound -> 0).id());
    }
}
