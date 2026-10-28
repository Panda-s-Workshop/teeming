package com.panda.teeming.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.overworld.Thornhorn;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

/** The {@code /teeming} commands, run through the server's real dispatcher with an operator's source. */
final class CommandTests {
    private CommandTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("command_guide", CommandTests::guide);
        out.accept("command_look", CommandTests::look);
        out.accept("command_spawns", CommandTests::spawns);
    }

    private static CommandSourceStack op(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel())
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)))).withSuppressedOutput();
    }

    /** The command's result, or -1 when the dispatcher refuses it (bad syntax or an unknown mob). */
    private static int run(GameTestHelper helper, String command) {
        return run(helper, op(helper), command);
    }

    private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            lastRefusal = e.getMessage();
            return -1;
        }
    }

    private static String lastRefusal = "";

    /** reveal fills one entry (seen, every look, kills to reveal) or all; reset clears one entry or all; a wrong name is refused. */
    private static void guide(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CommandSourceStack self = op(helper).withEntity(player);              // a mock player is in no player list, so it's targeted as @s
        FieldGuide.set(player, FieldGuideProgress.EMPTY.withKill("dunefin", "base"));
        helper.assertTrue(run(helper, self, "teeming guide reveal @s thornhorn") == 1, "reveal thornhorn runs for one player " + lastRefusal);
        FieldGuideProgress p = FieldGuide.get(player);
        helper.assertTrue(p.revealed("thornhorn") && p.killsOf("dunefin") == 1, "thornhorn revealed, dunefin untouched: " + p);
        helper.assertTrue(Looks.of("thornhorn").size() == 2, "thornhorn has both looks loaded");
        for (LookDef look : Looks.of("thornhorn")) helper.assertTrue(p.hasSeenLook("thornhorn", look.id()), "look seen: " + look.id());

        helper.assertTrue(run(helper, self, "teeming guide reset @s thornhorn") == 1, "reset thornhorn runs");
        p = FieldGuide.get(player);
        helper.assertTrue(!p.hasSeen("thornhorn") && p.killsOf("thornhorn") == 0 && !p.hasSeenLook("thornhorn", "base") && p.hasSeen("dunefin"),
                "only thornhorn is forgotten: " + p);

        helper.assertTrue(run(helper, self, "teeming guide reveal @s all") == 1, "reveal all runs");
        p = FieldGuide.get(player);
        for (RosterStats.Entry mob : RosterStats.ALL) helper.assertTrue(p.revealed(mob.id()), "revealed by all: " + mob.id());

        helper.assertTrue(run(helper, self, "teeming guide reset @s all") == 1, "reset all runs");
        p = FieldGuide.get(player);
        helper.assertTrue(p.seen().isEmpty() && p.kills().isEmpty() && p.looks().isEmpty() && p.trophies().isEmpty(), "reset all leaves an empty guide: " + p);
        helper.assertTrue(run(helper, self, "teeming guide reveal @s not_a_mob") == -1, "an unknown mob is refused");
        helper.succeed();
    }

    /** look sets a Teeming mob's look by name; a look it doesn't have and a vanilla mob change nothing. */
    private static void look(GameTestHelper helper) {
        Thornhorn mob = helper.spawn(ModMobs.THORNHORN.get(), new Vec3(3.5, 1, 3.5));
        mob.setNoAi(true);
        mob.core().setLook("base", false);
        String uuid = mob.getStringUUID();
        helper.assertTrue(run(helper, "teeming look " + uuid + " sunflower") == 1 && mob.look().equals("sunflower"), "the look is set: " + mob.look());
        helper.assertTrue(run(helper, "teeming look " + uuid + " no_such_look") == 0 && mob.look().equals("sunflower"), "an unknown look changes nothing");
        var pig = helper.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 6.5));
        helper.assertTrue(run(helper, "teeming look " + pig.getStringUUID() + " sunflower") == 0, "a vanilla mob has no Teeming look");
        helper.succeed();
    }

    /** spawns answers for every mob in the Overworld (Nether and End mobs say where they live instead); a wrong name is refused. */
    private static void spawns(GameTestHelper helper) {
        for (RosterStats.Entry mob : RosterStats.ALL) {
            int result = run(helper, "teeming spawns " + mob.id());
            helper.assertTrue(result >= 0, "spawns " + mob.id() + " runs, got " + result);
            if (mob.dim() != RosterStats.Dim.OVERWORLD) helper.assertTrue(result == 0, mob.id() + " lists no Overworld biome");
        }
        helper.assertTrue(run(helper, "teeming spawns not_a_mob") == -1, "an unknown mob is refused");
        helper.succeed();
    }
}
