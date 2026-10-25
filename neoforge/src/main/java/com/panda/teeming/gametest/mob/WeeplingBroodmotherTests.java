package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.WeeplingBroodmother;
import com.panda.teeming.mob.nether.WeeplingBroodmother.Form;
import com.panda.teeming.mob.nether.WeeplingBroodmother.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.ExperienceOrb;
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
 * Weepling Broodmother: every 200 ticks, with a target within 16 blocks, she squats for 20 ticks and lays 2 sacs of 1
 * health beside her; after 60 ticks each unpopped sac hatches a weepling with 6 health and 2 attack. At most 6 sacs and
 * weeplings at once. A hit pops a sac; a clutch popped whole adds 200 ticks to her wait. Kill her and her sacs burst at
 * once, her weeplings wither 100 ticks later.
 *
 * <p>The mother stands still in the middle of the arena (her phase machine and laying still run) with a follow range of
 * 1, so she never notices a survivor in another arena. A still pig is her target, given every tick; weeplings get the
 * pig as their target every tick too, and a follow range of 1.
 */
public final class WeeplingBroodmotherTests {
    private WeeplingBroodmotherTests() {}

    private static final Vec3 AT = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("weepling_broodmother_tell", WeeplingBroodmotherTests::tell);
        out.accept("weepling_broodmother_counter", WeeplingBroodmotherTests::popClutch);
        TeemingGameTests.timeLimit("weepling_broodmother_counter", 120);
        out.accept("weepling_broodmother_failure", WeeplingBroodmotherTests::hatches);
        TeemingGameTests.timeLimit("weepling_broodmother_failure", 220);
        out.accept("weepling_broodmother_clutch", WeeplingBroodmotherTests::clutch);
        out.accept("weepling_broodmother_every", WeeplingBroodmotherTests::every);
        TeemingGameTests.timeLimit("weepling_broodmother_every", 320);
        out.accept("weepling_broodmother_six", WeeplingBroodmotherTests::six);
        out.accept("weepling_broodmother_range", WeeplingBroodmotherTests::range);
        out.accept("weepling_broodmother_needs_target", WeeplingBroodmotherTests::needsTarget);
        out.accept("weepling_broodmother_death", WeeplingBroodmotherTests::motherDies);
        TeemingGameTests.timeLimit("weepling_broodmother_death", 160);
        out.accept("weepling_broodmother_orphan_sac", WeeplingBroodmotherTests::orphanSac);
        out.accept("weepling_broodmother_weepling", WeeplingBroodmotherTests::weeplingNumbers);
        out.accept("weepling_broodmother_brood_drops", WeeplingBroodmotherTests::broodDrops);
        out.accept("weepling_broodmother_reload", WeeplingBroodmotherTests::reload);
        out.accept("weepling_broodmother_family", WeeplingBroodmotherTests::family);
        out.accept("weepling_broodmother_sac_cancel", WeeplingBroodmotherTests::sacCancel);
        out.accept("weepling_broodmother_swing", WeeplingBroodmotherTests::swing);
        out.accept("weepling_broodmother_stats", WeeplingBroodmotherTests::stats);
        out.accept("weepling_broodmother_home", WeeplingBroodmotherTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A mother at {@code at} facing +Z that holds still, with a follow range of 1 and no lay of her own coming. */
    private static WeeplingBroodmother mother(GameTestHelper helper, Vec3 at) {
        WeeplingBroodmother m = helper.spawn(ModMobs.WEEPLING_BROODMOTHER.get(), at);
        m.stayPut();
        m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);    // 0 would mean no limit at all
        m.setUntilLay(100000);
        m.setYRot(0.0F);
        m.setYBodyRot(0.0F);
        m.setYHeadRot(0.0F);
        TestPlayers.whenDone(helper, () -> {
            for (WeeplingBroodmother b : m.brood()) b.discard();
        });
        return m;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    /**
     * Gives the mother the pig as her target every tick, and her weeplings too (with a follow range of 1), so nothing
     * in another arena pulls them off.
     */
    private static void targets(GameTestHelper helper, WeeplingBroodmother m, Pig pig) {
        helper.onEachTick(() -> {
            if (!pig.isAlive()) return;
            if (m.isAlive()) m.setTarget(pig);
            for (WeeplingBroodmother b : m.brood()) {
                if (b.form() != Form.WEEPLING) continue;
                b.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
                b.setTarget(pig);
            }
        });
    }

    /** Records each mechanic step the mother posts, by her own tick count; removed when the test ends. */
    private static Map<String, List<Integer>> steps(GameTestHelper helper, WeeplingBroodmother m) {
        Map<String, List<Integer>> at = new HashMap<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == m) at.computeIfAbsent(e.step(), k -> new ArrayList<>()).add(m.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    /** Pops (or hurts) a brood member with a blow of {@code amount} from the pig. */
    private static boolean hit(GameTestHelper helper, WeeplingBroodmother b, Pig by, float amount) {
        return b.hurtServer(helper.getLevel(), b.damageSources().mobAttack(by), amount);
    }

    private static int orbs(GameTestHelper helper, Vec3 abs) {
        return orbs(helper, AABB.ofSize(abs, 6, 4, 6));
    }

    private static int orbs(GameTestHelper helper, AABB box) {
        int total = 0;
        for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, box)) total += orb.getValue();
        return total;
    }

    /** Items lying in the arena, counted by the stack. */
    private static int stacks(GameTestHelper helper) {
        int total = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds())) total += item.getItem().getCount();
        return total;
    }

    private static int items(GameTestHelper helper, Vec3 abs) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, AABB.ofSize(abs, 6, 4, 6)).size();
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The squat: with her target 4 blocks off she starts it on her own; exactly 20 ticks from the squat to the lay, she
     * holds it 20 ticks and holds still all the while, and no sac exists until it ends. Then 2 sacs drop.
     */
    private static void tell(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, m, pig);
        Map<String, List<Integer>> at = steps(helper, m);
        Vec3[] start = {null};
        double[] moved = {0.0};
        boolean[] early = {false};
        int[] held = {0};
        helper.runAtTickTime(1, () -> {
            start[0] = m.position();
            m.setUntilLay(0);
        });
        helper.onEachTick(() -> {
            if (!m.core().in(Step.TELL)) return;
            held[0]++;
            early[0] |= !m.brood().isEmpty();
            moved[0] = Math.max(moved[0], m.position().distanceTo(start[0]));
        });
        helper.runAtTickTime(1 + 2 + 20 + 4, () -> {
            helper.assertTrue(at.containsKey("tell") && at.containsKey("lay"), "she squatted, then laid: " + at);
            int tell = at.get("lay").getFirst() - at.get("tell").getFirst();
            helper.assertTrue(tell == 20, "the squat lasts 20 ticks, measured " + tell);
            helper.assertTrue(Math.abs(held[0] - 20) <= 1, "she holds the squat 20 ticks, held " + held[0]);
            helper.assertTrue(!early[0], "no sac during the squat");
            helper.assertTrue(moved[0] < 0.1, "she holds still while she squats, moved " + moved[0]);
            helper.assertTrue(m.brood().size() == 2, "then 2 sacs drop, got " + m.brood().size());
            helper.succeed();
        });
    }

    /**
     * The counter: a blow of 1 pops a sac in that same tick (removed, never killed: no item, no experience). Popping
     * the whole clutch adds exactly 200 ticks to her wait for the next lay, and nothing hatches when the 60 ticks are
     * up. Popping only one of a clutch adds nothing.
     */
    private static void popClutch(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, m, pig);
        List<WeeplingBroodmother> sacs = new ArrayList<>();
        Vec3[] where = {null};
        int[] seen = {0, 0};                                                  // items, orbs
        helper.runAtTickTime(2, () -> {
            sacs.addAll(m.layClutch(helper.getLevel()));
            m.setUntilLay(1000);
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(sacs.size() == 2, "2 sacs, got " + sacs.size());
            where[0] = sacs.getFirst().position();
            int before = m.untilLay();
            helper.assertTrue(hit(helper, sacs.get(0), pig, 1.0F), "the blow lands on the first sac");
            helper.assertTrue(sacs.get(0).isRemoved() && sacs.get(0).getRemovalReason() == Entity.RemovalReason.DISCARDED,
                    "it pops at once, removed, not killed: " + sacs.get(0).getRemovalReason());
            helper.assertTrue(m.untilLay() == before, "one sac of two popped adds nothing: " + before + " -> " + m.untilLay());
            helper.assertTrue(hit(helper, sacs.get(1), pig, 1.0F), "the blow lands on the second sac");
            helper.assertTrue(sacs.get(1).isRemoved(), "the second pops too");
            int added = m.untilLay() - before;
            helper.assertTrue(added == 200, "the whole clutch popped adds 200 ticks to her wait, added " + added);
            helper.assertTrue(m.brood().isEmpty(), "she has no brood left");
        });
        helper.onEachTick(() -> {
            if (where[0] == null) return;
            Vec3 abs = where[0];
            seen[0] = Math.max(seen[0], items(helper, abs));
            seen[1] = Math.max(seen[1], orbs(helper, abs));
        });
        helper.runAtTickTime(2 + 60 + 10, () -> {
            helper.assertTrue(m.brood().isEmpty(), "nothing hatched");
            AABB arena = helper.getBounds();
            List<WeeplingBroodmother> weeplings = helper.getLevel().getEntitiesOfClass(WeeplingBroodmother.class, arena, b -> b.form() == Form.WEEPLING);
            helper.assertTrue(weeplings.isEmpty(), "no weepling in the arena, found " + weeplings.size());
            helper.assertTrue(seen[0] == 0 && seen[1] == 0, "a popped sac leaves nothing: " + seen[0] + " items, " + seen[1] + " experience");
            helper.succeed();
        });
    }

    /**
     * Failing the counter: a sac left alone hatches exactly 60 ticks after it was laid, into a weepling with 6 health
     * (of 6) and 2 attack, on her target; it rushes the pig 4 blocks off and its bite takes exactly 2.
     */
    private static void hatches(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, new Vec3(5.5, 1, 3.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 8.5));
        targets(helper, m, pig);
        List<WeeplingBroodmother> sacs = new ArrayList<>();
        long[] laidAt = {-1};
        long[] hatchedAt = {-1};
        float[] lost = {-1.0F};
        float start = pig.getHealth();
        helper.runAtTickTime(2, () -> {
            sacs.addAll(m.layClutch(helper.getLevel()));
            laidAt[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (sacs.isEmpty()) return;
            WeeplingBroodmother first = sacs.getFirst();
            if (hatchedAt[0] < 0 && first.form() == Form.WEEPLING) {
                hatchedAt[0] = helper.getTick();
                helper.assertTrue(Math.abs(first.getMaxHealth() - 6.0F) < 0.01F && Math.abs(first.getHealth() - 6.0F) < 0.01F,
                        "a weepling with 6 health of 6, got " + first.getHealth() + "/" + first.getMaxHealth());
                helper.assertTrue(Math.abs(first.getAttributeValue(Attributes.ATTACK_DAMAGE) - 2.0) < 0.01, "and 2 attack");
                helper.assertTrue(first.getTarget() == pig, "on her target");
            }
            if (lost[0] < 0 && pig.getHealth() < start) lost[0] = start - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sacs.size() == 2, "2 sacs laid, got " + sacs.size());
            helper.assertTrue(hatchedAt[0] >= 0, "a sac hatched");
            long took = hatchedAt[0] - laidAt[0];
            helper.assertTrue(took == 60, "it hatched 60 ticks after the lay, measured " + took);
            helper.assertTrue(lost[0] > 0, "a weepling bites the pig");
            helper.assertTrue(Math.abs(lost[0] - 2.0F) < 0.01F, "its bite takes 2, the pig lost " + lost[0]);
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /**
     * A lay: exactly 2 sacs, each with 1 health (of 1), a 0.56 x 0.48 hitbox, on the floor beside her (1.2 to 1.8
     * blocks from her middle), rooted, and they stay put; they are hers.
     */
    private static void clutch(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, m, pig);
        List<WeeplingBroodmother> sacs = new ArrayList<>();
        List<Vec3> laidAt = new ArrayList<>();
        helper.runAtTickTime(2, () -> {
            sacs.addAll(m.layClutch(helper.getLevel()));
            for (WeeplingBroodmother s : sacs) laidAt.add(s.position());
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(sacs.size() == 2, "exactly 2 sacs, got " + sacs.size());
            for (WeeplingBroodmother s : sacs) {
                helper.assertTrue(s.form() == Form.SAC, "a sac, is " + s.form());
                helper.assertTrue(Math.abs(s.getMaxHealth() - 1.0F) < 0.001F && Math.abs(s.getHealth() - 1.0F) < 0.001F,
                        "1 health of 1, got " + s.getHealth() + "/" + s.getMaxHealth());
                helper.assertTrue(Math.abs(s.getBbWidth() - 0.56F) < 0.02F && Math.abs(s.getBbHeight() - 0.48F) < 0.02F,
                        "a 0.56 x 0.48 hitbox, got " + s.getBbWidth() + " x " + s.getBbHeight());
                double d = s.position().subtract(m.position()).horizontalDistance();
                helper.assertTrue(d >= 1.2 - 0.01 && d <= 1.8 + 0.01, "beside her, 1.2 to 1.8 blocks off, got " + d);
                helper.assertTrue(Math.abs(s.getY() - m.getY()) < 0.01, "on the floor she stands on, y " + s.getY() + " vs " + m.getY());
                helper.assertTrue(s.pin() == Pin.ROOTED, "rooted");
                helper.assertTrue(s.mother() == m && m.brood().contains(s), "hers");
            }
        });
        helper.runAtTickTime(24, () -> {
            for (int i = 0; i < sacs.size(); i++) {
                double moved = sacs.get(i).position().distanceTo(laidAt.get(i));
                helper.assertTrue(moved < 0.01, "a sac stays where it was laid, moved " + moved);
            }
            helper.succeed();
        });
    }

    /**
     * Every 200 ticks: with 70 ticks to wait she squats 50 ticks later and lays when the wait runs out; then she waits
     * 200 ticks, squats again 180 ticks after the lay, and the second lay comes 200 ticks after the first. (Her sacs are
     * cleared after each lay, so she is never full.)
     */
    private static void every(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, m, pig);
        Map<String, List<Integer>> at = steps(helper, m);
        int[] preset = {-1};
        int[] after = {0};
        helper.runAtTickTime(1, () -> {
            m.setUntilLay(70);
            preset[0] = m.tickCount;
        });
        helper.onEachTick(() -> {
            if (at.getOrDefault("lay", List.of()).size() == 1) after[0] = Math.max(after[0], m.untilLay());
            for (WeeplingBroodmother b : m.brood()) b.discard();
        });
        helper.succeedWhen(() -> {
            List<Integer> lays = at.getOrDefault("lay", List.of()), tells = at.getOrDefault("tell", List.of());
            helper.assertTrue(lays.size() >= 2, "she lays twice on her own, at " + lays + ", squats at " + tells);
            helper.assertTrue(tells.size() == 2, "and squats before each, at " + tells);
            int lead = tells.get(0) - preset[0];
            int first = lays.get(0) - preset[0];
            int between = lays.get(1) - lays.get(0);
            int secondLead = tells.get(1) - lays.get(0);
            helper.assertTrue(lead == 50, "with 70 ticks to wait she squats 50 ticks later, measured " + lead);
            helper.assertTrue(first == 70, "and lays when the wait runs out, measured " + first);
            helper.assertTrue(after[0] == 200, "after a lay she waits 200 ticks, saw " + after[0]);
            helper.assertTrue(secondLead == 180, "she squats again 180 ticks after the lay, measured " + secondLead);
            helper.assertTrue(between == 200, "lays come 200 ticks apart, measured " + between);
        });
    }

    /**
     * At most 6: three lays make 6 sacs; a fourth makes none and she never squats while full; with one popped, a lay
     * makes exactly 1; a weepling counts the same as a sac.
     */
    private static void six(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, m, pig);
        Map<String, List<Integer>> at = steps(helper, m);
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            for (int i = 0; i < 3; i++) m.layClutch(level);
            helper.assertTrue(m.brood().size() == 6, "three lays make 6, got " + m.brood().size());
            helper.assertTrue(m.layClutch(level).isEmpty() && m.brood().size() == 6, "a fourth makes none: she holds 6");
            m.brood().getFirst().hatch(level);                               // a weepling still counts
            helper.assertTrue(m.layClutch(level).isEmpty(), "a weepling counts the same as a sac");
            m.setUntilLay(0);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(!at.containsKey("tell"), "she never squats while she holds 6: " + at);
            m.setUntilLay(100000);
            WeeplingBroodmother sac = m.brood().stream().filter(b -> b.form() == Form.SAC).findFirst().orElseThrow();
            hit(helper, sac, pig, 1.0F);
            List<WeeplingBroodmother> more = m.layClutch(helper.getLevel());
            helper.assertTrue(more.size() == 1 && m.brood().size() == 6, "with one popped a lay makes exactly 1, made " + more.size());
            helper.succeed();
        });
    }

    /**
     * Range 16: in a corner of the arena, with her target 16.3 blocks away she doesn't squat, though she is ready; moved
     * to 14.7 blocks away, she does.
     */
    private static void range(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, new Vec3(0.75, 1, 0.75));
        Pig pig = pig(helper, new Vec3(11.5, 7, 11.5));
        targets(helper, m, pig);
        Map<String, List<Integer>> at = steps(helper, m);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(pig.distanceTo(m) - 16.34F) < 0.05F, "the pig is 16.34 away, got " + pig.distanceTo(m));
            m.setUntilLay(0);
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(!at.containsKey("tell"), "16.3 blocks off: no squat: " + at);
            pig.snapTo(helper.absoluteVec(new Vec3(10.5, 6, 10.5)));
        });
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(Math.abs(pig.distanceTo(m) - 14.67F) < 0.05F, "now 14.67 away, got " + pig.distanceTo(m));
            helper.assertTrue(at.containsKey("tell"), "14.7 blocks off: she squats: " + at);
            helper.succeed();
        });
    }

    /** She lays only with a target: ready to lay and without one, she is still idle 40 ticks on, with no brood. */
    private static void needsTarget(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Map<String, List<Integer>> at = steps(helper, m);
        helper.runAtTickTime(1, () -> m.setUntilLay(0));
        helper.runAtTickTime(41, () -> {
            helper.assertTrue(!at.containsKey("tell") && m.core().idle(), "no squat without a target: " + at);
            helper.assertTrue(m.brood().isEmpty(), "and no sacs");
            helper.succeed();
        });
    }

    /**
     * Kill her: both unhatched sacs burst in that same tick (removed, not killed), and a hatched weepling lives on for
     * exactly 100 ticks, then withers away (removed, no item, no experience). She counts as one Field Guide kill.
     */
    private static void motherDies(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);         // it outlasts the weepling's bites
        pig.setHealth(1000.0F);
        targets(helper, m, pig);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(helper.absoluteVec(new Vec3(5.5, 1, -40.0)));          // far enough that orbs never fly to it
        TestPlayers.whenDone(helper, player::discard);
        List<WeeplingBroodmother> made = new ArrayList<>();
        WeeplingBroodmother[] weepling = {null};
        long[] diedAt = {-1};
        long[] witheredAt = {-1};
        int[] seen = {0, 0};
        helper.runAtTickTime(2, () -> {
            made.addAll(m.layClutch(helper.getLevel()));
            made.addAll(m.layClutch(helper.getLevel()));
            weepling[0] = made.getLast();
            weepling[0].hatch(helper.getLevel());
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(made.size() == 4 && weepling[0].form() == Form.WEEPLING, "3 sacs and a weepling, made " + made.size());
            int before = FieldGuide.get(player).killsOf("weepling_broodmother");
            helper.assertTrue(m.hurtServer(helper.getLevel(), m.damageSources().playerAttack(player), 1000.0F), "the blow lands");
            helper.assertTrue(m.isDeadOrDying(), "she dies");
            diedAt[0] = helper.getTick();
            for (WeeplingBroodmother b : made) {
                if (b == weepling[0]) continue;
                helper.assertTrue(b.isRemoved() && b.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                        "every unhatched sac bursts the moment she dies: " + b.getRemovalReason());
            }
            helper.assertTrue(!weepling[0].isRemoved(), "the weepling lives on for now");
            int kills = FieldGuide.get(player).killsOf("weepling_broodmother") - before;
            helper.assertTrue(kills == 1, "she counts as one kill, got " + kills);
        });
        int[] last = {0, 0};                                                  // the arena's items and experience the tick before
        helper.onEachTick(() -> {
            if (diedAt[0] < 0 || weepling[0] == null) return;
            int[] now = {stacks(helper), orbs(helper, helper.getBounds())};
            if (witheredAt[0] < 0 && weepling[0].isRemoved()) witheredAt[0] = helper.getTick();
            if (witheredAt[0] < 0) {
                last[0] = now[0];
                last[1] = now[1];
            } else {
                seen[0] = Math.max(seen[0], now[0] - last[0]);
                seen[1] = Math.max(seen[1], now[1] - last[1]);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(witheredAt[0] >= 0, "the weepling withers");
            long lived = witheredAt[0] - diedAt[0];
            helper.assertTrue(lived == 100, "100 ticks after her death, measured " + lived);
            helper.assertTrue(weepling[0].getRemovalReason() == Entity.RemovalReason.DISCARDED, "removed, not killed");
            helper.assertTrue(helper.getTick() >= witheredAt[0] + 3, "a few ticks to look for leftovers");
            helper.assertTrue(seen[0] == 0 && seen[1] == 0, "it leaves nothing new: " + seen[0] + " items, " + seen[1] + " experience");
        });
    }

    /** A sac whose mother leaves without dying (discarded, as a despawn does) bursts 20 ticks later, never hatching. */
    private static void orphanSac(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, m, pig);
        List<WeeplingBroodmother> sacs = new ArrayList<>();
        long[] goneAt = {-1};
        long[] burstAt = {-1};
        helper.runAtTickTime(2, () -> sacs.addAll(m.layClutch(helper.getLevel())));
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(sacs.size() == 2, "2 sacs, got " + sacs.size());
            m.discard();
            goneAt[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (goneAt[0] >= 0 && burstAt[0] < 0 && !sacs.isEmpty() && sacs.getFirst().isRemoved()) burstAt[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(burstAt[0] >= 0, "the sac bursts");
            long took = burstAt[0] - goneAt[0];
            helper.assertTrue(took == 20, "20 ticks after she left, measured " + took);
            for (WeeplingBroodmother s : sacs) helper.assertTrue(s.isRemoved() && s.form() == Form.SAC, "both burst unhatched");
        });
    }

    /**
     * A weepling: 6 health of 6, attack 2, speed 0.33, a 0.56 x 0.48 hitbox; it gives 1 experience, a sac 0 and its
     * mother 10.
     */
    private static void weeplingNumbers(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, m, pig);
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            List<WeeplingBroodmother> sacs = m.layClutch(level);
            helper.assertTrue(sacs.size() == 2, "2 sacs, got " + sacs.size());
            WeeplingBroodmother w = sacs.get(0);
            w.hatch(level);
            helper.assertTrue(w.form() == Form.WEEPLING, "it hatched");
            helper.assertTrue(Math.abs(w.getMaxHealth() - 6.0F) < 0.01F && Math.abs(w.getHealth() - 6.0F) < 0.01F,
                    "6 health of 6, got " + w.getHealth() + "/" + w.getMaxHealth());
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.ATTACK_DAMAGE) - 2.0) < 0.001, "attack 2");
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.33) < 0.001, "speed 0.33");
            helper.assertTrue(Math.abs(w.getBbWidth() - 0.56F) < 0.02F && Math.abs(w.getBbHeight() - 0.48F) < 0.02F,
                    "a 0.56 x 0.48 hitbox, got " + w.getBbWidth() + " x " + w.getBbHeight());
            helper.assertTrue(w.pin() == Pin.FREE, "free to run");
            helper.assertTrue(w.getExperienceReward(level, null) == 1, "a weepling gives 1 experience, got " + w.getExperienceReward(level, null));
            helper.assertTrue(sacs.get(1).getExperienceReward(level, null) == 0, "a sac gives none");
            helper.assertTrue(m.getExperienceReward(level, null) == 10, "the mother gives 10, got " + m.getExperienceReward(level, null));
            helper.succeed();
        });
    }

    /**
     * A weepling killed by a player drops no item and 1 experience, and it is no Field Guide kill of a broodmother.
     */
    private static void broodDrops(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, m, pig);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(helper.absoluteVec(new Vec3(5.5, 1, -40.0)));
        TestPlayers.whenDone(helper, player::discard);
        WeeplingBroodmother[] w = {null};
        Vec3[] where = {null};
        int[] before = {0};
        helper.runAtTickTime(2, () -> {
            w[0] = m.layClutch(helper.getLevel()).getFirst();
            w[0].hatch(helper.getLevel());
        });
        helper.runAtTickTime(4, () -> {
            before[0] = FieldGuide.get(player).killsOf("weepling_broodmother");
            where[0] = w[0].position();
            helper.assertTrue(w[0].hurtServer(helper.getLevel(), w[0].damageSources().playerAttack(player), 100.0F), "the blow lands");
            helper.assertTrue(w[0].isDeadOrDying(), "the weepling dies");
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(items(helper, where[0]) == 0, "it drops no item");
            int xp = orbs(helper, where[0]);
            helper.assertTrue(xp == 1, "it drops 1 experience, got " + xp);
            int kills = FieldGuide.get(player).killsOf("weepling_broodmother") - before[0];
            helper.assertTrue(kills == 0, "no Field Guide kill of a broodmother, got " + kills);
            helper.succeed();
        });
    }

    /**
     * A sac saved and loaded (as a chunk load does) is still a sac with 1 health and its small hitbox, and finds its
     * mother again: she counts it within a tick or two.
     */
    private static void reload(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, m, pig);
        WeeplingBroodmother[] loaded = {null};
        helper.runAtTickTime(2, () -> m.layClutch(helper.getLevel()));
        helper.runAtTickTime(4, () -> {
            ServerLevel level = helper.getLevel();
            WeeplingBroodmother sac = m.brood().getFirst();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            sac.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            sac.discard();
            WeeplingBroodmother fresh = (WeeplingBroodmother) ModMobs.WEEPLING_BROODMOTHER.get().create(level, EntitySpawnReason.LOAD);
            helper.assertTrue(fresh != null, "a fresh entity");
            fresh.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            helper.assertTrue(fresh.form() == Form.SAC, "it loads as a sac, is " + fresh.form());
            helper.assertTrue(Math.abs(fresh.getMaxHealth() - 1.0F) < 0.001F && Math.abs(fresh.getHealth() - 1.0F) < 0.001F, "1 health of 1");
            helper.assertTrue(Math.abs(fresh.getBbWidth() - 0.56F) < 0.02F && Math.abs(fresh.getBbHeight() - 0.48F) < 0.02F,
                    "its small hitbox, got " + fresh.getBbWidth() + " x " + fresh.getBbHeight());
            helper.assertTrue(level.addFreshEntity(fresh), "it joins");
            TestPlayers.whenDone(helper, fresh::discard);
            loaded[0] = fresh;
        });
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(m.brood().contains(loaded[0]) && loaded[0].mother() == m, "she counts it again: " + m.brood().size());
            helper.assertTrue(m.brood().size() == 2, "2 in all, got " + m.brood().size());
            helper.succeed();
        });
    }

    /** A mother and her brood never turn on one another; a pig is fair game. */
    private static void family(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        helper.runAtTickTime(2, () -> {
            List<WeeplingBroodmother> sacs = m.layClutch(helper.getLevel());
            WeeplingBroodmother w = sacs.get(0);
            w.hatch(helper.getLevel());
            helper.assertTrue(!w.canAttack(m) && !m.canAttack(w), "a weepling and its mother never fight");
            helper.assertTrue(!w.canAttack(sacs.get(1)), "nor a weepling and its sibling");
            helper.assertTrue(w.canAttack(pig) && m.canAttack(pig), "a pig is fair game");
            helper.succeed();
        });
    }

    /** A listener that cancels the "sac" step for every sac leaves the lay empty. */
    private static void sacCancel(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == m && e.step().equals("sac")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.layClutch(helper.getLevel()).isEmpty(), "no sac drops");
            helper.assertTrue(m.brood().isEmpty(), "she has none");
            helper.succeed();
        });
    }

    /** Her own blow, between lays: 3 (Atk) to a pig beside her. */
    private static void swing(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        Pig target = pig(helper, new Vec3(5.5, 1, 7.0));
        float before = target.getHealth();
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (target.isAlive()) m.setTarget(target);
            if (lost[0] < 0 && target.getHealth() < before) lost[0] = before - target.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0, "she strikes the pig");
            helper.assertTrue(Math.abs(lost[0] - 3.0F) < 0.01F, "her blow does 3 (Atk), the pig lost " + lost[0]);
        });
    }

    /** Health 36, attack 3, armour 2, a 1.4 x 1.2 hitbox; no weak point. */
    private static void stats(GameTestHelper helper) {
        WeeplingBroodmother m = mother(helper, AT);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.form() == Form.MOTHER, "a mother");
            helper.assertTrue(Math.abs(m.getMaxHealth() - 36.0F) < 0.01F && Math.abs(m.getHealth() - 36.0F) < 0.01F,
                    "health 36, got " + m.getHealth() + "/" + m.getMaxHealth());
            helper.assertTrue(Math.abs(m.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < 0.01, "attack 3");
            helper.assertTrue(Math.abs(m.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(m.getBbWidth() - 1.4F) < 0.02F && Math.abs(m.getBbHeight() - 1.2F) < 0.02F,
                    "hitbox 1.4 x 1.2, got " + m.getBbWidth() + " x " + m.getBbHeight());
            helper.assertTrue(!m.vulnerable(), "no weak point");
            helper.succeed();
        });
    }

    /**
     * Home: the crimson forest only, a monster, spawn weight 6 in groups of exactly 1. The Bloated Brood is its second
     * look.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(TeemingSpawns.tag("weepling_broodmother"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.CRIMSON_FOREST)), "its home holds the crimson forest");
        for (var other : List.of(Biomes.WARPED_FOREST, Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY, Biomes.BASALT_DELTAS, Biomes.PLAINS)) {
            helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("weepling_broodmother");
        helper.assertTrue(row.dim() == RosterStats.Dim.NETHER, "a Nether mob");
        helper.assertTrue(ModMobs.WEEPLING_BROODMOTHER.get().getCategory() == MobCategory.MONSTER, "a monster");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/weepling_broodmother")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.WEEPLING_BROODMOTHER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 6, "spawn weight 6, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("weepling_broodmother");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("bloated_brood")), "the Bloated Brood is a look: " + looks);
        helper.succeed();
    }
}
