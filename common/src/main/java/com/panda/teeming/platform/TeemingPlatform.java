package com.panda.teeming.platform;

import com.panda.teeming.fieldguide.FieldGuideProgress;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;

/** What common code needs from the mod loader. Each loader module provides one, found with {@link Services}. */
public interface TeemingPlatform {
    /** "neoforge" or "fabric". */
    String loaderName();

    /** Whether a mob may change blocks: the mobGriefing rule, plus whatever the loader lets other mods decide. */
    boolean canGrief(ServerLevel level, Entity entity);

    /** The display name of the mod that owns a namespace, or the namespace itself. */
    String modName(String namespace);

    /** A builder for a creative tab of the mod's own, which the loader places among its pages. */
    CreativeModeTab.Builder tabBuilder();

    /** The player's Field Guide progress: a player attachment, saved, copied on death and synced to its owner. */
    FieldGuideProgress fieldGuide(Player player);

    void setFieldGuide(Player player, FieldGuideProgress progress);
}
