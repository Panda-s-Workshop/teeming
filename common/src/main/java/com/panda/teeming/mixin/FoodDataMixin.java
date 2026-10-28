package com.panda.teeming.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.panda.teeming.effect.VoidRotEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A rotting player gets no natural regeneration. The food bar heals only a player it finds hurt; while Void Rot lasts
 * it finds none, so it neither heals nor spends food on healing. Hunger and starvation run as usual. The check's
 * result is modified rather than the call replaced, so another mod changing the same call still works beside it.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isHurt()Z"))
    private boolean teeming$noNaturalRegenWhileRotting(boolean hurt, ServerPlayer player) {
        return hurt && !VoidRotEffect.rotting(player);
    }
}
