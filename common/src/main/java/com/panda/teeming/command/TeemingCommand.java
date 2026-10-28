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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

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
            FieldGuideProgress progress = FieldGuide.get(player);
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
        source.sendSuccess(() -> Component.translatable("commands.teeming.spawns.numbers", name, mob.weight(), mob.groupMin(), mob.groupMax(), chance, density,
                mob.apex() ? 1 : TeemingConfig.localCap()), false);
        if (!mob.spawner().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.teeming.spawns.rule", mob.spawner()), false);
        }
        TagKey<Biome> tag = TeemingSpawns.tag(mob.id());
        List<String> here = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                .filter(biome -> biome.is(tag)).map(TeemingCommand::biomeId).sorted().toList();
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
        Holder<Biome> standing = level.getBiome(BlockPos.containing(source.getPosition()));
        boolean fits = tagged == 0 || standing.is(tag);
        source.sendSuccess(() -> Component.translatable(fits ? "commands.teeming.spawns.here_yes" : "commands.teeming.spawns.here_no", biomeId(standing)), false);
        return here.size();
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
