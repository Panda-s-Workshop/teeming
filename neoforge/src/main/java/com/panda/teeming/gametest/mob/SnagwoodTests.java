package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.overworld.Snagwood;
import com.panda.teeming.mob.overworld.Snagwood.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
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

/**
 * Snagwood: a 12-tick creak, then a root sweep of 6 (1.2 x 5) across the front 120 degrees out to 3 blocks; anything
 * outside the arc is untouched; axes deal x2; rooted it is disguised and still, a target within 5 blocks or a blow
 * wakes it, and 160 ticks without a target root it back. Every number is checked against a plain value.
 */
public final class SnagwoodTests {
    private SnagwoodTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("snagwood_tell", SnagwoodTests::tell);
        out.accept("snagwood_counter", SnagwoodTests::stepBehind);
        out.accept("snagwood_failure", SnagwoodTests::caughtInSweep);
        out.accept("snagwood_arc", SnagwoodTests::arcEdges);
        out.accept("snagwood_radius", SnagwoodTests::radiusEdges);
        out.accept("snagwood_axe", SnagwoodTests::axeDoubles);
        out.accept("snagwood_stats", SnagwoodTests::stats);
        out.accept("snagwood_home", SnagwoodTests::home);
        out.accept("snagwood_root_back", SnagwoodTests::rootBack);
        TeemingGameTests.timeLimit("snagwood_root_back", 240);
        out.accept("snagwood_wake_range", SnagwoodTests::wakeRange);
        out.accept("snagwood_wakes_for_player", SnagwoodTests::wakesForPlayer);
        out.accept("snagwood_hit_wakes", SnagwoodTests::hitWakes);
        out.accept("snagwood_disguise_holds", SnagwoodTests::disguiseHolds);
    }

    /** An awake Snagwood with the base look, facing +Z (yaw 0); {@code still} turns its AI off (it only acts when told). */
    private static Snagwood snag(GameTestHelper helper, double x, double z, boolean still) {
        Snagwood snag = helper.spawn(ModMobs.SNAGWOOD.get(), new Vec3(x, 1, z));
        snag.core().setLook("base", true);
        snag.setNoAi(still);
        snag.standAwake();
        snag.face(snag.position().add(0, 0, 5));
        return snag;
    }

    /** A fresh Snagwood, left to take root on its first tick, facing +Z. */
    private static Snagwood rooted(GameTestHelper helper, double x, double z) {
        Snagwood snag = helper.spawn(ModMobs.SNAGWOOD.get(), new Vec3(x, 1, z));
        snag.core().setLook("base", true);
        snag.face(snag.position().add(0, 0, 5));
        // no targets from beyond the arena: only what the test sets, or what comes within its own wake range
        snag.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        return snag;
    }

    /** A still mob {@code distance} blocks from ({@code x}, {@code z}), {@code degrees} off a +Z facing (0 = straight ahead). */
    private static <T extends Mob> T around(GameTestHelper helper, EntityType<T> type, double x, double z, double degrees, double distance) {
        double a = Math.toRadians(degrees);
        T mob = helper.spawn(type, new Vec3(x - Math.sin(a) * distance, 1, z + Math.cos(a) * distance));
        mob.setNoAi(true);
        return mob;
    }

    private static Pig pig(GameTestHelper helper, double x, double z, double degrees, double distance) {
        return around(helper, EntityTypes.PIG, x, z, degrees, distance);
    }

    /** The creak lasts exactly 12 ticks, holds still and hurts nothing; then the sweep takes 6 from a pig in front. */
    private static void tell(GameTestHelper helper) {
        Snagwood snag = snag(helper, 5.5, 3.5, true);
        Pig pig = pig(helper, 5.5, 3.5, 0, 2.0);
        Vec3 start = snag.position();
        int[] tick = {0};
        int[] creak = {0};
        boolean[] swept = {false};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 2) helper.assertTrue(snag.creak(pig), "the creak starts");
            if (snag.core().in(Step.TELL)) {
                creak[0]++;
                helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "the creak hurts nothing yet");
                helper.assertTrue(snag.position().distanceTo(start) < 0.01, "it holds still while it creaks");
                helper.assertTrue(Math.abs(snag.getYRot()) < 0.01F, "it holds its facing while it creaks, yaw " + snag.getYRot());
            } else if (creak[0] > 0 && snag.core().in(Step.SWEEP)) {
                swept[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(swept[0], "the creak ends in the sweep");
            helper.assertTrue(creak[0] == 12, "the creak lasts 12 ticks, saw " + creak[0]);
            float lost = pig.getMaxHealth() - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "the sweep does 1.2 x 5 = 6, the pig lost " + lost);
        });
    }

    /** Failing the counter, with its own AI: it walks in on a pig in front, creaks and sweeps it for 6. */
    private static void caughtInSweep(GameTestHelper helper) {
        Snagwood snag = snag(helper, 5.5, 2.5, false);
        Pig pig = pig(helper, 5.5, 2.5, 0, 3.5);
        snag.setTarget(pig);
        boolean[] creaked = {false};
        helper.onEachTick(() -> creaked[0] |= snag.core().in(Step.TELL));
        helper.succeedWhen(() -> {
            float lost = pig.getMaxHealth() - pig.getHealth();
            helper.assertTrue(creaked[0], "it creaked first");
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "caught in the sweep: 6, the pig lost " + lost);
        });
    }

    /** The counter: a pig that steps behind it during the creak is untouched; one that stays in the arc takes 6. */
    private static void stepBehind(GameTestHelper helper) {
        Snagwood snag = snag(helper, 5.5, 4.5, true);
        Pig dodger = pig(helper, 5.5, 4.5, 0, 2.0);
        Pig stayer = pig(helper, 5.5, 4.5, -40, 2.4);
        helper.runAtTickTime(2, () -> helper.assertTrue(snag.creak(dodger), "the creak starts"));
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(snag.core().in(Step.TELL), "still creaking at tick 8, phase " + snag.phase());
            dodger.snapTo(snag.getX(), dodger.getY(), snag.getZ() - 2.0);
        });
        helper.runAtTickTime(20, () -> {
            float dodged = dodger.getMaxHealth() - dodger.getHealth();
            float caught = stayer.getMaxHealth() - stayer.getHealth();
            helper.assertTrue(dodged == 0.0F, "the pig that stepped behind it is untouched, lost " + dodged);
            helper.assertTrue(Math.abs(caught - 6.0F) < 0.01F, "the pig left in the arc takes 6, lost " + caught);
            helper.succeed();
        });
    }

    /** The arc is 120 degrees: 58 degrees off its facing is hit, 62 degrees and straight behind are not. */
    private static void arcEdges(GameTestHelper helper) {
        double[][] spots = {{2.5, 1.5, 0.0}, {8.5, 1.5, 58.0}, {2.5, 7.5, 62.0}, {8.5, 7.5, 180.0}};
        float[] expect = {6.0F, 6.0F, 0.0F, 0.0F};
        Snagwood[] snags = new Snagwood[4];
        Pig[] pigs = new Pig[4];
        for (int i = 0; i < 4; i++) {
            snags[i] = snag(helper, spots[i][0], spots[i][1], true);
            pigs[i] = pig(helper, spots[i][0], spots[i][1], spots[i][2], 2.0);
        }
        helper.runAtTickTime(2, () -> {
            for (int i = 0; i < 4; i++) snags[i].sweep(helper.getLevel());
            for (int i = 0; i < 4; i++) {
                float lost = pigs[i].getMaxHealth() - pigs[i].getHealth();
                helper.assertTrue(Math.abs(lost - expect[i]) < 0.01F,
                        "a pig " + spots[i][2] + " degrees off its facing should lose " + expect[i] + ", lost " + lost);
            }
            helper.succeed();
        });
    }

    /** The sweep reaches 3 blocks: a pig 2.9 blocks ahead is hit, one 3.1 blocks ahead is not. */
    private static void radiusEdges(GameTestHelper helper) {
        Snagwood near = snag(helper, 2.5, 1.5, true);
        Snagwood far = snag(helper, 8.5, 1.5, true);
        Pig in = pig(helper, 2.5, 1.5, 0, 2.9);
        Pig out = pig(helper, 8.5, 1.5, 0, 3.1);
        helper.runAtTickTime(2, () -> {
            near.sweep(helper.getLevel());
            far.sweep(helper.getLevel());
            float a = in.getMaxHealth() - in.getHealth(), b = out.getMaxHealth() - out.getHealth();
            helper.assertTrue(Math.abs(a - 6.0F) < 0.01F, "2.9 blocks ahead is inside the reach: lost " + a + ", want 6");
            helper.assertTrue(b == 0.0F, "3.1 blocks ahead is outside it: lost " + b + ", want 0");
            helper.succeed();
        });
    }

    /**
     * Axes deal x2, before armour 6: a 4-damage axe blow becomes 8, which armour turns into 7.36; the same blow
     * bare-handed is 4, which armour turns into 3.36.
     */
    private static void axeDoubles(GameTestHelper helper) {
        Snagwood chopped = snag(helper, 2.5, 4.5, true);
        Snagwood punched = snag(helper, 8.5, 4.5, true);
        Husk axeman = around(helper, EntityTypes.HUSK, 2.5, 4.5, 0, 1.6);
        Husk boxer = around(helper, EntityTypes.HUSK, 8.5, 4.5, 0, 1.6);
        axeman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        helper.runAtTickTime(2, () -> {
            float c0 = chopped.getHealth(), p0 = punched.getHealth();
            chopped.hurtServer(helper.getLevel(), chopped.damageSources().mobAttack(axeman), 4.0F);
            punched.hurtServer(helper.getLevel(), punched.damageSources().mobAttack(boxer), 4.0F);
            float c = c0 - chopped.getHealth(), p = p0 - punched.getHealth();
            helper.assertTrue(Math.abs(c - 7.36F) < 0.01F, "an axe blow of 4 costs 7.36 (8 after x2, less armour 6), cost " + c);
            helper.assertTrue(Math.abs(p - 3.36F) < 0.01F, "a bare blow of 4 costs 3.36 (armour 6), cost " + p);
            helper.succeed();
        });
    }

    /** Health 30, attack 5, armour 6, a 0.9 x 2.6 hitbox. */
    private static void stats(GameTestHelper helper) {
        Snagwood snag = snag(helper, 5.5, 5.5, true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(snag.getMaxHealth() == 30.0F && snag.getHealth() == 30.0F, "health 30, got " + snag.getHealth() + "/" + snag.getMaxHealth());
            helper.assertTrue(snag.getAttributeValue(Attributes.ATTACK_DAMAGE) == 5.0, "attack 5, got " + snag.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(snag.getArmorValue() == 6, "armour 6, got " + snag.getArmorValue());
            helper.assertTrue(Math.abs(snag.getBbWidth() - 0.9F) < 0.01F && Math.abs(snag.getBbHeight() - 2.6F) < 0.01F,
                    "a 0.9 x 2.6 hitbox, got " + snag.getBbWidth() + " x " + snag.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: forests (birch forests too), never the dark forest; spawn weight 15, alone. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("snagwood"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.FOREST)), "its home holds the forest");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.BIRCH_FOREST)), "its home holds the birch forest");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.DARK_FOREST)), "its home never holds the dark forest");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/snagwood")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SNAGWOOD.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 15, "spawn weight 15, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got groups of " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }

    /**
     * Awake, it hunts a pig; the pig goes and 160 ticks later (no sooner) it roots back into its disguise, where it
     * stands.
     */
    private static void rootBack(GameTestHelper helper) {
        Snagwood snag = rooted(helper, 2.5, 2.5);
        Pig pig = pig(helper, 2.5, 2.5, 0, 8.0);
        int[] tick = {0};
        int[] since = {-1};
        int[] rootedAfter = {-1};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 2) {
                snag.standAwake();
                snag.setTarget(pig);
            }
            if (tick[0] == 20) {
                helper.assertTrue(!snag.disguised(), "with a target it stays awake, phase " + snag.phase());
                pig.discard();
                since[0] = 0;
            } else if (since[0] >= 0 && rootedAfter[0] < 0) {
                since[0]++;
                if (snag.disguised()) rootedAfter[0] = since[0];
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(rootedAfter[0] > 0, "it roots back, " + since[0] + " ticks without a target so far");
            helper.assertTrue(rootedAfter[0] == 160, "it roots back 160 ticks after it lost its target, took " + rootedAfter[0]);
            helper.assertTrue(snag.getTarget() == null, "rooted, it has no target");
        });
    }

    /** Rooted, a target 5.4 blocks away leaves it disguised; one 4.6 blocks away wakes it. */
    private static void wakeRange(GameTestHelper helper) {
        Snagwood stays = rooted(helper, 2.5, 1.5);
        Snagwood wakes = rooted(helper, 8.5, 1.5);
        Pig far = pig(helper, 2.5, 1.5, 0, 5.4);
        Pig near = pig(helper, 8.5, 1.5, 0, 4.6);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(stays.disguised() && wakes.disguised(), "fresh, both take root");
            stays.setTarget(far);
            wakes.setTarget(near);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(stays.disguised(), "a target 5.4 blocks away leaves it rooted, phase " + stays.phase());
            helper.assertTrue(!wakes.disguised(), "a target 4.6 blocks away wakes it, phase " + wakes.phase());
            helper.assertTrue(wakes.core().in(Step.UPROOT), "it tears its roots free first, phase " + wakes.phase());
            helper.succeed();
        });
    }

    /** Rooted, a survival player who walks within 4 blocks wakes it, and it turns on them. */
    private static void wakesForPlayer(GameTestHelper helper) {
        // in the middle of the arena, so a survivor from a neighbouring test is never within its wake range
        Snagwood snag = rooted(helper, 4.5, 2.5);
        // a follow range of 0 would make the target goal drop the player as soon as it is chosen; 6 keeps a player
        // 4 blocks off in reach and still ends short of the neighbouring arenas
        snag.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(6.0);
        ServerPlayer[] player = {null};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(snag.disguised(), "fresh, it takes root");
            player[0] = TestPlayers.survivor(helper, new Vec3(4.5, 1, 6.5));
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(!snag.disguised(), "a player 4 blocks away wakes it, phase " + snag.phase());
            helper.assertTrue(snag.getTarget() == player[0], "and it turns on them, target " + snag.getTarget());
            helper.succeed();
        });
    }

    /** Rooted, any blow wakes it at once and it turns on whoever struck it. */
    private static void hitWakes(GameTestHelper helper) {
        Snagwood snag = rooted(helper, 5.5, 2.5);
        Pig pig = pig(helper, 5.5, 2.5, 0, 9.0);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(snag.disguised(), "fresh, it takes root");
            snag.hurtServer(helper.getLevel(), snag.damageSources().mobAttack(pig), 2.0F);
            helper.assertTrue(snag.core().in(Step.UPROOT), "the blow wakes it, phase " + snag.phase());
            helper.assertTrue(snag.getTarget() == pig, "and it turns on the attacker");
            helper.succeed();
        });
    }

    /** Rooted, shoves and knockback leave it exactly where it stands and still disguised. */
    private static void disguiseHolds(GameTestHelper helper) {
        Snagwood snag = rooted(helper, 5.5, 5.5);
        Vec3[] start = {null};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(snag.disguised(), "fresh, it takes root");
            start[0] = snag.position();
            snag.knockback(2.0, -1.0, 0.0, snag.damageSources().generic(), 0.0F);
            snag.push(1.0, 0.3, 1.0);
            snag.setDeltaMovement(0.8, 0.0, -0.8);
        });
        helper.runAtTickTime(25, () -> {
            double moved = Math.hypot(snag.getX() - start[0].x, snag.getZ() - start[0].z);
            helper.assertTrue(moved < 0.01, "it never left its spot, moved " + moved);
            helper.assertTrue(snag.disguised(), "and it is still disguised, phase " + snag.phase());
            helper.assertTrue(Math.abs(snag.getYRot()) < 0.01F, "facing the way it took root, yaw " + snag.getYRot());
            helper.succeed();
        });
    }
}
