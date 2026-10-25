package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.Prismback;
import com.panda.teeming.mob.nether.Prismback.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Prismback: projectiles in {@code #teeming:reflectable} come back at 0.8 of their speed, owned by it, and can hit their
 * shooter; a shot rising into its belly gets through; every 120 ticks a 10-tick glimmer leads into a 20-tick flash in
 * which it stands still and bites nothing, and a melee blow loses half to the attacker; a flash that turns nothing
 * leaves it spent for 40 ticks, when shots strike home. Up close it bites for 5. Every number is checked against a
 * plain value.
 *
 * <p>Most Prismbacks here have their goals cleared and a follow range of 1, so they act only on what the test gives
 * them, and their own flash cycle set 1000 ticks off; they leave with the test. Shots are real arrows without gravity,
 * fired level along the arena, and are removed when the test ends.
 */
public final class PrismbackTests {
    private PrismbackTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("prismback_tell", PrismbackTests::tell);
        out.accept("prismback_counter", PrismbackTests::spentLetsShotsIn);
        out.accept("prismback_failure", PrismbackTests::blowInFlash);
        out.accept("prismback_cycle", PrismbackTests::cycle);
        TeemingGameTests.timeLimit("prismback_cycle", 180);
        out.accept("prismback_spent_length", PrismbackTests::spentLength);
        out.accept("prismback_reflect_speed", PrismbackTests::reflectSpeed);
        out.accept("prismback_hits_shooter", PrismbackTests::hitsShooter);
        out.accept("prismback_reflectable", PrismbackTests::reflectable);
        out.accept("prismback_trident", PrismbackTests::trident);
        out.accept("prismback_belly", PrismbackTests::belly);
        out.accept("prismback_reflect_cancel", PrismbackTests::reflectCancel);
        out.accept("prismback_blow_between", PrismbackTests::blowBetween);
        out.accept("prismback_bite", PrismbackTests::bite);
        out.accept("prismback_stats", PrismbackTests::stats);
        out.accept("prismback_home", PrismbackTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A Prismback that acts only on what the test tells it: no goals, a follow range of 1, its own flash far off. */
    private static Prismback prismback(GameTestHelper helper, Vec3 at) {
        Prismback p = helper.spawn(ModMobs.PRISMBACK.get(), at);
        p.core().setLook("base", true);
        p.removeFreeWill();
        p.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        p.glimmerIn(1000);
        TestPlayers.whenDone(helper, p::discard);
        return p;
    }

    /** A still Prismback: no AI, no gravity, so it stays exactly where it is put. */
    private static Prismback still(GameTestHelper helper, Vec3 at) {
        Prismback p = prismback(helper, at);
        p.setNoAi(true);
        p.setNoGravity(true);
        return p;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    /** A real arrow without gravity, from {@code from} along {@code dir} at 1.5 blocks a tick, shot by {@code owner}. */
    private static Arrow arrow(GameTestHelper helper, Vec3 from, Vec3 dir, Entity owner) {
        Vec3 at = helper.absoluteVec(from);
        Arrow arrow = new Arrow(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.ARROW), null);
        if (owner != null) arrow.setOwner(owner);
        arrow.setNoGravity(true);
        arrow.shoot(dir.x, dir.y, dir.z, 1.5F, 0.0F);
        helper.getLevel().addFreshEntity(arrow);
        TestPlayers.whenDone(helper, () -> {
            if (!arrow.isRemoved()) arrow.discard();
        });
        return arrow;
    }

    private static float lost(net.minecraft.world.entity.LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    /** Thorns from a mob scale with difficulty: half of a 10 blow (5) is 3.5 on Easy, 5 on Normal, 7.5 on Hard. */
    private static float halfOfTenOnPlayer(GameTestHelper helper) {
        return switch (helper.getLevel().getDifficulty()) {
            case EASY -> 3.5F;
            case HARD -> 7.5F;
            default -> 5.0F;
        };
    }

    // ---- the tell --------------------------------------------------------------------------------------------------

    /**
     * The tell: a 10-tick glimmer, then the flash holds exactly 20 ticks, in which it stands still and bites nothing,
     * though a pig it hunts stands in reach.
     */
    private static void tell(GameTestHelper helper) {
        Prismback p = helper.spawn(ModMobs.PRISMBACK.get(), new Vec3(5.5, 1, 5.5));
        p.core().setLook("base", true);
        p.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(2.0);   // the pig only: no survivor from another arena
        TestPlayers.whenDone(helper, p::discard);
        Pig pig = pig(helper, new Vec3(5.5, 1, 6.8));
        p.setTarget(pig);
        p.glimmerIn(3);
        int[] glimmer = {0}, flash = {0};
        float[] pigAtFlash = {-1.0F};
        Vec3[] posAtFlash = {null};
        boolean[] quiet = {true}, glimmerFirst = {false}, ended = {false};
        Step[] last = {Step.IDLE};
        helper.onEachTick(() -> {
            Step now = (Step) p.phase();
            if (now == Step.FLASH) {
                if (last[0] != Step.FLASH) {
                    glimmerFirst[0] = last[0] == Step.GLIMMER;
                    pigAtFlash[0] = pig.getHealth();
                    posAtFlash[0] = p.position();
                }
                flash[0]++;
                if (pig.getHealth() < pigAtFlash[0]) quiet[0] = false;
                if (Math.hypot(p.getX() - posAtFlash[0].x, p.getZ() - posAtFlash[0].z) > 0.01) quiet[0] = false;
            } else if (now == Step.GLIMMER) {
                glimmer[0]++;
            } else if (flash[0] > 0) {
                ended[0] = true;
            }
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the flash ends, " + flash[0] + " ticks so far");
            helper.assertTrue(glimmerFirst[0] && glimmer[0] == 10, "a 10-tick glimmer leads into it, saw " + glimmer[0]);
            helper.assertTrue(flash[0] == 20, "the flash holds 20 ticks, saw " + flash[0]);
            helper.assertTrue(quiet[0], "in the flash it neither bites nor moves");
        });
    }

    // ---- the counter and the failure -------------------------------------------------------------------------------

    /** The counter: a flash with nothing to turn leaves it spent, and then an arrow strikes home instead of coming back. */
    private static void spentLetsShotsIn(GameTestHelper helper) {
        Prismback p = still(helper, new Vec3(5.5, 1, 7.5));
        Pig shooter = pig(helper, new Vec3(5.5, 1, 3.0));
        Arrow[] shot = {null};
        helper.runAtTickTime(2, () -> helper.assertTrue(p.flash(), "the flash starts"));
        helper.runAtTickTime(26, () -> {
            helper.assertTrue(p.core().in(Step.SPENT), "a flash that turned nothing leaves it spent, phase " + p.phase());
            shot[0] = arrow(helper, new Vec3(5.5, 1.35, 4.5), new Vec3(0, 0, 1), shooter);
        });
        helper.runAtTickTime(34, () -> {
            helper.assertTrue(lost(p) > 0.5F, "the arrow struck home, it lost " + lost(p));
            helper.assertTrue(p.lastReflectOut().equals(Vec3.ZERO), "nothing was turned");
            helper.assertTrue(shot[0].isRemoved() || shot[0].getOwner() == shooter, "the arrow is still the pig's");
            helper.succeed();
        });
    }

    /**
     * The failure: a 10 blow during the flash is halved (5 reaches it, 4.3 after its armour 6) and the other half goes
     * back to the player who struck it.
     */
    private static void blowInFlash(GameTestHelper helper) {
        Prismback p = prismback(helper, new Vec3(5.5, 1, 5.5));
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(4.3, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(p.flash(), "the flash starts");
            float before = player.getHealth();
            p.hurtServer(helper.getLevel(), p.damageSources().playerAttack(player), 10.0F);
            float back = before - player.getHealth();
            helper.assertTrue(Math.abs(lost(p) - 4.3F) < 0.01F, "half the blow, 5, reaches it: 4.3 after armour 6, it lost " + lost(p));
            helper.assertTrue(Math.abs(back - halfOfTenOnPlayer(helper)) < 0.01F,
                    "the other half goes back: the player lost " + back + ", want " + halfOfTenOnPlayer(helper));
            helper.assertTrue(p.fed(), "the flash turned something");
            helper.succeed();
        });
    }

    /** The same 10 blow between flashes lands whole (9.52 after armour 6) and sends nothing back. */
    private static void blowBetween(GameTestHelper helper) {
        Prismback p = prismback(helper, new Vec3(5.5, 1, 5.5));
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(4.3, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            float before = player.getHealth();
            p.hurtServer(helper.getLevel(), p.damageSources().playerAttack(player), 10.0F);
            helper.assertTrue(Math.abs(lost(p) - 9.52F) < 0.01F, "a 10 blow at rest takes 9.52 after armour 6, it lost " + lost(p));
            helper.assertTrue(player.getHealth() >= before, "nothing comes back, the player lost " + (before - player.getHealth()));
            helper.succeed();
        });
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    /** Left to itself it flashes every 120 ticks: flash starts 120 ticks apart, each after a 10-tick glimmer, 20 long. */
    private static void cycle(GameTestHelper helper) {
        Prismback p = prismback(helper, new Vec3(5.5, 1, 5.5));
        p.glimmerIn(3);
        int[] tick = {0};
        List<Integer> flashStarts = new ArrayList<>(), glimmerStarts = new ArrayList<>(), flashLengths = new ArrayList<>();
        Step[] last = {Step.IDLE};
        int[] run = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            Step now = (Step) p.phase();
            if (now != last[0]) {
                if (now == Step.FLASH) flashStarts.add(tick[0]);
                if (now == Step.GLIMMER) glimmerStarts.add(tick[0]);
                if (last[0] == Step.FLASH) flashLengths.add(run[0]);
                run[0] = 0;
            }
            run[0]++;
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(flashStarts.size() >= 2 && flashLengths.size() >= 2, "two flashes so far: " + flashStarts);
            int gap = flashStarts.get(1) - flashStarts.get(0);
            helper.assertTrue(gap == 120, "one flash every 120 ticks, the gap was " + gap);
            helper.assertTrue(glimmerStarts.size() >= 2 && flashStarts.get(0) - glimmerStarts.get(0) == 10
                    && flashStarts.get(1) - glimmerStarts.get(1) == 10, "each flash comes 10 ticks after a glimmer: " + glimmerStarts + " " + flashStarts);
            helper.assertTrue(flashLengths.get(0) == 20 && flashLengths.get(1) == 20, "each flash lasts 20 ticks: " + flashLengths);
        });
    }

    /** Spent lasts exactly 40 ticks after an unfed flash; a flash that turned a blow is not followed by it. */
    private static void spentLength(GameTestHelper helper) {
        Prismback unfed = prismback(helper, new Vec3(3.5, 1, 5.5));
        Prismback fed = prismback(helper, new Vec3(8.5, 1, 5.5));
        Pig attacker = pig(helper, new Vec3(8.5, 1, 4.2));
        int[] spent = {0}, fedSpent = {0};
        boolean[] over = {false};
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(unfed.flash() && fed.flash(), "both flash");
            fed.hurtServer(helper.getLevel(), fed.damageSources().mobAttack(attacker), 2.0F);
            helper.assertTrue(fed.fed(), "the blow fed the second one's flash");
        });
        helper.onEachTick(() -> {
            if (unfed.core().in(Step.SPENT)) spent[0]++;
            else if (spent[0] > 0) over[0] = true;
            if (fed.core().in(Step.SPENT)) fedSpent[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the spent spell ends, " + spent[0] + " ticks so far");
            helper.assertTrue(spent[0] == 40, "spent for 40 ticks, saw " + spent[0]);
            helper.assertTrue(unfed.core().idle(), "then back at rest, phase " + unfed.phase());
            helper.assertTrue(fedSpent[0] == 0, "a fed flash leaves it unspent, saw " + fedSpent[0] + " spent ticks");
        });
    }

    // ---- turning projectiles ---------------------------------------------------------------------------------------

    /** An arrow comes straight back the way it came at 0.8 of its speed, owned by the Prismback, which is unhurt. */
    private static void reflectSpeed(GameTestHelper helper) {
        Prismback p = still(helper, new Vec3(5.5, 1, 7.5));
        Arrow a = arrow(helper, new Vec3(5.5, 1.35, 4.0), new Vec3(0, 0, 1), null);
        double[] hitZ = {Double.NaN};
        helper.succeedWhen(() -> {
            Vec3 in = p.lastReflectIn(), out = p.lastReflectOut();
            helper.assertTrue(!out.equals(Vec3.ZERO), "the arrow was turned");
            if (Double.isNaN(hitZ[0])) hitZ[0] = a.getZ();
            double ratio = out.length() / in.length();
            double dot = out.dot(in) / (out.length() * in.length());
            helper.assertTrue(Math.abs(ratio - 0.8) < 1.0E-6, "back at 0.8 of its speed, got " + ratio);
            helper.assertTrue(Math.abs(dot + 1.0) < 1.0E-6, "straight back the way it came, cosine " + dot);
            helper.assertTrue(a.getOwner() == p, "the turned arrow is the Prismback's, owner " + a.getOwner());
            helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "it is unhurt");
            helper.assertTrue(a.getZ() < hitZ[0] - 1.0, "the arrow flies back off it, z " + a.getZ() + " from " + hitZ[0]);
            a.discard();
        });
    }

    /** The turned arrow strikes the pig that shot it, as the Prismback's shot. */
    private static void hitsShooter(GameTestHelper helper) {
        Prismback p = still(helper, new Vec3(5.5, 1, 7.5));
        Pig shooter = pig(helper, new Vec3(5.5, 1, 3.5));
        arrow(helper, new Vec3(5.5, 1.4, 4.05), new Vec3(0, 0, 1), shooter);
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(shooter) > 0.5F, "the shooter was hit, lost " + lost(shooter));
            helper.assertTrue(shooter.getLastHurtByMob() == p, "by the Prismback's shot, last hurt by " + shooter.getLastHurtByMob());
            helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "the Prismback is unhurt");
        });
    }

    /**
     * The tag holds arrows, spectral arrows, tridents, snowballs, eggs and small fireballs: a snowball is turned (now
     * the Prismback's, flying back), and llama spit, which is not in the tag, lands and hurts.
     */
    private static void reflectable(GameTestHelper helper) {
        for (EntityType<?> type : List.of(EntityTypes.ARROW, EntityTypes.SPECTRAL_ARROW, EntityTypes.TRIDENT, EntityTypes.SNOWBALL,
                EntityTypes.EGG, EntityTypes.SMALL_FIREBALL)) {
            helper.assertTrue(type.builtInRegistryHolder().is(Prismback.REFLECTABLE), type + " is reflectable");
        }
        helper.assertTrue(!EntityTypes.LLAMA_SPIT.builtInRegistryHolder().is(Prismback.REFLECTABLE), "llama spit is not");
        Prismback turns = still(helper, new Vec3(3.5, 1, 7.5));
        Prismback takes = still(helper, new Vec3(8.5, 1, 7.5));
        Pig shooter = pig(helper, new Vec3(5.5, 1, 3.0));
        Snowball ball = helper.spawn(EntityTypes.SNOWBALL, new Vec3(3.5, 1.35, 5.5));
        ball.setOwner(shooter);
        ball.setNoGravity(true);
        ball.setDeltaMovement(0, 0, 0.8);
        LlamaSpit spit = helper.spawn(EntityTypes.LLAMA_SPIT, new Vec3(8.5, 1.35, 5.5));
        spit.setOwner(shooter);
        spit.setNoGravity(true);
        spit.setDeltaMovement(0, 0, 0.8);
        TestPlayers.whenDone(helper, () -> {
            if (!ball.isRemoved()) ball.discard();
            if (!spit.isRemoved()) spit.discard();
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(ball.isAlive() && ball.getOwner() == turns && ball.getDeltaMovement().z < 0,
                    "the snowball is turned back, owner " + ball.getOwner() + ", motion " + ball.getDeltaMovement());
            helper.assertTrue(lost(takes) > 0.1F && takes.lastReflectOut().equals(Vec3.ZERO), "the spit landed, it lost " + lost(takes));
            ball.discard();
            helper.succeed();
        });
    }

    /** A turned trident keeps its thrower as owner, so Loyalty still brings it home; it flies back all the same. */
    private static void trident(GameTestHelper helper) {
        Prismback p = still(helper, new Vec3(5.5, 1, 7.5));
        Pig thrower = pig(helper, new Vec3(5.5, 1, 3.0));
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 1.35, 4.5));
        ThrownTrident t = new ThrownTrident(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.TRIDENT));
        t.setOwner(thrower);
        t.setNoGravity(true);
        t.shoot(0, 0, 1, 1.5F, 0.0F);
        helper.getLevel().addFreshEntity(t);
        TestPlayers.whenDone(helper, () -> {
            if (!t.isRemoved()) t.discard();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!p.lastReflectOut().equals(Vec3.ZERO), "the trident was turned");
            helper.assertTrue(t.getOwner() == thrower, "it is still the thrower's, owner " + t.getOwner());
            helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "the Prismback is unhurt");
            t.discard();
        });
    }

    /** A shot rising into its bare belly from below gets through; the same shot coming down onto its back is turned. */
    private static void belly(GameTestHelper helper) {
        Prismback under = still(helper, new Vec3(3.5, 3, 5.5));
        Prismback over = still(helper, new Vec3(8.5, 3, 5.5));
        arrow(helper, new Vec3(3.5, 1.4, 5.5), new Vec3(0, 1, 0), null);
        Arrow down = arrow(helper, new Vec3(8.5, 5.5, 5.5), new Vec3(0, -1, 0), null);
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(lost(under) > 0.5F && under.lastReflectOut().equals(Vec3.ZERO), "the rising shot struck its belly, it lost " + lost(under));
            helper.assertTrue(over.getHealth() >= over.getMaxHealth() && over.lastReflectOut().y > 0.0,
                    "the falling shot was turned back up off its back, out " + over.lastReflectOut());
            down.discard();
            helper.succeed();
        });
    }

    /** Packs can veto the turn: with the "reflect" step cancelled, the arrow lands. */
    private static void reflectCancel(GameTestHelper helper) {
        Prismback p = still(helper, new Vec3(5.5, 1, 7.5));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == p && e.step().equals("reflect")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        arrow(helper, new Vec3(5.5, 1.35, 4.0), new Vec3(0, 0, 1), null);
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(lost(p) > 0.5F && p.lastReflectOut().equals(Vec3.ZERO), "the vetoed turn let the arrow land, it lost " + lost(p));
            helper.succeed();
        });
    }

    // ---- the bite and the row --------------------------------------------------------------------------------------

    /** Its bite is its attack: a still pig loses 5. */
    private static void bite(GameTestHelper helper) {
        Prismback p = prismback(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.8, 1, 5.5));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(p.doHurtTarget(helper.getLevel(), pig), "the bite lands");
            helper.assertTrue(Math.abs(lost(pig) - 5.0F) < 0.01F, "a bite does 5, lost " + lost(pig));
            helper.succeed();
        });
    }

    /** Health 30, attack 5, armour 6, a 1.0 x 0.7 hitbox; a monster fire doesn't hurt; no weak point; two looks. */
    private static void stats(GameTestHelper helper) {
        Prismback p = prismback(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(p.getMaxHealth() - 30.0F) < 0.01F && Math.abs(p.getHealth() - 30.0F) < 0.01F,
                    "health 30, got " + p.getHealth() + "/" + p.getMaxHealth());
            helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 5.0) < 0.01, "attack 5");
            helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - 6.0) < 0.01, "armour 6");
            helper.assertTrue(Math.abs(p.getBbWidth() - 1.0F) < 0.02F && Math.abs(p.getBbHeight() - 0.7F) < 0.02F,
                    "hitbox 1.0 x 0.7, got " + p.getBbWidth() + " x " + p.getBbHeight());
            helper.assertTrue(p.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(p.getType().fireImmune(), "fire doesn't hurt it");
            helper.assertTrue(!p.vulnerable(), "it has no weak point: nothing on it is ever gold");
            List<String> looks = Looks.of("prismback").stream().map(LookDef::id).sorted().toList();
            helper.assertTrue(looks.equals(List.of("base", "smoky_prismback")), "looks base and smoky_prismback, got " + looks);
            helper.succeed();
        });
    }

    /** Home: the nether wastes; spawn weight 10, in groups of 1 to 2. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("prismback"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.NETHER_WASTES)), "its home holds the nether wastes");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/prismback")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.PRISMBACK.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 10, "spawn weight 10, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "in groups of 1 to 2, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
