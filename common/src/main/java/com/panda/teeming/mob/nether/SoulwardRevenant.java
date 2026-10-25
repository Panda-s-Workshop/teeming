package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.Gauge;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Soulward Revenant: a soul gauge of 0–25 fills with every point of health it loses. From 20 (80 %) its seams burn
 * white and damage fills the gauge twice as fast. Full, it kneels for 1 s (tell), souls streaming in along the ring of
 * its reach, then releases a nova: 8 damage and knockback 1.2 to everything within 5 blocks, and the gauge empties.
 *
 * <p>Counter: a nova that catches no player is wasted. Its breastplate cracks and for 3 s it has no armour and nothing
 * fills the gauge; it keeps fighting, so the window is for hitting it hard, not for a free walk-up.
 */
public class SoulwardRevenant extends TeemingMonster {
    /** IDLE, the kneel (tell), the release, the cracked breastplate (after a wasted nova). */
    public enum Step implements Phase { IDLE, TELL, NOVA, SPENT }

    /** The gauge is full at 25 points of damage taken. */
    public static final float GAUGE_MAX = 25.0F;
    /** From 80 % of the gauge it glows bright and fills twice as fast. */
    public static final float BRIGHT_SHARE = 0.8F;
    public static final float BRIGHT_FILL = 2.0F;
    /** Above this the glow shows its second stage. */
    public static final float MID_AT = 10.0F;
    /** 1 s kneeling before the nova. */
    public static final int TELL_TICKS = 20;
    /** Half a second standing out of the release. */
    public static final int NOVA_TICKS = 10;
    /** The nova reaches 5 blocks from its feet, exactly where the tell draws its ring. */
    public static final double NOVA_RADIUS = 5.0;
    public static final float NOVA_DAMAGE = 8.0F;
    /**
     * Knockback 1.2 on the attack-knockback scale (the one weapons and the attack-knockback attribute use): vanilla turns
     * a strength of k into k / 2 blocks a tick, so a body is thrown out at 0.6 a tick and lifted 0.4, and comes to rest
     * about 3 blocks further out: one struck 2 blocks off lands just past the nova's own reach. Knockback resistance takes its share off both.
     */
    public static final double KNOCKBACK = 1.2;
    public static final double PUSH_SPEED = KNOCKBACK * 0.5;
    public static final double PUSH_LIFT = 0.4;
    /** A wasted nova cracks its breastplate for 3 s: armour 0, and the gauge sealed. */
    public static final int SPENT_TICKS = 60;
    public static final int SPENT_ARMOR = 0;

    private static final int RING_POINTS = 40;
    private static final int XP = 10;
    private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(0xE0FDFF, 1.6F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD36B, 1.0F);
    private static final Telegraph BRIGHT = Telegraph.particles(ParticleTypes.SOUL, 12, 0.5, 0.6).withSpeed(0.04);
    private static final Telegraph CRACK = Telegraph.particles(ParticleTypes.SOUL, 16, 0.4, 0.6).withSpeed(0.06);

    /**
     * The soul gauge: health lost fills it, twice over once bright; it fills and glows only while idle (full during the
     * kneel, sealed while the breastplate is cracked). Turning bright warns; full, it kneels; the nova empties it.
     */
    private final Gauge gauge = new Gauge(this, "Gauge", GAUGE_MAX)
            .stages(SoulwardRevenant::stageFor)
            .glowsWhen(() -> core().idle())
            .sealedWhen(() -> !core().idle())
            .rate(v -> v >= GAUGE_MAX * BRIGHT_SHARE ? BRIGHT_FILL : 1.0F)
            .onChange(before -> {
                if (Gauge.rose(before, gauge(), GAUGE_MAX * BRIGHT_SHARE) && core().idle()) {
                    BRIGHT.play(this);                           // the warning: from here on it fills twice as fast
                    playSound(ModSounds.SOULWARD_REVENANT_GLOW.get(), 1.0F, 1.0F);
                }
            })
            .releaseAt(GAUGE_MAX, () -> {
                if (core().idle()) kneel();
            });
    private boolean wasted;

    public SoulwardRevenant(EntityType<? extends SoulwardRevenant> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("soulward_revenant", 0.22);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return fights() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return fights() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return fights() && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** It walks and swings when idle and with its breastplate cracked; it stands only to kneel and release. */
    private boolean fights() {
        return core().idle() || core().in(Step.SPENT);
    }

    @Override
    public int visualStages() {
        return 4;
    }

    /** Its weak point, the breastplate, glows weak-point gold while it is cracked; the seams are dark then. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.SPENT);
    }

    /** Every phase sets its glow as it starts: the kneel burns at full, the release and the cracked plate go dark. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.TELL ? 3 : phase == Step.IDLE ? gauge.stage() : 0);
        return true;
    }

    // ---- the gauge ---------------------------------------------------------------------------------------------------

    public float gauge() {
        return gauge.value();
    }

    /** True from 80 % of the gauge: its seams burn white and damage fills it twice as fast. */
    public boolean bright() {
        return gauge() >= GAUGE_MAX * BRIGHT_SHARE;
    }

    /** The glow stage for a gauge: dark when empty, then soul-blue, cyan, and white once bright. */
    public static int stageFor(float gauge) {
        if (gauge <= 0.0F) return 0;
        if (gauge < MID_AT) return 1;
        return gauge < GAUGE_MAX * BRIGHT_SHARE ? 2 : 3;
    }

    /** Sets the gauge (0..25) and its glow; full, while idle, it kneels for the nova. Public as a test hook. */
    public void setGauge(float value) {
        gauge.set(value);
    }

    /**
     * Health it lost fills the gauge, twice over while it glows bright. It fills only while idle: full during the kneel,
     * sealed while the breastplate is cracked. Public as a test hook.
     */
    public void fill(float lost) {
        if (lost <= 0.0F || gauge.sealed() || !MechanicEvent.post(this, "fill", null)) return;
        gauge.fill(lost);
    }

    /** Kneels to gather the nova, if it is free to; true if it did. Public as a test hook. */
    public boolean kneel() {
        if (!core().idle() || !startPhase(Step.TELL, TELL_TICKS, getTarget())) return false;
        playSound(ModSounds.SOULWARD_REVENANT_TELL.get(), 1.5F, 1.0F);
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float before = getHealth();
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && core().in(Step.SPENT)) {
            Vec3 c = chest();                                    // a hit on the cracked plate: gold sparks, souls leak
            level.sendParticles(GOLD, c.x, c.y, c.z, 8, 0.25, 0.25, 0.25, 0.0);
            level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.02);
        }
        if (hurt && isAlive()) fill(before - getHealth());
        return hurt;
    }

    /** Cracked, it has no armour. */
    @Override
    public int getArmorValue() {
        return core().in(Step.SPENT) ? SPENT_ARMOR : super.getArmorValue();
    }

    // ---- the kneel and the nova --------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().in(Step.TELL) || core().in(Step.NOVA)) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));     // kneeling, it holds its ground
        }
    }

    /** Runs on the AI's ticks and a still mob's alike, so the kneel, the glow and the crack always show. */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (core().in(Step.TELL)) {
            int t = core().elapsed();
            if (t % 2 == 0) inward(level, Math.min(1.0, (double) t / TELL_TICKS));   // soul fire streams in along the ground
            if (t % 5 == 0) ring(level, ParticleTypes.SOUL_FIRE_FLAME, 0.0);  // how far to run
        } else if (core().in(Step.NOVA)) {
            if (core().elapsed() % 3 == 0) {                                   // the last of the souls rise off it
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 4, 0.5, 0.6, 0.5, 0.02);
            }
        } else if (core().in(Step.SPENT)) {
            if (core().elapsed() % 4 == 0) {
                Vec3 c = chest();                                              // the crack leaks souls and gold motes
                level.sendParticles(GOLD, true, true, c.x, c.y, c.z, 1, 0.2, 0.2, 0.2, 0.0);
                level.sendParticles(ParticleTypes.SOUL, true, true, c.x, c.y, c.z, 1, 0.15, 0.15, 0.15, 0.01);
            }
        } else if (core().idle() && bright() && tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.55, getZ(), 2, 0.3, 0.5, 0.3, 0.01);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) return;
        if (ended == Step.TELL) {
            if (startPhase(Step.NOVA, NOVA_TICKS, core().target())) nova(level);
            else settle();                                   // a vetoed nova: it stands back up still full
        } else if (ended == Step.NOVA && wasted) {
            wasted = false;
            if (startPhase(Step.SPENT, SPENT_TICKS, core().target())) {
                CRACK.play(this);
                Vec3 c = chest();
                level.sendParticles(GOLD, true, true, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.0);
                playSound(ModSounds.SOULWARD_REVENANT_CRACK.get(), 1.5F, 1.0F);
            } else {
                settle();
            }
        } else {
            wasted = false;
            settle();
        }
    }

    /** Back to idle, glowing as its gauge says. */
    private void settle() {
        core().end();
        gauge.show();
    }

    /**
     * The release: everything within 5 blocks takes 8 and is thrown out (a shield stops the damage, not the throw), and
     * the gauge empties. A nova that reaches no player is wasted and cracks the breastplate when the release ends.
     * Returns how many it hurt. Public as a test hook.
     */
    public int nova(ServerLevel level) {
        int hurt = 0;
        boolean caught = false;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(NOVA_RADIUS), this::canHit)) {
            if (victim.distanceTo(this) > NOVA_RADIUS || !MechanicEvent.post(this, "nova_hit", victim)) continue;
            if (victim instanceof Player) caught = true;
            if (victim.hurtServer(level, damageSources().mobAttack(this), NOVA_DAMAGE)) hurt++;
            throwOut(victim);
        }
        wasted = !caught;
        gauge.reset();
        core().setStage(0);
        burst(level);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.SOULWARD_REVENANT_NOVA.get(), getSoundSource(), 2.0F, 1.0F);
        return hurt;
    }

    private boolean canHit(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof SoulwardRevenant) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Sets the victim flying straight out from its feet, whatever the blow's own knockback did. */
    private void throwOut(LivingEntity victim) {
        double give = 1.0 - Math.clamp(victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        if (give <= 0.0) return;
        Vec3 away = victim.position().subtract(position()).multiply(1.0, 0.0, 1.0);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, yBodyRot) : away.normalize();
        Push.set(victim, new Vec3(away.x * PUSH_SPEED * give, PUSH_LIFT * give, away.z * PUSH_SPEED * give));
    }

    /** The release: a white flash at its chest and a ring of soul fire rushing out along the ground to the reach. */
    private void burst(ServerLevel level) {
        Vec3 c = chest();
        level.sendParticles(WHITE_DUST, true, true, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.0);
        level.sendParticles(ParticleTypes.SOUL, true, true, c.x, c.y, c.z, 12, 0.5, 0.5, 0.5, 0.1);
        for (double r = 0.5; r <= NOVA_RADIUS - 0.99; r += 0.875) {
            for (int i = 0; i < RING_POINTS; i++) {
                double a = i * (Math.PI * 2 / RING_POINTS);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r,
                        0, Math.cos(a), 0.05, Math.sin(a), 0.04);   // about a block out, never far past the reach
            }
        }
        ring(level, ParticleTypes.SOUL, 0.03);
    }

    /**
     * Soul fire on the ring of its reach, drifting in to its feet: these particles keep 96 % of their speed a tick, so a
     * speed of v carries them about 25 v blocks, and 0.16 brings them from the ring to it. Late in the kneel a few souls
     * join them ({@code fill} 0..1 is how far through the kneel it is).
     */
    private void inward(ServerLevel level, double fill) {
        for (int i = 0; i < 4; i++) {
            double a = getRandom().nextDouble() * Math.PI * 2, r = NOVA_RADIUS - 0.3 - getRandom().nextDouble() * 0.6;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r,
                    0, -Math.cos(a), 0.06, -Math.sin(a), 0.16);
        }
        if (fill > 0.5 && getRandom().nextBoolean()) {
            double a = getRandom().nextDouble() * Math.PI * 2;
            level.sendParticles(ParticleTypes.SOUL, true, true, getX() + Math.cos(a) * 1.5, getY() + 0.4, getZ() + Math.sin(a) * 1.5,
                    0, -Math.cos(a), 0.3, -Math.sin(a), 0.05);
        }
    }

    /** Particles round the ground at the nova's reach, rising at {@code lift}. */
    private void ring(ServerLevel level, ParticleOptions particle, double lift) {
        for (int i = 0; i < RING_POINTS; i++) {
            double a = i * (Math.PI * 2 / RING_POINTS);
            level.sendParticles(particle, true, true, getX() + Math.cos(a) * NOVA_RADIUS, getY() + 0.15, getZ() + Math.sin(a) * NOVA_RADIUS,
                    1, 0.0, 0.05, 0.0, lift);
        }
    }

    /** The middle of its breastplate. */
    private Vec3 chest() {
        return position().add(Vec3.directionFromRotation(0.0F, yBodyRot).scale(0.25)).add(0.0, getBbHeight() * 0.62, 0.0);
    }

    // ---- saving ------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        gauge.save(output);
    }

    /** The gauge survives a reload with its glow (a reload mid-kneel ends the kneel; the next hit starts it again). */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        gauge.load(input);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SOULWARD_REVENANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SOULWARD_REVENANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SOULWARD_REVENANT_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.75F + random.nextFloat() * 0.1F;
    }
}
