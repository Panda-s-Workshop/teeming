package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.overworld.Skreel;
import com.panda.teeming.mob.overworld.Skreel.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.SpawnTable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Skreel: it screeches only from 10 to 20 blocks above its target, the 20-tick screech hangs it in the air and hurts
 * nothing, then it dives in a straight line at 1 block a tick. A dive that lands does 1.5x its attack (7.5) and it
 * climbs back; a dive into a raised shield stuns it on the ground for 60 ticks, taking x1.5; a target that steps out of
 * the line is missed. After a dive it waits 80 ticks before the next screech.
 *
 * <p>Pigs stand in as targets where the numbers must be exact (no difficulty scaling). A shield needs a real survival
 * player who holds it up: the survivor is ticked by hand until the shield is raised (nothing else ticks it here).
 */
public final class SkreelTests {
    private SkreelTests() {}

    /** Where the target stands: the middle of the arena floor. */
    private static final Vec3 GROUND = new Vec3(5.5, 1, 5.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("skreel_tell", SkreelTests::tell);
        out.accept("skreel_counter", SkreelTests::shieldStuns);
        out.accept("skreel_failure", SkreelTests::diveHits);
        out.accept("skreel_stun_length", SkreelTests::stunLength);
        TeemingGameTests.timeLimit("skreel_stun_length", 160);
        out.accept("skreel_dive_low", SkreelTests::diveLow);
        out.accept("skreel_dive_high", SkreelTests::diveHigh);
        out.accept("skreel_dive_speed", SkreelTests::diveSpeed);
        out.accept("skreel_sidestep", SkreelTests::sidestep);
        out.accept("skreel_cooldown", SkreelTests::cooldown);
        TeemingGameTests.timeLimit("skreel_cooldown", 200);
        out.accept("skreel_dive_cancel", SkreelTests::diveCancel);
        out.accept("skreel_stats", SkreelTests::stats);
        out.accept("skreel_home", SkreelTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    /**
     * A Skreel {@code up} blocks above the arena floor at {@code x}, ready to screech. The test server roofs every
     * arena with barriers 8 above the floor, which leaves no sky to dive from, so the roof over this arena is opened
     * first, and the chunks round the arena are kept ticking; the bird is removed when the test ends, wherever it flew.
     */
    private static Skreel skreel(GameTestHelper helper, double x, double up) {
        openRoof(helper);
        TestPlayers.keepTicking(helper, 4);              // it circles 6 out from its target: 1 block past the edge, 4 after a sidestep
        Skreel bird = helper.spawn(ModMobs.SKREEL.get(), new Vec3(x, 1 + up, 5.5));
        bird.resetCooldown();
        TestPlayers.whenDone(helper, bird::discard);
        return bird;
    }

    /** Clears the barrier roof over the arena (the two layers above its 8 rows), so the sky above it is open. */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        for (int y = 2; y <= 26; y++) {
            BlockPos pos = new BlockPos(5, y, 5);
            if (!helper.getBlockState(pos).isAir()) helper.fail("the sky over the arena is shut at y " + y + " by " + helper.getBlockState(pos));
        }
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    /** A pig on the floor and a Skreel {@code up} blocks straight above it, hunting it. */
    private static Skreel overPig(GameTestHelper helper, double up, Pig[] out) {
        Pig pig = pig(helper, GROUND);
        Skreel bird = skreel(helper, GROUND.x, up);
        bird.setTarget(pig);
        out[0] = pig;
        return bird;
    }

    /**
     * A survival player at x 3.5 holding a shield up toward +x, and a Skreel 4 blocks further along x and 12 above,
     * hunting the player. The player is ticked by hand until the shield counts as raised.
     */
    private static Skreel overShield(GameTestHelper helper, ServerPlayer[] out) {
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(3.5, 1, 5.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.MAIN_HAND);
        Skreel bird = skreel(helper, 7.5, 12);
        bird.setTarget(p);
        helper.onEachTick(() -> {
            if (p.isUsingItem() && !p.isBlocking()) p.doTick();
            p.setYRot(-90.0F);         // facing +x, toward the bird
            p.setYHeadRot(-90.0F);
            p.yHeadRotO = -90.0F;
        });
        out[0] = p;
        return bird;
    }

    /** An entity for a failure message: its kind, name and place relative to this arena. */
    private static String who(GameTestHelper helper, net.minecraft.world.entity.Entity e) {
        if (e == null) return "none";
        Vec3 at = helper.relativeVec(e.position());
        return e.getType().builtInRegistryHolder().key().identifier() + " " + e.getName().getString()
                + String.format(" at %.1f %.1f %.1f", at.x, at.y, at.z) + (e.isAlive() ? "" : " dead " + e.getRemovalReason());
    }

    /**
     * What a screech needs, read now: a target, open sky over it, the height band, reach, sight and no cooldown; and
     * whether the bird's chunk ticks entities at all.
     */
    private static String screechCheck(GameTestHelper helper, Skreel bird) {
        var t = bird.getTarget();
        if (t == null) return "no target, bird at " + who(helper, bird);
        boolean sky = true;
        BlockPos.MutableBlockPos pos = t.blockPosition().mutable();
        for (int i = 1; i <= Skreel.MIN_HEIGHT + 2; i++) {
            pos.move(0, 1, 0);
            sky &= helper.getLevel().getBlockState(pos).getCollisionShape(helper.getLevel(), pos).isEmpty();
        }
        return String.format("target %s, bird %s (ticking %s), sky %s, height %.1f, across %.1f, sight %s, cooldown %d",
                who(helper, t), who(helper, bird), helper.getLevel().isPositionEntityTicking(bird.blockPosition()), sky,
                bird.getY() - t.getY(), bird.position().subtract(t.position()).horizontalDistance(),
                bird.getSensing().hasLineOfSight(t), bird.cooldown());
    }

    private static double height(Skreel bird, net.minecraft.world.entity.Entity target) {
        return bird.getY() - target.getY();
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /** 12 above a pig it screeches at once, for 20 ticks, hanging in the air and hurting nothing; the dive follows. */
    private static void tell(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Skreel bird = overPig(helper, 12, pig);
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] harmed = {false}, dived = {false};
        Vec3[] start = {null};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            if (bird.core().in(Step.DIVE)) dived[0] = true;
            if (!bird.core().in(Step.TELL)) return;
            if (first[0] < 0) {
                first[0] = helper.getTick();
                start[0] = bird.position();
            }
            ticks[0]++;
            harmed[0] |= pig[0].getHealth() < pig[0].getMaxHealth();
            moved[0] = Math.max(moved[0], bird.position().distanceTo(start[0]));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && !bird.core().in(Step.TELL), "the screech runs and ends, " + ticks[0] + " ticks so far");
            helper.assertTrue(first[0] <= 3, "12 above its target it screeches at once, started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 20) <= 1, "the screech lasts 20 ticks, lasted " + ticks[0]);
            helper.assertTrue(!harmed[0], "the screech hurts nothing");
            helper.assertTrue(moved[0] < 1.0, "it hangs in the air while it screeches, moved " + moved[0]);
            helper.assertTrue(dived[0], "the dive follows the screech, phase " + bird.phase());
        });
    }

    /**
     * The counter: a raised shield takes the dive. The player is unhurt, the Skreel lies stunned with its weak point
     * showing and takes x1.5 (4 becomes 6).
     */
    private static void shieldStuns(GameTestHelper helper) {
        ServerPlayer[] p = new ServerPlayer[1];
        Skreel bird = overShield(helper, p);
        float before = p[0].getHealth();
        boolean[] dived = {false};
        helper.onEachTick(() -> dived[0] |= bird.core().in(Step.DIVE));
        helper.succeedWhen(() -> {
            helper.assertTrue(p[0].isBlocking(), "the player holds the shield up");
            helper.assertTrue(bird.core().in(Step.STUNNED), "a blocked dive stuns it, phase " + bird.phase());
            helper.assertTrue(dived[0], "it got there by diving");
            helper.assertTrue(bird.vulnerable(), "stunned, its weak point shows");
            helper.assertTrue(p[0].getHealth() >= before, "the shield took it all, health " + p[0].getHealth() + " of " + before);
            float had = bird.getHealth();
            bird.hurtServer(helper.getLevel(), bird.damageSources().magic(), 4.0F);
            float lost = had - bird.getHealth();
            helper.assertTrue(Math.abs(lost - 6.0F) < 0.01F, "a stunned Skreel takes x1.5: 4 becomes 6, lost " + lost);
        });
    }

    /** Failing the counter: the dive lands on the pig for 1.5x its attack (7.5), then it climbs back up at least 3 blocks. */
    private static void diveHits(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Skreel bird = overPig(helper, 12, pig);
        float before = pig[0].getHealth();
        float[] lost = {-1.0F};
        double[] low = {Double.MAX_VALUE}, high = {-Double.MAX_VALUE};
        boolean[] screeched = {false};
        helper.onEachTick(() -> {
            screeched[0] |= bird.core().in(Step.TELL);
            if (lost[0] < 0 && pig[0].getHealth() < before) lost[0] = before - pig[0].getHealth();
            if (lost[0] >= 0 && bird.core().in(Step.CLIMB)) {
                low[0] = Math.min(low[0], bird.getY());
                high[0] = Math.max(high[0], bird.getY());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(screeched[0], "it screeched first");
            helper.assertTrue(Math.abs(lost[0] - 7.5F) < 0.01F, "the dive hits for 1.5x Atk (7.5), the pig lost " + lost[0]);
            helper.assertTrue(high[0] - low[0] >= 3.0, "it climbs back up after the hit, rose " + (high[0] - low[0]));
            helper.assertTrue(!bird.core().in(Step.STUNNED), "a dive that lands doesn't stun it");
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The stun from a blocked dive lasts 60 ticks, down on the ground, and then it climbs. */
    private static void stunLength(GameTestHelper helper) {
        ServerPlayer[] p = new ServerPlayer[1];
        Skreel bird = overShield(helper, p);
        int[] stunned = {0};
        boolean[] over = {false}, grounded = {false}, climbed = {false};
        helper.onEachTick(() -> {
            if (bird.core().in(Step.STUNNED)) {
                stunned[0]++;
                grounded[0] |= bird.onGround();
            } else if (stunned[0] > 0) {
                over[0] = true;
                climbed[0] |= bird.core().in(Step.CLIMB);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the stun ends, " + stunned[0] + " ticks so far, phase " + bird.phase());
            helper.assertTrue(Math.abs(stunned[0] - 60) <= 1, "the stun lasts 60 ticks, lasted " + stunned[0]);
            helper.assertTrue(grounded[0], "it lies on the ground while stunned");
            helper.assertTrue(climbed[0], "it climbs back up afterwards, phase " + bird.phase());
        });
    }

    /** Starting 6 above its target it climbs, and screeches as soon as it is 10 above: never lower. */
    private static void diveLow(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Skreel bird = overPig(helper, 6, pig);
        double[] at = {Double.NaN}, before = {Double.NaN};
        helper.onEachTick(() -> {
            // the screech starts from where the bird stood when the tick began, before that tick's move
            if (Double.isNaN(at[0]) && bird.core().in(Step.TELL)) at[0] = before[0];
            before[0] = height(bird, pig[0]);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!Double.isNaN(at[0]), "it climbs and screeches, phase " + bird.phase() + ", height " + height(bird, pig[0]));
            helper.assertTrue(at[0] >= 10.0 - 1.0E-6, "it doesn't screech lower than 10 above, screeched at " + at[0]);
            helper.assertTrue(at[0] <= 10.4, "it screeches as soon as it is 10 above, screeched at " + at[0]);
        });
    }

    /** Starting 24 above its target it sinks, and screeches as soon as it is 20 above: never higher. */
    private static void diveHigh(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Skreel bird = overPig(helper, 24, pig);
        double[] at = {Double.NaN}, before = {Double.NaN};
        helper.onEachTick(() -> {
            // the screech starts from where the bird stood when the tick began, before that tick's move
            if (Double.isNaN(at[0]) && bird.core().in(Step.TELL)) at[0] = before[0];
            before[0] = height(bird, pig[0]);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!Double.isNaN(at[0]), "it sinks and screeches, phase " + bird.phase() + ", height " + height(bird, pig[0]));
            helper.assertTrue(at[0] <= 20.0 + 1.0E-6, "it doesn't screech higher than 20 above, screeched at " + at[0]);
            helper.assertTrue(at[0] >= 19.6, "it screeches as soon as it is 20 above, screeched at " + at[0]);
        });
    }

    /** The dive covers 1 block a tick, measured tick by tick from 16 up. */
    private static void diveSpeed(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Skreel bird = overPig(helper, 16, pig);
        List<Double> steps = new ArrayList<>();
        Vec3[] last = {null};
        helper.onEachTick(() -> {
            if (bird.core().in(Step.DIVE)) {
                if (last[0] != null) steps.add(bird.position().distanceTo(last[0]));
                last[0] = bird.position();
            } else {
                last[0] = null;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(steps.size() >= 6 && !bird.core().in(Step.DIVE), "it dived for at least 6 ticks, saw " + steps.size());
            double mean = steps.subList(1, steps.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            helper.assertTrue(Math.abs(mean - 1.0) < 0.05, "the dive covers 1 block a tick, mean " + mean);
        });
    }

    /**
     * Dodging: the pig steps 4 blocks aside as the dive starts. The dive keeps to its straight line through where the
     * pig stood, misses, and the Skreel pulls up without being stunned.
     */
    private static void sidestep(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Skreel bird = overPig(helper, 12, pig);
        Vec3 stood = helper.absoluteVec(GROUND).add(0, pig[0].getBbHeight() * 0.5, 0);
        boolean[] stepped = {false}, stunned = {false}, climbed = {false};
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            if (bird.core().in(Step.DIVE)) {
                if (!stepped[0]) {
                    pig[0].snapTo(helper.absoluteVec(GROUND.add(4, 0, 0)));
                    stepped[0] = true;
                }
                closest[0] = Math.min(closest[0], bird.position().add(0, bird.getBbHeight() * 0.5, 0).distanceTo(stood));
            }
            stunned[0] |= bird.core().in(Step.STUNNED);
            climbed[0] |= stepped[0] && bird.core().in(Step.CLIMB);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stepped[0] && climbed[0], "it dived and pulled up, phase " + bird.phase());
            helper.assertTrue(bird.aim().distanceTo(stood) < 0.1, "it aimed at where the pig stood when the screech ended, " + bird.aim());
            helper.assertTrue(closest[0] < 1.0, "the dive kept its line through that spot, closest " + closest[0]);
            helper.assertTrue(pig[0].getHealth() >= pig[0].getMaxHealth(), "the pig that stepped aside is unhurt");
            helper.assertTrue(!stunned[0], "a miss doesn't stun it");
        });
    }

    /** After a dive it waits 80 ticks before it screeches again. */
    private static void cooldown(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Skreel bird = overPig(helper, 12, pig);
        pig[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig[0].setHealth(100.0F);
        long[] diveEnd = {-1}, again = {-1};
        boolean[] dived = {false};
        List<String> targets = new ArrayList<>();                            // each change of target, with its tick
        Object[] last = {null};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (bird.getTarget() != last[0]) {
                last[0] = bird.getTarget();
                targets.add(now + ": " + who(helper, bird.getTarget()));
            }
            if (bird.core().in(Step.DIVE)) dived[0] = true;
            else if (dived[0] && diveEnd[0] < 0) diveEnd[0] = now;
            if (diveEnd[0] >= 0 && again[0] < 0 && bird.core().in(Step.TELL)) again[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(again[0] >= 0, "it screeches again, phase " + bird.phase() + ", dive ended at " + diveEnd[0]
                    + "; " + screechCheck(helper, bird) + "; targets " + targets);
            long gap = again[0] - diveEnd[0];
            helper.assertTrue(gap >= 79 && gap <= 82, "80 ticks from the end of the dive to the next screech, saw " + gap);
        });
    }

    /** A listener that cancels the "dive_hit" step spares the target: no damage and no stun. */
    private static void diveCancel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Skreel bird = overPig(helper, 12, pig);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == bird && e.step().equals("dive_hit")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] dived = {false}, climbed = {false}, stunned = {false};
        helper.onEachTick(() -> {
            dived[0] |= bird.core().in(Step.DIVE);
            climbed[0] |= dived[0] && bird.core().in(Step.CLIMB);
            stunned[0] |= bird.core().in(Step.STUNNED);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dived[0] && climbed[0], "it dived and climbed back, phase " + bird.phase());
            helper.assertTrue(pig[0].getHealth() >= pig[0].getMaxHealth(), "a cancelled hit does no damage");
            helper.assertTrue(!stunned[0], "nor stuns it");
        });
    }

    /** Health 16, attack 5, armour 0, a 0.9 x 0.7 hitbox; outside a stun a hit counts at its face value. */
    private static void stats(GameTestHelper helper) {
        Skreel bird = helper.spawn(ModMobs.SKREEL.get(), GROUND);
        bird.removeFreeWill();
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(bird.getMaxHealth() - 16.0F) < 0.01F && Math.abs(bird.getHealth() - 16.0F) < 0.01F,
                    "health 16, got " + bird.getHealth() + "/" + bird.getMaxHealth());
            helper.assertTrue(Math.abs(bird.getAttributeValue(Attributes.ATTACK_DAMAGE) - 5.0) < 0.01, "attack 5");
            helper.assertTrue(Math.abs(bird.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(bird.getBbWidth() - 0.9F) < 0.02F && Math.abs(bird.getBbHeight() - 0.7F) < 0.02F,
                    "hitbox 0.9 x 0.7, got " + bird.getBbWidth() + " x " + bird.getBbHeight());
            helper.assertTrue(!bird.vulnerable(), "not stunned, no weak point");
            float had = bird.getHealth();
            bird.hurtServer(helper.getLevel(), bird.damageSources().magic(), 4.0F);
            float lost = had - bird.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F) < 0.01F, "outside a stun 4 stays 4, lost " + lost);
            helper.succeed();
        });
    }

    /**
     * Its home: windswept hills and stony and jagged peaks, each at its tuned weight (roster weight 10 elsewhere in its
     * tag), in groups of 1 to 2, in any light (a lit spot rates 0, as dark ones do).
     */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/skreel"));
        BiomeModifier modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key)
                .map(h -> h.value()).orElse(null);
        helper.assertTrue(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier, "a spawn modifier for the Skreel, got " + modifier);
        var spawns = (BiomeModifiers.AddSpawnsBiomeModifier) modifier;
        // each home biome has a tuned weight (the Overworld share): its own modifier at that weight, and the roster
        // weight (for biome mods' biomes) leaves it out
        for (var biome : List.of(Biomes.WINDSWEPT_HILLS, Biomes.STONY_PEAKS, Biomes.JAGGED_PEAKS)) {
            String path = biome.identifier().getPath();
            int want = com.panda.teeming.spawn.SpawnTable.tuned("skreel").stream().filter(r -> r.biome().equals(biome.identifier().toString()))
                    .mapToInt(com.panda.teeming.spawn.SpawnTable.Row::weight).findFirst().orElse(0);
            var tuned = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                    .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/skreel/" + path))).map(h -> h.value()).orElse(null);
            helper.assertTrue(!spawns.biomes().contains(access.lookupOrThrow(Registries.BIOME).getOrThrow(biome)), path + " has a tuned weight, so the roster weight leaves it out");
            helper.assertTrue(tuned instanceof BiomeModifiers.AddSpawnsBiomeModifier t && want > 0
                    && t.biomes().contains(access.lookupOrThrow(Registries.BIOME).getOrThrow(biome)) && t.spawners().unwrap().getFirst().weight() == want,
                    "it spawns in " + path + " at its tuned weight " + want + ", got " + tuned);
        }
        helper.assertTrue(!spawns.biomes().contains(biomes.getOrThrow(Biomes.PLAINS)), "not on the plains");
        var entries = spawns.spawners().unwrap();
        helper.assertTrue(entries.size() == 1, "one spawner entry, got " + entries.size());
        var entry = entries.getFirst();
        helper.assertTrue(entry.value().type() == ModMobs.SKREEL.get(), "the entry spawns Skreels");
        // the rest of its tag (a biome mod's biomes; every vanilla biome of its home is tuned) has its rest weight from the
        // spawn table, the share in mobs beside the mobs that live there too, not its roster weight 10
        int rest = SpawnTable.rest("skreel", 10).weight();
        helper.assertTrue(entry.weight() == rest, "rest weight " + rest + ", got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "groups of 1 to 2, got " + entry.value().count().minInclusive() + " to " + entry.value().count().maxInclusive());
        helper.assertTrue(RosterStats.get("skreel").anyLight(), "it spawns in any light");
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at.east(), Blocks.GLOWSTONE);
        Skreel bird = helper.spawn(ModMobs.SKREEL.get(), new Vec3(2.5, 1, 2.5));
        bird.removeFreeWill();
        helper.runAtTickTime(2, () -> {
            float value = bird.getWalkTargetValue(helper.absolutePos(at), helper.getLevel());
            helper.assertTrue(value == 0.0F, "a lit spot rates 0, got " + value);
            helper.succeed();
        });
    }
}
