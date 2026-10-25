package com.panda.teeming.mixin;

import com.panda.teeming.effect.SilencedEffect;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Silenced player uses no item. Every right-click use of an item (eating, drinking, drawing a bow, raising a shield,
 * throwing a pearl, a snowball or a potion) goes through {@code ItemStack.use} on the server and, as a prediction, on
 * the client, which knows its own effects: refusing it there stops the use on both sides and spends nothing.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void teeming$silencedUsesNoItem(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (SilencedEffect.silenced(player)) cir.setReturnValue(InteractionResult.FAIL);
    }
}
