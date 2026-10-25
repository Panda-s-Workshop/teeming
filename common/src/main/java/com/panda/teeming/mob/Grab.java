package com.panda.teeming.mob;

import com.panda.teeming.effect.EffectNumbers;
import com.panda.teeming.effect.SwallowedEffect;
import com.panda.teeming.registry.ModEffects;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Holding a victim: a swallow, a coil, a grip. The holder keeps one of these and moves the victim (or itself) each
 * tick. The victim shows the Swallowed status while it is held: given by {@link #take}, renewed by every
 * {@link #holding} check that still holds, taken away by {@link #release}. The status lifts by itself once no grab has
 * claimed its bearer for {@link EffectNumbers#SWALLOWED_GRACE} ticks, so every way the hold ends (let go, the victim
 * dying, leaving the level, going creative or getting away, the holder dying or unloading, a relog) leaves nothing on
 * the victim. The holder should neither push its victim nor be pushed by it ({@link #holds}).
 */
public final class Grab {
    /** Who holds whom, server side: each victim and the grab that last claimed it (weakly, so neither is kept alive). */
    private static final Map<LivingEntity, WeakReference<Grab>> HELD = new WeakHashMap<>();

    private final float maxWidth;
    private final float maxHeight;
    private final double escapeRange;
    private @Nullable LivingEntity victim;
    private long claimed;                 // game time of the latest take or holding check that held

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

    /** Takes hold of {@code prey}, pulling it off anything it rides; it shows as Swallowed. */
    public void take(LivingEntity prey) {
        victim = prey;
        prey.stopRiding();
        claim(prey);
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
        if (v.position().distanceToSqr(anchor) > escapeRange * escapeRange) return false;
        claim(v);
        return true;
    }

    /** Lets go; returns who was held, or null. The victim is no longer Swallowed, unless another grab has it now. */
    public @Nullable LivingEntity release() {
        LivingEntity v = victim;
        victim = null;
        if (v != null && !v.level().isClientSide()) {
            WeakReference<Grab> by = HELD.get(v);
            if (by != null && by.get() == this) {
                HELD.remove(v);
                v.removeEffect(ModEffects.SWALLOWED.holder());
            }
        }
        return v;
    }

    /** Marks {@code v} as held by this grab now, and shows it as Swallowed (again, if milk or a command took it off). */
    private void claim(LivingEntity v) {
        if (v.level().isClientSide()) return;
        claimed = v.level().getGameTime();
        WeakReference<Grab> by = HELD.get(v);
        if (by == null || by.get() != this) HELD.put(v, new WeakReference<>(this));
        if (!v.hasEffect(ModEffects.SWALLOWED.holder())) v.addEffect(SwallowedEffect.instance());
    }

    /**
     * True while a grab still holds {@code v}: it took or last checked it within {@link EffectNumbers#SWALLOWED_GRACE}
     * ticks. The Swallowed status lifts as soon as this turns false.
     */
    public static boolean isHeld(LivingEntity v) {
        WeakReference<Grab> by = HELD.get(v);
        Grab grab = by == null ? null : by.get();
        return grab != null && grab.victim == v && v.level().getGameTime() - grab.claimed <= EffectNumbers.SWALLOWED_GRACE;
    }

    /** Hurts the victim past its hurt cooldown, which would otherwise eat a regular bite or squeeze now and then. */
    public static boolean hurt(ServerLevel level, LivingEntity v, DamageSource source, float amount) {
        v.damageCooldownTime = 0;
        return v.hurtServer(level, source, amount);
    }
}
