package com.panda.teeming.gametest.mob;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.Stormeel;
import com.panda.teeming.mob.overworld.Stormeel.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Stormeel: in water it glows for 30 ticks, then shocks everything in water within 5 blocks for 6; things out of the
 * water take nothing; at most one discharge every 120 ticks. It swims after a target, and out of water it flops, can't
 * discharge and dries out. Every test builds its own walled pool on the arena floor.
 */
public final class StormeelTests {
    private StormeelTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("stormeel_tell", StormeelTests::tell);
        out.accept("stormeel_counter", StormeelTests::leaveWaterDuringGlow);
        out.accept("stormeel_failure", StormeelTests::stayInWater);
        out.accept("stormeel_dry_land", StormeelTests::dryLandInside);
        out.accept("stormeel_radius", StormeelTests::radius);
        out.accept("stormeel_every", StormeelTests::every);
        out.accept("stormeel_swims", StormeelTests::swims);
        out.accept("stormeel_beached", StormeelTests::beached);
        out.accept("stormeel_spawns_in_water", StormeelTests::spawnsInWater);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Water two deep from x = 1 to {@code lastWet} (z 1..10), stone walls round it, dry floor beyond. */
    private static void pool(GameTestHelper helper, int lastWet) {
        for (int x = 0; x <= 11; x++) {
            for (int z = 0; z <= 11; z++) {
                for (int y = 1; y <= 2; y++) {
                    boolean wall = x == 0 || z == 0 || z == 11 || x == lastWet + 1 || x == 11;
                    if (wall) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    else if (x <= lastWet) helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
    }

    private static Stormeel eel(GameTestHelper helper, double x, double y, double z) {
        Stormeel eel = helper.spawn(ModMobs.STORMEEL.get(), new Vec3(x, y, z));
        eel.setNoAi(true);
        return eel;
    }

    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        return pig;
    }

    private static void tough(Pig pig) {
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
    }

    /** Records the eel's own tick count at each mechanic step it posts ("tell", "pulse"); remove it when done. */
    private static Consumer<MechanicEvent> steps(Stormeel eel, Map<String, Integer> at) {
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == eel) at.putIfAbsent(e.step(), eel.tickCount);
        };
        MechanicEvent.listen(listener);
        return listener;
    }

    // ---- the tell, the counter, failing it -------------------------------------------------------------------------

    /** The glow: 30 ticks from the tell to the discharge, brightening (stage 1, then 2), and nothing hurt meanwhile. */
    private static void tell(GameTestHelper helper) {
        pool(helper, 10);
        Stormeel eel = eel(helper, 3.5, 1.1, 5.5);
        Pig pig = pig(helper, 5.5, 1, 5.5);
        Map<String, Integer> at = new HashMap<>();
        Consumer<MechanicEvent> listener = steps(eel, at);
        boolean[] dim = {false};
        int[] brightest = {0};
        helper.onEachTick(() -> {
            if (!eel.core().in(Step.TELL)) return;
            int stage = eel.core().stage();
            if (eel.core().elapsed() >= 2 && eel.core().elapsed() < 14 && stage == 1) dim[0] = true;
            brightest[0] = Math.max(brightest[0], stage);
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "nothing is hurt during the glow");
        });
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(eel.isInWater() && pig.isInWater(), "both are in the pool");
            helper.assertTrue(eel.core().stage() == 0, "at rest it glows at stage 0, stage " + eel.core().stage());
            helper.assertTrue(eel.charge(pig), "in water it starts the glow");
            helper.assertTrue(eel.core().in(Step.TELL), "the glow is the tell, phase " + eel.phase());
        });
        helper.runAtTickTime(2 + 30 + 4, () -> {
            MechanicEvent.unlisten(listener);
            helper.assertTrue(at.containsKey("tell") && at.containsKey("pulse"), "both steps happened: " + at);
            int glow = at.get("pulse") - at.get("tell");
            helper.assertTrue(glow == 30, "the glow lasts 30 ticks, measured " + glow);
            helper.assertTrue(dim[0] && brightest[0] == 2, "it brightens: stage 1 first, then 2 (saw stage 1: " + dim[0] + ", brightest " + brightest[0] + ")");
            helper.succeed();
        });
    }

    /** The counter: the pig leaves the water halfway through the glow (onto the wall, 4.4 blocks away) and takes nothing. */
    private static void leaveWaterDuringGlow(GameTestHelper helper) {
        pool(helper, 6);
        Stormeel eel = eel(helper, 3.5, 1.1, 5.5);
        Pig pig = pig(helper, 5.5, 1, 5.5);
        helper.runAtTickTime(2, () -> helper.assertTrue(eel.charge(pig) && pig.isInWater(), "the glow starts with the pig in the water"));
        helper.runAtTickTime(2 + 15, () -> {
            Vec3 wallTop = helper.absoluteVec(new Vec3(7.5, 3, 5.5));
            pig.teleportTo(wallTop.x, wallTop.y, wallTop.z);
        });
        helper.runAtTickTime(2 + 30 + 3, () -> {
            helper.assertTrue(eel.core().in(Step.PULSE), "it discharged, phase " + eel.phase());
            helper.assertTrue(!pig.isInWater() && pig.distanceTo(eel) < 5.0F, "the pig is out of the water but inside 5 blocks, " + pig.distanceTo(eel));
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "out of the water it takes nothing, health " + pig.getHealth());
            helper.succeed();
        });
    }

    /** Failing the counter: still in the water 3 blocks away at the discharge costs exactly 6. */
    private static void stayInWater(GameTestHelper helper) {
        pool(helper, 10);
        Stormeel eel = eel(helper, 3.5, 1.1, 5.5);
        Pig pig = pig(helper, 6.5, 1, 5.5);
        helper.runAtTickTime(2, () -> helper.assertTrue(eel.charge(pig), "the glow starts"));
        helper.runAtTickTime(2 + 30 - 2, () -> helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "no damage before the glow ends"));
        helper.runAtTickTime(2 + 30 + 3, () -> {
            float lost = pig.getMaxHealth() - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "the discharge does 6, lost " + lost);
            helper.succeed();
        });
    }

    /** Water only: two pigs on dry land inside 5 blocks (on the wall, on the floor beyond it) take nothing; one in water does. */
    private static void dryLandInside(GameTestHelper helper) {
        pool(helper, 6);
        Stormeel eel = eel(helper, 4.5, 1.1, 5.5);
        Pig wet = pig(helper, 1.5, 1, 5.5);
        Pig onWall = pig(helper, 7.5, 3, 5.5);
        Pig onFloor = pig(helper, 8.5, 1, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(wet.isInWater() && !onWall.isInWater() && !onFloor.isInWater(), "one pig wet, two dry");
            helper.assertTrue(onWall.distanceTo(eel) < 5.0F && onFloor.distanceTo(eel) < 5.0F, "both dry pigs are inside 5 blocks");
            int hit = eel.pulse(helper.getLevel());
            helper.assertTrue(hit == 1, "only the pig in the water is hit, hit " + hit);
            helper.assertTrue(Math.abs(wet.getMaxHealth() - wet.getHealth() - 6.0F) < 0.01F, "the wet pig loses 6");
            helper.assertTrue(onWall.getHealth() >= onWall.getMaxHealth(), "the pig on the wall takes nothing");
            helper.assertTrue(onFloor.getHealth() >= onFloor.getMaxHealth(), "the pig on the floor takes nothing");
            helper.succeed();
        });
    }

    /** The reach is 5 blocks: a pig in the water 4.5 blocks away is hit, one 5.3 blocks away is not. */
    private static void radius(GameTestHelper helper) {
        pool(helper, 10);
        Stormeel eel = eel(helper, 5.5, 1.0, 5.5);
        Pig inside = pig(helper, 1.0, 1, 5.5);
        Pig outside = pig(helper, 5.5 + 3.75, 1, 5.5 + 3.75);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(inside.isInWater() && outside.isInWater(), "both pigs are in the water");
            int hit = eel.pulse(helper.getLevel());
            helper.assertTrue(hit == 1, "one pig within 5 blocks is hit, hit " + hit);
            helper.assertTrue(inside.getHealth() < inside.getMaxHealth(), "4.5 blocks away is inside the reach");
            helper.assertTrue(outside.getHealth() >= outside.getMaxHealth(), "5.3 blocks away is outside the reach");
            helper.succeed();
        });
    }

    /**
     * Every 120 ticks: with a target in reach it starts the glow when 30 ticks remain until it may discharge, so the
     * discharge lands exactly when the wait runs out; after a discharge the wait is 120 ticks and it stays calm.
     */
    private static void every(GameTestHelper helper) {
        pool(helper, 10);
        Stormeel eel = helper.spawn(ModMobs.STORMEEL.get(), new Vec3(3.5, 1.1, 5.5));
        eel.removeFreeWill();
        Pig pig = pig(helper, 6.5, 1, 5.5);
        tough(pig);
        Map<String, Integer> at = new HashMap<>();
        Consumer<MechanicEvent> listener = steps(eel, at);
        int[] preset = {-1};
        int[] after = {0};
        helper.onEachTick(() -> {
            if (at.containsKey("pulse")) after[0] = Math.max(after[0], eel.untilPulse());
        });
        helper.runAtTickTime(1, () -> {
            eel.setTarget(pig);
            eel.setUntilPulse(40);
            preset[0] = eel.tickCount;
        });
        helper.runAtTickTime(95, () -> {
            MechanicEvent.unlisten(listener);
            helper.assertTrue(at.containsKey("tell") && at.containsKey("pulse"), "it charged and discharged on its own: " + at);
            int lead = at.get("tell") - preset[0];
            int toPulse = at.get("pulse") - preset[0];
            helper.assertTrue(lead == 10, "with 40 ticks to wait it starts glowing 10 ticks later, measured " + lead);
            helper.assertTrue(toPulse == 40, "and discharges when the wait runs out, measured " + toPulse);
            helper.assertTrue(after[0] == 120, "after a discharge it waits 120 ticks, saw " + after[0]);
            helper.assertTrue(eel.core().idle(), "no second glow inside the wait, phase " + eel.phase());
            int left = eel.untilPulse();
            int since = eel.tickCount - at.get("pulse");
            helper.assertTrue(left == 120 - since, "the wait counts down a tick at a time: " + left + " left, " + since + " since");
            helper.succeed();
        });
    }

    // ---- living in water -------------------------------------------------------------------------------------------

    /** It swims after its target through the water (and never leaves it). */
    private static void swims(GameTestHelper helper) {
        pool(helper, 10);
        Stormeel eel = helper.spawn(ModMobs.STORMEEL.get(), new Vec3(1.5, 1.1, 5.5));
        Pig pig = pig(helper, 9.5, 1, 5.5);
        tough(pig);
        float[] start = {0};
        helper.runAtTickTime(1, () -> {
            eel.setUntilPulse(100000);                                      // no discharge in the way
            eel.setTarget(pig);
            start[0] = eel.distanceTo(pig);
        });
        helper.onEachTick(() -> helper.assertTrue(helper.getTick() < 2 || eel.isInWater(), "it stays in the water"));
        helper.succeedWhen(() -> {
            float now = eel.distanceTo(pig);
            helper.assertTrue(helper.getTick() > 2 && now < 2.5F && start[0] > 7.0F, "it swam from " + start[0] + " to within 2.5 blocks, now " + now);
        });
    }

    /**
     * Out of water: it flops (hops off the ground), uses up its air (in water its air stays full), and can neither start
     * the glow nor shock a pig in the water 4 blocks away.
     */
    private static void beached(GameTestHelper helper) {
        pool(helper, 4);
        Stormeel dry = helper.spawn(ModMobs.STORMEEL.get(), new Vec3(8.5, 1, 5.5));
        Stormeel wet = eel(helper, 2.5, 1.1, 5.5);
        wet.setNoAi(false);
        wet.removeFreeWill();
        Pig pig = pig(helper, 4.5, 1, 5.5);
        int[] air = {0};
        double[] rose = {0};
        helper.onEachTick(() -> rose[0] = Math.max(rose[0], dry.getY() - helper.absoluteVec(new Vec3(0, 1, 0)).y));
        helper.runAtTickTime(3, () -> {
            air[0] = dry.getAirSupply();
            helper.assertTrue(!dry.isInWater() && dry.core().flag(Stormeel.BEACHED), "on the floor it is beached");
            helper.assertTrue(!dry.charge(pig), "out of water it can't start the glow");
            int hit = dry.pulse(helper.getLevel());
            helper.assertTrue(hit == 0 && pig.getHealth() >= pig.getMaxHealth(), "out of water its charge fizzles, hit " + hit);
        });
        helper.runAtTickTime(43, () -> {
            int used = air[0] - dry.getAirSupply();
            helper.assertTrue(used >= 38 && used <= 40, "out of water it uses a tick of air a tick, used " + used + " in 40");
            helper.assertTrue(wet.getAirSupply() == wet.getMaxAirSupply(), "in water its air is full, " + wet.getAirSupply());
            helper.assertTrue(rose[0] > 0.2, "it flops off the ground, rose " + rose[0]);
            helper.succeed();
        });
    }

    /** Natural spawns in water: the water placement accepts the pool, and the eel's own obstruction and spawn rules pass in water. */
    private static void spawnsInWater(GameTestHelper helper) {
        pool(helper, 10);
        Stormeel eel = eel(helper, 5.5, 1.0, 5.5);
        RosterStats.Entry row = RosterStats.get("stormeel");
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(row.place() == RosterStats.Place.WATER, "it is a water mob, place " + row.place());
            helper.assertTrue(TeemingSpawns.placeOk(row, eel.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))), "the pool is a spawn spot");
            helper.assertTrue(!TeemingSpawns.placeOk(row, eel.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 3, 5))), "the air above it is not");
            helper.assertTrue(eel.checkSpawnObstruction(helper.getLevel()), "water doesn't count as obstruction");
            helper.assertTrue(eel.checkSpawnRules(helper.getLevel(), EntitySpawnReason.NATURAL), "its spawn rules accept water in any light");
            helper.succeed();
        });
    }
}
