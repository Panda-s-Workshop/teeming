package com.panda.teeming.spawn;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Entry;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
    /** Default local cap: this many of one type within {@link #CAP_RADIUS}; apex mobs and passive giants: 1 in 128. */
    public static final int CAP = 3;
    public static final int CAP_RADIUS = 48;
    public static final int RARE_CAP_RADIUS = 128;
    /** Mobs capped at 1 in 128 blocks, besides the apex mobs. */
    private static final Set<String> GIANTS = Set.of("driftsong");
    private static final TagKey<Biome> NO_DEFAULT_MONSTERS = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "no_default_monsters"));

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
        SpawnPlacementType placement = switch (mob.place()) {
            case GROUND, SAND, LEAVES -> SpawnPlacementTypes.ON_GROUND;
            case WATER, SHALLOW -> SpawnPlacementTypes.IN_WATER;
            case LAVA -> SpawnPlacementTypes.IN_LAVA;
            case CEILING, AIR, NONE -> SpawnPlacementTypes.NO_RESTRICTIONS;
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
        if (!placeOk(mob, type, level, pos)) return false;
        boolean natural = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
        if (!natural) return true;
        if (!TeemingConfig.mobEnabled(mob.id())) return false;
        double chance = TeemingConfig.spawnChance(mob.id()) * Math.min(1.0, TeemingConfig.spawningDensity());
        if (chance <= 0 || random.nextDouble() >= chance) return false;
        if (pos.getY() > mob.maxY()) return false;
        if (!mob.passive()) {
            if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
            if (level.getBiome(pos).is(NO_DEFAULT_MONSTERS)) return false; // mushroom fields, deep dark
            if (!mob.anyLight() && mob.place() != RosterStats.Place.WATER && mob.place() != RosterStats.Place.LAVA
                    && !Monster.isDarkEnoughToSpawn(level, pos, random)) return false;
        }
        return underCap(mob, type, level, pos);
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

    /** Whether the mob's placement kind accepts the spot (no light, height or caps). Public for the placement GameTests. */
    public static boolean placeOk(Entry mob, EntityType<?> type, ServerLevelAccessor level, BlockPos pos) {
        return switch (mob.place()) {
            case GROUND -> level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type);
            case SAND -> level.getBlockState(pos.below()).is(BlockTags.SAND);
            case LEAVES -> level.getBlockState(pos.below()).is(BlockTags.LEAVES);
            case WATER -> level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.above()).is(FluidTags.WATER);
            case SHALLOW -> shallowWater(level, pos);
            case LAVA -> level.getFluidState(pos).is(FluidTags.LAVA) && level.getBlockState(pos.above()).isAir();
            case CEILING -> level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN);
            case AIR, NONE -> level.getBlockState(pos).isAir();
        };
    }

    /** Local caps: a 4th of a type within 48 blocks is refused (apex mobs and giants: a 2nd within 128). */
    public static boolean underCap(Entry mob, EntityType<? extends Mob> type, ServerLevelAccessor level, BlockPos pos) {
        boolean rare = mob.apex() || GIANTS.contains(mob.id());
        int radius = rare ? RARE_CAP_RADIUS : CAP_RADIUS;
        int cap = (int) Math.round((rare ? 1 : CAP) * Math.max(1.0, TeemingConfig.spawningDensity())); // density > 1 lifts the caps
        return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(radius), e -> e.getType() == type).size() < cap;
    }
}
