package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.effect.HeavyEffect;
import com.panda.teeming.effect.NulledEffect;
import com.panda.teeming.effect.SilencedEffect;
import com.panda.teeming.effect.SunderEffect;
import com.panda.teeming.effect.SwallowedEffect;
import com.panda.teeming.effect.VoidRotEffect;
import com.panda.teeming.platform.registry.DeferredHolder;
import com.panda.teeming.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** The mod's status effects; each one's name, description and icon are resources keyed by its id. */
public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, TeemingMod.MODID);

    public static final DeferredHolder<MobEffect, SunderEffect> SUNDER = EFFECTS.register("sunder", SunderEffect::new);
    public static final DeferredHolder<MobEffect, SilencedEffect> SILENCED = EFFECTS.register("silenced", SilencedEffect::new);
    public static final DeferredHolder<MobEffect, VoidRotEffect> VOID_ROT = EFFECTS.register("void_rot", VoidRotEffect::new);
    public static final DeferredHolder<MobEffect, HeavyEffect> HEAVY = EFFECTS.register("heavy", HeavyEffect::new);
    public static final DeferredHolder<MobEffect, NulledEffect> NULLED = EFFECTS.register("nulled", NulledEffect::new);
    public static final DeferredHolder<MobEffect, SwallowedEffect> SWALLOWED = EFFECTS.register("swallowed", SwallowedEffect::new);

    private ModEffects() {}

    /**
     * Gives {@code target} one level of {@code effect} for {@code ticks}, with particles and an icon, credited to
     * {@code source}; a longer one already on it is kept. False if the target refuses it.
     */
    public static boolean give(LivingEntity target, DeferredHolder<MobEffect, ? extends MobEffect> effect, int ticks, @Nullable Entity source) {
        if (target.level().isClientSide()) return false;
        return target.addEffect(new MobEffectInstance(effect.holder(), ticks, 0), source);
    }
}
