package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.nether.AshJackal;
import com.panda.teeming.mob.nether.AshJackal.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Ash Jackal: health 16, attack 4, armour 1, a 0.7 x 0.8 hitbox and no tell (a plain bite of 4); it takes one dropped
 * stack within 10 blocks (one stack each), runs off with it for 200 ticks and drops it when killed; it picks the
 * lowest health fraction within 16 blocks. Every number is checked against a plain value.
 *
 * <p>Every jackal here is confined to its own arena (it takes items and picks fights only inside it), so it never
 * reaches into the arenas beside it, and gives up what it carries before it is removed when the test ends.
 */
public final class AshJackalTests {
    private AshJackalTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("ash_jackal_tell", AshJackalTests::tell);
        out.accept("ash_jackal_counter", AshJackalTests::counter);
        out.accept("ash_jackal_failure", AshJackalTests::failure);
        out.accept("ash_jackal_stats", AshJackalTests::stats);
        out.accept("ash_jackal_item_range", AshJackalTests::itemRange);
        TeemingGameTests.timeLimit("ash_jackal_item_range", 160);
        out.accept("ash_jackal_one_stack", AshJackalTests::oneStack);
        TeemingGameTests.timeLimit("ash_jackal_one_stack", 160);
        out.accept("ash_jackal_nearer_takes_over", AshJackalTests::nearerTakesOver);
        out.accept("ash_jackal_flee_length", AshJackalTests::fleeLength);
        TeemingGameTests.timeLimit("ash_jackal_flee_length", 280);
        out.accept("ash_jackal_weakest", AshJackalTests::weakest);
        out.accept("ash_jackal_target_range", AshJackalTests::targetRange);
        out.accept("ash_jackal_carry_display", AshJackalTests::carryDisplay);
        out.accept("ash_jackal_never_from_players", AshJackalTests::neverFromPlayers);
        out.accept("ash_jackal_griefing_rule", AshJackalTests::griefingRule);
        out.accept("ash_jackal_save_load", AshJackalTests::saveLoad);
        out.accept("ash_jackal_home", AshJackalTests::home);
    }

    /** A jackal with the base look, confined to this arena, emptied and removed when the test ends wherever it ran to. */
    private static AshJackal jackal(GameTestHelper helper, double x, double z) {
        AshJackal jackal = helper.spawn(ModMobs.ASH_JACKAL.get(), new Vec3(x, 1, z));
        jackal.core().setLook("base", true);
        jackal.confine(helper.getBounds());
        TestPlayers.whenDone(helper, () -> {
            jackal.takeCarried();
            jackal.discard();
        });
        return jackal;
    }

    /** A still pig with {@code max} health and {@code health} left. */
    private static Pig pig(GameTestHelper helper, double x, double z, float max, float health) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        pig.setHealth(health);
        return pig;
    }

    /** A dropped stack lying still at (x, z), free to pick up. */
    private static ItemEntity item(GameTestHelper helper, double x, double z, ItemStack stack) {
        Vec3 at = helper.absoluteVec(new Vec3(x, 1, z));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        item.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    /** Every item display riding the jackal. */
    private static List<Display.ItemDisplay> displays(AshJackal jackal) {
        return jackal.getPassengers().stream().filter(p -> p instanceof Display.ItemDisplay).map(p -> (Display.ItemDisplay) p).toList();
    }

    /** No tell: from first sight of its target to the bite it never leaves idle, and the first bite takes exactly 4. */
    private static void tell(GameTestHelper helper) {
        AshJackal jackal = jackal(helper, 3.5, 5.5);
        Pig pig = pig(helper, 8.5, 5.5, 100.0F, 100.0F);
        jackal.setTarget(pig);
        boolean[] phased = {false};
        float[] first = {0.0F};
        helper.onEachTick(() -> {
            if (first[0] == 0.0F && pig.getHealth() < 100.0F) first[0] = 100.0F - pig.getHealth();
            if (first[0] == 0.0F && !jackal.core().idle()) phased[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first[0] > 0.0F, "it bit the pig");
            helper.assertTrue(!phased[0], "no tell or other phase before the bite");
            helper.assertTrue(Math.abs(first[0] - 4.0F) < 0.01F, "a plain bite takes 4, took " + first[0]);
            helper.assertTrue(jackal.bites() >= 1, "it counts the bite");
        });
    }

    /**
     * The counter: a carrier is killed and drops the stack it took, all of it (5 gold ingots), where it fell; its item
     * display goes with it; a packmate beside it leaves the dropped stack alone.
     */
    private static void counter(GameTestHelper helper) {
        AshJackal carrier = jackal(helper, 4.5, 6.5);
        AshJackal mate = jackal(helper, 8.5, 6.5);
        mate.setNoAi(true);
        item(helper, 5.0, 6.5, new ItemStack(Items.GOLD_INGOT, 5));
        boolean[] killed = {false};
        Vec3[] fell = {null};
        helper.onEachTick(() -> {
            if (killed[0] || carrier.carried().isEmpty()) return;
            helper.assertTrue(carrier.carried().is(Items.GOLD_INGOT) && carrier.carried().getCount() == 5, "it carries the whole stack of 5, " + carrier.carried());
            fell[0] = carrier.position();
            carrier.kill(helper.getLevel());
            killed[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0], "it took the stack and was killed");
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(fell[0], fell[0]).inflate(2.0),
                    e -> e.getItem().is(Items.GOLD_INGOT));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 5, "it drops the 5 gold where it fell, found " + drops);
            helper.assertTrue(displays(carrier).isEmpty() && helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class,
                    new AABB(fell[0], fell[0]).inflate(3.0)).isEmpty(), "its item display is gone");
            helper.assertTrue(mate.findItem() == null, "a packmate leaves the dropped stack alone");
        });
    }

    /**
     * Failing it: a jackal takes 3 diamonds four blocks from a survival player and gets away: the stack is off the
     * ground, the player has none of it, 40 ticks into its run it is more than 2 blocks farther from the player, and it
     * will not despawn while it carries the stack.
     */
    private static void failure(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(2.5, 1, 6.5));
        AshJackal jackal = jackal(helper, 6.0, 6.5);
        helper.assertTrue(!jackal.requiresCustomPersistence(), "with an empty mouth it may despawn");
        item(helper, 6.6, 6.5, new ItemStack(Items.DIAMOND, 3));
        double[] start = {-1};
        int[] fleeing = {0};
        helper.onEachTick(() -> {
            if (!jackal.core().in(Step.FLEE)) return;
            if (fleeing[0]++ == 0) start[0] = jackal.distanceTo(player);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(fleeing[0] >= 40, "it runs off with the stack, " + fleeing[0] + " ticks so far");
            helper.assertTrue(jackal.carried().is(Items.DIAMOND) && jackal.carried().getCount() == 3, "it carries the 3 diamonds, " + jackal.carried());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).stream().noneMatch(e -> e.getItem().is(Items.DIAMOND)),
                    "the stack is off the ground");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "the player has none of it");
            double gain = jackal.distanceTo(player) - start[0];
            helper.assertTrue(gain > 2.0, "it got more than 2 blocks farther from the player, by " + gain);
            helper.assertTrue(jackal.requiresCustomPersistence(), "it never despawns while it carries the stack");
        });
    }

    /** Health 16, attack 4, armour 1, a 0.7 x 0.8 hitbox. */
    private static void stats(GameTestHelper helper) {
        AshJackal jackal = jackal(helper, 5.5, 5.5);
        jackal.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(jackal.getMaxHealth() == 16.0F && jackal.getHealth() == 16.0F, "health 16, got " + jackal.getHealth() + "/" + jackal.getMaxHealth());
            helper.assertTrue(jackal.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4.0, "attack 4, got " + jackal.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(jackal.getArmorValue() == 1, "armour 1, got " + jackal.getArmorValue());
            helper.assertTrue(Math.abs(jackal.getBbWidth() - 0.7F) < 0.01F && Math.abs(jackal.getBbHeight() - 0.8F) < 0.01F,
                    "a 0.7 x 0.8 hitbox, got " + jackal.getBbWidth() + " x " + jackal.getBbHeight());
            helper.succeed();
        });
    }

    /**
     * Item range 10: a stack 10.54 blocks off is out of reach; once one lies 9.55 blocks off it goes for that, runs
     * over and takes it.
     */
    private static void itemRange(GameTestHelper helper) {
        AshJackal jackal = jackal(helper, 1.5, 1.5);
        jackal.setNoAi(true);
        item(helper, 9.0, 8.9, new ItemStack(Items.STICK, 2));             // 10.54 blocks
        ItemEntity[] near = {null};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(jackal.findItem() == null, "a stack 10.54 blocks off is out of range, it went for " + jackal.findItem());
            near[0] = item(helper, 8.5, 8.0, new ItemStack(Items.BONE, 4));       // 9.55 blocks
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(jackal.findItem() == near[0], "a stack 9.55 blocks off is in range, it went for " + jackal.findItem());
            jackal.setNoAi(false);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(jackal.carried().is(Items.BONE) && jackal.carried().getCount() == 4, "it ran over and took the 4 bones, carries " + jackal.carried());
            helper.assertTrue(!near[0].isAlive(), "they are off the ground");
        });
    }

    /**
     * One stack each: two jackals with one stack nearest to both and a second farther off: they never go for the same
     * one, so each ends up with a different whole stack, and neither takes a third that lies near by.
     */
    private static void oneStack(GameTestHelper helper) {
        AshJackal a = jackal(helper, 4.5, 4.5);
        AshJackal b = jackal(helper, 7.5, 4.5);
        item(helper, 6.0, 4.5, new ItemStack(Items.GOLD_INGOT, 2));       // 1.5 from both
        item(helper, 6.0, 8.5, new ItemStack(Items.BONE, 4));             // farther from both
        ItemEntity third = item(helper, 3.0, 9.5, new ItemStack(Items.STICK, 7));
        helper.succeedWhen(() -> {
            ItemStack ca = a.carried(), cb = b.carried();
            helper.assertTrue(!ca.isEmpty() && !cb.isEmpty(), "both carry a stack, " + ca + " and " + cb);
            helper.assertTrue(ca.getItem() != cb.getItem(), "different stacks, " + ca + " and " + cb);
            for (ItemStack c : List.of(ca, cb)) {
                helper.assertTrue((c.is(Items.GOLD_INGOT) && c.getCount() == 2) || (c.is(Items.BONE) && c.getCount() == 4), "a whole stack, " + c);
            }
            helper.assertTrue(a.snatches() == 1 && b.snatches() == 1, "one stack each, took " + a.snatches() + " and " + b.snatches());
            helper.assertTrue(third.isAlive() && third.getItem().getCount() == 7, "the third stack stays on the ground");
        });
    }

    /**
     * A stack one jackal is after is left to it by a packmate farther off, but a packmate nearer to it takes it over: the
     * nearest free jackal gets each stack.
     */
    private static void nearerTakesOver(GameTestHelper helper) {
        AshJackal runner = jackal(helper, 6.0, 9.5);
        runner.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);   // goes for it but never gets there
        AshJackal near = jackal(helper, 6.0, 7.5);
        near.setNoAi(true);
        AshJackal far = jackal(helper, 6.0, 1.5);
        far.setNoAi(true);
        ItemEntity gold = item(helper, 6.0, 6.0, new ItemStack(Items.GOLD_INGOT, 3));   // 3.5, 1.5 and 4.5 blocks off
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(runner.goingFor() == gold, "the first jackal goes for the stack, " + runner.goingFor());
            helper.assertTrue(far.findItem() == null, "a packmate farther off leaves it to that one, went for " + far.findItem());
            helper.assertTrue(near.findItem() == gold, "a packmate nearer to it takes it over, went for " + near.findItem());
            helper.succeed();
        });
    }

    /** It runs off for exactly 200 ticks after it takes a stack, then stops, still carrying it. */
    private static void fleeLength(GameTestHelper helper) {
        AshJackal jackal = jackal(helper, 6.0, 6.5);
        jackal.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);   // stays on stage
        item(helper, 6.5, 6.5, new ItemStack(Items.EMERALD, 1));
        int[] fled = {0};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (jackal.core().in(Step.FLEE)) fled[0]++;
            else if (fled[0] > 0) over[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the run ends, " + fled[0] + " ticks so far");
            helper.assertTrue(Math.abs(fled[0] - 200) <= 1, "it runs for 200 ticks, ran " + fled[0]);
            helper.assertTrue(jackal.core().idle() && jackal.carried().is(Items.EMERALD), "then stops, still carrying it");
        });
    }

    /**
     * It goes for the lowest health fraction, not the lowest health: a pig at 12 of 40 (30 %) over one at 8 of 10
     * (80 %, fewer points left), a survival player at full health and a pig at full health; once the player drops to
     * 4 of 20 (20 %) it goes for the player.
     */
    private static void weakest(GameTestHelper helper) {
        AshJackal jackal = jackal(helper, 6.0, 6.0);
        jackal.setNoAi(true);
        Pig third = pig(helper, 2.5, 9.5, 40.0F, 12.0F);
        Pig most = pig(helper, 9.5, 9.5, 10.0F, 8.0F);
        pig(helper, 9.5, 2.5, 4.0F, 4.0F);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(2.5, 1, 2.5));
        helper.runAtTickTime(3, () -> {
            LivingEntity pick = jackal.weakest();
            helper.assertTrue(pick == third, "it picks the pig at 30 % over the one at 80 % and the player at full, picked " + pick);
            helper.assertTrue(most.getHealth() < third.getHealth(), "the 80 % pig has fewer points left, " + most.getHealth());
            player.setHealth(4.0F);
        });
        helper.runAtTickTime(5, () -> {
            LivingEntity pick = jackal.weakest();
            helper.assertTrue(pick == player, "once the player is at 20 % it picks the player, picked " + pick);
            helper.succeed();
        });
    }

    /**
     * Range 16: a hurt pig 15.6 blocks off (corner to corner) is picked; a weaker one 16.4 blocks off, out over the gap
     * between arenas, is not.
     */
    private static void targetRange(GameTestHelper helper) {
        AshJackal jackal = jackal(helper, 0.5, 0.5);
        jackal.setNoAi(true);
        jackal.confine(helper.getBounds().expandTowards(0.0, 0.0, 6.0));   // reaches the far pig over the gap
        Pig near = pig(helper, 11.5, 11.5, 10.0F, 3.0F);                    // 15.56 blocks, 30 %
        Pig far = pig(helper, 0.5, 16.9, 10.0F, 1.0F);                      // 16.4 blocks, 10 %
        far.setNoGravity(true);
        TestPlayers.whenDone(helper, far::discard);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(Math.abs(jackal.distanceTo(near) - 15.56) < 0.05 && Math.abs(jackal.distanceTo(far) - 16.4) < 0.05,
                    "set up at 15.56 and 16.4 blocks, got " + jackal.distanceTo(near) + " and " + jackal.distanceTo(far));
            LivingEntity pick = jackal.weakest();
            helper.assertTrue(pick == near, "it picks the pig within 16 blocks over a weaker one at 16.4, picked " + pick);
            helper.succeed();
        });
    }

    /**
     * What it carries shows: while it carries a stack exactly one item display rides it, showing that item, at its
     * mouth (in front of it, at about two thirds of its height); when it drops the stack the display goes.
     */
    private static void carryDisplay(GameTestHelper helper) {
        AshJackal jackal = jackal(helper, 5.5, 5.5);
        jackal.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(displays(jackal).isEmpty(), "nothing on show with an empty mouth");
            jackal.carry(new ItemStack(Items.GOLDEN_APPLE, 2));
            List<Display.ItemDisplay> shown = displays(jackal);
            helper.assertTrue(shown.size() == 1, "one item display while it carries, " + shown.size());
            helper.assertTrue(shown.getFirst().getSlot(0).get().is(Items.GOLDEN_APPLE), "showing what it carries, " + shown.getFirst().getSlot(0).get());
        });
        helper.runAtTickTime(4, () -> {
            Display.ItemDisplay d = displays(jackal).getFirst();
            Vec3 rel = d.position().subtract(jackal.position());
            Vec3 forward = Vec3.directionFromRotation(0.0F, jackal.yBodyRot);
            double ahead = rel.x * forward.x + rel.z * forward.z;
            helper.assertTrue(ahead > 0.6 && ahead < 1.0, "it sits at the mouth, " + ahead + " ahead");
            helper.assertTrue(rel.y > 0.4 && rel.y < 0.75, "at about two thirds of its height, " + rel.y);
            jackal.kill(helper.getLevel());
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(displays(jackal).isEmpty(), "killed, it shows nothing");
            AABB round = new AABB(jackal.position(), jackal.position()).inflate(3.0);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class, round).isEmpty(), "and its display is gone from the world");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, round, e -> e.getItem().is(Items.GOLDEN_APPLE)).size() == 1,
                    "the stack it carried lies on the ground");
            helper.succeed();
        });
    }

    /**
     * It only takes what lies on the ground: a survival player holding a stack and a chest full of them stay untouched
     * while it hunts beside them.
     */
    private static void neverFromPlayers(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(3.5, 1, 6.5));
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);     // its bites never kill the player here
        player.setHealth(100.0F);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        BlockPos chestAt = new BlockPos(8, 1, 6);
        helper.setBlock(chestAt, Blocks.CHEST);
        helper.getBlockEntity(chestAt, ChestBlockEntity.class).setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        AshJackal jackal = jackal(helper, 6.5, 6.5);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(jackal.carried().isEmpty() && jackal.snatches() == 0, "it took nothing, carries " + jackal.carried());
            helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 16, "the player keeps all 16");
            helper.assertTrue(helper.getBlockEntity(chestAt, ChestBlockEntity.class).getItem(0).getCount() == 16, "the chest keeps all 16");
            helper.succeed();
        });
    }

    /**
     * Where mobs may not pick items up (the mob griefing rule off), it takes nothing: the rule is turned off and back
     * on within one tick, so no other test sees it.
     */
    private static void griefingRule(GameTestHelper helper) {
        AshJackal jackal = jackal(helper, 5.5, 5.5);
        jackal.setNoAi(true);
        ItemEntity stick = item(helper, 7.5, 5.5, new ItemStack(Items.STICK, 3));
        helper.runAtTickTime(3, () -> {
            ServerLevel level = helper.getLevel();
            GameRules rules = level.getGameRules();
            boolean was = rules.get(GameRules.MOB_GRIEFING);
            ItemEntity off;
            try {
                rules.set(GameRules.MOB_GRIEFING, false, level.getServer());
                off = jackal.findItem();
            } finally {
                rules.set(GameRules.MOB_GRIEFING, was, level.getServer());
            }
            helper.assertTrue(off == null, "with mob griefing off it goes for nothing, went for " + off);
            helper.assertTrue(jackal.findItem() == stick, "with it on it goes for the stack");
            helper.succeed();
        });
    }

    /** What it carries survives a save and reload, and shows again on the reloaded jackal. */
    private static void saveLoad(GameTestHelper helper) {
        AshJackal jackal = jackal(helper, 5.5, 5.5);
        jackal.setNoAi(true);
        jackal.carry(new ItemStack(Items.NETHERITE_SCRAP, 2));
        AshJackal[] copy = {null};
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            jackal.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            tag.remove("Passengers");
            AshJackal c = ModMobs.ASH_JACKAL.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            jackal.takeCarried();
            jackal.discard();
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, () -> {
                c.takeCarried();
                c.discard();
            });
            copy[0] = c;
        });
        helper.runAtTickTime(30, () -> {
            AshJackal c = copy[0];
            helper.assertTrue(c.carried().is(Items.NETHERITE_SCRAP) && c.carried().getCount() == 2, "it still carries the 2 scrap, " + c.carried());
            helper.assertTrue(c.core().flag(AshJackal.CARRY_FLAG), "and holds its jaw on it");
            List<Display.ItemDisplay> shown = displays(c);
            helper.assertTrue(shown.size() == 1 && shown.getFirst().getSlot(0).get().is(Items.NETHERITE_SCRAP), "and shows it again, " + shown.size() + " displays");
            helper.succeed();
        });
    }

    /**
     * Home: the Nether wastes and the soul sand valley, never the crimson forest; spawn weight 15, in packs of 2 to 3;
     * a monster.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("ash_jackal"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.NETHER_WASTES)), "its home holds the nether wastes");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SOUL_SAND_VALLEY)), "its home holds the soul sand valley");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.CRIMSON_FOREST)), "its home never holds the crimson forest");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/ash_jackal")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.ASH_JACKAL.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 15, "spawn weight 15, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 2 && entry.value().count().maxInclusive() == 3,
                "in packs of 2 to 3, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.ASH_JACKAL.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
