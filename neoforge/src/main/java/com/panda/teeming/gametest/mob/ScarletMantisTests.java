package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TeemingGameTests;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.mob.nether.ScarletMantis;
import com.panda.teeming.mob.nether.ScarletMantis.Step;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Scarlet Mantis: in a fight, about every 60 ticks (52 to 68, never the same beat for long) a 12-tick tell leads into a
 * 24-tick parry stance in which it stands still and slashes nothing; a melee blow in the stance does nothing and
 * staggers the attacker (Slowness III and Weakness V for 30 ticks, and the mantis refuses that attacker's blows for the
 * 30 ticks); shots still land. Up close it slashes for 6. Every number is checked against a plain value.
 *
 * <p>Most mantises here have their goals cleared, a follow range of 1 and their own stance cycle set 1000 ticks off, or
 * no AI at all, so they act only on what the test gives them; they leave with the test. Shots are real arrows without
 * gravity, fired level along the arena, and are removed when the test ends.
 */
public final class ScarletMantisTests {
    private ScarletMantisTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("scarlet_mantis_tell", ScarletMantisTests::tell);
        out.accept("scarlet_mantis_counter", ScarletMantisTests::shotsLand);
        out.accept("scarlet_mantis_failure", ScarletMantisTests::blowInStance);
        out.accept("scarlet_mantis_stance_length", ScarletMantisTests::stanceLength);
        out.accept("scarlet_mantis_cycle", ScarletMantisTests::cycle);
        TeemingGameTests.timeLimit("scarlet_mantis_cycle", 460);
        out.accept("scarlet_mantis_no_fight", ScarletMantisTests::noFight);
        out.accept("scarlet_mantis_stagger_effects", ScarletMantisTests::staggerEffects);
        out.accept("scarlet_mantis_stagger_length", ScarletMantisTests::staggerLength);
        out.accept("scarlet_mantis_staggered_mob", ScarletMantisTests::staggeredMob);
        out.accept("scarlet_mantis_blow_in_tell", ScarletMantisTests::blowInTell);
        out.accept("scarlet_mantis_blow_between", ScarletMantisTests::blowBetween);
        out.accept("scarlet_mantis_parry_cancel", ScarletMantisTests::parryCancel);
        out.accept("scarlet_mantis_glow", ScarletMantisTests::glow);
        out.accept("scarlet_mantis_slash", ScarletMantisTests::slash);
        out.accept("scarlet_mantis_stats", ScarletMantisTests::stats);
        out.accept("scarlet_mantis_home", ScarletMantisTests::home);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A mantis that acts only on what the test tells it: no goals, a follow range of 1, its own stance far off. */
    private static ScarletMantis mantis(GameTestHelper helper, Vec3 at) {
        ScarletMantis m = helper.spawn(ModMobs.SCARLET_MANTIS.get(), at);
        m.core().setLook("base", true);
        m.removeFreeWill();
        m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);   // 0 would mean no limit at all
        m.stanceIn(1000);
        TestPlayers.whenDone(helper, m::discard);
        return m;
    }

    /** A still mantis: no AI (so no stance cycle of its own), no gravity, so it stays exactly where it is put. */
    private static ScarletMantis still(GameTestHelper helper, Vec3 at) {
        ScarletMantis m = mantis(helper, at);
        m.setNoAi(true);
        m.setNoGravity(true);
        return m;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawn(EntityTypes.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    /** A still husk: a mob with an attack of its own (3) that never burns in daylight. */
    private static Husk husk(GameTestHelper helper, Vec3 at) {
        Husk husk = helper.spawn(EntityTypes.HUSK, at);
        husk.setNoAi(true);
        husk.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);   // bare-handed: its own attack only
        TestPlayers.whenDone(helper, husk::discard);
        return husk;
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

    private static float lost(LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    /** True if {@code e} has {@code effect} at exactly {@code amplifier} with exactly {@code ticks} left. */
    private static boolean has(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier, int ticks) {
        MobEffectInstance i = e.getEffect(effect);
        return i != null && i.getAmplifier() == amplifier && i.getDuration() == ticks;
    }

    private static String effect(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance i = e.getEffect(effect);
        return i == null ? "none" : "amplifier " + i.getAmplifier() + " for " + i.getDuration();
    }

    // ---- the tell --------------------------------------------------------------------------------------------------

    /**
     * The tell: in a fight, the scythes rise for exactly 12 ticks, then the stance starts; through the tell it neither
     * slashes nor moves, though the pig it hunts stands in reach.
     */
    private static void tell(GameTestHelper helper) {
        ScarletMantis m = helper.spawn(ModMobs.SCARLET_MANTIS.get(), new Vec3(5.5, 1, 5.5));
        m.core().setLook("base", true);
        m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(2.0);   // the pig only: no survivor from another arena
        TestPlayers.whenDone(helper, m::discard);
        Pig pig = pig(helper, new Vec3(5.5, 1, 6.8));
        m.setTarget(pig);
        m.stanceIn(26);                                               // its tell starts 12 ticks before: 14 ticks in
        int[] tell = {0};
        float[] pigAtTell = {-1.0F};
        Vec3[] posAtTell = {null};
        boolean[] quiet = {true}, stanceNext = {false};
        Step[] last = {Step.IDLE};
        helper.onEachTick(() -> {
            Step now = (Step) m.phase();
            if (now == Step.TELL) {
                if (last[0] != Step.TELL) {
                    pigAtTell[0] = pig.getHealth();
                    posAtTell[0] = m.position();
                }
                tell[0]++;
                if (pig.getHealth() < pigAtTell[0]) quiet[0] = false;
                if (Math.hypot(m.getX() - posAtTell[0].x, m.getZ() - posAtTell[0].z) > 0.01) quiet[0] = false;
            } else if (last[0] == Step.TELL) {
                stanceNext[0] = now == Step.STANCE;
            }
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tell[0] > 0 && last[0] != Step.TELL, "the tell ends, " + tell[0] + " ticks so far");
            helper.assertTrue(tell[0] == 12, "the tell holds 12 ticks, saw " + tell[0]);
            helper.assertTrue(stanceNext[0], "the stance follows it");
            helper.assertTrue(quiet[0], "in the tell it neither slashes nor moves");
        });
    }

    /**
     * The stance holds exactly 24 ticks, in which it neither slashes nor moves; then it is back at rest. It comes from its
     * own tell, so every tick of it is counted from the entity's own tick.
     */
    private static void stanceLength(GameTestHelper helper) {
        ScarletMantis m = helper.spawn(ModMobs.SCARLET_MANTIS.get(), new Vec3(5.5, 1, 5.5));
        m.core().setLook("base", true);
        m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(2.0);
        TestPlayers.whenDone(helper, m::discard);
        Pig pig = pig(helper, new Vec3(5.5, 1, 6.8));
        m.setTarget(pig);
        m.stanceIn(16);
        int[] stance = {0};
        float[] pigAtStance = {-1.0F};
        Vec3[] posAtStance = {null};
        boolean[] quiet = {true}, over = {false};
        helper.onEachTick(() -> {
            if (m.core().in(Step.STANCE)) {
                if (posAtStance[0] == null) {
                    pigAtStance[0] = pig.getHealth();
                    posAtStance[0] = m.position();
                }
                stance[0]++;
                if (pig.getHealth() < pigAtStance[0]) quiet[0] = false;
                if (Math.hypot(m.getX() - posAtStance[0].x, m.getZ() - posAtStance[0].z) > 0.01) quiet[0] = false;
            } else if (stance[0] > 0) {
                over[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(over[0], "the stance ends, " + stance[0] + " ticks so far");
            helper.assertTrue(stance[0] == 24, "the stance holds 24 ticks, saw " + stance[0]);
            helper.assertTrue(quiet[0], "in the stance it neither slashes nor moves");
            helper.assertTrue(m.core().idle(), "then it is back at rest, phase " + m.phase());
        });
    }

    // ---- the counter and the failure -------------------------------------------------------------------------------

    /** The counter: shots still land in the stance. An arrow strikes it home, and its shooter is not staggered. */
    private static void shotsLand(GameTestHelper helper) {
        ScarletMantis m = still(helper, new Vec3(5.5, 1, 7.5));
        Pig shooter = pig(helper, new Vec3(5.5, 1, 3.0));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.stance(), "the stance starts");
            arrow(helper, new Vec3(5.5, 1.6, 4.5), new Vec3(0, 0, 1), shooter);
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(m.core().in(Step.STANCE), "still in the stance, phase " + m.phase());
            helper.assertTrue(lost(m) > 0.5F, "the arrow struck home, it lost " + lost(m));
            helper.assertTrue(m.parries() == 0 && !m.staggered(shooter), "nothing was caught and the shooter is not staggered");
            helper.assertTrue(shooter.getEffect(MobEffects.SLOWNESS) == null, "the shooter is not slowed");
            helper.succeed();
        });
    }

    /**
     * The failure: a 10 blow in the stance does nothing, and the player who struck is staggered: Slowness III for 30
     * ticks, and no attacks: a bare-handed swing at a pig lands nothing, where the same swing by a player who never
     * struck the mantis hurts it.
     */
    private static void blowInStance(GameTestHelper helper) {
        ScarletMantis m = still(helper, new Vec3(5.5, 1, 5.5));
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(4.3, 1, 5.5));
        ServerPlayer other = TestPlayers.survivor(helper, new Vec3(8.5, 1, 8.5));
        Pig pig = pig(helper, new Vec3(3.0, 1, 4.5));
        Pig control = pig(helper, new Vec3(8.5, 1, 9.7));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.stance(), "the stance starts");
            boolean hurt = m.hurtServer(helper.getLevel(), m.damageSources().playerAttack(player), 10.0F);
            helper.assertTrue(!hurt && lost(m) < 0.001F, "the blow does nothing, it lost " + lost(m));
            helper.assertTrue(m.parries() == 1 && m.staggered(player), "the blow was caught and the player is staggered");
            helper.assertTrue(has(player, MobEffects.SLOWNESS, 2, 30), "Slowness III for 30 ticks, got " + effect(player, MobEffects.SLOWNESS));
            player.attack(pig);
            other.attack(control);
            helper.assertTrue(lost(pig) < 0.001F, "the staggered player's swing lands nothing, the pig lost " + lost(pig));
            helper.assertTrue(lost(control) > 0.1F, "the same swing by an unstaggered player hurts, the pig lost " + lost(control));
            helper.succeed();
        });
    }

    /** The stagger is Slowness III (amplifier 2) and Weakness V (amplifier 4), both for 30 ticks, on a mob attacker too. */
    private static void staggerEffects(GameTestHelper helper) {
        ScarletMantis m = still(helper, new Vec3(5.5, 1, 5.5));
        Husk husk = husk(helper, new Vec3(5.5, 1, 4.3));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.stance(), "the stance starts");
            m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(husk), 3.0F);
            helper.assertTrue(lost(m) < 0.001F, "the husk's blow does nothing, it lost " + lost(m));
            helper.assertTrue(has(husk, MobEffects.SLOWNESS, 2, 30), "Slowness III for 30 ticks, got " + effect(husk, MobEffects.SLOWNESS));
            helper.assertTrue(has(husk, MobEffects.WEAKNESS, 4, 30), "Weakness V for 30 ticks, got " + effect(husk, MobEffects.WEAKNESS));
            helper.assertTrue(Math.abs(husk.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.001,
                    "its attack is floored at 0, got " + husk.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.succeed();
        });
    }

    /**
     * No attacks for 30 ticks: the mantis refuses the staggered husk's blows 29 ticks after the parry (out of the
     * stance by then), and takes them 30 ticks after: 3 less armour 2 is 2.94.
     */
    private static void staggerLength(GameTestHelper helper) {
        ScarletMantis m = still(helper, new Vec3(5.5, 1, 5.5));
        Husk husk = husk(helper, new Vec3(5.5, 1, 4.3));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.stance(), "the stance starts");
            m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(husk), 3.0F);
            helper.assertTrue(m.staggered(husk), "the husk is staggered");
        });
        helper.runAtTickTime(2 + 29, () -> {
            helper.assertTrue(m.core().idle(), "the stance is over, phase " + m.phase());
            helper.assertTrue(m.staggered(husk), "29 ticks on, the husk is still staggered");
            boolean hurt = m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(husk), 3.0F);
            helper.assertTrue(!hurt && lost(m) < 0.001F, "and its blow does nothing, the mantis lost " + lost(m));
        });
        helper.runAtTickTime(2 + 30, () -> {
            helper.assertTrue(!m.staggered(husk), "30 ticks on, the stagger is over");
            boolean hurt = m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(husk), 3.0F);
            helper.assertTrue(hurt && Math.abs(lost(m) - 2.94F) < 0.01F, "its blow lands: 3 less armour 2 is 2.94, it lost " + lost(m));
            helper.succeed();
        });
    }

    /** A staggered mob attacks nothing else either: the husk's blow on a pig takes nothing; an unstaggered husk's takes 3. */
    private static void staggeredMob(GameTestHelper helper) {
        ScarletMantis m = still(helper, new Vec3(3.5, 1, 5.5));
        Husk husk = husk(helper, new Vec3(3.5, 1, 4.3));
        Husk fresh = husk(helper, new Vec3(8.5, 1, 4.3));
        Pig pig = pig(helper, new Vec3(3.5, 1, 3.0));
        Pig control = pig(helper, new Vec3(8.5, 1, 3.0));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.stance(), "the stance starts");
            m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(husk), 3.0F);
            husk.doHurtTarget(helper.getLevel(), pig);
            fresh.doHurtTarget(helper.getLevel(), control);
            helper.assertTrue(lost(pig) < 0.001F, "the staggered husk's blow takes nothing, the pig lost " + lost(pig));
            helper.assertTrue(Math.abs(lost(control) - 3.0F) < 0.01F, "an unstaggered husk's takes 3, the pig lost " + lost(control));
            helper.succeed();
        });
    }

    /** A blow in the tell, before the stance, lands: 10 less armour 2 is 9.84, and nobody is staggered. */
    private static void blowInTell(GameTestHelper helper) {
        ScarletMantis m = still(helper, new Vec3(5.5, 1, 5.5));
        Husk husk = husk(helper, new Vec3(5.5, 1, 4.3));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.tell(), "the tell starts");
            boolean hurt = m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(husk), 10.0F);
            helper.assertTrue(hurt && Math.abs(lost(m) - 9.84F) < 0.01F, "the blow lands for 9.84, it lost " + lost(m));
            helper.assertTrue(!m.staggered(husk) && husk.getEffect(MobEffects.SLOWNESS) == null, "the husk is not staggered");
            helper.succeed();
        });
    }

    /** The same blow at rest lands whole (9.84 after armour 2) and staggers nobody. */
    private static void blowBetween(GameTestHelper helper) {
        ScarletMantis m = still(helper, new Vec3(5.5, 1, 5.5));
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(4.3, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            boolean hurt = m.hurtServer(helper.getLevel(), m.damageSources().playerAttack(player), 10.0F);
            helper.assertTrue(hurt && Math.abs(lost(m) - 9.84F) < 0.01F, "a 10 blow at rest takes 9.84 after armour 2, it lost " + lost(m));
            helper.assertTrue(!m.staggered(player) && player.getEffect(MobEffects.SLOWNESS) == null, "nobody is staggered");
            helper.succeed();
        });
    }

    /** Packs can veto the parry: with the "parry" step cancelled, the blow lands in the stance and staggers nobody. */
    private static void parryCancel(GameTestHelper helper) {
        ScarletMantis m = still(helper, new Vec3(5.5, 1, 5.5));
        Husk husk = husk(helper, new Vec3(5.5, 1, 4.3));
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == m && e.step().equals("parry")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.stance(), "the stance starts");
            m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(husk), 10.0F);
            helper.assertTrue(Math.abs(lost(m) - 9.84F) < 0.01F, "the vetoed parry let the blow land, it lost " + lost(m));
            helper.assertTrue(!m.staggered(husk) && m.parries() == 0, "nobody is staggered");
            helper.succeed();
        });
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    /**
     * In a fight it takes the stance about every 60 ticks: five gaps between stance starts, each 52 to 68 ticks, not all
     * the same; each stance comes 12 ticks after a tell and lasts 24.
     */
    private static void cycle(GameTestHelper helper) {
        ScarletMantis m = mantis(helper, new Vec3(5.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.5, 1, 7.5));
        m.setTarget(pig);
        m.stanceIn(20);
        int[] tick = {0};
        List<Integer> stanceStarts = new ArrayList<>(), tellStarts = new ArrayList<>(), stanceLengths = new ArrayList<>();
        Step[] last = {Step.IDLE};
        int[] run = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (m.getTarget() != pig) m.setTarget(pig);            // stay in the fight whatever its target goals do
            Step now = (Step) m.phase();
            if (now != last[0]) {
                if (now == Step.STANCE) stanceStarts.add(tick[0]);
                if (now == Step.TELL) tellStarts.add(tick[0]);
                if (last[0] == Step.STANCE) stanceLengths.add(run[0]);
                run[0] = 0;
            }
            run[0]++;
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stanceStarts.size() >= 6 && stanceLengths.size() >= 6, "six stances so far: " + stanceStarts);
            List<Integer> gaps = new ArrayList<>();
            for (int i = 1; i < 6; i++) gaps.add(stanceStarts.get(i) - stanceStarts.get(i - 1));
            for (int gap : gaps) helper.assertTrue(gap >= 52 && gap <= 68, "every gap is 52 to 68 ticks: " + gaps);
            helper.assertTrue(gaps.stream().distinct().count() > 1, "the beat varies: " + gaps);
            for (int i = 0; i < 6; i++) {
                helper.assertTrue(stanceStarts.get(i) - tellStarts.get(i) == 12, "each stance comes 12 ticks after a tell: " + tellStarts + " " + stanceStarts);
                helper.assertTrue(stanceLengths.get(i) == 24, "each stance lasts 24 ticks: " + stanceLengths);
            }
        });
    }

    /** Out of a fight it never takes the stance: with no foe, nothing for 80 ticks. */
    private static void noFight(GameTestHelper helper) {
        ScarletMantis m = mantis(helper, new Vec3(5.5, 1, 5.5));
        m.stanceIn(5);
        boolean[] moved = {false};
        helper.onEachTick(() -> {
            if (m.getTarget() != null) m.setTarget(null);
            if (!m.core().idle()) moved[0] = true;
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(!moved[0], "no tell and no stance without a foe");
            helper.succeed();
        });
    }

    // ---- the look --------------------------------------------------------------------------------------------------

    /** Its glow follows the phase: dark at rest, red glints in the tell, blades and eyes lit in the stance. */
    private static void glow(GameTestHelper helper) {
        ScarletMantis m = still(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(2, () -> helper.assertTrue(m.core().stage() == 0, "dark at rest, stage " + m.core().stage()));
        helper.runAtTickTime(3, () -> helper.assertTrue(m.tell(), "the tell starts"));
        helper.runAtTickTime(5, () -> helper.assertTrue(m.core().stage() == 1, "glints in the tell, stage " + m.core().stage()));
        helper.runAtTickTime(20, () -> helper.assertTrue(m.core().in(Step.STANCE) && m.core().stage() == 2,
                "lit in the stance, phase " + m.phase() + " stage " + m.core().stage()));
        helper.runAtTickTime(42, () -> {
            helper.assertTrue(m.core().idle() && m.core().stage() == 0, "dark again at rest, stage " + m.core().stage());
            helper.assertTrue(!m.vulnerable(), "it has no weak point: nothing on it is ever gold");
            helper.succeed();
        });
    }

    // ---- the slash and the row -------------------------------------------------------------------------------------

    /** Its slash is its attack: a still pig loses 6. */
    private static void slash(GameTestHelper helper) {
        ScarletMantis m = mantis(helper, new Vec3(4.5, 1, 5.5));
        Pig pig = pig(helper, new Vec3(5.6, 1, 5.5));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(m.doHurtTarget(helper.getLevel(), pig), "the slash lands");
            helper.assertTrue(Math.abs(lost(pig) - 6.0F) < 0.01F, "a slash does 6, lost " + lost(pig));
            helper.succeed();
        });
    }

    /** Health 28, attack 6, armour 2, a 0.7 x 1.9 hitbox; a monster; three glow stages; two looks. */
    private static void stats(GameTestHelper helper) {
        ScarletMantis m = mantis(helper, new Vec3(5.5, 1, 5.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(Math.abs(m.getMaxHealth() - 28.0F) < 0.01F && Math.abs(m.getHealth() - 28.0F) < 0.01F,
                    "health 28, got " + m.getHealth() + "/" + m.getMaxHealth());
            helper.assertTrue(Math.abs(m.getAttributeValue(Attributes.ATTACK_DAMAGE) - 6.0) < 0.01, "attack 6");
            helper.assertTrue(Math.abs(m.getAttributeValue(Attributes.ARMOR) - 2.0) < 0.01, "armour 2");
            helper.assertTrue(Math.abs(m.getBbWidth() - 0.7F) < 0.02F && Math.abs(m.getBbHeight() - 1.9F) < 0.02F,
                    "hitbox 0.7 x 1.9, got " + m.getBbWidth() + " x " + m.getBbHeight());
            helper.assertTrue(m.getType().getCategory() == MobCategory.MONSTER, "a monster");
            helper.assertTrue(m.visualStages() == 3, "three glow stages");
            List<String> looks = Looks.of("scarlet_mantis").stream().map(LookDef::id).sorted().toList();
            helper.assertTrue(looks.equals(List.of("ashen_mantis", "base")), "looks base and ashen_mantis, got " + looks);
            helper.succeed();
        });
    }

    /** Home: the crimson forest; spawn weight 10, alone (groups of 1). */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("scarlet_mantis"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.CRIMSON_FOREST)), "its home holds the crimson forest");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/scarlet_mantis")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.SCARLET_MANTIS.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 10, "spawn weight 10, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.succeed();
    }
}
