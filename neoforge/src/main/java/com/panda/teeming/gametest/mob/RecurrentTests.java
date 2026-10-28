package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Recurrent;
import com.panda.teeming.mob.end.Recurrent.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Recurrent: an 8-tick wind-up (still, harmless) before a blow of 8.5 with no knockback that doesn't reach a pig 3 blocks
 * up. A killing blow winds it back to exactly the place and health it had 60 ticks before; a second kill inside 30 ticks
 * is final, and one 30 ticks on winds it back again. Its anchor unfolds only in a fight, has 4 health (an arrow breaks
 * it), stays within 6 blocks, waits behind its back and drifts out to its side; once it is broken the first kill is
 * final. Nothing happens after a final death; a kill command is always final; a vetoed rewind is a kill; it lands on
 * safe ground when its old spot is walled up; save and load keep the link and the state. Its stats, home and spawn
 * rule. Every number is checked against a plain value.
 */
public final class RecurrentTests {
    private RecurrentTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("recurrent_tell", RecurrentTests::tell);
        out.accept("recurrent_strike", RecurrentTests::strike);
        out.accept("recurrent_reach_height", RecurrentTests::reachHeight);
        out.accept("recurrent_failure", RecurrentTests::failure);
        TeemingGameTests.timeLimit("recurrent_failure", 140);
        out.accept("recurrent_counter", RecurrentTests::counter);
        TeemingGameTests.timeLimit("recurrent_counter", 180);
        out.accept("recurrent_once_per_60", RecurrentTests::oncePerSixty);
        TeemingGameTests.timeLimit("recurrent_once_per_60", 180);
        out.accept("recurrent_anchor_break", RecurrentTests::anchorBreak);
        out.accept("recurrent_anchor", RecurrentTests::anchor);
        out.accept("recurrent_anchor_arrow", RecurrentTests::anchorArrow);
        out.accept("recurrent_anchor_leash", RecurrentTests::anchorLeash);
        TeemingGameTests.timeLimit("recurrent_anchor_leash", 200);
        out.accept("recurrent_anchor_drift", RecurrentTests::anchorDrift);
        TeemingGameTests.timeLimit("recurrent_anchor_drift", 140);
        out.accept("recurrent_dies_mid_tell", RecurrentTests::diesMidTell);
        out.accept("recurrent_kill_command", RecurrentTests::killCommand);
        out.accept("recurrent_rewind_veto", RecurrentTests::rewindVeto);
        out.accept("recurrent_rewind_safe", RecurrentTests::rewindSafe);
        TeemingGameTests.timeLimit("recurrent_rewind_safe", 140);
        out.accept("recurrent_save_load", RecurrentTests::saveLoad);
        TeemingGameTests.timeLimit("recurrent_save_load", 160);
        out.accept("recurrent_stats", RecurrentTests::stats);
        out.accept("recurrent_home", RecurrentTests::home);
        out.accept("recurrent_spawn_rule", RecurrentTests::spawnRule);
    }

    // ---- fixtures --------------------------------------------------------------------------------------------------

    /**
     * A Recurrent with the base look, a follow range of 1 (it never notices survivors in other arenas), ready to strike,
     * facing +z; removed with its anchor when the test ends.
     */
    private static Recurrent recurrent(GameTestHelper helper, double x, double z) {
        Recurrent r = helper.spawn(ModMobs.RECURRENT.get(), new Vec3(x, 1, z));
        r.core().setLook("base", true);
        r.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        r.resetSwing();
        face(r, 0.0F);
        TestPlayers.whenDone(helper, () -> {
            Recurrent a = r.anchorOf(helper.getLevel());
            if (a != null) a.discard();
            r.discard();
        });
        return r;
    }

    /** One that stays exactly where it is put: no AI (its own tick still keeps its past and its anchor). */
    private static Recurrent still(GameTestHelper helper, double x, double z) {
        Recurrent r = recurrent(helper, x, z);
        r.setNoAi(true);
        return r;
    }

    private static void face(Recurrent r, float yaw) {
        r.setYRot(yaw);
        r.setYBodyRot(yaw);
        r.setYHeadRot(yaw);
    }

    /** A still pig with 100 health at (x, y, z), removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** Magic damage (armour doesn't count) with the hurt cooldown cleared first, so every hit lands in full. */
    private static void hit(GameTestHelper helper, Recurrent r, float amount) {
        r.damageCooldownTime = 0;
        r.hurtServer(helper.getLevel(), r.damageSources().magic(), amount);
    }

    /** A killing blow: far more than its health, as magic. */
    private static void kill(GameTestHelper helper, Recurrent r) {
        hit(helper, r, 1000.0F);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    /** Sets it down at (x, 1, z) in the arena. */
    private static void move(GameTestHelper helper, Recurrent r, double x, double z) {
        Vec3 to = helper.absoluteVec(new Vec3(x, 1, z));
        r.teleportTo(to.x, to.y, to.z);
    }

    private static Vec3 rel(GameTestHelper helper, Recurrent r) {
        return helper.relativeVec(r.position());
    }

    /** What the test saw of a Recurrent at the end of a game tick. */
    private record Seen(Vec3 at, float health) {}

    // ---- the blow --------------------------------------------------------------------------------------------------

    /** The wind-up lasts exactly 8 ticks, standing still and hurting nothing, and ends in the strike. */
    private static void tell(GameTestHelper helper) {
        Recurrent r = recurrent(helper, 5.5, 4.5);
        Pig pig = pig(helper, 5.5, 1, 5.6);
        r.setTarget(pig);
        long[] start = {-1}, strike = {-1};
        helper.onEachTick(() -> {
            if (r.core().in(Step.TELL)) {
                if (start[0] < 0) start[0] = helper.getTick();
                helper.assertTrue(r.getDeltaMovement().horizontalDistance() < 0.05, "it stands still while it winds up");
                helper.assertTrue(pig.getHealth() >= 100.0F, "the wind-up hurts nothing");
            } else if (start[0] >= 0 && strike[0] < 0) {
                strike[0] = helper.getTick();
                helper.assertTrue(r.core().in(Step.STRIKE), "the wind-up ends in the strike, phase " + r.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && strike[0] >= 0, "a wind-up and then a strike");
            helper.assertTrue(strike[0] - start[0] == 8, "the wind-up lasts 8 ticks, lasted " + (strike[0] - start[0]));
        });
    }

    /** The strike hits a pig 1 block in front for exactly 8.5 and doesn't move it; a pig behind is untouched. */
    private static void strike(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 4.5);
        Pig front = pig(helper, 5.5, 1, 5.5);
        Pig behind = pig(helper, 5.5, 1, 3.4);
        Vec3[] moved = {null};
        helper.runAtTickTime(2, () -> helper.assertTrue(r.tell(front), "it winds up"));
        helper.onEachTick(() -> {
            if (moved[0] == null && front.getHealth() < 100.0F) moved[0] = front.getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(moved[0] != null, "the strike landed");
            float lost = 100.0F - front.getHealth();
            helper.assertTrue(Math.abs(lost - 8.5F) < 0.01F, "the strike hits for 8.5, the pig lost " + lost);
            helper.assertTrue(moved[0].horizontalDistance() < 0.01, "and doesn't knock it back, motion " + moved[0]);
            helper.assertTrue(behind.getHealth() >= 100.0F, "nothing behind it is hit");
            helper.assertTrue(r.strikes() == 1, "one blow landed, " + r.strikes());
        });
    }

    /**
     * Reach counts height: beside a pig whose feet are 3 blocks up (1.1 over its head) the strike lands nothing; beside a
     * pig 2 blocks up (0.1 over its head) it lands its 8.5.
     */
    private static void reachHeight(GameTestHelper helper) {
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(3, y, 5), Blocks.STONE);
        for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(8, y, 5), Blocks.STONE);
        Recurrent high = still(helper, 3.5, 4.5);
        Recurrent low = still(helper, 8.5, 4.5);
        Pig up3 = pig(helper, 3.5, 4, 5.5);
        Pig up2 = pig(helper, 8.5, 3, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(high.tell(up3) && low.tell(up2), "both wind up");
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(up3.getHealth() >= 100.0F, "a pig 3 blocks up is out of reach, health " + up3.getHealth());
            float lost = 100.0F - up2.getHealth();
            helper.assertTrue(Math.abs(lost - 8.5F) < 0.01F, "a pig 2 blocks up is hit for 8.5, lost " + lost);
            helper.succeed();
        });
    }

    // ---- the rewind: failing the counter ---------------------------------------------------------------------------

    /**
     * Failing the counter: a kill winds it back to exactly where it stood and the health it had 60 ticks before, as the
     * test saw them at the end of that tick. It is hit to 26, moved, hit to 20 and moved again; killed at tick 90, it
     * stands where it stood at tick 30 with 26 health, alive, dazed, its face dark.
     */
    private static void failure(GameTestHelper helper) {
        Recurrent r = still(helper, 3.5, 5.5);
        Map<Long, Seen> seen = new HashMap<>();
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            long now = helper.getLevel().getGameTime();
            seen.put(now, new Seen(rel(helper, r), r.getHealth()));        // first: the state its own tick just kept
            long tick = helper.getTick();
            if (tick == 2) hit(helper, r, 4.0F);
            if (tick == 20) move(helper, r, 5.5, 5.5);
            if (tick == 40) hit(helper, r, 6.0F);
            if (tick == 45) move(helper, r, 7.5, 5.5);
            if (tick == 90) {
                Seen before = seen.get(now - 60);
                helper.assertTrue(before != null, "the test saw tick " + (now - 60));
                helper.assertTrue(near(r.getHealth(), 20.0) && near(rel(helper, r).x, 7.5), "before the kill it is at x 7.5 with 20");
                helper.assertTrue(near(before.at().x, 5.5) && near(before.health(), 26.0), "60 ticks ago it was at x 5.5 with 26, saw "
                        + before.at() + " with " + before.health());
                helper.assertTrue(r.canRewind(), "its face is lit: it can wind back");
                kill(helper, r);
                helper.assertTrue(r.isAlive() && !r.isDeadOrDying() && r.rewinds() == 1, "it wound back instead of dying");
                Vec3 at = rel(helper, r);
                helper.assertTrue(at.distanceTo(before.at()) < 0.01, "back where it stood 60 ticks before, " + before.at() + ", at " + at);
                helper.assertTrue(near(r.getHealth(), before.health()), "with the health it had then, " + before.health() + ", has " + r.getHealth());
                helper.assertTrue(r.core().in(Step.REWOUND), "dazed, phase " + r.phase());
                helper.assertTrue(!r.canRewind() && r.core().stage() == Recurrent.STAGE_DARK, "its face dark: it can't wind back now");
                done[0] = true;
                helper.succeed();
            }
        });
    }

    /**
     * The counter: a second kill 29 ticks after a rewind is final, though its anchor is whole; the anchor shatters with it.
     */
    private static void counter(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 5.5);
        Recurrent[] a = {null};
        long[] first = {-1};
        helper.runAtTickTime(2, () -> {
            a[0] = r.summonAnchor();
            helper.assertTrue(a[0] != null, "it has an anchor");
        });
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 70) {
                kill(helper, r);
                helper.assertTrue(r.isAlive() && r.rewinds() == 1, "the first kill winds it back");
                first[0] = tick;
            } else if (first[0] >= 0 && tick == first[0] + 29) {
                helper.assertTrue(!r.canRewind(), "29 ticks after a rewind it can't wind back");
                helper.assertTrue(a[0].isAlive() && r.anchorOf(helper.getLevel()) == a[0], "its anchor is still whole");
                kill(helper, r);
                helper.assertTrue(r.isDeadOrDying() && r.rewinds() == 1, "the second kill inside 30 ticks is final, rewinds " + r.rewinds());
                helper.assertTrue(a[0].isRemoved(), "and its anchor shatters with it");
                helper.succeed();
            }
        });
    }

    /** Once per 30 ticks, not once ever: a kill exactly 30 ticks after a rewind winds it back again. */
    private static void oncePerSixty(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 5.5);
        long[] first = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 70) {
                kill(helper, r);
                helper.assertTrue(r.isAlive() && r.rewinds() == 1, "the first kill winds it back");
                first[0] = tick;
            } else if (first[0] >= 0 && tick == first[0] + 29) {
                helper.assertTrue(!r.canRewind(), "29 ticks on it can't wind back yet");
            } else if (first[0] >= 0 && tick == first[0] + 30) {
                helper.assertTrue(r.canRewind() && r.core().stage() == Recurrent.STAGE_LIT, "30 ticks on its face is lit again");
                kill(helper, r);
                helper.assertTrue(r.isAlive() && r.rewinds() == 2, "a kill 30 ticks after a rewind winds it back again, rewinds " + r.rewinds());
                helper.succeed();
            }
        });
    }

    // ---- the anchor ------------------------------------------------------------------------------------------------

    /**
     * The anchor has 4 health: a hit of 3 leaves it at 1; a hit of 1 more breaks it (it is gone, no death). Its
     * Recurrent's face goes dark and cracks, and its first kill is then final.
     */
    private static void anchorBreak(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            Recurrent a = r.summonAnchor();
            helper.assertTrue(a != null, "it has an anchor");
            a.damageCooldownTime = 0;
            a.hurtServer(helper.getLevel(), a.damageSources().magic(), 3.0F);
            helper.assertTrue(a.isAlive() && near(a.getHealth(), 1.0), "a hit of 3 leaves the anchor at 1, got " + a.getHealth());
            a.damageCooldownTime = 0;
            a.hurtServer(helper.getLevel(), a.damageSources().magic(), 1.0F);
            helper.assertTrue(a.isRemoved(), "a hit of 1 more breaks it");
            helper.assertTrue(r.anchorBroken() && !r.canRewind(), "its Recurrent's anchor is broken: it can't wind back");
            helper.assertTrue(r.core().flag(Recurrent.FLAG_BROKEN), "its face shows the spread cracks");
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(r.core().stage() == Recurrent.STAGE_DARK, "its face is dark");
            kill(helper, r);
            helper.assertTrue(r.isDeadOrDying() && r.rewinds() == 0, "the first kill is final");
            helper.succeed();
        });
    }

    /**
     * No anchor without a fight: one at peace for 30 ticks has none. Given a foe it unfolds one on its next tick: an
     * anchor of its own type, flagged, in its look (gilded here), 4 health of 4, a 0.45 x 0.7 hitbox, weightless, worth no
     * experience, the two linked both ways. Only one.
     */
    private static void anchor(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 4.5);
        r.core().setLook("gilded_recurrent", true);
        Pig pig = pig(helper, 5.5, 1, 8.5);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(r.anchorOf(helper.getLevel()) == null, "at peace it has no anchor");
            r.setTarget(pig);
        });
        helper.runAtTickTime(33, () -> {
            ServerLevel level = helper.getLevel();
            Recurrent a = r.anchorOf(level);
            helper.assertTrue(a != null, "with a foe it unfolds its anchor");
            helper.assertTrue(a.anchor() && !r.anchor() && a.getType() == r.getType(), "the anchor is flagged, of its own type");
            helper.assertTrue(a.look().equals("gilded_recurrent"), "in its look, got " + a.look());
            helper.assertTrue(near(a.getMaxHealth(), 4.0) && near(a.getHealth(), 4.0), "4 health of 4, got " + a.getHealth() + "/" + a.getMaxHealth());
            helper.assertTrue(Math.abs(a.getBbWidth() - 0.45F) < 0.01F && Math.abs(a.getBbHeight() - 0.7F) < 0.01F,
                    "a 0.45 x 0.7 hitbox, got " + a.getBbWidth() + " x " + a.getBbHeight());
            helper.assertTrue(a.isNoGravity() && a.getExperienceReward(level, null) == 0, "weightless, no experience");
            helper.assertTrue(a.ownerOf(level) == r, "linked both ways");
            helper.assertTrue(r.summonAnchor() == a, "only one");
            int anchors = level.getEntitiesOfClass(Recurrent.class, helper.getBounds(), Recurrent::anchor).size();
            helper.assertTrue(anchors == 1, "one anchor in the arena, " + anchors);
            helper.succeed();
        });
    }

    /** A player's arrow breaks the anchor (it is a target a player can hit), and its Recurrent turns on the archer. */
    private static void anchorArrow(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 5.5);
        ServerPlayer archer = TestPlayers.survivor(helper, new Vec3(1.5, 1, 1.5));
        Recurrent[] a = {null};
        helper.runAtTickTime(2, () -> {
            a[0] = r.summonAnchor();
            helper.assertTrue(a[0] != null, "it has an anchor");
        });
        helper.runAtTickTime(20, () -> {
            // from 2.5 blocks out on the far side of the anchor from its Recurrent, so nothing else is in the way
            Vec3 aim = a[0].position().add(0.0, a[0].getBbHeight() * 0.5, 0.0);
            Vec3 out = aim.subtract(r.position()).multiply(1.0, 0.0, 1.0);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
            Vec3 from = aim.add(out.scale(2.5)).add(0.0, 0.3, 0.0);
            Arrow arrow = new Arrow(helper.getLevel(), archer, new ItemStack(Items.ARROW), null);
            arrow.setPos(from);
            Vec3 d = aim.subtract(from);
            arrow.shoot(d.x, d.y, d.z, 3.0F, 0.0F);
            arrow.setCritArrow(false);
            helper.getLevel().addFreshEntity(arrow);
            TestPlayers.whenDone(helper, arrow::discard);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(a[0] != null && a[0].isRemoved(), "the arrow broke the anchor");
            helper.assertTrue(r.anchorBroken(), "its Recurrent's anchor is broken");
            helper.assertTrue(r.getTarget() == archer, "and it turns on the archer");
        });
    }

    /**
     * The leash: an anchor set 8.9 blocks from its Recurrent is within 6 a tick later, and for 150 ticks of drifting
     * (with a foe and without) it is never farther than 6.
     */
    private static void anchorLeash(GameTestHelper helper) {
        Recurrent r = still(helper, 2.5, 5.5);
        Pig pig = pig(helper, 6.5, 1, 5.5);
        Recurrent[] a = {null};
        long[] set = {-1};
        double[] worst = {0.0};
        helper.runAtTickTime(2, () -> {
            a[0] = r.summonAnchor();
            helper.assertTrue(a[0] != null, "it has an anchor");
            Vec3 far = helper.absoluteVec(new Vec3(10.5, 5, 5.5));
            a[0].setPos(far.x, far.y, far.z);
            set[0] = helper.getTick();
            helper.assertTrue(a[0].position().distanceTo(r.position()) > 8.0, "set 8.9 blocks off");
        });
        helper.runAtTickTime(80, () -> r.setTarget(pig));
        helper.onEachTick(() -> {
            if (set[0] < 0 || helper.getTick() <= set[0]) return;
            double d = a[0].position().distanceTo(r.position());
            worst[0] = Math.max(worst[0], d);
            helper.assertTrue(d <= 6.0 + 1.0E-6, "the anchor stays within 6 blocks, " + d + " at tick " + helper.getTick());
            if (helper.getTick() >= set[0] + 150) {
                helper.assertTrue(a[0].isAlive(), "it drifted the whole time");
                helper.succeed();
            }
        });
    }

    /**
     * Its cycle in a fight: in the guard it floats behind its Recurrent (more than a block on the far side from its foe,
     * a block or more up); 80 ticks into the cycle it drifts out to the side, 3 blocks across and under a block up, where
     * a sword reaches it.
     */
    private static void anchorDrift(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 4.5);
        Pig pig = pig(helper, 5.5, 1, 7.5);
        Recurrent[] a = {null};
        helper.runAtTickTime(2, () -> {
            r.setTarget(pig);
            a[0] = r.summonAnchor();
            helper.assertTrue(a[0] != null, "it has an anchor");
            a[0].setDrift(40);
        });
        helper.runAtTickTime(40, () -> {                                    // drift 78: the guard
            Vec3 off = a[0].position().subtract(r.position());
            helper.assertTrue(off.z < -1.2 && Math.abs(off.x) < 0.3, "in the guard it is behind its back, away from the pig, " + off);
            helper.assertTrue(off.y > 1.0, "and up by its head, " + off.y);
        });
        helper.runAtTickTime(80, () -> {                                    // drift 118: out to the side
            Vec3 off = a[0].position().subtract(r.position());
            double across = Math.abs(off.x);
            helper.assertTrue(across > 2.9, "out of the guard it drifts 3 blocks to its side, " + off);
            helper.assertTrue(off.y < 1.0, "low enough for a sword, " + off.y);
            helper.succeed();
        });
    }

    // ---- nothing after death, and the edges of the rewind ------------------------------------------------------------

    /**
     * Nothing after death: with its anchor broken, it is killed 4 ticks into its wind-up. It stays dead, never winds back,
     * and the pig in reach is never struck.
     */
    private static void diesMidTell(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 4.5);
        Pig pig = pig(helper, 5.5, 1, 5.5);
        helper.runAtTickTime(2, () -> {
            Recurrent a = r.summonAnchor();
            a.damageCooldownTime = 0;
            a.hurtServer(helper.getLevel(), a.damageSources().magic(), 10.0F);
            helper.assertTrue(r.anchorBroken(), "its anchor is broken");
            helper.assertTrue(r.tell(pig), "it winds up at the pig");
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(r.core().in(Step.TELL), "4 ticks into its wind-up");
            kill(helper, r);
            helper.assertTrue(r.isDeadOrDying(), "dead");
        });
        helper.onEachTick(() -> {
            if (helper.getTick() > 6) {
                helper.assertTrue(pig.getHealth() >= 100.0F, "a dead Recurrent strikes nothing");
                helper.assertTrue(r.rewinds() == 0, "and never winds back");
            }
        });
        helper.runAtTickTime(36, () -> {
            helper.assertTrue(r.strikes() == 0 && (r.isRemoved() || r.isDeadOrDying()), "it stayed dead");
            helper.succeed();
        });
    }

    /** A kill that ignores invulnerability (a kill command) is final even with a whole anchor, which goes with it. */
    private static void killCommand(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 5.5);
        helper.runAtTickTime(70, () -> {
            Recurrent a = r.summonAnchor();
            helper.assertTrue(a != null && r.canRewind(), "an anchor, and its face lit");
            r.kill(helper.getLevel());
            helper.assertTrue(r.isDeadOrDying() && r.rewinds() == 0, "a kill command is final");
            helper.assertTrue(a.isRemoved(), "its anchor goes with it");
            helper.succeed();
        });
    }

    /** A listener that cancels the "rewind" step makes the kill final. */
    private static void rewindVeto(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 5.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == r && e.step().equals("rewind")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(70, () -> {
            kill(helper, r);
            helper.assertTrue(r.isDeadOrDying() && r.rewinds() == 0, "a vetoed rewind is a kill");
            helper.succeed();
        });
    }

    /**
     * Its old spot walled up: stone fills where its feet and head were 60 ticks before; it lands on top of the stone in
     * that column, its body clear of any block, never inside one.
     */
    private static void rewindSafe(GameTestHelper helper) {
        Recurrent r = still(helper, 3.5, 5.5);
        helper.runAtTickTime(20, () -> move(helper, r, 7.5, 5.5));
        helper.runAtTickTime(60, () -> {
            helper.setBlock(new BlockPos(3, 1, 5), Blocks.STONE);
            helper.setBlock(new BlockPos(3, 2, 5), Blocks.STONE);
        });
        helper.runAtTickTime(75, () -> {                                    // 60 ticks before: at x 3.5, now walled up
            kill(helper, r);
            helper.assertTrue(r.isAlive() && r.rewinds() == 1, "it wound back");
            Vec3 at = rel(helper, r);
            helper.assertTrue(near(at.x, 3.5) && near(at.z, 5.5), "in its old column, at " + at);
            helper.assertTrue(near(at.y, 3.0), "on top of the stone, at y " + at.y);
            helper.assertTrue(helper.getLevel().noCollision(r, r.getBoundingBox()), "its body clear of any block");
            helper.succeed();
        });
    }

    /**
     * Save and load: a Recurrent and its anchor, saved and loaded, find each other again by id; the anchor is still an
     * anchor of 4 health, small and weightless, and the Recurrent still can't wind back inside 30 ticks of the rewind it
     * made before it was saved. A Recurrent whose anchor broke loads with it broken and, given a foe, never unfolds another.
     */
    private static void saveLoad(GameTestHelper helper) {
        Recurrent r = still(helper, 3.5, 5.5);
        Recurrent broken = still(helper, 8.5, 5.5);
        Pig pig = pig(helper, 8.5, 1, 9.5);
        Recurrent[] loaded = {null, null, null};
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(r.summonAnchor() != null, "it has an anchor");
            kill(helper, r);
            helper.assertTrue(r.rewinds() == 1, "it wound back before the save");
            Recurrent b = broken.summonAnchor();
            b.damageCooldownTime = 0;
            b.hurtServer(helper.getLevel(), b.damageSources().magic(), 10.0F);
            helper.assertTrue(broken.anchorBroken(), "the other's anchor is broken");
        });
        helper.runAtTickTime(80, () -> {
            ServerLevel level = helper.getLevel();
            Recurrent a = r.anchorOf(level);
            CompoundTag owner = save(level, r), anchor = save(level, a), other = save(level, broken);
            a.discard();
            r.discard();
            broken.discard();
            loaded[1] = load(helper, anchor);
            loaded[0] = load(helper, owner);
            loaded[2] = load(helper, other);
        });
        helper.runAtTickTime(82, () -> {
            ServerLevel level = helper.getLevel();
            Recurrent o = loaded[0], a = loaded[1], b = loaded[2];
            helper.assertTrue(a.anchor() && !o.anchor(), "the anchor loads as an anchor, the Recurrent as one");
            helper.assertTrue(o.anchorOf(level) == a && a.ownerOf(level) == o, "they find each other again");
            helper.assertTrue(near(a.getMaxHealth(), 4.0) && a.isNoGravity() && Math.abs(a.getBbWidth() - 0.45F) < 0.01F,
                    "the anchor keeps 4 health, its size and no weight");
            helper.assertTrue(!o.canRewind(), "12 ticks after its rewind it still can't wind back");
            helper.assertTrue(b.anchorBroken() && b.core().flag(Recurrent.FLAG_BROKEN), "the broken anchor stays broken");
            b.setTarget(pig);
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(loaded[2].anchorOf(helper.getLevel()) == null, "a broken anchor never comes back");
            helper.assertTrue(loaded[0].anchorOf(helper.getLevel()) == loaded[1] && loaded[1].isAlive(), "the loaded anchor stays with it");
            helper.succeed();
        });
    }

    private static CompoundTag save(ServerLevel level, Recurrent r) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        r.saveWithoutId(out);
        return out.buildResult();
    }

    private static Recurrent load(GameTestHelper helper, CompoundTag tag) {
        ServerLevel level = helper.getLevel();
        Recurrent r = ModMobs.RECURRENT.get().create(level, EntitySpawnReason.LOAD);
        r.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        helper.assertTrue(level.addFreshEntity(r), "the loaded one is back in the level");
        TestPlayers.whenDone(helper, r::discard);
        return r;
    }

    // ---- the row ---------------------------------------------------------------------------------------------------

    /** Health 30, attack 8.5, armour 2, a 0.7 x 1.9 hitbox, 12 experience. */
    private static void stats(GameTestHelper helper) {
        Recurrent r = still(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(r.getMaxHealth() == 30.0F && r.getHealth() == 30.0F, "health 30, got " + r.getHealth() + "/" + r.getMaxHealth());
            helper.assertTrue(r.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8.5, "attack 8.5, got " + r.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(r.getArmorValue() == 2, "armour 2, got " + r.getArmorValue());
            helper.assertTrue(Math.abs(r.getBbWidth() - 0.7F) < 0.01F && Math.abs(r.getBbHeight() - 1.9F) < 0.01F,
                    "a 0.7 x 1.9 hitbox, got " + r.getBbWidth() + " x " + r.getBbHeight());
            helper.assertTrue(r.getExperienceReward(helper.getLevel(), null) == 12, "12 experience");
            helper.succeed();
        });
    }

    /** Home: the End barrens (not the highlands, midlands or main island), weight 3, alone, a monster; the Gilded Recurrent; time sand. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("recurrent"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "its home holds the End barrens");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.END_MIDLANDS) || b.is(Biomes.THE_END)),
                "never the highlands, midlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/recurrent")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.RECURRENT.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 3, "spawn weight 3, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.RECURRENT.get().getCategory() == MobCategory.MONSTER, "a monster");
        RosterStats.Entry row = RosterStats.get("recurrent");
        helper.assertTrue(row.secondLook().equals("gilded_recurrent"), "the Gilded Recurrent is its second look");
        helper.assertTrue(row.material().equals("time_sand"), "it drops time sand");
        helper.succeed();
    }

    /** Its spawn rule (on the ground) takes a spot on end stone and refuses one with nothing under it. */
    private static void spawnRule(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.END_STONE);
        RosterStats.Entry row = RosterStats.get("recurrent");
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground mob, place " + row.place());
        helper.assertTrue(TeemingSpawns.placeOk(row, ModMobs.RECURRENT.get(), helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))),
                "a spot on end stone takes a spawn");
        helper.assertTrue(!TeemingSpawns.placeOk(row, ModMobs.RECURRENT.get(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))),
                "a spot with nothing under it does not");
        helper.succeed();
    }
}
