package com.panda.teeming.gametest;

import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.advancement.TeemingAdvancements;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.GrandOrrery;
import com.panda.teeming.mob.nether.SlagheartGolem;
import com.panda.teeming.registry.ModCharms;
import com.panda.teeming.registry.ModComponents;
import com.panda.teeming.registry.ModItems;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Dim;
import com.panda.teeming.roster.RosterStats.Entry;
import com.panda.teeming.trophy.TrophyMob;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The advancement tab: every advancement loads with a display and English text, and each one the Field Guide grants
 * comes at the moment its rule is met, through the real seen check, kill and trophy beat (the feats are tested with
 * their mobs; the apex kills here, by a player's killing blow).
 */
public final class AdvancementTests {
    private AdvancementTests() {}

    /** Earned by holding any item of {@code #teeming:charms}: vanilla data, no code awards it. */
    static final String FIRST_CHARM = "first_charm";

    /** The tab, root first: what the generator writes and {@link TeemingAdvancements} names. */
    static final List<String> TAB = List.of(TeemingAdvancements.ROOT, TeemingAdvancements.OVERWORLD_NATURALIST,
            TeemingAdvancements.NETHER_NATURALIST, TeemingAdvancements.END_NATURALIST, TeemingAdvancements.KNOWLEDGE,
            TeemingAdvancements.MANTELPIECE, TeemingAdvancements.MENAGERIE, "basalt_behemoth", "liftstone_monolith", "grand_orrery",
            TeemingAdvancements.THORNHORN_WALL, TeemingAdvancements.SLAGHEART_QUENCH, TeemingAdvancements.MAGMA_TORTOISE_RIDE,
            TeemingAdvancements.GRAND_ORRERY_PATTERN, FIRST_CHARM);

    static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("advancements_load", AdvancementTests::load);
        out.accept("advancement_first_sight", AdvancementTests::firstSight);
        out.accept("advancement_naturalist", AdvancementTests::naturalist);
        out.accept("advancement_knowledge", AdvancementTests::knowledge);
        out.accept("advancement_trophies", AdvancementTests::trophies);
        out.accept("advancement_apex_kills", AdvancementTests::apexKills);
        out.accept("advancement_first_charm", AdvancementTests::firstCharm);
        out.accept("advancement_cooled_off", AdvancementTests::cooledOff);
        for (Stars how : Stars.values()) {
            String name = "advancement_between_the_stars_" + how.name().toLowerCase(java.util.Locale.ROOT);
            out.accept(name, h -> betweenTheStars(h, how));
            TeemingGameTests.timeLimit(name, 160);
            if (how == Stars.LEAVES) TeemingGameTests.wide(name);
        }
    }

    /** Whether the player has earned {@code teeming:<key>}; fails the test if it isn't loaded. */
    public static boolean done(GameTestHelper helper, ServerPlayer player, String key) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(TeemingMod.id(key));
        if (holder == null) throw helper.assertionException("advancement teeming:" + key + " is not loaded");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        FieldGuide.set(player, FieldGuideProgress.EMPTY);
        return player;
    }

    private static List<Entry> of(Dim dim) {
        return RosterStats.ALL.stream().filter(m -> dim == null || m.dim() == dim).toList();
    }

    private static String english(Component c) {
        return c.getContents() instanceof TranslatableContents t && Language.getInstance().has(t.getKey()) ? Language.getInstance().getOrDefault(t.getKey()) : null;
    }

    /**
     * The 15 advancements load: each has a display with an English title and
     * description and a mod item for its icon, shows a toast, and hangs off the root, which has no parent and the tab's
     * background. The apex kills and the first charm are vanilla criteria; the rest have the one "done" criterion code awards.
     */
    private static void load(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getAdvancements();
        List<String> wrong = new ArrayList<>();
        List<String> ours = manager.getAllAdvancements().stream().map(AdvancementHolder::id)
                .filter(id -> id.getNamespace().equals(TeemingMod.MODID) && !id.getPath().startsWith("recipes/")).map(id -> id.getPath()).sorted().toList();
        if (!ours.equals(TAB.stream().sorted().toList())) wrong.add("the tab is " + ours + ", want " + TAB.stream().sorted().toList());
        for (String key : TAB) {
            AdvancementHolder holder = manager.get(TeemingMod.id(key));
            if (holder == null) {
                wrong.add(key + " is not loaded");
                continue;
            }
            DisplayInfo d = holder.value().display().orElse(null);
            if (d == null) {
                wrong.add(key + " has no display");
                continue;
            }
            String title = english(d.title()), description = english(d.description());
            if (title == null || title.isBlank()) wrong.add(key + ": no English title");
            if (description == null || description.isBlank()) wrong.add(key + ": no English description");
            if (!d.showToast()) wrong.add(key + ": no toast");
            if (!d.icon().item().unwrapKey().map(k -> k.identifier().getNamespace().equals(TeemingMod.MODID)).orElse(false)) {
                wrong.add(key + ": its icon is not one of the mod's items, " + d.icon());
            }
            boolean root = key.equals(TeemingAdvancements.ROOT);
            if (root != holder.value().parent().isEmpty()) wrong.add(key + ": parent " + holder.value().parent());
            if (root != d.background().isPresent()) wrong.add(key + ": background " + d.background());
            if (!root && holder.value().parent().map(p -> !p.getNamespace().equals(TeemingMod.MODID)).orElse(true)) wrong.add(key + ": not in the tab");
            boolean coded = !List.of("basalt_behemoth", "liftstone_monolith", "grand_orrery", FIRST_CHARM).contains(key);
            if (coded && !holder.value().criteria().keySet().equals(java.util.Set.of(TeemingAdvancements.CRITERION))) {
                wrong.add(key + ": criteria " + holder.value().criteria().keySet() + ", want [done]");
            }
        }
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }

    /** No progress, no root; the first Teeming creature the 20-tick look records earns Teeming, and nothing else. */
    private static void firstSight(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.assertTrue(!done(helper, player, TeemingAdvancements.ROOT), "a fresh player hasn't earned Teeming");
        Mob horn = helper.spawn(ModMobs.THORNHORN.get(), new Vec3(5.5, 1, 1.5));
        horn.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(FieldGuide.sees(player, horn), "the Thornhorn 4 blocks off is in sight");
            FieldGuide.look(player);
            helper.assertTrue(FieldGuide.get(player).hasSeen("thornhorn"), "the look records it");
            helper.assertTrue(done(helper, player, TeemingAdvancements.ROOT), "first sight earns Teeming");
            helper.assertTrue(!done(helper, player, TeemingAdvancements.OVERWORLD_NATURALIST), "but not a Naturalist");
            helper.assertTrue(!done(helper, player, TeemingAdvancements.MANTELPIECE), "nor Mantelpiece");
            helper.succeed();
        });
    }

    /**
     * Every creature of a dimension but one seen earns nothing; the last one, seen by the look (the Overworld's) or
     * through a kill (the Nether's), earns that dimension's Naturalist only. The End's comes with its last entry too.
     */
    private static void naturalist(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        FieldGuideProgress p = FieldGuideProgress.EMPTY;
        for (Entry mob : of(null)) {
            if (!mob.id().equals("thornhorn") && !mob.id().equals("magma_tortoise") && !mob.id().equals("levimoth")) p = p.withSeen(mob.id(), "base");
        }
        FieldGuide.set(player, p);
        helper.assertTrue(done(helper, player, TeemingAdvancements.ROOT), "the creatures seen earn the root");
        for (String key : List.of(TeemingAdvancements.OVERWORLD_NATURALIST, TeemingAdvancements.NETHER_NATURALIST, TeemingAdvancements.END_NATURALIST)) {
            helper.assertTrue(!done(helper, player, key), "one creature short in every dimension earns no Naturalist, yet " + key);
        }
        Mob horn = helper.spawn(ModMobs.THORNHORN.get(), new Vec3(5.5, 1, 1.5));
        horn.setNoAi(true);
        Mob tortoise = helper.spawn(ModMobs.MAGMA_TORTOISE.get(), new Vec3(5.5, 1, 7.5));
        tortoise.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(FieldGuide.sees(player, horn), "the Thornhorn is in sight");
            FieldGuide.look(player);
            helper.assertTrue(done(helper, player, TeemingAdvancements.OVERWORLD_NATURALIST), "the last Overworld creature seen earns Overworld Naturalist");
            if (!FieldGuide.get(player).hasSeen("magma_tortoise")) {        // the look may have caught the tortoise too
                helper.assertTrue(!done(helper, player, TeemingAdvancements.NETHER_NATURALIST), "not the Nether's while the tortoise is unseen");
                tortoise.hurtServer(helper.getLevel(), tortoise.damageSources().playerAttack(player), 1000.0F);
                helper.assertTrue(tortoise.isDeadOrDying(), "the player's blow kills the tortoise");
            }
            helper.assertTrue(done(helper, player, TeemingAdvancements.NETHER_NATURALIST), "and the Nether's last earns Nether Naturalist");
            if (!FieldGuide.get(player).hasSeen("levimoth")) {              // a neighbouring arena's Levimoth may have been in view
                helper.assertTrue(!done(helper, player, TeemingAdvancements.END_NATURALIST), "the End is still one short");
                FieldGuide.set(player, FieldGuide.get(player).withSeen("levimoth", "base"));
            }
            helper.assertTrue(done(helper, player, TeemingAdvancements.END_NATURALIST), "the End's last earns End Naturalist");
            helper.assertTrue(!done(helper, player, TeemingAdvancements.KNOWLEDGE), "seeing all 90 is not revealing them");
            helper.succeed();
        });
    }

    /** All 90 revealed earns Teeming with Knowledge: 89 revealed and one a kill short don't; that last kill does. */
    private static void knowledge(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        int reveal = TeemingConfig.killsToReveal();
        FieldGuideProgress p = FieldGuideProgress.EMPTY;
        for (Entry mob : of(null)) {
            int kills = mob.id().equals("levimoth") ? reveal - 1 : reveal;
            for (int i = 0; i < kills; i++) p = p.withKill(mob.id(), "base");
        }
        FieldGuide.set(player, p);
        helper.assertTrue(done(helper, player, TeemingAdvancements.END_NATURALIST), "every creature killed is every creature seen");
        helper.assertTrue(!done(helper, player, TeemingAdvancements.KNOWLEDGE), "89 revealed earn nothing yet");
        Mob moth = helper.spawn(ModMobs.LEVIMOTH.get(), new Vec3(5.5, 1, 5.5));
        moth.setNoAi(true);
        moth.hurtServer(helper.getLevel(), moth.damageSources().playerAttack(player), 1000.0F);
        helper.assertTrue(moth.isDeadOrDying(), "the player's blow kills it");
        helper.assertTrue(FieldGuide.get(player).revealed("levimoth"), "the kill reveals the Levimoth");
        helper.assertTrue(done(helper, player, TeemingAdvancements.KNOWLEDGE), "the 90th reveal earns Teeming with Knowledge");
        helper.succeed();
    }

    private static ItemStack trophy(String mob) {
        ItemStack stack = ModItems.TROPHY.get().getDefaultInstance();
        stack.set(ModComponents.TROPHY_MOB.get(), new TrophyMob(mob, "base"));
        return stack;
    }

    /**
     * A trophy in the inventory on the 20-tick beat earns Mantelpiece (a blank one doesn't); 89 owned and the 90th
     * picked up earns The Whole Menagerie.
     */
    private static void trophies(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.getInventory().setItem(0, ModItems.TROPHY.get().getDefaultInstance());
        FieldGuide.look(player);
        helper.assertTrue(!done(helper, player, TeemingAdvancements.MANTELPIECE), "a blank trophy names no creature and earns nothing");
        player.getInventory().setItem(1, trophy("thornhorn"));
        FieldGuide.look(player);
        helper.assertTrue(done(helper, player, TeemingAdvancements.MANTELPIECE), "the first trophy owned earns Mantelpiece");
        helper.assertTrue(!done(helper, player, TeemingAdvancements.MENAGERIE), "one trophy is not all 90");
        FieldGuideProgress p = FieldGuide.get(player);
        for (Entry mob : of(null)) if (!mob.id().equals("grand_orrery")) p = p.withTrophy(mob.id());
        FieldGuide.set(player, p);
        helper.assertTrue(!done(helper, player, TeemingAdvancements.MENAGERIE), "89 trophies earn nothing yet");
        player.getInventory().setItem(2, trophy("grand_orrery"));
        FieldGuide.look(player);
        helper.assertTrue(FieldGuide.get(player).trophies().size() == 90, "90 owned, " + FieldGuide.get(player).trophies().size());
        helper.assertTrue(done(helper, player, TeemingAdvancements.MENAGERIE), "the 90th trophy earns The Whole Menagerie");
        helper.succeed();
    }

    /**
     * Holding a counter charm earns the first charm; a material does not. The inventory trigger is fired the way
     * vanilla fires it when a slot changes, with the stack that went in.
     */
    private static void firstCharm(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemStack hide = new ItemStack(ModMaterials.TOUGH_HIDE.get());
        player.getInventory().add(hide.copy());
        CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), hide);
        helper.assertTrue(!done(helper, player, FIRST_CHARM), "a material doesn't earn the first charm");
        ItemStack charm = new ItemStack(ModCharms.SHELLWARD_CHARM.get());
        player.getInventory().add(charm.copy());
        CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), charm);
        helper.assertTrue(done(helper, player, FIRST_CHARM), "a charm in the inventory earns the first charm");
        helper.succeed();
    }

    /** Each apex creature killed by a player's blow earns its own advancement and no other; a death by magic none. */
    private static void apexKills(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Map<String, EntityType<? extends Mob>> apex = Map.of("basalt_behemoth", ModMobs.BASALT_BEHEMOTH.get(),
                "liftstone_monolith", ModMobs.LIFTSTONE_MONOLITH.get(), "grand_orrery", ModMobs.GRAND_ORRERY.get());
        List<String> order = List.of("basalt_behemoth", "liftstone_monolith", "grand_orrery");
        List<String> killed = new ArrayList<>();
        Mob unowned = helper.spawn(apex.get("basalt_behemoth"), new Vec3(9.5, 1, 9.5));
        unowned.setNoAi(true);
        unowned.hurtServer(helper.getLevel(), unowned.damageSources().magic(), 100000.0F);
        helper.assertTrue(unowned.isDeadOrDying(), "magic kills the first Behemoth");
        helper.assertTrue(!done(helper, player, "basalt_behemoth"), "a death the player didn't deal earns nothing");
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i);
            Mob mob = helper.spawn(apex.get(key), new Vec3(2.5 + i * 3.5, 1, 6.5));
            mob.setNoAi(true);
            mob.hurtServer(helper.getLevel(), mob.damageSources().playerAttack(player), 100000.0F);
            helper.assertTrue(mob.isDeadOrDying(), "the player's blow kills the " + key);
            killed.add(key);
            for (String other : order) {
                helper.assertTrue(done(helper, player, other) == killed.contains(other),
                        "after killing " + killed + ", " + other + " done: " + done(helper, player, other));
            }
        }
        helper.assertTrue(RosterStats.ALL.stream().filter(Entry::apex).map(Entry::id).collect(Collectors.toSet()).equals(apex.keySet()),
                "the three apex creatures are the roster's apex rows");
        helper.succeed();
    }

    // ---- the feats whose rules are checked here as well as with their mobs ---------------------------------------

    /**
     * Cooled Off goes to the player whose own cooling hit quenches a Slagheart Golem, and a Quench Charm hit is one: a
     * survival player with the charm in the off-hand strikes a still golem at heat 2, which cools it to 1 and earns
     * nothing, then again, which quenches it and earns Cooled Off. A second player strikes a golem with a bare hand,
     * which heats it, and the golem is then quenched by powder snow at its feet: powder snow has no one to earn it, so
     * that player earns nothing though they were fighting it.
     */
    private static void cooledOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SlagheartGolem charmed = helper.spawn(ModMobs.SLAGHEART_GOLEM.get(), new Vec3(2.5, 1, 2.5));
        charmed.setNoAi(true);
        charmed.setHeat(2);
        ServerPlayer holder = TestPlayers.survivor(helper, new Vec3(4.5, 1, 2.5));
        holder.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModCharms.QUENCH_CHARM.get()));
        SlagheartGolem snowed = helper.spawn(ModMobs.SLAGHEART_GOLEM.get(), new Vec3(8.5, 1, 8.5));
        snowed.setHeat(2);
        ServerPlayer fighter = TestPlayers.survivor(helper, new Vec3(3.5, 1, 9.5));
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(charmed.hurtServer(level, level.damageSources().playerAttack(holder), 1.0F), "the charm holder's first blow lands");
            helper.assertTrue(charmed.heat() == 1 && !charmed.core().in(SlagheartGolem.Step.QUENCHED), "the charm hit cools 2 to 1, heat " + charmed.heat());
            helper.assertTrue(!done(helper, holder, TeemingAdvancements.SLAGHEART_QUENCH), "cooling short of 0 earns nothing");
            helper.assertTrue(snowed.hurtServer(level, level.damageSources().playerAttack(fighter), 1.0F), "the bare-handed blow lands");
            helper.assertTrue(snowed.heat() == 3, "a bare-handed blow heats it, heat " + snowed.heat());
        });
        helper.runAtTickTime(25, () -> {                 // after the golem's 20-tick hurt cooldown
            helper.assertTrue(charmed.hurtServer(level, level.damageSources().playerAttack(holder), 1.0F), "the charm holder's second blow lands");
            helper.assertTrue(charmed.heat() == 0 && charmed.core().in(SlagheartGolem.Step.QUENCHED), "the second charm hit quenches it, heat " + charmed.heat());
            helper.assertTrue(done(helper, holder, TeemingAdvancements.SLAGHEART_QUENCH), "and earns the charm holder Cooled Off");
            helper.setBlock(helper.relativePos(snowed.blockPosition()), Blocks.POWDER_SNOW);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(snowed.heat() == 0 && snowed.core().in(SlagheartGolem.Step.QUENCHED), "powder snow quenches the second golem, heat " + snowed.heat());
            helper.assertTrue(!done(helper, fighter, TeemingAdvancements.SLAGHEART_QUENCH), "and earns the player fighting it nothing");
        });
    }

    /** How the player meets the Grand Orrery's double ring in a Between the Stars test. */
    private enum Stars {
        /** Steps into the gap between two lanes and stays there, in reach: earns it. */
        EARNS,
        /** Steps out of the motes' range as they leave and back into the gap a few ticks later: earns nothing. */
        LEAVES,
        /** Stands where it aimed behind a stone wall the motes fade on, out of the orrery's sight: earns nothing. */
        HIDES,
        /** A listener cancels every mote, so none flies: earns nothing. */
        CANCELLED
    }

    /**
     * Between the Stars is earned only by staying in a pattern's reach, in range and in sight of the orrery, for the
     * whole pattern, with at least one mote actually flying and none touching you. A hanging orrery with no goals casts
     * a double ring (24 motes) at a survival player 5 blocks out, and the test waits until the pattern is over and
     * every mote spent; in every case nothing touches the player. LEAVES runs in the wide arena, where 18 blocks out is
     * clear of every wall; the others in a walled arena, so no mote leaves it.
     */
    private static void betweenTheStars(GameTestHelper helper, Stars how) {
        Vec3 hang = how == Stars.LEAVES ? new Vec3(30.0, 2, 22.0) : new Vec3(3.0, 2, 5.5);
        if (how != Stars.LEAVES) {
            for (int y = 1; y <= 5; y++) {
                for (int i = 0; i < 12; i++) {
                    helper.setBlock(new BlockPos(i, y, 0), Blocks.STONE);
                    helper.setBlock(new BlockPos(i, y, 11), Blocks.STONE);
                    helper.setBlock(new BlockPos(0, y, i), Blocks.STONE);
                    helper.setBlock(new BlockPos(11, y, i), Blocks.STONE);
                }
            }
        }
        if (how == Stars.HIDES) {                         // a wall 2 blocks out from the orrery, across the aim
            for (int y = 1; y <= 5; y++) for (int z = 3; z <= 8; z++) helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
        }
        GrandOrrery o = helper.spawn(ModMobs.GRAND_ORRERY.get(), hang);
        o.removeFreeWill();
        o.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        o.holdFire(true);
        o.anchor(true);
        TestPlayers.whenDone(helper, o::discard);
        Vec3 gap = along(hang, 7.5, 5.0);
        ServerPlayer p = TestPlayers.survivor(helper, along(hang, 0.0, 5.0));
        if (how == Stars.CANCELLED) {
            Consumer<MechanicEvent> veto = e -> {
                if (e.mob() == o && e.step().equals("mote")) e.cancel();
            };
            MechanicEvent.listen(veto);
            TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        }
        float[] health = {20.0F};
        long[] left = {-1};
        boolean[] cast = {false}, moved = {false};
        helper.runAtTickTime(1, () -> {
            health[0] = p.getHealth();
            o.setNextPattern(GrandOrrery.Step.DOUBLE_RING);
            helper.assertTrue(o.spinUp(p), "it spins up at the player");
        });
        helper.onEachTick(() -> {
            if (!o.core().in(GrandOrrery.Step.DOUBLE_RING)) return;
            cast[0] = true;
            if (o.fired() == 0) return;                     // the aim is taken as the first motes leave
            boolean first = !moved[0];
            moved[0] = true;
            if (how == Stars.EARNS && first) p.snapTo(helper.absoluteVec(gap));
            if (how == Stars.LEAVES && first) {
                p.snapTo(helper.absoluteVec(along(hang, 0.0, 18.0)));
                left[0] = helper.getTick();
            }
            if (how == Stars.LEAVES && left[0] >= 0 && helper.getTick() == left[0] + 5) p.snapTo(helper.absoluteVec(gap));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cast[0] && o.core().in(GrandOrrery.Step.OPEN) && o.motes().isEmpty(), "the pattern is over and every mote spent, phase " + o.phase());
            helper.assertTrue(o.fired() == (how == Stars.CANCELLED ? 0 : 24), "motes flown: " + o.fired());
            helper.assertTrue(health[0] - p.getHealth() == 0.0F, "nothing touched the player, lost " + (health[0] - p.getHealth()));
            boolean earned = done(helper, p, TeemingAdvancements.GRAND_ORRERY_PATTERN);
            helper.assertTrue(earned == (how == Stars.EARNS), how + ": earned Between the Stars " + earned);
        });
    }

    /** {@code at} plus {@code distance} blocks round {@code degrees} from +X toward +Z, on the floor. */
    private static Vec3 along(Vec3 at, double degrees, double distance) {
        double r = Math.toRadians(degrees);
        return new Vec3(at.x + Math.cos(r) * distance, 1, at.z + Math.sin(r) * distance);
    }
}
