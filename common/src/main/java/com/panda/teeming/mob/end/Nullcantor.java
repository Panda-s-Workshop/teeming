package com.panda.teeming.mob.end;

import com.panda.teeming.effect.NulledEffect;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.Zone;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Nullcantor: a tall, hooded chanter of the End with a hollow face and a wide choir collar, which never stops singing.
 *
 * <p>Its song is a null ring of radius 8 round it, always on: a ring of violet motes on the ground and a low hum every
 * 2 seconds that carries well past the ring. Every player inside is {@link NulledEffect Nulled}: no enchantment on their
 * gear has any effect (curses still hold), and their enchantments work again within a tick or two of stepping out. It
 * takes nothing away and leaves every item working; it is a place to fight round, not a status to carry off.
 *
 * <p>Up close it raises both arms and its chant swells for 10 ticks (tell: glyphs swirl into its hood), then brings its
 * arms down on its target for its attack (8.5), if the target is still in reach; it swings at most every 30 ticks.
 *
 * <p>Counter: fight it from outside the ring (a bow, a trident), or come in with plain gear and step back from the
 * swing. A hit from an attacker who stands outside the ring knocks its collar off: it flinches, and the collar's shard
 * lies where it stood and holds the ring there for 160 ticks while the Nullcantor walks on with none. Lead it away and
 * your enchantments work on it again; then the collar grows back and the ring is round it once more. A broken part
 * that shifts the fight: the dead ground stays put and the chanter doesn't.
 */
public class Nullcantor extends TeemingMonster {
    /** IDLE, the raised arms (tell), the blow, the flinch as its collar is knocked off (counter landed). */
    public enum Step implements Phase { IDLE, TELL, STRIKE, CRACKED }

    /** The null ring: its radius across the ground. */
    public static final double RING_RADIUS = 8.0;
    /** The ring holds players whose feet are from 2 blocks below to 4 blocks above its centre. */
    public static final double RING_BELOW = 2.0, RING_ABOVE = 4.0;
    /** The hum: every 2 s, loud enough (24 blocks) to be heard well outside the ring. */
    public static final int HUM_EVERY = 40;
    public static final float HUM_VOLUME = 1.5F;
    /** The swing: a 10-tick tell with its arms raised, then a blow of its attack (8.5), at most every 30 ticks. */
    public static final int TELL_TICKS = 10;
    public static final int STRIKE_TICKS = 8;
    public static final int STRIKE_EVERY = 30;
    /** The counter: the flinch as the collar comes off, and how long the fallen shard holds the ring (8 s). */
    public static final int CRACK_TICKS = 12;
    public static final int SHARD_TICKS = 160;

    /** Violet, the ring's light: well off weak-point gold. */
    public static final int RING_VIOLET = 0x9B5CFF;
    private static final int RING_ARGB = 0xFF000000 | RING_VIOLET;
    private static final int RING_POINTS = 40;
    private static final int GLINT_ARGB = 0xFFF4F2FF;
    private static final Telegraph CHANT = Telegraph.particles(ParticleTypes.ENCHANT, 14, 0.9, 0.9).withSpeed(0.6);

    /** Flag bit: its collar is off (the model shows the broken stub). */
    public static final int FLAG_COLLAR_OFF = 0;

    /** The ring's room round its centre: a disc of radius 8, from 2 blocks under the centre to 4 above. */
    public static final Zone.Shape RING = new Zone.Shape() {
        @Override
        public AABB bounds(Vec3 c) {
            return new AABB(c.x - RING_RADIUS, c.y - RING_BELOW, c.z - RING_RADIUS, c.x + RING_RADIUS, c.y + RING_ABOVE, c.z + RING_RADIUS);
        }

        @Override
        public boolean holds(Vec3 c, AABB box) {
            double dx = (box.minX + box.maxX) * 0.5 - c.x, dz = (box.minZ + box.maxZ) * 0.5 - c.z;
            return dx * dx + dz * dz <= RING_RADIUS * RING_RADIUS && box.minY >= c.y - RING_BELOW && box.minY <= c.y + RING_ABOVE;
        }
    };

    private final Zone ring = new Zone(RING, 1).affects(this::canNull, this::nullify).drawn(this::drawRing);
    private int strikeCooldown;
    private @Nullable Vec3 shard;
    private long shardEnd;
    private int hums, strikes, cracks;

    public Nullcantor(EntityType<? extends Nullcantor> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("nullcantor", 0.22);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }

            /** It closes in only: every blow is the tell and the strike, started from its AI step. */
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

    // ---- the null ring ---------------------------------------------------------------------------------------------

    /**
     * The ring runs from {@code tick()}, so it is on even for a mob without AI, and stops the moment it dies: it sits on
     * the Nullcantor, or on its fallen collar for as long as that lasts.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (shard != null && now >= shardEnd) regrow(level);
        if (shard == null && core().flag(FLAG_COLLAR_OFF) && !core().in(Step.CRACKED)) core().setFlag(FLAG_COLLAR_OFF, false);
        if (ring.centres().isEmpty() && shard == null && isAlive()) ring.carried(this);
        ring.tick(level);
        if (isAlive() && tickCount % HUM_EVERY == 0) {
            Vec3 at = ringCentre();
            level.playSound(null, at.x, at.y + 0.5, at.z, ModSounds.NULLCANTOR_HUM.get(), getSoundSource(), HUM_VOLUME, 0.9F + random.nextFloat() * 0.1F);
            hums++;
        }
    }

    private boolean canNull(LivingEntity e) {
        return e instanceof Player p && p.isAlive() && !p.isSpectator();
    }

    private void nullify(LivingEntity e) {
        if (MechanicEvent.post(this, "null", e)) NulledEffect.hold(e);
    }

    /** Where the ring is centred now: on it, or on its fallen collar. */
    public Vec3 ringCentre() {
        return shard != null ? shard : position();
    }

    /** True when the ring holds {@code e} where it stands now (whether or not a listener lets it null them). */
    public boolean ringHolds(LivingEntity e) {
        return isAlive() && RING.holds(ringCentre(), e.getBoundingBox());
    }

    /**
     * The ring, every other tick: violet motes on the ground along its edge that rise a little (full-bright, so they
     * show in the End's dim light), a few portal motes drifting up from it, and over a fallen collar, star-white glints.
     */
    private void drawRing(ServerLevel level, Vec3 c) {
        if (tickCount % 2 != 0) return;
        double turn = tickCount * 0.01;
        int half = RING_POINTS / 2, offset = (tickCount / 2) % 2;
        for (int i = 0; i < half; i++) {
            double a = turn + (i * 2 + offset) * (Math.PI * 2 / RING_POINTS);
            double x = c.x + Math.cos(a) * RING_RADIUS, z = c.z + Math.sin(a) * RING_RADIUS, y = c.y + 0.1;
            level.sendParticles(new TrailParticleOption(new Vec3(x, y + 0.6, z), RING_ARGB, 14), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (tickCount % 6 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * RING_RADIUS, c.y + 0.2, c.z + Math.sin(a) * RING_RADIUS,
                    2, 0.1, 0.05, 0.1, 0.01);
        }
        if (shard != null) {
            level.sendParticles(new TrailParticleOption(c.add(0.0, 0.9, 0.0), GLINT_ARGB, 10), c.x, c.y + 0.15, c.z, 2, 0.25, 0.0, 0.25, 0.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.3, c.z, 2, 0.2, 0.1, 0.2, 0.02);
        }
    }

    // ---- the swing -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (strikeCooldown > 0) strikeCooldown--;
        LivingEntity t = core().target();
        if (core().in(Step.TELL)) {
            holdStill();
            if (valid(t)) getLookControl().setLookAt(t, 30.0F, 30.0F);
            CHANT.trail(this, 2);
            return;
        }
        if (core().in(Step.STRIKE) || core().in(Step.CRACKED)) {
            holdStill();
            return;
        }
        LivingEntity target = getTarget();
        if (strikeCooldown == 0 && valid(target) && isWithinMeleeAttackRange(target) && hasLineOfSight(target)) windUp(target);
    }

    /** Starts the tell at {@code target}; true if it did. Public as a test hook. */
    public boolean windUp(LivingEntity target) {
        if (!core().idle() || strikeCooldown > 0 || level().isClientSide()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        strikeCooldown = STRIKE_EVERY;
        holdStill();
        CHANT.withSound(ModSounds.NULLCANTOR_TELL.get(), 1.2F, 1.0F).play(this);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            LivingEntity t = core().target();
            if (startPhase(Step.STRIKE, STRIKE_TICKS, t)) strike(level, t);
            else core().end();
        } else {
            core().end();
        }
    }

    /** The end of the tell: both arms come down, and the blow lands if the target is still within reach. */
    private void strike(ServerLevel level, @Nullable LivingEntity target) {
        playSound(ModSounds.NULLCANTOR_STRIKE.get(), 1.0F, 1.0F);
        if (valid(target) && isWithinMeleeAttackRange(target) && hasLineOfSight(target)) {
            AstralAugur.GroundMarks.inPlace(target, () -> doHurtTarget(level, target));
            strikes++;
        }
    }

    private boolean valid(@Nullable LivingEntity e) {
        return e != null && e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void holdStill() {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    // ---- the counter: the collar ------------------------------------------------------------------------------------

    /** A hit from an attacker standing outside the ring knocks its collar off (while it still wears one). */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && shard == null && source.getEntity() instanceof LivingEntity attacker && attacker != this && !ringHolds(attacker)) {
            startPhase(Step.CRACKED, CRACK_TICKS, attacker);
        }
        return hurt;
    }

    /**
     * Entering the flinch shows the collar off; a flinch with someone to blame (a hit from outside the ring) also drops
     * the shard and the ring with it. A flinch with no one behind it only shows the pose, and the collar is back when
     * it ends.
     */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        if (phase == Step.CRACKED) {
            core().setFlag(FLAG_COLLAR_OFF, true);
            if (target != null && level() instanceof ServerLevel level) breakCollar(level);
        }
        return true;
    }

    private void breakCollar(ServerLevel level) {
        if (shard != null) return;
        shard = position();
        shardEnd = level.getGameTime() + SHARD_TICKS;
        ring.open(level, shard, SHARD_TICKS);                 // replaces the ring it carried
        core().setFlag(FLAG_COLLAR_OFF, true);
        cracks++;
        double y = getY() + getBbHeight() * 0.72;
        level.sendParticles(ParticleTypes.END_ROD, getX(), y, getZ(), 14, 0.5, 0.15, 0.5, 0.06);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), y, getZ(), 24, 0.6, 0.2, 0.6, 0.05);
        level.playSound(null, getX(), y, getZ(), ModSounds.NULLCANTOR_CRACK.get(), getSoundSource(), 1.3F, 1.0F);
    }

    private void regrow(ServerLevel level) {
        shard = null;
        ring.clear();
        core().setFlag(FLAG_COLLAR_OFF, false);
        double y = getY() + getBbHeight() * 0.72;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), y, getZ(), 20, 0.5, 0.1, 0.5, 0.03);
        level.playSound(null, getX(), y, getZ(), ModSounds.NULLCANTOR_REGROW.get(), getSoundSource(), 1.0F, 1.0F);
    }

    /** True while its collar is off and the ring lies on the shard. */
    public boolean collarOff() {
        return shard != null;
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        ring.clear();
        super.onRemoval(reason);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Lets it swing again at once. Public as a test hook. */
    public void resetSwing() {
        strikeCooldown = 0;
    }

    /** Ticks until it may swing again. Public as a test hook. */
    public int swingCooldown() {
        return strikeCooldown;
    }

    /** Hums played, blows landed, collars knocked off. Public as test hooks. */
    public int hums() {
        return hums;
    }

    public int strikes() {
        return strikes;
    }

    public int cracks() {
        return cracks;
    }

    /** Where the fallen collar lies, or null. Public as a test hook. */
    public @Nullable Vec3 shard() {
        return shard;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.NULLCANTOR_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.NULLCANTOR_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.NULLCANTOR_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.1F;
    }
}
