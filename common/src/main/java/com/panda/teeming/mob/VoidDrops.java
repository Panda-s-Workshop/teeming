package com.panda.teeming.mob;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a Teeming mob's death drops land. Dropped where it died, they fall until they meet a block or lava; when that is
 * lava, or nothing down to the world's floor (the void), they would be lost. So when a player killed it they go to that
 * player instead: to where the player stands if that ground is safe, else onto the block holding the player up (a
 * player sneaking at an island's rim), else, with nothing under the player either (gliding, swimming in lava), straight
 * into the player's inventory, the experience at the player's side. With no player killer they drop where it died.
 */
public final class VoidDrops {
    private VoidDrops() {}

    /** Mobs whose drops are being handed to a player right now. */
    private static final Map<LivingEntity, Player> HANDING = new WeakHashMap<>();

    /** What a drop meets falling from a height: the cells from {@code fromY} down to {@code floorY}, read by height. */
    public interface Column {
        /** 0 open, 1 solid (the drop lands), 2 lava (the drop burns). */
        int at(int y);
    }

    /** Whether a drop falling from {@code fromY} lands on a solid block before it meets lava or the world's floor. */
    public static boolean lands(int fromY, int floorY, Column column) {
        for (int y = fromY; y >= floorY; y--) {
            int cell = column.at(y);
            if (cell == 2) return false;
            if (cell == 1) return true;
        }
        return false;
    }

    /** Whether a drop let go at {@code at} lands safely. */
    public static boolean lands(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        int from = Math.min((int) Math.floor(at.y), level.getMaxY());
        return lands(from, level.getMinY(), y -> {
            pos.set(x, y, z);
            if (level.getFluidState(pos).is(FluidTags.LAVA)) return 2;
            return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() ? 0 : 1;
        });
    }

    /** Where the drops go and who, if anyone, they are handed to; {@code at} null when they stay where it died. */
    public record Landing(@Nullable Vec3 at, @Nullable Player handTo) {
        static final Landing STAY = new Landing(null, null);
    }

    /** Where {@code mob}'s drops, let go at {@code from}, should go when {@code source} killed it. */
    public static Landing landing(ServerLevel level, LivingEntity mob, DamageSource source, Vec3 from) {
        if (lands(level, from)) return Landing.STAY;
        if (!(source.getEntity() instanceof Player killer) || killer.level() != level || !killer.isAlive()) return Landing.STAY;
        if (lands(level, killer.position())) return new Landing(killer.position(), null);
        if (killer.onGround() && killer.mainSupportingBlockPos.isPresent()) {
            BlockPos under = killer.mainSupportingBlockPos.get();
            Vec3 top = Vec3.atBottomCenterOf(under.above());
            if (lands(level, top)) return new Landing(top, null);
        }
        return new Landing(killer.position(), killer);
    }

    /**
     * Runs {@code drop} (death drops, or anything else it lets go of on death) with {@code mob} stood where
     * {@link #landing} says, handing item drops to the player when it says so.
     */
    public static void drop(ServerLevel level, LivingEntity mob, DamageSource source, Runnable drop) {
        Landing landing = landing(level, mob, source, mob.position());
        if (landing.at() == null) {
            drop.run();
            return;
        }
        Vec3 was = mob.position();
        mob.setPos(landing.at().x, landing.at().y, landing.at().z);
        if (landing.handTo() != null) HANDING.put(mob, landing.handTo());
        try {
            drop.run();
        } finally {
            HANDING.remove(mob);
            mob.setPos(was.x, was.y, was.z);
        }
    }

    /**
     * Called for each item {@code mob} drops: true when it went to a player's inventory instead (what doesn't fit is
     * thrown at the player's feet).
     */
    public static boolean handed(LivingEntity mob, ItemStack stack) {
        Player to = HANDING.get(mob);
        if (to == null || stack.isEmpty()) return false;
        ItemStack rest = stack.copy();
        if (!to.getInventory().add(rest) && !rest.isEmpty() && to.level() instanceof ServerLevel level) to.spawnAtLocation(level, rest);
        return true;
    }
}
