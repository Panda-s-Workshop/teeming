package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.end.Phasewalker;
import com.panda.teeming.mob.end.Phasewalker.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Phasewalker: its cycle is 60 ticks solid, a 10-tick shimmer (still solid, still hurt, nothing else yet), 60 ticks
 * phased: no damage of any kind lands (an arrow flies through it), and it floats at 0.15 blocks a tick straight through
 * a stone wall to its target, which it never hurts while phased. When the phased spell runs out inside a block it stays
 * phased and floats to open ground, and is never solid inside a block, not even after a reload. Solid, it strikes: an
 * 8-tick wind-up, then 9 to a target still in reach. The counter: 10 health lost in one solid spell cracks its glass
 * (11 from a player is 10.824 through armour 2), and a cracked walker leaks 12 health over a phased spell, never below
 * 1. Failing it: it walks through the wall a survival player hides behind and strikes. Health 32, attack 9, armour 2,
 * 0.7 x 2.0, End midlands at weight 4, alone. Every number is checked against a plain value.
 */
public final class PhasewalkerTests {
    private PhasewalkerTests() {}

    /** The middle of the arena: everything that moves here stays at least 3 blocks from the edge. */
    private static final Vec3 AT = new Vec3(6.5, 1, 6.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("phasewalker_tell", PhasewalkerTests::tell);
        out.accept("phasewalker_counter", PhasewalkerTests::counter);
        out.accept("phasewalker_failure", PhasewalkerTests::failure);
        TeemingGameTests.timeLimit("phasewalker_failure", 180);
        out.accept("phasewalker_cycle", PhasewalkerTests::cycle);
        TeemingGameTests.timeLimit("phasewalker_cycle", 200);
        out.accept("phasewalker_immune", PhasewalkerTests::immune);
        out.accept("phasewalker_arrow_through", PhasewalkerTests::arrowThrough);
        out.accept("phasewalker_through_wall", PhasewalkerTests::throughWall);
        TeemingGameTests.timeLimit("phasewalker_through_wall", 120);
        out.accept("phasewalker_never_solid_in_block", PhasewalkerTests::neverSolidInBlock);
        out.accept("phasewalker_crack_10", PhasewalkerTests::crackThreshold);
        out.accept("phasewalker_crack_one_spell", PhasewalkerTests::crackOneSpell);
        TeemingGameTests.timeLimit("phasewalker_crack_one_spell", 140);
        out.accept("phasewalker_bleed_12", PhasewalkerTests::bleed);
        out.accept("phasewalker_bleed_floor", PhasewalkerTests::bleedFloor);
        out.accept("phasewalker_windup", PhasewalkerTests::windup);
        out.accept("phasewalker_strike_reach", PhasewalkerTests::strikeReach);
        out.accept("phasewalker_phase_cancel", PhasewalkerTests::phaseCancel);
        out.accept("phasewalker_save_load", PhasewalkerTests::saveLoad);
        out.accept("phasewalker_armour_2", PhasewalkerTests::armour);
        out.accept("phasewalker_stats", PhasewalkerTests::stats);
        out.accept("phasewalker_home", PhasewalkerTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------------

    /** A walker at {@code at} with the base look, no goals and a follow range of 1 (it notices nothing past its own arena). */
    private static Phasewalker walker(GameTestHelper helper, Vec3 at) {
        Phasewalker w = helper.spawn(ModMobs.PHASEWALKER.get(), at);
        w.core().setLook("base", true);
        w.removeFreeWill();
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, w::discard);
        return w;
    }

    /** A still pig with 100 health, removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A stone wall across the arena at x {@code x0..x1}, 3 high, from z 2 to z 10. */
    private static void wall(GameTestHelper helper, int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = 1; y <= 3; y++) for (int z = 2; z <= 10; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
    }

    /** True when any block's collision shape overlaps its hitbox. */
    private static boolean inBlock(GameTestHelper helper, Phasewalker w) {
        return !helper.getLevel().noBlockCollision(w, w.getBoundingBox().deflate(1.0E-4));
    }

    /** What its blow of 9 costs a player: scaled by difficulty, as every mob's blow on a player is. */
    private static float blowOnPlayer(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case EASY -> 5.5F;
            case HARD -> 13.5F;
            default -> 9.0F;
        };
    }

    // ---- the tell, the counter, the failure ----------------------------------------------------------------------------

    /**
     * The shimmer holds exactly 10 ticks, and while it lasts nothing has happened yet: it is still solid (not phased, it
     * can be picked, a blow of 2 takes 2), it doesn't move, and the pig beside it is untouched. Then it is phased.
     */
    private static void tell(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        Pig pig = pig(helper, 6.5, 1, 7.7);
        int[] ticks = {0};
        boolean[] started = {false}, after = {false};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            if (!started[0] && helper.getTick() >= 2) {
                helper.assertTrue(w.shimmer(), "the shimmer starts");
                started[0] = true;
                start[0] = w.position();
            }
            if (!started[0] || after[0]) return;
            if (w.core().in(Step.SHIMMER)) {
                ticks[0]++;
                helper.assertTrue(!w.phased() && w.isPickable(), "still solid while it shimmers");
                helper.assertTrue(w.position().distanceTo(start[0]) < 0.01, "it stands still");
                helper.assertTrue(pig.getHealth() >= 100.0F, "the shimmer hurts nothing");
                if (ticks[0] == 5) {
                    float before = w.getHealth();
                    w.hurtServer(helper.getLevel(), w.damageSources().generic(), 2.0F);
                    helper.assertTrue(Math.abs(before - w.getHealth() - 2.0F) < 0.01F, "a blow still lands while it shimmers");
                }
            } else {
                after[0] = true;
                helper.assertTrue(w.core().in(Step.PHASED) && w.phased(), "the shimmer ends phased, has " + w.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(after[0], "the shimmer ended");
            helper.assertTrue(ticks[0] == 10, "the shimmer lasts 10 ticks, saw " + ticks[0]);
        });
    }

    /**
     * The counter: a survival player strikes it for 11 while it is solid. Through armour 2 it loses 10.824, which cracks
     * its glass (10 or more in one solid spell).
     */
    private static void counter(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(6.5, 1, 3.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!w.phased() && !w.cracked(), "solid and whole");
            w.hurtServer(helper.getLevel(), w.damageSources().playerAttack(player), 11.0F);
            float lost = 32.0F - w.getHealth();
            helper.assertTrue(Math.abs(lost - 10.824F) < 0.01F, "the blow lands for 10.824, lost " + lost);
            helper.assertTrue(w.cracked(), "and cracks its glass");
            helper.succeed();
        });
    }

    /**
     * Failing it: a survival player hides behind a stone wall. The walker fades out, floats through the wall without
     * hurting it, turns solid beside it in open ground and strikes once for its 9 (scaled by difficulty).
     */
    private static void failure(GameTestHelper helper) {
        wall(helper, 5, 6);
        Phasewalker w = walker(helper, new Vec3(3.5, 1, 6.5));
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(9.5, 1, 6.5));
        w.setTarget(player);
        float before = player.getHealth();
        boolean[] started = {false}, phasedOnce = {false};
        // what it saw each time that changed, so a timeout says why no blow landed
        StringBuilder seen = new StringBuilder();
        String[] last = {""};
        helper.onEachTick(() -> {
            if (!started[0] && helper.getTick() >= 2) {
                helper.assertTrue(w.shimmer(), "it shimmers");
                started[0] = true;
            }
            String now = String.format(Locale.ROOT, "%s gap %.1f x %.1f y %.1f target %s sight %s hurt %d health %.1f", w.phase().step(),
                    w.gapTo(player), helper.relativeVec(w.position()).x, helper.relativeVec(w.position()).y, w.getTarget() == player,
                    w.hasLineOfSight(player), player.hurtTime, player.getHealth());
            if (!now.equals(last[0])) {
                last[0] = now;
                seen.append(" | tick ").append(helper.getTick()).append(": ").append(now);
            }
            if (w.phased()) {
                phasedOnce[0] = true;
                helper.assertTrue(player.getHealth() >= before, "nothing reaches the player while it is phased");
            }
            if (!w.phased()) helper.assertTrue(!inBlock(helper, w), "never solid inside the wall");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(phasedOnce[0] && w.strikes() == 1, "it came through and struck, strikes " + w.strikes() + ", phase " + w.phase() + "; seen" + seen);
            helper.assertTrue(helper.relativeVec(w.position()).x > 7.0, "it stands on the player's side of the wall");
            float lost = before - player.getHealth();
            helper.assertTrue(Math.abs(lost - blowOnPlayer(helper)) < 0.01F, "the blow costs " + blowOnPlayer(helper) + ", lost " + lost);
        });
    }

    // ---- the cycle ------------------------------------------------------------------------------------------------------

    /** One whole turn of the cycle, untouched: the shimmer 10 ticks, phased 60, then solid 60 until the next shimmer. */
    private static void cycle(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        int[] shimmer = {0}, phased = {0}, solid = {0};
        int[] stage = {0};
        helper.onEachTick(() -> {
            if (stage[0] == 0 && helper.getTick() >= 2) {
                helper.assertTrue(w.shimmer(), "the shimmer starts");
                stage[0] = 1;
            }
            Phase p = w.phase();
            switch (stage[0]) {
                case 1 -> {
                    if (p == Step.SHIMMER) shimmer[0]++;
                    else if (p == Step.PHASED) {
                        stage[0] = 2;
                        phased[0]++;
                    } else helper.fail("the shimmer ended in " + p.step());
                }
                case 2 -> {
                    if (p == Step.PHASED) phased[0]++;
                    else if (p == Step.IDLE) {
                        stage[0] = 3;
                        solid[0]++;
                    } else helper.fail("the phased spell ended in " + p.step());
                }
                case 3 -> {
                    if (p == Step.IDLE) solid[0]++;
                    else if (p == Step.SHIMMER) stage[0] = 4;
                    else helper.fail("the solid spell ended in " + p.step());
                }
                default -> { }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] == 4, "a whole cycle ran");
            helper.assertTrue(shimmer[0] == 10, "the shimmer lasts 10 ticks, saw " + shimmer[0]);
            helper.assertTrue(phased[0] == 60, "phased for 60 ticks, saw " + phased[0]);
            helper.assertTrue(solid[0] == 60, "solid for 60 ticks, saw " + solid[0]);
        });
    }

    /** Phased, nothing hurts it: a blow of 1000, a mob's bite of 10, magic, fire. It can't be picked. */
    private static void immune(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        Pig pig = pig(helper, 3.5, 1, 3.5);
        helper.runAtTickTime(2, () -> w.startPhase(Step.PHASED, 60, null));
        helper.runAtTickTime(5, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(w.phased(), "phased");
            helper.assertTrue(!w.hurtServer(level, w.damageSources().generic(), 1000.0F), "a blow of 1000 doesn't land");
            helper.assertTrue(!w.hurtServer(level, w.damageSources().mobAttack(pig), 10.0F), "a bite doesn't land");
            helper.assertTrue(!w.hurtServer(level, w.damageSources().magic(), 10.0F), "magic doesn't land");
            helper.assertTrue(!w.hurtServer(level, w.damageSources().inFire(), 10.0F), "fire doesn't land");
            helper.assertTrue(w.getHealth() == 32.0F, "health 32 still, got " + w.getHealth());
            helper.assertTrue(!w.isPickable() && !w.canBeHitByProjectile(), "it can't be picked or hit by a projectile");
            helper.succeed();
        });
    }

    /**
     * An arrow flies straight through a phased walker and on past it; the same arrow on a solid walker beside it hits.
     * Both walkers are held still (no AI) so the arrows meet them where they stand.
     */
    private static void arrowThrough(GameTestHelper helper) {
        Phasewalker ghost = walker(helper, new Vec3(6.5, 1, 4.5));
        Phasewalker solid = walker(helper, new Vec3(6.5, 1, 8.5));
        ghost.setNoAi(true);
        solid.setNoAi(true);
        ServerLevel level = helper.getLevel();
        Arrow[] arrows = new Arrow[2];
        boolean[] passed = {false};
        helper.runAtTickTime(2, () -> {
            ghost.startPhase(Step.PHASED, 60, null);
            for (int i = 0; i < 2; i++) {
                Vec3 from = helper.absoluteVec(new Vec3(3.5, 2.0, i == 0 ? 4.5 : 8.5));
                Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
                arrow.setNoGravity(true);
                arrow.setDeltaMovement(0.9, 0.0, 0.0);
                level.addFreshEntity(arrow);
                TestPlayers.whenDone(helper, arrow::discard);
                arrows[i] = arrow;
            }
        });
        helper.onEachTick(() -> {
            if (arrows[0] != null && !arrows[0].isRemoved() && arrows[0].getX() > ghost.getX() + 1.0) passed[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(passed[0], "the arrow flew on past the phased walker");
            helper.assertTrue(ghost.getHealth() == 32.0F, "the phased walker is unhurt, " + ghost.getHealth());
            helper.assertTrue(solid.getHealth() < 32.0F, "the solid walker is hit, " + solid.getHealth());
        });
    }

    /**
     * Phased, it floats through a stone wall 2 thick to the pig past it, 0.15 blocks a tick, inside the wall for a while
     * and never hurt by it; the pig is never hurt while it is phased. When the spell runs out it is solid on the far side.
     */
    private static void throughWall(GameTestHelper helper) {
        wall(helper, 5, 6);
        Phasewalker w = walker(helper, new Vec3(3.5, 1, 6.5));
        Pig pig = pig(helper, 9.5, 1, 6.5);
        w.setTarget(pig);
        boolean[] inside = {false}, solidAgain = {false};
        double[] last = {Double.NaN};
        double[] lo = {Double.MAX_VALUE}, hi = {0.0};
        helper.runAtTickTime(2, () -> w.startPhase(Step.PHASED, 60, pig));
        helper.onEachTick(() -> {
            if (w.phased()) {
                if (inBlock(helper, w)) inside[0] = true;
                helper.assertTrue(w.getHealth() == 32.0F, "the wall never hurts it");
                helper.assertTrue(pig.getHealth() >= 100.0F, "it hurts nothing while phased");
                double x = helper.relativeVec(w.position()).x;
                // measure the float across the middle of the run, away from where it starts and where it stops
                if (!Double.isNaN(last[0]) && x > 4.0 && x < 7.5) {
                    double step = x - last[0];
                    lo[0] = Math.min(lo[0], step);
                    hi[0] = Math.max(hi[0], step);
                }
                last[0] = x;
            } else if (inside[0]) {
                solidAgain[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(inside[0], "it was inside the wall");
            helper.assertTrue(solidAgain[0] && !w.phased(), "solid again");
            helper.assertTrue(helper.relativeVec(w.position()).x > 7.0 && !inBlock(helper, w), "on the far side, in open ground");
            helper.assertTrue(Math.abs(lo[0] - 0.15) < 0.005 && Math.abs(hi[0] - 0.15) < 0.005, "0.15 blocks a tick, saw " + lo[0] + ".." + hi[0]);
        });
    }

    /**
     * Its phased spell runs out while it stands inside a block of stone 3 wide: it stays phased (lingering) and floats out
     * to open ground, and on no tick is it solid with a block in its hitbox; it never takes suffocation damage. It ends
     * solid, on the floor beside the stone.
     */
    private static void neverSolidInBlock(GameTestHelper helper) {
        for (int x = 5; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) for (int z = 5; z <= 7; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        Phasewalker w = walker(helper, AT);
        w.startPhase(Step.PHASED, 5, null);                                // phased before its first tick inside the stone
        boolean[] lingered = {false};
        helper.onEachTick(() -> {
            if (w.core().in(Step.LINGER)) lingered[0] = true;
            if (!w.phased()) helper.assertTrue(!inBlock(helper, w), "never solid inside a block");
            helper.assertTrue(w.getHealth() == 32.0F, "never hurt by the stone, " + w.getHealth());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lingered[0], "it lingered phased inside the stone");
            helper.assertTrue(!w.phased() && w.core().idle(), "solid again, " + w.phase());
            helper.assertTrue(!inBlock(helper, w), "in open ground");
            helper.assertTrue(Math.abs(helper.relativeVec(w.position()).y - 1.0) < 0.05, "on the floor, y " + helper.relativeVec(w.position()).y);
        });
    }

    // ---- the crack and the leak -----------------------------------------------------------------------------------------

    /** 9.9 lost in one solid spell leaves the glass whole; 10 cracks it. */
    private static void crackThreshold(GameTestHelper helper) {
        Phasewalker whole = walker(helper, new Vec3(4.5, 1, 6.5));
        Phasewalker broken = walker(helper, new Vec3(8.5, 1, 6.5));
        helper.runAtTickTime(2, () -> {
            whole.hurtServer(helper.getLevel(), whole.damageSources().generic(), 9.9F);
            broken.hurtServer(helper.getLevel(), broken.damageSources().generic(), 10.0F);
            helper.assertTrue(!whole.cracked(), "9.9 leaves it whole");
            helper.assertTrue(broken.cracked(), "10 cracks it");
            helper.succeed();
        });
    }

    /**
     * The damage must come in one solid spell: two blows of 6 in one spell crack it; a blow of 6, a phased spell, then a
     * blow of 6 in the next spell leave it whole.
     */
    private static void crackOneSpell(GameTestHelper helper) {
        Phasewalker together = walker(helper, new Vec3(4.5, 1, 6.5));
        Phasewalker apart = walker(helper, new Vec3(8.5, 1, 6.5));
        boolean[] second = {false};
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            together.hurtServer(level, together.damageSources().generic(), 6.0F);
            apart.hurtServer(level, apart.damageSources().generic(), 6.0F);
            apart.startPhase(Step.PHASED, 20, null);
        });
        // the second blows come once the hurt cooldown of the first is over
        helper.runAtTickTime(14, () -> {
            together.hurtServer(helper.getLevel(), together.damageSources().generic(), 6.0F);
            helper.assertTrue(Math.abs(together.getHealth() - 20.0F) < 0.01F, "both blows landed, health " + together.getHealth());
            helper.assertTrue(together.cracked(), "two blows of 6 in one spell crack it");
        });
        helper.onEachTick(() -> {
            if (!second[0] && helper.getTick() > 14 && !apart.phased()) {
                apart.hurtServer(helper.getLevel(), apart.damageSources().generic(), 6.0F);
                second[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0], "the second blow landed after the phased spell");
            helper.assertTrue(Math.abs(apart.getHealth() - 20.0F) < 0.01F, "both blows landed, health " + apart.getHealth());
            helper.assertTrue(!apart.cracked(), "6 and 6 in two spells leave it whole");
        });
    }

    /** A cracked walker loses exactly 12 health over a 60-tick phased spell, 1 every 5 ticks. */
    private static void bleed(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        w.setCracked(true);
        boolean[] started = {false}, ended = {false};
        float[] at = {0.0F};
        helper.onEachTick(() -> {
            if (!started[0] && helper.getTick() >= 2) {
                at[0] = w.getHealth();
                w.startPhase(Step.PHASED, 60, null);
                started[0] = true;
            } else if (started[0] && !ended[0] && !w.phased()) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the phased spell ended");
            float lost = at[0] - w.getHealth();
            helper.assertTrue(Math.abs(lost - 12.0F) < 0.01F && w.bleeds() == 12, "leaked 12, lost " + lost + " in " + w.bleeds());
        });
    }

    /** The leak never kills: a cracked walker at 4 health phased for 60 ticks ends at 1, alive. */
    private static void bleedFloor(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        w.setCracked(true);
        helper.runAtTickTime(2, () -> {
            w.setHealth(4.0F);
            w.startPhase(Step.PHASED, 60, null);
        });
        helper.onEachTick(() -> {
            if (!w.isRemoved()) helper.assertTrue(w.getHealth() >= 1.0F && w.isAlive(), "never below 1, " + w.getHealth());
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(!w.phased(), "solid again");
            helper.assertTrue(w.getHealth() == 1.0F && w.isAlive(), "at 1 and alive, " + w.getHealth());
            helper.succeed();
        });
    }

    // ---- the blow -------------------------------------------------------------------------------------------------------

    /** Its blow: an 8-tick wind-up, held still and harmless, then 9 damage to the pig in reach (armour 0 takes all 9). */
    private static void windup(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        Pig pig = pig(helper, 6.5, 1, 7.7);
        w.setTarget(pig);
        Vec3 start = w.position();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (!w.core().in(Step.WINDUP)) return;
            ticks[0]++;
            helper.assertTrue(pig.getHealth() >= 100.0F, "the wind-up hurts nothing");
            helper.assertTrue(Math.hypot(w.getX() - start.x, w.getZ() - start.z) < 0.05, "it holds still while it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(w.strikes() == 1, "the blow landed, strikes " + w.strikes() + ", phase " + w.phase());
            helper.assertTrue(ticks[0] == 8, "the wind-up lasts 8 ticks, saw " + ticks[0]);
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 9.0F) < 0.01F, "the blow takes 9, the pig lost " + lost);
        });
    }

    /** Stepping back out of reach during the wind-up: the blow hits nothing. */
    private static void strikeReach(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        Pig pig = pig(helper, 6.5, 1, 7.7);
        w.setTarget(pig);
        boolean[] moved = {false}, struck = {false};
        helper.onEachTick(() -> {
            if (w.core().in(Step.WINDUP) && !moved[0]) {
                pig.snapTo(pig.getX(), pig.getY(), pig.getZ() + 2.0);
                moved[0] = true;
            }
            if (w.core().in(Step.STRIKE)) struck[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0], "it swung");
            helper.assertTrue(w.strikes() == 0 && pig.getHealth() >= 100.0F, "at the air: the pig has " + pig.getHealth());
        });
    }

    // ---- vetoes, saving -------------------------------------------------------------------------------------------------

    /** A vetoed fade: after the shimmer it stays solid (a blow lands) and starts a new solid spell. */
    private static void phaseCancel(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == w && e.step().equals("phased")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> helper.assertTrue(w.shimmer(), "the shimmer starts"));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(!w.phased() && w.core().idle(), "it stayed solid, " + w.phase());
            helper.assertTrue(w.solidTicks() < 15, "a new solid spell started, " + w.solidTicks());
            helper.assertTrue(w.hurtServer(helper.getLevel(), w.damageSources().generic(), 2.0F), "a blow lands");
            helper.succeed();
        });
    }

    /**
     * Saved while phased inside stone, it comes back lingering (phased), not solid, and isn't hurt by the stone. Saved
     * solid and cracked 20 ticks into its spell, it comes back cracked with its spell at 20.
     */
    private static void saveLoad(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 3; y++) for (int z = 5; z <= 7; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        Phasewalker buried = walker(helper, new Vec3(3.5, 1, 6.5));
        buried.startPhase(Step.PHASED, 60, null);
        buried.setNoAi(true);
        Phasewalker open = walker(helper, new Vec3(8.5, 1, 6.5));
        open.setNoAi(true);
        open.setCracked(true);
        Phasewalker[] copies = new Phasewalker[2];
        helper.runAtTickTime(3, () -> {
            ServerLevel level = helper.getLevel();
            // the open one 20 ticks into its spell: a NoAI walker's clock stands still, so set it through a save
            List<Phasewalker> from = List.of(buried, open);
            for (int i = 0; i < 2; i++) {
                TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                from.get(i).saveWithoutId(out);
                CompoundTag tag = out.buildResult();
                tag.remove("UUID");
                if (i == 1) tag.putInt("SolidTicks", 20);
                Phasewalker c = ModMobs.PHASEWALKER.get().create(level, EntitySpawnReason.LOAD);
                c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                from.get(i).discard();
                c.setNoAi(true);
                level.addFreshEntity(c);
                TestPlayers.whenDone(helper, c::discard);
                copies[i] = c;
            }
        });
        helper.runAtTickTime(8, () -> {
            Phasewalker b = copies[0], o = copies[1];
            helper.assertTrue(b.phased() && b.core().in(Step.LINGER), "the buried copy came back lingering, " + b.phase());
            helper.assertTrue(b.getHealth() == 32.0F, "unhurt by the stone, " + b.getHealth());
            helper.assertTrue(o.cracked() && o.solidTicks() == 20 && !o.phased(), "the open copy is cracked and 20 ticks into its spell, " + o.solidTicks());
            helper.succeed();
        });
    }

    // ---- numbers ------------------------------------------------------------------------------------------------------

    /** Armour 2: a mob's blow of 10 costs it 9.84. */
    private static void armour(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        Pig pig = pig(helper, 3.5, 1, 3.5);
        helper.runAtTickTime(2, () -> {
            w.hurtServer(helper.getLevel(), w.damageSources().mobAttack(pig), 10.0F);
            float lost = 32.0F - w.getHealth();
            helper.assertTrue(Math.abs(lost - 9.84F) < 0.01F, "a blow of 10 costs 9.84 through armour 2, lost " + lost);
            helper.succeed();
        });
    }

    /** Health 32, attack 9, armour 2, a 0.7 x 2.0 hitbox. */
    private static void stats(GameTestHelper helper) {
        Phasewalker w = walker(helper, AT);
        w.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(w.getMaxHealth() == 32.0F && w.getHealth() == 32.0F, "health 32, got " + w.getHealth() + "/" + w.getMaxHealth());
            helper.assertTrue(w.getAttributeValue(Attributes.ATTACK_DAMAGE) == 9.0, "attack 9, got " + w.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(w.getArmorValue() == 2, "armour 2, got " + w.getArmorValue());
            helper.assertTrue(Math.abs(w.getBbWidth() - 0.7F) < 0.01F && Math.abs(w.getBbHeight() - 2.0F) < 0.01F,
                    "a 0.7 x 2.0 hitbox, got " + w.getBbWidth() + " x " + w.getBbHeight());
            AABB box = w.getBoundingBox();
            helper.assertTrue(Math.abs(box.getYsize() - 2.0) < 0.01, "the box stands 2 tall");
            helper.succeed();
        });
    }

    /** Home: the End midlands only; spawn weight 4, alone; a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("phasewalker"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "its home holds the End midlands");
        for (var other : List.of(Biomes.END_HIGHLANDS, Biomes.END_BARRENS, Biomes.SMALL_END_ISLANDS, Biomes.THE_END, Biomes.PLAINS)) {
            helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(other)), "never " + other.identifier());
        }
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/phasewalker")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(e -> e.value().type() == ModMobs.PHASEWALKER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.PHASEWALKER.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
