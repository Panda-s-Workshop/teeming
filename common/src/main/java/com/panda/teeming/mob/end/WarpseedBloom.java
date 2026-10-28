package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Shot;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.end.Riftjackal.SafeTeleport;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Warpseed Bloom: a chorus-flower turret of the End highlands, a knotted stalk rooted where it grew with a violet bulb
 * on top. It never walks and nothing pushes it off its spot; it only turns its bulb toward its target.
 *
 * <p>Tell: for 12 ticks the bulb swells and its veins glow lilac, with a creak that rises in pitch. Then it spits a
 * warpseed at its target: a slow lilac seed that flies straight at where the target was, 0.5 blocks a tick, out to 16
 * blocks, and stops at the first block or body in its way. A hit deals 3 and throws the one it hit 3 to 6 blocks in a
 * random direction onto safe ground (never over the void, below the islands, into lava or into a wall); with no safe
 * spot in reach, the throw does nothing. One seed every 60 ticks, at a target within 16 it can see.
 *
 * <p>Counter: step out of the seed's line, or raise a shield toward the bloom. A shield takes the seed: no damage and
 * no throw. The blocked seed bursts back on the bloom, which can't be thrown because it is rooted, so the warp tears
 * its bulb instead: the bloom takes the seed's 3, and the bulb hangs split and dark for 100 ticks, when it spits
 * nothing.
 */
public class WarpseedBloom extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL, SPIT, WILTED }

    /** 0.6 s of swelling, glowing and creaking before the spit. */
    public static final int TELL_TICKS = 12;
    /** The bulb's recoil after a spit. */
    public static final int SPIT_TICKS = 6;
    /** Ticks from one seed to the next, the tell included. */
    public static final int SPIT_EVERY = 60;
    /** It spits at targets this close, and a seed flies this far. */
    public static final double RANGE = 16.0;
    public static final float SEED_DAMAGE = 3.0F;
    /** Blocks a tick: 16 blocks in 32 ticks, slow enough to see coming and step aside. */
    public static final double SEED_SPEED = 0.5;
    /** How far from a hitbox a seed still strikes. */
    public static final double SEED_RADIUS = 0.2;
    /** A hit throws its target this far across, in a random direction. */
    public static final double WARP_NEAR = 3.0;
    public static final double WARP_FAR = 6.0;
    /** A blocked seed tears the bulb: 5 s with no seeds, and the bloom takes the seed's own damage. */
    public static final int WILT_TICKS = 100;
    public static final float REBOUND_DAMAGE = 3.0F;
    /** What the seed weighs on a raised shield (its damage, so the shield wears as from any 3-damage shot). */
    public static final float SHIELD_WEIGHT = 3.0F;
    /** The heaviest turn it makes in a tick to face its target, in degrees. */
    public static final float TURN_PER_TICK = 12.0F;
    /** The pore seeds leave from, from its feet at scale 1: up in the bulb, a little forward. */
    public static final double PORE_HEIGHT = 1.15;
    public static final double PORE_FORWARD = 0.35;
    /** The middle of the bulb, from its feet at scale 1. */
    public static final double BULB_HEIGHT = 1.15;

    /** Glow stages: the bulb dark, and its veins lit through the tell. */
    public static final int GLOW_DARK = 0;
    public static final int GLOW_LIT = 1;

    /** The seed's lilac, and the paler core and star-white glints it trails. */
    public static final int SEED_COLOR = 0xC9A2F0;
    private static final DustParticleOptions SEED_DUST = new DustParticleOptions(SEED_COLOR, 2.0F);
    private static final DustParticleOptions SEED_CORE = new DustParticleOptions(0xF4E8FF, 1.2F);
    private static final DustParticleOptions SEED_WAKE = new DustParticleOptions(0x9A6CC8, 0.8F);
    private static final DustParticleOptions BULB_DUST = new DustParticleOptions(0xB98AE0, 1.3F);
    private static final DustParticleOptions WILT_DUST = new DustParticleOptions(0x6E5478, 1.0F);

    /**
     * Seeds in flight. A seed is not an entity: the bloom moves it {@link #SEED_SPEED} along a straight line each tick,
     * stops it at the first block or hitbox on that stretch, and draws it with particles, so it can be dodged and it
     * never homes. Seeds are not saved; a reload drops the ones in the air.
     */
    private final List<Shot> seeds = new ArrayList<>();
    private final Shot.Rules rules = new Shot.Rules(this::seedCanHit, this::trail,
            (level, shot, victim, at) -> strike(level, victim, at), (level, shot, at, blocked) -> burst(level, at));
    private int cooldown;
    private int warps;
    private int blocks;
    private int wiltOnLoad;
    private @Nullable LivingEntity pendingTell;
    private @Nullable Warp fixedWarp;

    public WarpseedBloom(EntityType<? extends WarpseedBloom> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("warpseed_bloom", 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public int visualStages() {
        return 2;
    }

    /** Every phase it enters sets its glow: the veins lit through the tell, dark otherwise. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.TELL ? GLOW_LIT : GLOW_DARK);
        return true;
    }

    private void rest() {
        core().end();
        core().setStage(GLOW_DARK);
    }

    // ---- rooted: it turns, nothing more ----------------------------------------------------------------------------

    /** Only falling moves it: walking, knockback, pistons and currents all lose their sideways part. */
    @Override
    public Pin pin() {
        return Pin.ROOTED;
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

    /** Body and head are one piece: the body always faces where the head does. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        this.yBodyRot = this.yHeadRot;
    }

    private void turnToward(Vec3 point) {
        setFacing(Mth.approachDegrees(getYRot(), yawTo(point), TURN_PER_TICK));
    }

    private void setFacing(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Turns to face {@code point} at once. Public as a test hook. */
    public void face(Vec3 point) {
        setFacing(yawTo(point));
        yRotO = getYRot();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
    }

    private float yawTo(Vec3 point) {
        return (float) (Mth.atan2(point.z - getZ(), point.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    /** Where seeds leave: the pore on the front of the bulb. */
    public Vec3 pore() {
        return position().add(forward().scale(PORE_FORWARD * getScale())).add(0.0, PORE_HEIGHT * getScale(), 0.0);
    }

    /** The middle of the bulb. */
    public Vec3 bulbCentre() {
        return position().add(0.0, BULB_HEIGHT * getScale(), 0.0);
    }

    // ---- the spit cycle --------------------------------------------------------------------------------------------

    private static boolean canAim(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        boolean valid = canAim(target);
        if (valid && !core().in(Step.WILTED)) turnToward(target.position());
        if (!valid || !core().idle() || cooldown > 0) return;
        if (distanceTo(target) <= RANGE && getSensing().hasLineOfSight(target)) pendingTell = target;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (wiltOnLoad > 0) {
            // a bulb torn before a save is still torn after it
            int left = wiltOnLoad;
            wiltOnLoad = 0;
            startPhase(Step.WILTED, left, null);
        }
        // a tell starts after this tick's phase clock has run, so it lasts exactly TELL_TICKS
        if (pendingTell != null) {
            LivingEntity target = pendingTell;
            pendingTell = null;
            tell(target);
        }
        phaseCues(level);
        if (!seeds.isEmpty()) seeds.removeIf(shot -> shot.step(level, this, SEED_RADIUS, rules));
    }

    /**
     * Starts the tell at {@code target} if it is idle; ignores range, sight and cooldown. Returns false if it is busy
     * or an event cancelled it. Public as a test hook.
     */
    public boolean tell(@Nullable LivingEntity target) {
        if (!core().idle() || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        cooldown = SPIT_EVERY;
        Telegraph.particles(BULB_DUST, 10, 0.35, 0.8).withSound(ModSounds.WARPSEED_BLOOM_TELL.get(), 1.0F, 0.75F).play(this);
        return true;
    }

    /**
     * The tell's cues, from the phase alone so they show however it began: lilac motes drawn into the swelling bulb,
     * and the creak played again higher at a third and two thirds of the way. The torn bulb sheds dark flakes.
     */
    private void phaseCues(ServerLevel level) {
        Vec3 b = bulbCentre();
        if (core().in(Step.TELL)) {
            int t = core().elapsed();
            if (t % 2 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, b.x, b.y, b.z, 3, 0.5, 0.4, 0.5, 0.02);
            if (t % 3 == 0) level.sendParticles(BULB_DUST, true, true, b.x, b.y, b.z, 2, 0.3, 0.25, 0.3, 0.0);
            if (t == TELL_TICKS / 3 || t == 2 * TELL_TICKS / 3) {
                float pitch = t == TELL_TICKS / 3 ? 1.0F : 1.3F;
                level.playSound(null, b.x, b.y, b.z, ModSounds.WARPSEED_BLOOM_TELL.get(), getSoundSource(), 1.0F, pitch);
            }
        } else if (core().in(Step.WILTED) && tickCount % 8 == 0) {
            level.sendParticles(WILT_DUST, true, true, b.x, b.y - 0.4, b.z, 1, 0.25, 0.1, 0.25, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            LivingEntity target = canAim(getTarget()) ? getTarget() : core().target();
            if (target != null && target.isAlive() && startPhase(Step.SPIT, SPIT_TICKS, target)) spit(level, target);
            else rest();
        } else {
            rest();
        }
    }

    /**
     * Spits one seed from the pore straight at the middle of {@code target}, where it is now. Returns false if an
     * event cancelled it. Public as a test hook.
     */
    public boolean spit(ServerLevel level, LivingEntity target) {
        return spit(level, target.getBoundingBox().getCenter(), target);
    }

    /**
     * Spits one seed from the pore straight at {@code point}; {@code target} is who it was meant for, if anyone.
     * Returns false if an event cancelled it. Public as a test hook.
     */
    public boolean spit(ServerLevel level, Vec3 point, @Nullable LivingEntity target) {
        if (!MechanicEvent.post(this, "seed", target)) return false;
        Vec3 from = pore();
        Vec3 aim = point.subtract(from);
        if (aim.lengthSqr() < 1.0E-6) aim = forward();
        seeds.add(new Shot(Shot.Path.line(from, aim, SEED_SPEED, RANGE)));
        level.sendParticles(SEED_DUST, true, true, from.x, from.y, from.z, 6, 0.12, 0.12, 0.12, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, from.x, from.y, from.z, 6, 0.15, 0.15, 0.15, 0.05);
        level.playSound(null, from.x, from.y, from.z, ModSounds.WARPSEED_BLOOM_SPIT.get(), getSoundSource(), 1.0F, 1.0F);
        return true;
    }

    // ---- seeds -----------------------------------------------------------------------------------------------------

    private boolean seedCanHit(LivingEntity e) {
        return e != this && !(e instanceof WarpseedBloom) && e.canBeHitByProjectile() && !e.isSpectator();
    }

    /** The seed: a bright lilac ball with a pale core at its head, a star-white glint and a thin wake behind it. */
    private void trail(ServerLevel level, Vec3 from, Vec3 to) {
        level.sendParticles(SEED_DUST, true, true, to.x, to.y, to.z, 3, 0.03, 0.03, 0.03, 0.0);
        level.sendParticles(SEED_CORE, true, true, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
        Vec3 mid = from.lerp(to, 0.5);
        level.sendParticles(SEED_WAKE, true, true, mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
        if (tickCount % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, true, true, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.0);
    }

    /** The seed reaches a body: a raised shield toward the bloom takes it; anything else takes 3 and is thrown. */
    private void strike(ServerLevel level, LivingEntity victim, Vec3 at) {
        if (shieldBlocks(level, victim)) {
            blocked(level, victim, at);
            return;
        }
        level.sendParticles(SEED_DUST, true, true, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.WARPSEED_BLOOM_SEED_HIT.get(), getSoundSource(), 1.0F, 1.0F);
        if (!MechanicEvent.post(this, "seed_hit", victim)) return;
        AstralAugur.GroundMarks.hurtInPlace(level, victim, Shot.damage(this), SEED_DAMAGE);
        if (victim.isAlive()) warp(level, victim);
    }

    /**
     * True if {@code victim} holds up a shield that takes the seed: vanilla's own blocking rule, with the seed coming
     * from the bloom like any mob's projectile (so the shield has to face it).
     */
    private boolean shieldBlocks(ServerLevel level, LivingEntity victim) {
        return victim.isBlocking() && victim.applyItemBlocking(level, Shot.damage(this), SHIELD_WEIGHT) > 0.0F;
    }

    /** Who a seed may throw: players and mobs that portals take, never a creative or spectating player. */
    private static boolean warpable(LivingEntity e) {
        return e.isAlive() && (e instanceof Mob || e instanceof Player) && e.canUsePortal(false) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    /** The distance and direction of one throw. */
    public record Warp(double distance, double angle) {}

    /** A throw's distance for a roll {@code u} in 0..1: 3 blocks at 0, 6 at 1, evenly between. */
    public static double warpDistance(double u) {
        return WARP_NEAR + (WARP_FAR - WARP_NEAR) * Mth.clamp(u, 0.0, 1.0);
    }

    /**
     * Throws {@code victim} {@link #warpDistance} blocks in a random direction onto safe ground; when the first
     * direction is unsafe the next ones round the circle are tried at the same distance, and with none safe nothing
     * happens. Returns where it landed, or null.
     */
    private @Nullable Vec3 warp(ServerLevel level, LivingEntity victim) {
        if (!warpable(victim) || !MechanicEvent.post(this, "warp", victim)) return null;
        Warp roll = fixedWarp != null ? fixedWarp : new Warp(warpDistance(random.nextDouble()), random.nextDouble() * Math.PI * 2);
        Vec3 from = victim.position();
        Vec3 to = SafeTeleport.InLevel.find(level, victim, from, roll.distance(), roll.angle(), p -> true);
        if (to == null) {
            level.sendParticles(WILT_DUST, true, true, from.x, from.y + victim.getBbHeight() * 0.5, from.z, 6, 0.25, 0.3, 0.25, 0.0);
            return null;
        }
        puff(level, from, victim);
        SafeTeleport.InLevel.move(level, victim, to);
        puff(level, to, victim);
        level.playSound(null, from.x, from.y, from.z, ModSounds.WARPSEED_BLOOM_WARP.get(), getSoundSource(), 1.0F, 1.0F);
        level.playSound(null, to.x, to.y, to.z, ModSounds.WARPSEED_BLOOM_WARP.get(), getSoundSource(), 0.8F, 1.2F);
        warps++;
        return to;
    }

    private static void puff(ServerLevel level, Vec3 at, LivingEntity who) {
        double y = at.y + who.getBbHeight() * 0.5;
        level.sendParticles(ParticleTypes.PORTAL, true, true, at.x, y, at.z, 24, 0.3, 0.4, 0.3, 0.4);
        level.sendParticles(SEED_DUST, true, true, at.x, y, at.z, 8, 0.3, 0.4, 0.3, 0.0);
    }

    /**
     * The counter: the shield takes the seed, which bursts back on the bloom. The warp can't move a rooted plant, so it
     * tears the bulb: the bloom takes {@link #REBOUND_DAMAGE} and wilts for {@link #WILT_TICKS}, spitting nothing.
     */
    private void blocked(ServerLevel level, LivingEntity blocker, Vec3 at) {
        level.sendParticles(ParticleTypes.CRIT, true, true, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.3);
        level.sendParticles(SEED_DUST, true, true, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.WARPSEED_BLOOM_BLOCKED.get(), getSoundSource(), 1.0F, 1.0F);
        blocks++;
        if (!MechanicEvent.post(this, "rebound", blocker)) return;
        // the burst races back along the seed's line to the bulb
        Vec3 b = bulbCentre();
        int points = Math.max(1, (int) Math.ceil(at.distanceTo(b) / 0.4));
        for (int i = 1; i <= points; i++) {
            Vec3 p = at.lerp(b, (double) i / points);
            level.sendParticles(SEED_WAKE, true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        wilt(level, blocker);
    }

    /**
     * Tears the bulb: it wilts for 100 ticks, with any tell cut short, and the bloom takes the seed's 3 (credited to
     * {@code by}). Returns false if an event cancelled it. Public as a test hook.
     */
    public boolean wilt(ServerLevel level, @Nullable LivingEntity by) {
        if (!startPhase(Step.WILTED, WILT_TICKS, by)) return false;
        pendingTell = null;
        Vec3 b = bulbCentre();
        level.sendParticles(ParticleTypes.PORTAL, true, true, b.x, b.y, b.z, 30, 0.35, 0.35, 0.35, 0.5);
        level.sendParticles(BULB_DUST, true, true, b.x, b.y, b.z, 14, 0.3, 0.3, 0.3, 0.0);
        level.sendParticles(WILT_DUST, true, true, b.x, b.y, b.z, 10, 0.3, 0.3, 0.3, 0.0);
        level.playSound(null, b.x, b.y, b.z, ModSounds.WARPSEED_BLOOM_WILT.get(), getSoundSource(), 1.2F, 1.0F);
        hurtServer(level, damageSources().indirectMagic(this, by != null ? by : this), REBOUND_DAMAGE);
        return true;
    }

    /** A seed that hit nothing breaks where it stopped, so a dodge shows where the shot went. */
    private void burst(ServerLevel level, Vec3 at) {
        level.sendParticles(SEED_DUST, true, true, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.05);
        level.playSound(null, at.x, at.y, at.z, ModSounds.WARPSEED_BLOOM_SEED_BURST.get(), getSoundSource(), 0.6F, 1.0F);
    }

    // ---- saving ----------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SeedCooldown", cooldown);
        if (core().in(Step.WILTED)) output.putInt("WiltLeft", Math.max(1, core().length() - core().elapsed()));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        cooldown = input.getIntOr("SeedCooldown", 0);
        wiltOnLoad = input.getIntOr("WiltLeft", 0);
    }

    // ---- state and test hooks --------------------------------------------------------------------------------------

    /** Ticks until it may start its next tell. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it start a tell at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    /** True while its bulb is torn. */
    public boolean wilted() {
        return core().in(Step.WILTED);
    }

    /** How many of its seeds are in the air. */
    public int seedsInFlight() {
        return seeds.size();
    }

    /** How many things its seeds have thrown since it was spawned. */
    public int warps() {
        return warps;
    }

    /** How many of its seeds a shield has taken since it was spawned. */
    public int blocks() {
        return blocks;
    }

    /**
     * Makes every later throw use {@code warp} instead of a roll (null rolls again), so a test can keep the landing
     * inside its arena. Public as a test hook.
     */
    public void fixWarp(@Nullable Warp warp) {
        fixedWarp = warp;
    }

    /** Where each seed in the air is, oldest first. */
    public List<Vec3> seedPositions() {
        List<Vec3> out = new ArrayList<>(seeds.size());
        for (Shot s : seeds) out.add(s.pos());
        return out;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.WARPSEED_BLOOM_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.WARPSEED_BLOOM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.WARPSEED_BLOOM_DEATH.get();
    }
}
