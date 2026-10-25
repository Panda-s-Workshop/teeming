package com.panda.teeming.mob.nether;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Aurovore: a small, greedy, gold-crested lizard of the bastions and the Nether wastes that sniffs out gold.
 *
 * <p>It never simply bites. With its target next to it, it stops dead, stretches its neck at it and sniffs for 10
 * ticks (tell: the nose twitches and the crest rises, a sniffing sound, and the gold it has picked out glints on the
 * target as item flecks), then it bites for its attack damage. A bite that lands takes one item of
 * {@code #teeming:aurovore_loot} out of the victim's pack (a player's inventory or a mob's own inventory): the first
 * such stack in slot order, one item of it. It never takes worn armour or anything in either hand. With an item taken
 * it runs off from its victim for 200 ticks; a bite that found nothing to take is just a bite, and it fights on.
 *
 * <p>It keeps everything it ever took (its hoard) and never despawns while it holds any. What it holds is on show: the
 * newest piece in its jaws and the two pieces before it on its back, as small item displays riding it.
 *
 * <p>Counter: kill it before it gets away. Killed (or removed for good any other way) it drops its whole hoard. Failing
 * it: the stolen gold is gone with it.
 */
public class Aurovore extends TeemingMonster {
    /** IDLE, the sniff (tell), a bite that took nothing, running off with what it took. */
    public enum Step implements Phase { IDLE, SNIFF, BITE, FLEE }

    /** What it steals; the tag ships with every gold item piglins love, plus gold nuggets. */
    public static final TagKey<Item> LOOT = TagKey.create(Registries.ITEM, TeemingMod.id("aurovore_loot"));

    public static final int TELL_TICKS = 10;              // the sniff: 0.5 s
    public static final int BITE_TICKS = 6;               // the jaws stay shut this long after a bite that took nothing
    public static final int FLEE_TICKS = 200;             // it runs off with what it took for 10 s
    public static final int STEAL_COUNT = 1;              // items taken per bite that lands
    public static final float BITE = 1.0F;                // its bite: its attack damage, as is
    public static final int COOLDOWN = 20;                // ticks from the end of one bite (or run) to the next sniff
    public static final double START_REACH = 0.8;         // blocks between hitboxes: this close it starts to sniff
    public static final double BITE_REACH = 1.2;          // blocks between hitboxes: a victim this close when it bites is bitten
    public static final float BITE_ARC = 120.0F;          // degrees in front of it the bite covers
    public static final float TURN = 15.0F;               // degrees a tick it turns after its target while it sniffs
    public static final double FLEE_SPEED = 1.5;          // speed while it runs off, as a multiple of its walk
    public static final int SHOWN = 3;                    // pieces of its hoard on show: one in its jaws, two on its back

    /** {@code MobCore} flag bit set while it holds anything, so clients hold its jaw on the newest piece. */
    public static final int CARRY_FLAG = 0;

    /** Marks the item displays that show its hoard; the number after it is the place (0 the jaws, 1 and 2 the back). */
    private static final String SHOW_TAG = "teeming.aurovore_show";
    /** Where each piece on show sits, from its feet facing forward (blocks, at scale 1): forward, up, to its left. */
    private static final double[][] SHOW_AT = {{0.86, 0.47, 0.0}, {0.02, 0.66, 0.13}, {-0.24, 0.62, -0.12}};
    private static final float[] SHOW_SIZE = {0.4F, 0.34F, 0.3F};

    /** What it took, oldest first; the newest piece is always the last stack. */
    private final List<ItemStack> hoard = new ArrayList<>();
    private int cooldown;
    private @Nullable LivingEntity fleeFrom;
    private Vec3 fleeSpot = Vec3.ZERO;
    private int fleeRepath;
    private @Nullable AABB confine;
    private int bites;
    private int thefts;

    public Aurovore(EntityType<? extends Aurovore> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("aurovore", 0.27);
    }

    // ---- goals ----------------------------------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** It closes in only: every bite is the sniffed one, started from its AI step. */
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !core().in(Step.FLEE) && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (e, level) -> confined(e.position())) {
            @Override
            public boolean canUse() {
                return !core().in(Step.FLEE) && super.canUse();
            }
        });
    }

    // ---- reach and facing ------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Blocks of open ground between its hitbox and {@code target}'s, across. */
    public double gapTo(Entity target) {
        double across = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        return across - (getBbWidth() + target.getBbWidth()) / 2.0;
    }

    /** True when {@code target} stands inside the arc its bite covers. */
    public boolean inFront(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return true;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.degreesDifferenceAbs(toward, getYRot()) <= BITE_ARC / 2.0F;
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

    /** While it sniffs or bites its body stays square to the way it faces, so its nose points where the bite goes. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && (core().in(Step.SNIFF) || core().in(Step.BITE))) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    /** Where its nose is: just ahead of its head, at about two thirds of its height. */
    private Vec3 nose() {
        Vec3 f = Vec3.directionFromRotation(0.0F, getYRot());
        double reach = 0.9 * getScale();
        return position().add(f.x * reach, 0.5 * getScale(), f.z * reach);
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (core().in(Step.FLEE)) {
            fleeTick();
            return;
        }
        if (!core().idle()) {
            holdStill();
            if (core().in(Step.SNIFF)) sniffTick(level);
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && (!valid(target) || !confined(target.position()))) {
            setTarget(null);
            return;
        }
        if (cooldown == 0 && valid(target) && gapTo(target) <= START_REACH && getSensing().hasLineOfSight(target)) startSniff(target);
    }

    /** Stops and sniffs at {@code target} for {@link #TELL_TICKS}; the bite follows. False if it can't. Public as a test hook. */
    public boolean startSniff(LivingEntity target) {
        if (!core().idle() || !(level() instanceof ServerLevel level) || !startPhase(Step.SNIFF, TELL_TICKS, target)) return false;
        holdStill();
        face(target, 360.0F);
        Vec3 n = nose();
        level.playSound(null, n.x, n.y, n.z, ModSounds.AUROVORE_SNIFF.get(), getSoundSource(), 1.0F, 1.0F);
        Telegraph.particles(ParticleTypes.WHITE_ASH, 10, 0.3, 0.6).play(this);
        glint(level, target, 6);
        return true;
    }

    /** The sniff: it turns after its target, puffs at its nose, and the gold it has picked out glints on the target. */
    private void sniffTick(ServerLevel level) {
        LivingEntity target = core().target();
        if (valid(target)) face(target, TURN);
        int t = core().elapsed();
        if (t % 2 == 0) {
            Vec3 n = nose();
            level.sendParticles(ParticleTypes.WHITE_ASH, n.x, n.y, n.z, 3, 0.08, 0.05, 0.08, 0.01);
            if (valid(target)) glint(level, target, 2);
        }
        if (t == TELL_TICKS / 2) {
            Vec3 n = nose();
            level.playSound(null, n.x, n.y, n.z, ModSounds.AUROVORE_SNIFF.get(), getSoundSource(), 0.7F, 1.25F);
        }
    }

    /** Flecks of the item it would take from {@code target}, glinting on it; nothing when it carries no loot. */
    private void glint(ServerLevel level, LivingEntity target, int count) {
        Loot loot = lootIn(target);
        if (loot == null) return;
        ItemStack stack = loot.container().getItem(loot.slot());
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), target.getX(), target.getY(0.55), target.getZ(),
                count, target.getBbWidth() * 0.35, target.getBbHeight() * 0.2, target.getBbWidth() * 0.35, 0.02);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.SNIFF) {
            bite(level, core().target());
        } else {
            rest();
        }
    }

    /**
     * The bite at the end of the sniff: a target in reach and in front takes its attack damage, and if the bite lands
     * it loses one item of loot and the lizard runs off with it. Anything else is a snap at the air.
     */
    private void bite(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 n = nose();
        level.playSound(null, n.x, n.y, n.z, ModSounds.AUROVORE_BITE.get(), getSoundSource(), 1.0F, getVoicePitch());
        boolean landed = false;
        if (valid(target) && gapTo(target) <= BITE_REACH && inFront(target) && MechanicEvent.post(this, "bite", target)) {
            landed = target.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE);
            if (landed) {
                bites++;
                setLastHurtMob(target);
            }
        }
        if (landed && steal(level, target) && flee(target, target.position())) return;
        if (!startPhase(Step.BITE, BITE_TICKS, target)) rest();
    }

    // ---- stealing --------------------------------------------------------------------------------------------------

    /** One stack it may steal from: the container and the slot. */
    public record Loot(Container container, int slot) {}

    /**
     * The loot it would take from {@code target}: the first stack of {@link #LOOT} in its pack, in slot order. For a
     * player that is the inventory's 36 main slots (the hotbar first) without the selected one, so worn armour, the
     * off hand and the main hand are never touched; for a mob that carries an inventory (a piglin's, an allay's), that
     * inventory, which holds nothing worn or in hand either. Null when there is none.
     */
    public static @Nullable Loot lootIn(LivingEntity target) {
        if (target instanceof Player player) {
            Inventory inv = player.getInventory();
            int size = inv.getNonEquipmentItems().size();
            for (int i = 0; i < size; i++) {
                if (i == inv.getSelectedSlot()) continue;
                if (inv.getItem(i).is(LOOT)) return new Loot(inv, i);
            }
            return null;
        }
        if (target instanceof InventoryCarrier carrier) {
            Container inv = carrier.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(LOOT)) return new Loot(inv, i);
            }
        }
        return null;
    }

    /** Takes {@link #STEAL_COUNT} item of loot from {@code victim} into its hoard; false if it has none (or vetoed). */
    private boolean steal(ServerLevel level, LivingEntity victim) {
        Loot loot = lootIn(victim);
        if (loot == null || !MechanicEvent.post(this, "steal", victim)) return false;
        ItemStack taken = loot.container().removeItem(loot.slot(), STEAL_COUNT);
        if (taken.isEmpty()) return false;
        loot.container().setChanged();
        hoard(taken);
        thefts++;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, taken.getItem()), victim.getX(), victim.getY(0.55), victim.getZ(),
                12, 0.25, 0.25, 0.25, 0.08);
        Vec3 n = nose();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, taken.getItem()), n.x, n.y, n.z, 6, 0.1, 0.1, 0.1, 0.04);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.AUROVORE_STEAL.get(), getSoundSource(), 1.0F, getVoicePitch());
        return true;
    }

    /** Adds a stolen stack to the hoard as its newest piece, joined with a stack of the same item if one has room. */
    private void hoard(ItemStack taken) {
        for (int i = 0; i < hoard.size(); i++) {
            ItemStack s = hoard.get(i);
            if (ItemStack.isSameItemSameComponents(s, taken) && s.getCount() + taken.getCount() <= s.getMaxStackSize()) {
                hoard.remove(i);
                s.grow(taken.getCount());
                hoard.add(s);
                syncHoard();
                return;
            }
        }
        hoard.add(taken.copy());
        syncHoard();
    }

    /** Runs from {@code from} (or the spot {@code fromSpot}) for {@link #FLEE_TICKS}; false if vetoed. Public as a test hook. */
    public boolean flee(@Nullable LivingEntity from, Vec3 fromSpot) {
        if (!startPhase(Step.FLEE, FLEE_TICKS, from)) return false;
        fleeFrom = from;
        fleeSpot = fromSpot;
        fleeRepath = 0;
        setTarget(null);
        getNavigation().stop();
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.AUROVORE_FLEE.get(), getSoundSource(), 1.0F, getVoicePitch());
        }
        return true;
    }

    private void fleeTick() {
        setTarget(null);
        // a new spot every 10 ticks or on arrival; with none found (a cramped spot) it tries again next tick
        if (--fleeRepath > 0 && !getNavigation().isDone()) return;
        // from whoever it ran from; with no one (a run started without a cause) from where it stands, any way at all
        Vec3 threat = fleeFrom != null && fleeFrom.isAlive() ? fleeFrom.position() : fleeSpot.equals(Vec3.ZERO) ? position() : fleeSpot;
        Vec3 away = DefaultRandomPos.getPosAway(this, 16, 7, threat);
        if (away == null) away = DefaultRandomPos.getPosAway(this, 6, 3, threat);   // cornered: a short run beats none
        fleeRepath = away != null && getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED) ? 10 : 0;
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
        fleeFrom = null;
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    // ---- the hoard: keeping it, dropping it ------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel && (tickCount % 20 == 0 || core().flag(CARRY_FLAG) != !hoard.isEmpty())) syncHoard();
    }

    /** Killed, it drops its whole hoard, whatever the loot rules say. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) dropHoard(level);
    }

    /** Removed for good some other way (Peaceful, a command): it still never destroys what it stole. */
    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level && reason.shouldDestroy() && !hoard.isEmpty()) dropHoard(level);
        super.remove(reason);
    }

    /** Drops every stack of its hoard where it stands, in a little burst: the counter's payoff. */
    private void dropHoard(ServerLevel level) {
        if (hoard.isEmpty()) return;
        List<ItemStack> all = new ArrayList<>(hoard);
        hoard.clear();
        syncHoard();
        for (ItemStack stack : all) {
            ItemEntity dropped = spawnAtLocation(level, stack, (float) (getBbHeight() * 0.5));
            if (dropped != null) dropped.setDeltaMovement((random.nextDouble() - 0.5) * 0.2, 0.25, (random.nextDouble() - 0.5) * 0.2);
        }
        double y = getY() + getBbHeight() * 0.5;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, all.getLast().getItem()), getX(), y, getZ(), 14, 0.3, 0.25, 0.3, 0.1);
        level.sendParticles(ParticleTypes.POOF, getX(), y, getZ(), 8, 0.3, 0.2, 0.3, 0.02);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.AUROVORE_DROP.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** It never despawns while it holds anything it stole. */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || !hoard.isEmpty();
    }

    /** Copies of its hoard, oldest first (the newest piece last). */
    public List<ItemStack> hoard() {
        return hoard.stream().map(ItemStack::copy).toList();
    }

    /** How many of {@code item} it holds. */
    public int holds(Item item) {
        int n = 0;
        for (ItemStack s : hoard) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Test hook: takes its whole hoard away from it, so a test's stolen gold never spills into the next test. */
    public List<ItemStack> takeHoard() {
        List<ItemStack> taken = new ArrayList<>(hoard);
        hoard.clear();
        syncHoard();
        return taken;
    }

    /** Test hook: adds a stack to its hoard as its newest piece, as if it had just stolen it (without running off). */
    public void give(ItemStack stack) {
        if (!stack.isEmpty()) hoard(stack.copy());
    }

    /**
     * Test hook: from now on it picks fights only inside {@code box} (null: anywhere in range), so a test's lizard never
     * notices the players of the arenas beside it.
     */
    public void confine(@Nullable AABB box) {
        confine = box;
    }

    private boolean confined(Vec3 at) {
        return confine == null || confine.contains(at);
    }

    /** Bites that landed so far. Public as a test hook. */
    public int bites() {
        return bites;
    }

    /** Items stolen so far. Public as a test hook. */
    public int thefts() {
        return thefts;
    }

    /** Ticks until it may sniff again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it sniff again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- the hoard on show (a self-contained carry display: item displays riding at set places) --------------------

    /**
     * Keeps the displays in step with the hoard: the newest stack shows in its jaws, the two before it on its back, one
     * item display each; none for a place with nothing to show.
     */
    private void syncHoard() {
        core().setFlag(CARRY_FLAG, !hoard.isEmpty() && isAlive());
        if (!(level() instanceof ServerLevel level)) return;
        Display.ItemDisplay[] shown = new Display.ItemDisplay[SHOWN];
        for (Entity p : getPassengers()) {
            if (!(p instanceof Display.ItemDisplay d)) continue;
            int place = placeOf(d);
            if (place < 0) continue;
            if (shown[place] == null) shown[place] = d;
            else d.discard();
        }
        for (int place = 0; place < SHOWN; place++) {
            int index = hoard.size() - 1 - place;
            ItemStack want = isAlive() && index >= 0 ? hoard.get(index) : ItemStack.EMPTY;
            Display.ItemDisplay d = shown[place];
            if (want.isEmpty()) {
                if (d != null) d.discard();
                continue;
            }
            if (d == null) d = newShowDisplay(level, place, want);
            if (d != null && !ItemStack.isSameItemSameComponents(d.getSlot(0).get(), want)) d.getSlot(0).set(want.copyWithCount(1));
        }
    }

    /** Which place a display of its hoard shows (0 the jaws, 1 and 2 the back), or -1 for a display not its own. */
    private static int placeOf(Display.ItemDisplay d) {
        for (int place = 0; place < SHOWN; place++) if (d.entityTags().contains(SHOW_TAG + place)) return place;
        return -1;
    }

    /** One piece on show: always facing the viewer about the vertical, drawn small. */
    private Display.@Nullable ItemDisplay newShowDisplay(ServerLevel level, int place, ItemStack stack) {
        Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.LOAD);
        if (d == null) return null;
        float size = SHOW_SIZE[place];
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        out.store("transformation", Transformation.EXTENDED_CODEC, new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(size, size, size), new Quaternionf()));
        out.store("billboard", Display.BillboardConstraints.CODEC, Display.BillboardConstraints.VERTICAL);
        out.store("item_display", ItemDisplayContext.CODEC, ItemDisplayContext.FIXED);
        d.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
        d.addTag(SHOW_TAG + place);
        d.getSlot(0).set(stack.copyWithCount(1));
        d.snapTo(getX(), getY() + SHOW_AT[place][1], getZ(), getYRot(), 0.0F);
        if (!level.addFreshEntity(d)) return null;
        if (!d.startRiding(this, true, false)) {
            d.discard();
            return null;
        }
        return d;
    }

    /** Each piece on show sits at its place, turned with its body. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int place = passenger instanceof Display.ItemDisplay d ? placeOf(d) : -1;
        if (place < 0) return super.getPassengerAttachmentPoint(passenger, dimensions, scale);
        double[] at = SHOW_AT[place];
        return new Vec3(at[2] * scale, at[1] * scale, at[0] * scale).yRot(-yBodyRot * Mth.DEG_TO_RAD);
    }

    /** A display that leaves it (it died, or something pulled it off) goes; one that unloads with it stays. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (level().isClientSide() || passenger.isRemoved() || !(passenger instanceof Display.ItemDisplay d) || placeOf(d) < 0) return;
        RemovalReason reason = getRemovalReason();
        if (reason == null || reason.shouldDestroy()) d.discard();
    }

    // ---- save and load ---------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        var list = output.list("Hoard", ItemStack.CODEC);
        for (ItemStack s : hoard) if (!s.isEmpty()) list.add(s);
    }

    /** Its hoard survives a reload; the displays come back with it, or are made again. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        hoard.clear();
        input.list("Hoard", ItemStack.CODEC).ifPresent(l -> l.forEach(hoard::add));
        core().setFlag(CARRY_FLAG, !hoard.isEmpty());
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.AUROVORE_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.AUROVORE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.AUROVORE_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.0F + random.nextFloat() * 0.15F;
    }
}
