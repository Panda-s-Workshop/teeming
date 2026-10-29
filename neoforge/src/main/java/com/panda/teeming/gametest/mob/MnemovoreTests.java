package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Mnemovore;
import com.panda.teeming.mob.end.Mnemovore.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
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
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Mnemovore. A target within reach is gaped at for exactly 10 ticks (it holds still and nothing is hurt or taken yet),
 * then bitten for the attack of 8.5 (armour counts: a pig in 10 armour loses 6.545). A bite that lands on a survival
 * player at level 10 takes exactly one level and keeps exactly the 25 points it held (a player 40 % into level 20 drops
 * to 40 % into level 19, and 59 points are kept); a player at level 0 loses nothing but health; a vetoed "steal" takes
 * nothing. Stepping out of reach during the gape makes the bite snap on air. A bite reaches 1.25 blocks of open space
 * across or up, so a player on a pillar beside it is never gaped at. Gapes come 30 ticks apart. Killed, it spills
 * exactly the points it ate (48 eaten in two bites: 48 back), once and no more; killed mid-gape it bites no one. It
 * never despawns while it holds points, and they survive a save and reload. Stats, home and spawn rule against plain
 * values.
 */
public final class MnemovoreTests {
    private MnemovoreTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("mnemovore_tell", MnemovoreTests::tell);
        out.accept("mnemovore_counter", MnemovoreTests::counter);
        out.accept("mnemovore_failure", MnemovoreTests::failure);
        out.accept("mnemovore_points_kept", MnemovoreTests::pointsKept);
        out.accept("mnemovore_level_zero", MnemovoreTests::levelZero);
        out.accept("mnemovore_steal_veto", MnemovoreTests::stealVeto);
        out.accept("mnemovore_repay", MnemovoreTests::repay);
        out.accept("mnemovore_death_once", MnemovoreTests::deathOnce);
        out.accept("mnemovore_void_spill", MnemovoreTests::voidSpill);
        out.accept("mnemovore_no_knockback", MnemovoreTests::noKnockback);
        out.accept("mnemovore_dies_mid_tell", MnemovoreTests::diesMidTell);
        out.accept("mnemovore_pillar", MnemovoreTests::pillar);
        out.accept("mnemovore_armour", MnemovoreTests::armour);
        out.accept("mnemovore_persistence", MnemovoreTests::persistence);
        out.accept("mnemovore_save_load", MnemovoreTests::saveLoad);
        out.accept("mnemovore_spawn_rule", MnemovoreTests::spawnRule);
        out.accept("mnemovore_stats", MnemovoreTests::stats);
        out.accept("mnemovore_home", MnemovoreTests::home);
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------------

    /**
     * A Mnemovore with the base look at {@code (x, 1, z)} facing +X, no goals, a follow range of 1 (it notices no survivor
     * in another arena; {@link #aim} sets it on one in its own), removed when the test ends without spilling anything.
     */
    private static Mnemovore vore(GameTestHelper helper, double x, double z) {
        Mnemovore m = helper.spawn(ModMobs.MNEMOVORE.get(), new Vec3(x, 1, z));
        m.core().setLook("base", true);
        m.removeFreeWill();
        m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);   // test blows don't shove it off its spot
        turn(m, -90.0F);
        TestPlayers.whenDone(helper, () -> {
            m.setMemories(0, 0);                                             // test experience is not spilled as it goes
            m.discard();
        });
        return m;
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

    /** A survival player at {@code (x, y, z)} with {@code level} levels and {@code progress} of the way to the next. */
    private static ServerPlayer player(GameTestHelper helper, double x, double y, double z, int level, float progress) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(x, y, z));
        p.setExperienceLevels(level);
        p.experienceProgress = progress;
        return p;
    }

    /**
     * Sets {@code m} on the survivor {@code p} in its own arena and keeps it there. Its player-targeting goal looks at
     * least 2 blocks out whatever the follow range, and drops a target past the follow range, so with a range of 1 it
     * would pick up the survivor 1.4 away and then let go of it again at random; a range of 8 keeps it, and the nearest
     * player is always this arena's own.
     */
    private static void aim(Mnemovore m, ServerPlayer p) {
        m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0);
        m.setTarget(p);
    }

    private static void turn(LivingEntity e, float yaw) {
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.yHeadRotO = yaw;
        e.yBodyRot = yaw;
        e.yBodyRotO = yaw;
    }

    /** What one bite costs a player: the attack of 8.5, scaled by difficulty as every mob's blow on a player is. */
    private static float biteOnPlayer(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case PEACEFUL -> 0.0F;
            case EASY -> 5.25F;
            case HARD -> 12.75F;
            default -> 8.5F;
        };
    }

    /** All the experience lying in orbs in {@code box}: each orb's value times how many orbs merged into it. */
    private static int orbs(GameTestHelper helper, AABB box) {
        ServerLevel level = helper.getLevel();
        int total = 0;
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            orb.saveWithoutId(out);
            total += orb.getValue() * Math.max(1, out.buildResult().getIntOr("Count", 1));
        }
        return total;
    }

    // ---- the gape and the bite ---------------------------------------------------------------------------------------

    /**
     * The tell: a pig in reach is gaped at for exactly 10 ticks; meanwhile the Mnemovore holds still and the pig is
     * unhurt. Then the jaws shut and the pig has lost exactly the attack of 8.5; a pig has no levels, so nothing
     * is eaten.
     */
    private static void tell(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 6.5);
        m.setTarget(pig);
        int[] gape = {0};
        Vec3[] start = {null};
        float[] after = {-1};
        helper.onEachTick(() -> {
            if (m.core().in(Step.GAPE)) {
                if (start[0] == null) start[0] = m.position();
                gape[0]++;
                helper.assertTrue(m.position().distanceTo(start[0]) < 0.01, "it holds still through the gape");
                helper.assertTrue(pig.getHealth() >= 100.0F, "the gape hurts nothing yet");
            } else if (gape[0] > 0 && after[0] < 0) {
                after[0] = pig.getHealth();
                helper.assertTrue(m.core().in(Step.BITE), "the gape ends in the bite, phase " + m.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(after[0] >= 0, "it gaped and bit");
            helper.assertTrue(gape[0] == 10, "the gape lasts 10 ticks, saw " + gape[0]);
            float lost = 100.0F - after[0];
            helper.assertTrue(Math.abs(lost - 8.5F) < 0.01F, "the bite takes 8.5, the pig lost " + lost);
            helper.assertTrue(m.bites() == 1 && m.memories() == 0 && m.levelsEaten() == 0, "a pig has no levels to eat");
        });
    }

    /**
     * The counter: a survival player at level 10 in reach steps 3 blocks back halfway through the gape. The jaws snap on
     * nothing: the player keeps all 20 health and all 10 levels, and the Mnemovore holds nothing.
     */
    private static void counter(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        ServerPlayer p = player(helper, 6.9, 1, 6.5, 10, 0.0F);
        aim(m, p);
        boolean[] stepped = {false};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (!stepped[0] && m.core().in(Step.GAPE) && m.core().elapsed() == 5) {
                stepped[0] = true;
                p.snapTo(helper.absoluteVec(new Vec3(9.9, 1, 6.5)));
            } else if (stepped[0] && !done[0] && !m.core().in(Step.GAPE)) {
                done[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "it gaped, the player stepped back and the jaws shut");
            helper.assertTrue(m.misses() == 1 && m.bites() == 0, "the bite snapped on air, misses " + m.misses() + ", bites " + m.bites());
            helper.assertTrue(p.getHealth() >= 20.0F, "the player is unhurt, health " + p.getHealth());
            helper.assertTrue(p.experienceLevel == 10, "and keeps every level, level " + p.experienceLevel);
            helper.assertTrue(m.memories() == 0 && m.core().stage() == 0, "it holds nothing, its glyphs dark");
        });
    }

    /**
     * Failing the counter: a survival player at level 10, with no progress into level 11, stands in reach through the
     * gape. The bite takes the attack of 8.5 (at this difficulty) and exactly one level: the player is at level 9 with
     * no progress, and the Mnemovore holds exactly the 25 points level 9 to 10 is worth; its glyphs show one level
     * eaten, it swallows, and it can no longer despawn.
     */
    private static void failure(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        ServerPlayer p = player(helper, 6.9, 1, 6.5, 10, 0.0F);
        aim(m, p);
        helper.succeedWhen(() -> {
            helper.assertTrue(m.bites() == 1, "it bit once");
            float lost = 20.0F - p.getHealth();
            helper.assertTrue(Math.abs(lost - biteOnPlayer(helper)) < 0.01F, "the bite cost " + biteOnPlayer(helper) + ", the player lost " + lost);
            helper.assertTrue(p.experienceLevel == 9 && p.experienceProgress == 0.0F, "exactly one level gone: level " + p.experienceLevel
                    + ", progress " + p.experienceProgress);
            helper.assertTrue(m.memories() == 25 && m.levelsEaten() == 1, "it holds the 25 points that level was worth, got " + m.memories());
            helper.assertTrue(m.core().stage() == 1, "its glyphs show one level, stage " + m.core().stage());
            helper.assertTrue(m.core().in(Step.SWALLOW), "it swallows, phase " + m.phase());
            helper.assertTrue(m.requiresCustomPersistence(), "and holding it, it never despawns");
        });
    }

    /**
     * The exact points: a player 40 % of the way into level 20 is bitten down to 40 % of the way into level 19, and the
     * Mnemovore keeps the 59 points that cost (the 57 of level 19, plus 40 % of the 5 more level 20 asks).
     */
    private static void pointsKept(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        ServerPlayer p = player(helper, 6.9, 1, 6.5, 20, 0.4F);
        aim(m, p);
        helper.succeedWhen(() -> {
            helper.assertTrue(m.bites() == 1, "it bit once");
            helper.assertTrue(p.experienceLevel == 19 && Math.abs(p.experienceProgress - 0.4F) < 1.0E-6F,
                    "one level gone, the bar kept: level " + p.experienceLevel + ", progress " + p.experienceProgress);
            helper.assertTrue(m.memories() == 59 && m.levelsEaten() == 1, "it keeps 59 points, got " + m.memories());
        });
    }

    /** A player at level 0, part way to level 1, is bitten for the attack but keeps their progress: nothing is eaten. */
    private static void levelZero(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        ServerPlayer p = player(helper, 6.9, 1, 6.5, 0, 0.5F);
        aim(m, p);
        helper.succeedWhen(() -> {
            helper.assertTrue(m.bites() == 1, "it bit once");
            float lost = 20.0F - p.getHealth();
            helper.assertTrue(Math.abs(lost - biteOnPlayer(helper)) < 0.01F, "the bite still hurts, the player lost " + lost);
            helper.assertTrue(p.experienceLevel == 0 && Math.abs(p.experienceProgress - 0.5F) < 1.0E-6F,
                    "but takes nothing: level " + p.experienceLevel + ", progress " + p.experienceProgress);
            helper.assertTrue(m.memories() == 0 && m.levelsEaten() == 0 && m.core().stage() == 0, "it holds nothing");
            helper.assertTrue(m.core().in(Step.BITE), "and has nothing to swallow, phase " + m.phase());
            helper.assertTrue(!m.requiresCustomPersistence(), "so it may still despawn");
        });
    }

    /** A pack vetoing "steal" for this Mnemovore: the bite still hurts, but no level is taken. */
    private static void stealVeto(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        ServerPlayer p = player(helper, 6.9, 1, 6.5, 10, 0.0F);
        Consumer<MechanicEvent> veto = e -> {
            if (e.step().equals("steal") && e.mob() == m) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        aim(m, p);
        helper.succeedWhen(() -> {
            helper.assertTrue(m.bites() == 1, "it bit once");
            helper.assertTrue(p.getHealth() < 20.0F || biteOnPlayer(helper) == 0.0F, "the bite hurt");
            helper.assertTrue(p.experienceLevel == 10 && m.memories() == 0, "vetoed, nothing was taken: level " + p.experienceLevel);
        });
    }

    // ---- the payoff --------------------------------------------------------------------------------------------------

    /**
     * The payoff: two real bites on a player at level 10 take 25 and then 23 points (level 8 is left), the second gape
     * starting exactly 30 ticks after the first, and the glyphs show two levels. Killed, it spills 48 points of orbs:
     * exactly what it ate, no more. Against plain values too: 25 gives 25, 10 gives 10, 1 gives 1, nothing gives
     * nothing.
     */
    private static void repay(GameTestHelper helper) {
        helper.assertTrue(Mnemovore.repay(25) == 25 && Mnemovore.repay(10) == 10 && Mnemovore.repay(1) == 1 && Mnemovore.repay(0) == 0,
                "exactly what it ate: " + Mnemovore.repay(25) + ", " + Mnemovore.repay(10) + ", " + Mnemovore.repay(1));
        Mnemovore m = vore(helper, 5.5, 6.5);
        ServerPlayer p = player(helper, 6.9, 1, 6.5, 10, 0.0F);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);              // two bites leave it standing on any difficulty
        p.setHealth(40.0F);
        aim(m, p);
        long[] gapes = {-1, -1};
        boolean[] wasGape = {false};
        long[] killed = {-1};
        int[] xpBefore = {0};
        helper.onEachTick(() -> {
            boolean gaping = m.core().in(Step.GAPE);
            if (gaping && !wasGape[0]) {
                if (gapes[0] < 0) gapes[0] = helper.getTick();
                else if (gapes[1] < 0) gapes[1] = helper.getTick();
            }
            wasGape[0] = gaping;
            if (killed[0] < 0 && m.levelsEaten() == 2) {
                helper.assertTrue(m.memories() == 48, "two levels from 10: 25 and 23 points, got " + m.memories());
                helper.assertTrue(p.experienceLevel == 8, "the player is at level 8, at " + p.experienceLevel);
                helper.assertTrue(m.core().stage() == 2, "its glyphs show two levels, stage " + m.core().stage());
                p.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));           // out of the orbs' way
                xpBefore[0] = p.totalExperience;
                m.hurtServer(helper.getLevel(), m.damageSources().fellOutOfWorld(), 1000.0F);
                killed[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getTick() >= killed[0] + 3, "it ate twice and was killed");
            helper.assertTrue(gapes[1] - gapes[0] == 30, "the gapes came 30 ticks apart, " + (gapes[1] - gapes[0]));
            int back = orbs(helper, AABB.ofSize(m.position(), 5.0, 4.0, 5.0)) + (p.totalExperience - xpBefore[0]);
            helper.assertTrue(back == 48, "it spills the 48 points it ate, no more, found " + back);
            helper.assertTrue(m.memories() == 0, "and holds nothing more");
        });
    }

    /**
     * Nothing after death: holding 25 points it is killed; 25 points of orbs come out at once, its glyphs go dark, and
     * when the body is gone 30 ticks later it has spilled once only (the removal spills nothing more).
     */
    private static void deathOnce(GameTestHelper helper) {
        Mnemovore m = vore(helper, 6.5, 6.5);
        m.setNoAi(true);
        m.setMemories(25, 1);
        AABB box = AABB.ofSize(m.position(), 5.0, 4.0, 5.0);
        helper.runAtTickTime(2, () -> m.hurtServer(helper.getLevel(), m.damageSources().fellOutOfWorld(), 1000.0F));
        helper.runAtTickTime(5, () -> {
            int found = orbs(helper, box);
            helper.assertTrue(found == 25, "the death spills the 25 points it ate, found " + found);
            helper.assertTrue(m.memories() == 0 && m.core().stage() == 0 && m.spills() == 1, "it holds nothing now, its glyphs dark");
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(m.isRemoved(), "the body is gone");
            helper.assertTrue(m.spills() == 1, "and it spilled once only, " + m.spills() + " times");
            helper.succeed();
        });
    }

    /**
     * Killed in the void below the world, holding 25 points, it still pays them back: the 25 points of orbs come out on
     * the last ground it stood on, in its arena, where a player can pick them up, and none below the world's floor.
     */
    private static void voidSpill(GameTestHelper helper) {
        Mnemovore m = vore(helper, 6.5, 6.5);
        m.setMemories(25, 1);
        Vec3 ground = m.position();
        AABB box = AABB.ofSize(ground, 5.0, 4.0, 5.0);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(m.onGround(), "it stands on the arena floor first");
            m.snapTo(m.getX(), helper.getLevel().getMinY() - 70.0, m.getZ());
            m.hurtServer(helper.getLevel(), m.damageSources().fellOutOfWorld(), 1000.0F);
        });
        helper.runAtTickTime(8, () -> {
            int found = orbs(helper, box);
            helper.assertTrue(found == 25, "the 25 points come out on the ground it last stood on, found " + found);
            int below = orbs(helper, new AABB(m.getX() - 4, helper.getLevel().getMinY() - 200, m.getZ() - 4, m.getX() + 4, helper.getLevel().getMinY(), m.getZ() + 4));
            helper.assertTrue(below == 0, "none below the world's floor, found " + below);
            helper.succeed();
        });
    }

    /**
     * The bite moves no one: a pig that keeps its physics, bitten, has no sideways motion on the tick it is hurt, so a
     * player at an island's edge is never knocked over the void. Beside it a control pig hurt by the same kind of blow
     * straight through the game's own hurt is knocked back, so the check would see knockback if the bite gave any.
     */
    private static void noKnockback(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 6.5);
        pig.setNoAi(false);
        pig.removeFreeWill();
        Pig control = pig(helper, 6.9, 9.5);
        control.setNoAi(false);
        control.removeFreeWill();
        m.setTarget(pig);
        helper.runAtTickTime(3, () -> {
            control.hurtServer(helper.getLevel(), m.damageSources().mobAttack(m), 1.0F);
            helper.assertTrue(control.getDeltaMovement().horizontalDistance() > 0.1, "the control pig is knocked back, " + control.getDeltaMovement());
        });
        helper.onEachTick(() -> {
            if (pig.getHealth() < 100.0F) {
                helper.assertTrue(pig.getDeltaMovement().horizontalDistance() < 1.0E-3, "the bite gives no knockback, " + pig.getDeltaMovement());
                helper.succeed();
            }
        });
    }

    /**
     * A dead Mnemovore bites no one: killed 5 ticks into its gape at a level-10 player in reach, the player keeps every
     * health point and level, and no bite lands.
     */
    private static void diesMidTell(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        ServerPlayer p = player(helper, 6.9, 1, 6.5, 10, 0.0F);
        aim(m, p);
        long[] died = {-1};
        helper.onEachTick(() -> {
            if (died[0] < 0 && m.core().in(Step.GAPE) && m.core().elapsed() == 5) {
                m.hurtServer(helper.getLevel(), m.damageSources().fellOutOfWorld(), 1000.0F);
                died[0] = helper.getTick();
            }
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(died[0] >= 0 && (m.isDeadOrDying() || m.isRemoved()), "it died in the gape");
            helper.assertTrue(m.bites() == 0 && m.misses() == 0, "no bite came after, bites " + m.bites());
            helper.assertTrue(p.getHealth() >= 20.0F && p.experienceLevel == 10, "the player is unhurt and keeps every level");
            helper.succeed();
        });
    }

    // ---- reach and armour --------------------------------------------------------------------------------------------

    /**
     * Reach counts height: a level-10 player on a pillar two blocks high right beside it (0.6 across, 1.4 up) is never
     * gaped at in 60 ticks and keeps every level.
     */
    private static void pillar(GameTestHelper helper) {
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.STONE);
        Mnemovore m = vore(helper, 5.5, 6.5);
        ServerPlayer p = player(helper, 6.9, 3, 6.5, 10, 0.0F);
        aim(m, p);
        boolean[] gaped = {false};
        helper.onEachTick(() -> gaped[0] |= !m.core().idle());
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!gaped[0] && m.bites() == 0, "it never gaped at the player on the pillar");
            helper.assertTrue(p.getHealth() >= 20.0F && p.experienceLevel == 10, "who keeps every health point and level");
            helper.succeed();
        });
    }

    /** Armour counts: a pig in 10 armour loses 6.545 to the bite of 8.5 (8.5 x (1 - 5.75 / 25)). */
    private static void armour(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        Pig pig = pig(helper, 6.9, 6.5);
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        m.setTarget(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(m.bites() == 1, "it bit once");
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 6.545F) < 0.01F, "armour took its share, the pig lost " + lost);
        });
    }

    // ---- persistence and saving ---------------------------------------------------------------------------------------

    /**
     * Persistence while holding: empty, it may despawn; holding 25 points it never does, and a despawn check with no
     * player near leaves it be.
     */
    private static void persistence(GameTestHelper helper) {
        Mnemovore m = vore(helper, 6.5, 6.5);
        m.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!m.requiresCustomPersistence(), "empty, it may despawn");
            m.setMemories(25, 1);
            helper.assertTrue(m.requiresCustomPersistence(), "holding experience, it never despawns");
            m.checkDespawn();
            helper.assertTrue(!m.isRemoved(), "a despawn check leaves it be");
            helper.succeed();
        });
    }

    /** After a real bite (25 points from level 10), a save and reload keeps the points, the level count and the cooldown. */
    private static void saveLoad(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 6.5);
        ServerPlayer p = player(helper, 6.9, 1, 6.5, 10, 0.0F);
        aim(m, p);
        Mnemovore[] copy = {null};
        int[] cooldown = {0};
        helper.onEachTick(() -> {
            if (copy[0] != null || m.memories() == 0) return;
            ServerLevel level = helper.getLevel();
            cooldown[0] = m.biteCooldown();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            m.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            Mnemovore c = ModMobs.MNEMOVORE.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            m.setMemories(0, 0);                                             // so the original spills nothing as it goes
            m.discard();
            c.setNoAi(true);
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, () -> {
                c.setMemories(0, 0);
                c.discard();
            });
            copy[0] = c;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(copy[0] != null, "saved and reloaded after a bite");
            Mnemovore c = copy[0];
            helper.assertTrue(c.memories() == 25 && c.levelsEaten() == 1, "the 25 points and the level carry over, got " + c.memories());
            helper.assertTrue(c.core().stage() == 1, "and its glyphs show them, stage " + c.core().stage());
            helper.assertTrue(c.requiresCustomPersistence(), "it still never despawns");
            helper.assertTrue(cooldown[0] > 0 && Math.abs(c.biteCooldown() - cooldown[0]) <= 2, "the bite cooldown carries over: "
                    + cooldown[0] + " then " + c.biteCooldown());
        });
    }

    // ---- spawning, stats, home -----------------------------------------------------------------------------------------

    /** Its ground rule takes a spot on end stone and refuses one over thin air. */
    private static void spawnRule(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("mnemovore");
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground spawn, got " + row.place());
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.END_STONE);
        helper.assertTrue(TeemingSpawns.placeOk(row, ModMobs.MNEMOVORE.get(), helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3))),
                "on end stone it may spawn");
        helper.assertTrue(!TeemingSpawns.placeOk(row, ModMobs.MNEMOVORE.get(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))),
                "over thin air it may not");
        helper.succeed();
    }

    /** Health 28, attack 8.5, armour 2, a 1.0 x 0.6 hitbox. */
    private static void stats(GameTestHelper helper) {
        Mnemovore m = vore(helper, 5.5, 5.5);
        m.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.getMaxHealth() == 28.0F && m.getHealth() == 28.0F, "health 28, got " + m.getHealth() + "/" + m.getMaxHealth());
            helper.assertTrue(m.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8.5, "attack 8.5, got " + m.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(m.getArmorValue() == 2, "armour 2, got " + m.getArmorValue());
            helper.assertTrue(Math.abs(m.getBbWidth() - 1.0F) < 0.01F && Math.abs(m.getBbHeight() - 0.6F) < 0.01F,
                    "a 1.0 x 0.6 hitbox, got " + m.getBbWidth() + " x " + m.getBbHeight());
            helper.succeed();
        });
    }

    /**
     * Home: the End barrens only, spawn weight 4, alone or in pairs; a monster; a 10-tick tell in its row; the Starved
     * Mnemovore is a look; it drops void ichor.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("mnemovore"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(com.panda.teeming.gametest.TeemingGameTests.spawnBiomes(helper, "mnemovore").stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "its home holds the End barrens");
        helper.assertTrue(com.panda.teeming.gametest.TeemingGameTests.spawnBiomes(helper, "mnemovore").stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.END_MIDLANDS)
                || b.is(Biomes.SMALL_END_ISLANDS) || b.is(Biomes.THE_END)), "and nowhere else in the End");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/mnemovore")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.MNEMOVORE.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "alone or in pairs, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.MNEMOVORE.get().getCategory() == MobCategory.MONSTER, "a monster");
        RosterStats.Entry row = RosterStats.get("mnemovore");
        helper.assertTrue(row.tellTicks() == 10, "a 10-tick tell in its row");
        List<LookDef> looks = Looks.of("mnemovore");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("starved_mnemovore")), "the Starved Mnemovore is a look: " + looks);
        helper.assertTrue(row.material().equals("void_ichor") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_ichor")),
                "it drops void ichor, a registered item");
        helper.succeed();
    }
}
