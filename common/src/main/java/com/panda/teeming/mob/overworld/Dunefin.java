package com.panda.teeming.mob.overworld;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dunefin: a sand shark of the deserts. It swims through sand with only its dorsal fin showing (no block ever changes:
 * it is an entity passing through the sand, its body under the surface), and it hunts survival players who stand on
 * sand within 16 blocks, at 0.32 blocks a tick. Under its prey the fin rises for 0.75 s (tell); then it breaches
 * straight up, biting whatever is over it for 1.4x its attack, and comes down where its prey stands now, at most 3
 * blocks away. Counter: stand on anything that isn't sand (it never hunts you there and can't follow), or step off the
 * sand as the fin rises: it lands on the hard ground, beached and stunned for 3 s, its pale belly open to attack.
 * Stranded on hard ground it flops back to the nearest sand and dives in.
 */
public class Dunefin extends TeemingMonster {
    /** IDLE (swimming, or stranded and flopping), the fin rising (tell), the leap, diving into sand, lying beached. */
    public enum Step implements Phase { IDLE, TELL, BREACH, DIVE, BEACHED }

    public static final int TELL_TICKS = 15;              // the fin rises under its prey for 0.75 s
    public static final double SWIM_SPEED = 0.32;         // blocks a tick through sand while it hunts
    public static final float BITE_HIT = 1.4F;            // x attack damage
    public static final double HUNT_RANGE = 16.0;         // hunts players standing on sand this close
    public static final int BEACHED_TICKS = 60;           // coming down on anything but sand stuns it for 3 s
    public static final float BEACHED_DAMAGE = 1.5F;      // beached, it takes x1.5 from every blow
    public static final double TELL_RANGE = 1.0;          // the fin rises once it is this close (across) under its prey
    public static final double BREACH_REACH = 3.0;        // the leap comes down at most this far from where it rose
    public static final double BREACH_UP = 0.42;          // blocks a tick upward as it leaves the sand
    public static final double BREACH_GRAVITY = 0.08;     // the leap peaks about 1.3 blocks over where it rose
    public static final int BREACH_TICKS = 12;            // about 11 ticks in the air over level ground
    public static final int DIVE_TICKS = 8;
    public static final int COOLDOWN = 40;                // ticks from one breach to the next rise, at the soonest
    public static final double WANDER_SPEED = 0.1;        // cruising speed with nothing to hunt
    /** {@code core().flag} bits: under the sand (clients skip collisions, the model swims); stranded on hard ground. */
    public static final int SUBMERGED = 0, STRANDED = 1;

    private static final int FALL_TICKS = 5;              // from the top of the leap to the ground
    private static final int FLOP_EVERY = 15;
    private static final double FLOP_UP = 0.35, FLOP_SIDE = 0.15;
    private static final int FIND_SAND = 6;               // stranded, it flops toward sand this close
    private static final DustParticleOptions STAR = new DustParticleOptions(0xFFD36B, 1.1F);
    private static final int STARS = 4;

    private int cooldown;
    private int diveWait;
    private @Nullable Vec3 cruise;
    // the leap
    private boolean leaping, apex, bitten;
    private double climb, breachTop;
    private Vec3 breachFrom = Vec3.ZERO, lunge = Vec3.ZERO;
    private @Nullable LivingEntity prey;

    public Dunefin(EntityType<? extends Dunefin> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("dunefin", 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new Cruise());
    }

    // ---- burrow: moving through a block tag ------------------------------------------------------------------------
    // Self-contained: the mob is an entity passing through the blocks of MEDIUM (it never changes a block), its feet
    // SINK below the top of the column it is in, its collisions off. It moves only into columns of MEDIUM with nothing
    // solid on top, at most a block up or down from the one it is in, and comes out where the medium ends.

    /** What it swims through. */
    public static final TagKey<Block> MEDIUM = BlockTags.SAND;
    /** Its feet sit this far below the sand's top while it swims: the body stays under the surface, the fin above. */
    public static final double SINK = 0.55;
    /** The column this far ahead of its centre must be sand too, so its snout never pokes out where the sand ends. */
    private static final double NOSE = 0.7;

    private boolean submerged;
    /** The world height of the top of the sand it swims in. */
    private double top = Double.NaN;

    /**
     * The top of a column of sand at (x, z) it can swim in, coming from a column whose top is {@code from}: the same
     * height, a block higher or a block lower, with nothing solid on it. NaN if there is none.
     */
    private double topAt(double x, double z, double from) {
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(from) - 1;
        for (int dy : new int[] {0, 1, -1}) {
            BlockPos pos = new BlockPos(bx, by + dy, bz);
            if (isTop(pos)) return pos.getY() + 1.0;
        }
        return Double.NaN;
    }

    private boolean isTop(BlockPos pos) {
        Level level = level();
        if (!level.getBlockState(pos).is(MEDIUM)) return false;
        BlockPos above = pos.above();
        BlockState up = level.getBlockState(above);
        return !up.is(MEDIUM) && up.getCollisionShape(level, above).isEmpty() && up.getFluidState().isEmpty();
    }

    private void enterSand(double sandTop) {
        submerged = true;
        top = sandTop;
        noPhysics = true;
        resetFallDistance();
        core().setFlag(SUBMERGED, true);
        core().setFlag(STRANDED, false);
    }

    private void leaveSand() {
        submerged = false;
        noPhysics = false;
        core().setFlag(SUBMERGED, false);
    }

    /** True while it swims under the sand (both sides). */
    public boolean submerged() {
        return core().flag(SUBMERGED);
    }

    /** The top of the sand it swims in, or NaN out of the sand. */
    public double sandTop() {
        return submerged ? top : Double.NaN;
    }

    /** Sinks it into the sand it stands on at once, without the dive; true if it did. Public as a test hook. */
    public boolean submerge() {
        double t = topAt(getX(), getZ(), getY() + 0.5);
        if (level().isClientSide() || Double.isNaN(t)) return false;
        enterSand(t);
        setDeltaMovement(Vec3.ZERO);
        setPos(getX(), t - SINK, getZ());
        return true;
    }

    /** Swims and climbs with its collisions off while under the sand and leaving it; anything else moves as usual. */
    @Override
    public void travel(Vec3 input) {
        if (submerged || leaping) {
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        super.travel(input);
    }

    @Override
    public boolean isInWall() {
        return !submerged() && super.isInWall();
    }

    @Override
    public boolean isPushable() {
        return !submerged() && super.isPushable();
    }

    /** Under the sand it is lit by the air over it, not the dark inside the sand. */
    @Override
    public Vec3 getLightProbePosition(float partialTick) {
        return submerged() ? getPosition(partialTick).add(0.0, SINK + 0.1, 0.0) : super.getLightProbePosition(partialTick);
    }

    // ---- prey ------------------------------------------------------------------------------------------------------

    /**
     * Prey it hunts: alive, within 16 blocks and standing on sand, and not a player in creative or spectator. It picks
     * its own prey only among players; a target given to it by a command is chased by the same rule.
     */
    public boolean huntable(LivingEntity e) {
        if (!e.isAlive() || e.isRemoved() || e.level() != level() || e == this || e instanceof Dunefin) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return distanceTo(e) <= HUNT_RANGE && onSand(e);
    }

    /** Standing on sand: the block right under its feet is sand (a slab, a carpet or anything else on top is not). */
    public static boolean onSand(Entity e) {
        return e.level().getBlockState(BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ())).is(MEDIUM);
    }

    private @Nullable LivingEntity hunt(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target != null && !huntable(target)) {
            setTarget(null);
            target = null;
        }
        if (target == null && submerged) {
            Player p = level.getNearestPlayer(getX(), getY(), getZ(), HUNT_RANGE, e -> e instanceof Player q && huntable(q));
            if (p != null) {
                setTarget(p);
                target = p;
            }
        }
        return target;
    }

    // ---- the AI step -----------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (diveWait > 0) diveWait--;
        if (leaping) {
            leapTick(level);
            return;
        }
        LivingEntity target = hunt(level);
        if (core().in(Step.BEACHED)) {
            stunTick(level);
        } else if (core().in(Step.DIVE)) {
            diveTick(level);
        } else if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().idle()) {
            if (!submerged) {
                strandedTick(level);
            } else if (target != null && cooldown == 0 && closeUnder(target)) {
                rise(target);
            } else {
                swimTick(level, target);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            noPhysics = core().flag(SUBMERGED);
            return;
        }
        if (submerged && Double.isNaN(top)) {                 // loaded from a save: find the sand it swims in again
            double t = topAt(getX(), getZ(), getY() + 0.5);
            if (Double.isNaN(t)) leaveSand();
            else enterSand(t);
        }
        core().setFlag(SUBMERGED, submerged);
        core().setFlag(STRANDED, !submerged && !leaping && !isNoAi() && core().idle());
    }

    private void swimTick(ServerLevel level, @Nullable LivingEntity target) {
        double here = topAt(getX(), getZ(), top);
        if (Double.isNaN(here)) {
            if (!level.getBlockState(BlockPos.containing(getX(), getY() + 0.1, getZ())).is(MEDIUM)) {
                leaveSand();                                  // the sand round it is gone: it drops out
                return;
            }
            here = top;                                       // covered over: it holds there until it swims out
        }
        top = here;
        Vec3 dir = Vec3.ZERO;
        double step = 0.0;
        if (target != null) {
            Vec3 to = target.position().subtract(position());
            double d = to.horizontalDistance();
            if (d > 1.0E-3) {
                dir = new Vec3(to.x / d, 0.0, to.z / d);
                step = Math.min(SWIM_SPEED, d);
            }
        } else if (cruise != null) {
            dir = cruise;
            step = WANDER_SPEED;
        }
        if (step > 0.0) {
            double nx = getX() + dir.x * step, nz = getZ() + dir.z * step;
            double next = topAt(nx, nz, top);
            double nose = Double.isNaN(next) ? Double.NaN : topAt(nx + dir.x * NOSE, nz + dir.z * NOSE, next);
            if (Double.isNaN(next) || Double.isNaN(nose)) {   // the sand ends here: it can't follow
                step = 0.0;
                if (target == null && cruise != null) cruise = cruise.yRot(Mth.PI * (0.5F + random.nextFloat()));
            } else {
                top = next;
            }
        }
        setDeltaMovement(dir.x * step, Mth.clamp(top - SINK - getY(), -0.3, 0.3), dir.z * step);
        face(dir);
        if (step > 0.0 && tickCount % 2 == 0) wake(level, 2, 0.02);
    }

    private boolean closeUnder(LivingEntity target) {
        return target.position().subtract(position()).horizontalDistance() <= TELL_RANGE && Math.abs(target.getY() - top) <= 1.5;
    }

    /** Sand thrown up round the fin. */
    private void wake(ServerLevel level, int count, double speed) {
        BlockState sand = level.getBlockState(BlockPos.containing(getX(), top - 0.5, getZ()));
        if (!sand.is(MEDIUM)) return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand), getX(), top + 0.05, getZ(), count, 0.3, 0.02, 0.3, speed);
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---- the tell and the breach -----------------------------------------------------------------------------------

    /** Starts the tell under {@code target}: the fin rises for 15 ticks, then it breaches. True if it started. Public as a test hook. */
    public boolean rise(@Nullable LivingEntity target) {
        if (level().isClientSide() || !submerged || !core().idle() || leaping) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        setDeltaMovement(Vec3.ZERO);
        if (target != null) face(target.position().subtract(position()));
        BlockState sand = level().getBlockState(BlockPos.containing(getX(), top - 0.5, getZ()));
        Telegraph.particles(new BlockParticleOption(ParticleTypes.BLOCK, sand.is(MEDIUM) ? sand : level().getBlockState(blockPosition())), 14, 0.45, 1.0)
                .withSpeed(0.15).withSound(ModSounds.DUNEFIN_TELL.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    /** Holding still under its prey, fin rising, sand pouring off it. */
    private void tellTick(ServerLevel level) {
        LivingEntity target = core().target();
        setDeltaMovement(0.0, Mth.clamp(top - SINK - getY(), -0.3, 0.3), 0.0);
        if (target != null) face(target.position().subtract(position()));
        if (core().elapsed() % 2 == 0) wake(level, 4, 0.12);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (isNoAi() || !(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            breach(level, core().target());
        } else if (ended == Step.BREACH && leaping) {
            if (onGround()) land(level);
            else core().end();                                // still in the air: it lands wherever it comes down
        } else {
            core().end();                                     // a dive ends swimming; a stun ends stranded, and it flops back
        }
    }

    /** Leaves the sand straight up; the jaws close on whatever is over it on the way. */
    private void breach(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.BREACH, BREACH_TICKS, target)) {
            core().end();                                     // vetoed: it stays under
            return;
        }
        breachTop = top;
        breachFrom = position();
        leaveSand();
        noPhysics = true;                                     // until it has cleared the sand
        leaping = true;
        apex = false;
        bitten = false;
        climb = BREACH_UP;
        lunge = Vec3.ZERO;
        prey = target;
        cooldown = COOLDOWN;
        setDeltaMovement(0.0, climb, 0.0);
        level.playSound(null, getX(), breachTop, getZ(), ModSounds.DUNEFIN_BREACH.get(), getSoundSource(), 1.2F, 1.0F);
        BlockState sand = level.getBlockState(BlockPos.containing(getX(), breachTop - 0.5, getZ()));
        if (sand.is(MEDIUM)) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand), getX(), breachTop + 0.1, getZ(), 30, 0.5, 0.1, 0.5, 0.25);
        }
    }

    private void leapTick(ServerLevel level) {
        if (!apex && climb <= 0.0) {                          // the top of the leap: it comes down on where its prey is now
            apex = true;
            Vec3 aim = prey != null && prey.isAlive() ? prey.position() : position().add(getLookAngle());
            Vec3 to = aim.subtract(breachFrom).multiply(1.0, 0.0, 1.0);
            if (to.horizontalDistance() > BREACH_REACH) to = to.normalize().scale(BREACH_REACH);
            Vec3 land = breachFrom.add(to);
            lunge = new Vec3((land.x - getX()) / FALL_TICKS, 0.0, (land.z - getZ()) / FALL_TICKS);
            face(lunge);
        }
        if (noPhysics && getY() >= breachTop) noPhysics = false;   // clear of the sand: it falls like anything else from here
        if (!apex && !bitten && prey != null && prey.isAlive() && getBoundingBox().inflate(0.2).intersects(prey.getBoundingBox())) {
            bite(level, prey);
        }
        if (apex && (onGround() || (!noPhysics && level.getBlockState(BlockPos.containing(getX(), getY() + 0.1, getZ())).is(MEDIUM)))) {
            land(level);
            return;
        }
        setDeltaMovement(lunge.x, climb, lunge.z);
        climb -= BREACH_GRAVITY;
    }

    /** The bite on the way up: 1.4x its attack damage. */
    private void bite(ServerLevel level, LivingEntity target) {
        bitten = true;
        if (!MechanicEvent.post(this, "bite", target)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE_HIT;
        target.hurtServer(level, damageSources().mobAttack(this), damage);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.DUNEFIN_BITE.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** Down again: into the sand if it came down on sand, otherwise beached. */
    private void land(ServerLevel level) {
        leaping = false;
        noPhysics = false;
        lunge = Vec3.ZERO;
        prey = null;
        setDeltaMovement(0.0, Math.min(0.0, getDeltaMovement().y), 0.0);
        boolean sand = onSand(this) || level.getBlockState(BlockPos.containing(getX(), getY() + 0.1, getZ())).is(MEDIUM);
        if (sand) {
            if (!dive()) core().end();
        } else if (!beach(level)) {
            core().end();
        }
    }

    // ---- diving in, beached, stranded ------------------------------------------------------------------------------

    /** Into the sand it is on: the dive (8 ticks) ends with it swimming. True if it started. */
    private boolean dive() {
        double t = topAt(getX(), getZ(), getY() + 0.5);
        if (Double.isNaN(t) || diveWait > 0) return false;
        if (!startPhase(Step.DIVE, DIVE_TICKS, null)) {
            diveWait = 20;                                    // vetoed: it tries again a second later
            return false;
        }
        enterSand(t);
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), t, getZ(), ModSounds.DUNEFIN_DIVE.get(), getSoundSource(), 1.0F, 1.0F);
            wake(level, 12, 0.1);
        }
        return true;
    }

    private void diveTick(ServerLevel level) {
        Vec3 ahead = getLookAngle().multiply(1.0, 0.0, 1.0);
        double fwd = ahead.lengthSqr() > 1.0E-4 && Double.isNaN(topAt(getX() + ahead.x * 0.6, getZ() + ahead.z * 0.6, top)) ? 0.0 : 0.04;
        setDeltaMovement(ahead.x * fwd, Math.max(top - SINK - getY(), -0.1), ahead.z * fwd);
        if (core().elapsed() % 2 == 0) wake(level, 3, 0.05);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return super.hurtServer(level, source, core().in(Step.BEACHED) ? amount * BEACHED_DAMAGE : amount);
    }

    /** The counter landed: it came down on something that isn't sand. Stunned for 60 ticks; true if it was. Public as a test hook. */
    public boolean beach(ServerLevel level) {
        leaveSand();
        leaping = false;
        if (!startPhase(Step.BEACHED, BEACHED_TICKS, null)) return false;
        setDeltaMovement(0.0, Math.min(0.0, getDeltaMovement().y), 0.0);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.DUNEFIN_BEACHED.get(), getSoundSource(), 1.0F, 1.0F);
        BlockState under = level.getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ()));
        if (!under.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), getX(), getY() + 0.1, getZ(), 20, 0.5, 0.1, 0.5, 0.1);
        return true;
    }

    private void stunTick(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        if (tickCount % 2 != 0) return;
        Vec3 head = position().add(getLookAngle().multiply(1, 0, 1).scale(0.6)).add(0, getBbHeight() + 0.2, 0);
        double turn = core().elapsed() * (Math.PI * 2 / 20);  // gold stars circling over its head, a lap a second
        for (int i = 0; i < STARS; i++) {
            double a = turn + i * (Math.PI * 2 / STARS);
            level.sendParticles(STAR, head.x + Math.cos(a) * 0.55, head.y, head.z + Math.sin(a) * 0.55, 1, 0, 0, 0, 0);
        }
    }

    /** On hard ground: back into sand if it is on some, otherwise a flop toward the nearest sand every 15 ticks. */
    private void strandedTick(ServerLevel level) {
        if (!onGround()) return;
        if ((onSand(this) || level.getBlockState(BlockPos.containing(getX(), getY() + 0.1, getZ())).is(MEDIUM)) && dive()) return;
        if (tickCount % FLOP_EVERY != 0) return;
        BlockPos sand = nearestSand();
        Vec3 dir = sand == null ? new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5)
                : Vec3.atBottomCenterOf(sand).subtract(position()).multiply(1.0, 0.0, 1.0);
        if (dir.lengthSqr() > 1.0E-4) dir = dir.normalize();
        setDeltaMovement(dir.x * FLOP_SIDE, FLOP_UP, dir.z * FLOP_SIDE);
        face(dir);
        playSound(ModSounds.DUNEFIN_FLOP.get(), 1.0F, getVoicePitch());
    }

    private @Nullable BlockPos nearestSand() {
        BlockPos at = blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FIND_SAND, -2, -FIND_SAND), at.offset(FIND_SAND, 1, FIND_SAND))) {
            if (!isTop(pos)) continue;
            double d = pos.distToCenterSqr(position());
            if (d < bestD) {
                bestD = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    // ---- state -----------------------------------------------------------------------------------------------------

    /** Open to punishment while it lies beached: its belly glows weak-point gold. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.BEACHED);
    }

    /** Ticks until it may rise again. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it rise again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    /** True from the breach until it comes down. */
    public boolean leaping() {
        return leaping;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Dunefin) && super.canAttack(target);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Submerged", submerged);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        submerged = input.getBooleanOr("Submerged", false);
        noPhysics = submerged;
        top = Double.NaN;                                     // found again on the first tick
    }

    /** With nothing to hunt it cruises under the sand: now and then a new heading, for two to four seconds. */
    private final class Cruise extends Goal {
        private int left;

        Cruise() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return submerged && core().idle() && getTarget() == null && random.nextInt(40) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && submerged && core().idle() && getTarget() == null;
        }

        @Override
        public void start() {
            left = 40 + random.nextInt(40);
            float a = random.nextFloat() * Mth.TWO_PI;
            cruise = new Vec3(Mth.cos(a), 0.0, Mth.sin(a));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            left--;
        }

        @Override
        public void stop() {
            cruise = null;
        }
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() && submerged ? ModSounds.DUNEFIN_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.DUNEFIN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DUNEFIN_DEATH.get();
    }
}
