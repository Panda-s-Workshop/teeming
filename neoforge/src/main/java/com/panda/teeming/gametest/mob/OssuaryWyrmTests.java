package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.OssuaryWyrm;
import com.panda.teeming.mob.nether.OssuaryWyrm.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Ossuary Wyrm: it swims through soul sand and soul soil at 0.3 blocks a tick and hunts survival players within 16
 * blocks. 6.37 blocks from its prey it rumbles under the surface for 20 ticks (nothing happens yet), then arcs out:
 * the skull comes down on its prey at the end of the arc and bites for its attack, 8, the body following through the
 * same arc. It is one mob with nine hitboxes, the skull and 8 vertebrae of 0.8 each: a blow on the skull counts x2, on
 * a vertebra x0.5, all to the one health pool. It never leaves the soul sand.
 *
 * <p>Every test lays soul sand (or soul soil, netherrack, stone) on the arena's floor at y 1, so its top is y 2. The
 * strike tests put the wyrm 6.37 blocks from its prey along x, the leap's own length, so it rumbles at once. Expected
 * values are written out as plain numbers.
 */
public final class OssuaryWyrmTests {
    private OssuaryWyrmTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("ossuary_wyrm_tell", OssuaryWyrmTests::tell);
        out.accept("ossuary_wyrm_counter", OssuaryWyrmTests::stepAsideAndHitTheSkull);
        out.accept("ossuary_wyrm_failure", OssuaryWyrmTests::standStill);
        out.accept("ossuary_wyrm_bite_numbers", OssuaryWyrmTests::biteNumbers);
        out.accept("ossuary_wyrm_hit_numbers", OssuaryWyrmTests::hitNumbers);
        out.accept("ossuary_wyrm_one_pool", OssuaryWyrmTests::onePool);
        out.accept("ossuary_wyrm_buried_parts", OssuaryWyrmTests::buriedParts);
        out.accept("ossuary_wyrm_stats", OssuaryWyrmTests::stats);
        out.accept("ossuary_wyrm_swim_speed", OssuaryWyrmTests::swimSpeedSoulSand);
        out.accept("ossuary_wyrm_swim_soul_soil", OssuaryWyrmTests::swimSpeedSoulSoil);
        out.accept("ossuary_wyrm_chain_follows", OssuaryWyrmTests::chainFollows);
        out.accept("ossuary_wyrm_soul_sand_only", OssuaryWyrmTests::soulSandOnly);
        out.accept("ossuary_wyrm_crawls_back", OssuaryWyrmTests::crawlsBack);
        out.accept("ossuary_wyrm_spawns", OssuaryWyrmTests::spawns);
        out.accept("ossuary_wyrm_bite_cancel", OssuaryWyrmTests::biteCancel);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Fills x0..x1, z0..z1 at y 1 (on the stone floor) with {@code block}. */
    private static void floor(GameTestHelper helper, Block block, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) helper.setBlock(new BlockPos(x, 1, z), block);
    }

    /** A wyrm already under the soul sand at (x, z), its cruising goal removed (its hunt still runs). */
    private static OssuaryWyrm wyrm(GameTestHelper helper, double x, double z) {
        OssuaryWyrm w = helper.spawn(ModMobs.OSSUARY_WYRM.get(), new Vec3(x, 2, z));
        w.removeFreeWill();
        helper.assertTrue(w.submerge(), "it sinks into the soul sand it was placed on");
        return w;
    }

    /** A wyrm lying on hard ground at (x, z), facing +x (its skull at x, its vertebrae back toward -x). */
    private static OssuaryWyrm stranded(GameTestHelper helper, double x, double z) {
        OssuaryWyrm w = helper.spawn(ModMobs.OSSUARY_WYRM.get(), new Vec3(x, 2, z));
        w.removeFreeWill();
        w.setYRot(-90.0F);
        return w;
    }

    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        return pig;
    }

    /** Turns {@code looker}'s head and eyes straight at {@code at} (a blow it deals is traced along that look). */
    private static void aim(LivingEntity looker, Vec3 at) {
        Vec3 d = at.subtract(looker.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        looker.setYRot(yaw);
        looker.setYHeadRot(yaw);
        looker.yHeadRotO = yaw;
        looker.setXRot(pitch);
        looker.xRotO = pitch;
    }

    /** Records the wyrm's own tick count at the first time it posts each mechanic step; removed when the test ends. */
    private static Map<String, Integer> steps(GameTestHelper helper, OssuaryWyrm w) {
        Map<String, Integer> at = new HashMap<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == w) at.putIfAbsent(e.step(), w.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    private static double surface(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(0, 2, 0)).y;
    }

    private static Vec3 at(GameTestHelper helper, double x, double y, double z) {
        return helper.absoluteVec(new Vec3(x, y, z));
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A survival player 6.37 blocks away sets off the rumble at once. It lasts exactly 20 ticks from the tell to the
     * arc; meanwhile the wyrm holds still under the surface, its skull never out, and nothing happens to the player.
     */
    private static void tell(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 10, 3, 8);
        OssuaryWyrm w = wyrm(helper, 2.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(8.87, 2, 5.5));
        float hp = p.getHealth();
        Map<String, Integer> at = steps(helper, w);
        long[] first = {-1};
        boolean[] harmed = {false}, out = {false};
        double[] moved = {0.0};
        Vec3[] start = {null};
        StringBuilder seen = new StringBuilder();
        String[] last = {""};
        helper.onEachTick(() -> {
            // what the wyrm went through, for the failure message
            String now = w.phase().step() + (w.isRemoved() ? " removed " + w.getRemovalReason() : "") + (w.isNoAi() ? " noAI" : "");
            if (!now.equals(last[0])) {
                seen.append(" t").append(helper.getTick()).append('/').append(w.tickCount).append(' ').append(now);
                last[0] = now;
            }
            if (!w.core().in(Step.TELL)) return;
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = w.position();
            }
            harmed[0] |= p.getHealth() < hp;
            out[0] |= w.skullOut() || w.vulnerable() || !w.submerged();
            moved[0] = Math.max(moved[0], w.position().distanceTo(start[0]));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.containsKey("tell") && at.containsKey("arc"), "the rumble runs into the arc: " + at + ", phases" + seen
                    + ", elapsed " + w.core().elapsed() + "/" + w.core().length() + ", ticks " + w.tickCount);
            helper.assertTrue(first[0] <= 3, "6.37 blocks from its prey it rumbles at once, it started at tick " + first[0]);
            int tell = at.get("arc") - at.get("tell");
            helper.assertTrue(tell == 20, "the ground rumbles for 20 ticks, measured " + tell);
            helper.assertTrue(!harmed[0], "nothing happens to the player while it rumbles");
            helper.assertTrue(!out[0], "it stays under the surface through the rumble, its skull hidden");
            helper.assertTrue(moved[0] < 0.01, "it holds its spot, moved " + moved[0]);
        });
    }

    /**
     * The counter: as the ground rumbles the player steps 2 blocks out of its line. The skull comes down where they
     * stood and bites nothing; while it is out of the sand it glows gold, and a 4-damage blow traced onto it costs 8.
     */
    private static void stepAsideAndHitTheSkull(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 10, 2, 9);
        OssuaryWyrm w = wyrm(helper, 2.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(8.87, 2, 5.5));
        Pig striker = pig(helper, 5.5, 2, 2.5);
        float hp = p.getHealth();
        boolean[] stepped = {false}, harmed = {false}, arced = {false}, glowing = {false};
        float[] skullLoss = {-1.0F};
        helper.onEachTick(() -> {
            if (!stepped[0] && w.core().in(Step.TELL)) {
                p.snapTo(at(helper, 8.87, 2, 7.5));           // two blocks aside, out of its line
                stepped[0] = true;
            }
            harmed[0] |= p.getHealth() < hp;
            arced[0] |= w.core().in(Step.ARC);
            if (skullLoss[0] < 0 && w.core().in(Step.ARC) && w.skullOut()) {
                glowing[0] = w.vulnerable();
                aim(striker, w.partBoxes()[0].getCenter());
                float before = w.getHealth();
                w.hurtServer(helper.getLevel(), w.damageSources().indirectMagic(striker, striker), 4.0F);
                skullLoss[0] = before - w.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepped[0] && arced[0], "it rumbled and arced");
            helper.assertTrue(w.core().idle() && w.submerged(), "the leap ended back under the soul sand, phase " + w.phase());
            helper.assertTrue(!harmed[0], "two blocks out of its line the bite missed");
            helper.assertTrue(glowing[0], "its skull glowed gold while it was out of the sand");
            helper.assertTrue(Math.abs(skullLoss[0] - 8.0F) < 0.01F, "a blow of 4 on the skull costs 8, it lost " + skullLoss[0]);
        });
    }

    /**
     * Failing the counter: standing still in its line costs the bite at the end of the arc, Atk 8, scaled for
     * difficulty; the skull bites on its way down, past the top of the arc.
     */
    private static void standStill(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 10, 3, 8);
        OssuaryWyrm w = wyrm(helper, 2.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(8.87, 2, 5.5));
        float before = p.getHealth();
        // mob damage to players scales with difficulty: 8 is 5 on Easy, 8 on Normal, 12 on Hard
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 5.0F;
            case HARD -> 12.0F;
            default -> 8.0F;
        };
        double[] biteAt = {Double.NaN};
        boolean[] rumbled = {false};
        helper.onEachTick(() -> {
            rumbled[0] |= w.core().in(Step.TELL);
            if (Double.isNaN(biteAt[0]) && p.getHealth() < before) biteAt[0] = OssuaryWyrm.headS(w.phase(), w.core().progress(0.0F));
        });
        helper.succeedWhen(() -> {
            float lost = before - p.getHealth();
            helper.assertTrue(lost > 0.0F, "the skull bites, phase " + w.phase());
            helper.assertTrue(rumbled[0], "the ground rumbled first");
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the bite does " + expected + " here, the player lost " + lost);
            helper.assertTrue(biteAt[0] > 5.5, "it bit on the way down, past the top of the arc (5.5 along it), at " + biteAt[0]);
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** Given a pig as its prey (no difficulty scaling), the bite does exactly 8: its attack. */
    private static void biteNumbers(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 10, 3, 8);
        OssuaryWyrm w = wyrm(helper, 2.5, 5.5);
        Pig pig = pig(helper, 8.87, 2, 5.5);
        float before = pig.getHealth();
        float[] lost = {-1.0F};
        helper.runAtTickTime(1, () -> w.setTarget(pig));
        helper.onEachTick(() -> {
            if (lost[0] < 0 && pig.getHealth() < before) lost[0] = before - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] > 0.0F, "the skull bites the pig, phase " + w.phase());
            helper.assertTrue(Math.abs(lost[0] - 8.0F) < 0.01F, "the bite does 8 (Atk 8), the pig lost " + lost[0]);
        });
    }

    /**
     * Where a blow lands: three wyrms lie on stone; a 4-damage blow traced onto the skull costs 8, one onto the third
     * vertebra costs 2, and magic with no place to land costs 4.
     */
    private static void hitNumbers(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 0, 11, 0, 11);
        OssuaryWyrm skull = stranded(helper, 9.5, 2.5);
        OssuaryWyrm spine = stranded(helper, 9.5, 5.5);
        OssuaryWyrm plain = stranded(helper, 9.5, 8.5);
        Pig a = pig(helper, 9.5, 2, 1.0);
        Pig b = pig(helper, 7.1, 2, 4.0);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(skull.vulnerable(), "stranded on stone its skull lies open and glows gold");
            aim(a, skull.partBoxes()[0].getCenter());
            helper.assertTrue(skull.partHit(skull.damageSources().indirectMagic(a, a)) == 0, "the blow is traced onto the skull");
            float s0 = skull.getHealth();
            skull.hurtServer(helper.getLevel(), skull.damageSources().indirectMagic(a, a), 4.0F);
            float s = s0 - skull.getHealth();
            helper.assertTrue(Math.abs(s - 8.0F) < 0.01F, "a blow of 4 on the skull costs 8 (x2), lost " + s);

            aim(b, spine.partBoxes()[3].getCenter());
            helper.assertTrue(spine.partHit(spine.damageSources().indirectMagic(b, b)) == 3, "the blow is traced onto the third vertebra");
            float v0 = spine.getHealth();
            spine.hurtServer(helper.getLevel(), spine.damageSources().indirectMagic(b, b), 4.0F);
            float v = v0 - spine.getHealth();
            helper.assertTrue(Math.abs(v - 2.0F) < 0.01F, "a blow of 4 on a vertebra costs 2 (x0.5), lost " + v);

            float p0 = plain.getHealth();
            plain.hurtServer(helper.getLevel(), plain.damageSources().magic(), 4.0F);
            float pl = p0 - plain.getHealth();
            helper.assertTrue(Math.abs(pl - 4.0F) < 0.01F, "magic with no place to land costs 4, lost " + pl);
            helper.succeed();
        });
    }

    /**
     * The vertebrae are parts of the one mob, not mobs of their own: a blow on the last one (4, so 2) comes off the
     * wyrm's own health, and the arena holds one wyrm entity and nothing else of it. Every part's box is inside the
     * mob's own bounding box, so a swing at any of them reaches it.
     */
    private static void onePool(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 0, 11, 0, 11);
        OssuaryWyrm w = stranded(helper, 9.5, 5.5);
        Pig striker = pig(helper, 3.1, 2, 4.0);
        helper.runAtTickTime(3, () -> {
            AABB[] boxes = w.partBoxes();
            for (AABB box : boxes) helper.assertTrue(w.getBoundingBox().inflate(1.0E-6).contains(box.getCenter())
                    && w.getBoundingBox().inflate(1.0E-6).intersects(box), "every part lies in the mob's own box: " + box);
            aim(striker, boxes[8].getCenter());
            helper.assertTrue(w.partHit(w.damageSources().indirectMagic(striker, striker)) == 8, "the blow is traced onto the last vertebra");
            float before = w.getHealth();
            w.hurtServer(helper.getLevel(), w.damageSources().indirectMagic(striker, striker), 4.0F);
            float lost = before - w.getHealth();
            helper.assertTrue(Math.abs(lost - 2.0F) < 0.01F, "a blow of 4 on the tail comes off the wyrm's health as 2, lost " + lost);
            AABB arena = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(11, 8, 11).inflate(1.0);
            int wyrms = helper.getLevel().getEntitiesOfClass(OssuaryWyrm.class, arena, e -> true).size();
            helper.assertTrue(wyrms == 1, "one wyrm entity, its vertebrae no entities of their own, found " + wyrms);
            helper.succeed();
        });
    }

    /**
     * Parts under the soul sand are out of reach, so they are no part of the mob's own box: swimming with all of it
     * under, its box is the skull's alone; halfway through the arc it is the union of exactly the parts that reach
     * above the surface, never reaching down to the buried tail.
     */
    private static void buriedParts(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 10, 3, 8);
        OssuaryWyrm w = wyrm(helper, 2.5, 5.5);
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // unaware of players in other arenas
        w.setYRot(-90.0F);                                            // facing +x, its leap along the arena
        double surface = surface(helper);
        helper.runAtTickTime(2, () -> {
            AABB skull = w.partBoxes()[0];
            helper.assertTrue(w.getBoundingBox().equals(skull), "all of it under, its box is the skull's: " + w.getBoundingBox() + ", skull " + skull);
            helper.assertTrue(w.getBoundingBox().maxY <= surface, "and it is under the surface, top " + w.getBoundingBox().maxY);
            helper.assertTrue(w.startPhase(Step.ARC, 40, null), "the arc starts");
        });
        helper.runAtTickTime(22, () -> {
            AABB expected = null;
            int exposed = 0;
            for (AABB box : w.partBoxes()) {
                if (box.maxY <= surface) continue;
                expected = expected == null ? box : expected.minmax(box);
                exposed++;
            }
            helper.assertTrue(exposed >= 4 && exposed < 9, "halfway through the arc part of it is out, " + exposed + " parts");
            AABB box = w.getBoundingBox();
            helper.assertTrue(expected != null && box.inflate(1.0E-6).contains(expected.getMinPosition()) && expected.inflate(1.0E-6).contains(box.getMinPosition())
                    && box.inflate(1.0E-6).contains(expected.getMaxPosition()) && expected.inflate(1.0E-6).contains(box.getMaxPosition()),
                    "its box is the union of the parts out of the sand: " + box + ", expected " + expected);
            helper.succeed();
        });
    }

    /** Health 50, attack 8, armour 4, a 0.8 x 0.8 hitbox per part: the skull and 8 vertebrae, 0.8 apart. */
    private static void stats(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 10, 3, 8);
        OssuaryWyrm w = wyrm(helper, 8.5, 5.5);
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // unaware of players in other arenas
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(w.getMaxHealth() - 50.0F) < 0.01F && Math.abs(w.getHealth() - 50.0F) < 0.01F,
                    "health 50, got " + w.getHealth() + "/" + w.getMaxHealth());
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.ATTACK_DAMAGE) - 8.0) < 0.01, "attack 8");
            helper.assertTrue(Math.abs(w.getAttributeValue(Attributes.ARMOR) - 4.0) < 0.01, "armour 4");
            helper.assertTrue(Math.abs(w.getBbWidth() - 0.8F) < 0.02F && Math.abs(w.getBbHeight() - 0.8F) < 0.02F,
                    "a part's hitbox is 0.8 x 0.8, got " + w.getBbWidth() + " x " + w.getBbHeight());
            AABB[] boxes = w.partBoxes();
            helper.assertTrue(boxes.length == 9, "a skull and 8 vertebrae, got " + boxes.length + " parts");
            for (AABB box : boxes) {
                helper.assertTrue(Math.abs(box.getXsize() - 0.8) < 0.001 && Math.abs(box.getYsize() - 0.8) < 0.001 && Math.abs(box.getZsize() - 0.8) < 0.001,
                        "each part 0.8 across, got " + box);
            }
            for (int i = 1; i < boxes.length; i++) {
                double gap = boxes[i].getCenter().distanceTo(boxes[i - 1].getCenter());
                helper.assertTrue(Math.abs(gap - 0.8) < 0.001, "parts 0.8 apart along its body, got " + gap);
            }
            helper.succeed();
        });
    }

    /** Swimming toward its strike spot through soul sand it covers exactly 0.3 blocks a tick, its feet at the surface. */
    private static void swimSpeedSoulSand(GameTestHelper helper) {
        swimSpeed(helper, Blocks.SOUL_SAND);
    }

    /** The same through soul soil: 0.3 blocks a tick. */
    private static void swimSpeedSoulSoil(GameTestHelper helper) {
        swimSpeed(helper, Blocks.SOUL_SOIL);
    }

    /**
     * Its prey (a pig) is a block behind it, so it swims 5.4 blocks away to the spot 6.37 from the pig, along a strip of
     * {@code medium}. Every step it takes while under way is 0.3 blocks.
     */
    private static void swimSpeed(GameTestHelper helper, Block medium) {
        floor(helper, medium, 0, 11, 4, 6);
        OssuaryWyrm w = wyrm(helper, 9.5, 5.5);
        Pig pig = pig(helper, 10.5, 2, 5.5);
        double spotX = at(helper, 10.5 - 6.37, 0, 0).x;
        double top = surface(helper);
        List<Double> stepsX = new ArrayList<>();
        double[] last = {Double.NaN}, depth = {0.0};
        helper.runAtTickTime(1, () -> w.setTarget(pig));
        helper.onEachTick(() -> {
            boolean swimming = w.core().idle() && w.submerged() && w.getTarget() == pig && w.getX() - spotX > 0.35;
            if (swimming && !Double.isNaN(last[0])) {
                stepsX.add(last[0] - w.getX());
                depth[0] = Math.max(depth[0], Math.abs(w.getY() - top));
            }
            last[0] = swimming ? w.getX() : Double.NaN;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepsX.size() >= 15, "it swam for 15 ticks or more, saw " + stepsX.size());
            double mean = stepsX.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double spread = stepsX.stream().mapToDouble(s -> Math.abs(s - 0.3)).max().orElse(1);
            helper.assertTrue(Math.abs(mean - 0.3) < 0.005, "it swims 0.3 blocks a tick, mean " + mean);
            helper.assertTrue(spread < 0.01, "at a steady pace, worst step off by " + spread);
            helper.assertTrue(depth[0] < 0.01, "its feet stay at the surface (its body under it), off by up to " + depth[0]);
            helper.assertTrue(w.core().in(Step.TELL) || !w.core().idle(), "at the spot it rumbled, phase " + w.phase());
        });
    }

    /**
     * The body follows the skull: through the arc, every vertebra out of the sand lies on the path the skull took (within
     * 0.1 of it), and at one moment the skull and at least 4 vertebrae are out of the sand together.
     */
    private static void chainFollows(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 10, 3, 8);
        OssuaryWyrm w = wyrm(helper, 2.5, 5.5);
        Pig pig = pig(helper, 8.87, 2, 5.5);
        double top = surface(helper);
        List<Vec3> trail = new ArrayList<>();
        double[] worst = {0.0};
        int[] mostOut = {0}, checked = {0};
        helper.runAtTickTime(1, () -> w.setTarget(pig));
        helper.onEachTick(() -> {
            if (!w.core().in(Step.ARC) && !w.core().in(Step.FOLLOW) && !w.core().in(Step.TELL)) return;
            AABB[] boxes = w.partBoxes();
            trail.add(boxes[0].getCenter());
            int out = 0;
            for (int i = 1; i < boxes.length; i++) {
                Vec3 c = boxes[i].getCenter();
                if (c.y <= top) continue;
                out++;
                checked[0]++;
                worst[0] = Math.max(worst[0], distanceToPath(trail, c));
            }
            if (w.skullOut()) mostOut[0] = Math.max(mostOut[0], out);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(w.core().idle() && trail.size() > 40, "the leap is over, " + trail.size() + " ticks of it seen");
            helper.assertTrue(checked[0] > 50, "vertebrae were out of the sand, " + checked[0] + " looks");
            helper.assertTrue(worst[0] < 0.1, "every vertebra out of the sand is on the skull's path, worst " + worst[0] + " off");
            helper.assertTrue(mostOut[0] >= 4, "the skull and at least 4 vertebrae out at once, best " + mostOut[0]);
        });
    }

    /** How far {@code p} is from the polyline through {@code path}. */
    private static double distanceToPath(List<Vec3> path, Vec3 p) {
        double best = Double.MAX_VALUE;
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1), ab = path.get(i).subtract(a);
            double len = ab.lengthSqr();
            double t = len < 1.0E-9 ? 0.0 : Mth.clamp(p.subtract(a).dot(ab) / len, 0.0, 1.0);
            best = Math.min(best, a.add(ab.scale(t)).distanceTo(p));
        }
        return best;
    }

    /**
     * A player on netherrack beside the soul sand is out of its reach: the arc could not go back under where they stand.
     * It swims up to the edge of its soul sand and stops, never rumbles, never leaves it, and the player takes nothing.
     */
    private static void soulSandOnly(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 5, 1, 10);
        floor(helper, Blocks.NETHERRACK, 6, 10, 1, 10);
        OssuaryWyrm w = wyrm(helper, 2.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(8.5, 2, 5.5));
        float hp = p.getHealth();
        double edge = at(helper, 6, 0, 0).x;
        double[] furthest = {Double.NEGATIVE_INFINITY};
        boolean[] rumbled = {false}, left = {false}, hunted = {false};
        helper.onEachTick(() -> {
            furthest[0] = Math.max(furthest[0], w.getX());
            rumbled[0] |= !w.core().idle();
            left[0] |= !w.submerged();
            hunted[0] |= w.getTarget() == p;
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(hunted[0], "the player 6 blocks away is its prey");
            helper.assertTrue(w.strikeSpot(p) == null, "no spot on its soul sand reaches a player on netherrack");
            helper.assertTrue(furthest[0] > edge - 1.0, "it swam up to the edge of its soul sand, x " + furthest[0] + " (netherrack from " + edge + ")");
            helper.assertTrue(furthest[0] < edge - 0.3, "it never crossed onto the netherrack, x " + furthest[0]);
            helper.assertTrue(!rumbled[0] && !left[0], "it never rumbled and never left the soul sand");
            helper.assertTrue(p.getHealth() >= hp, "the player took nothing");
            helper.succeed();
        });
    }

    /** Stranded on netherrack 3 blocks from soul sand, it drags itself back and sinks in. */
    private static void crawlsBack(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 3, 1, 10);
        floor(helper, Blocks.NETHERRACK, 4, 10, 1, 10);
        OssuaryWyrm w = helper.spawn(ModMobs.OSSUARY_WYRM.get(), new Vec3(6.5, 2, 5.5));
        w.removeFreeWill();
        boolean[] strandedSeen = {false};
        helper.onEachTick(() -> strandedSeen[0] |= w.core().flag(OssuaryWyrm.STRANDED));
        helper.succeedWhen(() -> {
            helper.assertTrue(strandedSeen[0], "on the netherrack it was stranded");
            helper.assertTrue(w.submerged(), "it got back under the soul sand, x " + w.getX());
        });
    }

    /** Its home: the biome modifier adds it to soul sand valleys at weight 4, in groups of 1. */
    private static void spawns(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/ossuary_wyrm"));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key).map(Holder::value).orElse(null);
        if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) {
            helper.fail("no spawn biome modifier for the Ossuary Wyrm: " + modifier);
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> spawners = add.spawners().unwrap();
        helper.assertTrue(spawners.size() == 1 && spawners.getFirst().value().type() == ModMobs.OSSUARY_WYRM.get(), "one Ossuary Wyrm spawner, " + spawners);
        Weighted<MobSpawnSettings.SpawnerData> spawner = spawners.getFirst();
        helper.assertTrue(spawner.weight() == 4, "spawn weight 4, got " + spawner.weight());
        helper.assertTrue(spawner.value().count().minInclusive() == 1 && spawner.value().count().maxInclusive() == 1,
                "groups of 1, got " + spawner.value().count().minInclusive() + " to " + spawner.value().count().maxInclusive());
        helper.assertTrue(add.biomes().contains(access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.SOUL_SAND_VALLEY)), "soul sand valleys are its home");
        helper.succeed();
    }

    /** A listener that cancels the "bite" step lets the leap happen but leaves its prey unhurt; it ends back under. */
    private static void biteCancel(GameTestHelper helper) {
        floor(helper, Blocks.SOUL_SAND, 1, 10, 3, 8);
        OssuaryWyrm w = wyrm(helper, 2.5, 5.5);
        Pig pig = pig(helper, 8.87, 2, 5.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == w && e.step().equals("bite")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] leapt = {false};
        helper.runAtTickTime(1, () -> w.setTarget(pig));
        helper.onEachTick(() -> leapt[0] |= w.core().in(Step.FOLLOW));
        helper.succeedWhen(() -> {
            helper.assertTrue(leapt[0] && w.core().idle() && w.submerged(), "it leapt and went back under, phase " + w.phase());
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "a cancelled bite hurts nothing");
            helper.assertTrue(Math.abs(w.getX() - at(helper, 9.5, 0, 0).x) < 0.05, "it swims on from where it went back under, 7 blocks on: x " + w.getX());
        });
    }
}
