package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.overworld.Shiverwraith;
import com.panda.teeming.mob.overworld.Shiverwraith.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Shiverwraith: with a target within 12 blocks in sight it reaches out for 12 ticks (the tell, which chills and heals
 * nothing), then the tether locks: the target gains 3 freeze ticks a tick (fully frozen in about 2.5 s) and the wraith
 * heals 1 health a second. Fire, a lit campfire or lava within 3 blocks of the target, lost sight, or the target past
 * 12 blocks snaps it; it reaches out again 60 ticks after a snap.
 *
 * <p>The wraith stands in one corner of the arena with its goals cleared (it reads its target straight from the test)
 * and a still pig stands in as the target where the numbers must be exact: pigs freeze and thaw as players do. Freeze
 * damage and the slowdown are shown on a real survival player, ticked by hand (nothing else ticks it here), because the
 * helper's mock player counts as creative and never freezes.
 */
public final class ShiverwraithTests {
    private ShiverwraithTests() {}

    /** The wraith, in a corner. */
    private static final Vec3 AT = new Vec3(1.5, 1, 1.5);
    /** The target, in the middle of the arena: 7.07 blocks from the wraith. Its block is (6, 1, 6). */
    private static final Vec3 MID = new Vec3(6.5, 1, 6.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("shiverwraith_tell", ShiverwraithTests::tell);
        out.accept("shiverwraith_counter", ShiverwraithTests::campfireSnaps);
        out.accept("shiverwraith_failure", ShiverwraithTests::playerFreezes);
        TeemingGameTests.timeLimit("shiverwraith_failure", 200);
        out.accept("shiverwraith_freeze_rate", ShiverwraithTests::freezeRate);
        out.accept("shiverwraith_frozen_time", ShiverwraithTests::frozenTime);
        out.accept("shiverwraith_heal", ShiverwraithTests::heal);
        TeemingGameTests.timeLimit("shiverwraith_heal", 140);
        out.accept("shiverwraith_range", ShiverwraithTests::range);
        out.accept("shiverwraith_range_break", ShiverwraithTests::rangeBreak);
        out.accept("shiverwraith_heat_range", ShiverwraithTests::heatRange);
        TeemingGameTests.timeLimit("shiverwraith_heat_range", 140);
        out.accept("shiverwraith_fire", helper -> heatSnaps(helper, true));
        out.accept("shiverwraith_lava", helper -> heatSnaps(helper, false));
        out.accept("shiverwraith_warm_target", ShiverwraithTests::warmTarget);
        out.accept("shiverwraith_sight", ShiverwraithTests::sight);
        out.accept("shiverwraith_cooldown", ShiverwraithTests::cooldown);
        TeemingGameTests.timeLimit("shiverwraith_cooldown", 200);
        out.accept("shiverwraith_freeze_cancel", ShiverwraithTests::freezeCancel);
        out.accept("shiverwraith_stats", ShiverwraithTests::stats);
        out.accept("shiverwraith_home", ShiverwraithTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    private static Shiverwraith wraith(GameTestHelper helper) {
        Shiverwraith w = helper.spawn(ModMobs.SHIVERWRAITH.get(), AT);
        w.removeFreeWill();      // no wandering or clawing; its own phase machine still runs
        w.resetCooldown();
        // the base look now, so its first tick doesn't pick one and refill its health over a test's setHealth
        w.core().setLook(LookDef.BASE, true);
        return w;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    /** A point {@code d} blocks from the wraith along the arena's diagonal. */
    private static Vec3 diagonal(double d) {
        double k = d / Math.sqrt(2.0);
        return AT.add(k, 0, k);
    }

    /** A wraith and a pig in the middle of the arena, the pig its target. */
    private static Shiverwraith onPig(GameTestHelper helper, Pig[] out) {
        Shiverwraith w = wraith(helper);
        Pig pig = pig(helper, MID);
        w.setTarget(pig);
        out[0] = pig;
        return w;
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A pig 7 blocks away in sight sets off the tell at once. It lasts 12 ticks, the wraith holds still, and nothing
     * happens yet: no freeze on the pig, no healing (the wraith is at 10 of 20). The tether follows it.
     */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        w.setHealth(10.0F);
        Vec3 start = w.position();
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] chilled = {false}, healed = {false}, tethered = {false};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            tethered[0] |= w.core().in(Step.TETHER);
            if (!w.core().in(Step.TELL)) return;
            if (first[0] < 0) first[0] = helper.getTick();
            ticks[0]++;
            chilled[0] |= pig[0].getTicksFrozen() > 0;
            healed[0] |= w.getHealth() > 10.0F;
            moved[0] = Math.max(moved[0], w.position().subtract(start).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && tethered[0], "the tell runs and the tether follows, " + ticks[0] + " tell ticks, phase " + w.phase());
            helper.assertTrue(first[0] <= 3, "a target in range and sight sets it off at once, it started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 12) <= 1, "the tell lasts 12 ticks, lasted " + ticks[0]);
            helper.assertTrue(!chilled[0], "nothing freezes during the tell");
            helper.assertTrue(!healed[0], "it heals nothing during the tell");
            helper.assertTrue(moved[0] < 0.1, "it holds still while it reaches out, moved " + moved[0]);
        });
    }

    /**
     * The counter: 10 ticks into the tether a lit campfire is set 2 blocks from the pig. The tether snaps within 2 ticks
     * (the recoil phase starts), the freeze stops (the pig thaws afterwards) and it is off the tether.
     */
    private static void campfireSnaps(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        long[] locked = {-1}, placed = {-1}, snapped = {-1};
        int[] frozenAtSnap = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && w.core().in(Step.TETHER)) locked[0] = now;
            if (locked[0] >= 0 && placed[0] < 0 && now >= locked[0] + 10) {
                helper.setBlock(new BlockPos(8, 1, 6), Blocks.CAMPFIRE);
                placed[0] = now;
            }
            if (snapped[0] < 0 && w.core().in(Step.SNAP)) {
                snapped[0] = now;
                frozenAtSnap[0] = pig[0].getTicksFrozen();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(placed[0] >= 0, "the tether locked, phase " + w.phase());
            helper.assertTrue(CampfireBlock.isLitCampfire(helper.getBlockState(new BlockPos(8, 1, 6))), "the campfire is lit");
            helper.assertTrue(snapped[0] >= 0, "a lit campfire 2 blocks from the target snaps the tether, phase " + w.phase());
            helper.assertTrue(snapped[0] - placed[0] <= 2, "within 2 ticks, took " + (snapped[0] - placed[0]));
            helper.assertTrue(frozenAtSnap[0] > 0, "it had been freezing the pig, " + frozenAtSnap[0]);
            helper.assertTrue(helper.getTick() >= snapped[0] + 5, "waiting to see the pig thaw");
            helper.assertTrue(pig[0].getTicksFrozen() < frozenAtSnap[0], "the freeze stopped: " + frozenAtSnap[0] + " -> " + pig[0].getTicksFrozen());
            helper.assertTrue(!w.core().in(Step.TETHER), "it is off the tether, phase " + w.phase());
        });
    }

    /**
     * Failing the counter: a survival player stays in the beam. About 2.5 s after the lock they are fully frozen, then
     * vanilla freeze damage lands, they are slowed by the powder-snow slowdown, and the wraith (at 10 of 20) has healed.
     */
    private static void playerFreezes(GameTestHelper helper) {
        Shiverwraith w = wraith(helper);
        w.setHealth(10.0F);
        ServerPlayer p = TestPlayers.survivor(helper, MID);
        w.setTarget(p);
        float before = p.getHealth();
        long[] locked = {-1}, frozen = {-1};
        helper.onEachTick(() -> {
            p.doTick();          // thaw, freeze damage and the slowdown run in the player's own tick
            long now = helper.getTick();
            if (locked[0] < 0 && w.core().in(Step.TETHER)) locked[0] = now;
            if (locked[0] >= 0 && frozen[0] < 0 && p.isFullyFrozen()) frozen[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(locked[0] >= 0, "the tether locked on the player, phase " + w.phase());
            helper.assertTrue(frozen[0] >= 0, "the player freezes, " + p.getTicksFrozen() + " freeze ticks");
            helper.assertTrue(frozen[0] - locked[0] <= 50, "fully frozen within 2.5 s of the lock, took " + (frozen[0] - locked[0]) + " ticks");
            float lost = before - p.getHealth();
            helper.assertTrue(lost >= 1.0F - 0.01F, "a frozen player takes freeze damage, lost " + lost);
            DamageSource last = p.getLastDamageSource();
            helper.assertTrue(last != null && last.is(DamageTypes.FREEZE), "the damage is freezing, got " + last);
            double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(speed < 0.07, "frozen, the player is slowed (base 0.1), speed " + speed);
            helper.assertTrue(w.getHealth() > 10.0F, "the wraith healed while it held, health " + w.getHealth());
            helper.assertTrue(w.core().in(Step.TETHER), "the tether still holds, phase " + w.phase());
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** Locked on, the pig gains 3 freeze ticks a tick: 60 over 20 ticks (a tick either way for when the reading lands). */
    private static void freezeRate(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        long[] locked = {-1};
        int[] a = {-1}, b = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && w.core().in(Step.TETHER)) locked[0] = now;
            if (locked[0] < 0) return;
            if (now == locked[0] + 5) a[0] = pig[0].getTicksFrozen();
            if (now == locked[0] + 25) b[0] = pig[0].getTicksFrozen();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(a[0] >= 0 && b[0] >= 0, "both readings taken, phase " + w.phase());
            int gain = b[0] - a[0];
            helper.assertTrue(Math.abs(gain - 60) <= 5, "3 freeze ticks a tick: 60 over 20 ticks, got " + gain + " (" + a[0] + " -> " + b[0] + ")");
            helper.assertTrue(w.core().in(Step.TETHER), "it held throughout, phase " + w.phase());
        });
    }

    /** From no frost at all, the pig is fully frozen (140 freeze ticks) 44 to 50 ticks after the lock: about 2.5 s. */
    private static void frozenTime(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        long[] locked = {-1}, frozen = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && w.core().in(Step.TETHER)) {
                locked[0] = now;
                helper.assertTrue(pig[0].getTicksFrozen() == 0, "no frost before the lock, " + pig[0].getTicksFrozen());
            }
            if (locked[0] >= 0 && frozen[0] < 0 && pig[0].isFullyFrozen()) frozen[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(frozen[0] >= 0, "the pig freezes, " + pig[0].getTicksFrozen() + " freeze ticks");
            long took = frozen[0] - locked[0];
            helper.assertTrue(took >= 44 && took <= 50, "fully frozen in about 2.5 s (140 / 3 = 47 ticks), took " + took);
        });
    }

    /** Hurt to 10 of 20, the wraith heals nothing in the tether's first 18 ticks and exactly 3 by its 65th: 1 a second. */
    private static void heal(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        w.setHealth(10.0F);
        boolean[] early = {false};
        float[] at65 = {-1.0F};
        helper.onEachTick(() -> {
            if (!w.core().in(Step.TETHER)) return;
            int t = w.core().elapsed();
            if (t <= 18) early[0] |= w.getHealth() > 10.0F;
            if (t >= 65 && at65[0] < 0) at65[0] = w.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at65[0] >= 0, "the tether held 65 ticks, phase " + w.phase());
            helper.assertTrue(!early[0], "no healing before the first second");
            helper.assertTrue(Math.abs(at65[0] - 13.0F) < 0.01F, "1 health a second: 10 -> 13 after 65 ticks, got " + at65[0]);
        });
    }

    /** A pig 12.5 blocks away is out of reach: no tell for 20 ticks. Moved to 11.5 it sets the tell off within 3 ticks. */
    private static void range(GameTestHelper helper) {
        Shiverwraith w = wraith(helper);
        Pig pig = pig(helper, diagonal(12.5));
        w.setTarget(pig);
        long[] reached = {-1};
        double[] far = {0.0}, near = {0.0};
        helper.onEachTick(() -> {
            if (reached[0] < 0 && w.core().in(Step.TELL)) reached[0] = helper.getTick();
        });
        helper.runAtTickTime(20, () -> {
            far[0] = w.distanceTo(pig);
            helper.assertTrue(reached[0] < 0 && w.core().idle(), "12.5 blocks away is out of reach, reached at " + reached[0] + " from " + far[0]);
            pig.snapTo(helper.absoluteVec(diagonal(11.5)));
            near[0] = w.distanceTo(pig);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(far[0] - 12.5) < 0.05 && Math.abs(near[0] - 11.5) < 0.05,
                    "the pig stood 12.5 then 11.5 blocks away, measured " + far[0] + " and " + near[0]);
            helper.assertTrue(reached[0] >= 20, "11.5 blocks away is in reach, reached at " + reached[0]);
            helper.assertTrue(reached[0] <= 23, "within 3 ticks of stepping in, reached at " + reached[0]);
        });
    }

    /** Locked on a pig 6 blocks away, the pig moves to 11.5: it holds 10 ticks. Moved to 12.5 it snaps within 2 ticks. */
    private static void rangeBreak(GameTestHelper helper) {
        Shiverwraith w = wraith(helper);
        Pig pig = pig(helper, diagonal(6.0));
        w.setTarget(pig);
        long[] locked = {-1}, edge = {-1}, out = {-1}, snapped = {-1};
        boolean[] early = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && w.core().in(Step.TETHER)) locked[0] = now;
            if (snapped[0] < 0 && w.core().in(Step.SNAP)) {
                snapped[0] = now;
                early[0] = out[0] < 0;
            }
            if (locked[0] >= 0 && edge[0] < 0 && now >= locked[0] + 5) {
                pig.snapTo(helper.absoluteVec(diagonal(11.5)));
                edge[0] = now;
            }
            if (edge[0] >= 0 && out[0] < 0 && now >= edge[0] + 10) {
                early[0] |= !w.core().in(Step.TETHER);
                pig.snapTo(helper.absoluteVec(diagonal(12.5)));
                out[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(out[0] >= 0, "the tether locked and held, locked at " + locked[0]);
            helper.assertTrue(!early[0], "11.5 blocks away it held");
            helper.assertTrue(snapped[0] >= 0, "12.5 blocks away snaps it, phase " + w.phase());
            helper.assertTrue(snapped[0] - out[0] <= 2, "within 2 ticks, took " + (snapped[0] - out[0]));
        });
    }

    /**
     * Heat must be within 3 blocks of the target: a lit campfire 4 blocks from the pig leaves the tether holding for 15
     * ticks, an unlit campfire 3 blocks away for 10 more; lit at 3 blocks it snaps within 2 ticks.
     */
    private static void heatRange(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        BlockPos four = new BlockPos(10, 1, 6), three = new BlockPos(9, 1, 6);
        long[] locked = {-1}, unlit = {-1}, lit = {-1}, snapped = {-1};
        boolean[] early = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && w.core().in(Step.TETHER)) {
                locked[0] = now;
                helper.setBlock(four, Blocks.CAMPFIRE);
            }
            if (snapped[0] < 0 && w.core().in(Step.SNAP)) {
                snapped[0] = now;
                early[0] = lit[0] < 0;
            }
            if (locked[0] >= 0 && unlit[0] < 0 && now >= locked[0] + 15) {
                helper.setBlock(four, Blocks.AIR);
                helper.setBlock(three, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
                unlit[0] = now;
            }
            if (unlit[0] >= 0 && lit[0] < 0 && now >= unlit[0] + 10) {
                helper.setBlock(three, Blocks.CAMPFIRE);
                lit[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lit[0] >= 0, "the tether locked and held through both, locked at " + locked[0]);
            helper.assertTrue(!early[0], "a lit campfire 4 blocks away and an unlit one 3 away left it holding");
            helper.assertTrue(snapped[0] >= 0, "a lit campfire 3 blocks away snaps it, phase " + w.phase());
            helper.assertTrue(snapped[0] - lit[0] <= 2, "within 2 ticks, took " + (snapped[0] - lit[0]));
        });
    }

    /** Fire (on netherrack) or lava set 3 blocks from the pig 5 ticks into the tether snaps it within 2 ticks. */
    private static void heatSnaps(GameTestHelper helper, boolean fire) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        BlockPos at = new BlockPos(9, 1, 6);
        long[] locked = {-1}, placed = {-1}, snapped = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && w.core().in(Step.TETHER)) locked[0] = now;
            if (locked[0] >= 0 && placed[0] < 0 && now >= locked[0] + 5) {
                if (fire) {
                    helper.setBlock(at.below(), Blocks.NETHERRACK);
                    helper.setBlock(at, Blocks.FIRE);
                } else {
                    helper.setBlock(at, Blocks.LAVA);
                }
                placed[0] = now;
            }
            if (snapped[0] < 0 && w.core().in(Step.SNAP)) snapped[0] = now;
        });
        String what = fire ? "fire" : "lava";
        helper.succeedWhen(() -> {
            helper.assertTrue(placed[0] >= 0, "the tether locked, phase " + w.phase());
            helper.assertTrue(snapped[0] > placed[0], what + " 3 blocks from the target snaps the tether, phase " + w.phase());
            helper.assertTrue(snapped[0] - placed[0] <= 2, "within 2 ticks, took " + (snapped[0] - placed[0]));
        });
    }

    /** A target standing 2 blocks from a lit campfire is never reached for: no tell in 30 ticks, though it is in range and sight. */
    private static void warmTarget(GameTestHelper helper) {
        helper.setBlock(new BlockPos(8, 1, 6), Blocks.CAMPFIRE);
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        boolean[] reached = {false};
        helper.onEachTick(() -> reached[0] |= !w.core().idle());
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(w.distanceTo(pig[0]) <= 12.0 && w.hasLineOfSight(pig[0]), "the pig is in range and sight");
            helper.assertTrue(!reached[0], "it never reaches for a warmed target, phase " + w.phase());
            helper.succeed();
        });
    }

    /** A stone wall raised between them 10 ticks into the tether snaps it within 2 ticks: line of sight is lost. */
    private static void sight(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        long[] locked = {-1}, walled = {-1}, snapped = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && w.core().in(Step.TETHER)) locked[0] = now;
            if (locked[0] >= 0 && walled[0] < 0 && now >= locked[0] + 10) {
                for (int z = 0; z < 12; z++) {
                    for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
                }
                walled[0] = now;
            }
            if (snapped[0] < 0 && w.core().in(Step.SNAP)) snapped[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(walled[0] >= 0, "the tether locked, phase " + w.phase());
            helper.assertTrue(snapped[0] > walled[0], "a wall between them snaps the tether, phase " + w.phase());
            helper.assertTrue(snapped[0] - walled[0] <= 2, "within 2 ticks, took " + (snapped[0] - walled[0]));
            helper.assertTrue(!w.hasLineOfSight(pig[0]), "the wall blocks its sight");
        });
    }

    /** After a snap (a campfire, taken away 5 ticks later) it reaches out again 60 to 63 ticks after the snap, not before. */
    private static void cooldown(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        BlockPos fire = new BlockPos(8, 1, 6);
        long[] locked = {-1}, placed = {-1}, snapped = {-1}, cleared = {-1}, again = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && w.core().in(Step.TETHER)) locked[0] = now;
            if (locked[0] >= 0 && placed[0] < 0 && now >= locked[0] + 5) {
                helper.setBlock(fire, Blocks.CAMPFIRE);
                placed[0] = now;
            }
            if (snapped[0] < 0 && w.core().in(Step.SNAP)) snapped[0] = now;
            if (snapped[0] >= 0 && cleared[0] < 0 && now >= snapped[0] + 5) {
                helper.setBlock(fire, Blocks.AIR);
                cleared[0] = now;
            }
            if (snapped[0] >= 0 && again[0] < 0 && w.core().in(Step.TELL)) again[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(snapped[0] >= 0, "the campfire snapped the tether, phase " + w.phase());
            helper.assertTrue(again[0] >= 0, "it reaches out again, cooldown " + w.cooldown());
            long gap = again[0] - snapped[0];
            helper.assertTrue(gap >= 60 && gap <= 63, "60 ticks from the snap to the next tell, took " + gap);
        });
    }

    /** A listener that cancels the "freeze" step leaves the tether holding and healing but the pig unfrozen. */
    private static void freezeCancel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Shiverwraith w = onPig(helper, pig);
        w.setHealth(10.0F);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == w && e.step().equals("freeze")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.succeedWhen(() -> {
            helper.assertTrue(w.core().in(Step.TETHER) && w.core().elapsed() >= 25, "the tether holds 25 ticks, phase " + w.phase());
            helper.assertTrue(pig[0].getTicksFrozen() == 0, "a cancelled freeze step chills nothing, " + pig[0].getTicksFrozen());
            helper.assertTrue(w.getHealth() > 10.0F, "it still heals, health " + w.getHealth());
        });
    }

    /** Health 20, attack 3, armour 0, a 0.7 x 1.9 hitbox. */
    private static void stats(GameTestHelper helper) {
        Shiverwraith w = wraith(helper);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(w.getMaxHealth() - 20.0F) < 0.01F && Math.abs(w.getHealth() - 20.0F) < 0.01F,
                    "health 20, got " + w.getHealth() + "/" + w.getMaxHealth());
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < 0.01, "attack 3");
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(w.getBbWidth() - 0.7F) < 0.02F && Math.abs(w.getBbHeight() - 1.9F) < 0.02F,
                    "hitbox 0.7 x 1.9, got " + w.getBbWidth() + " x " + w.getBbHeight());
            helper.assertTrue(!w.canFreeze(), "it never freezes itself");
            helper.succeed();
        });
    }

    /**
     * Its home: snowy plains, ice spikes and frozen peaks, weight 10, one at a time, at night only (a lit spot rates
     * below 0, which natural spawning refuses).
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/shiverwraith"));
        BiomeModifier modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key)
                .map(h -> h.value()).orElse(null);
        helper.assertTrue(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier, "a spawn modifier for the Shiverwraith, got " + modifier);
        var spawns = (BiomeModifiers.AddSpawnsBiomeModifier) modifier;
        for (var biome : List.of(Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.FROZEN_PEAKS)) {
            helper.assertTrue(spawns.biomes().contains(biomes.getOrThrow(biome)), "it spawns in " + biome.identifier());
        }
        helper.assertTrue(!spawns.biomes().contains(biomes.getOrThrow(Biomes.PLAINS)), "not on the plains");
        var entries = spawns.spawners().unwrap();
        helper.assertTrue(entries.size() == 1, "one spawner entry, got " + entries.size());
        var entry = entries.getFirst();
        helper.assertTrue(entry.value().type() == ModMobs.SHIVERWRAITH.get(), "the entry spawns Shiverwraiths");
        helper.assertTrue(entry.weight() == 10, "weight 10, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "one at a time, got " + entry.value().count().minInclusive() + " to " + entry.value().count().maxInclusive());
        helper.assertTrue(!RosterStats.get("shiverwraith").anyLight(), "it spawns only in the dark");
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at.east(), Blocks.GLOWSTONE);
        Shiverwraith w = wraith(helper);
        helper.runAtTickTime(2, () -> {
            float value = w.getWalkTargetValue(helper.absolutePos(at), helper.getLevel());
            helper.assertTrue(value < 0.0F, "a lit spot rates below 0, got " + value);
            helper.succeed();
        });
    }
}
