package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.AnchorGolem;
import com.panda.teeming.mob.end.AnchorGolem.Step;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Anchor Golem: with its target within 1.5 blocks it heaves its anchor for exactly 14 ticks (still, hurting nothing)
 * while a ring of radius 1.25 lies at the target's feet; the anchor then strikes everyone in the ring for its attack of
 * 9 through armour, without knockback and never a bystander, and makes them Heavy for exactly 120 ticks (shown, with
 * particles and an icon): no jump, Levitation and Slow Falling cancelled and refused, and the Slow Falling given back
 * when the weight lifts (at once if the golem dies). A target that steps out of the ring is missed, and the anchor leaves
 * a sink of radius 1.5 for exactly 160 ticks; an Anchor Golem in a sink can't start a tell and moves at half speed, and
 * the sink leaves pigs and players alone. A target on a pillar 4 high beside it is out of reach. It swings at most
 * once every 56 ticks. A golem killed mid-tell lands nothing. Every number is checked against a plain value.
 *
 * <p>Each golem has no goals and a follow range of 1 (it notices nothing past its arena); its AI step and phases run.
 */
public final class AnchorGolemTests {
    private AnchorGolemTests() {}

    /** The golem at one side of the arena, the pig 1.7 blocks east of it: a gap of 0.65, well in reach. */
    private static final Vec3 AT = new Vec3(4.5, 1, 5.5);
    private static final Vec3 PIG = new Vec3(6.2, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("anchor_golem_tell", AnchorGolemTests::tell);
        out.accept("anchor_golem_counter", AnchorGolemTests::counter);
        out.accept("anchor_golem_failure", AnchorGolemTests::failure);
        out.accept("anchor_golem_strike", AnchorGolemTests::strike);
        out.accept("anchor_golem_armour", AnchorGolemTests::armour);
        out.accept("anchor_golem_heavy_120", AnchorGolemTests::heavyTime);
        TeemingGameTests.timeLimit("anchor_golem_heavy_120", 160);
        out.accept("anchor_golem_heavy_lift", AnchorGolemTests::heavyLift);
        out.accept("anchor_golem_gives_back", AnchorGolemTests::givesBack);
        out.accept("anchor_golem_lifts_on_death", AnchorGolemTests::liftsOnDeath);
        out.accept("anchor_golem_ring_radius", AnchorGolemTests::ringRadius);
        out.accept("anchor_golem_sink_time", AnchorGolemTests::sinkTime);
        TeemingGameTests.timeLimit("anchor_golem_sink_time", 200);
        out.accept("anchor_golem_mired", AnchorGolemTests::mired);
        out.accept("anchor_golem_pillar", AnchorGolemTests::pillar);
        out.accept("anchor_golem_every", AnchorGolemTests::every);
        TeemingGameTests.timeLimit("anchor_golem_every", 140);
        out.accept("anchor_golem_dies_mid_tell", AnchorGolemTests::diesMidTell);
        out.accept("anchor_golem_tell_veto", AnchorGolemTests::tellVeto);
        out.accept("anchor_golem_stats", AnchorGolemTests::stats);
        out.accept("anchor_golem_home", AnchorGolemTests::home);
        out.accept("anchor_golem_spawn_rule", AnchorGolemTests::spawnRule);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A golem with the base look, no goals and a follow range of 1, ready to swing, removed when the test ends. */
    private static AnchorGolem golem(GameTestHelper helper, Vec3 at) {
        AnchorGolem g = helper.spawn(ModMobs.ANCHOR_GOLEM.get(), at);
        g.core().setLook("base", true);
        g.removeFreeWill();
        g.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        g.resetSwing();
        TestPlayers.whenDone(helper, g::discard);
        return g;
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

    private static boolean near(double value, double expected) {
        return Math.abs(value - expected) < 0.01;
    }

    private static boolean heavy(net.minecraft.world.entity.LivingEntity e) {
        return e.hasEffect(ModEffects.HEAVY.holder());
    }

    // ---- the tell and the blow -----------------------------------------------------------------------------------

    /**
     * A pig in reach starts the tell: it lasts exactly 14 ticks, the golem stands still and nothing is hurt during it,
     * the ring lies at the pig's feet, and the tell ends in the swing.
     */
    private static void tell(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        Pig pig = pig(helper, PIG);
        g.setTarget(pig);
        long[] start = {-1}, swing = {-1};
        double[] ringOff = {-1.0};
        helper.onEachTick(() -> {
            if (g.core().in(Step.TELL)) {
                if (start[0] < 0) {
                    start[0] = helper.getTick();
                    Vec3 c = g.ringCentre();
                    ringOff[0] = c == null ? 99.0 : Math.hypot(c.x - pig.getX(), c.z - pig.getZ());
                }
                helper.assertTrue(g.getDeltaMovement().horizontalDistance() < 0.01, "it stands still while it heaves");
                helper.assertTrue(pig.getHealth() >= 100.0F && !heavy(pig), "the tell hurts nothing");
            } else if (start[0] >= 0 && swing[0] < 0) {
                swing[0] = helper.getTick();
                helper.assertTrue(g.core().in(Step.SWING), "the tell ends in the swing, phase " + g.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && swing[0] >= 0, "a tell and then a swing");
            helper.assertTrue(swing[0] - start[0] == 14, "the tell lasts 14 ticks, lasted " + (swing[0] - start[0]));
            helper.assertTrue(ringOff[0] < 0.01, "the ring lies at the pig's feet, " + ringOff[0] + " off");
        });
    }

    /**
     * The counter: the pig steps 2.3 blocks off the ring's centre 6 ticks into the tell. The anchor strikes nobody, the
     * pig is unhurt and not Heavy, and a sink opens where the ring was.
     */
    private static void counter(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        Pig pig = pig(helper, PIG);
        g.setTarget(pig);
        long[] start = {-1};
        Vec3[] ring = {null};
        helper.onEachTick(() -> {
            if (start[0] < 0 && g.core().in(Step.TELL)) {
                start[0] = helper.getTick();
                ring[0] = g.ringCentre();
            }
            if (start[0] >= 0 && helper.getTick() == start[0] + 6) pig.snapTo(helper.absoluteVec(new Vec3(8.5, 1, 5.5)));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && g.core().in(Step.SWING), "the anchor came down, phase " + g.phase());
            helper.assertTrue(g.strikes() == 0 && pig.getHealth() >= 100.0F && !heavy(pig), "the pig was missed: health " + pig.getHealth());
            Vec3 sink = g.sinkCentre();
            helper.assertTrue(g.sinksLeft() == 1 && sink != null, "a sink opened");
            helper.assertTrue(ring[0] != null && Math.hypot(sink.x - ring[0].x, sink.z - ring[0].z) < 0.01, "where the ring was");
        });
    }

    /**
     * Failing the counter: a survival player stays in the ring. The anchor hits them for its attack (9 on Normal; mob
     * damage to players scales with difficulty: 5.5 on Easy, 13.5 on Hard) as the golem's own blow, which armour
     * reduces, and they are Heavy; no sink opens.
     */
    private static void failure(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.0, 1, 5.5));
        g.setTarget(p);
        float before = p.getHealth();
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 5.5F;
            case HARD -> 13.5F;
            default -> 9.0F;
        };
        DamageSource[] last = {null};
        helper.onEachTick(() -> {
            if (last[0] == null && g.strikes() == 1) last[0] = p.getLastDamageSource();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(g.strikes() == 1, "the anchor struck, phase " + g.phase());
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the blow does " + expected + " here, the player lost " + lost);
            helper.assertTrue(last[0] != null && last[0].is(DamageTypes.MOB_ATTACK) && last[0].getEntity() == g, "the golem's attack, got " + last[0]);
            helper.assertTrue(!last[0].is(DamageTypeTags.BYPASSES_ARMOR), "which armour reduces");
            helper.assertTrue(heavy(p), "the player is Heavy");
            helper.assertTrue(g.sinksLeft() == 0 && g.sinkCentre() == null, "a blow that landed leaves no sink");
        });
    }

    /**
     * The blow on a pig with no armour: exactly 9, with no knockback (the pig keeps no motion; a control pig given the
     * same blow plainly is knocked back), and a second pig standing in the ring that isn't its target is left alone.
     */
    private static void strike(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        Pig pig = pig(helper, PIG);
        Pig bystander = pig(helper, new Vec3(6.3, 1, 6.4));
        Pig control = pig(helper, new Vec3(8.5, 1, 1.5));
        g.setTarget(pig);
        double[] struck = {-1.0}, knocked = {-1.0};
        helper.onEachTick(() -> {
            if (struck[0] < 0 && g.strikes() == 1) {
                struck[0] = pig.getDeltaMovement().horizontalDistance();
                control.hurtServer(helper.getLevel(), g.damageSources().mobAttack(g), 9.0F);
                knocked[0] = control.getDeltaMovement().horizontalDistance();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0] >= 0.0, "the anchor struck, phase " + g.phase());
            helper.assertTrue(near(pig.getHealth(), 91.0), "the pig lost exactly 9, has " + pig.getHealth());
            helper.assertTrue(struck[0] < 0.01, "and was not moved, motion " + struck[0]);
            helper.assertTrue(knocked[0] > 0.1, "a plain blow knocks the control back, motion " + knocked[0]);
            helper.assertTrue(bystander.getHealth() >= 100.0F && !heavy(bystander), "the bystander in the ring is untouched, health " + bystander.getHealth());
        });
    }

    /**
     * Armour counts: a pig with 10 armour takes 9 x (1 - (10 - 9/2) / 25) = 7.02 from the blow, the vanilla armour sum
     * for a blow of 9.
     */
    private static void armour(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        Pig pig = pig(helper, PIG);
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        g.setTarget(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(g.strikes() == 1, "the anchor struck, phase " + g.phase());
            helper.assertTrue(near(100.0 - pig.getHealth(), 7.02), "armour 10 takes the 9 down to 7.02, lost " + (100.0F - pig.getHealth()));
        });
    }

    // ---- Heavy ---------------------------------------------------------------------------------------------------

    /**
     * Heavy lasts exactly 120 ticks: given by the golem's own weight (as its blow gives it) at the end of a tick, it reads
     * 120, shows particles and an icon, and is gone 120 ticks later; the golem's blow itself leaves the pig Heavy.
     */
    private static void heavyTime(GameTestHelper helper) {
        AnchorGolem g = golem(helper, new Vec3(2.5, 1, 2.5));
        g.setNoAi(true);
        Pig pig = pig(helper, new Vec3(8.5, 1, 8.5));
        long[] given = {-1}, gone = {-1};
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(g.weigh(helper.getLevel(), pig), "the weight takes hold");
            MobEffectInstance on = pig.getEffect(ModEffects.HEAVY.holder());
            helper.assertTrue(on != null && on.getDuration() == 120, "Heavy for 120 ticks, got " + (on == null ? "none" : on.getDuration()));
            helper.assertTrue(on.isVisible() && on.showIcon(), "with its particles and its icon");
            given[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (given[0] >= 0 && gone[0] < 0 && !heavy(pig)) gone[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gone[0] >= 0, "the weight lifted");
            helper.assertTrue(gone[0] - given[0] == 120, "after exactly 120 ticks, lasted " + (gone[0] - given[0]));
        });
    }

    /**
     * Heavy cancels Levitation and Slow Falling and stops a jump: a floating pig loses both and refuses them while it
     * lasts; a Heavy pig's and a Heavy player's jump give no lift, where a plain pig jumps 0.42.
     */
    private static void heavyLift(GameTestHelper helper) {
        AnchorGolem g = golem(helper, new Vec3(2.5, 1, 2.5));
        g.setNoAi(true);
        Pig pig = pig(helper, new Vec3(5.5, 1, 5.5));
        Pig plain = pig(helper, new Vec3(8.5, 1, 5.5));
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 8.5));
        pig.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 0));
        pig.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(g.weigh(helper.getLevel(), pig) && g.weigh(helper.getLevel(), p), "both are weighed down");
            helper.assertTrue(!pig.hasEffect(MobEffects.LEVITATION) && !pig.hasEffect(MobEffects.SLOW_FALLING), "Levitation and Slow Falling are gone");
            boolean lev = pig.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 0));
            boolean slow = pig.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
            helper.assertTrue(!lev && !slow, "and refused while it lasts");
            for (net.minecraft.world.entity.LivingEntity e : java.util.List.of(pig, plain, p)) {
                e.setOnGround(true);
                e.setDeltaMovement(Vec3.ZERO);
                e.jumpFromGround();
            }
            helper.assertTrue(pig.getDeltaMovement().y <= 0.0, "a Heavy pig's jump gives no lift, dy " + pig.getDeltaMovement().y);
            helper.assertTrue(p.getDeltaMovement().y <= 0.0, "nor a Heavy player's, dy " + p.getDeltaMovement().y);
            helper.assertTrue(near(plain.getDeltaMovement().y, 0.42), "a plain pig jumps 0.42, dy " + plain.getDeltaMovement().y);
            helper.succeed();
        });
    }

    /**
     * Nothing is lost: Slow Falling (300 ticks) cancelled by the weight is held, and comes back with its 300 ticks once
     * the weight lifts; Levitation, a harm, does not come back.
     */
    private static void givesBack(GameTestHelper helper) {
        AnchorGolem g = golem(helper, new Vec3(2.5, 1, 2.5));
        Pig pig = pig(helper, new Vec3(8.5, 1, 8.5));
        pig.setNoAi(true);
        helper.runAtTickTime(5, () -> {
            pig.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
            pig.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 300, 0));
            helper.assertTrue(g.weigh(helper.getLevel(), pig), "the weight takes hold");
            helper.assertTrue(!pig.hasEffect(MobEffects.SLOW_FALLING) && g.holds(pig), "its Slow Falling is held");
            pig.removeEffect(ModEffects.HEAVY.holder());                 // the weight lifts
        });
        helper.runAtTickTime(8, () -> {
            MobEffectInstance back = pig.getEffect(MobEffects.SLOW_FALLING);
            helper.assertTrue(back != null && back.getDuration() >= 290 && back.getDuration() <= 300,
                    "Slow Falling is back with its time, got " + (back == null ? "none" : back.getDuration()));
            helper.assertTrue(!pig.hasEffect(MobEffects.LEVITATION), "Levitation stays gone");
            helper.assertTrue(!g.holds(pig) && g.lifts() == 1, "and the golem holds nothing more");
            helper.succeed();
        });
    }

    /** Killing the golem lifts its weight at once: the Heavy pig is free the tick it dies, its Slow Falling back. */
    private static void liftsOnDeath(GameTestHelper helper) {
        AnchorGolem g = golem(helper, new Vec3(2.5, 1, 2.5));
        Pig pig = pig(helper, new Vec3(8.5, 1, 8.5));
        pig.setNoAi(true);
        helper.runAtTickTime(5, () -> {
            pig.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
            helper.assertTrue(g.weigh(helper.getLevel(), pig) && heavy(pig), "Heavy");
        });
        helper.runAtTickTime(10, () -> {
            g.kill(helper.getLevel());
            helper.assertTrue(!heavy(pig), "the weight lifts the tick it dies");
            helper.assertTrue(pig.hasEffect(MobEffects.SLOW_FALLING), "and Slow Falling is back");
            helper.succeed();
        });
    }

    /** The ring's radius is 1.25: a pig 1.15 off its centre at the blow is struck, one 1.45 off is not. */
    private static void ringRadius(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        Pig pig = pig(helper, PIG);
        Pig inside = pig(helper, new Vec3(PIG.x, 1, PIG.z - 1.15));
        Pig outside = pig(helper, new Vec3(PIG.x, 1, PIG.z + 1.45));
        ServerPlayer near = TestPlayers.survivor(helper, new Vec3(PIG.x + 1.15, 1, PIG.z));
        ServerPlayer far = TestPlayers.survivor(helper, new Vec3(PIG.x + 1.45, 1, PIG.z));
        g.setTarget(pig);
        g.windUp(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(g.core().in(Step.SWING), "the anchor came down, phase " + g.phase());
            helper.assertTrue(heavy(near) && !heavy(far), "the player 1.15 off is struck, the one 1.45 off is not");
            helper.assertTrue(inside.getHealth() >= 100.0F && outside.getHealth() >= 100.0F, "pigs that aren't its target are never struck");
            helper.assertTrue(near(pig.getHealth(), 91.0), "its target is, health " + pig.getHealth());
        });
    }

    /** A sink lasts exactly 160 ticks. */
    private static void sinkTime(GameTestHelper helper) {
        AnchorGolem g = golem(helper, new Vec3(2.5, 1, 2.5));
        long[] opened = {-1}, closed = {-1};
        helper.runAtTickTime(5, () -> {
            g.openSink(helper.absoluteVec(new Vec3(8.5, 1, 8.5)));
            helper.assertTrue(g.sinkCentre() != null, "the sink is open");
            opened[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (opened[0] >= 0 && closed[0] < 0 && g.sinkCentre() == null) closed[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(closed[0] >= 0, "the sink closed");
            helper.assertTrue(closed[0] - opened[0] == 160, "after exactly 160 ticks, lasted " + (closed[0] - opened[0]));
        });
    }

    /**
     * The sink holds an Anchor Golem's anchor: one standing 1.4 off the centre of its own sink can't start a tell at a
     * pig in reach and moves at 0.09 (half of 0.18); another golem's sink holds it the same; one 1.65 off is free. A pig
     * and a player in the sink are untouched. Out of it, the golem is back to 0.18 and swings.
     */
    private static void mired(GameTestHelper helper) {
        AnchorGolem g = golem(helper, new Vec3(3.5, 1, 3.5));
        AnchorGolem other = golem(helper, new Vec3(3.5, 1, 8.5));
        AnchorGolem free = golem(helper, new Vec3(8.5, 1, 3.5));
        Pig pig = pig(helper, new Vec3(4.4, 1, 5.6));                       // in the sink, and in reach of g
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(3.5, 1, 6.2));    // in the sink, out of every golem's notice
        helper.runAtTickTime(2, () -> {
            g.openSink(helper.absoluteVec(new Vec3(3.5, 1, 4.9)));           // 1.4 from g
            other.openSink(helper.absoluteVec(new Vec3(3.5, 1, 7.1)));       // 1.4 from other, opened by another golem
            free.openSink(helper.absoluteVec(new Vec3(8.5, 1, 5.15)));      // 1.65 from free
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(g.mired() && g.core().flag(AnchorGolem.FLAG_MIRED), "a golem in its sink is held");
            helper.assertTrue(other.mired(), "another golem's sink holds it the same");
            helper.assertTrue(!free.mired(), "1.65 off the centre is out of the sink");
            helper.assertTrue(g.core().idle() && !g.windUp(pig), "held, it can't start a tell");
            helper.assertTrue(near(g.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.09), "and moves at 0.09, got " + g.getAttributeValue(Attributes.MOVEMENT_SPEED));
            helper.assertTrue(near(free.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.18), "a free golem at 0.18");
            helper.assertTrue(!heavy(pig) && !heavy(p) && pig.getHealth() >= 100.0F, "the sink leaves the pig and the player alone");
            g.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 1.5)));              // out: 3.4 from the sink
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(!g.mired() && !g.core().flag(AnchorGolem.FLAG_MIRED), "out of the sink it is free");
            helper.assertTrue(near(g.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.18), "back to 0.18, got " + g.getAttributeValue(Attributes.MOVEMENT_SPEED));
            helper.assertTrue(g.windUp(pig), "and swings again");
            helper.succeed();
        });
    }

    /**
     * Reach counts height: a pig on a pillar 4 high right beside it (0.95 across, 1.8 up from the top of its head) is out
     * of reach and starts no tell; a pig on the ground beside it does.
     */
    private static void pillar(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(6, y, 5), Blocks.STONE);
        AnchorGolem g = golem(helper, AT);
        Pig high = pig(helper, new Vec3(6.5, 5, 5.5));
        g.setTarget(high);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(g.tells() == 0 && g.core().idle(), "no tell at a pig 4 blocks up, phase " + g.phase());
            Pig low = pig(helper, new Vec3(4.5, 1, 7.2));
            g.setTarget(low);
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(g.tells() == 1, "a pig on the ground beside it starts one, tells " + g.tells());
            helper.succeed();
        });
    }

    /** At most once every 56 ticks: with the pig left in reach, the second tell starts 56 ticks after the first. */
    private static void every(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        Pig pig = pig(helper, PIG);
        g.setTarget(pig);
        long[] first = {-1}, second = {-1};
        boolean[] was = {false};
        helper.onEachTick(() -> {
            boolean in = g.core().in(Step.TELL);
            if (in && !was[0]) {
                if (first[0] < 0) first[0] = helper.getTick();
                else if (second[0] < 0) second[0] = helper.getTick();
            }
            was[0] = in;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0] >= 0, "two tells");
            helper.assertTrue(second[0] - first[0] == 56, "56 ticks apart, got " + (second[0] - first[0]));
            helper.assertTrue(near(pig.getHealth(), 82.0), "two blows of 9, health " + pig.getHealth());
        });
    }

    /** A dead golem lands nothing: killed 8 ticks into the tell, it strikes no one, leaves no sink and its ring fades. */
    private static void diesMidTell(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        Pig pig = pig(helper, PIG);
        g.setTarget(pig);
        long[] first = {-1}, killed = {-1};
        helper.onEachTick(() -> {
            if (first[0] < 0 && g.core().in(Step.TELL)) first[0] = helper.getTick();
            if (first[0] >= 0 && killed[0] < 0 && helper.getTick() == first[0] + 8) {
                g.kill(helper.getLevel());
                killed[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0] >= 0 && helper.getTick() >= killed[0] + 30, "killed mid-tell, waiting out the blow");
            helper.assertTrue(g.strikes() == 0 && g.sinksLeft() == 0, "no blow and no sink from a dead golem");
            helper.assertTrue(pig.getHealth() >= 100.0F && !heavy(pig), "the pig loses nothing, has " + pig.getHealth());
            helper.assertTrue(g.ringCentre() == null, "its ring is gone");
        });
    }

    /** A pack that vetoes the tell keeps it idle: no ring, no blow. */
    private static void tellVeto(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        Pig pig = pig(helper, PIG);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == g && e.step().equals("tell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        g.setTarget(pig);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(g.core().idle() && g.tells() == 0 && g.ringCentre() == null, "a cancelled tell leaves it idle, phase " + g.phase());
            helper.assertTrue(pig.getHealth() >= 100.0F, "nothing struck the pig");
            helper.succeed();
        });
    }

    // ---- the row ---------------------------------------------------------------------------------------------------

    /** Health 44, attack 9, armour 8, a 1.2 x 2.2 hitbox, speed 0.18, knockback resistance 0.7. */
    private static void stats(GameTestHelper helper) {
        AnchorGolem g = golem(helper, AT);
        g.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(g.getMaxHealth() == 44.0F && g.getHealth() == 44.0F, "health 44, got " + g.getHealth() + "/" + g.getMaxHealth());
            helper.assertTrue(g.getAttributeValue(Attributes.ATTACK_DAMAGE) == 9.0, "attack 9, got " + g.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(g.getArmorValue() == 8, "armour 8, got " + g.getArmorValue());
            helper.assertTrue(Math.abs(g.getBbWidth() - 1.2F) < 0.01F && Math.abs(g.getBbHeight() - 2.2F) < 0.01F,
                    "a 1.2 x 2.2 hitbox, got " + g.getBbWidth() + " x " + g.getBbHeight());
            helper.assertTrue(near(g.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.18), "speed 0.18");
            helper.assertTrue(near(g.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.7), "knockback resistance 0.7");
            helper.succeed();
        });
    }

    /** Home: the End midlands (not the highlands or the main island), weight 4, alone, a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("anchor_golem"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "its home holds the End midlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.THE_END)), "never the highlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/anchor_golem")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.ANCHOR_GOLEM.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.ANCHOR_GOLEM.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }

    /**
     * Its spawn rule takes end stone and refuses a spot with nothing under it; a natural spawn is refused in the light
     * (a glowstone block beside the spot), as it spawns only in the dark.
     */
    private static void spawnRule(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("anchor_golem");
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.END_STONE);
        helper.setBlock(new BlockPos(8, 0, 8), Blocks.AIR);
        helper.setBlock(new BlockPos(8, 1, 3), Blocks.END_STONE);
        helper.setBlock(new BlockPos(9, 2, 3), Blocks.GLOWSTONE);
        var type = ModMobs.ANCHOR_GOLEM.get();
        var level = helper.getLevel();
        helper.assertTrue(row.place() == RosterStats.Place.GROUND && !row.anyLight(), "a ground mob of the dark, place " + row.place());
        helper.assertTrue(TeemingSpawns.placeOk(row, type, level, helper.absolutePos(new BlockPos(3, 1, 3))), "end stone is a spawn spot");
        helper.assertTrue(!TeemingSpawns.placeOk(row, type, level, helper.absolutePos(new BlockPos(8, 1, 8))), "a spot over air is not");
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(!TeemingSpawns.check(row, type, level, EntitySpawnReason.NATURAL, helper.absolutePos(new BlockPos(8, 2, 3)), level.getRandom()),
                    "a lit spot on end stone takes no natural spawn");
            helper.succeed();
        });
    }
}
