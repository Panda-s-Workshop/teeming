package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.overworld.PuffcapShepherd;
import com.panda.teeming.mob.overworld.PuffcapShepherd.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.SpawnTable;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Puffcap Shepherd: its cap swells for 16 ticks, then it puffs Regeneration I and Speed I for 100 ticks onto every
 * hostile mob within 6 blocks (never players, passive mobs or itself), at most once every 160 ticks. A hit during the
 * swell bursts the cap: no puff, 40 ticks of reeling. It flees survival players within 6 blocks and swats a target
 * that corners it.
 *
 * <p>The shepherd stands in the middle of the arena. Husks (hostile, and they don't burn in daylight) stand in for its
 * flock (a spider where Regeneration matters, since undead mobs ignore it), a still pig for a passive mob; tests that need a real survival player place one and remove it at the end.
 */
public final class PuffcapShepherdTests {
    private PuffcapShepherdTests() {}

    private static final Vec3 AT = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("puffcap_shepherd_tell", PuffcapShepherdTests::tell);
        out.accept("puffcap_shepherd_counter", PuffcapShepherdTests::hitBurstsCap);
        out.accept("puffcap_shepherd_failure", PuffcapShepherdTests::packGetsBuffs);
        out.accept("puffcap_shepherd_radius", PuffcapShepherdTests::radius);
        out.accept("puffcap_shepherd_spares", PuffcapShepherdTests::spares);
        out.accept("puffcap_shepherd_every", PuffcapShepherdTests::every);
        TeemingGameTests.timeLimit("puffcap_shepherd_every", 240);
        out.accept("puffcap_shepherd_flee", PuffcapShepherdTests::flee);
        out.accept("puffcap_shepherd_swat", PuffcapShepherdTests::swat);
        out.accept("puffcap_shepherd_stats", PuffcapShepherdTests::stats);
        out.accept("puffcap_shepherd_home", PuffcapShepherdTests::home);
        out.accept("puffcap_shepherd_spores_cancel", PuffcapShepherdTests::sporesCancel);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** A shepherd in the middle of the arena with every goal cleared; its own phase machine still runs. */
    private static PuffcapShepherd shepherd(GameTestHelper helper) {
        PuffcapShepherd s = helper.spawn(ModMobs.PUFFCAP_SHEPHERD.get(), AT);
        s.removeFreeWill();
        return s;
    }

    /** A point {@code d} blocks from the shepherd along a direction at {@code deg} degrees round it. */
    private static Vec3 around(double d, double deg) {
        double a = Math.toRadians(deg);
        return AT.add(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    private static Husk husk(GameTestHelper helper, double d, double deg) {
        Husk husk = helper.spawn(EntityTypes.HUSK, around(d, deg));
        husk.setNoAi(true);
        return husk;
    }

    private static boolean buffed(LivingEntity e) {
        return e.hasEffect(MobEffects.REGENERATION) || e.hasEffect(MobEffects.SPEED);
    }

    /** Records the shepherd's own tick count each time it posts a mechanic step; removed when the test ends. */
    private static Map<String, List<Integer>> steps(GameTestHelper helper, PuffcapShepherd s) {
        Map<String, List<Integer>> at = new HashMap<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == s) at.computeIfAbsent(e.step(), k -> new ArrayList<>()).add(s.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /** The swell: exactly 16 ticks from its start to the puff, the shepherd holds still, and nobody is buffed meanwhile. */
    private static void tell(GameTestHelper helper) {
        PuffcapShepherd s = shepherd(helper);
        Husk husk = husk(helper, 3.0, 0);
        Map<String, List<Integer>> at = steps(helper, s);
        Vec3[] start = {null};
        double[] moved = {0.0};
        boolean[] early = {false};
        int[] held = {0};
        helper.runAtTickTime(2, () -> {
            start[0] = s.position();
            helper.assertTrue(s.swell(null), "it starts the swell");
            helper.assertTrue(s.core().in(Step.SWELL), "the swell is the tell, phase " + s.phase());
        });
        helper.onEachTick(() -> {
            if (!s.core().in(Step.SWELL)) return;
            held[0]++;
            early[0] |= buffed(husk);
            moved[0] = Math.max(moved[0], s.position().subtract(start[0]).horizontalDistance());
        });
        helper.runAtTickTime(2 + 16 + 4, () -> {
            helper.assertTrue(at.containsKey("swell") && at.containsKey("puff"), "it swelled, then puffed: " + at);
            int swell = at.get("puff").getFirst() - at.get("swell").getFirst();
            helper.assertTrue(swell == 16, "the swell lasts 16 ticks, measured " + swell);
            helper.assertTrue(Math.abs(held[0] - 16) <= 1, "it holds the swell phase 16 ticks, held " + held[0]);
            helper.assertTrue(!early[0], "nobody is buffed during the swell");
            helper.assertTrue(moved[0] < 0.1, "it stands still while it swells, moved " + moved[0]);
            helper.succeed();
        });
    }

    /**
     * The counter: a hit while idle changes nothing, but a hit 12 ticks into the swell bursts the cap. No puff
     * follows and the husk beside it stays unbuffed; it reels for exactly 40 ticks, and its next puff is a full 160
     * ticks away.
     */
    private static void hitBurstsCap(GameTestHelper helper) {
        PuffcapShepherd s = shepherd(helper);
        Husk husk = husk(helper, 3.0, 0);
        Pig pig = helper.spawn(EntityTypes.PIG, around(2.0, 180));
        pig.setNoAi(true);
        Map<String, List<Integer>> at = steps(helper, s);
        int[] reeled = {0};
        int[] wait = {-1};
        helper.onEachTick(() -> {
            if (s.core().in(Step.BURST)) reeled[0]++;
        });
        helper.runAtTickTime(1, () -> {
            s.hurtServer(helper.getLevel(), s.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(s.core().idle(), "a hit while idle bursts nothing, phase " + s.phase());
        });
        helper.runAtTickTime(2, () -> helper.assertTrue(s.swell(null), "it starts the swell"));
        helper.runAtTickTime(2 + 12, () -> {
            helper.assertTrue(s.core().in(Step.SWELL), "still swelling, phase " + s.phase());
            boolean hurt = s.hurtServer(helper.getLevel(), s.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(hurt, "the hit lands");
            helper.assertTrue(s.core().in(Step.BURST), "a hit in the swell bursts the cap, phase " + s.phase());
            wait[0] = s.untilPuff();
        });
        helper.runAtTickTime(2 + 12 + 40 + 4, () -> {
            helper.assertTrue(!at.containsKey("puff"), "no puff after a burst: " + at);
            helper.assertTrue(!buffed(husk), "the husk got nothing");
            helper.assertTrue(wait[0] == 160, "the next puff is 160 ticks away, " + wait[0]);
            helper.assertTrue(Math.abs(reeled[0] - 40) <= 1, "it reels 40 ticks, reeled " + reeled[0]);
            helper.assertTrue(s.core().idle(), "then it is idle again, phase " + s.phase());
            helper.succeed();
        });
    }

    /**
     * Failing the counter: the swell runs out and the pack is buffed. A hurt spider 3 blocks away gets Regeneration I
     * and Speed I for 100 ticks: 20 % faster at once, and Regeneration I's rate of healing: 1 at once, 1 more 50 ticks
     * later (Regeneration II would have healed 4 by then). A husk 5 blocks away gets Speed I too; like every undead
     * mob it ignores Regeneration (vanilla's {@code ignores_poison_and_regen} tag).
     */
    private static void packGetsBuffs(GameTestHelper helper) {
        PuffcapShepherd s = shepherd(helper);
        Spider spider = helper.spawn(EntityTypes.SPIDER, around(3.0, 90));
        spider.setNoAi(true);
        Husk husk = husk(helper, 5.0, 210);
        Map<String, List<Integer>> at = steps(helper, s);
        float[] hurtTo = {0};
        helper.runAtTickTime(1, () -> {
            spider.setHealth(10.0F);
            hurtTo[0] = spider.getHealth();
            helper.assertTrue(s.swell(null), "it starts the swell");
        });
        helper.runAtTickTime(1 + 16 + 2, () -> {
            helper.assertTrue(at.containsKey("puff"), "it puffed: " + at);
            MobEffectInstance regen = spider.getEffect(MobEffects.REGENERATION);
            helper.assertTrue(regen != null, "the spider gets Regeneration");
            helper.assertTrue(regen.getAmplifier() == 0, "Regeneration I, amplifier " + regen.getAmplifier());
            helper.assertTrue(regen.getDuration() > 95 && regen.getDuration() <= 100, "Regeneration lasts 100 ticks, " + regen.getDuration() + " left");
            for (LivingEntity mob : List.<LivingEntity>of(spider, husk)) {
                MobEffectInstance speed = mob.getEffect(MobEffects.SPEED);
                helper.assertTrue(speed != null, "spider and husk both get Speed");
                helper.assertTrue(speed.getAmplifier() == 0, "Speed I, amplifier " + speed.getAmplifier());
                helper.assertTrue(speed.getDuration() > 95 && speed.getDuration() <= 100, "Speed lasts 100 ticks, " + speed.getDuration() + " left");
                var move = mob.getAttribute(Attributes.MOVEMENT_SPEED);
                double ratio = move.getValue() / move.getBaseValue();
                helper.assertTrue(Math.abs(ratio - 1.2) < 0.001, "Speed I makes it 20 % faster, x" + ratio);
            }
            helper.assertTrue(!husk.hasEffect(MobEffects.REGENERATION), "the undead husk ignores Regeneration");
        });
        helper.runAtTickTime(95, () -> {
            float healed = spider.getHealth() - hurtTo[0];
            helper.assertTrue(Math.abs(healed - 2.0F) < 0.01F, "Regeneration I heals 2 in the 78 ticks since the puff, healed " + healed);
            helper.succeed();
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The reach is 6 blocks: a husk 5.5 blocks away is buffed, one 6.5 blocks away is not. */
    private static void radius(GameTestHelper helper) {
        PuffcapShepherd s = shepherd(helper);
        Husk inside = husk(helper, 5.5, 225);
        Husk outside = husk(helper, 6.5, 45);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(inside.distanceTo(s) - 5.5F) < 0.05F && Math.abs(outside.distanceTo(s) - 6.5F) < 0.05F,
                    "husks at 5.5 and 6.5, got " + inside.distanceTo(s) + " and " + outside.distanceTo(s));
            int buffed = s.puff(helper.getLevel());
            helper.assertTrue(buffed == 1, "one husk inside 6 blocks is buffed, buffed " + buffed);
            helper.assertTrue(buffed(inside), "5.5 blocks away is inside the reach");
            helper.assertTrue(!buffed(outside), "6.5 blocks away is outside the reach");
            helper.succeed();
        });
    }

    /** Only hostile mobs: a pig 2 blocks away, a survival player 3 blocks away and the shepherd itself get nothing. */
    private static void spares(GameTestHelper helper) {
        PuffcapShepherd s = shepherd(helper);
        Pig pig = helper.spawn(EntityTypes.PIG, around(2.0, 0));
        pig.setNoAi(true);
        ServerPlayer player = TestPlayers.survivor(helper, around(3.0, 120));
        Husk husk = husk(helper, 4.0, 240);
        helper.runAtTickTime(2, () -> {
            int buffed = s.puff(helper.getLevel());
            helper.assertTrue(buffed == 1, "only the husk is buffed, buffed " + buffed);
            helper.assertTrue(buffed(husk), "the husk is buffed");
            helper.assertTrue(!buffed(pig), "the pig is not");
            helper.assertTrue(!buffed(player), "the player is not");
            helper.assertTrue(!buffed(s), "the shepherd is not");
            helper.succeed();
        });
    }

    /**
     * Every 160 ticks: with a husk to tend and a target, it starts swelling when 16 ticks remain until it may puff, so
     * the puff lands exactly when the wait runs out; then it waits 160 ticks, and the second puff comes 160 ticks after
     * the first.
     */
    private static void every(GameTestHelper helper) {
        PuffcapShepherd s = shepherd(helper);
        husk(helper, 3.0, 0);
        Pig pig = helper.spawn(EntityTypes.PIG, around(5.0, 180));
        pig.setNoAi(true);
        Map<String, List<Integer>> at = steps(helper, s);
        int[] preset = {-1};
        int[] after = {0};
        helper.runAtTickTime(1, () -> {
            s.setTarget(pig);
            s.setUntilPuff(40);
            preset[0] = s.tickCount;
        });
        helper.onEachTick(() -> {
            if (at.containsKey("puff") && at.get("puff").size() == 1) after[0] = Math.max(after[0], s.untilPuff());
        });
        helper.succeedWhen(() -> {
            List<Integer> puffs = at.getOrDefault("puff", List.of()), swells = at.getOrDefault("swell", List.of());
            helper.assertTrue(puffs.size() >= 2, "it puffs twice on its own, puffs at " + puffs + ", swells at " + swells);
            helper.assertTrue(swells.size() == 2, "and swells before each, swells at " + swells);
            int lead = swells.get(0) - preset[0];
            int first = puffs.get(0) - preset[0];
            int between = puffs.get(1) - puffs.get(0);
            int secondLead = swells.get(1) - puffs.get(0);
            helper.assertTrue(lead == 24, "with 40 ticks to wait it starts swelling 24 ticks later, measured " + lead);
            helper.assertTrue(first == 40, "and puffs when the wait runs out, measured " + first);
            helper.assertTrue(after[0] == 160, "after a puff it waits 160 ticks, saw " + after[0]);
            helper.assertTrue(secondLead == 144, "it swells again 144 ticks after the puff, measured " + secondLead);
            helper.assertTrue(between == 160, "puffs come 160 ticks apart, measured " + between);
        });
    }

    /**
     * It flees survival players within 6 blocks: a player 7 blocks away leaves it where it is for 20 ticks; stepping to
     * 5 blocks sends it running, at least a block further off within 40 ticks.
     */
    private static void flee(GameTestHelper helper) {
        PuffcapShepherd s = helper.spawn(ModMobs.PUFFCAP_SHEPHERD.get(), AT);
        s.stayPut();
        ServerPlayer p = TestPlayers.survivor(helper, around(7.0, 45));
        Vec3[] start = {null};
        double[] moved = {0.0};
        int[] stepped = {-1};
        double[] fled = {0.0};
        helper.runAtTickTime(1, () -> start[0] = s.position());
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (start[0] == null) return;
            if (stepped[0] < 0) {
                moved[0] = Math.max(moved[0], s.position().subtract(start[0]).horizontalDistance());
                if (now >= 21) {
                    p.snapTo(helper.absoluteVec(around(5.0, 45)));
                    stepped[0] = (int) now;
                }
            } else {
                fled[0] = Math.max(fled[0], s.position().subtract(p.position()).horizontalDistance() - 5.0);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepped[0] >= 0, "the player is still 7 blocks away");
            helper.assertTrue(moved[0] < 0.3, "7 blocks away doesn't make it flee, it moved " + moved[0]);
            helper.assertTrue(fled[0] >= 1.0, "5 blocks away it runs: " + fled[0] + " blocks further off so far");
            helper.assertTrue(helper.getTick() - stepped[0] <= 40, "within 40 ticks of stepping in");
        });
    }

    /** Cornered (its target a block and a half away) it swats with its crook: exactly 2 damage (Atk) to a pig. */
    private static void swat(GameTestHelper helper) {
        PuffcapShepherd s = helper.spawn(ModMobs.PUFFCAP_SHEPHERD.get(), AT);
        s.stayPut();
        s.setUntilPuff(100000);                                               // no puff in the way
        Pig pig = helper.spawn(EntityTypes.PIG, around(1.5, 0));
        pig.setNoAi(true);
        float before = pig.getHealth();
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (pig.isAlive()) s.setTarget(pig);                              // players in other arenas never pull it off
            if (lost[0] < 0 && pig.getHealth() < before) lost[0] = before - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0, "it swats the pig that cornered it");
            helper.assertTrue(Math.abs(lost[0] - 2.0F) < 0.01F, "the swat does 2 (Atk), the pig lost " + lost[0]);
        });
    }

    /** Health 16, attack 2, armour 0, a 0.7 x 1.1 hitbox. */
    private static void stats(GameTestHelper helper) {
        PuffcapShepherd s = shepherd(helper);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(s.getMaxHealth() - 16.0F) < 0.01F && Math.abs(s.getHealth() - 16.0F) < 0.01F,
                    "health 16, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ATTACK_DAMAGE) - 2.0) < 0.01, "attack 2");
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(s.getBbWidth() - 0.7F) < 0.02F && Math.abs(s.getBbHeight() - 1.1F) < 0.02F,
                    "hitbox 0.7 x 1.1, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: the dark forest, roster weight 15 in groups of exactly 1; the Glowcap is 15 % of them. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("puffcap_shepherd"));
        helper.assertTrue(tag.isPresent() && tag.get().stream().anyMatch(b -> b.is(Biomes.DARK_FOREST)), "its biome tag holds the dark forest");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/puffcap_shepherd")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        var spawns = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap();
        Weighted<MobSpawnSettings.SpawnerData> entry = spawns.stream()
                .filter(w -> w.value().type() == ModMobs.PUFFCAP_SHEPHERD.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        // the rest of its tag (a biome mod's biomes; every vanilla biome of its home is tuned) has its rest weight from the
        // spawn table, the share in mobs beside the mobs that live there too, not its roster weight 15
        int rest = SpawnTable.rest("puffcap_shepherd", 15).weight();
        helper.assertTrue(entry.weight() == rest, "rest weight " + rest + ", got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("puffcap_shepherd");
        int total = looks.stream().mapToInt(LookDef::weight).sum();
        int glowcap = looks.stream().filter(l -> l.id().equals("glowcap")).mapToInt(LookDef::weight).sum();
        double share = total == 0 ? 0 : glowcap / (double) total;
        helper.assertTrue(Math.abs(share - 0.15) < 0.01, "the Glowcap is 15 % of them, " + glowcap + "/" + total);
        helper.succeed();
    }

    /** A listener that cancels the "spores" step for one husk leaves that husk unbuffed; the other is still buffed. */
    private static void sporesCancel(GameTestHelper helper) {
        PuffcapShepherd s = shepherd(helper);
        Husk vetoed = husk(helper, 3.0, 0);
        Husk other = husk(helper, 3.0, 180);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == s && e.step().equals("spores") && e.target() == vetoed) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> {
            int buffed = s.puff(helper.getLevel());
            helper.assertTrue(buffed == 1, "one husk buffed, buffed " + buffed);
            helper.assertTrue(!buffed(vetoed), "the vetoed husk gets nothing");
            helper.assertTrue(buffed(other), "the other husk is buffed");
            helper.succeed();
        });
    }
}
