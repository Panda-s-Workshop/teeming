package com.panda.teeming.gametest.mob;

import com.panda.teeming.mob.overworld.ShellguardCrab;
import com.panda.teeming.mob.overworld.ShellguardCrab.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Shellguard Crab: the front 120 degrees turn every blow and the crab pinches back; a shovel or a shove of 0.5 flips
 * it onto its back for 60 ticks, where it takes double. Every number is checked against a plain value.
 */
public final class ShellguardCrabTests {
    private ShellguardCrabTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("shellguard_crab_tell", ShellguardCrabTests::tell);
        out.accept("shellguard_crab_counter", ShellguardCrabTests::shovelFlips);
        out.accept("shellguard_crab_failure", ShellguardCrabTests::guardedFrontPinchesBack);
        out.accept("shellguard_crab_guard_arc", ShellguardCrabTests::guardArc);
        out.accept("shellguard_crab_knockback", ShellguardCrabTests::knockbackFlips);
        out.accept("shellguard_crab_flip_length", ShellguardCrabTests::flipLength);
        out.accept("shellguard_crab_stats", ShellguardCrabTests::stats);
    }

    /** A crab that stays where it is put, facing +Z (yaw 0), with the base look. */
    private static ShellguardCrab crab(GameTestHelper helper, double x, double z) {
        ShellguardCrab crab = helper.spawnWithNoFreeWill(ModMobs.SHELLGUARD_CRAB.get(), new Vec3(x, 1, z));
        crab.core().setLook("base", true);
        crab.setYRot(0.0F);
        crab.yRotO = 0.0F;
        crab.setYBodyRot(0.0F);
        crab.yBodyRotO = 0.0F;
        crab.setYHeadRot(0.0F);
        return crab;
    }

    /** A still attacker {@code distance} blocks from the crab, {@code degrees} off its facing (0 = straight in front). */
    private static <T extends Mob> T around(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type, double x, double z,
                                            double degrees, double distance) {
        double a = Math.toRadians(degrees);
        T mob = helper.spawn(type, new Vec3(x - Math.sin(a) * distance, 1, z + Math.cos(a) * distance));
        mob.setNoAi(true);
        return mob;
    }

    /** The claws stay up exactly 8 ticks, hurting nothing and standing still, then the pinch comes. */
    private static void tell(GameTestHelper helper) {
        ShellguardCrab crab = crab(helper, 5.5, 4.5);
        Pig pig = around(helper, EntityTypes.PIG, 5.5, 4.5, 0.0, 1.3);
        float before = pig.getHealth();
        int[] tick = {0};
        int[] up = {0};
        boolean[] pinched = {false};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 2) helper.assertTrue(crab.startTell(pig), "the tell starts");
            if (crab.core().in(Step.TELL)) {
                up[0]++;
                helper.assertTrue(pig.getHealth() >= before, "the raised claws hurt nothing yet");
                helper.assertTrue(crab.getDeltaMovement().horizontalDistance() < 0.05, "it holds still with its claws up");
            } else if (up[0] > 0 && crab.core().in(Step.PINCH)) {
                pinched[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(pinched[0], "the tell ends in the pinch");
            helper.assertTrue(up[0] == 8, "the claws stay up 8 ticks, saw " + up[0]);
        });
    }

    /** The failure: a blow to the guarded front does nothing, and the crab pinches the attacker back for Atk (4). */
    private static void guardedFrontPinchesBack(GameTestHelper helper) {
        ShellguardCrab crab = crab(helper, 5.5, 4.5);
        Pig pig = around(helper, EntityTypes.PIG, 5.5, 4.5, 0.0, 1.3);
        float pigBefore = pig.getHealth();
        helper.runAtTickTime(2, () -> {
            boolean landed = crab.hurtServer(helper.getLevel(), crab.damageSources().mobAttack(pig), 6.0F);
            helper.assertTrue(crab.getHealth() == 20.0F, "the guarded front takes no damage, health " + crab.getHealth());
            helper.assertTrue(landed, "a guarded melee blow still lands its shove");
            helper.assertTrue(crab.core().in(Step.TELL), "it raises its claws to pinch back, phase " + crab.phase());
            helper.assertTrue(crab.getTarget() == pig, "it turns on the attacker");
        });
        helper.succeedWhen(() -> {
            float lost = pigBefore - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F) < 0.01F, "the pinch back does Atk (4), the pig lost " + lost);
            helper.assertTrue(crab.getHealth() == 20.0F, "the crab is still unhurt");
        });
    }

    /**
     * The guard covers 120 degrees: blows from straight ahead and 58 degrees off are turned, blows from 62 degrees off
     * and from behind land (5 after armour 4 is 4.7). An ordinary blow's shove (0.4) doesn't flip it.
     */
    private static void guardArc(GameTestHelper helper) {
        double[][] spots = {{2.5, 2.5, 0.0}, {8.5, 2.5, 58.0}, {2.5, 7.5, 62.0}, {8.5, 7.5, 180.0}};
        float[] expect = {0.0F, 0.0F, 4.7F, 4.7F};
        ShellguardCrab[] crabs = new ShellguardCrab[4];
        Pig[] pigs = new Pig[4];
        for (int i = 0; i < 4; i++) {
            crabs[i] = crab(helper, spots[i][0], spots[i][1]);
            pigs[i] = around(helper, EntityTypes.PIG, spots[i][0], spots[i][1], spots[i][2], 1.6);
        }
        helper.runAtTickTime(2, () -> {
            for (int i = 0; i < 4; i++) {
                float before = crabs[i].getHealth();
                crabs[i].hurtServer(helper.getLevel(), crabs[i].damageSources().mobAttack(pigs[i]), 5.0F);
                float lost = before - crabs[i].getHealth();
                helper.assertTrue(Math.abs(lost - expect[i]) < 0.01F,
                        "a blow from " + spots[i][2] + " degrees off its front should cost " + expect[i] + ", cost " + lost);
                helper.assertTrue(!crabs[i].flipped(), "an ordinary blow's shove doesn't flip it (" + spots[i][2] + " degrees)");
            }
            helper.succeed();
        });
    }

    /** The counter: a shovel to the guarded front does no damage but flips it, belly up; flipped, a blow costs double. */
    private static void shovelFlips(GameTestHelper helper) {
        ShellguardCrab crab = crab(helper, 3.5, 5.5);
        ShellguardCrab upright = crab(helper, 8.5, 5.5);
        Husk husk = around(helper, EntityTypes.HUSK, 3.5, 5.5, 0.0, 1.6);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        helper.runAtTickTime(2, () -> {
            crab.hurtServer(helper.getLevel(), crab.damageSources().mobAttack(husk), 5.0F);
            helper.assertTrue(crab.getHealth() == 20.0F, "the shovel on the guarded front does no damage, health " + crab.getHealth());
            helper.assertTrue(crab.flipped(), "the shovel flips it, phase " + crab.phase());
            helper.assertTrue(crab.vulnerable(), "flipped, its belly shows");
        });
        helper.runAtTickTime(4, () -> {
            float before = crab.getHealth();
            crab.hurtServer(helper.getLevel(), crab.damageSources().magic(), 4.0F);
            float lost = before - crab.getHealth();
            helper.assertTrue(Math.abs(lost - 8.0F) < 0.01F, "flipped, it takes x2: 4 should cost 8, cost " + lost);
            float uprightBefore = upright.getHealth();
            upright.hurtServer(helper.getLevel(), upright.damageSources().magic(), 4.0F);
            float uprightLost = uprightBefore - upright.getHealth();
            helper.assertTrue(Math.abs(uprightLost - 4.0F) < 0.01F, "upright, 4 costs 4, cost " + uprightLost);
            helper.succeed();
        });
    }

    /** A shove of 0.5 flips it; one of 0.4 doesn't. */
    private static void knockbackFlips(GameTestHelper helper) {
        ShellguardCrab hard = crab(helper, 3.5, 5.5);
        ShellguardCrab soft = crab(helper, 8.5, 5.5);
        helper.runAtTickTime(2, () -> {
            hard.knockback(0.5, 0.0, 1.0, hard.damageSources().generic(), 0.0F);
            soft.knockback(0.4, 0.0, 1.0, soft.damageSources().generic(), 0.0F);
            helper.assertTrue(hard.flipped(), "knockback 0.5 flips it, phase " + hard.phase());
            helper.assertTrue(!soft.flipped(), "knockback 0.4 doesn't, phase " + soft.phase());
            helper.succeed();
        });
    }

    /** It lies on its back exactly 60 ticks, then stands and guards again. */
    private static void flipLength(GameTestHelper helper) {
        ShellguardCrab crab = crab(helper, 5.5, 5.5);
        int[] tick = {0};
        int[] down = {0};
        boolean[] up = {false};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 2) crab.knockback(0.6, 0.0, 1.0, crab.damageSources().generic(), 0.0F);
            if (crab.flipped()) down[0]++;
            else if (down[0] > 0) up[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(up[0], "it rolls back, " + down[0] + " ticks so far");
            helper.assertTrue(down[0] == 60, "it lies on its back 60 ticks, lay " + down[0]);
            helper.assertTrue(!crab.vulnerable(), "back on its legs, the belly is hidden again");
        });
    }

    /** Health 20, attack 4, armour 4, a 0.9 x 0.6 hitbox. */
    private static void stats(GameTestHelper helper) {
        ShellguardCrab crab = crab(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(crab.getMaxHealth() == 20.0F, "health 20, got " + crab.getMaxHealth());
            helper.assertTrue(crab.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4.0, "attack 4, got " + crab.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(crab.getArmorValue() == 4, "armour 4, got " + crab.getArmorValue());
            helper.assertTrue(Math.abs(crab.getBbWidth() - 0.9F) < 0.01F && Math.abs(crab.getBbHeight() - 0.6F) < 0.01F,
                    "a 0.9 x 0.6 hitbox, got " + crab.getBbWidth() + " x " + crab.getBbHeight());
            helper.succeed();
        });
    }
}
