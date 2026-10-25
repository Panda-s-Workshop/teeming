package com.panda.teeming.effect;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.registry.ModEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Heavy (harmful): weighed down. Gravity x{@link EffectNumbers#HEAVY_GRAVITY} and jump strength x0, as attribute
 * modifiers the effect owns (both attributes reach the client, so a player's own movement feels it), and Jump Boost
 * adds nothing while it lasts: no jumping at all. It cancels Levitation and Slow Falling when it lands and refuses
 * both while it lasts.
 */
public final class HeavyEffect extends TeemingEffect {
    public static final int COLOR = 0x5A5E70;
    public static final Identifier GRAVITY = TeemingMod.id("effect.heavy.gravity");
    public static final Identifier JUMP = TeemingMod.id("effect.heavy.jump");

    public HeavyEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        addAttributeModifier(Attributes.GRAVITY, GRAVITY, EffectNumbers.HEAVY_GRAVITY - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(Attributes.JUMP_STRENGTH, JUMP, EffectNumbers.HEAVY_JUMP - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** True while {@code e} is weighed down. */
    public static boolean heavy(LivingEntity e) {
        return e.hasEffect(ModEffects.HEAVY.holder());
    }

    /** True if {@code e} must refuse {@code incoming}: Levitation and Slow Falling never take hold of the heavy. */
    public static boolean refuses(LivingEntity e, MobEffectInstance incoming) {
        return (incoming.is(MobEffects.LEVITATION) || incoming.is(MobEffects.SLOW_FALLING)) && heavy(e);
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;
        entity.removeEffect(MobEffects.LEVITATION);
        entity.removeEffect(MobEffects.SLOW_FALLING);
    }
}
