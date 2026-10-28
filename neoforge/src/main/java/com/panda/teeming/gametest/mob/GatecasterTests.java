package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Gatecaster;
import com.panda.teeming.mob.end.Gatecaster.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Gatecaster: every 80 ticks, with a target within 16 blocks in its sight, it weaves for 20 ticks (the tell, which hurts
 * nothing): a gate opens 3-5 blocks behind the target as it faces (at its sides when behind is shut, nowhere when both
 * are), follows the target for 12 ticks and locks for 8. Then a bolt flies out of the gate along the locked line: 8.5 to
 * the first thing it meets, a mob's projectile from the gate's side, no knockback. A shield raised toward the gate
 * turns the bolt back: the caster is dragged to the ground under the gate and takes 8.5 from its own bolt.
 *
 * <p>The caster stands at the west side of the arena with its goals cleared (it reads its target straight from the
 * test, and a follow range of 1 keeps it from picking up survivors in other arenas), and its target stands 3 blocks
 * east of it, facing it, so the gate opens 3-5 blocks further east: every gate, every spot the caster can be dragged
 * to and every bolt stays inside the arena. A stone wall along the west edge, behind the caster, stops any bolt that
 * misses. The 16-block reach is measured straight up, through the opened roof, where no other arena lies.
 */
public final class GatecasterTests {
    private GatecasterTests() {}

    /** The caster at the west side, and its target 3 blocks east of it. */
    private static final Vec3 AT = new Vec3(1.5, 1, 6.5);
    private static final Vec3 MARK = new Vec3(4.5, 1, 6.5);
    /** The caster for the 16-block test, in the middle of the arena under the opened roof. */
    private static final Vec3 MIDDLE = new Vec3(5.5, 1, 5.5);
    /** Yaw facing west (toward the caster) and east (away from it, toward where the gate opens). */
    private static final float WEST = 90.0F, EAST = -90.0F, SOUTH = 0.0F;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("gatecaster_tell", GatecasterTests::tell);
        out.accept("gatecaster_counter", GatecasterTests::counter);
        out.accept("gatecaster_failure", GatecasterTests::failure);
        out.accept("gatecaster_damage", GatecasterTests::damage);
        out.accept("gatecaster_behind", GatecasterTests::behind);
        out.accept("gatecaster_behind_as_it_faces", GatecasterTests::behindAsItFaces);
        out.accept("gatecaster_near_end", GatecasterTests::nearEnd);
        out.accept("gatecaster_far_end", GatecasterTests::farEnd);
        out.accept("gatecaster_sides", GatecasterTests::sides);
        out.accept("gatecaster_no_gate", GatecasterTests::noGate);
        out.accept("gatecaster_lock", GatecasterTests::lock);
        out.accept("gatecaster_sidestep", GatecasterTests::sidestep);
        out.accept("gatecaster_bolt_flies", GatecasterTests::boltFlies);
        out.accept("gatecaster_shield_wrong_way", GatecasterTests::shieldWrongWay);
        out.accept("gatecaster_cooldown", GatecasterTests::cooldown);
        TeemingGameTests.timeLimit("gatecaster_cooldown", 200);
        out.accept("gatecaster_range", GatecasterTests::range);
        out.accept("gatecaster_sight", GatecasterTests::sight);
        out.accept("gatecaster_hit_cancel", GatecasterTests::hitCancel);
        out.accept("gatecaster_stats", GatecasterTests::stats);
        out.accept("gatecaster_home", GatecasterTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    private static Gatecaster caster(GameTestHelper helper, Vec3 at) {
        Gatecaster g = helper.spawn(ModMobs.GATECASTER.get(), at);
        g.removeFreeWill();      // no walking or backing off; its own phase machine still runs
        g.resetCooldown();
        g.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        // the base look now, so its first tick doesn't pick one and refill its health over a test's setHealth
        g.core().setLook(LookDef.BASE, true);
        return g;
    }

    /** A still pig with 40 health facing {@code yaw}. */
    private static Pig pig(GameTestHelper helper, Vec3 at, float yaw) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        turn(pig, yaw);
        return pig;
    }

    private static void turn(LivingEntity e, float yaw) {
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.yHeadRotO = yaw;
        e.setYBodyRot(yaw);
    }

    /** A stone wall along the west edge, up to the roof: it stops every bolt that misses. */
    private static void backstop(GameTestHelper helper) {
        for (int z = 0; z < 12; z++) {
            for (int y = 1; y <= 7; y++) helper.setBlock(new BlockPos(0, y, z), Blocks.STONE);
        }
    }

    /** The caster at the west side, a pig 3 blocks east of it facing it as its target, the backstop behind the caster. */
    private static Gatecaster onPig(GameTestHelper helper, Pig[] out) {
        backstop(helper);
        Gatecaster g = caster(helper, AT);
        Pig pig = pig(helper, MARK, WEST);
        g.setTarget(pig);
        out[0] = pig;
        return g;
    }

    /** Ticks when the tell first started and the bolt first flew, read each tick. */
    private static long[] watch(GameTestHelper helper, Gatecaster g) {
        long[] at = {-1, -1};
        helper.onEachTick(() -> {
            if (at[0] < 0 && g.core().in(Step.TELL)) at[0] = helper.getTick();
            if (at[1] < 0 && g.core().in(Step.BOLT)) at[1] = helper.getTick();
        });
        return at;
    }

    private static double across(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A pig 3 blocks away in sight sets off the tell at once. It lasts 20 ticks, the caster holds still, the gate stays
     * open the whole time, and nothing happens yet: the pig loses no health and no bolt flies. The bolt follows.
     */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        Vec3 start = g.position();
        int[] ticks = {0}, open = {0};
        long[] first = {-1};
        boolean[] hurt = {false}, flying = {false}, bolted = {false};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            bolted[0] |= g.core().in(Step.BOLT);
            if (!g.core().in(Step.TELL)) return;
            if (first[0] < 0) first[0] = helper.getTick();
            ticks[0]++;
            if (g.gate() != null && Gatecaster.Gate.live(g.core().flags())) open[0]++;
            hurt[0] |= pig[0].getHealth() < 40.0F;
            flying[0] |= g.bolt() != null;
            moved[0] = Math.max(moved[0], g.position().subtract(start).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && bolted[0], "the tell runs and the bolt follows, " + ticks[0] + " tell ticks, phase " + g.phase());
            helper.assertTrue(first[0] <= 3, "a target in range and sight sets it off at once, it started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 20) <= 1, "the tell lasts 20 ticks, lasted " + ticks[0]);
            helper.assertTrue(open[0] == ticks[0], "the gate is open the whole tell, " + open[0] + " of " + ticks[0]);
            helper.assertTrue(!hurt[0] && !flying[0], "the pig loses nothing and no bolt flies during the tell");
            helper.assertTrue(moved[0] < 0.1, "it holds still while it weaves, moved " + moved[0]);
        });
    }

    /**
     * The counter: a survival player faces the caster as the tell starts (so the gate opens behind them), turns round
     * 4 ticks in and raises a shield toward the gate. The shield takes the bolt: the player loses nothing. The bolt goes
     * back through the gate: the caster is dragged onto the ground under the gate, within half a block of its middle,
     * 3-5 blocks east of the player and facing them, and its own bolt bursts at its feet for 8.5 (30 -> 21.5).
     */
    private static void counter(GameTestHelper helper) {
        backstop(helper);
        ServerPlayer p = TestPlayers.survivor(helper, MARK);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        Gatecaster g = caster(helper, AT);
        g.setTarget(p);
        float before = p.getHealth();
        long[] start = {-1}, turnedAt = {-1};
        Vec3[] gate = {null};
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            long now = helper.getTick();
            if (start[0] < 0 && g.core().in(Step.TELL)) {
                start[0] = now;
                gate[0] = g.gate();
            }
            turn(p, start[0] >= 0 && now >= start[0] + 4 ? EAST : WEST);
            if (turnedAt[0] < 0 && g.core().in(Step.TURNED)) turnedAt[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gate[0] != null, "a gate opened, phase " + g.phase());
            helper.assertTrue(turnedAt[0] >= 0 && g.turns() == 1, "the bolt was turned back, phase " + g.phase() + ", turns " + g.turns());
            helper.assertTrue(p.isBlocking(), "the player holds the shield up");
            helper.assertTrue(g.lastStruck() == p, "the bolt reached the player, struck " + g.lastStruck());
            helper.assertTrue(p.getHealth() >= before, "the shield took it, health " + p.getHealth() + " of " + before);
            Vec3 floor = helper.absoluteVec(new Vec3(0, 1, 0));
            helper.assertTrue(across(g.position(), gate[0]) < 0.5 && Math.abs(g.getY() - floor.y) < 0.01,
                    "dragged to the ground under the gate " + gate[0] + ", stands at " + g.position());
            double d = across(g.position(), p.position());
            helper.assertTrue(d >= 2.9 && d <= 5.1, "3-5 blocks from the player, " + d);
            helper.assertTrue(Math.abs(g.getHealth() - 21.5F) < 0.01F, "its own bolt bursts for 8.5: 30 -> 21.5, got " + g.getHealth());
            helper.assertTrue(g.gate() == null, "the gate collapsed");
        });
    }

    /**
     * Failing the counter: a survival player keeps facing the caster. The gate opens behind them at chest height
     * (0.65 of 1.8 blocks over their feet), and the bolt hits them in the back for 8.5 (mob damage to players scales with
     * difficulty: 5.25 on Easy, 12.75 on Hard), a mob's projectile coming from the gate behind them, with no knockback.
     */
    private static void failure(GameTestHelper helper) {
        backstop(helper);
        ServerPlayer p = TestPlayers.survivor(helper, MARK);
        turn(p, WEST);
        Gatecaster g = caster(helper, AT);
        g.setTarget(p);
        float before = p.getHealth();
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 5.25F;
            case HARD -> 12.75F;
            default -> 8.5F;
        };
        long[] hitAt = {-1};
        Vec3[] gate = {null};
        double[] push = {0.0};
        helper.onEachTick(() -> {
            // a knockback shows as motion set on the player, read before its own tick spends it
            if (p.getHealth() < before) {
                if (hitAt[0] < 0) hitAt[0] = helper.getTick();
                push[0] = Math.max(push[0], p.getDeltaMovement().horizontalDistance());
            }
            p.doTick();          // the hurt cooldown runs in the player's own tick
            turn(p, WEST);
            if (gate[0] == null && g.core().in(Step.TELL)) gate[0] = g.gate();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hitAt[0] >= 0 && helper.getTick() >= hitAt[0] + 5, "the bolt struck, phase " + g.phase());
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the bolt does " + expected + " here, the player lost " + lost);
            DamageSource last = g.lastSource();
            helper.assertTrue(last != null && last.is(DamageTypes.MOB_PROJECTILE) && last.getEntity() == g, "a mob's projectile from the caster, got " + last);
            helper.assertTrue(gate[0] != null && gate[0].x > p.getX() + 2.9, "the gate stood behind the player (east), " + gate[0]);
            helper.assertTrue(Math.abs(gate[0].y - (p.getY() + 1.17)) < 0.01, "at chest height, 1.17 over the feet: " + (gate[0].y - p.getY()));
            helper.assertTrue(last.getSourcePosition().distanceTo(gate[0]) < 0.01, "the hit came from the gate, " + last.getSourcePosition());
            helper.assertTrue(push[0] < 0.05, "no knockback, pushed at " + push[0]);
            helper.assertTrue(g.turns() == 0 && g.hits() == 1, "nothing turned back, one hit");
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The bolt's hit: a pig with 40 health loses exactly 8.5. */
    private static void damage(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        helper.succeedWhen(() -> {
            helper.assertTrue(g.bolts() == 1 && g.bolt() == null, "the bolt flew and landed, phase " + g.phase());
            helper.assertTrue(g.lastStruck() == pig[0], "the bolt struck the pig, struck " + g.lastStruck());
            helper.assertTrue(Math.abs(pig[0].getHealth() - 31.5F) < 0.01F, "8.5 damage: 40 -> 31.5, got " + pig[0].getHealth());
        });
    }

    /**
     * Behind: with nothing in the way the gate opens exactly 4 blocks (the middle of 3-5) behind the pig, straight
     * back from the way it faces, its middle 0.9 over the pig's feet (a pig is too short for 0.65 of its height to
     * clear the ground).
     */
    private static void behind(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        Vec3[] gate = {null};
        helper.onEachTick(() -> {
            if (gate[0] == null && g.core().in(Step.TELL)) gate[0] = g.gate();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gate[0] != null, "a gate opened, phase " + g.phase());
            Vec3 at = pig[0].position();
            double d = across(gate[0], at);
            helper.assertTrue(Math.abs(d - 4.0) < 0.01, "4 blocks off, " + d);
            helper.assertTrue(gate[0].x - at.x > 3.99 && Math.abs(gate[0].z - at.z) < 0.01, "straight behind (east), " + gate[0]);
            helper.assertTrue(Math.abs(gate[0].y - at.y - 0.9) < 0.01, "0.9 over its feet, " + (gate[0].y - at.y));
            helper.assertTrue(g.straightBolts() == 0, "the bolt came out of the gate");
        });
    }

    /**
     * Behind as the target faces, not away from the caster: a pig facing south (across the caster's line) gets its gate
     * 4 blocks north of it.
     */
    private static void behindAsItFaces(GameTestHelper helper) {
        backstop(helper);
        Gatecaster g = caster(helper, AT);
        Pig pig = pig(helper, MARK, SOUTH);
        g.setTarget(pig);
        Vec3[] gate = {null};
        helper.onEachTick(() -> {
            if (gate[0] == null && g.core().in(Step.TELL)) gate[0] = g.gate();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gate[0] != null, "a gate opened, phase " + g.phase());
            Vec3 at = pig.position();
            helper.assertTrue(at.z - gate[0].z > 3.99 && at.z - gate[0].z < 4.01 && Math.abs(gate[0].x - at.x) < 0.01,
                    "4 blocks north of a pig facing south, gate " + gate[0] + ", pig " + at);
            helper.assertTrue(g.lastStruck() == pig && Math.abs(pig.getHealth() - 31.5F) < 0.01F, "and the bolt struck it, health " + pig.getHealth());
        });
    }

    /** The near end: a wall 4.5 blocks behind the pig shuts the 4- and 5-block spots, so the gate opens 3 blocks back. */
    private static void nearEnd(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        for (int z = 0; z < 12; z++) {
            for (int y = 1; y <= 7; y++) helper.setBlock(new BlockPos(9, y, z), Blocks.STONE);
        }
        Vec3[] gate = {null};
        helper.onEachTick(() -> {
            if (gate[0] == null && g.core().in(Step.TELL)) gate[0] = g.gate();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gate[0] != null, "a gate opened, phase " + g.phase());
            double d = gate[0].x - pig[0].getX();
            helper.assertTrue(Math.abs(d - 3.0) < 0.01, "3 blocks behind, " + d);
            helper.assertTrue(g.lastStruck() == pig[0], "and the bolt struck the pig");
        });
    }

    /** The far end: a block hanging where the 4-block ring would stand (but clear of the 5-block one) puts it 5 back. */
    private static void farEnd(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        helper.setBlock(new BlockPos(7, 2, 5), Blocks.STONE);
        Vec3[] gate = {null};
        helper.onEachTick(() -> {
            if (gate[0] == null && g.core().in(Step.TELL)) gate[0] = g.gate();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gate[0] != null, "a gate opened, phase " + g.phase());
            double d = gate[0].x - pig[0].getX();
            helper.assertTrue(Math.abs(d - 5.0) < 0.01, "5 blocks behind, " + d);
            helper.assertTrue(g.lastStruck() == pig[0], "and the bolt struck the pig");
        });
    }

    /**
     * The sides: a wall right behind the pig shuts every spot back there, so the gate opens at the pig's side. Both
     * sides lie square to the caster, so its left comes first: 4 blocks south of a pig facing west.
     */
    private static void sides(GameTestHelper helper) {
        backstop(helper);
        Gatecaster g = caster(helper, new Vec3(1.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(4.5, 1, 5.5), WEST);
        g.setTarget(pig);
        for (int z = 0; z < 12; z++) {
            for (int y = 1; y <= 7; y++) helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
        }
        Vec3[] gate = {null};
        helper.onEachTick(() -> {
            if (gate[0] == null && g.core().in(Step.TELL)) gate[0] = g.gate();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gate[0] != null, "a gate opened, phase " + g.phase());
            Vec3 at = pig.position();
            helper.assertTrue(Math.abs(gate[0].z - at.z - 4.0) < 0.01 && Math.abs(gate[0].x - at.x) < 0.01,
                    "4 blocks to its left (south), gate " + gate[0] + ", pig " + at);
            helper.assertTrue(g.lastStruck() == pig && Math.abs(pig.getHealth() - 31.5F) < 0.01F, "and the bolt struck it from the side");
        });
    }

    /**
     * No gate: walls shut the pig's back and both its sides, so no gate opens; the bolt is thrown straight from the
     * caster's own ring and still strikes the pig for 8.5.
     */
    private static void noGate(GameTestHelper helper) {
        backstop(helper);
        Gatecaster g = caster(helper, new Vec3(1.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(4.5, 1, 5.5), WEST);
        g.setTarget(pig);
        for (int y = 1; y <= 7; y++) {
            for (int z = 0; z < 12; z++) helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
            for (int x = 3; x <= 5; x++) {
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
            }
        }
        boolean[] gated = {false};
        helper.onEachTick(() -> gated[0] |= g.gate() != null || Gatecaster.Gate.live(g.core().flags()));
        helper.succeedWhen(() -> {
            helper.assertTrue(g.bolts() == 1 && g.bolt() == null, "the bolt flew and landed, phase " + g.phase());
            helper.assertTrue(!gated[0], "no gate opened");
            helper.assertTrue(g.straightBolts() == 1, "the bolt was thrown straight");
            helper.assertTrue(g.boltFrom().distanceTo(g.ringPoint()) < 0.01, "from its own ring, " + g.boltFrom());
            helper.assertTrue(g.lastStruck() == pig && Math.abs(pig.getHealth() - 31.5F) < 0.01F, "and it struck the pig, health " + pig.getHealth());
        });
    }

    /**
     * The aim follows the target for the tell's first 12 ticks and locks for its last 8: the pig steps a block aside 6
     * ticks in and the bolt still finds it; the lock comes 12 ticks in and holds 8.
     */
    private static void lock(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        long[] start = {-1}, lockedAt = {-1};
        int[] lockedTicks = {0};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (!g.core().in(Step.TELL)) return;
            if (start[0] < 0) start[0] = now;
            if (now == start[0] + 6) {
                pig[0].snapTo(helper.absoluteVec(MARK.add(0, 0, 1.0)));
                turn(pig[0], WEST);
            }
            if (g.locked()) {
                if (lockedAt[0] < 0) lockedAt[0] = now;
                lockedTicks[0]++;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lockedAt[0] >= 0 && g.bolts() == 1 && g.bolt() == null, "the aim locked and the bolt landed, phase " + g.phase());
            helper.assertTrue(Math.abs(lockedAt[0] - start[0] - 12) <= 1, "it locks 12 ticks in, locked at " + (lockedAt[0] - start[0]));
            helper.assertTrue(Math.abs(lockedTicks[0] - 8) <= 1, "locked for the last 8 ticks, was " + lockedTicks[0]);
            helper.assertTrue(g.lastStruck() == pig[0] && Math.abs(pig[0].getHealth() - 31.5F) < 0.01F,
                    "it followed the pig's step and struck it, struck " + g.lastStruck() + ", health " + pig[0].getHealth());
        });
    }

    /** The other counter: the pig steps 2 blocks aside the tick the aim locks. The bolt flies past: the pig is unhurt. */
    private static void sidestep(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        long[] lockedAt = {-1};
        helper.onEachTick(() -> {
            if (lockedAt[0] < 0 && g.core().in(Step.TELL) && g.locked()) {
                lockedAt[0] = helper.getTick();
                pig[0].snapTo(helper.absoluteVec(MARK.add(0, 0, 2.0)));
                turn(pig[0], WEST);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lockedAt[0] >= 0 && g.bolts() == 1 && g.bolt() == null, "the bolt flew and stopped, phase " + g.phase());
            helper.assertTrue(g.lastStruck() == null && g.hits() == 0, "it met nothing, struck " + g.lastStruck());
            helper.assertTrue(Math.abs(pig[0].getHealth() - 40.0F) < 0.01F, "the pig is unhurt, health " + pig[0].getHealth());
            helper.assertTrue(g.getHealth() >= 30.0F && g.turns() == 0, "and the caster is untouched");
        });
    }

    /**
     * A visible projectile: the bolt sets out from the gate's middle, flies 0.6 blocks a tick for several ticks (it is
     * seen crossing the 4 blocks to the pig, not landing at once), and the pig is hurt only when it arrives.
     */
    private static void boltFlies(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        Vec3[] gate = {null};
        int[] inFlight = {0};
        double[] lastTravel = {0.0}, worstStep = {0.0};
        boolean[] hurtEarly = {false};
        helper.onEachTick(() -> {
            if (gate[0] == null && g.core().in(Step.TELL)) gate[0] = g.gate();
            if (g.bolt() != null) {
                inFlight[0]++;
                double t = g.bolt().travelled();
                if (inFlight[0] > 1) worstStep[0] = Math.max(worstStep[0], Math.abs(t - lastTravel[0] - 0.6));
                lastTravel[0] = t;
                hurtEarly[0] |= pig[0].getHealth() < 40.0F;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(g.bolts() == 1 && g.bolt() == null && g.lastStruck() == pig[0], "the bolt flew and struck, phase " + g.phase());
            helper.assertTrue(gate[0] != null && g.boltFrom().distanceTo(gate[0]) < 0.01, "it set out from the gate's middle, " + g.boltFrom());
            helper.assertTrue(inFlight[0] >= 4, "in flight for several ticks, " + inFlight[0]);
            helper.assertTrue(worstStep[0] < 0.01, "0.6 blocks a tick, off by up to " + worstStep[0]);
            helper.assertTrue(!hurtEarly[0], "the pig is hurt only when it arrives");
        });
    }

    /**
     * A shield raised the wrong way: a survival player faces the caster with a shield up the whole time. The bolt comes
     * from the gate behind them, so the shield takes nothing: they are hurt, and nothing is turned back.
     */
    private static void shieldWrongWay(GameTestHelper helper) {
        backstop(helper);
        ServerPlayer p = TestPlayers.survivor(helper, MARK);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        Gatecaster g = caster(helper, AT);
        g.setTarget(p);
        float before = p.getHealth();
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            turn(p, WEST);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(g.bolts() == 1 && g.bolt() == null && g.lastStruck() == p, "the bolt struck the player, phase " + g.phase());
            helper.assertTrue(p.isBlocking(), "the shield is up, toward the caster");
            helper.assertTrue(p.getHealth() < before, "it came from behind: the player is hurt, " + p.getHealth() + " of " + before);
            helper.assertTrue(g.turns() == 0 && Math.abs(g.getHealth() - 30.0F) < 0.01F, "nothing turned back");
        });
    }

    /** Every 80 ticks: with the pig standing in sight, the second tell starts 80 ticks after the first. */
    private static void cooldown(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        long[] first = {-1}, second = {-1};
        boolean[] wasIn = {false};
        helper.onEachTick(() -> {
            boolean in = g.core().in(Step.TELL);
            if (in && !wasIn[0]) {
                if (first[0] < 0) first[0] = helper.getTick();
                else if (second[0] < 0) second[0] = helper.getTick();
            }
            wasIn[0] = in;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0] >= 0, "it weaved twice, first at " + first[0] + ", cooldown " + g.castCooldown());
            long gap = second[0] - first[0];
            helper.assertTrue(Math.abs(gap - 80) <= 1, "80 ticks from one tell to the next, took " + gap);
            helper.assertTrue(g.hits() >= 1 && pig[0].getHealth() <= 31.51F, "the first bolt struck, health " + pig[0].getHealth());
        });
    }

    /**
     * Clears the barrier roof over the arena and checks the sky above its middle is open well past 16 blocks, for the
     * test that measures the range straight up.
     */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        for (int y = 2; y <= 24; y++) {
            BlockPos pos = new BlockPos(5, y, 5);
            if (!helper.getBlockState(pos).isAir()) helper.fail("the sky over the arena is shut at y " + y + " by " + helper.getBlockState(pos));
        }
    }

    /**
     * Range, measured straight up: a pig 16.5 blocks above is out of range, no tell for 20 ticks. Moved down to 15.5
     * blocks it sets the tell off within 3 ticks.
     */
    private static void range(GameTestHelper helper) {
        openRoof(helper);
        Gatecaster g = caster(helper, MIDDLE);
        Pig pig = pig(helper, MIDDLE.add(0, 16.5, 0), WEST);
        pig.setNoGravity(true);
        TestPlayers.whenDone(helper, pig::discard);
        g.setTarget(pig);
        long[] told = {-1};
        double[] far = {0.0}, near = {0.0};
        helper.onEachTick(() -> {
            if (told[0] < 0 && g.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.runAtTickTime(20, () -> {
            far[0] = g.distanceTo(pig);
            helper.assertTrue(told[0] < 0 && g.core().idle(), "16.5 blocks away is out of range, told at " + told[0] + " from " + far[0]);
            pig.snapTo(helper.absoluteVec(MIDDLE.add(0, 15.5, 0)));
            near[0] = g.distanceTo(pig);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(far[0] - 16.5) < 0.05 && Math.abs(near[0] - 15.5) < 0.05,
                    "the pig stood 16.5 then 15.5 blocks away, measured " + far[0] + " and " + near[0]);
            helper.assertTrue(told[0] >= 20, "15.5 blocks away is in range, told at " + told[0]);
            helper.assertTrue(told[0] <= 23, "within 3 ticks of coming in, told at " + told[0]);
        });
    }

    /** Line of sight: with a wall between them it never weaves (30 ticks); the wall gone, it weaves within 3 ticks. */
    private static void sight(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        for (int z = 0; z < 12; z++) {
            for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
        }
        Gatecaster g = onPig(helper, pig);
        long[] told = {-1};
        helper.onEachTick(() -> {
            if (told[0] < 0 && g.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(told[0] < 0 && !g.hasLineOfSight(pig[0]), "behind the wall it never weaves, told at " + told[0]);
            for (int z = 0; z < 12; z++) {
                for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(3, y, z), Blocks.AIR);
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(told[0] >= 30 && told[0] <= 33, "in sight again it weaves within 3 ticks, told at " + told[0]));
    }

    /** A listener that cancels the "bolt_hit" step leaves the struck pig unhurt. */
    private static void hitCancel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Gatecaster g = onPig(helper, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == g && e.step().equals("bolt_hit")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.succeedWhen(() -> {
            helper.assertTrue(g.bolts() == 1 && g.bolt() == null, "the bolt flew and landed, phase " + g.phase());
            helper.assertTrue(g.lastStruck() == pig[0], "the bolt met the pig, struck " + g.lastStruck());
            helper.assertTrue(Math.abs(pig[0].getHealth() - 40.0F) < 0.01F, "a cancelled hit does nothing, pig " + pig[0].getHealth());
        });
    }

    /** Health 30, attack 8.5, armour 2, a 0.7 x 2.0 hitbox; it notices targets as far off as it weaves. */
    private static void stats(GameTestHelper helper) {
        Gatecaster g = helper.spawn(ModMobs.GATECASTER.get(), AT);
        g.removeFreeWill();
        g.core().setLook(LookDef.BASE, true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(g.getMaxHealth() - 30.0F) < 0.01F && Math.abs(g.getHealth() - 30.0F) < 0.01F,
                    "health 30, got " + g.getHealth() + "/" + g.getMaxHealth());
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ATTACK_DAMAGE) - 8.5) < 0.01, "attack 8.5");
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.FOLLOW_RANGE) - 16.0) < 0.01, "follow range 16, got "
                    + g.getAttributeValue(Attributes.FOLLOW_RANGE));
            helper.assertTrue(Math.abs(g.getBbWidth() - 0.7F) < 0.02F && Math.abs(g.getBbHeight() - 2.0F) < 0.02F,
                    "hitbox 0.7 x 2.0, got " + g.getBbWidth() + " x " + g.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: the End highlands only, a monster, spawn weight 4 in groups of 1. The Mirrored Weaver is its second look. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("gatecaster"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)), "its home holds the End highlands");
        for (var other : List.of(Biomes.END_MIDLANDS, Biomes.END_BARRENS, Biomes.SMALL_END_ISLANDS, Biomes.THE_END, Biomes.PLAINS)) {
            helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("gatecaster");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.GATECASTER.get().getCategory() == MobCategory.MONSTER, "a monster");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/gatecaster")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.GATECASTER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("gatecaster");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("mirrored_weaver")), "the Mirrored Weaver is a look: " + looks);
        helper.assertTrue(row.material().equals("rift_essence") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("rift_essence")),
                "it drops rift essence, a registered item");
        helper.succeed();
    }
}
