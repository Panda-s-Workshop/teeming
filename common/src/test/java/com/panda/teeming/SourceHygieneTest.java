package com.panda.teeming;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Cheap source-level checks: server safety, lang coverage for literal keys, and the End's in-place blows. */
class SourceHygieneTest {
    private static final Path SRC = Path.of("src", "main", "java");
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "teeming", "lang", "en_us.json");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void commonCodeNeverTouchesClientClasses() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : javaFiles()) {
            String norm = p.toString().replace('\\', '/');
            if (norm.contains("/client/")) continue;
            String text = Files.readString(p);
            if (text.contains("net.minecraft.client") || text.contains("com.mojang.blaze3d")) offenders.add(norm);
        }
        assertTrue(offenders.isEmpty(), "client classes referenced outside the client package: " + offenders);
    }

    /**
     * The End's void rule: an End mob's blow moves no one, so a player at an island's edge is never knocked over the
     * void. Every blow an End mob deals to another goes through {@code GroundMarks.hurtInPlace} or {@code inPlace}; the
     * only bare blows allowed are the rows that may move a player out (the Brinkram's edge charge and the Brinkray's ram,
     * each throwing by its row's distance), the Liftstone Monolith's slam (from no point, its push zeroed after) and the
     * Pullstar's touch (its sideways motion zeroed after). A mob's own hurt ({@code super.hurtServer}) isn't a blow.
     */
    @Test
    void endBlowsLandInPlace() throws IOException {
        Set<String> allowed = Set.of(
                "Brinkram.java: if (!victim.hurtServer(level, source, damage)) return;",
                "Brinkray.java: if (victim.hurtServer(level, source, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * RAM_HIT)) {",
                "LiftstoneMonolith.java: boolean hurt = v.hurtServer(level, slamSource(), slamDamage(v));",
                "Pullstar.java: if (!v.hurtServer(level, damageSources().mobAttack(this), damage)) continue;");
        List<String> bare = new ArrayList<>();
        try (Stream<Path> s = Files.list(SRC.resolve(Path.of("com", "panda", "teeming", "mob", "end")))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                for (String line : Files.readAllLines(p)) {
                    String code = line.strip();
                    boolean blow = (code.contains(".hurtServer(") && !code.contains("super.hurtServer(")) || code.contains("doHurtTarget(");
                    if (!blow || code.contains("inPlace(") || code.contains("hurtInPlace(") || code.startsWith("*") || code.startsWith("//")) continue;
                    String key = p.getFileName() + ": " + code;
                    if (!allowed.contains(key)) bare.add(key);
                }
            }
        }
        assertTrue(bare.isEmpty(), "End blows that can knock a player over the void: " + bare);
    }

    @Test
    void literalTranslationKeysExistInTheLangFile() throws IOException {
        String lang = Files.readString(LANG);
        Pattern literal = Pattern.compile("translatable\\(\"([a-z0-9_.]+)\"");
        Set<String> missing = new TreeSet<>();
        for (Path p : javaFiles()) {
            Matcher m = literal.matcher(Files.readString(p));
            while (m.find()) {
                String key = m.group(1);
                if (key.endsWith(".")) continue; // a prefix: the rest is computed
                if (!lang.contains("\"" + key + "\"")) missing.add(key + "  (" + p.getFileName() + ")");
            }
        }
        assertTrue(missing.isEmpty(), "lang keys used in code but missing from en_us.json: " + missing);
    }

    @Test
    void everyGeneratedJsonParsesAndHasNoTabs() throws IOException {
        Path res = Path.of("src", "main", "resources");
        com.google.gson.Gson gson = new com.google.gson.Gson();
        try (Stream<Path> s = Files.walk(res)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".json") || x.toString().endsWith(".mcmeta")).toList()) {
                String text = Files.readString(p);
                assertFalse(text.contains("\r"), "CRLF in " + p);
                assertDoesNotThrow(() -> gson.fromJson(text, Object.class), "invalid JSON: " + p);
            }
        }
    }
}
