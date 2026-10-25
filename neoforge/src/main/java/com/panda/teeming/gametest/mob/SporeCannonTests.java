package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.SporeCannon;
import com.panda.teeming.mob.nether.SporeCannon.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * Spore Cannon: a 20-tick tell with the bulb lit that fires nothing yet; volleys of 3 spores every 80 ticks, 4 ticks
 * apart, that home in at 0.3 blocks a tick for 4 each and have 1 health, so a swing or an arrow pops one; a hit on the
 * lit bulb lands double and stuns it for 80 ticks, taking double, and loses the volley; nothing moves it. Every number
 * is checked against a plain value.
 *
 * <p>Every arena here is walled at its edge, three blocks high, and roofed by the arena's barriers, so no spore leaves
 * it. Every cannon has its goals cleared and a follow range of 1, so it never notices a survival player in a
 * neighbouring arena; it is given its target, or told to fire directly. Pigs stand still and are given 60 health, so a
 * whole volley never kills one.
 */
public final class SporeCannonTests {
    private SporeCannonTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("spore_cannon_tell", SporeCannonTests::tell);
        out.accept("spore_cannon_counter", SporeCannonTests::bulbStuns);
        TeemingGameTests.timeLimit("spore_cannon_counter", 140);
        out.accept("spore_cannon_failure", SporeCannonTests::volleyHits);
        out.accept("spore_cannon_volley", SporeCannonTests::volleyShape);
        out.accept("spore_cannon_interval", SporeCannonTests::interval);
        TeemingGameTests.timeLimit("spore_cannon_interval", 140);
        out.accept("spore_cannon_speed", SporeCannonTests::speed);
        out.accept("spore_cannon_homing", SporeCannonTests::homing);
        out.accept("spore_cannon_spore_health", SporeCannonTests::sporeHealth);
        out.accept("spore_cannon_swat", SporeCannonTests::swat);
        out.accept("spore_cannon_shoot_down", SporeCannonTests::shootDown);
        out.accept("spore_cannon_bulb_only", SporeCannonTests::bulbOnly);
        out.accept("spore_cannon_bulb_arrow", SporeCannonTests::bulbArrow);
        out.accept("spore_cannon_stun_in_volley", SporeCannonTests::stunInVolley);
        out.accept("spore_cannon_rooted", SporeCannonTests::rooted);
        out.accept("spore_cannon_stats", SporeCannonTests::stats);
        out.accept("spore_cannon_home", SporeCannonTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Stone round the arena's edge cells, three high. */
    private static void walls(GameTestHelper helper) {
        for (int y = 1; y <= 3; y++) {
            for (int i = 0; i < 12; i++) {
                helper.setBlock(new BlockPos(i, y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(i, y, 11), Blocks.STONE);
                helper.setBlock(new BlockPos(0, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(11, y, i), Blocks.STONE);
            }
        }
    }

    /** A still pig with 60 health. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0);
        pig.setHealth(60.0F);
        return pig;
    }

    /** A cannon with no goals and a follow range of 1, aiming at {@code target} (or no one); its spores go with it. */
    private static SporeCannon cannon(GameTestHelper helper, double x, double z, @Nullable LivingEntity target) {
        SporeCannon c = helper.spawn(ModMobs.SPORE_CANNON.get(), new Vec3(x, 1, z));
        c.removeFreeWill();
        c.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        c.setTarget(target);
        TestPlayers.whenDone(helper, c::discard);
        return c;
    }

    /** A cannon with no AI at all (it neither turns nor starts a tell by itself); its spores still fly. */
    private static SporeCannon stillCannon(GameTestHelper helper, double x, double z) {
        SporeCannon c = cannon(helper, x, z, null);
        c.setNoAi(true);
        return c;
    }

    /** Puts {@code p}'s eyes at {@code eye} (absolute) looking straight at {@code point} (absolute). */
    private static void aim(ServerPlayer p, Vec3 eye, Vec3 point) {
        Vec3 d = point.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-Mth.atan2(d.y, Math.hypot(d.x, d.z)) * Mth.RAD_TO_DEG);
        p.snapTo(eye.x, eye.y - p.getEyeHeight(), eye.z, yaw, pitch);
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
    }

    /** A real arrow without gravity from {@code from} (absolute) along {@code dir} at 1.5 blocks a tick. */
    private static Arrow arrow(GameTestHelper helper, Vec3 from, Vec3 dir) {
        Arrow arrow = new Arrow(helper.getLevel(), from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
        arrow.setNoGravity(true);
        arrow.shoot(dir.x, dir.y, dir.z, 1.5F, 0.0F);
        helper.getLevel().addFreshEntity(arrow);
        TestPlayers.whenDone(helper, () -> {
            if (!arrow.isRemoved()) arrow.discard();
        });
        return arrow;
    }

    /**
     * Records each mechanic step of one cannon by the test's own tick. (Not a counter kept by another per-tick
     * callback: the helper runs the callbacks due on one tick in no fixed order, so such a counter may or may not have
     * moved on yet when a second callback reads it.)
     */
    private static final class Log implements Consumer<MechanicEvent> {
        final SporeCannon c;
        final GameTestHelper helper;
        final Map<String, List<Integer>> steps = new HashMap<>();

        Log(GameTestHelper helper, SporeCannon c) {
            this.c = c;
            this.helper = helper;
            MechanicEvent.listen(this);
            TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(this));
        }

        int tick() {
            return (int) helper.getTick();
        }

        @Override
        public void accept(MechanicEvent e) {
            if (e.mob() == c) steps.computeIfAbsent(e.step(), k -> new ArrayList<>()).add(tick());
        }

        List<Integer> of(String step) {
            return steps.getOrDefault(step, List.of());
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The tell lasts exactly 20 ticks with the bulb lit all through it: no spore is launched and nothing is hurt; then
     * the volley follows, and the bulb goes dark once it is fired.
     */
    private static void tell(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 5.5, 8.5);
        SporeCannon c = cannon(helper, 5.5, 3.5, pig);
        Log log = new Log(helper, c);
        int[] ticks = {0};
        boolean[] quiet = {true}, lit = {true}, ended = {false};
        helper.onEachTick(() -> {
            if (c.core().in(Step.TELL)) {
                ticks[0]++;
                lit[0] &= c.bulbLit() && c.vulnerable();
                if (c.launched() > 0 || pig.getHealth() < pig.getMaxHealth()) quiet[0] = false;
            } else if (ticks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the tell runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(log.of("tell").size() == 1 && log.of("tell").getFirst() <= 3, "with its target 5 blocks off it tells at once: " + log.steps);
            helper.assertTrue(ticks[0] == 20, "the tell lasts 20 ticks, lasted " + ticks[0]);
            helper.assertTrue(quiet[0], "nothing is launched and nothing hurt during the tell");
            helper.assertTrue(lit[0], "the bulb is lit all through the tell");
            helper.assertTrue(c.launched() == 3 && c.core().idle(), "the volley follows: launched " + c.launched() + ", phase " + c.phase());
            helper.assertTrue(!c.vulnerable() && !c.bulbLit(), "and the bulb goes dark once the volley is out");
        });
    }

    /**
     * The counter: a 3-damage blow aimed at the lit bulb 10 ticks into the tell takes 6 and stuns it for exactly 80
     * ticks; no spore flies from the broken volley. Stunned, a 3-damage blow anywhere takes 6; after the stun, 3.
     */
    private static void bulbStuns(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 5.5, 9.5);
        SporeCannon c = cannon(helper, 5.5, 5.5, pig);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 2.5));
        Log log = new Log(helper, c);
        int[] stunned = {0};
        boolean[] wasStunned = {false}, afterChecked = {false};
        float[] bulbLoss = {-1.0F}, stunLoss = {-1.0F}, afterLoss = {-1.0F};
        int[] launchedAtStun = {-1};
        helper.onEachTick(() -> {
            if (c.core().in(Step.TELL) && c.core().elapsed() == 10 && log.of("bulb_hit").isEmpty()) {
                aim(p, helper.absoluteVec(new Vec3(5.5, 2.62, 2.5)), c.bulbCentre());
                float before = c.getHealth();
                helper.assertTrue(c.hitsBulb(c.damageSources().indirectMagic(p, p)), "the blow is aimed at the bulb");
                helper.assertTrue(c.hurtServer(helper.getLevel(), c.damageSources().indirectMagic(p, p), 3.0F), "the blow lands");
                bulbLoss[0] = before - c.getHealth();
                launchedAtStun[0] = c.launched();
                helper.assertTrue(c.core().in(Step.STUNNED), "a hit on the lit bulb stuns it, phase " + c.phase());
            }
            if (c.core().in(Step.STUNNED)) {
                stunned[0]++;
                wasStunned[0] = true;
                if (c.core().elapsed() == 40 && stunLoss[0] < 0) {
                    c.damageCooldownTime = 0;                                 // the bulb hit's cooldown would refuse an equal blow
                    float before = c.getHealth();
                    c.hurtServer(helper.getLevel(), c.damageSources().magic(), 3.0F);
                    stunLoss[0] = before - c.getHealth();
                }
            } else if (wasStunned[0] && !afterChecked[0]) {
                afterChecked[0] = true;
                c.damageCooldownTime = 0;
                float before = c.getHealth();
                c.hurtServer(helper.getLevel(), c.damageSources().magic(), 3.0F);
                afterLoss[0] = before - c.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(afterChecked[0], "the stun runs out, stunned " + stunned[0] + " ticks so far");
            helper.assertTrue(Math.abs(bulbLoss[0] - 6.0F) < 0.01F, "the 3-damage blow on the bulb takes 6, took " + bulbLoss[0]);
            helper.assertTrue(stunned[0] == 80, "the stun lasts 80 ticks, lasted " + stunned[0]);
            helper.assertTrue(launchedAtStun[0] == 0 && c.launched() == 0, "no spore flew from the broken volley, launched " + c.launched());
            helper.assertTrue(Math.abs(stunLoss[0] - 6.0F) < 0.01F, "stunned, a 3-damage blow takes 6, took " + stunLoss[0]);
            helper.assertTrue(Math.abs(afterLoss[0] - 3.0F) < 0.01F, "after the stun, a 3-damage blow takes 3, took " + afterLoss[0]);
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "the pig was never hurt");
        });
    }

    /** Failing the counter: the volley reaches the pig, three spores of exactly 4: 12 in all. */
    private static void volleyHits(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 5.5, 8.5);
        SporeCannon c = cannon(helper, 5.5, 3.5, pig);
        Log log = new Log(helper, c);
        float before = pig.getHealth();
        helper.succeedWhen(() -> {
            float lost = before - pig.getHealth();
            helper.assertTrue(log.of("spore_hit").size() == 3 && c.sporesInFlight() == 0, "three spores struck, " + log.steps);
            helper.assertTrue(Math.abs(lost - 12.0F) < 0.01F, "a spore does 4, three of them 12: the pig lost " + lost);
        });
    }

    // ---- the volley --------------------------------------------------------------------------------------------------

    /** A volley is 3 spores, 4 ticks apart, the first on the tick the tell ends; each leaves the vent climbing. */
    private static void volleyShape(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 5.5, 9.5);
        SporeCannon c = stillCannon(helper, 5.5, 3.5);
        Log log = new Log(helper, c);
        List<Vec3> firstSeen = new ArrayList<>();
        int[] lastCount = {0}, atFireStart = {-1};
        helper.runAtTickTime(1, () -> helper.assertTrue(c.tell(pig), "it tells"));
        helper.onEachTick(() -> {
            if (atFireStart[0] < 0 && c.core().in(Step.FIRE)) atFireStart[0] = c.launched();
            if (c.launched() > lastCount[0]) {
                List<Vec3> v = c.sporeVelocities();
                firstSeen.add(v.getLast());
                lastCount[0] = c.launched();
            }
        });
        helper.succeedWhen(() -> {
            List<Integer> spores = log.of("spore");
            helper.assertTrue(spores.size() == 3 && c.launched() == 3, "3 spores to a volley: " + log.steps);
            helper.assertTrue(spores.get(1) - spores.get(0) == 4 && spores.get(2) - spores.get(1) == 4, "4 ticks apart: " + spores);
            helper.assertTrue(atFireStart[0] == 1, "the first leaves on the tick the tell ends, launched then " + atFireStart[0]);
            for (Vec3 v : firstSeen) helper.assertTrue(v.y > 0.2, "each spore leaves climbing, heading " + v);
        });
    }

    /** With its target held in view it fires a volley every 80 ticks: the first spores of two volleys are 80 apart. */
    private static void interval(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 5.5, 8.5);
        SporeCannon c = cannon(helper, 5.5, 3.5, pig);
        Log log = new Log(helper, c);
        helper.succeedWhen(() -> {
            List<Integer> spores = log.of("spore");
            helper.assertTrue(spores.size() >= 4, "two volleys so far: " + spores);
            int gap = spores.get(3) - spores.get(0);
            helper.assertTrue(gap == 80, "one volley every 80 ticks, the gap was " + gap + ": " + spores);
            helper.assertTrue(log.of("tell").size() == 2 && log.of("tell").get(1) - log.of("tell").get(0) == 80, "and one tell each: " + log.of("tell"));
        });
    }

    // ---- spores ----------------------------------------------------------------------------------------------------

    /** Each spore flies exactly 0.3 blocks every tick, whatever way it turns. */
    private static void speed(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 9.5, 9.5);
        SporeCannon c = stillCannon(helper, 2.5, 2.5);
        helper.runAtTickTime(1, () -> helper.assertTrue(c.tell(pig), "it tells"));
        List<Double> steps = new ArrayList<>();
        Vec3[] last = {null};
        int[] lastCount = {0};
        helper.onEachTick(() -> {
            int n = c.sporesInFlight();
            Vec3 now = n > 0 ? c.sporePositions().getFirst() : null;
            if (last[0] != null && now != null && n >= lastCount[0]) steps.add(now.distanceTo(last[0]));
            last[0] = now;
            lastCount[0] = n;
            for (Vec3 v : c.sporeVelocities()) helper.assertTrue(Math.abs(v.length() - 0.3) < 1.0E-6, "a spore's speed is 0.3, got " + v.length());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(c.launched() == 3 && c.sporesInFlight() == 0 && steps.size() >= 20, "followed a spore for 20 ticks, " + steps.size());
            for (double d : steps) helper.assertTrue(Math.abs(d - 0.3) < 1.0E-6, "0.3 blocks a tick, moved " + d);
        });
    }

    /** The spores home: the pig steps 5 blocks aside once all three are in the air, and every one still reaches it. */
    private static void homing(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 3.5, 8.5);
        SporeCannon c = stillCannon(helper, 3.5, 3.5);
        Log log = new Log(helper, c);
        float before = pig.getHealth();
        int[] moved = {-1};
        helper.runAtTickTime(1, () -> helper.assertTrue(c.tell(pig), "it tells"));
        helper.onEachTick(() -> {
            if (moved[0] < 0 && c.launched() == 3) {
                moved[0] = log.tick();
                helper.assertTrue(log.of("spore_hit").isEmpty(), "no spore has arrived yet");
                pig.snapTo(pig.getX() + 5.0, pig.getY(), pig.getZ());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(moved[0] > 0 && c.sporesInFlight() == 0, "the pig stepped aside and the volley is spent");
            helper.assertTrue(log.of("spore_hit").size() == 3, "every spore followed it there: " + log.steps);
            helper.assertTrue(Math.abs(before - pig.getHealth() - 12.0F) < 0.01F, "and struck for 12, the pig lost " + (before - pig.getHealth()));
        });
    }

    /** A spore has 1 health: half a point leaves it flying, the second half pops it, and it never arrives. */
    private static void sporeHealth(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 9.5, 9.5);
        SporeCannon c = stillCannon(helper, 2.5, 2.5);
        Log log = new Log(helper, c);
        float before = pig.getHealth();
        int[] stage = {0};
        helper.runAtTickTime(1, () -> helper.assertTrue(c.tell(pig), "it tells"));
        helper.onEachTick(() -> {
            if (stage[0] == 0 && c.launched() == 1) {
                helper.assertTrue(Math.abs(c.sporeHealth(0) - 1.0F) < 1.0E-6, "a fresh spore has 1 health, " + c.sporeHealth(0));
                helper.assertTrue(!c.hurtSpore(0, 0.5F), "half a point doesn't pop it");
                helper.assertTrue(Math.abs(c.sporeHealth(0) - 0.5F) < 1.0E-6, "it has half left, " + c.sporeHealth(0));
                stage[0] = 1;
            } else if (stage[0] == 1) {
                helper.assertTrue(c.sporesInFlight() == 1, "still flying with half its health");
                helper.assertTrue(c.hurtSpore(0, 0.5F), "the second half pops it");
                stage[0] = 2;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] == 2 && c.launched() == 3 && c.sporesInFlight() == 0, "the volley is spent");
            helper.assertTrue(log.of("spore_pop").size() == 1 && log.of("spore_hit").size() == 2, "one popped, two arrived: " + log.steps);
            helper.assertTrue(Math.abs(before - pig.getHealth() - 8.0F) < 0.01F, "the pig took the other two, lost " + (before - pig.getHealth()));
        });
    }

    /**
     * A survival player's swing pops a spore on its line: a swing aimed away pops nothing, a bare-handed swing aimed at
     * the first spore pops it, and only the other two reach the pig.
     */
    private static void swat(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 9.5, 5.5);
        SporeCannon c = stillCannon(helper, 2.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(3.5, 1, 8.5));
        Log log = new Log(helper, c);
        float before = pig.getHealth();
        int[] first = {-1}, swung = {-1};
        helper.runAtTickTime(1, () -> helper.assertTrue(c.tell(pig), "it tells"));
        helper.onEachTick(() -> {
            if (first[0] < 0 && c.launched() >= 1) first[0] = log.tick();
            if (first[0] < 0) return;
            int age = log.tick() - first[0];
            if (age == 7) {                                                   // a swing aimed straight up, at nothing
                aim(p, helper.absoluteVec(new Vec3(3.5, 2.62, 8.5)), helper.absoluteVec(new Vec3(3.5, 6.0, 8.5)));
                p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            } else if (age == 10) {
                helper.assertTrue(log.of("spore_pop").isEmpty(), "the swing at nothing popped nothing");
                Vec3 at = c.sporePositions().getFirst().add(c.sporeVelocities().getFirst());
                aim(p, at.add(0.0, 0.0, 2.0), at);                           // 2 blocks to its side, looking at it
                p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                swung[0] = log.tick();
            } else if (age == 13) {
                helper.assertTrue(log.of("spore_pop").size() == 1, "the swing popped the spore: " + log.steps);
                p.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 10.0)));      // out of the other spores' way
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(swung[0] > 0 && c.launched() == 3 && c.sporesInFlight() == 0, "the volley is spent");
            helper.assertTrue(log.of("spore_pop").size() == 1 && log.of("spore_hit").size() == 2, "one swatted, two arrived: " + log.steps);
            helper.assertTrue(Math.abs(before - pig.getHealth() - 8.0F) < 0.01F, "the pig took the other two, lost " + (before - pig.getHealth()));
        });
    }

    /** An arrow that crosses a spore's path pops it; only the other two reach the pig. */
    private static void shootDown(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 9.5, 5.5);
        SporeCannon c = stillCannon(helper, 2.5, 5.5);
        Log log = new Log(helper, c);
        float before = pig.getHealth();
        int[] first = {-1};
        Arrow[] shot = {null};
        StringBuilder trace = new StringBuilder();
        helper.runAtTickTime(1, () -> helper.assertTrue(c.tell(pig), "it tells"));
        helper.onEachTick(() -> {
            if (first[0] < 0 && c.launched() >= 1) first[0] = log.tick();
            if (first[0] >= 0 && log.tick() - first[0] == 10) {
                // where the spore will be in two ticks, crossed by an arrow from 3 blocks to its side
                Vec3 at = c.sporePositions().getFirst().add(c.sporeVelocities().getFirst().scale(2.0));
                shot[0] = arrow(helper, at.add(0.0, 0.0, -3.0), new Vec3(0, 0, 1));
                trace.append(" aim ").append(helper.relativeVec(at));
            }
            if (shot[0] != null && log.tick() - first[0] <= 15) {
                Arrow a = shot[0];
                trace.append(String.format(java.util.Locale.ROOT, " | t%d arrow %s old %s age %d removed %s spore %s",
                        log.tick(), helper.relativeVec(a.position()), helper.relativeVec(new Vec3(a.xo, a.yo, a.zo)), a.tickCount, a.isRemoved(),
                        c.sporesInFlight() > 0 ? helper.relativeVec(c.sporePositions().getFirst()) : "-"));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first[0] > 0 && c.launched() == 3 && c.sporesInFlight() == 0, "the volley is spent");
            helper.assertTrue(log.of("spore_pop").size() == 1 && log.of("spore_hit").size() == 2, "one shot down, two arrived: " + log.steps + trace);
            helper.assertTrue(Math.abs(before - pig.getHealth() - 8.0F) < 0.01F, "the pig took the other two, lost " + (before - pig.getHealth()));
        });
    }

    // ---- the bulb ----------------------------------------------------------------------------------------------------

    /** Only the lit bulb stuns: a blow on the stem while it is lit, or on the bulb while it is dark, lands as dealt. */
    private static void bulbOnly(GameTestHelper helper) {
        SporeCannon lit = stillCannon(helper, 3.5, 6.5);
        SporeCannon dark = stillCannon(helper, 8.5, 6.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(3.5, 1, 3.5));
        helper.runAtTickTime(1, () -> helper.assertTrue(lit.tell(null), "it tells"));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(lit.bulbLit() && !dark.bulbLit(), "one lit, one dark");
            aim(p, helper.absoluteVec(new Vec3(3.5, 2.62, 4.0)), lit.position().add(0.0, 0.4, 0.0));
            helper.assertTrue(!lit.hitsBulb(lit.damageSources().indirectMagic(p, p)), "a blow aimed at the stem is not on the bulb");
            float l0 = lit.getHealth();
            lit.hurtServer(helper.getLevel(), lit.damageSources().indirectMagic(p, p), 3.0F);
            aim(p, helper.absoluteVec(new Vec3(8.5, 2.62, 4.0)), dark.bulbCentre());
            helper.assertTrue(dark.hitsBulb(dark.damageSources().indirectMagic(p, p)), "a blow aimed at the dark bulb is on the bulb");
            float d0 = dark.getHealth();
            dark.hurtServer(helper.getLevel(), dark.damageSources().indirectMagic(p, p), 3.0F);
            helper.assertTrue(Math.abs(l0 - lit.getHealth() - 3.0F) < 0.01F, "the stem blow takes 3, took " + (l0 - lit.getHealth()));
            helper.assertTrue(Math.abs(d0 - dark.getHealth() - 3.0F) < 0.01F, "the dark bulb blow takes 3, took " + (d0 - dark.getHealth()));
            helper.assertTrue(lit.core().in(Step.TELL), "the stem blow doesn't stun it, phase " + lit.phase());
            helper.assertTrue(dark.core().idle(), "the dark bulb blow doesn't stun it, phase " + dark.phase());
            helper.succeed();
        });
    }

    /** An arrow into the lit bulb stuns it; an arrow into the lit stem only hurts. */
    private static void bulbArrow(GameTestHelper helper) {
        walls(helper);
        SporeCannon high = stillCannon(helper, 3.5, 6.5);
        SporeCannon low = stillCannon(helper, 8.5, 6.5);
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(high.tell(null) && low.tell(null), "both tell");
            arrow(helper, helper.absoluteVec(new Vec3(3.5, 1 + 1.3, 2.5)), new Vec3(0, 0, 1));
            arrow(helper, helper.absoluteVec(new Vec3(8.5, 1 + 0.4, 2.5)), new Vec3(0, 0, 1));
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(high.getHealth() < high.getMaxHealth() && low.getHealth() < low.getMaxHealth(), "both arrows struck");
            helper.assertTrue(high.core().in(Step.STUNNED), "the arrow into the bulb stunned it, phase " + high.phase());
            helper.assertTrue(!low.core().in(Step.STUNNED) && low.bulbLit(), "the arrow into the stem did not, phase " + low.phase());
            helper.succeed();
        });
    }

    /** A hit on the bulb during the volley: the spore in the air falls apart and the rest are never fired. */
    private static void stunInVolley(GameTestHelper helper) {
        walls(helper);
        Pig pig = pig(helper, 5.5, 9.5);
        SporeCannon c = stillCannon(helper, 5.5, 4.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(2.0, 1, 4.5));
        Log log = new Log(helper, c);
        boolean[] hit = {false};
        helper.runAtTickTime(1, () -> helper.assertTrue(c.tell(pig), "it tells"));
        helper.onEachTick(() -> {
            if (!hit[0] && c.core().in(Step.FIRE) && c.launched() == 1) {
                helper.assertTrue(c.bulbLit() && c.sporesInFlight() == 1, "the bulb is still lit with the first spore out");
                aim(p, helper.absoluteVec(new Vec3(2.0, 2.62, 4.5)), c.bulbCentre());
                helper.assertTrue(c.hurtServer(helper.getLevel(), c.damageSources().indirectMagic(p, p), 2.0F), "the blow lands");
                helper.assertTrue(c.core().in(Step.STUNNED), "stunned, phase " + c.phase());
                helper.assertTrue(c.sporesInFlight() == 0, "the spore in the air fell apart");
                hit[0] = true;
            }
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(hit[0], "the bulb was hit during the volley");
            helper.assertTrue(c.launched() == 1 && log.of("spore").size() == 1, "the rest of the volley was lost: " + log.steps);
            helper.assertTrue(log.of("spore_hit").isEmpty() && pig.getHealth() >= pig.getMaxHealth(), "the pig is unhurt");
            helper.succeed();
        });
    }

    // ---- rooted, stats, home -----------------------------------------------------------------------------------------

    /** Knockback, a shove and a blast next to it leave it exactly where it grew. */
    private static void rooted(GameTestHelper helper) {
        SporeCannon c = cannon(helper, 5.5, 5.5, null);
        Pig attacker = pig(helper, 4.0, 5.5);
        Vec3 start = c.position();
        float[] health = {0.0F};
        helper.runAtTickTime(2, () -> {
            health[0] = c.getHealth();
            c.hurtServer(helper.getLevel(), c.damageSources().mobAttack(attacker), 1.0F);
            c.knockback(3.0, -1.0, 0.0, c.damageSources().mobAttack(attacker), 1.0F);
            c.push(1.0, 0.3, 1.0);
            c.setDeltaMovement(0.8, 0.0, -0.8);
            helper.getLevel().explode(null, c.getX() + 1.5, c.getY() + 0.5, c.getZ(), 2.0F, Level.ExplosionInteraction.NONE);
        });
        helper.runAtTickTime(25, () -> {
            double moved = Math.hypot(c.getX() - start.x, c.getZ() - start.z);
            helper.assertTrue(c.getHealth() < health[0], "the blows landed, health " + c.getHealth());
            helper.assertTrue(moved < 0.01, "it never left its spot, moved " + moved);
            helper.succeed();
        });
    }

    /** Health 30, attack 4, armour 6, a 1.0 x 1.6 hitbox; a monster; looks base and Crimson Cannon. */
    private static void stats(GameTestHelper helper) {
        SporeCannon c = stillCannon(helper, 5.5, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(c.getMaxHealth() - 30.0F) < 0.01F && Math.abs(c.getHealth() - 30.0F) < 0.01F,
                    "health 30, got " + c.getHealth() + "/" + c.getMaxHealth());
            helper.assertTrue(Math.abs(c.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4");
            helper.assertTrue(Math.abs(c.getAttributeValue(Attributes.ARMOR) - 6.0) < 0.01, "armour 6");
            helper.assertTrue(Math.abs(c.getBbWidth() - 1.0F) < 0.02F && Math.abs(c.getBbHeight() - 1.6F) < 0.02F,
                    "hitbox 1.0 x 1.6, got " + c.getBbWidth() + " x " + c.getBbHeight());
            helper.assertTrue(c.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(!c.vulnerable(), "no gold while it is idle");
            List<String> looks = Looks.of("spore_cannon").stream().map(LookDef::id).sorted().toList();
            helper.assertTrue(looks.equals(List.of("base", "crimson_cannon")), "looks base and crimson_cannon, got " + looks);
            helper.succeed();
        });
    }

    /**
     * Home: the warped and crimson forests, never the nether wastes; spawn weight 8, alone. Both forests are share-capped,
     * so its biome-list spawns leave them out and the Teeming spawner places it there.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("spore_cannon"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(bi -> bi.is(Biomes.WARPED_FOREST)), "its home holds the warped forest");
        helper.assertTrue(tag.get().stream().anyMatch(bi -> bi.is(Biomes.CRIMSON_FOREST)), "its home holds the crimson forest");
        helper.assertTrue(tag.get().stream().noneMatch(bi -> bi.is(Biomes.NETHER_WASTES)), "its home never holds the nether wastes");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/spore_cannon")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SPORE_CANNON.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 8, "spawn weight 8, got " + entry.weight());
        var forests = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).biomes();
        var biomeLookup = access.lookupOrThrow(Registries.BIOME);
        helper.assertTrue(!forests.contains(biomeLookup.getOrThrow(Biomes.WARPED_FOREST)) && !forests.contains(biomeLookup.getOrThrow(Biomes.CRIMSON_FOREST)),
                "its biome-list spawns leave out the share-capped forests, where the Teeming spawner places it");
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, groups of " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
