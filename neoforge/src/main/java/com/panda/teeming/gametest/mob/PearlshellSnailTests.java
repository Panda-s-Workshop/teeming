package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.PearlshellSnail;
import com.panda.teeming.mob.end.PearlshellSnail.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Pearlshell Snail: a creature, HP 16, Atk 0, Arm 6, 0.6 x 0.5, no tell. A chorus fruit used on it starts a pearl
 * growing over 6000 ticks, one pearl at a time; shears on a snail with a grown pearl take exactly 1 ender pearl. Hurt,
 * it hides in its shell for 60 ticks, taking x0.2. It never attacks. Killed, it gives back its pearl, or the fruit.
 * Small End islands, weight 5, groups of 1-3; Purpur Snail. Expected values are written out as numbers.
 *
 * <p>Snails have their goals cleared (no wandering) unless a test needs them. Nothing a snail does reaches past its own
 * arena: it reacts only to what hurts it or is used on it.
 */
public final class PearlshellSnailTests {
    private PearlshellSnailTests() {}

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("pearlshell_snail_tell", PearlshellSnailTests::noTellNeverAttacks);
        out.accept("pearlshell_snail_counter", PearlshellSnailTests::feedWaitShear);
        out.accept("pearlshell_snail_failure", PearlshellSnailTests::hitShutsIt);
        out.accept("pearlshell_snail_grow_ticks", PearlshellSnailTests::growTicks);
        out.accept("pearlshell_snail_one_pearl", PearlshellSnailTests::onePearl);
        out.accept("pearlshell_snail_rooted", PearlshellSnailTests::rootedWhileShut);
        out.accept("pearlshell_snail_gives_back", PearlshellSnailTests::givesBack);
        out.accept("pearlshell_snail_veto", PearlshellSnailTests::veto);
        out.accept("pearlshell_snail_save_load", PearlshellSnailTests::saveLoad);
        out.accept("pearlshell_snail_stats", PearlshellSnailTests::stats);
        out.accept("pearlshell_snail_spawns", PearlshellSnailTests::spawns);
    }

    // ---- set-up ----------------------------------------------------------------------------------------------------

    /** A snail of {@code look} at (x, 1, z) with its goals cleared, removed when the test ends. */
    private static PearlshellSnail snail(GameTestHelper helper, double x, double z, String look) {
        PearlshellSnail s = helper.spawn(ModMobs.PEARLSHELL_SNAIL.get(), new Vec3(x, 1, z));
        s.core().setLook(look, true);
        s.removeFreeWill();
        TestPlayers.whenDone(helper, s::discard);
        return s;
    }

    private static PearlshellSnail snail(GameTestHelper helper, double x, double z) {
        return snail(helper, x, z, "base");
    }

    private static ServerPlayer holding(GameTestHelper helper, Vec3 at, ItemStack stack) {
        ServerPlayer p = TestPlayers.survivor(helper, at);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return p;
    }

    private static InteractionResult use(PearlshellSnail s, ServerPlayer p) {
        return s.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    /** A blow of {@code amount} that skips armour (magic), so what it takes is the amount itself. */
    private static boolean zap(GameTestHelper helper, PearlshellSnail s, float amount) {
        return s.hurtServer(helper.getLevel(), s.damageSources().magic(), amount);
    }

    /** Items of {@code which} lying within {@code reach} of {@code near}, counted by the item. */
    private static int lying(GameTestHelper helper, Item which, Vec3 near, double reach) {
        int n = 0;
        AABB box = new AABB(near, near).inflate(reach);
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, i -> i.isAlive() && i.getItem().is(which))) {
            n += item.getItem().getCount();
        }
        return n;
    }

    /** Items of {@code which} lying anywhere in this arena. */
    private static int lying(GameTestHelper helper, Item which) {
        int n = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), i -> i.isAlive() && i.getItem().is(which))) {
            n += item.getItem().getCount();
        }
        return n;
    }

    /** Notes every mechanic step the given snails post, for the rest of the test. */
    private static List<String> steps(GameTestHelper helper, PearlshellSnail... snails) {
        List<String> seen = new ArrayList<>();
        Set<PearlshellSnail> mine = Set.of(snails);
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() instanceof PearlshellSnail s && mine.contains(s)) seen.add(e.step());
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        return seen;
    }

    // ---- the tell, the counter, failing it -------------------------------------------------------------------------

    /**
     * No tell, because it never attacks: a survival player beside it hits it for 4 (armour 6 keeps 16 %: 3.36 lands) and
     * it shuts its shell. For the next 90 ticks it picks no target, the player loses nothing, and it posts no step but
     * "hide". Its attack is 0, and after the 60 ticks it is out again.
     */
    private static void noTellNeverAttacks(GameTestHelper helper) {
        PearlshellSnail s = helper.spawn(ModMobs.PEARLSHELL_SNAIL.get(), new Vec3(5.5, 1, 5.5));   // its goals on
        TestPlayers.whenDone(helper, s::discard);
        ServerPlayer p = TestPlayers.survivor(helper, new Vec3(4.5, 1, 5.5));
        List<String> seen = steps(helper, s);
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 3) {
                float before = s.getHealth();
                helper.assertTrue(s.hurtServer(helper.getLevel(), s.damageSources().playerAttack(p), 4.0F), "the blow lands");
                float lost = before - s.getHealth();
                helper.assertTrue(Math.abs(lost - 3.36F) < 0.01F, "armour 6 takes 16 % of a 4 hit, lost " + lost);
                helper.assertTrue(s.hidden(), "and it shuts its shell, phase " + s.phase());
            }
            if (t > 3) {
                helper.assertTrue(s.getTarget() == null, "it picks no target");
                helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "the player loses nothing, health " + p.getHealth());
            }
            if (t == 93) {
                helper.assertTrue(seen.equals(List.of("hide")), "it posted the hide and nothing else: " + seen);
                helper.assertTrue(Math.abs(s.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.01, "attack 0");
                helper.assertTrue(s.core().idle(), "it is out of its shell again, phase " + s.phase());
                helper.succeed();
            }
        });
    }

    /**
     * The counter: a survival player feeds it a chorus fruit (one of two is eaten), and a pearl starts growing (stage 1).
     * Shears do nothing while it grows, and cost nothing, even a tick short of grown (5999). Grown (6000, stage 4), the
     * shears take exactly 1 ender pearl and lose 1 durability, and the mouth is empty again (stage 0). Shears again do
     * nothing.
     */
    private static void feedWaitShear(GameTestHelper helper) {
        PearlshellSnail s = snail(helper, 5.5, 5.5);
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 3.5), new ItemStack(Items.CHORUS_FRUIT, 2));
        Vec3 at = s.position();
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 3) {
                InteractionResult fed = use(s, p);
                helper.assertTrue(fed == InteractionResult.SUCCESS_SERVER, "the fruit is taken, got " + fed);
                helper.assertTrue(p.getMainHandItem().getCount() == 1, "one fruit eaten, " + p.getMainHandItem().getCount() + " left");
                helper.assertTrue(s.growth() == 0 && s.core().stage() == 1 && s.core().in(Step.FEED),
                        "a pearl starts at stage 1 while it nibbles, growth " + s.growth() + ", stage " + s.core().stage() + ", phase " + s.phase());
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            }
            if (t == 30) {
                s.setGrowth(5999);
                InteractionResult early = use(s, p);
                helper.assertTrue(!early.consumesAction(), "shears do nothing a tick short of grown, got " + early);
                helper.assertTrue(p.getMainHandItem().getDamageValue() == 0 && lying(helper, Items.ENDER_PEARL) == 0, "and cost nothing");
                helper.assertTrue(s.core().stage() == 3 && !s.pearlGrown(), "the pearl shows stage 3, " + s.core().stage());
                s.advanceGrowth(1);
                helper.assertTrue(s.pearlGrown() && s.core().stage() == 4, "a tick later it is grown, stage " + s.core().stage());
                InteractionResult shorn = use(s, p);
                helper.assertTrue(shorn == InteractionResult.SUCCESS_SERVER, "the shears take the grown pearl, got " + shorn);
                helper.assertTrue(p.getMainHandItem().getDamageValue() == 1, "the shears lose 1, damage " + p.getMainHandItem().getDamageValue());
                helper.assertTrue(!s.hasPearl() && s.core().stage() == 0 && s.core().in(Step.HARVEST),
                        "the mouth is empty, growth " + s.growth() + ", stage " + s.core().stage());
                InteractionResult again = use(s, p);
                helper.assertTrue(!again.consumesAction() && p.getMainHandItem().getDamageValue() == 1, "shears again do nothing, got " + again);
            }
            if (t == 35) {
                helper.assertTrue(lying(helper, Items.ENDER_PEARL, at, 2.0) == 1, "exactly 1 ender pearl, got " + lying(helper, Items.ENDER_PEARL, at, 2.0));
                helper.assertTrue(lying(helper, Items.ENDER_PEARL) == 1, "and no other in the arena");
                helper.succeed();
            }
        });
    }

    /**
     * Failing it: a blow shuts it in its shell for exactly 60 ticks, counted tick by tick. The opening blow lands in full
     * (5 of 5); one while it is shut does x0.2 (1 of 5) and doesn't hold it in longer. Shut, it won't give up its grown
     * pearl (the shears do nothing and cost nothing) and the other snail, shut too, won't take a fruit. Once out, the
     * shears work.
     */
    private static void hitShutsIt(GameTestHelper helper) {
        PearlshellSnail grown = snail(helper, 4.5, 5.5);
        PearlshellSnail empty = snail(helper, 7.5, 5.5, "purpur_snail");
        grown.setGrowth(6000);
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 3.5), new ItemStack(Items.SHEARS));
        int[] shut = {0, 0};
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 5) {
                float before = grown.getHealth();
                helper.assertTrue(zap(helper, grown, 5.0F) && zap(helper, empty, 5.0F), "both blows land");
                helper.assertTrue(Math.abs(before - grown.getHealth() - 5.0F) < 0.01F, "the opening blow lands in full, lost " + (before - grown.getHealth()));
                helper.assertTrue(grown.hidden() && empty.hidden(), "both shut their shells");
            }
            if (t == 15) {
                float before = grown.getHealth();
                grown.setInvulnerableTime(0);
                helper.assertTrue(zap(helper, grown, 5.0F), "a blow on the shut shell lands");
                float lost = before - grown.getHealth();
                helper.assertTrue(Math.abs(lost - 1.0F) < 0.01F, "and does x0.2: 1 of 5, lost " + lost);
                InteractionResult shorn = use(grown, p);
                helper.assertTrue(!shorn.consumesAction() && grown.pearlGrown(), "shut, it keeps its pearl, got " + shorn);
                helper.assertTrue(p.getMainHandItem().getDamageValue() == 0, "and the shears lose nothing");
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHORUS_FRUIT));
                use(empty, p);
                helper.assertTrue(!empty.hasPearl() && p.getMainHandItem().getCount() == 1, "shut, it won't take a fruit");
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            }
            if (grown.hidden()) shut[0]++;
            if (empty.hidden()) shut[1]++;
            if (t == 70) {
                helper.assertTrue(shut[0] == 60 && shut[1] == 60, "shut for 60 ticks, counted " + shut[0] + " and " + shut[1]);
                helper.assertTrue(grown.core().idle() && empty.core().idle(), "then out again");
                InteractionResult shorn = use(grown, p);
                helper.assertTrue(shorn == InteractionResult.SUCCESS_SERVER && !grown.hasPearl(), "out, the shears take the pearl, got " + shorn);
            }
            if (t == 74) {
                helper.assertTrue(lying(helper, Items.ENDER_PEARL) == 1, "1 ender pearl, got " + lying(helper, Items.ENDER_PEARL));
                helper.succeed();
            }
        });
    }

    // ---- the numbers -------------------------------------------------------------------------------------------------

    /**
     * Growth runs on its own: 40 ticks after the feed the counter reads 40. The stages turn at a third and two thirds
     * (1999 is stage 1, 2000 stage 2, 3999 stage 2, 4000 stage 3), and from 5999 one ordinary tick later the pearl is grown
     * (6000, stage 4), where it stays: 20 ticks on it still reads 6000.
     */
    private static void growTicks(GameTestHelper helper) {
        PearlshellSnail s = snail(helper, 5.5, 5.5);
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 3.5), new ItemStack(Items.CHORUS_FRUIT));
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 3) helper.assertTrue(s.feed(p, p.getMainHandItem()) && s.growth() == 0, "fed, growth " + s.growth());
            if (t == 43) {
                helper.assertTrue(s.growth() == 40, "40 ticks on it has grown 40, " + s.growth());
                int[][] marks = {{1999, 1}, {2000, 2}, {3999, 2}, {4000, 3}};
                for (int[] m : marks) {
                    s.setGrowth(m[0] - 1);
                    s.advanceGrowth(1);
                    helper.assertTrue(s.growth() == m[0] && s.core().stage() == m[1], "at " + m[0] + " stage " + m[1] + ", got " + s.core().stage());
                }
                s.setGrowth(5999);
                helper.assertTrue(!s.pearlGrown(), "5999 is not grown");
            }
            if (t == 45) helper.assertTrue(s.pearlGrown() && s.growth() == 6000 && s.core().stage() == 4, "a tick later it is, " + s.growth());
            if (t == 65) {
                helper.assertTrue(s.growth() == 6000 && s.pearlGrown(), "and stays grown, " + s.growth());
                helper.succeed();
            }
        });
    }

    /**
     * One pearl at a time: a second fruit isn't taken while the first pearl grows, nor once it is grown (the fruit stays
     * in hand, the growth is unchanged). Once the shears take it, a fruit starts the next.
     */
    private static void onePearl(GameTestHelper helper) {
        PearlshellSnail s = snail(helper, 5.5, 5.5);
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 3.5), new ItemStack(Items.CHORUS_FRUIT, 3));
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 3) use(s, p);
            if (t == 30) {
                helper.assertTrue(s.hasPearl() && p.getMainHandItem().getCount() == 2, "the first fruit started a pearl");
                s.setGrowth(2500);
                use(s, p);
                helper.assertTrue(s.growth() == 2500 && p.getMainHandItem().getCount() == 2, "a second isn't taken while it grows, growth " + s.growth());
                s.setGrowth(6000);
                use(s, p);
                helper.assertTrue(s.growth() == 6000 && p.getMainHandItem().getCount() == 2, "nor once it is grown");
                s.shear(helper.getLevel(), SoundSource.PLAYERS, new ItemStack(Items.SHEARS));
                helper.assertTrue(!s.hasPearl(), "the shears took it");
            }
            if (t == 55) {                                      // the 20-tick shake after the shears is over
                InteractionResult fed = use(s, p);
                helper.assertTrue(fed == InteractionResult.SUCCESS_SERVER && s.growth() == 0 && p.getMainHandItem().getCount() == 1,
                        "now a fruit starts the next pearl, got " + fed + ", growth " + s.growth());
                helper.succeed();
            }
        });
    }

    /** Shut in its shell it doesn't move: a shove of 1.0 and its own goals leave it where it stood until it is out. */
    private static void rootedWhileShut(GameTestHelper helper) {
        PearlshellSnail s = helper.spawn(ModMobs.PEARLSHELL_SNAIL.get(), new Vec3(5.5, 1, 5.5));
        TestPlayers.whenDone(helper, s::discard);
        Vec3[] at = new Vec3[1];
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t == 5) {
                zap(helper, s, 1.0F);
                at[0] = s.position();
                s.knockback(1.0, 1.0, 0.0, s.damageSources().generic(), 0.0F, false);
                s.push(0.5, 0.0, 0.5);
            }
            if (t > 5 && t < 65) {
                helper.assertTrue(s.hidden(), "shut, phase " + s.phase());
                helper.assertTrue(Math.hypot(s.getX() - at[0].x, s.getZ() - at[0].z) < 0.01, "and where it stood, moved "
                        + Math.hypot(s.getX() - at[0].x, s.getZ() - at[0].z));
                helper.assertTrue(s.getNavigation().isDone(), "not walking anywhere");
            }
            if (t == 66) {
                helper.assertTrue(s.core().idle(), "out again");
                helper.succeed();
            }
        });
    }

    /**
     * Nothing given is lost: killed with its pearl grown it drops 1 ender pearl; killed while the pearl grows it gives
     * back the chorus fruit, 1; killed with none it drops neither.
     */
    private static void givesBack(GameTestHelper helper) {
        PearlshellSnail grown = snail(helper, 2.5, 5.5);
        PearlshellSnail growing = snail(helper, 5.5, 5.5);
        PearlshellSnail empty = snail(helper, 8.5, 5.5);
        grown.setGrowth(6000);
        growing.setGrowth(1234);
        Vec3[] at = {grown.position(), growing.position(), empty.position()};
        helper.runAtTickTime(3, () -> {
            for (PearlshellSnail s : List.of(grown, growing, empty)) zap(helper, s, 1000.0F);
            helper.assertTrue(grown.isDeadOrDying() && growing.isDeadOrDying() && empty.isDeadOrDying(), "all three die");
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(lying(helper, Items.ENDER_PEARL, at[0], 1.4) == 1 && lying(helper, Items.CHORUS_FRUIT, at[0], 1.4) == 0,
                    "the grown one drops its pearl, " + lying(helper, Items.ENDER_PEARL, at[0], 1.4));
            helper.assertTrue(lying(helper, Items.CHORUS_FRUIT, at[1], 1.4) == 1 && lying(helper, Items.ENDER_PEARL, at[1], 1.4) == 0,
                    "the growing one gives back the fruit, " + lying(helper, Items.CHORUS_FRUIT, at[1], 1.4));
            helper.assertTrue(lying(helper, Items.CHORUS_FRUIT, at[2], 1.4) == 0 && lying(helper, Items.ENDER_PEARL, at[2], 1.4) == 0,
                    "the empty one drops neither");
            helper.succeed();
        });
    }

    /**
     * Packs can veto each step: a vetoed "feed" leaves the fruit in hand and no pearl; a vetoed "harvest" leaves the
     * pearl and the shears untouched; a vetoed "hide" leaves it open, so the next blow lands in full.
     */
    private static void veto(GameTestHelper helper) {
        PearlshellSnail hungry = snail(helper, 3.5, 5.5);
        PearlshellSnail grown = snail(helper, 5.5, 5.5);
        PearlshellSnail open = snail(helper, 8.5, 5.5);
        grown.setGrowth(6000);
        Consumer<MechanicEvent> listener = e -> {
            if (e.mob() == hungry && e.step().equals("feed")) e.cancel();
            if (e.mob() == grown && e.step().equals("harvest")) e.cancel();
            if (e.mob() == open && e.step().equals("hide")) e.cancel();
        };
        MechanicEvent.listen(listener);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(listener));
        ServerPlayer p = holding(helper, new Vec3(5.5, 1, 3.5), new ItemStack(Items.CHORUS_FRUIT));
        helper.runAtTickTime(3, () -> {
            use(hungry, p);
            helper.assertTrue(!hungry.hasPearl() && p.getMainHandItem().getCount() == 1, "the vetoed feed leaves the fruit and no pearl");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            InteractionResult shorn = use(grown, p);
            helper.assertTrue(!shorn.consumesAction() && grown.pearlGrown(), "the vetoed harvest leaves the pearl, got " + shorn);
            helper.assertTrue(p.getMainHandItem().getDamageValue() == 0, "and the shears untouched");
            helper.assertTrue(zap(helper, open, 5.0F) && !open.hidden(), "the vetoed hide leaves it open");
        });
        helper.runAtTickTime(26, () -> {                     // past the 20-tick hurt cooldown of the first blow
            helper.assertTrue(lying(helper, Items.ENDER_PEARL) == 0, "no pearl dropped");
            float before = open.getHealth();
            helper.assertTrue(zap(helper, open, 5.0F), "the next blow lands");
            helper.assertTrue(Math.abs(before - open.getHealth() - 5.0F) < 0.01F, "in full, lost " + (before - open.getHealth()));
            helper.succeed();
        });
    }

    /** A reload keeps the pearl: a Purpur Snail at growth 3210 (stage 2) comes back at 3210, a grown one comes back grown. */
    private static void saveLoad(GameTestHelper helper) {
        PearlshellSnail growing = snail(helper, 3.5, 5.5, "purpur_snail");
        PearlshellSnail grown = snail(helper, 7.5, 5.5);
        growing.setNoAi(true);
        grown.setNoAi(true);
        PearlshellSnail[] copies = new PearlshellSnail[2];
        helper.runAtTickTime(2, () -> {
            growing.setGrowth(3210);
            grown.setGrowth(6000);
            grown.hide(null);
            ServerLevel level = helper.getLevel();
            PearlshellSnail[] originals = {growing, grown};
            for (int i = 0; i < 2; i++) {
                TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                originals[i].saveWithoutId(out);
                CompoundTag tag = out.buildResult();
                tag.remove("UUID");
                PearlshellSnail copy = ModMobs.PEARLSHELL_SNAIL.get().create(level, EntitySpawnReason.LOAD);
                copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                originals[i].discard();
                copies[i] = copy;
                helper.assertTrue(copy.growth() == (i == 0 ? 3210 : 6000), "the growth comes back, " + copy.growth());
                level.addFreshEntity(copy);
                TestPlayers.whenDone(helper, copy::discard);
            }
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(copies[0].hasPearl() && copies[0].core().stage() == 2 && copies[0].look().equals("purpur_snail"),
                    "the growing one at stage 2, purpur, stage " + copies[0].core().stage() + ", look " + copies[0].look());
            helper.assertTrue(copies[0].growth() >= 3210 && copies[0].growth() <= 3213, "and growing on from 3210, " + copies[0].growth());
            helper.assertTrue(copies[1].pearlGrown() && copies[1].core().stage() == 4, "the grown one grown, stage " + copies[1].core().stage());
            helper.assertTrue(copies[1].core().idle(), "a reload ends its hiding");
            helper.succeed();
        });
    }

    /** Health 16, attack 0, armour 6, a 0.6 x 0.5 hitbox, a creature; the Purpur Snail is the same. A new snail holds no pearl. */
    private static void stats(GameTestHelper helper) {
        PearlshellSnail s = snail(helper, 3.5, 5.5);
        PearlshellSnail purpur = snail(helper, 8.0, 5.5, "purpur_snail");
        helper.runAtTickTime(2, () -> {
            for (PearlshellSnail x : List.of(s, purpur)) {
                String who = x.look();
                helper.assertTrue(Math.abs(x.getMaxHealth() - 16.0F) < 0.01F && Math.abs(x.getHealth() - 16.0F) < 0.01F,
                        who + ": health 16, got " + x.getHealth() + "/" + x.getMaxHealth());
                helper.assertTrue(Math.abs(x.getAttributeValue(Attributes.ATTACK_DAMAGE)) < 0.01, who + ": attack 0");
                helper.assertTrue(Math.abs(x.getAttributeValue(Attributes.ARMOR) - 6.0) < 0.01, who + ": armour 6, " + x.getAttributeValue(Attributes.ARMOR));
                helper.assertTrue(Math.abs(x.getBbWidth() - 0.6F) < 0.01F && Math.abs(x.getBbHeight() - 0.5F) < 0.01F,
                        who + ": hitbox 0.6 x 0.5, got " + x.getBbWidth() + " x " + x.getBbHeight());
                helper.assertTrue(!x.hasPearl() && x.core().stage() == 0, who + ": no pearl yet");
            }
            helper.assertTrue(s.getType().getCategory() == MobCategory.CREATURE, "a creature, " + s.getType().getCategory());
            helper.succeed();
        });
    }

    /**
     * Home: its biome modifier adds it to the small End islands at weight 5 in groups of 1 to 3, and not to the main
     * island, the highlands, the midlands or the barrens. End stone is a spawn spot, in any light; a spot over air isn't.
     */
    private static void spawns(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/pearlshell_snail"));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).get(key).map(Holder::value).orElse(null);
        if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier add)) {
            helper.fail("no spawn biome modifier for the Pearlshell Snail: " + modifier);
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> spawners = add.spawners().unwrap();
        helper.assertTrue(spawners.size() == 1 && spawners.getFirst().value().type() == ModMobs.PEARLSHELL_SNAIL.get(), "one snail spawner, " + spawners);
        Weighted<MobSpawnSettings.SpawnerData> spawner = spawners.getFirst();
        helper.assertTrue(spawner.weight() == 5, "spawn weight 5, got " + spawner.weight());
        helper.assertTrue(spawner.value().count().minInclusive() == 1 && spawner.value().count().maxInclusive() == 3,
                "groups of 1 to 3, got " + spawner.value().count().minInclusive() + " to " + spawner.value().count().maxInclusive());
        var biomes = access.lookupOrThrow(Registries.BIOME);
        helper.assertTrue(add.biomes().contains(biomes.getOrThrow(Biomes.SMALL_END_ISLANDS)), "the small End islands are its home");
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.THE_END)), "not the main island");
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.END_HIGHLANDS)), "nor the highlands");
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.END_MIDLANDS)), "nor the midlands");
        helper.assertTrue(!add.biomes().contains(biomes.getOrThrow(Biomes.END_BARRENS)), "nor the barrens");

        helper.setBlock(new BlockPos(5, 0, 5), Blocks.END_STONE);
        RosterStats.Entry row = RosterStats.get("pearlshell_snail");
        PearlshellSnail s = snail(helper, 5.5, 5.5);
        s.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(row.place() == RosterStats.Place.GROUND, "a ground mob, place " + row.place());
            helper.assertTrue(row.anyLight(), "in any light");
            helper.assertTrue(TeemingSpawns.placeOk(row, s.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))), "end stone is a spawn spot");
            helper.assertTrue(!TeemingSpawns.placeOk(row, s.getType(), helper.getLevel(), helper.absolutePos(new BlockPos(8, 4, 8))), "a spot over air isn't");
            helper.succeed();
        });
    }
}
