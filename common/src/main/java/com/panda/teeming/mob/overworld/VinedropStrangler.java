package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Vinedrop Strangler: a knot of vines that hangs upside down under the jungle canopy. With prey below it (up to 12
 * blocks down) it creeps along the underside of the leaves until it is over it; once its prey is within a block across,
 * a dark ring of shadow shows on the ground where it will land for 12 ticks, leaves fall and the canopy rustles (tell).
 * Then it drops straight down at 1 block a tick and wraps whoever it lands on: Slowness IV and 1 damage a second for 80
 * ticks, coiled round them. Counter: cut it off with shears (used on it or hit with), or with a sword swing that
 * sweeps (fully charged, standing on the ground, not sprinting): the victim is freed at once and it is knocked loose,
 * stunned for 60 ticks and taking x1.5. Afterwards (freed, let go, or a drop that missed) it climbs back up to the
 * leaves it hung from. Hurt while it hangs waiting, it lets go and falls. On the ground with no leaves over it, it
 * lashes with its arms like any monster.
 *
 * <p>Nothing is kept on the victim but the Slowness, which runs out with the wrap: the hold is the strangler moving
 * itself onto its victim each tick, and every way it ends (cut free, the wrap running out, the victim dying or getting
 * away, the strangler dying or unloading) takes the Slowness off and stops following.
 */
public class VinedropStrangler extends TeemingMonster {
    /**
     * On the ground, hanging under leaves, the shadow (tell), dropping, wrapped round a victim, knocked loose, getting
     * up after a miss, climbing back up.
     */
    public enum Step implements Phase { IDLE, HANG, TELL, DROP, WRAP, STUNNED, RECOVER, CLIMB }

    public static final int TELL_TICKS = 12;              // the shadow shows for 12 ticks before it lets go
    public static final double DROP_HEIGHT = 12.0;        // it drops on prey at most this far below it, feet to feet
    public static final double TRIGGER_RANGE = 1.0;       // prey this close across, under it, sets off the shadow
    public static final double DROP_SPEED = 1.0;          // blocks a tick, straight down
    public static final int DROP_TICKS = 20;              // more than any drop from 12 up needs; past it, it just falls
    public static final double CATCH_REACH = 0.25;        // it catches prey this far past its own sides on the way down
    public static final int WRAP_TICKS = 80;              // it holds on for 4 s
    public static final int SLOWNESS_LEVEL = 3;           // amplifier 3 = Slowness IV
    public static final float SQUEEZE_DAMAGE = 1.0F;      // each squeeze
    public static final int SQUEEZE_EVERY = 20;           // once a second: 4 damage over the whole wrap
    /** Squeezes in a full wrap: one a second, the last as it lets go (ticks 20 to 80). */
    public static final int SQUEEZES = WRAP_TICKS / SQUEEZE_EVERY;
    public static final int STUN_TICKS = 60;              // knocked loose, it lies stunned for 3 s
    public static final float STUN_DAMAGE = 1.5F;         // stunned, it takes x1.5 from every blow
    /** A sword swing at least this charged sweeps (the vanilla rule), so it lands at 0.2 + 0.8 x 0.9² of full damage. */
    public static final float SWEEP_CHARGE = 0.9F;
    public static final double KNOCK_SPEED = 0.4;         // how hard it is thrown off its victim, blocks a tick
    public static final double KNOCK_UP = 0.3;
    public static final int RECOVER_TICKS = 20;           // getting up after a drop that missed
    public static final double CLIMB_SPEED = 0.25;        // climbing back up: 12 blocks in 48 ticks
    public static final int CLIMB_TICKS = 100;            // a climb that hasn't arrived by then gives up
    public static final double CREEP_SPEED = 0.08;        // creeping along the underside of the leaves, blocks a tick
    public static final double CREEP_RANGE = 8.0;         // it creeps toward prey at most this far across
    public static final int COOLDOWN = 60;                // back under the leaves, it waits this long before the next drop
    /** Prey taller or wider than this is too big to wrap. */
    public static final float MAX_PREY_WIDTH = 1.0F, MAX_PREY_HEIGHT = 2.0F;
    /** A victim found this far from it (an ender pearl, a teleport) has got away. */
    public static final double ESCAPE_RANGE = 4.0;
    /** On top of a canopy, it threads itself down through at most this many leaf blocks to hang under them. */
    public static final int THREAD_DEPTH = 4;
    public static final int SEEK_EVERY = 20;              // ticks between looks for leaves to hang from

    private static final DustParticleOptions SHADOW = new DustParticleOptions(0x16200E, 1.6F);
    private static final DustParticleOptions STAR = new DustParticleOptions(0xFFD36B, 1.1F);
    private static final ColorParticleOption LEAF = ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF4F7F2A);   // ARGB: opaque
    private static final BlockParticleOption SHREDS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.VINE.defaultBlockState());
    private static final int STARS = 4;
    private static final int RING_POINTS = 20;
    private static final double RING_RADIUS = 0.95;

    private int cooldown;
    private int stuck;
    private @Nullable Vec3 hangPos;
    private @Nullable Vec3 lastClimb;
    private final Grab grab = new Grab(MAX_PREY_WIDTH, MAX_PREY_HEIGHT, ESCAPE_RANGE);

    public VinedropStrangler(EntityType<? extends VinedropStrangler> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("vinedrop_strangler", 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // on the ground it lashes with its arms; hanging, its own phases drive it
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
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
        // it watches the ground far below it, not just the 4 blocks above and below a walking monster
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            protected AABB getTargetSearchArea(double range) {
                return mob.getBoundingBox().inflate(range, DROP_HEIGHT + 2.0, range);
            }
        });
    }

    /** The phases it holds itself up in: no gravity, it moves itself. */
    private boolean aloft() {
        return core().in(Step.HANG) || core().in(Step.TELL) || core().in(Step.DROP) || core().in(Step.WRAP) || core().in(Step.CLIMB);
    }

    @Override
    public boolean vulnerable() {
        return core().in(Step.STUNNED);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) return;
        setNoGravity(aloft());
        // after the victim's own move this tick, so it stays coiled wherever they went
        LivingEntity v = grab.victim();
        if (v != null && core().in(Step.WRAP) && !isNoAi() && grab.holding(this, position())) coil(v);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(aloft());
        if (cooldown > 0) cooldown--;
        if (core().in(Step.HANG)) {
            hangTick(level);
        } else if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().in(Step.DROP)) {
            dropTick(level);
        } else if (core().in(Step.WRAP)) {
            wrapTick(level);
        } else if (core().in(Step.STUNNED)) {
            getNavigation().stop();
            circleStars(level);
        } else if (core().in(Step.RECOVER)) {
            getNavigation().stop();
        } else if (core().in(Step.CLIMB)) {
            climbTick(level);
        } else if (getTarget() == null && tickCount % SEEK_EVERY == 0 && onGround()) {
            seekLeaves(level);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            drop(level);
        } else if (ended == Step.WRAP) {
            letGo(level);
        } else if (ended == Step.STUNNED || ended == Step.RECOVER) {
            climbBack(level);
        } else {
            core().end();              // a drop or climb that ran its length, or a posed hang: back on its own feet
        }
    }

    // ---- hanging under the leaves ----------------------------------------------------------------------------------

    /** True if it can hang with its feet at {@code at}: leaves just over its head and room for its body. */
    public boolean canHangAt(Vec3 at) {
        BlockPos above = BlockPos.containing(at.x, at.y + getBbHeight() + 0.05, at.z);
        if (!level().getBlockState(above).is(BlockTags.LEAVES)) return false;
        return level().noCollision(this, getBoundingBox().move(at.subtract(position())).deflate(1.0E-4));
    }

    /** Where it would hang under the first leaves straight above it (at most 14 up, nothing in between), or null. */
    private @Nullable Vec3 leavesAbove() {
        BlockPos.MutableBlockPos p = blockPosition().mutable();
        for (int i = 1; i <= DROP_HEIGHT + 2; i++) {
            p.move(0, 1, 0);
            BlockState state = level().getBlockState(p);
            if (state.getCollisionShape(level(), p).isEmpty()) continue;
            if (!state.is(BlockTags.LEAVES)) return null;
            Vec3 at = new Vec3(getX(), p.getY() - getBbHeight(), getZ());
            if (!canHangAt(at)) return null;
            AABB path = getBoundingBox().expandTowards(0, at.y - getY(), 0).deflate(1.0E-4);
            return level().noCollision(this, path) ? at : null;
        }
        return null;
    }

    /** Standing on top of a canopy: where it would hang under the leaves it stands on (at most 4 deep), or null. */
    private @Nullable Vec3 leavesBelow() {
        BlockPos.MutableBlockPos p = blockPosition().below().mutable();
        int depth = 0;
        while (depth < THREAD_DEPTH && level().getBlockState(p).is(BlockTags.LEAVES)) {
            p.move(0, -1, 0);
            depth++;
        }
        if (depth == 0 || level().getBlockState(p).is(BlockTags.LEAVES)) return null;
        Vec3 at = new Vec3(getX(), p.getY() + 1 - getBbHeight(), getZ());
        return canHangAt(at) ? at : null;
    }

    /** With nothing to hunt, it goes back up into the trees: climbs to leaves above, or threads down under its canopy. */
    private void seekLeaves(ServerLevel level) {
        Vec3 up = leavesAbove();
        if (up != null) {
            hangPos = up;
            if (startPhase(Step.CLIMB, CLIMB_TICKS, null)) startClimb();
            return;
        }
        Vec3 down = leavesBelow();
        if (down == null) return;
        rustle(level, position(), 10);
        Vec3 from = position();
        hangPos = down;
        if (!hang(false)) {
            hangPos = null;
            return;
        }
        setPos(down);
        rustle(level, from, 10);
        playSound(ModSounds.VINEDROP_STRANGLER_RUSTLE.get(), 0.8F, 1.0F);
    }

    /** Hangs at its hang spot; {@code afterAttack} starts the wait before the next drop. True if it started. */
    private boolean hang(boolean afterAttack) {
        if (!startPhase(Step.HANG, 0, null)) return false;
        if (afterAttack) cooldown = COOLDOWN;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /**
     * Hangs it at once under the leaf block at {@code leaves} (an absolute position), centred below it. Public as a
     * test hook; false if it can't hang there.
     */
    public boolean hangUnder(BlockPos leaves) {
        Vec3 at = new Vec3(leaves.getX() + 0.5, leaves.getY() - getBbHeight(), leaves.getZ() + 0.5);
        if (!canHangAt(at)) return false;
        hangPos = at;
        setPos(at);
        return hang(false);
    }

    private void holdAt(Vec3 at) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        if (position().distanceToSqr(at) > 1.0E-6) setPos(at);
    }

    private void hangTick(ServerLevel level) {
        if (hangPos == null) hangPos = position();
        if (!canHangAt(hangPos)) {           // the leaves are gone
            loseGrip(level);
            return;
        }
        holdAt(hangPos);
        LivingEntity t = getTarget();
        if (!prey(t)) return;
        double below = getY() - t.getY();
        if (below <= 0.0 || below > DROP_HEIGHT) return;
        Vec3 d = new Vec3(t.getX() - getX(), 0.0, t.getZ() - getZ());
        double across = d.length();
        if (across > 1.0E-3) face(d);
        if (across <= TRIGGER_RANGE) {
            if (cooldown == 0 && clearBelow(t)) shadow(level, t);
        } else if (across <= CREEP_RANGE) {
            Vec3 next = hangPos.add(d.scale(Math.min(CREEP_SPEED, across) / across));
            if (canHangAt(next)) {
                hangPos = next;
                holdAt(next);
            }
        }
    }

    /** Nothing solid between the top of {@code t} and its own feet. */
    private boolean clearBelow(LivingEntity t) {
        double top = t.getBoundingBox().maxY;
        if (top >= getY()) return true;
        return level().noCollision(this, new AABB(getX() - 0.3, top, getZ() - 0.3, getX() + 0.3, getY(), getZ() + 0.3));
    }

    /** Hurt while it hangs, it lets go and falls to the ground. */
    private void loseGrip(ServerLevel level) {
        core().end();
        setNoGravity(false);
        rustle(level, position().add(0, getBbHeight(), 0), 12);
        playSound(ModSounds.VINEDROP_STRANGLER_RUSTLE.get(), 1.0F, 0.8F);
    }

    // ---- the shadow and the drop -----------------------------------------------------------------------------------

    /** Starts the tell over {@code t}: the shadow, falling leaves and a rustle. Public as a test hook; true if it started. */
    public boolean shadow(ServerLevel level, LivingEntity t) {
        if (!core().in(Step.HANG) || !startPhase(Step.TELL, TELL_TICKS, t)) return false;
        playSound(ModSounds.VINEDROP_STRANGLER_RUSTLE.get(), 1.2F, 1.0F);
        ring(level);
        rustle(level, position().add(0, getBbHeight(), 0), 8);
        return true;
    }

    private void tellTick(ServerLevel level) {
        if (hangPos != null) holdAt(hangPos);
        else setDeltaMovement(Vec3.ZERO);
        int t = core().elapsed();
        if (t % 2 == 0) ring(level);
        if (t % 3 == 0) rustle(level, position().add(0, getBbHeight(), 0), 3);
        if (t == TELL_TICKS / 2) playSound(ModSounds.VINEDROP_STRANGLER_RUSTLE.get(), 1.2F, 1.2F);
    }

    /** The shadow: a dark ring on the ground straight below, where it will land. */
    private void ring(ServerLevel level) {
        double y = groundBelow(level) + 0.1;
        double turn = tickCount * 0.15;
        for (int i = 0; i < RING_POINTS; i++) {
            double a = turn + i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(SHADOW, true, true, getX() + Math.cos(a) * RING_RADIUS, y, getZ() + Math.sin(a) * RING_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(SHADOW, true, true, getX(), y, getZ(), 4, RING_RADIUS * 0.4, 0.0, RING_RADIUS * 0.4, 0.0);
    }

    /** Leaves shaken loose round {@code at}. */
    private void rustle(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(LEAF, true, true, at.x, at.y, at.z, count, 0.5, 0.1, 0.5, 0.0);
    }

    /** The top of the first solid block below it (within 14), or its own height when there is none. */
    private double groundBelow(ServerLevel level) {
        BlockPos.MutableBlockPos p = blockPosition().mutable();
        for (int i = 0; i <= DROP_HEIGHT + 2; i++, p.move(0, -1, 0)) {
            VoxelShape shape = level.getBlockState(p).getCollisionShape(level, p);
            if (!shape.isEmpty() && p.getY() + shape.max(Direction.Axis.Y) <= getY() + 1.0E-3) return p.getY() + shape.max(Direction.Axis.Y);
        }
        return getY();
    }

    private void drop(ServerLevel level) {
        if (!startPhase(Step.DROP, DROP_TICKS, core().target())) {
            if (!hang(false)) core().end();
            return;
        }
        setDeltaMovement(0.0, -DROP_SPEED, 0.0);
        playSound(ModSounds.VINEDROP_STRANGLER_DROP.get(), 1.2F, 1.0F);
        rustle(level, position().add(0, getBbHeight(), 0), 12);
    }

    /** Falling at 1 block a tick: it wraps the first prey its body (and a quarter block round it) reaches. */
    private void dropTick(ServerLevel level) {
        getNavigation().stop();
        if (core().elapsed() > 0 && (onGround() || verticalCollision)) {
            land(level);
            return;
        }
        AABB reach = getBoundingBox().inflate(CATCH_REACH, 0.0, CATCH_REACH).expandTowards(0.0, -DROP_SPEED, 0.0);
        List<LivingEntity> under = level.getEntitiesOfClass(LivingEntity.class, reach, this::prey);
        if (!under.isEmpty()) {
            LivingEntity aimed = core().target();
            if (wrap(aimed != null && under.contains(aimed) ? aimed : under.getFirst())) return;
        }
        setDeltaMovement(0.0, -DROP_SPEED, 0.0);
    }

    /** A drop that caught nothing: it lands, gathers itself and climbs back up. */
    private void land(ServerLevel level) {
        playSound(ModSounds.VINEDROP_STRANGLER_LAND.get(), 1.0F, 1.0F);
        level.sendParticles(SHREDS, getX(), getY() + 0.1, getZ(), 14, 0.4, 0.05, 0.4, 0.1);
        rustle(level, position().add(0, 0.3, 0), 6);
        if (!startPhase(Step.RECOVER, RECOVER_TICKS, null)) climbBack(level);
    }

    // ---- grab: coiled round a victim -------------------------------------------------------------------------------
    // The strangler moves itself onto its victim after the victim's move each tick (the Grab says whether it still
    // holds them) and leaves no state on it but the Slowness, applied for exactly the wrap's length. Every way the wrap ends runs release(), which
    // takes the Slowness off; a strangler that vanishes without it leaves at most the rest of 4 s of Slowness.

    /** Prey it can wrap: alive, here, small enough, and not a player in creative or spectator. */
    public boolean prey(@Nullable LivingEntity e) {
        return !(e instanceof VinedropStrangler) && grab.fits(this, e);
    }

    /** Wraps {@code prey} at once, without the shadow or the drop; true if it did. Public as a test hook. */
    public boolean wrap(@Nullable LivingEntity prey) {
        if (level().isClientSide() || !prey(prey) || core().in(Step.WRAP)) return false;
        if (!startPhase(Step.WRAP, WRAP_TICKS, prey)) return false;
        grab.take(prey);
        prey.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WRAP_TICKS, SLOWNESS_LEVEL), this);
        coil(prey);
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.VINEDROP_STRANGLER_WRAP.get(), getSoundSource(), 1.2F, 1.0F);
            level.sendParticles(SHREDS, getX(), getY() + 0.8, getZ(), 16, 0.4, 0.4, 0.4, 0.1);
            rustle(level, position().add(0, 1.2, 0), 10);
        }
        return true;
    }

    /** Onto its victim, facing the way they face, so it is coiled round them from behind. */
    private void coil(LivingEntity v) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        if (position().distanceToSqr(v.position()) > 1.0E-6) setPos(v.getX(), v.getY(), v.getZ());
        float yaw = v.getYRot();
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void wrapTick(ServerLevel level) {
        LivingEntity v = grab.victim();
        if (v == null || !grab.holding(this, position())) {
            release();
            climbBack(level);
            return;
        }
        coil(v);
        if (v instanceof Mob m) m.getNavigation().stop();
        int t = core().elapsed();
        if (t > 0 && t <= WRAP_TICKS && t % SQUEEZE_EVERY == 0) squeeze(level, v);
    }

    /** One squeeze: 1 damage, landing in full whatever hit the victim just before. */
    private void squeeze(ServerLevel level, LivingEntity v) {
        if (!MechanicEvent.post(this, "squeeze", v)) return;
        Grab.hurt(level, v, damageSources().mobAttack(this), SQUEEZE_DAMAGE);
        coil(v);                                              // the squeeze's knockback doesn't free it
        level.playSound(null, getX(), getY(), getZ(), ModSounds.VINEDROP_STRANGLER_SQUEEZE.get(), getSoundSource(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        level.sendParticles(SHREDS, getX(), getY() + 0.9, getZ(), 6, 0.35, 0.3, 0.35, 0.05);
    }

    /** Lets go of the victim, if any, and takes its Slowness off. */
    private @Nullable LivingEntity release() {
        LivingEntity v = grab.release();
        if (v != null) {
            MobEffectInstance slow = v.getEffect(MobEffects.SLOWNESS);
            if (slow != null && slow.getAmplifier() == SLOWNESS_LEVEL && slow.getDuration() <= WRAP_TICKS) v.removeEffect(MobEffects.SLOWNESS);
        }
        return v;
    }

    /** The wrap ran out (failing the counter): it lets go and climbs back up. */
    private void letGo(ServerLevel level) {
        release();
        level.sendParticles(SHREDS, getX(), getY() + 0.8, getZ(), 8, 0.4, 0.3, 0.4, 0.05);
        climbBack(level);
    }

    /**
     * The counter landed: the victim is freed at once and it is thrown off, stunned. {@code by} is whoever cut it (it
     * flies away from them, or out in front of a victim who freed themselves). Public as a test hook; true if it was
     * wrapped round someone.
     */
    public boolean cutFree(@Nullable Player by) {
        if (!core().in(Step.WRAP) || !(level() instanceof ServerLevel level)) return false;
        LivingEntity v = release();
        level.playSound(null, getX(), getY(), getZ(), ModSounds.VINEDROP_STRANGLER_FREED.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(SHREDS, getX(), getY() + 0.8, getZ(), 30, 0.45, 0.4, 0.45, 0.2);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 0.8, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        rustle(level, position().add(0, 0.8, 0), 12);
        Vec3 from;
        if (by != null && by != v) from = by.position();
        else if (v != null && v.getLookAngle().horizontalDistanceSqr() > 1.0E-4) from = position().subtract(v.getLookAngle().multiply(1, 0, 1).normalize());
        else from = position().add(0, 0, 1);
        if (startPhase(Step.STUNNED, STUN_TICKS, null)) {
            setNoGravity(false);
            Push.away(this, from, KNOCK_SPEED, KNOCK_UP);
        } else {
            climbBack(level);
        }
        return true;
    }

    /** Shears from any mod: the common shears tag both loaders share, or anything built on the vanilla item. */
    private static final TagKey<Item> SHEARS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/shear"));

    public static boolean shears(ItemStack stack) {
        return stack.is(SHEARS) || stack.getItem() instanceof ShearsItem;
    }

    /**
     * The player whose blow cuts it free, or null: a melee hit made holding shears, or a sword swing that sweeps (sword
     * in hand, standing on the ground, not sprinting, at least 90 % charged: the hit lands at 0.2 + 0.8 x charge² of the
     * player's full attack).
     */
    private @Nullable Player cutter(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player p) || source.getDirectEntity() != p || p.isSpectator()) return null;
        ItemStack held = p.getMainHandItem();
        if (shears(held)) return p;
        if (!held.is(ItemTags.SWORDS) || p.isSprinting() || !standing(p)) return null;
        double full = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return amount >= full * (0.2 + 0.8 * SWEEP_CHARGE * SWEEP_CHARGE) - 1.0E-3 ? p : null;
    }

    /** On the ground, by its own flag or by the block under its feet (a player with no client never reports the flag). */
    private static boolean standing(LivingEntity e) {
        if (e.onGround()) return true;
        BlockPos below = BlockPos.containing(e.getX(), e.getY() - 0.01, e.getZ());
        VoxelShape shape = e.level().getBlockState(below).getCollisionShape(e.level(), below);
        return !shape.isEmpty() && Math.abs(e.getY() - (below.getY() + shape.max(Direction.Axis.Y))) < 0.01;
    }

    /** Shears used on it while it is wrapped round someone cut it free. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (shears(held) && core().in(Step.WRAP)) {
            if (!level().isClientSide() && cutFree(player)) held.hurtAndBreak(1, player, hand);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** Shears or a sweep cut it free; stunned, it takes x1.5; hurt while it hangs, it falls. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Player cut = core().in(Step.WRAP) ? cutter(source, amount) : null;
        boolean hurt = super.hurtServer(level, source, core().in(Step.STUNNED) ? amount * STUN_DAMAGE : amount);
        if (!isAlive()) return hurt;
        if (cut != null) cutFree(cut);
        else if (hurt && core().in(Step.HANG) && source.getEntity() != null) loseGrip(level);
        return hurt;
    }

    // its own victim neither pushes it nor is pushed by it
    @Override
    public void push(Entity other) {
        if (!grab.holds(other)) super.push(other);
    }

    @Override
    protected void doPush(Entity other) {
        if (!grab.holds(other)) super.doPush(other);
    }

    @Override
    public void die(DamageSource source) {
        release();
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        release();
        super.onRemoval(reason);
    }

    // ---- back up into the leaves -----------------------------------------------------------------------------------

    /** Climbs back to the leaves it hung from, or to the nearest straight above; stays on the ground if there are none. */
    private void climbBack(ServerLevel level) {
        if (hangPos == null || !canHangAt(hangPos)) hangPos = leavesAbove();
        if (hangPos == null || !startPhase(Step.CLIMB, CLIMB_TICKS, null)) {
            core().end();
            return;
        }
        startClimb();
    }

    private void startClimb() {
        stuck = 0;
        lastClimb = null;
        getNavigation().stop();
        playSound(ModSounds.VINEDROP_STRANGLER_CLIMB.get(), 1.0F, 1.0F);
    }

    private void climbTick(ServerLevel level) {
        getNavigation().stop();
        Vec3 at = hangPos;
        if (at == null || !canHangAt(at)) {
            at = hangPos = leavesAbove();
            if (at == null) {
                core().end();
                return;
            }
        }
        Vec3 to = at.subtract(position());
        double dist = to.length();
        if (dist <= CLIMB_SPEED) {
            setPos(at);
            if (!hang(true)) core().end();
            return;
        }
        if (lastClimb != null && position().distanceTo(lastClimb) < CLIMB_SPEED * 0.25 && ++stuck >= 5) {
            hangPos = leavesAbove();           // blocked on the way back: the leaves straight overhead instead
            stuck = 0;
            if (hangPos == null) {
                core().end();
                return;
            }
        }
        lastClimb = position();
        setDeltaMovement(to.scale(CLIMB_SPEED / dist));
        if (core().elapsed() % 8 == 0) playSound(ModSounds.VINEDROP_STRANGLER_CLIMB.get(), 0.6F, 0.9F + random.nextFloat() * 0.2F);
    }

    // ---- small things ----------------------------------------------------------------------------------------------

    /** Gold stars circling over it while it lies stunned, one lap a second. */
    private void circleStars(ServerLevel level) {
        if (tickCount % 2 != 0) return;
        Vec3 head = position().add(0, 0.7, 0);
        double turn = core().elapsed() * (Math.PI * 2 / 20);
        for (int i = 0; i < STARS; i++) {
            double a = turn + i * (Math.PI * 2 / STARS);
            level.sendParticles(STAR, head.x + Math.cos(a) * 0.55, head.y, head.z + Math.sin(a) * 0.55, 1, 0, 0, 0, 0);
        }
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Who it is wrapped round, or null. */
    public @Nullable LivingEntity victim() {
        return grab.victim();
    }

    /** Where it hangs (or will hang once it has climbed back), or null. */
    public @Nullable Vec3 hangPos() {
        return hangPos;
    }

    /** Ticks until it may drop again. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it drop again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.VINEDROP_STRANGLER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.VINEDROP_STRANGLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.VINEDROP_STRANGLER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.15F;
    }
}
