package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Recurrent: a tall robed figure of the End barrens with a cracked clock-glass face, trailed by faint afterimages. Its
 * blow: for 8 ticks it raises its arm while its clock hands whirl with a winding click and motes rise (it turns after
 * its target for the first half, then holds), then it strikes down for its attack at a target still within 1.2 blocks
 * (across or up and down) in front of it, without knockback.
 *
 * <p>It keeps the last 60 ticks of where it stood and how much health it had. A blow that would kill it winds it back
 * instead: it snaps back to where it stood 60 ticks before, with the health it had then, leaving a trail of reversed
 * motes along the way it came and a reversed chime, and stands dazed for 20 ticks. It does this once in 60 ticks: a
 * kill within 60 ticks of a rewind is final. While it can rewind its face glows; for the 60 ticks after a rewind, and
 * for good once its anchor is broken, the face is dark.
 *
 * <p>Its time anchor is a small floating hourglass with 4 health that it unfolds the first time it fights. The anchor
 * floats behind the Recurrent's back, on the far side from its foe, and every 120 ticks drifts out to one side of it for
 * 40 ticks, low enough for a sword; it is never more than 6 blocks from it (a rewind drags it along). Break the anchor
 * and the next kill is final. A kill by something that ignores invulnerability (the void, a kill command) is always
 * final.
 *
 * <p>The anchor is this same entity type in another role, kept in the synced flags ({@link #FLAG_ANCHOR}): small, weightless,
 * with no goals, no loot and no experience. It shatters (it is removed, it doesn't die) when it breaks, and goes when
 * its Recurrent does. The two find each other again by id after a load.
 */
public class Recurrent extends TeemingMonster {
    /** At rest (closing in), the wind-up, the blow, dazed after a rewind. */
    public enum Step implements Phase { IDLE, TELL, STRIKE, REWOUND }

    /** The wind-up: 8 ticks, its arm up and its clock hands whirling. */
    public static final int TELL_TICKS = 8;
    /** It turns after its target for this much of the wind-up, then holds its aim. */
    public static final int AIM_TICKS = 4;
    /** The blow lands as this starts; the rest is the arm coming back. */
    public static final int STRIKE_TICKS = 8;
    /** Ticks from one wind-up to the next, at the soonest. */
    public static final int SWING_EVERY = 30;
    /** The blow's damage, times the attack. */
    public static final float STRIKE = 1.0F;
    /** Blocks between hitboxes (across or up and down): this close it winds up; this close when it strikes, the blow lands. */
    public static final double START_REACH = 0.6;
    public static final double STRIKE_REACH = 1.2;
    /** Degrees in front of it the blow covers. */
    public static final float STRIKE_ARC = 120.0F;
    /** Degrees a tick it turns after its target while it aims. */
    public static final float TURN = 20.0F;

    /** A killing blow sends it back to where it stood, with the health it had, this many ticks before. */
    public static final int REWIND_BACK = 60;
    /** After a rewind, a kill within this many ticks is final. */
    public static final int REWIND_EVERY = 60;
    /** Ticks of its own past it keeps: one more than it winds back. */
    public static final int HISTORY = REWIND_BACK + 1;
    /** It stands dazed this long after a rewind: a second's opening to close in for the final kill. */
    public static final int REWOUND_TICKS = 20;

    /** The anchor's health and how far it may float from its Recurrent, centre to centre. */
    public static final float ANCHOR_HEALTH = 4.0F;
    public static final double LEASH = 6.0;
    /** The anchor's hitbox. */
    public static final float ANCHOR_WIDTH = 0.45F;
    public static final float ANCHOR_HEIGHT = 0.7F;
    /** Blocks a tick the anchor drifts. */
    public static final double ANCHOR_SPEED = 0.25;
    /** Its cycle in a fight: this long behind the Recurrent's back, then this long out to one side (the sides take turns). */
    public static final int GUARD_TICKS = 80;
    public static final int OUT_TICKS = 40;
    public static final int CYCLE = GUARD_TICKS + OUT_TICKS;
    /** Behind its back: this far back and this high above its feet. */
    public static final double GUARD_BACK = 1.4;
    public static final double GUARD_UP = 1.4;
    /** Out to its side: this far across and this high above its feet, in reach of a sword. */
    public static final double OUT_SIDE = 3.0;
    public static final double OUT_UP = 0.8;
    /** With no foe it circles the Recurrent this far out and this high. */
    public static final double IDLE_RADIUS = 1.6;
    public static final double IDLE_UP = 1.5;
    /** Ticks either one waits for a missing partner (unloaded, or gone) before giving it up. */
    public static final int PARTNER_LOST = 100;

    public static final double SPEED = 0.25;
    public static final int XP = 12;

    /** Synced flag bits: this is an anchor; its anchor is broken (more cracks on its face); a rewind's trail is drawn. */
    public static final int FLAG_ANCHOR = 0;
    public static final int FLAG_BROKEN = 1;
    public static final int FLAG_TRAIL = 2;
    /** Glow stages: the face lit (it can rewind), the face dark (it can't). */
    public static final int STAGE_LIT = 0;
    public static final int STAGE_DARK = 1;

    private static final BlockParticleOption GRAINS = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
    private static final Telegraph TELL = Telegraph.particles(ParticleTypes.REVERSE_PORTAL, 10, 0.3, 0.75).withSpeed(0.03)
            .withSound(ModSounds.RECURRENT_TELL.get(), 1.0F, 1.0F);

    // its past: game time, place and health for each of the last HISTORY ticks, in a ring by game time
    private final long[] pastAt = new long[HISTORY];
    private final double[] pastX = new double[HISTORY];
    private final double[] pastY = new double[HISTORY];
    private final double[] pastZ = new double[HISTORY];
    private final float[] pastHealth = new float[HISTORY];
    /** Game time of its last rewind. */
    private long rewoundAt = Long.MIN_VALUE / 2;
    private boolean anchorBroken;
    private @Nullable UUID anchorId;
    private @Nullable Recurrent anchorRef;
    private int partnerMissing;
    private int untilSwing;
    private int rewinds;
    private int strikes;
    // the anchor's own: its Recurrent, and the tick of its cycle
    private @Nullable UUID ownerId;
    private @Nullable Recurrent ownerRef;
    private int drift;
    /** The role clients last sized it for; null until the first synced update. */
    private @Nullable Boolean sizedAnchor;

    public Recurrent(EntityType<? extends Recurrent> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
        java.util.Arrays.fill(pastAt, Long.MIN_VALUE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("recurrent", SPEED);
    }

    @Override
    public int visualStages() {
        return 2;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this) {
            @Override
            public boolean canUse() {
                return !anchor() && super.canUse();
            }
        });
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return !anchor() && core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !anchor() && core().idle() && super.canContinueToUse();
            }

            /** It closes in only: every blow is the wind-up and the strike, started from its AI step. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return !anchor() && core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !anchor() && core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return !anchor() && core().idle() && super.canUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !anchor() && core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !anchor() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return !anchor() && super.canUse();
            }
        });
    }

    // ---- which one it is ---------------------------------------------------------------------------------------------

    /** True for a time anchor, false for a Recurrent. Both sides. */
    public boolean anchor() {
        return core() != null && core().flag(FLAG_ANCHOR);           // null while the Entity constructor runs
    }

    /** Whether a set of synced flags is an anchor's: for the model, which sees only the flags. */
    public static boolean anchorIn(int flags) {
        return (flags & 1 << FLAG_ANCHOR) != 0;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (anchor()) return EntityDimensions.scalable(ANCHOR_WIDTH, ANCHOR_HEIGHT).withEyeHeight(ANCHOR_HEIGHT * 0.5F);
        return super.getDefaultDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (core() == null) return;
        boolean a = anchor();
        if (sizedAnchor == null || sizedAnchor != a) {
            sizedAnchor = a;
            refreshDimensions();
        }
    }

    // ---- its past and the rewind -------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        if (anchor()) {
            anchorTick(level);
            return;
        }
        if (!isAlive()) return;
        remember(level.getGameTime());
        keeperTick(level);
        int can = canRewindNow(level.getGameTime()) ? 1 : 0;
        if (can != couldRewind) {                                   // the face follows the window as it shuts and opens
            couldRewind = can;
            core().setStage(can == 1 ? STAGE_LIT : STAGE_DARK);
        }
        if (core().flag(FLAG_TRAIL) && !core().in(Step.REWOUND)) showTrail(null);   // a trail outlives nothing that ended its daze
    }

    /** Whether it could wind back on its last tick (1) or not (0); -1 before its first tick, so a loaded one sets its face. */
    private int couldRewind = -1;

    /** Writes where it stands and its health now into its past, at game time {@code now}. */
    private void remember(long now) {
        int i = (int) Math.floorMod(now, (long) HISTORY);
        pastAt[i] = now;
        pastX[i] = getX();
        pastY[i] = getY();
        pastZ[i] = getZ();
        pastHealth[i] = getHealth();
    }

    /**
     * The slot of its past it would wind back to at game time {@code now}: the latest at or before {@code now - 60}, or,
     * when it remembers nothing that old, the oldest it has. -1 when it remembers nothing.
     */
    private int pastSlot(long now) {
        long want = now - REWIND_BACK;
        int best = -1, oldest = -1;
        for (int i = 0; i < HISTORY; i++) {
            if (pastAt[i] == Long.MIN_VALUE) continue;
            if (pastAt[i] <= want && (best < 0 || pastAt[i] > pastAt[best])) best = i;
            if (oldest < 0 || pastAt[i] < pastAt[oldest]) oldest = i;
        }
        return best >= 0 ? best : oldest;
    }

    /** True while a killing blow would wind it back (its anchor whole, its last rewind 60 ticks or more ago). */
    private boolean canRewindNow(long now) {
        return !anchorBroken && now - rewoundAt >= REWIND_EVERY;
    }

    /**
     * A blow that would kill it winds it back instead, if it can; a kill it can't undo is final, and its anchor (if any)
     * shatters with it. An anchor that would die breaks instead.
     */
    @Override
    public void die(DamageSource source) {
        if (!(level() instanceof ServerLevel level) || isRemoved() || dead) {
            super.die(source);
            return;
        }
        if (anchor()) {
            breakAnchor(level, source);
            return;
        }
        if (rewind(level, source)) return;
        unravel(level);
        super.die(source);
    }

    /**
     * The rewind: back to where it stood 60 ticks ago (onto safe ground there, or where it is if there is none), with
     * the health it had then. A vetoed "rewind" is a kill. Returns true if it wound back.
     */
    private boolean rewind(ServerLevel level, DamageSource source) {
        long now = level.getGameTime();
        if (!canRewindNow(now) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        int slot = pastSlot(now);
        if (slot < 0) return false;
        LivingEntity by = source.getEntity() instanceof LivingEntity l ? l : null;
        if (!MechanicEvent.post(this, "rewind", by)) return false;
        Vec3 from = position();
        Vec3 back = new Vec3(pastX[slot], pastY[slot], pastZ[slot]);
        float health = Math.max(1.0F, Math.min(getMaxHealth(), pastHealth[slot]));
        reverseTrail(level, now, slot, from);
        setHealth(health);
        rewoundAt = now;
        rewinds++;
        core().setStage(STAGE_DARK);
        clearFire();
        Vec3 to = back.distanceToSqr(from) < 1.0E-6 ? null : landing(level, back);
        if (to != null) Riftjackal.SafeTeleport.InLevel.move(level, this, to);
        core().start(Step.REWOUND, REWOUND_TICKS, valid(by) ? by : getTarget());
        showTrail(from.subtract(position()));
        if (valid(by) && getTarget() == null) setTarget(by);
        Vec3 here = position();
        level.playSound(null, from.x, from.y + 1.0, from.z, ModSounds.RECURRENT_REWIND.get(), getSoundSource(), 1.2F, 1.0F);
        level.playSound(null, here.x, here.y + 1.0, here.z, ModSounds.RECURRENT_REWIND.get(), getSoundSource(), 1.0F, 0.9F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, here.x, here.y + 1.0, here.z, 40, 0.3, 0.6, 0.3, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, true, true, here.x, here.y + 1.6, here.z, 6, 0.2, 0.2, 0.2, 0.02);
        return true;
    }

    /** Where it lands, winding back to {@code back}: there if its body fits, else safe ground in that column, else null. */
    private @Nullable Vec3 landing(ServerLevel level, Vec3 back) {
        AABB box = getBoundingBox().move(back.subtract(position()));
        if (level.noCollision(this, box) && !level.containsAnyLiquid(box)) return back;
        return Riftjackal.SafeTeleport.InLevel.spot(level, this, back.x, back.y, back.z);
    }

    /** Reversed motes along the way it came, from where it died back to where it lands, and a puff where it died. */
    private void reverseTrail(ServerLevel level, long now, int slot, Vec3 from) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, from.x, from.y + 1.0, from.z, 30, 0.3, 0.6, 0.3, 0.1);
        level.sendParticles(GRAINS, true, true, from.x, from.y + 1.0, from.z, 12, 0.3, 0.5, 0.3, 0.0);
        Vec3 prev = from;
        for (int k = 0; k < HISTORY && now - k >= pastAt[slot]; k += 2) {
            long t = now - k;
            int i = (int) Math.floorMod(t, (long) HISTORY);
            if (pastAt[i] != t) continue;
            Vec3 p = new Vec3(pastX[i], pastY[i], pastZ[i]);
            Vec3 d = p.subtract(prev);                                     // each mote drifts on toward the older place
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, p.x, p.y + 1.0, p.z, 3, 0.15, 0.4, 0.15, 0.02);
            level.sendParticles(ParticleTypes.END_ROD, true, true, p.x, p.y + 1.2, p.z, 0, d.x, 0.0, d.z, 0.4);
            prev = p;
        }
    }

    /** Puts the way back to where it died into the synced flags (for the model's afterimages), or clears it (null). */
    private void showTrail(@Nullable Vec3 toDeath) {
        int bits = toDeath == null ? 0 : Trail.encode(toDeath.x, toDeath.y, toDeath.z);
        for (int bit = Trail.X_BIT; bit < Trail.END_BIT; bit++) core().setFlag(bit, (bits >> bit & 1) != 0);
        core().setFlag(FLAG_TRAIL, toDeath != null);
    }

    /** A final death: its afterimages scatter into sand and its anchor shatters with it. */
    private void unravel(ServerLevel level) {
        if (!core().idle()) core().end();
        showTrail(null);
        core().setStage(STAGE_DARK);
        level.sendParticles(GRAINS, true, true, getX(), getY(1.0), getZ(), 30, 0.3, 0.6, 0.3, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, getX(), getY(0.6), getZ(), 30, 0.4, 0.7, 0.4, 0.05);
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.RECURRENT_UNRAVEL.get(), getSoundSource(), 1.2F, 1.0F);
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.RECURRENT_DEATH.get(), getSoundSource(), getSoundVolume(), getVoicePitch());
        Recurrent a = anchorOf(level);
        if (a != null) a.shatter(level);
        anchorId = null;
        anchorRef = null;
        java.util.Arrays.fill(pastAt, Long.MIN_VALUE);
    }

    // ---- the anchor: the Recurrent's side ------------------------------------------------------------------------------

    /** It unfolds its anchor the first time it fights (a foe, or a blow), unless it has broken; a lost one it unfolds again. */
    private void keeperTick(ServerLevel level) {
        if (anchorOf(level) != null) {
            partnerMissing = 0;
            return;
        }
        if (anchorId != null) {
            if (++partnerMissing >= PARTNER_LOST) {                         // gone without breaking (unloaded): it may make another
                anchorId = null;
                partnerMissing = 0;
            }
            return;
        }
        if (!anchorBroken && (valid(getTarget()) || getLastHurtByMob() != null)) summonAnchor();
    }

    /**
     * Unfolds its anchor from its chest: a new entity of its own type, flagged as the anchor, in its look. Returns the
     * anchor (the one it already has, if any), or null when it can't have one (broken, vetoed, or this is an anchor).
     * Public as a test hook.
     */
    public @Nullable Recurrent summonAnchor() {
        if (!(level() instanceof ServerLevel level) || anchor() || anchorBroken || !isAlive()) return null;
        Recurrent have = anchorOf(level);
        if (have != null) return have;
        if (!MechanicEvent.post(this, "anchor", getTarget())) return null;
        @SuppressWarnings("unchecked")
        EntityType<Recurrent> type = (EntityType<Recurrent>) getType();
        Recurrent a = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (a == null) return null;
        a.becomeAnchor(this);
        Vec3 at = position().add(0.0, getBbHeight() * 0.45, 0.0);
        a.snapTo(at.x, at.y, at.z, getYRot(), 0.0F);
        if (!level.noCollision(a)) a.snapTo(getX(), getY(), getZ(), getYRot(), 0.0F);
        if (!level.addFreshEntity(a)) return null;
        anchorId = a.getUUID();
        anchorRef = a;
        partnerMissing = 0;
        level.playSound(null, at.x, at.y, at.z, ModSounds.RECURRENT_SUMMON.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, at.x, at.y, at.z, 20, 0.2, 0.3, 0.2, 0.08);
        return a;
    }

    /** Its living anchor, found again by id after a load (server only); null for none, or for an anchor itself. */
    public @Nullable Recurrent anchorOf(ServerLevel level) {
        if (anchor()) return null;
        if (anchorRef != null && anchorRef.isAlive() && !anchorRef.isRemoved()) return anchorRef;
        anchorRef = null;
        if (anchorId != null && level.getEntity(anchorId) instanceof Recurrent r && r.anchor() && r.isAlive() && !r.isRemoved()) anchorRef = r;
        return anchorRef;
    }

    /** Where its anchor drifts toward on tick {@code drift} of its cycle. */
    private Vec3 anchorSpot(int drift) {
        Vec3 base = position();
        LivingEntity foe = getTarget();
        if (!valid(foe)) {
            double a = drift * 0.03;
            return base.add(Math.cos(a) * IDLE_RADIUS, IDLE_UP, Math.sin(a) * IDLE_RADIUS);
        }
        Vec3 away = base.subtract(foe.position()).multiply(1.0, 0.0, 1.0);
        away = away.lengthSqr() < 1.0E-4 ? forward(getYRot()).scale(-1.0) : away.normalize();
        if (Math.floorMod(drift, CYCLE) < GUARD_TICKS) return base.add(away.scale(GUARD_BACK)).add(0.0, GUARD_UP, 0.0);
        int side = Math.floorMod(drift / CYCLE, 2) == 0 ? 1 : -1;
        return base.add(new Vec3(-away.z, 0.0, away.x).scale(side * OUT_SIDE)).add(away.scale(0.3)).add(0.0, OUT_UP, 0.0);
    }

    /** True once its anchor is broken: its next kill is final. Both sides read the flag; the server keeps the truth. */
    public boolean anchorBroken() {
        if (anchor()) return false;
        return level().isClientSide() ? core().flag(FLAG_BROKEN) : anchorBroken;
    }

    // ---- the anchor: its own side ------------------------------------------------------------------------------------

    /** Becomes {@code owner}'s anchor: the flag, its look, 4 health, no armour, no weight, no knockback, no experience. */
    private void becomeAnchor(Recurrent owner) {
        core().setFlag(FLAG_ANCHOR, true);
        core().setLook(owner.look().isEmpty() ? "base" : owner.look(), false);
        setBase(Attributes.MAX_HEALTH, ANCHOR_HEALTH);
        setBase(Attributes.ARMOR, 0.0);
        setBase(Attributes.KNOCKBACK_RESISTANCE, 1.0);
        setHealth(ANCHOR_HEALTH);
        setNoGravity(true);
        xpReward = 0;
        ownerId = owner.getUUID();
        ownerRef = owner;
        refreshDimensions();
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    /** An anchor's living Recurrent, found again by id after a load (server only); null for none. */
    public @Nullable Recurrent ownerOf(ServerLevel level) {
        if (!anchor()) return null;
        if (ownerRef != null && ownerRef.isAlive() && !ownerRef.isRemoved()) return ownerRef;
        ownerRef = null;
        if (ownerId != null && level.getEntity(ownerId) instanceof Recurrent r && !r.anchor() && r.isAlive() && !r.isRemoved()) ownerRef = r;
        return ownerRef;
    }

    /**
     * The anchor's tick: it drifts toward its spot behind or beside its Recurrent, and is pulled back to 6 blocks when it
     * is farther. Without its Recurrent (gone, or no longer claiming it) it goes, quietly, after a while.
     */
    private void anchorTick(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        Recurrent owner = ownerOf(level);
        if (owner == null) {
            if (++partnerMissing >= PARTNER_LOST) discard();
            return;
        }
        partnerMissing = 0;
        if (owner.anchorId == null || !owner.anchorId.equals(getUUID())) {
            discard();
            return;
        }
        if (!isAlive()) return;
        drift++;
        Vec3 step = owner.anchorSpot(drift).subtract(position());
        if (step.length() > ANCHOR_SPEED) step = step.normalize().scale(ANCHOR_SPEED);
        move(MoverType.SELF, step);
        setDeltaMovement(Vec3.ZERO);
        leash(level, owner);
        float spin = drift * 3.0F;
        setYRot(spin);
        yBodyRot = spin;
        yHeadRot = spin;
        if (drift % 6 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(0.5), getZ(), 1, 0.1, 0.15, 0.1, 0.01);
    }

    /** No farther than 6 blocks from {@code owner}: one too far is set back on the line between them, at 6. */
    private void leash(ServerLevel level, Recurrent owner) {
        Vec3 off = position().subtract(owner.position());
        if (off.length() <= LEASH) return;
        Vec3 to = owner.position().add(off.normalize().scale(LEASH - 0.05));
        AABB box = getBoundingBox().move(to.subtract(position()));
        if (!level.noCollision(this, box)) to = owner.position().add(0.0, owner.getBbHeight() * 0.45, 0.0);
        setPos(to.x, to.y, to.z);
    }

    /** The anchor breaks: its Recurrent's next kill is final. A vetoed "anchor_break" leaves it whole at full health. */
    private void breakAnchor(ServerLevel level, DamageSource source) {
        LivingEntity by = source.getEntity() instanceof LivingEntity l ? l : null;
        if (!MechanicEvent.post(this, "anchor_break", by)) {
            setHealth(ANCHOR_HEALTH);
            return;
        }
        Recurrent owner = ownerOf(level);
        if (owner != null) owner.anchorLost(level, by);
        shatter(level);
    }

    /** Its anchor broke: no more rewinds, its face goes dark and cracks further, and it turns on whoever broke it. */
    private void anchorLost(ServerLevel level, @Nullable LivingEntity by) {
        anchorBroken = true;
        anchorId = null;
        anchorRef = null;
        core().setFlag(FLAG_BROKEN, true);
        core().setStage(STAGE_DARK);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, getX(), getY(0.85), getZ(), 12, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, getX(), getY(0.85), getZ(), ModSounds.RECURRENT_ANCHOR_BREAK.get(), getSoundSource(), 0.8F, 0.7F);
        if (valid(by) && !valid(getTarget())) setTarget(by);
    }

    /** The anchor shatters into glass, sand and motes, and is gone (no death, no loot). */
    private void shatter(ServerLevel level) {
        double y = getY(0.5);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), true, true, getX(), y, getZ(), 24, 0.2, 0.25, 0.2, 0.1);
        level.sendParticles(GRAINS, true, true, getX(), y, getZ(), 16, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), y, getZ(), 8, 0.15, 0.15, 0.15, 0.05);
        level.playSound(null, getX(), y, getZ(), ModSounds.RECURRENT_ANCHOR_BREAK.get(), getSoundSource(), 1.2F, 1.0F);
        discard();
    }

    /** A blow to the anchor turns its Recurrent on whoever struck it. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && anchor() && !isRemoved() && source.getEntity() instanceof LivingEntity by) {
            Recurrent owner = ownerOf(level);
            if (owner != null && owner.valid(by) && !owner.valid(owner.getTarget())) owner.setTarget(by);
        }
        return hurt;
    }

    /** It goes with its Recurrent: a Recurrent removed for good (despawned, killed, discarded) takes its anchor along. */
    @Override
    public void onRemoval(RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy() && !anchor() && anchorRef != null && !anchorRef.isRemoved()) anchorRef.discard();
        super.onRemoval(reason);
    }

    /** An anchor stays as long as its Recurrent does: it never despawns on its own. */
    @Override
    public boolean requiresCustomPersistence() {
        return anchor() || super.requiresCustomPersistence();
    }

    @Override
    public boolean isPushable() {
        return !anchor() && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        if (!anchor()) super.pushEntities();
    }

    @Override
    public boolean canBreatheUnderwater() {
        return anchor() || super.canBreatheUnderwater();
    }

    // ---- the blow ------------------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof Recurrent) && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** True when {@code target} stands inside the arc its blow covers. */
    private boolean inFront(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return true;
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.degreesDifferenceAbs(toward, getYRot()) <= STRIKE_ARC / 2.0F;
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
        if (core() != null && (!core().idle() || anchor())) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (anchor()) return;
        super.customServerAiStep(level);
        if (untilSwing > 0) untilSwing--;
        if (!core().idle()) {
            holdStill();
            if (core().in(Step.TELL)) tellTick();
            else if (core().in(Step.REWOUND) && core().elapsed() % 2 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(0.6), getZ(), 3, 0.3, 0.5, 0.3, 0.05);
            }
            return;
        }
        LivingEntity target = getTarget();
        if (untilSwing == 0 && valid(target) && MobCore.gap(this, target) <= START_REACH && getSensing().hasLineOfSight(target)) tell(target);
    }

    /** Winds up at {@code target} for {@link #TELL_TICKS}; the strike follows. Returns false if refused. Public as a test hook. */
    public boolean tell(@Nullable LivingEntity target) {
        if (anchor() || !core().idle() || !(level() instanceof ServerLevel) || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        untilSwing = SWING_EVERY;
        holdStill();
        if (target != null) face(target, 360.0F);
        TELL.play(this);
        return true;
    }

    /** The wind-up: it turns after its target for the first half, and motes rise from its raised hand. */
    private void tellTick() {
        LivingEntity target = core().target();
        if (core().elapsed() < AIM_TICKS && valid(target)) face(target, TURN);
        if (core().elapsed() % 2 == 0 && level() instanceof ServerLevel level) {
            Vec3 hand = position().add(left(getYRot()).scale(-0.35)).add(0.0, getBbHeight() * 1.05, 0.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, hand.x, hand.y, hand.z, 3, 0.08, 0.1, 0.08, 0.02);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, getX(), getY(0.85), getZ(), 1, 0.15, 0.1, 0.15, 0.02);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            strike(level, core().target());
        } else {
            if (ended == Step.REWOUND) showTrail(null);
            core().end();
        }
    }

    /** The strike: its attack to a target still in reach (across or up and down), in front and in sight, without knockback. */
    private void strike(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.STRIKE, STRIKE_TICKS, target)) {
            core().end();
            return;
        }
        level.playSound(null, getX(), getY(0.7), getZ(), ModSounds.RECURRENT_STRIKE.get(), getSoundSource(), 1.0F, 1.0F);
        if (!valid(target) || MobCore.gap(this, target) > STRIKE_REACH || !inFront(target) || !hasLineOfSight(target)) return;
        if (!MechanicEvent.post(this, "strike_hit", target)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE;
        if (AstralAugur.GroundMarks.hurtInPlace(level, target, damageSources().mobAttack(this), damage)) {
            strikes++;
            setLastHurtMob(target);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
        }
    }

    private void holdStill() {
        getNavigation().stop();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0.0, v.y, 0.0);
    }

    /** The way a body with yaw {@code yawDeg} faces, flat. */
    private static Vec3 forward(float yawDeg) {
        float r = yawDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
    }

    /** A body with yaw {@code yawDeg}'s left, flat. */
    private static Vec3 left(float yawDeg) {
        float r = yawDeg * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(r), 0.0, Mth.sin(r));
    }

    /** The box the renderer culls it by: while a rewind's trail is drawn it reaches back to where it died. */
    @Override
    public AABB getInterpolatedBoundingBox(float partialTick) {
        AABB box = super.getInterpolatedBoundingBox(partialTick);
        if (core() == null || !core().flag(FLAG_TRAIL)) return box;
        int flags = core().flags();
        return box.minmax(box.move(Trail.x(flags), Trail.y(flags), Trail.z(flags)));
    }

    // ---- test hooks ----------------------------------------------------------------------------------------------------

    /** Times it wound back. Public as a test hook. */
    public int rewinds() {
        return rewinds;
    }

    /** Blows that landed. Public as a test hook. */
    public int strikes() {
        return strikes;
    }

    /** True while a killing blow would wind it back. Server side. Public as a test hook. */
    public boolean canRewind() {
        return !anchor() && canRewindNow(level().getGameTime());
    }

    /** Ticks until it may wind up again. Public as a test hook. */
    public int untilSwing() {
        return untilSwing;
    }

    /** Lets it wind up again at once. Public as a test hook. */
    public void resetSwing() {
        untilSwing = 0;
    }

    /** The anchor's tick of its cycle. Public as a test hook. */
    public int drift() {
        return drift;
    }

    /** Sets the anchor's tick of its cycle. Public as a test hook. */
    public void setDrift(int ticks) {
        drift = ticks;
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Anchor", anchor());
        if (anchor()) {
            if (ownerId != null) output.putString("Owner", ownerId.toString());
            output.putInt("Drift", drift);
            return;
        }
        if (anchorId != null) output.putString("AnchorId", anchorId.toString());
        output.putBoolean("AnchorBroken", anchorBroken);
        output.putLong("RewoundAt", rewoundAt);
        output.putInt("SwingWait", untilSwing);
        // its past, relative to where it stands now, and the game time it was saved at
        long now = level().getGameTime();
        int[] past = new int[HISTORY * 5];
        int n = 0;
        for (int i = 0; i < HISTORY; i++) {
            if (pastAt[i] == Long.MIN_VALUE || now - pastAt[i] > Integer.MAX_VALUE) continue;
            past[n++] = (int) (now - pastAt[i]);
            past[n++] = Float.floatToIntBits((float) (pastX[i] - getX()));
            past[n++] = Float.floatToIntBits((float) (pastY[i] - getY()));
            past[n++] = Float.floatToIntBits((float) (pastZ[i] - getZ()));
            past[n++] = Float.floatToIntBits(pastHealth[i]);
        }
        output.putIntArray("Past", java.util.Arrays.copyOf(past, n));
        output.putLong("PastAt", now);
    }

    /**
     * An anchor keeps its role, look, health (a saved attribute) and its Recurrent's id; a Recurrent keeps its anchor's
     * id, a broken anchor, the time of its last rewind and its past, and the wait to its next blow. A reload mid-blow or
     * mid-daze ends it.
     */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        boolean a = input.getBooleanOr("Anchor", false);
        core().setFlag(FLAG_ANCHOR, a);
        if (a) {
            ownerId = uuid(input.getStringOr("Owner", ""));
            drift = input.getIntOr("Drift", 0);
            xpReward = 0;
            setNoGravity(true);
            refreshDimensions();
            return;
        }
        anchorId = uuid(input.getStringOr("AnchorId", ""));
        anchorBroken = input.getBooleanOr("AnchorBroken", false);
        core().setFlag(FLAG_BROKEN, anchorBroken);
        rewoundAt = input.getLongOr("RewoundAt", Long.MIN_VALUE / 2);
        untilSwing = Math.clamp(input.getIntOr("SwingWait", 0), 0, SWING_EVERY);
        long savedAt = input.getLongOr("PastAt", 0L);
        int[] past = input.getIntArray("Past").orElse(new int[0]);
        java.util.Arrays.fill(pastAt, Long.MIN_VALUE);
        for (int k = 0; k + 4 < past.length; k += 5) {
            long at = savedAt - past[k];
            int i = (int) Math.floorMod(at, (long) HISTORY);
            pastAt[i] = at;
            pastX[i] = getX() + Float.intBitsToFloat(past[k + 1]);
            pastY[i] = getY() + Float.intBitsToFloat(past[k + 2]);
            pastZ[i] = getZ() + Float.intBitsToFloat(past[k + 3]);
            pastHealth[i] = Float.intBitsToFloat(past[k + 4]);
        }
        xpReward = XP;
    }

    private static @Nullable UUID uuid(String s) {
        try {
            return s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    // ---- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return !anchor() && core().idle() ? ModSounds.RECURRENT_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return anchor() ? ModSounds.RECURRENT_ANCHOR_HIT.get() : ModSounds.RECURRENT_HURT.get();
    }

    /** None here: a killing blow may yet be wound back, so a final death plays its own (and an anchor its shatter). */
    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }

    // ---- the trail in the flags (pure maths, no game types) ------------------------------------------------------------

    /**
     * The way back from where it landed to where it was killed, packed into the synced flags while it stands dazed, so the
     * model can lay its afterimages along it. Bits 3-9 and 10-16 hold x and z, bits 17-21 y, each in half blocks,
     * signed; x and z reach 31.5 blocks either way, y 7.5.
     */
    public static final class Trail {
        public static final int X_BIT = 3, Z_BIT = 10, Y_BIT = 17, END_BIT = 22;
        private static final int XZ_SIZE = 7, Y_SIZE = 5;
        public static final double STEP = 0.5;

        private Trail() {}

        private static int pack(double v, int size) {
            int max = (1 << size - 1) - 1;
            return Math.clamp(Math.round(v / STEP), -max, max) & (1 << size) - 1;
        }

        private static double unpack(int flags, int bit, int size) {
            int raw = flags >> bit & (1 << size) - 1;
            if (raw >= 1 << size - 1) raw -= 1 << size;
            return raw * STEP;
        }

        public static int encode(double dx, double dy, double dz) {
            return pack(dx, XZ_SIZE) << X_BIT | pack(dz, XZ_SIZE) << Z_BIT | pack(dy, Y_SIZE) << Y_BIT;
        }

        public static double x(int flags) {
            return unpack(flags, X_BIT, XZ_SIZE);
        }

        public static double z(int flags) {
            return unpack(flags, Z_BIT, XZ_SIZE);
        }

        public static double y(int flags) {
            return unpack(flags, Y_BIT, Y_SIZE);
        }
    }
}
