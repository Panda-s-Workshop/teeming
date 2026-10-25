package com.panda.teeming.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * A Teeming status effect. It checks its bearer every server tick and lifts the tick the bearer dies (vanilla would
 * keep it, and any attribute change it makes, through the death animation), so nothing of it outlives a death.
 */
public abstract class TeemingEffect extends MobEffect {
    protected TeemingEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public final boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        return entity.isAlive() && tick(level, entity, amplifier);
    }

    /** Runs every server tick while the effect lasts on a living bearer; returning false lifts it at once. */
    protected boolean tick(ServerLevel level, LivingEntity entity, int amplifier) {
        return true;
    }
}
