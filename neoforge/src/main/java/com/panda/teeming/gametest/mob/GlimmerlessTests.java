package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Glimmerless;
import com.panda.teeming.mob.end.Glimmerless.Footprints;
import com.panda.teeming.mob.end.Glimmerless.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Glimmerless: hidden with its AI on (invisible, drawn faintly only in its tell or as the Shimmering look), in plain
 * sight with its AI off and as it dies; an 8-tick shimmer that holds still and hurts nothing, then a rake of 7; 30
 * ticks between rakes; any projectile hit (a snowball, an egg, an arrow, a trident, a wind charge) reveals it for
 * exactly 200 ticks and a fresh reveal makes it flinch 3 blocks straight back from the shot, breaking off its strike;
 * a melee hit never reveals it; a print every 0.9 blocks it walks, on the ground a foot off its line, gone 60 ticks
 * later; HP 30, Atk 7, Arm 2, 0.7 x 1.9; End highlands and midlands, weight 4, alone. Every number is checked against
 * a plain value.
 */
public final class GlimmerlessTests {
    private GlimmerlessTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("glimmerless_tell", GlimmerlessTests::tell);
        out.accept("glimmerless_counter", GlimmerlessTests::counter);
        out.accept("glimmerless_failure", GlimmerlessTests::failure);
        out.accept("glimmerless_unseen", GlimmerlessTests::unseen);
        out.accept("glimmerless_reveal_200", GlimmerlessTests::reveal200);
        TeemingGameTests.timeLimit("glimmerless_reveal_200", 260);
        out.accept("glimmerless_reveal_arrow", h -> revealedBy(h, "arrow"));
        out.accept("glimmerless_reveal_trident", h -> revealedBy(h, "trident"));
        out.accept("glimmerless_reveal_egg", h -> revealedBy(h, "egg"));
        out.accept("glimmerless_reveal_wind_charge", h -> revealedBy(h, "wind_charge"));
        out.accept("glimmerless_melee_hidden", GlimmerlessTests::meleeHidden);
        out.accept("glimmerless_flinch_once", GlimmerlessTests::flinchOnce);
        out.accept("glimmerless_reveal_cancel", GlimmerlessTests::revealCancel);
        out.accept("glimmerless_cooldown", GlimmerlessTests::cooldown);
        TeemingGameTests.timeLimit("glimmerless_cooldown", 140);
        out.accept("glimmerless_footprints", GlimmerlessTests::footprints);
        TeemingGameTests.timeLimit("glimmerless_footprints", 220);
        out.accept("glimmerless_stats", GlimmerlessTests::stats);
        out.accept("glimmerless_home", GlimmerlessTests::home);
        out.accept("glimmerless_save_load", GlimmerlessTests::saveLoad);
    }

    /**
     * A Glimmerless with the base look and no goals (its hunt still runs from its AI step), a follow range of 1 so it
     * notices no one in a neighbouring arena, removed when the test ends wherever it went.
     */
    private static Glimmerless glim(GameTestHelper helper, double x, double z) {
        Glimmerless g = helper.spawn(ModMobs.GLIMMERLESS.get(), new Vec3(x, 1, z));
        g.core().setLook("base", true);
        g.removeFreeWill();
        g.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, g::discard);
        return g;
    }

    /** A still pig with 100 health and no armour, removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static float lost(Pig pig) {
        return 100.0F - pig.getHealth();
    }

    private static void cleanUp(GameTestHelper helper, Entity e) {
        TestPlayers.whenDone(helper, () -> {
            if (!e.isRemoved()) e.discard();
        });
    }

    /** A snowball with no gravity at {@code from} flying along {@code dir} at 0.9 blocks a tick, thrown by {@code owner}. */
    private static Snowball snowball(GameTestHelper helper, Vec3 from, Vec3 dir, Entity owner) {
        Snowball ball = helper.spawn(EntityTypes.SNOWBALL, from);
        if (owner != null) ball.setOwner(owner);
        ball.setNoGravity(true);
        ball.setDeltaMovement(dir.normalize().scale(0.9));
        cleanUp(helper, ball);
        return ball;
    }

    /**
     * The tell: 8 ticks held exactly, standing still, the pig unhurt, hidden but drawn faintly the whole time; then the
     * rake.
     */
    private static void tell(GameTestHelper helper) {
        Glimmerless g = glim(helper, 5.5, 5.5);
        Pig pig = pig(helper, 5.5, 6.7);
        g.setTarget(pig);
        int[] tell = {0};
        Vec3[] start = {null};
        boolean[] next = {false};
        helper.onEachTick(() -> {
            if (g.core().in(Step.TELL)) {
                if (start[0] == null) start[0] = g.position();
                tell[0]++;
                helper.assertTrue(Math.hypot(g.getX() - start[0].x, g.getZ() - start[0].z) < 0.01, "it holds still through its tell");
                helper.assertTrue(lost(pig) == 0.0F, "the tell hurts nothing yet");
                helper.assertTrue(g.drawnFaint(), "its body shows faintly through the tell");
                if (tell[0] > 1) helper.assertTrue(g.hidden() && g.isInvisible(), "and it is still hidden");
            } else if (tell[0] > 0 && !next[0]) {
                next[0] = true;
                helper.assertTrue(g.core().in(Step.STRIKE), "the tell ends in the rake, phase " + g.phase());
                helper.assertTrue(!g.drawnFaint(), "no longer drawn faintly");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(next[0], "it shimmered and raked");
            helper.assertTrue(tell[0] == 8, "the tell lasts 8 ticks, saw " + tell[0]);
        });
    }

    /** Failing the counter: the rake lands for exactly 7, and it stays hidden. */
    private static void failure(GameTestHelper helper) {
        Glimmerless g = glim(helper, 5.5, 5.5);
        Pig pig = pig(helper, 5.5, 6.7);
        g.setTarget(pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(g.strikes() == 1, "one rake landed, " + g.strikes());
            helper.assertTrue(Math.abs(lost(pig) - 7.0F) < 0.01F, "the rake takes exactly 7, took " + lost(pig));
            helper.assertTrue(g.hidden() && g.isInvisible() && g.reveals() == 0, "and it is still unseen");
        });
    }

    /**
     * The counter: a snowball hits it in the middle of its tell. It is revealed (no longer invisible), flinches back
     * instead of raking, and backs off 3 blocks straight away from the thrower; the pig is never hurt.
     */
    private static void counter(GameTestHelper helper) {
        Glimmerless g = glim(helper, 6.5, 5.5);
        Pig pig = pig(helper, 6.5, 6.7);
        Pig thrower = pig(helper, 2.5, 5.5);
        g.setTarget(pig);
        Snowball[] ball = {null};
        int[] tell = {0};
        Vec3[] hitAt = {null};
        boolean[] flinched = {false};
        helper.onEachTick(() -> {
            helper.assertTrue(lost(pig) == 0.0F, "the pig is never hurt");
            if (g.core().in(Step.TELL)) {
                tell[0]++;
                if (ball[0] == null && tell[0] == 2) ball[0] = snowball(helper, new Vec3(5.0, 1.5, 5.5), new Vec3(1, 0, 0), thrower);
            }
            if (hitAt[0] == null && g.reveals() > 0) {
                hitAt[0] = g.position();
                helper.assertTrue(tell[0] < 8, "the snowball landed inside the tell, after " + tell[0]);
                helper.assertTrue(g.revealed() && !g.hidden() && !g.isInvisible(), "the hit reveals it");
                helper.assertTrue(g.core().in(Step.RECOIL), "and it flinches, phase " + g.phase());
            }
            if (g.core().in(Step.RECOIL)) flinched[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hitAt[0] != null && flinched[0], "it was hit and flinched");
            helper.assertTrue(g.core().idle(), "the flinch is over");
            helper.assertTrue(g.strikes() == 0, "no rake landed");
            double back = g.getX() - hitAt[0].x;
            helper.assertTrue(back > 2.8 && back < 3.35, "it backed off 3 blocks straight away from the thrower, " + back);
            helper.assertTrue(Math.abs(g.getZ() - hitAt[0].z) < 0.2, "straight back, z moved " + (g.getZ() - hitAt[0].z));
            helper.assertTrue(!g.hidden(), "and it is still seen");
        });
    }

    /**
     * Unseen: with its AI on it is invisible, and a player can't see it; the Shimmering look is invisible yet drawn
     * faintly; with its AI off it stands in plain sight; dying, it shows.
     */
    private static void unseen(GameTestHelper helper) {
        Glimmerless g = glim(helper, 3.5, 5.5);
        Glimmerless shimmering = glim(helper, 6.5, 5.5);
        shimmering.core().setLook("shimmering", true);
        Glimmerless statue = glim(helper, 9.5, 5.5);
        statue.setNoAi(true);
        Player viewer = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(g.hidden() && g.isInvisible() && g.isInvisibleTo(viewer), "hidden: invisible, and unseen by a player");
            helper.assertTrue(!g.drawnFaint(), "and not even faintly drawn at rest");
            helper.assertTrue(shimmering.isInvisible() && !shimmering.isInvisibleTo(viewer), "the Shimmering look is hidden but drawn faintly");
            helper.assertTrue(!statue.hidden() && !statue.isInvisible(), "with its AI off it stands in plain sight");
            g.kill(helper.getLevel());
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(!g.isInvisible(), "it dies in plain sight");
            helper.succeed();
        });
    }

    /** A snowball reveals it for exactly 200 ticks, counted tick by tick until it is hidden again. */
    private static void reveal200(GameTestHelper helper) {
        Glimmerless g = glim(helper, 5.5, 5.5);
        int[] shown = {0};
        boolean[] hit = {false};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(g.hidden(), "hidden before the hit");
            snowball(helper, new Vec3(4.0, 1.5, 5.5), new Vec3(1, 0, 0), null);
        });
        helper.onEachTick(() -> {
            if (helper.getTick() <= 3) return;
            if (g.reveals() > 0) hit[0] = true;
            if (hit[0] && !g.isInvisible()) shown[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hit[0] && g.reveals() == 1, "the snowball hit it");
            helper.assertTrue(g.hidden() && g.isInvisible(), "hidden again");
            helper.assertTrue(shown[0] == 200, "seen for exactly 200 ticks, saw " + shown[0]);
        });
    }

    /** Each other projectile reveals it too: an arrow, a trident, an egg and a wind charge, real ones in flight. */
    private static void revealedBy(GameTestHelper helper, String kind) {
        Glimmerless g = glim(helper, 6.5, 5.5);
        ServerLevel level = helper.getLevel();
        // an egg may hatch a chick where it breaks
        TestPlayers.whenDone(helper, () -> level.getEntitiesOfClass(Chicken.class, helper.getBounds(), c -> true).forEach(Entity::discard));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(g.hidden(), "hidden before the " + kind + " is loosed");
            Vec3 from = helper.absoluteVec(new Vec3(4.0, 1.5, 5.5));
            Projectile shot = switch (kind) {
                case "arrow" -> new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
                case "trident" -> new ThrownTrident(level, from.x, from.y, from.z, new ItemStack(Items.TRIDENT));
                case "egg" -> EntityTypes.EGG.create(level, EntitySpawnReason.TRIGGERED);
                default -> EntityTypes.WIND_CHARGE.create(level, EntitySpawnReason.TRIGGERED);
            };
            shot.setPos(from);
            shot.setNoGravity(true);
            shot.setDeltaMovement(0.9, 0.0, 0.0);
            level.addFreshEntity(shot);
            cleanUp(helper, shot);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(g.reveals() == 1, "the " + kind + " revealed it");
            helper.assertTrue(!g.hidden() && !g.isInvisible() && g.revealLeft() > 150, "it is seen, " + g.revealLeft() + " ticks left");
        });
    }

    /** A melee blow hurts it but never reveals it. */
    private static void meleeHidden(GameTestHelper helper) {
        Glimmerless g = glim(helper, 5.5, 5.5);
        Pig hitter = pig(helper, 5.5, 7.5);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(g.hurtServer(helper.getLevel(), g.damageSources().mobAttack(hitter), 4.0F), "the blow lands");
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(g.getHealth() < g.getMaxHealth(), "it is hurt");
            helper.assertTrue(g.reveals() == 0 && g.hidden() && g.isInvisible(), "but still hidden");
            helper.succeed();
        });
    }

    /**
     * A second hit while it is seen keeps it seen for the full 200 again, without a second flinch: once the first
     * flinch is over, the second hit leaves it idle.
     */
    private static void flinchOnce(GameTestHelper helper) {
        Glimmerless g = glim(helper, 5.5, 5.5);
        ServerLevel level = helper.getLevel();
        Snowball ball = EntityTypes.SNOWBALL.create(level, EntitySpawnReason.TRIGGERED);
        ball.setPos(helper.absoluteVec(new Vec3(4.5, 1.5, 5.5)));
        helper.runAtTickTime(2, () -> {
            g.hurtServer(level, g.damageSources().thrown(ball, null), 0.0F);
            helper.assertTrue(g.reveals() == 1 && g.core().in(Step.RECOIL), "the first hit reveals it and it flinches");
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(g.core().idle(), "the flinch is over, phase " + g.phase());
            helper.assertTrue(g.revealLeft() < 160, "time has run off the reveal, " + g.revealLeft());
            g.hurtServer(level, g.damageSources().thrown(ball, null), 0.0F);
            helper.assertTrue(g.reveals() == 2, "the second hit counts");
            helper.assertTrue(g.revealLeft() == 200, "it is seen for the full 200 again, " + g.revealLeft());
            helper.assertTrue(g.core().idle(), "with no second flinch, phase " + g.phase());
            helper.succeed();
        });
    }

    /** A pack that vetoes the reveal keeps it hidden through a projectile hit. */
    private static void revealCancel(GameTestHelper helper) {
        Glimmerless g = glim(helper, 5.5, 5.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == g && e.step().equals("reveal")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        ServerLevel level = helper.getLevel();
        Snowball ball = EntityTypes.SNOWBALL.create(level, EntitySpawnReason.TRIGGERED);
        helper.runAtTickTime(2, () -> g.hurtServer(level, g.damageSources().thrown(ball, null), 0.0F));
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(g.reveals() == 0 && g.hidden() && g.core().idle(), "the vetoed reveal changes nothing");
            helper.succeed();
        });
    }

    /** The rake holds 8 ticks, and the next tell starts exactly 30 ticks after it ends. */
    private static void cooldown(GameTestHelper helper) {
        Glimmerless g = glim(helper, 5.5, 5.5);
        Pig pig = pig(helper, 5.5, 6.7);
        g.setTarget(pig);
        long[] strikeStart = {-1}, strikeEnd = {-1}, nextTell = {-1};
        Step[] last = {Step.IDLE};
        helper.onEachTick(() -> {
            Step now = (Step) g.phase();
            long t = helper.getTick();
            if (now != last[0]) {
                if (now == Step.STRIKE && strikeStart[0] < 0) strikeStart[0] = t;
                if (last[0] == Step.STRIKE && strikeEnd[0] < 0) strikeEnd[0] = t;
                if (now == Step.TELL && strikeEnd[0] >= 0 && nextTell[0] < 0) nextTell[0] = t;
                last[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(nextTell[0] >= 0, "a second tell started");
            helper.assertTrue(strikeEnd[0] - strikeStart[0] == 8, "the rake held 8 ticks, saw " + (strikeEnd[0] - strikeStart[0]));
            helper.assertTrue(nextTell[0] - strikeEnd[0] == 30, "30 ticks between rakes, saw " + (nextTell[0] - strikeEnd[0]));
        });
    }

    /**
     * Footprints: walking 7 blocks along the arena it leaves a print every 0.9 blocks, each on the ground a foot to one
     * side of its line, left and right in turn; every one is gone 60 ticks after it was left.
     */
    private static void footprints(GameTestHelper helper) {
        Glimmerless g = glim(helper, 2.5, 5.5);
        Vec3 goal = helper.absoluteVec(new Vec3(9.5, 1, 5.5));
        double floor = helper.absoluteVec(new Vec3(0, 1, 0)).y;
        double line = helper.absoluteVec(new Vec3(0, 1, 5.5)).z;
        boolean[] arrived = {false};
        List<Footprints.Print> all = new ArrayList<>();
        helper.onEachTick(() -> {
            for (Footprints.Print p : g.footprints()) if (!all.contains(p)) all.add(p);
            if (arrived[0]) return;
            g.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 1.0);
            if (Math.abs(g.getX() - goal.x) < 0.3) arrived[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(arrived[0], "it walked across");
            List<Footprints.Print> p = all;
            helper.assertTrue(p.size() >= 6 && p.size() <= 8, "about one print per 0.9 of 7 blocks, got " + p.size());
            for (int i = 0; i < p.size(); i++) {
                Footprints.Print a = p.get(i);
                helper.assertTrue(Math.abs(a.y() - floor) < 0.01, "print " + i + " lies on the floor, y " + a.y() + " floor " + floor);
                helper.assertTrue(Math.abs(Math.abs(a.z() - line) - 0.18) < 0.06, "print " + i + " a foot off its line, " + (a.z() - line));
                if (i > 0) {
                    Footprints.Print b = p.get(i - 1);
                    double gap = a.x() - b.x();
                    helper.assertTrue(gap > 0.75 && gap < 1.1, "prints 0.9 apart along the walk, " + gap);
                    helper.assertTrue(a.side() == -b.side(), "left and right in turn");
                }
            }
            long lastBorn = p.getLast().born();
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(now - lastBorn >= 60, "waiting for the last print to fade");
            helper.assertTrue(g.footprints().isEmpty(), "every print is gone 60 ticks after it was left");
        });
        helper.runAtTickTime(2, () -> helper.assertTrue(g.hidden(), "it walks unseen"));
    }

    /** HP 30, attack 7, armour 2, a 0.7 x 1.9 hitbox. */
    private static void stats(GameTestHelper helper) {
        Glimmerless g = glim(helper, 5.5, 5.5);
        g.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(g.getMaxHealth() == 30.0F && g.getHealth() == 30.0F, "health 30, got " + g.getHealth() + "/" + g.getMaxHealth());
            helper.assertTrue(g.getAttributeValue(Attributes.ATTACK_DAMAGE) == 7.0, "attack 7, got " + g.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(g.getArmorValue() == 2, "armour 2, got " + g.getArmorValue());
            helper.assertTrue(Math.abs(g.getBbWidth() - 0.7F) < 0.01F && Math.abs(g.getBbHeight() - 1.9F) < 0.01F,
                    "a 0.7 x 1.9 hitbox, got " + g.getBbWidth() + " x " + g.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: the End highlands and midlands, never the main island; spawn weight 4, alone; a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("glimmerless"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)), "its home holds the End highlands");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "and the End midlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.THE_END) || b.is(Biomes.SMALL_END_ISLANDS) || b.is(Biomes.END_BARRENS)),
                "never the main island, the small islands or the barrens");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/glimmerless")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.GLIMMERLESS.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.GLIMMERLESS.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }

    /** The reveal and the strike cooldown survive a save and load. */
    private static void saveLoad(GameTestHelper helper) {
        Glimmerless g = glim(helper, 5.5, 5.5);
        ServerLevel level = helper.getLevel();
        Snowball ball = EntityTypes.SNOWBALL.create(level, EntitySpawnReason.TRIGGERED);
        Glimmerless[] copy = {null};
        int[] left = {0};
        helper.runAtTickTime(2, () -> g.hurtServer(level, g.damageSources().thrown(ball, null), 0.0F));
        helper.runAtTickTime(12, () -> {
            left[0] = g.revealLeft();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            g.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            Glimmerless c = ModMobs.GLIMMERLESS.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            g.discard();
            c.removeFreeWill();
            c.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, c::discard);
            copy[0] = c;
        });
        helper.runAtTickTime(15, () -> {
            Glimmerless c = copy[0];
            helper.assertTrue(left[0] > 180 && left[0] < 200, "it was seen with " + left[0] + " ticks left");
            helper.assertTrue(Math.abs(c.revealLeft() - (left[0] - 3)) <= 2, "the reveal carries over: " + left[0] + " then " + c.revealLeft());
            helper.assertTrue(!c.hidden() && !c.isInvisible(), "and it is still seen");
            helper.succeed();
        });
    }
}
