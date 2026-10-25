package com.panda.teeming.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A shot that is not an entity: its owner moves it one tick along its {@link Path} at a time, it stops at the first
 * block or the first hitbox within a radius of that stretch, and the owner draws it with particles. So it can be
 * dodged, it never homes, and it ends where its path does. Shots are not saved; a reload drops the ones in the air.
 * A hit is {@link #damage}: a mob's projectile, so a raised shield takes it.
 *
 * <p>What a shot may hit, how it looks, and what a hit or a stop does are the owner's {@link Rules}. One shot at a time
 * steps with {@link #step}; a {@link Volley} carries many and looks hitboxes up once a tick for all of them.
 */
public final class Shot {
    /** The way a shot goes: where it is after some ticks of flight, and how many ticks it flies. */
    public interface Path {
        Vec3 at(double ticks);

        /** Ticks of flight until the path ends; the last tick may be a part of one. */
        double ticks();

        /** Straight from {@code from} along {@code dir} at {@code speed} blocks a tick, ending {@code range} blocks out. */
        static Path line(Vec3 from, Vec3 dir, double speed, double range) {
            Vec3 step = dir.normalize().scale(speed);
            double ticks = range / speed;
            return new Path() {
                @Override
                public Vec3 at(double t) {
                    return from.add(step.scale(t));
                }

                @Override
                public double ticks() {
                    return ticks;
                }
            };
        }
    }

    /** Draws this tick's stretch, from where the shot was to where it got (a hit point, a wall, or on along its path). */
    @FunctionalInterface
    public interface Draw {
        void draw(ServerLevel level, Vec3 from, Vec3 to);
    }

    /** The shot reached {@code victim}'s hitbox at {@code at}; it is spent. */
    @FunctionalInterface
    public interface Hit {
        void hit(ServerLevel level, Shot shot, LivingEntity victim, Vec3 at);
    }

    /** The shot stopped at {@code at} without a hit: at a block ({@code blocked}) or at the end of its path. */
    @FunctionalInterface
    public interface Stop {
        void stop(ServerLevel level, Shot shot, Vec3 at, boolean blocked);
    }

    /** One owner's rules for its shots: what they may hit, how they look, and what a hit or a stop does. */
    public record Rules(Predicate<LivingEntity> canHit, Draw draw, Hit hit, Stop stop) {}

    private static final double DONE = 1.0E-9;

    private final Path path;
    private double flown;
    private double travelled;
    private Vec3 pos;
    // this tick's stretch, from advance() to resolve()
    private Vec3 from;
    private Vec3 to;
    private boolean blocked;
    private double dt;

    public Shot(Path path) {
        this.path = path;
        this.pos = path.at(0.0);
        this.from = pos;
        this.to = pos;
    }

    /** A hit from {@code owner}'s shot: a mob's projectile, so a shield raised toward the owner takes it. */
    public static DamageSource damage(LivingEntity owner) {
        return owner.damageSources().mobProjectile(owner, owner);
    }

    /** Where the shot is now. */
    public Vec3 pos() {
        return pos;
    }

    /** How far it has gone so far, in blocks, stretch by stretch. */
    public double travelled() {
        return travelled;
    }

    /**
     * Moves the shot one tick, as {@code owner}, catching hitboxes within {@code radius}; true when it is spent (hit
     * something, or stopped).
     */
    public boolean step(ServerLevel level, Entity owner, double radius, Rules rules) {
        advance(level, owner);
        return resolve(level, level.getEntitiesOfClass(LivingEntity.class, sweep(radius), rules.canHit()), radius, rules);
    }

    /** This tick's stretch, cut short at the first block on it. */
    private void advance(ServerLevel level, Entity owner) {
        dt = Math.min(1.0, path.ticks() - flown);
        from = pos;
        to = path.at(flown + dt);
        BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        blocked = wall.getType() != HitResult.Type.MISS;
        if (blocked) to = wall.getLocation();
    }

    private AABB sweep(double radius) {
        return new AABB(from, to).inflate(radius + 0.1);
    }

    /** Finds the nearest hitbox on this tick's stretch among {@code near}, draws the stretch, and hits or stops. */
    private boolean resolve(ServerLevel level, List<LivingEntity> near, double radius, Rules rules) {
        LivingEntity victim = null;
        Vec3 at = to;
        double best = Double.MAX_VALUE;
        AABB sweep = sweep(radius);
        for (LivingEntity e : near) {
            if (!sweep.intersects(e.getBoundingBox())) continue;
            AABB box = e.getBoundingBox().inflate(radius);
            Optional<Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < best) {
                best = from.distanceToSqr(hit.get());
                victim = e;
                at = hit.get();
            }
        }
        rules.draw().draw(level, from, at);
        if (victim != null) {
            travelled += from.distanceTo(at);
            pos = at;
            rules.hit().hit(level, this, victim, at);
            return true;
        }
        travelled += from.distanceTo(to);
        flown += dt;
        pos = to;
        if (blocked || flown >= path.ticks() - DONE) {
            rules.stop().stop(level, this, to, blocked);
            return true;
        }
        return false;
    }

    /**
     * Many shots under one owner's rules, flown together: each tick every shot finds its stretch, one lookup finds the
     * hitboxes near any of them, and each is then hit or stopped in the order fired.
     */
    public static final class Volley {
        private final Entity owner;
        private final double radius;
        private final Rules rules;
        private final List<Shot> shots = new ArrayList<>();

        /** Shots from {@code owner} that catch hitboxes within {@code radius}. */
        public Volley(Entity owner, double radius, Rules rules) {
            this.owner = owner;
            this.radius = radius;
            this.rules = rules;
        }

        /** Fires a shot along {@code path}; it first moves on the owner's next {@link #tick}. */
        public Shot fire(Path path) {
            Shot shot = new Shot(path);
            shots.add(shot);
            return shot;
        }

        /** Moves every shot one tick. Shots fired from a hit or a stop wait for the next tick. */
        public void tick(ServerLevel level) {
            int n = shots.size();
            if (n == 0) return;
            AABB all = null;
            for (int i = 0; i < n; i++) {
                Shot s = shots.get(i);
                s.advance(level, owner);
                all = all == null ? s.sweep(radius) : all.minmax(s.sweep(radius));
            }
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, all, rules.canHit());
            int kept = 0;
            for (int i = 0; i < n; i++) {
                Shot s = shots.get(i);
                if (!s.resolve(level, near, radius, rules)) shots.set(kept++, s);
            }
            for (int i = n; i < shots.size(); i++) shots.set(kept++, shots.get(i));
            shots.subList(kept, shots.size()).clear();
        }

        public int size() {
            return shots.size();
        }

        /** Drops every shot in the air. Not from a hit or a stop, which run inside {@link #tick}. */
        public void clear() {
            shots.clear();
        }
    }
}
