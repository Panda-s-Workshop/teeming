package com.panda.teeming.client.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Base for the Teeming models. The geometry comes from {@code CreatureLayers}; subclasses only animate. Parts that
 * belong to some looks only are shown or hidden from its "only" table every frame.
 */
public abstract class TeemingModel extends EntityModel<TeemingRenderState> {
    protected static final float DEG = Mth.DEG_TO_RAD;
    private final Map<ModelPart, String[]> only = new HashMap<>();
    private final Function<String, ModelPart> lookup;

    protected TeemingModel(ModelPart root, Map<String, String[]> onlyTable) {
        this(root, onlyTable, RenderTypes::entityCutout);
    }

    protected TeemingModel(ModelPart root, Map<String, String[]> onlyTable, Function<Identifier, RenderType> renderType) {
        super(root, renderType);
        this.lookup = root.createPartLookup();
        onlyTable.forEach((name, looks) -> only.put(part(name), looks));
    }

    protected final ModelPart part(String name) {
        ModelPart p = lookup.apply(name);
        if (p == null) throw new IllegalStateException(getClass().getSimpleName() + " has no part '" + name + "'");
        return p;
    }

    @Override
    public final void setupAnim(TeemingRenderState state) {
        super.setupAnim(state);
        for (var e : only.entrySet()) {
            boolean show = false;
            for (String look : e.getValue()) show |= look.equals(state.look);
            e.getKey().visible = show;
        }
        animate(state, state.ageInTicks, state.walkAnimationPos, Math.min(1.0F, state.walkAnimationSpeed));
    }

    /** Poses the model; every part starts from its rest pose (the base class resets them). */
    protected abstract void animate(TeemingRenderState s, float t, float pos, float speed);

    protected static float smooth(float a) {
        a = Mth.clamp(a, 0.0F, 1.0F);
        return a * a * (3.0F - 2.0F * a);
    }

    /** 0 → 1 over the first {@code in} of a phase, holds, 1 → 0 over the last {@code out} (both as 0..1 progress). */
    protected static float envelope(float progress, float in, float out) {
        float a = in > 0 ? progress / in : 1.0F;
        float b = out > 0 ? (1.0F - progress) / out : 1.0F;
        return smooth(Math.min(a, b));
    }
}
