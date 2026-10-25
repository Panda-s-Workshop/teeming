package com.panda.teeming.gametest;

import com.panda.teeming.gametest.mob.ThornhornTests;
import com.panda.teeming.gametest.mob.SnagwoodTests;
import com.panda.teeming.gametest.mob.PuffcapShepherdTests;
import com.panda.teeming.gametest.mob.DunefinTests;
import com.panda.teeming.gametest.mob.RattlecoilTests;
import com.panda.teeming.gametest.mob.SnapmawBloomTests;
import com.panda.teeming.gametest.mob.StormeelTests;
import com.panda.teeming.gametest.mob.ShellguardCrabTests;
import com.panda.teeming.gametest.mob.SkreelTests;
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
        SnagwoodTests.register(out);
        PuffcapShepherdTests.register(out);
        DunefinTests.register(out);
        RattlecoilTests.register(out);
        SnapmawBloomTests.register(out);
        StormeelTests.register(out);
        ShellguardCrabTests.register(out);
        SkreelTests.register(out);
        SlagheartGolemTests.register(out);
        LevimothTests.register(out);
    }
}
