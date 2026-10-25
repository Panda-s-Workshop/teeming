package com.panda.teeming.mixin;

import com.panda.teeming.effect.HeavyEffect;
import com.panda.teeming.effect.VoidRotEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The status effects' rules that live where vanilla decides them: Void Rot halves every heal, and Heavy refuses
 * Levitation and Slow Falling and takes away Jump Boost's lift.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    /** All healing passes through here (regeneration, potions, food, beacons), so Void Rot halves it once, here. */
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float teeming$voidRotHalvesHealing(float amount) {
        return VoidRotEffect.healing((LivingEntity) (Object) this, amount);
    }

    /** A Heavy creature takes neither Levitation nor Slow Falling, from any source. */
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void teeming$heavyRefusesLift(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        if (HeavyEffect.refuses((LivingEntity) (Object) this, instance)) cir.setReturnValue(false);
    }

    /** Heavy leaves no jump at all: its jump-strength modifier zeroes the jump, and Jump Boost adds nothing on top. */
    @Inject(method = "getJumpBoostPower", at = @At("HEAD"), cancellable = true)
    private void teeming$heavyHasNoJumpBoost(CallbackInfoReturnable<Float> cir) {
        if (HeavyEffect.heavy((LivingEntity) (Object) this)) cir.setReturnValue(0.0F);
    }
}
