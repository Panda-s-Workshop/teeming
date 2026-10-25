package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.KeeningShade;
import com.panda.teeming.mob.nether.KeeningShade.Step;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Keening Shade: every 200 ticks, with a target within 10 blocks in its sight, it draws breath for 16 ticks (the tell,
 * which silences no one), then wails: every survival player within 10 blocks with a line of sight to it is Silenced for
 * 80 ticks. A wail that silences no one leaves it voiceless for 200 ticks, and its next breath waits 200 ticks after its
 * voice comes back.
 *
 * <p>The shade stands in the middle of the arena with its goals cleared (it reads its target straight from the test,
 * and a follow range of 1 keeps it from picking up survivors in other arenas). From the middle its 10-block wail never
 * reaches a neighbouring arena: the nearest one starts 11 blocks off. Survival players stand in as the ones it wails at;
 * a still pig is a target with no player in reach. The 10-block reach is measured straight up, through the opened roof,
 * where no other arena lies.
 */
public final class KeeningShadeTests {
    private KeeningShadeTests() {}

    /** The shade, in the middle of the arena. */
    private static final Vec3 AT = new Vec3(6.0, 1, 6.0);
    /** A player 4.5 blocks off, in its sight. */
    private static final Vec3 NEAR = new Vec3(10.5, 1, 6.0);
    /** A player 3.5 blocks off on the other side, behind a pillar in the sight test. */
    private static final Vec3 HIDDEN = new Vec3(6.0, 1, 9.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("keening_shade_tell", KeeningShadeTests::tell);
        out.accept("keening_shade_counter", KeeningShadeTests::counter);
        out.accept("keening_shade_failure", KeeningShadeTests::failure);
        TeemingGameTests.timeLimit("keening_shade_failure", 160);
        out.accept("keening_shade_radius", KeeningShadeTests::radius);
        out.accept("keening_shade_sight", KeeningShadeTests::sight);
        out.accept("keening_shade_start_sight", KeeningShadeTests::startSight);
        out.accept("keening_shade_first_breath", KeeningShadeTests::firstBreath);
        out.accept("keening_shade_cooldown", KeeningShadeTests::cooldown);
        TeemingGameTests.timeLimit("keening_shade_cooldown", 260);
        out.accept("keening_shade_voiceless", KeeningShadeTests::voiceless);
        TeemingGameTests.timeLimit("keening_shade_voiceless", 480);
        out.accept("keening_shade_melee", KeeningShadeTests::melee);
        out.accept("keening_shade_silence_cancel", KeeningShadeTests::silenceCancel);
        out.accept("keening_shade_tell_cancel", KeeningShadeTests::tellCancel);
        out.accept("keening_shade_stats", KeeningShadeTests::stats);
        out.accept("keening_shade_home", KeeningShadeTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------

    private static KeeningShade shade(GameTestHelper helper) {
        KeeningShade s = helper.spawn(ModMobs.KEENING_SHADE.get(), AT);
        s.removeFreeWill();      // no drifting or poking; its own phase machine still runs
        s.resetCooldown();
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // blind to other arenas (0 would mean no limit)
        // the base look now, so its first tick doesn't pick one and refill its health over a test's setHealth
        s.core().setLook(LookDef.BASE, true);
        return s;
    }

    /** A still pig with 40 health. */
    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        pig.setHealth(40.0F);
        return pig;
    }

    private static boolean silenced(ServerPlayer p) {
        return p.hasEffect(ModEffects.SILENCED.holder());
    }

    /** A stone wall across the arena at {@code x}, four high, between the shade and anyone past it. */
    private static void wallAtX(GameTestHelper helper, int x) {
        for (int z = 0; z < 12; z++) {
            for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
    }

    /**
     * Clears the barrier roof over the arena and checks the sky above its middle is open well past 10 blocks, for the
     * test that measures the reach straight up.
     */
    private static void openRoof(GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                for (int y = 8; y <= 9; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(Blocks.BARRIER)) helper.setBlock(pos, Blocks.AIR);
                }
            }
        }
        for (int y = 2; y <= 16; y++) {
            for (BlockPos pos : List.of(new BlockPos(5, y, 5), new BlockPos(6, y, 6), new BlockPos(5, y, 6), new BlockPos(6, y, 5))) {
                if (!helper.getBlockState(pos).isAir()) helper.fail("the sky over the arena is shut at y " + y + " by " + helper.getBlockState(pos));
            }
        }
    }

    // ---- tell, counter, failure ------------------------------------------------------------------------------------

    /**
     * A survival player 4.5 blocks away in sight sets off the breath at once. It lasts 16 ticks, the shade holds still,
     * and nobody is Silenced yet; the wail follows it and Silences the player.
     */
    private static void tell(GameTestHelper helper) {
        KeeningShade s = shade(helper);
        ServerPlayer p = TestPlayers.survivor(helper, NEAR);
        s.setTarget(p);
        Vec3 start = s.position();
        int[] ticks = {0};
        long[] first = {-1};
        boolean[] early = {false}, wailed = {false};
        double[] moved = {0.0};
        helper.onEachTick(() -> {
            wailed[0] |= s.core().in(Step.WAIL);
            if (!s.core().in(Step.TELL)) return;
            if (first[0] < 0) first[0] = helper.getTick();
            ticks[0]++;
            early[0] |= silenced(p);
            moved[0] = Math.max(moved[0], s.position().subtract(start).horizontalDistance());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] > 0 && wailed[0], "the breath runs and the wail follows, " + ticks[0] + " tell ticks, phase " + s.phase());
            helper.assertTrue(first[0] <= 3, "a target in range and sight sets it off at once, it started at tick " + first[0]);
            helper.assertTrue(Math.abs(ticks[0] - 16) <= 1, "the breath lasts 16 ticks, lasted " + ticks[0]);
            helper.assertTrue(!early[0], "nobody is Silenced while it breathes in");
            helper.assertTrue(moved[0] < 0.1, "it holds still while it breathes in, moved " + moved[0]);
            helper.assertTrue(silenced(p), "the wail Silences the player");
        });
    }

    /**
     * The counter: a wall goes up between them 8 ticks into the breath. The wail Silences no one, and once it has rung
     * out the shade is voiceless.
     */
    private static void counter(GameTestHelper helper) {
        KeeningShade s = shade(helper);
        ServerPlayer p = TestPlayers.survivor(helper, NEAR);
        s.setTarget(p);
        long[] start = {-1}, walled = {-1}, voiceless = {-1};
        boolean[] hit = {false};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (start[0] < 0 && s.core().in(Step.TELL)) start[0] = now;
            if (start[0] >= 0 && walled[0] < 0 && now >= start[0] + 8) {
                wallAtX(helper, 8);
                walled[0] = now;
            }
            hit[0] |= silenced(p);
            if (voiceless[0] < 0 && s.core().in(Step.VOICELESS)) voiceless[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(walled[0] >= 0 && s.wails() == 1, "the wall went up and it wailed, phase " + s.phase());
            helper.assertTrue(!s.hasLineOfSight(p), "the wall breaks its line of sight");
            helper.assertTrue(!hit[0] && s.lastSilenced() == 0, "behind the wall the player is never Silenced, silenced " + s.lastSilenced());
            helper.assertTrue(voiceless[0] >= 0 && s.voiceless(), "a wail that silences no one leaves it voiceless, phase " + s.phase());
        });
    }

    /**
     * Failing the counter: the player stays in sight. The wail Silences them for exactly 80 ticks, counted tick by tick
     * in the player's own tick; while it lasts a snowball isn't thrown, and the shade keeps its voice.
     */
    private static void failure(GameTestHelper helper) {
        KeeningShade s = shade(helper);
        ServerPlayer p = TestPlayers.survivor(helper, NEAR);
        s.setTarget(p);
        int[] ticks = {0};
        int[] length = {-1};
        long[] ended = {-1};
        boolean[] threw = {false}, voiceless = {false};
        helper.onEachTick(() -> {
            voiceless[0] |= s.core().in(Step.VOICELESS);
            // read before the player's own tick runs the effect down, so the tick it lands counts in full
            MobEffectInstance e = p.getEffect(ModEffects.SILENCED.holder());
            if (e != null) {
                if (ticks[0] == 0) {
                    length[0] = e.getDuration();
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SNOWBALL, 16));
                    InteractionResult r = p.gameMode.useItem(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
                    threw[0] = r.consumesAction() || p.getMainHandItem().getCount() != 16;
                }
                ticks[0]++;
            } else if (ticks[0] > 0 && ended[0] < 0) {
                ended[0] = helper.getTick();
            }
            p.doTick();          // the effect runs down in the player's own tick
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0] >= 0, "the player was Silenced and it wore off, " + ticks[0] + " ticks so far, phase " + s.phase());
            helper.assertTrue(length[0] == 80, "Silenced for 80 ticks, given " + length[0]);
            helper.assertTrue(Math.abs(ticks[0] - 80) <= 1, "it lasted 80 ticks, lasted " + ticks[0]);
            helper.assertTrue(!threw[0], "Silenced, the snowball isn't thrown");
            helper.assertTrue(s.lastSilenced() == 1 && !voiceless[0], "a wail that silenced someone keeps its voice, silenced " + s.lastSilenced());
        });
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /**
     * The wail's reach, straight up: one player floats 9.5 blocks above the shade and another 10.5. The breath starts
     * at the nearer, and the wail Silences it and not the farther.
     */
    private static void radius(GameTestHelper helper) {
        openRoof(helper);
        KeeningShade s = shade(helper);
        ServerPlayer in = TestPlayers.survivor(helper, AT.add(0, 9.5, 0));
        ServerPlayer out = TestPlayers.survivor(helper, AT.add(0, 10.5, 0));
        s.setTarget(in);
        helper.succeedWhen(() -> {
            helper.assertTrue(s.wails() == 1, "it wailed, phase " + s.phase());
            helper.assertTrue(Math.abs(s.distanceTo(in) - 9.5) < 0.05 && Math.abs(s.distanceTo(out) - 10.5) < 0.05,
                    "the players floated 9.5 and 10.5 blocks off, measured " + s.distanceTo(in) + " and " + s.distanceTo(out));
            helper.assertTrue(s.hasLineOfSight(in) && s.hasLineOfSight(out), "both in its sight");
            helper.assertTrue(silenced(in), "9.5 blocks away is within the wail");
            helper.assertTrue(!silenced(out), "10.5 blocks away is out of it");
            helper.assertTrue(s.lastSilenced() == 1, "one Silenced, got " + s.lastSilenced());
        });
    }

    /**
     * Line of sight at the wail: two players within 10 blocks, one in sight and one behind a pillar. The one in sight is
     * Silenced, the hidden one isn't.
     */
    private static void sight(GameTestHelper helper) {
        for (int x = 4; x <= 8; x++) {
            for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
        }
        KeeningShade s = shade(helper);
        ServerPlayer seen = TestPlayers.survivor(helper, NEAR);
        ServerPlayer hidden = TestPlayers.survivor(helper, HIDDEN);
        s.setTarget(seen);
        helper.succeedWhen(() -> {
            helper.assertTrue(s.wails() == 1, "it wailed, phase " + s.phase());
            helper.assertTrue(s.distanceTo(hidden) < 10.0 && !s.hasLineOfSight(hidden), "the hidden player is in reach but out of sight");
            helper.assertTrue(silenced(seen), "the player in sight is Silenced");
            helper.assertTrue(!silenced(hidden), "the player behind the pillar isn't");
            helper.assertTrue(s.lastReached().equals(List.of(seen)), "its wail reached only the one in sight: " + s.lastReached());
        });
    }

    /** It wastes no breath on a target out of sight: behind a wall, no breath for 30 ticks; the wall gone, it starts within 3. */
    private static void startSight(GameTestHelper helper) {
        wallAtX(helper, 8);
        KeeningShade s = shade(helper);
        ServerPlayer p = TestPlayers.survivor(helper, NEAR);
        s.setTarget(p);
        long[] told = {-1};
        helper.onEachTick(() -> {
            if (told[0] < 0 && s.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(told[0] < 0 && !s.hasLineOfSight(p), "behind the wall it never breathes in, told at " + told[0]);
            for (int z = 0; z < 12; z++) {
                for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(8, y, z), Blocks.AIR);
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(told[0] >= 30 && told[0] <= 33, "in sight again it breathes in within 3 ticks, told at " + told[0]));
    }

    /** A fresh shade, its wait left as it spawned, takes its first breath 10 ticks in, not on the tick it appears. */
    private static void firstBreath(GameTestHelper helper) {
        KeeningShade s = helper.spawn(ModMobs.KEENING_SHADE.get(), AT);
        s.removeFreeWill();
        s.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        s.core().setLook(LookDef.BASE, true);
        ServerPlayer p = TestPlayers.survivor(helper, NEAR);
        s.setTarget(p);
        long spawned = helper.getTick();
        long[] told = {-1};
        helper.onEachTick(() -> {
            if (told[0] < 0 && s.core().in(Step.TELL)) told[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(told[0] >= 0, "it breathed in, phase " + s.phase());
            long after = told[0] - spawned;
            helper.assertTrue(after >= 9 && after <= 12, "its first breath 10 ticks after it appeared, took " + after);
        });
    }

    /** Every 200 ticks: with the player standing in sight, the second breath starts 200 ticks after the first. */
    private static void cooldown(GameTestHelper helper) {
        KeeningShade s = shade(helper);
        ServerPlayer p = TestPlayers.survivor(helper, NEAR);
        s.setTarget(p);
        long[] first = {-1}, second = {-1};
        boolean[] wasIn = {false};
        helper.onEachTick(() -> {
            boolean in = s.core().in(Step.TELL);
            if (in && !wasIn[0]) {
                if (first[0] < 0) first[0] = helper.getTick();
                else if (second[0] < 0) second[0] = helper.getTick();
            }
            wasIn[0] = in;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0] >= 0, "it breathed in twice, first at " + first[0] + ", cooldown " + s.cooldown());
            long gap = second[0] - first[0];
            helper.assertTrue(Math.abs(gap - 200) <= 1, "200 ticks from one breath to the next, took " + gap);
            helper.assertTrue(s.wails() == 1 && s.lastSilenced() == 1, "the first wail Silenced the player, wails " + s.wails());
        });
    }

    /**
     * The payoff: with only a pig to wail at, the wail Silences no one and it is voiceless for 200 ticks, counted tick by
     * tick. The pig stays in reach and sight all along, and still it never breathes in while voiceless; its next breath
     * comes 200 ticks after its voice is back.
     */
    private static void voiceless(GameTestHelper helper) {
        KeeningShade s = shade(helper);
        Pig pig = pig(helper, NEAR);
        s.setTarget(pig);
        long[] back = {-1}, next = {-1};
        int[] mute = {0};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (s.core().in(Step.VOICELESS)) {
                mute[0]++;
                if (s.getTarget() != pig) s.setTarget(pig);
            } else if (mute[0] > 0 && back[0] < 0) {
                back[0] = now;
            }
            if (back[0] >= 0 && next[0] < 0 && s.core().in(Step.TELL)) next[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(next[0] >= 0, "voiceless " + mute[0] + " ticks, voice back at " + back[0] + ", phase " + s.phase());
            helper.assertTrue(s.lastSilenced() == 0, "the wail at the pig Silenced no one");
            helper.assertTrue(Math.abs(mute[0] - 200) <= 1, "voiceless for 200 ticks, was " + mute[0]);
            helper.assertTrue(s.wails() == 1, "no wail while voiceless, wails " + s.wails());
            long gap = next[0] - back[0];
            helper.assertTrue(Math.abs(gap - 200) <= 1, "its next breath 200 ticks after its voice is back, took " + gap);
            helper.assertTrue(Math.abs(pig.getHealth() - 40.0F) < 0.01F, "the wail hurts nothing, pig " + pig.getHealth());
        });
    }

    /** Its poke: its attack on a still pig with 40 health takes exactly 3. */
    private static void melee(GameTestHelper helper) {
        KeeningShade s = shade(helper);
        Pig pig = pig(helper, new Vec3(7.0, 1, 6.0));
        helper.runAtTickTime(2, () -> {
            boolean hit = s.doHurtTarget(helper.getLevel(), pig);
            helper.assertTrue(hit, "the poke lands");
            helper.assertTrue(Math.abs(pig.getHealth() - 37.0F) < 0.01F, "3 damage: 40 -> 37, got " + pig.getHealth());
            helper.succeed();
        });
    }

    /** A listener that cancels the "silence" step: the player in sight was reached, isn't Silenced, and the shade goes voiceless. */
    private static void silenceCancel(GameTestHelper helper) {
        KeeningShade s = shade(helper);
        ServerPlayer p = TestPlayers.survivor(helper, NEAR);
        s.setTarget(p);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == s && e.step().equals("silence")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.succeedWhen(() -> {
            helper.assertTrue(s.wails() == 1 && s.voiceless(), "it wailed and went voiceless, phase " + s.phase());
            helper.assertTrue(s.lastReached().contains(p), "the wail reached the player");
            helper.assertTrue(!silenced(p) && s.lastSilenced() == 0, "a cancelled silence does nothing");
        });
    }

    /** A listener that cancels the "tell" step: it never breathes in or wails, and the player is never Silenced (40 ticks). */
    private static void tellCancel(GameTestHelper helper) {
        KeeningShade s = shade(helper);
        ServerPlayer p = TestPlayers.survivor(helper, NEAR);
        s.setTarget(p);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == s && e.step().equals("tell")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        boolean[] told = {false};
        helper.onEachTick(() -> told[0] |= !s.core().idle());
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(!told[0] && s.wails() == 0, "no breath and no wail, phase " + s.phase());
            helper.assertTrue(!silenced(p), "nobody Silenced");
            helper.succeed();
        });
    }

    /** Health 20, attack 3, armour 0, a 0.6 x 1.8 hitbox; the valley's soul fire never hurts it, nor do falls. */
    private static void stats(GameTestHelper helper) {
        KeeningShade s = helper.spawn(ModMobs.KEENING_SHADE.get(), AT);
        s.removeFreeWill();
        s.core().setLook(LookDef.BASE, true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(s.getMaxHealth() - 20.0F) < 0.01F && Math.abs(s.getHealth() - 20.0F) < 0.01F,
                    "health 20, got " + s.getHealth() + "/" + s.getMaxHealth());
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < 0.01, "attack 3");
            helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(s.getBbWidth() - 0.6F) < 0.02F && Math.abs(s.getBbHeight() - 1.8F) < 0.02F,
                    "hitbox 0.6 x 1.8, got " + s.getBbWidth() + " x " + s.getBbHeight());
            helper.assertTrue(s.fireImmune(), "soul fire never hurts it");
            helper.assertTrue(!s.causeFallDamage(10.0, 1.0F, s.damageSources().fall()), "a drifting spectre takes no fall damage");
            helper.succeed();
        });
    }

    /** Home: the soul sand valley only, a monster, spawn weight 10 in groups of 1. The Hollow Shade is its second look. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var tag = biomes.get(TeemingSpawns.tag("keening_shade"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.SOUL_SAND_VALLEY)), "its home holds the soul sand valley");
        for (var other : List.of(Biomes.WARPED_FOREST, Biomes.CRIMSON_FOREST, Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS, Biomes.PLAINS)) {
            helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(other)), "and not " + other.identifier());
        }
        RosterStats.Entry row = RosterStats.get("keening_shade");
        helper.assertTrue(row.dim() == RosterStats.Dim.NETHER, "a Nether mob");
        helper.assertTrue(ModMobs.KEENING_SHADE.get().getCategory() == MobCategory.MONSTER, "a monster");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/keening_shade")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.KEENING_SHADE.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 10, "spawn weight 10, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "groups of 1, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        List<LookDef> looks = Looks.of("keening_shade");
        helper.assertTrue(looks.stream().anyMatch(x -> x.id().equals("hollow_shade")), "the Hollow Shade is a look: " + looks);
        helper.succeed();
    }
}
