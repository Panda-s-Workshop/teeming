package com.panda.teeming.effect;

import com.panda.teeming.mob.Grab;
import com.panda.teeming.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Swallowed (neutral): a status that shows a creature is held (swallowed, coiled, gripped). It does nothing itself;
 * the holder's {@link Grab} gives it on taking hold and takes it away on letting go. It has no length of its own: it
 * lifts by itself as soon as no holder still claims its bearer, so it never outlives a hold, a holder or a relog.
 */
public final class SwallowedEffect extends TeemingEffect {
    public static final int COLOR = 0x5E7A3A;

    public SwallowedEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }

    /** The status as a holder gives it: no end of its own, no particles, an icon. */
    public static MobEffectInstance instance() {
        return new MobEffectInstance(ModEffects.SWALLOWED.holder(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
    }

    @Override
    protected boolean tick(ServerLevel level, LivingEntity entity, int amplifier) {
        return Grab.isHeld(entity);
    }
}
