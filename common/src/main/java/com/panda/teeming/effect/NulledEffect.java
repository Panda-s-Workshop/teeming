package com.panda.teeming.effect;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.registry.ModEffects;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * Nulled (harmful): the enchantments on a player's gear do nothing. It is the mark of a player standing in a null ring
 * (a Nullcantor's): the ring holds whoever is inside it every tick, and the status lifts by itself within
 * {@link #GRACE} ticks of the last hold, so it is never carried away from the ring, outlives no ring and survives no
 * relog. It has no length of its own, no particles and an icon.
 *
 * <p>While it lasts, every enchantment effect vanilla looks up through {@code EnchantmentHelper} finds nothing on the
 * bearer's gear but its curses (Binding and Vanishing still hold): damage and protection, post-attack effects (Thorns,
 * Fire Aspect, Channeling), projectile effects (Power, Punch, Flame, Multishot, Piercing, Loyalty, Riptide, Quick
 * Charge), durability (Unbreaking, Mending), loot (Looting, Fortune), and the attribute and location effects (Efficiency,
 * Respiration, Depth Strider, Swift Sneak, Soul Speed, Frost Walker...). The mixin on {@code EnchantmentHelper} asks
 * {@link #view}, {@link #nulled} and {@link #sync}. The lookups run on the server, where the gear of every nulled
 * creature is known; the few a client runs for its own player (Quick Charge, Riptide) ask the bearer's status, which
 * the client has too. The enchantments themselves are untouched: the item keeps them, shows them and works again the
 * moment its wearer steps out. Silk Touch is not an enchantment effect but a loot table rule on the tool and still
 * applies.
 *
 * <p>With a Memory Charm in the bearer's off-hand ({@link Charms#halvesNulling}) the suppression only halves: every
 * lookup that reads the gear finds each enchantment at {@link Charms#nulledLevel} of its level instead of none, and
 * the lookups that ask about the bearer instead of an item ({@link #blanked}) let the halved gear speak. Quick Charge
 * and Riptide, the two a client also runs for its own player, stay off either way, so the client and the server agree.
 */
public final class NulledEffect extends TeemingEffect {
    public static final int COLOR = 0x7A4FC9;
    /** Ticks the status outlasts the last ring that held its bearer. */
    public static final int GRACE = 2;

    /** Server thread only: when a ring last held each bearer (game time). */
    private static final Map<LivingEntity, Long> HELD = new WeakHashMap<>();
    /** Creatures whose enchantments are switched off right now: their attribute and location effects are taken off. */
    private static final Set<LivingEntity> STRIPPED = ConcurrentHashMap.newKeySet();
    /** The stripped creatures that hold a Memory Charm: their gear reads at half its levels instead of none. */
    private static final Set<LivingEntity> HALVED = ConcurrentHashMap.newKeySet();
    private static volatile @Nullable Thread serverThread;

    public NulledEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /** The status as a ring gives it: no end of its own, no particles, an icon. */
    public static MobEffectInstance instance() {
        return new MobEffectInstance(ModEffects.NULLED.holder(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
    }

    /** Server: a ring holds {@code e} this tick; gives it the status if it lacks it. */
    public static void hold(LivingEntity e) {
        if (e.level().isClientSide()) return;
        HELD.put(e, e.level().getGameTime());
        if (!e.hasEffect(ModEffects.NULLED.holder())) e.addEffect(instance());
    }

    /** Server: true while a ring held {@code e} within the last {@link #GRACE} ticks. */
    public static boolean held(LivingEntity e) {
        Long at = HELD.get(e);
        return at != null && e.level().getGameTime() - at <= GRACE;
    }

    /** True while the enchantments of {@code e}'s gear do nothing (either side). */
    public static boolean nulled(@Nullable Entity e) {
        return e instanceof LivingEntity living && living.hasEffect(ModEffects.NULLED.holder());
    }

    /** True while the one who loosed {@code e} (an arrow, a trident) stands nulled, or {@code e} itself is nulled. */
    public static boolean nulledShooter(@Nullable Entity e) {
        return e instanceof Projectile p ? nulled(p.getOwner()) : nulled(e);
    }

    /**
     * True while {@code e} is nulled with nothing left of its enchantments: nulled without a Memory Charm. A lookup that
     * asks about the bearer rather than an item answers "none" only then; with the charm it reads the gear, halved.
     */
    public static boolean blanked(@Nullable Entity e) {
        return nulled(e) && !Charms.halvesNulling((LivingEntity) e);
    }

    /** As {@link #blanked}, for the one who loosed {@code e}, or {@code e} itself. */
    public static boolean blankedShooter(@Nullable Entity e) {
        return e instanceof Projectile p ? blanked(p.getOwner()) : blanked(e);
    }

    @Override
    protected boolean tick(ServerLevel level, LivingEntity entity, int amplifier) {
        return held(entity);
    }

    // ---- the enchantment lookups -----------------------------------------------------------------------------------

    /**
     * The stack an enchantment lookup should read in place of {@code stack}: the stack itself, unless it is the gear of
     * a nulled creature; then a copy holding only its curses, or no stack at all when it has none. A Memory Charm
     * holder's gear reads as a copy with every other enchantment at {@link Charms#nulledLevel} of its level. Server
     * thread only: elsewhere it always answers the stack itself.
     */
    public static ItemStack view(ItemStack stack) {
        if (STRIPPED.isEmpty() || stack.isEmpty() || Thread.currentThread() != serverThread) return stack;
        ItemEnchantments all = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (all.isEmpty()) return stack;
        LivingEntity wearer = wearer(stack);
        if (wearer == null) return stack;
        ItemEnchantments.Mutable kept = new ItemEnchantments.Mutable(all);
        if (HALVED.contains(wearer)) {
            for (Holder<Enchantment> e : List.copyOf(kept.keySet())) {
                if (!e.is(EnchantmentTags.CURSE)) kept.set(e, Charms.nulledLevel(kept.getLevel(e)));
            }
        } else {
            kept.removeIf(e -> !e.is(EnchantmentTags.CURSE));
            if (kept.keySet().isEmpty()) return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.set(DataComponents.ENCHANTMENTS, kept.toImmutable());
        return copy;
    }

    /** The level of {@code enchantment} a lookup on {@code stack} should find: as {@link #view} shows the stack. */
    public static int level(ItemStack stack, Holder<Enchantment> enchantment) {
        return view(stack).getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment);
    }

    /** True when a lookup on {@code stack} should see only its curses: it is the gear of a nulled creature. */
    public static boolean hides(ItemStack stack) {
        return view(stack) != stack;
    }

    /**
     * The nulled creature whose gear {@code stack} is: one of its equipment stacks, or equal to what it holds (block
     * loot reads a copy of the tool that broke the block); null when it is no one's.
     */
    private static @Nullable LivingEntity wearer(ItemStack stack) {
        for (LivingEntity e : STRIPPED) {
            if (e.isRemoved()) {
                STRIPPED.remove(e);
                HALVED.remove(e);
                continue;
            }
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                if (e.getItemBySlot(slot) == stack) return e;
            }
            if (ItemStack.matches(e.getMainHandItem(), stack) || ItemStack.matches(e.getOffhandItem(), stack)) return e;
        }
        return null;
    }

    /**
     * Server, every tick for every creature (from the enchantment tick): when a player's status comes or goes, takes
     * off or puts back what its enchantments keep applied between lookups: the attribute modifiers (Efficiency, Depth
     * Strider, Swift Sneak...) and the location effects (Soul Speed, Frost Walker). A Memory Charm moved into or out of
     * the off-hand while nulled puts everything back and strips it again, halved or not.
     */
    public static void sync(ServerLevel level, LivingEntity entity) {
        if (!(entity instanceof Player)) return;
        serverThread = Thread.currentThread();
        boolean on = entity.hasEffect(ModEffects.NULLED.holder());
        boolean half = on && Charms.halvesNulling(entity);
        if (STRIPPED.contains(entity) && (!on || half != HALVED.contains(entity))) restore(level, entity);
        if (on && !STRIPPED.contains(entity)) strip(level, entity, half);
    }

    private static void strip(ServerLevel level, LivingEntity entity, boolean half) {
        // still unlisted, so these see the whole of each enchantment and take all of it off...
        EnchantmentHelper.stopLocationBasedEffects(entity);
        modifiers(entity, false);
        if (half) HALVED.add(entity);
        STRIPPED.add(entity);
        // ...then, listed, they see only the curses (or, halved, half of every level) and put those back
        modifiers(entity, true);
        EnchantmentHelper.runLocationChangedEffects(level, entity);
    }

    private static void restore(ServerLevel level, LivingEntity entity) {
        EnchantmentHelper.stopLocationBasedEffects(entity);      // the curses' own, or the halved ones
        STRIPPED.remove(entity);
        HALVED.remove(entity);
        modifiers(entity, true);
        EnchantmentHelper.runLocationChangedEffects(level, entity);
    }

    /** Takes off (or, with {@code add}, puts back) the attribute modifiers the enchantments on each worn stack give. */
    private static void modifiers(LivingEntity entity, boolean add) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            EnchantmentHelper.forEachModifier(stack, slot, (attribute, modifier) -> {
                AttributeInstance instance = entity.getAttributes().getInstance(attribute);
                if (instance == null) return;
                instance.removeModifier(modifier.id());
                if (add) instance.addTransientModifier(modifier);
            });
        }
    }
}
