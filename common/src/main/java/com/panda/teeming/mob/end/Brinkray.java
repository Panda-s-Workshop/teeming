package com.panda.teeming.mob.end;

import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.Push;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.TeemingMonster;
import com.panda.teeming.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Brinkray: a wide, flat ray gliding in the void air beside the outer End islands. It is neutral. It leaves players
 * alone unless they stand where 20 or more blocks of open air lie below within 1 block of their feet: on a bridge, an
 * overhang, a pillar's edge. Then it glides out to a spot 7 blocks off over the drop, level with their feet, and banks
 * for 20 ticks (tell): it rolls hard onto its side with its wings flared, a deep whoosh sounds and a streak of light
 * runs along the line it will ram. Then it rams along that line at 1 block a tick, skimming the deck at leg height: a
 * hit deals its attack (armour counts) and throws the victim 1.8 blocks a tick on along the ram and 0.3 up, less by
 * knockback resistance, which on a bridge is out over the void. Once its quarry is back over solid ground it loses
 * interest. Hit by anyone, it hunts them anywhere for 200 ticks, like a vanilla neutral mob; away from a brink that
 * ram hurts but throws no one.
 *
 * <p>Counter: during the bank, step off the streak (back onto solid ground, or a couple of blocks along the bridge).
 * Its ram is committed to the line the streak showed, set as the bank began, and skims below the deck's top, so with
 * nobody on the line it rams the island's side and hangs there stunned for 60 ticks, its lights out, within reach of
 * the deck.
 */
public class Brinkray extends TeemingMonster {
    public enum Step implements Phase { IDLE, STALK, TELL, RAM, RECOVER, STUNNED }

    /** Blocks of open air below a spot within reach of the feet that make a brink. */
    public static final int DROP = 20;
    /** How far from the feet (across the ground) a drop counts. */
    public static final double BRINK_REACH = 1.0;
    /** The bank. */
    public static final int TELL_TICKS = 20;
    /** The throw at the brink, in blocks a tick on along the ram, and up (before knockback resistance). */
    public static final double KNOCKBACK = 1.8;
    public static final double KNOCKBACK_UP = 0.3;
    /** x attack damage: the ram is its one blow. */
    public static final float RAM_HIT = 1.0F;
    /** Hit by someone, it hunts them anywhere this long (each blow starts it again). */
    public static final int GRUDGE_TICKS = 200;
    /** Rammed into rock: stunned, held where it struck, this long. */
    public static final int STUN_TICKS = 60;
    /** The ram, in blocks a tick, and its longest run (16 blocks). */
    public static final double RAM_SPEED = 1.0;
    public static final int RAM_TICKS = 16;
    /** Its broad leading edge reaches this far ahead of its body. */
    public static final double RAM_REACH = 0.8;
    /** It banks this far from its quarry, out over the drop. */
    public static final double APPROACH = 7.0;
    /** Close enough to its banking spot to bank. */
    public static final double ARRIVE = 1.0;
    /** At the brink it rams at leg height (its body's underside this far under the feet); elsewhere at the body. */
    public static final double LEG_LEVEL = -0.2;
    public static final double BODY_LEVEL = 0.6;
    /** Ground within this far under the feet: the quarry is standing, not falling or gliding. */
    public static final double STAND_DEPTH = 1.5;
    /** Gliding out to its banking spot, in blocks a tick; it gives up after this long. */
    public static final double STALK_SPEED = 0.5;
    public static final int STALK_TICKS = 200;
    /** Pulling back out and up after a ram or a stun. */
    public static final int RECOVER_TICKS = 40;
    public static final double RECOVER_SPEED = 0.5;
    public static final double RECOVER_CLIMB = 0.12;
    /** It peels off this far (radians) to the side of the way it came. */
    public static final float PEEL = 0.9F;
    /** At rest: a slow glide between open spots this far round where it settled. */
    public static final double GLIDE_SPEED = 0.25;
    public static final double GLIDE_RANGE = 8.0;
    /** How far it looks for a quarry (its follow range), and how often. */
    public static final double HUNT_RANGE = 32.0;
    public static final int SCAN_EVERY = 5;

    /** Glow stages: 0 at rest (stars and streamers), 1 the bank (the leading edges lit), 2 stunned (every light out). */
    public static final int GLOW_REST = 0;
    public static final int GLOW_BANK = 1;
    public static final int GLOW_OUT = 2;

    /** Star-white, and a deep violet core, so the streak reads on the void and on cream end stone alike. */
    public static final int STAR = 0xF4F0FF;
    public static final int VIOLET = 0x5A2AA0;
    private static final DustParticleOptions STAR_DUST = new DustParticleOptions(STAR, 1.0F);
    private static final DustParticleOptions VIOLET_DUST = new DustParticleOptions(VIOLET, 1.5F);
    private static final DustParticleOptions TRAIL_DUST = new DustParticleOptions(STAR, 0.7F);
    private static final DustParticleOptions STUN_DUST = new DustParticleOptions(STAR, 1.4F);

    private final Map<UUID, Long> grudges = new HashMap<>();
    private @Nullable LivingEntity handed;
    private @Nullable Vec3 home;
    private double glideRange = GLIDE_RANGE;
    private @Nullable Vec3 waypoint;
    private int waypointAge;
    private @Nullable Vec3 approach;
    private Vec3 aim = Vec3.ZERO;
    private Vec3 ramDir = new Vec3(0, 0, 1);
    private Vec3 heading = new Vec3(0, 0, 1);
    private @Nullable Phase shown;
    private @Nullable AABB fakeVoidArea;
    private int fakeVoidY;
    private @Nullable Vec3 lastThrow;
    private int rams;
    private int hits;
    private int crashes;

    public Brinkray(EntityType<? extends Brinkray> type, Level level) {
        super(type, level, Step.values());
        moveControl = new FlyingMoveControl<>(this, 20, true);
        setNoGravity(true);
        xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("brinkray", 0.3).add(Attributes.FLYING_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, HUNT_RANGE)
                .add(Attributes.STEP_HEIGHT, 0.0);
    }

    /** It always flies: it never lands and never falls. */
    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    /** Stunned, it hangs where it struck the rock: nothing moves it, so it stays within reach. */
    @Override
    public Pin pin() {
        return core().in(Step.STUNNED) ? Pin.FIXED : Pin.FREE;
    }

    /** Its broad body brushes past whatever it touches without shoving it: only its ram moves anyone. */
    @Override
    protected void doPush(Entity entity) {}

    @Override
    public int visualStages() {
        return 3;
    }

    // ---- the brink -----------------------------------------------------------------------------------------------

    /**
     * The brink rule, with no game types so it can be checked on its own. A column is read as cells by their depth under
     * the cell the feet are in (1 is the cell of the block they stand on, 2 the one under it, and so on): it drops away
     * when none of the {@link #DROP} cells under the feet is solid. Round the feet it looks at eight points
     * {@link #BRINK_REACH} away, the four sides and the four diagonals.
     */
    public static final class Brink {
        private Brink() {}

        /** Whether a column drops away: none of the first {@link #DROP} cells below the feet is solid. */
        public static boolean drop(IntPredicate solidAt) {
            for (int d = 1; d <= DROP; d++) if (solidAt.test(d)) return false;
            return true;
        }

        /** The block column a point at (x, z) lies in, and whether it drops away. */
        public interface Columns {
            boolean drops(int blockX, int blockZ);
        }

        /**
         * The ways (unit vectors across the ground, x then z) from feet at (x, z) toward the points round them whose
         * column drops away; empty when none does, so the feet are not at a brink.
         */
        public static List<double[]> open(double x, double z, Columns columns) {
            List<double[]> found = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0;
                double dx = Math.cos(a), dz = Math.sin(a);
                if (columns.drops((int) Math.floor(x + dx * BRINK_REACH), (int) Math.floor(z + dz * BRINK_REACH))) found.add(new double[] {dx, dz});
            }
            return found;
        }
    }

    /**
     * Whether {@code e} stands at a brink: on the ground (not falling, not gliding), with a drop of {@link #DROP} or more
     * within {@link #BRINK_REACH} of its feet. Public as a test hook.
     */
    public boolean atBrink(LivingEntity e) {
        return !brinkSides(e).isEmpty();
    }

    private List<double[]> brinkSides(LivingEntity e) {
        if (!(level() instanceof ServerLevel level) || e.isFallFlying()) return List.of();
        int feet = Mth.floor(e.getY() + 0.01);
        AABB box = e.getBoundingBox();
        AABB under = new AABB(box.minX + 0.01, e.getY() - STAND_DEPTH, box.minZ + 0.01, box.maxX - 0.01, e.getY() - 0.001, box.maxZ - 0.01);
        if (level.noCollision(under)) return List.of();
        return Brink.open(e.getX(), e.getZ(), (bx, bz) -> {
            if (!level.hasChunkAt(new BlockPos(bx, feet, bz))) return false;
            return Brink.drop(d -> solidCell(level, bx, feet - d, bz));
        });
    }

    private boolean solidCell(ServerLevel level, int x, int y, int z) {
        if (fakeVoidArea != null && y <= fakeVoidY && fakeVoidArea.contains(x + 0.5, fakeVoidArea.minY, z + 0.5)) return false;
        if (y < level.getMinY()) return false;
        BlockPos pos = new BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty();
    }

    // ---- who it rams ---------------------------------------------------------------------------------------------

    /**
     * Whether it would ram {@code e} now: alive, not a creative or spectating player, and either standing at a brink or
     * the one who hit it in the last 200 ticks. Public as a test hook.
     */
    public boolean hostileTo(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof Brinkray) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return grudgeLeft(e) > 0 || atBrink(e);
    }

    /** Ticks left of its grudge against {@code e}, 0 if none. Public as a test hook. */
    public int grudgeLeft(LivingEntity e) {
        Long until = grudges.get(e.getUUID());
        return until == null ? 0 : (int) Math.max(0, until - level().getGameTime());
    }

    private double range() {
        return getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    /**
     * A target handed to it by a command, another mod or a test joins the players it watches: it rams that one by the
     * same rules (at a brink, or after a blow).
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target != null && target != this && !level().isClientSide()) handed = target;
    }

    /** Everyone it would ram now, in range and in sight, nearest first, until one hunt isn't vetoed. */
    private void scan(ServerLevel level) {
        long now = level.getGameTime();
        grudges.values().removeIf(until -> until <= now);
        List<LivingEntity> found = new ArrayList<>();
        for (Player p : level.players()) found.add(p);
        if (handed != null && !(handed instanceof Player)) {
            if (handed.isRemoved()) handed = null;
            else found.add(handed);
        }
        for (UUID id : grudges.keySet()) {
            if (level.getEntity(id) instanceof LivingEntity by && !(by instanceof Player) && !found.contains(by)) found.add(by);
        }
        found.removeIf(e -> distanceTo(e) > range() || !hostileTo(e) || !hasLineOfSight(e));
        found.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (LivingEntity e : found) {
            if (hunt(e)) return;
        }
    }

    private boolean hunt(LivingEntity quarry) {
        if (!startPhase(Step.STALK, STALK_TICKS, quarry)) return false;
        approach = null;
        return true;
    }

    // ---- the cycle -----------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        getNavigation().stop();
        setNoGravity(true);
        if (isDeadOrDying()) return;
        if (core().in(Step.STALK)) {
            stalkTick(level);
        } else if (core().in(Step.TELL)) {
            tellTick(level);
        } else if (core().in(Step.RAM)) {
            ramTick(level);
        } else if (core().in(Step.RECOVER)) {
            fly(new Vec3(heading.x * RECOVER_SPEED, RECOVER_CLIMB, heading.z * RECOVER_SPEED), 0.2);
        } else if (core().in(Step.STUNNED)) {
            setDeltaMovement(Vec3.ZERO);
            if (core().elapsed() % 4 == 0) stunStars(level);
        } else {
            glide(level);
            if (tickCount % SCAN_EVERY == 0) scan(level);
        }
        if (!core().in(Step.STUNNED) && tickCount % 3 == 0) trail(level);
    }

    /** Out to its banking spot over the drop; there it banks. A quarry back on solid ground is left alone. */
    private void stalkTick(ServerLevel level) {
        LivingEntity quarry = core().target();
        if (quarry == null || !hostileTo(quarry) || distanceTo(quarry) > range() * 1.5) {
            settle();
            return;
        }
        if (approach == null || core().elapsed() % 5 == 0) approach = approachFor(level, quarry);
        Vec3 to = approach.subtract(position());
        if (to.length() <= ARRIVE) {
            bank(level, quarry);
            return;
        }
        fly(to.length() > STALK_SPEED ? to.normalize().scale(STALK_SPEED) : to, 0.25);
    }

    /**
     * Where it banks for {@code quarry}: {@link #APPROACH} out from it, over the drop at a brink (on the side nearest
     * itself) or toward itself elsewhere, level with the feet at a brink and with the body elsewhere; the first such spot
     * with room for its body and a clear run in, or else the nearest-side one.
     */
    private Vec3 approachFor(ServerLevel level, LivingEntity quarry) {
        List<double[]> sides = brinkSides(quarry);
        double lift = sides.isEmpty() ? BODY_LEVEL : LEG_LEVEL;
        Vec3 mine = position().subtract(quarry.position()).multiply(1, 0, 1);
        Vec3 own = mine.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, -1) : mine.normalize();
        List<Vec3> ways = new ArrayList<>();
        if (sides.isEmpty()) {
            for (int i = 0; i < 8; i++) ways.add(own.yRot((float) (Math.PI / 4.0 * ((i + 1) / 2) * (i % 2 == 0 ? 1 : -1))));
        } else {
            for (double[] s : sides) ways.add(new Vec3(s[0], 0, s[1]));
            ways.sort(Comparator.comparingDouble(w -> -w.dot(own)));
        }
        Vec3 first = null;
        for (Vec3 way : ways) {
            Vec3 spot = quarry.position().add(way.scale(APPROACH)).add(0, lift, 0);
            if (first == null) first = spot;
            if (clearRun(level, spot, quarry.position().add(0, lift, 0))) return spot;
        }
        return first;
    }

    /** Room for its body at {@code from} and all along the run toward {@code to}, stopping short of what stands there. */
    private boolean clearRun(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-6) return false;
        Vec3 unit = d.scale(1.0 / len);
        double stop = getBbWidth() / 2.0 + 1.0;
        for (double s = 0.0; s <= len - stop; s += 1.0) {
            if (!level.noCollision(this, boxAt(from.add(unit.scale(s))))) return false;
        }
        return true;
    }

    private AABB boxAt(Vec3 feet) {
        double half = getBbWidth() / 2.0;
        return new AABB(feet.x - half, feet.y, feet.z - half, feet.x + half, feet.y + getBbHeight(), feet.z + half);
    }

    /**
     * Starts the bank toward {@code quarry}. The ram's line is set now and the streak shows it: at the quarry's feet as
     * they stand now, skimming the deck at a brink and at the body elsewhere.
     */
    private void bank(ServerLevel level, LivingEntity quarry) {
        boolean brink = atBrink(quarry);
        if (!startPhase(Step.TELL, TELL_TICKS, quarry)) {
            settle();
            return;
        }
        aim = quarry.position().add(0, brink ? LEG_LEVEL : BODY_LEVEL, 0);
        setDeltaMovement(getDeltaMovement().scale(0.3));
        playSound(ModSounds.BRINKRAY_BANK.get(), 2.0F, 1.0F);
    }

    /** The bank: it brakes and hangs side-on, faces down its line, and draws the streak of light along it. */
    private void tellTick(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().scale(0.6));
        face(aim.subtract(position()).multiply(1, 0, 1));
        if (core().elapsed() % 2 == 0) streak(level, aim);
        if (core().elapsed() == TELL_TICKS / 2) playSound(ModSounds.BRINKRAY_BANK.get(), 1.4F, 0.8F);
    }

    /** A line of light from its nose along the ram to its end: star-white and deep violet in turn. */
    private void streak(ServerLevel level, Vec3 aim) {
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = aim.add(0, getBbHeight() * 0.5, 0);
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-6) return;
        Vec3 unit = d.scale(1.0 / len);
        int k = 0;
        for (double s = getBbWidth() * 0.5; s <= len + 1.0; s += 0.5, k++) {
            Vec3 p = from.add(unit.scale(s));
            level.sendParticles(k % 2 == 0 ? STAR_DUST : VIOLET_DUST, true, true, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        if (!(level() instanceof ServerLevel level)) {
            core().end();
            return;
        }
        LivingEntity quarry = core().target();
        if (ended == Step.TELL) {
            if (quarry == null || !quarry.isAlive() || quarry instanceof Player p && (p.isCreative() || p.isSpectator())) {
                settle();
                return;
            }
            Vec3 d = aim.subtract(position());
            if (d.lengthSqr() < 1.0E-6 || !startPhase(Step.RAM, RAM_TICKS, quarry)) {
                settle();
                return;
            }
            ramDir = d.normalize();
            rams++;
            setDeltaMovement(ramDir.scale(RAM_SPEED));
            face(ramDir);
            playSound(ModSounds.BRINKRAY_RAM.get(), 2.0F, 1.0F);
        } else if (ended == Step.RAM || ended == Step.STUNNED) {
            recover(quarry);                                      // a ram that ran out met nothing; a stun wears off
        } else if (ended == Step.RECOVER && quarry != null && hostileTo(quarry) && distanceTo(quarry) <= range() && hunt(quarry)) {
            // still at the brink (or still owed a grudge): it comes round again
        } else {
            settle();
        }
    }

    /** The ram: straight along its line; the first touch of its quarry is the hit, rock in the way is the crash. */
    private void ramTick(ServerLevel level) {
        LivingEntity quarry = core().target();
        AABB reach = getBoundingBox().expandTowards(ramDir.scale(RAM_REACH)).inflate(0.1);
        if (quarry != null && quarry.isAlive() && hostileQuarry(quarry) && reach.intersects(quarry.getBoundingBox())) {
            hit(level, quarry);
            return;
        }
        if ((horizontalCollision || verticalCollision) && core().elapsed() >= 1) {
            crash(level);
            return;
        }
        setDeltaMovement(ramDir.scale(RAM_SPEED));
        face(ramDir);
        if (core().elapsed() % 2 == 0) {
            level.sendParticles(TRAIL_DUST, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0.4, 0.05, 0.4, 0.0);
        }
    }

    private static boolean hostileQuarry(LivingEntity e) {
        return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /**
     * The ram lands: its attack, through armour. At a brink the victim is thrown on along the ram, 1.8 a tick and 0.3
     * up, less by its knockback resistance and by half with a Shellward or a Grounding Charm in the off-hand; anywhere
     * else it throws no one, so nobody but a player at the brink is ever moved out over the void.
     */
    private void hit(ServerLevel level, LivingEntity victim) {
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BRINKRAY_RAM.get(), getSoundSource(), 1.5F, 0.8F);
        if (MechanicEvent.post(this, "ram_hit", victim)) {
            boolean brink = atBrink(victim);
            DamageSource source = damageSources().mobAttack(this);
            if (victim.hurtServer(level, source, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * RAM_HIT)) {
                hits++;
                Vec3 across = ramDir.multiply(1, 0, 1);
                across = across.lengthSqr() < 1.0E-6 ? heading : across.normalize();
                double keep = (1.0 - Mth.clamp(victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0)) * Charms.endChargeThrow(victim);
                if (brink && keep > 0.0 && MechanicEvent.post(this, "throw", victim)) {
                    lastThrow = new Vec3(across.x * KNOCKBACK * keep, KNOCKBACK_UP * keep, across.z * KNOCKBACK * keep);
                } else {
                    lastThrow = new Vec3(0.0, Math.min(0.0, victim.getDeltaMovement().y), 0.0);   // no knockback, not even vanilla's
                }
                Push.set(victim, lastThrow);
                Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.4, 0);
                level.sendParticles(ParticleTypes.CRIT, true, true, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.3);
                level.sendParticles(STAR_DUST, true, true, at.x, at.y, at.z, 10, 0.4, 0.3, 0.4, 0.0);
            }
        }
        recover(victim);
    }

    /** Rammed into rock: it hangs there stunned, held fast where it struck, every light out. */
    private void crash(ServerLevel level) {
        if (!startPhase(Step.STUNNED, STUN_TICKS, core().target())) {
            recover(core().target());
            return;
        }
        crashes++;
        setDeltaMovement(Vec3.ZERO);
        Vec3 front = position().add(ramDir.multiply(1, 0, 1).normalize().scale(getBbWidth() * 0.5 + 0.3)).add(0, getBbHeight() * 0.5, 0);
        BlockState rock = level.getBlockState(BlockPos.containing(front));
        if (rock.isAir()) rock = level.getBlockState(BlockPos.containing(front.x, front.y - 0.5, front.z));
        if (!rock.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock), true, true, front.x, front.y, front.z, 24, 0.6, 0.2, 0.6, 0.15);
        level.sendParticles(ParticleTypes.CRIT, true, true, getX(), getY() + getBbHeight(), getZ(), 16, 0.8, 0.2, 0.8, 0.2);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BRINKRAY_CRASH.get(), getSoundSource(), 1.6F, 1.0F);
    }

    /** Stunned: three stars wheel over its back, two star-white and one deep violet, so they read on sky and void alike. */
    private void stunStars(ServerLevel level) {
        double a = core().elapsed() * 0.35;
        for (int i = 0; i < 3; i++) {
            double t = a + i * Math.PI * 2.0 / 3.0;
            level.sendParticles(i == 0 ? VIOLET_DUST : STUN_DUST, true, true, getX() + Math.cos(t) * 0.8, getY() + getBbHeight() + 0.4,
                    getZ() + Math.sin(t) * 0.8, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Peels off back out over the drop, turned aside from the way it came, and climbs, still minding its quarry. */
    private void recover(@Nullable LivingEntity quarry) {
        Vec3 back = ramDir.multiply(-1, 0, -1);
        heading = back.lengthSqr() < 1.0E-6 ? heading : back.normalize().yRot(PEEL);
        setDeltaMovement(heading.x * RECOVER_SPEED, RECOVER_CLIMB, heading.z * RECOVER_SPEED);   // away at once, off its victim
        if (!startPhase(Step.RECOVER, RECOVER_TICKS, quarry)) settle();
    }

    /** Back to gliding round where it is now. */
    private void settle() {
        core().end();
        home = position();
        waypoint = null;
        approach = null;
    }

    // ---- gliding -------------------------------------------------------------------------------------------------

    /** At rest: a slow glide from one open spot to the next, round where it settled, never into rock. */
    private void glide(ServerLevel level) {
        if (home == null) home = position();
        waypointAge++;
        if (waypoint == null || position().distanceTo(waypoint) < 1.5 || waypointAge > 160) pickWaypoint(level);
        if (waypoint == null) {
            fly(Vec3.ZERO, 0.1);
            return;
        }
        Vec3 to = waypoint.subtract(position());
        fly(to.length() > GLIDE_SPEED ? to.normalize().scale(GLIDE_SPEED) : to, 0.06);
    }

    private void pickWaypoint(ServerLevel level) {
        waypointAge = 0;
        waypoint = null;
        Vec3 base = home == null ? position() : home;
        for (int i = 0; i < 8; i++) {
            Vec3 spot = base.add((random.nextDouble() * 2 - 1) * glideRange, (random.nextDouble() * 2 - 1) * Math.min(1.5, glideRange),
                    (random.nextDouble() * 2 - 1) * glideRange);
            if (level.noCollision(this, boxAt(spot)) && clearRun(level, position(), spot)) {
                waypoint = spot;
                return;
            }
        }
    }

    /** The wingtips trail light as it flies. */
    private void trail(ServerLevel level) {
        Vec3 side = new Vec3(-heading.z, 0, heading.x).scale(getBbWidth() * 0.48);
        Vec3 back = heading.scale(-0.3);
        Vec3 c = position().add(0, getBbHeight() * 0.45, 0).add(back);
        for (Vec3 tip : new Vec3[] {c.add(side), c.subtract(side)}) {
            level.sendParticles(TRAIL_DUST, false, true, tip.x, tip.y, tip.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Eases its motion toward {@code want} by {@code ease} (1 = at once) and faces the way it goes. */
    private void fly(Vec3 want, double ease) {
        Vec3 v = getDeltaMovement();
        Vec3 next = ease >= 1.0 ? want : new Vec3(Mth.lerp(ease, v.x, want.x), Mth.lerp(ease, v.y, want.y), Mth.lerp(ease, v.z, want.z));
        setDeltaMovement(next);
        face(next);
    }

    private void face(Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-6) return;
        setXRot((float) Mth.clamp(-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG, -30.0, 30.0));
        if (dir.horizontalDistanceSqr() < 1.0E-6) return;
        heading = dir.multiply(1, 0, 1).normalize();
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---- provoked ------------------------------------------------------------------------------------------------

    /** Hit by anyone (a creative player aside), it hunts them anywhere for 200 ticks; every blow starts that again. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && source.getEntity() instanceof LivingEntity by && by != this && hostileQuarry(by)
                && MechanicEvent.post(this, "grudge", by)) {
            grudges.put(by.getUUID(), level.getGameTime() + GRUDGE_TICKS);
        }
        return hurt;
    }

    // ---- the lights ----------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        Phase now = core().phase();
        if (now != shown) {
            shown = now;
            core().setStage(now == Step.TELL ? GLOW_BANK : now == Step.STUNNED ? GLOW_OUT : GLOW_REST);
        }
    }

    // ---- test hooks ----------------------------------------------------------------------------------------------

    /**
     * Counts every cell at or below height {@code y} in the columns inside {@code area} as open air, standing in for
     * the void under an island where no real drop of 20 fits (null: none). Public as a test hook.
     */
    public void fakeVoid(@Nullable AABB area, int y) {
        fakeVoidArea = area;
        fakeVoidY = y;
    }

    /** Sets where it glides at rest and how far round it. Public as a test hook. */
    public void glideAround(Vec3 at, double range) {
        home = at;
        glideRange = range;
        waypoint = null;
    }

    /** The motion it gave its last victim, or null. Public as a test hook. */
    public @Nullable Vec3 lastThrow() {
        return lastThrow;
    }

    /** Where its current (or last) bank aimed the ram. Public as a test hook. */
    public Vec3 aim() {
        return aim;
    }

    /** The line of its current (or last) ram, a unit vector. Public as a test hook. */
    public Vec3 ramDir() {
        return ramDir;
    }

    /** Rams started, rams that hit, and rams that ended in the rock, since it spawned. Public as test hooks. */
    public int rams() {
        return rams;
    }

    public int hits() {
        return hits;
    }

    public int crashes() {
        return crashes;
    }

    // ---- sounds --------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BRINKRAY_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BRINKRAY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BRINKRAY_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + random.nextFloat() * 0.1F;
    }
}
