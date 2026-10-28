package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Driftsong;
import com.panda.teeming.mob.end.Driftsong.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner.Rule;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Driftsong: HP 120, Atk 0, armour 6, a 6.0 x 2.5 hitbox, no tell. It drifts at 0.05 blocks a tick, keeping 4 blocks
 * under an island's underside; every 400 ticks its back lights for 20 ticks and it sings, giving Slow Falling for 200
 * ticks to every player within 24 blocks of the middle of its body; it never attacks, and hurt it dives 10 blocks. The
 * void below the outer End islands (the spawner's void-below rule), alone, one within 128 blocks.
 *
 * <p>The arena is small for a 6-block body, so the song tests moor it (its drift held still by a test hook while its
 * song keeps time), the drift test holds its course for the 2 blocks it is timed over, and the dive and spawn-rule
 * tests open the arena's roof and use the air above their own arena. Every song is kept to this test's own players:
 * a listener vetoes the gift to anyone else, and any other Driftsong's gift to them. Survivors never tick, so the Slow
 * Falling they get keeps its full length to be read. Expected values are plain numbers.
 */
public final class DriftsongTests {
    private DriftsongTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("driftsong_tell", DriftsongTests::tell);
        out.accept("driftsong_counter", DriftsongTests::counter);
        out.accept("driftsong_failure", DriftsongTests::failure);
        out.accept("driftsong_drift_speed", DriftsongTests::driftSpeed);
        out.accept("driftsong_song_every", DriftsongTests::songEvery);
        TeemingGameTests.timeLimit("driftsong_song_every", 480);
        out.accept("driftsong_song_range", DriftsongTests::songRange);
        out.accept("driftsong_never_attacks", DriftsongTests::neverAttacks);
        out.accept("driftsong_turns_gently", DriftsongTests::turnsGently);
        out.accept("driftsong_stays_under", DriftsongTests::staysUnder);
        TeemingGameTests.timeLimit("driftsong_stays_under", 240);
        out.accept("driftsong_dies_mid_swell", DriftsongTests::diesMidSwell);
        out.accept("driftsong_cap", DriftsongTests::cap);
        out.accept("driftsong_spawn_rule", DriftsongTests::spawnRule);
        out.accept("driftsong_save_load", DriftsongTests::saveLoad);
        out.accept("driftsong_stats", DriftsongTests::stats);
        out.accept("driftsong_home", DriftsongTests::home);
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------------

    /** A Driftsong at {@code at}, its next song far off, kept to this test (removed when it ends). */
    private static Driftsong driftsong(GameTestHelper helper, Vec3 at, Player... hearers) {
        Driftsong d = helper.spawn(ModMobs.DRIFTSONG.get(), at);
        d.core().setLook("base", true);
        d.setSongIn(100000);
        contain(helper, d, hearers);
        TestPlayers.whenDone(helper, d::discard);
        return d;
    }

    /** Only this Driftsong's gifts to these players land: it gives no one else Slow Falling, and no other gives them any. */
    private static void contain(GameTestHelper helper, Driftsong d, Player... hearers) {
        Set<Player> mine = Set.of(hearers);
        Consumer<MechanicEvent> keep = e -> {
            if (!e.step().equals("gift")) return;
            boolean ours = e.mob() == d, theirs = mine.contains(e.target());
            if (ours != theirs) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
    }

    /** Game ticks at which this Driftsong posted {@code step}, recorded from now on. */
    private static List<Long> steps(GameTestHelper helper, Driftsong d, String step) {
        List<Long> seen = new ArrayList<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == d && e.step().equals(step)) seen.add(helper.getLevel().getGameTime());
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return seen;
    }

    private static int slowFalling(Player p) {
        MobEffectInstance e = p.getEffect(MobEffects.SLOW_FALLING);
        return e == null ? 0 : e.getDuration();
    }

    /** Clears the barrier roof over the arena (the two layers above its 8 rows), so the air above it is open. */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
    }

    private static Vec3 rel(GameTestHelper helper, Driftsong d) {
        return helper.relativeVec(d.position());
    }

    // ---- the song ------------------------------------------------------------------------------------------------------

    /**
     * Tell: the swell. Its back lights (visual stage 1) and it holds the swell for 20 ticks, giving nothing yet; the
     * song and the gift land on the swell's last tick, not before.
     */
    private static void tell(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 1, 1.5));
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5), p);
        d.moor(200);
        List<Long> swell = steps(helper, d, "swell"), sing = steps(helper, d, "sing"), gift = steps(helper, d, "gift");
        helper.runAtTickTime(2, () -> d.setSongIn(3));
        helper.onEachTick(() -> {
            if (swell.isEmpty() || !sing.isEmpty()) return;
            helper.assertTrue(d.core().in(Step.SWELL), "it holds the swell until the song, has " + d.phase());
            helper.assertTrue(d.core().stage() == 1 || helper.getLevel().getGameTime() == swell.getFirst(), "its back is lit in the swell");
            helper.assertTrue(slowFalling(p) == 0, "nothing given in the swell");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!sing.isEmpty() && !gift.isEmpty(), "it sang");
            long held = sing.getFirst() - swell.getFirst();
            helper.assertTrue(held == 20, "the swell held 20 ticks, held " + held);
            helper.assertTrue(gift.getFirst().equals(sing.getFirst()), "the gift lands with the song");
            helper.assertTrue(slowFalling(p) == 200, "Slow Falling 200 after the song, got " + slowFalling(p));
        });
    }

    /** Counter: stand within its reach when it sings and you get the gift, Slow Falling (level I) for 200 ticks. */
    private static void counter(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(1.5, 1, 6.5));
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5), p);
        d.moor(200);
        helper.runAtTickTime(2, () -> d.setSongIn(1));
        helper.succeedWhen(() -> {
            MobEffectInstance e = p.getEffect(MobEffects.SLOW_FALLING);
            helper.assertTrue(e != null, "the song gave Slow Falling");
            helper.assertTrue(e.getDuration() == 200 && e.getAmplifier() == 0, "Slow Falling I for 200, got " + e);
            helper.assertTrue(d.core().in(Step.SING), "while it sings, has " + d.phase());
        });
    }

    /**
     * The song every 400 ticks: two swells 400 ticks apart, each gift 20 ticks after its swell, so the gifts are 400
     * apart too.
     */
    private static void songEvery(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(1.5, 1, 6.5));
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5), p);
        d.moor(1000);
        List<Long> swell = steps(helper, d, "swell"), gift = steps(helper, d, "gift");
        helper.runAtTickTime(2, () -> d.setSongIn(5));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(swell.size() == 1 && gift.size() == 1, "one song so far, " + swell.size() + " swells " + gift.size() + " gifts");
            helper.assertTrue(d.songIn() > 340 && d.songIn() <= 400, "the next one ~400 off, in " + d.songIn());
            p.removeEffect(MobEffects.SLOW_FALLING);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(swell.size() >= 2 && gift.size() >= 2, "it sang twice");
            long apart = swell.get(1) - swell.get(0), gifts = gift.get(1) - gift.get(0);
            helper.assertTrue(apart == 400, "swells 400 apart, got " + apart);
            helper.assertTrue(gifts == 400, "gifts 400 apart, got " + gifts);
            helper.assertTrue(gift.get(1) - swell.get(1) == 20, "each gift 20 after its swell");
            helper.assertTrue(slowFalling(p) == 200, "the second song gave 200 again, got " + slowFalling(p));
        });
    }

    /**
     * Reach 24 from the middle of its body: a player 23.9 straight above that point hears it, one 24.1 above doesn't, and
     * one 5 across does (the two above stand in the air over this arena; survivors never fall). The rule
     * itself against plain points.
     */
    private static void songRange(GameTestHelper helper) {
        helper.assertTrue(Driftsong.hears(Vec3.ZERO, new Vec3(23.9, 0, 0)), "23.9 across hears");
        helper.assertTrue(!Driftsong.hears(Vec3.ZERO, new Vec3(24.1, 0, 0)), "24.1 across doesn't");
        helper.assertTrue(Driftsong.hears(Vec3.ZERO, new Vec3(0, -23.9, 0)), "23.9 below hears");
        helper.assertTrue(Driftsong.hears(Vec3.ZERO, new Vec3(13.8, 13.8, 13.8)), "a 23.9 diagonal hears");
        helper.assertTrue(!Driftsong.hears(Vec3.ZERO, new Vec3(14.0, 14.0, 14.0)), "a 24.2 diagonal doesn't");
        // a body standing at y 1 has the middle of its 2.5-block height at y 2.25
        ServerPlayer near = TestPlayers.survivor(helper, new Vec3(6.5, 2.25 + 23.9, 6.5));
        ServerPlayer far = TestPlayers.survivor(helper, new Vec3(6.5, 2.25 + 24.1, 6.5));
        ServerPlayer floor = TestPlayers.survivor(helper, new Vec3(1.5, 2.25, 6.5));
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5), near, far, floor);
        d.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            Vec3 c = helper.relativeVec(d.songCentre());
            helper.assertTrue(c.distanceTo(new Vec3(6.5, 2.25, 6.5)) < 0.01, "the song is measured from the middle of its body, at " + c);
            int given = d.sing(helper.getLevel());
            helper.assertTrue(slowFalling(near) == 200, "23.9 above: Slow Falling 200, got " + slowFalling(near));
            helper.assertTrue(slowFalling(far) == 0, "24.1 above: nothing, got " + slowFalling(far));
            helper.assertTrue(slowFalling(floor) == 200, "5 across: Slow Falling 200, got " + slowFalling(floor));
            helper.assertTrue(given == 2, "two given, " + given);
            helper.succeed();
        });
    }

    /** A Driftsong killed in its swell never sings: no gift from the dying body. */
    private static void diesMidSwell(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(1.5, 1, 6.5));
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5), p);
        d.moor(200);
        List<Long> swell = steps(helper, d, "swell");
        helper.runAtTickTime(2, () -> d.setSongIn(1));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(swell.size() == 1 && d.core().in(Step.SWELL), "it is in its swell, " + d.phase());
            d.kill(helper.getLevel());
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(slowFalling(p) == 0, "no gift from a dead Driftsong, got " + slowFalling(p));
            helper.succeed();
        });
    }

    // ---- the drift -------------------------------------------------------------------------------------------------------

    /** Drift 0.05 a tick: 2.00 blocks in 40 ticks along a held course (east), level, nothing across it. */
    private static void driftSpeed(GameTestHelper helper) {
        Driftsong d = driftsong(helper, new Vec3(5.0, 1, 6.5));
        d.setYRot(-90.0F);
        d.setYBodyRot(-90.0F);
        d.setCourse(-90.0F, 200);
        Vec3[] from = {null};
        helper.runAtTickTime(5, () -> from[0] = rel(helper, d));
        helper.runAtTickTime(45, () -> {
            Vec3 now = rel(helper, d);
            double dx = now.x - from[0].x, dz = now.z - from[0].z, dy = now.y - from[0].y;
            helper.assertTrue(Math.abs(dx - 2.0) < 0.01, "2.00 blocks east in 40 ticks, got " + dx);
            helper.assertTrue(Math.abs(dz) < 0.01 && Math.abs(dy) < 0.01, "straight and level, dz " + dz + " dy " + dy);
            Vec3 v = d.getDeltaMovement();
            helper.assertTrue(Math.abs(v.horizontalDistance() - 0.05) < 1.0E-4, "0.05 a tick, got " + v.horizontalDistance());
            helper.succeed();
        });
    }

    /** It turns gently: half a degree a tick toward a new course, so 10 degrees in 20 ticks. */
    private static void turnsGently(GameTestHelper helper) {
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5));
        d.moor(200);
        d.setYRot(0.0F);
        d.setCourse(0.0F, 200);
        float[] from = {0};
        helper.runAtTickTime(4, () -> {
            from[0] = d.getYRot();
            d.setCourse(from[0] + 90.0F, 200);
        });
        helper.runAtTickTime(24, () -> {
            float turned = d.getYRot() - from[0];
            helper.assertTrue(Math.abs(turned - 10.0F) < 0.01F, "10 degrees in 20 ticks, turned " + turned);
            helper.succeed();
        });
    }

    /**
     * It keeps its back 4 blocks under an underside: put 2.5 under the arena's roof it sinks until the gap is 4 (within
     * the 0.2 its half-second probe can overshoot), and no further.
     */
    private static void staysUnder(GameTestHelper helper) {
        Driftsong d = driftsong(helper, new Vec3(6.5, 3, 6.5));
        d.moor(400);
        d.setCourse(d.getYRot(), 400);
        double[] roof = {0};
        helper.runAtTickTime(2, () -> {
            BlockPos at = BlockPos.containing(d.getX(), d.getBoundingBox().maxY, d.getZ());
            while (helper.getLevel().getBlockState(at).getCollisionShape(helper.getLevel(), at).isEmpty()) at = at.above();
            roof[0] = at.getY();
            double gap = roof[0] - d.getBoundingBox().maxY;
            helper.assertTrue(gap < 4.0, "it starts closer than 4 under the roof, " + gap);
        });
        helper.runAtTickTime(220, () -> {
            double gap = roof[0] - d.getBoundingBox().maxY;
            helper.assertTrue(gap >= 4.0 && gap < 4.25, "4 blocks under the roof, got " + gap);
            helper.succeed();
        });
    }

    // ---- hurt ------------------------------------------------------------------------------------------------------------

    /**
     * Failure: strike it and it dives exactly 10 blocks, its course turned away from you, and drifts on at 0.05; it
     * takes no target and never strikes back. Above this arena, its roof opened.
     */
    private static void failure(GameTestHelper helper) {
        openRoof(helper);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 1, 1.5));
        Driftsong d = driftsong(helper, new Vec3(6.5, 13, 5.0), p);
        d.setYRot(90.0F);
        d.setCourse(90.0F, 400);
        double[] from = {0};
        helper.runAtTickTime(2, () -> {
            from[0] = d.getY();
            boolean hurt = d.hurtServer(helper.getLevel(), d.damageSources().playerAttack(p), 2.0F);
            helper.assertTrue(hurt && d.core().in(Step.DIVE), "struck, it dives, has " + d.phase());
            // the striker stands north of it (it has drifted a few hundredths west since): its course turns south, yaw 0
            helper.assertTrue(Math.abs(d.course()) < 3.0F, "its course turned away from the striker (south), " + d.course());
            d.setCourse(d.course(), 400);       // kept on it, so its drift stays inside this arena
        });
        helper.runAtTickTime(20, () -> helper.assertTrue(from[0] - d.getY() > 3.0 && from[0] - d.getY() < 6.0,
                "half way down after 18 ticks, " + (from[0] - d.getY())));
        helper.runAtTickTime(60, () -> {
            double fell = from[0] - d.getY();
            helper.assertTrue(Math.abs(fell - 10.0) < 0.01, "dived 10 blocks, got " + fell);
            helper.assertTrue(Double.isNaN(d.diveTo()) && d.core().idle(), "the dive is over, " + d.phase());
            helper.assertTrue(Math.abs(d.getDeltaMovement().horizontalDistance() - 0.05) < 1.0E-4 && Math.abs(d.getDeltaMovement().y) < 1.0E-6,
                    "drifting on level at 0.05, " + d.getDeltaMovement());
            helper.assertTrue(d.getTarget() == null && p.getHealth() == 20.0F, "no target and no blow, " + p.getHealth());
            helper.succeed();
        });
    }

    /** It never attacks: a survivor beside it strikes it twice and stands there 80 ticks, untouched; it has no target. */
    private static void neverAttacks(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 1, 2.0));
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5), p);
        d.moor(200);
        d.setCourse(d.getYRot(), 200);
        helper.assertTrue(d.getAttributeValue(Attributes.ATTACK_DAMAGE) == 0.0, "attack 0");
        helper.runAtTickTime(2, () -> d.hurtServer(helper.getLevel(), d.damageSources().playerAttack(p), 2.0F));
        helper.runAtTickTime(30, () -> d.hurtServer(helper.getLevel(), d.damageSources().playerAttack(p), 2.0F));
        helper.onEachTick(() -> helper.assertTrue(p.getHealth() == 20.0F, "the player is never hurt, " + p.getHealth()));
        helper.runAtTickTime(82, () -> {
            helper.assertTrue(d.getTarget() == null && d.getLastHurtMob() == null, "it never targets or hits");
            helper.assertTrue(d.getHealth() < 120.0F, "it took the blows, " + d.getHealth());
            helper.succeed();
        });
    }

    // ---- home --------------------------------------------------------------------------------------------------------------

    /**
     * Local cap: one in 128 blocks. With one here the natural-spawn predicate refuses a second beside it and 127 blocks
     * above it, and allows one 150 above (its body, and any Driftsong of these tests in the air over its own arena, then lie outside
     * the 128-block box).
     */
    private static void cap(GameTestHelper helper) {
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5));
        d.setNoAi(true);
        RosterStats.Entry row = RosterStats.get("driftsong");
        BlockPos at = helper.absolutePos(new BlockPos(6, 1, 6));
        helper.runAtTickTime(2, () -> {
            var type = ModMobs.DRIFTSONG.get();
            helper.assertTrue(Driftsong.CAP_RADIUS == 128 && TeemingSpawns.RARE_CAP_RADIUS == 128, "its cap reaches 128 blocks");
            helper.assertTrue(!TeemingSpawns.underCap(row, type, helper.getLevel(), at), "a second beside it is refused");
            helper.assertTrue(!TeemingSpawns.underCap(row, type, helper.getLevel(), at.above(127)), "a second 127 blocks off is refused");
            helper.assertTrue(TeemingSpawns.underCap(row, type, helper.getLevel(), at.above(150)), "a second 150 blocks off may spawn");
            helper.succeed();
        });
    }

    /**
     * The spawner's void-below rule takes open air with an island more than 8 blocks over it (and under height 40), and
     * refuses open air with nothing over it, a spot only 8 under the island, and the floor. One summoned at the spot it
     * takes hovers there. The island is one end stone block 11 over the floor, above this arena with its roof opened.
     */
    private static void spawnRule(GameTestHelper helper) {
        openRoof(helper);
        Rule rule = Rule.of(RosterStats.get("driftsong").spawner());
        helper.assertTrue(rule == Rule.VOID_BELOW && rule.dimension == Level.END, "placed by the End's void-below rule, got " + rule);
        helper.setBlock(new BlockPos(4, 12, 4), Blocks.END_STONE);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(helper.absolutePos(new BlockPos(4, 2, 4)).getY() < 40, "the arena lies under height 40");
        helper.assertTrue(rule.fits(level, helper.absolutePos(new BlockPos(4, 2, 4))), "open air 10 under an island fits");
        helper.assertTrue(rule.fits(level, helper.absolutePos(new BlockPos(4, 4, 4))), "open air with the island's top 9 over it still fits");
        helper.assertTrue(!rule.fits(level, helper.absolutePos(new BlockPos(4, 5, 4))), "open air with the island's top only 8 over it doesn't");
        helper.assertTrue(!rule.fits(level, helper.absolutePos(new BlockPos(8, 2, 8))), "open air with nothing over it doesn't");
        helper.assertTrue(!rule.fits(level, helper.absolutePos(new BlockPos(4, 0, 4))), "the floor doesn't");
        helper.assertTrue(TeemingSpawns.placeOk(RosterStats.get("driftsong"), ModMobs.DRIFTSONG.get(), level, helper.absolutePos(new BlockPos(4, 2, 4))),
                "its placement takes the air there");
        Driftsong d = driftsong(helper, new Vec3(4.5, 2, 4.5));
        d.setNoAi(true);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(Math.abs(rel(helper, d).y - 2.0) < 0.001, "it hovers where it was put, at " + rel(helper, d).y);
            helper.succeed();
        });
    }

    /**
     * Home: the void below the outer End islands, placed only by the spawner's void-below rule (on no biome list, so no
     * biome modifier), its biome tag the four outer End biomes and any modded End biome; a creature, alone, weight 0.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("driftsong");
        helper.assertTrue(row.dim() == RosterStats.Dim.END && row.cat() == RosterStats.Cat.CREATURE && !row.apex(), "an End creature, not an apex");
        helper.assertTrue(ModMobs.DRIFTSONG.get().getCategory() == MobCategory.CREATURE, "creature category");
        helper.assertTrue(!row.biomeSpawns(), "on no biome list");
        var access = helper.getLevel().registryAccess();
        TeemingGameTests.assertOuterEndTag(helper, "driftsong");
        helper.assertTrue(access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/driftsong"))).isEmpty(), "no biome modifier");
        helper.assertTrue(row.weight() == 0, "weight 0, got " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "alone, got " + row.groupMin() + ".." + row.groupMax());
        helper.succeed();
    }

    /** Save and load: the song clock, its course and its look come back. */
    private static void saveLoad(GameTestHelper helper) {
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5));
        d.setNoAi(true);
        d.core().setLook("starlit_leviathan", true);
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            d.setSongIn(123);
            d.setCourse(37.0F, 0);
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            d.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            Driftsong c = ModMobs.DRIFTSONG.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            helper.assertTrue(c.songIn() == 123, "the song clock, " + c.songIn());
            helper.assertTrue(Math.abs(c.course() - 37.0F) < 0.001F, "the course, " + c.course());
            helper.assertTrue(c.look().equals("starlit_leviathan"), "the look, " + c.look());
            helper.assertTrue(c.isNoGravity(), "it still floats");
            c.discard();
            helper.succeed();
        });
    }

    /** Health 120, attack 0, armour 6, a 6.0 x 2.5 hitbox, both looks. */
    private static void stats(GameTestHelper helper) {
        Driftsong d = driftsong(helper, new Vec3(6.5, 1, 6.5));
        d.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(d.getMaxHealth() == 120.0F && d.getHealth() == 120.0F, "health 120, got " + d.getHealth() + "/" + d.getMaxHealth());
            helper.assertTrue(d.getAttributeValue(Attributes.ATTACK_DAMAGE) == 0.0, "attack 0");
            helper.assertTrue(d.getArmorValue() == 6, "armour 6, got " + d.getArmorValue());
            helper.assertTrue(Math.abs(d.getBbWidth() - 6.0F) < 0.01F && Math.abs(d.getBbHeight() - 2.5F) < 0.01F,
                    "a 6.0 x 2.5 hitbox, got " + d.getBbWidth() + " x " + d.getBbHeight());
            helper.assertTrue(RosterStats.get("driftsong").tellTicks() == 0, "no tell in its row");
            helper.succeed();
        });
    }
}
