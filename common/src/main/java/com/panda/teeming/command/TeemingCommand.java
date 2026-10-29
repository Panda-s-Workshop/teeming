package com.panda.teeming.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.panda.teeming.TeemingConfig;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.SpawnTable;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * Operator commands: {@code /teeming guide reveal|reset <players> <mob|all>}, {@code /teeming look <entity> <look>}
 * and {@code /teeming spawns <mob>} (where and how often it spawns in the dimension the command runs in).
 */
public final class TeemingCommand {
    public static final String ALL = "all";
    /** Biomes listed by {@code /teeming spawns} before the rest are counted. */
    public static final int BIOMES_LISTED = 12;

    private static final DynamicCommandExceptionType UNKNOWN_MOB = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.teeming.unknown_mob", id));
    private static final SuggestionProvider<CommandSourceStack> MOBS = (c, b) -> SharedSuggestionProvider.suggest(RosterStats.ALL.stream().map(RosterStats.Entry::id), b);
    private static final SuggestionProvider<CommandSourceStack> MOBS_OR_ALL = (c, b) -> SharedSuggestionProvider.suggest(
            Stream.concat(Stream.of(ALL), RosterStats.ALL.stream().map(RosterStats.Entry::id)), b);
    private static final SuggestionProvider<CommandSourceStack> LOOKS = (c, b) -> {
        try {
            if (EntityArgument.getEntity(c, "entity") instanceof TeemingCreature creature) {
                return SharedSuggestionProvider.suggest(Looks.of(creature.mobId()).stream().map(LookDef::id), b);
            }
        } catch (CommandSyntaxException ignored) {
            // no entity typed yet: nothing to suggest
        }
        return b.buildFuture();
    };

    private TeemingCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("teeming")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("guide")
                        .then(Commands.literal("reveal")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("mob", StringArgumentType.word()).suggests(MOBS_OR_ALL)
                                                .executes(c -> guide(c, true)))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("mob", StringArgumentType.word()).suggests(MOBS_OR_ALL)
                                                .executes(c -> guide(c, false))))))
                .then(Commands.literal("look")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .then(Commands.argument("look", StringArgumentType.word()).suggests(LOOKS)
                                        .executes(TeemingCommand::look))))
                .then(Commands.literal("spawns")
                        .then(Commands.argument("mob", StringArgumentType.word()).suggests(MOBS)
                                .executes(TeemingCommand::spawns))));
    }

    private static List<String> mobs(String arg, boolean allowAll) throws CommandSyntaxException {
        if (allowAll && arg.equals(ALL)) return RosterStats.ALL.stream().map(RosterStats.Entry::id).toList();
        if (!RosterStats.BY_ID.containsKey(arg)) throw UNKNOWN_MOB.create(arg);
        return List.of(arg);
    }

    private static int guide(CommandContext<CommandSourceStack> c, boolean reveal) throws CommandSyntaxException {
        List<String> mobs = mobs(StringArgumentType.getString(c, "mob"), true);
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        for (ServerPlayer player : players) {
            FieldGuideProgress progress = FieldGuide.get(player).withRevealAt(TeemingConfig.killsToReveal());
            for (String mob : mobs) {
                progress = reveal ? progress.withRevealed(mob, Looks.of(mob).stream().map(LookDef::id).toList()) : progress.without(mob);
            }
            FieldGuide.set(player, progress);
        }
        String key = reveal ? "commands.teeming.guide.reveal" : "commands.teeming.guide.reset";
        c.getSource().sendSuccess(() -> Component.translatable(key, mobs.size(), players.size()), true);
        return players.size();
    }

    private static int look(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(c, "entity");
        String look = StringArgumentType.getString(c, "look");
        if (!(entity instanceof TeemingCreature creature)) {
            c.getSource().sendFailure(Component.translatable("commands.teeming.look.not_teeming", entity.getDisplayName()));
            return 0;
        }
        List<String> looks = Looks.of(creature.mobId()).stream().map(LookDef::id).toList();
        if (!looks.contains(look)) {
            c.getSource().sendFailure(Component.translatable("commands.teeming.look.unknown", entity.getDisplayName(), look, String.join(", ", looks)));
            return 0;
        }
        creature.core().setLook(look, false);
        c.getSource().sendSuccess(() -> Component.translatable("commands.teeming.look.set", entity.getDisplayName(), look), true);
        return 1;
    }

    private static int spawns(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        RosterStats.Entry mob = RosterStats.get(mobs(StringArgumentType.getString(c, "mob"), false).getFirst());
        CommandSourceStack source = c.getSource();
        Component name = Component.translatable("entity.teeming." + mob.id());
        if (!TeemingConfig.mobEnabled(mob.id()) || TeemingConfig.spawningDensity() <= 0) {
            source.sendSuccess(() -> Component.translatable("commands.teeming.spawns.off", name), false);
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (dimension(mob.dim()) != level.dimension()) {
            source.sendSuccess(() -> Component.translatable("commands.teeming.spawns.elsewhere", name,
                    Component.translatable("screen.teeming.field_guide.tab." + mob.dim().name().toLowerCase(Locale.ROOT))), false);
            return 0;
        }
        String chance = String.format(Locale.ROOT, "%.2f", TeemingConfig.spawnChance(mob.id()));
        String density = String.format(Locale.ROOT, "%.2f", TeemingConfig.spawningDensity());
        source.sendSuccess(() -> Component.translatable("commands.teeming.spawns.numbers", name, mob.groupMin(), mob.groupMax(), chance, density,
                TeemingSpawns.cap(mob), TeemingSpawns.capRadius(mob)), false);
        if (!mob.spawner().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.teeming.spawns.rule", mob.spawner()), false);
        }
        BlockPos at = BlockPos.containing(source.getPosition());
        int listed = listWeight(level, at, mob);
        if (listed > 0) source.sendSuccess(() -> Component.translatable("commands.teeming.spawns.weight", biomeId(level.getBiome(at)), listed), false);
        TagKey<Biome> tag = TeemingSpawns.tag(mob.id());
        // left out: an outer End rule keeps off the main island by position, whose biome a broad End tag holds too; no
        // monster spawns where no monster spawns, nor in a share-capped biome where no spawner runs (the Overworld's)
        TeemingSpawner.Rule rule = TeemingSpawner.Rule.of(mob.spawner());
        boolean outer = rule != null && rule.dimension == Level.END && rule != TeemingSpawner.Rule.MAIN_ISLAND && rule != TeemingSpawner.Rule.APEX_ISLAND;
        // an outer End list mob's tag holds #teeming:modded_end, and with it every vanilla End biome: of those it spawns
        // only in its own home's (a biome with a modifier of its own), and in every biome mod's End biome
        boolean modded = SpawnTable.MODDED_END.containsKey(mob.id());
        List<String> home = SpawnTable.tuned(mob.id()).stream().map(SpawnTable.Row::biome).toList();
        List<String> here = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                .filter(biome -> biome.is(tag) && !(outer && !mob.biomeSpawns() && biome.is(net.minecraft.world.level.biome.Biomes.THE_END))
                        && !(modded && SpawnTable.VANILLA_END_BIOMES.contains(biomeId(biome)) && !home.contains(biomeId(biome)))
                        && !(!mob.passive() && (biome.is(TeemingSpawns.NO_DEFAULT_MONSTERS)
                                || level.dimension() == Level.OVERWORLD && rule == null && biome.is(TeemingSpawner.SHARE_CAPPED))))
                .map(TeemingCommand::biomeId).sorted().toList();
        int tagged = level.registryAccess().lookupOrThrow(Registries.BIOME).get(tag).map(set -> set.size()).orElse(0);
        if (tagged == 0) {
            source.sendSuccess(() -> Component.translatable("commands.teeming.spawns.anywhere"), false);
        } else if (here.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.teeming.spawns.no_biome"), false);
        } else {
            String list = here.stream().limit(BIOMES_LISTED).collect(Collectors.joining(", "));
            Component biomes = here.size() > BIOMES_LISTED
                    ? Component.translatable("commands.teeming.spawns.biomes_more", here.size(), list, here.size() - BIOMES_LISTED)
                    : Component.translatable("commands.teeming.spawns.biomes", here.size(), list);
            source.sendSuccess(() -> biomes, false);
        }
        Holder<Biome> standing = level.getBiome(at);
        String answer = whereYouStand(level, at, mob);
        source.sendSuccess(() -> Component.translatable("commands.teeming.spawns." + answer, biomeId(standing)), false);
        return here.size();
    }

    /**
     * Whether the mob spawns naturally at {@code pos}, as the lang key's last part: "here_yes" when the live spawn list
     * there holds it; "here_spawner" when Teeming's spawner places it there (its rule's region holds the spot, or a
     * share-capped Nether biome its tag holds); "here_no_monsters" for a monster where no monster spawns (mushroom
     * fields, deep dark); "here_no_capped" for a monster whose tag holds a share-capped Overworld biome (its list there
     * holds no Teeming monster and no spawner runs); "here_no_main_island" and "here_only_main_island" (or
     * "here_only_main_island_any" with the dragon gate off) for an End rule or End list mob
     * on the wrong side of the main island's edge; else "here_no". Public for the GameTests.
     */
    public static String whereYouStand(ServerLevel level, BlockPos pos, RosterStats.Entry mob) {
        Holder<Biome> biome = level.getBiome(pos);
        if (!mob.passive() && biome.is(TeemingSpawns.NO_DEFAULT_MONSTERS)) return "here_no_monsters";
        TeemingSpawner.Rule rule = TeemingSpawner.Rule.of(mob.spawner());
        boolean wantsMain = rule == TeemingSpawner.Rule.MAIN_ISLAND || rule == TeemingSpawner.Rule.APEX_ISLAND;
        // the spawn predicate keeps every End list mob but a main-island one off the main island's area, by position
        if (mob.biomeSpawns() && level.dimension() == Level.END && !TeemingSpawns.mainIslandAllows(mob, level, pos))
            return wantsMain ? mainIslandKey() : "here_no_main_island";
        if (mob.biomeSpawns() && listWeight(level, pos, mob) > 0) return "here_yes";
        TagKey<Biome> tag = TeemingSpawns.tag(mob.id());
        boolean inTag = biome.is(tag);
        if (mob.biomeSpawns() && !mob.passive() && inTag && biome.is(TeemingSpawner.SHARE_CAPPED))
            return level.dimension() == Level.NETHER ? "here_spawner" : "here_no_capped";
        if (rule == null || rule.dimension != level.dimension()) return "here_no";
        // the spawner asks a spawner-only mob's tag (an empty one places it wherever its rule fits), not a biome-list mob's
        int tagged = level.registryAccess().lookupOrThrow(Registries.BIOME).get(tag).map(set -> set.size()).orElse(0);
        if (!mob.biomeSpawns() && tagged > 0 && !inTag) return "here_no";
        if (level.dimension() == Level.END) {
            boolean main = TeemingSpawner.mainIsland(level, pos);
            if (main && !wantsMain) return "here_no_main_island";
            if (wantsMain && (!main || !TeemingSpawner.mainIslandOpen(level))) return mainIslandKey();
        }
        return "here_spawner";
    }

    /** The answer for a main-island mob away from it: "once the dragon is dead" only while that gate is on. */
    private static String mainIslandKey() {
        return TeemingConfig.endMainIslandAfterDragon() ? "here_only_main_island" : "here_only_main_island_any";
    }

    /** The mob's weight in the live spawn list the game uses at {@code pos}, after every loader and data change; 0 if absent. */
    private static int listWeight(ServerLevel level, BlockPos pos, RosterStats.Entry mob) {
        MobSpawnSettings settings = level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos);
        var type = TeemingSpawns.type(mob).orElse(null);
        if (type == null) return 0;
        return settings.getMobsInCategory(TeemingSpawns.category(mob)).unwrap().stream()
                .filter(w -> w.value().type() == type).mapToInt(Weighted::weight).sum();
    }

    private static String biomeId(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
    }

    private static net.minecraft.resources.ResourceKey<Level> dimension(RosterStats.Dim dim) {
        return switch (dim) {
            case OVERWORLD -> Level.OVERWORLD;
            case NETHER -> Level.NETHER;
            case END -> Level.END;
        };
    }
}
