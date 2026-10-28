package com.panda.teeming.mob.end;

import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
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
import net.minecraft.core.particles.TrailParticleOption;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Spirebloom: a void-crystal growth rooted in the end stone, a crown of dark spikes round one great central spire. It
 * never moves and nothing pushes it off its spot.
 *
 * <p>Every 80 ticks, at a target within 10 blocks across (and 3 up or down) that it can see, it cracks the ground: for
 * 16 ticks (tell) its crown draws in and shivers and a line of dark violet cracks runs over the ground from it toward
 * where the target stood, 10 blocks long, crystal motes rising along it and a grinding crack. The line is fixed where it
 * pointed when the tell began. Then crystal spikes burst up along it: everyone standing on the line (its target, and any
 * player) takes its attack through armour and is thrown straight up, never sideways, so no one is knocked off an
 * island's edge. Spikes rise only from ground: none stand over the void. A dead Spirebloom bursts nothing.
 *
 * <p>Counter: step off the line, to either side. A line that hits nobody leaves its spikes standing for 60 ticks as
 * cover: any arrow, fireball or bullet not shot by a player shatters on them, and monsters that walk into them are
 * fended off. Players pass through freely and shoot through them. Terrain left behind that a player can use against
 * other mobs.
 */
public class Spirebloom extends TeemingMonster {
    /** IDLE, the cracks running out (tell), the spikes up. */
    public enum Step implements Phase { IDLE, TELL, BURST }

    /** The tell: the cracks run out along the line this long before the spikes burst up. */
    public static final int TELL_TICKS = 16;
    /** The spikes stand up out of the line this long after the burst (the blow lands on its first tick). */
    public static final int BURST_TICKS = 10;
    /** Ticks from the start of one tell to the start of the next. */
    public static final int EVERY = 80;
    /** It cracks at targets this close across, centre to centre, and no farther up or down than this. */
    public static final double RANGE = 10.0;
    public static final double RANGE_UP = 3.0;
    /** The line: from 1 block out to 10 blocks from its centre, a spike every half block, each reaching this far across. */
    public static final double LINE_LENGTH = 10.0;
    public static final double LINE_START = 1.0;
    public static final double SPIKE_SPACING = 0.5;
    public static final double SPIKE_RADIUS = 0.5;
    /** How high over the ground a body's feet may be and still be struck: a jump doesn't clear the spikes. */
    public static final double SPIKE_HEIGHT = 1.5;
    /** The blow: x its attack (8), through armour. */
    public static final float STRIKE = 1.0F;
    /** The throw: straight up at this many blocks a tick (about 2 blocks high, no fall damage), nothing across. */
    public static final double KNOCK_UP = 0.6;
    /** A line that struck nobody leaves its spikes standing this long (3 s). */
    public static final int STAND_TICKS = 60;
    /** How hard the standing spikes fend a monster off, across, in blocks a tick. */
    public static final double FEND_PUSH = 0.4;
    public static final int XP = 12;

    /** Dark violet: the cracks, deep enough to read on pale end stone and well off weak-point gold. */
    public static final int DARK_VIOLET = 0x4A1E86;
    /** Star-white: the spikes' tips and the motes. */
    public static final int STAR_WHITE = 0xEEF0FF;
    private static final int STAR_ARGB = 0xFF000000 | STAR_WHITE;
    private static final int DEEP_ARGB = 0xFF24123F;
    private static final DustParticleOptions CRYSTAL_DUST = new DustParticleOptions(0x3A2570, 1.1F);
    private static final DustParticleOptions STAR_DUST = new DustParticleOptions(STAR_WHITE, 0.7F);
    private static final DustParticleOptions CRACK_DUST = new DustParticleOptions(DARK_VIOLET, 1.2F);
    private static final DustParticleOptions PRICK_DUST = new DustParticleOptions(STAR_WHITE, 1.0F);
    private static final DustParticleOptions SPIKE_TIP = new DustParticleOptions(STAR_WHITE, 0.8F);
    /** A spike's body from its foot up: dark void crystal, narrowing. */
    private static final DustParticleOptions[] SPIKE_BODY = {
            new DustParticleOptions(0x2A1858, 1.8F), new DustParticleOptions(0x2C1A5C, 1.6F), new DustParticleOptions(0x301D64, 1.4F),
            new DustParticleOptions(0x34206C, 1.2F), new DustParticleOptions(0x3A2476, 1.0F), new DustParticleOptions(0x442C86, 0.8F),
            new DustParticleOptions(0x503898, 0.6F)};

    /** Glow stages: dark, and the cracks lit through the tell. */
    public static final int GLOW_DARK = 0;
    public static final int GLOW_LIT = 1;

    private final AstralAugur.GroundMarks line = new AstralAugur.GroundMarks(SPIKE_HEIGHT)
            .affects(this::strikable, this::strikeHit)
            .drawn(this::drawCracks, (level, mark) -> { }, this::drawFaded);
    /** Where the line runs from and which way, flat: fixed when the tell starts. */
    private Vec3 lineFrom = Vec3.ZERO, lineDir = new Vec3(0, 0, 1);
    private final Hedge hedge = new Hedge();
    private long nextTell = Long.MIN_VALUE;
    private int tells, bursts, strikes, hedges, shattered, fended;

    public Spirebloom(EntityType<? extends Spirebloom> type, Level level) {
        super(type, level, Step.values());
        xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.monster("spirebloom", 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public int visualStages() {
        return 2;
    }

    /** Every phase it enters sets its glow: the cracks lit through the tell, dark otherwise. */
    @Override
    public boolean startPhase(Phase phase, int length, @Nullable LivingEntity target) {
        if (!core().start(phase, length, target)) return false;
        core().setStage(phase == Step.TELL ? GLOW_LIT : GLOW_DARK);
        return true;
    }

    private void rest() {
        core().end();
        core().setStage(GLOW_DARK);
    }

    // ---- rooted: it never moves ------------------------------------------------------------------------------------

    /** Only falling moves it: knockback, pistons and currents all lose their sideways part. */
    @Override
    public Pin pin() {
        return Pin.ROOTED;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Body and head are one piece. */
    @Override
    protected void tickHeadTurn(float yBodyRot) {
        this.yBodyRot = this.yHeadRot;
    }

    private void face(Vec3 at) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    // ---- every tick ------------------------------------------------------------------------------------------------

    /** The line's drawing and the standing spikes run from {@code tick()}, so they work for a mob without AI. */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (core().in(Step.TELL)) {
            line.tick(level);
            tellCues(level);
        } else if (core().in(Step.BURST) && core().elapsed() % 2 == 0 && core().elapsed() > 0) {
            for (Vec3 s : lastSpikes) drawSpike(level, s, 1.0 - (double) core().elapsed() / BURST_TICKS, false);
        }
        hedge.tick(level);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (!core().idle() || level.getGameTime() < nextTell || !valid(target)) return;
        if (inRange(target) && getSensing().hasLineOfSight(target)) crack(target);
    }

    private boolean valid(@Nullable LivingEntity e) {
        return e != null && e != this && e.isAlive() && !e.isRemoved() && e.level() == level()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Within {@link #RANGE} across, centre to centre, and {@link #RANGE_UP} up or down. */
    public boolean inRange(LivingEntity e) {
        return Math.hypot(e.getX() - getX(), e.getZ() - getZ()) <= RANGE && Math.abs(e.getY() - getY()) <= RANGE_UP;
    }

    // ---- the tell --------------------------------------------------------------------------------------------------

    /**
     * Starts the tell at {@code target}: the line is fixed toward where it stands now and the cracks start to run.
     * Ignores range, sight and the 80-tick rhythm, which it then restarts. True if it did. Public as a test hook.
     */
    public boolean crack(LivingEntity target) {
        if (!core().idle() || isDeadOrDying() || !(level() instanceof ServerLevel level)) return false;
        if (!startPhase(Step.TELL, TELL_TICKS, target)) return false;
        nextTell = level.getGameTime() + EVERY;
        face(target.position());
        Vec3 d = target.position().subtract(position()).multiply(1, 0, 1);
        lineDir = d.lengthSqr() < 1.0E-6 ? forward() : d.normalize();
        lineFrom = position();
        line.place(level, groundedSpots(level, lineFrom, lineDir), SPIKE_RADIUS, TELL_TICKS);
        Telegraph.particles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, position())), 16, 0.6, 0.2)
                .withSound(ModSounds.SPIREBLOOM_TELL.get(), 1.2F, 0.9F).play(this);
        tells++;
        return true;
    }

    /**
     * The spike spots along the line: every {@link #SPIKE_SPACING} from {@link #LINE_START} to {@link #LINE_LENGTH} out,
     * each settled on the ground below it. A spot with no ground in reach (over the void) gets no spike.
     */
    private static List<Vec3> groundedSpots(ServerLevel level, Vec3 from, Vec3 dir) {
        List<Vec3> spots = new ArrayList<>();
        int n = (int) Math.round((LINE_LENGTH - LINE_START) / SPIKE_SPACING);
        for (int i = 0; i <= n; i++) {
            double d = LINE_START + i * SPIKE_SPACING;
            Vec3 at = new Vec3(from.x + dir.x * d, from.y, from.z + dir.z * d);
            Vec3 g = AstralAugur.GroundMarks.ground(level, at);
            BlockPos under = BlockPos.containing(g.x, g.y - 0.5, g.z);
            if (!level.getBlockState(under).getCollisionShape(level, under).isEmpty()) spots.add(g);
        }
        return spots;
    }

    /** The tell's cues: a grinding crack again at its middle, grit off the mound, the crown shivering motes. */
    private void tellCues(ServerLevel level) {
        int t = core().elapsed();
        if (t == TELL_TICKS / 2) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.SPIREBLOOM_TELL.get(), getSoundSource(), 1.0F, 1.15F);
        }
        if (t % 3 == 0) {
            level.sendParticles(new TrailParticleOption(new Vec3(getX(), getY() + 2.3, getZ()), STAR_ARGB, 10), true, true,
                    getX(), getY() + 0.4, getZ(), 1, 0.35, 0.1, 0.35, 0.0);
        }
    }

    /**
     * The cracks, every other tick: a jagged dark violet crack runs out along the line as the tell fills, chipping the
     * stone at its front, with star-white crystal motes rising from the cracked part; star-white pricks mark where the
     * rest of the line will crack, so the whole line reads from the first tick. Dark violet on cream reads on end stone
     * in any light.
     */
    private void drawCracks(ServerLevel level, AstralAugur.GroundMarks.Mark mark, float fill) {
        if (tickCount % 2 != 0) return;
        Vec3 c = mark.centre();
        double d = Math.hypot(c.x - lineFrom.x, c.z - lineFrom.z);
        double front = LINE_START + (LINE_LENGTH - LINE_START + SPIKE_SPACING) * fill;
        double y = c.y + 0.08;
        double px = -lineDir.z, pz = lineDir.x;                    // across the line
        if (d <= front) {
            for (int i = 0; i < 3; i++) {                          // a jagged crack: three chips a mark, zig-zagging
                double along = (i - 1) * SPIKE_SPACING / 3.0;
                double side = (((int) Math.round(d * 6) + i) % 2 == 0 ? 1 : -1) * 0.12 + (random.nextDouble() - 0.5) * 0.06;
                double x = c.x + lineDir.x * along + px * side, z = c.z + lineDir.z * along + pz * side;
                level.sendParticles(CRACK_DUST, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(new TrailParticleOption(new Vec3(c.x, y, c.z), DEEP_ARGB, 6), true, true,
                    c.x + lineDir.x * SPIKE_SPACING, y, c.z + lineDir.z * SPIKE_SPACING, 1, 0.0, 0.0, 0.0, 0.0);
            if (random.nextInt(3) == 0) {
                double mx = c.x + px * (random.nextDouble() - 0.5) * 0.6, mz = c.z + pz * (random.nextDouble() - 0.5) * 0.6;
                level.sendParticles(STAR_DUST, true, true, mx, y + 0.2 + random.nextDouble() * 0.6, mz, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (d > front - 1.0) {                                 // the crack's front, chipping the stone
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, c)), true, true, c.x, y + 0.1, c.z, 3, 0.15, 0.05, 0.15, 0.05);
            }
        } else if (tickCount % 4 == 0) {
            level.sendParticles(PRICK_DUST, true, true, c.x, y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A line that never burst (its tell was cut short): the cracks close with a puff of grit. */
    private void drawFaded(ServerLevel level, AstralAugur.GroundMarks.Mark mark) {
        Vec3 c = mark.centre();
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, groundAt(level, c)), true, true, c.x, c.y + 0.2, c.z, 2, 0.2, 0.05, 0.2, 0.0);
    }

    // ---- the burst -------------------------------------------------------------------------------------------------

    private List<Vec3> lastSpikes = List.of();

    @Override
    public void onPhaseEnd(Phase ended) {
        if (ended == Step.TELL && level() instanceof ServerLevel level) {
            burst(level);
        } else {
            rest();
        }
    }

    /**
     * The end of the tell: crystal spikes burst up along the whole line. Everyone on it is struck; if no one is, the
     * spikes stay standing.
     */
    private void burst(ServerLevel level) {
        List<AstralAugur.GroundMarks.Mark> marks = line.marks();
        if (isDeadOrDying() || marks.isEmpty() || !startPhase(Step.BURST, BURST_TICKS, core().target())) {
            line.cancel(level);
            rest();
            return;
        }
        List<Vec3> spikes = new ArrayList<>(marks.size());
        for (AstralAugur.GroundMarks.Mark m : marks) spikes.add(m.centre());
        lastSpikes = List.copyOf(spikes);
        bursts++;
        Vec3 mid = spikes.get(spikes.size() / 2);
        level.playSound(null, mid.x, mid.y, mid.z, ModSounds.SPIREBLOOM_BURST.get(), getSoundSource(), 1.6F, 0.9F + random.nextFloat() * 0.1F);
        for (Vec3 s : spikes) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, s)), true, true, s.x, s.y + 0.1, s.z, 5, 0.2, 0.1, 0.2, 0.15);
            drawSpike(level, s, 1.0, true);
        }
        List<LivingEntity> hit = line.strike(level);
        if (hit.isEmpty()) stand(level, spikes);
    }

    /** Who the spikes strike: its target, and any player (never another Spirebloom or a bystander). */
    private boolean strikable(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof Spirebloom) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e == core().target();
    }

    /** One body on the line: its attack through armour, with no knockback, then a throw straight up. */
    private void strikeHit(ServerLevel level, LivingEntity victim) {
        if (isDeadOrDying() || !MechanicEvent.post(this, "spike", victim)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE;
        if (!AstralAugur.GroundMarks.hurtInPlace(level, victim, damageSources().mobAttack(this), damage)) return;
        strikes++;
        setLastHurtMob(victim);
        Push.set(victim, new Vec3(0.0, KNOCK_UP, 0.0));
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.SPIREBLOOM_STRUCK.get(), getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(STAR_DUST, true, true, victim.getX(), victim.getY() + 0.2, victim.getZ(), 10, 0.3, 0.1, 0.3, 0.0);
    }

    /**
     * One spike, drawn with particles (never a block): a tapering column of dark crystal, broad at its foot and narrow
     * at the top, with a star-white tip; every other spike is shorter, so the row reads jagged. {@code height} scales it
     * (a spike sinking back shrinks); {@code fresh} adds the burst's shards flying off.
     */
    private void drawSpike(ServerLevel level, Vec3 s, double height, boolean fresh) {
        double tall = SPIKE_HEIGHT * Math.max(0.2, height) * (((int) Math.round(s.x * 2 + s.z * 2) & 1) == 0 ? 1.0 : 0.7);
        for (int i = 0; i < SPIKE_BODY.length; i++) {
            double k = (i + 0.5) / (SPIKE_BODY.length + 1);
            level.sendParticles(SPIKE_BODY[i], true, true, s.x, s.y + tall * k, s.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(SPIKE_TIP, true, true, s.x, s.y + tall, s.z, 1, 0.0, 0.0, 0.0, 0.0);
        if (fresh) {
            level.sendParticles(STAR_DUST, true, true, s.x, s.y + tall, s.z, 2, 0.15, 0.1, 0.15, 0.0);
            level.sendParticles(new TrailParticleOption(new Vec3(s.x, s.y + tall + 0.3, s.z), STAR_ARGB, 6), true, true, s.x, s.y + 0.1, s.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ---- the counter: spikes left standing -------------------------------------------------------------------------

    /** A line that struck nobody: its spikes stay standing for {@link #STAND_TICKS}. */
    private void stand(ServerLevel level, List<Vec3> spikes) {
        if (!MechanicEvent.post(this, "stand", null)) return;
        hedge.raise(level, spikes, lineFrom, lineDir, STAND_TICKS);
        hedges++;
        Vec3 mid = spikes.get(spikes.size() / 2);
        level.playSound(null, mid.x, mid.y, mid.z, ModSounds.SPIREBLOOM_STAND.get(), getSoundSource(), 1.2F, 1.0F);
    }

    /**
     * Spikes left standing: a row of crystal along a line, kept server-side, never blocks. Each tick it draws itself,
     * shatters every projectile in it that a player didn't shoot, and fends off every monster that walks into it.
     * Players pass through. Written as a small section of its own so it can move into the shared kit.
     */
    private final class Hedge {
        private List<Vec3> spikes = List.of();
        private Vec3 from = Vec3.ZERO, dir = new Vec3(0, 0, 1);
        private long end;
        private long lastFend = Long.MIN_VALUE;

        void raise(ServerLevel level, List<Vec3> spots, Vec3 lineFrom, Vec3 lineDir, int ticks) {
            spikes = List.copyOf(spots);
            from = lineFrom;
            dir = lineDir;
            end = level.getGameTime() + ticks;
        }

        boolean up() {
            return !spikes.isEmpty();
        }

        void tick(ServerLevel level) {
            if (spikes.isEmpty()) return;
            long now = level.getGameTime();
            if (now >= end) {
                crumble(level);
                return;
            }
            if ((now - end) % 3 == 0) for (Vec3 s : spikes) drawSpike(level, s, 1.0, false);
            AABB area = bounds();
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, area, p -> p.isAlive() && !(p.getOwner() instanceof Player))) {
                if (crosses(p)) shatter(level, p);
            }
            for (Mob m : level.getEntitiesOfClass(Mob.class, area.inflate(1.0, 0.0, 1.0), m -> m instanceof Enemy && !(m instanceof Spirebloom) && m.isAlive())) {
                if (holds(m.getBoundingBox(), m.getBbWidth() * 0.5)) fend(level, m, now);
            }
        }

        private AABB bounds() {
            AABB box = null;
            for (Vec3 s : spikes) {
                AABB b = new AABB(s.x - SPIKE_RADIUS, s.y - 0.5, s.z - SPIKE_RADIUS, s.x + SPIKE_RADIUS, s.y + SPIKE_HEIGHT + 0.5, s.z + SPIKE_RADIUS);
                box = box == null ? b : box.minmax(b);
            }
            return box.inflate(3.0, 1.0, 3.0);
        }

        /** Whether a body with this box (and half-width) stands in a spike: its feet's middle that close to one. */
        private boolean holds(AABB box, double half) {
            double cx = (box.minX + box.maxX) * 0.5, cz = (box.minZ + box.maxZ) * 0.5;
            double r = SPIKE_RADIUS + half;
            for (Vec3 s : spikes) {
                double dx = cx - s.x, dz = cz - s.z;
                if (dx * dx + dz * dz <= r * r && box.minY >= s.y - 1.0 && box.minY <= s.y + SPIKE_HEIGHT) return true;
            }
            return false;
        }

        /** Whether a projectile is in a spike, or passes through one between last tick and next. */
        private boolean crosses(Projectile p) {
            Vec3 at = p.position(), v = p.getDeltaMovement();
            for (int k = -4; k <= 4; k++) {
                Vec3 q = at.add(v.scale(k / 4.0));
                for (Vec3 s : spikes) {
                    double dx = q.x - s.x, dz = q.z - s.z;
                    if (dx * dx + dz * dz <= SPIKE_RADIUS * SPIKE_RADIUS && q.y >= s.y - 0.2 && q.y <= s.y + SPIKE_HEIGHT) return true;
                }
            }
            return false;
        }

        private void shatter(ServerLevel level, Projectile p) {
            if (!MechanicEvent.post(Spirebloom.this, "shatter", p.getOwner() instanceof LivingEntity o ? o : null)) return;
            Vec3 at = p.position();
            p.discard();
            shattered++;
            level.playSound(null, at.x, at.y, at.z, ModSounds.SPIREBLOOM_SHATTER.get(), getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(STAR_DUST, true, true, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.0);
            level.sendParticles(ParticleTypes.CRIT, true, true, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
        }

        /** Pushes a monster out of the spikes, straight across the line, away from its middle. */
        private void fend(ServerLevel level, Mob m, long now) {
            if (!MechanicEvent.post(Spirebloom.this, "fend", m)) return;
            Vec3 rel = m.position().subtract(from).multiply(1, 0, 1);
            Vec3 across = rel.subtract(dir.scale(rel.dot(dir)));
            Vec3 out = across.lengthSqr() < 1.0E-4 ? new Vec3(-dir.z, 0.0, dir.x) : across.normalize();
            Push.set(m, new Vec3(out.x * FEND_PUSH, m.getDeltaMovement().y, out.z * FEND_PUSH));
            m.getNavigation().stop();
            fended++;
            if (now - lastFend >= 10) {
                lastFend = now;
                level.playSound(null, m.getX(), m.getY(), m.getZ(), ModSounds.SPIREBLOOM_FEND.get(), getSoundSource(), 0.9F, 1.0F);
                level.sendParticles(STAR_DUST, true, true, m.getX(), m.getY() + 0.5, m.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
            }
        }

        /** The spikes sink back into the ground. */
        void crumble(ServerLevel level) {
            if (spikes.isEmpty()) return;
            Vec3 mid = spikes.get(spikes.size() / 2);
            level.playSound(null, mid.x, mid.y, mid.z, ModSounds.SPIREBLOOM_CRUMBLE.get(), getSoundSource(), 1.0F, 1.0F);
            for (Vec3 s : spikes) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, groundAt(level, s)), true, true, s.x, s.y + 0.6, s.z, 2, 0.15, 0.3, 0.15, 0.0);
                level.sendParticles(CRYSTAL_DUST, true, true, s.x, s.y + 0.5, s.z, 2, 0.15, 0.3, 0.15, 0.0);
            }
            spikes = List.of();
        }

        void clear() {
            spikes = List.of();
        }
    }

    /** The block under {@code at}, or end stone where that is air. */
    private static BlockState groundAt(ServerLevel level, Vec3 at) {
        BlockState state = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        return state.isAir() ? Blocks.END_STONE.defaultBlockState() : state;
    }

    // ---- death and removal -----------------------------------------------------------------------------------------

    /** A dead Spirebloom bursts nothing: its cracks close unspent and any spikes it left sink back. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!isDeadOrDying() || !(level() instanceof ServerLevel level)) return;
        line.cancel(level);
        hedge.crumble(level);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        line.clear();
        hedge.clear();
        super.onRemoval(reason);
    }

    // ---- test hooks ------------------------------------------------------------------------------------------------

    /** Its next tell may start {@code ticks} from now, whatever the rhythm says. Public as a test hook. */
    public void nextTellIn(int ticks) {
        nextTell = level().getGameTime() + ticks;
    }

    /** The game time its next tell may start. Public as a test hook. */
    public long nextTell() {
        return nextTell;
    }

    /** The spike spots of the line now cracking, or an empty list. Public as a test hook. */
    public List<Vec3> lineSpikes() {
        List<Vec3> out = new ArrayList<>();
        for (AstralAugur.GroundMarks.Mark m : line.marks()) out.add(m.centre());
        return out;
    }

    /** The spikes left standing, or an empty list. Public as a test hook. */
    public List<Vec3> standingSpikes() {
        return hedge.spikes;
    }

    /** Raises standing spikes along a line from it toward {@code toward}, as a line that struck nobody. Test hook. */
    public void standToward(Vec3 toward) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 d = toward.subtract(position()).multiply(1, 0, 1);
        lineDir = d.lengthSqr() < 1.0E-6 ? forward() : d.normalize();
        lineFrom = position();
        List<Vec3> spots = groundedSpots(level, lineFrom, lineDir);
        if (!spots.isEmpty()) stand(level, spots);
    }

    /** Tells started, bursts, blows landed, lines left standing, shots shattered, monsters fended off. Test hooks. */
    public int tells() {
        return tells;
    }

    public int bursts() {
        return bursts;
    }

    public int strikes() {
        return strikes;
    }

    public int hedges() {
        return hedges;
    }

    public int shattered() {
        return shattered;
    }

    public int fended() {
        return fended;
    }

    // ---- sounds ----------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.SPIREBLOOM_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SPIREBLOOM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SPIREBLOOM_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.2F;
    }
}
