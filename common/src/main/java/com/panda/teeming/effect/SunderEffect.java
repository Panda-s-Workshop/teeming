package com.panda.teeming.effect;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.registry.ModEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/**
 * Sunder (harmful): cracked armour. Each level takes {@link EffectNumbers#SUNDER_ARMOR} armour (amplifier 0, 1, 2 are
 * 1, 2, 3 stacks: 4, 8, 12), as one attribute modifier the effect owns, so it comes and goes with the effect: it is
 * saved and loaded with it, lifted when it runs out, is milked off, and lifts on death.
 */
public final class SunderEffect extends TeemingEffect {
    public static final int COLOR = 0xA3202C;
    /** The armour modifier's id. */
    public static final Identifier MODIFIER = TeemingMod.id("effect.sunder");

    public SunderEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        addAttributeModifier(Attributes.ARMOR, MODIFIER, -EffectNumbers.SUNDER_ARMOR, AttributeModifier.Operation.ADD_VALUE);
    }

    /** Stacks of Sunder on {@code e} now: its level, 0 with none. */
    public static int stacks(LivingEntity e) {
        MobEffectInstance now = e.getEffect(ModEffects.SUNDER.holder());
        return now == null ? 0 : now.getAmplifier() + 1;
    }

    /**
     * Adds a stack to {@code victim}, at most {@code maxStacks}, and starts the whole Sunder over at {@code ticks}:
     * every stack shares one timer that each new stack restarts. False if the victim refuses the effect.
     */
    public static boolean addStack(LivingEntity victim, @Nullable Entity by, int maxStacks, int ticks) {
        if (victim.level().isClientSide()) return false;
        int level = Math.min(maxStacks, stacks(victim) + 1);
        return victim.addEffect(new MobEffectInstance(ModEffects.SUNDER.holder(), ticks, level - 1), by);
    }
}
