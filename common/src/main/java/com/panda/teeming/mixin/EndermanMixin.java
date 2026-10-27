package com.panda.teeming.mixin;

import com.panda.teeming.mob.end.Hollowmind;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An enderman ridden by a Hollowmind takes no harm from water: rain, a river and a splash of water leave it alone (and
 * so never make it teleport away), for as long as the parasite rides it.
 */
@Mixin(Enderman.class)
public abstract class EndermanMixin {
    @Inject(method = "isSensitiveToWater", at = @At("HEAD"), cancellable = true)
    private void teeming$riddenEndermanKeepsDry(CallbackInfoReturnable<Boolean> cir) {
        if (Hollowmind.keepsDry((Enderman) (Object) this)) cir.setReturnValue(false);
    }
}
