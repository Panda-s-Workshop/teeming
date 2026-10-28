package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.ChorusHusk;
import com.panda.teeming.mob.end.ChorusHusk.Step;
import com.panda.teeming.mob.end.ChorusHusk.Teleport;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
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
 * Chorus Husk: killed, it swells exactly 20 ticks (alive at 1 health, rooted, untouchable, its pods lit, nothing moved
 * yet), then bursts: everything within 4 blocks (a pig at 3.9 yes, one at 4.2 no; a survival player; never a creative
 * one) is teleported onto safe ground at the rolled distance (3 to 8 blocks; 8 checked in the world), and the husk is
 * gone, its loot dropped to its killer. A burst that catches no player drops 2 chorus fruit; one that catches a player
 * drops none. Its blow: an 8-tick wind-up, then 8.5 damage to a target still in reach. Health 30, attack 8.5, armour 2,
 * 0.7 x 2.0, End highlands at weight 5 in groups of 1 to 2. Every teleport here has a fixed roll whose landing lies on
 * this arena's floor, and every burst is kept to this arena. Every number is checked against a plain value.
 */
public final class ChorusHuskTests {
    private ChorusHuskTests() {}

    /** The husk stands in the middle, so its 4-block burst never reaches past this arena. */
    private static final Vec3 AT = new Vec3(6.5, 1, 6.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("chorus_husk_tell", ChorusHuskTests::tell);
        out.accept("chorus_husk_counter", ChorusHuskTests::counter);
        out.accept("chorus_husk_failure", ChorusHuskTests::failure);
        out.accept("chorus_husk_radius_4", ChorusHuskTests::radius);
        out.accept("chorus_husk_teleport_8", ChorusHuskTests::teleportFar);
        out.accept("chorus_husk_teleport_range", ChorusHuskTests::teleportRange);
        out.accept("chorus_husk_spares_creative", ChorusHuskTests::sparesCreative);
        out.accept("chorus_husk_safe_ground", ChorusHuskTests::safeGround);
        out.accept("chorus_husk_no_safe_spot", ChorusHuskTests::noSafeSpot);
        out.accept("chorus_husk_loot", ChorusHuskTests::loot);
        out.accept("chorus_husk_windup", ChorusHuskTests::windup);
        out.accept("chorus_husk_strike_reach", ChorusHuskTests::strikeReach);
        out.accept("chorus_husk_swell_cancel", ChorusHuskTests::swellCancel);
        out.accept("chorus_husk_void_death", ChorusHuskTests::voidDeath);
        out.accept("chorus_husk_save_load", ChorusHuskTests::saveLoad);
        out.accept("chorus_husk_stats", ChorusHuskTests::stats);
        out.accept("chorus_husk_home", ChorusHuskTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------------

    /**
     * A husk at {@code at} with the base look, no goals, a follow range of 1 (it notices nothing past its own arena), and
     * every burst teleport of a body outside this arena vetoed. Removed when the test ends.
     */
    private static ChorusHusk husk(GameTestHelper helper, Vec3 at) {
        ChorusHusk h = helper.spawn(ModMobs.CHORUS_HUSK.get(), at);
        h.core().setLook("base", true);
        h.removeFreeWill();
        h.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == h && e.step().equals("teleport") && e.target() != null && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        TestPlayers.whenDone(helper, h::discard);
        return h;
    }

    private static ChorusHusk husk(GameTestHelper helper) {
        return husk(helper, AT);
    }

    /** A still pig with 100 health, removed when the test ends wherever a burst sent it. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static void kill(GameTestHelper helper, ChorusHusk h) {
        h.hurtServer(helper.getLevel(), h.damageSources().generic(), 1000.0F);
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** Items of {@code which} lying in this arena. */
    private static int lying(GameTestHelper helper, Item which) {
        int n = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), i -> i.isAlive() && i.getItem().is(which))) {
            n += item.getItem().getCount();
        }
        return n;
    }

    private static boolean onGround(GameTestHelper helper, Entity e) {
        BlockPos under = BlockPos.containing(e.getX(), e.getY() - 0.01, e.getZ());
        return !helper.getLevel().getBlockState(under).getCollisionShape(helper.getLevel(), under).isEmpty() && helper.getLevel().noCollision(e);
    }

    // ---- the swell, the burst, the counter -----------------------------------------------------------------------------

    /**
     * Killed, it swells exactly 20 ticks: alive at 1 health, its pods lit (glow stage 1), rooted where it stood, a blow
     * of 10 does nothing to it, and the pig 2 blocks off hasn't moved. On the tick the swell ends it bursts: the pig is
     * teleported exactly 4 blocks (a fixed roll), and the husk is gone a tick later.
     */
    private static void tell(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        Pig pig = pig(helper, 4.5, 1, 6.5);
        h.fixRoll(new Teleport(4.0, Math.PI / 2));                         // +Z, toward the arena's far side
        Vec3 pigFrom = pig.position();
        Vec3[] start = {null};
        int[] swell = {0};
        boolean[] killed = {false}, burst = {false};
        helper.onEachTick(() -> {
            if (!killed[0] && helper.getTick() >= 2) {
                kill(helper, h);
                killed[0] = true;
                start[0] = h.position();
            }
            if (!killed[0] || burst[0]) return;
            if (h.swelling()) {
                swell[0]++;
                helper.assertTrue(h.isAlive() && Math.abs(h.getHealth() - 1.0F) < 0.01F, "alive at 1 health while it swells, " + h.getHealth());
                helper.assertTrue(h.core().stage() == ChorusHusk.GLOW_SWELL, "its pods are lit, stage " + h.core().stage());
                helper.assertTrue(flat(h.position(), start[0]) < 0.01, "rooted where it stood");
                helper.assertTrue(!h.hurtServer(helper.getLevel(), h.damageSources().generic(), 10.0F), "a blow does nothing to it");
                helper.assertTrue(flat(pig.position(), pigFrom) < 0.01 && h.teleported() == 0, "nothing is moved yet");
            } else {
                burst[0] = true;
                helper.assertTrue(h.bursts() == 1, "the swell ends in the burst");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(burst[0], "it burst");
            helper.assertTrue(swell[0] == 20, "the swell lasts 20 ticks, saw " + swell[0]);
            double went = flat(pig.position(), pigFrom);
            helper.assertTrue(Math.abs(went - 4.0) < 0.01, "the pig is teleported 4 blocks, went " + went);
            helper.assertTrue(onGround(helper, pig), "onto solid ground, not into a block");
            helper.assertTrue(h.isRemoved(), "the husk is gone");
        });
    }

    /**
     * The counter: the survival player stands 5 blocks off, out of the burst. It isn't moved, and the burst, having
     * caught no player, leaves 2 chorus fruit.
     */
    private static void counter(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(6.5, 1, 1.5));
        Vec3 from = player.position();
        helper.runAtTickTime(2, () -> kill(helper, h));
        helper.succeedWhen(() -> {
            helper.assertTrue(h.bursts() == 1, "it burst");
            helper.assertTrue(flat(player.position(), from) < 0.01 && h.teleported() == 0, "the player out of reach stays put");
            helper.assertTrue(h.spared() == 1, "the burst caught no player");
            int fruit = lying(helper, Items.CHORUS_FRUIT);
            helper.assertTrue(fruit == 2, "2 chorus fruit drop, found " + fruit);
        });
    }

    /**
     * Failing it: the survival player stands 2 blocks off as it bursts and is teleported exactly 5 blocks (a fixed roll)
     * onto the floor; no fruit drops.
     */
    private static void failure(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(4.5, 1, 6.5));
        h.fixRoll(new Teleport(5.0, 0.0));                                  // +X: from x 4.5 to 9.5
        Vec3 from = player.position();
        helper.runAtTickTime(2, () -> kill(helper, h));
        helper.succeedWhen(() -> {
            helper.assertTrue(h.bursts() == 1, "it burst");
            double went = flat(player.position(), from);
            helper.assertTrue(Math.abs(went - 5.0) < 0.01, "the player is teleported 5 blocks, went " + went);
            helper.assertTrue(Math.abs(player.getY() - from.y) < 0.01, "onto the floor");
            helper.assertTrue(onGround(helper, player), "standing on ground, not in a block");
            helper.assertTrue(h.spared() == 0 && lying(helper, Items.CHORUS_FRUIT) == 0, "no fruit drops");
        });
    }

    /** The reach is 4: a pig 3.9 blocks off is teleported, one 4.2 blocks off is not. */
    private static void radius(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        Pig in = pig(helper, 6.5, 1, 2.6);
        Pig out = pig(helper, 10.7, 1, 6.5);
        h.fixRoll(new Teleport(3.0, Math.PI / 2));                          // +Z, back toward the middle
        Vec3 inFrom = in.position(), outFrom = out.position();
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(in.distanceTo(h) - 3.9) < 0.01 && Math.abs(out.distanceTo(h) - 4.2) < 0.01, "pigs at 3.9 and 4.2");
            kill(helper, h);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(h.bursts() == 1, "it burst");
            helper.assertTrue(Math.abs(flat(in.position(), inFrom) - 3.0) < 0.01, "the pig at 3.9 is teleported");
            helper.assertTrue(flat(out.position(), outFrom) < 0.01, "the pig at 4.2 stays");
            helper.assertTrue(h.teleported() == 1, "one teleported, " + h.teleported());
        });
    }

    /** The farthest roll, 8 blocks, carries a pig exactly 8 blocks onto the floor. */
    private static void teleportFar(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        Pig pig = pig(helper, 6.5, 1, 2.5);
        h.fixRoll(new Teleport(ChorusHusk.teleportDistance(1.0), Math.PI / 2));   // +Z: from z 2.5 to 10.5
        Vec3 from = pig.position();
        helper.runAtTickTime(2, () -> kill(helper, h));
        helper.succeedWhen(() -> {
            helper.assertTrue(h.bursts() == 1, "it burst");
            double went = flat(pig.position(), from);
            helper.assertTrue(Math.abs(went - 8.0) < 0.01, "8 blocks, went " + went);
            helper.assertTrue(onGround(helper, pig), "onto the floor");
        });
    }

    /** A roll carries 3 blocks at its lowest and 8 at its highest, evenly between, never past either. */
    private static void teleportRange(GameTestHelper helper) {
        helper.assertTrue(Math.abs(ChorusHusk.teleportDistance(0.0) - 3.0) < 1.0E-9, "the shortest is 3");
        helper.assertTrue(Math.abs(ChorusHusk.teleportDistance(1.0) - 8.0) < 1.0E-9, "the longest is 8");
        helper.assertTrue(Math.abs(ChorusHusk.teleportDistance(0.5) - 5.5) < 1.0E-9, "halfway is 5.5");
        helper.assertTrue(Math.abs(ChorusHusk.teleportDistance(2.0) - 8.0) < 1.0E-9, "never past 8");
        helper.assertTrue(Math.abs(ChorusHusk.teleportDistance(-1.0) - 3.0) < 1.0E-9, "never short of 3");
        helper.succeed();
    }

    /** A creative player beside it is never teleported and doesn't count as caught: the fruit still drops. */
    private static void sparesCreative(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        creative.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 6.5)));
        h.fixRoll(new Teleport(4.0, 0.0));
        Vec3 from = creative.position();
        helper.runAtTickTime(2, () -> kill(helper, h));
        helper.succeedWhen(() -> {
            helper.assertTrue(h.bursts() == 1, "it burst");
            helper.assertTrue(flat(creative.position(), from) < 0.01 && h.teleported() == 0, "the creative player stays");
            helper.assertTrue(lying(helper, Items.CHORUS_FRUIT) == 2, "and the fruit drops");
        });
    }

    /**
     * Safe ground: the pig's first direction (3 blocks toward -X) is a pit 7 deep. It lands elsewhere, still exactly
     * 3 blocks off, on the floor and clear of the pit.
     */
    private static void safeGround(GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 5; z <= 7; z++) for (int y = 0; y >= -6; y--) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        ChorusHusk h = husk(helper);
        Pig pig = pig(helper, 4.5, 1, 6.5);
        h.fixRoll(new Teleport(3.0, Math.PI));                              // -X: to x 1.5, over the pit
        Vec3 from = pig.position();
        helper.runAtTickTime(2, () -> kill(helper, h));
        helper.succeedWhen(() -> {
            helper.assertTrue(h.bursts() == 1 && h.teleported() == 1, "it burst and moved the pig");
            Vec3 r = helper.relativeVec(pig.position());
            helper.assertTrue(Math.abs(flat(pig.position(), from) - 3.0) < 0.01, "3 blocks off, went " + flat(pig.position(), from));
            helper.assertTrue(Math.abs(r.y - 1.0) < 0.01, "on the floor, y " + r.y);
            boolean inPit = r.x > -0.5 && r.x < 3.5 && r.z > 4.5 && r.z < 8.5;
            helper.assertTrue(!inPit, "clear of the pit, at " + r);
            helper.assertTrue(onGround(helper, pig), "on solid ground");
        });
    }

    /** Husk and pig on pillars 5 high, 2 apart: every spot 4 blocks out is a drop past reach, so the pig stays put. */
    private static void noSafeSpot(GameTestHelper helper) {
        for (int y = 1; y <= 5; y++) {
            helper.setBlock(new BlockPos(6, y, 5), Blocks.STONE);
            helper.setBlock(new BlockPos(6, y, 7), Blocks.STONE);
        }
        ChorusHusk h = husk(helper, new Vec3(6.5, 6, 5.5));
        h.setNoAi(true);
        Pig pig = pig(helper, 6.5, 6, 7.5);
        h.fixRoll(new Teleport(4.0, 0.0));
        Vec3 from = pig.position();
        helper.runAtTickTime(2, () -> kill(helper, h));
        helper.succeedWhen(() -> {
            helper.assertTrue(h.bursts() == 1, "it burst");
            helper.assertTrue(flat(pig.position(), from) < 0.01 && Math.abs(pig.getY() - from.y) < 0.01, "the pig stays on its pillar");
            helper.assertTrue(h.teleported() == 0, "nothing was teleported");
        });
    }

    /**
     * A survival player kills it from 6 blocks: nothing drops while it swells; after the burst the kill is the player's
     * (counted in its kill stats), its loot (chorus silk) lies in the arena, and the body was removed as killed.
     */
    private static void loot(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(6.5, 1, 0.5));
        helper.runAtTickTime(2, () -> h.hurtServer(helper.getLevel(), h.damageSources().playerAttack(player), 1000.0F));
        helper.runAtTickTime(12, () -> helper.assertTrue(lying(helper, ModMaterials.CHORUS_SILK.get()) == 0, "no loot yet while it swells"));
        helper.succeedWhen(() -> {
            helper.assertTrue(h.isRemoved() && h.getRemovalReason() == Entity.RemovalReason.KILLED, "removed as killed, " + h.getRemovalReason());
            helper.assertTrue(lying(helper, ModMaterials.CHORUS_SILK.get()) >= 1, "its chorus silk dropped");
            int kills = player.getStats().getValue(Stats.ENTITY_KILLED.get(ModMobs.CHORUS_HUSK.get()));
            helper.assertTrue(kills == 1, "the kill is the player's, " + kills);
        });
    }

    // ---- the blow ------------------------------------------------------------------------------------------------------

    /**
     * Its blow: an 8-tick wind-up, held still and harmless, then 8.5 damage to the pig in reach (armour 0 takes all 8.5).
     */
    private static void windup(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        Pig pig = pig(helper, 6.5, 1, 7.7);
        h.setTarget(pig);
        Vec3 start = h.position();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (!h.core().in(Step.WINDUP)) return;
            ticks[0]++;
            helper.assertTrue(pig.getHealth() >= 100.0F, "the wind-up hurts nothing");
            helper.assertTrue(flat(h.position(), start) < 0.05, "it holds still while it winds up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(h.strikes() == 1, "the blow landed, strikes " + h.strikes() + ", phase " + h.phase());
            helper.assertTrue(ticks[0] == 8, "the wind-up lasts 8 ticks, saw " + ticks[0]);
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 8.5F) < 0.01F, "the blow takes 8.5, the pig lost " + lost);
        });
    }

    /** Stepping back out of reach during the wind-up: the blow hits nothing. */
    private static void strikeReach(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        Pig pig = pig(helper, 6.5, 1, 7.7);
        h.setTarget(pig);
        boolean[] moved = {false}, struck = {false};
        helper.onEachTick(() -> {
            if (h.core().in(Step.WINDUP) && !moved[0]) {
                pig.snapTo(pig.getX(), pig.getY(), pig.getZ() + 3.0);
                moved[0] = true;
            }
            if (h.core().in(Step.STRIKE)) struck[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(struck[0], "it swung");
            helper.assertTrue(h.strikes() == 0 && pig.getHealth() >= 100.0F, "at the air: the pig has " + pig.getHealth());
        });
    }

    // ---- vetoes, the void, saving ---------------------------------------------------------------------------------------

    /** A vetoed "swell" is an ordinary death: no swell, no burst, the pig beside it unmoved. */
    private static void swellCancel(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        Pig pig = pig(helper, 4.5, 1, 6.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == h && e.step().equals("swell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        Vec3 from = pig.position();
        helper.runAtTickTime(2, () -> {
            kill(helper, h);
            helper.assertTrue(h.isDeadOrDying() && !h.swelling(), "it dies at once");
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(h.bursts() == 0 && flat(pig.position(), from) < 0.01, "no burst, the pig unmoved");
            helper.succeed();
        });
    }

    /** Falling out of the world kills it at once, with no swell. */
    private static void voidDeath(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        helper.runAtTickTime(2, () -> {
            h.hurtServer(helper.getLevel(), h.damageSources().fellOutOfWorld(), 1000.0F);
            helper.assertTrue(h.isDeadOrDying() && !h.swelling() && !h.dying(), "dead, not swelling");
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(h.bursts() == 0, "no burst");
            helper.succeed();
        });
    }

    /** A swell saved and loaded part-way carries on: the copy swells, bursts on time and is gone. */
    private static void saveLoad(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        h.setNoAi(true);
        ChorusHusk[] copy = {null};
        int[] savedAt = {0}, burstAt = {0};
        helper.runAtTickTime(2, () -> kill(helper, h));
        helper.runAtTickTime(8, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(h.swelling(), "swelling before the save");
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            h.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            ChorusHusk c = ModMobs.CHORUS_HUSK.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            h.discard();
            c.setNoAi(true);
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, c::discard);
            copy[0] = c;
            savedAt[0] = (int) helper.getTick();
        });
        helper.onEachTick(() -> {
            if (copy[0] != null && burstAt[0] == 0 && copy[0].bursts() == 1) burstAt[0] = (int) helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(h.bursts() == 0, "the original, removed, never burst");
            helper.assertTrue(copy[0] != null && copy[0].bursts() == 1, "the copy burst");
            int after = burstAt[0] - savedAt[0];
            helper.assertTrue(after >= 13 && after <= 16, "on time: about 14 ticks after the save at tick 6 of 20, saw " + after);
            helper.assertTrue(copy[0].isRemoved(), "and is gone");
        });
    }

    // ---- stats and home ---------------------------------------------------------------------------------------------

    /** Health 30, attack 8.5, armour 2, a 0.7 x 2.0 hitbox. */
    private static void stats(GameTestHelper helper) {
        ChorusHusk h = husk(helper);
        h.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(h.getMaxHealth() == 30.0F && h.getHealth() == 30.0F, "health 30, got " + h.getHealth() + "/" + h.getMaxHealth());
            helper.assertTrue(h.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8.5, "attack 8.5, got " + h.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(h.getArmorValue() == 2, "armour 2, got " + h.getArmorValue());
            helper.assertTrue(Math.abs(h.getBbWidth() - 0.7F) < 0.01F && Math.abs(h.getBbHeight() - 2.0F) < 0.01F,
                    "a 0.7 x 2.0 hitbox, got " + h.getBbWidth() + " x " + h.getBbHeight());
            helper.succeed();
        });
    }

    /** Home: the End highlands, never the midlands or the main island; weight 5, in groups of 1 to 2; a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("chorus_husk"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)), "its home holds the End highlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_MIDLANDS) || b.is(Biomes.THE_END)), "never the midlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/chorus_husk")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.CHORUS_HUSK.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 5, "spawn weight 5, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "in groups of 1 to 2, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.CHORUS_HUSK.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
