package com.panda.teeming.effect;

/**
 * The numbers of the mod's status effects, as compile-time constants (they load no game class). The lengths are the
 * ones the mobs that cause each effect give it; a counter charm shortens them at the point it is given.
 */
public final class EffectNumbers {
    private EffectNumbers() {}

    /** Sunder: armour taken per level (amplifier 0 is one stack). */
    public static final double SUNDER_ARMOR = 4.0;
    /** Sunder: stacks a bite can build up to (amplifier 0..2). */
    public static final int SUNDER_MAX_STACKS = 3;

    /** Silenced: ticks a wail silences for (4 s). */
    public static final int SILENCED_TICKS = 80;

    /** Void Rot: ticks a bite rots for (8 s). */
    public static final int VOID_ROT_TICKS = 160;
    /** Void Rot: the share of any healing that still lands. */
    public static final float VOID_ROT_HEALING = 0.5F;

    /** Heavy: ticks a blow weighs down for (6 s). */
    public static final int HEAVY_TICKS = 120;
    /** Heavy: gravity is multiplied by this (falls land faster, knock-ups stay low). */
    public static final double HEAVY_GRAVITY = 1.5;
    /** Heavy: jump strength is multiplied by this: no jumping at all. */
    public static final double HEAVY_JUMP = 0.0;

    /**
     * Swallowed: ticks the status outlasts the holder's last check that it still holds its victim, so a holder that
     * vanished without letting go never leaves it behind.
     */
    public static final int SWALLOWED_GRACE = 20;
}
