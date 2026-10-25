package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.WarpseedBloom;
import com.panda.teeming.mob.end.WarpseedBloom.Step;
import com.panda.teeming.mob.end.WarpseedBloom.Warp;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Warpseed Bloom: a 12-tick tell (harmless, the veins lit); seeds of 3 that fly 16 blocks at 0.5 a tick, at targets
 * within 16, one every 60 ticks; a hit throws a mob or a survival player 3 to 6 blocks onto safe ground, or nowhere
 * when nothing is safe; a shield raised toward it takes the seed (no damage, no throw) and the seed bursts back, the
 * bloom taking 3 and its bulb torn for exactly 100 ticks with no seed; nothing ever moves it. Every number is checked
 * against a plain value. Throws that could leave the 12 x 12 arena are fixed by the test, or start where every spot in
 * reach is on the floor, and nothing a seed meets outside the arena is hurt or thrown.
 */
public final class WarpseedBloomTests {
    private WarpseedBloomTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("warpseed_bloom_tell", WarpseedBloomTests::tell);
        out.accept("warpseed_bloom_counter", WarpseedBloomTests::counter);
        out.accept("warpseed_bloom_failure", WarpseedBloomTests::failure);
        out.accept("warpseed_bloom_wilt_length", WarpseedBloomTests::wiltLength);
        TeemingGameTests.timeLimit("warpseed_bloom_wilt_length", 180);
        out.accept("warpseed_bloom_shield_away", WarpseedBloomTests::shieldAway);
        out.accept("warpseed_bloom_player_thrown", WarpseedBloomTests::playerThrown);
        out.accept("warpseed_bloom_warp_near", h -> warpExact(h, 3.0, 0.0));
        out.accept("warpseed_bloom_warp_far", h -> warpExact(h, 6.0, 1.0));
        out.accept("warpseed_bloom_warp_roll", WarpseedBloomTests::warpRoll);
        out.accept("warpseed_bloom_safe_ground", WarpseedBloomTests::safeGround);
        out.accept("warpseed_bloom_no_safe_spot", WarpseedBloomTests::noSafeSpot);
        out.accept("warpseed_bloom_range_in", WarpseedBloomTests::rangeIn);
        out.accept("warpseed_bloom_range_out", WarpseedBloomTests::rangeOut);
        out.accept("warpseed_bloom_seed_reach", WarpseedBloomTests::seedReach);
        out.accept("warpseed_bloom_seed_speed", WarpseedBloomTests::seedSpeed);
        out.accept("warpseed_bloom_spit_interval", WarpseedBloomTests::spitInterval);
        out.accept("warpseed_bloom_dodge", WarpseedBloomTests::dodge);
        out.accept("warpseed_bloom_warp_cancel", WarpseedBloomTests::warpCancel);
        out.accept("warpseed_bloom_rooted", WarpseedBloomTests::rooted);
        out.accept("warpseed_bloom_stats", WarpseedBloomTests::stats);
        out.accept("warpseed_bloom_save_load", WarpseedBloomTests::saveLoad);
        out.accept("warpseed_bloom_home", WarpseedBloomTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A still pig with 100 health at (x, y, z), removed when the test ends wherever a seed sent it. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /**
     * A bloom with the base look facing {@code target}, aimed at it, with a follow range of 1 so it never picks up a
     * survival player from another arena. Its seeds hurt and throw nothing outside this arena.
     */
    private static WarpseedBloom bloom(GameTestHelper helper, double x, double y, double z, LivingEntity target) {
        WarpseedBloom bloom = helper.spawn(ModMobs.WARPSEED_BLOOM.get(), new Vec3(x, y, z));
        bloom.core().setLook("base", true);
        bloom.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        bloom.face(target.position());
        bloom.setTarget(target);
        contain(helper, bloom);
        TestPlayers.whenDone(helper, bloom::discard);
        return bloom;
    }

    /** A bloom with no AI (it neither turns nor tells on its own); the hooks still work and its seeds still fly. */
    private static WarpseedBloom stillBloom(GameTestHelper helper, double x, double y, double z, LivingEntity target) {
        WarpseedBloom bloom = bloom(helper, x, y, z, target);
        bloom.setNoAi(true);
        return bloom;
    }

    /** Vetoes the hit and the throw for anything a seed of {@code bloom} meets outside this arena. */
    private static void contain(GameTestHelper helper, WarpseedBloom bloom) {
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() != bloom || e.target() == null) return;
            if ((e.step().equals("seed_hit") || e.step().equals("warp")) && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
    }

    /** Records the test tick of every seed, hit and throw of {@code bloom}, and where the one hit stood when struck. */
    private static final class Log implements Consumer<MechanicEvent> {
        final GameTestHelper helper;
        final WarpseedBloom bloom;
        final List<Integer> seeds = new ArrayList<>();
        final List<Integer> tells = new ArrayList<>();
        final List<Vec3> struckAt = new ArrayList<>();
        int hits;
        /** Hits after this many are vetoed (no damage, no throw); -1 lets every hit land. */
        int allowHits = -1;

        Log(GameTestHelper helper, WarpseedBloom bloom) {
            this.helper = helper;
            this.bloom = bloom;
            MechanicEvent.listen(this);
            TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(this));
        }

        @Override
        public void accept(MechanicEvent e) {
            if (e.mob() != bloom || e.cancelled()) return;
            int tick = (int) helper.getTick();
            switch (e.step()) {
                case "seed" -> seeds.add(tick);
                case "tell" -> tells.add(tick);
                case "seed_hit" -> {
                    if (allowHits >= 0 && hits >= allowHits) {
                        e.cancel();
                        return;
                    }
                    hits++;
                    if (e.target() != null) struckAt.add(e.target().position());
                }
                default -> {}
            }
        }
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** A survival player at (x, 1, z) facing {@code yaw}, shield raised and held up. */
    private static ServerPlayer shielded(GameTestHelper helper, double x, double z, float yaw) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(x, 1, z));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            p.setYRot(yaw);
            p.setYHeadRot(yaw);
            p.yHeadRotO = yaw;
        });
        return p;
    }

    /** A plateau of stone filling the arena 5 high, so ground off it (outside the arena too) is a drop past reach. */
    private static void plateau(GameTestHelper helper) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }

    // ---- the tell, the counter, the failure ------------------------------------------------------------------------

    /**
     * The tell lasts exactly 12 ticks: the veins are lit (glow stage 1), nothing is launched and nothing hurt. Then one
     * seed. (The throw is fixed 3 blocks toward the arena's middle.)
     */
    private static void tell(GameTestHelper helper) {
        Pig pig = pig(helper, 6.5, 1, 9.5);
        WarpseedBloom bloom = bloom(helper, 6.5, 1, 3.5, pig);
        bloom.fixWarp(new Warp(3.0, Math.PI));
        Log log = new Log(helper, bloom);
        int[] tellTicks = {0};
        boolean[] ended = {false}, quiet = {true}, lit = {true};
        helper.onEachTick(() -> {
            if (bloom.core().in(Step.TELL)) {
                tellTicks[0]++;
                if (!log.seeds.isEmpty() || pig.getHealth() < 100.0F) quiet[0] = false;
                if (bloom.core().stage() != 1) lit[0] = false;
            } else if (tellTicks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the tell ends, " + tellTicks[0] + " ticks so far");
            helper.assertTrue(tellTicks[0] == 12, "the bulb swells for 12 ticks, saw " + tellTicks[0]);
            helper.assertTrue(quiet[0], "nothing is launched or hurt during the tell");
            helper.assertTrue(lit[0], "the veins glow through the tell");
            helper.assertTrue(log.seeds.size() == 1, "the tell ends in one seed, saw " + log.seeds.size());
            helper.assertTrue(bloom.core().stage() == 0, "the glow goes dark after the tell");
        });
    }

    /**
     * The counter: a shield raised toward the bloom takes the seed. The player is unhurt and not thrown; the seed bursts
     * back, the bloom takes exactly 3 and its bulb is torn.
     */
    private static void counter(GameTestHelper helper) {
        ServerPlayer p = shielded(helper, 9.5, 6.5, 90.0F);           // facing -x, toward the bloom
        WarpseedBloom bloom = stillBloom(helper, 3.5, 1, 6.5, p);
        Log log = new Log(helper, bloom);
        Vec3 at = p.position();
        float before = p.getHealth();
        float[] bloomBefore = {0.0F};
        helper.runAtTickTime(2, () -> {
            bloomBefore[0] = bloom.getHealth();
            helper.assertTrue(bloom.tell(p), "the tell starts");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(p.isBlocking(), "the player holds the shield up");
            helper.assertTrue(bloom.blocks() == 1, "the shield took the seed, blocks " + bloom.blocks());
            helper.assertTrue(log.hits == 0 && bloom.warps() == 0, "no hit, no throw");
            helper.assertTrue(p.getHealth() >= before, "the player is unhurt, health " + p.getHealth());
            helper.assertTrue(p.position().distanceTo(at) < 0.01, "the player stays put");
            helper.assertTrue(bloom.wilted(), "its bulb is torn, phase " + bloom.phase());
            float lost = bloomBefore[0] - bloom.getHealth();
            helper.assertTrue(Math.abs(lost - 3.0F) < 0.01F, "the seed bursts back for 3, the bloom lost " + lost);
        });
    }

    /**
     * Failing the counter: the pig takes exactly 3 and is thrown 3 to 6 blocks across, onto the floor. It stands in the
     * arena's middle, so every spot in reach is on the floor.
     */
    private static void failure(GameTestHelper helper) {
        Pig pig = pig(helper, 6.0, 1, 6.0);
        WarpseedBloom bloom = bloom(helper, 6.0, 1, 1.5, pig);
        Log log = new Log(helper, bloom);
        log.allowHits = 1;
        float before = pig.getHealth();
        Vec3[] landed = {null};
        helper.onEachTick(() -> {
            if (landed[0] == null && bloom.warps() == 1) landed[0] = pig.position();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(log.hits == 1 && landed[0] != null, "one seed struck and threw the pig, hits " + log.hits + ", throws " + bloom.warps());
            float lost = before - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 3.0F) < 0.01F, "a seed does 3, the pig lost " + lost);
            double went = flat(landed[0], log.struckAt.getFirst());
            helper.assertTrue(went >= 3.0 - 0.01 && went <= 6.0 + 0.01, "thrown 3 to 6 blocks, went " + went);
            helper.assertTrue(Math.abs(landed[0].y - log.struckAt.getFirst().y) < 0.01, "onto the floor, y " + helper.relativeVec(landed[0]).y);
            helper.assertTrue(helper.getLevel().getBlockState(BlockPos.containing(landed[0]).below()).isSolid(), "standing on ground");
        });
    }

    /**
     * A torn bulb lasts exactly 100 ticks, and while it lasts it can't start a tell; once it is over it can. The tear
     * comes from a real blocked seed, so it starts where play starts it.
     */
    private static void wiltLength(GameTestHelper helper) {
        ServerPlayer p = shielded(helper, 9.5, 6.5, 90.0F);
        WarpseedBloom bloom = stillBloom(helper, 3.5, 1, 6.5, p);
        Log log = new Log(helper, bloom);
        int[] wilted = {0};
        boolean[] over = {false}, refused = {true}, againAfter = {false};
        helper.runAtTickTime(2, () -> bloom.tell(p));
        helper.onEachTick(() -> {
            if (bloom.wilted()) {
                wilted[0]++;
                if (bloom.tell(p)) refused[0] = false;
            } else if (wilted[0] > 0 && !over[0]) {
                over[0] = true;
                againAfter[0] = bloom.tell(p);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the tear heals, " + wilted[0] + " ticks so far");
            helper.assertTrue(wilted[0] == 100, "the bulb stays torn 100 ticks, saw " + wilted[0]);
            helper.assertTrue(refused[0], "no tell while it is torn");
            helper.assertTrue(log.seeds.size() == 1, "only the one blocked seed while torn, saw " + log.seeds.size());
            helper.assertTrue(againAfter[0], "healed, it tells again at once");
        });
    }

    /** A shield held facing away takes nothing: the seed in the player's back hurts and throws them. */
    private static void shieldAway(GameTestHelper helper) {
        ServerPlayer p = shielded(helper, 8.5, 6.5, -90.0F);          // facing +x, away from the bloom
        WarpseedBloom bloom = stillBloom(helper, 3.5, 1, 6.5, p);
        bloom.fixWarp(new Warp(3.0, Math.PI * 1.5));                    // toward -z, staying on the floor
        float before = p.getHealth();
        Vec3 at = p.position();
        helper.runAtTickTime(2, () -> bloom.tell(p));
        helper.succeedWhen(() -> {
            helper.assertTrue(p.isBlocking(), "the player holds the shield up, the wrong way");
            helper.assertTrue(bloom.blocks() == 0 && bloom.warps() == 1, "not blocked; thrown, blocks " + bloom.blocks() + ", throws " + bloom.warps());
            helper.assertTrue(p.getHealth() < before, "the player is hurt, health " + p.getHealth());
            helper.assertTrue(Math.abs(flat(p.position(), at) - 3.0) < 0.01, "thrown 3 blocks, went " + flat(p.position(), at));
            helper.assertTrue(!bloom.wilted(), "the bulb is whole");
        });
    }

    /** A survival player without a shield is hurt and thrown like any mob (the throw fixed at 4 blocks toward -x). */
    private static void playerThrown(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(8.5, 1, 6.5));
        WarpseedBloom bloom = stillBloom(helper, 3.5, 1, 9.5, p);
        bloom.fixWarp(new Warp(4.0, Math.PI));
        float before = p.getHealth();
        Vec3 at = p.position();
        helper.runAtTickTime(2, () -> bloom.tell(p));
        helper.succeedWhen(() -> {
            helper.assertTrue(bloom.warps() == 1, "thrown once, " + bloom.warps());
            helper.assertTrue(p.getHealth() < before, "hurt, health " + p.getHealth());
            helper.assertTrue(Math.abs(flat(p.position(), at) - 4.0) < 0.01, "thrown 4 blocks, went " + flat(p.position(), at));
            helper.assertTrue(Math.abs(p.getY() - at.y) < 0.01, "onto the floor");
        });
    }

    // ---- the throw -------------------------------------------------------------------------------------------------

    /**
     * The ends of the throw's reach: a roll of {@code u} throws exactly {@code distance} blocks (3 at the low end, 6 at
     * the high end), and the pig lands that far away on the floor, toward the arena's middle.
     */
    private static void warpExact(GameTestHelper helper, double distance, double u) {
        helper.assertTrue(Math.abs(WarpseedBloom.warpDistance(u) - distance) < 1.0E-9, "a roll of " + u + " throws " + distance
                + ", got " + WarpseedBloom.warpDistance(u));
        Pig pig = pig(helper, 9.5, 1, 6.0);
        WarpseedBloom bloom = stillBloom(helper, 9.5, 1, 1.5, pig);
        bloom.fixWarp(new Warp(WarpseedBloom.warpDistance(u), Math.PI));        // toward -x
        Log log = new Log(helper, bloom);
        Vec3 from = pig.position();
        helper.runAtTickTime(2, () -> bloom.tell(pig));
        helper.succeedWhen(() -> {
            helper.assertTrue(log.hits == 1 && bloom.warps() == 1, "struck and thrown");
            double went = flat(pig.position(), from);
            helper.assertTrue(Math.abs(went - distance) < 0.01, "thrown exactly " + distance + ", went " + went);
            helper.assertTrue(Math.abs(pig.getY() - from.y) < 0.01, "onto the floor");
        });
    }

    /** Rolled throws all lie between 3 and 6 blocks, and reach both ends: 2000 rolls fall in 3..6, the least under 3.05. */
    private static void warpRoll(GameTestHelper helper) {
        RandomSource random = RandomSource.create(57L);
        double least = Double.MAX_VALUE, most = 0.0;
        for (int i = 0; i < 2000; i++) {
            double d = WarpseedBloom.warpDistance(random.nextDouble());
            least = Math.min(least, d);
            most = Math.max(most, d);
        }
        helper.assertTrue(least >= 3.0 && most <= 6.0, "every throw in 3..6, saw " + least + ".." + most);
        helper.assertTrue(least < 3.05 && most > 5.95, "the whole reach is used, saw " + least + ".." + most);
        helper.assertTrue(Math.abs(WarpseedBloom.warpDistance(0.5) - 4.5) < 1.0E-9, "a middle roll throws 4.5");
        helper.succeed();
    }

    /**
     * Never off the edge: on a plateau 5 high, a throw first aimed off it (5 blocks toward -x from 3 blocks in, where
     * the ground is a drop past reach) lands elsewhere, exactly 5 blocks out, on the plateau's stone.
     */
    private static void safeGround(GameTestHelper helper) {
        plateau(helper);
        Pig pig = pig(helper, 3.0, 6, 6.0);
        WarpseedBloom bloom = stillBloom(helper, 8.5, 6, 6.0, pig);
        bloom.fixWarp(new Warp(5.0, Math.PI));
        Vec3 from = pig.position();
        helper.runAtTickTime(2, () -> bloom.tell(pig));
        helper.succeedWhen(() -> {
            helper.assertTrue(bloom.warps() == 1, "thrown once");
            Vec3 r = helper.relativeVec(pig.position());
            helper.assertTrue(Math.abs(flat(pig.position(), from) - 5.0) < 0.01, "exactly 5 blocks out, went " + flat(pig.position(), from));
            helper.assertTrue(Math.abs(r.y - 6.0) < 0.01, "on the plateau, y " + r.y);
            helper.assertTrue(r.x > 0.0 && r.x < 12.0 && r.z > 0.0 && r.z < 12.0, "not off its edge, at " + r);
            helper.assertTrue(helper.getLevel().getBlockState(BlockPos.containing(pig.position()).below()).is(Blocks.STONE), "on stone");
        });
    }

    /** On a pillar 5 high with only the floor in reach (a drop past reach) nothing is safe: the pig is hurt but stays. */
    private static void noSafeSpot(GameTestHelper helper) {
        for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.STONE);
        Pig pig = pig(helper, 5.5, 6, 5.5);
        WarpseedBloom bloom = bloom(helper, 5.5, 1, 11.5, pig);
        Log log = new Log(helper, bloom);
        log.allowHits = 1;
        Vec3 from = pig.position();
        helper.succeedWhen(() -> {
            helper.assertTrue(log.hits == 1, "the seed struck, hits " + log.hits);
            helper.assertTrue(Math.abs(pig.getHealth() - 97.0F) < 0.01F, "the pig took 3, health " + pig.getHealth());
            helper.assertTrue(pig.position().distanceTo(from) < 0.01, "and stays on its pillar");
            helper.assertTrue(bloom.warps() == 0, "nothing was thrown");
        });
    }

    // ---- range, reach, speed, rhythm -------------------------------------------------------------------------------

    /** A target 15.84 blocks away (corner to corner, on a pillar 3 high) is spat at and hit. */
    private static void rangeIn(GameTestHelper helper) {
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(11, y, 11), Blocks.STONE);
        Pig pig = pig(helper, 11.5, 4, 11.5);
        WarpseedBloom bloom = bloom(helper, 0.5, 1, 0.5, pig);
        bloom.fixWarp(new Warp(3.0, Math.PI * 1.25));                    // toward the arena's middle, down onto the floor
        Log log = new Log(helper, bloom);
        log.allowHits = 1;
        helper.runAtTickTime(1, () -> helper.assertTrue(Math.abs(bloom.distanceTo(pig) - 15.84) < 0.01, "15.84 apart, got " + bloom.distanceTo(pig)));
        helper.succeedWhen(() -> {
            helper.assertTrue(log.seeds.size() >= 1 && log.hits == 1, "spat at and hit, seeds " + log.seeds.size() + ", hits " + log.hits);
            helper.assertTrue(Math.abs(pig.getHealth() - 97.0F) < 0.01F, "the pig took 3, health " + pig.getHealth());
        });
    }

    /** A target 16.06 blocks away (on a pillar 4 high), in plain sight, is never spat at. */
    private static void rangeOut(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(11, y, 11), Blocks.STONE);
        Pig pig = pig(helper, 11.5, 5, 11.5);
        WarpseedBloom bloom = bloom(helper, 0.5, 1, 0.5, pig);
        Log log = new Log(helper, bloom);
        helper.runAtTickTime(1, () -> helper.assertTrue(Math.abs(bloom.distanceTo(pig) - 16.06) < 0.01, "16.06 apart, got " + bloom.distanceTo(pig)));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(bloom.getSensing().hasLineOfSight(pig), "it can see the pig");
            helper.assertTrue(log.tells.isEmpty() && log.seeds.isEmpty(), "out of range: no tell and no seed");
            helper.assertTrue(pig.getHealth() >= 100.0F, "the pig is unhurt");
            helper.succeed();
        });
    }

    /**
     * A seed flies 16 blocks and no further: spat corner to corner, climbing under the roof, the farthest it is seen is
     * 15.5 blocks from the pore (one 0.5 step short of 16), and then it is gone without a hit.
     */
    private static void seedReach(GameTestHelper helper) {
        Pig pig = pig(helper, 1.5, 1, 10.5);
        WarpseedBloom bloom = stillBloom(helper, 0.5, 1, 0.5, pig);
        Log log = new Log(helper, bloom);
        Vec3[] pore = {null};
        double[] farthest = {0.0};
        boolean[] spent = {false};
        helper.runAtTickTime(2, () -> {
            pore[0] = bloom.pore();
            Vec3 dir = new Vec3(1.0, 0.45, 1.0).normalize();
            helper.assertTrue(bloom.spit(helper.getLevel(), pore[0].add(dir.scale(16.0)), null), "the seed is launched");
        });
        helper.onEachTick(() -> {
            if (pore[0] == null) return;
            for (Vec3 s : bloom.seedPositions()) farthest[0] = Math.max(farthest[0], s.distanceTo(pore[0]));
            if (farthest[0] > 0.0 && bloom.seedsInFlight() == 0) spent[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(spent[0], "the seed is spent, farthest so far " + farthest[0]);
            helper.assertTrue(Math.abs(farthest[0] - 15.5) < 0.01, "last seen 15.5 out, one step short of 16: " + farthest[0]);
            helper.assertTrue(log.hits == 0 && bloom.blocks() == 0, "it hit nothing");
        });
    }

    /** A seed moves exactly 0.5 blocks each tick. */
    private static void seedSpeed(GameTestHelper helper) {
        Pig pig = pig(helper, 10.5, 1, 10.5);
        WarpseedBloom bloom = stillBloom(helper, 1.5, 1, 1.5, pig);
        bloom.fixWarp(new Warp(3.0, Math.PI));
        List<Vec3> track = new ArrayList<>();
        helper.runAtTickTime(2, () -> bloom.spit(helper.getLevel(), pig));
        helper.onEachTick(() -> {
            if (bloom.seedsInFlight() == 1) track.add(bloom.seedPositions().getFirst());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(track.size() >= 10, "tracked " + track.size() + " ticks of flight");
            for (int i = 1; i < 10; i++) {
                double step = track.get(i).distanceTo(track.get(i - 1));
                helper.assertTrue(Math.abs(step - 0.5) < 0.001, "0.5 blocks a tick, step " + i + " was " + step);
            }
        });
    }

    /** With its target held in range it spits once every 60 ticks (the hits vetoed, so the pig stays put). */
    private static void spitInterval(GameTestHelper helper) {
        Pig pig = pig(helper, 6.5, 1, 9.5);
        WarpseedBloom bloom = bloom(helper, 6.5, 1, 3.5, pig);
        Log log = new Log(helper, bloom);
        log.allowHits = 0;
        helper.succeedWhen(() -> {
            helper.assertTrue(log.seeds.size() >= 2, "two seeds so far: " + log.seeds);
            int gap = log.seeds.get(1) - log.seeds.get(0);
            helper.assertTrue(gap == 60, "one seed every 60 ticks, the gap was " + gap);
        });
    }

    /** The other counter: a target that steps aside as the seed leaves is not hit or thrown; seeds fly straight. */
    private static void dodge(GameTestHelper helper) {
        Pig pig = pig(helper, 7.5, 1, 7.5);
        WarpseedBloom bloom = stillBloom(helper, 1.5, 1, 1.5, pig);
        Log log = new Log(helper, bloom);
        helper.runAtTickTime(2, () -> bloom.spit(helper.getLevel(), pig));
        helper.runAtTickTime(4, () -> pig.snapTo(pig.getX() + 1.5, pig.getY(), pig.getZ() - 1.5));
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(log.seeds.size() == 1 && bloom.seedsInFlight() == 0, "the one seed is spent");
            helper.assertTrue(log.hits == 0 && bloom.warps() == 0, "it missed, hits " + log.hits);
            helper.assertTrue(pig.getHealth() >= 100.0F, "the pig that stepped aside is unhurt");
            helper.succeed();
        });
    }

    /** A vetoed throw: the hit still lands for 3, but the pig stays where it was struck. */
    private static void warpCancel(GameTestHelper helper) {
        Pig pig = pig(helper, 6.5, 1, 8.5);
        WarpseedBloom bloom = stillBloom(helper, 6.5, 1, 2.5, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == bloom && e.step().equals("warp")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        Vec3 from = pig.position();
        helper.runAtTickTime(2, () -> bloom.tell(pig));
        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs(pig.getHealth() - 97.0F) < 0.01F, "the hit lands for 3, health " + pig.getHealth());
            helper.assertTrue(pig.position().distanceTo(from) < 0.01 && bloom.warps() == 0, "the pig stays");
        });
    }

    /** Knockback, a shove and a blast next to it leave it exactly where it grew. */
    private static void rooted(GameTestHelper helper) {
        Pig pig = pig(helper, 11.5, 1, 11.5);
        WarpseedBloom bloom = stillBloom(helper, 5.5, 1, 5.5, pig);
        bloom.setNoAi(false);
        bloom.setTarget(null);
        Pig attacker = pig(helper, 4.0, 1, 5.5);
        Vec3 start = bloom.position();
        float[] health = {0.0F};
        helper.runAtTickTime(2, () -> {
            health[0] = bloom.getHealth();
            bloom.hurtServer(helper.getLevel(), bloom.damageSources().mobAttack(attacker), 1.0F);
            bloom.knockback(3.0, -1.0, 0.0, bloom.damageSources().mobAttack(attacker), 1.0F);
            bloom.push(1.0, 0.3, 1.0);
            bloom.setDeltaMovement(0.8, 0.0, -0.8);
            helper.getLevel().explode(null, bloom.getX() + 1.5, bloom.getY() + 0.5, bloom.getZ(), 2.0F, Level.ExplosionInteraction.NONE);
        });
        helper.runAtTickTime(25, () -> {
            double moved = Math.hypot(bloom.getX() - start.x, bloom.getZ() - start.z);
            helper.assertTrue(bloom.getHealth() < health[0], "the blows landed, health " + bloom.getHealth());
            helper.assertTrue(moved < 0.01, "it never left its spot, moved " + moved);
            helper.succeed();
        });
    }

    // ---- stats, saving, home ---------------------------------------------------------------------------------------

    /** Health 26, attack 3, armour 4, a 0.8 x 1.6 hitbox. */
    private static void stats(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 1, 9.5);
        WarpseedBloom bloom = stillBloom(helper, 4.5, 1, 4.5, pig);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(bloom.getMaxHealth() == 26.0F && bloom.getHealth() == 26.0F, "health 26, got " + bloom.getHealth() + "/" + bloom.getMaxHealth());
            helper.assertTrue(bloom.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3.0, "attack 3, got " + bloom.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(bloom.getArmorValue() == 4, "armour 4, got " + bloom.getArmorValue());
            helper.assertTrue(Math.abs(bloom.getBbWidth() - 0.8F) < 0.01F && Math.abs(bloom.getBbHeight() - 1.6F) < 0.01F,
                    "a 0.8 x 1.6 hitbox, got " + bloom.getBbWidth() + " x " + bloom.getBbHeight());
            helper.succeed();
        });
    }

    /** A torn bulb and the seed cooldown survive a save and reload. */
    private static void saveLoad(GameTestHelper helper) {
        Pig pig = pig(helper, 9.5, 1, 9.5);
        WarpseedBloom bloom = stillBloom(helper, 3.5, 1, 3.5, pig);
        WarpseedBloom[] copy = {null};
        int[] cooldown = {0};
        helper.runAtTickTime(2, () -> {
            bloom.tell(pig);
            helper.assertTrue(bloom.wilt(helper.getLevel(), null), "the bulb tears");
        });
        helper.runAtTickTime(10, () -> {
            ServerLevel level = helper.getLevel();
            cooldown[0] = bloom.cooldown();
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            bloom.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            tag.remove("UUID");
            WarpseedBloom c = ModMobs.WARPSEED_BLOOM.get().create(level, EntitySpawnReason.LOAD);
            c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            bloom.discard();
            c.setNoAi(true);
            level.addFreshEntity(c);
            TestPlayers.whenDone(helper, c::discard);
            copy[0] = c;
        });
        helper.runAtTickTime(14, () -> {
            WarpseedBloom c = copy[0];
            helper.assertTrue(c.wilted(), "still torn after the reload, phase " + c.phase());
            helper.assertTrue(c.core().length() > 80 && c.core().length() <= 92, "with the rest of its 100 ticks to go, " + c.core().length());
            helper.assertTrue(cooldown[0] == 60 && c.cooldown() == 60, "the seed cooldown carries over: " + cooldown[0] + " then " + c.cooldown());
            helper.succeed();
        });
    }

    /** Home: the End highlands, never the midlands or the main island; spawn weight 5, alone; a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("warpseed_bloom"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_HIGHLANDS)), "its home holds the End highlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_MIDLANDS) || b.is(Biomes.THE_END)), "never the midlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/warpseed_bloom")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.WARPSEED_BLOOM.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 5, "spawn weight 5, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, groups of " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.WARPSEED_BLOOM.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
