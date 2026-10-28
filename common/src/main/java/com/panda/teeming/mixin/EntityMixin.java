package com.panda.teeming.mixin;

import com.panda.teeming.charm.Charms;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Burning, where vanilla deals it: the one burn a second (or faster, inside a fire source) that a burning entity
 * takes in its own tick. A Quench Charm holder burning with the fire a burning patch lit takes half of each.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    /** The burn's damage, the only hurt the base tick deals. */
    @ModifyArg(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            index = 2)
    private float teeming$quenchHalvesPatchBurn(float amount) {
        return (Object) this instanceof LivingEntity burning ? Charms.patchBurn(burning, amount) : amount;
    }
}
