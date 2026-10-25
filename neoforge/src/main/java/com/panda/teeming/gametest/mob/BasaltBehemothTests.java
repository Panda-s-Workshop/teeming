package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.nether.BasaltBehemoth;
import com.panda.teeming.mob.nether.BasaltBehemoth.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
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
import net.minecraft.util.random.Weighted;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Basalt Behemoth: a 20-tick rear that hurts no one yet; a slam of 14 to everything on the ground within 4 blocks, thrown
 * back, and nothing in the air; then 60 ticks with the core open (armour 0, every hit double) while it keeps walking and
 * swatting; then 60 ticks before it may rear again. Health 200, attack 14, armour 16, a 2.4 x 3.2 hitbox, 100
 * experience, one in 128 blocks, placed alone in the basalt deltas by the Teeming spawner's rare apex rule. Every number
 * is checked against a plain value.
 *
 * <p>Every Behemoth stands at the arena's middle. Those that must stay unaware have their goals cleared and a follow
 * range of 1, so they never notice a survival player in a neighbouring arena; they are told to rear directly. Pigs
 * that might be thrown out of the arena are discarded when the test ends.
 */
public final class BasaltBehemothTests {
    private BasaltBehemothTests() {}

    /** The arena's middle, where every Behemoth stands. */
    private static final double MID = 6.0;

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("basalt_behemoth_tell", BasaltBehemothTests::tell);
        out.accept("basalt_behemoth_counter", BasaltBehemothTests::dodgeAndPunish);
        out.accept("basalt_behemoth_failure", BasaltBehemothTests::slammed);
        out.accept("basalt_behemoth_reach", BasaltBehemothTests::reach);
        out.accept("basalt_behemoth_jump", BasaltBehemothTests::jumpSpared);
        out.accept("basalt_behemoth_open_length", BasaltBehemothTests::openLength);
        TeemingGameTests.timeLimit("basalt_behemoth_open_length", 120);
        out.accept("basalt_behemoth_armor", BasaltBehemothTests::armor);
        out.accept("basalt_behemoth_walks_open", BasaltBehemothTests::walksOpen);
        out.accept("basalt_behemoth_cooldown", BasaltBehemothTests::cooldown);
        TeemingGameTests.timeLimit("basalt_behemoth_cooldown", 180);
        out.accept("basalt_behemoth_survive_three", BasaltBehemothTests::surviveThree);
        out.accept("basalt_behemoth_slam_veto", BasaltBehemothTests::slamVeto);
        out.accept("basalt_behemoth_stats", BasaltBehemothTests::stats);
        out.accept("basalt_behemoth_home", BasaltBehemothTests::home);
        out.accept("basalt_behemoth_cap", BasaltBehemothTests::cap);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A Behemoth at the arena's middle with no goals and a follow range of 1. */
    private static BasaltBehemoth unaware(GameTestHelper helper) {
        BasaltBehemoth b = helper.spawn(ModMobs.BASALT_BEHEMOTH.get(), new Vec3(MID, 1, MID));
        b.removeFreeWill();
        b.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        TestPlayers.whenDone(helper, b::discard);
        return b;
    }

    /** A still pig {@code dx} blocks east of the middle (west if negative), on the floor. */
    private static Pig pig(GameTestHelper helper, double dx) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(MID + dx, 1, MID));
        pig.setNoAi(true);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    private static float lost(Pig pig) {
        return pig.getMaxHealth() - pig.getHealth();
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * The rear lasts exactly 20 ticks: it stands still, flares (glow stage 2) and hurts no one, though a pig stands 2.5
     * blocks off; then the slam comes and the core opens.
     */
    private static void tell(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        Pig pig = pig(helper, 2.5);
        int[] ticks = {0};
        boolean[] quiet = {true}, flared = {true}, ended = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        boolean[] reared = {false};
        helper.onEachTick(() -> {
            if (!reared[0]) {                                                      // reared here, so its first tick is counted too
                reared[0] = true;
                helper.assertTrue(b.rear(pig), "it rears");
            }
            if (b.core().in(Step.TELL)) {
                if (start[0] == null) start[0] = b.position();
                ticks[0]++;
                moved[0] = Math.max(moved[0], b.position().subtract(start[0]).horizontalDistance());
                flared[0] &= b.core().stage() == 2 && !b.vulnerable();
                if (pig.getHealth() < pig.getMaxHealth()) quiet[0] = false;
            } else if (ticks[0] > 0) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the rear runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(ticks[0] == 20, "the rear lasts 20 ticks, lasted " + ticks[0]);
            helper.assertTrue(quiet[0], "nothing is hurt during the rear");
            helper.assertTrue(flared[0], "its joints flare (stage 2) and no gold shows during the rear");
            helper.assertTrue(moved[0] < 0.05, "it stands its ground while it rears, moved " + moved[0]);
            helper.assertTrue(pig.isDeadOrDying() || pig.getHealth() < pig.getMaxHealth(), "the slam follows the rear");
            helper.assertTrue(b.core().in(Step.EXPOSED), "and its core opens, phase " + b.phase());
        });
    }

    /**
     * The counter: a pig that stays 5 blocks off (outside the reach) is not touched by the slam; then, with the core
     * open, a blow of 10 takes exactly 20 (no armour, double) and the core shows gold.
     */
    private static void dodgeAndPunish(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        Pig far = pig(helper, 5.0);
        boolean[] opened = {false};
        helper.runAtTickTime(1, () -> helper.assertTrue(b.rear(far), "it rears"));
        helper.onEachTick(() -> {
            if (opened[0] || !b.core().in(Step.EXPOSED)) return;
            opened[0] = true;
            helper.assertTrue(far.getHealth() >= far.getMaxHealth(), "5 blocks off, the slam missed the pig, lost " + lost(far));
            helper.assertTrue(b.vulnerable() && b.core().stage() == 0, "open, the core shows gold and nothing else glows, stage " + b.core().stage());
            float before = b.getHealth();
            helper.assertTrue(b.hurtServer(helper.getLevel(), b.damageSources().mobAttack(far), 10.0F), "the blow lands");
            float taken = before - b.getHealth();
            helper.assertTrue(Math.abs(taken - 20.0F) < 0.01F, "open, a blow of 10 takes 20, took " + taken);
        });
        helper.succeedWhen(() -> helper.assertTrue(opened[0], "the core opened after the slam, phase " + b.phase()));
    }

    /** Failing it: a pig 2.5 blocks off, on the floor, takes exactly 14 and is thrown away from it at 0.7 a tick, up 0.35. */
    private static void slammed(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(MID + 2.5, 1, MID));
        pig.removeFreeWill();                                                      // it can be thrown, but won't wander
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        helper.runAtTickTime(2, () -> {
            int hurt = b.slam(helper.getLevel());
            Vec3 v = pig.getDeltaMovement();                                       // read on the tick of the hit
            helper.assertTrue(hurt == 1, "the slam hurts the pig, hurt " + hurt);
            helper.assertTrue(Math.abs(lost(pig) - 14.0F) < 0.01F, "the slam does 14, the pig lost " + lost(pig));
            helper.assertTrue(Math.abs(v.x - 0.7) < 0.01 && Math.abs(v.z) < 0.01, "thrown away from it at 0.7 a tick, motion " + v);
            helper.assertTrue(Math.abs(v.y - 0.35) < 0.01, "and lifted 0.35, motion " + v);
            helper.succeed();
        });
    }

    // ---- the slam's numbers ----------------------------------------------------------------------------------------

    /** The reach is 4 blocks from its middle: a pig 3.8 blocks off is hit, one 4.3 blocks off is not. */
    private static void reach(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        Pig inside = pig(helper, 3.8);
        Pig outside = pig(helper, -4.3);
        helper.runAtTickTime(2, () -> {
            int hurt = b.slam(helper.getLevel());
            helper.assertTrue(hurt == 1, "one pig within 4 blocks is hit, hurt " + hurt);
            helper.assertTrue(inside.isDeadOrDying() || inside.getHealth() < inside.getMaxHealth(), "3.8 blocks off is inside the reach");
            helper.assertTrue(outside.getHealth() >= outside.getMaxHealth(), "4.3 blocks off is outside the reach, lost " + lost(outside));
            helper.succeed();
        });
    }

    /** Only what is on the ground is hit: a pig held 0.8 blocks over the floor (mid-jump) is spared, one on the floor is not. */
    private static void jumpSpared(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        Pig floor = pig(helper, 3.0);
        Pig air = helper.spawn(EntityTypes.PIG, new Vec3(MID - 3.0, 1.8, MID));
        air.setNoAi(true);
        air.setNoGravity(true);
        TestPlayers.whenDone(helper, air::discard);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!BasaltBehemoth.grounded(air) && BasaltBehemoth.grounded(floor), "one pig in the air, one on the floor");
            int hurt = b.slam(helper.getLevel());
            helper.assertTrue(hurt == 1, "only one pig is hit, hurt " + hurt);
            helper.assertTrue(floor.isDeadOrDying() || floor.getHealth() < floor.getMaxHealth(), "the pig on the floor is hit");
            helper.assertTrue(air.getHealth() >= air.getMaxHealth() && air.getDeltaMovement().horizontalDistance() < 0.01,
                    "the pig in the air is neither hurt nor thrown, lost " + lost(air));
            helper.succeed();
        });
    }

    // ---- the open core ---------------------------------------------------------------------------------------------

    /**
     * After the slam the core stays open exactly 60 ticks: gold, armour 0 and glow stage 0 the whole time; then the plates
     * seal (armour 16, stage 1) and it waits 60 ticks before it may rear again.
     */
    private static void openLength(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        Pig pig = pig(helper, 5.0);
        int[] ticks = {0};
        boolean[] open = {true}, sealed = {false};
        helper.runAtTickTime(1, () -> helper.assertTrue(b.rear(pig), "it rears"));
        helper.onEachTick(() -> {
            if (b.core().in(Step.EXPOSED)) {
                ticks[0]++;
                open[0] &= b.vulnerable() && b.getArmorValue() == 0 && b.core().stage() == 0;
            } else if (ticks[0] > 0 && !sealed[0]) {
                sealed[0] = true;
                helper.assertTrue(b.core().idle(), "after the window it is idle, phase " + b.phase());
                helper.assertTrue(!b.vulnerable() && b.getArmorValue() == 16, "sealed: no gold, armour 16, armour " + b.getArmorValue());
                helper.assertTrue(b.core().stage() == 1, "its joints smoulder again, stage " + b.core().stage());
                helper.assertTrue(b.untilRear() == 60, "it waits 60 ticks before it may rear again, " + b.untilRear());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sealed[0], "the core opened and sealed, " + ticks[0] + " ticks open so far");
            helper.assertTrue(ticks[0] == 60, "the core stays open 60 ticks, stayed " + ticks[0]);
            helper.assertTrue(open[0], "gold, armour 0 and every other glow dark the whole time");
        });
    }

    /** Sealed, armour 16 cuts a blow of 10 to 5.6; open, the same blow takes 20 (armour 0, then double). */
    private static void armor(GameTestHelper helper) {
        BasaltBehemoth sealed = unaware(helper);
        BasaltBehemoth open = helper.spawn(ModMobs.BASALT_BEHEMOTH.get(), new Vec3(MID, 1, MID - 3.0));
        open.setNoAi(true);
        TestPlayers.whenDone(helper, open::discard);
        Pig pig = pig(helper, 4.0);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(sealed.getArmorValue() == 16, "sealed, armour 16, got " + sealed.getArmorValue());
            helper.assertTrue(open.startPhase(Step.EXPOSED, 60, null), "the second one opens");
            helper.assertTrue(open.getArmorValue() == 0, "open, armour 0, got " + open.getArmorValue());
            float a = sealed.getHealth(), c = open.getHealth();
            sealed.hurtServer(helper.getLevel(), sealed.damageSources().mobAttack(pig), 10.0F);
            open.hurtServer(helper.getLevel(), open.damageSources().mobAttack(pig), 10.0F);
            float tookSealed = a - sealed.getHealth(), tookOpen = c - open.getHealth();
            helper.assertTrue(Math.abs(tookSealed - 5.6F) < 0.01F, "sealed, a blow of 10 takes 5.6, took " + tookSealed);
            helper.assertTrue(Math.abs(tookOpen - 20.0F) < 0.01F, "open, a blow of 10 takes 20, took " + tookOpen);
            helper.succeed();
        });
    }

    /**
     * Open is no stun: with its core open and a pig 3.5 blocks off as its target it walks up and swats it for 14 (its
     * attack) inside the window. Its follow range is 4, short of any neighbouring arena.
     */
    private static void walksOpen(GameTestHelper helper) {
        BasaltBehemoth b = helper.spawn(ModMobs.BASALT_BEHEMOTH.get(), new Vec3(MID, 1, MID));
        b.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.0);
        TestPlayers.whenDone(helper, b::discard);
        Pig pig = pig(helper, 3.5);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        Vec3[] start = {null};
        double[] moved = {0.0};
        float[] swatted = {0.0F};
        helper.runAtTickTime(1, () -> {
            b.setTarget(pig);
            helper.assertTrue(b.startPhase(Step.EXPOSED, 60, pig), "its core opens");
            start[0] = b.position();
        });
        helper.onEachTick(() -> {
            if (start[0] == null || !b.core().in(Step.EXPOSED)) return;
            moved[0] = Math.max(moved[0], b.position().subtract(start[0]).horizontalDistance());
            swatted[0] = 1000.0F - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(b.core().in(Step.EXPOSED), "still open, phase " + b.phase());
            helper.assertTrue(moved[0] > 0.3, "open, it walks towards its target, moved " + moved[0]);
            helper.assertTrue(swatted[0] > 0.0F, "open, it swats the pig");
            helper.assertTrue(Math.abs(swatted[0] % 14.0F) < 0.01F, "each swat does 14, the pig lost " + swatted[0]);
        });
    }

    /**
     * With its target 3 blocks off it rears at once; once the core seals it rears again exactly 60 ticks later, not
     * before. Its follow range is 4, short of any neighbouring arena; the pig is too sturdy to die.
     */
    private static void cooldown(GameTestHelper helper) {
        BasaltBehemoth b = helper.spawn(ModMobs.BASALT_BEHEMOTH.get(), new Vec3(MID, 1, MID));
        b.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.0);
        TestPlayers.whenDone(helper, b::discard);
        Pig pig = pig(helper, 3.0);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        List<Long> rears = new ArrayList<>();
        long[] sealedAt = {-1};
        Step[] was = {Step.IDLE};
        helper.runAtTickTime(1, () -> b.setTarget(pig));
        helper.onEachTick(() -> {
            Step now = (Step) b.phase();
            if (now == Step.TELL && was[0] != Step.TELL) rears.add(helper.getTick());
            if (now == Step.IDLE && was[0] == Step.EXPOSED && sealedAt[0] < 0) sealedAt[0] = helper.getTick();
            was[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!rears.isEmpty() && rears.getFirst() <= 4, "with its target 3 blocks off it rears at once, rears " + rears);
            helper.assertTrue(sealedAt[0] > 0, "the core opened and sealed");
            helper.assertTrue(rears.size() >= 2, "and it rears again, rears " + rears + ", sealed at " + sealedAt[0]);
            helper.assertTrue(rears.get(1) - sealedAt[0] == 60, "60 ticks after the seal, took " + (rears.get(1) - sealedAt[0]));
        });
    }

    /**
     * A survival player with the Nether's reference armour (a diamond set: armour 20, toughness 8) 2.5 blocks off
     * survives three slams, each costing what the armour formula says for 14 at this difficulty: 4.76 on Normal (2.24
     * on Easy). On Hard (8.61 a slam) only two are survived, which the balance rule doesn't ask of Hard.
     */
    private static void surviveThree(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(MID + 2.5, 1, MID));
        // the set's numbers straight on the player: its equipment would only count once it ticks
        p.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
        p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0);
        // 14 becomes 14 x (1 - (20 - 14 / 4) / 25); mob damage to players scales with difficulty first
        Difficulty difficulty = helper.getLevel().getDifficulty();
        float each = switch (difficulty) {
            case EASY -> 2.24F;
            case HARD -> 8.61F;
            default -> 4.76F;
        };
        int slams = difficulty == Difficulty.HARD ? 2 : 3;
        float[] health = {20.0F};
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(p.getArmorValue() == 20, "armour 20, got " + p.getArmorValue());
            health[0] = p.getHealth();
        });
        for (int i = 0; i < slams; i++) {
            int slam = i + 1;
            helper.runAtTickTime(4 + i * 25, () -> {
                float before = p.getHealth();
                helper.assertTrue(b.slam(helper.getLevel()) == 1, "slam " + slam + " hits the player");
                float lost = before - p.getHealth();
                helper.assertTrue(Math.abs(lost - each) < 0.02F, "slam " + slam + " costs " + each + " here, cost " + lost);
            });
        }
        helper.runAtTickTime(4 + (slams - 1) * 25 + 2, () -> {
            helper.assertTrue(p.isAlive() && p.getHealth() > 0.0F, "the player lives through " + slams + " slams, health " + p.getHealth());
            helper.assertTrue(Math.abs(health[0] - p.getHealth() - slams * each) < 0.05F, slams + " slams cost " + slams * each
                    + ", cost " + (health[0] - p.getHealth()));
            helper.succeed();
        });
    }

    /** A pack that vetoes the slam step: the rear ends with no slam, no open core and no one hurt, and the wait starts. */
    private static void slamVeto(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        Pig pig = pig(helper, 2.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == b && e.step().equals("slam")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] reared = {false}, settled = {false};
        helper.runAtTickTime(1, () -> helper.assertTrue(b.rear(pig), "it rears"));
        helper.onEachTick(() -> {
            if (b.core().in(Step.TELL)) {
                reared[0] = true;
            } else if (reared[0] && !settled[0]) {
                settled[0] = true;
                helper.assertTrue(b.core().idle(), "the vetoed slam leaves it idle, phase " + b.phase());
                helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "no one is hurt");
                helper.assertTrue(!b.vulnerable() && b.getArmorValue() == 16, "the chest stays shut");
                helper.assertTrue(b.untilRear() == 60, "and it waits 60 ticks to rear again, " + b.untilRear());
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(settled[0], "the rear ran and ended, phase " + b.phase()));
    }

    // ---- stats, home, cap ------------------------------------------------------------------------------------------

    /** Health 200, attack 14, armour 16, a 2.4 x 3.2 hitbox, 100 experience; fire never hurts it and blows don't move it. */
    private static void stats(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        Pig pig = pig(helper, 4.0);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(b.getMaxHealth() - 200.0F) < 0.01F && Math.abs(b.getHealth() - 200.0F) < 0.01F,
                    "health 200, got " + b.getHealth() + "/" + b.getMaxHealth());
            helper.assertTrue(Math.abs(b.getAttributeValue(Attributes.ATTACK_DAMAGE) - 14.0) < 0.01, "attack 14, got " + b.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(Math.abs(b.getAttributeValue(Attributes.ARMOR) - 16.0) < 0.01 && b.getArmorValue() == 16, "armour 16, got " + b.getArmorValue());
            helper.assertTrue(Math.abs(b.getBbWidth() - 2.4F) < 0.02F && Math.abs(b.getBbHeight() - 3.2F) < 0.02F,
                    "hitbox 2.4 x 3.2, got " + b.getBbWidth() + " x " + b.getBbHeight());
            int xp = b.getExperienceReward(helper.getLevel(), pig);
            helper.assertTrue(xp == 100, "100 experience, got " + xp);
            helper.assertTrue(b.fireImmune(), "fire never hurts it");
            helper.assertTrue(b.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "blows never move it");
            helper.assertTrue(!b.vulnerable() && b.core().stage() == 1, "sealed and smouldering at rest, stage " + b.core().stage());
            helper.succeed();
        });
    }

    /**
     * Home: the basalt deltas and nowhere else (its biome tag, which the spawner reads), placed only by the Teeming
     * spawner's apex rule: on no biome list, so no biome modifier; alone (groups of 1); weight 1 among apex candidates;
     * an apex.
     */
    private static void home(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("basalt_behemoth");
        helper.assertTrue(row.apex() && row.dim() == RosterStats.Dim.NETHER, "a Nether apex");
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("basalt_behemoth"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().size() == 1 && tag.get().stream().anyMatch(b -> b.is(Biomes.BASALT_DELTAS)),
                "its home holds the basalt deltas only: " + tag.get());
        var rule = com.panda.teeming.spawn.TeemingSpawner.Rule.of(row.spawner());
        helper.assertTrue(rule == com.panda.teeming.spawn.TeemingSpawner.Rule.APEX_GROUND && rule.apex() && rule.dimension == net.minecraft.world.level.Level.NETHER,
                "the Nether apex rule places it, got '" + row.spawner() + "'");
        helper.assertTrue(!row.biomeSpawns(), "on no biome list");
        helper.assertTrue(access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/basalt_behemoth"))).isEmpty(), "no biome modifier");
        helper.assertTrue(row.weight() == 1, "weight 1, got " + row.weight());
        helper.assertTrue(row.groupMin() == 1 && row.groupMax() == 1, "alone, got " + row.groupMin() + ".." + row.groupMax());
        helper.succeed();
    }

    /**
     * Local cap: one in 128 blocks. With one here a second is refused 127 blocks above it and allowed 132 above (its
     * 3.2-block body then clears the 128-block box). Straight up, so no other arena's mobs are in either box.
     */
    private static void cap(GameTestHelper helper) {
        BasaltBehemoth b = unaware(helper);
        b.setNoAi(true);
        RosterStats.Entry row = RosterStats.get("basalt_behemoth");
        BlockPos at = helper.absolutePos(new BlockPos((int) MID, 1, (int) MID));
        helper.runAtTickTime(2, () -> {
            var type = ModMobs.BASALT_BEHEMOTH.get();
            helper.assertTrue(!TeemingSpawns.underCap(row, type, helper.getLevel(), at), "a second beside it is refused");
            helper.assertTrue(!TeemingSpawns.underCap(row, type, helper.getLevel(), at.above(127)), "a second 127 blocks off is refused");
            helper.assertTrue(TeemingSpawns.underCap(row, type, helper.getLevel(), at.above(132)), "a second 132 blocks off may spawn");
            helper.succeed();
        });
    }
}
