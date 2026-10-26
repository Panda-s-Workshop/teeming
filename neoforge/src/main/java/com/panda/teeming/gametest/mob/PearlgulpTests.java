package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Pearlgulp;
import com.panda.teeming.mob.end.Pearlgulp.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.Set;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Pearlgulp: a real ender pearl, thrown by a survival player, is eaten as it comes within 8 blocks of the mouth (never
 * further), and its thrower stays exactly where they stood, unhurt; a pearl that never comes within 8, one behind a
 * wall, one it is barred from eating and one thrown at a full pouch all fly on and teleport their thrower (so the
 * teleport these tests stop is real). Each pearl eaten is one blink of exactly 8 blocks, no more: the closing blink
 * comes after an 8-tick throat flare (still, harmless, the pouch blazing), the escape blink when a blow leaves it under
 * half health, straight away from the attacker and never within 3 blocks of a player. The bite takes 5 after an 8-tick
 * gape. Killed, it spills the pearls it holds. Stats, home and saving against plain values.
 */
public final class PearlgulpTests {
    private PearlgulpTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("pearlgulp_tell", PearlgulpTests::tell);
        out.accept("pearlgulp_counter", PearlgulpTests::counter);
        out.accept("pearlgulp_failure", PearlgulpTests::failure);
        out.accept("pearlgulp_range_8", PearlgulpTests::range8);
        out.accept("pearlgulp_needs_sight", PearlgulpTests::needsSight);
        out.accept("pearlgulp_gulp_cancel", PearlgulpTests::gulpCancel);
        out.accept("pearlgulp_full_pouch", PearlgulpTests::fullPouch);
        out.accept("pearlgulp_one_blink_each", PearlgulpTests::oneBlinkEach);
        TeemingGameTests.timeLimit("pearlgulp_one_blink_each", 220);
        out.accept("pearlgulp_escape", PearlgulpTests::escape);
        out.accept("pearlgulp_escape_clear", PearlgulpTests::escapeClear);
        out.accept("pearlgulp_bite", PearlgulpTests::bite);
        out.accept("pearlgulp_spill", PearlgulpTests::spill);
        out.accept("pearlgulp_stats", PearlgulpTests::stats);
        out.accept("pearlgulp_save_load", PearlgulpTests::saveLoad);
        out.accept("pearlgulp_home", PearlgulpTests::home);
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------------

    /** A plain gulp with the base look facing {@code yaw}, no goals, a short follow range, removed when the test ends. */
    private static Pearlgulp gulp(GameTestHelper helper, double x, double z, float yaw) {
        Pearlgulp g = helper.spawn(ModMobs.PEARLGULP.get(), new Vec3(x, 1, z));
        g.core().setLook("base", true);
        g.removeFreeWill();
        g.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        turn(g, yaw);
        TestPlayers.whenDone(helper, () -> {
            g.setHeld(0);                                                    // test pearls are not spilled into the world
            g.discard();
        });
        return g;
    }

    /** A gulp that does nothing at all but watch for pearls (that runs without its AI). */
    private static Pearlgulp still(GameTestHelper helper, double x, double z, float yaw) {
        Pearlgulp g = gulp(helper, x, z, yaw);
        g.setNoAi(true);
        return g;
    }

    /** A still pig with 100 health, removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static void turn(LivingEntity e, float yaw) {
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.yHeadRotO = yaw;
        e.yBodyRot = yaw;
        e.yBodyRotO = yaw;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /**
     * A real ender pearl thrown by {@code owner} with {@code motion} from the owner's eyes (where vanilla's throw starts
     * it), removed when the test ends if it is still about.
     */
    private static ThrownEnderpearl throwPearl(GameTestHelper helper, ServerPlayer owner, Vec3 motion) {
        ThrownEnderpearl pearl = new ThrownEnderpearl(helper.getLevel(), owner, new ItemStack(Items.ENDER_PEARL));
        pearl.setDeltaMovement(motion);
        helper.getLevel().addFreshEntity(pearl);
        TestPlayers.whenDone(helper, pearl::discard);
        return pearl;
    }

    /**
     * Keeps the gulps of this test to this test's pearls and its pearls to its gulps (other arenas throw pearls too): a
     * pearl-eating step aimed at {@code owner} by a gulp not in {@code mine}, or by one of {@code mine} at anyone else, is vetoed.
     * Also removes any endermite a teleport leaves in the arena, so none bites a thrower.
     */
    private static void contain(GameTestHelper helper, ServerPlayer owner, Pearlgulp... mine) {
        Set<Pearlgulp> set = Set.of(mine);
        Consumer<MechanicEvent> keep = e -> {
            if (!e.step().equals("eat_pearl")) return;
            boolean ours = e.mob() instanceof Pearlgulp g && set.contains(g);
            if (ours != (e.target() == owner)) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        helper.onEachTick(() -> {
            for (Endermite m : helper.getLevel().getEntitiesOfClass(Endermite.class, helper.getBounds().inflate(2.0))) m.discard();
        });
    }

    // ---- the gulp ----------------------------------------------------------------------------------------------------

    /**
     * The counter, from the gulp's side: a survival player 8.7 blocks from its mouth throws a real pearl at it. The pearl
     * is eaten the moment it comes within 8 (between 8 and 6.4 blocks out: a pearl moves under 1.6 a tick), and the
     * thrower stays exactly where they stood, unhurt (a pearl's teleport costs 5), long after the pearl would have landed.
     */
    private static void counter(GameTestHelper helper) {
        Pearlgulp g = still(helper, 3.5, 3.5, -45.0F);                    // facing +X +Z, toward the thrower
        ServerPlayer owner = TestPlayers.survivor(helper, new Vec3(10.0, 1, 10.0));
        contain(helper, owner, g);
        Vec3 stood = owner.position();
        double[] start = {0};
        ThrownEnderpearl[] pearl = {null};
        helper.runAtTickTime(3, () -> {
            start[0] = owner.getEyePosition().distanceTo(g.mouth());
            double s = 0.5 / Math.sqrt(2.0);
            pearl[0] = throwPearl(helper, owner, new Vec3(-s, 0.1, -s));
        });
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(start[0] > 8.3, "the pearl starts out of reach, " + start[0] + " from the mouth");
            helper.assertTrue(g.eaten() == 1 && g.held() == 1, "the pearl was eaten, eaten " + g.eaten() + ", held " + g.held());
            helper.assertTrue(pearl[0].isRemoved(), "the pearl is gone");
            double d = g.lastGulpDistance();
            helper.assertTrue(d <= 8.0 && d > 6.4, "snapped up as it came within 8, at " + d);
            helper.assertTrue(flat(owner.position(), stood) < 0.01 && Math.abs(owner.getY() - stood.y) < 0.01,
                    "the thrower stays put, at " + helper.relativeVec(owner.position()));
            helper.assertTrue(owner.getHealth() >= 20.0F, "and is not hurt by a teleport, health " + owner.getHealth());
            helper.assertTrue(g.core().stage() == 1, "its pouch glows for one pearl, stage " + g.core().stage());
            helper.succeed();
        });
    }

    /**
     * A pearl that passes no closer than 8.5 blocks from the mouth is not eaten: it lands and teleports its thrower, who
     * takes the teleport's 5. This is the teleport the other tests prove is stopped.
     */
    private static void range8(GameTestHelper helper) {
        Pearlgulp g = still(helper, 2.5, 2.5, -90.0F);                    // facing +X, along the pearl's path
        ServerPlayer owner = TestPlayers.survivor(helper, new Vec3(2.5, 1, 11.0));
        contain(helper, owner, g);
        Vec3 stood = owner.position();
        double[] closest = {Double.MAX_VALUE};
        float[] health = {-1};
        ThrownEnderpearl[] pearl = {null};
        helper.runAtTickTime(3, () -> pearl[0] = throwPearl(helper, owner, new Vec3(0.45, 0.1, 0.0)));
        helper.onEachTick(() -> {
            if (pearl[0] != null && !pearl[0].isRemoved()) closest[0] = Math.min(closest[0], pearl[0].position().distanceTo(g.mouth()));
            if (health[0] < 0 && flat(owner.position(), stood) > 1.0) health[0] = owner.getHealth();
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(closest[0] > 8.0 && closest[0] < 9.0, "the pearl passed just over 8 from the mouth, " + closest[0]);
            helper.assertTrue(g.eaten() == 0 && g.held() == 0, "so it was not eaten, eaten " + g.eaten());
            helper.assertTrue(flat(owner.position(), stood) > 4.0, "it landed and teleported its thrower, moved " + flat(owner.position(), stood));
            helper.assertTrue(Math.abs(health[0] - 15.0F) < 0.01F, "who took the teleport's 5, health " + health[0]);
            helper.succeed();
        });
    }

    /** A wall between its mouth and a pearl well within 8 hides the pearl: it flies on and teleports its thrower. */
    private static void needsSight(GameTestHelper helper) {
        for (int z = 3; z <= 10; z++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        Pearlgulp g = still(helper, 2.5, 6.5, -90.0F);
        ServerPlayer owner = TestPlayers.survivor(helper, new Vec3(6.5, 1, 9.5));
        contain(helper, owner, g);
        Vec3 stood = owner.position();
        double[] closest = {Double.MAX_VALUE};
        ThrownEnderpearl[] pearl = {null};
        helper.runAtTickTime(3, () -> pearl[0] = throwPearl(helper, owner, new Vec3(0.0, 0.1, -0.5)));
        helper.onEachTick(() -> {
            if (pearl[0] != null && !pearl[0].isRemoved()) closest[0] = Math.min(closest[0], pearl[0].position().distanceTo(g.mouth()));
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(closest[0] < 5.0, "the pearl came well within reach, " + closest[0]);
            helper.assertTrue(g.eaten() == 0, "but behind the wall it was not eaten");
            helper.assertTrue(flat(owner.position(), stood) > 4.0, "it teleported its thrower, moved " + flat(owner.position(), stood));
            helper.succeed();
        });
    }

    /** A vetoed "eat_pearl" leaves the pearl alone: it flies on and teleports its thrower. */
    private static void gulpCancel(GameTestHelper helper) {
        Pearlgulp g = still(helper, 2.5, 6.5, -90.0F);
        ServerPlayer owner = TestPlayers.survivor(helper, new Vec3(5.5, 1, 9.5));
        Consumer<MechanicEvent> veto = e -> {
            if (e.step().equals("eat_pearl") && (e.mob() == g || e.target() == owner)) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        contain(helper, owner, g);
        Vec3 stood = owner.position();
        helper.runAtTickTime(3, () -> throwPearl(helper, owner, new Vec3(0.0, 0.1, -0.45)));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(g.eaten() == 0 && g.held() == 0, "vetoed, nothing was eaten");
            helper.assertTrue(flat(owner.position(), stood) > 3.0, "the pearl teleported its thrower, moved " + flat(owner.position(), stood));
            helper.succeed();
        });
    }

    /** With a full pouch (16) it eats no more: the pearl flies on and teleports its thrower. */
    private static void fullPouch(GameTestHelper helper) {
        Pearlgulp g = still(helper, 2.5, 6.5, -90.0F);
        g.setHeld(16);
        ServerPlayer owner = TestPlayers.survivor(helper, new Vec3(5.5, 1, 9.5));
        contain(helper, owner, g);
        Vec3 stood = owner.position();
        helper.runAtTickTime(3, () -> throwPearl(helper, owner, new Vec3(0.0, 0.1, -0.45)));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(g.eaten() == 0 && g.held() == 16, "a full pouch takes nothing, held " + g.held());
            helper.assertTrue(flat(owner.position(), stood) > 3.0, "the pearl teleported its thrower, moved " + flat(owner.position(), stood));
            helper.succeed();
        });
    }

    // ---- the blink ---------------------------------------------------------------------------------------------------

    /**
     * The tell: holding one pearl, with its target 9.4 blocks off, it flares for exactly 8 ticks: it holds still, its
     * pouch blazes (glow stage 4), the pearl is not yet spent and the target is unhurt. Then it blinks exactly 8 blocks
     * straight toward the target, onto the floor, and the pearl is spent (stage 0).
     */
    private static void tell(GameTestHelper helper) {
        Pearlgulp g = gulp(helper, 1.5, 6.5, -90.0F);
        Pig pig = pig(helper, 10.9, 6.5);
        g.setHeld(1);
        g.setTarget(pig);
        int[] flare = {0};
        Vec3[] start = {null};
        boolean[] landed = {false};
        helper.onEachTick(() -> {
            if (g.core().in(Step.TELL)) {
                if (start[0] == null) start[0] = g.position();
                flare[0]++;
                helper.assertTrue(flat(g.position(), start[0]) < 0.01, "it holds still while it flares");
                helper.assertTrue(g.held() == 1, "the pearl is not spent yet");
                helper.assertTrue(g.core().stage() == Pearlgulp.GLOW_FLARE, "its pouch blazes, stage " + g.core().stage());
                helper.assertTrue(pig.getHealth() >= 100.0F, "the flare hurts nothing");
            } else if (flare[0] > 0 && !landed[0]) {
                landed[0] = true;
                helper.assertTrue(g.core().in(Step.BLINK), "the flare ends in the blink, phase " + g.phase());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(landed[0], "it flared and blinked");
            helper.assertTrue(flare[0] == 8, "the flare lasts 8 ticks, saw " + flare[0]);
            double went = flat(g.position(), start[0]);
            helper.assertTrue(Math.abs(went - 8.0) < 0.01, "the blink is 8 blocks, went " + went);
            helper.assertTrue(Math.abs(g.getZ() - start[0].z) < 0.01 && g.getX() > start[0].x, "straight toward the pig");
            helper.assertTrue(Math.abs(g.getY() - start[0].y) < 0.01, "onto the floor");
            helper.assertTrue(g.held() == 0 && g.blinks() == 1, "one pearl spent, held " + g.held());
            helper.assertTrue(g.core().stage() == 0, "its pouch is dark again, stage " + g.core().stage());
        });
    }

    /**
     * Failing the counter: a survival player throws a pearl near it. The pearl is eaten and the thrower stays put; with
     * the thrower 9.4 blocks off as its target, it flares and blinks exactly 8 blocks onto them, spending the pearl.
     */
    private static void failure(GameTestHelper helper) {
        Pearlgulp g = gulp(helper, 1.5, 6.5, -90.0F);
        ServerPlayer owner = TestPlayers.survivor(helper, new Vec3(10.9, 1, 6.5));
        contain(helper, owner, g);
        g.setTarget(owner);
        Vec3 stood = owner.position();
        Vec3[] start = {null};
        float[] healthAtBlink = {-1};
        helper.runAtTickTime(2, () -> {
            start[0] = g.position();
            throwPearl(helper, owner, new Vec3(-0.5, 0.1, 0.0));
        });
        helper.onEachTick(() -> {
            if (g.blinks() == 1 && healthAtBlink[0] < 0) healthAtBlink[0] = owner.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(g.eaten() == 1, "the pearl was eaten");
            helper.assertTrue(g.blinks() == 1, "and spent on a blink at the thrower, blinks " + g.blinks());
            helper.assertTrue(g.held() == 0, "the pearl is gone for good, held " + g.held());
            double went = flat(g.position(), start[0]);
            helper.assertTrue(Math.abs(went - 8.0) < 0.01, "8 blocks, went " + went);
            helper.assertTrue(flat(g.position(), owner.position()) < 2.0, "it lands beside the thrower, " + flat(g.position(), owner.position()));
            helper.assertTrue(flat(owner.position(), stood) < 0.01, "who never moved");
            helper.assertTrue(healthAtBlink[0] >= 20.0F, "nor was hurt by a teleport, health " + healthAtBlink[0]);
        });
    }

    /**
     * Each pearl is one blink: two real pearls eaten make two blinks of 8 (the pig is moved far off again after each),
     * then no third, though the pig stands far off again and the blink is ready.
     */
    private static void oneBlinkEach(GameTestHelper helper) {
        Pearlgulp g = gulp(helper, 1.5, 6.5, -90.0F);
        Pig pig = pig(helper, 10.9, 6.5);
        ServerPlayer owner = TestPlayers.survivor(helper, new Vec3(5.5, 1, 1.5));
        contain(helper, owner, g);
        g.setTarget(pig);
        helper.runAtTickTime(2, () -> throwPearl(helper, owner, new Vec3(0.0, 0.1, 0.3)));
        helper.runAtTickTime(4, () -> throwPearl(helper, owner, new Vec3(-0.1, 0.1, 0.3)));
        Vec3[] from = {null};
        int[] seen = {0};
        int[] lastBlink = {-1};
        helper.onEachTick(() -> {
            if (from[0] == null) from[0] = g.position();
            if (g.blinks() > seen[0]) {
                seen[0] = g.blinks();
                double went = flat(g.position(), from[0]);
                helper.assertTrue(Math.abs(went - 8.0) < 0.01, "blink " + seen[0] + " is 8 blocks, went " + went);
                from[0] = g.position();
                lastBlink[0] = (int) helper.getTick();
                // the pig steps far off again, so the gulp would blink once more if it could
                Vec3 far = helper.relativeVec(g.position()).x > 6.0 ? new Vec3(1.0, 1, 6.5) : new Vec3(10.9, 1, 1.5);
                Vec3 abs = helper.absoluteVec(far);
                pig.snapTo(abs.x, abs.y, abs.z);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(g.eaten() == 2, "two pearls eaten, " + g.eaten());
            helper.assertTrue(seen[0] == 2 && lastBlink[0] > 0, "two blinks, " + seen[0]);
            helper.assertTrue(helper.getTick() >= lastBlink[0] + 80, "waiting past a full blink cooldown, tick " + helper.getTick());
            helper.assertTrue(g.blinks() == 2 && g.held() == 0, "and no third: blinks " + g.blinks() + ", held " + g.held());
            helper.assertTrue(g.distanceTo(pig) > 6.0, "though its target is far off");
        });
    }

    /**
     * The escape: a blow of 4 leaves it over half health and it stays; a blow that leaves it under half (11 of 22) sends
     * it blinking exactly 8 blocks straight away from the pig that struck it, spending its pearl.
     */
    private static void escape(GameTestHelper helper) {
        Pearlgulp g = gulp(helper, 2.5, 6.5, -90.0F);
        g.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);   // the blows don't shove it off its spot
        Pig pig = pig(helper, 1.2, 6.5);
        g.setHeld(1);
        Vec3[] start = {null};
        helper.runAtTickTime(2, () -> {
            start[0] = g.position();
            g.hurtServer(helper.getLevel(), g.damageSources().mobAttack(pig), 4.0F);
            helper.assertTrue(g.getHealth() > 11.0F, "still over half, health " + g.getHealth());
            helper.assertTrue(g.escapes() == 0 && flat(g.position(), start[0]) < 0.01, "so it stays");
        });
        helper.runAtTickTime(20, () -> {
            start[0] = g.position();
            g.hurtServer(helper.getLevel(), g.damageSources().mobAttack(pig), 12.0F);
            helper.assertTrue(g.getHealth() < 11.0F, "under half, health " + g.getHealth());
            helper.assertTrue(g.escapes() == 1, "it blinks away at once");
            double went = flat(g.position(), start[0]);
            helper.assertTrue(Math.abs(went - 8.0) < 0.01, "8 blocks, went " + went);
            helper.assertTrue(Math.abs(g.getZ() - start[0].z) < 0.01 && g.getX() > start[0].x, "straight away from the pig");
            helper.assertTrue(g.held() == 0, "spending its pearl, held " + g.held());
            helper.succeed();
        });
    }

    /** An escape never lands within 3 blocks of a player: with a survivor on the straight-away spot it lands elsewhere. */
    private static void escapeClear(GameTestHelper helper) {
        Pearlgulp g = gulp(helper, 2.5, 6.5, -90.0F);
        g.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        Pig pig = pig(helper, 1.2, 6.5);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(10.5, 1, 6.5));
        g.setHeld(1);
        Vec3[] start = {null};
        helper.runAtTickTime(2, () -> {
            start[0] = g.position();
            g.hurtServer(helper.getLevel(), g.damageSources().mobAttack(pig), 14.0F);
            helper.assertTrue(g.escapes() == 1, "it blinks away");
            double went = flat(g.position(), start[0]);
            helper.assertTrue(Math.abs(went - 8.0) < 0.01, "8 blocks, went " + went);
            double gap = g.position().distanceTo(player.position());
            helper.assertTrue(gap >= 3.0, "at least 3 blocks from the player, " + gap);
            helper.succeed();
        });
    }

    // ---- the bite ----------------------------------------------------------------------------------------------------

    /** The gape lasts exactly 8 ticks and hurts nothing; then the bite takes 5. */
    private static void bite(GameTestHelper helper) {
        Pearlgulp g = gulp(helper, 4.5, 6.5, -90.0F);
        Pig pig = pig(helper, 6.0, 6.5);
        g.setTarget(pig);
        int[] gape = {0};
        float[] after = {-1};
        helper.onEachTick(() -> {
            if (g.core().in(Step.GAPE)) {
                gape[0]++;
                helper.assertTrue(pig.getHealth() >= 100.0F, "the gape hurts nothing yet");
            } else if (gape[0] > 0 && after[0] < 0) {
                after[0] = pig.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(after[0] >= 0, "it gaped and bit");
            helper.assertTrue(gape[0] == 8, "the gape lasts 8 ticks, saw " + gape[0]);
            float lost = 100.0F - after[0];
            helper.assertTrue(Math.abs(lost - 5.0F) < 0.01F, "the bite takes 5, the pig lost " + lost);
        });
    }

    // ---- the payoff ----------------------------------------------------------------------------------------------------

    /** Holding 3 pearls it never despawns; killed, it spills exactly 3 ender pearls. */
    private static void spill(GameTestHelper helper) {
        Pearlgulp g = still(helper, 6.5, 6.5, 0.0F);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!g.requiresCustomPersistence(), "empty, it may despawn");
            g.setHeld(3);
            helper.assertTrue(g.requiresCustomPersistence(), "holding pearls, it never despawns");
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds())) item.discard();
            g.kill(helper.getLevel());
        });
        helper.runAtTickTime(6, () -> {
            int pearls = 0;
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0), i -> i.getItem().is(Items.ENDER_PEARL))) {
                pearls += item.getItem().getCount();
            }
            helper.assertTrue(pearls == 3, "it spills its 3 pearls, found " + pearls);
            helper.succeed();
        });
    }

    // ---- stats, saving, home -------------------------------------------------------------------------------------------

    /** Health 22, attack 5, armour 2, a 0.9 x 0.8 hitbox. */
    private static void stats(GameTestHelper helper) {
        Pearlgulp g = still(helper, 5.5, 5.5, 0.0F);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(g.getMaxHealth() == 22.0F && g.getHealth() == 22.0F, "health 22, got " + g.getHealth() + "/" + g.getMaxHealth());
            helper.assertTrue(g.getAttributeValue(Attributes.ATTACK_DAMAGE) == 5.0, "attack 5, got " + g.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(g.getArmorValue() == 2, "armour 2, got " + g.getArmorValue());
            helper.assertTrue(Math.abs(g.getBbWidth() - 0.9F) < 0.01F && Math.abs(g.getBbHeight() - 0.8F) < 0.01F,
                    "a 0.9 x 0.8 hitbox, got " + g.getBbWidth() + " x " + g.getBbHeight());
            helper.succeed();
        });
    }

    /** The pearls it holds and the blink cooldown survive a save and reload. */
    private static void saveLoad(GameTestHelper helper) {
        Pearlgulp g = gulp(helper, 1.5, 6.5, -90.0F);
        Pig pig = pig(helper, 10.9, 6.5);
        g.setHeld(3);
        g.setTarget(pig);
        Pearlgulp[] copy = {null};
        int[] cooldown = {0};
        helper.onEachTick(() -> {
            if (copy[0] != null || g.blinks() < 1) return;
            ServerLevel level = helper.getLevel();
            cooldown[0] = g.blinkCooldown();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            g.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            Pearlgulp c = ModMobs.PEARLGULP.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            g.setHeld(0);                                                    // so the original spills nothing as it goes
            g.discard();
            c.setNoAi(true);
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, c::discard);
            copy[0] = c;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(copy[0] != null, "saved and reloaded after a blink");
            Pearlgulp c = copy[0];
            helper.assertTrue(c.held() == 2, "two pearls left after one blink, held " + c.held());
            helper.assertTrue(c.core().stage() == 2, "and its pouch shows them, stage " + c.core().stage());
            helper.assertTrue(cooldown[0] > 0 && Math.abs(c.blinkCooldown() - cooldown[0]) <= 2, "its blink cooldown carries over: " + cooldown[0] + " then " + c.blinkCooldown());
        });
    }

    /**
     * Home: the End highlands and the small End islands, never the midlands or the main island; spawn weight 4, alone
     * (groups of 1); a monster.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("pearlgulp"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)), "its home holds the End highlands");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SMALL_END_ISLANDS)), "and the small End islands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_MIDLANDS) || b.is(Biomes.THE_END)), "never the midlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/pearlgulp")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.PEARLGULP.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.PEARLGULP.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
