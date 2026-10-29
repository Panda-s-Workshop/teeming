package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.nether.ScarletMantis;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Facet Doppel: a broad humanoid of faceted mirror-glass from the End midlands, cold and empty. It has no attack and
 * no armour of its own: it takes them from the first player it sees.
 *
 * <p>The copy. The first time a player is in its sight within its follow range (any player but a spectator: a mirror
 * shows everyone), it stops and flickers for 20 ticks (the tell): its outer facets blink out and back, its facets
 * shimmer, flecks in that player's colours spin off it and it hums like a wet finger on glass. When the flicker ends, if
 * that player is still in sight, it rings like struck glass and copies them, once and for good: its armour becomes their
 * armour value at that moment (the total of the armour attribute), and its attack damage becomes the attack damage of
 * the item in their main hand, read from the item's own attribute modifiers on top of a bare hand's 1 (a sword's tooltip
 * number: iron 6, diamond 7). A bare hand copies as a fist, 1. Enchantments and status effects carry nothing over. A
 * glass copy of the item forms in its right hand. A flicker that ends with the player gone copies nothing, and it may
 * flicker again 40 ticks later.
 *
 * <p>Its blows: an 8-tick wind-up (the copy raised overhead, a ring of glass, lilac motes), then a blow for a quarter
 * more than its attack damage (never less than a fist's 1), brought down from overhead, at whatever is still in reach in
 * front of it: the item it copied hits harder in its hands than in yours.
 *
 * <p>Counter: meet it holding something weak, or switch to something weak while it flickers, then switch up. A melee blow
 * from a weapon that outclasses its copy (more attack damage by the same reading) shatters its facets: the chest diamond
 * and both shoulder facets break off with a crash of glass, and the armour it copied is gone for good. Its copied attack
 * stays. Failing it: it fights with your own armour and your own weapon, and no blow you can deal outclasses it.
 *
 * <p>The copy, the shattered facets and the blow's cooldown survive a reload; a flicker or wind-up under way does not.
 */
public class FacetDoppel extends TeemingMonster {
    /** At rest, the flicker on first sight (the tell), the wind-up before a blow, the blow. */
    public enum Step implements Phase { IDLE, TELL, WINDUP, STRIKE }

    /** The glass copy in its right hand; synced in the low bits of the flags, so the order is fixed. */
    public enum Held {
        NONE, FIST, SWORD, AXE, PICKAXE, SHOVEL, HOE, TRIDENT, MACE, SPEAR, ROD, CHUNK;

        private static final Held[] ALL = values();

        /** The copy that stands for {@code stack}: a bare hand is a fist, a block a glass cube, any other item a rod. */
        public static Held of(ItemStack stack) {
            if (stack.isEmpty()) return FIST;
            if (stack.is(ItemTags.SWORDS)) return SWORD;
            if (stack.is(ItemTags.AXES)) return AXE;
            if (stack.is(ItemTags.PICKAXES)) return PICKAXE;
            if (stack.is(ItemTags.SHOVELS)) return SHOVEL;
            if (stack.is(ItemTags.HOES)) return HOE;
            if (stack.is(ItemTags.SPEARS)) return SPEAR;
            if (stack.is(Items.TRIDENT)) return TRIDENT;
            if (stack.is(Items.MACE)) return MACE;
            if (stack.getItem() instanceof BlockItem) return CHUNK;
            return ROD;
        }

        /** The copy a flags value holds (both sides). */
        public static Held from(int flags) {
            int i = flags & KIND_MASK;
            return i < ALL.length ? ALL[i] : NONE;
        }
    }

    /** The flicker: 20 ticks between first sight and the copy. */
    public static final int TELL_TICKS = 20;
    /** A bare hand's attack damage, and the floor under every reading: a fist deals 1. */
    public static final double FIST_DAMAGE = 1.0;
    /** A flicker that copied nothing: this long before it may flicker again. */
    public static final int RETRY_TICKS = 40;

    public static final int WINDUP_TICKS = 8;             // 0.4 s with the copy raised overhead before the blow
    public static final int STRIKE_TICKS = 6;             // the blow lands on its first tick; the arm comes down this long
    public static final int COOLDOWN = 20;                // ticks from the end of one blow to the next wind-up
    public static final float STRIKE = 1.25F;             // x attack damage: the overhead blow lands a quarter harder
    public static final double START_REACH = 0.8;         // blocks between hitboxes: this close it winds up
    public static final double STRIKE_REACH = 1.4;        // blocks between hitboxes: a target this close when the blow lands is hit
    public static final float STRIKE_ARC = 120.0F;        // degrees in front of it the blow covers
    public static final float TURN = 15.0F;               // degrees a tick it turns after its target while winding up

    /** Glow stages: nothing lit, and the facets shimmering through the flicker (lilac and star-white, never gold). */
    public static final int GLOW_REST = 0;
    public static final int GLOW_FLICKER = 1;

    /** Flags: the copy held (bits 0-3), copied at all (bit 4), facets shattered (bit 5). */
    public static final int KIND_MASK = 0xF;
    public static final int FLAG_COPIED = 4;
    public static final int FLAG_SHATTERED = 5;

    private static final Identifier COPY_MODIFIER = TeemingMod.id("facet_doppel_copy");
    private static final DustParticleOptions LILAC = new DustParticleOptions(0xD8B4FF, 1.0F);
    private static final DustParticleOptions STAR = new DustParticleOptions(0xF8F4FF, 0.7F);
    private static final DustParticleOptions RING = new DustParticleOptions(0xE8DCFF, 0.9F);
    /** A bare player's colours: the shirt's cyan and the skin, for a player wearing and holding nothing coloured. */
    private static final int SHIRT = 0x3CA8B8, SKIN = 0xC8946C;

    private boolean copied;
    private double copiedAttack;
    private double copiedArmour;
    private Held held = Held.NONE;
    private boolean shattered;
    private int cooldown;
    private int retry;
    private @Nullable List<DustParticleOptions> shimmer;
    private int copies;
    private int shatters;
    private int strikes;

    public FacetDoppel(EntityType<? extends FacetDoppel> type, Level level) {
        super(type, level, Step.values());
        xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("facet_doppel", 0.26);
    }

    @Override
    public int visualStages() {
        return 2;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** It closes in only: every blow is the wind-up and strike, started from its AI step. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Every phase it enters sets its glow: the facets shimmer through the flicker, unlit otherwise. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.TELL ? GLOW_FLICKER : GLOW_REST);
        return true;
    }

    private void rest() {
        core().end();
        core().setStage(GLOW_REST);
    }

    // ---- the copy --------------------------------------------------------------------------------------------------

    /**
     * The attack damage {@code stack} gives in a main hand: a bare hand's 1 plus the item's own attack-damage modifiers
     * (its tooltip number; iron sword 6, diamond sword 7). Enchantments are not modifiers of the item, so they add
     * nothing; never below 0.
     */
    public static double attackOf(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return Math.max(0.0, modifiers.compute(Attributes.ATTACK_DAMAGE, FIST_DAMAGE, EquipmentSlot.MAINHAND));
    }

    /** True once it has copied someone. Both sides. */
    public boolean copied() {
        return copied || core().flag(FLAG_COPIED);
    }

    /** The copy in its hand. Both sides. */
    public Held held() {
        return Held.from(core().flags());
    }

    /** True once its facets have shattered. Both sides. */
    public boolean shattered() {
        return shattered || core().flag(FLAG_SHATTERED);
    }

    /** The attack damage it copied (0 before it copies). Server only. Public as a test hook. */
    public double copiedAttack() {
        return copied ? copiedAttack : 0.0;
    }

    /** The armour it copied (0 before it copies). Server only. Public as a test hook. */
    public double copiedArmour() {
        return copied ? copiedArmour : 0.0;
    }

    /** Whom it would flicker at now: the nearest player but a spectator, alive, within follow range and in sight. */
    private @Nullable Player spot(ServerLevel level) {
        double range = getAttributeValue(Attributes.FOLLOW_RANGE);
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(range), p -> canCopy(p, range))) {
            double d = distanceToSqr(p);
            if (d < bestDist && getSensing().hasLineOfSight(p)) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    private boolean canCopy(@Nullable Player p, double range) {
        return p != null && p.isAlive() && !p.isRemoved() && !p.isSpectator() && p.level() == level() && distanceTo(p) <= range;
    }

    /** Starts the flicker at {@code player}: it stops and faces them, and copies them 20 ticks on. Public as a test hook. */
    public boolean flicker(Player player) {
        if (copied || !core().idle() || !(level() instanceof ServerLevel level)) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, player)) {
            retry = RETRY_TICKS;
            return false;
        }
        holdStill();
        shimmer = colours(player);
        getLookControl().setLookAt(player, 60.0F, 60.0F);
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.FACET_DOPPEL_FLICKER.get(), getSoundSource(), 1.0F, 1.0F);
        Telegraph.particles(LILAC, 10, 0.4, 0.6).play(this);
        return true;
    }

    /** The flicker: it holds still facing them while flecks in their colours and star-white glints spin off its facets. */
    private void flickerTick(ServerLevel level) {
        LivingEntity who = core().target();
        if (who != null) getLookControl().setLookAt(who, 60.0F, 60.0F);
        int t = core().elapsed();
        if (t % 2 != 0) return;
        List<DustParticleOptions> colours = shimmer != null ? shimmer : List.of(LILAC);
        double a = t * 0.7;
        for (int i = 0; i < 3; i++) {
            double ang = a + i * (Math.PI * 2 / 3);
            double y = getY() + getBbHeight() * (0.35 + 0.2 * i);
            level.sendParticles(colours.get((t / 2 + i) % colours.size()), getX() + Math.cos(ang) * 0.55, y, getZ() + Math.sin(ang) * 0.55,
                    1, 0.02, 0.02, 0.02, 0.0);
        }
        if (t % 4 == 0) level.sendParticles(STAR, getX(), getY() + getBbHeight() * 0.7, getZ(), 2, 0.3, 0.4, 0.3, 0.0);
    }

    /**
     * The flicker ran out: if its player is still there and in sight, it copies them; otherwise it copies nothing and may
     * flicker again {@link #RETRY_TICKS} later.
     */
    private void endFlicker(ServerLevel level, @Nullable LivingEntity who) {
        rest();
        shimmer = null;
        if (!copied && who instanceof Player p && canCopy(p, getAttributeValue(Attributes.FOLLOW_RANGE)) && hasLineOfSight(p)
                && MechanicEvent.post(this, "copy", p)) {
            copyFrom(level, p);
        } else if (!copied) {
            retry = RETRY_TICKS;
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.7), getZ(), 6, 0.25, 0.3, 0.25, 0.01);
        }
    }

    /** Copies {@code player} now: their armour value, their held item's attack damage, a glass copy of the item. */
    private void copyFrom(ServerLevel level, Player player) {
        ItemStack stack = player.getMainHandItem();
        setCopy(attackOf(stack), player.getAttributeValue(Attributes.ARMOR), Held.of(stack), false);
        copies++;
        double y = getY() + getBbHeight() * 0.55;
        for (int i = 0; i < 28; i++) {
            double a = i * (Math.PI * 2 / 28);
            level.sendParticles(RING, true, true, getX() + Math.cos(a) * 1.1, y, getZ() + Math.sin(a) * 1.1, 1, 0.0, 0.0, 0.0, 0.0);
        }
        Vec3 hand = hand();
        level.sendParticles(STAR, true, true, hand.x, hand.y, hand.z, 10, 0.15, 0.3, 0.15, 0.0);
        level.playSound(null, getX(), y, getZ(), ModSounds.FACET_DOPPEL_COPY.get(), getSoundSource(), 1.2F, 1.0F);
    }

    /**
     * Sets the copy as if it had copied these numbers and that item (a reload, or a test that needs no player); with
     * {@code broken} its facets are already shattered and the armour is not applied. Public as a test hook.
     */
    public void setCopy(double attack, double armour, Held item, boolean broken) {
        copied = true;
        copiedAttack = Math.max(0.0, attack);
        copiedArmour = Math.max(0.0, armour);
        held = item;
        shattered = broken;
        applyCopy();
        syncFlags();
    }

    /** The copy as permanent modifiers on its attack and armour (replacing any before). */
    private void applyCopy() {
        AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance armour = getAttribute(Attributes.ARMOR);
        if (attack != null) {
            attack.removeModifier(COPY_MODIFIER);
            if (copied) attack.addOrReplacePermanentModifier(new AttributeModifier(COPY_MODIFIER, copiedAttack, AttributeModifier.Operation.ADD_VALUE));
        }
        if (armour != null) {
            armour.removeModifier(COPY_MODIFIER);
            if (copied && !shattered) armour.addOrReplacePermanentModifier(new AttributeModifier(COPY_MODIFIER, copiedArmour, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void syncFlags() {
        int kind = held.ordinal();
        for (int bit = 0; bit < 4; bit++) core().setFlag(bit, (kind >> bit & 1) != 0);
        core().setFlag(FLAG_COPIED, copied);
        core().setFlag(FLAG_SHATTERED, shattered);
    }

    /** Flecks in the player's colours: their chestplate's and held item's, or a bare player's shirt and skin. */
    private static List<DustParticleOptions> colours(Player player) {
        List<DustParticleOptions> out = new ArrayList<>();
        for (ItemStack stack : new ItemStack[] {player.getItemBySlot(EquipmentSlot.CHEST), player.getItemBySlot(EquipmentSlot.HEAD), player.getMainHandItem()}) {
            int c = colourOf(stack);
            if (c >= 0) out.add(new DustParticleOptions(c, 1.0F));
        }
        if (out.isEmpty()) {
            out.add(new DustParticleOptions(SHIRT, 1.0F));
            out.add(new DustParticleOptions(SKIN, 1.0F));
        }
        out.add(LILAC);
        return out;
    }

    /** A worn or held item's colour by what it is made of (dyed leather by its dye), or -1 for none. Gold reads as pale cream. */
    private static int colourOf(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        if (stack.has(DataComponents.DYED_COLOR)) return DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR) & 0xFFFFFF;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.startsWith("netherite_")) return 0x5A4A58;
        if (path.startsWith("diamond_")) return 0x5CE6E0;
        if (path.startsWith("iron_")) return 0xD8D8DC;
        if (path.startsWith("golden_")) return 0xEFE6B8;
        if (path.startsWith("copper_")) return 0xC87A52;
        if (path.startsWith("chainmail_")) return 0x9AA0AA;
        if (path.startsWith("leather_")) return 0xA06A40;
        if (path.startsWith("turtle_")) return 0x4CA048;
        if (path.startsWith("stone_")) return 0x8A8A8A;
        if (path.startsWith("wooden_")) return 0x9A7448;
        return -1;
    }

    // ---- the counter: a weapon that outclasses the copy shatters its facets ------------------------------------------

    /** A melee blow lands; if the attacker's weapon outclasses its copy, its facets shatter (once). */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && copied && !shattered && ScarletMantis.melee(source) && source.getEntity() instanceof LivingEntity attacker
                && outclasses(attacker.getMainHandItem())) {
            shatter(level, attacker);
        }
        return hurt;
    }

    /** True when {@code weapon} deals more than the copy it holds, by the same reading. Server only. Public as a test hook. */
    public boolean outclasses(ItemStack weapon) {
        return copied && attackOf(weapon) > copiedAttack + 1.0E-6;
    }

    /** The facets break: the copied armour is gone for good, with a crash of glass. */
    private void shatter(ServerLevel level, LivingEntity attacker) {
        if (!MechanicEvent.post(this, "shatter", attacker)) return;
        shattered = true;
        shatters++;
        applyCopy();
        syncFlags();
        double y = getY() + getBbHeight() * 0.72;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), true, true, getX(), y, getZ(),
                30, 0.5, 0.3, 0.5, 0.15);
        level.sendParticles(STAR, true, true, getX(), y, getZ(), 14, 0.6, 0.3, 0.6, 0.0);
        level.sendParticles(LILAC, true, true, getX(), y, getZ(), 10, 0.5, 0.3, 0.5, 0.0);
        level.playSound(null, getX(), y, getZ(), ModSounds.FACET_DOPPEL_SHATTER.get(), getSoundSource(), 1.3F, 1.0F);
    }

    // ---- the blow --------------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Blocks between its hitbox and {@code target}'s, across or up and down, whichever is more. */
    public double gapTo(Entity target) {
        return MobCore.gap(this, target);
    }

    /** True when {@code target} stands inside the arc its blow covers. */
    public boolean inFront(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return true;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.degreesDifferenceAbs(toward, getYRot()) <= STRIKE_ARC / 2.0F;
    }

    private void face(Entity target, float maxTurn) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** While a blow runs its body stays square to the way it faces. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && (core().in(Step.WINDUP) || core().in(Step.STRIKE))) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (retry > 0) retry--;
        if (core().in(Step.TELL)) {
            holdStill();
            flickerTick(level);
            return;
        }
        if (!core().idle()) {
            holdStill();
            if (core().in(Step.WINDUP)) windupTick(level);
            return;
        }
        if (!copied && retry == 0) {
            Player seen = spot(level);
            if (seen != null && flicker(seen)) return;
        }
        LivingEntity target = getTarget();
        if (cooldown == 0 && valid(target) && gapTo(target) <= START_REACH && getSensing().hasLineOfSight(target)) startWindup(target);
    }

    /** Raises its copy at {@code target} for {@link #WINDUP_TICKS}; the blow follows. Public as a test hook. */
    public boolean startWindup(LivingEntity target) {
        if (!core().idle() || !(level() instanceof ServerLevel level) || !startPhase(Step.WINDUP, WINDUP_TICKS, target)) return false;
        holdStill();
        face(target, 360.0F);
        level.playSound(null, getX(), getY(0.8), getZ(), ModSounds.FACET_DOPPEL_WINDUP.get(), getSoundSource(), 1.0F, 1.0F);
        Telegraph.particles(LILAC, 8, 0.35, 1.0).play(this);
        return true;
    }

    /** The wind-up: it turns after its target, glints running up the raised copy. */
    private void windupTick(ServerLevel level) {
        LivingEntity target = core().target();
        if (valid(target)) face(target, TURN);
        if (core().elapsed() % 2 == 0) {
            Vec3 at = position().add(Vec3.directionFromRotation(0.0F, yBodyRot + 90.0F).scale(0.45)).add(0.0, getBbHeight() * 1.05, 0.0);
            level.sendParticles(STAR, true, true, at.x, at.y, at.z, 2, 0.12, 0.15, 0.12, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            endFlicker(level, core().target());
        } else if (ended == Step.WINDUP) {
            strike(level, core().target());
        } else {
            rest();
            cooldown = COOLDOWN;
        }
    }

    /** The damage of one blow: its attack damage (the copy), never less than a fist's 1, times {@link #STRIKE}. */
    public float blowDamage() {
        return (float) Math.max(FIST_DAMAGE, getAttributeValue(Attributes.ATTACK_DAMAGE)) * STRIKE;
    }

    /** The blow: its damage to a target still in reach and in front; anything else is a swing at the air. */
    private void strike(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.STRIKE, STRIKE_TICKS, target)) {
            rest();
            cooldown = COOLDOWN;
            return;
        }
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.FACET_DOPPEL_STRIKE.get(), getSoundSource(), 1.0F, 1.0F);
        if (!valid(target) || gapTo(target) > STRIKE_REACH || !inFront(target) || !MechanicEvent.post(this, "strike", target)) return;
        if (AstralAugur.GroundMarks.hurtInPlace(level, target, damageSources().mobAttack(this), blowDamage())) {
            strikes++;
            setLastHurtMob(target);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
        }
    }

    /** Where its right fist is, for the glints when the copy forms. */
    private Vec3 hand() {
        return position().add(Vec3.directionFromRotation(0.0F, yBodyRot + 90.0F).scale(0.42)).add(0.0, getBbHeight() * 0.45, 0.0);
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Times it copied someone (0 or 1). Public as a test hook. */
    public int copies() {
        return copies;
    }

    /** Times its facets shattered (0 or 1). Public as a test hook. */
    public int shatters() {
        return shatters;
    }

    /** Blows that landed so far. Public as a test hook. */
    public int strikes() {
        return strikes;
    }

    /** Ticks until it may flicker again after a flicker that copied nothing. Public as a test hook. */
    public int retry() {
        return retry;
    }

    // ---- saving ----------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Copied", copied);
        output.putDouble("CopiedAttack", copiedAttack);
        output.putDouble("CopiedArmour", copiedArmour);
        output.putString("Held", held.name());
        output.putBoolean("Shattered", shattered);
        output.putInt("Cooldown", cooldown);
    }

    /** The copy, the held item, the shattered facets and the blow's cooldown survive a reload; a flicker does not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cooldown = Math.clamp(input.getIntOr("Cooldown", 0), 0, COOLDOWN);
        if (input.getBooleanOr("Copied", false)) {
            Held item;
            try {
                item = Held.valueOf(input.getStringOr("Held", "FIST"));
            } catch (IllegalArgumentException e) {
                item = Held.FIST;
            }
            setCopy(input.getDoubleOr("CopiedAttack", FIST_DAMAGE), input.getDoubleOr("CopiedArmour", 0.0), item, input.getBooleanOr("Shattered", false));
        }
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.FACET_DOPPEL_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.FACET_DOPPEL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FACET_DOPPEL_DEATH.get();
    }
}
