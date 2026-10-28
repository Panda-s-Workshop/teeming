package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.WoolcapGrazer;
import com.panda.teeming.mob.nether.WoolcapGrazer.Step;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Woolcap Grazer: a creature, HP 18, Atk 0, Arm 0, 1.0 x 1.2, no tell. Shears take 1-2 fungal fleece; the cap grows
 * back by grazing on nylium (and only nylium), which never changes the block. Hurt one, and every grazer within 16
 * blocks stampedes for 100 ticks; a stampeding grazer that runs into something knocks it back 0.6 (0.6 blocks a tick,
 * horizontally away, with a 0.4 hop) and does it no harm, and can't be sheared. Warped roots: two in love bring a calf.
 * Warped forest, weight 8, groups of 2-4; Crimson Woolcap; fungal fleece. Expected values are written out as numbers.
 *
 * <p>A stampede reaches grazers 16 blocks off, past the walls of this test's arena: every test that scares a herd
 * keeps its own stampede inside its arena (a listener vetoes the step for grazers outside it, when the threat is the
 * test's own). Grazers have their goals cleared (no wandering, grazing or following); their stampede, love and
 * breeding still run.
 */
public final class WoolcapGrazerTests {
    private WoolcapGrazerTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("woolcap_grazer_tell", WoolcapGrazerTests::noTellNeverAttacks);
        TeemingGameTests.timeLimit("woolcap_grazer_tell", 130);
        out.accept("woolcap_grazer_counter", WoolcapGrazerTests::shearCalmly);
        out.accept("woolcap_grazer_failure", WoolcapGrazerTests::trampled);
        TeemingGameTests.timeLimit("woolcap_grazer_failure", 120);
        out.accept("woolcap_grazer_stampede_length", WoolcapGrazerTests::stampedeLength);
        TeemingGameTests.timeLimit("woolcap_grazer_stampede_length", 130);
        out.accept("woolcap_grazer_herd_range", WoolcapGrazerTests::herdRange);
        out.accept("woolcap_grazer_knockback", WoolcapGrazerTests::knockback);
        out.accept("woolcap_grazer_bump_veto", WoolcapGrazerTests::bumpVeto);
        out.accept("woolcap_grazer_fleece_count", WoolcapGrazerTests::fleeceCount);
        out.accept("woolcap_grazer_regrow", WoolcapGrazerTests::regrowOnNylium);
        out.accept("woolcap_grazer_veto", WoolcapGrazerTests::veto);
        out.accept("woolcap_grazer_breed", WoolcapGrazerTests::breed);
        TeemingGameTests.timeLimit("woolcap_grazer_breed", 140);
        out.accept("woolcap_grazer_calf", WoolcapGrazerTests::calf);
        out.accept("woolcap_grazer_save_load", WoolcapGrazerTests::saveLoad);
        out.accept("woolcap_grazer_herd_look", WoolcapGrazerTests::herdLook);
        out.accept("woolcap_grazer_stats", WoolcapGrazerTests::stats);
        out.accept("woolcap_grazer_spawns", WoolcapGrazerTests::spawns);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A grown, fleeced grazer of {@code look} at (x, 1, z), goals cleared, removed when the test ends. */
    private static WoolcapGrazer grazer(GameTestHelper helper, double x, double z, String look) {
        WoolcapGrazer g = helper.spawn(ModMobs.WOOLCAP_GRAZER.get(), new Vec3(x, 1, z));
        g.core().setLook(look, true);
        g.removeFreeWill();
        TestPlayers.whenDone(helper, g::discard);
        return g;
    }

    private static WoolcapGrazer grazer(GameTestHelper helper, double x, double z) {
        return grazer(helper, x, z, "base");
    }

    /** A still pig: the threat that hurts the grazers. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        return pig;
    }

    private static ServerPlayer holding(GameTestHelper helper, Vec3 at, ItemStack stack) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return p;
    }

    private static InteractionResult use(WoolcapGrazer g, ServerPlayer p) {
        return g.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    /** Hurts {@code g} for {@code amount} as a blow from {@code by}. */
    private static void hit(GameTestHelper helper, WoolcapGrazer g, Pig by, float amount) {
        g.hurtServer(helper.getLevel(), g.damageSources().mobAttack(by), amount);
    }

    /** Keeps a stampede {@code cause} sets off inside this arena: grazers outside it (other tests') stay put. */
    private static void contain(GameTestHelper helper, Entity cause) {
        Consumer<MechanicEvent> keep = e -> {
            if (e.step().equals("stampede") && e.target() == cause && !helper.getBounds().contains(e.mob().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
    }

    /** The game times at which any of {@code herd} shoved {@code who} (posted "bump" steps, vetoed or not). */
    private static List<Long> bumps(GameTestHelper helper, Entity who, WoolcapGrazer... herd) {
        Set<WoolcapGrazer> mine = Set.of(herd);
        List<Long> at = new ArrayList<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.step().equals("bump") && e.target() == who && e.mob() instanceof WoolcapGrazer g && mine.contains(g)) at.add(helper.getLevel().getGameTime());
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    /** Fungal fleece lying in this arena, counted by the item; {@code clear} also removes it. */
    private static int fleece(GameTestHelper helper, boolean clear) {
        int n = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                i -> i.getItem().is(ModMaterials.FUNGAL_FLEECE.get()))) {
            n += item.getItem().getCount();
            if (clear) item.discard();
        }
        return n;
    }

    private static double horizontal(Vec3 v) {
        return Math.hypot(v.x, v.z);
    }

    // ---- the tell, the counter, failing it -------------------------------------------------------------------------

    /**
     * No tell, because it never attacks: a survival player hits it for 4 (armour 0 takes all 4), it and its herdmate
     * stampede, and for the next 105 ticks neither picks a target, nothing posts a step but the stampede and its
     * shoves, and the player loses no health. Its attack is 0.
     */
    private static void noTellNeverAttacks(GameTestHelper helper) {
        WoolcapGrazer g = grazer(helper, 5.5, 6.5);
        WoolcapGrazer mate = grazer(helper, 7.5, 6.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(3.5, 1, 6.5));
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
            helper.assertTrue(g.core().in(Step.STAMPEDE) && mate.core().in(Step.STAMPEDE), "both bolt, phases " + g.phase() + ", " + mate.phase());
        });
        helper.onEachTick(() -> {
            if (helper.getTick() <= 5) return;
            helper.assertTrue(g.getTarget() == null && mate.getTarget() == null, "neither picks a target");
            helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "the player loses nothing, health " + p.getHealth());
        });
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(steps.contains("stampede"), "the stampede was posted: " + steps);
            helper.assertTrue(steps.stream().allMatch(s -> s.equals("stampede") || s.equals("bump")), "and nothing but it and its shoves: " + steps);
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.01, "attack 0");
            helper.assertTrue(g.core().idle() && mate.core().idle(), "the stampede is over, phases " + g.phase() + ", " + mate.phase());
            helper.succeed();
        });
    }

    /**
     * The counter: shears from a survival player shear a calm grazer: 1 or 2 fungal fleece drop, the shears lose 1
     * durability, it is bare and shakes its back; the grazer beside it stays calm. Shears on the bare one do nothing.
     */
    private static void shearCalmly(GameTestHelper helper) {
        WoolcapGrazer g = grazer(helper, 5.5, 5.5);
        WoolcapGrazer calm = grazer(helper, 8.5, 5.5);
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 3.5), new ItemStack(Items.SHEARS));
        int[] dropped = {0};
        helper.runAtTickTime(5, () -> {
            InteractionResult result = use(g, p);
            helper.assertTrue(result.consumesAction(), "the shears take, got " + result);
            helper.assertTrue(g.shorn() && g.core().in(Step.SHEAR), "it is bare and shakes its back, phase " + g.phase());
            helper.assertTrue(p.getMainHandItem().getDamageValue() == 1, "the shears lose 1, damage " + p.getMainHandItem().getDamageValue());
            InteractionResult again = use(g, p);
            helper.assertTrue(!again.consumesAction(), "shears on the bare grazer do nothing, got " + again);
            helper.assertTrue(p.getMainHandItem().getDamageValue() == 1, "and cost nothing, damage " + p.getMainHandItem().getDamageValue());
        });
        helper.runAtTickTime(7, () -> {
            dropped[0] = fleece(helper, false);
            helper.assertTrue(dropped[0] >= 1 && dropped[0] <= 2, "1 or 2 fungal fleece drop, got " + dropped[0]);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(g.core().idle() && g.shorn(), "the shake is over and it stays bare, phase " + g.phase());
            helper.assertTrue(calm.core().idle() && !calm.shorn(), "its neighbour stayed calm, phase " + calm.phase());
            helper.assertTrue(fleece(helper, false) == dropped[0], "no more fleece came, " + fleece(helper, false));
            helper.succeed();
        });
    }

    /**
     * Failing it: a blow lands on one of three grazers with a survival player standing in front of them. All three
     * stampede, run into the player and knock them back 0.6 (0.6 blocks a tick horizontally, a 0.4 hop) each time,
     * without hurting them; and a stampeding grazer can't be sheared (the shears do nothing and lose nothing).
     */
    private static void trampled(GameTestHelper helper) {
        WoolcapGrazer a = grazer(helper, 3.5, 5.5);
        WoolcapGrazer b = grazer(helper, 3.5, 3.5);
        WoolcapGrazer c = grazer(helper, 3.5, 7.5);
        Pig pig = pig(helper, 1.5, 5.5);
        ServerPlayer p = holding(helper, new Vec3(7.5, 1, 5.5), new ItemStack(Items.SHEARS));
        contain(helper, pig);
        List<Long> hits = bumps(helper, p, a, b, c);
        List<Vec3> motion = new ArrayList<>();
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (helper.getTick() == 5) {
                hit(helper, a, pig, 2.0F);
                helper.assertTrue(a.core().in(Step.STAMPEDE) && b.core().in(Step.STAMPEDE) && c.core().in(Step.STAMPEDE),
                        "all three stampede, phases " + a.phase() + ", " + b.phase() + ", " + c.phase());
            }
            if (helper.getTick() == 12) {
                InteractionResult result = use(b, p);
                helper.assertTrue(!result.consumesAction() && !b.shorn(), "a stampeding grazer can't be sheared, got " + result);
                helper.assertTrue(p.getMainHandItem().getDamageValue() == 0, "the shears lose nothing");
            }
            if (!hits.isEmpty() && hits.getLast() == now) {
                motion.add(p.getDeltaMovement());                  // the shove given on this tick
                p.setDeltaMovement(Vec3.ZERO);                     // so the next one reads only its own
            }
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(!motion.isEmpty(), "the herd ran into the player");
            for (Vec3 v : motion) {
                helper.assertTrue(Math.abs(horizontal(v) - 0.6) < 0.001, "knocked back at 0.6 blocks a tick, got " + horizontal(v));
                helper.assertTrue(Math.abs(v.y - 0.4) < 0.001, "with a 0.4 hop, got " + v.y);
            }
            helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "and never hurt, health " + p.getHealth());
            helper.succeed();
        });
    }

    // ---- the stampede's numbers --------------------------------------------------------------------------------------

    /** The stampede lasts 100 ticks, counted tick by tick, for the hurt grazer and its herdmate alike; then both are calm. */
    private static void stampedeLength(GameTestHelper helper) {
        WoolcapGrazer a = grazer(helper, 4.5, 5.5);
        WoolcapGrazer b = grazer(helper, 7.5, 5.5);
        for (WoolcapGrazer g : List.of(a, b)) g.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);   // stay put
        Pig pig = pig(helper, 2.5, 5.5);
        contain(helper, pig);
        int[] ran = {0, 0};
        helper.onEachTick(() -> {
            if (helper.getTick() == 3) hit(helper, a, pig, 1.0F);
            if (a.core().in(Step.STAMPEDE)) ran[0]++;
            if (b.core().in(Step.STAMPEDE)) ran[1]++;
        });
        helper.runAtTickTime(3 + 100 + 10, () -> {
            helper.assertTrue(ran[0] == 100 && ran[1] == 100, "the stampede lasts 100 ticks, ran " + ran[0] + " and " + ran[1]);
            helper.assertTrue(a.core().idle() && b.core().idle() && a.rush() == null, "then both are calm");
            helper.succeed();
        });
    }

    /**
     * The herd is every grazer within 16 blocks of the hurt one: one 15.56 blocks off bolts, one 16.34 blocks off (on
     * a ledge in the far corner) doesn't. All three stand still with no AI, so only the phase shows it.
     */
    private static void herdRange(GameTestHelper helper) {
        WoolcapGrazer hurt = grazer(helper, 0.5, 0.5);
        WoolcapGrazer near = grazer(helper, 11.5, 11.5);
        helper.setBlock(new BlockPos(11, 5, 11), Blocks.STONE);
        WoolcapGrazer far = helper.spawn(ModMobs.WOOLCAP_GRAZER.get(), new Vec3(11.5, 6, 11.5));
        far.core().setLook("base", true);
        TestPlayers.whenDone(helper, far::discard);
        for (WoolcapGrazer g : List.of(hurt, near, far)) g.setNoAi(true);
        Pig pig = pig(helper, 2.5, 0.5);
        contain(helper, pig);
        helper.runAtTickTime(3, () -> hit(helper, hurt, pig, 1.0F));
        helper.runAtTickTime(4, () -> {
            double dNear = hurt.distanceTo(near);
            double dFar = hurt.distanceTo(far);
            helper.assertTrue(Math.abs(dNear - 15.56) < 0.02 && Math.abs(dFar - 16.34) < 0.02, "the grazers stand 15.56 and 16.34 off, got " + dNear + ", " + dFar);
            helper.assertTrue(hurt.core().in(Step.STAMPEDE), "the hurt one bolts");
            helper.assertTrue(near.core().in(Step.STAMPEDE), "so does the one 15.56 blocks off, phase " + near.phase());
            helper.assertTrue(far.core().idle(), "the one 16.34 blocks off stays calm, phase " + far.phase());
            helper.succeed();
        });
    }

    /**
     * Knockback 0.6: a stampeding grazer that runs into a survival player sends them off at 0.6 blocks a tick,
     * horizontally straight away from it (eastwards: it ran east into them), with a 0.4 hop, and takes no health; it
     * shoves the same player at most once in 10 ticks.
     */
    private static void knockback(GameTestHelper helper) {
        WoolcapGrazer g = grazer(helper, 2.5, 5.5);
        Pig pig = pig(helper, 0.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 1, 5.5));
        contain(helper, pig);
        List<Long> hits = bumps(helper, p, g);
        List<Vec3> motion = new ArrayList<>();
        List<Vec3> away = new ArrayList<>();
        helper.onEachTick(() -> {
            if (helper.getTick() == 3) hit(helper, g, pig, 1.0F);
            long now = helper.getLevel().getGameTime();
            if (!hits.isEmpty() && hits.getLast() == now) {
                motion.add(p.getDeltaMovement());
                away.add(p.position().subtract(g.position()).multiply(1, 0, 1).normalize());   // from the grazer to the player
                p.setDeltaMovement(Vec3.ZERO);
            }
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!motion.isEmpty(), "the grazer ran into the player");
            Vec3 first = motion.getFirst();
            helper.assertTrue(Math.abs(horizontal(first) - 0.6) < 0.001, "knocked back at 0.6 blocks a tick, got " + horizontal(first));
            Vec3 dir = new Vec3(first.x, 0, first.z).normalize();
            helper.assertTrue(dir.dot(away.getFirst()) > 0.999, "straight away from the grazer, got " + dir + " for " + away.getFirst());
            helper.assertTrue(first.x > 0.0, "eastwards, the way it ran, got " + first);
            helper.assertTrue(Math.abs(first.y - 0.4) < 0.001, "with a 0.4 hop, got " + first.y);
            for (int i = 1; i < hits.size(); i++) {
                helper.assertTrue(hits.get(i) - hits.get(i - 1) >= 10, "at most one shove in 10 ticks, shoves at " + hits);
            }
            helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "no health lost, " + p.getHealth());
            helper.succeed();
        });
    }

    /** A listener that vetoes "bump" lets a stampeding grazer run into a player without shoving them at all. */
    private static void bumpVeto(GameTestHelper helper) {
        WoolcapGrazer g = grazer(helper, 2.5, 5.5);
        Pig pig = pig(helper, 0.5, 5.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 1, 5.5));
        contain(helper, pig);
        List<Long> tries = bumps(helper, p, g);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == g && e.step().equals("bump")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(3, () -> hit(helper, g, pig, 1.0F));
        helper.onEachTick(() -> helper.assertTrue(horizontal(p.getDeltaMovement()) < 1.0E-4, "the player is never shoved, " + p.getDeltaMovement()));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!tries.isEmpty(), "the grazer did run into the player");
            helper.assertTrue(g.bumps() == 0, "and landed no shove, " + g.bumps());
            helper.succeed();
        });
    }

    // ---- the fleece --------------------------------------------------------------------------------------------------

    /** Shears take 1 or 2 fungal fleece: over 30 shearings every count is 1 or 2, and both come up. */
    private static void fleeceCount(GameTestHelper helper) {
        WoolcapGrazer g = grazer(helper, 5.5, 5.5);
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
     * It grows its cap back by grazing on nylium, warped or crimson: 40 ticks with its head down (counted tick by
     * tick, bare all the while), then the cap is back, and the nylium is still nylium. On netherrack or grass it won't
     * graze, and stays bare.
     */
    private static void regrowOnNylium(GameTestHelper helper) {
        BlockPos[] spots = {new BlockPos(3, 0, 4), new BlockPos(8, 0, 4), new BlockPos(3, 0, 8), new BlockPos(8, 0, 8)};
        Block[] floors = {Blocks.WARPED_NYLIUM, Blocks.CRIMSON_NYLIUM, Blocks.NETHERRACK, Blocks.GRASS_BLOCK};
        WoolcapGrazer[] g = new WoolcapGrazer[4];
        for (int i = 0; i < 4; i++) {
            helper.setBlock(spots[i], floors[i]);
            g[i] = grazer(helper, spots[i].getX() + 0.5, spots[i].getZ() + 0.5);
            g[i].setShorn(true);
        }
        int[] grazing = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() == 3) {
                helper.assertTrue(g[0].graze() && g[1].graze(), "on warped and crimson nylium it grazes");
                helper.assertTrue(!g[2].graze() && !g[3].graze(), "on netherrack and grass it won't");
            }
            if (g[0].core().in(Step.GRAZE)) {
                grazing[0]++;
                helper.assertTrue(g[0].shorn(), "bare while it grazes");
            }
        });
        helper.runAtTickTime(3 + 40 + 5, () -> {
            helper.assertTrue(grazing[0] == 40, "it grazes for 40 ticks, counted " + grazing[0]);
            helper.assertTrue(!g[0].shorn() && !g[1].shorn(), "the caps are back on both nylium grazers");
            helper.assertTrue(g[2].shorn() && g[3].shorn(), "the other two are still bare");
            for (int i = 0; i < 4; i++) {
                helper.assertTrue(helper.getBlockState(spots[i]).is(floors[i]), "the ground is unchanged at " + spots[i] + ", " + helper.getBlockState(spots[i]));
            }
            helper.succeed();
        });
    }

    /**
     * Vetoes: a listener that cancels "stampede" leaves a hurt grazer and its herdmate calm; one that cancels "shear"
     * leaves the cap on, the shears undamaged and no fleece.
     */
    private static void veto(GameTestHelper helper) {
        WoolcapGrazer hurt = grazer(helper, 3.5, 4.5);
        WoolcapGrazer mate = grazer(helper, 5.5, 4.5);
        WoolcapGrazer kept = grazer(helper, 8.5, 8.5);
        Pig pig = pig(helper, 1.5, 4.5);
        contain(helper, pig);
        ServerPlayer p = holding(helper, new Vec3(8.5, 1, 6.5), new ItemStack(Items.SHEARS));
        Consumer<MechanicEvent> veto = e -> {
            if ((e.mob() == hurt || e.mob() == mate || e.mob() == kept) && e.step().equals("stampede")) e.cancel();
            if (e.mob() == kept && e.step().equals("shear")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(3, () -> {
            hit(helper, hurt, pig, 1.0F);
            helper.assertTrue(hurt.core().idle() && mate.core().idle() && kept.core().idle(), "no stampede, phases " + hurt.phase() + ", " + mate.phase());
            InteractionResult result = use(kept, p);
            helper.assertTrue(!result.consumesAction() && !kept.shorn(), "the vetoed shearing doesn't take, got " + result);
            helper.assertTrue(p.getMainHandItem().getDamageValue() == 0, "the shears are untouched");
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(fleece(helper, false) == 0, "and no fleece dropped");
            helper.succeed();
        });
    }

    // ---- love, calves --------------------------------------------------------------------------------------------------

    /**
     * Breeding: warped roots from a survival player (one each) make two grown grazers fall in love; side by side for
     * 60 ticks they bring a calf: half size (0.5 x 0.6), one of its parents' looks, 24000 ticks from grown. The
     * parents rest 6000 ticks: more roots don't take. Experience drops.
     */
    private static void breed(GameTestHelper helper) {
        WoolcapGrazer a = grazer(helper, 4.5, 6.5, "base");
        WoolcapGrazer b = grazer(helper, 6.5, 6.5, "crimson_woolcap");
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 3.5), new ItemStack(Items.WARPED_ROOTS, 5));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(use(a, p).consumesAction() && use(b, p).consumesAction(), "both eat the roots");
            helper.assertTrue(p.getMainHandItem().getCount() == 3, "one root each, " + p.getMainHandItem().getCount() + " left");
            helper.assertTrue(a.love() == 600 && b.love() == 600, "both in love for 600 ticks, " + a.love() + ", " + b.love());
        });
        helper.runAtTickTime(110, () -> {
            List<WoolcapGrazer> calves = helper.getLevel().getEntitiesOfClass(WoolcapGrazer.class, helper.getBounds(), WoolcapGrazer::isBaby);
            calves.forEach(c -> TestPlayers.whenDone(helper, c::discard));
            helper.assertTrue(calves.size() == 1, "one calf, found " + calves.size());
            WoolcapGrazer calf = calves.getFirst();
            helper.assertTrue(Math.abs(calf.getBbWidth() - 0.5F) < 0.01F && Math.abs(calf.getBbHeight() - 0.6F) < 0.01F,
                    "half size, " + calf.getBbWidth() + " x " + calf.getBbHeight());
            helper.assertTrue(calf.look().equals("base") || calf.look().equals("crimson_woolcap"), "a parent's look, " + calf.look());
            helper.assertTrue(calf.age() <= -24000 + 100 && calf.age() > -24000, "24000 ticks from grown, age " + calf.age());
            helper.assertTrue(a.love() == 0 && b.love() == 0, "the parents are out of love");
            helper.assertTrue(a.age() > 6000 - 100 && b.age() > 6000 - 100 && a.age() <= 6000 && b.age() <= 6000, "and rest 6000 ticks, " + a.age() + ", " + b.age());
            helper.assertTrue(!use(a, p).consumesAction() && p.getMainHandItem().getCount() == 3, "more roots don't take while they rest");
            boolean orbs = !helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()).isEmpty();
            helper.assertTrue(orbs || p.totalExperience > 0, "experience dropped (or the player picked it up)");
            helper.succeed();
        });
    }

    /**
     * Calves: a calf 40 ticks from grown is half size and can't be sheared, and grows to 1.0 x 1.2 in 40 ticks;
     * warped roots take a tenth (100 of 1000) off a calf's time; a graze on nylium takes 1200 off.
     */
    private static void calf(GameTestHelper helper) {
        WoolcapGrazer young = grazer(helper, 3.5, 5.5);
        WoolcapGrazer fed = grazer(helper, 6.5, 5.5);
        helper.setBlock(new BlockPos(9, 0, 5), Blocks.WARPED_NYLIUM);
        WoolcapGrazer grazing = grazer(helper, 9.5, 5.5);
        ServerPlayer p = holding(helper, new Vec3(6.5, 1, 3.5), new ItemStack(Items.WARPED_ROOTS, 2));
        int[] at = {0};
        helper.runAtTickTime(2, () -> {
            young.setAge(-40);
            fed.setAge(-1000);
            grazing.setAge(-2000);
            helper.assertTrue(young.isBaby() && Math.abs(young.getBbWidth() - 0.5F) < 0.01F && Math.abs(young.getBbHeight() - 0.6F) < 0.01F,
                    "a calf is half size, " + young.getBbWidth() + " x " + young.getBbHeight());
            helper.assertTrue(use(fed, p).consumesAction() && p.getMainHandItem().getCount() == 1, "the calf eats a root");
            helper.assertTrue(fed.age() == -900, "a tenth off its 1000 ticks, age " + fed.age());
            helper.assertTrue(grazing.graze(), "a calf on nylium grazes");
            at[0] = grazing.age();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            helper.assertTrue(!use(young, p).consumesAction() && !young.shorn(), "shears do nothing to a calf");
        });
        helper.runAtTickTime(2 + 40 + 4, () -> {
            helper.assertTrue(!young.isBaby() && Math.abs(young.getBbWidth() - 1.0F) < 0.01F && Math.abs(young.getBbHeight() - 1.2F) < 0.01F,
                    "40 ticks on it is grown, " + young.getBbWidth() + " x " + young.getBbHeight());
            int gained = grazing.age() - at[0];
            helper.assertTrue(gained == 44 + 1200, "the graze took 1200 ticks off (plus 44 of growing), gained " + gained);
            helper.succeed();
        });
    }

    // ---- saving, herds, stats, home ------------------------------------------------------------------------------------

    /** A bare grazer and a calf keep their bare back, their age and their look through a save and reload. */
    private static void saveLoad(GameTestHelper helper) {
        WoolcapGrazer bare = grazer(helper, 3.5, 5.5, "crimson_woolcap");
        WoolcapGrazer young = grazer(helper, 7.5, 5.5);
        bare.setShorn(true);
        bare.setNoAi(true);
        young.setNoAi(true);
        WoolcapGrazer[] copies = new WoolcapGrazer[2];
        helper.runAtTickTime(2, () -> {
            young.setAge(-5000);
            ServerLevel level = helper.getLevel();
            WoolcapGrazer[] originals = {bare, young};
            for (int i = 0; i < 2; i++) {
                TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                originals[i].saveWithoutId(out);
                CompoundTag tag = out.buildResult();
                tag.remove("UUID");
                WoolcapGrazer copy = ModMobs.WOOLCAP_GRAZER.get().create(level, EntitySpawnReason.LOAD);
                copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                originals[i].discard();
                level.addFreshEntity(copy);
                TestPlayers.whenDone(helper, copy::discard);
                copies[i] = copy;
            }
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(copies[0].shorn() && copies[0].look().equals("crimson_woolcap"), "the bare one stays bare and crimson");
            helper.assertTrue(copies[1].isBaby() && copies[1].age() < -4990, "the calf is still a calf, age " + copies[1].age());
            helper.assertTrue(Math.abs(copies[1].getBbWidth() - 0.5F) < 0.01F, "at half size, " + copies[1].getBbWidth());
            helper.assertTrue(!copies[1].shorn(), "with its cap on");
            helper.succeed();
        });
    }

    /** A natural herd of four wears one look: the first picks it and the rest wear it. */
    private static void herdLook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<WoolcapGrazer> herd = new ArrayList<>();
        SpawnGroupData data = null;
        for (int i = 0; i < 4; i++) {
            WoolcapGrazer g = ModMobs.WOOLCAP_GRAZER.get().create(level, EntitySpawnReason.NATURAL);
            Vec3 at = helper.absoluteVec(new Vec3(3.5 + i * 1.5, 1, 5.5));
            g.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            data = g.finalizeSpawn(level, level.getCurrentDifficultyAt(g.blockPosition()), EntitySpawnReason.NATURAL, data);
            g.setNoAi(true);
            level.addFreshEntity(g);
            g.setPersistenceRequired();                        // a natural spawn despawns while other tests' players are over 128 blocks off
            TestPlayers.whenDone(helper, g::discard);
            herd.add(g);
        }
        helper.runAtTickTime(2, () -> {
            String look = herd.getFirst().look();
            helper.assertTrue(look.equals("base") || look.equals("crimson_woolcap"), "the herd has a look, " + look);
            helper.assertTrue(herd.stream().allMatch(g -> g.look().equals(look)), "all four wear it: " + herd.stream().map(WoolcapGrazer::look).toList());
            helper.succeed();
        });
    }

    /**
     * Health 18, attack 0, armour 0, a 1.0 x 1.2 hitbox, a creature; the Crimson Woolcap is the same size and health.
     * A grown grazer drops its loot; it is not a calf.
     */
    private static void stats(GameTestHelper helper) {
        WoolcapGrazer g = grazer(helper, 3.5, 5.5);
        WoolcapGrazer crimson = grazer(helper, 8.0, 5.5, "crimson_woolcap");
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(g.getMaxHealth() - 18.0F) < 0.01F && Math.abs(g.getHealth() - 18.0F) < 0.01F,
                    "health 18, got " + g.getHealth() + "/" + g.getMaxHealth());
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.01, "attack 0");
            helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(g.getBbWidth() - 1.0F) < 0.01F && Math.abs(g.getBbHeight() - 1.2F) < 0.01F,
                    "hitbox 1.0 x 1.2, got " + g.getBbWidth() + " x " + g.getBbHeight());
            helper.assertTrue(Math.abs(crimson.getBbWidth() - 1.0F) < 0.01F && Math.abs(crimson.getBbHeight() - 1.2F) < 0.01F
                    && Math.abs(crimson.getMaxHealth() - 18.0F) < 0.01F, "the Crimson Woolcap is the same, " + crimson.getBbWidth() + " x " + crimson.getBbHeight());
            helper.assertTrue(g.getType().getCategory() == MobCategory.CREATURE, "a creature, " + g.getType().getCategory());
            helper.assertTrue(!g.isBaby() && !g.shorn(), "grown, with its cap on");
            helper.succeed();
        });
    }

    /**
     * Home: the biome modifier adds it to the warped forest at weight 8 in groups of 2 to 4, never to the crimson
     * forest; warped nylium is a spawn spot, and its spawn rules pass there in the dark.
     */
    private static void spawns(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/woolcap_grazer"));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key).map(Holder::value).orElse(null);
        if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) {
            helper.fail("no spawn biome modifier for the Woolcap Grazer: " + modifier);
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> spawners = add.spawners().unwrap();
        helper.assertTrue(spawners.size() == 1 && spawners.getFirst().value().type() == ModMobs.WOOLCAP_GRAZER.get(), "one grazer spawner, " + spawners);
        Weighted<MobSpawnSettings.SpawnerData> spawner = spawners.getFirst();
        helper.assertTrue(spawner.weight() == 8, "spawn weight 8, got " + spawner.weight());
        helper.assertTrue(spawner.value().count().minInclusive() == 2 && spawner.value().count().maxInclusive() == 4,
                "groups of 2 to 4, got " + spawner.value().count().minInclusive() + " to " + spawner.value().count().maxInclusive());
        var biomes = access.lookupOrThrow(Registries.BIOME);
        helper.assertTrue(add.biomes().contains(biomes.getOrThrow(Biomes.WARPED_FOREST)), "the warped forest is its home");
        helper.assertTrue(add.biomes().contains(biomes.getOrThrow(Biomes.CRIMSON_FOREST)), "the crimson forest is its home, where its crimson look lives");
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.NETHER_WASTES)), "the nether wastes are not");

        helper.setBlock(new BlockPos(5, 0, 5), Blocks.WARPED_NYLIUM);
        RosterStats.Entry row = RosterStats.get("woolcap_grazer");
        WoolcapGrazer g = grazer(helper, 5.5, 5.5);
        g.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground mob, place " + row.place());
            helper.assertTrue(TeemingSpawns.placeOk(row, g.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))), "warped nylium is a spawn spot");
            helper.assertTrue(g.checkSpawnRules(helper.getLevel(), EntitySpawnReason.NATURAL), "its spawn rules accept it");
            helper.succeed();
        });
    }
}
