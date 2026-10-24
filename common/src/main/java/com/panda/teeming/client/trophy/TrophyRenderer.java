package com.panda.teeming.client.trophy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.panda.teeming.trophy.TrophyBlock;
import com.panda.teeming.trophy.TrophyBlockEntity;
import com.panda.teeming.trophy.TrophyMob;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stands the trophy's mob on its plinth: the mob's own renderer and model, in its look, frozen, facing out of the
 * plinth's front, fitted by {@link TrophyFigures}. Writes the mob's name on the brass nameplate.
 */
public class TrophyRenderer implements BlockEntityRenderer<TrophyBlockEntity, TrophyRenderer.State> {
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
        Level level = trophy.getLevel();
        if (mob.isEmpty() || level == null) return;
        float yaw = trophy.getBlockState().getValue(TrophyBlock.FACING).toYRot();
        Vec3 at = Vec3.atBottomCenterOf(trophy.getBlockPos()).add(0, TrophyFigures.PLINTH_TOP, 0);
        state.mob = TrophyFigures.pose(entities, level, mob, at, yaw, state.lightCoords);
        if (state.mob == null) return;
        state.scale = TrophyFigures.fit(mob.mob());
        state.yaw = yaw;
        Entity figure = TrophyFigures.figure(level, mob);
        state.name = figure == null ? null : figure.getType().getDescription().getVisualOrderText();
        state.nameWidth = state.name == null ? 0 : font.width(state.name);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.mob == null) return;
        pose.pushPose();
        pose.translate(0.5F, TrophyFigures.PLINTH_TOP, 0.5F);
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
