package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Spirebloom;
import com.panda.teeming.mob.end.Spirebloom.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Spirebloom: at a target in range it cracks the ground for exactly 16 ticks (rooted, hurting nothing) along a line
 * fixed toward where the target stood, 19 spikes from 1 to 10 blocks out; then spikes burst up along the line and strike
 * everyone on it for its attack of 20 through armour, throwing them straight up at 0.6 with nothing across (a plain blow
 * knocks the control across), never a bystander and never a player beside the line. A line that hits nobody leaves its
 * spikes standing for exactly 60 ticks: an ownerless arrow shatters on them, a player's flies through, a monster in them
 * is pushed out across the line, a pig and a player are left alone. It cracks once every 80 ticks, not at a target 11.5
 * away, and grows no spike past a drop out of reach. A Spirebloom killed mid-tell bursts nothing. Every number is
 * checked against a plain value.
 *
 * <p>Each Spirebloom stands at one corner of the arena and sends its line along the diagonal, so the whole line lies on
 * the arena's floor; its follow range is 1 (it notices nothing past its arena), its strikes and fends are vetoed for
 * anyone outside the arena, and its AI step and phases run.
 */
public final class SpirebloomTests {
    private SpirebloomTests() {}

    /** The Spirebloom at one corner; lines run along the diagonal from it. */
    private static final Vec3 AT = new Vec3(1.5, 1, 1.5);
    private static final double ROOT_HALF = Math.sqrt(0.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("spirebloom_tell", SpirebloomTests::tell);
        out.accept("spirebloom_counter", SpirebloomTests::counter);
        out.accept("spirebloom_failure", SpirebloomTests::failure);
        out.accept("spirebloom_strike", SpirebloomTests::strike);
        out.accept("spirebloom_armour", SpirebloomTests::armour);
        out.accept("spirebloom_length_10", SpirebloomTests::length);
        out.accept("spirebloom_line_fixed", SpirebloomTests::fixed);
        out.accept("spirebloom_beside", SpirebloomTests::beside);
        out.accept("spirebloom_every_80", SpirebloomTests::every);
        TeemingGameTests.timeLimit("spirebloom_every_80", 140);
        out.accept("spirebloom_range", SpirebloomTests::range);
        out.accept("spirebloom_dies_mid_tell", SpirebloomTests::diesMidTell);
        out.accept("spirebloom_stand_60", SpirebloomTests::stand);
        TeemingGameTests.timeLimit("spirebloom_stand_60", 120);
        out.accept("spirebloom_shatter", SpirebloomTests::shatter);
        out.accept("spirebloom_fend", SpirebloomTests::fend);
        out.accept("spirebloom_no_spike_past_drop", SpirebloomTests::drop);
        out.accept("spirebloom_tell_veto", SpirebloomTests::tellVeto);
        out.accept("spirebloom_stats", SpirebloomTests::stats);
        out.accept("spirebloom_home", SpirebloomTests::home);
        out.accept("spirebloom_spawn_rule", SpirebloomTests::spawnRule);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /**
     * A Spirebloom with the base look and a follow range of 1, its strikes and fends vetoed outside the arena, removed
     * when the test ends.
     */
    private static Spirebloom bloom(GameTestHelper helper, Vec3 at) {
        Spirebloom b = helper.spawn(ModMobs.SPIREBLOOM.get(), at);
        b.core().setLook("base", true);
        b.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == b && (e.step().equals("spike") || e.step().equals("fend")) && e.target() != null
                    && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        TestPlayers.whenDone(helper, b::discard);
        return b;
    }

    private static Spirebloom bloom(GameTestHelper helper) {
        return bloom(helper, AT);
    }

    /** A pig with 100 health and no goals (so a blow could still move it), removed when the test ends. */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.removeFreeWill();
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /**
     * A spot {@code d} blocks along the diagonal from the Spirebloom's corner and {@code off} blocks to one side of it
     * (positive: toward +x, -z), on the floor; relative to the arena.
     */
    private static Vec3 along(double d, double off) {
        return new Vec3(AT.x + (d + off) * ROOT_HALF, 1, AT.z + (d - off) * ROOT_HALF);
    }

    /** How far {@code at} (absolute) lies along the diagonal from {@code from}, and to one side of it. */
    private static double distanceAlong(Vec3 from, Vec3 at) {
        return ((at.x - from.x) + (at.z - from.z)) * ROOT_HALF;
    }

    private static double offset(Vec3 from, Vec3 at) {
        return ((at.x - from.x) - (at.z - from.z)) * ROOT_HALF;
    }

    private static boolean near(double value, double expected) {
        return Math.abs(value - expected) < 0.01;
    }

    /** What the spikes do to a survival player at the test's difficulty: 20 on Normal, 11 on Easy, 30 on Hard. */
    private static float playerBlow(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case EASY -> 11.0F;
            case HARD -> 30.0F;
            default -> 20.0F;
        };
    }

    // ---- the tell and the burst ----------------------------------------------------------------------------------

    /**
     * A pig 6 blocks out starts the tell: it lasts exactly 16 ticks, the Spirebloom stands still and nothing is hurt
     * during it, the line is down from its first tick, and the tell ends in the burst.
     */
    private static void tell(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(6, 0));
        b.setTarget(pig);
        long[] start = {-1}, burst = {-1};
        helper.onEachTick(() -> {
            if (b.core().in(Step.TELL)) {
                if (start[0] < 0) start[0] = helper.getTick();
                helper.assertTrue(b.getDeltaMovement().horizontalDistance() < 0.01, "it stands still while the cracks run");
                helper.assertTrue(pig.getHealth() >= 100.0F, "the tell hurts nothing");
                helper.assertTrue(b.lineSpikes().size() == 19, "the line is down, " + b.lineSpikes().size() + " spikes");
            } else if (start[0] >= 0 && burst[0] < 0) {
                burst[0] = helper.getTick();
                helper.assertTrue(b.core().in(Step.BURST), "the tell ends in the burst, phase " + b.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && burst[0] >= 0, "a tell and then a burst");
            helper.assertTrue(burst[0] - start[0] == 16, "the tell lasts 16 ticks, lasted " + (burst[0] - start[0]));
        });
    }

    /**
     * The counter: the pig steps 2 blocks off the line 6 ticks into the tell. The spikes strike nobody, the pig is
     * unhurt, and the spikes stay standing where the line was.
     */
    private static void counter(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(6, 0));
        b.setTarget(pig);
        long[] start = {-1};
        List<Vec3> line = new ArrayList<>();
        helper.onEachTick(() -> {
            if (start[0] < 0 && b.core().in(Step.TELL)) {
                start[0] = helper.getTick();
                line.addAll(b.lineSpikes());
            }
            if (start[0] >= 0 && helper.getTick() == start[0] + 6) pig.snapTo(helper.absoluteVec(along(6, 2.0)));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && b.bursts() == 1, "the spikes burst, phase " + b.phase());
            helper.assertTrue(b.strikes() == 0 && pig.getHealth() >= 100.0F, "the pig was missed: health " + pig.getHealth());
            helper.assertTrue(b.hedges() == 1 && b.standingSpikes().equals(line), "the spikes stand where the line was: " + b.standingSpikes().size());
        });
    }

    /**
     * Failing the counter: a survival player stays on the line. The spikes hit them for its attack (20 on Normal; mob
     * damage to players scales with difficulty: 11 on Easy, 30 on Hard; the player has 40 health) as the Spirebloom's own blow, which armour
     * reduces, and throw them straight up at 0.6 with nothing across; no spikes are left standing.
     */
    private static void failure(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        ServerPlayer p = TestPlayers.survivor(helper, along(6, 0));
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);         // the blow leaves it standing on any difficulty
        p.setHealth(40.0F);
        float before = p.getHealth();
        float expected = playerBlow(helper);
        DamageSource[] last = {null};
        Vec3[] motion = {null};
        helper.runAtTickTime(1, () -> helper.assertTrue(b.crack(p), "the tell starts at the player"));
        helper.onEachTick(() -> {
            if (last[0] == null && b.strikes() == 1) {
                last[0] = p.getLastDamageSource();
                motion[0] = p.getDeltaMovement();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(b.strikes() == 1, "the spikes struck, phase " + b.phase());
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the blow does " + expected + " here, the player lost " + lost);
            helper.assertTrue(last[0] != null && last[0].is(DamageTypes.MOB_ATTACK) && last[0].getEntity() == b, "the Spirebloom's attack, got " + last[0]);
            helper.assertTrue(!last[0].is(DamageTypeTags.BYPASSES_ARMOR), "which armour reduces");
            helper.assertTrue(near(motion[0].y, 0.6) && motion[0].horizontalDistance() < 0.01, "thrown straight up at 0.6, motion " + motion[0]);
            helper.assertTrue(b.hedges() == 0 && b.standingSpikes().isEmpty(), "a line that struck leaves no spikes standing");
        });
    }

    /**
     * The blow on a pig with no armour: exactly 20, and a throw straight up at 0.6 with nothing across (a control pig given
     * the same blow plainly is knocked across), and a second pig standing on the line that isn't its target is left alone.
     * The pig and the control have no AI, so the motion read after the tick is the motion the blow set.
     */
    private static void strike(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(6, 0));
        Pig bystander = pig(helper, along(4, 0));
        Pig control = pig(helper, new Vec3(9.5, 1, 2.5));
        pig.setNoAi(true);                  // a mob without AI never travels: its motion stays as the blow left it
        control.setNoAi(true);
        b.setTarget(pig);
        Vec3[] struck = {null};
        double[] knocked = {-1.0};
        helper.onEachTick(() -> {
            if (struck[0] == null && b.strikes() == 1) {
                struck[0] = pig.getDeltaMovement();
                control.hurtServer(helper.getLevel(), b.damageSources().mobAttack(b), 20.0F);
                knocked[0] = control.getDeltaMovement().horizontalDistance();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0] != null, "the spikes struck, phase " + b.phase());
            helper.assertTrue(near(pig.getHealth(), 80.0), "the pig lost exactly 20, has " + pig.getHealth());
            helper.assertTrue(near(struck[0].y, 0.6), "thrown up at 0.6, dy " + struck[0].y);
            helper.assertTrue(struck[0].horizontalDistance() < 0.01, "and not across at all, " + struck[0].horizontalDistance());
            helper.assertTrue(knocked[0] > 0.1, "a plain blow knocks the control across, motion " + knocked[0]);
            helper.assertTrue(bystander.getHealth() >= 100.0F, "the bystander on the line is untouched, health " + bystander.getHealth());
        });
    }

    /** Armour counts: a pig with 10 armour takes 20 x (1 - max(10 - 20/2, 2) / 25) = 18.4 from the blow, vanilla's armour sum. */
    private static void armour(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(6, 0));
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        b.setTarget(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(b.strikes() == 1, "the spikes struck, phase " + b.phase());
            helper.assertTrue(near(100.0 - pig.getHealth(), 18.4), "armour 10 takes the 20 down to 18.4, lost " + (100.0F - pig.getHealth()));
        });
    }

    /**
     * The line is 10 long: 19 spikes, the first 1 block out and the last 10 blocks out from the Spirebloom's middle, half
     * a block apart, all on the bearing to the target. A player 9.7 out on the line is struck; one 10.9 out is not.
     */
    private static void length(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(5, 0));
        ServerPlayer reached = TestPlayers.survivor(helper, along(9.7, 0));
        reached.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);         // the blow leaves it standing on any difficulty
        reached.setHealth(40.0F);
        ServerPlayer past = TestPlayers.survivor(helper, along(10.9, 0));
        float r0 = reached.getHealth(), p0 = past.getHealth();
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(b.crack(pig), "the tell starts");
            List<Vec3> spikes = b.lineSpikes();
            helper.assertTrue(spikes.size() == 19, "19 spikes, got " + spikes.size());
            Vec3 from = b.position();
            helper.assertTrue(near(distanceAlong(from, spikes.getFirst()), 1.0), "the first 1 block out, " + distanceAlong(from, spikes.getFirst()));
            helper.assertTrue(near(distanceAlong(from, spikes.getLast()), 10.0), "the last 10 blocks out, " + distanceAlong(from, spikes.getLast()));
            for (int i = 0; i < spikes.size(); i++) {
                helper.assertTrue(near(distanceAlong(from, spikes.get(i)), 1.0 + i * 0.5) && Math.abs(offset(from, spikes.get(i))) < 0.01,
                        "spike " + i + " half a block on from the last, on the bearing");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(b.bursts() == 1, "the spikes burst");
            helper.assertTrue(reached.getHealth() < r0, "the player 9.7 out is struck");
            helper.assertTrue(past.getHealth() >= p0, "the player 10.9 out is not, lost " + (p0 - past.getHealth()));
        });
    }

    /**
     * The line is fixed when the tell starts: the pig walks 3 blocks off it after 4 ticks, and every tick of the tell
     * the spikes still run toward where it stood at the start (the last 10 out on that bearing). It isn't struck.
     */
    private static void fixed(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(6, 0));
        b.setTarget(pig);
        long[] start = {-1};
        List<Vec3> first = new ArrayList<>();
        int[] checked = {0};
        helper.onEachTick(() -> {
            if (b.core().in(Step.TELL)) {
                if (start[0] < 0) {
                    start[0] = helper.getTick();
                    first.addAll(b.lineSpikes());
                    Vec3 end = helper.relativeVec(first.getLast());
                    helper.assertTrue(Math.hypot(end.x - along(10, 0).x, end.z - along(10, 0).z) < 0.01, "the line ends 10 out toward the pig, at " + end);
                }
                if (helper.getTick() == start[0] + 4) pig.snapTo(helper.absoluteVec(along(6, -3.0)));
                helper.assertTrue(b.lineSpikes().equals(first), "the line hasn't moved");
                checked[0]++;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(b.bursts() == 1 && checked[0] == 16, "the whole tell watched, " + checked[0] + " ticks");
            helper.assertTrue(pig.getHealth() >= 100.0F && b.strikes() == 0, "the pig that walked off is not struck");
        });
    }

    /**
     * A player beside the line is untouched: of two survival players 4 blocks out, the one 0.3 to the side of the line is
     * struck and the one 1.3 to the side is not.
     */
    private static void beside(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(7, 0));
        ServerPlayer on = TestPlayers.survivor(helper, along(4, 0.3));
        on.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);         // the blow leaves it standing on any difficulty
        on.setHealth(40.0F);
        ServerPlayer off = TestPlayers.survivor(helper, along(4, 1.3));
        float on0 = on.getHealth(), off0 = off.getHealth();
        helper.runAtTickTime(1, () -> helper.assertTrue(b.crack(pig), "the tell starts"));
        helper.succeedWhen(() -> {
            helper.assertTrue(b.bursts() == 1, "the spikes burst");
            helper.assertTrue(on.getHealth() < on0, "0.3 to the side is on the line and struck");
            helper.assertTrue(off.getHealth() >= off0 && off.getDeltaMovement().y < 0.01, "1.3 to the side is untouched, lost " + (off0 - off.getHealth()));
        });
    }

    /** Once every 80 ticks: with the pig left in range, the second tell starts 80 ticks after the first; two blows of 20. */
    private static void every(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(6, 0));
        b.setTarget(pig);
        long[] first = {-1}, second = {-1};
        boolean[] was = {false};
        helper.onEachTick(() -> {
            boolean in = b.core().in(Step.TELL);
            if (in && !was[0]) {
                if (first[0] < 0) first[0] = helper.getTick();
                else if (second[0] < 0) second[0] = helper.getTick();
            }
            was[0] = in;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0] >= 0 && b.strikes() == 2, "two tells, two blows");
            helper.assertTrue(second[0] - first[0] == 80, "80 ticks apart, got " + (second[0] - first[0]));
            helper.assertTrue(near(pig.getHealth(), 60.0), "two blows of 20, health " + pig.getHealth());
        });
    }

    /** It cracks only at targets within 10 across: a pig 11.5 out starts nothing in 20 ticks; moved to 9.5 out, it does. */
    private static void range(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(11.5, 0));
        b.setTarget(pig);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(b.tells() == 0 && b.core().idle(), "no tell at a pig 11.5 out, phase " + b.phase());
            pig.snapTo(helper.absoluteVec(along(9.5, 0)));
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(b.tells() == 1, "a pig 9.5 out starts one, tells " + b.tells());
            helper.succeed();
        });
    }

    /** A dead Spirebloom bursts nothing: killed 8 ticks into the tell, it strikes no one, leaves no spikes standing. */
    private static void diesMidTell(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(6, 0));
        b.setTarget(pig);
        long[] first = {-1}, killed = {-1};
        helper.onEachTick(() -> {
            if (first[0] < 0 && b.core().in(Step.TELL)) first[0] = helper.getTick();
            if (first[0] >= 0 && killed[0] < 0 && helper.getTick() == first[0] + 8) {
                b.kill(helper.getLevel());
                killed[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getTick() >= killed[0] + 30, "killed mid-tell, waiting out the burst");
            helper.assertTrue(b.bursts() == 0 && b.strikes() == 0 && b.hedges() == 0, "no burst, no blow, no spikes from a dead Spirebloom");
            helper.assertTrue(pig.getHealth() >= 100.0F, "the pig loses nothing, has " + pig.getHealth());
            helper.assertTrue(b.lineSpikes().isEmpty() && b.standingSpikes().isEmpty(), "its line is gone");
        });
    }

    // ---- the spikes left standing --------------------------------------------------------------------------------

    /** Spikes left standing by a line that struck nobody stand exactly 60 ticks from the burst. */
    private static void stand(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(6, 0));
        long[] up = {-1}, down = {-1};
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(b.crack(pig), "the tell starts");
            pig.snapTo(helper.absoluteVec(along(6, 2.5)));
        });
        helper.onEachTick(() -> {
            boolean standing = !b.standingSpikes().isEmpty();
            if (standing && up[0] < 0) {
                up[0] = helper.getTick();
                helper.assertTrue(b.core().in(Step.BURST) && b.core().elapsed() == 0, "they stand from the burst's first tick");
            }
            if (!standing && up[0] >= 0 && down[0] < 0) down[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(down[0] >= 0, "the spikes sank");
            helper.assertTrue(down[0] - up[0] == 60, "after exactly 60 ticks, stood " + (down[0] - up[0]));
            helper.assertTrue(pig.getHealth() >= 100.0F, "the pig beside the line was never struck");
        });
    }

    /** An arrow with no owner flying across the standing spikes shatters on them; one a player shot flies through. */
    private static void shatter(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        ServerPlayer archer = TestPlayers.survivor(helper, along(3, -3.0));
        Arrow[] stray = {null}, own = {null};
        helper.runAtTickTime(2, () -> {
            b.standToward(helper.absoluteVec(along(10, 0)));
            helper.assertTrue(b.standingSpikes().size() == 19, "the spikes stand, " + b.standingSpikes().size());
            Vec3 across = new Vec3(ROOT_HALF, 0, -ROOT_HALF).scale(0.5);                 // 0.5 a tick, across the line
            stray[0] = arrow(helper, along(5, -2.5).add(0, 0.5, 0), across, null);
            own[0] = arrow(helper, along(7, -2.5).add(0, 0.5, 0), across, archer);
        });
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(stray[0].isRemoved() && b.shattered() == 1, "the stray arrow shattered, shattered " + b.shattered());
            helper.assertTrue(!own[0].isRemoved(), "the player's arrow is still flying");
            double side = offset(helper.absoluteVec(AT), own[0].position());
            helper.assertTrue(side > 1.0, "and has gone through to the far side, " + side);
            helper.succeed();
        });
    }

    /** An arrow at {@code at} with no gravity, flying at {@code motion}, shot by {@code owner}; removed when the test ends. */
    private static Arrow arrow(GameTestHelper helper, Vec3 at, Vec3 motion, LivingEntity owner) {
        Vec3 abs = helper.absoluteVec(at);
        Arrow arrow = new Arrow(helper.getLevel(), abs.x, abs.y, abs.z, new ItemStack(Items.ARROW), null);
        if (owner != null) arrow.setOwner(owner);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(motion);
        helper.getLevel().addFreshEntity(arrow);
        TestPlayers.whenDone(helper, arrow::discard);
        return arrow;
    }

    /**
     * Standing spikes fend off monsters: an endermite set down in them is pushed out across the line (over a block to the
     * side within 8 ticks); a pig and a survival player standing in them are left where they are.
     */
    private static void fend(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Endermite mite = helper.spawn(EntityTypes.ENDERMITE, along(5, 0.2));
        mite.removeFreeWill();
        mite.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, mite::discard);
        Pig pig = pig(helper, along(7.5, 0));
        ServerPlayer p = TestPlayers.survivor(helper, along(3, 0));
        Vec3 from = helper.absoluteVec(AT);
        Vec3 pig0 = pig.position(), p0 = p.position();
        helper.runAtTickTime(2, () -> b.standToward(helper.absoluteVec(along(10, 0))));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(b.fended() > 0, "the spikes fended the endermite off");
            helper.assertTrue(Math.abs(offset(from, mite.position())) > 1.0, "it is pushed out to the side, " + offset(from, mite.position()));
            helper.assertTrue(pig.position().distanceTo(pig0) < 0.05, "the pig is left alone, moved " + pig.position().distanceTo(pig0));
            helper.assertTrue(p.position().distanceTo(p0) < 0.05, "and so is the player");
            helper.succeed();
        });
    }

    /**
     * Spikes rise only from ground in reach: on a platform 5 high that ends 6 blocks out, the line stops at the edge (11
     * spikes, the last 6 out), and a survival player on the floor below, past the edge on the line, is untouched.
     */
    private static void drop(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.END_STONE);
            }
        }
        Spirebloom b = bloom(helper, new Vec3(AT.x, 6, AT.z));
        Pig pig = pig(helper, new Vec3(along(4, 0).x, 6, along(4, 0).z));
        ServerPlayer below = TestPlayers.survivor(helper, along(8, 0));
        float before = below.getHealth();
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(b.crack(pig), "the tell starts");
            List<Vec3> spikes = b.lineSpikes();
            helper.assertTrue(spikes.size() == 11, "the line stops at the edge, " + spikes.size() + " spikes");
            helper.assertTrue(near(distanceAlong(b.position(), spikes.getLast()), 6.0), "the last 6 out");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(b.bursts() == 1 && b.strikes() == 1, "the spikes burst and struck the pig");
            helper.assertTrue(below.getHealth() >= before, "the player below the edge is untouched");
        });
    }

    /** A pack that vetoes the tell keeps it idle: no line, no burst. */
    private static void tellVeto(GameTestHelper helper) {
        Spirebloom b = bloom(helper);
        Pig pig = pig(helper, along(6, 0));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == b && e.step().equals("tell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        b.setTarget(pig);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(b.core().idle() && b.tells() == 0 && b.lineSpikes().isEmpty(), "a cancelled tell leaves it idle, phase " + b.phase());
            helper.assertTrue(pig.getHealth() >= 100.0F, "nothing struck the pig");
            helper.succeed();
        });
    }

    // ---- the row ---------------------------------------------------------------------------------------------------

    /**
     * Health 28, attack 20, armour 6, a 0.9 x 1.5 hitbox, speed 0, 12 experience; rooted: a hit that would knock it back
     * leaves it where it is.
     */
    private static void stats(GameTestHelper helper) {
        Spirebloom b = bloom(helper, new Vec3(5.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(3.5, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(b.getMaxHealth() == 28.0F && b.getHealth() == 28.0F, "health 28, got " + b.getHealth() + "/" + b.getMaxHealth());
            helper.assertTrue(b.getAttributeValue(Attributes.ATTACK_DAMAGE) == 20.0, "attack 20, got " + b.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(b.getArmorValue() == 6, "armour 6, got " + b.getArmorValue());
            helper.assertTrue(Math.abs(b.getBbWidth() - 0.9F) < 0.01F && Math.abs(b.getBbHeight() - 1.5F) < 0.01F,
                    "a 0.9 x 1.5 hitbox, got " + b.getBbWidth() + " x " + b.getBbHeight());
            helper.assertTrue(b.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.0, "speed 0");
            helper.assertTrue(b.getExperienceReward(helper.getLevel(), null) == 12, "12 experience");
            b.hurtServer(helper.getLevel(), b.damageSources().mobAttack(pig), 2.0F);
            b.knockback(1.0, -1.0, 0.0, b.damageSources().mobAttack(pig), 2.0F, false);
        });
        helper.runAtTickTime(8, () -> {
            Vec3 now = helper.relativeVec(b.position());
            helper.assertTrue(Math.hypot(now.x - 5.5, now.z - 5.5) < 0.01, "rooted, it hasn't moved: " + now);
            helper.succeed();
        });
    }

    /** Home: the End barrens and nowhere else in the End, weight 4, alone, a monster; the Rose Spire; void crystal. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("spirebloom"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "spirebloom").stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "its home holds the End barrens");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "spirebloom").stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.END_MIDLANDS)
                || b.is(Biomes.SMALL_END_ISLANDS) || b.is(Biomes.THE_END)), "and nowhere else in the End");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/spirebloom")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SPIREBLOOM.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.SPIREBLOOM.get().getCategory() == MobCategory.MONSTER, "a monster");
        RosterStats.Entry row = RosterStats.get("spirebloom");
        helper.assertTrue(row.secondLook().equals("rose_spire"), "the Rose Spire is its second look");
        helper.assertTrue(row.material().equals("void_crystal") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_crystal")),
                "it drops void crystal, a registered item");
        helper.succeed();
    }

    /** Its spawn rule (on the ground) takes a spot on end stone and refuses one with nothing under it. */
    private static void spawnRule(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.END_STONE);
        RosterStats.Entry row = RosterStats.get("spirebloom");
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground mob, place " + row.place());
        helper.assertTrue(TeemingSpawns.placeOk(row, ModMobs.SPIREBLOOM.get(), helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))),
                "a spot on end stone takes a spawn");
        helper.assertTrue(!TeemingSpawns.placeOk(row, ModMobs.SPIREBLOOM.get(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))),
                "a spot with nothing under it does not");
        helper.succeed();
    }
}
