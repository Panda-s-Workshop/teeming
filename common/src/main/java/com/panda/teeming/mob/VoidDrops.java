package com.panda.teeming.mob;

import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a Teeming mob's death drops land. Over the void (nothing solid in its column down to the world's floor) its
 * loot and experience would fall out of the world, so a mob a player killed leaves them at that player's feet; with
 * no player killer, or a killer over the void too, they drop where it died as usual.
 */
public final class VoidDrops {
    private VoidDrops() {}

    /**
     * Whether nothing solid lies in a column from {@code fromY} down to {@code floorY}, both included. {@code solidAt}
     * reads a block height; the rule needs no game types so it can be checked on its own.
     */
    public static boolean overVoid(int fromY, int floorY, IntPredicate solidAt) {
        for (int y = fromY; y >= floorY; y--) if (solidAt.test(y)) return false;
        return true;
    }

    /** Whether the column under {@code at} is open to the world's floor. */
    public static boolean overVoid(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        int from = Math.min((int) Math.floor(at.y), level.getMaxY());
        return overVoid(from, level.getMinY(), y -> {
            pos.set(x, y, z);
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        });
    }

    /** Where {@code mob}'s drops go: the killing player's feet when it died over the void, else nowhere new (null). */
    public static @Nullable Vec3 moveTo(ServerLevel level, LivingEntity mob, DamageSource source) {
        if (!(source.getEntity() instanceof Player killer) || killer.level() != level || !killer.isAlive()) return null;
        if (!overVoid(level, mob.position()) || overVoid(level, killer.position())) return null;
        return killer.position();
    }

    /** Runs {@code drop} (the vanilla death drops) with {@code mob} stood at {@link #moveTo}'s spot, if there is one. */
    public static void drop(ServerLevel level, LivingEntity mob, DamageSource source, Runnable drop) {
        Vec3 at = moveTo(level, mob, source);
        if (at == null) {
            drop.run();
            return;
        }
        Vec3 was = mob.position();
        mob.setPos(at.x, at.y, at.z);
        try {
            drop.run();
        } finally {
            mob.setPos(was.x, was.y, was.z);
        }
    }
}
