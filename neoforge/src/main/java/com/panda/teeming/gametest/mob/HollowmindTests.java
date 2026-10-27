package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Hollowmind;
import com.panda.teeming.mob.end.Hollowmind.Step;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Hollowmind: alone it makes for the nearest enderman within 16 blocks; beside one, a 20-tick latch tell that changes
 * nothing, then it rides the enderman's head. The ridden host turns on the parasite's target, moves 30 % faster, hits
 * 50 % harder and takes no harm from water; killing the parasite on its head frees the host, which calms down. A host
 * that dies drops the parasite with 100 ticks to climb onto another enderman within 16; after that it is on its own.
 * Players close to it on the ground are nipped: an 8-tick tell, then a bite of 4.
 *
 * <p>Every mob here has its goals cleared and a follow range of 1, so none notices players in other arenas. The test
 * endermen never teleport (a listener cancels their teleports), and a listener cancels every step of a test's parasite
 * aimed at anything outside its own arena, so it never makes for another test's enderman. Players are real survival
 * players (an enderman stays angry only at those); nothing ticks them unless the test does.
 */
public final class HollowmindTests {
    private HollowmindTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("hollowmind_tell", HollowmindTests::tell);
        out.accept("hollowmind_counter", HollowmindTests::counter);
        out.accept("hollowmind_failure", HollowmindTests::failure);
        out.accept("hollowmind_turns_nearest", HollowmindTests::turnsNearest);
        out.accept("hollowmind_host_speed", HollowmindTests::hostSpeed);
        out.accept("hollowmind_host_damage", HollowmindTests::hostDamage);
        out.accept("hollowmind_water", HollowmindTests::water);
        out.accept("hollowmind_on_head", HollowmindTests::onHead);
        out.accept("hollowmind_new_host", HollowmindTests::newHost);
        TeemingGameTests.timeLimit("hollowmind_new_host", 180);
        out.accept("hollowmind_on_its_own", HollowmindTests::onItsOwn);
        out.accept("hollowmind_save_load", HollowmindTests::saveLoad);
        TeemingGameTests.timeLimit("hollowmind_on_its_own", 200);
        TeemingGameTests.timeLimit("hollowmind_save_load", 200);
        out.accept("hollowmind_host_range", HollowmindTests::hostRange);
        out.accept("hollowmind_nip", HollowmindTests::nip);
        out.accept("hollowmind_ride_cancel", HollowmindTests::rideCancel);
        out.accept("hollowmind_stats", HollowmindTests::stats);
        out.accept("hollowmind_home", HollowmindTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /** The test endermen: an enderman listed here never teleports. */
    private static final Set<Entity> HELD = ConcurrentHashMap.newKeySet();
    private static boolean listening;

    private static Hollowmind mind(GameTestHelper helper, Vec3 at) {
        Hollowmind h = helper.spawn(ModMobs.HOLLOWMIND.get(), at);
        h.removeFreeWill();        // no goals; its own steps (the search, the latch, the nip) still run
        h.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        h.core().setLook(LookDef.BASE, true);
        // nothing outside this arena: no other test's enderman to make for, ride or turn
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == h && e.target() != null && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        TestPlayers.whenDone(helper, h::discard);
        return h;
    }

    private static Enderman enderman(GameTestHelper helper, Vec3 at) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((EntityTeleportEvent.EnderEntity e) -> {
                if (HELD.contains(e.getEntityLiving())) e.setCanceled(true);
            });
        }
        Enderman e = helper.spawn(EntityTypes.ENDERMAN, at);
        e.removeFreeWill();
        e.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        HELD.add(e);
        TestPlayers.whenDone(helper, () -> {
            HELD.remove(e);
            e.discard();
        });
        return e;
    }

    /** {@code h} climbs onto {@code e} at once, as at the end of its latch. */
    private static void mount(Hollowmind h, Enderman e) {
        h.startPhase(Step.RIDE, 0, e);
        h.startRiding(e, true, true);
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        return pig;
    }

    private static boolean near(double value, double expected) {
        return Math.abs(value - expected) < 0.01;
    }

    private static double speed(Enderman e) {
        return e.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    private static double attack(Enderman e) {
        return e.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** What one blow of {@code e} costs a fresh 40-health pig (a pig takes mob damage unscaled by difficulty). */
    private static float blow(GameTestHelper helper, Enderman e, Pig pig) {
        pig.setHealth(40.0F);
        pig.damageCooldownTime = 0;          // two blows in one tick: the second isn't eaten by the hurt cooldown
        e.doHurtTarget(helper.getLevel(), pig);
        return 40.0F - pig.getHealth();
    }

    // ---- the latch -----------------------------------------------------------------------------------------------

    /**
     * Beside an enderman it starts the latch by itself: the tell holds 20 ticks and changes nothing (no rider, no
     * boosts, no target, still hurt by water), then it is riding the enderman.
     */
    private static void tell(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(4.5, 1, 5.5));
        Enderman e = enderman(helper, new Vec3(6.0, 1, 5.5));
        int[] telling = {0};
        boolean[] early = {false}, done = {false};
        helper.onEachTick(() -> {
            if (h.core().in(Step.TELL)) {
                telling[0]++;
                early[0] |= h.isPassenger() || e.isVehicle() || e.getTarget() != null || !e.isSensitiveToWater()
                        || !near(speed(e), 0.3) || !near(attack(e), 7.0);
            } else if (telling[0] > 0) {
                done[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "the latch tell ends");
            helper.assertTrue(telling[0] == 20, "the latch tell holds 20 ticks, held " + telling[0]);
            helper.assertTrue(!early[0], "nothing changes during the tell");
            helper.assertTrue(h.host() == e && h.core().in(Step.RIDE) && h.latches() == 1, "then it rides the enderman");
        });
    }

    /**
     * Killed on the host's head, the parasite frees it: the host forgets its target and its anger and loses both boosts,
     * even when the killing swing also catches the host (a sweep). The parasite on the head is within a player's reach
     * from 1.5 blocks away, and two charged diamond-sword swings (7 each) kill its 14 health.
     */
    private static void counter(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(5.5, 1, 6.5));
        Enderman e = enderman(helper, new Vec3(5.5, 1, 6.5));
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 5.0));
        mount(h, e);
        h.setTarget(p);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(e.getTarget() == p && e.isAngryAt(p, helper.getLevel()), "the host has turned on the player");
            helper.assertTrue(p.isWithinEntityInteractionRange(h.getBoundingBox(), 0.0), "the parasite on its head is within reach");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            p.doTick();
        });
        helper.runAtTickTime(5, () -> {
            for (int i = 0; i < 20; i++) p.doTick();
            p.attack(h);
            helper.assertTrue(h.isAlive() && h.getHealth() < 14.0F, "one swing hurts it, health " + h.getHealth());
            helper.assertTrue(h.host() == e, "and it holds on");
        });
        helper.runAtTickTime(20, () -> {
            for (int i = 0; i < 20; i++) p.doTick();
            p.attack(h);
            helper.assertTrue(!h.isAlive(), "a second swing kills it, health " + h.getHealth());
            e.hurtServer(helper.getLevel(), p.damageSources().playerAttack(p), 1.0F);   // the same swing sweeps the host
        });
        helper.runAtTickTime(23, () -> {
            helper.assertTrue(h.frees() == 1 && !e.isVehicle(), "the host is free");
            helper.assertTrue(e.getTarget() == null && !e.isAngry() && e.getPersistentAngerTarget() == null && !e.isAngryAt(p, helper.getLevel()),
                    "the freed host is calm, target " + e.getTarget());
            helper.assertTrue(near(speed(e), 0.3) && near(attack(e), 7.0), "and has lost its boosts: speed " + speed(e) + ", attack " + attack(e));
            helper.succeed();
        });
    }

    /**
     * Leaving it on the host costs: the ridden enderman is angry at the player, moves at 0.585 (0.45 x 1.3) where a
     * plain enderman after the same player moves at 0.45, and hits a pig for 10.5 where the plain one hits for 7.
     */
    private static void failure(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(3.5, 1, 6.5));
        Enderman host = enderman(helper, new Vec3(3.5, 1, 6.5));
        Enderman plain = enderman(helper, new Vec3(8.5, 1, 6.5));
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 3.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 9.5));
        mount(h, host);
        h.setTarget(p);
        plain.setTarget(p);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(host.getTarget() == p && host.isAngryAt(p, helper.getLevel()) && host.getPersistentAngerTarget() != null,
                    "the host is angry at the player, as if stared at");
            helper.assertTrue(near(speed(host), 0.585) && near(speed(plain), 0.45), "speed 0.585 against 0.45, got " + speed(host) + " / " + speed(plain));
            float ridden = blow(helper, host, pig), free = blow(helper, plain, pig);
            helper.assertTrue(Math.abs(ridden - 10.5F) < 0.01F && Math.abs(free - 7.0F) < 0.01F, "blows of 10.5 against 7, got " + ridden + " / " + free);
            helper.succeed();
        });
    }

    /**
     * Nobody hands it a target: riding, it picks the nearest player it sees, and the host turns on that one (2.5 blocks
     * off) and not the farther one (5 blocks off). Its follow range here is 8, too short to reach another arena's
     * players past these two.
     */
    private static void turnsNearest(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(5.5, 1, 5.5));
        Enderman e = enderman(helper, new Vec3(5.5, 1, 5.5));
        h.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0);
        ServerPlayer near = TestPlayers.survivor(helper, new Vec3(5.5, 1, 3.0));
        ServerPlayer far = TestPlayers.survivor(helper, new Vec3(5.5, 1, 10.5));
        mount(h, e);
        helper.succeedWhen(() -> {
            helper.assertTrue(h.getTarget() == near, "the parasite picks the nearest player");
            helper.assertTrue(e.getTarget() == near && e.isAngryAt(near, helper.getLevel()) && !e.isAngryAt(far, helper.getLevel()),
                    "and the host turns on that one alone");
        });
    }

    /** The host's speed: 0.3 becomes 0.39 while it is ridden (+30 %), and is 0.3 again once the parasite is gone. */
    private static void hostSpeed(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(5.5, 1, 5.5));
        Enderman e = enderman(helper, new Vec3(5.5, 1, 5.5));
        mount(h, e);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(e.getTarget() == null && near(speed(e), 0.39), "ridden: speed 0.39, got " + speed(e));
            h.discard();
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(!e.isVehicle() && near(speed(e), 0.3), "free: speed 0.3, got " + speed(e));
            helper.succeed();
        });
    }

    /** The host's damage: 7 becomes 10.5 while ridden (+50 %), on the attribute and in a real blow; 7 again after. */
    private static void hostDamage(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(4.5, 1, 5.5));
        Enderman e = enderman(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(6.0, 1, 5.5));
        mount(h, e);
        helper.runAtTickTime(3, () -> {
            float hit = blow(helper, e, pig);
            helper.assertTrue(near(attack(e), 10.5) && Math.abs(hit - 10.5F) < 0.01F, "ridden: attack 10.5, a blow of " + hit);
            h.discard();
        });
        helper.runAtTickTime(5, () -> {
            float hit = blow(helper, e, pig);
            helper.assertTrue(near(attack(e), 7.0) && Math.abs(hit - 7.0F) < 0.01F, "free: attack 7, a blow of " + hit);
            helper.succeed();
        });
    }

    /**
     * Standing in water for 30 ticks the ridden enderman loses nothing, where a plain enderman in water is hurt; once the
     * parasite is gone the water hurts the host again.
     */
    private static void water(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(3.5, 1, 5.5));
        Enderman host = enderman(helper, new Vec3(3.5, 1, 5.5));
        Enderman plain = enderman(helper, new Vec3(8.5, 1, 5.5));
        helper.setBlock(new BlockPos(3, 1, 5), Blocks.WATER);
        helper.setBlock(new BlockPos(8, 1, 5), Blocks.WATER);
        mount(h, host);
        helper.runAtTickTime(32, () -> {
            helper.assertTrue(host.isInWater() && !host.isSensitiveToWater(), "the ridden host stands in water and doesn't mind it");
            helper.assertTrue(host.getHealth() == host.getMaxHealth(), "and loses nothing, health " + host.getHealth());
            helper.assertTrue(plain.isInWater() && plain.isSensitiveToWater() && plain.getHealth() < plain.getMaxHealth(),
                    "a plain enderman in water is hurt, health " + plain.getHealth());
            h.discard();
        });
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(host.isSensitiveToWater() && host.getHealth() < host.getMaxHealth(), "free again, the water hurts it, health " + host.getHealth());
            helper.succeed();
        });
    }

    /**
     * It rides on the host's head (2.81 above its feet), 5 pixels higher once the angry head lifts, and glows gold
     * there; the host keeps steering itself.
     */
    private static void onHead(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(5.5, 1, 6.5));
        Enderman e = enderman(helper, new Vec3(5.5, 1, 6.5));
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 3.5));
        mount(h, e);
        helper.runAtTickTime(3, () -> {
            double up = h.getY() - e.getY();
            helper.assertTrue(near(up, 2.80625), "on the calm head, 2.81 up, got " + up);
            helper.assertTrue(h.vulnerable() && e.getControllingPassenger() == null, "glowing gold, and the host steers itself");
            h.setTarget(p);
        });
        helper.runAtTickTime(6, () -> {
            double up = h.getY() - e.getY();
            helper.assertTrue(e.isCreepy() && near(up, 3.11875), "on the angry, lifted head, 3.12 up, got " + up);
            helper.succeed();
        });
    }

    // ---- after the host dies -------------------------------------------------------------------------------------

    /** Its host killed, it drops off and makes for another enderman 4.5 blocks away, starting its latch within 100 ticks. */
    private static void newHost(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(3.5, 1, 5.5));
        Enderman first = enderman(helper, new Vec3(3.5, 1, 5.5));
        Enderman second = enderman(helper, new Vec3(8.0, 1, 5.5));
        mount(h, first);
        helper.runAtTickTime(3, () -> first.kill(helper.getLevel()));
        long[] dropped = {-1}, told = {-1};
        helper.onEachTick(() -> {
            if (dropped[0] < 0 && h.core().in(Step.SEEK)) dropped[0] = helper.getTick();
            if (told[0] < 0 && dropped[0] >= 0 && h.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dropped[0] >= 0, "it drops off the dead host to search");
            helper.assertTrue(told[0] >= 0 && told[0] - dropped[0] <= 100, "it starts its latch within 100 ticks, after " + (told[0] - dropped[0]));
            helper.assertTrue(h.host() == second && near(speed(second), 0.39), "and rides the second enderman, boosted");
        });
    }

    /**
     * With no enderman near, its search lasts exactly 100 ticks; then it is on its own and never latches again, not even
     * onto an enderman right beside it.
     */
    private static void onItsOwn(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(5.5, 1, 5.5));
        Enderman first = enderman(helper, new Vec3(5.5, 1, 5.5));
        mount(h, first);
        helper.runAtTickTime(3, () -> first.kill(helper.getLevel()));
        int[] seeking = {0};
        long[] ended = {-1};
        boolean[] latched = {false};
        helper.onEachTick(() -> {
            if (h.core().in(Step.SEEK)) {
                seeking[0]++;
            } else if (seeking[0] > 0 && ended[0] < 0) {
                ended[0] = helper.getTick();
                helper.assertTrue(h.orphan(), "its time is up: it is on its own");
                Enderman late = enderman(helper, new Vec3(9.5, 1, 9.5));
                late.snapTo(h.getX() + 1.2, h.getY(), h.getZ());      // right beside it
            }
            if (ended[0] >= 0) latched[0] |= h.core().in(Step.TELL) || h.isPassenger();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0] >= 0 && helper.getTick() >= ended[0] + 40, "40 ticks after its search ended");
            helper.assertTrue(seeking[0] == 100, "the search lasts 100 ticks, lasted " + seeking[0]);
            helper.assertTrue(!latched[0], "and it never latches again");
        });
    }

    /**
     * Saved and loaded once its search ran out (as a chunk load does), it is still on its own: an enderman set right
     * beside the loaded one is never latched onto.
     */
    private static void saveLoad(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(5.5, 1, 5.5));
        Enderman first = enderman(helper, new Vec3(5.5, 1, 5.5));
        mount(h, first);
        helper.runAtTickTime(3, () -> first.kill(helper.getLevel()));
        Hollowmind[] loaded = {null};
        long[] at = {-1};
        boolean[] latched = {false};
        helper.onEachTick(() -> {
            if (loaded[0] == null && h.orphan()) {
                ServerLevel level = helper.getLevel();
                TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                h.saveWithoutId(out);
                CompoundTag tag = out.buildResult();
                h.discard();
                Hollowmind fresh = ModMobs.HOLLOWMIND.get().create(level, EntitySpawnReason.LOAD);
                helper.assertTrue(fresh != null, "a fresh entity");
                fresh.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                helper.assertTrue(fresh.orphan(), "it loads on its own");
                helper.assertTrue(level.addFreshEntity(fresh), "it joins");
                TestPlayers.whenDone(helper, fresh::discard);
                Enderman late = enderman(helper, new Vec3(9.5, 1, 9.5));
                late.snapTo(fresh.getX() + 1.2, fresh.getY(), fresh.getZ());     // right beside it
                loaded[0] = fresh;
                at[0] = helper.getTick();
            }
            if (loaded[0] != null) latched[0] |= loaded[0].core().in(Step.TELL) || loaded[0].isPassenger();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at[0] >= 0 && helper.getTick() >= at[0] + 40, "40 ticks after the reload");
            helper.assertTrue(!latched[0], "and it never latches again");
        });
    }

    /**
     * It makes for the nearest free enderman within 16 blocks: one 12.7 blocks away across the arena is chosen over a
     * nearer one that already carries a rider; 15.99 blocks is in range and 16.01 is not.
     */
    private static void hostRange(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(1.5, 1, 1.5));
        Enderman far = enderman(helper, new Vec3(10.5, 1, 10.5));
        Enderman taken = enderman(helper, new Vec3(5.5, 1, 5.5));
        Pig rider = pig(helper, new Vec3(5.5, 4, 5.5));
        rider.startRiding(taken, true, false);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(h.distanceTo(far) > 12.0 && h.findHost(helper.getLevel()) == far, "the free enderman 12.7 away is its quarry");
            helper.assertTrue(Hollowmind.inHostRange(15.99 * 15.99) && !Hollowmind.inHostRange(16.01 * 16.01), "the reach is 16 blocks");
            rider.stopRiding();
            rider.discard();
            helper.assertTrue(h.findHost(helper.getLevel()) == taken, "once free, the nearer one is chosen");
            helper.succeed();
        });
    }

    // ---- the nip -------------------------------------------------------------------------------------------------

    /** A target beside it on the ground: the nip's tell holds 8 ticks and hurts nothing, then a bite of exactly 4. */
    private static void nip(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.3, 1, 5.5));
        h.setTarget(pig);
        int[] telling = {0};
        boolean[] early = {false}, done = {false};
        helper.onEachTick(() -> {
            if (h.core().in(Step.NIP)) {
                telling[0]++;
                early[0] |= pig.getHealth() < 40.0F;
            } else if (telling[0] > 0 && h.core().in(Step.BITE)) {
                done[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "the nip's tell ends in a bite");
            helper.assertTrue(telling[0] == 8, "the tell holds 8 ticks, held " + telling[0]);
            helper.assertTrue(!early[0], "nothing lands during the tell");
            helper.assertTrue(Math.abs(pig.getHealth() - 36.0F) < 0.01F && h.bites() == 1, "the bite takes exactly 4, health " + pig.getHealth());
        });
    }

    /** A pack that cancels the climb: the tell runs out and it stays on the ground, the enderman unridden. */
    private static void rideCancel(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(4.5, 1, 5.5));
        Enderman e = enderman(helper, new Vec3(6.0, 1, 5.5));
        Consumer<MechanicEvent> veto = ev -> {
            if (ev.mob() == h && ev.step().equals("ride")) ev.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] told = {false};
        helper.onEachTick(() -> told[0] |= h.core().in(Step.TELL));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(told[0], "it ran its tell");
            helper.assertTrue(!h.isPassenger() && !e.isVehicle() && !h.core().in(Step.RIDE), "but never climbed on");
            helper.assertTrue(near(speed(e), 0.3) && near(attack(e), 7.0), "and the enderman is unchanged");
            helper.succeed();
        });
    }

    // ---- the row -------------------------------------------------------------------------------------------------

    private static void stats(GameTestHelper helper) {
        Hollowmind h = mind(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(near(h.getMaxHealth(), 14.0) && near(h.getHealth(), 14.0), "health 14, got " + h.getHealth() + "/" + h.getMaxHealth());
            helper.assertTrue(near(h.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.0), "attack 4");
            helper.assertTrue(near(h.getAttributeValue(Attributes.ARMOR), 0.0), "armour 0");
            helper.assertTrue(Math.abs(h.getBbWidth() - 0.5F) < 0.02F && Math.abs(h.getBbHeight() - 0.5F) < 0.02F,
                    "hitbox 0.5 x 0.5, got " + h.getBbWidth() + " x " + h.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: the End highlands by biome spawn, weight 4, alone. The Pale Mind is its second look; it drops rift essence. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(com.panda.teeming.spawn.TeemingSpawns.tag("hollowmind"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)), "its home holds the End highlands");
        for (var other : List.of(Biomes.END_BARRENS, Biomes.END_MIDLANDS, Biomes.SMALL_END_ISLANDS, Biomes.THE_END, Biomes.PLAINS)) {
            helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("hollowmind");
        helper.assertTrue(row.dim() == RosterStats.Dim.END, "an End mob");
        helper.assertTrue(ModMobs.HOLLOWMIND.get().getCategory() == MobCategory.MONSTER, "a monster");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/hollowmind")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.HOLLOWMIND.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("hollowmind");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("pale_mind")), "the Pale Mind is a look: " + looks);
        helper.assertTrue(row.material().equals("rift_essence") && BuiltInRegistries.ITEM.containsKey(TeemingMod.id("rift_essence")),
                "it drops rift essence, a registered item");
        helper.succeed();
    }
}
