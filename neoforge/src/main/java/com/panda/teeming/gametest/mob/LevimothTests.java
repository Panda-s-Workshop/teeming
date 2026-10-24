package com.panda.teeming.gametest.mob;

import com.panda.teeming.mob.end.Levimoth;
import com.panda.teeming.mob.end.Levimoth.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;

/** Levimoth: a hit starts the flare, the flare ends in a dust cloud that levitates what's inside, not the moth. */
public final class LevimothTests {
    private LevimothTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("levimoth_tell", LevimothTests::tell);
        out.accept("levimoth_counter", LevimothTests::dustRadius);
        out.accept("levimoth_failure", LevimothTests::hurtReleasesDust);
        out.accept("levimoth_cooldown", LevimothTests::cooldown);
    }

    private static Levimoth moth(GameTestHelper helper) {
        Levimoth moth = helper.spawn(ModMobs.LEVIMOTH.get(), new Vec3(5.5, 1, 5.5));
        moth.setNoAi(true);
        return moth;
    }

    private static Pig pig(GameTestHelper helper, double x) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, 5.5));
        pig.setNoAi(true);
        return pig;
    }

    private static List<AreaEffectCloud> clouds(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class, helper.getBounds());
    }

    /** A hit starts a 16-tick flare with no dust and no effect on anything yet. */
    private static void tell(GameTestHelper helper) {
        Levimoth moth = moth(helper);
        Pig near = pig(helper, 7.0);
        helper.runAtTickTime(2, () -> {
            moth.hurtServer(helper.getLevel(), moth.damageSources().magic(), 1.0F);
            helper.assertTrue(moth.core().in(Step.TELL), "a hit starts the flare, phase " + moth.phase());
        });
        helper.runAtTickTime(2 + Levimoth.TELL_TICKS - 2, () -> {
            helper.assertTrue(moth.core().in(Step.TELL), "the flare lasts 16 ticks, phase " + moth.phase());
            helper.assertTrue(clouds(helper).isEmpty() && !near.hasEffect(MobEffects.LEVITATION), "no dust and no levitation during the flare");
            helper.succeed();
        });
    }

    /** Failing the counter: standing in the dust lifts you (Levitation II); the moth itself stays put. */
    private static void hurtReleasesDust(GameTestHelper helper) {
        Levimoth moth = moth(helper);
        Pig near = pig(helper, 7.0);
        helper.runAtTickTime(2, () -> {
            moth.hurtServer(helper.getLevel(), moth.damageSources().magic(), 1.0F);
            helper.assertTrue(moth.core().in(Step.TELL), "a hit starts the flare, phase " + moth.phase());
            helper.assertTrue(clouds(helper).isEmpty(), "no dust before the flare ends");
        });
        helper.runAtTickTime(2 + Levimoth.TELL_TICKS - 2, () -> helper.assertTrue(clouds(helper).isEmpty(), "the flare lasts 16 ticks before the dust"));
        helper.succeedWhen(() -> {
            List<AreaEffectCloud> clouds = clouds(helper);
            helper.assertTrue(clouds.size() == 1, "the flare ends in one cloud, found " + clouds.size());
            helper.assertTrue(Math.abs(clouds.getFirst().getRadius() - Levimoth.CLOUD_RADIUS) < 0.01F, "radius 3, got " + clouds.getFirst().getRadius());
            helper.assertTrue(near.hasEffect(MobEffects.LEVITATION), "the pig 1.5 blocks away levitates");
            helper.assertTrue(near.getEffect(MobEffects.LEVITATION).getAmplifier() == Levimoth.LEVITATION_LEVEL, "Levitation II");
            helper.assertTrue(!moth.hasEffect(MobEffects.LEVITATION), "its own dust doesn't lift the moth");
        });
    }

    /** The counter: keeping out of the cloud. 2 blocks from its centre lifts, 5 blocks out does nothing. */
    private static void dustRadius(GameTestHelper helper) {
        Levimoth moth = moth(helper);
        Pig near = pig(helper, 7.5);
        Pig far = pig(helper, 10.5);
        helper.runAtTickTime(2, () -> helper.assertTrue(moth.release(helper.getLevel()) != null, "release makes a cloud"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(near.hasEffect(MobEffects.LEVITATION), "2 blocks out is inside the cloud");
            helper.assertTrue(!far.hasEffect(MobEffects.LEVITATION), "5 blocks out is outside the cloud");
            helper.succeed();
        });
    }

    private static void cooldown(GameTestHelper helper) {
        Levimoth moth = moth(helper);
        helper.runAtTickTime(2, () -> moth.hurtServer(helper.getLevel(), moth.damageSources().magic(), 1.0F));
        helper.runAtTickTime(2 + Levimoth.TELL_TICKS + Levimoth.RELEASE_TICKS + 4, () -> {
            helper.assertTrue(moth.core().idle() && moth.cooldown() > 0, "after the release it rests, cooldown " + moth.cooldown() + ", " + moth.phase());
            moth.hurtServer(helper.getLevel(), moth.damageSources().magic(), 1.0F);
            helper.assertTrue(moth.core().idle(), "a hit during the cooldown doesn't flare again, phase " + moth.phase());
            helper.assertTrue(clouds(helper).size() == 1, "still one cloud");
            helper.succeed();
        });
    }
}
