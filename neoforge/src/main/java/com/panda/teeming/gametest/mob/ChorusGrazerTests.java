package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.ChorusGrazer;
import com.panda.teeming.mob.end.ChorusGrazer.Step;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Chorus Grazer: a creature, HP 24, Atk 0, Arm 0, 1.1 x 1.3, no tell. Startled (hurt, or a survival player sprinting
 * within 3 blocks), every grazer within 16 blocks blinks 4-8 blocks away onto safe ground (never over a drop past 4
 * blocks, the void, lava, or a wall) and stays skittish for 100 ticks: no shears, no feeding. Shears take 1-2 purpur
 * fleece; only chorus fruit grows it back: a shorn grazer walks to a chorus fruit lying within 8 blocks and eats it
 * (30 ticks of chewing). Chorus fruit by hand: two in love bring a calf. End highlands, weight 6, groups of 2-4; Pale
 * Grazer; purpur fleece. Expected values are written out as numbers.
 *
 * <p>A startle reaches grazers 16 blocks off, past the walls of this test's arena: every test that startles a herd keeps
 * it inside its arena (a listener vetoes the step for grazers outside it, when the threat is the test's own). The arena
 * is 12 blocks across inside a barrier wall, with the ground round it 2 blocks below its floor. A blink that turns aside
 * at full length (up to 90 degrees off, as it does when the way ahead is unsafe or the floor runs out) can clear the
 * wall and land on that ground, which is a legal spot. So a test whose blink can turn aside stands on a floor raised to
 * y 3 ({@link #raise}): the ground round the arena then lies 5 blocks down, past the 4-block drop, and every blink, of
 * any length, lands on the raised floor. A blink straight east from x 2.5 needs no raised floor: it lands 4 to 8 blocks
 * east, on the floor, at every length. Grazers have their goals cleared unless a test needs them (no wandering, shying,
 * eating or following); the blink, love and breeding still run.
 */
public final class ChorusGrazerTests {
    private ChorusGrazerTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("chorus_grazer_tell", ChorusGrazerTests::noTellNeverAttacks);
        TeemingGameTests.timeLimit("chorus_grazer_tell", 130);
        out.accept("chorus_grazer_counter", ChorusGrazerTests::shearCalmly);
        out.accept("chorus_grazer_failure", ChorusGrazerTests::startledHerd);
        TeemingGameTests.timeLimit("chorus_grazer_failure", 130);
        out.accept("chorus_grazer_blink_range", ChorusGrazerTests::blinkRange);
        out.accept("chorus_grazer_herd_range", ChorusGrazerTests::herdRange);
        out.accept("chorus_grazer_skittish_length", ChorusGrazerTests::skittishLength);
        TeemingGameTests.timeLimit("chorus_grazer_skittish_length", 130);
        out.accept("chorus_grazer_sprint_startle", ChorusGrazerTests::sprintStartle);
        out.accept("chorus_grazer_shy", ChorusGrazerTests::shy);
        out.accept("chorus_grazer_safe_void", ChorusGrazerTests::safeVoid);
        out.accept("chorus_grazer_safe_lava", ChorusGrazerTests::safeLava);
        out.accept("chorus_grazer_no_safe_spot", ChorusGrazerTests::noSafeSpot);
        out.accept("chorus_grazer_fleece_count", ChorusGrazerTests::fleeceCount);
        out.accept("chorus_grazer_eats_fruit", ChorusGrazerTests::eatsFruit);
        TeemingGameTests.timeLimit("chorus_grazer_eats_fruit", 160);
        out.accept("chorus_grazer_fruit_range", ChorusGrazerTests::fruitRange);
        out.accept("chorus_grazer_hand_fed", ChorusGrazerTests::handFed);
        out.accept("chorus_grazer_startled_meal", ChorusGrazerTests::startledMeal);
        out.accept("chorus_grazer_veto", ChorusGrazerTests::veto);
        out.accept("chorus_grazer_breed", ChorusGrazerTests::breed);
        TeemingGameTests.timeLimit("chorus_grazer_breed", 140);
        out.accept("chorus_grazer_calf", ChorusGrazerTests::calf);
        out.accept("chorus_grazer_save_load", ChorusGrazerTests::saveLoad);
        out.accept("chorus_grazer_herd_look", ChorusGrazerTests::herdLook);
        out.accept("chorus_grazer_stats", ChorusGrazerTests::stats);
        out.accept("chorus_grazer_spawns", ChorusGrazerTests::spawns);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** The height grazers stand at on the raised floor ({@link #raise}). */
    private static final int RAISED = 4;

    /**
     * Fills the arena with stone up to y 3, so grazers stand at y {@link #RAISED}: the ground round the arena then lies 5
     * blocks below the floor, past the blink's 4-block drop, and no blink can land outside.
     */
    private static void raise(GameTestHelper helper) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) for (int y = 1; y < RAISED; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }

    /** A grown, fleeced grazer of {@code look} at (x, 1, z), goals cleared, removed when the test ends. */
    private static ChorusGrazer grazer(GameTestHelper helper, double x, double z, String look) {
        return grazer(helper, new Vec3(x, 1, z), look);
    }

    private static ChorusGrazer grazer(GameTestHelper helper, double x, double z) {
        return grazer(helper, x, z, "base");
    }

    /** A grown, fleeced grazer of {@code look} at {@code at}, goals cleared, removed when the test ends. */
    private static ChorusGrazer grazer(GameTestHelper helper, Vec3 at, String look) {
        ChorusGrazer g = wild(helper, at);
        g.core().setLook(look, true);
        g.removeFreeWill();
        return g;
    }

    /** A grazer that keeps its goals, removed when the test ends. */
    private static ChorusGrazer wild(GameTestHelper helper, double x, double z) {
        return wild(helper, new Vec3(x, 1, z));
    }

    private static ChorusGrazer wild(GameTestHelper helper, Vec3 at) {
        ChorusGrazer g = helper.spawn(ModMobs.CHORUS_GRAZER.get(), at);
        TestPlayers.whenDone(helper, g::discard);
        return g;
    }

    /** A still pig: the threat that hurts the grazers. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        return pig(helper, new Vec3(x, 1, z));
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    private static ServerPlayer holding(GameTestHelper helper, Vec3 at, ItemStack stack) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return p;
    }

    private static InteractionResult use(ChorusGrazer g, ServerPlayer p) {
        return g.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    /** Hurts {@code g} for {@code amount} as a blow from {@code by}. */
    private static boolean hit(GameTestHelper helper, ChorusGrazer g, LivingEntity by, float amount) {
        return g.hurtServer(helper.getLevel(), g.damageSources().mobAttack(by), amount);
    }

    /** Keeps a startle {@code cause} sets off inside this arena: grazers outside it (other tests') stay put. */
    private static void contain(GameTestHelper helper, Entity cause) {
        Consumer<MechanicEvent> keep = e -> {
            if (e.step().equals("skittish") && e.target() == cause && !helper.getBounds().contains(e.mob().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
    }

    /** Puts the given grazers on a team of their own that never pushes or is pushed, removed when the test ends. */
    private static void noPushing(GameTestHelper helper, ChorusGrazer... herd) {
        Scoreboard board = helper.getLevel().getScoreboard();
        PlayerTeam team = board.addPlayerTeam("teeming_herd_" + herd[0].getStringUUID());
        team.setCollisionRule(Team.CollisionRule.NEVER);
        for (ChorusGrazer g : herd) board.addPlayerToTeam(g.getScoreboardName(), team);
        TestPlayers.whenDone(helper, () -> board.removePlayerTeam(team));
    }

    /** Vetoes {@code step} for the given grazers for the rest of the test. */
    private static void veto(GameTestHelper helper, String step, ChorusGrazer... herd) {
        Set<ChorusGrazer> mine = Set.of(herd);
        Consumer<MechanicEvent> listener = e -> {
            if (e.step().equals(step) && e.mob() instanceof ChorusGrazer g && mine.contains(g)) e.cancel();
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
    }

    /** A chorus fruit lying still at (x, 1, z), removed when the test ends. */
    private static ItemEntity fruit(GameTestHelper helper, double x, double z) {
        Vec3 at = helper.absoluteVec(new Vec3(x, 1, z));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.CHORUS_FRUIT));
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        TestPlayers.whenDone(helper, item::discard);
        return item;
    }

    /** Items of {@code which} lying in this arena, counted by the item; {@code clear} also removes them. */
    private static int lying(GameTestHelper helper, net.minecraft.world.item.Item which, boolean clear) {
        int n = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), i -> i.isAlive() && i.getItem().is(which))) {
            n += item.getItem().getCount();
            if (clear) item.discard();
        }
        return n;
    }

    private static int fleece(GameTestHelper helper, boolean clear) {
        return lying(helper, ModMaterials.PURPUR_FLEECE.get(), clear);
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** One "skittish" step a grazer saw: the tick, the threat and where it stood; read as text once a listener could veto it. */
    private record Startle(long tick, String by, MechanicEvent event) {
        @Override
        public String toString() {
            return "tick " + tick + " by " + by + (event.cancelled() ? " (vetoed)" : "");
        }
    }

    /**
     * Notes each "skittish" step of the given grazers, for failure messages: the tick, the threat and where it stood
     * (relative to this arena, so a threat from a neighbouring arena shows), and whether a listener vetoed it.
     */
    private static Map<ChorusGrazer, List<Startle>> startles(GameTestHelper helper, ChorusGrazer... herd) {
        Map<ChorusGrazer, List<Startle>> seen = new HashMap<>();
        Set<ChorusGrazer> mine = Set.of(herd);
        Consumer<MechanicEvent> listener = e -> {
            if (!e.step().equals("skittish") || !(e.mob() instanceof ChorusGrazer g) || !mine.contains(g)) return;
            String by = e.target() == null ? "nothing" : e.target().getType().getDescriptionId() + " at " + helper.relativeVec(e.target().position());
            seen.computeIfAbsent(g, k -> new ArrayList<>()).add(new Startle(helper.getTick(), by, e));
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return seen;
    }

    /**
     * It blinked from {@code from} to where it stands: 4 to 8 blocks across the ground, onto the arena floor (standing at
     * y {@code floor}), inside the arena, with ground under every block column its hitbox covers and nothing in its way.
     */
    private static void landed(GameTestHelper helper, ChorusGrazer g, Vec3 from, double floor, String who, Map<ChorusGrazer, List<Startle>> log) {
        Vec3 rel = helper.relativeVec(g.position());
        String seen = " (from " + helper.relativeVec(from) + " to " + rel + ", startled " + log.get(g) + ")";
        double went = flat(g.position(), from);
        helper.assertTrue(went >= 4.0 - 1.0E-6 && went <= 8.0 + 1.0E-6, who + " blinks 4 to 8 blocks, went " + went + seen);
        helper.assertTrue(Math.abs(rel.y - floor) < 0.01, who + " stands on the floor (y " + floor + "), y " + rel.y + seen);
        helper.assertTrue(helper.getBounds().contains(g.position()), who + " stays in the arena" + seen);
        AABB box = g.getBoundingBox();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                BlockPos under = new BlockPos(x, Mth.floor(g.getY()) - 1, z);
                helper.assertTrue(!helper.getLevel().getBlockState(under).getCollisionShape(helper.getLevel(), under).isEmpty(),
                        who + " has ground under every column it covers, none at " + helper.relativePos(under) + seen);
            }
        }
        helper.assertTrue(helper.getLevel().noCollision(g), who + " is not inside a block" + seen);
    }

    // ---- the tell, the counter, failing it -------------------------------------------------------------------------

    /**
     * No tell, because it never attacks: a survival player hits it for 4 (armour 0 takes all 4), it and its herdmate
     * blink away, and for the next 105 ticks neither picks a target, nothing posts a step but "skittish" and "blink",
     * and the player loses no health. Its attack is 0. Then both are calm again.
     */
    private static void noTellNeverAttacks(GameTestHelper helper) {
        ChorusGrazer g = grazer(helper, 2.5, 4.5);
        ChorusGrazer mate = grazer(helper, 2.5, 6.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(0.5, 1, 5.5));
        contain(helper, p);
        List<String> steps = new ArrayList<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == g || e.mob() == mate) steps.add(e.step());
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        helper.runAtTickTime(5, () -> {
            float before = g.getHealth();
            g.hurtServer(helper.getLevel(), g.damageSources().playerAttack(p), 4.0F);
            float lost = before - g.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F) < 0.01F, "armour 0 takes all of a 4 hit, lost " + lost);
            helper.assertTrue(g.skittish() && mate.skittish(), "both turn skittish, phases " + g.phase() + ", " + mate.phase());
            helper.assertTrue(g.blinks() == 1 && mate.blinks() == 1, "and both blink away, " + g.blinks() + ", " + mate.blinks());
        });
        helper.onEachTick(() -> {
            if (helper.getTick() <= 5) return;
            helper.assertTrue(g.getTarget() == null && mate.getTarget() == null, "neither picks a target");
            helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "the player loses nothing, health " + p.getHealth());
        });
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(steps.contains("skittish") && steps.contains("blink"), "the startle was posted: " + steps);
            helper.assertTrue(steps.stream().allMatch(s -> s.equals("skittish") || s.equals("blink")), "and nothing but it: " + steps);
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.01, "attack 0");
            helper.assertTrue(g.core().idle() && mate.core().idle(), "both are calm again, phases " + g.phase() + ", " + mate.phase());
            helper.succeed();
        });
    }

    /**
     * The counter: a survival player walks up (not sprinting, 2 blocks off) and shears a calm grazer: 1 or 2 purpur
     * fleece drop, the shears lose 1 durability, it is shorn and shakes its back; neither it nor the grazer beside it is
     * startled. Shears on the shorn one do nothing.
     */
    private static void shearCalmly(GameTestHelper helper) {
        ChorusGrazer g = grazer(helper, 5.5, 5.5);
        ChorusGrazer calm = grazer(helper, 8.5, 5.5);
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 3.5), new ItemStack(Items.SHEARS));
        int[] dropped = {0};
        helper.runAtTickTime(5, () -> {
            InteractionResult result = use(g, p);
            helper.assertTrue(result.consumesAction(), "the shears take, got " + result);
            helper.assertTrue(g.shorn() && g.core().in(Step.SHEAR), "it is shorn and shakes its back, phase " + g.phase());
            helper.assertTrue(p.getMainHandItem().getDamageValue() == 1, "the shears lose 1, damage " + p.getMainHandItem().getDamageValue());
            InteractionResult again = use(g, p);
            helper.assertTrue(!again.consumesAction(), "shears on the shorn grazer do nothing, got " + again);
            helper.assertTrue(p.getMainHandItem().getDamageValue() == 1, "and cost nothing, damage " + p.getMainHandItem().getDamageValue());
        });
        helper.runAtTickTime(7, () -> {
            dropped[0] = fleece(helper, false);
            helper.assertTrue(dropped[0] >= 1 && dropped[0] <= 2, "1 or 2 purpur fleece drop, got " + dropped[0]);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(g.core().idle() && g.shorn(), "the shake is over and it stays shorn, phase " + g.phase());
            helper.assertTrue(calm.core().idle() && !calm.shorn(), "its neighbour stayed calm, phase " + calm.phase());
            helper.assertTrue(g.blinks() == 0 && calm.blinks() == 0, "a walking player startles neither");
            helper.assertTrue(fleece(helper, false) == dropped[0], "no more fleece came, " + fleece(helper, false));
            helper.succeed();
        });
    }

    /**
     * Failing it: a survival player with shears sprints up to a herd of three (2 blocks from the nearest). All three
     * blink 4-8 blocks away, each onto the floor, and are skittish for 100 ticks: the shears do nothing (and lose
     * nothing) and chorus fruit isn't taken. Once the 100 ticks are up they are calm and the shears work again. The
     * outer two flee at an angle, so their blinks can turn aside: on the raised floor. Two blinks can land side by side,
     * a block apart, and a grazer is 1.1 wide, so the herd stands on a team that never pushes: the landing is checked a
     * tick after the startle, and a push in that tick would move a grazer from where its blink put it.
     */
    private static void startledHerd(GameTestHelper helper) {
        raise(helper);
        ChorusGrazer a = grazer(helper, new Vec3(2.5, RAISED, 4.3), "base");
        ChorusGrazer b = grazer(helper, new Vec3(2.5, RAISED, 5.5), "base");
        ChorusGrazer c = grazer(helper, new Vec3(2.5, RAISED, 6.7), "base");
        ChorusGrazer[] herd = {a, b, c};
        noPushing(helper, herd);
        Map<ChorusGrazer, List<Startle>> log = startles(helper, herd);
        ServerPlayer p = holding(helper, new Vec3(0.5, RAISED, 5.5), new ItemStack(Items.SHEARS));
        contain(helper, p);
        Vec3[] from = new Vec3[3];
        long[] startled = {-1};
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 4) for (int i = 0; i < 3; i++) from[i] = herd[i].position();
            if (t == 5) p.setSprinting(true);
            if (startled[0] < 0 && a.skittish() && b.skittish() && c.skittish()) {
                startled[0] = t;
                p.setSprinting(false);
                for (int i = 0; i < 3; i++) landed(helper, herd[i], from[i], RAISED, "grazer " + i, log);
            }
            if (startled[0] > 0 && t == startled[0] + 5) {
                InteractionResult shorn = use(b, p);
                helper.assertTrue(!shorn.consumesAction() && !b.shorn(), "a skittish grazer can't be sheared, got " + shorn);
                helper.assertTrue(p.getMainHandItem().getDamageValue() == 0, "the shears lose nothing");
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHORUS_FRUIT, 3));
                helper.assertTrue(!use(c, p).consumesAction() && p.getMainHandItem().getCount() == 3, "nor will it take chorus fruit");
                helper.assertTrue(c.love() == 0, "and falls in love with no one");
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            }
            if (startled[0] > 0 && t == startled[0] + 101) {
                helper.assertTrue(a.core().idle() && b.core().idle() && c.core().idle(), "100 ticks on, the herd is calm");
                helper.assertTrue(use(b, p).consumesAction() && b.shorn(), "and the shears work again");
                helper.succeed();
            }
        });
        helper.runAtTickTime(9, () -> helper.assertTrue(startled[0] > 0 && startled[0] <= 7, "the sprint startles the herd at once, at " + startled[0]));
    }

    // ---- the blink's numbers -----------------------------------------------------------------------------------------

    /**
     * The blink's length: 20 startles of a grazer brought back to the same spot each time; every blink goes 4 to 8
     * blocks straight away from the pig (east), onto the floor; short ones (under 6) and long ones (over 6) both come up.
     */
    private static void blinkRange(GameTestHelper helper) {
        ChorusGrazer g = grazer(helper, 2.5, 5.5);
        Pig pig = pig(helper, 0.5, 5.5);
        contain(helper, pig);
        Map<ChorusGrazer, List<Startle>> log = startles(helper, g);
        Vec3 home = helper.absoluteVec(new Vec3(2.5, 1, 5.5));
        List<Double> went = new ArrayList<>();
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t < 3 || t >= 43) return;
            if (t % 2 == 1) {
                g.core().end();
                g.teleportTo(home.x, home.y, home.z);
                return;
            }
            Vec3 from = g.position();
            helper.assertTrue(g.startle(helper.getLevel(), pig, pig.position()) == 1, "the grazer is startled");
            landed(helper, g, from, 1, "trial " + went.size(), log);
            helper.assertTrue(g.getX() - from.x > 3.99 && Math.abs(g.getZ() - from.z) < 0.01, "straight away from the pig, went " + g.position().subtract(from));
            went.add(flat(g.position(), from));
        });
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(went.size() == 20, "20 blinks, counted " + went.size());
            helper.assertTrue(went.stream().anyMatch(d -> d < 6.0) && went.stream().anyMatch(d -> d > 6.0), "short and long blinks both: " + went);
            helper.assertTrue(g.blinks() == 20, "each one a blink, " + g.blinks());
            helper.succeed();
        });
    }

    /**
     * The herd is every grazer within 16 blocks of the startled one: one 15.56 blocks off turns skittish, one 16.34
     * blocks off (on a ledge in the far corner) doesn't. All three stand still with no AI and their blinks vetoed, so
     * only the phase shows it.
     */
    private static void herdRange(GameTestHelper helper) {
        ChorusGrazer hurt = grazer(helper, 0.5, 0.5);
        ChorusGrazer near = grazer(helper, 11.5, 11.5);
        helper.setBlock(new BlockPos(11, 5, 11), Blocks.STONE);
        ChorusGrazer far = helper.spawn(ModMobs.CHORUS_GRAZER.get(), new Vec3(11.5, 6, 11.5));
        far.core().setLook("base", true);
        TestPlayers.whenDone(helper, far::discard);
        for (ChorusGrazer g : List.of(hurt, near, far)) g.setNoAi(true);
        veto(helper, "blink", hurt, near, far);
        Pig pig = pig(helper, 2.5, 0.5);
        contain(helper, pig);
        helper.runAtTickTime(3, () -> hit(helper, hurt, pig, 1.0F));
        helper.runAtTickTime(4, () -> {
            double dNear = hurt.distanceTo(near);
            double dFar = hurt.distanceTo(far);
            helper.assertTrue(Math.abs(dNear - 15.56) < 0.02 && Math.abs(dFar - 16.34) < 0.02, "the grazers stand 15.56 and 16.34 off, got " + dNear + ", " + dFar);
            helper.assertTrue(hurt.skittish(), "the hurt one is startled");
            helper.assertTrue(near.skittish(), "so is the one 15.56 blocks off, phase " + near.phase());
            helper.assertTrue(far.core().idle(), "the one 16.34 blocks off stays calm, phase " + far.phase());
            helper.succeed();
        });
    }

    /**
     * Skittish for 100 ticks, counted tick by tick, for the hurt grazer and its herdmate alike. A second blow 20 ticks
     * in neither blinks it again nor starts the 100 ticks over; then both are calm.
     */
    private static void skittishLength(GameTestHelper helper) {
        ChorusGrazer a = grazer(helper, 2.5, 4.5);
        ChorusGrazer b = grazer(helper, 2.5, 6.5);
        Pig pig = pig(helper, 0.5, 5.5);
        contain(helper, pig);
        int[] ran = {0, 0};
        Vec3[] at = new Vec3[1];
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 3) hit(helper, a, pig, 1.0F);
            if (t == 23) {
                at[0] = a.position();
                a.setInvulnerableTime(0);
                helper.assertTrue(hit(helper, a, pig, 1.0F), "the second blow lands");
                helper.assertTrue(flat(a.position(), at[0]) < 0.01 && a.blinks() == 1, "and doesn't blink it again, blinks " + a.blinks());
            }
            if (a.skittish()) ran[0]++;
            if (b.skittish()) ran[1]++;
        });
        helper.runAtTickTime(3 + 100 + 10, () -> {
            helper.assertTrue(ran[0] == 100 && ran[1] == 100, "skittish for 100 ticks, ran " + ran[0] + " and " + ran[1]);
            helper.assertTrue(a.core().idle() && b.core().idle(), "then both are calm");
            helper.succeed();
        });
    }

    /**
     * Sprinting within 3 blocks startles it; nothing else a player does near it does. A creative player sprinting 1.5
     * blocks off: calm. The same player in survival walking 1.5 off: calm. Sprinting 3.2 off: calm. Sprinting 2.8 off:
     * it blinks away and turns skittish. From x 3.5 a long blink east would cross the arena's edge and turns aside: on
     * the raised floor.
     */
    private static void sprintStartle(GameTestHelper helper) {
        raise(helper);
        ChorusGrazer g = grazer(helper, new Vec3(3.5, RAISED, 5.5), "base");
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(2.0, RAISED, 5.5));
        contain(helper, p);
        Map<ChorusGrazer, List<Startle>> log = startles(helper, g);
        Vec3 home = helper.absoluteVec(new Vec3(3.5, RAISED, 5.5));
        long[] startled = {-1};
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 2) {
                p.setGameMode(GameType.CREATIVE);
                p.setSprinting(true);
            }
            if (t == 14) {
                p.setGameMode(GameType.SURVIVAL);
                p.setSprinting(false);
            }
            if (t == 26) {
                p.snapTo(helper.absoluteVec(new Vec3(0.3, RAISED, 5.5)));
                p.setSprinting(true);
            }
            if (t == 38) p.snapTo(helper.absoluteVec(new Vec3(0.7, RAISED, 5.5)));
            if (t > 2 && t < 38) {
                helper.assertTrue(g.core().idle() && flat(g.position(), home) < 0.01, "calm at tick " + t + ", phase " + g.phase());
                if (t == 37) helper.assertTrue(Math.abs(p.distanceTo(g) - 3.2) < 0.01, "3.2 blocks off, got " + p.distanceTo(g));
            }
            if (t == 38) helper.assertTrue(Math.abs(p.distanceTo(g) - 2.8) < 0.01, "2.8 blocks off, got " + p.distanceTo(g));
            if (startled[0] < 0 && g.skittish()) startled[0] = t;
        });
        helper.runAtTickTime(44, () -> {
            helper.assertTrue(startled[0] >= 38 && startled[0] <= 40, "sprinting 2.8 off startles it, at " + startled[0]);
            landed(helper, g, home, RAISED, "the grazer", log);
            helper.assertTrue(g.getX() > home.x, "away from the sprinter (east)");
            helper.succeed();
        });
    }

    /**
     * A skittish grazer shies from a survival player within 6 blocks: with its blink vetoed it stays where it was
     * startled, 3 blocks from the player, and 30 ticks later it has walked off to at least 4.5 blocks.
     */
    private static void shy(GameTestHelper helper) {
        ChorusGrazer g = wild(helper, 5.5, 6.5);
        g.core().setLook("base", true);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 3.5));
        contain(helper, p);
        veto(helper, "blink", g);
        double[] start = {0};
        Vec3 spot = helper.absoluteVec(new Vec3(5.5, 1, 6.5));
        helper.runAtTickTime(2, () -> {
            g.teleportTo(spot.x, spot.y, spot.z);
            helper.assertTrue(g.startle(helper.getLevel(), p, p.position()) == 1 && g.skittish(), "startled, phase " + g.phase());
            start[0] = flat(g.position(), p.position());
        });
        helper.runAtTickTime(32, () -> {
            double now = flat(g.position(), p.position());
            helper.assertTrue(Math.abs(start[0] - 3.0) < 0.01, "it began 3 blocks off, " + start[0]);
            helper.assertTrue(now >= 4.5, "and shied off to at least 4.5, now " + now);
            helper.assertTrue(g.skittish(), "still skittish");
            helper.succeed();
        });
    }

    /**
     * Never over the void: the raised floor where every eastward blink would land is dug out 7 deep (past its 4-block
     * reach, as the void is). It still blinks 4 to 8 blocks, but onto the floor beside the pit, with ground under every
     * column it covers.
     */
    private static void safeVoid(GameTestHelper helper) {
        raise(helper);
        for (int x = 5; x <= 11; x++) for (int z = 2; z <= 9; z++) for (int y = RAISED - 1; y >= RAISED - 7; y--) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        ChorusGrazer g = grazer(helper, new Vec3(2.5, RAISED, 5.5), "base");
        Pig pig = pig(helper, new Vec3(0.5, RAISED, 5.5));
        contain(helper, pig);
        Map<ChorusGrazer, List<Startle>> log = startles(helper, g);
        helper.runAtTickTime(2, () -> {
            Vec3 from = g.position();
            hit(helper, g, pig, 1.0F);
            helper.assertTrue(g.blinks() == 1, "it still blinks");
            landed(helper, g, from, RAISED, "the grazer", log);
            Vec3 rel = helper.relativeVec(g.position());
            helper.assertTrue(rel.x < 5.0 - 0.55 || rel.z < 2.0 - 0.55 || rel.z > 10.0 + 0.55, "clear of the pit, at " + rel);
            helper.succeed();
        });
    }

    /** Lava where the blink would land, in the raised floor: it lands elsewhere, on the floor, never in or beside the lava. */
    private static void safeLava(GameTestHelper helper) {
        raise(helper);
        for (int x = 6; x <= 11; x++) for (int z = 3; z <= 8; z++) helper.setBlock(new BlockPos(x, RAISED - 1, z), Blocks.LAVA);
        ChorusGrazer g = grazer(helper, new Vec3(2.5, RAISED, 5.5), "base");
        Pig pig = pig(helper, new Vec3(0.5, RAISED, 5.5));
        contain(helper, pig);
        Map<ChorusGrazer, List<Startle>> log = startles(helper, g);
        helper.runAtTickTime(2, () -> {
            Vec3 from = g.position();
            hit(helper, g, pig, 1.0F);
            helper.assertTrue(g.blinks() == 1, "it still blinks");
            landed(helper, g, from, RAISED, "the grazer", log);
            helper.assertTrue(!g.isInLava(), "not in lava");
            helper.succeed();
        });
    }

    /** On a pillar 5 high nothing in reach is safe (every drop is past 4): it stays, takes the hit and turns skittish. */
    private static void noSafeSpot(GameTestHelper helper) {
        for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.STONE);
        ChorusGrazer g = helper.spawn(ModMobs.CHORUS_GRAZER.get(), new Vec3(5.5, 6, 5.5));
        TestPlayers.whenDone(helper, g::discard);
        g.removeFreeWill();
        g.setAge(-1000);                                       // a calf: its half-size body fits on the pillar's top
        Pig pig = pig(helper, 3.5, 5.5);
        contain(helper, pig);
        helper.runAtTickTime(4, () -> {
            Vec3 from = g.position();
            float before = g.getHealth();
            hit(helper, g, pig, 1.0F);
            helper.assertTrue(flat(g.position(), from) < 0.5, "it stays on the pillar, moved " + flat(g.position(), from));
            helper.assertTrue(g.blinks() == 0 && g.skittish(), "no blink, but skittish, phase " + g.phase());
            helper.assertTrue(Math.abs(before - g.getHealth() - 1.0F) < 0.01F, "it still takes the hit");
            helper.succeed();
        });
    }

    // ---- the fleece and chorus fruit -------------------------------------------------------------------------------

    /** Shears take 1 or 2 purpur fleece: over 30 shearings every count is 1 or 2, and both come up. */
    private static void fleeceCount(GameTestHelper helper) {
        ChorusGrazer g = grazer(helper, 5.5, 5.5);
        g.setNoAi(true);
        List<Integer> counts = new ArrayList<>();
        ItemStack shears = new ItemStack(Items.SHEARS);
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t < 2 || t >= 62) return;
            if (t % 2 == 0) {
                g.setShorn(false);
                g.core().end();
                g.shear(helper.getLevel(), SoundSource.PLAYERS, shears);
            } else {
                counts.add(fleece(helper, true));
            }
        });
        helper.runAtTickTime(65, () -> {
            helper.assertTrue(counts.size() == 30, "30 shearings, counted " + counts.size());
            helper.assertTrue(counts.stream().allMatch(n -> n == 1 || n == 2), "each gave 1 or 2 fleece: " + counts);
            helper.assertTrue(counts.contains(1) && counts.contains(2), "and both counts came up: " + counts);
            helper.succeed();
        });
    }

    /**
     * Eating chorus fruit, with its goals on: a shorn grazer walks to a fruit lying 5 blocks off and eats it; it chews
     * for 30 ticks (counted tick by tick, shorn all the while), then its fleece is back and the fruit is gone. A fleeced
     * grazer isn't hungry: the other fruit is still there at the end, and it never ate.
     */
    private static void eatsFruit(GameTestHelper helper) {
        ChorusGrazer g = wild(helper, 2.5, 4.5);
        g.core().setLook("base", true);
        g.setShorn(true);
        ChorusGrazer full = wild(helper, 2.5, 9.5);
        full.core().setLook("pale_grazer", true);
        fruit(helper, 7.5, 4.5);
        fruit(helper, 4.5, 9.5);
        int[] chewed = {0};
        boolean[] fullAte = {false};
        long[] done = {-1};
        helper.onEachTick(() -> {
            if (g.core().in(Step.EAT)) {
                chewed[0]++;
                helper.assertTrue(g.shorn(), "shorn while it chews");
                helper.assertTrue(lying(helper, Items.CHORUS_FRUIT, false) == 1, "one fruit is taken when it starts");
            }
            if (full.core().in(Step.EAT)) fullAte[0] = true;
            if (done[0] < 0 && chewed[0] > 0 && g.core().idle()) done[0] = helper.getTick();
            if (done[0] > 0 && helper.getTick() == done[0] + 2) {
                helper.assertTrue(chewed[0] == 30, "it chews for 30 ticks, counted " + chewed[0]);
                helper.assertTrue(!g.shorn(), "and its fleece is back");
                helper.assertTrue(lying(helper, Items.CHORUS_FRUIT, false) == 1 && !fullAte[0], "the fleeced grazer left the other fruit alone");
                helper.succeed();
            }
        });
    }

    /**
     * It goes after fruit within 8 blocks: a fruit 7.8 blocks off is in reach of a shorn grazer, one 8.4 off isn't. A
     * fleeced adult isn't hungry; a calf is.
     */
    private static void fruitRange(GameTestHelper helper) {
        ChorusGrazer g = grazer(helper, 2.5, 5.5);
        g.setNoAi(true);
        g.setShorn(true);
        ChorusGrazer full = grazer(helper, 2.5, 9.5);
        full.setNoAi(true);
        ChorusGrazer calf = grazer(helper, 2.5, 1.5);
        calf.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            calf.setAge(-1000);
            ItemEntity item = fruit(helper, 10.3, 5.5);
            item.setNoGravity(true);
            helper.assertTrue(Math.abs(item.distanceTo(g) - 7.8) < 0.01, "the fruit lies 7.8 off, " + item.distanceTo(g));
            helper.assertTrue(g.fruitInReach() == item, "7.8 blocks off is in reach");
            item.setPos(helper.absoluteVec(new Vec3(10.9, 1, 5.5)));
            helper.assertTrue(Math.abs(item.distanceTo(g) - 8.4) < 0.01, "moved to 8.4 off, " + item.distanceTo(g));
            helper.assertTrue(g.fruitInReach() == null, "8.4 blocks off is out of reach");
            helper.assertTrue(g.hungry() && calf.hungry() && !full.hungry(), "shorn and calves are hungry, a fleeced adult isn't");
            helper.succeed();
        });
    }

    /**
     * Chorus fruit by hand grows a shorn grazer's fleece back: one fruit taken, 30 ticks of chewing, then fleeced. A
     * fleeced one fed instead falls in love.
     */
    private static void handFed(GameTestHelper helper) {
        ChorusGrazer g = grazer(helper, 4.5, 5.5);
        g.setShorn(true);
        ServerPlayer p = holding(helper, new Vec3(4.5, 1, 3.5), new ItemStack(Items.CHORUS_FRUIT, 4));
        int[] chewed = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() == 3) {
                helper.assertTrue(use(g, p).consumesAction() && p.getMainHandItem().getCount() == 3, "it takes one fruit");
                helper.assertTrue(g.core().in(Step.EAT) && g.love() == 0, "and chews it, not falling in love, phase " + g.phase());
            }
            if (g.core().in(Step.EAT)) chewed[0]++;
        });
        helper.runAtTickTime(3 + 30 + 3, () -> {
            helper.assertTrue(chewed[0] == 30, "30 ticks of chewing, counted " + chewed[0]);
            helper.assertTrue(!g.shorn(), "and its fleece is back");
            helper.assertTrue(use(g, p).consumesAction() && g.love() == 600, "fed again, fleeced, it falls in love for 600 ticks, " + g.love());
            helper.succeed();
        });
    }

    /** Startled mid-meal, it drops the fruit back on the ground and stays shorn. */
    private static void startledMeal(GameTestHelper helper) {
        ChorusGrazer g = grazer(helper, 2.5, 5.5);
        g.setShorn(true);
        Pig pig = pig(helper, 0.5, 5.5);
        contain(helper, pig);
        helper.runAtTickTime(2, () -> {
            ItemEntity item = fruit(helper, 3.0, 5.5);
            helper.assertTrue(g.eat(item) && !item.isAlive(), "it starts on the fruit");
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(lying(helper, Items.CHORUS_FRUIT, false) == 0, "no fruit lies about while it chews");
            hit(helper, g, pig, 1.0F);
            helper.assertTrue(g.skittish(), "the blow startles it");
            helper.assertTrue(lying(helper, Items.CHORUS_FRUIT, false) == 1, "and it drops the fruit, " + lying(helper, Items.CHORUS_FRUIT, false));
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(g.shorn(), "it never swallowed: still shorn");
            helper.succeed();
        });
    }

    /**
     * Vetoes: a listener that cancels "skittish" leaves a hurt grazer and its herdmate calm and in place; one that
     * cancels "blink" lets them turn skittish where they stand; one that cancels "shear" leaves the fleece on, the shears
     * undamaged and no fleece; one that cancels "eat" leaves a shorn grazer and its fruit alone.
     */
    private static void veto(GameTestHelper helper) {
        ChorusGrazer hurt = grazer(helper, 2.5, 3.5);
        ChorusGrazer mate = grazer(helper, 2.5, 5.5);
        ChorusGrazer kept = grazer(helper, 8.5, 9.5);
        ChorusGrazer hungry = grazer(helper, 8.5, 2.5);
        hungry.setShorn(true);
        Pig pig = pig(helper, 0.5, 4.5);
        contain(helper, pig);
        ServerPlayer p = holding(helper, new Vec3(8.5, 1, 7.5), new ItemStack(Items.SHEARS));
        String[] vetoed = {"skittish"};
        Consumer<MechanicEvent> listener = e -> {
            if ((e.mob() == hurt || e.mob() == mate || e.mob() == kept || e.mob() == hungry) && e.step().equals(vetoed[0])) e.cancel();
            if (e.mob() == kept && e.step().equals("shear")) e.cancel();
            if (e.mob() == hungry && e.step().equals("eat")) e.cancel();
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        Vec3[] at = new Vec3[2];
        helper.runAtTickTime(3, () -> {
            at[0] = hurt.position();
            at[1] = mate.position();
            hit(helper, hurt, pig, 1.0F);
            helper.assertTrue(hurt.core().idle() && mate.core().idle(), "no startle, phases " + hurt.phase() + ", " + mate.phase());
            helper.assertTrue(flat(hurt.position(), at[0]) < 0.01 && flat(mate.position(), at[1]) < 0.01, "and no blink");
            InteractionResult result = use(kept, p);
            helper.assertTrue(!result.consumesAction() && !kept.shorn(), "the vetoed shearing doesn't take, got " + result);
            helper.assertTrue(p.getMainHandItem().getDamageValue() == 0, "the shears are untouched");
            ItemEntity item = fruit(helper, 9.0, 2.5);
            helper.assertTrue(!hungry.eat(item) && item.isAlive() && hungry.core().idle(), "the vetoed meal leaves the fruit");
            vetoed[0] = "blink";
        });
        helper.runAtTickTime(26, () -> {                     // past the 20-tick hurt cooldown of the first blow
            helper.assertTrue(fleece(helper, false) == 0, "no fleece dropped");
            at[0] = hurt.position();
            at[1] = mate.position();
            boolean landed = hit(helper, hurt, pig, 1.0F);
            helper.assertTrue(hurt.skittish() && mate.skittish(), "with only the blink vetoed both turn skittish, blow landed " + landed
                    + ", phases " + hurt.phase() + ", " + mate.phase());
            helper.assertTrue(flat(hurt.position(), at[0]) < 0.01 && flat(mate.position(), at[1]) < 0.01 && hurt.blinks() == 0,
                    "where they stand");
            helper.succeed();
        });
    }

    // ---- love, calves ------------------------------------------------------------------------------------------------

    /**
     * Breeding: chorus fruit from a survival player (one each) makes two grown grazers fall in love; side by side for
     * 60 ticks they bring a calf: half size (0.55 x 0.65), one of its parents' looks, 24000 ticks from grown. The
     * parents rest 6000 ticks: more fruit doesn't take. Experience drops.
     */
    private static void breed(GameTestHelper helper) {
        ChorusGrazer a = grazer(helper, 4.5, 6.5, "base");
        ChorusGrazer b = grazer(helper, 6.5, 6.5, "pale_grazer");
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 3.5), new ItemStack(Items.CHORUS_FRUIT, 5));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(use(a, p).consumesAction() && use(b, p).consumesAction(), "both eat the fruit");
            helper.assertTrue(p.getMainHandItem().getCount() == 3, "one fruit each, " + p.getMainHandItem().getCount() + " left");
            helper.assertTrue(a.love() == 600 && b.love() == 600, "both in love for 600 ticks, " + a.love() + ", " + b.love());
        });
        helper.runAtTickTime(110, () -> {
            List<ChorusGrazer> calves = helper.getLevel().getEntitiesOfClass(ChorusGrazer.class, helper.getBounds(), ChorusGrazer::isBaby);
            calves.forEach(c -> TestPlayers.whenDone(helper, c::discard));
            helper.assertTrue(calves.size() == 1, "one calf, found " + calves.size());
            ChorusGrazer calf = calves.getFirst();
            helper.assertTrue(Math.abs(calf.getBbWidth() - 0.55F) < 0.01F && Math.abs(calf.getBbHeight() - 0.65F) < 0.01F,
                    "half size, " + calf.getBbWidth() + " x " + calf.getBbHeight());
            helper.assertTrue(calf.look().equals("base") || calf.look().equals("pale_grazer"), "a parent's look, " + calf.look());
            helper.assertTrue(calf.age() <= -24000 + 100 && calf.age() > -24000, "24000 ticks from grown, age " + calf.age());
            helper.assertTrue(a.love() == 0 && b.love() == 0, "the parents are out of love");
            helper.assertTrue(a.age() > 6000 - 100 && b.age() > 6000 - 100 && a.age() <= 6000 && b.age() <= 6000, "and rest 6000 ticks, " + a.age() + ", " + b.age());
            helper.assertTrue(!use(a, p).consumesAction() && p.getMainHandItem().getCount() == 3, "more fruit doesn't take while they rest");
            boolean orbs = !helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()).isEmpty();
            helper.assertTrue(orbs || p.totalExperience > 0, "experience dropped (or the player picked it up)");
            helper.succeed();
        });
    }

    /**
     * Calves: a calf 40 ticks from grown is half size and can't be sheared, and grows to 1.1 x 1.3 in 40 ticks; chorus
     * fruit by hand takes a tenth (100 of 1000) off a calf's time; a fruit it eats off the ground takes 1200 off.
     */
    private static void calf(GameTestHelper helper) {
        ChorusGrazer young = grazer(helper, 3.5, 5.5);
        ChorusGrazer fed = grazer(helper, 6.5, 5.5);
        ChorusGrazer eating = grazer(helper, 9.5, 5.5);
        ServerPlayer p = holding(helper, new Vec3(6.5, 1, 3.5), new ItemStack(Items.CHORUS_FRUIT, 2));
        int[] at = {0};
        helper.runAtTickTime(2, () -> {
            young.setAge(-40);
            fed.setAge(-1000);
            eating.setAge(-2000);
            helper.assertTrue(young.isBaby() && Math.abs(young.getBbWidth() - 0.55F) < 0.01F && Math.abs(young.getBbHeight() - 0.65F) < 0.01F,
                    "a calf is half size, " + young.getBbWidth() + " x " + young.getBbHeight());
            helper.assertTrue(use(fed, p).consumesAction() && p.getMainHandItem().getCount() == 1, "the calf eats a fruit from the hand");
            helper.assertTrue(fed.age() == -900, "a tenth off its 1000 ticks, age " + fed.age());
            helper.assertTrue(eating.eat(fruit(helper, 9.5, 6.5)), "a calf eats a fruit off the ground");
            at[0] = eating.age();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            helper.assertTrue(!use(young, p).consumesAction() && !young.shorn(), "shears do nothing to a calf");
        });
        helper.runAtTickTime(2 + 40 + 4, () -> {
            helper.assertTrue(!young.isBaby() && Math.abs(young.getBbWidth() - 1.1F) < 0.01F && Math.abs(young.getBbHeight() - 1.3F) < 0.01F,
                    "40 ticks on it is grown, " + young.getBbWidth() + " x " + young.getBbHeight());
            int gained = eating.age() - at[0];
            helper.assertTrue(gained == 44 + 1200, "the fruit took 1200 ticks off (plus 44 of growing), gained " + gained);
            helper.succeed();
        });
    }

    // ---- saving, herds, stats, home ------------------------------------------------------------------------------------

    /** A shorn grazer and a calf keep their shorn back, their age and their look through a save and reload. */
    private static void saveLoad(GameTestHelper helper) {
        ChorusGrazer bare = grazer(helper, 3.5, 5.5, "pale_grazer");
        ChorusGrazer young = grazer(helper, 7.5, 5.5);
        bare.setShorn(true);
        bare.setNoAi(true);
        young.setNoAi(true);
        ChorusGrazer[] copies = new ChorusGrazer[2];
        helper.runAtTickTime(2, () -> {
            young.setAge(-5000);
            ServerLevel level = helper.getLevel();
            ChorusGrazer[] originals = {bare, young};
            for (int i = 0; i < 2; i++) {
                TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                originals[i].saveWithoutId(out);
                CompoundTag tag = out.buildResult();
                tag.remove("UUID");
                ChorusGrazer copy = ModMobs.CHORUS_GRAZER.get().create(level, EntitySpawnReason.LOAD);
                copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                originals[i].discard();
                level.addFreshEntity(copy);
                TestPlayers.whenDone(helper, copy::discard);
                copies[i] = copy;
            }
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(copies[0].shorn() && copies[0].look().equals("pale_grazer"), "the shorn one stays shorn and pale");
            helper.assertTrue(copies[1].isBaby() && copies[1].age() < -4990, "the calf is still a calf, age " + copies[1].age());
            helper.assertTrue(Math.abs(copies[1].getBbWidth() - 0.55F) < 0.01F, "at half size, " + copies[1].getBbWidth());
            helper.assertTrue(!copies[1].shorn(), "with its fleece on");
            helper.succeed();
        });
    }

    /** A natural herd of four wears one look: the first picks it and the rest wear it. */
    private static void herdLook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ChorusGrazer> herd = new ArrayList<>();
        SpawnGroupData data = null;
        for (int i = 0; i < 4; i++) {
            ChorusGrazer g = ModMobs.CHORUS_GRAZER.get().create(level, EntitySpawnReason.NATURAL);
            Vec3 at = helper.absoluteVec(new Vec3(3.5 + i * 1.5, 1, 5.5));
            g.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            data = g.finalizeSpawn(level, level.getCurrentDifficultyAt(g.blockPosition()), EntitySpawnReason.NATURAL, data);
            g.setNoAi(true);
            level.addFreshEntity(g);
            TestPlayers.whenDone(helper, g::discard);
            herd.add(g);
        }
        helper.runAtTickTime(2, () -> {
            String look = herd.getFirst().look();
            helper.assertTrue(look.equals("base") || look.equals("pale_grazer"), "the herd has a look, " + look);
            helper.assertTrue(herd.stream().allMatch(g -> g.look().equals(look)), "all four wear it: " + herd.stream().map(ChorusGrazer::look).toList());
            helper.succeed();
        });
    }

    /**
     * Health 24, attack 0, armour 0, a 1.1 x 1.3 hitbox, a creature; the Pale Grazer is the same size and health. A
     * grown grazer is not a calf and wears its fleece.
     */
    private static void stats(GameTestHelper helper) {
        ChorusGrazer g = grazer(helper, 3.5, 5.5);
        ChorusGrazer pale = grazer(helper, 8.0, 5.5, "pale_grazer");
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(g.getMaxHealth() - 24.0F) < 0.01F && Math.abs(g.getHealth() - 24.0F) < 0.01F,
                    "health 24, got " + g.getHealth() + "/" + g.getMaxHealth());
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.01, "attack 0");
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(g.getBbWidth() - 1.1F) < 0.01F && Math.abs(g.getBbHeight() - 1.3F) < 0.01F,
                    "hitbox 1.1 x 1.3, got " + g.getBbWidth() + " x " + g.getBbHeight());
            helper.assertTrue(Math.abs(pale.getBbWidth() - 1.1F) < 0.01F && Math.abs(pale.getBbHeight() - 1.3F) < 0.01F
                    && Math.abs(pale.getMaxHealth() - 24.0F) < 0.01F, "the Pale Grazer is the same, " + pale.getBbWidth() + " x " + pale.getBbHeight());
            helper.assertTrue(g.getType().getCategory() == MobCategory.CREATURE, "a creature, " + g.getType().getCategory());
            helper.assertTrue(!g.isBaby() && !g.shorn(), "grown, with its fleece on");
            helper.succeed();
        });
    }

    /**
     * Home: the biome modifier adds it to the End highlands at weight 6 in groups of 2 to 4, and not to the main island,
     * the barrens or the small islands; end stone is a spawn spot, and it spawns in any light.
     */
    private static void spawns(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/chorus_grazer"));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key).map(Holder::value).orElse(null);
        if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) {
            helper.fail("no spawn biome modifier for the Chorus Grazer: " + modifier);
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> spawners = add.spawners().unwrap();
        helper.assertTrue(spawners.size() == 1 && spawners.getFirst().value().type() == ModMobs.CHORUS_GRAZER.get(), "one grazer spawner, " + spawners);
        Weighted<MobSpawnSettings.SpawnerData> spawner = spawners.getFirst();
        helper.assertTrue(spawner.weight() == 6, "spawn weight 6, got " + spawner.weight());
        helper.assertTrue(spawner.value().count().minInclusive() == 2 && spawner.value().count().maxInclusive() == 4,
                "groups of 2 to 4, got " + spawner.value().count().minInclusive() + " to " + spawner.value().count().maxInclusive());
        var biomes = access.lookupOrThrow(Registries.BIOME);
        helper.assertTrue(add.biomes().contains(biomes.getOrThrow(Biomes.END_HIGHLANDS)), "the End highlands are its home");
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.THE_END)), "not the main island");
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.END_BARRENS)), "nor the barrens");
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.SMALL_END_ISLANDS)), "nor the small islands");

        helper.setBlock(new BlockPos(5, 0, 5), Blocks.END_STONE);
        RosterStats.Entry row = RosterStats.get("chorus_grazer");
        ChorusGrazer g = grazer(helper, 5.5, 5.5);
        g.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground mob, place " + row.place());
            helper.assertTrue(row.anyLight(), "in any light");
            helper.assertTrue(TeemingSpawns.placeOk(row, g.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))), "end stone is a spawn spot");
            helper.succeed();
        });
    }
}
