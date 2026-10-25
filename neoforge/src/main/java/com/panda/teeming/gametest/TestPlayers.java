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
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Real players for GameTests. The helper's mock player always counts as creative, so mobs that only react to
 * survival players (warnings, targeting, "hunts players standing on sand") never see it. A survivor here is a real
 * {@link ServerPlayer} in survival on an in-memory connection, and it leaves the server when the test ends. Also the
 * shared test fixtures: clean-up when a test ends, and chunks kept ticking round an arena.
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

    /**
     * Keeps entities ticking up to {@code margin} blocks outside the arena, for a mob that can leave it (a flyer over an
     * opened roof). The test server force-loads only the chunks under each arena, and an entity in a chunk beside them
     * stops ticking where it is; a neighbouring arena's chunks stay forced only while its own batch runs, so whether a
     * mob a block past the edge still ticks depends on where the grid put the arena. The margin is how far past the
     * arena's edge the mob can get. The chunks stay forced until the batch ends, when the runner lets go of every forced
     * chunk: letting go sooner could drop a chunk that a neighbour's mob, or the next batch's arena, still needs.
     */
    public static void keepTicking(GameTestHelper helper, int margin) {
        AABB area = helper.getBounds().inflate(margin, 0, margin);
        for (int cx = Mth.floor(area.minX) >> 4; cx <= Mth.floor(area.maxX) >> 4; cx++) {
            for (int cz = Mth.floor(area.minZ) >> 4; cz <= Mth.floor(area.maxZ) >> 4; cz++) helper.getLevel().setChunkForced(cx, cz, true);
        }
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
