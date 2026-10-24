package com.panda.teeming.fabric;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.platform.TeemingPlatform;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.gamerules.GameRules;

/** {@link TeemingPlatform} on Fabric: the mobGriefing rule and Fabric Loader's mod list. */
public final class FabricPlatform implements TeemingPlatform {
    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public boolean canGrief(ServerLevel level, Entity entity) {
        return level.getGameRules().get(GameRules.MOB_GRIEFING);
    }

    @Override
    public String modName(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace).map(c -> c.getMetadata().getName()).orElse(namespace);
    }

    @Override
    public CreativeModeTab.Builder tabBuilder() {
        return FabricCreativeModeTab.builder();
    }

    /** The Field Guide: saved, kept on death, synced to the owning player only. */
    static final AttachmentType<FieldGuideProgress> FIELD_GUIDE = AttachmentRegistry.create(TeemingMod.id("field_guide"), builder -> builder
            .initializer(() -> FieldGuideProgress.EMPTY)
            .persistent(FieldGuideProgress.CODEC)
            .copyOnDeath()
            .syncWith(FieldGuideProgress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    @Override
    public FieldGuideProgress fieldGuide(Player player) {
        return player.getAttachedOrElse(FIELD_GUIDE, FieldGuideProgress.EMPTY);
    }

    @Override
    public void setFieldGuide(Player player, FieldGuideProgress progress) {
        player.setAttached(FIELD_GUIDE, progress);
    }
}
