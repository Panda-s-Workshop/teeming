package com.panda.teeming.spawn;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.roster.RosterStats;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Teeming's share of one Overworld or Nether biome's monster list, counted in mobs: each entry's weight times its mean
 * group, since natural spawning places a pick's whole group at its spot and vanilla's monsters come up to four to a
 * pick. A vanilla entry's group counts up to its cluster size (natural spawning stops a spot there: 4, the ghast's 1),
 * times the share of its picks its own spawn rule lets through (the ghast's 1 in 20). Each kind is held against the
 * vanilla mobs that can stand where it does: the open-water mobs against the drowned, the land mobs against the other
 * vanilla monsters a count of arrivals sees ({@link SpawnTable#UNCOUNTED_VANILLA} left out). Of the land mobs, those on
 * ordinary ground or a ceiling carry the share; the narrow-place mobs (canopy leaves, a shallow pool, open sand, a lava
 * sea's surface) and the mobs that spawn only below y 0 go on top, except where the biome's ordinary ground is the
 * mob's place ({@link SpawnTable#CARRIES}). An Overworld Teeming monster that spawns in any light counts
 * {@link SpawnTable#ANY_LIGHT_FACTOR} times its mobs, except where it carries on its narrow place. Plain Java over the
 * list's entries, so the GameTests and the harness count it the same way.
 */
public final class ListShare {
    private ListShare() {}

    /** One monster entry of a live list: its entity id ({@code namespace:path}), weight and group. */
    public record Entry(String type, int weight, int min, int max) {
        /** Weight times the mean group. */
        public double mobs() {
            return weight * (min + max) / 2.0;
        }

        /** A vanilla entry's mobs: weight times the mean group up to its cluster size, times the share of picks its rule lets through. */
        public double vanillaMobs() {
            int cap = SpawnTable.VANILLA_CLUSTER.getOrDefault(type, SpawnTable.CLUSTER);
            double sum = 0;
            for (int c = min; c <= max; c++) sum += Math.min(c, cap);
            return weight * sum / (max - min + 1) * SpawnTable.VANILLA_PASS.getOrDefault(type, 1.0);
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

    /** The mob's kind in a biome mod's biome, where no biome makes its narrow place the ordinary ground. */
    public static Kind kind(RosterStats.Entry row) {
        return kind(row, null);
    }

    /** The mob's kind in {@code biome} ({@code namespace:path}; null for a biome mod's biome). */
    public static Kind kind(RosterStats.Entry row, @Nullable String biome) {
        if (row.maxY() <= 0) return Kind.DEEP;
        if (row.place() == RosterStats.Place.WATER) return Kind.WET;
        if (biome != null && SpawnTable.carries(row.id(), biome)) return Kind.CARRY;
        return switch (row.place()) {
            case SAND, LEAVES, SHALLOW, LAVA -> Kind.NARROW;
            default -> Kind.CARRY;
        };
    }

    public static Shares of(List<Entry> monsters) {
        return of(monsters, null);
    }

    /** The shares of one biome's monster list ({@code biome}: its id, or null for a biome mod's biome). */
    public static Shares of(List<Entry> monsters, @Nullable String biome) {
        double land = 0, water = 0, carry = 0, narrow = 0, deep = 0, wet = 0;
        for (Entry e : monsters) {
            int colon = e.type().indexOf(':');
            String ns = colon < 0 ? "minecraft" : e.type().substring(0, colon);
            String path = e.type().substring(colon + 1);
            if (ns.equals(TeemingMod.MODID)) {
                RosterStats.Entry row = RosterStats.BY_ID.get(path);
                if (row == null) continue;
                Kind kind = kind(row, biome);
                boolean carriesHere = biome != null && SpawnTable.carries(row.id(), biome);
                boolean anyLight = row.anyLight() && !row.passive() && row.dim() == RosterStats.Dim.OVERWORLD && !carriesHere;
                double mobs = e.mobs() * (anyLight ? SpawnTable.ANY_LIGHT_FACTOR : 1);
                switch (kind) {
                    case CARRY -> carry += mobs;
                    case NARROW -> narrow += mobs;
                    case DEEP -> deep += mobs;
                    case WET -> wet += mobs;
                }
            } else if (SpawnTable.WATER_MONSTERS.contains(e.type())) {
                water += e.vanillaMobs();
            } else if (!SpawnTable.UNCOUNTED_VANILLA.contains(e.type())) {
                land += e.vanillaMobs();
            }
        }
        return new Shares(land, water, carry, narrow, deep, wet);
    }
}
