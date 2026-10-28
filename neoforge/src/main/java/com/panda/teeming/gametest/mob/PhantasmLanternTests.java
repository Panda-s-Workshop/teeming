package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.PhantasmLantern;
import com.panda.teeming.mob.nether.PhantasmLantern.Illusions;
import com.panda.teeming.mob.nether.PhantasmLantern.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
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
 * Phantasm Lantern: every 200 ticks, with a target within 16 blocks, it conjures for 20 ticks and then releases up to 3
 * illusions, copies of the mobs within 16 blocks (or of hoglins, piglins, endermen and striders), each with 1 health,
 * no damage, no shadow, no loot, no experience and no kill. They vanish when it dies, and a blow on it snuffs the newest.
 *
 * <p>The lantern stands still in the middle of the arena (its phase machine, conjuring and illusions still run) with a
 * follow range of 1, so it never notices a survivor in another arena. A still pig is its target, given every tick.
 */
public final class PhantasmLanternTests {
    private PhantasmLanternTests() {}

    private static final Vec3 AT = new Vec3(5.5, 1, 5.5);
    private static final Set<EntityType<?>> HOME = Set.of(EntityTypes.HOGLIN, EntityTypes.PIGLIN, EntityTypes.ENDERMAN, EntityTypes.STRIDER);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("phantasm_lantern_tell", PhantasmLanternTests::tell);
        out.accept("phantasm_lantern_counter", PhantasmLanternTests::deathDispels);
        out.accept("phantasm_lantern_failure", PhantasmLanternTests::swingAtIllusion);
        out.accept("phantasm_lantern_gutter", PhantasmLanternTests::blowSnuffsNewest);
        out.accept("phantasm_lantern_illusion", PhantasmLanternTests::illusionNumbers);
        out.accept("phantasm_lantern_harmless", PhantasmLanternTests::harmless);
        out.accept("phantasm_lantern_every", PhantasmLanternTests::every);
        TeemingGameTests.timeLimit("phantasm_lantern_every", 320);
        out.accept("phantasm_lantern_three", PhantasmLanternTests::three);
        out.accept("phantasm_lantern_range", PhantasmLanternTests::range);
        out.accept("phantasm_lantern_copies", PhantasmLanternTests::copies);
        out.accept("phantasm_lantern_home_kinds", PhantasmLanternTests::homeKinds);
        out.accept("phantasm_lantern_never_copies", PhantasmLanternTests::neverCopies);
        out.accept("phantasm_lantern_needs_target", PhantasmLanternTests::needsTarget);
        out.accept("phantasm_lantern_reload", PhantasmLanternTests::reloadSwept);
        out.accept("phantasm_lantern_leaves", PhantasmLanternTests::leaves);
        out.accept("phantasm_lantern_keeps_away", PhantasmLanternTests::keepsAway);
        out.accept("phantasm_lantern_illusion_cancel", PhantasmLanternTests::illusionCancel);
        out.accept("phantasm_lantern_stats", PhantasmLanternTests::stats);
        out.accept("phantasm_lantern_home", PhantasmLanternTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A lantern at {@code at} that holds still, with a follow range of 1 and no release of its own coming. */
    private static PhantasmLantern lantern(GameTestHelper helper, Vec3 at) {
        PhantasmLantern l = helper.spawn(ModMobs.PHANTASM_LANTERN.get(), at);
        // its illusions all pop when fewer of the chunks round it tick, and a neighbouring arena's chunks stop ticking
        // when that arena's batch ends: keep the ring round this arena ticking so only the test decides
        TestPlayers.keepTicking(helper, 16);
        l.stayPut();
        l.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        l.setUntilConjure(100000);
        return l;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    /** Gives the lantern the pig as its target every tick, so nothing in another arena pulls it off. */
    private static void targets(GameTestHelper helper, PhantasmLantern l, Pig pig) {
        helper.onEachTick(() -> {
            if (pig.isAlive() && l.isAlive()) l.setTarget(pig);
        });
    }

    /** Records each mechanic step the lantern posts, by its own tick count; removed when the test ends. */
    private static Map<String, List<Integer>> steps(GameTestHelper helper, PhantasmLantern l) {
        Map<String, List<Integer>> at = new HashMap<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == l) at.computeIfAbsent(e.step(), k -> new ArrayList<>()).add(l.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    /** The mobs of {@code sources} inside this arena: the arenas sit closer together than its copy reach. */
    private static List<Mob> here(GameTestHelper helper, List<Mob> sources) {
        AABB arena = helper.getBounds();
        return sources.stream().filter(m -> arena.contains(m.position())).toList();
    }

    /** A still husk at {@code at}: a mob to copy that never burns in daylight. */
    private static Husk husk(GameTestHelper helper, Vec3 at) {
        Husk husk = helper.spawn(EntityTypes.HUSK, at);
        husk.setNoAi(true);
        return husk;
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The conjuring: with its target 4 blocks off it conjures on its own; exactly 20 ticks from the conjuring to the
     * release, it holds still all the while, and no illusion exists until it ends. Then 3 step out.
     */
    private static void tell(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        Map<String, List<Integer>> at = steps(helper, l);
        Vec3[] start = {null};
        double[] moved = {0.0};
        boolean[] early = {false};
        int[] held = {0};
        helper.runAtTickTime(1, () -> {
            start[0] = l.position();
            l.setUntilConjure(0);
        });
        helper.onEachTick(() -> {
            if (!l.core().in(Step.CONJURE)) return;
            held[0]++;
            early[0] |= l.illusions().size() > 0;
            moved[0] = Math.max(moved[0], l.position().distanceTo(start[0]));
        });
        helper.runAtTickTime(1 + 2 + 20 + 4, () -> {
            helper.assertTrue(at.containsKey("conjure") && at.containsKey("release"), "it conjured, then released: " + at);
            int tell = at.get("release").getFirst() - at.get("conjure").getFirst();
            helper.assertTrue(tell == 20, "the conjuring lasts 20 ticks, measured " + tell);
            helper.assertTrue(Math.abs(held[0] - 20) <= 1, "it holds the conjuring 20 ticks, held " + held[0]);
            helper.assertTrue(!early[0], "no illusion during the conjuring");
            helper.assertTrue(moved[0] < 0.1, "it holds still while it conjures, moved " + moved[0]);
            helper.assertTrue(l.illusions().size() == 3, "then 3 illusions step out, got " + l.illusions().size());
            helper.succeed();
        });
    }

    /**
     * The counter: kill the lantern and all 3 illusions vanish in that same tick. They are removed, never killed, so
     * the player who killed the lantern has killed it alone: no kill of an illusion's kind and one mob kill in all.
     */
    private static void deathDispels(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestPlayers.whenDone(helper, player::discard);
        List<Mob> made = new ArrayList<>();
        helper.runAtTickTime(2, () -> made.addAll(l.release(helper.getLevel())));
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(made.size() == 3 && l.illusions().size() == 3, "3 illusions, got " + made.size());
            Set<EntityType<?>> kinds = new HashSet<>();
            for (Mob m : made) kinds.add(m.getType());
            int before = player.getStats().getValue(Stats.CUSTOM, Stats.MOB_KILLS);
            helper.assertTrue(l.hurtServer(helper.getLevel(), l.damageSources().playerAttack(player), 1000.0F), "the blow lands");
            helper.assertTrue(l.isDeadOrDying(), "the lantern dies");
            for (Mob m : made) {
                helper.assertTrue(m.isRemoved(), "every illusion vanishes the moment it dies: " + m.getType());
                helper.assertTrue(m.getRemovalReason() == Entity.RemovalReason.DISCARDED, "removed, not killed: " + m.getRemovalReason());
            }
            helper.assertTrue(l.illusions().size() == 0, "it holds none");
            for (EntityType<?> kind : kinds) {
                if (kind == l.getType()) continue;
                helper.assertTrue(player.getStats().getValue(Stats.ENTITY_KILLED, kind) == 0, "no kill of an illusion's kind " + kind);
            }
            int kills = player.getStats().getValue(Stats.CUSTOM, Stats.MOB_KILLS) - before;
            helper.assertTrue(kills == 1, "one mob kill, the lantern, got " + kills);
            helper.succeed();
        });
    }

    /**
     * Failing the counter: a player swings at an illusion instead. It pops at once (gone a tick later), the lantern is
     * untouched at 18, nothing is left where it stood (no items, no experience), it never counts as a kill, and at its
     * next release the lantern conjures a fresh one in its place.
     */
    private static void swingAtIllusion(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(helper.absoluteVec(new Vec3(5.5, 1, -40.0)));          // far enough that orbs never fly to it
        TestPlayers.whenDone(helper, player::discard);
        Mob[] struck = {null};
        Vec3[] where = {null};
        int[] seen = {0, 0};                                                  // orbs, items
        long[] hitAt = {-1};
        helper.runAtTickTime(2, () -> {
            l.release(helper.getLevel());
            l.setUntilConjure(60);
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(l.illusions().size() == 3, "3 illusions, got " + l.illusions().size());
            struck[0] = l.illusions().live().getFirst();
            where[0] = struck[0].position();
            helper.assertTrue(struck[0].hurtServer(helper.getLevel(), struck[0].damageSources().playerAttack(player), 6.0F), "the swing lands");
            hitAt[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (where[0] == null) return;
            ServerLevel level = helper.getLevel();
            seen[0] = Math.max(seen[0], level.getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(where[0], 4, 3, 4)).size());
            seen[1] = Math.max(seen[1], level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(where[0], 4, 3, 4)).size());
            if (helper.getTick() == hitAt[0] + 1) {
                helper.assertTrue(struck[0].isRemoved() && struck[0].getRemovalReason() == Entity.RemovalReason.DISCARDED,
                        "it popped a tick later, removed, not killed: " + struck[0].getRemovalReason());
                helper.assertTrue(l.illusions().size() == 2, "2 left, got " + l.illusions().size());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hitAt[0] >= 0 && helper.getTick() > hitAt[0] + 1, "the swing landed");
            helper.assertTrue(Math.abs(l.getHealth() - 18.0F) < 0.01F, "the lantern is untouched at 18, got " + l.getHealth());
            helper.assertTrue(seen[0] == 0 && seen[1] == 0, "nothing left where it stood: " + seen[0] + " orbs, " + seen[1] + " items");
            helper.assertTrue(player.getStats().getValue(Stats.ENTITY_KILLED, struck[0].getType()) == 0, "no kill counted");
            helper.assertTrue(player.getStats().getValue(Stats.CUSTOM, Stats.MOB_KILLS) == 0, "no mob kill at all");
            helper.assertTrue(l.illusions().size() == 3 && !l.illusions().live().contains(struck[0]),
                    "at its next release a fresh illusion takes its place, holds " + l.illusions().size());
        });
    }

    /**
     * Each blow that lands on the lantern gutters its flame and snuffs its newest illusion: the third made goes, the
     * other two stay; a second blow snuffs the second; a fall, which is no blow, snuffs nothing.
     */
    private static void blowSnuffsNewest(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        List<Mob> made = new ArrayList<>();
        helper.runAtTickTime(2, () -> made.addAll(l.release(helper.getLevel())));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(made.size() == 3, "3 illusions, got " + made.size());
            helper.assertTrue(l.hurtServer(helper.getLevel(), l.damageSources().fall(), 1.0F), "the fall hurts it");
            helper.assertTrue(l.illusions().size() == 3, "a fall snuffs nothing");
            l.damageCooldownTime = 0;                                         // the fall's cooldown would refuse the blow
            helper.assertTrue(l.hurtServer(helper.getLevel(), l.damageSources().mobAttack(pig), 1.0F), "the blow lands");
            helper.assertTrue(made.get(2).isRemoved() && !made.get(0).isRemoved() && !made.get(1).isRemoved(), "the newest is snuffed, the others stay");
            helper.assertTrue(l.illusions().size() == 2, "2 left, got " + l.illusions().size());
        });
        helper.runAtTickTime(20, () -> {
            l.damageCooldownTime = 0;
            helper.assertTrue(l.hurtServer(helper.getLevel(), l.damageSources().mobAttack(pig), 1.0F), "a second blow lands");
            helper.assertTrue(made.get(1).isRemoved() && !made.get(0).isRemoved(), "it snuffs the next newest");
            helper.assertTrue(Math.abs(l.getHealth() - 15.0F) < 0.01F, "the lantern took the fall and both blows: 15, got " + l.getHealth());
            helper.succeed();
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /**
     * An illusion: 1 health (of 1), 0 attack damage, no shadow (the lantern and a real pig keep theirs), no equipment
     * drops, the empty loot table, no experience, no AI of its own, silent, picks nothing up, marked; and the synced
     * slots clients read hold exactly its illusions' ids.
     */
    private static void illusionNumbers(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        helper.runAtTickTime(2, () -> l.release(helper.getLevel()));
        helper.runAtTickTime(4, () -> {
            List<Mob> live = l.illusions().live();
            helper.assertTrue(live.size() == 3, "3 illusions, got " + live.size());
            for (Mob m : live) {
                helper.assertTrue(Math.abs(m.getMaxHealth() - 1.0F) < 0.001F && Math.abs(m.getHealth() - 1.0F) < 0.001F,
                        "1 health of 1, got " + m.getHealth() + "/" + m.getMaxHealth() + " " + m.getType());
                AttributeInstance attack = m.getAttribute(Attributes.ATTACK_DAMAGE);
                helper.assertTrue(attack == null || Math.abs(attack.getValue()) < 0.001, "no attack damage, " + m.getType());
                helper.assertTrue(!PhantasmLantern.castsShadow(m), "no shadow, " + m.getType());
                for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                    helper.assertTrue(m.getDropChances().byEquipment(slot) == 0.0F, "drops none of its gear, " + slot);
                }
                helper.assertTrue(m.getLootTable().isPresent() && m.getLootTable().get().identifier().equals(Identifier.withDefaultNamespace("empty")),
                        "drops no loot: table " + m.getLootTable());
                helper.assertTrue(m.wasExperienceConsumed(), "gives no experience");
                helper.assertTrue(m.isNoAi() && m.isSilent() && !m.canPickUpLoot(), "no AI of its own, silent, picks nothing up");
                helper.assertTrue(Illusions.isIllusion(m), "marked as an illusion");
            }
            helper.assertTrue(PhantasmLantern.castsShadow(l) && PhantasmLantern.castsShadow(pig), "the lantern and the pig keep their shadows");
            int[] ids = l.syncedIllusionIds();
            for (int i = 0; i < 3; i++) helper.assertTrue(ids[i] == live.get(i).getId(), "slot " + i + " holds illusion " + live.get(i).getId() + ", has " + ids[i]);
            helper.succeed();
        });
    }

    /**
     * No damage: the illusions walk up to the pig and swing at it, and the pig is never hurt, 10 health after 60
     * ticks of it with no attacker on record. They are copies of the pig, the only mob of this arena it may copy (the
     * arenas sit closer together than its copy reach, so the kinds next door are left out).
     *
     * <p>An illusion steps out 1.5 to 3 blocks from the lantern, at random, and walks to its own place beside the pig
     * (one of three, 1.35 blocks from it on the lantern's side, the first 2.5 blocks from the lantern); within 0.25 of
     * that place it stands. So one can step out almost where it will stand and barely move. Once out, each is moved to a
     * set start 2.9 to 4 blocks from its place, clear of the lantern, so each one has to walk.
     */
    private static void harmless(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 8.5));
        targets(helper, l, pig);
        int[] swings = {0};
        Vec3[] start = {null};
        double[] walked = {0.0};
        Vec3[] starts = {new Vec3(1.5, 1, 7.5), new Vec3(2.5, 1, 4.5), new Vec3(9.5, 1, 7.5)};
        helper.runAtTickTime(2, () -> {
            l.release(helper.getLevel(), here(helper, l.sources(helper.getLevel(), null)));
            List<Mob> live = l.illusions().live();
            helper.assertTrue(live.size() == 3, "3 illusions, got " + live.size());
            for (int i = 0; i < 3; i++) {
                Vec3 at = helper.absoluteVec(starts[i]);
                live.get(i).snapTo(at.x, at.y, at.z);
            }
            start[0] = live.getFirst().position();
        });
        helper.onEachTick(() -> {
            if (start[0] == null) return;
            for (Mob m : l.illusions().live()) if (m.isSwinging()) swings[0]++;
            if (!l.illusions().live().isEmpty()) walked[0] = Math.max(walked[0], l.illusions().live().getFirst().position().distanceTo(start[0]));
        });
        helper.runAtTickTime(64, () -> {
            helper.assertTrue(l.illusions().size() == 3, "3 illusions still, got " + l.illusions().size());
            helper.assertTrue(walked[0] > 0.3, "they move: the first walked " + walked[0] + " from " + helper.relativeVec(start[0])
                    + ", now at " + l.illusions().live().stream().map(m -> helper.relativeVec(m.position()).toString()).toList());
            helper.assertTrue(swings[0] > 0, "they swing at the pig");
            helper.assertTrue(Math.abs(pig.getHealth() - 10.0F) < 0.01F, "the pig is never hurt: 10, got " + pig.getHealth());
            helper.assertTrue(pig.getLastHurtByMob() == null, "nothing ever hurt it");
            helper.succeed();
        });
    }

    /**
     * Every 200 ticks: with 70 ticks to wait it conjures 50 ticks later and releases when the wait runs out; then it
     * waits 200 ticks, conjures again 180 ticks after the release, and the second release comes 200 ticks after the
     * first. (Its illusions are cleared after each release, so it is never full.)
     */
    private static void every(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        Map<String, List<Integer>> at = steps(helper, l);
        int[] preset = {-1};
        int[] after = {0};
        helper.runAtTickTime(1, () -> {
            l.setUntilConjure(70);
            preset[0] = l.tickCount;
        });
        helper.onEachTick(() -> {
            if (at.getOrDefault("release", List.of()).size() == 1) after[0] = Math.max(after[0], l.untilConjure());
            if (l.illusions().size() > 0) l.illusions().dispel(helper.getLevel());
        });
        helper.succeedWhen(() -> {
            List<Integer> releases = at.getOrDefault("release", List.of()), conjures = at.getOrDefault("conjure", List.of());
            helper.assertTrue(releases.size() >= 2, "it releases twice on its own, at " + releases + ", conjures at " + conjures);
            helper.assertTrue(conjures.size() == 2, "and conjures before each, at " + conjures);
            int lead = conjures.get(0) - preset[0];
            int first = releases.get(0) - preset[0];
            int between = releases.get(1) - releases.get(0);
            int secondLead = conjures.get(1) - releases.get(0);
            helper.assertTrue(lead == 50, "with 70 ticks to wait it starts conjuring 50 ticks later, measured " + lead);
            helper.assertTrue(first == 70, "and releases when the wait runs out, measured " + first);
            helper.assertTrue(after[0] == 200, "after a release it waits 200 ticks, saw " + after[0]);
            helper.assertTrue(secondLead == 180, "it conjures again 180 ticks after the release, measured " + secondLead);
            helper.assertTrue(between == 200, "releases come 200 ticks apart, measured " + between);
        });
    }

    /**
     * Up to 3: with five husks about, a release makes exactly 3; a second release at once makes none (it is full) and
     * it never conjures while full; with one popped, a release makes exactly 1.
     */
    private static void three(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        for (int i = 0; i < 5; i++) husk(helper, new Vec3(1.5 + i * 2.0, 1, 1.5));
        Map<String, List<Integer>> at = steps(helper, l);
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(l.release(level).size() == 3, "a release makes exactly 3");
            helper.assertTrue(l.release(level).isEmpty() && l.illusions().size() == 3, "a second makes none: it holds 3");
            l.setUntilConjure(0);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(!at.containsKey("conjure"), "it never conjures while it holds 3: " + at);
            l.illusions().pop(l.illusions().live().getFirst());
            l.setUntilConjure(100000);
            List<Mob> more = l.release(helper.getLevel());
            helper.assertTrue(more.size() == 1 && l.illusions().size() == 3, "with one gone a release makes exactly 1, made " + more.size());
            helper.succeed();
        });
    }

    /**
     * Range 16: the lantern hangs in a corner near the roof; a husk 15.4 blocks away is one it may copy, a pig 16.7
     * blocks away is not, so (of the mobs in this arena) all its illusions are husks.
     */
    private static void range(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, new Vec3(0.5, 7, 0.5));
        Pig target = pig(helper, new Vec3(3.5, 1, 1.5));
        Husk inside = husk(helper, new Vec3(10.5, 1, 10.5));
        Pig outside = pig(helper, new Vec3(11.5, 1, 11.5));
        targets(helper, l, target);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(inside.distanceTo(l) - 15.36F) < 0.05F && Math.abs(outside.distanceTo(l) - 16.67F) < 0.05F,
                    "a husk at 15.36 and a pig at 16.67, got " + inside.distanceTo(l) + " and " + outside.distanceTo(l));
            List<Mob> sources = l.sources(helper.getLevel(), target);
            helper.assertTrue(sources.contains(inside), "15.4 blocks away it may be copied: " + sources);
            helper.assertTrue(!sources.contains(outside), "16.7 blocks away it may not: " + sources);
            List<Mob> made = l.release(helper.getLevel(), here(helper, sources));
            helper.assertTrue(!made.isEmpty(), "it makes some (in a corner some spots are off the floor)");
            for (Mob m : made) helper.assertTrue(m.getType() == EntityTypes.HUSK, "every illusion is a husk, got " + m.getType());
            helper.succeed();
        });
    }

    /**
     * It copies the mobs about: with two husks near and its target, a pig, the husks are what it may copy and the pig
     * never is; copying from this arena's mobs, its 3 illusions are husks.
     */
    private static void copies(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        Husk a = husk(helper, new Vec3(2.5, 1, 2.5));
        Husk b = husk(helper, new Vec3(8.5, 1, 2.5));
        helper.runAtTickTime(2, () -> {
            List<Mob> sources = here(helper, l.sources(helper.getLevel(), pig));
            helper.assertTrue(sources.size() == 2 && sources.contains(a) && sources.contains(b), "the two husks, never its target: " + sources);
            l.release(helper.getLevel(), sources);
            List<Mob> live = l.illusions().live();
            helper.assertTrue(live.size() == 3, "3 illusions, got " + live.size());
            for (Mob m : live) helper.assertTrue(m.getType() == EntityTypes.HUSK, "copies of the husks about, never its target: " + m.getType());
            helper.succeed();
        });
    }

    /**
     * With nothing about to copy its illusions are hoglins, piglins, endermen or striders; alone with its target in
     * this arena, it finds nothing here to copy.
     */
    private static void homeKinds(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        helper.runAtTickTime(2, () -> {
            List<Mob> sources = here(helper, l.sources(helper.getLevel(), pig));
            helper.assertTrue(sources.isEmpty(), "nothing here to copy: " + sources);
            l.release(helper.getLevel(), sources);
            List<Mob> live = l.illusions().live();
            helper.assertTrue(live.size() == 3, "3 illusions, got " + live.size());
            for (Mob m : live) helper.assertTrue(HOME.contains(m.getType()), "one of its home's kinds, got " + m.getType());
            helper.succeed();
        });
    }

    /**
     * Never players, bosses or apex mobs, never another lantern or Teeming mob, never a mob that hurts without its AI
     * (creepers, slimes, magma cubes, guardians); its home's kinds and the husk can be copied.
     */
    private static void neverCopies(GameTestHelper helper) {
        for (EntityType<?> t : List.<EntityType<?>>of(EntityTypes.PLAYER, EntityTypes.WITHER, EntityTypes.ENDER_DRAGON, EntityTypes.WARDEN,
                EntityTypes.ELDER_GUARDIAN, EntityTypes.CREEPER, EntityTypes.SLIME, EntityTypes.MAGMA_CUBE, EntityTypes.GUARDIAN,
                ModMobs.PHANTASM_LANTERN.get(), ModMobs.HOLLOW_MOURNER.get(), ModMobs.PYRE_HERALD.get())) {
            helper.assertTrue(!Illusions.copyable(t), "never copied: " + t);
        }
        for (EntityType<?> t : List.<EntityType<?>>of(EntityTypes.HOGLIN, EntityTypes.PIGLIN, EntityTypes.ENDERMAN, EntityTypes.STRIDER, EntityTypes.HUSK)) {
            helper.assertTrue(Illusions.copyable(t), "may be copied: " + t);
        }
        helper.succeed();
    }

    /** It conjures only with a target: ready to conjure and without one, it is still idle 40 ticks on. */
    private static void needsTarget(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Map<String, List<Integer>> at = steps(helper, l);
        helper.runAtTickTime(1, () -> l.setUntilConjure(0));
        helper.runAtTickTime(41, () -> {
            helper.assertTrue(!at.containsKey("conjure") && l.core().idle(), "no conjuring without a target: " + at);
            helper.assertTrue(l.illusions().size() == 0, "and no illusions");
            helper.succeed();
        });
    }

    /**
     * An illusion saved and loaded (as a chunk load does) is a still figure: no AI, 1 health, no attack, the empty loot
     * table, silent, still marked. It is refused as it joins the level, so no copy outlives its lantern, while the
     * lantern's own illusions stay.
     */
    private static void reloadSwept(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        Mob[] loaded = {null};
        helper.runAtTickTime(2, () -> l.release(helper.getLevel()));
        helper.runAtTickTime(4, () -> {
            Mob m = l.illusions().live().getFirst();
            ServerLevel level = helper.getLevel();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            m.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            Mob fresh = (Mob) m.getType().create(level, EntitySpawnReason.LOAD);
            helper.assertTrue(fresh != null, "a fresh " + m.getType());
            fresh.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            fresh.setUUID(java.util.UUID.randomUUID());                       // a copy beside the original, as a stray
            fresh.setPos(fresh.position().add(0.0, 0.0, 0.01));
            helper.assertTrue(fresh.isNoAi() && fresh.isSilent(), "it loads with no AI and silent");
            helper.assertTrue(Math.abs(fresh.getMaxHealth() - 1.0F) < 0.001F && Math.abs(fresh.getHealth() - 1.0F) < 0.001F, "1 health of 1");
            AttributeInstance attack = fresh.getAttribute(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(attack == null || Math.abs(attack.getValue()) < 0.001, "no attack damage");
            helper.assertTrue(fresh.getLootTable().isPresent() && fresh.getLootTable().get().identifier().equals(Identifier.withDefaultNamespace("empty")),
                    "no loot: " + fresh.getLootTable());
            helper.assertTrue(Illusions.isIllusion(fresh), "still marked");
            boolean joined = level.addFreshEntity(fresh);
            TestPlayers.whenDone(helper, fresh::discard);
            helper.assertTrue(!joined && level.getEntity(fresh.getUUID()) == null, "a loaded illusion is refused as it joins");
            loaded[0] = fresh;
        });
        helper.runAtTickTime(4 + 21, () -> {
            helper.assertTrue(l.illusions().size() == 3, "its own 3 stay, holds " + l.illusions().size());
            helper.succeed();
        });
    }

    /** However the lantern leaves the level (here discarded, as a despawn does), its illusions go in that same tick. */
    private static void leaves(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        List<Mob> made = new ArrayList<>();
        helper.runAtTickTime(2, () -> made.addAll(l.release(helper.getLevel())));
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(made.size() == 3, "3 illusions, got " + made.size());
            l.discard();
            for (Mob m : made) helper.assertTrue(m.isRemoved(), "gone with it: " + m.getType());
            helper.succeed();
        });
    }

    /**
     * It keeps away: a survival player 3 blocks off, and within 40 ticks it is at least 2 blocks further away, rising
     * off the floor toward its hover height as it goes.
     */
    private static void keepsAway(GameTestHelper helper) {
        PhantasmLantern l = helper.spawn(ModMobs.PHANTASM_LANTERN.get(), new Vec3(7.5, 1, 5.5));
        l.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        l.setUntilConjure(100000);
        TestPlayers.whenDone(helper, l::discard);                           // it may leave the arena
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(10.5, 1, 5.5));
        double[] start = {-1.0};
        double[] fled = {0.0};
        double[] rose = {0.0};
        double floor = helper.absoluteVec(new Vec3(0, 1, 0)).y;
        helper.onEachTick(() -> {
            if (helper.getTick() == 2) start[0] = l.position().subtract(p.position()).horizontalDistance();
            if (start[0] < 0) return;
            fled[0] = Math.max(fled[0], l.position().subtract(p.position()).horizontalDistance() - start[0]);
            rose[0] = Math.max(rose[0], l.getY() - floor);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] > 0 && start[0] < 3.5, "the player starts 3 blocks off, " + start[0]);
            helper.assertTrue(fled[0] >= 2.0, "it backs off: " + fled[0] + " blocks further so far");
            helper.assertTrue(rose[0] >= 0.6, "and rises toward its hover: " + rose[0]);
            helper.assertTrue(helper.getTick() <= 42, "within 40 ticks");
        });
    }

    /** A listener that cancels the "illusion" step for every illusion leaves the release empty. */
    private static void illusionCancel(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        targets(helper, l, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == l && e.step().equals("illusion")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(l.release(helper.getLevel()).isEmpty(), "nothing steps out");
            helper.assertTrue(l.illusions().size() == 0, "it holds none");
            helper.succeed();
        });
    }

    /** Health 18, attack 0, armour 0, a 0.6 x 0.9 hitbox; fire-immune. */
    private static void stats(GameTestHelper helper) {
        PhantasmLantern l = lantern(helper, AT);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(l.getMaxHealth() - 18.0F) < 0.01F && Math.abs(l.getHealth() - 18.0F) < 0.01F,
                    "health 18, got " + l.getHealth() + "/" + l.getMaxHealth());
            helper.assertTrue(Math.abs(l.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.01, "attack 0");
            helper.assertTrue(Math.abs(l.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(l.getBbWidth() - 0.6F) < 0.02F && Math.abs(l.getBbHeight() - 0.9F) < 0.02F,
                    "hitbox 0.6 x 0.9, got " + l.getBbWidth() + " x " + l.getBbHeight());
            helper.assertTrue(l.fireImmune(), "fire-immune");
            helper.succeed();
        });
    }

    /**
     * Home: the warped and crimson forests only, a monster, spawn weight 10 in groups of exactly 1; both forests are
     * share-capped, so its biome-list spawns leave them out and the Teeming spawner places it there. The Crimson Lantern
     * is its second look, the one of the crimson forest.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(TeemingSpawns.tag("phantasm_lantern"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.WARPED_FOREST)), "its home holds the warped forest");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.CRIMSON_FOREST)), "its home holds the crimson forest");
        for (var other : List.of(Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY, Biomes.BASALT_DELTAS, Biomes.PLAINS)) {
            helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("phantasm_lantern");
        helper.assertTrue(row.dim() == RosterStats.Dim.NETHER, "a Nether mob");
        helper.assertTrue(ModMobs.PHANTASM_LANTERN.get().getCategory() == MobCategory.MONSTER, "a monster");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/phantasm_lantern")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.PHANTASM_LANTERN.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 10, "spawn weight 10, got " + entry.weight());
        var forests = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).biomes();
        var biomeLookup = access.lookupOrThrow(Registries.BIOME);
        helper.assertTrue(!forests.contains(biomeLookup.getOrThrow(Biomes.WARPED_FOREST)) && !forests.contains(biomeLookup.getOrThrow(Biomes.CRIMSON_FOREST)),
                "its biome-list spawns leave out the share-capped forests, where the Teeming spawner places it");
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("phantasm_lantern");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("crimson_lantern")), "the Crimson Lantern is a look: " + looks);
        helper.succeed();
    }
}
