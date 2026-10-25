package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.Aurovore;
import com.panda.teeming.mob.nether.Aurovore.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
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
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * Aurovore: health 24, attack 4, armour 4, a 0.9 x 0.7 hitbox; a 10-tick sniff that does nothing yet, then a bite for
 * its attack damage that takes one item of {@code #teeming:aurovore_loot} from the victim's pack (never worn armour or
 * either hand) and sends it running for 200 ticks; killed, it drops everything it took. Every number is checked against
 * a plain value.
 *
 * <p>Every lizard here has its goals cleared, a follow range of 1 and is confined to its own arena, so it never notices
 * a survival player in a neighbouring arena; its target is given to it directly and stands beside it, and its sniff
 * and bite still run from its AI step. It gives up its hoard before it is removed when the test ends, so no stolen
 * gold spills into the next test. Players get 100 health, so no bite kills them; a bite on a player scales with
 * difficulty like any mob's blow (3 on Easy, 4 on Normal, 6 on Hard), on a mob it is the plain 4.
 */
public final class AurovoreTests {
    private AurovoreTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("aurovore_tell", AurovoreTests::tell);
        out.accept("aurovore_counter", AurovoreTests::counter);
        out.accept("aurovore_failure", AurovoreTests::failure);
        out.accept("aurovore_stats", AurovoreTests::stats);
        out.accept("aurovore_bite_damage", AurovoreTests::biteDamage);
        out.accept("aurovore_one_per_bite", AurovoreTests::onePerBite);
        TeemingGameTests.timeLimit("aurovore_one_per_bite", 160);
        out.accept("aurovore_flee_length", AurovoreTests::fleeLength);
        TeemingGameTests.timeLimit("aurovore_flee_length", 280);
        out.accept("aurovore_never_worn_or_held", AurovoreTests::neverWornOrHeld);
        out.accept("aurovore_loot_tag", AurovoreTests::lootTag);
        out.accept("aurovore_from_mobs", AurovoreTests::fromMobs);
        out.accept("aurovore_steal_cancel", AurovoreTests::stealCancel);
        out.accept("aurovore_hoard_display", AurovoreTests::hoardDisplay);
        out.accept("aurovore_save_load", AurovoreTests::saveLoad);
        out.accept("aurovore_home", AurovoreTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /**
     * A lizard at (x, z) with the base look, no goals, a follow range of 1 and confined to this arena, hunting
     * {@code target}; emptied and removed when the test ends wherever it ran to.
     */
    private static Aurovore lizard(GameTestHelper helper, double x, double z, @Nullable LivingEntity target) {
        Aurovore a = helper.spawn(ModMobs.AUROVORE.get(), new Vec3(x, 1, z));
        a.core().setLook("base", true);
        a.removeFreeWill();
        a.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        a.confine(helper.getBounds());
        a.setTarget(target);
        a.resetCooldown();
        TestPlayers.whenDone(helper, () -> {
            a.takeHoard();
            a.discard();
        });
        return a;
    }

    /** A survival player at (x, z) with 100 health, facing the lizard on its west. */
    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(x, 1, z));
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        p.setHealth(100.0F);
        return p;
    }

    /** A pig that stands where it is put, with 100 health. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** What one bite costs a player: its attack damage of 4, scaled by difficulty as every mob's blow on a player is. */
    private static float biteOnPlayer(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case EASY -> 3.0F;
            case HARD -> 6.0F;
            default -> 4.0F;
        };
    }

    private static int count(ServerPlayer p, Item item) {
        return p.getInventory().countItem(item);
    }

    /** Every item display riding the lizard. */
    private static List<Display.ItemDisplay> displays(Aurovore a) {
        return a.getPassengers().stream().filter(p -> p instanceof Display.ItemDisplay).map(p -> (Display.ItemDisplay) p).toList();
    }

    /** The item display riding in the lizard's jaws: in front of it (0.65-1.05 blocks), below its back (0.3-0.7 up). */
    private static Display.@Nullable ItemDisplay inJaws(Aurovore a) {
        Vec3 f = Vec3.directionFromRotation(0.0F, a.yBodyRot);
        for (Display.ItemDisplay d : displays(a)) {
            Vec3 rel = d.position().subtract(a.position());
            double ahead = rel.x * f.x + rel.z * f.z;
            if (ahead > 0.65 && ahead < 1.05 && rel.y > 0.3 && rel.y < 0.7) return d;
        }
        return null;
    }

    /** Items of {@code item} lying on the ground within 3 blocks of {@code at}. */
    private static int onGround(GameTestHelper helper, Vec3 at, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0), e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The sniff holds for exactly 10 ticks: it stands still, the player keeps their health and all 3 gold ingots; then
     * the bite follows and lands.
     */
    private static void tell(GameTestHelper helper) {
        ServerPlayer p = player(helper, 5.9, 5.5);
        p.getInventory().setItem(4, new ItemStack(Items.GOLD_INGOT, 3));
        Aurovore a = lizard(helper, 4.5, 5.5, p);
        int[] ticks = {0}, startedAt = {-1}, tick = {0};
        boolean[] quiet = {true}, ended = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (a.core().in(Step.SNIFF)) {
                if (start[0] == null) {
                    start[0] = a.position();
                    startedAt[0] = tick[0];
                }
                ticks[0]++;
                moved[0] = Math.max(moved[0], a.position().distanceTo(start[0]));
                if (p.getHealth() < 100.0F || count(p, Items.GOLD_INGOT) != 3 || a.bites() > 0) quiet[0] = false;
            } else if (ticks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "it sniffs and bites, " + ticks[0] + " ticks of sniffing so far");
            helper.assertTrue(startedAt[0] >= 1 && startedAt[0] <= 3, "with its target beside it, it sniffs at once, at tick " + startedAt[0]);
            helper.assertTrue(ticks[0] == 10, "the sniff lasts 10 ticks, " + ticks[0]);
            helper.assertTrue(quiet[0], "nothing is bitten or taken while it sniffs");
            helper.assertTrue(moved[0] < 0.05, "it stands still while it sniffs, moved " + moved[0]);
            helper.assertTrue(a.bites() == 1, "the bite follows the sniff and lands, bites " + a.bites());
        });
    }

    /**
     * The counter: a lizard that already holds 5 raw gold and a golden carrot steals a gold ingot and is killed as it
     * starts to run: on the ground where it fell lie all of it, 5 raw gold, the carrot and the ingot (raw gold, because its own
     * leftovers on death are gold nuggets), and its displays are
     * gone.
     */
    private static void counter(GameTestHelper helper) {
        ServerPlayer p = player(helper, 5.9, 5.5);
        p.getInventory().setItem(3, new ItemStack(Items.GOLD_INGOT, 2));
        Aurovore a = lizard(helper, 4.5, 5.5, p);
        a.give(new ItemStack(Items.RAW_GOLD, 5));
        a.give(new ItemStack(Items.GOLDEN_CARROT, 1));
        boolean[] killed = {false};
        Vec3[] fell = {null};
        helper.onEachTick(() -> {
            if (killed[0] || !a.core().in(Step.FLEE)) return;
            helper.assertTrue(a.holds(Items.GOLD_INGOT) == 1, "it holds the ingot it took, " + a.hoard());
            fell[0] = a.position();
            a.kill(helper.getLevel());
            killed[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0], "it stole and was killed");
            helper.assertTrue(onGround(helper, fell[0], Items.RAW_GOLD) == 5, "the 5 raw gold lie where it fell");
            helper.assertTrue(onGround(helper, fell[0], Items.GOLDEN_CARROT) == 1, "the golden carrot lies where it fell");
            helper.assertTrue(onGround(helper, fell[0], Items.GOLD_INGOT) == 1, "the stolen ingot lies where it fell");
            helper.assertTrue(a.hoard().isEmpty(), "it holds nothing now");
            helper.assertTrue(displays(a).isEmpty() && helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class,
                    new AABB(fell[0], fell[0]).inflate(3.0)).isEmpty(), "its displays are gone");
        });
    }

    /**
     * Failing it: a bite lands on a player with 5 gold ingots: the player loses one bite of health and exactly 1 ingot,
     * the lizard holds it, and 40 ticks into its run it is more than 2 blocks farther away, and it will not despawn.
     */
    private static void failure(GameTestHelper helper) {
        ServerPlayer p = player(helper, 7.9, 5.5);
        p.getInventory().setItem(3, new ItemStack(Items.GOLD_INGOT, 5));
        Aurovore a = lizard(helper, 6.5, 5.5, p);
        helper.assertTrue(!a.requiresCustomPersistence(), "with nothing stolen it may despawn");
        double[] start = {-1};
        int[] fleeing = {0};
        float[] healthAtBite = {-1.0F};
        helper.onEachTick(() -> {
            if (!a.core().in(Step.FLEE)) return;
            if (fleeing[0]++ == 0) {
                start[0] = a.distanceTo(p);
                healthAtBite[0] = p.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(fleeing[0] >= 40, "it runs off with the ingot, " + fleeing[0] + " ticks so far");
            float lost = 100.0F - healthAtBite[0];
            helper.assertTrue(Math.abs(lost - biteOnPlayer(helper)) < 0.01F, "the bite took " + biteOnPlayer(helper) + ", took " + lost);
            helper.assertTrue(count(p, Items.GOLD_INGOT) == 4, "the player lost exactly 1 of 5 ingots, has " + count(p, Items.GOLD_INGOT));
            helper.assertTrue(a.holds(Items.GOLD_INGOT) == 1 && a.thefts() == 1, "it holds the 1 ingot, " + a.hoard());
            double gain = a.distanceTo(p) - start[0];
            helper.assertTrue(gain > 2.0, "it got more than 2 blocks farther from the player, by " + gain);
            helper.assertTrue(a.requiresCustomPersistence(), "it never despawns while it holds stolen gold");
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** Health 24, attack 4, armour 4, a 0.9 x 0.7 hitbox. */
    private static void stats(GameTestHelper helper) {
        Aurovore a = lizard(helper, 5.5, 5.5, null);
        a.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(a.getMaxHealth() == 24.0F && a.getHealth() == 24.0F, "health 24, got " + a.getHealth() + "/" + a.getMaxHealth());
            helper.assertTrue(Math.abs(a.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4, got " + a.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(a.getArmorValue() == 4, "armour 4, got " + a.getArmorValue());
            helper.assertTrue(Math.abs(a.getBbWidth() - 0.9F) < 0.01F && Math.abs(a.getBbHeight() - 0.7F) < 0.01F,
                    "a 0.9 x 0.7 hitbox, got " + a.getBbWidth() + " x " + a.getBbHeight());
            helper.succeed();
        });
    }

    /**
     * The bite takes exactly 4 from a pig; a pig carries nothing to steal, so it doesn't run but shuts its jaws for the
     * bite and goes back to the fight.
     */
    private static void biteDamage(GameTestHelper helper) {
        Pig pig = pig(helper, 5.9, 5.5);
        Aurovore a = lizard(helper, 4.5, 5.5, pig);
        boolean[] fled = {false}, bit = {false}, back = {false};
        helper.onEachTick(() -> {
            fled[0] |= a.core().in(Step.FLEE);
            bit[0] |= a.core().in(Step.BITE);
            back[0] |= bit[0] && a.core().idle();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(a.bites() == 1, "the bite landed, bites " + a.bites());
            helper.assertTrue(Math.abs(100.0F - pig.getHealth() - 4.0F) < 0.01F, "the bite takes 4, took " + (100.0F - pig.getHealth()));
            helper.assertTrue(bit[0] && back[0], "it shuts its jaws on the bite and goes back to the fight");
            helper.assertTrue(!fled[0] && a.hoard().isEmpty(), "with nothing to take it doesn't run");
        });
    }

    /**
     * One item a bite: a player carries 16 gold nuggets (slot 2) and 3 gold ingots (slot 6). The first bite takes 1
     * nugget (the first stack of loot in slot order), the second 1 more: 14 nuggets and all 3 ingots are left, and the
     * lizard holds one stack of 2 nuggets.
     */
    private static void onePerBite(GameTestHelper helper) {
        ServerPlayer p = player(helper, 5.9, 5.5);
        p.getInventory().setItem(2, new ItemStack(Items.GOLD_NUGGET, 16));
        p.getInventory().setItem(6, new ItemStack(Items.GOLD_INGOT, 3));
        Aurovore a = lizard(helper, 4.5, 5.5, p);
        a.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);   // stays beside the player to bite again
        int[] afterFirst = {-1, -1};
        helper.onEachTick(() -> {
            if (afterFirst[0] < 0 && a.thefts() == 1) {
                afterFirst[0] = count(p, Items.GOLD_NUGGET);
                afterFirst[1] = count(p, Items.GOLD_INGOT);
                p.setInvulnerableTime(0);                               // a test player never ticks its hurt cooldown down
                a.core().end();                                         // cut the run short so it bites again
                a.resetCooldown();
                a.setTarget(p);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(afterFirst[0] == 15 && afterFirst[1] == 3, "the first bite took 1 nugget, left " + afterFirst[0] + " nuggets and " + afterFirst[1] + " ingots");
            helper.assertTrue(a.thefts() == 2 && a.bites() == 2, "a second bite took a second item, thefts " + a.thefts());
            helper.assertTrue(count(p, Items.GOLD_NUGGET) == 14 && count(p, Items.GOLD_INGOT) == 3,
                    "14 nuggets and 3 ingots are left, " + count(p, Items.GOLD_NUGGET) + " and " + count(p, Items.GOLD_INGOT));
            List<ItemStack> hoard = a.hoard();
            helper.assertTrue(hoard.size() == 1 && hoard.getFirst().is(Items.GOLD_NUGGET) && hoard.getFirst().getCount() == 2,
                    "it holds one stack of 2 nuggets, " + hoard);
        });
    }

    /** It runs off for exactly 200 ticks after it steals, then stops, still holding what it took. */
    private static void fleeLength(GameTestHelper helper) {
        ServerPlayer p = player(helper, 5.9, 5.5);
        p.getInventory().setItem(1, new ItemStack(Items.RAW_GOLD, 2));
        Aurovore a = lizard(helper, 4.5, 5.5, p);
        a.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);   // stays on stage
        int[] fled = {0};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (a.core().in(Step.FLEE)) fled[0]++;
            else if (fled[0] > 0) over[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the run ends, " + fled[0] + " ticks so far");
            helper.assertTrue(fled[0] == 200, "it runs for 200 ticks, ran " + fled[0]);
            helper.assertTrue(!a.core().in(Step.FLEE) && a.holds(Items.RAW_GOLD) == 1, "then stops, still holding the raw gold, " + a.hoard());
        });
    }

    /**
     * It never takes worn armour or anything in either hand: a player in a golden helmet and boots, a golden sword in
     * the main hand and a golden apple in the off hand, with only iron in the pack, is bitten and keeps all of it; the
     * lizard takes nothing and doesn't run. A gold nugget then put in slot 7 is what it would go for.
     */
    private static void neverWornOrHeld(GameTestHelper helper) {
        ServerPlayer p = player(helper, 5.9, 5.5);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE, 2));
        p.getInventory().setItem(5, new ItemStack(Items.IRON_INGOT, 8));
        Aurovore a = lizard(helper, 4.5, 5.5, p);
        boolean[] fled = {false};
        helper.onEachTick(() -> fled[0] |= a.core().in(Step.FLEE));
        helper.succeedWhen(() -> {
            helper.assertTrue(a.bites() == 1 && a.core().idle(), "the bite landed and it is done with it, bites " + a.bites());
            helper.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET) && p.getItemBySlot(EquipmentSlot.FEET).is(Items.GOLDEN_BOOTS),
                    "the worn golden helmet and boots stay on");
            helper.assertTrue(p.getMainHandItem().is(Items.GOLDEN_SWORD), "the golden sword stays in hand");
            helper.assertTrue(p.getOffhandItem().is(Items.GOLDEN_APPLE) && p.getOffhandItem().getCount() == 2, "both golden apples stay in the off hand");
            helper.assertTrue(count(p, Items.IRON_INGOT) == 8, "the iron is no loot");
            helper.assertTrue(a.hoard().isEmpty() && a.thefts() == 0 && !fled[0], "it took nothing and didn't run");
            p.getInventory().setItem(7, new ItemStack(Items.GOLD_NUGGET, 1));
            Aurovore.Loot loot = Aurovore.lootIn(p);
            helper.assertTrue(loot != null && loot.slot() == 7, "a gold nugget in slot 7 is what it would take, " + (loot == null ? "none" : loot.slot()));
        });
    }

    /**
     * Its loot is the tag {@code teeming:aurovore_loot}: gold ingots, nuggets, raw gold, gold blocks, golden apples and
     * carrots, gold tools and armour and clocks are in it; iron, copper, diamonds, emeralds and netherite are not.
     */
    private static void lootTag(GameTestHelper helper) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, TeemingMod.id("aurovore_loot"));
        helper.assertTrue(Aurovore.LOOT.equals(tag), "it steals by the tag teeming:aurovore_loot, " + Aurovore.LOOT.location());
        for (Item in : List.of(Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.RAW_GOLD, Items.GOLD_BLOCK, Items.GOLDEN_APPLE,
                Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_HELMET,
                Items.GOLDEN_BOOTS, Items.CLOCK, Items.GOLD_ORE, Items.NETHER_GOLD_ORE)) {
            helper.assertTrue(new ItemStack(in).is(tag), in + " is loot");
        }
        for (Item out : List.of(Items.IRON_INGOT, Items.COPPER_INGOT, Items.DIAMOND, Items.EMERALD, Items.NETHERITE_INGOT,
                Items.IRON_SWORD, Items.STICK)) {
            helper.assertTrue(!new ItemStack(out).is(tag), out + " is no loot");
        }
        helper.succeed();
    }

    /**
     * It robs mobs that carry an inventory too: a piglin holding a golden sword with 2 gold ingots in its inventory loses
     * 1 ingot to the bite and keeps the sword, and the lizard runs.
     */
    private static void fromMobs(GameTestHelper helper) {
        Piglin piglin = helper.spawn(EntityTypes.PIGLIN, new Vec3(5.9, 1, 5.5));
        piglin.setNoAi(true);
        piglin.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        piglin.setHealth(100.0F);
        piglin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
        piglin.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
        TestPlayers.whenDone(helper, piglin::discard);
        Aurovore a = lizard(helper, 4.5, 5.5, piglin);
        helper.succeedWhen(() -> {
            helper.assertTrue(a.core().in(Step.FLEE), "it took something and runs, phase " + a.phase());
            helper.assertTrue(piglin.getInventory().countItem(Items.GOLD_INGOT) == 1, "the piglin has 1 ingot left");
            helper.assertTrue(piglin.getMainHandItem().is(Items.GOLDEN_SWORD), "and keeps its sword");
            helper.assertTrue(a.holds(Items.GOLD_INGOT) == 1, "the lizard holds the ingot, " + a.hoard());
        });
    }

    /** A pack that vetoes the theft (the "steal" step) leaves the player their gold, and the lizard doesn't run. */
    private static void stealCancel(GameTestHelper helper) {
        ServerPlayer p = player(helper, 5.9, 5.5);
        p.getInventory().setItem(3, new ItemStack(Items.GOLD_INGOT, 2));
        Aurovore a = lizard(helper, 4.5, 5.5, p);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == a && e.step().equals("steal")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] fled = {false};
        helper.onEachTick(() -> fled[0] |= a.core().in(Step.FLEE));
        helper.succeedWhen(() -> {
            helper.assertTrue(a.bites() == 1 && a.core().idle(), "the bite landed and it is done with it");
            helper.assertTrue(count(p, Items.GOLD_INGOT) == 2 && a.hoard().isEmpty(), "the theft was vetoed: the player keeps both ingots");
            helper.assertTrue(!fled[0], "and it doesn't run");
        });
    }

    /**
     * What it holds shows: the newest piece in its jaws (in front of it at about two thirds of its height), the two
     * before it on its back, one item display each and never more than three; killed, the displays go and all four
     * stacks lie on the ground.
     */
    private static void hoardDisplay(GameTestHelper helper) {
        Aurovore a = lizard(helper, 5.5, 5.5, null);
        a.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(displays(a).isEmpty(), "nothing on show with nothing stolen");
            a.give(new ItemStack(Items.RAW_GOLD, 3));
            a.give(new ItemStack(Items.GOLDEN_CARROT, 1));
            a.give(new ItemStack(Items.GOLD_INGOT, 1));
            helper.assertTrue(displays(a).size() == 3, "three pieces on show, " + displays(a).size());
        });
        helper.runAtTickTime(4, () -> {
            Display.ItemDisplay jaws = inJaws(a);
            helper.assertTrue(jaws != null && jaws.getSlot(0).get().is(Items.GOLD_INGOT), "the newest piece, the ingot, sits in its jaws");
            long onBack = displays(a).stream().filter(d -> {
                Vec3 rel = d.position().subtract(a.position());
                return rel.y > 0.5 && rel.horizontalDistance() < 0.45;
            }).count();
            helper.assertTrue(onBack == 2, "two pieces ride on its back, " + onBack);
            a.give(new ItemStack(Items.CLOCK, 1));
            List<Display.ItemDisplay> shown = displays(a);
            helper.assertTrue(shown.size() == 3, "still three on show with four stacks held, " + shown.size());
            helper.assertTrue(shown.stream().noneMatch(d -> d.getSlot(0).get().is(Items.RAW_GOLD)), "the oldest, the raw gold, is no longer on show");
            a.kill(helper.getLevel());
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(displays(a).isEmpty(), "killed, it shows nothing");
            AABB round = new AABB(a.position(), a.position()).inflate(3.0);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class, round).isEmpty(), "and its displays are gone from the world");
            for (Item item : List.of(Items.RAW_GOLD, Items.GOLDEN_CARROT, Items.GOLD_INGOT, Items.CLOCK)) {
                helper.assertTrue(onGround(helper, a.position(), item) > 0, item + " lies on the ground");
            }
            helper.assertTrue(onGround(helper, a.position(), Items.RAW_GOLD) == 3, "all 3 raw gold");
            helper.succeed();
        });
    }

    /** Its hoard survives a save and reload, and shows again on the reloaded lizard. */
    private static void saveLoad(GameTestHelper helper) {
        Aurovore a = lizard(helper, 5.5, 5.5, null);
        a.setNoAi(true);
        a.give(new ItemStack(Items.GOLD_NUGGET, 4));
        a.give(new ItemStack(Items.GOLDEN_APPLE, 1));
        Aurovore[] copy = {null};
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            a.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            tag.remove("Passengers");
            Aurovore c = ModMobs.AUROVORE.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            a.takeHoard();
            a.discard();
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, () -> {
                c.takeHoard();
                c.discard();
            });
            copy[0] = c;
        });
        helper.runAtTickTime(30, () -> {
            Aurovore c = copy[0];
            helper.assertTrue(c.holds(Items.GOLD_NUGGET) == 4 && c.holds(Items.GOLDEN_APPLE) == 1, "it still holds 4 nuggets and the apple, " + c.hoard());
            helper.assertTrue(c.core().flag(Aurovore.CARRY_FLAG), "and holds its jaw on the newest");
            List<Display.ItemDisplay> shown = displays(c);
            helper.assertTrue(shown.size() == 2, "and shows both again, " + shown.size() + " displays");
            helper.succeed();
        });
    }

    /**
     * Home: the Nether wastes (never the crimson or warped forest) at spawn weight 6, alone; and the Teeming spawner's
     * bastion rule, which places it only inside a bastion remnant (never on an open floor like this arena's); a monster.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("aurovore"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.NETHER_WASTES)), "its home holds the nether wastes");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.CRIMSON_FOREST) || b.is(Biomes.WARPED_FOREST)), "never the crimson or warped forest");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/aurovore")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.AUROVORE.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 6, "spawn weight 6, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got groups of " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        RosterStats.Entry row = RosterStats.get("aurovore");
        helper.assertTrue(row.spawner().equals("bastion") && TeemingSpawner.Rule.of(row.spawner()) == TeemingSpawner.Rule.BASTION,
                "the bastion rule places it too, got '" + row.spawner() + "'");
        helper.assertTrue(TeemingSpawner.Rule.BASTION.dimension.equals(Level.NETHER), "a Nether rule");
        helper.assertTrue(!TeemingSpawner.Rule.BASTION.fits(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))),
                "the bastion rule refuses an open floor outside a bastion");
        helper.assertTrue(ModMobs.AUROVORE.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
