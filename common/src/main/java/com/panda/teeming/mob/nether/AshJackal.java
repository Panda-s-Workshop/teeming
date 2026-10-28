package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.platform.Services;
import com.panda.teeming.registry.ModSounds;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
 * Ash Jackal: a lean scavenger dog of the Nether wastes that hunts in packs of two or three.
 *
 * <p>Each jackal with an empty mouth goes for the nearest dropped item stack within 10 blocks (one stack each: the
 * nearest free jackal gets each one, and a nearer one takes over a stack another is after), sniffs at it, dips its head
 * for 6 ticks and takes the whole stack in its jaws, then runs off with it for 200 ticks, away from the nearest player.
 * It keeps carrying it after that and never despawns while it does; killed (or removed for good any other way) it drops what
 * it carried. Its packmates leave a stack a dead
 * jackal dropped alone for 200 ticks, so the player who killed it can take it back. It only takes items lying on the
 * ground, never from players or containers, and only where mobs may pick items up at all (the mob griefing rule).
 *
 * <p>In a fight it goes for whoever has the lowest health fraction within 16 blocks and in sight: any survival player,
 * or another mob that is already hurt; every 10 ticks it switches to a weaker one if it sees one. Hit, it turns on its
 * attacker and calls the pack. Its bites are for its attack damage, at most one every 20 ticks.
 *
 * <p>Tell: before every snatch and every bite it stops dead and sniffs for 10 ticks, its nose lifted toward the item
 * or its target and its ears up, with a sniffing sound and puffs of ash at its nose. Nothing is bitten or taken during
 * the sniff; the dip or the bite follows it.
 *
 * <p>Counter: kill the carrier before it gets away; it drops the stack it took. Failing it: the stack is gone with
 * the jackal.
 *
 * <p>What it carries shows as the real item in its jaws: a small item display (an entity with no hitbox) rides at its
 * mouth while it carries something, kept in step with the carried stack and removed with it.
 */
public class AshJackal extends TeemingMonster {
    /** IDLE, the sniff before a snatch or a bite (tell), the dip that takes an item, running off with it. */
    public enum Step implements Phase { IDLE, TELL, SNATCH, FLEE }

    /** The sniff before every snatch and every bite: 0.5 s. */
    public static final int TELL_TICKS = 10;
    /** Ticks from a bite to the next sniff, so with the sniff it bites at most once every 20 ticks. */
    public static final int BITE_REST = 10;

    /** It goes for dropped items this close (blocks). */
    public static final double ITEM_RANGE = 10.0;
    /** How long it runs off with a stack it took, in ticks. */
    public static final int FLEE_TICKS = 200;
    /** It picks its fight among those this close (blocks). */
    public static final double TARGET_RANGE = 16.0;
    /** Ticks between looks for a weaker target. */
    public static final int RETARGET_TICKS = 10;
    /** The dip of the head that takes an item, in ticks. */
    public static final int SNATCH_TICKS = 6;
    /** It takes an item this close to its middle, horizontally (blocks). */
    public static final double SNATCH_REACH = 1.3;
    /** Speed while it runs for an item, as a multiple of its walk. */
    public static final double SNATCH_SPEED = 1.3;
    /** Speed while it runs off with one. */
    public static final double FLEE_SPEED = 1.5;
    /** It gives up on an item it has not reached in this many ticks (out of reach, say), and tries another. */
    public static final int GIVE_UP_TICKS = 100;
    /** Its packmates leave a stack a dead jackal dropped alone for this many ticks. */
    public static final int RECLAIM_TICKS = 200;
    /** Its bite: its attack damage, as is. */
    public static final float BITE = 1.0F;

    /** {@code MobCore} flag bit set while it carries something, so clients hold its jaw on the item. */
    public static final int CARRY_FLAG = 0;

    /** The puffs of ash at the start of its sniff. */
    private static final Telegraph SNIFF_PUFF = Telegraph.particles(ParticleTypes.WHITE_ASH, 10, 0.3, 0.6);

    /** Marks the item display that shows what it carries, and the stacks dead jackals dropped. */
    private static final String CARRY_TAG = "teeming.jackal_carry";
    private static final String DROPPED_TAG = "teeming.jackal_dropped";
    /** Where the carried item sits, from its feet facing forward (blocks, at scale 1), and how big it is drawn. */
    private static final double MOUTH_FORWARD = 0.74;
    private static final double MOUTH_UP = 0.63;
    private static final float CARRY_SIZE = 0.45F;

    private ItemStack carried = ItemStack.EMPTY;
    private @Nullable ItemEntity itemTarget;
    private long nextItemLook;
    private @Nullable LivingEntity fleeFrom;
    private Vec3 fleeSpot = Vec3.ZERO;
    private int fleeRepath;
    private @Nullable AABB confine;
    private boolean sniffingItem;
    private int biteRest;
    private int snatches;
    private int bites;

    public AshJackal(EntityType<? extends AshJackal> type, Level level) {
        super(type, level, Step.values());
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("ash_jackal", 0.32);
    }

    // ---- goals ----------------------------------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SnatchGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** In reach, it sniffs instead of biting at once: the bite follows the sniff. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
                if (biteRest == 0 && canPerformAttack(target)) sniff(target, false);
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new WeakestTargetGoal());
        targetSelector.addGoal(2, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !core().in(Step.FLEE) && super.canUse();
            }
        }.setAlertOthers());
    }

    /** It runs for the nearest free item in range and dips to take it. */
    private final class SnatchGoal extends Goal {
        private int ticks;
        private int repath;

        SnatchGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!carried.isEmpty() || !core().idle() || level().getGameTime() < nextItemLook) return false;
            nextItemLook = level().getGameTime() + RETARGET_TICKS;
            itemTarget = findItem();
            return itemTarget != null;
        }

        @Override
        public boolean canContinueToUse() {
            ItemEntity item = itemTarget;
            return item != null && item.isAlive() && !item.getItem().isEmpty() && carried.isEmpty() && core().idle() && ticks < GIVE_UP_TICKS
                    && item.distanceTo(AshJackal.this) <= ITEM_RANGE + 2.0;
        }

        @Override
        public void start() {
            ticks = 0;
            repath = 0;
        }

        @Override
        public void tick() {
            ItemEntity item = itemTarget;
            if (item == null) return;
            ticks++;
            getLookControl().setLookAt(item, 30.0F, 30.0F);
            if (inReach(item)) {
                getNavigation().stop();
                if (!sniff(null, true)) leaveItem();          // vetoed: leave this one and look again later
                return;
            }
            // the last stretch goes straight at the stack: a path ends once it stands in the stack's block, which can
            // leave it just out of reach (a packmate in the way, the stack near a block edge)
            if (distanceToSqr(item) < 2.5 * 2.5 && (getNavigation().isDone() || getNavigation().isStuck())) {
                getMoveControl().setWantedPosition(item.getX(), item.getY(), item.getZ(), SNATCH_SPEED);
                return;
            }
            if (--repath > 0) return;
            repath = 5;
            getNavigation().moveTo(item.getX(), item.getY(), item.getZ(), SNATCH_SPEED);
        }

        @Override
        public void stop() {
            if (core().idle()) itemTarget = null;      // a sniff or a dip keeps it: the dip takes this item
            getNavigation().stop();
        }
    }

    /** Every few ticks it looks for a weaker target than the one it has, and takes it. */
    private final class WeakestTargetGoal extends Goal {
        private @Nullable LivingEntity pick;
        private long nextLook;

        WeakestTargetGoal() {
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (core().in(Step.FLEE) || level().getGameTime() < nextLook) return false;
            nextLook = level().getGameTime() + RETARGET_TICKS;
            pick = weakest();
            LivingEntity now = getTarget();
            return pick != null && pick != now && (now == null || !now.isAlive() || fraction(pick) < fraction(now));
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            setTarget(pick);
        }
    }

    // ---- the fight ---------------------------------------------------------------------------------------------------

    /**
     * Whom it would go for now: of those within {@link #TARGET_RANGE} and in sight (survival players, and other mobs
     * that are already hurt; never another jackal), the one with the lowest health fraction, the nearest on a tie.
     * Null if there is no one. Server only; public as a test hook.
     */
    public @Nullable LivingEntity weakest() {
        if (!(level() instanceof ServerLevel level)) return null;
        TargetingConditions sight = TargetingConditions.forCombat().range(TARGET_RANGE).selector((e, l) -> candidate(e));
        LivingEntity best = null;
        float bestFraction = Float.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(TARGET_RANGE), e -> e != this)) {
            if (!sight.test(level, this, e)) continue;
            float f = fraction(e);
            if (best == null || f < bestFraction - 1.0E-4F || (Math.abs(f - bestFraction) <= 1.0E-4F && distanceToSqr(e) < distanceToSqr(best))) {
                best = e;
                bestFraction = f;
            }
        }
        return best;
    }

    private boolean candidate(LivingEntity e) {
        if (e instanceof AshJackal || e instanceof ArmorStand || !confined(e.position())) return false;
        return e instanceof Player || e.getHealth() < e.getMaxHealth();
    }

    private static float fraction(LivingEntity e) {
        return e.getMaxHealth() > 0.0F ? e.getHealth() / e.getMaxHealth() : 1.0F;
    }

    /**
     * Stops and sniffs for {@link #TELL_TICKS}, at {@code target} (a bite follows) or, with {@code forItem}, at the item
     * it is going for (the dip follows). False if it can't or the step was vetoed. Public as a test hook.
     */
    public boolean sniff(@Nullable LivingEntity target, boolean forItem) {
        if (!core().idle() || !(level() instanceof ServerLevel level) || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        sniffingItem = forItem;
        holdStill();
        Vec3 n = nose();
        level.playSound(null, n.x, n.y, n.z, ModSounds.ASH_JACKAL_SNIFF.get(), getSoundSource(), 1.0F, getVoicePitch());
        SNIFF_PUFF.play(this);
        return true;
    }

    /** The sniff: it stands still, nose to what it sniffs at, and puffs ash from its nose. */
    private void sniffTick(ServerLevel level) {
        holdStill();
        Entity at = sniffingItem ? itemTarget : core().target();
        if (at != null) getLookControl().setLookAt(at, 30.0F, 60.0F);
        int t = core().elapsed();
        if (t % 3 == 1) {
            Vec3 n = nose();
            level.sendParticles(ParticleTypes.WHITE_ASH, n.x, n.y, n.z, 3, 0.08, 0.05, 0.08, 0.01);
        }
        if (t == TELL_TICKS / 2) {
            Vec3 n = nose();
            level.playSound(null, n.x, n.y, n.z, ModSounds.ASH_JACKAL_SNIFF.get(), getSoundSource(), 0.7F, getVoicePitch() * 1.2F);
        }
    }

    /** The bite at the end of the sniff: a target still in reach takes its attack damage; anything else is a snap at the air. */
    private void bite(ServerLevel level, @Nullable LivingEntity target) {
        core().end();
        biteRest = BITE_REST;
        if (target == null || !target.isAlive() || target.level() != level() || !canAttack(target) || !isWithinMeleeAttackRange(target)) return;
        swingForAttack(InteractionHand.MAIN_HAND);
        doHurtTarget(level, target);
    }

    /** Where its nose is: just ahead of its head, at about two thirds of its height. */
    private Vec3 nose() {
        Vec3 f = Vec3.directionFromRotation(0.0F, getYHeadRot());
        double reach = 0.85 * getScale();
        return position().add(f.x * reach, 0.6 * getScale(), f.z * reach);
    }

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    /** A plain bite for its attack damage. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            bites++;
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASH_JACKAL_BITE.get(), getSoundSource(), 1.0F, getVoicePitch());
        }
        return hit;
    }

    // ---- items: snatching, running off, dropping ------------------------------------------------------------------------

    /**
     * The dropped item it would go for now: the nearest stack within {@link #ITEM_RANGE} that can be picked up, that no
     * other jackal as near to it or nearer is after (a nearer one takes over), and that no dead jackal dropped in the
     * last {@link #RECLAIM_TICKS}. Null if it carries
     * something already, mobs may not pick items up here, or there is none. Server only; public as a test hook.
     */
    public @Nullable ItemEntity findItem() {
        if (!(level() instanceof ServerLevel level) || !carried.isEmpty() || !Services.PLATFORM.canGrief(level, this)) return null;
        ItemEntity best = null;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(ITEM_RANGE), this::takeable)) {
            if (claimedByOther(level, item)) continue;
            if (best == null || distanceToSqr(item) < distanceToSqr(best)) best = item;
        }
        return best;
    }

    private boolean takeable(ItemEntity item) {
        return item.isAlive() && !item.getItem().isEmpty() && !item.hasPickUpDelay() && item.distanceTo(this) <= ITEM_RANGE
                && confined(item.position()) && !(item.entityTags().contains(DROPPED_TAG) && item.getAge() < RECLAIM_TICKS);
    }

    /** Another jackal is after this stack and is at least as near to it: it is that one's to take. */
    private boolean claimedByOther(ServerLevel level, ItemEntity item) {
        double mine = distanceToSqr(item);
        return !level.getEntitiesOfClass(AshJackal.class, item.getBoundingBox().inflate(ITEM_RANGE + 4.0),
                j -> j != this && j.itemTarget == item && j.isAlive() && j.distanceToSqr(item) <= mine).isEmpty();
    }

    private boolean inReach(ItemEntity item) {
        return Math.hypot(item.getX() - getX(), item.getZ() - getZ()) <= SNATCH_REACH && Math.abs(item.getY() - getY()) <= 1.5;
    }

    /** Gives up the stack it was going for and looks for another only after {@link #GIVE_UP_TICKS}. */
    private void leaveItem() {
        itemTarget = null;
        nextItemLook = level().getGameTime() + GIVE_UP_TICKS;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            if (!sniffingItem) {
                bite(level, core().target());
                return;
            }
            core().end();
            ItemEntity item = itemTarget;
            if (item == null || !item.isAlive() || item.getItem().isEmpty() || !carried.isEmpty() || !inReach(item)) {
                itemTarget = null;
            } else if (!startPhase(Step.SNATCH, SNATCH_TICKS, null)) {
                leaveItem();
            }
        } else if (ended == Step.SNATCH && level() instanceof ServerLevel level) {
            ItemEntity item = itemTarget;
            itemTarget = null;
            core().end();
            if (item != null && item.isAlive() && !item.getItem().isEmpty() && carried.isEmpty() && inReach(item)) take(level, item);
        } else {
            core().end();
        }
    }

    /** Takes the whole stack into its jaws and runs off with it. */
    private void take(ServerLevel level, ItemEntity item) {
        if (!MechanicEvent.post(this, "take", null)) return;
        carried = item.getItem().copy();
        item.discard();
        snatches++;
        core().setFlag(CARRY_FLAG, true);
        syncCarried(level);
        double y = getY() + getBbHeight() * 0.6;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, carried.getItem()), getX(), y, getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        level.sendParticles(ParticleTypes.WHITE_ASH, getX(), y, getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.ASH_JACKAL_SNATCH.get(), getSoundSource(), 1.0F, getVoicePitch());
        Player near = level.getNearestPlayer(getX(), getY(), getZ(), TARGET_RANGE, e -> e instanceof Player p && !p.isSpectator() && confined(p.position()));
        LivingEntity threat = near != null ? near : getTarget();
        flee(threat, threat != null ? threat.position() : position());
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
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASH_JACKAL_CACKLE.get(), getSoundSource(), 1.0F, getVoicePitch());
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

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (biteRest > 0) biteRest--;
        if (core().in(Step.FLEE)) {
            fleeTick();
            return;
        }
        if (core().in(Step.TELL)) {
            sniffTick(level);
            return;
        }
        if (core().in(Step.SNATCH)) {
            holdStill();
            if (itemTarget != null) getLookControl().setLookAt(itemTarget, 30.0F, 60.0F);
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !canAttack(target) || distanceTo(target) > TARGET_RANGE * 1.5)) setTarget(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && (tickCount % 20 == 0 || core().flag(CARRY_FLAG) != !carried.isEmpty())) {
            core().setFlag(CARRY_FLAG, !carried.isEmpty() && isAlive());
            syncCarried(level);
        }
    }

    /** Killed, it drops what it carried, whatever the loot rules say. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) dropCarried(level);
    }

    /** Removed for good some other way (Peaceful, a command): it still never destroys what it carried. */
    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level && reason.shouldDestroy() && !carried.isEmpty()) dropCarried(level);
        super.remove(reason);
    }

    /** Drops the carried stack where it stands; its packmates leave it alone for {@link #RECLAIM_TICKS}. */
    private void dropCarried(ServerLevel level) {
        if (carried.isEmpty()) return;
        ItemStack stack = carried;
        carried = ItemStack.EMPTY;
        core().setFlag(CARRY_FLAG, false);
        syncCarried(level);
        ItemEntity dropped = spawnAtLocation(level, stack, (float) (getBbHeight() * 0.5));
        if (dropped != null) {
            dropped.addTag(DROPPED_TAG);
            dropped.setDeltaMovement((random.nextDouble() - 0.5) * 0.15, 0.2, (random.nextDouble() - 0.5) * 0.15);
        }
        double y = getY() + getBbHeight() * 0.5;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), getX(), y, getZ(), 10, 0.25, 0.25, 0.25, 0.08);
        level.sendParticles(ParticleTypes.POOF, getX(), y, getZ(), 8, 0.3, 0.2, 0.3, 0.02);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.ASH_JACKAL_DROP.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** It never despawns while it carries something. */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || !carried.isEmpty();
    }

    /** A copy of what it carries (empty for nothing). */
    public ItemStack carried() {
        return carried.copy();
    }

    /** Test hook: takes what it carries away from it, so a test's stolen stack never spills into the next test. */
    public ItemStack takeCarried() {
        ItemStack taken = carried;
        carried = ItemStack.EMPTY;
        core().setFlag(CARRY_FLAG, false);
        if (level() instanceof ServerLevel level) syncCarried(level);
        return taken;
    }

    /** Test hook: gives it a stack to carry, as if it had just taken it (without running off). */
    public void carry(ItemStack stack) {
        carried = stack.copy();
        core().setFlag(CARRY_FLAG, !carried.isEmpty());
        if (level() instanceof ServerLevel level) syncCarried(level);
    }

    /**
     * Test hook: from now on it takes items and picks fights only inside {@code box} (null: anywhere in range), so a
     * test's jackal leaves the arenas beside it alone and never notices their players.
     */
    public void confine(@Nullable AABB box) {
        confine = box;
    }

    private boolean confined(Vec3 at) {
        return confine == null || confine.contains(at);
    }

    /** The stack it is running for now, or null. Public as a test hook. */
    public @Nullable ItemEntity goingFor() {
        return itemTarget;
    }

    /** Stacks taken so far. Public as a test hook. */
    public int snatches() {
        return snatches;
    }

    /** Bites that landed so far. Public as a test hook. */
    public int bites() {
        return bites;
    }

    /** True while its sniff is at an item (a dip follows), false while it is at a target (a bite follows). Public as a test hook. */
    public boolean sniffingItem() {
        return sniffingItem;
    }

    // ---- the carried item on show (a self-contained carry display: an item display riding at the mouth) ----------

    /**
     * Keeps the display in step with the carried stack: one item display riding it while it carries something, showing
     * that stack; none otherwise.
     */
    private void syncCarried(ServerLevel level) {
        List<Display.ItemDisplay> shown = new ArrayList<>();
        for (Entity p : getPassengers()) {
            if (p instanceof Display.ItemDisplay d && d.entityTags().contains(CARRY_TAG)) shown.add(d);
        }
        if (carried.isEmpty() || !isAlive()) {
            shown.forEach(Entity::discard);
            return;
        }
        Display.ItemDisplay keep = shown.isEmpty() ? newCarryDisplay(level) : shown.getFirst();
        for (Display.ItemDisplay d : shown) if (d != keep) d.discard();
        if (keep != null && !ItemStack.matches(keep.getSlot(0).get(), carried)) keep.getSlot(0).set(carried.copyWithCount(1));
    }

    /** The carried item on show: always facing the viewer about the vertical, drawn small. */
    private Display.@Nullable ItemDisplay newCarryDisplay(ServerLevel level) {
        Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.LOAD);
        if (d == null) return null;
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        out.store("transformation", Transformation.EXTENDED_CODEC, new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(CARRY_SIZE, CARRY_SIZE, CARRY_SIZE), new Quaternionf()));
        out.store("billboard", Display.BillboardConstraints.CODEC, Display.BillboardConstraints.VERTICAL);
        out.store("item_display", ItemDisplayContext.CODEC, ItemDisplayContext.FIXED);
        d.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
        d.addTag(CARRY_TAG);
        d.getSlot(0).set(carried.copyWithCount(1));
        d.snapTo(getX(), getY() + MOUTH_UP, getZ(), getYRot(), 0.0F);
        if (!level.addFreshEntity(d)) return null;
        if (!d.startRiding(this, true, false)) {
            d.discard();
            return null;
        }
        return d;
    }

    /** The carried item sits at its mouth, turned with its body. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        if (!(passenger instanceof Display.ItemDisplay)) return super.getPassengerAttachmentPoint(passenger, dimensions, scale);
        return new Vec3(0.0, MOUTH_UP * scale, MOUTH_FORWARD * scale).yRot(-yBodyRot * Mth.DEG_TO_RAD);
    }

    /** A carry display that leaves it (it died, or something pulled it off) goes; one that unloads with it stays. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (level().isClientSide() || passenger.isRemoved() || !(passenger instanceof Display.ItemDisplay d) || !d.entityTags().contains(CARRY_TAG)) return;
        RemovalReason reason = getRemovalReason();
        if (reason == null || reason.shouldDestroy()) d.discard();
    }

    // ---- save and load ---------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (!carried.isEmpty()) output.store("Carried", ItemStack.CODEC, carried);
    }

    /** What it carries survives a reload; its display comes back with it, or is made again. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        carried = input.read("Carried", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        core().setFlag(CARRY_FLAG, !carried.isEmpty());
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.ASH_JACKAL_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ASH_JACKAL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ASH_JACKAL_DEATH.get();
    }
}
