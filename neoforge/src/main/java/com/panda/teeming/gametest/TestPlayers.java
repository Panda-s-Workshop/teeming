package com.panda.teeming.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Real players for GameTests. The helper's mock player always counts as creative, so mobs that only react to
 * survival players (warnings, targeting, "hunts players standing on sand") never see it. A survivor here is a real
 * {@link ServerPlayer} in survival on an in-memory connection, and it leaves the server when the test ends.
 */
public final class TestPlayers {
    private TestPlayers() {}

    /** A survival player at {@code at} (relative to the test structure), removed when the test passes or fails. */
    public static ServerPlayer survivor(GameTestHelper helper, Vec3 at) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-survivor"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        // until its client says it loaded, the server ignores hits on a player
        if (!player.connection.hasClientLoaded()) player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.getFoodData().setFoodLevel(17);     // below 18: no natural healing to blur a damage reading
        player.getFoodData().setSaturation(0.0F);
        player.snapTo(helper.absoluteVec(at));
        whenDone(helper, () -> level.getServer().getPlayerList().remove(player));
        return player;
    }

    /** Runs {@code cleanup} once the test passes or fails. */
    public static void whenDone(GameTestHelper helper, Runnable cleanup) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {}

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                cleanup.run();
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                cleanup.run();
            }

            @Override
            public void testAddedForRerun(GameTestInfo old, GameTestInfo fresh, GameTestRunner runner) {}
        });
    }
}
