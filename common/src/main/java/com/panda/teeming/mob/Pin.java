package com.panda.teeming.mob;

import net.minecraft.world.phys.Vec3;

/**
 * How firmly a mob is held where it is: a disguise that stands stock still, a grip on a ceiling, a point stuck in the
 * ground. A held mob is neither pushed nor knocked back; the base classes read {@link TeemingCreature#pin()} for it.
 */
public enum Pin {
    /** Moves as any mob does. */
    FREE,
    /** Rooted to the spot: it only falls. */
    ROOTED,
    /** Held fast: nothing moves it, not even gravity. */
    FIXED;

    /** The part of {@code delta} this hold lets through. */
    public Vec3 limit(Vec3 delta) {
        return switch (this) {
            case FREE -> delta;
            case ROOTED -> new Vec3(0.0, Math.min(0.0, delta.y), 0.0);
            case FIXED -> Vec3.ZERO;
        };
    }

    public boolean held() {
        return this != FREE;
    }
}
