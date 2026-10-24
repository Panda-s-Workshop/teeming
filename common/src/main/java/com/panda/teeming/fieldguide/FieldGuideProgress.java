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
 * One player's Field Guide, immutable: the mobs seen, kills per mob, and the looks seen ({@code mob/look}).
 * Saved and synced to its owner as a player attachment on both loaders; copied on death.
 */
public record FieldGuideProgress(Set<String> seen, Map<String, Integer> kills, Set<String> looks) {
    public static final FieldGuideProgress EMPTY = new FieldGuideProgress(Set.of(), Map.of(), Set.of());
    private static final int MAX = 4096;

    public static final Codec<FieldGuideProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("seen").forGetter(p -> List.copyOf(p.seen)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("kills").forGetter(FieldGuideProgress::kills),
            Codec.STRING.listOf().fieldOf("looks").forGetter(p -> List.copyOf(p.looks))
    ).apply(i, (seen, kills, looks) -> new FieldGuideProgress(new LinkedHashSet<>(seen), kills, new LinkedHashSet<>(looks))));

    public static final StreamCodec<ByteBuf, FieldGuideProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(LinkedHashSet::new, ByteBufCodecs.STRING_UTF8, MAX), p -> new LinkedHashSet<>(p.seen),
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT, MAX), p -> new LinkedHashMap<>(p.kills),
            ByteBufCodecs.collection(LinkedHashSet::new, ByteBufCodecs.STRING_UTF8, MAX), p -> new LinkedHashSet<>(p.looks),
            FieldGuideProgress::new);

    public FieldGuideProgress {
        seen = Collections.unmodifiableSet(new LinkedHashSet<>(seen));
        kills = Collections.unmodifiableMap(new LinkedHashMap<>(kills));
        looks = Collections.unmodifiableSet(new LinkedHashSet<>(looks));
    }

    public boolean hasSeen(String mob) {
        return seen.contains(mob);
    }

    public int killsOf(String mob) {
        return kills.getOrDefault(mob, 0);
    }

    /** Tell, counter, drops and looks show once the player has killed it this many times. */
    public boolean revealed(String mob, int killsToReveal) {
        return killsOf(mob) >= killsToReveal;
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
        return new FieldGuideProgress(s, kills, l);
    }

    /** A copy with one more kill of the mob, which also counts as seeing it. */
    public FieldGuideProgress withKill(String mob, String look) {
        FieldGuideProgress p = withSeen(mob, look);
        Map<String, Integer> k = new LinkedHashMap<>(p.kills);
        k.merge(mob, 1, Integer::sum);
        return new FieldGuideProgress(p.seen, k, p.looks);
    }
}
