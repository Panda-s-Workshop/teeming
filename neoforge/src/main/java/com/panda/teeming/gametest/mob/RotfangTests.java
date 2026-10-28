package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Rotfang;
import com.panda.teeming.mob.end.Rotfang.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Rotfang. A target in reach is snarled at for exactly 10 ticks (it holds still and nothing is hurt yet), then bitten
 * for the attack of 6 (armour counts: a pig in 10 armour loses 4.32) with no knockback, and given Void Rot for exactly
 * 160 ticks: shown on the player, it ends after 160 ticks on a pig, halves healing (a heal of 4 gives 2) and stops a
 * full food bar's natural regeneration. Struck during the snarl it bites itself instead: the striker keeps its health
 * and loses its own Void Rot, and the Rotfang takes the blow plus its own bite of 6 through its armour (6.844 in all),
 * credited to the striker, and rots. Stepping out of reach makes the jaws close on nothing; a target on a pillar is
 * never snarled at; killed mid-snarl it bites no one. A pair takes turns, and a Rotfang hands its target only to a
 * packmate that can reach it. Groups of 1-2, stats, home and spawn rule against plain values.
 */
public final class RotfangTests {
    private RotfangTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("rotfang_tell", RotfangTests::tell);
        out.accept("rotfang_counter", RotfangTests::counter);
        out.accept("rotfang_failure", RotfangTests::failure);
        out.accept("rotfang_rot_length", RotfangTests::rotLength);
        TeemingGameTests.timeLimit("rotfang_rot_length", 220);
        out.accept("rotfang_rot_healing", RotfangTests::rotHealing);
        out.accept("rotfang_rot_no_regen", RotfangTests::rotNoRegen);
        out.accept("rotfang_armour", RotfangTests::armour);
        out.accept("rotfang_no_knockback", RotfangTests::noKnockback);
        out.accept("rotfang_step_back", RotfangTests::stepBack);
        out.accept("rotfang_pillar", RotfangTests::pillar);
        out.accept("rotfang_dies_mid_tell", RotfangTests::diesMidTell);
        out.accept("rotfang_gnaw_veto", RotfangTests::gnawVeto);
        out.accept("rotfang_pack_turns", RotfangTests::packTurns);
        out.accept("rotfang_rally", RotfangTests::rally);
        out.accept("rotfang_save_load", RotfangTests::saveLoad);
        out.accept("rotfang_group", RotfangTests::group);
        out.accept("rotfang_spawn_rule", RotfangTests::spawnRule);
        out.accept("rotfang_stats", RotfangTests::stats);
        out.accept("rotfang_home", RotfangTests::home);
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------------

    /**
     * A Rotfang with the base look at {@code (x, 1, z)} facing {@code yaw}, no goals, a follow range of 1 (it notices no
     * survivor in another arena; {@link #aim} sets it on one in its own), removed when the test ends.
     */
    private static Rotfang fang(GameTestHelper helper, double x, double z, float yaw) {
        Rotfang f = helper.spawn(ModMobs.ROTFANG.get(), new Vec3(x, 1, z));
        f.core().setLook("base", true);
        f.removeFreeWill();
        f.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        f.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);   // test blows don't shove it off its spot
        turn(f, yaw);
        TestPlayers.whenDone(helper, f::discard);
        return f;
    }

    /** A Rotfang at {@code (x, 1, z)} facing +X. */
    private static Rotfang fang(GameTestHelper helper, double x, double z) {
        return fang(helper, x, z, -90.0F);
    }

    /** A still pig with 100 health, removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /**
     * Sets {@code f} on the survivor {@code p} in its own arena and keeps it there. Its player-targeting goal looks at
     * least 2 blocks out whatever the follow range, and drops a target past the follow range, so with a range of 1 it
     * would pick up the survivor 1.4 away and let go of it again; a range of 8 keeps it, and the nearest player is
     * always this arena's own.
     */
    private static void aim(Rotfang f, ServerPlayer p) {
        f.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0);
        f.setTarget(p);
    }

    private static void turn(LivingEntity e, float yaw) {
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.yHeadRotO = yaw;
        e.yBodyRot = yaw;
        e.yBodyRotO = yaw;
    }

    private static boolean rotting(LivingEntity e) {
        return e.hasEffect(ModEffects.VOID_ROT.holder());
    }

    /** What one bite costs a player: the attack of 6, scaled by difficulty as every mob's blow on a player is. */
    private static float biteOnPlayer(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case PEACEFUL -> 0.0F;
            case EASY -> 4.0F;
            case HARD -> 9.0F;
            default -> 6.0F;
        };
    }

    // ---- the snarl and the bite --------------------------------------------------------------------------------------

    /**
     * The tell: a pig in reach is snarled at for exactly 10 ticks; meanwhile the Rotfang holds still and the pig is
     * unhurt and not rotting. Then the jaws shut: the pig has lost exactly the attack of 6 and rots.
     */
    private static void tell(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 6.5);
        f.setTarget(pig);
        int[] snarl = {0};
        Vec3[] start = {null};
        float[] after = {-1};
        helper.onEachTick(() -> {
            if (f.core().in(Step.SNARL)) {
                if (start[0] == null) start[0] = f.position();
                snarl[0]++;
                helper.assertTrue(f.position().distanceTo(start[0]) < 0.01, "it holds still through the snarl");
                helper.assertTrue(pig.getHealth() >= 100.0F && !rotting(pig), "the snarl hurts and rots nothing yet");
            } else if (snarl[0] > 0 && after[0] < 0) {
                after[0] = pig.getHealth();
                helper.assertTrue(f.core().in(Step.BITE), "the snarl ends in the bite, phase " + f.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(after[0] >= 0, "it snarled and bit");
            helper.assertTrue(snarl[0] == 10, "the snarl lasts 10 ticks, saw " + snarl[0]);
            float lost = 100.0F - after[0];
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "the bite takes 6, the pig lost " + lost);
            helper.assertTrue(f.bites() == 1 && f.rots() == 1 && rotting(pig), "and the pig rots");
        });
    }

    /**
     * The counter: a survival player already rotting stands in reach and strikes the Rotfang for 1, 5 ticks into its
     * snarl. Its jaw turns on itself: it gnaws its own leg (no bite comes, the player keeps all 20 health through the
     * gnaw), the player's Void Rot is drawn out, and the Rotfang lost exactly the blow (1 through armour 2: 0.94) plus its
     * own bite (6 through armour 2: 5.904), 6.844 in all, last hurt by the player, and rots itself.
     */
    private static void counter(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.9, 1, 6.5));
        ModEffects.give(p, ModEffects.VOID_ROT, 160, null);
        aim(f, p);
        boolean[] struck = {false};
        boolean[] gnawEnded = {false};
        float[] lost = {-1};
        helper.onEachTick(() -> {
            if (!struck[0] && f.core().in(Step.SNARL) && f.core().elapsed() == 5) {
                struck[0] = true;
                f.hurtServer(helper.getLevel(), f.damageSources().playerAttack(p), 1.0F);
                lost[0] = 26.0F - f.getHealth();
                helper.assertTrue(f.core().in(Step.GNAW), "struck in the snarl it gnaws itself, phase " + f.phase());
                helper.assertTrue(f.gnaws() == 1 && f.cleanses() == 1, "it bit itself once and drew the rot out once");
                helper.assertTrue(!rotting(p), "the player's Void Rot is gone");
                helper.assertTrue(rotting(f), "the Rotfang rots itself");
                helper.assertTrue(f.getLastDamageSource() != null && f.getLastDamageSource().getEntity() == p,
                        "its own bite is credited to the player");
            } else if (struck[0] && !gnawEnded[0] && !f.core().in(Step.GNAW)) {
                gnawEnded[0] = true;
                helper.assertTrue(f.bites() == 0 && f.rots() == 0, "no bite came from the snarl");
                helper.assertTrue(p.getHealth() >= 20.0F && !rotting(p), "the player is unhurt and clean, health " + p.getHealth());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gnawEnded[0], "it was struck in the snarl and gnawed itself through");
            helper.assertTrue(Math.abs(lost[0] - 6.844F) < 0.01F, "the blow and its own bite cost it 6.844, it lost " + lost[0]);
        });
    }

    /**
     * Failing the counter: a survival player stands in reach through the snarl. The bite costs the attack of 6 (at this
     * difficulty) and gives Void Rot for exactly 160 ticks, shown on the player with particles and an icon.
     */
    private static void failure(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.9, 1, 6.5));
        aim(f, p);
        helper.succeedWhen(() -> {
            helper.assertTrue(f.bites() == 1, "it bit once");
            float lost = 20.0F - p.getHealth();
            helper.assertTrue(Math.abs(lost - biteOnPlayer(helper)) < 0.01F, "the bite cost " + biteOnPlayer(helper) + ", the player lost " + lost);
            MobEffectInstance rot = p.getEffect(ModEffects.VOID_ROT.holder());
            helper.assertTrue(rot != null, "the player has Void Rot");
            helper.assertTrue(rot.getDuration() == 160, "for 160 ticks, got " + rot.getDuration());
            helper.assertTrue(rot.isVisible() && rot.showIcon() && rot.getAmplifier() == 0, "shown: particles and an icon, level I");
        });
    }

    /**
     * Void Rot lasts exactly 160 ticks: a pig (spawned before the Rotfang, so it has ticked before the bite lands in the
     * same tick) is bitten once; the Rotfang leaves at once. The rot is first seen with 160 ticks left and is on the pig
     * for exactly 160 ticks, then gone.
     */
    private static void rotLength(GameTestHelper helper) {
        Pig pig = pig(helper, 6.9, 6.5);
        Rotfang f = fang(helper, 5.5, 6.5);
        f.setTarget(pig);
        int[] first = {-1};
        int[] seen = {0};
        boolean[] ended = {false};
        helper.onEachTick(() -> {
            if (f.bites() == 1 && !f.isRemoved()) f.discard();                // one bite only
            if (ended[0]) return;
            if (rotting(pig)) {
                if (first[0] < 0) first[0] = pig.getEffect(ModEffects.VOID_ROT.holder()).getDuration();
                seen[0]++;
            } else if (seen[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the pig was bitten and the rot ran out");
            helper.assertTrue(first[0] == 160, "160 ticks left when first seen, got " + first[0]);
            helper.assertTrue(seen[0] == 160, "on the pig for 160 ticks, saw " + seen[0]);
        });
    }

    /**
     * Healing halved: a pig bitten by the Rotfang heals 2 from a heal of 4; an unbitten pig beside it heals 4.
     */
    private static void rotHealing(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        Pig bitten = pig(helper, 6.9, 6.5);
        Pig healthy = pig(helper, 6.9, 9.5);
        f.setTarget(bitten);
        helper.succeedWhen(() -> {
            helper.assertTrue(f.rots() == 1 && rotting(bitten) && !rotting(healthy), "the bitten pig rots");
            f.discard();
            float[] gains = new float[2];
            Pig[] pigs = {bitten, healthy};
            for (int i = 0; i < 2; i++) {
                pigs[i].setHealth(50.0F);
                pigs[i].heal(4.0F);
                gains[i] = pigs[i].getHealth() - 50.0F;
            }
            helper.assertTrue(Math.abs(gains[0] - 2.0F) < 1.0E-4F, "rotting, a heal of 4 gives 2, gave " + gains[0]);
            helper.assertTrue(Math.abs(gains[1] - 4.0F) < 1.0E-4F, "healthy, it gives 4, gave " + gains[1]);
        });
    }

    /**
     * No natural regeneration: a survival player bitten by the Rotfang and one never bitten, both at 10 health with a
     * full food bar. Over 40 ticks the healthy one heals; the bitten one stays at 10 and spends no food on it.
     */
    private static void rotNoRegen(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        ServerPlayer bitten = TestPlayers.survivor(helper, new Vec3(6.9, 1, 6.5));
        ServerPlayer healthy = TestPlayers.survivor(helper, new Vec3(6.9, 1, 9.5));
        aim(f, bitten);
        helper.succeedWhen(() -> {
            helper.assertTrue(f.rots() == 1 && rotting(bitten) && !rotting(healthy), "the bitten player rots");
            f.discard();
            for (ServerPlayer p : List.of(bitten, healthy)) {
                p.getFoodData().setFoodLevel(20);
                p.getFoodData().setSaturation(5.0F);
                p.setHealth(10.0F);
            }
            for (int i = 0; i < 40; i++) {
                bitten.doTick();
                healthy.doTick();
            }
            helper.assertTrue(healthy.getHealth() > 10.5F, "never bitten, the full food bar heals, health " + healthy.getHealth());
            helper.assertTrue(Math.abs(bitten.getHealth() - 10.0F) < 1.0E-4F && rotting(bitten), "bitten, no natural regeneration: health "
                    + bitten.getHealth());
            helper.assertTrue(bitten.getFoodData().getSaturationLevel() == 5.0F, "and no food spent on it, saturation "
                    + bitten.getFoodData().getSaturationLevel());
        });
    }

    // ---- armour, knockback, reach ------------------------------------------------------------------------------------

    /** Armour counts: a pig in 10 armour loses 4.32 to the bite of 6 (6 x (1 - 7 / 25)). */
    private static void armour(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 6.5);
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        f.setTarget(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(f.bites() == 1, "it bit once");
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 4.32F) < 0.01F, "armour took its share, the pig lost " + lost);
        });
    }

    /**
     * The bite never knocks its victim back (so it never sends anyone off an island's edge): in the tick it lands, the
     * bitten pig has no sideways motion. The control: a plain mob's blow on a second pig sends it off at 0.4.
     */
    private static void noKnockback(GameTestHelper helper) {
        Pig bitten = pig(helper, 6.9, 6.5);
        Pig control = pig(helper, 6.9, 9.5);
        Rotfang f = fang(helper, 5.5, 6.5);
        f.setTarget(bitten);
        double[] push = {-1};
        helper.onEachTick(() -> {
            if (push[0] < 0 && f.bites() == 1) push[0] = bitten.getDeltaMovement().horizontalDistance();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(push[0] >= 0, "it bit");
            helper.assertTrue(push[0] < 1.0E-4, "the bitten pig is not pushed, motion " + push[0]);
            control.hurtServer(helper.getLevel(), f.damageSources().mobAttack(f), 1.0F);
            double shove = control.getDeltaMovement().horizontalDistance();
            helper.assertTrue(shove > 0.3, "a plain blow does push, motion " + shove);
        });
    }

    /**
     * Stepping back: a survival player in reach steps 3 blocks back 5 ticks into the snarl. The jaws close on nothing:
     * the player keeps all 20 health and never rots.
     */
    private static void stepBack(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.9, 1, 6.5));
        aim(f, p);
        boolean[] stepped = {false};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (!stepped[0] && f.core().in(Step.SNARL) && f.core().elapsed() == 5) {
                stepped[0] = true;
                p.snapTo(helper.absoluteVec(new Vec3(9.9, 1, 6.5)));
            } else if (stepped[0] && !done[0] && !f.core().in(Step.SNARL)) {
                done[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "it snarled, the player stepped back and the jaws shut");
            helper.assertTrue(f.misses() == 1 && f.bites() == 0, "the jaws closed on air, misses " + f.misses());
            helper.assertTrue(p.getHealth() >= 20.0F && !rotting(p), "the player is unhurt and never rots");
        });
    }

    /**
     * Reach counts height: a player on a pillar two blocks high right beside it (0.6 across, 1.4 up) is never snarled
     * at in 60 ticks.
     */
    private static void pillar(GameTestHelper helper) {
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.STONE);
        Rotfang f = fang(helper, 5.5, 6.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.9, 3, 6.5));
        aim(f, p);
        boolean[] snarled = {false};
        helper.onEachTick(() -> snarled[0] |= !f.core().idle());
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!snarled[0] && f.bites() == 0, "it never snarled at the player on the pillar");
            helper.assertTrue(p.getHealth() >= 20.0F && !rotting(p), "who is unhurt and never rots");
            helper.succeed();
        });
    }

    /** A dead Rotfang bites no one: killed 5 ticks into its snarl at a pig in reach, the pig is unhurt and never rots. */
    private static void diesMidTell(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 6.5);
        f.setTarget(pig);
        long[] died = {-1};
        helper.onEachTick(() -> {
            if (died[0] < 0 && f.core().in(Step.SNARL) && f.core().elapsed() == 5) {
                f.hurtServer(helper.getLevel(), f.damageSources().fellOutOfWorld(), 1000.0F);
                died[0] = helper.getTick();
            }
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(died[0] >= 0 && (f.isDeadOrDying() || f.isRemoved()), "it died in the snarl");
            helper.assertTrue(f.bites() == 0 && f.misses() == 0 && f.gnaws() == 0, "no bite came after, bites " + f.bites());
            helper.assertTrue(pig.getHealth() >= 100.0F && !rotting(pig), "the pig is unhurt and never rots");
            helper.succeed();
        });
    }

    /**
     * A pack vetoing "gnaw" for this Rotfang: struck in the snarl it goes on snarling and bites; the striker is hurt and
     * rots, and nothing is drawn out.
     */
    private static void gnawVeto(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.9, 1, 6.5));
        Consumer<MechanicEvent> veto = e -> {
            if (e.step().equals("gnaw") && e.mob() == f) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        aim(f, p);
        boolean[] struck = {false};
        helper.onEachTick(() -> {
            if (!struck[0] && f.core().in(Step.SNARL) && f.core().elapsed() == 5) {
                struck[0] = true;
                f.hurtServer(helper.getLevel(), f.damageSources().playerAttack(p), 1.0F);
                helper.assertTrue(f.core().in(Step.SNARL), "vetoed, it keeps snarling, phase " + f.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0] && f.bites() == 1 && f.gnaws() == 0 && f.cleanses() == 0, "it bit and never gnawed itself");
            helper.assertTrue(rotting(p) && (p.getHealth() < 20.0F || biteOnPlayer(helper) == 0.0F), "the striker was bitten and rots");
        });
    }

    // ---- the pack ----------------------------------------------------------------------------------------------------

    /**
     * A pair takes turns: two Rotfangs either side of one pig, both in reach and both set on it. Over 70 ticks never are
     * both snarling or biting at once, and both bite.
     */
    private static void packTurns(GameTestHelper helper) {
        Pig pig = pig(helper, 6.5, 6.5);
        Rotfang a = fang(helper, 5.1, 6.5, -90.0F);
        Rotfang b = fang(helper, 7.9, 6.5, 90.0F);
        a.setTarget(pig);
        b.setTarget(pig);
        boolean[] overlap = {false};
        helper.onEachTick(() -> {
            boolean busyA = a.core().in(Step.SNARL) || a.core().in(Step.BITE);
            boolean busyB = b.core().in(Step.SNARL) || b.core().in(Step.BITE);
            overlap[0] |= busyA && busyB;
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(!overlap[0], "the two never snarled or bit at once");
            helper.assertTrue(a.bites() >= 1 && b.bites() >= 1, "both bit in turn, " + a.bites() + " and " + b.bites());
            helper.succeed();
        });
    }

    /**
     * A Rotfang with a target hands it to a packmate near by that has none and can follow it that far (follow range 8,
     * the pig 2 away), and not to one that can't (follow range 1, the pig 3.5 away).
     */
    private static void rally(GameTestHelper helper) {
        Pig pig = pig(helper, 6.5, 6.5);
        Rotfang leader = fang(helper, 4.5, 4.5);
        Rotfang near = fang(helper, 6.5, 4.5);
        Rotfang blind = fang(helper, 6.5, 10.0);
        near.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0);
        leader.setTarget(pig);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(near.getTarget() == pig, "the packmate in range took the pig as its target");
            helper.assertTrue(blind.getTarget() == null, "the one that can't follow that far did not");
            helper.succeed();
        });
    }

    // ---- saving ------------------------------------------------------------------------------------------------------

    /** After a real bite, a save and reload keeps the bite cooldown. */
    private static void saveLoad(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 6.5);
        f.setTarget(pig);
        Rotfang[] copy = {null};
        int[] cooldown = {0};
        helper.onEachTick(() -> {
            if (copy[0] != null || f.bites() == 0) return;
            ServerLevel level = helper.getLevel();
            cooldown[0] = f.biteCooldown();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            f.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            Rotfang c = ModMobs.ROTFANG.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            f.discard();
            c.setNoAi(true);
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, c::discard);
            copy[0] = c;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(copy[0] != null, "saved and reloaded after a bite");
            helper.assertTrue(cooldown[0] > 0 && Math.abs(copy[0].biteCooldown() - cooldown[0]) <= 2, "the bite cooldown carries over: "
                    + cooldown[0] + " then " + copy[0].biteCooldown());
            helper.assertTrue(copy[0].core().idle(), "a snarl under way does not");
        });
    }

    // ---- spawning, stats, home -----------------------------------------------------------------------------------------

    /**
     * Groups of 1 to 2 through both spawners: the Teeming spawner's group size for its row gives 1 or 2, both seen over
     * 200 rolls; the spawn list entry in its End barrens home comes 1 to 2 at a time.
     */
    private static void group(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("rotfang");
        Set<Integer> seen = new HashSet<>();
        RandomSource random = RandomSource.create(77L);
        for (int i = 0; i < 200; i++) seen.add(TeemingSpawner.groupSize(row, random));
        helper.assertTrue(seen.equals(Set.of(1, 2)), "groups of 1 or 2, both seen: " + seen);
        Weighted<MobSpawnSettings.SpawnerData> barrens = entry(helper, "spawns/rotfang/end_barrens");
        helper.assertTrue(barrens != null, "the End barrens' spawn list has it");
        helper.assertTrue(barrens.weight() > 0, "with a weight, got " + barrens.weight());
        helper.assertTrue(barrens.value().count().minInclusive() == 1 && barrens.value().count().maxInclusive() == 2,
                "1 to 2 at a time, got " + barrens.value().count().minInclusive() + ".." + barrens.value().count().maxInclusive());
        helper.succeed();
    }

    /** The spawn entry for the Rotfang in the biome modifier {@code path}, or null. */
    private static Weighted<MobSpawnSettings.SpawnerData> entry(GameTestHelper helper, String path) {
        var modifier = helper.getLevel().registryAccess().lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id(path)));
        if (modifier.isEmpty() || !(modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier spawns)) return null;
        return spawns.spawners().unwrap().stream().filter(w -> w.value().type() == ModMobs.ROTFANG.get()).findFirst().orElse(null);
    }

    /** Its ground rule takes a spot on end stone and refuses one over thin air. */
    private static void spawnRule(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("rotfang");
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground spawn, got " + row.place());
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.END_STONE);
        helper.assertTrue(TeemingSpawns.placeOk(row, ModMobs.ROTFANG.get(), helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3))),
                "on end stone it may spawn");
        helper.assertTrue(!TeemingSpawns.placeOk(row, ModMobs.ROTFANG.get(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))),
                "over thin air it may not");
        helper.succeed();
    }

    /** Health 26, attack 6, armour 2, a 0.8 x 0.8 hitbox. */
    private static void stats(GameTestHelper helper) {
        Rotfang f = fang(helper, 5.5, 5.5);
        f.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(f.getMaxHealth() == 26.0F && f.getHealth() == 26.0F, "health 26, got " + f.getHealth() + "/" + f.getMaxHealth());
            helper.assertTrue(f.getAttributeValue(Attributes.ATTACK_DAMAGE) == 6.0, "attack 6, got " + f.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(f.getArmorValue() == 2, "armour 2, got " + f.getArmorValue());
            helper.assertTrue(Math.abs(f.getBbWidth() - 0.8F) < 0.01F && Math.abs(f.getBbHeight() - 0.8F) < 0.01F,
                    "a 0.8 x 0.8 hitbox, got " + f.getBbWidth() + " x " + f.getBbHeight());
            helper.succeed();
        });
    }

    /**
     * Home: the End barrens only; its own spawn entry has weight 5, alone or in pairs; a monster; a 10-tick tell in its
     * row; the Blight Fang is a look; it drops void ichor.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("rotfang"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "its home holds the End barrens");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.END_MIDLANDS)
                || b.is(Biomes.SMALL_END_ISLANDS) || b.is(Biomes.THE_END)), "and nowhere else in the End");
        Weighted<MobSpawnSettings.SpawnerData> own = entry(helper, "spawns/rotfang");
        helper.assertTrue(own != null, "it has a spawn modifier that spawns it");
        helper.assertTrue(own.weight() == 5, "spawn weight 5, got " + own.weight());
        helper.assertTrue(own.value().count().minInclusive() == 1 && own.value().count().maxInclusive() == 2,
                "alone or in pairs, got " + own.value().count().minInclusive() + ".." + own.value().count().maxInclusive());
        helper.assertTrue(ModMobs.ROTFANG.get().getCategory() == MobCategory.MONSTER, "a monster");
        RosterStats.Entry row = RosterStats.get("rotfang");
        helper.assertTrue(row.tellTicks() == 10, "a 10-tick tell in its row");
        List<LookDef> looks = Looks.of("rotfang");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("blight_fang")), "the Blight Fang is a look: " + looks);
        helper.assertTrue(row.material().equals("void_ichor") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_ichor")),
                "it drops void ichor, a registered item");
        helper.succeed();
    }
}
