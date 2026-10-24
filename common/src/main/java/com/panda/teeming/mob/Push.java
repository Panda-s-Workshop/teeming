package com.panda.teeming.mob;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Moving another entity from a mechanic (knock back, pull, launch). On 26.3 {@code needsSync} sends motion only to
 * the other trackers, so a pushed player is also sent their own motion.
 */
public final class Push {
    private Push() {}

    /** Sets the entity's motion to {@code motion}. */
    public static void set(Entity target, Vec3 motion) {
        target.setDeltaMovement(motion);
        sync(target);
    }

    /** Knocks the entity away from {@code from} horizontally by {@code strength} blocks/tick, plus {@code up}. */
    public static void away(Entity target, Vec3 from, double strength, double up) {
        Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        set(target, new Vec3(dir.x * strength, Math.max(target.getDeltaMovement().y, up), dir.z * strength));
    }

    public static void sync(Entity target) {
        target.needsSync = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
