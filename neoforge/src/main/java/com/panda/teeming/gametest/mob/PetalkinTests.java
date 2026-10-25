package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.overworld.Petalkin;
import com.panda.teeming.mob.overworld.Petalkin.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Petalkin: HP 12, Atk 3, Arm 0, 0.5 x 0.9, no tell. Neutral: it ignores everyone until it is hit. A hit from something
 * makes it blink 6 blocks to a safe spot (never a drop past 4 blocks or the void, lava or a wall), re-form there for 10
 * ticks, and leave 3 decoys where it stood for 100 ticks: copies in its look that cast no shadow, make no sound and pop
 * at any hit. Cherry groves, weight 8, alone or in pairs.
 *
 * <p>Most tests stand it 2 blocks east of a still pig that "hits" it, so the blink goes 6 blocks east to the middle of
 * the arena's far side, and clear its goals so it stays where it lands. A safety test then spoils that spot.
 */
public final class PetalkinTests {
    private PetalkinTests() {}

    /** Where it stands, and where the pig that hits it stands: straight west of it. */
    private static final Vec3 AT = new Vec3(2.5, 1, 5.5);
    private static final Vec3 PIG = new Vec3(0.5, 1, 5.5);
    /** The column straight away from the pig at the full blink range: 6 blocks east. */
    private static final BlockPos FAR = new BlockPos(8, 0, 5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("petalkin_tell", PetalkinTests::tell);
        TeemingGameTests.timeLimit("petalkin_tell", 120);
        out.accept("petalkin_counter", PetalkinTests::counter);
        out.accept("petalkin_failure", PetalkinTests::failure);
        TeemingGameTests.timeLimit("petalkin_failure", 240);
        out.accept("petalkin_stats", PetalkinTests::stats);
        out.accept("petalkin_blink_range", PetalkinTests::blinkRange);
        out.accept("petalkin_decoys", PetalkinTests::decoys);
        out.accept("petalkin_decoy_length", PetalkinTests::decoyLength);
        TeemingGameTests.timeLimit("petalkin_decoy_length", 160);
        out.accept("petalkin_death_pops_decoys", PetalkinTests::deathPopsDecoys);
        out.accept("petalkin_safe_lava", PetalkinTests::safeLava);
        out.accept("petalkin_safe_drop", PetalkinTests::safeDrop);
        out.accept("petalkin_safe_wall", PetalkinTests::safeWall);
        out.accept("petalkin_no_safe_spot", PetalkinTests::noSafeSpot);
        out.accept("petalkin_peck", PetalkinTests::peck);
        out.accept("petalkin_blink_cancel", PetalkinTests::blinkCancel);
        out.accept("petalkin_home", PetalkinTests::home);
    }

    /** A Petalkin at {@link #AT} with no goals (it stays where a blink puts it), and the pig west of it. */
    private static Petalkin still(GameTestHelper helper, Pig[] pig) {
        Petalkin kin = helper.spawn(ModMobs.PETALKIN.get(), AT);
        kin.removeFreeWill();
        pig[0] = helper.spawn(EntityTypes.PIG, PIG);
        pig[0].setNoAi(true);
        return kin;
    }

    /** The pig's blow: 1 damage, from the pig. */
    private static boolean hit(GameTestHelper helper, Petalkin kin, Pig pig, float amount) {
        return kin.hurtServer(helper.getLevel(), kin.damageSources().mobAttack(pig), amount);
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /**
     * No tell, and neutral: a survival player beside it for 40 ticks is never targeted or hurt. Hit, it re-forms where
     * it lands for 10 ticks, standing still and hurting nothing, and only then turns on whoever hit it.
     */
    private static void tell(GameTestHelper helper) {
        Petalkin kin = helper.spawn(ModMobs.PETALKIN.get(), new Vec3(3.5, 1, 5.5));
        // held still until the hit (its stroll would carry it off the spot the blink is measured from)
        kin.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(3.5, 1, 7.0));
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(1.5, 1, 5.5));
        pig.setNoAi(true);
        float playerHealth = player.getHealth();
        int[] blinkTicks = {0};
        double[] moved = {0.0};
        boolean[] over = {false};
        Vec3[] last = {null};
        helper.onEachTick(() -> {
            if (kin.core().in(Step.BLINK)) {
                blinkTicks[0]++;
                if (last[0] != null) moved[0] = Math.max(moved[0], flat(kin.position(), last[0]));
                last[0] = kin.position();
            } else if (blinkTicks[0] > 0) {
                over[0] = true;
            }
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(kin.getTarget() == null, "unprovoked, it targets no one, has " + kin.getTarget());
            helper.assertTrue(player.getHealth() >= playerHealth, "unprovoked, it never pecks a player beside it");
            helper.assertTrue(kin.core().idle(), "no tell or other phase before it is hit, phase " + kin.phase());
            kin.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
            hit(helper, kin, pig, 1.0F);
            last[0] = kin.position();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the re-forming ends, " + blinkTicks[0] + " ticks so far");
            helper.assertTrue(Math.abs(blinkTicks[0] - 10) <= 1, "it re-forms for 10 ticks, took " + blinkTicks[0]);
            helper.assertTrue(moved[0] < 0.05, "it stands still while it re-forms, moved " + moved[0]);
            helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "re-forming hurts nothing");
            helper.assertTrue(kin.getTarget() == pig, "then it turns on whoever hit it, target " + kin.getTarget());
        });
    }

    /**
     * The counter: the real one is told apart (a shadow and footsteps; its decoys have neither), and a hit on it lands
     * in full: 4 damage. It blinks again from that hit, and the copies of its first blink pop for a new three.
     */
    private static void counter(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Petalkin kin = still(helper, pig);
        Pig east = helper.spawn(EntityTypes.PIG, new Vec3(10.5, 1, 5.5));
        east.setNoAi(true);
        List<Petalkin> first = new ArrayList<>();
        helper.runAtTickTime(2, () -> {
            hit(helper, kin, pig[0], 1.0F);
            first.addAll(kin.decoys());
            helper.assertTrue(first.size() == 3, "the first hit leaves 3 decoys, got " + first.size());
            helper.assertTrue(kin.getAgeScale() == 1.0F && kin.makesFootsteps(), "the real one casts a shadow and makes footsteps");
            for (Petalkin d : first) {
                helper.assertTrue(d.getAgeScale() == 0.0F, "a decoy's shadow scale is 0: it casts no shadow");
                helper.assertTrue(!d.makesFootsteps(), "a decoy makes no footsteps");
            }
        });
        // past the hurt cooldown of the first hit, the player strikes the one with a shadow, from the east
        helper.runAtTickTime(16, () -> {
            float before = kin.getHealth();
            Vec3 from = kin.position();
            helper.assertTrue(hit(helper, kin, east, 4.0F), "the hit on the real one lands");
            float lost = before - kin.getHealth();
            helper.assertTrue(Math.abs(lost - 4.0F) < 0.01F, "the real one takes the whole hit, lost " + lost);
            helper.assertTrue(Math.abs(flat(kin.position(), from) - 6.0) < 0.05, "and blinks again, 6 blocks, went " + flat(kin.position(), from));
            for (Petalkin d : first) helper.assertTrue(d.isRemoved(), "the first blink's copies popped");
            helper.assertTrue(kin.decoys().size() == 3, "3 new decoys, got " + kin.decoys().size());
            helper.succeed();
        });
    }

    /**
     * Failing the counter: three hits on the decoys each pop one and leave the real one untouched, while the real one
     * comes back and pecks for its attack, 3.
     */
    private static void failure(GameTestHelper helper) {
        Petalkin kin = helper.spawn(ModMobs.PETALKIN.get(), AT);
        Pig pig = helper.spawn(EntityTypes.PIG, PIG);
        pig.setNoAi(true);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        float[] health = {0.0F};
        helper.runAtTickTime(2, () -> {
            hit(helper, kin, pig, 1.0F);
            health[0] = kin.getHealth();
            List<Petalkin> copies = kin.decoys();
            helper.assertTrue(copies.size() == 3, "3 decoys to waste hits on, got " + copies.size());
            for (int i = 0; i < copies.size(); i++) {
                Petalkin d = copies.get(i);
                helper.assertTrue(d.hurtServer(helper.getLevel(), d.damageSources().playerAttack(player), 6.0F), "the hit on a decoy lands");
                helper.assertTrue(d.isRemoved(), "a hit pops the decoy");
                helper.assertTrue(kin.decoys().size() == copies.size() - i - 1, "only that one pops");
            }
            helper.assertTrue(Math.abs(kin.getHealth() - health[0]) < 0.01F, "hits on decoys cost the real one nothing");
        });
        helper.succeedWhen(() -> {
            float lost = pig.getMaxHealth() - pig.getHealth();
            helper.assertTrue(lost > 0.0F, "the real one comes back and pecks");
            helper.assertTrue(Math.abs(lost - 3.0F) < 0.01F, "a peck is its attack, 3; the pig lost " + lost);
            helper.assertTrue(Math.abs(kin.getHealth() - health[0]) < 0.01F, "still untouched");
        });
    }

    /** HP 12, Atk 3, Arm 0, a 0.5 x 0.9 hitbox, a creature; its two looks are Petalkin and Snowpetal. */
    private static void stats(GameTestHelper helper) {
        Petalkin kin = helper.spawn(ModMobs.PETALKIN.get(), AT);
        kin.removeFreeWill();
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(kin.getMaxHealth() - 12.0F) < 0.01F && Math.abs(kin.getHealth() - 12.0F) < 0.01F,
                    "health 12, got " + kin.getHealth() + "/" + kin.getMaxHealth());
            helper.assertTrue(Math.abs(kin.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < 0.01, "attack 3");
            helper.assertTrue(Math.abs(kin.getAttributeValue(Attributes.ARMOR)) < 0.01, "armour 0");
            helper.assertTrue(Math.abs(kin.getBbWidth() - 0.5F) < 0.02F && Math.abs(kin.getBbHeight() - 0.9F) < 0.02F,
                    "hitbox 0.5 x 0.9, got " + kin.getBbWidth() + " x " + kin.getBbHeight());
            helper.assertTrue(ModMobs.PETALKIN.get().getCategory() == MobCategory.CREATURE, "a creature");
            helper.assertTrue(!kin.vulnerable(), "no weak point");
            List<String> looks = Looks.of("petalkin").stream().map(LookDef::id).toList();
            helper.assertTrue(looks.contains("base") && looks.contains("snowpetal"), "looks base and snowpetal, got " + looks);
            helper.succeed();
        });
    }

    /**
     * A hit from the pig sends it 6 blocks straight away from the pig, onto the floor, on the tick of the hit. A hurt
     * with no one behind it (magic) is no hit: it stays.
     */
    private static void blinkRange(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Petalkin kin = still(helper, pig);
        helper.runAtTickTime(2, () -> {
            Vec3 from = kin.position();
            kin.hurtServer(helper.getLevel(), kin.damageSources().magic(), 1.0F);
            helper.assertTrue(flat(kin.position(), from) < 0.01 && kin.decoys().isEmpty(), "a hurt from no one doesn't blink it");
        });
        helper.runAtTickTime(14, () -> {
            Vec3 from = kin.position();
            hit(helper, kin, pig[0], 1.0F);
            Vec3 to = kin.position();
            helper.assertTrue(Math.abs(flat(to, from) - 6.0) < 0.05, "it blinks 6 blocks, went " + flat(to, from));
            helper.assertTrue(to.x - from.x > 5.9, "straight away from the pig (east), went " + to.subtract(from));
            helper.assertTrue(Math.abs(to.y - from.y) < 0.01, "onto the floor, y " + helper.relativeVec(to).y);
            helper.assertTrue(kin.core().in(Step.BLINK), "and re-forms there, phase " + kin.phase());
            helper.succeed();
        });
    }

    /**
     * The decoys: 3 where it stood (each within a block of the spot), in its look (Snowpetal here), the same 0.5 x 0.9
     * size, no shadow, no footsteps, never saved with the world. The real one keeps its shadow.
     */
    private static void decoys(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Petalkin kin = still(helper, pig);
        kin.core().setLook("snowpetal", true);
        helper.runAtTickTime(2, () -> {
            Vec3 from = kin.position();
            hit(helper, kin, pig[0], 1.0F);
            List<Petalkin> copies = kin.decoys();
            helper.assertTrue(copies.size() == 3, "3 decoys, got " + copies.size());
            helper.assertTrue(!kin.isDecoy() && kin.getAgeScale() == 1.0F && kin.makesFootsteps() && kin.shouldBeSaved(),
                    "the real one is no decoy: a shadow, footsteps, saved");
            for (Petalkin d : copies) {
                helper.assertTrue(d.isDecoy(), "a decoy");
                helper.assertTrue(flat(d.position(), from) <= 1.0, "standing where it stood, " + flat(d.position(), from) + " off");
                helper.assertTrue(d.look().equals("snowpetal"), "in its look, got " + d.look());
                helper.assertTrue(Math.abs(d.getBbWidth() - 0.5F) < 0.02F && Math.abs(d.getBbHeight() - 0.9F) < 0.02F,
                        "its size, got " + d.getBbWidth() + " x " + d.getBbHeight());
                helper.assertTrue(d.getAgeScale() == 0.0F, "no shadow: its shadow scale is 0");
                helper.assertTrue(!d.makesFootsteps(), "no footsteps");
                helper.assertTrue(!d.shouldBeSaved(), "never saved");
            }
            helper.succeed();
        });
    }

    /** The decoys last 100 ticks, then pop by themselves. */
    private static void decoyLength(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Petalkin kin = still(helper, pig);
        List<Petalkin> copies = new ArrayList<>();
        int[] lived = {0};
        boolean[] gone = {false};
        helper.runAtTickTime(2, () -> {
            hit(helper, kin, pig[0], 1.0F);
            copies.addAll(kin.decoys());
        });
        helper.onEachTick(() -> {
            if (copies.isEmpty() || gone[0]) return;
            if (copies.stream().anyMatch(d -> !d.isRemoved())) lived[0]++;
            else gone[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(copies.size() == 3, "3 decoys");
            helper.assertTrue(gone[0], "the decoys pop in the end, " + lived[0] + " ticks so far");
            helper.assertTrue(Math.abs(lived[0] - 100) <= 1, "the decoys last 100 ticks, lasted " + lived[0]);
            helper.assertTrue(kin.isAlive(), "the real one stays");
        });
    }

    /** When the real one dies its copies pop with it. */
    private static void deathPopsDecoys(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Petalkin kin = still(helper, pig);
        helper.runAtTickTime(2, () -> {
            hit(helper, kin, pig[0], 1.0F);
            List<Petalkin> copies = kin.decoys();
            helper.assertTrue(copies.size() == 3, "3 decoys");
            kin.kill(helper.getLevel());
            helper.assertTrue(copies.stream().allMatch(Petalkin::isRemoved), "its death pops every copy");
            helper.succeed();
        });
    }

    /** Lava where the full blink would land: it lands elsewhere, on stone, never in or on lava. */
    private static void safeLava(GameTestHelper helper) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) helper.setBlock(FAR.offset(x, 0, z), Blocks.LAVA);
        Pig[] pig = new Pig[1];
        Petalkin kin = still(helper, pig);
        helper.runAtTickTime(2, () -> {
            Vec3 from = kin.position();
            hit(helper, kin, pig[0], 1.0F);
            safeLanding(helper, kin, from, "lava");
            helper.assertTrue(!kin.isInLava(), "not in lava");
            helper.succeed();
        });
    }

    /** A pit 7 deep where the full blink would land (past its 4-block reach, as the void is): it lands on the floor. */
    private static void safeDrop(GameTestHelper helper) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y >= -6; y--) helper.setBlock(FAR.offset(x, y, z), Blocks.AIR);
        Pig[] pig = new Pig[1];
        Petalkin kin = still(helper, pig);
        helper.runAtTickTime(2, () -> {
            Vec3 from = kin.position();
            hit(helper, kin, pig[0], 1.0F);
            safeLanding(helper, kin, from, "the pit");
            helper.succeed();
        });
    }

    /** A wall 3 high where the full blink would land: it lands beside it, not in it or on it. */
    private static void safeWall(GameTestHelper helper) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 1; y <= 3; y++) helper.setBlock(FAR.offset(x, y, z), Blocks.STONE);
        Pig[] pig = new Pig[1];
        Petalkin kin = still(helper, pig);
        helper.runAtTickTime(2, () -> {
            Vec3 from = kin.position();
            hit(helper, kin, pig[0], 1.0F);
            safeLanding(helper, kin, from, "the wall");
            helper.assertTrue(helper.getLevel().noCollision(kin), "not inside a block");
            helper.succeed();
        });
    }

    /** It blinked, no further than 6, to stone on the arena floor and clear of the spoiled 3 x 3 round {@link #FAR}. */
    private static void safeLanding(GameTestHelper helper, Petalkin kin, Vec3 from, String what) {
        Vec3 to = helper.relativeVec(kin.position());
        double went = flat(kin.position(), from);
        helper.assertTrue(went > 1.9 && went < 6.05, "it still blinks, no further than 6 blocks; went " + went);
        helper.assertTrue(Math.abs(to.y - 1.0) < 0.01, "it stands on the floor, y " + to.y);
        boolean inside = to.x > FAR.getX() - 1 - 0.25 && to.x < FAR.getX() + 2 + 0.25 && to.z > FAR.getZ() - 1 - 0.25 && to.z < FAR.getZ() + 2 + 0.25;
        helper.assertTrue(!inside, "it keeps clear of " + what + ", landed at " + to);
        BlockPos under = BlockPos.containing(kin.position()).below();
        helper.assertTrue(helper.getLevel().getBlockState(under).is(Blocks.STONE), "on stone, got " + helper.getLevel().getBlockState(under));
        helper.assertTrue(kin.decoys().size() == 3, "with its 3 decoys left behind");
    }

    /** On a pillar 5 high nothing in reach is safe (every drop is past 4): it stays and takes the hit, no decoys. */
    private static void noSafeSpot(GameTestHelper helper) {
        for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.STONE);
        Petalkin kin = helper.spawn(ModMobs.PETALKIN.get(), new Vec3(5.5, 6, 5.5));
        kin.removeFreeWill();
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(3.5, 1, 5.5));
        pig.setNoAi(true);
        helper.runAtTickTime(4, () -> {
            Vec3 from = kin.position();
            float before = kin.getHealth();
            hit(helper, kin, pig, 1.0F);
            helper.assertTrue(flat(kin.position(), from) < 0.5, "it stays on the pillar, moved " + flat(kin.position(), from));
            helper.assertTrue(kin.decoys().isEmpty(), "no blink, no decoys");
            helper.assertTrue(Math.abs(before - kin.getHealth() - 1.0F) < 0.01F, "it still takes the hit");
            helper.succeed();
        });
    }

    /** A peck does its attack, 3. */
    private static void peck(GameTestHelper helper) {
        Petalkin kin = helper.spawn(ModMobs.PETALKIN.get(), new Vec3(4.5, 1, 5.5));
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(6.0, 1, 5.5));
        pig.setNoAi(true);
        helper.runAtTickTime(2, () -> kin.setTarget(pig));
        helper.succeedWhen(() -> {
            float lost = pig.getMaxHealth() - pig.getHealth();
            helper.assertTrue(lost > 0.0F, "it pecks its target");
            helper.assertTrue(Math.abs(lost - 3.0F) < 0.01F, "a peck does 3, the pig lost " + lost);
        });
    }

    /** A vetoed "blink" leaves it where it was with no decoys; a vetoed "decoys" lets it blink but leave none. */
    private static void blinkCancel(GameTestHelper helper) {
        Pig[] pig = new Pig[1];
        Petalkin kin = still(helper, pig);
        String[] veto = {"blink"};
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == kin && e.step().equals(veto[0])) e.cancel();
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        helper.runAtTickTime(2, () -> {
            Vec3 from = kin.position();
            hit(helper, kin, pig[0], 1.0F);
            helper.assertTrue(flat(kin.position(), from) < 0.5, "a vetoed blink keeps it in place");
            helper.assertTrue(kin.decoys().isEmpty() && kin.core().idle(), "and leaves no decoys");
            veto[0] = "decoys";
        });
        helper.runAtTickTime(16, () -> {
            Vec3 from = kin.position();
            hit(helper, kin, pig[0], 2.0F);
            helper.assertTrue(flat(kin.position(), from) > 1.9, "with only the decoys vetoed it still blinks");
            helper.assertTrue(kin.decoys().isEmpty(), "but leaves no decoys");
            helper.succeed();
        });
    }

    /** Its home: cherry groves (and only there by default), weight 8, alone or in pairs, a creature, in any light. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("petalkin"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.CHERRY_GROVE)), "its home holds the cherry grove");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.PLAINS)), "not the plains");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/petalkin")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.PETALKIN.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 8, "spawn weight 8, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 2,
                "alone or in pairs, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(RosterStats.get("petalkin").anyLight(), "in any light");
        helper.succeed();
    }
}
