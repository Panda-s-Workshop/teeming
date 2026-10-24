package com.panda.teeming.gametest;

import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.mob.end.Levimoth;
import com.panda.teeming.mob.overworld.Thornhorn;
import com.panda.teeming.registry.ModMobs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A real ServerPlayer near a mob gets its entry, a wall hides it, three kills reveal it. */
final class FieldGuideTests {
    private FieldGuideTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("fieldguide_seen", FieldGuideTests::seen);
        out.accept("fieldguide_reveal", FieldGuideTests::reveal);
    }

    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(helper.absoluteVec(new Vec3(x, 1, z)));
        FieldGuide.set(player, FieldGuideProgress.EMPTY);
        return player;
    }

    private static void seen(GameTestHelper helper) {
        ServerPlayer player = player(helper, 1.5, 5.5);
        Thornhorn thornhorn = helper.spawn(ModMobs.THORNHORN.get(), new Vec3(9.5, 1, 5.5));
        thornhorn.setNoAi(true);
        Levimoth hidden = helper.spawn(ModMobs.LEVIMOTH.get(), new Vec3(5.5, 1, 10.5));
        hidden.setNoAi(true);
        for (int y = 1; y <= 4; y++) for (int x = 1; x <= 9; x++) helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);   // a wall between
        helper.runAtTickTime(3, () -> {
            // the level tick may already have looked for this player, and other arenas' mobs can be in view, so ask about these two
            helper.assertTrue(FieldGuide.sees(player, thornhorn), "the Thornhorn 8 blocks away in plain sight is seen");
            helper.assertTrue(!FieldGuide.sees(player, hidden), "the Levimoth behind the wall is not");
            FieldGuide.look(player);
            FieldGuideProgress p = FieldGuide.get(player);
            helper.assertTrue(p.hasSeen("thornhorn"), "looking records the Thornhorn");
            helper.assertTrue(p.hasSeenLook("thornhorn", thornhorn.look()), "and its look, " + thornhorn.look());
            helper.assertTrue(FieldGuide.look(player) == 0, "looking again adds nothing");
            helper.succeed();
        });
    }

    private static void reveal(GameTestHelper helper) {
        ServerPlayer player = player(helper, 1.5, 1.5);
        for (int i = 0; i < FieldGuide.KILLS_TO_REVEAL; i++) {
            Levimoth moth = helper.spawn(ModMobs.LEVIMOTH.get(), new Vec3(3.5 + i * 2, 1, 6.5));
            moth.setNoAi(true);
            moth.hurtServer(helper.getLevel(), moth.damageSources().playerAttack(player), 1000.0F);
            helper.assertTrue(moth.isDeadOrDying(), "the player's blow kills it");
            boolean last = i == FieldGuide.KILLS_TO_REVEAL - 1;
            helper.assertTrue(FieldGuide.get(player).revealed("levimoth", FieldGuide.KILLS_TO_REVEAL) == last,
                    "revealed only at the third kill, kills " + FieldGuide.get(player).killsOf("levimoth"));
        }
        Levimoth other = helper.spawn(ModMobs.LEVIMOTH.get(), new Vec3(9.5, 1, 9.5));
        other.setNoAi(true);
        other.hurtServer(helper.getLevel(), other.damageSources().magic(), 1000.0F);
        helper.assertTrue(FieldGuide.get(player).killsOf("levimoth") == FieldGuide.KILLS_TO_REVEAL, "a death the player didn't cause doesn't count");
        helper.succeed();
    }
}
