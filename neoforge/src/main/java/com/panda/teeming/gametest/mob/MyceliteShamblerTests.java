package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.overworld.MyceliteShambler;
import com.panda.teeming.mob.overworld.MyceliteShambler.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Mycelite Shambler: health 24, attack 3, armour 0, a 1.0 x 1.8 hitbox. When it dies it bursts, as its body vanishes
 * a second after the killing blow, into two of the next size: scale 0.7 and 12 health, then scale 0.45 and 6 health,
 * which don't burst. Each burst puffs Poison I for 40 ticks onto everything within 2 blocks but shamblers. A shambler
 * that burns or dies to fire doesn't burst and doesn't puff. Loot (the spore sac) only drops from a death that
 * doesn't burst.
 *
 * <p>The shambler stands in the middle of the arena with its goals cleared; still pigs stand in for whoever is near
 * the burst. Kills use a generic source unless the test is about who killed it, so the new shamblers have no target
 * to walk off after; the ones a burst leaves get their goals cleared as soon as they are found.
 */
public final class MyceliteShamblerTests {
    private MyceliteShamblerTests() {}

    private static final Vec3 AT = new Vec3(5.5, 1, 5.5);
    /** Ticks from the killing blow to the burst: vanilla's death time, as a slime's split. */
    private static final int DEATH_TICKS = 20;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("mycelite_shambler_tell", MyceliteShamblerTests::tell);
        out.accept("mycelite_shambler_counter", MyceliteShamblerTests::burningDoesntBurst);
        out.accept("mycelite_shambler_fire_kill", MyceliteShamblerTests::fireKillDoesntBurst);
        out.accept("mycelite_shambler_failure", MyceliteShamblerTests::burstsInTwo);
        out.accept("mycelite_shambler_medium", MyceliteShamblerTests::mediumBursts);
        out.accept("mycelite_shambler_smallest", MyceliteShamblerTests::smallestDoesnt);
        out.accept("mycelite_shambler_radius", MyceliteShamblerTests::radius);
        out.accept("mycelite_shambler_poison", MyceliteShamblerTests::poison);
        out.accept("mycelite_shambler_sprout", MyceliteShamblerTests::sprout);
        out.accept("mycelite_shambler_sizes", MyceliteShamblerTests::sizes);
        out.accept("mycelite_shambler_stats", MyceliteShamblerTests::stats);
        out.accept("mycelite_shambler_attack", MyceliteShamblerTests::attack);
        TeemingGameTests.timeLimit("mycelite_shambler_attack", 200);
        out.accept("mycelite_shambler_small_attack", MyceliteShamblerTests::smallAttack);
        TeemingGameTests.timeLimit("mycelite_shambler_small_attack", 200);
        out.accept("mycelite_shambler_home", MyceliteShamblerTests::home);
        out.accept("mycelite_shambler_burst_cancel", MyceliteShamblerTests::burstCancel);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A shambler in the middle of the arena with every goal cleared. */
    private static MyceliteShambler shambler(GameTestHelper helper) {
        MyceliteShambler s = helper.spawn(ModMobs.MYCELITE_SHAMBLER.get(), AT);
        s.removeFreeWill();
        return s;
    }

    private static Vec3 around(double d, double deg) {
        double a = Math.toRadians(deg);
        return AT.add(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    private static Pig pig(GameTestHelper helper, double d, double deg) {
        Pig pig = helper.spawn(EntityTypes.PIG, around(d, deg));
        pig.setNoAi(true);
        return pig;
    }

    private static void kill(GameTestHelper helper, MyceliteShambler s) {
        s.hurtServer(helper.getLevel(), s.damageSources().generic(), 1000.0F);
        helper.assertTrue(s.isDeadOrDying(), "the blow kills it");
    }

    /** Shamblers in a small box round the middle of this arena, but {@code not}. */
    private static List<MyceliteShambler> near(GameTestHelper helper, MyceliteShambler not) {
        AABB box = new AABB(helper.absoluteVec(AT), helper.absoluteVec(AT)).inflate(3.0, 2.0, 3.0);
        return helper.getLevel().getEntitiesOfClass(MyceliteShambler.class, box, e -> e != not && e.isAlive());
    }

    /** Spore sacs lying round the middle of this arena. */
    private static int sporeSacs(GameTestHelper helper) {
        AABB box = new AABB(helper.absoluteVec(AT), helper.absoluteVec(AT)).inflate(3.0, 2.0, 3.0);
        int n = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).equals(TeemingMod.id("spore_sac"))) n += item.getItem().getCount();
        }
        return n;
    }

    /** Records the game time each time this shambler posts a mechanic step; removed when the test ends. */
    private static Map<String, List<Long>> steps(GameTestHelper helper, MyceliteShambler s) {
        Map<String, List<Long>> at = new HashMap<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == s) at.computeIfAbsent(e.step(), k -> new ArrayList<>()).add(helper.getLevel().getGameTime());
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    private static boolean poisoned(Pig pig) {
        return pig.hasEffect(MobEffects.POISON);
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * It has no tell before a swing; the warning it gives is before the burst. From the killing blow until its body
     * vanishes (20 ticks) it is marked as bursting, its cap swelling, and nothing happens yet: no new shamblers and no
     * poison on the pig beside it. The burst comes when the body vanishes, 20 ticks after the blow.
     */
    private static void tell(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        Pig pig = pig(helper, 1.0, 0);
        Map<String, List<Long>> at = steps(helper, s);
        int[] held = {0};
        boolean[] early = {false};
        boolean[] flagged = {true};
        helper.runAtTickTime(2, () -> {
            kill(helper, s);
            helper.assertTrue(s.willBurst(), "a full-grown shambler killed by a sword's worth of damage will burst");
        });
        helper.onEachTick(() -> {
            if (!s.isDeadOrDying() || s.isRemoved()) return;
            held[0]++;
            flagged[0] &= s.core().flag(MyceliteShambler.FLAG_BURSTING);
            early[0] |= poisoned(pig) || !near(helper, s).isEmpty();
        });
        helper.runAtTickTime(2 + DEATH_TICKS + 4, () -> {
            helper.assertTrue(at.containsKey("burst") && at.containsKey("spores"), "it was marked to burst, then puffed: " + at);
            long wait = at.get("spores").getFirst() - at.get("burst").getFirst();
            helper.assertTrue(Math.abs(wait - 20) <= 1, "the burst comes 20 ticks after the blow, measured " + wait);
            helper.assertTrue(Math.abs(held[0] - 20) <= 1, "it lies dying 20 ticks, held " + held[0]);
            helper.assertTrue(flagged[0], "it shows it will burst the whole time");
            helper.assertTrue(!early[0], "nothing happens before the burst");
            helper.assertTrue(s.isRemoved() && near(helper, s).size() == 2, "then it is gone and two stand in its place, " + near(helper, s).size());
            helper.succeed();
        });
    }

    /**
     * The counter: a shambler that is burning when it dies doesn't burst. No new shamblers, no poison on the pig a
     * block away, no spores step; it drops its spore sac (loot comes from a death that doesn't burst).
     */
    private static void burningDoesntBurst(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        Pig pig = pig(helper, 1.0, 0);
        Map<String, List<Long>> at = steps(helper, s);
        helper.runAtTickTime(2, () -> {
            s.igniteForTicks(100);
            helper.assertTrue(s.isOnFire(), "it burns");
            kill(helper, s);
            helper.assertTrue(!s.willBurst(), "a burning shambler won't burst");
            helper.assertTrue(!s.core().flag(MyceliteShambler.FLAG_BURSTING), "and doesn't show it will");
        });
        helper.runAtTickTime(4, () -> helper.assertTrue(sporeSacs(helper) == 1, "it drops one spore sac, found " + sporeSacs(helper)));
        helper.runAtTickTime(2 + DEATH_TICKS + 6, () -> {
            helper.assertTrue(s.isRemoved(), "its body is gone");
            helper.assertTrue(near(helper, s).isEmpty(), "nothing bursts out of it, found " + near(helper, s).size());
            helper.assertTrue(!poisoned(pig), "the pig beside it isn't poisoned");
            helper.assertTrue(!at.containsKey("spores"), "no spores: " + at);
            helper.succeed();
        });
    }

    /** Dying to fire damage stops the burst too, though it wasn't burning: no new shamblers, no poison. */
    private static void fireKillDoesntBurst(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        Pig pig = pig(helper, 1.0, 90);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!s.isOnFire(), "it isn't burning");
            s.hurtServer(helper.getLevel(), s.damageSources().inFire(), 1000.0F);
            helper.assertTrue(s.isDeadOrDying(), "fire kills it");
            helper.assertTrue(!s.willBurst(), "a shambler killed by fire won't burst");
        });
        helper.runAtTickTime(2 + DEATH_TICKS + 6, () -> {
            helper.assertTrue(s.isRemoved(), "its body is gone");
            helper.assertTrue(near(helper, s).isEmpty(), "nothing bursts out of it, found " + near(helper, s).size());
            helper.assertTrue(!poisoned(pig), "the pig beside it isn't poisoned");
            helper.succeed();
        });
    }

    /**
     * Failing the counter: a full-grown Crimson Cap shambler killed by a plain blow bursts into exactly two Crimson Cap
     * shamblers of scale 0.7 with 12 of 12 health and a 0.7 x 1.26 hitbox, each a Mycelite Shambler (a monster that
     * counts against the caps like any other). The pig a block away gets Poison I for 40 ticks. No spore sac drops
     * from a death that bursts.
     */
    private static void burstsInTwo(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        Pig pig = pig(helper, 1.0, 180);
        List<MyceliteShambler> kids = new ArrayList<>();
        int[] poisonLeft = {-1};
        int[] poisonLevel = {-1};
        helper.runAtTickTime(1, () -> {
            s.core().setLook("crimson_cap", true);
            kill(helper, s);
        });
        helper.onEachTick(() -> {
            if (!s.isRemoved()) return;
            if (kids.isEmpty()) {
                kids.addAll(near(helper, s));
                kids.forEach(k -> k.removeFreeWill());
            }
            MobEffectInstance poison = pig.getEffect(MobEffects.POISON);
            if (poisonLeft[0] < 0 && poison != null) {
                poisonLeft[0] = poison.getDuration();
                poisonLevel[0] = poison.getAmplifier();
            }
        });
        helper.runAtTickTime(1 + DEATH_TICKS + 4, () -> {
            helper.assertTrue(s.isRemoved(), "the first one is gone");
            helper.assertTrue(kids.size() == 2, "it bursts into exactly two, found " + kids.size());
            for (MyceliteShambler k : kids) {
                helper.assertTrue(k.getType() == s.getType() && k.getType().getCategory() == MobCategory.MONSTER,
                        "each is a Mycelite Shambler, a monster");
                helper.assertTrue("crimson_cap".equals(k.look()), "in the Crimson Cap look, got " + k.look());
                helper.assertTrue(Math.abs(k.getScale() - 0.7F) < 0.01F, "scale 0.7, got " + k.getScale());
                helper.assertTrue(Math.abs(k.getMaxHealth() - 12.0F) < 0.01F && Math.abs(k.getHealth() - 12.0F) < 0.01F,
                        "12 of 12 health, got " + k.getHealth() + "/" + k.getMaxHealth());
                helper.assertTrue(Math.abs(k.getBbWidth() - 0.7F) < 0.02F && Math.abs(k.getBbHeight() - 1.26F) < 0.02F,
                        "a 0.7 x 1.26 hitbox, got " + k.getBbWidth() + " x " + k.getBbHeight());
            }
            helper.assertTrue(poisonLevel[0] == 0, "the pig gets Poison I, amplifier " + poisonLevel[0]);
            helper.assertTrue(poisonLeft[0] > 37 && poisonLeft[0] <= 40, "for 40 ticks, " + poisonLeft[0] + " left when first seen");
            helper.assertTrue(sporeSacs(helper) == 0, "a death that bursts drops no spore sac, found " + sporeSacs(helper));
            helper.succeed();
        });
    }

    /** A scale 0.7 shambler bursts into two of scale 0.45 with 6 of 6 health and a 0.45 x 0.81 hitbox. */
    private static void mediumBursts(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        Pig pig = pig(helper, 1.0, 270);
        List<MyceliteShambler> kids = new ArrayList<>();
        helper.runAtTickTime(1, () -> {
            s.setSize(MyceliteShambler.MEDIUM, true);
            kill(helper, s);
            helper.assertTrue(s.willBurst(), "a scale 0.7 shambler bursts too");
        });
        helper.onEachTick(() -> {
            if (s.isRemoved() && kids.isEmpty()) {
                kids.addAll(near(helper, s));
                kids.forEach(k -> k.removeFreeWill());
            }
        });
        helper.runAtTickTime(1 + DEATH_TICKS + 4, () -> {
            helper.assertTrue(kids.size() == 2, "it bursts into exactly two, found " + kids.size());
            for (MyceliteShambler k : kids) {
                helper.assertTrue(Math.abs(k.getScale() - 0.45F) < 0.01F, "scale 0.45, got " + k.getScale());
                helper.assertTrue(Math.abs(k.getMaxHealth() - 6.0F) < 0.01F && Math.abs(k.getHealth() - 6.0F) < 0.01F,
                        "6 of 6 health, got " + k.getHealth() + "/" + k.getMaxHealth());
                helper.assertTrue(Math.abs(k.getBbWidth() - 0.45F) < 0.02F && Math.abs(k.getBbHeight() - 0.81F) < 0.02F,
                        "a 0.45 x 0.81 hitbox, got " + k.getBbWidth() + " x " + k.getBbHeight());
            }
            helper.assertTrue(poisoned(pig), "its burst poisons the pig too");
            helper.succeed();
        });
    }

    /** The smallest (scale 0.45) doesn't burst: no new shamblers, no puff, and it drops its spore sac. */
    private static void smallestDoesnt(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        Pig pig = pig(helper, 1.0, 45);
        Map<String, List<Long>> at = steps(helper, s);
        helper.runAtTickTime(1, () -> {
            s.setSize(MyceliteShambler.SMALL, true);
            kill(helper, s);
            helper.assertTrue(!s.willBurst(), "the smallest won't burst");
        });
        helper.runAtTickTime(3, () -> helper.assertTrue(sporeSacs(helper) == 1, "it drops one spore sac, found " + sporeSacs(helper)));
        helper.runAtTickTime(1 + DEATH_TICKS + 6, () -> {
            helper.assertTrue(s.isRemoved(), "its body is gone");
            helper.assertTrue(near(helper, s).isEmpty(), "nothing comes out of it, found " + near(helper, s).size());
            helper.assertTrue(!poisoned(pig) && !at.containsKey("spores"), "and no spores: " + at);
            helper.succeed();
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /**
     * The puff reaches 2 blocks: a pig 1.5 blocks away is poisoned, one 2.5 blocks away is not, and another shambler
     * a block away is spared.
     */
    private static void radius(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        Pig inside = pig(helper, 1.5, 0);
        Pig outside = pig(helper, 2.5, 180);
        MyceliteShambler other = helper.spawn(ModMobs.MYCELITE_SHAMBLER.get(), around(1.0, 90));
        other.removeFreeWill();
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(inside.distanceTo(s) - 1.5F) < 0.05F && Math.abs(outside.distanceTo(s) - 2.5F) < 0.05F,
                    "pigs at 1.5 and 2.5, got " + inside.distanceTo(s) + " and " + outside.distanceTo(s));
            int n = s.puff(helper.getLevel());
            helper.assertTrue(n == 1, "one pig inside 2 blocks is poisoned, poisoned " + n);
            helper.assertTrue(poisoned(inside), "1.5 blocks away is inside the reach");
            helper.assertTrue(!poisoned(outside), "2.5 blocks away is outside it");
            helper.assertTrue(!other.hasEffect(MobEffects.POISON), "shamblers are spared");
            helper.succeed();
        });
    }

    /**
     * Poison I for 40 ticks: amplifier 0 and 40 ticks at the puff; the pig is poisoned for 40 ticks, counted, and
     * loses exactly 1 health in them (Poison I hurts every 25 ticks; Poison II would hurt 3 times).
     */
    private static void poison(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        Pig pig = pig(helper, 1.0, 0);
        float[] before = {0};
        int[] ticks = {0};
        long[] puffed = {-1};
        helper.runAtTickTime(2, () -> {
            before[0] = pig.getHealth();
            helper.assertTrue(s.puff(helper.getLevel()) == 1, "the pig is poisoned");
            MobEffectInstance poison = pig.getEffect(MobEffects.POISON);
            helper.assertTrue(poison != null && poison.getAmplifier() == 0, "Poison I");
            helper.assertTrue(poison.getDuration() == 40, "for 40 ticks, " + poison.getDuration());
            puffed[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (puffed[0] >= 0 && helper.getTick() > puffed[0] && poisoned(pig)) ticks[0]++;
        });
        helper.runAtTickTime(2 + 50, () -> {
            helper.assertTrue(Math.abs(ticks[0] - 40) <= 1, "poisoned for 40 ticks, counted " + ticks[0]);
            float lost = before[0] - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 1.0F) < 0.01F, "Poison I takes 1 health in 40 ticks, took " + lost);
            helper.succeed();
        });
    }

    /** A new shambler stands up out of the burst for 10 ticks (counted), still and not fighting, then is idle. */
    private static void sprout(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        List<MyceliteShambler> kids = new ArrayList<>();
        int[] held = {0};
        helper.runAtTickTime(1, () -> kids.addAll(s.burst(helper.getLevel())));
        helper.onEachTick(() -> {
            if (!kids.isEmpty() && kids.getFirst().core().in(Step.SPROUT)) held[0]++;
        });
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(kids.size() == 2, "the burst leaves two, got " + kids.size());
            helper.assertTrue(kids.getFirst().core().in(Step.SPROUT), "standing up out of the burst, phase " + kids.getFirst().phase());
        });
        helper.runAtTickTime(1 + 10 + 5, () -> {
            helper.assertTrue(Math.abs(held[0] - 10) <= 1, "it stands up for 10 ticks, held " + held[0]);
            helper.assertTrue(kids.getFirst().core().idle(), "then it is idle, phase " + kids.getFirst().phase());
            kids.forEach(k -> k.discard());
            s.discard();
            helper.succeed();
        });
    }

    /** The sizes on living shamblers: 1.0, 0.7 and 0.45 scale; 24, 12 and 6 health; hitboxes scaled from 1.0 x 1.8. */
    private static void sizes(GameTestHelper helper) {
        MyceliteShambler large = shambler(helper);
        MyceliteShambler medium = helper.spawn(ModMobs.MYCELITE_SHAMBLER.get(), around(3.0, 0));
        MyceliteShambler small = helper.spawn(ModMobs.MYCELITE_SHAMBLER.get(), around(3.0, 180));
        medium.removeFreeWill();
        small.removeFreeWill();
        helper.runAtTickTime(1, () -> {
            medium.setSize(MyceliteShambler.MEDIUM, true);
            small.setSize(MyceliteShambler.SMALL, true);
        });
        helper.runAtTickTime(3, () -> {
            float[][] want = {{1.0F, 24.0F, 1.0F, 1.8F}, {0.7F, 12.0F, 0.7F, 1.26F}, {0.45F, 6.0F, 0.45F, 0.81F}};
            MyceliteShambler[] mobs = {large, medium, small};
            for (int i = 0; i < 3; i++) {
                MyceliteShambler m = mobs[i];
                float[] w = want[i];
                helper.assertTrue(m.size() == i, "size " + i + ", got " + m.size());
                helper.assertTrue(Math.abs(m.getScale() - w[0]) < 0.01F, "scale " + w[0] + ", got " + m.getScale());
                helper.assertTrue(Math.abs(m.getMaxHealth() - w[1]) < 0.01F && Math.abs(m.getHealth() - w[1]) < 0.01F,
                        "health " + w[1] + ", got " + m.getHealth() + "/" + m.getMaxHealth());
                helper.assertTrue(Math.abs(m.getBbWidth() - w[2]) < 0.02F && Math.abs(m.getBbHeight() - w[3]) < 0.02F,
                        "hitbox " + w[2] + " x " + w[3] + ", got " + m.getBbWidth() + " x " + m.getBbHeight());
            }
            helper.succeed();
        });
    }

    /** Health 24, attack 3, armour 0, a 1.0 x 1.8 hitbox at full size. */
    private static void stats(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(s.getMaxHealth() - 24.0F) < 0.01F && Math.abs(s.getHealth() - 24.0F) < 0.01F,
                    "health 24, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < 0.01, "attack 3");
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(s.getBbWidth() - 1.0F) < 0.02F && Math.abs(s.getBbHeight() - 1.8F) < 0.02F,
                    "hitbox 1.0 x 1.8, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.succeed();
        });
    }

    /** A swing with no tell: exactly 3 damage (Atk) to a pig it has as its target. */
    private static void attack(GameTestHelper helper) {
        swing(helper, MyceliteShambler.LARGE);
    }

    /** The smallest hits as hard: 3 damage. */
    private static void smallAttack(GameTestHelper helper) {
        swing(helper, MyceliteShambler.SMALL);
    }

    private static void swing(GameTestHelper helper, int size) {
        MyceliteShambler s = helper.spawn(ModMobs.MYCELITE_SHAMBLER.get(), AT);
        Pig pig = pig(helper, 1.6, 0);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        float[] before = {-1.0F};
        float[] lost = {-1.0F};
        helper.runAtTickTime(1, () -> {
            s.setSize(size, true);
            before[0] = pig.getHealth();
        });
        helper.onEachTick(() -> {
            if (before[0] < 0) return;
            if (pig.isAlive()) s.setTarget(pig);                              // players in other arenas never pull it off
            if (lost[0] < 0 && pig.getHealth() < before[0]) lost[0] = before[0] - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0, "it hits the pig");
            helper.assertTrue(Math.abs(lost[0] - 3.0F) < 0.01F, "the hit does 3 (Atk), the pig lost " + lost[0]);
        });
    }

    /**
     * Home: dark forest and caves (dripstone and lush), never the mushroom fields; spawn weight 15 in groups of exactly
     * 1; two looks, base and the Crimson Cap, the Crimson Cap a 15 to 30 % share.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("mycelite_shambler"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.DARK_FOREST)), "the tag holds the dark forest");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.DRIPSTONE_CAVES)), "and the dripstone caves");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.LUSH_CAVES)), "and the lush caves");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.MUSHROOM_FIELDS)), "never the mushroom fields");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/mycelite_shambler")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        var spawns = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap();
        Weighted<MobSpawnSettings.SpawnerData> entry = spawns.stream()
                .filter(w -> w.value().type() == ModMobs.MYCELITE_SHAMBLER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 15, "spawn weight 15, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("mycelite_shambler");
        helper.assertTrue(looks.stream().map(LookDef::id).toList().equals(List.of("base", "crimson_cap")), "looks base and crimson_cap, got " + looks);
        int total = looks.stream().mapToInt(LookDef::weight).sum();
        int crimson = looks.stream().filter(l -> l.id().equals("crimson_cap")).mapToInt(LookDef::weight).sum();
        double share = total == 0 ? 0 : crimson / (double) total;
        helper.assertTrue(share >= 0.15 && share <= 0.30, "the Crimson Cap is 15 to 30 % of them, " + crimson + "/" + total);
        helper.succeed();
    }

    /** A listener that cancels the "burst" step: the shambler dies whole, with no new shamblers and no poison, and drops its loot. */
    private static void burstCancel(GameTestHelper helper) {
        MyceliteShambler s = shambler(helper);
        Pig pig = pig(helper, 1.0, 0);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == s && e.step().equals("burst")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> {
            kill(helper, s);
            helper.assertTrue(!s.willBurst(), "a vetoed burst won't happen");
        });
        helper.runAtTickTime(4, () -> helper.assertTrue(sporeSacs(helper) == 1, "it drops one spore sac, found " + sporeSacs(helper)));
        helper.runAtTickTime(2 + DEATH_TICKS + 6, () -> {
            helper.assertTrue(near(helper, s).isEmpty(), "nothing bursts out, found " + near(helper, s).size());
            helper.assertTrue(!poisoned(pig), "no poison");
            helper.succeed();
        });
    }
}
