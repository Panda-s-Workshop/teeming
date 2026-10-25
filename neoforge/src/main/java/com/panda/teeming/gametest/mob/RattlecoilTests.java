package com.panda.teeming.gametest.mob;

import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.Rattlecoil;
import com.panda.teeming.mob.overworld.Rattlecoil.Step;
import com.panda.teeming.registry.ModMobs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;

/**
 * Rattlecoil: a player within 6 sets off a 30-tick rattle that does nothing yet; after it, coming within 4 draws a
 * strike (Atk, then Poison II for 100 ticks), and backing off past 8 calms it.
 *
 * <p>The warning reacts to survival players only, and the GameTest mock player always counts as creative, so these
 * tests place a real survival {@link ServerPlayer} and remove it when the test ends. The snake stands in the middle of
 * the arena with its wandering goals cleared, at least 10 blocks from any other arena.
 */
public final class RattlecoilTests {
    private RattlecoilTests() {}

    private static final Vec3 AT = new Vec3(4.5, 1, 4.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("rattlecoil_tell", RattlecoilTests::tell);
        out.accept("rattlecoil_counter", RattlecoilTests::backOff);
        out.accept("rattlecoil_failure", RattlecoilTests::strikePoisons);
        out.accept("rattlecoil_warn_range", RattlecoilTests::warnRange);
        out.accept("rattlecoil_strike_range", RattlecoilTests::strikeRange);
        out.accept("rattlecoil_calm_range", RattlecoilTests::calmRange);
        out.accept("rattlecoil_bite_numbers", RattlecoilTests::biteNumbers);
        out.accept("rattlecoil_stats", RattlecoilTests::stats);
        out.accept("rattlecoil_venom_cancel", RattlecoilTests::venomCancel);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    private static Rattlecoil snake(GameTestHelper helper) {
        Rattlecoil snake = helper.spawn(ModMobs.RATTLECOIL.get(), AT);
        snake.removeFreeWill();      // no wandering; its own phase machine still runs
        return snake;
    }

    /** A real survival player {@code d} blocks from the snake (along the arena's diagonal), removed when the test ends. */
    private static ServerPlayer survivor(GameTestHelper helper, double d) {
        double k = d / Math.sqrt(2.0);
        return TestPlayers.survivor(helper, AT.add(k, 0, k));
    }

    private static void place(GameTestHelper helper, ServerPlayer player, double d) {
        double k = d / Math.sqrt(2.0);
        player.snapTo(helper.absoluteVec(AT.add(k, 0, k)));
    }

    private static Pig pig(GameTestHelper helper, double d) {
        double k = d / Math.sqrt(2.0);
        Pig pig = helper.spawn(EntityTypes.PIG, AT.add(k, 0, k));
        pig.setNoAi(true);
        return pig;
    }

    private static boolean harmed(ServerPlayer p) {
        return p.getHealth() < p.getMaxHealth() || p.hasEffect(MobEffects.POISON);
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A player 3 blocks away (close enough to be struck) sets off the rattle at once. It lasts 30 ticks, the snake
     * stays put, and nothing happens to the player while it rattles.
     */
    private static void tell(GameTestHelper helper) {
        Rattlecoil snake = snake(helper);
        ServerPlayer p = survivor(helper, 3.0);
        Vec3 start = snake.position();
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] harmed = {false};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (!snake.core().in(Step.RATTLE)) return;
            if (first[0] < 0) first[0] = helper.getTick();
            ticks[0]++;
            harmed[0] |= harmed(p);
            moved[0] = Math.max(moved[0], snake.position().subtract(start).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && !snake.core().in(Step.RATTLE), "the rattle runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(first[0] <= 3, "a player 3 blocks away sets it off at once, it started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 30) <= 1, "the rattle lasts 30 ticks, lasted " + ticks[0]);
            helper.assertTrue(!harmed[0], "nothing happens to the player during the rattle");
            helper.assertTrue(moved[0] < 0.1, "it holds still while it rattles, moved " + moved[0]);
            helper.assertTrue(snake.getTarget() == p, "it warned that player, target " + snake.getTarget());
        });
    }

    /** The counter: warned at 5 blocks, the player backs off to 9. The rattle ends, it settles down and forgets them. */
    private static void backOff(GameTestHelper helper) {
        Rattlecoil snake = snake(helper);
        ServerPlayer p = survivor(helper, 5.0);
        boolean[] warned = {false}, calmed = {false}, harmed = {false}, struck = {false};
        helper.onEachTick(() -> {
            warned[0] |= snake.core().in(Step.RATTLE);
            calmed[0] |= snake.core().in(Step.CALM);
            struck[0] |= snake.core().in(Step.STRIKE);
            harmed[0] |= harmed(p);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(snake.core().in(Step.RATTLE), "5 blocks away sets off the rattle, phase " + snake.phase());
            place(helper, p, 9.0);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(warned[0] && calmed[0], "it rattled, then settled down (calm seen: " + calmed[0] + ")");
            helper.assertTrue(snake.core().idle(), "it is back to idle, phase " + snake.phase());
            helper.assertTrue(snake.getTarget() == null, "it forgot the player, target " + snake.getTarget());
            helper.assertTrue(!struck[0] && !harmed[0], "it never struck");
        });
    }

    /** Failing the counter: staying 3 blocks away through the rattle draws the strike, Atk damage and Poison II for 100 ticks. */
    private static void strikePoisons(GameTestHelper helper) {
        Rattlecoil snake = snake(helper);
        ServerPlayer p = survivor(helper, 3.0);
        float before = p.getHealth();
        // mob damage to players scales with difficulty: Atk 4 is 3 on Easy, 4 on Normal, 6 on Hard
        float expected = switch (helper.getLevel().getDifficulty()) {
            case EASY -> 3.0F;
            case HARD -> 6.0F;
            default -> 4.0F;
        };
        boolean[] rattled = {false};
        helper.onEachTick(() -> rattled[0] |= snake.core().in(Step.RATTLE));
        helper.succeedWhen(() -> {
            MobEffectInstance poison = p.getEffect(MobEffects.POISON);
            helper.assertTrue(poison != null, "the strike poisons, phase " + snake.phase());
            helper.assertTrue(rattled[0], "it rattled first");
            helper.assertTrue(poison.getAmplifier() == 1, "Poison II, amplifier " + poison.getAmplifier());
            helper.assertTrue(poison.getDuration() > 95 && poison.getDuration() <= 100, "Poison lasts 100 ticks, " + poison.getDuration() + " left");
            float lost = before - p.getHealth();
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the bite does " + expected + " here, the player lost " + lost);
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** 6.5 blocks away is outside the warning: no rattle for 20 ticks. Stepping to 5.5 sets it off within 3 ticks. */
    private static void warnRange(GameTestHelper helper) {
        Rattlecoil snake = snake(helper);
        ServerPlayer p = survivor(helper, 6.5);
        long[] rattled = {-1};
        helper.onEachTick(() -> {
            if (rattled[0] < 0 && snake.core().in(Step.RATTLE)) rattled[0] = helper.getTick();
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(rattled[0] < 0 && snake.core().idle(), "6.5 blocks away doesn't set it off, rattled at " + rattled[0]);
            place(helper, p, 5.5);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(rattled[0] >= 20, "5.5 blocks away sets it off, rattled at " + rattled[0]);
            helper.assertTrue(rattled[0] <= 23, "within 3 ticks of stepping in, rattled at " + rattled[0]);
        });
    }

    /** After the rattle, 4.5 blocks away is safe for 20 ticks; stepping to 3.5 draws the strike within 2 ticks. */
    private static void strikeRange(GameTestHelper helper) {
        Rattlecoil snake = snake(helper);
        ServerPlayer p = survivor(helper, 4.5);
        long[] wary = {-1}, stepped = {-1}, struck = {-1};
        boolean[] early = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (wary[0] < 0 && snake.core().in(Step.WARY)) wary[0] = now;
            if (struck[0] < 0 && snake.core().in(Step.STRIKE)) {
                struck[0] = now;
                early[0] = stepped[0] < 0;
            }
            if (wary[0] >= 0 && stepped[0] < 0 && now >= wary[0] + 20) {
                early[0] |= !snake.core().in(Step.WARY) || harmed(p);
                place(helper, p, 3.5);
                stepped[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepped[0] >= 0, "it watched (wary) after the rattle, wary from tick " + wary[0]);
            helper.assertTrue(!early[0], "4.5 blocks away it held its coil and never struck");
            helper.assertTrue(struck[0] >= 0, "3.5 blocks away draws the strike, phase " + snake.phase());
            helper.assertTrue(struck[0] - stepped[0] <= 2, "within 2 ticks of stepping in, " + (struck[0] - stepped[0]));
        });
    }

    /** While it watches, 7.5 blocks away keeps it coiled for 15 ticks; backing off to 8.5 calms it within 2 ticks. */
    private static void calmRange(GameTestHelper helper) {
        Rattlecoil snake = snake(helper);
        ServerPlayer p = survivor(helper, 5.0);
        long[] wary = {-1}, backed = {-1}, calmed = {-1};
        boolean[] early = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (wary[0] < 0 && snake.core().in(Step.WARY)) {
                wary[0] = now;
                place(helper, p, 7.5);
            }
            if (calmed[0] < 0 && snake.core().in(Step.CALM)) {
                calmed[0] = now;
                early[0] = backed[0] < 0;
            }
            if (wary[0] >= 0 && backed[0] < 0 && now >= wary[0] + 15) {
                place(helper, p, 8.5);
                backed[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(backed[0] >= 0, "it watched after the rattle, wary from tick " + wary[0]);
            helper.assertTrue(!early[0], "7.5 blocks away didn't calm it");
            helper.assertTrue(calmed[0] >= 0, "8.5 blocks away calms it, phase " + snake.phase());
            helper.assertTrue(calmed[0] - backed[0] <= 2, "within 2 ticks of backing off, " + (calmed[0] - backed[0]));
        });
    }

    /**
     * Provoked by a pig (no difficulty scaling, no healing), it rattles at the pig, then strikes it 2.5 blocks away:
     * exactly 4 damage (Atk) and Poison II for 100 ticks.
     */
    private static void biteNumbers(GameTestHelper helper) {
        Rattlecoil snake = snake(helper);
        Pig pig = pig(helper, 2.5);
        float[] lost = {-1.0F};
        float before = pig.getHealth();
        helper.runAtTickTime(1, () -> {
            snake.hurtServer(helper.getLevel(), snake.damageSources().mobAttack(pig), 1.0F);
            helper.assertTrue(snake.core().in(Step.RATTLE), "a hit sets off the rattle, phase " + snake.phase());
            helper.assertTrue(snake.getTarget() == pig, "aimed at the attacker");
        });
        helper.onEachTick(() -> {
            if (lost[0] < 0 && pig.getHealth() < before) lost[0] = before - pig.getHealth();
        });
        helper.succeedWhen(() -> {
            MobEffectInstance poison = pig.getEffect(MobEffects.POISON);
            helper.assertTrue(poison != null, "the strike poisons the pig, phase " + snake.phase());
            helper.assertTrue(Math.abs(lost[0] - 4.0F) < 0.01F, "the bite does 4 (Atk), the pig lost " + lost[0]);
            helper.assertTrue(poison.getAmplifier() == 1, "Poison II, amplifier " + poison.getAmplifier());
            helper.assertTrue(poison.getDuration() >= 97 && poison.getDuration() <= 100, "Poison lasts 100 ticks, " + poison.getDuration() + " left");
        });
    }

    /** Health 14, attack 4, armour 0, a 0.8 x 0.4 hitbox. */
    private static void stats(GameTestHelper helper) {
        Rattlecoil snake = snake(helper);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(snake.getMaxHealth() - 14.0F) < 0.01F && Math.abs(snake.getHealth() - 14.0F) < 0.01F,
                    "health 14, got " + snake.getHealth() + "/" + snake.getMaxHealth());
            helper.assertTrue(Math.abs(snake.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 0.01, "attack 4");
            helper.assertTrue(Math.abs(snake.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(snake.getBbWidth() - 0.8F) < 0.02F && Math.abs(snake.getBbHeight() - 0.4F) < 0.02F,
                    "hitbox 0.8 x 0.4, got " + snake.getBbWidth() + " x " + snake.getBbHeight());
            helper.succeed();
        });
    }

    /** A listener that cancels the "venom" step lets the bite land but leaves the target unpoisoned. */
    private static void venomCancel(GameTestHelper helper) {
        Rattlecoil snake = snake(helper);
        Pig pig = pig(helper, 2.5);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == snake && e.step().equals("venom")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        float before = pig.getHealth();
        helper.runAtTickTime(1, () -> snake.hurtServer(helper.getLevel(), snake.damageSources().mobAttack(pig), 1.0F));
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < before, "the bite still lands, phase " + snake.phase());
            helper.assertTrue(!pig.hasEffect(MobEffects.POISON), "a cancelled venom step poisons nothing");   // venom goes on in the same tick as the bite
        });
    }
}
