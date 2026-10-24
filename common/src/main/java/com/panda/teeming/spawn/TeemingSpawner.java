package com.panda.teeming.spawn;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The Teeming spawner: places the mobs biome spawn lists can't, on the server tick. Each player gets one
 * attempt every 200 ticks in the Nether and the End: pick a registered mob with a spawner rule for this dimension
 * (weighted), a column 24–64 blocks away, the rule's position in it, then the same predicate as natural spawns
 * ({@link TeemingSpawns#check}: config, light, local caps), under the vanilla per-player category cap.
 */
public final class TeemingSpawner {
    public static final int EVERY = 200;
    public static final int MIN_DISTANCE = 24;
    public static final int MAX_DISTANCE = 64;
    /** The End's main island region: vanilla's {@code the_end} biome covers 64 chunks around the origin. */
    public static final int MAIN_ISLAND_RADIUS = 1024;
    /** Empty blocks under a spot that make it a drop into the void (underside and void-air rules). */
    public static final int VOID_GAP = 4;
    private static final int SCAN = 24;

    /** Where a spawner mob appears. The rule names are the {@code spawner} values in the roster. */
    public enum Rule {
        FORTRESS(Level.NETHER), BASTION(Level.NETHER), LAVA_SEA_AIR(Level.NETHER), FUNGUS_CAP(Level.NETHER),
        MAIN_ISLAND(Level.END), UNDERSIDE(Level.END), VOID_AIR(Level.END), VOID_BELOW(Level.END), SKY(Level.END);

        public final ResourceKey<Level> dimension;

        Rule(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        public static @Nullable Rule of(String name) {
            if (name.isEmpty()) return null;
            return valueOf(name.toUpperCase(Locale.ROOT));
        }

        /** Whether a mob of this rule may stand at {@code pos}: the shape of the spot only, no light or caps. */
        public boolean fits(ServerLevel level, BlockPos pos) {
            if (!air(level, pos)) return false;
            return switch (this) {
                case FORTRESS -> floor(level, pos) && inStructure(level, pos, BuiltinStructures.FORTRESS);
                case BASTION -> floor(level, pos) && inStructure(level, pos, BuiltinStructures.BASTION_REMNANT);
                case FUNGUS_CAP -> air(level, pos.above()) && level.getBlockState(pos.below()).is(BlockTags.WART_BLOCKS);
                case LAVA_SEA_AIR -> pos.getY() > level.getSeaLevel() + 1 && lavaBelow(level, pos);
                case MAIN_ISLAND -> floor(level, pos) && mainIsland(level, pos) && mainIslandOpen(level);
                case UNDERSIDE -> !mainIsland(level, pos) && level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN)
                        && openBelow(level, pos);
                case VOID_AIR -> !mainIsland(level, pos) && air(level, pos.above()) && openBelow(level, pos) && edgeNear(level, pos);
                case VOID_BELOW -> !mainIsland(level, pos) && pos.getY() < 40 && islandAbove(level, pos);
                case SKY -> !mainIsland(level, pos) && level.canSeeSky(pos) && air(level, pos.above()) && air(level, pos.below());
            };
        }

        /** A spot of this rule in the column at x, z near height {@code y}, or null. */
        @Nullable BlockPos find(ServerLevel level, int x, int y, int z, RandomSource random) {
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
                    if (!empty) return null;
                    pos.setY(y + random.nextInt(9) - 4);
                }
                default -> {
                    for (int dy = SCAN; dy >= -SCAN; dy--) {   // fortress, bastion, fungus caps, undersides: scan the column
                        pos.setY(y + dy);
                        if (fits(level, pos)) return pos.immutable();
                    }
                    return null;
                }
            }
            return fits(level, pos) ? pos.immutable() : null;
        }
    }

    private TeemingSpawner() {}

    // ---- the tick -------------------------------------------------------------------------------------------------

    /** Server, after each level tick. */
    public static void tick(ServerLevel level) {
        if (TeemingConfig.spawningDensity() <= 0 || !level.getGameRules().get(GameRules.SPAWN_MOBS)) return;
        if (level.dimension() != Level.NETHER && level.dimension() != Level.END) return;
        List<ServerPlayer> players = level.players();
        for (ServerPlayer player : players) {
            if (player.isSpectator() || (level.getGameTime() + player.getId()) % EVERY != 0) continue;
            attempt(level, player, level.getRandom());
        }
    }

    /** One attempt near the player; returns the spawned mob or null. Public as a test and command hook. */
    public static @Nullable Mob attempt(ServerLevel level, ServerPlayer player, RandomSource random) {
        List<Entry> candidates = new ArrayList<>();
        int total = 0;
        for (Entry mob : RosterStats.ALL) {
            Rule rule = Rule.of(mob.spawner());
            if (rule == null || rule.dimension != level.dimension() || TeemingSpawns.type(mob).isEmpty()) continue;
            candidates.add(mob);
            total += Math.max(1, mob.weight());
        }
        if (candidates.isEmpty()) return null;
        int roll = random.nextInt(total);
        Entry mob = candidates.getLast();
        for (Entry c : candidates) {
            roll -= Math.max(1, c.weight());
            if (roll < 0) {
                mob = c;
                break;
            }
        }
        EntityType<? extends Mob> type = TeemingSpawns.type(mob).orElseThrow();
        MobCategory category = TeemingSpawns.category(mob);
        if (!underCategoryCap(level, player, category)) return null;
        double angle = random.nextDouble() * Mth.TWO_PI;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return null;
        BlockPos pos = Rule.of(mob.spawner()).find(level, x, player.getBlockY(), z, random);
        return pos == null ? null : spawn(level, mob, type, pos, random);
    }

    /** Spawns the mob at a spot its rule found, if the natural-spawn predicate agrees. */
    public static @Nullable Mob spawn(ServerLevel level, Entry mob, EntityType<? extends Mob> type, BlockPos pos, RandomSource random) {
        if (!TeemingSpawns.check(mob, type, level, EntitySpawnReason.NATURAL, pos, random)) return null;
        Mob created = type.create(level, EntitySpawnReason.NATURAL);
        if (created == null) return null;
        created.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        created.setYRot(random.nextFloat() * 360.0F);
        created.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
        level.addFreshEntity(created);
        return created;
    }

    /** The vanilla per-player cap of the category (70 monsters, 10 creatures) within the despawn range. */
    static boolean underCategoryCap(ServerLevel level, ServerPlayer player, MobCategory category) {
        AABB around = player.getBoundingBox().inflate(category.getDespawnDistance());
        return level.getEntitiesOfClass(Mob.class, around, e -> e.getType().getCategory() == category).size() < category.getMaxInstancesPerChunk();
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
