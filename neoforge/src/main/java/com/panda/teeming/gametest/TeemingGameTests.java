package com.panda.teeming.gametest;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.spawn.TeemingSpawns;
import com.panda.teeming.registry.ModTabs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registration, data and behaviour checks that run on a GameTest server. */
public final class TeemingGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, TeemingMod.MODID);
    private static final Identifier ARENA = TeemingMod.id("test_arena");
    /** An empty box 100 wide, 48 high and 44 deep, open to the sky (written by the arena generator). */
    private static final Identifier WIDE_ARENA = TeemingMod.id("test_arena_wide");
    /** Ticks a test may run before it fails; most finish well inside this. */
    private static final int DEFAULT_TICKS = 100;
    private static final Map<String, Integer> TIME_LIMITS = new HashMap<>();
    private static final Set<String> WIDE = new HashSet<>();

    static {
        FUNCTIONS.register("mod_loaded", () -> TeemingGameTests::modLoaded);
        FUNCTIONS.register("lang_coverage", () -> TeemingGameTests::langCoverage);
        FUNCTIONS.register("looks_loaded", () -> TeemingGameTests::looksLoaded);
        FUNCTIONS.register("spawns_resolve", () -> TeemingGameTests::spawnsResolve);
        FUNCTIONS.register("looks_built", () -> TeemingGameTests::looksBuilt);
        FUNCTIONS.register("spawn_caps", () -> TeemingGameTests::spawnCaps);
        SpawnTests.register((name, test) -> FUNCTIONS.register(name, () -> test));
        FieldGuideTests.register((name, test) -> FUNCTIONS.register(name, () -> test));
        TrophyTests.register((name, test) -> FUNCTIONS.register(name, () -> test));
        MaterialTests.register((name, test) -> FUNCTIONS.register(name, () -> test));
        EffectTests.register((name, test) -> FUNCTIONS.register(name, () -> test));
        MobTests.register((name, test) -> FUNCTIONS.register(name, () -> test));
    }

    private TeemingGameTests() {}

    /**
     * Gives one test longer than the default 100 ticks, for mechanics that need it (a cooldown timed through to the
     * next attack, two shots 40 ticks apart). Call it next to the test's registration.
     */
    public static void timeLimit(String name, int ticks) {
        TIME_LIMITS.put(name, ticks);
    }

    /**
     * Stands one test in the wide arena with no roof, for a test that builds far past a 12-block arena. Everything it
     * builds stays inside the arena's chunks, which the test server keeps ticking: chunks a test forces fresh far away
     * may not tick their mobs for hundreds of ticks in a full run.
     */
    public static void wide(String name) {
        WIDE.add(name);
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(TeemingGameTests::registerTests);
    }

    /**
     * Test names to run, as comma-separated prefixes from the {@code teeming.gametests} system property (for example
     * {@code chorus_grazer_,spawn_}); empty or unset runs every test.
     */
    private static final java.util.List<String> ONLY = java.util.Arrays.stream(System.getProperty("teeming.gametests", "").split(","))
            .map(String::trim).filter(prefix -> !prefix.isEmpty()).toList();

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(TeemingMod.id("default"));
        for (var entry : FUNCTIONS.getEntries()) {
            String name = entry.getId().getPath();
            if (!ONLY.isEmpty() && ONLY.stream().noneMatch(name::startsWith)) continue;
            ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(Registries.TEST_FUNCTION, entry.getId());
            int ticks = TIME_LIMITS.getOrDefault(name, DEFAULT_TICKS);
            TestData<Holder<TestEnvironmentDefinition<?>>> data = WIDE.contains(name)
                    ? new TestData<>(env, Level.OVERWORLD, WIDE_ARENA, ticks, 0, true, Rotation.NONE, false, 1, 1, true, 0)
                    : new TestData<>(env, ARENA, ticks, 0, true);
            event.registerTest(key.identifier(), new FunctionGameTestInstance(key, data));
        }
    }

    // ---- tests -------------------------------------------------------------------------------------------------

    /** The mod's own registrations reached the game's registries. */
    private static void modLoaded(GameTestHelper helper) {
        if (BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(TeemingMod.id("main")).isEmpty()) helper.fail("teeming:main tab missing");
        if (ModTabs.MAIN.get() == null) helper.fail("ModTabs.MAIN did not resolve");
        helper.succeed();
    }

    /** Every name the mod shows has an English translation. */
    private static void langCoverage(GameTestHelper helper) {
        Language lang = Language.getInstance();
        List<String> missing = new ArrayList<>();
        for (String key : List.of("itemGroup.teeming")) {
            if (!lang.has(key)) missing.add(key);
        }
        BuiltInRegistries.ITEM.listElements().forEach(ref -> {
            if (!ref.key().identifier().getNamespace().equals(TeemingMod.MODID)) return;
            String key = ref.value().getDescriptionId();
            if (!lang.has(key)) missing.add(key);
        });
        BuiltInRegistries.BLOCK.listElements().forEach(ref -> {
            if (!ref.key().identifier().getNamespace().equals(TeemingMod.MODID)) return;
            String key = ref.value().getDescriptionId();
            if (!lang.has(key)) missing.add(key);
        });
        BuiltInRegistries.ENTITY_TYPE.listElements().forEach(ref -> {
            if (!ref.key().identifier().getNamespace().equals(TeemingMod.MODID)) return;
            String key = ref.value().getDescriptionId();
            if (!lang.has(key)) missing.add(key);
        });
        // item tags: recipe viewers show their names, and Fabric warns about a tag without one
        helper.getLevel().registryAccess().lookupOrThrow(Registries.ITEM).getTags().forEach(tag -> {
            Identifier id = tag.key().location();
            if (!id.getNamespace().equals(TeemingMod.MODID)) return;
            String key = "tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.');
            if (!lang.has(key)) missing.add(key);
        });
        helper.getLevel().getServer().getAdvancements().getAllAdvancements().forEach(holder -> {
            if (!holder.id().getNamespace().equals(TeemingMod.MODID)) return;
            holder.value().display().ifPresent(d -> {
                for (var c : List.of(d.title(), d.description())) {
                    if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && !lang.has(t.getKey())) missing.add(t.getKey());
                }
            });
        });
        if (!missing.isEmpty()) helper.fail("missing lang keys: " + missing);
        helper.succeed();
    }

    /** Every roster mob's looks loaded from data (177), base first, each with a name. */
    private static void looksLoaded(GameTestHelper helper) {
        Language lang = Language.getInstance();
        List<String> wrong = new ArrayList<>();
        for (RosterStats.Entry mob : RosterStats.ALL) {
            List<LookDef> looks = Looks.of(mob.id());
            List<String> want = mob.secondLook().isEmpty() ? List.of(LookDef.BASE) : List.of(LookDef.BASE, mob.secondLook());
            List<String> got = looks.stream().map(LookDef::id).toList();
            if (!got.equals(want)) wrong.add(mob.id() + " has " + got + ", want " + want);
            for (LookDef look : looks) {
                if (!lang.has("look.teeming." + mob.id() + "." + look.id())) wrong.add("no name for " + mob.id() + "/" + look.id());
                if (look.scale() < 0.5F || look.scale() > 1.5F) wrong.add(mob.id() + "/" + look.id() + " scale " + look.scale());
            }
        }
        if (Looks.count() != 177) wrong.add(Looks.count() + " looks loaded, want 177");
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }

    /**
     * Every mob has a home. Biome-spawned mobs: their tag holds at least one biome of this world; once the mob
     * is registered, its NeoForge biome modifier and placement exist too. Spawner-only mobs: a spawner rule.
     */
    private static void spawnsResolve(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var modifiers = access.lookupOrThrow(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        List<String> wrong = new ArrayList<>();
        for (RosterStats.Entry mob : RosterStats.ALL) {
            if (mob.biomeSpawns()) {
                int size = biomes.get(TeemingSpawns.tag(mob.id())).map(set -> set.size()).orElse(0);
                if (size == 0) wrong.add(mob.id() + ": empty biome tag");
            } else if (mob.spawner().isEmpty()) {
                wrong.add(mob.id() + ": no biome spawns and no spawner rule");
            }
            TeemingSpawns.type(mob).ifPresent(type -> {
                if (mob.biomeSpawns() && modifiers.get(ResourceKey.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                        TeemingMod.id("spawns/" + mob.id()))).isEmpty()) wrong.add(mob.id() + ": registered but no biome modifier (add it to roster_data.BUILT)");
                if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(type) == null) wrong.add(mob.id() + ": no placement");
            });
        }
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }

    /**
     * On real mobs: every registered roster mob, in each of its looks, has a texture and a hitbox scaled by the
     * look ({@code bbWidth == width x scale} within 0.02).
     */
    private static void looksBuilt(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (RosterStats.Entry row : RosterStats.ALL) {
            var type = TeemingSpawns.type(row).orElse(null);
            if (type == null) continue;
            var mob = helper.spawn(type, new net.minecraft.world.phys.Vec3(5.5, 1, 5.5));
            if (!(mob instanceof com.panda.teeming.mob.TeemingCreature creature)) {
                wrong.add(row.id() + " is not a TeemingCreature");
                continue;
            }
            for (LookDef look : Looks.of(row.id())) {
                creature.core().setLook(look.id(), true);
                float want = row.width() * look.scale();
                if (Math.abs(mob.getBbWidth() - want) > 0.02F) wrong.add(row.id() + "/" + look.id() + " width " + mob.getBbWidth() + ", want " + want);
                String texture = "/assets/teeming/textures/entity/" + row.id() + "/" + look.id() + ".png";
                if (TeemingGameTests.class.getResource(texture) == null) wrong.add("missing " + texture);
                checked++;
            }
            mob.discard();
        }
        if (checked == 0) wrong.add("no registered mob to check");
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }

    /** Local cap: with the cap reached a spawn is refused, one below it is allowed. Counts whatever other tests left nearby. */
    private static void spawnCaps(GameTestHelper helper) {
        RosterStats.Entry row = RosterStats.get("levimoth");
        var type = TeemingSpawns.type(row).orElseThrow();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(5, 1, 5));
        var area = new net.minecraft.world.phys.AABB(pos).inflate(TeemingSpawns.CAP_RADIUS);
        int already = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area, e -> e.getType() == type).size();
        List<net.minecraft.world.entity.Mob> placed = new ArrayList<>();
        for (int i = already; i < TeemingSpawns.CAP; i++) {
            var mob = helper.spawn(type, new net.minecraft.world.phys.Vec3(2.5 + placed.size() * 2, 1, 2.5));
            mob.setNoAi(true);
            placed.add(mob);
        }
        helper.assertTrue(!TeemingSpawns.underCap(row, type, level, pos), TeemingSpawns.CAP + " nearby: another is refused");
        if (placed.isEmpty()) {
            helper.succeed();   // other tests filled the cap already; the refusal is what matters
            return;
        }
        placed.getLast().discard();
        helper.assertTrue(TeemingSpawns.underCap(row, type, level, pos), (TeemingSpawns.CAP - 1) + " nearby: one more may spawn");
        helper.succeed();
    }
}
