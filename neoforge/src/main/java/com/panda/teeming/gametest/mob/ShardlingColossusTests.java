package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.ShardlingColossus;
import com.panda.teeming.mob.end.ShardlingColossus.Form;
import com.panda.teeming.mob.end.ShardlingColossus.Step;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Shardling Colossus: a 16-tick crack (still, harmless) before its slam, which hits for 9 within 3 blocks in front and
 * nothing behind, beside or past that, with no knockback. A single hit of 12 splits it (the End sword's 11 doesn't), counted as
 * dealt, before its armour; so does a hit that leaves it below half its health (exactly half doesn't). The halves are
 * at scale 0.7 (a 0.98 x 1.82 hitbox) with half its health each and half its maximum, hit for 6, and set off to rejoin
 * exactly 100 ticks after the split. Failing the counter: they rejoin into one fused colossus with their health added
 * up, which never splits again. The counter: one half killed inside the 100 ticks leaves the other a half for good. The
 * drops: the first half to fall drops a void crystal; the second drops another only when a player's single hit of 12
 * split it and both halves fell inside the 100 ticks, so a colossus worn below half, or halves killed too slowly, pay
 * once; with one half out of reach when the other falls, still one between them. Vetoes, persistence of the halves, a
 * reload, its stats and its home. Every number is checked against a plain
 * value.
 */
public final class ShardlingColossusTests {
    private ShardlingColossusTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("shardling_colossus_tell", ShardlingColossusTests::tell);
        out.accept("shardling_colossus_slam", ShardlingColossusTests::slam);
        out.accept("shardling_colossus_split_hit_12", ShardlingColossusTests::splitHitTwelve);
        out.accept("shardling_colossus_split_before_armour", ShardlingColossusTests::splitBeforeArmour);
        out.accept("shardling_colossus_split_below_half", ShardlingColossusTests::splitBelowHalf);
        out.accept("shardling_colossus_halves", ShardlingColossusTests::halves);
        out.accept("shardling_colossus_half_slam", ShardlingColossusTests::halfSlam);
        out.accept("shardling_colossus_rejoin_100", ShardlingColossusTests::rejoinHundred);
        TeemingGameTests.wide("shardling_colossus_rejoin_100");
        TeemingGameTests.timeLimit("shardling_colossus_rejoin_100", 300);
        out.accept("shardling_colossus_failure", ShardlingColossusTests::failure);
        TeemingGameTests.wide("shardling_colossus_failure");
        TeemingGameTests.timeLimit("shardling_colossus_failure", 300);
        out.accept("shardling_colossus_meet_a_block_apart", ShardlingColossusTests::meetABlockApart);
        out.accept("shardling_colossus_counter", ShardlingColossusTests::counter);
        TeemingGameTests.wide("shardling_colossus_counter");
        TeemingGameTests.timeLimit("shardling_colossus_counter", 220);
        out.accept("shardling_colossus_wear_split_pays_once", ShardlingColossusTests::wearSplitPaysOnce);
        out.accept("shardling_colossus_heavy_split_pays_twice", ShardlingColossusTests::heavySplitPaysTwice);
        TeemingGameTests.timeLimit("shardling_colossus_heavy_split_pays_twice", 160);
        out.accept("shardling_colossus_heavy_split_too_slow", ShardlingColossusTests::heavySplitTooSlow);
        TeemingGameTests.timeLimit("shardling_colossus_heavy_split_too_slow", 160);
        out.accept("shardling_colossus_twin_gone_left_falls", h -> twinGone(h, false));
        out.accept("shardling_colossus_twin_gone_right_falls", h -> twinGone(h, true));
        out.accept("shardling_colossus_despawn_hands_drops", ShardlingColossusTests::despawnHandsDrops);
        out.accept("shardling_colossus_tell_veto", ShardlingColossusTests::tellVeto);
        out.accept("shardling_colossus_split_veto", ShardlingColossusTests::splitVeto);
        out.accept("shardling_colossus_halves_kept", ShardlingColossusTests::halvesKept);
        out.accept("shardling_colossus_reload", ShardlingColossusTests::reload);
        TeemingGameTests.wide("shardling_colossus_reload");
        TeemingGameTests.timeLimit("shardling_colossus_reload", 160);
        out.accept("shardling_colossus_stats", ShardlingColossusTests::stats);
        out.accept("shardling_colossus_home", ShardlingColossusTests::home);
    }

    // ---- fixtures --------------------------------------------------------------------------------------------------

    /**
     * A colossus with the base look, a follow range of 1 (it, and its halves, never notice survivors in other arenas),
     * ready to slam, facing +z, removed when the test ends with any half it made; its slam never reaches past the arena.
     */
    /**
     * The middle of the wide arena, for the tests that leave the halves to stroll for 100 ticks or more: from a 12-block
     * arena they can walk into a chunk the test server doesn't tick and freeze there, so they never rejoin.
     */
    private static final double MID_X = 50.5, MID_Z = 22.5;

    private static ShardlingColossus colossus(GameTestHelper helper, double x, double z) {
        ShardlingColossus c = helper.spawn(ModMobs.SHARDLING_COLOSSUS.get(), new Vec3(x, 1, z));
        c.core().setLook("base", true);
        c.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        c.resetSlam();
        face(c, 0.0F);
        TestPlayers.whenDone(helper, () -> {
            ShardlingColossus twin = c.twin(helper.getLevel());
            if (twin != null) twin.discard();
            c.discard();
        });
        contain(helper);
        return c;
    }

    private static void face(ShardlingColossus c, float yaw) {
        c.setYRot(yaw);
        c.setYBodyRot(yaw);
        c.setYHeadRot(yaw);
    }

    /** Vetoes the slam's hit on anything outside this arena, for a colossus inside it. */
    private static void contain(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() instanceof ShardlingColossus && e.step().equals("slam_hit") && e.target() != null
                    && bounds.contains(e.mob().position()) && !bounds.contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
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

    /** Magic damage (armour doesn't count) with the hurt cooldown cleared first, so every hit lands in full. */
    private static void hit(GameTestHelper helper, ShardlingColossus c, float amount) {
        c.damageCooldownTime = 0;
        c.hurtServer(helper.getLevel(), c.damageSources().magic(), amount);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    // ---- the slam --------------------------------------------------------------------------------------------------

    /** The crack lasts exactly 16 ticks, standing still and hurting nothing, and ends in the slam. */
    private static void tell(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 4.5);
        Pig pig = pig(helper, 5.5, 6.3);
        c.setTarget(pig);
        long[] start = {-1}, slam = {-1};
        helper.onEachTick(() -> {
            if (c.core().in(Step.TELL)) {
                if (start[0] < 0) start[0] = helper.getTick();
                helper.assertTrue(c.getDeltaMovement().horizontalDistance() < 0.05, "it stands still while it cracks");
                helper.assertTrue(pig.getHealth() >= 100.0F, "the tell hurts nothing");
            } else if (start[0] >= 0 && slam[0] < 0) {
                slam[0] = helper.getTick();
                helper.assertTrue(c.core().in(Step.SLAM), "the tell ends in the slam, phase " + c.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && slam[0] >= 0, "a tell and then a slam");
            helper.assertTrue(slam[0] - start[0] == 16, "the crack lasts 16 ticks, lasted " + (slam[0] - start[0]));
        });
    }

    /**
     * The slam hits the pig in front (1.8 blocks out) for 9 and doesn't move it; a pig behind (2 blocks back), one beside
     * (2.1 blocks across, 90 degrees off) and one past its reach (4.5 blocks out, its near side 4.05) are untouched.
     */
    private static void slam(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 4.0);
        Pig front = pig(helper, 5.5, 5.8);
        Pig behind = pig(helper, 5.5, 2.0);
        Pig beside = pig(helper, 7.6, 4.0);
        Pig far = pig(helper, 5.5, 8.5);
        c.setTarget(front);
        c.stayPut();
        Vec3[] moved = {null};
        helper.onEachTick(() -> {
            if (moved[0] == null && front.getHealth() < 100.0F) moved[0] = front.getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(moved[0] != null, "the slam landed");
            float lost = 100.0F - front.getHealth();
            helper.assertTrue(Math.abs(lost - 9.0F) < 0.01F, "the slam hits for 9, the pig lost " + lost);
            helper.assertTrue(moved[0].horizontalDistance() < 0.01, "and doesn't knock it back, motion " + moved[0]);
            helper.assertTrue(behind.getHealth() >= 100.0F, "nothing behind it is hit");
            helper.assertTrue(beside.getHealth() >= 100.0F, "nothing 90 degrees off is hit");
            helper.assertTrue(far.getHealth() >= 100.0F, "nothing past 3 blocks is hit");
        });
    }

    /** A half hits for 6: the left half, after its stagger, slams a pig 1.5 blocks in front of it. */
    private static void halfSlam(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 4.0);
        Pig[] pig = {null};
        helper.runAtTickTime(1, () -> helper.assertTrue(c.split(null, 10) != null, "it split"));
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(c.form() == Form.LEFT && c.core().idle(), "the left half is done staggering, phase " + c.phase());
            Vec3 at = helper.relativeVec(c.position());
            pig[0] = pig(helper, at.x, at.z + 1.5);
            helper.assertTrue(c.tell(pig[0]), "the half starts its tell");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(pig[0] != null && pig[0].getHealth() < 100.0F, "the half's slam landed");
            float lost = 100.0F - pig[0].getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "a half's slam hits for 6, the pig lost " + lost);
        });
    }

    // ---- the split -------------------------------------------------------------------------------------------------

    /**
     * A hit of 11 (the End reference sword's) leaves it whole at 39; a hit of 12 splits it, each half with 19 of the 38
     * left and a maximum of 25.
     */
    private static void splitHitTwelve(GameTestHelper helper) {
        ShardlingColossus eleven = colossus(helper, 3.5, 5.5);
        ShardlingColossus twelve = colossus(helper, 8.5, 5.5);
        helper.runAtTickTime(1, () -> {
            hit(helper, eleven, 11.0F);
            hit(helper, twelve, 12.0F);
            helper.assertTrue(eleven.form() == Form.WHOLE && near(eleven.getHealth(), 39.0), "a hit of 11 leaves it whole at 39, got "
                    + eleven.form() + " at " + eleven.getHealth());
            ShardlingColossus twin = twelve.twin(helper.getLevel());
            helper.assertTrue(twelve.form() == Form.LEFT && twin != null && twin.form() == Form.RIGHT, "a hit of 12 splits it, got " + twelve.form());
            for (ShardlingColossus h : List.of(twelve, twin)) {
                helper.assertTrue(near(h.getHealth(), 19.0) && near(h.getMaxHealth(), 25.0),
                        "each half has 19 of the 38 left, of 25, got " + h.getHealth() + "/" + h.getMaxHealth());
                helper.assertTrue(h.core().in(Step.SPLIT), "each half staggers out of the split");
            }
            helper.succeed();
        });
    }

    /**
     * A pig's blow of 12 splits it though its armour of 8 takes some of it (12 x (1 - 2 / 25) = 11.04, under 12): the hit
     * counts as dealt. Each half has 19.48 (half of what armour left, 38.96).
     */
    private static void splitBeforeArmour(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 4.5);
        Pig pig = pig(helper, 5.5, 8.0);
        helper.runAtTickTime(1, () -> {
            c.hurtServer(helper.getLevel(), c.damageSources().mobAttack(pig), 12.0F);
            ShardlingColossus twin = c.twin(helper.getLevel());
            helper.assertTrue(c.form() == Form.LEFT && twin != null, "a blow of 12 through armour still splits it");
            helper.assertTrue(near(c.getHealth(), 19.48) && near(c.getHealth(), twin.getHealth()),
                    "armour took part of the blow: each half has 19.48, got " + c.getHealth() + " and " + twin.getHealth());
            helper.assertTrue(c.getTarget() == pig && twin.getTarget() == pig, "both halves turn on whoever split it");
            helper.succeed();
        });
    }

    /**
     * Below half splits it: five hits of 5 leave it at 25, exactly half, still whole; one more of 1 leaves it at 24 and
     * splits it, each half with 12.
     */
    private static void splitBelowHalf(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 5.5);
        helper.runAtTickTime(1, () -> {
            for (int i = 0; i < 5; i++) hit(helper, c, 5.0F);
            helper.assertTrue(c.form() == Form.WHOLE && near(c.getHealth(), 25.0), "at exactly half it is whole, got "
                    + c.form() + " at " + c.getHealth());
            hit(helper, c, 1.0F);
            ShardlingColossus twin = c.twin(helper.getLevel());
            helper.assertTrue(c.form() == Form.LEFT && twin != null, "below half it splits");
            helper.assertTrue(near(c.getHealth(), 12.0) && near(twin.getHealth(), 12.0), "each half has 12, got "
                    + c.getHealth() + " and " + twin.getHealth());
            helper.succeed();
        });
    }

    /**
     * The halves: scale 0.7, a 0.98 x 1.82 hitbox (the colossus's is 1.4 x 2.6), side by side 1 block apart across where
     * it faced, staggering 10 ticks, each a half for 7 experience; the left half is the colossus itself. (With no AI
     * here, so its body holds the facing it was given.)
     */
    private static void halves(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 5.5);
        c.setNoAi(true);                                        // it holds its facing; its halves are still timed
        UUID id = c.getUUID();
        ShardlingColossus[] right = {null};
        long[] split = {-1}, stood = {-1};
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(near(c.getScale(), 1.0) && near(c.getBbWidth(), 1.4) && near(c.getBbHeight(), 2.6), "whole, it is 1.4 x 2.6");
            right[0] = c.split(null, 10);
            split[0] = helper.getTick();
            helper.assertTrue(right[0] != null, "it split");
            for (ShardlingColossus h : List.of(c, right[0])) {
                helper.assertTrue(near(h.getScale(), 0.7), "a half is at scale 0.7, got " + h.getScale());
                helper.assertTrue(near(h.getBbWidth(), 0.98) && near(h.getBbHeight(), 1.82), "a half is 0.98 x 1.82, got "
                        + h.getBbWidth() + " x " + h.getBbHeight());
                helper.assertTrue(h.getExperienceReward(helper.getLevel(), null) == 7, "a half gives 7 experience");
            }
            helper.assertTrue(c.getUUID().equals(id) && c.form() == Form.LEFT && right[0].form() == Form.RIGHT && right[0].made(),
                    "the colossus is the left half, the new one the right");
            Vec3 d = right[0].position().subtract(c.position());
            helper.assertTrue(near(Math.abs(d.x), 1.0) && near(d.z, 0.0), "the halves stand 1 block apart across its facing, " + d);
        });
        helper.onEachTick(() -> {
            if (split[0] >= 0 && stood[0] < 0 && c.core().idle()) stood[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stood[0] >= 0, "the stagger ended");
            helper.assertTrue(stood[0] - split[0] == 10, "the halves stagger 10 ticks, " + (stood[0] - split[0]));
        });
    }

    // ---- rejoining: the counter and its failure ------------------------------------------------------------------

    /** The halves set off to rejoin exactly 100 ticks after the split, both of them, and not a tick before; then they meet. */
    private static void rejoinHundred(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, MID_X, MID_Z);
        ShardlingColossus[] right = {null};
        long[] split = {-1}, left = {-1}, rightAt = {-1};
        helper.runAtTickTime(1, () -> {
            right[0] = c.split(null, 10);
            split[0] = helper.getTick();
        });
        StringBuilder log = trail(helper, c, right);
        helper.onEachTick(() -> {
            if (split[0] < 0 || right[0] == null) return;
            if (left[0] < 0 && c.core().in(Step.REGROUP)) left[0] = helper.getTick();
            if (rightAt[0] < 0 && right[0].core().in(Step.REGROUP)) rightAt[0] = helper.getTick();
            if (left[0] < 0 && rightAt[0] < 0) {
                helper.assertTrue(c.form() == Form.LEFT && right[0].isAlive(), "two halves until they set off");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(left[0] >= 0 && rightAt[0] >= 0, "both set off to rejoin");
            helper.assertTrue(left[0] - split[0] == 100 && rightAt[0] - split[0] == 100,
                    "they set off 100 ticks after the split, saw " + (left[0] - split[0]) + " and " + (rightAt[0] - split[0]));
            helper.assertTrue(c.form() == Form.WHOLE && c.fused() && right[0].isRemoved(), "and then rejoined:" + log);
        });
    }

    /**
     * Failing the counter: halves left at 19 and 21 rejoin into one colossus with 40 of 50, at scale 1, fused; the right
     * half is gone without a death. A fused colossus never splits again: a hit of 16 (12 or more, and leaving it at 24,
     * below half) leaves it whole.
     */
    private static void failure(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, MID_X, MID_Z);
        ShardlingColossus[] right = {null};
        boolean[] struck = {false};
        helper.runAtTickTime(1, () -> right[0] = c.split(null, 10));
        StringBuilder log = trail(helper, c, right);
        helper.runAtTickTime(20, () -> {
            hit(helper, c, 6.0F);
            hit(helper, right[0], 4.0F);
            helper.assertTrue(near(c.getHealth(), 19.0) && near(right[0].getHealth(), 21.0), "the halves are at 19 and 21");
        });
        helper.onEachTick(() -> {
            if (!struck[0] && c.form() == Form.WHOLE && c.core().in(Step.FUSE)) {
                struck[0] = true;
                helper.assertTrue(near(c.getHealth(), 40.0) && near(c.getMaxHealth(), 50.0), "rejoined with 40 of 50, got "
                        + c.getHealth() + "/" + c.getMaxHealth());
                helper.assertTrue(near(c.getScale(), 1.0) && near(c.getBbWidth(), 1.4), "whole size again");
                helper.assertTrue(c.fused() && right[0].isRemoved() && !right[0].isDeadOrDying(), "fused; the right half gone without a death");
                hit(helper, c, 16.0F);
                helper.assertTrue(c.form() == Form.WHOLE && near(c.getHealth(), 24.0), "fused, a hit of 16 leaves it whole at 24, got "
                        + c.form() + " at " + c.getHealth());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0], "the halves rejoined:" + log);
            helper.assertTrue(c.form() == Form.WHOLE && c.twin(helper.getLevel()) == null, "and stay one");
        });
    }

    /**
     * Halves setting off from neighbouring blocks, their middles 1.6 apart (past the 1.2 that rejoins them), still meet: a
     * path to the twin counts as done a block short, and they used to stand there until the walk ran out.
     */
    private static void meetABlockApart(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 5.5);
        ShardlingColossus[] right = {null};
        long[] off = {-1}, met = {-1};
        helper.runAtTickTime(1, () -> right[0] = c.split(null, 10));
        StringBuilder log = trail(helper, c, right);
        helper.runAtTickTime(15, () -> {
            Vec3 l = helper.absoluteVec(new Vec3(4.1, 1, 5.5)), r = helper.absoluteVec(new Vec3(5.7, 1, 5.5));
            c.snapTo(l.x, l.y, l.z, 0.0F, 0.0F);
            right[0].snapTo(r.x, r.y, r.z, 0.0F, 0.0F);
            c.getNavigation().stop();
            right[0].getNavigation().stop();
            c.setAge(ShardlingColossus.REJOIN_AFTER);
            right[0].setAge(ShardlingColossus.REJOIN_AFTER);
            off[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (met[0] < 0 && off[0] >= 0 && c.form() == Form.WHOLE) met[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(met[0] >= 0, "the halves rejoined:" + log);
            helper.assertTrue(met[0] - off[0] <= 40, "within 40 ticks of setting off, took " + (met[0] - off[0]));
        });
    }

    /**
     * What the halves did, for a failure message: a line whenever either one sets off or stops walking, and every 50
     * ticks: who walks, how far apart their middles are across the ground and the height between them, and whether each
     * stands where the server ticks entities.
     */
    private static StringBuilder trail(GameTestHelper helper, ShardlingColossus c, ShardlingColossus[] right) {
        StringBuilder log = new StringBuilder();
        boolean[] walking = {false, false};
        helper.onEachTick(() -> {
            ShardlingColossus r = right[0];
            if (r == null || c.form() == Form.WHOLE) return;
            boolean lw = c.core().in(Step.REGROUP), rw = r.core().in(Step.REGROUP);
            if (lw != walking[0] || rw != walking[1] || helper.getTick() % 50 == 0) {
                ServerLevel level = helper.getLevel();
                log.append(String.format(" t%d %s%s apart %.2f dy %.2f ticking %b/%b;", helper.getTick(), lw ? "L" : "-", rw ? "R" : "-",
                        Math.hypot(r.getX() - c.getX(), r.getZ() - c.getZ()), r.getY() - c.getY(),
                        level.isPositionEntityTicking(c.blockPosition()), level.isPositionEntityTicking(r.blockPosition())));
                walking[0] = lw;
                walking[1] = rw;
            }
        });
        return log;
    }

    /**
     * The counter: the right half killed 30 ticks after the split drops a void crystal and leaves the left half a half,
     * alone, for good: 140 ticks after the split it has never set off to rejoin. Killed then, past the 100 ticks and from
     * a split that no player's heavy hit made, the left half drops no second crystal: one from one colossus.
     */
    private static void counter(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, MID_X, MID_Z);
        ShardlingColossus[] right = {null};
        Set<UUID> before = new HashSet<>();
        Set<UUID> crystals = new HashSet<>();
        boolean[] regrouped = {false};
        helper.runAtTickTime(1, () -> {
            for (ItemEntity i : crystals(helper)) before.add(i.getUUID());
            right[0] = c.split(null, 10);
        });
        helper.runAtTickTime(31, () -> {
            right[0].hurtServer(helper.getLevel(), right[0].damageSources().magic(), 1000.0F);
            helper.assertTrue(right[0].isDeadOrDying(), "the right half is dead");
            helper.assertTrue(c.twin(helper.getLevel()) == null, "the left half has no twin now");
        });
        helper.onEachTick(() -> {
            regrouped[0] |= c.core().in(Step.REGROUP);
            for (ItemEntity i : crystals(helper)) if (!before.contains(i.getUUID())) crystals.add(i.getUUID());
        });
        helper.runAtTickTime(141, () -> {
            helper.assertTrue(!regrouped[0] && c.form() == Form.LEFT && near(c.getScale(), 0.7), "140 ticks on it is still a lone half");
            helper.assertTrue(crystals.size() == 1, "the dead half dropped a void crystal, saw " + crystals.size());
            c.hurtServer(helper.getLevel(), c.damageSources().magic(), 1000.0F);
        });
        helper.runAtTickTime(160, () -> {
            helper.assertTrue(c.isDeadOrDying(), "the left half is dead");
            helper.assertTrue(crystals.size() == 1, "the second half dropped no second void crystal, saw " + crystals.size());
            helper.succeed();
        });
    }

    private static List<ItemEntity> crystals(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                i -> i.isAlive() && i.getItem().is(ModMaterials.VOID_CRYSTAL.get()));
    }

    /** Every void crystal that turns up in the arena from now on, by id, counted each tick. */
    private static Set<UUID> newCrystals(GameTestHelper helper) {
        Set<UUID> before = new HashSet<>(), seen = new HashSet<>();
        for (ItemEntity i : crystals(helper)) before.add(i.getUUID());
        helper.onEachTick(() -> {
            for (ItemEntity i : crystals(helper)) if (!before.contains(i.getUUID())) seen.add(i.getUUID());
        });
        return seen;
    }

    /** A player's blow of {@code amount} (as dealt, before armour), with the hurt cooldown cleared first. */
    private static void strike(GameTestHelper helper, ShardlingColossus c, ServerPlayer player, float amount) {
        c.damageCooldownTime = 0;
        c.hurtServer(helper.getLevel(), c.damageSources().playerAttack(player), amount);
    }

    /**
     * A colossus split by a player at tick 1, both halves held still (no AI, so neither walks to the player and its
     * drops stay where they fell): {@code heavy}, by one blow of 12; otherwise worn down by blows of 10 (under 12; 8.8
     * each through its armour), the third leaving it at 23.6, below half. Returns the two halves.
     */
    private static ShardlingColossus[] splitByPlayer(GameTestHelper helper, ShardlingColossus c, ServerPlayer player, boolean heavy) {
        ShardlingColossus[] halves = {c, null};
        helper.runAtTickTime(1, () -> {
            if (heavy) {
                strike(helper, c, player, 12.0F);
            } else {
                strike(helper, c, player, 10.0F);
                strike(helper, c, player, 10.0F);
                helper.assertTrue(c.form() == Form.WHOLE && near(c.getHealth(), 32.4), "two blows of 10 leave it whole at 32.4, got "
                        + c.form() + " at " + c.getHealth());
                strike(helper, c, player, 10.0F);
            }
            ShardlingColossus twin = c.twin(helper.getLevel());
            helper.assertTrue(c.form() == Form.LEFT && twin != null, "the player's blows split it");
            helper.assertTrue(c.heavy() == heavy && twin.heavy() == heavy, (heavy ? "a blow of 12 is a heavy split" : "wearing it down isn't a heavy split"));
            c.setNoAi(true);
            twin.setNoAi(true);
            halves[1] = twin;
        });
        return halves;
    }

    private static void kill(GameTestHelper helper, ShardlingColossus half, ServerPlayer player) {
        strike(helper, half, player, 1000.0F);
        helper.assertTrue(half.isDeadOrDying(), "the half is dead");
    }

    /**
     * Worn down, it pays once: a player's three blows of 10 split it below half, and both halves killed by the player 2
     * and 4 ticks after the split, well inside the 100 ticks, drop one void crystal between them.
     */
    private static void wearSplitPaysOnce(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 4.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 1, 10.5));
        Set<UUID> crystals = newCrystals(helper);
        ShardlingColossus[] h = splitByPlayer(helper, c, player, false);
        helper.runAtTickTime(3, () -> {
            kill(helper, h[1], player);
            helper.assertTrue(!h[0].paysTwice(helper.getLevel()), "a worn split never pays twice");
        });
        helper.runAtTickTime(5, () -> kill(helper, h[0], player));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(crystals.size() == 1, "a colossus worn in two drops one void crystal, saw " + crystals.size());
            helper.succeed();
        });
    }

    /**
     * The counter done: a player's blow of 12 splits it, the right half is killed 3 ticks after the split and the left
     * 99 ticks after, the last tick inside the 100; each drops a void crystal, two from one colossus.
     */
    private static void heavySplitPaysTwice(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 4.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 1, 10.5));
        Set<UUID> crystals = newCrystals(helper);
        ShardlingColossus[] h = splitByPlayer(helper, c, player, true);
        helper.runAtTickTime(4, () -> kill(helper, h[1], player));
        helper.runAtTickTime(20, () -> helper.assertTrue(crystals.size() == 1, "the first half dropped a void crystal, saw " + crystals.size()));
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(h[0].age() == 99 && h[0].paysTwice(helper.getLevel()), "99 ticks after a heavy split the second half still pays, age "
                    + h[0].age());
            kill(helper, h[0], player);
        });
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(crystals.size() == 2, "both halves of a heavy split, killed in time, drop a void crystal each, saw " + crystals.size());
            helper.succeed();
        });
    }

    /**
     * Too slow: a player's blow of 12 splits it, the right half is killed 30 ticks after the split, the left 100 ticks
     * after, the first tick past the window; one void crystal between them.
     */
    private static void heavySplitTooSlow(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 4.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 1, 10.5));
        Set<UUID> crystals = newCrystals(helper);
        ShardlingColossus[] h = splitByPlayer(helper, c, player, true);
        helper.runAtTickTime(31, () -> kill(helper, h[1], player));
        helper.runAtTickTime(101, () -> {
            helper.assertTrue(crystals.size() == 1, "the first half dropped a void crystal, saw " + crystals.size());
            helper.assertTrue(h[0].age() == 100 && !h[0].paysTwice(helper.getLevel()), "100 ticks after the split the window is shut, age " + h[0].age());
            kill(helper, h[0], player);
        });
        helper.runAtTickTime(121, () -> {
            helper.assertTrue(crystals.size() == 1, "halves of a heavy split killed too slowly drop one void crystal, saw " + crystals.size());
            helper.succeed();
        });
    }

    /** A half's save, as a chunk unload writes it. */
    private static CompoundTag save(GameTestHelper helper, ShardlingColossus half) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        half.saveWithoutId(out);
        return out.buildResult();
    }

    /** A half loaded from its save and back in the level, removed when the test ends. */
    private static ShardlingColossus load(GameTestHelper helper, CompoundTag tag) {
        ServerLevel level = helper.getLevel();
        ShardlingColossus loaded = ModMobs.SHARDLING_COLOSSUS.get().create(level, EntitySpawnReason.LOAD);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        helper.assertTrue(level.addFreshEntity(loaded), "the reloaded half is back in the level");
        TestPlayers.whenDone(helper, loaded::discard);
        return loaded;
    }

    /**
     * One half out of reach when the other falls: 2 ticks after a player's worn split, one half is saved and unloaded
     * (taken out of the level as a chunk unload does, so its twin can't find it) and the other is killed; then the
     * unloaded half comes back from its save and is killed too. One void crystal between them, whichever went:
     * {@code leftGoes} false, the left half (which carries the drops) falls first and drops it, and the right comes back
     * without them and drops nothing; true, the right falls first with nothing to find and drops nothing, and the left
     * comes back still carrying them and drops it.
     */
    private static void twinGone(GameTestHelper helper, boolean leftGoes) {
        ShardlingColossus c = colossus(helper, 5.5, 4.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 1, 10.5));
        Set<UUID> crystals = newCrystals(helper);
        ShardlingColossus[] h = splitByPlayer(helper, c, player, false);
        CompoundTag[] saved = {null};
        helper.runAtTickTime(3, () -> {
            ShardlingColossus gone = h[leftGoes ? 0 : 1], stays = h[leftGoes ? 1 : 0];
            helper.assertTrue(h[0].carries() && !h[1].carries(), "the left half carries the drops from the split");
            saved[0] = save(helper, gone);
            gone.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(stays.twin(helper.getLevel()) == null, "its twin is out of reach");
            kill(helper, stays, player);
        });
        helper.runAtTickTime(10, () -> {
            int first = leftGoes ? 0 : 1;
            helper.assertTrue(crystals.size() == first, "the half that fell alone dropped " + first + " void crystals, saw " + crystals.size());
            ShardlingColossus back = load(helper, saved[0]);
            helper.assertTrue(back.form() == (leftGoes ? Form.LEFT : Form.RIGHT) && back.carries() == leftGoes && !back.twinFell(),
                    "the half comes back as it went, carrying the drops: " + back.carries());
            helper.assertTrue(back.twin(helper.getLevel()) == null, "and finds no living twin");
            kill(helper, back, player);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(crystals.size() == 1, "one colossus, its halves falling apart, drops one void crystal, saw " + crystals.size());
            helper.succeed();
        });
    }

    /**
     * The half that carries the drops despawns with its twin in reach: 2 ticks after a player's worn split the left half
     * (the carrier) is removed as a despawn removes it, dropping nothing, and hands the drops to the right half, which
     * then carries them. The right half killed afterwards drops the colossus's one void crystal.
     */
    private static void despawnHandsDrops(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 4.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(5.5, 1, 10.5));
        Set<UUID> crystals = newCrystals(helper);
        ShardlingColossus[] h = splitByPlayer(helper, c, player, false);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(h[0].carries() && !h[1].carries(), "the left half carries the drops from the split");
            helper.assertTrue(h[1].twin(helper.getLevel()) == h[0], "its twin is in reach");
            h[0].discard();
            helper.assertTrue(h[0].isRemoved() && !h[0].carries(), "the despawned half gave the drops up");
            helper.assertTrue(h[1].carries() && !h[1].twinFell(), "and its twin carries them now, not as a twin that fell");
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(crystals.isEmpty(), "a despawn drops nothing, saw " + crystals.size());
            kill(helper, h[1], player);
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(crystals.size() == 1, "the half left carrying them drops the colossus's void crystal, saw " + crystals.size());
            helper.succeed();
        });
    }

    // ---- vetoes, persistence, reload --------------------------------------------------------------------------------

    /** A listener that cancels the "tell" step stops the slam: the pig in front is never hit. */
    private static void tellVeto(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 4.5);
        Pig pig = pig(helper, 5.5, 6.3);
        c.setTarget(pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == c && e.step().equals("tell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(c.core().idle(), "a cancelled tell leaves it idle, phase " + c.phase());
            helper.assertTrue(pig.getHealth() >= 100.0F, "no slam hit the pig");
            helper.succeed();
        });
    }

    /** A listener that cancels the "split" step keeps it whole: a hit of 12 leaves it at 38, one colossus. */
    private static void splitVeto(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 5.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == c && e.step().equals("split")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(1, () -> {
            hit(helper, c, 12.0F);
            helper.assertTrue(c.form() == Form.WHOLE && near(c.getHealth(), 38.0) && near(c.getScale(), 1.0), "vetoed, it stays whole at 38");
            helper.succeed();
        });
    }

    /**
     * The right half is exactly as persistent as the colossus it came from: one kept from despawning makes a kept half;
     * one that isn't (made the way natural spawns are) makes a half that despawns like any monster.
     */
    private static void halvesKept(GameTestHelper helper) {
        helper.runAtTickTime(1, () -> {
            ServerLevel level = helper.getLevel();
            ShardlingColossus kept = colossus(helper, 3.5, 5.5);
            kept.setPersistenceRequired();
            ShardlingColossus keptHalf = kept.split(null, 10);
            helper.assertTrue(keptHalf != null && (keptHalf.isPersistenceRequired() || keptHalf.requiresCustomPersistence()),
                    "a kept colossus makes a kept half");

            ShardlingColossus loose = ModMobs.SHARDLING_COLOSSUS.get().create(level, EntitySpawnReason.NATURAL);
            Vec3 at = helper.absoluteVec(new Vec3(8.5, 1, 5.5));
            loose.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            level.addFreshEntity(loose);
            boolean looseKept = loose.isPersistenceRequired() || loose.requiresCustomPersistence();
            ShardlingColossus looseHalf = loose.split(null, 10);
            helper.assertTrue(!looseKept, "a natural colossus isn't kept");
            helper.assertTrue(looseHalf != null && !looseHalf.isPersistenceRequired() && !looseHalf.requiresCustomPersistence(),
                    "nor is its half");
            looseHalf.discard();
            loose.discard();
            helper.succeed();
        });
    }

    /**
     * A half saved and loaded keeps its form, scale, health, its twin (found again by id) and its clock: the reloaded
     * right half, saved 20 ticks after a heavy split, still sets off to rejoin 100 ticks after it. It also keeps its
     * drop record: the split was heavy, 20 ticks ago, its twin hasn't fallen and it doesn't carry the drops. A second
     * colossus's left half, saved after its right half fell, keeps that its twin fell and that it no longer carries them.
     */
    private static void reload(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, MID_X, MID_Z);
        ShardlingColossus d = colossus(helper, MID_X - 8.0, MID_Z);
        ShardlingColossus[] copy = {null};
        long[] split = {-1}, setOff = {-1};
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(c.split(null, 10, true) != null, "it split, heavy");
            helper.assertTrue(d.split(null, 10, true) != null, "the second split, heavy");
            split[0] = helper.getTick();
        });
        helper.runAtTickTime(21, () -> {
            ShardlingColossus dRight = d.twin(helper.getLevel());
            helper.assertTrue(dRight != null, "the second's right half lives");
            dRight.hurtServer(helper.getLevel(), dRight.damageSources().magic(), 1000.0F);
            helper.assertTrue(dRight.isDeadOrDying() && d.twinFell() && !d.carries(), "its left half saw it fall and gave up the drops");
            CompoundTag tag = save(helper, d);
            d.discard();
            ShardlingColossus lone = load(helper, tag);
            helper.assertTrue(lone.form() == Form.LEFT && lone.heavy() && lone.splitAge() == 20 && lone.twinFell() && !lone.carries(),
                    "reloaded, it is heavy, split 20 ticks ago, its twin fell and it carries nothing: heavy " + lone.heavy() + ", split age "
                            + lone.splitAge() + ", twin fell " + lone.twinFell() + ", carries " + lone.carries());
            lone.discard();
        });
        helper.runAtTickTime(21, () -> {
            ServerLevel level = helper.getLevel();
            ShardlingColossus right = c.twin(level);
            helper.assertTrue(right != null, "the right half lives");
            float health = right.getHealth();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            right.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            right.discard();
            ShardlingColossus loaded = ModMobs.SHARDLING_COLOSSUS.get().create(level, EntitySpawnReason.LOAD);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            helper.assertTrue(level.addFreshEntity(loaded), "the reloaded half is back in the level");
            TestPlayers.whenDone(helper, loaded::discard);
            helper.assertTrue(loaded.form() == Form.RIGHT && loaded.made() && near(loaded.getScale(), 0.7)
                    && near(loaded.getHealth(), health) && near(loaded.getMaxHealth(), 25.0), "it is the same right half");
            helper.assertTrue(loaded.age() == 20, "its clock reads 20, got " + loaded.age());
            helper.assertTrue(loaded.heavy() && loaded.splitAge() == 20 && !loaded.twinFell() && !loaded.carries() && c.carries(),
                    "its drop record survives: heavy " + loaded.heavy() + ", split age " + loaded.splitAge() + ", twin fell "
                            + loaded.twinFell() + ", carries " + loaded.carries());
            helper.assertTrue(loaded.twin(level) == c && c.twin(level) == loaded, "the halves find each other again");
            copy[0] = loaded;
        });
        helper.onEachTick(() -> {
            if (copy[0] != null && setOff[0] < 0 && copy[0].core().in(Step.REGROUP)) setOff[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(setOff[0] >= 0, "the reloaded half set off to rejoin");
            helper.assertTrue(setOff[0] - split[0] == 100, "100 ticks after the split, saw " + (setOff[0] - split[0]));
        });
    }

    // ---- the row ---------------------------------------------------------------------------------------------------

    /** Health 50, attack 9, armour 8, a 1.4 x 2.6 hitbox, 14 experience. */
    private static void stats(GameTestHelper helper) {
        ShardlingColossus c = colossus(helper, 5.5, 5.5);
        c.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(c.getMaxHealth() == 50.0F && c.getHealth() == 50.0F, "health 50, got " + c.getHealth() + "/" + c.getMaxHealth());
            helper.assertTrue(c.getAttributeValue(Attributes.ATTACK_DAMAGE) == 9.0, "attack 9, got " + c.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(c.getArmorValue() == 8, "armour 8, got " + c.getArmorValue());
            helper.assertTrue(Math.abs(c.getBbWidth() - 1.4F) < 0.01F && Math.abs(c.getBbHeight() - 2.6F) < 0.01F,
                    "a 1.4 x 2.6 hitbox, got " + c.getBbWidth() + " x " + c.getBbHeight());
            helper.assertTrue(c.getExperienceReward(helper.getLevel(), null) == 14, "14 experience");
            helper.succeed();
        });
    }

    /** Home: the End midlands (not the highlands or the main island), weight 3, alone, a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("shardling_colossus"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "shardling_colossus").stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "its home holds the End midlands");
        helper.assertTrue(TeemingGameTests.spawnBiomes(helper, "shardling_colossus").stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.THE_END)), "never the highlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/shardling_colossus")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SHARDLING_COLOSSUS.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 3, "spawn weight 3, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.SHARDLING_COLOSSUS.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
