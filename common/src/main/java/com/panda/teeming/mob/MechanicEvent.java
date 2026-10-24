package com.panda.teeming.mob;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Posted (server side) before every step of a Teeming mob's mechanic: entering a phase, firing a projectile, applying
 * an effect. A listener that calls {@link #cancel()} stops that step, so packs and scripts can veto any part of any
 * mob. The bus is plain common code, the same on both loaders.
 */
public final class MechanicEvent {
    private static final List<Consumer<MechanicEvent>> LISTENERS = new CopyOnWriteArrayList<>();

    private final Mob mob;
    private final String step;
    private final @Nullable LivingEntity target;
    private boolean cancelled;

    private MechanicEvent(Mob mob, String step, @Nullable LivingEntity target) {
        this.mob = mob;
        this.step = step;
        this.target = target;
    }

    /** Adds a listener for every mechanic step of every Teeming mob. */
    public static void listen(Consumer<MechanicEvent> listener) {
        LISTENERS.add(listener);
    }

    /** Removes a listener added with {@link #listen}. */
    public static void unlisten(Consumer<MechanicEvent> listener) {
        LISTENERS.remove(listener);
    }

    /** Posts a step; true when no listener cancelled it. */
    public static boolean post(Mob mob, String step, @Nullable LivingEntity target) {
        if (LISTENERS.isEmpty()) return true;
        MechanicEvent event = new MechanicEvent(mob, step, target);
        for (Consumer<MechanicEvent> listener : LISTENERS) listener.accept(event);
        return !event.cancelled;
    }

    public Mob mob() {
        return mob;
    }

    public String step() {
        return step;
    }

    public @Nullable LivingEntity target() {
        return target;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }
}
