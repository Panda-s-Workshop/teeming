package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.VoidDrops;
import com.panda.teeming.mob.end.Riftjackal.SafeTeleport;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Pearlgulp: a squat, wide-mouthed void creature of the End highlands and small islands, with a pouch throat slung under
 * its jaw. It watches the air for ender pearls: any pearl in flight within {@link #EAT_RANGE} blocks of its mouth, in
 * its sight, is snapped out of the air and swallowed (the pouch swells). The pearl is gone and its thrower is not
 * teleported (the step "eat_pearl", aimed at the pearl's thrower). It holds up to {@link #MAX_HELD}; with a full pouch it
 * lets pearls fly past.
 *
 * <p>Each pearl it holds is one blink of exactly {@link #BLINK_DISTANCE} blocks onto safe ground
 * ({@link SafeTeleport}: never over the void, into lava or into a wall), at most one blink every {@link #BLINK_EVERY}
 * ticks. It blinks for two reasons only:
 * <ul>
 *   <li>to close on a target farther off than {@link #CLOSE_FROM} (and within {@link #BLINK_REACH}, in its sight): an
 *   8-tick tell first (it rears, the mouth opens, the pouch blazes lilac-white, a chime, and a shimmer where it will
 *   land), then it lands toward the target, never closer than {@link #LAND_GAP} to it;</li>
 *   <li>to escape when a blow leaves it under half its health: it blinks straight away from whoever struck it, onto a
 *   spot at least {@link #NEAR_PLAYER} blocks from every player, so the escape needs no tell.</li>
 * </ul>
 * Each blink spends one pearl for good.
 *
 * <p>Its bite (its attack, 8.5) comes after an 8-tick gape: the jaw drops wide and it hums; the bite lands if the target
 * is still within {@link #BITE_HOLD}. At most one gape every {@link #BITE_EVERY} ticks.
 *
 * <p>Counter: don't throw pearls near it. If it has eaten some, stand and fight: it blinks only to reach a target far
 * off or to flee once badly hurt, and each blink burns a pearl's power. Killed, it spills every pearl it ate, burnt or
 * not (the payoff: the pearls back, never lost for good). It never despawns while it owes one, and a removal that
 * destroys it spills them too.
 */
public class Pearlgulp extends TeemingMonster {
    /** IDLE, the throat flare before a blink (tell), the landing, the gulp, the gape before a bite, the bite. */
    public enum Step implements Phase { IDLE, TELL, BLINK, GULP, GAPE, BITE }

    /** A pearl in flight this close to its mouth, in its sight, is eaten. */
    public static final double EAT_RANGE = 8.0;
    /** Every blink is exactly this far. */
    public static final double BLINK_DISTANCE = 8.0;
    /** It holds at most this many pearls (a stack); with a full pouch it eats no more. */
    public static final int MAX_HELD = 16;
    /** The throat flare before a blink toward a target: 0.4 s. */
    public static final int TELL_TICKS = 8;
    public static final int BLINK_TICKS = 6;
    public static final int GULP_TICKS = 6;
    /** The gape before a bite: 0.4 s. */
    public static final int GAPE_TICKS = 8;
    public static final int BITE_TICKS = 6;
    /** The bite: this many times its attack. */
    public static final float BITE = 1.0F;
    /** It starts a gape at a target this close, and the bite lands on one still this close. */
    public static final double BITE_START = 2.0;
    public static final double BITE_HOLD = 2.5;
    /** At most one gape every this many ticks, counted from gape to gape. */
    public static final int BITE_EVERY = 20;
    /** At most one blink every this many ticks, counted from blink to blink. */
    public static final int BLINK_EVERY = 60;
    /** It blinks toward a target only when the target is farther off than this, and no farther than {@link #BLINK_REACH}. */
    public static final double CLOSE_FROM = 6.0;
    public static final double BLINK_REACH = 16.0;
    /** A closing blink lands at least this far from its target (never on it). */
    public static final double LAND_GAP = 1.25;
    /** An escape lands at least this far from every player, so it lands next to no one and needs no tell. */
    public static final double NEAR_PLAYER = 3.0;
    /** A blow that leaves it under this share of its health sends it blinking away. */
    public static final float ESCAPE_BELOW = 0.5F;

    /** Glow stages: an empty pouch, one, two, three or more pearls, and the flare before a blink. */
    public static final int GLOW_EMPTY = 0;
    public static final int GLOW_FLARE = 4;

    private static final long NEVER = Long.MIN_VALUE;
    private static final DustParticleOptions FLARE = new DustParticleOptions(0xD9B8FF, 1.0F);
    private static final DustParticleOptions SHIMMER = new DustParticleOptions(0xB98AE4, 0.8F);
    private static final DustParticleOptions PEARL = new DustParticleOptions(0x2FA58D, 0.8F);
    private static final Telegraph GAPE = Telegraph.particles(SHIMMER, 6, 0.3, 0.4).withSpeed(0.0)
            .withSound(ModSounds.PEARLGULP_GAPE.get(), 1.0F, 1.0F);

    private int held;
    private final Footing footing = new Footing();
    /** Pearls burnt on blinks: their power is spent, but they are still owed back when it dies. */
    private int spent;
    private long blinkAt = NEVER;
    private long gapeAt = NEVER;
    /** Where the blink it is flaring for will land, picked as the flare starts. */
    private @Nullable Vec3 landing;
    private int eaten;
    private int blinks;
    private int escapes;
    private int bites;
    private double lastGulpDistance = Double.NaN;

    public Pearlgulp(EntityType<? extends Pearlgulp> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("pearlgulp", 0.24);
    }

    @Override
    public int visualStages() {
        return 5;
    }

    // ---- goals ---------------------------------------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal());
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

    /** With a target it waddles up to it and stops in bite reach; the bite itself is the gape and the snap. */
    private final class ChaseGoal extends Goal {
        private int repath;

        ChaseGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return core().idle() && canBite(getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--repath > 0) return;
            repath = 10;
            if (distanceTo(target) > BITE_START * 0.8) getNavigation().moveTo(target, 1.0);
            else getNavigation().stop();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    private boolean canBite(@Nullable LivingEntity e) {
        return e != null && e != this && e.isAlive() && !(e instanceof Pearlgulp) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    // ---- the gulp: pearls in flight ----------------------------------------------------------------------------------

    /** The watch for pearls runs from {@code tick()}, so it works whatever its AI is doing. */
    @Override
    public void tick() {
        super.tick();
        footing.track(this);
        if (level() instanceof ServerLevel level && isAlive() && held < MAX_HELD) watch(level);
    }

    /** Where its mouth is: the front of its head, low. */
    public Vec3 mouth() {
        Vec3 ahead = Vec3.directionFromRotation(0.0F, yBodyRot);
        return position().add(ahead.scale(getBbWidth() * 0.6)).add(0.0, getBbHeight() * 0.45, 0.0);
    }

    private void watch(ServerLevel level) {
        Vec3 mouth = mouth();
        List<ThrownEnderpearl> pearls = level.getEntitiesOfClass(ThrownEnderpearl.class, getBoundingBox().inflate(EAT_RANGE + 1.0),
                p -> p.isAlive() && p.position().distanceToSqr(mouth) <= EAT_RANGE * EAT_RANGE);
        for (ThrownEnderpearl pearl : pearls) {
            if (held >= MAX_HELD) return;
            if (!sees(level, mouth, pearl.position())) continue;
            gulp(level, pearl, mouth);
        }
    }

    private boolean sees(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Snaps {@code pearl} out of the air: it is discarded (so nothing teleports) and the pouch holds one more. */
    private void gulp(ServerLevel level, ThrownEnderpearl pearl, Vec3 mouth) {
        Entity owner = pearl.getOwner();
        if (!MechanicEvent.post(this, "eat_pearl", owner instanceof LivingEntity l ? l : null)) return;
        Vec3 at = pearl.position();
        lastGulpDistance = at.distanceTo(mouth);
        pearl.discard();
        held++;
        eaten++;
        Vec3 d = mouth.subtract(at);
        int n = Math.max(4, (int) Math.ceil(d.length() * 2.0));
        for (int i = 0; i <= n; i++) {                                     // a lick of light from the pearl to its mouth
            Vec3 p = at.add(d.scale(i / (double) n));
            level.sendParticles(i % 2 == 0 ? PEARL : SHIMMER, true, true, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, mouth.x, mouth.y, mouth.z, 12, 0.2, 0.15, 0.2, 0.05);
        level.playSound(null, mouth.x, mouth.y, mouth.z, ModSounds.PEARLGULP_GULP.get(), getSoundSource(), 1.0F, 1.0F);
        level.playSound(null, at.x, at.y, at.z, ModSounds.PEARLGULP_GULP.get(), getSoundSource(), 0.6F, 1.2F);
        face(at.subtract(position()));
        if (core().idle()) startPhase(Step.GULP, GULP_TICKS, null);
        showPouch();
    }

    // ---- the AI step: the closing blink and the bite -----------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (core().in(Step.TELL)) {
            holdStill();
            if (target != null) face(target.position().subtract(position()));
            flareTick(level);
        } else if (core().in(Step.GAPE)) {
            holdStill();
            LivingEntity t = core().target();
            if (t != null) face(t.position().subtract(position()));
            if (core().elapsed() % 3 == 0) GAPE.trail(this, 1);
        } else if (core().in(Step.BLINK) || core().in(Step.BITE) || core().in(Step.GULP)) {
            holdStill();
        } else if (core().idle() && canBite(target)) {
            long now = level.getGameTime();
            double d = distanceTo(target);
            if (d <= BITE_START && (gapeAt == NEVER || now - gapeAt >= BITE_EVERY) && hasLineOfSight(target)) {
                gape(target);
            } else if (d > CLOSE_FROM) {
                tryCloseIn(level, target, now);
            }
        }
    }

    /** True when a closing blink at {@code target} is due: a pearl held, the cooldown over, the target in reach and sight. */
    private boolean blinkDue(LivingEntity target, long now) {
        double d = distanceTo(target);
        return held > 0 && (blinkAt == NEVER || now - blinkAt >= BLINK_EVERY) && d > CLOSE_FROM && d <= BLINK_REACH
                && onGround() && !isInWater() && hasLineOfSight(target);
    }

    private void tryCloseIn(ServerLevel level, LivingEntity target, long now) {
        if (!blinkDue(target, now)) return;
        Vec3 spot = closingSpot(level, target);
        if (spot == null || !startPhase(Step.TELL, TELL_TICKS, target)) return;
        landing = spot;
        holdStill();
        level.playSound(null, getX(), getY(), getZ(), ModSounds.PEARLGULP_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        level.playSound(null, spot.x, spot.y, spot.z, ModSounds.PEARLGULP_TELL.get(), getSoundSource(), 0.5F, 1.3F);
    }

    /**
     * Where a closing blink at {@code target} lands: exactly {@link #BLINK_DISTANCE} away, the first safe spot turning
     * out from straight at the target, nearer the target than it stands now and at least {@link #LAND_GAP} from it.
     * Null when no spot is. Public as a test hook.
     */
    public @Nullable Vec3 closingSpot(ServerLevel level, LivingEntity target) {
        double toward = Math.atan2(target.getZ() - getZ(), target.getX() - getX());
        double now = flat(position(), target.position());
        return SafeTeleport.InLevel.find(level, this, position(), BLINK_DISTANCE, toward, p -> {
            double gap = flat(p, target.position());
            return gap >= LAND_GAP && gap < now;
        });
    }

    /** The flare: the pouch blazes, lilac motes rise from its throat, and a shimmer gathers where it will land. */
    private void flareTick(ServerLevel level) {
        if (core().elapsed() % 2 != 0) return;
        Vec3 m = mouth();
        level.sendParticles(FLARE, true, true, m.x, m.y - 0.15, m.z, 4, 0.25, 0.1, 0.25, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, m.x, m.y, m.z, 3, 0.2, 0.1, 0.2, 0.02);
        if (landing != null) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, landing.x, landing.y + 0.2, landing.z, 6, 0.3, 0.05, 0.3, 0.03);
            for (int i = 0; i < 8; i++) {
                double a = i * (Math.PI / 4) + core().elapsed() * 0.4;
                level.sendParticles(SHIMMER, true, true, landing.x + Math.cos(a) * 0.6, landing.y + 0.1, landing.z + Math.sin(a) * 0.6, 1, 0.0, 0.02, 0.0, 0.0);
            }
        }
    }

    /** The spot its current flare will land on, or null. Public as a test hook. */
    public @Nullable Vec3 landing() {
        return landing;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            rest();
            return;
        }
        if (ended == Step.TELL) closeIn(level);
        else if (ended == Step.GAPE) bite(level, core().target());
        else rest();
    }

    /** Every phase it enters sets its glow: the flare through the tell, the pearls it holds otherwise. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        showPouch();
        return true;
    }

    private void rest() {
        core().end();
        showPouch();
    }

    /** The pouch glow: blazing through the flare, else one stage per pearl held, up to three. */
    private void showPouch() {
        core().setStage(core().in(Step.TELL) ? GLOW_FLARE : Math.min(held, 3));
    }

    /** The closing blink as the flare ends: to the landing it picked, if that is still safe, spending one pearl. */
    private void closeIn(ServerLevel level) {
        LivingEntity target = core().target();
        Vec3 to = landing;
        landing = null;
        if (to != null) to = SafeTeleport.InLevel.spot(level, this, to.x, to.y, to.z);
        if (to == null || held <= 0 || !MechanicEvent.post(this, "blink", target)) {
            rest();
            return;
        }
        jump(level, to, ModSounds.PEARLGULP_BLINK.get());
        if (target != null) face(target.position().subtract(to));
        blinks++;
        if (!startPhase(Step.BLINK, BLINK_TICKS, target)) rest();
    }

    /** Moves it to {@code to} with a puff at both ends, spending one pearl and starting the blink cooldown. */
    private void jump(ServerLevel level, Vec3 to, SoundEvent sound) {
        Vec3 from = position();
        puff(level, from);
        level.playSound(null, from.x, from.y, from.z, sound, getSoundSource(), 1.0F, 1.0F);
        SafeTeleport.InLevel.move(level, this, to);
        puff(level, to);
        level.playSound(null, to.x, to.y, to.z, sound, getSoundSource(), 1.0F, 1.15F);
        held--;
        spent++;
        blinkAt = level.getGameTime();
        showPouch();
    }

    private static void puff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, true, true, at.x, at.y + 0.4, at.z, 28, 0.35, 0.3, 0.35, 0.4);
        level.sendParticles(PEARL, true, true, at.x, at.y + 0.3, at.z, 8, 0.3, 0.2, 0.3, 0.0);
    }

    // ---- the bite ----------------------------------------------------------------------------------------------------

    private void gape(LivingEntity target) {
        if (!startPhase(Step.GAPE, GAPE_TICKS, target)) return;
        gapeAt = level().getGameTime();
        holdStill();
        face(target.position().subtract(position()));
        GAPE.play(this);
    }

    /** The end of the gape: the bite lands, for its attack, on a target still within {@link #BITE_HOLD}. */
    private void bite(ServerLevel level, @Nullable LivingEntity victim) {
        if (canBite(victim) && distanceTo(victim) <= BITE_HOLD && MechanicEvent.post(this, "bite", victim)) {
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE;
            if (AstralAugur.GroundMarks.hurtInPlace(level, victim, damageSources().mobAttack(this), damage)) {
                bites++;
                setLastHurtMob(victim);
            }
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.PEARLGULP_BITE.get(), getSoundSource(), 1.0F, 1.0F);
        if (!startPhase(Step.BITE, BITE_TICKS, victim)) rest();
    }

    // ---- the escape --------------------------------------------------------------------------------------------------

    /** A blow that leaves it under half its health, with a pearl held and its blink ready, sends it blinking away. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && getHealth() < getMaxHealth() * ESCAPE_BELOW) escape(level, source.getEntity());
        return hurt;
    }

    private void escape(ServerLevel level, @Nullable Entity from) {
        long now = level.getGameTime();
        if (held <= 0 || (blinkAt != NEVER && now - blinkAt < BLINK_EVERY)) return;
        Vec3 away = from != null ? position().subtract(from.position()) : Vec3.directionFromRotation(0.0F, getYRot() + 180.0F);
        double first = away.horizontalDistanceSqr() < 1.0E-6 ? random.nextDouble() * Math.PI * 2 : Math.atan2(away.z, away.x);
        Vec3 to = SafeTeleport.InLevel.find(level, this, position(), BLINK_DISTANCE, first, p -> clearOfPlayers(level, p));
        if (to == null || !MechanicEvent.post(this, "escape", from instanceof LivingEntity l ? l : null)) return;
        landing = null;
        jump(level, to, ModSounds.PEARLGULP_ESCAPE.get());
        if (from != null) face(from.position().subtract(to));
        escapes++;
        if (!startPhase(Step.BLINK, BLINK_TICKS, null)) rest();
    }

    /** True when no player (spectators aside) stands within {@link #NEAR_PLAYER} of {@code at}. */
    private static boolean clearOfPlayers(ServerLevel level, Vec3 at) {
        for (Player p : level.players()) {
            if (!p.isSpectator() && p.position().distanceToSqr(at) < NEAR_PLAYER * NEAR_PLAYER) return false;
        }
        return true;
    }

    // ---- the payoff: the pearls back ---------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // what it owes goes where its own drops go: to the killer when it would be lost in lava or the void
        if (level() instanceof ServerLevel level) VoidDrops.drop(level, this, source, () -> spill(level));
    }

    /** Removed for good some other way (Peaceful, a command): it still never destroys the pearls it owes. */
    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level && reason.shouldDestroy() && owed() > 0) spill(level);
        super.remove(reason);
    }

    /**
     * Spills every pearl it owes (held and burnt) where it stands, in a little burst (on the last ground it stood on, if
     * it died in the void below the world).
     */
    private void spill(ServerLevel level) {
        if (owed() <= 0) return;
        int n = owed();
        held = 0;
        spent = 0;
        showPouch();
        Vec3 at = footing.spill(this, position().add(0.0, getBbHeight() * 0.5, 0.0));
        ItemEntity dropped = spawnAtLocation(level, new ItemStack(Items.ENDER_PEARL, n), at.subtract(position()));
        if (dropped != null) dropped.setDeltaMovement((random.nextDouble() - 0.5) * 0.2, 0.25, (random.nextDouble() - 0.5) * 0.2);
        Vec3 m = footing.spill(this, mouth());
        level.sendParticles(PEARL, true, true, m.x, m.y, m.z, 16, 0.3, 0.25, 0.3, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, m.x, m.y, m.z, 20, 0.3, 0.25, 0.3, 0.1);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.PEARLGULP_SPILL.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** It never despawns while it owes a pearl. */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || owed() > 0;
    }

    // ---- small helpers and test hooks --------------------------------------------------------------------------------

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** Pearls it holds now, each one blink. */
    public int held() {
        return held;
    }

    /** Pearls it gives back when it dies: those it holds and those it burnt on blinks. */
    public int owed() {
        return held + spent;
    }

    /** Sets the pearls it holds (0 to {@link #MAX_HELD}). Public as a test hook. */
    public void setHeld(int pearls) {
        held = Math.clamp(pearls, 0, MAX_HELD);
        showPouch();
    }

    /** Blinks onto {@code to} now, if it is safe ground and a pearl is held, burning it. Public as a test hook. */
    public boolean blinkTo(Vec3 to) {
        if (!(level() instanceof ServerLevel level) || held <= 0) return false;
        Vec3 at = SafeTeleport.InLevel.spot(level, this, to.x, to.y, to.z);
        if (at == null) return false;
        jump(level, at, ModSounds.PEARLGULP_BLINK.get());
        blinks++;
        return true;
    }

    /** Pearls it has eaten so far. Public as a test hook. */
    public int eaten() {
        return eaten;
    }

    /** Closing blinks so far. Public as a test hook. */
    public int blinks() {
        return blinks;
    }

    /** Escape blinks so far. Public as a test hook. */
    public int escapes() {
        return escapes;
    }

    /** Bites that landed so far. Public as a test hook. */
    public int bites() {
        return bites;
    }

    /** How far from its mouth the last pearl it ate was when it was snapped up (NaN before the first). Test hook. */
    public double lastGulpDistance() {
        return lastGulpDistance;
    }

    /** Ticks until its next blink may start (0: ready). Public as a test hook. */
    public int blinkCooldown() {
        if (blinkAt == NEVER) return 0;
        return (int) Math.max(0L, blinkAt + BLINK_EVERY - level().getGameTime());
    }

    // ---- save and load -----------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Pearls", held);
        output.putInt("Spent", spent);
        output.putInt("BlinkCooldown", blinkCooldown());
    }

    /** The pearls it holds and owes and the blink cooldown survive a reload; a flare or gape under way does not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        held = Math.clamp(input.getIntOr("Pearls", 0), 0, MAX_HELD);
        spent = Math.max(0, input.getIntOr("Spent", 0));
        int cooldown = Math.clamp(input.getIntOr("BlinkCooldown", 0), 0, BLINK_EVERY);
        blinkAt = cooldown > 0 ? level().getGameTime() - BLINK_EVERY + cooldown : NEVER;
        showPouch();
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.PEARLGULP_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PEARLGULP_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PEARLGULP_DEATH.get();
    }
}
