package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.LiftstoneMonolith;
import com.panda.teeming.mob.end.LiftstoneMonolith.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.SpawnTable;
import com.panda.teeming.spawn.TeemingSpawner;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Liftstone Monolith: a 20-tick rise that moves and hurts no one yet; then everything within 6 blocks is lifted straight
 * up 6 blocks over 20 ticks, held 10, and slammed back down for 12, with no fall damage on top; a solid block within 3
 * over a head stops that lift; it rises again 160 ticks after it last rose; a pull that caught on a roof splits the
 * capstone for 80 ticks with armour 0. Health 250, attack 12, armour 12, a 1.6 x 4.0 hitbox, 100 experience, one in 128
 * blocks, placed alone on the End's main island once the dragon is dead by the Teeming spawner's rare apex rule, never
 * within 256 blocks of another. Every number is checked against a plain value.
 *
 * <p>Every Monolith stands at the arena's middle, so its 6-block reach stays inside the arena. Those that must stay
 * unaware have their goals cleared and a follow range of 1, so they never notice a survival player in a neighbouring
 * arena; they are told to rise directly. Pigs are discarded when the test ends.
 */
public final class LiftstoneMonolithTests {
    private LiftstoneMonolithTests() {}

    /** The arena's middle, where every Monolith stands. */
    private static final double MID = 6.5;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("liftstone_monolith_tell", LiftstoneMonolithTests::tell);
        out.accept("liftstone_monolith_counter", LiftstoneMonolithTests::roofAndPunish);
        out.accept("liftstone_monolith_failure", LiftstoneMonolithTests::slammedPlayer);
        out.accept("liftstone_monolith_lift", LiftstoneMonolithTests::lift);
        out.accept("liftstone_monolith_reach", LiftstoneMonolithTests::reach);
        out.accept("liftstone_monolith_roof", LiftstoneMonolithTests::roof);
        out.accept("liftstone_monolith_slam", LiftstoneMonolithTests::slam);
        out.accept("liftstone_monolith_midair", LiftstoneMonolithTests::midair);
        out.accept("liftstone_monolith_cycle", LiftstoneMonolithTests::cycle);
        TeemingGameTests.timeLimit("liftstone_monolith_cycle", 220);
        out.accept("liftstone_monolith_cracked_length", LiftstoneMonolithTests::crackedLength);
        TeemingGameTests.timeLimit("liftstone_monolith_cracked_length", 180);
        out.accept("liftstone_monolith_armor", LiftstoneMonolithTests::armor);
        out.accept("liftstone_monolith_survive_three", LiftstoneMonolithTests::surviveThree);
        out.accept("liftstone_monolith_slam_veto", LiftstoneMonolithTests::slamVeto);
        out.accept("liftstone_monolith_lets_go", LiftstoneMonolithTests::letsGo);
        out.accept("liftstone_monolith_stats", LiftstoneMonolithTests::stats);
        out.accept("liftstone_monolith_home", LiftstoneMonolithTests::home);
        out.accept("liftstone_monolith_cap", LiftstoneMonolithTests::cap);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A Monolith at the arena's middle with no goals and a follow range of 1. */
    private static LiftstoneMonolith unaware(GameTestHelper helper) {
        LiftstoneMonolith m = helper.spawn(ModMobs.LIFTSTONE_MONOLITH.get(), new Vec3(MID, 1, MID));
        m.removeFreeWill();
        m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        TestPlayers.whenDone(helper, m::discard);
        return m;
    }

    /** A still pig with 100 health {@code dx}, {@code dz} blocks from the middle, {@code up} over the floor. */
    private static Pig pig(GameTestHelper helper, double dx, double up, double dz) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(MID + dx, 1 + up, MID + dz));
        pig.setNoAi(true);
        sturdy(pig, 100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static void sturdy(Pig pig, float health) {
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        pig.setHealth(health);
    }

    private static float lost(Pig pig) {
        return pig.getMaxHealth() - pig.getHealth();
    }

    /** Clears the barrier roof over the arena (the two layers above its 8 rows), so a player can rise the whole 6. */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
    }

    /** The slam's 12 as the game scales mob blows to players at this difficulty. */
    private static float slamHere(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case EASY -> 7.0F;          // min(12 / 2 + 1, 12)
            case HARD -> 18.0F;         // 12 x 1.5
            default -> 12.0F;
        };
    }

    /** Follows one body through a test: its highest rise over where it started, and its widest drift sideways. */
    private static final class Track {
        final Vec3 start;
        double rise, drift;

        Track(Vec3 start) {
            this.start = start;
        }

        void see(Vec3 at) {
            rise = Math.max(rise, at.y - start.y);
            drift = Math.max(drift, at.subtract(start).horizontalDistance());
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The rise lasts exactly 20 ticks: it stands still, its runes climb from stage 2 to 4, and a pig 3 blocks off is
     * neither moved nor hurt; then the lift begins and the pig leaves the ground.
     */
    private static void tell(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        Pig pig = pig(helper, 3.0, 0, 0);
        double from = pig.getY();
        int[] ticks = {0};
        boolean[] quiet = {true}, risen = {false}, ended = {false};
        List<Integer> stages = new ArrayList<>();
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (!risen[0]) {                                                       // risen here, so its first tick is counted too
                risen[0] = true;
                helper.assertTrue(m.rise(pig), "it rises");
            }
            if (m.core().in(Step.TELL)) {
                if (start[0] == null) start[0] = m.position();
                ticks[0]++;
                moved[0] = Math.max(moved[0], m.position().subtract(start[0]).horizontalDistance());
                int stage = m.core().stage();
                if (stages.isEmpty() || stages.getLast() != stage) stages.add(stage);
                if (Math.abs(pig.getY() - from) > 0.01 || pig.getHealth() < pig.getMaxHealth()) quiet[0] = false;
            } else if (ticks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the rise runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(ticks[0] == 20, "the rise lasts 20 ticks, lasted " + ticks[0]);
            helper.assertTrue(quiet[0], "nothing is moved or hurt during the rise");
            helper.assertTrue(stages.equals(List.of(2, 3, 4)), "its runes light stage by stage, 2 to 4, saw " + stages);
            helper.assertTrue(moved[0] < 0.05, "it stands its ground while it rises, moved " + moved[0]);
            helper.assertTrue(m.core().in(Step.LIFT) && pig.getY() > from + 0.2, "the lift follows, phase " + m.phase() + ", pig up " + (pig.getY() - from));
        });
    }

    /**
     * The counter: a pig under a stone 2 blocks over its head stays on the floor untouched, while a pig in the open 3
     * blocks the other way is lifted and slammed for 12. The pull caught on the roof, so the capstone splits: the heart
     * shows gold, every other glow is dark, armour 0, and a blow of 10 takes exactly 10.
     */
    private static void roofAndPunish(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        helper.setBlock(new BlockPos(9, 4, 6), Blocks.STONE);
        Pig roofed = pig(helper, 3.0, 0, 0);
        Pig open = pig(helper, -3.0, 0, 0);
        Track r = new Track(roofed.position()), o = new Track(open.position());
        boolean[] split = {false};
        helper.runAtTickTime(1, () -> helper.assertTrue(m.rise(open), "it rises"));
        helper.onEachTick(() -> {
            r.see(roofed.position());
            o.see(open.position());
            if (split[0] || !m.core().in(Step.CRACKED)) return;
            split[0] = true;
            helper.assertTrue(r.rise < 0.01 && lost(roofed) == 0.0F, "under the roof the pig never left the floor, rose " + r.rise + ", lost " + lost(roofed));
            helper.assertTrue(o.rise > 5.9 && Math.abs(lost(open) - 12.0F) < 0.01F, "in the open the pig was lifted and slammed, rose " + o.rise + ", lost " + lost(open));
            helper.assertTrue(m.vulnerable() && m.core().stage() == 0, "split, the heart shows gold and nothing else glows, stage " + m.core().stage());
            helper.assertTrue(m.getArmorValue() == 0, "split, armour 0, got " + m.getArmorValue());
            float before = m.getHealth();
            helper.assertTrue(m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(open), 10.0F), "the blow lands");
            float taken = before - m.getHealth();
            helper.assertTrue(Math.abs(taken - 10.0F) < 0.01F, "split, a blow of 10 takes 10, took " + taken);
        });
        helper.succeedWhen(() -> helper.assertTrue(split[0], "the capstone split after the slam, phase " + m.phase()));
    }

    /**
     * Failing it: a survival player in the open 3 blocks off (the roof over the arena opened) is lifted straight up 6
     * blocks, never moved sideways, slammed back onto the spot it stood on and loses exactly the slam (12 on Normal, as
     * the game scales mob blows), and nothing more in the 20 ticks after it lands.
     */
    private static void slammedPlayer(GameTestHelper helper) {
        openRoof(helper);
        LiftstoneMonolith m = unaware(helper);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(MID + 3.0, 1, MID));
        float each = slamHere(helper);
        Track t = new Track(p.position());
        float[] health = {20.0F, -1.0F};
        long[] landed = {-1};
        boolean[] lifted = {false};
        helper.runAtTickTime(1, () -> {
            health[0] = p.getHealth();
            helper.assertTrue(m.rise(p), "it rises");
        });
        helper.onEachTick(() -> {
            if (helper.getTick() < 2) return;
            t.see(p.position());
            lifted[0] |= m.held().contains(p);
            if (landed[0] < 0 && lifted[0] && !m.held().contains(p) && Math.abs(p.getY() - t.start.y) < 0.05) {
                landed[0] = helper.getTick();
                health[1] = p.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(landed[0] > 0 && helper.getTick() >= landed[0] + 20, "it landed, and 20 ticks passed");
            helper.assertTrue(Math.abs(t.rise - 6.0) < 0.05, "the player rose 6 blocks, rose " + t.rise);
            helper.assertTrue(t.drift < 0.01, "and never moved sideways, drifted " + t.drift);
            helper.assertTrue(Math.abs(p.getY() - t.start.y) < 0.05, "it lands where it stood, " + (p.getY() - t.start.y) + " off");
            helper.assertTrue(Math.abs(health[0] - health[1] - each) < 0.01F, "the landing costs " + each + " here, cost " + (health[0] - health[1]));
            helper.assertTrue(Math.abs(health[1] - p.getHealth()) < 0.01F, "and nothing more after it (no fall damage), lost " + (health[1] - p.getHealth()));
        });
    }

    // ---- the pull's numbers ----------------------------------------------------------------------------------------

    /**
     * The lift: a pig rises 0.3 a tick for exactly 20 ticks, to 6 blocks over where it stood, straight up; it is held
     * there 10 ticks; and the Monolith itself never moves.
     */
    private static void lift(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        Pig pig = pig(helper, 0, 0, 3.0);
        double from = pig.getY();
        Vec3 spot = pig.position(), mid = m.position();
        List<Double> rising = new ArrayList<>(), holding = new ArrayList<>();
        double[] drift = {0.0}, moved = {0.0};
        helper.runAtTickTime(1, () -> helper.assertTrue(m.rise(pig), "it rises"));
        helper.onEachTick(() -> {
            if (m.core().in(Step.LIFT)) rising.add(pig.getY() - from);
            if (m.core().in(Step.HOLD)) holding.add(pig.getY() - from);
            drift[0] = Math.max(drift[0], pig.position().subtract(spot).horizontalDistance());
            moved[0] = Math.max(moved[0], m.position().distanceTo(mid));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(m.core().in(Step.SLAM) || m.core().idle(), "the lift and the hold are over, phase " + m.phase());
            helper.assertTrue(rising.size() == 20, "it lifts for 20 ticks, lifted for " + rising.size());
            helper.assertTrue(Math.abs(rising.getFirst() - 0.3) < 0.01, "0.3 up after the first tick, " + rising.getFirst());
            helper.assertTrue(Math.abs(rising.getLast() - 6.0) < 0.01, "6 up after the last, " + rising.getLast());
            for (int i = 1; i < rising.size(); i++) {
                helper.assertTrue(Math.abs(rising.get(i) - rising.get(i - 1) - 0.3) < 0.01, "an even 0.3 a tick: " + rising);
            }
            helper.assertTrue(holding.size() == 10 && holding.stream().allMatch(y -> Math.abs(y - 6.0) < 0.01), "held 6 up for 10 ticks: " + holding);
            helper.assertTrue(drift[0] < 0.01, "straight up, drifted " + drift[0]);
            helper.assertTrue(moved[0] < 0.01, "the Monolith stands still, moved " + moved[0]);
        });
    }

    /**
     * The reach is 6 blocks from its middle: a pig 5.8 off is lifted, one 6.3 off is not, nor a pig hanging 3.5 blocks
     * over the floor (beyond the 3 it reaches up), nor a creative player 3 off.
     */
    private static void reach(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        double d = 5.8 / Math.sqrt(2.0), e = 6.3 / Math.sqrt(2.0);
        Pig inside = pig(helper, d, 0, d);
        Pig outside = pig(helper, -e, 0, -e);
        Pig high = pig(helper, -2.0, 3.5, 2.0);
        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        creative.snapTo(helper.absoluteVec(new Vec3(MID + 3.0, 1, MID)));
        helper.runAtTickTime(1, () -> helper.assertTrue(m.rise(null), "it rises"));
        helper.onEachTick(() -> {
            if (!m.core().in(Step.LIFT)) return;
            helper.assertTrue(creative.isCreative(), "the mock player counts as creative");
            List<?> held = m.held();
            helper.assertTrue(held.contains(inside), "5.8 blocks off is inside the reach");
            helper.assertTrue(!held.contains(outside), "6.3 blocks off is outside it");
            helper.assertTrue(!held.contains(high), "3.5 blocks over the floor is above it");
            helper.assertTrue(!held.contains(creative), "a creative player is never lifted");
            helper.assertTrue(held.size() == 1, "only the one pig, held " + held.size());
            helper.succeed();
        });
    }

    /**
     * A solid block within 3 over the head stops the lift: a pig with a stone 2.1 over its head stays down; a pig with
     * a stone 3.1 over its head is lifted, stops under that stone (3.1 up, not 6) and is still slammed for 12.
     */
    private static void roof(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        helper.setBlock(new BlockPos(9, 4, 6), Blocks.STONE);      // a pig's head is 0.9 up: this one is 2.1 over it
        helper.setBlock(new BlockPos(3, 5, 6), Blocks.STONE);      // and this one 3.1
        Pig low = pig(helper, 3.0, 0, 0);
        Pig high = pig(helper, -3.0, 0, 0);
        Track l = new Track(low.position()), h = new Track(high.position());
        boolean[] checked = {false};
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(LiftstoneMonolith.Hoist.roofed(low, 3.0) && !LiftstoneMonolith.Hoist.roofed(high, 3.0), "one roofed within 3, one not");
            double room = LiftstoneMonolith.Hoist.headroom(high, 6.0);
            helper.assertTrue(Math.abs(room - 3.1) < 0.01, "3.1 of headroom under the higher stone, " + room);
            helper.assertTrue(m.rise(null), "it rises");
        });
        helper.onEachTick(() -> {
            l.see(low.position());
            h.see(high.position());
            if (checked[0] || !(m.core().in(Step.CRACKED) || (m.core().idle() && helper.getTick() > 30))) return;
            checked[0] = true;
            helper.assertTrue(l.rise < 0.01 && lost(low) == 0.0F, "2.1 under a stone: never lifted, never hurt, rose " + l.rise + ", lost " + lost(low));
            helper.assertTrue(Math.abs(h.rise - 3.1) < 0.02, "3.1 under a stone: lifted until it meets it, rose " + h.rise);
            helper.assertTrue(Math.abs(lost(high) - 12.0F) < 0.01F && Math.abs(high.getY() - h.start.y) < 0.01, "and slammed back down for 12, lost " + lost(high));
        });
        helper.succeedWhen(() -> helper.assertTrue(checked[0], "the pull ran, phase " + m.phase()));
    }

    /**
     * The slam: a pig with its own physics is lifted and slammed, loses exactly 12 on landing, lands where it stood,
     * and loses nothing more in the 20 ticks after (no fall damage on top). A pig dropped 5.4 blocks beside it, out of
     * reach, takes the game's fall damage of 2 (the fall less 3, rounded down), so falls do hurt in this arena.
     */
    private static void slam(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(MID + 3.0, 1, MID));
        pig.removeFreeWill();                                                  // its own physics, no wandering
        sturdy(pig, 100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        Pig control = helper.spawn(EntityTypes.PIG, new Vec3(MID - 5.0, 6.4, MID - 5.0));   // 7 blocks off, out of reach
        control.removeFreeWill();
        sturdy(control, 100.0F);
        TestPlayers.whenDone(helper, control::discard);
        Track t = new Track(pig.position());
        long[] landed = {-1};
        float[] atLanding = {-1.0F};
        boolean[] lifted = {false};
        helper.runAtTickTime(1, () -> helper.assertTrue(m.rise(null), "it rises"));
        helper.onEachTick(() -> {
            t.see(pig.position());
            lifted[0] |= m.held().contains(pig);
            if (landed[0] < 0 && lifted[0] && !m.held().contains(pig)) {
                landed[0] = helper.getTick();
                atLanding[0] = lost(pig);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(landed[0] > 0 && helper.getTick() >= landed[0] + 20, "it landed, and 20 ticks passed");
            helper.assertTrue(t.rise > 5.8, "it was lifted, rose " + t.rise);
            helper.assertTrue(Math.abs(atLanding[0] - 12.0F) < 0.01F, "the landing costs 12, cost " + atLanding[0]);
            helper.assertTrue(Math.abs(lost(pig) - 12.0F) < 0.01F, "and nothing more after it (no fall damage), lost " + lost(pig));
            helper.assertTrue(Math.abs(pig.getY() - t.start.y) < 0.05 && t.drift < 0.05, "it lands where it stood");
            helper.assertTrue(Math.abs(lost(control) - 2.0F) < 0.01F, "a 5.4-block fall costs the game's 2, cost " + lost(control));
        });
    }

    /** A pig caught in mid-air, half a block over the floor, is slammed onto the floor under it (not back into the air) for 12. */
    private static void midair(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        Pig pig = pig(helper, 3.0, 0.5, 0);
        double floor = helper.absoluteVec(new Vec3(0, 1, 0)).y;
        boolean[] lifted = {false}, done = {false};
        helper.runAtTickTime(1, () -> helper.assertTrue(m.rise(null), "it rises"));
        helper.onEachTick(() -> {
            lifted[0] |= m.held().contains(pig);
            if (done[0] || !lifted[0] || m.held().contains(pig)) return;
            done[0] = true;
            helper.assertTrue(Math.abs(pig.getY() - floor) < 0.01, "slammed onto the floor, " + (pig.getY() - floor) + " over it");
            helper.assertTrue(Math.abs(lost(pig) - 12.0F) < 0.01F, "for 12, lost " + lost(pig));
        });
        helper.succeedWhen(() -> helper.assertTrue(done[0], "the pig was lifted and slammed"));
    }

    /**
     * Every 160 ticks: with its target 2.8 blocks off it rises at once, and again exactly 160 ticks after that rise.
     * Its follow range is 4, short of any neighbouring arena; the pig is too sturdy to die.
     */
    private static void cycle(GameTestHelper helper) {
        LiftstoneMonolith m = helper.spawn(ModMobs.LIFTSTONE_MONOLITH.get(), new Vec3(MID, 1, MID));
        m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.0);
        TestPlayers.whenDone(helper, m::discard);
        Pig pig = pig(helper, 2.8, 0, 0);
        sturdy(pig, 1000.0F);
        List<Long> rises = new ArrayList<>();
        Step[] was = {Step.IDLE};
        helper.runAtTickTime(1, () -> m.setTarget(pig));
        helper.onEachTick(() -> {
            Step now = (Step) m.phase();
            if (now == Step.TELL && was[0] != Step.TELL) rises.add(helper.getTick());
            was[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!rises.isEmpty() && rises.getFirst() <= 4, "with its target in reach it rises at once, rises " + rises);
            helper.assertTrue(rises.size() >= 2, "and rises again, rises " + rises);
            helper.assertTrue(rises.get(1) - rises.getFirst() == 160, "160 ticks after the first, took " + (rises.get(1) - rises.getFirst()));
        });
    }

    /**
     * After a pull that caught on a roof the capstone stays split exactly 80 ticks: gold, armour 0 and every other
     * glow dark the whole time; then it closes (armour 12, faint runes), and the next rise is still 160 ticks from the
     * last, not from the close.
     */
    private static void crackedLength(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        helper.setBlock(new BlockPos(9, 3, 6), Blocks.STONE);
        Pig pig = pig(helper, 3.0, 0, 0);
        int[] ticks = {0};
        long[] rose = {-1};
        boolean[] open = {true}, sealed = {false};
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(m.rise(pig), "it rises");
            rose[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            if (m.core().in(Step.CRACKED)) {
                ticks[0]++;
                open[0] &= m.vulnerable() && m.getArmorValue() == 0 && m.core().stage() == 0;
            } else if (ticks[0] > 0 && !sealed[0]) {
                sealed[0] = true;
                helper.assertTrue(m.core().idle(), "after the window it is idle, phase " + m.phase());
                helper.assertTrue(!m.vulnerable() && m.getArmorValue() == 12, "closed: no gold, armour 12, armour " + m.getArmorValue());
                helper.assertTrue(m.core().stage() == 1, "its runes glow faintly again, stage " + m.core().stage());
                long since = helper.getTick() - rose[0];
                helper.assertTrue(m.untilRise() == 160 - since, "the next rise is 160 ticks from the last: " + m.untilRise() + " to go, " + since + " since");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sealed[0], "the capstone split and closed, " + ticks[0] + " ticks split so far");
            helper.assertTrue(ticks[0] == 80, "the capstone stays split 80 ticks, stayed " + ticks[0]);
            helper.assertTrue(open[0], "gold, armour 0 and every other glow dark the whole time");
        });
    }

    /** Whole, armour 12 cuts a blow of 10 to 7.2; split, the same blow takes 10. */
    private static void armor(GameTestHelper helper) {
        LiftstoneMonolith whole = unaware(helper);
        LiftstoneMonolith split = helper.spawn(ModMobs.LIFTSTONE_MONOLITH.get(), new Vec3(MID - 3.5, 1, MID - 3.5));
        split.setNoAi(true);
        TestPlayers.whenDone(helper, split::discard);
        Pig pig = pig(helper, 4.0, 0, 0);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(whole.getArmorValue() == 12, "whole, armour 12, got " + whole.getArmorValue());
            helper.assertTrue(split.startPhase(Step.CRACKED, 80, null), "the second one splits");
            helper.assertTrue(split.getArmorValue() == 0, "split, armour 0, got " + split.getArmorValue());
            float a = whole.getHealth(), c = split.getHealth();
            whole.hurtServer(helper.getLevel(), whole.damageSources().mobAttack(pig), 10.0F);
            split.hurtServer(helper.getLevel(), split.damageSources().mobAttack(pig), 10.0F);
            float tookWhole = a - whole.getHealth(), tookSplit = c - split.getHealth();
            helper.assertTrue(Math.abs(tookWhole - 7.2F) < 0.01F, "whole, a blow of 10 takes 7.2, took " + tookWhole);
            helper.assertTrue(Math.abs(tookSplit - 10.0F) < 0.01F, "split, a blow of 10 takes 10, took " + tookSplit);
            helper.succeed();
        });
    }

    /**
     * A survival player in the End's reference armour (a netherite set: armour 20, toughness 12) survives three slams,
     * each costing what the armour formula says for the slam at this difficulty: 3.55 on Normal (1.79 on Easy, 6.19 on
     * Hard).
     */
    private static void surviveThree(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(MID + 3.0, 1, MID));
        // the set's numbers straight on the player: its equipment would only count once it ticks
        p.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
        p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(12.0);
        // d becomes d x (1 - (20 - d / 5) / 25); mob blows to players scale with difficulty first
        float each = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 1.792F;
            case HARD -> 6.192F;
            default -> 3.552F;
        };
        float[] health = {20.0F};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(p.getArmorValue() == 20, "armour 20, got " + p.getArmorValue());
            health[0] = p.getHealth();
        });
        for (int i = 0; i < 3; i++) {
            int slam = i + 1;
            helper.runAtTickTime(4 + i * 25, () -> {
                float before = p.getHealth();
                helper.assertTrue(m.slamHit(helper.getLevel(), p), "slam " + slam + " lands on the player");
                float cost = before - p.getHealth();
                helper.assertTrue(Math.abs(cost - each) < 0.02F, "slam " + slam + " costs " + each + " here, cost " + cost);
            });
        }
        helper.runAtTickTime(4 + 2 * 25 + 2, () -> {
            helper.assertTrue(p.isAlive() && p.getHealth() > 0.0F, "the player lives through three slams, health " + p.getHealth());
            helper.assertTrue(Math.abs(health[0] - p.getHealth() - 3 * each) < 0.05F, "three slams cost " + 3 * each + ", cost " + (health[0] - p.getHealth()));
            helper.succeed();
        });
    }

    /** A pack that vetoes the slam step: the held pig is set back down where it stood, unhurt, and the Monolith goes idle. */
    private static void slamVeto(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        Pig pig = pig(helper, 3.0, 0, 0);
        double from = pig.getY();
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == m && e.step().equals("slam")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] held = {false}, settled = {false};
        helper.runAtTickTime(1, () -> helper.assertTrue(m.rise(pig), "it rises"));
        helper.onEachTick(() -> {
            if (m.core().in(Step.HOLD)) {
                held[0] = true;
            } else if (held[0] && !settled[0]) {
                settled[0] = true;
                helper.assertTrue(m.core().idle(), "the vetoed slam leaves it idle, phase " + m.phase());
                helper.assertTrue(lost(pig) == 0.0F, "the pig is unhurt, lost " + lost(pig));
                helper.assertTrue(Math.abs(pig.getY() - from) < 0.01 && m.held().isEmpty(), "and set back down where it stood");
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(settled[0], "the hold ran and ended, phase " + m.phase()));
    }

    /** Removed in the middle of a lift, it lets go of the pig it held, which drifts down under 60 ticks of Slow Falling. */
    private static void letsGo(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        Pig pig = pig(helper, 3.0, 0, 0);
        helper.runAtTickTime(1, () -> helper.assertTrue(m.rise(pig), "it rises"));
        helper.onEachTick(() -> {
            if (!m.core().in(Step.LIFT) || m.core().elapsed() < 10 || m.isRemoved()) return;
            helper.assertTrue(m.held().contains(pig), "it holds the pig mid-lift");
            m.discard();
            MobEffectInstance cushion = pig.getEffect(MobEffects.SLOW_FALLING);
            helper.assertTrue(cushion != null && cushion.getDuration() == 60, "let go, the pig falls under Slow Falling for 60 ticks, got " + cushion);
            helper.assertTrue(m.held().isEmpty(), "and is no longer held");
            helper.succeed();
        });
    }

    // ---- stats, home, cap ------------------------------------------------------------------------------------------

    /** Health 250, attack 12, armour 12, a 1.6 x 4.0 hitbox, 100 experience; blows don't move it; faint runes at rest. */
    private static void stats(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        Pig pig = pig(helper, 4.0, 0, 0);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(m.getMaxHealth() - 250.0F) < 0.01F && Math.abs(m.getHealth() - 250.0F) < 0.01F,
                    "health 250, got " + m.getHealth() + "/" + m.getMaxHealth());
            helper.assertTrue(Math.abs(m.getAttributeValue(Attributes.ATTACK_DAMAGE) - 12.0) < 0.01, "attack 12, got " + m.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(Math.abs(m.getAttributeValue(Attributes.ARMOR) - 12.0) < 0.01 && m.getArmorValue() == 12, "armour 12, got " + m.getArmorValue());
            helper.assertTrue(Math.abs(m.getBbWidth() - 1.6F) < 0.02F && Math.abs(m.getBbHeight() - 4.0F) < 0.02F,
                    "hitbox 1.6 x 4.0, got " + m.getBbWidth() + " x " + m.getBbHeight());
            int xp = m.getExperienceReward(helper.getLevel(), pig);
            helper.assertTrue(xp == 100, "100 experience, got " + xp);
            helper.assertTrue(m.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "blows never move it");
            helper.assertTrue(!m.vulnerable() && m.core().stage() == 1, "whole and faintly lit at rest, stage " + m.core().stage());
            helper.succeed();
        });
    }

    /**
     * Home: the End's main island, placed only by the Teeming spawner's main-island apex rule: only after a won apex
     * roll (1 in 80 attempts), with room for its whole body, none within 256 blocks, and shut until the dragon has been
     * killed (no dragon was ever killed in this level). That rule takes the main island's ground as the plain
     * main-island rule does; it is not that rule, so the Monolith is no longer one of its weighted picks. On no biome
     * list, so no biome modifier; alone (groups of 1), weight 0; an apex. The rule at work is spawner_apex_island.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("liftstone_monolith");
        helper.assertTrue(row.apex() && row.dim() == RosterStats.Dim.END, "an End apex");
        TeemingSpawner.Rule rule = TeemingSpawner.Rule.of(row.spawner());
        helper.assertTrue(rule == TeemingSpawner.Rule.APEX_ISLAND && rule.apex() && rule.dimension == Level.END,
                "the main-island apex rule places it, got '" + row.spawner() + "'");
        helper.assertTrue(!TeemingSpawner.Rule.MAIN_ISLAND.apex(), "the plain main-island rule stays an ordinary weighted rule");
        helper.assertTrue(Math.abs(SpawnTable.APEX_CHANCE - 1.0 / 80.0) < 1.0E-9, "a won roll 1 attempt in 80, got " + SpawnTable.APEX_CHANCE);
        helper.assertTrue(SpawnTable.APEX_RADIUS == 256 && SpawnTable.APEX_RADIUS >= 2 * LiftstoneMonolith.CAP_RADIUS,
                "none within 256 blocks, twice its local cap, got " + SpawnTable.APEX_RADIUS);
        if (TeemingConfig.endMainIslandAfterDragon()) {
            helper.assertTrue(!TeemingSpawner.mainIslandOpen(helper.getLevel()), "the main island stays shut while no dragon has been killed");
        }
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("liftstone_monolith"));
        helper.assertTrue(tag.isEmpty() || tag.get().size() == 0, "its biome tag is empty: the main-island rule alone says where");
        helper.assertTrue(!row.biomeSpawns(), "on no biome list");
        helper.assertTrue(access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/liftstone_monolith"))).isEmpty(), "no biome modifier");
        helper.assertTrue(row.weight() == 0, "weight 0, got " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "alone, got " + row.groupMin() + ".." + row.groupMax());
        helper.succeed();
    }

    /**
     * Local cap: one in 128 blocks. With one here a second is refused 127 blocks above it and allowed 133 above (its
     * 4-block body then clears the 128-block box), both by its own rule and by the natural-spawn predicate's cap. The
     * spawner's apex rule keeps them further apart: it refuses a second 255 blocks above and allows one 262 above (clear
     * of the 256-block box). Straight up, so no other arena's mobs are in any box.
     */
    private static void cap(GameTestHelper helper) {
        LiftstoneMonolith m = unaware(helper);
        m.setNoAi(true);
        RosterStats.Entry row = RosterStats.get("liftstone_monolith");
        BlockPos at = helper.absolutePos(new BlockPos((int) MID, 1, (int) MID));
        helper.runAtTickTime(2, () -> {
            var type = ModMobs.LIFTSTONE_MONOLITH.get();
            helper.assertTrue(!LiftstoneMonolith.roomFor(helper.getLevel(), at), "a second beside it is refused");
            helper.assertTrue(!LiftstoneMonolith.roomFor(helper.getLevel(), at.above(127)), "a second 127 blocks off is refused");
            helper.assertTrue(LiftstoneMonolith.roomFor(helper.getLevel(), at.above(133)), "a second 133 blocks off may spawn");
            helper.assertTrue(!TeemingSpawns.underCap(row, type, helper.getLevel(), at.above(127)), "the spawn predicate refuses 127 off too");
            helper.assertTrue(TeemingSpawns.underCap(row, type, helper.getLevel(), at.above(133)), "and allows 133 off");
            helper.assertTrue(!TeemingSpawner.apexClear(helper.getLevel(), type, at.above(133)), "the spawner's apex rule refuses 133 off");
            helper.assertTrue(!TeemingSpawner.apexClear(helper.getLevel(), type, at.above(255)), "and 255 off");
            helper.assertTrue(TeemingSpawner.apexClear(helper.getLevel(), type, at.above(262)), "and allows 262 off");
            helper.succeed();
        });
    }
}
