package com.panda.teeming.mixin;

import com.panda.teeming.effect.NulledEffect;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Nulled creature's enchantments do nothing (see {@link NulledEffect}). Every enchantment effect is looked up here:
 * <ul>
 * <li>the two loops over an item's enchantments (every effect component reads one of them) and the tag test read only
 * the curses of a nulled creature's gear;</li>
 * <li>the single-level lookup (Looting, Fortune) answers 0 for anything but a curse on it;</li>
 * <li>lookups whose item has left the wearer's hands (an arrow's bow, a thrown trident) ask about whoever shot it:
 * damage and knockback by the damage's attacker, projectile effects by the projectile's owner;</li>
 * <li>Mending finds no item to repair on a nulled creature;</li>
 * <li>the two lookups a client also runs for its own player (Quick Charge, Riptide) ask the player's status;</li>
 * <li>and the per-tick enchantment step keeps the attribute and location effects in step with the status.</li>
 * </ul>
 * A Memory Charm holder's gear reads at half its levels instead ({@link NulledEffect#view}), so the lookups that ask
 * about the bearer step aside for it ({@link NulledEffect#blanked}) and let the halved gear answer; Quick Charge and
 * Riptide stay off for it too, as both sides must agree on them.
 * The methods are the same on both loaders (NeoForge only changes how the loops read the enchantments), so nothing here
 * depends on a loader.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
    @ModifyVariable(method = {
            "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
            "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
            "hasTag(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/tags/TagKey;)Z"
    }, at = @At("HEAD"), argsOnly = true)
    private static ItemStack teeming$nulledGearShowsOnlyCurses(ItemStack stack) {
        return NulledEffect.view(stack);
    }

    @Inject(method = "getItemEnchantmentLevel", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledLevel(Holder<Enchantment> enchantment, ItemInstance item, CallbackInfoReturnable<Integer> cir) {
        if (item instanceof ItemStack stack && !enchantment.is(EnchantmentTags.CURSE) && NulledEffect.hides(stack)) cir.setReturnValue(NulledEffect.level(stack, enchantment));
    }

    @Inject(method = "getRandomItemWith", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledMendsNothing(DataComponentType<?> component, LivingEntity entity, Predicate<ItemStack> filter,
                                                   CallbackInfoReturnable<Optional<EnchantedItemInUse>> cir) {
        if (NulledEffect.blanked(entity)) cir.setReturnValue(Optional.empty());
    }

    /** Sharpness, Smite, Power, Impaling, Density, Breach, Knockback and Punch: by the attacker, wherever the weapon is. */
    @Inject(method = {"modifyDamage", "modifyFallBasedDamage", "modifyArmorEffectiveness", "modifyKnockback"}, at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledAttacker(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float amount,
                                               CallbackInfoReturnable<Float> cir) {
        if (NulledEffect.blanked(source.getEntity())) cir.setReturnValue(amount);
    }

    /** Fire Aspect, Channeling, Wind Burst: by the attacker. */
    @Inject(method = "doPostAttackEffectsWithItemSource", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledAfterHit(ServerLevel level, Entity victim, DamageSource source, ItemStack weapon, CallbackInfo ci) {
        if (NulledEffect.blanked(source.getEntity())) ci.cancel();
    }

    @Inject(method = "doPostAttackEffectsWithItemSourceOnBreak", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledAfterHitOnBreak(ServerLevel level, Entity victim, DamageSource source, ItemStack weapon,
                                                      Consumer<ItemStack> onBreak, CallbackInfo ci) {
        if (NulledEffect.blanked(source.getEntity())) ci.cancel();
    }

    /** Flame: by the shooter. */
    @Inject(method = "onProjectileSpawned", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledShot(ServerLevel level, ItemStack weapon, Projectile projectile, Consumer<ItemStack> onBreak, CallbackInfo ci) {
        if (NulledEffect.blanked(projectile.getOwner())) ci.cancel();
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledHitBlock(ServerLevel level, ItemStack weapon, LivingEntity owner, Entity entity, EquipmentSlot slot, Vec3 at,
                                               BlockState state, Consumer<ItemStack> onBreak, CallbackInfo ci) {
        if (NulledEffect.blanked(owner) || NulledEffect.blankedShooter(entity)) ci.cancel();
    }

    /** Multishot. */
    @Inject(method = "processProjectileCount", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledCount(ServerLevel level, ItemStack weapon, Entity shooter, int count, CallbackInfoReturnable<Integer> cir) {
        if (NulledEffect.blankedShooter(shooter)) cir.setReturnValue(count);
    }

    @Inject(method = "processProjectileSpread", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledSpread(ServerLevel level, ItemStack weapon, Entity shooter, float spread, CallbackInfoReturnable<Float> cir) {
        if (NulledEffect.blankedShooter(shooter)) cir.setReturnValue(spread);
    }

    /** Loyalty: a thrown trident comes back only to a thrower out of the ring. */
    @Inject(method = "getTridentReturnToOwnerAcceleration", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledLoyalty(ServerLevel level, ItemStack trident, Entity thrown, CallbackInfoReturnable<Integer> cir) {
        if (NulledEffect.blankedShooter(thrown)) cir.setReturnValue(0);
    }

    /** Luck of the Sea. */
    @Inject(method = "getFishingLuckBonus", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledLuck(ServerLevel level, ItemStack rod, Entity fisher, CallbackInfoReturnable<Integer> cir) {
        if (NulledEffect.blankedShooter(fisher)) cir.setReturnValue(0);
    }

    /** Lure. */
    @Inject(method = "getFishingTimeReduction", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledLure(ServerLevel level, ItemStack rod, Entity fisher, CallbackInfoReturnable<Float> cir) {
        if (NulledEffect.blankedShooter(fisher)) cir.setReturnValue(0.0F);
    }

    /** Quick Charge, on both sides. */
    @Inject(method = "modifyCrossbowChargingTime", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledCharge(ItemStack crossbow, LivingEntity user, float time, CallbackInfoReturnable<Float> cir) {
        if (NulledEffect.nulled(user)) cir.setReturnValue(time);
    }

    /** Riptide, on both sides: a nulled player throws the trident instead. */
    @Inject(method = "getTridentSpinAttackStrength", at = @At("HEAD"), cancellable = true)
    private static void teeming$nulledRiptide(ItemStack trident, LivingEntity user, CallbackInfoReturnable<Float> cir) {
        if (NulledEffect.nulled(user)) cir.setReturnValue(0.0F);
    }

    /** Every creature's enchantment tick: the attribute and location effects follow the status in and out. */
    @Inject(method = "tickEffects", at = @At("HEAD"))
    private static void teeming$nulledInStep(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        NulledEffect.sync(level, entity);
    }
}
