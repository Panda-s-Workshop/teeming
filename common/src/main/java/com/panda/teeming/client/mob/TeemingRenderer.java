package com.panda.teeming.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.TeemingCreature;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * One renderer for every Teeming mob: the synced look picks {@code textures/entity/<mob>/<look>.png}, and looks with a
 * glowmask get an emissive layer ({@code <look>_glow.png}: eyes, embers, runes) at full brightness. Mobs with a weak
 * point show {@code <look>_weak.png}, the weak point in weak-point gold, at full brightness while they are vulnerable.
 */
public class TeemingRenderer<T extends Mob & TeemingCreature> extends MobRenderer<T, TeemingRenderState, TeemingModel> {
    private final String mob;
    private final Set<String> glowLooks;
    private final float modelScale;
    private final int glowStages;
    private final Map<String, Identifier> textures = new HashMap<>();
    private final Map<String, Identifier> glows = new HashMap<>();
    private final Map<String, Identifier> weaks = new HashMap<>();

    /** {@code glowStages} above 1: the look's glowmask is {@code <look>_glow_<stage>.png}, by {@code MobCore.stage}. */
    public TeemingRenderer(EntityRendererProvider.Context ctx, TeemingModel model, String mob, Set<String> glowLooks, float shadow, float modelScale,
                           int glowStages, boolean weakPoint) {
        super(ctx, model, shadow);
        this.mob = mob;
        this.glowLooks = glowLooks;
        this.modelScale = modelScale;
        this.glowStages = glowStages;
        if (!glowLooks.isEmpty()) addLayer(new Glow(this));
        if (weakPoint) addLayer(new WeakPoint(this));
    }

    @Override
    public TeemingRenderState createRenderState() {
        return new TeemingRenderState();
    }

    @Override
    public void extractRenderState(T entity, TeemingRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        String look = entity.look();
        state.look = look.isEmpty() ? "base" : look;
        state.phase = entity.phase().ordinal();
        state.progress = entity.phaseProgress(partialTicks);
        state.phaseTime = entity.core().elapsed() + (entity.core().idle() ? 0.0F : partialTicks);
        state.flags = entity.core().flags();
        state.stage = Math.min(entity.core().stage(), Math.max(0, glowStages - 1));
        state.vulnerable = entity.vulnerable();
        state.attack = entity.getSwingAnimation(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(TeemingRenderState state) {
        return textures.computeIfAbsent(state.look, l -> TeemingMod.id("textures/entity/" + mob + "/" + l + ".png"));
    }

    @Override
    protected void scale(TeemingRenderState state, PoseStack poseStack) {
        if (modelScale != 1.0F) poseStack.scale(modelScale, modelScale, modelScale);
    }

    private final class Glow extends RenderLayer<TeemingRenderState, TeemingModel> {
        Glow(TeemingRenderer<T> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, TeemingRenderState state, float yRot, float xRot) {
            if (state.isInvisible || !glowLooks.contains(state.look)) return;
            String name = glowStages > 1 ? state.look + "_glow_" + state.stage : state.look + "_glow";
            Identifier glow = glows.computeIfAbsent(name, n -> TeemingMod.id("textures/entity/" + mob + "/" + n + ".png"));
            collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(glow), lightCoords,
                    OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }

    private final class WeakPoint extends RenderLayer<TeemingRenderState, TeemingModel> {
        WeakPoint(TeemingRenderer<T> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, TeemingRenderState state, float yRot, float xRot) {
            if (state.isInvisible || !state.vulnerable) return;
            Identifier weak = weaks.computeIfAbsent(state.look, l -> TeemingMod.id("textures/entity/" + mob + "/" + l + "_weak.png"));
            collector.order(2).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(weak), lightCoords,
                    OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
