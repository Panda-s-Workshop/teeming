package com.panda.teeming.mob.end;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The last spot a mob stood on solid ground, so what it owes a player is never spilled into the void below the world,
 * where the game removes items and orbs at once. A mob that dies down there spills on the last ground it stood on.
 */
final class Footing {
    private @Nullable Vec3 last;

    /** Call every tick: remembers where it stands while it stands on something. */
    void track(Entity mob) {
        if (mob.onGround()) last = mob.position();
    }

    /**
     * Where to spill: {@code wanted}, unless that lies below the world's floor; then the last ground it stood on, at the
     * same height above its feet. With no ground seen since it was loaded, {@code wanted} as it is.
     */
    Vec3 spill(Entity mob, Vec3 wanted) {
        if (wanted.y >= mob.level().getMinY() || last == null) return wanted;
        return last.add(wanted.x - mob.getX(), wanted.y - mob.getY(), wanted.z - mob.getZ());
    }
}
