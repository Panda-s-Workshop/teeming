package com.panda.teeming.spawn;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The Teeming spawner: places the mobs biome spawn lists can't, on the server tick. Each player gets one attempt every
 * 200 ticks in the Nether and the End. An attempt chooses the spot first: a column 24–64 blocks away, then every
 * spawner rule's spot in it, and in a share-capped biome the spots of the biome-list monsters that live there. It then
 * picks, by weight, among the mobs whose rule accepted a spot (a structure's spot goes to that structure's mobs; an apex
 * is a candidate only after the attempt wins its rare apex roll, and then takes the spot), and
 * spawns it if the natural-spawn predicate agrees ({@link TeemingSpawns#check}: config, light, local caps), under the
 * vanilla per-player category cap. Spawns in the open world (not a structure) also keep the Teeming share of the
 * monsters round the player at the target ({@link SpawnTable#TARGET_SHARE}).
 *
 * <p>In the End an attempt first takes one of the End's rules at random ({@link #END_TURNS}) and looks only for that
 * rule's spots, so each rule gets its share of attempts: open sky lies over almost every column, and picked among all
 * rules at once it would take nearly every attempt from the rules that need an island's edge or underside. Only when
 * that rule finds nothing does the attempt look for every rule as above.
 */
public final class TeemingSpawner {
    public static final int EVERY = 200;
    /** While the player stands in a share-capped biome, open-world spawns are also tried this often. */
    public static final int CAPPED_EVERY = 50;
    public static final int MIN_DISTANCE = 24;
    public static final int MAX_DISTANCE = 64;
    /** Columns an attempt tries until one holds a spot some mob accepts. */
    public static final int COLUMNS = 4;
    /** Columns an End attempt tries on an edge rule's turn (underside, void air): edges are few. */
    public static final int EDGE_COLUMNS = 12;
    /** The share cap counts the monsters within this many blocks of the player (vanilla's despawn range). */
    public static final int SHARE_RADIUS = 128;
    /** The End's main island region: vanilla's {@code the_end} biome covers 64 chunks around the origin. */
    public static final int MAIN_ISLAND_RADIUS = 1024;
    /** Empty blocks under a spot that make it a drop into the void (underside and void-air rules). */
    public static final int VOID_GAP = 4;
    /** Blocks an underside or void-air rule walks from its column, in one direction, to find an island's edge. */
    public static final int EDGE_WALK = 16;
    /** How many blocks out from the edge a void-air spot stands (the edge check accepts land 1 to 4 to the side). */
    public static final int EDGE_OFF = 3;
    /** How far under an island's top an underside spot may lie. */
    public static final int UNDERSIDE_DEPTH = 64;
    private static final int SCAN = 24;
    /** How far from the first of a group the others may stand (blocks, each way). */
    public static final int GROUP_SPREAD = 3;
    /** Biomes whose Teeming monsters only this spawner places, under the share cap (default: {@link SpawnTable#SHARE_CAPPED}). */
    public static final TagKey<Biome> SHARE_CAPPED = TagKey.create(Registries.BIOME, TeemingMod.id("share_capped"));

    /** Where a spawner mob appears. The rule names are the {@code spawner} values in the roster. */
    public enum Rule {
        FORTRESS(Level.NETHER), BASTION(Level.NETHER), LAVA_SEA_AIR(Level.NETHER), FUNGUS_CAP(Level.NETHER), APEX_GROUND(Level.NETHER),
        MAIN_ISLAND(Level.END), APEX_ISLAND(Level.END), UNDERSIDE(Level.END), VOID_AIR(Level.END), VOID_BELOW(Level.END), SKY(Level.END), APEX_SKY(Level.END);

        public final ResourceKey<Level> dimension;

        Rule(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        public static @Nullable Rule of(String name) {
            if (name.isEmpty()) return null;
            return valueOf(name.toUpperCase(Locale.ROOT));
        }

        /** A rule that needs an island's edge or underside: its End turn tries {@link #EDGE_COLUMNS} columns. */
        public boolean edge() {
            return this == UNDERSIDE || this == VOID_AIR;
        }

        /** A structure's own spot: when an attempt finds one, only that structure's mobs may take it. */
        public boolean structure() {
            return this == FORTRESS || this == BASTION;
        }

        /**
         * A spot in the open Nether: its spawns count toward the biome's Teeming share, so they obey the share cap. The
         * End aims at a different mix and structures keep their own lists, so neither does.
         */
        public boolean shared() {
            return this == LAVA_SEA_AIR || this == FUNGUS_CAP;
        }

        /**
         * An apex, on open Nether ground, on the End's main island once the dragon is dead, or in the open sky off it: only after a won apex roll,
         * only with room for its whole body, and only with no other of its kind near (SpawnTable).
         */
        public boolean apex() {
            return this == APEX_GROUND || this == APEX_ISLAND || this == APEX_SKY;
        }

        /**
         * A spot in open air with nothing under it: the rule's own shape stands in for the mob's placement kind, so a
         * flier whose biome list puts it on the ground (the Resonant Triad) can take it too.
         */
        public boolean openAir() {
            return this == LAVA_SEA_AIR || this == VOID_AIR || this == VOID_BELOW || this == SKY || this == APEX_SKY;
        }

        /** Whether a mob of this rule may stand at {@code pos}: the shape of the spot only, no light or caps. */
        public boolean fits(ServerLevel level, BlockPos pos) {
            if (!air(level, pos)) return false;
            return switch (this) {
                case FORTRESS -> floor(level, pos) && inStructure(level, pos, BuiltinStructures.FORTRESS);
                case BASTION -> floor(level, pos) && inStructure(level, pos, BuiltinStructures.BASTION_REMNANT);
                case FUNGUS_CAP -> air(level, pos.above()) && level.getBlockState(pos.below()).is(BlockTags.WART_BLOCKS);
                case APEX_GROUND -> floor(level, pos);
                case LAVA_SEA_AIR -> pos.getY() > level.getSeaLevel() + 1 && lavaBelow(level, pos);
                case MAIN_ISLAND, APEX_ISLAND -> floor(level, pos) && mainIsland(level, pos) && mainIslandOpen(level);
                case UNDERSIDE -> !mainIsland(level, pos) && level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN)
                        && openBelow(level, pos);
                case VOID_AIR -> !mainIsland(level, pos) && air(level, pos.above()) && openBelow(level, pos) && edgeNear(level, pos);
                case VOID_BELOW -> !mainIsland(level, pos) && pos.getY() < 40 && islandAbove(level, pos);
                case SKY, APEX_SKY -> !mainIsland(level, pos) && level.canSeeSky(pos) && air(level, pos.above()) && air(level, pos.below());
            };
        }

        /**
         * A spot of this rule in the column at x, z near height {@code y}, or null. The underside and void-air rules
         * ignore the height and may take a column up to {@link #EDGE_WALK} blocks away, at the nearest island edge.
         */
        public @Nullable BlockPos find(ServerLevel level, int x, int y, int z, RandomSource random) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            boolean empty = top <= level.getMinY();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
            switch (this) {
                case MAIN_ISLAND -> {
                    if (empty) return null;
                    pos.setY(top);
                    return fits(level, pos) ? pos.immutable() : null;
                }
                case LAVA_SEA_AIR -> pos.setY(level.getSeaLevel() + 3 + random.nextInt(8));
                case VOID_BELOW -> {
                    if (empty) return null;
                    pos.setY(level.getMinY() + 8 + random.nextInt(24));
                }
                case SKY -> pos.setY(Math.max(top, 64) + 20 + random.nextInt(30));
                case VOID_AIR -> {
                    // off an island's edge, level with its ground: from the column, walk to the nearest edge; the spot
                    // stands EDGE_OFF blocks out, so a wide body (the Brinkray's 3) clears the rock
                    int[] edge = edgeAt(level, x, z, random);
                    if (edge == null) return null;
                    pos.set(edge[0] + (edge[0] - edge[2]) * (EDGE_OFF - 1), level.getHeight(Heightmap.Types.MOTION_BLOCKING, edge[2], edge[3]),
                            edge[1] + (edge[1] - edge[3]) * (EDGE_OFF - 1));
                }
                case UNDERSIDE -> {
                    // under the island in the column, else under the nearest edge: islands are deeper than a scan near
                    // the player's height reaches, and a random column seldom lands beside an edge
                    BlockPos here = empty ? null : underside(level, x, z);
                    if (here != null) return here;
                    int[] edge = edgeAt(level, x, z, random);
                    return edge == null ? null : underside(level, edge[2], edge[3]);
                }
                default -> {
                    for (int dy = SCAN; dy >= -SCAN; dy--) {   // fortress, bastion, fungus caps: scan the column
                        pos.setY(y + dy);
                        if (fits(level, pos)) return pos.immutable();
                    }
                    return null;
                }
            }
            return fits(level, pos) ? pos.immutable() : null;
        }
    }

    /**
     * A mob and the spot found for it. {@code rule} null: a biome-list monster placed in a share-capped biome, standing
     * in for that biome's spawn list.
     */
    public record Candidate(Entry mob, EntityType<? extends Mob> type, BlockPos pos, @Nullable Rule rule) {
        public boolean structure() {
            return rule != null && rule.structure();
        }

        public boolean apex() {
            return rule != null && rule.apex();
        }

        /** Counts toward the biome's Teeming share, so the share cap applies. */
        public boolean shared() {
            return rule == null || rule.shared();
        }
    }

    /** The End's rules that place regular (not apex) mobs: each is a turn an End attempt may take first. */
    public static final List<Rule> END_TURNS = Arrays.stream(Rule.values())
            .filter(r -> r.dimension == Level.END && !r.apex() && RosterStats.ALL.stream().anyMatch(e -> Rule.of(e.spawner()) == r))
            .toList();

    private TeemingSpawner() {}

    // ---- the tick -------------------------------------------------------------------------------------------------

    /** Server, after each level tick. */
    public static void tick(ServerLevel level) {
        if (TeemingConfig.spawningDensity() <= 0 || !level.getGameRules().get(GameRules.SPAWN_MOBS)) return;
        if (level.dimension() != Level.NETHER && level.dimension() != Level.END) return;
        List<ServerPlayer> players = level.players();
        for (ServerPlayer player : players) {
            if (player.isSpectator()) continue;
            long time = level.getGameTime() + player.getId();
            if (time % EVERY == 0) attempt(level, player, level.getRandom(), false);
            else if (time % CAPPED_EVERY == 0 && level.getBiome(player.blockPosition()).is(SHARE_CAPPED)) attempt(level, player, level.getRandom(), true);
        }
    }

    /** One attempt near the player, every rule; returns the spawned mob or null. Public as a test and command hook. */
    public static @Nullable Mob attempt(ServerLevel level, ServerPlayer player, RandomSource random) {
        return attempt(level, player, random, false);
    }

    /** One attempt near the player; {@code sharedOnly} leaves out structure and End rules. */
    public static @Nullable Mob attempt(ServerLevel level, ServerPlayer player, RandomSource random, boolean sharedOnly) {
        boolean apex = !sharedOnly && rollsApex(random);
        if (!sharedOnly && !apex && level.dimension() == Level.END && !END_TURNS.isEmpty()) {
            Rule turn = END_TURNS.get(random.nextInt(END_TURNS.size()));
            for (int i = 0; i < (turn.edge() ? EDGE_COLUMNS : COLUMNS); i++) {
                BlockPos column = column(player, random);
                if (!level.hasChunkAt(column)) continue;
                List<Candidate> found = candidatesAt(level, column.getX(), column.getY(), column.getZ(), random, false, false, turn);
                if (!found.isEmpty()) return place(level, player, pick(found, random), random);
            }
        }
        for (int i = 0; i < COLUMNS; i++) {
            BlockPos column = column(player, random);
            if (!level.hasChunkAt(column)) continue;
            List<Candidate> found = candidatesAt(level, column.getX(), column.getY(), column.getZ(), random, sharedOnly, apex);
            if (!found.isEmpty()) return place(level, player, pick(found, random), random);
        }
        return null;
    }

    /** A random column {@link #MIN_DISTANCE} to {@link #MAX_DISTANCE} blocks from the player, at the player's height. */
    private static BlockPos column(ServerPlayer player, RandomSource random) {
        double angle = random.nextDouble() * Mth.TWO_PI;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        return new BlockPos(Mth.floor(player.getX() + Math.cos(angle) * distance), player.getBlockY(),
                Mth.floor(player.getZ() + Math.sin(angle) * distance));
    }

    /** Spawns the picked candidate's group if its caps allow; the first mob spawned, or null. */
    private static @Nullable Mob place(ServerLevel level, ServerPlayer player, Candidate pick, RandomSource random) {
        MobCategory category = TeemingSpawns.category(pick.mob());
        if (pick.shared()) {
            // the share cap keeps Teeming to its share on top of vanilla's mobs, so the category cap is lifted by that share
            if (!underCategoryCap(level, player, category, 1.0 / (1.0 - SpawnTable.TARGET_SHARE))) return null;
            if (category == MobCategory.MONSTER && !shareAllows(level, player)) return null;
        } else if (level.dimension() == Level.END && category == MobCategory.MONSTER) {
            if (!endAllows(teemingNear(level, player, category))) return null;
        } else if (!underCategoryCap(level, player, category, 1.0)) {
            return null;
        }
        return spawnGroup(level, pick, random);
    }

    /**
     * Every mob that may take a spot in the column at x, z near height {@code y}, with that spot: the spot first, then
     * only the mobs whose rule accepts it. A mob placed only by the spawner goes only where its biome tag says, when the
     * tag holds anything. Public for the GameTests.
     */
    public static List<Candidate> candidatesAt(ServerLevel level, int x, int y, int z, RandomSource random, boolean sharedOnly) {
        return candidatesAt(level, x, y, z, random, sharedOnly, false);
    }

    /** As above; {@code apex}: the attempt won its apex roll, so apex mobs may take a spot too. */
    public static List<Candidate> candidatesAt(ServerLevel level, int x, int y, int z, RandomSource random, boolean sharedOnly, boolean apex) {
        return candidatesAt(level, x, y, z, random, sharedOnly, apex, null);
    }

    /** As above; {@code only} not null: only that rule's mobs, and no share-capped biome-list monsters. */
    public static List<Candidate> candidatesAt(ServerLevel level, int x, int y, int z, RandomSource random, boolean sharedOnly, boolean apex,
                                               @Nullable Rule only) {
        List<Candidate> out = new ArrayList<>();
        Map<Rule, Optional<BlockPos>> spots = new EnumMap<>(Rule.class);
        for (Entry mob : RosterStats.ALL) {
            Rule rule = Rule.of(mob.spawner());
            if (rule == null || rule.dimension != level.dimension() || (sharedOnly && !rule.shared()) || (rule.apex() && !apex)) continue;
            if (only != null && rule != only) continue;
            Optional<EntityType<? extends Mob>> type = TeemingSpawns.type(mob);
            if (type.isEmpty()) continue;
            if (rule.apex()) {
                BlockPos pos = apexSpot(level, rule, mob, type.get(), x, y, z, random);
                if (pos != null) out.add(new Candidate(mob, type.get(), pos, rule));
                continue;
            }
            BlockPos pos = spots.computeIfAbsent(rule, r -> Optional.ofNullable(r.find(level, x, y, z, random))).orElse(null);
            if (pos == null) continue;
            if (!mob.biomeSpawns() && !inTagOrUntagged(level, mob, pos)) continue;
            out.add(new Candidate(mob, type.get(), pos, rule));
        }
        if (only == null) shareCappedCandidates(level, x, y, z, random, out);
        return out;
    }

    /** Whether this attempt wins the apex roll ({@link SpawnTable#APEX_CHANCE}). */
    public static boolean rollsApex(RandomSource random) {
        return random.nextDouble() < SpawnTable.APEX_CHANCE;
    }

    /**
     * An apex's spot in the column: ground its rule accepts (open Nether ground; the main island once the dragon is dead)
     * and its placement accepts, with room for its whole body, in its biome tag, and no other of its kind within
     * {@link SpawnTable#APEX_RADIUS} blocks. A random one of those in the column, or null.
     */
    private static @Nullable BlockPos apexSpot(ServerLevel level, Rule rule, Entry mob, EntityType<? extends Mob> type, int x, int y, int z,
                                               RandomSource random) {
        List<BlockPos> fit = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        for (int dy = -SCAN; dy <= SCAN; dy++) {
            pos.setY(y + dy);
            if (pos.getY() <= level.getMinY() || pos.getY() >= level.getMaxY() || !rule.fits(level, pos)) continue;
            if (!TeemingSpawns.placeOk(mob, type, level, pos) || !inTagOrUntagged(level, mob, pos)) continue;
            if (!level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) continue;
            fit.add(pos.immutable());
        }
        if (fit.isEmpty()) return null;
        BlockPos spot = fit.get(random.nextInt(fit.size()));
        return apexClear(level, type, spot) ? spot : null;
    }

    /** No mob of the type within {@link SpawnTable#APEX_RADIUS} blocks of the spot. Public for the GameTests. */
    public static boolean apexClear(ServerLevel level, EntityType<?> type, BlockPos spot) {
        return level.getEntitiesOfClass(Mob.class, new AABB(spot).inflate(SpawnTable.APEX_RADIUS), e -> e.getType() == type).isEmpty();
    }

    /** A spot in the column whose biome is share-capped, with its biome. */
    private record Spot(BlockPos pos, Holder<Biome> biome) {}

    /**
     * The biome-list monsters of a share-capped biome: each one whose tag holds the spot's biome, at a random spot in
     * the column that its placement accepts and that has room for it.
     */
    private static void shareCappedCandidates(ServerLevel level, int x, int y, int z, RandomSource random, List<Candidate> out) {
        List<Spot> column = null;
        for (Entry mob : RosterStats.ALL) {
            if (!mob.biomeSpawns() || mob.passive()) continue;
            Optional<EntityType<? extends Mob>> type = TeemingSpawns.type(mob);
            if (type.isEmpty()) continue;
            if (column == null) column = cappedColumn(level, x, y, z);
            if (column.isEmpty()) return;
            TagKey<Biome> home = TeemingSpawns.tag(mob.id());
            List<BlockPos> fit = new ArrayList<>();
            for (Spot spot : column) {
                if (!spot.biome().is(home) || !TeemingSpawns.placeOk(mob, type.get(), level, spot.pos())) continue;
                if (!level.noCollision(type.get().getSpawnAABB(spot.pos().getX() + 0.5, spot.pos().getY(), spot.pos().getZ() + 0.5))) continue;
                fit.add(spot.pos());
            }
            if (!fit.isEmpty()) out.add(new Candidate(mob, type.get(), fit.get(random.nextInt(fit.size())), null));
        }
    }

    /** The open (air or fluid) blocks of the column near {@code y} whose biome is share-capped. */
    private static List<Spot> cappedColumn(ServerLevel level, int x, int y, int z) {
        List<Spot> out = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        for (int dy = -SCAN; dy <= SCAN; dy++) {
            pos.setY(y + dy);
            if (pos.getY() <= level.getMinY() || pos.getY() >= level.getMaxY()) continue;
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) continue;
            Holder<Biome> biome = level.getBiome(pos);
            if (biome.is(SHARE_CAPPED)) out.add(new Spot(pos.immutable(), biome));
        }
        return out;
    }

    /**
     * Weighted pick among the candidates (weight 0 counts as 1). A structure's spot goes to that structure's mobs; else a
     * won apex roll goes to the apex, as it is only a candidate after one.
     */
    public static Candidate pick(List<Candidate> found, RandomSource random) {
        List<Candidate> pool = found.stream().anyMatch(Candidate::structure) ? found.stream().filter(Candidate::structure).toList()
                : found.stream().anyMatch(Candidate::apex) ? found.stream().filter(Candidate::apex).toList() : found;
        int total = 0;
        for (Candidate c : pool) total += Math.max(1, c.mob().weight());
        int roll = random.nextInt(total);
        for (Candidate c : pool) {
            roll -= Math.max(1, c.mob().weight());
            if (roll < 0) return c;
        }
        return pool.getLast();
    }

    /** Whether the spot's biome is in the mob's tag; an empty tag places it wherever its rule fits. */
    private static boolean inTagOrUntagged(ServerLevel level, Entry mob, BlockPos pos) {
        TagKey<Biome> tag = TeemingSpawns.tag(mob.id());
        int size = level.registryAccess().lookupOrThrow(Registries.BIOME).get(tag).map(set -> set.size()).orElse(0);
        return size == 0 || level.getBiome(pos).is(tag);
    }

    // ---- the share cap -------------------------------------------------------------------------------------------

    /**
     * The monsters within {@link #SHARE_RADIUS} of the spot: {Teeming, other}. Apex mobs count on neither side: an apex
     * doesn't take a regular mob's place in the share. Public for the GameTests.
     */
    public static int[] monstersAround(ServerLevel level, BlockPos center) {
        int[] count = new int[2];
        AABB around = new AABB(center).inflate(SHARE_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, around, e -> e.getType().getCategory() == MobCategory.MONSTER)) {
            var id = mob.getType().builtInRegistryHolder().key().identifier();
            boolean teeming = id.getNamespace().equals(TeemingMod.MODID);
            if (teeming) {
                Entry row = RosterStats.BY_ID.get(id.getPath());
                if (row != null && row.apex()) continue;
            }
            count[teeming ? 0 : 1]++;
        }
        return count;
    }

    private static boolean shareAllows(ServerLevel level, ServerPlayer player) {
        int[] count = monstersAround(level, player.blockPosition());
        return shareAllows(count[0], count[1]);
    }

    /**
     * The share cap: one more Teeming monster only if that brings the Teeming share of the monsters around nearer the
     * target. With no other monster around none is added, and the share settles on the whole number nearest the target.
     */
    public static boolean shareAllows(int teeming, int others) {
        double now = teeming + others == 0 ? 0.0 : (double) teeming / (teeming + others);
        double next = (teeming + 1.0) / (teeming + others + 1.0);
        return Math.abs(next - SpawnTable.TARGET_SHARE) < Math.abs(now - SpawnTable.TARGET_SHARE);
    }

    // ---- spawning ------------------------------------------------------------------------------------------------

    /** Spawns the mob at a spot its rule found, if the natural-spawn predicate agrees. */
    public static @Nullable Mob spawn(ServerLevel level, Entry mob, EntityType<? extends Mob> type, BlockPos pos, RandomSource random) {
        return spawn(level, mob, type, pos, random, new SpawnGroupData[1], false);
    }

    /**
     * As above; {@code group} carries the spawn data from one member of a group to the next, as vanilla's spawner does,
     * so a mob can match its look or link to the others of its group. {@code openAir}: the spot is an open-air rule's
     * ({@link Rule#openAir}), whose shape stands in for the mob's placement kind.
     */
    private static @Nullable Mob spawn(ServerLevel level, Entry mob, EntityType<? extends Mob> type, BlockPos pos, RandomSource random,
                                       SpawnGroupData[] group, boolean openAir) {
        boolean ok = openAir ? TeemingSpawns.checkPlaced(mob, type, level, EntitySpawnReason.NATURAL, pos, random)
                : TeemingSpawns.check(mob, type, level, EntitySpawnReason.NATURAL, pos, random);
        if (!ok) return null;
        Mob created = type.create(level, EntitySpawnReason.NATURAL);
        if (created == null) return null;
        created.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        created.setYRot(random.nextFloat() * 360.0F);
        group[0] = created.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, group[0]);
        level.addFreshEntity(created);
        return created;
    }

    /** How many come together: the row's group size, from its least to its most. */
    public static int groupSize(Entry mob, RandomSource random) {
        return mob.groupMin() + random.nextInt(Math.max(1, mob.groupMax() - mob.groupMin() + 1));
    }

    /**
     * Spawns the picked mob and the rest of its group (the row's group size), the others on free spots its rule accepts
     * within {@link #GROUP_SPREAD} blocks of the first; a member without such a spot is left out. Returns the first, or
     * null. Public as a test hook.
     */
    public static @Nullable Mob spawnGroup(ServerLevel level, Candidate pick, RandomSource random) {
        SpawnGroupData[] group = new SpawnGroupData[1];
        boolean openAir = pick.rule() != null && pick.rule().openAir();
        Mob first = spawn(level, pick.mob(), pick.type(), pick.pos(), random, group, openAir);
        if (first == null) return null;
        for (int i = groupSize(pick.mob(), random) - 1; i > 0; i--) {
            BlockPos at = groupSpot(level, pick, random);
            if (at != null) spawn(level, pick.mob(), pick.type(), at, random, group, openAir);
        }
        return first;
    }

    /** A free spot for another member of the group near the first: the same rule's shape (or the mob's placement), room for its body. */
    private static @Nullable BlockPos groupSpot(ServerLevel level, Candidate pick, RandomSource random) {
        for (int tries = 0; tries < 10; tries++) {
            BlockPos at = pick.pos().offset(random.nextInt(2 * GROUP_SPREAD + 1) - GROUP_SPREAD, random.nextInt(3) - 1,
                    random.nextInt(2 * GROUP_SPREAD + 1) - GROUP_SPREAD);
            if (at.equals(pick.pos())) continue;
            boolean shape = pick.rule() != null ? pick.rule().fits(level, at) : TeemingSpawns.placeOk(pick.mob(), pick.type(), level, at);
            if (shape && level.noCollision(pick.type().getSpawnAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))) return at;
        }
        return null;
    }

    /** The vanilla per-player cap of the category (70 monsters, 10 creatures) within the despawn range, times {@code lift}. */
    static boolean underCategoryCap(ServerLevel level, ServerPlayer player, MobCategory category, double lift) {
        AABB around = player.getBoundingBox().inflate(category.getDespawnDistance());
        int count = level.getEntitiesOfClass(Mob.class, around, e -> e.getType().getCategory() == category).size();
        return count < category.getMaxInstancesPerChunk() * lift;
    }

    /** Teeming mobs of the category within the despawn range of the player. */
    static int teemingNear(ServerLevel level, ServerPlayer player, MobCategory category) {
        AABB around = player.getBoundingBox().inflate(category.getDespawnDistance());
        return level.getEntitiesOfClass(Mob.class, around, e -> e.getType().getCategory() == category
                && TeemingMod.MODID.equals(e.getType().builtInRegistryHolder().key().identifier().getNamespace())).size();
    }

    /**
     * The End's cap: room while the Teeming monsters round the player are fewer than {@link SpawnTable#END_SHARE} of the
     * vanilla per-player monster cap. Endermen don't count, so the main island, which they keep full, still gets its own.
     */
    public static boolean endAllows(int teemingNearby) {
        return teemingNearby < MobCategory.MONSTER.getMaxInstancesPerChunk() * SpawnTable.END_SHARE;
    }

    /**
     * A biome mod's End biome: one more Teeming monster only while the Teeming monsters within {@link #SHARE_RADIUS} of
     * the spot don't outnumber the others ({@link #outnumberAllows}). Its lists are set for endermen in groups of four;
     * where its endermen come in ones and twos the lists alone would bring Teeming to about 70 % of its monsters, and
     * this holds it near half ({@link SpawnTable#END_SHARE}).
     */
    public static boolean moddedEndAllows(ServerLevel level, BlockPos pos) {
        int[] count = monstersAround(level, pos);
        return outnumberAllows(count[0], count[1]);
    }

    /** Room for one more Teeming monster: they don't outnumber the others yet. */
    public static boolean outnumberAllows(int teeming, int others) {
        return teeming <= others;
    }

    // ---- the main-island gate ------------------------------------------------------------------------------------

    /** In the End, within the main island region. */
    public static boolean mainIsland(ServerLevel level, BlockPos pos) {
        return level.dimension() == Level.END && onMainIsland(pos);
    }

    public static boolean onMainIsland(BlockPos pos) {
        return (long) pos.getX() * pos.getX() + (long) pos.getZ() * pos.getZ() <= (long) MAIN_ISLAND_RADIUS * MAIN_ISLAND_RADIUS;
    }

    /** Main-island End mobs appear only once the dragon has been killed (config {@code endMainIslandAfterDragon}). */
    public static boolean mainIslandOpen(ServerLevel level) {
        if (!TeemingConfig.endMainIslandAfterDragon()) return true;
        EnderDragonFight fight = level.getDragonFight();
        return fight != null && fight.hasPreviouslyKilledDragon();
    }

    // ---- spot shapes ---------------------------------------------------------------------------------------------

    private static boolean air(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    private static boolean floor(ServerLevel level, BlockPos pos) {
        return air(level, pos.above()) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos, ResourceKey<Structure> structure) {
        return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(structure)).isValid();
    }

    private static boolean openBelow(ServerLevel level, BlockPos pos) {
        for (int i = 1; i <= VOID_GAP; i++) {
            if (!air(level, pos.below(i))) return false;
        }
        return true;
    }

    /**
     * An island's edge near the column at x, z: walking up to {@link #EDGE_WALK} blocks in one random direction, the
     * first two neighbouring columns whose tops differ by more than {@link #VOID_GAP} (the ground drops away, or an
     * island rises out of the void). {the lower column's x, z, the higher column's x, z}, or null with no edge within
     * the walk or past the loaded chunks.
     */
    private static int @Nullable [] edgeAt(ServerLevel level, int x, int z, RandomSource random) {
        Direction way = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int px = x, pz = z, before = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        for (int i = 1; i <= EDGE_WALK; i++) {
            int nx = px + way.getStepX(), nz = pz + way.getStepZ();
            if (!level.hasChunkAt(new BlockPos(nx, level.getMinY(), nz))) return null;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, nx, nz);
            if (top - before > VOID_GAP) return new int[] {px, pz, nx, nz};
            if (before - top > VOID_GAP) return new int[] {nx, nz, px, pz};
            px = nx;
            pz = nz;
            before = top;
        }
        return null;
    }

    /** The highest underside spot in the land column at x, z, at most {@link #UNDERSIDE_DEPTH} under its top, or null. */
    private static @Nullable BlockPos underside(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top - 1; y >= Math.max(level.getMinY() + VOID_GAP + 1, top - UNDERSIDE_DEPTH); y--) {
            pos.setY(y);
            if (Rule.UNDERSIDE.fits(level, pos)) return pos.immutable();
        }
        return null;
    }

    /** A solid block 1–4 blocks to the side, just below: the spot is beside an island's edge. */
    private static boolean edgeNear(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int d = 1; d <= 4; d++) {
                BlockPos edge = pos.relative(side, d).below();
                if (!air(level, edge)) return true;
            }
        }
        return false;
    }

    private static boolean lavaBelow(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < 12 && p.getY() > level.getMinY(); i++) {
            p.move(Direction.DOWN);
            if (level.getFluidState(p).is(FluidTags.LAVA)) return true;
            if (!air(level, p)) return false;
        }
        return false;
    }

    private static boolean islandAbove(ServerLevel level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY() + 8;
    }
}
