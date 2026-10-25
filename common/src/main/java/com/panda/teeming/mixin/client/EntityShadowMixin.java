package com.panda.teeming.mixin.client;

import com.panda.teeming.mob.nether.PhantasmLantern;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Phantasm Lantern's illusions cast no shadow: the one sure way to tell them from the real mobs they copy. Vanilla
 * only drops the shadow of invisible entities, so it is cleared here once the render state is complete.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityShadowMixin {
    @Inject(method = "finalizeRenderState", at = @At("TAIL"))
    private void teeming$illusionsCastNoShadow(Entity entity, EntityRenderState state, CallbackInfo ci) {
        if (!PhantasmLantern.castsShadow(entity)) {
            state.shadowRadius = 0.0F;
            state.shadowPieces.clear();
        }
    }
}
