package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.TeemingAnimal;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.mob.overworld.VinedropStrangler;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Chorus Grazer: a placid, woolly grazer of the End highlands with a purpur fleece and chorus growing from its back. It
 * never attacks. Shears take 1 to 2 purpur fleece and leave it shorn, and only chorus fruit grows the fleece back: a
 * shorn grazer (or a calf) walks to a chorus fruit lying within 8 blocks and eats it, chewing for 30 ticks, and the
 * fleece is back when it swallows. A fruit fed by hand does the same for a shorn grazer, makes a fleeced adult fall in
 * love for 600 ticks, and takes a tenth off a calf's growing time. Two in love within 8 blocks come together and, after
 * 60 ticks side by side, bring a calf that grows up in 24000 ticks.
 *
 * <p>Startled (hurt by anything, or a player not in creative sprinting within 3 blocks), every grazer within 16 blocks
 * blinks away: each one alone, 4 to 8 blocks from where it stood and away from what startled it, onto safe ground (see
 * the blink section), with portal motes where it left and where it lands and a soft pop. A spot that isn't safe is
 * never taken; with none in reach it stays where it is. Each then stays skittish for 100 ticks: it shies from players
 * within 6 blocks and can't be sheared, fed or bred, and while skittish it doesn't blink again. The blink moves only the
 * grazer itself; nothing it does hurts or moves anyone else.
 *
 * <p>Counter: walk (or sneak) up and shear it calmly. Failing it costs a forced retreat: the herd scatters across the
 * island and stays wary for 5 seconds, out of reach of the shears.
 *
 * <p>The kit has no breeding for passive mobs, so the love, the calf and growing up live in their own section here, as
 * in the Woolcap Grazer; the kit has no blink yet, so the herd's blink and its safe-ground maths ({@link Blink}) live in
 * theirs.
 */
public class ChorusGrazer extends TeemingAnimal implements Shearable {
    /** IDLE, shaking off the shears, eating a chorus fruit, skittish after a blink. */
    public enum Step implements Phase { IDLE, SHEAR, EAT, SKITTISH }

    /** Shears take this many purpur fleece, evenly picked. */
    public static final int FLEECE_MIN = 1, FLEECE_MAX = 2;
    /** It shakes its bare back for 1 s after the shears. */
    public static final int SHEAR_TICKS = 20;
    /** Eating a chorus fruit: 1.5 s of chewing; the fleece is back when it swallows. */
    public static final int EAT_TICKS = 30;
    /** A hungry grazer (shorn, or a calf) walks to a chorus fruit lying this close. */
    public static final double FRUIT_RANGE = 8.0;
    /** It eats the fruit once its middle is this close to it, across the ground. */
    public static final double EAT_REACH = 1.5;

    /** Every grazer within 16 blocks of the startled one blinks. */
    public static final double HERD_RANGE = 16.0;
    /** Each blinks 4 to 8 blocks from where it stood. */
    public static final double BLINK_MIN = 4.0, BLINK_MAX = 8.0;
    /** After the blink it stays skittish for 5 s. */
    public static final int SKITTISH_TICKS = 100;
    /** A player sprinting this close startles it. */
    public static final double STARTLE_RANGE = 3.0;
    /** A skittish grazer shies from players this close, at 1.3 times its walking speed. */
    public static final double SHY_RANGE = 6.0;
    public static final double SHY_SPEED = 1.3;

    /** Chorus fruit fed by hand: an adult is in love for 30 s. */
    public static final int LOVE_TICKS = 600;
    /** A partner in love this close is sought out; this close they count as side by side. */
    public static final double MATE_RANGE = 8.0, MATE_TOUCH = 3.0;
    /** Side by side for this long, they bring a calf. */
    public static final int MATE_TICKS = 60;
    /** A parent rests this long before it can fall in love again. */
    public static final int BREED_COOLDOWN = 6000;
    /** A calf grows up in 20 minutes. */
    public static final int GROW_TICKS = 24000;
    /** A fruit fed by hand cuts a calf's remaining growing time by a tenth; one it finds and eats takes 1200 ticks off. */
    public static final double CALF_FEED_SHARE = 0.1;
    public static final int CALF_EAT_GROWTH = 1200;

    /** {@code MobCore} flag bits, synced to clients: the fleece is shorn; it is a calf. */
    public static final int SHORN_FLAG = 0, CALF_FLAG = 1;

    private static final Telegraph SHORN = Telegraph.particles(ParticleTypes.WITCH, 16, 0.5, 0.7).withSpeed(0.05);
    private static final Telegraph REGROWN = Telegraph.particles(ParticleTypes.HAPPY_VILLAGER, 8, 0.5, 0.85);
    private static final Telegraph HEARTS = Telegraph.particles(ParticleTypes.HEART, 3, 0.4, 1.0);

    /** Ticks since birth below 0 (a calf), ticks of rest after breeding above 0; saved. */
    private int age;
    private int love;
    private int sideBySide;
    private boolean wasCalf;
    private int blinks;

    public ChorusGrazer(EntityType<? extends ChorusGrazer> type, Level level) {
        super(type, level, Step.values());
        xpReward = 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // chorus fruit draws it from 10 blocks, as wheat does a sheep (the tempt goal reads this attribute)
        return TeemingAttributes.animal("chorus_grazer", 0.23).add(Attributes.TEMPT_RANGE, 10.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ShyGoal());
        goalSelector.addGoal(3, new TemptGoal(this, 1.1, s -> s.is(Items.CHORUS_FRUIT), false) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(4, new FruitGoal());
        goalSelector.addGoal(5, new FollowHerdGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0) {
            @Override
            public boolean canUse() {
                return core().idle() && love <= 0 && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** A natural herd shares one look: the first to spawn picks it and the rest wear it. */
    public record HerdData(String look) implements SpawnGroupData {}

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (data instanceof HerdData herd) {
            core().setLook(herd.look(), true);
            return herd;
        }
        return reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION ? new HerdData(look()) : result;
    }

    // ---- the fleece: shears, and chorus fruit to grow it back --------------------------------------------------------

    /** True once shorn, until it eats a chorus fruit (both sides). */
    public boolean shorn() {
        return core().flag(SHORN_FLAG);
    }

    /** Server: shears or regrows the fleece without the rest of the mechanic. Public as a test hook. */
    public void setShorn(boolean on) {
        core().setFlag(SHORN_FLAG, on);
    }

    /** True for the 100 ticks after a startle (both sides). */
    public boolean skittish() {
        return core().in(Step.SKITTISH);
    }

    /** Ready for the shears: a grown grazer with its fleece on, calm (not skittish), alive. Both sides. */
    @Override
    public boolean readyForShearing() {
        return isAlive() && !shorn() && !isBaby() && !skittish();
    }

    /**
     * Server: the shears take 1 to 2 purpur fleece, and it shakes its bare back for {@link #SHEAR_TICKS}. Called by a
     * player's shears and by a dispenser's. Nothing happens if a listener vetoes the "shear" step.
     */
    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears) {
        if (!readyForShearing() || !startPhase(Step.SHEAR, SHEAR_TICKS, null)) return;
        setShorn(true);
        getNavigation().stop();
        level.playSound(null, this, ModSounds.CHORUS_GRAZER_SHEAR.get(), source, 1.0F, 1.0F);
        SHORN.play(this);
        int count = FLEECE_MIN + random.nextInt(FLEECE_MAX - FLEECE_MIN + 1);
        for (int i = 0; i < count; i++) {
            ItemEntity drop = spawnAtLocation(level, new ItemStack(ModMaterials.PURPUR_FLEECE.get()), 1.0F);
            if (drop != null) drop.setDeltaMovement(drop.getDeltaMovement().add((random.nextFloat() - random.nextFloat()) * 0.1F,
                    random.nextFloat() * 0.05F, (random.nextFloat() - random.nextFloat()) * 0.1F));
        }
    }

    /** Hungry: shorn, or a calf. Only a hungry grazer goes after chorus fruit lying on the ground. */
    public boolean hungry() {
        return shorn() || isBaby();
    }

    /** The nearest chorus fruit lying within {@link #FRUIT_RANGE}, or null. Public as a test hook. */
    public @Nullable ItemEntity fruitInReach() {
        ItemEntity best = null;
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(FRUIT_RANGE),
                i -> i.isAlive() && i.getItem().is(Items.CHORUS_FRUIT) && i.distanceTo(this) <= FRUIT_RANGE)) {
            if (best == null || item.distanceToSqr(this) < best.distanceToSqr(this)) best = item;
        }
        return best;
    }

    /**
     * Server: eats one chorus fruit from {@code item} if it is hungry and calm: it chews for {@link #EAT_TICKS} and the
     * fleece comes back when it swallows. False (nothing eaten) otherwise, or when a listener vetoes the "eat" step.
     * Public as a test hook (the fruit goal calls it once the grazer reaches the fruit).
     */
    public boolean eat(ItemEntity item) {
        if (level().isClientSide() || !item.isAlive() || !item.getItem().is(Items.CHORUS_FRUIT)) return false;
        if (!startChewing()) return false;
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) item.discard();
        return true;
    }

    /** Starts the chew if it is hungry, calm and alive; false otherwise or when "eat" is vetoed. */
    private boolean startChewing() {
        if (!isAlive() || !core().idle() || !hungry() || !startPhase(Step.EAT, EAT_TICKS, null)) return false;
        getNavigation().stop();
        playSound(ModSounds.CHORUS_GRAZER_EAT.get(), 1.0F, getVoicePitch());
        return true;
    }

    /** It swallowed: the fleece grows back (a calf grows instead). */
    private void swallowed() {
        if (!MechanicEvent.post(this, "regrow", null)) return;
        if (shorn()) {
            setShorn(false);
            REGROWN.play(this);
            playSound(ModSounds.CHORUS_GRAZER_REGROW.get(), 1.0F, getVoicePitch());
        }
        if (isBaby()) ageUp(CALF_EAT_GROWTH);
    }

    /** A hungry grazer walks to a chorus fruit lying within {@link #FRUIT_RANGE} and eats it. */
    private final class FruitGoal extends Goal {
        private @Nullable ItemEntity fruit;
        private int repath;

        FruitGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!core().idle() || love > 0 || !hungry() || tickCount % 5 != 0) return false;
            fruit = fruitInReach();
            return fruit != null;
        }

        @Override
        public boolean canContinueToUse() {
            return fruit != null && fruit.isAlive() && core().idle() && hungry() && fruit.distanceTo(ChorusGrazer.this) <= FRUIT_RANGE + 2.0;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (fruit == null) return;
            getLookControl().setLookAt(fruit, 30.0F, 30.0F);
            double dx = fruit.getX() - getX(), dz = fruit.getZ() - getZ();
            if (dx * dx + dz * dz <= EAT_REACH * EAT_REACH && Math.abs(fruit.getY() - getY()) < 1.5) {
                eat(fruit);
                fruit = null;
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                getNavigation().moveTo(fruit, 1.0);
            }
        }

        @Override
        public void stop() {
            fruit = null;
            getNavigation().stop();
        }
    }

    // ---- the herd's blink --------------------------------------------------------------------------------------------

    /** Any hurt that lands startles it, and with it the herd. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) {
            Entity by = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
            startle(level, by, by != null ? by.position() : source.getSourcePosition());
        }
        return hurt;
    }

    /**
     * Server: every grazer within {@link #HERD_RANGE} of this one (itself too) that isn't skittish already blinks away
     * from {@code from} (a random way when null) and turns skittish for {@link #SKITTISH_TICKS}. Returns how many did.
     * Public as a test hook.
     */
    public int startle(ServerLevel level, @Nullable Entity by, @Nullable Vec3 from) {
        LivingEntity threat = by instanceof LivingEntity l ? l : null;
        int set = 0;
        List<ChorusGrazer> herd = level.getEntitiesOfClass(ChorusGrazer.class, getBoundingBox().inflate(HERD_RANGE),
                g -> g.isAlive() && !g.skittish() && g.distanceTo(this) <= HERD_RANGE);
        for (ChorusGrazer g : herd) if (g.flee(level, from, threat)) set++;
        return set;
    }

    /** Turns skittish and, unless a listener vetoes the "blink" step, blinks; false if "skittish" is vetoed. */
    private boolean flee(ServerLevel level, @Nullable Vec3 from, @Nullable LivingEntity threat) {
        boolean chewing = core().in(Step.EAT);
        if (!startPhase(Step.SKITTISH, SKITTISH_TICKS, threat)) return false;
        if (chewing) spawnAtLocation(level, new ItemStack(Items.CHORUS_FRUIT), 0.5F);   // startled mid-meal, it drops the fruit
        love = 0;
        sideBySide = 0;
        getNavigation().stop();
        if (MechanicEvent.post(this, "blink", threat)) blink(level, from);
        return true;
    }

    /** Blinks of this grazer so far (landed ones). Public as a test hook. */
    public int blinks() {
        return blinks;
    }

    /** One blink: 4 to 8 blocks (the length picked evenly), to the first safe spot away from {@code from}; stays put with none. */
    private void blink(ServerLevel level, @Nullable Vec3 from) {
        double length = BLINK_MIN + random.nextDouble() * (BLINK_MAX - BLINK_MIN);
        Vec3 to = findBlink(level, from, length);
        Vec3 at = position();
        motes(level, at, true);
        if (to == null) return;
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
        float yaw = (float) (Mth.atan2(to.z - at.z, to.x - at.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        blinks++;
        motes(level, to, false);
    }

    /** Portal motes and a soft pop where it leaves ({@code leaving}) and where it lands, with a star-white glint. */
    private void motes(ServerLevel level, Vec3 at, boolean leaving) {
        double y = at.y + getBbHeight() * 0.5;
        level.sendParticles(leaving ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL, at.x, y, at.z, leaving ? 32 : 20,
                getBbWidth() * 0.45, getBbHeight() * 0.4, getBbWidth() * 0.45, leaving ? 0.4 : 0.03);
        level.sendParticles(ParticleTypes.END_ROD, at.x, y, at.z, 3, 0.3, 0.3, 0.3, 0.01);
        level.playSound(null, at.x, y, at.z, ModSounds.CHORUS_GRAZER_BLINK.get(), getSoundSource(), leaving ? 0.8F : 0.6F,
                (leaving ? 1.0F : 1.2F) + (random.nextFloat() - 0.5F) * 0.1F);
    }

    /**
     * Where a blink of {@code length} away from {@code from} lands: the first safe spot trying straight away from it,
     * then 30 degrees further round each side in turn up to 90, at that length, then each whole block nearer down to
     * {@link #BLINK_MIN}, then each further up to {@link #BLINK_MAX}; only then the same in the half facing back. A spot is the middle of a block, 4 to 8 blocks
     * across the ground from where it stands. Null when nothing in reach is safe. Public as a test hook.
     */
    public @Nullable Vec3 findBlink(ServerLevel level, @Nullable Vec3 from, double length) {
        double ax = from == null ? 0.0 : getX() - from.x, az = from == null ? 0.0 : getZ() - from.z;
        double away = ax * ax + az * az < 1.0E-4 ? random.nextDouble() * Math.PI * 2.0 : Math.atan2(az, ax);
        int headroom = Math.max(1, Mth.ceil(getBbHeight()));
        // every length in the half facing away first: a calm herd scatters away from a threat, never back past it
        for (boolean ahead : new boolean[] {true, false}) {
            for (double d : Blink.distances(length, BLINK_MIN, BLINK_MAX)) {
                for (int offset : Blink.OFFSETS) {
                    if ((Math.abs(offset) <= Blink.AHEAD) != ahead) continue;
                    double a = away + Math.toRadians(offset);
                    Vec3 spot = landing(level, getX() + Math.cos(a) * d, getZ() + Math.sin(a) * d, headroom);
                    if (spot == null) continue;
                    double went = Math.hypot(spot.x - getX(), spot.z - getZ());
                    if (went >= BLINK_MIN && went <= BLINK_MAX) return spot;
                }
            }
        }
        return null;
    }

    /**
     * The spot to stand on in the block at (x, z), near its own height, or null when it isn't safe: every block column
     * its hitbox covers needs solid ground at the same height with open air above it (so it never stands half over an
     * island's edge), and nothing in or under its hitbox may burn, prick or drown it.
     */
    private @Nullable Vec3 landing(ServerLevel level, double x, double z, int headroom) {
        BlockPos base = BlockPos.containing(x, getY() + 0.01, z);
        if (!level.hasChunkAt(base) || !level.getWorldBorder().isWithinBounds(base)) return null;
        double cx = base.getX() + 0.5, cz = base.getZ() + 0.5;
        double half = getBbWidth() / 2.0;
        int x0 = Mth.floor(cx - half), x1 = Mth.floor(cx + half), z0 = Mth.floor(cz - half), z1 = Mth.floor(cz + half);
        List<IntUnaryOperator> columns = new ArrayList<>();
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                BlockPos column = new BlockPos(bx, base.getY(), bz);
                if (!level.hasChunkAt(column)) return null;
                columns.add(d -> cell(level, column.above(d)));
            }
        }
        int dy = Blink.landing(columns.toArray(IntUnaryOperator[]::new), headroom);
        if (dy == Blink.NONE) return null;
        BlockPos floor = base.above(dy - 1);
        double top = floor.getY() + level.getBlockState(floor).getCollisionShape(level, floor).max(Direction.Axis.Y);
        Vec3 at = new Vec3(cx, top, cz);
        AABB box = getDimensions(Pose.STANDING).makeBoundingBox(at);
        if (!level.noCollision(this, box) || level.containsAnyLiquid(box)) return null;
        return at;
    }

    /** What one cell of a column is, for {@link Blink#landing}. */
    private static int cell(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY()) return Blink.VOID;
        if (level.isOutsideBuildHeight(pos)) return Blink.AIR;
        FluidState fluid = level.getFluidState(pos);
        if (fluid.is(FluidTags.LAVA)) return Blink.LAVA;
        if (!fluid.isEmpty()) return Blink.LIQUID;
        BlockState state = level.getBlockState(pos);
        if (hazard(state)) return Blink.HAZARD;
        return state.getCollisionShape(level, pos).isEmpty() ? Blink.AIR : Blink.SOLID;
    }

    /** Blocks that hurt whoever stands in or on them. */
    private static boolean hazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.LAVA);
    }

    /**
     * The safe-ground maths of a blink, with no game types so it can be checked on its own. Each block column under the
     * hitbox is read as cells by their height above the feet's starting cell. A blink lands at a height where every
     * column has solid ground just below and open air for the whole body: never in a fluid, never on or in a hazard,
     * never over a gap, and never more than {@link #MAX_DROP} below or {@link #MAX_RISE} above where it stood, so the
     * void (which has no ground within reach) is never a landing.
     */
    public static final class Blink {
        public static final int AIR = 0;
        public static final int SOLID = 1;
        /** Water and other fluids: neither ground nor room to stand. */
        public static final int LIQUID = 2;
        public static final int LAVA = 3;
        /** Fire, magma, cactus, berry bushes, powder snow: never stood in or on. */
        public static final int HAZARD = 4;
        /** Below the bottom of the world. */
        public static final int VOID = 5;
        /** {@link #landing} found nowhere to stand. */
        public static final int NONE = Integer.MIN_VALUE;
        /** It lands at most this many blocks above its own height, and at most this many below. */
        public static final int MAX_RISE = 2;
        public static final int MAX_DROP = 4;
        /** The directions it tries, in degrees round from straight away from what startled it, in order. */
        public static final int[] OFFSETS = {0, 30, -30, 60, -60, 90, -90, 120, -120, 150, -150, 180};
        /** Directions within this many degrees of straight away are the half facing away, tried at every length first. */
        public static final int AHEAD = 90;

        private Blink() {}

        /** The heights it tries, nearest its own first: 0, -1, 1, -2, 2, -3, -4. */
        public static int[] heights() {
            int[] out = new int[MAX_RISE + MAX_DROP + 1];
            int n = 0;
            out[n++] = 0;
            for (int d = 1; d <= Math.max(MAX_RISE, MAX_DROP); d++) {
                if (d <= MAX_DROP) out[n++] = -d;
                if (d <= MAX_RISE) out[n++] = d;
            }
            return out;
        }

        /** True when every column has solid ground in cell {@code dy - 1} and air from {@code dy} for {@code headroom} cells. */
        public static boolean standable(IntUnaryOperator[] columns, int dy, int headroom) {
            if (columns.length == 0) return false;
            for (IntUnaryOperator cell : columns) {
                if (cell.applyAsInt(dy - 1) != SOLID) return false;
                for (int h = 0; h < headroom; h++) if (cell.applyAsInt(dy + h) != AIR) return false;
            }
            return true;
        }

        /** The height of the feet cell to land in, nearest its own first, or {@link #NONE}. */
        public static int landing(IntUnaryOperator[] columns, int headroom) {
            for (int dy : heights()) if (standable(columns, dy, headroom)) return dy;
            return NONE;
        }

        /**
         * The lengths it tries: {@code length}, then a block shorter each time down to {@code min}, then a block longer
         * each time up to {@code max}. Every one lies within [min, max].
         */
        public static double[] distances(double length, double min, double max) {
            double start = Math.clamp(length, min, max);
            List<Double> out = new ArrayList<>();
            for (double d = start; d >= min - 1.0E-9; d -= 1.0) out.add(d);
            for (double d = start + 1.0; d <= max + 1.0E-9; d += 1.0) out.add(d);
            double[] arr = new double[out.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
            return arr;
        }
    }

    /** A skittish grazer shies from players (not in creative) within {@link #SHY_RANGE}, and from whatever hurt it. */
    private final class ShyGoal extends Goal {
        private int repath;

        ShyGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private @Nullable Vec3 threat() {
            Entity near = level().getNearestPlayer(getX(), getY(), getZ(), SHY_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
            if (near != null) return near.position();
            LivingEntity hurtBy = getLastHurtByMob();
            return hurtBy != null && hurtBy.isAlive() && hurtBy.distanceTo(ChorusGrazer.this) <= SHY_RANGE ? hurtBy.position() : null;
        }

        @Override
        public boolean canUse() {
            return skittish() && threat() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return skittish() && (!getNavigation().isDone() || threat() != null);
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (--repath > 0 && !getNavigation().isDone()) return;
            repath = 10;
            Vec3 from = threat();
            if (from == null) return;
            Vec3 away = DefaultRandomPos.getPosAway(ChorusGrazer.this, 8, 4, from);
            if (away == null) away = DefaultRandomPos.getPosAway(ChorusGrazer.this, 4, 2, from);   // cornered: a short shy beats none
            if (away != null) getNavigation().moveTo(away.x, away.y, away.z, SHY_SPEED);
        }
    }

    // ---- love, calves and growing up ---------------------------------------------------------------------------------

    /** A calf until it grows up (both sides, from the synced flag): half size, no shears, no loot. */
    @Override
    public boolean isBaby() {
        return core() != null && core().flag(CALF_FLAG);   // null while the Entity constructor sizes it
    }

    /** Below 0: ticks until a calf grows up; above 0: ticks before a parent can fall in love again (server side). */
    public int age() {
        return age;
    }

    /** Sets the age (below 0 makes it a calf, 0 or more grown). Public as a test hook. */
    public void setAge(int ticks) {
        age = ticks;
        boolean calf = ticks < 0;
        if (calf != core().flag(CALF_FLAG)) {
            core().setFlag(CALF_FLAG, calf);
            refreshDimensions();
        }
    }

    private void ageUp(int ticks) {
        if (age < 0) setAge(Math.min(0, age + ticks));
    }

    /** Ticks of love left (server side). */
    public int love() {
        return love;
    }

    /** True when chorus fruit by hand would make it fall in love now: grown, fleeced, rested, calm, not in love already. */
    public boolean canFallInLove() {
        return isAlive() && age == 0 && love <= 0 && !shorn() && core().idle();
    }

    /**
     * Server: eats one of {@code food} (chorus fruit) from a player's hand. A calf grows a tenth of its remaining time; a
     * shorn grazer chews it and grows its fleece back; a fleeced adult falls in love for {@link #LOVE_TICKS}. Returns
     * false (nothing eaten) when it is skittish, none of these applies, or a listener vetoes the step. Public as a test
     * hook.
     */
    public boolean feed(Player player, ItemStack food) {
        if (level().isClientSide() || !food.is(Items.CHORUS_FRUIT) || skittish()) return false;
        if (isBaby()) {
            food.consume(1, player);
            ageUp((int) Math.ceil(-age * CALF_FEED_SHARE));
            playSound(ModSounds.CHORUS_GRAZER_FED.get(), 1.0F, getVoicePitch());
            REGROWN.play(this);
            return true;
        }
        if (shorn()) {
            if (!startChewing()) return false;
            food.consume(1, player);
            return true;
        }
        if (!canFallInLove() || !MechanicEvent.post(this, "love", player)) return false;
        food.consume(1, player);
        love = LOVE_TICKS;
        sideBySide = 0;
        playSound(ModSounds.CHORUS_GRAZER_FED.get(), 1.0F, getVoicePitch());
        HEARTS.play(this);
        return true;
    }

    /** Server: seeks out a partner in love and, side by side for {@link #MATE_TICKS}, brings a calf. */
    private void loveTick(ServerLevel level) {
        if (love <= 0 || !core().idle() || isBaby()) return;
        ChorusGrazer mate = mate(level);
        if (mate == null) {
            sideBySide = 0;
            return;
        }
        getLookControl().setLookAt(mate, 10.0F, getMaxHeadXRot());
        if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(mate, 1.0);
        if (distanceToSqr(mate) < MATE_TOUCH * MATE_TOUCH && ++sideBySide >= MATE_TICKS) breed(level, mate);
    }

    /** The nearest other grown grazer in love within {@link #MATE_RANGE}, calm, or null. */
    private @Nullable ChorusGrazer mate(ServerLevel level) {
        ChorusGrazer best = null;
        for (ChorusGrazer g : level.getEntitiesOfClass(ChorusGrazer.class, getBoundingBox().inflate(MATE_RANGE),
                g -> g != this && g.isAlive() && g.love > 0 && !g.isBaby() && g.core().idle() && g.distanceTo(this) <= MATE_RANGE)) {
            if (best == null || g.distanceToSqr(this) < best.distanceToSqr(this)) best = g;
        }
        return best;
    }

    /** A calf is born beside this parent, wearing one parent's look; both parents rest {@link #BREED_COOLDOWN}. */
    private void breed(ServerLevel level, ChorusGrazer mate) {
        if (!MechanicEvent.post(this, "breed", mate)) {
            sideBySide = 0;
            return;
        }
        @SuppressWarnings("unchecked")
        EntityType<ChorusGrazer> type = (EntityType<ChorusGrazer>) getType();
        ChorusGrazer calf = type.create(level, EntitySpawnReason.BREEDING);
        for (ChorusGrazer parent : new ChorusGrazer[] {this, mate}) {
            parent.love = 0;
            parent.sideBySide = 0;
            parent.age = BREED_COOLDOWN;
        }
        if (calf == null) return;
        calf.core().setLook(random.nextBoolean() ? look() : mate.look(), true);
        calf.setAge(-GROW_TICKS);
        calf.snapTo(getX(), getY(), getZ(), random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(calf);
        HEARTS.play(calf);
        HEARTS.play(mate);
        ExperienceOrb.award(level, position(), 1 + random.nextInt(7));
    }

    /** A calf keeps near the herd's grown grazers. */
    private final class FollowHerdGoal extends Goal {
        private @Nullable ChorusGrazer parent;
        private int repath;

        FollowHerdGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!isBaby() || !core().idle() || tickCount % 10 != 0) return false;
            parent = null;
            for (ChorusGrazer g : level().getEntitiesOfClass(ChorusGrazer.class, getBoundingBox().inflate(8.0, 4.0, 8.0), g -> !g.isBaby() && g.isAlive())) {
                if (parent == null || g.distanceToSqr(ChorusGrazer.this) < parent.distanceToSqr(ChorusGrazer.this)) parent = g;
            }
            return parent != null && parent.distanceToSqr(ChorusGrazer.this) > 9.0;
        }

        @Override
        public boolean canContinueToUse() {
            return parent != null && parent.isAlive() && core().idle() && isBaby() && parent.distanceToSqr(ChorusGrazer.this) > 4.0
                    && parent.distanceToSqr(ChorusGrazer.this) < 256.0;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (parent != null && --repath <= 0) {
                repath = 10;
                getNavigation().moveTo(parent, 1.1);
            }
        }

        @Override
        public void stop() {
            parent = null;
        }
    }

    // ---- interaction, ticking ----------------------------------------------------------------------------------------

    /** Shears shear it when it is ready; chorus fruit feeds it; anything else does what it does to any mob. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (VinedropStrangler.shears(held) && readyForShearing()) {
            if (level() instanceof ServerLevel level) {
                shear(level, SoundSource.PLAYERS, held);
                if (!shorn()) return InteractionResult.PASS;         // vetoed: nothing taken, the shears untouched
                gameEvent(GameEvent.SHEAR, player);
                held.hurtAndBreak(1, player, hand.asEquipmentSlot());
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.CHORUS_FRUIT)) {
            if (level().isClientSide()) return InteractionResult.CONSUME;
            return feed(player, held) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
        }
        return super.mobInteract(player, hand);
    }

    /** Ageing and love count down whatever it is doing, as vanilla animals' do; the size follows the calf flag. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel) {
            if (age < 0) setAge(age + 1);
            else if (age > 0) age--;
            if (love > 0) {
                love--;
                HEARTS.trail(this, 10);
            }
        }
        boolean calf = isBaby();
        if (calf != wasCalf) {
            wasCalf = calf;
            refreshDimensions();
        }
    }

    /** The first player (not in creative) sprinting within {@link #STARTLE_RANGE}, or null. */
    private @Nullable ServerPlayer sprinter(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && p.isSprinting() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) && p.distanceToSqr(this) <= STARTLE_RANGE * STARTLE_RANGE) return p;
        }
        return null;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!skittish() && isAlive()) {
            ServerPlayer sprinting = sprinter(level);
            if (sprinting != null) startle(level, sprinting, sprinting.position());
        }
        if (core().in(Step.EAT) || core().in(Step.SHEAR)) {
            getNavigation().stop();
            getMoveControl().setWait();
            if (core().in(Step.EAT) && core().elapsed() % 6 == 3) {
                Vec3 mouth = position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(getBbWidth() * 0.75));
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.CHORUS_FRUIT), mouth.x, getY() + getBbHeight() * 0.3, mouth.z,
                        3, 0.08, 0.05, 0.08, 0.05);
            }
            if (core().in(Step.EAT) && core().elapsed() == EAT_TICKS / 2) playSound(ModSounds.CHORUS_GRAZER_EAT.get(), 0.7F, getVoicePitch());
        } else if (core().idle()) {
            loveTick(level);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        core().end();
        if (ended == Step.EAT) swallowed();
    }

    // ---- save and load ---------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Shorn", shorn());
        output.putInt("Age", age);
    }

    /** The fleece and the age survive a reload; love and a skittish spell in progress don't, as a reload ends any mechanic. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setShorn(input.getBooleanOr("Shorn", false));
        setAge(input.getIntOr("Age", 0));
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return skittish() ? null : ModSounds.CHORUS_GRAZER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.CHORUS_GRAZER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.CHORUS_GRAZER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return (isBaby() ? 1.4F : 1.0F) + (random.nextFloat() - random.nextFloat()) * 0.1F;
    }
}
