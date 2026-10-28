package com.panda.teeming.mob.nether;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Cindermoth Swarm: a drifting cloud of small glowing moths, one mob drawn as many. It is drawn to light and to nothing
 * else. It drifts slowly toward the brightest light within 12 blocks: a light-giving item someone holds, or one lying
 * on the ground. Reaching whoever carries its light, it flares, then engulfs them: while it engulfs, whatever it
 * touches burns, its attack damage (1) as fire, once a second, with flames showing on the victim while it does.
 *
 * <p>What gives light: any item that places a block which gives light (torches, lanterns, glowstone, shroomlights, sea
 * lanterns, campfires, froglights, end rods...), as bright as that block. Brightest wins; between two equally bright
 * lights one lying on the ground beats one held, then the nearer wins. Someone holding a light is sought at their
 * chest, where the light is carried. A target handed to it (a command, a pack, another mob) counts as the dimmest light
 * of all: it goes for it only while there is nothing brighter.
 *
 * <p>Counter: put the light away (it loses interest), or drop one: it hovers on a light on the ground for 200 ticks,
 * circling it, then loses interest in that light for good and goes back to whoever still carries one. A blow in melee
 * scatters the cloud for 40 ticks: it is thrown back, burns no one and forgets its light. Payoff: the swarm loses its
 * light and its burn while lured or scattered. A cloud is easy to cut: a sweeping sword swing takes double.
 *
 * <p>Tell: the flare. Touching the one it seeks, it stops where it is for 12 ticks: the moths pull together into a
 * tight knot and glow brighter, with a rising flutter and ember sparks. Nothing burns during the flare. Then it
 * engulfs them, if they are still in it; stepped out of it, it drifts after them and flares again when it touches them.
 * It never places or breaks a block, and it never takes the light: a dropped torch stays where it lies.
 */
public class CindermothSwarm extends TeemingMonster {
    /**
     * IDLE drifting, SEEK flying at a light, HOVER circling a light on the ground, TELL the flare before it engulfs,
     * ENGULF wrapped round a carrier, SCATTER after a blow.
     */
    public enum Step implements Phase { IDLE, SEEK, HOVER, TELL, ENGULF, SCATTER }

    /** The flare before it engulfs: 0.6 s. */
    public static final int TELL_TICKS = 12;

    /** It seeks the brightest light within this many blocks of the middle of the cloud. */
    public static final double SEEK_RANGE = 12.0;
    /** A light lying on the ground holds it this long, circling it; then that light bores it for good. */
    public static final int HOVER_TICKS = 200;
    /** While it engulfs, whatever it touches burns once every this many ticks: its attack damage, as fire. */
    public static final int BURN_INTERVAL = 20;
    /**
     * Flames set on whoever it burns. Vanilla fire hurts only when its timer passes a multiple of 20, so 19 ticks of
     * flames show the burn without ever burning on their own: the burn stops the moment it stops touching.
     */
    public static final int FLAME_TICKS = 19;
    /** A blow in melee scatters the cloud this long: it drifts apart, burns no one and forgets its light. */
    public static final int SCATTER_TICKS = 40;
    /** How hard the blow throws the scattered cloud back, in blocks a tick. */
    public static final double SCATTER_PUSH = 0.5;
    /** A sweeping sword swing cuts through the whole cloud: it takes this much more. */
    public static final float SWEEP_DAMAGE = 2.0F;
    /** A sword swing at least this charged sweeps (the vanilla rule), landing at 0.2 + 0.8 x 0.9² of full damage. */
    public static final float SWEEP_CHARGE = 0.9F;
    /** Its top speed while it seeks, in blocks a tick: slower than a walk (0.216), so walking away escapes it. */
    public static final double SEEK_SPEED = 0.12;
    /** It has reached a light on the ground once it is this close to it across, and circles it this high up. */
    public static final double HOVER_REACH = 1.0;
    public static final double HOVER_HEIGHT = 0.8;
    /** It looks round for the brightest light this often (and at once when the one it wanted is gone). */
    public static final int LOOK_TICKS = 10;
    /** A light on the ground it has not reached in this long (behind glass, over a drop) is given up for good. */
    public static final int GIVE_UP_TICKS = 300;
    /** It touches whatever comes this close to its hitbox. */
    public static final double TOUCH_REACH = 0.1;

    /** A light it could go for: an item on the ground, or someone holding one (or handed to it, at brightness 0). */
    public record Light(@Nullable ItemEntity item, @Nullable LivingEntity carrier, int brightness) {
        /** Where the light is: the item, or the carrier's chest. */
        public Vec3 at() {
            return item != null ? item.position() : carrier.position().add(0, carrier.getBbHeight() * 0.6, 0);
        }

        public boolean dropped() {
            return item != null;
        }

        boolean same(@Nullable Light other) {
            return other != null && other.item == item && other.carrier == carrier;
        }
    }

    private final Set<UUID> bored = new HashSet<>();
    private @Nullable Light light;
    private @Nullable ItemEntity lure;
    private @Nullable LivingEntity handed;
    private @Nullable AABB confine;
    private int burnCooldown;
    private int chasing;

    public CindermothSwarm(EntityType<? extends CindermothSwarm> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("cindermoth_swarm", 0.2).add(Attributes.FLYING_SPEED, 0.1);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // drifts about while it sees no light; its own phases fly it otherwise
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    // ---- what gives light --------------------------------------------------------------------------------------------

    /** How much light an item gives: as much as the block it places gives, or 0. */
    public static int brightness(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem block)) return 0;
        return block.getBlock().defaultBlockState().getLightEmission();
    }

    /** The brighter of the two lights someone holds, or 0. */
    public static int held(LivingEntity carrier) {
        return Math.max(brightness(carrier.getMainHandItem()), brightness(carrier.getOffhandItem()));
    }

    /** True when {@code a} (at {@code da} blocks) beats {@code b} (at {@code db}): brighter, then on the ground, then nearer. */
    private static boolean beats(Light a, double da, @Nullable Light b, double db) {
        if (b == null) return true;
        if (a.brightness() != b.brightness()) return a.brightness() > b.brightness();
        if (a.dropped() != b.dropped()) return a.dropped();
        return da < db;
    }

    /**
     * The light it would go for now: the brightest within {@link #SEEK_RANGE} by the rule above, leaving out lights on
     * the ground it has already circled. Null when there is none. Server only; public as a test hook.
     */
    public @Nullable Light brightest() {
        if (!(level() instanceof ServerLevel level)) return null;
        Vec3 from = centre();
        AABB around = getBoundingBox().inflate(SEEK_RANGE);
        Light best = null;
        double bestAt = 0.0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around, i -> i.isAlive() && !bored.contains(i.getUUID()) && inside(i))) {
            int b = brightness(item.getItem());
            double d = item.position().distanceTo(from);
            if (b <= 0 || d > SEEK_RANGE) continue;
            Light l = new Light(item, null, b);
            if (beats(l, d, best, bestAt)) {
                best = l;
                bestAt = d;
            }
        }
        for (Player p : level.getEntitiesOfClass(Player.class, around, this::prey)) {
            Light l = new Light(null, p, held(p));
            double d = l.at().distanceTo(from);
            if (l.brightness() <= 0 || d > SEEK_RANGE) continue;
            if (beats(l, d, best, bestAt)) {
                best = l;
                bestAt = d;
            }
        }
        if (handed != null && valid(handed) && inside(handed)) {
            Light l = new Light(null, handed, held(handed));
            double d = l.at().distanceTo(from);
            if (d <= SEEK_RANGE && beats(l, d, best, bestAt)) best = l;
        }
        return best;
    }

    /** Still worth going for: an item still lying there, a carrier still holding a light (or handed to it), in range. */
    private boolean live(Light l) {
        if (l.item() != null) {
            return l.item().isAlive() && !bored.contains(l.item().getUUID()) && l.item().position().distanceTo(centre()) <= SEEK_RANGE;
        }
        LivingEntity c = l.carrier();
        if (c == null || !valid(c) || l.at().distanceTo(centre()) > SEEK_RANGE) return false;
        return c == handed || held(c) > 0;
    }

    // ---- the drift ---------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        core().setStage(core().in(Step.TELL) ? 1 : 0);                 // the flare's brighter glow, and only the flare's
        if (burnCooldown > 0) burnCooldown--;
        if (core().in(Step.SCATTER)) {
            steerSelf();
            setDeltaMovement(getDeltaMovement().scale(0.85));      // the moths drift apart, slowing
            return;
        }
        if (core().in(Step.ENGULF)) burnTouching(level);
        if (core().in(Step.HOVER)) {
            hover(level);
            return;
        }
        if ((light != null && !live(light)) || (level.getGameTime() + getId()) % LOOK_TICKS == 0) choose(level);
        if (light == null) return;
        steerSelf();
        if (core().in(Step.TELL)) {
            flareTick(level);
            return;
        }
        if (light.item() != null) {
            seekItem(level, light.item());
        } else {
            seekCarrier(level, light.carrier());
        }
    }

    /** Looks round for the brightest light and turns to it; with none, it goes back to drifting. */
    private void choose(ServerLevel level) {
        Light pick = brightest();
        if (pick == null) {
            light = null;
            chasing = 0;
            super.setTarget(null);
            if (core().in(Step.SEEK) || core().in(Step.TELL) || core().in(Step.ENGULF)) core().end();
            return;
        }
        if (pick.same(light)) {
            light = pick;
            return;
        }
        boolean fresh = light == null;
        light = pick;
        chasing = 0;
        super.setTarget(pick.carrier());
        if (core().idle() || !core().in(Step.SEEK)) {
            if (!startPhase(Step.SEEK, 0, pick.carrier())) {
                light = null;
                return;
            }
        }
        if (fresh) cue(level, ParticleTypes.SMALL_FLAME, 6, ModSounds.CINDERMOTH_SWARM_DRAWN.get());
    }

    /** Flies to a light on the ground; once over it, it starts circling. Unreached for too long, it gives up on it. */
    private void seekItem(ServerLevel level, ItemEntity item) {
        if (!core().in(Step.SEEK) && !startPhase(Step.SEEK, 0, null)) return;
        Vec3 over = item.position().add(0, HOVER_HEIGHT + getBbHeight() * 0.5, 0);
        Vec3 c = centre();
        if (Math.hypot(over.x - c.x, over.z - c.z) <= HOVER_REACH && Math.abs(over.y - c.y) <= 1.0) {
            if (startPhase(Step.HOVER, HOVER_TICKS, null)) {
                lure = item;
                light = null;
                cue(level, ParticleTypes.SMALL_FLAME, 10, ModSounds.CINDERMOTH_SWARM_LURED.get());
            }
            return;
        }
        if (++chasing > GIVE_UP_TICKS) {
            bored.add(item.getUUID());
            light = null;
            core().end();
            return;
        }
        fly(over, SEEK_SPEED);
    }

    /**
     * Flies at a carrier's chest. Touching them it flares, and only after the flare does it engulf them; engulfing, it
     * stays wrapped round them, and seeks them again once they are out of it.
     */
    private void seekCarrier(ServerLevel level, LivingEntity carrier) {
        boolean touching = touches(carrier);
        if (core().in(Step.ENGULF) && touching) {
            fly(light.at(), SEEK_SPEED);
            return;
        }
        if (touching && flare(level, carrier)) return;
        if (!core().in(Step.SEEK) && !startPhase(Step.SEEK, 0, carrier)) return;
        fly(light.at(), SEEK_SPEED);
    }

    /**
     * The flare: it stops where it is for {@link #TELL_TICKS}, the moths drawn into a tight, bright knot, a flutter and
     * a burst of ember sparks; the engulf follows. False if the step was vetoed. Public as a test hook.
     */
    public boolean flare(ServerLevel level, LivingEntity carrier) {
        if (!startPhase(Step.TELL, TELL_TICKS, carrier)) return false;
        core().setStage(1);
        setDeltaMovement(Vec3.ZERO);
        face(carrier.position().subtract(position()));
        playSound(ModSounds.CINDERMOTH_SWARM_FLARE.get(), 0.8F, 0.85F);
        level.sendParticles(soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME,
                getX(), getY() + getBbHeight() * 0.5, getZ(), 10, 0.35, 0.3, 0.35, 0.03);
        return true;
    }

    /** The flare held: it hangs still, ember sparks fly off the knot, and the flutter rises halfway through. */
    private void flareTick(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        int t = core().elapsed();
        if (t % 2 == 0) {
            level.sendParticles(soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME,
                    getX(), getY() + getBbHeight() * 0.5, getZ(), 3, 0.15, 0.15, 0.15, 0.05);
        }
        if (t == TELL_TICKS / 2) playSound(ModSounds.CINDERMOTH_SWARM_FLARE.get(), 1.0F, 1.25F);
    }

    /** Circles a light on the ground; picked up or burnt away, it looks round afresh. */
    private void hover(ServerLevel level) {
        steerSelf();
        if (lure == null || !lure.isAlive()) {
            lure = null;
            core().end();
            return;
        }
        double a = (level.getGameTime() + getId() * 7L) * 0.12;
        Vec3 round = lure.position().add(Math.cos(a) * 0.6, HOVER_HEIGHT + getBbHeight() * 0.5, Math.sin(a) * 0.6);
        fly(round, SEEK_SPEED * 0.6);
    }

    /** Its own phases fly it: the drifting goal's path and steering stop, so nothing adds to the speed it sets. */
    private void steerSelf() {
        getNavigation().stop();
        getMoveControl().setWait();
    }

    private void fly(Vec3 to, double speed) {
        Vec3 d = to.subtract(centre());
        double length = d.length();
        Vec3 want = length < 1.0E-3 ? Vec3.ZERO : d.scale(Math.min(speed, length * 0.3) / length);
        setDeltaMovement(getDeltaMovement().lerp(want, 0.3));
        face(getDeltaMovement());
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            core().setStage(0);
            afterFlare(level);
            return;
        }
        if (ended == Step.HOVER && lure != null) {
            bored.add(lure.getUUID());                              // that light bores it now, for good
            playSound(ModSounds.CINDERMOTH_SWARM_BORED.get(), 0.8F, 0.95F + random.nextFloat() * 0.1F);
            forgetBored();
        }
        lure = null;
        light = null;
        core().end();
    }

    /**
     * The flare is over: with the carrier still in the cloud (and still its light) it engulfs them and burns at once;
     * otherwise it seeks them again.
     */
    private void afterFlare(ServerLevel level) {
        LivingEntity carrier = light != null ? light.carrier() : null;
        if (carrier == null || !live(light)) {
            light = null;
            core().end();
            return;
        }
        if (touches(carrier) && startPhase(Step.ENGULF, 0, carrier)) {
            burnTouching(level);
            return;
        }
        if (!startPhase(Step.SEEK, 0, carrier)) core().end();
    }

    /** Drops lights it got bored of that are gone from the world, so the list never grows without end. */
    private void forgetBored() {
        if (bored.size() <= 16 || !(level() instanceof ServerLevel level)) return;
        bored.removeIf(id -> level.getEntity(id) == null);
    }

    // ---- the burn ----------------------------------------------------------------------------------------------------

    /** While it engulfs: burns everyone it touches, once every {@link #BURN_INTERVAL} ticks, its attack damage as fire, and flames. */
    private void burnTouching(ServerLevel level) {
        if (burnCooldown > 0) return;
        List<LivingEntity> touching = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(TOUCH_REACH), this::burnable);
        boolean burnt = false;
        for (LivingEntity victim : touching) {
            if (!MechanicEvent.post(this, "burn", victim)) continue;
            if (victim.getRemainingFireTicks() < FLAME_TICKS) victim.setRemainingFireTicks(FLAME_TICKS);
            victim.hurtServer(level, damageSources().onFire(), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
            level.sendParticles(soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 6, 0.25, 0.4, 0.25, 0.01);
            burnt = true;
        }
        if (burnt) {
            burnCooldown = BURN_INTERVAL;
            playSound(ModSounds.CINDERMOTH_SWARM_BURN.get(), 0.9F, 0.95F + random.nextFloat() * 0.1F);
        }
    }

    /** Who it burns on touch: players in survival or adventure, and whatever it was handed as a target. */
    private boolean burnable(LivingEntity e) {
        return valid(e) && inside(e) && (prey(e) || e == handed);
    }

    private boolean touches(LivingEntity e) {
        return getBoundingBox().inflate(TOUCH_REACH).intersects(e.getBoundingBox());
    }

    // ---- a blow scatters it; a sweep cuts it -------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean sweep = sweeps(source, amount);
        boolean hurt = super.hurtServer(level, source, sweep ? amount * SWEEP_DAMAGE : amount);
        if (hurt && isAlive() && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && !source.is(DamageTypeTags.IS_PROJECTILE)) {
            scatter(level, attacker);
        }
        return hurt;
    }

    /** The blow scatters the cloud: thrown back from the attacker, it forgets its light and burns no one for a while. */
    private void scatter(ServerLevel level, LivingEntity attacker) {
        if (!startPhase(Step.SCATTER, SCATTER_TICKS, null)) return;
        light = null;
        lure = null;
        chasing = 0;
        super.setTarget(null);
        Vec3 away = position().subtract(attacker.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        setDeltaMovement(away.normalize().scale(SCATTER_PUSH).add(0, 0.1, 0));
        cue(level, soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, 14, ModSounds.CINDERMOTH_SWARM_SCATTER.get());
    }

    /**
     * A sweeping sword swing: a sword in the main hand, standing on the ground, not sprinting, at least 90 % charged (the
     * hit lands at 0.2 + 0.8 x charge² of the player's full attack).
     */
    private static boolean sweeps(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player p) || source.getDirectEntity() != p || p.isSpectator()) return false;
        if (!p.getMainHandItem().is(ItemTags.SWORDS) || p.isSprinting() || !standing(p)) return false;
        double full = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return amount >= full * (0.2 + 0.8 * SWEEP_CHARGE * SWEEP_CHARGE) - 1.0E-3;
    }

    /** On the ground, by its own flag or by the block under its feet (a player with no client never reports the flag). */
    private static boolean standing(LivingEntity e) {
        if (e.onGround()) return true;
        BlockPos below = BlockPos.containing(e.getX(), e.getY() - 0.01, e.getZ());
        VoxelShape shape = e.level().getBlockState(below).getCollisionShape(e.level(), below);
        return !shape.isEmpty() && Math.abs(e.getY() - (below.getY() + shape.max(Direction.Axis.Y))) < 0.01;
    }

    // ---- targets -----------------------------------------------------------------------------------------------------

    /** A target handed to it (a command, a pack, another mob) is sought as the dimmest light: only with nothing brighter about. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (level().isClientSide()) return;
        handed = target != null && target != this && !(target instanceof CindermothSwarm) ? target : null;
    }

    /** Players it is drawn to and burns: alive, in survival or adventure. */
    private boolean prey(LivingEntity e) {
        return e instanceof Player p && valid(p) && inside(p);
    }

    private boolean valid(LivingEntity e) {
        return e.isAlive() && !e.isRemoved() && e.level() == level() && e != this && !(e instanceof CindermothSwarm)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private boolean inside(Entity e) {
        return confine == null || confine.contains(e.position());
    }

    // ---- looks and cues ----------------------------------------------------------------------------------------------

    private boolean soul() {
        return "soul_moths".equals(look());
    }

    private void cue(ServerLevel level, ParticleOptions particle, int count, SoundEvent sound) {
        playSound(sound, 1.0F, 0.95F + random.nextFloat() * 0.1F);
        level.sendParticles(particle, getX(), getY() + getBbHeight() * 0.5, getZ(), count, 0.4, 0.35, 0.4, 0.02);
    }

    /** Embers drift off the cloud as it flies (client side only). */
    @Override
    public void aiStep() {
        super.aiStep();
        // soul flames are big next to the small flames: fewer of them; while it flares, many more
        boolean flaring = core().in(Step.TELL);
        if (level().isClientSide() && isAlive() && random.nextInt(soul() ? (flaring ? 3 : 12) : (flaring ? 1 : 4)) == 0) {
            level().addParticle(soul() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME,
                    getRandomX(0.6), getY() + random.nextDouble() * getBbHeight(), getRandomZ(0.6), 0.0, -0.01, 0.0);
        }
    }

    private Vec3 centre() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }

    /** Two glows: its usual one, and the brighter one of the flare. */
    @Override
    public int visualStages() {
        return 2;
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // a cloud neither pushes nor is pushed: it drifts through whoever it engulfs
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected void doPush(Entity other) {
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- test hooks --------------------------------------------------------------------------------------------------

    /**
     * Public as a test hook: from now on only lights, carriers and victims inside {@code box} count, so a test never
     * reaches into the arenas beside it; null lifts it.
     */
    public void confine(@Nullable AABB box) {
        confine = box;
    }

    /** True while it touches {@code e}. Public as a test hook. */
    public boolean touching(LivingEntity e) {
        return touches(e);
    }

    /** The light it is flying at, or null. Public as a test hook. */
    public @Nullable Light light() {
        return light;
    }

    /** The light on the ground it is circling, or null. Public as a test hook. */
    public @Nullable ItemEntity lure() {
        return lure;
    }

    /** True once it has circled {@code item} and lost interest in it. Public as a test hook. */
    public boolean boredOf(ItemEntity item) {
        return bored.contains(item.getUUID());
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.CINDERMOTH_SWARM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.CINDERMOTH_SWARM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.CINDERMOTH_SWARM_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + random.nextFloat() * 0.15F;
    }
}
