package com.panda.teeming.spawn;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Entry;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Where and when Teeming mobs may spawn, shared by both loaders. The biome lists come from data
 * ({@code #teeming:spawns/<id>}, NeoForge biome modifiers, Fabric {@code BiomeModifications}); this class gives every
 * registered mob its placement and the predicate that applies light, height, config and local caps.
 */
public final class TeemingSpawns {
    /** Local caps: {@link TeemingConfig#localCap} of one type within {@link #CAP_RADIUS}; apex mobs and passive giants: 1 in 128; both times the density above 1. */
    public static final int CAP_RADIUS = 48;
    public static final int RARE_CAP_RADIUS = 128;
    /** Mobs capped at 1 in 128 blocks, besides the apex mobs. */
    private static final Set<String> GIANTS = Set.of("driftsong");
    /** Biomes where no monster spawns naturally (mushroom fields, deep dark): the spawn predicate refuses every monster there. */
    public static final TagKey<Biome> NO_DEFAULT_MONSTERS = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "no_default_monsters"));
    /**
     * A biome mod's End biomes reach the outer-island End mobs through this tag ({@code #c:is_end} and
     * {@code #minecraft:is_end} by default); vanilla's five End biomes are left out wherever it is read.
     */
    public static final TagKey<Biome> MODDED_END = TagKey.create(Registries.BIOME, TeemingMod.id("modded_end"));

    private TeemingSpawns() {}

    /** The loader's way of registering a placement (NeoForge: the event; Fabric: {@code SpawnPlacements.register}). */
    public interface Placements {
        <T extends Mob> void set(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate);
    }

    public static TagKey<Biome> tag(String id) {
        return TagKey.create(Registries.BIOME, TeemingMod.id("spawns/" + id));
    }

    public static MobCategory category(Entry mob) {
        return mob.passive() ? MobCategory.CREATURE : MobCategory.MONSTER;
    }

    /** The roster mob's entity type, if it is registered yet (mobs land one by one). */
    @SuppressWarnings("unchecked")
    public static Optional<EntityType<? extends Mob>> type(Entry mob) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(TeemingMod.id(mob.id())).map(t -> (EntityType<? extends Mob>) t);
    }

    /** Registers the placement of every registered roster mob. */
    public static void placements(Placements out) {
        for (Entry mob : RosterStats.ALL) type(mob).ifPresent(type -> register(out, type, mob));
    }

    private static <T extends Mob> void register(Placements out, EntityType<T> type, Entry mob) {
        // Leaves refuse every mob but the ocelot and the parrot through ON_GROUND (each leaves block asks the type), so a
        // canopy mob takes no vanilla placement and its own predicate asks for leaves below and room above.
        SpawnPlacementType placement = switch (mob.place()) {
            case GROUND, SAND -> SpawnPlacementTypes.ON_GROUND;
            case WATER, SHALLOW -> SpawnPlacementTypes.IN_WATER;
            case LAVA -> SpawnPlacementTypes.IN_LAVA;
            case LEAVES, CEILING, AIR, NONE -> SpawnPlacementTypes.NO_RESTRICTIONS;
        };
        Heightmap.Types heightmap = switch (mob.place()) {
            case WATER, SHALLOW -> Heightmap.Types.OCEAN_FLOOR;
            case LEAVES -> Heightmap.Types.MOTION_BLOCKING;
            default -> Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        };
        out.set(type, placement, heightmap, (t, level, reason, pos, random) -> check(mob, t, level, reason, pos, random));
    }

    /** The spawn predicate. Natural and chunk-generation spawns get every rule; spawners, eggs and commands only the place. */
    public static boolean check(Entry mob, EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
                                RandomSource random) {
        return placeOk(mob, type, level, pos) && checkPlaced(mob, type, level, reason, pos, random);
    }

    /** The spawn predicate past the placement kind, for a spot whose shape a spawner rule has already settled. */
    public static boolean checkPlaced(Entry mob, EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
                                      RandomSource random) {
        boolean natural = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
        if (!natural) return true;
        if (!TeemingConfig.mobEnabled(mob.id())) return false;
        double chance = TeemingConfig.spawnChance(mob.id()) * Math.min(1.0, TeemingConfig.spawningDensity());
        if (mob.place() == RosterStats.Place.WATER) chance *= SpawnTable.OPEN_WATER_CHANCE;   // it wins open water by default
        if (chance <= 0 || random.nextDouble() >= chance) return false;
        if (pos.getY() > mob.maxY()) return false;
        if (mob.surface() && !level.canSeeSky(pos)) return false;
        boolean end = level.getLevel().dimension() == Level.END;
        if (end && !mainIslandAllows(mob, level.getLevel(), pos)) return false;
        if (!mob.passive()) {
            if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
            if (level.getBiome(pos).is(NO_DEFAULT_MONSTERS)) return false; // mushroom fields, deep dark
            if (!mob.anyLight() && mob.place() != RosterStats.Place.WATER && mob.place() != RosterStats.Place.LAVA
                    && !Monster.isDarkEnoughToSpawn(level, pos, random)) return false;
        }
        if (!underCap(mob, type, level, pos)) return false;
        return !(end && !mob.passive() && moddedEnd(level.getBiome(pos)) && !TeemingSpawner.moddedEndAllows(level.getLevel(), pos));
    }

    /**
     * The main island's rule in the End, by position, as the spawner's rules have it: inside the main island's area only a
     * main-island mob may spawn, and only once the dragon is dead (config {@code endMainIslandAfterDragon}); outside it,
     * any. A biome mod's End biome can lie inside that area, so a biome list there would otherwise reach the dragon's
     * island with the outer islands' mobs.
     */
    public static boolean mainIslandAllows(Entry mob, ServerLevel level, BlockPos pos) {
        if (!TeemingSpawner.mainIsland(level, pos)) return true;
        TeemingSpawner.Rule rule = TeemingSpawner.Rule.of(mob.spawner());
        boolean main = rule == TeemingSpawner.Rule.MAIN_ISLAND || rule == TeemingSpawner.Rule.APEX_ISLAND;
        return main && TeemingSpawner.mainIslandOpen(level);
    }

    /** A biome mod's End biome: in {@link #MODDED_END}, and not one of vanilla's five End biomes. */
    public static boolean moddedEnd(Holder<Biome> biome) {
        return biome.is(MODDED_END) && biome.unwrapKey().map(k -> !SpawnTable.VANILLA_END_BIOMES.contains(k.identifier().toString())).orElse(true);
    }

    /**
     * Room for the whole mob in the air below a sturdy underside. The spawner checks the mob's full box at the spot, so
     * a ceiling mob taller than one block needs that many air blocks under the ceiling; it grips it on its first tick.
     */
    private static boolean underCeiling(ServerLevelAccessor level, EntityType<?> type, BlockPos pos) {
        int rows = Math.max(1, (int) Math.ceil(type.getHeight()));
        for (int i = 0; i < rows; i++) if (!level.getBlockState(pos.above(i)).isAir()) return false;
        BlockPos ceiling = pos.above(rows);
        return level.getBlockState(ceiling).isFaceSturdy(level, ceiling, Direction.DOWN);
    }

    /**
     * Water one or two deep over a solid floor, open to the air: a swamp pool or a mangrove puddle, never a lake bed or
     * an ocean floor.
     */
    private static boolean shallowWater(ServerLevelAccessor level, BlockPos pos) {
        if (!level.getFluidState(pos).is(FluidTags.WATER)) return false;
        BlockPos floor = level.getFluidState(pos.below()).is(FluidTags.WATER) ? pos.below(2) : pos.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        BlockPos above = pos.above();
        int water = pos.getY() - floor.getY();                  // water blocks from the floor up to pos
        while (level.getFluidState(above).is(FluidTags.WATER) && water < 3) {
            water++;
            above = above.above();
        }
        return water <= 2 && level.getBlockState(above).isAir();
    }

    /** The spot and the block over it are open, as vanilla's ground placement asks: no fluid, no full block, nothing that hurts. */
    private static boolean roomAbove(ServerLevelAccessor level, EntityType<?> type, BlockPos pos) {
        BlockPos up = pos.above();
        return NaturalSpawner.isValidEmptySpawnBlock(level, pos, level.getBlockState(pos), level.getFluidState(pos), type)
                && NaturalSpawner.isValidEmptySpawnBlock(level, up, level.getBlockState(up), level.getFluidState(up), type);
    }

    /** Whether the mob's placement kind accepts the spot (no light, height or caps). Public for the placement GameTests. */
    public static boolean placeOk(Entry mob, EntityType<?> type, ServerLevelAccessor level, BlockPos pos) {
        return switch (mob.place()) {
            case GROUND -> level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type);
            case SAND -> level.getBlockState(pos.below()).is(BlockTags.SAND);
            case LEAVES -> level.getBlockState(pos.below()).is(BlockTags.LEAVES) && roomAbove(level, type, pos);
            case WATER -> level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.above()).is(FluidTags.WATER);
            case SHALLOW -> shallowWater(level, pos);
            case LAVA -> level.getFluidState(pos).is(FluidTags.LAVA) && level.getBlockState(pos.above()).isAir();
            case CEILING -> underCeiling(level, type, pos);
            case AIR, NONE -> level.getBlockState(pos).isAir();
        };
    }

    /** Apex mobs and giants are capped at 1 within {@link #RARE_CAP_RADIUS}, the rest by the config within {@link #CAP_RADIUS}. */
    public static boolean rare(Entry mob) {
        return mob.apex() || GIANTS.contains(mob.id());
    }

    /** How many of the mob's type may stand within its cap radius; a spawning density above 1 lifts it. */
    public static int cap(Entry mob) {
        return (int) Math.round((rare(mob) ? 1 : TeemingConfig.localCap()) * Math.max(1.0, TeemingConfig.spawningDensity()));
    }

    public static int capRadius(Entry mob) {
        return rare(mob) ? RARE_CAP_RADIUS : CAP_RADIUS;
    }

    /** Local caps: one more of a type is refused once {@link #cap} of it stand within {@link #capRadius}. */
    public static boolean underCap(Entry mob, EntityType<? extends Mob> type, ServerLevelAccessor level, BlockPos pos) {
        return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(capRadius(mob)), e -> e.getType() == type).size() < cap(mob);
    }
}
