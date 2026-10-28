package com.panda.teeming.spawn;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.roster.RosterStats;
import java.util.List;

/**
 * Teeming's share of one Overworld biome's monster list, counted in mobs: each entry's weight times its mean group, since
 * natural spawning places a pick's whole group at its spot and vanilla's land monsters come four to a pick. Each kind is
 * held against the vanilla mobs that can stand where it does: the open-water mobs against the drowned, the land mobs
 * against the other vanilla monsters a count of arrivals sees ({@link SpawnTable#UNCOUNTED_VANILLA} left out). Of the
 * land mobs, those on ordinary ground or a ceiling carry the share; the narrow-place mobs (canopy leaves, a shallow
 * pool, open sand) and the mobs that spawn only below y 0 go on top. A Teeming monster that spawns in any light counts
 * {@link SpawnTable#ANY_LIGHT_FACTOR} times its mobs. Plain Java over the list's entries, so the GameTests and the
 * harness count it the same way.
 */
public final class OverworldShare {
    private OverworldShare() {}

    /** One monster entry of a live list: its entity id ({@code namespace:path}), weight and group. */
    public record Entry(String type, int weight, int min, int max) {
        public double mobs() {
            return weight * (min + max) / 2.0;
        }
    }

    /** The mobs of each kind on one list, and the shares they make. */
    public record Shares(double vanillaLand, double vanillaWater, double carry, double narrow, double deep, double wet) {
        /** The carrying mobs' share of the land mobs. */
        public double carrying() {
            return share(carry, vanillaLand);
        }

        /** The land share above y 0: carrying and narrow-place mobs. */
        public double surface() {
            return share(carry + narrow, vanillaLand);
        }

        /** The land share below y 0: carrying and below-y-0 mobs. */
        public double below() {
            return share(carry + deep, vanillaLand);
        }

        /** The open-water mobs' share against the drowned. */
        public double water() {
            return share(wet, vanillaWater);
        }
    }

    public static double share(double teeming, double others) {
        return teeming + others <= 0 ? 0.0 : teeming / (teeming + others);
    }

    /** How the share counts a Teeming mob. */
    public enum Kind { CARRY, NARROW, DEEP, WET }

    public static Kind kind(RosterStats.Entry row) {
        if (row.maxY() <= 0) return Kind.DEEP;
        return switch (row.place()) {
            case WATER -> Kind.WET;
            case SAND, LEAVES, SHALLOW -> Kind.NARROW;
            default -> Kind.CARRY;
        };
    }

    public static Shares of(List<Entry> monsters) {
        double land = 0, water = 0, carry = 0, narrow = 0, deep = 0, wet = 0;
        for (Entry e : monsters) {
            int colon = e.type().indexOf(':');
            String ns = colon < 0 ? "minecraft" : e.type().substring(0, colon);
            String path = e.type().substring(colon + 1);
            if (ns.equals(TeemingMod.MODID)) {
                RosterStats.Entry row = RosterStats.BY_ID.get(path);
                if (row == null) continue;
                double mobs = e.mobs() * (row.anyLight() && !row.passive() ? SpawnTable.ANY_LIGHT_FACTOR : 1);
                switch (kind(row)) {
                    case CARRY -> carry += mobs;
                    case NARROW -> narrow += mobs;
                    case DEEP -> deep += mobs;
                    case WET -> wet += mobs;
                }
            } else if (SpawnTable.WATER_MONSTERS.contains(e.type())) {
                water += e.mobs();
            } else if (!SpawnTable.UNCOUNTED_VANILLA.contains(e.type())) {
                land += e.mobs();
            }
        }
        return new Shares(land, water, carry, narrow, deep, wet);
    }
}
