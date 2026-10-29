package com.panda.teeming.mob.end;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.Grab;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Pullstar: a floating knot of dark gravity stone round a burning star-white heart, hovering a block over the End
 * barrens. While its heart burns it pulls every living thing within 8 blocks of its middle (and items lying about)
 * toward itself: a steady drift of 0.09 blocks a tick, which a walking player outpaces easily and a sneaking one feels
 * at a quarter of that. It never pulls anyone across open void: a body with a drop of 5 or more anywhere between it and
 * the star (its own column included) is left alone, and the star itself never drifts out over the void.
 *
 * <p>Touching it burns: when something touches it the heart flares white for 8 ticks (the stones clench round it, a
 * charging whine, white sparks), then everything still touching takes its attack (armour counts), and is held where it
 * is rather than knocked back. Then it waits a second before it can flare again.
 *
 * <p>Every 100 ticks it dims for 40. For the 20 ticks before (the tell) its stones loosen and wobble outward, the heart
 * flickers and a hum falls; dim, its heart gutters to an ember that glows weak-point gold: no pull, no burn, and it
 * takes double damage. Counter: walk against the pull (or sneak through it), keep off its touch while it burns, and
 * strike its heart while it is dim.
 */
public class Pullstar extends TeemingMonster {
    /** Bright and pulling (idle), the loosening before the dim (its tell), and the dim. */
    public enum Step implements Phase { IDLE, TELL, DIM }

    /** One full turn of its heart: bright, the tell, then the dim. */
    public static final int CYCLE_TICKS = 100;
    public static final int TELL_TICKS = 20;
    public static final int DIM_TICKS = 40;
    /** Bright with no warning yet: what is left of the cycle. */
    public static final int BRIGHT_TICKS = CYCLE_TICKS - TELL_TICKS - DIM_TICKS;
    /** Damage it takes while dim, as a multiple. */
    public static final float DIM_DAMAGE = 2.0F;
    /** The pull reaches bodies whose middle is within this many blocks of its own. */
    public static final double PULL_RADIUS = 8.0;
    /** The steady speed the pull draws a body that stands still, in blocks a tick: 1.8 m/s against a walk's 4.3. */
    public static final double PULL_DRIFT = 0.09;
    /** A sneaking player is pulled at this share of it. */
    public static final double SNEAK_SHARE = 0.25;
    /** The touch: its wind-up (the flare), its damage as a multiple of its attack, and the reach it counts as a touch. */
    public static final int FLARE_TICKS = 8;
    public static final float TOUCH_HIT = 1.0F;
    public static final double TOUCH_REACH = 0.25;
    /** From one flare's start to the earliest next one. */
    public static final int TOUCH_EVERY = 28;
    /**
     * It hovers this far over the ground under it, and drifts up to its target only until this far off across: inside
     * its reach it waits and lets the pull do the rest, so a player who walks away leaves it behind.
     */
    public static final double HOVER = 1.0;
    public static final double KEEP = 6.0;
    public static final int XP = 10;

    /** Glow stages: the heart dark (dim; only the gold shows), bright, and blazing in the flare. */
    public static final int STAGE_DARK = 0, STAGE_BRIGHT = 1, STAGE_FLARE = 2;
    /** Synced flags: the flare under way (for the pose), and a pull an event switched off for this bright stretch. */
    public static final int FLAG_FLARE = 0, FLAG_NO_PULL = 1;

    /** Its reach traced on the ground: dark violet, which reads on end stone. */
    private static final DustParticleOptions REACH = new DustParticleOptions(0x3A1A6E, 1.6F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(0xF6F2FF, 1.0F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD36B, 1.0F);
    private static final BlockParticleOption GRIT = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
    private static final Telegraph FLARE = Telegraph.particles(ParticleTypes.END_ROD, 6, 0.3, 0.5).withSpeed(0.06);
    private static final Telegraph FLARE_TRAIL = Telegraph.particles(WHITE, 6, 0.35, 0.5);
    private static final int REACH_POINTS = 48;
    /** Steps along the line to a body when looking for open void between, in blocks. */
    private static final double VOID_STEP = 0.5;
    private static final double WANDER = 5.0;

    private long brightSince = -1;
    private long flareAt = -1;
    private long nextFlare;
    private @Nullable Phase seen;
    private boolean lifted;
    private @Nullable Vec3 lastLand;
    private @Nullable Vec3 wanderTo;
    private int touches;
    private int flares;

    public Pullstar(EntityType<? extends Pullstar> type, Level level) {
        super(type, level, Step.values());
        setNoGravity(true);
        xpReward = XP;
        core().setStage(STAGE_BRIGHT);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("pullstar", 0.08).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public int visualStages() {
        return 3;
    }

    /** Its heart, guttered to an ember, glows weak-point gold while it is dim. */
    @Override
    public boolean vulnerable() {
        return core().in(Step.DIM);
    }

    /** It always hovers: it never falls and never takes a fall. */
    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(stageFor(phase, 0));
        return true;
    }

    // ---- the cycle ---------------------------------------------------------------------------------------------------

    /** Burning: idle or in its tell, alive, and not switched off by an event. Both sides know it. */
    public boolean pulls() {
        return isAlive() && !isDeadOrDying() && (core().idle() || core().in(Step.TELL)) && !core().flag(FLAG_NO_PULL);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            // a player's own client moves them: each one pulls itself toward every star it is in reach of
            if (pulls()) for (Player p : level().players()) if (p.isLocalPlayer()) pullBody(p);
            return;
        }
        long now = level.getGameTime();
        if (!lifted) rise(level);
        if (core().idle() && (seen != Step.IDLE || brightSince < 0)) brighten(level, now);
        if (!isNoAi() && isAlive() && core().idle() && now - brightSince >= BRIGHT_TICKS) {
            if (!startPhase(Step.TELL, TELL_TICKS, getTarget())) brightSince = now;   // a vetoed tell skips this dim
            else playSound(ModSounds.PULLSTAR_FADE.get(), 1.4F, 1.1F);
        }
        flareTick(level, now);
        if (pulls()) {
            pullAll(level);
            brightParticles(level);
        }
        if (core().in(Step.TELL)) tellTick(level);
        else if (core().in(Step.DIM)) dimTick(level);
        if (!isNoAi() || isDeadOrDying()) core().setStage(stageFor(core().phase(), core().elapsed()));
        seen = core().phase();
    }

    /** A bright stretch begins: the pull is asked for once (an event may switch it off until the next). */
    private void brighten(ServerLevel level, long now) {
        if (seen == Step.DIM) {
            playSound(ModSounds.PULLSTAR_RELIGHT.get(), 1.2F, 1.0F);
            level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.2, 0.2, 0.2, 0.05);
        }
        brightSince = now;
        core().setFlag(FLAG_NO_PULL, !MechanicEvent.post(this, "pull", null));
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && startPhase(Step.DIM, DIM_TICKS, core().target())) {
            playSound(ModSounds.PULLSTAR_DIM.get(), 1.4F, 1.0F);
            endFlare();
            return;
        }
        core().end();
    }

    /**
     * The glow for {@code phase}, {@code t} ticks in: dark while dim or dead; in the tell the heart flickers, faster as
     * the dim nears; bright otherwise, blazing in a flare.
     */
    private int stageFor(Phase phase, int t) {
        if (isDeadOrDying() || phase == Step.DIM) return STAGE_DARK;
        if (phase == Step.TELL) {
            int every = t < 6 ? 0 : t < 12 ? 4 : t < 16 ? 3 : 2;
            return every > 0 && t % every == 0 ? STAGE_DARK : STAGE_BRIGHT;
        }
        return core().flag(FLAG_FLARE) ? STAGE_FLARE : STAGE_BRIGHT;
    }

    /** The tell: grit falls from the loosening stones and the hum falls in three steps. */
    private void tellTick(ServerLevel level) {
        int t = core().elapsed();
        if (t % 2 == 0) {
            double a = random.nextDouble() * Mth.TWO_PI;
            level.sendParticles(GRIT, getX() + Math.cos(a) * 0.5, getY() + getBbHeight() * 0.4, getZ() + Math.sin(a) * 0.5, 2, 0.1, 0.05, 0.1, 0.0);
        }
        if (t == 7 || t == 14) playSound(ModSounds.PULLSTAR_FADE.get(), 1.4F, t == 7 ? 0.85F : 0.6F);
    }

    /** Dim: smoke off the ember, and gold motes from the weak point. */
    private void dimTick(ServerLevel level) {
        int t = core().elapsed();
        double y = getY() + getBbHeight() * 0.5;
        if (t % 3 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), y + 0.2, getZ(), 1, 0.1, 0.05, 0.1, 0.01);
        if (t % 4 == 0) level.sendParticles(GOLD, true, true, getX(), y, getZ(), 2, 0.2, 0.2, 0.2, 0.0);
    }

    // ---- the pull ----------------------------------------------------------------------------------------------------

    private Vec3 middle() {
        return position().add(0.0, getBbHeight() * 0.5, 0.0);
    }

    /** True when the middle of {@code e} is within 8 blocks of its own. */
    public boolean inReach(Entity e) {
        return e.getBoundingBox().getCenter().distanceToSqr(middle()) <= PULL_RADIUS * PULL_RADIUS;
    }

    /**
     * What the pull may move: players not in creative or spectator and not gliding, and mobs; not decorations, bosses,
     * shulkers, riders, another Pullstar, or what another mob holds.
     */
    public boolean pullable(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isSpectator() || e.isPassenger()) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isFallFlying();
        if (!(e instanceof Mob)) return false;
        return !(e instanceof Pullstar || e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Shulker) && !Grab.isHeld(e);
    }

    /**
     * The steady speed the pull draws {@code e} at now, in blocks a tick: 0.09, a quarter of that for a sneaking player,
     * half of either with a Grounding Charm in the off-hand, and 0 for anything out of reach, across open void, already
     * touching it or not pulled at all, or while it is dim. A player's own client asks it too, and sees the same charm.
     */
    public double drift(LivingEntity e) {
        if (!pulls() || !pullable(e) || !inReach(e) || alongside(e) || voidBetween(e)) return 0.0;
        return (e.isShiftKeyDown() ? PULL_DRIFT * SNEAK_SHARE : PULL_DRIFT) * Charms.grounded(e);
    }

    /** Horizontally so close it is already pressed against the star: no more pull (no jitter in its middle). */
    private boolean alongside(Entity e) {
        double min = (getBbWidth() + e.getBbWidth()) * 0.5 + 0.05;
        return Mth.square(e.getX() - getX()) + Mth.square(e.getZ() - getZ()) <= min * min;
    }

    /**
     * Pulls {@code e} one tick: adds what keeps it drifting at {@link #drift} once its own friction has taken its share
     * (on stone 45 % of its speed a tick, in the air 9 %), so a body drifts at the same speed on any ground.
     */
    private void pullBody(LivingEntity e) {
        double speed = drift(e);
        if (speed <= 0.0) return;
        Vec3 in = new Vec3(getX() - e.getX(), 0.0, getZ() - e.getZ()).normalize();
        double add = speed * (1.0 - keep(e));
        e.setDeltaMovement(e.getDeltaMovement().add(in.x * add, 0.0, in.z * add));
    }

    /** The share of its speed {@code e} keeps from one tick to the next. */
    private static double keep(LivingEntity e) {
        if (e.isInWater() || e.isInLava()) return 0.8;
        if (e.onGround()) return e.level().getBlockState(e.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91;
        return 0.91;
    }

    /** Server: one tick of the pull on every mob and item in reach (players pull themselves on their own client). */
    private void pullAll(ServerLevel level) {
        AABB box = new AABB(middle(), middle()).inflate(PULL_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> !(x instanceof Player) && pullable(x))) {
            if (drift(e) > 0.0 && MechanicEvent.post(this, "pull", e)) pullBody(e);
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, i -> i.isAlive() && inReach(i) && !alongside(i))) {
            if (voidBetween(item) || !MechanicEvent.post(this, "pull_item", null)) continue;
            Vec3 in = new Vec3(getX() - item.getX(), 0.0, getZ() - item.getZ());
            double d = in.length();
            double step = Math.min(PULL_DRIFT, d);
            Push.set(item, new Vec3(in.x / d * step, item.getDeltaMovement().y, in.z / d * step));
        }
    }

    /**
     * True when open void lies between {@code e} and the star: along the line from its column to the star's, every
     * half block, a column with nothing solid in the 5 cells under the height of its feet (the Brinkram's edge). Its
     * own column counts.
     */
    public boolean voidBetween(Entity e) {
        double dx = getX() - e.getX(), dz = getZ() - e.getZ();
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / VOID_STEP));
        int feet = Mth.floor(e.getY() + 0.01);
        for (int i = 0; i <= steps; i++) {
            double k = (double) i / steps;
            BlockPos column = BlockPos.containing(e.getX() + dx * k, feet, e.getZ() + dz * k);
            if (!level().hasChunkAt(column)) return true;
            if (Brinkram.Brink.edge(dy -> solid(level(), column.above(dy)))) return true;
        }
        return false;
    }

    private static boolean solid(Level level, BlockPos pos) {
        if (pos.getY() < level.getMinY() || level.isOutsideBuildHeight(pos)) return false;
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Motes streaming in to the heart, its reach traced on the ground, and a thrum now and then. */
    private void brightParticles(ServerLevel level) {
        Vec3 m = middle();
        if (tickCount % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Mth.TWO_PI, r = 2.0 + random.nextDouble() * (PULL_RADIUS - 2.0);
                double px = m.x + Math.cos(a) * r, pz = m.z + Math.sin(a) * r, py = getY() - HOVER + 0.2 + random.nextDouble() * 1.6;
                // a speed-0 count-0 particle moves along its offset: each mote flies in toward the heart
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, px, py, pz, 0, m.x - px, m.y - py, m.z - pz, 0.12);
            }
        }
        if (tickCount % 10 == 0) {
            double y = Double.isNaN(groundBelow(getX(), getZ(), getY())) ? getY() - HOVER : groundBelow(getX(), getZ(), getY());
            double turn = tickCount * 0.01;
            for (int i = 0; i < REACH_POINTS; i++) {
                double a = turn + i * (Mth.TWO_PI / REACH_POINTS);
                level.sendParticles(REACH, true, true, getX() + Math.cos(a) * PULL_RADIUS, y + 0.15, getZ() + Math.sin(a) * PULL_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (tickCount % 40 == 0) playSound(ModSounds.PULLSTAR_PULL.get(), 0.9F, 0.9F + random.nextFloat() * 0.1F);
    }

    // ---- the touch ---------------------------------------------------------------------------------------------------

    /** What its touch burns: players not in creative or spectator, and mobs; never another Pullstar. */
    public boolean touchable(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isSpectator() || e instanceof Pullstar) return false;
        if (e instanceof Player p) return !p.isCreative();
        return e instanceof Mob;
    }

    /** Everything touching it now: hitboxes within a quarter block, across or up and down. */
    public List<LivingEntity> touching() {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(TOUCH_REACH + 0.1), this::touchable)) {
            if (MobCore.gap(this, e) <= TOUCH_REACH) out.add(e);
        }
        return out;
    }

    /** While it burns: a touch starts a flare; a flare runs 8 ticks and lands on whatever still touches it. */
    private void flareTick(ServerLevel level, long now) {
        boolean burning = isAlive() && !isDeadOrDying() && (core().idle() || core().in(Step.TELL));
        if (flareAt >= 0) {
            if (!burning) {
                endFlare();                                             // dim or dead: the flare gutters with the heart
            } else if (now - flareAt >= FLARE_TICKS) {
                endFlare();
                touch(level);
            } else {
                FLARE_TRAIL.trail(this, 3);
            }
            return;
        }
        if (!burning || now < nextFlare) return;
        for (LivingEntity e : touching()) {
            if (!MechanicEvent.post(this, "flare", e)) continue;
            flareAt = now;
            nextFlare = now + TOUCH_EVERY;
            flares++;
            core().setFlag(FLAG_FLARE, true);
            core().setStage(STAGE_FLARE);
            FLARE.play(this);
            playSound(ModSounds.PULLSTAR_FLARE.get(), 1.2F, 1.0F);
            return;
        }
    }

    private void endFlare() {
        flareAt = -1;
        core().setFlag(FLAG_FLARE, false);
        if (core().stage() == STAGE_FLARE) core().setStage(isDeadOrDying() || core().in(Step.DIM) ? STAGE_DARK : STAGE_BRIGHT);
    }

    /** The flare lands: its attack (armour counts), on everything still touching it, held where it is (no knockback). */
    private void touch(ServerLevel level) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * TOUCH_HIT;
        for (LivingEntity v : touching()) {
            if (!MechanicEvent.post(this, "touch", v)) continue;
            if (!v.hurtServer(level, damageSources().mobAttack(this), damage)) continue;
            touches++;
            setLastHurtMob(v);
            Push.set(v, new Vec3(0.0, Math.min(0.0, v.getDeltaMovement().y), 0.0));
            double y = v.getY() + v.getBbHeight() * 0.5;
            level.sendParticles(ParticleTypes.CRIT, true, true, v.getX(), y, v.getZ(), 10, 0.25, 0.25, 0.25, 0.2);
            level.sendParticles(WHITE, true, true, v.getX(), y, v.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, v.getX(), y, v.getZ(), ModSounds.PULLSTAR_TOUCH.get(), getSoundSource(), 1.0F, 1.0F);
        }
    }

    // ---- the dim heart -----------------------------------------------------------------------------------------------

    /** Dim, it takes double damage, and a blow that lands rings off the gold heart. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean dim = core().in(Step.DIM) && amount > 0.0F && !isDeadOrDying()
                && MechanicEvent.post(this, "heart_hit", source.getEntity() instanceof LivingEntity l ? l : null);
        boolean hurt = super.hurtServer(level, source, dim ? amount * DIM_DAMAGE : amount);
        if (hurt && dim) {
            double y = getY() + getBbHeight() * 0.5;
            level.playSound(null, getX(), y, getZ(), ModSounds.PULLSTAR_HEART_HIT.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(GOLD, true, true, getX(), y, getZ(), 14, 0.25, 0.25, 0.25, 0.0);
            level.sendParticles(ParticleTypes.CRIT, getX(), y, getZ(), 8, 0.2, 0.2, 0.2, 0.3);
        }
        return hurt;
    }

    // ---- hovering ----------------------------------------------------------------------------------------------------

    /** The top of the highest solid block under (x, z) within 6 below {@code fromY}, or NaN (open void). */
    private double groundBelow(double x, double z, double fromY) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, fromY, z).mutable();
        for (int i = 0; i <= (int) HOVER + Brinkram.Brink.MAX_DROP + 1; i++, pos.move(0, -1, 0)) {
            if (pos.getY() < level().getMinY()) return Double.NaN;
            VoxelShape shape = level().getBlockState(pos).getCollisionShape(level(), pos);
            if (!shape.isEmpty() && pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) <= fromY + 1.0E-6) {
                return pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return Double.NaN;
    }

    /** Its first tick: up to its hover height over the ground it was set on. */
    private void rise(ServerLevel level) {
        lifted = true;
        double ground = groundBelow(getX(), getZ(), getY() + 0.5);
        if (!Double.isNaN(ground) && getY() < ground + HOVER) setPos(getX(), ground + HOVER, getZ());
    }

    /**
     * Steering: it hovers a block over the ground, drifts up to its target until 6 blocks off, otherwise wanders a
     * little, and never moves out over open void. Pushed out over it, it drifts back to the last land it hovered over.
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
        double ground = groundBelow(getX(), getZ(), getY());
        if (!Double.isNaN(ground)) lastLand = position();
        Vec3 want = Vec3.ZERO;
        LivingEntity target = getTarget();
        boolean hunting = target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
        if (Double.isNaN(ground) && lastLand != null) {
            want = across(lastLand, speed);
        } else if (hunting) {
            double gap = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
            if (gap > KEEP) want = across(target.position(), speed);
        } else {
            if (wanderTo == null || random.nextInt(160) == 0 || across(wanderTo, 1.0).lengthSqr() < 1.0E-6
                    || Math.hypot(wanderTo.x - getX(), wanderTo.z - getZ()) < 0.5) {
                double a = random.nextDouble() * Mth.TWO_PI, r = 1.0 + random.nextDouble() * WANDER;
                Vec3 spot = new Vec3(getX() + Math.cos(a) * r, getY(), getZ() + Math.sin(a) * r);
                wanderTo = Double.isNaN(groundBelow(spot.x, spot.z, getY())) ? null : spot;
            }
            if (wanderTo != null) want = across(wanderTo, speed * 0.5);
        }
        if (want.lengthSqr() > 1.0E-8 && !Double.isNaN(ground)) {
            Vec3 ahead = position().add(want.normalize().scale(getBbWidth() * 0.5 + 0.5));
            if (Double.isNaN(groundBelow(ahead.x, ahead.z, getY()))) want = Vec3.ZERO;   // the void ahead: it stops at the brink
        }
        double vy = Double.isNaN(ground) ? 0.0 : Mth.clamp((ground + HOVER - getY()) * 0.25, -0.08, 0.08);
        setDeltaMovement(want.x, vy, want.z);
        Vec3 look = hunting ? target.position().subtract(position()) : want;
        if (look.horizontalDistanceSqr() > 1.0E-6) {
            float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }

    /** Across the ground toward {@code to} at {@code speed}, no farther than it is. */
    private Vec3 across(Vec3 to, double speed) {
        Vec3 d = new Vec3(to.x - getX(), 0.0, to.z - getZ());
        double len = d.length();
        if (len < 1.0E-4) return Vec3.ZERO;
        return d.scale(Math.min(speed, len) / len);
    }

    // ---- test hooks --------------------------------------------------------------------------------------------------

    /** Flares started and touches that landed, since it spawned. Public as test hooks. */
    public int flares() {
        return flares;
    }

    public int touches() {
        return touches;
    }

    /** True while a flare is under way. Public as a test hook. */
    public boolean flaring() {
        return flareAt >= 0;
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.PULLSTAR_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PULLSTAR_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PULLSTAR_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.1F;
    }
}
