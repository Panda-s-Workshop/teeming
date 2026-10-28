package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Resonant Triad: three small floating tuning forks of void crystal that hover round their foe in a loose triangle and
 * sing in turn. They come as a group of three, linked at spawn (each keeps its triad's id, its seat 0-2 and the game time
 * the song started), and find each other again by that id after a load.
 *
 * <p>While all three live the triad is whole and one of them sings: its tines glow weak-point gold and it hums. Only the
 * singer takes damage; the other two shrug every hit off with a ping and a shimmer (blows do nothing, projectiles bounce
 * off), whatever hits them, except damage nothing can stop (the void, a kill command). The song passes to the next
 * seat every 60 ticks. For the last 10 ticks before it passes (the tell) the next one brightens star-white and the
 * chord rises in three steps. Kill the singer while it sings and the chord breaks: the two left crack, stop singing and
 * take damage like any mob, for good. A triad that loses a member any other way (one gone for 100 ticks) breaks too.
 *
 * <p>Each zaps whoever it hunts: within 5 blocks (across or up and down) and in sight it crackles for 8 ticks (its tines
 * pinch and spark, the fork leans in), then an arc of violet light strikes for its attack, through armour and without
 * knockback, if the target is still within 5 blocks and in sight. Then it waits 40 ticks before it can crackle again.
 *
 * <p>A natural group the spawner could seat only in part is completed by its first member 20 ticks after it arrives. A
 * shard from a spawn egg or a command links to the nearest triad within 8 blocks with a free seat, or comes alone; a
 * shard alone, or a triad short of a member, has no shield and never sings.
 */
public class ResonantTriad extends TeemingMonster {
    /** At rest (hovering), the zap's crackle, the zap. */
    public enum Step implements Phase { IDLE, TELL, ZAP }

    /** A triad: three seats. */
    public static final int SEATS = 3;
    /** Each seat sings this long, then the song passes to the next. */
    public static final int SING_TICKS = 60;
    /** The tell before the song passes: the next one brightens and the chord rises. */
    public static final int RISE_TICKS = 10;
    /** The singer's hum repeats this often. */
    public static final int HUM_EVERY = 20;
    /** The zap's crackle (tell). */
    public static final int TELL_TICKS = 8;
    /** The zap: the arc strikes as this starts; the rest is the fork swinging back. */
    public static final int ZAP_TICKS = 6;
    /** Ticks after a zap before it can crackle again. */
    public static final int ZAP_EVERY = 40;
    /** Blocks between hitboxes (across or up and down) it zaps across, and within which its zap lands. */
    public static final double ZAP_RANGE = 5.0;
    /** The zap's damage, times the attack. */
    public static final float ZAP = 1.0F;
    /** Its triad's members are looked for this far off. */
    public static final double LINK_RANGE = 16.0;
    /** A shard from an egg or a command joins a triad with a free seat this close. */
    public static final double JOIN_RANGE = 8.0;
    /** Ticks a whole triad waits for a missing member (unloaded, or gone) before its chord breaks. */
    public static final int PARTNER_LOST = 100;
    /** A natural group short of a member is completed this long after it arrives. */
    public static final int COMPLETE_AFTER = 20;
    /** The triangle: each shard hangs this far from the triad's middle. */
    public static final double RADIUS = 1.4;
    /** With a foe, the triad's middle keeps this far from it across the ground. */
    public static final double HOLD = 3.0;
    /** With no foe it hovers this high over the ground (when there is ground below). */
    public static final double HOVER = 1.5;
    /** Blocks a tick it flies to its place in the triangle; idle, the triangle drifts round its home this far out. */
    public static final double SPEED = 0.15;
    public static final double WANDER = 3.0;
    public static final int XP = 10;

    /** Glow stages: dim; rising (the next singer's tell); singing (the gold weak point, nothing else lit); broken. */
    public static final int STAGE_DIM = 0;
    public static final int STAGE_RISE = 1;
    public static final int STAGE_SING = 2;
    public static final int STAGE_BROKEN = 3;
    /** Synced flag bit: it shrugs hits off now (the triad is whole and it isn't singing). */
    public static final int FLAG_SHIELDED = 0;

    /** Weak-point gold motes round the singer, star-white ones round the rising one; the arc is violet (rose for the Rose Triad). */
    public static final int GOLD = 0xFFD36B;
    public static final int STAR_WHITE = 0xF2EEFF;
    public static final int ARC_VIOLET = 0xB58CFF;
    public static final int ARC_ROSE = 0xFF9CC8;
    private static final DustParticleOptions GOLD_MOTE = new DustParticleOptions(GOLD, 0.8F);
    private static final DustParticleOptions WHITE_MOTE = new DustParticleOptions(STAR_WHITE, 0.8F);
    private static final DustParticleOptions VIOLET_ARC = new DustParticleOptions(ARC_VIOLET, 0.7F);
    private static final DustParticleOptions ROSE_ARC = new DustParticleOptions(ARC_ROSE, 0.7F);
    private static final BlockParticleOption VIOLET_SHARDS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STAINED_GLASS.purple().defaultBlockState());
    private static final BlockParticleOption ROSE_SHARDS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STAINED_GLASS.pink().defaultBlockState());
    /** Where the tine tips are, over its feet and to each side, in blocks at scale 1. */
    private static final double TIPS_UP = 0.95, TIPS_SIDE = 0.22;
    private static final int GROUND_SEARCH = 10;

    // the link: its triad, its seat, when the song started, whether the three have been together, whether the chord broke
    private UUID triad = UUID.randomUUID();
    private int seat;
    private long epoch;
    private boolean formed;
    private boolean broken;
    /** A natural group's first member: it completes its triad if the spawner seated only part of it. */
    private boolean founder;
    private final List<ResonantTriad> kin = new ArrayList<>();
    private int kinScan;
    private int missing;
    private int shownStage = -1;
    private int untilZap = ZAP_EVERY / 2;
    private int lastShrug = -100;
    private @Nullable Vec3 home;
    /** Test hook: it hangs where it is; its zap and its song still work. */
    private boolean held;
    private int zaps;
    private int shrugs;

    public ResonantTriad(EntityType<? extends ResonantTriad> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("resonant_triad", SPEED).add(Attributes.FLYING_SPEED, SPEED);
    }

    @Override
    public int visualStages() {
        return 4;
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    // ---- the link ------------------------------------------------------------------------------------------------------

    /** What the spawner hands from one member of a natural group to the next: the triad's id, song, look and next seat. */
    public static final class Chord implements SpawnGroupData {
        final UUID id;
        final long epoch;
        final String look;
        int next;

        Chord(UUID id, long epoch, String look) {
            this.id = id;
            this.epoch = epoch;
            this.look = look;
        }
    }

    /**
     * A natural group links through the spawn data: the first starts a triad (seat 0, its look, the song starting now) and
     * each next one takes the next seat and the first's look. A shard spawned any other way (an egg, a command, a spawner)
     * joins the nearest triad within 8 blocks that has a free seat, or comes alone.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        long now = level.getLevel().getGameTime();
        if (data instanceof Chord chord && chord.next < SEATS) {
            core().setLook(chord.look, true);
            link(chord.id, chord.next++, chord.epoch);
            return chord;
        }
        if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) {
            Chord chord = new Chord(UUID.randomUUID(), now, look());
            link(chord.id, chord.next++, chord.epoch);
            founder = true;
            return chord;
        }
        if (!joinNearest(level)) link(UUID.randomUUID(), 0, now);
        return result;
    }

    private void link(UUID id, int seat, long epoch) {
        this.triad = id;
        this.seat = seat;
        this.epoch = epoch;
        this.formed = false;
        this.broken = false;
        kin.clear();
        kinScan = 0;
    }

    /** Joins the nearest triad within 8 blocks that isn't whole or broken and has a free seat; false if there is none. */
    private boolean joinNearest(ServerLevelAccessor level) {
        List<ResonantTriad> near = level.getEntitiesOfClass(ResonantTriad.class, getBoundingBox().inflate(JOIN_RANGE),
                t -> t != this && t.isAlive() && !t.broken && !t.formed);
        near.sort((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this)));
        for (ResonantTriad other : near) {
            boolean[] taken = new boolean[SEATS];
            for (ResonantTriad m : level.getEntitiesOfClass(ResonantTriad.class, other.getBoundingBox().inflate(LINK_RANGE),
                    t -> t.isAlive() && t.triad.equals(other.triad))) {
                if (m != this) taken[m.seat] = true;
            }
            for (int s = 0; s < SEATS; s++) {
                if (taken[s]) continue;
                if (!other.look().isEmpty()) core().setLook(other.look(), true);
                link(other.triad, s, other.epoch);
                return true;
            }
        }
        return false;
    }

    /**
     * Links three shards into one whole triad, seats 0, 1 and 2 in that order, the song starting at game time {@code epoch}
     * (seat 0 sings first). Public as a test and staging hook.
     */
    public static void link(ResonantTriad a, ResonantTriad b, ResonantTriad c, long epoch) {
        UUID id = UUID.randomUUID();
        ResonantTriad[] all = {a, b, c};
        for (int s = 0; s < SEATS; s++) {
            all[s].link(id, s, epoch);
            all[s].founder = false;
        }
        for (ResonantTriad t : all) {
            t.kin.clear();
            for (ResonantTriad o : all) if (o != t) t.kin.add(o);
            t.formed = true;
            t.kinScan = 10;
        }
    }

    /** The other living members of its triad within reach, found again every half second (server only). */
    private List<ResonantTriad> kin(ServerLevel level) {
        boolean stale = kin.removeIf(t -> !t.isAlive() || t.isRemoved() || !t.triad.equals(triad) || t.distanceToSqr(this) > LINK_RANGE * LINK_RANGE);
        // a triad still forming looks every tick, so a member that joins is found at once
        if (--kinScan <= 0 || stale || (!formed && !broken)) {
            kinScan = 10;
            kin.clear();
            kin.addAll(level.getEntitiesOfClass(ResonantTriad.class, getBoundingBox().inflate(LINK_RANGE),
                    t -> t != this && t.isAlive() && !t.isRemoved() && t.triad.equals(triad) && t.seat != seat));
        }
        return kin;
    }

    /** True while the triad is whole: the three have been together, the chord isn't broken and both others are here. */
    public boolean whole() {
        return level() instanceof ServerLevel level && isAlive() && formed && !broken && kin(level).size() == SEATS - 1;
    }

    /** The seat that sings at game time {@code now} (when the triad is whole). */
    public int singerAt(long now) {
        return (int) Math.floorMod(Math.floorDiv(now - epoch, (long) SING_TICKS), (long) SEATS);
    }

    /** Ticks since the song last passed, 0..59, at game time {@code now}. */
    public int intoSong(long now) {
        return (int) Math.floorMod(now - epoch, (long) SING_TICKS);
    }

    /** True while it sings: the triad is whole and its seat has the song (server only). */
    public boolean singing() {
        return whole() && singerAt(level().getGameTime()) == seat;
    }

    /** True while it shrugs hits off: the triad is whole and another seat has the song (server only). */
    public boolean shielded() {
        return whole() && singerAt(level().getGameTime()) != seat;
    }

    /** The singer's tines glow weak-point gold: it is the one target a blow can hurt. Clients read the synced stage. */
    @Override
    public boolean vulnerable() {
        if (level().isClientSide()) return core().stage() == STAGE_SING && isAlive();
        return singing();
    }

    // ---- the song -----------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        if (!isAlive()) return;
        long now = level.getGameTime();
        List<ResonantTriad> others = kin(level);
        if (!formed && !broken && others.size() == SEATS - 1) formed = true;
        if (formed && !broken) {
            if (others.size() < SEATS - 1) {
                if (++missing >= PARTNER_LOST) breakChord(level, false);
            } else {
                missing = 0;
            }
        }
        if (founder && tickCount >= COMPLETE_AFTER) {
            founder = false;
            if (!formed && !broken) complete(level);
        }
        boolean whole = formed && !broken && others.size() == SEATS - 1;
        int singer = singerAt(now), into = intoSong(now);
        int stage = broken ? STAGE_BROKEN : !whole ? STAGE_DIM : singer == seat ? STAGE_SING
                : into >= SING_TICKS - RISE_TICKS && (singer + 1) % SEATS == seat ? STAGE_RISE : STAGE_DIM;
        if (stage != shownStage) {                                    // only on a change, so a posed stage holds
            shownStage = stage;
            core().setStage(stage);
        }
        boolean shield = whole && singer != seat;
        if (core().flag(FLAG_SHIELDED) != shield) core().setFlag(FLAG_SHIELDED, shield);
        if (stage == STAGE_SING) {
            if (into % HUM_EVERY == 0) playSound(ModSounds.RESONANT_TRIAD_SING.get(), 1.0F, rose() ? 1.1F : 1.0F);
            if (tickCount % 3 == 0) tipMotes(level, GOLD_MOTE, 0.0);
        } else if (stage == STAGE_RISE) {
            int step = into - (SING_TICKS - RISE_TICKS);
            // the chord rises in three steps over the tell
            if (step % 4 == 0 && step < RISE_TICKS) playSound(ModSounds.RESONANT_TRIAD_RISE.get(), 1.0F, 1.0F + 0.15F * (step / 4));
            if (tickCount % 2 == 0) tipMotes(level, WHITE_MOTE, 0.06);
        } else if (stage == STAGE_BROKEN && tickCount % 12 == 0) {
            level.sendParticles(shards(), getX(), getY(0.5), getZ(), 1, 0.15, 0.2, 0.15, 0.0);
        }
    }

    /** Motes at both tine tips (rising when {@code rise} is above 0). */
    private void tipMotes(ServerLevel level, DustParticleOptions mote, double rise) {
        Vec3 side = left(yBodyRot).scale(TIPS_SIDE * getScale());
        Vec3 top = position().add(0.0, TIPS_UP * getScale(), 0.0);
        for (Vec3 tip : new Vec3[]{top.add(side), top.subtract(side)}) {
            level.sendParticles(mote, true, false, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, rise);
        }
    }

    /**
     * The chord breaks for this member: it cracks, stops singing and takes damage as any mob does, for good. {@code loud}:
     * a member died and the break rings out; a member that went missing breaks quietly.
     */
    private void breakChord(ServerLevel level, boolean loud) {
        if (broken) return;
        broken = true;
        missing = 0;
        core().setFlag(FLAG_SHIELDED, false);
        if (loud) {
            level.playSound(null, getX(), getY(0.5), getZ(), ModSounds.RESONANT_TRIAD_BREAK.get(), getSoundSource(), 1.2F, rose() ? 1.1F : 1.0F);
            level.sendParticles(shards(), true, true, getX(), getY(0.6), getZ(), 14, 0.2, 0.3, 0.2, 0.08);
            level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY(0.7), getZ(), 4, 0.15, 0.2, 0.15, 0.03);
        }
    }

    /**
     * A natural group short of a member: the first fills the empty seats beside itself, as many as it finds room for in
     * open air within 2 blocks, each with its triad's id, song and look.
     */
    private void complete(ServerLevel level) {
        boolean[] taken = new boolean[SEATS];
        taken[seat] = true;
        for (ResonantTriad m : kin(level)) taken[m.seat] = true;
        for (int s = 0; s < SEATS; s++) {
            if (taken[s]) continue;
            @SuppressWarnings("unchecked")
            EntityType<ResonantTriad> type = (EntityType<ResonantTriad>) getType();
            ResonantTriad m = type.create(level, EntitySpawnReason.NATURAL);
            if (m == null) return;
            Vec3 at = null;
            for (int tries = 0; tries < 12 && at == null; tries++) {
                double a = (s * 120.0 + tries * 37.0) * Mth.DEG_TO_RAD, r = tries < 6 ? RADIUS : 2.0;
                Vec3 spot = position().add(Math.cos(a) * r, (tries % 3) * 0.5, Math.sin(a) * r);
                m.snapTo(spot.x, spot.y, spot.z, getYRot(), 0.0F);
                if (level.noCollision(m)) at = spot;
            }
            if (at == null) continue;
            m.core().setLook(look().isEmpty() ? "base" : look(), true);
            m.link(triad, s, epoch);
            if (isPersistenceRequired()) m.setPersistenceRequired();       // as lasting as the one that made it
            level.addFreshEntity(m);
            kinScan = 0;
        }
    }

    /** A whole triad leaves together: when one despawns, the others go with it. */
    @Override
    public void checkDespawn() {
        super.checkDespawn();
        if (isRemoved() && level() instanceof ServerLevel level && formed && !broken) {
            for (ResonantTriad m : new ArrayList<>(kin(level))) if (!m.isRemoved()) m.discard();
        }
    }

    // ---- the shield ---------------------------------------------------------------------------------------------------

    /**
     * While the triad is whole, a member that isn't singing shrugs every hit off with a ping and a shimmer, and turns on
     * whoever struck it; damage nothing can stop (the void, a kill command) still lands. A vetoed "shield" lets the hit in.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        LivingEntity by = source.getEntity() instanceof LivingEntity l ? l : null;
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && shielded() && MechanicEvent.post(this, "shield", by)) {
            shrug(level, by);
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    /** A projectile that hits a shielded member bounces off it, as a blow does, without landing or piercing. */
    @Override
    public ProjectileDeflection deflection(Projectile projectile) {
        if (level().isClientSide()) return core().flag(FLAG_SHIELDED) ? BOUNCE : super.deflection(projectile);
        LivingEntity by = projectile.getOwner() instanceof LivingEntity l ? l : null;
        if (!shielded() || !MechanicEvent.post(this, "shield", by)) return super.deflection(projectile);
        shrug((ServerLevel) level(), by);
        return BOUNCE;
    }

    /** Back the way it came at a fifth of its speed and a little up, so it drops beside the fork. */
    private static final ProjectileDeflection BOUNCE = (projectile, by, random, scale) -> {
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.2).add(0.0, 0.15, 0.0));
        Push.sync(projectile);
    };

    /** The ping and shimmer of a shrugged-off hit (at most every 4 ticks), and it turns on whoever struck it. */
    private void shrug(ServerLevel level, @Nullable LivingEntity by) {
        shrugs++;
        if (tickCount - lastShrug >= 4) {
            lastShrug = tickCount;
            level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.RESONANT_TRIAD_SHRUG.get(), getSoundSource(), 1.0F, 1.0F);
            for (int i = 0; i < 8; i++) {
                double a = i * (Math.PI * 2 / 8), r = getBbWidth() * 0.7;
                level.sendParticles(WHITE_MOTE, true, false, getX() + Math.cos(a) * r, getY(0.55), getZ() + Math.sin(a) * r, 1, 0.0, 0.1, 0.0, 0.0);
            }
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.6), getZ(), 3, 0.15, 0.2, 0.15, 0.02);
        }
        if (valid(by) && !valid(getTarget())) setTarget(by);
    }

    /** The singer dying breaks the chord for the two left (any member dying does): they crack and lose the shield. */
    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level && !isRemoved() && !dead) {
            if (formed && !broken) {
                for (ResonantTriad m : new ArrayList<>(kin(level))) m.breakChord(level, true);
            }
            broken = true;
            shownStage = STAGE_DIM;
            core().setStage(STAGE_DIM);
            core().setFlag(FLAG_SHIELDED, false);
            level.sendParticles(shards(), true, true, getX(), getY(0.5), getZ(), 20, 0.2, 0.3, 0.2, 0.12);
        }
        super.die(source);
    }

    // ---- the zap ------------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        getNavigation().stop();
        if (!core().in(Step.TELL) && untilZap > 0) untilZap--;       // the wait runs from the zap itself
        LivingEntity target = getTarget();
        if (!valid(target)) {
            target = null;
            for (ResonantTriad m : kin(level)) {                     // one's foe is the triad's foe
                if (valid(m.getTarget()) && distanceTo(m.getTarget()) <= getAttributeValue(Attributes.FOLLOW_RANGE)) {
                    target = m.getTarget();
                    setTarget(target);
                    break;
                }
            }
        }
        if (core().in(Step.TELL)) {
            crackle(level);
            if (valid(core().target())) face(core().target().position().subtract(position()));
        } else if (core().in(Step.ZAP)) {
            if (core().elapsed() < 3 && valid(core().target())) arc(level, core().target());
        } else if (core().idle()) {
            if (untilZap <= 0 && valid(target) && MobCore.gap(this, target) <= ZAP_RANGE && hasLineOfSight(target)
                    && startPhase(Step.TELL, TELL_TICKS, target)) {
                playSound(ModSounds.RESONANT_TRIAD_CRACKLE.get(), 1.0F, rose() ? 1.1F : 1.0F);
            }
        }
        if (held) setDeltaMovement(Vec3.ZERO);
        else steer(level, valid(target) ? target : null);
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) zap(level, core().target());
        else core().end();
    }

    /** The zap: an arc of light to the target, its attack through armour and without knockback, if still in reach and sight. */
    private void zap(ServerLevel level, @Nullable LivingEntity target) {
        untilZap = ZAP_EVERY;
        if (!isAlive() || !startPhase(Step.ZAP, ZAP_TICKS, target)) {
            core().end();
            return;
        }
        playSound(ModSounds.RESONANT_TRIAD_ZAP.get(), 1.0F, rose() ? 1.1F : 1.0F);
        if (!valid(target) || MobCore.gap(this, target) > ZAP_RANGE || !hasLineOfSight(target)) return;
        arc(level, target);
        if (!MechanicEvent.post(this, "zap_hit", target)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * ZAP;
        if (AstralAugur.GroundMarks.hurtInPlace(level, target, damageSources().mobAttack(this), damage)) {
            zaps++;
            setLastHurtMob(target);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, target.getX(), target.getY(0.6), target.getZ(), 10, 0.2, 0.25, 0.2, 0.15);
        }
    }

    /** The crackle: sparks jump between the tine tips. */
    private void crackle(ServerLevel level) {
        Vec3 side = left(yBodyRot).scale(TIPS_SIDE * getScale());
        Vec3 top = position().add(0.0, TIPS_UP * getScale(), 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, top.x, top.y, top.z, 2, Math.abs(side.x), 0.05, Math.abs(side.z), 0.05);
        level.sendParticles(arcMote(), true, false, top.x, top.y, top.z, 2, Math.abs(side.x), 0.06, Math.abs(side.z), 0.0);
    }

    /** A jagged line of violet light from the tines to the target's middle, drawn afresh each tick. */
    private void arc(ServerLevel level, LivingEntity target) {
        Vec3 from = position().add(0.0, TIPS_UP * getScale(), 0.0);
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
        Vec3 d = to.subtract(from);
        int n = Math.max(2, (int) Math.ceil(d.length() / 0.25));
        for (int i = 0; i <= n; i++) {
            double k = (double) i / n, j = Math.sin(k * Math.PI) * 0.18;
            Vec3 p = from.add(d.scale(k)).add((random.nextDouble() - 0.5) * j, (random.nextDouble() - 0.5) * j, (random.nextDouble() - 0.5) * j);
            level.sendParticles(arcMote(), true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof ResonantTriad) && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---- hovering in a triangle ---------------------------------------------------------------------------------------

    /**
     * Its place in the triangle: {@link #RADIUS} out from the triad's middle at its seat's angle (the triangle turns
     * slowly). With a foe the middle keeps {@link #HOLD} blocks from it at its chest height; with none it drifts round
     * the triad's home, over the ground where there is any.
     */
    private void steer(ServerLevel level, @Nullable LivingEntity foe) {
        List<ResonantTriad> others = kin(level);
        Vec3 middle = position();
        for (ResonantTriad m : others) middle = middle.add(m.position());
        middle = middle.scale(1.0 / (others.size() + 1));
        if (home == null) home = middle;
        long now = level.getGameTime();
        Vec3 want;
        if (foe != null) {
            Vec3 away = middle.subtract(foe.position()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            want = foe.position().add(away.scale(HOLD)).add(0.0, foe.getBbHeight() * 0.5 - getBbHeight() * 0.5, 0.0);
            home = null;
        } else {
            double a = now * 0.004 + (triad.getLeastSignificantBits() & 0xFF) * 0.1;
            want = home.add(Math.cos(a) * WANDER, 0.0, Math.sin(a) * WANDER);
            double ground = groundBelow(level, want.x, want.y + 1.0, want.z);
            if (!Double.isNaN(ground)) want = new Vec3(want.x, ground + HOVER, want.z);
        }
        if (!others.isEmpty()) {
            double a = (seat * 120.0 + now * 0.8) * Mth.DEG_TO_RAD;
            want = want.add(Math.cos(a) * RADIUS, 0.0, Math.sin(a) * RADIUS);
        }
        Vec3 to = want.subtract(position());
        if (to.length() > SPEED) to = to.normalize().scale(SPEED);
        setDeltaMovement(getDeltaMovement().lerp(to, 0.2));
        if (foe != null) face(foe.position().subtract(position()));
        else face(position().subtract(middle).lengthSqr() < 1.0E-4 ? getDeltaMovement() : position().subtract(middle));
    }

    /** The top of the first solid block below {@code y} within 10 blocks, or NaN over open air (the void). */
    private double groundBelow(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < GROUND_SEARCH; i++, pos.move(0, -1, 0)) {
            if (!level.getFluidState(pos).isEmpty()) return pos.getY() + level.getFluidState(pos).getHeight(level, pos);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return pos.getY() + shape.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** A body with yaw {@code yawDeg}'s left, flat. */
    private static Vec3 left(float yawDeg) {
        float r = yawDeg * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(r), 0.0, Mth.sin(r));
    }

    private boolean rose() {
        return "rose_triad".equals(look());
    }

    private DustParticleOptions arcMote() {
        return rose() ? ROSE_ARC : VIOLET_ARC;
    }

    private BlockParticleOption shards() {
        return rose() ? ROSE_SHARDS : VIOLET_SHARDS;
    }

    // ---- saving -------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Triad", triad.toString());
        output.putInt("Seat", seat);
        output.putLong("Epoch", epoch);
        output.putBoolean("Formed", formed);
        output.putBoolean("Broken", broken);
        output.putBoolean("Founder", founder);
        output.putInt("ZapWait", untilZap);
    }

    /** It keeps its triad, seat, song, whether the three were together and a broken chord; a reload mid-zap ends it. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            triad = UUID.fromString(input.getStringOr("Triad", ""));
        } catch (IllegalArgumentException bad) {
            triad = UUID.randomUUID();
        }
        seat = Math.clamp(input.getIntOr("Seat", 0), 0, SEATS - 1);
        epoch = input.getLongOr("Epoch", 0L);
        formed = input.getBooleanOr("Formed", false);
        broken = input.getBooleanOr("Broken", false);
        founder = input.getBooleanOr("Founder", false);
        untilZap = Math.clamp(input.getIntOr("ZapWait", 0), 0, ZAP_EVERY);
        kin.clear();
        kinScan = 0;
        missing = 0;
        shownStage = -1;
    }

    // ---- test hooks ---------------------------------------------------------------------------------------------------

    /** Its triad's id, its seat and the game time its song started. Public as test hooks. */
    public UUID triad() {
        return triad;
    }

    public int seat() {
        return seat;
    }

    public long epoch() {
        return epoch;
    }

    /** True once the three have been together; true once its chord broke. Public as test hooks. */
    public boolean formed() {
        return formed;
    }

    public boolean broken() {
        return broken;
    }

    /** The other living members it finds now. Public as a test hook. */
    public List<ResonantTriad> members() {
        return level() instanceof ServerLevel level ? List.copyOf(kin(level)) : List.of();
    }

    /** Sets the game time the song started (seat 0 sings from it). Public as a test and staging hook. */
    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    /** Zaps that landed and hits shrugged off, since it spawned. Public as test hooks. */
    public int zaps() {
        return zaps;
    }

    public int shrugs() {
        return shrugs;
    }

    /** Ticks before it may crackle again; sets them. Public as test hooks. */
    public int zapWait() {
        return untilZap;
    }

    public void setZapWait(int ticks) {
        untilZap = Math.max(0, ticks);
    }

    /** Holds it where it is (true) or lets it fly again; its zap and song work either way. Public as a test hook. */
    public void hold(boolean on) {
        held = on;
        if (on) setDeltaMovement(Vec3.ZERO);
    }

    // ---- sounds -------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().stage() == STAGE_SING ? null : ModSounds.RESONANT_TRIAD_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.RESONANT_TRIAD_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.RESONANT_TRIAD_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return (rose() ? 1.1F : 1.0F) + random.nextFloat() * 0.1F;
    }
}
