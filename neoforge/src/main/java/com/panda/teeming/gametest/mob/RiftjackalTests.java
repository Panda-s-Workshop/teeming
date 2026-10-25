package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Riftjackal;
import com.panda.teeming.mob.end.Riftjackal.Blink;
import com.panda.teeming.mob.end.Riftjackal.SafeTeleport;
import com.panda.teeming.mob.end.Riftjackal.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Riftjackal: an 8-tick crouch (still, harmless, the landing picked at its target's side or back, 2 to 4 blocks out),
 * then the blink; a bite of 6 if the target is in reach; a rift left where it vanished, open exactly 60 ticks, that
 * throws a mob or a survival player exactly 8 blocks onto safe ground and never a Riftjackal; each jackal blinks every
 * 60 ticks and a pack takes turns, a crouch at most every 20 ticks; the counter (the target facing the landing) throws
 * the jackal 8 blocks away with no bite and no rift. The safe-teleport rule is checked in the world (lava, fire, a pit,
 * one block of headroom, nowhere safe). Every number is checked against a plain value.
 */
public final class RiftjackalTests {
    private RiftjackalTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("riftjackal_tell", RiftjackalTests::tell);
        out.accept("riftjackal_counter", RiftjackalTests::counter);
        out.accept("riftjackal_failure", RiftjackalTests::failure);
        out.accept("riftjackal_reach", RiftjackalTests::reach);
        out.accept("riftjackal_every_60", RiftjackalTests::every60);
        TeemingGameTests.timeLimit("riftjackal_every_60", 200);
        out.accept("riftjackal_pack_turns", RiftjackalTests::packTurns);
        TeemingGameTests.timeLimit("riftjackal_pack_turns", 220);
        out.accept("riftjackal_rift_length", RiftjackalTests::riftLength);
        out.accept("riftjackal_safe_lava", h -> safeAround(h, "lava"));
        out.accept("riftjackal_rift_throws", RiftjackalTests::riftThrows);
        out.accept("riftjackal_safe_fire", h -> safeAround(h, "fire"));
        out.accept("riftjackal_rift_spares_jackals", RiftjackalTests::riftSparesJackals);
        out.accept("riftjackal_safe_pit", h -> safeAround(h, "pit"));
        out.accept("riftjackal_rift_closes", RiftjackalTests::riftCloses);
        out.accept("riftjackal_safe_headroom", h -> safeAround(h, "headroom"));
        out.accept("riftjackal_no_safe_spot", RiftjackalTests::noSafeSpot);
        out.accept("riftjackal_blink_cancel", RiftjackalTests::blinkCancel);
        out.accept("riftjackal_stats", RiftjackalTests::stats);
        out.accept("riftjackal_alpha", RiftjackalTests::alphaStats);
        out.accept("riftjackal_pack_spawn", RiftjackalTests::packSpawn);
        out.accept("riftjackal_save_load", RiftjackalTests::saveLoad);
        out.accept("riftjackal_home", RiftjackalTests::home);
    }

    /**
     * A plain jackal with the base look and a pack of its own, no goals (it stays where the blinks put it; its hunt
     * still runs), a short follow range, removed when the test ends wherever it went.
     */
    private static Riftjackal jackal(GameTestHelper helper, double x, double y, double z) {
        Riftjackal j = helper.spawn(ModMobs.RIFTJACKAL.get(), new Vec3(x, y, z));
        j.core().setLook("base", true);
        j.joinPack(UUID.randomUUID(), false);
        j.removeFreeWill();
        j.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        TestPlayers.whenDone(helper, j::discard);
        return j;
    }

    /** A jackal that does nothing at all, for the rift tests: its rifts still work. */
    private static Riftjackal still(GameTestHelper helper, double x, double y, double z) {
        Riftjackal j = jackal(helper, x, y, z);
        j.setNoAi(true);
        return j;
    }

    /** A still pig with 100 health facing {@code yaw}, removed when the test ends wherever a rift sent it. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z, float yaw) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        turn(pig, yaw);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static void turn(LivingEntity e, float yaw) {
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.yHeadRotO = yaw;
        e.yBodyRot = yaw;
    }

    /** Turns {@code e} to look straight at {@code at}. */
    private static void lookAt(LivingEntity e, Vec3 at) {
        turn(e, (float) (Mth.atan2(at.z - e.getZ(), at.x - e.getX()) * Mth.RAD_TO_DEG) - 90.0F);
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** A plateau of stone filling the arena 5 high, so ground off it (outside the arena too) is a drop past reach. */
    private static void plateau(GameTestHelper helper) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }

    /** True for a spot (absolute) whose hitbox lies inside this test's arena. */
    private static Predicate<Vec3> inArena(GameTestHelper helper) {
        return p -> {
            Vec3 r = helper.relativeVec(p);
            return r.x > 0.5 && r.x < 11.5 && r.z > 0.5 && r.z < 11.5;
        };
    }

    // ---- the crouch, the blink and the bite ------------------------------------------------------------------------

    /**
     * The crouch lasts exactly 8 ticks: the jackal holds still, its rift light flared (glow stage 1), hurts nothing and
     * opens no rift. Its landing is picked as the crouch starts, at the pig's side or back (outside the 60 degrees the
     * pig sees), 2 to 4 blocks from it. Then it is there, facing the pig, and its rift lies where it crouched.
     */
    private static void tell(GameTestHelper helper) {
        Riftjackal j = jackal(helper, 2.5, 1, 6.5);
        Pig pig = pig(helper, 7.5, 1, 6.5, 0.0F);
        j.setTarget(pig);
        int[] crouch = {0};
        Vec3[] start = {null}, spot = {null};
        boolean[] landed = {false};
        helper.onEachTick(() -> {
            if (j.core().in(Step.TELL)) {
                if (start[0] == null) {
                    start[0] = j.position();
                    spot[0] = j.landing();
                }
                crouch[0]++;
                helper.assertTrue(flat(j.position(), start[0]) < 0.01, "it holds still while it crouches");
                helper.assertTrue(pig.getHealth() >= 100.0F, "the crouch hurts nothing yet");
                helper.assertTrue(j.rifts().isEmpty(), "and opens no rift yet");
                helper.assertTrue(j.core().stage() == Riftjackal.GLOW_FLARE, "its rift light flares, stage " + j.core().stage());
            } else if (crouch[0] > 0 && !landed[0]) {
                landed[0] = true;
                helper.assertTrue(j.core().in(Step.LAND), "the crouch ends in the landing, phase " + j.phase());
                helper.assertTrue(j.core().stage() == Riftjackal.GLOW_REST, "its light settles");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(landed[0], "it crouched and blinked");
            helper.assertTrue(crouch[0] == 8, "the crouch lasts 8 ticks, saw " + crouch[0]);
            helper.assertTrue(spot[0] != null, "a landing was picked as the crouch started");
            double out = flat(spot[0], pig.position());
            helper.assertTrue(out > 1.99 && out < 4.01, "2 to 4 blocks from the pig, got " + out);
            helper.assertTrue(!Blink.sees(0.0F, spot[0].x - pig.getX(), spot[0].z - pig.getZ()), "at its side or back, out of its sight");
            double angle = Math.toDegrees(Math.acos(((spot[0].z - pig.getZ())) / out));     // from the pig's facing, +Z
            helper.assertTrue(angle > 89.0, "90 degrees or more from where the pig faces, got " + angle);
            helper.assertTrue(flat(j.position(), spot[0]) < 0.01, "it is where it picked");
            Vec3 toPig = pig.position().subtract(j.position());
            float want = (float) (Mth.atan2(toPig.z, toPig.x) * Mth.RAD_TO_DEG) - 90.0F;
            helper.assertTrue(Math.abs(Mth.wrapDegrees(j.getYRot() - want)) < 1.0F, "facing the pig, yaw " + j.getYRot() + " want " + want);
            helper.assertTrue(j.rifts().size() == 1 && flat(j.rifts().getFirst(), start[0]) < 0.01, "its rift lies where it crouched");
        });
    }

    /**
     * The counter: the pig turns to face the landing while the jackal crouches. The jackal lands seen and is thrown
     * back through its own rift: exactly 8 blocks from where it landed, straight away from the pig; the pig is unhurt
     * and no rift is left. Both side spots are walled off so it must land behind the pig, and the throw stays inside
     * the arena.
     */
    private static void counter(GameTestHelper helper) {
        // the pig faces the arena's corner (yaw 135); its sides are walled, so the landing is behind it, toward the middle
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 2, 3), Blocks.STONE);
        Pig pig = pig(helper, 2.5, 1, 2.5, 135.0F);
        Riftjackal j = jackal(helper, 8.5, 1, 8.5);
        j.setTarget(pig);
        Vec3[] spot = {null};
        boolean[] turned = {false};
        helper.onEachTick(() -> {
            if (j.core().in(Step.TELL) && !turned[0]) {
                spot[0] = j.landing();
                helper.assertTrue(spot[0] != null, "a landing is picked");
                lookAt(pig, spot[0]);
                turned[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(j.snaps() == 1, "seen as it lands, it is thrown back, snaps " + j.snaps() + ", phase " + j.phase());
            helper.assertTrue(j.core().in(Step.FLUNG), "and tumbles, phase " + j.phase());
            Vec3 behind = helper.absoluteVec(new Vec3(2.5 + Math.sqrt(2), 1, 2.5 + Math.sqrt(2)));
            helper.assertTrue(flat(spot[0], behind) < 0.01, "it landed behind the pig, at " + helper.relativeVec(spot[0]));
            double thrown = flat(j.position(), spot[0]);
            helper.assertTrue(Math.abs(thrown - 8.0) < 0.01, "thrown 8 blocks from its landing, got " + thrown);
            double from = flat(j.position(), pig.position());
            helper.assertTrue(Math.abs(from - 10.0) < 0.01, "straight away from the pig (2 + 8 blocks), got " + from);
            helper.assertTrue(pig.getHealth() >= 100.0F && j.bites() == 0, "it bit nothing, the pig has " + pig.getHealth());
            helper.assertTrue(j.rifts().isEmpty(), "its rift snapped shut, " + j.rifts().size() + " open");
        });
    }

    /**
     * Failing the counter: the pig never turns. The jackal lands at its side or back and bites for 6, and its rift lies
     * where it crouched.
     */
    private static void failure(GameTestHelper helper) {
        Riftjackal j = jackal(helper, 3.5, 1, 9.5);
        Pig pig = pig(helper, 6.5, 1, 5.5, 0.0F);
        j.setTarget(pig);
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            if (j.core().in(Step.TELL) && start[0] == null) start[0] = j.position();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(j.blinks() == 1, "it blinked");
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "the bite takes 6, the pig lost " + lost);
            helper.assertTrue(j.snaps() == 0, "unseen, it was not thrown back");
            helper.assertTrue(j.rifts().size() == 1 && flat(j.rifts().getFirst(), start[0]) < 0.01, "and its rift lies where it vanished");
        });
    }

    /** Out of reach: the pig steps 3 blocks further off during the crouch. The jackal still blinks and leaves a rift, but bites nothing. */
    private static void reach(GameTestHelper helper) {
        Riftjackal j = jackal(helper, 2.5, 1, 9.5);
        Pig pig = pig(helper, 6.5, 1, 5.5, 0.0F);
        j.setTarget(pig);
        boolean[] moved = {false};
        helper.onEachTick(() -> {
            if (j.core().in(Step.TELL) && !moved[0]) {
                Vec3 away = pig.position().subtract(j.landing()).multiply(1, 0, 1).normalize().scale(3.0);
                pig.snapTo(pig.getX() + away.x, pig.getY(), pig.getZ() + away.z);
                moved[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(j.blinks() == 1, "it blinked");
            helper.assertTrue(j.distanceTo(pig) > 2.5, "the pig is out of reach, " + j.distanceTo(pig));
            helper.assertTrue(pig.getHealth() >= 100.0F && j.bites() == 0, "so it bit nothing");
            helper.assertTrue(j.rifts().size() == 1, "and still left its rift");
        });
    }

    /** A lone jackal blinks again exactly 60 ticks after its last crouch started, biting 6 each time. */
    private static void every60(GameTestHelper helper) {
        Riftjackal j = jackal(helper, 2.5, 1, 6.5);
        Pig pig = pig(helper, 6.5, 1, 6.5, 0.0F);
        j.setTarget(pig);
        List<Integer> crouches = new ArrayList<>();
        boolean[] was = {false};
        helper.onEachTick(() -> {
            boolean now = j.core().in(Step.TELL);
            if (now && !was[0]) crouches.add((int) helper.getTick());
            was[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(crouches.size() >= 2 && j.blinks() >= 2, "it blinked twice, crouches at " + crouches);
            int gap = crouches.get(1) - crouches.get(0);
            helper.assertTrue(gap == 60, "60 ticks from crouch to crouch, saw " + gap);
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F * j.bites()) < 0.01F && j.bites() >= 2, "bites of 6, the pig lost " + lost + " to " + j.bites());
        });
    }

    /**
     * A pack of three (the alpha and two) blinks in turn: never two crouching at once; crouches in the pack at least 20
     * ticks apart; the first three turns go to three different jackals, the alpha first; each jackal at least 60 ticks
     * from its own last crouch.
     */
    private static void packTurns(GameTestHelper helper) {
        Pig pig = pig(helper, 6.5, 1, 6.5, 0.0F);
        Riftjackal alpha = jackal(helper, 2.5, 1, 6.5);
        Riftjackal a = jackal(helper, 10.5, 1, 6.5);
        Riftjackal b = jackal(helper, 6.5, 1, 10.5);
        Riftjackal.formPack(alpha, a, b);
        List<Riftjackal> pack = List.of(alpha, a, b);
        for (Riftjackal j : pack) j.setTarget(pig);
        List<Riftjackal> turns = new ArrayList<>();
        List<Integer> times = new ArrayList<>();
        Map<Riftjackal, Integer> last = new HashMap<>();
        boolean[] crouching = new boolean[3];
        helper.onEachTick(() -> {
            int tick = (int) helper.getTick();
            int holders = 0;
            for (int i = 0; i < 3; i++) {
                Riftjackal j = pack.get(i);
                if (j.holdsTurn()) holders++;
                boolean now = j.core().in(Step.TELL);
                if (now && !crouching[i]) {
                    if (!times.isEmpty()) helper.assertTrue(tick - times.getLast() >= 20, "crouches in a pack at least 20 ticks apart, saw " + (tick - times.getLast()));
                    Integer before = last.get(j);
                    if (before != null) helper.assertTrue(tick - before >= 60, "a jackal at least 60 ticks from its last crouch, saw " + (tick - before));
                    last.put(j, tick);
                    turns.add(j);
                    times.add(tick);
                }
                crouching[i] = now;
            }
            helper.assertTrue(holders <= 1, "one crouching at a time, tick " + tick + ": " + holders);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(turns.size() >= 5, "a steady run of turns, " + turns.size() + " so far at " + times);
            helper.assertTrue(new HashSet<>(turns.subList(0, 3)).size() == 3, "the first three turns go to three jackals");
            helper.assertTrue(turns.getFirst() == alpha, "the alpha goes first");
            float lost = 100.0F - pig.getHealth();
            int bites = alpha.bites() + a.bites() + b.bites();
            helper.assertTrue(Math.abs(lost - 6.0F * bites) < 0.01F, "bites of 6, the pig lost " + lost + " to " + bites);
        });
    }

    // ---- rifts ---------------------------------------------------------------------------------------------------------

    /**
     * A rift stays open exactly 60 ticks: counted from the tick it opens, in the same callback that opens it (callbacks
     * due on one tick run in no fixed order).
     */
    private static void riftLength(GameTestHelper helper) {
        Riftjackal j = still(helper, 9.5, 1, 9.5);
        int[] open = {0};
        boolean[] opened = {false}, closed = {false};
        helper.onEachTick(() -> {
            if (!opened[0] && helper.getTick() >= 2) {
                j.openRift(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
                opened[0] = true;
            }
            if (!opened[0] || closed[0]) return;
            if (!j.rifts().isEmpty()) open[0]++;
            else closed[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(closed[0], "the rift closes, open " + open[0] + " ticks so far");
            helper.assertTrue(open[0] == 60, "a rift lasts 60 ticks, lasted " + open[0]);
        });
    }

    /**
     * A pig and a survival player stepping into rifts are each thrown exactly 8 blocks, onto the plateau's stone (safe
     * ground), out of the rift. Off the plateau is a drop past reach, so every throw lands on it.
     */
    private static void riftThrows(GameTestHelper helper) {
        plateau(helper);
        Riftjackal j = still(helper, 6.5, 6, 6.5);
        Pig pig = pig(helper, 2.5, 6, 2.5, 0.0F);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(9.5, 6, 2.5));
        Vec3 pigFrom = pig.position(), playerFrom = player.position();
        helper.runAtTickTime(2, () -> {
            j.openRift(pigFrom);
            j.openRift(playerFrom);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(j.throwsSoFar() == 2, "both thrown, " + j.throwsSoFar());
            for (LivingEntity e : List.of(pig, player)) {
                Vec3 from = e == pig ? pigFrom : playerFrom;
                double went = flat(e.position(), from);
                helper.assertTrue(Math.abs(went - 8.0) < 0.01, e.getName().getString() + " thrown 8 blocks, went " + went);
                helper.assertTrue(Math.abs(e.getY() - from.y) < 0.01, "onto the plateau, y " + helper.relativeVec(e.position()).y);
                BlockPos under = BlockPos.containing(e.position()).below();
                helper.assertTrue(helper.getLevel().getBlockState(under).is(Blocks.STONE), "standing on stone, got " + helper.getLevel().getBlockState(under));
                helper.assertTrue(helper.getLevel().noCollision(e), "not inside a block");
            }
        });
    }

    /** A Riftjackal standing in a rift is never thrown. */
    private static void riftSparesJackals(GameTestHelper helper) {
        Riftjackal owner = still(helper, 9.5, 1, 9.5);
        Riftjackal other = still(helper, 4.5, 1, 4.5);
        Vec3 at = other.position();
        helper.runAtTickTime(2, () -> owner.openRift(at));
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(owner.rifts().size() == 1, "the rift is open");
            helper.assertTrue(flat(other.position(), at) < 0.01 && owner.throwsSoFar() == 0, "the jackal in it stays put");
            helper.succeed();
        });
    }

    /** Its rifts close with it: on death, and on removal. */
    private static void riftCloses(GameTestHelper helper) {
        Riftjackal dies = still(helper, 2.5, 1, 2.5);
        Riftjackal gone = still(helper, 9.5, 1, 9.5);
        helper.runAtTickTime(2, () -> {
            dies.openRift(helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
            gone.openRift(helper.absoluteVec(new Vec3(7.5, 1, 7.5)));
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(dies.rifts().size() == 1 && gone.rifts().size() == 1, "both rifts open");
            dies.kill(helper.getLevel());
            gone.discard();
            helper.assertTrue(dies.rifts().isEmpty(), "death closes its rift");
            helper.assertTrue(gone.rifts().isEmpty(), "removal closes its rift");
            helper.succeed();
        });
    }

    /** On a pillar 5 high with only the floor in throwing range (a drop past reach) nothing is safe: a rift does nothing. */
    private static void noSafeSpot(GameTestHelper helper) {
        for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.STONE);
        Riftjackal j = still(helper, 9.5, 1, 9.5);
        Pig pig = pig(helper, 5.5, 6, 5.5, 0.0F);
        Vec3 from = pig.position();
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(SafeTeleport.InLevel.find(helper.getLevel(), pig, from, 8.0, 0.0, inArena(helper)) == null, "no safe spot 8 out");
            j.openRift(from);
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(flat(pig.position(), from) < 0.01 && Math.abs(pig.getY() - from.y) < 0.01, "the pig stays where it is");
            helper.assertTrue(j.throwsSoFar() == 0, "nothing was thrown");
            helper.succeed();
        });
    }

    /**
     * The safe-teleport rule in the world: the first direction tried (+X, 8 blocks from the corner) is spoiled by lava
     * under the feet, fire where they go, a pit 7 deep, or a ceiling one block up (3 thick, so its top is past reach);
     * the throw lands elsewhere, exactly 8 blocks out, on the arena's stone.
     */
    private static void safeAround(GameTestHelper helper, String what) {
        BlockPos far = new BlockPos(10, 0, 2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos p = far.offset(x, 0, z);
                switch (what) {
                    case "lava" -> helper.setBlock(p, Blocks.LAVA);
                    case "fire" -> helper.setBlock(p.above(), Blocks.FIRE);
                    case "pit" -> { for (int y = 0; y >= -6; y--) helper.setBlock(p.atY(y), Blocks.AIR); }
                    default -> { for (int y = 2; y <= 4; y++) helper.setBlock(p.atY(y), Blocks.STONE); }   // too tall to stand on
                }
            }
        }
        Pig pig = pig(helper, 2.5, 1, 2.5, 0.0F);
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            Vec3 from = pig.position();
            Vec3 first = helper.absoluteVec(new Vec3(10.5, 1, 2.5));
            Block underFirst = level.getBlockState(BlockPos.containing(first).below()).getBlock();
            helper.assertTrue(SafeTeleport.InLevel.spot(level, pig, first.x, from.y, first.z) == null, "the spoiled spot is unsafe (" + what + ", " + underFirst + ")");
            Vec3 to = SafeTeleport.InLevel.find(level, pig, from, 8.0, 0.0, inArena(helper));
            helper.assertTrue(to != null, "a safe spot is found");
            Vec3 r = helper.relativeVec(to);
            helper.assertTrue(Math.abs(flat(to, from) - 8.0) < 0.01, "exactly 8 blocks out, got " + flat(to, from));
            helper.assertTrue(Math.abs(r.y - 1.0) < 0.01, "on the floor, y " + r.y);
            boolean inside = r.x > 9 - 0.5 && r.x < 12 + 0.5 && r.z > 1 - 0.5 && r.z < 4 + 0.5;
            helper.assertTrue(!inside, "clear of the " + what + ", landed at " + r);
            helper.assertTrue(level.getBlockState(BlockPos.containing(to).below()).is(Blocks.STONE), "on stone");
            helper.succeed();
        });
    }

    /** A vetoed "blink" leaves it where it crouched: no rift, no bite. */
    private static void blinkCancel(GameTestHelper helper) {
        Riftjackal j = jackal(helper, 2.5, 1, 6.5);
        Pig pig = pig(helper, 6.5, 1, 6.5, 0.0F);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == j && e.step().equals("blink")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        j.setTarget(pig);
        Vec3[] start = {null};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (j.core().in(Step.TELL) && start[0] == null) start[0] = j.position();
            if (start[0] != null && !j.core().in(Step.TELL)) over[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the crouch ended");
            helper.assertTrue(flat(j.position(), start[0]) < 0.01 && j.blinks() == 0, "it stayed where it crouched");
            helper.assertTrue(j.rifts().isEmpty() && pig.getHealth() >= 100.0F, "no rift, no bite");
        });
    }

    // ---- stats, the pack, saving, home -----------------------------------------------------------------------------

    /** A plain jackal: health 26, attack 6, armour 2, a 0.8 x 0.9 hitbox, no alpha sheets. */
    private static void stats(GameTestHelper helper) {
        Riftjackal j = still(helper, 5.5, 1, 5.5);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!j.isAlpha() && !j.core().flag(Riftjackal.ALPHA_FLAG), "no alpha");
            helper.assertTrue(j.getMaxHealth() == 26.0F && j.getHealth() == 26.0F, "health 26, got " + j.getHealth() + "/" + j.getMaxHealth());
            helper.assertTrue(j.getAttributeValue(Attributes.ATTACK_DAMAGE) == 6.0, "attack 6, got " + j.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(j.getArmorValue() == 2, "armour 2, got " + j.getArmorValue());
            helper.assertTrue(Math.abs(j.getBbWidth() - 0.8F) < 0.01F && Math.abs(j.getBbHeight() - 0.9F) < 0.01F,
                    "a 0.8 x 0.9 hitbox, got " + j.getBbWidth() + " x " + j.getBbHeight());
            helper.succeed();
        });
    }

    /** The alpha: health 40 (full), attack 6, armour 2, 1.15x the size (0.92 x 1.035), its flag set. */
    private static void alphaStats(GameTestHelper helper) {
        Riftjackal alpha = still(helper, 5.5, 1, 5.5);
        Riftjackal.formPack(alpha);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(alpha.isAlpha() && alpha.core().flag(Riftjackal.ALPHA_FLAG), "it leads, and shows its sheets");
            helper.assertTrue(alpha.getMaxHealth() == 40.0F && alpha.getHealth() == 40.0F, "health 40, got " + alpha.getHealth() + "/" + alpha.getMaxHealth());
            helper.assertTrue(alpha.getAttributeValue(Attributes.ATTACK_DAMAGE) == 6.0, "attack 6");
            helper.assertTrue(alpha.getArmorValue() == 2, "armour 2");
            helper.assertTrue(Math.abs(alpha.getBbWidth() - 0.92F) < 0.01F && Math.abs(alpha.getBbHeight() - 1.035F) < 0.01F,
                    "a 0.92 x 1.035 hitbox, got " + alpha.getBbWidth() + " x " + alpha.getBbHeight());
            helper.succeed();
        });
    }

    /** A natural group of three is one pack: the first founds it as the alpha and the rest join, in its look. */
    private static void packSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Riftjackal> group = new ArrayList<>();
        SpawnGroupData data = null;
        for (int i = 0; i < 3; i++) {
            Riftjackal j = ModMobs.RIFTJACKAL.get().create(level, EntitySpawnReason.NATURAL);
            Vec3 at = helper.absoluteVec(new Vec3(3.5 + i * 2.0, 1, 5.5));
            j.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            data = j.finalizeSpawn(level, level.getCurrentDifficultyAt(j.blockPosition()), EntitySpawnReason.NATURAL, data);
            j.setNoAi(true);
            level.addFreshEntity(j);
            TestPlayers.whenDone(helper, j::discard);
            group.add(j);
        }
        helper.runAtTickTime(2, () -> {
            UUID pack = group.getFirst().packId();
            helper.assertTrue(group.getFirst().isAlpha(), "the group's first jackal leads it");
            helper.assertTrue(group.stream().filter(Riftjackal::isAlpha).count() == 1, "one alpha");
            helper.assertTrue(group.stream().allMatch(j -> j.packId().equals(pack)), "all three share one pack");
            helper.assertTrue(group.stream().allMatch(j -> j.look().equals(group.getFirst().look())), "all three wear the alpha's look");
            helper.assertTrue(group.getFirst().pack().size() == 3, "the pack counts three");
            helper.succeed();
        });
    }

    /**
     * Pack, alpha and the blink cooldown survive a save and reload; open rifts do not. The alpha has just blinked, so
     * its cooldown is part-way through.
     */
    private static void saveLoad(GameTestHelper helper) {
        Riftjackal alpha = jackal(helper, 2.5, 1, 6.5);
        Riftjackal member = still(helper, 9.5, 1, 9.5);
        UUID id = Riftjackal.formPack(alpha, member);
        Pig pig = pig(helper, 6.5, 1, 6.5, 0.0F);
        alpha.setTarget(pig);
        Riftjackal[] copy = {null};
        int[] cooldown = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || alpha.blinks() < 1 || alpha.core().in(Step.LAND)) return;
            done[0] = true;
            ServerLevel level = helper.getLevel();
            helper.assertTrue(alpha.rifts().size() == 1, "its rift is open before the save");
            cooldown[0] = alpha.blinkCooldown();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            alpha.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            Riftjackal c = ModMobs.RIFTJACKAL.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            alpha.discard();
            c.setNoAi(true);
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, c::discard);
            copy[0] = c;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(copy[0] != null, "saved and reloaded");
            Riftjackal c = copy[0];
            helper.assertTrue(c.packId().equals(id) && c.isAlpha() && c.core().flag(Riftjackal.ALPHA_FLAG), "still the pack's alpha");
            helper.assertTrue(c.getMaxHealth() == 40.0F, "with health 40, got " + c.getMaxHealth());
            helper.assertTrue(cooldown[0] > 0 && Math.abs(c.blinkCooldown() - cooldown[0]) <= 2, "its blink cooldown carries over: " + cooldown[0] + " then " + c.blinkCooldown());
            helper.assertTrue(c.rifts().isEmpty(), "its rifts do not");
            helper.assertTrue(c.pack().contains(member), "it finds its packmate");
        });
    }

    /**
     * Home: the End highlands, never the midlands; spawn weight 4, in packs of 2 to 3; a monster, so it rates a lit
     * spot below 0 as vanilla's do.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("riftjackal"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)), "its home holds the End highlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_MIDLANDS) || b.is(Biomes.THE_END)), "never the midlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/riftjackal")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.RIFTJACKAL.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 2 && entry.value().count().maxInclusive() == 3,
                "in packs of 2 to 3, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.RIFTJACKAL.get().getCategory() == MobCategory.MONSTER, "a monster");
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at.east(), Blocks.GLOWSTONE);
        Riftjackal j = still(helper, 2.5, 1, 2.5);
        BlockPos abs = helper.absolutePos(at);
        helper.runAtTickTime(2, () -> {
            float value = j.getWalkTargetValue(abs, helper.getLevel());
            helper.assertTrue(value < 0.0F, "a lit spot rates below 0, got " + value);
            helper.succeed();
        });
    }
}
