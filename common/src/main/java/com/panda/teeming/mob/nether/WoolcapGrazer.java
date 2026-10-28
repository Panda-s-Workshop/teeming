package com.panda.teeming.mob.nether;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAnimal;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.Telegraph;
import com.panda.teeming.registry.ModMaterials;
import com.panda.teeming.registry.ModSounds;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Woolcap Grazer: a shaggy, sheep-sized grazer of the warped forests whose fleece is a cap of warped fungus. It never
 * attacks. Shears take 1 to 2 fungal fleece and leave it bare; it grows the cap back by grazing on nylium (40 ticks
 * with its head down), and grazing never changes the block. Warped roots make an adult fall in love for 600 ticks; two
 * in love within 8 blocks come together and, after 60 ticks side by side, bring a calf that grows up in 24000 ticks.
 *
 * <p>Hurt one, and every grazer within 16 blocks of it stampedes for 100 ticks. The herd runs as one, first straight
 * away from whatever hurt it, then wheeling round every 25 ticks to sweep back across where the stampede began, never
 * more than 8 blocks out. Whatever a stampeding grazer runs into (a mob, or a player not in creative) is knocked back
 * 0.6: sent off at 0.6 blocks a tick, horizontally away from the grazer, with a 0.4 hop, the push vanilla's knockback of
 * strength 0.6 gives a standing target (less by the target's knockback resistance). The shove does no damage. A
 * stampeding grazer can't be sheared, bred or fed.
 *
 * <p>Counter: shear calmly. Failing it: hit one in a crowd, and the herd tramples back and forth over you.
 *
 * <p>The kit has no breeding for passive mobs, so the love, the calf and growing up live in their own section here
 * (an age counter as vanilla's ageable mobs keep it: below 0 a calf growing up, above 0 a parent resting).
 */
public class WoolcapGrazer extends TeemingAnimal implements Shearable {
    /** IDLE, shaking off the shears, grazing on nylium, the stampede. */
    public enum Step implements Phase { IDLE, SHEAR, GRAZE, STAMPEDE }

    /** Shears take this many fungal fleece, evenly picked. */
    public static final int FLEECE_MIN = 1, FLEECE_MAX = 2;
    /** It shakes its bare back for 1 s after the shears. */
    public static final int SHEAR_TICKS = 20;
    /** Grazing: 2 s with its head down on nylium; the cap is back at the end. */
    public static final int GRAZE_TICKS = 40;
    /** The chance it starts grazing on a goal check (about every other tick): 1 in this many. */
    public static final int GRAZE_CHANCE_BARE = 200, GRAZE_CHANCE_CALF = 50, GRAZE_CHANCE_FULL = 1000;

    /** The stampede: 5 s, every grazer within 16 blocks of the hurt one. */
    public static final int STAMPEDE_TICKS = 100;
    public static final double HERD_RANGE = 16.0;
    /** Knockback strength of a stampeding grazer's shove: the speed it sends a body off at, in blocks a tick. */
    public static final double KNOCKBACK = 0.6;
    /** The hop that goes with the shove, in blocks a tick upwards: vanilla knockback's own. */
    public static final double KNOCKBACK_LIFT = 0.4;
    /** One grazer shoves the same body at most once in this many ticks. */
    public static final int BUMP_COOLDOWN = 10;
    /** The stampede runs at 1.6 times its walking speed. */
    public static final double STAMPEDE_SPEED = 1.6;
    /** The herd sweeps no farther than this from where the stampede began. */
    public static final double STAMPEDE_REACH = 8.0;
    /** The herd wheels round to a new heading every 25 ticks. */
    public static final int STAMPEDE_TURN = 25;

    /** Warped roots: an adult is in love for 30 s. */
    public static final int LOVE_TICKS = 600;
    /** A partner in love this close is sought out; this close they count as side by side. */
    public static final double MATE_RANGE = 8.0, MATE_TOUCH = 3.0;
    /** Side by side for this long, they bring a calf. */
    public static final int MATE_TICKS = 60;
    /** A parent rests this long before it can fall in love again. */
    public static final int BREED_COOLDOWN = 6000;
    /** A calf grows up in 20 minutes. */
    public static final int GROW_TICKS = 24000;
    /** Warped roots cut a calf's remaining growing time by a tenth; a graze takes 1200 ticks off it. */
    public static final double CALF_FEED_SHARE = 0.1;
    public static final int CALF_GRAZE_GROWTH = 1200;

    /** {@code MobCore} flag bits, synced to clients: the cap is shorn; it is a calf. */
    public static final int SHORN_FLAG = 0, CALF_FLAG = 1;

    private static final Telegraph SHORN = Telegraph.particles(ParticleTypes.WARPED_SPORE, 24, 0.5, 0.7).withSpeed(0.05);
    private static final Telegraph REGROWN = Telegraph.particles(ParticleTypes.HAPPY_VILLAGER, 8, 0.45, 0.8);
    private static final Telegraph HEARTS = Telegraph.particles(ParticleTypes.HEART, 3, 0.4, 1.0);
    private static final Telegraph BOLT = Telegraph.particles(ParticleTypes.POOF, 10, 0.5, 0.5).withSpeed(0.05);
    private static final Telegraph BUMP = Telegraph.particles(ParticleTypes.CRIT, 8, 0.3, 0.6).withSpeed(0.2);

    /** Ticks since birth below 0 (a calf), ticks of rest after breeding above 0; saved. */
    private int age;
    private int love;
    private int sideBySide;
    private @Nullable Rush rush;
    private final Map<UUID, Long> bumped = new HashMap<>();
    private int bumps;
    private boolean wasCalf;

    public WoolcapGrazer(EntityType<? extends WoolcapGrazer> type, Level level) {
        super(type, level, Step.values());
        xpReward = 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // warped roots draw it from 10 blocks, as wheat does a sheep (the tempt goal reads this attribute)
        return TeemingAttributes.animal("woolcap_grazer", 0.23).add(Attributes.TEMPT_RANGE, 10.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new TemptGoal(this, 1.1, s -> s.is(Items.WARPED_ROOTS), false) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(4, new FollowHerdGoal());
        goalSelector.addGoal(5, new GrazeGoal());
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

    // ---- the fleece: shears, and grazing it back on nylium --------------------------------------------------------

    /** True once shorn, until it grazes the cap back (both sides). */
    public boolean shorn() {
        return core().flag(SHORN_FLAG);
    }

    /** Server: shears or regrows the cap without the rest of the mechanic. Public as a test hook. */
    public void setShorn(boolean on) {
        core().setFlag(SHORN_FLAG, on);
    }

    /** Ready for the shears: a grown grazer with its cap on, calm (not stampeding), alive. Both sides. */
    @Override
    public boolean readyForShearing() {
        return isAlive() && !shorn() && !isBaby() && !core().in(Step.STAMPEDE);
    }

    /**
     * Server: the shears take 1 to 2 fungal fleece, and it shakes its bare back for {@link #SHEAR_TICKS}. Called by a
     * player's shears and by a dispenser's. Nothing happens if a listener vetoes the "shear" step.
     */
    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears) {
        if (!readyForShearing() || !startPhase(Step.SHEAR, SHEAR_TICKS, null)) return;
        setShorn(true);
        getNavigation().stop();
        level.playSound(null, this, ModSounds.WOOLCAP_GRAZER_SHEAR.get(), source, 1.0F, 1.0F);
        SHORN.play(this);
        int count = FLEECE_MIN + random.nextInt(FLEECE_MAX - FLEECE_MIN + 1);
        for (int i = 0; i < count; i++) {
            ItemEntity drop = spawnAtLocation(level, new ItemStack(ModMaterials.FUNGAL_FLEECE.get()), 1.0F);
            if (drop != null) drop.setDeltaMovement(drop.getDeltaMovement().add((random.nextFloat() - random.nextFloat()) * 0.1F,
                    random.nextFloat() * 0.05F, (random.nextFloat() - random.nextFloat()) * 0.1F));
        }
    }

    /** True when it stands on nylium (warped or crimson), the only ground it grazes. */
    public boolean onNylium() {
        return level().getBlockState(blockPosition().below()).is(BlockTags.NYLIUM);
    }

    /**
     * Server: starts grazing if it is idle on nylium; false otherwise, or when a listener vetoes the "graze" step.
     * Public as a test hook (the graze goal calls it now and then on its own).
     */
    public boolean graze() {
        if (level().isClientSide() || !isAlive() || !core().idle() || !onNylium()) return false;
        if (!startPhase(Step.GRAZE, GRAZE_TICKS, null)) return false;
        getNavigation().stop();
        playSound(ModSounds.WOOLCAP_GRAZER_GRAZE.get(), 0.8F, getVoicePitch());
        return true;
    }

    /** The graze is done: if it still stands on nylium, the cap grows back (and a calf grows); the block stays as it was. */
    private void grazed() {
        if (!onNylium() || !MechanicEvent.post(this, "regrow", null)) return;
        if (shorn()) {
            setShorn(false);
            REGROWN.play(this);
        }
        if (isBaby()) ageUp(CALF_GRAZE_GROWTH);
    }

    /** Now and then, idle on nylium, it lowers its head to graze: often while bare, rarely with its cap on. */
    private final class GrazeGoal extends Goal {
        GrazeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            int chance = isBaby() ? GRAZE_CHANCE_CALF : shorn() ? GRAZE_CHANCE_BARE : GRAZE_CHANCE_FULL;
            return core().idle() && love <= 0 && random.nextInt(chance) == 0 && onNylium() && graze();
        }

        @Override
        public boolean canContinueToUse() {
            return core().in(Step.GRAZE);
        }
    }

    // ---- the stampede ------------------------------------------------------------------------------------------------

    /**
     * One stampede, shared by every grazer it set running: where it began, the first heading (radians, away from the
     * threat) and when it began. The heading wheels round every {@link #STAMPEDE_TURN} ticks by a turn every member
     * works out the same from the seed, so the herd keeps running as one without talking.
     */
    public record Rush(Vec3 centre, double heading, long seed, long start) {
        /** The heading after {@code segment} turns: each a turn of 110 to 160 degrees, to the left or right. */
        public double heading(int segment) {
            double a = heading;
            for (int k = 1; k <= segment; k++) {
                long h = mix(seed + k * 0x9E3779B97F4A7C15L);
                double turn = Math.toRadians(110.0 + (h >>> 11) % 51);
                a += (h & 1) == 0 ? turn : -turn;
            }
            return a;
        }

        /** Where the herd is running at game time {@code now}: {@link #STAMPEDE_REACH} out along the current heading. */
        public Vec3 goal(long now) {
            double a = heading((int) Math.max(0, (now - start) / STAMPEDE_TURN));
            return centre.add(Math.cos(a) * STAMPEDE_REACH, 0.0, Math.sin(a) * STAMPEDE_REACH);
        }

        private static long mix(long z) {
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
    }

    /** Any hurt that lands sets the herd running. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) scare(level, source);
        return hurt;
    }

    /**
     * Server: every grazer within {@link #HERD_RANGE} of this one (itself too) stampedes for {@link #STAMPEDE_TICKS},
     * first away from what hurt it (or from where the hurt came from; a random way with neither). Returns how many
     * set off. Public as a test hook.
     */
    public int scare(ServerLevel level, @Nullable DamageSource source) {
        Entity by = source == null ? null : source.getEntity();
        Vec3 from = by != null ? by.position() : source != null ? source.getSourcePosition() : null;
        Vec3 away = from == null ? Vec3.ZERO : position().subtract(from).multiply(1, 0, 1);
        double heading = away.lengthSqr() < 1.0E-4 ? random.nextDouble() * Math.PI * 2.0 : Math.atan2(away.z, away.x);
        Rush run = new Rush(position(), heading, random.nextLong(), level.getGameTime());
        int set = 0;
        List<WoolcapGrazer> herd = level.getEntitiesOfClass(WoolcapGrazer.class, getBoundingBox().inflate(HERD_RANGE),
                g -> g.isAlive() && g.distanceTo(this) <= HERD_RANGE);
        for (WoolcapGrazer g : herd) if (g.stampede(run, by instanceof LivingEntity l ? l : null)) set++;
        if (set > 0) level.playSound(null, getX(), getY(), getZ(), ModSounds.WOOLCAP_GRAZER_STAMPEDE.get(), getSoundSource(), 1.5F, getVoicePitch());
        return set;
    }

    /** Joins stampede {@code run}; false if a listener vetoes the "stampede" step. */
    private boolean stampede(Rush run, @Nullable LivingEntity threat) {
        if (!startPhase(Step.STAMPEDE, STAMPEDE_TICKS, threat)) return false;
        rush = run;
        love = 0;
        sideBySide = 0;
        bumped.clear();
        getNavigation().stop();
        BOLT.play(this);
        return true;
    }

    /** The stampede it runs in, or null (server side). */
    public @Nullable Rush rush() {
        return core().in(Step.STAMPEDE) ? rush : null;
    }

    /** Shoves this grazer has landed so far. Public as a test hook. */
    public int bumps() {
        return bumps;
    }

    private void stampedeTick(ServerLevel level) {
        if (rush == null) return;                                   // a posed stampede has no herd to run with
        long now = level.getGameTime();
        Vec3 goal = rush.goal(now);
        boolean turned = (now - rush.start()) % STAMPEDE_TURN == 0;
        if (turned || getNavigation().isDone() || core().elapsed() % 10 == 0) run(goal);
        if (core().elapsed() % 2 == 0 && onGround()) {
            ParticleOptions dust = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().below()));
            level.sendParticles(dust, getX(), getY() + 0.1, getZ(), 3, 0.3, 0.05, 0.3, 0.05);
        }
        long cutoff = now - BUMP_COOLDOWN;
        bumped.values().removeIf(t -> t <= cutoff);
    }

    /**
     * Runs for {@code goal}. When no path gets nearer (a wall, the edge of the ground, a spot out of reach), any spot
     * towards it, then any spot at all close by: a cornered grazer still runs somewhere.
     */
    private void run(Vec3 goal) {
        if (getNavigation().moveTo(goal.x, goal.y, goal.z, STAMPEDE_SPEED) && getNavigation().getPath() != null
                && getNavigation().getPath().getNodeCount() > 1) return;
        Vec3 spot = DefaultRandomPos.getPosTowards(this, 8, 4, goal, Math.PI / 2.0);
        if (spot == null) spot = DefaultRandomPos.getPos(this, 4, 2);
        if (spot != null) getNavigation().moveTo(spot.x, spot.y, spot.z, STAMPEDE_SPEED);
    }

    /** A stampeding grazer shoves what it runs into instead of nudging it; otherwise it nudges as any mob does. */
    @Override
    protected void doPush(Entity other) {
        if (level() instanceof ServerLevel level && core().in(Step.STAMPEDE) && other instanceof LivingEntity victim && canBump(victim)) {
            bump(level, victim);
            return;
        }
        super.doPush(other);
    }

    private boolean canBump(LivingEntity e) {
        return e.isAlive() && !(e instanceof WoolcapGrazer) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e) && !e.isPassenger();
    }

    /** The shove: {@link #KNOCKBACK} blocks a tick away from this grazer and a {@link #KNOCKBACK_LIFT} hop (half with a Shellward Charm), no damage. */
    private void bump(ServerLevel level, LivingEntity victim) {
        Long last = bumped.get(victim.getUUID());
        if (last != null && level.getGameTime() - last < BUMP_COOLDOWN) return;
        bumped.put(victim.getUUID(), level.getGameTime());
        if (!MechanicEvent.post(this, "bump", victim)) return;
        double resist = Mth.clamp(victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        if (resist >= 1.0) return;
        double charm = Charms.chargeThrow(victim);
        Push.away(victim, position(), KNOCKBACK * (1.0 - resist) * charm, KNOCKBACK_LIFT * charm);
        bumps++;
        BUMP.play(victim);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.WOOLCAP_GRAZER_BUMP.get(), getSoundSource(), 1.0F, getVoicePitch());
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

    /** True when warped roots would make it fall in love now: grown, rested, calm, not in love already. */
    public boolean canFallInLove() {
        return isAlive() && age == 0 && love <= 0 && !core().in(Step.STAMPEDE);
    }

    /**
     * Server: eats one of {@code food} (warped roots): a grown grazer falls in love for {@link #LOVE_TICKS}, a calf grows
     * a tenth of its remaining time. Returns false (nothing eaten) when neither applies or a listener vetoes "love".
     * Public as a test hook.
     */
    public boolean feed(Player player, ItemStack food) {
        if (level().isClientSide() || !food.is(Items.WARPED_ROOTS) || core().in(Step.STAMPEDE)) return false;
        if (isBaby()) {
            food.consume(1, player);
            ageUp((int) Math.ceil(-age * CALF_FEED_SHARE));
            playSound(ModSounds.WOOLCAP_GRAZER_FED.get(), 1.0F, getVoicePitch());
            REGROWN.play(this);
            return true;
        }
        if (!canFallInLove() || !MechanicEvent.post(this, "love", player)) return false;
        food.consume(1, player);
        love = LOVE_TICKS;
        sideBySide = 0;
        playSound(ModSounds.WOOLCAP_GRAZER_FED.get(), 1.0F, getVoicePitch());
        HEARTS.play(this);
        return true;
    }

    /** Server: seeks out a partner in love and, side by side for {@link #MATE_TICKS}, brings a calf. */
    private void loveTick(ServerLevel level) {
        if (love <= 0 || !core().idle() || isBaby()) return;
        WoolcapGrazer mate = mate(level);
        if (mate == null) {
            sideBySide = 0;
            return;
        }
        getLookControl().setLookAt(mate, 10.0F, getMaxHeadXRot());
        if (core().elapsed() % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(mate, 1.0);
        if (distanceToSqr(mate) < MATE_TOUCH * MATE_TOUCH && ++sideBySide >= MATE_TICKS) breed(level, mate);
    }

    /** The nearest other grown grazer in love within {@link #MATE_RANGE}, calm, or null. */
    private @Nullable WoolcapGrazer mate(ServerLevel level) {
        WoolcapGrazer best = null;
        for (WoolcapGrazer g : level.getEntitiesOfClass(WoolcapGrazer.class, getBoundingBox().inflate(MATE_RANGE),
                g -> g != this && g.isAlive() && g.love > 0 && !g.isBaby() && g.core().idle() && g.distanceTo(this) <= MATE_RANGE)) {
            if (best == null || g.distanceToSqr(this) < best.distanceToSqr(this)) best = g;
        }
        return best;
    }

    /** A calf is born beside this parent, wearing one parent's look; both parents rest {@link #BREED_COOLDOWN}. */
    private void breed(ServerLevel level, WoolcapGrazer mate) {
        if (!MechanicEvent.post(this, "breed", mate)) {
            sideBySide = 0;
            return;
        }
        @SuppressWarnings("unchecked")
        EntityType<WoolcapGrazer> type = (EntityType<WoolcapGrazer>) getType();
        WoolcapGrazer calf = type.create(level, EntitySpawnReason.BREEDING);
        for (WoolcapGrazer parent : new WoolcapGrazer[] {this, mate}) {
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
        private @Nullable WoolcapGrazer parent;
        private int repath;

        FollowHerdGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!isBaby() || !core().idle() || tickCount % 10 != 0) return false;
            parent = null;
            for (WoolcapGrazer g : level().getEntitiesOfClass(WoolcapGrazer.class, getBoundingBox().inflate(8.0, 4.0, 8.0), g -> !g.isBaby() && g.isAlive())) {
                if (parent == null || g.distanceToSqr(WoolcapGrazer.this) < parent.distanceToSqr(WoolcapGrazer.this)) parent = g;
            }
            return parent != null && parent.distanceToSqr(WoolcapGrazer.this) > 9.0;
        }

        @Override
        public boolean canContinueToUse() {
            return parent != null && parent.isAlive() && core().idle() && isBaby() && parent.distanceToSqr(WoolcapGrazer.this) > 4.0
                    && parent.distanceToSqr(WoolcapGrazer.this) < 256.0;
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

    /** Shears shear it when it is ready; warped roots feed it; anything else does what it does to any mob. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (com.panda.teeming.mob.overworld.VinedropStrangler.shears(held) && readyForShearing()) {
            if (level() instanceof ServerLevel level) {
                shear(level, SoundSource.PLAYERS, held);
                if (!shorn()) return InteractionResult.PASS;         // vetoed: nothing taken, the shears untouched
                gameEvent(GameEvent.SHEAR, player);
                held.hurtAndBreak(1, player, hand.asEquipmentSlot());
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.WARPED_ROOTS)) {
            if (level().isClientSide()) return InteractionResult.CONSUME;
            return feed(player, held) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
        }
        return super.mobInteract(player, hand);
    }

    /** Ageing and love count down whatever it is doing, as vanilla animals' do; the size follows the calf flag. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel level) {
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

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().in(Step.STAMPEDE)) {
            stampedeTick(level);
        } else if (core().in(Step.GRAZE) || core().in(Step.SHEAR)) {
            getNavigation().stop();
            getMoveControl().setWait();
            if (core().in(Step.GRAZE) && core().elapsed() % 8 == 4) {
                ParticleOptions bits = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().below()));
                Vec3 mouth = position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(getBbWidth() * 0.7));
                level.sendParticles(bits, mouth.x, getY() + 0.05, mouth.z, 3, 0.1, 0.02, 0.1, 0.02);
            }
        } else {
            loveTick(level);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.GRAZE) grazed();
        if (ended == Step.STAMPEDE) {
            rush = null;
            bumped.clear();
            getNavigation().stop();
        }
        core().end();
    }

    // ---- save and load ---------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Shorn", shorn());
        output.putInt("Age", age);
    }

    /** The cap and the age survive a reload; love and a stampede in progress don't, as a reload ends any mechanic. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setShorn(input.getBooleanOr("Shorn", false));
        setAge(input.getIntOr("Age", 0));
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().in(Step.STAMPEDE) ? null : ModSounds.WOOLCAP_GRAZER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.WOOLCAP_GRAZER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.WOOLCAP_GRAZER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return (isBaby() ? 1.4F : 1.0F) + (random.nextFloat() - random.nextFloat()) * 0.1F;
    }
}
