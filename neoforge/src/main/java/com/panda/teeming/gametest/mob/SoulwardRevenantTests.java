package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.SoulwardRevenant;
import com.panda.teeming.mob.nether.SoulwardRevenant.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Soulward Revenant: damage taken fills its gauge, twice over from 80 %; full, it kneels for 20 ticks and releases a
 * nova (8 damage, knockback 1.2, radius 5) that empties the gauge; a nova that catches no player cracks its breastplate
 * for 60 ticks (armour 0, gauge sealed).
 */
public final class SoulwardRevenantTests {
    private SoulwardRevenantTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("soulward_revenant_tell", SoulwardRevenantTests::tell);
        out.accept("soulward_revenant_counter", SoulwardRevenantTests::wastedNovaCracks);
        TeemingGameTests.timeLimit("soulward_revenant_counter", 140);
        out.accept("soulward_revenant_failure", SoulwardRevenantTests::novaCatchesPlayer);
        out.accept("soulward_revenant_gauge", SoulwardRevenantTests::gauge);
        out.accept("soulward_revenant_bright", SoulwardRevenantTests::bright);
        out.accept("soulward_revenant_reach", SoulwardRevenantTests::reach);
        out.accept("soulward_revenant_knockback", SoulwardRevenantTests::knockback);
        out.accept("soulward_revenant_stats", SoulwardRevenantTests::stats);
        out.accept("soulward_revenant_spawns", SoulwardRevenantTests::spawns);
        out.accept("soulward_revenant_nova_cancel", SoulwardRevenantTests::novaCancel);
    }

    private static SoulwardRevenant revenant(GameTestHelper helper, double x, double z) {
        SoulwardRevenant r = helper.spawn(ModMobs.SOULWARD_REVENANT.get(), new Vec3(x, 1, z));
        r.setNoAi(true);
        return r;
    }

    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        return pig;
    }

    /** Full at 25, it kneels for 20 ticks at full glow and hurts nothing until the nova. */
    private static void tell(GameTestHelper helper) {
        SoulwardRevenant r = revenant(helper, 5.5, 5.5);
        Pig pig = pig(helper, 7.5, 5.5);
        int[] kneel = {0};
        boolean[] hurtEarly = {false}, dim = {false};
        helper.runAtTickTime(2, () -> {
            r.setGauge(25.0F);
            helper.assertTrue(r.core().in(Step.TELL), "a full gauge starts the kneel, phase " + r.phase());
        });
        helper.onEachTick(() -> {
            if (!r.core().in(Step.TELL)) return;
            kneel[0]++;
            hurtEarly[0] |= pig.getHealth() < pig.getMaxHealth();
            dim[0] |= r.core().stage() != 3;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(kneel[0] > 0 && !r.core().in(Step.TELL), "the kneel ends, " + kneel[0] + " ticks so far");
            helper.assertTrue(Math.abs(kneel[0] - 20) <= 1, "the kneel lasts 20 ticks, lasted " + kneel[0]);
            helper.assertTrue(!hurtEarly[0], "nothing is hurt while it kneels");
            helper.assertTrue(!dim[0], "it burns at full glow the whole kneel");
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the nova comes when the kneel ends");
        });
    }

    /**
     * The counter: nobody in reach when it goes off. The wasted nova cracks its breastplate for 60 ticks: armour 6 drops
     * to 0 (a blow of 4 takes 3.36 before and 4 while cracked), the gauge stays sealed at 0, and the weak point shows.
     */
    private static void wastedNovaCracks(GameTestHelper helper) {
        SoulwardRevenant r = revenant(helper, 3.5, 5.5);
        Pig hitter = pig(helper, 9.5, 5.5);                          // 6 blocks off: out of the nova's reach
        int[] spent = {0};
        boolean[] open = {true}, over = {false};
        float[] tookArmoured = {Float.NaN}, tookCracked = {Float.NaN}, sealed = {Float.NaN};
        helper.runAtTickTime(2, () -> {
            float before = r.getHealth();
            r.hurtServer(helper.getLevel(), r.damageSources().mobAttack(hitter), 4.0F);
            tookArmoured[0] = before - r.getHealth();
        });
        helper.runAtTickTime(4, () -> r.setGauge(25.0F));
        helper.onEachTick(() -> {
            if (r.core().in(Step.SPENT)) {
                spent[0]++;
                open[0] &= r.vulnerable() && r.getArmorValue() == 0 && r.core().stage() == 0;
                if (spent[0] == 20) {
                    float before = r.getHealth();
                    r.hurtServer(helper.getLevel(), r.damageSources().mobAttack(hitter), 4.0F);
                    tookCracked[0] = before - r.getHealth();
                    sealed[0] = r.gauge();
                }
            } else if (spent[0] > 0) {
                over[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the crack heals, " + spent[0] + " ticks so far, phase " + r.phase());
            helper.assertTrue(Math.abs(tookArmoured[0] - 3.36F) < 0.01F, "armour 6 cuts a blow of 4 to 3.36, took " + tookArmoured[0]);
            helper.assertTrue(Math.abs(spent[0] - 60) <= 1, "the breastplate stays cracked 60 ticks, " + spent[0]);
            helper.assertTrue(open[0], "cracked: no armour, the weak point showing, the seams dark");
            helper.assertTrue(Math.abs(tookCracked[0] - 4.0F) < 0.01F, "cracked, a blow of 4 takes 4, took " + tookCracked[0]);
            helper.assertTrue(sealed[0] == 0.0F, "cracked, nothing fills the gauge, gauge " + sealed[0]);
            helper.assertTrue(r.getArmorValue() == 6 && r.core().idle(), "afterwards armour 6 again, got " + r.getArmorValue());
        });
    }

    /** Failing the counter: a player 2 blocks off takes the nova's 8 (scaled for difficulty), the gauge empties, no crack. */
    private static void novaCatchesPlayer(GameTestHelper helper) {
        SoulwardRevenant r = revenant(helper, 5.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(7.5, 1, 5.5));
        float before = p.getHealth();
        // mob damage to players scales with difficulty: 8 is 5 on Easy, 8 on Normal, 12 on Hard
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 5.0F;
            case HARD -> 12.0F;
            default -> 8.0F;
        };
        boolean[] cracked = {false};
        helper.runAtTickTime(2, () -> r.setGauge(25.0F));
        helper.onEachTick(() -> cracked[0] |= r.core().in(Step.SPENT));
        helper.runAtTickTime(2 + 20 + 10 + 10, () -> {
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the nova does " + expected + " here, the player lost " + lost);
            helper.assertTrue(r.gauge() == 0.0F && r.core().stage() == 0, "the nova empties the gauge, gauge " + r.gauge());
            helper.assertTrue(!cracked[0] && r.core().idle(), "a nova that caught a player cracks nothing, phase " + r.phase());
            helper.succeed();
        });
    }

    /** Every point of health it loses fills the gauge by one; armour's share does not count; 24.9 is not yet full. */
    private static void gauge(GameTestHelper helper) {
        SoulwardRevenant r = revenant(helper, 5.5, 5.5);
        Pig hitter = pig(helper, 9.5, 9.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(r.gauge() == 0.0F && r.core().stage() == 0, "it spawns empty and dark");
            r.hurtServer(helper.getLevel(), r.damageSources().magic(), 4.0F);
            helper.assertTrue(Math.abs(r.gauge() - 4.0F) < 0.01F, "4 damage fills it to 4, gauge " + r.gauge());
            helper.assertTrue(r.core().stage() == 1, "a little filled, the first glow, stage " + r.core().stage());
        });
        helper.runAtTickTime(14, () -> {
            r.setGauge(0.0F);
            r.hurtServer(helper.getLevel(), r.damageSources().mobAttack(hitter), 4.0F);
            helper.assertTrue(Math.abs(r.gauge() - 3.36F) < 0.01F, "a blow of 4 against armour 6 lands 3.36 and fills that, gauge " + r.gauge());
            r.setGauge(14.9F);
        });
        helper.runAtTickTime(26, () -> {
            helper.assertTrue(r.core().stage() == 2, "half full, the second glow, stage " + r.core().stage());
            r.hurtServer(helper.getLevel(), r.damageSources().magic(), 2.5F);   // 14.9 + 2.5 = 17.4: still under 80 %
            helper.assertTrue(Math.abs(r.gauge() - 17.4F) < 0.01F, "under 80 % it fills one for one, gauge " + r.gauge());
            r.setGauge(24.9F);
            helper.assertTrue(r.core().idle(), "24.9 is not full, phase " + r.phase());
        });
        helper.runAtTickTime(38, () -> {
            r.hurtServer(helper.getLevel(), r.damageSources().magic(), 1.0F);
            helper.assertTrue(r.gauge() == 25.0F, "the gauge tops out at 25, gauge " + r.gauge());
            helper.assertTrue(r.core().in(Step.TELL), "full, it kneels, phase " + r.phase());
            helper.succeed();
        });
    }

    /** At 19 a hit of 2 fills 2; at 20 (80 % of 25) it glows bright and the same hit fills 4. */
    private static void bright(GameTestHelper helper) {
        SoulwardRevenant r = revenant(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            r.setGauge(19.0F);
            helper.assertTrue(!r.bright() && r.core().stage() == 2, "19 is not bright, stage " + r.core().stage());
            r.hurtServer(helper.getLevel(), r.damageSources().magic(), 2.0F);
            helper.assertTrue(Math.abs(r.gauge() - 21.0F) < 0.01F, "below 80 % a hit of 2 fills 2, gauge " + r.gauge());
        });
        helper.runAtTickTime(14, () -> {
            r.setGauge(20.0F);
            helper.assertTrue(r.bright() && r.core().stage() == 3, "20 is bright, at full glow, stage " + r.core().stage());
            r.hurtServer(helper.getLevel(), r.damageSources().magic(), 2.0F);
            helper.assertTrue(Math.abs(r.gauge() - 24.0F) < 0.01F, "bright, a hit of 2 fills 4, gauge " + r.gauge());
            helper.succeed();
        });
    }

    /** The nova reaches 5 blocks and does 8: a pig 4.5 blocks off loses 8 of its 10, one 5.3 blocks off nothing. */
    private static void reach(GameTestHelper helper) {
        SoulwardRevenant r = revenant(helper, 5.5, 5.5);
        Pig inside = pig(helper, 1.0, 5.5);
        Pig outside = pig(helper, 5.5 + 3.75, 5.5 + 3.75);
        helper.runAtTickTime(2, () -> {
            int hit = r.nova(helper.getLevel());
            helper.assertTrue(hit == 1, "one pig within 5 blocks is hit, hit " + hit);
            float lost = inside.getMaxHealth() - inside.getHealth();
            helper.assertTrue(Math.abs(lost - 8.0F) < 0.01F, "4.5 blocks off it does 8, lost " + lost);
            helper.assertTrue(outside.getHealth() >= outside.getMaxHealth(), "5.3 blocks off is out of reach");
            helper.succeed();
        });
    }

    /**
     * Knockback 1.2: on the tick of the nova a pig 2 blocks off is moving straight out at 0.6 a tick and up at 0.4, and it
     * comes to rest about 3.1 blocks further out (measured in this open arena), just past the 5-block reach.
     */
    private static void knockback(GameTestHelper helper) {
        SoulwardRevenant r = revenant(helper, 2.5, 5.5);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 5.5));
        pig.removeFreeWill();                                         // its body moves; it walks nowhere by itself
        double startX = helper.absoluteVec(new Vec3(4.5, 1, 5.5)).x;
        int[] still = {0};
        helper.runAtTickTime(2, () -> {
            r.nova(helper.getLevel());
            Vec3 v = pig.getDeltaMovement();
            helper.assertTrue(Math.abs(v.x - 0.6) < 0.01 && Math.abs(v.z) < 0.01, "thrown straight out at 0.6 a tick, " + v);
            helper.assertTrue(Math.abs(v.y - 0.4) < 0.01, "and up at 0.4, " + v);
        });
        helper.onEachTick(() -> {
            if (helper.getTick() > 4 && pig.getDeltaMovement().horizontalDistance() < 0.003) still[0]++;
            else still[0] = 0;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(still[0] >= 3, "the pig comes to rest, at x " + pig.getX());
            double moved = pig.getX() - startX;
            helper.assertTrue(Math.abs(moved - 3.1) < 0.4, "it comes to rest about 3.1 blocks further out, moved " + moved);
            helper.assertTrue(pig.distanceTo(r) > 5.0, "past the nova's reach, " + pig.distanceTo(r) + " blocks from it");
        });
    }

    private static void stats(GameTestHelper helper) {
        SoulwardRevenant r = revenant(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(r.getMaxHealth() == 40.0F && r.getHealth() == 40.0F, "health 40, got " + r.getHealth() + "/" + r.getMaxHealth());
            helper.assertTrue(r.getAttributeValue(Attributes.ATTACK_DAMAGE) == 6.0, "attack 6, got " + r.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(r.getArmorValue() == 6, "armour 6, got " + r.getArmorValue());
            helper.assertTrue(Math.abs(r.getBbWidth() - 0.8F) < 0.01F && Math.abs(r.getBbHeight() - 2.2F) < 0.01F,
                    "a 0.8 x 2.2 hitbox, got " + r.getBbWidth() + " x " + r.getBbHeight());
            helper.succeed();
        });
    }

    /** Its home: the biome modifier adds it to soul sand valleys at weight 6, alone. */
    private static void spawns(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/soulward_revenant"));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key).map(Holder::value).orElse(null);
        if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) {
            helper.fail("no spawn biome modifier for the Soulward Revenant: " + modifier);
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> spawners = add.spawners().unwrap();
        helper.assertTrue(spawners.size() == 1 && spawners.getFirst().value().type() == ModMobs.SOULWARD_REVENANT.get(), "one Soulward Revenant spawner, " + spawners);
        Weighted<MobSpawnSettings.SpawnerData> spawner = spawners.getFirst();
        helper.assertTrue(spawner.weight() == 6, "spawn weight 6, got " + spawner.weight());
        helper.assertTrue(spawner.value().count().minInclusive() == 1 && spawner.value().count().maxInclusive() == 1,
                "groups of 1, got " + spawner.value().count().minInclusive() + " to " + spawner.value().count().maxInclusive());
        int tunedWeight = com.panda.teeming.spawn.SpawnTable.tuned("soulward_revenant").stream().filter(r -> r.biome().equals("minecraft:soul_sand_valley"))
                .mapToInt(com.panda.teeming.spawn.SpawnTable.Row::weight).findFirst().orElse(0);
        var tunedModifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/soulward_revenant/soul_sand_valley"))).map(Holder::value).orElse(null);
        if (!(tunedModifier instanceof BiomeModifiers.AddSpawnsBiomeModifier tunedAdd)) {
            helper.fail("no tuned spawn modifier for the soul sand valley: " + tunedModifier);
            return;
        }
        var tunedSpawner = tunedAdd.spawners().unwrap().getFirst();
        helper.assertTrue(tunedWeight > 0 && tunedSpawner.value().type() == ModMobs.SOULWARD_REVENANT.get() && tunedSpawner.weight() == tunedWeight,
                "the soul sand valley: tuned weight " + tunedWeight + " for its share, got " + tunedSpawner.weight());
        helper.assertTrue(tunedAdd.biomes().contains(access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.SOUL_SAND_VALLEY)), "the soul sand valley is its home");
        helper.assertTrue(!add.biomes().contains(access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.SOUL_SAND_VALLEY)),
                "the roster weight leaves out the soul sand valley, which has its own");
        helper.succeed();
    }

    /** A listener that cancels the "nova" step: it stands back up from the kneel still full, and nothing is hurt. */
    private static void novaCancel(GameTestHelper helper) {
        SoulwardRevenant r = revenant(helper, 5.5, 5.5);
        Pig pig = pig(helper, 7.5, 5.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == r && e.step().equals("nova")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] knelt = {false};
        helper.runAtTickTime(2, () -> r.setGauge(25.0F));
        helper.onEachTick(() -> knelt[0] |= r.core().in(Step.TELL));
        helper.runAtTickTime(2 + 20 + 6, () -> {
            helper.assertTrue(knelt[0] && r.core().idle(), "it knelt and stood back up, phase " + r.phase());
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "a cancelled nova hurts nothing");
            helper.assertTrue(r.gauge() == 25.0F, "it is still full, gauge " + r.gauge());
            helper.succeed();
        });
    }
}
