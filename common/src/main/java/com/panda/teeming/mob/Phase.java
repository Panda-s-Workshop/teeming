package com.panda.teeming.mob;

import java.util.Locale;

/**
 * One step of a mob's mechanic. Each mob declares an enum of these whose first constant is the idle phase; the
 * ordinal is what gets synced to clients. {@link #step()} names the step in {@link MechanicEvent}s ("tell", "charge").
 */
public interface Phase {
    String name();

    int ordinal();

    default String step() {
        return name().toLowerCase(Locale.ROOT);
    }
}
