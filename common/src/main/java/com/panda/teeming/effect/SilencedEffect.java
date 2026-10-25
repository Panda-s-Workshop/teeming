package com.panda.teeming.effect;

import com.panda.teeming.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Silenced (harmful): no item use. A silenced player's right click does nothing with any item (eating, drinking, a
 * bow, a shield, an ender pearl, anything thrown): every item use starts in {@code ItemStack.use}, which refuses it on
 * both sides while the effect lasts. An item already in use when it lands (a raised shield, a meal half eaten, a drawn
 * bow) is dropped at once, and anything that starts using an item another way (a mob drawing its bow) is stopped on
 * its next tick. Placing blocks and using an item on a block are not item use and still work.
 */
public final class SilencedEffect extends TeemingEffect {
    public static final int COLOR = 0x7FB8C4;

    public SilencedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /** True while {@code e} may use no item. */
    public static boolean silenced(LivingEntity e) {
        return e.hasEffect(ModEffects.SILENCED.holder());
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity.isUsingItem()) entity.stopUsingItem();
    }

    @Override
    protected boolean tick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity.isUsingItem()) entity.stopUsingItem();
        return true;
    }
}
