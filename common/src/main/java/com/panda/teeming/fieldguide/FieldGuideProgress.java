package com.panda.teeming.fieldguide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One player's Field Guide, immutable: the mobs seen, kills per mob, the looks seen ({@code mob/look}), the mobs
 * whose trophy the player has owned, and the server's kills-to-reveal setting (carried here so the client shows the
 * server's number).
 * Saved and synced to its owner as a player attachment on both loaders; copied on death.
 */
public record FieldGuideProgress(Set<String> seen, Map<String, Integer> kills, Set<String> looks, Set<String> trophies, int revealAt) {
    public static final int DEFAULT_REVEAL_AT = 3;
    public static final FieldGuideProgress EMPTY = new FieldGuideProgress(Set.of(), Map.of(), Set.of(), Set.of(), DEFAULT_REVEAL_AT);
    private static final int MAX = 4096;

    public static final Codec<FieldGuideProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("seen").forGetter(p -> List.copyOf(p.seen)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("kills").forGetter(FieldGuideProgress::kills),
            Codec.STRING.listOf().fieldOf("looks").forGetter(p -> List.copyOf(p.looks)),
            Codec.STRING.listOf().optionalFieldOf("trophies", List.of()).forGetter(p -> List.copyOf(p.trophies)),
            Codec.INT.optionalFieldOf("reveal_at", DEFAULT_REVEAL_AT).forGetter(FieldGuideProgress::revealAt)
    ).apply(i, (seen, kills, looks, trophies, revealAt) -> new FieldGuideProgress(new LinkedHashSet<>(seen), kills, new LinkedHashSet<>(looks),
            new LinkedHashSet<>(trophies), revealAt)));

    public static final StreamCodec<ByteBuf, FieldGuideProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(LinkedHashSet::new, ByteBufCodecs.STRING_UTF8, MAX), p -> new LinkedHashSet<>(p.seen),
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT, MAX), p -> new LinkedHashMap<>(p.kills),
            ByteBufCodecs.collection(LinkedHashSet::new, ByteBufCodecs.STRING_UTF8, MAX), p -> new LinkedHashSet<>(p.looks),
            ByteBufCodecs.collection(LinkedHashSet::new, ByteBufCodecs.STRING_UTF8, MAX), p -> new LinkedHashSet<>(p.trophies),
            ByteBufCodecs.VAR_INT, FieldGuideProgress::revealAt,
            FieldGuideProgress::new);

    public FieldGuideProgress {
        seen = Collections.unmodifiableSet(new LinkedHashSet<>(seen));
        kills = Collections.unmodifiableMap(new LinkedHashMap<>(kills));
        looks = Collections.unmodifiableSet(new LinkedHashSet<>(looks));
        trophies = Collections.unmodifiableSet(new LinkedHashSet<>(trophies));
        revealAt = Math.max(1, revealAt);
    }

    public boolean hasSeen(String mob) {
        return seen.contains(mob);
    }

    public int killsOf(String mob) {
        return kills.getOrDefault(mob, 0);
    }

    /** Tell, counter, drops and looks show once the player has killed it {@link #revealAt} times. */
    public boolean revealed(String mob) {
        return killsOf(mob) >= revealAt;
    }

    /** A copy carrying the server's kills-to-reveal setting; the same instance if it hasn't changed. */
    public FieldGuideProgress withRevealAt(int value) {
        return value == revealAt ? this : new FieldGuideProgress(seen, kills, looks, trophies, value);
    }

    public boolean hasSeenLook(String mob, String look) {
        return looks.contains(mob + "/" + look);
    }

    /** A copy with the mob (and its look) seen; the same instance if nothing changes. */
    public FieldGuideProgress withSeen(String mob, String look) {
        boolean newMob = !seen.contains(mob);
        boolean newLook = !look.isEmpty() && !looks.contains(mob + "/" + look);
        if (!newMob && !newLook) return this;
        Set<String> s = new LinkedHashSet<>(seen);
        s.add(mob);
        Set<String> l = new LinkedHashSet<>(looks);
        if (newLook) l.add(mob + "/" + look);
        return new FieldGuideProgress(s, kills, l, trophies, revealAt);
    }

    /** A copy with one more kill of the mob, which also counts as seeing it. */
    public FieldGuideProgress withKill(String mob, String look) {
        FieldGuideProgress p = withSeen(mob, look);
        Map<String, Integer> k = new LinkedHashMap<>(p.kills);
        k.merge(mob, 1, Integer::sum);
        return new FieldGuideProgress(p.seen, k, p.looks, p.trophies, p.revealAt);
    }

    /** A copy with the mob seen in each of {@code looks} and killed often enough to be revealed. */
    public FieldGuideProgress withRevealed(String mob, Iterable<String> looks) {
        FieldGuideProgress p = withSeen(mob, "");
        for (String look : looks) p = p.withSeen(mob, look);
        if (p.revealed(mob)) return p;
        Map<String, Integer> k = new LinkedHashMap<>(p.kills);
        k.put(mob, revealAt);
        return new FieldGuideProgress(p.seen, k, p.looks, p.trophies, p.revealAt);
    }

    /** A copy that has never seen the mob: no sighting, kills, looks or trophy; the same instance if it hadn't. */
    public FieldGuideProgress without(String mob) {
        String prefix = mob + "/";
        if (!seen.contains(mob) && !kills.containsKey(mob) && !trophies.contains(mob) && looks.stream().noneMatch(l -> l.startsWith(prefix))) return this;
        Set<String> s = new LinkedHashSet<>(seen);
        s.remove(mob);
        Map<String, Integer> k = new LinkedHashMap<>(kills);
        k.remove(mob);
        Set<String> l = new LinkedHashSet<>(looks);
        l.removeIf(look -> look.startsWith(prefix));
        Set<String> t = new LinkedHashSet<>(trophies);
        t.remove(mob);
        return new FieldGuideProgress(s, k, l, t, revealAt);
    }

    public boolean hasTrophy(String mob) {
        return trophies.contains(mob);
    }

    /** A copy with the mob's trophy owned; the same instance if it already was. */
    public FieldGuideProgress withTrophy(String mob) {
        if (trophies.contains(mob)) return this;
        Set<String> t = new LinkedHashSet<>(trophies);
        t.add(mob);
        return new FieldGuideProgress(seen, kills, looks, t, revealAt);
    }
}
