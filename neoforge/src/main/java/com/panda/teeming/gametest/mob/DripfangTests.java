package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.Dripfang;
import com.panda.teeming.mob.overworld.Dripfang.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Dripfang: hanging under a ceiling, a target within 1.5 blocks across the ground beneath it sets off a 12-tick drip
 * that hurts nothing; then it drops point-first and impales the target for 10.5 (1.5 x 7). A target that steps aside
 * during the drip is missed, and the miss sticks it in the ground for exactly 60 ticks, taking x1.5. After a hit, or
 * once it wrenches free, it climbs back to the spot it hung from. Every number is checked against a plain value.
 *
 * <p>Every hanging Dripfang here grips a stone block set 5 above the arena floor (its point 3.6 up), under the arena's
 * barrier roof. Pigs stand in as targets with 40 health, so the 10.5 hit leaves them standing to be read.
 */
public final class DripfangTests {
    private DripfangTests() {}

    /** The underside of the stone each hanging Dripfang grips, above the arena floor. */
    private static final int CEILING = 5;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("dripfang_tell", DripfangTests::tell);
        out.accept("dripfang_counter", DripfangTests::stepAside);
        out.accept("dripfang_failure", DripfangTests::impaled);
        out.accept("dripfang_range", DripfangTests::rangeEdges);
        out.accept("dripfang_stuck_length", DripfangTests::stuckLength);
        TeemingGameTests.timeLimit("dripfang_stuck_length", 200);
        out.accept("dripfang_stuck_damage", DripfangTests::stuckDamage);
        out.accept("dripfang_stats", DripfangTests::stats);
        out.accept("dripfang_home", DripfangTests::home);
        out.accept("dripfang_grips_ceiling", DripfangTests::gripsCeiling);
        out.accept("dripfang_hit_drips", DripfangTests::hitDrips);
        out.accept("dripfang_drips_for_player", DripfangTests::dripsForPlayer);
        out.accept("dripfang_drop_cancel", DripfangTests::dropCancel);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /**
     * A Dripfang with the base look hanging from a stone block set over ({@code x}, {@code z}), ready to drip at once.
     * Its follow range is 0, so it only hunts what the test gives it or what passes beneath it.
     */
    private static Dripfang hanging(GameTestHelper helper, double x, double z) {
        helper.setBlock(new BlockPos((int) Math.floor(x), CEILING, (int) Math.floor(z)), Blocks.STONE);
        Dripfang fang = helper.spawn(ModMobs.DRIPFANG.get(), new Vec3(x, CEILING - 1.4, z));
        fang.core().setLook("base", true);
        fang.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        helper.assertTrue(fang.hangHere(), "it grips the stone above it");
        fang.resetRearm();
        return fang;
    }

    /** A still pig on the floor with 40 health. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        return pig;
    }

    private static float lost(Pig pig) {
        return pig.getMaxHealth() - pig.getHealth();
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * With a pig 0.6 blocks off beneath it, it drips at once, for exactly 12 ticks, holding still and hurting nothing;
     * then the drop takes 10.5 from the pig.
     */
    private static void tell(GameTestHelper helper) {
        Dripfang fang = hanging(helper, 5.5, 5.5);
        Pig pig = pig(helper, 6.1, 5.5);
        fang.setTarget(pig);
        Vec3 start = fang.position();
        int[] drip = {0};
        boolean[] dropped = {false};
        helper.onEachTick(() -> {
            if (fang.core().in(Step.TELL)) {
                drip[0]++;
                helper.assertTrue(lost(pig) == 0.0F, "the drip hurts nothing yet, the pig lost " + lost(pig));
                helper.assertTrue(fang.position().distanceTo(start) < 0.01, "it holds still while it drips");
            } else if (drip[0] > 0 && fang.core().in(Step.DROP)) {
                dropped[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dropped[0], "the drip ends in the drop, phase " + fang.phase());
            helper.assertTrue(drip[0] == 12, "the drip lasts 12 ticks, saw " + drip[0]);
            helper.assertTrue(Math.abs(lost(pig) - 10.5F) < 0.01F, "the drop does 1.5 x 7 = 10.5, the pig lost " + lost(pig));
        });
    }

    /**
     * The counter: a pig beneath it that steps 3 blocks aside in the middle of the drip is untouched, and the missed
     * drop sticks it in the ground.
     */
    private static void stepAside(GameTestHelper helper) {
        Dripfang fang = hanging(helper, 5.5, 5.5);
        Pig pig = pig(helper, 6.0, 5.5);
        fang.setTarget(pig);
        int[] drip = {0};
        helper.onEachTick(() -> {
            if (fang.core().in(Step.TELL) && ++drip[0] == 6) pig.snapTo(helper.absoluteVec(new Vec3(8.5, 1, 5.5)));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(drip[0] > 0, "it dripped at the pig");
            helper.assertTrue(fang.core().in(Step.STUCK), "the missed drop sticks it in the ground, phase " + fang.phase());
            helper.assertTrue(lost(pig) == 0.0F, "the pig that stepped aside is untouched, lost " + lost(pig));
            helper.assertTrue(fang.vulnerable(), "stuck, it is open to punishment");
        });
    }

    /**
     * Failing the counter: a pig 1.2 blocks off beneath it that stays put is impaled for 10.5; then the Dripfang climbs
     * back up and hangs at the spot it dropped from.
     */
    private static void impaled(GameTestHelper helper) {
        Dripfang fang = hanging(helper, 5.5, 5.5);
        Vec3 home = fang.position();
        Pig pig = pig(helper, 5.5, 6.7);
        fang.setTarget(pig);
        boolean[] dropped = {false}, climbed = {false}, stuck = {false};
        helper.onEachTick(() -> {
            dropped[0] |= fang.core().in(Step.DROP);
            climbed[0] |= dropped[0] && fang.core().in(Step.CLIMB);
            stuck[0] |= fang.core().in(Step.STUCK);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(lost(pig) - 10.5F) < 0.01F, "impaled: 10.5, the pig lost " + lost(pig));
            helper.assertTrue(!stuck[0], "a hit never sticks it");
            helper.assertTrue(climbed[0], "after the hit it climbs back up");
            helper.assertTrue(fang.disguised(), "and hangs again, phase " + fang.phase());
            helper.assertTrue(fang.position().distanceTo(home) < 0.01, "at the spot it dropped from, " + fang.position().distanceTo(home) + " off");
        });
    }

    /** The drop zone is 1.5 blocks across the ground: a pig 1.4 blocks off beneath it sets off the drip, one 1.6 off doesn't. */
    private static void rangeEdges(GameTestHelper helper) {
        Dripfang near = hanging(helper, 2.5, 3.5);
        Dripfang far = hanging(helper, 8.5, 3.5);
        Pig in = pig(helper, 2.5, 4.9);
        Pig out = pig(helper, 8.5, 5.1);
        near.setTarget(in);
        far.setTarget(out);
        boolean[] nearDripped = {false}, farDripped = {false};
        helper.onEachTick(() -> {
            nearDripped[0] |= !near.disguised();
            farDripped[0] |= !far.disguised();
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(nearDripped[0], "a pig 1.4 blocks off beneath it sets off the drip, phase " + near.phase());
            helper.assertTrue(!farDripped[0], "a pig 1.6 blocks off doesn't, phase " + far.phase());
            helper.assertTrue(lost(out) == 0.0F, "and is untouched");
            helper.succeed();
        });
    }

    /**
     * A drip over empty floor ends in a miss that sticks it for exactly 60 ticks; then it wrenches free, climbs and
     * hangs again at the spot it dropped from.
     */
    private static void stuckLength(GameTestHelper helper) {
        Dripfang fang = hanging(helper, 5.5, 5.5);
        Vec3 home = fang.position();
        int[] tick = {0}, stuck = {0};
        boolean[] climbed = {false};
        helper.onEachTick(() -> {
            if (++tick[0] == 2) helper.assertTrue(fang.drip(null), "the drip starts");
            if (fang.core().in(Step.STUCK)) stuck[0]++;
            climbed[0] |= stuck[0] > 0 && fang.core().in(Step.CLIMB);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stuck[0] > 0, "the drop missed and stuck it, phase " + fang.phase());
            helper.assertTrue(climbed[0], "it climbs once free, stuck " + stuck[0] + " ticks so far");
            helper.assertTrue(stuck[0] == 60, "it was stuck 60 ticks, saw " + stuck[0]);
            helper.assertTrue(fang.disguised(), "and hangs again, phase " + fang.phase());
            helper.assertTrue(fang.position().distanceTo(home) < 0.01, "at the spot it dropped from, " + fang.position().distanceTo(home) + " off");
        });
    }

    /**
     * Stuck, it takes x1.5 before armour 2: a 4-damage blow becomes 6, which armour turns into 5.904; the same blow on a
     * free one is 4, which armour turns into 3.936.
     */
    private static void stuckDamage(GameTestHelper helper) {
        Dripfang stuck = helper.spawn(ModMobs.DRIPFANG.get(), new Vec3(2.5, 1, 4.5));
        Dripfang free = helper.spawn(ModMobs.DRIPFANG.get(), new Vec3(8.5, 1, 4.5));
        Pig pig = pig(helper, 5.5, 8.5);
        for (Dripfang fang : new Dripfang[] {stuck, free}) {
            fang.core().setLook("base", true);
            fang.setNoAi(true);
        }
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(stuck.stick(), "it sticks");
            float s0 = stuck.getHealth(), f0 = free.getHealth();
            stuck.hurtServer(helper.getLevel(), stuck.damageSources().mobAttack(pig), 4.0F);
            free.hurtServer(helper.getLevel(), free.damageSources().mobAttack(pig), 4.0F);
            float s = s0 - stuck.getHealth(), f = f0 - free.getHealth();
            helper.assertTrue(Math.abs(s - 5.904F) < 0.01F, "stuck, a blow of 4 costs 5.904 (6 after x1.5, less armour 2), cost " + s);
            helper.assertTrue(Math.abs(f - 3.936F) < 0.01F, "free, a blow of 4 costs 3.936 (armour 2), cost " + f);
            helper.succeed();
        });
    }

    /** Health 16, attack 7, armour 2, a 0.6 x 1.4 hitbox. */
    private static void stats(GameTestHelper helper) {
        Dripfang fang = helper.spawn(ModMobs.DRIPFANG.get(), new Vec3(5.5, 1, 5.5));
        fang.core().setLook("base", true);
        fang.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(fang.getMaxHealth() == 16.0F && fang.getHealth() == 16.0F, "health 16, got " + fang.getHealth() + "/" + fang.getMaxHealth());
            helper.assertTrue(fang.getAttributeValue(Attributes.ATTACK_DAMAGE) == 7.0, "attack 7, got " + fang.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(fang.getArmorValue() == 2, "armour 2, got " + fang.getArmorValue());
            helper.assertTrue(Math.abs(fang.getBbWidth() - 0.6F) < 0.01F && Math.abs(fang.getBbHeight() - 1.4F) < 0.01F,
                    "a 0.6 x 1.4 hitbox, got " + fang.getBbWidth() + " x " + fang.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: dripstone caves; spawn weight 20, groups of 1 to 2. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("dripfang"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.DRIPSTONE_CAVES)), "its home holds the dripstone caves");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/dripfang")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.DRIPFANG.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 20, "spawn weight 20, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "groups of 1 to 2, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }

    // ---- the disguise ------------------------------------------------------------------------------------------------

    /**
     * A fresh Dripfang just under a ceiling grips it on its first tick, its crown flush with the stone: one spawned
     * with its top 0.6 short of the stone rises to it, one spawned overlapping the stone (feet in the air block under
     * it) settles down to it. Hanging, shoves and knockback leave it where it is, still disguised.
     */
    private static void gripsCeiling(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, CEILING, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(8, CEILING, 5), Blocks.STONE);
        Dripfang low = helper.spawn(ModMobs.DRIPFANG.get(), new Vec3(2.5, CEILING - 2, 5.5));
        Dripfang high = helper.spawn(ModMobs.DRIPFANG.get(), new Vec3(8.5, CEILING - 1, 5.5));
        for (Dripfang fang : new Dripfang[] {low, high}) {
            fang.core().setLook("base", true);
            fang.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        }
        double crown = helper.absoluteVec(new Vec3(0, CEILING, 0)).y;
        helper.runAtTickTime(3, () -> {
            for (Dripfang fang : new Dripfang[] {low, high}) {
                helper.assertTrue(fang.disguised(), "it grips the ceiling on its first tick, phase " + fang.phase());
                double top = fang.getY() + fang.getBbHeight();
                helper.assertTrue(Math.abs(top - crown) < 0.01, "its crown is flush with the stone, " + (top - crown) + " off");
            }
            low.knockback(2.0, -1.0, 0.0, low.damageSources().generic(), 0.0F);
            low.push(1.0, 0.3, 1.0);
            low.setDeltaMovement(0.8, -0.5, -0.8);
        });
        helper.runAtTickTime(25, () -> {
            double top = low.getY() + low.getBbHeight();
            helper.assertTrue(Math.abs(top - crown) < 0.01 && Math.abs(low.getX() - helper.absoluteVec(new Vec3(2.5, 0, 0)).x) < 0.01,
                    "shoved, it never left its spot");
            helper.assertTrue(low.disguised() && low.isNoGravity(), "and still hangs, phase " + low.phase());
            helper.succeed();
        });
    }

    /** Hanging, any blow shakes it into the drip at once, and it turns on whoever struck it. */
    private static void hitDrips(GameTestHelper helper) {
        Dripfang fang = hanging(helper, 5.5, 5.5);
        Pig pig = pig(helper, 5.5, 9.5);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(fang.disguised(), "it hangs");
            fang.hurtServer(helper.getLevel(), fang.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(fang.core().in(Step.TELL), "the blow sets off the drip, phase " + fang.phase());
            helper.assertTrue(fang.getTarget() == pig, "and it turns on the attacker");
            helper.succeed();
        });
    }

    /** A survival player who walks in beneath it, 1 block off, sets off the drip, aimed at them. */
    private static void dripsForPlayer(GameTestHelper helper) {
        Dripfang fang = hanging(helper, 4.5, 4.5);
        ServerPlayer[] player = {null};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(fang.disguised(), "it hangs");
            player[0] = TestPlayers.survivor(helper, new Vec3(5.5, 1, 4.5));
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(fang.core().in(Step.TELL), "a player beneath it sets off the drip, phase " + fang.phase());
            helper.assertTrue(fang.getTarget() == player[0], "and it turns on them, target " + fang.getTarget());
            helper.assertTrue(fang.aim().distanceTo(player[0].position()) < 0.01, "it aims where they stand");
            helper.succeed();
        });
    }

    /** A pack that cancels the drop step keeps it hanging: the drip ends with no drop and no harm done. */
    private static void dropCancel(GameTestHelper helper) {
        Dripfang fang = hanging(helper, 5.5, 5.5);
        Pig pig = pig(helper, 5.8, 5.5);
        fang.setTarget(pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == fang && e.step().equals("drop")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] dripped = {false}, dropped = {false};
        helper.onEachTick(() -> {
            dripped[0] |= fang.core().in(Step.TELL);
            dropped[0] |= fang.core().in(Step.DROP);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(dripped[0], "it dripped");
            helper.assertTrue(!dropped[0], "a cancelled drop never happens");
            helper.assertTrue(fang.disguised(), "it keeps its grip, phase " + fang.phase());
            helper.assertTrue(lost(pig) == 0.0F, "and the pig is untouched, lost " + lost(pig));
            helper.succeed();
        });
    }
}
