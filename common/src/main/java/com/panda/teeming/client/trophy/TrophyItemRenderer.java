package com.panda.teeming.client.trophy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.registry.ModComponents;
import com.panda.teeming.trophy.TrophyMob;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The figure on a trophy item, so each trophy shows its own mob in the inventory, in the hand and on the ground. The
 * client item draws the plinth model and then this, which stands the stack's mob on it like the placed trophy.
 */
public final class TrophyItemRenderer implements SpecialModelRenderer<TrophyMob> {
    public static final Identifier TYPE = TeemingMod.id("trophy_figure");
    /** The item's plinth is the block model unrotated, whose nameplate faces north; the figure faces the same way. */
    private static final float YAW = 180.0F;

    public record Unbaked() implements SpecialModelRenderer.Unbaked<TrophyMob> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<TrophyMob> bake(BakingContext context) {
            return new TrophyItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    @Override
    public void submit(@Nullable TrophyMob mob, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean hasFoil, int outlineColor) {
        Minecraft mc = Minecraft.getInstance();
        if (mob == null || mob.isEmpty() || mc.level == null) return;      // no level (title screen): the bare plinth
        EntityRenderState state = TrophyFigures.pose(mc.getEntityRenderDispatcher(), mc.level, mob, Vec3.ZERO, YAW, light);
        if (state == null) return;
        float scale = TrophyFigures.fit(mob.mob());
        pose.pushPose();
        pose.translate(0.5F, TrophyFigures.PLINTH_TOP, 0.5F);
        pose.scale(scale, scale, scale);
        mc.getEntityRenderDispatcher().submit(state, mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState,
                0.0, 0.0, 0.0, pose, collector);
        pose.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float half = TrophyFigures.FIT_WIDTH / 2, top = TrophyFigures.PLINTH_TOP + TrophyFigures.FIT_HEIGHT;
        for (float x : new float[] {0.5F - half, 0.5F + half}) {
            for (float y : new float[] {0.0F, top}) {
                for (float z : new float[] {0.5F - half, 0.5F + half}) output.accept(new Vector3f(x, y, z));
            }
        }
    }

    @Override
    public @Nullable TrophyMob extractArgument(ItemStack stack) {
        return stack.get(ModComponents.TROPHY_MOB.get());
    }
}
