package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.ShredjawBeetle;
import com.panda.teeming.mob.nether.ShredjawBeetle.Step;
import com.panda.teeming.mob.nether.ShredjawBeetle.Sunder;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * Shredjaw Beetle: a 12-tick jaw spread that does nothing yet, then a bite for 5 that takes 4 armour per stack of
 * Sunder (at most 3 stacks, for 200 ticks) and 2 more durability from every worn armour piece; a shield raised toward
 * it takes the whole bite, and the beetle reels with its own chitin cracked (armour 8 down to 4). Every number is
 * checked against a plain value.
 *
 * <p>Every beetle here has its goals cleared and a follow range of 1, so it never notices a survival player in a
 * neighbouring arena; its target is given to it directly, and its bite cycle still runs. Pigs have no AI and 100
 * health, so they stand where they are put and survive several bites. Husks wear iron armour (vanilla never wears a
 * mob's armour down when it is hurt, so any loss is the bite's own).
 */
public final class ShredjawBeetleTests {
    private ShredjawBeetleTests() {}

    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("shredjaw_beetle_tell", ShredjawBeetleTests::tell);
        out.accept("shredjaw_beetle_counter", ShredjawBeetleTests::shieldBlocks);
        out.accept("shredjaw_beetle_failure", ShredjawBeetleTests::bittenPlayer);
        out.accept("shredjaw_beetle_bite_damage", ShredjawBeetleTests::biteDamage);
        out.accept("shredjaw_beetle_sunder_armour", ShredjawBeetleTests::sunderArmour);
        out.accept("shredjaw_beetle_sunder_max", ShredjawBeetleTests::sunderMax);
        TeemingGameTests.timeLimit("shredjaw_beetle_sunder_max", 160);
        out.accept("shredjaw_beetle_sunder_length", ShredjawBeetleTests::sunderLength);
        TeemingGameTests.timeLimit("shredjaw_beetle_sunder_length", 280);
        out.accept("shredjaw_beetle_wear", ShredjawBeetleTests::wear);
        out.accept("shredjaw_beetle_sunder_victim_dies", ShredjawBeetleTests::victimDies);
        out.accept("shredjaw_beetle_sunder_beetle_dies", ShredjawBeetleTests::beetleDies);
        out.accept("shredjaw_beetle_backed_off", ShredjawBeetleTests::backedOff);
        out.accept("shredjaw_beetle_bite_cancel", ShredjawBeetleTests::biteCancel);
        out.accept("shredjaw_beetle_stats", ShredjawBeetleTests::stats);
        out.accept("shredjaw_beetle_home", ShredjawBeetleTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A beetle at {@code at} with no goals and a follow range of 1, hunting {@code target}; the base look; it leaves with the test. */
    private static ShredjawBeetle beetle(GameTestHelper helper, Vec3 at, @Nullable LivingEntity target) {
        ShredjawBeetle b = helper.spawn(ModMobs.SHREDJAW_BEETLE.get(), at);
        b.core().setLook("base", true);
        b.removeFreeWill();
        b.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        b.setTarget(target);
        b.resetCooldown();
        TestPlayers.whenDone(helper, b::discard);
        return b;
    }

    /** A pig that stands where it is put, with 100 health. */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A husk with no goals in a full set of iron armour. */
    private static Husk husk(GameTestHelper helper, Vec3 at) {
        Husk husk = helper.spawn(EntityTypes.HUSK, at);
        husk.removeFreeWill();
        dress(husk);
        TestPlayers.whenDone(helper, husk::discard);
        return husk;
    }

    private static void dress(LivingEntity e) {
        e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        e.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }

    /** Durability each armour piece has lost, head to feet. */
    private static List<Integer> worn(LivingEntity e) {
        List<Integer> out = new ArrayList<>();
        for (EquipmentSlot slot : ARMOUR) out.add(e.getItemBySlot(slot).getDamageValue());
        return out;
    }

    /** The Sunder modifier's amount on {@code e}'s armour, 0 with none. */
    private static double sunder(LivingEntity e) {
        AttributeModifier m = e.getAttribute(Attributes.ARMOR).getModifier(Sunder.ID);
        return m == null ? 0.0 : m.amount();
    }

    private static float lost(Pig pig) {
        return 100.0F - pig.getHealth();
    }

    /** Records the test tick of each mechanic step of one beetle; it stops listening when the test ends. */
    private static final class Log implements Consumer<MechanicEvent> {
        final ShredjawBeetle beetle;
        final List<Integer> tells = new ArrayList<>(), bites = new ArrayList<>(), sunders = new ArrayList<>(),
                wears = new ArrayList<>(), jars = new ArrayList<>(), cracks = new ArrayList<>();
        boolean cancelBite;
        int tick;

        Log(GameTestHelper helper, ShredjawBeetle beetle) {
            this.beetle = beetle;
            MechanicEvent.listen(this);
            TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(this));
            helper.onEachTick(() -> tick++);
        }

        @Override
        public void accept(MechanicEvent e) {
            if (e.mob() != beetle) return;
            switch (e.step()) {
                case "tell" -> tells.add(tick);
                case "bite" -> {
                    bites.add(tick);
                    if (cancelBite) e.cancel();
                }
                case "sunder" -> sunders.add(tick);
                case "wear" -> wears.add(tick);
                case "jarred" -> jars.add(tick);
                case "crack" -> cracks.add(tick);
                default -> { }
            }
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /** The jaws spread for exactly 12 ticks: it stands still and hurts nothing, and the bite follows. */
    private static void tell(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5));
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), pig);
        Log log = new Log(helper, b);
        int[] ticks = {0};
        boolean[] quiet = {true}, ended = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (b.core().in(Step.TELL)) {
                if (start[0] == null) start[0] = b.position();
                ticks[0]++;
                moved[0] = Math.max(moved[0], b.position().distanceTo(start[0]));
                if (!log.bites.isEmpty() || lost(pig) > 0.0F || sunder(pig) != 0.0) quiet[0] = false;
            } else if (ticks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the jaws spread and close, " + ticks[0] + " ticks so far");
            helper.assertTrue(log.tells.size() == 1 && log.tells.getFirst() <= 3, "with its target beside it the jaws spread at once: " + log.tells);
            helper.assertTrue(ticks[0] == 12, "the jaws stay spread for 12 ticks, " + ticks[0]);
            helper.assertTrue(quiet[0], "nothing is bitten, hurt or sundered while the jaws are spread");
            helper.assertTrue(moved[0] < 0.05, "it stands still while its jaws are spread, moved " + moved[0]);
            helper.assertTrue(log.bites.size() == 1, "the bite follows the tell, saw " + log.bites.size());
        });
    }

    /**
     * The counter: a shield raised toward it takes the bite. The player keeps their health, their armour and every
     * point of durability, and has no Sunder; the beetle reels and its own chitin cracks, armour 8 down to 4.
     */
    private static void shieldBlocks(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.9, 1, 5.5));
        dress(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            p.setYRot(90.0F);            // facing -x, toward the beetle
            p.setYHeadRot(90.0F);
            p.yHeadRotO = 90.0F;
        });
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), p);
        Log log = new Log(helper, b);
        float before = p.getHealth();
        boolean[] jarred = {false};
        helper.onEachTick(() -> jarred[0] |= b.core().in(Step.JARRED));
        helper.succeedWhen(() -> {
            helper.assertTrue(p.isBlocking(), "the player holds the shield up");
            helper.assertTrue(log.tells.size() == 1 && jarred[0], "the jaws closed on the shield and it reels, phase " + b.phase());
            helper.assertTrue(log.sunders.isEmpty() && log.wears.isEmpty(), "no Sunder and no wear were tried");
            helper.assertTrue(p.getHealth() >= before, "the player is unhurt, health " + p.getHealth());
            helper.assertTrue(sunder(p) == 0.0 && Sunder.stacks(p) == 0, "the player has no Sunder, modifier " + sunder(p));
            helper.assertTrue(worn(p).equals(List.of(0, 0, 0, 0)), "not a point of armour durability lost, " + worn(p));
            helper.assertTrue(log.cracks.size() == 1 && Sunder.stacks(b) == 1, "its own chitin cracked once, stacks " + Sunder.stacks(b));
            helper.assertTrue(Math.abs(b.getAttributeValue(Attributes.ARMOR) - 4.0) < 0.01, "its armour 8 drops to 4, " + b.getAttributeValue(Attributes.ARMOR));
        });
    }

    /**
     * Failing the counter: a player in a full iron set (armour 15) takes the bite. One stack of Sunder takes armour to
     * 11, and every piece loses 3 durability: 1 from the hit, as any hit costs, and 2 more from the jaws.
     */
    private static void bittenPlayer(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.9, 1, 5.5));
        dress(p);
        p.doTick();                       // the armour counts from the player's next tick
        double armourBefore = p.getAttributeValue(Attributes.ARMOR);
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), p);
        Log log = new Log(helper, b);
        float before = p.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(armourBefore - 15.0) < 0.01, "an iron set gives armour 15, got " + armourBefore);
            helper.assertTrue(log.sunders.size() == 1, "the bite landed and sundered, " + log.sunders);
            helper.assertTrue(p.getHealth() < before, "the bite hurt, health " + p.getHealth());
            helper.assertTrue(Sunder.stacks(p) == 1 && Math.abs(sunder(p) + 4.0) < 0.01, "one stack, -4 armour, modifier " + sunder(p));
            helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - 11.0) < 0.01, "armour 15 drops to 11, " + p.getAttributeValue(Attributes.ARMOR));
            helper.assertTrue(worn(p).equals(List.of(3, 3, 3, 3)), "every piece lost 1 from the hit and 2 from the jaws, " + worn(p));
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** A bite does its attack damage, 5, to an unarmoured pig. */
    private static void biteDamage(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5));
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), pig);
        Log log = new Log(helper, b);
        helper.succeedWhen(() -> {
            helper.assertTrue(log.sunders.size() == 1, "one bite landed, " + log.sunders);
            helper.assertTrue(Math.abs(lost(pig) - 5.0F) < 0.01F, "the bite does 5, the pig lost " + lost(pig));
        });
    }

    /** One stack of Sunder takes exactly 4 armour: a husk in iron (2 of its own and 15) goes from 17 to 13. */
    private static void sunderArmour(GameTestHelper helper) {
        Husk husk = husk(helper, new Vec3(5.8, 1, 5.5));
        double[] before = {-1.0};
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), null);
        Log log = new Log(helper, b);
        helper.runAtTickTime(3, () -> {
            before[0] = husk.getAttributeValue(Attributes.ARMOR);   // its armour counts from its own tick
            b.setTarget(husk);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(before[0] - 17.0) < 0.01, "the husk starts with armour 17, " + before[0]);
            helper.assertTrue(log.sunders.size() == 1, "one bite landed, " + log.sunders);
            helper.assertTrue(Math.abs(husk.getAttributeValue(Attributes.ARMOR) - 13.0) < 0.01, "one stack takes 4: armour 13, " + husk.getAttributeValue(Attributes.ARMOR));
        });
    }

    /** Four bites, but Sunder stops at 3 stacks: -12 armour, never -16. */
    private static void sunderMax(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5));
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), pig);
        Log log = new Log(helper, b);
        List<Integer> stacks = new ArrayList<>();
        helper.onEachTick(() -> {
            if (b.core().idle() && b.cooldown() > 0) b.resetCooldown();   // the bites come back to back
            if (stacks.size() < log.sunders.size()) stacks.add(Sunder.stacks(pig));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.sunders.size() >= 4 && stacks.size() >= 4, "four bites landed, " + log.sunders.size());
            helper.assertTrue(stacks.subList(0, 4).equals(List.of(1, 2, 3, 3)), "stacks after each bite " + stacks);
            helper.assertTrue(Math.abs(sunder(pig) + 12.0) < 0.01, "three stacks at most: -12 armour, modifier " + sunder(pig));
            helper.assertTrue(Math.abs(lost(pig) - 5.0F * log.sunders.size()) < 0.01F, "each bite still does 5, lost " + lost(pig));
        });
    }

    /** One bite's Sunder lasts 200 ticks, then the modifier is gone. */
    private static void sunderLength(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5));
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), pig);
        Log log = new Log(helper, b);
        int[] on = {0};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (!log.sunders.isEmpty()) b.setTarget(null);               // one bite only
            if (sunder(pig) != 0.0) on[0]++;
            else if (on[0] > 0) over[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.sunders.size() == 1, "one bite landed, " + log.sunders);
            helper.assertTrue(over[0], "the Sunder wears off, " + on[0] + " ticks so far");
            helper.assertTrue(Math.abs(on[0] - 200) <= 1, "the Sunder lasts 200 ticks, lasted " + on[0]);
            helper.assertTrue(Sunder.stacks(pig) == 0, "no stacks are left");
        });
    }

    /** The jaws wear each worn armour piece by 2 (a husk's armour loses nothing else when it is hurt). */
    private static void wear(GameTestHelper helper) {
        Husk husk = husk(helper, new Vec3(5.8, 1, 5.5));
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), husk);
        Log log = new Log(helper, b);
        helper.succeedWhen(() -> {
            helper.assertTrue(log.wears.size() == 1, "one bite landed, " + log.wears);
            helper.assertTrue(worn(husk).equals(List.of(2, 2, 2, 2)), "every piece lost 2, " + worn(husk));
        });
    }

    /** A sundered victim that dies loses its Sunder at once, and the modifier is never a saved (permanent) one. */
    private static void victimDies(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5));
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), pig);
        Log log = new Log(helper, b);
        int[] killedAt = {-1};
        boolean[] transientOnly = {true};
        helper.onEachTick(() -> {
            if (!log.sunders.isEmpty() && killedAt[0] < 0) {
                b.setTarget(null);
                transientOnly[0] = pig.getAttribute(Attributes.ARMOR).getPermanentModifiers().stream().noneMatch(m -> m.id().equals(Sunder.ID));
                killedAt[0] = log.tick;
                pig.kill(helper.getLevel());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killedAt[0] > 0 && log.tick >= killedAt[0] + 2, "the sundered pig was killed");
            helper.assertTrue(transientOnly[0], "Sunder is a transient modifier, never saved with its victim");
            helper.assertTrue(sunder(pig) == 0.0 && Sunder.stacks(pig) == 0, "a dead victim keeps no Sunder, modifier " + sunder(pig));
        });
    }

    /** The beetle that bit a victim dies: no beetle is left to time it, so the victim's Sunder lifts at once. */
    private static void beetleDies(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5));
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), pig);
        Log log = new Log(helper, b);
        double[] whileAlive = {0.0};
        helper.onEachTick(() -> {
            if (!log.sunders.isEmpty() && b.isAlive()) {
                whileAlive[0] = sunder(pig);
                b.kill(helper.getLevel());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(whileAlive[0] + 4.0) < 0.01, "the bite sundered the pig, modifier " + whileAlive[0]);
            helper.assertTrue(!b.isAlive(), "the beetle is dead");
            helper.assertTrue(sunder(pig) == 0.0 && Sunder.stacks(pig) == 0, "its victim's Sunder lifted, modifier " + sunder(pig));
        });
    }

    /** Backing out of reach while the jaws are spread: they close on air, nothing is hurt or sundered. */
    private static void backedOff(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5));
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), pig);
        Log log = new Log(helper, b);
        helper.onEachTick(() -> {
            if (b.core().in(Step.TELL) && b.core().elapsed() == 6) pig.snapTo(helper.absoluteVec(new Vec3(8.5, 1, 5.5)));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.bites.size() == 1, "the jaws closed, " + log.bites);
            helper.assertTrue(b.core().idle() && b.cooldown() > 0, "the bite is over, phase " + b.phase());
            helper.assertTrue(lost(pig) == 0.0F && log.sunders.isEmpty() && sunder(pig) == 0.0, "a pig 3 blocks off is not bitten, lost " + lost(pig));
        });
    }

    /** A listener that cancels the "bite" step stops it: no damage and no Sunder. */
    private static void biteCancel(GameTestHelper helper) {
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5));
        ShredjawBeetle b = beetle(helper, new Vec3(4.5, 1, 5.5), pig);
        Log log = new Log(helper, b);
        log.cancelBite = true;
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(log.tells.size() == 1 && log.bites.size() == 1, "the tell ran and the bite was asked for: " + log.tells + " " + log.bites);
            helper.assertTrue(lost(pig) == 0.0F && sunder(pig) == 0.0, "a cancelled bite hurts and sunders nothing, lost " + lost(pig));
            helper.succeed();
        });
    }

    /** Health 30, attack 5, armour 8, a 1.1 x 0.8 hitbox; a monster; looks base and Warped Shredjaw. */
    private static void stats(GameTestHelper helper) {
        ShredjawBeetle b = beetle(helper, new Vec3(5.5, 1, 5.5), null);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(b.getMaxHealth() - 30.0F) < 0.01F && Math.abs(b.getHealth() - 30.0F) < 0.01F,
                    "health 30, got " + b.getHealth() + "/" + b.getMaxHealth());
            helper.assertTrue(Math.abs(b.getAttributeValue(Attributes.ATTACK_DAMAGE) - 5.0) < 0.01, "attack 5");
            helper.assertTrue(Math.abs(b.getAttributeValue(Attributes.ARMOR) - 8.0) < 0.01, "armour 8");
            helper.assertTrue(Math.abs(b.getBbWidth() - 1.1F) < 0.02F && Math.abs(b.getBbHeight() - 0.8F) < 0.02F,
                    "hitbox 1.1 x 0.8, got " + b.getBbWidth() + " x " + b.getBbHeight());
            helper.assertTrue(b.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(!b.vulnerable(), "it has no weak point");
            List<String> looks = Looks.of("shredjaw_beetle").stream().map(LookDef::id).sorted().toList();
            helper.assertTrue(looks.equals(List.of("base", "warped_shredjaw")), "looks base and warped_shredjaw, got " + looks);
            helper.succeed();
        });
    }

    /** Home: the crimson forest and not the nether wastes; spawn weight 12, in groups of 1 to 2. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("shredjaw_beetle"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(bi -> bi.is(Biomes.CRIMSON_FOREST)), "its home holds the crimson forest");
        helper.assertTrue(tag.get().stream().noneMatch(bi -> bi.is(Biomes.NETHER_WASTES)), "its home never holds the nether wastes");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/shredjaw_beetle")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SHREDJAW_BEETLE.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 12, "spawn weight 12, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "in groups of 1 to 2, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
