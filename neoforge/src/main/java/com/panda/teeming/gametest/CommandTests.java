package com.panda.teeming.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.overworld.Thornhorn;
import com.panda.teeming.command.TeemingCommand;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;

/** The {@code /teeming} commands, run through the server's real dispatcher with an operator's source. */
final class CommandTests {
    private CommandTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("command_guide", CommandTests::guide);
        out.accept("command_look", CommandTests::look);
        out.accept("command_spawns", CommandTests::spawns);
        out.accept("command_spawns_where", CommandTests::spawnsWhere);
        out.accept("command_spawns_rules", CommandTests::spawnsRules);
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

    /**
     * "Where you stand" tells the truth: in the mushroom fields and the deep dark (both in the Hollow Mourner's and the
     * Hushwing's tags, where no monster spawns) the answer is no; in the plains, whose live list holds the Mourner, yes.
     * The arena's biome is set for each answer and put back after.
     */
    private static void spawnsWhere(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos from = at.offset(-4, -4, -4), to = at.offset(4, 4, 4);
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Holder<Biome> was = level.getBiome(at);
        java.util.List<String> wrong = new java.util.ArrayList<>();
        for (var c : java.util.List.of(java.util.Map.entry(Biomes.MUSHROOM_FIELDS, "here_no_monsters"), java.util.Map.entry(Biomes.DEEP_DARK, "here_no_monsters"),
                java.util.Map.entry(Biomes.PLAINS, "here_yes"))) {
            FillBiomeCommand.fill(level, from, to, biomes.getOrThrow(c.getKey()));
            for (String id : java.util.List.of("hollow_mourner", "hushwing")) {
                String got = TeemingCommand.whereYouStand(level, at, RosterStats.get(id));
                String want = id.equals("hushwing") && c.getKey() == Biomes.PLAINS ? "here_yes" : c.getValue();
                if (!got.equals(want)) wrong.add(id + " in " + c.getKey().identifier().getPath() + ": " + got + ", want " + want);
            }
        }
        FillBiomeCommand.fill(level, from, to, was);
        helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        helper.succeed();
    }

    /** The lang keys of the messages a command sent, in order. */
    private static final class Heard implements CommandSource {
        final java.util.List<String> keys = new java.util.ArrayList<>();

        @Override public void sendSystemMessage(Component message) {
            keys.add(message.getContents() instanceof TranslatableContents t ? t.getKey() : message.getString());
        }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }

    /**
     * The answer for a spawner rule asks the rule's spot, and an emptied tag is told apart. In the GameTest server's
     * Nether at (-3000, 120, 3000), painted soul sand valley (in no forest, outside the Aurovore's tag) and in neither a
     * fortress nor a bastion: the fortress's Pyre Herald gets "only inside a nether fortress", the bastion's Aurovore
     * "only inside a bastion" and the lava sea's Soot Manta "where its rule fits, in the air over a lava sea", never the
     * spawner's yes. The Aurovore's tag lets the spawner place it there though the tag doesn't hold the biome (its
     * bastions stand in four Nether biomes). With its tag read as emptied the spawner places it nowhere, the command's
     * biome line says its tag is empty and "where you stand" says no; the spawner-only Herald with an emptied tag keeps
     * "any biome where its rule fits". The biome is put back after.
     */
    private static void spawnsRules(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            helper.fail("the GameTest server has no Nether");
            return;
        }
        BlockPos at = new BlockPos(-3000, 120, 3000);
        nether.getChunk(at.getX() >> 4, at.getZ() >> 4);
        BlockPos from = at.offset(-4, -4, -4), to = at.offset(4, 4, 4);
        Holder<Biome> was = nether.getBiome(at);
        FillBiomeCommand.fill(nether, from, to, nether.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.SOUL_SAND_VALLEY));
        java.util.List<String> wrong = new java.util.ArrayList<>();
        RosterStats.Entry aurovore = RosterStats.get("aurovore"), herald = RosterStats.get("pyre_herald"), manta = RosterStats.get("soot_manta");
        if (TeemingSpawner.Rule.FORTRESS.within(nether, at) || TeemingSpawner.Rule.BASTION.within(nether, at)) wrong.add("the spot is inside a structure");
        if (nether.getBiome(at).is(TeemingSpawns.tag("aurovore"))) wrong.add("the Aurovore's tag holds the soul sand valley");
        for (var c : java.util.List.of(java.util.Map.entry(herald, "here_only_fortress"), java.util.Map.entry(aurovore, "here_only_bastion"),
                java.util.Map.entry(manta, "here_rule_lava_sea_air"))) {
            String got = TeemingCommand.whereYouStand(nether, at, c.getKey());
            if (!got.equals(c.getValue())) wrong.add(c.getKey().id() + ": " + got + ", want " + c.getValue());
        }
        if (!TeemingSpawner.tagAllows(nether, aurovore, at)) wrong.add("the Aurovore's tag keeps the spawner from a bastion in a biome outside it");
        Heard heard = new Heard();
        // not op(): its output is suppressed, and the messages are what this reads
        CommandSourceStack there = helper.getLevel().getServer().createCommandSourceStack().withLevel(nether).withPosition(Vec3.atCenterOf(at)).withSource(heard);
        TeemingSpawns.withEmptiedTags(java.util.Set.of("aurovore", "pyre_herald"), () -> {
            if (TeemingSpawner.tagAllows(nether, aurovore, at)) wrong.add("with its tag emptied the spawner may still place the Aurovore");
            String got = TeemingCommand.whereYouStand(nether, at, aurovore);
            if (!got.equals("here_no")) wrong.add("with its tag emptied the Aurovore gets " + got);
            got = TeemingCommand.whereYouStand(nether, at, herald);
            if (!got.equals("here_only_fortress")) wrong.add("with its tag emptied the Herald gets " + got);
            run(helper, there, "teeming spawns aurovore");
            if (!heard.keys.contains("commands.teeming.spawns.emptied") || !heard.keys.contains("commands.teeming.spawns.here_no"))
                wrong.add("the Aurovore with its tag emptied: " + heard.keys);
            heard.keys.clear();
            run(helper, there, "teeming spawns pyre_herald");
            if (!heard.keys.contains("commands.teeming.spawns.anywhere")) wrong.add("the Herald with its tag emptied: " + heard.keys);
        });
        FillBiomeCommand.fill(nether, from, to, was);
        helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        helper.succeed();
    }
}
