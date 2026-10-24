package com.panda.teeming.gametest.mob;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.Thornhorn;
import com.panda.teeming.mob.overworld.Thornhorn.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Thornhorn (ROSTER #1): the tell leads to the charge, the charge hits, a wall stuns it, a cancelled tell stops it all. */
public final class ThornhornTests {
    private ThornhornTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("thornhorn_tell", ThornhornTests::tell);
        out.accept("thornhorn_counter", ThornhornTests::wallStun);
        out.accept("thornhorn_failure", ThornhornTests::tellThenCharge);
        out.accept("mechanic_event_cancel", ThornhornTests::cancelledTell);
    }

    /** A Thornhorn 9 blocks from its target, cooldown cleared. */
    private static Thornhorn setUp(GameTestHelper helper, Pig[] target) {
        Thornhorn horn = helper.spawn(ModMobs.THORNHORN.get(), new Vec3(1.5, 1, 5.5));
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(10.5, 1, 5.5));
        pig.setNoAi(true);
        horn.setTarget(pig);
        horn.resetCooldown();
        target[0] = pig;
        return horn;
    }

    /** It paws for the whole 20-tick tell, standing still and hurting nothing, before it charges. */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Thornhorn horn = setUp(helper, pig);
        helper.runAtTickTime(4, () -> helper.assertTrue(horn.core().in(Step.TELL), "it paws (tell) first, phase " + horn.phase()));
        helper.runAtTickTime(Thornhorn.TELL_TICKS, () -> {
            helper.assertTrue(horn.core().in(Step.TELL), "the tell is still on near its 20th tick, phase " + horn.phase());
            helper.assertTrue(horn.getDeltaMovement().horizontalDistance() < 0.05, "it stands still while it paws");
            helper.assertTrue(pig[0].getHealth() >= pig[0].getMaxHealth(), "the tell hurts nothing");
            helper.succeed();
        });
    }

    /** Failing the counter: a target that stays in line takes the charge, 1.5x Atk. */
    private static void tellThenCharge(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Thornhorn horn = setUp(helper, pig);
        float before = pig[0].getHealth();
        helper.runAtTickTime(5, () -> helper.assertTrue(horn.core().in(Step.TELL), "it paws (tell) before charging, phase " + horn.phase()));
        helper.runAtTickTime(Thornhorn.TELL_TICKS + 4, () -> helper.assertTrue(horn.core().in(Step.CHARGE) || horn.core().in(Step.RECOVER),
                "the tell ends in a charge, phase " + horn.phase()));
        helper.succeedWhen(() -> {
            float lost = before - pig[0].getHealth();
            helper.assertTrue(lost >= 7.0F, "the charge hits for 1.5x Atk (7.5), the pig lost " + lost);
        });
    }

    /** The counter: the target sidesteps (a wall goes up in its place), the charge ends in the wall, stunned and taking x1.5. */
    private static void wallStun(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Thornhorn horn = setUp(helper, pig);
        // the target "sidesteps": a wall goes up between them once the tell has started
        helper.runAtTickTime(6, () -> {
            for (int z = 2; z <= 9; z++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(horn.core().in(Step.STUNNED), "a charge into a wall stuns it, phase " + horn.phase());
            float before = horn.getHealth();
            horn.hurtServer(helper.getLevel(), horn.damageSources().magic(), 4.0F);
            float lost = before - horn.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F * Thornhorn.STUN_DAMAGE) < 0.01F, "a stunned Thornhorn takes x1.5, lost " + lost);
            helper.assertTrue(pig[0].getHealth() >= pig[0].getMaxHealth(), "the wall saved the pig");
        });
    }

    /** A listener that cancels the "tell" step stops the whole charge. */
    private static void cancelledTell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Thornhorn horn = setUp(helper, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == horn && e.step().equals("tell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        helper.runAtTickTime(40, () -> {
            MechanicEvent.unlisten(veto);
            helper.assertTrue(horn.core().idle(), "a cancelled tell leaves it idle, phase " + horn.phase());
            helper.assertTrue(pig[0].getHealth() >= pig[0].getMaxHealth(), "no charge hit the pig");
            helper.succeed();
        });
    }
}
