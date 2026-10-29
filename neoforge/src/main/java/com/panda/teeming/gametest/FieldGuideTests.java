package com.panda.teeming.gametest;

import com.mojang.authlib.GameProfile;
import com.panda.teeming.TeemingConfig;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.mob.end.Glimmerless;
import com.panda.teeming.mob.end.Levimoth;
import com.panda.teeming.mob.nether.Gleamtrap;
import com.panda.teeming.mob.nether.Lavaleaper;
import com.panda.teeming.mob.nether.OssuaryWyrm;
import com.panda.teeming.mob.overworld.Dripfang;
import com.panda.teeming.mob.overworld.Dunefin;
import com.panda.teeming.mob.overworld.MireGulper;
import com.panda.teeming.mob.overworld.Snagwood;
import com.panda.teeming.mob.overworld.Thornhorn;
import com.panda.teeming.registry.ModComponents;
import com.panda.teeming.registry.ModItems;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.trophy.TrophyMob;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A real ServerPlayer near a mob gets its entry, a wall hides it, so do invisibility, a disguise and a surface until the
 * mob breaks cover, three kills reveal it, a held trophy is owned for good: through a save and load and through death.
 */
final class FieldGuideTests {
    private FieldGuideTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("fieldguide_seen", FieldGuideTests::seen);
        out.accept("fieldguide_hidden_unseen", FieldGuideTests::hiddenUnseen);
        out.accept("fieldguide_lurker_unseen", FieldGuideTests::lurkerUnseen);
        out.accept("fieldguide_reveal", FieldGuideTests::reveal);
        out.accept("fieldguide_trophy_owned", FieldGuideTests::trophyOwned);
        out.accept("fieldguide_trophy_kept", FieldGuideTests::trophyKept);
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

    /**
     * One way of hiding: the mob, how it hides, how it breaks cover. Each check runs in a single tick, so the mob's own
     * tick can't undo the state between the two looks.
     */
    private record Hiding<T extends Mob>(String what, EntityType<T> type, Consumer<T> hide, Consumer<T> show) {
        @SuppressWarnings("unchecked")
        void check(GameTestHelper helper, ServerPlayer player, Mob mob) {
            T m = (T) mob;
            hide.accept(m);
            helper.assertTrue(!FieldGuide.sees(player, m), what + ": not seen while it hides");
            show.accept(m);
            helper.assertTrue(FieldGuide.sees(player, m), what + ": seen once it breaks cover");
        }
    }

    /**
     * Every kind of hiding keeps a mob in plain sight 3 to 9 blocks off out of the guide until it shows itself: the
     * invisible Glimmerless (seen when it shows, and the Shimmering one's faint outline is seen even while it hides), a
     * rooted Snagwood (until it wakes), a disguised Gleamtrap (until it splits open), a hanging Dripfang (until it lets
     * go), and a Mire Gulper, a Dunefin and an Ossuary Wyrm under their surface (until they come up). Looking records
     * none of them while they hide.
     */
    private static void hiddenUnseen(GameTestHelper helper) {
        ServerPlayer player = player(helper, 1.5, 5.5);
        List<Hiding<?>> table = List.of(
                // the base look: a Shimmering one's faint outline is seen while it hides (checked below), and the look is rolled at spawn
                new Hiding<>("an invisible Glimmerless", ModMobs.GLIMMERLESS.get(),
                        m -> { m.core().setLook("base", true); m.core().setFlag(Glimmerless.HIDDEN_FLAG, true); },
                        m -> m.core().setFlag(Glimmerless.HIDDEN_FLAG, false)),
                new Hiding<Snagwood>("a rooted Snagwood", ModMobs.SNAGWOOD.get(),
                        m -> helper.assertTrue(m.disguised() || m.root(true), "it roots"), m -> helper.assertTrue(m.wake(null), "it wakes")),
                new Hiding<Gleamtrap>("a disguised Gleamtrap", ModMobs.GLEAMTRAP.get(),
                        m -> helper.assertTrue(m.disguised(), "it waits disguised"),
                        m -> helper.assertTrue(m.startPhase(Gleamtrap.Step.TELL, Gleamtrap.TELL_TICKS, null), "it splits open")),
                new Hiding<Dripfang>("a hanging Dripfang", ModMobs.DRIPFANG.get(),
                        m -> helper.assertTrue(m.disguised() || m.hang(m.position(), true), "it hangs"), m -> m.core().end()),
                new Hiding<MireGulper>("a submerged Mire Gulper", ModMobs.MIRE_GULPER.get(),
                        m -> m.core().setFlag(MireGulper.SUBMERGED, true), m -> m.core().setFlag(MireGulper.SUBMERGED, false)),
                new Hiding<Dunefin>("a Dunefin under the sand", ModMobs.DUNEFIN.get(),
                        m -> m.core().setFlag(Dunefin.SUBMERGED, true), m -> m.core().setFlag(Dunefin.SUBMERGED, false)),
                new Hiding<OssuaryWyrm>("an Ossuary Wyrm under the soul sand", ModMobs.OSSUARY_WYRM.get(),
                        m -> m.core().setFlag(OssuaryWyrm.SUBMERGED, true), m -> m.core().setFlag(OssuaryWyrm.SUBMERGED, false)));
        Mob[] mobs = new Mob[table.size()];
        for (int i = 0; i < table.size(); i++) {
            Mob m = helper.spawn(table.get(i).type(), new Vec3(4.5 + (i % 4) * 2, 1, 2.5 + (i / 4) * 6));
            m.setNoAi(true);
            TestPlayers.whenDone(helper, m::discard);
            mobs[i] = m;
        }
        helper.runAtTickTime(3, () -> {
            for (int i = 0; i < table.size(); i++) table.get(i).check(helper, player, mobs[i]);

            Glimmerless faint = (Glimmerless) mobs[0];
            faint.core().setLook("shimmering", true);
            faint.core().setFlag(Glimmerless.HIDDEN_FLAG, true);
            helper.assertTrue(faint.isInvisible() && FieldGuide.sees(player, faint), "a hidden Shimmering Glimmerless shows faintly, so it is seen");
            faint.core().setFlag(Glimmerless.HIDDEN_FLAG, false);

            FieldGuide.set(player, FieldGuideProgress.EMPTY);
            Snagwood snag = (Snagwood) mobs[1];
            helper.assertTrue(snag.root(true), "the Snagwood roots again");
            ((MireGulper) mobs[4]).core().setFlag(MireGulper.SUBMERGED, true);
            FieldGuide.look(player);
            FieldGuideProgress p = FieldGuide.get(player);
            helper.assertTrue(!p.hasSeen("snagwood") && !p.hasSeen("mire_gulper"), "looking records neither hidden one: " + p);
            helper.assertTrue(p.hasSeen("gleamtrap"), "but records the open Gleamtrap beside them: " + p);
            helper.succeed();
        });
    }

    /**
     * A Lavaleaper lurking in the lava, its back under the surface, isn't seen from the shore 5 blocks off, though lava
     * doesn't block a line of sight; held in its tell, its back out of the lava, it is.
     */
    private static void lurkerUnseen(GameTestHelper helper) {
        for (int x = 0; x <= 11; x++) {                                     // lava two deep, x 1..8, a stone shore round it
            for (int z = 0; z <= 11; z++) {
                for (int y = 1; y <= 2; y++) {
                    boolean lava = x >= 1 && x <= 8 && z >= 1 && z <= 10;
                    helper.setBlock(new BlockPos(x, y, z), lava ? Blocks.LAVA : Blocks.STONE);
                }
            }
        }
        ServerPlayer player = player(helper, 10.5, 5.5);
        player.snapTo(helper.absoluteVec(new Vec3(10.5, 3, 5.5)));
        Lavaleaper l = helper.spawn(ModMobs.LAVALEAPER.get(), new Vec3(5.5, 2.2, 5.5));
        l.removeFreeWill();
        TestPlayers.whenDone(helper, l::discard);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(l.isInLava() && l.getBoundingBox().maxY < l.lavaSurface(), "it lurks with its back under the lava");
            helper.assertTrue(player.hasLineOfSight(l) && !FieldGuide.sees(player, l), "in a line of sight but lurking, it isn't seen");
            helper.assertTrue(l.startPhase(Lavaleaper.Step.TELL, 60, null), "a held tell starts");
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(l.getBoundingBox().maxY > l.lavaSurface(), "in the tell its back is out of the lava");
            helper.assertTrue(FieldGuide.sees(player, l), "and it is seen");
            helper.succeed();
        });
    }

    private static void trophyOwned(GameTestHelper helper) {
        ServerPlayer player = player(helper, 1.5, 1.5);
        ItemStack trophy = ModItems.TROPHY.get().getDefaultInstance();
        trophy.set(ModComponents.TROPHY_MOB.get(), new TrophyMob("slagheart_golem", "base"));
        player.getInventory().setItem(3, trophy);
        player.getInventory().setItem(4, ModItems.TROPHY.get().getDefaultInstance());   // a blank trophy names no mob
        FieldGuide.look(player);
        FieldGuideProgress p = FieldGuide.get(player);
        helper.assertTrue(p.hasTrophy("slagheart_golem"), "a trophy in the inventory marks its mob as owned");
        helper.assertTrue(p.trophies().size() == 1, "only named trophies count, owned " + p.trophies());
        player.getInventory().setItem(3, ItemStack.EMPTY);                             // placed on a shelf, say
        FieldGuide.look(player);
        helper.assertTrue(FieldGuide.get(player).hasTrophy("slagheart_golem"), "it stays owned once the trophy leaves the inventory");
        helper.succeed();
    }

    /**
     * What the guide holds, owned trophies included, is the player's for good: saved and loaded with the player (the
     * loader's attachment serializer, as a relog does), and carried over when they die and respawn (the server's real
     * respawn, which hands the new player a copy). The trophy itself is gone by then: it dropped with the inventory.
     */
    private static void trophyKept(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-trophy-keeper"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        if (!player.connection.hasClientLoaded()) player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ServerPlayer[] current = {player};
        TestPlayers.whenDone(helper, () -> level.getServer().getPlayerList().remove(current[0]));
        FieldGuide.set(player, FieldGuideProgress.EMPTY.withKill("thornhorn", "base"));
        ItemStack trophy = ModItems.TROPHY.get().getDefaultInstance();
        trophy.set(ModComponents.TROPHY_MOB.get(), new TrophyMob("levimoth", "base"));
        player.getInventory().setItem(0, trophy);
        FieldGuide.look(player);
        helper.assertTrue(FieldGuide.get(player).hasTrophy("levimoth"), "held on the beat, the Levimoth trophy is owned");

        var registries = level.registryAccess();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        player.saveWithoutId(out);
        ServerPlayer loaded = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, out.buildResult()));
        FieldGuideProgress saved = FieldGuide.get(loaded);
        helper.assertTrue(saved.hasTrophy("levimoth") && saved.killsOf("thornhorn") == 1, "saved and loaded, the guide keeps it: " + saved);

        player.getInventory().setItem(0, ItemStack.EMPTY);                 // no trophy left to find again after the respawn
        player.hurtServer(level, player.damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(player.isDeadOrDying(), "the player dies");
        ServerPlayer respawned = level.getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        current[0] = respawned;
        helper.assertTrue(respawned != player, "the respawn is a new player");
        FieldGuideProgress after = FieldGuide.get(respawned);
        helper.assertTrue(after.hasTrophy("levimoth"), "after death and respawn the trophy is still owned: " + after);
        helper.assertTrue(after.killsOf("thornhorn") == 1 && after.hasSeen("thornhorn"), "and the rest of the guide too: " + after);
        helper.succeed();
    }

    private static void reveal(GameTestHelper helper) {
        ServerPlayer player = player(helper, 1.5, 1.5);
        int needed = TeemingConfig.killsToReveal();
        helper.assertTrue(needed == 3, "the default is 3 kills, config says " + needed);
        FieldGuide.set(player, FieldGuideProgress.EMPTY.withRevealAt(7));                // a value from an older setting
        FieldGuide.look(player);
        helper.assertTrue(FieldGuide.get(player).revealAt() == needed, "a look brings the server's setting, got " + FieldGuide.get(player).revealAt());
        for (int i = 0; i < needed; i++) {
            Levimoth moth = helper.spawn(ModMobs.LEVIMOTH.get(), new Vec3(3.5 + i * 2, 1, 6.5));
            moth.setNoAi(true);
            moth.hurtServer(helper.getLevel(), moth.damageSources().playerAttack(player), 1000.0F);
            helper.assertTrue(moth.isDeadOrDying(), "the player's blow kills it");
            boolean last = i == needed - 1;
            helper.assertTrue(FieldGuide.get(player).revealAt() == needed, "the progress carries the server's setting to the client");
            helper.assertTrue(FieldGuide.get(player).revealed("levimoth") == last,
                    "revealed only at the third kill, kills " + FieldGuide.get(player).killsOf("levimoth"));
        }
        Levimoth other = helper.spawn(ModMobs.LEVIMOTH.get(), new Vec3(9.5, 1, 9.5));
        other.setNoAi(true);
        other.hurtServer(helper.getLevel(), other.damageSources().magic(), 1000.0F);
        helper.assertTrue(FieldGuide.get(player).killsOf("levimoth") == needed, "a death the player didn't cause doesn't count");
        helper.succeed();
    }
}
