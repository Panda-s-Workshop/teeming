package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.overworld.HollowMourner;
import com.panda.teeming.mob.overworld.HollowMourner.Grave;
import com.panda.teeming.mob.overworld.HollowMourner.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Hollow Mourner: it remembers undead that die within 8 blocks of it for 30 s; every 200 ticks it chants for 60 ticks
 * and then raises one of them, a fresh mob of that kind where it fell, with no loot or experience. Any hit staggers it
 * for 40 ticks and breaks a chant.
 *
 * <p>The mourner stands in the middle of the arena with its goals cleared (its phase machine and memory still run).
 * Husks stand in for the dead: undead that don't burn in daylight. A still pig is the hand that hits it and kills them.
 */
public final class HollowMournerTests {
    private HollowMournerTests() {}

    private static final Vec3 AT = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("hollow_mourner_tell", HollowMournerTests::tell);
        out.accept("hollow_mourner_counter", HollowMournerTests::hitBreaksChant);
        out.accept("hollow_mourner_failure", HollowMournerTests::deadRise);
        TeemingGameTests.timeLimit("hollow_mourner_failure", 140);
        out.accept("hollow_mourner_raised_reload", HollowMournerTests::raisedReload);
        out.accept("hollow_mourner_stagger", HollowMournerTests::stagger);
        out.accept("hollow_mourner_range", HollowMournerTests::range);
        out.accept("hollow_mourner_memory", HollowMournerTests::memory);
        out.accept("hollow_mourner_every", HollowMournerTests::every);
        TeemingGameTests.timeLimit("hollow_mourner_every", 320);
        out.accept("hollow_mourner_undead_only", HollowMournerTests::undeadOnly);
        out.accept("hollow_mourner_raise_cancel", HollowMournerTests::raiseCancel);
        out.accept("hollow_mourner_swing", HollowMournerTests::swing);
        out.accept("hollow_mourner_stats", HollowMournerTests::stats);
        out.accept("hollow_mourner_home", HollowMournerTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A mourner in the middle of the arena with every goal cleared; its own phase machine and memory still run. */
    private static HollowMourner mourner(GameTestHelper helper) {
        HollowMourner m = helper.spawn(ModMobs.HOLLOW_MOURNER.get(), AT);
        m.removeFreeWill();
        return m;
    }

    /** A point {@code d} blocks from the mourner along a direction at {@code deg} degrees round it. */
    private static Vec3 around(double d, double deg) {
        double a = Math.toRadians(deg);
        return AT.add(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    private static Husk husk(GameTestHelper helper, Vec3 at) {
        Husk husk = helper.spawn(EntityTypes.HUSK, at);
        husk.setNoAi(true);
        return husk;
    }

    private static Pig pig(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(1.5, 1, 1.5));
        pig.setNoAi(true);
        return pig;
    }

    /** Kills a mob outright with a blow from {@code by}: it lies dying for a second, as any mob does. */
    private static void killBy(GameTestHelper helper, LivingEntity victim, LivingEntity by) {
        victim.hurtServer(helper.getLevel(), victim.damageSources().mobAttack(by), 1000.0F);
    }

    /** An item for a failure message: what it holds, where (relative), how old, and who threw it. */
    private static String describe(GameTestHelper helper, ItemEntity item) {
        Vec3 at = helper.relativeVec(item.position());
        return item.getItem() + String.format(" at %.2f %.2f %.2f", at.x, at.y, at.z) + " age " + item.getAge()
                + " on tick " + helper.getTick() + " thrower " + item.getOwner();
    }

    /** Live raised husks within half a block of {@code abs} (an absolute position). */
    private static List<Husk> raisedAt(GameTestHelper helper, Vec3 abs) {
        return helper.getLevel().getEntitiesOfClass(Husk.class, AABB.ofSize(abs, 1.0, 2.0, 1.0),
                h -> h.isAlive() && h.entityTags().contains(HollowMourner.RAISED_TAG));
    }

    /** Every raised husk in the level near the arena, with its position relative to the arena. */
    private static String raised(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Husk.class, helper.getBounds().inflate(16.0), h -> h.entityTags().contains(HollowMourner.RAISED_TAG))
                .stream().map(h -> String.format(Locale.ROOT, "%.2f %.2f %.2f%s", helper.relativeVec(h.position()).x, helper.relativeVec(h.position()).y,
                        helper.relativeVec(h.position()).z, h.isAlive() ? "" : " dead")).toList().toString();
    }

    /** Records the mourner's own tick count each time it posts a mechanic step; removed when the test ends. */
    private static Map<String, List<Integer>> steps(GameTestHelper helper, HollowMourner m) {
        return steps(helper, m, false);
    }

    /**
     * Records each mechanic step the mourner posts, by its own tick count or, with {@code gameTime}, by the game time
     * (for steps started from outside its tick, such as a hit); removed when the test ends.
     */
    private static Map<String, List<Integer>> steps(GameTestHelper helper, HollowMourner m, boolean gameTime) {
        Map<String, List<Integer>> at = new HashMap<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == m) at.computeIfAbsent(e.step(), k -> new ArrayList<>())
                    .add(gameTime ? (int) helper.getLevel().getGameTime() : m.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    private static Grave grave(ServerLevel level, Vec3 abs, long age) {
        return new Grave(UUID.randomUUID(), EntityTypes.HUSK, abs, false, level.getGameTime() - age);
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The chant: a husk dies 3 blocks away and the mourner chants over its grave on its own; exactly 60 ticks from
     * the chant to the raising, it stands still all the while, and nothing stands up at the grave until it ends.
     */
    private static void tell(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        Husk husk = husk(helper, around(3.0, 0));
        Pig pig = pig(helper);
        Map<String, List<Integer>> at = steps(helper, m);
        Vec3 grave = helper.absoluteVec(around(3.0, 0));
        Vec3[] start = {null};
        double[] moved = {0.0};
        boolean[] early = {false};
        int[] held = {0};
        // the tick a raised husk first stands at the grave: once up it has its AI back and may walk off (to a player in
        // a neighbouring arena, say) before the check
        int[] rose = {-1};
        helper.runAtTickTime(1, () -> {
            start[0] = m.position();
            killBy(helper, husk, pig);
        });
        helper.onEachTick(() -> {
            if (held[0] > 0 && rose[0] < 0 && !m.core().in(Step.CHANT) && !raisedAt(helper, grave).isEmpty()) rose[0] = (int) helper.getTick();
            if (!m.core().in(Step.CHANT)) return;
            held[0]++;
            early[0] |= !raisedAt(helper, grave).isEmpty();
            moved[0] = Math.max(moved[0], m.position().subtract(start[0]).horizontalDistance());
        });
        helper.runAtTickTime(1 + 3 + 60 + 4, () -> {
            helper.assertTrue(at.containsKey("chant") && at.containsKey("raise"), "it chanted, then raised: " + at);
            int chant = at.get("raise").getFirst() - at.get("chant").getFirst();
            helper.assertTrue(chant == 60, "the chant lasts 60 ticks, measured " + chant);
            helper.assertTrue(Math.abs(held[0] - 60) <= 1, "it holds the chant phase 60 ticks, held " + held[0]);
            helper.assertTrue(!early[0], "nothing rises during the chant");
            helper.assertTrue(moved[0] < 0.1, "it stands still while it chants, moved " + moved[0]);
            helper.assertTrue(rose[0] >= 0, "then the husk stands up at its grave, first seen there on tick " + rose[0]
                    + "; raised husks " + raised(helper));
            helper.succeed();
        });
    }

    /**
     * The counter: a hit 30 ticks into the chant breaks it. It staggers for exactly 40 ticks and nothing rises; the
     * grave is still remembered, and the moment it recovers it starts the chant again.
     */
    private static void hitBreaksChant(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        Husk husk = husk(helper, around(3.0, 90));
        Pig pig = pig(helper);
        Map<String, List<Integer>> at = steps(helper, m);
        Vec3 grave = helper.absoluteVec(around(3.0, 90));
        Map<String, List<Integer>> time = steps(helper, m, true);
        int[] chantAt = {-1};
        int[] reeled = {0};
        boolean[] rose = {false};
        helper.runAtTickTime(1, () -> killBy(helper, husk, pig));
        helper.onEachTick(() -> {
            if (m.core().in(Step.STAGGER)) reeled[0]++;
            rose[0] |= !raisedAt(helper, grave).isEmpty();
            if (chantAt[0] < 0 && m.core().in(Step.CHANT)) chantAt[0] = (int) helper.getTick();
            if (chantAt[0] >= 0 && helper.getTick() == chantAt[0] + 30) {
                helper.assertTrue(m.core().in(Step.CHANT), "still chanting, phase " + m.phase());
                helper.assertTrue(m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(pig), 1.0F), "the hit lands");
                helper.assertTrue(m.core().in(Step.STAGGER), "a hit staggers it, phase " + m.phase());
                helper.assertTrue(m.chanting() == null, "and the chant is broken");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.getOrDefault("chant", List.of()).size() >= 2, "it chants again after the stagger: " + at);
            int stagger = time.get("stagger").getFirst();
            int again = time.get("chant").get(1) - stagger;
            helper.assertTrue(again == 40, "the stagger lasts 40 ticks, then it chants again: measured " + again);
            helper.assertTrue(Math.abs(reeled[0] - 40) <= 1, "it holds the stagger 40 ticks, held " + reeled[0]);
            helper.assertTrue(!at.containsKey("raise") && !rose[0], "nothing rose from the broken chant: " + at);
            helper.assertTrue(m.graves().size() == 1 && m.graves().getFirst().pos().distanceTo(grave) < 0.6,
                    "the grave is still remembered: " + m.graves());
        });
    }

    /**
     * Failing the counter: the chant runs out and the dead husk stands up where it fell, a fresh husk at full health
     * (20), marked as raised, and the grave is used up. The raised dead carry nothing: killed by a player it leaves no
     * items and no experience, while a plain husk killed by the same player beside it leaves experience.
     */
    private static void deadRise(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        Husk husk = husk(helper, around(4.0, 90));
        Husk control = husk(helper, around(3.5, 270));
        Pig pig = pig(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(helper.absoluteVec(new Vec3(5.5, 1, -40.0)));          // far enough that orbs never fly to it
        TestPlayers.whenDone(helper, player::discard);
        Vec3 grave = helper.absoluteVec(around(4.0, 90));
        Vec3 controlAt = helper.absoluteVec(around(3.5, 270));
        long[] killedAt = {-1};
        // the most orbs and items seen on any tick: orbs fly to any player within 8 blocks, and a survivor in a
        // neighbouring arena can take them before a count at the end
        int[] seen = {0, 0, 0};                                               // control orbs, raised orbs, raised items
        // the dead husk's own loot (0 to 2 rotten flesh, killed by the pig) already lies by the grave when it rises, so
        // only items that appear after the raised husk is killed are its drops
        Set<UUID> lying = new HashSet<>();
        Map<UUID, String> itemsSeen = new HashMap<>();                        // each new item by the grave, as first seen
        helper.runAtTickTime(1, () -> killBy(helper, husk, pig));
        helper.onEachTick(() -> {
            if (killedAt[0] >= 0) {
                ServerLevel level = helper.getLevel();
                seen[0] = Math.max(seen[0], level.getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(controlAt, 4, 3, 4)).size());
                seen[1] = Math.max(seen[1], level.getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(grave, 4, 3, 4)).size());
                List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(grave, 4, 3, 4),
                        e -> !lying.contains(e.getUUID()));
                seen[2] = Math.max(seen[2], items.size());
                for (ItemEntity item : items) itemsSeen.putIfAbsent(item.getUUID(), describe(helper, item));
                return;
            }
            List<Husk> raised = raisedAt(helper, grave);
            if (raised.isEmpty()) return;
            Husk r = raised.getFirst();
            helper.assertTrue(raised.size() == 1, "one husk rises, got " + raised.size());
            helper.assertTrue(Math.abs(r.getHealth() - 20.0F) < 0.01F, "a fresh husk at full health 20, got " + r.getHealth());
            helper.assertTrue(!r.isBaby(), "a grown husk, as the dead one was");
            helper.assertTrue(r.getLootTable().isPresent() && r.getLootTable().get().identifier().equals(Identifier.withDefaultNamespace("empty")),
                    "it drops no loot: table " + r.getLootTable());
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                helper.assertTrue(r.getDropChances().byEquipment(slot) == 0.0F, "it drops none of its gear, " + slot);
            }
            helper.assertTrue(r.wasExperienceConsumed(), "it gives no experience");
            helper.assertTrue(m.graves().isEmpty(), "the grave is used up: " + m.graves());
            for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, AABB.ofSize(grave, 4, 3, 4))) lying.add(e.getUUID());
            r.hurtServer(helper.getLevel(), r.damageSources().playerAttack(player), 1000.0F);
            control.hurtServer(helper.getLevel(), control.damageSources().playerAttack(player), 1000.0F);
            killedAt[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killedAt[0] >= 0, "the dead husk rose");
            helper.assertTrue(helper.getTick() >= killedAt[0] + 24, "waiting for both bodies to fade");
            helper.assertTrue(seen[0] > 0, "a plain husk leaves experience, saw at most " + seen[0] + " orbs");
            helper.assertTrue(seen[1] == 0, "the raised husk leaves no experience, saw " + seen[1] + " orbs");
            helper.assertTrue(seen[2] == 0, "and no items, saw " + seen[2] + ": " + itemsSeen.values()
                    + " (already lying there: " + lying.size() + ")");
        });
    }

    /**
     * A raised mob saved and loaded again still gives no experience: the skip isn't in its saved data, so it is set
     * again when the loaded mob joins the level. A plain husk loaded the same way still gives experience.
     */
    private static void raisedReload(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        ServerLevel level = helper.getLevel();
        LivingEntity raised = m.raise(level, grave(level, helper.absoluteVec(around(3.0, 90)), 0));
        helper.assertTrue(raised != null, "the husk rises");
        Husk control = husk(helper, around(3.0, 270));
        LivingEntity loaded = reload(helper, raised);
        LivingEntity loadedControl = reload(helper, control);
        helper.assertTrue(loaded.entityTags().contains(HollowMourner.RAISED_TAG), "the loaded husk is still marked as raised");
        helper.assertTrue(loaded.wasExperienceConsumed(), "the loaded raised husk gives no experience");
        helper.assertTrue(!loadedControl.wasExperienceConsumed(), "a loaded plain husk still gives experience");
        helper.succeed();
    }

    /** The mob saved, removed, and a fresh one of its kind loaded from that save in its place, as a chunk load does. */
    private static LivingEntity reload(GameTestHelper helper, LivingEntity mob) {
        ServerLevel level = helper.getLevel();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        mob.saveWithoutId(out);
        CompoundTag tag = out.buildResult();
        mob.discard();
        LivingEntity fresh = (LivingEntity) mob.getType().create(level, EntitySpawnReason.LOAD);
        helper.assertTrue(fresh != null, "a fresh " + mob.getType());
        fresh.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        helper.assertTrue(!fresh.wasExperienceConsumed(), "the saved data alone doesn't carry the skip");
        level.addFreshEntity(fresh);
        TestPlayers.whenDone(helper, fresh::discard);
        return fresh;
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /**
     * A hit staggers it for 40 ticks, and steady blows can't keep it down: a fall staggers nothing; a hit while idle
     * staggers it; a second hit 20 ticks in doesn't make it longer, so it stands up 40 ticks after the first; a hit
     * 10 ticks after that doesn't stagger it; but a hit during a chant inside those 40 ticks still breaks the chant.
     */
    private static void stagger(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        m.setUntilChant(100000);                                              // no chant until the grave below
        Pig pig = pig(helper);
        Map<String, List<Integer>> time = steps(helper, m, true);
        int[] firstAt = {-1};
        Runnable hit = () -> {
            m.damageCooldownTime = 0;                                         // the last hit's cooldown would refuse an equal one
            helper.assertTrue(m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(pig), 1.0F), "the hit lands");
        };
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.hurtServer(helper.getLevel(), m.damageSources().fall(), 1.0F), "the fall hurts it");
            helper.assertTrue(m.core().idle(), "a fall doesn't stagger it, phase " + m.phase());
        });
        helper.runAtTickTime(4, () -> {
            hit.run();
            firstAt[0] = (int) helper.getLevel().getGameTime();
            helper.assertTrue(m.core().in(Step.STAGGER), "a hit while idle staggers it, phase " + m.phase());
        });
        helper.runAtTickTime(24, () -> {
            hit.run();
            helper.assertTrue(m.core().in(Step.STAGGER), "still staggered 20 ticks on, phase " + m.phase());
        });
        helper.runAtTickTime(42, () -> helper.assertTrue(m.core().in(Step.STAGGER), "staggered until 40 ticks after the first hit, phase " + m.phase()));
        helper.runAtTickTime(46, () -> helper.assertTrue(m.core().idle(), "up again 40 ticks after the first hit, not the second, phase " + m.phase()));
        helper.runAtTickTime(54, () -> {
            hit.run();
            helper.assertTrue(m.core().idle(), "a hit 10 ticks after the stagger doesn't stagger it, phase " + m.phase());
            m.remember(grave(helper.getLevel(), helper.absoluteVec(around(2.0, 0)), 0));
            m.setUntilChant(0);
        });
        helper.runAtTickTime(64, () -> {
            helper.assertTrue(m.core().in(Step.CHANT), "it chants over the grave, phase " + m.phase());
            hit.run();
            helper.assertTrue(m.core().in(Step.STAGGER), "a hit during the chant breaks it, even so soon, phase " + m.phase());
        });
        helper.runAtTickTime(66, () -> {
            List<Integer> staggers = time.getOrDefault("stagger", List.of());
            helper.assertTrue(staggers.size() == 2, "two staggers, at the first hit and the chant: " + time);
            helper.assertTrue(staggers.getFirst() == firstAt[0], "the first at the first hit: " + time);
            helper.succeed();
        });
    }

    /** It remembers undead that die within 8 blocks: a husk dying 7.5 blocks away is remembered, one at 8.5 is not. */
    private static void range(GameTestHelper helper) {
        HollowMourner m = helper.spawn(ModMobs.HOLLOW_MOURNER.get(), new Vec3(1.5, 1, 5.5));
        m.removeFreeWill();
        m.setUntilChant(100000);
        Husk inside = husk(helper, new Vec3(9.0, 1, 5.5));
        Husk outside = husk(helper, new Vec3(10.0, 1, 5.5));
        Pig pig = pig(helper);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(inside.distanceTo(m) - 7.5F) < 0.05F && Math.abs(outside.distanceTo(m) - 8.5F) < 0.05F,
                    "husks at 7.5 and 8.5, got " + inside.distanceTo(m) + " and " + outside.distanceTo(m));
            killBy(helper, inside, pig);
            killBy(helper, outside, pig);
        });
        helper.runAtTickTime(6, () -> {
            Vec3 in = helper.absoluteVec(new Vec3(9.0, 1, 5.5)), out = helper.absoluteVec(new Vec3(10.0, 1, 5.5));
            List<Grave> graves = m.graves();
            helper.assertTrue(graves.stream().anyMatch(g -> g.pos().distanceTo(in) < 0.3), "7.5 blocks away is remembered: " + graves);
            helper.assertTrue(graves.stream().noneMatch(g -> g.pos().distanceTo(out) < 0.3), "8.5 blocks away is not: " + graves);
            helper.assertTrue(graves.stream().allMatch(g -> g.type() == EntityTypes.HUSK), "as a husk: " + graves);
            helper.succeed();
        });
    }

    /**
     * It remembers for 30 s (600 ticks): a grave 590 ticks old is kept and one 610 ticks old is forgotten; the kept one
     * is gone 12 ticks later, at 602. Its memory is bounded: of 12 fresh graves it keeps the newest 8.
     */
    private static void memory(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        m.setUntilChant(100000);
        HollowMourner full = helper.spawn(ModMobs.HOLLOW_MOURNER.get(), new Vec3(2.5, 1, 9.5));
        full.removeFreeWill();
        full.setUntilChant(100000);
        Vec3 young = helper.absoluteVec(around(2.0, 0)), old = helper.absoluteVec(around(2.0, 180));
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            m.remember(grave(level, young, 590));
            m.remember(grave(level, old, 610));
            for (int i = 0; i < 12; i++) {
                full.remember(new Grave(UUID.randomUUID(), EntityTypes.HUSK, helper.absoluteVec(new Vec3(1.5 + i * 0.5, 1, 1.5)), false,
                        level.getGameTime() - 100 + i));
            }
        });
        helper.runAtTickTime(4, () -> {
            List<Grave> graves = m.graves();
            helper.assertTrue(graves.size() == 1 && graves.getFirst().pos().distanceTo(young) < 0.01,
                    "the 590-tick-old grave is kept, the 610-tick-old one forgotten: " + graves);
            List<Grave> kept = full.graves();
            helper.assertTrue(kept.size() == 8, "it keeps 8 graves, kept " + kept.size());
            long newest = kept.getLast().diedAt(), oldest = kept.getFirst().diedAt();
            helper.assertTrue(newest - oldest == 7, "the newest 8 of them, " + (newest - oldest + 1) + " ticks apart");
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(m.graves().isEmpty(), "past 600 ticks the grave is forgotten: " + m.graves());
            helper.succeed();
        });
    }

    /**
     * Every 200 ticks: with two graves and 70 ticks to wait, it chants 10 ticks later and raises the first when the
     * wait runs out; then it waits 200 ticks, and the second rises 200 ticks after the first, each after a 60-tick
     * chant.
     */
    private static void every(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        Husk a = husk(helper, around(3.0, 0));
        Husk b = husk(helper, around(3.0, 180));
        Pig pig = pig(helper);
        Map<String, List<Integer>> at = steps(helper, m);
        int[] preset = {-1};
        int[] after = {0};
        helper.runAtTickTime(1, () -> {
            m.setUntilChant(70);
            preset[0] = m.tickCount;
            killBy(helper, a, pig);
            killBy(helper, b, pig);
        });
        helper.onEachTick(() -> {
            if (at.getOrDefault("raise", List.of()).size() == 1) after[0] = Math.max(after[0], m.untilChant());
            for (Vec3 g : List.of(helper.absoluteVec(around(3.0, 0)), helper.absoluteVec(around(3.0, 180)))) {
                for (Husk risen : raisedAt(helper, g)) risen.removeFreeWill();   // the raised stay where they stood
            }
        });
        helper.succeedWhen(() -> {
            List<Integer> raises = at.getOrDefault("raise", List.of()), chants = at.getOrDefault("chant", List.of());
            helper.assertTrue(raises.size() >= 2, "it raises twice on its own, raises at " + raises + ", chants at " + chants);
            helper.assertTrue(chants.size() == 2, "and chants before each, chants at " + chants);
            int lead = chants.get(0) - preset[0];
            int first = raises.get(0) - preset[0];
            int between = raises.get(1) - raises.get(0);
            int secondLead = chants.get(1) - raises.get(0);
            helper.assertTrue(lead == 10, "with 70 ticks to wait it starts chanting 10 ticks later, measured " + lead);
            helper.assertTrue(first == 70, "and raises when the wait runs out, measured " + first);
            helper.assertTrue(after[0] == 200, "after a raising it waits 200 ticks, saw " + after[0]);
            helper.assertTrue(secondLead == 140, "it chants again 140 ticks after the raising, measured " + secondLead);
            helper.assertTrue(between == 200, "raisings come 200 ticks apart, measured " + between);
        });
    }

    /**
     * Only undead: a pig and a spider dying beside it leave no grave, a husk does. Players, the Wither and any other
     * boss can never be raised; zombies, skeletons and husks can.
     */
    private static void undeadOnly(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        m.setUntilChant(100000);
        Pig victim = helper.spawn(EntityTypes.PIG, around(2.0, 0));
        victim.setNoAi(true);
        Spider spider = helper.spawn(EntityTypes.SPIDER, around(3.0, 120));
        spider.setNoAi(true);
        Husk husk = husk(helper, around(3.0, 240));
        Pig pig = pig(helper);
        helper.runAtTickTime(2, () -> {
            killBy(helper, victim, pig);
            killBy(helper, spider, pig);
            killBy(helper, husk, pig);
        });
        helper.runAtTickTime(6, () -> {
            List<Grave> graves = m.graves();
            helper.assertTrue(graves.size() == 1 && graves.getFirst().type() == EntityTypes.HUSK, "only the husk is remembered: " + graves);
            helper.assertTrue(!HollowMourner.raisable(EntityTypes.PLAYER), "never a player");
            helper.assertTrue(!HollowMourner.raisable(EntityTypes.WITHER), "never the Wither");
            helper.assertTrue(!HollowMourner.raisable(EntityTypes.PIG) && !HollowMourner.raisable(EntityTypes.SPIDER), "never the living");
            for (EntityType<?> t : List.<EntityType<?>>of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.HUSK)) {
                helper.assertTrue(HollowMourner.raisable(t), "undead can be raised: " + t);
            }
            helper.succeed();
        });
    }

    /** A listener that cancels the "raise" step stops the dead rising: the chant runs out and nothing stands up. */
    private static void raiseCancel(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        Husk husk = husk(helper, around(3.0, 45));
        Pig pig = pig(helper);
        Vec3 grave = helper.absoluteVec(around(3.0, 45));
        Map<String, List<Integer>> at = steps(helper, m);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == m && e.step().equals("raise")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(1, () -> killBy(helper, husk, pig));
        helper.runAtTickTime(1 + 3 + 60 + 6, () -> {
            helper.assertTrue(at.containsKey("chant") && at.containsKey("raise"), "it chanted and tried to raise: " + at);
            helper.assertTrue(raisedAt(helper, grave).isEmpty(), "nothing rose");
            helper.assertTrue(m.core().idle(), "it is idle again, phase " + m.phase());
            helper.succeed();
        });
    }

    /** It claws what it fights: exactly 3 damage (Atk) to a pig beside it. */
    private static void swing(GameTestHelper helper) {
        HollowMourner m = helper.spawn(ModMobs.HOLLOW_MOURNER.get(), AT);
        m.stayPut();
        m.setUntilChant(100000);
        Pig target = helper.spawn(EntityTypes.PIG, around(1.5, 0));
        target.setNoAi(true);
        float before = target.getHealth();
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (target.isAlive()) m.setTarget(target);                        // players in other arenas never pull it off
            if (lost[0] < 0 && target.getHealth() < before) lost[0] = before - target.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0, "it claws the pig");
            helper.assertTrue(Math.abs(lost[0] - 3.0F) < 0.01F, "the claw does 3 (Atk), the pig lost " + lost[0]);
        });
    }

    /** Health 26, attack 3, armour 2, a 0.7 x 2.0 hitbox; no weak point. */
    private static void stats(GameTestHelper helper) {
        HollowMourner m = mourner(helper);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(m.getMaxHealth() - 26.0F) < 0.01F && Math.abs(m.getHealth() - 26.0F) < 0.01F,
                    "health 26, got " + m.getHealth() + "/" + m.getMaxHealth());
            helper.assertTrue(Math.abs(m.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < 0.01, "attack 3");
            helper.assertTrue(Math.abs(m.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(m.getBbWidth() - 0.7F) < 0.02F && Math.abs(m.getBbHeight() - 2.0F) < 0.02F,
                    "hitbox 0.7 x 2.0, got " + m.getBbWidth() + " x " + m.getBbHeight());
            helper.assertTrue(!m.vulnerable(), "no weak point");
            helper.succeed();
        });
    }

    /**
     * Home: Overworld land biomes, swamps among them, a monster of the dark, spawn weight 5 in groups of exactly 1;
     * never the mushroom fields, which keep out every monster without a home of its own there. The Bog Mourner is
     * limited to swamps.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(TeemingSpawns.tag("hollow_mourner"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        for (var biome : List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.SWAMP, Biomes.TAIGA, Biomes.DESERT)) {
            helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(biome)), "its home holds " + biome.identifier());
        }
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.NETHER_WASTES)), "never the Nether");
        var noMonsters = biomes.get(TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "no_default_monsters")));
        helper.assertTrue(noMonsters.isPresent() && noMonsters.get().stream().anyMatch(b -> b.is(Biomes.MUSHROOM_FIELDS)),
                "the mushroom fields refuse monster spawns, its own included");
        RosterStats.Entry row = RosterStats.get("hollow_mourner");
        helper.assertTrue(!row.anyLight(), "only in the dark: at night on the surface");
        helper.assertTrue(ModMobs.HOLLOW_MOURNER.get().getCategory() == MobCategory.MONSTER, "a monster");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/hollow_mourner")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.HOLLOW_MOURNER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 5, "spawn weight 5, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("hollow_mourner");
        LookDef bog = looks.stream().filter(l -> l.id().equals("bog_mourner")).findFirst().orElse(null);
        helper.assertTrue(bog != null && "#c:is_swamp".equals(bog.biomes()), "the Bog Mourner lives in swamps: " + bog);
        helper.succeed();
    }
}
