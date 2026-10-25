package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.Lavaleaper;
import com.panda.teeming.mob.nether.Lavaleaper.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Lavaleaper: HP 24, Atk 7, armour 2, 0.8 x 0.6. It swims under the lava at 0.2 blocks a tick, its back 0.15 under the
 * surface; a target within 4 blocks across of it in its lava sets off the tell: 10 ticks risen half sunk, nothing yet;
 * then it leaps, bites for 7 and sets the target on fire for 3 s, and flops back into the lava, ready again 40 ticks
 * later. A fishing bobber hooked on it and reeled in yanks it onto land: helpless 100 ticks, x1.5, not moving. Out of
 * the lava it wriggles back. Lava seas in every Nether biome, weight 10, groups of 1 to 2.
 *
 * <p>Every test lays a lava pool two deep (y 1 and 2, its surface at y 2 + 8/9) from x 1 to a shore, walled with stone,
 * and a stone shore two high beyond it (its top at y 3, a hair over the lava). Leapers hunt survival players, and the
 * GameTest mock player always counts as creative, so the player tests place real survival players that leave when the
 * test ends. Each Leaper's follow range is cut so it can't notice a survivor in a neighbouring arena. Expected values
 * are written out as plain numbers.
 */
public final class LavaleaperTests {
    private LavaleaperTests() {}

    /** The lava surface in the pools, relative to the arena: a source block's lava is 8/9 of a block deep. */
    private static final double SURFACE = 2.0 + 8.0 / 9.0;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("lavaleaper_tell", LavaleaperTests::tell);
        out.accept("lavaleaper_counter", LavaleaperTests::reelIn);
        TeemingGameTests.timeLimit("lavaleaper_counter", 220);
        out.accept("lavaleaper_failure", LavaleaperTests::stayOnShore);
        out.accept("lavaleaper_bite_numbers", LavaleaperTests::biteNumbers);
        out.accept("lavaleaper_leap_range", LavaleaperTests::leapRange);
        out.accept("lavaleaper_cooldown", LavaleaperTests::cooldown);
        TeemingGameTests.timeLimit("lavaleaper_cooldown", 200);
        out.accept("lavaleaper_helpless_damage", LavaleaperTests::helplessDamage);
        out.accept("lavaleaper_line_breaks", LavaleaperTests::lineBreaks);
        out.accept("lavaleaper_wriggles_back", LavaleaperTests::wrigglesBack);
        TeemingGameTests.timeLimit("lavaleaper_wriggles_back", 160);
        out.accept("lavaleaper_lurks", LavaleaperTests::lurks);
        out.accept("lavaleaper_swim_speed", LavaleaperTests::swimSpeed);
        out.accept("lavaleaper_stays_in_lava", LavaleaperTests::staysInLava);
        out.accept("lavaleaper_ignores_creative", LavaleaperTests::ignoresCreative);
        out.accept("lavaleaper_bite_cancel", LavaleaperTests::biteCancel);
        out.accept("lavaleaper_fire_immune", LavaleaperTests::fireImmune);
        out.accept("lavaleaper_stats", LavaleaperTests::stats);
        out.accept("lavaleaper_spawns", LavaleaperTests::spawns);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** Lava two deep from x = 1 to {@code lastLava} (z 1..10), stone walls round it, a stone shore two high beyond. */
    private static void shore(GameTestHelper helper, int lastLava) {
        for (int x = 0; x <= 11; x++) {
            for (int z = 0; z <= 11; z++) {
                for (int y = 1; y <= 2; y++) {
                    boolean lava = x >= 1 && x <= lastLava && z >= 1 && z <= 10;
                    helper.setBlock(new BlockPos(x, y, z), lava ? Blocks.LAVA : Blocks.STONE);
                }
            }
        }
    }

    /**
     * A base-look Leaper in the lava at (x, z), facing +X (the shore), its wandering goal cleared (its hunt still runs)
     * and its follow range cut to 1, so it notices no survivor at all unless a test widens it.
     */
    private static Lavaleaper leaper(GameTestHelper helper, double x, double z) {
        Lavaleaper l = helper.spawn(ModMobs.LAVALEAPER.get(), new Vec3(x, 2.2, z));
        l.core().setLook("base", true);
        l.removeFreeWill();
        l.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        l.setYRot(-90.0F);
        l.setYBodyRot(-90.0F);
        return l;
    }

    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        return pig;
    }

    /** A survival player standing on the shore at (x, z) with a fishing rod in hand. */
    private static ServerPlayer angler(GameTestHelper helper, double x, double z) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(x, 3, z));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
        return p;
    }

    /**
     * Casts {@code p}'s bobber straight down onto the Leaper from just over its back: on the bobber's next tick its own
     * collision check hooks it, as a cast that lands on it does.
     */
    private static FishingHook cast(GameTestHelper helper, ServerPlayer p, Lavaleaper l) {
        ServerLevel level = helper.getLevel();
        FishingHook hook = new FishingHook(p, level, 0, 0);
        hook.snapTo(l.getX(), l.getBoundingBox().maxY + 0.6, l.getZ());
        hook.setDeltaMovement(0.0, -1.0, 0.0);
        level.addFreshEntity(hook);
        TestPlayers.whenDone(helper, hook::discard);
        return hook;
    }

    /** Records the Leaper's own tick count at the first time it posts each mechanic step; removed when the test ends. */
    private static Map<String, Integer> steps(GameTestHelper helper, Lavaleaper l) {
        Map<String, Integer> at = new HashMap<>();
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == l) at.putIfAbsent(e.step(), l.tickCount);
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return at;
    }

    private static double surface(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(0, SURFACE, 0)).y;
    }

    private static double x(GameTestHelper helper, double x) {
        return helper.absoluteVec(new Vec3(x, 0, 0)).x;
    }

    // ---- the tell, the counter, failing it -------------------------------------------------------------------------

    /**
     * A survival player on the shore 3.5 blocks off sets off the tell. It lasts exactly 10 ticks from the tell to the
     * leap; meanwhile it holds still, the player takes nothing, and it rises from under the lava until its back breaks
     * the surface.
     */
    private static void tell(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = leaper(helper, 3.0, 5.5);
        l.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.5);
        // the player steps up once it has sunk to its lurking depth, so the tell starts from a lurk
        ServerPlayer[] p = {null};
        float[] hp = {0.0F};
        helper.runAtTickTime(30, () -> {
            p[0] = TestPlayers.survivor(helper, new Vec3(6.5, 3, 5.5));
            hp[0] = p[0].getHealth();
        });
        Map<String, Integer> at = steps(helper, l);
        boolean[] harmed = {false};
        double[] moved = {0.0}, before = {Double.NaN}, risen = {Double.NEGATIVE_INFINITY}, last = {Double.NaN};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            if (p[0] == null || at.containsKey("leap")) return;                               // the first tell only
            if (!l.core().in(Step.TELL)) {
                last[0] = l.getBoundingBox().maxY - surface(helper);  // its back on the tick before the tell rises
                return;
            }
            if (start[0] == null) {
                start[0] = l.position();
                before[0] = last[0];
            }
            harmed[0] |= p[0].getHealth() < hp[0];
            moved[0] = Math.max(moved[0], l.position().subtract(start[0]).horizontalDistance());
            risen[0] = Math.max(risen[0], l.getBoundingBox().maxY - surface(helper));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.containsKey("tell") && at.containsKey("leap"), "the tell runs into the leap: " + at);
            int tell = at.get("leap") - at.get("tell");
            helper.assertTrue(tell == 10, "the bubbles last 10 ticks, measured " + tell);
            helper.assertTrue(!harmed[0], "nothing happens to the player during the tell");
            helper.assertTrue(moved[0] < 0.05, "it holds still in the tell, moved " + moved[0]);
            helper.assertTrue(before[0] < 0.01, "the tell starts with its back under the surface, " + before[0]);
            helper.assertTrue(risen[0] > 0.15, "in the tell its back breaks the surface, " + risen[0]);
        });
    }

    /**
     * The counter: as it rises in the tell, the player hooks it with a fishing rod and reels in. It never leaps; it is
     * yanked out onto the shore in front of the player and lies helpless for exactly 100 ticks, open to attack the whole
     * time and not moving at all, and the player takes nothing.
     */
    private static void reelIn(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = leaper(helper, 4.0, 5.5);
        l.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.5);
        ServerPlayer p = angler(helper, 7.5, 5.5);
        float hp = p.getHealth();
        Map<String, Integer> at = steps(helper, l);
        FishingHook[] hook = {null};
        boolean[] reeled = {false}, harmed = {false}, open = {true}, inLava = {false};
        int[] helpless = {0};
        double[] drift = {0.0};
        Vec3[] lay = {null};
        helper.onEachTick(() -> {
            if (hook[0] == null && l.core().in(Step.TELL)) hook[0] = cast(helper, p, l);
            if (hook[0] != null && !reeled[0] && l.hook() == hook[0]) {
                hook[0].retrieve(p.getMainHandItem());
                reeled[0] = true;
            }
            harmed[0] |= p.getHealth() < hp;
            if (l.core().in(Step.HELPLESS)) {
                if (lay[0] == null) {
                    lay[0] = l.position();
                    inLava[0] = l.isInLava();
                }
                helpless[0]++;
                open[0] &= l.vulnerable();
                drift[0] = Math.max(drift[0], l.position().subtract(lay[0]).length());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(reeled[0], "the bobber hooked it and the player reeled in");
            helper.assertTrue(helpless[0] > 0 && !l.core().in(Step.HELPLESS), "it lay helpless and got up, " + helpless[0] + " ticks so far");
            helper.assertTrue(helpless[0] == 100, "helpless for 100 ticks, lasted " + helpless[0]);
            helper.assertTrue(!inLava[0] && lay[0].x > x(helper, 6.0), "it lay on the shore, x " + lay[0].x + " (shore from " + x(helper, 6.0) + ")");
            helper.assertTrue(lay[0].distanceTo(p.position()) < 2.5, "in front of the player, " + lay[0].distanceTo(p.position()) + " away");
            helper.assertTrue(open[0], "its weak point shows the whole time it lies helpless");
            helper.assertTrue(drift[0] < 0.01, "helpless, it doesn't move, drifted " + drift[0]);
            helper.assertTrue(!at.containsKey("leap") && !at.containsKey("bite"), "it never leapt or bit: " + at);
            helper.assertTrue(!harmed[0], "the player took nothing");
        });
    }

    /**
     * Failing it: a survival player who stays 3.5 blocks from the lava is leapt at and bitten for Atk 7, scaled for
     * difficulty, and set on fire for 3 s (60 ticks); the Leaper ends back in the lava.
     */
    private static void stayOnShore(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = leaper(helper, 3.0, 5.5);
        l.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.5);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(6.5, 3, 5.5));
        float before = p.getHealth();
        // mob damage to players scales with difficulty: 7 is 4.5 on Easy, 7 on Normal, 10.5 on Hard
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 4.5F;
            case HARD -> 10.5F;
            default -> 7.0F;
        };
        long[] bitAt = {-1};
        float[] lost = {0.0F};
        int[] fire = {-1};
        helper.onEachTick(() -> {
            if (bitAt[0] < 0 && p.getHealth() < before) {
                bitAt[0] = helper.getTick();
                fire[0] = p.getRemainingFireTicks();
            }
            if (bitAt[0] >= 0 && helper.getTick() == bitAt[0] + 5) lost[0] = before - p.getHealth();   // before the first burn lands
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bitAt[0] >= 0, "the leap bites, phase " + l.phase());
            helper.assertTrue(helper.getTick() > bitAt[0] + 5, "five ticks after the bite");
            helper.assertTrue(Math.abs(lost[0] - expected) < 0.01F, "the bite does " + expected + " here, the player lost " + lost[0]);
            helper.assertTrue(fire[0] == 59 || fire[0] == 60, "the player burns for 60 ticks, " + fire[0] + " left at the bite");
            helper.assertTrue(l.core().idle() && l.isInLava() && !l.airborne(), "it flopped back into the lava, phase " + l.phase());
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /**
     * Given a pig on the shore as its target (no difficulty scaling), the bite does exactly 7 (its attack) and the pig
     * burns for exactly as many ticks as it had left at the bite: 60, 3 s.
     */
    private static void biteNumbers(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = leaper(helper, 3.0, 5.5);
        Pig pig = pig(helper, 6.6, 3, 5.5);
        float before = pig.getHealth();
        long[] bitAt = {-1};
        float[] lost = {0.0F};
        int[] fire = {-1}, burning = {0};
        helper.runAtTickTime(1, () -> l.setTarget(pig));
        helper.onEachTick(() -> {
            if (bitAt[0] < 0 && pig.getHealth() < before) {
                bitAt[0] = helper.getTick();
                fire[0] = pig.getRemainingFireTicks();
            }
            if (bitAt[0] >= 0 && helper.getTick() == bitAt[0] + 5) lost[0] = before - pig.getHealth();
            if (bitAt[0] >= 0 && pig.isOnFire()) burning[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bitAt[0] >= 0, "the leap bites the pig, phase " + l.phase());
            helper.assertTrue(Math.abs(lost[0] - 7.0F) < 0.01F, "the bite does 7, the pig lost " + lost[0]);
            helper.assertTrue(fire[0] == 59 || fire[0] == 60, "set on fire for 60 ticks, " + fire[0] + " left at the bite");
            helper.assertTrue(!pig.isOnFire() && burning[0] == fire[0], "it burned out after " + burning[0] + " ticks, " + fire[0] + " left at the bite");
        });
    }

    /**
     * The reach is 4 blocks across. At the very edge of its lava (so it can swim no closer), a pig 4.4 blocks away on
     * the shore is never leapt at in 30 ticks; moved to 3.9 blocks, the tell starts within 2 ticks.
     */
    private static void leapRange(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = leaper(helper, 5.5, 5.5);
        Pig pig = pig(helper, 9.9, 3, 5.5);
        boolean[] early = {false};
        long[] moved = {-1}, told = {-1};
        double[] far = {0}, near = {0};
        helper.runAtTickTime(1, () -> l.setTarget(pig));
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (moved[0] < 0) early[0] |= !l.core().idle();
            if (moved[0] < 0 && now >= 30) {
                far[0] = pig.position().subtract(l.position()).horizontalDistance();
                pig.teleportTo(l.getX() + 3.9, pig.getY(), l.getZ());
                near[0] = pig.position().subtract(l.position()).horizontalDistance();
                moved[0] = now;
            }
            if (moved[0] >= 0 && told[0] < 0 && l.core().in(Step.TELL)) told[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(moved[0] >= 0, "the pig was moved");
            helper.assertTrue(far[0] > 4.3 && far[0] < 4.5 && near[0] > 3.85 && near[0] < 3.95, "set-up: " + far[0] + " then " + near[0] + " across");
            helper.assertTrue(!early[0], "4.4 blocks away it never leapt");
            helper.assertTrue(told[0] >= 0, "3.9 blocks away the tell starts");
            helper.assertTrue(told[0] - moved[0] <= 2, "within 2 ticks, " + (told[0] - moved[0]));
        });
    }

    /** Back in the lava after a leap, it starts its next tell exactly 40 ticks after it splashed down. */
    private static void cooldown(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = leaper(helper, 3.0, 5.5);
        Pig pig = pig(helper, 6.6, 3, 5.5);
        int[] splash = {-1}, next = {-1};
        boolean[] returning = {false};
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == l && e.step().equals("tell") && splash[0] >= 0 && next[0] < 0) next[0] = l.tickCount;
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        helper.runAtTickTime(1, () -> l.setTarget(pig));
        helper.onEachTick(() -> {
            if (l.core().in(Step.RETURN)) returning[0] = true;
            if (returning[0] && splash[0] < 0 && l.core().idle() && l.isInLava()) splash[0] = l.tickCount;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(splash[0] >= 0, "it leapt, bit and flopped back in, phase " + l.phase());
            helper.assertTrue(next[0] >= 0, "and told again");
            helper.assertTrue(next[0] - splash[0] == 40, "40 ticks after the splash, measured " + (next[0] - splash[0]));
        });
    }

    /**
     * Helpless (yanked onto the shore), it takes x1.5: a blow of 4 costs 6, and it shows its weak point. One lurking in
     * the lava takes the same blow as 4 and shows none.
     */
    private static void helplessDamage(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper yanked = leaper(helper, 3.0, 3.5);
        Lavaleaper lurking = leaper(helper, 3.0, 7.5);
        ServerPlayer angler = helper.makeMockServerPlayerInLevel();
        angler.snapTo(helper.absoluteVec(new Vec3(8.5, 3, 3.5)));
        boolean[] checked = {false};
        helper.runAtTickTime(5, () -> helper.assertTrue(yanked.yank(helper.getLevel(), angler), "it can be yanked"));
        helper.onEachTick(() -> {
            if (checked[0] || !yanked.core().in(Step.HELPLESS)) return;
            checked[0] = true;
            helper.assertTrue(yanked.vulnerable() && !lurking.vulnerable(), "only the helpless one shows its weak point");
            float before = yanked.getHealth();
            yanked.hurtServer(helper.getLevel(), yanked.damageSources().magic(), 4.0F);
            float lost = before - yanked.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "helpless it takes x1.5: 4 becomes 6, lost " + lost);
            float before2 = lurking.getHealth();
            lurking.hurtServer(helper.getLevel(), lurking.damageSources().magic(), 4.0F);
            float lost2 = before2 - lurking.getHealth();
            helper.assertTrue(Math.abs(lost2 - 4.0F) < 0.01F, "lurking it takes 4, lost " + lost2);
            helper.succeed();
        });
    }

    /**
     * A line that breaks yanks nothing: hooked, the angler puts the rod away, the bobber drops off, and the Leaper stays
     * in the lava, never yanked.
     */
    private static void lineBreaks(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = leaper(helper, 3.0, 5.5);
        ServerPlayer p = angler(helper, 6.5, 5.5);
        FishingHook[] hook = {null};
        boolean[] hooked = {false}, yanked = {false};
        helper.runAtTickTime(3, () -> hook[0] = cast(helper, p, l));
        helper.onEachTick(() -> {
            if (hook[0] != null && !hooked[0] && l.hook() == hook[0]) {
                hooked[0] = true;
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);   // the rod put away: the line breaks
            }
            yanked[0] |= l.core().in(Step.YANKED) || l.core().in(Step.HELPLESS);
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(hooked[0], "the bobber hooked it");
            helper.assertTrue(hook[0].isRemoved(), "the bobber dropped off when the rod was put away");
            helper.assertTrue(!yanked[0], "it was never yanked");
            helper.assertTrue(l.isInLava() && l.core().idle(), "it stayed in the lava, phase " + l.phase());
            helper.succeed();
        });
    }

    /** Out of the lava on the shore, 2.5 blocks from it, it wriggles back in hops and swims in the lava again. */
    private static void wrigglesBack(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = helper.spawn(ModMobs.LAVALEAPER.get(), new Vec3(8.5, 3, 5.5));
        l.core().setLook("base", true);
        l.removeFreeWill();
        l.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        boolean[] stranded = {false};
        helper.onEachTick(() -> stranded[0] |= l.core().flag(Lavaleaper.STRANDED));
        helper.succeedWhen(() -> {
            helper.assertTrue(stranded[0], "on the shore it was stranded");
            helper.assertTrue(l.isInLava() && !l.core().flag(Lavaleaper.STRANDED), "it got back into the lava, x " + l.getX());
        });
    }

    /**
     * Lurking, it floats with its back 0.15 under the lava surface; held in its tell it rises half sunk, its back 0.3
     * over the surface; and the lava does it no harm.
     */
    private static void lurks(GameTestHelper helper) {
        shore(helper, 8);
        Lavaleaper l = leaper(helper, 3.5, 5.5);
        Lavaleaper risen = leaper(helper, 6.5, 5.5);
        helper.runAtTickTime(40, () -> {
            double lurk = l.getBoundingBox().maxY - surface(helper);
            helper.assertTrue(Math.abs(lurk + 0.15) < 0.02, "lurking, its back is 0.15 under the surface, " + lurk);
            helper.assertTrue(l.isInLava() && l.getHealth() >= l.getMaxHealth(), "in the lava, unharmed");
            helper.assertTrue(risen.startPhase(Step.TELL, 60, null), "a held tell starts");
        });
        helper.runAtTickTime(80, () -> {
            double up = risen.getBoundingBox().maxY - surface(helper);
            helper.assertTrue(Math.abs(up - 0.3) < 0.02, "in the tell its back is 0.3 over the surface, " + up);
            helper.assertTrue(Math.abs(risen.getDeltaMovement().y) < 0.005, "and holds there, " + risen.getDeltaMovement().y);
            helper.succeed();
        });
    }

    /** Swimming across the lava it covers 0.2 blocks a tick: measured over 20 ticks, and never faster on any tick. */
    private static void swimSpeed(GameTestHelper helper) {
        shore(helper, 10);
        Lavaleaper l = leaper(helper, 2.0, 5.5);
        Vec3[] from = {null}, last = {null};
        double[] fastest = {0};
        helper.runAtTickTime(2, () -> l.swimTo(helper.absoluteVec(new Vec3(9.8, 2.2, 5.5))));
        helper.onEachTick(() -> {
            Vec3 now = l.position().multiply(1, 0, 1);
            if (last[0] != null && helper.getTick() > 3) fastest[0] = Math.max(fastest[0], now.distanceTo(last[0]));
            last[0] = now;
            if (helper.getTick() == 15) from[0] = now;
        });
        helper.runAtTickTime(35, () -> {
            double speed = l.position().multiply(1, 0, 1).distanceTo(from[0]) / 20.0;
            helper.assertTrue(Math.abs(speed - 0.2) < 0.005, "it swims 0.2 blocks a tick, measured " + speed);
            helper.assertTrue(fastest[0] < 0.2005, "never faster on any tick, fastest " + fastest[0]);
            helper.assertTrue(l.isInLava(), "under the lava all the way");
            helper.succeed();
        });
    }

    /**
     * Hunting a pig 5 blocks past the shore (out of reach), it swims to the shore and stops there, in the lava every
     * tick, and never leaps.
     */
    private static void staysInLava(GameTestHelper helper) {
        shore(helper, 4);
        Lavaleaper l = leaper(helper, 2.0, 5.5);
        Pig pig = pig(helper, 10.5, 3, 5.5);
        double startX = l.getX();
        boolean[] leapt = {false};
        helper.runAtTickTime(1, () -> l.setTarget(pig));
        helper.onEachTick(() -> {
            helper.assertTrue(helper.getTick() < 3 || l.isInLava(), "it stays in the lava");
            leapt[0] |= !l.core().idle();
        });
        helper.runAtTickTime(60, () -> {
            double shoreX = x(helper, 5.0);
            helper.assertTrue(l.getX() - startX > 1.5, "it swam towards the pig, " + (l.getX() - startX));
            helper.assertTrue(l.getBoundingBox().maxX <= shoreX, "and stopped at the shore, " + (shoreX - l.getBoundingBox().maxX) + " short of it");
            helper.assertTrue(!leapt[0], "it never leapt at a pig out of reach");
            helper.succeed();
        });
    }

    /** A creative player standing at the lava's edge, 3 blocks from it, is never its target: no tell in 60 ticks. */
    private static void ignoresCreative(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = leaper(helper, 3.0, 5.5);
        l.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.5);
        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        creative.snapTo(helper.absoluteVec(new Vec3(6.0, 3, 5.5)));
        boolean[] told = {false}, picked = {false};
        helper.onEachTick(() -> {
            told[0] |= !l.core().idle();
            picked[0] |= l.getTarget() == creative;
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(creative.isCreative(), "the mock player counts as creative");
            helper.assertTrue(!picked[0] && !told[0], "it never targets nor leaps at the creative player, phase " + l.phase());
            helper.succeed();
        });
    }

    /** A listener that cancels the "bite" step lets the leap happen but leaves the pig unhurt and not burning. */
    private static void biteCancel(GameTestHelper helper) {
        shore(helper, 5);
        Lavaleaper l = leaper(helper, 3.0, 5.5);
        Pig pig = pig(helper, 6.6, 3, 5.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == l && e.step().equals("bite")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] returned = {false}, burnt = {false};
        helper.runAtTickTime(1, () -> l.setTarget(pig));
        helper.onEachTick(() -> {
            returned[0] |= l.core().in(Step.RETURN);
            burnt[0] |= pig.isOnFire();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(returned[0] && l.core().idle() && l.isInLava(), "it leapt and flopped back, phase " + l.phase());
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth() && !burnt[0], "a cancelled bite hurts nothing and lights nothing");
        });
    }

    // ---- fire, stats, spawning ---------------------------------------------------------------------------------------

    /** Fire-immune: 60 ticks lurking in lava cost nothing and never set it alight; lava damage is refused outright. */
    private static void fireImmune(GameTestHelper helper) {
        shore(helper, 8);
        Lavaleaper l = leaper(helper, 4.5, 5.5);
        helper.onEachTick(() -> helper.assertTrue(!l.isOnFire(), "it never burns"));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(l.isInLava(), "it was in the lava");
            helper.assertTrue(l.getHealth() >= 24.0F, "60 ticks of lava cost nothing, health " + l.getHealth());
            helper.assertTrue(l.getType().fireImmune(), "its type is fire-immune");
            helper.assertTrue(!l.hurtServer(helper.getLevel(), l.damageSources().lava(), 4.0F), "lava damage is refused");
            helper.succeed();
        });
    }

    /** Health 24, attack 7, armour 2, a 0.8 x 0.6 hitbox; the Ember Leaper is the same size with the same health. */
    private static void stats(GameTestHelper helper) {
        shore(helper, 8);
        Lavaleaper l = leaper(helper, 3.5, 5.5);
        Lavaleaper ember = leaper(helper, 6.5, 5.5);
        ember.core().setLook("ember_leaper", true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(l.getMaxHealth() - 24.0F) < 0.01F && Math.abs(l.getHealth() - 24.0F) < 0.01F,
                    "health 24, got " + l.getHealth() + "/" + l.getMaxHealth());
            helper.assertTrue(Math.abs(l.getAttributeValue(Attributes.ATTACK_DAMAGE) - 7.0) < 0.01, "attack 7");
            helper.assertTrue(Math.abs(l.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(l.getBbWidth() - 0.8F) < 0.02F && Math.abs(l.getBbHeight() - 0.6F) < 0.02F,
                    "hitbox 0.8 x 0.6, got " + l.getBbWidth() + " x " + l.getBbHeight());
            helper.assertTrue(Math.abs(ember.getBbWidth() - 0.8F) < 0.02F && Math.abs(ember.getBbHeight() - 0.6F) < 0.02F,
                    "the Ember Leaper is 0.8 x 0.6, got " + ember.getBbWidth() + " x " + ember.getBbHeight());
            helper.assertTrue(Math.abs(ember.getMaxHealth() - 24.0F) < 0.01F, "with health 24, got " + ember.getMaxHealth());
            helper.succeed();
        });
    }

    /**
     * Its home: the biome modifier adds it to every Nether biome at weight 10 in groups of 1 to 2; the lava placement
     * takes lava with air over it and refuses lava under lava and dry ground; and in lava (as bright as a block gets) its
     * obstruction and spawn rules pass.
     */
    private static void spawns(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/lavaleaper"));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key).map(Holder::value).orElse(null);
        if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) {
            helper.fail("no spawn biome modifier for the Lavaleaper: " + modifier);
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> spawners = add.spawners().unwrap();
        helper.assertTrue(spawners.size() == 1 && spawners.getFirst().value().type() == ModMobs.LAVALEAPER.get(), "one Lavaleaper spawner, " + spawners);
        Weighted<MobSpawnSettings.SpawnerData> spawner = spawners.getFirst();
        helper.assertTrue(spawner.weight() == 10, "spawn weight 10, got " + spawner.weight());
        helper.assertTrue(spawner.value().count().minInclusive() == 1 && spawner.value().count().maxInclusive() == 2,
                "groups of 1 to 2, got " + spawner.value().count().minInclusive() + " to " + spawner.value().count().maxInclusive());
        var biomes = access.lookupOrThrow(Registries.BIOME);
        for (var biome : List.of(Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS, Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST, Biomes.SOUL_SAND_VALLEY)) {
            helper.assertTrue(add.biomes().contains(biomes.getOrThrow(biome)), biome.identifier() + " is its home");
        }
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.PLAINS)), "the Overworld is not");

        shore(helper, 8);
        RosterStats.Entry row = RosterStats.get("lavaleaper");
        Lavaleaper l = helper.spawn(ModMobs.LAVALEAPER.get(), new Vec3(5.5, 2.0, 5.5));
        l.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(row.place() == RosterStats.Place.LAVA, "it is a lava mob, place " + row.place());
            helper.assertTrue(TeemingSpawns.placeOk(row, l.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5))), "lava under air is a spawn spot");
            helper.assertTrue(!TeemingSpawns.placeOk(row, l.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))), "lava under lava is not");
            helper.assertTrue(!TeemingSpawns.placeOk(row, l.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(10, 3, 5))), "the dry shore is not");
            helper.assertTrue(l.checkSpawnObstruction(helper.getLevel()), "lava doesn't count as obstruction");
            helper.assertTrue(l.checkSpawnRules(helper.getLevel(), EntitySpawnReason.NATURAL), "its spawn rules accept the lava, bright as it is");
            helper.succeed();
        });
    }
}
