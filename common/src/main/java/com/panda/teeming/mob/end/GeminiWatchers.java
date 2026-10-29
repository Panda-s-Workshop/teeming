package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.MobCore;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gemini Watchers: two tall, slender sentinels of void crystal of the End barrens, mirror images of each other, each with
 * one great crystal eye set to one side of its face. They come as a pair, linked from the moment they spawn, and each
 * fights on its own with a staff: for 8 ticks it raises the staff with a lens-like slide while motes rise (it turns after
 * its target for the first half, then holds), then brings it down for its attack (armour counts) on a target still
 * within 1.2 blocks (across or up and down) in front of it, without knockback.
 *
 * <p>When one is killed while its twin stands within 12 blocks, it doesn't die: it falls as a crystal husk, lying on the
 * ground, dark, untouchable and still. Its twin turns to it at once, stands still and channels a revive beam from its eye
 * to the husk for 100 ticks: a beam of light, a rising hum every second, and the husk's cracks glowing brighter in four
 * steps. When the beam runs its course the fallen one stands again with half its health. The counter is the second
 * kill: the twin doesn't fight back while it channels, and killing it inside those 100 ticks ends both, for good. Sight
 * doesn't matter (the beam is the bond between them, not a ray), so stepping between them or behind a wall changes
 * nothing. A kill that ignores invulnerability (the void, a kill command) is always final, and a husk whose twin is gone
 * crumbles.
 *
 * <p>A pair spawned together links through the spawn data the first hands the second (the second wears the first's
 * look); one placed from a spawn egg links to the nearest unlinked watcher within 8 blocks, or comes alone. The first of a
 * pair has its eye on its left, its twin on its right ({@link #FLAG_MIRROR}). Both find each other again by id after a
 * load, and a revive in progress carries on where it was.
 */
public class GeminiWatchers extends TeemingMonster {
    /** At rest (closing in), the staff's wind-up, the blow, channelling a revive, lying as a husk, standing up again. */
    public enum Step implements Phase { IDLE, TELL, STRIKE, CHANNEL, HUSK, RISE }

    /** The staff's wind-up: 8 ticks, the staff raised high. */
    public static final int TELL_TICKS = 8;
    /** It turns after its target for this much of the wind-up, then holds its aim. */
    public static final int AIM_TICKS = 4;
    /** The blow lands as this starts; the rest is the staff coming back. */
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

    /** The revive beam runs this long: the fallen one stands again as it ends. */
    public static final int REVIVE_TICKS = 100;
    /** The share of its health a revived watcher stands up with. */
    public static final float REVIVE_HEALTH = 0.5F;
    /** A twin farther than this (centre to centre) can't reach its fallen partner with the beam: that kill is final. */
    public static final double CHANNEL_RANGE = 12.0;
    /** A rising hum every this many ticks of the beam. */
    public static final int HUM_EVERY = 20;
    /** Standing up again takes this long, harmless. */
    public static final int RISE_TICKS = 20;
    /** A watcher placed from a spawn egg links to the nearest unlinked one within this many blocks. */
    public static final double EGG_LINK = 8.0;
    /** Ticks a husk or a channelling twin waits for its partner to turn up after a load before giving it up. */
    public static final int PARTNER_WAIT = 40;
    /** A husk's hitbox: lying on the ground. */
    public static final float HUSK_WIDTH = 1.2F;
    public static final float HUSK_HEIGHT = 0.45F;

    public static final double SPEED = 0.25;
    public static final int XP = 12;

    /** Synced flag bits: it lies as a husk; its eye is on its right (the twin of a pair); the beam is drawn. */
    public static final int FLAG_HUSK = 0;
    public static final int FLAG_MIRROR = 1;
    public static final int FLAG_BEAM = 2;
    /** Glow stages: standing, its eye lit; a husk gone dark; the brightest a husk gets, as the beam ends. */
    public static final int STAGE_STANDING = 0;
    public static final int STAGE_DARK = 1;
    public static final int STAGE_BRIGHTEST = 4;
    /** A husk's health while it lies: kept above 0 so it isn't dying, and nothing can take it (it can't be hurt). */
    private static final float HUSK_HEALTH = 1.0F;

    private static final int LILAC = 0xD8C8FF;
    private static final int STAR = 0xFBF8FF;
    private static final DustParticleOptions BEAM_DUST = new DustParticleOptions(LILAC, 1.0F);
    private static final DustParticleOptions STAR_DUST = new DustParticleOptions(STAR, 1.2F);
    private static final BlockParticleOption SHARDS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
    private static final Telegraph TELL = Telegraph.particles(BEAM_DUST, 8, 0.3, 0.9).withSound(ModSounds.GEMINI_WATCHERS_TELL.get(), 1.0F, 1.0F);
    private static final Telegraph CHANNEL_TELL = Telegraph.particles(STAR_DUST, 16, 0.35, 0.85).withSound(ModSounds.GEMINI_WATCHERS_CHANNEL.get(), 1.4F, 1.0F);

    private @Nullable UUID twinId;
    private @Nullable GeminiWatchers twinRef;
    /** A player who killed it into a husk: credited if the husk crumbles with no player kill of its own. */
    private @Nullable UUID killerId;
    /** True while it channels a revive on its twin's husk. */
    private boolean reviving;
    private int partnerMissing;
    private int untilSwing;
    private int strikes;
    private int revives;

    public GeminiWatchers(EntityType<? extends GeminiWatchers> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("gemini_watchers", SPEED);
    }

    @Override
    public int visualStages() {
        return STAGE_BRIGHTEST + 1;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this) {
            @Override
            public boolean canUse() {
                return !husk() && super.canUse();
            }
        });
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return !husk() && core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !husk() && core().idle() && super.canContinueToUse();
            }

            /** It closes in only: every blow is the wind-up and the strike, started from its AI step. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return !husk() && core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !husk() && core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return !husk() && core().idle() && super.canUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !husk() && core().idle() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this, GeminiWatchers.class) {
            @Override
            public boolean canUse() {
                return !husk() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return !husk() && super.canUse();
            }
        });
    }

    // ---- what it is now ------------------------------------------------------------------------------------------------

    /** True while it lies as a husk. Both sides read the synced flag. */
    public boolean husk() {
        return core() != null && core().flag(FLAG_HUSK);              // null while the Entity constructor runs
    }

    /** True for the twin of a pair: its eye on its right. Both sides. */
    public boolean mirrored() {
        return core() != null && core().flag(FLAG_MIRROR);
    }

    /** True while it channels a revive on its twin's husk. Server side. */
    public boolean reviving() {
        return reviving;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (husk()) return EntityDimensions.scalable(HUSK_WIDTH, HUSK_HEIGHT).withEyeHeight(HUSK_HEIGHT * 0.5F);
        return super.getDefaultDimensions(pose);
    }

    /** The role clients last sized it for; null until the first synced update. */
    private @Nullable Boolean sizedHusk;

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (core() == null) return;
        boolean h = husk();
        if (sizedHusk == null || sizedHusk != h) {
            sizedHusk = h;
            refreshDimensions();
        }
    }

    // ---- the pair ------------------------------------------------------------------------------------------------------

    /** What a natural spawn group shares: the pair's first watcher. */
    public record PairData(GeminiWatchers first) implements SpawnGroupData {}

    /**
     * Links {@code first} and {@code second} as a pair: each knows the other by id, the first has its eye on its left and
     * the second on its right. Public as a test hook.
     */
    public static void pair(GeminiWatchers first, GeminiWatchers second) {
        first.link(second, false);
        second.link(first, true);
    }

    private void link(GeminiWatchers twin, boolean mirror) {
        twinId = twin.getUUID();
        twinRef = twin;
        partnerMissing = 0;
        core().setFlag(FLAG_MIRROR, mirror);
    }

    private void unlink() {
        twinId = null;
        twinRef = null;
    }

    /** Whether it has a twin, found or not. Server side. Public as a test hook. */
    public boolean linked() {
        return twinId != null;
    }

    /** Its living twin (a husk counts), found again by id after a load; null for none. Server side. */
    public @Nullable GeminiWatchers twinOf(ServerLevel level) {
        if (twinId == null) return null;
        if (twinRef == null || twinRef.isRemoved() || !twinRef.isAlive() || !twinId.equals(twinRef.getUUID())) {
            twinRef = level.getEntity(twinId) instanceof GeminiWatchers g && g.isAlive() && !g.isRemoved() ? g : null;
        }
        return twinRef != null && getUUID().equals(twinRef.twinId) ? twinRef : null;
    }

    /**
     * A natural spawn group is a pair: the first hands itself on in the spawn data and the second links to it, wearing its
     * look. One placed from a spawn egg (by hand or from a dispenser) links to the nearest unlinked watcher within 8
     * blocks, in its look, or comes alone.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (data instanceof PairData pair) {
            GeminiWatchers first = pair.first();
            if (first != this && first.twinId == null && !first.husk()) {
                core().setLook(first.look(), true);
                pair(first, this);
            }
            return pair;
        }
        if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) return new PairData(this);
        if (reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.DISPENSER) {
            GeminiWatchers mate = null;
            for (GeminiWatchers g : level.getEntitiesOfClass(GeminiWatchers.class, getBoundingBox().inflate(EGG_LINK),
                    o -> o != this && o.isAlive() && !o.husk() && o.twinId == null && o.distanceTo(this) <= EGG_LINK)) {
                if (mate == null || g.distanceTo(this) < mate.distanceTo(this)) mate = g;
            }
            if (mate != null) {
                core().setLook(mate.look(), true);
                mate.link(this, mate.mirrored());
                link(mate, !mate.mirrored());
            }
        }
        return result;
    }

    // ---- the husk and the revive ---------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved() || !isAlive()) return;
        if (husk()) huskTick(level);
        else if (reviving) reviveTick(level);
    }

    /**
     * A blow that would kill it lays it down as a husk instead, while its twin stands within reach to raise it; a kill it
     * can't come back from is final. With its twin already a husk, the pair is killed together: both stay dead.
     */
    @Override
    public void die(DamageSource source) {
        if (!(level() instanceof ServerLevel level) || isRemoved() || dead) {
            super.die(source);
            return;
        }
        if (husk()) {                                                // only a kill that ignores invulnerability reaches a husk
            finalDeath(level, source);
            return;
        }
        GeminiWatchers twin = twinOf(level);
        if (twin != null && twin.husk()) {                           // killed while its twin lies fallen: both stay dead
            sever(level, twin);
            finalDeath(level, source);
            twin.crumble(level, source);
            return;
        }
        LivingEntity by = source.getEntity() instanceof LivingEntity l ? l : null;
        if (twin != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && fall(level, twin, by)) return;
        finalDeath(level, source);
    }

    /**
     * Falls as a husk while {@code twin} starts its revive beam; a vetoed "husk" or "channel", or a twin out of reach,
     * makes the kill final. Returns true if it fell as a husk.
     */
    private boolean fall(ServerLevel level, GeminiWatchers twin, @Nullable LivingEntity by) {
        if (twin.distanceTo(this) > CHANNEL_RANGE || !startPhase(Step.HUSK, 0, by)) return false;
        if (!twin.beginRevive(this)) {
            core().end();
            return false;
        }
        core().setFlag(FLAG_HUSK, true);
        core().setStage(STAGE_DARK);
        setHealth(HUSK_HEALTH);
        killerId = by instanceof Player p ? p.getUUID() : null;
        partnerMissing = 0;
        setTarget(null);
        getNavigation().stop();
        removeAllEffects();
        clearFire();
        holdStill();
        refreshDimensions();
        level.playSound(null, getX(), getY(0.5), getZ(), ModSounds.GEMINI_WATCHERS_HUSK.get(), getSoundSource(), 1.2F, 1.0F);
        level.sendParticles(SHARDS, true, true, getX(), getY(0.5), getZ(), 20, 0.4, 0.2, 0.4, 0.1);
        level.sendParticles(BEAM_DUST, true, true, getX(), getY(0.5), getZ(), 12, 0.5, 0.2, 0.5, 0.0);
        return true;
    }

    /** A husk waits for its twin's beam; with no twin channelling on it (gone, or never back after a load) it crumbles. */
    private void huskTick(ServerLevel level) {
        holdStill();
        GeminiWatchers twin = twinOf(level);
        if (twin != null && twin.reviving && twin.core().in(Step.CHANNEL)) {
            partnerMissing = 0;
            return;
        }
        if (++partnerMissing >= PARTNER_WAIT) crumble(level, damageSources().generic());
    }

    /**
     * Starts the revive beam on {@code husk}, its fallen twin: it turns to it, stands still and channels for 100 ticks. A
     * blow in progress is dropped. False if it can't (a husk itself, or a vetoed "channel").
     */
    private boolean beginRevive(GeminiWatchers husk) {
        if (!isAlive() || husk() || !startPhase(Step.CHANNEL, REVIVE_TICKS, husk)) return false;
        reviving = true;
        partnerMissing = 0;
        setTarget(null);
        holdStill();
        face(husk, 360.0F);
        showBeam(husk);
        CHANNEL_TELL.play(this);
        return true;
    }

    /** One tick of the beam: it faces the husk, holds still, draws the beam, brightens the husk and hums as it rises. */
    private void reviveTick(ServerLevel level) {
        if (!core().in(Step.CHANNEL)) {
            stopRevive();
            return;
        }
        GeminiWatchers husk = twinOf(level);
        if (husk == null || !husk.husk()) {
            if (++partnerMissing >= PARTNER_WAIT) stopRevive();          // the husk never came back after a load
            return;
        }
        partnerMissing = 0;
        holdStill();
        face(husk, 360.0F);
        showBeam(husk);
        int elapsed = core().elapsed();
        husk.core().setStage(Revive.stage(elapsed));
        drawBeam(level, husk, elapsed);
        if (elapsed > 0 && elapsed % HUM_EVERY == 0) {
            float rise = (float) elapsed / REVIVE_TICKS;                  // each hum higher than the one before
            level.playSound(null, getX(), getY(0.85), getZ(), ModSounds.GEMINI_WATCHERS_HUM.get(), getSoundSource(), 1.0F, 0.8F + 0.8F * rise);
        }
    }

    /** The beam runs its course: the fallen twin stands again. */
    private void finishRevive(ServerLevel level) {
        GeminiWatchers husk = twinOf(level);
        stopRevive();
        if (husk != null && husk.husk() && husk.isAlive()) husk.standAgain(level, this);
    }

    private void stopRevive() {
        reviving = false;
        showBeam(null);
        if (core().in(Step.CHANNEL)) core().end();
    }

    /** It stands again with half its health, on safe ground where it lay, and turns on its twin's foe. */
    private void standAgain(ServerLevel level, GeminiWatchers by) {
        core().setFlag(FLAG_HUSK, false);
        core().setStage(STAGE_STANDING);
        refreshDimensions();
        setHealth(getMaxHealth() * REVIVE_HEALTH);
        killerId = null;
        partnerMissing = 0;
        revives++;
        if (!level.noCollision(this) || level.containsAnyLiquid(getBoundingBox())) {
            Vec3 to = Riftjackal.SafeTeleport.InLevel.spot(level, this, getX(), getY(), getZ());
            if (to != null) Riftjackal.SafeTeleport.InLevel.move(level, this, to);
        }
        if (!startPhase(Step.RISE, RISE_TICKS, null)) core().end();
        LivingEntity foe = by.getLastHurtByMob() != null ? by.getLastHurtByMob() : by.getTarget();
        if (valid(foe)) setTarget(foe);
        level.playSound(null, getX(), getY(0.5), getZ(), ModSounds.GEMINI_WATCHERS_REVIVE.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(STAR_DUST, true, true, getX(), getY(0.5), getZ(), 30, 0.4, 0.8, 0.4, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY(0.6), getZ(), 12, 0.3, 0.6, 0.3, 0.06);
    }

    /** The bond breaks: the channelling twin is killed while its partner lies fallen; the beam scatters into motes. */
    private void sever(ServerLevel level, GeminiWatchers husk) {
        Vec3 from = eyePoint(), to = huskPoint(husk);
        Vec3 d = to.subtract(from);
        for (int i = 0; i <= 10; i++) {
            Vec3 p = from.add(d.scale(i / 10.0));
            level.sendParticles(ParticleTypes.END_ROD, true, true, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.08);
        }
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.GEMINI_WATCHERS_SEVER.get(), getSoundSource(), 1.3F, 1.0F);
        level.playSound(null, husk.getX(), husk.getY(), husk.getZ(), ModSounds.GEMINI_WATCHERS_SEVER.get(), getSoundSource(), 1.0F, 0.8F);
    }

    /** A husk's final death: its twin was killed (or is gone). It shatters where it lies. */
    private void crumble(ServerLevel level, DamageSource source) {
        if (!husk() || !isAlive()) return;
        finalDeath(level, source);
    }

    /**
     * A death that sticks: no beam, no link, a shattering eye; the kill is credited to the player who dealt it, or, for a
     * husk that crumbles on its own, to the player who laid it down.
     */
    private void finalDeath(ServerLevel level, DamageSource source) {
        if (reviving) stopRevive();
        if (!core().idle()) core().end();                            // a husk keeps lying (its flag) while its death plays
        GeminiWatchers twin = twinOf(level);
        if (twin != null) twin.unlink();
        unlink();
        if (source.getEntity() instanceof Player p) setLastHurtByPlayer(p, 100);
        else if (killerId != null) setLastHurtByPlayer(killerId, 100);
        if (getHealth() > 0.0F) setHealth(0.0F);
        level.sendParticles(SHARDS, true, true, getX(), getY(0.5), getZ(), 30, 0.3, 0.5, 0.3, 0.15);
        level.sendParticles(STAR_DUST, true, true, getX(), getY(0.5), getZ(), 10, 0.3, 0.4, 0.3, 0.0);
        level.playSound(null, getX(), getY(0.6), getZ(), ModSounds.GEMINI_WATCHERS_DEATH.get(), getSoundSource(), getSoundVolume(), getVoicePitch());
        super.die(source);
    }

    /** The beam's two ends: the middle of its eye, and the middle of the husk lying on the ground. */
    private Vec3 eyePoint() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
        double s = getScale();
        return position().add(0.0, (24.0 + 3.5) / 16.0 * s, 0.0).add(forward.scale(5.0 / 16.0 * s)).add(left.scale((mirrored() ? -1.0 : 1.0) / 16.0 * s));
    }

    private static Vec3 huskPoint(GeminiWatchers husk) {
        return husk.position().add(0.0, Beam.AIM_UP, 0.0);
    }

    /**
     * The beam, as everyone sees it from afar: lilac dust every other tick creeping from the eye into the husk, a
     * star-white mote running its length every few ticks, and motes rising off the husk, more as it brightens.
     */
    private void drawBeam(ServerLevel level, GeminiWatchers husk, int elapsed) {
        Vec3 from = eyePoint(), to = huskPoint(husk);
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 0.3) return;
        if (elapsed % 2 == 0) {
            double creep = (elapsed / 2 % 3) / 3.0 * 0.6;
            for (double run = creep; run < length; run += 0.6) {
                Vec3 p = from.add(d.scale(run / length));
                level.sendParticles(BEAM_DUST, true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (elapsed % 5 == 0) {
            Vec3 p = from.add(d.scale((elapsed % 15) / 15.0));
            level.sendParticles(STAR_DUST, true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        int every = Math.max(1, 5 - Revive.stage(elapsed));
        if (elapsed % every == 0) {
            level.sendParticles(ParticleTypes.END_ROD, true, true, husk.getX(), husk.getY() + 0.3, husk.getZ(), 1, 0.4, 0.05, 0.4, 0.02);
        }
    }

    /** Puts the way to the husk into the synced flags (for the model's beam), or clears it (null). */
    private void showBeam(@Nullable GeminiWatchers husk) {
        int bits = husk == null ? 0 : Beam.encode(husk.getX() - getX(), husk.getY() - getY(), husk.getZ() - getZ());
        int flags = core().flags();
        for (int bit = Beam.X_BIT; bit < Beam.END_BIT; bit++) {
            boolean on = (bits >> bit & 1) != 0;
            if (((flags >> bit) & 1) != (on ? 1 : 0)) core().setFlag(bit, on);
        }
        if (core().flag(FLAG_BEAM) != (husk != null)) core().setFlag(FLAG_BEAM, husk != null);
    }

    // ---- what a husk is to the world -------------------------------------------------------------------------------------

    /** A husk can't be hurt (only a kill that ignores invulnerability reaches it). */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return husk() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
    }

    /** A husk is no target: blows and arrows pass it by, and no mob sees it as a foe. */
    @Override
    public boolean isPickable() {
        return !husk() && super.isPickable();
    }

    @Override
    public boolean isAttackable() {
        return !husk() && super.isAttackable();
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !husk() && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return husk() || super.ignoreExplosion(explosion);
    }

    @Override
    public boolean isPushable() {
        return !husk() && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        if (!husk()) super.pushEntities();
    }

    /** A husk, a channelling twin and one standing up again stay where they are: they only fall. */
    @Override
    public Pin pin() {
        if (core() == null) return Pin.FREE;
        return husk() || core().in(Step.CHANNEL) || core().in(Step.RISE) ? Pin.ROOTED : Pin.FREE;
    }

    /** A husk and a channelling twin never despawn mid-revive. */
    @Override
    public boolean requiresCustomPersistence() {
        return husk() || reviving || super.requiresCustomPersistence();
    }

    /** The box the renderer culls it by: a husk lies long, and a beam reaches to the husk. */
    @Override
    public AABB getInterpolatedBoundingBox(float partialTick) {
        AABB box = super.getInterpolatedBoundingBox(partialTick);
        if (core() == null) return box;
        if (husk()) box = box.inflate(0.6, 0.2, 0.6);
        if (core().flag(FLAG_BEAM)) {
            int flags = core().flags();
            box = box.minmax(box.move(Beam.x(flags), Beam.y(flags), Beam.z(flags)));
        }
        return box;
    }

    /** A blow to one turns its twin on the attacker too. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && !husk() && source.getEntity() instanceof LivingEntity by) {
            GeminiWatchers twin = twinOf(level);
            if (twin != null && !twin.husk() && !twin.reviving && twin.valid(by) && !twin.valid(twin.getTarget())) twin.setTarget(by);
        }
        return hurt;
    }

    // ---- the staff blow ------------------------------------------------------------------------------------------------

    private boolean valid(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && !target.isRemoved() && target.level() == level()
                && !(target instanceof GeminiWatchers) && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
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

    /** While a step runs, and while it lies, its body stays square to the way it faces. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        if (core() != null && (!core().idle() || husk())) this.yBodyRot = getYRot();
        else super.tickHeadTurn(yBodyRot);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (untilSwing > 0) untilSwing--;
        if (husk()) return;
        if (!core().idle()) {
            holdStill();
            if (core().in(Step.TELL)) tellTick();
            return;
        }
        LivingEntity target = getTarget();
        if (untilSwing == 0 && valid(target) && MobCore.gap(this, target) <= START_REACH && getSensing().hasLineOfSight(target)) tell(target);
    }

    /** Winds up at {@code target} for {@link #TELL_TICKS}; the strike follows. Returns false if refused. Public as a test hook. */
    public boolean tell(@Nullable LivingEntity target) {
        if (husk() || !core().idle() || !(level() instanceof ServerLevel) || !startPhase(Step.TELL, TELL_TICKS, target)) return false;
        untilSwing = SWING_EVERY;
        holdStill();
        if (target != null) face(target, 360.0F);
        TELL.play(this);
        return true;
    }

    /** The wind-up: it turns after its target for the first half, and lilac motes rise from its staff. */
    private void tellTick() {
        LivingEntity target = core().target();
        if (core().elapsed() < AIM_TICKS && valid(target)) face(target, TURN);
        if (core().elapsed() % 2 == 0 && level() instanceof ServerLevel level) {
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            double side = mirrored() ? 1.0 : -1.0;                   // the staff is in the hand away from the eye
            Vec3 staff = position().add(Mth.cos(yaw) * 0.3 * side, getBbHeight() * 1.1, Mth.sin(yaw) * 0.3 * side);
            level.sendParticles(BEAM_DUST, true, true, staff.x, staff.y, staff.z, 2, 0.08, 0.1, 0.08, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) return;
        if (ended == Step.TELL) strike(level, core().target());
        else if (ended == Step.CHANNEL && reviving) finishRevive(level);
        else core().end();
    }

    /** The strike: its attack (armour counts) to a target still in reach (across or up and down), in front and in sight, without knockback. */
    private void strike(ServerLevel level, @Nullable LivingEntity target) {
        if (!startPhase(Step.STRIKE, STRIKE_TICKS, target)) {
            core().end();
            return;
        }
        level.playSound(null, getX(), getY(0.7), getZ(), ModSounds.GEMINI_WATCHERS_STRIKE.get(), getSoundSource(), 1.0F, 1.0F);
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

    // ---- test hooks ----------------------------------------------------------------------------------------------------

    /** Blows that landed. Public as a test hook. */
    public int strikes() {
        return strikes;
    }

    /** Times it stood again from a husk. Public as a test hook. */
    public int revives() {
        return revives;
    }

    /** Ticks until it may wind up again. Public as a test hook. */
    public int untilSwing() {
        return untilSwing;
    }

    /** Lets it wind up again at once. Public as a test hook. */
    public void resetSwing() {
        untilSwing = 0;
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (twinId != null) output.putString("Twin", twinId.toString());
        output.putBoolean("Mirror", mirrored());
        output.putBoolean("Husk", husk());
        if (killerId != null) output.putString("Killer", killerId.toString());
        boolean channelling = reviving && core().in(Step.CHANNEL);
        output.putBoolean("Reviving", channelling);
        if (channelling) output.putInt("ReviveLeft", Math.max(1, REVIVE_TICKS - core().elapsed()));
        output.putInt("SwingWait", untilSwing);
        output.putInt("Revives", revives);
    }

    /**
     * It keeps its twin's id, its side, whether it lies as a husk (and who laid it down), and a revive in progress with
     * the ticks it has left; the two find each other again by id. A reload mid-blow ends the blow.
     */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        twinId = uuid(input.getStringOr("Twin", ""));
        twinRef = null;
        core().setFlag(FLAG_MIRROR, input.getBooleanOr("Mirror", false));
        boolean h = input.getBooleanOr("Husk", false);
        core().setFlag(FLAG_HUSK, h);
        core().setStage(h ? STAGE_DARK : STAGE_STANDING);
        killerId = uuid(input.getStringOr("Killer", ""));
        refreshDimensions();
        untilSwing = Math.clamp(input.getIntOr("SwingWait", 0), 0, SWING_EVERY);
        revives = input.getIntOr("Revives", 0);
        reviving = false;
        if (!h && input.getBooleanOr("Reviving", false)) {
            int left = Math.clamp(input.getIntOr("ReviveLeft", REVIVE_TICKS), 1, REVIVE_TICKS);
            reviving = startPhase(Step.CHANNEL, left, null);
        }
        partnerMissing = 0;
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
        return !husk() && core().idle() ? ModSounds.GEMINI_WATCHERS_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GEMINI_WATCHERS_HURT.get();
    }

    /** None here: a killing blow may lay it down as a husk, so a final death plays its own. */
    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }

    // ---- the husk's glow as the beam runs (pure maths, no game types) ---------------------------------------------------

    /** How bright a husk glows as its twin's beam runs. */
    public static final class Revive {
        private Revive() {}

        /** The husk's glow stage {@code elapsed} ticks into the beam: dark, then three steps brighter, a step each quarter. */
        public static int stage(int elapsed) {
            return Math.clamp(STAGE_DARK + (long) elapsed * (STAGE_BRIGHTEST - STAGE_DARK + 1) / REVIVE_TICKS, STAGE_DARK, STAGE_BRIGHTEST);
        }
    }

    // ---- the beam in the flags (pure maths, no game types) ---------------------------------------------------------------

    /**
     * The way from a channelling twin's feet to its husk, packed into the synced flags so the model can stretch its beam
     * from the eye to the husk. Bits 3-9, 10-16 and 17-23 hold x, z and y, each in quarter blocks, signed; each reaches
     * 15.75 blocks either way, past the 12 a beam can span.
     */
    public static final class Beam {
        public static final int X_BIT = 3, Z_BIT = 10, Y_BIT = 17, END_BIT = 24;
        private static final int SIZE = 7;
        public static final double STEP = 0.25;
        /** The beam aims this high over the husk's feet: the middle of a body lying on the ground. */
        public static final double AIM_UP = 0.25;

        private Beam() {}

        private static int pack(double v) {
            int max = (1 << SIZE - 1) - 1;
            return Math.clamp(Math.round(v / STEP), -max, max) & (1 << SIZE) - 1;
        }

        private static double unpack(int flags, int bit) {
            int raw = flags >> bit & (1 << SIZE) - 1;
            if (raw >= 1 << SIZE - 1) raw -= 1 << SIZE;
            return raw * STEP;
        }

        public static int encode(double dx, double dy, double dz) {
            return pack(dx) << X_BIT | pack(dz) << Z_BIT | pack(dy) << Y_BIT;
        }

        public static double x(int flags) {
            return unpack(flags, X_BIT);
        }

        public static double z(int flags) {
            return unpack(flags, Z_BIT);
        }

        public static double y(int flags) {
            return unpack(flags, Y_BIT);
        }
    }
}
