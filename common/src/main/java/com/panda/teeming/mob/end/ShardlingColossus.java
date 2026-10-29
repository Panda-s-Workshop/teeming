package com.panda.teeming.mob.end;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModSounds;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.EntitySpawnReason;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shardling Colossus: a heavy golem of violet void crystal with a glowing seam down its middle. Its blow is a slam:
 * for 16 ticks the seam cracks and glows, it raises both arms with a grinding note and lilac dust marks the reach on
 * the ground (it turns to follow its target for the first half of that, then holds), and then it slams down in front
 * of it: its attack (9) to everything within 3 blocks of its middle and 75 degrees of where it faces, without
 * knockback, so a slam never throws anyone off an island.
 *
 * <p>A single hit of 12 or more (as dealt, before armour), or a hit that leaves it below half its health, splits it
 * along the seam into two halves with a shatter and sparks: each at scale 0.7 with half the health it had when it
 * split (and half its maximum), hitting for 6 and a little quicker on its one leg. The halves fight on. 100 ticks
 * after the split, if both still live, they stop fighting, walk to each other and rejoin into one colossus with their
 * health added up. A rejoined colossus is fused: its seam is sealed and it never splits again.
 *
 * <p>Counter: kill one half within the 100 ticks; the other goes dark and fights on alone, and never rejoins. Each half
 * gives half its experience. The first half to fall drops a whole colossus's drops (its void crystal, its leftovers and
 * its trophy roll); the second drops them again only if a player split it with one heavy hit and both halves fell
 * inside those 100 ticks. A colossus worn below half, or one whose halves took longer, drops once; so does a fused
 * one, which is one colossus again. Each half keeps its own record, so the rule holds when its twin is out of reach (in
 * an unloaded chunk or another dimension): the left half carries the drops from the split, and a half that falls with
 * its twin in reach takes them from it. A half that falls with its twin out of reach drops them only if it carries them;
 * the other drops them again only if it saw its twin fall, so the colossus never pays more than the rule says.
 *
 * <p>The halves are this same entity type in another form, kept in the synced flags ({@link Form}). The colossus
 * itself becomes the left half; the right half is new, made at the split, and stays exactly as persistent as the
 * colossus it came from. Starting the split step on a whole colossus is the split itself.
 */
public class ShardlingColossus extends TeemingMonster {
    /**
     * IDLE; the slam's tell and the slam; the halves walking together; the rejoined colossus settling; the halves
     * staggering out of the split.
     */
    public enum Step implements Phase { IDLE, TELL, SLAM, REGROUP, FUSE, SPLIT }

    /** What this one is: the whole colossus, or its left or right half. */
    public enum Form { WHOLE, LEFT, RIGHT }

    /** 16 ticks: the seam cracks, the arms go up. */
    public static final int TELL_TICKS = 16;
    /** It turns to follow its target for this much of the tell, then holds its aim: time to step aside. */
    public static final int AIM_TICKS = 8;
    /** The slam lands as this starts; the rest is the arms coming back up. */
    public static final int SLAM_TICKS = 12;
    /** Ticks from one tell to the next, at the soonest. */
    public static final int SLAM_EVERY = 40;
    /** The slam reaches this far from its middle (at scale 1; a half's reach shrinks with it), this wide either side. */
    public static final double SLAM_REACH = 3.0;
    public static final double SLAM_ARC = 75.0;
    /**
     * A single hit this big (as dealt, before armour) splits it: more than the End reference sword's 11, so a plain
     * blow doesn't, and splitting it early takes a heavier one (a critical hit).
     */
    public static final float SPLIT_HIT = 12.0F;
    /** A hit that leaves it below this share of its maximum health splits it. */
    public static final float SPLIT_BELOW = 0.5F;
    /** A half: this scale, and this share of the colossus's health (and of its maximum). */
    public static final float HALF_SCALE = 0.7F;
    public static final float HALF_SHARE = 0.5F;
    /** A half's blow and speed. */
    public static final double HALF_ATTACK = 6.0;
    public static final double HALF_SPEED = 0.26;
    /** The halves stagger out of the split this long before they fight. */
    public static final int SPLIT_TICKS = 10;
    /** Each half lands this far to its own side of where the colossus stood. */
    public static final double SPLIT_APART = 0.5;
    /** Ticks after the split when both halves, if they live, set off to rejoin. */
    public static final int REJOIN_AFTER = 100;
    /** Halves whose middles come this close (across the ground) rejoin. */
    public static final double MEET = 1.2;
    /** Halves this close (across the ground) while walking together stop pathing and walk straight at each other. */
    public static final double STEER = 2.5;
    /** Halves that haven't met after this long fight on, and set off again another 100 ticks later. */
    public static final int REGROUP_MAX = 200;
    public static final double REGROUP_SPEED = 1.1;
    /** The rejoined colossus stands and settles this long. */
    public static final int FUSE_TICKS = 20;
    public static final double SPEED = 0.2;
    public static final double KNOCKBACK_RESISTANCE = 0.6;
    /** Experience for the whole colossus and for each half. */
    public static final int XP = 14;
    public static final int HALF_XP = 7;

    /** Synced flag bits: a half, the right half, a fused colossus. */
    public static final int FLAG_HALF = 0;
    public static final int FLAG_RIGHT = 1;
    public static final int FLAG_FUSED = 2;

    public static final int SEAM_COLOR = 0xCFA8FF;
    public static final int CRYSTAL_COLOR = 0x6B45B8;
    public static final int GLINT_COLOR = 0xF6F2FF;
    private static final DustParticleOptions SEAM = new DustParticleOptions(SEAM_COLOR, 1.0F);
    private static final DustParticleOptions SHARD = new DustParticleOptions(CRYSTAL_COLOR, 1.4F);
    private static final DustParticleOptions GLINT = new DustParticleOptions(GLINT_COLOR, 0.8F);
    private static final DustParticleOptions DARK = new DustParticleOptions(0x2C1D52, 1.2F);
    private static final Identifier HALF_MODIFIER = TeemingMod.id("shardling_half");
    private static final Telegraph CRACK = Telegraph.particles(SEAM, 12, 0.3, 0.6).withSound(ModSounds.SHARDLING_COLOSSUS_TELL.get(), 1.3F, 0.85F);

    /** Ticks until it may tell again. */
    private int untilSlam;
    /** A fused colossus never splits again. */
    private boolean fused;
    /** A half's twin, and the game time its clock started (the split, or its last try at rejoining). */
    private @Nullable UUID twinId;
    private @Nullable ShardlingColossus twin;
    private long splitAt;
    /**
     * A half: whether a player's single hit of {@link #SPLIT_HIT} or more split it (not wearing it below half), the game
     * time of the split (never moved by a try at rejoining), whether its twin has already fallen and dropped the
     * colossus's drops, and whether it carries the drops (the one half that may drop them without a heavy split's second
     * pay). All saved, so none needs the twin to be found.
     */
    private boolean heavy;
    private long splitTime;
    private boolean twinFell;
    private boolean carries;
    /** The right half, made at the split; and whether the colossus it came from was kept from despawning then. */
    private boolean made;
    private boolean kept;
    /** The form clients last sized it for; null until the first synced update. */
    private @Nullable Form sizedFor;
    /** Set in registerGoals, which runs inside the Mob constructor, so it has no initializer. */
    private @Nullable WaterAvoidingRandomStrollGoal stroll;

    public ShardlingColossus(EntityType<? extends ShardlingColossus> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("shardling_colossus", SPEED).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
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

            /** In reach it never just swings: every blow is the tell and the slam. */
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
                if (untilSlam == 0 && core().idle() && canPerformAttack(target)) tell(target);
            }
        });
        stroll = new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        };
        goalSelector.addGoal(5, stroll);
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

    // ---- what it is ----------------------------------------------------------------------------------------------

    /** Its form, read from the synced flags on either side. */
    public Form form() {
        return core() == null ? Form.WHOLE : formOf(core().flags());          // null while the Entity constructor runs
    }

    /** The form held in a set of synced flags: for the model, which sees only the flags. */
    public static Form formOf(int flags) {
        if ((flags & 1 << FLAG_HALF) == 0) return Form.WHOLE;
        return (flags & 1 << FLAG_RIGHT) != 0 ? Form.RIGHT : Form.LEFT;
    }

    /** Whether a set of synced flags is a fused colossus's: for the model's sealed seam. */
    public static boolean fusedIn(int flags) {
        return (flags & 1 << FLAG_FUSED) != 0;
    }

    /** True for a colossus that rejoined from its halves: it never splits again. */
    public boolean fused() {
        return fused;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (core() == null) return;
        Form f = form();
        if (f != sizedFor) {
            sizedFor = f;
            refreshDimensions();
        }
    }

    /** It stands to tell and slam, staggers where it split, and settles where it rejoined. */
    @Override
    public Pin pin() {
        return core().in(Step.TELL) || core().in(Step.SLAM) || core().in(Step.SPLIT) || core().in(Step.FUSE) ? Pin.ROOTED : Pin.FREE;
    }

    /** Starting the split step on a whole colossus splits it: the step is the split itself. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (phase == Step.SPLIT && form() == Form.WHOLE && !level().isClientSide()) return split(target, length) != null;
        return core().start(phase, length, target);
    }

    // ---- the slam --------------------------------------------------------------------------------------------------

    /** Starts the tell if it is free to; true if it did. Ignores the wait since the last slam. Public as a test hook. */
    public boolean tell(@Nullable LivingEntity target) {
        if (level().isClientSide() || !core().idle()) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        untilSlam = SLAM_EVERY;
        getNavigation().stop();
        if (target != null) face(target.position().subtract(position()));
        CRACK.play(this);
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (untilSlam > 0) untilSlam--;
        if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().in(Step.REGROUP)) {
            regroupTick(level);
        } else if (!core().idle()) {
            getNavigation().stop();
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        }
    }

    /**
     * The tell: it stands, turned to its target for the first {@link #AIM_TICKS} and then holding its aim, while the seam
     * sparks and lilac dust traces the slam's reach on the ground; a second grind half way.
     */
    private void tellTick(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        int t = core().elapsed();
        LivingEntity target = core().target();
        if (t < AIM_TICKS && target != null && target.isAlive()) face(target.position().subtract(position()));
        Vec3 chest = position().add(forward().scale(getBbWidth() * 0.45)).add(0.0, getBbHeight() * 0.6, 0.0);
        if (t % 2 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chest.x, chest.y, chest.z, 2, 0.05, getBbHeight() * 0.18, 0.05, 0.05);
        if (t % 3 == 0) level.sendParticles(SEAM, chest.x, chest.y, chest.z, 3, 0.08, getBbHeight() * 0.2, 0.08, 0.0);
        if (t % 4 == 0) reachMarks(level);
        if (t == TELL_TICKS / 2) playSound(ModSounds.SHARDLING_COLOSSUS_TELL.get(), 1.0F, 0.95F);
    }

    /** Lilac dust on the ground along the edge of the slam's reach, across its arc. */
    private void reachMarks(ServerLevel level) {
        double reach = SLAM_REACH * getScale();
        int points = 11;
        for (int i = 0; i < points; i++) {
            double a = -SLAM_ARC + 2.0 * SLAM_ARC * i / (points - 1);
            Vec3 d = forward().yRot((float) Math.toRadians(a)).scale(reach - 0.2);
            level.sendParticles(SEAM, getX() + d.x, getY() + 0.1, getZ() + d.z, 1, 0.05, 0.02, 0.05, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            if (startPhase(Step.SLAM, SLAM_TICKS, core().target())) slam(level);
            else core().end();
        } else {
            core().end();
        }
    }

    /**
     * The slam: its attack to every living thing (but other colossi) within {@link #SLAM_REACH} of its middle, scaled
     * with it, and {@link #SLAM_ARC} degrees of where it faces, as a mob's blow (shields catch it) with no knockback.
     * Returns how many it hurt. Public as a test hook.
     */
    public int slam(ServerLevel level) {
        double reach = SLAM_REACH * getScale();
        Vec3 ahead = forward();
        double cos = Math.cos(Math.toRadians(SLAM_ARC));
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        int hit = 0;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach, 1.0, reach), this::canHit)) {
            if (!inReach(victim.getBoundingBox(), reach, ahead, cos)) continue;
            if (!MechanicEvent.post(this, "slam_hit", victim)) continue;
            if (AstralAugur.GroundMarks.hurtInPlace(level, victim, damageSources().mobAttack(this), damage)) hit++;
        }
        Vec3 fists = position().add(ahead.scale(getBbWidth() * 0.5 + 0.8 * getScale()));
        BlockState ground = level.getBlockState(BlockPos.containing(fists.x, getY() - 0.2, fists.z));
        if (ground.isAir()) ground = Blocks.END_STONE.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), fists.x, getY() + 0.1, fists.z, 30, 0.7 * getScale(), 0.05, 0.7 * getScale(), 0.2);
        level.sendParticles(SHARD, fists.x, getY() + 0.3, fists.z, 12, 0.6 * getScale(), 0.2, 0.6 * getScale(), 0.02);
        for (int i = 0; i < 24; i++) {                                         // dust over the reach, never past it
            double a = Math.toRadians(-SLAM_ARC + random.nextDouble() * 2.0 * SLAM_ARC);
            Vec3 d = ahead.yRot((float) a).scale(0.8 + random.nextDouble() * (reach - 1.0));
            level.sendParticles(ParticleTypes.POOF, getX() + d.x, getY() + 0.15, getZ() + d.z, 1, 0.0, 0.02, 0.0, 0.01);
        }
        level.playSound(null, fists.x, getY(), fists.z, ModSounds.SHARDLING_COLOSSUS_SLAM.get(), getSoundSource(), 1.3F, form() == Form.WHOLE ? 0.9F : 1.15F);
        return hit;
    }

    /** Whether a hitbox lies in the slam: its nearest point within {@code reach} across the ground, inside the arc, at its feet' height. */
    private boolean inReach(AABB box, double reach, Vec3 ahead, double cos) {
        double nx = Mth.clamp(getX(), box.minX, box.maxX), nz = Mth.clamp(getZ(), box.minZ, box.maxZ);
        double dx = nx - getX(), dz = nz - getZ();
        if (dx * dx + dz * dz > reach * reach) return false;
        if (box.maxY < getY() - 0.5 || box.minY > getY() + getBbHeight()) return false;
        Vec3 to = new Vec3((box.minX + box.maxX) * 0.5 - getX(), 0.0, (box.minZ + box.maxZ) * 0.5 - getZ());
        return to.lengthSqr() < 0.25 || to.normalize().dot(ahead) >= cos;
    }

    private boolean canHit(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof ShardlingColossus) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private static boolean valid(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof ShardlingColossus)
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---- the split -------------------------------------------------------------------------------------------------

    /**
     * A hit of 12 or more, or one that leaves it below half its health, splits a whole colossus that isn't fused. A
     * player's hit of 12 or more makes it a heavy split, whatever health it leaves.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && !isRemoved() && form() == Form.WHOLE && !fused
                && (amount >= SPLIT_HIT || getHealth() < getMaxHealth() * SPLIT_BELOW)) {
            split(source.getEntity() instanceof LivingEntity by ? by : null, SPLIT_TICKS,
                    amount >= SPLIT_HIT && source.getEntity() instanceof Player);
        }
        return hurt;
    }

    /** A split that wasn't a player's heavy hit: {@link #split(LivingEntity, int, boolean)} with {@code heavy} false. */
    public @Nullable ShardlingColossus split(@Nullable LivingEntity by, int ticks) {
        return split(by, ticks, false);
    }

    /**
     * The split: it becomes the left half, and the right half stands up beside it, each at scale 0.7 with half the
     * health it had (and half its maximum), staggering for {@code ticks} and then after {@code by}. {@code heavy}: a
     * player's single heavy hit split it, so both halves falling inside {@link #REJOIN_AFTER} pays the drops twice. A
     * vetoed "split" leaves it whole. Returns the new right half, or null if it didn't split. Public as a test hook.
     */
    public @Nullable ShardlingColossus split(@Nullable LivingEntity by, int ticks, boolean heavy) {
        if (!(level() instanceof ServerLevel level) || form() != Form.WHOLE || fused || isRemoved() || !isAlive()) return null;
        if (!core().start(Step.SPLIT, ticks, by)) return null;
        float each = getHealth() * HALF_SHARE;
        boolean keep = isPersistenceRequired() || requiresCustomPersistence();
        String look = look().isEmpty() ? "base" : look();
        AttributeInstance range = getAttribute(Attributes.FOLLOW_RANGE);
        double follow = range == null ? 24.0 : range.getBaseValue();
        Vec3 at = position();
        Vec3 side = left().scale(SPLIT_APART * getScale());
        LivingEntity target = valid(by) ? by : null;
        @SuppressWarnings("unchecked")
        EntityType<ShardlingColossus> type = (EntityType<ShardlingColossus>) getType();
        ShardlingColossus right = convertTo(type, new ConversionParams(ConversionType.SPLIT_ON_DEATH, false, false, getTeam()),
                EntitySpawnReason.TRIGGERED, c -> {
                    c.core().setLook(look, false);
                    AttributeInstance r = c.getAttribute(Attributes.FOLLOW_RANGE);
                    if (r != null) r.setBaseValue(follow);                     // the halves keep its senses
                    c.becomeHalf(Form.RIGHT);
                    c.setHealth(each);
                    c.made = true;
                    c.kept = keep;
                    c.twinId = getUUID();
                    c.twin = this;
                    c.snapTo(at.x, at.y, at.z, getYRot(), 0.0F);
                    c.yBodyRot = yBodyRot;
                    c.yHeadRot = yHeadRot;
                    c.core().start(Step.SPLIT, ticks, target);
                    if (target != null) c.setTarget(target);
                });
        if (right == null) {
            core().end();
            return null;
        }
        becomeHalf(Form.LEFT);
        setHealth(each);
        made = false;
        kept = false;
        twinId = right.getUUID();
        twin = right;
        splitAt = level.getGameTime();
        right.splitAt = splitAt;
        for (ShardlingColossus h : java.util.List.of(this, right)) {
            h.heavy = heavy;
            h.splitTime = splitAt;
            h.twinFell = false;
            h.carries = h == this;                                          // the left half carries the drops
        }
        place(level, this, at.add(side));
        place(level, right, at.subtract(side));
        if (target != null) setTarget(target);
        shatter(level, at);
        return right;
    }

    /** Moves a half to {@code to} if it fits there; otherwise it stays where the colossus stood. */
    private static void place(ServerLevel level, ShardlingColossus half, Vec3 to) {
        AABB box = half.getBoundingBox().move(to.x - half.getX(), to.y - half.getY(), to.z - half.getZ());
        if (level.noCollision(half, box)) half.setPos(to.x, to.y, to.z);
    }

    /** The shatter: crystal shards, sparks and star-white glints burst from the seam, with the sound of breaking glass. */
    private void shatter(ServerLevel level, Vec3 at) {
        double y = at.y + getBbHeight() * 0.7;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), at.x, y, at.z, 40, 0.3, 0.6, 0.3, 0.2);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, y, at.z, 24, 0.15, 0.7, 0.15, 0.3);
        level.sendParticles(SHARD, at.x, y, at.z, 20, 0.3, 0.6, 0.3, 0.05);
        level.sendParticles(GLINT, at.x, y, at.z, 12, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, at.x, y, at.z, ModSounds.SHARDLING_COLOSSUS_SPLIT.get(), getSoundSource(), 1.4F, 1.0F);
    }

    /** A half: the flags, then its scale, health share, blow and speed as permanent modifiers (saved with it). */
    private void becomeHalf(Form side) {
        core().setFlag(FLAG_HALF, true);
        core().setFlag(FLAG_RIGHT, side == Form.RIGHT);
        modify(Attributes.SCALE, HALF_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(Attributes.MAX_HEALTH, HALF_SHARE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(Attributes.ATTACK_DAMAGE, HALF_ATTACK - baseOf(Attributes.ATTACK_DAMAGE), AttributeModifier.Operation.ADD_VALUE);
        modify(Attributes.MOVEMENT_SPEED, HALF_SPEED - baseOf(Attributes.MOVEMENT_SPEED), AttributeModifier.Operation.ADD_VALUE);
        xpReward = HALF_XP;
        refreshDimensions();
    }

    /** Whole again: the half's modifiers come off. */
    private void becomeWhole() {
        core().setFlag(FLAG_HALF, false);
        core().setFlag(FLAG_RIGHT, false);
        for (Holder<Attribute> a : java.util.List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED)) {
            AttributeInstance instance = getAttribute(a);
            if (instance != null) instance.removeModifier(HALF_MODIFIER);
        }
        twinId = null;
        twin = null;
        made = false;
        kept = false;
        heavy = false;
        twinFell = false;
        carries = false;
        xpReward = XP;
        refreshDimensions();
    }

    private void modify(Holder<Attribute> attribute, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.addOrReplacePermanentModifier(new AttributeModifier(HALF_MODIFIER, amount, op));
    }

    private double baseOf(Holder<Attribute> attribute) {
        AttributeInstance instance = getAttribute(attribute);
        return instance == null ? 0.0 : instance.getBaseValue();
    }

    // ---- the halves: rejoining -------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && !isRemoved() && isAlive() && form() != Form.WHOLE) halfTick(level);
    }

    /**
     * A half's clock: {@link #REJOIN_AFTER} ticks after the split, with its twin alive, it sets off to rejoin (whatever
     * it was doing). A walk that hasn't met after {@link #REGROUP_MAX} ends, and the clock starts over. Without a twin
     * it fights on alone.
     */
    private void halfTick(ServerLevel level) {
        ShardlingColossus t = twin(level);
        if (t == null) {
            if (core().in(Step.REGROUP)) core().end();
            return;
        }
        if (core().in(Step.REGROUP)) {
            if (core().elapsed() >= REGROUP_MAX) {
                core().end();
                splitAt = level.getGameTime();
            } else if (form() == Form.LEFT && core().elapsed() % 3 == 0) {
                tether(level, t);
            }
        } else if (age() >= REJOIN_AFTER) {
            if (startPhase(Step.REGROUP, 0, null)) {
                getNavigation().stop();
                if (form() == Form.LEFT) {
                    Vec3 mid = position().add(t.position()).scale(0.5);
                    level.playSound(null, mid.x, mid.y + 1.0, mid.z, ModSounds.SHARDLING_COLOSSUS_REGROUP.get(), getSoundSource(), 1.3F, 1.0F);
                }
            } else {
                splitAt = level.getGameTime();                                 // vetoed: they try again later
            }
        }
    }

    /** A thread of lilac dust between the two halves while they walk together. */
    private void tether(ServerLevel level, ShardlingColossus t) {
        Vec3 a = position().add(0.0, getBbHeight() * 0.55, 0.0), b = t.position().add(0.0, t.getBbHeight() * 0.55, 0.0);
        int n = Math.max(2, (int) (a.distanceTo(b) * 3.0));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n);
            level.sendParticles(SEAM, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /**
     * Walking together: each half heads for the other; the first to find them close enough rejoins them. A path ends in
     * its twin's own block, and within {@link #STEER} they walk straight at each other's middle: a path counts as done a
     * block short, and two halves a block apart would stand there, too far to rejoin, until the walk ran out.
     */
    private void regroupTick(ServerLevel level) {
        if (form() == Form.WHOLE) {
            core().end();
            return;
        }
        ShardlingColossus t = twin(level);
        if (t == null) {
            core().end();
            return;
        }
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        if (dx * dx + dz * dz <= MEET * MEET && Math.abs(t.getY() - getY()) < 1.0) {
            if (form() == Form.LEFT) fuse(level, t);
            else t.fuse(level, this);
            return;
        }
        if (dx * dx + dz * dz <= STEER * STEER) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), REGROUP_SPEED);
        } else if (tickCount % 5 == 0 || getNavigation().isDone()) {
            getNavigation().moveTo(t, 0, REGROUP_SPEED);
        }
    }

    /**
     * The left half takes its twin back: the right half is gone (no death, no drops), and this one is the whole colossus
     * again, half way between where they stood if it fits there, with their health added up (to its maximum), fused, and
     * settling for {@link #FUSE_TICKS}. A vetoed "fuse" sends both back to fighting, to try again later. Public as a
     * test hook.
     */
    public boolean fuse(ServerLevel level, ShardlingColossus other) {
        if (form() != Form.LEFT || other.form() != Form.RIGHT || !isAlive() || !other.isAlive() || other.isRemoved()) return false;
        LivingEntity target = valid(getTarget()) ? getTarget() : valid(other.getTarget()) ? other.getTarget() : null;
        if (!core().start(Step.FUSE, FUSE_TICKS, target)) {
            core().end();
            other.core().end();
            splitAt = level.getGameTime();
            other.splitAt = splitAt;
            return false;
        }
        float sum = getHealth() + other.getHealth();
        Vec3 mid = position().add(other.position()).scale(0.5);
        if (other.isPersistenceRequired()) setPersistenceRequired();
        if (!hasCustomName() && other.hasCustomName()) setCustomName(other.getCustomName());
        other.discard();
        becomeWhole();
        fused = true;
        core().setFlag(FLAG_FUSED, true);
        setHealth(Math.min(getMaxHealth(), sum));
        AABB box = getBoundingBox().move(mid.x - getX(), 0.0, mid.z - getZ());
        if (level.noCollision(this, box)) setPos(mid.x, getY(), mid.z);
        if (target != null) setTarget(target);
        double y = getY() + getBbHeight() * 0.6;
        level.sendParticles(SEAM, getX(), y, getZ(), 30, 0.2, getBbHeight() * 0.3, 0.2, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, getX(), y, getZ(), 10, 0.3, getBbHeight() * 0.3, 0.3, 0.02);
        level.playSound(null, getX(), y, getZ(), ModSounds.SHARDLING_COLOSSUS_REJOIN.get(), getSoundSource(), 1.4F, 1.0F);
        return true;
    }

    /** A half's living twin, found again by id after a load (server only); null for the whole colossus or a lone half. */
    public @Nullable ShardlingColossus twin(ServerLevel level) {
        if (form() == Form.WHOLE) return null;
        if (twin != null && twin.isAlive() && !twin.isRemoved() && twin.form() != Form.WHOLE) return twin;
        twin = null;
        if (twinId != null && level.getEntity(twinId) instanceof ShardlingColossus c && c.isAlive() && c.form() != Form.WHOLE) twin = c;
        return twin;
    }

    /**
     * A half that dies while its twin lives: the counter landed, and the twin goes dark and fights on alone. Falling
     * first, it takes the drops from its twin. With its twin out of reach nothing passes: each keeps its own record.
     */
    @Override
    public void die(DamageSource source) {
        if (form() != Form.WHOLE && !isRemoved() && !dead && level() instanceof ServerLevel level) {
            ShardlingColossus t = twin(level);
            if (t != null) {
                carries = true;
                t.sever(level, source.getEntity() instanceof LivingEntity by ? by : null);
            }
            twinId = null;
            twin = null;
        }
        super.die(source);
    }

    /**
     * The colossus's drops once: the half that carries them drops them (the first to fall, or the left half if they fell
     * out of each other's reach); the other drops them again only after a heavy split, if it saw its twin fall and falls inside
     * {@link #REJOIN_AFTER} of the split (so both halves did).
     */
    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        if (form() != Form.WHOLE && !carries && !(twinFell && paysTwice(level))) return false;
        return super.shouldDropLoot(level);
    }

    /**
     * A half that carries the drops and is removed without dying (a despawn, a discard) while its twin is in reach
     * hands them to its twin, so they aren't lost. Unloading or changing dimension keeps them: the half comes back.
     */
    @Override
    public void onRemoval(RemovalReason reason) {
        if (reason == RemovalReason.DISCARDED && carries && form() != Form.WHOLE && level() instanceof ServerLevel level) {
            ShardlingColossus t = twin(level);
            if (t != null) t.carries = true;
            carries = false;
        }
        super.onRemoval(reason);
    }

    /** True for a half of a heavy split, still inside {@link #REJOIN_AFTER} of it. Public as a test hook. */
    public boolean paysTwice(ServerLevel level) {
        return form() != Form.WHOLE && heavy && level.getGameTime() - splitTime < REJOIN_AFTER;
    }

    /** True for a half whose split was a player's heavy hit. Public as a test hook. */
    public boolean heavy() {
        return heavy;
    }

    /** True for a half whose twin fell first, in reach, and dropped the colossus's drops. Public as a test hook. */
    public boolean twinFell() {
        return twinFell;
    }

    /** True for the half that carries the colossus's drops. Public as a test hook. */
    public boolean carries() {
        return carries;
    }

    /** Ticks since the split itself (never moved by a try at rejoining). Public as a test hook. */
    public long splitAge() {
        return level().getGameTime() - splitTime;
    }

    /**
     * Its twin is dead: it can't rejoin now, and its twin took the drops. Its seam's light gutters out in dark dust,
     * with a dull chime.
     */
    private void sever(ServerLevel level, @Nullable LivingEntity by) {
        twinId = null;
        twin = null;
        twinFell = true;
        carries = false;
        if (core().in(Step.REGROUP)) core().end();
        if (!MechanicEvent.post(this, "sever", by)) return;
        double y = getY() + getBbHeight() * 0.6;
        level.sendParticles(DARK, getX(), y, getZ(), 20, 0.15, getBbHeight() * 0.3, 0.15, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, getX(), y, getZ(), 10, 0.15, getBbHeight() * 0.3, 0.15, 0.01);
        level.playSound(null, getX(), y, getZ(), ModSounds.SHARDLING_COLOSSUS_SEVER.get(), getSoundSource(), 1.2F, 1.0F);
    }

    /**
     * The right half stays as long as the colossus it came from: kept from despawning if the colossus was kept (named,
     * or kept by whatever placed it) when it split, or is kept now. Otherwise both despawn like any monster.
     */
    @Override
    public boolean requiresCustomPersistence() {
        if (super.requiresCustomPersistence()) return true;
        if (!made) return false;
        if (kept) return true;
        ShardlingColossus maker = twin;
        return maker != null && maker.isAlive() && !maker.made && (maker.isPersistenceRequired() || maker.requiresCustomPersistence());
    }

    // ---- save --------------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Form", form().ordinal());
        output.putBoolean("Fused", fused);
        output.putInt("HalfAge", (int) Math.min(Integer.MAX_VALUE, age()));
        output.putBoolean("Made", made);
        output.putBoolean("Kept", kept);
        if (twinId != null) output.putString("Twin", twinId.toString());
        output.putInt("SlamWait", untilSlam);
        output.putBoolean("Heavy", heavy);
        output.putInt("SplitAge", (int) Math.clamp(level().getGameTime() - splitTime, 0L, Integer.MAX_VALUE));
        output.putBoolean("TwinFell", twinFell);
        output.putBoolean("Carries", carries);
    }

    /**
     * A half keeps its form, its twin's id and its clock (its scale and health share are saved modifiers), whether its
     * split was heavy and how long ago, whether its twin has fallen and whether it carries the drops (in a save without
     * that record, the left half carries them unless its twin fell); a fused colossus stays fused; the wait to the next
     * slam survives. A reload mid-tell, mid-walk or mid-split ends it.
     */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int f = Math.clamp(input.getIntOr("Form", 0), 0, Form.values().length - 1);
        core().setFlag(FLAG_HALF, f != Form.WHOLE.ordinal());
        core().setFlag(FLAG_RIGHT, f == Form.RIGHT.ordinal());
        fused = input.getBooleanOr("Fused", false);
        core().setFlag(FLAG_FUSED, fused);
        setAge(input.getIntOr("HalfAge", 0));
        made = input.getBooleanOr("Made", false);
        kept = input.getBooleanOr("Kept", false);
        try {
            String id = input.getStringOr("Twin", "");
            twinId = id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException badId) {
            twinId = null;
        }
        untilSlam = Math.max(0, input.getIntOr("SlamWait", 0));
        heavy = input.getBooleanOr("Heavy", false);
        splitTime = level().getGameTime() - Math.max(0, input.getIntOr("SplitAge", 0));
        twinFell = input.getBooleanOr("TwinFell", false);
        carries = input.getBooleanOr("Carries", f == Form.LEFT.ordinal() && !twinFell);
        xpReward = f == Form.WHOLE.ordinal() ? XP : HALF_XP;
        refreshDimensions();
    }

    // ---- helpers and test hooks --------------------------------------------------------------------------------------

    private Vec3 forward() {
        return Vec3.directionFromRotation(0.0F, yBodyRot);
    }

    /** Its own left, across the ground. */
    private Vec3 left() {
        Vec3 f = forward();
        return new Vec3(f.z, 0.0, -f.x);
    }

    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Ticks since a half's clock started: the split, or its last try at rejoining. Public as a test hook. */
    public long age() {
        return level().getGameTime() - splitAt;
    }

    /** Sets a half's ticks since the split. Public as a test hook. */
    public void setAge(int ticks) {
        splitAt = level().getGameTime() - Math.max(0, ticks);
    }

    /** Ticks until it may tell again. Public as a test hook. */
    public int untilSlam() {
        return untilSlam;
    }

    /** Lets it tell again at once. Public as a test hook. */
    public void resetSlam() {
        untilSlam = 0;
    }

    /** True for the right half, made at a split. Public as a test hook. */
    public boolean made() {
        return made;
    }

    /** Test hook: it no longer wanders; it still closes on its target, tells, slams and rejoins. */
    public void stayPut() {
        if (stroll != null) goalSelector.removeGoal(stroll);
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SHARDLING_COLOSSUS_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SHARDLING_COLOSSUS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SHARDLING_COLOSSUS_DEATH.get();
    }

    /** The halves sound higher than the whole colossus. */
    @Override
    public float getVoicePitch() {
        return (form() == Form.WHOLE ? 0.7F : 0.95F) + random.nextFloat() * 0.1F;
    }
}
