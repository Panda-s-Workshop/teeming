package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Brinkram;
import com.panda.teeming.mob.end.Brinkram.Step;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Brinkram: a 20-tick paw (still, harmless) before the charge; the charge runs 0.5 blocks a tick, hits for 8 and throws
 * the victim 2.5 out along it and 0.4 up (less by knockback resistance, a survival player too); a wall stops it with a
 * head shake and nothing more. At an edge (a pit dug 7 deep, past the 4-block step that still counts as ground) it goes
 * over on a roll of 70 % (counted over 4000 rolls) and skids to a stop otherwise: both ways are run with the roll fixed.
 * A skid leaves it teetering 40 ticks, lit as open, and a blow then tips it over; one that dies before it lands leaves
 * its gravity stone on the floor at the brink. A drop of 4 is ground (no roll), a drop of 5 is an edge. Its ordinary
 * butt winds up for 10 ticks first. Every number is checked against a plain value.
 */
public final class BrinkramTests {
    private BrinkramTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("brinkram_tell", BrinkramTests::tell);
        out.accept("brinkram_counter", BrinkramTests::counter);
        out.accept("brinkram_failure", BrinkramTests::failure);
        out.accept("brinkram_charge_speed", BrinkramTests::chargeSpeed);
        out.accept("brinkram_throw", BrinkramTests::throwNumbers);
        out.accept("brinkram_throw_resisted", BrinkramTests::throwResisted);
        out.accept("brinkram_throws_players", BrinkramTests::throwsPlayers);
        out.accept("brinkram_edge_70", BrinkramTests::edgeSeventy);
        out.accept("brinkram_skid", BrinkramTests::skid);
        TeemingGameTests.timeLimit("brinkram_skid", 140);
        out.accept("brinkram_tip_over", BrinkramTests::tipOver);
        out.accept("brinkram_loot_at_brink", BrinkramTests::lootAtBrink);
        out.accept("brinkram_lands", BrinkramTests::lands);
        out.accept("brinkram_drop_4_is_ground", BrinkramTests::dropFourIsGround);
        out.accept("brinkram_drop_5_is_edge", BrinkramTests::dropFiveIsEdge);
        out.accept("brinkram_wall", BrinkramTests::wall);
        out.accept("brinkram_butt", BrinkramTests::butt);
        out.accept("brinkram_tell_veto", BrinkramTests::tellVeto);
        out.accept("brinkram_stats", BrinkramTests::stats);
        out.accept("brinkram_home", BrinkramTests::home);
    }

    // ---- fixtures --------------------------------------------------------------------------------------------------

    /**
     * A Brinkram with the base look, a follow range of 1 (it never notices survivors in other arenas), ready to charge,
     * removed when the test ends wherever it went.
     */
    private static Brinkram ram(GameTestHelper helper, double x, double z) {
        Brinkram r = helper.spawn(ModMobs.BRINKRAM.get(), new Vec3(x, 1, z));
        r.core().setLook("base", true);
        r.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        r.resetCooldown();
        TestPlayers.whenDone(helper, r::discard);
        return r;
    }

    /** A still pig with 100 health, removed when the test ends wherever it was thrown. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A ram at x 3 and a pig 7.5 blocks east of it on the same line, the ram aimed at it. */
    private static Brinkram lineUp(GameTestHelper helper, Pig[] target) {
        Brinkram r = ram(helper, 3.0, 5.5);
        Pig pig = pig(helper, 10.5, 5.5);
        r.setTarget(pig);
        target[0] = pig;
        return r;
    }

    /**
     * A pit across the charge's line, x 6 to 8 and z 2 to 9, {@code depth} blocks deep from the floor, with a stone
     * bottom under it (so a fall into it always lands in this arena).
     */
    private static void pit(GameTestHelper helper, int depth) {
        for (int x = 6; x <= 8; x++) {
            for (int z = 2; z <= 9; z++) {
                for (int y = 0; y > -depth; y--) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, -depth, z), Blocks.STONE);
            }
        }
    }

    private static double relY(GameTestHelper helper, LivingEntity e) {
        return helper.relativeVec(e.position()).y;
    }

    // ---- the tell and the charge -----------------------------------------------------------------------------------

    /** It paws for exactly 20 ticks, standing still and hurting nothing, before it charges. */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        long[] start = {-1}, charge = {-1};
        helper.onEachTick(() -> {
            if (r.core().in(Step.TELL)) {
                if (start[0] < 0) start[0] = helper.getTick();
                helper.assertTrue(r.getDeltaMovement().horizontalDistance() < 0.05, "it stands still while it paws");
                helper.assertTrue(pig[0].getHealth() >= 100.0F, "the tell hurts nothing");
            } else if (start[0] >= 0 && charge[0] < 0) {
                charge[0] = helper.getTick();
                helper.assertTrue(r.core().in(Step.CHARGE), "the tell ends in the charge, phase " + r.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && charge[0] >= 0, "a tell and then a charge");
            helper.assertTrue(charge[0] - start[0] == 20, "the paw lasts 20 ticks, lasted " + (charge[0] - start[0]));
        });
    }

    /** Failing the counter: a target that stays in line takes the charge, 8 (its attack) for a pig with no armour. */
    private static void failure(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        helper.succeedWhen(() -> {
            float lost = 100.0F - pig[0].getHealth();
            helper.assertTrue(lost > 0.0F, "the charge lands");
            helper.assertTrue(Math.abs(lost - 8.0F) < 0.01F, "the charge hits for 8, the pig lost " + lost);
            helper.assertTrue(r.edgeRolls() == 0, "flat ground has no edge");
        });
    }

    /** The charge covers 0.5 blocks a tick. */
    private static void chargeSpeed(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        List<Double> steps = new ArrayList<>();
        double[] last = {Double.NaN};
        helper.onEachTick(() -> {
            if (r.core().in(Step.CHARGE)) {
                if (!Double.isNaN(last[0])) steps.add(r.getX() - last[0]);
                last[0] = r.getX();
            } else {
                last[0] = Double.NaN;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(pig[0].getHealth() < 100.0F, "the charge reached the pig");
            helper.assertTrue(steps.size() >= 4, "it charged for at least 4 ticks, saw " + steps.size());
            double mean = steps.subList(1, steps.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - 0.5) < 0.02, "the charge covers 0.5 blocks a tick, mean " + mean + " of " + steps);
        });
    }

    /**
     * The throw: the pig's motion on the tick of the hit is 2.5 out along the charge (east) and 0.4 up. A still pig keeps
     * the motion it is given, so it is read when the hit is first seen.
     */
    private static void throwNumbers(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        lineUp(helper, pig);
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && pig[0].getHealth() < 100.0F) thrown[0] = pig[0].getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown[0] != null, "the charge hit");
            Vec3 v = thrown[0];
            helper.assertTrue(Math.abs(v.x - 2.5) < 0.01 && Math.abs(v.z) < 0.01, "thrown 2.5 out along the charge, motion " + v);
            helper.assertTrue(Math.abs(v.y - 0.4) < 0.01, "and 0.4 up, motion " + v);
        });
    }

    /** Knockback resistance 0.6 keeps 0.4 of the throw: 1.0 out and 0.16 up. */
    private static void throwResisted(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        lineUp(helper, pig);
        pig[0].getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && pig[0].getHealth() < 100.0F) thrown[0] = pig[0].getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown[0] != null, "the charge hit");
            Vec3 v = thrown[0];
            helper.assertTrue(Math.abs(v.x - 1.0) < 0.01 && Math.abs(v.y - 0.16) < 0.01, "0.4 of the throw is left: 1.0 out, 0.16 up, motion " + v);
        });
    }

    /** A survival player in the way is thrown the same 2.5 out and 0.4 up (it is sent its own motion). */
    private static void throwsPlayers(GameTestHelper helper) {
        Brinkram r = ram(helper, 3.0, 5.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(9.5, 1, 5.5));
        r.setTarget(player);
        float full = player.getHealth();
        Vec3[] thrown = {null};
        helper.onEachTick(() -> {
            if (thrown[0] == null && player.getHealth() < full) thrown[0] = player.getDeltaMovement();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown[0] != null, "the charge hit the player");
            Vec3 v = thrown[0];
            helper.assertTrue(Math.abs(v.x - 2.5) < 0.01 && Math.abs(v.z) < 0.01 && Math.abs(v.y - 0.4) < 0.01,
                    "the player is thrown 2.5 out and 0.4 up, motion " + v);
        });
    }

    // ---- the edge --------------------------------------------------------------------------------------------------

    /** The roll at an edge comes up "over" 70 % of the time: 4000 rolls land within 3 points of it. */
    private static void edgeSeventy(GameTestHelper helper) {
        Brinkram r = ram(helper, 5.5, 5.5);
        r.setNoAi(true);
        helper.runAtTickTime(1, () -> {
            int over = 0;
            for (int i = 0; i < 4000; i++) if (r.rollOver()) over++;
            double share = over / 4000.0;
            helper.assertTrue(r.edgeRolls() == 4000, "every roll counted");
            helper.assertTrue(share >= 0.67 && share <= 0.73, "it goes over 70 % of the time, saw " + share);
            helper.succeed();
        });
    }

    /**
     * The counter: baited across a pit 7 deep, the charge reaches the brink with no ground ahead and (the roll fixed to
     * "over") runs on over it and falls in. The pig across the pit is never touched; the drop spot is on the floor, a
     * step back from the brink.
     */
    private static void counter(GameTestHelper helper) {
        pit(helper, 7);
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        r.fixEdgeRoll(true);
        boolean[] over = {false};
        Vec3[] brink = {null};
        helper.onEachTick(() -> {
            if (r.core().in(Step.OVER) && !over[0]) {
                over[0] = true;
                brink[0] = r.brink();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "it went over, phase " + r.phase());
            helper.assertTrue(r.edgeRolls() == 1 && r.overs() == 1, "one roll at the brink, one fall, saw " + r.edgeRolls() + " and " + r.overs());
            helper.assertTrue(relY(helper, r) < -1.0, "it fell into the pit, at y " + relY(helper, r));
            helper.assertTrue(pig[0].getHealth() >= 100.0F, "the pig across the pit was never hit");
            Vec3 b = helper.relativeVec(brink[0]);
            helper.assertTrue(b.x > 3.5 && b.x < 5.0 && Math.abs(b.y - 1.0) < 0.01, "its drops would fall on the floor a step back from the brink, at " + b);
        });
    }

    /**
     * The other way of the roll: fixed to "skid", it stops at the brink of the 7-deep pit and teeters exactly 40 ticks,
     * lit as open the whole time, never falling; then it backs off.
     */
    private static void skid(GameTestHelper helper) {
        pit(helper, 7);
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        r.fixEdgeRoll(false);
        long[] start = {-1}, end = {-1};
        helper.onEachTick(() -> {
            helper.assertTrue(relY(helper, r) > 0.99, "it never leaves the floor, at y " + relY(helper, r));
            helper.assertTrue(!r.core().in(Step.OVER), "it never goes over");
            if (r.core().in(Step.TEETER)) {
                if (start[0] < 0) start[0] = helper.getTick();
                helper.assertTrue(r.vulnerable(), "teetering, it is lit as open");
            } else if (start[0] >= 0 && end[0] < 0) {
                end[0] = helper.getTick();
                helper.assertTrue(r.core().in(Step.RECOVER), "it backs off the brink, phase " + r.phase());
            }
            if (!r.core().in(Step.TEETER)) helper.assertTrue(!r.vulnerable(), "only teetering is it lit as open");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(end[0] >= 0, "the teeter ended");
            helper.assertTrue(end[0] - start[0] == 40, "it teetered 40 ticks, " + (end[0] - start[0]));
            helper.assertTrue(r.edgeRolls() == 1 && r.overs() == 0, "one roll, no fall");
            double front = helper.relativeVec(r.position()).x + 0.6;
            helper.assertTrue(front > 5.0 && front < 6.4, "it stopped at the brink, its front at " + front);
            helper.assertTrue(pig[0].getHealth() >= 100.0F, "the pig was never hit");
        });
    }

    /** A blow while it teeters tips it over into the pit; the blow does its plain damage (1, no multiplier). */
    private static void tipOver(GameTestHelper helper) {
        pit(helper, 7);
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        r.fixEdgeRoll(false);
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            if (r.core().in(Step.TEETER) && lost[0] < 0.0F) {
                float before = r.getHealth();
                r.hurtServer(helper.getLevel(), r.damageSources().indirectMagic(pig[0], pig[0]), 1.0F);
                lost[0] = before - r.getHealth();
                helper.assertTrue(r.core().in(Step.OVER), "the blow tips it over, phase " + r.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] >= 0.0F, "it teetered and was struck");
            helper.assertTrue(Math.abs(lost[0] - 1.0F) < 0.01F, "the blow does its plain 1, lost " + lost[0]);
            helper.assertTrue(r.edgeRolls() == 1 && r.overs() == 1, "the skid's roll, then the tip");
            helper.assertTrue(relY(helper, r) < -1.0, "it fell into the pit, at y " + relY(helper, r));
        });
    }

    /**
     * Dying before it lands, it leaves its drops at the brink: killed while falling into the pit, its gravity stone lies
     * on the floor west of the pit, not in it.
     */
    private static void lootAtBrink(GameTestHelper helper) {
        pit(helper, 7);
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        r.fixEdgeRoll(true);
        long[] died = {-1};
        helper.onEachTick(() -> {
            if (died[0] < 0 && r.core().in(Step.OVER) && relY(helper, r) < -1.0) {
                helper.assertTrue(r.brink() != null, "falling from the edge, it knows where the brink was");
                r.hurtServer(helper.getLevel(), r.damageSources().fellOutOfWorld(), 1000.0F);
                died[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(died[0] >= 0 && r.isDeadOrDying(), "it died in the fall");
            helper.assertTrue(helper.getTick() - died[0] >= 10, "the drops have settled");
            List<ItemEntity> stones = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(0, 8, 0),
                    i -> i.isAlive() && i.getItem().is(ModMaterials.GRAVITY_STONE.get()));
            helper.assertTrue(!stones.isEmpty(), "it dropped its gravity stone");
            for (ItemEntity stone : stones) {
                Vec3 at = helper.relativeVec(stone.position());
                helper.assertTrue(at.x < 6.0 && at.x > 2.5 && at.y > 0.9 && at.y < 1.6, "the stone lies on the floor at the brink, at " + at);
            }
        });
    }

    /** One that lands alive at the bottom of a pit keeps its drops where it is: the brink is forgotten and it recovers. */
    private static void lands(GameTestHelper helper) {
        pit(helper, 7);
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        r.fixEdgeRoll(true);
        boolean[] over = {false};
        helper.onEachTick(() -> over[0] |= r.core().in(Step.OVER));
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "it went over");
            helper.assertTrue(relY(helper, r) < -6.0 + 0.01 && relY(helper, r) > -6.0 - 0.01, "it landed on the pit's floor, at y " + relY(helper, r));
            helper.assertTrue(!r.core().in(Step.OVER) && r.brink() == null, "landed, the brink is forgotten, phase " + r.phase());
            helper.assertTrue(r.isAlive(), "a fall of 7 doesn't kill it");
        });
    }

    /** A step down of 4 is still ground: the charge runs down into a pit 4 deep with no edge roll at all. */
    private static void dropFourIsGround(GameTestHelper helper) {
        pit(helper, 4);
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        r.fixEdgeRoll(false);
        double[] lowest = {1.0};
        helper.onEachTick(() -> {
            lowest[0] = Math.min(lowest[0], relY(helper, r));
            helper.assertTrue(r.edgeRolls() == 0 && !r.core().in(Step.TEETER) && !r.core().in(Step.OVER), "no edge at a drop of 4");
        });
        helper.succeedWhen(() -> helper.assertTrue(lowest[0] < -2.5, "it charged down into the pit, lowest y " + lowest[0]));
    }

    /** A drop of 5 is an edge: the charge stops for the roll at the brink of a pit 5 deep (fixed to skid here). */
    private static void dropFiveIsEdge(GameTestHelper helper) {
        pit(helper, 5);
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        r.fixEdgeRoll(false);
        helper.succeedWhen(() -> {
            helper.assertTrue(r.edgeRolls() == 1, "one roll at the brink, saw " + r.edgeRolls());
            helper.assertTrue(r.core().in(Step.TEETER) && relY(helper, r) > 0.99, "it teeters at the brink, phase " + r.phase());
        });
    }

    // ---- the wall, the butt, the veto ------------------------------------------------------------------------------

    /**
     * A wall stops the charge: a 20-tick head shake and then its rest, never lit as open, and a blow on it then does its
     * plain damage (4 magic is 4: no stun bonus). The pig behind the wall is untouched.
     */
    private static void wall(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        // the wall goes up once the tell has started (it charges only at a target it can see)
        helper.runAtTickTime(6, () -> {
            for (int z = 2; z <= 9; z++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        });
        long[] start = {-1}, end = {-1};
        float[] lost = {-1.0F};
        helper.onEachTick(() -> {
            helper.assertTrue(!r.vulnerable(), "a wall never opens it up");
            if (r.core().in(Step.BONK)) {
                if (start[0] < 0) {
                    start[0] = helper.getTick();
                    float before = r.getHealth();
                    r.hurtServer(helper.getLevel(), r.damageSources().magic(), 4.0F);
                    lost[0] = before - r.getHealth();
                }
            } else if (start[0] >= 0 && end[0] < 0) {
                end[0] = helper.getTick();
                helper.assertTrue(r.core().idle() && r.cooldown() > 0, "the shake ends in its rest, phase " + r.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(end[0] >= 0, "a wall stopped the charge and the shake ended");
            helper.assertTrue(end[0] - start[0] == 20, "it shook its head 20 ticks, " + (end[0] - start[0]));
            helper.assertTrue(Math.abs(lost[0] - 4.0F) < 0.01F, "a blow after a wall does its plain 4, lost " + lost[0]);
            helper.assertTrue(pig[0].getHealth() >= 100.0F, "the wall saved the pig");
            helper.assertTrue(r.edgeRolls() == 0, "a wall is no edge");
        });
    }

    /** Close in, it butts instead: a 10-tick wind-up hurting nothing, then the butt lands for 8 (its attack). */
    private static void butt(GameTestHelper helper) {
        Brinkram r = ram(helper, 4.5, 5.5);
        Pig pig = pig(helper, 5.9, 5.5);
        r.setTarget(pig);
        long[] start = {-1}, hit = {-1};
        float[] lost = {0.0F};
        helper.onEachTick(() -> {
            helper.assertTrue(!r.core().in(Step.TELL) && !r.core().in(Step.CHARGE), "too close to charge");
            if (r.core().in(Step.BUTT) && start[0] < 0) start[0] = helper.getTick();
            if (hit[0] < 0 && pig.getHealth() < 100.0F) {
                hit[0] = helper.getTick();
                lost[0] = 100.0F - pig.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(start[0] >= 0 && hit[0] >= 0, "a wind-up, then the butt");
            helper.assertTrue(hit[0] - start[0] == 10, "the butt lands 10 ticks after the wind-up starts, " + (hit[0] - start[0]));
            helper.assertTrue(Math.abs(lost[0] - 8.0F) < 0.01F, "the butt hits for 8, lost " + lost[0]);
        });
    }

    /** A listener that cancels the "tell" step stops the whole charge. */
    private static void tellVeto(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Brinkram r = lineUp(helper, pig);
        r.removeFreeWill();                     // it doesn't walk up and butt the pig instead; the charge still tries
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == r && e.step().equals("tell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(r.core().idle(), "a cancelled tell leaves it idle, phase " + r.phase());
            helper.assertTrue(pig[0].getHealth() >= 100.0F, "no charge hit the pig");
            helper.succeed();
        });
    }

    // ---- the row ---------------------------------------------------------------------------------------------------

    /** Health 40, attack 8, armour 6, a 1.2 x 1.4 hitbox. */
    private static void stats(GameTestHelper helper) {
        Brinkram r = ram(helper, 5.5, 5.5);
        r.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(r.getMaxHealth() == 40.0F && r.getHealth() == 40.0F, "health 40, got " + r.getHealth() + "/" + r.getMaxHealth());
            helper.assertTrue(r.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8.0, "attack 8, got " + r.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(r.getArmorValue() == 6, "armour 6, got " + r.getArmorValue());
            helper.assertTrue(Math.abs(r.getBbWidth() - 1.2F) < 0.01F && Math.abs(r.getBbHeight() - 1.4F) < 0.01F,
                    "a 1.2 x 1.4 hitbox, got " + r.getBbWidth() + " x " + r.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: the End midlands (not the highlands or the main island), weight 5, alone or in pairs, a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("brinkram"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "its home holds the End midlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.THE_END)), "never the highlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/brinkram")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.BRINKRAM.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 5, "spawn weight 5, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "alone or in pairs, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.BRINKRAM.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
