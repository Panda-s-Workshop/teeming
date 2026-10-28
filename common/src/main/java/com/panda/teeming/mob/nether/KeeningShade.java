package com.panda.teeming.mob.nether;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.effect.EffectNumbers;
import com.panda.teeming.effect.SilencedEffect;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModEffects;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Keening Shade: a thin, hollow-eyed spectre of the soul sand valleys that drifts a hand's breadth above the sand. It
 * pokes weakly in melee (its attack damage, 3); its voice is the threat. Every 200 ticks, with a target within 10
 * blocks in its sight, it draws a long breath for 16 ticks (tell): it holds still, its chest caves in, its head tips
 * back and soul wisps are pulled into its mouth with a rising breath. Then it wails: a ring of sound runs out from it,
 * and every player within 10 blocks who has a line of sight to it at that moment is Silenced for 80 ticks (no item use:
 * no eating, no potions, no shield, no bow, nothing thrown); a Clear Voice Charm holder for 20 only. Creative and
 * spectating players are never touched.
 *
 * <p>Counter: break its line of sight while it breathes in: step behind a block or a tree (or out of its reach). A wail
 * that silences no one tears its voice: for 200 ticks it is voiceless. It sinks to the ground, its jaw hanging, soul
 * leaking from its throat, and it can't wail; once its voice comes back it waits a further 200 ticks before its next
 * breath. It still drifts and pokes while voiceless. It takes no extra damage meanwhile: the reward is twenty seconds in
 * which you may eat, drink, raise a shield and shoot.
 *
 * <p>The wail is not an entity and leaves nothing behind: the Silence is a status on each player it reached, and the
 * ring is particles. Nothing is placed or broken.
 */
public class KeeningShade extends TeemingMonster {
    /** IDLE, the breath (tell), the wail, voiceless after a wail that silenced no one (counter landed). */
    public enum Step implements Phase { IDLE, TELL, WAIL, VOICELESS }

    /** 16 ticks of drawing breath before the wail. */
    public static final int TELL_TICKS = 16;
    /** It draws breath for targets this close, and the wail reaches every player this close to it. */
    public static final double WAIL_RADIUS = 10.0;
    /** Ticks from one breath to the next, at the soonest. */
    public static final int WAIL_EVERY = 200;
    /** A wail silences for 4 s: the status's own length. */
    public static final int SILENCE_TICKS = EffectNumbers.SILENCED_TICKS;
    /** The wail itself: its ring runs out to the full radius over the first half, and rings once more as an echo. */
    public static final int WAIL_TICKS = 20;
    public static final int RING_TICKS = 10;
    /** A wail that silences no one leaves it voiceless this long... */
    public static final int VOICELESS_TICKS = 200;
    /** ...and its next breath waits this long after its voice comes back. */
    public static final int AFTER_VOICELESS = WAIL_EVERY;
    /** A fresh shade gathers itself this long before its first breath, so it never wails on the tick it appears. */
    public static final int FIRST_WAIL = 10;

    /** Soul cyan, the colour of the wisps and the ring of sound: well off weak-point gold. */
    public static final int SOUL_COLOR = 0x8FE6EE;
    // small motes: a dust mote lives longer the bigger it is, and the ring must not linger as a carpet
    private static final DustParticleOptions RING = new DustParticleOptions(SOUL_COLOR, 0.9F);
    private static final DustParticleOptions ECHO = new DustParticleOptions(0x5FB8C4, 0.6F);
    private static final Telegraph BREATH = Telegraph.particles(ParticleTypes.SOUL, 3, 0.5, 0.85).withSpeed(0.01);
    /** Wisps drawn in a tick while it breathes, from this far out. */
    private static final int WISPS = 1;
    private static final double WISP_FROM = 2.2;
    /**
     * A wisp's speed per block it has to go: a flame slows by 4 % a tick, so it coasts 25 times its first speed and
     * comes to rest just short of the mouth instead of flying past it.
     */
    private static final double WISP_PULL = 0.036;
    /** Points round the ring: one for every block of its edge and a bit, at the least this many. */
    private static final double RING_POINTS_PER_BLOCK = 1.3;
    private static final int RING_MIN_POINTS = 12;

    private int cooldown = FIRST_WAIL;
    private int wails;
    private int lastSilenced = -1;
    private final List<Player> lastReached = new ArrayList<>();

    public KeeningShade(EntityType<? extends KeeningShade> type, Level level) {
        super(type, level, Step.values());
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("keening_shade", 0.23);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // it still drifts after you and pokes while voiceless; it never moves while it breathes in or wails
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
            @Override
            public boolean canUse() {
                return free() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return free() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return free() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return free() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F) {
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

    /** True while it may move and poke: idle, or voiceless. */
    private boolean free() {
        return core() != null && (core().idle() || core().in(Step.VOICELESS));
    }

    // ---- the cycle -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().in(Step.VOICELESS)) return;                          // its wait starts again when its voice comes back
        if (cooldown > 0) cooldown--;
        if (core().in(Step.TELL) || core().in(Step.WAIL)) {
            holdStill();
            LivingEntity target = core().target();
            if (target != null && target.isAlive()) face(target);
        } else if (core().idle() && cooldown == 0 && canWail(getTarget())) {
            inhale(getTarget());
        }
    }

    /** True when {@code target} is worth a wail: alive, within 10 blocks and in its sight. */
    public boolean canWail(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))
                && distanceTo(target) <= WAIL_RADIUS && hasLineOfSight(target);
    }

    /** Starts the breath (the tell) at {@code target}; true if it started. Public as a test hook. */
    public boolean inhale(@Nullable LivingEntity target) {
        if (target == null || !core().idle() || cooldown > 0 || level().isClientSide()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        cooldown = WAIL_EVERY;
        holdStill();
        face(target);
        BREATH.withSound(ModSounds.KEENING_SHADE_INHALE.get(), 1.2F, 1.0F).play(this);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
            return;
        }
        if (ended == Step.TELL) {
            if (startPhase(Step.WAIL, WAIL_TICKS, core().target())) wail(level);
            else core().end();
        } else if (ended == Step.WAIL) {
            if (lastSilenced == 0) choke(level);
            else core().end();
        } else if (ended == Step.VOICELESS) {
            voiceBack(level);
        } else {
            core().end();
        }
    }

    /**
     * The wail: every player within 10 blocks who can see it, and isn't creative or spectating, is Silenced for 80
     * ticks ({@link Charms#CLEAR_VOICE_SILENCED_TICKS} on a Clear Voice Charm holder). Returns how many it silenced.
     * Run when the wail starts.
     */
    private int wail(ServerLevel level) {
        wails++;
        lastReached.clear();
        int silenced = 0;
        for (ServerPlayer p : level.players()) {
            if (!hears(p)) continue;
            lastReached.add(p);
            if (!MechanicEvent.post(this, "silence", p)) continue;
            ModEffects.give(p, ModEffects.SILENCED, Charms.silencedTicks(p, SILENCE_TICKS), this);
            if (SilencedEffect.silenced(p)) silenced++;
        }
        lastSilenced = silenced;
        Vec3 m = mouth();
        level.sendParticles(ParticleTypes.SOUL, true, true, m.x, m.y, m.z, 12, 0.25, 0.2, 0.25, 0.06);
        // a wail carries: twice the reach of an ordinary sound
        level.playSound(null, m.x, m.y, m.z, ModSounds.KEENING_SHADE_WAIL.get(), getSoundSource(), 2.0F, 1.0F);
        return silenced;
    }

    /** True when its wail reaches {@code p}: a survival or adventure player within 10 blocks with a line of sight to it. */
    public boolean hears(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && p.level() == level()
                && distanceTo(p) <= WAIL_RADIUS && hasLineOfSight(p);
    }

    /** Its voice tears (the counter): smoke and soul from its throat, a broken sound, and 200 ticks voiceless. */
    private void choke(ServerLevel level) {
        if (!startPhase(Step.VOICELESS, VOICELESS_TICKS, null)) {
            core().end();
            return;
        }
        Vec3 m = mouth();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, m.x, m.y, m.z, 8, 0.15, 0.1, 0.15, 0.02);
        level.sendParticles(ParticleTypes.SOUL, true, true, m.x, m.y, m.z, 6, 0.2, 0.15, 0.2, 0.03);
        level.playSound(null, m.x, m.y, m.z, ModSounds.KEENING_SHADE_CHOKE.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** Its voice comes back: soul at its mouth, and its next breath waits {@link #AFTER_VOICELESS} ticks. */
    private void voiceBack(ServerLevel level) {
        cooldown = AFTER_VOICELESS;
        Vec3 m = mouth();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.x, m.y, m.z, 6, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, m.x, m.y, m.z, ModSounds.KEENING_SHADE_VOICE.get(), getSoundSource(), 1.0F, 1.0F);
        core().end();
    }

    /** True while its voice is torn: it can't wail. */
    public boolean voiceless() {
        return core() != null && core().in(Step.VOICELESS);
    }

    // ---- what players see: the wisps it breathes in, the ring of its wail, the soul leaking from a torn voice ------

    /** Runs whether or not its AI does, so a held pose shows the same as the real thing. */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        int t = core().elapsed();
        if (core().in(Step.TELL)) {
            breathTick(level, t);
        } else if (core().in(Step.WAIL)) {
            ringTick(level, t);
        } else if (core().in(Step.VOICELESS) && t % 4 == 0) {
            Vec3 m = mouth();
            level.sendParticles(ParticleTypes.SMOKE, m.x, m.y, m.z, 1, 0.05, 0.03, 0.05, 0.01);
            if (t % 12 == 0) level.sendParticles(ParticleTypes.SOUL, m.x, m.y + 0.1, m.z, 1, 0.08, 0.05, 0.08, 0.01);
        }
    }

    /**
     * The breath: soul wisps from round it, sent flying into its mouth, and the breath rising in pitch half way. Each
     * is sent with its speed toward the mouth (a count of 0 makes the offset the particle's velocity).
     */
    private void breathTick(ServerLevel level, int t) {
        Vec3 m = mouth();
        for (int i = 0; i < WISPS; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian());
            if (d.lengthSqr() < 1.0E-4) continue;
            Vec3 from = m.add(d.normalize().scale(WISP_FROM));
            Vec3 v = m.subtract(from).scale(WISP_PULL);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
        }
        if (t % 5 == 0) level.sendParticles(ParticleTypes.SOUL, m.x, m.y, m.z, 1, 0.3, 0.2, 0.3, 0.0);
        if (t == TELL_TICKS / 2) level.playSound(null, m.x, m.y, m.z, ModSounds.KEENING_SHADE_INHALE.get(), getSoundSource(), 0.9F, 1.25F);
    }

    /**
     * The ring of sound: a circle of soul-cyan motes running out from it to the full 10 blocks over {@link #RING_TICKS},
     * then a fainter echo. Drawn at its feet and at its chest.
     */
    private void ringTick(ServerLevel level, int t) {
        boolean echo = (t / RING_TICKS) % 2 == 1;
        double r = WAIL_RADIUS * ((t % RING_TICKS) + 1) / RING_TICKS;
        int points = Math.max(RING_MIN_POINTS, (int) Math.ceil(r * Mth.TWO_PI * RING_POINTS_PER_BLOCK));
        double turn = random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < points; i++) {
            double a = turn + i * (Mth.TWO_PI / points);
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            level.sendParticles(echo ? ECHO : RING, true, true, x, getY() + 0.2, z, 1, 0.0, 0.05, 0.0, 0.0);
            if (!echo && i % 3 == 0) level.sendParticles(RING, true, true, x, getY() + 1.1, z, 1, 0.0, 0.05, 0.0, 0.0);
        }
    }

    /** Where its breath goes in and its wail comes out: the mouth, below its eyes and a little ahead of them. */
    public Vec3 mouth() {
        return getEyePosition().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(0.22 * getScale())).add(0, -0.3 * getScale(), 0);
    }

    // ---- moving and facing -----------------------------------------------------------------------------------------

    /** While it breathes in or wails its body faces exactly the way it looks. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && (core().in(Step.TELL) || core().in(Step.WAIL))) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    private void face(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void holdStill() {
        getNavigation().stop();
        getMoveControl().setWait();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    /** A drifting spectre: falls never hurt it. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Ticks until it may draw breath again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    /** Lets it draw breath again at once. Public as a test hook. */
    public void resetCooldown() {
        cooldown = 0;
    }

    /** Wails so far. Public as a test hook. */
    public int wails() {
        return wails;
    }

    /** How many players its last wail silenced, or -1 before its first. Public as a test hook. */
    public int lastSilenced() {
        return lastSilenced;
    }

    /** The players its last wail reached (in range and in sight), whether or not a listener let it silence them. Public as a test hook. */
    public List<Player> lastReached() {
        return List.copyOf(lastReached);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        lastReached.clear();
        super.onRemoval(reason);
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    /** It moans while idle; voiceless, it makes no sound at all. */
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.KEENING_SHADE_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.KEENING_SHADE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.KEENING_SHADE_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.2F;
    }
}
