package com.panda.teeming.mob;

import com.panda.teeming.roster.RosterStats;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

/** A mob's attributes from its roster row, so health, damage and armour have one source ({@code RosterStats}). */
public final class TeemingAttributes {
    private TeemingAttributes() {}

    public static AttributeSupplier.Builder monster(String id, double speed) {
        return stats(Monster.createMonsterAttributes(), id, speed);
    }

    public static AttributeSupplier.Builder animal(String id, double speed) {
        return stats(Mob.createMobAttributes(), id, speed);
    }

    private static AttributeSupplier.Builder stats(AttributeSupplier.Builder builder, String id, double speed) {
        RosterStats.Entry row = RosterStats.get(id);
        return builder
                .add(Attributes.MAX_HEALTH, row.health())
                .add(Attributes.ATTACK_DAMAGE, row.attack())
                .add(Attributes.ARMOR, row.armor())
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }
}
