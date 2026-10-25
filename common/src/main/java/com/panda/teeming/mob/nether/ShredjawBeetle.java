package com.panda.teeming.mob.nether;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shredjaw Beetle: a heavy crimson-forest beetle with saw-toothed mandibles. It never simply hits: with its target
 * next to it, it spreads its jaws wide for 12 ticks (tell: the jaws gape and grind, clicking, throwing chitin sparks),
 * then they crash shut in a crushing bite for its attack damage. A bite that lands sunders: one stack of Sunder takes
 * 4 armour off the victim (at most 3 stacks, 12 armour), and every worn armour piece loses 2 more durability than
 * the hit itself costs. The stacks last 200 ticks from the latest bite, then fall away together.
 *
 * <p>Counter: raise a shield toward it. A blocked bite does nothing to the victim (no damage, no Sunder, no wear),
 * and the jaws jar on the shield: the beetle reels back for 30 ticks, unable to bite, and its own chitin cracks, one
 * stack of the same Sunder on itself (its armour 8 drops to 4). Backing out of reach before the jaws close also
 * makes it miss.
 *
 * <p>Sunder is a transient armour modifier ({@link Sunder}): never saved, so it is gone after a relog, and never
 * copied to a respawned player. It lifts the moment its victim dies, and when the last beetle that bit a victim dies
 * or unloads.
 */
public class ShredjawBeetle extends TeemingMonster {
    /** IDLE, jaws spread (tell), the bite, reeling from a blocked bite. */
    public enum Step implements Phase { IDLE, TELL, BITE, JARRED }

    public static final int TELL_TICKS = 12;              // the jaws gape for 0.6 s
    public static final int BITE_TICKS = 6;               // the bite lands on its first tick; the jaws stay shut this long
    public static final int JARRED_TICKS = 30;            // after a blocked bite it reels and can't bite for 1.5 s
    public static final int COOLDOWN = 30;                // ticks from the end of one bite to the next tell
    public static final double START_REACH = 0.8;         // blocks between hitboxes: this close it spreads its jaws
    public static final double BITE_REACH = 1.2;          // blocks between hitboxes: a victim this close when the jaws close is bitten
    public static final float BITE_ARC = 120.0F;          // degrees in front of it the bite covers
    public static final float TURN = 12.0F;               // degrees a tick it turns after its target during the tell
    public static final double SUNDER_ARMOR = 4.0;        // armour taken per stack of Sunder
    public static final int SUNDER_MAX = 3;               // stacks at most: 12 armour
    public static final int SUNDER_TICKS = 200;           // the stacks last 10 s after the latest bite
    public static final int WEAR = 2;                     // extra durability each worn armour piece loses per bite

    public static final int CRIMSON = 0xA3202C, TEAL = 0x1E8A80;
    private static final DustParticleOptions CHITIN_RED = new DustParticleOptions(CRIMSON, 0.9F);
    private static final DustParticleOptions CHITIN_TEAL = new DustParticleOptions(TEAL, 0.9F);

    private int cooldown;

    public ShredjawBeetle(EntityType<? extends ShredjawBeetle> type, Level level) {
        super(type, level, Step.values());
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("shredjaw_beetle", 0.24).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** It closes in only: every attack is the telegraphed bite, started from its AI step. */
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

    /** True when {@code target} stands inside the arc its jaws cover. */
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

    /** While a step runs its body stays square to the way it faces, so the jaws point where the bite goes. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    /** Where its jaws are: just ahead of its head, low. */
    private Vec3 jaws() {
        Vec3 f = Vec3.directionFromRotation(0.0F, getYRot());
        double reach = getBbWidth() * 0.5 + 0.25 * getScale();
        return position().add(f.x * reach, 0.3 * getScale(), f.z * reach);
    }

    private DustParticleOptions chitin() {
        return look().startsWith("warped") ? CHITIN_TEAL : CHITIN_RED;
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        // the timers count down before this tick's bites, so a fresh stack lasts its full length
        if (level() instanceof ServerLevel level) Sunder.tick(level.getServer());
        super.tick();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (core().in(Step.JARRED)) {
            // reeling: it stops chasing, but the shield's shove still carries it back
            getNavigation().stop();
            if (core().elapsed() % 4 == 0) {
                Vec3 j = jaws();
                level.sendParticles(ParticleTypes.SMOKE, j.x, j.y + 0.1, j.z, 1, 0.1, 0.05, 0.1, 0.0);
            }
            return;
        }
        if (!core().idle()) {
            holdStill();
            if (core().in(Step.TELL)) gape(level);
            return;
        }
        LivingEntity target = getTarget();
        if (cooldown == 0 && valid(target) && gapTo(target) <= START_REACH && getSensing().hasLineOfSight(target)) startTell(target);
    }

    /** Spreads its jaws at {@code target} for {@link #TELL_TICKS}; the bite follows. Public as a test hook. */
    public boolean startTell(LivingEntity target) {
        if (!core().idle() || !(level() instanceof ServerLevel level) || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        holdStill();
        face(target, 360.0F);
        Vec3 j = jaws();
        level.playSound(null, j.x, j.y, j.z, ModSounds.SHREDJAW_BEETLE_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        Telegraph.particles(chitin(), 8, 0.35, 0.4).play(this);
        return true;
    }

    /** The tell: it turns after its target, the jaws grind and throw sparks, and they click again halfway. */
    private void gape(ServerLevel level) {
        LivingEntity target = core().target();
        if (valid(target)) face(target, TURN);
        int t = core().elapsed();
        Vec3 j = jaws();
        if (t % 2 == 0) {
            Vec3 side = Vec3.directionFromRotation(0.0F, getYRot() + 90.0F).scale(0.35 * getScale());
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, j.x + side.x, j.y, j.z + side.z, 2, 0.05, 0.05, 0.05, 0.08);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, j.x - side.x, j.y, j.z - side.z, 2, 0.05, 0.05, 0.05, 0.08);
            level.sendParticles(chitin(), j.x, j.y, j.z, 2, 0.2, 0.08, 0.2, 0.0);
        }
        if (t == TELL_TICKS / 2) level.playSound(null, j.x, j.y, j.z, ModSounds.SHREDJAW_BEETLE_TELL.get(), getSoundSource(), 0.7F, 1.2F);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            closeJaws(level, core().target());
        } else {
            rest();
        }
    }

    /**
     * The jaws close on {@code target}: a shield raised toward it takes the bite and jars them; a target in reach and
     * in front is bitten; anything else is a snap at the air.
     */
    private void closeJaws(ServerLevel level, @Nullable LivingEntity target) {
        boolean reaches = valid(target) && gapTo(target) <= BITE_REACH && inFront(target);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (reaches && target.applyItemBlocking(level, damageSources().mobAttack(this), damage) > 0.0F) {
            blocked(level, target);
            return;
        }
        if (!startPhase(Step.BITE, BITE_TICKS, target)) {
            rest();
            return;
        }
        Vec3 j = jaws();
        level.playSound(null, j.x, j.y, j.z, ModSounds.SHREDJAW_BEETLE_BITE.get(), getSoundSource(), 1.0F, 1.0F);
        if (reaches) bite(level, target, damage);
    }

    /** The bite lands: its attack damage, then a stack of Sunder and the extra wear on every worn armour piece. */
    private void bite(ServerLevel level, LivingEntity target, float damage) {
        if (!target.hurtServer(level, damageSources().mobAttack(this), damage)) return;
        setLastHurtMob(target);
        boolean sundered = MechanicEvent.post(this, "sunder", target) && Sunder.apply(target, this);
        if (MechanicEvent.post(this, "wear", target)) wear(target);
        if (sundered) {
            level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), ModSounds.SHREDJAW_BEETLE_SUNDER.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(chitin(), target.getX(), target.getY(0.55), target.getZ(), 12, 0.3, 0.3, 0.3, 0.0);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.55), target.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
        }
    }

    /**
     * Every worn armour piece (the slots vanilla wears down when its wearer is hurt) loses {@link #WEAR} more
     * durability. Public as a test hook.
     */
    public static void wear(LivingEntity target) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (!slot.isArmor()) continue;
            ItemStack stack = target.getItemBySlot(slot);
            Equippable worn = stack.get(DataComponents.EQUIPPABLE);
            if (stack.isEmpty() || !stack.isDamageableItem() || worn == null || !worn.damageOnHurt() || worn.slot() != slot) continue;
            stack.hurtAndBreak(WEAR, target, slot);
        }
    }

    /**
     * The counter landed: the shield took the bite. The victim keeps its armour, its health and its durability; the
     * beetle's jaws jar, it reels back and its own chitin cracks (one stack of Sunder on itself).
     */
    private void blocked(ServerLevel level, LivingEntity target) {
        Vec3 j = jaws();
        level.playSound(null, j.x, j.y, j.z, ModSounds.SHREDJAW_BEETLE_BLOCKED.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, j.x, j.y, j.z, 10, 0.2, 0.15, 0.2, 0.3);
        level.sendParticles(chitin(), getX(), getY(0.8), getZ(), 16, 0.4, 0.15, 0.4, 0.0);
        if (MechanicEvent.post(this, "crack", target)) Sunder.apply(this, this);
        if (!startPhase(Step.JARRED, JARRED_TICKS, null)) rest();
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    @Override
    public void die(DamageSource source) {
        Sunder.release(this);
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        Sunder.release(this);
        super.onRemoval(reason);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Ticks until it may spread its jaws again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it bite again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- Sunder ----------------------------------------------------------------------------------------------------

    /**
     * Sunder: a timed armour loss, kept as one transient modifier on the victim's armour ({@link #ID}, add value,
     * {@code -4 x stacks}). Each bite adds a stack (at most {@link #SUNDER_MAX}) and restarts the shared timer of
     * {@link #SUNDER_TICKS}; when it runs out every stack goes at once. The modifier is transient, so it is never saved
     * (a relog clears it) and a respawned player never gets it back.
     *
     * <p>The timers live here, ticked once per server tick by any beetle that ticks. A victim's Sunder also lifts when
     * the victim dies or leaves, and when every beetle that bit it is gone (died or unloaded), so nothing is left
     * behind when no beetle is around to time it.
     */
    public static final class Sunder {
        public static final Identifier ID = TeemingMod.id("shredjaw_sunder");

        private static final class Mark {
            int stacks;
            int left;
            final Set<UUID> by = new HashSet<>();
        }

        // server thread only; weak keys, so a victim the server dropped is never kept alive here
        private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();
        private static @Nullable MinecraftServer lastServer;
        private static int lastTick = Integer.MIN_VALUE;

        private Sunder() {}

        /** Adds a stack to {@code victim} (the timer restarts), laid by {@code by}. False if it has no armour to sunder. */
        public static boolean apply(LivingEntity victim, LivingEntity by) {
            AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
            if (armor == null || victim.level().isClientSide()) return false;
            Mark mark = MARKS.computeIfAbsent(victim, v -> new Mark());
            mark.stacks = Math.min(SUNDER_MAX, mark.stacks + 1);
            mark.left = SUNDER_TICKS;
            mark.by.add(by.getUUID());
            armor.addOrUpdateTransientModifier(new AttributeModifier(ID, -SUNDER_ARMOR * mark.stacks, AttributeModifier.Operation.ADD_VALUE));
            return true;
        }

        /** Stacks of Sunder on {@code victim} now (0 to 3). */
        public static int stacks(LivingEntity victim) {
            Mark mark = MARKS.get(victim);
            return mark == null ? 0 : mark.stacks;
        }

        /** Ticks until {@code victim}'s Sunder falls away, 0 with none. */
        public static int ticksLeft(LivingEntity victim) {
            Mark mark = MARKS.get(victim);
            return mark == null ? 0 : mark.left;
        }

        /** Once per server tick: counts every timer down, lifts the ones that ran out and shows the rest. */
        static void tick(MinecraftServer server) {
            if (server == lastServer && server.getTickCount() == lastTick) return;
            lastServer = server;
            lastTick = server.getTickCount();
            for (Iterator<Map.Entry<LivingEntity, Mark>> it = MARKS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<LivingEntity, Mark> e = it.next();
                LivingEntity victim = e.getKey();
                Mark mark = e.getValue();
                if (victim == null) {
                    it.remove();
                } else if (victim.isRemoved() || !victim.isAlive() || --mark.left <= 0) {
                    strip(victim);
                    it.remove();
                } else if (mark.left % 10 == 0 && victim.level() instanceof ServerLevel level) {
                    // cracked chitin flakes drift off a sundered victim, more with every stack
                    level.sendParticles(victim instanceof ShredjawBeetle b ? b.chitin() : CHITIN_RED,
                            victim.getX(), victim.getY(0.6), victim.getZ(), 2 * mark.stacks, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }

        /** {@code beetle} is gone: every victim no other beetle has bitten loses its Sunder now. */
        static void release(ShredjawBeetle beetle) {
            UUID id = beetle.getUUID();
            for (Iterator<Map.Entry<LivingEntity, Mark>> it = MARKS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<LivingEntity, Mark> e = it.next();
                Mark mark = e.getValue();
                if (!mark.by.remove(id) || !mark.by.isEmpty() && e.getKey() != beetle) continue;
                if (e.getKey() != null) strip(e.getKey());
                it.remove();
            }
        }

        private static void strip(LivingEntity victim) {
            AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
            if (armor != null) armor.removeModifier(ID);
        }
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SHREDJAW_BEETLE_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SHREDJAW_BEETLE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SHREDJAW_BEETLE_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.SHREDJAW_BEETLE_STEP.get(), 0.4F, 1.0F);
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.1F;
    }
}
