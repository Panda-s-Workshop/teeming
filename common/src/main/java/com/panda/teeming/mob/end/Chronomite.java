package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.Gauge;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.Zone;
import com.panda.teeming.registry.ModSounds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Chronomite: a squat creature of the End midlands and barrens carrying a domed clockwork shell with a glowing dial.
 * While its shell holds, a time field of radius 6 is on round it, marked by a slowly turning ring of sand-amber motes on
 * the ground and a ticking: players and mobs inside move at x0.4, projectiles inside fly at x0.4 (time catches up as one
 * strikes, so it hits at full force), and attacks come 40 % slower (attack speed x0.6). The shell holds 20 points: it
 * takes every blow before its health does, its cracks glowing amber and growing as it drains. When it cracks the field
 * ends at once: the shell shatters and its sand pours out, and the field never comes back.
 *
 * <p>It fights in melee: an 8-tick tell (its front rears up and its mandibles spread, a click, sand motes), then a
 * bite for its attack damage at whatever is still in reach in front of it.
 *
 * <p>Counter: crack the shell, ideally from outside the field, where you are quick and your shots only crawl the last
 * few blocks. The payoff is a broken part: the field is gone for the rest of its life. Failing it: fighting inside the
 * field, slowed to a crawl and swinging slowly while it bites.
 *
 * <p>The time field is kept in its own section ({@link #slow}, {@link #release}, the shots) so the kit can lift it later.
 * The shell is saved (a cracked one stays cracked); a tell or bite under way is not.
 */
public class Chronomite extends TeemingMonster {
    /** IDLE, the tell before a bite, the bite, the shatter as its shell cracks. */
    public enum Step implements Phase { IDLE, TELL, BITE, SHATTER }

    /** The field reaches this far from its feet (any part of a hitbox within it is inside). */
    public static final double FIELD_RADIUS = 6.0;
    /** Inside the field: movement speed x0.4. */
    public static final double FIELD_MOVE = 0.4;
    /** Inside the field: projectiles fly at x0.4. */
    public static final double FIELD_SHOTS = 0.4;
    /** Inside the field: attack speed x0.6, so attacks come 40 % slower. */
    public static final double FIELD_ATTACK = 0.6;
    /** The shell: this many points of damage are taken before its health. */
    public static final float SHELL = 20.0F;

    public static final int TELL_TICKS = 8;              // 0.4 s reared up before the bite
    public static final int BITE_TICKS = 6;              // the bite lands on its first tick; the head comes back this long
    public static final int SHATTER_TICKS = 12;          // the shell bursts and its sand pours out
    public static final int COOLDOWN = 20;               // ticks from the end of one bite to the next tell
    public static final float BITE = 1.0F;               // x attack damage
    public static final double START_REACH = 0.6;        // blocks between hitboxes: this close it rears up
    public static final double BITE_REACH = 1.2;         // blocks between hitboxes: a target this close when it bites is hit
    public static final float BITE_ARC = 120.0F;         // degrees in front of it the bite covers
    public static final float TURN = 15.0F;              // degrees a tick it turns after its target while it rears
    public static final int TICK_EVERY = 20;             // the field ticks once a second
    public static final int FIELD_RETRY = 20;            // a field a listener refused is asked for again this often

    /** Glow and model stages: 0 whole; 1 to 3 cracked more and more; 4 broken open, the field gone. */
    public static final int STAGE_WHOLE = 0;
    public static final int STAGE_BROKEN = 4;

    private static final Identifier SPEED_ID = TeemingMod.id("time_field_speed");
    private static final Identifier ATTACK_ID = TeemingMod.id("time_field_attack");

    /** Sand amber: an orange-brown glow, never the gold of a weak point. */
    private static final DustParticleOptions RING = new DustParticleOptions(0xD9884A, 1.0F);
    private static final DustParticleOptions MOTE = new DustParticleOptions(0xE0B48A, 0.7F);
    private static final DustParticleOptions CASE = new DustParticleOptions(0x2A2440, 1.3F);
    private static final DustParticleOptions RIB = new DustParticleOptions(0x9A5CB4, 1.0F);
    private static final BlockParticleOption SAND = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
    private static final int RING_POINTS = 40;
    private static final Zone.Shape SHAPE = Zone.Shape.ball(FIELD_RADIUS);

    private final Gauge shell = new Gauge(this, "Shell", SHELL).from(SHELL).stages(Chronomite::crackStage).onChange(this::shellChanged);
    private final Zone field = new Zone(SHAPE, 1).affects(this::fieldTakes, this::inField).drawn(this::drawField);
    /** Who the field slows now, and who it reached this tick. */
    private Set<LivingEntity> slowed = new HashSet<>();
    private Set<LivingEntity> reached = new HashSet<>();
    /** Projectiles the field slowed, with where each was a tick ago (one that stopped moving is stuck in a block). */
    private final Map<Projectile, Vec3> shots = new HashMap<>();
    private boolean fieldOpen;
    private int fieldRetry;
    private int cooldown;
    private int bites;
    private int cracks;
    private int strikesHastened;

    public Chronomite(EntityType<? extends Chronomite> type, Level level) {
        super(type, level, Step.values());
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("chronomite", 0.2);
    }

    @Override
    public int visualStages() {
        return STAGE_BROKEN + 1;
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

            /** It closes in only: every bite is the tell and the bite, started from its AI step. */
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
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

    /** The shatter shows the shell broken open from its first tick. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        if (phase == Step.SHATTER) core().setStage(STAGE_BROKEN);
        return true;
    }

    // ---- the shell -----------------------------------------------------------------------------------------------------

    /** The crack stage a shell of {@code points} shows: 0 whole, 1 to 3 by thirds as it drains, 4 at nothing. */
    public static int crackStage(float points) {
        if (points >= SHELL) return STAGE_WHOLE;
        if (points <= 0.0F) return STAGE_BROKEN;
        return 1 + Math.min(2, (int) ((SHELL - points) / (SHELL / 3.0F)));
    }

    /** Points left in the shell (0 once cracked). Server side. */
    public float shell() {
        return shell.value();
    }

    /** True while the shell holds. Server side. */
    public boolean shellHolds() {
        return shell.value() > 0.0F;
    }

    /** Sets the shell's points (0 cracks it, with the shatter). Public as a test hook. */
    public void setShell(float points) {
        shell.set(points);
    }

    private void shellChanged(float before) {
        if (before > 0.0F && shell.value() <= 0.0F) crack();
    }

    /**
     * Every blow goes to the shell first, before armour. A blow the shell can hold is taken there whole
     * ({@link #actuallyHurt}); a blow bigger than what the shell has left empties it (it cracks) and the rest lands as an
     * ordinary blow on its health, through its armour. The hurt cooldown counts as usual: a blow within it counts only
     * for what it adds to the last one. What reaches past invulnerability (a fall out of the world, a kill command) goes
     * straight through.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (amount > 0.0F && shellHolds() && !isDeadOrDying() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isInvulnerableTo(level, source)) {
            boolean cooling = damageCooldownTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN);
            float fresh = cooling ? amount - lastHurt : amount;
            if (fresh > shell.value()) {
                float had = shell.value();
                shell.set(0.0F);                       // cracks, with the shatter
                super.hurtServer(level, source, amount - had);
                return true;
            }
        }
        return super.hurtServer(level, source, amount);
    }

    /** A blow the shell can hold: taken from the shell, its health untouched. */
    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        if (amount > 0.0F && shellHolds() && !isInvulnerableTo(level, source) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float took = Math.min(shell.value(), amount);
            amount -= took;
            boolean last = took >= shell.value();
            if (!last) {
                level.playSound(null, getX(), getY(0.7), getZ(), ModSounds.CHRONOMITE_CLINK.get(), getSoundSource(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
                level.sendParticles(MOTE, getX(), getY(0.8), getZ(), 6, 0.3, 0.15, 0.3, 0.0);
                level.sendParticles(CASE, getX(), getY(0.8), getZ(), 3, 0.3, 0.15, 0.3, 0.0);
            }
            shell.set(shell.value() - took);          // a shell emptied here cracks, with the shatter
            if (amount <= 0.0F) return;
        }
        super.actuallyHurt(level, source, amount);
    }

    /** The counter landed: the field ends at once, the shell bursts and its sand pours out. */
    private void crack() {
        if (!(level() instanceof ServerLevel level)) return;
        closeField();
        cracks++;
        double y = getY() + getBbHeight() * 0.8;
        level.playSound(null, getX(), y, getZ(), ModSounds.CHRONOMITE_SHATTER.get(), getSoundSource(), 1.3F, 1.0F);
        level.playSound(null, getX(), y, getZ(), ModSounds.CHRONOMITE_POUR.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(CASE, true, true, getX(), y, getZ(), 24, 0.45, 0.25, 0.45, 0.0);
        level.sendParticles(RIB, true, true, getX(), y, getZ(), 12, 0.45, 0.25, 0.45, 0.0);
        level.sendParticles(RING, true, true, getX(), y, getZ(), 20, 0.6, 0.3, 0.6, 0.0);
        level.sendParticles(SAND, true, true, getX(), y + 0.2, getZ(), 24, 0.35, 0.15, 0.35, 0.0);
        if (isAlive()) startPhase(Step.SHATTER, SHATTER_TICKS, null);
        shell.show();
    }

    // ---- the time field --------------------------------------------------------------------------------------------------

    /** True while its field is on. Server side. */
    public boolean fieldOpen() {
        return fieldOpen;
    }

    /** How many bodies the field slows now. Server side. Public as a test hook. */
    public int slowedCount() {
        return slowed.size();
    }

    /** True while {@code e} is slowed by a time field. */
    public static boolean slowedByField(LivingEntity e) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SPEED_ID);
    }

    /** Whom the field takes: any living thing but a Chronomite, never a creative or spectating player. */
    private boolean fieldTakes(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Chronomite) && !e.isSpectator()
                && !(e instanceof Player p && p.isCreative()) && e.getAttribute(Attributes.MOVEMENT_SPEED) != null;
    }

    /** Each tick for whatever is inside: slowed, unless a listener keeps it out. */
    private void inField(LivingEntity e) {
        if (!slowed.contains(e) && !MechanicEvent.post(this, "slow", e)) return;
        slow(e);
        reached.add(e);
        if ((tickCount + e.getId()) % 6 == 0 && level() instanceof ServerLevel level) {
            // whatever the field holds wears a small ring of motes at its feet, turning as slowly as the big one
            double r = e.getBbWidth() * 0.8 + 0.2;
            double turn = tickCount * 0.02;
            for (int i = 0; i < 8; i++) {
                double a = turn + i * (Math.PI / 4);
                level.sendParticles(RING, e.getX() + Math.cos(a) * r, e.getY() + 0.1, e.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(MOTE, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3,
                    e.getBbWidth() * 0.4, 0.0);
        }
    }

    /** Slows a body: movement (walking and flying) x0.4, attack speed x0.6, until {@link #release}. */
    public static void slow(LivingEntity e) {
        modify(e, Attributes.MOVEMENT_SPEED, SPEED_ID, FIELD_MOVE - 1.0);
        modify(e, Attributes.FLYING_SPEED, SPEED_ID, FIELD_MOVE - 1.0);
        modify(e, Attributes.ATTACK_SPEED, ATTACK_ID, FIELD_ATTACK - 1.0);
    }

    /** Takes the field's slowing off a body. */
    public static void release(LivingEntity e) {
        for (Holder<Attribute> a : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            AttributeInstance i = e.getAttribute(a);
            if (i != null) i.removeModifier(SPEED_ID);
        }
        AttributeInstance attack = e.getAttribute(Attributes.ATTACK_SPEED);
        if (attack != null) attack.removeModifier(ATTACK_ID);
    }

    private static void modify(LivingEntity e, Holder<Attribute> attribute, Identifier id, double amount) {
        AttributeInstance i = e.getAttribute(attribute);
        if (i != null && !i.hasModifier(id)) i.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** The field: a ring of sand-amber motes turning slowly on the ground at its edge, motes drifting inside, a tick. */
    private void drawField(ServerLevel level, Vec3 at) {
        if (tickCount % 4 == 0) {
            double turn = tickCount * 0.01;
            for (int i = 0; i < RING_POINTS; i++) {
                double a = turn + i * (Math.PI * 2 / RING_POINTS);
                level.sendParticles(RING, at.x + Math.cos(a) * FIELD_RADIUS, at.y + 0.1, at.z + Math.sin(a) * FIELD_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
            }
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(random.nextDouble()) * FIELD_RADIUS;
                level.sendParticles(MOTE, at.x + Math.cos(a) * r, at.y + 0.2 + random.nextDouble() * 1.5, at.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (tickCount % TICK_EVERY == 0) {
            boolean tock = (tickCount / TICK_EVERY) % 2 == 1;
            level.playSound(null, getX(), getY(0.8), getZ(), ModSounds.CHRONOMITE_TICK.get(), getSoundSource(), 0.7F, tock ? 0.8F : 1.0F);
        }
    }

    /**
     * Server, every tick, AI or not: opens the field while the shell holds (asking listeners first), runs it, and closes
     * it when the shell has cracked, the mob is dying, or it shudders through the shatter.
     */
    private void fieldTick(ServerLevel level) {
        // a shell shown broken (the shatter, or a stage set from outside) carries no field either
        boolean wanted = isAlive() && shellHolds() && !core().in(Step.SHATTER) && core().stage() < STAGE_BROKEN;
        if (!wanted) {
            if (fieldOpen) closeField();
            return;
        }
        if (!fieldOpen) {
            if (fieldRetry > 0) {
                fieldRetry--;
                return;
            }
            if (!MechanicEvent.post(this, "field", null)) {
                fieldRetry = FIELD_RETRY;
                return;
            }
            field.carried(this);
            fieldOpen = true;
        }
        reached = new HashSet<>();
        field.tick(level);
        for (LivingEntity e : slowed) if (!reached.contains(e)) release(e);
        slowed = reached;
        shotsTick(level, position());
    }

    /** Ends the field: everything it slowed goes back to its own speed, every shot it held back flies on at full speed. */
    private void closeField() {
        field.clear();
        for (LivingEntity e : slowed) release(e);
        slowed = new HashSet<>();
        reached = new HashSet<>();
        for (Projectile p : shots.keySet()) if (!p.isRemoved()) hasten(p);
        shots.clear();
        fieldOpen = false;
    }

    // ---- the field's hold on projectiles ----------------------------------------------------------------------------------

    /**
     * Projectiles in the field: one coming in is slowed to x0.4 (its fall slowed to match), one leaving flies on at its
     * own speed, and one about to strike something living gets its speed back for the blow, so it hits at full force.
     */
    private void shotsTick(ServerLevel level, Vec3 centre) {
        Set<Projectile> inside = new HashSet<>(level.getEntitiesOfClass(Projectile.class, SHAPE.bounds(centre),
                p -> p.isAlive() && !(p instanceof FishingHook) && SHAPE.holds(centre, p.getBoundingBox())));
        Set<Projectile> struck = new HashSet<>();
        for (Iterator<Map.Entry<Projectile, Vec3>> it = shots.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Projectile, Vec3> held = it.next();
            Projectile p = held.getKey();
            if (p.isRemoved()) {
                it.remove();
                continue;
            }
            if (!inside.contains(p)) {
                hasten(p);
                it.remove();
                continue;
            }
            boolean moved = p.position().distanceToSqr(held.getValue()) > 1.0E-8;
            held.setValue(p.position());
            if (!moved) continue;                                         // stuck in a block: left as it is
            if (strikesNext(level, p, p.getDeltaMovement())) {
                hasten(p);
                strikesHastened++;
                struck.add(p);
                it.remove();
                continue;
            }
            // time runs slow for its fall too: of each tick's pull only x0.16 (0.4 squared) is left
            p.setDeltaMovement(p.getDeltaMovement().add(0.0, p.getGravity() * (1.0 - FIELD_SHOTS * FIELD_SHOTS), 0.0));
            Push.sync(p);
        }
        for (Projectile p : inside) {
            if (shots.containsKey(p) || struck.contains(p)) continue;
            Vec3 slow = p.getDeltaMovement().scale(FIELD_SHOTS);
            if (strikesNext(level, p, slow)) continue;                     // already striking: it lands at full force
            if (!MechanicEvent.post(this, "slow_shot", p.getOwner() instanceof LivingEntity owner ? owner : null)) continue;
            p.setDeltaMovement(slow);
            Push.sync(p);
            shots.put(p, p.position());
        }
    }

    private static void hasten(Projectile p) {
        p.setDeltaMovement(p.getDeltaMovement().scale(1.0 / FIELD_SHOTS));
        Push.sync(p);
    }

    /** True when moving {@code by} from where it is would carry {@code p} into something living it can hit. */
    private static boolean strikesNext(ServerLevel level, Projectile p, Vec3 by) {
        if (by.lengthSqr() < 1.0E-8) return false;
        Vec3 from = p.position();
        Vec3 to = from.add(by);
        BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
        Entity owner = p.getOwner();
        return ProjectileUtil.getEntityHitResult(level, p, from, to, p.getBoundingBox().expandTowards(by).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && e.isPickable() && !e.isSpectator() && e != owner) != null;
    }

    /** Held projectiles that got their speed back as they struck. Public as a test hook. */
    public int strikesHastened() {
        return strikesHastened;
    }

    /** How many projectiles the field holds back now. Public as a test hook. */
    public int heldShots() {
        return shots.size();
    }

    // ---- the bite --------------------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Blocks of open ground between its hitbox and {@code target}'s, across. */
    public double gapTo(Entity target) {
        double across = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        return across - (getBbWidth() + target.getBbWidth()) / 2.0;
    }

    /** True when {@code target} stands inside the arc its bite covers. */
    public boolean inFront(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return true;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.degreesDifferenceAbs(toward, getYRot()) <= BITE_ARC / 2.0F;
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
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) fieldTick(level);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (cooldown > 0) cooldown--;
        if (!core().in(Step.SHATTER) && core().stage() != shell.stage()) shell.show();
        if (!core().idle()) {
            holdStill();
            if (core().in(Step.TELL)) tellTick(level);
            else if (core().in(Step.SHATTER)) pourTick(level);
            return;
        }
        LivingEntity target = getTarget();
        if (cooldown == 0 && valid(target) && gapTo(target) <= START_REACH && getSensing().hasLineOfSight(target)) startTell(target);
    }

    /** Rears up at {@code target} for {@link #TELL_TICKS}; the bite follows. Public as a test hook. */
    public boolean startTell(LivingEntity target) {
        if (!core().idle() || !(level() instanceof ServerLevel level) || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        holdStill();
        face(target, 360.0F);
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.CHRONOMITE_TELL.get(), getSoundSource(), 1.0F, 1.0F);
        Telegraph.particles(MOTE, 8, 0.35, 0.9).play(this);
        return true;
    }

    /** The tell: it turns after its target, sand motes shaken off its raised front. */
    private void tellTick(ServerLevel level) {
        LivingEntity target = core().target();
        if (valid(target)) face(target, TURN);
        if (core().elapsed() % 2 == 0) {
            Vec3 front = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(getBbWidth() * 0.6));
            level.sendParticles(MOTE, front.x, getY() + getBbHeight() * 0.6, front.z, 2, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /** The shatter: sand pours from the broken shell. */
    private void pourTick(ServerLevel level) {
        level.sendParticles(SAND, getX(), getY() + getBbHeight() * 0.9, getZ(), 3, 0.3, 0.05, 0.3, 0.0);
        if (core().elapsed() % 3 == 0) level.sendParticles(MOTE, getX(), getY() + getBbHeight() * 0.7, getZ(), 2, 0.4, 0.1, 0.4, 0.0);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
        } else if (ended == Step.TELL) {
            bite(level, core().target());
        } else {
            rest();
            if (ended == Step.BITE) cooldown = COOLDOWN;
        }
    }

    private void rest() {
        core().end();
        shell.show();
    }

    /** The bite: its attack damage to a target still in reach and in front; anything else snaps at the air. */
    private void bite(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.BITE, BITE_TICKS, target)) {
            rest();
            cooldown = COOLDOWN;
            return;
        }
        level.playSound(null, getX(), getY(0.5), getZ(), ModSounds.CHRONOMITE_BITE.get(), getSoundSource(), 1.0F, 1.0F);
        if (!valid(target) || gapTo(target) > BITE_REACH || !inFront(target)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * BITE;
        if (target.hurtServer(level, damageSources().mobAttack(this), damage)) {
            bites++;
            setLastHurtMob(target);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
        }
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    // ---- ends, saving and test hooks -------------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) closeField();
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        if (!level().isClientSide()) closeField();
        super.onRemoval(reason);
    }

    /** Bites that landed so far. Public as a test hook. */
    public int bites() {
        return bites;
    }

    /** Times its shell cracked. Public as a test hook. */
    public int cracks() {
        return cracks;
    }

    /** Ticks until it may rear up again. Public as a test hook. */
    public int cooldown() {
        return cooldown;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        shell.save(output);
        output.putInt("Cooldown", cooldown);
    }

    /** The shell (a cracked one stays cracked) and the bite cooldown survive a reload; a tell or bite does not. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        shell.load(input);
        cooldown = Math.clamp(input.getIntOr("Cooldown", 0), 0, COOLDOWN);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.CHRONOMITE_AMBIENT.get() : null;
    }

    /** A blow on the shell clinks; once it is gone the body itself cries out. */
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return shellHolds() ? ModSounds.CHRONOMITE_CLINK.get() : ModSounds.CHRONOMITE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.CHRONOMITE_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.15F;
    }
}
