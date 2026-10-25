package com.panda.teeming.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Holding a victim: a swallow, a coil, a grip. The holder keeps one of these and moves the victim (or itself) each
 * tick. Nothing is stored on the victim, so every way the hold ends (let go, the victim dying, leaving the level, going
 * creative or getting away, the holder dying or unloading) simply stops it. The holder should neither push its victim
 * nor be pushed by it ({@link #holds}).
 */
public final class Grab {
    private final float maxWidth;
    private final float maxHeight;
    private final double escapeRange;
    private @Nullable LivingEntity victim;

    /** Prey no wider than {@code maxWidth} nor taller than {@code maxHeight}; found farther than {@code escapeRange}, it got away. */
    public Grab(float maxWidth, float maxHeight, double escapeRange) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.escapeRange = escapeRange;
    }

    /** Prey that fits: alive, in the holder's level, small enough, and not a player in creative or spectator. */
    public boolean fits(Entity holder, @Nullable LivingEntity e) {
        if (e == null || !e.isAlive() || e.isRemoved() || e == holder || e.level() != holder.level()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return e.getBbWidth() <= maxWidth && e.getBbHeight() <= maxHeight;
    }

    /** Takes hold of {@code prey}, pulling it off anything it rides. */
    public void take(LivingEntity prey) {
        victim = prey;
        prey.stopRiding();
    }

    public @Nullable LivingEntity victim() {
        return victim;
    }

    /** True if {@code e} is the one held. */
    public boolean holds(@Nullable Entity e) {
        return e != null && e == victim;
    }

    /** Still holding: alive, here, not gone creative, and within the escape range of {@code anchor}. */
    public boolean holding(Entity holder, Vec3 anchor) {
        LivingEntity v = victim;
        if (v == null || !v.isAlive() || v.isRemoved() || v.level() != holder.level()) return false;
        if (v instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return v.position().distanceToSqr(anchor) <= escapeRange * escapeRange;
    }

    /** Lets go; returns who was held, or null. */
    public @Nullable LivingEntity release() {
        LivingEntity v = victim;
        victim = null;
        return v;
    }

    /** Hurts the victim past its hurt cooldown, which would otherwise eat a regular bite or squeeze now and then. */
    public static boolean hurt(ServerLevel level, LivingEntity v, DamageSource source, float amount) {
        v.damageCooldownTime = 0;
        return v.hurtServer(level, source, amount);
    }
}
