package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.effect.NulledEffect;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Nullcantor;
import com.panda.teeming.mob.end.Nullcantor.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * Nullcantor: a null ring of radius 8 is always on round it; a player inside is Nulled, and no enchantment on their
 * gear has any effect (Sharpness, Protection, Feather Falling, Efficiency, Mending, Loyalty...; curses still hold),
 * until a tick or two after they step out. Its swing: a 10-tick tell that hurts nothing, then a blow of 8.5, at most
 * every 30 ticks. A hit from an attacker standing outside the ring knocks its collar off: the ring stays where the
 * collar fell for 160 ticks while the Nullcantor walks on without one, then the collar grows back.
 *
 * <p>The Nullcantor's goals are cleared and its follow range is 1, so it never notices players in other arenas, and a
 * listener keeps its ring off every player outside this arena (its radius reaches past the arena's edge). The players
 * are real survival players (the helper's mock player can't be hurt); nothing ticks them on its own, so each test
 * ticks them itself, which runs their status and the enchantment tick that keeps their attributes in step.
 */
public final class NullcantorTests {
    private NullcantorTests() {}

    /** The Nullcantor at one side of the arena, so players can stand up to 9.5 blocks from it inside the arena. */
    private static final Vec3 SIDE = new Vec3(1.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("nullcantor_tell", NullcantorTests::tell);
        out.accept("nullcantor_counter", NullcantorTests::counter);
        out.accept("nullcantor_failure", NullcantorTests::failure);
        out.accept("nullcantor_ring_radius", NullcantorTests::ringRadius);
        out.accept("nullcantor_step_out", NullcantorTests::stepOut);
        out.accept("nullcantor_enchantments", NullcantorTests::enchantments);
        out.accept("nullcantor_strike", NullcantorTests::strike);
        out.accept("nullcantor_inside_hit", NullcantorTests::insideHit);
        out.accept("nullcantor_shard_time", NullcantorTests::shardTime);
        TeemingGameTests.timeLimit("nullcantor_shard_time", 220);
        out.accept("nullcantor_hum", NullcantorTests::hum);
        out.accept("nullcantor_null_cancel", NullcantorTests::nullCancel);
        out.accept("nullcantor_status", NullcantorTests::status);
        out.accept("nullcantor_stats", NullcantorTests::stats);
        out.accept("nullcantor_home", NullcantorTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    private static Nullcantor cantor(GameTestHelper helper, Vec3 at) {
        Nullcantor n = helper.spawn(ModMobs.NULLCANTOR.get(), at);
        n.removeFreeWill();        // no walking; its phase machine and its ring still run
        n.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        n.core().setLook(LookDef.BASE, true);
        // its ring reaches past the arena's edge: it nulls no one but this test's players
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == n && e.step().equals("null") && e.target() != null && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        TestPlayers.whenDone(helper, n::discard);
        return n;
    }

    /** A survival player at {@code at}, ticked every test tick. */
    private static ServerPlayer player(GameTestHelper helper, Vec3 at) {
        return player(helper, at, null);
    }

    /**
     * As above, with {@code after} run right after the player's own tick, in the same callback. Per-tick callbacks
     * live in a hash map, so two separate ones run in no fixed order: a check made in its own callback may see this
     * tick's status change, or only the next tick's.
     */
    private static ServerPlayer player(GameTestHelper helper, Vec3 at, @Nullable Consumer<ServerPlayer> after) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        helper.onEachTick(() -> {
            if (p.isRemoved()) return;
            p.doTick();
            if (after != null) after.accept(p);
        });
        return p;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        return pig;
    }

    private static Holder<Enchantment> ench(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack enchanted(GameTestHelper helper, net.minecraft.world.item.Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(ench(helper, key), level);
        return stack;
    }

    /** Dresses {@code p} in diamond armour, each piece with Protection IV, and a Sharpness V iron sword. */
    private static void kit(GameTestHelper helper, ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.DIAMOND_HELMET, Enchantments.PROTECTION, 4));
        p.setItemSlot(EquipmentSlot.CHEST, enchanted(helper, Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 4));
        p.setItemSlot(EquipmentSlot.LEGS, enchanted(helper, Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 4));
        p.setItemSlot(EquipmentSlot.FEET, enchanted(helper, Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 4));
        p.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 5));
    }

    private static boolean nulled(ServerPlayer p) {
        return p.hasEffect(ModEffects.NULLED.holder());
    }

    private static boolean near(float value, float expected) {
        return Math.abs(value - expected) < 0.01F;
    }

    /** Sharpness on {@code p}'s sword against {@code victim}: a base hit of 6, with whatever the sword adds. */
    private static float sharpness(GameTestHelper helper, ServerPlayer p, Pig victim) {
        return EnchantmentHelper.modifyDamage(helper.getLevel(), p.getMainHandItem(), victim, p.damageSources().playerAttack(p), 6.0F);
    }

    /**
     * What a plain 6 costs {@code p} after its armour and its Protection; health is reset first. A cactus prick: armour
     * counts against it (generic damage skips armour), it has no attacker and it doesn't scale with difficulty.
     */
    private static float hit(GameTestHelper helper, ServerPlayer p) {
        p.setHealth(20.0F);
        p.damageCooldownTime = 0;
        p.setInvulnerableTime(0);
        p.hurtServer(helper.getLevel(), p.damageSources().cactus(), 6.0F);
        return 20.0F - p.getHealth();
    }

    private static void snap(GameTestHelper helper, net.minecraft.world.entity.Entity e, Vec3 at) {
        e.snapTo(helper.absoluteVec(at));
    }

    // ---- the swing -----------------------------------------------------------------------------------------------

    /** A target in reach starts the tell: it holds 10 ticks and nothing lands during it. */
    private static void tell(GameTestHelper helper) {
        Nullcantor n = cantor(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.7, 1, 5.5));
        n.setTarget(pig);
        int[] telling = {0};
        long[] first = {-1};
        boolean[] early = {false}, done = {false};
        helper.onEachTick(() -> {
            if (n.core().in(Step.TELL)) {
                if (first[0] < 0) first[0] = helper.getTick();
                telling[0]++;
                early[0] |= pig.getHealth() < 40.0F;
            } else if (telling[0] > 0) {
                done[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "it raised its arms and brought them down, phase " + n.phase());
            helper.assertTrue(first[0] >= 0 && first[0] <= 3, "a target in reach starts the tell at once, at " + first[0]);
            helper.assertTrue(Math.abs(telling[0] - 10) <= 1, "the tell lasts 10 ticks, lasted " + telling[0]);
            helper.assertTrue(!early[0], "nothing lands during the tell");
        });
    }

    /** After the tell the blow lands for its attack, 8.5 (40 -> 31.5), and it swings again no sooner than 30 ticks on. */
    private static void strike(GameTestHelper helper) {
        Nullcantor n = cantor(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.7, 1, 5.5));
        n.setTarget(pig);
        List<Long> tells = new ArrayList<>();
        boolean[] was = {false};
        float[] afterFirst = {-1.0F};
        helper.onEachTick(() -> {
            boolean now = n.core().in(Step.TELL);
            if (now && !was[0]) tells.add(helper.getTick());
            was[0] = now;
            if (afterFirst[0] < 0 && tells.size() == 1 && n.core().in(Step.STRIKE) && n.strikes() == 1) afterFirst[0] = pig.getHealth();
            pig.snapTo(n.position().add(1.2, 0.0, 0.0));                    // keep it in reach after the knockback
            pig.setDeltaMovement(Vec3.ZERO);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tells.size() >= 2, "it swung twice, tells at " + tells);
            helper.assertTrue(near(afterFirst[0], 31.5F), "the blow does its attack, 8.5: 40 -> 31.5, got " + afterFirst[0]);
            long gap = tells.get(1) - tells.get(0);
            helper.assertTrue(Math.abs(gap - 30) <= 1, "it swings every 30 ticks, the second tell came " + gap + " after the first");
        });
    }

    // ---- the ring ------------------------------------------------------------------------------------------------

    /** Radius 8: a player 7.5 blocks from it is Nulled, one 8.5 blocks off is not. */
    private static void ringRadius(GameTestHelper helper) {
        Nullcantor n = cantor(helper, SIDE);
        ServerPlayer in = player(helper, SIDE.add(7.5, 0, 0));
        ServerPlayer out = player(helper, SIDE.add(8.5, 0, 0));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(n.ringHolds(in) && nulled(in), "7.5 blocks off is inside the ring");
            helper.assertTrue(!n.ringHolds(out) && !nulled(out), "8.5 blocks off is outside it");
            helper.assertTrue(Math.abs(Nullcantor.RING.bounds(Vec3.ZERO).maxX - 8.0) < 1.0E-6, "the ring's radius is 8");
            helper.succeed();
        });
    }

    /**
     * Failing the counter: inside the ring Sharpness V adds nothing to a hit of 6, and Protection IV on a diamond set
     * takes nothing off a plain 6: the armour alone leaves 6 x (1 - 18.5 / 25) = 1.56.
     */
    private static void failure(GameTestHelper helper) {
        cantor(helper, SIDE);
        ServerPlayer p = player(helper, SIDE.add(3.0, 0, 0));
        kit(helper, p);
        Pig victim = pig(helper, new Vec3(9.5, 1, 9.5));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(nulled(p), "the player in the ring is Nulled");
            helper.assertTrue(near(sharpness(helper, p, victim), 6.0F), "Sharpness V adds nothing: " + sharpness(helper, p, victim));
            float lost = hit(helper, p);
            helper.assertTrue(near(lost, 1.56F), "Protection IV takes nothing off: 6 -> 1.56 after the armour alone, lost " + lost);
            helper.assertTrue(p.getMainHandItem().getEnchantments().getLevel(ench(helper, Enchantments.SHARPNESS)) == 5, "the sword keeps its Sharpness V");
            helper.succeed();
        });
    }

    /**
     * Stepping out: the status lifts within 3 ticks and every enchantment works again: Sharpness V makes a 6 a 9,
     * Protection IV leaves 1.56 x (1 - 16 / 25) = 0.5616 of a plain 6, and Efficiency V's mining speed (+26) is back.
     * Stepping in again switches them off again.
     */
    private static void stepOut(GameTestHelper helper) {
        cantor(helper, SIDE);
        long[] left = {-1}, lifted = {-1};
        ServerPlayer p = player(helper, SIDE.add(3.0, 0, 0), self -> {
            if (left[0] >= 0 && lifted[0] < 0 && !nulled(self)) lifted[0] = helper.getTick();
        });
        kit(helper, p);
        ServerPlayer miner = player(helper, SIDE.add(3.0, 0, 3.0));
        miner.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 5));
        Pig victim = pig(helper, new Vec3(9.5, 1, 9.5));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(nulled(p) && nulled(miner), "both start inside the ring, Nulled");
            helper.assertTrue(Math.abs(miner.getAttributeValue(Attributes.MINING_EFFICIENCY)) < 1.0E-6,
                    "inside, Efficiency V adds no mining speed: " + miner.getAttributeValue(Attributes.MINING_EFFICIENCY));
            snap(helper, p, SIDE.add(9.5, 0, 0));
            snap(helper, miner, SIDE.add(9.5, 0, 2.0));
            left[0] = helper.getTick();
        });
        helper.runAtTickTime(20, () -> {
            // levels tick before tests, so the ring's last hold is on the step-out tick itself; then 2 ticks of grace,
            // and the player's own tick on the third lifts it: exactly 3, every time
            helper.assertTrue(lifted[0] >= 0 && lifted[0] - left[0] == NulledEffect.GRACE + 1,
                    "the status lifts 3 ticks after stepping out, took " + (lifted[0] - left[0]));
            helper.assertTrue(!nulled(miner), "the miner's too");
            helper.assertTrue(near(sharpness(helper, p, victim), 9.0F), "Sharpness V again: 6 -> 9, got " + sharpness(helper, p, victim));
            float lost = hit(helper, p);
            helper.assertTrue(near(lost, 0.5616F), "Protection IV again: a plain 6 costs 0.5616, lost " + lost);
            helper.assertTrue(Math.abs(miner.getAttributeValue(Attributes.MINING_EFFICIENCY) - 26.0) < 1.0E-6,
                    "Efficiency V's +26 mining speed is back: " + miner.getAttributeValue(Attributes.MINING_EFFICIENCY));
            snap(helper, p, SIDE.add(3.0, 0, 0));
            snap(helper, miner, SIDE.add(3.0, 0, 3.0));
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(nulled(p) && nulled(miner), "back in the ring, Nulled again");
            helper.assertTrue(near(sharpness(helper, p, victim), 6.0F), "and Sharpness adds nothing again");
            float lost = hit(helper, p);
            helper.assertTrue(near(lost, 1.56F), "nor Protection, lost " + lost);
            helper.assertTrue(Math.abs(miner.getAttributeValue(Attributes.MINING_EFFICIENCY)) < 1.0E-6, "nor Efficiency");
            helper.succeed();
        });
    }

    /**
     * More enchantments, inside then outside: Feather Falling IV takes 12 off a fall's worth (EPF) outside, nothing
     * inside; Mending finds damaged gear to repair only outside; a trident with Loyalty III thrown from inside never
     * comes back, from outside it does. Curse of Binding holds either way.
     */
    private static void enchantments(GameTestHelper helper) {
        cantor(helper, SIDE);
        ServerPlayer p = player(helper, SIDE.add(3.0, 0, 0));
        ItemStack boots = enchanted(helper, Items.IRON_BOOTS, Enchantments.FEATHER_FALLING, 4);
        boots.enchant(ench(helper, Enchantments.MENDING), 1);
        boots.setDamageValue(50);
        p.setItemSlot(EquipmentSlot.FEET, boots);
        p.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, Enchantments.BINDING_CURSE, 1));
        float[] fall = new float[2];
        boolean[] mend = new boolean[2], bound = new boolean[2];
        int[] loyalty = new int[2];
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(nulled(p), "inside the ring");
            fall[0] = EnchantmentHelper.getDamageProtection(helper.getLevel(), p, p.damageSources().fall());
            mend[0] = EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, p, ItemStack::isDamaged).isPresent();
            bound[0] = EnchantmentHelper.has(p.getItemBySlot(EquipmentSlot.HEAD), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
            ItemStack trident = enchanted(helper, Items.TRIDENT, Enchantments.LOYALTY, 3);
            ThrownTrident thrown = new ThrownTrident(helper.getLevel(), p, trident);
            loyalty[0] = EnchantmentHelper.getTridentReturnToOwnerAcceleration(helper.getLevel(), trident, thrown);
            snap(helper, p, SIDE.add(9.5, 0, 0));
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(!nulled(p), "outside the ring");
            fall[1] = EnchantmentHelper.getDamageProtection(helper.getLevel(), p, p.damageSources().fall());
            mend[1] = EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, p, ItemStack::isDamaged).isPresent();
            bound[1] = EnchantmentHelper.has(p.getItemBySlot(EquipmentSlot.HEAD), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
            ItemStack trident = enchanted(helper, Items.TRIDENT, Enchantments.LOYALTY, 3);
            ThrownTrident thrown = new ThrownTrident(helper.getLevel(), p, trident);
            loyalty[1] = EnchantmentHelper.getTridentReturnToOwnerAcceleration(helper.getLevel(), trident, thrown);
            helper.assertTrue(near(fall[0], 0.0F) && near(fall[1], 12.0F), "Feather Falling IV: 0 inside, 12 outside; got " + fall[0] + ", " + fall[1]);
            helper.assertTrue(!mend[0] && mend[1], "Mending: nothing to repair inside, the boots outside; got " + mend[0] + ", " + mend[1]);
            helper.assertTrue(loyalty[0] == 0 && loyalty[1] > 0, "Loyalty III: no return inside, a return outside; got " + loyalty[0] + ", " + loyalty[1]);
            helper.assertTrue(bound[0] && bound[1], "Curse of Binding holds inside and out");
            helper.succeed();
        });
    }

    // ---- the counter: the collar ----------------------------------------------------------------------------------

    private static Arrow arrow(GameTestHelper helper, Vec3 at) {
        Vec3 a = helper.absoluteVec(at);
        return new Arrow(helper.getLevel(), a.x, a.y, a.z, new ItemStack(Items.ARROW), null);
    }

    /**
     * An arrow from a shooter 12.7 blocks off (outside the ring) knocks the collar off: it flinches, and the ring stays
     * where it stood. Walked 9 blocks away, the player beside it is free while the one at the shard is Nulled.
     */
    private static void counter(GameTestHelper helper) {
        Vec3 at = new Vec3(1.5, 1, 1.5);
        Nullcantor n = cantor(helper, at);
        ServerPlayer shooter = player(helper, new Vec3(10.5, 1, 10.5));
        ServerPlayer beside = player(helper, new Vec3(10.5, 1, 2.5));
        ServerPlayer atShard = player(helper, new Vec3(2.5, 1, 2.5));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(!n.collarOff() && !nulled(shooter), "a whole collar, and the shooter stands outside the ring");
            helper.assertTrue(n.hurtServer(helper.getLevel(), n.damageSources().arrow(arrow(helper, new Vec3(9.5, 2, 9.5)), shooter), 2.0F), "the arrow hurts it");
            helper.assertTrue(n.collarOff() && n.core().in(Step.CRACKED), "the collar comes off and it flinches, phase " + n.phase());
            helper.assertTrue(n.shard() != null && n.shard().distanceTo(helper.absoluteVec(at)) < 0.01, "the shard lies where it stood");
            helper.assertTrue(n.core().flag(Nullcantor.FLAG_COLLAR_OFF), "the model shows the broken collar");
            snap(helper, n, new Vec3(10.5, 1, 1.5));                           // it walks on, 9 blocks
        });
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(!nulled(beside), "a block from the Nullcantor, but 9 from the shard: not Nulled");
            helper.assertTrue(nulled(atShard), "a block from the shard: Nulled");
            helper.assertTrue(n.ringCentre().distanceTo(helper.absoluteVec(at)) < 0.01, "the ring sits on the shard");
            helper.succeed();
        });
    }

    /** A hit from an attacker inside the ring does nothing to the collar. */
    private static void insideHit(GameTestHelper helper) {
        Nullcantor n = cantor(helper, SIDE);
        ServerPlayer near = player(helper, SIDE.add(5.0, 0, 0));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(nulled(near), "the attacker stands inside the ring");
            n.hurtServer(helper.getLevel(), n.damageSources().arrow(arrow(helper, SIDE.add(4.0, 1, 0)), near), 2.0F);
            helper.assertTrue(!n.collarOff() && n.cracks() == 0 && !n.core().in(Step.CRACKED), "the collar stays on, phase " + n.phase());
            helper.assertTrue(n.ringCentre().distanceTo(n.position()) < 1.0E-6, "the ring stays on it");
            helper.succeed();
        });
    }

    /** The shard holds the ring for 160 ticks; then the collar grows back and the ring is round the Nullcantor again. */
    private static void shardTime(GameTestHelper helper) {
        Nullcantor n = cantor(helper, SIDE);
        ServerPlayer shooter = player(helper, SIDE.add(9.5, 0, 0));
        long[] off = {-1}, back = {-1};
        helper.runAtTickTime(5, () -> {
            n.hurtServer(helper.getLevel(), n.damageSources().arrow(arrow(helper, SIDE.add(9.0, 1, 0)), shooter), 2.0F);
            helper.assertTrue(n.collarOff(), "an arrow from 9.5 blocks knocks the collar off");
            off[0] = helper.getLevel().getGameTime();
        });
        helper.onEachTick(() -> {
            if (off[0] >= 0 && back[0] < 0 && !n.collarOff()) back[0] = helper.getLevel().getGameTime();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(back[0] >= 0, "the collar grows back");
            helper.assertTrue(Math.abs(back[0] - off[0] - 160) <= 1, "the shard lasts 160 ticks, lasted " + (back[0] - off[0]));
            helper.assertTrue(!n.core().flag(Nullcantor.FLAG_COLLAR_OFF), "the model shows the whole collar again");
            helper.assertTrue(n.ringCentre().distanceTo(n.position()) < 1.0E-6, "the ring is round the Nullcantor again");
        });
    }

    /** The hum: every 40 ticks. */
    private static void hum(GameTestHelper helper) {
        Nullcantor n = cantor(helper, SIDE);
        List<Long> at = new ArrayList<>();
        int[] seen = {0};
        helper.onEachTick(() -> {
            if (n.hums() > seen[0]) {
                seen[0] = n.hums();
                at.add(helper.getLevel().getGameTime());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.size() >= 2, "it hummed twice, at " + at);
            helper.assertTrue(at.get(1) - at.get(0) == 40, "a hum every 40 ticks, got " + (at.get(1) - at.get(0)));
            helper.assertTrue(Nullcantor.HUM_VOLUME > 1.0F, "loud enough to carry past the ring (16 blocks x its volume)");
        });
    }

    /** A listener that cancels the "null" step keeps a player in the ring free. */
    private static void nullCancel(GameTestHelper helper) {
        Nullcantor n = cantor(helper, SIDE);
        ServerPlayer p = player(helper, SIDE.add(3.0, 0, 0));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == n && e.step().equals("null")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(n.ringHolds(p), "the player stands in the ring");
            helper.assertTrue(!nulled(p), "but a vetoed step nulls no one");
            helper.succeed();
        });
    }

    /**
     * The status: named and described, harmful, no end of its own, no particles, an icon; with no ring holding its
     * bearer it lifts by itself within 3 ticks, so it can't be carried away from a ring.
     */
    private static void status(GameTestHelper helper) {
        Language lang = Language.getInstance();
        String key = ModEffects.NULLED.get().getDescriptionId();
        helper.assertTrue(key.equals("effect.teeming.nulled") && lang.has(key) && lang.has(key + ".description"), "named and described: " + key);
        helper.assertTrue(ModEffects.NULLED.get().getCategory() == MobEffectCategory.HARMFUL, "harmful");
        MobEffectInstance given = NulledEffect.instance();
        helper.assertTrue(given.isInfiniteDuration() && given.showIcon() && !given.isVisible(), "no end of its own, an icon, no particles");
        long[] gone = {-1};
        ServerPlayer p = player(helper, new Vec3(5.5, 1, 5.5), self -> {
            if (gone[0] < 0 && !nulled(self)) gone[0] = helper.getTick();
        });
        p.addEffect(NulledEffect.instance());
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(gone[0] >= 0 && gone[0] <= 3, "with no ring it lifts within 3 ticks, at " + gone[0]);
            helper.succeed();
        });
    }

    // ---- the roster row --------------------------------------------------------------------------------------------

    private static void stats(GameTestHelper helper) {
        Nullcantor n = cantor(helper, SIDE);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(near(n.getMaxHealth(), 40.0F) && near(n.getHealth(), 40.0F), "health 40, got " + n.getHealth() + "/" + n.getMaxHealth());
            helper.assertTrue(Math.abs(n.getAttributeValue(Attributes.ATTACK_DAMAGE) - 8.5) < 0.01, "attack 8.5");
            helper.assertTrue(Math.abs(n.getAttributeValue(Attributes.ARMOR) - 4.0) < 0.01, "armour 4");
            helper.assertTrue(Math.abs(n.getBbWidth() - 0.7F) < 0.02F && Math.abs(n.getBbHeight() - 2.0F) < 0.02F,
                    "hitbox 0.7 x 2.0, got " + n.getBbWidth() + " x " + n.getBbHeight());
            helper.assertTrue(n.ringCentre().distanceTo(n.position()) < 1.0E-6 && !n.collarOff(), "its ring is on from the start, round it");
            helper.succeed();
        });
    }

    /**
     * Home: the End barrens by biome spawn, weight 3 in groups of 1, and the main island through the Teeming spawner's
     * main-island rule. The Pale Cantor is its second look; it drops void ichor.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(TeemingSpawns.tag("nullcantor"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "nullcantor").stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "its home holds the End barrens");
        for (var other : List.of(Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS, Biomes.SMALL_END_ISLANDS, Biomes.PLAINS)) {
            helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "nullcantor").stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("nullcantor");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.NULLCANTOR.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.assertTrue(row.spawner().equals("main_island") && TeemingSpawner.Rule.of(row.spawner()) == TeemingSpawner.Rule.MAIN_ISLAND,
                "the spawner places it on the main island, rule " + row.spawner());
        helper.assertTrue(TeemingSpawner.Rule.MAIN_ISLAND.dimension.equals(Level.END), "an End rule");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/nullcantor")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.NULLCANTOR.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 3, "spawn weight 3, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("nullcantor");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("pale_cantor")), "the Pale Cantor is a look: " + looks);
        helper.assertTrue(row.material().equals("void_ichor") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_ichor")),
                "it drops void ichor, a registered item");
        helper.succeed();
    }
}
