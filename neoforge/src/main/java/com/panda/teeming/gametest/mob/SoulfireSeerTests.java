package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.SoulfireSeer;
import com.panda.teeming.mob.nether.SoulfireSeer.Step;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * Soulfire Seer: every 100 ticks, with a target within 32 blocks in its sight, it takes aim for 24 ticks (the tell,
 * which hurts nothing): the line follows the target for 16 ticks, then locks for 8. Then the beam fires along the locked
 * line out to 32 blocks: 9 and 3 s of soul fire to the first thing it meets; blocks stop it. A beam that hits nothing
 * (a sidestep after the lock, or cover while the line is drawn) snuffs its eye: blind for 80 ticks.
 *
 * <p>The seer stands at one side of the arena with its goals cleared (it reads its target straight from the test, and
 * a follow range of 1 keeps it from picking up survivors in other arenas), and a still pig stands in as the target 8
 * blocks off. A stone wall at the far side stops every beam inside the arena, so a missed beam never reaches another
 * test. The 32-block numbers are measured straight up, through the opened roof, where no other arena lies. Targets that
 * must show the beam's damage alone are given fire resistance, so the soul fire it lights adds nothing.
 */
public final class SoulfireSeerTests {
    private SoulfireSeerTests() {}

    /** The seer, at one side of the arena, and the target 8 blocks from it across the middle. */
    private static final Vec3 AT = new Vec3(2.5, 1, 6.5);
    private static final Vec3 MARK = new Vec3(10.5, 1, 6.5);
    /** A step to the side: off the line the beam will take. */
    private static final Vec3 ASIDE = new Vec3(10.5, 1, 8.5);
    /** The seer for the 32-block tests, in the middle of the arena under the opened roof. */
    private static final Vec3 MIDDLE = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("soulfire_seer_tell", SoulfireSeerTests::tell);
        out.accept("soulfire_seer_counter", SoulfireSeerTests::sidestep);
        TeemingGameTests.timeLimit("soulfire_seer_counter", 140);
        out.accept("soulfire_seer_failure", SoulfireSeerTests::playerStruck);
        out.accept("soulfire_seer_damage", SoulfireSeerTests::damage);
        out.accept("soulfire_seer_soul_fire", SoulfireSeerTests::soulFire);
        TeemingGameTests.timeLimit("soulfire_seer_soul_fire", 140);
        out.accept("soulfire_seer_lock", SoulfireSeerTests::lock);
        out.accept("soulfire_seer_range", SoulfireSeerTests::range);
        out.accept("soulfire_seer_reach", SoulfireSeerTests::reach);
        out.accept("soulfire_seer_reach_end", SoulfireSeerTests::reachEnd);
        out.accept("soulfire_seer_sight", SoulfireSeerTests::sight);
        out.accept("soulfire_seer_cover", SoulfireSeerTests::cover);
        out.accept("soulfire_seer_first_hit", SoulfireSeerTests::firstHit);
        out.accept("soulfire_seer_cooldown", SoulfireSeerTests::cooldown);
        TeemingGameTests.timeLimit("soulfire_seer_cooldown", 180);
        out.accept("soulfire_seer_snuff_time", SoulfireSeerTests::snuffTime);
        TeemingGameTests.timeLimit("soulfire_seer_snuff_time", 180);
        out.accept("soulfire_seer_shield", SoulfireSeerTests::shield);
        out.accept("soulfire_seer_hit_cancel", SoulfireSeerTests::hitCancel);
        out.accept("soulfire_seer_stats", SoulfireSeerTests::stats);
        out.accept("soulfire_seer_home", SoulfireSeerTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    private static SoulfireSeer seer(GameTestHelper helper, Vec3 at) {
        SoulfireSeer s = helper.spawn(ModMobs.SOULFIRE_SEER.get(), at);
        s.removeFreeWill();      // no walking or backing off; its own phase machine still runs
        s.resetCooldown();
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        // the base look now, so its first tick doesn't pick one and refill its health over a test's setHealth
        s.core().setLook(LookDef.BASE, true);
        return s;
    }

    /** A still pig with 40 health, sturdy enough for two beams and their fire. */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        return pig;
    }

    /** Fire resistance: the soul fire the beam lights then costs nothing, so what is lost is the beam's own hit. */
    private static void fireproof(LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000));
    }

    /** A stone wall across the far side of the arena, up to the roof: it stops every beam fired across the arena. */
    private static void backstop(GameTestHelper helper) {
        for (int z = 0; z < 12; z++) {
            for (int y = 1; y <= 7; y++) helper.setBlock(new BlockPos(11, y, z), Blocks.STONE);
        }
    }

    /** A stone wall across the arena between the seer and the target, four high. */
    private static void wall(GameTestHelper helper) {
        for (int z = 0; z < 12; z++) {
            for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
        }
    }

    /** The seer at its side, the pig 8 blocks off across the arena as its target, a backstop behind the pig. */
    private static SoulfireSeer onPig(GameTestHelper helper, Pig[] out) {
        backstop(helper);
        SoulfireSeer s = seer(helper, AT);
        Pig pig = pig(helper, MARK);
        s.setTarget(pig);
        out[0] = pig;
        return s;
    }

    /**
     * Clears the barrier roof over the arena and checks the sky above its middle is open well past 32 blocks, for the
     * tests that measure the range straight up.
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
        for (int y = 2; y <= 40; y++) {
            BlockPos pos = new BlockPos(5, y, 5);
            if (!helper.getBlockState(pos).isAir()) helper.fail("the sky over the arena is shut at y " + y + " by " + helper.getBlockState(pos));
        }
    }

    /** A still, floating pig {@code up} blocks above the arena floor over the middle, removed when the test ends. */
    private static Pig skyPig(GameTestHelper helper, double up) {
        Pig pig = pig(helper, MIDDLE.add(0, up, 0));
        pig.setNoGravity(true);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** Moves {@code pig} straight up or down so its feet stand {@code above} blocks over the seer's eye. */
    private static void feetAboveEye(GameTestHelper helper, Pig pig, SoulfireSeer s, double above) {
        Vec3 at = helper.absoluteVec(MIDDLE);
        pig.snapTo(at.x, s.getEyeY() + above, at.z);
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A pig 8 blocks away in sight sets off the tell at once. It lasts 24 ticks, the seer holds still, and nothing
     * happens yet: the pig loses no health and isn't burning. The beam follows it.
     */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        Vec3 start = s.position();
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] hurt = {false}, fired = {false};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            fired[0] |= s.core().in(Step.FIRE);
            if (!s.core().in(Step.TELL)) return;
            if (first[0] < 0) first[0] = helper.getTick();
            ticks[0]++;
            hurt[0] |= pig[0].getHealth() < 40.0F || pig[0].isOnFire();
            moved[0] = Math.max(moved[0], s.position().subtract(start).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && fired[0], "the tell runs and the beam follows, " + ticks[0] + " tell ticks, phase " + s.phase());
            helper.assertTrue(first[0] <= 3, "a target in range and sight sets it off at once, it started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 24) <= 1, "the tell lasts 24 ticks, lasted " + ticks[0]);
            helper.assertTrue(!hurt[0], "the pig loses nothing and doesn't burn during the tell");
            helper.assertTrue(moved[0] < 0.1, "it holds still while it aims, moved " + moved[0]);
        });
    }

    /**
     * The counter: the pig steps 2 blocks aside the tick the aim locks. The beam goes down the locked line into the wall
     * and hits nothing: the pig is unhurt and not burning, and the seer's eye is snuffed out: it is blind (no target).
     */
    private static void sidestep(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        long[] lockedAt = {-1}, snuffed = {-1};
        boolean[] lostTarget = {false};
        helper.onEachTick(() -> {
            if (lockedAt[0] < 0 && s.core().in(Step.TELL) && s.locked()) {
                lockedAt[0] = helper.getTick();
                pig[0].snapTo(helper.absoluteVec(ASIDE));
            }
            if (s.core().in(Step.SNUFFED)) {
                if (snuffed[0] < 0) snuffed[0] = helper.getTick();
                lostTarget[0] |= s.getTarget() == null;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lockedAt[0] >= 0, "the aim locked, phase " + s.phase());
            helper.assertTrue(snuffed[0] >= 0, "a beam that hits nothing snuffs its eye, phase " + s.phase() + ", misses " + s.misses());
            helper.assertTrue(s.misses() == 1 && s.struck() == null, "the beam met nothing, struck " + s.struck());
            helper.assertTrue(s.beamEnd().x < helper.absoluteVec(new Vec3(11.01, 0, 0)).x, "the wall stopped it, it ended at " + s.beamEnd());
            helper.assertTrue(Math.abs(pig[0].getHealth() - 40.0F) < 0.01F && !pig[0].isOnFire(), "the pig is unhurt, health " + pig[0].getHealth());
            helper.assertTrue(lostTarget[0], "blind, it has no target");
            helper.assertTrue(s.blind(), "and it is still blind");
        });
    }

    /**
     * Failing the counter: a survival player stays on the line. The beam hits them for 9 (mob damage to players scales
     * with difficulty: 5.5 on Easy, 13.5 on Hard), a mob's projectile, and sets them alight. Fire resistance keeps the
     * fire's own ticks out of the reading; the burning still shows.
     */
    private static void playerStruck(GameTestHelper helper) {
        backstop(helper);
        SoulfireSeer s = seer(helper, AT);
        ServerPlayer p = TestPlayers.survivor(helper, MARK);
        fireproof(p);
        s.setTarget(p);
        float before = p.getHealth();
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 5.5F;
            case HARD -> 13.5F;
            default -> 9.0F;
        };
        long[] firedAt = {-1};
        boolean[] burned = {false};
        DamageSource[] last = {null};
        helper.onEachTick(() -> {
            p.doTick();          // the hurt cooldown and the burning run down in the player's own tick
            if (firedAt[0] < 0 && s.core().in(Step.FIRE)) firedAt[0] = helper.getTick();
            if (firedAt[0] >= 0) {
                burned[0] |= p.isOnFire();
                if (last[0] == null) last[0] = p.getLastDamageSource();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(firedAt[0] >= 0 && helper.getTick() >= firedAt[0] + 5, "the beam fired, phase " + s.phase());
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the beam does " + expected + " here, the player lost " + lost);
            helper.assertTrue(last[0] != null && last[0].is(DamageTypes.MOB_PROJECTILE), "a mob's projectile, got " + last[0]);
            helper.assertTrue(burned[0], "the player burns");
            helper.assertTrue(s.struck() == p && s.misses() == 0, "the beam struck the player, struck " + s.struck());
            helper.assertTrue(!s.blind(), "a beam that struck doesn't snuff its eye");
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The beam's hit: a fire-proofed pig with 40 health loses exactly 9. */
    private static void damage(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        fireproof(pig[0]);
        long[] firedAt = {-1};
        helper.onEachTick(() -> {
            if (firedAt[0] < 0 && s.core().in(Step.FIRE)) firedAt[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(firedAt[0] >= 0 && helper.getTick() >= firedAt[0] + 5, "the beam fired, phase " + s.phase());
            helper.assertTrue(s.struck() == pig[0], "the beam struck the pig, struck " + s.struck());
            helper.assertTrue(Math.abs(pig[0].getHealth() - 31.0F) < 0.01F, "9 damage: 40 -> 31, got " + pig[0].getHealth());
        });
    }

    /** The soul fire: the pig the beam strikes burns for 60 ticks (3 s), counted tick by tick, from the beam's tick. */
    private static void soulFire(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        long[] firedAt = {-1}, outAt = {-1};
        int[] burning = {0};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (firedAt[0] < 0 && s.core().in(Step.FIRE)) firedAt[0] = now;
            if (firedAt[0] < 0) return;
            if (pig[0].isOnFire()) burning[0]++;
            else if (burning[0] > 0 && outAt[0] < 0) outAt[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(firedAt[0] >= 0, "the beam fired, phase " + s.phase());
            helper.assertTrue(outAt[0] >= 0, "the pig burned and went out, " + burning[0] + " burning ticks");
            helper.assertTrue(Math.abs(burning[0] - 60) <= 2, "it burns 60 ticks, burned " + burning[0]);
            helper.assertTrue(pig[0].getHealth() < 31.0F, "and the fire hurt it past the beam's 9, health " + pig[0].getHealth());
        });
    }

    /**
     * The line follows the target for the tell's first 16 ticks and locks for its last 8: the pig steps aside 10 ticks
     * in and the beam still finds it; the lock comes 16 ticks in and holds 8 ticks.
     */
    private static void lock(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        fireproof(pig[0]);
        long[] start = {-1}, lockedAt = {-1};
        int[] lockedTicks = {0};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (!s.core().in(Step.TELL)) return;
            if (start[0] < 0) start[0] = now;
            if (now == start[0] + 10) pig[0].snapTo(helper.absoluteVec(ASIDE));
            if (s.locked()) {
                if (lockedAt[0] < 0) lockedAt[0] = now;
                lockedTicks[0]++;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lockedAt[0] >= 0 && s.tells() == 1 && !s.core().in(Step.TELL), "the aim locked and fired, phase " + s.phase());
            helper.assertTrue(Math.abs(lockedAt[0] - start[0] - 16) <= 1, "it locks 16 ticks in, locked at " + (lockedAt[0] - start[0]));
            helper.assertTrue(Math.abs(lockedTicks[0] - 8) <= 1, "locked for the last 8 ticks, was " + lockedTicks[0]);
            helper.assertTrue(s.struck() == pig[0] && Math.abs(pig[0].getHealth() - 31.0F) < 0.01F,
                    "it followed the pig's step and struck it, struck " + s.struck() + ", health " + pig[0].getHealth());
        });
    }

    /**
     * Range, measured straight up: a pig 32.5 blocks above is out of range, no tell for 20 ticks. Moved down to 31.5
     * blocks it sets the tell off within 3 ticks.
     */
    private static void range(GameTestHelper helper) {
        openRoof(helper);
        SoulfireSeer s = seer(helper, MIDDLE);
        Pig pig = skyPig(helper, 32.5);
        s.setTarget(pig);
        long[] told = {-1};
        double[] far = {0.0}, near = {0.0};
        helper.onEachTick(() -> {
            if (told[0] < 0 && s.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.runAtTickTime(20, () -> {
            far[0] = s.distanceTo(pig);
            helper.assertTrue(told[0] < 0 && s.core().idle(), "32.5 blocks away is out of range, told at " + told[0] + " from " + far[0]);
            pig.snapTo(helper.absoluteVec(MIDDLE.add(0, 31.5, 0)));
            near[0] = s.distanceTo(pig);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(far[0] - 32.5) < 0.05 && Math.abs(near[0] - 31.5) < 0.05,
                    "the pig stood 32.5 then 31.5 blocks away, measured " + far[0] + " and " + near[0]);
            helper.assertTrue(told[0] >= 20, "31.5 blocks away is in range, told at " + told[0]);
            helper.assertTrue(told[0] <= 23, "within 3 ticks of coming in, told at " + told[0]);
        });
    }

    /**
     * The beam's reach, straight up: aimed at a pig 20 blocks up, which then rises along the locked line until its feet
     * are 31.7 blocks above the eye (the beam catches within 0.15 of a hitbox, so 31.55): the beam reaches it, 9 damage.
     */
    private static void reach(GameTestHelper helper) {
        openRoof(helper);
        SoulfireSeer s = seer(helper, MIDDLE);
        Pig pig = skyPig(helper, 20.0);
        fireproof(pig);
        s.setTarget(pig);
        boolean[] moved = {false};
        helper.onEachTick(() -> {
            if (!moved[0] && s.core().in(Step.TELL) && s.locked()) {
                feetAboveEye(helper, pig, s, 31.7);
                moved[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(moved[0] && !s.core().in(Step.TELL), "the aim locked and the beam fired, phase " + s.phase());
            helper.assertTrue(s.struck() == pig, "31.7 blocks up is within reach, struck " + s.struck() + ", beam ended at " + s.beamEnd());
            helper.assertTrue(Math.abs(pig.getHealth() - 31.0F) < 0.01F, "9 damage, health " + pig.getHealth());
        });
    }

    /**
     * The end of the beam's reach: the same, with the pig's feet 32.3 blocks above the eye (32.15 with the margin). The
     * beam ends 32 blocks out having met nothing: the pig is unhurt and the seer is snuffed.
     */
    private static void reachEnd(GameTestHelper helper) {
        openRoof(helper);
        SoulfireSeer s = seer(helper, MIDDLE);
        Pig pig = skyPig(helper, 20.0);
        s.setTarget(pig);
        boolean[] moved = {false};
        Vec3[] from = {null};
        helper.onEachTick(() -> {
            if (!moved[0] && s.core().in(Step.TELL) && s.locked()) {
                feetAboveEye(helper, pig, s, 32.3);
                from[0] = s.eye();
                moved[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(moved[0] && s.core().in(Step.SNUFFED), "the beam fired and its eye is snuffed, phase " + s.phase());
            helper.assertTrue(s.struck() == null && s.misses() == 1, "32.3 blocks up is out of reach, struck " + s.struck());
            double length = s.beamEnd().distanceTo(from[0]);
            helper.assertTrue(Math.abs(length - 32.0) < 0.05, "the beam reaches 32 blocks, it ran " + length);
            helper.assertTrue(Math.abs(pig.getHealth() - 40.0F) < 0.01F && !pig.isOnFire(), "the pig is unhurt, health " + pig.getHealth());
        });
    }

    /** Line of sight: with a wall between them it never takes aim (30 ticks); the wall gone, it aims within 3 ticks. */
    private static void sight(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        wall(helper);
        SoulfireSeer s = onPig(helper, pig);
        long[] told = {-1};
        helper.onEachTick(() -> {
            if (told[0] < 0 && s.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(told[0] < 0 && !s.hasLineOfSight(pig[0]), "behind the wall it never aims, told at " + told[0]);
            for (int z = 0; z < 12; z++) {
                for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(6, y, z), Blocks.AIR);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(told[0] >= 30 && told[0] <= 33, "in sight again it aims within 3 ticks, told at " + told[0]);
        });
    }

    /**
     * The other counter, cover while the line is drawn: a wall goes up 8 ticks into the tell. The line locks within 2
     * ticks on the spot it last saw the pig, the beam stops at the wall, the pig is unhurt and the eye is snuffed.
     */
    private static void cover(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        long[] start = {-1}, walled = {-1}, lockedAt = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (start[0] < 0 && s.core().in(Step.TELL)) start[0] = now;
            if (start[0] >= 0 && walled[0] < 0 && now >= start[0] + 8) {
                wall(helper);
                walled[0] = now;
            }
            if (lockedAt[0] < 0 && s.locked() && s.core().in(Step.TELL)) lockedAt[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(walled[0] >= 0 && s.core().in(Step.SNUFFED), "the wall went up and its eye is snuffed, phase " + s.phase());
            helper.assertTrue(lockedAt[0] - walled[0] <= 2 && lockedAt[0] >= walled[0], "the line locked as the wall went up, "
                    + (lockedAt[0] - walled[0]) + " ticks after");
            helper.assertTrue(s.struck() == null, "the beam met nothing, struck " + s.struck());
            helper.assertTrue(s.beamEnd().x < helper.absoluteVec(new Vec3(6.01, 0, 0)).x, "the wall stopped it, it ended at " + s.beamEnd());
            helper.assertTrue(Math.abs(pig[0].getHealth() - 40.0F) < 0.01F && !pig[0].isOnFire(), "the pig is unhurt, health " + pig[0].getHealth());
        });
    }

    /** The first thing it meets: a second pig standing on the line 2 blocks short of the target takes the beam; the target doesn't. */
    private static void firstHit(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        Pig near = pig(helper, new Vec3(8.5, 1, 6.5));
        fireproof(near);
        helper.succeedWhen(() -> {
            helper.assertTrue(!s.core().in(Step.TELL) && s.tells() == 1, "the beam fired, phase " + s.phase());
            helper.assertTrue(s.struck() == near, "the nearer pig took it, struck " + s.struck());
            helper.assertTrue(Math.abs(near.getHealth() - 31.0F) < 0.01F, "9 to the nearer pig, health " + near.getHealth());
            helper.assertTrue(Math.abs(pig[0].getHealth() - 40.0F) < 0.01F && !pig[0].isOnFire(), "nothing to the one behind, health " + pig[0].getHealth());
        });
    }

    /** Every 100 ticks: with the pig standing in sight, the second tell starts 100 ticks after the first. */
    private static void cooldown(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        fireproof(pig[0]);
        long[] first = {-1}, second = {-1};
        boolean[] wasIn = {false};
        helper.onEachTick(() -> {
            boolean in = s.core().in(Step.TELL);
            if (in && !wasIn[0]) {
                if (first[0] < 0) first[0] = helper.getTick();
                else if (second[0] < 0) second[0] = helper.getTick();
            }
            wasIn[0] = in;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0] >= 0, "it took aim twice, first at " + first[0] + ", cooldown " + s.cooldown());
            long gap = second[0] - first[0];
            helper.assertTrue(Math.abs(gap - 100) <= 1, "100 ticks from one tell to the next, took " + gap);
            helper.assertTrue(s.hits() == 1 && Math.abs(pig[0].getHealth() - 31.0F) < 0.01F, "the first beam struck, health " + pig[0].getHealth());
        });
    }

    /**
     * Snuffed: after a sidestep its eye stays out for 80 ticks, counted tick by tick. The pig is put back on the line and
     * given to it as a target every tick, and still it never takes aim and keeps no target; then it stands up idle.
     */
    private static void snuffTime(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        long[] lockedAt = {-1}, ended = {-1};
        int[] blind = {0};
        boolean[] aimed = {false}, kept = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (lockedAt[0] < 0 && s.core().in(Step.TELL) && s.locked()) {
                lockedAt[0] = now;
                pig[0].snapTo(helper.absoluteVec(ASIDE));
            }
            if (s.core().in(Step.SNUFFED)) {
                blind[0]++;
                if (blind[0] == 3) pig[0].snapTo(helper.absoluteVec(MARK));
                kept[0] |= blind[0] > 1 && s.getTarget() != null;
                s.setTarget(pig[0]);
            } else if (blind[0] > 0 && ended[0] < 0) {
                ended[0] = now;
            }
            aimed[0] |= blind[0] > 0 && ended[0] < 0 && s.core().in(Step.TELL);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0] >= 0, "the eye was snuffed and rekindled, " + blind[0] + " blind ticks, phase " + s.phase());
            helper.assertTrue(Math.abs(blind[0] - 80) <= 1, "blind for 80 ticks, was " + blind[0]);
            helper.assertTrue(!aimed[0], "blind, it never took aim");
            helper.assertTrue(!kept[0], "blind, it keeps no target");
            helper.assertTrue(Math.abs(pig[0].getHealth() - 40.0F) < 0.01F, "the pig is unhurt, health " + pig[0].getHealth());
        });
    }

    /**
     * A shield: a survival player on the line holding a shield up toward it. The beam reaches them and the shield takes
     * it: no damage and no fire. Something met the beam, so the eye isn't snuffed.
     */
    private static void shield(GameTestHelper helper) {
        backstop(helper);
        ServerPlayer p = TestPlayers.survivor(helper, MARK);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            p.setYRot(90.0F);            // facing -x, toward the seer
            p.setYHeadRot(90.0F);
            p.yHeadRotO = 90.0F;
        });
        SoulfireSeer s = seer(helper, AT);
        s.setTarget(p);
        float before = p.getHealth();
        long[] firedAt = {-1};
        helper.onEachTick(() -> {
            if (firedAt[0] < 0 && s.core().in(Step.FIRE)) firedAt[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(firedAt[0] >= 0 && helper.getTick() >= firedAt[0] + 2, "the beam fired, phase " + s.phase());
            helper.assertTrue(p.isBlocking(), "the player holds the shield up");
            helper.assertTrue(s.struck() == p, "the beam reached the player, struck " + s.struck());
            helper.assertTrue(p.getHealth() >= before && !p.isOnFire(), "the shield took it, health " + p.getHealth() + " of " + before);
            helper.assertTrue(s.hits() == 0 && !s.blind(), "no hit landed, and the eye is not snuffed");
        });
    }

    /** A listener that cancels the "beam_hit" step leaves the struck pig unhurt and unburnt, and the eye lit. */
    private static void hitCancel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        SoulfireSeer s = onPig(helper, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == s && e.step().equals("beam_hit")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        long[] firedAt = {-1};
        helper.onEachTick(() -> {
            if (firedAt[0] < 0 && s.core().in(Step.FIRE)) firedAt[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(firedAt[0] >= 0 && helper.getTick() >= firedAt[0] + 5, "the beam fired, phase " + s.phase());
            helper.assertTrue(s.struck() == pig[0], "the beam met the pig, struck " + s.struck());
            helper.assertTrue(Math.abs(pig[0].getHealth() - 40.0F) < 0.01F && !pig[0].isOnFire(), "a cancelled hit does nothing, pig " + pig[0].getHealth());
            helper.assertTrue(!s.blind(), "the eye is not snuffed");
        });
    }

    /** Health 22, attack 9, armour 0, a 0.6 x 2.0 hitbox; it notices targets as far off as its beam reaches. */
    private static void stats(GameTestHelper helper) {
        SoulfireSeer s = helper.spawn(ModMobs.SOULFIRE_SEER.get(), AT);
        s.removeFreeWill();
        s.core().setLook(LookDef.BASE, true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(s.getMaxHealth() - 22.0F) < 0.01F && Math.abs(s.getHealth() - 22.0F) < 0.01F,
                    "health 22, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ATTACK_DAMAGE) - 9.0) < 0.01, "attack 9");
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.FOLLOW_RANGE) - 32.0) < 0.01, "follow range 32, got "
                    + s.getAttributeValue(Attributes.FOLLOW_RANGE));
            helper.assertTrue(Math.abs(s.getBbWidth() - 0.6F) < 0.02F && Math.abs(s.getBbHeight() - 2.0F) < 0.02F,
                    "hitbox 0.6 x 2.0, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.assertTrue(s.fireImmune(), "its own soul fire never hurts it");
            helper.succeed();
        });
    }

    /** Home: the soul sand valley only, a monster, spawn weight 8 in groups of 1. The Bone Seer is its second look. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(TeemingSpawns.tag("soulfire_seer"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SOUL_SAND_VALLEY)), "its home holds the soul sand valley");
        for (var other : List.of(Biomes.WARPED_FOREST, Biomes.CRIMSON_FOREST, Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS, Biomes.PLAINS)) {
            helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("soulfire_seer");
        helper.assertTrue(row.dim() == RosterStats.Dim.NETHER, "a Nether mob");
        helper.assertTrue(ModMobs.SOULFIRE_SEER.get().getCategory() == MobCategory.MONSTER, "a monster");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/soulfire_seer")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SOULFIRE_SEER.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 8, "spawn weight 8, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("soulfire_seer");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("bone_seer")), "the Bone Seer is a look: " + looks);
        helper.succeed();
    }
}
