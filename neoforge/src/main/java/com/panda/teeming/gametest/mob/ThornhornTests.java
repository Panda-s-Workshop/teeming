package com.panda.teeming.gametest.mob;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.Thornhorn;
import com.panda.teeming.mob.overworld.Thornhorn.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Thornhorn: the tell leads to the charge, the charge hits, a wall stuns it, a cancelled tell stops it all. */
public final class ThornhornTests {
    private ThornhornTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("thornhorn_tell", ThornhornTests::tell);
        out.accept("thornhorn_counter", ThornhornTests::wallStun);
        out.accept("thornhorn_failure", ThornhornTests::tellThenCharge);
        out.accept("mechanic_event_cancel", ThornhornTests::cancelledTell);
        out.accept("thornhorn_charge_numbers", ThornhornTests::chargeNumbers);
        out.accept("thornhorn_stun_length", ThornhornTests::stunLength);
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
            helper.assertTrue(horn.vulnerable(), "stunned, its weak point shows");
            float before = horn.getHealth();
            horn.hurtServer(helper.getLevel(), horn.damageSources().magic(), 4.0F);
            float lost = before - horn.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F * Thornhorn.STUN_DAMAGE) < 0.01F, "a stunned Thornhorn takes x1.5, lost " + lost);
            helper.assertTrue(pig[0].getHealth() >= pig[0].getMaxHealth(), "the wall saved the pig");
        });
    }

    /** The charge covers 0.45 blocks a tick, knocks the target back hard (1.6) and is followed by a 100-tick rest. */
    private static void chargeNumbers(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Thornhorn horn = setUp(helper, pig);
        List<Double> steps = new ArrayList<>();
        double[] last = {Double.NaN};
        double[] pigSpeed = {0.0};
        int[] cooldown = {0};
        helper.onEachTick(() -> {
            if (horn.core().in(Step.CHARGE)) {
                if (!Double.isNaN(last[0])) steps.add(horn.getX() - last[0]);
                last[0] = horn.getX();
            } else {
                last[0] = Double.NaN;
            }
            pigSpeed[0] = Math.max(pigSpeed[0], pig[0].getDeltaMovement().horizontalDistance());
            cooldown[0] = Math.max(cooldown[0], horn.cooldown());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cooldown[0] > 0 && horn.core().idle(), "it rests after the charge, cooldown " + cooldown[0]);
            helper.assertTrue(cooldown[0] >= Thornhorn.COOLDOWN - 1 && cooldown[0] <= Thornhorn.COOLDOWN, "the rest is 100 ticks, saw " + cooldown[0]);
            helper.assertTrue(steps.size() >= 3, "it charged for at least 3 ticks, saw " + steps.size());
            double mean = steps.subList(1, steps.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - Thornhorn.CHARGE_SPEED) < 0.05, "the charge covers 0.45 blocks a tick, mean " + mean);
            helper.assertTrue(pigSpeed[0] >= Thornhorn.KNOCKBACK * 0.5, "the gore knocks the target back (1.6), its speed peaked at " + pigSpeed[0]);
        });
    }

    /** The stun from a wall lasts 60 ticks. */
    private static void stunLength(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Thornhorn horn = setUp(helper, pig);
        helper.runAtTickTime(6, () -> {
            for (int z = 2; z <= 9; z++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
        });
        int[] stunned = {0};
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (horn.core().in(Step.STUNNED)) stunned[0]++;
            else if (stunned[0] > 0) over[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the stun ends, " + stunned[0] + " ticks so far");
            helper.assertTrue(Math.abs(stunned[0] - Thornhorn.STUN_TICKS) <= 1, "the stun lasts 60 ticks, lasted " + stunned[0]);
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
