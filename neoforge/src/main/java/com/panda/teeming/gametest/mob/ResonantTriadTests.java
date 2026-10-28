package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.end.ResonantTriad;
import com.panda.teeming.mob.end.ResonantTriad.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
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
 * Resonant Triad: health 16 each, attack 4, armour 0, a 0.6 x 0.9 hitbox, in groups of three. While all three live only
 * the singer takes damage; the song passes every 60 ticks, with a 10 tick tell on the next one; killing the singer
 * breaks the chord and the other two take damage. Each zaps for its attack (through armour, without knockback) after an
 * 8 tick crackle, within 5 blocks, then waits 40 ticks. Every number is checked against a plain value.
 *
 * <p>Shards here are NoAI unless a test needs the zap: the song, the link and the shield run in the entity's tick, so
 * they work without AI. Zapping shards are held in place, have a follow range of 1 (so they never pick up a survivor in
 * the arena next door) and are handed a NoAI pig with 100 health. Everything stays inside its own arena.
 */
public final class ResonantTriadTests {
    private ResonantTriadTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("resonant_triad_tell", ResonantTriadTests::tell);
        out.accept("resonant_triad_counter", ResonantTriadTests::counter);
        out.accept("resonant_triad_failure", ResonantTriadTests::failure);
        out.accept("resonant_triad_links", ResonantTriadTests::links);
        out.accept("resonant_triad_complete", ResonantTriadTests::complete);
        out.accept("resonant_triad_egg", ResonantTriadTests::egg);
        out.accept("resonant_triad_only_singer", ResonantTriadTests::onlySinger);
        out.accept("resonant_triad_rotation", ResonantTriadTests::rotation);
        TeemingGameTests.timeLimit("resonant_triad_rotation", 220);
        out.accept("resonant_triad_projectile", ResonantTriadTests::projectile);
        out.accept("resonant_triad_void", ResonantTriadTests::bypass);
        out.accept("resonant_triad_shield_cancel", ResonantTriadTests::shieldCancel);
        out.accept("resonant_triad_lost", ResonantTriadTests::lost);
        TeemingGameTests.timeLimit("resonant_triad_lost", 160);
        out.accept("resonant_triad_save_load", ResonantTriadTests::saveLoad);
        out.accept("resonant_triad_zap", ResonantTriadTests::zap);
        out.accept("resonant_triad_zap_tell", ResonantTriadTests::zapTell);
        out.accept("resonant_triad_zap_range", ResonantTriadTests::zapRange);
        out.accept("resonant_triad_zap_step_out", ResonantTriadTests::zapStepOut);
        out.accept("resonant_triad_zap_cooldown", ResonantTriadTests::zapCooldown);
        out.accept("resonant_triad_armour", ResonantTriadTests::armour);
        out.accept("resonant_triad_dies_mid_tell", ResonantTriadTests::diesMidTell);
        out.accept("resonant_triad_after_death", ResonantTriadTests::afterDeath);
        out.accept("resonant_triad_spawn_rule", ResonantTriadTests::spawnRule);
        out.accept("resonant_triad_stats", ResonantTriadTests::stats);
        out.accept("resonant_triad_home", ResonantTriadTests::home);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    /** A shard with the base look at full health, NoAI and follow range 1 unless a test changes them; removed when the test ends. */
    private static ResonantTriad shard(GameTestHelper helper, double x, double y, double z) {
        ResonantTriad t = helper.spawn(ModMobs.RESONANT_TRIAD.get(), new Vec3(x, y, z));
        t.core().setLook(LookDef.BASE, true);
        t.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        t.setNoAi(true);
        TestPlayers.whenDone(helper, t::discard);
        return t;
    }

    /** Three NoAI shards in a triangle 3 up, linked into one whole triad whose song started {@code ago} ticks before now. */
    private static ResonantTriad[] triad(GameTestHelper helper, int ago) {
        ResonantTriad a = shard(helper, 4.5, 3, 4.5), b = shard(helper, 7.5, 3, 4.5), c = shard(helper, 6.0, 3, 7.5);
        ResonantTriad.link(a, b, c, helper.getLevel().getGameTime() - ago);
        return new ResonantTriad[]{a, b, c};
    }

    /** A NoAI pig with 100 health at (x, z) on the floor; removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A shard with its AI on, held in place 2 up, alone (no shield), its zap ready, hunting {@code pig}. */
    private static ResonantTriad zapper(GameTestHelper helper, double x, double z, Pig pig) {
        ResonantTriad t = shard(helper, x, 2, z);
        t.setNoAi(false);
        t.hold(true);
        t.setZapWait(0);
        t.setTarget(pig);
        return t;
    }

    /** Hurts {@code t} with {@code source} for {@code amount}, its hurt cooldown cleared, and returns what it lost. */
    private static float lost(ResonantTriad t, DamageSource source, float amount) {
        t.damageCooldownTime = 0;
        float before = t.getHealth();
        t.hurtServer((ServerLevel) t.level(), source, amount);
        return before - t.getHealth();
    }

    private static float lost(ResonantTriad t, float amount) {
        return lost(t, t.damageSources().generic(), amount);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    /** The member whose stage is {@code stage}, or null. */
    private static ResonantTriad withStage(ResonantTriad[] all, int stage) {
        for (ResonantTriad t : all) if (t.core().stage() == stage) return t;
        return null;
    }

    private static int count(ResonantTriad[] all, int stage) {
        int n = 0;
        for (ResonantTriad t : all) if (t.core().stage() == stage) n++;
        return n;
    }

    /** Discards every shard in the test's bounds when it ends (for shards the triad made itself). */
    private static void sweepWhenDone(GameTestHelper helper) {
        AABB box = helper.getBounds();
        ServerLevel level = helper.getLevel();
        TestPlayers.whenDone(helper, () -> level.getEntitiesOfClass(ResonantTriad.class, box).forEach(ResonantTriad::discard));
    }

    // ---- tell, counter, failure --------------------------------------------------------------------------------------

    /**
     * The tell before the song passes: the next one brightens (stage 1) exactly 10 ticks before it takes the song; through
     * the tell the old singer still sings (the one gold, vulnerable one) and the brightening one still shrugs a hit off.
     * Then the brightening one sings and the old one is dim.
     */
    private static void tell(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 30);                // seat 0 sings; the tell starts 20 ticks from now
        long[] riseAt = {-1}, passAt = {-1};
        boolean[] checked = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            ResonantTriad rising = withStage(all, ResonantTriad.STAGE_RISE), singer = withStage(all, ResonantTriad.STAGE_SING);
            if (riseAt[0] < 0 && rising != null) {
                riseAt[0] = now;
                helper.assertTrue(rising == all[1], "the next seat brightens, got seat " + rising.seat());
                helper.assertTrue(singer == all[0], "seat 0 still sings through the tell");
            }
            if (riseAt[0] >= 0 && passAt[0] < 0) {
                if (singer == all[1]) {
                    passAt[0] = now;
                    helper.assertTrue(now - riseAt[0] == 10, "the tell lasts 10 ticks, got " + (now - riseAt[0]));
                    helper.assertTrue(all[0].core().stage() == ResonantTriad.STAGE_DIM, "the old singer is dim");
                } else if (!checked[0] && now - riseAt[0] == 5) {
                    checked[0] = true;
                    helper.assertTrue(near(lost(all[1], 4.0F), 0.0), "the brightening one still shrugs a hit off");
                    helper.assertTrue(all[0].vulnerable() && !all[1].vulnerable(), "only the old singer is vulnerable in the tell");
                    helper.assertTrue(count(all, ResonantTriad.STAGE_SING) == 1, "one singer");
                }
            }
            if (passAt[0] >= 0 && now >= passAt[0] + 2) {
                helper.assertTrue(checked[0], "the hit in the tell was tried");
                helper.assertTrue(all[1].vulnerable() && !all[0].vulnerable() && !all[2].vulnerable(), "seat 1 alone is vulnerable now");
                helper.succeed();
            }
        });
    }

    /**
     * The counter: kill the singer while it sings and the chord breaks. Before, a 5 on a dim one does nothing; the singer
     * dies of 16; the two left are broken (stage 3, no longer shielded, no gold) and each loses exactly 5 from a 5.
     */
    private static void counter(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 10);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(near(lost(all[1], 5.0F), 0.0), "a dim one shrugs a 5 off before");
            helper.assertTrue(all[0].singing(), "seat 0 sings");
            lost(all[0], 16.0F);
            helper.assertTrue(all[0].isDeadOrDying(), "16 kills the singer");
            for (ResonantTriad t : new ResonantTriad[]{all[1], all[2]}) {
                helper.assertTrue(t.broken() && !t.shielded() && !t.whole() && !t.vulnerable(), "the chord broke for seat " + t.seat());
                float l = lost(t, 5.0F);
                helper.assertTrue(near(l, 5.0), "seat " + t.seat() + " takes a 5 in full now, lost " + l);
            }
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(all[1].core().stage() == ResonantTriad.STAGE_BROKEN && all[2].core().stage() == ResonantTriad.STAGE_BROKEN,
                    "both show the broken stage");
            helper.assertTrue(!all[1].core().flag(ResonantTriad.FLAG_SHIELDED), "no longer shielded on clients either");
            helper.succeed();
        });
    }

    /**
     * Failing the counter: hit the dim ones and nothing happens. Twenty blows of 10 on the two dim shards take nothing,
     * each is counted as shrugged off, the triad stays whole and it turns on its attacker.
     */
    private static void failure(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 10);
        Pig pig = pig(helper, 6.0, 9.5);
        helper.runAtTickTime(3, () -> {
            for (int i = 0; i < 10; i++) {
                for (ResonantTriad t : new ResonantTriad[]{all[1], all[2]}) {
                    helper.assertTrue(near(lost(t, t.damageSources().mobAttack(pig), 10.0F), 0.0), "a dim one takes nothing");
                }
            }
            helper.assertTrue(all[1].shrugs() == 10 && all[2].shrugs() == 10, "each shrugged off 10, " + all[1].shrugs() + " and " + all[2].shrugs());
            helper.assertTrue(near(all[1].getHealth(), 16.0) && near(all[2].getHealth(), 16.0), "both still at 16");
            helper.assertTrue(all[0].whole() && all[1].whole() && all[2].whole(), "the triad is still whole");
            helper.assertTrue(all[1].getTarget() == pig, "it turns on whoever struck it");
            helper.succeed();
        });
    }

    // ---- the link ----------------------------------------------------------------------------------------------------

    /**
     * The group of three links at spawn through the spawn data the spawner hands on: the first starts a triad, the next two
     * take seats 1 and 2, the first's look and its song. On the next ticks each finds the other two, the three are whole,
     * and exactly one sings.
     */
    private static void links(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResonantTriad a = shard(helper, 4.5, 3, 4.5), b = shard(helper, 7.5, 3, 4.5), c = shard(helper, 6.0, 3, 7.5);
        var difficulty = level.getCurrentDifficultyAt(helper.absolutePos(new BlockPos(6, 3, 6)));
        SpawnGroupData data = a.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, null);
        b.core().setLook("rose_triad".equals(a.look()) ? LookDef.BASE : "rose_triad", true);
        data = b.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, data);
        data = c.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, data);
        helper.assertTrue(a.triad().equals(b.triad()) && b.triad().equals(c.triad()), "one triad");
        helper.assertTrue(a.seat() == 0 && b.seat() == 1 && c.seat() == 2, "seats 0, 1, 2, got " + a.seat() + b.seat() + c.seat());
        helper.assertTrue(a.epoch() == b.epoch() && b.epoch() == c.epoch(), "one song");
        helper.assertTrue(b.look().equals(a.look()) && c.look().equals(a.look()), "the first's look, " + a.look() + ": " + b.look() + ", " + c.look());
        helper.runAtTickTime(3, () -> {
            for (ResonantTriad t : new ResonantTriad[]{a, b, c}) {
                helper.assertTrue(t.members().size() == 2 && t.formed() && t.whole(), "seat " + t.seat() + " finds the other two");
            }
            int singers = (a.singing() ? 1 : 0) + (b.singing() ? 1 : 0) + (c.singing() ? 1 : 0);
            helper.assertTrue(singers == 1, "exactly one sings, " + singers);
            helper.succeed();
        });
    }

    /** A natural group the spawner seated only in part (two of three): 20 ticks on, the first fills the empty seat beside itself. */
    private static void complete(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        sweepWhenDone(helper);
        ResonantTriad a = shard(helper, 5.0, 3, 5.0), b = shard(helper, 7.0, 3, 5.0);
        var difficulty = level.getCurrentDifficultyAt(helper.absolutePos(new BlockPos(6, 3, 6)));
        b.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, a.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, null));
        helper.runAtTickTime(10, () -> helper.assertTrue(a.members().size() == 1 && !a.formed(), "two of three, not yet whole"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(a.members().size() == 2, "the third seat is filled, " + a.members().size());
            ResonantTriad third = a.members().stream().filter(m -> m != b).findFirst().orElseThrow();
            helper.assertTrue(third.seat() == 2 && third.triad().equals(a.triad()) && third.look().equals(a.look()), "seat 2 of the same triad");
            helper.assertTrue(third.distanceTo(a) <= 2.6, "beside the first, " + third.distanceTo(a));
            helper.assertTrue(a.whole() && b.whole() && third.whole(), "whole");
            helper.succeed();
        });
    }

    /**
     * Shards from spawn eggs: the first comes alone (seat 0, no shield: it takes a 3); the second and third join it (seats 1
     * and 2), and the three are whole; a fourth beside them finds no free seat and comes alone.
     */
    private static void egg(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var difficulty = level.getCurrentDifficultyAt(helper.absolutePos(new BlockPos(6, 3, 6)));
        ResonantTriad a = shard(helper, 6.0, 3, 6.0);
        a.finalizeSpawn(level, difficulty, EntitySpawnReason.SPAWN_ITEM_USE, null);
        ResonantTriad[] more = {null, null, null};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(a.seat() == 0 && a.members().isEmpty() && !a.whole(), "the first is alone");
            helper.assertTrue(near(lost(a, 3.0F), 3.0), "alone, it takes a 3");
            more[0] = shard(helper, 7.5, 3, 6.0);
            more[0].finalizeSpawn(level, difficulty, EntitySpawnReason.SPAWN_ITEM_USE, null);
            more[1] = shard(helper, 6.0, 3, 7.5);
            more[1].finalizeSpawn(level, difficulty, EntitySpawnReason.SPAWN_ITEM_USE, null);
            helper.assertTrue(more[0].triad().equals(a.triad()) && more[1].triad().equals(a.triad()), "both join the first");
            helper.assertTrue(more[0].seat() == 1 && more[1].seat() == 2, "seats 1 and 2, got " + more[0].seat() + more[1].seat());
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(a.whole() && more[0].whole() && more[1].whole(), "the three are whole");
            more[2] = shard(helper, 4.5, 3, 6.0);
            more[2].finalizeSpawn(level, difficulty, EntitySpawnReason.SPAWN_ITEM_USE, null);
            helper.assertTrue(!more[2].triad().equals(a.triad()) && more[2].seat() == 0, "a fourth comes alone");
        });
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(!more[2].whole() && near(lost(more[2], 3.0F), 3.0), "the fourth has no shield");
            helper.succeed();
        });
    }

    // ---- the shield --------------------------------------------------------------------------------------------------

    /** While all three live only the singer takes damage: a 3 on each takes 3 from the singer and nothing from the others. */
    private static void onlySinger(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 70);                  // seat 1 sings (70 ticks in: its 10th tick)
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(all[1].singing() && all[1].vulnerable(), "seat 1 sings");
            float s0 = lost(all[0], 3.0F), s1 = lost(all[1], 3.0F), s2 = lost(all[2], 3.0F);
            helper.assertTrue(near(s1, 3.0), "the singer loses 3, " + s1);
            helper.assertTrue(near(s0, 0.0) && near(s2, 0.0), "the others lose nothing, " + s0 + " and " + s2);
            helper.assertTrue(all[1].core().stage() == ResonantTriad.STAGE_SING && all[0].core().flag(ResonantTriad.FLAG_SHIELDED)
                    && !all[1].core().flag(ResonantTriad.FLAG_SHIELDED), "clients see the singer's gold and the others' shield");
            helper.succeed();
        });
    }

    /** The singer rotates every 60 ticks: the song passes seat 0, 1, 2, 0, exactly 60 ticks apart. */
    private static void rotation(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 50);
        List<long[]> passes = new ArrayList<>();                  // {tick, seat}
        int[] last = {-1};
        helper.onEachTick(() -> {
            ResonantTriad singer = withStage(all, ResonantTriad.STAGE_SING);
            int seat = singer == null ? -1 : singer.seat();
            helper.assertTrue(count(all, ResonantTriad.STAGE_SING) <= 1, "never two singers");
            if (seat != last[0] && last[0] != -1) passes.add(new long[]{helper.getTick(), seat});
            last[0] = seat;
            if (passes.size() == 3) {
                helper.assertTrue(passes.get(0)[1] == 1 && passes.get(1)[1] == 2 && passes.get(2)[1] == 0,
                        "seats in turn: " + passes.get(0)[1] + passes.get(1)[1] + passes.get(2)[1]);
                long g1 = passes.get(1)[0] - passes.get(0)[0], g2 = passes.get(2)[0] - passes.get(1)[0];
                helper.assertTrue(g1 == 60 && g2 == 60, "60 ticks apart, got " + g1 + " and " + g2);
                helper.succeed();
            }
        });
    }

    /** An arrow at a dim one bounces back off it and hurts nothing; an arrow at the singer hurts it. */
    private static void projectile(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 10);
        Arrow[] arrows = {null, null};
        helper.runAtTickTime(3, () -> {
            arrows[0] = arrow(helper, new Vec3(7.5, 3.4, 1.5), new Vec3(0, 0, 1.0));     // at seat 1 (dim), from 3 off
            arrows[1] = arrow(helper, new Vec3(4.5, 3.4, 1.5), new Vec3(0, 0, 1.0));     // at seat 0 (the singer)
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(near(all[1].getHealth(), 16.0) && all[1].shrugs() >= 1, "the dim one shrugged the arrow off, health "
                    + all[1].getHealth() + ", shrugs " + all[1].shrugs());
            helper.assertTrue(arrows[0].isAlive() && arrows[0].getDeltaMovement().z < 0.0, "the arrow bounced back, motion " + arrows[0].getDeltaMovement());
            helper.assertTrue(all[0].getHealth() < 16.0F, "the singer took the other arrow, health " + all[0].getHealth());
            helper.succeed();
        });
    }

    /** An arrow at {@code at}, no gravity, no owner, flying at {@code motion}; removed when the test ends. */
    private static Arrow arrow(GameTestHelper helper, Vec3 at, Vec3 motion) {
        Vec3 abs = helper.absoluteVec(at);
        Arrow arrow = new Arrow(helper.getLevel(), abs.x, abs.y, abs.z, new ItemStack(Items.ARROW), null);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(motion);
        helper.getLevel().addFreshEntity(arrow);
        TestPlayers.whenDone(helper, arrow::discard);
        return arrow;
    }

    /** Damage nothing can stop (falling out of the world) still kills a dim one, and that breaks the chord for the others. */
    private static void bypass(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 10);
        helper.runAtTickTime(3, () -> {
            lost(all[2], all[2].damageSources().fellOutOfWorld(), 100.0F);
            helper.assertTrue(all[2].isDeadOrDying(), "the void kills a dim one");
            helper.assertTrue(all[0].broken() && all[1].broken(), "and the chord breaks");
            helper.succeed();
        });
    }

    /** A vetoed "shield" step lets the hit in: a dim one loses the 4. */
    private static void shieldCancel(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 10);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == all[2] && e.step().equals("shield")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(near(lost(all[2], 4.0F), 4.0), "the vetoed shield lets a 4 in");
            helper.assertTrue(near(lost(all[1], 4.0F), 0.0), "the other dim one still shrugs");
            helper.succeed();
        });
    }

    /**
     * A member that goes missing (removed, not killed): for 100 ticks the other two stay whole and keep their shield (it may
     * only be unloaded), then the chord breaks and they take damage.
     */
    private static void lost(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 10);
        long[] goneAt = {-1};
        helper.runAtTickTime(3, () -> {
            all[2].discard();
            goneAt[0] = helper.getTick();
        });
        helper.runAtTickTime(90, () -> helper.assertTrue(!all[0].broken() && !all[1].broken(), "not broken 87 ticks after"));
        helper.succeedWhen(() -> {
            helper.assertTrue(all[0].broken() && all[1].broken(), "broken once 100 ticks pass");
            helper.assertTrue(helper.getTick() - goneAt[0] >= 100, "not before 100 ticks, " + (helper.getTick() - goneAt[0]));
            helper.assertTrue(near(lost(all[1], 3.0F), 3.0), "and it takes damage");
        });
    }

    /**
     * Save and load: the three, saved and loaded, find each other again by their triad's id, are whole with the same seats,
     * and the same seat sings as before the save. A broken one loads broken.
     */
    private static void saveLoad(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 70);                  // seat 1 sings
        ResonantTriad lone = shard(helper, 9.5, 3, 9.5);
        ResonantTriad[] loaded = {null, null, null, null};
        helper.runAtTickTime(3, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(all[1].singing(), "seat 1 sings before the save");
            lone.finalizeSpawn(level, level.getCurrentDifficultyAt(lone.blockPosition()), EntitySpawnReason.SPAWN_ITEM_USE, null);
            CompoundTag[] tags = new CompoundTag[4];
            for (int i = 0; i < 3; i++) tags[i] = save(level, all[i]);
            tags[3] = save(level, lone);
            for (ResonantTriad t : all) t.discard();
            lone.discard();
            for (int i = 0; i < 4; i++) loaded[i] = load(helper, tags[i]);
        });
        helper.runAtTickTime(6, () -> {
            for (int i = 0; i < 3; i++) {
                ResonantTriad t = loaded[i];
                helper.assertTrue(t.seat() == i && t.triad().equals(loaded[0].triad()), "seat " + i + " of the same triad");
                helper.assertTrue(t.members().size() == 2 && t.whole(), "seat " + i + " finds the others again");
            }
            helper.assertTrue(loaded[1].singing() && !loaded[0].singing() && !loaded[2].singing(), "seat 1 still sings");
            helper.assertTrue(near(lost(loaded[0], 3.0F), 0.0) && near(lost(loaded[1], 3.0F), 3.0), "the shield holds as before");
            helper.assertTrue(!loaded[3].whole() && loaded[3].seat() == 0, "the lone one loads alone");
            helper.succeed();
        });
    }

    private static CompoundTag save(ServerLevel level, ResonantTriad t) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        t.saveWithoutId(out);
        return out.buildResult();
    }

    private static ResonantTriad load(GameTestHelper helper, CompoundTag tag) {
        ServerLevel level = helper.getLevel();
        ResonantTriad t = ModMobs.RESONANT_TRIAD.get().create(level, EntitySpawnReason.LOAD);
        t.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        helper.assertTrue(level.addFreshEntity(t), "the loaded one is back in the level");
        TestPlayers.whenDone(helper, t::discard);
        return t;
    }

    // ---- the zap -----------------------------------------------------------------------------------------------------

    /**
     * The zap: its attack of 4 through armour (a pig has none), exactly 4 lost, and no knockback (a pig with no goals but
     * its physics doesn't move).
     */
    private static void zap(GameTestHelper helper) {
        Pig pig = pig(helper, 6.0, 8.5);
        pig.setNoAi(false);
        pig.removeFreeWill();
        ResonantTriad t = zapper(helper, 6.0, 5.5, pig);
        float[] hp = {100.0F};
        helper.onEachTick(() -> {
            if (pig.getHealth() < hp[0]) {
                float l = hp[0] - pig.getHealth();
                helper.assertTrue(near(l, 4.0), "the zap deals 4, " + l);
                helper.assertTrue(pig.getDeltaMovement().horizontalDistance() < 1.0E-3, "no knockback, " + pig.getDeltaMovement());
                helper.assertTrue(t.zaps() == 1 && t.core().in(Step.ZAP), "one zap landed");
                helper.succeed();
            }
        });
    }

    /**
     * The crackle: within reach it crackles for exactly 8 ticks and the pig loses nothing in them; the zap lands on the tick
     * the crackle ends.
     */
    private static void zapTell(GameTestHelper helper) {
        Pig pig = pig(helper, 6.0, 8.5);
        ResonantTriad t = zapper(helper, 6.0, 5.5, pig);
        long[] tellAt = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (tellAt[0] < 0 && t.core().in(Step.TELL)) tellAt[0] = now;
            if (tellAt[0] >= 0 && t.core().in(Step.TELL)) helper.assertTrue(near(pig.getHealth(), 100.0), "nothing lands in the crackle");
            if (tellAt[0] >= 0 && t.core().in(Step.ZAP)) {
                helper.assertTrue(now - tellAt[0] == 8, "the crackle lasts 8 ticks, " + (now - tellAt[0]));
                helper.assertTrue(near(pig.getHealth(), 96.0), "the zap lands as it ends, " + pig.getHealth());
                helper.succeed();
            }
        });
    }

    /** Out of reach (a gap just over 5 blocks) it never crackles; at a gap under 5 it does. */
    private static void zapRange(GameTestHelper helper) {
        Pig far = pig(helper, 2.5, 9.5), close = pig(helper, 9.5, 2.5);
        ResonantTriad out = zapper(helper, 2.5, 3.5, far), in = zapper(helper, 9.5, 6.5, close);
        helper.runAtTickTime(2, () -> {
            double gapOut = MobCore.gap(out, far), gapIn = MobCore.gap(in, close);
            helper.assertTrue(gapOut > 5.0 && gapOut < 5.5, "the far pig is just past 5, " + gapOut);
            helper.assertTrue(gapIn < 5.0, "the near pig is within 5, " + gapIn);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(out.zaps() == 0 && !out.core().in(Step.TELL) && near(far.getHealth(), 100.0), "out of reach: no crackle, no zap");
            helper.assertTrue(in.zaps() >= 1, "in reach: zapped");
            helper.succeed();
        });
    }

    /** Stepping out during the crackle: a pig moved to a gap past 5 mid-crackle takes nothing. */
    private static void zapStepOut(GameTestHelper helper) {
        Pig pig = pig(helper, 6.0, 8.0);
        ResonantTriad t = zapper(helper, 6.0, 5.0, pig);
        boolean[] moved = {false};
        helper.onEachTick(() -> {
            if (!moved[0] && t.core().in(Step.TELL) && t.core().elapsed() >= 3) {
                moved[0] = true;
                Vec3 to = helper.absoluteVec(new Vec3(6.0, 1, 11.0));
                pig.snapTo(to.x, to.y, to.z);
            }
            if (moved[0] && t.core().in(Step.ZAP)) {
                helper.assertTrue(MobCore.gap(t, pig) > 5.0, "the pig is out of reach, " + MobCore.gap(t, pig));
                helper.assertTrue(near(pig.getHealth(), 100.0) && t.zaps() == 0, "the zap misses");
                helper.succeed();
            }
        });
    }

    /** After a zap it waits exactly 40 ticks before the next crackle. */
    private static void zapCooldown(GameTestHelper helper) {
        Pig pig = pig(helper, 6.0, 8.5);
        ResonantTriad t = zapper(helper, 6.0, 5.5, pig);
        long[] zapAt = {-1};
        boolean[] wasTell = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            boolean tell = t.core().in(Step.TELL);
            if (zapAt[0] < 0 && t.core().in(Step.ZAP)) zapAt[0] = now;
            if (zapAt[0] >= 0 && tell && !wasTell[0]) {
                helper.assertTrue(now - zapAt[0] == 40, "the next crackle 40 ticks after the zap, " + (now - zapAt[0]));
                helper.succeed();
            }
            wasTell[0] = tell;
        });
    }

    /** Armour counts: a pig with 10 armour takes 4 x (1 - 8 / 25) = 2.72 from the zap. */
    private static void armour(GameTestHelper helper) {
        Pig pig = pig(helper, 6.0, 8.5);
        pig.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        zapper(helper, 6.0, 5.5, pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < 100.0F, "zapped");
            helper.assertTrue(Math.abs(100.0F - pig.getHealth() - 2.72F) < 0.01F, "armour 10 leaves 2.72, lost " + (100.0F - pig.getHealth()));
        });
    }

    /** Killed mid-crackle, it zaps nothing: the pig stays at 100. */
    private static void diesMidTell(GameTestHelper helper) {
        Pig pig = pig(helper, 6.0, 8.5);
        ResonantTriad t = zapper(helper, 6.0, 5.5, pig);
        long[] killedAt = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (killedAt[0] < 0 && t.core().in(Step.TELL) && t.core().elapsed() >= 4) {
                lost(t, 100.0F);
                killedAt[0] = now;
                helper.assertTrue(t.isDeadOrDying(), "killed mid-crackle");
            }
            if (killedAt[0] >= 0 && now >= killedAt[0] + 12) {
                helper.assertTrue(near(pig.getHealth(), 100.0) && t.zaps() == 0, "no zap from the dead, pig " + pig.getHealth());
                helper.succeed();
            }
        });
    }

    /**
     * Nothing after death: the killed singer stops singing at once (dim, not vulnerable), never counts as a member again,
     * and the song never comes back to the two left (no member sings, none brightens) through the next full round.
     */
    private static void afterDeath(GameTestHelper helper) {
        ResonantTriad[] all = triad(helper, 10);
        helper.runAtTickTime(3, () -> {
            lost(all[0], 16.0F);
            helper.assertTrue(!all[0].vulnerable() && all[0].core().stage() == ResonantTriad.STAGE_DIM, "the dead singer is dim at once");
        });
        helper.onEachTick(() -> {
            if (helper.getTick() <= 3) return;
            helper.assertTrue(!all[1].members().contains(all[0]) && !all[2].members().contains(all[0]), "the dead one is no member");
            helper.assertTrue(all[1].core().stage() != ResonantTriad.STAGE_SING && all[2].core().stage() != ResonantTriad.STAGE_SING
                    && all[1].core().stage() != ResonantTriad.STAGE_RISE && all[2].core().stage() != ResonantTriad.STAGE_RISE
                    && !all[1].vulnerable() && !all[2].vulnerable(), "the song never comes back");
            if (helper.getTick() >= 90) helper.succeed();
        });
    }

    // ---- the row -----------------------------------------------------------------------------------------------------

    /**
     * Its spawner rule (sky): open air with air above and below under the open sky is a spot; the floor is not, nor is air
     * under an island block (checked once the sky light under it has settled).
     */
    private static void spawnRule(GameTestHelper helper) {
        TeemingSpawner.Rule rule = TeemingSpawner.Rule.of(RosterStats.get("resonant_triad").spawner());
        helper.assertTrue(rule == TeemingSpawner.Rule.SKY, "its rule is the sky's, got " + rule);
        helper.assertTrue(rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(3, 4, 3))), "open air under the sky is a spot");
        helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))), "a spot on the floor is not");
        helper.setBlock(new BlockPos(8, 6, 8), Blocks.END_STONE);
        helper.succeedWhen(() -> helper.assertTrue(!rule.fits(helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))),
                "air under an island block is not"));
    }

    /** Health 16, attack 4, armour 0, a 0.6 x 0.9 hitbox, 10 experience; it flies. */
    private static void stats(GameTestHelper helper) {
        ResonantTriad t = shard(helper, 6.0, 3, 6.0);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(t.getMaxHealth() == 16.0F && t.getHealth() == 16.0F, "health 16, got " + t.getHealth() + "/" + t.getMaxHealth());
            helper.assertTrue(t.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4.0, "attack 4, got " + t.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(t.getArmorValue() == 0, "armour 0, got " + t.getArmorValue());
            helper.assertTrue(Math.abs(t.getBbWidth() - 0.6F) < 0.01F && Math.abs(t.getBbHeight() - 0.9F) < 0.01F,
                    "a 0.6 x 0.9 hitbox, got " + t.getBbWidth() + " x " + t.getBbHeight());
            helper.assertTrue(t.getExperienceReward(helper.getLevel(), null) == 10, "10 experience");
            helper.assertTrue(t.isNoGravity(), "it flies");
            helper.succeed();
        });
    }

    /**
     * Home: the end highlands' spawn list through its own modifier (weight 3, groups of exactly 3) and the Teeming spawner's
     * sky rule; never the main island by list; a monster of the End. Groups are always 3. The Rose Triad is its second look;
     * it drops void crystal.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        RosterStats.Entry row = RosterStats.get("resonant_triad");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.RESONANT_TRIAD.get().getCategory() == MobCategory.MONSTER, "a monster");
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("resonant_triad"));
        helper.assertTrue(tag.isPresent() && tag.get().stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)), "its home holds the end highlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.THE_END)), "never the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/resonant_triad")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.RESONANT_TRIAD.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 3, "spawn weight 3, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 3 && entry.value().count().maxInclusive() == 3,
                "groups of 3, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(row.spawner().equals("sky"), "the spawner's sky rule places it too, got " + row.spawner());
        Set<Integer> sizes = new HashSet<>();
        RandomSource random = RandomSource.create(89L);
        for (int i = 0; i < 50; i++) sizes.add(TeemingSpawner.groupSize(row, random));
        helper.assertTrue(sizes.equals(Set.of(3)), "the spawner's groups are always 3: " + sizes);
        List<LookDef> looks = Looks.of("resonant_triad");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("rose_triad")), "the Rose Triad is a look: " + looks);
        helper.assertTrue(row.material().equals("void_crystal") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("void_crystal")),
                "it drops void crystal, a registered item");
        helper.succeed();
    }
}
