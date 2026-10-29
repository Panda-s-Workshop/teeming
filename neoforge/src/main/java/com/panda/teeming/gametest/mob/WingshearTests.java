package com.panda.teeming.gametest.mob;

import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.end.Wingshear;
import com.panda.teeming.mob.end.Wingshear.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wingshear: it hunts only survival players gliding on elytra within 48 blocks, at 1.25 blocks a tick. Within a gap of
 * 5 it screeches for 16 ticks, keeping its place and hurting nothing, then dives at 1.5 blocks a tick: the hit deals its
 * attack of 8.5 (armour counts), ends the glide, and every try to glide again in the next 40 ticks is put out. Outrun by
 * 8 blocks past the closest it got, it loses its glider and circles off for 100 ticks, hunting nobody; a player who
 * lands is left alone. Killed mid-screech, it dives at nothing.
 *
 * <p>Gliders are real survival players with elytra, set gliding with {@code startFallFlying}. Nothing ticks them here,
 * so they hang where they are put (a glider standing still in the air), and nothing but the Wingshear ends their glide.
 * Pigs with 100 health stand in where a number must be exact (no difficulty scaling): a target handed to it with
 * {@code setTarget} is hunted whether or not it glides. Every test keeps its Wingshears to its own quarry: a hunt that
 * pairs one of this test's mobs with another test's glider, or the other way round, is vetoed.
 */
public final class WingshearTests {
    private WingshearTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("wingshear_tell", WingshearTests::tell);
        out.accept("wingshear_failure", WingshearTests::failure);
        out.accept("wingshear_counter", WingshearTests::counter);
        TeemingGameTests.timeLimit("wingshear_counter", 200);
        out.accept("wingshear_lose_gap", WingshearTests::loseGap);
        out.accept("wingshear_walker", WingshearTests::walker);
        out.accept("wingshear_lands", WingshearTests::lands);
        out.accept("wingshear_chase_speed", WingshearTests::chaseSpeed);
        out.accept("wingshear_dive_speed", WingshearTests::diveSpeed);
        out.accept("wingshear_dive_damage", WingshearTests::diveDamage);
        out.accept("wingshear_no_knockback", WingshearTests::noKnockback);
        out.accept("wingshear_armour", WingshearTests::armour);
        out.accept("wingshear_dies_mid_tell", WingshearTests::diesMidTell);
        out.accept("wingshear_range", WingshearTests::range);
        out.accept("wingshear_dive_cancel", WingshearTests::diveCancel);
        out.accept("wingshear_pair", WingshearTests::pair);
        out.accept("wingshear_spawn_rule", WingshearTests::spawnRule);
        out.accept("wingshear_stats", WingshearTests::stats);
        out.accept("wingshear_home", WingshearTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A Wingshear at {@code at} (relative), removed when the test ends wherever it flew. */
    private static Wingshear shear(GameTestHelper helper, Vec3 at) {
        Wingshear w = helper.spawn(ModMobs.WINGSHEAR.get(), at);
        w.core().setLook(LookDef.BASE, true);
        TestPlayers.whenDone(helper, w::discard);
        return w;
    }

    /** A survival player with elytra at {@code at} (relative), gliding. */
    private static ServerPlayer glider(GameTestHelper helper, Vec3 at) {
        ServerPlayer p = winged(helper, at);
        p.startFallFlying();
        return p;
    }

    /**
     * A survival player wearing elytra at {@code at} (relative), not yet gliding. It is ticked once by hand (nothing else
     * ticks it here), as a joining player is: the tick takes the elytra into account, which the loader's glide check
     * reads from then on. Then it is put back where it was, at rest.
     */
    private static ServerPlayer winged(GameTestHelper helper, Vec3 at) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        p.doTick();
        p.snapTo(helper.absoluteVec(at));
        p.setDeltaMovement(Vec3.ZERO);
        return p;
    }

    /** A still pig with 100 health at {@code at} (relative). */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /**
     * Keeps this test's Wingshears and quarries to each other: any Wingshear step that pairs one of {@code mine} with a
     * target that isn't, or a stranger with one of {@code mine}, is vetoed.
     */
    private static void contain(GameTestHelper helper, Entity... mine) {
        Set<Entity> set = new HashSet<>(List.of(mine));
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() instanceof Wingshear && e.target() != null && set.contains(e.mob()) != set.contains(e.target())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    /** A glider 5 blocks along x from a Wingshear at the same height: a gap of 4, inside its screech reach. */
    private static final Vec3 SHEAR_AT = new Vec3(3.5, 3, 5.5);
    private static final Vec3 GLIDER_AT = new Vec3(8.5, 3, 5.5);

    // ---- the tell ------------------------------------------------------------------------------------------------

    /**
     * The screech holds for exactly 16 ticks with the leading edges lit (glow stage 1), and in that time it dives at
     * nothing: the glider keeps its health and its glide, and it keeps its place beside them.
     */
    private static void tell(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        ServerPlayer p = glider(helper, GLIDER_AT);
        contain(helper, w, p);
        float health = p.getHealth();
        long[] from = {-1}, to = {-1};
        boolean[] litAll = {true}, quiet = {true};
        double[] drift = {0};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (from[0] < 0 && w.core().in(Step.TELL)) {
                from[0] = now;
                start[0] = w.position();
            }
            if (from[0] >= 0 && to[0] < 0) {
                if (w.core().in(Step.TELL)) {
                    if (w.core().stage() != Wingshear.GLOW_SCREECH && now > from[0]) litAll[0] = false;
                    if (p.getHealth() < health || !p.isFallFlying() || w.dives() > 0) quiet[0] = false;
                    drift[0] = Math.max(drift[0], w.position().distanceTo(start[0]));
                } else {
                    to[0] = now;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(to[0] >= 0, "the screech ended");
            helper.assertTrue(to[0] - from[0] == 16, "the screech lasted 16 ticks, took " + (to[0] - from[0]));
            helper.assertTrue(litAll[0], "its leading edges were lit through the screech");
            helper.assertTrue(quiet[0], "nothing happened to the glider during the screech");
            helper.assertTrue(drift[0] < 0.2, "it kept its place beside a glider that held still, drifted " + drift[0]);
            helper.assertTrue(w.core().in(Step.DIVE) || w.core().in(Step.RECOVER), "then it dove, has " + w.core().phase().step());
        });
    }

    // ---- the failure ---------------------------------------------------------------------------------------------

    /**
     * Failing the counter: the dive lands. The glider is hurt, the glide is over at once, and every try to glide again
     * (the server's answer to a client asking, {@code tryToStartFallFlying}, each tick) is put out until exactly 40
     * ticks after the hit; the first try after that holds.
     */
    private static void failure(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        ServerPlayer p = glider(helper, GLIDER_AT);
        contain(helper, w, p);
        float health = p.getHealth();
        long[] hit = {-1}, free = {-1};
        boolean[] endedAtHit = {false};
        int[] shutAtHit = {-1}, tries = {0};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (hit[0] < 0) {
                if (p.getHealth() < health) {
                    hit[0] = now;
                    endedAtHit[0] = !p.isFallFlying();
                    shutAtHit[0] = w.shutFor(p);
                    p.tryToStartFallFlying();
                }
                return;
            }
            if (free[0] < 0) {
                if (p.isFallFlying()) free[0] = now;
                else if (p.tryToStartFallFlying()) tries[0]++;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hit[0] >= 0, "the dive hit the glider");
            helper.assertTrue(endedAtHit[0], "the hit ended the glide");
            helper.assertTrue(w.hits() == 1, "one hit, saw " + w.hits());
            helper.assertTrue(shutAtHit[0] >= 39 && shutAtHit[0] <= 40, "its wings are shut for 40 ticks, " + shutAtHit[0] + " left at the hit");
            helper.assertTrue(free[0] >= 0, "the glider glides again (" + tries[0] + " tries that started, shut for " + w.shutFor(p) + ")");
            helper.assertTrue(free[0] - hit[0] == 40, "the first try that held came 40 ticks after the hit, took " + (free[0] - hit[0]));
            helper.assertTrue(tries[0] >= 38, "every try in between started and was put out, " + tries[0] + " of 39");
        });
    }

    // ---- the counter ---------------------------------------------------------------------------------------------

    /**
     * The counter: the glider pulls away mid-screech (moved 8.7 blocks farther off than the closest the Wingshear got,
     * as a rocket's boost takes them; the boost's speed against its chase is in the numbers test). It loses them at
     * once, dives at nothing, and circles off for exactly 100 ticks, hunting nobody though the glider is still gliding in
     * range; then it hunts again.
     */
    private static void counter(GameTestHelper helper) {
        Wingshear w = shear(helper, new Vec3(2.5, 2, 2.5));
        ServerPlayer p = glider(helper, new Vec3(6.5, 2, 2.5));   // across 4, less both half-widths: a gap of 3 or less
        contain(helper, w, p);
        long[] tell = {-1}, lost = {-1}, back = {-1};
        boolean[] huntedWhileLost = {false};
        double[] closest = {-1}, gapAt = {-1};
        float health = p.getHealth();
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (tell[0] < 0 && w.core().in(Step.TELL)) tell[0] = now;
            if (tell[0] >= 0 && lost[0] < 0 && now == tell[0] + 4) {
                closest[0] = w.closest();
                Vec3 away = helper.absoluteVec(new Vec3(11.5, 6, 11.5));   // the far corner: what a rocket's boost opens up
                p.teleportTo(away.x, away.y, away.z);
                gapAt[0] = MobCore.gap(w, p);
            }
            if (lost[0] < 0 && w.core().in(Step.LOST)) lost[0] = now;
            if (lost[0] >= 0 && back[0] < 0) {
                if (w.core().in(Step.LOST)) {
                    if (!p.isFallFlying()) p.startFallFlying();
                } else if (w.core().in(Step.CHASE) || w.core().in(Step.TELL)) {
                    back[0] = now;
                } else if (!w.core().idle()) {
                    huntedWhileLost[0] = true;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] >= 0, "it lost the glider");
            helper.assertTrue(closest[0] >= 0 && closest[0] <= 5.0, "the closest it got was inside its screech reach, " + closest[0]);
            helper.assertTrue(gapAt[0] - closest[0] >= 8.0, "the glider was 8 farther than its closest, " + (gapAt[0] - closest[0]));
            helper.assertTrue(lost[0] - tell[0] == 5, "lost on the tick after the glider pulled away, " + (lost[0] - tell[0] - 4));
            helper.assertTrue(w.dives() == 0 && p.getHealth() >= health, "no dive, no harm");
            helper.assertTrue(back[0] >= 0, "after circling off it hunts again");
            helper.assertTrue(!huntedWhileLost[0], "it did nothing else in between");
            long circled = back[0] - lost[0];
            helper.assertTrue(circled >= 100 && circled <= 100 + Wingshear.SCAN_EVERY,
                    "it circled off for 100 ticks before hunting again, took " + circled);
        });
    }

    /** A glider only 6.5 blocks farther than its closest is still hunted: the gap to lose it is 8. */
    private static void loseGap(GameTestHelper helper) {
        Wingshear w = shear(helper, new Vec3(2.5, 2, 2.5));
        ServerPlayer p = glider(helper, new Vec3(6.5, 2, 2.5));
        contain(helper, w, p);
        long[] tell = {-1};
        double[] grew = {-1};
        boolean[] lost = {false};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (tell[0] < 0 && w.core().in(Step.TELL)) tell[0] = now;
            if (tell[0] >= 0 && now == tell[0] + 4) {
                // 6.5 farther than the closest: straight along the line from the Wingshear through the glider
                Vec3 dir = p.position().subtract(w.position()).multiply(1, 0, 1).normalize();
                double want = w.closest() + 6.5 + (w.getBbWidth() + p.getBbWidth()) / 2.0;
                Vec3 spot = new Vec3(w.getX(), p.getY(), w.getZ()).add(dir.scale(want));
                p.teleportTo(spot.x, spot.y, spot.z);
                grew[0] = MobCore.gap(w, p) - w.closest();
            }
            if (tell[0] >= 0 && now > tell[0] + 4 && now <= tell[0] + 6 && w.core().in(Step.LOST)) lost[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tell[0] >= 0 && helper.getLevel().getGameTime() > tell[0] + 6, "two ticks after the glider pulled away");
            helper.assertTrue(grew[0] > 6.4 && grew[0] < 6.6, "it pulled 6.5 farther than the closest, " + grew[0]);
            helper.assertTrue(!lost[0], "it still hunts them, has " + w.core().phase().step());
        });
    }

    // ---- who it hunts --------------------------------------------------------------------------------------------

    /**
     * A survival player on foot, in reach and in sight, is left alone for 60 ticks; once the same player glides, it is
     * hunted within a few ticks.
     */
    private static void walker(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        ServerPlayer p = winged(helper, new Vec3(8.5, 1, 5.5));
        contain(helper, w, p);
        float health = p.getHealth();
        boolean[] hunted = {false};
        long[] glide = {-1}, chased = {-1};
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (glide[0] < 0) {
                if (!w.core().idle()) hunted[0] = true;
                if (t >= 60) {
                    p.startFallFlying();
                    glide[0] = t;
                }
            } else if (chased[0] < 0 && !w.core().idle()) {
                chased[0] = t;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!hunted[0] && p.getHealth() >= health, "the player on foot was left alone for 60 ticks");
            helper.assertTrue(chased[0] >= 0, "the same player, gliding, is hunted");
            helper.assertTrue(chased[0] - glide[0] <= Wingshear.SCAN_EVERY + 1, "within a look round, took " + (chased[0] - glide[0]));
        });
    }

    /** A glider who lands mid-screech is left alone at once: no dive, no harm, and no hunt for the next 40 ticks. */
    private static void lands(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        ServerPlayer p = glider(helper, GLIDER_AT);
        contain(helper, w, p);
        float health = p.getHealth();
        long[] tell = {-1}, landed = {-1}, idle = {-1};
        boolean[] huntedAfter = {false};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (tell[0] < 0 && w.core().in(Step.TELL)) tell[0] = now;
            if (tell[0] >= 0 && landed[0] < 0 && now == tell[0] + 6) {
                p.stopFallFlying();
                landed[0] = now;
            }
            if (landed[0] >= 0 && now > landed[0]) {
                if (idle[0] < 0 && w.core().idle()) idle[0] = now;
                if (idle[0] >= 0 && !w.core().idle()) huntedAfter[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(landed[0] >= 0 && helper.getLevel().getGameTime() >= landed[0] + 40, "40 ticks after landing");
            helper.assertTrue(idle[0] == landed[0] + 1, "it let go on the next tick, took " + (idle[0] - landed[0]));
            helper.assertTrue(!huntedAfter[0], "and hunted nobody after");
            helper.assertTrue(w.dives() == 0 && p.getHealth() >= health, "no dive, no harm");
        });
    }

    /**
     * Its chase runs at 1.25 blocks a tick: each whole tick of the chase moves it 1.25 (faster than a glide that holds
     * its height, slower than a rocket's 1.67).
     */
    private static void chaseSpeed(GameTestHelper helper) {
        Wingshear w = shear(helper, new Vec3(2.5, 2, 2.5));
        ServerPlayer p = glider(helper, new Vec3(9.5, 4, 9.5));   // across 9.9: a gap of 8.9, past its screech reach
        contain(helper, w, p);
        List<Double> steps = new ArrayList<>();
        Vec3[] last = {null};
        helper.onEachTick(() -> {
            boolean chasing = w.core().in(Step.CHASE);
            if (chasing && last[0] != null) steps.add(w.position().distanceTo(last[0]));
            last[0] = chasing ? w.position() : null;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(w.core().in(Step.TELL) || w.core().in(Step.DIVE), "the chase brought it into reach");
            helper.assertTrue(steps.size() >= 2, "two whole chase ticks measured, " + steps);
            for (double d : steps) helper.assertTrue(Math.abs(d - 1.25) < 0.01, "each chase tick moves 1.25, saw " + steps);
        });
    }

    /** Its dive runs at 1.5 blocks a tick, still short of a rocket's 1.67. */
    private static void diveSpeed(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        ServerPlayer p = glider(helper, GLIDER_AT);
        contain(helper, w, p);
        double[] step = {-1};
        Vec3[] at = {null};
        helper.onEachTick(() -> {
            if (step[0] >= 0) return;
            if (at[0] != null) step[0] = w.position().distanceTo(at[0]);
            else if (w.core().in(Step.DIVE)) at[0] = w.position();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(step[0] >= 0, "it dove");
            helper.assertTrue(Math.abs(step[0] - 1.5) < 0.01, "a dive tick moves 1.5, saw " + step[0]);
        });
    }

    /** The dive deals its attack, 8.5, to an unarmoured pig handed to it as a target, and then it pulls up. */
    private static void diveDamage(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        Pig pig = pig(helper, new Vec3(8.5, 1, 5.5));
        contain(helper, w, pig);
        w.setTarget(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(w.hits() == 1, "one hit, saw " + w.hits());
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 8.5F) < 0.01F, "the dive takes 8.5, the pig lost " + lost);
            helper.assertTrue(w.core().in(Step.RECOVER), "it pulls up after the hit, has " + w.core().phase().step());
        });
    }

    /**
     * The dive moves no one sideways: a pig that keeps its physics, struck, has no sideways motion on the tick it is hurt,
     * so a player it is set on while standing at an edge (a Vigil Owl's call hands it any marked player) is never knocked
     * over the void. A control pig hurt by the same kind of blow straight through the game's own hurt is knocked back.
     */
    private static void noKnockback(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        Pig pig = pig(helper, new Vec3(8.5, 1, 5.5));
        pig.setNoAi(false);
        pig.removeFreeWill();
        Pig control = pig(helper, new Vec3(3.5, 1, 9.5));
        control.setNoAi(false);
        control.removeFreeWill();
        contain(helper, w, pig);
        w.setTarget(pig);
        helper.runAtTickTime(3, () -> {
            control.hurtServer(helper.getLevel(), w.damageSources().mobAttack(w), 1.0F);
            helper.assertTrue(control.getDeltaMovement().horizontalDistance() > 0.3, "the control pig is knocked back, " + control.getDeltaMovement());
        });
        helper.onEachTick(() -> {
            if (pig.getHealth() < 100.0F) {
                // the diving body brushes the pig as it passes (entity push, up to about a tenth, by how far it overlaps);
                // a blow's knockback is 0.4, as the control shows
                helper.assertTrue(pig.getDeltaMovement().horizontalDistance() < 0.2, "the dive gives no knockback, " + pig.getDeltaMovement());
                helper.succeed();
            }
        });
    }

    /**
     * Armour counts against the dive: a pig with 10 armour takes 8.5 x (1 - 5.75/25) = 6.545 (vanilla's formula: the
     * armour less half the damage, 5.75 here, at least a fifth of the armour, each point 4 %).
     */
    private static void armour(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        Pig pig = pig(helper, new Vec3(8.5, 1, 5.5));
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        contain(helper, w, pig);
        helper.runAtTickTime(2, () -> w.setTarget(pig));          // armour counts from the wearer's next tick
        helper.succeedWhen(() -> {
            helper.assertTrue(w.hits() == 1, "one hit, saw " + w.hits());
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 6.545F) < 0.01F, "armour 10 turns the 8.5 into 6.545, the pig lost " + lost);
        });
    }

    /** Killed 8 ticks into its screech, it dives at nothing: the glider keeps health and glide through the death. */
    private static void diesMidTell(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        ServerPlayer p = glider(helper, GLIDER_AT);
        contain(helper, w, p);
        float health = p.getHealth();
        long[] first = {-1}, killed = {-1};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (first[0] < 0 && w.core().in(Step.TELL)) first[0] = now;
            if (first[0] >= 0 && killed[0] < 0 && now == first[0] + 8) {
                w.kill(helper.getLevel());
                killed[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getLevel().getGameTime() >= killed[0] + 30, "killed mid-screech, waiting out the dive");
            helper.assertTrue(w.dives() == 0 && w.hits() == 0, "no dive from a dead Wingshear");
            helper.assertTrue(p.getHealth() >= health && p.isFallFlying(), "the glider keeps health and glide");
        });
    }

    /**
     * Its reach: a glider 47.5 blocks off (straight up, through the opened roof) is hunted, one 48.5 off is not; nor is a
     * player on foot, nor a creative glider.
     */
    private static void range(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        Wingshear w = shear(helper, new Vec3(5.5, 2, 5.5));
        w.setNoAi(true);
        ServerPlayer p = glider(helper, new Vec3(5.5, 2 + 47.5, 5.5));
        contain(helper, w, p);
        helper.runAtTickTime(2, () -> {
            for (int y = 3; y <= 50; y++) {
                BlockPos pos = new BlockPos(5, y, 5);
                if (!helper.getBlockState(pos).isAir()) helper.fail("the sky over the arena is shut at y " + y + " by " + helper.getBlockState(pos));
            }
            helper.assertTrue(near(w.distanceTo(p), 47.5), "47.5 off, " + w.distanceTo(p));
            helper.assertTrue(w.huntable(p), "a glider 47.5 off is hunted");
            Vec3 far = helper.absoluteVec(new Vec3(5.5, 2 + 48.5, 5.5));
            p.teleportTo(far.x, far.y, far.z);
            helper.assertTrue(near(w.distanceTo(p), 48.5), "48.5 off, " + w.distanceTo(p));
            helper.assertTrue(!w.huntable(p), "a glider 48.5 off is not");
            Vec3 close = helper.absoluteVec(new Vec3(5.5, 6, 5.5));
            p.teleportTo(close.x, close.y, close.z);
            helper.assertTrue(w.huntable(p), "a glider 4 off is hunted");
            p.stopFallFlying();
            helper.assertTrue(!w.huntable(p), "the same player not gliding is not");
            p.startFallFlying();
            p.setGameMode(GameType.CREATIVE);
            helper.assertTrue(!w.huntable(p), "a creative glider is not");
            helper.succeed();
        });
    }

    /** A pack that vetoes "dive_hit" spares the glider: the dive passes through, no harm, no shut wings. */
    private static void diveCancel(GameTestHelper helper) {
        Wingshear w = shear(helper, SHEAR_AT);
        ServerPlayer p = glider(helper, GLIDER_AT);
        contain(helper, w, p);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == w && e.step().equals("dive_hit")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        float health = p.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(w.dives() == 1 && w.core().in(Step.RECOVER), "it dove and pulled up, has " + w.core().phase().step());
            helper.assertTrue(w.hits() == 0 && p.getHealth() >= health && p.isFallFlying() && w.shutFor(p) == 0,
                    "a vetoed hit does nothing to the glider");
        });
    }

    // ---- spawning ------------------------------------------------------------------------------------------------

    /**
     * Groups of 1 to 2: the spawner's group size for its row gives 1 or 2, both seen over 200 rolls; the members of a
     * natural group fly in one look, the first's, handed on in the spawn data whatever the second rolled.
     */
    private static void pair(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("wingshear");
        Set<Integer> seen = new HashSet<>();
        RandomSource random = RandomSource.create(87L);
        for (int i = 0; i < 200; i++) seen.add(TeemingSpawner.groupSize(row, random));
        helper.assertTrue(seen.equals(Set.of(1, 2)), "groups of 1 or 2, both seen: " + seen);
        var level = helper.getLevel();
        var difficulty = level.getCurrentDifficultyAt(helper.absolutePos(new BlockPos(5, 3, 5)));
        for (int i = 0; i < 6; i++) {
            Wingshear first = shear(helper, new Vec3(2.5 + i, 3, 4.5));
            Wingshear second = shear(helper, new Vec3(2.5 + i, 3, 7.5));
            var data = first.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, null);
            second.core().setLook(first.look().equals(LookDef.BASE) ? "dusk_shear" : LookDef.BASE, true);
            second.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, data);
            helper.assertTrue(second.look().equals(first.look()), "the second took the first's look " + first.look() + ", got " + second.look());
        }
        helper.succeed();
    }

    /**
     * Its spawner rule (sky): open air with air above and below under the open sky is a spot; the floor is not, nor is
     * air under a roof (checked once the sky light under it has settled).
     */
    private static void spawnRule(GameTestHelper helper) {
        TeemingSpawner.Rule rule = TeemingSpawner.Rule.of(RosterStats.get("wingshear").spawner());
        helper.assertTrue(rule == TeemingSpawner.Rule.SKY, "its rule is the sky's, got " + rule);
        helper.assertTrue(rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(3, 4, 3))), "open air under the sky is a spot");
        helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))), "a spot on the floor is not");
        helper.setBlock(new BlockPos(8, 6, 8), Blocks.END_STONE);
        helper.succeedWhen(() -> helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))),
                "air under an island block is not"));
    }

    // ---- the row -------------------------------------------------------------------------------------------------

    /** Health 30, attack 8.5, armour 2, a 1.4 x 0.6 hitbox; it hunts as far as 48 (its follow range too). */
    private static void stats(GameTestHelper helper) {
        Wingshear w = shear(helper, new Vec3(5.5, 3, 5.5));
        w.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(near(w.getMaxHealth(), 30.0) && near(w.getHealth(), 30.0), "health 30, got " + w.getHealth() + "/" + w.getMaxHealth());
            helper.assertTrue(near(w.getAttributeValue(Attributes.ATTACK_DAMAGE), 8.5), "attack 8.5");
            helper.assertTrue(near(w.getAttributeValue(Attributes.ARMOR), 2.0), "armour 2");
            helper.assertTrue(Math.abs(w.getBbWidth() - 1.4F) < 0.02F && Math.abs(w.getBbHeight() - 0.6F) < 0.02F,
                    "hitbox 1.4 x 0.6, got " + w.getBbWidth() + " x " + w.getBbHeight());
            helper.assertTrue(near(w.getAttributeValue(Attributes.FOLLOW_RANGE), 48.0), "follow range 48");
            helper.assertTrue(w.isNoGravity(), "it flies");
            helper.succeed();
        });
    }

    /**
     * Home: the open sky over the outer End, only through the Teeming spawner's sky rule (which refuses the main island),
     * on no biome list, its biome tag the four outer End biomes and any modded End biome; a monster of the End; weight 0, groups of 1-2. The Dusk Shear is its
     * second look; it drops void membrane.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("wingshear");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.WINGSHEAR.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.assertTrue(row.spawner().equals("sky") && TeemingSpawner.Rule.SKY.dimension.equals(Level.END), "the End's sky rule places it");
        helper.assertTrue(TeemingSpawner.onMainIsland(new BlockPos(0, 100, 0)) && !TeemingSpawner.onMainIsland(new BlockPos(1100, 100, 0)),
                "the sky rule's main-island test: the origin is the main island, 1100 out is the outer islands");
        TeemingGameTests.assertOuterEndTag(helper, "wingshear");
        helper.assertTrue(row.weight() == 0, "weight 0, got " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 2, "groups of 1-2, got " + row.groupMin() + ".." + row.groupMax());
        List<LookDef> looks = Looks.of("wingshear");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("dusk_shear")), "the Dusk Shear is a look: " + looks);
        helper.assertTrue(row.material().equals("void_membrane") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_membrane")),
                "it drops void membrane, a registered item");
        helper.succeed();
    }
}
