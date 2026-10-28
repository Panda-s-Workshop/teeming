package com.panda.teeming.charm;

import com.panda.teeming.item.CharmItem;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.platform.registry.DeferredItem;
import com.panda.teeming.registry.ModCharms;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * What each counter charm changes, and the one check every mechanic makes: a charm counts only in its holder's
 * off-hand, so wearing one trades away the shield, totem or torch. A mechanic asks here with its target and scales
 * its own number; the charm item carries no behaviour of its own.
 */
public final class Charms {
    private Charms() {}

    /** Shellward Charm: a charge or ram throws its holder this much as far (and as high). */
    public static final double SHELLWARD_THROW = 0.5;

    /**
     * Hearth Charm: the share of a Teeming mob's freeze build-up its holder takes. It covers freezing a mob adds on
     * purpose (the Shiverwraith's frost tether); vanilla powder snow and the thaw are left as they are.
     */
    public static final double HEARTH_FREEZE = 0.5;

    /** Hearth Charm: the share of the health a frost tether on its holder heals the tether's owner (the Shiverwraith). */
    public static final double HEARTH_TETHER_HEAL = 0.5;

    /**
     * Hush Charm: the share of a sound hunter's mining range at which its holder's mining is heard. Walking and
     * sneaking already make no noise a sound hunter hears, so with the charm only sprinting and fighting are heard:
     * breaking blocks becomes as quiet as walking. A projectile its holder throws still lands as a decoy.
     */
    public static final double HUSH_MINING = 0.0;

    /** Antivenom Charm: the share of its length that Poison from a Teeming mob lasts on its holder. */
    public static final double ANTIVENOM_POISON = 0.5;

    /**
     * Quench Charm: heat a hit from its holder takes off a heat-builder, where any other hit adds heat. A heat-builder is
     * a mob whose heat gauge rises with the blows it takes and erupts when full (the Slagheart Golem); the holder's hit
     * is a cooling hit like a snowball's, so it also stops an eruption being told.
     */
    public static final int QUENCH_COOL = 1;
    /**
     * Quench Charm: the share of each burn that reaches its holder while it burns with the fire a burning patch lit
     * (the patch's after-burn included). A burning patch is a mob's area of burning ground (the Slagspitter's); vanilla
     * burning deals the damage, so the burn is halved where vanilla applies it, before armour.
     */
    public static final float QUENCH_PATCH_BURN = 0.5F;

    /**
     * Tether Charm: ticks a tether holds its holder before it snaps, as the mob's own counter would snap it. A tether is
     * a line a mob keeps on a victim at range for as long as range and sight allow (the Veinleech's vein); a pull that
     * drags its victim in and ends by itself well within this (the Fortress Jailer's chain) is not one.
     */
    public static final int TETHER_SNAP_TICKS = 40;

    /**
     * Gilded Ward Charm: the chance a theft from its holder fails, rolled on the thief's own random source each time it
     * would take something: an item (the Aurovore's bite) or an effect (the Siphonjelly's sting). A failed theft takes
     * nothing; the blow that carried it still lands.
     */
    public static final float GILDED_WARD_FAIL = 0.5F;

    /** Clear Voice Charm: ticks Silenced lasts on its holder, whatever length its source gives (a wail gives 80). */
    public static final int CLEAR_VOICE_SILENCED_TICKS = 20;

    /** Who burns with a patch's fire, and the game time that fire runs out (server side; weak, so no one is kept alive). */
    private static final Map<LivingEntity, Long> PATCH_FIRE = new WeakHashMap<>();

    /** True when {@code holder} has {@code charm} in the off-hand. */
    public static boolean holds(LivingEntity holder, DeferredItem<CharmItem> charm) {
        return holder.getOffhandItem().is(charm.get());
    }

    /** A chance charm that saved its holder says so at most this often, so a run of saves doesn't ring on every tick. */
    public static final int FELT_COOLDOWN = 40;

    /** The game time each holder last heard its charm save it (server side; weak, so no one is kept alive). */
    private static final Map<LivingEntity, Long> FELT = new WeakHashMap<>();

    /**
     * Passes {@code saved} through; when a chance charm (Gilded Ward, Rift, Memory) has just saved {@code holder}, the
     * charm chimes and sparks round it, so the save reads as the charm's and not as luck.
     */
    public static boolean felt(LivingEntity holder, boolean saved) {
        if (!saved || !(holder.level() instanceof ServerLevel level)) return saved;
        long now = level.getGameTime();
        Long last = FELT.get(holder);
        if (last != null && now - last < FELT_COOLDOWN) return true;
        FELT.put(holder, now);
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, holder.getSoundSource(), 1.2F, 1.4F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, holder.getX(), holder.getY() + holder.getBbHeight() * 0.6, holder.getZ(), 12, 0.4, 0.4, 0.4, 0.1);
        return true;
    }

    /** The game time {@code holder} last heard its charm save it, or -1. Public as a test hook. */
    public static long lastFelt(LivingEntity holder) {
        return FELT.getOrDefault(holder, -1L);
    }

    /** The share of a charge's or ram's throw that reaches {@code victim}: halved by the Shellward Charm. */
    public static double chargeThrow(LivingEntity victim) {
        return holds(victim, ModCharms.SHELLWARD_CHARM) ? SHELLWARD_THROW : 1.0;
    }

    /** The share of a Teeming mob's freeze build-up that reaches {@code target}: halved by the Hearth Charm. */
    public static double freezeBuildUp(LivingEntity target) {
        return holds(target, ModCharms.HEARTH_CHARM) ? HEARTH_FREEZE : 1.0;
    }

    /** The share of its healing a frost tether on {@code target} gives its owner: halved by the Hearth Charm. */
    public static double frostTetherHeal(LivingEntity target) {
        return holds(target, ModCharms.HEARTH_CHARM) ? HEARTH_TETHER_HEAL : 1.0;
    }

    /** The share of a sound hunter's mining range at which {@code miner} is heard: none with the Hush Charm. */
    public static double miningHeard(LivingEntity miner) {
        return holds(miner, ModCharms.HUSH_CHARM) ? HUSH_MINING : 1.0;
    }

    /** The share of its length that Poison from a Teeming mob lasts on {@code target}: halved by the Antivenom Charm. */
    public static double poisonLength(LivingEntity target) {
        return holds(target, ModCharms.ANTIVENOM_CHARM) ? ANTIVENOM_POISON : 1.0;
    }

    /** Ticks a Teeming mob's Poison of {@code ticks} lasts on {@code target}, after the Antivenom Charm. */
    public static int poisonTicks(LivingEntity target, int ticks) {
        return (int) Math.round(ticks * poisonLength(target));
    }

    /** Heat a hit from {@code attacker} takes off a heat-builder: {@link #QUENCH_COOL} from a Quench Charm holder, else 0 (the hit heats it). */
    public static int hitCools(@Nullable Entity attacker) {
        return attacker instanceof LivingEntity holder && holds(holder, ModCharms.QUENCH_CHARM) ? QUENCH_COOL : 0;
    }

    /** A burning patch has just set {@code burning} on fire for {@code fireTicks}: until that fire is out it burns with a patch's fire. */
    public static void litByPatch(LivingEntity burning, int fireTicks) {
        if (burning.level().isClientSide()) return;
        PATCH_FIRE.put(burning, burning.level().getGameTime() + fireTicks);
    }

    /** A burn of {@code amount} on {@code burning}: halved by the Quench Charm while it burns with a patch's fire. */
    public static float patchBurn(LivingEntity burning, float amount) {
        Long out = PATCH_FIRE.get(burning);
        if (out == null) return amount;
        if (burning.level().getGameTime() > out) {
            PATCH_FIRE.remove(burning);
            return amount;
        }
        return holds(burning, ModCharms.QUENCH_CHARM) ? amount * QUENCH_PATCH_BURN : amount;
    }

    /** True when a tether that has held {@code victim} for {@code heldTicks} snaps: at {@link #TETHER_SNAP_TICKS} on a Tether Charm holder. */
    public static boolean tetherSnaps(LivingEntity victim, int heldTicks) {
        return heldTicks >= TETHER_SNAP_TICKS && holds(victim, ModCharms.TETHER_CHARM);
    }

    /** True when a theft from {@code victim} fails: a Gilded Ward Charm holder, and the thief's {@code random} rolls under {@link #GILDED_WARD_FAIL}. */
    public static boolean theftFails(LivingEntity victim, RandomSource random) {
        return felt(victim, holds(victim, ModCharms.GILDED_WARD_CHARM) && random.nextFloat() < GILDED_WARD_FAIL);
    }

    /** Ticks Silenced lasts on {@code victim} when its source gives {@code ticks}: at most {@link #CLEAR_VOICE_SILENCED_TICKS} on a Clear Voice Charm holder. */
    public static int silencedTicks(LivingEntity victim, int ticks) {
        return holds(victim, ModCharms.CLEAR_VOICE_CHARM) ? Math.min(ticks, CLEAR_VOICE_SILENCED_TICKS) : ticks;
    }

    // ---- End charms --------------------------------------------------------------------------------------------------

    /**
     * Grounding Charm: the share of an End mob's pull, lift, Levitation or throw that reaches its holder. A pull draws
     * half as fast (the Pullstar's drift, the Abyss Angler's reel) or half as far (the Underclinger's drag); a lift rises
     * half as high (the Liftstone Monolith); Levitation a Teeming mob or its cloud gives (the Levimoth's dust) lasts half
     * as long, so it lifts half as high; a throw, knock-up or launch (Brinkram, Brinkray, Spirebloom, Riftmine Sapper)
     * sets half the motion. Vanilla's own shove from a blow is left as it is. Charges and rams also ask
     * {@link #chargeThrow} (the Shellward Charm) and multiply the two: the off-hand holds one charm, so the product is
     * 0.5 with either and never 0.25 (see {@link #endChargeThrow}).
     */
    public static final double GROUNDING = 0.5;

    /** The share of an End mob's pull, lift or throw that reaches {@code target}: halved by the Grounding Charm. */
    public static double grounded(LivingEntity target) {
        return holds(target, ModCharms.GROUNDING_CHARM) ? GROUNDING : 1.0;
    }

    /**
     * The share of an End charge's or ram's throw that reaches {@code victim}: halved by the Shellward Charm (charges and
     * rams) or the Grounding Charm (End knockback). Only one charm fits the off-hand, so they never stack.
     */
    public static double endChargeThrow(LivingEntity victim) {
        return chargeThrow(victim) * grounded(victim);
    }

    /**
     * Levitation as it lands on {@code target}: {@code incoming} itself, or for a Grounding Charm holder Levitation from
     * a Teeming mob (given by the mob itself or by a cloud it owns) with {@link #GROUNDING} of its length (at least a
     * tick), the same strength and look.
     */
    public static MobEffectInstance levitation(LivingEntity target, MobEffectInstance incoming, @Nullable Entity source) {
        if (!incoming.is(MobEffects.LEVITATION) || incoming.isInfiniteDuration() || !fromTeemingMob(source) || grounded(target) >= 1.0) return incoming;
        int ticks = Math.max(1, (int) Math.round(incoming.getDuration() * GROUNDING));
        return new MobEffectInstance(incoming.getEffect(), ticks, incoming.getAmplifier(), incoming.isAmbient(), incoming.isVisible(), incoming.showIcon());
    }

    private static boolean fromTeemingMob(@Nullable Entity source) {
        return source instanceof TeemingCreature || source instanceof AreaEffectCloud cloud && cloud.getOwner() instanceof TeemingCreature;
    }

    /**
     * Rift Charm: the chance a forced teleport from a Teeming mob fails on its holder, rolled once per teleport on the
     * mob's own random: a warpseed's throw, a step into a Riftjackal's rift (one roll per step in, not per tick inside),
     * an Exile Wraith's mark, and a Chorus Husk's death burst (a chorus-fruit teleport it forces on whoever stands near).
     * A teleport a mob makes of itself, or a hold that moves its victim, is no forced teleport.
     */
    public static final double RIFT_FAIL = 0.5;

    /**
     * Rift Charm: an exit rift aimed at its holder opens this many ticks earlier before its bolt (the Gatecaster's gate):
     * its tell runs this much longer, the extra time spent locked on the line of fire.
     */
    public static final int RIFT_EARLY = 10;

    /** True when a forced teleport of {@code target} fails: the Rift Charm's {@link #RIFT_FAIL}, rolled on {@code random}. */
    public static boolean teleportFails(LivingEntity target, RandomSource random) {
        return felt(target, holds(target, ModCharms.RIFT_CHARM) && random.nextDouble() < RIFT_FAIL);
    }

    /** Ticks an exit rift aimed at {@code target} shows before its bolt, from its usual {@code ticks}: the Rift Charm adds {@link #RIFT_EARLY}. */
    public static int exitRiftTicks(@Nullable LivingEntity target, int ticks) {
        return target != null && holds(target, ModCharms.RIFT_CHARM) ? ticks + RIFT_EARLY : ticks;
    }

    /** Rot Ward Charm: the share of its length that Void Rot or Heavy from a Teeming mob lasts on its holder. */
    public static final double ROT_WARD = 0.5;

    /** Ticks a Teeming mob's Void Rot or Heavy of {@code ticks} lasts on {@code target}, after the Rot Ward Charm. */
    public static int rotTicks(LivingEntity target, int ticks) {
        return holds(target, ModCharms.ROT_WARD_CHARM) ? (int) Math.round(ticks * ROT_WARD) : ticks;
    }

    /**
     * Memory Charm: the chance a Teeming mob's theft of its holder's experience fails (the Mnemovore's bite still lands,
     * but takes no level), rolled once per theft on the mob's own random.
     */
    public static final double MEMORY_FAIL = 0.5;

    /** True when a theft of {@code target}'s experience fails: the Memory Charm's {@link #MEMORY_FAIL}, rolled on {@code random}. */
    public static boolean xpTheftFails(LivingEntity target, RandomSource random) {
        return felt(target, holds(target, ModCharms.MEMORY_CHARM) && random.nextDouble() < MEMORY_FAIL);
    }

    /**
     * Memory Charm: under enchantment suppression (a Nullcantor's ring, the Nulled status) its holder's gear keeps this
     * share of each enchantment's level instead of none, rounded up, so every enchantment keeps at least level I:
     * Protection IV works as II, Sharpness V as III, Mending I as I. Curses hold either way.
     */
    public static final double MEMORY_LEVELS = 0.5;

    /** True when {@code holder}'s suppressed enchantments keep half their levels: the Memory Charm. */
    public static boolean halvesNulling(LivingEntity holder) {
        return holds(holder, ModCharms.MEMORY_CHARM);
    }

    /** An enchantment's {@code level} under suppression for a Memory Charm holder: {@link #MEMORY_LEVELS} of it, rounded up. */
    public static int nulledLevel(int level) {
        return level <= 0 ? 0 : (int) Math.ceil(level * MEMORY_LEVELS);
    }
}
