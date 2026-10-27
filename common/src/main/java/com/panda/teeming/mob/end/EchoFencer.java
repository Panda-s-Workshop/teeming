package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Echo Fencer: a slim duelist of the End midlands in a long purpur coat and a tall collar, fighting with a thin rapier.
 * It keeps a rapier's length from its foe; within reach it draws back for 10 ticks (the tell: the blade glints at its
 * tip, a whisper of steel, sparks), turning after its target, then lunges: a thrust for its attack damage at everything
 * standing in a line 3 blocks straight ahead of it (the blade stops at the first block in its way).
 *
 * <p>Where it struck, an afterimage of pale time sand stays, frozen in the lunge, while the fencer steps a block aside
 * out of the line (never toward a drop). 10 ticks later the afterimage draws back in turn (its own tell: a fainter,
 * higher whisper, a glint, sand trickling along the line), and 20 ticks after the thrust it lunges from the same spot in
 * the same direction for 0.8 of the fencer's attack, then crumbles into sand. The afterimage is a picture and a timed
 * hit, not an entity: nothing can strike it or push it. One stands at a time; the next tell comes only once it is gone.
 *
 * <p>Counter: after the first lunge, step out of the afterimage's line before it repeats. While its afterimage stands,
 * the fencer only follows a foe who backs out of its reach, and the afterimage's blade cuts whatever stands in its line,
 * the fencer included: lead it back into its own line and its afterimage runs it through, for the echo's damage through
 * any armour, and the first time it also cracks the little hourglass in its hilt, which spills a pinch of time sand on
 * the ground. Failing it: standing where it attacked costs the echo's 5.6 on top of the 7.
 *
 * <p>The fencer keeps no glow stage: its synced flags carry the afterimage (see {@link Afterimage}) and the cracked
 * hourglass, and the model draws both from them.
 */
public class EchoFencer extends TeemingMonster {
    /** At rest (closing in), the tell (drawing back), the lunge, the recovery while its afterimage waits, the echo. */
    public enum Step implements Phase { IDLE, TELL, THRUST, RECOVER, ECHO }

    /** The tell: 10 ticks drawing back before the lunge. */
    public static final int TELL_TICKS = 10;
    /** The lunge is held this long; its thrust lands on the first tick. */
    public static final int THRUST_TICKS = 4;
    /** The afterimage repeats the thrust this many ticks after it. */
    public static final int ECHO_DELAY = 20;
    /** From the end of the lunge to the echo: the fencer steps aside while the afterimage waits. */
    public static final int RECOVER_TICKS = ECHO_DELAY - THRUST_TICKS;
    /** The afterimage's own tell: it draws back for the last 10 ticks before it repeats the thrust. */
    public static final int ECHO_TELL_TICKS = 10;
    /** The echo: the afterimage's lunge lands on the first tick, then it crumbles into sand over the rest. */
    public static final int ECHO_TICKS = 10;
    /** Ticks after the echo ends before the next tell can start. */
    public static final int COOLDOWN = 10;

    /** The thrust's damage, times the attack damage. */
    public static final float THRUST = 1.0F;
    /** The echo's damage, times the attack damage: 0.8 of 7 is 5.6. */
    public static final float ECHO = 0.8F;
    /** The thrust's line: this many blocks straight ahead from where the fencer stands. */
    public static final double REACH = 3.0;
    /** How far to either side of the line the blade catches a hitbox. */
    public static final double LINE_RADIUS = 0.4;
    /** The band above the fencer's feet the blade sweeps, low to high (the lunge drops it). */
    public static final double LINE_LOW = 0.2;
    public static final double LINE_HIGH = 1.8;
    /** The line is checked at points this far apart, from this far ahead of where the fencer stands (its hand). */
    public static final double LINE_STEP = 0.1;
    public static final double LINE_START = 0.5;
    /** The blade's height above the fencer's feet: a block in the way at this height stops the line. */
    public static final double BLADE_HEIGHT = 1.2;
    /** Its own echo cuts the fencer only when it stands at least this far from the spot it struck from. */
    public static final double SELF_CLEAR = 0.8;

    /** Blocks of open ground between hitboxes at which it starts the tell. */
    public static final double START_GAP = 1.8;
    /** While its afterimage stands it follows only a foe farther than this (blocks between hitboxes). */
    public static final double CHASE_GAP = 3.0;
    /** The step aside after a lunge: this many ticks at this many blocks a tick, about a block. */
    public static final int SIDESTEP_TICKS = 4;
    public static final double SIDESTEP_SPEED = 0.25;
    /** Degrees a tick it turns after its target while drawing back. */
    public static final float TURN = 12.0F;

    /** Flag bit: the hourglass in its hilt is cracked (the first time its echo ran it through). */
    public static final int CRACKED_BIT = Afterimage.BITS;

    /** The afterimage's sand: a pale, greyed time sand, far from the weak-point gold. */
    public static final int SAND_COLOR = 0xD6CAA6;
    /** The glints: a lilac-tinted star white. */
    public static final int GLINT_COLOR = 0xEFE6FF;
    private static final DustParticleOptions SAND = new DustParticleOptions(SAND_COLOR, 0.8F);
    private static final DustParticleOptions GLINT = new DustParticleOptions(GLINT_COLOR, 0.6F);
    private static final DustParticleOptions LILAC = new DustParticleOptions(0xB98BE8, 0.9F);
    private static final BlockParticleOption GRAINS = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());

    private static final Telegraph TELL = Telegraph.particles(ParticleTypes.ELECTRIC_SPARK, 6, 0.3, 0.7).withSpeed(0.02)
            .withSound(ModSounds.ECHO_FENCER_TELL.get(), 1.0F, 1.0F);

    /** The spot and facing it struck from, snapped to what the flags carry; null while no afterimage stands. */
    private @Nullable Vec3 anchor;
    private float anchorYaw;
    /** Which way it steps aside after this lunge: +1 to its left, -1 to its right, 0 not at all. */
    private int side;
    private int cooldown;
    private int thrusts;
    private int echoes;
    private int turned;

    public EchoFencer(EntityType<? extends EchoFencer> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("echo_fencer", 0.3);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return calm() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return calm() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return calm() && super.canUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return calm() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** At rest with nothing to fight: free to wander and look about. */
    private boolean calm() {
        return core().idle() && !valid(getTarget());
    }

    // ---- closing in and the tell -------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Blocks of open ground between its hitbox and {@code target}'s, across. */
    public double gapTo(Entity target) {
        double across = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        return across - (getBbWidth() + target.getBbWidth()) / 2.0;
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

    /** While a step runs its body stays square to the way it faces. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && !core().idle()) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        if (core().in(Step.TELL)) {
            holdStill();
            LivingEntity aim = core().target();
            if (valid(aim)) face(aim, TURN);
            tellTick(level);
        } else if (core().in(Step.THRUST)) {
            holdStill();
        } else if (core().in(Step.RECOVER)) {
            if (core().elapsed() <= SIDESTEP_TICKS && side != 0) sidestep();
            else follow(target, CHASE_GAP);
        } else if (core().in(Step.ECHO)) {
            follow(target, CHASE_GAP);
        } else if (valid(target)) {
            if (gapTo(target) > START_GAP || !getSensing().hasLineOfSight(target)) {
                chase(target);
            } else {
                getNavigation().stop();
                face(target, 30.0F);
                if (cooldown == 0) tell(target);
            }
        }
    }

    /** Follows {@code target} only while it is farther than {@code gap}; nearer, it stands and faces it. */
    private void follow(@Nullable LivingEntity target, double gap) {
        if (!valid(target)) {
            holdStill();
        } else if (gapTo(target) > gap) {
            chase(target);
        } else {
            holdStill();
            face(target, TURN);
        }
    }

    private void chase(LivingEntity target) {
        if (tickCount % 5 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    /** Draws back at {@code target} for {@link #TELL_TICKS}; the lunge follows. Returns false if it was refused. Public as a test hook. */
    public boolean tell(LivingEntity target) {
        if (!core().idle() || !(level() instanceof ServerLevel) || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        holdStill();
        face(target, 360.0F);
        TELL.play(this);
        return true;
    }

    /** The tell: sparks glint at the blade's tip as it draws back. */
    private void tellTick(ServerLevel level) {
        if (core().elapsed() % 2 != 0) return;
        Vec3 tip = position().add(forward(getYRot()).scale(1.4)).add(0.0, 1.2, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, tip.x, tip.y, tip.z, 2, 0.08, 0.08, 0.08, 0.02);
        level.sendParticles(GLINT, true, true, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.0);
    }

    // ---- the lunge, the afterimage and the echo ----------------------------------------------------------------------

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            rest();
            return;
        }
        if (ended == Step.TELL) {
            if (!lunge(core().target())) rest();
        } else if (ended == Step.THRUST && anchor != null && startPhase(Step.RECOVER, RECOVER_TICKS, core().target())) {
            stepAside(level);
        } else if (ended == Step.RECOVER && anchor != null && startPhase(Step.ECHO, ECHO_TICKS, core().target())) {
            echo(level);
        } else {
            rest();
        }
    }

    /** Back at rest: the afterimage, if any, is gone, and the next tell waits out the cooldown. */
    private void rest() {
        boolean was = anchor != null;
        clearAfterimage();
        core().end();
        if (was) cooldown = COOLDOWN;
    }

    /**
     * The lunge: a thrust for its attack damage along its line, from where it stands the way it faces (snapped to what
     * its flags carry, so the afterimage repeats exactly this line), and the afterimage left there. Returns false if it
     * was refused. Public as a test hook (it doesn't need the tell first).
     */
    public boolean lunge(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel level) || !startPhase(Step.THRUST, THRUST_TICKS, target)) return false;
        holdStill();
        anchor = new Vec3(Afterimage.snapXZ(getX()), Afterimage.snapY(getY()), Afterimage.snapXZ(getZ()));
        anchorYaw = Afterimage.snapYaw(getYRot());
        setYRot(anchorYaw);
        yBodyRot = anchorYaw;
        yHeadRot = anchorYaw;
        showAfterimage();
        thrusts++;
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.ECHO_FENCER_THRUST.get(), getSoundSource(), 1.0F, 1.0F);
        strike(level, THRUST, false);
        streak(level, ParticleTypes.ELECTRIC_SPARK, 2);
        return true;
    }

    /** After the lunge it steps a block aside, out of the line, to whichever side has ground (either at random), leaving the afterimage. */
    private void stepAside(ServerLevel level) {
        boolean left = safeAside(level, 1), right = safeAside(level, -1);
        side = left && right ? (random.nextBoolean() ? 1 : -1) : left ? 1 : right ? -1 : 0;
        Vec3 at = anchor;
        level.playSound(null, at.x, at.y + 1.0, at.z, ModSounds.ECHO_FENCER_AFTERIMAGE.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(SAND, true, true, at.x, at.y + 1.0, at.z, 12, 0.3, 0.5, 0.3, 0.0);
    }

    /** True when the spot about a block to its left ({@code dir} +1) or right (-1) is open and has ground under it. */
    private boolean safeAside(ServerLevel level, int dir) {
        Vec3 to = position().add(left(anchorYaw).scale(dir * (SIDESTEP_TICKS * SIDESTEP_SPEED + 0.3)));
        BlockPos feet = BlockPos.containing(to.x, getY() + 0.01, to.z);
        if (!level.noCollision(this, getBoundingBox().move(to.subtract(position())))) return false;
        for (int down = 1; down <= 2; down++) {
            BlockPos under = feet.below(down);
            if (!level.getBlockState(under).getCollisionShape(level, under).isEmpty()) return true;
        }
        return false;
    }

    private void sidestep() {
        getNavigation().stop();
        Vec3 aside = left(anchorYaw).scale(side * SIDESTEP_SPEED);
        setDeltaMovement(aside.x, getDeltaMovement().y, aside.z);
    }

    /** The echo: the afterimage's lunge along the same line for 0.8 of the attack. */
    private void echo(ServerLevel level) {
        Vec3 at = anchor;
        echoes++;
        level.playSound(null, at.x, at.y + 1.0, at.z, ModSounds.ECHO_FENCER_ECHO.get(), getSoundSource(), 1.0F, 1.0F);
        strike(level, ECHO, true);
        streak(level, GRAINS, 3);
        streak(level, ParticleTypes.ELECTRIC_SPARK, 1);
    }

    /**
     * One thrust along the afterimage's line: {@code multiplier} times the attack damage to every player and mob in it
     * (never a creative or spectating player, never another Echo Fencer). From the echo, the fencer itself is cut too
     * when it stands in the line away from the spot it struck from. Returns how many it hit.
     */
    private int strike(ServerLevel level, float multiplier, boolean fromEcho) {
        Vec3 at = anchor;
        if (at == null) return 0;
        double reach = reach(level);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier;
        AABB area = new AABB(at, at.add(forward(anchorYaw).scale(reach))).inflate(LINE_RADIUS + 1.0, LINE_HIGH + 1.0, LINE_RADIUS + 1.0);
        List<LivingEntity> caught = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, area,
                e -> !(e instanceof EchoFencer) && e.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e) && inLine(e, reach)));
        int hit = 0;
        for (LivingEntity victim : caught) {
            if (!MechanicEvent.post(this, fromEcho ? "echo_hit" : "thrust_hit", victim)) continue;
            if (victim.hurtServer(level, damageSources().mobAttack(this), damage)) {
                hit++;
                setLastHurtMob(victim);
                level.sendParticles(ParticleTypes.CRIT, true, true, victim.getX(), victim.getY(0.6), victim.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
            }
        }
        if (fromEcho && isAlive() && position().subtract(at).horizontalDistance() >= SELF_CLEAR && inLine(this, reach)) runThrough(level, damage);
        return hit;
    }

    /** True when {@code e}'s hitbox lies on the afterimage's line, {@code reach} blocks long. */
    private boolean inLine(LivingEntity e, double reach) {
        AABB b = e.getBoundingBox();
        return Afterimage.inLine(anchor.x, anchor.y, anchor.z, anchorYaw, reach, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    /** How far the blade reaches along the line before the first block in its way, at most {@link #REACH}. */
    private double reach(ServerLevel level) {
        Vec3 dir = forward(anchorYaw);
        int steps = (int) Math.floor(REACH / LINE_STEP + 1.0E-6);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = anchor.add(dir.scale(i * LINE_STEP)).add(0.0, BLADE_HEIGHT, 0.0);
            BlockPos pos = BlockPos.containing(p);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return (i - 1) * LINE_STEP;
        }
        return REACH;
    }

    /**
     * The counter's payoff: its own afterimage runs it through, for the echo's damage through any armour. The first
     * time it also cracks the hourglass in its hilt, which spills a pinch of time sand on the ground.
     */
    private void runThrough(ServerLevel level, float damage) {
        if (!MechanicEvent.post(this, "turned", this)) return;
        if (!hurtServer(level, damageSources().magic(), damage)) return;
        turned++;
        level.sendParticles(GRAINS, true, true, getX(), getY(0.6), getZ(), 24, 0.3, 0.4, 0.3, 0.0);
        level.sendParticles(LILAC, true, true, getX(), getY(0.6), getZ(), 10, 0.3, 0.4, 0.3, 0.0);
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.ECHO_FENCER_TURNED.get(), getSoundSource(), 1.2F, 1.0F);
        if (cracked() || !isAlive() || !MechanicEvent.post(this, "spill", this)) return;
        core().setFlag(CRACKED_BIT, true);
        spawnAtLocation(level, new ItemStack(ModMaterials.TIME_SAND.get()), 1.0F);
        level.sendParticles(SAND, true, true, getX(), getY(0.55), getZ(), 16, 0.2, 0.2, 0.2, 0.0);
        level.playSound(null, getX(), getY(0.55), getZ(), ModSounds.ECHO_FENCER_SPILL.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** Particles strung along the line at the blade's height, one point every {@code every} tenths of a block. */
    private void streak(ServerLevel level, ParticleOptions particle, int every) {
        Vec3 dir = forward(anchorYaw);
        double reach = reach(level);
        for (double s = 0.5; s <= reach; s += LINE_STEP * every) {
            Vec3 p = anchor.add(dir.scale(s));
            level.sendParticles(particle, true, true, p.x, p.y + BLADE_HEIGHT, p.z, 1, 0.03, 0.05, 0.03, 0.0);
        }
    }

    /** Its afterimage at work: drawing back through its tell, then crumbling after its echo. */
    private void afterimageTick(ServerLevel level) {
        Vec3 at = anchor;
        if (at == null) return;
        if (core().in(Step.RECOVER)) {
            int t = core().elapsed();
            int start = RECOVER_TICKS - ECHO_TELL_TICKS;
            if (t == start) {
                level.playSound(null, at.x, at.y + 1.0, at.z, ModSounds.ECHO_FENCER_ECHO_TELL.get(), getSoundSource(), 0.9F, 1.0F);
            }
            if (t >= start && t % 2 == 0) {
                Vec3 dir = forward(anchorYaw);
                for (double s = 0.6; s <= REACH; s += 0.6) {
                    Vec3 p = at.add(dir.scale(s));
                    level.sendParticles(SAND, true, true, p.x, p.y + 0.1, p.z, 1, 0.05, 0.0, 0.05, 0.0);
                }
                Vec3 tip = at.add(dir.scale(1.2)).add(0.0, 1.2, 0.0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        } else if (core().in(Step.ECHO)) {
            int t = core().elapsed();
            if (t == ECHO_TICKS / 2) {
                level.playSound(null, at.x, at.y + 1.0, at.z, ModSounds.ECHO_FENCER_CRUMBLE.get(), getSoundSource(), 0.8F, 1.0F);
            }
            if (t >= ECHO_TICKS / 2) level.sendParticles(GRAINS, true, true, at.x, at.y + 1.0, at.z, 3, 0.25, 0.5, 0.25, 0.0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        // an afterimage outlives nothing that ended its steps (a command, a reload, a vetoed step)
        if (anchor != null && !(core().in(Step.THRUST) || core().in(Step.RECOVER) || core().in(Step.ECHO))) clearAfterimage();
        afterimageTick(level);
    }

    @Override
    public void die(DamageSource source) {
        clearAfterimage();
        if (!core().idle()) core().end();
        super.die(source);
    }

    // ---- the afterimage in the flags ---------------------------------------------------------------------------------

    private void showAfterimage() {
        int keep = core().flags() & ~Afterimage.MASK;
        setFlags(keep | Afterimage.encode(anchor.x, anchor.y, anchor.z, anchorYaw));
    }

    private void clearAfterimage() {
        anchor = null;
        side = 0;
        if (Afterimage.live(core().flags())) setFlags(core().flags() & ~Afterimage.MASK);
    }

    private void setFlags(int flags) {
        for (int bit = 0; bit < Afterimage.BITS; bit++) core().setFlag(bit, (flags >> bit & 1) != 0);
    }

    /** True once its own echo has cracked the hourglass in its hilt. Both sides. */
    public boolean cracked() {
        return core().flag(CRACKED_BIT);
    }

    /** Where its afterimage stands, or null. Server only. Public as a test hook. */
    public @Nullable Vec3 anchor() {
        return anchor;
    }

    /** The way its afterimage faces, in degrees (meaningful only while one stands). Public as a test hook. */
    public float anchorYaw() {
        return anchorYaw;
    }

    /** True while its afterimage draws back, the echo's tell. Public as a test hook. */
    public boolean echoTelling() {
        return anchor != null && core().in(Step.RECOVER) && core().elapsed() >= RECOVER_TICKS - ECHO_TELL_TICKS;
    }

    /** The box the renderer culls it by: while an afterimage stands it reaches it, so it stays drawn when the fencer is off screen. */
    @Override
    public AABB getInterpolatedBoundingBox(float partialTick) {
        AABB box = super.getInterpolatedBoundingBox(partialTick);
        if (core() == null) return box;
        int flags = core().flags();
        if (!Afterimage.live(flags)) return box;
        Vec3 at = new Vec3(Afterimage.x(flags, getX()), Afterimage.y(flags, getY()), Afterimage.z(flags, getZ()));
        Vec3 tip = at.add(forward((float) Afterimage.yaw(flags)).scale(REACH));
        return box.minmax(new AABB(at, tip).inflate(0.6, 0.0, 0.6).expandTowards(0.0, getBbHeight(), 0.0));
    }

    /** The way a body with yaw {@code yawDeg} faces, flat. */
    public static Vec3 forward(float yawDeg) {
        float r = yawDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
    }

    /** A body with yaw {@code yawDeg}'s left, flat. */
    public static Vec3 left(float yawDeg) {
        float r = yawDeg * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(r), 0.0, Mth.sin(r));
    }

    // ---- test hooks --------------------------------------------------------------------------------------------------

    /** Thrusts so far (the lunge, not the echo). Public as a test hook. */
    public int thrusts() {
        return thrusts;
    }

    /** Echoes so far. Public as a test hook. */
    public int echoes() {
        return echoes;
    }

    /** Times its own echo ran it through. Public as a test hook. */
    public int turned() {
        return turned;
    }

    /** Ticks until it may draw back again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Which way it steps aside after this lunge: +1 left, -1 right, 0 not at all. Public as a test hook. */
    public int side() {
        return side;
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Cooldown", cooldown);
        output.putBoolean("Cracked", cracked());
    }

    /** The cooldown and the cracked hourglass survive a reload; a lunge under way and its afterimage do not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cooldown = Math.clamp(input.getIntOr("Cooldown", 0), 0, COOLDOWN);
        core().setFlag(CRACKED_BIT, input.getBooleanOr("Cracked", false));
    }

    // ---- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.ECHO_FENCER_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ECHO_FENCER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ECHO_FENCER_DEATH.get();
    }

    // ---- the afterimage's place and line (pure maths, no game types) -------------------------------------------------

    /**
     * Where an afterimage stands and the line it strikes along, with no game types. The spot is packed into the synced
     * flags as its world position, wrapped, so the client can draw it fixed in the world however the fencer moves.
     *
     * <p>Flags: bit 0 says an afterimage stands; bits 1-7 its x and bits 8-14 its z, in eighths of a block, wrapped every
     * 16 blocks; bits 15-20 the way it faces, in 64ths of a turn; bits 21-23 its height, in half blocks, wrapped every 4.
     * The client unwraps each against the fencer's own position, which never strays that far from it while it stands.
     */
    public static final class Afterimage {
        /** Flag bits the afterimage uses, from bit 0. */
        public static final int BITS = 24;
        public static final int MASK = (1 << BITS) - 1;
        public static final int LIVE_BIT = 0, X_BIT = 1, Z_BIT = 8, YAW_BIT = 15, Y_BIT = 21;
        private static final int XZ_SIZE = 7, YAW_SIZE = 6, Y_SIZE = 3;
        public static final int XZ_PER_BLOCK = 8, Y_PER_BLOCK = 2;
        public static final int YAW_STEPS = 1 << YAW_SIZE;
        /** The wrap: x and z repeat every 16 blocks, y every 4. */
        public static final double XZ_WRAP = (double) (1 << XZ_SIZE) / XZ_PER_BLOCK, Y_WRAP = (double) (1 << Y_SIZE) / Y_PER_BLOCK;

        private Afterimage() {}

        public static double snapXZ(double v) {
            return Math.round(v * XZ_PER_BLOCK) / (double) XZ_PER_BLOCK;
        }

        public static double snapY(double v) {
            return Math.round(v * Y_PER_BLOCK) / (double) Y_PER_BLOCK;
        }

        public static float snapYaw(float yawDeg) {
            return (float) (Math.round(yawDeg / (360.0 / YAW_STEPS)) * (360.0 / YAW_STEPS));
        }

        /** Packs an afterimage at x, y, z facing {@code yawDeg} (each snapped to what the flags hold). */
        public static int encode(double x, double y, double z, double yawDeg) {
            int qx = Math.floorMod(Math.round(x * XZ_PER_BLOCK), 1 << XZ_SIZE);
            int qz = Math.floorMod(Math.round(z * XZ_PER_BLOCK), 1 << XZ_SIZE);
            int qy = Math.floorMod(Math.round(y * Y_PER_BLOCK), 1 << Y_SIZE);
            int qyaw = Math.floorMod((int) Math.round(yawDeg / (360.0 / YAW_STEPS)), YAW_STEPS);
            return 1 << LIVE_BIT | qx << X_BIT | qz << Z_BIT | qyaw << YAW_BIT | qy << Y_BIT;
        }

        private static int field(int flags, int bit, int size) {
            return flags >> bit & (1 << size) - 1;
        }

        /** From flags: whether an afterimage stands. */
        public static boolean live(int flags) {
            return (flags >> LIVE_BIT & 1) != 0;
        }

        /** {@code v} moved by whole wraps to lie within half a wrap of {@code near}. */
        private static double unwrap(double v, double wrap, double near) {
            return v + wrap * Math.round((near - v) / wrap);
        }

        /** From flags: its x, unwrapped near {@code nearX} (the fencer's own x). */
        public static double x(int flags, double nearX) {
            return unwrap(field(flags, X_BIT, XZ_SIZE) / (double) XZ_PER_BLOCK, XZ_WRAP, nearX);
        }

        public static double z(int flags, double nearZ) {
            return unwrap(field(flags, Z_BIT, XZ_SIZE) / (double) XZ_PER_BLOCK, XZ_WRAP, nearZ);
        }

        public static double y(int flags, double nearY) {
            return unwrap(field(flags, Y_BIT, Y_SIZE) / (double) Y_PER_BLOCK, Y_WRAP, nearY);
        }

        /** From flags: the way it faces, in degrees 0..360. */
        public static double yaw(int flags) {
            return field(flags, YAW_BIT, YAW_SIZE) * (360.0 / YAW_STEPS);
        }

        /**
         * True when a box (min and max corners) lies on the line {@code reach} blocks long from x, y, z along yaw
         * {@code yawDeg}: some point of the line from {@link EchoFencer#LINE_START} on, checked every {@link EchoFencer#LINE_STEP}, is within
         * {@link EchoFencer#LINE_RADIUS} of the box across, and the box reaches into the band from
         * {@link EchoFencer#LINE_LOW} to {@link EchoFencer#LINE_HIGH} above the line's feet.
         */
        public static boolean inLine(double x, double y, double z, double yawDeg, double reach,
                                     double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            if (maxY <= y + LINE_LOW || minY >= y + LINE_HIGH) return false;
            double r = Math.toRadians(yawDeg);
            double fx = -Math.sin(r), fz = Math.cos(r);
            int steps = (int) Math.floor((reach - LINE_START) / LINE_STEP + 1.0E-6);
            for (int i = 0; i <= steps; i++) {
                double s = LINE_START + i * LINE_STEP;
                double px = x + fx * s, pz = z + fz * s;
                if (px >= minX - LINE_RADIUS && px <= maxX + LINE_RADIUS && pz >= minZ - LINE_RADIUS && pz <= maxZ + LINE_RADIUS) return true;
            }
            return false;
        }
    }
}
