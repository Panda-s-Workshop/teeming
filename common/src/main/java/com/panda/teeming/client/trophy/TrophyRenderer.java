package com.panda.teeming.client.trophy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.panda.teeming.client.mob.MobBounds;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.trophy.TrophyBlock;
import com.panda.teeming.trophy.TrophyBlockEntity;
import com.panda.teeming.trophy.TrophyMob;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stands the trophy's mob on its plinth: the mob's own renderer and model, in its look, frozen, facing out of the
 * plinth's front, scaled so its model (wings, horns and tails too, not just its hitbox) stands at most {@link #FIT_HEIGHT}
 * tall and {@link #FIT_WIDTH} across. Writes the mob's name on the brass nameplate.
 */
public class TrophyRenderer implements BlockEntityRenderer<TrophyBlockEntity, TrophyRenderer.State> {
    public static final float FIT_HEIGHT = 0.6F;
    public static final float FIT_WIDTH = 0.8F;
    private static final float PLINTH_TOP = 4 / 16F;
    /** The nameplate's front face and centre, in pixels from the block's centre and floor, and its usable width. */
    private static final float PLATE_Z = 6.5F / 16F, PLATE_Y = 1.5F / 16F, PLATE_WIDTH = 9.5F / 16F;
    private static final float TEXT_SCALE = 1 / 96F;
    private static final int TEXT_COLOR = 0xFF2A1E0E;

    public static class State extends BlockEntityRenderState {
        public @Nullable EntityRenderState mob;
        public float scale = 1.0F;
        public float yaw;
        public @Nullable FormattedCharSequence name;
        public int nameWidth;
    }

    private final EntityRenderDispatcher entities;
    private final Font font;
    /** One client-only entity per mob and look, never added to the level. */
    private final Map<TrophyMob, Entity> figures = new HashMap<>();

    public TrophyRenderer(BlockEntityRendererProvider.Context context) {
        entities = context.entityRenderer();
        font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrophyBlockEntity trophy, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(trophy, state, partialTick, camera, crumbling);
        state.mob = null;
        state.name = null;
        TrophyMob mob = trophy.mob();
        if (mob.isEmpty() || trophy.getLevel() == null) return;
        Entity figure = figure(trophy.getLevel(), mob);
        if (figure == null) return;
        float yaw = trophy.getBlockState().getValue(TrophyBlock.FACING).toYRot();
        figure.setPos(Vec3.atBottomCenterOf(trophy.getBlockPos()).add(0, PLINTH_TOP, 0));
        figure.setYRot(yaw);
        figure.setXRot(0.0F);
        if (figure instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = yaw;
            living.yHeadRot = living.yHeadRotO = yaw;
        }
        EntityRenderState s = entities.extractEntity(figure, 0.0F);
        s.lightCoords = state.lightCoords;
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
        state.mob = s;
        state.scale = fit(mob.mob());
        state.yaw = yaw;
        state.name = figure.getType().getDescription().getVisualOrderText();
        state.nameWidth = font.width(state.name);
    }

    /** The scale that fits the mob's model on the plinth; its hitbox when the model's size isn't known. */
    public static float fit(String mob) {
        float[] model = MobBounds.of(mob);
        if (model != null) return Math.min(FIT_HEIGHT / model[1], FIT_WIDTH / Math.max(model[0], model[2]));
        RosterStats.Entry row = RosterStats.get(mob);
        return row == null ? FIT_HEIGHT : Math.min(FIT_HEIGHT / row.height(), FIT_WIDTH / row.width());
    }

    private @Nullable Entity figure(Level level, TrophyMob mob) {
        return figures.computeIfAbsent(mob, key -> {
            Entity e = BuiltInRegistries.ENTITY_TYPE.getOptional(TeemingMod.id(key.mob())).map(t -> t.create(level, EntitySpawnReason.LOAD)).orElse(null);
            if (e == null) return null;
            e.setId(-2000 - figures.size());                         // renderers read the id; the figure never joins the level
            if (e instanceof TeemingCreature c) c.core().setLook(key.look(), false);
            return e;
        });
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.mob == null) return;
        pose.pushPose();
        pose.translate(0.5F, PLINTH_TOP, 0.5F);
        pose.scale(state.scale, state.scale, state.scale);
        entities.submit(state.mob, camera, 0.0, 0.0, 0.0, pose, collector);
        pose.popPose();
        if (state.name != null) {
            pose.pushPose();
            pose.translate(0.5F, PLATE_Y, 0.5F);
            pose.rotateDegrees(Axis.YP, -state.yaw);
            pose.translate(0.0F, 0.0F, PLATE_Z + 0.002F);
            float k = Math.min(TEXT_SCALE, PLATE_WIDTH / Math.max(1, state.nameWidth));
            pose.scale(k, -k, k);
            collector.submitText(pose, -state.nameWidth / 2.0F, -4.0F, state.name, false, Font.DisplayMode.POLYGON_OFFSET,
                    state.lightCoords, TEXT_COLOR, 0, 0);
            pose.popPose();
        }
    }
}
