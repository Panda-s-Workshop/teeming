package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.Quakehopper;
import com.panda.teeming.mob.nether.Quakehopper.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Quakehopper: the 14-tick crouch holds it still and hurts nothing, then it hops; a landing sends a ring out along the
 * ground at 0.4 blocks a tick to a radius of 7, whose front strikes everything on the ground once for 6 and throws it
 * away from the landing spot, and spares anything in the air. Its target clearing the front in the air spends its
 * springs: 100 ticks without hops at half speed. Up close it kicks for 5. Every number is checked against a plain
 * value.
 *
 * <p>Pigs stand in as targets (no difficulty scaling), with 100 health. "Still" pigs have no AI (they stand where they
 * are put and count as on the ground by the floor under their feet); "loose" pigs keep their physics, so they can jump
 * and be thrown. Every hopper here has its goals cleared and a follow range of 1, so it acts only on what the test
 * gives it, and leaves with the test. Distances to a pig are to the near edge of its 0.9-wide hitbox.
 */
public final class QuakehopperTests {
    private QuakehopperTests() {}

    /** Half a pig's width: its centre stands this far beyond its near edge. */
    private static final double PIG_HALF = 0.45;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("quakehopper_tell", QuakehopperTests::tell);
        out.accept("quakehopper_counter", QuakehopperTests::jumpedRingSpends);
        out.accept("quakehopper_failure", QuakehopperTests::groundedTakesRing);
        out.accept("quakehopper_ring_speed", QuakehopperTests::ringSpeed);
        out.accept("quakehopper_ring_radius", QuakehopperTests::ringRadius);
        out.accept("quakehopper_ring_air", QuakehopperTests::ringSparesAir);
        out.accept("quakehopper_knockback", QuakehopperTests::knockback);
        out.accept("quakehopper_ring_cancel", QuakehopperTests::ringCancel);
        out.accept("quakehopper_hop", QuakehopperTests::hop);
        out.accept("quakehopper_spent_length", QuakehopperTests::spentLength);
        TeemingGameTests.timeLimit("quakehopper_spent_length", 140);
        out.accept("quakehopper_kick", QuakehopperTests::kick);
        out.accept("quakehopper_stats", QuakehopperTests::stats);
        out.accept("quakehopper_home", QuakehopperTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A hopper at {@code at} that acts only on what the test tells it: no goals, no follow range, the base look. */
    private static Quakehopper hopper(GameTestHelper helper, Vec3 at) {
        Quakehopper q = helper.spawn(ModMobs.QUAKEHOPPER.get(), at);
        q.core().setLook("base", true);
        q.removeFreeWill();
        q.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        q.resetCooldown();
        TestPlayers.whenDone(helper, q::discard);
        return q;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at, boolean still) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        if (still) {
            pig.setNoAi(true);
        } else {
            pig.removeFreeWill();
        }
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** Health lost so far by a 100-health pig. */
    private static float lost(Pig pig) {
        return 100.0F - pig.getHealth();
    }

    /** The game time a pig first lost health, kept in {@code at[0]} (-1 until then). */
    private static void watchHit(GameTestHelper helper, Pig pig, long[] at) {
        at[0] = -1;
        helper.onEachTick(() -> {
            if (at[0] < 0 && pig.getHealth() < 100.0F) at[0] = helper.getLevel().getGameTime();
        });
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /** With a pig 8 blocks off it crouches at once, for 14 ticks, standing still and hurting nothing; the hop follows. */
    private static void tell(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(1.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(9.5, 1, 5.5), true);
        q.setTarget(pig);
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] harmed = {false}, hopped = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (q.core().in(Step.HOP)) hopped[0] = true;
            if (!q.core().in(Step.TELL)) return;
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = q.position();
            }
            ticks[0]++;
            harmed[0] |= lost(pig) > 0.0F;
            moved[0] = Math.max(moved[0], q.position().subtract(start[0]).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && !q.core().in(Step.TELL), "the crouch runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(first[0] <= 3, "with its target 8 blocks off it crouches at once, started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 14) <= 1, "the crouch lasts 14 ticks, lasted " + ticks[0]);
            helper.assertTrue(!harmed[0], "the crouch hurts nothing");
            helper.assertTrue(moved[0] < 0.1, "it stays put while it crouches, moved " + moved[0]);
            helper.assertTrue(hopped[0], "the hop follows the crouch, phase " + q.phase());
        });
    }

    /**
     * The counter: a loose pig 3.9 blocks out, the ring's target, jumps just before the front reaches it. The front
     * passes under it in the air: no damage, it counts as dodged, and when the ring is done the hopper's springs are
     * spent: it moves at half speed (0.2 becomes 0.1) and never crouches, even ready and with its target in range.
     */
    private static void jumpedRingSpends(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(1.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(1.5 + 3.9 + PIG_HALF, 1, 5.5), false);
        long[] t0 = {-1};
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(q.quake(helper.getLevel(), pig), "the ring goes out");
            t0[0] = helper.getLevel().getGameTime();
        });
        boolean[] jumped = {false}, spent = {false}, crouched = {false};
        int[] spentTicks = {0};
        helper.onEachTick(() -> {
            if (t0[0] < 0) return;
            long k = helper.getLevel().getGameTime() - t0[0];
            if (!jumped[0] && k >= 6) {            // the front reaches its near edge about 9 ticks after landing
                jumped[0] = true;
                pig.setDeltaMovement(0.0, 0.42, 0.0);
            }
            if (q.core().in(Step.SPENT)) {
                spent[0] = true;
                spentTicks[0]++;
                q.setTarget(pig);                   // ready and in range: only the spent springs keep it down
                q.resetCooldown();
            }
            crouched[0] |= spent[0] && q.core().in(Step.TELL);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(spent[0] && spentTicks[0] >= 30, "its springs give out once the ring is done, " + spentTicks[0] + " ticks spent, phase " + q.phase());
            helper.assertTrue(lost(pig) == 0.0F, "the pig in the air is untouched, lost " + lost(pig));
            helper.assertTrue(q.wave() != null && q.wave().dodged(pig) && !q.wave().struck(pig), "the ring counts the pig as dodged");
            double speed = q.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(speed - 0.1) < 1.0E-6, "spent it moves at half speed, 0.2 becomes 0.1, got " + speed);
            helper.assertTrue(!crouched[0], "spent it never crouches to hop");
        });
    }

    /**
     * Failing the counter: a loose pig 3 blocks out stays on the ground. The front takes 6 off it, once, and throws it
     * outward (it comes to rest at least 1.5 blocks further out than it stood); the hopper isn't spent, and waits 60
     * ticks before it may crouch again.
     */
    private static void groundedTakesRing(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(1.5, 1, 5.5));
        double from = 3.0 + PIG_HALF;
        Pig pig = pig(helper, new Vec3(1.5 + from, 1, 5.5), false);
        Vec3[] centre = {null};
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(q.quake(helper.getLevel(), pig), "the ring goes out");
            centre[0] = q.position();
        });
        boolean[] spent = {false}, done = {false};
        int[] wait = {-1};
        helper.onEachTick(() -> {
            spent[0] |= q.core().in(Step.SPENT);
            if (centre[0] != null && !done[0] && q.core().idle()) {
                done[0] = true;
                wait[0] = q.cooldown();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "the ring runs out, phase " + q.phase());
            helper.assertTrue(Math.abs(lost(pig) - 6.0F) < 0.01F, "the front strikes once for 6, the pig lost " + lost(pig));
            double out = pig.position().subtract(centre[0]).horizontalDistance();
            helper.assertTrue(out > from + 1.5, "it was thrown outward, from " + from + " to " + out);
            helper.assertTrue(pig.onGround() && pig.getDeltaMovement().horizontalDistance() < 0.01, "and has come to rest");
            helper.assertTrue(!spent[0], "a struck target doesn't spend its springs");
            helper.assertTrue(wait[0] == 60 || wait[0] == 59, "it waits 60 ticks before the next crouch, " + wait[0]);
        });
    }

    // ---- the ring's numbers ------------------------------------------------------------------------------------------

    /**
     * Speed: still pigs 2.2 and 6.2 blocks out on two sides are struck 10 ticks apart (4 blocks at 0.4 a tick), the
     * near one 6 ticks after the landing (2.2 / 0.4, rounded up).
     */
    private static void ringSpeed(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(1.5, 1, 1.5));
        Pig near = pig(helper, new Vec3(1.5 + 2.2 + PIG_HALF, 1, 1.5), true);
        Pig far = pig(helper, new Vec3(1.5, 1, 1.5 + 6.2 + PIG_HALF), true);
        long[] t0 = {-1}, hitNear = {-1}, hitFar = {-1};
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(q.quake(helper.getLevel(), null), "the ring goes out");
            t0[0] = helper.getLevel().getGameTime();
        });
        watchHit(helper, near, hitNear);
        watchHit(helper, far, hitFar);
        helper.succeedWhen(() -> {
            helper.assertTrue(hitNear[0] > 0 && hitFar[0] > 0, "both pigs are struck, " + hitNear[0] + " and " + hitFar[0]);
            long gap = hitFar[0] - hitNear[0];
            helper.assertTrue(gap == 10, "4 blocks further out takes 10 ticks at 0.4 a tick, took " + gap);
            long first = hitNear[0] - t0[0];
            helper.assertTrue(first >= 5 && first <= 6, "the front is 2.2 blocks out 6 ticks after the landing, struck after " + first);
            helper.assertTrue(Math.abs(lost(near) - 6.0F) < 0.01F && Math.abs(lost(far) - 6.0F) < 0.01F,
                    "each takes 6, lost " + lost(near) + " and " + lost(far));
        });
    }

    /**
     * Radius: a still pig 6.8 blocks out is struck, one 7.2 out never is; the ring grows for 18 ticks (7 / 0.4) and
     * ends at radius 7.
     */
    private static void ringRadius(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(1.5, 1, 1.5));
        Pig inside = pig(helper, new Vec3(1.5 + 6.8 + PIG_HALF, 1, 1.5), true);
        Pig outside = pig(helper, new Vec3(1.5, 1, 1.5 + 7.2 + PIG_HALF), true);
        helper.runAtTickTime(5, () -> helper.assertTrue(q.quake(helper.getLevel(), null), "the ring goes out"));
        int[] quake = {0};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (q.core().in(Step.QUAKE)) quake[0]++;
            else if (quake[0] > 0) over[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the ring runs out, " + quake[0] + " ticks so far");
            helper.assertTrue(Math.abs(quake[0] - 18) <= 1, "it grows for 18 ticks, grew for " + quake[0]);
            double r = q.wave().radius(helper.getLevel().getGameTime());
            helper.assertTrue(Math.abs(r - 7.0) < 1.0E-9, "it stops at radius 7, " + r);
            helper.assertTrue(Math.abs(lost(inside) - 6.0F) < 0.01F, "6.8 blocks out is inside the ring, lost " + lost(inside));
            helper.assertTrue(lost(outside) == 0.0F, "7.2 blocks out is beyond it, lost " + lost(outside));
        });
    }

    /**
     * Only the ground: two still pigs 3 blocks out, one on the floor, one held 1.2 blocks over it (within the ring's
     * reach of its height, so only being in the air spares it). The front strikes the first and passes the second.
     */
    private static void ringSparesAir(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(1.5, 1, 1.5));
        Pig ground = pig(helper, new Vec3(1.5 + 3.0 + PIG_HALF, 1, 1.5), true);
        Pig air = pig(helper, new Vec3(1.5, 2.2, 1.5 + 3.0 + PIG_HALF), true);
        air.setNoGravity(true);
        helper.runAtTickTime(5, () -> helper.assertTrue(q.quake(helper.getLevel(), null), "the ring goes out"));
        helper.succeedWhen(() -> {
            helper.assertTrue(q.core().idle(), "the ring runs out, phase " + q.phase());
            helper.assertTrue(Math.abs(lost(ground) - 6.0F) < 0.01F, "the pig on the ground takes 6, lost " + lost(ground));
            helper.assertTrue(lost(air) == 0.0F, "the pig in the air is untouched, lost " + lost(air));
            helper.assertTrue(q.wave().dodged(air), "the front passed under it");
        });
    }

    /**
     * Knockback: a still pig 3 blocks east (no AI, so it keeps whatever motion it is given) is left moving east at
     * exactly 0.5 blocks a tick and up at 0.3, straight away from the landing spot, in place of the hit's own shove.
     */
    private static void knockback(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(1.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(1.5 + 3.0 + PIG_HALF, 1, 5.5), true);
        helper.runAtTickTime(5, () -> helper.assertTrue(q.quake(helper.getLevel(), null), "the ring goes out"));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(pig) > 0.0F, "the pig is struck");
            Vec3 m = pig.getDeltaMovement();
            helper.assertTrue(Math.abs(m.x - 0.5) < 1.0E-6, "thrown east at 0.5 a tick, " + m.x);
            helper.assertTrue(Math.abs(m.z) < 1.0E-6, "straight away from the landing, z " + m.z);
            helper.assertTrue(Math.abs(m.y - 0.3) < 1.0E-6, "and up at 0.3, " + m.y);
        });
    }

    /** A listener that cancels the "ring_hit" step spares the pig; the ring still runs its course. */
    private static void ringCancel(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(1.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(1.5 + 3.0 + PIG_HALF, 1, 5.5), true);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == q && e.step().equals("ring_hit")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(5, () -> helper.assertTrue(q.quake(helper.getLevel(), null), "the ring goes out"));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 30 && q.core().idle(), "the ring runs out, phase " + q.phase());
            helper.assertTrue(lost(pig) == 0.0F, "a cancelled strike does no damage, lost " + lost(pig));
            helper.assertTrue(q.wave().struck(pig), "the front still reached it");
        });
    }

    // ---- the hop, the spent springs and the kick --------------------------------------------------------------------

    /**
     * The hop at a still pig 9 blocks east: up 4.4 blocks (0.8 a tick, less 0.08 each tick), down 3 blocks short of the
     * pig (6 blocks east), and the ring goes out from where it came down.
     */
    private static void hop(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(1.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(10.5, 1, 5.5), true);
        q.setTarget(pig);
        double[] peak = {0.0};
        Vec3[] ring = {null};
        helper.onEachTick(() -> {
            if (q.airborne()) peak[0] = Math.max(peak[0], helper.relativeVec(q.position()).y - 1.0);
            if (ring[0] == null && q.core().in(Step.QUAKE) && q.wave() != null) ring[0] = helper.relativeVec(q.wave().centre());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ring[0] != null, "it hopped and landed, phase " + q.phase());
            helper.assertTrue(Math.abs(ring[0].x - 7.5) < 0.05 && Math.abs(ring[0].z - 5.5) < 0.05,
                    "it comes down 3 blocks short of the pig, at x 7.5, ring at " + ring[0]);
            helper.assertTrue(Math.abs(ring[0].y - 1.0) < 0.01, "on the floor, y " + ring[0].y);
            helper.assertTrue(peak[0] > 4.3 && peak[0] < 4.5, "the hop peaks 4.4 blocks up, " + peak[0]);
        });
    }

    /** Spent springs last 100 ticks; then it is back to its own pace, 0.2. */
    private static void spentLength(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(5, () -> helper.assertTrue(q.spend(helper.getLevel()), "its springs give out"));
        int[] spent = {0};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (q.core().in(Step.SPENT)) spent[0]++;
            else if (spent[0] > 0) over[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the spell ends, " + spent[0] + " ticks so far");
            helper.assertTrue(Math.abs(spent[0] - 100) <= 1, "it lasts 100 ticks, lasted " + spent[0]);
            double speed = q.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(speed - 0.2) < 1.0E-6, "back to 0.2 afterwards, " + speed);
        });
    }

    /** Up close it kicks for its attack: a still pig loses 5. */
    private static void kick(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.8, 1, 5.5), true);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(q.doHurtTarget(helper.getLevel(), pig), "the kick lands");
            helper.assertTrue(Math.abs(lost(pig) - 5.0F) < 0.01F, "a kick does 5, lost " + lost(pig));
            helper.succeed();
        });
    }

    /** Health 26, attack 5, armour 2, a 1.0 x 1.0 hitbox; a monster fire doesn't hurt; looks base and Magma Hopper. */
    private static void stats(GameTestHelper helper) {
        Quakehopper q = hopper(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(q.getMaxHealth() - 26.0F) < 0.01F && Math.abs(q.getHealth() - 26.0F) < 0.01F,
                    "health 26, got " + q.getHealth() + "/" + q.getMaxHealth());
            helper.assertTrue(Math.abs(q.getAttributeValue(Attributes.ATTACK_DAMAGE) - 5.0) < 0.01, "attack 5");
            helper.assertTrue(Math.abs(q.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(q.getBbWidth() - 1.0F) < 0.02F && Math.abs(q.getBbHeight() - 1.0F) < 0.02F,
                    "hitbox 1.0 x 1.0, got " + q.getBbWidth() + " x " + q.getBbHeight());
            helper.assertTrue(q.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(q.getType().fireImmune(), "fire doesn't hurt it");
            helper.assertTrue(!q.vulnerable(), "it has no weak point");
            List<String> looks = Looks.of("quakehopper").stream().map(LookDef::id).sorted().toList();
            helper.assertTrue(looks.equals(List.of("base", "magma_hopper")), "looks base and magma_hopper, got " + looks);
            helper.succeed();
        });
    }

    /** Home: the basalt deltas and not the nether wastes; spawn weight 12, in groups of 1 to 2. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("quakehopper"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.BASALT_DELTAS)), "its home holds the basalt deltas");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.NETHER_WASTES)), "its home never holds the nether wastes");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/quakehopper")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.QUAKEHOPPER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 12, "spawn weight 12, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "in groups of 1 to 2, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
