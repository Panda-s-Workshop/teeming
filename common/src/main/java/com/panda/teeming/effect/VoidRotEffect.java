package com.panda.teeming.effect;

import com.panda.teeming.registry.ModEffects;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Void Rot (harmful): the body won't mend. While it lasts a player gets no natural regeneration from a full food bar
 * (and spends no food on it), and every other healing (Regeneration, Instant Health, a beacon, any mod's heal) lands
 * at {@link EffectNumbers#VOID_ROT_HEALING} of its amount. Both rules are checked where vanilla heals, in
 * {@code LivingEntity.heal} and the food bar's tick.
 */
public final class VoidRotEffect extends TeemingEffect {
    public static final int COLOR = 0x4B2A6B;

    public VoidRotEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /** True while {@code e} rots. */
    public static boolean rotting(LivingEntity e) {
        return e.hasEffect(ModEffects.VOID_ROT.holder());
    }

    /** What {@code amount} of healing comes to on {@code e}: halved while it rots. */
    public static float healing(LivingEntity e, float amount) {
        return amount > 0.0F && rotting(e) ? amount * EffectNumbers.VOID_ROT_HEALING : amount;
    }
}
