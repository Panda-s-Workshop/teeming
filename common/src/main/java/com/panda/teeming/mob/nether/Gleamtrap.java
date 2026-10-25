package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Gleamtrap: an ambush mob of the crimson and warped forests that passes for a shroomlight on the cap of a huge
 * fungus. Disguised, it is one glowing block: it sits square on the block grid, never moves, casts no shadow and is as
 * solid as the block it copies, so it can be stood on. Every 80 ticks it gives itself away with a pulse: its glow swells
 * and fades, its top lifts a crack, it throbs softly and a few motes rise.
 *
 * <p>Whatever touches it, hits it, or stands within a block of it for 40 ticks in a row sets it off. Tell: for 10 ticks
 * it splits open, teeth showing, and twists toward its prey. Then the lid slams shut: a bite for 1.5x Atk on whoever set
 * it off, if they are still within a block. It settles back into its disguise for 20 ticks, during which nothing sets it
 * off. Counter: watch for the pulse and kill it from range (it never reaches past a block), or step away during the tell.
 * It never places or breaks a block.
 */
public class Gleamtrap extends TeemingMonster {
    public enum Step implements Phase { IDLE, PULSE, TELL, BITE, RECOVER }

    public static final int TELL_TICKS = 10;            // it splits open, teeth showing
    public static final int BITE_TICKS = 6;             // the lid slammed shut; the bite lands as it starts
    public static final int RECOVER_TICKS = 20;         // settling back into its disguise; nothing sets it off meanwhile
    public static final float BITE_HIT = 1.5F;          // x attack damage: 10.5 for its attack of 7
    public static final double REACH = 1.0;             // blocks out from its sides, top and bottom: "next to it", and the bite's reach
    public static final double TOUCH = 0.1;             // closer than this, a body is touching it
    public static final int LINGER_TICKS = 40;          // standing next to it this many ticks in a row sets it off
    public static final int PULSE_EVERY = 80;           // ticks from one pulse to the next while it waits undisturbed
    public static final int PULSE_TICKS = 12;           // the glow swells to its brightest and fades back
    public static final int PULSE_STAGES = 3;           // glow stages: at rest, mid pulse, the peak
    public static final float TURN_PER_TICK = 36.0F;    // once it splits open it twists toward its prey this fast, in degrees

    private static final DustParticleOptions MOTE = new DustParticleOptions(0xFEDD9F, 0.9F);

    /** Ticks each body near it has stood within {@link #REACH}, in a row, by entity id. */
    private final Map<Integer, Integer> lingering = new HashMap<>();
    /** Ticks since its last pulse began; a fresh one starts part way, so neighbours don't pulse in step. */
    private int pulseClock = -1;
    /** False until it has squared itself to the block grid, on its first tick. */
    private boolean settled;
    /** The grid direction it holds while disguised: a multiple of 90 degrees. */
    private float gridYaw;

    public Gleamtrap(EntityType<? extends Gleamtrap> type, Level level) {
        super(type, level, Step.values());
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("gleamtrap", 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    /** No goals: it never walks or hunts. What comes near it, or strikes it, is all that moves it to act. */
    @Override
    protected void registerGoals() {
    }

    @Override
    public int visualStages() {
        return PULSE_STAGES;
    }

    // ---- disguise: one shroomlight block until something comes too close ----------------------------------------------
    // Self-contained: the idle phase and the pulse are the disguise. It squares itself to the block grid on its first
    // tick and holds a grid direction while disguised; it is pinned in place and as solid as a block; a pulse every
    // PULSE_EVERY ticks is its only giveaway. A shared disguise kit can lift this section as it stands.

    /** True while it passes for a block: waiting, or in the middle of a pulse. */
    public boolean disguised() {
        return core().idle() || core().in(Step.PULSE);
    }

    /** Only falling moves it, whether disguised or open: walking, knockback, pistons and currents lose their sideways part. */
    @Override
    public Pin pin() {
        return Pin.ROOTED;
    }

    /** As solid as the block it passes for: players and mobs stand on it and bump into it. */
    @Override
    public boolean canBeCollidedWith(@Nullable Entity entity) {
        return isAlive();
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /**
     * Squares it to the block grid: the middle of the block it stands in (if that spot is free) and the nearest of the
     * four grid directions. A fresh spawn does this on its first tick.
     */
    private void settle() {
        settled = true;
        double x = Mth.floor(getX()) + 0.5, z = Mth.floor(getZ()) + 0.5;
        AABB there = getBoundingBox().move(x - getX(), 0.0, z - getZ());
        if (level().noCollision(this, there)) snapTo(x, getY(), z);
        holdGrid(getYRot());
    }

    /** The nearest grid direction to {@code yaw}: a multiple of 90 degrees. */
    public static float gridYaw(float yaw) {
        return Mth.wrapDegrees(Math.round(yaw / 90.0F) * 90.0F);
    }

    private void holdGrid(float yaw) {
        gridYaw = gridYaw(yaw);
        setFacing(gridYaw);
        yRotO = getYRot();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
    }

    /**
     * Settles into its disguise facing the grid direction nearest the way it faces now. Returns false if it isn't
     * disguised. Public as a test and screenshot hook.
     */
    public boolean root(boolean quiet) {
        if (!disguised()) return false;
        holdGrid(getYRot());
        return true;
    }

    private void setFacing(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Body and head are one block: both always face the way it does. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        this.yBodyRot = getYRot();
        this.yHeadRot = getYRot();
    }

    // ---- what sets it off ----------------------------------------------------------------------------------------------

    /** Players in survival or adventure and mobs other than its own kind set it off; nothing else does. */
    private boolean prey(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Gleamtrap) && !e.isSpectator()
                && (e instanceof Mob || e instanceof Player p && !p.isCreative());
    }

    /** The widest gap between its box and {@code e}'s along any axis, in blocks; 0 when they touch or overlap. */
    public double gap(Entity e) {
        AABB a = getBoundingBox(), b = e.getBoundingBox();
        double dx = Math.max(0.0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
        double dy = Math.max(0.0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
        double dz = Math.max(0.0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
        return Math.max(dx, Math.max(dy, dz));
    }

    /**
     * Counts each body within {@link #REACH} one more tick and forgets those that left. Returns who sets it off: the
     * nearest body touching it at once, else the first to have stood next to it for {@link #LINGER_TICKS} in a row.
     */
    private @Nullable LivingEntity sense(ServerLevel level) {
        Map<Integer, Integer> seen = new HashMap<>();
        LivingEntity touching = null, lingered = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(REACH + 0.01), this::prey)) {
            double gap = gap(e);
            if (gap > REACH) continue;
            int ticks = lingering.getOrDefault(e.getId(), 0) + 1;
            seen.put(e.getId(), ticks);
            if (gap <= TOUCH && gap < nearest) {
                touching = e;
                nearest = gap;
            }
            if (ticks >= LINGER_TICKS && lingered == null) lingered = e;
        }
        lingering.clear();
        lingering.putAll(seen);
        return touching != null ? touching : lingered;
    }

    /** Ticks {@code e} has stood within {@link #REACH} in a row, as of its last look. Public as a test hook. */
    public int lingered(Entity e) {
        return lingering.getOrDefault(e.getId(), 0);
    }

    // ---- the phase machine ---------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!settled) settle();
        if (disguised()) setFacing(gridYaw);
        if (core().in(Step.PULSE)) {
            // the glow runs up through its stages and back down: 0, 1, 2 at the middle of the pulse, 1, 0
            float k = 1.0F - Math.abs(2.0F * core().progress(0.0F) - 1.0F);
            core().setStage(Math.round(k * (PULSE_STAGES - 1)));
        } else if (core().in(Step.TELL)) {
            LivingEntity target = core().target();
            if (target != null && Math.hypot(target.getX() - getX(), target.getZ() - getZ()) > 0.3) {
                float to = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
                setFacing(Mth.approachDegrees(getYRot(), to, TURN_PER_TICK));
            }
            Telegraph.particles(spore(), 6, 0.35, 0.6).trail(this, 3);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (pulseClock < 0) pulseClock = getRandom().nextInt(PULSE_EVERY);
        if (!disguised()) {
            lingering.clear();
            return;
        }
        if (core().idle() && core().stage() != 0) core().setStage(0);     // at rest only the cores glow
        LivingEntity prey = sense(level);
        if (prey != null && spring(prey)) return;
        if (++pulseClock >= PULSE_EVERY && core().idle()) pulse();
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.PULSE) {
            core().setStage(0);
            core().end();
        } else if (ended == Step.TELL) {
            LivingEntity target = core().target();
            if (startPhase(Step.BITE, BITE_TICKS, target)) bite(level, target);
            else rest();
        } else if (ended == Step.BITE) {
            if (!startPhase(Step.RECOVER, RECOVER_TICKS, null)) rest();
        } else {
            rest();
        }
    }

    /** Back in its disguise, squared to the grid again; the next pulse is a full {@link #PULSE_EVERY} away. */
    private void rest() {
        core().end();
        core().setStage(0);
        lingering.clear();
        pulseClock = 0;
        holdGrid(getYRot());
    }

    /**
     * Gives itself away: for {@link #PULSE_TICKS} its glow swells and fades and its top lifts a crack, with a soft throb
     * and a few motes. Returns false if it isn't waiting or an event cancelled it. Public as a test hook.
     */
    public boolean pulse() {
        if (!core().idle()) return false;
        pulseClock = 0;
        if (!startPhase(Step.PULSE, PULSE_TICKS, null)) return false;
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(0.5), getZ(), ModSounds.GLEAMTRAP_PULSE.get(), getSoundSource(), 0.6F, 1.0F);
            level.sendParticles(MOTE, getX(), getY(0.6), getZ(), 5, 0.4, 0.3, 0.4, 0.0);
            level.sendParticles(spore(), getX(), getY(1.0), getZ(), 2, 0.3, 0.05, 0.3, 0.01);
        }
        return true;
    }

    /** Ticks since its last pulse began. Public as a test hook. */
    public int pulseClock() {
        return pulseClock;
    }

    /**
     * Springs the trap at {@code target}: for {@link #TELL_TICKS} it splits open, teeth showing, and turns toward it;
     * the bite follows. Returns false if it isn't disguised or an event cancelled it. Public as a test hook.
     */
    public boolean spring(@Nullable LivingEntity target) {
        if (!disguised() || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        lingering.clear();
        core().setStage(0);
        Telegraph.particles(spore(), 12, 0.4, 0.7).withSpeed(0.03).withSound(ModSounds.GLEAMTRAP_TELL.get(), 1.0F, 1.0F).play(this);
        return true;
    }

    /**
     * The lid slams shut on {@code target}: 1.5x Atk if it is still within {@link #REACH}, else the jaws close on
     * nothing. Returns whether it hurt. Public as a test hook.
     */
    public boolean bite(ServerLevel level, @Nullable LivingEntity target) {
        level.playSound(null, getX(), getY(0.5), getZ(), ModSounds.GLEAMTRAP_BITE.get(), getSoundSource(), 1.0F, 1.0F);
        boolean inReach = target != null && prey(target) && gap(target) <= REACH;
        if (inReach && MechanicEvent.post(this, "bite_hit", target)) {
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE_HIT;
            if (target.hurtServer(level, damageSources().mobAttack(this), damage)) {
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                level.sendParticles(throat(), getX(), getY(0.6), getZ(), 6, 0.3, 0.1, 0.3, 0.0);
                return true;
            }
            return false;
        }
        // the counter landed: the jaws close on empty air
        level.playSound(null, getX(), getY(0.5), getZ(), ModSounds.GLEAMTRAP_MISS.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.POOF, getX(), getY(1.0), getZ(), 5, 0.3, 0.05, 0.3, 0.02);
        level.sendParticles(spore(), getX(), getY(0.8), getZ(), 6, 0.35, 0.1, 0.35, 0.02);
        return false;
    }

    /** Any blow springs it at whoever struck it (or shot it), near or far; only those within reach are bitten. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && disguised() && source.getEntity() instanceof LivingEntity attacker) spring(attacker);
        return hurt;
    }

    private ParticleOptions spore() {
        return "warped_gleamtrap".equals(look()) ? ParticleTypes.WARPED_SPORE : ParticleTypes.CRIMSON_SPORE;
    }

    private DustParticleOptions throat() {
        return new DustParticleOptions("warped_gleamtrap".equals(look()) ? 0x5FF0D0 : 0xFFB347, 1.2F);
    }

    // ---- sounds --------------------------------------------------------------------------------------------------------

    /** A shroomlight makes no sound: it creaks only while it is open. */
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return disguised() ? null : ModSounds.GLEAMTRAP_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GLEAMTRAP_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GLEAMTRAP_DEATH.get();
    }
}
