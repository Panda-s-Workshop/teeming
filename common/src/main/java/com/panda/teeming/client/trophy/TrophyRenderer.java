package com.panda.teeming.client.trophy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.trophy.TrophyBlock;
import com.panda.teeming.trophy.TrophyBlockEntity;
import com.panda.teeming.trophy.TrophyMob;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stands the trophy's mob on its plinth: the mob's own renderer and model, in its look, frozen, scaled so
 * the larger of its height and width fits {@link #FIT} blocks, facing out of the plinth's front.
 */
public class TrophyRenderer implements BlockEntityRenderer<TrophyBlockEntity, TrophyRenderer.State> {
    public static final float FIT = 0.6F;
    private static final float PLINTH_TOP = 4 / 16F;

    public static class State extends BlockEntityRenderState {
        public @Nullable EntityRenderState mob;
        public float scale = 1.0F;
    }

    private final EntityRenderDispatcher entities;
    /** One client-only entity per mob and look, never added to the level. */
    private final Map<TrophyMob, Entity> figures = new HashMap<>();

    public TrophyRenderer(BlockEntityRendererProvider.Context context) {
        entities = context.entityRenderer();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrophyBlockEntity trophy, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(trophy, state, partialTick, camera, crumbling);
        state.mob = null;
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
        RosterStats.Entry row = RosterStats.get(mob.mob());
        state.scale = row == null ? FIT : FIT / Math.max(row.height(), row.width());
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
    }
}
