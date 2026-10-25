package com.panda.teeming.mob;

import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A value a mob builds up, from blows it takes or from time: heat, a soul charge. It runs from 0 to {@code max}; its
 * glow stage follows it (synced to clients through {@link MobCore#setStage}); a release fires while it sits at its
 * threshold; afterwards {@link #reset} drops it to a set level; and while it is sealed nothing fills it. It lives on
 * the server and is saved with the mob under its own key.
 *
 * <p>The owner keeps its own numbers and rules: what fills it and by how much ({@link #fill}, {@link #every}, a
 * {@link Rate} for a gauge that fills faster as it climbs), when it is sealed, how the glow reads it, what a change
 * sets off ({@link #onChange}) and what the release does ({@link #releaseAt}). A drain, a cooling hit or a test sets it
 * directly with {@link #set}, which never asks whether it is sealed.
 */
public final class Gauge {
    /** The glow stage (0..15) for a value. */
    public interface Stages {
        int of(float value);
    }

    /** How much a point of fill counts at a value: 1 for one for one, 2 for twice as fast. */
    public interface Rate {
        float at(float value);
    }

    /** Server, after every {@link #set}: the value before it; the gauge already holds the new one. */
    public interface Change {
        void changed(float before);
    }

    private final Mob owner;
    private final MobCore core;
    private final String key;
    private final float max;
    private float start;
    private boolean whole;
    private float resetTo;
    private Stages stages = v -> 0;
    private BooleanSupplier glows = () -> true;
    private BooleanSupplier sealed = () -> false;
    private Rate rate = v -> 1.0F;
    private int every;
    private float perEvery;
    private int clock;
    private float threshold = Float.NaN;
    private Runnable release = () -> {};
    private Change change = before -> {};
    private float value;

    /** A gauge from 0 to {@code max} on {@code owner}, saved under {@code key}. It starts empty and resets to 0. */
    public <M extends Mob & TeemingCreature> Gauge(M owner, String key, float max) {
        this.owner = owner;
        this.core = owner.core();
        this.key = key;
        this.max = max;
    }

    /** The value it spawns with, and reads back from a save that has none. */
    public Gauge from(float value) {
        start = value;
        this.value = clamp(value);
        return this;
    }

    /** Whole steps only (a value is rounded down), saved as an int. */
    public Gauge whole() {
        whole = true;
        value = clamp(value);
        return this;
    }

    /** How its glow reads the value. */
    public Gauge stages(Stages stages) {
        this.stages = stages;
        return this;
    }

    /** The glow follows the value only while this holds (a phase may own the glow meanwhile); always by default. */
    public Gauge glowsWhen(BooleanSupplier glows) {
        this.glows = glows;
        return this;
    }

    /** {@link #fill} and the clock add nothing while this holds. */
    public Gauge sealedWhen(BooleanSupplier sealed) {
        this.sealed = sealed;
        return this;
    }

    /** How much each point of {@link #fill} counts, read at the value before the fill. */
    public Gauge rate(Rate rate) {
        this.rate = rate;
        return this;
    }

    /** Fills by {@code amount} every {@code ticks} ticks that {@link #tick} is told it runs. */
    public Gauge every(int ticks, float amount) {
        every = ticks;
        perEvery = amount;
        return this;
    }

    /** At or above {@code threshold}, every {@link #set} ends by calling {@code release} on the server. */
    public Gauge releaseAt(float threshold, Runnable release) {
        this.threshold = threshold;
        this.release = release;
        return this;
    }

    /** Where {@link #reset} drops it after a release. */
    public Gauge resetTo(float level) {
        resetTo = level;
        return this;
    }

    /** Called on the server after every {@link #set}, before the release check. */
    public Gauge onChange(Change change) {
        this.change = change;
        return this;
    }

    // ---- reading -----------------------------------------------------------------------------------------------------

    public float value() {
        return value;
    }

    public float max() {
        return max;
    }

    public boolean sealed() {
        return sealed.getAsBoolean();
    }

    /** True at or above the release threshold. */
    public boolean full() {
        return value >= threshold;
    }

    /** The glow stage its value reads as. */
    public int stage() {
        return stages.of(value);
    }

    /** True if a change from {@code before} to {@code now} climbed to {@code at} or past it. */
    public static boolean rose(float before, float now, float at) {
        return before < at && now >= at;
    }

    // ---- changing ----------------------------------------------------------------------------------------------------

    /**
     * Sets the value, kept within 0..max. On the server the glow then follows it (while it glows), the change hook runs,
     * and at the threshold the release fires.
     */
    public void set(float to) {
        float before = value;
        value = clamp(to);
        if (owner.level().isClientSide()) return;
        if (glows.getAsBoolean()) core.setStage(stages.of(value));
        change.changed(before);
        if (full()) release.run();
    }

    /** Adds {@code amount} times the rate, unless the amount is nothing or the gauge is sealed; true if it did. */
    public boolean fill(float amount) {
        if (amount <= 0.0F || sealed()) return false;
        set(value + amount * rate.at(value));
        return true;
    }

    /** Server, once a tick: counts toward the timed fill while {@code running}, and fills when the count is up. */
    public void tick(boolean running) {
        if (!running || every <= 0) return;
        if (++clock >= every) {
            clock = 0;
            fill(perEvery);
        }
    }

    /** After a release: back to the reset level. */
    public void reset() {
        set(resetTo);
    }

    /** Shows its glow stage now, whatever {@link #glowsWhen} says (a phase ending, the mob being built). */
    public void show() {
        core.setStage(stages.of(value));
    }

    // ---- saving ------------------------------------------------------------------------------------------------------

    public void save(ValueOutput output) {
        if (whole) output.putInt(key, (int) value);
        else output.putFloat(key, value);
    }

    /** Reads the value back (the start value if the save has none) and shows its glow; nothing fires. */
    public void load(ValueInput input) {
        value = clamp(whole ? input.getIntOr(key, (int) start) : input.getFloatOr(key, start));
        show();
    }

    private float clamp(float v) {
        float c = Math.clamp(v, 0.0F, max);
        return whole ? (float) Math.floor(c) : c;
    }
}
