package com.panda.teeming.gametest.mob;

import com.panda.teeming.advancement.TeemingAdvancements;
import com.panda.teeming.gametest.AdvancementTests;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.nether.SlagheartGolem;
import com.panda.teeming.mob.nether.SlagheartGolem.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Slagheart Golem: it spawns at heat 6 and every hit heats it by 2, so a straight melee fight erupts before it falls;
 * full heat erupts, thrown cold items ({@code #teeming:cold_items}: snowballs) and powder snow quench it, quenched it
 * takes x1.5.
 */
public final class SlagheartGolemTests {
    private SlagheartGolemTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("slagheart_golem_tell", SlagheartGolemTests::tell);
        out.accept("slagheart_golem_counter", SlagheartGolemTests::snowballStopsEruption);
        out.accept("slagheart_golem_failure", SlagheartGolemTests::heatErupts);
        out.accept("slagheart_golem_snowball_cools", SlagheartGolemTests::snowballCools);
        out.accept("slagheart_golem_powder_snow_quench", SlagheartGolemTests::powderSnowQuench);
        out.accept("slagheart_golem_reach", SlagheartGolemTests::reach);
        out.accept("slagheart_golem_heat_clock", SlagheartGolemTests::heatClock);
        out.accept("slagheart_golem_splash", SlagheartGolemTests::splash);
        out.accept("slagheart_golem_quench_length", SlagheartGolemTests::quenchLength);
        out.accept("slagheart_golem_feat", SlagheartGolemTests::feat);
        out.accept("slagheart_golem_cold_items", SlagheartGolemTests::coldItems);
        out.accept("slagheart_golem_fight_erupts", SlagheartGolemTests::fightErupts);
        TeemingGameTests.timeLimit("slagheart_golem_fight_erupts", 200);
    }

    private static SlagheartGolem golem(GameTestHelper helper) {
        SlagheartGolem golem = helper.spawn(ModMobs.SLAGHEART_GOLEM.get(), new Vec3(5.5, 1, 5.5));
        golem.setNoAi(true);
        return golem;
    }

    /** At heat 12 it shakes for 20 ticks (the tell) and hurts nothing until the eruption. */
    private static void tell(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(7.5, 1, 5.5));
        pig.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            golem.setHeat(SlagheartGolem.MAX_HEAT);
            helper.assertTrue(golem.core().in(Step.TELL), "heat 12 starts the tell, phase " + golem.phase());
        });
        helper.runAtTickTime(2 + SlagheartGolem.TELL_TICKS - 2, () -> {
            helper.assertTrue(golem.core().in(Step.TELL), "the tell lasts 20 ticks, phase " + golem.phase());
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "nothing is hurt during the tell");
            helper.succeed();
        });
    }

    /** The counter: a snowball during the tell cools it to 10 and the eruption never comes. */
    private static void snowballStopsEruption(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(7.5, 1, 5.5));
        pig.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            golem.setHeat(SlagheartGolem.MAX_HEAT);
            Snowball ball = helper.spawn(EntityTypes.SNOWBALL, new Vec3(5.5, 2.5, 2.5));
            ball.setDeltaMovement(0, 0, 0.8);
        });
        helper.runAtTickTime(2 + SlagheartGolem.TELL_TICKS + SlagheartGolem.ERUPT_TICKS + 5, () -> {
            helper.assertTrue(golem.heat() == SlagheartGolem.MAX_HEAT - SlagheartGolem.SNOWBALL, "the snowball cooled it to 10, heat " + golem.heat());
            helper.assertTrue(golem.core().idle(), "the tell ended without an eruption, phase " + golem.phase());
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "nothing erupted onto the pig");
            helper.succeed();
        });
    }

    /** Failing the counter: full heat erupts, 10 damage within 5 blocks, then heat 6. */
    private static void heatErupts(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(7.5, 1, 5.5));
        pig.setNoAi(true);
        golem.setHeat(SlagheartGolem.MAX_HEAT - 2);
        helper.runAtTickTime(2, () -> {
            golem.hurtServer(helper.getLevel(), golem.damageSources().magic(), 1.0F);
            helper.assertTrue(golem.heat() == SlagheartGolem.MAX_HEAT, "a hit heats it by 2, heat " + golem.heat());
            helper.assertTrue(golem.core().in(Step.TELL), "full heat starts the eruption tell, phase " + golem.phase());
            helper.assertTrue(golem.core().stage() == 3, "full heat glows at stage 3, stage " + golem.core().stage());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.isDeadOrDying() || pig.getHealth() <= pig.getMaxHealth() - SlagheartGolem.ERUPT_DAMAGE + 0.01F,
                    "the eruption hits the pig 2 blocks away for 10, health " + pig.getHealth());
            helper.assertTrue(golem.heat() == SlagheartGolem.AFTER_ERUPTION, "after the eruption heat is 6, heat " + golem.heat());
        });
    }

    private static void snowballCools(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        helper.assertTrue(golem.heat() == 6 && golem.core().stage() == 2, "it spawns at heat 6, glowing at stage 2, stage " + golem.core().stage());
        golem.setHeat(9);
        Snowball ball = helper.spawn(EntityTypes.SNOWBALL, new Vec3(5.5, 2.5, 2.5));
        ball.setDeltaMovement(0, 0, 0.8);
        helper.succeedWhen(() -> helper.assertTrue(golem.heat() == 9 - SlagheartGolem.SNOWBALL, "a snowball cools it by 2, heat " + golem.heat()));
    }

    private static void powderSnowQuench(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        golem.setNoAi(false);
        golem.setHeat(10);
        helper.runAtTickTime(2, () -> helper.setBlock(new BlockPos(5, 1, 5), Blocks.POWDER_SNOW));
        helper.succeedWhen(() -> {
            helper.assertTrue(golem.heat() == 0 && golem.core().in(Step.QUENCHED), "powder snow quenches it, heat " + golem.heat() + ", " + golem.phase());
            helper.assertTrue(golem.vulnerable(), "quenched, its weak point shows");
            float before = golem.getHealth();
            golem.hurtServer(helper.getLevel(), golem.damageSources().magic(), 4.0F);
            float lost = before - golem.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F * SlagheartGolem.QUENCHED_DAMAGE) < 0.01F, "quenched it takes x1.5, lost " + lost);
        });
    }

    /** The eruption reaches 5 blocks, exactly where its ring is drawn: 4.5 blocks away is hit, 5.3 is not. */
    private static void reach(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        Pig inside = helper.spawn(EntityTypes.PIG, new Vec3(1.0, 1, 5.5));
        Pig outside = helper.spawn(EntityTypes.PIG, new Vec3(5.5 + 3.75, 1, 5.5 + 3.75));
        inside.setNoAi(true);
        outside.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            int hit = golem.erupt(helper.getLevel());
            helper.assertTrue(hit == 1, "one pig within 5 blocks is hit, hit " + hit);
            helper.assertTrue(inside.isDeadOrDying() || inside.getHealth() < inside.getMaxHealth(), "4.5 blocks away is inside the reach");
            helper.assertTrue(outside.getHealth() >= outside.getMaxHealth() && !outside.isOnFire(), "5.3 blocks away is outside the reach");
            helper.succeed();
        });
    }

    /** While it has a target it heats by 1 every 60 ticks. */
    private static void heatClock(GameTestHelper helper) {
        SlagheartGolem golem = helper.spawn(ModMobs.SLAGHEART_GOLEM.get(), new Vec3(5.5, 1, 5.5));
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(9.5, 1, 5.5));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);              // outlasts the golem's blows, so the target stays
        pig.setHealth(1000.0F);
        helper.runAtTickTime(1, () -> golem.setTarget(pig));
        helper.runAtTickTime(SlagheartGolem.HEAT_EVERY - 6, () -> helper.assertTrue(golem.heat() == 6, "no heat before 60 ticks, heat " + golem.heat()));
        helper.runAtTickTime(SlagheartGolem.HEAT_EVERY + 6, () -> {
            helper.assertTrue(golem.heat() == 7, "60 ticks with a target heat it by 1, heat " + golem.heat());
            helper.succeed();
        });
    }

    /** A splash water bottle cools it by 4. */
    private static void splash(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        golem.setHeat(9);
        helper.runAtTickTime(2, () -> {
            Vec3 at = helper.absoluteVec(new Vec3(5.5, 4.5, 5.5));
            ThrownSplashPotion potion = new ThrownSplashPotion(helper.getLevel(), at.x, at.y, at.z,
                    PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));
            potion.setDeltaMovement(0, -0.6, 0);
            helper.getLevel().addFreshEntity(potion);
        });
        helper.succeedWhen(() -> helper.assertTrue(golem.heat() == 9 - SlagheartGolem.SPLASH, "splash water cools it by 4, heat " + golem.heat()));
    }

    /**
     * Cooled Off: the snowball that brings a golem to heat 0 and quenches it earns its thrower the advancement. Here a
     * survival player's first snowball takes heat 4 to 2 (nothing yet), their second quenches it (earned); a snowball
     * nobody threw quenches a second golem and earns nobody anything.
     */
    private static void feat(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        SlagheartGolem other = helper.spawn(ModMobs.SLAGHEART_GOLEM.get(), new Vec3(9.5, 1, 9.5));
        other.setNoAi(true);
        ServerPlayer thrower = TestPlayers.survivor(helper, new Vec3(1.5, 1, 1.5));
        golem.setHeat(4);
        other.setHeat(2);
        Snowball stray = helper.spawn(EntityTypes.SNOWBALL, new Vec3(9.5, 2.5, 6.5));
        stray.setDeltaMovement(0, 0, 0.8);
        throwAt(helper, thrower, 2);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(other.core().in(Step.QUENCHED), "the stray snowball quenches the other golem, heat " + other.heat());
            helper.assertTrue(golem.heat() == 2 && !golem.core().in(Step.QUENCHED), "the first snowball cools 4 to 2, heat " + golem.heat());
            helper.assertTrue(!AdvancementTests.done(helper, thrower, TeemingAdvancements.SLAGHEART_QUENCH), "cooling short of 0 earns nothing");
        });
        throwAt(helper, thrower, 14);
        helper.succeedWhen(() -> {
            helper.assertTrue(golem.heat() == 0 && golem.core().in(Step.QUENCHED), "the second snowball quenches it, heat " + golem.heat());
            helper.assertTrue(AdvancementTests.done(helper, thrower, TeemingAdvancements.SLAGHEART_QUENCH), "and earns its thrower Cooled Off");
        });
    }

    /** At tick {@code at}, a snowball thrown by {@code thrower} flies into the golem at the arena's middle. */
    private static void throwAt(GameTestHelper helper, ServerPlayer thrower, int at) {
        helper.runAtTickTime(at, () -> {
            Snowball ball = helper.spawn(EntityTypes.SNOWBALL, new Vec3(5.5, 2.5, 2.5));
            ball.setOwner(thrower);
            ball.setDeltaMovement(0, 0, 0.8);
        });
    }

    /** Quenched lasts 60 ticks. */
    private static void quenchLength(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        helper.runAtTickTime(2, () -> golem.setHeat(0));
        helper.runAtTickTime(2 + SlagheartGolem.QUENCH_TICKS - 3, () -> helper.assertTrue(golem.core().in(Step.QUENCHED), "still quenched near 60 ticks, " + golem.phase()));
        helper.runAtTickTime(2 + SlagheartGolem.QUENCH_TICKS + 3, () -> {
            helper.assertTrue(!golem.core().in(Step.QUENCHED), "quenched ends after 60 ticks, " + golem.phase());
            helper.succeed();
        });
    }

    /**
     * The quench items are a tag packs can fill, {@code #teeming:cold_items}, snowballs by default: a thrown snowball
     * counts as cold, a thrown egg and a splash water bottle (which has its own rule) don't.
     */
    private static void coldItems(GameTestHelper helper) {
        helper.assertTrue(Items.SNOWBALL.getDefaultInstance().is(SlagheartGolem.COLD_ITEMS), "snowballs are cold items");
        helper.assertTrue(!Items.EGG.getDefaultInstance().is(SlagheartGolem.COLD_ITEMS), "eggs are not");
        Snowball ball = helper.spawn(EntityTypes.SNOWBALL, new Vec3(1.5, 3.5, 1.5));
        ball.setNoGravity(true);
        ThrownEgg egg = helper.spawn(EntityTypes.EGG, new Vec3(3.5, 3.5, 1.5));
        egg.setNoGravity(true);
        Vec3 at = helper.absoluteVec(new Vec3(7.5, 3.5, 1.5));
        ThrownSplashPotion potion = new ThrownSplashPotion(helper.getLevel(), at.x, at.y, at.z,
                PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));
        helper.assertTrue(SlagheartGolem.cold(ball), "a thrown snowball cools it");
        helper.assertTrue(!SlagheartGolem.cold(egg), "a thrown egg doesn't");
        helper.assertTrue(!SlagheartGolem.cold(potion), "splash water isn't a cold item: it cools by its own rule");
        ball.discard();
        egg.discard();
        helper.succeed();
    }

    /**
     * A straight melee fight against the Nether reference player (8.5 a blow through its armour 6, 1.6 blows a second,
     * one every 12 ticks) reaches full heat and erupts before the golem falls: heat 6 plus 2 a hit is 12 at the third
     * blow, the eruption follows 20 ticks later, and 44 health takes a sixth blow 60 ticks after the first.
     */
    private static void fightErupts(GameTestHelper helper) {
        SlagheartGolem golem = golem(helper);
        Pig attacker = helper.spawn(EntityTypes.PIG, new Vec3(1.5, 1, 1.5));
        attacker.setNoAi(true);
        boolean[] erupted = {false};
        int[] blows = {0};
        helper.onEachTick(() -> {
            if (golem.core().in(Step.ERUPT) && golem.isAlive()) erupted[0] = true;
            if (helper.getTick() >= 2 && (helper.getTick() - 2) % 12 == 0 && golem.isAlive()) {
                golem.hurtServer(helper.getLevel(), golem.damageSources().mobAttack(attacker), 8.5F);
                blows[0]++;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!golem.isAlive(), "the blows kill it");
            helper.assertTrue(blows[0] >= 6, "it takes six blows, took " + blows[0]);
            helper.assertTrue(erupted[0], "it erupts before it falls");
        });
    }
}
