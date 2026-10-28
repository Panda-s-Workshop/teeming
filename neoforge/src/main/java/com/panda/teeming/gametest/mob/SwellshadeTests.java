package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Swellshade;
import com.panda.teeming.mob.end.Swellshade.Step;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Swellshade: asleep it never grows; from the tick it takes a foe it swells exactly every 200 ticks, after a 16-tick
 * breath, one step a time: +0.25 scale, +25 % health and attack (a hurt shade gains the 6 health too), four steps to x2
 * (1.6 x 3.2, health 48, attack 9) and never a fifth. Its blow: an 8-tick wind-up, then its attack to a target still in
 * reach, and its reach grows with it. Killed fresh it is snuffed out and drops 1 void ichor and 10 experience; full size
 * drops 3 and 20. A swell waits for headroom, a vetoed swell doesn't happen, and size and clock survive a reload. Health
 * 24, attack 4.5, armour 0, 0.8 x 1.6; End midlands at weight 4, alone. Every number is checked against a plain value.
 * Nothing it does reaches past its own arena: its foe is set by each test, and its follow range is 1.
 */
public final class SwellshadeTests {
    private SwellshadeTests() {}

    private static final Vec3 AT = new Vec3(6.5, 1, 6.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("swellshade_tell", SwellshadeTests::tell);
        out.accept("swellshade_counter", SwellshadeTests::counter);
        out.accept("swellshade_failure", SwellshadeTests::failure);
        TeemingGameTests.timeLimit("swellshade_failure", 260);
        out.accept("swellshade_growth_to_x2", SwellshadeTests::growth);
        TeemingGameTests.timeLimit("swellshade_growth_to_x2", 880);
        out.accept("swellshade_cap", SwellshadeTests::cap);
        out.accept("swellshade_asleep", SwellshadeTests::asleep);
        TeemingGameTests.timeLimit("swellshade_asleep", 260);
        out.accept("swellshade_no_room", SwellshadeTests::noRoom);
        out.accept("swellshade_swell_cancel", SwellshadeTests::swellCancel);
        out.accept("swellshade_reach_grows", SwellshadeTests::reachGrows);
        out.accept("swellshade_pillar", SwellshadeTests::pillar);
        out.accept("swellshade_dodge", SwellshadeTests::dodge);
        out.accept("swellshade_full_drop", SwellshadeTests::fullDrop);
        out.accept("swellshade_save_load", SwellshadeTests::saveLoad);
        TeemingGameTests.timeLimit("swellshade_save_load", 160);
        out.accept("swellshade_stats", SwellshadeTests::stats);
        out.accept("swellshade_home", SwellshadeTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------------

    /** A shade at {@code at} with the base look, no goals and a follow range of 1 (it notices nothing past its arena). */
    private static Swellshade shade(GameTestHelper helper, Vec3 at) {
        Swellshade s = helper.spawn(ModMobs.SWELLSHADE.get(), at);
        s.core().setLook("base", true);
        s.removeFreeWill();
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, s::discard);
        return s;
    }

    private static Swellshade shade(GameTestHelper helper) {
        return shade(helper, AT);
    }

    /** A still pig with 100 health, removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A foe 5 blocks off: it wakes the shade and stays far out of its reach. */
    private static Pig farFoe(GameTestHelper helper, Swellshade s) {
        Pig pig = pig(helper, 6.5, 1, 1.5);
        s.setTarget(pig);
        return pig;
    }

    private static int ichor(GameTestHelper helper) {
        int n = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                i -> i.isAlive() && i.getItem().is(ModMaterials.VOID_ICHOR.get()))) {
            n += item.getItem().getCount();
        }
        return n;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    /** Size, health and attack for growth step {@code n}, against plain values for each step. */
    private static void assertSize(GameTestHelper helper, Swellshade s, double scale, double width, double height, double health, double attack) {
        helper.assertTrue(near(s.getScale(), scale), "scale " + scale + ", got " + s.getScale());
        helper.assertTrue(near(s.getBbWidth(), width) && near(s.getBbHeight(), height),
                "a " + width + " x " + height + " hitbox, got " + s.getBbWidth() + " x " + s.getBbHeight());
        helper.assertTrue(near(s.getMaxHealth(), health), "max health " + health + ", got " + s.getMaxHealth());
        helper.assertTrue(near(s.getAttributeValue(Attributes.ATTACK_DAMAGE), attack), "attack " + attack + ", got " + s.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    // ---- the blow (the tell) ---------------------------------------------------------------------------------------------

    /**
     * Its blow: an 8-tick wind-up, held still and harmless, then 4.5 damage to the pig in reach (armour 0 takes all
     * 4.5).
     */
    private static void tell(GameTestHelper helper) {
        Swellshade s = shade(helper, new Vec3(6.5, 1, 5.0));
        Pig pig = pig(helper, 6.5, 1, 6.25);                               // a gap of 0.4 between the hitboxes
        s.setTarget(pig);
        Vec3 start = s.position();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (!s.core().in(Step.WINDUP)) return;
            ticks[0]++;
            helper.assertTrue(pig.getHealth() >= 100.0F, "the wind-up hurts nothing");
            helper.assertTrue(Math.hypot(s.getX() - start.x, s.getZ() - start.z) < 0.05, "it holds still while it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(s.strikes() == 1, "the blow landed, strikes " + s.strikes() + ", phase " + s.phase());
            helper.assertTrue(ticks[0] == 8, "the wind-up lasts 8 ticks, saw " + ticks[0]);
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 4.5F) < 0.01F, "the blow takes 4.5, the pig lost " + lost);
        });
    }

    /** Stepping back out of reach during the wind-up: the blow hits nothing. */
    private static void dodge(GameTestHelper helper) {
        Swellshade s = shade(helper, new Vec3(6.5, 1, 5.0));
        Pig pig = pig(helper, 6.5, 1, 6.25);
        s.setTarget(pig);
        boolean[] moved = {false}, struck = {false};
        helper.onEachTick(() -> {
            if (s.core().in(Step.WINDUP) && !moved[0]) {
                pig.snapTo(pig.getX(), pig.getY(), pig.getZ() + 3.0);
                moved[0] = true;
            }
            if (s.core().in(Step.STRIKE)) struck[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0], "it swung");
            helper.assertTrue(s.strikes() == 0 && pig.getHealth() >= 100.0F, "at the air: the pig has " + pig.getHealth());
        });
    }

    /**
     * Its reach grows with it: with 2 blocks between the hitboxes a full-size shade's blow lands for 9, and a fresh
     * one's misses.
     */
    private static void reachGrows(GameTestHelper helper) {
        Swellshade big = shade(helper, new Vec3(8.0, 1, 3.5));
        big.setSteps(4);
        Pig bigPig = pig(helper, 8.0, 1, 3.5 + 2.0 + (1.6 + 0.9) / 2.0);
        Swellshade small = shade(helper, new Vec3(3.5, 1, 3.5));
        Pig smallPig = pig(helper, 3.5, 1, 3.5 + 2.0 + (0.8 + 0.9) / 2.0);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(near(big.gapTo(bigPig), 2.0) && near(small.gapTo(smallPig), 2.0),
                    "gaps of 2, got " + big.gapTo(bigPig) + " and " + small.gapTo(smallPig));
            helper.assertTrue(big.startWindup(bigPig) && small.startWindup(smallPig), "both wind up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(big.core().in(Step.STRIKE) || big.cooldown() > 0, "the big one swung");
            helper.assertTrue(small.core().in(Step.STRIKE) || small.cooldown() > 0, "the small one swung");
            helper.assertTrue(big.strikes() == 1 && Math.abs(100.0F - bigPig.getHealth() - 9.0F) < 0.01F,
                    "the full-size blow lands for 9, the pig has " + bigPig.getHealth());
            helper.assertTrue(small.strikes() == 0 && smallPig.getHealth() >= 100.0F, "the fresh one's falls short");
        });
    }

    /**
     * A target on a pillar 3 high, 0.4 across from it (a gap its blow reaches on the ground), is out of reach: the
     * blow measures the gap up and down too, and hits nothing.
     */
    private static void pillar(GameTestHelper helper) {
        Swellshade s = shade(helper, new Vec3(6.5, 1, 5.0));
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(6, y, 6), Blocks.STONE);
        Pig pig = pig(helper, 6.5, 4, 6.25);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.gapTo(pig) > 1.0, "the pillar puts it out of reach, gap " + s.gapTo(pig));
            helper.assertTrue(s.startWindup(pig), "it winds up anyway");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(s.cooldown() > 0 || s.core().in(Step.STRIKE), "it swung");
            helper.assertTrue(s.strikes() == 0 && pig.getHealth() >= 100.0F, "and hit nothing, the pig has " + pig.getHealth());
        });
    }

    // ---- the counter and the failure ---------------------------------------------------------------------------------

    /**
     * The counter: a survival player kills it within its first 200 ticks awake. It never swelled: it is snuffed out,
     * dropping 1 void ichor and 10 experience.
     */
    private static void counter(GameTestHelper helper) {
        Swellshade s = shade(helper);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(6.5, 1, 2.5));
        s.setTarget(player);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(s.awake() && s.steps() == 0, "awake and still fresh");
            helper.assertTrue(ichor(helper) == 0, "no ichor yet");
            helper.assertTrue(s.experience() == 10, "10 experience fresh, " + s.experience());
            s.hurtServer(helper.getLevel(), s.damageSources().playerAttack(player), 1000.0F);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(s.isDeadOrDying(), "dead");
            helper.assertTrue(s.snuffs() == 1 && s.swells() == 0, "snuffed out fresh");
            int n = ichor(helper);
            helper.assertTrue(n == 1, "1 void ichor, found " + n);
        });
    }

    /**
     * Failing it: 200 ticks after it wakes it swells, after a 16-tick breath: scale 1.25, a 1.0 x 2.0 hitbox, max health
     * 30, attack 5.625, and the hurt shade (20 of 24) gains the 6 health: 26.
     */
    private static void failure(GameTestHelper helper) {
        Swellshade s = shade(helper);
        s.setHealth(20.0F);
        farFoe(helper, s);
        long[] woke = {-1}, swelled = {-1};
        int[] inhale = {0};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (woke[0] < 0 && s.awake()) woke[0] = now;
            if (swelled[0] < 0 && s.steps() == 1) swelled[0] = now;
            if (swelled[0] < 0 && s.core().in(Step.INHALE)) inhale[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(swelled[0] >= 0, "it swelled");
            helper.assertTrue(swelled[0] - woke[0] == 200, "200 ticks after it woke, saw " + (swelled[0] - woke[0]));
            helper.assertTrue(inhale[0] == 16, "a 16-tick breath before it, saw " + inhale[0]);
            assertSize(helper, s, 1.25, 1.0, 2.0, 30.0, 5.625);
            helper.assertTrue(near(s.getHealth(), 26.0), "the 6 health it gains is added: 26, got " + s.getHealth());
            helper.assertTrue(s.swells() == 1, "one swell, " + s.swells());
        });
    }

    /**
     * Four swells, at 200, 400, 600 and 800 ticks after it wakes, each one step bigger, to x2: scale 2, a 1.6 x 3.2
     * hitbox, health 48 (full, since every gain is added), attack 9, 20 experience.
     */
    private static void growth(GameTestHelper helper) {
        Swellshade s = shade(helper);
        farFoe(helper, s);
        long[] woke = {-1};
        long[] at = {-1, -1, -1, -1, -1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (woke[0] < 0 && s.awake()) woke[0] = now;
            int n = s.steps();
            if (n > 0 && at[n] < 0) at[n] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at[4] >= 0, "it reached full size, step " + s.steps());
            for (int n = 1; n <= 4; n++) {
                helper.assertTrue(at[n] - woke[0] == 200L * n, "swell " + n + " at " + 200 * n + " ticks, saw " + (at[n] - woke[0]));
            }
            assertSize(helper, s, 2.0, 1.6, 3.2, 48.0, 9.0);
            helper.assertTrue(near(s.getHealth(), 48.0), "at full health, 48, got " + s.getHealth());
            helper.assertTrue(s.experience() == 20, "20 experience, " + s.experience());
        });
    }

    /** Up to x2 and no further: a full-size shade whose clock runs out stays as it is. */
    private static void cap(GameTestHelper helper) {
        Swellshade s = shade(helper);
        farFoe(helper, s);
        s.setSteps(4);
        s.setGrowClock(Swellshade.GROW_EVERY - 5);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(s.steps() == 4 && s.swells() == 0, "still 4 steps, no swell, " + s.steps());
            assertSize(helper, s, 2.0, 1.6, 3.2, 48.0, 9.0);
            helper.succeed();
        });
    }

    /** Asleep (no foe ever) it never swells: after 230 ticks it is as it spawned. */
    private static void asleep(GameTestHelper helper) {
        Swellshade s = shade(helper);
        helper.runAtTickTime(230, () -> {
            helper.assertTrue(!s.awake() && s.steps() == 0 && s.swells() == 0, "asleep and fresh");
            assertSize(helper, s, 1.0, 0.8, 1.6, 24.0, 4.5);
            helper.succeed();
        });
    }

    /**
     * A swell waits for headroom: a 2.8-tall shade (3 steps) under a roof 3 blocks over its feet can't take its 3.2-tall
     * fourth step; it holds there, then swells the tick the roof is gone.
     */
    private static void noRoom(GameTestHelper helper) {
        for (int x = 4; x <= 8; x++) for (int z = 4; z <= 8; z++) helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
        Swellshade s = shade(helper);
        farFoe(helper, s);
        s.setSteps(3);
        s.setGrowClock(Swellshade.GROW_EVERY - 1);
        long[] cleared = {-1}, swelled = {-1};
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(near(s.getBbHeight(), 2.8), "2.8 tall, " + s.getBbHeight());
            helper.assertTrue(s.steps() == 3 && s.swells() == 0 && s.waits() > 0, "it waits for room, waits " + s.waits());
            helper.assertTrue(s.growClock() == Swellshade.GROW_EVERY, "its clock held at the mark, " + s.growClock());
            for (int x = 4; x <= 8; x++) for (int z = 4; z <= 8; z++) helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
            cleared[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (cleared[0] >= 0 && swelled[0] < 0 && s.steps() == 4) swelled[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(swelled[0] >= 0, "it swelled once there was room");
            helper.assertTrue(swelled[0] - cleared[0] <= 1, "at once, " + (swelled[0] - cleared[0]) + " ticks after");
            assertSize(helper, s, 2.0, 1.6, 3.2, 48.0, 9.0);
        });
    }

    /** A vetoed swell doesn't happen: still fresh, its clock started over. */
    private static void swellCancel(GameTestHelper helper) {
        Swellshade s = shade(helper);
        farFoe(helper, s);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == s && e.step().equals("swell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        s.setGrowClock(Swellshade.GROW_EVERY - 5);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(s.steps() == 0 && s.swells() == 0, "no swell");
            helper.assertTrue(s.growClock() > 0 && s.growClock() < 20, "its clock started over, " + s.growClock());
            assertSize(helper, s, 1.0, 0.8, 1.6, 24.0, 4.5);
            helper.succeed();
        });
    }

    /** Killed full size by a survival player it bursts, not snuffed out, and drops 3 void ichor (1 + 2) and 20 experience. */
    private static void fullDrop(GameTestHelper helper) {
        Swellshade s = shade(helper);
        s.setSteps(4);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(6.5, 1, 1.5));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(s.experience() == 20, "20 experience at full size, " + s.experience());
            helper.assertTrue(ichor(helper) == 0, "no ichor yet");
            s.hurtServer(helper.getLevel(), s.damageSources().playerAttack(player), 1000.0F);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(s.isDeadOrDying() && s.snuffs() == 0, "dead, and not snuffed out");
            int n = ichor(helper);
            helper.assertTrue(n == 3, "3 void ichor, found " + n);
        });
    }

    /**
     * Saved and loaded at 2 steps, hurt to 30 of 36, 150 ticks into its clock: the copy keeps its size (scale 1.5, health
     * 30 of 36, attack 6.75), stays awake without a foe, and swells on time.
     */
    private static void saveLoad(GameTestHelper helper) {
        Swellshade s = shade(helper);
        s.setSteps(2);
        s.setHealth(30.0F);
        s.setGrowClock(150);
        Swellshade[] copy = {null};
        long[] savedAt = {-1}, swelledAt = {-1};
        int[] clockAtLoad = {0};
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            s.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            Swellshade c = ModMobs.SWELLSHADE.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            s.discard();
            level.addFreshEntity(c);
            c.removeFreeWill();
            TestPlayers.whenDone(helper, c::discard);
            assertSize(helper, c, 1.5, 1.2, 2.4, 36.0, 6.75);
            helper.assertTrue(near(c.getHealth(), 30.0), "its health kept, 30, got " + c.getHealth());
            helper.assertTrue(c.awake() && c.steps() == 2 && c.getTarget() == null, "awake at 2 steps, with no foe");
            copy[0] = c;
            clockAtLoad[0] = c.growClock();
            savedAt[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (copy[0] != null && swelledAt[0] < 0 && copy[0].steps() == 3) swelledAt[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(swelledAt[0] >= 0, "the copy swelled");
            long expect = 200 - clockAtLoad[0];
            long took = swelledAt[0] - savedAt[0];
            helper.assertTrue(clockAtLoad[0] >= 150 && clockAtLoad[0] <= 153, "its clock kept, " + clockAtLoad[0]);
            helper.assertTrue(Math.abs(took - expect) <= 1, "on time: " + expect + " ticks after the load, took " + took);
            assertSize(helper, copy[0], 1.75, 1.4, 2.8, 42.0, 7.875);
        });
    }

    // ---- stats and home ---------------------------------------------------------------------------------------------

    /** Health 24, attack 4.5, armour 0, a 0.8 x 1.6 hitbox, fresh. */
    private static void stats(GameTestHelper helper) {
        Swellshade s = shade(helper);
        s.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.getMaxHealth() == 24.0F && s.getHealth() == 24.0F, "health 24, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(s.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4.5, "attack 4.5, got " + s.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(s.getArmorValue() == 0, "armour 0, got " + s.getArmorValue());
            helper.assertTrue(Math.abs(s.getBbWidth() - 0.8F) < 0.01F && Math.abs(s.getBbHeight() - 1.6F) < 0.01F,
                    "a 0.8 x 1.6 hitbox, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.assertTrue(s.steps() == 0 && near(s.getScale(), 1.0), "fresh, scale 1");
            helper.succeed();
        });
    }

    /** Home: the End midlands, never the highlands or the main island; weight 4, alone; a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("swellshade"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "its home holds the End midlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.THE_END)), "never the highlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/swellshade")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SWELLSHADE.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.SWELLSHADE.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
