package com.panda.teeming.mob.look;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.panda.teeming.TeemingMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads every mob's looks from {@code data/<ns>/teeming/looks/<mob>/<look>.json} (reloads with {@code /reload}).
 * Registered on {@code AddServerReloadListenersEvent} (NeoForge) and {@code ResourceLoader} (Fabric). The base look
 * comes first in each list.
 */
public final class Looks extends SimpleJsonResourceReloadListener<Looks.Json> {
    public static final Identifier ID = TeemingMod.id("looks");
    private static volatile Map<String, List<LookDef>> byMob = Map.of();

    public record Json(int weight, float scale, Map<String, Double> attributes, String biomes) {
        public static final Codec<Json> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Json::weight),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Json::scale),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("attributes", Map.of()).forGetter(Json::attributes),
                Codec.STRING.optionalFieldOf("biomes", "").forGetter(Json::biomes)
        ).apply(i, Json::new));
    }

    public Looks() {
        super(Json.CODEC, FileToIdConverter.json("teeming/looks"));
    }

    @Override
    protected void apply(Map<Identifier, Json> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, List<LookDef>> loaded = new HashMap<>();
        files.forEach((id, json) -> {
            String path = id.getPath();
            int slash = path.indexOf('/');
            if (slash <= 0 || slash == path.length() - 1) {
                TeemingMod.LOGGER.warn("Teeming: look {} is not <mob>/<look>, skipped", id);
                return;
            }
            String mob = path.substring(0, slash);
            loaded.computeIfAbsent(mob, k -> new ArrayList<>())
                    .add(new LookDef(mob, path.substring(slash + 1), json.weight(), json.scale(), Map.copyOf(json.attributes()), json.biomes()));
        });
        Map<String, List<LookDef>> sorted = new HashMap<>();
        loaded.forEach((mob, looks) -> {
            looks.sort(Comparator.comparing((LookDef l) -> !l.id().equals(LookDef.BASE)).thenComparing(LookDef::id));
            sorted.put(mob, List.copyOf(looks));
        });
        byMob = Map.copyOf(sorted);
        TeemingMod.LOGGER.info("Teeming: {} looks for {} mobs", files.size(), sorted.size());
    }

    /** The mob's looks, base first; empty when none are loaded. */
    public static List<LookDef> of(String mob) {
        return byMob.getOrDefault(mob, List.of());
    }

    public static Optional<LookDef> get(String mob, String look) {
        for (LookDef def : of(mob)) if (def.id().equals(look)) return Optional.of(def);
        return Optional.empty();
    }

    public static int count() {
        return byMob.values().stream().mapToInt(List::size).sum();
    }
}
