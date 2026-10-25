package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.nether.CindermothSwarm;
import com.panda.teeming.mob.nether.CindermothSwarm.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Cindermoth Swarm: health 10, armour 0, a 1.0 x 1.0 hitbox, no tell; it seeks the brightest light within 12 blocks (a
 * held light item, or a light lying on the ground, which holds it 200 ticks), then whoever carries one; whatever it
 * touches burns 1 a second. A blow scatters it for 40 ticks; a sweeping sword swing takes double. Every number is
 * checked against a plain value.
 *
 * <p>Every swarm here is confined to its own arena (lights, carriers and victims elsewhere don't count), so it never
 * reaches into the arenas beside it, and it is removed when the test ends wherever it drifted. Survival players are
 * ticked by hand each tick (they have no client), so their hurt cooldown runs down as in play.
 */
public final class CindermothSwarmTests {
    private CindermothSwarmTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("cindermoth_swarm_tell", CindermothSwarmTests::tell);
        TeemingGameTests.timeLimit("cindermoth_swarm_tell", 140);
        out.accept("cindermoth_swarm_counter", CindermothSwarmTests::counter);
        TeemingGameTests.timeLimit("cindermoth_swarm_counter", 400);
        out.accept("cindermoth_swarm_failure", CindermothSwarmTests::failure);
        TeemingGameTests.timeLimit("cindermoth_swarm_failure", 240);
        out.accept("cindermoth_swarm_stats", CindermothSwarmTests::stats);
        out.accept("cindermoth_swarm_seek_range", CindermothSwarmTests::seekRange);
        out.accept("cindermoth_swarm_brightest", CindermothSwarmTests::brightest);
        out.accept("cindermoth_swarm_put_away", CindermothSwarmTests::putAway);
        TeemingGameTests.timeLimit("cindermoth_swarm_put_away", 140);
        out.accept("cindermoth_swarm_scatter", CindermothSwarmTests::scatter);
        TeemingGameTests.timeLimit("cindermoth_swarm_scatter", 140);
        out.accept("cindermoth_swarm_sweep", CindermothSwarmTests::sweep);
        out.accept("cindermoth_swarm_slow", CindermothSwarmTests::slow);
        TeemingGameTests.timeLimit("cindermoth_swarm_slow", 140);
        out.accept("cindermoth_swarm_handed_target", CindermothSwarmTests::handedTarget);
        TeemingGameTests.timeLimit("cindermoth_swarm_handed_target", 240);
        out.accept("cindermoth_swarm_home", CindermothSwarmTests::home);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    /** A swarm with the base look, confined to this arena, removed when the test ends. */
    private static CindermothSwarm swarm(GameTestHelper helper, double x, double y, double z) {
        CindermothSwarm s = helper.spawn(ModMobs.CINDERMOTH_SWARM.get(), new Vec3(x, y, z));
        s.core().setLook("base", true);
        s.confine(helper.getBounds());
        TestPlayers.whenDone(helper, s::discard);
        return s;
    }

    /** A survival player holding {@code held} in the main hand. */
    private static ServerPlayer holding(GameTestHelper helper, Vec3 at, ItemStack held) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        return p;
    }

    /** A stack lying still on the floor at (x, z), removed when the test ends. */
    private static ItemEntity item(GameTestHelper helper, double x, double z, ItemStack stack) {
        Vec3 at = helper.absoluteVec(new Vec3(x, 1, z));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        item.setNeverPickUp();                       // nobody here picks it up
        helper.getLevel().addFreshEntity(item);
        TestPlayers.whenDone(helper, item::discard);
        return item;
    }

    private static Vec3 centre(CindermothSwarm s) {
        return s.position().add(0, s.getBbHeight() * 0.5, 0);
    }

    // ---- tell, counter, failure --------------------------------------------------------------------------------------

    /**
     * No tell: a survival player holding a torch 6 blocks off. The swarm turns to the light within 10 ticks (its look
     * round), goes straight from idle to seeking with no other phase before it reaches them, and the first touch
     * burns exactly 1.
     */
    private static void tell(GameTestHelper helper) {
        ServerPlayer p = holding(helper, new Vec3(2.5, 1, 5.5), new ItemStack(Items.TORCH));
        CindermothSwarm s = swarm(helper, 8.5, 1.5, 5.5);
        float before = p.getHealth();
        int[] seekAt = {-1};
        float[] first = {0.0F};
        Set<Phase> seen = new HashSet<>();
        helper.onEachTick(() -> {
            p.doTick();
            if (seekAt[0] < 0 && s.core().in(Step.SEEK)) seekAt[0] = (int) helper.getTick();
            if (first[0] == 0.0F && p.getHealth() < before) first[0] = before - p.getHealth();
            if (first[0] == 0.0F) seen.add(s.phase());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first[0] > 0.0F, "it reached the player and burned them, phase " + s.phase());
            helper.assertTrue(seekAt[0] >= 0 && seekAt[0] <= 11, "it turned to the light within 10 ticks, at tick " + seekAt[0]);
            helper.assertTrue(Set.of(Step.IDLE, Step.SEEK, Step.ENGULF).containsAll(seen), "no tell or other phase before the burn, saw " + seen);
            helper.assertTrue(Math.abs(first[0] - 1.0F) < 0.01F, "the first touch burns 1, took " + first[0]);
        });
    }

    /**
     * The counter: a survival player holds a torch and a torch lies on the ground. Equally bright, the one on the ground
     * wins: the swarm circles it for exactly 200 ticks while the player loses nothing, never takes it (it still lies
     * there, one torch), then is bored of it for good and goes back to the player.
     */
    private static void counter(GameTestHelper helper) {
        ServerPlayer p = holding(helper, new Vec3(2.5, 1, 2.5), new ItemStack(Items.TORCH));
        CindermothSwarm s = swarm(helper, 6.5, 1.5, 6.5);
        ItemEntity torch = item(helper, 9.5, 9.5, new ItemStack(Items.TORCH));
        float before = p.getHealth();
        int[] hovered = {0}, after = {0};
        boolean[] lured = {false}, again = {false};
        float[] lostInHover = {0.0F};
        helper.onEachTick(() -> {
            p.doTick();
            if (s.core().in(Step.HOVER)) {
                if (after[0] > 0) again[0] = true;
                hovered[0]++;
                lured[0] |= s.lure() == torch;
                lostInHover[0] = Math.max(lostInHover[0], before - p.getHealth());
            } else if (hovered[0] > 0) {
                after[0]++;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lured[0], "it circled the torch on the ground, not the held one");
            helper.assertTrue(after[0] > 0, "the circling ended, " + hovered[0] + " ticks so far");
            helper.assertTrue(Math.abs(hovered[0] - 200) <= 1, "it circles the torch for 200 ticks, circled " + hovered[0]);
            helper.assertTrue(lostInHover[0] == 0.0F, "the player loses nothing while it circles, lost " + lostInHover[0]);
            helper.assertTrue(torch.isAlive() && torch.getItem().is(Items.TORCH) && torch.getItem().getCount() == 1, "the torch still lies there, " + torch.getItem());
            helper.assertTrue(s.boredOf(torch), "that torch bores it now");
            helper.assertTrue(!again[0], "it never circles it again");
            helper.assertTrue(s.light() != null && s.light().carrier() == p, "it goes back to the player, light " + s.light());
        });
    }

    /**
     * Failing it: the swarm reaches a survival player holding a torch and stays on them. They lose exactly 1 every 20
     * ticks, to fire, with flames on them that never last 20 ticks (so they never burn on their own). Once they put the
     * torch away and step off, the burn stops at once: nothing more over the next 40 ticks, and the flames go out.
     */
    private static void failure(GameTestHelper helper) {
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 4.5), new ItemStack(Items.TORCH));
        CindermothSwarm s = swarm(helper, 5.5, 1.5, 7.5);
        List<Long> at = new ArrayList<>();
        List<Float> lost = new ArrayList<>();
        float[] last = {p.getHealth()};
        boolean[] lit = {false}, longFlames = {false};
        DamageSource[] source = {null};
        long[] off = {-1};
        float[] offHealth = {0.0F};
        helper.onEachTick(() -> {
            p.doTick();
            long now = helper.getTick();
            if (p.getHealth() < last[0]) {
                at.add(now);
                lost.add(last[0] - p.getHealth());
                lit[0] |= p.isOnFire();
                source[0] = p.getLastDamageSource();
            }
            last[0] = p.getHealth();
            longFlames[0] |= p.getRemainingFireTicks() >= 20;
            if (off[0] < 0 && at.size() >= 4) {
                // the counter, late: the torch away and a step back out of the cloud
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                Vec3 away = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
                p.teleportTo(away.x, away.y, away.z);
                off[0] = now;
                offHealth[0] = p.getHealth();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(off[0] >= 0 && helper.getTick() >= off[0] + 40, "4 burns, then 40 ticks clear, " + at.size() + " burns so far");
            for (int i = 0; i < 4; i++) {
                helper.assertTrue(Math.abs(lost.get(i) - 1.0F) < 0.01F, "each burn takes 1, burn " + i + " took " + lost.get(i));
                if (i > 0) helper.assertTrue(at.get(i) - at.get(i - 1) == 20, "one burn every 20 ticks, " + at);
            }
            helper.assertTrue(source[0] != null && source[0].is(DamageTypes.ON_FIRE), "it burns as fire, " + source[0]);
            helper.assertTrue(lit[0], "the player is alight when it burns");
            helper.assertTrue(!longFlames[0], "the flames never last 20 ticks, so they never burn on their own");
            helper.assertTrue(Math.abs(p.getHealth() - offHealth[0]) < 0.01F, "nothing more once the torch is away and they stepped off, "
                    + offHealth[0] + " -> " + p.getHealth());
            helper.assertTrue(!p.isOnFire(), "the flames went out");
        });
    }

    // ---- the numbers -------------------------------------------------------------------------------------------------

    /** Health 10, attack 1 (its burn), armour 0, a 1.0 x 1.0 hitbox; fire and lava never hurt it. */
    private static void stats(GameTestHelper helper) {
        CindermothSwarm s = swarm(helper, 5.5, 1.5, 5.5);
        s.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.getMaxHealth() == 10.0F && s.getHealth() == 10.0F, "health 10, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(s.getAttributeValue(Attributes.ATTACK_DAMAGE) == 1.0, "attack 1, got " + s.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(s.getArmorValue() == 0, "armour 0, got " + s.getArmorValue());
            helper.assertTrue(Math.abs(s.getBbWidth() - 1.0F) < 0.01F && Math.abs(s.getBbHeight() - 1.0F) < 0.01F,
                    "a 1.0 x 1.0 hitbox, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.assertTrue(s.fireImmune(), "fire never hurts it");
            helper.succeed();
        });
    }

    /**
     * Range 12, measured from the middle of the cloud: a torch on the ground 12.5 blocks off is out of range and one
     * at 11.5 in; a survival player holding a lantern with their chest 12.5 blocks off is out of range and at 11.5 in.
     */
    private static void seekRange(GameTestHelper helper) {
        CindermothSwarm s = swarm(helper, 1.5, 1.0, 1.5);
        s.setNoAi(true);
        ItemEntity[] far = {null}, near = {null};
        ServerPlayer[] p = {null};
        helper.runAtTickTime(2, () -> {
            Vec3 c = helper.relativeVec(centre(s));
            far[0] = item(helper, c.x + diag(12.5, c.y - 1.0), c.z + diag(12.5, c.y - 1.0), new ItemStack(Items.TORCH));
        });
        helper.runAtTickTime(4, () -> {
            double d = far[0].position().distanceTo(centre(s));
            helper.assertTrue(Math.abs(d - 12.5) < 0.05, "the far torch lies 12.5 off, " + d);
            helper.assertTrue(s.brightest() == null, "a torch 12.5 blocks off is out of range, it picked " + s.brightest());
            Vec3 c = helper.relativeVec(centre(s));
            near[0] = item(helper, c.x + diag(11.5, c.y - 1.0), c.z + diag(11.5, c.y - 1.0), new ItemStack(Items.TORCH));
        });
        helper.runAtTickTime(6, () -> {
            double d = near[0].position().distanceTo(centre(s));
            helper.assertTrue(Math.abs(d - 11.5) < 0.05, "the near torch lies 11.5 off, " + d);
            CindermothSwarm.Light l = s.brightest();
            helper.assertTrue(l != null && l.item() == near[0], "a torch 11.5 blocks off is in range, it picked " + l);
            near[0].discard();
            far[0].discard();
            Vec3 c = helper.relativeVec(centre(s));
            double chest = 1.8 * 0.6;
            p[0] = holding(helper, new Vec3(c.x + diag(12.5, c.y - 1.0 - chest), 1, c.z + diag(12.5, c.y - 1.0 - chest)), new ItemStack(Items.LANTERN));
        });
        helper.runAtTickTime(8, () -> {
            CindermothSwarm.Light l = new CindermothSwarm.Light(null, p[0], 15);
            double d = l.at().distanceTo(centre(s));
            helper.assertTrue(Math.abs(d - 12.5) < 0.05, "the player's chest is 12.5 off, " + d);
            helper.assertTrue(s.brightest() == null, "a held lantern 12.5 blocks off is out of range, it picked " + s.brightest());
            Vec3 c = helper.relativeVec(centre(s));
            double chest = p[0].getBbHeight() * 0.6;
            Vec3 to = helper.absoluteVec(new Vec3(c.x + diag(11.5, c.y - 1.0 - chest), 1, c.z + diag(11.5, c.y - 1.0 - chest)));
            p[0].teleportTo(to.x, to.y, to.z);
        });
        helper.runAtTickTime(10, () -> {
            CindermothSwarm.Light l = s.brightest();
            helper.assertTrue(l != null && l.carrier() == p[0], "a held lantern 11.5 blocks off is in range, it picked " + l);
            double d = l.at().distanceTo(centre(s));
            helper.assertTrue(Math.abs(d - 11.5) < 0.05, "the player's chest is 11.5 off, " + d);
            helper.succeed();
        });
    }

    /** The offset along both floor axes that puts a point {@code d} blocks away, {@code dy} below. */
    private static double diag(double d, double dy) {
        return Math.sqrt(d * d - dy * dy) / Math.sqrt(2.0);
    }

    /**
     * Brightest wins, as bright as the block the item places: torch 14, soul torch 10, lantern 15, glowstone 15, and a
     * stick or glowstone dust give none. A lantern 5 blocks off beats a soul torch 3 off and a stick 1.5 off; with the
     * lantern gone, a torch on the ground beats an equally bright torch held nearer; a held lantern beats that torch.
     */
    private static void brightest(GameTestHelper helper) {
        helper.assertTrue(CindermothSwarm.brightness(new ItemStack(Items.TORCH)) == 14, "a torch gives 14");
        helper.assertTrue(CindermothSwarm.brightness(new ItemStack(Items.SOUL_TORCH)) == 10, "a soul torch gives 10");
        helper.assertTrue(CindermothSwarm.brightness(new ItemStack(Items.LANTERN)) == 15, "a lantern gives 15");
        helper.assertTrue(CindermothSwarm.brightness(new ItemStack(Items.GLOWSTONE)) == 15, "glowstone gives 15");
        helper.assertTrue(CindermothSwarm.brightness(new ItemStack(Items.STICK)) == 0, "a stick gives none");
        helper.assertTrue(CindermothSwarm.brightness(new ItemStack(Items.GLOWSTONE_DUST)) == 0, "glowstone dust gives none");
        CindermothSwarm s = swarm(helper, 5.5, 1.0, 5.5);
        s.setNoAi(true);
        item(helper, 7.0, 5.5, new ItemStack(Items.STICK));
        item(helper, 5.5, 8.5, new ItemStack(Items.SOUL_TORCH));
        ItemEntity lantern = item(helper, 5.5, 0.5, new ItemStack(Items.LANTERN));
        ItemEntity[] torch = {null};
        ServerPlayer[] p = {null};
        helper.runAtTickTime(2, () -> {
            CindermothSwarm.Light l = s.brightest();
            helper.assertTrue(l != null && l.item() == lantern, "the lantern beats the nearer soul torch and stick, picked " + l);
            lantern.discard();
            torch[0] = item(helper, 10.5, 5.5, new ItemStack(Items.TORCH));                // 5 off
            p[0] = holding(helper, new Vec3(2.5, 1, 5.5), new ItemStack(Items.TORCH));       // 3 off
        });
        helper.runAtTickTime(4, () -> {
            CindermothSwarm.Light l = s.brightest();
            helper.assertTrue(l != null && l.item() == torch[0], "a torch on the ground beats an equally bright one held nearer, picked " + l);
            p[0].setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LANTERN));
        });
        helper.runAtTickTime(6, () -> {
            CindermothSwarm.Light l = s.brightest();
            helper.assertTrue(l != null && l.carrier() == p[0] && l.brightness() == 15, "a held lantern beats the torch on the ground, picked " + l);
            p[0].setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p[0].setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.LANTERN));
        });
        helper.runAtTickTime(8, () -> {
            CindermothSwarm.Light l = s.brightest();
            helper.assertTrue(l != null && l.carrier() == p[0], "a lantern in the off hand counts too, picked " + l);
            helper.succeed();
        });
    }

    /**
     * Putting the light away: the swarm seeks a survival player holding a torch; the moment the torch is put away (a
     * stick in hand instead) it loses interest within 10 ticks and drifts again, never having touched them.
     */
    private static void putAway(GameTestHelper helper) {
        ServerPlayer p = holding(helper, new Vec3(2.5, 1, 5.5), new ItemStack(Items.TORCH));
        CindermothSwarm s = swarm(helper, 9.0, 1.5, 5.5);
        float before = p.getHealth();
        long[] away = {-1}, idle = {-1};
        helper.onEachTick(() -> {
            p.doTick();
            long now = helper.getTick();
            if (away[0] < 0 && s.light() != null && s.light().carrier() == p) {
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                away[0] = now;
            }
            if (away[0] >= 0 && idle[0] < 0 && s.light() == null && s.core().idle()) idle[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(away[0] >= 0, "it sought the player's torch");
            helper.assertTrue(idle[0] >= 0, "it lost interest, phase " + s.phase());
            helper.assertTrue(idle[0] - away[0] <= 10, "within 10 ticks, took " + (idle[0] - away[0]));
            helper.assertTrue(helper.getTick() >= idle[0] + 20 && s.light() == null, "and stays uninterested in a stick, light " + s.light());
            helper.assertTrue(p.getHealth() == before, "it never burned them");
        });
    }

    /**
     * A blow scatters it: hit in melee by a survival player holding a torch, it is thrown back at least 0.4 blocks a
     * tick, forgets the light and scatters for exactly 40 ticks. Held in the middle of the player all that time, it burns
     * nothing; once it gathers again it burns them within 15 ticks.
     */
    private static void scatter(GameTestHelper helper) {
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 4.5), new ItemStack(Items.TORCH));
        CindermothSwarm s = swarm(helper, 5.5, 1.5, 7.0);
        float before = p.getHealth();
        int[] scattered = {0};
        long[] over = {-1}, burnt = {-1};
        boolean[] thrown = {false};
        float[] lostInScatter = {0.0F};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(s.hurtServer(helper.getLevel(), s.damageSources().playerAttack(p), 1.0F), "the blow lands");
            Vec3 v = s.getDeltaMovement();
            thrown[0] = v.horizontalDistance() >= 0.4 && v.z > 0.0;
            helper.assertTrue(s.light() == null, "it forgets the light");
        });
        helper.onEachTick(() -> {
            p.doTick();
            long now = helper.getTick();
            if (s.core().in(Step.SCATTER)) {
                scattered[0]++;
                s.setPos(p.getX(), p.getY() + 0.6, p.getZ());          // held right in the middle of the player
                lostInScatter[0] = Math.max(lostInScatter[0], before - p.getHealth());
            } else if (scattered[0] > 0 && over[0] < 0) {
                over[0] = now;
            }
            if (over[0] >= 0 && burnt[0] < 0 && p.getHealth() < before) burnt[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown[0], "the blow throws it back, away from the player");
            helper.assertTrue(over[0] >= 0, "the scatter ended, " + scattered[0] + " ticks so far");
            helper.assertTrue(Math.abs(scattered[0] - 40) <= 1, "it scatters for 40 ticks, scattered " + scattered[0]);
            helper.assertTrue(lostInScatter[0] == 0.0F, "scattered, it burns nothing, the player lost " + lostInScatter[0]);
            helper.assertTrue(burnt[0] >= 0 && burnt[0] - over[0] <= 15, "gathered again, it burns them within 15 ticks, at " + (burnt[0] - over[0]));
        });
    }

    /**
     * A sweep cuts it: a survival player with a diamond sword (7). A barely charged swing takes a little and leaves it
     * alive; a fully charged swing, standing on the floor, takes double (14) and kills it outright at 10 health; a fully
     * charged punch takes exactly 1, not doubled.
     */
    private static void sweep(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(5.5, 1, 4.5));
        CindermothSwarm weak = swarm(helper, 5.5, 1.0, 5.6);
        CindermothSwarm cut = swarm(helper, 4.5, 1.0, 5.6);
        CindermothSwarm punched = swarm(helper, 6.5, 1.0, 5.6);
        for (CindermothSwarm s : List.of(weak, cut, punched)) s.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            p.doTick();                // the sword counts from the player's next tick, and drawing it resets the swing
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) == 7.0, "the sword hits for 7, " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(p.getAttackStrengthScale(0.5F) < 0.5F, "the swing is barely charged, " + p.getAttackStrengthScale(0.5F));
            p.attack(weak);
            helper.assertTrue(weak.isAlive() && weak.getHealth() > 7.0F, "a weak swing leaves it alive, health " + weak.getHealth());
            for (int i = 0; i < 20; i++) p.doTick();
            helper.assertTrue(p.getAttackStrengthScale(0.5F) >= 0.9F, "now the swing is charged, " + p.getAttackStrengthScale(0.5F));
            p.attack(cut);
            helper.assertTrue(!cut.isAlive(), "a sweeping swing takes 14 and kills it outright, health " + cut.getHealth());
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            for (int i = 0; i < 20; i++) p.doTick();
            p.attack(punched);
            helper.assertTrue(Math.abs(punched.getHealth() - 9.0F) < 0.01F, "a charged punch takes exactly 1, health " + punched.getHealth());
            helper.succeed();
        });
    }

    /** It flies slowly: seeking a torch 7.5 blocks off it never moves more than 0.12 blocks a tick, yet covers at least 2.5 blocks in 40 ticks. */
    private static void slow(GameTestHelper helper) {
        ServerPlayer p = holding(helper, new Vec3(1.5, 1, 5.5), new ItemStack(Items.TORCH));
        CindermothSwarm s = swarm(helper, 9.0, 1.5, 5.5);
        double[] top = {0.0};
        int[] seeking = {0};
        Vec3[] last = {null}, start = {null};
        double[] covered = {-1};
        helper.onEachTick(() -> {
            p.doTick();
            Vec3 now = s.position();
            if (s.core().in(Step.SEEK)) {
                if (start[0] == null) start[0] = now;
                if (last[0] != null) top[0] = Math.max(top[0], now.distanceTo(last[0]));
                if (++seeking[0] == 40) covered[0] = now.distanceTo(start[0]);
            }
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(covered[0] >= 0.0, "it sought the torch for 40 ticks, " + seeking[0] + " so far");
            helper.assertTrue(top[0] <= 0.12 + 1.0E-3, "never faster than 0.12 blocks a tick, top " + top[0]);
            helper.assertTrue(covered[0] >= 2.5, "but it gets there: at least 2.5 blocks in 40 ticks, " + covered[0]);
        });
    }

    /**
     * A target handed to it (a pig) is sought as the dimmest light: it goes to the pig and burns it 1 every 20 ticks,
     * the pig alight (a real ticking body: its own fire never adds a point). A torch dropped near by then wins it away.
     */
    private static void handedTarget(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(3.5, 1, 5.5));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        CindermothSwarm s = swarm(helper, 7.5, 1.5, 5.5);
        s.setTarget(pig);
        List<Long> at = new ArrayList<>();
        List<Float> lost = new ArrayList<>();
        float[] last = {100.0F};
        boolean[] lit = {false};
        ItemEntity[] torch = {null};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (pig.getHealth() < last[0]) {
                at.add(now);
                lost.add(last[0] - pig.getHealth());
                lit[0] |= pig.isOnFire();
            }
            last[0] = pig.getHealth();
            if (torch[0] == null && at.size() >= 3) torch[0] = item(helper, 8.5, 9.5, new ItemStack(Items.TORCH));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(at.size() >= 3, "it burned the pig 3 times, " + at.size() + " so far");
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(Math.abs(lost.get(i) - 1.0F) < 0.01F, "each burn takes 1, burn " + i + " took " + lost.get(i));
                if (i > 0) helper.assertTrue(at.get(i) - at.get(i - 1) == 20, "one burn every 20 ticks, " + at);
            }
            helper.assertTrue(lit[0], "the pig is alight");
            helper.assertTrue(s.light() != null && s.light().item() == torch[0], "a torch on the ground wins it away from the pig, light " + s.light());
        });
    }

    /** Its home: the soul sand valley and the nether wastes, spawn weight 12, alone; a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("cindermoth_swarm"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SOUL_SAND_VALLEY)), "its home holds the soul sand valley");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.NETHER_WASTES)), "its home holds the nether wastes");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.WARPED_FOREST)), "its home never holds the warped forest");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/cindermoth_swarm")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.CINDERMOTH_SWARM.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 12, "spawn weight 12, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.CINDERMOTH_SWARM.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
