package com.panda.teeming.client.trophy;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.client.mob.MobBounds;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.trophy.TrophyMob;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The trophy figures, shared by the placed trophy and the trophy item: one client-only entity per mob and look, never
 * added to the level, posed as a statue and scaled so its model (wings, horns and tails too) stands at most
 * {@link #FIT_HEIGHT} tall and {@link #FIT_WIDTH} across.
 */
public final class TrophyFigures {
    public static final float FIT_HEIGHT = 0.6F;
    public static final float FIT_WIDTH = 0.8F;
    /** Where the figure stands: the plinth's top, in blocks from the floor. */
    public static final float PLINTH_TOP = 4 / 16F;

    private static final Map<TrophyMob, Entity> FIGURES = new HashMap<>();
    private static @Nullable Level figuresLevel;

    private TrophyFigures() {}

    /** The scale that fits the mob's model on the plinth; its hitbox when the model's size isn't known. */
    public static float fit(String mob) {
        float[] model = MobBounds.of(mob);
        if (model != null) return Math.min(FIT_HEIGHT / model[1], FIT_WIDTH / Math.max(model[0], model[2]));
        RosterStats.Entry row = RosterStats.get(mob);
        return row == null ? FIT_HEIGHT : Math.min(FIT_HEIGHT / row.height(), FIT_WIDTH / row.width());
    }

    /** The figure's render state, standing at {@code at} and facing {@code yaw}, frozen mid-stride; null if the mob is unknown. */
    public static @Nullable EntityRenderState pose(EntityRenderDispatcher entities, Level level, TrophyMob mob, Vec3 at, float yaw, int light) {
        Entity figure = figure(level, mob);
        if (figure == null) return null;
        figure.setPos(at);
        figure.setYRot(yaw);
        figure.setXRot(0.0F);
        if (figure instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = yaw;
            living.yHeadRot = living.yHeadRotO = yaw;
        }
        EntityRenderState s = entities.extractEntity(figure, 0.0F);
        s.lightCoords = light;
        s.shadowRadius = 0.0F;
        s.shadowPieces.clear();
        s.nameTag = null;
        if (s instanceof LivingEntityRenderState living) {                // a statue: facing out, head straight, legs still
            living.bodyRot = yaw;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
            living.walkAnimationPos = 0.0F;
            living.walkAnimationSpeed = 0.0F;
        }
        return s;
    }

    /** The figure entity for this mob and look, or null if the mob is unknown. */
    public static @Nullable Entity figure(Level level, TrophyMob mob) {
        if (figuresLevel != level) {                                    // figures belong to one level; a new world starts over
            FIGURES.clear();
            figuresLevel = level;
        }
        return FIGURES.computeIfAbsent(mob, key -> {
            Entity e = BuiltInRegistries.ENTITY_TYPE.getOptional(TeemingMod.id(key.mob())).map(t -> t.create(level, EntitySpawnReason.LOAD)).orElse(null);
            if (e == null) return null;
            e.setId(-2000 - FIGURES.size());                         // renderers read the id; the figure never joins the level
            if (e instanceof TeemingCreature c) c.core().setLook(key.look(), false);
            return e;
        });
    }
}
