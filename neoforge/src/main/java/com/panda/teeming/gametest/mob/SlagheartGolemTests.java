package com.panda.teeming.gametest.mob;

import com.panda.teeming.mob.nether.SlagheartGolem;
import com.panda.teeming.mob.nether.SlagheartGolem.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Slagheart Golem (ROSTER #24): hits heat it, full heat erupts, snowballs and powder snow quench it, quenched it takes x1.5. */
public final class SlagheartGolemTests {
    private SlagheartGolemTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("slagheart_golem_tell", SlagheartGolemTests::tell);
        out.accept("slagheart_golem_counter", SlagheartGolemTests::snowballStopsEruption);
        out.accept("slagheart_golem_failure", SlagheartGolemTests::heatErupts);
        out.accept("slagheart_golem_snowball_cools", SlagheartGolemTests::snowballCools);
        out.accept("slagheart_golem_powder_snow_quench", SlagheartGolemTests::powderSnowQuench);
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
        golem.setHeat(SlagheartGolem.MAX_HEAT - 1);
        helper.runAtTickTime(2, () -> {
            golem.hurtServer(helper.getLevel(), golem.damageSources().magic(), 1.0F);
            helper.assertTrue(golem.heat() == SlagheartGolem.MAX_HEAT, "a hit heats it by 1, heat " + golem.heat());
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
        helper.assertTrue(golem.heat() == 3 && golem.core().stage() == 1, "it spawns at heat 3, glowing at stage 1, stage " + golem.core().stage());
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
            float before = golem.getHealth();
            golem.hurtServer(helper.getLevel(), golem.damageSources().magic(), 4.0F);
            float lost = before - golem.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F * SlagheartGolem.QUENCHED_DAMAGE) < 0.01F, "quenched it takes x1.5, lost " + lost);
        });
    }
}
