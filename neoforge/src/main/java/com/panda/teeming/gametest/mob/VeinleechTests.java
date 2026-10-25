package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.Veinleech;
import com.panda.teeming.mob.nether.Veinleech.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Veinleech: with a target within 10 blocks in its sight it rears for 10 ticks (the tell, which drains and heals
 * nothing), then its vein latches on: the target loses 1 health a second and the leech heals 1 a second. Lost sight, or
 * the target past 10 blocks, snaps the vein (also when the tell ends with the target already out of sight) and the
 * leech is stunned for 40 ticks; it rears again 40 ticks after the stun.
 *
 * <p>The leech sits in one corner of the arena with its goals cleared (it reads its target straight from the test, and
 * a follow range of 1 keeps it from picking up survivors in other arenas) and a still pig stands in as the target where
 * the numbers must be exact. The drain on a player is shown on a real survival player, ticked by hand (nothing else
 * ticks it here, and its hurt cooldown only runs down in its own tick), because the helper's mock player counts as
 * creative.
 */
public final class VeinleechTests {
    private VeinleechTests() {}

    /** The leech, in a corner. */
    private static final Vec3 AT = new Vec3(1.5, 1, 1.5);
    /** The target, in the middle of the arena: 7.07 blocks from the leech. */
    private static final Vec3 MID = new Vec3(6.5, 1, 6.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("veinleech_tell", VeinleechTests::tell);
        out.accept("veinleech_counter", VeinleechTests::wallSnaps);
        TeemingGameTests.timeLimit("veinleech_counter", 140);
        out.accept("veinleech_failure", VeinleechTests::playerDrained);
        out.accept("veinleech_drain", VeinleechTests::drain);
        out.accept("veinleech_heal", VeinleechTests::heal);
        out.accept("veinleech_range", VeinleechTests::range);
        out.accept("veinleech_range_break", VeinleechTests::rangeBreak);
        out.accept("veinleech_stun_time", VeinleechTests::stunTime);
        TeemingGameTests.timeLimit("veinleech_stun_time", 120);
        out.accept("veinleech_miss", VeinleechTests::coverDuringTell);
        out.accept("veinleech_cooldown", VeinleechTests::cooldown);
        TeemingGameTests.timeLimit("veinleech_cooldown", 200);
        out.accept("veinleech_drain_cancel", VeinleechTests::drainCancel);
        out.accept("veinleech_stats", VeinleechTests::stats);
        out.accept("veinleech_home", VeinleechTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    private static Veinleech leech(GameTestHelper helper) {
        Veinleech v = helper.spawn(ModMobs.VEINLEECH.get(), AT);
        v.removeFreeWill();      // no wandering or biting; its own phase machine still runs
        v.resetCooldown();
        v.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        // the base look now, so its first tick doesn't pick one and refill its health over a test's setHealth
        v.core().setLook(LookDef.BASE, true);
        return v;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    /** A point {@code d} blocks from the leech along the arena's diagonal. */
    private static Vec3 diagonal(double d) {
        double k = d / Math.sqrt(2.0);
        return AT.add(k, 0, k);
    }

    /** A leech and a pig in the middle of the arena, the pig its target. */
    private static Veinleech onPig(GameTestHelper helper, Pig[] out) {
        Veinleech v = leech(helper);
        Pig pig = pig(helper, MID);
        v.setTarget(pig);
        out[0] = pig;
        return v;
    }

    /** A stone wall across the arena between the corner and the middle, four high. */
    private static void wall(GameTestHelper helper) {
        for (int z = 0; z < 12; z++) {
            for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A pig 7 blocks away in sight sets off the tell at once. It lasts 10 ticks, the leech holds still, and nothing
     * happens yet: the pig loses no health and the leech (at 10 of 22) heals nothing. The latch follows it.
     */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Veinleech v = onPig(helper, pig);
        v.setHealth(10.0F);
        Vec3 start = v.position();
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] hurt = {false}, healed = {false}, latched = {false};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            latched[0] |= v.core().in(Step.LATCH);
            if (!v.core().in(Step.TELL)) return;
            if (first[0] < 0) first[0] = helper.getTick();
            ticks[0]++;
            hurt[0] |= pig[0].getHealth() < pig[0].getMaxHealth();
            healed[0] |= v.getHealth() > 10.0F;
            moved[0] = Math.max(moved[0], v.position().subtract(start).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && latched[0], "the tell runs and the latch follows, " + ticks[0] + " tell ticks, phase " + v.phase());
            helper.assertTrue(first[0] <= 3, "a target in range and sight sets it off at once, it started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 10) <= 1, "the tell lasts 10 ticks, lasted " + ticks[0]);
            helper.assertTrue(!hurt[0], "the pig loses nothing during the tell");
            helper.assertTrue(!healed[0], "it heals nothing during the tell");
            helper.assertTrue(moved[0] < 0.1, "it holds still while it rears, moved " + moved[0]);
        });
    }

    /**
     * The counter: 10 ticks into the latch a stone wall is raised between them. The vein snaps within 2 ticks, the
     * leech is stunned, and the pig loses nothing more: its health is the same 40 ticks later.
     */
    private static void wallSnaps(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Veinleech v = onPig(helper, pig);
        long[] latched = {-1}, walled = {-1}, snapped = {-1};
        float[] atSnap = {-1.0F};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (latched[0] < 0 && v.core().in(Step.LATCH)) latched[0] = now;
            if (latched[0] >= 0 && walled[0] < 0 && now >= latched[0] + 10) {
                wall(helper);
                walled[0] = now;
            }
            if (snapped[0] < 0 && v.core().in(Step.STUNNED)) {
                snapped[0] = now;
                atSnap[0] = pig[0].getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(walled[0] >= 0, "the vein latched, phase " + v.phase());
            helper.assertTrue(snapped[0] > walled[0], "a wall between them snaps the vein and stuns the leech, phase " + v.phase());
            helper.assertTrue(snapped[0] - walled[0] <= 2, "within 2 ticks, took " + (snapped[0] - walled[0]));
            helper.assertTrue(!v.hasLineOfSight(pig[0]), "the wall blocks its sight");
            helper.assertTrue(helper.getTick() >= snapped[0] + 40, "waiting 40 ticks after the snap");
            helper.assertTrue(Math.abs(pig[0].getHealth() - atSnap[0]) < 0.01F, "the drain stopped: " + atSnap[0] + " -> " + pig[0].getHealth());
            helper.assertTrue(!v.core().in(Step.LATCH), "it is off the vein, phase " + v.phase());
        });
    }

    /**
     * Failing the counter: a survival player stays in sight. 65 ticks after the latch they have lost exactly 3 health
     * to magic (1 a second), and the leech (at 10 of 22) has healed exactly 3.
     */
    private static void playerDrained(GameTestHelper helper) {
        Veinleech v = leech(helper);
        v.setHealth(10.0F);
        ServerPlayer p = TestPlayers.survivor(helper, MID);
        v.setTarget(p);
        float before = p.getHealth();
        long[] latched = {-1};
        float[] lost = {-1.0F}, leech = {-1.0F};
        DamageSource[] last = {null};
        helper.onEachTick(() -> {
            p.doTick();          // the hurt cooldown runs down in the player's own tick
            long now = helper.getTick();
            if (latched[0] < 0 && v.core().in(Step.LATCH)) latched[0] = now;
            if (latched[0] >= 0 && lost[0] < 0 && now >= latched[0] + 65) {
                lost[0] = before - p.getHealth();
                leech[0] = v.getHealth();
                last[0] = p.getLastDamageSource();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(latched[0] >= 0, "the vein latched on the player, phase " + v.phase());
            helper.assertTrue(lost[0] >= 0.0F, "65 ticks of the latch went by, phase " + v.phase());
            helper.assertTrue(Math.abs(lost[0] - 3.0F) < 0.01F, "1 health a second: 3 lost after 65 ticks, lost " + lost[0]);
            helper.assertTrue(last[0] != null && last[0].is(DamageTypes.MAGIC), "the drain is magic, got " + last[0]);
            helper.assertTrue(Math.abs(leech[0] - 13.0F) < 0.01F, "the leech healed 10 -> 13, health " + leech[0]);
            helper.assertTrue(v.core().in(Step.LATCH), "the vein still holds, phase " + v.phase());
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The pig (10 health) loses nothing in the latch's first 18 ticks, exactly 1 by its 25th and exactly 3 by its 65th. */
    private static void drain(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Veinleech v = onPig(helper, pig);
        boolean[] early = {false};
        float[] at25 = {-1.0F}, at65 = {-1.0F};
        helper.onEachTick(() -> {
            if (!v.core().in(Step.LATCH)) return;
            int t = v.core().elapsed();
            if (t <= 18) early[0] |= pig[0].getHealth() < 10.0F;
            if (t >= 25 && at25[0] < 0) at25[0] = pig[0].getHealth();
            if (t >= 65 && at65[0] < 0) at65[0] = pig[0].getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at65[0] >= 0, "the latch held 65 ticks, phase " + v.phase());
            helper.assertTrue(!early[0], "nothing drained before the first second");
            helper.assertTrue(Math.abs(at25[0] - 9.0F) < 0.01F, "1 health after the first second: 10 -> 9, got " + at25[0]);
            helper.assertTrue(Math.abs(at65[0] - 7.0F) < 0.01F, "1 health a second: 10 -> 7 after 65 ticks, got " + at65[0]);
        });
    }

    /** Hurt to 10 of 22, the leech heals nothing in the latch's first 18 ticks and exactly 3 by its 65th: 1 a second. */
    private static void heal(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Veinleech v = onPig(helper, pig);
        v.setHealth(10.0F);
        boolean[] early = {false};
        float[] at65 = {-1.0F};
        helper.onEachTick(() -> {
            if (!v.core().in(Step.LATCH)) return;
            int t = v.core().elapsed();
            if (t <= 18) early[0] |= v.getHealth() > 10.0F;
            if (t >= 65 && at65[0] < 0) at65[0] = v.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at65[0] >= 0, "the latch held 65 ticks, phase " + v.phase());
            helper.assertTrue(!early[0], "no healing before the first second");
            helper.assertTrue(Math.abs(at65[0] - 13.0F) < 0.01F, "1 health a second: 10 -> 13 after 65 ticks, got " + at65[0]);
        });
    }

    /** A pig 10.5 blocks away is out of reach: no tell for 20 ticks. Moved to 9.5 it sets the tell off within 3 ticks. */
    private static void range(GameTestHelper helper) {
        Veinleech v = leech(helper);
        Pig pig = pig(helper, diagonal(10.5));
        v.setTarget(pig);
        long[] reared = {-1};
        double[] far = {0.0}, near = {0.0};
        helper.onEachTick(() -> {
            if (reared[0] < 0 && v.core().in(Step.TELL)) reared[0] = helper.getTick();
        });
        helper.runAtTickTime(20, () -> {
            far[0] = v.distanceTo(pig);
            helper.assertTrue(reared[0] < 0 && v.core().idle(), "10.5 blocks away is out of reach, reared at " + reared[0] + " from " + far[0]);
            pig.snapTo(helper.absoluteVec(diagonal(9.5)));
            near[0] = v.distanceTo(pig);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(far[0] - 10.5) < 0.05 && Math.abs(near[0] - 9.5) < 0.05,
                    "the pig stood 10.5 then 9.5 blocks away, measured " + far[0] + " and " + near[0]);
            helper.assertTrue(reared[0] >= 20, "9.5 blocks away is in reach, reared at " + reared[0]);
            helper.assertTrue(reared[0] <= 23, "within 3 ticks of stepping in, reared at " + reared[0]);
        });
    }

    /** Latched on a pig 6 blocks away, the pig moves to 9.5: it holds 10 ticks. Moved to 10.5 it snaps within 2 ticks. */
    private static void rangeBreak(GameTestHelper helper) {
        Veinleech v = leech(helper);
        Pig pig = pig(helper, diagonal(6.0));
        v.setTarget(pig);
        long[] latched = {-1}, edge = {-1}, out = {-1}, snapped = {-1};
        boolean[] early = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (latched[0] < 0 && v.core().in(Step.LATCH)) latched[0] = now;
            if (snapped[0] < 0 && v.core().in(Step.STUNNED)) {
                snapped[0] = now;
                early[0] |= out[0] < 0;
            }
            if (latched[0] >= 0 && edge[0] < 0 && now >= latched[0] + 5) {
                pig.snapTo(helper.absoluteVec(diagonal(9.5)));
                edge[0] = now;
            }
            if (edge[0] >= 0 && out[0] < 0 && now >= edge[0] + 10) {
                early[0] |= !v.core().in(Step.LATCH);
                pig.snapTo(helper.absoluteVec(diagonal(10.5)));
                out[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(out[0] >= 0, "the vein latched and held, latched at " + latched[0]);
            helper.assertTrue(!early[0], "9.5 blocks away it held");
            helper.assertTrue(snapped[0] >= 0, "10.5 blocks away snaps it and stuns the leech, phase " + v.phase());
            helper.assertTrue(snapped[0] - out[0] <= 2, "within 2 ticks, took " + (snapped[0] - out[0]));
        });
    }

    /**
     * After the wall snaps the vein, the leech stays stunned for 40 ticks (counted tick by tick): it neither moves when
     * the pig is moved back into its sight, nor rears or latches; then it stands up idle.
     */
    private static void stunTime(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Veinleech v = onPig(helper, pig);
        long[] latched = {-1}, walled = {-1}, ended = {-1};
        int[] stunned = {0};
        boolean[] acted = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (latched[0] < 0 && v.core().in(Step.LATCH)) latched[0] = now;
            if (latched[0] >= 0 && walled[0] < 0 && now >= latched[0] + 5) {
                wall(helper);
                walled[0] = now;
            }
            if (v.core().in(Step.STUNNED)) {
                stunned[0]++;
                // back in sight on the same side as the leech: a free leech would rear at once
                if (stunned[0] == 5) pig[0].snapTo(helper.absoluteVec(new Vec3(1.5, 1, 3.5)));
            } else if (stunned[0] > 0) {
                if (ended[0] < 0) ended[0] = now;
                acted[0] |= v.core().in(Step.TELL) || v.core().in(Step.LATCH);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0] >= 0, "the wall stunned it and the stun ended, " + stunned[0] + " stunned ticks, phase " + v.phase());
            helper.assertTrue(Math.abs(stunned[0] - 40) <= 1, "stunned for 40 ticks, was " + stunned[0]);
            helper.assertTrue(!acted[0], "it doesn't rear at once after the stun");
            helper.assertTrue(v.core().idle(), "idle after the stun, phase " + v.phase());
        });
    }

    /**
     * Cover during the tell: a wall goes up 5 ticks into the rear. When the tell ends the vein has nothing to latch on
     * to: it snaps at the wall, the leech is stunned and the pig loses nothing.
     */
    private static void coverDuringTell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Veinleech v = onPig(helper, pig);
        long[] reared = {-1}, walled = {-1}, snapped = {-1};
        boolean[] latched = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (reared[0] < 0 && v.core().in(Step.TELL)) reared[0] = now;
            if (reared[0] >= 0 && walled[0] < 0 && now >= reared[0] + 5) {
                wall(helper);
                walled[0] = now;
            }
            latched[0] |= v.core().in(Step.LATCH);
            if (snapped[0] < 0 && v.core().in(Step.STUNNED)) snapped[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(walled[0] >= 0 && snapped[0] >= 0, "the wall went up in the tell and the leech is stunned, phase " + v.phase());
            helper.assertTrue(Math.abs(snapped[0] - reared[0] - 10) <= 1, "stunned as the tell ends, 10 ticks in, at " + (snapped[0] - reared[0]));
            helper.assertTrue(!latched[0], "the vein never latched");
            helper.assertTrue(helper.getTick() >= snapped[0] + 25, "waiting past the first drink");
            helper.assertTrue(Math.abs(pig[0].getHealth() - pig[0].getMaxHealth()) < 0.01F, "the pig lost nothing, health " + pig[0].getHealth());
        });
    }

    /**
     * After a snap (the pig moved out to 10.5 blocks, then back to 6 blocks 5 ticks later) it rears again 80 to 83
     * ticks after the snap: 40 stunned and 40 waiting.
     */
    private static void cooldown(GameTestHelper helper) {
        Veinleech v = leech(helper);
        Pig pig = pig(helper, diagonal(6.0));
        v.setTarget(pig);
        long[] latched = {-1}, snapped = {-1}, back = {-1}, again = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (latched[0] < 0 && v.core().in(Step.LATCH)) {
                latched[0] = now;
                pig.snapTo(helper.absoluteVec(diagonal(10.5)));
            }
            if (snapped[0] < 0 && v.core().in(Step.STUNNED)) snapped[0] = now;
            if (snapped[0] >= 0 && back[0] < 0 && now >= snapped[0] + 5) {
                pig.snapTo(helper.absoluteVec(diagonal(6.0)));
                back[0] = now;
            }
            if (back[0] >= 0 && again[0] < 0 && v.core().in(Step.TELL)) again[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(snapped[0] >= 0, "moving out of reach snapped the vein, phase " + v.phase());
            helper.assertTrue(again[0] >= 0, "it rears again, cooldown " + v.cooldown());
            long gap = again[0] - snapped[0];
            helper.assertTrue(gap >= 80 && gap <= 83, "80 ticks from the snap to the next tell, took " + gap);
        });
    }

    /** A listener that cancels the "drain" step leaves the vein holding but the pig untouched and the leech unhealed. */
    private static void drainCancel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Veinleech v = onPig(helper, pig);
        v.setHealth(10.0F);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == v && e.step().equals("drain")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.succeedWhen(() -> {
            helper.assertTrue(v.core().in(Step.LATCH) && v.core().elapsed() >= 45, "the latch holds 45 ticks, phase " + v.phase());
            helper.assertTrue(Math.abs(pig[0].getHealth() - pig[0].getMaxHealth()) < 0.01F, "a cancelled drain takes nothing, pig " + pig[0].getHealth());
            helper.assertTrue(Math.abs(v.getHealth() - 10.0F) < 0.01F, "and heals nothing, leech " + v.getHealth());
        });
    }

    /** Health 22, attack 2, armour 0, a 0.6 x 0.5 hitbox. */
    private static void stats(GameTestHelper helper) {
        Veinleech v = leech(helper);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(v.getMaxHealth() - 22.0F) < 0.01F && Math.abs(v.getHealth() - 22.0F) < 0.01F,
                    "health 22, got " + v.getHealth() + "/" + v.getMaxHealth());
            helper.assertTrue(Math.abs(v.getAttributeValue(Attributes.ATTACK_DAMAGE) - 2.0) < 0.01, "attack 2");
            helper.assertTrue(Math.abs(v.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(v.getBbWidth() - 0.6F) < 0.02F && Math.abs(v.getBbHeight() - 0.5F) < 0.02F,
                    "hitbox 0.6 x 0.5, got " + v.getBbWidth() + " x " + v.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: the crimson forest only, a monster, spawn weight 15 in groups of 1 to 2. The Pale Leech is its second look. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(TeemingSpawns.tag("veinleech"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.CRIMSON_FOREST)), "its home holds the crimson forest");
        for (var other : List.of(Biomes.WARPED_FOREST, Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY, Biomes.BASALT_DELTAS, Biomes.PLAINS)) {
            helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("veinleech");
        helper.assertTrue(row.dim() == RosterStats.Dim.NETHER, "a Nether mob");
        helper.assertTrue(ModMobs.VEINLEECH.get().getCategory() == MobCategory.MONSTER, "a monster");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/veinleech")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.VEINLEECH.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 15, "spawn weight 15, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "groups of 1 to 2, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("veinleech");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("pale_leech")), "the Pale Leech is a look: " + looks);
        helper.succeed();
    }
}
