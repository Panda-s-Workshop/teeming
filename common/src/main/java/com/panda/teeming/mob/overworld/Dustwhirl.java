package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dustwhirl: a small dust devil of the savanna and the wooded badlands with a glowing core. It always tugs dropped
 * items within 8 blocks toward itself and swallows those that reach it; it keeps them (never destroys them) and spits
 * them all out when it dies or is removed.
 *
 * <p>When its target comes within 5 blocks it spins up for 1 s (tell: the funnel tightens, dust rises, a whistle
 * climbs, its core lights). Then it gusts for 3 s: every player within 5 blocks is pushed straight outward at 0.6
 * blocks a tick, a sneaking player at half that (and sneaking keeps a player from being blown off a ledge, as it
 * always does). Then it slows down for 2 s: the funnel opens, its core shows and it takes x1.5. It stands its ground
 * from the spin-up to the end of the slowdown, and rests 3 s before it can spin up again. Counter: sneak through the
 * gust and strike the core while it slows.
 */
public class Dustwhirl extends TeemingMonster {
    /** IDLE, the spin-up (tell), the gust, the slowdown (its core shows). */
    public enum Step implements Phase { IDLE, SPIN, GUST, SLOW }

    public static final int TELL_TICKS = 20;               // 1 s of spin-up before the gust
    public static final int GUST_TICKS = 60;               // 3 s of gust
    public static final int SLOW_TICKS = 40;               // 2 s with the core showing
    /** How fast the gust pushes a player outward, in blocks a tick. */
    public static final double GUST_SPEED = 0.6;
    /** A sneaking player is pushed at this share of the gust's speed. */
    public static final double SNEAK_FACTOR = 0.5;
    /** Damage it takes while it slows, as a multiple. */
    public static final float CORE_DAMAGE = 1.5F;
    /** It tugs dropped items this close (blocks). */
    public static final double ITEM_REACH = 8.0;
    /** How fast it tugs them, in blocks a tick. */
    public static final double PULL_SPEED = 0.15;
    /** An item this close to its middle (horizontally) is swallowed. */
    public static final double SWALLOW_REACH = 0.9;
    /** It keeps at most this many stacks; past that it still tugs but swallows nothing more. */
    public static final int MAX_HELD = 27;
    /** The gust reaches players this close (blocks). */
    public static final double GUST_RADIUS = 5.0;
    /** It spins up when its target is this close. */
    public static final double TRIGGER_RANGE = 5.0;
    /** Ticks after a slowdown before it may spin up again. */
    public static final int COOLDOWN = 60;
    /** Glow stage of its core: 1 while it spins up and gusts. */
    public static final int LIT = 1;

    private static final int GUST_RING_POINTS = 20;

    private final List<ItemStack> held = new ArrayList<>();
    private int cooldown;
    private @Nullable AABB confine;

    public Dustwhirl(EntityType<? extends Dustwhirl> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("dustwhirl", 0.28);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** From the spin-up to the end of the slowdown it stands its ground: it only falls. */
    @Override
    public Pin pin() {
        MobCore core = core();
        return core != null && !core.idle() ? Pin.ROOTED : Pin.FREE;   // null while the Entity constructor runs
    }

    // ---- the spin-up, the gust and the slowdown --------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        core().setStage(core().in(Step.SPIN) || core().in(Step.GUST) ? LIT : 0);
        DustParticleOptions dust = new DustParticleOptions(dustColor(), 1.3F);
        if (core().in(Step.SPIN)) {
            int t = core().elapsed();
            if (t % 2 == 0) {                                                  // dust rising up the funnel
                for (int i = 0; i < 4; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 0.3 + random.nextDouble() * 0.5;
                    level.sendParticles(dust, getX() + Math.cos(a) * r, getY() + random.nextDouble() * 0.4, getZ() + Math.sin(a) * r,
                            0, 0.0, 1.0, 0.0, 0.15);
                }
            }
            if (t > 0 && t % 5 == 0) playSound(ModSounds.DUSTWHIRL_WHISTLE.get(), 0.8F, 0.9F + 0.15F * (t / 5));
        } else if (core().in(Step.GUST)) {
            if (core().elapsed() % 3 == 0) gustRing(level);
            Telegraph.particles(ParticleTypes.SMALL_GUST, 3, 0.5, 0.5).trail(this, 2);
        } else if (core().in(Step.SLOW)) {
            if (tickCount % 4 == 0) level.sendParticles(new DustParticleOptions(0xFFD36B, 0.8F), getX(), getY() + getBbHeight() * 0.48, getZ(),
                    2, 0.2, 0.15, 0.2, 0.0);
        } else if (tickCount % 6 == 0) {
            level.sendParticles(dust, getX(), getY() + 0.2, getZ(), 2, 0.35, 0.1, 0.35, 0.0);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        tugItems(level);
        if (!core().idle()) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
        if (core().in(Step.GUST)) {
            gust(level);
        } else if (core().idle() && cooldown == 0) {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive() && distanceTo(target) <= TRIGGER_RANGE && getSensing().hasLineOfSight(target)) spinUp(target);
        }
    }

    /** Starts the spin-up if it is free to; true if it did. Ignores the rest after a slowdown. Public as a test hook. */
    public boolean spinUp(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle()) return false;
        if (!startPhase(Step.SPIN, TELL_TICKS, target)) return false;
        Telegraph.particles(new DustParticleOptions(dustColor(), 1.3F), 10, 0.5, 0.2).withSpeed(0.05)
                .withSound(ModSounds.DUSTWHIRL_SPIN.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) return;
        if (ended == Step.SPIN) {
            if (startPhase(Step.GUST, GUST_TICKS, core().target())) {
                level.playSound(null, getX(), getY(), getZ(), ModSounds.DUSTWHIRL_GUST.get(), getSoundSource(), 1.3F, 1.0F);
                level.sendParticles(ParticleTypes.GUST, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                gustRing(level);
            } else {
                rest();
            }
        } else if (ended == Step.GUST) {
            if (startPhase(Step.SLOW, SLOW_TICKS, null)) {
                level.playSound(null, getX(), getY(), getZ(), ModSounds.DUSTWHIRL_SLOW.get(), getSoundSource(), 1.0F, 1.0F);
                level.sendParticles(new DustParticleOptions(dustColor(), 1.5F), getX(), getY() + 0.3, getZ(), 16, 0.5, 0.15, 0.5, 0.0);
            } else {
                rest();
            }
        } else {
            rest();
        }
    }

    private void rest() {
        core().end();
        cooldown = COOLDOWN;
    }

    /**
     * One tick of the gust: every survival or adventure player within 5 blocks is pushed straight outward at 0.6 blocks
     * a tick (0.3 while sneaking), keeping their own fall. Returns how many it pushed. Public as a test hook.
     */
    public int gust(ServerLevel level) {
        int pushed = 0;
        for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(GUST_RADIUS), this::inGust)) {
            if (!MechanicEvent.post(this, "gust_push", player)) continue;
            double speed = player.isShiftKeyDown() ? GUST_SPEED * SNEAK_FACTOR : GUST_SPEED;
            Vec3 out = player.position().subtract(position()).multiply(1, 0, 1);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : out.normalize();          // standing in its middle: blown one way
            Push.set(player, new Vec3(out.x * speed, player.getKnownMovement().y, out.z * speed));
            pushed++;
        }
        return pushed;
    }

    private boolean inGust(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) <= GUST_RADIUS && confined(p.position());
    }

    /** Blown dust filling the gust's reach (never past it), and a few small puffs of wind in it. */
    private void gustRing(ServerLevel level) {
        DustParticleOptions dust = new DustParticleOptions(dustColor(), 1.0F);
        double turn = tickCount * 0.2;
        for (int i = 0; i < GUST_RING_POINTS; i++) {
            double a = turn + i * (Math.PI * 2 / GUST_RING_POINTS);
            double r = 0.8 + random.nextDouble() * (GUST_RADIUS - 1.0);
            level.sendParticles(dust, getX() + Math.cos(a) * r, getY() + 0.1 + random.nextDouble() * 1.3, getZ() + Math.sin(a) * r,
                    0, Math.cos(a), 0.0, Math.sin(a), 1.0);
        }
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 1.0 + random.nextDouble() * (GUST_RADIUS - 2.0);
            level.sendParticles(ParticleTypes.SMALL_GUST, getX() + Math.cos(a) * r, getY() + 0.3 + random.nextDouble(), getZ() + Math.sin(a) * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.SLOW);
    }

    /** While it slows its core shows: it takes x1.5, and a hit that lands rings off the core. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean slow = core().in(Step.SLOW);
        boolean hurt = super.hurtServer(level, source, slow ? amount * CORE_DAMAGE : amount);
        if (hurt && slow) {
            double y = getY() + getBbHeight() * 0.48;
            level.playSound(null, getX(), y, getZ(), ModSounds.DUSTWHIRL_CORE_HIT.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(new DustParticleOptions(0xFFD36B, 1.2F), getX(), y, getZ(), 14, 0.25, 0.2, 0.25, 0.0);
            level.sendParticles(ParticleTypes.CRIT, getX(), y, getZ(), 8, 0.2, 0.2, 0.2, 0.3);
        }
        return hurt;
    }

    // ---- items ---------------------------------------------------------------------------------------------------------

    /** Tugs every dropped item within 8 blocks toward its middle; swallows those that reach it. Returns how many it tugged. */
    public int tugItems(ServerLevel level) {
        int tugged = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(ITEM_REACH),
                e -> e.isAlive() && !e.getItem().isEmpty() && e.distanceTo(this) <= ITEM_REACH && confined(e.position()))) {
            Vec3 in = position().subtract(item.position()).multiply(1, 0, 1);
            double d = in.length();
            boolean alongside = item.getY() > getY() - 0.5 && item.getY() < getY() + getBbHeight();
            if (d <= SWALLOW_REACH && alongside) {
                if (held.size() < MAX_HELD) swallow(level, item);
                continue;
            }
            if (d < 1.0E-4 || !MechanicEvent.post(this, "tug", null)) continue;
            double step = Math.min(PULL_SPEED, d);
            Push.set(item, new Vec3(in.x / d * step, item.getDeltaMovement().y, in.z / d * step));
            tugged++;
        }
        return tugged;
    }

    private void swallow(ServerLevel level, ItemEntity item) {
        if (!MechanicEvent.post(this, "steal", null)) return;
        ItemStack stack = item.getItem().copy();
        held.add(stack);
        item.discard();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), getX(), getY() + getBbHeight() * 0.5, getZ(), 8, 0.2, 0.3, 0.2, 0.05);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.DUSTWHIRL_STEAL.get(), getSoundSource(), 0.8F, 1.0F);
    }

    /** Throws out everything it holds. */
    private void spitHeld(ServerLevel level) {
        for (ItemStack stack : held) {
            ItemEntity e = spawnAtLocation(level, stack, (float) (getBbHeight() * 0.5));
            if (e != null) e.setDeltaMovement((random.nextDouble() - 0.5) * 0.4, 0.25, (random.nextDouble() - 0.5) * 0.4);
        }
        held.clear();
    }

    /** It spits out what it holds the moment it dies, whatever the loot rules say. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) spitHeld(level);
    }

    /** Removed for good some other way (Peaceful, a command): it still never destroys what it holds. */
    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level && reason.shouldDestroy() && !held.isEmpty()) spitHeld(level);
        super.remove(reason);
    }

    /** It never despawns while it holds something. */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || !held.isEmpty();
    }

    /** A copy of what it holds. */
    public List<ItemStack> held() {
        return held.stream().map(ItemStack::copy).toList();
    }

    /** Test hook: takes what it holds away from it, so a test's swallowed items never spill into the next test. */
    public List<ItemStack> takeHeld() {
        List<ItemStack> taken = List.copyOf(held);
        held.clear();
        return taken;
    }

    /**
     * Test hook: from now on it tugs items and gusts players only inside {@code box} (null: anywhere in reach), so a
     * test's whirl leaves the arenas beside it alone.
     */
    public void confine(@Nullable AABB box) {
        confine = box;
    }

    private boolean confined(Vec3 at) {
        return confine == null || confine.contains(at);
    }

    /** Ticks until it may spin up again. */
    public int cooldown() {
        return cooldown;
    }

    /** Test hook: lets it spin up again at once. */
    public void resetCooldown() {
        cooldown = 0;
    }

    private int dustColor() {
        return "red_dust".equals(look()) ? 0xB8643A : 0xC4A06A;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        var list = output.list("Held", ItemStack.CODEC);
        for (ItemStack s : held) if (!s.isEmpty()) list.add(s);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        held.clear();
        input.list("Held", ItemStack.CODEC).ifPresent(l -> l.forEach(held::add));
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.DUSTWHIRL_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.DUSTWHIRL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DUSTWHIRL_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + random.nextFloat() * 0.15F;
    }
}
