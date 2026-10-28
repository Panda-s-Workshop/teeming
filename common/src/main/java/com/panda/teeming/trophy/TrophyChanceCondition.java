package com.panda.teeming.trophy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.panda.teeming.TeemingConfig;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Loot condition {@code teeming:trophy_chance}: passes with {@code chance} plus {@code per_level} for each level of the
 * killer's Looting, all scaled by the {@code trophies.chance} setting (1 at its default), at most 1.
 */
public record TrophyChanceCondition(float chance, float perLevel) implements LootItemCondition {
    public static final MapCodec<TrophyChanceCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(TrophyChanceCondition::chance),
            Codec.floatRange(0, 1).optionalFieldOf("per_level", 0F).forGetter(TrophyChanceCondition::perLevel)
    ).apply(i, TrophyChanceCondition::new));

    @Override
    public MapCodec<TrophyChanceCondition> codec() {
        return MAP_CODEC;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ATTACKING_ENTITY);
    }

    /** The chance for a killer with this Looting level. */
    public double chanceAt(int looting) {
        return Math.min(1.0, (chance + perLevel * looting) * TeemingConfig.trophyScale());
    }

    @Override
    public boolean test(LootContext context) {
        Entity killer = context.getOptional(LootContextParams.ATTACKING_ENTITY);
        int looting = killer instanceof LivingEntity living
                ? EnchantmentHelper.getEnchantmentLevel(context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), living)
                : 0;
        return context.getRandom().nextFloat() < chanceAt(looting);
    }
}
