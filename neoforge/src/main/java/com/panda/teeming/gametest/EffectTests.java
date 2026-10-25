package com.panda.teeming.gametest;

import com.panda.teeming.effect.SunderEffect;
import com.panda.teeming.effect.SwallowedEffect;
import com.panda.teeming.mob.overworld.MireGulper;
import com.panda.teeming.platform.registry.DeferredHolder;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's five status effects, each against plain values: Sunder takes 4 armour a level; Silenced blocks a real
 * survival player's item use; Void Rot stops natural regeneration and halves healing; Heavy makes gravity x1.5, leaves
 * no jump and refuses Levitation and Slow Falling; Swallowed shows exactly while a grabber holds its victim. Every
 * effect lifts on death, and what is saved with a creature loads back whole.
 */
public final class EffectTests {
    private EffectTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("effects_registered", EffectTests::registered);
        out.accept("effect_sunder_armour", EffectTests::sunderArmour);
        out.accept("effect_sunder_stacks", EffectTests::sunderStacks);
        out.accept("effect_sunder_relog", EffectTests::sunderRelog);
        out.accept("effect_silenced_items", EffectTests::silencedItems);
        out.accept("effect_silenced_drops_use", EffectTests::silencedDropsUse);
        out.accept("effect_void_rot_healing", EffectTests::voidRotHealing);
        out.accept("effect_void_rot_regen", EffectTests::voidRotRegen);
        out.accept("effect_heavy_attributes", EffectTests::heavyAttributes);
        out.accept("effect_heavy_gravity", EffectTests::heavyGravity);
        out.accept("effect_heavy_no_jump", EffectTests::heavyNoJump);
        out.accept("effect_heavy_cancels_lift", EffectTests::heavyCancelsLift);
        out.accept("effect_swallowed_while_held", EffectTests::swallowedWhileHeld);
        out.accept("effect_swallowed_holder_gone", EffectTests::swallowedHolderGone);
        out.accept("effect_swallowed_unclaimed", EffectTests::swallowedUnclaimed);
        out.accept("effects_lift_on_death", EffectTests::liftOnDeath);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A pig with no goals (it still falls and can jump), 20 health; it leaves with the test. */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.removeFreeWill();
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
        pig.setHealth(20.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static MobEffectInstance of(DeferredHolder<MobEffect, ? extends MobEffect> effect, int ticks, int amplifier) {
        return new MobEffectInstance(effect.holder(), ticks, amplifier);
    }

    private static boolean has(LivingEntity e, DeferredHolder<MobEffect, ? extends MobEffect> effect) {
        return e.hasEffect(effect.holder());
    }

    private static double armour(LivingEntity e) {
        return e.getAttributeValue(Attributes.ARMOR);
    }

    // ---- registration ----------------------------------------------------------------------------------------------

    /** Five effects with English names and descriptions, the right categories, and Swallowed's icon without particles. */
    private static void registered(GameTestHelper helper) {
        Language lang = Language.getInstance();
        List<String> missing = new ArrayList<>();
        List<DeferredHolder<MobEffect, ? extends MobEffect>> all = List.of(ModEffects.SUNDER, ModEffects.SILENCED, ModEffects.VOID_ROT,
                ModEffects.HEAVY, ModEffects.SWALLOWED);
        for (var e : all) {
            String key = e.get().getDescriptionId();
            if (!key.equals("effect.teeming." + e.getId().getPath())) missing.add("odd key " + key);
            if (!lang.has(key)) missing.add(key);
            if (!lang.has(key + ".description")) missing.add(key + ".description");
        }
        helper.assertTrue(missing.isEmpty(), "every effect is named and described: " + missing);
        for (var e : List.of(ModEffects.SUNDER, ModEffects.SILENCED, ModEffects.VOID_ROT, ModEffects.HEAVY)) {
            helper.assertTrue(e.get().getCategory() == MobEffectCategory.HARMFUL, e.getId() + " is harmful");
        }
        helper.assertTrue(ModEffects.SWALLOWED.get().getCategory() == MobEffectCategory.NEUTRAL, "Swallowed is neutral");
        MobEffectInstance held = SwallowedEffect.instance();
        helper.assertTrue(held.isInfiniteDuration() && held.showIcon() && !held.isVisible(), "Swallowed shows an icon, no particles, no end of its own");
        helper.succeed();
    }

    // ---- Sunder ----------------------------------------------------------------------------------------------------

    /** 4 armour a level: a pig with armour 20 has 16, 12 and 8 at levels 1 to 3, and 20 again once it is gone. */
    private static void sunderArmour(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        pig.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
        List<Double> seen = new ArrayList<>();
        for (int amp = 0; amp < 3; amp++) {
            pig.removeEffect(ModEffects.SUNDER.holder());
            pig.addEffect(of(ModEffects.SUNDER, 200, amp));
            seen.add(armour(pig));
        }
        pig.removeEffect(ModEffects.SUNDER.holder());
        seen.add(armour(pig));
        helper.assertTrue(close(seen, List.of(16.0, 12.0, 8.0, 20.0)), "armour at levels 1, 2, 3 and after: " + seen);
        helper.succeed();
    }

    /**
     * Stacks add up to the cap the caller sets (3 for the beetle) and share one timer that each stack restarts: after
     * 40 ticks a new stack puts the whole Sunder back at 200.
     */
    private static void sunderStacks(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        List<Integer> stacks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            SunderEffect.addStack(pig, null, 3, 200);
            stacks.add(SunderEffect.stacks(pig));
        }
        helper.assertTrue(stacks.equals(List.of(1, 2, 3, 3)), "stacks after each of four " + stacks);
        helper.runAtTickTime(40, () -> {
            int before = pig.getEffect(ModEffects.SUNDER.holder()).getDuration();
            SunderEffect.addStack(pig, null, 3, 200);
            int after = pig.getEffect(ModEffects.SUNDER.holder()).getDuration();
            helper.assertTrue(Math.abs(before - 160) <= 1, "40 ticks on, 160 left, got " + before);
            helper.assertTrue(after == 200 && SunderEffect.stacks(pig) == 3, "a new stack restarts it at 200, got " + after);
            var m = pig.getAttribute(Attributes.ARMOR).getModifier(SunderEffect.MODIFIER);
            helper.assertTrue(m != null && Math.abs(m.amount() + 12.0) < 0.01, "three stacks take 12 armour, modifier " + (m == null ? null : m.amount()));
            helper.succeed();
        });
    }

    /**
     * Saved and loaded (a relog, a chunk reload), a sundered pig comes back with the same stacks, the same time left
     * and the same armour; once that Sunder ends, its armour is whole again: no modifier is left behind.
     */
    private static void sunderRelog(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        pig.addEffect(of(ModEffects.SUNDER, 150, 1));
        pig.addEffect(of(ModEffects.HEAVY, 150, 0));
        var registries = helper.getLevel().registryAccess();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        pig.saveWithoutId(out);
        Pig copy = EntityTypes.PIG.create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(copy != null, "a pig to load into");
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, out.buildResult()));
        MobEffectInstance sunder = copy.getEffect(ModEffects.SUNDER.holder());
        helper.assertTrue(sunder != null && sunder.getAmplifier() == 1 && sunder.getDuration() == 150, "the Sunder loads as saved: " + sunder);
        helper.assertTrue(Math.abs(armour(copy) - 2.0) < 0.01, "two stacks: armour 10 down to 2, got " + armour(copy));
        helper.assertTrue(Math.abs(copy.getAttributeValue(Attributes.GRAVITY) - 0.12) < 1.0E-6, "Heavy's gravity loads with it, got "
                + copy.getAttributeValue(Attributes.GRAVITY));
        copy.removeEffect(ModEffects.SUNDER.holder());
        copy.removeEffect(ModEffects.HEAVY.holder());
        helper.assertTrue(Math.abs(armour(copy) - 10.0) < 0.01, "with the Sunder gone the armour is 10 again, got " + armour(copy));
        helper.assertTrue(Math.abs(copy.getAttributeValue(Attributes.GRAVITY) - 0.08) < 1.0E-6, "and gravity 0.08, got "
                + copy.getAttributeValue(Attributes.GRAVITY));
        copy.discard();
        // and the original's runs out by itself
        pig.removeEffect(ModEffects.SUNDER.holder());
        pig.addEffect(of(ModEffects.SUNDER, 5, 0));
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(!has(pig, ModEffects.SUNDER) && Math.abs(armour(pig) - 10.0) < 0.01, "a Sunder that ran out leaves armour 10, got " + armour(pig));
            helper.succeed();
        });
    }

    // ---- Silenced --------------------------------------------------------------------------------------------------

    private static InteractionResult use(GameTestHelper helper, ServerPlayer p, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return p.gameMode.useItem(p, helper.getLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
    }

    /**
     * A real survival player, Silenced: an ender pearl isn't thrown, bread isn't eaten (they're hungry), a shield doesn't
     * go up, a bow isn't drawn, a snowball isn't thrown; nothing is spent. With the effect gone the same snowball flies.
     */
    private static void silencedItems(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 5.5));
        p.getInventory().add(new ItemStack(Items.ARROW, 16));
        p.addEffect(of(ModEffects.SILENCED, 80, 0));
        List<String> wrong = new ArrayList<>();
        InteractionResult pearl = use(helper, p, new ItemStack(Items.ENDER_PEARL, 4));
        if (pearl.consumesAction() || p.getMainHandItem().getCount() != 4) wrong.add("pearl " + pearl + " x" + p.getMainHandItem().getCount());
        if (!helper.getLevel().getEntitiesOfClass(ThrownEnderpearl.class, new AABB(p.blockPosition()).inflate(4)).isEmpty()) wrong.add("a pearl flew");
        if (p.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) wrong.add("the pearl went on cooldown");
        for (ItemStack stack : List.of(new ItemStack(Items.BREAD, 4), new ItemStack(Items.SHIELD), new ItemStack(Items.BOW), new ItemStack(Items.POTION))) {
            InteractionResult r = use(helper, p, stack);
            if (r.consumesAction() || p.isUsingItem()) wrong.add(stack.getItem() + " " + r + (p.isUsingItem() ? " in use" : ""));
            p.stopUsingItem();
        }
        InteractionResult snow = use(helper, p, new ItemStack(Items.SNOWBALL, 16));
        if (snow.consumesAction() || p.getMainHandItem().getCount() != 16) wrong.add("snowball " + snow + " x" + p.getMainHandItem().getCount());
        helper.assertTrue(wrong.isEmpty(), "a Silenced player uses no item: " + wrong);
        p.removeEffect(ModEffects.SILENCED.holder());
        InteractionResult free = use(helper, p, new ItemStack(Items.SNOWBALL, 16));
        helper.assertTrue(free.consumesAction() && p.getMainHandItem().getCount() == 15, "unsilenced, the snowball is thrown: " + free
                + " x" + p.getMainHandItem().getCount());
        InteractionResult bread = use(helper, p, new ItemStack(Items.BREAD, 4));
        helper.assertTrue(p.isUsingItem(), "and the bread is eaten: " + bread);
        p.stopUsingItem();
        helper.succeed();
    }

    /** A raised shield drops the moment Silenced lands, and stays down while it lasts. */
    private static void silencedDropsUse(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 5.5));
        InteractionResult up = use(helper, p, new ItemStack(Items.SHIELD));
        helper.assertTrue(up.consumesAction() && p.isUsingItem(), "the shield goes up first: " + up);
        p.addEffect(of(ModEffects.SILENCED, 80, 0));
        helper.assertTrue(!p.isUsingItem(), "Silenced drops it at once");
        p.startUsingItem(InteractionHand.MAIN_HAND);                 // forced up past the item's use
        p.doTick();
        helper.assertTrue(!p.isUsingItem(), "and it won't stay up while Silenced lasts");
        helper.succeed();
    }

    // ---- Void Rot --------------------------------------------------------------------------------------------------

    /** Healing lands at half: a heal of 4 gives 2, Instant Health's 4 gives 2; without Void Rot both give 4. */
    private static void voidRotHealing(GameTestHelper helper) {
        Pig rotting = pig(helper, new Vec3(4.5, 1, 5.5));
        Pig healthy = pig(helper, new Vec3(7.5, 1, 5.5));
        rotting.addEffect(of(ModEffects.VOID_ROT, 160, 0));
        List<Float> gains = new ArrayList<>();
        for (Pig pig : List.of(rotting, healthy)) {
            pig.setHealth(10.0F);
            pig.heal(4.0F);
            gains.add(pig.getHealth() - 10.0F);
            pig.setHealth(10.0F);
            MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(helper.getLevel(), null, null, pig, 0, 1.0);
            gains.add(pig.getHealth() - 10.0F);
        }
        helper.assertTrue(closeF(gains, List.of(2.0F, 2.0F, 4.0F, 4.0F)), "heal and Instant Health, rotting then healthy: " + gains);
        helper.succeed();
    }

    /**
     * No natural regeneration: two real survival players at 10 health with a full food bar. Over 40 ticks the healthy
     * one heals and spends saturation; the rotting one stays at 10 and spends none.
     */
    private static void voidRotRegen(GameTestHelper helper) {
        ServerPlayer rotting = TestPlayers.survivor(helper, new Vec3(4.5, 1, 5.5));
        ServerPlayer healthy = TestPlayers.survivor(helper, new Vec3(7.5, 1, 5.5));
        for (ServerPlayer p : List.of(rotting, healthy)) {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5.0F);
            p.setHealth(10.0F);
        }
        rotting.addEffect(of(ModEffects.VOID_ROT, 160, 0));
        for (int i = 0; i < 40; i++) {
            rotting.doTick();
            healthy.doTick();
        }
        helper.assertTrue(healthy.getHealth() > 10.5F, "without Void Rot the full food bar heals, health " + healthy.getHealth());
        helper.assertTrue(healthy.getFoodData().getSaturationLevel() < 5.0F, "and spends saturation, " + healthy.getFoodData().getSaturationLevel());
        helper.assertTrue(Math.abs(rotting.getHealth() - 10.0F) < 1.0E-4F, "rotting, no natural regeneration: health " + rotting.getHealth());
        helper.assertTrue(rotting.getFoodData().getSaturationLevel() == 5.0F && rotting.getFoodData().getFoodLevel() == 20,
                "and no food spent on it: saturation " + rotting.getFoodData().getSaturationLevel());
        helper.succeed();
    }

    // ---- Heavy -----------------------------------------------------------------------------------------------------

    /** Gravity 0.08 becomes 0.12 (x1.5) and jump strength 0.42 becomes 0; both come back when it ends. */
    private static void heavyAttributes(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        double g0 = pig.getAttributeValue(Attributes.GRAVITY), j0 = pig.getAttributeValue(Attributes.JUMP_STRENGTH);
        pig.addEffect(of(ModEffects.HEAVY, 120, 0));
        double g1 = pig.getAttributeValue(Attributes.GRAVITY), j1 = pig.getAttributeValue(Attributes.JUMP_STRENGTH);
        pig.removeEffect(ModEffects.HEAVY.holder());
        double g2 = pig.getAttributeValue(Attributes.GRAVITY), j2 = pig.getAttributeValue(Attributes.JUMP_STRENGTH);
        helper.assertTrue(Math.abs(g0 - 0.08) < 1.0E-6 && Math.abs(j0 - 0.42) < 1.0E-6, "a pig starts at gravity 0.08, jump 0.42: " + g0 + ", " + j0);
        helper.assertTrue(Math.abs(g1 - 0.12) < 1.0E-6, "Heavy: gravity 0.12, got " + g1);
        helper.assertTrue(Math.abs(j1) < 1.0E-6, "Heavy: jump strength 0, got " + j1);
        helper.assertTrue(Math.abs(g2 - 0.08) < 1.0E-6 && Math.abs(j2 - 0.42) < 1.0E-6, "gone: back to 0.08 and 0.42: " + g2 + ", " + j2);
        helper.succeed();
    }

    /**
     * It really falls 1.5 times as hard: from each tick's fall speed (v' = (v - g) x 0.98 in the air) a Heavy pig's
     * gravity reads 0.12 and a plain one's 0.08.
     */
    private static void heavyGravity(GameTestHelper helper) {
        Pig heavy = pig(helper, new Vec3(4.5, 6, 5.5));
        Pig plain = pig(helper, new Vec3(7.5, 6, 5.5));
        heavy.addEffect(of(ModEffects.HEAVY, 120, 0));
        List<Double> hv = new ArrayList<>(), pv = new ArrayList<>();
        helper.onEachTick(() -> {
            if (!heavy.onGround() && hv.size() < 6) hv.add(heavy.getDeltaMovement().y);
            if (!plain.onGround() && pv.size() < 6) pv.add(plain.getDeltaMovement().y);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hv.size() >= 6 && pv.size() >= 6, "both fell for 6 ticks");
            double gh = gravity(hv), gp = gravity(pv);
            helper.assertTrue(Math.abs(gp - 0.08) < 0.002, "a plain pig falls at gravity 0.08, read " + gp + " from " + pv);
            helper.assertTrue(Math.abs(gh - 0.12) < 0.002, "a Heavy pig falls at gravity 0.12, read " + gh + " from " + hv);
        });
    }

    /** Gravity read from the last two of six fall speeds: g = v - v' / 0.98. */
    private static double gravity(List<Double> v) {
        return v.get(4) - v.get(5) / 0.98;
    }

    /** No jumping: a Heavy pig's jump gives it no lift, even with Jump Boost II; a plain pig jumps 0.42. */
    private static void heavyNoJump(GameTestHelper helper) {
        Pig heavy = pig(helper, new Vec3(4.5, 1, 5.5));
        Pig boosted = pig(helper, new Vec3(6.5, 1, 5.5));
        Pig plain = pig(helper, new Vec3(8.5, 1, 5.5));
        heavy.addEffect(of(ModEffects.HEAVY, 120, 0));
        boosted.addEffect(of(ModEffects.HEAVY, 120, 0));
        boosted.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 120, 1));
        helper.runAtTickTime(10, () -> {
            for (Pig pig : List.of(heavy, boosted, plain)) {
                helper.assertTrue(pig.onGround(), "every pig stands on the floor");
                pig.setDeltaMovement(Vec3.ZERO);
                pig.jumpFromGround();
            }
            helper.assertTrue(heavy.getDeltaMovement().y <= 0.0, "Heavy: no lift, dy " + heavy.getDeltaMovement().y);
            helper.assertTrue(boosted.getDeltaMovement().y <= 0.0, "Heavy with Jump Boost II: no lift, dy " + boosted.getDeltaMovement().y);
            helper.assertTrue(Math.abs(plain.getDeltaMovement().y - 0.42) < 1.0E-4, "a plain pig jumps 0.42, dy " + plain.getDeltaMovement().y);
            helper.succeed();
        });
    }

    /** Heavy takes Levitation and Slow Falling off when it lands and refuses both while it lasts; after, they work again. */
    private static void heavyCancelsLift(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        pig.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 0));
        pig.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
        helper.assertTrue(pig.hasEffect(MobEffects.LEVITATION) && pig.hasEffect(MobEffects.SLOW_FALLING), "it floats first");
        pig.addEffect(of(ModEffects.HEAVY, 120, 0));
        helper.assertTrue(!pig.hasEffect(MobEffects.LEVITATION) && !pig.hasEffect(MobEffects.SLOW_FALLING), "Heavy cancels both");
        boolean lev = pig.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 0));
        boolean slow = pig.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
        helper.assertTrue(!lev && !slow && !pig.hasEffect(MobEffects.LEVITATION) && !pig.hasEffect(MobEffects.SLOW_FALLING),
                "and refuses both while it lasts");
        pig.removeEffect(ModEffects.HEAVY.holder());
        helper.assertTrue(pig.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0)), "once it ends, Slow Falling takes again");
        helper.succeed();
    }

    // ---- Swallowed -------------------------------------------------------------------------------------------------

    private static MireGulper gulper(GameTestHelper helper, Vec3 at) {
        MireGulper g = helper.spawn(ModMobs.MIRE_GULPER.get(), at);
        g.removeFreeWill();
        g.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        TestPlayers.whenDone(helper, g::discard);
        return g;
    }

    /** A Mire Gulper's prey is Swallowed from the gulp, all through the hold, and not once it is spat out. */
    private static void swallowedWhileHeld(GameTestHelper helper) {
        MireGulper g = gulper(helper, new Vec3(5.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(6.5, 1, 5.5));
        int[] heldTicks = {0}, swallowedTicks = {0};
        helper.runAtTickTime(2, () -> helper.assertTrue(g.swallow(pig), "it swallows the pig"));
        helper.onEachTick(() -> {
            if (g.victim() == pig) heldTicks[0]++;
            if (has(pig, ModEffects.SWALLOWED)) swallowedTicks[0]++;
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(g.victim() == pig && has(pig, ModEffects.SWALLOWED), "held, the pig shows as Swallowed");
            helper.assertTrue(swallowedTicks[0] == heldTicks[0], "Swallowed every tick it was held: " + swallowedTicks[0] + " of " + heldTicks[0]);
            g.spit(helper.getLevel());
        });
        helper.runAtTickTime(41, () -> {
            helper.assertTrue(g.victim() == null && !has(pig, ModEffects.SWALLOWED), "spat out, it is Swallowed no more");
            helper.succeed();
        });
    }

    /** The holder dies, or is removed, while it holds: the victim is Swallowed no more. */
    private static void swallowedHolderGone(GameTestHelper helper) {
        MireGulper killed = gulper(helper, new Vec3(3.5, 1, 3.5));
        MireGulper removed = gulper(helper, new Vec3(3.5, 1, 8.5));
        Pig a = pig(helper, new Vec3(4.5, 1, 3.5));
        Pig b = pig(helper, new Vec3(4.5, 1, 8.5));
        helper.runAtTickTime(2, () -> helper.assertTrue(killed.swallow(a) && removed.swallow(b), "both gulpers swallow"));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(has(a, ModEffects.SWALLOWED) && has(b, ModEffects.SWALLOWED), "both pigs are Swallowed");
            killed.hurtServer(helper.getLevel(), killed.damageSources().magic(), 1000.0F);
            removed.discard();
        });
        helper.runAtTickTime(11, () -> {
            helper.assertTrue(!has(a, ModEffects.SWALLOWED), "its holder killed, the pig is Swallowed no more");
            helper.assertTrue(!has(b, ModEffects.SWALLOWED), "its holder removed, the pig is Swallowed no more");
            helper.succeed();
        });
    }

    /** Swallowed with no holder claiming it (loaded back after a relog, given by a command) lifts by itself at once. */
    private static void swallowedUnclaimed(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        pig.addEffect(SwallowedEffect.instance());
        helper.assertTrue(has(pig, ModEffects.SWALLOWED), "it is given");
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(!has(pig, ModEffects.SWALLOWED), "no holder claims it, so it is gone");
            helper.succeed();
        });
    }

    // ---- death -----------------------------------------------------------------------------------------------------

    /** Every effect lifts the tick its bearer dies, with its attribute changes: nothing outlasts a death. */
    private static void liftOnDeath(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        for (var e : List.of(ModEffects.SUNDER, ModEffects.SILENCED, ModEffects.VOID_ROT, ModEffects.HEAVY)) pig.addEffect(of(e, 160, 0));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(pig.getActiveEffects().size() == 4 && Math.abs(armour(pig) - 6.0) < 0.01, "four effects, armour 6");
            pig.kill(helper.getLevel());
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(pig.isDeadOrDying(), "the pig is dying");
            helper.assertTrue(pig.getActiveEffects().isEmpty(), "and has no effects left: " + pig.getActiveEffects());
            helper.assertTrue(Math.abs(armour(pig) - 10.0) < 0.01 && Math.abs(pig.getAttributeValue(Attributes.GRAVITY) - 0.08) < 1.0E-6,
                    "its armour and gravity are its own again: " + armour(pig) + ", " + pig.getAttributeValue(Attributes.GRAVITY));
            helper.succeed();
        });
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private static boolean close(List<Double> got, List<Double> want) {
        if (got.size() != want.size()) return false;
        for (int i = 0; i < got.size(); i++) if (Math.abs(got.get(i) - want.get(i)) > 0.01) return false;
        return true;
    }

    private static boolean closeF(List<Float> got, List<Float> want) {
        if (got.size() != want.size()) return false;
        for (int i = 0; i < got.size(); i++) if (Math.abs(got.get(i) - want.get(i)) > 0.01F) return false;
        return true;
    }
}
