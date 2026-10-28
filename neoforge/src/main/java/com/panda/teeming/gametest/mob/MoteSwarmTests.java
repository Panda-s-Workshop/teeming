package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.MoteSwarm;
import com.panda.teeming.mob.end.MoteSwarm.Blow;
import com.panda.teeming.mob.end.MoteSwarm.Step;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Mote Swarm: health 30, attack 1 a mote, armour 0, a 1.5 x 1.5 hitbox. Wrapped round its target for 10 ticks it
 * gathers for 8 (the tell), then surges for 10: each mote that touches deals 1 through armour, at most 3 on one body a
 * tick, as many motes as it has left (one a point of health, 30 at full). A plain single-target hit does a quarter;
 * sweeps, explosions, splash and lingering potions and other area damage do triple. Small end islands and end midlands,
 * weight 4, alone. Every number is checked against a plain value.
 *
 * <p>Swarms that hunt a pig here have a follow range of 1, so they never pick up a survivor in the arena next door; the
 * pig is handed to them. Pigs are NoAI and given 100 health, so a whole surge never kills one.
 */
public final class MoteSwarmTests {
    private MoteSwarmTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("mote_swarm_tell", MoteSwarmTests::tell);
        out.accept("mote_swarm_tell_cancel", MoteSwarmTests::tellCancel);
        out.accept("mote_swarm_counter", MoteSwarmTests::counter);
        out.accept("mote_swarm_failure", MoteSwarmTests::failure);
        out.accept("mote_swarm_armour", MoteSwarmTests::armour);
        out.accept("mote_swarm_thin_surge", MoteSwarmTests::thinSurge);
        out.accept("mote_swarm_motes_drawn", MoteSwarmTests::motesDrawn);
        out.accept("mote_swarm_sword", MoteSwarmTests::sword);
        out.accept("mote_swarm_projectiles", MoteSwarmTests::projectiles);
        out.accept("mote_swarm_explosion", MoteSwarmTests::explosion);
        out.accept("mote_swarm_splash", MoteSwarmTests::splash);
        out.accept("mote_swarm_lingering", MoteSwarmTests::lingering);
        out.accept("mote_swarm_area", MoteSwarmTests::area);
        out.accept("mote_swarm_plain", MoteSwarmTests::plain);
        out.accept("mote_swarm_step_out", MoteSwarmTests::stepOut);
        out.accept("mote_swarm_dead", MoteSwarmTests::dead);
        out.accept("mote_swarm_in_place", MoteSwarmTests::inPlace);
        out.accept("mote_swarm_drift", MoteSwarmTests::drift);
        out.accept("mote_swarm_stats", MoteSwarmTests::stats);
        out.accept("mote_swarm_home", MoteSwarmTests::home);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    /** A swarm with the base look at full health, removed when the test ends; follow range 1 unless a test widens it. */
    private static MoteSwarm swarm(GameTestHelper helper, double x, double y, double z, boolean ai) {
        MoteSwarm s = helper.spawn(ModMobs.MOTE_SWARM.get(), new Vec3(x, y, z));
        s.core().setLook("base", true);
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        s.setNoAi(!ai);
        TestPlayers.whenDone(helper, s::discard);
        return s;
    }

    /** A NoAI pig with 100 health standing at (x, z). */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** A swarm with its AI on, wrapped round a pig of its own and hunting it. */
    private static MoteSwarm onPig(GameTestHelper helper, Pig pig) {
        MoteSwarm s = swarm(helper, helper.relativeVec(pig.position()).x, 1.0, helper.relativeVec(pig.position()).z, true);
        s.setTarget(pig);
        return s;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.01F;
    }

    /** Hurts {@code s} with {@code source} and returns what it lost; its hurt cooldown is cleared first. */
    private static float lost(MoteSwarm s, DamageSource source, float amount) {
        s.damageCooldownTime = 0;
        float before = s.getHealth();
        s.hurtServer((ServerLevel) s.level(), source, amount);
        return before - s.getHealth();
    }

    /** A survival player holding a diamond sword (7), swung up to full charge and standing on the floor. */
    private static ServerPlayer swordsman(GameTestHelper helper, double x, double z) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(x, 1, z));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        for (int i = 0; i < 25; i++) p.doTick();     // the sword counts from the player's next tick; then the swing charges
        p.setOnGround(true);                          // no client reports it: a sweep needs it
        return p;
    }

    // ---- tell, counter, failure --------------------------------------------------------------------------------------

    /**
     * The gather: wrapped round a pig, the swarm gathers only after 10 ticks; the gather lasts exactly 8 ticks, glowing
     * its flare (stage 1) and hanging still (it moves less than 0.01 blocks), and the pig loses nothing in it. On the
     * tick it ends the surge begins and the first motes land: exactly 3.
     */
    private static void tell(GameTestHelper helper) {
        Pig pig = pig(helper, 5.5, 5.5);
        MoteSwarm s = onPig(helper, pig);
        long[] startedAt = {-1}, hitAt = {-1};
        int[] ticks = {0};
        boolean[] quiet = {true}, flare = {true}, surging = {false};
        float[] first = {0.0F};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (s.core().in(Step.TELL)) {
                if (startedAt[0] < 0) {
                    startedAt[0] = now;
                    start[0] = s.position();
                }
                ticks[0]++;
                moved[0] = Math.max(moved[0], s.position().distanceTo(start[0]));
                if (s.core().stage() != MoteSwarm.STAGE_FLARE) flare[0] = false;
                if (pig.getHealth() < 100.0F) quiet[0] = false;
            }
            if (hitAt[0] < 0 && pig.getHealth() < 100.0F) {
                hitAt[0] = now;
                first[0] = 100.0F - pig.getHealth();
                surging[0] = s.core().in(Step.SURGE);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hitAt[0] >= 0, "it gathered and surged on the pig, phase " + s.phase());
            helper.assertTrue(startedAt[0] >= 10, "it gathers only after 10 ticks wrapped round the pig, at tick " + startedAt[0]);
            helper.assertTrue(ticks[0] == 8, "the gather lasts 8 ticks, " + ticks[0]);
            helper.assertTrue(quiet[0], "nothing lands while it gathers");
            helper.assertTrue(flare[0], "it glows its flare all through the gather");
            helper.assertTrue(moved[0] < 0.01, "it hangs still while it gathers, moved " + moved[0]);
            helper.assertTrue(hitAt[0] == startedAt[0] + 8 && surging[0], "the first motes land as the gather ends, at tick " + hitAt[0]
                    + ", the gather began at " + startedAt[0] + ", surging " + surging[0]);
            helper.assertTrue(near(first[0], 3.0F), "the first tick of the surge lands 3 motes of 1, took " + first[0]);
        });
    }

    /** A listener that cancels the "tell" step: wrapped round a pig for 70 ticks, the swarm never gathers or surges. */
    private static void tellCancel(GameTestHelper helper) {
        Pig pig = pig(helper, 5.5, 5.5);
        MoteSwarm s = onPig(helper, pig);
        int[] vetoed = {0};
        boolean[] calm = {true};
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == s && e.step().equals("tell")) {
                e.cancel();
                vetoed[0]++;
            }
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.onEachTick(() -> {
            if (!s.core().idle()) calm[0] = false;
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(vetoed[0] >= 3, "it tried to gather every 10 ticks, " + vetoed[0] + " vetoed");
            helper.assertTrue(calm[0] && s.surges() == 0, "it never gathers or surges, surges " + s.surges());
            helper.assertTrue(pig.getHealth() == 100.0F, "the pig loses nothing, health " + pig.getHealth());
            helper.succeed();
        });
    }

    /**
     * The counter, a sweep: a survival player swings a diamond sword (7) at full charge at a pig beside the swarm. The
     * swing lands 7 on the pig and sweeps 1 into the swarm, which takes it triple: exactly 3, leaving 27, and it draws
     * 27 motes.
     */
    private static void counter(GameTestHelper helper) {
        Pig target = helper.spawn(EntityTypes.PIG, new Vec3(5.5, 1, 5.0));
        target.setNoAi(true);
        TestPlayers.whenDone(helper, target::discard);
        MoteSwarm s = swarm(helper, 6.6, 1.0, 5.6, false);
        helper.runAtTickTime(2, () -> {
            ServerPlayer p = swordsman(helper, 5.5, 3.5);
            helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) == 7.0, "the sword hits for 7, " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(p.getAttackStrengthScale(0.5F) >= 0.9F, "the swing is charged, " + p.getAttackStrengthScale(0.5F));
            p.attack(target);
            helper.assertTrue(near(target.getHealth(), 3.0F), "the pig takes the swing's 7, health " + target.getHealth());
            helper.assertTrue(near(s.getHealth(), 27.0F), "the sweep's 1 lands triple: 3, leaving 27, health " + s.getHealth());
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(s.motes() == 27 && s.shownMotes() == 27, "it has 27 motes left and draws 27, " + s.motes() + "/" + s.shownMotes());
            helper.succeed();
        });
    }

    /**
     * Failing: a pig stays inside through a whole surge of a full swarm. It loses 3 on each of exactly 10 ticks, 30 in
     * all, never more than 3 in one tick, in one surge.
     */
    private static void failure(GameTestHelper helper) {
        Pig pig = pig(helper, 5.5, 5.5);
        MoteSwarm s = onPig(helper, pig);
        List<Float> losses = new ArrayList<>();
        float[] last = {100.0F};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            float h = pig.getHealth();
            if (h < last[0]) losses.add(last[0] - h);
            last[0] = h;
            if (s.core().in(Step.LOOSEN)) done[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "the surge is over, phase " + s.phase());
            helper.assertTrue(losses.size() == 10, "motes land on 10 ticks, " + losses);
            helper.assertTrue(losses.stream().allMatch(l -> near(l, 3.0F)), "3 motes of 1 land each tick, never more, " + losses);
            helper.assertTrue(near(100.0F - pig.getHealth(), 30.0F), "a full swarm's surge deals 30, took " + (100.0F - pig.getHealth()));
            helper.assertTrue(s.surges() == 1 && s.stings() == 30, "one surge, 30 motes, " + s.surges() + "/" + s.stings());
        });
    }

    /**
     * Armour counts, mote by mote: a pig with 20 armour takes each mote's 1 as 1 x (1 - 19.5 / 25) = 0.22, so 0.66 a
     * tick of the surge and 6.6 over it.
     */
    private static void armour(GameTestHelper helper) {
        Pig pig = pig(helper, 5.5, 5.5);
        pig.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
        MoteSwarm s = onPig(helper, pig);
        List<Float> losses = new ArrayList<>();
        float[] last = {100.0F};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            float h = pig.getHealth();
            if (h < last[0]) losses.add(last[0] - h);
            last[0] = h;
            if (s.core().in(Step.LOOSEN)) done[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "the surge is over, phase " + s.phase());
            helper.assertTrue(losses.size() == 10 && losses.stream().allMatch(l -> near(l, 0.66F)), "0.66 a tick through 20 armour, " + losses);
            helper.assertTrue(Math.abs(100.0F - pig.getHealth() - 6.6F) < 0.05F, "6.6 over the surge, took " + (100.0F - pig.getHealth()));
        });
    }

    /** A thinner swarm stings less: at 12 health it has 12 motes, and its surge lands 3 on each of 4 ticks, 12 in all. */
    private static void thinSurge(GameTestHelper helper) {
        Pig pig = pig(helper, 5.5, 5.5);
        MoteSwarm s = onPig(helper, pig);
        s.setHealth(12.0F);
        List<Float> losses = new ArrayList<>();
        float[] last = {100.0F};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            float h = pig.getHealth();
            if (h < last[0]) losses.add(last[0] - h);
            last[0] = h;
            if (s.core().in(Step.LOOSEN)) done[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "the surge is over, phase " + s.phase());
            helper.assertTrue(losses.size() == 4 && losses.stream().allMatch(l -> near(l, 3.0F)), "3 on each of 4 ticks, " + losses);
            helper.assertTrue(near(100.0F - pig.getHealth(), 12.0F), "12 motes, 12 damage, took " + (100.0F - pig.getHealth()));
        });
    }

    /**
     * The motes it draws fall with its health, one for each point of 30 rounded up, and the model's synced count
     * follows: 30 at 30, 30 at 29.5, 15 at 15, 8 at 7.5, 1 at 1 and at 0.4.
     */
    private static void motesDrawn(GameTestHelper helper) {
        MoteSwarm s = swarm(helper, 5.5, 1.0, 5.5, false);
        float[] health = {30.0F, 29.5F, 15.0F, 7.5F, 1.0F, 0.4F};
        int[] want = {30, 30, 15, 8, 1, 1};
        for (int i = 0; i < health.length; i++) {
            int k = i;
            helper.runAtTickTime(2 + 3 * k, () -> s.setHealth(health[k]));
            helper.runAtTickTime(4 + 3 * k, () -> helper.assertTrue(s.motes() == want[k] && s.shownMotes() == want[k],
                    "at " + health[k] + " health it has " + want[k] + " motes, " + s.motes() + ", draws " + s.shownMotes()));
        }
        helper.runAtTickTime(4 + 3 * health.length, helper::succeed);
    }

    // ---- damage taken ------------------------------------------------------------------------------------------------

    /**
     * A sword's main hit is single-target: a survival player swings a diamond sword (7) at full charge straight at the
     * swarm, nothing beside it; it takes a quarter, 1.75.
     */
    private static void sword(GameTestHelper helper) {
        MoteSwarm s = swarm(helper, 5.5, 1.0, 6.0, false);
        helper.runAtTickTime(2, () -> {
            ServerPlayer p = swordsman(helper, 5.5, 4.0);
            p.attack(s);
            helper.assertTrue(near(30.0F - s.getHealth(), 1.75F), "the sword's 7 lands a quarter, 1.75, took " + (30.0F - s.getHealth()));
            helper.succeed();
        });
    }

    /** Projectiles are single-target: an arrow's 8 and a trident's 8 each do a quarter, 2. */
    private static void projectiles(GameTestHelper helper) {
        MoteSwarm s = swarm(helper, 5.5, 1.0, 5.5, false);
        helper.runAtTickTime(2, () -> {
            Level level = helper.getLevel();
            var arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.LOAD);
            var trident = EntityTypes.TRIDENT.create(level, EntitySpawnReason.LOAD);
            DamageSource shot = s.damageSources().arrow(arrow, null), thrown = s.damageSources().trident(trident, null);
            helper.assertTrue(s.blow(shot) == Blow.SINGLE && s.blow(thrown) == Blow.SINGLE, "both are single-target");
            float a = lost(s, shot, 8.0F), t = lost(s, thrown, 8.0F);
            helper.assertTrue(near(a, 2.0F), "an arrow's 8 does 2, " + a);
            helper.assertTrue(near(t, 2.0F), "a trident's 8 does 2, " + t);
            helper.succeed();
        });
    }

    /**
     * A real explosion of power 0.5 at the swarm's feet, in the open: 8 before the swarm's rule ((1 + 1) / 2 x 7 x 1 +
     * 1), so it takes 24 and is left with 6.
     */
    private static void explosion(GameTestHelper helper) {
        MoteSwarm s = swarm(helper, 5.5, 2.0, 5.5, false);
        helper.runAtTickTime(2, () -> {
            helper.getLevel().explode(null, s.getX(), s.getY(), s.getZ(), 0.5F, Level.ExplosionInteraction.NONE);
            helper.assertTrue(near(s.getHealth(), 6.0F), "the explosion's 8 lands triple, 24, leaving 6, health " + s.getHealth());
            helper.succeed();
        });
    }

    /** A real splash potion of Harming dropped onto it: a direct hit's 6, triple, 18, leaving 12. */
    private static void splash(GameTestHelper helper) {
        MoteSwarm s = swarm(helper, 5.5, 2.0, 5.5, false);
        helper.runAtTickTime(2, () -> {
            Vec3 at = helper.absoluteVec(new Vec3(5.5, 5.2, 5.5));
            ThrownSplashPotion potion = new ThrownSplashPotion(helper.getLevel(), at.x, at.y, at.z,
                    PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING));
            potion.setDeltaMovement(0, -0.5, 0);
            helper.getLevel().addFreshEntity(potion);
            TestPlayers.whenDone(helper, potion::discard);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(s.getHealth() < 30.0F, "the potion has landed");
            helper.assertTrue(near(s.getHealth(), 12.0F), "Harming's 6 lands triple, 18, leaving 12, health " + s.getHealth());
        });
    }

    /** A real lingering cloud of Harming round it: its first dose, 3 (half of 6), lands triple, 9. */
    private static void lingering(GameTestHelper helper) {
        MoteSwarm s = swarm(helper, 5.5, 2.0, 5.5, false);
        float[] first = {0.0F};
        helper.runAtTickTime(2, () -> {
            AreaEffectCloud cloud = new AreaEffectCloud(helper.getLevel(), s.getX(), s.getY(), s.getZ());
            cloud.setRadius(2.0F);
            cloud.setWaitTime(0);
            cloud.setDuration(80);
            cloud.setPotionContents(new PotionContents(Potions.HARMING));
            helper.getLevel().addFreshEntity(cloud);
            TestPlayers.whenDone(helper, cloud::discard);
        });
        helper.onEachTick(() -> {
            if (first[0] == 0.0F && s.getHealth() < 30.0F) first[0] = 30.0F - s.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first[0] > 0.0F, "the cloud has dosed it");
            helper.assertTrue(near(first[0], 9.0F), "the cloud's 3 lands triple, 9, took " + first[0]);
        });
    }

    /** Other area damage is triple too: dragon's breath, lightning, a firework and an explosion's 2 each do 6. */
    private static void area(GameTestHelper helper) {
        MoteSwarm s = swarm(helper, 5.5, 1.0, 5.5, false);
        helper.runAtTickTime(2, () -> {
            var rocket = EntityTypes.FIREWORK_ROCKET.create(helper.getLevel(), EntitySpawnReason.LOAD);
            DamageSource[] sources = {s.damageSources().dragonBreath(), s.damageSources().lightningBolt(),
                    s.damageSources().fireworks(rocket, null), s.damageSources().explosion(null, null)};
            String[] names = {"dragon's breath", "lightning", "a firework", "an explosion"};
            for (int i = 0; i < sources.length; i++) {
                helper.assertTrue(s.blow(sources[i]) == Blow.SPREAD, names[i] + " spreads");
                float l = lost(s, sources[i], 2.0F);
                helper.assertTrue(near(l, 6.0F), names[i] + "'s 2 does 6, " + l);
            }
            helper.assertTrue(near(s.getHealth(), 6.0F), "24 in all, health " + s.getHealth());
            helper.succeed();
        });
    }

    /** Everything else comes as it is: a fall, magic with no thrower and a plain hurt each take their 2. */
    private static void plain(GameTestHelper helper) {
        MoteSwarm s = swarm(helper, 5.5, 1.0, 5.5, false);
        helper.runAtTickTime(2, () -> {
            DamageSource[] sources = {s.damageSources().fall(), s.damageSources().magic(), s.damageSources().generic()};
            String[] names = {"a fall", "magic", "a plain hurt"};
            for (int i = 0; i < sources.length; i++) {
                helper.assertTrue(s.blow(sources[i]) == Blow.PLAIN, names[i] + " is plain");
                float l = lost(s, sources[i], 2.0F);
                helper.assertTrue(near(l, 2.0F), names[i] + "'s 2 does 2, " + l);
            }
            helper.succeed();
        });
    }

    // ---- the surge ---------------------------------------------------------------------------------------------------

    /**
     * Stepping out of the cloud while it gathers: the pig leaves 3 blocks on the gather's first tick. The swarm still
     * surges where it gathered, but no mote lands and the pig loses nothing.
     */
    private static void stepOut(GameTestHelper helper) {
        Pig pig = pig(helper, 4.5, 5.5);
        MoteSwarm s = onPig(helper, pig);
        boolean[] moved = {false}, done = {false};
        helper.onEachTick(() -> {
            if (!moved[0] && s.core().in(Step.TELL)) {
                moved[0] = true;
                Vec3 to = pig.position().add(3.0, 0.0, 0.0);
                pig.teleportTo(to.x, to.y, to.z);
            }
            if (s.core().in(Step.LOOSEN)) done[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "it gathered and surged, phase " + s.phase());
            helper.assertTrue(s.surges() == 1 && s.stings() == 0, "it surged where it gathered and no mote landed, " + s.surges() + "/" + s.stings());
            helper.assertTrue(pig.getHealth() == 100.0F, "the pig loses nothing, health " + pig.getHealth());
        });
    }

    /**
     * Nothing after death: one swarm is killed 4 ticks into its gather, another 3 ticks into its surge. Over the next 30
     * ticks the first pig loses nothing, and the second loses nothing more than it had lost when its swarm died.
     */
    private static void dead(GameTestHelper helper) {
        Pig a = pig(helper, 3.5, 5.5), b = pig(helper, 8.5, 5.5);
        MoteSwarm sa = onPig(helper, a), sb = onPig(helper, b);
        long[] killedA = {-1}, killedB = {-1};
        float[] atDeath = {0.0F};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (killedA[0] < 0 && sa.core().in(Step.TELL) && sa.core().elapsed() == 4) {
                sa.kill(helper.getLevel());
                killedA[0] = now;
            }
            if (killedB[0] < 0 && sb.core().in(Step.SURGE) && sb.core().elapsed() == 3) {
                sb.kill(helper.getLevel());
                killedB[0] = now;
                atDeath[0] = b.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            long now = helper.getTick();
            helper.assertTrue(killedA[0] >= 0 && killedB[0] >= 0 && now >= Math.max(killedA[0], killedB[0]) + 30, "both killed, 30 ticks gone");
            helper.assertTrue(a.getHealth() == 100.0F, "killed mid-gather it lands nothing, the pig at " + a.getHealth());
            helper.assertTrue(atDeath[0] < 100.0F && b.getHealth() == atDeath[0], "killed mid-surge no mote lands after, " + atDeath[0] + " -> " + b.getHealth());
        });
    }

    /**
     * The motes hurt in place: a survival player (given 100 health) standing in the swarm, which finds them by itself
     * (follow range 4) and gathers on them, takes all 30 motes of its surge but is never moved: no motion and less than
     * 0.01 blocks across from where they stood.
     */
    private static void inPlace(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 5.5));
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);     // a whole surge, 30 with no armour, would kill a player of 20
        p.setHealth(100.0F);
        MoteSwarm s = swarm(helper, 5.5, 1.0, 5.5, true);
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.0);
        Vec3 start = p.position();
        double[] drift = {0.0}, speed = {0.0};
        boolean[] done = {false};
        Object[] gatheredOn = {null};
        helper.onEachTick(() -> {
            p.doTick();
            if (done[0]) return;
            if (gatheredOn[0] == null && s.core().in(Step.TELL)) gatheredOn[0] = s.core().target();
            drift[0] = Math.max(drift[0], Math.hypot(p.getX() - start.x, p.getZ() - start.z));
            speed[0] = Math.max(speed[0], p.getDeltaMovement().horizontalDistance());
            if (s.core().in(Step.LOOSEN)) done[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0], "it found the player and surged, phase " + s.phase() + ", target " + s.getTarget());
            helper.assertTrue(gatheredOn[0] == p, "it found the player by itself and gathered on them, gathered on " + gatheredOn[0] + ", now hunting " + s.getTarget());
            helper.assertTrue(p.isAlive() && p.getHealth() < 100.0F && s.stings() == 30, "the whole surge landed on them, health " + p.getHealth() + ", " + s.stings() + " motes");
            helper.assertTrue(speed[0] < 1.0E-3 && drift[0] < 0.01, "never moved: speed " + speed[0] + ", drift " + drift[0]);
        });
    }

    /**
     * It drifts after a survival player 6 blocks off (follow range 8), never faster than 0.15 blocks a tick, and wraps
     * round them.
     */
    private static void drift(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(2.5, 1, 5.5));
        MoteSwarm s = swarm(helper, 8.5, 1.0, 5.5, true);
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0);
        double[] top = {0.0};
        Vec3[] last = {null};
        helper.onEachTick(() -> {
            Vec3 now = s.position();
            if (last[0] != null) top[0] = Math.max(top[0], now.distanceTo(last[0]));
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(s.touches(p), "it reaches the player, " + s.position().distanceTo(p.position()) + " off");
            helper.assertTrue(top[0] <= 0.15 + 1.0E-3, "never faster than 0.15 blocks a tick, top " + top[0]);
        });
    }

    // ---- numbers -----------------------------------------------------------------------------------------------------

    /** Health 30, attack 1 (a mote), armour 0, a 1.5 x 1.5 hitbox, 30 motes at full. */
    private static void stats(GameTestHelper helper) {
        MoteSwarm s = swarm(helper, 5.5, 1.0, 5.5, false);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.getMaxHealth() == 30.0F && s.getHealth() == 30.0F, "health 30, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(s.getAttributeValue(Attributes.ATTACK_DAMAGE) == 1.0, "attack 1, got " + s.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(s.getArmorValue() == 0, "armour 0, got " + s.getArmorValue());
            helper.assertTrue(near(s.getBbWidth(), 1.5F) && near(s.getBbHeight(), 1.5F), "a 1.5 x 1.5 hitbox, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.assertTrue(s.motes() == 30, "30 motes at full health, " + s.motes());
            helper.succeed();
        });
    }

    /**
     * Home: small end islands and end midlands, never the highlands or the main island; its own spawn modifier weighs 4,
     * alone; a monster. Its ground spawn rule takes end stone and refuses a spot with nothing under it.
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("mote_swarm"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SMALL_END_ISLANDS)), "its home holds the small end islands");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "its home holds the end midlands");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.THE_END)), "never the highlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/mote_swarm")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.MOTE_SWARM.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.MOTE_SWARM.get().getCategory() == MobCategory.MONSTER, "a monster");

        RosterStats.Entry row = RosterStats.get("mote_swarm");
        helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground spawn, place " + row.place());
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.END_STONE);
        helper.setBlock(new BlockPos(9, 0, 9), Blocks.AIR);
        var type = ModMobs.MOTE_SWARM.get();
        helper.assertTrue(TeemingSpawns.placeOk(row, type, helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))), "end stone is a spawn spot");
        helper.assertTrue(!TeemingSpawns.placeOk(row, type, helper.getLevel(), helper.absolutePos(new BlockPos(9, 1, 9))), "a spot with nothing under it is not");
        helper.succeed();
    }
}
