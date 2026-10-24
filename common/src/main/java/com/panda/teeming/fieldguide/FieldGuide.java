package com.panda.teeming.fieldguide;

import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The Field Guide's server side. "Seen": a Teeming mob within 24 blocks that the player has a line of sight
 * to, checked every 20 ticks per player against nearby Teeming mobs only. A player kill counts too; the third kill
 * reveals the mob's tell, counter, drops and looks. The client turns the synced changes into toasts.
 */
public final class FieldGuide {
    public static final double SEEN_RANGE = 24.0;
    public static final int CHECK_EVERY = 20;
    public static final int KILLS_TO_REVEAL = 3;

    private FieldGuide() {}

    public static FieldGuideProgress get(Player player) {
        return Services.PLATFORM.fieldGuide(player);
    }

    /** Replaces the player's progress (server). Public for tests and commands. */
    public static void set(Player player, FieldGuideProgress progress) {
        Services.PLATFORM.setFieldGuide(player, progress);
    }

    /** Server, after each level tick: each player is checked once every 20 ticks, spread by entity id. */
    public static void tick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && (level.getGameTime() + player.getId()) % CHECK_EVERY == 0) look(player);
        }
    }

    /** Records every Teeming mob the player can see now; returns how many were new. Public as a test hook. */
    public static int look(ServerPlayer player) {
        FieldGuideProgress before = get(player);
        FieldGuideProgress after = before;
        int fresh = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SEEN_RANGE),
                m -> m instanceof TeemingCreature && m.isAlive())) {
            if (!sees(player, mob)) continue;
            TeemingCreature creature = (TeemingCreature) mob;
            if (!after.hasSeen(creature.mobId())) fresh++;
            after = after.withSeen(creature.mobId(), creature.look());
        }
        if (after != before) set(player, after);
        return fresh;
    }

    /** The sight rule: within 24 blocks and in the player's line of sight. */
    public static boolean sees(ServerPlayer player, Mob mob) {
        return mob.distanceTo(player) <= SEEN_RANGE && player.hasLineOfSight(mob);
    }

    /** A Teeming mob died: a player who killed it gets the kill. Called by the mob bases. */
    public static void onKilled(LivingEntity mob, DamageSource source) {
        if (mob.level().isClientSide() || !(source.getEntity() instanceof ServerPlayer player) || !(mob instanceof TeemingCreature creature)) return;
        set(player, get(player).withKill(creature.mobId(), creature.look()));
    }
}
