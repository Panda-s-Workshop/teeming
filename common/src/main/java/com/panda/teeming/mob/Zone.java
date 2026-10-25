package com.panda.teeming.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Areas a mob owns for a while, with an effect on whoever is inside: a cloud, a pool, a ring on the ground. They live
 * only on the server, in the owner, never as blocks or saved data, so a reload or the owner's removal simply ends them.
 * The owner ticks them ({@link #tick}) from wherever they should keep working: its AI step, so they stop while it dies,
 * or its {@code tick()}, so they outlast its death throes. It clears them on removal. At most {@code max} are kept;
 * opening one more drops the oldest.
 *
 * <p>The owner keeps its own numbers and effects: who the areas touch, what they do to them each tick and how they
 * look are its callbacks.
 */
public final class Zone {
    /** The room an area takes up around its centre. */
    public interface Shape {
        /** A box around everything the shape can hold, for the entity lookup. */
        AABB bounds(Vec3 centre);

        /** Whether a hitbox {@code box} counts as inside. */
        boolean holds(Vec3 centre, AABB box);

        /** A ball: any part of a hitbox within {@code radius} of the centre. */
        static Shape ball(double radius) {
            return new Shape() {
                @Override
                public AABB bounds(Vec3 centre) {
                    return new AABB(centre, centre).inflate(radius);
                }

                @Override
                public boolean holds(Vec3 centre, AABB box) {
                    return box.distanceToSqr(centre) <= radius * radius;
                }
            };
        }

        /**
         * A flat disc lying on its centre, like a vanilla effect cloud: a hitbox that reaches into the slab
         * {@code height} thick, with its middle within {@code radius} across.
         */
        static Shape disc(double radius, double height) {
            return new Shape() {
                @Override
                public AABB bounds(Vec3 centre) {
                    return new AABB(centre.x - radius, centre.y, centre.z - radius, centre.x + radius, centre.y + height, centre.z + radius);
                }

                @Override
                public boolean holds(Vec3 centre, AABB box) {
                    double dx = (box.minX + box.maxX) * 0.5 - centre.x, dz = (box.minZ + box.maxZ) * 0.5 - centre.z;
                    return box.intersects(bounds(centre)) && dx * dx + dz * dz <= radius * radius;
                }
            };
        }
    }

    /** One area: fixed where it was opened until its end time, or sitting on a carrier for as long as that lives. */
    public static final class Area {
        private final Vec3 centre;
        private final long end;
        private final @Nullable Entity carrier;

        private Area(Vec3 centre, long end, @Nullable Entity carrier) {
            this.centre = centre;
            this.end = end;
            this.carrier = carrier;
        }

        public Vec3 centre() {
            return carrier != null ? carrier.position() : centre;
        }

        private boolean over(long now) {
            return carrier != null ? !carrier.isAlive() : now >= end;
        }
    }

    private final Shape shape;
    private final int max;
    private final List<Area> areas = new ArrayList<>(2);
    private Predicate<LivingEntity> who = e -> true;
    private @Nullable Consumer<LivingEntity> effect;
    private @Nullable BiConsumer<ServerLevel, Vec3> look;

    /** Areas of {@code shape}, at most {@code max} at once. */
    public Zone(Shape shape, int max) {
        this.shape = shape;
        this.max = Math.max(1, max);
    }

    /** Each tick, {@code effect} for every living thing inside an area that {@code who} accepts. */
    public Zone affects(Predicate<LivingEntity> who, Consumer<LivingEntity> effect) {
        this.who = who;
        this.effect = effect;
        return this;
    }

    /** Each tick, before the effect, {@code look} draws each area from its centre. */
    public Zone drawn(BiConsumer<ServerLevel, Vec3> look) {
        this.look = look;
        return this;
    }

    /** Opens an area at {@code centre} for {@code ticks} ticks, starting this tick. */
    public Area open(ServerLevel level, Vec3 centre, int ticks) {
        return add(new Area(centre, level.getGameTime() + ticks, null));
    }

    /**
     * Opens an area that sits on {@code carrier} and lasts as long as it does: a vanilla effect cloud that times and
     * applies its own effect, with the owner tracing its edge.
     */
    public Area carried(Entity carrier) {
        return add(new Area(carrier.position(), Long.MAX_VALUE, carrier));
    }

    private Area add(Area area) {
        while (areas.size() >= max) areas.removeFirst();
        areas.add(area);
        return area;
    }

    /** Ends areas whose time is up, then draws each one left and applies its effect to those inside. */
    public void tick(ServerLevel level) {
        if (areas.isEmpty()) return;
        long now = level.getGameTime();
        areas.removeIf(a -> a.over(now));
        for (Area area : areas) {
            Vec3 at = area.centre();
            if (look != null) look.accept(level, at);
            if (effect == null) continue;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, shape.bounds(at), e -> who.test(e) && shape.holds(at, e.getBoundingBox()))) {
                effect.accept(e);
            }
        }
    }

    /** The centres of the areas still open. */
    public List<Vec3> centres() {
        return areas.stream().map(Area::centre).toList();
    }

    /** Ends every area at once. */
    public void clear() {
        areas.clear();
    }
}
