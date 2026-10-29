package com.panda.teeming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The settings file's rewrite: the current header, keys in order, and a pack's values, comments and own keys kept. */
class SettingsFileTest {
    private static final String HEADER = "Teeming (read at startup)\nspawningDensity: 0..4, thins below 1";
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("spawningDensity", "1.0");
        DEFAULTS.put("trophies.chance", "0.025");
        DEFAULTS.put("mobs.thornhorn.enabled", "true");
    }

    private static final Map<String, String> RENAMED = Map.of("mob.thornhorn.enabled", "mobs.thornhorn.enabled");

    @Test
    void firstStartWritesEveryDefaultUnderTheHeader() {
        String text = SettingsFile.write("", HEADER, DEFAULTS, RENAMED);
        assertEquals("# Teeming (read at startup)\n# spawningDensity: 0..4, thins below 1\n\nspawningDensity=1.0\ntrophies.chance=0.025\nmobs.thornhorn.enabled=true\n", text);
        assertEquals(text, SettingsFile.write(text, HEADER, DEFAULTS, RENAMED), "writing it again changes nothing");
    }

    @Test
    void anOldFileGetsTheNewHeaderInOrderWithItsValuesCommentsAndOwnKeys() {
        // as Properties.store wrote it: the old header, a date, keys in hash order; then a pack's edits
        String old = """
                #Teeming (read at startup)
                #spawningDensity: multiplies how often every Teeming mob spawns
                #Thu Oct 29 02:48:00 CET 2026
                trophies.chance=0.05
                # the pack's own setting, read by its scripts
                pack.extra = keep me
                # thinner spawns for this pack
                spawningDensity = 0.5
                """;
        String text = SettingsFile.write(old, HEADER, DEFAULTS, RENAMED);
        assertEquals("""
                # Teeming (read at startup)
                # spawningDensity: 0..4, thins below 1

                # thinner spawns for this pack
                spawningDensity = 0.5
                trophies.chance=0.05
                mobs.thornhorn.enabled=true
                # the pack's own setting, read by its scripts
                pack.extra = keep me
                """, text);
        Properties p = SettingsFile.read(text);
        assertEquals("0.5", p.getProperty("spawningDensity"));
        assertEquals("keep me", p.getProperty("pack.extra"));
        assertEquals(text, SettingsFile.write(text, HEADER, DEFAULTS, RENAMED), "writing it again changes nothing");
        String newer = SettingsFile.write(text, HEADER + "\nmore", DEFAULTS, RENAMED);
        assertTrue(newer.startsWith("# Teeming (read at startup)\n# spawningDensity: 0..4, thins below 1\n# more\n\n# thinner spawns"),
                "a new header replaces the old one: " + newer);
    }

    @Test
    void aRenamedKeyMovesItsValueUnlessTheNewNameIsSet() {
        String moved = SettingsFile.write("mob.thornhorn.enabled=false\n", HEADER, DEFAULTS, RENAMED);
        Properties p = SettingsFile.read(moved);
        assertEquals("false", p.getProperty("mobs.thornhorn.enabled"));
        assertTrue(!p.containsKey("mob.thornhorn.enabled"), "the old name is gone: " + moved);
        String both = SettingsFile.write("mob.thornhorn.enabled=false\nmobs.thornhorn.enabled=true\n", HEADER, DEFAULTS, RENAMED);
        assertEquals("true", SettingsFile.read(both).getProperty("mobs.thornhorn.enabled"));
    }

    @Test
    void aContinuedValueAndTrailingCommentsStay() {
        String old = "# header\n\npack.list = a, \\\n    b\nspawningDensity=2.0\n# the end\n";
        String text = SettingsFile.write(old, HEADER, DEFAULTS, RENAMED);
        assertEquals("a, b", SettingsFile.read(text).getProperty("pack.list"));
        assertTrue(text.endsWith("pack.list = a, \\\n    b\n# the end\n"), text);
    }

    @Test
    void aFileWithAByteOrderMarkKeepsItsFirstKey(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("teeming.properties");
        Files.writeString(file, "\uFEFFspawningDensity=2.5\n");
        String old = SettingsFile.readFile(file).orElseThrow();
        assertEquals("2.5", SettingsFile.read(SettingsFile.write(old, HEADER, DEFAULTS, RENAMED)).getProperty("spawningDensity"));
    }

    @Test
    void aFileThatIsNotUtf8IsReadAsLatin1(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("teeming.properties");
        Files.write(file, "spawningDensity=1.0\n# caf\u00e9\ntrophies.chance=0.05\n".getBytes(StandardCharsets.ISO_8859_1));
        String old = SettingsFile.readFile(file).orElseThrow();
        String text = SettingsFile.write(old, HEADER, DEFAULTS, RENAMED);
        assertEquals("0.05", SettingsFile.read(text).getProperty("trophies.chance"));
        assertTrue(text.contains("# caf\u00e9"), "its comment kept: " + text);
    }

    @Test
    void aUtf16FileWithAByteOrderMarkIsReadAsUtf16(@TempDir Path dir) throws IOException {
        // as Windows PowerShell 5.1's Out-File and Notepad's "UTF-16 LE" save it, and the big-endian form
        String body = "# café\nspawningDensity=2.5\ntrophies.chance=0.05\n";
        for (Charset charset : new Charset[] {StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE}) {
            Path file = dir.resolve("teeming-" + charset.name() + ".properties");
            Files.write(file, ("﻿" + body).getBytes(charset));
            String old = SettingsFile.readFile(file).orElseThrow();
            assertEquals(body, old, charset + ": the text, without its mark");
            String text = SettingsFile.write(old, HEADER, DEFAULTS, RENAMED);
            Properties p = SettingsFile.read(text);
            assertEquals("2.5", p.getProperty("spawningDensity"), charset + ": " + text);
            assertEquals("0.05", p.getProperty("trophies.chance"), charset + ": " + text);
            assertEquals(List.of(), SettingsFile.unknownKeys(text, DEFAULTS.keySet()), charset + ": no key is left unread");
        }
    }

    @Test
    void aMistypedOrUnknownKeyIsNamedInFileOrder() {
        String old = "spawningDensty=2.0\n# the pack's own\npack.extra = keep me\ntrophies.chance=0.05\nmob.thornhorn.enabled=false\n";
        String text = SettingsFile.write(old, HEADER, DEFAULTS, RENAMED);
        assertEquals(List.of("spawningDensty", "pack.extra"), SettingsFile.unknownKeys(text, DEFAULTS.keySet()),
                "the typo and the pack's key; a renamed key has moved to its new name: " + text);
        assertEquals("2.0", SettingsFile.read(text).getProperty("spawningDensty"), "the unknown key is still kept");
        assertEquals(List.of(), SettingsFile.unknownKeys(SettingsFile.write("", HEADER, DEFAULTS, RENAMED), DEFAULTS.keySet()),
                "a fresh file has none");
    }

    @Test
    void noFileIsEmptyAndAnUnreadableOneIsNothing(@TempDir Path dir) {
        assertEquals("", SettingsFile.readFile(dir.resolve("missing.properties")).orElseThrow());
        assertTrue(SettingsFile.readFile(dir).isEmpty(), "a path that can't be read gives nothing, so nothing is written over it");
    }
}
