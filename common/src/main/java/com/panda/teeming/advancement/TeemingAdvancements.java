package com.panda.teeming.advancement;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Dim;
import com.panda.teeming.roster.RosterStats.Entry;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The advancements the mod's own rules grant. Their JSON (written by the advancement generator) has one criterion,
 * "done", on the impossible trigger, and only this awards it. The apex kills are vanilla kill criteria and need no code.
 *
 * <p>From the Field Guide, checked whenever a player's progress is written and on every 20-tick look: the root on the
 * first creature seen; a Naturalist once every creature of its dimension is seen; Teeming with Knowledge once all are
 * revealed; Mantelpiece on the first trophy owned, The Whole Menagerie on all of them. "Every creature" is every
 * registered roster mob the config hasn't switched off, so a pack that turns one off can still finish the set.
 *
 * <p>The four counter feats are awarded by their mobs at the moment the feat happens; each mob's code says what counts.
 */
public final class TeemingAdvancements {
    public static final String ROOT = "teeming";
    public static final String OVERWORLD_NATURALIST = "overworld_naturalist";
    public static final String NETHER_NATURALIST = "nether_naturalist";
    public static final String END_NATURALIST = "end_naturalist";
    public static final String KNOWLEDGE = "teeming_with_knowledge";
    public static final String MANTELPIECE = "mantelpiece";
    public static final String MENAGERIE = "the_whole_menagerie";
    public static final String THORNHORN_WALL = "thornhorn_wall";
    public static final String SLAGHEART_QUENCH = "slagheart_quench";
    public static final String MAGMA_TORTOISE_RIDE = "magma_tortoise_ride";
    public static final String GRAND_ORRERY_PATTERN = "grand_orrery_pattern";
    /** The criterion every code-awarded advancement of the mod has. */
    public static final String CRITERION = "done";

    private TeemingAdvancements() {}

    /** Awards {@code teeming:<key>}; nothing if it is already done or a data pack removed it. */
    public static void award(ServerPlayer player, String key) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        AdvancementHolder holder = server.getAdvancements().get(TeemingMod.id(key));
        if (holder != null) player.getAdvancements().award(holder, CRITERION);
    }

    /** Awards whatever the player's Field Guide progress has earned. */
    public static void fromProgress(ServerPlayer player, FieldGuideProgress progress) {
        if (progress.seen().isEmpty() && progress.trophies().isEmpty()) return;
        FieldGuideProgress p = progress.withRevealAt(TeemingConfig.killsToReveal());
        if (anySeen(p)) award(player, ROOT);
        if (every(Dim.OVERWORLD, m -> p.hasSeen(m.id()))) award(player, OVERWORLD_NATURALIST);
        if (every(Dim.NETHER, m -> p.hasSeen(m.id()))) award(player, NETHER_NATURALIST);
        if (every(Dim.END, m -> p.hasSeen(m.id()))) award(player, END_NATURALIST);
        if (every(null, m -> p.revealed(m.id()))) award(player, KNOWLEDGE);
        if (counted().anyMatch(m -> p.hasTrophy(m.id()))) award(player, MANTELPIECE);
        if (every(null, m -> p.hasTrophy(m.id()))) award(player, MENAGERIE);
    }

    private static boolean anySeen(FieldGuideProgress p) {
        return counted().anyMatch(m -> p.hasSeen(m.id()));
    }

    /** True when every counted creature of {@code dim} (null: of all three) passes {@code test}, and there is one. */
    private static boolean every(Dim dim, Predicate<Entry> test) {
        List<Entry> set = counted().filter(m -> dim == null || m.dim() == dim).toList();
        return !set.isEmpty() && set.stream().allMatch(test);
    }

    /** The creatures the sets count: registered roster mobs the config hasn't switched off. */
    private static Stream<Entry> counted() {
        return RosterStats.ALL.stream().filter(m -> TeemingSpawns.type(m).isPresent() && TeemingConfig.mobEnabled(m.id()));
    }
}
