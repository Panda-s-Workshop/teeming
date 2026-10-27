package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.FacetDoppel;
import com.panda.teeming.mob.end.FacetDoppel.Held;
import com.panda.teeming.mob.end.FacetDoppel.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Facet Doppel: the first player in its sight within its follow range starts a flicker of exactly 20 ticks (glow stage 1,
 * holding still, nothing copied yet); then it copies that player's armour value and the attack damage of their main-hand
 * item: an iron set 15, a diamond or netherite set 20, an iron sword 6, a diamond sword 7, a netherite sword 8, a bare
 * hand 1. Enchantments (Sharpness V, Protection IV) and effects (Strength II) carry nothing over; what is held when the
 * flicker ends is what it copies; the copy never changes after, and the glass copy in its hand matches the item.
 * Spectators are never copied, creative players are; a player out of range or out of sight starts nothing; a flicker
 * that ends with its player gone copies nothing and waits 40 ticks. Its blow: an 8-tick wind-up, then its copied attack
 * (6 from an iron sword, a fist's 1 before any copy). The counter: a melee blow from a weapon that outclasses the copy
 * shatters its facets once, and the copied armour is gone; an equal weapon or an arrow does nothing to them. HP 36,
 * attack and armour 0 until it copies, 0.6 x 1.95, End midlands at weight 3 in groups of 1. Its follow range is 1 (it
 * notices no one) until a test opens it to 5, which reaches its own player and nothing past the arena's barrier walls.
 */
public final class FacetDoppelTests {
    private FacetDoppelTests() {}

    /** The doppel in the middle of the arena, its player 3 blocks north of it. */
    private static final Vec3 AT = new Vec3(6.5, 1, 6.5);
    private static final Vec3 NEAR = new Vec3(6.5, 1, 3.5);
    /** Its follow range once a test lets it see: enough for a player 3 blocks off, short of every other arena. */
    private static final double SEE = 5.0;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("facet_doppel_tell", FacetDoppelTests::tell);
        out.accept("facet_doppel_counter", FacetDoppelTests::counter);
        out.accept("facet_doppel_failure", FacetDoppelTests::failure);
        out.accept("facet_doppel_diamond_set", FacetDoppelTests::diamondSet);
        out.accept("facet_doppel_netherite_set", FacetDoppelTests::netheriteSet);
        out.accept("facet_doppel_no_enchantments", FacetDoppelTests::noEnchantments);
        out.accept("facet_doppel_no_effects", FacetDoppelTests::noEffects);
        out.accept("facet_doppel_switch_in_flicker", FacetDoppelTests::switchInFlicker);
        out.accept("facet_doppel_fixed", FacetDoppelTests::fixed);
        out.accept("facet_doppel_item_readings", FacetDoppelTests::itemReadings);
        out.accept("facet_doppel_range_and_sight", FacetDoppelTests::rangeAndSight);
        out.accept("facet_doppel_spectator", FacetDoppelTests::spectator);
        out.accept("facet_doppel_creative", FacetDoppelTests::creative);
        out.accept("facet_doppel_gone", FacetDoppelTests::gone);
        out.accept("facet_doppel_copy_cancel", FacetDoppelTests::copyCancel);
        out.accept("facet_doppel_windup", FacetDoppelTests::windup);
        out.accept("facet_doppel_strike_reach", FacetDoppelTests::strikeReach);
        out.accept("facet_doppel_fist_floor", FacetDoppelTests::fistFloor);
        out.accept("facet_doppel_shatter_rules", FacetDoppelTests::shatterRules);
        out.accept("facet_doppel_save_load", FacetDoppelTests::saveLoad);
        out.accept("facet_doppel_stats", FacetDoppelTests::stats);
        out.accept("facet_doppel_home", FacetDoppelTests::home);
        TeemingGameTests.timeLimit("facet_doppel_gone", 160);
        TeemingGameTests.timeLimit("facet_doppel_range_and_sight", 140);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------------

    /** A doppel at {@code at}, base look, no goals, follow range 1 (it notices no one yet). Removed when the test ends. */
    private static FacetDoppel doppel(GameTestHelper helper, Vec3 at) {
        FacetDoppel d = helper.spawn(ModMobs.FACET_DOPPEL.get(), at);
        d.core().setLook("base", true);
        d.removeFreeWill();
        d.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, d::discard);
        return d;
    }

    private static FacetDoppel doppel(GameTestHelper helper) {
        return doppel(helper, AT);
    }

    private static void see(FacetDoppel d) {
        d.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SEE);
    }

    /**
     * A survival player at {@code at} holding {@code hand} and wearing {@code armour} (head, chest, legs, feet), ticked
     * every test tick so its equipment counts (nothing ticks a test player on its own).
     */
    private static ServerPlayer player(GameTestHelper helper, Vec3 at, ItemStack hand, ItemStack... armour) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemInHand(InteractionHand.MAIN_HAND, hand);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < armour.length && i < slots.length; i++) p.setItemSlot(slots[i], armour[i]);
        helper.onEachTick(() -> {
            if (!p.isRemoved()) p.doTick();
        });
        return p;
    }

    private static ItemStack[] ironSet() {
        return new ItemStack[] {new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS),
                new ItemStack(Items.IRON_BOOTS)};
    }

    /** A still pig with 100 health, removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static double attack(FacetDoppel d) {
        return d.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    private static double armour(FacetDoppel d) {
        return d.getAttributeValue(Attributes.ARMOR);
    }

    /**
     * The player's gear counts by tick 3; then the doppel may see it. By tick 40 it has copied: its attack, armour and
     * held copy must be {@code wantAttack}, {@code wantArmour} and {@code wantHeld}.
     */
    private static void copies(GameTestHelper helper, ServerPlayer p, int playerArmour, double wantAttack, double wantArmour, Held wantHeld) {
        FacetDoppel d = doppel(helper);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(p.getArmorValue() == playerArmour, "the player wears armour " + playerArmour + ", got " + p.getArmorValue());
            see(d);
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(d.copied() && d.copies() == 1, "it copied the player");
            helper.assertTrue(attack(d) == wantAttack, "attack " + wantAttack + ", got " + attack(d));
            helper.assertTrue(armour(d) == wantArmour && d.getArmorValue() == (int) wantArmour, "armour " + wantArmour + ", got " + armour(d));
            helper.assertTrue(d.held() == wantHeld, "it holds a glass " + wantHeld + ", got " + d.held());
            helper.succeed();
        });
    }

    // ---- the flicker, the copy, the counter ---------------------------------------------------------------------------

    /**
     * The tell: a survival player in an iron set with an iron sword comes into sight 3 blocks off. It flickers exactly 20
     * ticks: held still, its facets lit (glow stage 1), nothing copied (attack 0, armour 0, no copy in hand). Then it has
     * copied attack 6 and armour 15 and holds a glass sword.
     */
    private static void tell(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.IRON_SWORD), ironSet());
        int[] ticks = {0};
        Vec3[] start = {null};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(p.getArmorValue() == 15, "the iron set counts 15, got " + p.getArmorValue());
            see(d);
        });
        helper.onEachTick(() -> {
            if (!d.core().in(Step.TELL)) return;
            if (start[0] == null) start[0] = d.position();
            ticks[0]++;
            helper.assertTrue(d.core().stage() == FacetDoppel.GLOW_FLICKER, "its facets shimmer, stage " + d.core().stage());
            helper.assertTrue(!d.copied() && attack(d) == 0.0 && armour(d) == 0.0 && d.held() == Held.NONE, "nothing is copied while it flickers");
            helper.assertTrue(Math.hypot(d.getX() - start[0].x, d.getZ() - start[0].z) < 0.01, "it holds still");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(d.copied(), "it copied");
            helper.assertTrue(ticks[0] == 20, "the flicker lasts 20 ticks, saw " + ticks[0]);
            helper.assertTrue(attack(d) == 6.0, "attack 6 from the iron sword, got " + attack(d));
            helper.assertTrue(armour(d) == 15.0 && d.getArmorValue() == 15, "armour 15 from the iron set, got " + armour(d));
            helper.assertTrue(d.held() == Held.SWORD, "a glass sword in its hand, got " + d.held());
            helper.assertTrue(d.core().stage() == FacetDoppel.GLOW_REST, "the shimmer is over");
        });
    }

    /**
     * The counter: the player meets it bare-handed in an iron set, so it copies a fist (1) and armour 15. A blow with a
     * diamond sword (7, which outclasses 1) shatters its facets: armour 0, its attack still 1, once. Its blow on a pig
     * then takes exactly 1.
     */
    private static void counter(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        ServerPlayer p = player(helper, NEAR, ItemStack.EMPTY, ironSet());
        Pig[] pig = {null};
        helper.runAtTickTime(3, () -> see(d));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(d.copied() && attack(d) == 1.0 && armour(d) == 15.0 && d.held() == Held.FIST,
                    "a fist (1) and armour 15, got " + attack(d) + " / " + armour(d) + " / " + d.held());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            helper.assertTrue(d.hurtServer(helper.getLevel(), d.damageSources().playerAttack(p), 1.0F), "the blow lands");
            helper.assertTrue(d.shattered() && d.shatters() == 1, "its facets shatter");
            helper.assertTrue(armour(d) == 0.0 && d.getArmorValue() == 0, "the copied armour is gone, got " + armour(d));
            helper.assertTrue(attack(d) == 1.0, "its attack stays a fist's 1, got " + attack(d));
            d.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
            d.setTarget(null);
            pig[0] = pig(helper, 6.5, 1, 7.7);
        });
        helper.runAtTickTime(42, () -> helper.assertTrue(d.startWindup(pig[0]), "it winds up at the pig"));
        helper.runAtTickTime(60, () -> {
            float lost = 100.0F - pig[0].getHealth();
            helper.assertTrue(d.strikes() == 1 && Math.abs(lost - 1.0F) < 0.01F, "its blow takes 1, the pig lost " + lost);
            helper.succeed();
        });
    }

    /**
     * Failing it: the player meets it with an iron set and an iron sword, so it copies 6 and 15. A blow from the same
     * iron sword (6, no better) leaves its facets whole and its armour 15; its blow on a pig takes 6.
     */
    private static void failure(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.IRON_SWORD), ironSet());
        Pig[] pig = {null};
        helper.runAtTickTime(3, () -> see(d));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(d.copied() && attack(d) == 6.0 && armour(d) == 15.0, "6 and 15, got " + attack(d) + " / " + armour(d));
            helper.assertTrue(d.hurtServer(helper.getLevel(), d.damageSources().playerAttack(p), 1.0F), "the blow lands");
            helper.assertTrue(!d.shattered() && d.shatters() == 0 && armour(d) == 15.0, "an equal weapon breaks nothing, armour " + armour(d));
            d.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
            d.setTarget(null);
            pig[0] = pig(helper, 6.5, 1, 7.7);
        });
        helper.runAtTickTime(42, () -> helper.assertTrue(d.startWindup(pig[0]), "it winds up at the pig"));
        helper.runAtTickTime(60, () -> {
            float lost = 100.0F - pig[0].getHealth();
            helper.assertTrue(d.strikes() == 1 && Math.abs(lost - 6.0F) < 0.01F, "its blow takes 6, the pig lost " + lost);
            helper.succeed();
        });
    }

    /** A diamond set and a diamond sword: it copies armour 20 and attack 7. */
    private static void diamondSet(GameTestHelper helper) {
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_HELMET),
                new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
        copies(helper, p, 20, 7.0, 20.0, Held.SWORD);
    }

    /** The End's reference gear, a netherite set and a netherite sword: it copies armour 20 and attack 8. */
    private static void netheriteSet(GameTestHelper helper) {
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.NETHERITE_HELMET),
                new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS));
        copies(helper, p, 20, 8.0, 20.0, Held.SWORD);
    }

    /** A Sharpness V diamond sword and an iron set with Protection IV on every piece: it copies 7 and 15, nothing more. */
    private static void noEnchantments(GameTestHelper helper) {
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 5);
        ItemStack[] set = ironSet();
        for (ItemStack piece : set) piece.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
        helper.assertTrue(sword.isEnchanted() && set[1].isEnchanted(), "the gear is enchanted");
        ServerPlayer p = player(helper, NEAR, sword, set);
        copies(helper, p, 15, 7.0, 15.0, Held.SWORD);
    }

    /**
     * A player under Strength II (their own attack climbs by 6) holding an iron sword: it copies the sword's 6, not the
     * player's 12.
     */
    private static void noEffects(GameTestHelper helper) {
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.IRON_SWORD), ironSet());
        p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 1));
        helper.runAtTickTime(2, () -> helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) == 12.0,
                "the player's own attack is 12 with Strength II, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        copies(helper, p, 15, 6.0, 15.0, Held.SWORD);
    }

    /** Held when the flicker ends is what counts: the player holds a diamond sword as it begins and a stick at its end. */
    private static void switchInFlicker(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.DIAMOND_SWORD));
        boolean[] switched = {false};
        helper.runAtTickTime(3, () -> see(d));
        helper.onEachTick(() -> {
            if (!switched[0] && d.core().in(Step.TELL) && d.core().elapsed() >= 10) {
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                switched[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(switched[0] && d.copied(), "it copied after the switch");
            helper.assertTrue(attack(d) == 1.0 && d.held() == Held.ROD, "a stick: attack 1 and a glass rod, got " + attack(d) + " / " + d.held());
        });
    }

    /**
     * Fixed once copied: after it copied an iron sword and an iron set, the player takes up a diamond sword and sheds the
     * armour, and a second player in a diamond set comes as close; 30 ticks on it still has 6 and 15, one copy, and has
     * not flickered again.
     */
    private static void fixed(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.IRON_SWORD), ironSet());
        // the second player waits 5.7 blocks off, out of its range of 5, until it has copied the first
        ServerPlayer second = player(helper, new Vec3(10.5, 1, 10.5), new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.DIAMOND_HELMET),
                new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
        boolean[] again = {false};
        helper.runAtTickTime(3, () -> see(d));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(d.copied() && attack(d) == 6.0 && armour(d) == 15.0, "copied 6 and 15");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                p.setItemSlot(slot, ItemStack.EMPTY);
            }
            second.snapTo(helper.absoluteVec(new Vec3(9.5, 1, 6.5)));
        });
        helper.onEachTick(() -> {
            if (helper.getTick() > 40 && d.core().in(Step.TELL)) again[0] = true;
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(!again[0] && d.copies() == 1, "it never flickers again");
            helper.assertTrue(attack(d) == 6.0 && armour(d) == 15.0 && d.held() == Held.SWORD, "still 6 and 15, got " + attack(d) + " / " + armour(d));
            helper.succeed();
        });
    }

    /**
     * What a held item reads as, against vanilla's tooltip numbers: a bare hand 1 (a fist), iron sword 6, diamond 7,
     * netherite 8, iron axe 9, iron pickaxe 4, iron shovel 4.5, iron hoe 1, trident 9, mace 6, stick 1 (a rod), a block 1 (a
     * cube); a spear is a spear.
     */
    private static void itemReadings(GameTestHelper helper) {
        Object[][] cases = {
                {ItemStack.EMPTY, 1.0, Held.FIST}, {new ItemStack(Items.IRON_SWORD), 6.0, Held.SWORD},
                {new ItemStack(Items.DIAMOND_SWORD), 7.0, Held.SWORD}, {new ItemStack(Items.NETHERITE_SWORD), 8.0, Held.SWORD},
                {new ItemStack(Items.IRON_AXE), 9.0, Held.AXE}, {new ItemStack(Items.IRON_PICKAXE), 4.0, Held.PICKAXE},
                {new ItemStack(Items.IRON_SHOVEL), 4.5, Held.SHOVEL}, {new ItemStack(Items.IRON_HOE), 1.0, Held.HOE},
                {new ItemStack(Items.TRIDENT), 9.0, Held.TRIDENT}, {new ItemStack(Items.MACE), 6.0, Held.MACE},
                {new ItemStack(Items.STICK), 1.0, Held.ROD}, {new ItemStack(Items.STONE), 1.0, Held.CHUNK},
        };
        for (Object[] c : cases) {
            ItemStack stack = (ItemStack) c[0];
            double want = (double) c[1];
            double got = FacetDoppel.attackOf(stack);
            helper.assertTrue(Math.abs(got - want) < 1.0E-6, stack + " reads " + want + ", got " + got);
            helper.assertTrue(Held.of(stack) == c[2], stack + " is a " + c[2] + ", got " + Held.of(stack));
        }
        helper.assertTrue(Held.of(new ItemStack(Items.STONE_SPEAR)) == Held.SPEAR, "a spear is a spear");
        helper.succeed();
    }

    /**
     * A player 5.5 blocks off is past its follow range of 5: no flicker in 30 ticks. Opened to 7, with a stone wall
     * between them: still none. With the wall gone it flickers.
     */
    private static void rangeAndSight(GameTestHelper helper) {
        FacetDoppel d = doppel(helper, new Vec3(6.5, 1, 8.5));
        player(helper, new Vec3(6.5, 1, 3.0), new ItemStack(Items.IRON_SWORD));
        boolean[] early = {false};
        helper.runAtTickTime(3, () -> see(d));
        helper.onEachTick(() -> {
            if (helper.getTick() < 70 && d.core().in(Step.TELL)) early[0] = true;
        });
        helper.runAtTickTime(34, () -> {
            helper.assertTrue(!early[0] && !d.copied(), "5.5 blocks is out of range");
            for (int x = 4; x <= 8; x++) {
                for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
            }
            d.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(7.0);
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(!early[0] && !d.copied(), "out of sight behind the wall");
            for (int x = 4; x <= 8; x++) {
                for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, 6), Blocks.AIR);
            }
        });
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(d.copied() && attack(d) == 6.0, "in sight and in range it copied");
            helper.succeed();
        });
    }

    /** A spectator 3 blocks off is never copied. */
    private static void spectator(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.IRON_SWORD));
        p.setGameMode(GameType.SPECTATOR);
        helper.runAtTickTime(3, () -> see(d));
        helper.onEachTick(() -> helper.assertTrue(!d.core().in(Step.TELL), "no flicker at a spectator"));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(!d.copied() && attack(d) == 0.0, "nothing copied");
            helper.succeed();
        });
    }

    /** A creative player is copied as anyone is (a mirror shows everyone): an iron sword, attack 6. */
    private static void creative(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        p.snapTo(helper.absoluteVec(NEAR));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        helper.runAtTickTime(3, () -> see(d));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(d.copied() && attack(d) == 6.0 && d.held() == Held.SWORD, "copied 6, got " + attack(d));
            helper.succeed();
        });
    }

    /**
     * Its player leaves during the flicker: it copies nothing, waits 40 ticks without flickering, and only then flickers
     * at the player who came back, at least 40 ticks after the first flicker ended.
     */
    private static void gone(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.IRON_SWORD));
        long[] firstEnd = {-1}, secondStart = {-1};
        boolean[] moved = {false}, wasTell = {false}, back = {false};
        helper.runAtTickTime(3, () -> see(d));
        helper.onEachTick(() -> {
            boolean tell = d.core().in(Step.TELL);
            if (tell && !moved[0] && d.core().elapsed() >= 5) {
                p.snapTo(helper.absoluteVec(new Vec3(6.5, 1, 0.5)));             // 6 blocks off: out of range
                moved[0] = true;
            }
            if (wasTell[0] && !tell && firstEnd[0] < 0) {
                firstEnd[0] = helper.getTick();
                helper.assertTrue(d.retry() >= 39 && d.retry() <= 40, "it waits 40 ticks, retry " + d.retry());
                helper.assertTrue(!d.copied(), "nothing copied");
                p.snapTo(helper.absoluteVec(NEAR));                                // back in range at once
                back[0] = true;
            }
            if (back[0] && tell && secondStart[0] < 0 && firstEnd[0] >= 0 && helper.getTick() > firstEnd[0]) secondStart[0] = helper.getTick();
            wasTell[0] = tell;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(secondStart[0] > 0, "it flickers again");
            long wait = secondStart[0] - firstEnd[0];
            helper.assertTrue(wait >= 40 && wait <= 41, "after 40 ticks, waited " + wait);
            helper.assertTrue(d.copied() && attack(d) == 6.0, "and copies the player the second time");
        });
    }

    /** A vetoed "copy" step copies nothing. */
    private static void copyCancel(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        player(helper, NEAR, new ItemStack(Items.IRON_SWORD), ironSet());
        boolean[] flickered = {false};
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == d && e.step().equals("copy")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(3, () -> see(d));
        helper.onEachTick(() -> flickered[0] |= d.core().in(Step.TELL));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(flickered[0], "it flickered");
            helper.assertTrue(!d.copied() && attack(d) == 0.0 && armour(d) == 0.0 && d.held() == Held.NONE, "nothing copied");
            helper.succeed();
        });
    }

    // ---- the blow ------------------------------------------------------------------------------------------------------

    /** Its blow with an iron sword's copy: an 8-tick wind-up, held still and harmless, then 6 to the pig in reach. */
    private static void windup(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        d.setCopy(6.0, 15.0, Held.SWORD, false);
        Pig pig = pig(helper, 6.5, 1, 7.7);
        d.setTarget(pig);
        Vec3 start = d.position();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (!d.core().in(Step.WINDUP)) return;
            ticks[0]++;
            helper.assertTrue(pig.getHealth() >= 100.0F, "the wind-up hurts nothing");
            helper.assertTrue(Math.hypot(d.getX() - start.x, d.getZ() - start.z) < 0.05, "it holds still while it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(d.strikes() == 1, "the blow landed, strikes " + d.strikes() + ", phase " + d.phase());
            helper.assertTrue(ticks[0] == 8, "the wind-up lasts 8 ticks, saw " + ticks[0]);
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "the blow takes 6, the pig lost " + lost);
        });
    }

    /** Stepping back out of reach during the wind-up: the blow hits nothing. */
    private static void strikeReach(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        d.setCopy(6.0, 0.0, Held.SWORD, false);
        Pig pig = pig(helper, 6.5, 1, 7.7);
        d.setTarget(pig);
        boolean[] moved = {false}, struck = {false};
        helper.onEachTick(() -> {
            if (d.core().in(Step.WINDUP) && !moved[0]) {
                pig.snapTo(pig.getX(), pig.getY(), pig.getZ() + 3.0);
                moved[0] = true;
            }
            if (d.core().in(Step.STRIKE)) struck[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0], "it swung");
            helper.assertTrue(d.strikes() == 0 && pig.getHealth() >= 100.0F, "at the air: the pig has " + pig.getHealth());
        });
    }

    /** Before it has copied anyone its attack is 0, and its blow lands as a fist: 1. */
    private static void fistFloor(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        Pig pig = pig(helper, 6.5, 1, 7.7);
        d.setTarget(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(!d.copied() && attack(d) == 0.0, "attack 0 with no copy");
            helper.assertTrue(d.strikes() == 1, "the blow landed");
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 1.0F) < 0.01F, "a fist's 1, the pig lost " + lost);
        });
    }

    /**
     * What shatters its facets, with a copy of 6 and armour 15: an arrow from a player holding a netherite sword, no; a
     * player's blow with an iron sword (6), no; a vetoed shatter, no; a blow with a diamond sword (7), yes, armour 0; a
     * second such blow changes nothing more (one shatter).
     */
    private static void shatterRules(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        d.setCopy(6.0, 15.0, Held.SWORD, false);
        ServerPlayer p = player(helper, NEAR, new ItemStack(Items.NETHERITE_SWORD));
        ServerLevel level = helper.getLevel();
        boolean[] vetoing = {true};
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == d && e.step().equals("shatter") && vetoing[0]) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(armour(d) == 15.0 && d.outclasses(new ItemStack(Items.DIAMOND_SWORD)) && !d.outclasses(new ItemStack(Items.IRON_SWORD)),
                    "7 outclasses 6, 6 does not");
            Vec3 a = d.position().add(0, 1, -1);
            Arrow arrow = new Arrow(level, a.x, a.y, a.z, new ItemStack(Items.ARROW), null);
            helper.assertTrue(d.hurtServer(level, d.damageSources().arrow(arrow, p), 1.0F), "the arrow lands");
            helper.assertTrue(!d.shattered() && armour(d) == 15.0, "an arrow breaks nothing");
        });
        helper.runAtTickTime(14, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(d.hurtServer(level, d.damageSources().playerAttack(p), 2.0F), "the iron blow lands");
            helper.assertTrue(!d.shattered() && armour(d) == 15.0, "an equal weapon breaks nothing");
        });
        helper.runAtTickTime(26, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            helper.assertTrue(d.hurtServer(level, d.damageSources().playerAttack(p), 3.0F), "the vetoed blow lands");
            helper.assertTrue(!d.shattered() && armour(d) == 15.0, "a vetoed shatter breaks nothing");
            vetoing[0] = false;
        });
        helper.runAtTickTime(38, () -> {
            helper.assertTrue(d.hurtServer(level, d.damageSources().playerAttack(p), 4.0F), "the diamond blow lands");
            helper.assertTrue(d.shattered() && d.shatters() == 1 && armour(d) == 0.0, "a better weapon shatters it, armour " + armour(d));
            helper.assertTrue(attack(d) == 6.0, "its attack stays 6");
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(d.hurtServer(level, d.damageSources().playerAttack(p), 5.0F), "another diamond blow lands");
            helper.assertTrue(d.shatters() == 1 && armour(d) == 0.0, "it shatters once");
            helper.succeed();
        });
    }

    // ---- saving, stats and home -----------------------------------------------------------------------------------------

    /** A copy of 6 with its facets shattered (armour 0 of a copied 15) and a glass sword survives a save and load. */
    private static void saveLoad(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        d.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            d.setCopy(6.0, 15.0, Held.SWORD, true);
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            d.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            FacetDoppel c = ModMobs.FACET_DOPPEL.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            c.setNoAi(true);
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, c::discard);
            helper.assertTrue(c.copied() && c.copies() == 0, "the copy came back copied, without copying anew");
            helper.assertTrue(attack(c) == 6.0, "attack 6, got " + attack(c));
            helper.assertTrue(armour(c) == 0.0 && c.copiedArmour() == 15.0, "armour 0 of a copied 15, got " + armour(c));
            helper.assertTrue(c.shattered() && c.held() == Held.SWORD, "shattered, a glass sword in hand");
            helper.succeed();
        });
    }

    /** Health 36; attack 0 and armour 0 until it copies; a 0.6 x 1.95 hitbox. */
    private static void stats(GameTestHelper helper) {
        FacetDoppel d = doppel(helper);
        d.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(d.getMaxHealth() == 36.0F && d.getHealth() == 36.0F, "health 36, got " + d.getHealth() + "/" + d.getMaxHealth());
            helper.assertTrue(attack(d) == 0.0, "attack 0 of its own, got " + attack(d));
            helper.assertTrue(d.getArmorValue() == 0 && armour(d) == 0.0, "armour 0 of its own, got " + armour(d));
            helper.assertTrue(Math.abs(d.getBbWidth() - 0.6F) < 0.01F && Math.abs(d.getBbHeight() - 1.95F) < 0.01F,
                    "a 0.6 x 1.95 hitbox, got " + d.getBbWidth() + " x " + d.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: the End midlands, never the highlands or the main island; weight 3, in groups of exactly 1; a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("facet_doppel"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "its home holds the End midlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.THE_END)), "never the highlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/facet_doppel")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.FACET_DOPPEL.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 3, "spawn weight 3, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "in groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.FACET_DOPPEL.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
