package com.panda.teeming.mixin;

import com.panda.teeming.effect.VoidRotEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A rotting player gets no natural regeneration. The food bar heals only a player it finds hurt; while Void Rot lasts
 * it finds none, so it neither heals nor spends food on healing. Hunger and starvation run as usual.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isHurt()Z"))
    private boolean teeming$noNaturalRegenWhileRotting(ServerPlayer player) {
        return player.isHurt() && !VoidRotEffect.rotting(player);
    }
}
