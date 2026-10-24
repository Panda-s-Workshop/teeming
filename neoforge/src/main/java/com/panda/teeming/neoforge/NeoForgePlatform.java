package com.panda.teeming.neoforge;

import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.platform.TeemingPlatform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.EventHooks;

/** {@link TeemingPlatform} on NeoForge: NeoForge's grief hook and the mod list. */
public final class NeoForgePlatform implements TeemingPlatform {
    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public boolean canGrief(ServerLevel level, Entity entity) {
        return EventHooks.canEntityGrief(level, entity);
    }

    @Override
    public String modName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    @Override
    public CreativeModeTab.Builder tabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public FieldGuideProgress fieldGuide(Player player) {
        return player.getData(NeoForgeAttachments.FIELD_GUIDE);
    }

    @Override
    public void setFieldGuide(Player player, FieldGuideProgress progress) {
        player.setData(NeoForgeAttachments.FIELD_GUIDE, progress);
    }
}
