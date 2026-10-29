package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.BlackstoneScuttler;
import com.panda.teeming.mob.nether.BlackstoneScuttler.Step;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Blackstone Scuttler: weapon blows and projectiles do x0.3 to its fused shell, a pickaxe x3, anything else lands
 * whole; it rears for 12 ticks, then lunges straight the way it aimed as it reared and pinches for 5; the gilded look
 * drops gold nuggets. Every number is checked against a plain value.
 *
 * <p>Damage readings use still Scuttlers (no AI, no gravity) hit once each, since a second hit inside the hurt cooldown
 * would be swallowed. Its armour is 10, so a blow of {@code d} reaching it costs {@code d * (1 - max(2, 10 - d/2) / 25)}:
 * 10 x0.3 = 3 costs 1.98, 4 x3 = 12 costs 10.08, 10 x3 = 30 costs 27.6 (armour never counts for less than 2).
 * Scuttlers that move have their goals cleared and a follow range of 1, so they act only on the targets the test gives
 * them; they leave with the test.
 */
public final class BlackstoneScuttlerTests {
    private BlackstoneScuttlerTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("blackstone_scuttler_tell", BlackstoneScuttlerTests::tell);
        out.accept("blackstone_scuttler_counter", BlackstoneScuttlerTests::pickaxeCracks);
        out.accept("blackstone_scuttler_failure", BlackstoneScuttlerTests::swordGlancesThenPinch);
        out.accept("blackstone_scuttler_weapons", BlackstoneScuttlerTests::weapons);
        out.accept("blackstone_scuttler_projectiles", BlackstoneScuttlerTests::projectiles);
        out.accept("blackstone_scuttler_whole", BlackstoneScuttlerTests::whole);
        out.accept("blackstone_scuttler_crack_cancel", BlackstoneScuttlerTests::crackCancel);
        out.accept("blackstone_scuttler_rear_range", BlackstoneScuttlerTests::rearRange);
        out.accept("blackstone_scuttler_lunge", BlackstoneScuttlerTests::lunge);
        out.accept("blackstone_scuttler_sidestep", BlackstoneScuttlerTests::sidestep);
        out.accept("blackstone_scuttler_gilded", BlackstoneScuttlerTests::gilded);
        out.accept("blackstone_scuttler_stats", BlackstoneScuttlerTests::stats);
        out.accept("blackstone_scuttler_home", BlackstoneScuttlerTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A Scuttler that acts only on what the test gives it: no goals, a follow range of 1, its rear ready. */
    private static BlackstoneScuttler scuttler(GameTestHelper helper, Vec3 at, String look) {
        BlackstoneScuttler s = helper.spawn(ModMobs.BLACKSTONE_SCUTTLER.get(), at);
        s.core().setLook(look, true);
        s.removeFreeWill();
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        s.resetCooldown();
        TestPlayers.whenDone(helper, s::discard);
        return s;
    }

    /** A still Scuttler: no AI, no gravity, so it stays exactly where it is put and never reacts. */
    private static BlackstoneScuttler still(GameTestHelper helper, double x, double z) {
        BlackstoneScuttler s = scuttler(helper, new Vec3(x, 1, z), "base");
        s.setNoAi(true);
        s.setNoGravity(true);
        return s;
    }

    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static float lost(LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    /** Hits {@code s} once with a player blow of {@code amount}, the player holding {@code held}, and returns what it lost. */
    private static float blow(GameTestHelper helper, BlackstoneScuttler s, ServerPlayer player, Item held, float amount) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held == Items.AIR ? ItemStack.EMPTY : new ItemStack(held));
        float before = s.getHealth();
        s.hurtServer(helper.getLevel(), s.damageSources().playerAttack(player), amount);
        return before - s.getHealth();
    }

    private static void near(GameTestHelper helper, float got, float want, String what) {
        helper.assertTrue(Math.abs(got - want) < 0.01F, what + ": want " + want + ", got " + got);
    }

    // ---- the tell --------------------------------------------------------------------------------------------------

    /** It rears for exactly 12 ticks, standing still and hurting nothing though a pig stands in reach, then lunges. */
    private static void tell(GameTestHelper helper) {
        BlackstoneScuttler s = scuttler(helper, new Vec3(3.5, 1, 5.5), "base");
        Pig pig = pig(helper, 5.5, 5.5);
        int[] tick = {0}, reared = {0};
        boolean[] lunged = {false}, quiet = {true};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 3) helper.assertTrue(s.rear(pig), "the rear starts");
            if (s.core().in(Step.REAR)) {
                if (start[0] == null) start[0] = s.position();
                reared[0]++;
                if (pig.getHealth() < pig.getMaxHealth()) quiet[0] = false;
                if (Math.hypot(s.getX() - start[0].x, s.getZ() - start[0].z) > 0.02) quiet[0] = false;
            } else if (reared[0] > 0 && s.core().in(Step.LUNGE)) {
                lunged[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lunged[0], "the rear ends in the lunge, reared " + reared[0] + " ticks so far");
            helper.assertTrue(reared[0] == 12, "the rear holds 12 ticks, saw " + reared[0]);
            helper.assertTrue(quiet[0], "while it rears it neither moves nor hurts anything");
        });
    }

    // ---- the counter and the failure -------------------------------------------------------------------------------

    /**
     * The counter: a pickaxe blow of 4 reaches it as 12 (x3) and costs 10.08 against armour 10, where the same 4 as
     * magic costs 4 and as a sword blow 0.75; a netherite pickaxe blow of 10 costs 27.6.
     */
    private static void pickaxeCracks(GameTestHelper helper) {
        BlackstoneScuttler cracked = still(helper, 2.5, 3.5);
        BlackstoneScuttler control = still(helper, 5.5, 3.5);
        BlackstoneScuttler sword = still(helper, 8.5, 3.5);
        BlackstoneScuttler hard = still(helper, 5.5, 7.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 1, 10.5));
        helper.runAtTickTime(2, () -> {
            near(helper, blow(helper, cracked, player, Items.IRON_PICKAXE, 4.0F), 10.08F, "a pickaxe blow of 4 cracks it for x3");
            float before = control.getHealth();
            control.hurtServer(helper.getLevel(), control.damageSources().magic(), 4.0F);
            near(helper, before - control.getHealth(), 4.0F, "the same 4 as magic lands whole");
            near(helper, blow(helper, sword, player, Items.IRON_SWORD, 4.0F), 0.75F, "the same 4 with a sword glances off (1.2 against armour 10)");
            near(helper, blow(helper, hard, player, Items.NETHERITE_PICKAXE, 10.0F), 27.6F, "a pickaxe blow of 10 cracks it for x3");
            helper.assertTrue(BlackstoneScuttler.shell(cracked.damageSources().playerAttack(player)) == BlackstoneScuttler.Shell.CRACK,
                    "a pickaxe in the hand reads as a crack");
            helper.assertTrue(!cracked.vulnerable(), "a crack is no punish window: nothing on it glows gold");
            helper.succeed();
        });
    }

    /**
     * The failure: a sword blow of 10 costs it only 1.98 (x0.3 against armour 10), and the Scuttler that was struck
     * rears and lunges into the pig in front of it, which loses 5 (1x Atk).
     */
    private static void swordGlancesThenPinch(GameTestHelper helper) {
        BlackstoneScuttler s = scuttler(helper, new Vec3(3.5, 1, 5.5), "base");
        Pig pig = pig(helper, 5.5, 5.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(3.5, 1, 2.0));
        helper.runAtTickTime(3, () -> {
            near(helper, blow(helper, s, player, Items.DIAMOND_SWORD, 10.0F), 1.98F, "a sword blow of 10 glances off");
            helper.assertTrue(s.rear(pig), "it rears at the pig");
        });
        helper.succeedWhen(() -> {
            near(helper, lost(pig), 5.0F, "the lunge pinches the pig for Atk 5");
            near(helper, lost(s), 1.98F, "the Scuttler still has all but 1.98");
        });
    }

    // ---- the shell -------------------------------------------------------------------------------------------------

    /** Weapons: a blow of 10 with a sword, an axe, a mace, a trident in the hand or a bare fist costs 1.98 (x0.3). */
    private static void weapons(GameTestHelper helper) {
        Item[] held = {Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE, Items.TRIDENT, Items.AIR};
        double[][] at = {{2.5, 3.0}, {5.5, 3.0}, {8.5, 3.0}, {2.5, 7.0}, {5.5, 7.0}};
        BlackstoneScuttler[] s = new BlackstoneScuttler[held.length];
        for (int i = 0; i < held.length; i++) s[i] = still(helper, at[i][0], at[i][1]);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(9.0, 1, 9.0));
        helper.runAtTickTime(2, () -> {
            for (int i = 0; i < held.length; i++) {
                near(helper, blow(helper, s[i], player, held[i], 10.0F), 1.98F, "a blow of 10 with " + held[i] + " glances off");
            }
            helper.succeed();
        });
    }

    /** Projectiles: an arrow of 10 and a thrown trident of 10 each cost 1.98 (x0.3). */
    private static void projectiles(GameTestHelper helper) {
        BlackstoneScuttler shot = still(helper, 3.5, 5.5);
        BlackstoneScuttler speared = still(helper, 7.5, 5.5);
        Pig shooter = pig(helper, 5.5, 9.5);
        helper.runAtTickTime(2, () -> {
            Vec3 a = helper.absoluteVec(new Vec3(3.5, 1.5, 7.0));
            Arrow arrow = new Arrow(helper.getLevel(), a.x, a.y, a.z, new ItemStack(Items.ARROW), null);
            Vec3 b = helper.absoluteVec(new Vec3(7.5, 1.5, 7.0));
            ThrownTrident trident = new ThrownTrident(helper.getLevel(), b.x, b.y, b.z, new ItemStack(Items.TRIDENT));
            shot.hurtServer(helper.getLevel(), shot.damageSources().arrow(arrow, shooter), 10.0F);
            speared.hurtServer(helper.getLevel(), speared.damageSources().trident(trident, shooter), 10.0F);
            near(helper, lost(shot), 1.98F, "an arrow of 10 glances off");
            near(helper, lost(speared), 1.98F, "a thrown trident of 10 glances off");
            helper.succeed();
        });
    }

    /** Neither a blow nor a shot lands whole (magic 4 costs 4); fire and lava never hurt it. */
    private static void whole(GameTestHelper helper) {
        BlackstoneScuttler magic = still(helper, 3.5, 5.5);
        BlackstoneScuttler lava = still(helper, 7.5, 5.5);
        helper.runAtTickTime(2, () -> {
            magic.hurtServer(helper.getLevel(), magic.damageSources().magic(), 4.0F);
            lava.hurtServer(helper.getLevel(), lava.damageSources().lava(), 4.0F);
            near(helper, lost(magic), 4.0F, "magic lands whole");
            near(helper, lost(lava), 0.0F, "lava doesn't hurt it");
            helper.succeed();
        });
    }

    /** Packs can veto the crack: with the "crack" step cancelled, a pickaxe blow of 10 glances off like a sword (1.98). */
    private static void crackCancel(GameTestHelper helper) {
        BlackstoneScuttler s = still(helper, 5.5, 5.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 1, 9.0));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == s && e.step().equals("crack")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> {
            near(helper, blow(helper, s, player, Items.IRON_PICKAXE, 10.0F), 1.98F, "a vetoed crack leaves the pickaxe an ordinary weapon");
            helper.succeed();
        });
    }

    // ---- the rear and the lunge ------------------------------------------------------------------------------------

    /** It rears at a target 2.8 blocks away and not at one 3.4 blocks away. */
    private static void rearRange(GameTestHelper helper) {
        BlackstoneScuttler close = scuttler(helper, new Vec3(3.5, 1, 3.5), "base");
        BlackstoneScuttler far = scuttler(helper, new Vec3(3.5, 1, 7.5), "base");
        Pig near = pig(helper, 6.3, 3.5);
        Pig away = pig(helper, 6.9, 7.5);
        close.setTarget(near);
        far.setTarget(away);
        boolean[] closeReared = {false}, farReared = {false};
        helper.onEachTick(() -> {
            if (close.core().in(Step.REAR)) closeReared[0] = true;
            if (!far.core().idle()) farReared[0] = true;
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(closeReared[0], "a target 2.8 blocks away makes it rear");
            helper.assertTrue(!farReared[0], "a target 3.4 blocks away doesn't, phase " + far.phase());
            helper.succeed();
        });
    }

    /** The lunge goes 0.4 blocks a tick for 6 ticks: 2.4 blocks straight at where it aimed, then it settles. */
    private static void lunge(GameTestHelper helper) {
        BlackstoneScuttler s = scuttler(helper, new Vec3(2.5, 1, 5.5), "base");
        Pig mark = pig(helper, 10.5, 5.5);
        int[] tick = {0}, lunging = {0};
        Vec3[] from = {null}, to = {null};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 3) helper.assertTrue(s.rear(mark), "the rear starts");
            if (s.core().in(Step.LUNGE)) {
                if (from[0] == null) from[0] = s.position();
                lunging[0]++;
            } else if (from[0] != null && to[0] == null) {
                to[0] = s.position();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(to[0] != null, "the lunge ends");
            helper.assertTrue(lunging[0] == 6, "the lunge lasts 6 ticks, saw " + lunging[0]);
            double dx = to[0].x - from[0].x, dz = to[0].z - from[0].z;
            helper.assertTrue(Math.abs(dx - 2.4) < 0.05 && Math.abs(dz) < 0.05, "it moves 2.4 blocks toward the mark, moved " + dx + ", " + dz);
            helper.assertTrue(s.core().in(Step.RECOVER) || s.core().idle(), "then it settles, phase " + s.phase());
            helper.assertTrue(mark.getHealth() >= mark.getMaxHealth(), "the far mark is untouched");
        });
    }

    /** The counter's other half: a pig that moves 2 blocks aside while it rears is missed; the lunge keeps its aim. */
    private static void sidestep(GameTestHelper helper) {
        BlackstoneScuttler s = scuttler(helper, new Vec3(3.5, 1, 5.5), "base");
        Pig pig = pig(helper, 5.5, 5.5);
        Vec3[] aim = {null};
        helper.runAtTickTime(3, () -> helper.assertTrue(s.rear(pig), "the rear starts"));
        helper.runAtTickTime(9, () -> {
            Vec3 aside = helper.absoluteVec(new Vec3(5.5, 1, 7.8));
            pig.snapTo(aside.x, aside.y, aside.z, pig.getYRot(), pig.getXRot());
        });
        helper.onEachTick(() -> {
            if (s.core().in(Step.LUNGE) && aim[0] == null) aim[0] = s.lungeDir();
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(aim[0] != null, "it lunged");
            helper.assertTrue(aim[0].x > 0.99, "the lunge kept the aim it reared with (+x), went " + aim[0]);
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "the pig that stepped aside is missed, lost " + lost(pig));
            helper.succeed();
        });
    }

    // ---- the gilded look -------------------------------------------------------------------------------------------

    /** Both looks drop their chitin plate; only the Gilded Scuttler drops gold nuggets as well, 1 to 3 of them. */
    private static void gilded(GameTestHelper helper) {
        BlackstoneScuttler base = scuttler(helper, new Vec3(3.0, 1, 5.5), "base");
        BlackstoneScuttler gilded = scuttler(helper, new Vec3(8.0, 1, 5.5), BlackstoneScuttler.GILDED);
        base.setNoAi(true);
        gilded.setNoAi(true);
        Vec3[] spots = new Vec3[2];
        helper.runAtTickTime(2, () -> {
            spots[0] = base.position();
            spots[1] = gilded.position();
            base.kill(helper.getLevel());
            gilded.kill(helper.getLevel());
        });
        helper.runAtTickTime(4, () -> {
            int[] baseDrops = drops(helper, spots[0]), gildedDrops = drops(helper, spots[1]);
            helper.assertTrue(baseDrops[0] == 0, "the base look drops no nuggets, dropped " + baseDrops[0]);
            helper.assertTrue(gildedDrops[0] >= 1 && gildedDrops[0] <= 3, "the gilded look drops 1 to 3 nuggets, dropped " + gildedDrops[0]);
            helper.assertTrue(baseDrops[1] >= 1 && gildedDrops[1] >= 1, "both drop chitin plate: " + baseDrops[1] + ", " + gildedDrops[1]);
            for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(spots[0], spots[1]).inflate(2.0))) e.discard();
            helper.succeed();
        });
    }

    /** {nuggets, chitin plates} on the ground within 2 blocks of {@code at}. */
    private static int[] drops(GameTestHelper helper, Vec3 at) {
        int[] n = new int[2];
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(2.0))) {
            if (e.getItem().is(Items.GOLD_NUGGET)) n[0] += e.getItem().getCount();
            if (e.getItem().is(ModMaterials.CHITIN_PLATE.get())) n[1] += e.getItem().getCount();
        }
        return n;
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** Health 30, attack 5, armour 10, a 1.1 x 0.7 hitbox, a fire-immune monster with no weak point, two looks. */
    private static void stats(GameTestHelper helper) {
        BlackstoneScuttler s = still(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(s.getMaxHealth() - 30.0F) < 0.01F && Math.abs(s.getHealth() - 30.0F) < 0.01F,
                    "health 30, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ATTACK_DAMAGE) - 5.0) < 0.01, "attack 5");
            helper.assertTrue(s.getArmorValue() == 10, "armour 10, got " + s.getArmorValue());
            helper.assertTrue(Math.abs(s.getBbWidth() - 1.1F) < 0.02F && Math.abs(s.getBbHeight() - 0.7F) < 0.02F,
                    "hitbox 1.1 x 0.7, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.assertTrue(s.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(s.getType().fireImmune(), "fire doesn't hurt it");
            helper.assertTrue(!s.vulnerable(), "it has no weak point: nothing on it is ever gold");
            List<String> looks = Looks.of("blackstone_scuttler").stream().map(LookDef::id).sorted().toList();
            helper.assertTrue(looks.equals(List.of("base", "gilded_scuttler")), "looks base and gilded_scuttler, got " + looks);
            helper.succeed();
        });
    }

    /** Home: the basalt deltas; spawn weight 10, in groups of 1 to 2. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("blackstone_scuttler"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.BASALT_DELTAS)), "its home holds the basalt deltas");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/blackstone_scuttler")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.BLACKSTONE_SCUTTLER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 10, "spawn weight 10, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "in groups of 1 to 2, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
