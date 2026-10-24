package com.panda.teeming.gametest;

import com.panda.teeming.gametest.mob.ThornhornTests;
import com.panda.teeming.gametest.mob.SlagheartGolemTests;
import com.panda.teeming.gametest.mob.LevimothTests;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;

/** Registers every mob's own GameTests (gametest/mob/<Mob>Tests.java). */
final class MobTests {
    private MobTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        ThornhornTests.register(out);
        SlagheartGolemTests.register(out);
        LevimothTests.register(out);
    }
}
