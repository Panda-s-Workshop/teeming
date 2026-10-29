package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Zone;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Void Bloom: a rooted, rubbery flower of void ichor in the end barrens, a stem bowed over like a crook with one big
 * drooping cup hanging from it, dripping. It never moves and never strikes. Once it has noticed something (it has a
 * target), a stain spreads on the ground round it: an area drawn with dark violet dust and a darker rim, never blocks.
 * The stain starts at radius 0.5 and grows 0.5 every 40 ticks, up to 8. Asleep (no target), it holds its size.
 *
 * <p>Inside the stain, everything but Void Blooms takes 1 damage a second and is weakened (Weakness I, lasting a moment
 * after it steps out). The stain eats at it from below: no knockback, and armour, Protection and a raised shield don't
 * stop it ({@code teeming:void_stain}), so it costs every player the same.
 *
 * <p>Tell: for the 8 ticks before each growth step the next ring darkens and throbs where it will land, the cup swells
 * and its drips glow and lengthen, and the stain seethes. Then the edge jumps out half a block.
 *
 * <p>Counter: reach it early, while the stain is narrow, or shoot it from outside. Kill it and the stain recedes over
 * 40 ticks, harmless, and the land comes back. Nothing grows once it is dying.
 */
public class VoidBloom extends TeemingMonster {
    public enum Step implements Phase { IDLE, TELL }

    /** The stain at first, each growth step, and its widest. */
    public static final double START_RADIUS = 0.5;
    public static final double GROW_STEP = 0.5;
    public static final double MAX_RADIUS = 8.0;
    /** Ticks from one growth step to the next, the tell included. */
    public static final int GROW_EVERY = 40;
    /** The edge darkens and throbs this long before each growth step. */
    public static final int TELL_TICKS = 8;
    /** What the stain does to those inside: this much damage once a second, and Weakness while they stay. */
    public static final float STAIN_DAMAGE = 1.0F;
    /** The stain's damage type: through armour, Protection and shields. */
    public static final ResourceKey<DamageType> STAIN_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TeemingMod.id("void_stain"));
    public static final int DAMAGE_EVERY = 20;
    public static final int WEAKNESS_LEVEL = 0;                 // amplifier 0 = Weakness I
    /** Weakness is topped up to this long while inside, whenever it has this little left: it fades soon after leaving. */
    public static final int WEAKNESS_TICKS = 20;
    public static final int WEAKNESS_TOP_UP = 10;
    /** After death the stain shrinks to nothing over this long, harmless, and the body goes with it. */
    public static final int RECEDE_TICKS = 40;
    /** The stain lies on the ground: it reaches feet from a block below it to 1.5 above (a jump doesn't clear it). */
    public static final double STAIN_BELOW = 1.0;
    public static final double STAIN_ABOVE = 1.5;

    /** Glow stages: dark, and the mouth and drips lit through the tell. */
    public static final int GLOW_DARK = 0;
    public static final int GLOW_LIT = 1;

    /** The stain's colours: dark violet that reads on pale end stone, a darker rim, and the near-black of the next ring. */
    public static final int STAIN_COLOR = 0x3A1C5C;
    public static final int DEEP_COLOR = 0x26123E;
    public static final int RIM_COLOR = 0x1A0B2C;
    public static final int NEXT_COLOR = 0x10061C;
    /** End-stone cream: some of the stain's blots fade into it as they age, so the stain seethes and a receding one thins out. */
    public static final int GROUND_COLOR = 0xD9D8A2;
    // few, large blots inside, so the bloom and the ground still show; a dense, thin rim and a thin next ring, with
    // ground between them, so the edge is what reads
    private static final DustParticleOptions STAIN = new DustParticleOptions(STAIN_COLOR, 2.8F);
    private static final DustColorTransitionOptions DEEP_FADING = new DustColorTransitionOptions(DEEP_COLOR, GROUND_COLOR, 3.2F);
    private static final DustParticleOptions DEEP = new DustParticleOptions(DEEP_COLOR, 2.0F);
    private static final DustParticleOptions RIM = new DustParticleOptions(RIM_COLOR, 1.7F);
    private static final DustParticleOptions EDGE = new DustParticleOptions(RIM_COLOR, 1.0F);
    private static final DustParticleOptions NEXT_BIG = new DustParticleOptions(NEXT_COLOR, 1.9F);
    private static final DustParticleOptions NEXT_SMALL = new DustParticleOptions(NEXT_COLOR, 1.1F);
    /** Rim particles stand about this far apart along the edge: close enough to draw an unbroken line. */
    private static final double RIM_SPACING = 0.5;
    /** Blots keep this far inside the rim, and off a clear patch round the stem (at most a block, half the radius). */
    private static final double BLOT_INSET = 0.3;
    private static final double CLEAR_ROUND_STEM = 1.0;
    /** The cup's lip, from its feet at scale 1: forward of the stem and half a block up. */
    public static final double CUP_FORWARD = 0.48;
    public static final double CUP_HEIGHT = 0.5;

    private double radius = START_RADIUS;
    private int growIn = GROW_EVERY;
    private int grows;
    /** The stain's radius when it died: the recede shrinks it from here. Negative while alive. */
    private double recedeFrom = -1.0;

    private final Zone stain = new Zone(new StainShape(), 1).affects(this::stainable, this::touch).drawn(this::draw);
    private Zone.@Nullable Area area;

    public VoidBloom(EntityType<? extends VoidBloom> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("void_bloom", 0.0)
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

    /** Every phase it enters sets its glow: the mouth and drips lit through the tell, dark otherwise. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.TELL ? GLOW_LIT : GLOW_DARK);
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        core().end();
        core().setStage(GLOW_DARK);
    }

    // ---- rooted ----------------------------------------------------------------------------------------------------

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

    // ---- the stain -------------------------------------------------------------------------------------------------

    /** Awake: it has noticed something alive (never a creative or spectating player), and its stain grows. */
    public boolean awake() {
        LivingEntity t = getTarget();
        return t != null && t.isAlive() && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        if (isDeadOrDying()) recede();
        else growTick(level);
        // the area sits where the bloom stands; it only moves if the ground under it went
        if (area == null || area.centre().distanceToSqr(position()) > 1.0E-6) {
            stain.clear();
            area = stain.open(level, position(), Integer.MAX_VALUE);
        }
        stain.tick(level);
        if (tickCount % 10 == 0 && !isDeadOrDying()) drip(level, ParticleTypes.FALLING_OBSIDIAN_TEAR);
    }

    /**
     * While awake and not yet at its widest: counts down to the next growth step, starts the tell 8 ticks before it
     * (or again, whole, if a tell was cut short), and grows when the count runs out. A cancelled tell skips the step.
     */
    private void growTick(ServerLevel level) {
        if (!awake() || radius >= MAX_RADIUS) return;
        growIn--;
        if (growIn <= 0) {
            growIn = GROW_EVERY;
            spread(level);
            return;
        }
        if (growIn <= TELL_TICKS && core().idle()) {
            growIn = TELL_TICKS;
            if (startPhase(Step.TELL, TELL_TICKS, getTarget())) {
                level.playSound(null, getX(), getY() + CUP_HEIGHT, getZ(), ModSounds.VOID_BLOOM_TELL.get(), getSoundSource(), 1.0F, 1.0F);
            } else {
                growIn = GROW_EVERY;
            }
        }
    }

    /** One growth step: the edge jumps out by 0.5, up to 8. Returns false if an event cancelled it. */
    private boolean spread(ServerLevel level) {
        if (!MechanicEvent.post(this, "grow", getTarget())) return false;
        radius = Math.min(MAX_RADIUS, radius + GROW_STEP);
        grows++;
        level.playSound(null, getX(), getY(), getZ(), ModSounds.VOID_BLOOM_GROW.get(), getSoundSource(), 1.0F, 0.9F + 0.2F * (float) (radius / MAX_RADIUS));
        ring(level, radius, RIM, 0.5, true);
        level.sendParticles(ParticleTypes.SQUID_INK, true, true, getX(), getY() + 0.1, getZ(), 6, radius * 0.4, 0.05, radius * 0.4, 0.02);
        return true;
    }

    /** Who the stain touches: anyone alive but itself and other blooms, never creative or spectating players; nobody once it is dying. */
    private boolean stainable(LivingEntity e) {
        return isAlive() && e != this && !(e instanceof VoidBloom) && e.isAlive() && (e instanceof Mob || e instanceof Player)
                && !e.isSpectator() && !(e instanceof Player p && p.isCreative());
    }

    /** Each tick, for each one inside: the damage once a second, and Weakness topped up. */
    private void touch(LivingEntity victim) {
        if (!(level() instanceof ServerLevel level)) return;
        boolean pulse = level.getGameTime() % DAMAGE_EVERY == 0;
        MobEffectInstance weak = victim.getEffect(MobEffects.WEAKNESS);
        boolean topUp = weak == null || weak.getAmplifier() <= WEAKNESS_LEVEL && weak.getDuration() <= WEAKNESS_TOP_UP;
        if (!pulse && !topUp) return;
        if (!MechanicEvent.post(this, "stain", victim)) return;
        if (topUp) victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, WEAKNESS_LEVEL, false, true, true), this);
        if (pulse) {
            AstralAugur.GroundMarks.hurtInPlace(level, victim, stainSource(level), STAIN_DAMAGE);
            Vec3 at = victim.position();
            level.sendParticles(DEEP, at.x, at.y + 0.2, at.z, 4, victim.getBbWidth() * 0.4, 0.15, victim.getBbWidth() * 0.4, 0.0);
        }
    }

    /** The stain's hit, from the bloom. */
    private DamageSource stainSource(ServerLevel level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(STAIN_TYPE), this);
    }

    /** The stain's room: a flat disc on the ground round the bloom, as wide as the stain is now. */
    private final class StainShape implements Zone.Shape {
        @Override
        public AABB bounds(Vec3 c) {
            double r = stainRadius();
            return new AABB(c.x - r, c.y - STAIN_BELOW, c.z - r, c.x + r, c.y + STAIN_ABOVE, c.z + r);
        }

        @Override
        public boolean holds(Vec3 c, AABB box) {
            double r = stainRadius();
            double dx = (box.minX + box.maxX) * 0.5 - c.x, dz = (box.minZ + box.maxZ) * 0.5 - c.z;
            return dx * dx + dz * dz <= r * r && box.minY >= c.y - STAIN_BELOW && box.minY <= c.y + STAIN_ABOVE;
        }
    }

    /**
     * The stain on the ground: a few large dark violet blots spread over it, kept off the stem and inside the edge, a
     * dense near-black rim at its edge (the line to stay outside), and through the tell the next ring, near-black,
     * throbbing where it will land. Its blots are short-lived (a quarter fade into end-stone cream as they go), so a
     * receding stain thins out behind its drawing-back edge.
     */
    private void draw(ServerLevel level, Vec3 c) {
        double r = stainRadius();
        if (r <= 0.05) return;
        if (tickCount % 3 == 0) {
            int blots = 1 + (int) (r * r * 0.25);
            double outer = Math.max(0.2, r - BLOT_INSET), inner = Math.min(Math.min(CLEAR_ROUND_STEM, r * 0.5), outer * 0.5);
            for (int i = 0; i < blots; i++) {
                double d = Math.sqrt(inner * inner + (outer * outer - inner * inner) * random.nextDouble()), a = random.nextDouble() * Math.PI * 2;
                level.sendParticles(random.nextInt(4) == 0 ? DEEP_FADING : STAIN, false, false, c.x + Math.cos(a) * d, c.y + 0.06,
                        c.z + Math.sin(a) * d, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (receding()) {
            // the edge drawing back: a short-lived dark line that shows where the land comes back
            if (tickCount % 2 == 0) ring(level, r, EDGE, 0.1, true);
        } else if (tickCount % 3 == 0) {
            ring(level, r, RIM, 0.08, false);
        }
        if (core().in(Step.TELL) && !isDeadOrDying()) {
            int t = core().elapsed();
            if (t % 2 == 0) ring(level, Math.min(MAX_RADIUS, radius + GROW_STEP), (t / 2) % 2 == 0 ? NEXT_BIG : NEXT_SMALL, 0.1, true);
            if (t % 3 == 0) drip(level, ParticleTypes.FALLING_OBSIDIAN_TEAR);
        }
    }

    /** A ring of {@code dust} round the bloom at radius {@code r}, starting at a turning angle, seen from afar if {@code far}. */
    private void ring(ServerLevel level, double r, ParticleOptions dust, double lift, boolean far) {
        int points = Math.max(6, (int) Math.ceil(Math.PI * 2 * r / RIM_SPACING));
        double turn = tickCount * 0.03;
        for (int i = 0; i < points; i++) {
            double a = turn + i * (Math.PI * 2 / points);
            level.sendParticles(dust, far, far, getX() + Math.cos(a) * r, getY() + lift, getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A drop falling from the cup's lip. */
    private void drip(ServerLevel level, ParticleOptions drop) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double s = getScale();
        double x = getX() - Mth.sin(yaw) * CUP_FORWARD * s + (random.nextDouble() - 0.5) * 0.3 * s;
        double z = getZ() + Mth.cos(yaw) * CUP_FORWARD * s + (random.nextDouble() - 0.5) * 0.3 * s;
        level.sendParticles(drop, x, getY() + CUP_HEIGHT * s, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    // ---- death: the stain recedes ----------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (recedeFrom >= 0.0 || !(level() instanceof ServerLevel level)) return;
        core().setStage(GLOW_DARK);
        // a cancelled recede takes the stain away at once
        recedeFrom = MechanicEvent.post(this, "recede", null) ? radius : 0.0;
        level.playSound(null, getX(), getY(), getZ(), ModSounds.VOID_BLOOM_RECEDE.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.SQUID_INK, true, true, getX(), getY() + CUP_HEIGHT, getZ(), 12, 0.3, 0.2, 0.3, 0.03);
    }

    /** Shrinks the stain from where it stood at death to nothing as the body sinks: 40 ticks. */
    private void recede() {
        if (recedeFrom < 0.0) recedeFrom = radius;          // died some way that skipped die()
        radius = recedeFrom * Math.max(0.0, 1.0 - (double) deathTime / RECEDE_TICKS);
    }

    /** The body stays as long as its stain takes to recede, then goes with the usual puff. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= RECEDE_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        stain.clear();
        area = null;
        super.onRemoval(reason);
    }

    // ---- saving ----------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("StainRadius", radius);
        output.putInt("StainGrowIn", growIn);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        radius = Mth.clamp(input.getDoubleOr("StainRadius", START_RADIUS), START_RADIUS, MAX_RADIUS);
        growIn = Mth.clamp(input.getIntOr("StainGrowIn", GROW_EVERY), 1, GROW_EVERY);
    }

    // ---- state and test hooks --------------------------------------------------------------------------------------

    /** The stain's radius now: growing while alive, shrinking while it dies. */
    public double stainRadius() {
        return radius;
    }

    /** Sets the stain's radius (kept between 0.5 and 8). Public as a test hook. */
    public void setStainRadius(double r) {
        radius = Mth.clamp(r, START_RADIUS, MAX_RADIUS);
    }

    /** Ticks of being awake until the next growth step. */
    public int growIn() {
        return growIn;
    }

    /** How many growth steps it has taken since it was spawned. */
    public int grows() {
        return grows;
    }

    /** True once it has died and its stain is receding. */
    public boolean receding() {
        return recedeFrom >= 0.0;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.VOID_BLOOM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.VOID_BLOOM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.VOID_BLOOM_DEATH.get();
    }
}
