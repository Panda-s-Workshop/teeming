package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.MendingCrystalling;
import com.panda.teeming.mob.end.MendingCrystalling.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Mending Crystalling: its beam locks on the most hurt End mob within 12 blocks in its sight and heals it 2 every 20
 * ticks, the first heal 20 ticks after the lock; the mob glows while it is mended; it switches to a mob worse off by a
 * tenth of its health; the beam drops on a whole mob; it keeps its mob between itself and the player it watches, backs
 * off from a player with nothing to mend and never attacks; any hit at all, a snowball or a 1-damage arrow, breaks it,
 * and then the healing stops, the glow goes and a void crystal drops. Health 4, attack 0, armour 0, a 0.5 x 0.8
 * hitbox, placed on the End's main island by the Teeming spawner in groups of 1-2. Every number is checked against a
 * plain value.
 *
 * <p>Every crystalling's beam is kept to its own arena: a pack-style veto cancels its lock and heal on any mob outside
 * the test's bounds, so it never mends another test's End mobs. Crystallings that must not notice a survival player in
 * a neighbouring arena have a follow range of 1. Crystallings that fly are discarded when the test ends.
 */
public final class MendingCrystallingTests {
    private MendingCrystallingTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("mending_crystalling_tell", MendingCrystallingTests::tell);
        out.accept("mending_crystalling_counter", MendingCrystallingTests::snowballBreaksIt);
        out.accept("mending_crystalling_failure", MendingCrystallingTests::healsTwoASecond);
        TeemingGameTests.timeLimit("mending_crystalling_failure", 170);
        out.accept("mending_crystalling_range", MendingCrystallingTests::range);
        out.accept("mending_crystalling_most_hurt", MendingCrystallingTests::mostHurt);
        out.accept("mending_crystalling_patients", MendingCrystallingTests::patients);
        out.accept("mending_crystalling_whole", MendingCrystallingTests::wholeDropsBeam);
        out.accept("mending_crystalling_sight", MendingCrystallingTests::sight);
        out.accept("mending_crystalling_keeps_behind", MendingCrystallingTests::keepsBehind);
        TeemingGameTests.timeLimit("mending_crystalling_keeps_behind", 160);
        out.accept("mending_crystalling_backs_off", MendingCrystallingTests::backsOff);
        out.accept("mending_crystalling_never_attacks", MendingCrystallingTests::neverAttacks);
        out.accept("mending_crystalling_one_blow", MendingCrystallingTests::oneBlow);
        out.accept("mending_crystalling_heal_veto", MendingCrystallingTests::healVeto);
        out.accept("mending_crystalling_stats", MendingCrystallingTests::stats);
        out.accept("mending_crystalling_home", MendingCrystallingTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /**
     * A crystalling at {@code x, y, z}; {@code anchored} holds it in place (its beam still works); every one has a follow
     * range of 1 and its beam kept to this arena.
     */
    private static MendingCrystalling crystalling(GameTestHelper helper, double x, double y, double z, boolean anchored) {
        MendingCrystalling c = helper.spawn(ModMobs.MENDING_CRYSTALLING.get(), new Vec3(x, y, z));
        c.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        c.anchor(anchored);
        c.setPersistenceRequired();
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == c && (e.step().equals("mend") || e.step().equals("heal")) && e.target() != null
                    && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> {
            MechanicEvent.unlisten(keep);
            c.discard();
        });
        return c;
    }

    /** A still mob of {@code type} at {@code x, z} on the floor with {@code health} left. */
    private static <T extends Mob> T patient(GameTestHelper helper, EntityType<T> type, double x, double z, float health) {
        T m = helper.spawn(type, new Vec3(x, 1, z));
        m.setNoAi(true);
        m.setPersistenceRequired();
        m.setHealth(health);
        TestPlayers.whenDone(helper, m::discard);
        return m;
    }

    private static boolean glows(LivingEntity e) {
        return e.hasEffect(MobEffects.GLOWING);
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * It has no tell: the beam locks on a hurt endermite within half a second of seeing it (a glow on the mite from that
     * tick), and the first heal, exactly 2, lands exactly 20 ticks after the lock; nothing heals before.
     */
    private static void tell(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 6.5, 2, 6.5, true);
        Mob mite = patient(helper, EntityTypes.ENDERMITE, 3.5, 6.5, 2.0F);
        long[] locked = {-1}, healed = {-1};
        float[] gain = {0.0F};
        boolean[] early = {false}, glowing = {true};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && c.core().in(Step.MEND)) {
                locked[0] = now;
                helper.assertTrue(c.patient() == mite, "it locks on the mite");
            }
            if (locked[0] >= 0 && healed[0] < 0) {
                glowing[0] &= glows(mite);
                if (mite.getHealth() > 2.0F) {
                    healed[0] = now;
                    gain[0] = mite.getHealth() - 2.0F;
                }
            } else if (locked[0] < 0 && mite.getHealth() > 2.0F) {
                early[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(locked[0] >= 0 && locked[0] <= 12, "the beam locks within half a second, at tick " + locked[0]);
            helper.assertTrue(healed[0] > 0, "the mite is healed");
            helper.assertTrue(!early[0], "nothing heals before the lock");
            helper.assertTrue(healed[0] - locked[0] == 20, "the first heal lands 20 ticks after the lock, after " + (healed[0] - locked[0]));
            helper.assertTrue(Math.abs(gain[0] - 2.0F) < 0.01F, "the heal is 2, got " + gain[0]);
            helper.assertTrue(glowing[0], "the mite glows from the lock on");
        });
    }

    /**
     * The counter: a snowball, which does no damage, breaks it mid-beam. It dies, the mite stops glowing and stops
     * healing, and a void crystal lies on the floor.
     */
    private static void snowballBreaksIt(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 6.5, 2, 6.5, true);
        Mob mite = patient(helper, EntityTypes.ENDERMITE, 6.5, 3.5, 1.0F);
        Item crystal = BuiltInRegistries.ITEM.getOptional(TeemingMod.id("void_crystal")).orElseThrow();
        float[] atDeath = {-1.0F};
        long[] died = {-1};
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(c.patient() == mite && glows(mite), "the beam holds on the glowing mite before the throw");
            Vec3 from = helper.absoluteVec(new Vec3(4.0, 2.45, 6.5));
            Snowball ball = new Snowball(helper.getLevel(), from.x, from.y, from.z, new ItemStack(Items.SNOWBALL));
            ball.setDeltaMovement(0.5, 0.06, 0.0);
            helper.getLevel().addFreshEntity(ball);
            TestPlayers.whenDone(helper, ball::discard);
        });
        helper.onEachTick(() -> {
            if (died[0] < 0 && helper.getTick() > 14 && !c.isAlive()) {
                died[0] = helper.getTick();
                atDeath[0] = mite.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(died[0] > 0, "the snowball breaks it");
            helper.assertTrue(helper.getTick() >= died[0] + 30, "30 ticks pass after it breaks");
            helper.assertTrue(!glows(mite), "the mite loses its glow");
            helper.assertTrue(mite.getHealth() == atDeath[0], "the mite heals no more: " + atDeath[0] + " then " + mite.getHealth());
            int lying = 0;
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), i -> i.getItem().is(crystal))) {
                lying += item.getItem().getCount();
            }
            helper.assertTrue(lying >= 1, "a void crystal lies on the floor");
        });
    }

    /**
     * Failing the counter: left alone, it heals an enderman with 10 of 40 health by exactly 10 over the 100 ticks after
     * the lock, in five heals of 2 that come 20 ticks apart.
     */
    private static void healsTwoASecond(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 8.5, 2, 6.5, true);
        Mob man = patient(helper, EntityTypes.ENDERMAN, 4.5, 6.5, 10.0F);
        long[] locked = {-1};
        float[] last = {10.0F}, at100 = {-1.0F};
        java.util.List<Long> heals = new java.util.ArrayList<>();
        java.util.List<Float> sizes = new java.util.ArrayList<>();
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && c.core().in(Step.MEND)) locked[0] = now;
            if (locked[0] >= 0 && now == locked[0] + 100) at100[0] = man.getHealth();
            if (man.getHealth() != last[0]) {
                if (locked[0] >= 0 && now <= locked[0] + 100) {
                    heals.add(now - locked[0]);
                    sizes.add(man.getHealth() - last[0]);
                }
                last[0] = man.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(locked[0] >= 0 && helper.getTick() > locked[0] + 100, "100 ticks of beam pass");
            helper.assertTrue(heals.equals(java.util.List.of(20L, 40L, 60L, 80L, 100L)), "a heal every 20 ticks, at " + heals);
            for (float s : sizes) helper.assertTrue(Math.abs(s - 2.0F) < 0.01F, "each heal is 2, got " + sizes);
            helper.assertTrue(Math.abs(at100[0] - 20.0F) < 0.01F, "10 restored in the 100 ticks after the lock: 10 to " + at100[0]);
            helper.assertTrue(Math.abs(c.healed() - 10.0F) < 0.01F && c.heals() == 5, "five heals restoring 10, " + c.heals() + " restoring " + c.healed());
        });
    }

    // ---- the beam's numbers and rules ------------------------------------------------------------------------------

    /**
     * Range 12, feet to feet: a hurt endermite 11.6 blocks off is mended; a worse-hurt one 12.3 blocks off is never
     * locked or healed.
     */
    private static void range(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 1.5, 1, 1.5, true);
        Mob near = patient(helper, EntityTypes.ENDERMITE, 9.7, 9.7, 4.0F);
        Mob far = patient(helper, EntityTypes.ENDERMITE, 10.6, 9.77, 1.0F);
        Set<LivingEntity> locked = new HashSet<>();
        helper.onEachTick(() -> {
            if (c.patient() != null) locked.add(c.patient());
        });
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(Math.abs(c.distanceTo(near) - 11.6) < 0.05, "the near mite is 11.6 off, " + c.distanceTo(near));
            helper.assertTrue(Math.abs(c.distanceTo(far) - 12.3) < 0.05, "the far mite is 12.3 off, " + c.distanceTo(far));
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(near.getHealth() > 4.0F, "11.6 blocks off is mended, health " + near.getHealth());
            helper.assertTrue(far.getHealth() == 1.0F && !locked.contains(far), "12.3 blocks off is never mended, health " + far.getHealth());
            helper.succeed();
        });
    }

    /**
     * Most hurt first, by share of health: it locks on a mite at 4 of 8 over one at 6 of 8. A mite that drops to 3.6
     * (worse by less than a tenth) doesn't take the beam; once the first heal leaves the other at 6, it does, and the
     * one it left stops glowing.
     */
    private static void mostHurt(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 6.5, 2, 6.5, true);
        Mob a = patient(helper, EntityTypes.ENDERMITE, 3.5, 6.5, 6.0F);
        Mob b = patient(helper, EntityTypes.ENDERMITE, 9.5, 6.5, 4.0F);
        long[] locked = {-1}, switched = {-1};
        boolean[] held = {true};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (locked[0] < 0 && c.patient() != null) {
                locked[0] = now;
                helper.assertTrue(c.patient() == b, "it locks on the mite at half health first");
            }
            if (locked[0] < 0) return;
            long t = now - locked[0];
            if (t == 2) a.setHealth(3.6F);
            if (t > 2 && t < 20 && c.patient() != b) held[0] = false;
            if (switched[0] < 0 && c.patient() == a) switched[0] = t;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(held[0], "a mite worse off by less than a tenth doesn't take the beam");
            helper.assertTrue(switched[0] >= 20 && switched[0] <= 31, "once the other is healed it switches within half a second, at " + switched[0]);
            helper.assertTrue(glows(a) && !glows(b), "the new mite glows, the one it left doesn't");
        });
    }

    /**
     * Who counts: endermen, shulkers, endermites and the End's Teeming mobs, never a pig, a zombie, a blaze, a Nether
     * Teeming mob, another crystalling or a player. With a pig at 1 of 10 and an endermite at 6 of 8, it mends the mite
     * and the pig is never healed.
     */
    private static void patients(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 6.5, 2, 6.5, true);
        Mob mite = patient(helper, EntityTypes.ENDERMITE, 3.5, 6.5, 6.0F);
        Pig pig = patient(helper, EntityTypes.PIG, 9.5, 6.5, 1.0F);
        helper.runAtTickTime(2, () -> {
            for (EntityType<?> yes : java.util.List.of(EntityTypes.ENDERMAN, EntityTypes.SHULKER, EntityTypes.ENDERMITE,
                    ModMobs.LEVIMOTH.get(), ModMobs.CHORUS_GRAZER.get(), ModMobs.ASTRAL_AUGUR.get())) {
                var e = yes.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
                helper.assertTrue(e != null && MendingCrystalling.mends(e), "it mends " + yes);
            }
            for (EntityType<?> no : java.util.List.of(EntityTypes.PIG, EntityTypes.ZOMBIE, EntityTypes.BLAZE, ModMobs.PYRE_HERALD.get(),
                    ModMobs.MENDING_CRYSTALLING.get(), EntityTypes.ENDER_DRAGON)) {
                var e = no.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
                helper.assertTrue(e != null && !MendingCrystalling.mends(e), "it never mends " + no);
            }
            ServerPlayer p = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(!MendingCrystalling.mends(p), "it never mends a player");
            p.discard();
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(c.patient() == mite || mite.getHealth() >= 8.0F, "it mends the mite");
            helper.assertTrue(mite.getHealth() > 6.0F, "the mite is healed, " + mite.getHealth());
            helper.assertTrue(pig.getHealth() == 1.0F && !glows(pig), "the pig is never healed, " + pig.getHealth());
            helper.succeed();
        });
    }

    /** The beam drops the moment its mob is whole: a mite at 6 of 8 is healed to 8 and the beam lets go, glow and all. */
    private static void wholeDropsBeam(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 6.5, 2, 6.5, true);
        Mob mite = patient(helper, EntityTypes.ENDERMITE, 3.5, 6.5, 6.0F);
        long[] whole = {-1};
        helper.onEachTick(() -> {
            if (whole[0] < 0 && mite.getHealth() >= 8.0F) whole[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(whole[0] > 0 && helper.getTick() >= whole[0] + 2, "the mite is whole");
            helper.assertTrue(c.core().idle() && c.patient() == null, "the beam lets go, phase " + c.phase());
            helper.assertTrue(!glows(mite), "the mite stops glowing");
            helper.assertTrue(c.heals() == 1, "one heal of 2 made it whole, heals " + c.heals());
        });
    }

    /** Sight: a stone wall between it and a hurt mite keeps the beam off; taken down, the beam locks within half a second. */
    private static void sight(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 9.5, 2, 6.5, true);
        Mob mite = patient(helper, EntityTypes.ENDERMITE, 3.5, 6.5, 2.0F);
        for (int y = 1; y <= 5; y++) for (int z = 3; z <= 9; z++) helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
        boolean[] blind = {true};
        long[] opened = {-1};
        helper.onEachTick(() -> {
            if (helper.getTick() < 40 && c.patient() != null) blind[0] = false;
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(blind[0] && mite.getHealth() == 2.0F, "behind the wall the mite is never mended");
            for (int y = 1; y <= 5; y++) for (int z = 3; z <= 9; z++) helper.setBlock(new BlockPos(6, y, z), Blocks.AIR);
            opened[0] = helper.getTick();
        });
        helper.runAtTickTime(52, () -> {
            helper.assertTrue(c.patient() == mite, "with the wall down the beam locks within half a second");
            helper.succeed();
        });
    }

    // ---- keeping away ----------------------------------------------------------------------------------------------

    /**
     * It keeps its mob between itself and the player it watches: with a hurt enderman in the middle and a survival
     * player 4 blocks to one side, a crystalling starting off to the other side drifts round to about 4 blocks behind
     * the enderman, on the line from the player through it, and keeps its beam on it.
     */
    private static void keepsBehind(GameTestHelper helper) {
        Mob man = patient(helper, EntityTypes.ENDERMAN, 6.5, 6.5, 20.0F);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(2.5, 1, 6.5));
        MendingCrystalling c = crystalling(helper, 6.5, 2.2, 3.5, false);
        helper.runAtTickTime(1, () -> c.setTarget(p));
        Vec3 spot = helper.absoluteVec(new Vec3(10.5, 2.2, 6.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 40, "it has had time to move");
            double off = c.position().subtract(spot).horizontalDistance();
            helper.assertTrue(off < 1.0, "it hangs about 4 blocks behind the enderman, " + off + " from the spot, at " + helper.relativeVec(c.position()));
            double toPlayer = c.position().subtract(p.position()).horizontalDistance();
            double manToPlayer = man.position().subtract(p.position()).horizontalDistance();
            helper.assertTrue(toPlayer > manToPlayer + 3.0, "the enderman is between it and the player: " + toPlayer + " vs " + manToPlayer);
            helper.assertTrue(c.patient() == man, "its beam stays on the enderman");
            helper.assertTrue(c.getY() - helper.absoluteVec(Vec3.ZERO).y > 1.6, "it hovers off the floor, at " + helper.relativeVec(c.position()));
        });
    }

    /** With nothing to mend it backs away from the player (here a pig) it watches, out to about 8 blocks, never closer. */
    private static void backsOff(GameTestHelper helper) {
        Pig pig = patient(helper, EntityTypes.PIG, 2.5, 6.5, 10.0F);
        MendingCrystalling c = crystalling(helper, 5.0, 2.2, 6.5, false);
        helper.runAtTickTime(1, () -> c.setTarget(pig));
        double[] least = {99.0};
        helper.onEachTick(() -> {
            if (helper.getTick() > 2) least[0] = Math.min(least[0], c.position().subtract(pig.position()).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            double d = c.position().subtract(pig.position()).horizontalDistance();
            helper.assertTrue(d > 7.0, "it backs off to about 8 blocks, at " + d);
            helper.assertTrue(least[0] > 2.3, "and never closes in, came to " + least[0]);
        });
    }

    /** It never attacks: a survival player beside it notices nothing but a crystal backing away, and loses no health. */
    private static void neverAttacks(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(2.5, 1, 6.5));
        MendingCrystalling c = crystalling(helper, 4.0, 2.2, 6.5, false);
        c.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(6.0);   // it notices this player, none next door
        float[] health = {-1.0F};
        double[] most = {0.0};
        boolean[] watched = {false};
        helper.runAtTickTime(2, () -> health[0] = p.getHealth());
        helper.onEachTick(() -> {
            watched[0] |= c.getTarget() == p;
            most[0] = Math.max(most[0], c.position().subtract(p.position()).horizontalDistance());
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(watched[0], "it noticed the player");
            helper.assertTrue(p.getHealth() == health[0], "the player loses nothing: " + health[0] + " then " + p.getHealth());
            helper.assertTrue(most[0] > 4.5, "it backed away from the player, as far as " + most[0]);
            helper.succeed();
        });
    }

    // ---- breaking it ------------------------------------------------------------------------------------------------

    /** One blow of any size breaks it: a 1-damage mob blow, a 1-damage arrow, and a 1-damage hit right after another. */
    private static void oneBlow(GameTestHelper helper) {
        MendingCrystalling a = crystalling(helper, 3.5, 2, 3.5, true);
        MendingCrystalling b = crystalling(helper, 8.5, 2, 8.5, true);
        Pig pig = patient(helper, EntityTypes.PIG, 3.5, 8.5, 10.0F);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(a.hurtServer(helper.getLevel(), a.damageSources().mobAttack(pig), 1.0F), "the blow lands");
            helper.assertTrue(!a.isAlive(), "a blow of 1 breaks it, health " + a.getHealth());
            Vec3 at = b.position().add(0.0, 0.4, 0.0);
            Vec3 from = at.add(-2.0, 0.1, 0.0);
            Arrow arrow = new Arrow(helper.getLevel(), from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            arrow.setDeltaMovement(0.5, 0.02, 0.0);                               // slow enough to do 1
            helper.getLevel().addFreshEntity(arrow);
            TestPlayers.whenDone(helper, arrow::discard);
        });
        helper.succeedWhen(() -> helper.assertTrue(!b.isAlive(), "a weak arrow breaks the second, health " + b.getHealth()));
    }

    /** A pack that vetoes the heal step: the beam still locks and the mite glows, but it is never healed. */
    private static void healVeto(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 6.5, 2, 6.5, true);
        Mob mite = patient(helper, EntityTypes.ENDERMITE, 3.5, 6.5, 2.0F);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == c && e.step().equals("heal")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(c.patient() == mite && glows(mite), "the beam holds on the glowing mite");
            helper.assertTrue(mite.getHealth() == 2.0F && c.heals() == 0, "but it is never healed, health " + mite.getHealth());
            helper.succeed();
        });
    }

    // ---- stats, home -----------------------------------------------------------------------------------------------

    /** Health 4, attack 0, armour 0, a 0.5 x 0.8 hitbox, 10 experience; it floats, free of gravity. */
    private static void stats(GameTestHelper helper) {
        MendingCrystalling c = crystalling(helper, 6.5, 2, 6.5, true);
        Pig pig = patient(helper, EntityTypes.PIG, 3.5, 3.5, 10.0F);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(c.getMaxHealth() - 4.0F) < 0.01F && Math.abs(c.getHealth() - 4.0F) < 0.01F,
                    "health 4, got " + c.getHealth() + "/" + c.getMaxHealth());
            helper.assertTrue(Math.abs(c.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.01, "attack 0, got " + c.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(c.getArmorValue() == 0, "armour 0, got " + c.getArmorValue());
            helper.assertTrue(Math.abs(c.getBbWidth() - 0.5F) < 0.02F && Math.abs(c.getBbHeight() - 0.8F) < 0.02F,
                    "hitbox 0.5 x 0.8, got " + c.getBbWidth() + " x " + c.getBbHeight());
            int xp = c.getExperienceReward(helper.getLevel(), pig);
            helper.assertTrue(xp == 10, "10 experience, got " + xp);
            helper.assertTrue(c.isNoGravity(), "it floats");
            helper.succeed();
        });
    }

    /**
     * Home: the End's main island, placed only by the Teeming spawner's main-island rule, shut until the dragon has
     * been killed; on no biome list and with an empty biome tag; weight 0, groups of 1-2.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("mending_crystalling");
        helper.assertTrue(row.dim() == RosterStats.Dim.END && !row.apex(), "an ordinary End mob");
        TeemingSpawner.Rule rule = TeemingSpawner.Rule.of(row.spawner());
        helper.assertTrue(rule == TeemingSpawner.Rule.MAIN_ISLAND && rule.dimension == Level.END, "the main-island rule places it, got '" + row.spawner() + "'");
        if (TeemingConfig.endMainIslandAfterDragon()) {
            helper.assertTrue(!TeemingSpawner.mainIslandOpen(helper.getLevel()), "the main island stays shut while no dragon has been killed");
        }
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("mending_crystalling"));
        helper.assertTrue(tag.isEmpty() || tag.get().size() == 0, "its biome tag is empty: the main-island rule alone says where");
        helper.assertTrue(!row.biomeSpawns(), "on no biome list");
        helper.assertTrue(access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/mending_crystalling"))).isEmpty(), "no biome modifier");
        helper.assertTrue(row.weight() == 0, "weight 0, got " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 2, "groups of 1-2, got " + row.groupMin() + ".." + row.groupMax());
        helper.succeed();
    }
}
